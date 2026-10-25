<a id="back-to-top"></a>

# Ngữ cảnh và tham số

## Menu
- [JoinPoint để đọc đối số, chữ ký và nguồn lời gọi được hỗ trợ](#advice-context-arguments-001)
- [getThis() và getTarget() tương ứng proxy và đối tượng đích](#advice-context-arguments-002)
- [MethodSignature và việc lấy thông tin phương thức](#advice-context-arguments-003)
- [Ràng buộc đối số bằng args và tham số advice](#advice-context-arguments-004)
- [Ràng buộc annotation vào tham số advice](#advice-context-arguments-005)
- [Ràng buộc kết quả returning và ngoại lệ throwing](#advice-context-arguments-006)
- [argNames, tên tham số và tùy chọn biên dịch -parameters](#advice-context-arguments-007)
- [Đối số null, kiểu nguyên thủy, overload và varargs](#advice-context-arguments-008)
- [Giới hạn suy luận khi tham số có generic và xóa kiểu](#advice-context-arguments-009)
- [Bridge method, phương thức interface và phương thức triển khai](#advice-context-arguments-010)
- [Đọc annotation trên đúng phương thức khi đối tượng là proxy](#advice-context-arguments-011)

## <a id="advice-context-arguments-001">JoinPoint để đọc đối số, chữ ký và nguồn lời gọi được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: sử dụng `JoinPoint` để đọc đối số, chữ ký và ngữ cảnh lời gọi

## 1. `JoinPoint` giải quyết vấn đề gì trong Spring Aspect?

Khi viết advice trong Spring AOP, đôi khi chỉ biết một lời gọi khớp pointcut là chưa đủ: bạn còn muốn biết **đang thực thi method nào**, **những đối số nào đã được truyền**, **Spring proxy nào nhận lời gọi** và **target bean nào thực hiện nghiệp vụ**.

Spring cung cấp đối tượng `org.aspectj.lang.JoinPoint` cho advice đang được gọi. Đây là **ảnh chụp ngữ cảnh của method execution hiện tại** do Spring AOP proxy quan sát, không phải đối tượng đại diện cho toàn bộ stack trace hoặc mọi thao tác Java trong JVM.

Ví dụ ngắn:

```java
@Aspect
@Component
public class OrderAuditAspect {
    @Before("execution(* demo.joinpoint.OrderService.place(..))")
    public void beforePlace(JoinPoint joinPoint) {
        System.out.println("method=" + joinPoint.getSignature().getName());
        System.out.println("args=" + Arrays.toString(joinPoint.getArgs()));
    }
}
```

Đây là **fragment** để hiểu API: nó cần imports, một target Spring bean, hạ tầng Spring AOP và một consumer gọi target qua proxy. Với lời gọi `place("ORD-7", 2)`, `getSignature().getName()` cho `"place"`, còn `getArgs()` mô tả các đối số `["ORD-7", 2]` theo thứ tự tham số.

**Trọng tâm:** đây là cách dùng `JoinPoint` trong **Spring Aspect**, không phải giải thích AOP nói chung hoặc full AspectJ bytecode weaving. `JoinPoint` của Spring AOP chỉ xuất hiện trong advice khi một **method execution của Spring-managed bean** đã được proxy chặn và pointcut khớp.

## 2. Sơ đồ: ngữ cảnh được tạo ở đâu?

```text
Một consumer
      |
      | orderOperations.place("ORD-7", 2)
      v
Spring AOP proxy
      |
      | kiểm tra advisor / pointcut
      v
@Before advice nhận JoinPoint
      |
      +-- getArgs()       -> ["ORD-7", 2]
      +-- getSignature()  -> thông tin method place(...)
      +-- getThis()       -> đối tượng Spring AOP proxy
      +-- getTarget()     -> đối tượng OrderService target
      |
      v
Target OrderService.place("ORD-7", 2)
      |
      v
Caller nhận kết quả
```

Các mốc cần phân biệt:

- Đối số của biểu thức gọi method đã được Java **đánh giá trước** khi lời gọi chạm proxy. `getArgs()` không cho biết đối số được tạo bằng expression nào trong source code.
- Spring AOP tạo ngữ cảnh advice trong luồng **method execution đã được intercept**. Nó không tạo một `JoinPoint` mới cho những lời gọi nội bộ `this.someMethod()` bỏ qua proxy.
- `JoinPoint` cung cấp **thông tin invocation ở ranh giới proxy**. Nó không cung cấp sẵn tên controller, file nguồn hoặc dòng mã Java ở nơi caller gõ lệnh gọi.
- Nếu không có aspect/advice áp dụng, target vẫn có thể thực thi nhưng không có advice để nhận `JoinPoint`.

Ví dụ kiểu lời gọi `new OrderService().place(...)` tạo object Java ngoài Spring container; nó không có Spring AOP proxy tự động, nên advice trên không chạy.

## 3. Bốn API cốt lõi

| API `JoinPoint` | Thông tin nhận được | Lưu ý trong Spring |
| --- | --- | --- |
| `getArgs()` | Mảng đối số của invocation theo vị trí | Có thể chứa `null`, wrapper của primitive, DTO hoặc đối tượng nhạy cảm; không phải tên biến nguồn |
| `getSignature()` | Mô tả method đang được advice | Dùng `MethodSignature` khi cần return type, parameter types, `Method` |
| `getThis()` | **Spring AOP proxy** đại diện cho lời gọi | Với JDK proxy, thường implement interface nhưng không phải instance của target concrete class |
| `getTarget()` | **Target object** nằm sau proxy | Là đối tượng nghiệp vụ, có thể `null` với một số trường hợp target source đặc biệt; không tự gọi target nếu muốn advice |
| `getKind()` | Loại join point, thường `method-execution` trong Spring AOP | Không phải tên operation hay tên caller |
| `toShortString()`, `toLongString()` | Mô tả chữ của join point | Phù hợp debug, không coi output là schema log ổn định |
| `getStaticPart()` | Phần thông tin tĩnh của join point | Ít cần trong business advice; không phải Java stack frame của caller |
| `getSourceLocation()` | Giao diện source location của AspectJ JoinPoint | **Không dùng như định danh tin cậy của caller**; Spring proxy không cung cấp call-site source tracking tương đương weaving |

**Đặc biệt:** phương thức `JoinPoint.getTarget()` không có nghĩa trả lại một “cổng vào advice”. Nếu dùng `joinPoint.getTarget()` để gọi method khác trên target, bạn có thể chủ động **bypass Spring AOP proxy**. Nếu cần một bean collaboration đi qua Spring AOP, hãy inject Spring bean phù hợp.

## 4. Đọc đối số với `getArgs()`

### 4.1. Vị trí trong mảng tương ứng tham số method

Xét một method nghiệp vụ:

```java
public String place(String orderId, int quantity) {
    return "RCPT-" + orderId + "-" + quantity;
}
```

Trong advice:

```java
Object[] args = joinPoint.getArgs();
String orderId = (String) args[0];
int quantity = (Integer) args[1];
```

Đây là fragment chỉ đúng nếu pointcut đã giới hạn đến method có đúng các tham số `(String, int)`. Với primitive `int`, phần tử tương ứng trong `Object[]` được biểu diễn bằng wrapper `Integer`. Không được suy đoán mảng luôn đủ phần tử: method không tham số trả mảng rỗng, và pointcut rộng có thể bắt method có signature khác.

Một cách chỉ dùng để debug:

```java
System.out.println(Arrays.toString(joinPoint.getArgs()));
```

Lệnh cần import `java.util.Arrays`; kết quả phụ thuộc `toString()` của object. Nó có thể vô tình in secrets hoặc kích hoạt logic tốn kém trong `toString()`. Ví dụ chạy ở mục 8 dùng các giá trị thử nghiệm không nhạy cảm.

### 4.2. `getArgs()` không cho biết **tên tham số ở source**

Từ `getArgs()` bạn biết có giá trị `"ORD-7"` và `2`, nhưng **không biết** caller đã viết `place(orderId, quantity)` hay `place("ORD-" + i, 1 + 1)`. Tên parameter `orderId`, `quantity` là metadata của method definition; nó không nằm trong array đối số.

Để xem parameter types và metadata reflection, dùng `MethodSignature`. Để thấy **tên parameter** qua reflection một cách đáng tin, cần compiler flags/metadata phù hợp (ví dụ `-parameters`); nếu thiếu, có thể chỉ nhận tên tổng quát hoặc metadata không có.

### 4.3. Với pointcut rộng phải tránh ép kiểu mù quáng

```java
@Before("execution(* demo.joinpoint..*Service.*(..))")
public void unsafe(JoinPoint jp) {
    String orderId = (String) jp.getArgs()[0]; // Có thể lỗi.
}
```

Trong code trên, method no-arg gây `ArrayIndexOutOfBoundsException`; method có tham số đầu tiên là `Long` có thể gây `ClassCastException`. Chỉ ép kiểu sau khi expression hoặc runtime checks thực sự xác nhận signature.

Ví dụ quan sát an toàn hơn:

```java
Object[] args = jp.getArgs();
System.out.println("argument count=" + args.length);
if (args.length > 0 && args[0] instanceof String first) {
    System.out.println("first argument length=" + first.length());
}
```

Đoạn này giả định method `jp` là một `JoinPoint` argument của advice. Ngay cả khi kiểu hợp lệ, trước khi ghi log hãy xem dữ liệu có phải token, password, email, số tài khoản, địa chỉ hoặc thông tin nhạy cảm không.

### 4.4. Có nên gán lại phần tử `getArgs()`?

Không sử dụng thao tác:

```java
jp.getArgs()[0] = "FORCED"; // Không phải hợp đồng để thay argument của target.
```

để kỳ vọng target tự nhận argument mới. Trong Spring AOP, `getArgs()` phục vụ **quan sát**; implementation có thể trả một bản sao/snapshot. Khi thật sự cần điều chỉnh arguments trước khi tiếp tục invocation, dùng `@Around` với `ProceedingJoinPoint.proceed(Object[] args)` và tuân thủ đúng số lượng/kiểu tham số.

Tránh thay trực tiếp các DTO mutable chỉ để logging; ngay cả khi mảng riêng, đối tượng được tham chiếu bên trong có thể là instance mutable của nghiệp vụ.

## 5. Đọc chữ ký bằng `getSignature()`

`joinPoint.getSignature()` có kiểu chung `org.aspectj.lang.Signature`. Với method-execution join point trong Spring AOP, có thể chuyển sang `org.aspectj.lang.reflect.MethodSignature` để lấy API dành riêng cho method.

```java
MethodSignature signature = (MethodSignature) jp.getSignature();

String name = signature.getName();
Class<?> returnType = signature.getReturnType();
Class<?>[] parameterTypes = signature.getParameterTypes();
Method method = signature.getMethod();
```

Đoạn trên là fragment bên trong advice, cần imports:

```java
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import java.lang.reflect.Method;
```

Các trường:

- `getName()`: tên method, ví dụ `place`.
- `getReturnType()`: kiểu trả về **khai báo**, ví dụ `String.class` hoặc `int.class`, không phải return value thực tế.
- `getParameterTypes()`: những kiểu tham số khai báo theo thứ tự; khác với runtime types trong `getArgs()`.
- `getMethod()`: reflective method của invocation được Spring AOP biết đến. **Không luôn đồng nghĩa với method declaration trực tiếp trên implementation class** nếu đang dùng JDK interface proxy.
- `getDeclaringTypeName()`: thông tin declaring type của signature, nhưng có thể là interface type trong trường hợp JDK proxy; không dùng nó để đoán caller class.

### 5.1. Interface method và implementation method có thể khác nhau

```java
public interface OrderOperations {
    String place(String orderId, int quantity);
}

@Service
public class OrderService implements OrderOperations {
    @Override
    public String place(String orderId, int quantity) {
        return "RCPT-" + orderId + "-" + quantity;
    }
}
```

Khi caller giữ **JDK dynamic proxy** dưới kiểu `OrderOperations`, signature của invocation có thể liên quan method khai báo trên interface, dù logic thực thi nằm tại `OrderService.place(...)`.

Nếu mục đích là tìm annotation hoặc method implement cụ thể, Spring có helper:

```java
MethodSignature sig = (MethodSignature) jp.getSignature();
Method invoked = sig.getMethod();

Object target = jp.getTarget();
if (target != null) {
    Method specific = AopUtils.getMostSpecificMethod(
            invoked, target.getClass());
    System.out.println(specific.getDeclaringClass().getSimpleName());
}
```

Đây là fragment minh họa, cần import `org.springframework.aop.support.AopUtils`. Với các kiểu bridge/generic method và annotation mapping phức tạp, còn phải xét quy tắc lookup của framework liên quan thay vì chỉ đọc một annotation từ `sig.getMethod()`.

### 5.2. Chữ ký **không** phải địa chỉ source của caller

Method signature cho biết invocation **đang thực thi method nào**. Nó **không** nói:

- Java class nào gọi method đó trước khi vào proxy;
- source file và dòng call site ở controller/service khác;
- HTTP endpoint, user identity, request ID hoặc transaction ID;
- nơi object argument được tạo ra.

Những giá trị này nếu cần phải đi qua các cơ chế riêng: explicit request context, correlation ID, tracing/observation infrastructure, hoặc business parameters. Không ép `getSignature().getDeclaringTypeName()` thành “caller class”.

## 6. `getThis()` và `getTarget()`: phân biệt Spring proxy với bean đích

Trong Spring AOP, hai phương thức này **khác nhau về ngữ nghĩa**:

```java
Object proxy = jp.getThis();
Object target = jp.getTarget();
```

- `proxy` là **Spring AOP proxy** mà lời gọi hiện tại đi qua.
- `target` là **Spring AOP target** đang được advice; trong các trường hợp thông thường là instance service thật.
- `proxy == target` **không phải điều phải luôn đúng**. Với một AOP proxy thực, chúng thường là hai Java object khác nhau.
- `getTarget()` có thể `null` với trường hợp đặc biệt không có target object; code hạ tầng/generic aspect phải chịu được khả năng đó.
- JDK proxy thực hiện interface, không phải subclass của concrete class. Ví dụ `proxy instanceof OrderOperations` true nhưng `proxy instanceof OrderService` false.
- CGLIB proxy thường là subclass có thể assign sang concrete class nếu target class không bị final; vẫn là **proxy** với interceptor chain.

**Không gọi thẳng business method trên `getTarget()` để cố kích hoạt advice.** Target invocation không qua proxy sẽ không tạo một interception mới. Nếu thật sự phải gọi operation được advice, thiết kế consumer nhận bean khác qua constructor injection là cách ưu tiên; không lạm dụng tự gọi proxy từ aspect.

### 6.1. `getThis()` cũng không phải caller object

Tên `getThis()` dễ khiến bạn tưởng nó trả Java `this` của class gọi service. Trong Spring AOP, nó trả **proxy object của chính bean đang được intercept**, không phải controller, workflow hoặc REST endpoint gọi bean này.

Ví dụ:

```text
CheckoutController -> OrderOperations proxy -> OrderService target
                           |                      |
                   jp.getThis()             jp.getTarget()
```

Không có API `JoinPoint.getCaller()` phổ quát trong Spring AOP.

### 6.2. Cần source/caller provenance thì làm gì?

“**Nguồn lời gọi được hỗ trợ**” trong Spring proxy có nghĩa thực tế bạn có thể xác định method, đối số, proxy và target liên quan. Nó không có nghĩa Spring JoinPoint tự thu thập call-site location.

Nếu muốn biết luồng request nào dẫn tới service, nên:

- Đặt **trace/span/correlation ID** ở ranh giới request và truyền qua hạ tầng quan sát phù hợp.
- Nếu cần danh tính nghiệp vụ caller, truyền một thông tin caller rõ ràng trong domain API hoặc context được bảo đảm.
- Với Spring MVC, có thể lấy metadata tuyến request trong interceptor/filter/web layer phù hợp, thay vì đoán từ method signature của service.
- Với thread hops/async, phải tính đến việc context propagation cần cấu hình thích hợp.
- Nếu chỉ đang debug, stack trace có thể cho manh mối, nhưng không phải API bền vững: proxy, inlining, threads, async hoặc refactoring khiến call stack thay đổi.

Một `SourceLocation` trong AspectJ API **không nên được coi là vị trí mà consumer đã gọi service** trong Spring AOP. Nên hiểu Spring AOP chủ yếu quan sát **execution join point**, không phải join point kiểu source `call(...)` được weaving tại call site.

## 7. Quy tắc khai báo tham số advice

### 7.1. `JoinPoint` đứng đầu trong advice thường

Các loại advice như `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing` có thể nhận `org.aspectj.lang.JoinPoint` là **tham số đầu tiên**:

```java
@Before("execution(* demo.joinpoint.OrderService.place(..))")
public void audit(JoinPoint jp) {
    System.out.println(jp.getSignature().getName());
}
```

Không cần khai báo `JoinPoint` trên business method; Spring tự cung cấp nó cho advice khi execution match pointcut. Không thể inject `JoinPoint` như một singleton bean rồi dùng để suy ra mọi lời gọi đang diễn ra; nó là ngữ cảnh của invocation trong advice.

### 7.2. `@Around` phải dùng `ProceedingJoinPoint`

Với `@Around`, tham số đầu tiên phải là `ProceedingJoinPoint`, subtype của `JoinPoint` có `proceed()`:

```java
@Around("execution(* demo.joinpoint.OrderService.place(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("calling " + pjp.getSignature().getName());
    return pjp.proceed();
}
```

Điều kiện để target thật chạy là advice thực hiện `proceed()`. Nếu quên `return pjp.proceed()`, return value hoặc control flow tới caller có thể sai. Trong khi đó, `JoinPoint` thông thường không có `proceed()`; `@Before` không thể chủ động bỏ qua invocation rồi return một kết quả thay thế như `@Around`.

### 7.3. Khi nào nên bind parameter trực tiếp thay vì dùng `Object[]`?

Nếu pointcut chỉ chọn method có một argument `String`:

```java
@Before(
    value = "execution(* demo.joinpoint.OrderService.cancel(..))"
          + " && args(orderId)",
    argNames = "orderId"
)
public void beforeCancel(JoinPoint jp, String orderId) {
    System.out.println("cancel length=" + orderId.length());
}
```

Đây là fragment minh họa cần method `OrderService.cancel(String)` tồn tại và đúng một đối số. `args(orderId)` vừa kiểm tra/bind argument theo quy tắc pointcut, còn `argNames="orderId"` mô tả tên binding khi compiler không lưu metadata tham số phù hợp. Spring không đòi tự cast `jp.getArgs()[0]` trong trường hợp này.

**Không gộp snippet này vào dự án ở mục 8** vì dự án đó không định nghĩa `cancel(..)`; đây là hướng mở rộng API riêng.

Với nhiều tham số, khai báo tên và thứ tự đúng với expression, ví dụ `args(orderId, quantity)`; không suy đoán `argNames` hay Java parameter names là luôn có trong bytecode.

### 7.4. Return value và thrown exception là ngữ cảnh khác

`JoinPoint.getArgs()` trả input arguments. Nó **không** trả kết quả của target hay exception chưa ném. Spring hỗ trợ:

- `@AfterReturning(pointcut=..., returning="result")` để bind giá trị return khi method kết thúc bình thường;
- `@AfterThrowing(pointcut=..., throwing="ex")` để bind exception phù hợp;
- `@Around` để truy cập outcome của `proceed()` trong try/catch/finally.

`getSignature()` cho biết **declared return type**; nó không tương đương với **actual returned value**.

## 8. Ví dụ độc lập chạy được: đọc signature, args, proxy và target

Ví dụ dùng **Java 17 + Maven + Spring Framework 6.2.x**, không cần Spring Boot. Target có interface nên với `@EnableAspectJAutoProxy(proxyTargetClass=false)` Spring ưu tiên **JDK dynamic proxy**. Aspect chỉ advice `place(String,int)`; một method khác không match, lời gọi `this.place()` bypass, và object tạo bằng `new` không qua Spring proxy.

### 8.1. Cấu trúc thư mục

```text
spring-joinpoint-context/
├── pom.xml
└── src/main/java/demo/joinpoint/
    ├── AppConfig.java
    ├── OrderOperations.java
    ├── OrderService.java
    ├── ContextAspect.java
    └── Main.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-joinpoint-context</artifactId>
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
package demo.joinpoint;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` phát hiện Spring service và aspect. `@EnableAspectJAutoProxy` bật auto-proxy creator. Với interface `OrderOperations` và cấu hình này trong Spring Framework thuần, proxy của bean service là JDK dynamic proxy.

### 8.4. `OrderOperations.java`

```java
package demo.joinpoint;

public interface OrderOperations {
    String place(String orderId, int quantity);
    String health();
    void batch();
}
```

Hợp đồng consumer chỉ gồm public interface methods, tương thích JDK dynamic proxy.

### 8.5. `OrderService.java`

```java
package demo.joinpoint;

import org.springframework.stereotype.Service;

@Service
public class OrderService implements OrderOperations {
    @Override
    public String place(String orderId, int quantity) {
        System.out.println("[target] place " + orderId + " qty=" + quantity);
        return "RCPT-" + orderId + "-" + quantity;
    }

    @Override
    public String health() {
        System.out.println("[target] health");
        return "UP";
    }

    @Override
    public void batch() {
        System.out.println("[target] batch");
        this.place("INNER", 1); // Target tự gọi: bypass AOP proxy.
    }
}
```

Pointcut bên dưới chỉ áp dụng cho `place(..)` khi lời gọi **đi qua proxy**. Method `health()` và `batch()` không thuộc pointcut. `this.place("INNER", 1)` vẫn chạy Java method bình thường nhưng không có before advice riêng.

### 8.6. `ContextAspect.java`

```java
package demo.joinpoint;

import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ContextAspect {
    @Before("execution(* demo.joinpoint.OrderService.place(..))")
    public void observe(JoinPoint jp) {
        MethodSignature signature = (MethodSignature) jp.getSignature();

        System.out.println("[advice] method=" + signature.getName());
        System.out.println("[advice] args=" + Arrays.toString(jp.getArgs()));
        System.out.println("[advice] returnType="
                + signature.getReturnType().getSimpleName());

        System.out.println("[advice] proxyImplementsPort="
                + (jp.getThis() instanceof OrderOperations));

        Object target = jp.getTarget();
        System.out.println("[advice] targetClass="
                + (target == null ? "<none>"
                                  : target.getClass().getSimpleName()));
    }
}
```

Quan sát cụ thể:

- `getSignature()` mô tả method `place`; ép sang `MethodSignature` để đọc declared return type.
- `getArgs()` chứa hai giá trị theo thứ tự, số `2` được boxing trong array.
- `getThis() instanceof OrderOperations` true với JDK proxy thực hiện interface.
- `getTarget()` là instance `OrderService` nên `targetClass=OrderService`.
- Aspect chỉ quan sát, không mutate args hoặc gọi lại method target/proxy.

### 8.7. `Main.java`

```java
package demo.joinpoint;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderOperations orders = context.getBean(OrderOperations.class);

            System.out.println("[main] jdkProxy="
                    + AopUtils.isJdkDynamicProxy(orders));

            System.out.println("[caller] receipt="
                    + orders.place("ORD-7", 2));

            System.out.println("[caller] health="
                    + orders.health());

            orders.batch();

            OrderOperations unmanaged = new OrderService();
            System.out.println("[caller] rawReceipt="
                    + unmanaged.place("RAW", 1));
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.joinpoint.Main
```

**Kết quả stdout của các lệnh `System.out` trong ví dụ** (bỏ qua log Maven/Spring nếu phát thêm):

```text
[main] jdkProxy=true
[advice] method=place
[advice] args=[ORD-7, 2]
[advice] returnType=String
[advice] proxyImplementsPort=true
[advice] targetClass=OrderService
[target] place ORD-7 qty=2
[caller] receipt=RCPT-ORD-7-2
[target] health
[caller] health=UP
[target] batch
[target] place INNER qty=1
[target] place RAW qty=1
[caller] rawReceipt=RCPT-RAW-1
```

### 8.8. Phân tích thứ tự log

1. `getBean(OrderOperations.class)` nhận Spring AOP bean reference, dùng JDK proxy nên dòng đầu là `jdkProxy=true`.
2. `orders.place("ORD-7",2)` vào proxy, pointcut match; Spring tạo `JoinPoint` cho advice. Advice in metadata **trước** target.
3. `getArgs()` trả mảng gồm `String "ORD-7"` và `Integer 2`; `Arrays.toString(...)` hiển thị `[ORD-7, 2]`.
4. `MethodSignature.getReturnType()` trả `String.class`; phương thức đích sau đó trả `"RCPT-ORD-7-2"` tới caller.
5. `orders.health()` không khớp pointcut và chỉ in dòng target.
6. `orders.batch()` không khớp pointcut. Bên trong target, `this.place("INNER",1)` bỏ qua Spring proxy; **không có** dòng advice cho INNER.
7. `new OrderService().place("RAW",1)` gọi object ngoài container; có dòng target nhưng không có advice. Caller vẫn nhận return value của Java method.

Ví dụ dùng **một before advice duy nhất**, vì vậy không cần suy đoán thứ tự giữa nhiều advisors. Không dùng `getSourceLocation()` để cố in “caller file” vì Spring proxy không bảo đảm cung cấp metadata call-site đáng tin.

## 9. Khi muốn nguồn lời gọi: phân biệt bốn loại “nguồn”

Từ “nguồn” có thể chỉ nhiều thứ rất khác nhau:

| Loại thông tin | Spring `JoinPoint` đáp ứng ra sao? | Phương án thích hợp |
| --- | --- | --- |
| Method **đang được advice** | Có: `getSignature()`, `MethodSignature` | Dùng method name, return/param types; bổ sung target type nếu cần |
| Đối tượng **proxy tiếp nhận invocation** | Có: `getThis()` | Phân biệt JDK/CGLIB, không coi đó là caller object |
| Đối tượng **thực thi target** | Có: `getTarget()` | Có thể dùng target class cho metadata, không gọi trực tiếp nếu cần advice |
| **Java caller object**, file/dòng nơi gọi method | Không có API chuẩn đáng tin của Spring JoinPoint cho call site | Tracing, context explicit, stack trace chỉ để debug |
| **HTTP/request origin** | Không nằm sẵn trong `JoinPoint` | Spring web filter/interceptor, request context/observation theo luồng |
| **User/principal** | Không thể suy ra chỉ từ signature/args | Spring Security context hoặc explicit application context phù hợp |
| **Transaction/tracing context** | Không đồng nhất với JoinPoint | Spring transaction/observability APIs theo đúng module |

Trong Spring AOP, `getThis()` biểu thị proxy **ở phía callee**, không phải object “this” ở caller. `getTarget()` biểu thị bean thực thi nghiệp vụ. Nếu một request bắt đầu từ `CheckoutController` rồi đến `OrderService.place()`, metadata tự nhiên của JoinPoint vẫn là method execution của `OrderService`, không phải tên controller.

### 9.1. Vì sao không nên lấy stack trace làm khóa nghiệp vụ?

Java stack trace cho biết những frame hiện diện tại một thời điểm, nhưng:

- Proxy layers và interceptors có thể thêm nhiều frames hạ tầng.
- Async execution, scheduling, reactive pipelines và thread pools làm caller logic không còn ở stack hiện tại.
- Refactor method, compiler optimizations, generated proxy classes có thể thay đổi frame.
- Stack capture tốn chi phí nếu thực hiện trên mọi request và có thể làm lộ chi tiết implementation.

Dùng stack trace khi cần debug lỗi, không tự gán nó thành “nguồn caller” ổn định cho audit hoặc authorization.

### 9.2. Có nên dùng `getSourceLocation()`?

`JoinPoint` theo AspectJ API có method `getSourceLocation()`. Nhưng Spring AOP **không weaving call-site bytecode**, vì vậy không có đủ thông tin để cung cấp file/dòng **nơi một consumer gọi bean** một cách tổng quát. Không dùng `getSourceLocation()` làm nguồn sự thật về controller, caller ID hay vị trí gọi.

Nếu yêu cầu nghiệp vụ thực sự cần các thông tin này, hãy thiết kế hạ tầng trace/span ID hoặc tham số context rõ ràng. Nếu muốn debug implementation method, reflection từ `MethodSignature` thường có ý nghĩa hơn dựa vào một “source location” không đúng phạm vi.

## 10. Gắn dữ liệu vào advice: typed binding và những lưu ý

### 10.1. Binding qua `args(...)` giúp type-safe hơn

Với một method:

```java
public String place(String orderId, int quantity) { /* ... */ }
```

một advice tương ứng có thể bind hai giá trị:

```java
@Before(
    value = "execution(* demo.joinpoint.OrderService.place(..))"
          + " && args(orderId, quantity)",
    argNames = "orderId,quantity"
)
public void auditArgs(JoinPoint jp, String orderId, int quantity) {
    System.out.println("orderId length=" + orderId.length());
    System.out.println("quantity=" + quantity);
}
```

Đây là một fragment **thay thế** advice `observe(...)`, không nên cùng đăng ký nếu chỉ muốn một dòng log cho mỗi invocation. Cú pháp `JoinPoint` là tham số đặc biệt đầu tiên; việc bind `orderId` và `quantity` là phần Spring @AspectJ xử lý theo expression, phù hợp Java signature.

Nếu tên trong `argNames` sai hoặc không tương ứng với variables trong pointcut/advice, context có thể lỗi lúc cấu hình. Khi pointcut có nhiều method overloads, nên kiểm tra kiểu/argument positions và test kỹ.

### 10.2. Annotation binding không tự suy ra giá trị caller

Ví dụ annotation custom `@Audited` có thể được dùng với `@annotation(...)` để chọn method. Điều đó giúp tìm method **được gắn annotation** theo quy tắc Spring pointcut, nhưng không cung cấp Java caller source. Annotation cần được giữ ở runtime, đặt nơi phù hợp và được aspect nhận diện. Khi dùng JDK proxy, method trên interface và method implementation có thể khác, nên kiểm tra cơ chế annotation lookup thực tế.

### 10.3. Không lưu `JoinPoint` trong singleton field

Aspect `@Component` thường được tạo như singleton Spring bean. Nếu đặt:

```java
private JoinPoint lastJoinPoint; // Không nên lưu invocation state lâu dài.

@Before("execution(* demo.joinpoint..*Service.*(..))")
public void before(JoinPoint jp) {
    lastJoinPoint = jp;
}
```

sẽ có nguy cơ **race condition** khi nhiều threads xử lý invocation đồng thời. `JoinPoint` và `getArgs()` liên quan một invocation, không phải state chung. Hãy dùng local variable, truyền dữ liệu tới telemetry đã được thiết kế concurrent-safe hoặc dùng context phù hợp với vòng đời một request.

## 11. Giới hạn Spring AOP ảnh hưởng trực tiếp đến thông tin JoinPoint

- Spring AOP thông thường intercept **method execution** trên Spring-managed beans, không chặn mọi method call trong JVM. `call(...)`, field access, constructor join points của full AspectJ không tự được hỗ trợ chỉ bằng Spring proxy.
- Với **JDK proxy**, chỉ những public interface methods được proxy công bố có thể bị caller gọi qua reference proxy. Public method chỉ nằm trên concrete implementation không tự xuất hiện trên JDK interface proxy.
- Với **CGLIB proxy**, method phải có thể được subclass override; private/final methods có giới hạn. Final class không được CGLIB subclass.
- **Self-invocation** như `this.place()` bypass proxy; advice không tạo `JoinPoint` riêng cho lời gọi đó.
- Một object tạo bằng `new` ngoài Spring không tự được AOP proxy; không có advice context chỉ vì class khác cùng tên là bean.
- `@Aspect` một mình chưa đăng ký aspect là Spring bean; cần `@Component` hoặc cấu hình `@Bean`, cùng AOP activation.
- `JoinPoint.getThis()`/`getTarget()` mô tả proxy/target **của invocation đang match**, không phải dependency graph hay toàn bộ Spring container.
- `@Before` thấy đối số đầu vào nhưng chưa thấy return/exception của target. Muốn outcome, cần đúng loại advice hoặc logic ở `@Around`.
- Vài tính năng AOP khác như introduction interfaces có thể làm proxy công bố interface bổ sung; không cast trực tiếp về implementation nếu đang dùng JDK proxy.
- Multiple application contexts có thể có các proxy/aspects khác nhau; kiểm tra reference thực tế từ consumer trước khi kết luận JoinPoint thuộc context nào.

## 12. Checklist xử lý sự cố

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| `@Before` không nhận JoinPoint | Advice không match, aspect không đăng ký bean hoặc lời gọi bypass proxy | Xem `@Aspect @Component`, `@EnableAspectJAutoProxy`, AopUtils và call path |
| `getArgs()[0]` bị lỗi | Method không có tham số hoặc pointcut match nhiều signatures | Kiểm tra length, class và expression |
| ClassCastException khi ép arg | Runtime argument type khác giả định | Dùng `instanceof` hoặc `args(...)` binding đúng kiểu |
| `getSignature().getName()` đúng nhưng getMethod không chỉ implementation | JDK proxy biết interface method | Dùng target class + `AopUtils.getMostSpecificMethod(...)` khi cần |
| `getThis().getClass()` khác target class | Spring AOP trả proxy object | Đây là điều bình thường; dùng AopUtils để xác định loại proxy |
| `getTarget()` khác `getThis()` | Target và proxy là hai references khác nhau | Không so sánh identity để quyết định lỗi |
| Aspect in tên service, không in tên controller | Signature mô tả callee method execution | Dùng request tracing/correlation IDs, không coi JoinPoint là caller API |
| `getSourceLocation()` không cho dòng call-site | Spring AOP không weave vị trí caller | Tránh phụ thuộc; trace ở ranh giới request |
| `getArgs()` in lộ token/password | Logging không kiểm soát dữ liệu nhạy cảm | Redaction/allowlist metadata, không log toàn bộ args |
| Advice biến đổi args nhưng target nhận cũ | Gán vào array từ `getArgs()` không phải contract điều khiển invocation | `@Around` và `proceed(newArgs)` nếu thật sự cần |
| `this.method()` không có log advice | Self-invocation bypass proxy | Tách sang Spring bean khác/đổi boundary gọi |
| `new Service().method()` không có advice | Object ngoài container | Inject Spring-managed bean |
| Method được advice hai lần | Có nhiều advisors hoặc lần gọi qua proxy nhiều lần | Xem order/pointcut, tránh gắn trùng aspects |
| `JoinPoint` được lưu trong field rồi sai dữ liệu | Singleton aspect chia sẻ state giữa threads | Giữ dữ liệu invocation trong local variables |
| Thay `JoinPoint` bằng `ProceedingJoinPoint` trong `@Before` | Sai contract advice type | Chỉ dùng `ProceedingJoinPoint` làm param bắt buộc đầu cho `@Around` |

**Trình tự kiểm tra nhanh:**

1. Xác minh **target và aspect thuộc Spring context** và AOP đã bật.
2. Lấy **bean reference thực tế** từ DI/context, dùng `AopUtils.isAopProxy(...)` và kiểm tra JDK/CGLIB.
3. Xem pointcut có match **execution method** và caller có qua proxy không.
4. Dùng `getSignature()` trước để xác định method, rồi mới xử lý `getArgs()` dựa vào declared types.
5. Nếu cần implementation annotation/method, resolve method cụ thể theo target class, chú ý interface/generics.
6. Dùng `getThis()`/`getTarget()` đúng nghĩa proxy và callee target.
7. Nếu yêu cầu caller/request source, dùng tracing/context thật, không suy diễn từ signature hoặc getSourceLocation.
8. Thêm integration test chứng minh chính xác method nào được advice, dữ liệu nào được log và trường hợp nào bypass proxy.

## 13. Câu hỏi ôn tập

**1. `JoinPoint.getArgs()` chứa gì?** Mảng runtime values của invocation theo thứ tự method parameters; nó không chứa tên Java variables ở nơi gọi hoặc thông tin call-site.

**2. `getThis()` có phải caller object không?** Không. Với Spring AOP, đó là **AOP proxy** của bean đang được intercept.

**3. `getTarget()` và `getThis()` có luôn giống nhau không?** Không. Proxy và target thường là hai objects khác nhau; getTarget đôi khi có thể null trong các trường hợp target đặc biệt.

**4. Muốn biết return type `String` của method, dùng API nào?** Ép `jp.getSignature()` sang `MethodSignature` rồi dùng `getReturnType()`. Đây là kiểu khai báo, không phải actual returned value.

**5. Có thể biết Java controller gọi service ở file dòng nào bằng `getSignature()` không?** Không. Signature mô tả method execution ở phía callee; tracing hoặc context riêng mới thích hợp để suy ra nguồn request/caller.

**6. `getArgs()[0] = anotherValue` có thay argument target nhận không?** Không dựa vào contract Spring AOP. Nếu thật sự cần thay inputs, dùng around advice và `proceed(Object[])` với đúng signature.

**7. Vì sao `this.place()` không cho một JoinPoint advice riêng?** Target gọi trực tiếp method của nó, bỏ qua Spring AOP proxy; không có method interception mới.

**8. `@Around` có nhận `JoinPoint` giống `@Before` không?** Nó yêu cầu `ProceedingJoinPoint` làm tham số đầu tiên; subtype này cung cấp `proceed()`.

**9. Làm sao nhận typed argument an toàn hơn array cast?** Bind parameter bằng `args(name)` trong pointcut và parameter trong advice, kèm metadata/`argNames` phù hợp.

**10. Có nên lưu `JoinPoint` vào field của singleton aspect?** Không. Đó là state theo invocation; field chia sẻ tạo race condition, dễ lộ dữ liệu.

## Tham khảo tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice: Access to the Current JoinPoint](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Pointcuts và execution join points](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 API — MethodInvocationProceedingJoinPoint (getThis/getTarget)](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/aspectj/MethodInvocationProceedingJoinPoint.html)
- [Spring Framework 6.2 — @AspectJ annotation support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html)
- [Spring Framework 6.2 — Introduction to AOP proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-002">getThis() và getTarget() tương ứng proxy và đối tượng đích</a>

<details>
<summary>Xem chi tiết</summary>

# getThis() và getTarget(): phân biệt Spring AOP proxy với đối tượng đích

## 1. Câu hỏi cốt lõi trong một @Aspect

Trong một Spring `@Aspect`, khi một method được gọi qua proxy, ta có thể đang nhìn thấy **hai đối tượng Java khác nhau**:

1. **Spring AOP proxy**: object mà client lấy từ `ApplicationContext` hoặc được inject vào một bean khác. Nó nhận lời gọi, tìm các advisor/interceptor phù hợp và điều phối execution.
2. **Target object**: object thật chứa logic nghiệp vụ, được proxy chuyển lời gọi đến sau khi chạy các advice cần thiết.

Spring cung cấp hai API trên `JoinPoint`/`ProceedingJoinPoint`:

- **`joinPoint.getThis()` trả về Spring AOP proxy hiện hành.**
- **`joinPoint.getTarget()` trả về Spring AOP target hiện hành**, tức đối tượng nằm phía sau proxy (trong trường hợp đặc biệt proxy không có target, kết quả có thể là `null`).

Đây không chỉ là hai cách lấy tên lớp. Chúng có **identity, runtime class, interface exposure và đường gọi method khác nhau**. Chọn nhầm có thể khiến logging sai kiểu, code ép kiểu thất bại, lời gọi bỏ qua aspect hoặc thậm chí vô tình gọi advice đệ quy.

Ví dụ hình dung:

```text
ApplicationContext
      |
      v
 Spring AOP proxy  <-------------  joinPoint.getThis()
      |
      |  Advisor A -> Advisor B -> ...
      |
      v
 Business target   <-------------  joinPoint.getTarget()
      |
      v
 Method implementation
```

**Phạm vi bài:** `getThis()`/`getTarget()` và ý nghĩa **trong Spring AOP dựa trên proxy**, đặc biệt khi dùng `@Aspect` với Spring Framework. Ta không mở rộng sang semantics của AspectJ compiler/weaver độc lập, vì chính việc Spring dùng proxy mới tạo ra khác biệt quan trọng giữa hai object này.

### Sau bài học, bạn sẽ

- Hiểu `this`, `target` và các object mà Spring AOP dùng khi chặn một method.
- Phân biệt `getThis()`/`getTarget()` với Java keyword `this` trong target và trong aspect.
- Dự đoán kết quả với JDK dynamic proxy so với class-based CGLIB proxy.
- Hiểu `this(TypePattern)` và `target(TypePattern)` trong Spring pointcut.
- Biết vì sao **không dùng `getTarget()` rồi gọi method để “proceed”**, và vì sao gọi qua `getThis()` trong advice có nguy cơ đệ quy.
- Tự chạy ví dụ Java 17/Spring Framework với hai context riêng và kết quả quan sát được.
- Chẩn đoán self-invocation, proxy casting, targetless proxy và target source động mà không suy luận sai về identity.

## 2. Cơ chế Spring AOP: client gọi proxy, proxy gọi target

Một Spring bean có thể được tạo trước dưới dạng target, rồi `BeanPostProcessor` thuộc hạ tầng auto-proxy creator quyết định có cần trả một proxy thay cho target khi bean được công bố qua container hay không.

Trong trường hợp một bean cần advice:

```text
Caller
   |
   | injected JobApi bean
   v
Proxy [identity P]
   |
   | interceptor chain
   v
Target JobService [identity T]
   |
   | thực thi perform("A")
   v
Return
```

Thông thường, `P !== T` xét theo **reference identity (`==` trong Java)**. Proxy đại diện cho target trước caller nhưng không phải là chính target. Hai đối tượng có thể có cách biểu diễn `equals`/`hashCode` đặc biệt; khi học ranh giới AOP, dùng `==` thay vì `equals` để so sánh identity.

### Vai trò khác nhau

| Thuộc tính | `getThis()` | `getTarget()` |
| --- | --- | --- |
| Trả về gì trong Spring AOP? | Proxy hiện hành | Target hiện hành phía sau proxy |
| Gọi method qua reference này | Có thể đi qua Spring AOP advisor chain | Gọi trực tiếp trên target, bỏ qua các advisor của proxy đó |
| Runtime class thường gặp | JDK-generated proxy hoặc CGLIB subclass | Implementation class thực |
| Có giống `context.getBean(...)`? | Trong trường hợp Spring proxy thông thường, là reference proxy mà client dùng | Thường không, khi bean được proxy |
| Có thể bằng `null`? | Không đối với `MethodInvocationProceedingJoinPoint` của Spring AOP | Có thể khi proxy không có target object |
| Có luôn được ép thành implementation class? | **Không** đối với JDK proxy | Thường là instance target implementation nếu target tồn tại |
| Có luôn là một singleton cố định mãi mãi? | Không nên suy luận ngoài cấu hình bean/proxy cụ thể | Không: target source/scopes có thể thay target theo invocation |
| Dùng để tiếp tục current `@Around`? | Không phải API tiếp tục chain | Không phải API tiếp tục chain |
| API đúng để tiếp tục `@Around` | **`ProceedingJoinPoint.proceed()`** | **`ProceedingJoinPoint.proceed()`** |

Spring chính thức ghi rõ `getThis()` trả proxy, `getTarget()` trả target là POJO phía sau proxy, không mang các advice của proxy trên reference đó. Với `@Around`, muốn tiếp tục invocation hiện tại thì **`proceed()` mới đúng**.

## 3. getThis() không đồng nghĩa Java this của target hoặc aspect

Có ba ngữ cảnh `this` dễ nhầm:

### 3.1. this trong @Aspect class

```java
@Aspect
public class InspectAspect {

    @Around("execution(* com.example.service..*.*(..))")
    public Object inspect(ProceedingJoinPoint pjp) throws Throwable {
        Object aspectInstance = this;
        Object proxy = pjp.getThis();
        Object target = pjp.getTarget();

        System.out.println(aspectInstance == proxy); // thường false
        System.out.println(proxy == target);         // thường false
        return pjp.proceed();
    }
}
```

Đây là snippet minh họa; cần các imports, Spring bean registration và target khớp. `this` trong source của `InspectAspect` là **aspect instance**. `pjp.getThis()` lại là **proxy của Spring bean được advice**. Hai đối tượng này phục vụ hai vai trò hoàn toàn khác nhau.

### 3.2. this trong target implementation

```java
public class JobService {
    public void outer() {
        this.inner();
    }

    public void inner() {
        System.out.println("inner");
    }
}
```

Khi `outer()` đã được proxy điều phối tới target, `this` bên trong `JobService.outer()` trỏ vào **target object**. Lời gọi `this.inner()` **không quay trở lại Spring proxy**, nên advice dành riêng cho `inner()` không tự chạy.

### 3.3. this trong Spring pointcut expression

```java
@Pointcut("this(com.example.api.JobApi)")
public void proxyImplementsJobApi() {}
```

Designator `this(TypePattern)` xét **proxy object** trong Spring AOP, không xét biến `this` của source Java trong aspect. `target(TypePattern)` lại xét **target object**. Phần 6 đi sâu vào sự khác biệt này.

## 4. JDK dynamic proxy: proxy và implementation có kiểu khác nhau

Khi target triển khai một interface phù hợp và Spring không bị buộc dùng class-based proxy, Spring AOP thường tạo **JDK dynamic proxy**.

Ví dụ:

```java
package demo.identity;

public interface JobApi {
    String perform(String id);
}
```

```java
package demo.identity;

public class JobService implements JobApi {
    @Override
    public String perform(String id) {
        return "done:" + id;
    }
}
```

Bean được tiêm qua `JobApi`. Với JDK proxy:
- `getThis() instanceof JobApi` là `true`.
- `getThis() instanceof JobService` là `false`: JDK proxy triển khai interface nhưng **không phải subclass của `JobService`**.
- `getTarget() instanceof JobService` là `true` nếu target là `JobService`.
- `getThis() == getTarget()` thường là `false`.
- Dù proxy không phải instance của implementation, pointcut Spring vẫn có thể match execution của target method phù hợp.

Một lỗi thực tế:

```java
JobService service = (JobService) context.getBean(JobApi.class);
```

Nếu bean thực tế là JDK proxy, cast trên có thể ném `ClassCastException`. Đó không phải lỗi của `getTarget()`, mà là do **client đang ép kiểu proxy thành implementation**. Cách sử dụng thông thường là inject/get bean theo **interface `JobApi`** hoặc xem lại lý do cần proxy theo class.

### JDK proxy chỉ expose method qua interface phù hợp

Nếu implementation có thêm method `internalOnly()` không được khai báo trên interface proxy, client không thể gọi method đó qua `JobApi` reference. Không dùng `getTarget()` làm đường vòng để gọi method ngoài interface mà vẫn mong Spring advice chạy. Nếu nghiệp vụ cần method được advice, thiết kế service contract và proxy boundary cho phù hợp.

## 5. CGLIB class-based proxy: instanceof giống nhưng identity vẫn khác

Nếu target không có interface, hoặc cấu hình `proxyTargetClass=true` yêu cầu class proxy, Spring thường dùng **CGLIB** tạo subclass của implementation.

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfiguration {
}
```

Đây là snippet cấu hình, không phải ứng dụng độc lập. Khi `JobService` được Spring AOP class-proxy:
- `getThis() instanceof JobApi` là `true` nếu implementation có interface đó.
- `getThis() instanceof JobService` cũng là `true` vì CGLIB proxy là subclass.
- `getTarget() instanceof JobService` là `true`.
- **`getThis() == getTarget()` vẫn thường là `false`**: quan hệ kế thừa không làm proxy và target thành cùng object.
- `getThis().getClass()` có thể trả một CGLIB-generated class; `getTarget().getClass()` trả implementation class.

Do `getThis() instanceof JobService` là true với CGLIB, người học dễ kết luận `getThis()` và `getTarget()` là một. Kết luận này sai: **`instanceof` kiểm tra quan hệ kiểu, còn `==` kiểm tra identity**.

### Giới hạn class-based proxy

CGLIB phải tạo subclass để override method. Nó không thể advice bằng override đối với:
- class `final`;
- method `final`;
- method `private`;
- một số method không visible để subclass override.

Ngoài ra, self-invocation từ target vẫn bypass Spring AOP dù proxy là CGLIB. Đổi JDK proxy thành CGLIB **không phải cách sửa** vấn đề `this.inner()`.

## 6. this(TypePattern) so với target(TypePattern) trong Spring pointcut

Spring Framework nêu rõ sự khác biệt giữa **proxy** và **target**:

- `this(TypePattern)`: match khi **proxy** là instance của type được chỉ định.
- `target(TypePattern)`: match khi **target object** là instance của type được chỉ định.

### 6.1. Với interface

```java
@Pointcut("this(demo.identity.JobApi)")
public void proxyIsJobApi() {}

@Pointcut("target(demo.identity.JobApi)")
public void targetIsJobApi() {}
```

Khi target `JobService implements JobApi` và proxy triển khai interface `JobApi`, cả hai thường match. Không nên dùng ví dụ này để chứng minh chúng tương đương **trong mọi cấu hình**.

### 6.2. Với implementation class

```java
@Pointcut("this(demo.identity.JobService)")
public void proxyIsJobService() {}

@Pointcut("target(demo.identity.JobService)")
public void targetIsJobService() {}
```

Với **JDK proxy**:
- `this(JobService)` thường không match vì proxy không subclass `JobService`.
- `target(JobService)` có thể match vì target chính là `JobService`.

Với **CGLIB proxy**:
- Cả `this(JobService)` và `target(JobService)` thường match vì proxy là subclass và target là instance implementation.

### 6.3. Ma trận nhanh

Giả sử target là `JobService implements JobApi` và có Spring AOP proxy như mô tả:

| Câu hỏi/điều kiện | JDK proxy | CGLIB proxy |
| --- | --- | --- |
| `getThis() instanceof JobApi` | `true` | `true` |
| `getTarget() instanceof JobApi` | `true` | `true` |
| `getThis() instanceof JobService` | `false` | `true` |
| `getTarget() instanceof JobService` | `true` | `true` |
| `getThis() == getTarget()` | `false` | `false` |
| `this(JobService)` | Không match | Có thể match |
| `target(JobService)` | Có thể match | Có thể match |
| `this(JobApi)` | Có thể match | Có thể match |
| `target(JobApi)` | Có thể match | Có thể match |

“Có thể match” ở các dòng pointcut còn phụ thuộc việc method thực sự được Spring AOP intercept, type pattern hợp lệ và bean/advisor configuration phù hợp. Ma trận mô tả **quan hệ type của hai object**, không tự khẳng định mọi lời gọi trong Java đều có advice.

### 6.4. Khi chọn type pattern, tránh dùng nhầm @target

`target(SomeType)` kiểm tra quan hệ kiểu của target. Trong khi đó:
- `@target(MyAnnotation)` xét **annotation** trên runtime target type.
- `@within(MyAnnotation)` xét annotation theo declaring type của method execution.
- `within(PackageOrTypePattern)` giới hạn theo type/package khai báo execution.
- `@annotation(MyAnnotation)` xét method annotation.

Các designator này giải quyết yêu cầu khác nhau. Nếu chỉ muốn log loại Spring bean được advice, dùng `pjp.getTarget()` để quan sát object; nếu muốn ràng buộc advisor theo target type, dùng `target(...)` theo semantics của Spring pointcut.

## 7. Ví dụ chạy đầy đủ: so sánh JDK proxy và CGLIB ở hai context

Ví dụ Spring Framework thuần dưới đây dùng **Java 17**, **Spring Framework 6.1.14**, **AspectJ Weaver 1.9.22.1** và Gradle. Ta tạo hai `ApplicationContext` tách biệt để không bị một cấu hình `proxyTargetClass=true` trong context CGLIB ảnh hưởng context JDK.

Mục tiêu:
- In loại proxy mà Spring tạo (qua `AopUtils`, không in tên generated class ngẫu nhiên).
- Trong chính `@Around`, so sánh `pjp.getThis()` và `pjp.getTarget()`.
- Kiểm tra `instanceof JobService` của proxy/target.
- Chạy một method gọi từ bên ngoài và một method tự gọi method khác bên trong target.
- Quan sát self-invocation không tạo advice lồng thêm.

### 7.1. Cấu trúc project

```text
spring-aop-proxy-identity/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/identity/
    ├── JobApi.java
    ├── JobService.java
    ├── InspectAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 7.2. Cấu hình Gradle

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-proxy-identity'
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
    mainClass = 'demo.identity.DemoApplication'
}
```

`spring-context` cung cấp `AnnotationConfigApplicationContext`; `spring-aop` chứa AOP proxy infrastructure, `AopUtils` và interceptor integration; `aspectjweaver` giúp Spring phân tích `@Aspect` pointcut expression.

### 7.3. Public interface

Tệp `src/main/java/demo/identity/JobApi.java`:

```java
package demo.identity;

public interface JobApi {
    String perform(String task);
    void runInside();
}
```

Cả `perform` và `runInside` được khai báo trên interface để **JDK dynamic proxy** có thể expose cả hai method. Trong CGLIB case, proxy còn là subclass của implementation.

### 7.4. Target implementation

Tệp `src/main/java/demo/identity/JobService.java`:

```java
package demo.identity;

public class JobService implements JobApi {

    @Override
    public String perform(String task) {
        System.out.println("target:perform:" + task);
        return "done:" + task;
    }

    @Override
    public void runInside() {
        System.out.println("target:runInside");
        perform("inside");
    }
}
```

`perform("inside")` là lời gọi nội bộ trên target (`this.perform(...)`), không phải một lời gọi quay lại Spring proxy. Vì vậy, dù pointcut có thể khớp method `perform`, lời gọi này không phát sinh một `@Around` lần nữa.

### 7.5. Aspect quan sát proxy và target bằng ProceedingJoinPoint

Tệp `src/main/java/demo/identity/InspectAspect.java`:

```java
package demo.identity;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;

@Aspect
public class InspectAspect {

    @Around(
        "execution(public * demo.identity..*.perform(..))"
        + " || execution(public * demo.identity..*.runInside(..))"
    )
    public Object inspect(ProceedingJoinPoint pjp) throws Throwable {
        Object proxy = pjp.getThis();
        Object target = pjp.getTarget();

        System.out.println("aspect:" + pjp.getSignature().getName());
        System.out.println("proxyKind="
            + (AopUtils.isJdkDynamicProxy(proxy) ? "JDK" : "CGLIB"));
        System.out.println("sameObject=" + (proxy == target));
        System.out.println("proxyIsJobService="
            + (proxy instanceof JobService));
        System.out.println("targetIsJobService="
            + (target instanceof JobService));
        System.out.println("targetAopProxy="
            + AopUtils.isAopProxy(target));

        return pjp.proceed();
    }
}
```

**Giải thích kỹ:**
- `pjp.getThis()` là proxy. `AopUtils.isJdkDynamicProxy(proxy)` giúp in nhãn **JDK** hoặc **CGLIB** ổn định trong hai cấu hình thử nghiệm; không lấy string từ tên generated class.
- `pjp.getTarget()` là `JobService` target trong ví dụ, không phải một proxy lồng khác.
- `sameObject=false` cho biết proxy và target không có cùng Java reference identity.
- `proxyIsJobService` khác nhau giữa JDK và CGLIB: JDK proxy không kế thừa implementation; CGLIB subclass có quan hệ `instanceof`.
- `targetIsJobService=true` trong cả hai vì Spring dùng `JobService` làm target.
- `targetAopProxy=false` vì target là plain object của ví dụ, không phải Spring AOP proxy; không lấy đó làm quy luật tuyệt đối cho mọi custom nested-proxy setup.
- `return pjp.proceed();` mới **tiếp tục invocation hiện hành**. Ta không gọi trực tiếp `((JobService) target).perform(...)` và không dùng `((JobApi) proxy).perform(...)` để thay `proceed()`.

Pointcut dùng `execution(public * demo.identity..*.perform(..))` hoặc `runInside(..)`; chỉ các method có tên tương ứng và package/type phù hợp mới được advice. Hai method trên cùng target đều được match khi client gọi qua proxy.

### 7.6. Hai context cấu hình hai kiểu proxy

Tệp `src/main/java/demo/identity/AppConfig.java`:

```java
package demo.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class AppConfig {

    @Configuration(proxyBeanMethods = false)
    public static class CommonBeans {

        @Bean
        public JobApi jobService() {
            return new JobService();
        }

        @Bean
        public InspectAspect inspectAspect() {
            return new InspectAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class JdkConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class CglibConfig {
    }
}
```

- `CommonBeans` đăng ký `JobService` qua kiểu interface `JobApi` và đăng ký aspect bằng `@Bean`. `@Aspect` một mình không khiến class được Spring tạo thành bean.
- `JdkConfig` bật Spring AOP với `proxyTargetClass=false`. Vì target triển khai `JobApi`, trường hợp thông thường này dùng JDK dynamic proxy.
- `CglibConfig` ép class-based proxy bằng `proxyTargetClass=true`.
- Hai context được tạo **riêng** chứ không import đồng thời hai configs vào một context. Nếu nhiều hạ tầng Spring cùng request class-based proxy trong một context, thiết lập `true` có thể chi phối lựa chọn chung.
- `@Bean` khai báo return type `JobApi` vẫn cho Spring giữ concrete target `JobService` để tạo proxy theo lớp khi cần. Client trong demo lấy bean theo `JobApi.class`, hợp lệ ở cả hai context.

### 7.7. Main và lệnh chạy

Tệp `src/main/java/demo/identity/DemoApplication.java`:

```java
package demo.identity;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        run("JDK", AppConfig.JdkConfig.class);
        run("CGLIB", AppConfig.CglibConfig.class);
    }

    private static void run(String label, Class<?> config) {
        System.out.println("==" + label + "==");

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(config)) {

            JobApi bean = context.getBean(JobApi.class);
            System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
            System.out.println("result=" + bean.perform("outside"));
            bean.runInside();
        }
    }
}
```

Chạy tại thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu đã có Gradle Wrapper, chạy `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

### 7.8. Stdout từ Java code

```text
==JDK==
isAopProxy=true
aspect:perform
proxyKind=JDK
sameObject=false
proxyIsJobService=false
targetIsJobService=true
targetAopProxy=false
target:perform:outside
result=done:outside
aspect:runInside
proxyKind=JDK
sameObject=false
proxyIsJobService=false
targetIsJobService=true
targetAopProxy=false
target:runInside
target:perform:inside
==CGLIB==
isAopProxy=true
aspect:perform
proxyKind=CGLIB
sameObject=false
proxyIsJobService=true
targetIsJobService=true
targetAopProxy=false
target:perform:outside
result=done:outside
aspect:runInside
proxyKind=CGLIB
sameObject=false
proxyIsJobService=true
targetIsJobService=true
targetAopProxy=false
target:runInside
target:perform:inside
```

Đây là các dòng do code tự in; không tính Gradle, logging hệ thống hay banner môi trường.

### 7.9. Giải thích từng kết quả

**Ở nhánh JDK:**

`isAopProxy=true` chứng minh reference từ Spring là proxy. `proxyKind=JDK` cho thấy proxy triển khai interface chứ không subclass `JobService`. Vì vậy `proxyIsJobService=false` nhưng `targetIsJobService=true`. Spring vẫn chạy advice rồi chuyển đến `JobService.perform("outside")`.

**Ở nhánh CGLIB:**

`proxyKind=CGLIB` cho biết proxy là class-based. `proxyIsJobService=true` nhờ kế thừa, nhưng `sameObject=false`: proxy và target vẫn là hai object khác nhau. Đây là điểm cần nhớ khi kiểm tra code bằng `instanceof`.

**Ở cả hai:**

`aspect:runInside` xuất hiện vì caller gọi `bean.runInside()` qua Spring proxy. Sau đó có `target:runInside` và `target:perform:inside` nhưng **không có `aspect:perform` thứ hai**: lời gọi `perform("inside")` phát sinh trong target nên không đi qua proxy.

Nếu chỉ kiểm tra target method logs mà không xem advice logs, bạn có thể tưởng `perform` đã được advice hai lần. Output tách riêng hai loại log để chứng minh thực tế không phải vậy.

## 8. Không dùng getTarget() để tiếp tục method trong @Around

Một lỗi nghiêm trọng là dùng target reference để tự gọi lại method:

```java
@Around("execution(* demo.identity.JobService.perform(..))")
public Object broken(ProceedingJoinPoint pjp) {
    JobService target = (JobService) pjp.getTarget();
    return target.perform("manually");
}
```

Đây là **anti-pattern minh họa**, không đặt vào project chạy. Method này bỏ qua `pjp.proceed()` và gọi trực tiếp `target.perform`. Vì vậy:
- Invocation từ caller đã tới advice nhưng **không tiếp tục những interceptors nằm phía trong**.
- Nếu có transaction, cache, security, tracing hoặc advisor khác phía trong, chúng có thể bị bỏ qua.
- Nó tự thay argument bằng `"manually"`, có thể vi phạm method contract và business expectations.
- Nếu có target source đặc biệt hoặc targetless proxy, cast hoặc dereference có thể thất bại.

Muốn giữ nguyên flow:

```java
return pjp.proceed();
```

Muốn thay argument có chủ đích:

```java
Object[] modified = pjp.getArgs().clone();
modified[0] = "normalized";
return pjp.proceed(modified);
```

Với Spring AOP, `proceed(Object[])` nhận bộ method arguments phù hợp số lượng/thứ tự/kiểu; không phải lý do để gọi trực tiếp target reflection. Đây là snippet minh họa bên trong `@Around` có method nhận ít nhất một String parameter.

## 9. Cẩn thận khi gọi lại qua getThis()

Spring Javadoc lưu ý: nếu **muốn một lời gọi nhận advice**, gọi qua proxy chứ không gọi trực tiếp target. Tuy nhiên, **không có nghĩa lời khuyên đúng là thay `proceed()` bằng gọi method trên `pjp.getThis()` ngay trong chính @Around advice**.

Ví dụ nguy hiểm:

```java
@Around("execution(* demo.identity.JobService.perform(..))")
public Object recursive(ProceedingJoinPoint pjp) {
    JobApi proxy = (JobApi) pjp.getThis();
    return proxy.perform("again");
}
```

Lời gọi `proxy.perform("again")` tạo một invocation **mới** đi qua cùng proxy. Nếu pointcut vẫn match, advice lại chạy, lại gọi proxy và có thể tạo đệ quy không dừng, cuối cùng `StackOverflowError`.

`pjp.proceed()` là API tiếp tục **lời gọi hiện tại**; gọi qua proxy chỉ thích hợp khi bạn thực sự cần tạo **một lời gọi mới** từ nơi khác và đã thiết kế tránh đệ quy/circular aspect behavior.

### getThis() có cần exposeProxy=true không?

**Không.** Trong method `@Aspect` đang chạy qua Spring AOP, `pjp.getThis()` cung cấp current proxy như một phần `JoinPoint`.

Cơ chế khác là:

```java
Object proxy = AopContext.currentProxy();
```

`AopContext.currentProxy()` truy cập proxy từ thread-local context và thường cần bật `exposeProxy=true`; nó gây coupling rõ ràng với Spring AOP và không được khuyến nghị như cách thiết kế business logic thông thường. **Đừng nhầm điều kiện của `AopContext.currentProxy()` với `pjp.getThis()`.**

## 10. getTarget() có thể null hoặc thay đổi theo invocation

### 10.1. Targetless proxy

Spring AOP Javadoc cho phép `getTarget() == null` nếu không có target instance. Đây không phải trường hợp service target trong demo, nhưng quan trọng khi có custom proxy/`TargetSource` không cung cấp target.

Vì thế, trong logging/library infrastructure tránh code:

```java
String targetClass = pjp.getTarget().getClass().getName();
```

nếu bạn không bảo đảm target tồn tại. Một cách chẩn đoán an toàn hơn:

```java
Object target = pjp.getTarget();
String name = target != null
    ? target.getClass().getName()
    : "<no target>";
```

### 10.2. Scoped target và dynamic TargetSource

Với những cấu hình proxy đặc biệt như scoped proxy hoặc custom `TargetSource`, một proxy có thể đại diện cho **những target instances khác nhau tại những thời điểm khác nhau**. Ví dụ request-scoped target có thể khác giữa requests, dù dependency inject vào singleton service là một proxy ổn định.

Do đó:
- Không cache `pjp.getTarget()` từ invocation đầu rồi dùng như target vĩnh viễn.
- Không giả định `getTarget()` luôn trả cùng identity trên mọi lời gọi.
- Không tự biến target reference thành một Spring bean mới được inject vào nơi khác.
- Cẩn thận với object lifecycle/scopes: giữ raw target có thể kéo dài reference ngoài phạm vi vốn được dự tính.

**Trong demo singleton thông thường**, target là `JobService` ổn định, nhưng đây là đặc điểm cấu hình demo, không phải hợp đồng chung của mọi Spring proxy.

### 10.3. Nested/custom proxies

Trong hệ thống tự thêm `ProxyFactory`, custom `BeanPostProcessor` hoặc proxy layers từ library khác, object nằm ngay sau proxy ngoài có thể **bản thân là một proxy khác**. Khi đó một lần `getTarget()` không chắc trả “business POJO cuối cùng”.

`AopUtils.isAopProxy(target)` hoặc `Advised` có thể giúp phân tích cấu trúc, nhưng cần tôn trọng target source lifecycle; không tự liên tục unwrap/cast trong business advice. Với mục tiêu ghi log type thông thường, có thể dùng các utility Spring để xác định target class, song **đọc type metadata không tương đương lấy target object rồi gọi trực tiếp**.

## 11. Quan sát class, bean name và method signature đúng cách

### 11.1. Class của proxy không phải class nghiệp vụ

`pjp.getThis().getClass().getName()` có thể tạo output kiểu generated class của JDK hoặc CGLIB, thay đổi theo version và runtime. Không dùng các substring từ tên class generated làm điều kiện nghiệp vụ.

Để kiểm tra loại proxy:

```java
Object proxy = pjp.getThis();

boolean jdk = AopUtils.isJdkDynamicProxy(proxy);
boolean cglib = AopUtils.isCglibProxy(proxy);
Class<?> targetClass = AopUtils.getTargetClass(proxy);
```

Đây là snippet trong advice cần import `org.springframework.aop.support.AopUtils`. `getTargetClass` trả **type metadata** từ proxy khi có thể, không phải object business target; `pjp.getTarget()` mới trả target object tại join point.

### 11.2. getSignature() để biết method

```java
String methodName = pjp.getSignature().getName();
```

Khi cần kiểu trả về, parameter types hoặc method annotation, có thể dùng:

```java
import org.aspectj.lang.reflect.MethodSignature;

MethodSignature sig = (MethodSignature) pjp.getSignature();
Class<?> returnType = sig.getReturnType();
Class<?>[] parameterTypes = sig.getParameterTypes();
```

Đây là snippets trong Spring `@Around` method-execution advice. Với JDK proxy, method được mô tả có thể gắn với interface declaration trong khi implementation method có metadata khác. Khi đọc annotation ở implementation, cần resolve method cụ thể trong target type đúng bằng utility phù hợp và có tests.

### 11.3. getArgs() không phải getTarget()

`pjp.getArgs()` trả các **actual arguments** của invocation, không trả bean target. Trong ví dụ `perform("outside")`, `getArgs()[0]` là chuỗi `"outside"`. Không nhầm “đối tượng đích” của Spring proxy với “object đối số” truyền vào method.

### 11.4. getThis() cũng không phải Spring bean name

`getThis()` trả proxy reference, không trả bean name. Một target class có thể được đăng ký dưới nhiều Spring bean definitions hoặc với aliases. Nếu nghiệp vụ cần phân loại theo tên Spring bean, cân nhắc `bean(namePattern)` pointcut của Spring AOP hoặc API bean metadata phù hợp; không đoán bean name từ proxy generated class.

## 12. Ảnh hưởng tới transaction, cache và những advice khác

Một service method có thể được bao bởi nhiều advisors: `@Aspect` logging, Spring transaction và caching. Hai điều cần giữ rõ:

1. **Proxy** nhận lời gọi từ client và điều phối toàn bộ chain.
2. **Target** thực thi business method khi phần chain tới được nó.

Ví dụ:

```text
Caller
  |
  v
Spring AOP proxy (getThis)
  |
  +-- Audit @Around
  +-- TransactionInterceptor
  +-- CacheInterceptor
  |
  v
Service target (getTarget)
```

Đây là sơ đồ **thứ tự minh họa**, không phải precedence mặc định bắt buộc.

Nếu bạn gọi thẳng `pjp.getTarget().perform(...)` trong logging advice, transaction/cache advisors phía trong có thể bị bỏ qua. Nếu bạn gọi `pjp.proceed()`, phần interceptor chain tiếp tục với thứ tự đã cấu hình. Nếu cache interceptor đứng ngoài và có cache hit, logging advice có thể **không được gọi** ở invocation đó.

### Có thể tự gọi một method khác trên target để tránh AOP không?

Có thể gọi method trực tiếp trên raw target theo Java semantics, nhưng làm vậy **bỏ qua cross-cutting concerns** và không nên dùng như “tối ưu” ngầm. Nếu một method thực sự không cần advice, chọn pointcut hẹp hơn hoặc thiết kế interface/service boundary phù hợp.

### Self-invocation và @Transactional

`this.saveTransactional()` từ target cũng bypass transaction proxy trong `AdviceMode.PROXY`. Điều này có thể làm annotation `@Transactional` “không hoạt động” dù bean đã được proxy đúng. Thiết kế tốt là chuyển transactional operation sang một Spring bean cộng tác, inject proxy của bean đó và gọi qua dependency.

Tương tự với `@Cacheable` và `@Async` ở proxy mode: việc gọi trên raw target không tự đi qua interceptors của bean ấy.

## 13. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân | Cách kiểm tra hoặc xử lý |
| --- | --- | --- |
| `getThis() instanceof Implementation` false | Đang dùng JDK dynamic proxy | Dùng interface để inject/call hoặc xem nhu cầu class-based proxy |
| `getThis() instanceof Implementation` true nhưng `getThis()==getTarget()` false | CGLIB proxy là subclass khác instance | Phân biệt `instanceof` với reference identity |
| Cast Spring bean proxy thành class implementation lỗi | JDK proxy không subclass implementation | Inject interface, tránh cast không cần thiết |
| Dùng `getTarget()` gọi method mà transaction/audit không chạy | Bỏ qua advisor chain | Trong `@Around` dùng `pjp.proceed()` |
| Dùng `getThis()` gọi lại method đang được advice và stack overflow | Invocation mới lặp cùng advisor | Không thay `proceed()` bằng lời gọi qua proxy |
| `AopContext.currentProxy()` báo không có proxy | Chưa bật `exposeProxy` hoặc không ở lời gọi qua proxy | Dùng `pjp.getThis()` trong advice; tránh phụ thuộc thread-local nếu không cần |
| `getTarget().getClass()` ném `NullPointerException` | Targetless proxy/custom target source | Null-check trước khi dereference |
| Target instance thay đổi giữa requests | Scoped proxy/dynamic target source | Không cache target reference, quản lý scope đúng |
| `this(JobService)` không match nhưng `target(JobService)` match | Proxy JDK chỉ triển khai interface | Dùng type pattern đúng ý nghĩa proxy vs target |
| CGLIB proxy không advice method `final` | Method không override được | Đổi thiết kế class/method hoặc dùng proxy interface phù hợp |
| Method A được advice nhưng A tự gọi B không thấy advice của B | Self-invocation trên target | Di chuyển B sang Spring bean cộng tác và gọi qua proxy |
| Logger in tên class có ký hiệu generated proxy khác nhau | Log runtime proxy class thay vì target type | Dùng `AopUtils.getTargetClass`/metadata được hỗ trợ |
| Aspect không chạy dù class đúng package | Aspect không phải Spring bean, chưa bật AOP hoặc không qua proxy | Kiểm tra registration, `AopUtils.isAopProxy`, pointcut, call path |
| Proxy/target đều là proxy | Nested proxies do custom infrastructure | Xem các proxy layers; không giả định single-level unwrap |
| Quan sát `pjp.getTarget()` rồi lưu lâu trong singleton | Target scope có thể thay đổi | Chỉ sử dụng reference trong phạm vi invocation khi thật sự cần |

## 14. Quy trình debug getThis/getTarget

1. **Xác nhận đúng context.** Bean có được tạo bởi `ApplicationContext` sở hữu `@EnableAspectJAutoProxy` không?
2. **Xác nhận aspect là Spring bean.** `@Aspect` không tự đăng ký component; cần `@Component`/`@Bean`.
3. **Lấy proxy từ container.** Không tạo service bằng `new` rồi yêu cầu AOP hoạt động.
4. **Kiểm tra loại proxy.** `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`.
5. **Trong advice, in type an toàn.** Dùng `getThis()` cho proxy; null-check `getTarget()` nếu infrastructure có thể targetless.
6. **So sánh `==` thay vì `equals`** để xác minh object identity.
7. **Kiểm tra pointcut.** `this(Type)` đang chọn proxy hay `target(Type)` đang chọn target? Có match method execution thật?
8. **Quan sát call path.** Client gọi proxy hay target tự gọi `this.method()`?
9. **Không dùng raw target để “fix” việc cast.** Việc gọi method trên target sẽ bypass AOP.
10. **Không dùng proxy để mô phỏng proceed.** Trong `@Around` luôn cân nhắc `ProceedingJoinPoint.proceed()`.
11. **Nếu có scoped proxy/TargetSource**, xác định identity theo mỗi invocation, tránh assumption singleton.
12. **Nếu có caching/transaction/retry**, xem advisor ordering và method có thật sự được đi tới không.
13. **Viết integration test** trên Spring context với hai cấu hình JDK/CGLIB nếu ứng dụng có thể chạy ở cả hai chế độ.

### Một đoạn chẩn đoán dùng sau startup

```java
Object bean = context.getBean("jobService");
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Đây là snippet phụ thuộc biến `context` và import `AopUtils`, không phải Java file độc lập. Kết quả boolean giúp thấy loại proxy; `getTargetClass` cung cấp class metadata, không phải target instance.

## 15. Kết luận

Trong **Spring AOP**:

- **`getThis()` là Spring AOP proxy** — điểm client gọi vào, nơi advisors/interceptors có cơ hội hoạt động.
- **`getTarget()` là target object hiện hành** — object chứa method implementation thực, có thể `null` trong cấu hình targetless hoặc thay đổi theo dynamic target source.
- **`getThis() == getTarget()` thường false**, kể cả với CGLIB. JDK proxy không phải instance implementation; CGLIB proxy có thể là instance implementation nhờ subclassing, nhưng identity vẫn khác.
- **`this(TypePattern)` kiểm tra proxy**, còn **`target(TypePattern)` kiểm tra target** trong Spring pointcut.
- **`pjp.proceed()` mới là cách tiếp tục current around-advice invocation**. Gọi trực tiếp trên target bỏ qua advice; gọi lại trên proxy có thể tạo invocation mới và đệ quy.
- **Self-invocation của target bỏ qua proxy**; nó không được sửa chỉ bằng đổi JDK sang CGLIB.

Điểm cần nhớ sâu nhất không phải cách lấy tên class, mà là **đường đi của lời gọi**. Khi Spring proxy và target bị xem là một object, logging, transaction, caching, retry và các aspect khác có thể bị áp dụng thiếu hoặc trùng theo cách khó dự đoán.

### Tài liệu chính thức

- [Spring Framework 6.1 Javadoc — MethodInvocationProceedingJoinPoint](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/aspectj/MethodInvocationProceedingJoinPoint.html): mô tả `getThis()` trả Spring proxy, `getTarget()` trả target và khả năng target bằng `null`.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): phân biệt `this`/`target` trong Spring AOP proxy-based system.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, quyền intercept và self-invocation.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `JoinPoint`, `ProceedingJoinPoint` và `proceed()`.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` và bật Spring AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-003">MethodSignature và việc lấy thông tin phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# MethodSignature và việc lấy thông tin phương thức trong Spring AOP

## 1. Mục đích

Khi một advice trong Spring AOP chạy, nó thường cần biết **Spring bean method nào đang được gọi**, kiểu tham số, kiểu trả về, lớp khai báo hoặc annotation gắn trên method để thực hiện audit, tracing và metrics. Thay vì suy từ tên object proxy hoặc phân tích chuỗi log, Spring Aspect có thể đọc metadata một cách có kiểu thông qua `org.aspectj.lang.reflect.MethodSignature`.

Ví dụ đặt **bên trong Spring @Aspect bean**:

~~~java
@Before("execution(* demo..service..*(..))")
public void before(JoinPoint joinPoint) {
    MethodSignature signature =
            (MethodSignature) joinPoint.getSignature();

    String methodName = signature.getName();
    Class<?> returnType = signature.getReturnType();
    Class<?>[] types = signature.getParameterTypes();
    Method method = signature.getMethod();
}
~~~

Đây là snippet minh họa, không phải Java class hoàn chỉnh: cần import `org.aspectj.lang.JoinPoint`, `org.aspectj.lang.annotation.Before`, `org.aspectj.lang.reflect.MethodSignature` và `java.lang.reflect.Method`. Trong Spring AOP, join point được advice là **method execution của Spring-managed bean**, được gọi qua JDK/CGLIB proxy; nó không bao phủ mọi method trên mọi Java object.

**Phạm vi:** chỉ tập trung cách khai thác `MethodSignature` trong Spring Aspect, khác biệt giữa method metadata và runtime arguments, cách tìm đúng method triển khai khi proxy theo interface, lỗi thường gặp và ví dụ Spring Framework chạy được. Không bàn lại lý thuyết AspectJ bytecode weaving.

## 2. Phân biệt những đối tượng trong một Spring AOP advice

| Kiểu | API | Thông tin/công dụng |
| --- | --- | --- |
| `JoinPoint` | `org.aspectj.lang.JoinPoint` | Invocation context, signature, proxy, target và runtime argument values |
| `ProceedingJoinPoint` | `org.aspectj.lang.ProceedingJoinPoint` | Dành cho `@Around`, kế thừa JoinPoint và cho gọi `proceed(...)` |
| `Signature` | `org.aspectj.lang.Signature` | Tên, modifiers, declaring type và các dạng hiển thị chữ ký |
| `MethodSignature` | `org.aspectj.lang.reflect.MethodSignature` | Kiểu signature dành riêng cho method execution: Java Method, parameter types/names, return type, exception types |
| `Method` | `java.lang.reflect.Method` | Java reflection: annotation, declaring class, generic types, modifiers và bridge method |

Sơ đồ:

~~~text
Spring bean caller
       |
       v
   AOP proxy --------> target method
       |
       v
@Aspect advice(JoinPoint point)
       |
       +-- point.getThis()       : proxy reference
       +-- point.getTarget()     : target object
       +-- point.getArgs()       : giá trị runtime
       +-- point.getSignature()  : Signature
                                    |
                                    v
                             MethodSignature
                               |        |
                         getMethod()  getReturnType()
                               |
                               v
                     java.lang.reflect.Method
~~~

**MethodSignature mô tả chữ ký**, còn `JoinPoint.getArgs()` chứa **giá trị của từng đối số tại invocation**. Ví dụ method `greet(String name, int times)` khai báo `String,int`, nhưng một lần caller có thể truyền `"Ada", 2`. Không đánh đồng loại tham số với giá trị tham số.

Một phân biệt nữa: `getThis()` thường đại diện AOP proxy; `getTarget()` đại diện đối tượng đích mà proxy ủy quyền tới. Trong JDK proxy, chúng không cùng class. Điều này tác động lớn đến cách đọc method annotation và declaring type.

## 3. Các MethodSignature API đáng dùng

### 3.1. Tên và declaring type

~~~java
MethodSignature s = (MethodSignature) point.getSignature();

String name = s.getName();
Class<?> declaredIn = s.getDeclaringType();
String qualifiedType = s.getDeclaringTypeName();
int modifiers = s.getModifiers();
boolean isPublic = java.lang.reflect.Modifier.isPublic(modifiers);
~~~

`getName()` trả tên method, chẳng hạn `greet`. `getDeclaringType()` và `getDeclaringTypeName()` cho kiểu xuất phát từ **signature mà proxy đang xử lý**; với JDK dynamic proxy có thể là một interface, không nhất thiết là implementation class. `getModifiers()` là tập cờ Java modifiers, có thể dùng `java.lang.reflect.Modifier` để diễn giải.

`s.toShortString()`, `s.toLongString()` và `s.toString()` thuận tiện cho log chẩn đoán. **Không parse các chuỗi hiển thị đó bằng regex** để xây định danh ổn định: định dạng string có thể phụ thuộc library version; hãy lấy `name`, `declaringType`, `parameterTypes` riêng.

### 3.2. Kiểu trả về và ngoại lệ khai báo

~~~java
Class<?> returnType = s.getReturnType();
Class<?>[] declaredExceptions = s.getExceptionTypes();
~~~

`getReturnType()` mô tả **kiểu trả về đã khai báo**: `String.class` cho `String greet(...)`, `void.class` cho `void send()`, `int.class` cho `int sum(...)`. Nó không phải object kết quả của một invocation. Nếu method khai báo `List<String>`, raw class thông thường là `List.class` chứ không bảo toàn `String` element type.

`getExceptionTypes()` mô tả các exception trong **throws declaration** của method; nó không cho biết method thực sự đã ném lỗi nào. Muốn đọc giá trị trả về thì dùng `@AfterReturning(returning="...")`; muốn đọc exception đã ném thì dùng `@AfterThrowing(throwing="...")` hoặc `@Around` khi yêu cầu điều khiển flow thực sự tồn tại.

### 3.3. Kiểu tham số và tên tham số

~~~java
Class<?>[] parameterTypes = s.getParameterTypes();
String[] parameterNames = s.getParameterNames();
Object[] argumentValues = point.getArgs();
~~~

- `parameterTypes` có các `Class<?>` theo thứ tự khai báo: `String.class` và `int.class` trong ví dụ.
- `parameterNames` có thể chứa tên như `name`/`times`, nhưng **không được bảo đảm luôn tồn tại** với mọi bytecode/proxy.
- `argumentValues` là giá trị runtime, ví dụ `["Ada", 2]`; primitive `int` được boxed thành `Integer` trong `Object[]`.

Bật compiler flag `-parameters` khi kiểm soát project Java 17+. Mặc dù vậy, vẫn viết code có fallback khi `getParameterNames()` trả `null` hoặc metadata không đầy đủ. Tránh tạo logging/audit schema quan trọng chỉ phụ thuộc tên tham số của một third-party dependency.

### 3.4. Java reflection Method

~~~java
Method method = s.getMethod();
String name = method.getName();
Class<?> declaringClass = method.getDeclaringClass();
Class<?>[] formalTypes = method.getParameterTypes();
java.lang.reflect.Type genericType = method.getGenericReturnType();
~~~

`getMethod()` là cầu nối để đọc annotation, modifiers, type/generic metadata, `isBridge()` và `isSynthetic()`. Tuy nhiên, **đừng tự giả định `getMethod()` luôn trả method trên target implementation**: khi dùng JDK proxy, nó có thể là method khai báo trên interface.

Điểm này rất quan trọng nếu annotation audit chỉ đặt trên method triển khai: `method.getAnnotation(AuditTag.class)` có thể `null` khi `method` xuất phát từ interface.

## 4. Vấn đề JDK proxy: tìm method triển khai để lấy annotation

Giả sử interface `GreetingPort` có:

~~~java
public interface GreetingPort {
    String greet(String name, int times);
}
~~~

Implementation có annotation:

~~~java
public class GreetingService implements GreetingPort {
    @AuditTag("greeting")
    @Override
    public String greet(String name, int times) {
        return "Hello " + name + " x" + times;
    }
}
~~~

Hai đoạn trên là minh họa riêng; source hoàn chỉnh ở phần 5. Nếu Spring AOP chọn **JDK dynamic proxy**, interface method và implementation method là hai đối tượng Java reflection `Method` khác nhau. Annotation nằm trên implementation method, không tự lan sang interface method.

**Cách đọc không đủ an toàn:**

~~~java
MethodSignature s = (MethodSignature) point.getSignature();
AuditTag tag = s.getMethod().getAnnotation(AuditTag.class);
// Có thể null dù implementation method có @AuditTag.
~~~

**Cách Spring-native:**

~~~java
MethodSignature s = (MethodSignature) point.getSignature();

Method methodOnSignature = s.getMethod();
Class<?> targetClass = point.getTarget().getClass();

Method targetMethod = org.springframework.aop.support.AopUtils
        .getMostSpecificMethod(methodOnSignature, targetClass);

AuditTag tag = targetMethod.getAnnotation(AuditTag.class);
~~~

`AopUtils.getMostSpecificMethod(method, targetClass)` tìm method tương ứng trên target class nếu có; nó còn xử lý vấn đề bridge method theo tài liệu Spring. Với ví dụ `GreetingService`, method đích được tìm thấy sẽ chứa `@AuditTag`.

Điểm cần nhớ:

1. `point.getTarget()` là target trong Spring AOP invocation thông thường; đừng lấy `point.getThis().getClass()` là generated proxy class rồi tìm trực tiếp annotation tại đó.
2. Nếu method trên interface không có implementation method cụ thể hoặc target class không resolve được, helper có thể trả method ban đầu; nên viết fallback thích hợp.
3. `target.getClass()` có thể liên quan custom target source/đối tượng đặc biệt; code library tổng quát nên kiểm tra `getTarget()!=null`.
4. Khi annotation nằm trên interface chứ không phải implementation, cần xác định rõ chính sách đọc annotation trên **cả hai vị trí**, thay vì mặc định annotation ở đâu cũng “inherited”.
5. Helper tìm method **không tự làm advice match**. Pointcut phải match và invocation phải qua Spring proxy trước khi code trong advice có cơ hội chạy.

### Tìm custom meta-annotation theo Spring

~~~java
AuditTag tag =
    org.springframework.core.annotation.AnnotatedElementUtils
        .findMergedAnnotation(targetMethod, AuditTag.class);
~~~

`findMergedAnnotation` có ích khi project dùng Spring composed/meta-annotations hoặc aliasing phù hợp. Khác với `getAnnotation` của Java reflection thuần; **không** có nghĩa nó luôn đọc được mọi annotation trong mọi interface hierarchy hay đúng bằng semantics của `@annotation(...)` pointcut. Nếu yêu cầu interface annotation fallback:

~~~java
AuditTag tag = AnnotatedElementUtils.findMergedAnnotation(
        targetMethod, AuditTag.class);
if (tag == null) {
    tag = AnnotatedElementUtils.findMergedAnnotation(
            methodOnSignature, AuditTag.class);
}
~~~

Đây là hai snippet minh họa dùng các biến/import đã giải thích. Ưu tiên implementation trước, interface sau là **chính sách cụ thể của ví dụ**, không phải một quy tắc phổ quát cho mọi annotation hệ thống. Với Spring Boot/Spring Framework app lớn, viết integration test cho placement annotation thực tế.

## 5. Project Java 17+ Spring Framework 6.2: ví dụ chạy được

Chương trình sẽ dùng **JDK interface proxy**, một custom `@AuditTag` nằm **chỉ trên implementation method**, một `@Before` advice đọc `MethodSignature` và annotation, cùng lời gọi method không match để so sánh.

Cấu trúc:

~~~text
spring-method-signature/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── AuditTag.java
    ├── GreetingPort.java
    ├── GreetingService.java
    └── MetadataAspect.java
~~~

Yêu cầu JDK 17+, Maven 3.9+, Spring Framework **6.2.0**, AspectJ Weaver **1.9.22**. Các version cố định để học; không khẳng định đây là bản mới nhất. Không cần AspectJ weaving/javaagent.

### 5.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-method-signature</artifactId>
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
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` chứa IoC/Java config; `spring-aop` có `AopUtils` và auto-proxy framework; `aspectjweaver` hỗ trợ Spring phân tích kiểu `@AspectJ` pointcut, **không tự kích hoạt weaving**. `<parameters>true</parameters>` cung cấp tên tham số compile-time nhưng không phải yếu tố quyết định stdout bên dưới.

### 5.2. Annotation đánh dấu implementation method

Tạo `src/main/java/demo/AuditTag.java`:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditTag {
    String value();
}
~~~

`RUNTIME` retention để có thể đọc khi advice inspect `Method`. `METHOD` target để annotation chỉ đánh dấu method. `value` mang tag dùng cho audit.

### 5.3. Interface và implementation

`src/main/java/demo/GreetingPort.java`:

~~~java
package demo;

public interface GreetingPort {
    String greet(String name, int times);
    String echo(String message);
}
~~~

`src/main/java/demo/GreetingService.java`:

~~~java
package demo;

public class GreetingService implements GreetingPort {
    @AuditTag("greeting")
    @Override
    public String greet(String name, int times) {
        System.out.println("TARGET: greet " + name + " x" + times);
        return "Hello " + name + " x" + times;
    }

    @Override
    public String echo(String message) {
        System.out.println("TARGET: echo " + message);
        return message;
    }
}
~~~

Annotation **chỉ có ở implementation** `GreetingService.greet`. Cả `greet` và `echo` thuộc cùng interface, nhưng pointcut phía dưới chỉ chọn `greet`. Method `echo` giúp xác nhận mọi lời gọi qua Spring proxy không tự trở thành một advice event.

### 5.4. Aspect sử dụng MethodSignature

`src/main/java/demo/MetadataAspect.java`:

~~~java
package demo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Aspect
public class MetadataAspect {
    private final AtomicInteger adviceCount = new AtomicInteger();

    @Before("execution(* demo.GreetingPort.greet(..))")
    public void inspect(JoinPoint point) {
        if (!(point.getSignature() instanceof MethodSignature sig)) {
            throw new IllegalStateException("Not a method join point");
        }

        Method method = sig.getMethod();
        Class<?> targetClass = point.getTarget().getClass();

        Method actual = AopUtils.getMostSpecificMethod(
                method, targetClass);

        AuditTag tag = AnnotatedElementUtils.findMergedAnnotation(
                actual, AuditTag.class);
        if (tag == null) {
            throw new IllegalStateException("Missing annotation");
        }

        String formalTypes = Arrays.stream(actual.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));

        String methodInfo =
                actual.getDeclaringClass().getSimpleName()
                + "." + actual.getName()
                + "(" + formalTypes + ")->"
                + actual.getReturnType().getSimpleName();

        adviceCount.incrementAndGet();
        System.out.println("METHOD: " + methodInfo);
        System.out.println("TAG: " + tag.value());
        System.out.println("ARGS: " + Arrays.toString(point.getArgs()));
    }

    public int count() {
        return adviceCount.get();
    }
}
~~~

Những ý quan trọng trong code:

1. `@Aspect` chỉ đánh dấu class; class này sẽ được đăng ký thành Spring bean qua `@Bean`.
2. `@Before` được chọn vì chỉ đọc/in trước target; không cần quyền can thiệp `proceed()` của `@Around`.
3. `point.getSignature()` được cast với `instanceof MethodSignature` để kiểm tra loại join point trong code.
4. `sig.getMethod()` có thể đến từ `GreetingPort`; helper `getMostSpecificMethod` xác định implementation method nếu có.
5. `actual.getParameterTypes()` và `actual.getReturnType()` trả kiểu khai báo; ghép thành `GreetingService.greet(String,int)->String` mà không phụ thuộc tên tham số.
6. `findMergedAnnotation(actual,AuditTag.class)` đọc tag `greeting` trực tiếp trên implementation method.
7. `point.getArgs()` trả giá trị runtime và có thể in `[Ada, 2]` (khác với `[String,int]`).
8. `AtomicInteger` chỉ tăng trong advice. Nếu `echo` không match, bộ đếm không tăng.
9. Không sử dụng `getParameterNames()` trong stdout vì giá trị có thể phụ thuộc metadata compiler/interface đã nạp.

Với production logging, tránh in mọi `getArgs()` một cách vô điều kiện: một trong các phần tử có thể chứa password, access token, số thẻ, dữ liệu cá nhân hoặc `toString()` có side effect. Demo chỉ có tên và một số nguyên không nhạy cảm.

### 5.5. Configuration và main method

`src/main/java/demo/App.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class App {
    @Bean
    public GreetingPort greetingPort() {
        return new GreetingService();
    }

    @Bean
    public MetadataAspect metadataAspect() {
        return new MetadataAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            GreetingPort service = context.getBean(GreetingPort.class);
            MetadataAspect aspect = context.getBean(MetadataAspect.class);

            boolean jdk = AopUtils.isJdkDynamicProxy(service);
            System.out.println("JDK_PROXY: " + jdk);

            String greeted = service.greet("Ada", 2);
            System.out.println("RESULT: " + greeted);

            String echoed = service.echo("ping");
            System.out.println("ECHO: " + echoed);

            int count = aspect.count();
            System.out.println("ADVICE_COUNT: " + count);

            if (!jdk || !"Hello Ada x2".equals(greeted)
                    || !"ping".equals(echoed) || count != 1) {
                throw new IllegalStateException(
                        "MethodSignature verification failed");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

`@EnableAspectJAutoProxy(proxyTargetClass = false)` cho phép JDK interface proxy khi bean có interface; không phải một mệnh lệnh buộc mọi bean đều dùng JDK proxy. Với project này, aspect có advisor match `greet` và `GreetingService implements GreetingPort`, nên JDK proxy là kết quả mong đợi. Chương trình kiểm tra thực tế bằng `AopUtils.isJdkDynamicProxy` và ném lỗi nếu điều kiện không đúng.

`new GreetingService()` được gọi **trong `@Bean` factory**; giá trị trả về được Spring quản lý và có thể được auto-proxy. Caller lấy reference thông qua `ApplicationContext`, không giữ một raw target reference khác để bỏ qua advice.

### 5.6. Lệnh chạy và stdout dự kiến

Chạy tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do code `System.out.println` tạo ra:

~~~text
JDK_PROXY: true
METHOD: GreetingService.greet(String,int)->String
TAG: greeting
ARGS: [Ada, 2]
TARGET: greet Ada x2
RESULT: Hello Ada x2
TARGET: echo ping
ECHO: ping
ADVICE_COUNT: 1
CHECK: PASS
~~~

Maven/SLF4J có thể in thêm dòng ngoài đoạn trên. Bản này mô tả **application stdout mong đợi khi project được chạy**; chưa phải biên bản thực thi Maven trên máy của người đọc.

Kết quả chứng minh:

- Service reference Spring trả về là JDK proxy.
- Method metadata resolution tìm đúng **implementation** thay vì mắc kẹt ở interface.
- `@AuditTag("greeting")` trên implementation được tìm thấy.
- `String,int` là **parameter types**, `[Ada, 2]` là **argument values**.
- Dòng advice xuất hiện trước business method; `echo` không có advice tương ứng.
- `ADVICE_COUNT: 1` xác nhận aspect chạy đúng một lần.
- `CHECK: PASS` chỉ xuất hiện khi kết quả kinh doanh, proxy type và advice count đáp ứng kỳ vọng.

## 6. Tái sử dụng MethodSignature theo loại advice

### 6.1. @Before: metadata trước invocation

~~~java
@Before("execution(* demo..service..*(..))")
public void before(JoinPoint jp) {
    MethodSignature signature =
            (MethodSignature) jp.getSignature();
    System.out.println(signature.getName());
}
~~~

Đây là method advice minh họa; package thực phải khớp expression. Khi chỉ cần đọc tên method và arguments trước business call, `@Before` là lựa chọn đơn giản hơn `@Around`.

### 6.2. @AfterReturning: metadata và kết quả thực

~~~java
@AfterReturning(
    pointcut = "execution(* demo..service..*(..))",
    returning = "value",
    argNames = "value"
)
public void afterReturning(JoinPoint jp, Object value) {
    MethodSignature s = (MethodSignature) jp.getSignature();
    System.out.println("Declared: " + s.getReturnType().getSimpleName());
    System.out.println("Actual: " + value);
}
~~~

`getReturnType()` là **declared return type**, còn `value` là actual return value trên nhánh method **trả về bình thường**. Nếu target ném exception, `@AfterReturning` không được gọi.

### 6.3. @AfterThrowing: metadata và exception thực

~~~java
@AfterThrowing(
    pointcut = "execution(* demo..service..*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterThrowing(JoinPoint jp, Throwable error) {
    MethodSignature s = (MethodSignature) jp.getSignature();
    System.out.println(s.getName() + " threw "
            + error.getClass().getSimpleName());
}
~~~

`error` là exception thực tế; `s.getExceptionTypes()` là các loại **khai báo trong method throws**. `@AfterThrowing` quan sát failure, không tự biến thrown exception thành successful result.

### 6.4. @Around: metadata và chuyển tiếp

~~~java
@Around("execution(* demo..service..*(..))")
public Object around(ProceedingJoinPoint jp) throws Throwable {
    MethodSignature s = (MethodSignature) jp.getSignature();
    System.out.println(s.getName());
    return jp.proceed();
}
~~~

`ProceedingJoinPoint` kế thừa `JoinPoint` nên có cùng API `getSignature()`. Khác với `@Before`, `@Around` có quyền bỏ qua `proceed()`, thay arguments/return hoặc ném lại exception. Đừng dùng quyền này chỉ để in tên method khi `@Before` đáp ứng đủ.

Nếu thực sự dùng `proceed(Object[])` để thay arguments, số phần tử của mảng mới trong **Spring AOP** phải tương ứng method target; `getParameterTypes()` giúp xác định signature, `getArgs()` giúp đọc current values. Đây là một bài toán khác với đơn thuần **lấy metadata**.

Các snippets trong mục 6 là **những lựa chọn độc lập**, không nên thêm tất cả vào `MetadataAspect` của project chính khi muốn output đúng như phần 5.

## 7. Overload, generic, bridge method và annotation placement

### 7.1. Method overload

Hai method này có cùng tên:

~~~java
public void dispatch(String code) {
}

public void dispatch(String code, int retries) {
}
~~~

`MethodSignature.getName()` trả `dispatch` trong cả hai. Nếu tạo metrics key chỉ từ tên, hai overload bị gộp sai. Dùng thêm parameter types:

~~~java
String types = java.util.Arrays
        .stream(s.getParameterTypes())
        .map(Class::getTypeName)
        .collect(java.util.stream.Collectors.joining(","));

String key = s.getDeclaringTypeName()
        + "#" + s.getName() + "(" + types + ")";
~~~

Đây là snippet trong advice đã có biến `MethodSignature s`. Cần quyết định key theo signature proxy hay implementation method; nếu muốn class target cụ thể, dùng method đã resolve.

### 7.2. Generic bridge

~~~java
interface Converter<T> {
    T convert(T value);
}

class StringConverter implements Converter<String> {
    @Override
    public String convert(String value) {
        return value.trim();
    }
}
~~~

Đây là snippet Java minh họa riêng. Java generics erasure có thể tạo **bridge method** ở implementation để duy trì polymorphism. `Method.isBridge()`/`isSynthetic()` cho biết một số đặc điểm reflection; `AopUtils.getMostSpecificMethod` xử lý bridge resolution cho metadata lookup theo tài liệu Spring. Nếu bạn tự dùng reflection `getDeclaredMethod` chỉ theo tên/arity, dễ gặp sai method hoặc thiếu annotation trên user-declared method.

### 7.3. Annotation trên interface vs implementation

Method annotation của Java không tự động kế thừa từ interface method xuống implementation method theo `@Inherited`; cơ chế `@Inherited` chủ yếu liên quan class annotations. Cần quy ước rõ:

- Đặt annotation trên implementation method: resolve target method rồi tìm annotation.
- Đặt annotation trên interface method: cần tìm tại interface method khi target method không có tag.
- Cần meta-annotation hoặc aliasing: cân nhắc `AnnotatedElementUtils.findMergedAnnotation`.
- Aspect dùng pointcut `@annotation(...)` để **chọn join point**: semantics matching không nhất thiết đồng nhất một cách máy móc với Spring merged annotation lookup trong method advice.

### 7.4. Method name, return type và runtime arguments có ý nghĩa khác nhau

| Thứ cần biết | Cách đọc | Ví dụ |
| --- | --- | --- |
| Tên method | `signature.getName()` | `greet` |
| Kiểu tham số | `signature.getParameterTypes()` | `String,int` |
| Tên tham số | `signature.getParameterNames()` | `name,times` (nếu metadata có) |
| Giá trị thực tế | `joinPoint.getArgs()` | `"Ada",2` |
| Kiểu trả về khai báo | `signature.getReturnType()` | `String` |
| Giá trị trả về thực tế | `@AfterReturning(returning=...)` | `Hello Ada x2` |
| Exceptions khai báo | `signature.getExceptionTypes()` | Kiểu trong `throws` |
| Exception thực tế | `@AfterThrowing(throwing=...)` | `IllegalStateException` nếu thực sự thoát ra |
| Java reflection Method | `signature.getMethod()` | Có thể là interface method |
| Method implementation | `AopUtils.getMostSpecificMethod(...)` | `GreetingService.greet` |
| Proxy vs target | `joinPoint.getThis()` / `getTarget()` | JDK proxy / GreetingService |

Bảng này giúp tránh nhầm **static metadata** với **dynamic invocation state**.

## 8. Lỗi thường gặp và hướng gỡ

**1. `ClassCastException` hoặc method metadata không phải implementation:** nếu dùng JDK proxy, reference và `Method` có thể gắn với interface. Không cast proxy xuống implementation class; dùng interface API cho caller và `AopUtils.getMostSpecificMethod` cho metadata.

**2. Annotation trả `null`:** kiểm tra nó nằm trên interface hay implementation. `signature.getMethod().getAnnotation(...)` có thể nhìn nhầm method; resolve target method rồi đọc. Nếu vẫn null, xem retention `RUNTIME`, annotation target `METHOD` và cấu hình meta-annotation.

**3. Parameter names bị thiếu:** bảo đảm `-parameters` được bật ở module chứa method; xem tên trên interface và implementation có đồng bộ không. Dù bật flag, luôn có fallback khi xử lý library bên thứ ba.

**4. Return type không giống loại object được trả:** declared type `List`/`Number` có thể khác runtime type `ArrayList`/`Integer`. `getReturnType()` không phải giá trị mà target đã trả; cần `@AfterReturning`/`@Around` cho outcome.

**5. Advice không chạy, nên không thấy metadata:** có thể do pointcut không khớp, aspect chưa đăng ký làm bean, object tự tạo ngoài Spring, bean chưa được auto-proxy hoặc self-invocation. Xác minh `AopUtils.isAopProxy` trên reference caller thực sự dùng. Không chuyển `@Before` thành `@Around` chỉ để mong method được intercept.

**6. `getDeclaringType()` không phải tên bean:** declaring type là lớp/interface khai báo chữ ký; một Spring class có thể có nhiều bean instances hoặc nhiều bean names. `MethodSignature` không tự mang bean name, request ID hay user identity.

**7. Log method signature bằng `toLongString()` rồi parse bằng regex:** cách này giòn, phụ thuộc format/version. Lấy accessor có kiểu và tự format stable key nếu cần.

**8. Nhật ký vô tình lộ dữ liệu:** `Arrays.toString(jp.getArgs())` trong ví dụ chỉ dùng dữ liệu học tập. Với production Spring AOP, chỉ log field đã được cho phép, áp dụng redaction và tránh gọi `toString` trên object nhạy cảm.

**9. Dùng `getExceptionTypes()` để đo failures:** đây là declared exceptions, không phải actual exceptions. Dùng `@AfterThrowing` với typed binding nếu cần metrics failure.

**10. Dùng reflection `Method.invoke` ngay trong advice để “gọi method thật”:** như vậy có thể làm thay đổi/đi vòng khỏi interceptor chain, dẫn tới double invocation hoặc mất transaction/cache/security. Trong `@Around` cần `ProceedingJoinPoint.proceed()` để tiếp tục chain theo đúng cơ chế Spring AOP; method reflection ở đây chỉ để **đọc metadata**.

## 9. Kiểm thử qua ApplicationContext

Khi cần kiểm thử `MethodSignature` trong Spring AOP, test phải gọi đúng **Spring bean proxy**. Việc tự tạo `new GreetingService()` chỉ kiểm tra logic Java, không tạo join point advice.

Có thể viết JUnit 5 integration test sau, sau khi bổ sung `junit-jupiter` và `maven-surefire-plugin` trong `pom.xml`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodSignatureTest {
    @Test
    void implementationAnnotationIsVisibleThroughJdkProxy() {
        try (var context =
                new AnnotationConfigApplicationContext(App.class)) {
            GreetingPort service = context.getBean(GreetingPort.class);
            MetadataAspect aspect = context.getBean(MetadataAspect.class);

            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertEquals("Hello Ada x2", service.greet("Ada", 2));
            assertEquals("ping", service.echo("ping"));
            assertEquals(1, aspect.count());
        }
    }
}
~~~

Đây là **file test Java hoàn chỉnh** nhưng không thể chạy chỉ bằng `pom.xml` tối thiểu ở phần 5 nếu chưa khai báo JUnit dependency và Surefire plugin. Trong test, `MetadataAspect` sẽ ném exception nếu không tìm được implementation annotation; vì thế test vừa kiểm tra advice thực thi, vừa kiểm tra metadata resolution đủ để không gặp lỗi.

Sau khi bổ sung test dependencies, chạy:

~~~bash
mvn clean test
~~~

Nếu muốn test riêng các method metadata, có thể dùng thêm `MethodSignature.getParameterTypes()` và `AopUtils.getMostSpecificMethod(...)` trực tiếp ở các integration test. Tuy nhiên, đọc method reflection ngoài context **không thay thế** việc thử Spring AOP advice thực tế đi qua JDK proxy.

## 10. Checklist khi viết Spring Aspect cần metadata method

- [ ] `@Aspect` class đã đăng ký như Spring bean; target là Spring bean đúng context.
- [ ] Caller gọi qua Spring proxy, không dùng raw object hoặc self-invocation để kiểm thử advice.
- [ ] Lấy `MethodSignature` từ `JoinPoint.getSignature()`; dùng `instanceof` khi helper cần an toàn.
- [ ] Đọc method name, return type, parameter types qua accessor; không parse string representation.
- [ ] Dùng `joinPoint.getArgs()` cho runtime values, không nhầm với parameter types/names.
- [ ] `getParameterNames()` được xử lý với fallback; `-parameters` bật khi phù hợp.
- [ ] Với JDK proxy, `getMethod()` có thể là method interface; không mặc định annotation ở đó.
- [ ] Resolve `AopUtils.getMostSpecificMethod(...)` trước khi tra annotation trên implementation.
- [ ] Phân biệt declared return type và actual return value; declared throws và actual thrown exception.
- [ ] Khi có overload/bridge/generic methods, test theo đúng proxy configuration của Spring.
- [ ] Hạn chế reflection và string construction trong hot-path advice; cache metadata theo `Method` nếu có nhu cầu và chính sách hợp lệ.
- [ ] Không log toàn bộ arguments nhạy cảm; xác định rõ mục tiêu observability.

## 11. Tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `JoinPoint`/`ProceedingJoinPoint` và metadata trong Spring advice.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB và self-invocation.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring AOP method execution trên managed beans.
- [AopUtils Javadoc — Spring Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/support/AopUtils.html): `getMostSpecificMethod` và bridge methods.
- [AnnotatedElementUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html): annotation lookup hỗ trợ merged/composed metadata.
- [MethodSignature — AspectJ API](https://www.javadoc.io/doc/org.aspectj/aspectjrt/1.9.22/org/aspectj/lang/reflect/MethodSignature.html): các accessor `getMethod`, `getParameterTypes`, `getParameterNames`, `getReturnType` và `getExceptionTypes`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-004">Ràng buộc đối số bằng args và tham số advice</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc đối số bằng `args` và tham số advice trong Spring AOP

## 1. Mục tiêu: đưa giá trị đối số của lời gọi Spring bean vào advice có kiểu rõ ràng

Khi viết một Spring `@Aspect`, nhiều lời advice cần biết **giá trị nào caller thực sự truyền vào method đích**. Ví dụ một advice audit muốn lấy `accountId` và `channel` mà không phải tự duyệt `Object[]`, ép kiểu bằng tay rồi đoán vị trí phần tử.

Spring AOP hỗ trợ **argument binding** bằng `args(...)` trong AspectJ-style pointcut expression. Khi dùng **tên của một formal parameter** thay vì tên kiểu Java trong `args`, Spring có thể **truyền giá trị argument được chọn vào tham số của advice**.

```java
@Before(
    value = "execution(* com.example.payment.PaymentService.pay(..))"
          + " && args(accountId, amount)",
    argNames = "accountId,amount"
)
public void beforePay(String accountId, java.math.BigDecimal amount) {
    System.out.println("accountId=" + accountId);
    System.out.println("amount=" + amount);
}
```

Đây là **method fragment** trong một class `@Aspect` được Spring quản lý, không phải chương trình Java độc lập. Giả định `PaymentService.pay(String, BigDecimal)` là method được caller gọi **qua Spring AOP proxy**.

Các thành phần có trách nhiệm riêng:

- `execution(...PaymentService.pay(..))`: giới hạn join point vào method nghiệp vụ mong muốn.
- `args(accountId, amount)`: lấy **hai arguments thực tế theo thứ tự** và đưa chúng vào những biến được đặt tên trong pointcut expression.
- `String accountId` và `BigDecimal amount`: kiểu Java Spring/AspectJ dùng để bind và kiểm tra tương thích.
- `argNames="accountId,amount"`: ánh xạ chính xác tên xuất hiện trong expression với formal parameters của method advice.
- Body `beforePay`: logic audit thực sự, **không** nằm trong expression.

**Phạm vi:** Bài tập trung vào Spring Aspect, `@AspectJ` annotation-style và **Spring proxy-based AOP**. Các khái niệm AspectJ ngoài Spring chỉ được nêu để tránh nhầm API, không mở rộng sang compile-time/load-time weaving.

## 2. `args` có hai cách sử dụng: type pattern và binding

Điểm dễ nhầm nhất là biểu thức `args(...)` **không phải lúc nào cũng có cùng ý nghĩa**.

### 2.1. Dùng kiểu Java để chọn invocation

```text
args(java.io.Serializable)
```

Đây là **type pattern**: nó chọn lời gọi có **đúng một argument** có kiểu runtime phù hợp với `Serializable`, trong phạm vi Spring proxy join points.

Ví dụ target method khai báo:

```java
public String send(Object payload) { /* ... */ }
```

Nếu truyền `"hello"`, argument runtime là `String` và implements `Serializable`. Nếu truyền `new PlainPayload()` không implements interface đó, điều kiện khác đi dù chữ ký `send(Object)` vẫn y nguyên.

### 2.2. Dùng tên parameter để bind giá trị

```text
args(payload)
```

Nếu `payload` được khai báo trong signature của named pointcut hoặc method advice, nó là **binding identifier**. Ví dụ:

```java
@Before(
    value = "execution(* com.example.msg.MessageService.send(..))"
          + " && args(payload)",
    argNames = "payload"
)
public void inspect(java.io.Serializable payload) {
    System.out.println(payload.getClass().getSimpleName());
}
```

Khi runtime argument phù hợp `Serializable`, `payload` nhận **chính argument đang được truyền**, không phải một `Serializable` instance Spring tự tạo.

**Điểm so sánh:**

| Cú pháp | Ý nghĩa | Có bind giá trị vào advice parameter không? |
| --- | --- | --- |
| `args(java.io.Serializable)` | Runtime type filter | Không, vì đây là tên kiểu đầy đủ |
| `args(payload)` với `Serializable payload` | Runtime type filter **và** binding | Có, lấy object tương ứng |
| `args(payload, channel)` | Bind hai arguments theo vị trí | Có nếu signature/kiểu tương thích |
| `args(payload, ..)` | Bind argument đầu, cho phép phần còn lại | Có |
| `args(.., channel)` | Bind argument cuối, cho phép phần trước | Có |
| `execution(* *.send(java.io.Serializable))` | Lọc kiểu tham số **khai báo trong method signature** | Không tự bind argument |

Spring docs nhấn mạnh `args(java.io.Serializable)` khác `execution(* *(java.io.Serializable))`: `args` có thể dựa trên **đối số thực tế tại runtime**, còn `execution` xét **chữ ký phương thức khai báo**. Đây là điểm cần kiểm tra trước khi sửa một pointcut đang “không chạy”.

## 3. Tên binding phải khớp nhau ở ba nơi

Một advice binding đầy đủ có ba chỗ cần thống nhất:

```java
@Before(
    value = "execution(* com.example.order.OrderService.create(..))"
          + " && args(request, source)",
    argNames = "request,source"
)
public void before(OrderRequest request, String source) {
    // ...
}
```

| Vị trí | Ký hiệu | Tác dụng |
| --- | --- | --- |
| Pointcut string | `args(request, source)` | Tham chiếu các tên binding từ invocation |
| Advice annotation | `argNames="request,source"` | Cung cấp ánh xạ tên tham số tường minh |
| Advice Java method | `OrderRequest request, String source` | Kiểu và tham số thực sự nhận giá trị |

Đây là **fragment**: `OrderRequest` là lớp cần tồn tại trong project. Đặt tên `request` trên cả ba vị trí giúp người đọc không phải suy diễn bằng reflection.

Ví dụ đổi tên **chỉ** trong Java:

```java
@Before(
    value = "execution(* com.example.order.OrderService.create(..))"
          + " && args(request, source)",
    argNames = "request,source"
)
public void before(OrderRequest payload, String channel) {}
```

Java vẫn có thể biên dịch vì annotation là chuỗi. Nhưng `argNames` khai báo tên binding khác tên source parameters; một số cấu hình vẫn bind được theo **thứ tự danh sách tường minh**, nhưng điều này dễ gây nhầm khi refactor. Cách an toàn là **đồng bộ tên ở cả ba nơi** và chạy context test. Đặc biệt nếu xóa `argNames`, việc Spring tìm parameter names từ build metadata có thể thay đổi kết quả.

Nếu sai **số lượng**, **thứ tự binding**, **type** hoặc identifier không thể resolve, Spring thường báo lỗi khi tạo advisor/khởi động context hoặc advice không áp dụng như mong muốn. Không được coi annotation expression string là được Java compiler type-check đầy đủ.

## 4. `argNames`, `-parameters` và cơ chế tìm tên argument

`args(payload)` trong pointcut dùng **tên binding** `payload`, không phải tên parameter của target method. Spring phải nối tên này với đúng tham số của lời advice. Cách dễ kiểm soát nhất là khai báo `argNames = "payload"`; Java `-parameters` là nguồn metadata khác cho Spring tìm tên tham số khi không khai báo tường minh. Nếu còn nhiều biến hoặc annotation/return bindings, việc để Spring suy luận có thể mơ hồ.

### 4.1. Trường hợp có `JoinPoint` đầu tiên

Tham số đầu tiên đặc biệt `JoinPoint`, `ProceedingJoinPoint` (cho `@Around`) hoặc `JoinPoint.StaticPart` có thể **bỏ tên khỏi `argNames`**. Ví dụ:

```java
@Before(value = "execution(* com.example.payment.PaymentService.pay(..))"
        + " && args(accountId, amount)",
        argNames = "accountId,amount")
public void audit(JoinPoint jp, String accountId, BigDecimal amount) {
    System.out.println(jp.getSignature().getName() + ": " + accountId);
}
```

Đây là đoạn minh họa nằm trong Spring-managed `@Aspect`, cần imports `JoinPoint`, `BigDecimal` và `@Before`; `jp` do Spring cấp, hai tham số còn lại đến từ binding. Khi không bind biến nào khác, lời advice chỉ nhận `JoinPoint` không cần `argNames`. `getArgs()` đọc mảng giá trị theo vị trí; `args(name)` ràng buộc giá trị có kiểu và tham gia điều kiện match. Bài **argNames, tên tham số và `-parameters`** ở chương này đi sâu vào cấu hình Maven/Gradle và thứ tự phát hiện tên.

## 5. `args` dựa trên vị trí: một, hai hoặc nhiều tham số

Giả sử target method:

```java
public Receipt charge(String accountId, BigDecimal amount, String channel);
```

Mỗi biểu thức dưới đây có ý nghĩa khác nhau:

```text
args(accountId, amount, channel)
args(accountId, ..)
args(.., channel)
args(accountId, *, channel)
args(java.lang.String, java.math.BigDecimal, java.lang.String)
```

- `args(accountId, amount, channel)`: bind đủ **ba vị trí**, signature của named pointcut/advice cũng phải tương ứng.
- `args(accountId, ..)`: bind **tham số đầu**; phía sau có thể là không hoặc nhiều arguments.
- `args(.., channel)`: bind **tham số cuối**; phần trước có thể nhiều vị trí.
- `args(accountId, *, channel)`: bind vị trí đầu và cuối, giữa là **một** argument bất kỳ. Nếu dùng pattern có wildcard và binding phức tạp, chạy context test để bảo đảm parser/type matching tương thích.
- `args(String, BigDecimal, String)`: chỉ kiểm tra kiểu theo vị trí, **không tạo named bindings**.

Ví dụ chỉ quan tâm `accountId` đầu tiên dù method có thêm parameters:

```java
@Before(
    value = "execution(* com.example.billing.BillingService.charge(..))"
          + " && args(accountId, ..)",
    argNames = "accountId"
)
public void beforeCharge(String accountId) {
    // ...
}
```

Đây là method fragment hợp lệ về ý tưởng: `accountId` bind vào argument đầu, không yêu cầu advice khai báo các parameters còn lại.

**Không nhầm vị trí với tên tham số method đích.** Caller không truyền “argument tên `accountId`”; JVM truyền một **dãy values có thứ tự**. Tên `accountId` trong pointcut chỉ là **tên binding** mà advice tác giả đặt. Đổi tên tham số `id` trong target Java không tự khiến `args(accountId,..)` sai nếu vị trí và kiểu vẫn hợp lệ.

### 5.1. Overloads

Với hai overload:

```java
void submit(Object payload);
void submit(String payload);
```

Java compiler quyết định overload dựa trên **kiểu biểu thức ở call site**, trước khi Spring AOP áp dụng pointcut. Sau đó `args(payload)` quan sát **runtime argument** của method execution đã được chọn.

Nếu code viết `Object value = "hello"; service.submit(value);`, Java gọi overload `submit(Object)` dù `value` trỏ tới `String`. Advice `args(payload)` với `String payload` có thể vẫn bind được runtime `String` khi method execution đi qua proxy phù hợp. Nó không làm method đang được gọi chuyển thành overload `submit(String)`.

## 6. Ràng buộc đối số cùng với named `@Pointcut`

Khi nhiều advice cần cùng điều kiện chọn method và cùng formal argument types, tạo named pointcut:

```java
@Pointcut(
    value = "execution(* com.example.order.OrderService.place(..))"
          + " && args(orderId, channel)",
    argNames = "orderId,channel"
)
public void orderPlaced(String orderId, String channel) {}
```

Rồi tham chiếu lại:

```java
@Before(
    value = "orderPlaced(orderId,channel)",
    argNames = "orderId,channel"
)
public void beforeOrder(String orderId, String channel) {
    // ...
}
```

```java
@AfterReturning(
    pointcut = "orderPlaced(orderId,channel)",
    returning = "receipt",
    argNames = "orderId,channel,receipt"
)
public void afterOrder(
        String orderId, String channel, Receipt receipt) {
    // ...
}
```

Đây là các **fragments** của một class `@Aspect`; `Receipt` và `OrderService.place(..)` phải có thật trong project. Named pointcut có formal parameters nhưng **body của `orderPlaced(...)` rỗng**: Spring không gọi Java body này để lấy arguments. Nó là khai báo metadata về cách bind values.

Chú ý `returning="receipt"` bind **return value** của method, trong khi `args(orderId,channel)` bind **input arguments**. Không dùng cùng một identifier cho hai đối tượng có ý nghĩa khác nhau.

### 6.1. Binding names ở nhiều tầng

Khi pointcut trong class chung:

```java
@Pointcut(
    value = "execution(* com.example.order.OrderService.place(..))"
          + " && args(orderId, channel)",
    argNames = "orderId,channel"
)
public void placed(String orderId, String channel) {}
```

Aspect khác có thể dùng:

```java
@Before(
    value = "com.example.aop.CommonPointcuts.placed(orderId,channel)",
    argNames = "orderId,channel"
)
public void audit(String orderId, String channel) {}
```

**Fully-qualified class + method reference** giúp Spring tìm named pointcut bên ngoài class. Method trong `CommonPointcuts` cần Java visibility phù hợp (thường `public`). Không cần khởi tạo `CommonPointcuts` thành bean chỉ vì nó chứa public named pointcuts được tham chiếu bằng tên; còn class `@Aspect` chứa advice phải là Spring bean.

## 7. Binding ở các loại advice khác nhau

`args` không chỉ dùng cho `@Before`. Trong Spring, advice methods có thể nhận values từ pointcut bindings, với điều kiện lựa chọn đúng signature/annotation:

| Advice | Dùng `args` để nhận input? | Binding bổ sung | Điều đáng lưu ý |
| --- | --- | --- | --- |
| `@Before` | Có | Có thể nhận `JoinPoint` đầu tiên | Không thay argument caller chỉ bằng gán lại parameter local. |
| `@After` | Có | Có thể nhận `JoinPoint` | Chạy theo semantics của after (finally), không phải chỉ sau thành công. |
| `@AfterReturning` | Có | `returning="result"` | `result` là return value; gán lại advice parameter không thay value caller nhận. |
| `@AfterThrowing` | Có | `throwing="error"` | Binding exception; chỉ chạy khi lời gọi kết thúc bằng lỗi phù hợp. |
| `@Around` | Có | `ProceedingJoinPoint` là tham số đầu tiên | Có thể `proceed()` hoặc `proceed(Object[])`; advice quyết định có và khi nào chuyển tiếp. |

Ví dụ kết hợp args với return result:

```java
@AfterReturning(
    pointcut = "execution(* com.example.order.OrderService.place(..))"
             + " && args(orderId, ..)",
    returning = "result",
    argNames = "orderId,result"
)
public void afterPlace(String orderId, String result) {
    // orderId: input từ lời gọi, result: giá trị trả về bình thường.
}
```

Ví dụ kết hợp args với exception:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.order.OrderService.place(..))"
             + " && args(orderId, ..)",
    throwing = "error",
    argNames = "orderId,error"
)
public void onFailure(String orderId, RuntimeException error) {
    // ...
}
```

Cả hai là **fragments**. Trong ứng dụng thật, return type, exception type và signature của `place` phải tương thích. `@AfterThrowing` không có nhiệm vụ tự “nuốt lỗi”; nếu cần fallback result cho caller, cân nhắc `@Around` với contract rõ.

### 7.1. `@Around` và `ProceedingJoinPoint` đi cùng binding

```java
@Around(
    value = "execution(* com.example.order.OrderService.place(..))"
          + " && args(orderId, channel)",
    argNames = "orderId,channel"
)
public Object around(
        org.aspectj.lang.ProceedingJoinPoint pjp,
        String orderId,
        String channel) throws Throwable {

    System.out.println("channel=" + channel);
    return pjp.proceed();
}
```

`ProceedingJoinPoint` đứng đầu, **không cần nằm trong danh sách `argNames`**. Hai Java parameters còn lại được bind từ arguments đang được truyền qua proxy.

Nếu muốn **thực sự đổi arguments chuyển cho target**, cần `proceed(newArgs)` với array đúng số lượng/kiểu của underlying method. Ví dụ trong Spring AOP:

```java
Object[] newArgs = pjp.getArgs().clone();
newArgs[1] = "normalized-channel";
Object outcome = pjp.proceed(newArgs);
return outcome;
```

Chỉ dùng khi underlying method **thực sự có tham số thứ hai kiểu tương thích với `String`**. Gán `channel = "..." ` trong advice chỉ sửa **biến cục bộ**; nó không thay argument của invocation đang được proceed như `proceed(newArgs)`.

Spring docs nêu `proceed(Object[])` ở Spring proxy AOP sử dụng array phù hợp với **arguments của underlying method**, có khác biệt cần phân biệt với AspectJ weaving thuần. Không dùng để âm thầm thay sensitive inputs mà không có policy rõ ràng.

## 8. Ví dụ đầy đủ Java 17+: bind input, nhận `JoinPoint`, bind cả return value

Ví dụ tập trung vào ba mục tiêu:

1. Target có `send(Object payload, String channel)`. Method signature cố định `Object` nhưng runtime payload có thể khác kiểu.
2. Named pointcut `serializableMessages(...)` bind một `Serializable payload` và một `String channel`.
3. `@Before` nhận `JoinPoint` đầu tiên + input bindings; `@AfterReturning` nhận input bindings + `String result`.
4. Gọi cùng method ba lần với `String`, một record không implements `Serializable`, và `Integer`.
5. Chỉ hai lần có Serializable payload kích hoạt cả hai advice. Cả ba lần target vẫn chạy và caller đều nhận result.

Dự án dùng **Java 17+, Spring Framework 6.1.14, AspectJ weaver 1.9.22.1, Maven**, không bật AspectJ weaving. Các version được cố định để minh họa chứ không khẳng định là mới nhất.

### 8.1. Cấu trúc thư mục

```text
spring-aop-args-binding/
├── pom.xml
└── src/main/java/com/example/binding/
    ├── MessageService.java
    ├── BindingAspect.java
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
    <artifactId>spring-aop-args-binding</artifactId>
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
                <configuration>
                    <parameters>true</parameters>
                </configuration>
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

`spring-context` cung cấp `ApplicationContext` và các Spring AOP dependencies phù hợp trong cây phụ thuộc; `aspectjweaver` cho phép Spring parse/match `@AspectJ` expressions. `parameters=true` giữ parameter-name metadata theo yêu cầu khi cần; ví dụ cũng đặt `argNames` tường minh để không phụ thuộc riêng việc tự suy luận.

### 8.3. `src/main/java/com/example/binding/MessageService.java`

```java
package com.example.binding;

public class MessageService {

    public String send(Object payload, String channel) {
        String typeName = payload.getClass().getSimpleName();
        System.out.println("[TARGET] channel=" + channel
                + ", type=" + typeName);

        return "sent:" + channel + ":" + typeName;
    }
}
```

Method cố ý khai báo parameter đầu là `Object`, nhưng gọi với `String`, `Integer` và một record không implements `Serializable`. Trong project demo, `payload` luôn non-null để output xác định; nếu production API cho phép null, cần kiểm tra trước `getClass()`.

### 8.4. `src/main/java/com/example/binding/BindingAspect.java`

```java
package com.example.binding;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class BindingAspect {

    private final AtomicInteger beforeCount = new AtomicInteger();
    private final AtomicInteger afterCount = new AtomicInteger();

    @Pointcut(
        value = "execution(public java.lang.String "
              + "com.example.binding.MessageService.send(..))"
              + " && args(payload,channel)",
        argNames = "payload,channel"
    )
    public void serializableMessages(
            Serializable payload, String channel) {}

    @Before(
        value = "serializableMessages(payload,channel)",
        argNames = "payload,channel"
    )
    public void before(
            JoinPoint jp,
            Serializable payload,
            String channel) {

        beforeCount.incrementAndGet();
        System.out.println("[BEFORE] method="
                + jp.getSignature().getName()
                + ", channel=" + channel
                + ", type=" + payload.getClass().getSimpleName());
    }

    @AfterReturning(
        pointcut = "serializableMessages(payload,channel)",
        returning = "result",
        argNames = "payload,channel,result"
    )
    public void after(
            Serializable payload,
            String channel,
            String result) {

        afterCount.incrementAndGet();
        System.out.println("[AFTER] type="
                + payload.getClass().getSimpleName()
                + ", result=" + result);
    }

    public int beforeCount() {
        return beforeCount.get();
    }

    public int afterCount() {
        return afterCount.get();
    }
}
```

Phân tích cụ thể:

- Named pointcut nhận **hai formal parameters**: `Serializable payload`, `String channel`.
- `args(payload,channel)` giới hạn đúng **hai arguments** trong `send(Object,String)` và bind theo vị trí.
- `Serializable payload` khiến điều kiện **dynamic runtime type** của payload quan trọng: `String` và `Integer` phù hợp; `PlainPayload` trong main không phù hợp.
- `@Before` có `JoinPoint` đầu tiên. `argNames` **chỉ liệt kê** `payload,channel`, đúng khả năng đặc biệt của Spring.
- `@AfterReturning` tham chiếu cùng named pointcut nhưng còn bind `result` qua `returning="result"`.
- `String result` là return value do target tạo khi method kết thúc bình thường; việc nhận thêm input arguments không thay đổi giá trị trả về.
- Hai counters giúp phân biệt việc advice thật sự chạy với việc Spring chỉ có một aspect bean singleton.

Ví dụ không dùng `payload.toString()` để tránh log dữ liệu message thực tế; chỉ in **kiểu payload**, channel và string result vốn được tạo có chủ đích. Với ứng dụng production, cần kiểm tra cả channel/result có chứa thông tin nhạy cảm không.

### 8.5. `src/main/java/com/example/binding/AppConfig.java`

```java
package com.example.binding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public MessageService messageService() {
        return new MessageService();
    }

    @Bean
    public BindingAspect bindingAspect() {
        return new BindingAspect();
    }
}
```

`@EnableAspectJAutoProxy` kích hoạt hạ tầng Spring auto-proxy. `proxyTargetClass=true` chọn CGLIB class proxy vì `MessageService` không implement business interface. `BindingAspect` phải được đăng ký thành bean — `@Aspect` riêng lẻ **không phải** `@Component`.

### 8.6. `src/main/java/com/example/binding/DemoApp.java`

```java
package com.example.binding;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {

    public record PlainPayload(String label) {
        // Java record không tự implements Serializable.
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            MessageService service = context.getBean(MessageService.class);
            BindingAspect aspect = context.getBean(BindingAspect.class);

            System.out.println("isAopProxy="
                    + AopUtils.isAopProxy(service));

            System.out.println("result1="
                    + service.send("hello", "web"));

            System.out.println("result2="
                    + service.send(new PlainPayload("plain"), "queue"));

            System.out.println("result3="
                    + service.send(Integer.valueOf(7), "batch"));

            System.out.println("beforeCount=" + aspect.beforeCount());
            System.out.println("afterCount=" + aspect.afterCount());
        }
    }
}
```

`PlainPayload` là `record` trong `DemoApp` nhưng **không** implement `Serializable`. `String` và `Integer` implement `Serializable` trên JDK 17+; vì thế hai lời gọi đầu/cuối tạo những kết quả match khác nhau từ **cùng một method và cùng một Spring proxy**.

### 8.7. Chạy và stdout dự kiến

Trong thư mục chứa `pom.xml`, với Maven và JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.binding.DemoApp
```

Những dòng do code chương trình và aspect in ra, theo đúng thứ tự (không tính thông báo Maven/SLF4J tùy môi trường):

```text
isAopProxy=true
[BEFORE] method=send, channel=web, type=String
[TARGET] channel=web, type=String
[AFTER] type=String, result=sent:web:String
result1=sent:web:String
[TARGET] channel=queue, type=PlainPayload
result2=sent:queue:PlainPayload
[BEFORE] method=send, channel=batch, type=Integer
[TARGET] channel=batch, type=Integer
[AFTER] type=Integer, result=sent:batch:Integer
result3=sent:batch:Integer
beforeCount=2
afterCount=2
```

**Tại sao đúng như vậy?**

1. Aspect có pointcut `execution(...MessageService.send(..))`; Spring tạo proxy vì method có thể được advice.
2. Khi nhận `String`, runtime payload type tương thích `Serializable` nên Spring bind `payload="hello"`, `channel="web"` và chạy `@Before`.
3. Target chạy và trả `sent:web:String`; `@AfterReturning` bind **cả input** và **output** rồi in dòng `[AFTER]`.
4. Khi nhận `PlainPayload`, runtime payload không tương thích `Serializable`, nên **hai advice không chạy**, nhưng target vẫn chạy bình thường và trả `sent:queue:PlainPayload`.
5. Khi nhận `Integer`, runtime payload tương thích `Serializable`; cả hai advice chạy lần thứ hai.
6. Counter cuối là `2` cho mỗi advice trong khi target chạy `3` lần. Điều này chứng minh **binding và type filter được xét theo từng invocation**, không phải một điều kiện cố định bằng tên method.

**Lưu ý kiểm chứng:** Code/output được thiết kế theo API của Spring Framework 6.1; đây là stdout **dự kiến từ source**. Trước khi dùng trong dự án sản xuất, hãy chạy Maven/integration test trên JDK và Spring version mục tiêu, vì classpath/proxy configuration và advisor order thực tế có thể khác.

## 9. Những góc cạnh cần kiểm thử khi binding arguments

### 9.1. `null`

`null` không có runtime class cụ thể, nhưng matching của `args` còn liên quan **kiểu khai báo của method, logic binder và Spring/AspectJ implementation**. Không đưa ra một quy tắc tuyệt đối kiểu “`args(SomeType)` luôn match null” hoặc “luôn bỏ null” cho mọi chữ ký.

Cách an toàn:

- Nếu method cho phép `null`, viết integration test với **đúng pointcut và Spring version**.
- Không gọi `payload.getClass()`, `payload.toString()` hoặc unbox nếu `payload` có thể null.
- Nếu advice cần kiểm tra **null/non-null value**, cân nhắc `JoinPoint.getArgs()` và logic explicit sau khi pointcut giới hạn method hợp lý.
- Dùng `args` cho **ràng buộc kiểu**; đừng giả định nó là một validator đầy đủ cho mọi business input.

### 9.2. Primitive và wrapper types

Method có `void submit(int count)` khai báo primitive, còn object arguments của interception API có thể liên quan boxing. Đối với `args(count)` và advice parameter `Integer` hoặc `int`, hãy xác minh matching/binding khi ứng dụng có primitive method signatures. Nếu chỉ cần chọn method khai báo `int`, biểu thức `execution(* *.submit(int))` phù hợp hơn.

Khi thay arguments trong `@Around` bằng `proceed(Object[])`, `null` không thể được unbox thành primitive. Không dựa vào phép chuyển kiểu ngầm để bỏ qua kiểm tra validity của input.

### 9.3. Varargs là một array parameter tại JVM

Method:

```java
void submit(String... ids);
```

có JVM parameter tương ứng một array `String[]`. Dù caller viết `submit("A","B")`, không tự suy rằng pointcut `args(id1,id2)` sẽ có hai method arguments tách biệt. Cần đối chiếu varargs/array matching của Spring/AspectJ version sử dụng và thiết kế binding theo JVM signature khi cần.

### 9.4. Generic type erasure

Spring docs cảnh báo không thể dùng `args` để xác minh đầy đủ generic element type của một collection, ví dụ bind `Collection<MyType>` rồi hy vọng Spring kiểm tra mọi phần tử `MyType` ở runtime.

Nếu cần nhận collection:

```java
@Before(
    value = "execution(* com.example..BatchService.process(..))"
          + " && args(items)",
    argNames = "items"
)
public void beforeBatch(java.util.Collection<?> items) {
    for (Object item : items) {
        // Tự kiểm tra loại phần tử nếu policy thật sự cần.
    }
}
```

Đây là **method fragment** cần bean/class `BatchService`. `Collection<?>` tránh lời cam kết generic element type không thể kiểm tra tại runtime. Đừng đọc generic `Collection<Order>` trên advice signature như một bộ lọc tự động của Spring AOP.

### 9.5. Annotation binding khác `args`

`args(payload)` bind method input value. `@args(SomeAnnotation)` là một **pointcut designator khác**: nó xét annotation trên kiểu runtime của arguments, không phải binding tương đương với `args`.

`@annotation(audited)` có thể bind **annotation instance gắn với method** vào advice parameter; `target(bean)` có thể bind target object; `this(proxy)` có thể bind Spring proxy object. Khi kết hợp các binding này, danh sách `argNames` phải đúng với **tất cả ordinary formal parameters** mà advice cần bind.

Ví dụ fragment:

```java
@Before(
    value = "execution(* com.example..Service.*(..))"
          + " && args(payload,..)"
          + " && target(bean)",
    argNames = "payload,bean"
)
public void inspect(Object payload, Object bean) {
    // payload: một argument, bean: target instance.
}
```

Không nhầm `bean` trong `target(bean)` với Spring-specific `bean(namePattern)` pointcut: hai ký hiệu này có chức năng khác nhau.

## 10. Spring proxy boundary: điều kiện bắt buộc trước cả binding

Dù `args` và `argNames` viết đúng, advice **không tự chạy** nếu method không đi qua Spring AOP proxy:

```text
Spring Bean A -> Spring proxy của Bean B -> advice binding args -> target method
Spring Bean B -> this.method(...)         -> bỏ qua Spring AOP proxy
new BeanB().method(...)                   -> đối tượng ngoài Spring proxy
```

Cần xác minh:

- **Aspect được đăng ký thành Spring bean** qua `@Component` hoặc `@Bean`, chứ không chỉ có `@Aspect`.
- `@EnableAspectJAutoProxy` hoặc Spring Boot AOP auto-configuration tương ứng đã hoạt động.
- Bean đích nằm trong đúng `ApplicationContext` có hạ tầng auto-proxy.
- Caller dùng reference Spring cấp; không tự `new` service hoặc giữ raw reference.
- Với JDK proxy, method cần khả dụng theo interface proxy; với CGLIB, `final`/`private` method không được proxy override theo cách thông thường.
- Method đang được gọi thực sự khớp pointcut `execution`/scope **và** `args` dynamic conditions.

**Một `AopUtils.isAopProxy(bean) == true` chưa chứng minh `args(payload)` match invocation.** Proxy có thể tồn tại vì advisor khác; còn kiểu runtime của argument thay đổi trên từng lời gọi.

## 11. Vì sao nên kết hợp `execution` với `args`?

Spring có các phép kiểm pointcut tĩnh và động. `execution` giúp Spring xác định nhanh **method/class signatures** có liên quan; `args` có thể cần kiểm tra arguments ở **mỗi invocation**.

Biểu thức quá rộng:

```java
@Before("args(java.io.Serializable)")
public void anySerializable() {}
```

có thể chọn nhiều method không liên quan trên các Spring beans được proxy. Nếu advice thực hiện logging/audit, điều này làm tăng chi phí và nguy cơ ghi dữ liệu nhạy cảm.

Biểu thức hẹp hơn:

```java
@Before(
    "execution(public * com.example.billing.BillingService.submit(..))"
    + " && args(java.io.Serializable,..)"
)
public void chosenSerializable() {}
```

chỉ xem những lời gọi có method signature/mục tiêu đúng phạm vi cần thiết, rồi lọc theo runtime argument type.

**Không đưa business value logic vào `args` một cách tưởng tượng.** Ví dụ `args(java.math.BigDecimal)` không kiểm tra `amount > 100000`; cần bind `BigDecimal amount` rồi kiểm tra giá trị trong advice hoặc validation/service tương ứng.

Cân nhắc hiệu năng khi có nhiều advice pointcuts động, nhưng **ưu tiên độ đúng và phạm vi rõ** trước khi tối ưu vi mô. Với Spring `MethodMatcher`, có static matching và khả năng runtime matching; không nên khẳng định mỗi biểu thức `args` nhất thiết làm Spring kiểm tra mọi method của mọi bean ở mỗi lời gọi.

## 12. Lỗi thường gặp khi bind arguments

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| Spring báo không tìm được parameter name | Thiếu `argNames`/`-parameters`, expression phức tạp không suy luận được | Khai báo `argNames` đủ, biên dịch Java với `-parameters` khi phù hợp. |
| Named pointcut có 2 parameters nhưng advice tham chiếu không có arguments | Không khớp số lượng bindings | Dùng `pointcutName(payload,channel)` và signature tương ứng. |
| Binding `payload` bị null gây NPE | Advice giả định mọi argument non-null | Kiểm tra null, test API có nullable values. |
| `args(Serializable)` chạy với `String` dù method khai báo `Object` | Đây là runtime type matching, hành vi đúng | Phân biệt `args` với `execution` static signature. |
| Cùng method, một lần advice chạy và lần khác không | Actual argument types khác nhau | Đo đầu vào trên cùng Spring proxy; kiểm tra binding type. |
| Advice nhận sai argument position | Biểu thức và Java signature khác thứ tự | Kiểm tra `args(first,second)`, `argNames` và thứ tự parameters. |
| `argNames` khai báo thiếu `JoinPoint` đầu tiên | Đây có thể là hành vi được Spring cho phép | Phân biệt first `JoinPoint`/ `ProceedingJoinPoint` với parameters bình thường. |
| `@AfterReturning` bị nhầm với input binding | `returning="result"` là return value, không phải method input | Dùng `args(input)` riêng và khai báo cả input/result trong `argNames`. |
| `@AfterThrowing` không nhận exception | Tên `throwing` khác tham số advice, type không tương thích hoặc method không ném lỗi | Đồng bộ `throwing`, advice parameter và test failure paths. |
| Gán `payload = replacement` trong advice nhưng target vẫn nhận giá trị cũ | Gán lại local advice parameter không sửa invocation arguments | Dùng `@Around` + `proceed(newArgs)` nếu policy yêu cầu và type hợp lệ. |
| Bind collection generic không lọc được item type | Erasure và giới hạn matching generic types | Dùng `Collection<?>` rồi kiểm tra phần tử hoặc thiết kế typed request object. |
| Có `@Aspect` nhưng advice không chạy | Aspect chưa là bean, proxy không bật, caller đi qua `this`/`new` hoặc pointcut không match | Kiểm tra context, proxy, advisor, method và đường gọi. |
| `args` match nhiều nơi ngoài dự kiến | Pointcut chỉ dựa vào kiểu argument chung | Giới hạn `execution`/package/class, bổ sung negative tests. |
| `@args` được dùng nhầm thay `args` | `@args` xét annotation của runtime argument type | Dùng đúng designator theo nhu cầu binding. |

## 13. Checklist kiểm thử binding đúng trong Spring

Một integration test đáng tin cần xác minh **cả registration, type matching, value binding và hành vi nghiệp vụ**:

1. Spring `ApplicationContext` có target bean và aspect bean như thiết kế.
2. `AopUtils.isAopProxy(target)` đúng khi advisors có thể áp dụng, nhưng không chỉ dựa vào kiểm tra này.
3. Method được caller gọi qua Spring proxy, không qua self-invocation hoặc instance tự `new`.
4. Với argument thuộc kiểu match, advice nhận **đúng object và vị trí**, không chỉ đúng tên class.
5. Với argument thuộc kiểu không match, target vẫn chạy nhưng advice counter không tăng.
6. Nếu dùng hai arguments, kiểm thử đảo thứ tự/khác kiểu khi API cho phép để phát hiện binding nhầm.
7. Nếu có `JoinPoint`, `getSignature().getName()`, `getArgs()`, `getThis()`, `getTarget()` có đúng nghĩa không.
8. Nếu kết hợp `@AfterReturning`/`@AfterThrowing`, test normal return và exception separately, bao gồm tên binding input/output.
9. Nếu có `@Around` sửa arguments, assert target nhận **newArgs** thật và caller nhận return value hợp lệ.
10. Thử null, primitive, varargs, generic collections nếu business API có các trường hợp ấy.
11. Bảo vệ thông tin nhạy cảm: không log toàn bộ payload, password, token chỉ vì `args(payload)` cho truy cập dễ dàng.
12. Khi refactor tên tham số advice, named pointcut, method signatures hoặc nâng Spring version, chạy lại tests cho binding annotations và build metadata.

Ví dụ ma trận của project ở phần 8:

```text
send("hello", "web")             -> target +1, BEFORE +1, AFTER +1
send(new PlainPayload(...), ...) -> target +1, BEFORE +0, AFTER +0
send(Integer.valueOf(7), ...)   -> target +1, BEFORE +1, AFTER +1

Total target = 3; beforeCount = 2; afterCount = 2
```

Các con số này phụ thuộc **named pointcut đã khai báo**, chứ không phải mọi Spring Aspect có `args` đều chạy đúng 2 lần.

## 14. Tóm tắt cơ chế và tài liệu chính thức

| Câu hỏi | Trả lời ngắn |
| --- | --- |
| `args(java.io.Serializable)` làm gì? | Lọc theo kiểu **argument runtime** phù hợp, không tự bind named value. |
| `args(payload)` làm gì? | Bind giá trị argument vào formal parameter có tên `payload` với kiểu đã khai báo. |
| `argNames` dùng để làm gì? | Cung cấp ánh xạ parameter names rõ ràng cho Spring Aspect binding. |
| `JoinPoint` có cần xuất hiện trong `argNames` không? | Có thể bỏ tên của **leading JoinPoint/ProceedingJoinPoint/StaticPart** theo quy tắc Spring. |
| Named `@Pointcut` có bind values không? | Có, bằng formal parameters và `args(...)`, rồi tham chiếu pointcut kèm parameters từ advice. |
| `@AfterReturning` nhận input và output cùng lúc được không? | Có: `args(input)` và `returning="result"`. |
| `@Around` có đổi arguments chuyển đến target không? | Có: dùng `proceed(Object[])` với đúng số lượng/kiểu; gán lại local parameter không đủ. |
| `args` có lọc theo giá trị như `amount > 100` không? | Không; cần kiểm tra value trong advice/validation. |
| Code bên ngoài Spring hoặc self-invocation có chạy binding advice không? | Không theo cơ chế Spring proxy AOP thông thường. |

Tài liệu Spring Framework để đối chiếu:

- Declaring Advice — Advice Parameters, Passing Parameters to Advice, Determining Argument Names: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Declaring a Pointcut — Supported Pointcut Designators, `args`: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Pointcut API — Static and Dynamic MethodMatcher: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Proxying Mechanisms — Spring proxy boundary: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận:** `args` trong Spring Aspect là cầu nối giữa **runtime invocation arguments** và **formal parameters có kiểu của advice**. Để dùng đúng, phải hiểu `args(type)` khác `args(name)`, ràng buộc **đúng vị trí/kiểu/tên**, phối hợp `argNames` hoặc `-parameters` hợp lý và kiểm thử trên **Spring AOP proxy thực tế**. Khi cần thêm return value hay exception, dùng `returning`/`throwing` độc lập với input binding; khi cần thay arguments đến target, dùng `@Around` và `proceed(newArgs)` theo contract method.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-005">Ràng buộc annotation vào tham số advice</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc annotation vào tham số advice trong Spring AOP

## 1. Bài học giải quyết vấn đề gì?

Trong **Spring Framework AOP sử dụng cú pháp @AspectJ**, một pointcut không chỉ có thể hỏi “method execution này có annotation phù hợp không?”, mà còn có thể **truyền chính annotation instance đó vào tham số của advice**. Advice nhờ vậy đọc được metadata như mã audit, nhóm nghiệp vụ, nhãn metric hoặc cấp độ theo dõi mà không phải tự tìm annotation bằng reflection.

Một ví dụ:

```java
@AuditAction("create")
public String create(String orderId) {
    return "created:" + orderId;
}
```

Và trên Spring-managed aspect:

```java
@Before(
    value = "execution(public * com.example.binding.BillingService.*(..))"
          + " && @annotation(action)",
    argNames = "action"
)
public void beforeAction(JoinPoint jp, AuditAction action) {
    System.out.println(action.value() + ":" + jp.getSignature().getName());
}
```

Trong biểu thức, **`action` không phải tên một class annotation**. Nó là **biến binding**, được nối với tham số Java `AuditAction action` của advice. Khi một Spring AOP proxy intercept method phù hợp, Spring/AspectJ pointcut machinery đưa annotation object vào advice để đọc `action.value()`.

**Phạm vi bài:** cơ chế binding annotation **trong Spring AOP cho Spring-managed beans**, cách khai báo annotations và advice parameters, dùng `@annotation`/`@within`/`@target`/`@args` khi thích hợp, `argNames`, Spring proxy và kiểm thử. Các ý niệm AOP tổng quát và AspectJ weaving không phải trọng tâm; `@EnableAspectJAutoProxy` **không** bật compile-time/load-time weaving.

## 2. Phân biệt chọn method theo annotation và bind annotation

Cả hai biểu thức sau có thể chọn method được đánh dấu `@AuditAction`, nhưng vai trò của chúng khác nhau.

**Dạng kiểm tra annotation có tồn tại:**

```java
@Before("@annotation(com.example.binding.AuditAction)")
public void beforeAnnotatedMethod() {
    // Biết method execution match marker, không nhận annotation object.
}
```

**Dạng binding để đọc metadata:**

```java
@Before(
    value = "@annotation(action)",
    argNames = "action"
)
public void beforeAnnotatedMethod(AuditAction action) {
    String auditCode = action.value();
}
```

Dạng thứ nhất dùng **fully qualified annotation type name**. Dạng thứ hai dùng **tên biến `action`**, được suy từ chữ ký advice/`argNames`; kiểu Java `AuditAction` của tham số ràng buộc annotation sẽ được bind.

Điểm nhớ:

- `@annotation(SomeType)`: yêu cầu **method join point** mang annotation có type ấy.
- `@annotation(action)` và `AuditAction action`: yêu cầu annotation type `AuditAction` và **truyền annotation instance** vào advice.
- `action.value()`: đọc metadata được khai báo trên annotation; không cần `method.getAnnotation(...)` thủ công trong case đơn giản.
- **Binding không tự tạo Spring proxy**. Method phải thuộc target Spring bean, aspect là bean và invocation đi qua proxy thích hợp.
- **Tên biến phải khớp chính xác**; annotation attribute như `value`, `code` không phải tên binding trừ khi advice parameter thật sự mang tên đó.

Không nhầm annotation type của Java `@Target` (chỉ nơi có thể đặt annotation) với pointcut expression `@target(...)` (xét annotation ở class của target object Spring).

## 3. Annotation Java cần khai báo đúng cho Spring AOP

Một annotation cho **method**:

```java
package com.example.binding;

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

Một annotation cho **class/type**:

```java
package com.example.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceGroup {
    String value();
}
```

Từng annotation nằm trong file Java riêng có cùng tên public type. Trong ví dụ `ServiceGroup` là annotation trên target class; `AuditAction` trên method.

### Vì sao dùng RUNTIME retention?

Spring/AspectJ pointcut matching và binding cần thông tin annotation lúc chạy. Với bài học này dùng `@Retention(RetentionPolicy.RUNTIME)` để các annotations có sẵn trên runtime class/method.

- `SOURCE`: annotation không tồn tại trong compiled class file ở runtime.
- `CLASS`: thông tin có thể nằm trong bytecode, nhưng không có bảo đảm nhìn thấy theo Java reflection thông thường; **không chọn** nó cho bài binding runtime.
- `RUNTIME`: annotation sẵn có để runtime matcher/binding sử dụng.
- `@Target(METHOD)` chọn vị trí gắn trên method; `@Target(TYPE)` chọn class/interface/type. Đặt đúng loại annotation tương ứng pointcut designator.

Các annotation trên là Java annotation bình thường; **chúng không tự tạo Spring bean hoặc tự chạy advice**. `@Aspect` cần là Spring-managed bean, target cũng cần Spring-managed, và AOP infrastructure phải được bật.

### Có cần @Inherited?

`@Inherited` (Java) áp dụng cho việc kế thừa **annotation trên class** trong một số lookup trên subclass; nó **không** có tác dụng làm annotation trên method tự được thừa kế, cũng **không truyền annotation từ interface xuống implementing class**.

Không lạm dụng `@Inherited` như lời giải cho mọi mismatch `@annotation`/`@target`. Với interface, overridden methods, Kotlin types hoặc composed annotations, hãy kiểm tra actual Spring advisor matching trên proxy mode thực tế.

## 4. Bốn nhóm pointcut annotation mà Spring AOP hỗ trợ

Spring AOP hỗ trợ các pointcut designators sau với method execution join points của Spring beans:

| Designator | Annotation được xét trên đâu? | Dạng binding có thể dùng | Điểm cần nhớ |
| --- | --- | --- | --- |
| `@annotation` | **Method đang được execution** | `@annotation(action)` → `AuditAction action` | Phù hợp nhất để nhận metadata gắn ở từng operation. |
| `@target` | **Class của target object** đằng sau Spring proxy | `@target(group)` → `ServiceGroup group` | Gắn annotation ở implementation/target class thực tế. |
| `@within` | **Type mà trong đó method execution được khai báo** và có annotation | `@within(group)` → `ServiceGroup group` | Khác `@target` trong trường hợp inheritance/declaring type. |
| `@args` | **Runtime types của arguments được truyền** mang type annotations tương ứng | `@args(marker)` → type-annotation object phù hợp | Không phải annotation đặt trực tiếp lên parameter declaration. |

Tất cả các designators này đều **xét annotation như một điều kiện matching**, và có **binding form** để truyền annotation vào advice.

### 4.1. @annotation — annotation trên target method

```java
@Before(
    value = "execution(public * com.example.binding..*Service.*(..))"
          + " && @annotation(action)",
    argNames = "action"
)
public void inspectMethod(AuditAction action) {
    System.out.println("code=" + action.value());
}
```

Đoạn này là minh họa đặt trong `@Aspect` được Spring quản lý. Nó chỉ xét method execution trong package/type pattern và bind annotation kiểu `AuditAction`. Nếu method không có annotation phù hợp, advice không được gọi do biểu thức đó.

Đây là lựa chọn tự nhiên cho metadata của từng public operation: `create` có tag `"create"`, `refund` có tag `"refund"`, trong khi method `health` không mang marker thì không bị advice `@annotation` này chọn.

### 4.2. @target — annotation trên runtime target class

```java
@Before(
    value = "execution(public * com.example.binding..*Service.*(..))"
          + " && @target(group)",
    argNames = "group"
)
public void inspectTarget(ServiceGroup group) {
    System.out.println("serviceGroup=" + group.value());
}
```

Khi object đích của Spring AOP proxy thuộc class có `@ServiceGroup("billing")`, advice có thể nhận annotation của **class target ấy**. `@target` không có nghĩa “annotation trên return type” và cũng không đồng nghĩa Java meta-annotation `@Target`.

Một Spring proxy có runtime class sinh tự động; **đừng kiểm tra `proxy.getClass()` rồi nghĩ đó chắc là nơi đặt marker**. Khi debug, kiểm tra target type bằng `AopUtils.getTargetClass(proxy)`.

### 4.3. @within — annotation trên type khai báo method

```java
@Before(
    value = "execution(public * com.example.binding..*Service.*(..))"
          + " && @within(group)",
    argNames = "group"
)
public void inspectWithin(ServiceGroup group) {
    // Bind annotation của type liên quan đến @within.
}
```

So với `@target`, Spring docs mô tả `@within` về **method execution được khai báo trong annotated types**. Nếu method inherited từ superclass, annotation nằm ở subclass hay interface, ý nghĩa declaring type/runtime target có thể phân kỳ; luôn kiểm thử version và loại proxy của dự án trước khi dùng annotation làm policy quan trọng.

### 4.4. @args — annotation trên runtime type của đối số

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitivePayload {
}
```

```java
@SensitivePayload
public class TransferRequest {
}
```

```java
@Before(
    value = "execution(public * com.example.binding..*Service.process(..))"
          + " && @args(marker)",
    argNames = "marker"
)
public void beforeSensitivePayload(SensitivePayload marker) {
    // Thông tin annotation trên runtime class của argument.
}
```

Các đoạn `@args` là **minh họa**, không nằm trong project runnable mục 7; cần đúng imports, Spring beans, public types và chữ ký một argument thích hợp. `@args` khác hoàn toàn với Java `@Target(ElementType.PARAMETER)`: nó xét **type của giá trị argument tại runtime**, không xem annotation đặt trên parameter declaration.

`@args` có điều kiện phụ thuộc runtime arguments; nếu null, primitive, nhiều arguments, interface/subclass hoặc một annotation nằm trên declared parameter type nhưng không nằm trên actual argument class, kết quả matching cần thử trong context thật.

## 5. Cú pháp argNames và tham số lời advice

Spring dùng tên trong pointcut expressions và tên tham số advice để liên kết binding. Ví dụ:

```java
@Before(
    value = "@annotation(action)",
    argNames = "action"
)
public void beforeAction(AuditAction action) {
    // ...
}
```

Ba vị trí `action` là **cùng một binding name**. Nếu viết `@annotation(audit)` nhưng `argNames="action"`, Spring không thể liên kết tên `audit` như ví dụ dự kiến và có thể báo lỗi khi dựng advisor.

### 5.1. JoinPoint là tham số đặc biệt

Khi cần cả metadata của method và annotation:

```java
@Before(
    value = "@annotation(action)",
    argNames = "action"
)
public void beforeAction(JoinPoint jp, AuditAction action) {
    String methodName = jp.getSignature().getName();
    String actionName = action.value();
}
```

`JoinPoint` có import `org.aspectj.lang.JoinPoint` và **đặt đầu danh sách tham số**. Theo Spring, tên tham số `JoinPoint` đầu tiên **có thể bỏ khỏi `argNames`**, vì framework nhận diện kiểu đặc biệt đó. Bởi vậy `argNames="action"` là đúng cho signature `(JoinPoint jp, AuditAction action)`.

Một advice chỉ cần `JoinPoint` có thể khai báo:

```java
@Before("execution(* com.example.binding..*Service.*(..))")
public void inspect(JoinPoint jp) {
    System.out.println(jp.getSignature().getName());
}
```

Không cần `argNames` nếu không bind biến expression nào khác.

### 5.2. ProceedingJoinPoint dành cho @Around

```java
@Around(
    value = "execution(public * com.example.binding..*Service.*(..))"
          + " && @annotation(action)",
    argNames = "action"
)
public Object aroundAction(
        ProceedingJoinPoint pjp, AuditAction action) throws Throwable {
    // Có thể đọc action.value() trước hoặc sau.
    return pjp.proceed();
}
```

Đây là **mã minh họa** dùng trong Spring-managed `@Aspect` và cần import `org.aspectj.lang.ProceedingJoinPoint`, `Around`. `ProceedingJoinPoint` phải là tham số đầu của `@Around` và `proceed()` được gọi để target tiếp tục chạy. `argNames` không cần kể tên `pjp` đầu tiên.

Nếu chỉ đọc annotation/log, `@Before` ít quyền hơn và tránh nguy cơ quên gọi `proceed()`; dùng `@Around` khi thực sự cần quản lý thứ tự/thời gian hoặc biến đổi outcome theo contract.

### 5.3. Nhiều binding parameters

Có thể bind cả business argument, target và annotation:

```java
@Before(
    value = "execution(public * com.example.binding.BillingService.create(..))"
          + " && args(id)"
          + " && @annotation(action)",
    argNames = "id,action"
)
public void beforeCreate(
        JoinPoint jp, String id, AuditAction action) {
    System.out.println(
        action.value() + ":" + id + ":" + jp.getSignature().getName()
    );
}
```

- `args(id)` bind **runtime method argument** vào `String id`.
- `@annotation(action)` bind **method annotation** vào `AuditAction action`.
- `argNames="id,action"` tương ứng hai binding variables; không cần ghi `jp` vì nó là tham số `JoinPoint` đầu tiên.
- Advice chỉ chạy khi toàn bộ biểu thức match.
- Nếu giá trị đầu vào hoặc annotation metadata nhạy cảm, **không in thẳng** vào production logs.

`args(id)` khác với `@args(marker)`: cái trước bind **giá trị argument**, cái sau xét/bind **annotation trên type của argument**.

## 6. Spring infrastructure bắt buộc để annotation binding chạy

Một target method có annotation không tự được advice. Cần đủ bốn điều kiện:

1. **Aspect bean:** class `@Aspect` phải được đăng ký bằng `@Component` trong vùng component scan hoặc trả về từ `@Bean`.
2. **AOP support:** trong Spring Framework Java-config dùng `@EnableAspectJAutoProxy` và `aspectjweaver` tương thích; với Spring Boot sử dụng AOP auto-configuration khi starter phù hợp.
3. **Target bean:** business object là Spring-managed bean; nếu tạo `new BillingService()` bên ngoài context, Spring AOP không tự intercept.
4. **Proxy boundary:** caller lấy/inject target **qua proxy**, method phải nằm trên interface của JDK proxy hoặc override được trong CGLIB; `this.inner()` không quay lại proxy.

Đây là **Spring AOP proxy**, không phải AspectJ load-time weaving. Có dependency `aspectjweaver` để Spring parse `@AspectJ` expressions không đồng nghĩa đã thiết lập Java agent.

`@EnableAspectJAutoProxy(proxyTargetClass=true)` có thể ép class-based proxy. Với Java `final class`, CGLIB không subclass được; `final method` không override được, nên annotation trên method ấy không tự cứu interception. Nếu có interface và không ép class proxy, JDK dynamic proxy có thể phù hợp, nhưng method phải thuộc interface và annotation matching trên interface/implementation cần integration test.

### Hai cách tạo aspect bean

**Component scan:**

```java
@Aspect
@Component
public class AuditAspect {
    @Before("@annotation(action)")
    public void before(AuditAction action) {
        // ...
    }
}
```

**Bean factory method:**

```java
@Configuration
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

Các đoạn code này **minh họa**, không phải ví dụ project runnable; imports/classes sẽ đầy đủ ở mục 7. `@Aspect` **không tự mang nghĩa của `@Component`**: nếu dùng component scan mà thiếu registration, advice sẽ không được Spring dựng. Không đăng ký trùng cả scan và `@Bean` nếu không có lý do rõ ràng.

## 7. Demo chạy được: bind annotation trên method và class vào hai advice

Demo Spring Framework thuần với **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1** và Gradle. Một Spring target class mang `@ServiceGroup("billing")`, hai methods mang `@AuditAction`. Aspect dùng hai `@Before` advices riêng:

- `@annotation(action)` bind metadata của từng method và lưu event.
- `@target(group)` bind annotation của target class và tăng counter cho mọi method phù hợp trên target.

Ngoài ra, `selfCall` chứng minh một method nội bộ có `@AuditAction` **không được advice lần nữa** khi được gọi qua `this`, dù body Java vẫn chạy.

### 7.1. Cấu trúc thư mục

```text
spring-aop-annotation-binding/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/binding/
    ├── AuditAction.java
    ├── ServiceGroup.java
    ├── BillingService.java
    ├── BindingAspect.java
    ├── AppConfig.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aop-annotation-binding'
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
    mainClass = 'com.example.binding.Main'
}
```

Tài liệu Spring Framework 6.2.19 tương thích với Java 17+. Trong project Spring Boot, tránh ép các Spring module version thủ công trái BOM của phiên bản Boot đang dùng.

### 7.2. Định nghĩa annotation trên method

**`AuditAction.java`**

```java
package com.example.binding;

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

Annotation này không có logic runtime riêng; Spring AOP aspect sẽ bind và đọc `value()` khi pointcut match.

### 7.3. Định nghĩa annotation trên target type

**`ServiceGroup.java`**

```java
package com.example.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceGroup {
    String value();
}
```

`ServiceGroup` khác `AuditAction` ở `@Target`: một annotation dùng trên class, một annotation dùng trên method.

### 7.4. Spring service với hai methods được đánh dấu

**`BillingService.java`**

```java
package com.example.binding;

@ServiceGroup("billing")
public class BillingService {
    @AuditAction("create")
    public String create(String id) {
        return "created:" + id;
    }

    @AuditAction("reject")
    public String reject(String id) {
        throw new IllegalStateException("rejected:" + id);
    }

    public String health() {
        return "ok";
    }

    public String selfCall(String id) {
        return this.create(id);
    }
}
```

- `create`: có method annotation `@AuditAction("create")`; return bình thường.
- `reject`: có method annotation `@AuditAction("reject")`; ném exception. `@Before` vẫn được gọi trước khi target ném.
- `health`: không có method annotation `@AuditAction`, nhưng **class** có `@ServiceGroup("billing")`.
- `selfCall`: không có method annotation; nó gọi `this.create(...)` trên cùng target. Self-invocation bỏ qua proxy, nên `@Before` cho `create` không chạy thêm cho lời gọi nội bộ này.

Không method nào final/private và class không final để loại trừ nguyên nhân CGLIB không override được.

### 7.5. Aspect bind hai annotation kinds độc lập

**`BindingAspect.java`**

```java
package com.example.binding;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BindingAspect {
    private final List<String> actionEvents =
            new CopyOnWriteArrayList<>();
    private final AtomicInteger groupCalls = new AtomicInteger();

    @Before(
        value = "execution(public * com.example.binding.BillingService.*(..))"
              + " && @annotation(action)",
        argNames = "action"
    )
    public void beforeAction(JoinPoint jp, AuditAction action) {
        actionEvents.add(
            action.value() + ":" + jp.getSignature().getName()
        );
    }

    @Before(
        value = "execution(public * com.example.binding.BillingService.*(..))"
              + " && @target(group)",
        argNames = "group"
    )
    public void beforeGroup(ServiceGroup group) {
        if ("billing".equals(group.value())) {
            groupCalls.incrementAndGet();
        }
    }

    public List<String> events() {
        return List.copyOf(actionEvents);
    }

    public int groupCount() {
        return groupCalls.get();
    }
}
```

Lưu ý:

- `argNames="action"` ánh xạ tới `AuditAction action`; tham số `JoinPoint jp` đầu tiên được nhận diện đặc biệt nên không cần liệt kê.
- `argNames="group"` ánh xạ tới `ServiceGroup group`.
- `group.value()` đọc metadata của annotation bind trên class target, không phải tên class Spring proxy.
- `CopyOnWriteArrayList` và `AtomicInteger` giúp các phép ghi quan sát không bị race cơ bản; demo gọi tuần tự, không dùng counters làm distributed metrics.
- **Không in log trong advice**, để stdout cuối cùng không phụ thuộc thứ tự hai `@Before` advices trên cùng join point.

Nếu project thực tế có thể gặp thêm advisors khác, nên test điều kiện pointcut và side effects trên đúng Spring context.

### 7.6. Spring config với class-based proxy

**`AppConfig.java`**

```java
package com.example.binding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    BillingService billingService() {
        return new BillingService();
    }

    @Bean
    BindingAspect bindingAspect() {
        return new BindingAspect();
    }
}
```

`BillingService` và `BindingAspect` đều được Spring tạo như beans qua `@Bean`. `@EnableAspectJAutoProxy` bật hỗ trợ các `@AspectJ` annotations. Cấu hình `proxyTargetClass=true` chọn CGLIB class proxy để main có thể lấy Spring bean qua `BillingService.class`. Demo không cần `@ComponentScan`.

### 7.7. Main dùng Spring proxy và kiểm chứng output

**`Main.java`**

```java
package com.example.binding;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            BillingService service = context.getBean(BillingService.class);
            BindingAspect aspect = context.getBean(BindingAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("create=" + service.create("A"));

            try {
                service.reject("B");
            } catch (IllegalStateException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("health=" + service.health());
            System.out.println("selfCall=" + service.selfCall("C"));

            System.out.println("events=" + aspect.events());
            System.out.println("groupCalls=" + aspect.groupCount());
        }
    }
}
```

Lệnh chạy tại thư mục project, với Gradle và JDK 17+ đã cài:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper: `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Các dòng do chương trình in theo đúng thứ tự** (không tính log bên ngoài của JVM/Spring/Gradle):

```text
proxy=true
create=created:A
caught=rejected:B
health=ok
selfCall=created:C
events=[create:create, reject:reject]
groupCalls=4
```

### 7.8. Vì sao có hai action events nhưng bốn group calls?

| Invocation từ main | Annotation cấp method? | Annotation cấp target class? | `@annotation(action)` chạy? | `@target(group)` chạy? |
| --- | --- | --- | --- | --- |
| `service.create("A")` | `@AuditAction("create")` | `@ServiceGroup("billing")` | Có: `create:create` | Có: +1 |
| `service.reject("B")` | `@AuditAction("reject")` | `@ServiceGroup("billing")` | Có: `reject:reject` | Có: +1 |
| `service.health()` | Không | `@ServiceGroup("billing")` | Không | Có: +1 |
| `service.selfCall("C")` | Không ở method `selfCall` | `@ServiceGroup("billing")` | Không ở entry `selfCall` | Có: +1 |
| `this.create("C")` bên trong target `selfCall` | Có annotation ở method `create` | Class có annotation | **Không** vì self-invocation bypass proxy | **Không**, vì call không qua proxy |
| **Tổng trên lời gọi proxy** | Hai annotated entries | Bốn external entries | **2** | **4** |

Body `create(..)` thực thi hai lần (external `A` và internal `C`) nhưng method advice `@annotation(action)` chỉ chạy **một lần với create** ở external entry. Body `reject` ném lỗi nhưng `@Before` đã chạy trước đó; vì thế `reject:reject` vẫn xuất hiện.

Bảng cho thấy binding phụ thuộc **Spring interception**. Annotation còn tồn tại trên source method ở self-call; điều thiếu là **proxy boundary**, không phải metadata.

## 8. Named @Pointcut có annotation parameter và tái sử dụng

Nếu nhiều advice trong cùng aspect muốn dùng chung selection, có thể khai báo named pointcut có **tham số binding**:

```java
@Aspect
public class SharedAuditAspect {
    @Pointcut(
        value = "execution(public * com.example.binding.BillingService.*(..))"
              + " && @annotation(action)",
        argNames = "action"
    )
    private void annotatedAction(AuditAction action) {}

    @Before(
        value = "annotatedAction(action)",
        argNames = "action"
    )
    public void before(AuditAction action) {
        System.out.println("before=" + action.value());
    }

    @AfterReturning(
        value = "annotatedAction(action)",
        argNames = "action"
    )
    public void afterReturning(AuditAction action) {
        System.out.println("returned=" + action.value());
    }
}
```

Đây là **một biến thể minh họa**, cần imports `@Aspect`, `@Pointcut`, `@Before`, `@AfterReturning` và registration làm Spring bean; **không thêm** vào demo mục 7 nếu muốn giữ stdout đã nêu.

- `annotatedAction(AuditAction action)` là **signature của named pointcut**, không phải business method và body nên rỗng.
- `argNames="action"` trên `@Pointcut` xác định tên tham số được dùng bởi expression.
- `annotatedAction(action)` trong advice expression truyền **binding variable**, không phải gọi method Java để nhận annotation.
- `@Before` chạy trước method; `@AfterReturning` chỉ chạy nếu method kết thúc bình thường. `reject(..)` ném lỗi nên after-returning sẽ không xuất hiện cho case ấy.
- Nếu muốn dùng named pointcut từ **class aspect khác**, cần visibility Java hợp lệ và fully qualified reference; private chỉ dùng trong declaring type.
- Không dùng `@Pointcut` có signature trả `boolean` để hỏi annotation match; signature named pointcut phải trả `void`.

Nên giữ public catalog pointcuts với ý nghĩa ổn định khi nhiều modules cùng tham chiếu. Đối với một aspect duy nhất, private pointcut giúp giảm coupling.

## 9. Bind nhiều annotation trong cùng advice

Một method thuộc annotated target class và tự có method marker có thể bind cả hai:

```java
@Before(
    value = "execution(public * com.example.binding.BillingService.*(..))"
          + " && @target(group)"
          + " && @annotation(action)",
    argNames = "group,action"
)
public void beforeBoth(
        ServiceGroup group, AuditAction action) {
    System.out.println(group.value() + ":" + action.value());
}
```

Đây là **đoạn minh họa**, giả sử nó nằm trong Spring-managed `@Aspect`. Trong project mục 7, nó sẽ chọn `create(..)` và `reject(..)`, nhưng không chọn `health(..)` hay `selfCall(..)` vì hai method sau thiếu `@AuditAction`. Nó có thể in `billing:create` và `billing:reject` nếu được đăng ký; không thêm để tránh đổi stdout.

### Dùng thêm JoinPoint

```java
@Before(
    value = "execution(public * com.example.binding.BillingService.*(..))"
          + " && @target(group) && @annotation(action)",
    argNames = "group,action"
)
public void beforeBoth(
        JoinPoint jp, ServiceGroup group, AuditAction action) {
    System.out.println(
        jp.getSignature().getName()
        + ":" + group.value()
        + ":" + action.value()
    );
}
```

Spring nhận `JoinPoint` đầu tiên theo quy tắc đặc biệt. `argNames` chỉ khai báo các biến expression mà advice cần bind; với signature khác nhau, phải sửa `argNames` tương ứng.

Dùng nhiều annotation bindings hợp lý khi mỗi annotation mang metadata khác nhau. Đừng tạo pointcut quá rộng chỉ để “nhặt” mọi annotation trong hệ thống; giới hạn bằng `execution(...)`/package/type phù hợp.

## 10. Phân biệt reflection lookup và pointcut binding trong Spring

Một cách đọc annotation phổ biến bằng reflection:

```java
Method method = BillingService.class.getMethod("create", String.class);
AuditAction marker = method.getAnnotation(AuditAction.class);
System.out.println(marker.value());
```

Đây là đoạn Java **minh họa**; cần import `java.lang.reflect.Method` và context/try-catch cho checked reflection exceptions. Reflection trực tiếp hữu ích khi bạn **đã biết chính xác class và method**.

Trong Spring AOP, advice có thể nhận metadata thuận tiện hơn:

```java
@Before(
    value = "execution(* com.example.binding.BillingService.create(..))"
          + " && @annotation(action)",
    argNames = "action"
)
public void beforeCreate(AuditAction action) {
    System.out.println(action.value());
}
```

Ưu điểm là Spring/AspectJ matcher liên kết **đúng annotation tham gia join point matching** mà không cần advice tự viết code tra Java `Method`, bridge method hay tự quyết định tìm annotation ở target type nào.

**Không suy rằng binding thay thế mọi nhu cầu reflection:** nếu phải duyệt method overloads, annotations ở generic interface, composed/meta-annotations hoặc thông tin riêng của runtime proxy, có thể cần API `AopUtils.getMostSpecificMethod`, Spring `AnnotatedElementUtils`/`MergedAnnotations` theo bài toán. Những API đó có semantics tìm kiếm khác với pointcut expression; không gọi chúng là “mặc định tương đương `@annotation`”.

## 11. Các bẫy Spring proxy và annotation placement

### 11.1. Annotation chỉ đặt trên interface

```java
public interface PaymentPort {
    @AuditAction("pay")
    String pay(String id);
}

public class PaymentService implements PaymentPort {
    @Override
    public String pay(String id) {
        return "paid:" + id;
    }
}
```

Đây là đoạn minh họa. Annotation Java trên interface method **không tự được kế thừa lên implementation method** bằng Java `@Inherited`. Với Spring AOP, JDK interface proxy và CGLIB class proxy có đường resolve method khác nhau; Spring có các cơ chế annotation search ở một số tính năng nhưng không nên suy mọi `@annotation` expression đều tự tìm annotation trên interface giống mọi infrastructure.

**Khuyến nghị:** khi một audit policy quan trọng gắn vào method implementation, đặt annotation ở nơi đã được Spring aspect matcher kiểm chứng, hoặc thiết kế contract/pointcut theo interface một cách explicit. Viết integration tests riêng cho JDK proxy và class proxy nếu dự án hỗ trợ cả hai.

### 11.2. Annotation ở superclass/subclass

`@target` xét target class, còn `@within` mô tả declared type; method được kế thừa hay override có thể làm việc matching/binding khác mong đợi. `@Inherited` chỉ có ý nghĩa đối với **annotation cấp class** theo quy tắc Java; nó không tự biến inherited method thành method được annotate.

Nếu cần một annotation policy bao phủ mọi subclass, kiểm thử một class có annotation trực tiếp, subclass có/không `@Inherited`, override method và inherited method riêng biệt.

### 11.3. Annotation trên class nhưng dùng @annotation

`@ServiceGroup` gắn class và có `@Target(TYPE)`. Dùng `@annotation(group)` để chọn annotation cấp class **không đúng điểm cắt**; dùng `@target(group)` hoặc `@within(group)` phù hợp. Ngược lại, `@AuditAction` có `@Target(METHOD)` không tự làm `@target(action)` match.

### 11.4. Method annotation nhưng method không intercept được

Một method `final` trong class-based CGLIB proxy không override được; `private` cũng không thể advice bằng cơ chế proxy. Một `@AuditAction` đầy đủ metadata **không sửa** được hạn chế kỹ thuật này. Lời gọi `this.create()` bên trong target vẫn bypass dù method public/non-final.

### 11.5. Bean chưa được Spring quản lý

`@AuditAction` không đăng ký Spring bean; `@Aspect` cũng không tự biến thành `@Component`. Nếu service được tạo bằng `new` hoặc aspect không nằm trong bean registry thì không có interceptor tương ứng.

### 11.6. Không kết luận từ AopUtils.isAopProxy() đơn độc

`AopUtils.isAopProxy(bean)` cho biết bean có Spring proxy. Nó **không chứng minh** advisor cụ thể của `@annotation(action)` match mọi method invocation. Có thể proxy do advisor khác, method không có annotation hoặc caller thực sự dùng raw target.

Dùng **event recorder** hoặc counter như demo để xác minh annotation object đã được truyền vào đúng advice trên đúng call path.

## 12. Khi metadata annotation điều khiển audit, metrics hoặc security

Một annotation có thể mang metadata:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ActionPolicy {
    String name();
    boolean sensitive() default false;
}
```

Một advice có thể bind `ActionPolicy policy` rồi quyết định **cách quan sát** method:

```java
@Before(
    value = "@annotation(policy)",
    argNames = "policy"
)
public void recordPolicy(ActionPolicy policy) {
    if (!policy.sensitive()) {
        System.out.println("action=" + policy.name());
    }
}
```

Đây là **mã minh họa**, cần imports và registration. Với annotation nói “sensitive”, chính policy **không log dữ liệu nhạy cảm** là hợp lý hơn việc log trọn `JoinPoint.getArgs()`.

Nhưng không xem một custom logging aspect là **ranh giới bảo mật duy nhất**:

- Spring method security cần cấu hình activation riêng và các interceptors chuyên dụng.
- Nếu custom security advice chỉ dựa trên `@annotation` nhưng caller dùng self-invocation, nó có thể bị bỏ qua.
- Pointcut quá rộng cũng có thể advice vào beans ngoài mục tiêu.
- `@Around` có quyền skip `proceed()`/return fallback; với authorization cần contract rõ và security integration tests.
- Annotation metadata không bảo đảm caller đã được xác thực/ủy quyền; đó là logic ở tầng security infrastructure.

Nếu dùng annotations cho tracing/metrics, phải tính cardinality của tags. Ví dụ biến `orderId` thành metric label sẽ tạo cardinality cao; nên tag theo `@AuditAction("create")` và service group ổn định, không theo từng customer/request ID.

## 13. Spring Boot áp dụng thế nào?

Trong Spring Boot có AOP auto-configuration đúng version, `@AspectJ` annotations vẫn hoạt động cùng quy tắc:

- Tạo annotation với `RUNTIME` retention.
- Đánh dấu method hoặc class mục tiêu.
- Tạo `@Aspect` **được Spring quản lý** (thường `@Aspect` + `@Component`) và đặt trong component-scan scope.
- Viết pointcut `execution(...) && @annotation(marker)` (hoặc `@target`/`@within`) và tham số advice đúng type/name.
- Dùng `argNames` khi muốn binding tường minh; `JoinPoint`/`ProceedingJoinPoint` đầu tiên có quy tắc riêng.
- Kiểm tra proxy kind/call path và viết integration tests, đặc biệt khi target class/method final, interface annotation hoặc Kotlin default-final.

Ví dụ dependencies Gradle (lựa chọn theo Boot major với dependency management phù hợp):

```gradle
// Spring Boot 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

```gradle
// Spring Boot 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

Hai dòng thuộc **hai lựa chọn khác nhau**, không thêm cả hai vào project bất kể Boot version. `spring.aop.proxy-target-class` ảnh hưởng JDK/CGLIB proxy; việc dùng `@annotation` không loại bỏ giới hạn Spring proxy.

## 14. Kiểm thử Spring annotation binding một cách có ý nghĩa

### 14.1. Test trên Spring ApplicationContext thật

Tối thiểu một integration test phải tạo Spring context chứa:

1. Target service bean;
2. `@Aspect` bean;
3. `@EnableAspectJAutoProxy` hoặc Boot AOP auto-config;
4. Method call qua bean reference do Spring trả về.

Test Java method như `new BindingAspect().beforeAction(..., ...)` chỉ cho biết method advice Java chạy khi được gọi thủ công, **không chứng minh Spring bind annotation theo pointcut**.

### 14.2. Kiểm chứng các trường hợp từ demo

- `create("A")`: có một `AuditAction("create")` event và một `ServiceGroup("billing")` group count.
- `reject("B")`: `@Before` vẫn nhận marker `"reject"` dù method sau đó ném exception.
- `health()`: không có method annotation event, nhưng type annotation event vẫn được tính.
- `selfCall("C")`: group advice cho **entry method `selfCall`** được tính; nested `this.create()` không tạo thêm method event.
- Lời gọi trực tiếp tới `new BillingService().create("X")` (ngoài container) không làm aspect counters thay đổi.
- Khi bỏ annotation `@AuditAction` khỏi một method, advice `@annotation` không còn match method ấy; không nhất thiết bean mất proxy vì còn `@target` advisor.

Một assertion độc lập dùng các classes demo:

```java
try (var context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    BillingService service = context.getBean(BillingService.class);
    BindingAspect aspect = context.getBean(BindingAspect.class);

    service.create("A");
    try {
        service.reject("B");
    } catch (IllegalStateException expected) {
        // Có lỗi nhưng @Before đã chạy.
    }
    service.health();
    service.selfCall("C");

    if (!aspect.events().equals(
            java.util.List.of("create:create", "reject:reject"))) {
        throw new AssertionError("Incorrect method annotation binding");
    }
    if (aspect.groupCount() != 4) {
        throw new AssertionError("Incorrect target type annotation binding");
    }
}
```

Đây là **biến thể test** khởi tạo context mới, cần import `AnnotationConfigApplicationContext` và đặt trong Java class test/main phù hợp; không thêm vào `Main.java` nếu muốn giữ stdout mục 7.7.

### 14.3. Test binding khi đổi tên parameter

Tạo một test fixture riêng cố ý đặt `@annotation(marker)` nhưng `argNames="action"`. Mục tiêu là khẳng định config không hợp lệ được phát hiện khi Spring dựng advisor. Không đưa một malformed advice vào production context hay demo chính vì nó có thể làm toàn context fail startup.

### 14.4. Kiểm thử proxy types

Nếu project dùng cả JDK dynamic proxies và CGLIB, viết test riêng với interface annotation và implementation annotation; kiểm tra `AopUtils.isJdkDynamicProxy(bean)` hoặc `isCglibProxy(bean)`, sau đó assert **advice invocation**, không chỉ proxy tồn tại. Đảm bảo caller inject bằng interface nếu bean là JDK proxy.

## 15. Những lỗi cấu hình thường gặp

| Triệu chứng | Nguyên nhân có khả năng | Kiểm tra |
| --- | --- | --- |
| Khởi tạo Spring context lỗi khi bind annotation | `argNames` không khớp tên biến trong expression hoặc tham số advice | Đồng nhất `@annotation(marker)`, `argNames="marker"`, `AuditAction marker`. |
| Advice chạy nhưng không đọc được metadata mong muốn | Dùng dạng FQCN presence-only hoặc dùng sai annotation type | Dùng binding form `@annotation(marker)` và đúng parameter type. |
| Annotation ở class, `@annotation(marker)` không match | `@annotation` tìm method annotation, không phải type | Dùng `@target` hoặc `@within` thích hợp. |
| Annotation ở method, `@target(marker)` không match | `@target` tìm annotation trên target type | Dùng `@annotation`. |
| Annotation chỉ có `SOURCE` retention | Runtime không có annotation object để match/bind | Chọn `RUNTIME` cho marker của bài này. |
| Annotation chỉ trên interface nhưng class implementation khác | Java annotations không tự kế thừa từ interface theo cách mặc định | Kiểm thử actual Spring method resolution/JDK-CGLIB và đặt policy đúng nơi. |
| Aspect không được gọi | Aspect chưa là bean, AOP chưa bật, target ngoài container | Kiểm tra `@Bean`/`@Component`, `@EnableAspectJAutoProxy` và component scan. |
| Có proxy nhưng thiếu event ở nested call | `this.inner()` bypass proxy | Tách sang injected collaborator hoặc advice outer entry. |
| @Before method args khớp sai | `args(arg)` bind argument runtime nhưng type/name không tương thích | Xác nhận number/type of args và `argNames`. |
| Annotation không bind vào `JoinPoint` | Nhầm `JoinPoint` với binding variable | `JoinPoint` đầu tiên, marker được khai báo parameter riêng. |
| Code `@Around` có annotation nhưng target không chạy | Quên `pjp.proceed()` hoặc short-circuit | Gọi `proceed()` theo contract, test side effects. |
| Method `final` dưới CGLIB không được advice | Class proxy không override được method | Chọn proxy strategy/entry method phù hợp. |
| Method có annotation nhưng chỉ test `new Service()` | Không có Spring proxy | Test bằng Spring context và injected proxy. |
| Advice log sai mức nhạy cảm | Metadata dùng để log raw args/result không được kiểm soát | Whitelist/redact, giới hạn cardinality và áp dụng privacy policy. |

## 16. Quy trình áp dụng vào một dự án Spring thực

### Bước 1 — Định nghĩa metadata thực sự cần

Không thêm annotation chỉ để “cho đẹp”. Chọn thông tin ổn định ở compile time: operation name, audit category hoặc service family. Nếu metadata biến đổi theo từng request, nó nên đến từ argument/context chứ không phải một annotation constant.

### Bước 2 — Chọn đúng vị trí annotation

Method operation → `@Target(METHOD)` và `@annotation`; service target type → `@Target(TYPE)` và `@target`; type khai báo execution → `@within`; annotation trên runtime argument type → `@args`.

### Bước 3 — Bảo đảm RUNTIME retention

Khai báo `@Retention(RUNTIME)`, có tên method/class khớp nơi gắn marker. Kiểm tra annotation có thật trên concrete target hoặc method cần intercept; không mặc định kế thừa interface.

### Bước 4 — Bật Spring AOP đúng context

Aspect phải là Spring bean, target là Spring bean, bật `@EnableAspectJAutoProxy`/Boot AOP auto-configuration và có dependencies tương thích. Với multi-context applications, AOP support có phạm vi context; không tự giả định advisor ở root tự áp lên mọi child context.

### Bước 5 — Viết pointcut có scope

Kết hợp `execution(public * com.example.orders..*Service.*(..))` với binding designator. Tránh chỉ viết `@annotation(marker)` trên toàn application nếu nó vô tình chọn quá rộng hoặc tạo proxy không cần thiết.

### Bước 6 — Đặt argNames và chữ ký advice chính xác

Tên biến pointcut phải khớp tham số advice; `JoinPoint` đầu tiên được binding riêng, còn các biến khác cần có Java types đúng. Nếu có nhiều binding variables, khai báo `argNames` theo đúng thứ tự tham số binding.

### Bước 7 — Test qua Spring proxy

Gọi annotated method từ context, unannotated method, method ném lỗi, call path self-invocation, rồi đo events/metadata; bảo đảm annotation object nhận được đúng giá trị.

### Bước 8 — Rà soát side effects và vận hành

Không lưu request-sensitive mutable state trong singleton aspect; không log secrets; không dùng annotation marker để thay thế authorization; nếu advice có thể ném exception hoặc thay outcome (`@Around`), kiểm thử contract thật.

## 17. Checklist cuối bài

- [ ] Chọn chính xác `@annotation` (method), `@target` (target class), `@within` (declaring type) hay `@args` (runtime argument class)?
- [ ] Annotation có `@Target` phù hợp và `@Retention(RUNTIME)`?
- [ ] Biết khi nào dùng `@annotation(FQCN)` để match và `@annotation(marker)` để bind annotation instance?
- [ ] Tham số advice có kiểu annotation đúng và tên `marker` khớp `argNames`/pointcut?
- [ ] Nếu dùng `JoinPoint` hoặc `ProceedingJoinPoint`, nó là tham số đầu và không cần khai báo tên trong `argNames`?
- [ ] Aspect có `@Aspect` **và** là Spring-managed bean?
- [ ] Target là Spring-managed bean, hạ tầng AOP bật đúng context và AspectJ dependency có mặt?
- [ ] Pointcut expression được giới hạn bằng package/type/method contract phù hợp?
- [ ] Caller đi qua Spring proxy, không `new`/self-invocation, method proxy intercept được?
- [ ] Nếu annotation đặt ở interface/superclass, đã test với đúng JDK/CGLIB proxy và inheritance semantics?
- [ ] Annotation data được sử dụng an toàn, không log secret hoặc high-cardinality request IDs?
- [ ] Integration test assert metadata binding/events, không chỉ `AopUtils.isAopProxy(bean)`?
- [ ] Nếu bind thêm `args(...)`, tên, vị trí và Java types của arguments phù hợp?
- [ ] Nếu dùng `@Around`, có `proceed()` đúng semantics và giữ contract return/exception?

**Ghi nhớ:** Với Spring AOP, một annotation trên method/class có thể trở thành **giá trị tham số của advice** thông qua binding form như `@annotation(action)` hoặc `@target(group)`. Spring nối **tên biến trong expression**, `argNames` và **kiểu tham số Java**; advice nhận được annotation object để đọc metadata. Nhưng metadata chỉ được truyền khi có **Spring-managed aspect + Spring target bean + proxy invocation + pointcut match**. Đó là ranh giới quan trọng giữa annotation nằm trong source và advice thực sự chạy.

## Tài liệu Spring tham khảo

- [Spring Framework 6.2 — Declaring Advice: Passing Parameters to Advice và argNames](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Declaring a Pointcut: @annotation, @target, @within, @args](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Maven Central — AspectJ Weaver](https://central.sonatype.com/artifact/org.aspectj/aspectjweaver)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-006">Ràng buộc kết quả returning và ngoại lệ throwing</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc kết quả `returning` và ngoại lệ `throwing` trong Spring AOP

## 1. Mục tiêu và phạm vi

Trong Spring Framework, khi khai báo advice bằng `@Aspect`, hai thuộc tính quan trọng là **`returning`** của `@AfterReturning` và **`throwing`** của `@AfterThrowing`. Chúng không chỉ cho biết Spring nên chuyển **giá trị trả về** hoặc **ngoại lệ thoát khỏi method** vào tham số Java nào, mà kiểu tham số advice còn ảnh hưởng **lời gọi nào đủ điều kiện kích hoạt advice**.

Bài học tập trung vào **Spring Aspect/Spring AOP proxy-based** và trả lời các câu hỏi:

1. Làm thế nào để truy cập giá trị trả về của một method Spring bean bằng `returning = "result"`?
2. Làm thế nào để nhận exception qua `throwing = "error"`?
3. Tại sao `String result`, `Object result`, `IllegalArgumentException error` và `Throwable error` có phạm vi áp dụng khác nhau?
4. Cách Spring xác định tên tham số advice, khi nào dùng `argNames` hoặc `javac -parameters`?
5. Khi nào advice không chạy: method `void`, kiểu không phù hợp, exception bị bắt trong target, invocation qua `this`, object ngoài Spring?
6. Làm sao viết code có thể chạy bằng Maven/Java 17+, dự đoán stdout và kiểm thử cả trường hợp match lẫn không match?

Đây **không phải** bài lý thuyết Aspect tổng quát. Nội dung hướng vào điều kiện thực tế để **Spring tạo advisor, chọn method và truyền thông tin ngữ cảnh vào Spring-managed `@Aspect` bean**.

## 2. Hai kiểu binding cơ bản

### 2.1. `returning`: kết quả từ method kết thúc bình thường

Ví dụ:

~~~java
@Aspect
public class ResultAspect {

    @AfterReturning(
        pointcut = "execution(* example.PaymentService.find(..))",
        returning = "result",
        argNames = "result"
    )
    public void afterFind(String result) {
        System.out.println("payment result: " + result);
    }
}
~~~

Đây là **snippet**, giả định có `example.PaymentService.find(..)`, aspect được đăng ký thành Spring bean và Spring AOP đã bật.

- `pointcut` chọn **method execution** phù hợp.
- `returning = "result"` yêu cầu Spring **bind kết quả trả về** vào tham số advice có tên `result`.
- Tham số `String result` vừa cho phép code đọc kết quả dưới dạng String, vừa hạn chế advice vào những kết quả kiểu String phù hợp.
- `argNames = "result"` chỉ rõ tên binding để không phải phụ thuộc hoàn toàn vào việc compiler lưu tên tham số.
- Advice này chỉ chạy khi method đã **trả về bình thường** qua lời gọi được Spring AOP can thiệp.

**Không thể** dùng `@AfterReturning` để trả một reference mới thay thế kết quả đã được method trả về. Advice có thể quan sát giá trị nhận được; nếu object mutable và bạn gọi phương thức làm thay đổi trạng thái của chính object ấy thì nội dung object có thể thay đổi — đó là tác dụng phụ cần đặc biệt tránh nếu chỉ muốn ghi log/audit.

### 2.2. `throwing`: exception thoát khỏi method

Ví dụ:

~~~java
@Aspect
public class FailureAspect {

    @AfterThrowing(
        pointcut = "execution(* example.PaymentService.pay(..))",
        throwing = "error",
        argNames = "error"
    )
    public void afterFailedPayment(IllegalArgumentException error) {
        System.out.println("invalid payment: " + error.getMessage());
    }
}
~~~

Đây cũng là **snippet**, không phải project hoàn chỉnh.

- `throwing = "error"` bind throwable được ném ra từ method execution vào tham số có tên `error`.
- Kiểu `IllegalArgumentException` làm advice chỉ áp dụng cho throwable **tương thích với kiểu này**; một `IllegalStateException` không được advice này nhận.
- Spring chỉ chạy `@AfterThrowing` khi join point đi theo **exceptional exit**, không phải bất kỳ nơi nào code đã từng tạo rồi bắt exception.
- Advice không tự biến exception thành normal return. Nếu chỉ log và return `void`, ngoại lệ gốc tiếp tục lan truyền qua proxy theo hành vi thông thường.

Một advice `@AfterThrowing` cũng **không phải exception handler toàn cục** của tất cả exceptions từ những advice khác. Theo tài liệu Spring, nó dành cho exception đến từ method join point được advice, **không mặc nhiên nhận exception do một `@After` hay `@AfterReturning` khác phát sinh**.

## 3. So sánh chính xác ngữ nghĩa

| Khía cạnh | `@AfterReturning(..., returning = "x")` | `@AfterThrowing(..., throwing = "x")` |
| --- | --- | --- |
| Thời điểm | Method execution được advice **trả về bình thường** | Method execution được advice **ném Throwable thoát ra** |
| Giá trị được bind | Return value | Throwable được ném |
| Cần tên tham số tương ứng | `x` phải trùng binding name của tham số advice | `x` phải trùng binding name của tham số advice |
| Kiểu tham số tác động matching | Ví dụ `String` chỉ nhận kết quả tương thích String | Ví dụ `IllegalArgumentException` chỉ nhận exception tương thích |
| Cách diễn tả phạm vi rộng | `Object x` khi cần kết quả dưới dạng Object, với điều kiện nhận giá trị phù hợp | `Throwable x` khi cần nhiều loại throwable |
| Nếu target bắt lỗi rồi return | Chạy nếu giá trị trả về match | **Không chạy** vì không có exception thoát join point |
| Nếu target trả `void` bình thường | Có thể dùng `@AfterReturning` **không có binding** | Không chạy vì không có throwable |
| Nếu target ném exception | Không chạy | Chạy khi kiểu exception khớp |
| Tự đổi return của caller | Không thay reference trả về bằng return của advice | Không chuyển exceptional exit thành success chỉ bằng return `void` |

**Điểm cốt lõi:** `returning` và `throwing` vừa là **tên để bind ngữ cảnh**, vừa kích hoạt **type-sensitive matching** dựa trên kiểu tham số advice. Chỉ khác mỗi kiểu Java của tham số có thể thay đổi tập invocation mà advice áp dụng.

## 4. Tên binding phải khớp tên tham số advice

Cấu hình đúng:

~~~java
@AfterReturning(
    pointcut = "execution(* example.ReportService.read(..))",
    returning = "value",
    argNames = "value"
)
public void afterRead(String value) {
    // Tên "value" nhất quán ở thuộc tính và tham số advice.
}
~~~

Cấu hình có lỗi:

~~~java
// Ví dụ SAI, không sao chép làm code chạy.
@AfterReturning(
    pointcut = "execution(* example.ReportService.read(..))",
    returning = "result",
    argNames = "value"
)
public void afterRead(String value) {
    // "result" không tương ứng binding name "value".
}
~~~

Khi cấu hình sai, Spring có thể ném exception trong quá trình phân tích/tạo advisor hoặc khi resolve parameters; **đừng dựa vào một tên exception duy nhất** cho mọi phiên bản Spring.

Quy trình kiểm tra:

1. Xác định giá trị thuộc tính `returning = "..."` hoặc `throwing = "..."`.
2. Kiểm tra method advice có tham số tương ứng không.
3. Nếu sử dụng `argNames`, tên trong đó phải tương thích với các biến được bind.
4. Kiểm tra đúng **kiểu** tham số: String, Object, loại exception cụ thể hoặc Throwable.
5. Kiểm tra expression không bỏ sót target method hoặc bị loại do pointcut khác.
6. Kiểm tra Spring AOP đã nhận diện advice method và đã tạo proxy phù hợp.

Nếu method advice có nhiều tham số, bạn có thể kết hợp `JoinPoint`, `args(...)`, `target(...)`, `@annotation(...)` và giá trị `returning`/`throwing`, nhưng cần đặt tên không xung đột và khai báo chính xác.

## 5. Spring tìm tên tham số lời advice bằng cách nào?

`returning = "value"` và `throwing = "error"` là **tên binding** phải ánh xạ được tới tham số advice có kiểu phù hợp. Ví dụ `@AfterReturning(pointcut = "execution(* example.PaymentService.find(..))", returning = "value", argNames = "value")` có thể đi với `afterFind(JoinPoint jp, String value)`; tên `jp` được lược bỏ khỏi `argNames` vì `JoinPoint` đứng đầu.

Trong Java, chọn `argNames` tường minh khi có nhiều binding, hoặc biên dịch với `-parameters` để giữ tên tham số cho Java reflection. Spring cũng có chiến lược suy luận trong những trường hợp rõ ràng; đừng xem debug symbols cũ là cách thay thế chắc chắn. `ProceedingJoinPoint` chỉ được dùng để `proceed()` trong `@Around`, không phải trong `@AfterReturning` hay `@AfterThrowing`.

Phần chuyên sâu **argNames, tên tham số và tùy chọn biên dịch `-parameters`** trình bày Maven/Gradle, named pointcut, thứ tự phát hiện tên và cách gỡ ambiguity. Riêng bài này tập trung vào **khi nào result/error được bind theo kiểu dữ liệu**, độc lập với việc tìm tên.

## 6. Kiểu tham số hạn chế những lời gọi nào được chọn?

### 6.1. Trường hợp `String` và `Object` ở `returning`

~~~java
// Chỉ quan sát return value phù hợp String.
@AfterReturning(
    pointcut = "execution(* example.CatalogService.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterString(String result) {
    // ...
}
~~~

Một target method trả `String` phù hợp sẽ được advice. Một method trả `Integer` không thuộc phạm vi binding `String` chỉ vì chúng đều là Object trong Java.

Nếu đổi advice thành:

~~~java
@AfterReturning(
    pointcut = "execution(* example.CatalogService.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterAnyResult(Object result) {
    // ...
}
~~~

thì kiểu `Object` nhận phạm vi kết quả rộng hơn. Từ góc nhìn Java reflection/Spring AOP, kết quả primitive được boxing ở ranh giới cần thiết khi được chuyển qua advice. Tuy nhiên, nếu mục tiêu của bạn là nhận callback cho **mọi method normal completion kể cả `void`**, hãy dùng dạng `@AfterReturning` **không khai báo `returning`**, thay vì cố ép một giá trị trả về cho method không có return value.

**Tránh ví dụ mơ hồ về `null` hoặc method khai báo Object nhưng runtime String:** ngữ nghĩa static/dynamic matching, kiểu được khai báo và giá trị runtime có thể phụ thuộc cơ chế matching. Với bài kiểm tra quan trọng, hãy thử bằng **Spring context và phương thức thật** thay vì suy luận đơn thuần từ kiểu source.

### 6.2. Trường hợp `IllegalArgumentException` và `Throwable` ở `throwing`

~~~java
@AfterThrowing(
    pointcut = "execution(* example.CatalogService.*(..))",
    throwing = "problem",
    argNames = "problem"
)
public void whenInvalidInput(IllegalArgumentException problem) {
    // ...
}
~~~

Phạm vi bị giới hạn vào exception tương thích với `IllegalArgumentException`, gồm các subclass của nó. Ngược lại:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.CatalogService.*(..))",
    throwing = "problem",
    argNames = "problem"
)
public void whenAnyThrowable(Throwable problem) {
    // ...
}
~~~

Dạng `Throwable` bao quát các throwable đi ra từ matched method. Nếu cần hẹp hơn, có thể dùng `Exception`, `RuntimeException` hoặc custom exception type. Đừng dùng `Throwable` chỉ để ghi log **mọi exception từ mọi bean**; expression nên giới hạn package/operation phù hợp.

**Đặc biệt:** `throwing` **không phụ thuộc** method có khai báo `throws IllegalArgumentException` trong signature hay không. Runtime exception unchecked vẫn có thể thoát method và được Spring AOP quan sát nếu invocation đi qua proxy, phù hợp pointcut và throwable type.

### 6.3. Không thể suy nhánh kết quả chỉ từ tên callback

Một callback `@AfterReturning` không được chạy khi method ném lỗi; một callback `@AfterThrowing` không chạy nếu target đã catch exception rồi return. Đừng viết business logic giả định rằng hai callback luôn xuất hiện thành cặp cho mỗi lời gọi. Với một invocation cụ thể, normal và exceptional exit là hai nhánh khác nhau.

## 7. Kết hợp `JoinPoint` với dữ liệu bound

Ví dụ advice quan sát method signature và return value:

~~~java
@AfterReturning(
    pointcut = "execution(* example.InvoiceService.load(..))",
    returning = "invoice",
    argNames = "invoice"
)
public void recordLoadedInvoice(JoinPoint jp, Invoice invoice) {
    String operation = jp.getSignature().getName();
    // Log tên operation và một identifier được phép.
}
~~~

Đây là **snippet**, `Invoice` và `InvoiceService` là type nghiệp vụ minh họa, không phải project chạy độc lập. Nó thể hiện:

- `JoinPoint.getSignature()` cung cấp mô tả method đang được advice.
- Giá trị `invoice` là **return value được bind**, không phải phần tử `jp.getArgs()`.
- `jp.getArgs()` cho biết **input arguments** của invocation, không phải kết quả trả về.
- `jp.getThis()` là Spring AOP proxy, `jp.getTarget()` là target object; chúng có thể không phải cùng object (đặc biệt với JDK proxy).
- Không log nguyên object `invoice` bằng `toString()` khi có thông tin nhạy cảm.

Cách sử dụng tương tự với lỗi:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.InvoiceService.load(..))",
    throwing = "failure",
    argNames = "failure"
)
public void recordFailure(JoinPoint jp, RuntimeException failure) {
    String operation = jp.getSignature().getName();
    String type = failure.getClass().getSimpleName();
    // Log operation và loại exception theo policy.
}
~~~

Advice có thể kiểm tra nội dung error để phân loại, nhưng **không nên đưa toàn bộ exception message/stack trace vào metric labels** hoặc mặc định coi chúng an toàn để public.

## 8. Khi nào callback không chạy dù biểu thức đúng?

Trong Spring AOP, sự xuất hiện của `@AfterReturning` hoặc `@AfterThrowing` trong source không đủ bảo đảm callback chạy. Cần:

1. **Aspect bean được đăng ký trong Spring container.** Class `@Aspect` đơn lẻ không đồng nghĩa `@Component`; cần `@Component` trong component scan hoặc `@Bean`.
2. **Hạ tầng Spring AOP hoạt động.** Có thể là `@EnableAspectJAutoProxy` trong Spring Framework thuần hoặc AOP auto-configuration của Spring Boot khi được bật.
3. **Pointcut match đúng method execution** trên bean cần advice.
4. **Lời gọi thực sự đi qua Spring AOP proxy.**
5. **Outcome khớp annotation:** normal return với `@AfterReturning`, exception exit với `@AfterThrowing`.
6. **Kiểu giá trị/exception phù hợp** với tham số Java được bind.

Ví dụ:

~~~java
public String outer() {
    return this.find();
}
~~~

Nếu `outer()` được gọi qua proxy, một advice nhắm vào `outer` vẫn có thể chạy. Nhưng `this.find()` bên trong target không tự quay lại proxy, nên advice riêng cho `find()` **bị bỏ qua tại lời gọi nội bộ**. Điều này áp dụng cho cả JDK proxy và CGLIB proxy Spring AOP.

**Một kết quả đặc biệt cần nhớ:** Nếu `outer()` trả về String nhờ gọi `this.find()`, một `@AfterReturning` với pointcut match **cả outer** vẫn có thể nhận String của outer, **dù invocation nội bộ find không được advice riêng**. Ví dụ runnable dưới đây có chính tình huống ấy.

## 9. Ví dụ Maven + Java 17+ hoàn chỉnh

### 9.1. Mục tiêu kiểm chứng

Dự án sử dụng **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Java **17+** và Maven. Các tình huống:

- `find()` trả `String`: kích hoạt `@AfterReturning` có tham số `String`.
- `count()` trả `int`: **không** kích hoạt advice trả về String.
- `ping()` là `void`, hoàn thành bình thường: kích hoạt `@AfterReturning` **không binding** cho đúng method ấy.
- `failInput()` ném `IllegalArgumentException`: kích hoạt `@AfterThrowing` bound `IllegalArgumentException`.
- `failState()` ném `IllegalStateException`: **không** kích hoạt advice giới hạn `IllegalArgumentException`, nhưng kích hoạt advice bound `Throwable` có pointcut riêng.
- `recover()` tự bắt `IllegalArgumentException` rồi return String: kích hoạt after-returning String, **không** kích hoạt after-throwing.
- `selfCall()` gọi `this.find()`: callback chỉ nhận **kết quả của selfCall** mà proxy đã thấy, không nhận thêm invocation của find bên trong.

Các advice không chỉnh return value, không nuốt exception và không có hai callback **cùng loại trên cùng một join point** để stdout tránh thứ tự không xác định.

### 9.2. Cấu trúc

~~~text
spring-binding-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── DemoService.java
    ├── BindingAspect.java
    └── Main.java
~~~

### 9.3. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-binding-demo</artifactId>
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
                <configuration>
                    <release>17</release>
                    <parameters>true</parameters>
                </configuration>
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

BOM giúp các dependency Spring dùng version thống nhất. Trong dự án Spring Boot thật, ưu tiên starter/BOM của Boot thay vì copy version của demo vào nơi đang được Boot quản lý.

### 9.4. `DemoService.java`

~~~java
package example;

public class DemoService {

    public String find() {
        System.out.println("target: find");
        return "FOUND";
    }

    public int count() {
        System.out.println("target: count");
        return 3;
    }

    public void ping() {
        System.out.println("target: ping");
    }

    public void failInput() {
        System.out.println("target: failInput");
        throw new IllegalArgumentException("invalid");
    }

    public void failState() {
        System.out.println("target: failState");
        throw new IllegalStateException("state");
    }

    public String recover() {
        System.out.println("target: recover begin");
        try {
            throw new IllegalArgumentException("invalid");
        } catch (IllegalArgumentException ignored) {
            System.out.println("target: handled invalid");
            return "RECOVERED";
        }
    }

    public String selfCall() {
        System.out.println("target: selfCall");
        return this.find();
    }
}
~~~

Điểm học chính: `recover()` có một exception **nội bộ đã bị bắt**, nên nó trả về bình thường. `selfCall()` sử dụng `this.find()`, không tạo invocation mới qua Spring AOP proxy cho method find.

### 9.5. `BindingAspect.java`

~~~java
package example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class BindingAspect {

    @AfterReturning(
        pointcut = "execution(* example.DemoService.*(..))",
        returning = "value",
        argNames = "value"
    )
    public void whenStringReturned(JoinPoint jp, String value) {
        System.out.println("returning String: "
            + jp.getSignature().getName() + " -> " + value);
    }

    @AfterReturning("execution(* example.DemoService.ping(..))")
    public void afterVoidPing() {
        System.out.println("returning ping: normal exit");
    }

    @AfterThrowing(
        pointcut = "execution(* example.DemoService.*(..))",
        throwing = "error",
        argNames = "error"
    )
    public void whenIllegalArgument(JoinPoint jp, IllegalArgumentException error) {
        System.out.println("throwing IAE: "
            + jp.getSignature().getName() + " -> " + error.getMessage());
    }

    @AfterThrowing(
        pointcut = "execution(* example.DemoService.failState(..))",
        throwing = "failure",
        argNames = "failure"
    )
    public void whenFailState(Throwable failure) {
        System.out.println("throwing Throwable: failState -> "
            + failure.getClass().getSimpleName());
    }
}
~~~

Các điểm cần kiểm tra:

- `whenStringReturned` có `returning = "value"`, `String value` và `argNames = "value"` **trùng tên**.
- `whenIllegalArgument` có `throwing = "error"`, `IllegalArgumentException error` và `argNames = "error"` **trùng tên**.
- `whenFailState` có `Throwable failure` nên nhận `IllegalStateException` do target ném từ `failState()`.
- `afterVoidPing()` không ràng buộc return value nên có thể chạy sau method `void` kết thúc bình thường.
- `JoinPoint` là tham số đầu tiên trong hai advice, và không phải đưa tên của nó vào `argNames`.
- `@Aspect` không tự đăng ký làm Spring bean; `AppConfig` sẽ khai báo `@Bean` cho `BindingAspect`.
- Không có advice nào biến giá trị trả về hoặc exception thành một kết quả khác.

### 9.6. `AppConfig.java`

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public DemoService demoService() {
        return new DemoService();
    }

    @Bean
    public BindingAspect bindingAspect() {
        return new BindingAspect();
    }
}
~~~

Spring AOP nhận diện `BindingAspect` là aspect bean, tạo advisors cho các phương thức được annotate và áp dụng chúng lên `DemoService`. `proxyTargetClass = true` sử dụng class-based CGLIB proxy để code demo lấy bean bằng `DemoService.class`. Điều này không làm self-invocation chạy advice.

### 9.7. `Main.java`

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            DemoService service = context.getBean(DemoService.class);

            System.out.println("spring AOP proxy: " + AopUtils.isAopProxy(service));

            System.out.println("-- string return --");
            System.out.println("caller: " + service.find());

            System.out.println("-- primitive return --");
            System.out.println("caller: " + service.count());

            System.out.println("-- void normal return --");
            service.ping();

            System.out.println("-- illegal argument --");
            try {
                service.failInput();
            } catch (IllegalArgumentException e) {
                System.out.println("caller caught: " + e.getMessage());
            }

            System.out.println("-- other exception --");
            try {
                service.failState();
            } catch (IllegalStateException e) {
                System.out.println("caller caught: " + e.getMessage());
            }

            System.out.println("-- exception caught in target --");
            System.out.println("caller: " + service.recover());

            System.out.println("-- self invocation --");
            System.out.println("caller: " + service.selfCall());
        }
    }
}
~~~

Chạy ở thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 9.8. Stdout dự kiến

Các dòng do chính mã ví dụ in ra theo thứ tự, không kể log tùy cấu hình của Maven/JVM:

~~~text
spring AOP proxy: true
-- string return --
target: find
returning String: find -> FOUND
caller: FOUND
-- primitive return --
target: count
caller: 3
-- void normal return --
target: ping
returning ping: normal exit
-- illegal argument --
target: failInput
throwing IAE: failInput -> invalid
caller caught: invalid
-- other exception --
target: failState
throwing Throwable: failState -> IllegalStateException
caller caught: state
-- exception caught in target --
target: recover begin
target: handled invalid
returning String: recover -> RECOVERED
caller: RECOVERED
-- self invocation --
target: selfCall
target: find
returning String: selfCall -> FOUND
caller: FOUND
~~~

**Tại sao output ổn định?** Mỗi trường hợp được thiết kế chỉ có một advice **của loại phù hợp thực sự in log** trên mỗi invocation, không dựa vào thứ tự không xác định giữa hai advice cùng loại cùng match join point. Không log proxy class name cụ thể vì JDK/Spring có thể thay đổi tên sinh động.

### 9.9. Đọc kết quả từng trường hợp

**1) `find()`:** method trả `String "FOUND"`. Advice có `String value` chạy; caller vẫn nhận `FOUND` vì `@AfterReturning` không thay reference trả về.

**2) `count()`:** method trả primitive `int 3`. Kiểu trả về không phù hợp `String`; không có dòng `returning String`. Caller nhận `3`.

**3) `ping()`:** method `void` hoàn tất bình thường; `@AfterReturning` không có `returning` của riêng ping chạy và in `returning ping: normal exit`. Không có giá trị trả về nào cần bind.

**4) `failInput()`:** target ném `IllegalArgumentException("invalid")` ra ngoài. `@AfterThrowing` có tham số `IllegalArgumentException error` được gọi. Caller vẫn bắt ngoại lệ gốc sau advice.

**5) `failState()`:** target ném `IllegalStateException("state")`. Advice chỉ nhận `IllegalArgumentException` **không chạy** cho exception này; advice có `Throwable failure` và pointcut riêng match method `failState` sẽ chạy. Caller vẫn bắt `IllegalStateException`.

**6) `recover()`:** exception `IllegalArgumentException` được **catch trong chính target method**, rồi method trả String `RECOVERED`. Spring AOP thấy normal return, nên advice bound String chạy; `@AfterThrowing` không thấy exception bị catch nội bộ.

**7) `selfCall()`:** caller gọi `selfCall` qua proxy. Trong target, `this.find()` bypass proxy, nhưng kết quả `FOUND` của `selfCall` vẫn được trả về qua proxy, nên `@AfterReturning` in **một lần** cho `selfCall`. Không có dòng returning riêng cho method `find` nội bộ.

## 10. Nếu muốn lấy cả arguments và result/error?

Spring AOP cho phép binding đối số method qua `args(...)` kết hợp với `returning` hoặc `throwing`. Ví dụ minh họa dành cho method `lookup(String key)`:

~~~java
@AfterReturning(
    pointcut = "execution(* example.KeyService.lookup(..)) && args(key)",
    returning = "result",
    argNames = "key,result"
)
public void afterLookup(String key, String result) {
    System.out.println("lookup completed");
}
~~~

Đây là **snippet**, không thuộc project demo. Các tên `key` và `result` đều có thể được bind nếu expression và signature đúng. `args(key)` ràng buộc đối số đầu vào; `returning = "result"` ràng buộc giá trị **đầu ra**.

Tương tự:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.KeyService.lookup(..)) && args(key)",
    throwing = "failure",
    argNames = "key,failure"
)
public void afterLookupFailed(String key, RuntimeException failure) {
    System.out.println("lookup failed");
}
~~~

Tuy nhiên:

- Không đưa toàn bộ `key`, return object, exception message hoặc stack trace vào log nếu chứa bí mật.
- Kiểu `String key` hạn chế những invocation có argument phù hợp.
- Kiểu `String result` và `RuntimeException failure` tiếp tục giới hạn kết quả/exception được binding.
- Khi có overloads, generics hoặc interfaces, hãy test binding bằng **context thật**, không đoán chỉ từ tên method.
- Nếu có `JoinPoint` đứng đầu signature, có thể không ghi tên `JoinPoint` trong `argNames`.

## 11. Tại sao `@AfterReturning` không dùng để đổi kết quả?

Ví dụ sai về ý định:

~~~java
// Advice method có thể thực thi, nhưng String trả từ method này
// KHÔNG trở thành return value thay thế cho caller.
@AfterReturning(
    pointcut = "execution(* example.DemoService.find(..))",
    returning = "value",
    argNames = "value"
)
public void changeResult(String value) {
    value = "OVERRIDDEN";
}
~~~

Thay local variable `value` không thay giá trị caller nhận. Nếu bạn thực sự phải sửa result theo một policy rõ ràng, cần cân nhắc `@Around` để:

~~~java
@Around("execution(* example.DemoService.find(..))")
public Object transform(ProceedingJoinPoint pjp) throws Throwable {
    Object original = pjp.proceed();
    return transformUnderBusinessContract(original);
}
~~~

Đây là **snippet minh họa**, `transformUnderBusinessContract(...)` cần được ứng dụng định nghĩa. Dùng `@Around` với `return` thay thế có khả năng thay đổi contract, không nên dùng chỉ để audit hoặc log.

Lưu ý: nếu `@AfterReturning` nhận một **mutable object**, gọi method sửa object đó có thể tạo thay đổi quan sát được ở caller dù không thay reference. Đây vẫn là **mutation side effect** và có thể trái với yêu cầu “advice chỉ đọc kết quả”.

## 12. Tại sao `@AfterThrowing` không phải `catch` toàn cục?

Một advice kiểu:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.DemoService.failInput(..))",
    throwing = "error",
    argNames = "error"
)
public void logError(IllegalArgumentException error) {
    System.out.println("invalid input");
}
~~~

không biến việc ném exception thành một normal return. Khi advice return `void`, exception ban đầu tiếp tục lan truyền. Nó chỉ quan sát **exception thoát khỏi method execution được match**.

Nếu advice tự ném một exception mới, exception đó có thể **thay đổi exception caller thấy**. Đừng throw một lỗi mới từ logic logging/metrics chỉ vì backend log không sẵn sàng. Nếu muốn chủ động catch/translate/fallback, bạn cần thiết kế tại tầng xử lý phù hợp hoặc dùng `@Around` có hợp đồng rõ ràng về `try/catch` và trả về.

**Một ngoại lệ không được advice nhìn thấy** không đồng nghĩa exception không tồn tại: có thể target bắt nội bộ, một interceptor phía trong đã xử lý rồi return fallback, hoặc caller không đi qua Spring AOP proxy.

## 13. Giới hạn đặc thù của Spring AOP proxy

### 13.1. JDK proxy và CGLIB

- **JDK dynamic proxy**: interface-based; caller nên inject qua interface được expose.
- **CGLIB class-based proxy**: subclass target; không thể proxy final class, final/private method không được advice theo cơ chế override.
- **Cả hai** cùng chịu giới hạn self-invocation trong Spring AOP. Chuyển sang CGLIB không tự làm `this.method()` đi qua proxy.

### 13.2. Aspect bean phải là Spring bean

Class `@Aspect` có thể được đăng ký bằng `@Component` dưới component scan, hoặc một method `@Bean` trả về aspect instance. Nếu aspect chỉ tồn tại dưới dạng class trên classpath, Spring không có nghĩa vụ nhận advice của nó.

Trong Spring Framework thuần, `@EnableAspectJAutoProxy` là cấu hình quen thuộc. Trong Spring Boot, auto-configuration AOP có thể cung cấp hạ tầng này tùy dependency và setting; vẫn cần aspect bean đúng.

### 13.3. Method không qua proxy

- `new DemoService().find()` ngoài container không tự có Spring advice.
- `this.find()` trong target không tạo thêm invocation qua proxy.
- Lấy underlying target từ `Advised.getTargetSource()` rồi gọi trực tiếp có thể bypass advice.
- Một around interceptor ở ngoài return sớm mà không `proceed()` có thể ngăn invocation đi vào các advice phía trong.

### 13.4. Kết quả async/reactive

Nếu method trả về `CompletableFuture` hoặc một reactive publisher, `@AfterReturning` có thể chạy khi **method trả về Future/Publisher object**, không có nghĩa công việc bất đồng bộ/pipeline đã hoàn tất. `@AfterThrowing` tại method boundary không mặc nhiên nhận những lỗi phát sinh **sau đó** trong future/stream.

Cần theo dõi completion qua callback/lifecycle của Future hoặc reactive operator phù hợp khi muốn quan sát kết quả bất đồng bộ cuối cùng; không biến `returning` thành cơ chế chờ future hoàn thành.

## 14. Lỗi thường gặp và phương pháp chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| Spring báo lỗi binding tên tham số | `returning`/`throwing` không khớp tên tham số, `argNames` sai hoặc không có metadata tên | So thuộc tính annotation, Java parameter name; dùng `argNames` hoặc compile `-parameters` |
| `@AfterReturning` không chạy với một method `int` | Advice nhận `String`, kiểu không phù hợp | Đổi kiểu nếu mục đích nghiệp vụ cho phép hoặc dùng pointcut khác |
| Method `void` không kích hoạt advice có `returning` cụ thể | Không có return value để bind | Dùng `@AfterReturning` không binding cho normal completion |
| `@AfterThrowing(IllegalArgumentException)` không chạy với `IllegalStateException` | Typed throwing binding giới hạn throwable | Chọn type phù hợp hoặc `Throwable` nếu cần phạm vi rộng |
| Advice `@AfterThrowing` không thấy lỗi đã được `catch` | Target trả về bình thường | Kiểm tra exception thực sự thoát method |
| Exception vẫn tới caller sau `@AfterThrowing` | Đây là hành vi bình thường | Nếu cần handling/translation, thiết kế riêng; không coi after-throwing là catch |
| Bean có proxy nhưng method không chạy advice | Pointcut/returning/throwing không match hoặc advisor do tính năng khác | Kiểm tra eligible advisors và method signature |
| Advice chạy khi gọi trực tiếp service nhưng không chạy trong `this.method()` | Self-invocation không đi qua proxy | Tách method cần advice sang Spring bean khác khi phù hợp |
| JDK proxy không cast sang implementation | Runtime proxy type là interface-based | Inject interface hoặc xác minh class-based proxy có thể dùng |
| Dùng `JoinPoint.getArgs()` để tìm return value | Args là **tham số đầu vào** | Dùng `returning` để nhận return value |
| Đổi String bound value mà caller không thấy đổi | `@AfterReturning` không thay reference | Nếu cần biến đổi result, cân nhắc `@Around` có kiểm soát |
| Advice có hai tham số bind nhưng `argNames` chỉ ghi một | Binding thiếu hoặc mơ hồ | Liệt kê tên các bind variables và kiểm tra JoinPoint đầu tiên |
| Log về exception nhạy cảm bị lộ | In message/stack trace hoặc object bất kể policy | Giới hạn dữ liệu, chuẩn hóa labels và kiểm soát log |
| Advice chạy không theo thứ tự dự tính | Nhiều advisors/aspects, `@Order`/infrastructure order | Kiểm tra precedence và integration test cụ thể |

### Checklist nhanh

1. **Target là Spring bean?** Lấy qua context hoặc dependency injection.
2. **Aspect là bean?** Xem `@Component` scan hoặc `@Bean` và AOP setup.
3. **Pointcut đúng?** Kiểm tra package, class, method, visibility, JDK/CGLIB.
4. **Tên binding đúng?** `returning="value"` ↔ `value`, `throwing="error"` ↔ `error`.
5. **Kiểu binding đúng?** `String`, `Integer`, `Object`, `IllegalArgumentException`, `Throwable`.
6. **Nhánh đúng?** Normal return hay exceptional exit; exception có bị catch trong target không?
7. **Lời gọi qua proxy?** Không `new` hoặc self-invocation.
8. **Có infrastructure advisors hoặc quanh method shortcut?** Kiểm tra interceptor chain.
9. **Test negative cases?** Method `void`, return type khác, exception type khác, catch nội bộ.
10. **Preserve business contract?** Advice không đổi return reference, không mask exception, không log dữ liệu nhạy cảm.

## 15. Bài tập thực hành

**Tình huống A.** Aspect dùng `@AfterReturning(returning="result")` và `public void audit(String result)`; method trả `Integer`. Có chắc advice chạy?

**Đáp án:** Không. Typed binding String giới hạn kết quả tương thích String; Integer không tương thích String.

**Tình huống B.** `@AfterThrowing(throwing="failure")` bind `IllegalArgumentException failure`, nhưng method ném `IllegalStateException`. Lời advice có chạy không?

**Đáp án:** Không nếu không có exception phù hợp khác. Cả hai là RuntimeException nhưng không phải subtype của nhau.

**Tình huống C.** Method ném `IllegalArgumentException` rồi catch ngay bên trong và trả `"RECOVERED"`. Nhánh nào Spring AOP nhìn thấy?

**Đáp án:** Normal return. `@AfterReturning` có type tương thích sẽ chạy; `@AfterThrowing` không nhận exception đã bị target bắt.

**Tình huống D.** Tên advice method parameter là `value`, annotation ghi `returning="result"` và `argNames="value"`. Có hợp lệ không?

**Đáp án:** Không. Tên `returning` phải tương ứng binding name của tham số; hãy khai báo thống nhất.

**Tình huống E.** `@AfterReturning` không có `returning` được đặt riêng cho một method `void ping()` trả về bình thường. Nó có chạy không?

**Đáp án:** Có thể chạy khi pointcut match, Spring AOP proxy nhận invocation và method kết thúc bình thường. Không yêu cầu bind một return value.

**Tình huống F.** Một method `selfCall()` có `String` return, bên trong gọi `this.find()`. Aspect bound String match cả hai method. Caller gọi `selfCall()` trên proxy. Có mấy invocation đi qua proxy?

**Đáp án:** Một invocation cho `selfCall()`. `find()` nội bộ bị bypass, nhưng return String của `selfCall()` vẫn có thể được advice nhận.

**Tình huống G.** `@AfterThrowing` log lỗi và return `void`. Nó đã “handle exception” để caller không thấy lỗi chưa?

**Đáp án:** Chưa. Sau advice, exception gốc vẫn lan truyền khi không có tầng khác chủ động xử lý.

**Tình huống H.** Class `@Aspect` có advice đúng nhưng không đăng ký thành Spring bean. Có phải tất cả method có signature phù hợp sẽ được advice?

**Đáp án:** Không. Spring AOP cần aspect bean để nhận diện advisor; annotation trên class đơn độc không đủ.

**Tình huống I.** `@AfterReturning` nhận `String result` và gán `result = "new"`. Caller có nhận chuỗi mới không?

**Đáp án:** Không. Gán lại biến cục bộ trong advice không thay reference đã được method trả về. Muốn biến đổi return cần cân nhắc `@Around`.

## 16. Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): giải thích `@AfterReturning` và `@AfterThrowing` với `returning`, `throwing`, typed binding, naming, `JoinPoint` và advice ordering.
- [Spring Framework — Determining Argument Names](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#_determining_argument_names): `argNames`, Java `-parameters` và cơ chế tìm tên tham số.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký aspect bean, bật `@EnableAspectJAutoProxy`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): ranh giới JDK/CGLIB proxies và self-invocation.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): cú pháp pointcut Spring hỗ trợ.
- [Spring Framework — Spring AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): normal và exceptional exits ở cấp method join point.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-007">argNames, tên tham số và tùy chọn biên dịch -parameters</a>

<details>
<summary>Xem chi tiết</summary>

# argNames, tên tham số và tùy chọn biên dịch -parameters trong Spring Aspect

## 1. Vấn đề thực tế: advice lấy đúng đối số bằng cách nào?

Một Spring `@Aspect` thường cần biết **giá trị argument**, **annotation trên method**, **giá trị trả về** hoặc **ngoại lệ** của Spring bean được advise. Để đưa các giá trị ấy vào tham số của advice method, Spring phải giải quyết một bài toán: **tên biến được dùng trong biểu thức pointcut phải ánh xạ chính xác đến tên tham số nào trong Java advice method?**

Ví dụ:

```java
@Before("execution(* com.example.service..*.*(..)) && args(account,..)")
public void check(Account account) {
    System.out.println(account.id());
}
```

Ở đây, `account` trong `args(account,..)` là **tên binding**; `Account account` trong chữ ký advice là **tham số nhận actual argument**. Spring phải biết hai cái tên ấy tương ứng với nhau. Nếu file `.class` không giữ tên tham số, pointcut hoặc advice có nhiều tham số cùng kiểu, quá trình bind có thể thất bại hoặc trở nên mơ hồ.

Trong Spring AOP có hai cách chủ động, phổ biến nhất:

1. **Khai báo tường minh tên bằng `argNames`** trên annotation advice hoặc `@Pointcut`.
2. **Biên dịch Java với `-parameters`** để giữ metadata tên tham số cho reflection, cho phép Spring tự phát hiện tên từ method signature.

Spring còn có những cơ chế suy luận fallback khác, nhưng chúng có giới hạn; đừng dựa vào việc “đôi lúc suy ra được” để thiết kế hệ thống cần chạy ổn định giữa các bản build.

**Phạm vi bài:** tên tham số và binding trong Spring Framework `@AspectJ` **theo cơ chế Spring AOP proxy**, không phải hướng dẫn tổng quát về Java reflection hay lập trình AspectJ weaving bên ngoài Spring.

### Sau bài học, bạn sẽ làm được

- Hiểu ba khái niệm khác nhau: **tên biến trong pointcut**, **tên tham số Java** và **giá trị argument runtime**.
- Sử dụng `argNames` cho `@Before`, `@Around`, `@AfterReturning`, `@AfterThrowing` và `@Pointcut`.
- Biết khi nào có thể bỏ tên `JoinPoint`/`ProceedingJoinPoint` khỏi `argNames`.
- Bật `-parameters` khi build bằng Gradle, Maven hoặc `javac`.
- Kiểm tra metadata `Parameter.isNamePresent()` và phân biệt nó với debug information.
- Phát hiện lỗi khi đổi tên biến, thiếu tên binding, nhiều tham số cùng kiểu, hoặc không tương thích giữa named pointcut và advice.
- Chạy project Java 17/Spring Framework với **một aspect dùng `argNames`** và **một aspect dựa trên `-parameters`**, so sánh kết quả.
- Phân biệt name binding với những giới hạn bình thường của Spring AOP proxy như self-invocation.

## 2. Hai loại tên và giá trị thực tế

Xem lời gọi:

```java
orderService.submit("P-42", 3);
```

Giả sử target method là:

```java
public String submit(String code, int amount) {
    return "accepted:" + code + ":" + amount;
}
```

Một Spring advice có thể khai báo:

```java
@Before("execution(* demo.binding.OrderService.submit(..))"
      + " && args(code,amount)")
public void before(String code, int amount) {
    System.out.println(code + ":" + amount);
}
```

Ba phần có vai trò riêng:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Pointcut binding identifier** | `code` trong `args(code,amount)` | Biến do expression giới thiệu để bắt giá trị đối số phù hợp |
| **Advice Java parameter name** | `String code` | Tên mà Spring cần khám phá hoặc được khai báo tường minh |
| **Actual runtime value** | `"P-42"` | Đối số thật được caller truyền vào invocation |
| **Declared Java parameter type** | `String` / `int` | Điều kiện kiểu cho advice binding và method signature |
| **Spring bean proxy** | `orderService` được inject | Nơi Spring xét pointcut/advisors trước khi gọi target |

Đổi tên tham số của target từ `code` thành `sku` **không nhất thiết** làm expression `args(code,amount)` sai. `args(...)` có binding identifiers của **pointcut/advice**, không phải lệnh tìm target parameter theo tên source code. Nếu interface/implementation target được biên dịch không có `-parameters`, advice `args(code,amount)` vẫn có thể bind actual argument values nếu Spring xác định được **tên tham số của advice** bằng một trong các chiến lược hỗ trợ.

Nhưng nếu đổi tên tham số advice `String code` thành `String sku` mà giữ `args(code,amount)` và không đặt `argNames` tương ứng, Spring có thể không liên kết được biến đúng; khi đó mới xảy ra vấn đề trực tiếp của bài học.

### Phân biệt name binding với index

`JoinPoint.getArgs()[0]` lấy phần tử đầu theo **vị trí**, không cần đọc tên tham số advice. `args(code,amount)` bind theo những **tên symbol** được khai báo trong pointcut expression để Spring truyền giá trị đến advice parameters có kiểu tương ứng.

Khi một advice chỉ cần raw arguments để log đơn giản, `JoinPoint.getArgs()` đôi khi phù hợp hơn. Khi cần type-safe binding hoặc annotation/result/exception, `argNames`/`-parameters` mang ý nghĩa rõ ràng.

## 3. Spring tìm tên tham số như thế nào?

Theo Spring Framework reference, quá trình bind advice dựa trên những `ParameterNameDiscoverer` được thử theo thứ tự ưu tiên. Chiến lược nào lấy được tên hợp lệ trước sẽ được sử dụng.

### 3.1. Những chiến lược quan trọng

| Chiến lược Spring | Nguồn thông tin | Khi nào hiệu quả? |
| --- | --- | --- |
| `AspectJAnnotationParameterNameDiscoverer` | `argNames` khai báo trong annotation advice/pointcut | Khi tác giả tự đặt tên tường minh |
| `KotlinReflectionParameterNameDiscoverer` | Kotlin reflection | Với code Kotlin và thư viện reflection phù hợp |
| `StandardReflectionParameterNameDiscoverer` | Java reflection `Parameter.getName()` | Khi code Java được biên dịch bằng `javac -parameters` |
| `AspectJAdviceParameterNameDiscoverer` | Suy luận từ pointcut, `returning`, `throwing`, method types | Fallback trong những tình huống có đủ thông tin và không mơ hồ |

Nếu không discover được tên cần thiết, Spring có thể ném exception khi khởi tạo Spring AOP advisor hoặc khi chuẩn bị advice binding. **Không bảo đảm fallback suy luận được mọi expression phức tạp.**

Khi nhiều tham số cùng kiểu `String`, việc suy luận “tham số nào tương ứng tên nào” có thể không duy nhất; đó là một lý do lớn để dùng `-parameters` hoặc `argNames`.

### 3.2. Trường hợp không cần tên tham số

Advice không bind dữ liệu contextual nào:

```java
@Before("execution(public * com.example.service..*.*(..))")
public void count() {
    // Không có parameter cần bind.
}
```

Không cần `argNames` vì advice không nhận parameter.

Advice chỉ nhận join point:

```java
@Before("execution(public * com.example.service..*.*(..))")
public void inspect(JoinPoint jp) {
    System.out.println(jp.getSignature().getName());
}
```

Nếu tham số đầu là `JoinPoint` (hoặc `ProceedingJoinPoint` trong `@Around`), Spring có xử lý riêng cho tham số này. Advice chỉ có `JoinPoint` không nhất thiết phải khai báo `argNames`.

**Đừng suy ra từ hai trường hợp đơn giản rằng name metadata không quan trọng khi advice nhận nhiều parameters để bind.**

## 4. Cách thứ nhất: argNames tường minh

### 4.1. Trên @Before với args

```java
@Before(
    value = "execution(public * com.example.service..*.*(..))"
          + " && args(code,amount)",
    argNames = "code,amount"
)
public void before(String code, int amount) {
    System.out.println("code=" + code + ", amount=" + amount);
}
```

`argNames` nhận danh sách tên phân cách bằng dấu phẩy, đặt theo **thứ tự parameters được bind trong advice signature**. Tham số `String code` nhận binding `code`; `int amount` nhận `amount`.

Nếu Java source đổi parameter names thành `String sku, int qty` nhưng annotation vẫn dùng `argNames="code,amount"`, Spring vẫn có thể bind đúng theo thứ tự, miễn các điểm cắt/kiểu tham số khớp. Với phương pháp này, **tên được khai báo bởi `argNames` là nguồn sự thật cho expression**, không nhất thiết phải trùng tên tham số Java source.

Để dễ đọc và ít nhầm, thông lệ tốt vẫn là **giữ tên `argNames` trùng với tên tham số thực tế**.

### 4.2. Kết hợp @annotation và args

```java
@Before(
    value = "execution(public * com.example.service..*.*(..))"
          + " && args(code,amount) && @annotation(marker)",
    argNames = "code,amount,marker"
)
public void before(
        String code, int amount, Tracked marker) {
    System.out.println(marker.value() + ":" + code + ":" + amount);
}
```

`marker` là một annotation object được bind từ annotation trên method; `code` và `amount` đến từ actual method arguments. `Tracked` ở snippet là một runtime-retained annotation dùng trên method, cần định nghĩa/import phù hợp. Project chạy đầy đủ ở phần 9 có định nghĩa đó.

Đây là **ba binding names** và ba advice parameters. Không được chỉ ghi `argNames="marker"` nếu Spring không còn cách khám phá `code` và `amount` tương ứng.

### 4.3. Bỏ tên JoinPoint khỏi argNames

Spring có quy tắc tiện lợi: nếu **tham số đầu tiên** của advice là `JoinPoint`, `ProceedingJoinPoint` hoặc `JoinPoint.StaticPart`, bạn **có thể bỏ tên của tham số ấy** khỏi `argNames`.

```java
@Before(
    value = "execution(public * com.example.service..*.*(..))"
          + " && args(code,amount) && @annotation(marker)",
    argNames = "code,amount,marker"
)
public void before(
        JoinPoint jp, String code, int amount, Tracked marker) {

    String method = jp.getSignature().getName();
    System.out.println(method + ":" + marker.value());
}
```

Ở đây có **bốn Java parameters**, nhưng `argNames` chỉ có **ba tên**; không phải lỗi. Spring nhận ra `JoinPoint jp` đứng đầu, còn ba tên được dùng cho các binding variables.

Quy tắc này không có nghĩa bạn có thể bỏ ngẫu nhiên tên của bất kỳ tham số nào. Nếu tham số đầu tiên là `String code`, không được bỏ `code` rồi chờ Spring tự đoán phần còn lại.

### 4.4. Với @Around và ProceedingJoinPoint

```java
@Around(
    value = "execution(public String com.example.service.NameService.hello(..))"
          + " && args(name)",
    argNames = "name"
)
public Object around(ProceedingJoinPoint pjp, String name)
        throws Throwable {

    return pjp.proceed(new Object[] { name.trim() });
}
```

`pjp` đứng đầu và được bỏ khỏi danh sách `argNames`; `name` được bind từ method argument. Đây là snippet minh họa cần có `NameService` và AOP setup phù hợp.

Trong **Spring AOP**, `proceed(Object[])` lấy **bộ arguments của target method** theo đúng số lượng, thứ tự và kiểu; không tự đồng nghĩa với `argNames`, và đây là điểm cần chú ý khi so với AspectJ compiler/weaver.

### 4.5. Với @AfterReturning và returning

```java
@AfterReturning(
    pointcut = "execution(public String com.example.service..*.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterReturning(String result) {
    System.out.println("returned=" + result);
}
```

`returning="result"` đặt **tên binding** của giá trị trả về; `argNames="result"` ánh xạ binding ấy đến parameter advice. Advice chỉ chạy khi target invocation kết thúc bình thường và kết quả phù hợp ràng buộc kiểu `String`.

**`result` không phải tên tham số của target method.** Nó là tên symbol của giá trị trả về ở advice layer.

### 4.6. Với @AfterThrowing và throwing

```java
@AfterThrowing(
    pointcut = "execution(* com.example.service..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterThrowing(Throwable error) {
    System.out.println("thrown=" + error.getClass().getSimpleName());
}
```

`throwing="error"` bind throwable được ném khỏi method invocation vào advice parameter. Kiểu `Throwable` bao quát checked/unchecked exceptions. Nếu dùng `IllegalArgumentException`, advice chỉ nhận loại throwable phù hợp kiểu đó.

Việc đổi string `throwing="error"` thành `throwing="cause"` mà không đồng bộ tên binding tương ứng có thể khiến Spring không resolve được advice parameters.

## 5. Cách thứ hai: bật javac -parameters

### 5.1. Java mặc định có giữ parameter names không?

Khi biên dịch Java bằng `javac` mà không bật `-parameters`, file `.class` thông thường **không có metadata MethodParameters cần cho `java.lang.reflect.Parameter` cung cấp tên source-level một cách đáng tin**.

Ví dụ:

```java
public void before(String code, int amount) {}
```

Nếu compile thiếu `-parameters`, Java reflection có thể trả những tên tổng hợp như `arg0` và `arg1`; `Parameter.isNamePresent()` thường là `false`. Những tên đó không nhất thiết trùng biến `code`/`amount` dùng trong pointcut.

Với:

```bash
javac -parameters -d out MyAspect.java
```

`javac` giữ tên parameters trong class metadata; reflection có thể thấy `code` và `amount`, cùng `isNamePresent() == true`.

Lệnh trên minh họa syntax compiler option, **không đủ để compile một Spring aspect độc lập** nếu thiếu classpath Spring/AspectJ và các source dependencies. Project chạy ở phần 9 cấu hình Gradle đầy đủ.

### 5.2. Gradle

Nếu dùng Gradle với plugin Java:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
```

Lệnh này thêm `-parameters` vào các tác vụ compile Java. Nếu muốn chỉ đặt cho source set hoặc module chứa aspects, có thể cấu hình task cụ thể, nhưng cần đảm bảo **các aspect classes cần name discovery** thật sự được biên dịch với flag.

Trong Gradle hiện đại, có thể sử dụng cách tương đương:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add('-parameters')
}
```

Đừng mặc định một flag thiết lập ở module A sẽ tự áp dụng cho module B. Trong multi-module Gradle, cấu hình cần áp dụng ở module chứa mã aspect cần được compile. Việc Gradle daemon hoặc IDE chọn JDK khác với toolchain cấu hình cũng cần được kiểm tra khi build có lỗi.

### 5.3. Maven

Với Maven Compiler Plugin hỗ trợ tham số này, có thể khai báo:

```xml
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
  </plugins>
</build>
```

Đây là đoạn cấu hình Maven POM cần ghép vào project thực tế, **không phải POM hoàn chỉnh**. `<parameters>true</parameters>` khiến plugin truyền metadata names tương ứng cho Java compilation; dùng version plugin được tổ chức dự án phê duyệt.

### 5.4. Kiểm tra metadata bằng reflection

```java
Method method =
    MyAspect.class.getDeclaredMethod("before", String.class, int.class);

for (Parameter parameter : method.getParameters()) {
    System.out.println(parameter.getName()
            + ":" + parameter.isNamePresent());
}
```

Snippet này cần `java.lang.reflect.Method`, `Parameter` và lớp `MyAspect` thật. Nếu compile bằng `-parameters` và method có hai tham số mang tên `code`, `amount`, ví dụ có thể in:

```text
code:true
amount:true
```

Khi không có flag, **tên cụ thể do reflection trả về không nên dùng như API hợp đồng**; kiểm tra `isNamePresent()` thay vì phỏng đoán tên biến từ debugger.

## 6. -parameters không đồng nghĩa debug information (-g)

Một nhầm lẫn phổ biến là cho rằng compile có debug symbols thì Spring chắc chắn biết tên advice parameters.

| Cơ chế | Thông tin | Có thay thế -parameters theo cách ổn định không? |
| --- | --- | --- |
| `javac -parameters` | Metadata `MethodParameters` đọc được bằng `java.lang.reflect.Parameter` | **Có**, dành cho Java reflection parameter-name discovery |
| `javac -g` | Debug information phục vụ debugger, có thể có local variable tables | **Không nên xem là thay thế tương đương** |
| `@Before(argNames="...")` | Tên binding được khai báo rõ trong annotation | **Có** cho binding advice đó, không cần phụ thuộc reflection names |
| AspectJ `ajc` | Có cơ chế giữ tên cần thiết của `@AspectJ` advice | Có thể không cần `argNames` nếu tên đã được compiler cung cấp |
| Compiler inference fallback của Spring | Suy luận từ biểu thức/loại parameter | Chỉ trong các trường hợp đủ thông tin, dễ có ambiguity |

Đối với Spring Framework hiện đại, tài liệu chính thức **khuyến nghị `-parameters`** cho Java reflection. Không lấy behavior từng có ở các phiên bản cũ hoặc build tool cũ làm hợp đồng bất biến.

**Điểm tinh tế:** Metadata trong `-parameters` cần xuất hiện trên **advice/pointcut method** để Spring tìm tên parameters của advice/pointcut. Bạn không nhất thiết phải gắn `-parameters` vào mọi target method nếu mục tiêu duy nhất là bind `args(code,amount)` đến advice parameters; nhưng việc giữ tên target method cũng hữu ích cho các tính năng Spring khác như data binding, validation và reflection. Quyết định phạm vi phải dựa trên dependency và đặc điểm project.

## 7. Named pointcut cũng có parameter names

Một named pointcut được tái sử dụng có thể mang parameters:

```java
@Pointcut(
    value = "execution(public * com.example.service..*.*(..))"
          + " && args(code,amount)",
    argNames = "code,amount"
)
public void orderOperation(String code, int amount) {}
```

Khi advice dùng named pointcut:

```java
@Before(
    value = "orderOperation(code,amount)",
    argNames = "code,amount"
)
public void before(String code, int amount) {
    // ...
}
```

Hai lớp ánh xạ tồn tại:
1. `@Pointcut` method: expression `args(code,amount)` và signature của named pointcut nhận đúng binding.
2. Advice method: expression `orderOperation(code,amount)` cung cấp binding cho advice signature.

**Không lẫn** `argNames` ở `@Pointcut` với `argNames` ở advice. Nếu khai báo explicit ở một nơi nhưng nơi còn lại thiếu thông tin tên phù hợp, binding vẫn có thể thất bại.

### Chia sẻ sang aspect khác

Khi named pointcut nằm trong `com.example.aop.CommonPointcuts`, advice ở một `@Aspect` khác có thể tham chiếu:

```java
@Before(
    value = "com.example.aop.CommonPointcuts.orderOperation(code,amount)",
    argNames = "code,amount"
)
public void record(String code, int amount) {
    // ...
}
```

Đây là snippets minh họa cho visibility/name binding; các class phải được định nghĩa và dùng đúng Spring bean/pointcut configuration. Thường nên đặt named pointcut public nếu cần tham chiếu từ class/package khác.

## 8. Lỗi thường gặp khi sử dụng argNames và -parameters

### 8.1. Thiếu tên dẫn tới bind không được

```java
@Before("execution(* com.example.service..*.*(..))"
      + " && args(code,amount)")
public void before(String x, int y) {
}
```

Nếu không có `-parameters` và không khai báo `argNames`, Spring có thể suy luận được trong vài trường hợp đơn giản nhưng **không có bảo đảm ổn định trong mọi biểu thức**. Nếu quá trình suy luận không thành công, startup AOP có thể báo lỗi thiếu/bất nhất tham số binding.

Khắc phục:

```java
@Before(
    value = "execution(* com.example.service..*.*(..))"
          + " && args(code,amount)",
    argNames = "code,amount"
)
public void before(String x, int y) {
}
```

Cách này cho parser tên binding rõ theo thứ tự parameter. Hoặc giữ tên `String code,int amount` trong source và build với `-parameters`.

### 8.2. Hai tham số cùng kiểu gây ambiguity

```java
@Before("execution(* com.example.service..*.*(..))"
      + " && args(first,second)")
public void before(String first, String second) {
}
```

Nếu thiếu `argNames`/`-parameters`, algorithm suy luận không luôn xác định được symbol nào ứng với parameter nào. Không nên “tráo” thứ tự ý nghĩa của `first` và `second` chỉ vì hai tham số đều `String`.

Đặt explicit:

```java
@Before(
    value = "execution(* com.example.service..*.*(..))"
          + " && args(first,second)",
    argNames = "first,second"
)
public void before(String first, String second) {
}
```

### 8.3. Trả kết quả và lỗi khác nhau: returning/throwing

Sai về tên:

```java
@AfterReturning(
    pointcut = "execution(* com.example.service..*.*(..))",
    returning = "value",
    argNames = "result"
)
public void after(String result) {}
```

`returning` muốn bind symbol `value`, trong khi `argNames` lại khai báo `result`. Đây là sự **không nhất quán về tên** cần sửa.

Đúng:

```java
@AfterReturning(
    pointcut = "execution(* com.example.service..*.*(..))",
    returning = "value",
    argNames = "value"
)
public void after(Object result) {}
```

Java parameter thực có thể tên `result`, vì explicit `argNames` mô tả binding names theo thứ tự. Tuy nhiên, để dễ bảo trì, nên đổi Java parameter thành `value` đồng nhất.

Với `throwing`, nguyên tắc hoàn toàn tương tự.

### 8.4. Đếm sai số tên do JoinPoint

Sai về assumption:

```java
@Before(
    value = "execution(* com.example.service..*.*(..))"
          + " && args(code,amount)",
    argNames = "jp,code"
)
public void before(JoinPoint jp, String code, int amount) {}
```

Mẫu này có **thiếu `amount`** cho binding, và `jp` lại được ghi như tên thường mà Spring có thể xử lý đặc biệt. Đúng và dễ đọc:

```java
@Before(
    value = "execution(* com.example.service..*.*(..))"
          + " && args(code,amount)",
    argNames = "code,amount"
)
public void before(JoinPoint jp, String code, int amount) {}
```

### 8.5. Cho rằng -parameters sửa mọi lỗi pointcut

`-parameters` **không giải quyết**:
- Pointcut `execution(...)` sai package/type/method signature;
- Spring aspect chưa được đăng ký bean;
- Target không được proxy;
- Self-invocation `this.method()` bypass proxy;
- CGLIB không override được `final`/`private` method;
- Annotation cần `RetentionPolicy.RUNTIME` nhưng không có;
- Binding kiểu không tương thích, hoặc expression `args(...)` yêu cầu arity khác actual method;
- Exception hoặc result không có type phù hợp với advice binding;
- Nhiều advisors trong context cùng tham gia và thay outcome.

Đó là các vấn đề khác, không phải name discovery.

## 9. Ví dụ chạy đầy đủ: explicit argNames và tự tìm tên từ -parameters

Ví dụ này tạo **một Spring service** với method `submit(String code, int amount)` và **hai aspects**:
- `ExplicitAspect` chủ động khai báo `argNames`, bind cả `args` và `@annotation`, đếm `@Before`, `@AfterReturning`, `@AfterThrowing`.
- `InferredAspect` **không dùng `argNames`**, nhờ `javac -parameters` để Spring đọc tên parameters `code`/`amount` từ bytecode.

Chúng ta tránh in log bên trong mỗi advice để kết quả không phụ thuộc thứ tự giữa hai aspects. Mỗi aspect tự đếm, rồi `main` in snapshot sau khi gọi service.

**Môi trường:** Java 17+, Spring Framework `6.1.14`, AspectJ Weaver `1.9.22.1`, Gradle. Spring Framework thuần, không cần Spring Boot, web server hoặc database.

### 9.1. Cấu trúc project

```text
spring-aop-parameter-names/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/binding/
    ├── Tracked.java
    ├── OrderService.java
    ├── ExplicitAspect.java
    ├── InferredAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 9.2. Gradle bật -parameters

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-parameter-names'
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

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.binding.DemoApplication'
}
```

Điểm quyết định là `options.compilerArgs += ['-parameters']`: `InferredAspect.before(String code, int amount)` giữ lại hai tên cần thiết cho Spring reflection name discovery.

### 9.3. Annotation dùng trong @annotation binding

`src/main/java/demo/binding/Tracked.java`:

```java
package demo.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tracked {
    String value();
}
```

`@Retention(RUNTIME)` bảo đảm Spring có thể đọc method annotation trong quá trình pointcut/advice matching. `@Tracked` đặt ở **method**, không phải argument type.

### 9.4. Target Spring service

`src/main/java/demo/binding/OrderService.java`:

```java
package demo.binding;

public class OrderService {

    @Tracked("orders")
    public String submit(String code, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "amount must be positive");
        }
        return "accepted:" + code + ":" + amount;
    }
}
```

Method không `final` và target class không `final`, nên có thể được Spring CGLIB proxy. Hai lần gọi trong `main` đều có `code="P42"`, nhưng khác `amount`.

### 9.5. Aspect khai báo argNames tường minh

`src/main/java/demo/binding/ExplicitAspect.java`:

```java
package demo.binding;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ExplicitAspect {

    private int beforeCalls;
    private int returnedCalls;
    private int thrownCalls;
    private String lastBinding = "";

    @Before(
        value = "execution(public String demo.binding.OrderService.submit(..))"
              + " && args(code,amount) && @annotation(tag)",
        argNames = "code,amount,tag"
    )
    public void before(JoinPoint jp, String code, int amount, Tracked tag) {
        beforeCalls++;
        lastBinding = jp.getSignature().getName()
            + ":" + code + ":" + amount + ":" + tag.value();
    }

    @AfterReturning(
        pointcut = "execution(public String demo.binding.OrderService.submit(..))",
        returning = "result",
        argNames = "result"
    )
    public void returned(String result) {
        returnedCalls++;
    }

    @AfterThrowing(
        pointcut = "execution(public String demo.binding.OrderService.submit(..))",
        throwing = "error",
        argNames = "error"
    )
    public void thrown(IllegalArgumentException error) {
        thrownCalls++;
    }

    public int beforeCalls() { return beforeCalls; }
    public int returnedCalls() { return returnedCalls; }
    public int thrownCalls() { return thrownCalls; }
    public String lastBinding() { return lastBinding; }
}
```

Trong `before`:
- `args(code,amount)` bind actual arguments theo vị trí và kiểu.
- `@annotation(tag)` bind instance của `@Tracked("orders")` từ method execution.
- `JoinPoint jp` là tham số **đứng đầu**, do đó `argNames="code,amount,tag"` cố ý **không có `jp`**.
- `@AfterReturning` chỉ tăng trên normal return.
- `@AfterThrowing` dùng kiểu `IllegalArgumentException`, đúng kiểu lỗi của ví dụ.

Các biến đếm là field mutable trên singleton aspect; ví dụ gọi tuần tự để output đơn giản. Trong production concurrent, hãy dùng counters thread-safe hoặc hệ metric có hỗ trợ concurrency; không coi code đếm `int` này là mẫu production.

### 9.6. Aspect dựa vào -parameters

`src/main/java/demo/binding/InferredAspect.java`:

```java
package demo.binding;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class InferredAspect {

    private int beforeCalls;

    @Before(
        "execution(public String demo.binding.OrderService.submit(..))"
        + " && args(code,amount)"
    )
    public void before(String code, int amount) {
        beforeCalls++;
    }

    public int beforeCalls() {
        return beforeCalls;
    }
}
```

Không có `argNames` trong `@Before`. Vì build đã bật `-parameters`, Spring có thể lấy tên `code`, `amount` từ chính method `InferredAspect.before(...)` bằng `StandardReflectionParameterNameDiscoverer`. Nếu bỏ `-parameters` rồi build lại, Spring có thể dựa vào fallback inference hoặc báo lỗi, tùy mức rõ ràng của expression; **không nên dựa vào kết quả một thử nghiệm đơn giản để bảo đảm mọi advice phức tạp vẫn chạy**.

### 9.7. Khởi tạo context và đăng ký aspects

`src/main/java/demo/binding/AppConfig.java`:

```java
package demo.binding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public ExplicitAspect explicitAspect() {
        return new ExplicitAspect();
    }

    @Bean
    public InferredAspect inferredAspect() {
        return new InferredAspect();
    }
}
```

`@Aspect` **không tự đăng ký bean**; ba `@Bean` methods khiến service/aspects thuộc Spring context. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật Spring AOP và tạo class-based proxy phù hợp với `OrderService` không implement interface.

### 9.8. Main kiểm tra Java reflection và advice binding

`src/main/java/demo/binding/DemoApplication.java`:

```java
package demo.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) throws Exception {
        Method method = InferredAspect.class.getDeclaredMethod(
            "before", String.class, int.class);

        boolean namesPresent = Arrays.stream(method.getParameters())
            .allMatch(Parameter::isNamePresent);
        System.out.println("namesPresent=" + namesPresent);

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            ExplicitAspect explicit =
                context.getBean(ExplicitAspect.class);
            InferredAspect inferred =
                context.getBean(InferredAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            System.out.println("result=" + service.submit("P42", 3));

            try {
                service.submit("P42", 0);
            } catch (IllegalArgumentException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println(
                "explicitBefore=" + explicit.beforeCalls());
            System.out.println(
                "explicitReturned=" + explicit.returnedCalls());
            System.out.println(
                "explicitThrown=" + explicit.thrownCalls());
            System.out.println(
                "inferredBefore=" + inferred.beforeCalls());
            System.out.println(
                "lastBinding=" + explicit.lastBinding());
        }
    }
}
```

### 9.9. Chạy và stdout dự kiến

Trong thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu project đã có Gradle Wrapper thì dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows. Hãy dùng Gradle hỗ trợ toolchain Java 17.

**Những dòng do Java code tự in**, không kể Gradle output hoặc logger của Spring:

```text
namesPresent=true
proxy=true
result=accepted:P42:3
caught=amount must be positive
explicitBefore=2
explicitReturned=1
explicitThrown=1
inferredBefore=2
lastBinding=submit:P42:0:orders
```

Giải thích:
- `namesPresent=true` xác nhận **method `InferredAspect.before`** mang Java parameter-name metadata.
- `proxy=true` cho thấy `OrderService` được Spring AOP proxy, nên lời gọi trong `main` thực sự được interception.
- `explicitBefore=2`: explicit `argNames` bind thành công trong cả hai lần gọi.
- `inferredBefore=2`: cách khám phá tên bằng `-parameters` cũng bind thành công hai lần.
- `explicitReturned=1`: lần `amount=3` trả bình thường.
- `explicitThrown=1`: lần `amount=0` ném `IllegalArgumentException`.
- `lastBinding=submit:P42:0:orders` chứng minh binding nhận đúng method name, actual `code`/`amount`, và giá trị `@Tracked` trên method.

**Không suy luận về thứ tự hai `@Before`** từ ví dụ: chúng không in log trong invocation, nên số đếm là ổn định dù Spring thay advisor ordering. Nếu nghiệp vụ phụ thuộc thứ tự advice, khai báo và kiểm thử `@Order`/`Ordered` riêng.

## 10. Khi nào ưu tiên argNames, khi nào ưu tiên -parameters?

| Tình huống | Khuyến nghị |
| --- | --- |
| Java project đang dùng Spring Framework hiện đại, kiểm soát được build | Bật `-parameters` nhất quán trong module chứa aspects |
| Advice phức tạp, nhiều parameters cùng kiểu, muốn public documentation rõ | Thêm `argNames` tường minh và dùng tên nhất quán |
| Java class được build bởi thư viện bên ngoài, không kiểm soát compiler options | Dùng `argNames` khi viết advice của mình, tránh phụ thuộc names bị strip |
| Advice chỉ nhận `JoinPoint` | Thường không cần `argNames` |
| Advice chỉ log trước và không bind tham số | Không cần `argNames` hoặc reflection metadata |
| Advice bind `returning`/`throwing` | Giữ symbol tương ứng với `argNames` và advice parameter đúng kiểu |
| Named pointcut truyền parameters sang advice | Bảo đảm name metadata hoặc explicit `argNames` ở cả pointcut method và advice |
| Tính năng cần đọc tên **target method parameters** cho API/docs/data binding | Bật `-parameters` ở module chứa **target classes** liên quan |
| Di chuyển code giữa Gradle modules hoặc CI khác IDE | Kiểm tra compiler flag ở đúng compile task, tránh assumption flag tự lan |
| Muốn loại bỏ ambiguity khó tái hiện | Viết integration test Spring AOP proxy trên build artifact thực |

Không cần đồng thời khai báo `argNames` cho mọi advice đã có `-parameters`. Tuy vậy, với thư viện công khai hoặc expression phức tạp, explicit names có thể giúp người đọc hiểu binding contract và tránh phụ thuộc vào flags ở môi trường build bên ngoài.

## 11. Debug lỗi tên tham số theo từng bước

1. **Đọc chính xác exception root cause.** Lỗi có thể xuất hiện lúc Spring xây advisor hoặc khởi tạo context; đừng gộp mọi exception thành “pointcut sai”.
2. **Kiểm tra Spring bean.** Aspect đã được đăng ký và `@EnableAspectJAutoProxy` có hiệu lực trong đúng context?
3. **Kiểm tra biểu thức.** `args(x,y)` / `@annotation(tag)` / `returning` / `throwing` có tạo đủ binding variables không?
4. **Kiểm tra advice signature.** Số parameters, thứ tự, kiểu và tên có tương ứng expression?
5. **Kiểm tra tham số JoinPoint đầu tiên.** Nếu có, đừng bắt buộc phải khai báo tên nó trong `argNames`.
6. **Kiểm tra flags thực của compiler.** Build config chứa `-parameters` không đồng nghĩa source class được biên dịch lại thành công. Clean/rebuild khi cần.
7. **Kiểm tra trực tiếp `Parameter.isNamePresent()`** trên advice method trong compiled class.
8. **Không chỉ kiểm tra Debug/LineNumberTable.** `-g` không phải hợp đồng thay thế Java reflection parameter metadata.
9. **Nếu có nhiều parameters cùng kiểu**, dùng `argNames` rõ, không phụ thuộc fallback suy luận.
10. **Kiểm tra named pointcuts riêng.** Lớp chứa pointcut có tên parameters và annotation `argNames` phù hợp, expression được tham chiếu đúng scope/visibility?
11. **Kiểm tra Spring proxy/call path.** Nếu startup không lỗi nhưng advice không chạy, có thể vấn đề là method không match hoặc bị self-invocation, không phải tên parameters.
12. **Viết kiểm thử positive/negative.** Chạy method với các actual arguments/annotations phù hợp và không phù hợp; xác nhận advice values thực sự đúng, không chỉ đếm số lượt gọi.

### Ví dụ nhanh kiểm tra reflection metadata

```java
Method m = InferredAspect.class.getDeclaredMethod(
    "before", String.class, int.class);

for (Parameter p : m.getParameters()) {
    System.out.println(p.getName() + ":" + p.isNamePresent());
}
```

Trong project ở phần 9, đoạn này có thể in:

```text
code:true
amount:true
```

Khi dùng nhiều hệ build, nên đặt test/assertion tự động để phát hiện việc `-parameters` bị tắt ở module chứa aspects.

## 12. Checklist thực hành

- [ ] Xác định đây là bài toán **Spring Aspect binding**, không nhầm với tên target method parameters.
- [ ] Mỗi biến trong `args(...)`, `@annotation(...)`, `target(...)`, `this(...)`... có type và tên advice parameter tương ứng.
- [ ] Nếu dùng `argNames`, số lượng/thứ tự tên khớp với các parameters cần bind.
- [ ] `JoinPoint`/`ProceedingJoinPoint`/`JoinPoint.StaticPart` ở tham số đầu được xử lý đúng, có thể bỏ tên trong `argNames`.
- [ ] Tên `returning`/`throwing` khớp tên binding của advice.
- [ ] `javac -parameters` bật ở đúng module/compile task khi dựa trên reflection names.
- [ ] Không đánh đồng `-g` và `-parameters`.
- [ ] Với named pointcut nhận parameters, kiểm tra cả tên ở pointcut declaration và advice caller.
- [ ] Aspect có `@Aspect` và là Spring bean; target do Spring quản lý.
- [ ] Spring AOP proxy được tạo và method invocation thực sự đi qua proxy.
- [ ] Có test cho multiple same-type parameters để phát hiện ambiguity.
- [ ] Không kỳ vọng `-parameters` sửa self-invocation, method `final`, sai pointcut hoặc thiếu annotation retention.

## 13. Kết luận

**`argNames` và `-parameters` giải quyết cùng một nhu cầu cốt lõi trong Spring Aspect: cho Spring biết tên cần thiết để liên kết pointcut variables với advice parameters.**

- **`argNames`:** khai báo **tường minh** những binding names theo thứ tự chữ ký advice/pointcut, phù hợp khi muốn rõ ràng hoặc không kiểm soát compiler flags.
- **`-parameters`:** giữ Java method parameter names trong bytecode để Spring tìm bằng reflection; là phương án được Spring khuyến nghị cho Java code hiện đại.
- **Fallback inference:** chỉ là trợ giúp trong một số trường hợp có đủ thông tin; không nên xem như bảo đảm cho mọi advice phức tạp.

Cả hai cách chỉ liên quan **name binding**. Chúng không thay đổi semantics của `args`, `@annotation`, `returning` hoặc `throwing`, và không xóa giới hạn Spring AOP là **chỉ method invocation qua Spring-managed proxy mới có thể nhận advice**.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice, Determining Argument Names](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): danh sách `ParameterNameDiscoverer`, `argNames`, `-parameters`, quy tắc bỏ tên `JoinPoint` đứng đầu và `returning`/`throwing`.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): `args`, `@annotation`, binding pointcuts và named pointcut declarations.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký Spring `@Aspect` và auto-proxy infrastructure.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation và giới hạn proxy.
- [Spring Framework API — AspectJAdviceParameterNameDiscoverer](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/aspectj/AspectJAdviceParameterNameDiscoverer.html): thuật toán fallback phát hiện tên, những trường hợp ambiguity.
- [Java 17 — javac Tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): tùy chọn `-parameters` để tạo MethodParameters metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-008">Đối số null, kiểu nguyên thủy, overload và varargs</a>

<details>
<summary>Xem chi tiết</summary>

# Đối số `null`, kiểu nguyên thủy, overload và varargs trong Spring AOP

## 1. Vì sao đây là vấn đề quan trọng khi viết Spring Aspect?

Một lời gọi tới Spring bean có thể có tham số `null`, `int`, `boolean`, các method cùng tên nhưng khác kiểu tham số (*overload*), hoặc một method khai báo `String...` (*varargs*). Khi sử dụng `@Before`, `@Around` hay một loại advice khác, ta cần biết Spring AOP nhận diện **method nào** và lấy **giá trị đối số nào**.

Có hai phép xét khác nhau, rất dễ nhầm:

1. **`execution(...)`** chọn *method execution join point* theo **chữ ký method đã khai báo**, như tên method, số lượng và kiểu formal parameters.
2. **`args(...)`** xét **các đối số tại lời gọi**, có thể kiểm tra kiểu hoặc **bind argument value** vào tham số của advice. Một số điều kiện của `args` cần kiểm tra runtime, trong khi `execution` thường dùng thông tin tĩnh về chữ ký.

Trong Spring AOP, cả hai chỉ áp dụng cho **method execution đi qua Spring AOP proxy**. Chúng không tự biến mọi method call trong Java thành join point có thể được advice.

Các vấn đề cụ thể:

| Tình huống | Điểm cần nhớ |
| --- | --- |
| Đối số `null` | Không có runtime class của giá trị `null`; khi kiểm tra theo type, cần xét declared parameter type và cơ chế pointcut, không dùng `arg.getClass()` trực tiếp |
| `int`, `boolean` ... | Java method vẫn có kiểu primitive; nhưng `JoinPoint.getArgs()` là `Object[]`, nên primitive values xuất hiện dưới dạng boxed values |
| Overload | Java xác định overload theo compile-time argument types; Spring proxy intercept **method cụ thể đã được chọn**, không chọn overload mới |
| Varargs `T...` | Tại bytecode/reflective method signature, varargs là **một tham số array `T[]`**, không phải vô hạn tham số formal |
| Bind tham số advice | Tên trong `args(name)` phải được Spring liên kết đúng với Java advice parameter; có thể cung cấp `argNames` |
| Sửa arguments trong `@Around` | `proceed(Object[])` phải nhận đúng số lượng và kiểu tham số logic, chú ý primitive, `null` và array của varargs |

**Phạm vi:** tập trung vào **Spring Aspect / Spring AOP**, các kiểu advice, `JoinPoint`/`ProceedingJoinPoint` và method matching của Spring; không lặp lại lý thuyết Aspect tổng quát hay AspectJ compile-time weaving.

## 2. Hai cách đọc đối số trong advice

### 2.1. Cách tổng quát: `JoinPoint.getArgs()`

Mọi loại advice hỗ trợ `JoinPoint` làm tham số đầu tiên, còn `@Around` dùng `ProceedingJoinPoint`. Ví dụ:

```java
@Before("execution(* com.example.service..*.*(..))")
public void inspect(JoinPoint jp) {
    Object[] args = jp.getArgs();

    System.out.println("count=" + args.length);
    for (int i = 0; i < args.length; i++) {
        Object value = args[i];
        System.out.println(
            i + ": " + (value == null ? "<null>" : value.getClass().getName())
        );
    }
}
```

Đây là **đoạn minh họa cần đặt trong Spring-managed `@Aspect` bean**. `getArgs()` cho bạn từng argument tại invocation dưới dạng phần tử của `Object[]`:

- Argument được truyền `null` → phần tử `null`, không có class để gọi `getClass()`.
- Formal parameter `int` → giá trị ở `Object[]` được **box** thành `Integer` tương ứng.
- Varargs `String... tags` → thường là **một phần tử `String[]`** trong `Object[]` (không tự tách thành nhiều phần tử ở top level).
- Với overload, số/kiểu **giá trị runtime** chưa chắc nói hết chữ ký method cụ thể. Hai overload `foo(String)` và `foo(Integer)` đều có thể được gọi bằng một `null` được cast tương ứng.

`Object[]` chứa các **đối số được truyền vào lời gọi**, không phải mảng khai báo kiểu formal parameters. Muốn biết kiểu khai báo của method, sử dụng `MethodSignature`.

### 2.2. Cách có kiểu: `args(name)` để bind

Spring AOP cho phép ràng buộc argument vào tham số Java của advice:

```java
@Before(
    value = "execution(* com.example.service..*.save(..)) && args(id,..)",
    argNames = "id"
)
public void beforeSave(String id) {
    System.out.println("id=" + id);
}
```

**Đây là snippet, không phải file standalone.** Cú pháp `args(id,..)`:

- Chọn join point có **ít nhất một argument**, argument đầu thỏa binding type của `id`.
- Bind giá trị argument đầu vào tham số advice `String id`.
- `,..` cho phép còn **không hoặc nhiều** argument sau đó.
- `argNames = "id"` nói rõ tên binding để giảm phụ thuộc vào metadata tên tham số của compiler.

Không được nhầm:

- `args(java.lang.String)`: phần trong là **type pattern**.
- `args(id)` và Java advice parameter `String id`: đây là **binding form**.
- `execution(* *.*(java.lang.String))`: kiểm tra **declared method parameter type**, không phải class runtime của giá trị cụ thể.

Spring khuyến nghị sử dụng typed binding khi có thể, nhưng với yêu cầu quan sát nhiều overload, `null` và varargs cùng lúc, `JoinPoint.getArgs()` kết hợp `MethodSignature` thường dễ phân tích hơn.

## 3. Trường hợp đối số `null`

### 3.1. Một giá trị `null` không có runtime class

Giả sử method:

```java
public String accept(String value) {
    return value == null ? "EMPTY" : value;
}
```

Và lời gọi thông qua Spring proxy:

```java
service.accept(null);
```

Tại Spring advice, `jp.getArgs().length == 1` và `jp.getArgs()[0] == null`. Không được viết:

```java
String typeName = jp.getArgs()[0].getClass().getName();
```

vì có thể gây `NullPointerException` **ngay trong advice**, làm lỗi mới xuất hiện trước khi target chạy.

Cách an toàn:

```java
Object arg = jp.getArgs()[0];
String kind = (arg == null) ? "null" : arg.getClass().getSimpleName();
```

Các snippet ở phần này không phải project hoàn chỉnh; phần 7 có chương trình runnable.

### 3.2. Phân biệt `execution` và `args` khi có `null`

Với method khai báo `accept(String value)`:

- `execution(* *.accept(java.lang.String))` match **chữ ký một tham số `String`**, dù invocation truyền `"abc"` hay `null`.
- `args(java.lang.String)` là điều kiện type-related trên argument. Trong ngữ nghĩa AspectJ, khi argument **là `null`**, khả năng match được xét theo **static declared argument type** tương thích với type pattern; không thể lấy runtime class từ chính `null`. Với method có declared parameter `String` thì có thể match cho `null`.
- Nếu method khai báo `accept(Object value)` và caller truyền `null`, **đừng suy** rằng `args(java.lang.String)` phải match như trường hợp declared parameter `String`: kiểu tĩnh là `Object` và `null` không cung cấp class runtime để chứng minh nó là `String`.

Một case dễ nhầm:

```java
Object item = null;
service.acceptObject(item); // method acceptObject(Object)
```

Không thể suy “item từng dùng để giữ String nên `args(String)` khớp”; đối với `null` thì thông tin kiểu object động không tồn tại.

**Nguyên tắc thực hành:** nếu mục tiêu của advice là xử lý **cả giá trị `null`**, hãy match method theo chữ ký rõ ràng bằng `execution(...)` hoặc phạm vi method thích hợp, sau đó dùng `JoinPoint.getArgs()` và kiểm tra `null` bên trong advice. Cách này ổn định, dễ kiểm chứng và không phụ thuộc một suy luận sai về runtime class.

### 3.3. `null` và typed binding

Ví dụ:

```java
@Before(
    value = "execution(* demo..*.accept(java.lang.String)) && args(value)",
    argNames = "value"
)
public void capture(String value) {
    System.out.println(value == null ? "NULL INPUT" : "INPUT " + value);
}
```

Đây là advice **minh họa**, chưa được thêm vào project ở phần 7. Việc một binding kiểu tham chiếu có thể mang `null` **không giống** việc được phép bind `null` vào primitive `int`. Nếu cần xử lý `null` một cách chủ động, đặt tham số advice là reference type thích hợp và kiểm tra trước khi dereference.

`null` **không thể truyền trực tiếp** làm giá trị cho formal parameter primitive `int`/`boolean` của method Java; nếu wrapper `Integer` được unbox từ `null`, lỗi `NullPointerException` có thể xảy ra trước lúc Spring proxy thực sự nhận lời gọi đó.

## 4. Kiểu nguyên thủy và boxing khi đi qua Spring AOP

### 4.1. Formal parameter là primitive nhưng `getArgs()` chứa wrapper

Xét:

```java
public int addOne(int number) {
    return number + 1;
}
```

Với client gọi `addOne(4)` qua proxy:

| Câu hỏi | Kết quả |
| --- | --- |
| `Method.getParameterTypes()[0]` | `int.class` |
| `method.getParameterTypes()[0].isPrimitive()` | `true` |
| `JoinPoint.getArgs()[0]` | Boxed `Integer` giữ giá trị 4 |
| `JoinPoint.getArgs()[0] instanceof Integer` | `true` |
| Có thể truyền `null` trực tiếp cho formal `int`? | Không |

Tại JVM reflective/AOP invocation boundary, `Object[]` không thể chứa một primitive như `int` trực tiếp. Vì vậy Java boxing/unboxing tham gia khi biểu diễn hoặc chuyển đối số.

### 4.2. `args` và autoboxing

Theo AspectJ pointcut semantics, boxing/unboxing được tính đến khi match các kiểu primitive/wrapper. Ví dụ với **một argument** kiểu `int`, `args(int)` và `args(java.lang.Integer)` có thể đều phù hợp; điểm này không đổi formal signature là `int`.

Phân biệt rõ:

```text
Method signature: addOne(int)
                        |
                Spring AOP invocation
                        |
              JoinPoint.getArgs()[0]
                        |
                        v
                  Integer(4)
```

Nếu muốn chỉ chọn **formal primitive int**, dùng:

```java
@Before("execution(* demo..*.addOne(int))")
public void beforePrimitiveAdd() {
    // match method signature addOne(int)
}
```

Nếu muốn bind giá trị:

```java
@Before(
    value = "execution(* demo..*.addOne(int)) && args(amount)",
    argNames = "amount"
)
public void beforeAdd(int amount) {
    System.out.println("amount=" + amount);
}
```

Đây là **hai snippets độc lập**, cần @Aspect bean và import. `argNames` là tên tham số advice, không liên quan tên formal parameter trên implementation (formal parameter có thể tên `number` nhưng binding vẫn tên `amount`).

### 4.3. Những lỗi primitive thường gặp

- **Ép `Object` sang primitive bằng cast sai:** dùng `((Integer) obj).intValue()` hoặc cast hợp lệ với unboxing, không `obj.getClass()==int.class`.
- **Thay argument bằng `null` khi target cần `int`:** `proceed(...)` có thể thất bại khi Spring/reflective invocation phải unbox `null`.
- **Cho advice return `null` với method trả primitive:** nếu `@Around` trả `null` thay `int`, Spring có thể ném lỗi vì không thể dùng `null` làm primitive return value.
- **Nghĩ `Integer` và `int` luôn giống nhau:** declared method signatures `calculate(int)` và `calculate(Integer)` là **hai overload khác nhau**, dù argument reflection đều có thể xuất hiện dưới dạng `Integer` khi khác `null`.

## 5. Overload: Spring advice intercept method nào?

Java chọn **overload** trước khi Spring AOP thực thi invocation trên proxy. Ví dụ:

```java
public String render(String text) { return "S:" + text; }
public String render(Integer number) { return "I:" + number; }
```

Với lời gọi:

```java
service.render("hello");         // render(String)
service.render(Integer.valueOf(3)); // render(Integer)
service.render((String) null);   // render(String)
service.render((Integer) null);  // render(Integer)
```

Lời gọi không cast:

```java
service.render(null);
```

là **lỗi biên dịch do ambiguous overload** trong ví dụ hai kiểu không có quan hệ subtype `String` và `Integer`. Spring AOP không “giải quyết” lại overload ở runtime; program phải biên dịch trước đã.

### 5.1. Vì sao `getArgs()` chưa đủ để nhận diện overload?

Hai lời gọi:

```java
service.render((String) null);
service.render((Integer) null);
```

đều có `JoinPoint.getArgs()` biểu diễn thành:

```text
[null]
```

Nhưng method chữ ký khác nhau. Muốn biết chính xác method:

```java
MethodSignature sig = (MethodSignature) jp.getSignature();
Class<?>[] declaredTypes = sig.getMethod().getParameterTypes();
```

- Với overload đầu: `declaredTypes[0] == String.class`.
- Với overload thứ hai: `declaredTypes[0] == Integer.class`.

Mã đó là snippet cần import `org.aspectj.lang.reflect.MethodSignature`, `java.lang.reflect.Method` nếu dùng `Method` trực tiếp.

### 5.2. Pointcut theo overload cụ thể

```java
@Before("execution(* demo..*.render(java.lang.String))")
public void beforeRenderText() {
    System.out.println("TEXT");
}

@Before("execution(* demo..*.render(java.lang.Integer))")
public void beforeRenderNumber() {
    System.out.println("NUMBER");
}
```

Đây là **hai advice minh họa**. Spring AOP dùng static signature để phân biệt hai overloaded methods, kể cả khi client truyền `null` đã cast.

Đừng dùng riêng `args(java.lang.String)` nếu bạn cần ràng buộc **tuyệt đối** vào overload có formal parameter `String`: khi tham số khai báo là `Object` nhưng runtime value là `String`, điều kiện `args(String)` còn có thể match lời gọi `render(Object)`. Kết hợp `execution(...)` và `args(...)` khi cần cả static method selection lẫn runtime value condition.

### 5.3. `int` vs `Integer` overload

Nếu class có:

```java
void total(int quantity) { }
void total(Integer quantity) { }
```

thì Java có hai overload hợp lệ. Khi gọi `total(5)`, compiler thường chọn formal primitive `int` phù hợp trực tiếp; `total(Integer.valueOf(5))` chọn reference `Integer`. Đối với `total(null)`, formal `int` không thể nhận null, nên overload wrapper `Integer` có thể được chọn.

Tại `getArgs()`, lời gọi thành công ở cả hai overload có thể thấy boxed `Integer`, nên **phải dùng method signature nếu muốn phân biệt formal `int` và `Integer`**.

## 6. Varargs: `T...` chính là một formal array parameter

### 6.1. Method được biên dịch ra chữ ký nào?

Java:

```java
public String tags(String label, String... names) {
    return label + ":" + names.length;
}
```

Về tham số phản chiếu (reflection), chữ ký tương đương:

```java
tags(String label, String[] names)
```

cộng thuộc tính **isVarArgs** trên method. Khi client gọi:

```java
service.tags("many", "a", "b");
```

Java compiler gom các varargs vào array cuối cùng trước khi invocation. Ở Spring AOP:

- `MethodSignature.getMethod().getParameterCount()` là **2**, không phải 3.
- `JoinPoint.getArgs().length` thông thường là **2**.
- `getArgs()[0]` là String `"many"`.
- `getArgs()[1]` là `String[] {"a","b"}`, **không** phải hai arguments top-level `"a"`, `"b"`.
- `((MethodSignature) jp.getSignature()).getMethod().isVarArgs()` trả `true`.

Nếu định bind phần varargs, hãy nghĩ tới **một array argument** `String[]`, không phải vô hạn Java advice parameters.

### 6.2. Ba trạng thái varargs cần phân biệt

| Cách gọi Java | Formal argument thứ hai trong method |
| --- | --- |
| `tags("zero")` | `new String[0]`, tức array rỗng |
| `tags("two","a","b")` | `String[] {"a","b"}` |
| `tags("nil",(String[]) null)` | **null array**, không phải mảng rỗng |
| `tags("oneNull",(String) null)` | `String[] {null}`, tức mảng có một phần tử null |

`tags("nil", (String[]) null)` là lời gọi đặc biệt cần cast rõ ràng. Đối với:

```java
tags("ambiguous", null);
```

Java có thể cảnh báo **non-varargs call for last parameter**, vì `null` có thể là array argument; đừng để người đọc đoán rằng null này có nghĩa là một phần tử trong array. Muốn một null element, dùng `(String)null` hoặc `new String[] { null }`.

Khi code advice cần in array đẹp, dùng:

```java
System.out.println(Arrays.deepToString(jp.getArgs()));
```

với import `java.util.Arrays`. `Arrays.toString(jp.getArgs())` có thể in identity string của nested `String[]`; `deepToString` hiển thị nội dung nested arrays một cách đọc được.

### 6.3. Pointcut `execution(..)` với varargs

Để chọn đúng method `tags(String, String...)` theo Java reflection parameter types, cần biết tham số cuối là `String[]`. Tuy nhiên cú pháp AspectJ pattern cho varargs có thể cần lưu ý cú pháp dấu ba chấm. Nếu không phải cố ý lọc chữ ký array, cách rõ ràng và ít lỗi:

```java
@Before("execution(* demo..*.tags(..))")
public void beforeTags(JoinPoint jp) {
    Object[] args = jp.getArgs();
    // Kiểm tra signature, array ở vị trí cuối nếu cần.
}
```

Đây là snippet. `(..)` match bất kỳ số formal parameters của method có tên `tags`; kiểm tra array thật bằng `MethodSignature` giúp tránh nhầm varargs ở call site với số lượng formal parameters.

**Không dùng** `args(label, first, second)` chỉ vì Java source call có hai vararg elements; đó không phải ba formal invocation arguments trong cơ chế proxy.

## 7. Project Spring AOP runnable: bốn loại tình huống trong một service

Ví dụ đầy đủ sử dụng **Java 17+, Maven, Spring Framework 6.2.6**. Nó có:

- Một Spring-managed bean `ArgumentService`, target implementation `ArgumentServiceImpl`.
- Một `@Aspect` dùng một `@Around` advice chỉ để **ghi lại method signature và argument values** rồi gọi `proceed()` đúng một lần, không thay đổi dữ liệu.
- Những lời gọi `null`, primitive `int`, hai overload nhận `String`/`Integer`, varargs rỗng/nhiều phần tử/null array/array chứa null.
- Output độc lập với tên generated proxy class: aspect in **declared parameter simple names** từ `MethodSignature` và argument values qua `Arrays.deepToString`.

Cách tổ chức một `@Around` này giúp trace không phụ thuộc thứ tự của nhiều advices cùng match một join point. Mã chạy bằng Spring AOP proxy của framework, **không** compile-time weaving.

### 7.1. Cấu trúc thư mục

```text
spring-aop-argument-cases/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ArgumentCasesDemo.java
```

### 7.2. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-argument-cases</artifactId>
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
                    <mainClass>demo.ArgumentCasesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.3. Tệp `src/main/java/demo/ArgumentCasesDemo.java`

```java
package demo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class ArgumentCasesDemo {

    public interface ArgumentService {
        String accept(String value);
        int addOne(int number);
        String render(String value);
        String render(Integer value);
        String tags(String label, String... values);
    }

    public static class ArgumentServiceImpl implements ArgumentService {

        @Override
        public String accept(String value) {
            System.out.println("TARGET accept=" + value);
            return value == null ? "NULL" : "TEXT:" + value;
        }

        @Override
        public int addOne(int number) {
            System.out.println("TARGET addOne=" + number);
            return number + 1;
        }

        @Override
        public String render(String value) {
            System.out.println("TARGET render(String)=" + value);
            return "S:" + value;
        }

        @Override
        public String render(Integer value) {
            System.out.println("TARGET render(Integer)=" + value);
            return "I:" + value;
        }

        @Override
        public String tags(String label, String... values) {
            String view = values == null ? "null" : Arrays.toString(values);
            System.out.println(
                "TARGET tags label=" + label + " values=" + view
            );
            return label + ":" + (values == null ? "NULL" : values.length);
        }
    }

    @Aspect
    public static class TraceAspect {

        @Around(
            "execution(* demo..*.accept(..)) || " +
            "execution(* demo..*.addOne(..)) || " +
            "execution(* demo..*.render(..)) || " +
            "execution(* demo..*.tags(..))"
        )
        public Object trace(ProceedingJoinPoint pjp) throws Throwable {
            Method method =
                ((MethodSignature) pjp.getSignature()).getMethod();

            String declaredTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));

            System.out.println(
                "AOP " + method.getName()
                    + "(" + declaredTypes + ")"
                    + " varargs=" + method.isVarArgs()
                    + " args=" + Arrays.deepToString(pjp.getArgs())
            );

            return pjp.proceed();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {
        @Bean
        public ArgumentService argumentService() {
            return new ArgumentServiceImpl();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) {
        try (var ctx =
                 new AnnotationConfigApplicationContext(Config.class)) {

            ArgumentService service = ctx.getBean(ArgumentService.class);
            System.out.println("AOP_PROXY=" + AopUtils.isAopProxy(service));

            System.out.println("CLIENT=" + service.accept(null));
            System.out.println("CLIENT=" + service.addOne(4));
            System.out.println("CLIENT=" + service.render((String) null));
            System.out.println("CLIENT=" + service.render((Integer) null));
            System.out.println("CLIENT=" + service.tags("zero"));
            System.out.println("CLIENT=" + service.tags("many", "x", "y"));
            System.out.println(
                "CLIENT=" + service.tags("nil", (String[]) null)
            );
            System.out.println(
                "CLIENT=" + service.tags("oneNull", (String) null)
            );
        }
    }
}
```

Vì `ArgumentService` là interface, cấu hình Spring Framework dùng `proxyTargetClass = false` có thể tạo **JDK dynamic proxy**. `TraceAspect` được đăng ký qua `@Bean` nên Spring đọc `@Aspect` và tạo advisor áp dụng cho các method trong biểu thức.

### 7.4. Chạy Maven

Từ thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

**Những dòng stdout do chính chương trình in, theo logic và chữ ký trong ví dụ** (không tính logging riêng của Maven/Spring):

```text
AOP_PROXY=true
AOP accept(String) varargs=false args=[null]
TARGET accept=null
CLIENT=NULL
AOP addOne(int) varargs=false args=[4]
TARGET addOne=4
CLIENT=5
AOP render(String) varargs=false args=[null]
TARGET render(String)=null
CLIENT=S:null
AOP render(Integer) varargs=false args=[null]
TARGET render(Integer)=null
CLIENT=I:null
AOP tags(String,String[]) varargs=true args=[zero, []]
TARGET tags label=zero values=[]
CLIENT=zero:0
AOP tags(String,String[]) varargs=true args=[many, [x, y]]
TARGET tags label=many values=[x, y]
CLIENT=many:2
AOP tags(String,String[]) varargs=true args=[nil, null]
TARGET tags label=nil values=null
CLIENT=nil:NULL
AOP tags(String,String[]) varargs=true args=[oneNull, [null]]
TARGET tags label=oneNull values=[null]
CLIENT=oneNull:1
```

Đây là **stdout dự kiến từ mã được trình bày**, không phải tuyên bố đã chạy Maven trong môi trường cụ thể.

### 7.5. Phân tích output theo từng chủ đề

**Trường hợp `accept(null)`:** advice nhìn thấy `args=[null]` nhưng vẫn xác định formal parameter là `String` thông qua `MethodSignature`. Target trả `NULL` mà không gây `NullPointerException`.

**Trường hợp `addOne(4)`:** signature hiển thị `addOne(int)`, trong khi `getArgs()` biểu diễn giá trị primitive 4 dưới dạng phần tử của `Object[]` và in `[4]`.

**Hai overload `render(...)`:** cả hai có `args=[null]`. Chỉ dựa vào giá trị runtime trong mảng không thể biết caller chọn overload nào; signature cho thấy `render(String)` hoặc `render(Integer)`.

**Varargs rỗng:** `tags("zero")` vẫn có **2 formal arguments**, trong đó array cuối có length 0. Vì vậy output `[zero, []]`.

**Varargs nhiều phần tử:** `tags("many","x","y")` là một label và một array cuối; `Arrays.deepToString` in `[many, [x, y]]`, không phải ba phần tử top-level.

**Null array:** `tags("nil",(String[])null)` chuyển array cuối là `null`; output `[nil, null]` và target được viết để không dereference `null`.

**Một phần tử null:** `tags("oneNull",(String)null)` tạo một array độ dài 1 có phần tử `null`; output `[oneNull, [null]]`, khác rõ với null array.

## 8. Ghép `execution(...)` và `args(...)` cho đúng mục đích

Bốn biểu thức minh họa sau **không phải advice được đăng ký trong project ở mục 7**:

```java
// 1. Formal method signature có một tham số String
@Before("execution(* demo..*.accept(java.lang.String))")
public void declaredString() { }

// 2. Runtime argument được bind theo type String
@Before(
    value = "execution(* demo..*.accept(..)) && args(value)",
    argNames = "value"
)
public void boundString(String value) { }

// 3. Formal overload render(Integer)
@Before("execution(* demo..*.render(java.lang.Integer))")
public void integerOverload() { }

// 4. Formal primitive int
@Before("execution(* demo..*.addOne(int))")
public void primitiveOverload() { }
```

Với **overload**, ưu tiên dùng `execution(...FormalType...)` để chọn chữ ký. Với **runtime condition hoặc muốn lấy giá trị cụ thể**, dùng `args(...)` (binding) hoặc `JoinPoint.getArgs()` khi muốn inspect rộng.

### `args(..)` không tự phân biệt mọi overload

Đặc biệt:

- Nếu overload có `render(String)` và `render(Object)`, một invocation `render(Object)` nhận runtime `String` có thể thỏa điều kiện `args(String)`, dù declared signature không phải `render(String)`.
- Nếu argument là `null`, kết quả typed matching chịu ảnh hưởng của declared parameter type; đừng coi `args(String)` là cách thử `arg instanceof String` trong mọi tình huống.
- Nếu method có primitive `int`, argument quan sát trong `Object[]` là boxed wrapper. Không suy ra formal signature từ mỗi `Object#getClass()`.
- Nếu method có varargs, phần cuối của `args` thường tương ứng với **một array argument** trong runtime invocation, không phải một biến binding riêng cho từng vararg element.

Một chiến lược an toàn cho bài toán logging tổng quát là **pointcut tương đối tĩnh** cộng với code xử lý các `Object[]` và signature có kiểm tra null/array.

## 9. Thay thế argument bằng `ProceedingJoinPoint.proceed(Object[])`

Trong `@Around`, Spring cho phép tiếp tục chain với **mảng arguments thay thế**:

```java
@Around("execution(* demo..*.accept(java.lang.String))")
public Object trimInput(ProceedingJoinPoint pjp) throws Throwable {
    Object[] next = pjp.getArgs().clone();

    String oldValue = (String) next[0]; // Có thể null
    next[0] = oldValue == null ? "" : oldValue.trim();

    return pjp.proceed(next);
}
```

Đây là **snippet**, chưa được đăng ký trong project mục 7. Điều quan trọng:

1. `proceed()` giữ nguyên arguments hiện tại.
2. `proceed(next)` chuyển các values mới tới phần tiếp theo của Spring AOP invocation, phù hợp khi cần chuẩn hóa input.
3. Số phần tử `next` phải khớp **formal parameter count** của method được intercept. Không nhầm số phần tử trong varargs array với số formal params.
4. Giá trị thay thế phải **assignable** tới formal parameter type; primitive không nhận `null`.
5. Nếu method overload, `proceed(newArgs)` **không gọi overload khác**. Nó tiếp tục *method invocation đã chọn* với các values mới, không gọi lại Java overload resolution.
6. Thay arguments có thể ảnh hưởng logic nghiệp vụ, cache key, validation và các advice khác tùy thứ tự interceptor; chỉ sửa khi có chủ đích và test.

### 9.1. Khi muốn sửa varargs

Giả sử đang advice method:

```java
String tags(String label, String... values);
```

Về formal params, có **hai vị trí**: `String` và `String[]`. Muốn thay array cuối bằng hai phần tử:

```java
Object[] next = pjp.getArgs().clone();
next[1] = new String[] {"normalized", "checked"};
return pjp.proceed(next);
```

Nếu chuyển nhầm:

```java
return pjp.proceed(new Object[] {
    "label", "normalized", "checked"
});
```

thì `Object[]` thay thế có ba phần tử, trong khi formal method chỉ có **hai parameters**; đây không phải cách an toàn để truyền varargs cho `proceed(Object[])`.

### 9.2. Kiểu `int` và `Integer` khi thay args

Với method `addOne(int)`:

```java
Object[] args = pjp.getArgs().clone();
args[0] = Integer.valueOf(10); // Hợp lệ để unbox thành int
return pjp.proceed(args);
```

Không dùng `args[0] = null`: không thể unbox null thành primitive `int`. Cũng không đưa một `Long` tùy ý thay cho `Integer` rồi giả định Spring luôn tự chuyển đổi mọi numeric type theo ý bạn.

### 9.3. Spring AOP và native AspectJ có khác biệt về binding

Tài liệu Spring giải thích cách viết `proceed(Object[])` nhất quán khi dùng fully-typed `@Around` advice và bind các method arguments theo thứ tự tương ứng. Khi chuyển logic từ Spring proxy mode sang AspectJ weaving mode, **không mặc định** mọi cách đẩy `Object[]` trong các snippets đều giữ nguyên semantics; tuân theo hợp đồng advice arguments/binding của mode đó.

## 10. Các tình huống bị hiểu nhầm với `@args`

Trong Spring AOP pointcuts, `args(Type)` và `@args(Annotation)` **khác hoàn toàn**:

- `args(Type)` xem kiểu/đối số runtime, có thể dùng binding form.
- `@args(Annotation)` dùng **annotation trên runtime class của argument object** để match theo cơ chế annotation designator.

Ví dụ:

```java
@Pointcut("@args(com.example.app.Tracked)")
public void trackedRuntimeArgument() {
}
```

Đây là **snippet minh họa**, cần annotation/class thật. Với argument `null` thì **không có runtime object class** để kiểm tra annotation. Vì thế không thể dùng `@args(Tracked)` như một cách tổng quát nhận diện annotation nằm trên type formal parameter khi giá trị null.

Nếu muốn điều kiện về **annotation trên khai báo parameter** hoặc trên method, cần chọn công cụ phù hợp (reflection metadata, annotation resolver, `@annotation` đối với method annotation) thay vì đánh đồng với `@args`.

## 11. Proxy boundary, JDK/CGLIB và method eligibility

### 11.1. Cùng biểu thức nhưng method không qua proxy

Một Spring aspect có pointcut khớp `addOne(int)`, nhưng nếu caller gọi:

```java
new ArgumentServiceImpl().addOne(4);
```

thì object này không phải Spring AOP proxy; advice không tự chạy.

Tương tự, khi target thực thi:

```java
public void outer() {
    this.inner("x");
}
```

lời gọi `this.inner(...)` bên trong target không quay lại proxy. Dù method `inner(String)` có `args(String)` và `execution(...)` phù hợp, Spring AOP sẽ không xử lý một join point mới ở ranh giới proxy cho lời gọi nội bộ đó.

### 11.2. Proxy strategy ảnh hưởng nơi có thể intercept

- JDK dynamic proxy công bố các interface methods. Trong project runnable, cả hai overload `render(String)` và `render(Integer)` đều nằm trên interface nên client gọi chúng được qua JDK proxy.
- CGLIB subclass proxy có thể intercept method overrideable trên implementation; `final/private` method không được advice nhờ CGLIB override.
- Chuyển từ JDK sang CGLIB **không tự làm null/varargs khác bản chất Java**: `String...` vẫn là array trong signature, primitive vẫn có boxing khi đi qua `Object[]`.
- Không cần ép CGLIB để phân biệt overload nếu interface đã công bố các overload.

### 11.3. Method reflection có thể là method interface

Với JDK proxy, `MethodSignature.getMethod()` thường phản ánh interface method được gọi. Nó vẫn chứa chữ ký formal parameters để phân biệt overload và varargs. Nếu cần tìm declaration cụ thể trên implementation để đọc method annotations, Spring cung cấp tiện ích như `AopUtils.getMostSpecificMethod(interfaceMethod, targetClass)`. Nhưng với bài toán **số argument, kiểu formal và isVarArgs**, chữ ký interface đã đủ trong demo.

## 12. Bảng quy tắc chọn công cụ

| Nhu cầu | Cách ưu tiên | Lý do |
| --- | --- | --- |
| Ghi lại mọi giá trị arguments, bao gồm null/array | `JoinPoint.getArgs()` + kiểm tra an toàn | Không ràng buộc bắt buộc một typed parameter |
| Phân biệt hai overload cùng tên | `MethodSignature.getMethod().getParameterTypes()` hoặc `execution(...Type...)` | Giá trị `null` có thể làm mảng runtime giống nhau |
| Bind tham số đầu kiểu cụ thể | `args(name,..)` + Java advice param + `argNames` | Typed binding rõ ràng |
| Chỉ chọn method formal `int` | `execution(* *.method(int))` | Tránh nhầm kiểu boxed value với declared signature |
| Biết method có varargs hay không | `Method.isVarArgs()` | Cờ metadata đúng thay vì đếm call-site arguments |
| Đếm varargs elements | Kiểm tra/cast formal array cuối, rồi `Array.getLength` hoặc `values.length` | Varargs được truyền như một array formal param |
| Muốn sửa input trước target | `@Around` và `proceed(Object[])` | Có quyền điều phối chain |
| Muốn chọn theo runtime object annotations | `@args(...)`, xét giới hạn null | Không phải cùng nghĩa với `args(...)` |
| Chẩn đoán không có advice | Kiểm tra proxy, expression và đường gọi | Spring AOP chỉ chặn lời gọi đi qua proxy |

## 13. Checklist gỡ lỗi

1. **Client có lấy đúng Spring bean từ context không?** Không dùng `new` để kiểm tra advice.
2. **Bean có proxy chưa?** Dùng `AopUtils.isAopProxy(bean)` trong test.
3. **Method actual signature là gì?** In `MethodSignature.getMethod()` và `getParameterTypes()`.
4. **Pointcut đang match static signature hay runtime arg type?** Phân biệt `execution` và `args`.
5. **Có argument `null` không?** Tránh dereference; kiểm tra declared type, không đoán runtime class.
6. **Formal parameter primitive hay wrapper?** `int` và `Integer` là overload khác nhau, nhưng `getArgs()` hiển thị boxed value.
7. **Method overload có ambiguous call không?** Java compile-time resolution có thể yêu cầu explicit cast với `null`.
8. **Varargs có phải array cuối không?** Phân biệt array rỗng, null array và array chứa phần tử null.
9. **Có `@Around` nào sửa arguments không?** `proceed(Object[])` phải đúng số formal params và kiểu được chấp nhận.
10. **Có `args(...)` binding name mismatch không?** Kiểm tra advice parameter names, `argNames` và kiểu binding.
11. **Method có bị short-circuit bởi advice khác không?** Cache/around advice có thể không gọi tiếp target.
12. **Có self-invocation, final/private hoặc khác biệt JDK/CGLIB không?** Pointcut khớp không có nghĩa mọi lời gọi đều được Spring proxy chặn.
13. **Logging có gây side effects không?** Đừng gọi `toString()` mù trên input lớn hoặc nhạy cảm; với arrays dùng cách in phù hợp và cẩn thận bảo mật.

## 14. Bài tập tự kiểm tra

**Câu 1.** `String accept(String value)` được gọi bằng `null` qua proxy. `getArgs()[0]` và formal parameter type là gì?  
**Đáp án:** Value là `null`; formal type vẫn `String.class`.

**Câu 2.** `int addOne(int number)` được gọi với 4. `getArgs()[0].getClass()` thường trả class nào?  
**Đáp án:** `Integer.class` do boxing; formal type vẫn `int.class`.

**Câu 3.** Có `render(String)` và `render(Integer)`. Gọi `render(null)` có chạy đến Spring aspect không?  
**Đáp án:** Không nếu Java compile báo ambiguous overload; cần cast `(String)null` hoặc `(Integer)null` để chọn method.

**Câu 4.** Với `render((String)null)` và `render((Integer)null)`, mảng `getArgs()` có đủ phân biệt overload không?  
**Đáp án:** Không; cả hai có thể là `[null]`. Dùng `MethodSignature` hoặc match `execution(...FormalType...)`.

**Câu 5.** `tags("x","a","b")` khai báo `tags(String, String...)`. `getArgs().length` thông thường bằng mấy?  
**Đáp án:** 2, vì formal varargs parameter là một `String[]`.

**Câu 6.** Khác biệt giữa `tags("x")` và `tags("x",(String[])null)`?  
**Đáp án:** Lần đầu có array rỗng; lần sau có null array. Code target cần xử lý khác nhau nếu muốn.

**Câu 7.** `tags("x",(String)null)` khác null array như thế nào?  
**Đáp án:** Nó tạo array một phần tử `null`; độ dài array là 1, không phải `null`.

**Câu 8.** `args(String)` có ý nghĩa chính xác như `execution(* *(String))` không?  
**Đáp án:** Không. `execution` chọn formal method signature, `args` xét argument/type at invocation; `null` và runtime subtype khiến khác biệt thể hiện rõ.

**Câu 9.** Dùng `@Around` đã intercept `render(String)`, rồi gọi `proceed(new Object[]{Integer.valueOf(5)})` có chuyển sang overload `render(Integer)`?  
**Đáp án:** Không. Spring tiếp tục invocation của method đã chọn; argument mới không buộc Java chọn overload lại.

**Câu 10.** `proceed(new Object[]{"label","a","b"})` có thích hợp để truyền ba vararg elements cho method `tags(String, String...)` không?  
**Đáp án:** Không. Formal signature có hai tham số, nên cần array cuối như `new Object[]{"label", new String[]{"a","b"}}`.

**Câu 11.** Nếu `@Before` match method nhưng target gọi nó bằng `this.method()` nội bộ, advice có chắc chạy không?  
**Đáp án:** Không, vì self-invocation trên target bỏ qua Spring proxy.

**Câu 12.** Vì sao không nên log `value.getClass()` cho mọi phần tử `getArgs()`?  
**Đáp án:** Vì có thể chứa null; ngoài ra log raw values có thể lộ bí mật và varargs arrays cần trình bày riêng.

## 15. Tài liệu chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `JoinPoint.getArgs()`, `args(name)` binding, advice parameter names, `proceed(Object[])`.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `execution(...)` versus `args(...)`, static method signatures và runtime argument matching.
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): method matching tĩnh/động trong Spring proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): giới hạn JDK/CGLIB, self-invocation.
- [AspectJ Programming Guide — Pointcut Semantics](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): semantics của `args` và trường hợp `null`.
- [AspectJ 5 — Autoboxing and Unboxing](https://eclipse.dev/aspectj/doc/latest/adk15notebook/autoboxing.html): match primitive và wrapper với `args`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-009">Giới hạn suy luận khi tham số có generic và xóa kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Giới hạn suy luận khi tham số có generic và xóa kiểu

## 1. Vấn đề trong Spring Aspect: `List<String>` có thật sự được nhận diện?

Một Spring aspect thường cần quan sát hoặc ràng buộc đối số method thông qua `JoinPoint.getArgs()`, `args(...)`, `MethodSignature` hoặc tên tham số của advice. Khi target method khai báo kiểu có generic như `List<String>`, `Collection<Order>`, `Optional<User>` hoặc `Repository<T>`, phải phân biệt:

1. **Kiểu khai báo trong source/class metadata**, chẳng hạn `List<String>`.
2. **Kiểu runtime của object được truyền vào**, chẳng hạn một instance của `List`.
3. **Kiểu runtime của mỗi phần tử**, chẳng hạn từng object `String`.
4. **Khả năng match/bind của Spring AOP pointcut**, vốn không tương đương một bộ kiểm tra đầy đủ mọi type parameter tại runtime.

Java thực hiện **generic type erasure**: đa số type arguments không nằm trong runtime class của object `List`. Spring có thể đọc **generic signature metadata** của một method bằng reflection nếu metadata tồn tại, nhưng điều đó không chứng minh nội dung collection thực sự phù hợp.

Đây là giới hạn thực hành quan trọng khi dùng **Spring Aspect**, không phải bài học tổng quát về generics. Ta tập trung vào những câu hỏi: advice match method nào, bind được giá trị gì, reflection biết được đến đâu, khi nào phải tự kiểm tra phần tử, và tác động của JDK/CGLIB proxies cùng bridge methods.

### Ví dụ phân biệt ba lớp thông tin

```java
public interface MessagePort {
    void deliver(List<String> names);
}
```

Khi consumer gọi `port.deliver(List.of("An", "Bình"))` trên Spring-managed proxy:

- Java compile-time kiểm tra `List<String>` theo method contract.
- `JoinPoint.getArgs()[0]` nhận một **List object**. `getClass()` cho biết implementation class của List, không cho ra `List<String>`.
- Nếu declaration thực sự còn generic signature, reflection có thể đọc rằng **tham số của method** được khai báo `List<String>`.
- Runtime argument vẫn có thể bị tạo qua raw type, unchecked cast, reflection, deserialization hoặc code không an toàn. Method declaration không bảo đảm mọi element đúng là String.
- Spring AOP không tự duyệt tất cả List elements mỗi invocation để suy ra `T`, vì không thể xây quy tắc tổng quát đáng tin cho `null`, collection rỗng, wildcard hoặc mutable list.

**Kết luận mở đầu:** *Generic declaration metadata khác với generic runtime verification; `JoinPoint` và pointcut binding không thay thế Java type-safety của application boundary.*

## 2. Spring AOP quan sát lời gọi ở ranh giới proxy

```text
Consumer
   |
   | port.deliver(List<String>)
   v
JDK proxy (interface) hoặc CGLIB proxy (class)
   |
   | pointcut match method-execution
   v
@Before / @Around advice
   |
   +-- jp.getArgs()[0]             -> List object (runtime value)
   +-- jp.getSignature()           -> method signature
   +-- Method.getGeneric...        -> declared metadata nếu có
   +-- args(values)                -> bind runtime object
   |
   v
Target.deliver(List<String>)
```

Spring AOP dựa trên proxy, chỉ hỗ trợ **method-execution join points** của Spring-managed beans. Không phải mọi method call trong JVM đều tạo `JoinPoint` cho advice.

Khi target tự gọi `this.deliver(...)`, lời gọi nội bộ **bypass proxy**, nên advice không tự thấy invocation đó. Tương tự, object được tạo bằng `new` bên ngoài Spring IoC không tự có AOP proxy.

### Cách gọi method không thay đổi erasure

Chuyển từ JDK proxy sang CGLIB không “khôi phục” generic arguments bên trong runtime List; cả hai vẫn dựa trên Java runtime. Điểm khác là method phản chiếu từ interface hoặc implementation có thể khác nhau, khiến ta phải **resolve concrete method** trước khi đọc generic metadata.

## 3. Khi Spring bind được tham số có kiểu cụ thể

Tài liệu chính thức Spring Framework, mục **Advice Parameters and Generics**, giải thích Spring AOP hỗ trợ các trường hợp generic ở class declaration và method parameter. Ví dụ:

```java
public interface Sample<T> {
    void sampleGenericMethod(T value);
    void sampleGenericCollectionMethod(Collection<T> values);
}
```

Nếu caller truyền một object thuộc class `Invoice` vào `sampleGenericMethod(T)`, advice có thể bind kiểu cụ thể nhờ runtime type của argument, tùy pointcut và giới hạn phương thức:

```java
@Before(
    value = "execution(* demo.generic..*.sampleGenericMethod(..))"
          + " && args(invoice)",
    argNames = "invoice"
)
public void beforeInvoice(Invoice invoice) {
    System.out.println("invoice id=" + invoice.id());
}
```

Đây là **fragment**; `Invoice` phải là type thực có trong dự án, aspect cần Spring quản lý và method cần đi qua proxy. `args(invoice)` giúp bind actual argument và giới hạn kiểu phù hợp. Spring có thể kiểm tra runtime object có phải `Invoice`, khác hẳn câu hỏi “một List đang chứa `Invoice` hay không”.

Với một đối số dạng class có generic `T` nhưng method thực tế nhận subclass cụ thể, Spring/AspectJ pointcut matching có thể dùng **runtime type**, declared method signature và các quy tắc khác. Không suy từ một ví dụ đơn giản rằng mọi trường hợp generic hierarchy, overload, `null`, interface và bridge methods đều được match giống nhau.

## 4. Giới hạn quan trọng: `Collection<Invoice>` trong advice

Điểm Spring nêu rõ: bạn **không thể chỉ dùng** một advice parameter kiểu `Collection<Invoice>` để làm pointcut phân biệt chính xác contents của một generic collection:

```java
// KHÔNG dùng như phương pháp xác minh List/Collection<Invoice> runtime.
@Before(
    value = "execution(* demo.generic..*.sampleGenericCollectionMethod(..))"
          + " && args(values)",
    argNames = "values"
)
public void beforeInvoices(Collection<Invoice> values) {
    // Kiểu khai báo này KHÔNG có nghĩa Spring đã kiểm tra từng element.
}
```

Đây là snippet **phản ví dụ**, không phải code khuyến nghị. Lý do không chỉ nằm ở “Spring không đủ thông minh”: object `Collection` tại runtime thông thường không mã hóa `Invoice` làm type argument, và Spring không duyệt tất cả phần tử để kết luận generic match.

Cách nên dùng khi cần inspect actual contents:

```java
@Before(
    value = "execution(* demo.generic..*.sampleGenericCollectionMethod(..))"
          + " && args(values)",
    argNames = "values"
)
public void inspect(Collection<?> values) {
    boolean allInvoices = values.stream()
            .allMatch(Invoice.class::isInstance);
    System.out.println("all invoices=" + allInvoices);
}
```

Đây là **fragment** giả định `Invoice` có thật. `Collection<?>` nói rõ rằng advice chưa biết element type, rồi ta kiểm tra từng phần tử theo policy cụ thể. Tuy nhiên:

- `allMatch(...)` trên **collection rỗng** trả `true` theo quy tắc toán học (vacuous truth): *không có phần tử sai* không đồng nghĩa *đã chứng minh kiểu Invoice*.
- Nếu collection chứa `null`, `Invoice.class.isInstance(null)` là `false`; bạn cần quyết định chính sách null.
- Nếu collection mutable và bị thay đổi đồng thời, một lần kiểm tra không bảo đảm nó mãi hợp lệ.
- Nếu chỉ muốn logging, không nhất thiết cần duyệt mọi element vì chi phí, độ nhạy cảm dữ liệu và side effects.
- Nếu validation ảnh hưởng bảo mật/nghiệp vụ, chọn boundary đúng, kiểm tra có chủ đích và cân nhắc Spring Bean Validation / contract validation phù hợp; không coi aspect tự bảo đảm toàn bộ hệ thống.

**Trọng tâm:** binding `Collection<?>` đủ để advice đọc object một cách an toàn về mặt generic, còn việc xác minh element type là một bước riêng tùy mục tiêu.

## 5. `args(...)` khác với `execution(...)` về cách match

Spring AOP hỗ trợ hai nhóm thông tin thường bị trộn lẫn:

- `execution(...)` xét **method signature/execution** phù hợp với pattern.
- `args(...)` xét **runtime arguments** phù hợp với type/argument binding.

### 5.1. Không viết generic type argument như một runtime type trong pointcut

Không giả định có thể viết:

```text
execution(* demo.generic.CatalogService.store(java.util.List<java.lang.String>))
```

để Spring AOP thực hiện element-type matching cho `List<String>`. Pointcut execution trong proxy-based Spring AOP không phải một ngôn ngữ truy vấn nội dung generics. Việc Spring có thể **đọc** generic signature từ method declaration là khả năng khác với **match tất cả elements**.

Khi mục tiêu chỉ là method có tên `storeNames`, dùng method pattern:

```java
@Before("execution(* demo.generic.CatalogService.storeNames(..))")
public void beforeNames(JoinPoint jp) {
    Object first = jp.getArgs()[0];
    System.out.println(first instanceof List<?>);
}
```

Snippet trên không tự chứng minh `List<String>` hợp lệ; nó chỉ quan sát một argument thuộc List.

### 5.2. `args(value)` và `args(values)`

Với argument scalar, `args(value)` gắn actual value vào typed advice parameter. Với `List<?>`, `args(values)` bind **List object** chứ không bind từng element. Bạn phải viết rõ logic kiểm tra nếu muốn biết element types.

Hãy cân nhắc `null`: một runtime argument null không có concrete class để `getClass()` kiểm tra. Điều kiện static signature và cơ chế type binding có thể ảnh hưởng match; không suy từ `null` rằng generic type argument được biết. Nếu pointcut hoặc advice phải hoạt động cả khi null, kiểm tra bằng integration test cho đúng overload/config.

### 5.3. Overload theo generic element type không hợp lệ trong Java

Bạn không thể khai báo cùng class hai overload chỉ khác type argument:

```java
// PHẢN VÍ DỤ — hai method có cùng erasure, không biên dịch.
public void store(List<String> values) {}
public void store(List<Integer> values) {}
```

Sau erasure, cả hai có dạng `store(List)`. Java không phân biệt method overload bằng `String` hay `Integer` ẩn trong List. Vì thế đừng thiết kế pointcut để phân biệt hai overload vốn không tồn tại ở Java bytecode.

Có thể dùng hai tên method khác nhau như `storeNames(List<String>)` và `storeNumbers(List<Integer>)`, hoặc dùng wrapper types riêng nếu thực sự cần API khác nhau.

## 6. Reflection: `getParameterTypes()` và `getGenericParameterTypes()`

Khi lời gọi Spring AOP đến một method, advice có thể dùng:

```java
MethodSignature signature = (MethodSignature) jp.getSignature();
Method method = signature.getMethod();

Class<?>[] erasedTypes = method.getParameterTypes();
Type[] declaredTypes = method.getGenericParameterTypes();
```

Đây là **fragment** trong advice; imports gồm `org.aspectj.lang.reflect.MethodSignature`, `java.lang.reflect.Method` và `java.lang.reflect.Type`.

Giả sử concrete declaration là:

```java
public void storeNames(List<String> values) { /* ... */ }
```

Các khái niệm:

| API | Có thể thấy gì? | Không kết luận được gì? |
| --- | --- | --- |
| `Method.getParameterTypes()[0]` | `java.util.List.class` — raw Class | Không cho biết element đang là String |
| `Method.getGenericParameterTypes()[0]` | Metadata `java.util.List<java.lang.String>` trong method declaration nếu còn generic signature | Không chứng minh List object thực sự chỉ chứa String |
| `JoinPoint.getArgs()[0].getClass()` | Concrete runtime List class | Không có type argument chuẩn như `List<String>` |
| `MethodSignature.getParameterTypes()[0]` | Erased declared parameter Class | Không đủ để suy `T` ở method generic |
| `JoinPoint.getArgs()` | Actual runtime values | Không cung cấp Java type arguments trên collection |

Một `ParameterizedType` có thể mô tả chữ ký method có `List<String>`, nhưng data của `Type` **thuộc declaration**, không thuộc object List đang được truyền. Nếu caller dùng raw types/unchecked casts sai, chữ ký vẫn ghi String.

### 6.1. Spring `ResolvableType` giúp đọc generic declaration

Spring cung cấp `org.springframework.core.ResolvableType` để làm việc với generic type metadata trong reflection:

```java
Method method = ...; // Concrete method đã được xác định đúng.
ResolvableType parameter = ResolvableType.forMethodParameter(method, 0);
Class<?> elementType = parameter.getGeneric(0).resolve();

System.out.println(elementType); // Với List<String>: class java.lang.String.
```

Đây là fragment, không phải file Java hoàn chỉnh. `ResolvableType` thuận tiện hơn việc tự xử lý `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`, nhưng:

- Nó resolve **declared type metadata**, không duyệt runtime collection.
- Với `T` chưa biết, wildcard hoặc parameterized type phức tạp, resolution có thể không thành `String.class`; `resolve()` có thể trả `null` hoặc fallback tùy overload.
- Method được chọn phải đúng declaring/implementation context. Một generic interface `Port<T>` chỉ chứa `T`; muốn biết concrete binding `T = String`, cần xét target implementation class hoặc cung cấp owner context phù hợp.
- Có thể có proxies, bridge methods và hierarchies; đọc nhầm `Method` của interface thay vì implementation sẽ làm metadata kém cụ thể hơn.

Không biến `ResolvableType.resolve() == String.class` thành một “kiểm tra runtime List chỉ chứa String”.

### 6.2. Tên argument không phải generic type

Spring có `argNames`, phản chiếu Java parameter names và pointcut binding. Đây là cơ chế **gắn tên biến advice** với pointcut expression, hoàn toàn khác với việc bảo đảm generic type.

Ví dụ:

```java
@Before(
    value = "execution(* demo.generic.CatalogService.storeNames(..))"
          + " && args(values)",
    argNames = "values"
)
public void before(List<?> values) {
    System.out.println(values.size());
}
```

`argNames="values"` giúp Spring bind tên `values`; nó không khiến `List<?>` biến thành `List<String>`. Các compiler flags như `-parameters` hỗ trợ discovery tên method parameters, chứ không thay cơ chế generic erasure.

## 7. Generic interface, bridge methods và Spring proxy

Ví dụ:

```java
public interface Handler<T> {
    void handle(T value);
}

public class StringHandler implements Handler<String> {
    @Override
    public void handle(String value) {
        System.out.println("handle " + value);
    }
}
```

Khi biên dịch Java, generic `Handler<T>` bị erase (với unbounded T thường thành `Object`). Compiler có thể tạo **synthetic bridge method** để bảo đảm overriding và polymorphism làm việc đúng giữa `handle(Object)` và `handle(String)`.

Với Spring AOP:

- Một JDK proxy có thể nhận method nhìn theo **interface declaration** có tham số erasure là `Object`, trong khi implementation method cụ thể nhận `String`.
- CGLIB proxy có thể liên quan subclass/bridge signatures. Đừng khẳng định mọi invocation cho một generic method luôn có đúng một Java Method object giống nhau ở tất cả proxy types.
- Khi cần tìm annotation hoặc generic metadata cụ thể, nên resolve **most-specific target method** và xử lý bridge nếu cần.

Spring cung cấp công cụ:

```java
MethodSignature sig = (MethodSignature) jp.getSignature();
Method invokedMethod = sig.getMethod();
Object target = jp.getTarget();

if (target != null) {
    Method specific = AopUtils.getMostSpecificMethod(
            invokedMethod, target.getClass());
    System.out.println(specific.toGenericString());
}
```

Snippet cần imports `org.aspectj.lang.reflect.MethodSignature`, `java.lang.reflect.Method`, `org.springframework.aop.support.AopUtils`. Spring cung cấp `BridgeMethodResolver` khi cần làm việc với bridge methods cụ thể:

```java
Method userMethod = BridgeMethodResolver.findBridgedMethod(specific);
```

`BridgeMethodResolver` nằm ở `org.springframework.core`. Lời gọi trên chỉ minh họa khi biến `specific` có sẵn. Không tự cast result sang implementation method rồi giả định `getGenericParameterTypes()` lúc nào cũng là `List<String>`, nhất là khi metadata được khai báo ở generic superclass/interface.

### 7.1. Generic type erasure không phải lỗi riêng của Spring

Khi ứng dụng dùng reflection, dynamic proxy và method interception, erasure là đặc tính của Java compilation/runtime. Spring cung cấp utilities giúp **giải metadata**, nhưng không thể suy luận thứ Java runtime không lưu ở object hoặc method context.

Vì vậy, việc thay `@EnableAspectJAutoProxy(proxyTargetClass=true)` không thể tự sửa một advice đang mong `List<String>` tự match contents.

## 8. Dự án chạy được: phân biệt metadata generic và runtime arguments

Ví dụ dùng **Java 17**, **Maven** và **Spring Framework 6.2.x** không cần Spring Boot. Ta chủ động tạo một Spring-managed service có interface và một aspect với ba before advices cho ba method khác nhau. Mỗi method dùng pointcut riêng để stdout có thứ tự kiểm chứng được, không phụ thuộc precedence của nhiều advisors trên cùng invocation.

Mục tiêu:

1. Bind tham số `String` scalar với `args(text)`.
2. Đọc declared generic parameter type của `List<String>` và `List<Integer>`.
3. Chứng minh runtime list là `List`, nhưng nếu muốn biết element class thì phải kiểm tra contents.
4. Không log concrete implementation class của `List.of(...)` vì tên internal của JDK không là API ổn định.
5. Dùng Spring-managed JDK proxy để minh họa đúng ranh giới AOP.

### 8.1. Cấu trúc thư mục

```text
spring-aop-generic-args/
├── pom.xml
└── src/main/java/demo/generic/
    ├── AppConfig.java
    ├── PayloadPort.java
    ├── PayloadService.java
    ├── InspectAspect.java
    └── Main.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-generic-args</artifactId>
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
package demo.generic;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@EnableAspectJAutoProxy(proxyTargetClass=false)` trong Spring Framework thuần ưu tiên **JDK interface proxy** vì target bên dưới triển khai `PayloadPort`.

### 8.4. `PayloadPort.java`

```java
package demo.generic;

import java.util.List;

public interface PayloadPort {
    void submit(String text);
    void storeNames(List<String> names);
    void storeNumbers(List<Integer> numbers);
}
```

Đây là ba method **khác tên**, vì Java không cho overload chỉ khác `List<String>` và `List<Integer>` sau erasure.

### 8.5. `PayloadService.java`

```java
package demo.generic;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PayloadService implements PayloadPort {
    @Override
    public void submit(String text) {
        System.out.println("[target] submit " + text);
    }

    @Override
    public void storeNames(List<String> names) {
        System.out.println("[target] names count=" + names.size());
    }

    @Override
    public void storeNumbers(List<Integer> numbers) {
        System.out.println("[target] numbers count=" + numbers.size());
    }
}
```

Target chỉ in số phần tử, không cast element, để tập trung vào điều kiện match/generic observation của advice.

### 8.6. `InspectAspect.java`

```java
package demo.generic;

import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class InspectAspect {
    @Before(
        value = "execution(* demo.generic.PayloadService.submit(..))"
              + " && args(text)",
        argNames = "text"
    )
    public void beforeText(String text) {
        System.out.println("[advice] text=" + text);
    }

    @Before("execution(* demo.generic.PayloadService.storeNames(..))")
    public void beforeNames(JoinPoint jp) {
        Method method = concreteMethod(jp);
        List<?> values = (List<?>) jp.getArgs()[0];

        System.out.println("[advice] declared="
                + method.getGenericParameterTypes()[0].getTypeName());
        System.out.println("[advice] runtimeIsList=" + (values instanceof List));
        System.out.println("[advice] allStrings="
                + values.stream().allMatch(String.class::isInstance));
    }

    @Before("execution(* demo.generic.PayloadService.storeNumbers(..))")
    public void beforeNumbers(JoinPoint jp) {
        Method method = concreteMethod(jp);
        List<?> values = (List<?>) jp.getArgs()[0];

        System.out.println("[advice] declared="
                + method.getGenericParameterTypes()[0].getTypeName());
        System.out.println("[advice] runtimeIsList=" + (values instanceof List));
        System.out.println("[advice] allIntegers="
                + values.stream().allMatch(Integer.class::isInstance));
    }

    private Method concreteMethod(JoinPoint jp) {
        MethodSignature sig = (MethodSignature) jp.getSignature();
        return AopUtils.getMostSpecificMethod(
                sig.getMethod(), jp.getTarget().getClass());
    }
}
```

Trong `InspectAspect`:

- `@Before ... args(text)` bind scalar `String` cụ thể.
- Hai method còn lại dùng `JoinPoint` để xem object List, resolve **concrete declaration** bằng Spring `AopUtils.getMostSpecificMethod(...)`, rồi đọc generic signature metadata.
- `method.getGenericParameterTypes()[0]` mô tả declared `List<String>` hoặc `List<Integer>`.
- `values instanceof List` chỉ kiểm tra **raw runtime class/interface**.
- `allMatch(...)` kiểm tra từng phần tử hiện có. Đây là **policy minh họa**; nó không tự là hợp đồng tổng quát cho List rỗng/null/mutable.
- Ba pointcuts không chồng lên nhau, vì vậy kết quả log dễ dự đoán.

### 8.7. `Main.java`

```java
package demo.generic;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            PayloadPort port = context.getBean(PayloadPort.class);
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(port));

            port.submit("A1");
            port.storeNames(List.of("An", "Binh"));
            port.storeNumbers(List.of(10, 20));
        }
    }
}
```

Chạy trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.generic.Main
```

**Stdout do các lệnh `System.out` của ví dụ phát ra** (không tính log của Maven/Spring nếu môi trường in thêm):

```text
jdkProxy=true
[advice] text=A1
[target] submit A1
[advice] declared=java.util.List<java.lang.String>
[advice] runtimeIsList=true
[advice] allStrings=true
[target] names count=2
[advice] declared=java.util.List<java.lang.Integer>
[advice] runtimeIsList=true
[advice] allIntegers=true
[target] numbers count=2
```

### 8.8. Đọc kết quả chính xác

1. Spring quản lý `PayloadService`, aspect `InspectAspect` được scan và pointcuts match; interface proxy tạo ra nên `jdkProxy=true`.
2. `port.submit("A1")` đi qua proxy. `args(text)` bind String runtime và advice in `text=A1` trước target.
3. `port.storeNames(List.of("An","Binh"))` vào advice. Reflection trên concrete method cho declared `java.util.List<java.lang.String>`, không phải giá trị “type argument của runtime List object”.
4. `values instanceof List` là true. Kiểm tra từng phần tử String trong **mảng dữ liệu thực tế của lời gọi này** trả true; đó là kết quả do sample contents, không do pointcut tự suy generic.
5. `port.storeNumbers(List.of(10,20))` có cùng raw List surface, nhưng declared generic type trong **method khác** là `List<Integer>` và từng element trong ví dụ là Integer.
6. Không có đoạn nào chứng minh mọi List runtime đều có type parameter cố định; output chỉ phản ánh hai method declarations và những sample arguments cụ thể.

## 9. Thử nghiệm suy luận sai thường gặp

### 9.1. Empty collection làm `allMatch` trả true

```java
List<?> empty = List.of();
boolean strings = empty.stream().allMatch(String.class::isInstance);
boolean integers = empty.stream().allMatch(Integer.class::isInstance);
System.out.println(strings + " " + integers); // true true
```

Đây là snippet Java độc lập (trong method) minh họa semantics Stream. Empty list không có phần tử để phản chứng cả hai kiểm tra. Nếu business policy đòi **có ít nhất một phần tử và tất cả là String**:

```java
boolean nonEmptyStrings = !empty.isEmpty()
        && empty.stream().allMatch(String.class::isInstance);
```

Cần dựa vào validation contract riêng, không đổ lỗi cho Spring AOP vì collection rỗng.

### 9.2. Phần tử null và wildcard

```java
List<?> mixed = java.util.Arrays.asList("An", null);
boolean allStrings = mixed.stream().allMatch(String.class::isInstance);
System.out.println(allStrings); // false
```

Nếu `null` được cho phép theo business contract, phải viết rõ điều kiện, ví dụ `value == null || value instanceof String`. Trong Spring AOP, `args(values)` bind **whole list**; nó không biết policy null của từng element.

### 9.3. Raw types/unchecked casts có thể phá lời hứa của declaration

```java
@SuppressWarnings({"rawtypes", "unchecked"})
static List<String> unsafe() {
    List raw = List.of(123);
    return raw; // Biên dịch với unchecked conversion, không kiểm tra elements.
}
```

Đây là **phản ví dụ** cho type safety, không phải pattern nên dùng. Runtime list chứa `Integer` dù caller về mặt source cầm `List<String>`. `Method.getGenericParameterTypes()` vẫn mô tả declaration `List<String>` nếu method contract là như vậy; nó không chứng minh object chứa String.

Khi validation là yêu cầu thực, kiểm tra nội dung tại boundary phù hợp hoặc dùng DTO/domain types không gây nhầm lẫn type parameter.

### 9.4. `getClass().getTypeParameters()` không phải câu trả lời

```java
List<String> names = List.of("An");
System.out.println(names.getClass().getTypeParameters().length);
```

Một concrete runtime class của List có thể không khai báo bất kỳ formal type variable nào cần cho suy luận; dù có generic type variables, chúng **không phải** actual `String` argument ở call site. Không dùng `getTypeParameters()` của runtime instance như cách “phục hồi List<String>”.

### 9.5. Return value generics có giới hạn tương tự

Nếu một service trả `List<User>`, `@AfterReturning(returning="result")` có thể bind reference được return; advice không tự xác minh mỗi User bên trong list. Nếu chỉ cần quan sát, dùng `List<?>`/`Object` thích hợp và tránh ép generic không được kiểm chứng. Đối với `@Around`, `proceed()` trả `Object`; advice phải trả lại value tương thích **declared return type** và không thay container object bằng một type khác.

## 10. Cạm bẫy khi đọc generic metadata trong Spring

| Vấn đề | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `jp.getArgs()[0].getClass()` không có `String` | Runtime List class bị generic erasure | Dùng declaration metadata nếu muốn biết chữ ký; inspect elements nếu cần validate |
| Advice `Collection<Invoice>` không lọc đúng contents | Spring không match generic collection elements | Bind `Collection<?>` rồi kiểm tra elements theo policy |
| Empty list passes allMatch String/Integer | Không có phần tử để phản chứng | Kết hợp `!isEmpty()` khi policy cần nonempty |
| `null` làm `getClass()` lỗi | Null không có runtime class | Kiểm tra null trước |
| Reflection thấy type variable `T` thay vì String | Đang đọc generic interface/base method, chưa resolve implementation binding | Dùng concrete target class context, `ResolvableType`, Spring method resolution |
| `MethodSignature.getMethod()` chỉ về interface | JDK proxy hoạt động trên interface methods | Dùng `AopUtils.getMostSpecificMethod(...)` |
| Có synthetic bridge method | Java compiler sinh bridge do erasure/overriding | Sử dụng `BridgeMethodResolver` nếu cần user method |
| `argNames` không hoạt động | Parameter name binding không đủ metadata hoặc sai tên | Khai báo đúng `argNames`, cấu hình `-parameters` nếu cần |
| Overload `m(List<String>)` và `m(List<Integer>)` không biên dịch | Hai method có cùng erased signature | Đặt method names khác hoặc dùng domain wrapper types |
| `@Around` trả raw List chứa sai element type | Advice chỉ giữ raw type, không bảo toàn generic contract | Giữ nguyên value/collection, test domain contract |
| Proxy không advice method cần inspect | Target không là Spring bean, aspect thiếu đăng ký hoặc self-invocation | Kiểm tra context/proxy/pointcut |
| Đổi CGLIB mà không sửa được generic mismatch | Proxy mode không tái tạo erased element type | Sửa validation/API thiết kế |
| Log toàn bộ generic payload | Dữ liệu có thể nhạy cảm, tốn chi phí, `toString()` side effects | Redaction, chỉ log metadata an toàn |
| Async Future/Reactive Publisher generic T không suy từ payload tức thời | Container wrapper đã return trước khi payload sẵn | Xử lý trong async/reactive context, giữ đúng wrapper contract |

### Quy trình kiểm tra để tránh suy luận quá mức

1. Xác nhận method thực sự được gọi qua **Spring AOP proxy** và pointcut đúng.
2. Xác định bạn muốn biết **declared generic signature** hay **runtime contents**.
3. Dùng `getArgs()` để lấy actual value, kiểm tra `null` và raw runtime interface.
4. Dùng `MethodSignature`/most-specific method/`ResolvableType` để đọc declared metadata, nếu cần.
5. Nếu muốn biết element types, định nghĩa explicit policy cho collection rỗng, null elements, mixed contents và mutable data.
6. Không dùng `Collection<T>` typed advice như công cụ tự động kiểm tra tất cả element types.
7. Xét bridge/interface method khi target là generic implementation.
8. Test Spring AOP integration trên đúng proxy mode, empty/null/mixed/boundary data; không chỉ dựa vào một sample hợp lệ.

## 11. Câu hỏi tự kiểm tra

**1. Spring `JoinPoint.getArgs()` có trả `List<String>` reified object không?** Không. Nó trả actual argument object; Java List runtime thông thường không lưu generic type argument String.

**2. `Method.getGenericParameterTypes()[0]` trả `List<String>` thì có chứng minh nội dung List gồm Strings không?** Không. Nó mô tả method declaration metadata, không kiểm tra runtime elements.

**3. Vì sao `Collection<Invoice>` trong signature advice không đủ để lọc runtime collection?** Vì erasure và Spring không tự duyệt từng phần tử hoặc quyết định null/empty policy.

**4. Nếu muốn kiểm tra thật những phần tử trong list, dùng gì?** Bind `Collection<?>`/`List<?>` rồi inspect phần tử bằng `instanceof`/`Class.isInstance` theo business policy.

**5. `allMatch(String.class::isInstance)` trên list rỗng trả gì?** True; đó không phải bằng chứng list có generic String.

**6. Vì sao một JDK proxy đôi khi chỉ cung cấp Method của interface trong signature?** Proxy thực hiện interface. Khi cần concrete metadata, resolve most-specific target method.

**7. Bridge method là gì trong bối cảnh Spring AOP generic target?** Method compiler có thể sinh để giữ polymorphism sau erasure; việc đọc signature/annotation cần quan tâm bridge resolution.

**8. `-parameters` có khôi phục `List<String>` runtime contents không?** Không. Nó giúp lưu Java parameter names; khác với generic metadata/element type checking.

**9. Việc đổi `proxyTargetClass=true` có giúp `args(...)` match List<String> elements tự động không?** Không. CGLIB không thay đổi generic erasure.

**10. Điểm nào nên chịu trách nhiệm validation dữ liệu?** Boundary/service/domain validation có contract rõ ràng; advice có thể quan sát hoặc áp cross-cutting policy nhưng không tự suy luận hơn khả năng runtime.

## Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice: Advice Parameters and Generics](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Declaring Advice: binding args, advice parameter types](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — Declaring Pointcuts: args và execution](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 API — ResolvableType](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/ResolvableType.html)
- [Spring Framework 6.2 API — BridgeMethodResolver](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/BridgeMethodResolver.html)
- [Spring Framework — Proxying Mechanisms, JDK/CGLIB và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework 6.2 API — AopUtils.getMostSpecificMethod](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/support/AopUtils.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-010">Bridge method, phương thức interface và phương thức triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Bridge method, phương thức interface và phương thức triển khai trong Spring AOP

## 1. Mục tiêu bài học

Một Spring bean có thể được truy cập qua **interface**, triển khai logic trong **implementation class**, và dùng **generic type**. Khi dùng Spring AOP, một lời gọi đơn giản như `processor.process("spring")` đôi khi liên quan tới ba đối tượng `java.lang.reflect.Method` khác nhau:

1. **Method trên interface** mà JDK dynamic proxy công bố.
2. **Method cụ thể trên implementation** thực hiện nghiệp vụ.
3. **Synthetic bridge method** do Java compiler tạo để bảo toàn overriding sau khi generic type erasure.

Chúng có thể cùng tên nhưng có **declaring class**, **parameter type**, **return type**, **annotation** và cờ `isBridge()` khác nhau. Nếu advice chỉ đọc `MethodSignature.getMethod()` rồi giả định đó luôn là method triển khai, logic tìm annotation hoặc chọn metadata dễ sai.

Sau bài này, bạn có thể:

- Phân biệt rõ **interface method**, **implementation method** và **bridge method** trong ngữ cảnh Spring AOP.
- Giải thích vì sao Java compiler tạo bridge cho `Processor<T>` và `TextProcessor implements Processor<String>`.
- Hiểu JDK dynamic proxy khác CGLIB ở cách expose method và điều gì có thể xuất hiện trong `MethodSignature`.
- Dùng **`AopUtils.getMostSpecificMethod(Method, Class<?>)`** để tìm method tương ứng trên target class, và **`BridgeMethodResolver.findBridgedMethod(Method)`** khi cần gỡ bridge.
- Xây một **`@Around` Spring aspect** ghi thông tin method đúng, không giả định chữ ký reflection luôn đến từ implementation.
- Kiểm tra annotation đặt ở interface so với annotation đặt ở implementation mà không khẳng định sai rằng mọi kiểu proxy luôn trả cùng metadata.
- Nhận diện nguy cơ ghi log/advice trùng, pointcut match sai, type erasure, self-invocation và method không nằm trong proxy interface.

**Phạm vi:** Tập trung vào **Spring Framework proxy-based AOP** và cách Spring xử lý method metadata tại một method-execution join point. Không đi sâu vào Java generics hoặc AspectJ weaving độc lập ngoài những gì cần thiết để hiểu Spring Aspect.

## 2. Ba lớp chữ ký thường gặp

Ví dụ Java:

~~~java
public interface Processor<T> {
    T process(T input);
}

public class TextProcessor implements Processor<String> {
    @Override
    public String process(String input) {
        return input.trim();
    }
}
~~~

Đây là **hai top-level types, đặt ở hai file Java khác nhau** nếu muốn biên dịch, và chưa phải toàn bộ ứng dụng Spring AOP của mục 7.

Ở source code, ta thấy:

- Interface: `T process(T input)`.
- Implementation: `String process(String input)`.

Nhưng do **type erasure**, chữ ký runtime của interface generic tương ứng với kiểu erased:

~~~text
Processor.process(Object) : Object
~~~

Trong implementation có method thật:

~~~text
TextProcessor.process(String) : String
~~~

Để một lời gọi qua interface erased vẫn hoạt động đúng với method đã override, compiler có thể sinh thêm bridge tương tự:

~~~java
// Pseudocode biểu diễn bytecode do compiler tạo,
// không chèn đoạn này trực tiếp vào TextProcessor.java.
public Object process(Object input) {
    return process((String) input);
}
~~~

Bridge method là **synthetic method của class file**, không phải method ứng dụng tự viết. Ví dụ có thể được phản chiếu bằng `Method.isBridge() == true` và `Method.isSynthetic() == true`. Method triển khai thật `process(String)` không mang cờ bridge.

Sơ đồ:

~~~text
Processor<String> proxy (JDK interface)
      |
      +-- process(Object) ở interface sau erasure
      |
      v
Spring AOP invocation
      |
      v
TextProcessor target
      |
      +-- bridge process(Object) (do javac tạo)
      |       |
      |       +-- chuyển String argument rồi gọi
      |           process(String)
      |
      +-- process(String) (method nghiệp vụ thực)
~~~

Sơ đồ là **mô hình lý giải chữ ký**, không bắt buộc mọi invocation Spring phải gọi cả hai method qua reflection theo đúng thứ tự. Spring AOP có thể **resolve method cụ thể/bridge** trước khi quyết định advisor và gọi target. Đừng suy ra có hai advice executions chỉ vì có bridge method.

## 3. Tại sao có bridge method?

Java generics phần lớn được triển khai bằng **type erasure**. Trước erasure, `Processor<String>.process(String)` nhìn như một method nhận và trả String. Sau erasure, bytecode của interface phải hỗ trợ `process(Object)`; implementation lại khai báo `process(String)`.

Nếu compiler không duy trì một đường gọi tương thích với erased interface method, Java polymorphism bị phá vỡ khi caller dùng reference kiểu generic interface. Bridge giải quyết việc đó.

Phân biệt:

| Thuộc tính | Interface method | Implementation method | Bridge method |
| --- | --- | --- | --- |
| Ví dụ | `Processor.process(Object)` | `TextProcessor.process(String)` | `TextProcessor.process(Object)` |
| Nguồn tạo | Định nghĩa interface generic | Developer viết | Compiler tạo |
| `getDeclaringClass()` | `Processor.class` | `TextProcessor.class` | `TextProcessor.class` |
| Parameter runtime | Object | String | Object |
| Return runtime | Object | String | Object |
| `isBridge()` | false | false | **true** |
| `isSynthetic()` | Thường false | Thường false | **true** trong ví dụ javac |
| Annotation ở implementation | Không tự có annotation đó | Có nếu khai báo | Không nên coi annotation bridge là nguồn chân lý |

Không nên chép/match tên method rồi tự suy đoán generic parameter. Với method có overload hoặc hierarchy, chỉ dùng `getName()` sẽ không phân biệt được:

~~~java
String process(String input);
String process(String input, int limit);
~~~

Điểm quan trọng là dùng **type + signature + target class** để resolve đúng method.

## 4. Spring AOP hoạt động qua proxy, không phải weave bytecode

Spring AOP dùng một proxy bao quanh Spring-managed target:

~~~text
Caller
  |
  v
JDK proxy hoặc CGLIB proxy
  |
  +-- Advisor / pointcut
  +-- @Before, @Around, @After... thích hợp
  |
  v
Target method
~~~

Hai kiểu proxy chủ yếu:

### 4.1. JDK dynamic proxy

Khi target có interface và cấu hình chọn JDK proxy, đối tượng nhận bởi caller triển khai các interface đã được proxy. Caller thường viết:

~~~java
Processor<String> processor = /* Spring-injected proxy */;
processor.process("hello");
~~~

Proxy **không phải một instance của `TextProcessor`**. Không ép kiểu proxy JDK về concrete class:

~~~java
// Có thể ClassCastException nếu bean là JDK dynamic proxy.
TextProcessor concrete = (TextProcessor) processor;
~~~

Method thông qua `MethodSignature.getMethod()` trong một invocation JDK proxy **có thể là method trên interface**, không phải method ứng dụng đã annotate trong implementation. Vì vậy metadata của interface có thể khác metadata của target.

### 4.2. Class-based CGLIB proxy

Khi cấu hình `@EnableAspectJAutoProxy(proxyTargetClass = true)` hoặc target không có interface phù hợp, Spring có thể dùng CGLIB class-based proxy.

Proxy là subclass của target và có thể override những method hợp lệ. Nhưng:

- Không proxy được `final` class bằng subclass.
- Không advice được `private`/`final` method bằng override.
- Method không visible để subclass override, ví dụ package-private trong superclass ở package khác, có thể không được advice.
- Self-invocation trên target vẫn bypass Spring proxy.
- Bridge/synthetic methods có thể hiện diện trong target class, nhưng không nên coi bridge là một operation riêng để thực hiện business advice lần thứ hai.

**Bật CGLIB không làm biến mất bridge method mà compiler đã tạo**, cũng không đảm bảo `MethodSignature.getMethod()` lúc nào có đúng annotation bạn cần. Khi đọc metadata, giải pháp bền vững là resolve target method rõ ràng.

### 4.3. Phân biệt JDK proxy và target

Các helper hữu ích:

~~~java
AopUtils.isAopProxy(bean);
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
AopUtils.getTargetClass(bean);
~~~

Trong đó `getTargetClass(bean)` trả class thực của target khi framework xác định được. Nó **không** mang nghĩa unwrap tất cả custom proxies trong mọi hệ thống. `pjp.getTarget()` trong advice chỉ target hiện tại, còn `pjp.getThis()` biểu diễn Spring AOP proxy.

## 5. MethodSignature và những giới hạn phải hiểu

Ở `@Around` advice, có thể lấy metadata:

~~~java
MethodSignature signature =
        (MethodSignature) pjp.getSignature();

Method invokedMethod = signature.getMethod();
~~~

`MethodSignature` là interface thuộc `org.aspectj.lang.reflect`; Spring AOP cung cấp thông tin join point theo contract đó. Trong JDK proxy, `invokedMethod` thường mô tả interface method; với CGLIB hoặc tình huống generic khác, declaring class/bridge status có thể khác.

**Không được mặc định**:

~~~java
// Sai giả định: method từ signature luôn là method khai báo
// trực tiếp trong TextProcessor và luôn mang annotation của implementation.
Method method = ((MethodSignature) pjp.getSignature()).getMethod();
boolean marked = method.isAnnotationPresent(TrackedOperation.class);
~~~

Nếu annotation chỉ xuất hiện trên `TextProcessor.process(String)`, phép kiểm tra trên `Processor.process(Object)` có thể trả false.

Hai đối tượng khác nhau:

- **Invoked method**: method mà AOP invocation/signature công bố tại vị trí proxy.
- **Most-specific target method**: method triển khai tương ứng trên target class, phù hợp để tìm metadata của implementation.

Đừng kết luận rằng chỉ cần `getMethod().getDeclaringClass()` là bạn đã tìm được concrete implementation type.

## 6. Hai API Spring quan trọng

### 6.1. AopUtils.getMostSpecificMethod

~~~java
Method resolved = AopUtils.getMostSpecificMethod(
        invokedMethod,
        targetClass
);
~~~

API thuộc `org.springframework.aop.support.AopUtils`. Nó tìm method trên target class tương ứng với method được cung cấp, ví dụ từ `Processor.process(Object)` về `TextProcessor.process(String)` trong trường hợp generic bridge được giải quyết.

Nó đặc biệt hữu ích khi phải truy tìm:

- Custom annotation đặt trên implementation method.
- Method signature đúng của target cho audit/metrics.
- Method cụ thể có generic return/parameter đã được bind.
- Chỗ thực sự định nghĩa nghiệp vụ trong class hierarchy.

Trong Spring Framework 6.2.x, helper này có khả năng xử lý **bridge resolution**; vì vậy không cần giả định nó sẽ trả bridge. Nếu cần làm rõ cho người đọc, vẫn có thể dùng `BridgeMethodResolver.findBridgedMethod(...)` an toàn trên kết quả.

### 6.2. BridgeMethodResolver.findBridgedMethod

~~~java
Method bridged =
        BridgeMethodResolver.findBridgedMethod(candidate);
~~~

API thuộc `org.springframework.core.BridgeMethodResolver`. Nếu `candidate` là bridge method, Spring cố tìm **method gốc tương ứng**. Nếu không phải bridge, API có thể trả lại method đầu vào; không bắt buộc gọi `isBridge()` trước.

**Hạn chế:** `findBridgedMethod` chỉ resolve theo class chứa candidate; nó **không thể tự suy ra toàn bộ implementation** chỉ từ một method của interface nếu không có target class. Vì vậy thường cần:

~~~java
Method declared =
        ((MethodSignature) pjp.getSignature()).getMethod();

Class<?> targetClass = pjp.getTarget().getClass();

Method specific =
        AopUtils.getMostSpecificMethod(declared, targetClass);

Method original =
        BridgeMethodResolver.findBridgedMethod(specific);
~~~

Cách làm này xử lý tốt tình huống method trên proxy là interface/generic bridge và bạn cần xem annotation trên implementation. Nó không tự giải quyết mọi bài toán **annotation inheritance, interface annotation composition hoặc meta-annotation**; với những trường hợp đó cần thêm chiến lược Spring merged annotation phù hợp.

### 6.3. Tại sao không dùng getDeclaredMethod trực tiếp?

Đoạn sau dễ sai:

~~~java
targetClass.getDeclaredMethod(
    invokedMethod.getName(),
    invokedMethod.getParameterTypes()
);
~~~

Nếu `invokedMethod` là `Processor.process(Object)` nhưng implementation method nghiệp vụ khai báo `process(String)`, lời gọi ở trên có thể trả bridge `process(Object)` hoặc thất bại, tùy class/compiler/hierarchy. Khi annotation nằm trên `process(String)`, đọc trực tiếp bridge không phải mục tiêu.

`AopUtils.getMostSpecificMethod` và `BridgeMethodResolver` được Spring thiết kế chính cho những trường hợp này.

## 7. Ví dụ chạy được: generic Processor, Spring @Around và bridge resolution

Dự án sử dụng **Java 17+**, **Maven 3.9+**, **Spring Framework 6.2.12**, **AspectJ Weaver 1.9.24**. Không cần Spring Boot hay database.

### 7.1. Cấu trúc

~~~text
spring-aop-bridge-method/
├── pom.xml
└── src/main/java/demo/
    ├── Processor.java
    ├── TrackedOperation.java
    ├── TextProcessor.java
    ├── TraceAspect.java
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
    <artifactId>spring-aop-bridge-method</artifactId>
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

`spring-context` đưa vào các module container và AOP cần thiết; `aspectjweaver` cho Spring đọc `@AspectJ` annotation/pointcut expression. Đây vẫn là **Spring proxy-based AOP**, không phải bytecode weaving.

### 7.3. Processor.java

~~~java
package demo;

public interface Processor<T> {
    T process(T input);
}
~~~

Ở runtime, `Processor<T>.process(T)` bị erasure thành method nhận và trả Object. Generic type parameter của source không buộc Java reflection method mang parameter String.

### 7.4. TrackedOperation.java

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedOperation {
}
~~~

Marker annotation được khai báo với runtime retention và method target. Nó chỉ gắn ở **method implementation** trong ví dụ; cố ý không gắn vào interface, để phân biệt metadata từ hai nơi.

### 7.5. TextProcessor.java

~~~java
package demo;

import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TextProcessor implements Processor<String> {

    @TrackedOperation
    @Override
    public String process(String input) {
        System.out.println("TARGET process(String)");
        return input.trim().toUpperCase(Locale.ROOT);
    }
}
~~~

Hai điểm:

1. `@Service` làm implementation trở thành Spring bean qua component scanning.
2. `@TrackedOperation` nằm trên `process(String)`, không nằm trên interface generic `process(Object)`.

Với javac, Java compiler tạo bridge để implementation tuân theo interface erased contract. Việc đó không cần tự viết bridge trong source.

### 7.6. TraceAspect.java

~~~java
package demo;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {

    @Around("execution(* demo.Processor+.process(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature =
                (MethodSignature) pjp.getSignature();

        Method interfaceOrProxyMethod = signature.getMethod();

        Method specific = AopUtils.getMostSpecificMethod(
                interfaceOrProxyMethod,
                pjp.getTarget().getClass()
        );

        Method original = BridgeMethodResolver.findBridgedMethod(specific);

        boolean annotationOnImplementation =
                original.isAnnotationPresent(TrackedOperation.class);

        System.out.println(
                "ADVICE process annotationOnImpl=" +
                annotationOnImplementation);

        return pjp.proceed();
    }
}
~~~

Cách đọc:

- `@Aspect` khai báo advice; `@Component` đăng ký aspect bean.
- Pointcut `execution(* demo.Processor+.process(..))` chọn những method executions phù hợp thuộc type `Processor` hoặc subtype/implementation của nó, trong giới hạn Spring AOP.
- `signature.getMethod()` cho method được AOP invocation công bố. **Không ép kết luận nó luôn là implementation method.**
- `AopUtils.getMostSpecificMethod` dựa trên **target class** để tìm method cụ thể.
- `BridgeMethodResolver.findBridgedMethod` bảo đảm nếu candidate là bridge, ta dùng method gốc.
- `original.isAnnotationPresent` kiểm tra annotation **đặt trực tiếp** trên original method. Với custom composed/meta-annotations hoặc annotations trên interface, phải dùng cách lookup tương ứng với yêu cầu.
- `pjp.proceed()` đúng một lần, trả nguyên kết quả và để target thực thi. Advice **không tự gọi lại** `TextProcessor.process` trực tiếp nên không phá chuỗi interceptor.

`@Around` là một trong các loại advice quan sát signature thuận tiện; với `@Before`, có thể dùng `JoinPoint` và cast signature tương tự, nhưng không có `proceed()`.

### 7.7. AppConfig.java

~~~java
package demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "demo")
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
~~~

Cấu hình bật component scan để đăng ký `TextProcessor` và `TraceAspect`, sau đó tạo hạ tầng Spring AOP. `proxyTargetClass = false` thể hiện ý định **không ép class-based proxy**; với implementation có interface trong ví dụ, Spring Framework thuần dùng JDK dynamic proxy khi phù hợp.

Nếu một phần cấu hình khác dùng chung auto-proxy creator và ép CGLIB, loại proxy thực có thể đổi. Vì vậy chương trình sẽ in cả `AopUtils.isJdkDynamicProxy` để kiểm tra thay vì chỉ suy đoán.

### 7.8. App.java: quan sát interface, bridge và target method

~~~java
package demo;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.BridgeMethodResolver;

public class App {

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName()
                + "." + method.getName()
                + "("
                + Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("")
                + ")";
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            @SuppressWarnings("unchecked")
            Processor<String> processor =
                    (Processor<String>) context.getBean(Processor.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(processor));
            System.out.println(
                    "IS_JDK_PROXY " +
                    AopUtils.isJdkDynamicProxy(processor));

            Method interfaceMethod =
                    Processor.class.getMethod("process", Object.class);

            Method bridge = Arrays.stream(
                        TextProcessor.class.getDeclaredMethods())
                    .filter(Method::isBridge)
                    .filter(m -> m.getName().equals("process"))
                    .findFirst()
                    .orElseThrow();

            Method specific = AopUtils.getMostSpecificMethod(
                    interfaceMethod,
                    TextProcessor.class
            );

            Method bridged = BridgeMethodResolver.findBridgedMethod(bridge);

            System.out.println(
                    "INTERFACE_METHOD " + describe(interfaceMethod));
            System.out.println("BRIDGE_PRESENT " + bridge.isBridge());
            System.out.println("SPECIFIC_METHOD " + describe(specific));
            System.out.println(
                    "BRIDGED_METHOD " + describe(bridged));

            System.out.println(
                    "ANNOTATION_ON_INTERFACE " +
                    interfaceMethod.isAnnotationPresent(
                            TrackedOperation.class));
            System.out.println(
                    "ANNOTATION_ON_IMPL " +
                    bridged.isAnnotationPresent(
                            TrackedOperation.class));

            String result = processor.process("  spring  ");
            System.out.println("RESULT " + result);
        }
    }
}
~~~

Ở đây:

- `Processor.class.getMethod("process", Object.class)` lấy method đã bị erasure trên generic interface.
- `TextProcessor.class.getDeclaredMethods()` tìm bridge do compiler tạo; **lọc theo `isBridge()` thay vì tin vào thứ tự reflection**.
- `AopUtils.getMostSpecificMethod` nhận interface method và concrete target class; trong tình huống này nó tìm về `TextProcessor.process(String)`.
- `BridgeMethodResolver.findBridgedMethod(bridge)` truy về method gốc mà bridge hỗ trợ.
- `describe` in chữ ký ngắn, không in tên các proxy classes ngẫu nhiên, giúp output ổn định.
- Caller dùng `Processor<String>` interface bean, **không cast JDK proxy về TextProcessor**.

Chạy trong thư mục có `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

### 7.9. Stdout logic dự kiến

~~~text
IS_PROXY true
IS_JDK_PROXY true
INTERFACE_METHOD Processor.process(Object)
BRIDGE_PRESENT true
SPECIFIC_METHOD TextProcessor.process(String)
BRIDGED_METHOD TextProcessor.process(String)
ANNOTATION_ON_INTERFACE false
ANNOTATION_ON_IMPL true
ADVICE process annotationOnImpl=true
TARGET process(String)
RESULT SPRING
~~~

Đây là **stdout logic kỳ vọng** cho project Java 17+ và Spring Framework thuần cấu hình như trên, không tính logging Maven/Spring. Phần reflection của javac cho thấy một bridge method; Spring AOP chỉ ghi **một** dòng `ADVICE` cho lời gọi business proxy `process`.

Nếu dự án thực chạy với compiler, Java language level hoặc AOP config khác, cần kiểm chứng thực tế các dòng phản ánh loại proxy và method metadata. Không coi kiểu proxy hay số bridge methods của mọi class Java là bất biến trong mọi môi trường.

**Điểm cần nhìn:** `INTERFACE_METHOD` và `BRIDGED_METHOD` không trùng chữ ký, nhưng cùng mô tả một logical service operation. `ANNOTATION_ON_INTERFACE false` và `ANNOTATION_ON_IMPL true` chứng minh không được đọc annotation implementation bằng cách chỉ xem method ở interface.

## 8. Thử thay đổi proxyTargetClass sang CGLIB

Trong `AppConfig.java`, thay:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = false)
~~~

bằng:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

Đây là **thí nghiệm sửa một dòng cấu hình**, không tạo thêm `AppConfig` thứ hai.

Khi khởi động context mới:

- Spring có thể dùng **CGLIB class-based proxy** cho `TextProcessor`.
- `AopUtils.isAopProxy(processor)` vẫn dự kiến `true`.
- `AopUtils.isJdkDynamicProxy(processor)` dự kiến `false`.
- `AopUtils.isCglibProxy(processor)` sẽ là `true` nếu CGLIB thực sự được dùng.
- Class-based proxy vẫn có thể expose interface `Processor` để caller gọi qua interface.
- Bridge method trong class file vẫn tồn tại; đổi proxy không làm Java generics mất type erasure.
- Method mà `MethodSignature.getMethod()` biểu diễn có thể khác trong chi tiết declaring class so với JDK proxy; **logic resolve most-specific method vẫn cần thiết**.

Tránh mô tả rằng `proxyTargetClass = true` sẽ khiến Spring advise cả private, final hoặc self-invoked methods. Các giới hạn proxy vẫn còn.

## 9. Annotation trên interface và implementation: quyết định ở đâu?

Với `Processor<T>` và `TextProcessor implements Processor<String>`, một marker đặt trên interface method có thể **không có mặt trực tiếp** trên implementation method; chiều ngược lại cũng vậy. `@Inherited` của Java không làm annotation trên method được kế thừa qua override. Nếu dùng raw reflection, phải biết đang cầm `Method` của interface, implementation hay bridge.

Điều này tác động đến pointcut `@annotation(...)`: Spring có thể resolve method trong lúc match, còn gọi `isAnnotationPresent(...)` trên `MethodSignature.getMethod()` không tự tái hiện toàn bộ semantics của framework. Khi một annotation liên quan generic interface, hãy kiểm tra cả proxy JDK/CGLIB và nhất là **method được resolve sau bridge**. Đừng coi một bytecode bridge là method nghiệp vụ độc lập chỉ vì reflection nhìn thấy nó.

## 10. Khi cần annotation metadata hợp nhất

Đường đọc metadata đúng thứ tự là: lấy method từ `MethodSignature`, tìm **most-specific target method** bằng `AopUtils.getMostSpecificMethod`, resolve bridge khi cần bằng `BridgeMethodResolver.findBridgedMethod`, rồi chọn chính sách **raw annotation** hoặc **merged/meta-annotation** bằng các API Spring phù hợp. Bước tìm method và bước tìm annotation có mục đích khác nhau; điểm cắt `@annotation(...)` cũng phải được kiểm tra bằng advice invocation thực tế.

Bài **Đọc annotation trên đúng phương thức khi đối tượng là proxy** trình bày đầy đủ lookup ở interface, implementation, superclass và composed annotations. Phần trọng tâm tại đây là compiler bridge và cách xác định **một logical method** cho mỗi lời gọi proxy.

## 11. Bridge method không đồng nghĩa overload nghiệp vụ

Java source có thể cho developer viết overload:

~~~java
String process(String input);
String process(String input, int limit);
~~~

Nhưng bridge method thường là thứ compiler tạo để hỗ trợ overriding/generics hoặc covariant return, không phải overload mà team chủ động thêm.

| Khái niệm | Được tạo bởi | Vì sao có? | Có nên xem là business operation riêng? |
| --- | --- | --- | --- |
| Overload `process(String,int)` | Developer | Nhiều biến thể API khác danh sách tham số | Có, nếu nghiệp vụ phân biệt |
| Override `process(String)` | Developer | Cài đặt contract của generic interface | Có, là logic target |
| Bridge `process(Object)` | Compiler | Bảo toàn overriding sau type erasure | Thường **không** |
| JDK proxy method `Processor.process(Object)` | Interface/proxy contract | Cung cấp điểm gọi của proxy | Cùng logical operation của target |

Đừng ghi audit hai lần chỉ vì reflection trả về cả `process(String)` và `process(Object)`. Khi quét methods để tự xây metadata, nên lọc bridge/synthetic theo mục đích hoặc resolve về bridged method rồi deduplicate theo logical signature. Cần tránh việc lọc tất cả synthetic methods một cách mù quáng nếu framework có những method đặc biệt cần thiết khác.

Trong **Spring AOP**, một lời gọi `processor.process("x")` qua proxy về nguyên tắc là một logical proxy invocation; việc compiler sinh bridge không tự làm xuất hiện hai lời gọi advice cùng pointcut. Nếu quan sát hai log, trước hết kiểm tra **nhiều advisors cùng match**, double invocation thực tế hoặc nhiều proxy layers.

## 12. Generic, erasure và pointcut expression

Một expression như:

~~~text
execution(* demo.Processor+.process(..))
~~~

phù hợp để chọn một family method theo tên/type mà không phụ thuộc chi tiết generic erased parameter.

Nếu viết expression ràng buộc parameter cụ thể, cần hiểu:

- `Processor<T>` bị erasure; interface method có runtime parameter Object.
- Implementation `TextProcessor.process(String)` có signature riêng.
- Spring AspectJ pointcut matcher có thể resolve target/bridged method.
- Một expression quan sát interface/generic hierarchy không nên được đánh giá bằng cách chỉ đối chiếu chuỗi `Method.toString()`.

Nếu bạn cần lựa chọn “mọi implementation của `Processor<String>` nhưng không phải `Processor<Integer>`”, Java erasure khiến việc match **generic type argument** theo tên method đơn giản không đáng tin cậy. Cân nhắc explicit bean types, custom annotation, một contract riêng hoặc kiểm tra metadata type phù hợp với Spring `ResolvableType`; viết integration test chứng minh match behavior.

**Không dùng `args(java.lang.String)` như thể nó là một phép chọn overload trên declared parameter type**: `args` xét argument runtime type, trong khi `execution` xét method execution signature. Một target có method khai báo `process(Object)` vẫn có thể nhận String instance tại runtime.

## 13. Truyền tham số JoinPoint và đọc MethodSignature an toàn

Bên trong advice, ngoài `getMethod()` bạn có thể quan sát:

~~~java
Object[] arguments = pjp.getArgs();
Object proxy = pjp.getThis();
Object target = pjp.getTarget();

MethodSignature signature =
        (MethodSignature) pjp.getSignature();

String methodName = signature.getName();
Class<?> declaringType = signature.getDeclaringType();
~~~

Đây là **fragment ở bên trong `@Around`**, chưa có imports và context đầy đủ. Phải hiểu:

- `getArgs()` mô tả arguments của invocation; runtime argument type có thể khác declared signature do inheritance.
- `getThis()` là proxy đang cung cấp AOP invocation.
- `getTarget()` là target object.
- `getDeclaringType()` không mặc nhiên là `target.getClass()` với JDK proxy.
- `getName()` chỉ là tên, không phân biệt overload hoặc generic bridge.
- `MethodSignature.getMethod()` là entry để resolve đúng target method, không phải bằng chứng annotation tồn tại trên implementation.
- Nếu aspect match những join point types khác trong môi trường AspectJ weaving, không được cast mọi signature sang `MethodSignature` một cách vô điều kiện; riêng Spring AOP chỉ xử lý method execution join points.

Khi thay đổi arguments qua `pjp.proceed(Object[])`, cần đúng số lượng và kiểu; không dùng generic erasure làm lý do chuyền sai runtime type. Một bridge có thể cast Object thành String, nên truyền sai type có thể dẫn đến `ClassCastException` trong đường thực thi.

## 14. Pointcut match interface nhưng method chỉ có trong implementation

Ví dụ một service:

~~~java
public interface OrderApi {
    void place();
}

public class OrderService implements OrderApi {
    @Override
    public void place() {
    }

    public void internalReport() {
    }
}
~~~

Hai type trên phải ở **hai file Java riêng** nếu đều public. Đây là fragment mô tả ranh giới, không phải project runnable chính.

Nếu bean được proxy bằng **JDK dynamic proxy** của `OrderApi`, caller không thể gọi `internalReport()` qua `OrderApi` vì method không nằm trong contract proxy. Expression:

~~~text
execution(* demo.OrderService.internalReport(..))
~~~

không thể khiến method đó tự xuất hiện trên JDK proxy interface. Nếu thật sự cần advice method ấy:

- Cân nhắc đưa method vào một interface public phù hợp.
- Hoặc dùng **CGLIB class-based proxy** nếu class/method có thể override.
- Hoặc refactor sang một Spring bean khác với API rõ ràng.

Bridge resolution là vấn đề **method metadata**; nó không thể vượt qua hạn chế method exposure của proxy.

## 15. Self-invocation vẫn bypass Spring AOP

Nếu `TextProcessor.process(String)` gọi một method khác trong cùng target qua `this.helper()`, lời gọi nội bộ đó không tự quay qua proxy nên advice của `helper()` không được áp dụng theo đường đi ấy.

~~~java
public String process(String input) {
    return this.normalize(input);
}

public String normalize(String input) {
    return input.trim();
}
~~~

Đây là **fragment minh họa**, không phải cách thay file `TextProcessor.java` của ví dụ vì còn thiếu annotations/imports. Dù `normalize` có chữ ký rõ ràng và Spring có thể resolve method, lời gọi `this.normalize` trong target không tạo thêm invocation đi qua proxy.

Đừng nhầm hai câu hỏi:

1. **Spring có resolve được method đúng không?** Liên quan bridge, interface, target method và pointcut.
2. **Lời gọi có đi qua proxy không?** Liên quan self-invocation, JDK/CGLIB và bean lifecycle.

Có phương thức được resolve đúng **không bảo đảm advice sẽ chạy** nếu caller bypass proxy.

## 16. Các lỗi phổ biến và hướng xử lý

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `getMethod().getAnnotation(...)` trả null | `getMethod()` đang là interface method | Resolve target method với `AopUtils.getMostSpecificMethod` |
| Chọn được `process(Object)` thay vì `process(String)` | Method từ generic interface hoặc compiler bridge | Dùng `BridgeMethodResolver` và target class |
| Reflection quét thấy hai method cùng tên | Có method implementation và bridge do compiler sinh | Kiểm tra `isBridge()`, `isSynthetic()` và parameter types |
| Advice log hai lần | Nhiều advisors/lần gọi/proxy layers, không nhất thiết do bridge | Đếm proxy invocations, kiểm tra advisor chain |
| Cast Spring proxy sang concrete implementation thất bại | Proxy là JDK dynamic proxy | Inject/use interface; cân nhắc CGLIB khi cần |
| `@annotation` không match ở một cấu hình proxy | Annotation placement hoặc method resolution khác | Test interface/implementation metadata trong context thật |
| Method trên interface không có annotation nhưng implementation có | Raw reflection nhìn nhầm nơi đặt annotation | Resolve most-specific target method |
| Generic type String nhưng reflection trả Object | Type erasure ở generic interface | Xem `getGenericParameterTypes()` và `ResolvableType` khi cần |
| Spring không advice implementation-only method | JDK proxy không expose method ấy | Thêm API vào interface hoặc thay kiến trúc/proxy |
| Spring không advice private/final method | Class-based proxy không override được | Thay entrypoint/interceptable design |
| Advice không chạy khi target tự gọi method | Self-invocation bypass proxy | Tách bean/gọi qua Spring-managed dependency |
| `getDeclaredMethod` thất bại | Dùng erased interface parameter types trên implementation | `AopUtils.getMostSpecificMethod` |
| `ClassCastException` khi gọi generic bridge | Runtime argument không tương thích String | Bảo đảm typed caller/proceed arguments đúng |
| Scan annotation bỏ sót meta-annotation | Raw reflection không dò composed annotation | Cân nhắc `AnnotatedElementUtils` / merged metadata |
| Spring context không tạo AOP proxy | Aspect không là bean, AOP chưa bật hoặc không có advisor phù hợp | Kiểm tra `@Component`, `@Aspect`, `@EnableAspectJAutoProxy` |

## 17. Checklist triển khai Spring Aspect trên interface generic

1. Xác định method được advice nằm trên **interface**, implementation hay cả hai?
2. Bean đã được đăng ký trong Spring ApplicationContext chưa?
3. Aspect đã là Spring bean và AOP infrastructure đã bật chưa?
4. Proxy thực tế là JDK hay CGLIB? Dùng `AopUtils.isJdkDynamicProxy`/`isCglibProxy`.
5. Method được `MethodSignature.getMethod()` khai báo ở class nào?
6. Có bridge method không? Dùng `Method.isBridge()` để biết.
7. Nếu cần annotation implementation, đã resolve `AopUtils.getMostSpecificMethod` theo target class chưa?
8. Nếu candidate là bridge, đã tìm bridged method chưa?
9. Có yêu cầu meta-annotation/hierarchy không? Nếu có, dùng Spring annotation utilities đúng policy.
10. Pointcut có match **một logical business operation** như mong muốn không?
11. Có advisor khác, overload, interface default method hoặc inherited method làm số advice thay đổi không?
12. Method có được công bố trên JDK proxy interface hoặc interceptable bởi CGLIB không?
13. Caller có đi qua proxy thay vì `new`/`this.method()` không?
14. Có integration test kiểm tra cả JDK proxy, CGLIB (nếu được hỗ trợ) và annotation placement quan trọng không?
15. Đã đối chiếu output trên runtime/compiler mà ứng dụng triển khai, thay vì suy đoán reflection order chưa?

## 18. Câu hỏi tự đánh giá

**1. Vì sao `Processor<String>.process(String)` có thể xuất hiện `process(Object)` trên bytecode?**

Vì type erasure biến generic interface method thành erased signature, và compiler có thể sinh bridge để duy trì overriding.

**2. `Method.isBridge()` nói lên điều gì?**

Nó cho biết method được đánh dấu compiler-generated bridge; không có nghĩa đó là một operation nghiệp vụ tách biệt.

**3. Với JDK proxy, `MethodSignature.getMethod()` có luôn là method implementation không?**

Không. Nó có thể là method khai báo trong interface. Cần resolve target method khi muốn metadata implementation.

**4. API Spring nào thường dùng để tìm implementation method từ interface Method?**

`AopUtils.getMostSpecificMethod(method, targetClass)`.

**5. API nào dùng để tìm method gốc khi đang có bridge?**

`BridgeMethodResolver.findBridgedMethod(bridgeMethod)`.

**6. Có cần kiểm tra `isBridge()` trước khi gọi `findBridgedMethod` không?**

Không bắt buộc; API Spring cho phép truyền non-bridge method và có thể trả method ấy.

**7. Annotation chỉ nằm trên implementation thì raw reflection của interface method có thấy không?**

Không. Phải xem concrete method hoặc sử dụng cách tìm annotation theo quy ước Spring phù hợp.

**8. Tại sao đổi JDK proxy sang CGLIB không làm bridge method biến mất?**

Bridge là sản phẩm của Java compiler trên class file, không phải đối tượng chỉ tồn tại do JDK proxy tạo.

**9. Có bridge method thì Spring AOP phải chạy @Around hai lần không?**

Không. Bridge không tự là hai client proxy invocations. Nếu advice chạy hai lần, cần điều tra call path và advisors.

**10. Nếu method chỉ có trên implementation, client JDK proxy gọi nó trực tiếp được không?**

Không, nếu method không thuộc interface mà JDK proxy expose.

**11. Có phải `@annotation` giống hệt `MethodSignature.getMethod().isAnnotationPresent` trong mọi trường hợp?**

Không. Spring method matching và annotation resolution có thể xét target/bridge hierarchy, còn raw reflection chỉ đọc annotation theo chính Method đó.

**12. Resolve đúng bridge có sửa được self-invocation bypass proxy không?**

Không. Method metadata resolution và proxy invocation boundary là hai vấn đề độc lập.

## 19. Tài liệu Spring Framework chính thức

- **Spring Framework — AOP Proxying Mechanisms:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- **Spring Framework — @AspectJ Pointcuts:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html
- **Spring Framework 6.2.x — AopUtils Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/support/AopUtils.html
- **Spring Framework 6.2.x — BridgeMethodResolver Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/BridgeMethodResolver.html
- **Spring Framework — AnnotatedElementUtils Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html
- **Spring Framework — AspectJ-style AOP Support:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html

**Kết luận:** Với generic interfaces trong Spring AOP, **interface method, concrete implementation method và compiler bridge method** có thể đại diện cho cùng một logical operation nhưng chứa metadata khác nhau. Khi advice cần xem annotation hoặc chữ ký method triển khai, hãy xuất phát từ `MethodSignature`, xác định **target class**, dùng **`AopUtils.getMostSpecificMethod`** và **`BridgeMethodResolver.findBridgedMethod`** khi phù hợp. Đồng thời luôn nhớ: Spring AOP là **proxy-based**; dù đã resolve đúng method, advice vẫn chỉ chạy khi invocation thực sự đi qua proxy và advisor match.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-context-arguments-011">Đọc annotation trên đúng phương thức khi đối tượng là proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc annotation trên đúng phương thức khi đối tượng là proxy trong Spring AOP

## 1. Vấn đề cần giải quyết

Khi viết advice trong Spring AOP, ta thường muốn đọc annotation của **phương thức nghiệp vụ thực sự được gọi** để quyết định mức độ audit, nhãn metric, chính sách kiểm tra hoặc cách ghi log.

Ví dụ:

```java
public interface BillingOperations {
    String pay(String id);
}

public class BillingService implements BillingOperations {
    @TrackOperation("payment")
    @Override
    public String pay(String id) {
        return "paid:" + id;
    }
}
```

Trong Spring Framework, caller có thể nhận một **JDK dynamic proxy** chỉ triển khai `BillingOperations`, hoặc một **CGLIB class-based proxy** kế thừa `BillingService`. Nếu một advice viết:

```java
Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
TrackOperation annotation = method.getAnnotation(TrackOperation.class);
```

thì annotation **có thể là `null` dù annotation có thật trên `BillingService.pay()`**: `method` có thể phản ánh method được khai báo ở **interface** khi dùng JDK proxy, trong khi annotation chỉ nằm trên **implementation**.

Đây không phải lỗi của Java annotation, cũng không tự động chứng minh Spring pointcut sai. Nó là sự khác biệt giữa các **method representations** có thể thấy trong một lời gọi Spring proxy AOP.

**Cách xử lý nền tảng trong Spring:**

1. Lấy `Method` từ `MethodSignature`.
2. Xác định **target class thực tế** của Spring bean.
3. Dùng `AopUtils.getMostSpecificMethod(method, targetClass)` để tìm method cụ thể trên implementation (và giải quyết bridge method theo API Spring).
4. Dùng `AnnotatedElementUtils.findMergedAnnotation(...)` khi cần hỗ trợ **meta-annotations, composed annotations và các thuộc tính annotation được hợp nhất**.
5. Nếu annotation vẫn không tìm thấy, xem lại **annotation nằm ở interface hay implementation, nơi đặt annotation, phạm vi pointcut, proxy strategy và Spring version**.

Ví dụ:

```java
Method declared = ((MethodSignature) joinPoint.getSignature()).getMethod();
Class<?> targetType = AopUtils.getTargetClass(joinPoint.getTarget());
Method specific = AopUtils.getMostSpecificMethod(declared, targetType);
TrackOperation operation =
        AnnotatedElementUtils.findMergedAnnotation(specific, TrackOperation.class);
```

Đây là **fragment trong Spring advice**, cần `joinPoint` là `JoinPoint`/`ProceedingJoinPoint`, các imports và annotation `TrackOperation` do ứng dụng khai báo. Phần 8 sẽ có project đầy đủ.

**Giới hạn của bài:** tập trung vào cách xác định **method annotation đúng trong Spring Aspect trên Spring-managed beans được gọi qua Spring AOP proxy**. Các kiến thức AspectJ/weaving chung không phải trọng tâm.

## 2. Ba thực thể rất dễ bị đánh đồng

Khi Spring AOP thực thi advice, cần phân biệt:

| Thành phần | API / ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Proxy** | `joinPoint.getThis()` | Reference Spring AOP proxy mà caller đi qua. Có thể là JDK proxy hoặc CGLIB subclass. |
| **Target** | `joinPoint.getTarget()` | Object nghiệp vụ được proxy ủy quyền tới. |
| **Method của join point signature** | `((MethodSignature) jp.getSignature()).getMethod()` | `java.lang.reflect.Method` mô tả method được báo cho advice; có thể là **interface method**. |
| **Method cụ thể trên target class** | `AopUtils.getMostSpecificMethod(method, targetClass)` | Method triển khai thích hợp để xem annotation của implementation. |
| **Annotation trên method cụ thể** | `AnnotatedElementUtils.findMergedAnnotation(specific, TrackOperation.class)` | Metadata, có thể gồm composed/meta-annotations theo quy tắc tìm kiếm Spring. |

Điều cần nhớ:

```text
Caller
  |
  v
Spring AOP proxy                  <-- getThis()
  |
  | JoinPoint / MethodSignature
  | có thể mô tả BillingOperations.pay(...)
  v
BillingService target             <-- getTarget()
  |
  v
BillingService.pay(...)           <-- most specific method
  |
  +-- @TrackOperation("payment")  <-- annotation đặt trên implementation
```

Trong một JDK proxy, lớp runtime của `getThis()` là generated proxy class, không phải `BillingService`. Nếu dùng `getThis().getClass().getDeclaredMethod(...)` để tìm annotation nghiệp vụ, bạn thường đang **đọc nhầm lớp hạ tầng proxy**.

Trong một CGLIB proxy, `getThis().getClass()` là generated subclass. Lớp này cũng **không phải điểm neo ổn định** để đọc annotation; method annotations không mặc nhiên được sao chép nguyên vẹn lên mọi generated overrides của proxy.

## 3. Vì sao `MethodSignature.getMethod()` không luôn là implementation method?

### 3.1. JDK interface proxy

Khi Spring tạo JDK dynamic proxy cho target implement `BillingOperations`, public API của proxy là các interface mà nó expose. Lời gọi `proxy.pay("P-1")` đi qua method invocation dựa trên interface contract.

Trong advice:

```java
Method signatureMethod =
    ((MethodSignature) jp.getSignature()).getMethod();
```

`signatureMethod.getDeclaringClass()` **có thể là `BillingOperations.class`**. Nếu `@TrackOperation` chỉ được đặt trên `BillingService.pay(String)`, thì:

```java
signatureMethod.getAnnotation(TrackOperation.class)
```

có thể trả về `null`.

**Không suy rằng implementation không có annotation chỉ vì interface `Method` không có.** Annotation trên method của implementation không tự “lan ngược” sang method cùng chữ ký trong Java interface.

### 3.2. CGLIB class proxy

Khi Spring tạo subclass proxy, method được intercept có thể phản ánh target class rõ hơn, nhưng cũng không nên gắn logic vào điều kiện “CGLIB luôn trả một loại Method cố định”. Advisor, bridge methods, generic types và proxy implementation có thể ảnh hưởng representation trong những tình huống cụ thể.

Giải pháp thống nhất cho **cả JDK và CGLIB** là chuẩn hóa về target method bằng API Spring thay vì tự phân nhánh:

```java
Method specific = AopUtils.getMostSpecificMethod(
    ((MethodSignature) jp.getSignature()).getMethod(),
    AopUtils.getTargetClass(jp.getTarget())
);
```

Nếu target không thể implement chữ ký đó hoặc có custom `TargetSource`, Spring có thể trả lại original method; phải kiểm tra nếu ứng dụng có proxy phức tạp.

### 3.3. Phân biệt tên method và đối tượng `Method`

`jp.getSignature().getName()` trả tên method như `"pay"`, nhưng **không đủ** để tìm chính xác annotation trên overload.

Ví dụ class có:

```java
String pay(String id)
String pay(String id, String channel)
```

Chỉ biết tên `"pay"` không xác định được overload nào thực thi. `MethodSignature.getMethod()` mang theo **parameter types**, và `AopUtils.getMostSpecificMethod` dùng thông tin chữ ký đó để đối chiếu method đúng trên target class.

Không nên tự dùng:

```java
target.getClass().getMethod(jp.getSignature().getName());
```

vì câu lệnh tìm method **không tham số**, dễ ném `NoSuchMethodException` hoặc chọn sai khi target có overload. API Spring giúp giảm những lỗi mà logic reflection thủ công thường mắc.

## 4. Chuẩn hóa method bằng `AopUtils.getMostSpecificMethod`

Spring Framework cung cấp API:

```java
public static Method getMostSpecificMethod(
        Method method, Class<?> targetClass);
```

Chữ ký trên là **mô tả API**, không phải chương trình độc lập. `method` có thể là method khai báo ở interface; `targetClass` là type class target thực tế.

Ví dụ:

```java
Method interfaceMethod =
        BillingOperations.class.getMethod("pay", String.class);

Method actual = AopUtils.getMostSpecificMethod(
        interfaceMethod, BillingService.class
);
```

Khi `BillingService` triển khai interface theo cách ví dụ, `actual` là đại diện cho **`BillingService.pay(String)`**.

### 4.1. Vì sao không chỉ dùng `target.getClass().getMethod(...)`?

Một đoạn reflection tự dựng cần xử lý thêm:

- Method **overloading**: phải biết chính xác parameter types.
- Class hierarchy: method có thể kế thừa từ superclass.
- Generic bridge methods: compiler có thể sinh bridge method với signature erasure.
- Annotation đặt trên method implementation/interface/supertype.
- Generated proxy class: không nên lấy `proxy.getClass()` làm nguồn domain metadata.
- Method visibility và accessibility.

`AopUtils.getMostSpecificMethod` kết hợp logic Spring để tìm implementation method tương ứng; **Javadoc Spring 6.1.14 xác nhận API này giải quyết bridge methods** nhằm đọc attributes từ original method definition.

Có thể viết:

```java
Method specific =
        AopUtils.getMostSpecificMethod(signatureMethod, targetClass);
```

thay vì tự dò bằng `getDeclaredMethods()` và so tên chuỗi.

### 4.2. Bridge methods với generics

Ví dụ generics minh họa:

```java
interface Handler<T> {
    void handle(T value);
}

class StringHandler implements Handler<String> {
    @TrackOperation("handle-string")
    public void handle(String value) {
        // ...
    }
}
```

Compiler có thể sinh **bridge method** để bảo toàn polymorphism khi Java type erasure. Nếu reflection lấy bridge method, annotation gắn với method nguồn `handle(String)` có thể không được quan sát theo cách mong muốn khi chỉ dùng `getDeclaredAnnotation` trên bridge.

Trong Spring AOP, `AopUtils.getMostSpecificMethod` là điểm bắt đầu hợp lý để chọn target method **và resolve bridge method**. Khi làm việc bên ngoài AOP có thể dùng `org.springframework.core.BridgeMethodResolver` theo nhu cầu, nhưng đừng thực hiện nhiều lớp resolution thủ công một cách vô ích.

Đừng mặc định mỗi Java compiler/version đều sinh và gắn method annotations lên bridge methods theo cùng cách. Kiểm tra bằng API Spring và integration tests.

## 5. Đọc annotation: `getAnnotation`, `findMergedAnnotation`, hay `@annotation`?

Có ít nhất ba nhu cầu khác nhau:

### 5.1. Chỉ đọc annotation gắn trực tiếp lên đúng method

```java
TrackOperation direct =
        specificMethod.getDeclaredAnnotation(TrackOperation.class);
```

Phù hợp khi policy **cố ý chỉ công nhận annotation đặt trực tiếp** lên implementation method. Nó không tự khám phá meta-annotation và không “merge” thuộc tính từ các composed annotations.

`specificMethod.getAnnotation(TrackOperation.class)` tương tự về mục tiêu trên method được chọn, nhưng dùng API `AnnotatedElement`; đừng kỳ vọng tự phát hiện annotation trên interface hoặc composed annotation.

### 5.2. Cần semantics annotation của Spring, gồm meta-annotations

```java
TrackOperation marker = AnnotatedElementUtils.findMergedAnnotation(
        specificMethod,
        TrackOperation.class
);
```

Spring `AnnotatedElementUtils.findMergedAnnotation` có thể tìm trong **annotation hierarchy** và tổng hợp metadata của composed annotations; hỗ trợ `@AliasFor` theo các quy tắc Spring.

Trường hợp ứng dụng dùng:

```java
@TrackOperation("critical")
public @interface CriticalOperation {}
```

và method được đánh dấu:

```java
@CriticalOperation
public void cancel(String id) { /* ... */ }
```

thì **direct** `getDeclaredAnnotation(TrackOperation.class)` trên method có thể trả `null`, trong khi **merged** annotation search có thể tìm `TrackOperation("critical")` thông qua meta-annotation `@CriticalOperation`. Các fragments này cần annotation type có `@Retention(RUNTIME)` và `@Target` phù hợp; ví dụ hoàn chỉnh sẽ khai báo đầy đủ.

Khi cần đọc alias/override attributes trong composed annotations, nên dùng `AnnotatedElementUtils` hoặc `MergedAnnotations` thay vì tự loop `getAnnotations()` và parse annotation tree.

### 5.3. Nếu mục tiêu đơn giản là chọn method có annotation, có thể bind trực tiếp trong pointcut

Spring `@AspectJ` hỗ trợ:

```java
@Before(
    value = "execution(public * com.example..service.*.*(..))"
          + " && @annotation(audited)",
    argNames = "audited"
)
public void audit(TrackOperation audited) {
    System.out.println(audited.value());
}
```

Đây là **fragment** theo cú pháp annotation binding của Spring. Nếu bạn chỉ cần `TrackOperation` dùng để lọc **join point đang được advice** và lấy giá trị annotation tương ứng, `@annotation(audited)` thường đơn giản hơn tự dùng reflection.

Tuy nhiên, **đừng coi `@annotation` luôn đồng nghĩa `findMergedAnnotation` cho mọi trường hợp composed annotation, interface annotation và proxy strategy**. Đây là hai cơ chế với các quy tắc matching/binding riêng. Với interface/implementation khác nhau, generic bridge, composed annotations, hãy viết test trên Spring version thật.

Nếu advice đã chạy do `execution(...)` nhưng cần **kiểm tra annotation theo một policy fallback đặc biệt**, pipeline `getMostSpecificMethod` + `findMergedAnnotation` là cách kiểm soát điều kiện đọc metadata rõ hơn.

## 6. Lấy target class đúng cách

Trong advice, hai reference quan trọng là:

```java
Object proxy = jp.getThis();
Object target = jp.getTarget();
```

Đối với Spring AOP:

- `getThis()`: Spring proxy.
- `getTarget()`: target đang được advice.

Khi đọc annotation của **implementation method**, ưu tiên target class:

```java
Class<?> targetClass = AopUtils.getTargetClass(jp.getTarget());
```

Với target object bình thường, nó nhận diện class nghiệp vụ. Nếu gọi `jp.getThis().getClass()`, bạn có thể nhận class generated của JDK/CGLIB proxy và không tìm được method annotation cần thiết.

### 6.1. Cách nhận diện proxy

```java
Object bean = context.getBean("billingOperations");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Đây là **fragment chẩn đoán**, cần biến `context`. Kiểm tra proxy style không thay thế được tìm target method, nhưng giúp giải thích vì sao cùng advice có thể thấy method representation khác nhau giữa cấu hình.

### 6.2. Nested proxies và scoped targets

Nếu ứng dụng có scoped proxy, custom `TargetSource`, nhiều lớp proxy hoặc transaction/security advisors, `getTarget()` và `getTargetClass` có thể cần diễn giải theo **lớp target thực sự đang được advice**. Spring còn có `AopProxyUtils.ultimateTargetClass(...)` cho những tình huống nhất định, nhưng một số cách unwrap có thể kích hoạt target creation hoặc không phù hợp với scoped/non-singleton target.

**Không tự động unwrap mọi proxy đến raw object chỉ để đọc annotation.** Ưu tiên API class metadata, dùng Spring proxy utilities có hiểu biết về target source, và kiểm thử đúng ApplicationContext/scopes của hệ thống.

## 7. Method annotations nằm ở interface, implementation hoặc superclass

Một service có thể khai báo chính sách tại nhiều vị trí:

```java
public interface BillingOperations {
    @TrackOperation("interface-policy")
    String pay(String id);
}

public class BillingService implements BillingOperations {
    @Override
    public String pay(String id) {
        return "paid:" + id;
    }
}
```

Đây là **fragments minh họa**, không phải project hoàn chỉnh. Khi method annotation chỉ đặt trên interface:

- `BillingService.class.getMethod(...).getDeclaredAnnotation(...)` trên implementation method có thể trả `null`.
- `BillingOperations.class.getMethod(...).getAnnotation(...)` có thể thấy annotation.
- Spring merged annotation search với **find semantics** có khả năng tìm metadata từ method hierarchy, bao gồm các method tương ứng ở interface theo API và version.
- Nếu codebase cần **ưu tiên annotation implementation, fallback annotation interface**, phải mô tả chính sách rõ ràng và test đúng ordering.

Một mẫu fallback có chủ đích:

```java
Method signatureMethod =
        ((MethodSignature) jp.getSignature()).getMethod();

Method implementationMethod =
        AopUtils.getMostSpecificMethod(
                signatureMethod,
                AopUtils.getTargetClass(jp.getTarget())
        );

TrackOperation metadata = AnnotatedElementUtils.findMergedAnnotation(
        implementationMethod, TrackOperation.class
);

if (metadata == null && !implementationMethod.equals(signatureMethod)) {
    metadata = AnnotatedElementUtils.findMergedAnnotation(
            signatureMethod, TrackOperation.class
    );
}
```

Đây là **fragment đặt trong advice** và không nên sao chép nguyên xi khi có conflicting annotations trên implementation/interface mà chưa định nghĩa precedence. `findMergedAnnotation` đã thực hiện hierarchy search trong nhiều tình huống; fallback minh họa việc **không bỏ quên interface `Method` ban đầu**, chứ không phải khẳng định Spring API luôn cần hai lần tìm.

### 7.1. `@Inherited` không phải giải pháp cho method annotations

Java `@Inherited` chỉ ảnh hưởng cơ chế lookup annotation trên **class inheritance** theo contract của Java, **không làm method annotation tự kế thừa trong mọi override**. Không thêm `@Inherited` vào method-level annotation rồi kỳ vọng `Method.getAnnotation` tự hợp nhất annotation từ interface method.

Nếu cần policy kế thừa qua method hierarchy, hãy dùng API Spring phù hợp như `AnnotatedElementUtils.findMergedAnnotation`, đồng thời xác định và test precedence giữa interface, superclass và implementation.

### 7.2. Composed annotations và `@AliasFor`

Ví dụ nâng cao:

```java
@TrackOperation("default")
public @interface BusinessOperation {
    // Có thể dùng @AliasFor để override thuộc tính TrackOperation.
}
```

Đây là **fragment khái niệm**; muốn override phải khai báo đầy đủ `@AliasFor(annotation=..., attribute=...)`, retention và target. Không thể mặc định rằng mọi property tên giống nhau tự merge như mong muốn: Spring meta-annotation aliasing có quy tắc riêng và có thể báo lỗi khi cấu hình không nhất quán.

Nếu chỉ cần một label cố định cho composed annotation, `@TrackOperation("critical")` trên annotation type thứ hai đã đủ cho ví dụ runnable.

## 8. Ví dụ Java 17+ hoàn chỉnh: JDK proxy, CGLIB và annotation trên implementation

Mục tiêu của ví dụ:

1. Khai báo `BillingOperations` interface **không có method annotation**.
2. Class `BillingService` implement interface, đặt **`@TrackOperation("payment")` trực tiếp trên `pay`**.
3. Đặt **`@CriticalOperation`** (meta-annotated với `@TrackOperation("critical")`) trên `cancel`.
4. Không có annotation trên `ping`.
5. Dùng **một `@Around` Spring advice** chọn method executions của target `BillingOperations`, sau đó resolve most-specific method và merged annotation.
6. Chạy cùng project trong **hai ApplicationContexts độc lập**: JDK proxy và CGLIB proxy. Xác minh annotation trên implementation ở cả hai.
7. Đối chiếu trực tiếp `Method` lấy từ interface với most-specific implementation `Method` mà **không dựa vào generated proxy class name**.

Môi trường cố định: **Java 17+, Spring Framework 6.1.14, AspectJ weaver 1.9.22.1, Maven**. Đây là bộ phiên bản minh họa, **không phải tuyên bố phiên bản mới nhất**.

### 8.1. Cấu trúc project

```text
spring-aop-correct-method-annotation/
├── pom.xml
└── src/main/java/com/example/methodanno/
    ├── TrackOperation.java
    ├── CriticalOperation.java
    ├── BillingOperations.java
    ├── BillingService.java
    ├── InspectAspect.java
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
    <artifactId>spring-aop-correct-method-annotation</artifactId>
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

`spring-context` cung cấp `ApplicationContext` và các Spring AOP dependencies trong cây phụ thuộc của cấu hình ví dụ. `aspectjweaver` cung cấp thư viện để Spring phân tích `@AspectJ` pointcut expressions. **Không cấu hình compile-time/load-time AspectJ weaving**.

### 8.3. `src/main/java/com/example/methodanno/TrackOperation.java`

```java
package com.example.methodanno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface TrackOperation {
    String value();
}
```

Hai target types có chủ đích:

- `METHOD`: cho phép đặt `@TrackOperation("payment")` trực tiếp trên method.
- `ANNOTATION_TYPE`: cho phép đặt `@TrackOperation("critical")` **trên một annotation khác** để tạo meta-annotation.

`RUNTIME` giúp Spring đọc marker tại runtime.

### 8.4. `src/main/java/com/example/methodanno/CriticalOperation.java`

```java
package com.example.methodanno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@TrackOperation("critical")
public @interface CriticalOperation {
}
```

`@CriticalOperation` là **composed annotation** đơn giản. Một method chỉ mang `@CriticalOperation` không cần trực tiếp khai báo `@TrackOperation`; Spring `findMergedAnnotation` có thể nhận diện `TrackOperation` qua meta-annotation.

### 8.5. `src/main/java/com/example/methodanno/BillingOperations.java`

```java
package com.example.methodanno;

public interface BillingOperations {
    String pay(String id);
    String cancel(String id);
    String ping();
}
```

Interface **không có method-level annotation**. Đây là điều kiện giúp kiểm chứng lỗi “đọc nhầm method trên interface” một cách xác định.

### 8.6. `src/main/java/com/example/methodanno/BillingService.java`

```java
package com.example.methodanno;

public class BillingService implements BillingOperations {

    @Override
    @TrackOperation("payment")
    public String pay(String id) {
        System.out.println("[TARGET] pay " + id);
        return "paid:" + id;
    }

    @Override
    @CriticalOperation
    public String cancel(String id) {
        System.out.println("[TARGET] cancel " + id);
        return "cancelled:" + id;
    }

    @Override
    public String ping() {
        System.out.println("[TARGET] ping");
        return "pong";
    }
}
```

Ba methods dùng chung **target class** nhưng annotation khác nhau:

- `pay`: direct `@TrackOperation("payment")`.
- `cancel`: `@CriticalOperation`, gián tiếp mang meta-annotation `TrackOperation("critical")`.
- `ping`: không có annotation thuộc policy này.

### 8.7. `src/main/java/com/example/methodanno/InspectAspect.java`

```java
package com.example.methodanno;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Aspect
public class InspectAspect {

    @Around(
        "target(com.example.methodanno.BillingOperations)"
        + " && execution(public * com.example.methodanno..*.*(..))"
    )
    public Object inspect(ProceedingJoinPoint pjp) throws Throwable {
        Method signatureMethod =
                ((MethodSignature) pjp.getSignature()).getMethod();

        Class<?> targetClass = AopUtils.getTargetClass(pjp.getTarget());

        Method actualMethod = AopUtils.getMostSpecificMethod(
                signatureMethod, targetClass);

        TrackOperation marker = AnnotatedElementUtils.findMergedAnnotation(
                actualMethod, TrackOperation.class);

        String label = (marker == null) ? "none" : marker.value();
        System.out.println("[ASPECT] method="
                + actualMethod.getName() + ", marker=" + label);

        return pjp.proceed();
    }
}
```

Vì advice dùng **`target(BillingOperations)` và `execution(public ...)`**, nó chỉ áp dụng cho public method executions của target phù hợp với interface và package trong demo; **không đòi target method phải có `TrackOperation`**. Đây là lựa chọn có chủ đích để `ping` không có marker vẫn bị inspect và in `marker=none`.

Nếu mục tiêu production là **chỉ chặn annotated operations**, nên cân nhắc `@annotation` trong pointcut (sau khi kiểm tra semantics của annotations/implementation/interface) hoặc sử dụng Spring `Pointcut`/advisor có predicate tương đương. **Đừng nhân rộng một advice reflection trên mọi method của cả ứng dụng** nếu chỉ cần vài annotated methods.

### 8.8. `src/main/java/com/example/methodanno/DemoApp.java`

```java
package com.example.methodanno;

import java.lang.reflect.Method;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;

public class DemoApp {

    @Configuration
    static class Beans {
        @Bean
        BillingOperations billingOperations() {
            return new BillingService();
        }

        @Bean
        InspectAspect inspectAspect() {
            return new InspectAspect();
        }
    }

    @Configuration
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkProxyConfig {
    }

    @Configuration
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibProxyConfig {
    }

    private static void inspectContext(String name, Class<?> config)
            throws NoSuchMethodException {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(config)) {

            BillingOperations service = context.getBean(BillingOperations.class);

            System.out.println("[" + name + "] jdk="
                    + AopUtils.isJdkDynamicProxy(service));
            System.out.println("[" + name + "] cglib="
                    + AopUtils.isCglibProxy(service));

            Method apiMethod =
                    BillingOperations.class.getMethod("pay", String.class);

            Method implementationMethod = AopUtils.getMostSpecificMethod(
                    apiMethod, AopUtils.getTargetClass(service));

            System.out.println("[" + name + "] interfaceDirect="
                    + (apiMethod.getDeclaredAnnotation(TrackOperation.class)
                       != null));

            System.out.println("[" + name + "] implementationDirect="
                    + (implementationMethod.getDeclaredAnnotation(
                            TrackOperation.class) != null));

            System.out.println("[" + name + "] implementationMarker="
                    + AnnotatedElementUtils.findMergedAnnotation(
                            implementationMethod, TrackOperation.class).value());

            System.out.println("[" + name + "] pay="
                    + service.pay("P-1"));

            System.out.println("[" + name + "] cancel="
                    + service.cancel("C-2"));

            System.out.println("[" + name + "] ping="
                    + service.ping());
        }
    }

    public static void main(String[] args) throws NoSuchMethodException {
        inspectContext("JDK", JdkProxyConfig.class);
        inspectContext("CGLIB", CglibProxyConfig.class);
    }
}
```

Hai contexts được tạo **độc lập**. Nếu dùng `@EnableAspectJAutoProxy(proxyTargetClass=false)` và `true` cùng một context, Spring có cơ chế hợp nhất hạ tầng auto-proxy; class-based setting mạnh hơn có thể chi phối và làm sai phép so sánh. Hai contexts tách biệt bảo đảm phép thử thực sự quan sát hai chiến lược.

`apiMethod` trong `DemoApp` được lấy **tường minh từ interface** để chứng minh direct annotation absent; không cần đặt giả định output vào cách `MethodSignature.getMethod()` thể hiện chi tiết trên CGLIB runtime. Đây là cách dựng phép thử đáng tin hơn việc assert string tên generated proxy class.

### 8.9. Chạy chương trình và stdout kỳ vọng

Yêu cầu JDK 17+, Maven và quyền tải dependencies. Từ thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.methodanno.DemoApp
```

Các dòng do chương trình in ra (bỏ qua log Spring/Maven/SLF4J nếu môi trường có):

```text
[JDK] jdk=true
[JDK] cglib=false
[JDK] interfaceDirect=false
[JDK] implementationDirect=true
[JDK] implementationMarker=payment
[ASPECT] method=pay, marker=payment
[TARGET] pay P-1
[JDK] pay=paid:P-1
[ASPECT] method=cancel, marker=critical
[TARGET] cancel C-2
[JDK] cancel=cancelled:C-2
[ASPECT] method=ping, marker=none
[TARGET] ping
[JDK] ping=pong
[CGLIB] jdk=false
[CGLIB] cglib=true
[CGLIB] interfaceDirect=false
[CGLIB] implementationDirect=true
[CGLIB] implementationMarker=payment
[ASPECT] method=pay, marker=payment
[TARGET] pay P-1
[CGLIB] pay=paid:P-1
[ASPECT] method=cancel, marker=critical
[TARGET] cancel C-2
[CGLIB] cancel=cancelled:C-2
[ASPECT] method=ping, marker=none
[TARGET] ping
[CGLIB] ping=pong
```

**Giải thích:**

1. JDK context có interface-based proxy; CGLIB context ép class proxy, trong khi **target implementation và aspect code không đổi**.
2. `interfaceDirect=false` vì chúng ta cố ý không đặt `@TrackOperation` lên `BillingOperations.pay`.
3. `implementationDirect=true` vì annotation trực tiếp nằm trên `BillingService.pay`.
4. `implementationMarker=payment` là giá trị được tìm trên implementation method qua Spring API.
5. Advice đọc `pay` có marker `payment`, dù JDK proxy cho caller làm việc qua interface.
6. Advice đọc `cancel` có marker `critical` nhờ `@CriticalOperation` là meta-annotated.
7. Advice đọc `ping` có marker `none` nhưng vẫn cho phép target method chạy; **không đọc được annotation** không đồng nghĩa **method không được gọi**.
8. Hai contexts cho thấy **một cách normalize target method dùng được cho cả JDK và CGLIB**. Không có nhu cầu tạo một nhánh reflection riêng chỉ vì loại proxy khác nhau.

Lệnh và stdout là kết quả **dự kiến theo code**. Khi tích hợp vào codebase thực, hãy chạy bằng đúng Spring/JDK/proxy configuration của dự án; ví dụ chưa phải một kiểm chứng tự động cho mọi loại `TargetSource`, nested proxy, bridge methods hoặc annotation hierarchy.

## 9. Khi annotation nằm trên interface mà không nằm trên implementation

Bài demo đặt marker ở implementation. Nhưng một codebase khác có thể muốn annotation làm **hợp đồng API của interface**:

```java
public interface BillingOperations {
    @TrackOperation("api-payment")
    String pay(String id);
}
```

và implementation:

```java
@Override
public String pay(String id) {
    return "paid:" + id;
}
```

Hai fragments mô tả một **cấu hình thay thế**, không sửa project runnable ở phần 8.

Trong mô hình này:

- Direct lookup trên interface method có thể thấy marker.
- Direct lookup trên concrete method không nhất thiết thấy marker.
- `AnnotatedElementUtils.findMergedAnnotation(concreteMethod, TrackOperation.class)` có **find semantics** và có khả năng khám phá method hierarchy (bao gồm các interfaces) theo API Spring.
- Việc một **pointcut `@annotation` có match** annotation chỉ ở interface hay không cần test riêng; không đồng nhất annotation hierarchy resolution với AspectJ pointcut matching.
- Nếu interface và implementation mang **hai marker xung đột**, application phải định nghĩa cách ưu tiên, không để chi tiết proxy style quyết định policy ngẫu nhiên.

**Khuyến nghị thực hành:** Chọn một source of truth nhất quán. Chẳng hạn: “annotation policy luôn đặt trên concrete service method”, hoặc “interface API là nguồn khai báo chính và implementation không ghi đè tùy tiện”. Document và kiểm thử quy tắc để tránh silent drift.

## 10. Xử lý annotation hợp nhất và `@AliasFor`

Trong ứng dụng Spring, composed annotations cho phép thiết kế meta-policy:

```java
@TrackOperation("critical")
public @interface CriticalOperation {}
```

Dạng đầy đủ phải thêm `@Target`, `@Retention` đúng như project runnable. Khi `@CriticalOperation` nằm trên method, `getDeclaredAnnotation(TrackOperation.class)` thường không tìm thấy vì method không gắn trực tiếp `TrackOperation`; `findMergedAnnotation` có thể tìm thấy nó.

Nếu muốn composed annotation có attribute để **override** `value` của `TrackOperation`:

```java
@TrackOperation("default")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ClassifiedOperation {

    @org.springframework.core.annotation.AliasFor(
        annotation = TrackOperation.class,
        attribute = "value"
    )
    String value() default "default";
}
```

Đây là **fragment annotation definition**; cần import `java.lang.annotation.*` hoặc import từng type, và `TrackOperation` cho phép `ANNOTATION_TYPE`. Với `@ClassifiedOperation("vip")`, `findMergedAnnotation(method, TrackOperation.class)` có thể tạo synthesized annotation `value="vip"`.

Các lưu ý:

- Chỉ những alias hợp lệ theo Spring `@AliasFor` semantics mới hoạt động; giá trị mặc định và cấu trúc meta-annotation phải nhất quán.
- `getAnnotation` hoặc `getDeclaredAnnotation` thông thường **không tự áp dụng** meta-annotation attribute override của Spring.
- Nếu code cần merged annotation trên class hierarchy, nên kiểm tra rõ `findMergedAnnotation` vs `getMergedAnnotation`: **find semantics** tra cứu rộng hơn theo cấu trúc phù hợp.
- Đừng xây dựng policy audit/security bằng cách đọc `toString()` của annotation hoặc dùng tên runtime proxy class; lấy metadata type-safe theo annotation type.

## 11. Những lỗi phổ biến khi dùng reflection trong Spring advice

| Dấu hiệu | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| `method.getAnnotation(...)=null` nhưng implementation có annotation | `MethodSignature.getMethod()` là interface method trên JDK proxy. | Lấy target class, gọi `AopUtils.getMostSpecificMethod` rồi tìm annotation. |
| Nhìn vào `getThis().getClass()` thấy `jdk.proxy...` | Đang nhìn generated proxy class, không phải target. | Dùng `getTarget()`, `AopUtils.getTargetClass`. |
| Reflection gọi `getMethod(name)` ném lỗi | Method thật có tham số; overloads/parameter types bị bỏ qua. | Dùng `MethodSignature.getMethod()` rồi resolve target method. |
| Annotation trực tiếp không có nhưng meta-annotation có | Dùng `getDeclaredAnnotation` thay vì Spring merged search. | Dùng `AnnotatedElementUtils.findMergedAnnotation` khi cần composed metadata. |
| Generic method có bridge method và metadata không đúng | Đang đọc compiler-generated bridge method. | Dùng `AopUtils.getMostSpecificMethod` / `BridgeMethodResolver` theo API Spring. |
| Annotation chỉ nằm ở interface, tìm trực tiếp trên implementation trả null | Method-level annotation không tự kế thừa như class annotation. | Dùng find semantics hoặc chính sách fallback interface method rõ ràng. |
| Advice chạy trên method không annotate | Pointcut `execution` rộng; reflection trong advice không phải pointcut predicate. | Đặt điều kiện annotation trong pointcut khi phù hợp hoặc tự skip logic trong advice. |
| `@annotation(marker)` không bind như mong đợi | Tên advice parameter/`argNames` không khớp, hoặc annotation placement/proxy style khác giả định. | Kiểm tra naming, type và annotated method; test implementation/interface. |
| Spring bean không bị advice | Aspect chưa thành Spring bean, chưa bật AOP, pointcut không match hoặc caller bypass proxy. | Kiểm tra bean registration, `@EnableAspectJAutoProxy`, `AopUtils`, invocation path. |
| CGLIB proxy không intercept method `final` | CGLIB không override được method đó. | Điều chỉnh public method contract hoặc proxy strategy phù hợp. |
| Có cùng tên method ở nhiều overloads, đọc nhầm annotation | Sử dụng method name string thay vì complete method signature. | Giữ `Method` và parameter types trong resolution. |
| Class bị move/rename và expression cũ không match | String pointcut hardcode old package/class. | Dùng stable named pointcut/annotation contract + behavior tests. |
| `@Inherited` trên annotation method không giúp | Java `@Inherited` không áp dụng phương thức theo cách giả định. | Dùng hierarchy-aware Spring annotation utility, thiết kế quy ước placement. |
| Đọc annotation bằng `proxy.getClass().getDeclaredMethods()` | Generated proxy overrides không phải source of truth. | Resolve target class/method trước. |

## 12. Chẩn đoán theo trình tự để tìm “đúng method”

Một quy trình có thể áp dụng khi Spring advice đã chạy nhưng annotation trả `null`:

1. **Xác nhận proxy thực:** Bean được Spring tạo/cung cấp, `AopUtils.isAopProxy(bean)` ra sao?
2. **Xác định method trong advice:** `((MethodSignature) jp.getSignature()).getMethod()`, class khai báo và parameter types của method.
3. **Xác định target thực:** `jp.getTarget()`, `AopUtils.getTargetClass(...)`; không lấy class generated từ `jp.getThis()` làm business class.
4. **Tìm most-specific method:** `AopUtils.getMostSpecificMethod(signatureMethod,targetClass)`.
5. **Kiểm tra placement:** annotation đặt trên concrete method, interface method, parent method hay meta-annotation?
6. **Chọn lookup semantics:** `getDeclaredAnnotation` nếu cần direct-only; `findMergedAnnotation` nếu cần hierarchy/composition.
7. **Xử lý generic bridge methods:** API Spring giúp giải quyết nhiều trường hợp; test các generic interfaces/overrides quan trọng.
8. **Xem lại pointcut:** `@annotation` dùng matcher của pointcut, không nhất thiết giống hoàn toàn custom reflection policy.
9. **Kiểm tra method call boundary:** self-invocation hoặc target object tự `new` không đi qua Spring proxy, nên advice không chạy để đọc metadata.
10. **Đưa các trường hợp vào integration test** thay vì chỉ debug bằng class names từ stdout.

Chỉ cần có `isAopProxy=true` **chưa đủ**: bean có thể được proxy vì transaction, cache hoặc advisor khác, trong khi pointcut audit của bạn không match method cần quan sát.

## 13. Checklist cho integration tests

Một bộ test đủ tin cậy nên có các cases:

| Case | Kết quả mong muốn |
| --- | --- |
| Annotation trực tiếp trên implementation, JDK proxy | Đọc ra metadata implementation thông qua most-specific method. |
| Annotation trực tiếp trên implementation, CGLIB proxy | Đọc ra metadata nhất quán với JDK. |
| Annotation chỉ trên interface method | Đọc ra metadata theo policy hierarchy/fallback đã document. |
| Annotation ở superclass method hoặc override | Đọc đúng precedence được ứng dụng quy định. |
| Method có composed annotation | `findMergedAnnotation` đọc ra annotation hợp nhất và attributes mong đợi. |
| Generic override có bridge method | Metadata đúng trên original method, không đọc nhầm compiler-generated method. |
| Method không annotate | Trả null hoặc “none” đúng ý đồ, không gây lỗi. |
| Hai overloads cùng tên, khác annotations | Không nhầm method/parameter types. |
| Caller bypass proxy bằng `this` hoặc `new` | Spring advice không chạy theo proxy AOP thông thường. |
| Đổi proxy style khi refactor | Annotation lookup vẫn đọc đúng với target type thật. |

Một test tốt không chỉ assert `method.getName()`, mà phải assert **marker.value()** trên method cụ thể, kèm đúng số lần advice/target chạy. Nếu annotation điều khiển security/audit/transaction policy, hãy test cả **trường hợp không đủ điều kiện** và exception paths.

## 14. Nên dùng reflection trong advice hay binding trực tiếp `@annotation`?

**Dùng `@annotation` binding khi:**

- Policy phụ thuộc đúng annotation trên method mà Spring pointcut engine match.
- Không cần fallback tùy chỉnh từ implementation sang interface hoặc meta-annotation resolution khác.
- Muốn code advice ngắn, type-safe, rõ ý định.
- Có integration test bao phủ JDK/CGLIB và annotation placement thật.

**Dùng method resolution + Spring annotation utilities khi:**

- Advice được chọn rộng bằng `execution/target`, nhưng phải đọc marker để phân loại hành vi.
- MethodSignature có thể là interface method còn marker ở concrete implementation.
- Code cần đọc **composed/meta-annotations** với `@AliasFor`.
- Cần chính sách rõ về method hierarchy, overrides hoặc generic bridge methods.
- Muốn nhất quán khi thay đổi proxy style mà không dùng generated proxy classes.

**Không nên lạm dụng reflection** trong mỗi invocation nếu policy có thể chọn trực tiếp bằng một pointcut tĩnh/annotation có ý nghĩa. Nếu thực sự cần đọc method metadata thường xuyên, có thể cache theo **method/target class hoặc resolved method** với chiến lược thread-safe phù hợp. Chỉ cache immutable metadata, không cache `JoinPoint`/request-specific state trong singleton aspect; cân nhắc classloader và lifecycle khi ứng dụng hot-reload nhiều contexts.

## 15. Những ranh giới vẫn giữ nguyên của Spring AOP

Dù resolve đúng annotation, Spring proxy AOP vẫn có các giới hạn cơ bản:

- **Spring quản lý bean và có advisor áp dụng:** đối tượng `new` ngoài container không tự được Spring AOP xử lý.
- **Caller đi qua proxy:** lời gọi `this.method()` nội bộ trên target không quay lại proxy.
- **JDK proxy:** lời gọi đi qua contract của proxied interfaces; không thể cast JDK proxy về mọi concrete implementation.
- **CGLIB proxy:** class `final` không subclass được; method `final/private` không được advice bằng override theo mô hình thông thường.
- **Thời điểm khởi tạo:** constructor/init callback trên raw target không giống lời gọi nghiệp vụ bình thường sau khi proxy được trao cho caller.
- **Context/scope:** creator hoạt động trong context; scoped proxies/custom target sources có lớp indirection cần kiểm thử riêng.
- **Annotation placement:** Java reflection `@Inherited` không tự giải quyết method hierarchy; Spring annotation search có semantics và giới hạn riêng.

Đọc đúng metadata **không thay thế thiết kế call boundary và pointcut**. Một annotation rất đúng trên implementation vẫn không tạo advice cho self-invocation mà proxy không nhìn thấy.

### Tài liệu Spring Framework chính thức

- **AopUtils.getMostSpecificMethod — Spring Framework 6.1.14 Javadoc:** https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/support/AopUtils.html
- **AnnotatedElementUtils — Spring annotation hierarchy, composed annotations:** https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/annotation/AnnotatedElementUtils.html
- **BridgeMethodResolver — Spring Framework Javadoc:** https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/BridgeMethodResolver.html
- **Declaring Advice — annotation binding with `@annotation`:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- **Declaring a Pointcut — `@annotation`, `this`, `target`:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- **Proxying Mechanisms — JDK/CGLIB and self-invocation:** https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận:** Khi một object là Spring AOP proxy, `MethodSignature.getMethod()` không nhất thiết là **phương thức triển khai mang annotation nghiệp vụ**. Để đọc đúng trong Spring Aspect, hãy xác định **target class**, chuẩn hóa sang **most-specific method** bằng `AopUtils.getMostSpecificMethod`, rồi dùng **Spring merged annotation utilities** nếu cần meta-annotation hoặc hierarchy search. Kiểm thử trên cả JDK và CGLIB proxy giúp tránh những lỗi “annotation có trên source nhưng advice đọc ra null”.


</details>

- [Quay lại đầu trang](#back-to-top)
