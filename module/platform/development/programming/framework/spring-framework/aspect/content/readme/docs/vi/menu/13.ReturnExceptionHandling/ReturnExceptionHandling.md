<a id="back-to-top"></a>

# Kết quả và ngoại lệ

## Menu
- [Giữ hợp đồng trả về khi advice bọc phương thức đích](#return-exception-handling-001)
- [Kết quả null và phương thức trả về kiểu nguyên thủy](#return-exception-handling-002)
- [Checked exception, unchecked exception và chữ ký proxy interface](#return-exception-handling-003)
- [Ảnh hưởng của ngoại lệ do chính advice phát sinh](#return-exception-handling-004)
- [Phân biệt ngoại lệ từ phương thức đích và từ advice khác](#return-exception-handling-005)
- [Đóng tài nguyên và dọn ngữ cảnh trong mọi nhánh kết thúc](#return-exception-handling-006)
- [Không dùng @AfterThrowing như bằng chứng mọi lỗi đều đã được xử lý](#return-exception-handling-007)
- [Kiểm thử việc thay đổi kết quả hoặc ánh xạ ngoại lệ bằng @Around](#return-exception-handling-008)
- [Phân biệt phương thức đã trả Future/Publisher và công việc đã hoàn tất](#return-exception-handling-009)

## <a id="return-exception-handling-001">Giữ hợp đồng trả về khi advice bọc phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ hợp đồng trả về khi advice bọc phương thức đích trong Spring AOP

## 1. Vấn đề: around advice có thể thay đổi thứ caller nhận được

Trong **Spring AOP**, một Spring-managed bean có thể được bọc bằng JDK dynamic proxy hoặc CGLIB proxy để các advice thực thi khi caller gọi một method qua proxy. Với `@Around`, advice không chỉ được làm việc trước/sau method mà còn **quyết định có chuyển tiếp lời gọi, trả giá trị nào hoặc ném exception nào**.

Điều này đặt ra trách nhiệm rất rõ: **nếu mục tiêu của aspect chỉ là logging, tracing, metrics hay quan sát kết quả, phải giữ nguyên hợp đồng của method đích**. Không được vô ý làm mất giá trị trả về, trả sai kiểu, biến kết quả thành `null`, đổi semantic của `Optional` hoặc nuốt exception.

Quy tắc quan trọng nhất:

~~~java
@Around("execution(* demo.CatalogService.*(..))")
public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
    // Thực hiện hành vi trước khi vào target nếu cần.
    Object result = pjp.proceed();
    // Chỉ quan sát result, không thay đổi reference nếu không được yêu cầu.
    return result;
}
~~~

Đoạn này là **method advice minh họa**: phải đặt trong Spring bean `@Aspect`, có import `org.aspectj.lang.ProceedingJoinPoint` và `org.aspectj.lang.annotation.Around`, cùng bean target và pointcut hợp lệ. Nó không phải một chương trình Java độc lập.

**Vì sao cần trả `Object`?** Theo tài liệu Spring Framework, `@Around` nên khai báo kiểu trả về `Object`; giá trị advice trả về sẽ là giá trị mà **caller của method đích nhìn thấy**. Thông thường, hãy `return pjp.proceed();` ngay cả khi method đích là `void`. Nếu khai báo chính method `@Around` trả `void`, Spring sẽ đưa `null` trở lại interceptor chain, làm mất return value của method đích; với method trả primitive thì `null` còn có thể dẫn đến lỗi invocation.

Nội dung tập trung **Spring Aspect trong Spring Framework**, không chuyển sang lý thuyết AspectJ weaving tổng quát.

## 2. Hợp đồng trả về gồm những gì?

Trong Java, “hợp đồng trả về” không chỉ là một từ khóa `return`. Đối với caller sử dụng một Spring AOP proxy, cần bảo toàn các thuộc tính có ý nghĩa:

| Thành phần hợp đồng | Ví dụ method | Điều advice quan sát thuần túy phải giữ |
| --- | --- | --- |
| **Kiểu khai báo** | `String label(...)` | Caller tiếp tục nhận đối tượng `String` hoặc `null` nếu method contract cho phép |
| **Giá trị** | `int quantity()` | Giữ đúng giá trị số, không vô tình trả `0` hay giá trị khác |
| **Primitive không-null** | `boolean enabled()` | Không trả `null` hoặc wrapper sai kiểu cho primitive |
| **Reference identity** | `Customer findById(...)` | Không thay object khác khi requirement chỉ là quan sát; Spring có một số xử lý đặc biệt khi target trả chính `this` |
| **Nullability** | `String nullable()` | Nếu target trả `null` hợp lệ, advice không tự biến thành chuỗi `"null"` |
| **Container kết quả** | `Optional<String> lookup()` | Giữ `Optional.empty()` là empty, không tự biến thành `null` hoặc một `Optional` khác |
| **Method không có giá trị** | `void ping()` | Target phải được gọi nếu không có requirement short-circuit; không cố ép một giá trị hữu hình cho caller |
| **Exception** | `String fail()` | Nếu target ném lỗi, advice quan sát không được âm thầm trả fallback thành công |
| **Generic contract** | `List<Order> findAll()` | Không trả `List` có element không tương thích dù kiểu raw `List` có vẻ hợp lệ |
| **Lazy/async return** | `CompletableFuture<Result>` hoặc reactive type | Không thay wrapper hoặc ngầm block/subscribe ngoài yêu cầu; completion xảy ra ngoài thời điểm method return |

Ví dụ caller có thể viết:

~~~java
String code = service.label("ada");
int quantity = service.quantity();
Optional<String> maybe = service.search(false);
~~~

Nếu advice bọc các method này nhưng đổi `String` thành `Integer`, trả `null` cho `int`, hoặc biến `Optional.empty()` thành `null`, code phía caller bị phá vỡ dù **business target vẫn chạy bình thường**.

**Giữ hợp đồng** không có nghĩa Spring buộc `@Around` chỉ được quan sát. Spring **cho phép** advice chủ động trả giá trị thay thế; việc đó cần là requirement được thiết kế, document và test, không phải side effect ngoài ý muốn của logging.

## 3. Đi qua chuỗi proxy: ai quyết định return value?

~~~text
Caller: String value = service.label("ada")
                         |
                         v
                 Spring AOP proxy
                         |
                         v
           @Around advice: before logic
                         |
                    pjp.proceed()
                         |
                         v
           Các interceptor phía trong (nếu có)
                         |
                         v
              Target.label("ada")
                         |
                         | return "ADA"
                         v
              interceptor chain
                         |
                         v
           @Around advice nhận Object "ADA"
                         |
                  return result
                         |
                         v
              Caller nhìn thấy "ADA"
~~~

Nếu advice viết:

~~~java
Object result = pjp.proceed();
return result;
~~~

thì nó tiếp tục chain, nhận object result (hoặc `null`), rồi trả lại cho proxy. Với target trả primitive `int`, giá trị trên đường đi của AOP interceptor được biểu diễn dưới dạng wrapper phù hợp, ví dụ `Integer`, trước khi proxy giao lại giá trị cho caller.

Nếu advice viết:

~~~java
pjp.proceed();
return null;
~~~

thì target **có thể vẫn chạy**, nhưng giá trị return từ target **đã bị advice vứt bỏ**. Caller nhìn thấy `null` (nếu return type là reference) hoặc gặp lỗi nếu method khai báo primitive. Đây là một bug rất khác với việc quên `proceed()`: trong trường hợp này side effect của target vẫn xảy ra, chỉ giá trị return bị mất.

Nếu advice viết:

~~~java
return "fixed-result";
~~~

mà **không gọi `proceed()`**, target có thể hoàn toàn không chạy. Điều này hợp lệ về khả năng kỹ thuật của `@Around` (ví dụ cache hit) nhưng làm thay đổi hợp đồng và side effects, nên chỉ đúng khi được thiết kế rõ.

## 4. Phân biệt @Before, @AfterReturning và @Around

Khi chỉ muốn **ghi nhận kết quả trả về** mà không thay đổi kết quả, dùng `@AfterReturning` thường an toàn và ít quyền can thiệp hơn `@Around`.

~~~java
@AfterReturning(
    pointcut = "execution(String demo.CatalogService.label(..))",
    returning = "result",
    argNames = "result"
)
public void observe(String result) {
    System.out.println("OBSERVED: " + result);
}
~~~

Advice này nhận return value khi target method **hoàn thành bình thường**. `@AfterReturning` **không thể trả một object reference hoàn toàn khác để thay return value** của caller. Nó không có `proceed()` và theo thông lệ khai báo `void`.

Có một ngoại lệ khái niệm cần hiểu: nếu object trả về **mutable**, Java code trong `@AfterReturning` vẫn có thể chỉnh sửa nội dung của chính object đó; điều này là một side effect mà caller có thể thấy. **Không thể thay reference** khác với **không thể mutate object**. Nếu yêu cầu chỉ quan sát, không sửa mutable return object.

`@Before` chỉ đủ khi cần xử lý **trước** method, không cần biết result. `@AfterThrowing` giúp quan sát exception mà target ném. `@After` (after-finally) dùng khi cần xử lý lúc hoàn thành kể cả success/error. **Chỉ chọn `@Around`** khi cần state đi xuyên trước/sau trong cùng invocation, đổi arguments/return, short-circuit hoặc kiểm soát chuỗi method execution theo yêu cầu.

Lưu ý: bản thân một advice loại đơn giản vẫn có thể ném exception khiến invocation bị fail; “ít quyền can thiệp” giúp giảm bề mặt lỗi, không thay thế việc thiết kế error policy cho logging/metrics.

## 5. Ví dụ Java 17+: một around advice giữ nguyên nhiều kiểu kết quả

Project Spring Framework thuần này dùng `@Around` chung cho các method của `CatalogService` nhưng **không thay đổi giá trị**. Nó kiểm chứng đầy đủ:

1. `String` trả bình thường;
2. `int` primitive;
3. `boolean` primitive;
4. `Optional<String>` có và không có giá trị;
5. `void` method;
6. Reference `null` hợp lệ;
7. Exception từ target truyền nguyên loại và message tới caller.

Chương trình có assertion tổng hợp và bộ đếm advice, không chỉ in chữ “PASS” theo mặc định.

Cấu trúc file:

~~~text
spring-aop-return-contract/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── App.java
    │   ├── CatalogService.java
    │   └── PreserveResultAspect.java
    └── test/java/demo/
        └── ReturnContractTest.java
~~~

Sử dụng Java 17+, Maven 3.9+, Spring Framework **6.2.0**, AspectJ Weaver **1.9.22** và JUnit Jupiter **5.11.0**. Phiên bản được khóa cho ví dụ, không khẳng định mới nhất. Không dùng Spring Boot hay AspectJ bytecode weaving.

### 5.1. Maven pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-return-contract</artifactId>
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

`spring-context` cung cấp `AnnotationConfigApplicationContext` và Java configuration. `spring-aop` chứa proxy/advisor support. `aspectjweaver` cung cấp các kiểu thư viện giúp Spring hiểu `@AspectJ` pointcut; chỉ thêm dependency này **không kích hoạt weaving**. `junit-jupiter`/Surefire dùng để chạy integration tests đơn giản trong cùng project.

### 5.2. Target với các kiểu return khác nhau

Tạo `src/main/java/demo/CatalogService.java`:

~~~java
package demo;

import java.util.Locale;
import java.util.Optional;

public class CatalogService {
    public String label(String input) {
        System.out.println("TARGET: label " + input);
        return input.toUpperCase(Locale.ROOT);
    }

    public int quantity() {
        System.out.println("TARGET: quantity");
        return 7;
    }

    public boolean enabled() {
        System.out.println("TARGET: enabled");
        return true;
    }

    public Optional<String> search(boolean exists) {
        System.out.println("TARGET: search " + exists);
        return exists ? Optional.of("found") : Optional.empty();
    }

    public void ping() {
        System.out.println("TARGET: ping");
    }

    public String nullable() {
        System.out.println("TARGET: nullable");
        return null;
    }

    public String fail() {
        System.out.println("TARGET: fail");
        throw new IllegalStateException("boom");
    }
}
~~~

Class này không `final`; các method đều `public` và không `final`, giúp CGLIB tạo class-based proxy và intercept. `search(true)` trả `Optional.of("found")`; `search(false)` trả `Optional.empty()`; đây là hai kết quả hợp lệ **khác `null`**. `nullable()` được phép trả `null` theo thiết kế minh họa; `quantity()` và `enabled()` là primitives nên **không thể có giá trị Java `null`**.

### 5.3. Aspect giữ giá trị và không nuốt exception

Tạo `src/main/java/demo/PreserveResultAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class PreserveResultAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Around("execution(* demo.CatalogService.*(..))")
    public Object preserve(ProceedingJoinPoint point) throws Throwable {
        String method =
                ((MethodSignature) point.getSignature()).getName();

        System.out.println("AROUND: enter " + method);
        try {
            Object result = point.proceed();
            System.out.println("AROUND: return " + method);
            return result;
        } catch (Throwable error) {
            System.out.println(
                    "AROUND: throw " + method
                    + " " + error.getClass().getSimpleName());
            throw error;
        } finally {
            calls.incrementAndGet();
        }
    }

    public int calls() {
        return calls.get();
    }
}
~~~

Điểm thiết kế:

- `@Aspect` chỉ nhận diện class chứa advice; Spring vẫn cần đăng ký nó làm **bean**.
- `@Around` có **kiểu trả về `Object`** và `throws Throwable` như API `ProceedingJoinPoint.proceed()`.
- `Object result = point.proceed(); return result;` giữ nguyên reference/kết quả mà inner interceptor chain trả về, không ép kiểu thủ công sang `String` hay wrapper cụ thể.
- Với `int` và `boolean`, Spring AOP xử lý boxing/unboxing ở ranh giới interceptor/proxy; advice chỉ cần trả đúng object nhận được. **Không** tự trả `null` hay chuỗi `"7"` thay `Integer`.
- Với `void`, return từ `proceed()` có thể là `null` ở tầng interceptor; `return result;` vẫn là cách giữ semantics của void method. Không biến method advice thành `void`.
- Với `Optional.empty()` hoặc `null` từ target, reference/result được chuyển tiếp như đang có, không “chuẩn hóa” ngoài yêu cầu.
- Khối `catch(Throwable error)` ở đây chỉ **in marker và `throw error` ngay**; nó không làm mất loại exception gốc. Nếu không cần log loại lỗi, có thể bỏ catch và giữ `try/finally`.
- `finally` tăng `calls` kể cả khi target ném exception. Counter này đo **số lần advice được gọi**, không phải số success.
- Pointcut chọn các method trên `CatalogService`, ví dụ cố tình gọi mỗi method qua Spring bean proxy. Không thử bắt constructors, field access hay object `new` bên ngoài container.

Nếu logger có thể ném exception (ví dụ lỗi serialize/log sink), production cần error policy để logging không vô tình đổi business outcome; code in `System.out` chỉ phù hợp ví dụ tối thiểu.

### 5.4. Spring configuration và main method

Tạo `src/main/java/demo/App.java`:

~~~java
package demo;

import java.util.Optional;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public CatalogService catalogService() {
        return new CatalogService();
    }

    @Bean
    public PreserveResultAspect preserveResultAspect() {
        return new PreserveResultAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            CatalogService service = context.getBean(CatalogService.class);
            PreserveResultAspect aspect =
                    context.getBean(PreserveResultAspect.class);

            boolean proxy = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxy);

            String label = service.label("ada");
            System.out.println("CALLER_LABEL: " + label);

            int quantity = service.quantity();
            System.out.println("CALLER_QUANTITY: " + quantity);

            boolean enabled = service.enabled();
            System.out.println("CALLER_ENABLED: " + enabled);

            Optional<String> present = service.search(true);
            System.out.println("CALLER_PRESENT: " + present);

            Optional<String> absent = service.search(false);
            System.out.println("CALLER_ABSENT: " + absent);

            service.ping();
            System.out.println("CALLER_PING: complete");

            String nullable = service.nullable();
            System.out.println("CALLER_NULL: " + (nullable == null));

            boolean caught = false;
            try {
                service.fail();
            } catch (IllegalStateException error) {
                caught = "boom".equals(error.getMessage());
                System.out.println("CALLER_EXCEPTION: " + error.getMessage());
            }

            int count = aspect.calls();
            System.out.println("ADVICE_COUNT: " + count);

            if (!proxy
                    || !"ADA".equals(label)
                    || quantity != 7
                    || !enabled
                    || !Optional.of("found").equals(present)
                    || !Optional.empty().equals(absent)
                    || nullable != null
                    || !caught
                    || count != 8) {
                throw new IllegalStateException(
                        "Return contract was not preserved");
            }

            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Tại sao đây là phép thử thực sự của Spring AOP?

1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật auto-proxy creator và class-based proxy cho bean phù hợp. `CatalogService` không cần interface trong ví dụ.
2. `@Bean catalogService()` trả instance cho Spring quản lý; caller sử dụng bean từ `ApplicationContext` thay vì gọi `new CatalogService()` ở ngoài.
3. `@Bean preserveResultAspect()` đăng ký aspect trong cùng context, để Spring nhận advisor `@Around`.
4. `AopUtils.isAopProxy(service)` xác nhận bean reference thực sự là AOP proxy, không chỉ là target được gọi trực tiếp.
5. Tám lần gọi qua proxy: `label`, `quantity`, `enabled`, `search(true)`, `search(false)`, `ping`, `nullable`, `fail`. Do đó `ADVICE_COUNT` dự kiến bằng **8**, kể cả lần `fail` kết thúc bằng exception.
6. Caller nhận **các kiểu và giá trị đúng**: `String`, `int`, `boolean`, `Optional` có/không có giá trị, `void`, `null` reference và `IllegalStateException`.
7. Condition cuối kiểm tra cả proxy, các giá trị, kiểu quan sát qua Java assignment và exception, rồi mới in `CHECK: PASS`. Nếu `@Around` lỡ trả `null` từ `quantity`, chương trình sẽ không vượt qua được lệnh Java assignment vào `int`.

### 5.5. Chạy Maven và đối chiếu stdout

Chạy trong thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng `System.out.println` của ứng dụng, theo thứ tự:

~~~text
PROXY: true
AROUND: enter label
TARGET: label ada
AROUND: return label
CALLER_LABEL: ADA
AROUND: enter quantity
TARGET: quantity
AROUND: return quantity
CALLER_QUANTITY: 7
AROUND: enter enabled
TARGET: enabled
AROUND: return enabled
CALLER_ENABLED: true
AROUND: enter search
TARGET: search true
AROUND: return search
CALLER_PRESENT: Optional[found]
AROUND: enter search
TARGET: search false
AROUND: return search
CALLER_ABSENT: Optional.empty
AROUND: enter ping
TARGET: ping
AROUND: return ping
CALLER_PING: complete
AROUND: enter nullable
TARGET: nullable
AROUND: return nullable
CALLER_NULL: true
AROUND: enter fail
TARGET: fail
AROUND: throw fail IllegalStateException
CALLER_EXCEPTION: boom
ADVICE_COUNT: 8
CHECK: PASS
~~~

Đây là **stdout mong đợi theo source**, không phải claim đã thực thi Maven trên máy của người học. Maven/SLF4J có thể sinh dòng khác. `CHECK: PASS` chỉ xảy ra khi mọi điều kiện trong code được thỏa mãn.

### 5.6. Integration tests bằng JUnit Jupiter

Tạo `src/test/java/demo/ReturnContractTest.java`:

~~~java
package demo;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnContractTest {
    @Test
    void preservesReferencePrimitivesVoidOptionalNullAndException() {
        try (var context =
                new AnnotationConfigApplicationContext(App.class)) {
            CatalogService service = context.getBean(CatalogService.class);
            PreserveResultAspect aspect =
                    context.getBean(PreserveResultAspect.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("ADA", service.label("ada"));
            assertEquals(7, service.quantity());
            assertTrue(service.enabled());
            assertEquals(Optional.of("found"), service.search(true));
            assertEquals(Optional.empty(), service.search(false));

            service.ping();
            assertNull(service.nullable());

            IllegalStateException error = assertThrows(
                    IllegalStateException.class, service::fail);
            assertEquals("boom", error.getMessage());

            assertEquals(8, aspect.calls());
        }
    }

    @Test
    void plainObjectOutsideSpringIsNotAutoProxied() {
        CatalogService raw = new CatalogService();
        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("ADA", raw.label("ada"));
    }
}
~~~

Lệnh chạy:

~~~bash
mvn clean test
~~~

Kết quả mong đợi trong báo cáo Surefire nếu chạy thành công:

~~~text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
~~~

JUnit kiểm tra trực tiếp **contract quan sát bởi caller**. Chỉ thấy bean là proxy hoặc advice có log chưa đủ: một advice có thể chạy nhưng vô tình trả giá trị sai. Mỗi assert tương ứng một nhóm return behavior, gồm cả thrown exit.

## 6. Lỗi nghiêm trọng: @Around khai báo void làm mất return value

Ví dụ **sai** cho một method `String`:

~~~java
@Around("execution(String demo.CatalogService.label(..))")
public void wrong(ProceedingJoinPoint point) throws Throwable {
    point.proceed();
}
~~~

Method advice trả `void` khiến Spring dùng `null` làm result của advice; caller của `String label(...)` có thể nhận `null` dù target đã trả `"ADA"`. Nếu pointcut dùng để bọc method primitive `int quantity()`, `null` không thể trả như một `int`: Spring AOP có thể ném `AopInvocationException` do null không tương thích primitive return type.

**Sửa đúng:**

~~~java
@Around("execution(String demo.CatalogService.label(..))")
public Object correct(ProceedingJoinPoint point) throws Throwable {
    return point.proceed();
}
~~~

Hai đoạn này là những **phương án minh họa riêng**, không nên thêm vào project chạy ở phần 5 vốn đã có một `@Around` trên `CatalogService`. Tránh để advice sai hoạt động cùng advice đúng khi muốn kiểm tra output chuẩn.

Theo Spring docs, `@Around` nên khai báo **`Object`** làm return type kể cả khi target method `void`. Với `void` target, `return point.proceed()` vẫn là idiom giữ nguyên chain; không cần đặc biệt viết `return null` nếu không có yêu cầu thay behavior.

## 7. Lỗi khi bỏ return, thay sai reference hoặc sai kiểu

### 7.1. Target chạy nhưng result bị vứt đi

~~~java
@Around("execution(* demo.CatalogService.label(..))")
public Object broken(ProceedingJoinPoint point) throws Throwable {
    point.proceed();  // Target vẫn có thể đã chạy.
    return null;      // Nhưng kết quả từ target đã bị bỏ.
}
~~~

Đối với method trả `String`, caller có thể nhận `null` mà không thấy exception tại advice. Với primitive, proxy không thể đưa `null` trở lại như `int` hoặc `boolean`. Cách sửa: **`return point.proceed();`**.

### 7.2. Thay reference khác dù không được yêu cầu

~~~java
@Around("execution(* demo.CatalogService.label(..))")
public Object replaces(ProceedingJoinPoint point) throws Throwable {
    point.proceed();
    return "FORCED";
}
~~~

Dù kiểu `String` hợp lệ, giá trị `"FORCED"` không còn là kết quả của target. Nếu nghiệp vụ không yêu cầu transform, đây là bug semantics. Nếu ứng dụng chủ động cần cache/fallback/masking ở boundary, thay return value có thể hợp lệ, nhưng phải document và kiểm thử tương thích hợp đồng.

### 7.3. Sai kiểu object

~~~java
@Around("execution(* demo.CatalogService.label(..))")
public Object wrongType(ProceedingJoinPoint point) throws Throwable {
    point.proceed();
    return Integer.valueOf(42);
}
~~~

Target khai báo `String label(...)` mà advice trả `Integer`. `@Around` trả `Object` không làm method interface/concrete class của caller được phép trả bất kỳ kiểu nào. Ở ranh giới proxy/caller, có thể xuất hiện `ClassCastException` hoặc lỗi chuyển kiểu/invocation tương ứng. Đừng dựa vào exact exception message giữa JDK/CGLIB/configurations; **hành vi trả sai type là không hợp lệ**.

### 7.4. Trả null cho primitive

~~~java
@Around("execution(int demo.CatalogService.quantity())")
public Object nullPrimitive(ProceedingJoinPoint point) throws Throwable {
    point.proceed();
    return null;
}
~~~

Không có giá trị Java `null` của primitive `int`. Với Spring AOP, null result không tương thích primitive có thể được framework báo bằng `AopInvocationException`; tùy proxy/chain còn có thể có exception wrapper. Đừng dùng kiểu này như một cách “trả default”. Nếu requirement cho fallback 0, nó phải là chính sách được quyết định rõ, không tự phát từ logging.

### 7.5. Optional: không biến empty thành null

~~~java
@Around("execution(* demo.CatalogService.search(..))")
public Object badOptional(ProceedingJoinPoint point) throws Throwable {
    Object result = point.proceed();
    return result == null ? null : Optional.empty();
}
~~~

Sai với `search(true)`: dù target trả `Optional.of("found")`, advice thay thành empty. Sai với `search(false)` nếu downstream cần phân biệt một Optional container hợp lệ với việc method trả null. Nếu contract khai báo `Optional<T>` và quy ước không-null, **giữ nguyên `Optional` object** khi chỉ quan sát. Chỉ transform khi có requirement rõ.

Mọi đoạn trong phần 7 là **các mẫu sai độc lập**, không phải code để thêm vào project chuẩn. Trong code review, nên đặc biệt tìm pattern `proceed(); return null;` hoặc catch rồi return default không được mô tả bằng API contract.

## 8. Exception cũng là một phần của hợp đồng quan sát bởi caller

### 8.1. Truyền nguyên ngoại lệ khi chỉ cần log

~~~java
@Around("execution(* demo.CatalogService.fail(..))")
public Object traceFailure(ProceedingJoinPoint point)
        throws Throwable {
    try {
        return point.proceed();
    } catch (Throwable error) {
        System.out.println("Error: " + error.getClass().getSimpleName());
        throw error;
    }
}
~~~

Advice phải **rethrow đúng object `error`** nếu không có chủ đích translate exceptions. Khi target ném `IllegalStateException("boom")`, caller vẫn quan sát exception đó. Thêm `throws Throwable` cho advice method phù hợp với `ProceedingJoinPoint.proceed()`.

### 8.2. Nuốt exception làm đổi thrown exit thành normal return

~~~java
@Around("execution(* demo.CatalogService.fail(..))")
public Object hidesFailure(ProceedingJoinPoint point)
        throws Throwable {
    try {
        return point.proceed();
    } catch (Throwable error) {
        return null;
    }
}
~~~

Đây là một **thay đổi hợp đồng rất lớn**: thay vì để caller xử lý thất bại, advice trả `null` như một normal return. Với method trả `String`, code phía caller có thể không biết target đã thất bại. Với primitive return, null lại không hợp kiểu.

Nếu mục tiêu business thật sự là fallback/recovery, hãy định nghĩa exception classes được xử lý, fallback có kiểu hợp lệ, tác động với transaction/cache và logging/audit, rồi viết integration tests. Trong nhiều trường hợp, error handling thuộc **service boundary** hoặc circuit breaker/retry integration chuyên dụng hơn là một around advice tùy tiện.

### 8.3. Finally để cleanup mà không thay kết quả

~~~java
@Around("execution(* demo..service..*.*(..))")
public Object measure(ProceedingJoinPoint point)
        throws Throwable {
    long start = System.nanoTime();
    try {
        return point.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - start;
        // Ghi metric; không ném exception khác từ cleanup.
    }
}
~~~

Mẫu này duy trì normal return hoặc exception từ `proceed()` vì có `return` ở `try` và `finally` không thay kết quả. `System.nanoTime()` chỉ đo elapsed duration, không phải wall-clock timestamp. Trong production, cần bảo vệ việc ghi metric khỏi failure nếu metric failure không được phép ảnh hưởng business.

Một câu `return` trong `finally` hoặc exception ném ra trong `finally` có thể **ghi đè outcome** của `try`. Tránh pattern đó với các around advices chỉ quan sát.

## 9. Primitive, reference, void, generic và async: checklist type safety

### Primitive và boxing

Trong Java, `int quantity()` và `boolean enabled()` không thể trả `null`. Tại interceptor boundary, result có thể được đóng gói thành `Integer`/`Boolean`. Trả `String "7"` thay cho `Integer.valueOf(7)` không phải một phép đổi type được proxy tự động bảo đảm. Nếu không chủ đích transform, **return nguyên object** từ `proceed()`.

### Reference có thể null

Method `String nullable()` được thiết kế cho phép `null` thì `@Around` quan sát nên giữ `null`. Không coi “không có giá trị” là lỗi AOP tự thân. Nếu API contract cấm `null`, sửa tại service/validation layer hoặc định nghĩa policy explicit; logging aspect không nên tự đặt default.

### Method void

Method `void ping()` không cho caller nhận value. Trong Spring AOP, around advice `Object preserve(...)` vẫn trả kết quả của `proceed()` để giữ chain nhất quán. Không khai báo advice `void` chỉ vì nó có thể match `void` method; cùng aspect có thể match các methods trả giá trị khác.

### Optional

`Optional.empty()` là một object hợp lệ, **không đồng nghĩa `null`**. Giữ `Optional<T>` result khi chỉ quan sát. Trả `Optional.empty()` tùy ý thay `Optional.of(...)` có thể làm caller nghĩ dữ liệu không tồn tại.

### Generics

Giả sử một Spring service khai báo `List<Order> findAll()`. Runtime type erasure có thể khiến proxy chỉ kiểm tra raw `List`; trả một `List<String>` từ `@Around` có thể không báo lỗi ngay, nhưng code caller khi đọc phần tử như `Order` sẽ lỗi. Kiểm thử cả **element contract**, không chỉ `result instanceof List`.

### Async / Future / reactive

Với `CompletableFuture<Result> fetchAsync()`, “return contract” gồm cả **wrapper** và hành vi complete/exceptionally complete trong tương lai. Một advice trả `Result` trực tiếp thay vì `CompletableFuture<Result>` là sai kiểu; chặn `join()`/`get()` không có requirement có thể làm block thread và thay exception/timing. Với Reactor `Mono<T>`/`Flux<T>`, subscription và signals nằm ngoài thời điểm method return; nếu cần quan sát **outcome bất đồng bộ** phải chọn integration/callback phù hợp, không giả định log sau `proceed()` nghĩa là pipeline đã kết thúc.

## 10. Khi nhiều aspects cùng tham gia interceptor chain

Một `@Around` có thể bao ngoài các advice khác như transaction/cache/security; khi gọi `pjp.proceed()`, nó chuyển xuống **phần còn lại của interceptor chain**, không nhất thiết gọi target trực tiếp ngay lập tức.

~~~text
Caller
  |
  v
Outer audit @Around
  | proceed()
  v
Inner transaction/cache advisor
  | proceed() hoặc short-circuit theo semantics của nó
  v
Target method (khi được gọi)
  |
  v
Inner advisor trả result / ném exception
  |
  v
Outer @Around nhận result / exception
  |
  v
Caller
~~~

Vì vậy, **giữ hợp đồng** nghĩa là bảo toàn result/exception do inner chain thực sự trả, không chỉ giả định value đến trực tiếp từ target.

Một cache advisor có thể chủ động trả cache hit mà không chạy target. Nếu outer audit aspect trả nguyên `pjp.proceed()` result, nó không phá cache behavior. Nếu outer aspect ép result thành một type không hợp lệ, hoặc tự gọi target reflection để bypass interceptor chain, nó có thể phá cache, transaction và security policies.

Nếu dùng `@Order` để quyết định outer/inner precedence, giá trị **nhỏ hơn** thường có precedence cao hơn: advice ngoài đi vào trước và hoàn thành sau khi inner chain trả. Đừng dựa vào vị trí khai báo code để suy ordering giữa các aspects. Test trên đúng Spring context khi outcome phụ thuộc thứ tự.

## 11. Cách thiết kế một around advice an toàn

Một skeleton có mục đích rõ ràng:

~~~java
@Aspect
public class TimingAspect {
    @Around("execution(* demo..service..*.*(..))")
    public Object measure(ProceedingJoinPoint point) throws Throwable {
        long started = System.nanoTime();
        try {
            return point.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - started;
            // Metrics sink phải có failure policy rõ ràng.
        }
    }
}
~~~

Đây là **snippet minh họa**; cần import `@Aspect`, `@Around`, `ProceedingJoinPoint`, đăng ký aspect Spring bean và dùng package pointcut thực tế. Nếu code chỉ muốn log **trước** method, `@Before` đơn giản hơn. Nếu chỉ muốn log **result thành công**, `@AfterReturning` thường đúng quyền can thiệp hơn. Dùng `@Around` khi phải giữ local state trước/sau invocation hoặc can thiệp luồng thực thi với mục tiêu rõ ràng.

Một code review tốt nên hỏi:

1. Advice có thật sự cần `@Around` hay `@Before`/`@AfterReturning` đáp ứng đủ?
2. Mỗi nhánh success có `return` đúng giá trị `proceed()` trả không?
3. Mỗi nhánh failure có giữ nguyên exception theo contract không?
4. Có `return null`/default vô tình nào không? Nếu có, target return type là reference hay primitive?
5. Method có `Optional`, generic collections, async wrapper hoặc nullable contract không?
6. `proceed()` có đúng số lần cần thiết? Retry có thể gây side effects?
7. Có self-invocation, target raw, method `final` hoặc pointcut không match làm advice không chạy?
8. Log/metrics code có thể ném lỗi khiến outcome thay đổi không?
9. Nếu muốn transform có chủ ý, có test caller-facing result và exception tương ứng không?
10. Nếu nhiều advisors cùng match, đã xác minh thứ tự và transaction/cache semantics thực tế chưa?

## 12. Bài tập kiểm tra các tình huống sai

### Bài 1: quên return

Mẫu:

~~~java
Object result = point.proceed();
return null;
~~~

Với `String label()`, caller có thể nhận null. Sửa thành `return result;`. Với `int quantity()`, null không tương thích primitive; không dùng ví dụ sai đó trong production.

### Bài 2: ép mọi kết quả về String

Mẫu:

~~~java
Object result = point.proceed();
return String.valueOf(result);
~~~

Nếu aspect match `int`, `boolean` hoặc `Optional`, method công bố return type không khớp với `String`. Nếu aspect chỉ cần log, hãy log `String.valueOf(result)` vào logger rồi **`return result;`**.

### Bài 3: fallback “im lặng”

Mẫu:

~~~java
try {
    return point.proceed();
} catch (Throwable error) {
    return "";
}
~~~

Với method ném lỗi và contract yêu cầu caller được thấy exception, đây là bug. Với method primitive/Optional, chuỗi `""` còn sai type. Nếu fallback có mục đích, phải match method cụ thể, xác định loại exception được handle và đúng return type, document behavior và test.

### Bài 4: method void

Method `void ping()` không đòi hỏi caller nhận giá trị. Trong một `@Around Object` chung cho nhiều methods, vẫn dùng:

~~~java
return point.proceed();
~~~

Không đổi advice thành `void` chỉ vì một method mục tiêu trả `void`. Spring docs khuyến nghị `Object` return type để không vứt bỏ return value của các method trả value.

### Bài 5: Optional

Nếu `search(false)` trả `Optional.empty()`, lời gọi advice nên giữ object result đó. Khi thấy `Optional.empty()` không có nghĩa target trả `null`. Trả `null` từ aspect có thể khiến caller `search(false).isPresent()` ném `NullPointerException`.

### Bài 6: exception

Trong `@Around` chỉ logging:

~~~java
try {
    return point.proceed();
} catch (Throwable error) {
    logException(error);
    throw error;
}
~~~

`logException` là helper **giả định** có error policy riêng, không phải method đã được định nghĩa trong project chạy phần 5. Không để helper ném một exception khác nếu yêu cầu là giữ nguyên exception gốc.

## 13. Gỡ lỗi khi caller nhận sai kết quả dù target đúng

1. **Đo proxy**: dùng `AopUtils.isAopProxy(service)` trên reference thực sự đang được gọi. Nếu false, không nên quy kết ngay cho around advice.
2. **Xem pointcut**: method trả kết quả sai có thực sự match `@Around` này không? Có aspect khác nằm trong chain không?
3. **Kiểm tra advice signature**: `@Around` có khai báo return `Object`, tham số `ProceedingJoinPoint` đầu tiên và `throws Throwable` không?
4. **Kiểm tra đường success**: có gọi `proceed()` đúng một lần và `return` chính result không, hay chỉ gọi rồi bỏ?
5. **Kiểm tra đường failure**: có catch và return default/`null` vô tình không? Có `finally` ném lỗi khác không?
6. **So return type**: dùng `MethodSignature.getReturnType()` để xác định method khai báo `String`, `int`, `void`...; đối chiếu object result hoặc fallback.
7. **Đặc biệt với primitive**: `null` không thể giao cho `int`/`boolean`, kiểm tra lỗi `AopInvocationException`/wrapper tương ứng.
8. **Với generics**: kiểm tra cả element types và behavior caller sử dụng, không chỉ raw class.
9. **Với Optional/async**: phân biệt `Optional.empty()`/`null` và object wrapper/result hoàn tất sau này.
10. **Xác minh nhiều advisors**: transaction/cache/security có thể thay đổi cách tạo result/exception; đọc precedence/order thực tế.
11. **Không thay target bằng reflection trực tiếp**: gọi `method.invoke(target, ...)` không tương đương `ProceedingJoinPoint.proceed()` và có thể bypass inner AOP advisors.
12. **Viết integration tests**: assert caller-facing results của các method representative, exception propagation và số lần advice chạy, không chỉ xem Spring bean có AOP proxy.

### Mẫu đo return type bên trong advice

~~~java
MethodSignature signature =
        (MethodSignature) point.getSignature();
Class<?> declaredReturn = signature.getReturnType();
System.out.println("Declared: " + declaredReturn.getTypeName());

Object result = point.proceed();
System.out.println("Is null: " + (result == null));
return result;
~~~

Đây là snippet trong `@Around` đã có biến `point` và import `MethodSignature`; không phải file chạy độc lập. Tránh log nội dung result nếu nó có dữ liệu nhạy cảm hoặc có `toString()` gây side effects. Chỉ đọc metadata khi cần và không parse chuỗi class name của generated proxy.

## 14. Checklist trước khi merge một Spring @Around advice

- [ ] Business requirement nêu rõ đây là **quan sát** hay **chủ động transform/short-circuit**.
- [ ] Nếu chỉ cần xử lý trước, dùng `@Before`; nếu chỉ đọc successful result, cân nhắc `@AfterReturning`.
- [ ] `@Around` khai báo `Object` và tham số đầu `ProceedingJoinPoint`.
- [ ] Mọi nhánh success bình thường trả **đúng result** của `proceed()` khi không có policy thay thế.
- [ ] `proceed()` được gọi đúng số lần; không skip/retry trái yêu cầu.
- [ ] Thrown exit propagate exception gốc nếu chỉ logging/tracing.
- [ ] Không có `return` trong `finally` và logging/metrics không vô ý làm đổi outcome.
- [ ] Đã test reference type, primitive, void, nullable, Optional và exception đại diện.
- [ ] Fallback nếu có phải đúng declared return type/nullability, được document và kiểm thử.
- [ ] Aspect và target là Spring-managed beans; caller dùng proxy, tránh self-invocation.
- [ ] Có integration test caller-facing, không chỉ đếm số lần advice.
- [ ] Nếu chain có transaction/cache/security, ordering và tương tác return/exception đã được xem xét.
- [ ] Không đưa dữ liệu nhạy cảm vào log chỉ vì advice được phép đọc `getArgs()` hoặc result.

## 15. Tài liệu Spring Framework chính thức

- [Spring Framework 6.2 — Declaring Advice / Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `Object` return type, `proceed()`, ý nghĩa return value nhìn từ caller, lưu ý `@Around void` và `@AfterReturning` không thay reference.
- [Spring Framework 6.2 — Advice API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html): `MethodInterceptor.invoke` trả giá trị của invocation, `AfterReturningAdvice` chỉ quan sát value và exception từ advice.
- [Spring Framework 6.2 — AOP Concepts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-defn.html): `@Around` là loại advice mạnh, nên chọn advice ít quyền nhất đáp ứng yêu cầu.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): proxy boundary, JDK/CGLIB, self-invocation.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): đăng ký @Aspect Spring bean và kích hoạt auto-proxying.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-002">Kết quả null và phương thức trả về kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Kết quả `null` và phương thức trả về kiểu nguyên thủy

## 1. Vì sao kết quả `null` trở thành lỗi tại ranh giới Spring AOP proxy?

Trong Spring Aspect, một `@Around` advice có thể **thay đổi kết quả cuối cùng** mà consumer nhận từ một method execution. Đây là tính năng hữu ích khi xử lý cache hoặc fallback, nhưng cũng tạo một lỗi đặc trưng: **advice trả `null` cho phương thức có kiểu trả về nguyên thủy**.

```java
@Around("execution(* demo.results.ResultService.count(..))")
public Object incorrect(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();
    return null; // SAI: count() được khai báo trả int.
}
```

Đây là **fragment sai để phân tích**, không phải implementation được khuyến nghị. Cho dù `count()` thật sự trả `int 7`, Spring AOP proxy nhận giá trị `null` từ advice và không thể chuyển nó thành `int` hợp lệ cho caller.

Với Spring Framework 6.x, cả hai họ proxy đều có kiểm tra kiểu này:

- `JdkDynamicAopProxy` kiểm tra kết quả khi method có primitive return type (ngoại trừ `void`).
- `CglibAopProxy` cũng kiểm tra kết quả trong quá trình xử lý return value.
- Khi kết quả là `null` nhưng method signature yêu cầu primitive như `int`, Spring có thể ném `org.springframework.aop.AopInvocationException`. Thông điệp điển hình nói rằng null return value từ advice không khớp primitive return type.

**Đừng nhầm `int 0` với `null`:** `0` là giá trị `int` hợp lệ; `null` không phải một `int`. Spring proxy không tự invent giá trị mặc định `0`, `false` hoặc `0.0` để cứu một advice trả sai.

Bài học này chỉ tập trung vào **Spring Aspect/Spring AOP**, lời gọi qua proxy, outcome mà advice trả về và giới hạn Java return contract. Không bàn AOP tổng quát hoặc AspectJ weaving bên ngoài Spring.

## 2. Cần phân biệt ba “kiểu kết quả”

### 2.1. Declared return type của target

Java method định nghĩa một hợp đồng trả về:

```java
public int count() { return 7; }
public Integer boxedCount() { return null; }
public String nullableName() { return null; }
public void complete() { System.out.println("done"); }
```

- `int` là primitive; không có null.
- `Integer` là reference type; có thể null.
- `String` cũng là reference type; có thể null về mặt Java, dù business contract có thể không cho phép.
- `void` biểu thị method **không có return value**; nó khác với “method có một giá trị null”.

### 2.2. Return type của `ProceedingJoinPoint.proceed()`

Spring AOP `@Around` truy cập kết quả phần còn lại của interceptor chain qua `Object`:

```java
Object result = pjp.proceed();
```

Với method trả `int 7`, trong advice `result` là một object tương thích `Integer(7)` (boxing). Với reference-type method trả null, `result == null`. Với `void`, `proceed()` thông thường cũng trả null, nhưng ý nghĩa ở API level khác nhau.

Vì `Object` không chứa thông tin primitive trực tiếp, **cần xem method signature** để biết null có hợp lệ hay không. Không thể suy từ `result == null` rằng method nhất định trả `String`, `Integer`, hoặc `void`.

### 2.3. Actual return value của around advice

```java
@Around("execution(* demo.results.ResultService.*(..))")
public Object preserving(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    return result;
}
```

**Đây là hình thức mặc định nên dùng khi advice chỉ quan sát:** giá trị của `proceed()` được chuyển tiếp nguyên vẹn (trừ trường hợp advice khác nằm trong chain đã chủ đích transform). Caller cuối cùng nhận kết quả theo return contract của method được proxy công bố.

Ngược lại, nếu advice viết `return null`, `return "OK"` hoặc `return 0`, đó là một **quyết định thay đổi result**, không phải hành động vô hại. Với primitive, return khác type có thể gây exception; với reference type, return khác value có thể phá semantic contract.

## 3. Ma trận null, primitive, wrapper và void trong Spring proxy

| Target method | Kết quả từ target | Advice return | Outcome kỳ vọng |
| --- | --- | --- | --- |
| `int count()` | `7` | `Integer(7)` qua `proceed()` | Caller nhận `int 7` |
| `int count()` | `7` | `null` | **Lỗi** do null không tương thích primitive |
| `int count()` | `7` | `Integer(0)` | Caller nhận `0`: đúng kiểu nhưng **đã thay đổi giá trị** |
| `boolean active()` | `true` | `Boolean.TRUE` | Caller nhận `true` |
| `boolean active()` | `true` | `null` | **Lỗi** null không tương thích primitive |
| `long total()` | `9L` | `Long(9L)` | Caller nhận `9L` |
| `long total()` | `9L` | `Integer(9)` | Không bảo đảm hợp lệ; wrapper khác kiểu |
| `Integer boxedCount()` | `null` | `null` | Hợp lệ theo Java reference type |
| `String nullableName()` | `null` | `null` | Java cho phép; caller phải biết nullability |
| `Optional<String> find()` | `Optional.empty()` | `Optional.empty()` | Hợp lệ và khác với null |
| `Optional<String> find()` | `Optional.empty()` | `null` | Java có thể nhận nhưng phá Optional contract |
| `void complete()` | Không có value | `null` từ `proceed()` | Lời gọi `void` hợp lệ |

**Chú ý về lỗi:** `AopInvocationException` là một outcome phổ biến được Spring thực hiện rõ ràng khi **proxy thấy null cho primitive**, không phải lời hứa rằng mọi loại sai kiểu đều ném chính exception này. Trả `String` cho `int` có thể dẫn tới `ClassCastException` hoặc lỗi type conversion ở nơi khác. Trong Kotlin/coroutines hay custom proxy stack, cơ chế khác có thể tham gia; bài này xét Java 17 và Spring AOP proxy thông thường.

### 3.1. Boxing và unboxing có giải quyết null được không?

Không. Java có thể boxing `int 7` thành `Integer.valueOf(7)` và unboxing `Integer(7)` trở lại `int 7`, nhưng `null` không có trạng thái primitive tương ứng.

```java
Integer boxed = null;
// int primitive = boxed; // Nếu chạy sẽ lỗi NullPointerException khi unboxing.
```

Đây là lỗi **ngôn ngữ Java tại điểm unboxing**. Spring AOP có thêm lớp kiểm tra **ở proxy return boundary**, nên với advice trả null cho target primitive, lỗi có thể là `AopInvocationException` ngay tại proxy, trước khi code caller tự unbox.

### 3.2. `void` khác `Void`

- `void complete()`: không có return value; Spring AOP biểu diễn kết quả bên trong advice bằng null.
- `java.lang.Void completeReference()`: đây là **reference type**, có thể trả null về mặt Java, dù `Void` không có value thông thường để khởi tạo.
- `CompletableFuture<Void> asyncComplete()`: trả một **future object**; một future completed với value null vẫn là một future, không phải advice trả null thay future.

Không dùng kiểu `Void` như một cách diễn tả rằng `void` không cần giữ contract. Trong Spring `@Around`, advice tốt vẫn trả chính result từ `proceed()`.

### 3.3. Primitive `char`, `byte`, `short`, `float`, `double` cũng không nhận null

Nguyên tắc áp dụng cho tất cả các primitive có giá trị: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. Wrapper cần tương thích; đừng đoán Spring luôn thực hiện primitive widening khi advice trả một wrapper khác type.

## 4. Vì sao `@Around` nên trả `Object` thay vì `void`?

Spring Framework hướng dẫn method `@Around` **nên khai báo kiểu trả về `Object`**, và tham số đầu tiên phải là `ProceedingJoinPoint`.

**Đúng với một advice logging áp dụng cho nhiều return types:**

```java
@Around("execution(* demo.results..*Service.*(..))")
public Object logResult(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    System.out.println("completed " + pjp.getSignature().getName());
    return result;
}
```

**Sai khi target có non-void methods:**

```java
@Around("execution(* demo.results..*Service.*(..))")
public void logResultWrong(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed(); // Giá trị của target bị bỏ qua.
}
```

Tài liệu Spring nêu rằng một advice `@Around` khai báo kiểu trả về `void` khiến caller thấy `null`, **bất kể** target đã return gì. Với `int`, `boolean`... điều này có thể khiến Spring proxy ném lỗi null-primitive; với `String`, nó có thể âm thầm đổi kết quả thành null.

Đừng viết một advice kiểu `void` chỉ vì nó “chỉ log”. Đối với `@Before`, method advice kiểu `void` là bình thường, vì nó không chịu trách nhiệm đưa return value cho caller. Còn `@Around` trực tiếp kiểm soát outcome, nên cần return rõ ràng.

### 4.1. `return` bên trong `finally` có thể phá cả return lẫn exception

```java
@Around("execution(* demo.results..*Service.*(..))")
public Object dangerous(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        return null; // Sai: ghi đè cả return hoặc exception bên trong.
    }
}
```

Java `finally` có lệnh `return` có thể chặn exception đã ném, khiến proxy chỉ thấy null. Đây là lỗi nguy hiểm nếu đang dùng `@Transactional` hoặc caller dựa vào exception để rollback/retry.

**Đúng:**

```java
@Around("execution(* demo.results..*Service.*(..))")
public Object safe(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        System.out.println("end");
    }
}
```

Dù target return null hay throw, cleanup/ghi log cuối vẫn có thể chạy; advice không âm thầm thay outcome. Cần thiết kế cleanup sao cho bản thân nó không tùy tiện ném exception che lỗi gốc.

## 5. Cách xác định return type được khai báo trong Spring Aspect

Trong `@Around` có thể dùng `MethodSignature`:

```java
import org.aspectj.lang.reflect.MethodSignature;
import java.lang.reflect.Method;

MethodSignature signature = (MethodSignature) pjp.getSignature();
Class<?> declaredReturnType = signature.getReturnType();
Method method = signature.getMethod();

System.out.println("returnType=" + declaredReturnType.getName());
```

Đây là **fragment** trong advice, không phải file Java độc lập. Với một Spring-managed bean có method `int count()`, `declaredReturnType == int.class`. Nếu method `void complete()`, result type là `void.class`.

Có thể kiểm tra null-primitive một cách minh bạch khi một advice định thay đổi kết quả:

```java
Object result = pjp.proceed();
Class<?> returnType =
        ((MethodSignature) pjp.getSignature()).getReturnType();

if (result == null && returnType.isPrimitive()
        && returnType != void.class) {
    throw new IllegalStateException(
            "null is incompatible with primitive return type " + returnType);
}
return result;
```

Đây là **fragment phòng vệ**, không bắt buộc khi bạn không thay đổi kết quả của `proceed()`; Spring proxy đã có kiểm tra trường hợp null–primitive. Một target Java method có primitive return và chạy bình thường tự nó không thể return null, nên null xuất hiện thường do advice/interceptor bên trong đã làm biến dạng kết quả.

### 5.1. Method trên JDK proxy có thể là interface method

Với JDK dynamic proxy, `MethodSignature.getMethod()` có thể biểu diễn method trên interface thay vì implementation. Khi cần annotation hoặc return type cụ thể trên implementation, dùng:

```java
MethodSignature signature = (MethodSignature) pjp.getSignature();
Method invoked = signature.getMethod();
Object target = pjp.getTarget();

if (target != null) {
    Method specific = AopUtils.getMostSpecificMethod(
            invoked, target.getClass());
    System.out.println(specific.getReturnType().getName());
}
```

Fragment này cần import `org.springframework.aop.support.AopUtils`. Với bridge/generic methods, còn phải cân nhắc metadata và `BridgeMethodResolver`. Tuy nhiên null–primitive compatibility vẫn được xét theo **return contract của method invocation**, không suy ra chỉ bằng cách xem một DTO generic.

### 5.2. Method return `Integer` có nhất thiết được phép null?

Về **Java type system**, có. Nhưng business API có thể yêu cầu non-null. Spring proxy chỉ không có căn cứ để coi một `Integer null` là không hợp lệ như `int null`. Nếu muốn bảo đảm non-null reference, đặt validation ở boundary phù hợp (ví dụ annotation validation có cơ chế thực thi) hoặc kiểm tra cụ thể trong service/around advice.

## 6. Kết quả null của `@AfterReturning` khác với kết quả null của `@Around`

Spring `@AfterReturning` chạy khi method execution kết thúc **bình thường**, kể cả khi method reference type return null. Nhưng có hai kiểu advice cần phân biệt:

**Không bind return value:**

```java
@AfterReturning("execution(* demo.results.ResultService.nullableName(..))")
public void afterNormal() {
    System.out.println("completed normally");
}
```

**Bind return value với kiểu cụ thể:**

```java
@AfterReturning(
    pointcut = "execution(* demo.results.ResultService.nullableName(..))",
    returning = "value"
)
public void inspect(String value) {
    System.out.println("value=" + value);
}
```

Các đoạn là **fragment** thay thế lẫn nhau. Khi có `returning="value"`, Spring dùng kiểu tham số advice để giới hạn matching/binding của return value. Cần hiểu semantics matching đối với `null` và loại tham số cụ thể theo phiên bản/cấu hình; đừng coi `String value` luôn được gọi với một String non-null. Với mục tiêu chỉ biết method hoàn tất, `@AfterReturning` **không cần binding** là lựa chọn đơn giản nhất.

**Đặc biệt:** method `@AfterReturning` không phải nơi để trả một reference mới thay target result. Nếu advice có method return `String`, giá trị return từ advice không trở thành kết quả mới cho caller. Khi cần thay kết quả, dùng `@Around` và đảm bảo return type tương thích.

### 6.1. `null` không nhất thiết nghĩa là exception

Nếu method trả `String null` qua proxy và không có advisor cấm null, invocation vẫn kết thúc **bình thường**. `@AfterThrowing` không chạy chỉ vì return value null. Ngược lại, khi advice trả null cho `int` khiến Spring proxy ném exception, đó là lỗi **ở ranh giới proxy/advice**, không phải một exception tự nhiên do target method ném.

### 6.2. `@AfterThrowing` không là bộ giám sát mọi lỗi trong interceptor chain

Một advice `@AfterThrowing` được định nghĩa cho exceptional exit từ method execution được advice; tài liệu Spring lưu ý nó **không phải general exception callback** bắt mọi lỗi của các advice khác. Khi Spring proxy tự ném `AopInvocationException` vì null–primitive ở lớp ngoài chain, không được mặc định một `@AfterThrowing` dành cho method đích sẽ nhìn thấy lỗi đó. Cần quan sát tại caller hoặc dùng `@Around` ngoài thích hợp, có chú ý advisor precedence.

## 7. Ví dụ Java 17 + Spring Framework 6.2: đúng null và sai null

Ví dụ hoàn chỉnh sau dùng Maven, Java 17, Spring Framework 6.2 và **Spring AOP proxy** (không cần Spring Boot). Dùng interface `ResultPort` để JDK dynamic proxy thể hiện rõ return contract.

Dự án có một Spring-managed service với:

- `nullableName()` trả String null **hợp lệ về Java**;
- `count()` trả primitive `int 7`;
- `zero()` trả primitive `int 0` **hợp lệ**, không phải null;
- `boxedCount()` trả `Integer null`;
- `emptyResult()` trả `Optional.empty()` (khác null);
- `complete()` trả `void`;
- `brokenPrimitive()` trả primitive `int 9`, nhưng **một advice cố tình trả null**, khiến proxy ném lỗi.

Các pointcuts cho trường hợp bảo toàn và trường hợp lỗi **không chồng nhau**, do đó không cần dựa vào advisor precedence để dự đoán stdout.

### 7.1. Cấu trúc thư mục

```text
spring-aop-null-primitive/
├── pom.xml
└── src/main/java/demo/results/
    ├── AppConfig.java
    ├── ResultPort.java
    ├── ResultService.java
    ├── ResultAspect.java
    └── Main.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-null-primitive</artifactId>
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

### 7.3. `AppConfig.java`

```java
package demo.results;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` phát hiện service và aspect trong package `demo.results`. `@EnableAspectJAutoProxy` bật Spring AOP proxying. Target triển khai interface; `proxyTargetClass=false` ưu tiên JDK dynamic proxy.

### 7.4. `ResultPort.java`

```java
package demo.results;

import java.util.Optional;

public interface ResultPort {
    String nullableName();
    int count();
    int zero();
    Integer boxedCount();
    Optional<String> emptyResult();
    void complete();
    int brokenPrimitive();
}
```

Method `int brokenPrimitive()` có primitive return; không thể return null trong Java source của target. Lỗi sẽ đến từ advice return `Object null`.

### 7.5. `ResultService.java`

```java
package demo.results;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ResultService implements ResultPort {
    @Override
    public String nullableName() {
        System.out.println("[target] nullableName");
        return null;
    }

    @Override
    public int count() {
        System.out.println("[target] count");
        return 7;
    }

    @Override
    public int zero() {
        System.out.println("[target] zero");
        return 0;
    }

    @Override
    public Integer boxedCount() {
        System.out.println("[target] boxedCount");
        return null;
    }

    @Override
    public Optional<String> emptyResult() {
        System.out.println("[target] emptyResult");
        return Optional.empty();
    }

    @Override
    public void complete() {
        System.out.println("[target] complete");
    }

    @Override
    public int brokenPrimitive() {
        System.out.println("[target] brokenPrimitive");
        return 9;
    }
}
```

Target minh họa các kết quả Java hợp lệ. `null` ở `nullableName()` và `boxedCount()` là null reference. `emptyResult()` trả **một Optional object rỗng**, không phải null. `zero()` trả số 0 hợp lệ.

### 7.6. `ResultAspect.java`

```java
package demo.results;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ResultAspect {
    @Around(
        "execution(* demo.results.ResultService.*(..))"
      + " && !execution(* demo.results.ResultService.brokenPrimitive(..))"
    )
    public Object preserve(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        Object result = pjp.proceed();

        String kind = result == null
                ? "null"
                : result.getClass().getSimpleName();

        System.out.println("[advice] " + name + " kind=" + kind);
        return result;
    }

    @Around("execution(* demo.results.ResultService.brokenPrimitive(..))")
    public Object breakPrimitive(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        System.out.println("[advice] brokenPrimitive discarded=" + result);
        return null; // Chủ đích minh họa lỗi Spring proxy.
    }
}
```

Hai advices có expression tách biệt:

- `preserve(...)` nhận các method còn lại của `ResultService`, gọi `proceed()` một lần, log runtime kind khi result không null, **trả nguyên value**.
- `breakPrimitive(...)` chỉ match `brokenPrimitive()`, vẫn gọi target nên log target xuất hiện, rồi **return null** bất chấp declared `int`.
- `@Aspect @Component` tạo aspect bean; method target cũng phải đi qua proxy Spring để các advice này chạy.
- `result.getClass()` được bảo vệ bởi kiểm tra `result == null` nên không gây NPE khi reference null/void.

Lưu ý `@Around` phải khai báo `Object` để đảm bảo trả result. Nếu advice có `void` return, caller sẽ nhận null dù target method đã chạy.

### 7.7. `Main.java`

```java
package demo.results;

import org.springframework.aop.framework.AopInvocationException;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            ResultPort port = context.getBean(ResultPort.class);
            System.out.println("[main] jdkProxy="
                    + AopUtils.isJdkDynamicProxy(port));

            System.out.println("[caller] name=" + port.nullableName());
            System.out.println("[caller] count=" + port.count());
            System.out.println("[caller] zero=" + port.zero());
            System.out.println("[caller] boxed=" + port.boxedCount());
            System.out.println("[caller] optional=" + port.emptyResult());

            port.complete();
            System.out.println("[caller] complete returned void");

            try {
                System.out.println("[caller] broken="
                        + port.brokenPrimitive());
            } catch (AopInvocationException ex) {
                System.out.println("[caller] primitive null rejected");
            }
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.results.Main
```

**Stdout dự kiến** từ các lệnh `System.out` của ứng dụng (bỏ qua log riêng của Maven/Spring nếu môi trường phát sinh):

```text
[main] jdkProxy=true
[target] nullableName
[advice] nullableName kind=null
[caller] name=null
[target] count
[advice] count kind=Integer
[caller] count=7
[target] zero
[advice] zero kind=Integer
[caller] zero=0
[target] boxedCount
[advice] boxedCount kind=null
[caller] boxed=null
[target] emptyResult
[advice] emptyResult kind=Optional
[caller] optional=Optional.empty
[target] complete
[advice] complete kind=null
[caller] complete returned void
[target] brokenPrimitive
[advice] brokenPrimitive discarded=9
[caller] primitive null rejected
```

### 7.8. Đọc kết quả theo từng trường hợp

1. `jdkProxy=true`: bean `ResultService` được Spring proxy qua interface `ResultPort`.
2. `nullableName()`: target trả reference null, around advice thấy null và trả nguyên; caller in `name=null`. Đây là **normal return**, không phải primitive lỗi.
3. `count()`: target trả primitive 7. Khi `proceed()` hoàn tất, advice nhận wrapper `Integer`, log `kind=Integer`, rồi trả wrapper; caller nhận primitive 7.
4. `zero()`: target trả `int 0`. Advice log `Integer`, caller nhận 0; **0 khác null**.
5. `boxedCount()`: declared `Integer` cho phép null, advice trả null nên proxy chấp nhận. Caller in `boxed=null`.
6. `emptyResult()`: giá trị là Optional object. `getClass().getSimpleName()` trong JDK thông thường in `Optional`, caller in `Optional.empty`.
7. `complete()`: target void; `proceed()` biểu diễn kết quả bằng null; advice return null hợp lệ vì declared type là `void`.
8. `brokenPrimitive()`: target đã trả `int 9`; advice cố tình **bỏ** `Integer(9)` và return null. Spring proxy phát hiện declared primitive type và ném `AopInvocationException`. Caller bắt và in dòng `primitive null rejected`.
9. Tất cả các invocation từ Main đi qua Spring-managed reference; nếu dùng `new ResultService()` thay cho DI/context, advice không chạy và `brokenPrimitive()` sẽ trả 9 bình thường.

**Cảnh báo:** dòng số 8 là lỗi được tạo **có chủ đích để học**. Trong production phải sửa advice, không bắt lỗi từ proxy rồi trả thành công giả.

## 8. Nếu mục tiêu là fallback, chọn giá trị tương thích và có ý nghĩa

Một `@Around` có thể catch exception và trả fallback:

```java
@Around("execution(int demo.results.ResultService.count(..))")
public Object fallbackCount(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return 0; // Integer(0) qua Object, tương thích int.
    }
}
```

Đây là **fragment riêng**, không được thêm vào dự án mục 7 nếu chưa thiết kế ordering giữa nhiều around advices. Về mặt kiểu Java, số 0 có thể đi qua proxy tới `int`. Nhưng **đúng kiểu không bảo đảm đúng nghiệp vụ**: với số lượng đơn hàng hay số tiền, 0 có thể là dữ liệu sai nghiêm trọng.

Nếu method thực tế trả `Optional<String>`, fallback thường thích hợp hơn là `Optional.empty()` (theo contract). Nếu method trả `CompletableFuture<String>`, fallback phải là một future tương thích, không phải String payload. Nếu method trả `String` có `@NonNull` contract, return null dù Java cho phép vẫn là vi phạm nghiệp vụ.

### 8.1. Quy tắc tránh silent data corruption

- **Không tự chuyển null thành 0** cho primitive nếu không có requirement nghiệp vụ.
- **Không dùng `return null` chung** cho mọi return type.
- **Không dùng `Object.class`** như lời giải cho vấn đề typed contract: method public vẫn có signature declared return type cố định.
- **Không gọi `proceed()` hai lần** để “lấy lại value” nếu invocation có thể có side effects.
- **Không trả wrapper khác type** rồi hy vọng runtime sẽ tự chuyển thành primitive tương ứng.
- **Không catch `Throwable` rồi return null** để che mọi lỗi; có thể nuốt exceptions nghiêm trọng và che rollback conditions.

## 9. Null, exception và transaction trong Spring AOP

### 9.1. Null reference return là normal completion

Một method `String lookup()` trả null không tự ném exception; Spring có thể cho invocation kết thúc bình thường. Vì thế `@AfterReturning` có thể chạy, còn `@AfterThrowing` thường không chạy chỉ vì kết quả null.

Nếu application yêu cầu null là lỗi, hãy tạo **validation policy** có căn cứ ở service/interface boundary; dùng `@NonNull`/validation cần framework thực sự thực thi, chứ chỉ tên annotation không tự bảo đảm Spring AOP biến mọi null thành exception.

### 9.2. Null primitive là lỗi được phát hiện tại proxy boundary

Nếu một `@Around` bên trong chain trả null cho method primitive, Spring proxy bên ngoài có thể ném `AopInvocationException`. Lỗi này không nhất thiết phát sinh từ thân target method; trong demo mục 7 target đã chạy xong và trả int đúng.

Điều này quan trọng khi debugging:

```text
[target] brokenPrimitive
[advice] brokenPrimitive discarded=9
[caller] primitive null rejected
```

**Không suy ra target đã ném exception** chỉ vì caller bắt được lỗi từ lời gọi proxy. Cần phân biệt target exception và proxy/advice return contract violation.

### 9.3. Transaction rollback không chữa return mismatch

Spring `@Transactional` có thể dùng một advisor khác trong chain. Nếu transaction interceptor thấy target trả bình thường nhưng một outer AOP layer sau đó ném null-primitive exception, rollback/commit đã có thể được quyết định ở boundary bên trong. Nếu lỗi phát sinh bên trong transaction boundary, nó có thể kích hoạt rollback tùy exception type và rollback rules.

Do đó không tuyên bố “primitive null làm transaction rollback” trong mọi cấu hình. Hãy xem advisor ordering, transaction interceptor position và test commit/rollback thực tế.

### 9.4. Async return wrappers

Method `CompletableFuture<Integer> countAsync()` có declared return type **future**, không phải `int`. Một `@Around` trả null có thể không bị kiểm tra null-primitive ngay, vì future là reference type, nhưng caller có thể NPE khi gọi `thenApply(...)`. Tương tự với `Mono<Integer>` hoặc `Flux<Integer>` nếu dùng Reactor.

Một future hoàn thành với kết quả null vẫn là **future object**; wrapper, completion/error behavior và value đều thuộc contract của async API. Không return payload trực tiếp thay future.

## 10. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân chính | Cách xử lý |
| --- | --- | --- |
| `AopInvocationException` nói null không khớp primitive | Around advice (hoặc advisor) trả null cho `int`/`boolean`... | `return pjp.proceed()` khi chỉ quan sát; fallback phải cùng kiểu |
| Target đã in giá trị đúng nhưng caller vẫn lỗi | Result bị advice ghi đè sau khi target return | Kiểm tra mọi đường `return` của `@Around` |
| Method `String` trả null mà không lỗi proxy | Reference type cho phép null ở JVM | Kiểm tra business nullability contract |
| Method `Integer` trả null khác `int` thế nào? | Wrapper là reference, primitive không nhận null | Dùng type/API phù hợp |
| Method `void` có `result == null` | Không có business return value | Đây là biểu diễn bình thường của `proceed()` |
| `Optional.empty()` bị đổi thành null | Advice phá wrapper contract | Trả lại Optional nguyên bản |
| Advice khai báo `void` áp lên non-void target | Return bị loại bỏ theo semantics Spring `@Around` | Khai báo `Object` và return result |
| Catch error rồi `return null` | Che exception và phá primitive contract | Chỉ fallback khi contract cho phép |
| `return null` trong `finally` | Ghi đè return hoặc exception | Không return trong finally |
| Trả `Integer(5)` cho target `long` | Wrapper không phù hợp `Long` | Giữ nguyên boxed value của proceed |
| `@AfterReturning` không chạy theo dự kiến | Return binding type/pointcut không phù hợp | Kiểm tra declared/actual type, normal completion và annotation |
| Logging advice không chạy khi self-call | `this.method()` bypass proxy | Tách bean hoặc gọi qua Spring proxy đúng ranh giới |
| Return null xảy ra trên JDK proxy nhưng đổi sang CGLIB vẫn lỗi | Cả hai hỗ trợ kiểm tra primitive-null | Sửa advice, không đổi proxy mode để né lỗi |
| Unit test tạo service bằng `new` không tái hiện lỗi | Không có Spring AOP proxy trong unit test | Test tích hợp với ApplicationContext thật |
| Null primitive proxy exception nhưng transaction vẫn commit | Lỗi phát sinh ngoài transaction boundary | Xem advisor chain và transaction ordering |
| Future/Reactive return null gây lỗi ở caller | Reference wrapper bị mất | Giữ wrapper thích hợp |

## 11. Checklist code review và kiểm thử

1. Xác nhận target là Spring-managed bean và aspect được đăng ký; nếu không có proxy, không có advice.
2. Xác định **declared return type** của method, đặc biệt primitive, wrapper, `void`, `Optional` và async wrapper.
3. Với `@Around` chỉ logging/metrics, dùng `Object result = pjp.proceed(); return result;`, gọi proceed đúng một lần.
4. Kiểm tra mọi nhánh `return`, `catch`, `finally`; không trả null ngoài policy.
5. Kiểm tra boxed primitive có đúng wrapper và giá trị hay không.
6. Kiểm tra null reference theo contract nghiệp vụ; `String null` không mặc nhiên là proxy error.
7. Xác nhận `@AfterReturning` và `@AfterThrowing` được dùng đúng outcome semantics.
8. Nếu có multiple advisors, cache, transaction, retry hoặc async, xác minh actual ordering và điểm phát sinh lỗi.
9. Viết integration tests trên **đúng Spring AOP proxy**, với cả value, null, zero, void, Optional và primitive violation.
10. Giữ log exception rõ nguồn: target throw, advice throw hay proxy return type check.
11. Với JDK proxy, gọi qua interface methods; với CGLIB, kiểm tra final/private restrictions và self-invocation.
12. Không sửa lỗi primitive-null bằng cách đặt return zero giả hoặc nuốt exceptions.

### Bảng ca kiểm thử nên có

| Case | Expected result |
| --- | --- |
| `String nullableName()` → null | Caller thấy null nếu contract cho phép |
| `Integer boxedCount()` → null | Caller thấy null |
| `int count()` → 7 | Caller thấy 7 |
| `int zero()` → 0 | Caller thấy 0, không xem là null |
| `void complete()` | Method chạy, không có business return value |
| `Optional.empty()` | Caller thấy Optional rỗng |
| `@Around` cố tình trả null cho `int` | Proxy ném lỗi loại primitive-null mismatch |
| Target ném exception thật | Advice không tự chữa lỗi trừ khi xử lý có chủ đích |
| Self-invocation `this.count()` | Không có interception riêng cho method gọi nội bộ |

## 12. Câu hỏi tự đánh giá

**1. Vì sao `Object result = pjp.proceed()` có thể là `Integer` với method trả `int`?** Vì `proceed()` sử dụng kiểu Object; primitive được boxing trong đường đi qua advice.

**2. `@Around` trả null cho `int` có làm caller nhận 0 không?** Không. Trong Spring AOP proxy thông thường, null không khớp primitive và có thể phát sinh `AopInvocationException`.

**3. `Integer boxedCount()` trả null có phải cùng lỗi không?** Không. Integer là reference type, nên null hợp lệ về Java. Business code vẫn có thể không cho phép.

**4. `void complete()` có `proceed()` trả null thì có lỗi primitive-null không?** Không. Spring loại trừ `void` khỏi kiểm tra null cho primitive có giá trị.

**5. Vì sao `@Around public void log(...)` có thể phá method `String` hoặc `int`?** Vì Spring sẽ trả null cho caller, bỏ qua kết quả `proceed()`.

**6. `@AfterReturning` có thể return String mới để thay result không?** Không theo hợp đồng advice này. Dùng `@Around` nếu cần cố ý thay value.

**7. Nếu `Optional<String>` trả `Optional.empty()`, có nên đổi thành null trong advice không?** Không theo Optional contract thông thường; đó là thay đổi API và có thể gây NPE.

**8. Vì sao dùng `return` trong `finally` nguy hiểm?** Nó ghi đè return và có thể nuốt exception trong Java, khiến proxy thấy outcome sai.

**9. Đổi JDK proxy sang CGLIB có sửa được advice trả null cho int không?** Không. Hai loại Spring proxy đều có giới hạn null-primitive.

**10. Nếu target return đúng nhưng caller nhận lỗi, cần kiểm tra đâu?** Kiểm tra `@Around`, advisors ở ngoài target, đường return sau `proceed()` và proxy return type contract trước khi quy lỗi cho method đích.

## Tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice: Around Advice và return contract](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — AOP Concepts và Advice types](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html)
- [Spring Framework 6.2 — Proxying Mechanisms: JDK/CGLIB, self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework source — JdkDynamicAopProxy kiểm tra null-primitive](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/JdkDynamicAopProxy.java)
- [Spring Framework source — CglibAopProxy kiểm tra null-primitive](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/CglibAopProxy.java)
- [Spring Framework — @AspectJ support, registration và advisors](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-003">Checked exception, unchecked exception và chữ ký proxy interface</a>

<details>
<summary>Xem chi tiết</summary>

# Checked exception, unchecked exception và chữ ký proxy interface trong Spring AOP

## 1. Vì sao cùng một exception lại có thể đổi kiểu khi đi qua Spring proxy?

Khi dùng Spring `@Aspect`, bạn có thể nhìn thấy tình huống tưởng như nghịch lý:

- Trong `@Around` advice, lệnh `throw new IOException("...")` được thực thi.
- Nhưng caller của service lại bắt được **`java.lang.reflect.UndeclaredThrowableException`**, không phải `IOException` trực tiếp.
- Với `IllegalStateException`, caller lại nhận đúng exception gốc.
- Nếu interface method có khai báo `throws IOException`, `IOException` có thể truyền qua JDK proxy mà không bị bọc.

Khác biệt này liên quan trực tiếp đến **hợp đồng `throws` của method được gọi trên JDK dynamic proxy**. Spring AOP dùng proxy để chặn method execution của Spring-managed beans; khi chọn **JDK dynamic proxy**, proxy thực thi giao diện Java. JVM/JDK generated proxy có quy tắc rất cụ thể đối với checked exception được `InvocationHandler` ném ra.

Điều kiện tổng quát:

> Một JDK proxy có thể truyền trực tiếp unchecked `RuntimeException`/`Error` và checked exception **tương thích với `throws` được method trên proxy interface khai báo**. Nếu invocation handler ném một checked exception không tương thích với hợp đồng đó, lời gọi trên proxy sẽ ném `java.lang.reflect.UndeclaredThrowableException` chứa exception gốc.

Đây là quy tắc của JDK proxy mà Spring AOP phải hoạt động bên trong, **không phải mọi `IOException` trong Spring tự động bị bọc**.

**Phạm vi bài:** Cách Spring `@AspectJ` advice tác động đến exception qua **Spring AOP proxy**, đặc biệt interface method, JDK proxy, class-based CGLIB proxy, `@Around` và `@AfterThrowing`. Không đi sâu lý thuyết exception Java chung không liên quan Spring, cũng không bàn AspectJ bytecode weaving ngoài Spring.

### Kết quả học tập

Sau bài học, bạn có thể:

1. Phân biệt checked `Exception` với unchecked `RuntimeException`/`Error` **khi truyền qua Spring JDK proxy**.
2. Hiểu sự khác nhau giữa `throws` của **proxy interface method**, `throws` của implementation method và `throws Throwable` trên advice.
3. Giải thích chính xác lúc nào xuất hiện `java.lang.reflect.UndeclaredThrowableException` và cách tìm cause gốc.
4. Viết `@Around` quan sát/rethrow kết quả mà không vô ý làm thay đổi hợp đồng exception.
5. Bind `@AfterThrowing(throwing="...")` đúng và hiểu vị trí advisor ảnh hưởng exception advice quan sát.
6. Phân biệt giới hạn JDK proxy với class-based CGLIB proxy mà không kết luận quá rộng.
7. Chạy Java 17/Spring Framework demo đầy đủ: normal return, checked declared, checked undeclared từ advice, unchecked exception.
8. Chẩn đoán vấn đề exception cùng với proxy type, self-invocation, transaction và nhiều advisors.

## 2. Ba lớp hợp đồng liên quan đến một lời gọi

Giả sử caller thực hiện:

```java
ReportApi api = context.getBean(ReportApi.class);
api.load();
```

Trong Spring AOP có ba lớp cần tách:

| Lớp | Ví dụ | Vai trò |
| --- | --- | --- |
| **Proxy interface API** | `interface ReportApi { String load() throws IOException; }` | Hợp đồng mà caller sử dụng; JDK-generated proxy method có danh sách checked exceptions được phép khai báo |
| **Target implementation** | `class ReportService implements ReportApi` | Chứa mã Java nghiệp vụ; có thể trả kết quả hoặc throw |
| **Spring advice/interceptor** | `@Around ... throws Throwable` | Có thể gọi `proceed()`, bắt/rethrow hoặc tạo exception mới trước/sau target |

Đường đi thông thường:

```text
Caller (kiểu ReportApi)
    |
    v
JDK-generated Spring AOP proxy
    |
    v
JdkDynamicAopProxy / InvocationHandler
    |
    +--> Spring advisor/interceptor chain
    |       |
    |       +--> @Around advice (throws Throwable)
    |       |
    |       +--> other advisors, if any
    |       |
    |       +--> target method
    |
    v
Proxy method boundary checks outgoing Throwable
    |
    +--> checked exception compatible with interface throws: pass through
    +--> RuntimeException / Error: pass through
    +--> undeclared checked exception: wrap in UndeclaredThrowableException
    |
    v
Caller
```

Điểm quan trọng là **`throws Throwable` ở advice không “mở rộng” chữ ký của `ReportApi.load()`**. Java source có thể cho phép advice khai báo `throws Throwable` vì `ProceedingJoinPoint.proceed()` ném `Throwable`, nhưng proxy interface vẫn có hợp đồng `throws` riêng.

## 3. Checked và unchecked trong hợp đồng JDK proxy

### 3.1. Checked exception được interface khai báo

```java
public interface ReportApi {
    String load() throws IOException;
}
```

`IOException` là checked exception được interface method khai báo. Nếu target hoặc advice ném `IOException` và không có interceptor khác biến đổi nó, JDK proxy có thể truyền trực tiếp `IOException` cho caller.

Nếu method khai báo `throws Exception`, `IOException` cũng phù hợp (vì là subtype của `Exception`). Nếu chỉ khai báo `throws FileNotFoundException`, một `IOException` chung không tự động phù hợp vì không phải subtype của `FileNotFoundException`.

Một interface khai báo `throws IOException` **không buộc** mọi lời gọi phải ném `IOException`; nó chỉ nêu rằng method có thể throw. `RuntimeException`/`Error` vẫn có thể được ném mà không cần liệt kê trong `throws`.

### 3.2. Unchecked exception không cần khai báo trong interface

```java
public interface ReportApi {
    String load();
}
```

Nếu target hoặc advice ném `IllegalStateException`, nó là subtype của `RuntimeException`, nên không gặp hạn chế checked-exception signature của JDK proxy.

Tương tự, `Error` và các subtype là unchecked. Không nên chủ động catch tất cả `Error` rồi thay thành trạng thái thành công; lỗi hệ thống nghiêm trọng cần được truyền/giám sát phù hợp.

### 3.3. Checked exception không có trong proxy interface throws

```java
public interface ReportApi {
    String load();
}
```

Nếu một **Spring `@Around` advice** ném `new IOException("from advice")` khi method `load()` không khai báo `throws IOException`, JDK generated proxy không thể trình bày nó như một checked exception hợp lệ của interface method. Nó bọc exception gốc:

```text
java.lang.reflect.UndeclaredThrowableException
    |
    +-- getUndeclaredThrowable() -> java.io.IOException("from advice")
```

Caller có thể kiểm tra `ex.getUndeclaredThrowable()` hoặc cause để tìm lỗi gốc, nhưng cách sửa bền vững không phải “lúc nào cũng unwrap ở caller”: **cần thiết kế hợp đồng lỗi của service và advice cho đúng**.

### 3.4. Bảng kết quả mong đợi với JDK dynamic proxy

| Throwable thoát khỏi interceptor chain | `interface method throws` | Caller thường nhận |
| --- | --- | --- |
| `new IOException("disk")` | `throws IOException` | `IOException` |
| `new FileNotFoundException("file")` | `throws IOException` | `FileNotFoundException` |
| `new IOException("disk")` | không khai báo checked exception | `UndeclaredThrowableException` chứa `IOException` |
| `new IOException("disk")` | `throws SQLException` | `UndeclaredThrowableException` chứa `IOException` |
| `new IllegalStateException("bad")` | không có `throws` | `IllegalStateException` |
| `new AssertionError("broken")` | không có `throws` | `AssertionError` |
| `new IOException("disk")` | `throws Exception` | `IOException` |

**Giả định:** Đang nói về `java.lang.reflect.Proxy` thông qua Spring JDK proxy, exception thực sự thoát ra từ invocation handler và không bị advisor khác đổi trước boundary. Kết quả khi nhiều interface có cùng method signature nhưng ràng buộc `throws` khác nhau cần xem kỹ hợp đồng của proxy method tương ứng; tránh dựa vào một mặt interface rồi bỏ qua những interface cùng được proxy.

## 4. Lỗi quan trọng: implementation không thể tự mở rộng checked throws

Giả sử:

```java
interface JobApi {
    void run();
}
```

Đoạn implementation dưới đây **không hợp lệ tại compile time**:

```java
class JobService implements JobApi {
    @Override
    public void run() throws IOException {
        throw new IOException("disk");
    }
}
```

Java không cho overriding method tự thêm **checked exception mới, rộng hơn hoặc không tương thích** với hợp đồng interface method. Đây là lỗi `javac`, **không phải lỗi Spring proxy runtime**.

Vậy `IOException` undeclared xuất hiện từ đâu khi interface vẫn không khai báo? Những nguồn thực tế có thể là:
- `@Around` advice tự tạo `IOException` với signature `throws Throwable`.
- Advice `@Before`, `@After`, `@AfterThrowing` hoặc custom interceptor ném checked exception không phù hợp interface.
- Một thư viện/reflection/sneaky-throw technique truyền checked exception qua đường không bị compiler kiểm tra như source-level override.
- Advisor đổi một exception đã khai báo sang checked exception khác không tương thích.

**Mẫu cần tránh:** thay đổi exception trong aspect rồi kỳ vọng caller `catch(IOException)` luôn bắt được dù interface method không khai báo `throws IOException`.

## 5. @Around: proceed() và rethrow đúng để giữ hợp đồng

```java
@Aspect
public class ExceptionAspect {

    @Around("execution(* com.example.app.service..*.*(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (Throwable error) {
            System.out.println(
                "aspect:saw:" + error.getClass().getSimpleName());
            throw error;
        }
    }
}
```

Đây là snippet minh họa chưa có Spring configuration hoàn chỉnh; code chạy đầy đủ có ở phần 9.

**Vì sao khai báo `throws Throwable`?** `ProceedingJoinPoint.proceed()` khai báo `throws Throwable`. Advice có quyền quan sát rồi rethrow nguyên Throwable; nhưng quyết định cuối cùng caller nhận exception trực tiếp hay wrapper vẫn chịu ảnh hưởng của **proxy method contract và các advisor khác**.

Không viết:

```java
catch (Throwable error) {
    throw new IOException("wrapped", error);
}
```

nếu pointcut advice bao phủ nhiều service methods mà một số method không khai báo `IOException`. Nếu dùng JDK proxy, phần lớn những lời gọi ấy có thể bắt đầu sinh `UndeclaredThrowableException` ở caller.

### 5.1. Lỗi được tạo **trước** khi proceed

```java
@Around("execution(* com.example.api.JobApi.run(..))")
public Object validate(ProceedingJoinPoint pjp) throws Throwable {
    throw new IOException("validation infrastructure failure");
}
```

Đây là snippet giả định interface method `run()` tồn tại, với checked exception chưa khai báo. Nếu Spring JDK proxy thực hiện method không có `throws IOException`, advice đã ném checked exception và **target không được chạy**; caller có thể nhận wrapper `UndeclaredThrowableException`.

### 5.2. Lỗi từ target và lỗi từ advice khác nhau

Trong logging, nên phân biệt:
- `target_failure`: target logic thực sự đã ném exception;
- `advice_failure`: instrumentation/logging/auditing aspect tự ném exception;
- `proxy_boundary_wrapping`: JDK proxy đổi exception **được phát ra ngoài interceptor chain** thành wrapper do `throws` không tương thích.

Lưu ý `UndeclaredThrowableException` **không chứng minh target ném lỗi**. Nó có thể chỉ là bằng chứng một aspect đã ném checked exception sai hợp đồng, trong khi target không hề được gọi.

### 5.3. Không biến catch-all thành hành vi nuốt lỗi

```java
catch (Throwable error) {
    return null;
}
```

không phải cách sửa `UndeclaredThrowableException`. Nếu target trả primitive hoặc cần kết quả không-null, `null` có thể làm caller lỗi khác. Nếu operation thay đổi trạng thái nghiệp vụ, return bình thường sau lỗi còn có thể làm sai transaction rollback và hành vi ứng dụng. Hãy dùng exception policy có chủ đích, tránh conceal nguyên nhân.

## 6. @AfterThrowing quan sát gì?

Spring `@AfterThrowing` chạy khi **join point phù hợp kết thúc bằng exception**, và có thể bind exception qua `throwing`:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "cause",
    argNames = "cause"
)
public void onThrown(Throwable cause) {
    System.out.println(cause.getClass().getSimpleName());
}
```

Snippet nằm trong Spring `@Aspect` bean. Nếu dùng `Throwable`, binding không tự loại checked exceptions; nếu đổi thành `RuntimeException`, advice có thể chỉ chạy với exception thuộc runtime hierarchy.

**Điểm tinh tế:** Trong JDK proxy, `java.lang.reflect.UndeclaredThrowableException` có thể được tạo **ở lớp proxy/generated method sau khi Spring AOP interceptor chain đã ném IOException ra ngoài**. `@AfterThrowing` nằm **bên trong** Spring AOP chain có thể quan sát `IOException` gốc, trong khi caller bên ngoài nhận `UndeclaredThrowableException`. Không lấy `@AfterThrowing` log làm cam kết exception type cuối cùng của caller.

Ngược lại, nếu một advisor ở phía trong đã tạo wrapper trước khi `@AfterThrowing` chạy, advice có thể nhìn thấy wrapper. Thực tế quan sát luôn phụ thuộc **vị trí interceptor**.

### 6.1. Binding không đổi exception type

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void onChecked(IOException error) {
    // Chỉ quan sát IOException phù hợp; không tự chuyển
    // IOException thành RuntimeException.
}
```

Một `@AfterThrowing` chỉ log và return bình thường **không swallow exception gốc**. Nếu advice tự ném exception mới, exception mới có thể ghi đè exception cũ. Cần cân nhắc tương thích `throws` của proxy method trước khi ném checked exception từ chính advice.

### 6.2. Nếu target tự catch IOException rồi return?

```java
public String safeLoad() {
    try {
        return fetch();
    } catch (IOException ex) {
        return "FALLBACK";
    }
}
```

Đây là snippet minh họa, chưa định nghĩa `fetch()`. Nếu exception được target bắt và method return bình thường, `@AfterThrowing` cho `safeLoad` **không chạy** vì không có exceptional completion của method execution, dù bên trong source đã từng ném `IOException`.

### 6.3. @AfterReturning và @After

- `@AfterReturning` xử lý normal completion; `null` vẫn là normal return.
- `@AfterThrowing` xử lý exceptional completion đi ra từ join point.
- `@After` có semantics finally trên normal/exception.
- `@Around` kiểm soát `proceed()`, có thể catch/rethrow/fallback nên outcome ở bên ngoài chain có thể khác outcome của target.

Cả bốn đều phải được Spring AOP chặn qua proxy mới có hiệu lực.

## 7. JDK proxy và CGLIB: so sánh đúng giới hạn

Spring Framework hỗ trợ hai cơ chế proxy phổ biến:

| Tiêu chí | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Tạo proxy bằng | `java.lang.reflect.Proxy` / interface implementations | Subclass runtime của target class |
| Method client có thể gọi qua proxy | Những method expose bởi interfaces được proxy | Những method có thể truy cập/override theo class proxy |
| Quan hệ với target implementation class | Proxy thường **không instanceof** implementation | Proxy là subclass của implementation |
| Kiểm tra checked exception không khai báo | JDK `InvocationHandler` rule: checked không phù hợp `throws` dẫn đến `java.lang.reflect.UndeclaredThrowableException` | Không tự áp quy tắc `java.lang.reflect.Proxy` của JDK; hành vi thực tế phụ thuộc generated method/interceptor, phải test |
| `final` class | Có thể proxy qua interface nếu target class final vẫn implement interface phù hợp | Không thể subclass final class |
| `final` / `private` target method | JDK proxy có thể intercept interface method được expose (không override target class method) | Không thể override final/private method |
| Self-invocation | Bypass Spring AOP proxy | Bypass Spring AOP proxy |

**Không kết luận “CGLIB bảo đảm mọi checked exception ném trực tiếp không wrap”.** CGLIB có interceptor/code-generation riêng và có những kiểu wrapper của chính CGLIB hoặc framework. Quy tắc đặc thù `java.lang.reflect.UndeclaredThrowableException` phía trên được chuẩn JDK đảm bảo cho **JDK dynamic proxy**. Nếu chọn CGLIB, hãy kiểm tra thực nghiệm loại exception/cause trên phiên bản Spring ứng dụng đang chạy và trên signature target cụ thể.

Cũng **không đổi sang CGLIB chỉ để che một interface throws contract sai**. Nếu API có lỗi Checked exception contract, sửa interface, đổi exception policy hoặc chuyển lỗi thành unchecked loại có ý nghĩa là thiết kế bền vững hơn.

### 7.1. Tham khảo cấu hình Spring

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JdkProxyConfig {
}
```

Trong một Spring context phù hợp, `proxyTargetClass=false` cho phép chọn JDK proxy khi target triển khai interface. Muốn class-based proxy:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class CglibProxyConfig {
}
```

Đây là **hai snippet cấu hình thay thế**, không nên import đồng thời hai configuration vào cùng context rồi dự đoán mỗi bean có một kiểu proxy; thiết lập class-based proxy có thể được hợp nhất giữa các cơ chế auto-proxy. Ví dụ runnable tiếp theo chỉ dùng JDK proxy để kết quả được suy ra chắc chắn từ `InvocationHandler` Javadoc.

## 8. Checked exceptions trong API design và Spring aspects

### 8.1. Giữ hợp đồng explicit khi caller thực sự xử lý lỗi

Nếu caller cần bắt và phục hồi khi `IOException`:

```java
public interface DocumentApi {
    byte[] load(String id) throws IOException;
}
```

Implementation được phép throw `IOException` hoặc subtype. Trong `@Around`, nếu service method và advice rethrow `IOException`, JDK proxy nhận exception phù hợp `throws`.

Một API khai báo `throws Exception` cũng có thể cho phép `IOException`, nhưng nó quá rộng và đẩy gánh nặng xử lý lỗi sang caller. Chọn checked type cụ thể khi contract thật sự cần biểu diễn lỗi phục hồi được.

### 8.2. Khi không thể hoặc không nên khai báo checked exception

Có thể chọn **unchecked domain exception** rõ ràng:

```java
public class ReportUnavailableException extends RuntimeException {
    public ReportUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Trong Spring advice:

```java
try {
    return pjp.proceed();
} catch (IOException ex) {
    throw new ReportUnavailableException("Cannot load report", ex);
}
```

Cả hai là **snippets cần đặt trong project thật**; không dán vào demo nếu chưa đổi code/expected stdout. Chuyển sang unchecked domain exception là **thay đổi hợp đồng lỗi có chủ đích**. Không dùng `RuntimeException` chung không message/cause; cần giữ nguyên `ex` làm cause để debug.

### 8.3. Đừng mở rộng throws chỉ để vừa logging aspect

Một `@Before` ghi log không nên cố throw `IOException` chỉ vì log sink bị lỗi, rồi bắt toàn bộ service interfaces khai báo `throws IOException`. Với instrumentation không critical, cân nhắc logging fallback/retry nội bộ và metric báo lỗi instrument. Với audit bắt buộc, thiết kế contract/thất bại kiểm soát rõ, không vô tình thay exception type tại JDK proxy boundary.

### 8.4. Khi nhiều interfaces có cùng method

JDK proxy có thể implement **nhiều** interfaces. Nếu chúng có method với cùng tên/chữ ký tham số nhưng `throws` khác nhau, exception type được phép có thêm ràng buộc. Đừng chỉ kiểm tra interface mà caller tình cờ cast vào; xem tất cả proxied interfaces có liên quan, compiler/JDK proxy quy tắc cho duplicate method signatures và viết integration test trên proxy thực.

## 9. Ví dụ Java 17 + Spring Framework đầy đủ

Ví dụ sử dụng một Spring service implementing interface có bốn operations:

1. `success()`: trả bình thường.
2. `declaredChecked() throws IOException`: target ném `IOException` đã khai báo hợp lệ.
3. `undeclaredChecked()`: **không có `throws IOException`**, nhưng `@Around` tự ném `IOException` trước khi target được gọi.
4. `failUnchecked()`: target ném `IllegalStateException` (unchecked).

Điểm mấu chốt: sự khác nhau giữa **checked được phép** và **checked không được phép** xuất hiện tại method boundary của **JDK dynamic proxy**, chứ không cần viết implementation Java không hợp lệ.

**Môi trường**: Java 17+, Spring Framework `6.1.14`, AspectJ Weaver `1.9.22.1`, Gradle. Dùng Spring Framework thuần, không phụ thuộc Spring Boot auto-configuration.

### 9.1. Cấu trúc project

```text
spring-aop-exception-contract/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/proxycase/
    ├── ReportApi.java
    ├── ReportService.java
    ├── CheckedAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 9.2. Gradle

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-exception-contract'
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
    mainClass = 'demo.proxycase.DemoApplication'
}
```

### 9.3. Interface contract

`src/main/java/demo/proxycase/ReportApi.java`:

```java
package demo.proxycase;

import java.io.IOException;

public interface ReportApi {
    String success();

    String declaredChecked() throws IOException;

    String undeclaredChecked();

    String failUnchecked();
}
```

Trong bốn methods, **chỉ `declaredChecked()` khai báo `throws IOException`**. Đây là chi tiết quyết định behavior JDK proxy ở phần kiểm chứng.

### 9.4. Target implementation

`src/main/java/demo/proxycase/ReportService.java`:

```java
package demo.proxycase;

import java.io.IOException;

public class ReportService implements ReportApi {

    @Override
    public String success() {
        System.out.println("target:success");
        return "OK";
    }

    @Override
    public String declaredChecked() throws IOException {
        System.out.println("target:declaredChecked");
        throw new IOException("disk");
    }

    @Override
    public String undeclaredChecked() {
        System.out.println("target:undeclaredChecked");
        return "UNEXPECTED";
    }

    @Override
    public String failUnchecked() {
        System.out.println("target:failUnchecked");
        throw new IllegalStateException("boom");
    }
}
```

`undeclaredChecked` được viết **hợp lệ**: nó không khai báo và cũng không tự ném checked exception. Trong demo, exception checked không được khai báo sẽ xuất phát từ Spring `@Around`, không phải từ override Java trái luật.

### 9.5. Aspect tạo và quan sát các đường exception

`src/main/java/demo/proxycase/CheckedAspect.java`:

```java
package demo.proxycase;

import java.io.IOException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class CheckedAspect {

    @Around(
        "target(demo.proxycase.ReportService)"
        + " && execution(public * *(..))"
    )
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();

        System.out.println("around:enter:" + method);

        try {
            if ("undeclaredChecked".equals(method)) {
                throw new IOException("advice-checked");
            }
            return pjp.proceed();
        } catch (Throwable error) {
            System.out.println(
                "around:throw:" + method
                + ":" + error.getClass().getSimpleName());
            throw error;
        } finally {
            System.out.println("around:finally:" + method);
        }
    }
}
```

**Vì sao `@Around` khai báo `throws Throwable` không vi phạm Java compile?** Đây là advice method riêng, không phải implementation của `ReportApi`. Spring gọi nó trong interceptor chain, và `proceed()` có hợp đồng `throws Throwable`. Khi exception đi **ra ngoài** chain để JDK proxy trả cho caller, `throws` của **`ReportApi` method** mới là ranh giới cần xét.

**Điều demo cố ý làm:** Với `undeclaredChecked`, aspect ném `IOException` *trước khi gọi `proceed()`*. Vì thế **không có** dòng `target:undeclaredChecked` trên stdout. Nếu xuất hiện dòng ấy, advice đã đi vào target và demo không còn minh họa “checked do advice tạo trước target” như dự định.

### 9.6. Cấu hình Spring

`src/main/java/demo/proxycase/AppConfig.java`:

```java
package demo.proxycase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    public ReportApi reportApi() {
        return new ReportService();
    }

    @Bean
    public CheckedAspect checkedAspect() {
        return new CheckedAspect();
    }
}
```

`@Bean reportApi()` trả về interface `ReportApi`; runtime object thực là `ReportService` implementing interface. `@EnableAspectJAutoProxy(proxyTargetClass = false)` dùng proxy theo interface trong cấu hình này.

`@Bean checkedAspect()` đăng ký aspect; `@Aspect` riêng lẻ không biến một Java class thành Spring bean. Vì pointcut xét target `ReportService`, advice áp dụng cho method execution của service qua proxy, không tự advise Spring infrastructure hoặc chính aspect.

### 9.7. Main kiểm tra exception phía caller

`src/main/java/demo/proxycase/DemoApplication.java`:

```java
package demo.proxycase;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            ReportApi api = context.getBean(ReportApi.class);
            System.out.println(
                "jdkProxy=" + AopUtils.isJdkDynamicProxy(api));

            System.out.println("caller:success:" + api.success());

            try {
                api.declaredChecked();
            } catch (IOException ex) {
                System.out.println("caller:declared:"
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
            }

            try {
                api.undeclaredChecked();
            } catch (UndeclaredThrowableException ex) {
                Throwable original = ex.getUndeclaredThrowable();
                System.out.println("caller:undeclared:"
                    + ex.getClass().getSimpleName()
                    + ":" + original.getClass().getSimpleName()
                    + ":" + original.getMessage());
            }

            try {
                api.failUnchecked();
            } catch (IllegalStateException ex) {
                System.out.println("caller:unchecked:"
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage());
            }
        }
    }
}
```

**Lưu ý thiết kế:** Caller của `api.undeclaredChecked()` **không thể** viết `catch(IOException ex)` quanh riêng lời gọi ấy trong Java source nếu không có statement nào khác trong vùng try có thể throw IOException: compiler có thể báo checked exception “never thrown”. Vì vậy demo bắt `UndeclaredThrowableException`, một `RuntimeException`.

Trong xử lý lỗi production, thường nên thiết kế interface/advice consistent để caller không phải unwrap wrapper. Đoạn `getUndeclaredThrowable()` ở đây chỉ nhằm **minh họa nguyên nhân**.

### 9.8. Chạy project

Tại thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu project có Gradle Wrapper, sử dụng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

### 9.9. Stdout dự kiến của Java code

```text
jdkProxy=true
around:enter:success
target:success
around:finally:success
caller:success:OK
around:enter:declaredChecked
target:declaredChecked
around:throw:declaredChecked:IOException
around:finally:declaredChecked
caller:declared:IOException:disk
around:enter:undeclaredChecked
around:throw:undeclaredChecked:IOException
around:finally:undeclaredChecked
caller:undeclared:UndeclaredThrowableException:IOException:advice-checked
around:enter:failUnchecked
target:failUnchecked
around:throw:failUnchecked:IllegalStateException
around:finally:failUnchecked
caller:unchecked:IllegalStateException:boom
```

Đây là những dòng do Java code tự in, không tính Gradle, Spring logging hoặc các thông tin môi trường khác.

### 9.10. Phân tích output theo chữ ký interface

| Phương thức | Checked `throws` trên `ReportApi` | Exception gốc trong advice/target | Caller nhận |
| --- | --- | --- | --- |
| `success()` | Không | Không | `"OK"` |
| `declaredChecked()` | `IOException` | `IOException("disk")` từ target | `IOException("disk")` |
| `undeclaredChecked()` | Không | `IOException("advice-checked")` từ `@Around` | `java.lang.reflect.UndeclaredThrowableException` |
| `failUnchecked()` | Không | `IllegalStateException("boom")` từ target | `IllegalStateException("boom")` |

Ba đặc điểm đáng quan sát:

1. Log `around:throw:undeclaredChecked:IOException` xuất hiện **trước** dòng caller nhận `UndeclaredThrowableException`. Advice nhìn thấy exception gốc, còn JDK proxy boundary thay đổi hình thức ngoại lệ.
2. Không có `target:undeclaredChecked`: ngoại lệ checked được tạo **trong aspect**, target chưa chạy. Do đó không được kết luận target thất bại chỉ từ việc caller nhận `UndeclaredThrowableException`.
3. `failUnchecked` được truyền nguyên loại `IllegalStateException` vì nó là unchecked; interface method không cần liệt kê `throws IllegalStateException`.

Đây là kết quả của cấu hình demo JDK proxy. Nếu thêm một aspect khác để catch, wrap hoặc swallow exception trước khi tới generated proxy, output phải được cập nhật theo advisor ordering mới.

## 10. Khi nào @Around tạo UndeclaredThrowableException ngoài mong muốn?

Có một số pattern lỗi phổ biến.

### 10.1. Logging aspect ném checked exception mới

```java
@Around("execution(* demo.proxycase..*.*(..))")
public Object log(ProceedingJoinPoint pjp) throws Throwable {
    auditSink.write("start"); // giả sử có thể throw IOException
    return pjp.proceed();
}
```

Đây là snippet **không độc lập**: `auditSink` là dependency giả định. Nếu `auditSink.write` throw checked `IOException` và proxy interface method được advice không khai báo IOException, JDK proxy có thể bọc. Thực tế target có thể chưa chạy.

Biện pháp: quyết định rõ **audit failure policy**. Nếu audit bắt buộc, thể hiện lỗi trong service contract/domain unchecked exception. Nếu audit không critical, kiểm soát/log lỗi instrumentation riêng thay vì vô tình làm mọi API bị wrapper.

### 10.2. After-throwing advice thay exception gốc bằng checked type khác

```java
@AfterThrowing(
    pointcut = "execution(* demo.proxycase..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void changeFailure(Throwable error) throws IOException {
    throw new IOException("audit failed", error);
}
```

Đoạn này **không khuyến nghị**. Khi target đã throw `IllegalStateException` nhưng after-throwing advice đổi thành checked `IOException` mà interface method không khai báo, JDK proxy có thể wrap `IOException`. Caller mất cái nhìn trực tiếp về lỗi target; original exception chỉ còn dưới cause. Một advice quan sát lỗi nên log và return, không ném exception mới trừ khi có policy cụ thể.

### 10.3. Sai lầm “advice throws Throwable là đủ”

`throws Throwable` trên advice chỉ khiến Java compiler chấp nhận những exception đi qua lời gọi `proceed()`. Nó **không chỉnh bytecode method signature của interface** và không thông báo cho caller nên bắt `IOException`. Cần đánh giá tầng API interface và tầng advice riêng.

### 10.4. Luôn unwrap ở caller thay vì sửa hợp đồng

Việc unwrap `UndeclaredThrowableException` có thể hữu ích trong diagnostics, nhưng nếu ứng dụng phải unwrap ở mọi caller vì aspect liên tục tạo checked exception sai interface contract, đó là dấu hiệu sai thiết kế. Hãy thống nhất:
- Những checked exception caller cần xử lý;
- Những unchecked domain exceptions mô tả lỗi không thể phục hồi ở tầng này;
- Những lỗi instrumentation không nên phá operation;
- Thứ tự các advisor được phép catch/wrap.

## 11. Spring transaction và rollback: không đồng nhất với checked/unchecked qua proxy

Với Spring Framework 6.1, Spring declarative transaction thường mặc định rollback với `RuntimeException` hoặc `Error`; checked `Exception` thường không tự kích hoạt rollback nếu không cấu hình `rollbackFor` phù hợp.

**Đây là quy tắc transaction**, **khác** quy tắc JDK proxy wrapping:

- JDK proxy quyết định caller nhìn thấy direct checked exception hay `UndeclaredThrowableException` từ interface `throws`.
- Spring transaction interceptor quyết định rollback theo exception mà **nó quan sát tại vị trí trong advisor chain** và rollback rules.
- Một exception checked có thể được `@Around` catch thành unchecked hoặc ngược lại trước khi transaction interceptor quan sát, tùy order.
- Nếu transaction interceptor đã quyết định rollback rồi, outer advice đổi exception thành fallback không tự hoàn nguyên quyết định đó.

Không suy ra “caller thấy `UndeclaredThrowableException` nên transaction chắc chắn rollback”. Cần inspect vị trí transaction advisor và exception thực sự mà nó đã nhận.

### Ví dụ policy theo domain

Nếu một thao tác ghi database phải rollback khi `BusinessCheckedException` thoát ra, có thể khai báo:

```java
@Transactional(rollbackFor = BusinessCheckedException.class)
public void applyChanges() throws BusinessCheckedException {
    // ...
}
```

Đây là snippet minh họa cần `@Transactional`, class `BusinessCheckedException`, Spring transaction manager và transaction AOP config. Nó không làm JDK proxy interface tự thêm `throws BusinessCheckedException`. Nếu service được gọi qua interface JDK proxy, interface contract vẫn phải tương thích để caller thấy checked exception trực tiếp.

## 12. JDK proxy có luôn được Spring sử dụng khi có interface?

Trong Spring AOP, nếu target triển khai interface, mặc định thường chọn JDK dynamic proxy, nhưng **không thể chỉ nhìn “có interface” là kết luận chắc chắn**:
- `@EnableAspectJAutoProxy(proxyTargetClass = true)` buộc class-based proxy;
- Spring Boot có thể cung cấp mặc định khác tùy phiên bản/properties/configuration;
- Các auto-proxy creators trong cùng context có thể hợp nhất cấu hình;
- Custom `ProxyFactory` có thể được thiết lập `setProxyTargetClass(true)`.

Kiểm tra thực tế:

```java
Object proxy = context.getBean("reportApi");
System.out.println(AopUtils.isAopProxy(proxy));
System.out.println(AopUtils.isJdkDynamicProxy(proxy));
System.out.println(AopUtils.isCglibProxy(proxy));
```

Đây là snippet chẩn đoán cần biến `context` và import `AopUtils`, không phải một file Java chạy độc lập.

Với JDK proxy, cách tốt nhất thường là inject interface và giữ checked `throws` contract rõ ràng. Với CGLIB, client có thể nhận class-based proxy, nhưng cần hiểu giới hạn `final`/`private` và không kỳ vọng checked-exception wrapping y hệt JDK proxy.

## 13. Self-invocation và ngoại lệ: proxy boundary bị bỏ qua

Giả sử target implementation:

```java
public void outer() {
    inner();
}

public void inner() {
    throw new IllegalStateException("boom");
}
```

Nếu caller gọi `outer()` qua Spring proxy:
- Advice match `outer()` có thể chạy.
- Lời gọi `this.inner()` ở **bên trong target** không qua proxy, nên advice chỉ match `inner()` không được kích hoạt cho lời gọi nội bộ.
- Ngoại lệ từ `inner()` vẫn có thể thoát qua `outer()` rồi tới proxy caller, nơi các rules exception/proxy interface signature áp dụng cho **method `outer()` được gọi**.

Đó là khác biệt giữa “Spring advice không chạy” và “Java exception không lan truyền”. Self-invocation không vô hiệu hóa Java exception; nó chỉ bypass Spring AOP interceptor chain cho lời gọi nội bộ.

Nếu muốn advice cho `inner()`, tách nó sang một Spring-managed collaborator và gọi qua proxy của collaborator. Đừng dùng `getTarget()` gọi trực tiếp rồi mong transaction/logging interceptors vẫn chạy.

## 14. Bảng xử lý lỗi nhanh

| Triệu chứng | Giải thích khả dĩ | Cách khắc phục |
| --- | --- | --- |
| Caller thấy `UndeclaredThrowableException` sau lời gọi JDK proxy | Checked exception không phù hợp `throws` của proxied interface method | Xem `getUndeclaredThrowable()`, xác định advice/target tạo lỗi, sửa API/policy |
| Aspect log `IOException` nhưng caller thấy wrapper | Wrapping diễn ra ở boundary JDK generated proxy sau inner chain | Phân biệt exception trong aspect với caller-visible exception |
| `IllegalStateException` qua proxy không bị wrap | Unchecked exception không cần `throws` | Đây là hành vi bình thường |
| Đã khai báo `throws IOException` trên advice nhưng vẫn wrap | Advice signature không thay interface signature | Khai báo checked type trong interface nếu contract cho phép |
| Implementation tự thêm `throws IOException` khi interface không cho phép, build fail | Java override rule không cho broaden checked exceptions | Sửa interface/implementation design, không phải Spring config |
| `@AfterThrowing` không thấy lỗi từ aspect ngoài | Advice đó chưa đi qua pointcut/interceptor hoặc exception sinh ở ngoài vị trí nó quan sát | Kiểm tra advisor chain/order |
| Target không chạy nhưng caller thấy IOException wrapper | `@Around`/`@Before` ném lỗi trước `proceed()` | Kiểm tra code instrumentation, dòng trước proceed |
| Caller `catch(IOException)` không compile cho API không khai báo | Java compiler xét method contract | Bắt wrapper RuntimeException để debug, sửa API |
| Đổi CGLIB và lỗi biến đổi | Kiểu proxy thực đã đổi; class-based proxy có cơ chế intercept khác | Test exception/cause, không che lỗi contract bằng đổi proxy |
| Spring transaction không rollback dù aspect log exception | Checked exception hoặc catch/fallback và order/rules | Kiểm tra transaction advisor vị trí, `rollbackFor` |
| Exception ném nội bộ nhưng after-throwing không chạy | Target đã catch rồi return, hoặc self-invocation không đi qua proxy | Kiểm tra đường gọi/return của target |
| Đổi exception trong `@AfterThrowing` làm mất lỗi gốc | Advice tự throw lỗi mới thay lỗi trước | Preserve original exception/cause, log an toàn |
| JDK proxy không expose method implementation thêm | Interface chưa khai báo method | Thiết kế public service API hoặc dùng class proxy nếu thật sự cần |
| Không thấy proxy type dự đoán | `proxyTargetClass`/Boot/custom infrastructure khác dự kiến | Dùng `AopUtils.isJdkDynamicProxy`, `isCglibProxy` |

## 15. Checklist thiết kế @Aspect xử lý exceptions

- [ ] Xác nhận proxy đang là **JDK dynamic proxy** hay **CGLIB**, không suy đoán từ tên target class.
- [ ] Nếu JDK proxy, kiểm tra `throws` của **interface method mà caller invoke**.
- [ ] Không cố khai báo checked exception rộng hơn ở implementation override.
- [ ] `@Around` mặc định `return pjp.proceed()`, log rồi **rethrow nguyên exception** nếu không có fallback policy.
- [ ] `@Before`/`@AfterThrowing`/`@After` không vô tình ném checked exception mới mà interface không cho phép.
- [ ] Không dùng `throws Throwable` trên advice như cách “mở rộng” interface exception contract.
- [ ] Nếu đã gặp `UndeclaredThrowableException`, tìm underlying checked cause trước khi phân tích business failure.
- [ ] Bảo toàn `cause` khi chủ đích chuyển sang domain unchecked exception.
- [ ] Không log hoặc nuốt `Throwable` rộng một cách nguy hiểm; cân nhắc `Error` và fatal problems.
- [ ] Khi xét `@AfterThrowing`, biết chính xác nó nằm đâu trong advisor chain và quan sát exception nào.
- [ ] Với transaction, đặt rollback rules đúng và kiểm thử trên Spring proxy thật.
- [ ] Có tests normal/checked declared/checked undeclared/unchecked exception.
- [ ] Có tests cho proxy type và interface exposure khi ứng dụng có thể thay đổi AOP config.
- [ ] Không quên self-invocation: `this.method()` không tạo một Spring AOP intercepted invocation mới.
- [ ] Với multi-interface duplicate signatures, kiểm thử checked-exception contract và generated proxy thực tế.

## 16. Kết luận

Trong Spring AOP, exception caller nhìn thấy được quyết định **đồng thời** bởi:

1. **Outcome của target/advice/interceptor chain** — nó ném exception gốc hay trả bình thường?
2. **Proxy type** — dùng JDK dynamic proxy hay class-based CGLIB?
3. **Method contract tại proxy boundary** — với JDK proxy, checked exception có tương thích danh sách `throws` của interface method không?

**`IOException` là checked exception; `IllegalStateException` là unchecked.** Một checked exception phù hợp `throws` của interface có thể truyền trực tiếp qua JDK proxy; một checked exception do Spring advice ném ra nhưng không được interface method cho phép có thể bị bọc thành **`java.lang.reflect.UndeclaredThrowableException`**. Unchecked exception thường truyền trực tiếp.

**`throws Throwable` của `@Around` không thay đổi hợp đồng `throws` của interface.** Vì vậy, đừng tìm cách chữa lỗi bằng việc chỉ sửa advice signature. Thiết kế interface, domain exceptions, proxy configuration và advisor ordering có chủ đích; khi debug, quan sát cả exception trong advice và exception phía caller.

### Tài liệu tham khảo

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK dynamic proxy, CGLIB, lựa chọn proxy và các giới hạn.
- [Spring Framework — AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html): mặc định proxy theo interface, class-based proxy.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Around`, `@AfterThrowing`, propagation và binding.
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): behavior when advice throws checked exception not compatible with signature.
- [Java SE 17 — InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): quy tắc `throws` từ handler và `UndeclaredThrowableException`.
- [Java SE 17 — UndeclaredThrowableException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html): wrapper RuntimeException cho undeclared checked exception.
- [Spring Framework — Declarative Transaction Rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): rollback rules độc lập với quy tắc JDK proxy wrapping.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-004">Ảnh hưởng của ngoại lệ do chính advice phát sinh</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của ngoại lệ do chính advice phát sinh trong Spring AOP

## 1. Vì sao ngoại lệ bên trong advice đặc biệt nguy hiểm?

Trong Spring Framework, lời gọi tới một Spring bean có thể đi qua **một chuỗi các advisors/interceptors** trước khi đến method nghiệp vụ (*target method*). Mỗi advice do `@Aspect` khai báo cũng chạy trên đường đi này. Vì vậy, nếu **code trong advice tự ném exception**, kết quả caller quan sát được có thể khác hoàn toàn với kết quả target dự định trả về hoặc lỗi gốc target đã ném.

Các câu hỏi thực tế:

- Nếu một `@Before` advice ném lỗi, target có được gọi không?
- Target đã `return "OK"`, nhưng `@AfterReturning` bị lỗi khi ghi metric: caller nhận gì?
- Target ném `IllegalArgumentException`, nhưng `@AfterThrowing` ném tiếp `IllegalStateException`: lỗi nào đi tới client?
- `@After` như `finally` ném lỗi trên nhánh thành công và trên nhánh đang xử lý lỗi thì có gì khác?
- `@Around` bắt/đổi/ném exception từ `proceed()` có thể che mất lỗi ban đầu như thế nào?
- Một outer aspect có thể quan sát được lỗi từ advice bên trong hay không, và điều gì quyết định điều đó?

**Thông điệp quan trọng:** một advice không tự động trở thành “ngoài luồng” đối với đường trả về của lời gọi. Exception trong advice có thể **dừng phần thực thi phía trong, làm một normal return thành exceptional return, hoặc thay thế exception gốc**. Trong Spring AOP, hiệu quả cụ thể phụ thuộc **vị trí advice trong proxy interceptor chain**.

Bài tập trung vào **Spring Aspect (@AspectJ style trên Spring AOP proxy)**. Những kiến thức chung về ngoại lệ Java và AspectJ weaving chỉ nhắc đến khi cần phân biệt với hành vi thực tế của Spring.

## 2. Mô hình gọi qua proxy: nơi exception có thể xuất hiện

Một proxy có thể sắp xếp các interceptors thành những tầng lồng nhau:

```text
Caller
  |
  v
Spring AOP proxy
  |
  +-- Outer @Around: enter
        |
        +-- @Before
        |     |
        |     +-- nếu throw -> phần phía trong chưa chạy
        |
        +-- Target business method
        |     |
        |     +-- return hoặc throw
        |
        +-- @AfterReturning / @AfterThrowing
        |     |
        |     +-- nếu advice này throw -> kết quả phía ngoài thay đổi
        |
        +-- @After (finally)
        |     |
        |     +-- nếu throw -> có thể che cả return lẫn lỗi cũ
        |
        +-- Outer @Around: return / catch / finally
  |
  v
Caller nhận value hoặc exception cuối cùng
```

Sơ đồ là **mô hình khái niệm**, không phải thứ tự tuyệt đối của mọi cấu hình. Từng `@Aspect` có mức precedence, có thể thêm transaction/cache/async advisor, và advice có thể short-circuit hoặc gọi `proceed()` nhiều lần. Với một lời gọi nhất định, chỉ các advisors có pointcut phù hợp mới tham gia.

Điều chắc chắn của cơ chế Java call stack: **exception được ném trong một tầng sẽ đi ra phía ngoài cho tới khi có tầng bắt nó**. Nếu một tầng trong quá trình thoát lại ném exception mới, tầng ngoài có thể chỉ nhìn thấy exception mới ấy.

### Ví dụ tuyến lỗi đơn giản

```text
Target:         throw BusinessException("business failed")
After advice:   throw TelemetryException("log unavailable")
Outer advice:   catch TelemetryException
Caller:         nhận TelemetryException
```

Nếu `TelemetryException` không giữ `BusinessException` làm `cause` hoặc `suppressed`, thông tin nguyên nhân gốc có thể rất khó truy vết. Đó là lý do việc ném lỗi từ logging, metrics hay cleanup advice phải được thiết kế có chủ đích.

## 3. Bảng tổng quan theo từng loại advice

Bảng giả định code advice thực sự được kích hoạt, có pointcut phù hợp và không có tầng phía ngoài chuyển lỗi thành fallback:

| Advice tự ném exception | Điều xảy ra với target | Caller thường thấy |
| --- | --- | --- |
| `@Before` | Nếu advice throw **trước khi đi vào target**, target không được thực thi | Exception từ `@Before` |
| `@AfterReturning` | Target **đã return bình thường**; advice chạy rồi throw | Exception từ `@AfterReturning` thay vì return value |
| `@AfterThrowing` | Target **đã ném lỗi phù hợp**; advice nhận lỗi rồi throw lỗi khác | Exception mới do advice ném, trừ khi tầng ngoài biến đổi |
| `@After` | Phần thực thi bên trong đã đi tới nhánh finally; advice throw | Có thể che normal return **hoặc** exception ban đầu |
| `@Around` trước `proceed()` | Chưa gọi phần chain bên trong, target có thể không chạy | Exception từ around advice |
| `@Around` sau `proceed()` trả bình thường | Target/inner chain có thể đã hoàn thành bình thường | Exception từ around advice |
| `@Around` catch rồi throw mới | Target/inner chain đã phát sinh Throwable | Throwable mới, có thể giữ lỗi cũ làm cause |
| `@Around` catch rồi return fallback | Target/inner chain đã có lỗi | Caller nhận fallback, không nhận lỗi gốc nếu outer chain không phát sinh lỗi khác |

Bảng này **không** nói rằng advice luôn bị ép phải ném lại ngoại lệ gốc. Trong Java, `@Before`/`@AfterReturning`/`@AfterThrowing`/`@After` thường là method `void`; nhưng body của chúng vẫn có thể ném unchecked exception. Spring không tự cô lập mọi lỗi advice khỏi caller.

### Ba điều phải luôn tách

1. **Lỗi target:** exception được target method tạo ra khi thực thi nghiệp vụ.
2. **Lỗi advice:** exception phát sinh từ chính việc kiểm tra quyền, ghi log, tăng metric, cleanup, transform hoặc retry.
3. **Lỗi caller quan sát:** exception cuối cùng thoát khỏi **toàn bộ Spring proxy chain**. Đây có thể là lỗi target, lỗi advice, wrapper exception hoặc không có lỗi nếu outer around đã trả fallback.

Không dùng `@AfterThrowing` để suy luận đơn giản rằng “mọi exception caller nhận đều do target ném”.

## 4. `@Before` ném exception: target không được gọi

Ví dụ một advice kiểm tra điều kiện trước method execution:

```java
@Aspect
public class PermissionAspect {
    @Before("execution(* com.example.app.service..*.submit(..))")
    public void check() {
        throw new IllegalStateException("permission unavailable");
    }
}
```

Đây là **snippet trong Spring `@Aspect` bean**, chưa phải chương trình độc lập. Nếu advice khớp và ném lỗi trước khi invocation được tiếp tục:

```text
CLIENT -> Spring proxy -> @Before throws
                             |
                             +--> TARGET.submit() không chạy
                             |
                             +--> exception đi ra bên ngoài
```

Điều này có thể **là chủ đích** nếu advice thực hiện authorization và dùng exception để từ chối lời gọi. Nhưng cũng có thể là **lỗi ngoài ý muốn** khi advice chỉ có nhiệm vụ log, ví dụ `logger.info(...)` hoặc một formatter tự ném exception.

### Có chắc mọi `@After` khác vẫn chạy khi `@Before` throw?

**Không.** Nếu `@Before` nằm ở tầng ngoài và ném lỗi **trước khi một after/finally interceptor ở phía trong được gọi**, interceptor phía trong chưa được tham gia nên không thể tự chạy cleanup. Ngược lại, một outer `@Around` đã bắt đầu trước before advice có thể dùng `try/finally` để ghi nhận sự cố.

Thứ tự giữa các advice trong Spring được quyết định bởi precedence và nesting; không được hiểu `@After` là “global finally bắt mọi lỗi ở bất kỳ tầng nào”.

Nếu cần áp dụng một hành động cleanup **ngay cả khi precondition trước target thất bại**, đặt cleanup ở một tầng ngoài đã thực sự được enter, và test thứ tự advisor cụ thể.

### Kiểm tra bảo toàn side effects

Nếu `@Before` thất bại trước target, target không thực hiện side effects của method đó. Nhưng một advice **ở tầng ngoài** có thể đã làm side effects trước khi lỗi xuất hiện, ví dụ ghi audit request-start hoặc mở span tracing. Không dùng câu “target không chạy” để khẳng định “không có gì đã xảy ra”.

## 5. `@AfterReturning` ném exception: biến thành công thành thất bại

`@AfterReturning` được gọi **chỉ khi phần method execution tương ứng trả về bình thường**. Nó có thể bind giá trị trả về:

```java
@AfterReturning(
    pointcut = "execution(* com.example.app.service..*.complete(..))",
    returning = "result",
    argNames = "result"
)
public void recordSuccess(String result) {
    System.out.println("BUSINESS RETURNED " + result);
    throw new IllegalStateException("metrics unavailable");
}
```

Đây là **snippet minh họa**. Sau khi target thực thi và trả `"OK"`:

```text
Target.complete()       -> return "OK"
@AfterReturning         -> throw IllegalStateException
Outer caller            -> nhận IllegalStateException, không nhận "OK"
```

**Đây là một bẫy quan trọng:** business operation đã thực thi thành công, có thể đã ghi dữ liệu hoặc gửi thông điệp, nhưng client lại nghĩ operation thất bại vì telemetry advice tự ném lỗi.

Hệ quả:

- Client có thể retry request vì nhận 5xx; nếu operation không idempotent, retry có thể tạo **duplicate side effects**.
- Nếu có transaction advisor, exception từ advice **có thể ảnh hưởng transaction** tùy vị trí tương đối của advice và transaction interceptor. Không suy rằng dữ liệu đã commit hoặc rollback chỉ từ dòng log.
- `@AfterReturning` không có chức năng thay return object bằng cách `return` một value từ advice; nhưng **throw exception** vẫn làm đường thoát kết quả trở thành exceptional nếu không có outer handler.

### Advice ném sau khi target return khác exception target

Một outer `@Around` nhìn thấy exception từ `proceed()` trong trường hợp này có thể bắt `IllegalStateException("metrics unavailable")`, dù target hoàn toàn không ném lỗi. Vì vậy outer observer cần phân biệt **lỗi infrastructure/advice** với **lỗi nghiệp vụ**, thông qua typing, cause chain, structured events hoặc design logging thích hợp.

**Spring documentation lưu ý:** `@AfterThrowing` đi kèm trong cùng aspect **không được coi là callback bắt lỗi do một `@AfterReturning` khác trong aspect đó ném ra**. Nếu muốn giám sát lỗi từ toàn chain, thiết kế một outer `@Around` rõ ràng.

## 6. `@AfterThrowing` ném exception mới: che lỗi nghiệp vụ gốc

Giả sử target method ném:

```java
throw new IllegalArgumentException("invalid order");
```

Một after-throwing advice có thể bind throwable gốc:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.placeOrder(..))",
    throwing = "original",
    argNames = "original"
)
public void onFailure(IllegalArgumentException original) {
    System.out.println("FAILED " + original.getMessage());
    throw new IllegalStateException("audit system failed", original);
}
```

Đây là snippet, chưa phải project độc lập. Khi advice ném exception mới, Java control flow thoát **từ advice** bằng `IllegalStateException`:

```text
Target:           IllegalArgumentException("invalid order")
Advice:           receives original
Advice:           throws IllegalStateException("audit system failed", original)
Caller/outer:     sees IllegalStateException
Cause chain:      IllegalArgumentException -> "invalid order"
```

So với việc ném:

```java
throw new IllegalStateException("audit system failed");
```

việc dùng constructor có `cause` giúp giữ đường truy tìm lỗi ban đầu. Nhưng **lựa chọn tốt hơn** đối với một advice chỉ phục vụ audit/log là không ném exception mới làm mất lỗi nghiệp vụ nếu telemetry hỏng.

### Tên `throwing` và kiểu ngoại lệ vẫn quan trọng

- `throwing = "original"` phải khớp tên tham số advice; `argNames = "original"` giúp binding rõ ràng.
- Kiểu `IllegalArgumentException` chỉ match runtime throwable thuộc class đó hoặc subclass.
- Nếu target ném `IOException` và advice chỉ nhận `IllegalArgumentException` thì advice không chạy, nên nó **không thể ném lỗi thứ cấp từ body advice đó** cho trường hợp ấy.
- Nếu dùng `Throwable original`, advice có thể nhận cả `Error` — cân nhắc rất kỹ trước khi gọi hệ thống log/metric có nguy cơ lỗi trong đường xử lý lỗi nghiêm trọng.

### Vì sao `@AfterThrowing` không phải “bộ xử lý mọi lỗi của proxy”?

Tài liệu Spring Framework 6.2 phân biệt rõ: advice này phục vụ **exception từ join point (user-declared target method)**, không được hiểu như general callback bắt exception phát sinh từ các companion `@After` hoặc `@AfterReturning` trong cùng `@Aspect`. Thứ tự advisor/nesting là lý do một exception mới từ advice **không nhất thiết** đi qua mọi `@AfterThrowing` khác.

Muốn xử lý lỗi từ toàn chain, sử dụng một **outer around interceptor/aspect** được xếp thứ tự rõ ràng, rồi kiểm thử những đường gọi thực tế.

## 7. `@After` ném exception: rủi ro của logic trong finally

Trong Spring AOP, `@After` có ngữ nghĩa **after-finally advice**. Nó chạy khi method execution tương ứng kết thúc bình thường hoặc bằng exception:

```java
@After("execution(* com.example.app.service..*.store(..))")
public void release() {
    System.out.println("RELEASING");
    throw new IllegalStateException("cleanup failed");
}
```

Snippet trên cố ý chứa lỗi để minh họa hai nhánh:

### 7.1. Target đã thành công

```text
Target.store()     -> return "STORED"
@After.release()   -> throw IllegalStateException("cleanup failed")
Caller             -> nhận cleanup exception thay vì "STORED"
```

Giống trường hợp `@AfterReturning`, target có thể đã làm side effects nhưng caller nhận lỗi từ việc cleanup.

### 7.2. Target đã ném exception

```text
Target.store()     -> throw IllegalArgumentException("storage rejected")
@After.release()   -> throw IllegalStateException("cleanup failed")
Caller             -> nhận cleanup exception
Original exception -> có nguy cơ không còn trong cause chain
```

Đây là ngữ nghĩa tương tự khi một Java `finally` block ném exception mới và che exception đang lan truyền. Với `@After`, bạn **không có thuộc tính `throwing`** để tự nhận exception gốc. Vì thế, nếu code `@After` throw không gắn nguyên nhân cũ, thông tin lỗi gốc rất dễ bị mất trên đường đi ra client.

### Không được nhầm với `try-with-resources`

Java `try-with-resources` có quy tắc riêng để giữ lỗi từ `try` là primary exception và đưa lỗi `close()` vào **suppressed exceptions**. Spring `@After` tự ném exception **không tự động áp dụng cơ chế suppressed như try-with-resources**. Nếu cần giữ cả hai lỗi, phải thiết kế chính sách giữ cause/suppressed rõ ràng ở tầng có cả hai throwable, ví dụ một outer `@Around`.

### Cleanup nên fail-safe trong phạm vi yêu cầu

Một advice chỉ ghi metric hoặc dọn trạng thái tracing thường nên tránh làm thất bại business operation chỉ vì hệ thống telemetry tạm thời unavailable. Nhưng **không được nuốt mọi lỗi một cách máy móc**: nếu advice thực sự chịu trách nhiệm enforce một invariant bắt buộc, failure cần một contract nghiệp vụ rõ ràng và được test.

## 8. `@Around`: có thể bắt, chuyển đổi, ném mới hoặc nuốt exception

`@Around` nhận `ProceedingJoinPoint` và có quyền điều khiển lời gọi qua `proceed()`. Vì vậy, nó ảnh hưởng mạnh nhất tới exception flow.

### 8.1. Giữ nguyên lỗi và chạy finally

```java
@Around("execution(* com.example.app.service..*.dispatch(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        System.out.println("TRACE finished");
    }
}
```

Nếu phần phía trong ném exception và `finally` không ném thêm lỗi, exception gốc được propagate. Nếu phần phía trong trả bình thường, giá trị đó cũng được giữ.

### 8.2. Chuyển exception nhưng giữ cause

```java
@Around("execution(* com.example.app.service..*.dispatch(..))")
public Object wrapFailure(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalArgumentException original) {
        throw new IllegalStateException(
            "dispatch failed in proxy chain", original
        );
    }
}
```

Ở đây `IllegalStateException` được caller nhận; `original` còn nằm trong `getCause()`. Cần bảo đảm kiểu mới thực sự phù hợp với contract của service/API, không wrap tùy tiện rồi làm lỗi client khó xử lý.

### 8.3. Catch rồi return fallback

```java
@Around("execution(* com.example.app.service..*.lookup(..))")
public Object fallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException original) {
        return "FALLBACK";
    }
}
```

Snippet này hợp lý **chỉ khi method được advice trả kiểu tương thích `String`** và contract nghiệp vụ cho phép fallback. Nếu `pjp.proceed()` ném exception từ một advice phía trong, `catch` ở đây cũng có thể bắt lỗi đó, **không chỉ lỗi target**.

**Cảnh báo:** catch rộng mọi `RuntimeException` trong around advice có thể vô tình biến lỗi authorization, validation, transaction hoặc telemetry thành success. Khi cần fallback, hãy match đúng class exception và kiểm soát phạm vi pointcut.

### 8.4. Tự ném lỗi trước `proceed()`

```java
@Around("execution(* com.example.app.service..*.lookup(..))")
public Object deny(ProceedingJoinPoint pjp) {
    throw new IllegalStateException("precondition failed");
}
```

Target không được gọi vì advice không `proceed()`. Điều này có thể có chủ đích (policy/security), nhưng không phù hợp cho một aspect chỉ ghi log.

### 8.5. Ném lỗi sau `proceed()` thành công

```java
@Around("execution(* com.example.app.service..*.lookup(..))")
public Object postValidate(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    if (result == null) {
        throw new IllegalStateException("unexpected null");
    }
    return result;
}
```

Trong trường hợp này, target có thể đã hoàn thành bình thường, nhưng caller nhận exception do post-validation advice. Hãy phân biệt **đánh giá kết quả của advice** và **business method success** trong logs/metrics.

## 9. Demo runnable: năm loại lỗi advice và một lời gọi thành công

Ví dụ hoàn chỉnh sử dụng **Java 17+, Maven, Spring Framework 6.2.19**. Một Spring bean có các methods cố ý riêng biệt để mỗi method chỉ kích hoạt **một loại failing advice** trong aspect bên trong:

1. `scenarioBefore()`: `@Before` ném lỗi, target không chạy.
2. `scenarioReturning()`: target trả bình thường; `@AfterReturning` ném lỗi.
3. `scenarioThrowing()`: target ném `IllegalArgumentException`; `@AfterThrowing` ném `IllegalStateException` mới **có cause gốc**.
4. `scenarioFinallyNormal()`: target trả bình thường; `@After` ném lỗi.
5. `scenarioFinallyError()`: target ném lỗi; `@After` ném lỗi mới **che mất lỗi gốc**.
6. `scenarioAround()`: target ném lỗi; `@Around` của inner aspect bắt và wrap thành lỗi khác.
7. `scenarioHealthy()`: không có failing advice bên trong, nên target trả bình thường.

Một aspect quan sát bên ngoài mang `@Order(1)`; aspect cố ý gây lỗi bên trong mang `@Order(2)`. Do đó log của **outer around** cho biết exception nào thực sự thoát khỏi nội bộ. **Không chạy riêng các snippet minh họa trước đó cùng demo**, vì chúng có thể tạo advisor bổ sung và thay đổi trace.

### 9.1. Cấu trúc project

```text
spring-aop-advice-exceptions/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AdviceFailureDemo.java
```

### 9.2. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-advice-exceptions</artifactId>
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
                    <mainClass>demo.AdviceFailureDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 9.3. Tệp `src/main/java/demo/AdviceFailureDemo.java`

```java
package demo;

import java.util.function.Supplier;

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

public class AdviceFailureDemo {

    public interface ScenarioService {
        String scenarioBefore();
        String scenarioReturning();
        String scenarioThrowing();
        String scenarioFinallyNormal();
        String scenarioFinallyError();
        String scenarioAround();
        String scenarioHealthy();
    }

    public static class ScenarioServiceImpl implements ScenarioService {
        @Override
        public String scenarioBefore() {
            System.out.println("TARGET before");
            return "BEFORE-OK";
        }

        @Override
        public String scenarioReturning() {
            System.out.println("TARGET returning");
            return "RETURNING-OK";
        }

        @Override
        public String scenarioThrowing() {
            System.out.println("TARGET throwing");
            throw new IllegalArgumentException("business throwing");
        }

        @Override
        public String scenarioFinallyNormal() {
            System.out.println("TARGET finally normal");
            return "FINALLY-OK";
        }

        @Override
        public String scenarioFinallyError() {
            System.out.println("TARGET finally error");
            throw new IllegalArgumentException("business finally");
        }

        @Override
        public String scenarioAround() {
            System.out.println("TARGET around");
            throw new IllegalArgumentException("business around");
        }

        @Override
        public String scenarioHealthy() {
            System.out.println("TARGET healthy");
            return "HEALTHY";
        }
    }

    @Aspect
    @Order(1)
    public static class OuterObserverAspect {

        @Around("execution(* demo..*.scenario*(..))")
        public Object observe(ProceedingJoinPoint pjp) throws Throwable {
            String name = pjp.getSignature().getName();
            System.out.println("OUTER enter " + name);

            try {
                Object result = pjp.proceed();
                System.out.println("OUTER returned " + result);
                return result;
            } catch (Throwable ex) {
                System.out.println(
                    "OUTER caught "
                        + ex.getClass().getSimpleName()
                        + ":" + ex.getMessage()
                );
                throw ex;
            }
        }
    }

    @Aspect
    @Order(2)
    public static class FailingAdviceAspect {

        @Before("execution(* demo..*.scenarioBefore(..))")
        public void failBefore() {
            System.out.println("ADVICE before throws");
            throw new IllegalStateException("before advice failed");
        }

        @AfterReturning(
            pointcut = "execution(* demo..*.scenarioReturning(..))",
            returning = "result",
            argNames = "result"
        )
        public void failAfterReturn(String result) {
            System.out.println(
                "ADVICE afterReturning result=" + result
            );
            throw new IllegalStateException("returning advice failed");
        }

        @AfterThrowing(
            pointcut = "execution(* demo..*.scenarioThrowing(..))",
            throwing = "original",
            argNames = "original"
        )
        public void failAfterThrow(IllegalArgumentException original) {
            System.out.println(
                "ADVICE afterThrowing original="
                    + original.getMessage()
            );
            throw new IllegalStateException(
                "throwing advice failed", original
            );
        }

        @After("execution(* demo..*.scenarioFinally*(..))")
        public void failAfterFinally() {
            System.out.println("ADVICE afterFinally throws");
            throw new IllegalStateException("after advice failed");
        }

        @Around("execution(* demo..*.scenarioAround(..))")
        public Object failAround(ProceedingJoinPoint pjp)
                throws Throwable {
            System.out.println("ADVICE around enters");
            try {
                return pjp.proceed();
            } catch (IllegalArgumentException original) {
                System.out.println(
                    "ADVICE around caught " + original.getMessage()
                );
                throw new IllegalStateException(
                    "around advice failed", original
                );
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {

        @Bean
        public ScenarioService scenarioService() {
            return new ScenarioServiceImpl();
        }

        @Bean
        public OuterObserverAspect outerObserverAspect() {
            return new OuterObserverAspect();
        }

        @Bean
        public FailingAdviceAspect failingAdviceAspect() {
            return new FailingAdviceAspect();
        }
    }

    private static void runCase(
            String label, Supplier<String> operation) {

        System.out.println("=== " + label + " ===");
        try {
            System.out.println("CLIENT value=" + operation.get());
        } catch (RuntimeException ex) {
            System.out.println(
                "CLIENT exception="
                    + ex.getClass().getSimpleName()
                    + ":" + ex.getMessage()
            );

            Throwable cause = ex.getCause();
            if (cause != null) {
                System.out.println(
                    "CLIENT cause="
                        + cause.getClass().getSimpleName()
                        + ":" + cause.getMessage()
                );
            }
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {

            ScenarioService service =
                    context.getBean(ScenarioService.class);

            System.out.println(
                "AOP_PROXY=" + AopUtils.isAopProxy(service)
            );
            System.out.println(
                "JDK_PROXY=" + AopUtils.isJdkDynamicProxy(service)
            );

            runCase("before", service::scenarioBefore);
            runCase("returning", service::scenarioReturning);
            runCase("throwing", service::scenarioThrowing);
            runCase("finally normal", service::scenarioFinallyNormal);
            runCase("finally error", service::scenarioFinallyError);
            runCase("around", service::scenarioAround);
            runCase("healthy", service::scenarioHealthy);
        }
    }
}
```

**Điểm cần đọc kỹ:** tất cả method references `service::scenarioBefore` ... đều gọi **bean reference do context cấp**, không gọi trực tiếp `new ScenarioServiceImpl()`. Spring đang bật AOP auto-proxy và class implement interface, nên JDK proxy phù hợp. `@Order(1)` thấp hơn về giá trị số (cao hơn về precedence) so với `@Order(2)`, vì vậy observer ở ngoài failing aspect khi pointcuts cùng khớp.

### 9.4. Chạy ứng dụng

Trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng stdout do chương trình in theo logic được trình bày** (không tính log Maven hoặc Spring):

```text
AOP_PROXY=true
JDK_PROXY=true
=== before ===
OUTER enter scenarioBefore
ADVICE before throws
OUTER caught IllegalStateException:before advice failed
CLIENT exception=IllegalStateException:before advice failed
=== returning ===
OUTER enter scenarioReturning
TARGET returning
ADVICE afterReturning result=RETURNING-OK
OUTER caught IllegalStateException:returning advice failed
CLIENT exception=IllegalStateException:returning advice failed
=== throwing ===
OUTER enter scenarioThrowing
TARGET throwing
ADVICE afterThrowing original=business throwing
OUTER caught IllegalStateException:throwing advice failed
CLIENT exception=IllegalStateException:throwing advice failed
CLIENT cause=IllegalArgumentException:business throwing
=== finally normal ===
OUTER enter scenarioFinallyNormal
TARGET finally normal
ADVICE afterFinally throws
OUTER caught IllegalStateException:after advice failed
CLIENT exception=IllegalStateException:after advice failed
=== finally error ===
OUTER enter scenarioFinallyError
TARGET finally error
ADVICE afterFinally throws
OUTER caught IllegalStateException:after advice failed
CLIENT exception=IllegalStateException:after advice failed
=== around ===
OUTER enter scenarioAround
ADVICE around enters
TARGET around
ADVICE around caught business around
OUTER caught IllegalStateException:around advice failed
CLIENT exception=IllegalStateException:around advice failed
CLIENT cause=IllegalArgumentException:business around
=== healthy ===
OUTER enter scenarioHealthy
TARGET healthy
OUTER returned HEALTHY
CLIENT value=HEALTHY
```

Đây là **stdout dự kiến từ code**, không phải tuyên bố đã thực thi Maven trong một runtime thực tế. Demo chỉ dùng các beans/aspects đã định nghĩa ở trên. Các logs do framework hoặc Maven phát sinh không thuộc chuỗi dự kiến.

### 9.5. Phân tích vì sao trace có thứ tự này

**Case `before`:** outer around đã chạy và in `OUTER enter`. Failing before advice ném lỗi ngay, nên **không có dòng `TARGET before`**. Observer phía ngoài bắt lỗi do advice và caller nhận lỗi đó.

**Case `returning`:** target trả thành công (có dòng `TARGET returning`), after-returning nhận `"RETURNING-OK"` rồi ném lỗi. Observer và caller thấy **returning advice failed**, không nhận `RETURNING-OK`. Điều này rất nguy hiểm nếu target đã có side effect.

**Case `throwing`:** target ném `IllegalArgumentException("business throwing")`, inner after-throwing nhận lỗi gốc rồi ném `IllegalStateException("throwing advice failed", original)`. Outer observer nhìn thấy lỗi mới, nhưng client vẫn truy ngược lỗi gốc qua `getCause()`.

**Case `finally normal`:** target hoàn thành bình thường, sau đó `@After` ném lỗi. Caller nhận failure, không nhận `FINALLY-OK`. Việc “finally” chạy sau return không có nghĩa nó vô hại với return path.

**Case `finally error`:** target ném `IllegalArgumentException("business finally")` nhưng `@After` ném `IllegalStateException("after advice failed")`. Vì code advice không preserve original, client không có dòng `CLIENT cause`. Đây là ví dụ **mất nguyên nhân gốc**.

**Case `around`:** target ném lỗi nghiệp vụ; inner around bắt rồi wrap có cause; outer nhận wrapper. Không có vấn đề mất cause vì code giữ `original`.

**Case `healthy`:** không có failing advice áp dụng cho method này; observer bên ngoài thấy target return bình thường. Dòng `OUTER returned HEALTHY` đối chiếu với sáu nhánh lỗi, giúp nhận ra outer observer không phải nguyên nhân gây failure.

### 9.6. Khi một method nhận nhiều failing advices thì sao?

Demo tách method theo loại advice **để kết quả rõ và không phụ thuộc thứ tự của nhiều advice cùng loại**. Trong ứng dụng thật, nếu nhiều advice cùng match một method, phải phân tích thứ tự nesting.

Ví dụ với hai aspects:

```text
OuterAspect @Order(1)
    |
    +-- @Around
          |
          +-- InnerAspect @Order(2)
                |
                +-- @AfterReturning hoặc @AfterThrowing
                +-- @After
                |
                +-- Target
```

Nếu inner advice ném exception, outer around có thể bắt exception đó vì `pjp.proceed()` bao trùm toàn bộ chain phía trong. Nếu **outer** advice lại ném exception mới sau khi nó đã `proceed()`, inner advice đã kết thúc trước đó không tự chạy lại để quan sát lỗi của outer.

Đừng giả định thứ tự dòng log khi một aspect chứa nhiều `@After` cùng loại chỉ dựa vào thứ tự khai báo source; tài liệu Spring không bảo đảm ordering như vậy.

## 10. Phân biệt “lỗi advice” và “lỗi target” trong hệ thống lớn

### 10.1. Dùng kiểu exception và cause chain

Một custom exception dành cho lỗi advice có thể phân biệt nguồn:

```java
public class AdviceInfrastructureException
        extends RuntimeException {

    public AdviceInfrastructureException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
```

Đây là **class minh họa độc lập**. Không cần thiết phải khai báo exception mới cho mọi advice nhỏ, nhưng nếu hệ thống thật sự muốn propagate infrastructure failure, một kiểu rõ nghĩa và cause chain đầy đủ giúp:

- Phân biệt lỗi nghiệp vụ với lỗi telemetry/infrastructure.
- Tránh retry business operation sai lý do.
- Dễ tạo logging/alert rules.
- Giữ được stack trace của cả advice và target khi có lỗi gốc.

**Tuy nhiên, typed wrapper không thay thế quyết định thiết kế quan trọng hơn:** liệu một lỗi logging/metrics không thiết yếu có nên làm hỏng business call hay không?

### 10.2. Kiểm tra exception tại outer boundary

Một outer around có thể ghi thông tin context của exception:

```java
@Around("execution(public * com.example.app.service..*.*(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Throwable failure) {
        System.out.println(
            "FAILED " + pjp.getSignature().toShortString()
                + " with " + failure.getClass().getSimpleName()
        );
        throw failure;
    }
}
```

Đây là **snippet** cần aspect bean riêng, đúng pointcut và ordering. Nó quan sát exception từ **phần chain nằm bên trong** nó. Nó không phải universal observer cho mọi bean hoặc mọi lỗi JVM nếu Spring proxy không được gọi.

Không suy rằng exception từ observer luôn là “target failure”; có thể là error của before/after/around advice khác ở phía trong.

### 10.3. Giữ nguyên Throwable bằng `throw failure`

Trong observer snippet, `throw failure;` ném lại đúng object đang có, không tạo exception khác. Nếu code ghi log thành công, caller vẫn nhận same throwable. Đây thường là lựa chọn an toàn khi trách nhiệm của aspect chỉ là giám sát.

Một ngôn ngữ quan trọng trong production logs:

- `method execution observed as failed` không có nghĩa lỗi do target.
- `proxy invocation failed` chính xác hơn khi nguyên nhân có thể là advice.
- `business rejected` chỉ nên dùng khi có bằng chứng từ service contract/type lỗi nghiệp vụ.

## 11. Thiết kế advice an toàn: khi nào nuốt lỗi, khi nào propagate?

Không có quy tắc “catch mọi exception trong advice” áp dụng cho mọi yêu cầu. Phải xét **mục đích advice**.

### Trường hợp không thiết yếu: logging hoặc metric best-effort

Một cách phòng ngừa:

```java
@Before("execution(* com.example.app.service..*.*(..))")
public void before(JoinPoint jp) {
    try {
        telemetryRecordSafely(jp.getSignature().toShortString());
    } catch (RuntimeException telemetryFailure) {
        // Gửi lỗi tới fallback diagnostic path nếu có;
        // không che business flow do telemetry không thiết yếu.
    }
}
```

Đây là **mẫu ý tưởng chưa biên dịch độc lập**, vì cần định nghĩa `telemetryRecordSafely(...)` và đăng ký `@Aspect` bean. Nếu bắt lỗi để bảo vệ business call, phải có **fallback reporting** hoặc health-monitoring phù hợp; nuốt lỗi rồi không ai biết logging/metrics đang hỏng sẽ gây mù quan sát.

Không catch `Throwable` và nuốt cả `OutOfMemoryError`/`StackOverflowError` chỉ để giữ một request “thành công”; những lỗi nghiêm trọng thường không nên được che giấu.

### Trường hợp thiết yếu: authorization hoặc invariant check

Nếu aspect chịu trách nhiệm **từ chối truy cập**, ném exception có thể chính là hành vi chủ đích:

```java
@Before("execution(* com.example.app.service..*.sensitive(..))")
public void denyIfNotAllowed() {
    if (!accessAllowed()) {
        throw new SecurityException("access denied");
    }
}
```

Đây là snippet có `accessAllowed()` cần được cài đặt phù hợp. Khi không đủ quyền, ngăn target chạy là chính xác. Không catch exception này rồi return success chỉ để tránh lỗi xuất hiện ở client.

**Cần phân biệt:** exception được thiết kế làm **policy decision** và exception ngoài ý muốn do telemetry/database/logger bị lỗi. Hai trường hợp không nên áp cùng một recovery policy.

### Trường hợp cleanup thất bại

Một `@After` chịu trách nhiệm cleanup cần làm rõ:

- Cleanup bắt buộc để bảo đảm consistency hay chỉ giải phóng phụ trợ?
- Nếu target đã thất bại, có muốn giữ exception gốc làm **primary** và ghi cleanup failure ở **suppressed/cause**?
- Nếu target thành công nhưng cleanup fail, có được báo business success không?
- Cleanup có thể retry/idempotent không?
- Có nên thực hiện resource management tại code business/try-with-resources thay vì ở một `@After` không có trực tiếp exception gốc?

Đừng mặc định dùng `@After` cho những tài nguyên mà lifecycle cần sự đảm bảo transactional/ownership cụ thể bên trong business method.

## 12. Nếu cần giữ cả lỗi target và lỗi cleanup

Một outer `@Around` có thể chủ động giữ cả primary và secondary exception khi **nó có quyền quản lý cả hai**:

```java
@Around("execution(* com.example.app.service..*.operate(..))")
public Object carefullyCleanup(ProceedingJoinPoint pjp)
        throws Throwable {

    Throwable primary = null;

    try {
        return pjp.proceed();
    } catch (Throwable failure) {
        primary = failure;
        throw failure;
    } finally {
        try {
            releaseOwnedResource();
        } catch (Throwable cleanupFailure) {
            if (primary != null) {
                if (cleanupFailure != primary) {
                    primary.addSuppressed(cleanupFailure);
                }
            } else {
                throw cleanupFailure;
            }
        }
    }
}
```

Đây là **pseudocode Java hợp lệ trong một advice body**, cần triển khai `releaseOwnedResource()` và aspect bean; dùng như mẫu tư duy, không tự động copy vào mọi hệ thống. Nó có chủ đích:

- Nếu phần chain bên trong ném lỗi, giữ **primary** và đính kèm lỗi cleanup trong `getSuppressed()`, thay vì mất primary.
- Nếu phần chain bên trong thành công nhưng cleanup thất bại, propagate cleanup failure vì không có primary lỗi khác.
- Tránh `addSuppressed` nếu cùng object throwable.

**Giới hạn:** code bắt `Throwable` chủ yếu để giữ và propagate, không để nuốt `Error`. Nếu `releaseOwnedResource()` không do aspect sở hữu lifecycle, đừng cố cleanup trong aspect.

Một lựa chọn đơn giản hơn là Java `try-with-resources` khi tài nguyên thuộc về **chính method business**, bởi Java có suppressed-exception semantics chuẩn cho lỗi đóng tài nguyên.

## 13. Interaction với thứ tự Spring advice

### 13.1. Precedence cùng aspect

Tài liệu Spring Framework 6.2 cho thứ tự precedence **từ cao đến thấp** giữa các loại advice thuộc cùng một `@Aspect`:

```text
@Around > @Before > @After > @AfterReturning > @AfterThrowing
```

Do `@After` dùng after-finally semantics, trên chiều thoát bình thường nó thực tế chạy **sau** `@AfterReturning`; trên chiều thoát lỗi chạy **sau** `@AfterThrowing`, khi những advice này thuộc cùng aspect và thỏa điều kiện.

Một exception ném từ một advice có thể thay đổi những tầng **bên ngoài** nhìn thấy gì, nhưng không làm các interceptors **đã đi qua** tự chạy lại.

### 13.2. Precedence giữa các aspects

`@Order(1)` có precedence cao hơn `@Order(2)`. Aspect có precedence cao hơn **vào trước, ra sau** trong các lời gọi lồng nhau. Vì vậy một outer `@Around` có thể bắt lỗi phát sinh từ target **lẫn các advice nằm bên trong**.

Khi không khai báo ordering giữa các aspects khác nhau mà chúng cùng match, thứ tự tương đối không được đảm bảo; không được dựa vào alphabetical class name hoặc thứ tự khai báo trong source.

### 13.3. Advice cùng loại trong cùng aspect

Hai `@After` cùng match một method, nhưng khai báo `first()` và `second()` trong cùng `@Aspect`, không có ordering contract dựa trên vị trí source. Nếu thứ tự ảnh hưởng kết quả/lỗi, gộp logic vào một advice method hoặc tách thành các aspects với `@Order` rõ ràng.

### 13.4. Outer aspect quan sát được gì?

| Vị trí lỗi | Outer around đã enter và bao phần lỗi? | Có thể catch trong outer? |
| --- | --- | --- |
| Target method phía trong | Có | Có |
| `@Before` phía trong | Có | Có |
| `@AfterReturning` phía trong | Có | Có |
| `@AfterThrowing` phía trong | Có | Có |
| `@After` phía trong | Có | Có |
| Advice nằm **bên ngoài** outer around | Không | Không |
| Một object khác không qua Spring AOP proxy | Không | Không |
| Task bất đồng bộ trên thread khác sau khi proxy đã trả | Không trực tiếp qua synchronous `proceed()` | Không thể giả định |

Chính vì vậy, tên “outer” chỉ có ý nghĩa khi **thứ tự chain đã được xác minh**.

## 14. Spring transaction, cache, async và retry: nơi lỗi advice gây nhầm lẫn

### 14.1. Transaction advisor và lỗi ném sau target

Với `@Transactional`, transaction behavior do một interceptor riêng quản lý. Nếu custom `@AfterReturning` ném exception:

- Nếu custom advice ở **bên trong transaction interceptor**, transaction interceptor có thể quan sát exceptional completion và quyết định rollback theo rollback rules.
- Nếu custom advice ở **bên ngoài transaction interceptor**, transaction interceptor có thể đã xử lý commit/rollback trước khi custom advice ném lỗi. Caller vẫn nhận exception, nhưng dữ liệu có thể đã commit.

Vì vậy, **không suy từ client nhận exception rằng transaction chắc chắn rollback**. Phải kiểm tra transaction advisor order, `rollbackFor`/`noRollbackFor` và integration test với database thật nếu tác động dữ liệu quan trọng.

### 14.2. Cache interceptor và advice thất bại

Nếu `@Cacheable` phục vụ cache hit, target method có thể không chạy. Một outer custom advice vẫn có thể quan sát kết quả từ cache, còn advice ở phía trong cache interceptor có thể không được enter. Nếu after-returning advice ném lỗi khi cache hit, client có thể thấy failure mặc dù cache có dữ liệu tốt.

Đừng coi `@AfterReturning` nhất thiết là bằng chứng “target method vừa chạy”.

### 14.3. Async interceptor

Với `@Async` hoặc `CompletableFuture`, một exception trong async task không đồng nhất với exception đồng bộ được outer advice bắt tại lời gọi proxy. Cần kiểm tra:

- Advisor có ở trước hay sau async boundary?
- Proxy method trả `Future`/`CompletableFuture` hay `void`?
- Lỗi phát sinh ngay khi scheduling hay sau đó khi task chạy?
- Caller quan sát exception bằng `join()`/`get()` hay handler async riêng?

Một outer synchronous `@Around` không thể tự bắt mọi lỗi hoàn thành muộn sau khi `proceed()` đã trả future bình thường.

### 14.4. Retry

Nếu một retry interceptor thử target nhiều lần, lỗi từ advice bên trong có thể **kích hoạt retry** như lỗi nghiệp vụ tùy cấu hình. Ví dụ target đã ghi dữ liệu rồi after-returning advice ném `IllegalStateException`; retry có thể gọi target một lần nữa, dẫn đến **duplicate writes**.

Ngược lại, nếu advice ở bên ngoài retry interceptor thì lỗi do advice ném sau khi retry đã hoàn tất có thể không được retry interceptor nhìn thấy.

Đặt câu hỏi: “Mình muốn retry lỗi nghiệp vụ, lỗi transport hay cả lỗi telemetry?”. Không gộp mọi `RuntimeException` thành retryable một cách mù quáng.

## 15. Ghi log, metrics và tracing mà không làm mất lỗi gốc

Một Spring aspect dùng trong production nên đáp ứng:

1. **Chỉ can thiệp đúng Spring bean và method cần thiết.** Pointcut quá rộng có thể intercept logger hoặc hạ tầng metrics của chính nó, gây vòng lặp.
2. **Không log secrets/PII.** `JoinPoint.getArgs()` có thể chứa passwords, tokens, payload nhạy cảm; chọn metadata cần thiết và mask có kiểm soát.
3. **Không ném lỗi chỉ vì formatter/toString() thất bại** nếu tracing là best-effort.
4. **Xác định contract của advice:** policy-enforcing (được phép fail closed) hay observability-only (nên cô lập lỗi thứ cấp).
5. **Bảo toàn cause chain** nếu quyết định wrap lỗi nghiệp vụ.
6. **Đừng lặng lẽ nuốt mọi exception** khiến hệ thống mất tín hiệu cần thiết; có thể báo lỗi telemetry qua channel riêng và health checks.
7. **Không giữ request state bằng mutable fields trong singleton aspect** nếu không có đồng bộ/hướng dẫn thread-safe rõ ràng.
8. **Không tự động retry cả target vì metric bị lỗi.** Đánh giá idempotency và transaction boundary.
9. **Sử dụng `@Order` khi exception flow giữa các aspects là yêu cầu.**
10. **Viết integration tests gọi bean từ context** để xác minh caller thấy gì, không chỉ mock lời gọi method advice trực tiếp.

### Khi log chính nó có thể ném exception

Các lỗi không chỉ từ network client. Một advice có thể fail khi:

- Gọi `toString()` của domain object (implementation tùy ý, có thể throw).
- Serialize object cyclic/không hợp lệ.
- Truy cập lazy-loaded property ngoài transaction.
- Truy cập một logger appender bị lỗi I/O.
- Ghi metric với tag cardinality cao hoặc backend client throw.
- Cast `JoinPoint.getArgs()[i]` sai kiểu hoặc truy cập index không tồn tại.
- Dùng `getCause().getMessage()` mà không kiểm tra `null`.
- Dùng shared mutable collection trong singleton aspect không thread-safe.

Vì vậy, “code advice chỉ có một dòng logging” không đồng nghĩa “advice không thể gây lỗi”.

## 16. Checklist debug khi client nhận exception bất ngờ

| Quan sát | Chẩn đoán ưu tiên |
| --- | --- |
| Không có log `TARGET`, chỉ có exception | `@Before` hay outer `@Around` đã throw/short-circuit trước target? |
| Target log thành công nhưng client nhận 5xx | `@AfterReturning` / `@After` / outer `@Around` có ném lỗi sau target không? |
| Target ném lỗi A nhưng caller nhận B | Advice có wrap hoặc override? Có giữ `cause`/`suppressed` không? |
| `@AfterThrowing` không log lỗi B từ companion advice | Spring không xem nó là global callback cho lỗi do các after advice khác ném |
| Cleanup log xuất hiện rồi lỗi gốc biến mất | `@After` có ném exception mới từ finally không? |
| Lỗi bị đổi thành success/fallback | `@Around` nào catch và return? |
| Có proxy nhưng advice không chạy | Pointcut không match, method không intercept được, self-invocation, hoặc order short-circuit |
| Retry operation dù target đã ghi xong | Lỗi after advice bị retry interceptor hiểu là lỗi target? |
| Spring transaction commit nhưng client nhận exception | Advice ném lỗi ngoài transaction interceptor sau commit? |
| Exception chỉ xuất hiện với Spring Boot, không có trong test thuần | Proxy strategy/advisor auto-configuration/ordering khác? |
| Outer log không thấy lỗi | Outer observer có thật sự bao advice/target lỗi theo `@Order`? |
| Async client không thấy lỗi ngay | Lỗi xảy ra sau proxy return trong future/executor? |

### Dụng cụ nhận diện proxy

```java
Object bean = context.getBean("scenarioService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.getTargetClass(bean));
```

Đây là snippet, cần Spring context và import `org.springframework.aop.support.AopUtils`. Trong **demo đầy đủ**, bean đã có JDK dynamic proxy do interface và cấu hình cụ thể. Nhưng **có proxy** không chứng minh một advice nhất định match; cần kiểm tra cả pointcut và đường gọi.

## 17. Kiểm thử hành vi exception propagation

Khi viết test cho aspect, không chỉ assert target output. Test ít nhất:

- **Before error:** số lần target method thực thi bằng 0; caller thấy lỗi từ before advice.
- **After-returning error:** số lần target thực thi bằng 1 và trả nội bộ thành công; caller lại thấy lỗi advice.
- **After-throwing error:** target lỗi gốc, advice ném wrapper; caller nhận wrapper và `getCause()` đúng lỗi gốc.
- **After/finally trên normal return:** caller có thể thấy cleanup error.
- **After/finally trên target error:** kiểm tra có/không có cause gốc tùy chính sách của advice.
- **Around wrap:** caller nhận wrapper và exception gốc còn trong chain.
- **Healthy method:** không bị advice thất bại của method khác tác động.
- **Ordering:** observer bên ngoài nhận exception từ các advisors phía trong khi đã cấu hình đúng.
- **Không qua proxy:** lời gọi trên object `new` không kích hoạt Spring advice; self-invocation cần kiểm tra riêng.
- **Transaction/cache/retry:** test lại với các advisors thật nếu đó là ranh giới quan trọng.

Có thể dùng JUnit 5 với `assertThrows` và `assertSame` trên cause object, nhưng nên dùng một event recorder hoặc capture log để xác minh **thứ tự xảy ra**. Business return/exception đơn lẻ không chứng minh thứ tự toàn bộ advice.

Ví dụ ý tưởng:

```java
IllegalStateException seen = assertThrows(
    IllegalStateException.class,
    service::scenarioThrowing
);

assertEquals("throwing advice failed", seen.getMessage());
assertInstanceOf(IllegalArgumentException.class, seen.getCause());
assertEquals("business throwing", seen.getCause().getMessage());
```

Đây là **JUnit 5 snippet** cần dependencies/imports và Spring bean đã được lấy từ context, không phải file Java chạy độc lập. Test này xác minh rằng wrapper của after-throwing advice giữ lại exception gốc.

## 18. Câu hỏi tự kiểm tra

**1.** Target chưa chạy, `@Before` ném `SecurityException`. Caller có thể nhận gì?  
**Đáp án:** `SecurityException` từ before advice nếu không có outer handler; target không được chạy.

**2.** Target return `"OK"`, nhưng `@AfterReturning` ném `IllegalStateException`. Client chắc chắn thấy `"OK"` chứ?  
**Đáp án:** Không. Nếu lỗi advice không được catch, client nhận exception thay cho normal return.

**3.** Target ném `IllegalArgumentException`, `@AfterThrowing` ném `IllegalStateException` mới mà không có `cause`. Lỗi cũ có chắc còn trong `getCause()`?  
**Đáp án:** Không. Muốn preserve, dùng wrapper constructor có cause hoặc chính sách giữ suppressed ở tầng phù hợp.

**4.** `@After` ném lỗi trong đường `finally` khi target cũng đang ném lỗi. Exception nào có thể tới caller?  
**Đáp án:** Exception mới từ `@After` có thể che exception target.

**5.** `@AfterThrowing` cùng aspect có được đảm bảo bắt exception do companion `@AfterReturning` ném không?  
**Đáp án:** Không. Spring không định nghĩa nó là general exception callback cho companion after advice.

**6.** Một outer `@Around` có thể bắt lỗi `@Before` của aspect trong không?  
**Đáp án:** Có, nếu outer around đã enter, bao advice bên trong và `pjp.proceed()` ném lỗi đó.

**7.** Nếu outer `@Around` catch mọi `RuntimeException` và return `"FALLBACK"`, có thể vô tình nuốt lỗi authorization/advice không?  
**Đáp án:** Có. Catch cần hẹp và đúng contract.

**8.** Target đã ghi database thành công, client nhận lỗi từ metric advice, sau đó retry. Rủi ro gì?  
**Đáp án:** Duplicate side effects nếu operation không idempotent; transaction/retry ordering phải được kiểm tra.

**9.** Một aspect chứa hai `@After` cùng match. Source method nào viết trước thì chắc chạy trước?  
**Đáp án:** Không có ordering contract dựa trên vị trí khai báo Java source.

**10.** `@After` luôn chạy ngay cả khi `@Before` phía ngoài ném trước khi phần chain của after advice được enter?  
**Đáp án:** Không. Interceptor chưa được enter không thể chạy finally của nó.

**11.** Outer observer `@Order(1)` có thể quan sát lỗi do inner advice `@Order(2)` ném không?  
**Đáp án:** Có nếu cả hai match, outer around bao lời gọi phía trong và lỗi không bị xử lý trước khi ra tới observer.

**12.** Tại sao một lỗi log được wrap nên giữ `cause` nếu phải propagate?  
**Đáp án:** Để không mất thông tin lỗi gốc/stacktrace khi caller chỉ nhận wrapper exception.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around`, exception semantics, `proceed()` và *Advice Ordering*. Chú ý ghi chú về giới hạn `@AfterThrowing` với lỗi do companion after advice ném.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): proxy boundary, JDK/CGLIB và self-invocation.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): aspect registration và Spring-managed auto-proxying.
- [Spring Framework 6.2 — Declarative Transactions](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative.html): transaction advice và quan hệ exception/transaction.
- [Spring Framework 6.2 — Understanding the Spring Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction.html): nguyên tắc transaction và rollback tùy interceptor/configuration.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-005">Phân biệt ngoại lệ từ phương thức đích và từ advice khác</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngoại lệ từ phương thức đích và từ advice khác trong Spring AOP

## 1. Mục tiêu và tình huống

Trong Spring Framework AOP, một lần gọi service qua proxy có thể thất bại vì **target method** hoặc vì **một advisor/advice đứng trước, sau hay bao quanh target**. Ví dụ:

- `@Before` kiểm tra quyền và ném exception **trước khi target chạy**.
- Target method tự ném exception trong quá trình xử lý nghiệp vụ.
- Target **trả kết quả bình thường**, nhưng `@AfterReturning` tự ném exception khi xử lý kết quả.
- `@Around` bắt exception từ continuation rồi **đổi thành một exception khác**, hoặc nuốt lỗi và trả fallback.
- `@After` chạy cleanup rồi ném lỗi mới, đôi khi che đi exception gốc.
- `TransactionInterceptor` hoặc exception-translation interceptor có thể tạo/chuyển đổi exception **bên ngoài thân method target**.

Vì vậy, **exception cuối mà caller nhận từ Spring proxy không chứng minh target body đã ném exception ấy**. Điều này ảnh hưởng logging, audit, metrics, retry và chẩn đoán lỗi.

Sau bài này, bạn sẽ biết:

1. Phân biệt **throw site**, **observation site**, **exception cuối** và **target execution outcome**.
2. Hiểu `@AfterThrowing` được Spring thiết kế cho method execution ném lỗi, **không phải một global catch cho mọi advice khác**.
3. Biết `@Around` bắt được exception từ **continuation phía trong**, có thể gồm cả inner advice.
4. Dùng `@Order` để kiểm soát phạm vi mà từng advice quan sát được.
5. Thử bốn kịch bản với Spring Framework 6.2.x, Java 17+, Maven: bình thường; target throw; before advice throw; after-returning advice throw.
6. Thiết kế instrumentation để không ghi nhầm “target failed” khi thực tế một advice bị lỗi.
7. Nhận diện giới hạn proxy, checked exception wrapping, transaction commit và secondary exception.

**Giới hạn phạm vi:** Chỉ tập trung vào **Spring-managed `@Aspect` và Spring proxy-based AOP**, không đi sang AspectJ weaving hoặc các khái niệm AOP tổng quát không cần cho tình huống Spring.

## 2. Đặt từng nguồn lỗi vào đúng vị trí của interceptor chain

Demo ở mục 6 sử dụng **bốn aspect tách biệt**, có `@Order` rõ ràng:

~~~text
Client → Spring proxy
       → OuterTraceAspect @Around (0)
       → BeforeGateAspect @Before (10)
       → AfterResultAspect @AfterReturning (20)
       → TargetFailureAspect @AfterThrowing (30)
       → JobService.execute(...) (target)
~~~

Các aspect nằm ở tầng ngoài có thể nhìn thấy lỗi xuất hiện từ tầng trong khi gọi `proceed()`. Nhưng nếu `BeforeGateAspect` ném lỗi, target và các advisor nằm sâu hơn chưa chạy; nếu `AfterResultAspect` ném sau khi target return, `TargetFailureAspect` nằm phía trong đã kết thúc bình thường. Đây là **thứ tự có chủ đích của demo**, không phải thứ tự mặc định của mọi Spring application.

| Scenario | Nơi đầu tiên phát sinh lỗi | Target đã chạy? | Observer trong cùng demo thấy gì? |
| --- | --- | --- | --- |
| `BEFORE` | `@Before` ngoài | Không | Outer `@Around` catch; inner `@AfterThrowing` không được enter |
| `TARGET` | `JobService.execute()` | Có, nhưng throw | Inner `@AfterThrowing` log; outer `@Around` catch |
| `AFTER` | `@AfterReturning` ngoài | Có, đã return | Outer `@Around` catch; inner `@AfterThrowing` đã hoàn tất |
| `OK` | Không có lỗi | Có, đã return | Outer `@Around` trả kết quả bình thường |

## 3. Truy nguyên lỗi: phân biệt nguồn ném, nơi quan sát và kết quả caller

| Tín hiệu | Câu hỏi cần xác minh |
| --- | --- |
| **Throw site** | Java code nào thực sự tạo và ném Throwable gốc? |
| **Target outcome** | Thân method đã chạy, và nó return hay throw? |
| **Observation site** | Advisor nào nhìn thấy lỗi khi Throwable đi qua tầng đó? |
| **Final exception** | Throwable nào cuối cùng ra khỏi Spring proxy tới caller? |

Một target có thể throw `IllegalArgumentException` nhưng inner `@Around` chuyển thành `ServiceException` và giữ lỗi cũ ở `cause`. Khi đó observer ở ngoài và caller nhìn thấy `ServiceException`; điều này **không chứng minh** target đã ném loại lỗi mới. Dùng `getCause()`, suppressed exceptions và structured trace/correlation ID để truy nguyên. `JoinPoint.getSignature()` chỉ định danh join point đang được advice, **không cung cấp throw site**.

## 4. Ranh giới của `@AfterThrowing`

~~~java
@AfterThrowing(
    pointcut = "execution(* demo.JobApi.execute(..))",
    throwing = "ex"
)
public void onFailure(RuntimeException ex) {
    System.out.println("AFTER_THROWING " + ex.getClass().getSimpleName());
}
~~~

Đây là fragment trong một Spring `@Aspect`. `throwing = "ex"` phải khớp tên tham số advice; kiểu `RuntimeException` chỉ bind lỗi thuộc kiểu ấy (có thể dùng `Throwable` nếu cần phạm vi rộng hơn). Advice ghi log rồi trả `void` **không tự nuốt lỗi**.

Theo Spring Framework, `@AfterThrowing` phục vụ exception từ method execution được advice, **không phải callback tổng quát cho ngoại lệ phát sinh từ companion `@After`/`@AfterReturning`**. Nó quan sát Throwable nào còn phụ thuộc vị trí advisor, typed binding và việc continuation có đi tới advisor đó hay không. Nếu chính observer ném thêm lỗi, caller có thể thấy lỗi mới và mất nguyên nhân ban đầu.

## 5. `@Around` quan sát continuation, không suy ra nguồn lỗi

~~~java
@Around("execution(* demo.JobApi.execute(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (RuntimeException ex) {
        System.out.println("OUTER caught " + ex.getClass().getName());
        throw ex;
    }
}
~~~

Khi đây là advisor ngoài cùng, `catch` có thể nhận lỗi từ inner `@Before`, target, inner `@AfterReturning`, `@After`, transaction commit hoặc interceptor khác. Log `OUTER caught` chỉ chứng minh **continuation bên trong thất bại**, chưa chứng minh thân target thất bại. Nếu chính outer `@Around` ném trước `proceed()`, phía trong có thể chưa hề chạy.

## 6. Demo hoàn chỉnh Spring Framework: ba nguồn lỗi rõ ràng

Môi trường:

- **Java 17+**
- **Maven 3.9+**
- **Spring Framework 6.2.12**
- **AspectJ Weaver 1.9.24**

Mỗi concern đặt ở một Spring `@Aspect` class riêng với `@Order` khác nhau, tránh phụ thuộc thứ tự không được bảo đảm giữa hai advice cùng loại trong một aspect. Dự án dùng **Spring Framework thuần**, không cần Spring Boot/DB/server.

### 6.1. Cấu trúc

~~~text
spring-aop-exception-origin/
├── pom.xml
└── src/main/java/demo/
    ├── JobApi.java
    ├── Failures.java
    ├── JobService.java
    ├── OuterTraceAspect.java
    ├── BeforeGateAspect.java
    ├── AfterResultAspect.java
    ├── TargetFailureAspect.java
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
    <artifactId>spring-aop-exception-origin</artifactId>
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

`spring-context` kéo các module Spring AOP cần thiết; `aspectjweaver` hỗ trợ annotations/pointcut expression cho Spring `@AspectJ` style. Không dùng AspectJ compiler/weaving ở đây.

### 6.3. JobApi.java

~~~java
package demo;

public interface JobApi {
    String execute(String scenario);
}
~~~

Interface bảo đảm caller có thể lấy bean theo kiểu `JobApi`. Spring AOP có thể dùng JDK dynamic proxy với implementation có interface.

### 6.4. Failures.java

~~~java
package demo;

public final class Failures {

    private Failures() {
    }

    public static final class TargetFailure
            extends RuntimeException {
    }

    public static final class BeforeFailure
            extends RuntimeException {
    }

    public static final class AfterFailure
            extends RuntimeException {
    }
}
~~~

Ba custom exceptions riêng giúp demo nhận diện **nguồn throw theo thiết kế**. Trong production, type name không tự chứng minh nguồn throw: bất kỳ component nào cũng có thể ném cùng type, hoặc exception có thể bị translate.

### 6.5. JobService.java

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class JobService implements JobApi {

    @Override
    public String execute(String scenario) {
        System.out.println("TARGET enter " + scenario);

        if ("TARGET".equals(scenario)) {
            throw new Failures.TargetFailure();
        }

        return "done:" + scenario;
    }
}
~~~

Target chỉ ném ở kịch bản `TARGET`. Với `AFTER`, nó **đã chạy và trả bình thường**, trước khi một advice khác có thể gây exception.

### 6.6. OuterTraceAspect.java

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class OuterTraceAspect {

    @Around("execution(* demo.JobApi.execute(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        String scenario = (String) pjp.getArgs()[0];

        System.out.println("OUTER enter " + scenario);

        try {
            Object result = pjp.proceed();
            System.out.println("OUTER success " + scenario);
            return result;
        } catch (RuntimeException ex) {
            System.out.println(
                    "OUTER caught " + ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
~~~

Vì `@Order(0)` có precedence cao hơn các aspects còn lại trong demo, outer around nằm ngoài và quan sát cả **before**, **target**, **after-returning** ở continuation.

`pjp.proceed()` được gọi đúng một lần; advice không short-circuit. `catch` ở đây cố ý chỉ nhận RuntimeException của ba kịch bản, không phải chính sách bắt `Error`/checked exception tổng quát.

### 6.7. BeforeGateAspect.java

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
public class BeforeGateAspect {

    @Before("execution(* demo.JobApi.execute(..))")
    public void before(JoinPoint jp) {
        String scenario = (String) jp.getArgs()[0];
        System.out.println("BEFORE enter " + scenario);

        if ("BEFORE".equals(scenario)) {
            throw new Failures.BeforeFailure();
        }
    }
}
~~~

Với input `BEFORE`, advice ném **trước khi target hoặc các inner advisors được đi tới**. Caller vẫn thấy exception khi gọi JobApi, nhưng target không ném lỗi vì target chưa chạy.

### 6.8. AfterResultAspect.java

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(20)
public class AfterResultAspect {

    @AfterReturning(
        pointcut = "execution(* demo.JobApi.execute(..))",
        returning = "result"
    )
    public void after(JoinPoint jp, Object result) {
        String scenario = (String) jp.getArgs()[0];
        System.out.println("AFTER_RETURNING " + scenario);

        if ("AFTER".equals(scenario)) {
            throw new Failures.AfterFailure();
        }
    }
}
~~~

Với `AFTER`, target trả về `done:AFTER` rồi advice ném `AfterFailure`. Vì thế **target thành công nhưng proxy invocation thất bại**. `returning="result"` bind kết quả trả về vào tham số cùng tên; `Object` cho phép nhận String trong project này.

### 6.9. TargetFailureAspect.java

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(30)
public class TargetFailureAspect {

    @AfterThrowing(
        pointcut = "execution(* demo.JobApi.execute(..))",
        throwing = "ex"
    )
    public void afterThrow(JoinPoint jp, RuntimeException ex) {
        System.out.println(
                "AFTER_THROWING " + ex.getClass().getSimpleName());
    }
}
~~~

Aspect này được đặt **phía trong** before và after-returning aspects của demo:

- `TARGET`: target ném, exception đi qua `@AfterThrowing`, log xuất hiện.
- `BEFORE`: lời gọi bị chặn trước khi đến advisor này, nên không có log.
- `AFTER`: advisor này đã hoàn tất bình thường khi target trả về; after-returning advice đứng ngoài nó mới ném exception, nên không có log.
- `OK`: target trả bình thường, không có log.

Đây là **kết quả của thứ tự được thiết kế và kiểm chứng trong demo**, không phải tuyên bố mọi `@AfterThrowing` đều tự biết nguồn throw trong mọi Spring context.

### 6.10. AppConfig.java

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

`@Aspect` không tự đăng ký object thành bean. Mỗi aspect trong demo có thêm `@Component` và `@ComponentScan` bao phủ package. `@EnableAspectJAutoProxy` bật hạ tầng tạo proxy.

### 6.11. App.java

~~~java
package demo;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            JobApi jobs = context.getBean(JobApi.class);
            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(jobs));

            for (String scenario :
                    List.of("OK", "TARGET", "BEFORE", "AFTER")) {
                System.out.println("=== " + scenario + " ===");

                try {
                    String result = jobs.execute(scenario);
                    System.out.println("CALLER result " + result);
                } catch (RuntimeException ex) {
                    System.out.println(
                            "CALLER caught " +
                            ex.getClass().getSimpleName());
                }
            }
        }
    }
}
~~~

Chương trình lấy **Spring-managed proxy**; không tạo JobService bằng `new`. Mỗi exception được bắt ở caller để các scenario tiếp theo vẫn chạy.

Chạy trong thư mục có `pom.xml`:

~~~bash
mvn -q clean compile exec:java
~~~

### 6.12. Stdout logic kỳ vọng

~~~text
IS_PROXY true
=== OK ===
OUTER enter OK
BEFORE enter OK
TARGET enter OK
AFTER_RETURNING OK
OUTER success OK
CALLER result done:OK
=== TARGET ===
OUTER enter TARGET
BEFORE enter TARGET
TARGET enter TARGET
AFTER_THROWING TargetFailure
OUTER caught TargetFailure
CALLER caught TargetFailure
=== BEFORE ===
OUTER enter BEFORE
BEFORE enter BEFORE
OUTER caught BeforeFailure
CALLER caught BeforeFailure
=== AFTER ===
OUTER enter AFTER
BEFORE enter AFTER
TARGET enter AFTER
AFTER_RETURNING AFTER
OUTER caught AfterFailure
CALLER caught AfterFailure
~~~

Đây là **stdout logic dự kiến**, không tính logging riêng của Maven/Spring. Các aspects có precedence khác nhau để output không dựa trên thứ tự không xác định của nhiều advice cùng loại ở cùng class.

### 6.13. Bảng suy luận kết quả

| Kịch bản | `@Before` | Target chạy | Target throw | `@AfterReturning` | `@AfterThrowing` bên trong log | Caller |
| --- | --- | --- | --- | --- | --- | --- |
| **OK** | chạy | có | không | chạy | không | `done:OK` |
| **TARGET** | chạy | có | có | không chạy | **TargetFailure** | `TargetFailure` |
| **BEFORE** | chạy rồi throw | **không** | không | không chạy | không | `BeforeFailure` |
| **AFTER** | chạy | có và return | không | chạy rồi throw | không | `AfterFailure` |

Cả ba nhánh lỗi đều có `OUTER caught ...`. Như vậy **outer @Around quan sát lỗi từ nhiều thành phần**, còn inner `@AfterThrowing` chỉ quan sát lỗi target trong cấu hình có kiểm soát này.

## 7. Ghi log/metrics chính xác theo phase

Không nên dùng một counter `target_exception_count` để tăng mỗi khi generic outer `@Around` catch exception. Một phương án giàu thông tin hơn:

| Trường metadata | Ý nghĩa |
| --- | --- |
| `proxy.outcome` | Caller nhận result hay exception? |
| `target.reached` | Có bằng chứng đã đi vào target body chưa? |
| `target.outcome` | Body target return hay throw? |
| `advice.phase` | before, after-returning, after-throwing, around, transaction... |
| `exception.type` | Class exception quan sát được |
| `exception.cause` | Cause chain nếu có wrapper |
| `operation.id` | Correlation ID của logical invocation |
| `advisor.order` | Vị trí/precedence có liên quan |

`pjp.getTarget()` cho biết target object, **không chứng minh target method đã chạy**. `MethodSignature.getMethod()` nói tên/chữ ký method, **không cho biết nơi phát sinh exception**. Nếu cần chắc chắn target đã được thực thi, dùng instrumentation **ở bên trong target hoặc ranh giới target**, hoặc integration-test với spy/counter.

## 8. @Around đổi, nuốt hoặc giữ exception

### 8.1. Chuyển đổi và giữ cause

~~~java
@Around("execution(* demo.JobApi.execute(..))")
public Object translate(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Failures.TargetFailure ex) {
        throw new IllegalStateException("Service failed", ex);
    }
}
~~~

Fragment này minh họa cách một aspect **tạo exception mới** nhưng giữ original dưới `getCause()`. Outer observer sẽ trực tiếp thấy `IllegalStateException`, không phải `TargetFailure`.

### 8.2. Nuốt exception và trả fallback

~~~java
@Around("execution(* demo.JobApi.execute(..))")
public Object fallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Failures.TargetFailure ex) {
        return "fallback";
    }
}
~~~

Nếu fallback được phép theo contract, caller có thể thấy **proxy success** dù target đã ném lỗi. Đừng ghi `target.success=true` chỉ dựa vào kết quả của outer `@Around`.

### 8.3. Lỗi mới trong finally

~~~java
try {
    return pjp.proceed();
} finally {
    auditWriter.write();
}
~~~

Đây là fragment. Nếu `pjp.proceed()` ném lỗi X nhưng `auditWriter.write()` cũng ném lỗi Y từ `finally`, Y có thể **che X**. Nếu audit là best-effort, có thể bắt riêng lỗi audit rồi giữ exception gốc; nếu audit là bắt buộc, cần quy tắc ưu tiên lỗi rõ ràng.

**Không chép ba fragment này cùng lúc vào demo mục 6** nếu muốn giữ expected stdout: chúng là các phương án thay thế dành cho thực hành.

## 9. @AfterThrowing tự ném cũng có thể che lỗi gốc

Ví dụ:

~~~java
@AfterThrowing(
    pointcut = "execution(* demo.JobApi.execute(..))",
    throwing = "original"
)
public void audit(RuntimeException original) {
    auditWriter.write(original);
}
~~~

Nếu target ném `TargetFailure`, nhưng `auditWriter.write` ném `AuditStorageFailure`, caller có thể nhận `AuditStorageFailure` thay lỗi ban đầu. `@AfterThrowing` không phải một observer vô hại nếu code bên trong có thể ném.

Với một chính sách **giữ nguyên lỗi nghiệp vụ**, có thể dùng fragment:

~~~java
try {
    return pjp.proceed();
} catch (RuntimeException original) {
    try {
        auditWriter.write(original);
    } catch (RuntimeException auditError) {
        original.addSuppressed(auditError);
    }
    throw original;
}
~~~

Mẫu này giữ original và đính lỗi audit vào suppressed list. Nhưng không phù hợp mọi hệ thống, đặc biệt nơi audit failure phải làm thay đổi business outcome; cần xác định policy rõ ràng.

Một Spring bean recorder được aspect gọi cũng phải **nằm ngoài pointcut của chính aspect ấy** để tránh advice lặp lại qua collaborator.

## 10. Transaction và exception translation: lỗi sau target

Trong Spring, ngoài custom `@Aspect` còn có những interceptors như:

- `TransactionInterceptor` cho các `@Transactional` phù hợp.
- Method security advisors.
- Spring persistence exception translation cho các repository đã được cấu hình.
- Cache/retry advisors do ứng dụng hoặc thư viện cung cấp.

Chẳng hạn:

~~~text
Client -> OuterTraceAspect -> TransactionInterceptor
                                   |
                                   +-- begin transaction
                                   +-- Target method RETURN
                                   +-- COMMIT throws exception
                    |
                    +-- OuterTraceAspect catches commit error
~~~

Target body **có thể đã return bình thường**, nhưng proxy trả lỗi vì commit thất bại. Vì vậy cần phân biệt:

- `target.body.success`
- `transaction.commit.success`
- `proxy.invocation.success`

Chúng không luôn giống nhau. Nếu `@AfterReturning` nằm **trong** transaction advisor, callback có thể chạy trước khi commit; nếu nó đứng **ngoài**, có thể quan sát một kết quả đã qua transaction. Thứ tự này cần được kiểm tra trên context thực.

Spring exception translation cũng có thể khiến caller thấy `DataAccessException` thay vì exception low-level đã phát sinh trong repository, tùy translator và configuration. Đừng khẳng định mọi `@Repository` tự dịch mọi exception nếu chưa có advisor/translator phù hợp.

## 11. Java checked exception và JDK proxy

Interface:

~~~java
public interface FileApi {
    String read(String path) throws java.io.IOException;
}
~~~

Đây là fragment interface, không phải file của demo mục 6. Nếu target ném `IOException` được khai báo, caller có thể catch type ấy bình thường.

Nếu một advice ném **checked exception không thuộc method throws contract**, JDK dynamic proxy có thể wrap vào `UndeclaredThrowableException`. Lúc đó exception cuối khác exception do advice ném, và caller nên kiểm tra cause có kiểm soát.

Hướng thực hành:

- Ưu tiên exception types tương thích Java interface contract.
- Không dùng `catch(Throwable)` rồi retry mọi thứ.
- Không giả định một class exception ở caller cho biết nơi lỗi bắt đầu.
- Khi log, giữ cause/suppressed data nếu domain cho phép.
- Test cả JDK và CGLIB proxies nếu application phụ thuộc vào proxy type.

Demo hoàn chỉnh chỉ dùng **unchecked exceptions** để dễ quan sát mà không làm lẫn checked-exception mechanics.

## 12. Short-circuit và self-invocation

Một outer `@Around` có thể chủ động ném lỗi hoặc trả fallback **không gọi `proceed()`**:

~~~java
if (maintenanceMode) {
    throw new IllegalStateException("Rejected by outer advice");
}
return pjp.proceed();
~~~

Đây là fragment. Trong nhánh bảo trì, các advisors phía trong và target không được đi tới. Một `@AfterThrowing` nằm phía trong sẽ **không quan sát được lỗi mà outer advice tự ném trước proceed**.

Ngược lại, self-invocation:

~~~java
public void outer() {
    this.inner();
}
~~~

có thể **bỏ qua Spring proxy**, nên advice cho inner không tự chạy, dù body inner vẫn thực thi. Đây không phải `@Around` short-circuit; ở đây AOP join point cho inner **không được proxy intercept ngay từ đầu**.

Hai tình huống phải phân biệt:

1. **Không vào proxy**: advice không chạy.
2. **Vào proxy rồi ném trước target**: một số advice đã chạy, target chưa chạy.

## 13. Advice ordering là một phần của kết luận

Trong Spring `@AspectJ` style:

- `@Order` hoặc `Ordered` giúp cấu hình precedence giữa các aspects.
- Số nhỏ hơn thường biểu thị precedence cao hơn.
- Trên đường vào, outer aspect chạy trước.
- Trên đường ra, inner aspects hoàn tất trước outer aspects khi continuation được gọi bình thường.
- Hai advice **cùng loại trong cùng một class `@Aspect`** và cùng match không có thứ tự source được bảo đảm.
- `@After` mang finally semantics; nó không có nghĩa “luôn chạy trước `@AfterReturning`” chỉ vì thứ tự precedence khác loại.

Một observer ở phía ngoài có thể nhìn thấy nhiều loại lỗi hơn một observer phía trong. Thay đổi `@Order` có thể thay đổi exception mà observer thấy, vì vậy test cần kết hợp **source, type, order và invocation path**.

## 14. Checklist debug khi proxy invocation thất bại

1. Xác nhận caller có gọi **Spring-managed proxy** không; `AopUtils.isAopProxy(bean)` là kiểm tra ban đầu hữu ích.
2. Liệt kê **mọi advisor** đang áp dụng: custom aspects, transaction, security, cache và translation.
3. Xác định precedence thực, không suy ra từ thứ tự method trong source.
4. Ghi nhận **target entry** và **target exit** nếu cần khẳng định target body có chạy.
5. Đặt một outer observer cho **proxy outcome**, đồng thời ghi stage của các advice nghi ngờ.
6. Thử riêng target throw, before throw, after-returning throw.
7. Xem `getCause()` / suppressed exceptions nếu có wrapping.
8. Kiểm tra transaction commit/rollback và external side effects.
9. Không thay exception gốc bằng lỗi ghi metrics/audit ngoài ý muốn.
10. Phân loại logs: target failure, advice failure, transaction failure và proxy outcome.

## 15. Các lỗi phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Log “target error” khi before advice chặn quyền | Gán mọi proxy exception cho target | Theo dõi target-entry và phase |
| Target in “return” nhưng caller nhận exception | AfterReturning/transaction interceptor ném sau target | Kiểm tra advisors đường ra |
| `@AfterThrowing` không log dù caller catch lỗi | Exception phát sinh bên ngoài phạm vi advisor hoặc bị type-filter | Kiểm tra `@Order` và kiểu bind |
| `@AfterThrowing` không bắt lỗi từ một `@AfterReturning` khác | Không phải generic catch cho companion advice | Dùng outer `@Around` nếu cần quan sát continuation |
| Outer `@Around` bắt TargetFailure mà caller thấy ServiceException | Inner advice dịch lỗi | Giữ cause và log exception chain |
| Caller nhận success dù target lỗi | Inner around trả fallback | Tách proxy outcome khỏi target outcome |
| Lỗi audit che business failure | Audit advice ném exception thứ cấp | Chính sách priority, cause/suppressed |
| Exception type khác khi JDK proxy được dùng | Undeclared checked exception wrapping | Sửa contract và kiểm tra cause |
| Advisor chạy sai thứ tự sau refactor | `@Order` không rõ hoặc trùng | Cấu hình precedence và integration test |
| Không thấy advice trong self-call | `this.method()` bypass proxy | Refactor sang Spring bean dependency |
| Logging aspect lặp khi gọi recorder | Recorder cũng match pointcut | Loại technical collaborator |

## 16. Các bài test cần có

Với demo mục 6:

- **OK:** có dòng `TARGET enter OK`, `AFTER_RETURNING OK` và caller nhận `done:OK`.
- **TARGET:** có target entry, `AFTER_THROWING TargetFailure` và caller catch `TargetFailure`.
- **BEFORE:** có `BEFORE enter BEFORE` nhưng **không** có `TARGET enter BEFORE`.
- **AFTER:** có `TARGET enter AFTER`, `AFTER_RETURNING AFTER` nhưng caller catch `AfterFailure`.
- Outer around ghi nhận cả ba exceptions.
- `TargetFailureAspect` không log `BeforeFailure` hoặc `AfterFailure` trong ordering của demo.
- Nếu đổi order hoặc thêm advisors, expected events cần được đánh giá lại theo chain mới.

Một kiểm tra bằng code fragment:

~~~java
try {
    jobs.execute("BEFORE");
    throw new AssertionError("Should fail in before advice");
} catch (Failures.BeforeFailure expected) {
    // Use a spy/counter to assert that target was not invoked.
}
~~~

Đây là test fragment, chưa phải class độc lập. Đừng chỉ assert type của exception rồi suy rằng target chắc chắn không chạy; instrument target bằng counter/spy trong integration test để có bằng chứng.

## 17. Câu hỏi tự đánh giá

**1. Outer `@Around` catch một RuntimeException có chứng minh target ném không?**

Không. Inner before/after-returning/transaction advisor cũng có thể ném.

**2. `@AfterThrowing` có phải generic catcher cho mọi advice không?**

Không. Spring mô tả nó cho exception của method execution và lưu ý nó không phải callback tổng quát cho lỗi từ companion after/after-returning advice.

**3. Nếu `@Before` throw trước target, target có chạy không?**

Không qua invocation đang bị chặn đó.

**4. Target return bình thường nhưng `@AfterReturning` throw thì proxy trả gì?**

Proxy có thể trả exception của advice, dù target không ném exception.

**5. `@AfterThrowing` return void có nuốt lỗi target không?**

Không. Lỗi tiếp tục lan truyền nếu không được outer layer xử lý.

**6. Khi muốn catch, wrap, retry hoặc fallback, dùng advice nào?**

`@Around`, với `proceed()` và error-handling policy rõ ràng.

**7. Tại sao TargetFailureAspect trong ví dụ chỉ thấy lỗi target?**

Vì `@Order(30)` đặt nó bên trong before/after-returning aspects: lỗi BEFORE xảy ra trước khi đến nó, lỗi AFTER xảy ra sau khi nó hoàn tất bình thường.

**8. Transaction commit lỗi sau khi target return có phải target-body exception?**

Không. Đó là exception ở transaction interceptor/commit boundary.

**9. Vì sao exception cause quan trọng?**

Advice có thể wrap lỗi; cause giữ thông tin về failure ban đầu.

**10. Vì sao không dùng tên exception để xác định nguồn mọi lúc?**

Cùng type có thể được nhiều layer ném, hoặc exception có thể bị dịch/wrap.

## 18. Tài liệu Spring Framework chính thức

- **Spring Framework — Declaring Advice, After Throwing và Ordering:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html
- **Spring Framework — AOP Concepts:** https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- **Spring Framework — Advice API:** https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- **Spring Framework — Proxying Mechanisms:** https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Framework — @AspectJ Support:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- **Spring Framework — Declarative Transaction Management:** https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html

**Kết luận:** Một exception đi ra khỏi **Spring AOP proxy** không nhất thiết đến từ **method đích**. `@Before` có thể chặn trước target; `@AfterReturning` có thể thất bại sau target; `@Around` có thể quan sát, dịch, nuốt hoặc thay exception; transaction và các advisors khác cũng tham gia. Muốn phân loại đúng, hãy **đặt observer ở boundary thích hợp, kiểm soát `@Order`, ghi dấu target execution và bảo toàn exception cause** thay vì suy luận từ một catch hoặc một dòng log.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-006">Đóng tài nguyên và dọn ngữ cảnh trong mọi nhánh kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng tài nguyên và dọn ngữ cảnh trong mọi nhánh kết thúc của Spring AOP

## 1. Mục tiêu: không để tài nguyên hoặc ngữ cảnh rò rỉ sau method execution

Khi một Spring service được gọi qua AOP proxy, custom `@Aspect` có thể tạo **tài nguyên tạm** hoặc **ngữ cảnh theo lời gọi** để tracing, audit, logging, đo thời gian, kiểm soát resource handle hay tích hợp một client bên ngoài. Những đối tượng này phải được dọn dẹp cả khi target **trả về thành công**, **trả `null`**, **hoàn tất `void`**, **ném exception**, hoặc khi interceptor bên trong trả sớm.

Ví dụ:

- Aspect đặt mã tương quan vào `ThreadLocal` trước khi gọi `proceed()`. Nếu quên xóa/khôi phục, thread pool có thể mang mã của request trước vào lần xử lý sau.
- Aspect tự mở `AutoCloseable` lease để theo dõi một call. Nếu target ném exception, chỉ đóng trong nhánh success sẽ làm lease bị rò rỉ.
- Aspect tạo MDC logging fields tạm thời. Nếu scope lồng nhau, gọi `clear()` vô điều kiện có thể xóa luôn ngữ cảnh hợp lệ của caller.
- Aspect ghi nhận “cleanup complete” nhưng callback cleanup tự ném exception, khiến exception gốc bị che khuất.
- Aspect tưởng method trả `CompletableFuture` bình thường nghĩa là công việc asynchronous đã kết thúc và đóng tài nguyên cần dùng về sau quá sớm.

**Mục tiêu thực hành:** thiết kế cleanup **theo vòng đời của từng invocation Spring AOP**: tài nguyên nào aspect thật sự sở hữu thì aspect đóng; context tạm do aspect đặt thì aspect khôi phục đúng trạng thái trước đó; exception/result của target được giữ nguyên nếu cleanup chỉ nhằm quan sát. Đối với transaction/security/HTTP request context do Spring infrastructure sở hữu, không tự tiện đóng hoặc xóa.

**Phạm vi:** Spring Framework, Spring-managed `@Aspect`, `@After` (after-finally), `@Around` với Java `try/finally` và `try-with-resources`, proxy boundary, nested invocation, transaction/async/reactive nuances và test. Đây không phải hướng dẫn AspectJ compile-time/load-time weaving.

## 2. Điều Spring bảo đảm và điều Spring không thể bảo đảm

Spring Framework mô tả `@After` là **after (finally) advice**: nó được gọi khi một **matched method execution** thoát ra, bất kể đường trả về bình thường hay bằng exception. `@AfterReturning` chỉ dành cho normal return, `@AfterThrowing` dành cho exceptional exit. Spring cũng hỗ trợ `@Around` với `ProceedingJoinPoint.proceed()`, nhờ đó chính advice có thể dùng `finally` bảo vệ phần scope mà nó tạo.

Nhưng cụm “mọi nhánh kết thúc” phải hiểu **đúng ranh giới**:

1. **Đã đi vào advice hay chưa?** `@After` không chạy nếu method không qua Spring proxy, pointcut không match hoặc một outer interceptor short-circuit **trước khi** tới advisor của nó.
2. **Một lần method execution hay hoàn tất tác vụ bất đồng bộ?** Khi method trả `Future`/`Mono`/`Flux` bình thường, một Spring proxy advice đồng bộ có thể kết thúc trước khi dữ liệu/task bên trong hoàn thành.
3. **Target đã return hay transaction đã commit?** `@After` quanh target execution không mặc nhiên là callback “after DB commit”; vị trí transaction advisor và transaction propagation quyết định thời điểm.
4. **Cleanup có hoàn toàn không thể lỗi?** `finally` cố chạy cleanup trong Java khi thread thực thi bình thường qua control flow, nhưng không phải cam kết khi process bị kill, JVM dừng bất thường hoặc xảy ra lỗi hệ thống ngoài khả năng kiểm soát. Bản thân cleanup cũng có thể ném exception.
5. **Ai sở hữu tài nguyên?** Tài nguyên được `PlatformTransactionManager` hoặc Servlet/Spring Security filter quản lý không thuộc quyền custom aspect tự `close()`/`clear()`.
6. **Nhiều Spring aspects cùng tham gia?** Advice precedence có thể ảnh hưởng phạm vi cleanup: aspect ngoài có thể bọc nhiều interceptors hơn aspect trong.

Bởi vậy, không viết “`@After` luôn chạy cho mọi method gọi trong ứng dụng”. Câu đúng là: **khi một lời gọi method được Spring proxy intercept và đã đi vào `@After` advice phù hợp, Spring áp dụng after-finally khi join point thoát**, với các hạn chế proxy/invocation lifecycle ở trên.

## 3. Bảng chọn cơ chế cleanup phù hợp trong Spring AOP

| Mục tiêu | Cơ chế ưu tiên | Lý do |
| --- | --- | --- |
| Chạy bước dọn sau normal return hoặc exception của method matching | `@After` | Semantics after-finally của Spring AOP; không cần quyền điều khiển `proceed()`. |
| Aspect **tự cấp phát** resource trước invocation và phải đóng đúng handle của lần gọi ấy | `@Around` + `try/finally` hoặc `try-with-resources` | Resource handle nằm trong biến cục bộ của chính invocation; tránh dùng singleton aspect field chia sẻ. |
| Tạm đặt `ThreadLocal`/MDC cho target và khôi phục giá trị trước invocation | `@Around` + capture/restore trong `finally` | Scope bao quanh chính phần target dùng context; hỗ trợ nested calls. |
| Ghi nhận **chỉ normal-return value** | `@AfterReturning(returning="result")` | Quan sát kết quả; **không đủ** để cleanup trên exceptional exit. |
| Log/metric exception loại cụ thể | `@AfterThrowing(throwing="error")` | Đọc lỗi; **không đủ** để cleanup trên success. |
| Cleanup resource do Spring transaction management quản lý | Transaction manager/`TransactionSynchronization` | Resource lifecycle phụ thuộc transaction, có thể sống dài hơn một service method. |
| Dọn request-level SecurityContext | Spring Security filter/security infrastructure | Custom service aspect không nên xóa authentication/context của caller. |
| Cleanup khi future hoàn tất hoặc publisher terminal | Callback API/reactive operators đúng lifecycle | Synchronous `finally` trong AOP chỉ bảo vệ lúc method trả wrapper. |

**Quy tắc chọn:** nếu chỉ cần dọn ở cuối method mà không khởi tạo context riêng, `@After` đơn giản hơn. Nếu advice đã tạo một context/resource ở **đầu** call và cần sở hữu handle, `@Around` + `finally` thể hiện ownership/timing rõ nhất.

## 4. @After là after-finally, không phải after-success

Ví dụ annotation từ `org.aspectj.lang.annotation`:

```java
@Aspect
public class CompletionAuditAspect {
    @After(
        "execution(public * com.example.cleanup.TaskService.*(..))"
    )
    public void afterCompletion(JoinPoint jp) {
        System.out.println("finished:" + jp.getSignature().getName());
    }
}
```

Đây là đoạn minh họa, cần imports `Aspect`, `After`, `JoinPoint`, aspect bean và target Spring bean để chạy. Khi target method matching **return bình thường** hoặc **throw exception**, advice có thể ghi sự kiện kết thúc trong cả hai trường hợp.

**Phân biệt:**

```java
@AfterReturning(
    "execution(public * com.example.cleanup.TaskService.*(..))"
)
public void afterSuccess() {
    // Không chạy nếu method thoát bằng exception.
}

@AfterThrowing(
    "execution(public * com.example.cleanup.TaskService.*(..))"
)
public void afterError() {
    // Không chạy nếu method return bình thường.
}

@After(
    "execution(public * com.example.cleanup.TaskService.*(..))"
)
public void afterFinally() {
    // Dành cho bước dọn ở cả hai kiểu thoát.
}
```

Các đoạn trên minh họa **ba advice có semantics khác nhau**, không thêm vào project demo chính nếu không muốn thay stdout. Hai callbacks `@AfterReturning`/`@AfterThrowing` **không phải** một bộ đôi đủ thay thế cleanup chung khi còn các failure modes như chính advice khác ném exception hoặc outer chain short-circuit trước/giữa các layers.

### 4.1. @After không tự nhận return và exception object

`@After` có thể nhận `JoinPoint` để lấy method signature/arguments. Nó **không có cùng cú pháp `returning` của `@AfterReturning` hay `throwing` của `@AfterThrowing` để tự bind đồng thời cả result và exception**.

Nếu cleanup chỉ cần release resource, không cần quan sát payload. Nếu phải biết outcome, có thể kết hợp với các advice thích hợp hoặc dùng **một `@Around`** kiểm soát normal/exception branches mà vẫn đặt cleanup trong `finally`.

### 4.2. Thận trọng với exception do @After ném ra

Ví dụ nguy hiểm:

```java
@After("execution(* com.example.cleanup.TaskService.*(..))")
public void close() {
    throw new IllegalStateException("cleanup failed");
}
```

Đây là anti-pattern minh họa. Nếu target đang ném lỗi gốc, exception do cleanup ném thêm có thể làm caller quan sát lỗi khác, tùy chuỗi advisors. **After-finally nghĩa là có cơ hội cleanup khi join point thoát; không nghĩa Spring tự cách ly mọi lỗi phát sinh trong cleanup.**

Nếu cleanup best-effort, quyết định rõ policy logging/suppression. Nếu resource close failure làm operation thất bại theo contract, truyền lỗi một cách chủ đích và giữ cause/suppressed exceptions khi cần.

## 5. @Around + try/finally cho tài nguyên aspect sở hữu

`@Around` là lựa chọn tự nhiên khi resource/context được tạo **trong cùng một advice**:

```java
@Around("execution(public * com.example.cleanup.TaskService.*(..))")
public Object withResource(ProceedingJoinPoint pjp) throws Throwable {
    final ResourceHandle handle = openMyResource();
    try {
        return pjp.proceed();
    } finally {
        handle.close();
    }
}
```

Đây là đoạn minh họa cần `ResourceHandle`, `openMyResource()` và import Java/Spring tương ứng. Nó đảm bảo trên các đường Java control flow thông thường đi ra khỏi `proceed()`, `handle.close()` được gọi.

Tuy nhiên:

- Nếu `openMyResource()` **throw trước `try`**, handle chưa được tạo để đóng; những trạng thái tạm nào đã được thay đổi trước đó vẫn cần được bảo vệ bằng một outer `try/finally`.
- Nếu `handle.close()` throw trong `finally` khi `proceed()` đang throw, exception cleanup có thể **che exception gốc**.
- Nếu scope được tạo theo invocation, không lưu handle vào field mutable của singleton aspect để các threads ghi đè handle của nhau.
- `pjp.proceed()` chỉ nên chạy một lần nếu không có retry/short-circuit được định nghĩa rõ; cleanup vẫn phải tính cho mỗi attempt khi có retry.
- Nếu resource handle thuộc target hoặc transaction infrastructure, không tự close chỉ vì có thể lấy reference tới nó.

### 5.1. try-with-resources giữ ngoại lệ gốc tốt hơn finally close thủ công

Java `try-with-resources` có quy tắc suppression khi cả body và `close()` đều throw:

```java
try (MyResource resource = acquire()) {
    return pjp.proceed();
}
```

Nếu `proceed()` throw lỗi A và `resource.close()` throw lỗi B, với `try-with-resources`, lỗi A thường là primary, B được lưu trong `A.getSuppressed()`; đây là cơ chế **Java**, rất hữu ích cho Spring AOP cleanup. Nếu body return bình thường nhưng close throw, thì caller có thể nhận close failure: đó là behavior cần được ghi rõ nếu resource release là critical.

**Mã minh họa**: `MyResource`/`acquire()` là giả định. Trong project runnable ở mục 9, `Lease.close()` không ném exception để giữ rõ hợp đồng bảo toàn normal/exception outcome.

### 5.2. Nếu acquisition fail giữa chừng

Với nhiều resources:

```java
try (ResourceA a = openA();
     ResourceB b = openB()) {
    return pjp.proceed();
}
```

Nếu `openA()` thành công nhưng `openB()` throw, Java `try-with-resources` vẫn đóng `a`. Đóng theo thứ tự ngược với thứ tự mở. Điều này tránh rò resource A do việc acquire B fail.

Đây là đoạn minh họa độc lập về syntax Java; không mặc định Spring quản lý hai resource types giả định này. Đối với resources do Spring/JPA/JDBC quản lý, sử dụng lifecycle APIs của framework thay vì tự ý đóng.

## 6. Dọn ThreadLocal/context: khôi phục trạng thái trước đó, không phải luôn clear

Một custom Spring aspect có thể tạm đặt **correlation ID, tracing label hoặc request-scoped marker** vào `ThreadLocal` để service downstream đọc trên cùng thread:

```java
String previous = Context.current();
Context.set("task:" + pjp.getSignature().getName());
try {
    return pjp.proceed();
} finally {
    Context.set(previous);
}
```

Đây là đoạn minh họa. `Context.set(null)` phải thực hiện `ThreadLocal.remove()` trong implementation để không giữ reference dư. **Tại sao khôi phục** thay vì `remove()` vô điều kiện?

Giả sử caller đã đặt `"request-42"`, outer aspect tạm đổi thành `"service:save"`. Khi method xong, code dùng `remove()` vô điều kiện sẽ **xóa luôn request context do caller sở hữu**. Code capture `previous` rồi restore `previous` đúng hơn:

```text
Caller scope: context = request-42
  Aspect enters: previous = request-42
  Aspect sets: context = service:save
    Target reads: service:save
  Aspect finally restores: context = request-42
Caller continues: context = request-42
```

Với nested invocations:

```text
Caller context = caller
  Outer Spring advice sets outer
    Collaborator call via second Spring proxy sets inner
    Inner finally restores outer
  Outer finally restores caller
```

Mỗi layer phải chỉ restore **trạng thái mà layer ấy đã thay**. Nếu mỗi advice gọi `clear()` global, inner layer có thể phá context của outer layer.

### 6.1. ThreadLocal và ThreadPool

Thread pool tái sử dụng threads. Nếu advice đặt `ThreadLocal` mà không restore/remove trong `finally`, một invocation tiếp theo trên cùng worker thread có thể quan sát marker cũ, gây:

- Log correlation sai (dính request/tenant trước);
- Giữ object reference lớn lâu hơn cần thiết;
- Rò dữ liệu ngữ cảnh giữa các requests/tasks;
- Test flaky phụ thuộc thread reuse và thứ tự chạy.

Tuy nhiên, **không tự xóa mọi ThreadLocal của framework khác**. Aspect chỉ dọn ThreadLocal do nó tạo/quản lý, hoặc scope API công khai có ownership rõ.

### 6.2. MDC/SLF4J

Nếu logging tích hợp SLF4J MDC, một Spring `@Around` thường cần thiết lập một marker tạm trong `MDC` rồi restore marker trước đó:

```java
String oldValue = MDC.get("operation");
MDC.put("operation", operationName);
try {
    return pjp.proceed();
} finally {
    if (oldValue == null) {
        MDC.remove("operation");
    } else {
        MDC.put("operation", oldValue);
    }
}
```

Đây là **mẫu minh họa** cần `org.slf4j.MDC` và logger implementation/SLF4J dependency thích hợp. Nó không nằm trong demo không phụ thuộc SLF4J ở mục 9. Không gọi `MDC.clear()` vô điều kiện nếu caller/framework đã đặt request ID hoặc trace fields khác.

Nếu bạn chỉ muốn restore **một key**, giữ đúng key cũ. Nếu advice chủ động thay toàn bộ MDC map, cân nhắc snapshot/restore toàn map bằng API của logging integration và đánh giá chi phí/context coupling.

### 6.3. SecurityContextHolder của Spring Security không thuộc ownership mặc định của aspect

Trong Servlet applications, Spring Security thường lưu authentication state trong `SecurityContextHolder` (mặc định dùng `ThreadLocal`) và xử lý lifecycle qua filter infrastructure. Một custom Spring service aspect **không nên** kết thúc mỗi call bằng:

```java
SecurityContextHolder.clearContext(); // Không làm bừa ở mọi service exit.
```

Code này là **anti-pattern minh họa** khi Spring Security filter/caller vẫn cần authenticated principal. Nó có thể xóa context giữa một request còn đang thực thi, khiến downstream authorization sai.

Chỉ can thiệp nếu component **thật sự sở hữu việc thiết lập context tạm** theo hợp đồng rõ ràng, và phải restore context trước đó đúng cách. Với task execution cross-thread, dùng các công cụ Spring Security `DelegatingSecurityContext...` phù hợp thay vì gán/clear context thủ công tùy tiện.

## 7. Không đóng tài nguyên Spring/JPA/JDBC do tầng khác quản lý

Spring transaction infrastructure có thể bind resources vào thread, quản lý connection/EntityManager và synchronization callbacks. Một `@Aspect` custom không được tự kết luận “method sắp kết thúc, tôi có quyền `Connection.close()`/`EntityManager.close()`”.

### 7.1. @Transactional và resource ownership

Nếu Spring service có:

```java
@Transactional
public void writeOrder(String id) {
    // JPA repository / JdbcTemplate work
}
```

Đoạn minh họa chỉ hợp lệ khi ứng dụng bật transaction management và có transaction manager/data source đúng. `@Transactional` service execution có thể tham gia **transaction của caller**, kéo dài qua nhiều service methods.

Nếu một custom aspect đóng connection/EntityManager trong `@After` của method giữa transaction, resource vẫn có thể đang được Spring cần cho các operations còn lại. Hậu quả có thể là premature close, rollback, lỗi lazy loading hoặc mất context.

**Quy tắc:** dùng `JdbcTemplate`, Spring data repositories, transaction-aware resource access hoặc Spring transaction APIs. Không lấy trực tiếp Spring-bound resource rồi đóng theo lifecycle của *một method advice* nếu resource thuộc lifecycle transaction.

### 7.2. Cleanup cần gắn với transaction completion?

Dùng Spring transaction synchronization:

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // Giải phóng một resource do COMPONENT NÀY sở hữu
                // sau transaction completion tương ứng.
            }
        }
    );
}
```

Đây là đoạn **minh họa**, cần imports từ `org.springframework.transaction.support` và resource ownership rõ ràng. `afterCompletion` nhận status commit/rollback/unknown của transaction synchronization; nó có semantics khác `@After` trên một target method.

Nếu synchronization không active, ứng dụng phải quyết định policy riêng: cleanup ngay, không đăng ký, hoặc báo lỗi theo contract. Không gọi `registerSynchronization` vô điều kiện.

Khi cần sự kiện nghiệp vụ **sau commit**, có thể dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` nếu phù hợp; nó cũng **không phải** thay thế chung cho cleanup local `AutoCloseable`.

### 7.3. Lỗi commit có thể xảy ra sau khi target return

Thứ tự custom aspect và transaction advisor ảnh hưởng việc `@After`/`finally` của bạn nằm trước hay sau commit. Target method return bình thường nhưng commit có thể thất bại ở interceptor bên ngoài. Đừng gán `cleanup-called` nghĩa là database committed.

Với test transaction, xác nhận actual DB state và thời điểm callback đúng transaction boundary; không suy chỉ từ thứ tự log.

## 8. @After và @Around khác nhau ở ownership, không chỉ cú pháp

So sánh hai patterns:

**Pattern A — `@After` giải phóng một state do infrastructure riêng quản lý:**

```java
@After("execution(* com.example.cleanup.TaskService.*(..))")
public void recordEnd(JoinPoint jp) {
    recorder.record(jp.getSignature().getName());
}
```

`@After` có thể hợp lý nếu chỉ ghi completion event vào một bean recorder thread-safe; không cần giữ resource handle được tạo ở đầu invocation. `recorder` là dependency minh họa, chưa có definition trong đoạn code này.

**Pattern B — `@Around` sở hữu scope theo invocation:**

```java
@Around("execution(* com.example.cleanup.TaskService.*(..))")
public Object withScope(ProceedingJoinPoint pjp) throws Throwable {
    String previous = MyContext.current();
    MyContext.set("within");
    try (AutoCloseable resource = myResourceFactory.open()) {
        return pjp.proceed();
    } finally {
        MyContext.restore(previous);
    }
}
```

**Đoạn B chỉ minh họa thiết kế**, `AutoCloseable.close()` có thể khai báo `throws Exception`, vì vậy chữ ký `throws Throwable` của advice cho phép propagate và Java try-with-resources dùng suppression. `MyContext`/`myResourceFactory` phải có implementation.

Pattern B có **scope owner rõ**: aspect tạo handle và giữ nó bằng local variable, bao đúng phạm vi target invocation. Với singleton Spring `@Aspect`, đừng dùng mutable instance fields như `currentHandle`/`currentRequestId` để lưu state per-call vì nhiều requests đồng thời có thể ghi đè nhau.

### 8.1. Không dùng @AfterReturning + @AfterThrowing thay try/finally một cách máy móc

Có thể viết hai advices riêng, nhưng dễ lặp cleanup code và có thể khác loại exception/type binding. Khi resource handle được tạo từ `@Before`, làm sao `@AfterReturning` và `@AfterThrowing` lấy đúng handle theo invocation/thread? Dùng singleton field là sai; chuyển qua ThreadLocal lại thêm một lifecycle phải quản lý.

Nếu logic là **setup → proceed → cleanup**, một `@Around` với lexical scope và `finally` thường gọn, an toàn và dễ test hơn.

## 9. Demo chạy được Java 17+: cleanup ở thành công, void, null và exception

Demo dưới đây thực sự chạy **Spring Framework AOP proxy** với một Spring-managed `@Aspect`. Aspect tạo **một lease (resource handle)** và **đặt một marker ThreadLocal** riêng cho từng invocation. Với cả normal return, `void`, `null` và target exception, aspect luôn đóng lease rồi **khôi phục marker của caller**.

Môi trường: Java 17+, Spring Framework **6.2.19**, AspectJ Weaver **1.9.25.1**, Gradle. Các resource objects được tạo trong demo là **resource giả chỉ ghi count**, không mở socket/JDBC connection; không dùng chúng để suy transaction resource cleanup.

### 9.1. Cây thư mục

```text
spring-aop-cleanup/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/cleanup/
    ├── AppConfig.java
    ├── CallContext.java
    ├── ProbeResources.java
    ├── CleanupAspect.java
    ├── TaskService.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aop-cleanup'
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
    mainClass = 'com.example.cleanup.Main'
}
```

Với Spring Boot production, để Boot BOM quản lý versions của Spring modules và AspectJ, không sao chép các versions của demo vào một project Boot major khác thiếu kiểm tra tương thích.

### 9.2. Một CallContext có restore đúng previous value

**`CallContext.java`**

```java
package com.example.cleanup;

public final class CallContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CallContext() {}

    public static String current() {
        return CURRENT.get();
    }

    public static void set(String value) {
        if (value == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }
}
```

Trong demo, `CallContext` đóng vai trò một **ThreadLocal do ứng dụng/aspect sở hữu**, hoàn toàn độc lập với `SecurityContextHolder` hay Spring transaction manager. `set(null)` gọi `remove()` để release reference ở thread khi không có parent value.

Main cố ý đặt `"caller"` trước các service calls; advice phải restore **`"caller"`**, không được gọi clear vô điều kiện vì trạng thái này do caller sở hữu.

### 9.3. Lease resource mở/đóng có đếm số lần

**`ProbeResources.java`**

```java
package com.example.cleanup;

import java.util.concurrent.atomic.AtomicInteger;

public class ProbeResources {
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();

    public Lease open(String name) {
        opened.incrementAndGet();
        System.out.println("resource:open:" + name);
        return new Lease(name);
    }

    public int openedCount() {
        return opened.get();
    }

    public int closedCount() {
        return closed.get();
    }

    public final class Lease implements AutoCloseable {
        private final String name;
        private boolean done;

        private Lease(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            if (!done) {
                done = true;
                closed.incrementAndGet();
                System.out.println("resource:close:" + name);
            }
        }
    }
}
```

Mỗi `open()` tạo **Lease riêng**; không tái sử dụng một handle mutable chung trong aspect singleton. `close()` trong demo không khai báo/ném checked exception nên không làm che exception từ target.

`AtomicInteger` bảo vệ counters nếu nhiều threads cập nhật; boolean `done` thuộc từng handle và demo không chuyển cùng handle giữa threads. Nếu một resource thực sự được đóng concurrently từ nhiều threads, cần concurrency/idempotency contract riêng.

### 9.4. Spring target có bốn đường thoát

**`TaskService.java`**

```java
package com.example.cleanup;

public class TaskService {
    public String ok(String id) {
        System.out.println("target:ok:context=" + CallContext.current());
        return "ok:" + id;
    }

    public void refresh() {
        System.out.println("target:refresh:context=" + CallContext.current());
    }

    public String missing() {
        System.out.println("target:missing:context=" + CallContext.current());
        return null;
    }

    public String fail() {
        System.out.println("target:fail:context=" + CallContext.current());
        throw new IllegalStateException("boom");
    }
}
```

Các methods đều public/non-final; class không final. `fail()` đại diện exceptional exit; `missing()` đại diện **normal return của `null`**; `refresh()` đại diện normal completion của `void`.

### 9.5. Spring aspect dùng try-with-resources và finally

**`CleanupAspect.java`**

```java
package com.example.cleanup;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class CleanupAspect {
    private final ProbeResources resources;

    public CleanupAspect(ProbeResources resources) {
        this.resources = resources;
    }

    @Around("execution(public * com.example.cleanup.TaskService.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        String previous = CallContext.current();
        CallContext.set("scope:" + method);

        try {
            try (ProbeResources.Lease lease = resources.open(method)) {
                System.out.println(
                    "advice:enter:" + method
                    + ":context=" + CallContext.current()
                );
                return pjp.proceed();
            }
        } finally {
            CallContext.set(previous);
            System.out.println(
                "advice:restore:" + method
                + ":context=" + CallContext.current()
            );
        }
    }
}
```

**Thứ tự hoạt động chính xác:**

1. Capture `previous` **trước** khi thay đổi current marker.
2. Set marker tạm cho invocation hiện tại.
3. `resources.open(method)` tạo Lease.
4. Target được gọi **một lần** qua `pjp.proceed()`.
5. Nếu target return hoặc throw, Java `try-with-resources` gọi `Lease.close()` (trong ví dụ chỉ tăng count và in).
6. `finally` của wrapper **luôn khôi phục previous** khi control flow thoát khỏi phần try, kể cả nếu `open()` hoặc `close()` throw.
7. Advice không bắt rồi biến lỗi thành `null`; nó để exception gốc truyền về caller theo Java propagation rules.
8. Nếu `close()` trong một resource thực tế throw, phải xét suppression/masking behavior; `Lease.close()` trong demo cố ý no-throw để quan sát rõ 4 outcomes.

**Chi tiết thiết kế quan trọng:** đoạn cleanup trong `finally` vẫn có `System.out.println`. Trong app production, ngay cả logging cũng nên được coi là side effect có khả năng lỗi theo môi trường; nếu bảo toàn exception/result là contract bắt buộc, cần thiết kế cleanup/error logging sao cho failure policy tường minh. `finally` chỉ bảo đảm control flow tới cleanup trong các đường Java bình thường, không biến mọi side effect thành infallible.

### 9.6. Đăng ký Spring beans và bật AOP support

**`AppConfig.java`**

```java
package com.example.cleanup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    ProbeResources probeResources() {
        return new ProbeResources();
    }

    @Bean
    CleanupAspect cleanupAspect(ProbeResources resources) {
        return new CleanupAspect(resources);
    }

    @Bean
    TaskService taskService() {
        return new TaskService();
    }
}
```

`@Aspect` không tự là `@Component`; `@Bean` đăng ký aspect vào Spring container. Target `TaskService` cũng là Spring-managed bean. `@EnableAspectJAutoProxy(proxyTargetClass=true)` kích hoạt Spring AOP class-based proxy, nên `getBean(TaskService.class)` có thể trả proxy của concrete class.

Không cần `@ComponentScan`, vì đã khai báo cả ba beans. Spring AOP dùng AspectJ annotations/pointcut parser nhưng interception vẫn là proxy, không cần `-javaagent`.

### 9.7. Main chạy bốn trường hợp và kiểm tra resource count

**`Main.java`**

```java
package com.example.cleanup;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService service = context.getBean(TaskService.class);
            ProbeResources resources = context.getBean(ProbeResources.class);

            CallContext.set("caller");

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("before=" + CallContext.current());

            System.out.println("result=" + service.ok("A"));
            System.out.println("afterOk=" + CallContext.current());

            service.refresh();
            System.out.println("afterRefresh=" + CallContext.current());

            System.out.println(
                "missingIsNull=" + (service.missing() == null)
            );
            System.out.println("afterMissing=" + CallContext.current());

            try {
                service.fail();
            } catch (IllegalStateException error) {
                System.out.println("caught=" + error.getMessage());
            }
            System.out.println("afterFail=" + CallContext.current());

            System.out.println("opened=" + resources.openedCount());
            System.out.println("closed=" + resources.closedCount());

            CallContext.set(null);
            System.out.println("afterClear=" + CallContext.current());
        }
    }
}
```

Ở root project, với Java 17+ và Gradle có sẵn:

```bash
gradle run --quiet
```

Với Gradle Wrapper: `./gradlew run --quiet` (Linux/macOS), `gradlew.bat run --quiet` (Windows).

**Stdout do chương trình in, theo đúng thứ tự** (không tính thông báo ngoài của JVM/Gradle/Spring):

```text
proxy=true
before=caller
resource:open:ok
advice:enter:ok:context=scope:ok
target:ok:context=scope:ok
resource:close:ok
advice:restore:ok:context=caller
result=ok:A
afterOk=caller
resource:open:refresh
advice:enter:refresh:context=scope:refresh
target:refresh:context=scope:refresh
resource:close:refresh
advice:restore:refresh:context=caller
afterRefresh=caller
resource:open:missing
advice:enter:missing:context=scope:missing
target:missing:context=scope:missing
resource:close:missing
advice:restore:missing:context=caller
missingIsNull=true
afterMissing=caller
resource:open:fail
advice:enter:fail:context=scope:fail
target:fail:context=scope:fail
resource:close:fail
advice:restore:fail:context=caller
caught=boom
afterFail=caller
opened=4
closed=4
afterClear=null
```

### 9.8. Bốn outcomes, cùng một cleanup invariant

| Method call | Target outcome | `Lease.close()` được gọi? | `CallContext` sau call | Caller nhận |
| --- | --- | --- | --- | --- |
| `ok("A")` | Normal `String` | Có | `caller` | `ok:A` |
| `refresh()` | Normal `void` | Có | `caller` | Không có value |
| `missing()` | Normal `null` | Có | `caller` | `null` |
| `fail()` | Throw `IllegalStateException` | Có | `caller` | Exception `boom` |

Hai invariants được demo chứng minh theo thiết kế:

- **`opened=4` và `closed=4`**: một lease per intercepted external invocation, đóng đúng một lần mỗi handle.
- **`afterOk=afterRefresh=afterMissing=afterFail=caller`**: nested aspect scope không xóa context của caller. Sau cùng, chính caller dọn `CallContext` mà caller đã đặt, nên `afterClear=null`.

Đừng nhầm **số lần close** với số transaction commits. Lease của demo là resource ứng dụng tự tạo, không phải Spring transaction-managed connection.

## 10. Case nâng cao: nested Spring bean calls và context scopes

Khi một service gọi sang **một Spring collaborator bean khác qua proxy**, có thể tồn tại nhiều nested aspect scopes:

```text
Request scope = req-42
  Proxy A: @Around sets service-A
    Service A calls injected proxy B
      Proxy B: @Around sets service-B
      Service B target sees service-B
      Proxy B finally restores service-A
    Service A target sees service-A again
  Proxy A finally restores req-42
Request scope still req-42
```

Muốn tính đúng, mỗi around advice giữ **`previous` riêng trong local variable** và restore trong `finally`. Không dùng field `previousContext` trên singleton `@Aspect` vì concurrent calls sẽ ghi đè nhau.

Nếu Service A gọi `this.someMethod()` nội bộ, invocation đó **không quay qua Spring AOP proxy**, nên không có scope mới của advice nhắm riêng `someMethod`; đây là giới hạn Spring proxy, không phải lỗi `finally`.

### 10.1. Trường hợp context ban đầu là null

Nếu `CallContext.current()` trả `null` trước khi advice vào:

```java
String previous = CallContext.current(); // null
CallContext.set("scope:work");
try {
    return pjp.proceed();
} finally {
    CallContext.set(previous); // set(null) thực thi ThreadLocal.remove()
}
```

Sau call `ThreadLocal` lại trống. Đây là behavior đúng đối với một invocation không có parent context.

## 11. Tài nguyên đóng lỗi: bảo toàn lỗi gốc và xác định failure policy

**Mọi lời khuyên “luôn đóng trong finally” đều phải xét việc đóng có thể thất bại.**

### 11.1. finally close thủ công có thể che target exception

```java
try {
    return pjp.proceed();  // Ném ServiceException
} finally {
    resource.close();      // Lại ném IOException
}
```

Nếu `close()` ném trên đường unwind của `finally`, Java có thể khiến exception cleanup thay exception service ở caller. Với audit/metric resource không critical, behavior ấy thường không mong muốn.

### 11.2. try-with-resources quản lý suppressed exceptions

```java
try (MyResource resource = acquire()) {
    return pjp.proceed();
}
```

Nếu method body ném A và `close()` ném B, Java `try-with-resources` đặt B vào `A.getSuppressed()` trong trường hợp thông thường. Nhờ đó exception chính còn lại để caller xử lý.

Nhưng không biến `try-with-resources` thành câu “cleanup không bao giờ làm target call thất bại”: nếu target return bình thường nhưng close ném exception, close failure vẫn có thể trở thành lỗi caller nhận.

### 11.3. Suppress lỗi cleanup có kiểm soát

Đối với cleanup **best-effort**:

```java
Throwable primary = null;
try {
    return pjp.proceed();
} catch (Throwable error) {
    primary = error;
    throw error;
} finally {
    try {
        bestEffortResource.close();
    } catch (Exception cleanupFailure) {
        if (primary != null && primary != cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        } else {
            // Chọn rõ policy khi target đã return bình thường.
            // Ví dụ ghi telemetry qua kênh an toàn không ném lỗi.
        }
    }
}
```

Đây là **đoạn minh họa** với `bestEffortResource` đã được tạo ở scope ngoài. Cần quyết định policy cho error từ logger/telemetry và từ `Error` của cleanup. Không lạm dụng `catch(Throwable)` để nuốt các lỗi fatal; nếu resource release là critical, propagate và giữ cause/suppressed information.

### 11.4. Đóng tài nguyên của ai?

| Resource | Chủ sở hữu thường gặp | Hành động của custom aspect |
| --- | --- | --- |
| Lease/temporary handle được aspect tạo riêng per invocation | Chính aspect | Đóng trong scope của advice. |
| File/InputStream/socket do component custom mở | Component mở | Đóng đúng chỗ, chọn lifecycle theo use case. |
| JDBC connection do `JdbcTemplate`/transaction manager quản lý | Spring JDBC/Transaction infrastructure | **Không** đóng thủ công khi method advice thoát. |
| JPA EntityManager của transaction | Persistence/transaction infrastructure | **Không** gọi `close()` tùy tiện trong custom aspect. |
| SecurityContext trong Servlet request | Spring Security filter/security framework | Không `clearContext()` vô điều kiện trong service advice. |
| MDC key do aspect đặt tạm | Aspect | Restore key trước đó, không xóa các keys khác. |
| Reactor Context của reactive chain | Reactor subscription/context operators | Quản lý bằng reactive lifecycle, không dùng một ThreadLocal context giả định xuyên mọi signals. |

## 12. Future, @Async và reactive: dọn đúng vòng đời thực

### 12.1. CompletableFuture

Giả sử service:

```java
public CompletableFuture<String> loadAsync(String id) {
    return remoteCallAsync(id);
}
```

Một Spring `@Around` đồng bộ có thể thấy `pjp.proceed()` **return `CompletableFuture` rất nhanh**. `finally` chạy ngay sau khi trả wrapper khỏi target invocation, **trước khi asynchronous computation thật sự hoàn tất**.

Nếu resource chỉ cần trong lúc **tạo/schedule** future, cleanup trong `finally` có thể đúng. Nếu async task vẫn dùng resource đó, đóng ngay là **quá sớm**.

Có thể gắn completion callback vào future khi hợp đồng cho phép:

```java
CompletableFuture<String> future = serviceOperation();
future.whenComplete((value, error) -> {
    // Cleanup resource được sở hữu cho đến khi future kết thúc.
});
```

Đây là đoạn minh họa. Callback có thể chạy trên thread khác hoặc ngay khi completed; resource ownership cần thread safety, cancellation semantics và error policy. Với `@Async` của Spring, async proxy interceptors có thể chuyển phần target/advice sang executor thread; kiểm tra advisor ordering và execution thread trước khi quyết định context/cleanup scope.

**Không lấy `ThreadLocal` của caller và kỳ vọng nó tự xuất hiện ở worker thread**. Khi cần truyền context, sử dụng Spring TaskDecorator, phù hợp với executor và context cụ thể; với SecurityContext dùng công cụ Spring Security hỗ trợ context propagation.

### 12.2. Mono / Flux

Một method trả reactive publisher:

```java
public Mono<String> findReactive(String id) {
    return repository.find(id);
}
```

Đoạn minh họa cần Reactor/Spring reactive dependencies và repository thật. Với lazy publishers, `@Around` đồng bộ chỉ bọc **thời điểm trả publisher object**, không tự bọc subscription, cancellation hoặc terminal signals.

Khi resource thuộc vòng đời subscription, dùng Reactor `using`/`usingWhen` hoặc `doFinally` với `SignalType` thích hợp; các APIs này xử lý các nhánh complete/error/cancel theo lifecycle reactive. Không dùng `try/finally` đồng bộ để đóng connection mà publisher chỉ dùng **sau** khi subscribe.

### 12.3. Khác biệt giữa exception đồng bộ và exception async

Nếu `pjp.proceed()` throw ngay, `catch/finally` của `@Around` thấy exception đồng bộ. Nếu nó trả `Future` bình thường rồi future complete exceptionally về sau, advice sync **không nhận lần exceptional exit đó** qua `@AfterThrowing` trên method return wrapper. Muốn đo async failure, quan sát future completion hoặc reactive error signal theo đúng API.

## 13. Spring Boot: cấu hình cần đủ nhưng không tự can thiệp lifecycle framework

Trong Spring Boot có AOP auto-configuration phù hợp, bạn có thể đăng ký:

```java
@Aspect
@Component
public class CleanupAspect {
    @Around("execution(public * com.example.app..*Service.*(..))")
    public Object cleanup(ProceedingJoinPoint pjp) throws Throwable {
        // Capture context, acquire owned lease, proceed, restore in finally.
        return pjp.proceed();
    }
}
```

Đây là **khung minh họa**, chưa có logic cleanup; các package/imports cần bổ sung. `@Aspect` **không tự** là Spring bean, vì vậy `@Component` cần nằm trong component scan hoặc aspect cần được trả từ `@Bean`.

Một số lưu ý Spring-specific:

- Spring Framework Java configuration có `@EnableAspectJAutoProxy`; Boot thường tự bật AOP khi có starter thích hợp.
- Với JDK proxy, method được advice phải expose qua interface; với CGLIB, class/method `final` không được override. `private` method không phải Spring AOP proxy entry.
- `this.innerMethod()` từ target bypass proxy; Spring không tạo additional cleanup scope cho nó.
- Aspect bean thường singleton; giữ invocation state trong **locals** chứ không trong shared mutable fields.
- `@Order` với transaction/cache/security/async advisors có thể khiến cleanup scope nằm ngoài hoặc trong features ấy. Kiểm tra actual proxy/advisor chain.
- Không gọi `TransactionSynchronizationManager.clear()`/`SecurityContextHolder.clearContext()` như một “universal cleanup” từ custom business aspect.

Dependencies theo major version của Spring Boot (chọn **một** nếu Boot dependency management tương ứng):

```gradle
// Spring Boot 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

```gradle
// Spring Boot 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

Không thêm đồng thời cả hai chỉ để hy vọng tăng khả năng interception. Spring AOP proxy và Spring transaction/request security lifecycle vẫn cần được hiểu tách biệt.

## 14. Test Spring AOP cleanup đúng cách

### 14.1. Vì sao unit test gọi trực tiếp aspect không đủ?

`new CleanupAspect(resources).around(fakeProceedingJoinPoint)` có thể kiểm tra logic Java của advice nhưng **không chứng minh Spring đã đăng ký @Aspect bean, chọn method execution, tạo proxy hoặc nối interceptor chain**.

Integration test phải lấy `TaskService` **từ ApplicationContext**, gọi qua Spring proxy, kiểm tra `AopUtils.isAopProxy(service)` và assert state/resources sau từng invocation.

### 14.2. Các invariant cần assert

Với demo:

1. `service.ok("A")` trả đúng `"ok:A"`; lease đóng, context restore.
2. `service.refresh()` hoàn tất; lease đóng, context restore dù `void`.
3. `service.missing()` trả đúng `null`; lease đóng, context restore.
4. `service.fail()` vẫn ném `IllegalStateException("boom")`; lease đóng, context restore.
5. `openedCount == closedCount == 4` sau bốn external proxy calls.
6. Caller `"caller"` còn nguyên sau mỗi method call, không bị `finally` xóa nhầm.
7. Nếu caller ban đầu `null`, sau một intercepted call context lại `null`.
8. Một raw `new TaskService()` không kích hoạt Spring advice, nên không làm counter resource của Spring aspect tăng.
9. Nếu thêm nested collaborator bean/proxy, inner scope restore outer scope rồi outer restore caller.
10. Nếu một outer aspect short-circuit không `proceed()`, inner cleanup không được gọi vì inner không sở hữu resource nào ở invocation đó.

### 14.3. Test assertion ví dụ

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    TaskService service = context.getBean(TaskService.class);
    ProbeResources resources = context.getBean(ProbeResources.class);
    CallContext.set("parent");

    if (!"ok:X".equals(service.ok("X"))) {
        throw new AssertionError("Result changed");
    }
    if (!"parent".equals(CallContext.current())) {
        throw new AssertionError("Parent context lost on normal return");
    }

    service.refresh();
    if (service.missing() != null) {
        throw new AssertionError("Null return changed");
    }

    try {
        service.fail();
        throw new AssertionError("Expected IllegalStateException");
    } catch (IllegalStateException expected) {
        if (!"boom".equals(expected.getMessage())) {
            throw new AssertionError("Wrong original exception", expected);
        }
    }

    if (!"parent".equals(CallContext.current())) {
        throw new AssertionError("Parent context lost on exception");
    }
    if (resources.openedCount() != 4 || resources.closedCount() != 4) {
        throw new AssertionError("Resource lease count mismatch");
    }

    CallContext.set(null);
}
```

Đây là **đoạn test minh họa** sử dụng classes trong demo, cần import `AnnotationConfigApplicationContext` và đặt trong Java test/main phù hợp; nó khởi tạo context riêng, **không ghép vào `Main`** nếu muốn giữ stdout mục 9.7. Với JUnit có thể dùng `assertThrows`/`assertEquals` cùng `@SpringJUnitConfig` hoặc `@SpringBootTest`.

### 14.4. Failure injection nên kiểm tra thêm

- Resource acquisition throw: context vẫn restore trong outer `finally`.
- Resource close throw sau target normal return: exception close được xử lý/propagate theo policy đã định.
- Resource close throw khi target đang throw: `try-with-resources` giữ error gốc và đánh dấu close exception suppressed.
- Logger/metric callback throw: không tự ý làm mất target exception/outcome nếu telemetry best-effort.
- Two concurrent requests: không chia sẻ mutable resource handles hoặc current context field trong singleton aspect.
- Async task/thread swap: cleanup thuộc đúng thread và đúng lifecycle thay vì dùng synchronous assertion giả.
- Transaction participation: không đóng Spring-managed resource khi method chỉ là một bước trong transaction rộng hơn.

## 15. Những lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| Aspect cleanup không chạy khi service method kết thúc | Target không qua Spring proxy, pointcut không match, aspect chưa là bean hoặc outer short-circuit | Kiểm tra `AopUtils.isAopProxy`, `@EnableAspectJAutoProxy`, bean registry và caller path. |
| Cleanup chỉ chạy trên success | Đặt trong `@AfterReturning` hoặc sau `proceed()` mà không có `finally` | Chuyển sang `@After` hoặc `@Around` + `finally` đúng ownership. |
| Context request biến mất sau nested service call | Inner aspect gọi `remove()/clear()` vô điều kiện | Capture previous và restore trong `finally`. |
| Thread pool giữ marker của request trước | Thiếu `finally`/`ThreadLocal.remove()` lúc không có parent | Tạo scope per invocation; test thread reuse. |
| Resource close khi target exception bị bỏ qua | Code close chỉ nằm sau `return pjp.proceed()` | Dùng `try-with-resources`/`finally`. |
| Caller nhận cleanup exception thay vì lỗi gốc | `finally close()` ném trên đường exceptional exit | Dùng suppression policy, không throw từ best-effort telemetry. |
| Dao/JPA lỗi sau một service call | Aspect đóng connection/EntityManager do Spring transaction quản lý | Bỏ manual close, dùng Spring transaction-managed APIs. |
| `@After` in log nhưng DB chưa commit | Advice nằm trước transaction completion trong advisor chain | Inspect order và dùng transaction synchronization khi cần afterCompletion. |
| Security principal bị mất giữa request | Custom aspect xóa `SecurityContextHolder` | Để Spring Security filter quản lý; chỉ restore context mà component tự sở hữu. |
| Future lỗi sau khi aspect báo success | Aspect chỉ thấy future wrapper normal return | Quan sát future completion. |
| Mono/Flux dùng resource đã đóng | Aspect sync finally đóng trước khi subscribe | Sử dụng Reactor resource lifecycle `using/usingWhen/doFinally`. |
| Nested proxy scopes chen lẫn sai | Lưu previous context ở field singleton aspect | Dùng local per-invocation state và restore theo LIFO. |
| Cùng một resource bị đóng hai lần | Nhiều advice cùng nhận là owner hoặc cleanup không idempotent | Xác định ownership một nơi, quản lý close contract. |
| @After không chạy khi @Around outer trả sớm | Inner advice không hề được đi vào | Cleanup phải thuộc layer đã acquire resource; không mong downstream finally chạy. |
| `@After` chạy nhưng ngoại lệ business bị đổi | Cleanup code tự throw hoặc swallow trong advice khác | Kiểm tra exception primary/suppressed và contract. |

## 16. Quy trình thiết kế thực tế

**Bước 1 — Xác định resource/context thuộc về ai.** Custom aspect có thực sự mở/đặt nó không? Nếu resource do transaction/security/Servlet/Reactor infrastructure sở hữu, chọn lifecycle API tương ứng, không close/clear thủ công.

**Bước 2 — Chọn đúng scope.** Mọi cleanup cần gắn với một per-invocation handle hoặc local snapshot: `previous ThreadLocal`, `Lease`, span scope, trace field key.

**Bước 3 — Chọn advice ít quyền nhất.** `@After` cho completion observation; `@Around` khi cần setup/teardown lexical scope và giữ resource handle. Đừng dùng `@Around` chỉ để in log sau method nếu `@After` đủ.

**Bước 4 — Chuẩn bị trước khi proceed.** Capture previous context rồi set temporary state; acquire resources trong một `try` được bảo vệ để acquisition failure cũng restore mọi state đã thay.

**Bước 5 — Giữ contract.** `return pjp.proceed()` trong try, không tự đổi return type/reference hoặc nuốt exception trừ khi API/policy cho phép. Chạy `proceed()` một lần nếu không có retry design.

**Bước 6 — Cleanup đáng tin.** Với tài nguyên owned, `try-with-resources` hoặc `finally`; restore parent context trong outer `finally`. Xác định cleanup failure policy, dùng suppressed exceptions để giữ root cause.

**Bước 7 — Kiểm tra nested/concurrent calls.** Không dùng fields singleton aspect để giữ per-call mutable state. Với thread pool, dọn `ThreadLocal` khi previous null; với nested scopes, restore previous thay vì clear global.

**Bước 8 — Xem lifecycle thực.** `@Async`/future/reactive subscription, transaction completion và HTTP request/security scopes có thể dài hơn một method execution. Đặt cleanup vào đúng hook.

**Bước 9 — Khóa bằng integration tests.** Chạy qua Spring proxy thật, test return/void/null/exception, resource counts, parent context restoration, cleanup failure injection, self-call và worker-thread behaviors nếu liên quan.

## 17. Checklist trước khi đưa vào production

- [ ] Aspect có `@Aspect` và được Spring đăng ký làm bean, target là Spring-managed bean?
- [ ] Spring AOP được bật và caller đi qua đúng JDK/CGLIB proxy?
- [ ] Pointcut match đúng service method, không tưởng `this.method()` cũng được advice?
- [ ] Đã phân biệt `@After` finally với `@AfterReturning` success-only và `@AfterThrowing` exception-only?
- [ ] Tài nguyên cần cleanup có thật sự **do aspect sở hữu**, không phải transaction/security infrastructure?
- [ ] Nếu advice khởi tạo resource, đã dùng local handle và `try-with-resources`/`finally`?
- [ ] Nếu thay `ThreadLocal`/MDC, có snapshot previous và restore trong `finally`?
- [ ] Nếu previous null, code `remove()` thay vì giữ key/thread reference?
- [ ] Nested invocations restore context LIFO, không `clear()` parent context?
- [ ] Không lưu per-invocation `Resource`/`previousContext` vào mutable field của singleton aspect?
- [ ] Cleanup failure có policy rõ; không làm mất root exception hoặc biến normal return thành failure ngoài contract?
- [ ] Nếu resource close có thể throw, đã xét `getSuppressed()`/try-with-resources?
- [ ] Không đóng JDBC/JPA Spring-managed connections/entity managers trong custom `@After`?
- [ ] Không xóa `SecurityContextHolder` hoặc transaction synchronization global vô điều kiện?
- [ ] Với future/`@Async`/Reactor, cleanup gắn đúng completion/terminal lifecycle?
- [ ] Có integration tests Spring proxy thật cho success, `void`, `null`, exception, nested và concurrency?
- [ ] Audit cleanup log không được hiểu là transaction commit hoặc async business success?
- [ ] Các outer aspects/cache/security advisors có thể short-circuit đã được xét khi đặt cleanup ownership?

**Ghi nhớ:** Trong Spring AOP, **`@After` là after-finally của matched method execution**, còn **`@Around` + Java `try/finally`/`try-with-resources`** là cách rõ ràng nhất khi chính aspect mở tài nguyên hoặc tạm đặt ngữ cảnh. Dọn **đúng cái mình sở hữu** và **khôi phục trạng thái caller**, không xóa/đóng resource do transaction, security hay async/reactive lifecycle khác quản lý. Hoàn tất method không nhất thiết là hoàn tất transaction hoặc asynchronous computation.

## Tài liệu tham khảo

- [Spring Framework 6.2 — Declaring Advice: After (Finally) Advice và Around Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Resource Synchronization with Transactions](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/tx-resource-synchronization.html)
- [Spring Framework 6.2 — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html)
- [Spring Security — SecurityContextHolder và Servlet authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
- [Java — try-with-resources và suppressed exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-007">Không dùng @AfterThrowing như bằng chứng mọi lỗi đều đã được xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng @AfterThrowing như bằng chứng mọi lỗi đều đã được xử lý trong Spring AOP

## 1. Ý tưởng trọng tâm: quan sát ngoại lệ khác với xử lý ngoại lệ

Trong Spring Framework, một advice khai báo bằng `@AfterThrowing` có thể chạy khi **method execution khớp pointcut** trên một Spring-managed bean **kết thúc bằng việc ném ngoại lệ** qua Spring AOP proxy. Đây là một thời điểm phù hợp để ghi nhận lỗi, metrics, audit hoặc chẩn đoán; **không phải bằng chứng rằng lỗi đã được giải quyết**.

Ví dụ:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.OrderService.submit(..))",
    throwing = "error",
    argNames = "error"
)
public void observedFailure(IllegalArgumentException error) {
    System.out.println("observed: " + error.getMessage());
}
~~~

Giả sử `OrderService.submit(...)` ném `IllegalArgumentException("invalid-order")`. Trong một ứng dụng Spring AOP cấu hình hợp lệ, advice có thể in `observed: invalid-order`, **rồi caller vẫn nhận chính ngoại lệ đó**. Việc ghi nhận một exception không tự động chuyển nó thành kết quả thành công, thông báo HTTP, phản hồi người dùng, transaction rollback hay bản ghi đã được sửa.

**Ba khái niệm phải tách biệt:**

- **Observed (được quan sát):** advice thấy Throwable đi ra từ một method execution phù hợp.
- **Handled (được xử lý):** một thành phần thực sự quyết định cách khôi phục, dịch lỗi, trả phản hồi hay propagate có kiểm soát.
- **Resolved (đã giải quyết):** hệ thống thỏa hợp đồng nghiệp vụ sau lỗi, ví dụ trạng thái hợp lệ, tài nguyên nhất quán hoặc quy trình retry/fallback thành công.

`@AfterThrowing` chủ yếu cung cấp **tín hiệu quan sát exceptional exit**; nó không chứng minh hai trạng thái sau.

Bài chỉ tập trung vào **Spring Aspect/Spring AOP proxy-based**, phù hợp Special Note của module. Không lặp lại lý thuyết Aspect chung.

## 2. Ngữ nghĩa chính thức của @AfterThrowing trong Spring

Spring Framework mô tả `@AfterThrowing` là advice chạy khi một **matched method execution exits by throwing an exception**. Cụ thể:

1. Lớp chứa advice phải được Spring đăng ký làm một **`@Aspect` bean**.
2. Spring AOP cần được bật (ví dụ `@EnableAspectJAutoProxy` khi dùng Spring Framework thuần).
3. Pointcut phải match **method execution** trên target bean.
4. Caller phải đi qua **Spring AOP proxy**, không gọi trực tiếp target ngoài container.
5. Invocation phải kết thúc bằng exception **thoát ra tại boundary được advice**, không chỉ một exception được tạo rồi catch bên trong.
6. Nếu có `throwing = "error"` và tham số có kiểu cụ thể, Throwable phải **tương thích kiểu đó**.

Nếu tất cả điều kiện thỏa, advice có thể chạy. Nhưng khi method advice chỉ log rồi return bình thường, **exception gốc vẫn tiếp tục lan truyền**, trừ khi có một interceptor/caller khác chủ động xử lý nó.

~~~text
Caller
   |
   v
Spring AOP proxy
   |
   +--> matched target method
   |        |
   |        +-- throw IllegalArgumentException("invalid-order")
   |        |
   +--> @AfterThrowing advice
   |        |
   |        +-- log / metrics / audit (KHÔNG tự handle)
   |
   +--> exception đi ra khỏi proxy
   |
   v
Caller phải catch hoặc tiếp tục propagate
~~~

Điểm mấu chốt là **`@AfterThrowing` diễn ra trên đường truyền ngoại lệ**, không tự biến một *exceptional exit* thành *normal return*. Đây cũng không phải nơi bao quát mọi lỗi từ các advice khác: tài liệu Spring lưu ý nó không phải **general exception handling callback** cho exceptions do `@After` hoặc `@AfterReturning` đi kèm gây ra.

## 3. Một dòng log nói được và không nói được điều gì?

Giả sử quan sát được:

~~~text
AOP observed: submit -> invalid-order
~~~

Kết luận hợp lệ:

- Có một invocation mà advice đã được kích hoạt.
- Tại boundary quan sát, method execution đã kết thúc theo nhánh exception mà binding phù hợp.
- Code trong advice đã in được dòng log.
- Nếu log đến từ tham số `Throwable`, ta biết throwable được chuyển vào advice **theo ngữ nghĩa của binding**.

**Không thể kết luận chỉ từ dòng này rằng:**

- Caller đã catch exception.
- HTTP endpoint đã trả status phù hợp.
- Transaction đã rollback hoặc commit theo ý định.
- Cơ sở dữ liệu đã được phục hồi.
- Client đã nhận được thông báo hữu ích.
- Có một retry thành công.
- Tất cả các exceptions khác của ứng dụng đều được quan sát.
- Lỗi đã biến mất khỏi hệ thống.
- Mọi inner/outer interceptor đã chạy.
- Sự kiện đã được gửi thành công đến log/metrics backend bên ngoài.

Một test chỉ assert `logs.contains("AOP observed")` là **test quan sát**, không phải test nghiệp vụ thành công hay test exception handling.

## 4. So sánh @AfterThrowing với các cơ chế Spring khác

| Công cụ / vị trí | Chức năng chính | Có tự biến lỗi thành kết quả hợp lệ không? |
| --- | --- | --- |
| `@AfterThrowing` trên `@Aspect` | Quan sát ngoại lệ thoát method execution theo pointcut và kiểu binding | **Không** |
| `@After` | Chạy after-finally khi method trả bình thường hoặc ném lỗi | Không |
| `@AfterReturning` | Nhận callback khi method execution trả bình thường | Không; không chạy nếu lỗi thoát method |
| `@Around` với `try/catch` và `proceed()` | Có thể bắt, rethrow, translate hoặc return fallback theo contract | **Có thể**, khi code chủ động làm như vậy |
| `try/catch` trong service | Xử lý lỗi tại nơi chủ sở hữu logic nghiệp vụ | **Có thể** |
| Caller `try/catch` | Xử lý exception tại ranh giới gọi method | **Có thể** |
| Spring MVC `@ExceptionHandler` hoặc `@ControllerAdvice` | Chuyển exception trong vòng đời web request thành HTTP response phù hợp | **Có thể**, theo cấu hình |
| Spring transaction interceptor | Áp dụng rollback/commit policy tại transaction boundary | Có thể rollback; **không đồng nghĩa exception đã được handle** |
| Spring Retry / resilience policy | Có thể retry/fallback theo quy tắc riêng | Tùy cấu hình, idempotency và kết quả thực tế |

**Không gộp các khái niệm:** “exception đã được logged”, “exception đã bị caught”, “request đã có HTTP response”, “transaction đã rollback” và “business operation đã recovered” là **các sự kiện khác nhau**.

Ví dụ Spring MVC có thể biến một ngoại lệ thành HTTP 400 bằng `@ExceptionHandler` trong khi Spring AOP `@AfterThrowing` vẫn chỉ làm nhiệm vụ ghi nhận lỗi ở service layer. Hoặc caller tự catch exception mà không có web layer nào tham gia.

## 5. Binding `throwing` chỉ quan sát loại lỗi tương thích

Một advice:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.OrderService.*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterInvalidOrder(IllegalArgumentException error) {
    // Chỉ các exceptions tương thích IllegalArgumentException.
}
~~~

bị giới hạn bởi **cả hai điều kiện**:

1. Pointcut chọn các method executions của `OrderService`.
2. Exception thoát method phải tương thích với `IllegalArgumentException`.

So sánh:

| Advice parameter | Những lỗi có thể bind | Không tự nhận |
| --- | --- | --- |
| `IllegalArgumentException error` | IllegalArgumentException và subclasses | IllegalStateException không cùng nhánh |
| `IllegalStateException error` | IllegalStateException và subclasses | IllegalArgumentException |
| `RuntimeException error` | RuntimeException và subclasses | Checked IOException, AssertionError |
| `Exception error` | Exception và subclasses | Error không phải Exception |
| `Throwable error` | Các Throwable phù hợp từ method join point | Không chứng minh mọi lỗi ở nơi khác đều đi qua pointcut |

**Dùng `Throwable` không biến advice thành global handler.** Nó chỉ mở rộng **loại exception có thể bind**, không mở rộng thành mọi bean, mọi join point hoặc mọi invocation vượt ngoài Spring proxy.

Tên trong `throwing = "error"` phải khớp tên tham số Java advice. `argNames = "error"` khai báo rõ binding để tránh phụ thuộc cách compiler lưu tên tham số. Nếu có `JoinPoint` ở vị trí đầu tiên, Spring cho phép bỏ tên tham số `JoinPoint` khỏi `argNames` theo quy tắc binding documented.

## 6. Ngoại lệ nội bộ đã được catch: @AfterThrowing không nhận tín hiệu

Một target Spring bean:

~~~java
public String recoverInternally() {
    try {
        throw new IllegalArgumentException("temporary");
    } catch (IllegalArgumentException ex) {
        return "RECOVERED";
    }
}
~~~

Nếu caller gọi `recoverInternally()` qua proxy:

- Trong target có một exception được ném ra rồi **catch ngay trong method**.
- Từ góc nhìn Spring AOP method execution, method **trả về `"RECOVERED"` bình thường**.
- `@AfterThrowing` **không chạy** cho exception đã được xử lý nội bộ.
- `@AfterReturning` phù hợp có thể chạy, vì có normal return.
- Điều đó **không** chứng minh bên trong method chưa từng có lỗi.

Vì thế, **không có log `@AfterThrowing` không có nghĩa không có lỗi**, đặc biệt khi code có catch/fallback.

Mặt ngược lại cũng quan trọng: **có log `@AfterThrowing` không có nghĩa lỗi đã được handle**. Hai quan sát này không được suy luận theo hai chiều đơn giản.

## 7. Caller catch và @AfterThrowing không mâu thuẫn

Code caller:

~~~java
try {
    service.submit(true);
} catch (IllegalArgumentException ex) {
    System.out.println("caller caught: " + ex.getMessage());
}
~~~

Nếu `submit(true)` là lời gọi qua Spring proxy và có pointcut/throwing type phù hợp, thứ tự có thể là:

~~~text
target: submit
advice observed: submit -> invalid-order
caller caught: invalid-order
~~~

Tại thời điểm `@AfterThrowing` log, exception đang **đi ra từ matched method execution**. Caller vẫn có thể catch nó **sau đó**. Do đó advice chạy và caller catch **không loại trừ nhau**.

**Không suy sai rằng advice đã xử lý lỗi thay caller.** Nếu xóa `try/catch` ở caller mà không có tầng khác xử lý, exception vẫn lan truyền theo quy tắc Java/Spring thông thường.

Cũng đừng kết luận caller đã catch đúng loại exception chỉ vì advice có binding typed. Mỗi tầng có thể có contract khác; caller nên có test riêng cho propagation và error response.

## 8. Ví dụ Spring AOP với một interceptor bên ngoài xử lý fallback

Một `@Around` advice có thể chủ động catch exception:

~~~java
// Snippet trong một class @Aspect đã là Spring bean.
@Around("execution(* example.OrderService.submit(..))")
public Object recoverAtBoundary(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalArgumentException ex) {
        return "FALLBACK";
    }
}
~~~

Snippet này minh họa điều mà `@AfterThrowing` **không làm theo mặc định**: chuyển exception thành một normal return. Nhưng nếu áp dụng, nó **thay đổi business contract**, nên chỉ đúng khi fallback được nghiệp vụ chấp nhận.

Còn phải xét **vị trí trong advisor chain**:

- Nếu `@Around` nằm **ngoài** `@AfterThrowing`, inner after-throwing advice có thể thấy exception trước, sau đó outer catch và trả fallback; caller chỉ thấy thành công.
- Nếu `@Around` nằm **trong** `@AfterThrowing` và catch exception rồi trả fallback, outer after-throwing advice có thể **không chạy** vì thấy normal return.
- Nếu nhiều aspects có cùng precedence hoặc không khai báo order, không nên dựa vào thứ tự ngẫu nhiên.

**Cảnh báo:** Một `@Around` fallback không nên tự ý nuốt mọi `Throwable` trong một shared aspect. Nó có thể che giấu lỗi hệ thống, thay đổi HTTP status, phá vỡ retry/rollback semantics và làm dữ liệu ở trạng thái không mong muốn.

## 9. Project Maven/Java 17+ chạy được: quan sát ≠ xử lý

Project dùng **Java 17+**, Spring Framework **6.2.19**, AspectJ Weaver **1.9.25.1**. Một Spring service có năm đường gọi được thiết kế để phân biệt:

1. Normal return: không có exceptional exit.
2. Ném `IllegalArgumentException` tới caller: `@AfterThrowing` ghi nhận nhưng **không catch thay caller**.
3. Catch exception bên trong target: aspect không thấy exceptional exit.
4. Ném exception khác kiểu `IllegalStateException`: typed advice không match.
5. Method ngoài gọi `this.submit(true)` nội bộ: proxy chỉ thấy exceptional exit của **method ngoài**, không tạo thêm invocation riêng cho method inner.

Project không cần Spring Boot, HTTP server, database hoặc transaction manager.

### 9.1. Cấu trúc thư mục

~~~text
spring-after-throwing-not-handled/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── OrderService.java
    ├── FailureObserverAspect.java
    └── Main.java
~~~

### 9.2. Maven `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-after-throwing-not-handled</artifactId>
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

Project dùng Spring Framework BOM để các module Spring thống nhất phiên bản. Với ứng dụng Spring Boot thực tế, ưu tiên để Boot quản lý versions/starter phù hợp; không buộc phiên bản của project học tập lên ứng dụng khác.

### 9.3. `OrderService.java` — target nghiệp vụ

~~~java
package example;

public class OrderService {

    public String submit(boolean invalid) {
        System.out.println("target: submit");
        if (invalid) {
            throw new IllegalArgumentException("invalid-order");
        }
        return "ACCEPTED";
    }

    public String recoverInternally() {
        System.out.println("target: recover");
        try {
            throw new IllegalArgumentException("temporary");
        } catch (IllegalArgumentException ex) {
            System.out.println("target: recovered temporary");
            return "RECOVERED";
        }
    }

    public void failState() {
        System.out.println("target: failState");
        throw new IllegalStateException("storage");
    }

    public String forwardInternally() {
        System.out.println("target: forwardInternally");
        return this.submit(true);
    }
}
~~~

Ở đây `submit(true)` ném lỗi, `submit(false)` thành công. `recoverInternally()` bắt lỗi ngay bên trong. `failState()` ném một loại exception khác. `forwardInternally()` gọi trực tiếp `this.submit(true)`, tức là lời gọi **nội bộ trên target**, không đi qua Spring AOP proxy lần thứ hai.

### 9.4. `FailureObserverAspect.java` — aspect chỉ quan sát

~~~java
package example;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class FailureObserverAspect {

    private final CopyOnWriteArrayList<String> observed =
        new CopyOnWriteArrayList<>();

    @AfterThrowing(
        pointcut = "execution(* example.OrderService.*(..))",
        throwing = "failure",
        argNames = "failure"
    )
    public void observe(JoinPoint jp, IllegalArgumentException failure) {
        String method = jp.getSignature().getName();
        String value = method + ":" + failure.getMessage();
        observed.add(value);
        System.out.println("advice observed: " + method
            + " -> " + failure.getMessage());
    }

    public List<String> observations() {
        return List.copyOf(observed);
    }
}
~~~

**Tại sao advice không “handle” exception?**

- Nó ghi một entry và in log; không có `try/catch` bao quanh target execution.
- Method advice trả `void` không biến exception từ target thành success.
- Không có `return` của advice để thay thế return value của target.
- Với `IllegalStateException`, typed parameter `IllegalArgumentException` **không tương thích**, nên advice không chạy.
- Trong `recoverInternally()` không có exception thoát ra, nên cũng không chạy.
- `observations()` chỉ trả bản sao danh sách các sự kiện **đã được observer ghi**, không trả một danh sách **mọi lỗi đã xử lý**.

`CopyOnWriteArrayList` làm ví dụ dễ dùng khi kiểm tra, nhưng trong production không nên giữ vô hạn toàn bộ error messages trong bộ nhớ, và không nên log message thô nếu dữ liệu có thể nhạy cảm.

### 9.5. `AppConfig.java` — đăng ký Spring-managed beans

~~~java
package example;

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
    public FailureObserverAspect failureObserverAspect() {
        return new FailureObserverAspect();
    }
}
~~~

- `@EnableAspectJAutoProxy` bật Spring AOP hỗ trợ `@AspectJ` trong context thuần.
- `@Bean` đưa cả target và aspect vào Spring container; chỉ có `@Aspect` trên class không tự đăng ký bean.
- `proxyTargetClass = true` cho phép demo lấy bean theo concrete class thông qua CGLIB proxy.
- Pointcut được giới hạn ở `OrderService`, không tự áp dụng cho tất cả beans.
- Spring AOP proxy-based vẫn có giới hạn self-invocation dù chọn CGLIB.

### 9.6. `Main.java` — năm tình huống và assertions

~~~java
package example;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService orders = context.getBean(OrderService.class);
            FailureObserverAspect observer =
                context.getBean(FailureObserverAspect.class);

            System.out.println("spring AOP proxy: "
                + AopUtils.isAopProxy(orders));

            System.out.println("-- normal return --");
            System.out.println("caller: " + orders.submit(false));

            System.out.println("-- exception still reaches caller --");
            try {
                orders.submit(true);
            } catch (IllegalArgumentException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            System.out.println("-- exception caught inside target --");
            System.out.println("caller: " + orders.recoverInternally());

            System.out.println("-- different exception type --");
            try {
                orders.failState();
            } catch (IllegalStateException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            System.out.println("-- self invocation --");
            try {
                orders.forwardInternally();
            } catch (IllegalArgumentException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            List<String> expected = List.of(
                "submit:invalid-order",
                "forwardInternally:invalid-order"
            );
            if (!expected.equals(observer.observations())) {
                throw new AssertionError(
                    "Unexpected observations: " + observer.observations()
                );
            }
            System.out.println("verified observed count: "
                + observer.observations().size());
        }
    }
}
~~~

Các dòng `caller caught` chứng minh **caller** đã bắt exception, không phải aspect. Assertion xác minh chính xác các exceptional exits **mà Spring aspect đã quan sát**.

### 9.7. Lệnh chạy

Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

**Stdout của các `System.out.println` trong project:**

~~~text
spring AOP proxy: true
-- normal return --
target: submit
caller: ACCEPTED
-- exception still reaches caller --
target: submit
advice observed: submit -> invalid-order
caller caught: invalid-order
-- exception caught inside target --
target: recover
target: recovered temporary
caller: RECOVERED
-- different exception type --
target: failState
caller caught: storage
-- self invocation --
target: forwardInternally
target: submit
advice observed: forwardInternally -> invalid-order
caller caught: invalid-order
verified observed count: 2
~~~

Maven hoặc runtime có thể phát thêm log ngoài mã ví dụ; các dòng trên mô tả chính xác chuỗi in từ các class mẫu.

### 9.8. Phân tích từng tình huống

**Normal return:** `submit(false)` trả `ACCEPTED`; không có `@AfterThrowing`. Điều này chỉ chứng minh method **không thoát bằng exception** tại proxy boundary; nó chưa chứng minh rằng mọi side effect đều thành công ở những thành phần bên ngoài.

**Ngoại lệ vẫn tới caller:** `submit(true)` ném `IllegalArgumentException`. Aspect ghi nhận nó, sau đó caller mới catch. Nếu xóa catch ở `Main`, exception có thể lan lên tầng gọi bên ngoài; advice không chịu trách nhiệm “giải quyết” ngoại lệ.

**Catch trong target:** `recoverInternally()` có lỗi bên trong nhưng xử lý bằng một `catch` cục bộ rồi trả `RECOVERED`. Không có observation ở after-throwing vì exception đã không thoát method. Đây là phản ví dụ cho suy luận **“không log @AfterThrowing ⇒ không xảy ra lỗi”**.

**Khác kiểu:** `failState()` ném `IllegalStateException`, không hợp với tham số `IllegalArgumentException failure`; aspect không ghi nhận, nhưng caller vẫn nhận lỗi. Đây là phản ví dụ cho suy luận **“observer này nhìn thấy mọi ngoại lệ”**.

**Self-invocation:** caller gọi `forwardInternally()` qua proxy. Target gọi `this.submit(true)` trực tiếp, **không qua proxy cho method submit**; exception lại thoát khỏi chính `forwardInternally()`. Vì thế callback ghi tên **`forwardInternally`**, không ghi một callback riêng cho `submit` nội bộ. Điều này minh họa cần quan sát **method join point ở ranh giới proxy**, không chỉ nơi Throwable được tạo.

## 10. Bảng kết quả: có log hay không, ai thực sự xử lý?

| Lời gọi | Exceptional exit tại proxy? | Typed `@AfterThrowing(IllegalArgumentException)` có chạy? | Người/điểm xử lý thật |
| --- | --- | --- | --- |
| `orders.submit(false)` | Không | Không | Return bình thường, không cần catch exception |
| `orders.submit(true)` | Có, IllegalArgumentException | **Có**, log `submit` | `Main.catch(IllegalArgumentException)` |
| `orders.recoverInternally()` | Không, method đã catch nội bộ | Không | `OrderService.recoverInternally()` |
| `orders.failState()` | Có, IllegalStateException | **Không** do kiểu binding | `Main.catch(IllegalStateException)` |
| `orders.forwardInternally()` | Có, IllegalArgumentException trên method ngoài | **Có**, log `forwardInternally` | `Main.catch(IllegalArgumentException)` |
| `new OrderService().submit(true)` ngoài Spring | Không qua Spring proxy | Không | Caller bên ngoài phải catch/propagate |

**Ý nghĩa:** Một observable callback không cho biết **lỗi đã được xử lý bởi ai**. Để chứng minh handling, phải xác minh behavior của tầng có trách nhiệm: service fallback, caller catch, web response, transaction rollback policy, v.v.

## 11. Exception từ chính advice có thể làm mọi việc tệ hơn

Một after-throwing advice có thể ném exception mới:

~~~java
@AfterThrowing(
    pointcut = "execution(* example.OrderService.submit(..))",
    throwing = "failure",
    argNames = "failure"
)
public void dangerous(IllegalArgumentException failure) {
    throw new IllegalStateException("logging backend unavailable");
}
~~~

Đây là **snippet cảnh báo**, không thuộc demo mục 9. Nếu chạy:

- Target có thể đã ném `IllegalArgumentException("invalid-order")`.
- Lời advice tự ném `IllegalStateException("logging backend unavailable")`.
- Caller có thể thấy lỗi **của advice thay cho exception gốc**, làm mất khả năng chẩn đoán hoặc thay đổi transaction/retry semantics.

Vì vậy logging/metrics trong `@AfterThrowing` nên có **failure policy** rõ ràng. Nếu mục tiêu chỉ là best-effort observation, đừng để backend observability ném exception bừa bãi làm thay đổi nghiệp vụ. Nhưng cũng không nên dùng một `catch(Throwable)` im lặng trên toàn bộ hệ thống để che giấu mọi vấn đề; cân nhắc chính sách ghi nhận lỗi instrumentation phù hợp.

Điều tương tự áp dụng cho `@AfterReturning`, `@After` và quanh `@Around`: advice là code thật, nên nó có thể ném lỗi của chính mình.

## 12. @AfterThrowing không phải callback toàn cục cho lỗi của advice khác

Tài liệu Spring nêu rõ: after-throwing advice dùng để nhận exception từ **join point (user-declared target method)**, không phải một callback tổng quát bắt lỗi từ một `@After` hoặc `@AfterReturning` đi kèm.

Ví dụ:

~~~java
@Aspect
public class ConfusingAspect {

    @AfterReturning("execution(* example.OrderService.submit(..))")
    public void afterSuccess() {
        throw new IllegalStateException("observer failed");
    }

    @AfterThrowing(
        pointcut = "execution(* example.OrderService.submit(..))",
        throwing = "failure",
        argNames = "failure"
    )
    public void afterThrown(Throwable failure) {
        // Không được giả định sẽ được gọi chỉ vì afterSuccess() ném lỗi.
    }
}
~~~

Đây là **snippet giải thích**, không phải file của demo. Mặc dù một exception có thể thoát toàn bộ Spring proxy khi advice khác thất bại, Spring không bảo đảm rằng `@AfterThrowing` trong cùng aspect được dùng như một general exception observer cho lỗi từ những advice cùng aspect.

Nếu cần giám sát lỗi ở **ranh giới toàn bộ chain**, có thể xây một `@Around` observer bao ngoài các tầng cần quan sát, dùng `try/catch(Throwable t)` rồi `throw t`. Nhưng phải kiểm soát ordering để observer thật sự nằm ngoài, và việc bao ngoài như vậy vẫn **không phải giám sát mọi lỗi trong JVM**.

## 13. @AfterThrowing và Spring transactions: quan sát không chứng minh rollback

Trong Spring, transaction thường được thực hiện bằng advisor/interceptor riêng quanh method có `@Transactional`. Một trace có thể gồm:

~~~text
Caller
  -> Audit/Failure Aspect
      -> TransactionInterceptor
          -> TargetService.method()
          <- throw IllegalStateException
      <- TransactionInterceptor đánh dấu rollback/rollback theo policy
  <- Audit/Failure Aspect ghi nhận exception
  <- Caller
~~~

Nhưng **thứ tự có thể khác** nếu audit/failure aspect nằm bên trong transaction interceptor hoặc cấu hình infrastructure advisors khác. Không dựa vào một sơ đồ duy nhất cho mọi ứng dụng.

Quan trọng hơn:

- `@AfterThrowing` **không tự ra lệnh rollback**.
- Spring transaction rollback có **rule theo loại exception**, cấu hình `rollbackFor`/`noRollbackFor`, transaction manager và propagation.
- Một exception đã được **catch và không rethrow** trước khi tới transaction interceptor có thể khiến tầng transaction thấy normal return; cách xử lý phụ thuộc nghiệp vụ/cấu hình.
- Một transaction có thể thất bại ở **commit phase** sau khi target method trả về, vào vị trí khác trong interceptor chain.
- Có transaction rollback **không đồng nghĩa nghiệp vụ đã được phục hồi** hoặc HTTP request đã được handle.
- Một `@AfterThrowing` log exception **không thể một mình làm assertion rằng dữ liệu đã nhất quán**.

**Kiểm thử đúng:** dùng integration test với transaction manager và persistence thực; gọi Spring bean qua proxy, assert exception/HTTP behavior **và** trạng thái dữ liệu trước-sau transaction. Đừng chỉ assert có dòng log.

## 14. Web layer xử lý ngoại lệ bằng cơ chế khác

Với Spring MVC, nếu cần chuyển exception từ service thành HTTP response có chủ đích, có thể dùng `@ExceptionHandler` trong controller hoặc `@RestControllerAdvice`.

Ví dụ **snippet Spring MVC**:

~~~java
@RestControllerAdvice
public class ApiExceptionMapping {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Invalid request");
    }
}
~~~

Đoạn này cần module Spring Web/MVC, imports thích hợp và một web application context; **không thuộc project Maven chạy độc lập ở mục 9**.

Những gì có thể xảy ra khi controller gọi `OrderService.submit(true)` qua Spring AOP proxy:

1. Target service ném `IllegalArgumentException`.
2. Spring `@AfterThrowing` ghi nhận exceptional exit của service.
3. Exception tiếp tục đi tới controller/web exception resolution layer.
4. `@ExceptionHandler` chuyển exception thành HTTP 400 với response cụ thể.

Ở đây **@ExceptionHandler** mới là tầng chuyển lỗi thành HTTP response. `@AfterThrowing` chỉ là observer ở service join point. Không nhầm trách nhiệm hai annotation chỉ vì đều liên quan tới exception.

Tương tự, command-line app, message consumer, scheduled task hay gRPC service phải có **cơ chế handling phù hợp ở boundary của chúng**; `@AfterThrowing` không tự thay thế các cơ chế đó.

## 15. AOP proxy boundary và những khoảng trống quan sát

### 15.1. Object tạo ngoài Spring

~~~java
new OrderService().submit(true);
~~~

Không có Spring-managed proxy ở lời gọi này. Bất kể một class `@Aspect` đã được đăng ký ở application context nào đó, **instance tự `new` này không tự được AOP advice**.

### 15.2. Self-invocation

~~~java
public String forwardInternally() {
    return this.submit(true);
}
~~~

Spring AOP không tự intercept một lời gọi method nội bộ trên `this` khi target code đang thực thi. Nếu `forwardInternally()` được gọi từ client qua proxy và cũng match pointcut, after-throwing có thể thấy **exception thoát từ forwardInternally**, không phải một join point mới cho `submit` bên trong.

### 15.3. JDK/CGLIB và method không thể advice

- JDK dynamic proxy dựa trên interface, caller thường inject qua interface được expose.
- CGLIB proxy dựa trên subclass; final/private methods không thể được override/advice theo cách thông thường.
- Việc đổi JDK sang CGLIB **không giải quyết self-invocation** trong Spring AOP proxy mode.
- Có bean proxy nhưng pointcut chỉ match một số method **không có nghĩa mọi method đều sinh after-throwing event**.

### 15.4. Asynchronous methods

Nếu method trả về `CompletableFuture<T>` hoặc reactive publisher, lỗi xuất hiện **sau khi method đã trả Future/Publisher** có thể không tạo exceptional exit ngay tại Spring AOP method boundary theo cách bạn đang quan sát. Với asynchronous/reactive application, cần instrumentation ở **future completion / reactive subscriber lifecycle** hoặc công cụ quan sát chuyên dụng.

**Không dùng số lượng `@AfterThrowing` callbacks làm tổng số lỗi end-to-end** trong một hệ thống có async tasks nếu chưa định nghĩa chính xác observation boundary.

## 16. Đếm lỗi bằng @AfterThrowing có thể sai theo cả hai chiều

### 16.1. Đếm thiếu (undercount)

- Exception bị catch **bên trong target**.
- Exception bị inner `@Around` xử lý và trả fallback.
- Exception khác kiểu `throwing` binding.
- Pointcut không match class/method.
- Invocation bypass Spring proxy.
- Lỗi phát sinh sau method return trong async/future/publisher.
- Lỗi phát sinh trong một infrastructure component hoặc advice khác, không phải user-declared target join point tương ứng.

### 16.2. Đếm trùng (overcount)

- Một error đi qua **nhiều service-layer proxy boundaries** cùng có aspect quan sát.
- Nhiều `@Aspect` beans hoặc advisors cùng ghi cùng một exception ở những tầng khác nhau.
- Retry gọi target nhiều lần, mỗi attempt ném lỗi và bị observer ghi riêng.
- Nested proxies gây cùng một failure xuất hiện trong nhiều interceptor chains.
- Một outer method và inner method **đều gọi qua Spring proxy** và đều quan sát cùng failure trong hai method invocations khác nhau.

**Do đó, metric “@AfterThrowing triggered N times” không tự bằng “N business operations failed”**. Cần xác định **đơn vị đếm**: method invocation, attempt, request, transaction, business command hay end-to-end outcome.

Để thống kê đáng tin cậy, dùng correlation ID/invocation ID (không chứa dữ liệu nhạy cảm), tránh trùng trên cùng logical operation và bổ sung end-to-end outcome metrics khi cần.

## 17. Kiểm thử: đừng chỉ assert callback đã được gọi

Một bộ test cần nhiều lớp xác nhận.

### 17.1. Ma trận test bắt buộc

| Trường hợp | Advice có thể quan sát? | Hành vi nghiệp vụ cần assert |
| --- | --- | --- |
| Method trả bình thường | Không có `@AfterThrowing` | Return value đúng |
| Method ném đúng exception type | Có, nếu pointcut/proxy match | Caller vẫn nhận đúng Throwable khi chưa có handler |
| Method ném sai exception type | Typed observer không chạy | Caller vẫn thấy exception phù hợp |
| Exception được catch nội bộ | Không có exceptional exit ở method boundary | Fallback đúng, trạng thái nhất quán |
| Caller catch exception | Observer có thể chạy **trước** catch | Catch branch đã được thực hiện |
| Self-invocation | Không có thêm inner proxy invocation | Không ghi nhầm inner join point |
| Outer `@Around` trả fallback | Tùy advisor ordering | Return/fallback đúng contract |
| @AfterThrowing tự ném lỗi | Có rủi ro che ngoại lệ gốc | Instrumentation failure policy được kiểm thử |
| @Transactional service ném lỗi | Tùy pointcut, order và rollback policy | Assert database state/transaction outcome thực |
| Web controller gặp lỗi | Có thể observer ghi ở service | Assert HTTP response/status/body riêng |
| Retry cùng operation | Có thể log nhiều attempts | Assert chỉ một logical operation outcome khi phù hợp |
| Future/publisher thất bại muộn | Method boundary có thể không thấy | Assert completion/cancellation outcome bằng API tương ứng |

### 17.2. Kiểm thử đúng exception type và message

Ví dụ ý tưởng JUnit 5, cần thêm `junit-jupiter` và Spring test dependencies thích hợp nếu triển khai trong project thực:

~~~java
IllegalArgumentException ex = assertThrows(
    IllegalArgumentException.class,
    () -> orders.submit(true)
);
assertEquals("invalid-order", ex.getMessage());
~~~

Đây là **snippet kiểm thử**, không được chèn nguyên xi vào `Main.java` của project mục 9 khi muốn giữ stdout. Nếu test chỉ kiểm `observer.observations().size() == 1` mà không dùng `assertThrows` hoặc kiểm tra state/response, test sẽ không chứng minh propagation đúng.

### 17.3. Kiểm thử “xử lý thành công” ở nơi xử lý thật

Nếu service có `recoverInternally()`, hãy assert `"RECOVERED"` cùng các hậu điều kiện. Nếu controller dùng `@ExceptionHandler`, hãy dùng MockMvc/WebTestClient thích hợp để assert HTTP status. Nếu transaction rollback là yêu cầu, hãy chạy test có transaction/persistence config thật và xác nhận DB state; chỉ log `@AfterThrowing` không đủ.

**Nguyên tắc test:** Một test cho **observability** và một test cho **business handling** là hai kiểm chứng khác nhau.

## 18. Lỗi thường gặp và cách sửa

| Triệu chứng / suy luận sai | Giải thích đúng | Hành động phù hợp |
| --- | --- | --- |
| “Đã log ở @AfterThrowing nên lỗi được xử lý” | Logging không catch/translate/fallback | Xác định handler thật và kiểm thử kết quả caller |
| “Không có @AfterThrowing nên không hề xảy ra lỗi” | Lỗi có thể bị catch nội bộ, kiểu/pointcut không match | Kiểm tra boundary, exception paths và negative cases |
| “@AfterThrowing nhận tất cả exceptions” | Chỉ matched method executions và typed binding | Scope rõ pointcut/throwable, thêm observability ở tầng đúng |
| “throwing=error tự catch exception” | `throwing` chỉ bind ngoại lệ vào tham số advice | Đặt catch/fallback ở tầng sở hữu hợp đồng |
| “Advice trả void nghĩa là swallowed exception” | Void callback hoàn tất không biến exceptional exit thành success | Assert `assertThrows`/caller catch |
| “Throwable parameter đủ để monitor mọi lỗi JVM” | Không vượt proxy, pointcut và join point limits | Dùng application-level error handling/monitoring bổ sung |
| “Spring transaction chắc rollback khi after-throwing log” | Rollback do tx policy/order/config | Test transaction outcome, rollback rules |
| “@AfterThrowing sẽ catch lỗi từ @AfterReturning cùng aspect” | Không phải general exception callback | Xem tài liệu và dùng outer observer rõ scope nếu cần |
| “Self-call khiến method inner luôn có AOP advice” | Self-invocation bypass proxy | Tách sang bean khác hoặc đổi đường gọi qua proxy |
| “Nhiều log nghĩa là nhiều lỗi nghiệp vụ độc lập” | Một failure có thể đi qua nhiều boundaries/attempts | Correlate theo logical operation |
| “Không cần handler web vì đã có aspect” | AOP observer không tạo HTTP response | Dùng @ExceptionHandler/@ControllerAdvice thích hợp |
| “Thêm catch Throwable trong @Around là luôn an toàn” | Có thể nuốt Error/che lỗi/cản rollback | Chỉ catch theo policy đã xác định, rethrow khi cần |
| “Logged exception message luôn an toàn” | Có thể có PII/secrets | Chuẩn hóa event schema, giới hạn dữ liệu |
| “Đo exception trong async method giống synchronous” | Future/publisher có completion boundary khác | Hook vào lifecycle thực của async/reaction |
| “Pointcut match một bean là đủ” | Method nào đó mới là unit được advice | Kiểm tra method-level matching, proxy reference |

### Quy trình chẩn đoán thực tế

1. **Xác minh aspect bean:** `@Aspect` được đăng ký bằng `@Component` hoặc `@Bean`; AOP đã bật.
2. **Xác minh target bean:** caller lấy đúng Spring-managed proxy, không dùng `new` hoặc unwrapped target.
3. **Xác minh pointcut:** method/class/package có thật sự match không?
4. **Xác minh kiểu throwing:** `Throwable`, `Exception` hay loại chuyên biệt?
5. **Xác minh source của lỗi:** target method, advice bên ngoài, transaction commit, async completion?
6. **Xác minh ai catch:** target, inner interceptor, outer `@Around`, caller, MVC exception resolver?
7. **Xác minh propagation:** test exception type, message, identity nếu cần.
8. **Xác minh side effects và trạng thái dữ liệu:** rollback/commit/fallback có đúng không?
9. **Xác minh counts:** một event observer có thể không tương ứng một logical error.
10. **Xác minh bảo mật log:** chỉ ghi thông tin cần thiết, không lộ request payload/credentials.

## 19. Bài tập vận dụng

**Câu 1.** Một `@AfterThrowing` log `IllegalArgumentException("bad")` rồi trả `void`. Nếu caller không catch, exception sẽ ra sao?

**Đáp án:** Exception vẫn tiếp tục lan truyền theo đường gọi Spring AOP bình thường, trừ khi một tầng khác chủ động xử lý. Việc advice return void không xóa exception.

**Câu 2.** Method target có `try/catch` bắt lỗi rồi trả `"RECOVERED"`. Vì sao observer after-throwing không chạy?

**Đáp án:** Tại Spring AOP method boundary đó là **normal return**, không phải exceptional exit. Lỗi tồn tại bên trong method nhưng đã được catch trước khi thoát.

**Câu 3.** Advice bind `IllegalArgumentException error`. Target ném `IllegalStateException`. Có chứng minh hệ thống không có lỗi chỉ vì observer không log không?

**Đáp án:** Không. Typed binding không tương thích exception đó; caller vẫn có thể nhận lỗi.

**Câu 4.** Một request đã trả HTTP 400, service after-throwing cũng log lỗi. Thành phần nào xử lý HTTP response?

**Đáp án:** Thành phần web exception resolution, ví dụ `@ExceptionHandler`/`@RestControllerAdvice`. Observer ở service không tự tạo HTTP 400.

**Câu 5.** Có @AfterThrowing log exception trong một `@Transactional` method. Có khẳng định DB chắc chắn rollback thành công không?

**Đáp án:** Không. Rollback chịu sự điều khiển của transaction interceptor, rollback rules, manager và advisor ordering; phải kiểm thử DB state và tx outcome.

**Câu 6.** Vì sao `this.submit(true)` có thể không sinh một log cho method submit nhưng vẫn sinh log cho method ngoài forwardInternally?

**Đáp án:** Self-invocation bỏ qua proxy đối với submit; khi exception thoát khỏi forwardInternally được caller gọi qua proxy, after-throwing quan sát **join point của forwardInternally**.

**Câu 7.** Một `@Around` outer bắt lỗi rồi trả fallback. @AfterThrowing inner có thể đã chạy không?

**Đáp án:** Có. Inner observer có thể đã thấy lỗi trước khi outer catch. Khi đó outer caller thấy normal return, dù inner đã ghi exception.

**Câu 8.** Một `@AfterThrowing` advice ném exception mới vì logging backend thất bại. Điều gì nguy hiểm?

**Đáp án:** Exception mới có thể che mất lỗi nghiệp vụ gốc và thay đổi behavior/transaction semantics. Instrumentation cần failure policy rõ.

**Câu 9.** Một method trả CompletableFuture ngay, rồi future mới hoàn tất thất bại. Có thể lấy @AfterThrowing ở method làm nguồn sự thật duy nhất cho failure không?

**Đáp án:** Không. Lỗi completion có thể xuất hiện sau method return và thuộc async lifecycle; phải quan sát completion với API thích hợp.

**Câu 10.** Phương pháp đúng để chứng minh một exception đã được xử lý là gì?

**Đáp án:** Xác định tầng sở hữu handling, rồi assert **kết quả thực tế**: caller nhận fallback, HTTP status đúng, transaction/DB state hợp lệ hoặc trạng thái nghiệp vụ đã phục hồi; không chỉ tìm dòng log từ AOP.

## 20. Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice: After Throwing Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): ngữ nghĩa khi method exits by throwing, `throwing` typed binding, và lưu ý **không phải general exception handling callback**.
- [Spring Framework — After Returning / After (Finally) Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): phân biệt normal return, exceptional exit và finally semantics.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB AOP proxies, self-invocation và ranh giới lời gọi.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký aspect bean và bật Spring AOP.
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): giới hạn method execution trong Spring-managed beans.
- [Spring Framework — Declarative Transaction Rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): rollback rules khác với việc aspect quan sát exception.
- [Spring Framework — MVC Exception Handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html): dùng `@ExceptionHandler` để xử lý exceptions ở web layer.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-008">Kiểm thử việc thay đổi kết quả hoặc ánh xạ ngoại lệ bằng @Around</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử việc thay đổi kết quả hoặc ánh xạ ngoại lệ bằng @Around trong Spring AOP

## 1. Bài toán và tiêu chí thành công

Trong Spring Framework, `@Around` là loại advice có khả năng can thiệp lớn nhất vào một method execution của **Spring-managed bean**: advice quyết định có gọi `ProceedingJoinPoint.proceed()`, truyền tham số nào, trả kết quả nào cho caller và xử lý exception ra sao. Do đó, kiểm thử `@Around` không thể dừng ở việc “advice đã được gọi”. Phải kiểm tra **hợp đồng mà caller thực sự quan sát** và các tác động lên target.

Bài học này tập trung vào hai hành vi **có chủ đích**:

1. **Thay đổi kết quả trả về**: method đích tạo một giá trị, advice chuyển đổi giá trị đó rồi trả một giá trị *cùng kiểu hợp đồng* cho caller. Ví dụ target trả `100`, policy giảm `10`, caller nhận `90`.
2. **Ánh xạ ngoại lệ**: method đích ném một loại lỗi hạ tầng hoặc lỗi thấp tầng đã biết; advice chuyển thành exception thuộc contract công bố ở service boundary và giữ **original cause**. Ví dụ `IllegalArgumentException("unknown:missing")` được ánh xạ sang `CatalogException("catalog-missing")` với `getCause()` trỏ về lỗi ban đầu.

Đồng thời phải chứng minh **những thứ không được biến đổi**:

- Method trả kết quả bình thường mà không cần chuyển đổi vẫn cho caller thấy chính kết quả đó.
- Exception không thuộc quy tắc mapping vẫn truyền qua, không bị gói/trả fallback tùy tiện.
- Method không khớp pointcut không tự có advice.
- Lời gọi `this.method()` nội bộ và object do `new` ở ngoài Spring không tự đi qua Spring proxy.
- Target được gọi đúng **một lần** trong các trường hợp không có chính sách retry hoặc short-circuit.
- Không có hành vi “test pass” chỉ vì câu lệnh in log: các assertions cần đánh giá **giá trị, kiểu, exception, cause và side effects**.

**Phạm vi:** Spring Aspect được áp dụng bằng Spring AOP proxy; cú pháp `@AspectJ` được Spring hỗ trợ, nhưng bài không yêu cầu AspectJ compile-time/load-time weaving.

## 2. Quyền của @Around và vì sao phải kiểm thử ở boundary

Khi caller lấy service từ `ApplicationContext`, Spring có thể tạo AOP proxy nếu advisor khớp. `@Around` bao phần còn lại của interceptor chain:

~~~text
Caller gọi service.total(100)
       |
       v
Spring AOP proxy
       |
       v
@Around: before
       |
       | pjp.proceed()
       v
Target.total(100) --> 100
       |
       v
@Around: đổi 100 thành 90
       |
       | return Integer.valueOf(90)
       v
Caller nhận int 90
~~~

Một ví dụ khác về exception:

~~~text
Caller gọi service.load("missing")
       |
       v
Spring AOP proxy
       |
       v
@Around: try { pjp.proceed(); }
       |
       v
Target.load(...) throws IllegalArgumentException
       |
       v
@Around: catch (IllegalArgumentException original)
       |
       | throw new CatalogException("catalog-missing", original)
       v
Caller bắt CatalogException
       |
       +-- getMessage() = "catalog-missing"
       +-- getCause()   = IllegalArgumentException
       +-- cause message = "unknown:missing"
~~~

Trong Spring AOP, `pjp.proceed()` tiếp tục qua **các interceptor/advice bên trong** trước khi đến target, nên exception được bắt bởi `@Around` có thể đến từ một inner interceptor chứ không chỉ từ thân target. Nếu ứng dụng có nhiều advisors, cần kiểm tra vị trí và trách nhiệm exception mapping dựa trên chain thực tế.

### Quy tắc lựa chọn advice

`@AfterReturning` phù hợp khi **chỉ quan sát** một giá trị trả về thành công; nó không thay thế reference trả về. `@AfterThrowing` phù hợp khi **chỉ ghi nhận** exception, không biến nó thành kiểu lỗi khác hay normal return. `@Around` được sử dụng ở đây vì requirement thực sự là **thay return value và ánh xạ exception**. Đừng dùng `@Around` cho mọi nhiệm vụ logging chỉ vì nó có nhiều quyền can thiệp.

Một advice chỉ cần quan sát nên viết:

~~~java
@Around("execution(* demo.PricingService.*(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
~~~

Đây là snippet minh họa trong Spring bean `@Aspect`, không phải class chạy độc lập. Nó giữ return/exception của chain. Nếu requirement không cần logic “trước và sau” trên cùng invocation, cân nhắc advice hẹp hơn.

## 3. Ma trận yêu cầu trước khi viết test

Ta có service `PricingService` và hai pointcut riêng: một cho `total(int)`, một cho `load(String)`. Ma trận được định nghĩa **trước khi chạy chương trình**:

| Lời gọi | Hành vi của target | Hành vi Spring @Around mong đợi | Caller cần thấy |
| --- | --- | --- | --- |
| `service.total(100)` | Trả primitive `100` | `proceed()` đúng một lần, trừ 10 | `int 90` |
| `service.load("ready")` | Trả `"item:ready"` | Không chuyển đổi result | `String "item:ready"` |
| `service.load("missing")` | Ném `IllegalArgumentException("unknown:missing")` | Ánh xạ đúng một lỗi đã biết, giữ cause | `CatalogException("catalog-missing")` |
| `service.load("down")` | Ném `IllegalStateException("backend-down")` | Không match quy tắc catch | Vẫn là `IllegalStateException` |
| `service.peek()` | Trả `"peek-ok"` | Không pointcut nào match | Không có marker advice |
| `service.loadInside("missing")` | `this.load("missing")` ném lỗi gốc | Internal call bypass Spring proxy | `IllegalArgumentException` không được map |
| `new PricingService(trace).total(100)` | Trả 100 | Không Spring auto-proxy | `int 100` |
| `new PricingService(trace).load("missing")` | Ném lỗi gốc | Không advice | `IllegalArgumentException` |

Các ca dương xác nhận transformation/mapping đúng, trong khi ca âm xác nhận **phạm vi thay đổi không lan ngoài yêu cầu**. Nếu chỉ kiểm tra `total(100) == 90`, có thể vẫn còn bug nuốt exception; nếu chỉ assert `CatalogException`, bạn có thể bỏ mất stack/cause gốc.

## 4. Project Maven Java 17+ đầy đủ

Project Spring Framework thuần, không cần Spring Boot, server, database hay mock library.

~~~text
spring-around-contract-test/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── App.java
    │   ├── EventTrace.java
    │   ├── PricingService.java
    │   ├── CatalogException.java
    │   └── ResultMappingAspect.java
    └── test/java/demo/
        └── AroundContractTest.java
~~~

Yêu cầu: Java 17+, Maven 3.9+ và khả năng lấy dependencies. Phiên bản minh họa cố định: **Spring Framework 6.2.0**, **AspectJ Weaver 1.9.22**, **JUnit Jupiter 5.11.0**. Đây là version cho ví dụ, không khẳng định phiên bản mới nhất.

### 4.1. Maven pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-around-contract-test</artifactId>
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

`spring-context` cung cấp bean container/Java config. `spring-aop` cung cấp proxy, advisors và `AopUtils`. `aspectjweaver` được Spring dùng cho biểu thức `@AspectJ`; chỉ thêm dependency đó **không kích hoạt AspectJ weaving**. JUnit Jupiter và Surefire cho phép chạy các test tự động.

### 4.2. EventTrace: quan sát cả advice và target

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
}
~~~

Trong ví dụ, mỗi test tạo một context/trace riêng và chạy tuần tự, vì thế `ArrayList` phù hợp. Nếu code thật ghi audit đồng thời nhiều threads, cần collector hoặc logging framework thread-safe. `snapshot()` giúp assertions so nội dung trace mà không sửa danh sách gốc.

Trace được ghi **bên trong method advice và target**, cho phép phân biệt `"advice chạy"` với `"target chạy"`; chỉ dùng `AopUtils.isAopProxy` không chứng minh advisor cụ thể đã khớp.

### 4.3. Lỗi cấp contract: CatalogException

Tạo `src/main/java/demo/CatalogException.java`:

~~~java
package demo;

public class CatalogException extends RuntimeException {
    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

`CatalogException` là runtime exception của ví dụ, **không phải built-in exception của Spring**. Constructor nhận `cause` là yếu tố bắt buộc trong policy đã đề ra: caller và observability có thể truy ngược lỗi gốc. Không chuyển lỗi thành chuỗi message rồi đánh mất stack/cause.

Một contract tốt nên giải thích vì sao ánh xạ `IllegalArgumentException` thành `CatalogException` ở service boundary có ý nghĩa. Ở ví dụ học tập, `IllegalArgumentException("unknown:missing")` tượng trưng cho lỗi “không tìm thấy” từ tầng nội bộ; production không nên bắt **mọi `IllegalArgumentException`** trong mọi service vì nó còn có thể biểu thị **programming bug**. Hãy thu hẹp pointcut và dùng loại lỗi cụ thể hơn nếu module có cơ chế error code rõ.

### 4.4. Target PricingService

Tạo `src/main/java/demo/PricingService.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class PricingService {
    private final EventTrace trace;
    private final AtomicInteger totalCalls = new AtomicInteger();
    private final AtomicInteger loadCalls = new AtomicInteger();

    public PricingService(EventTrace trace) {
        this.trace = trace;
    }

    public int total(int base) {
        totalCalls.incrementAndGet();
        trace.add("TARGET:total:" + base);
        return base;
    }

    public String load(String code) {
        loadCalls.incrementAndGet();
        trace.add("TARGET:load:" + code);

        if ("missing".equals(code)) {
            throw new IllegalArgumentException("unknown:missing");
        }
        if ("down".equals(code)) {
            throw new IllegalStateException("backend-down");
        }
        return "item:" + code;
    }

    public String peek() {
        trace.add("TARGET:peek");
        return "peek-ok";
    }

    public String loadInside(String code) {
        trace.add("TARGET:loadInside");
        return this.load(code);
    }

    public int totalCalls() {
        return totalCalls.get();
    }

    public int loadCalls() {
        return loadCalls.get();
    }
}
~~~

Các method có chủ đích:

- `total(int)` trả đúng tham số `base`. Nếu caller nhận 90 khi đưa vào 100, chứng tỏ **phép biến đổi return thuộc advice**, không phải target.
- `load("ready")` trả bình thường; `load("missing")` ném một lỗi đúng policy; `load("down")` ném loại lỗi khác để thử passthrough.
- `peek()` không match bất kỳ pointcut nào ở phần 4.5.
- `loadInside(String)` gọi `this.load(code)` **bên trong cùng target**, là ví dụ self-invocation không qua Spring proxy.
- `totalCalls()/loadCalls()` là các bộ đếm **số lần thực thi method target**. Một test có thể assert số lần bằng 1 để phát hiện retry/double proceed không được yêu cầu.

Code chạy với Java 17; class và methods không `final`, nên Spring có thể dùng CGLIB class proxy. Tránh hiểu rằng `new PricingService(...)` ở ngoài context cũng được advice; việc đối tượng là Spring-managed bean và caller đi qua proxy mới là điều kiện cần.

### 4.5. Spring @Aspect: biến đổi giá trị và map exception

Tạo `src/main/java/demo/ResultMappingAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ResultMappingAspect {
    private final EventTrace trace;
    private final AtomicInteger adviceCalls = new AtomicInteger();

    public ResultMappingAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(int demo.PricingService.total(int))")
    public Object discount(ProceedingJoinPoint point) throws Throwable {
        adviceCalls.incrementAndGet();
        trace.add("ADVICE:total:before");

        int original = (Integer) point.proceed();
        int transformed = original - 10;

        trace.add("ADVICE:total:after");
        return transformed;
    }

    @Around("execution(String demo.PricingService.load(String))")
    public Object mapLoadError(ProceedingJoinPoint point) throws Throwable {
        adviceCalls.incrementAndGet();
        trace.add("ADVICE:load:before");

        try {
            Object result = point.proceed();
            trace.add("ADVICE:load:return");
            return result;
        } catch (IllegalArgumentException original) {
            trace.add("ADVICE:load:map");
            throw new CatalogException("catalog-missing", original);
        }
    }

    public int adviceCalls() {
        return adviceCalls.get();
    }
}
~~~

Cơ chế và ý nghĩa kiểm thử:

1. `@Around` trả `Object`, có tham số `ProceedingJoinPoint` đầu tiên và khai báo `throws Throwable` để cho phép chain truyền exception.
2. `discount(...)` chặn đúng `int PricingService.total(int)`. Vì target trả primitive `int`, result của `proceed()` tại advice được boxed thành `Integer`, nên phép cast/unbox `(Integer)` là phù hợp trong **project này**.
3. `return transformed;` được auto-box thành `Integer` trên đường AOP interceptor; caller của method `int total(int)` nhận `int` sau unboxing. Không trả `String "90"` hoặc `null`.
4. `point.proceed()` được gọi **một lần**, không phải hai lần. Nếu target ghi DB hoặc gửi thông báo, hai lần proceed sẽ tạo side effects ngoài yêu cầu.
5. `mapLoadError(...)` có scope **duy nhất `PricingService.load(String)`**, không áp dụng cho `peek` hay toàn bộ Spring beans. Khi không có exception, nó trả **chính result** của `proceed()`.
6. Chỉ `IllegalArgumentException` bị map thành `CatalogException`. Exception `IllegalStateException` không khớp `catch`, nên **tự propagate** qua advice tới caller.
7. Constructor `CatalogException("catalog-missing", original)` giữ cause gốc; không ép lỗi thành normal return hoặc thay bằng exception thiếu nguyên nhân.
8. `adviceCalls` đếm các lần enter advice, bao gồm invocation sẽ ném exception. Mỗi test kiểm tra cả counter lẫn trace, không dựa duy nhất vào log.
9. Bốn advice pointcut ở đây **không tồn tại**: chỉ có hai `@Around` methods, trên hai method đích khác nhau. Vì vậy không có ambiguity về thứ tự multiple advices trên cùng target method.
10. Trong ứng dụng thật, việc biến đổi giá bằng trừ 10 và map exception cần **business contract rõ ràng**. Ví dụ cố ý đơn giản để kiểm chứng cơ chế Spring AOP, không phải một giải pháp tính giá hoặc error mapping sản xuất hoàn chỉnh.

### 4.6. Java configuration và main

Tạo `src/main/java/demo/App.java`:

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
    public EventTrace eventTrace() {
        return new EventTrace();
    }

    @Bean
    public PricingService pricingService(EventTrace trace) {
        return new PricingService(trace);
    }

    @Bean
    public ResultMappingAspect resultMappingAspect(EventTrace trace) {
        return new ResultMappingAspect(trace);
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);

            boolean proxy = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxy);

            int total = service.total(100);
            System.out.println("TOTAL: " + total);

            String loaded = service.load("ready");
            System.out.println("LOAD_OK: " + loaded);

            boolean mapped = false;
            try {
                service.load("missing");
            } catch (CatalogException error) {
                Throwable cause = error.getCause();
                mapped = "catalog-missing".equals(error.getMessage())
                        && cause instanceof IllegalArgumentException
                        && "unknown:missing".equals(cause.getMessage());
                System.out.println(
                        "MAPPED: " + error.getClass().getSimpleName()
                        + " <= " + cause.getClass().getSimpleName());
            }

            boolean passedThrough = false;
            try {
                service.load("down");
            } catch (IllegalStateException error) {
                passedThrough = "backend-down".equals(error.getMessage());
                System.out.println(
                        "PASSTHROUGH: " + error.getClass().getSimpleName());
            }

            int calls = aspect.adviceCalls();
            int totalTargets = service.totalCalls();
            int loadTargets = service.loadCalls();

            System.out.println(
                    "COUNTS: advice=" + calls
                    + ",total=" + totalTargets
                    + ",load=" + loadTargets);

            if (!proxy
                    || total != 90
                    || !"item:ready".equals(loaded)
                    || !mapped
                    || !passedThrough
                    || calls != 4
                    || totalTargets != 1
                    || loadTargets != 3) {
                throw new IllegalStateException(
                        "Spring AOP contract verification failed");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Bài chạy trong `AnnotationConfigApplicationContext`, không cần Spring TestContext hoặc Spring Boot.

- `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật hạ tầng Spring AOP proxy và yêu cầu class-based proxy cho bean phù hợp. Target không phải `final`.
- `@Bean` factory tạo service/aspect/trace đều là Spring-managed beans; aspect cũng phải được **đăng ký làm bean**, chỉ `@Aspect` chưa đủ.
- `context.getBean(PricingService.class)` trả Spring reference; `AopUtils.isAopProxy` xác nhận proxy tồn tại.
- `total(100)` chứng minh caller nhận **90** do advice chứ không phải **100** của target.
- `load("ready")` chứng minh result bình thường không bị thay khi advice chỉ có policy map exception.
- `load("missing")` chứng minh **kiểu exception mới, message và cause gốc**.
- `load("down")` chứng minh **loại exception không được map** vẫn truyền thẳng.
- `adviceCalls=4` và các bộ đếm target cho thấy bốn invocation advice đều dẫn tới target đúng một lần: một total và ba load.
- Chỉ sau khi tất cả điều kiện đều đúng, code mới in `CHECK: PASS`. Một lỗi return type, mapping sai, bỏ `proceed()` hoặc proceed trùng có thể khiến chương trình fail.

### 4.7. Chạy Maven và stdout mong đợi

Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã trong `App.main` in ra:

~~~text
PROXY: true
TOTAL: 90
LOAD_OK: item:ready
MAPPED: CatalogException <= IllegalArgumentException
PASSTHROUGH: IllegalStateException
COUNTS: advice=4,total=1,load=3
CHECK: PASS
~~~

Maven hoặc logging framework có thể in thêm thông báo khác. Đây là **stdout mong đợi theo code**, không phải báo cáo Maven đã chạy trên máy thật. Nếu gặp lỗi khi lấy dependencies hoặc fail điều kiện, chương trình không thể in dòng `CHECK: PASS` theo nhánh thành công.

## 5. JUnit 5 integration tests: ca được đổi, ca không được đổi

Đây là phần quan trọng nhất. Test thực sự **gọi service bean qua Spring proxy** và assert cả output, exception lẫn trace. Mỗi test tạo một context riêng nên recorder/counter không bị rò rỉ giữa các test.

Tạo `src/test/java/demo/AroundContractTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AroundContractTest {
    @Test
    void transformsReturnValueAndCallsTargetOnce() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals(90, service.total(100));
            assertEquals(1, service.totalCalls());
            assertEquals(1, aspect.adviceCalls());
            assertEquals(
                    List.of("ADVICE:total:before",
                            "TARGET:total:100",
                            "ADVICE:total:after"),
                    trace.snapshot());
        }
    }

    @Test
    void successfulReturnPassesThroughUnchanged() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertEquals("item:ready", service.load("ready"));
            assertEquals(1, service.loadCalls());
            assertEquals(1, aspect.adviceCalls());
            assertEquals(
                    List.of("ADVICE:load:before",
                            "TARGET:load:ready",
                            "ADVICE:load:return"),
                    trace.snapshot());
        }
    }

    @Test
    void knownExceptionIsMappedAndCauseIsPreserved() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            CatalogException mapped = assertThrows(
                    CatalogException.class,
                    () -> service.load("missing"));

            assertEquals("catalog-missing", mapped.getMessage());
            IllegalArgumentException cause =
                    assertInstanceOf(IllegalArgumentException.class,
                            mapped.getCause());
            assertEquals("unknown:missing", cause.getMessage());
            assertEquals(1, service.loadCalls());
            assertEquals(1, aspect.adviceCalls());
            assertEquals(
                    List.of("ADVICE:load:before",
                            "TARGET:load:missing",
                            "ADVICE:load:map"),
                    trace.snapshot());
        }
    }

    @Test
    void unrelatedExceptionIsNotMapped() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            IllegalStateException original = assertThrows(
                    IllegalStateException.class,
                    () -> service.load("down"));

            assertEquals("backend-down", original.getMessage());
            assertEquals(1, service.loadCalls());
            assertEquals(1, aspect.adviceCalls());
            assertEquals(
                    List.of("ADVICE:load:before",
                            "TARGET:load:down"),
                    trace.snapshot());
        }
    }

    @Test
    void nonMatchingMethodIsNotInterceptedByTheseAdvices() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertEquals("peek-ok", service.peek());
            assertEquals(0, aspect.adviceCalls());
            assertEquals(List.of("TARGET:peek"), trace.snapshot());
        }
    }

    @Test
    void selfInvocationDoesNotApplyTheMappingAdvice() {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            PricingService service = context.getBean(PricingService.class);
            ResultMappingAspect aspect =
                    context.getBean(ResultMappingAspect.class);
            EventTrace trace = context.getBean(EventTrace.class);

            IllegalArgumentException raw = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.loadInside("missing"));

            assertEquals("unknown:missing", raw.getMessage());
            assertEquals(1, service.loadCalls());
            assertEquals(0, aspect.adviceCalls());
            assertEquals(
                    List.of("TARGET:loadInside",
                            "TARGET:load:missing"),
                    trace.snapshot());
        }
    }

    @Test
    void manualObjectOutsideSpringDoesNotGetAutoProxy() {
        EventTrace trace = new EventTrace();
        PricingService plain = new PricingService(trace);

        assertFalse(AopUtils.isAopProxy(plain));
        assertEquals(100, plain.total(100));

        IllegalArgumentException raw = assertThrows(
                IllegalArgumentException.class,
                () -> plain.load("missing"));

        assertEquals("unknown:missing", raw.getMessage());
        assertEquals(1, plain.totalCalls());
        assertEquals(1, plain.loadCalls());
        assertEquals(
                List.of("TARGET:total:100",
                        "TARGET:load:missing"),
                trace.snapshot());
    }
}
~~~

Chúng ta có **bảy test methods**. Điểm quan trọng nhất: các test không chỉ assert kết quả “có vẻ đúng”, mà còn assert **số lượt gọi target, số lượt advice và chuỗi sự kiện**:

- Test 1 chứng minh transform thành công và chỉ gọi target một lần; biến đổi không được thực hiện ở target.
- Test 2 chứng minh `@Around` **không đổi kết quả** khi không có lý do đổi.
- Test 3 chứng minh exception mapping đúng type/message/cause; không đánh mất thông tin lỗi gốc.
- Test 4 chứng minh exception khác đi qua; giúp bắt lỗi `catch(Throwable)` rồi map tất cả.
- Test 5 chứng minh `peek()` không bị các pointcut ngoài phạm vi can thiệp.
- Test 6 chứng minh **self-invocation** là giới hạn proxy-based Spring AOP, không phải lỗi parser pointcut.
- Test 7 chứng minh object thường ngoài Spring không tự được auto-proxy, dù gọi cùng method Java.

Chạy:

~~~bash
mvn clean test
~~~

Khi tất cả test pass, báo cáo của Maven Surefire sẽ có dòng tương đương:

~~~text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
~~~

File báo cáo thường nằm tại:

~~~text
target/surefire-reports/demo.AroundContractTest.txt
~~~

Nếu test fail, đọc **assertion failure và root cause**. Không sửa expected values cho khớp output sai; hãy tìm lỗi trong pointcut, bean registration, return contract, exception mapping và số lần proceed. Đây là kết quả **mong đợi**, không phải xác nhận đã chạy test thật trong môi trường hiện tại.

## 6. Vì sao phải assert đúng kiểu return và đúng cause?

### 6.1. Trả một giá trị có vẻ đúng nhưng sai declared type

Với method:

~~~java
public int total(int base) {
    return base;
}
~~~

Spring AOP `@Around` thường trả `Object`, nhưng caller vẫn gọi **method khai báo `int`**. `return "90";` từ advice là `String`, không tương thích `int`; `return null;` cũng không hợp lệ vì primitive không có `null`. Framework có thể báo `AopInvocationException`/lỗi unboxing hoặc kiểu không tương thích tùy cách proxy/chain xử lý.

Ví dụ đúng cho contract minh họa:

~~~java
@Around("execution(int demo.PricingService.total(int))")
public Object discount(ProceedingJoinPoint point) throws Throwable {
    int raw = (Integer) point.proceed();
    return raw - 10;  // Auto-box thành Integer; caller nhận int.
}
~~~

Snippet này là **phương án rút gọn** của advice trong project chính, không thêm cả hai vào cùng aspect. Nếu dùng một around advice wildcard match `String`, `boolean`, `Optional` lẫn `int`, không thể ép mọi result thành `Integer`. Phải thu hẹp pointcut hoặc xử lý đúng declared return type cho từng method.

### 6.2. Giá trị đổi đúng nhưng target không chạy

Một quanh advice:

~~~java
@Around("execution(int demo.PricingService.total(int))")
public Object misleading(ProceedingJoinPoint point) {
    return 90;
}
~~~

Có thể làm `assertEquals(90, service.total(100))` pass, nhưng **không gọi `proceed()`**. Điều này sẽ phá side effects của target nếu business method thực sự cần chạy. Test của chúng ta còn kiểm tra `service.totalCalls() == 1` và event `TARGET:total:100`, vì requirement là **chuyển đổi kết quả sau khi target chạy**, không phải short-circuit.

Short-circuit đôi khi là thiết kế hợp lệ (ví dụ cache hit) nhưng phải được tuyên bố ngay trong contract/test. Không thể suy nó từ một test chỉ so giá trị.

### 6.3. Exception mới có type đúng nhưng mất nguyên nhân

Mẫu sai:

~~~java
catch (IllegalArgumentException original) {
    throw new CatalogException("catalog-missing", null);
}
~~~

Caller thấy đúng `CatalogException` nhưng `getCause() == null`. Điều đó làm khó việc tìm lỗi từ tầng dưới. Mẫu đúng:

~~~java
catch (IllegalArgumentException original) {
    throw new CatalogException("catalog-missing", original);
}
~~~

Nếu module có API mapping error code chi tiết hơn, giữ thêm request ID/status code theo contract (và không log thông tin nhạy cảm). Không cần sao chép nguyên văn message lỗi thấp tầng cho người dùng cuối; giữ cause là khác với **phơi bày toàn bộ thông tin nội bộ**.

### 6.4. Catch quá rộng biến lỗi không liên quan thành lỗi “known”

~~~java
try {
    return point.proceed();
} catch (Throwable error) {
    throw new CatalogException("catalog-missing", error);
}
~~~

Đây là mẫu **không phù hợp** nếu policy chỉ định map một loại lỗi. Nó có thể che `IllegalStateException`, lỗi programming và cả những `Error` nghiêm trọng thành một category giả. Test `unrelatedExceptionIsNotMapped` phát hiện bug đó.

Đôi khi application cần ánh xạ nhiều loại lỗi sang một exception API chung; hãy **chỉ định rõ nhóm lỗi được phép**, bảo toàn cause, và có integration tests kiểm chứng từng nhánh. Không đưa catch rộng vào mọi service chỉ vì nó thuận tiện.

### 6.5. Biến lỗi thành normal return không nằm trong yêu cầu

~~~java
try {
    return point.proceed();
} catch (IllegalArgumentException error) {
    return "item:unknown";
}
~~~

Đoạn này không ném `CatalogException` mà trả một `String` bình thường. Đây là **fallback**, không phải **exception mapping**. Với business policy cho phép fallback, nó có thể có ý nghĩa, nhưng sẽ khiến caller không nhận được lỗi và có thể làm transaction/cache/metrics diễn giải request là thành công. Test phải phân biệt **map-to-exception** và **recover-to-value**.

Trong bài này, mapping là **throw new CatalogException(...)**, không phải trả fallback.

## 7. Phương pháp thiết kế test để bắt regression

Để không bỏ sót lỗi, xây một ma trận theo **đường đi của lời gọi** và **outcome**:

| Trục | Ca dương cần chứng minh | Ca âm cần chứng minh |
| --- | --- | --- |
| Result transform | Target trả 100, caller nhận 90 | Target phải thực sự chạy; không được đổi method khác |
| Error map | Lỗi known được chuyển đúng exception API | Lỗi không thuộc policy không bị map |
| Success passthrough | `load("ready")` trả bình thường đúng value | Không tự bọc result trong container sai type |
| Cause preservation | `CatalogException.getCause()` là lỗi gốc | Không bỏ `cause` hoặc thay bằng lỗi unrelated |
| Proxy boundary | Spring bean được proxy; advice chạy | Raw `new` object không auto-proxy |
| Self-invocation | External caller gọi `load` được map | `this.load` trong target bypass proxy |
| Invocation count | Mỗi lượt gọi target đúng một lần | Không quên `proceed()`, không retry vô hạn |
| Advice scope | `total` và `load` được advice | `peek` không bị advice |
| Error semantics | Exception ném qua caller | Không tự chuyển thrown exit thành normal return |

Bộ test không cần tạo mọi tổ hợp của cả ứng dụng. Nhưng một requirement có **hai điều kiện độc lập** (match pointcut và xử lý đúng outcome), nên có ít nhất một ca bảo đảm từng điều kiện và một ca âm riêng chống việc mở rộng không chủ ý.

Nếu nhiều `@Around`/transaction/cache advisors cùng match một method, việc quan sát **event trace** và số lần gọi target là quan trọng vì `proceed()` chuyển qua phần còn lại của advisor chain. Đối với một target được cache, cache hit có thể khiến target không chạy; trong trường hợp đó **test expected invocation count phải tuân contract cache** chứ không mặc định một lần target ở mọi lời gọi.

## 8. Mutation testing thủ công: cố ý làm sai và xem test fail

Các thay đổi sau được làm **từng cái một**, chỉ cho học tập. Sau mỗi lần thử, hoàn nguyên `ResultMappingAspect.java` rồi chạy lại `mvn clean test`.

### Mutation A: quên gọi proceed

Thay đoạn transform bằng:

~~~java
trace.add("ADVICE:total:before");
trace.add("ADVICE:total:after");
return 90;
~~~

Nếu chỉ assert `service.total(100)==90`, mutation sẽ lọt. Trong bộ test thật, `service.totalCalls()==1` và `TARGET:total:100` biến mất nên test dương fail.

### Mutation B: return sai kiểu

~~~java
return "90";
~~~

Với `int total(int)`, không tương thích contract. Lời gọi proxy có thể ném exception thay vì trả số nguyên. Kiểm thử caller-facing result bắt được, thay vì chỉ kiểm tra advice đã được gọi.

### Mutation C: map mọi exception

~~~java
catch (Throwable original) {
    throw new CatalogException("catalog-missing", original);
}
~~~

Ca `load("down")` phải nhận `IllegalStateException`; mutation biến nó thành `CatalogException`, nên `unrelatedExceptionIsNotMapped` fail.

### Mutation D: mất cause

~~~java
throw new CatalogException("catalog-missing", null);
~~~

Ca map exception vẫn bắt đúng `CatalogException` và message, nhưng assertion `assertInstanceOf(IllegalArgumentException.class, mapped.getCause())` fail. Đây là lý do không nên assert chỉ exception class.

### Mutation E: nới pointcut quá rộng

Đổi:

~~~java
@Around("execution(String demo.PricingService.load(String))")
~~~

thành một biểu thức match cả các method khác có `String` return, ví dụ:

~~~java
@Around("execution(String demo.PricingService.*(..))")
~~~

Method `peek()` vốn không thuộc chính sách sẽ có `ADVICE:load:before` và advice counter tăng, khiến `nonMatchingMethodIsNotInterceptedByTheseAdvices` fail. Nếu future method có String arguments khác nhau, pointcut còn có thể chặn ngoài dự kiến.

### Mutation F: nuốt exception và trả fallback

~~~java
catch (IllegalArgumentException original) {
    return "item:unknown";
}
~~~

Ca `knownExceptionIsMappedAndCauseIsPreserved` fail vì không nhận `CatalogException`. Contract thay đổi từ thrown exit thành normal return, khác hoàn toàn mapping yêu cầu.

Điểm chung: **một positive test không đủ**. Các negative tests giúp giữ đúng *phạm vi và loại can thiệp*, tránh vô tình thay đổi hợp đồng khi refactor pointcut/advice.

## 9. Phân biệt @AfterThrowing và @Around khi ánh xạ exception

Trong Spring AOP:

~~~java
@AfterThrowing(
    pointcut = "execution(String demo.PricingService.load(String))",
    throwing = "error",
    argNames = "error"
)
public void logError(IllegalArgumentException error) {
    System.out.println(error.getMessage());
}
~~~

`@AfterThrowing` là lựa chọn thích hợp **nếu chỉ muốn log** khi target invocation ném lỗi. Nó **không có cơ chế thay exception đang truyền qua thành exception object khác** theo cách khai báo trong mẫu này. Để chủ động catch và throw một exception mới, `@Around` là loại advice phù hợp khi requirement thật sự là mapping.

Mặt khác, `@Around` có thể bọc các advisors bên trong; lỗi bắt được từ `point.proceed()` có thể đến từ **interceptor/advice ở trong**, không nhất thiết chỉ từ target method. Cần xác định order/precedence nếu ánh xạ lỗi liên quan transaction, security hoặc cache. Đừng catch `Throwable` rộng rồi coi mọi lỗi là từ target.

Nếu cần áp dụng exception mapping thống nhất ở HTTP API boundary, Spring MVC có `@ExceptionHandler`/`@ControllerAdvice` cho presentation-level exception translation. Đó là một **cơ chế khác** với Spring AOP, không nên sử dụng chỉ để chứng minh một `@Around` có thể map lỗi. Bài này vẫn xét **method boundary của Spring bean**.

## 10. Ràng buộc về checked exception và proxy type

Ví dụ chính ánh xạ từ `IllegalArgumentException` sang **runtime `CatalogException`**. Đây là lựa chọn có chủ ý để tránh một vấn đề khác làm nhiễu bài học.

Nếu `@Around` ném một **checked exception** mà method khai báo qua JDK proxy **không cho phép trong throws declaration**, caller có thể gặp wrapper như `UndeclaredThrowableException` theo cơ chế Java proxy. Với CGLIB và các adapters khác, biểu hiện có thể khác. Vì vậy:

- Nếu API cần mapped checked exception, xem **throws declaration của interface/implementation** và proxy strategy;
- Kiểm thử trên đúng Spring context thực tế (JDK vs CGLIB), không chỉ invoke raw target;
- Đừng đổi exception class rồi mặc định caller luôn thấy đúng class đó dù method signature không công bố nó;
- Nếu mapping nhằm thống nhất business API lỗi và không muốn checked exception wrappers, một runtime exception được thiết kế rõ thường đơn giản hơn, nhưng cần thống nhất contract toàn ứng dụng.

`@Around` khai báo `throws Throwable` vì đó là API `ProceedingJoinPoint.proceed()`; điều này **không tự thay đổi throws clause** của interface method mà caller gọi. Advice signature có `throws Throwable` không đồng nghĩa target method được phép ném mọi checked exception qua proxy mà không có hệ quả.

## 11. Thứ tự advisors và giao dịch

Nếu một Spring bean còn sử dụng transaction/cache/security advisors, advice mới có thể nằm ở **bên ngoài hoặc bên trong** những advisors đó. Với Spring `@Order`, số nhỏ hơn có precedence cao hơn, nên outer advice đi vào trước và đi ra sau inner advice theo mô hình nesting.

Ví dụ:

~~~text
Caller
  |
  v
ResultMapping @Around (outer)
  | proceed()
  v
Transaction advisor (inner)
  | proceed()
  v
Target throws IllegalArgumentException
  |
  v
Transaction advisor xử lý lỗi theo rollback rules
  |
  v
ResultMapping catch -> throw CatalogException
  |
  v
Caller
~~~

Ở trường hợp khác, transaction advisor có thể nằm **bên ngoài** mapping advice; khi đó transaction logic nhìn thấy exception đã được map. Điều này có thể ảnh hưởng rollback/metrics/status policy. Không khẳng định một order cố định cho transaction advisor nếu không kiểm tra cấu hình.

Muốn test đúng tương tác:

1. Tạo context có **các advisors thực tế** của ứng dụng.
2. Khai báo order rõ nếu nghiệp vụ yêu cầu.
3. Gọi qua Spring proxy, xác nhận kết quả mapping và trạng thái transaction/rollback bằng integration test phù hợp.
4. Không giả định “cứ exception mới extends RuntimeException thì mọi transaction luôn rollback”: Spring transaction có cấu hình rollback rules riêng.
5. Tránh tự gọi `method.invoke(target,...)` trong advice vì nó có thể bypass những advisors ở phần còn lại của chain; dùng `point.proceed()`.

## 12. Các lỗi kiểm thử dễ gặp

**Lỗi 1: Test tự `new PricingService` nhưng mong aspect chạy.**  
Object tự tạo ngoài container không được auto-proxy. Test `manualObjectOutsideSpringDoesNotGetAutoProxy` là ca âm; test chính phải lấy bean từ `AnnotationConfigApplicationContext`.

**Lỗi 2: Chỉ dùng `AopUtils.isAopProxy`.**  
Proxy có thể được tạo vì advisor khác. Phải assert **marker trong advice cần test**, kết quả caller và target invocation count.

**Lỗi 3: Chỉ kiểm tra exception class mới.**  
Nếu mất cause, test vẫn có thể pass. Assert **message/error code, cause type và cause message** theo contract.

**Lỗi 4: Không test exception không được map.**  
Một catch rộng sẽ biến mọi lỗi thành lỗi known. Đối chứng `IllegalStateException` là ca âm quan trọng.

**Lỗi 5: Self-invocation bị đọc sai thành lỗi mapping.**  
`this.load("missing")` từ trong target không đi qua Spring proxy nên mapping không chạy; không chuyển `@Around` thành một advice khác để chữa self-invocation. Nếu cần policy áp dụng, chuyển method sang collaborator bean và gọi qua injected proxy.

**Lỗi 6: Quên kiểm tra số lần proceed.**  
Advice có thể gọi `proceed()` hai lần rồi chỉ trả kết quả cuối, tạo side effects trùng. Assert target counter để khóa yêu cầu exactly-once trong ví dụ.

**Lỗi 7: Test state dùng chung giữa các ca.**  
Nếu dùng static counter hoặc tái dùng context mà không reset, test ảnh hưởng nhau. Ví dụ tạo một context mới cho mỗi test và trace instance riêng.

**Lỗi 8: Logging ném exception làm outcome thay đổi.**  
Nếu việc ghi log trong catch thất bại, caller có thể nhận exception của logger thay vì exception mapping đã thiết kế. Trong production, định nghĩa error policy cho logging/metrics; test cả logging failure nếu đó là yêu cầu thực tế.

**Lỗi 9: Mapping sai loại hoặc sai contract return.**  
`@Around` trả `Object`, nhưng method `int` cần wrapper số tương thích, `String` cần String/reference hợp lệ; không tự trả `null` cho primitive hoặc trả một chuỗi lỗi thay exception khi policy không cho phép.

**Lỗi 10: Test chỉ dựa vào console output.**  
Console chứa stdout của Maven/framework và không phải interface ổn định. Dùng assertions đối với result, exception, trace và counters; console chỉ để quan sát khi học.

## 13. Các bài tập mở rộng

### 13.1. Chuyển phép transform thành có điều kiện

Thay yêu cầu từ “trừ 10 mọi lần” thành “chỉ giảm nếu result lớn hơn hoặc bằng 10”. Khi đó, `discount` có thể kiểm tra result sau `proceed()`, nhưng phải xác định contract với số âm, overflow và bất kỳ transformation nào không còn hợp lệ về nghiệp vụ.

Viết test dương cho `total(100)` → `90` và ca âm `total(5)` → `5`. Đừng sửa target method nếu điều cần kiểm tra là **result transformation bởi aspect**.

### 13.2. Ánh xạ một exception type chuyên biệt

Thay vì dùng `IllegalArgumentException`, tạo `ItemNotFoundException` riêng ở tầng nội bộ và chỉ `catch(ItemNotFoundException original)`. Đây là contract có ý nghĩa hơn trong production vì không vô tình map các `IllegalArgumentException` phát sinh từ bug lập trình.

Viết test rằng `IllegalArgumentException` không mang “item not found” vẫn propagate, còn lỗi chuyên biệt được map. Giữ cause.

### 13.3. Kiểm thử trả fallback có chủ đích

Nếu requirement thay đổi từ **throw mapped exception** sang **trả fallback `"item:unknown"`**, hãy viết một `@Around` mới với contract rõ ràng và test khẳng định:
- Method đích được gọi hay không;
- Caller nhận đúng String fallback;
- Exception không còn truyền tới caller **vì chính sách mới**, không phải do bị nuốt tình cờ;
- Các exception ngoài policy vẫn propagate.

Đừng đổi một dòng catch rồi để tên test `knownExceptionIsMappedAndCauseIsPreserved` trở nên sai nội dung.

### 13.4. Thêm nhiều advisors vào cùng join point

Tạo một `@Before` audit aspect và một `@Around` mapping aspect cùng khớp `load`, đặt `@Order` tường minh và ghi trace hai chiều. Thử hai cách ordering. Quan sát exception nào đi qua từng advisor và khẳng định code không vô tình map lỗi do **audit advice** ném nếu requirement chỉ muốn lỗi từ business target.

Đây là bài tập Spring AOP advice-chain, không cần AspectJ weaving.

## 14. Checklist QA trước khi đưa @Around lên production

- [ ] Đã có requirement cụ thể cho **result transformation** hoặc **exception mapping**; không dùng `@Around` chỉ để log.
- [ ] Target và aspect là Spring-managed beans; caller dùng proxy.
- [ ] Pointcut đủ hẹp cho từng loại result/exception; không bắt nhầm mọi method.
- [ ] `@Around` trả `Object`, khai báo `throws Throwable` khi gọi `proceed()`.
- [ ] Normal return sau transformation tương thích kiểu khai báo, nullability, primitive/generics.
- [ ] Một invocation gọi `proceed()` đúng số lần đã thiết kế; không skip/retry tình cờ.
- [ ] Không có catch rộng làm thay lỗi không liên quan thành lỗi known.
- [ ] Mapping giữ type/message/error code/cause theo contract; không lộ lỗi nội bộ ngoài ý muốn.
- [ ] Known exception bị map; unrelated exception được truyền nguyên; successful result không bị sửa khi không cần.
- [ ] Integration test assert **caller-facing output**, target counter, advice counter và trace.
- [ ] Có test negative cho method không match, raw object và self-invocation nếu chúng là rủi ro thực tế.
- [ ] Đã đánh giá tác động đối với transaction/cache/security advisors và thứ tự trong chain.
- [ ] Logging/metrics trong advice không vô ý ném exception làm phá hợp đồng.
- [ ] Nếu API dùng checked exceptions, đã kiểm tra interface throws declaration và loại proxy.
- [ ] Không dùng `Method.invoke(target)` để thay `ProceedingJoinPoint.proceed()` nếu cần giữ chain Spring AOP.

## 15. Tài liệu chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Around`, `proceed()`, handling return/exception và quyền thay giá trị trả về.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy, self-invocation và ranh giới lời gọi.
- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): giới hạn method execution và thiết kế pointcut expression.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): đăng ký Spring aspect bean và auto-proxy.
- [Spring Framework 6.2 — Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html#advice-ordering): precedence khi nhiều advices cùng match.
- [JUnit 5.11.0 — User Guide](https://junit.org/junit5/docs/5.11.0/user-guide/): JUnit Jupiter `@Test`, `assertThrows`, `assertInstanceOf` và test lifecycle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="return-exception-handling-009">Phân biệt phương thức đã trả Future/Publisher và công việc đã hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phương thức đã trả Future/Publisher và công việc đã hoàn tất trong Spring Aspect

## 1. Vấn đề: `@AfterReturning` chạy rồi nhưng tác vụ đã hoàn tất chưa?

Trong **Spring AOP**, `@AfterReturning` chạy khi **lời gọi Java method được Spring proxy chặn đã trả về bình thường**. Nếu kiểu trả về là `Future<T>`, `CompletableFuture<T>`, `CompletionStage<T>`, `Mono<T>`, `Flux<T>` hoặc một `Publisher<T>`, method hoàn toàn có thể **trả ra một handle/mô tả công việc** trước khi kết quả bất đồng bộ hoặc reactive stream hoàn tất.

Ví dụ:

```java
@AfterReturning(
    pointcut = "execution(* com.example.jobs.JobService.start(..))",
    returning = "result",
    argNames = "result"
)
public void afterReturn(Object result) {
    System.out.println("Method đã trả về: " + result.getClass().getName());
}
```

Đây là **fragment trong một Spring `@Aspect` bean**, không phải chương trình Java độc lập. Dòng log này nói rằng lời gọi **`start(..)` qua proxy đã có normal return**. Nó **không tự chứng minh** rằng:

- `CompletableFuture` đã hoàn thành.
- Future đã thành công thay vì hoàn thành exceptionally hoặc bị cancel.
- `Mono`/`Flux` đã được subscribe.
- Publisher đã phát `onNext`, `onComplete` hoặc `onError`.
- Dữ liệu đã được gửi hết tới HTTP client.
- Transaction, remote call, message processing hoặc toàn bộ workflow bên ngoài đã hoàn tất.

Đây là một khác biệt nền tảng giữa **method-execution lifecycle** của Spring Aspect và **asynchronous/reactive processing lifecycle** của giá trị trả về.

**Phạm vi:** Bài tập trung vào `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around`, `ProceedingJoinPoint` và thứ tự quan sát trong **Spring proxy-based AOP**. Những kiến thức Future/Reactive Streams được dùng để giải thích Spring Aspect, không biến bài thành hướng dẫn chung về concurrency hoặc Reactor.

## 2. Ba mốc khác nhau cần đặt tên chính xác

### 2.1. Method invocation kết thúc

Lời gọi qua Spring proxy:

```text
Caller
  -> Spring AOP proxy
     -> advice / target.method()
     <- target trả về một Future hoặc Publisher
     -> @AfterReturning có thể chạy
  <- caller nhận Future hoặc Publisher
```

Điều kiện `@AfterReturning` là **method trả về bình thường**. Nó không chờ một giá trị `T` nằm trong `Future<T>` hoặc Publisher xuất hiện sau này.

### 2.2. Future/CompletionStage hoàn thành

```text
method startFuture()
  |
  +--> tạo CompletableFuture<T> còn pending
  +--> return future                <-- Spring @AfterReturning ở đây
                                      caller đã có handle
...
executor / event / callback xử lý
  |
  +--> complete(value)
      hoặc completeExceptionally(error)
      hoặc cancel()
                                      <-- Future chuyển sang completed state
```

Một `Future` có thể đã complete trước lúc method return, có thể complete rất lâu sau hoặc không bao giờ complete. **Không giả định tất cả Futures luôn pending tại return**; ví dụ `CompletableFuture.completedFuture(value)` trả về object **đã hoàn thành**. Nhưng `@AfterReturning` vẫn **chỉ gắn với normal return của method**, không phải listener theo dõi trạng thái future.

### 2.3. Publisher được subscribe và phát terminal signal

Với một **cold Reactor `Mono`** thông thường:

```text
method makeMono()
  |
  +--> tạo Mono mô tả pipeline
  +--> return Mono                  <-- Spring @AfterReturning ở đây
                                     (có thể chưa xử lý dữ liệu)
...
consumer subscribe()
  |
  +--> onSubscribe / request
  +--> onNext(value) nếu có dữ liệu
  +--> onComplete() hoặc onError(error)
                                     <-- Terminal signal khi có
```

Với `Flux` có thể có **nhiều `onNext`**, terminal signal có thể đến rất lâu sau. Với **hot publisher**, công việc hoặc emission có thể xảy ra **trước khi subscriber mới đăng ký**; không được mô tả mọi Publisher là cold. Một subscription cũng có thể bị **cancel**, không phát terminal success/error cho subscriber theo cách thông thường.

**Bảng mốc tóm tắt:**

| Khái niệm | Mốc mà nó thực sự nói tới | Có đồng nghĩa hoàn tất công việc không? |
| --- | --- | --- |
| `@AfterReturning` | Spring-advised Java method trả bình thường | **Không**, nếu result là Future/Publisher chưa xử lý xong. |
| `@AfterThrowing` | Advised method **thoát ra bằng exception** | **Không tự bắt** lỗi được phát sau đó qua Future hoặc `onError`. |
| `@After` | Advised method thoát bình thường hoặc lỗi | **Không** phải callback terminal của Future/Publisher. |
| `future.isDone()` | Future completed/cancelled state | Có thể là thành công, lỗi hoặc cancellation; **không đồng nghĩa success**. |
| `future.join()` / `get()` | Chờ/đọc kết quả Future | Có thể block; exception wrapping cần xử lý. |
| `CompletionStage.whenComplete` | Callback khi stage hoàn thành | Quan sát thành công **hoặc** lỗi của stage. |
| Reactor `doOnSuccess` | Thành công của một `Mono` subscription | Có thể success rỗng; nhận `null` khi `Mono` complete empty. |
| Reactor `doOnError` | Publisher phát error signal | Là lỗi reactive, không nhất thiết Java method throw. |
| Reactor `doFinally` | Sau termination/cancellation theo semantics Reactor | Có thể thấy `ON_COMPLETE`, `ON_ERROR`, `CANCEL`. |

## 3. `@AfterReturning` bind giá trị nào?

Tài liệu Spring cho phép dùng `returning` để nhận object mà target invocation đã trả:

```java
@AfterReturning(
    pointcut = "execution(* com.example.jobs.JobService.startFuture(..))",
    returning = "future",
    argNames = "future"
)
public void returned(java.util.concurrent.CompletableFuture<?> future) {
    System.out.println("done=" + future.isDone());
}
```

Nếu `startFuture()` trả một `CompletableFuture` chưa hoàn tất, advice in `done=false` rồi **kết thúc**. Khi future complete về sau, **không có lần gọi `@AfterReturning` thứ hai chỉ vì completion**.

Điều tương tự với:

```java
@AfterReturning(
    pointcut = "execution(* com.example.jobs.JobService.makePublisher(..))",
    returning = "publisher",
    argNames = "publisher"
)
public void returned(reactor.core.publisher.Mono<?> publisher) {
    System.out.println("Đã tạo/trả một Mono");
}
```

Advice nhận **đối tượng `Mono`**, không tự nhận phần tử `T` mà Mono có thể emit sau khi subscribe.

**Hai đoạn trên là fragments, không phải project độc lập.** Type của formal parameter trong advice còn **hạn chế matching của return value**: `CompletableFuture<?>` sẽ không phù hợp khi method thực tế trả một loại value khác. Đối với `void`/null/primitive hoặc nhiều subtype, kiểm thử binding thực tế trong Spring context.

### Gán lại parameter advice không thay Future/Publisher caller nhận

```java
@AfterReturning(
    pointcut = "execution(* com.example.jobs.JobService.startFuture(..))",
    returning = "future",
    argNames = "future"
)
public void incorrect(CompletableFuture<String> future) {
    future = CompletableFuture.completedFuture("replacement");
}
```

Đây là **anti-pattern minh họa**. `future = ...` chỉ gán lại biến local của advice; Spring `@AfterReturning` không có hợp đồng thay **reference** trả cho caller. Nếu phải đổi result được caller nhận, sử dụng `@Around` return stage/publisher phù hợp hoặc thiết kế transformation ở service/adapter.

## 4. `@AfterThrowing` không tự quan sát lỗi xuất hiện sau return

Cần phân biệt bốn trường hợp:

| Kịch bản | Lời gọi Java method | Trạng thái công việc về sau | `@AfterThrowing` nhắm method đó? |
| --- | --- | --- | --- |
| Method `throw new IllegalStateException(...)` trước return | Thoát bằng exception | Không trả handle | **Có**, nếu pointcut/type match. |
| Method trả `CompletableFuture` đang pending, sau đó complete exceptionally | **Normal return** | Future lỗi về sau | **Không chỉ vì** future lỗi. |
| Method trả `Mono.error(ex)` bình thường | **Normal return** | Subscription phát `onError` | **Không chỉ vì** `onError`. |
| Method ném lỗi ngay lúc xây dựng Publisher trước `return` | Thoát bằng exception | Publisher chưa được trả | **Có**, nếu advisor và loại exception match. |

Ví dụ:

```java
public CompletableFuture<String> start() {
    CompletableFuture<String> result = new CompletableFuture<>();
    // Một thread hoặc callback khác sẽ gọi:
    // result.completeExceptionally(new IllegalStateException("late"));
    return result;
}
```

Đây là fragment. Target method không throw khi trả future; Spring AOP nhìn thấy **normal return**. Khi future chuyển sang exceptional state về sau, caller gặp lỗi tại `join()` hoặc `get()` (hoặc khi callback chạy), không phải một **method-execution exception** tự động “quay ngược” vào `@AfterThrowing` của `start()`.

Tương tự:

```java
public Mono<String> fetch() {
    return Mono.error(new IllegalStateException("stream-error"));
}
```

Method `fetch()` hoàn toàn có thể trả `Mono` bình thường. Reactive error được phát **khi publisher được subscribe**, không phải Java exception của method `fetch()` trong trường hợp này.

**Lưu ý:** nếu một advice `@Around` hoặc hạ tầng Spring khác ở bên trong biến lỗi thành **synchronous throw** trước khi returning ra ngoài, việc advice nào quan sát gì còn phụ thuộc advisor chain. Các kết luận trên nhắm đúng ranh giới method execution của **Spring AOP thông thường**, không phủ nhận những điều chỉnh do các interceptors khác.

## 5. Khác biệt với `@Async` của Spring

Trong Spring, `@Async` dùng task execution infrastructure và thường được proxy-based. Với một service:

```java
@Async
public CompletableFuture<String> compute() {
    return CompletableFuture.completedFuture("done");
}
```

đây là **fragment**; cần `@EnableAsync` và executor phù hợp. Có hai lớp cần nghĩ tới:

1. **Method/service invocation** bị hạ tầng `@Async` chuyển sang executor.
2. **Future** caller nhận đại diện cho kết quả của công việc được thực thi.

Khi cùng method có custom `@Aspect`, thứ tự giữa **async interceptor** và **custom advisor** ảnh hưởng advice chạy ở thread nào và nhìn thấy **wrapper Future nào**. Nếu custom advice nằm ngoài async interceptor, `@AfterReturning` có thể quan sát Future returned cho caller trước khi worker thực thi xong. Nếu advice nằm bên trong đoạn invocation được executor thực thi, nó có thể quan sát một return value khác ở một thời điểm khác.

**Không suy từ annotation source order** rằng `@AfterReturning` của custom aspect luôn chạy trong executor thread hay luôn chạy sau khi `@Async` task hoàn thành. Muốn hiểu chính xác, phải kiểm tra ordering/proxy chain và test trên configuration thật.

Đối với `@Async void`, caller không nhận Future để `get()`; Spring có `AsyncUncaughtExceptionHandler` cho uncaught async exceptions. `@AfterThrowing` trên một custom aspect không phải phương tiện thay thế tự động cho cấu hình exception-handling của `@Async`.

### Một phân biệt thường bị bỏ quên

`CompletableFuture.supplyAsync(...)` trong target method và Spring `@Async` **không phải cùng một kỹ thuật**. Cả hai có thể làm việc bất đồng bộ, nhưng executor, proxy chain, bean boundary và error routing khác nhau. Bài demo ở phần 9 cố ý **không dùng `@Async` hoặc thread race**: nó tạo pending Future, trả ngay và hoàn thành về sau bằng một method được gọi từ main để stdout có thể kiểm tra chính xác.

## 6. Dùng Future callbacks để quan sát completion, không chặn trong advice

Với `CompletableFuture<T>` hoặc `CompletionStage<T>`, khi mục tiêu là theo dõi **kết quả cuối**, có thể đăng ký completion callback.

Ví dụ trong Spring `@Around`:

```java
@Around("execution(* com.example.jobs.JobService.start(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    CompletionStage<?> stage = (CompletionStage<?>) pjp.proceed();

    return stage.whenComplete((value, failure) -> {
        if (failure == null) {
            metrics.incrementSuccess();
        } else {
            metrics.incrementFailure();
        }
    });
}
```

Đây là **fragment** cần method target trả `CompletionStage`, `metrics` tồn tại và import `CompletionStage`. `@Around` trả về stage sau khi attach callback; callback có thể chạy ở thread **hoàn thành stage**, không nhất thiết cùng thread với method advice.

Các lựa chọn khác:

- `whenComplete`: quan sát success/error, về nguyên tắc giữ result/exception gốc trừ khi callback tự throw.
- `handle`: có thể **biến đổi** success hoặc failure thành giá trị khác.
- `thenApply`: transform **success value**.
- `exceptionally`: xử lý một số exceptional completions.
- `whenCompleteAsync`, `thenApplyAsync`: lên lịch callback bằng executor có thể khác; tránh giả định ThreadLocal/MDC/SecurityContext tự được truyền.

Đừng viết:

```java
@AfterReturning(
    pointcut = "execution(* com.example.jobs.JobService.start(..))",
    returning = "future"
)
public void waitForResult(CompletableFuture<?> future) {
    future.join(); // Có thể block proxy caller thread.
}
```

Đây là **anti-pattern**. `join()` có thể block vô hạn nếu Future không complete, làm mất tính bất đồng bộ, tăng latency và có thể gây deadlock/starvation nếu completion cần resource đang bị caller thread giữ. Nếu chỉ cần metric, đăng ký callback non-blocking phù hợp hơn.

### `Future` thường và `CompletionStage` không tương đương

Interface `java.util.concurrent.Future` chỉ có các operations như `isDone()`, `get()`, `cancel()`. Nó **không có callback API `whenComplete`**.

Nếu một method trả `Future<T>` thuần, muốn quan sát hoàn thành có thể cần API hỗ trợ từ thư viện, một adapter thích hợp hoặc tổ chức completion ở nơi tạo Future. Không nên buộc một aspect generic phải polling liên tục hay gọi `get()` trong request thread chỉ để lấy metric.

### Exception wrapping

- `CompletableFuture.join()` thường ném unchecked `CompletionException` bao quanh cause khi future completed exceptionally.
- `Future.get()` dùng checked `ExecutionException` khi task ném lỗi.
- `CancellationException` là trạng thái đặc biệt, không nên tính như một thành công.
- Callback có thể nhận `Throwable` đại diện lỗi hoặc một wrapper; chính sách log/metric nên normalize cause phù hợp mà không nuốt thông tin.

`future.isDone()==true` chỉ nói future **đã hoàn tất ở một trạng thái**; cần phân biệt normal, exceptional, cancelled nếu nghiệp vụ yêu cầu.

## 7. Reactive `Mono`/`Flux`: method return, subscription, signal và cancellation

### 7.1. Cold publisher

Một ví dụ Reactor:

```java
public Mono<String> load() {
    return Mono.defer(() -> {
        System.out.println("Đang thực hiện công việc");
        return Mono.just("value");
    });
}
```

Đây là method fragment trong một bean. Khi `load()` được gọi:

- Java method tạo `Mono` và **return**.
- Spring `@AfterReturning` có thể chạy ngay sau normal return.
- `Mono.defer` supplier **chỉ được gọi khi có subscription**.
- Nếu không có ai subscribe, supplier không chạy.
- Nếu có **hai subscriptions** vào cold publisher, supplier có thể chạy **hai lần** dù target `load()` chỉ được gọi **một lần**.

Vì vậy hãy phân biệt các counters:

```text
methodInvocations = 1
afterReturningInvocations = 1
subscriptions = 2
workExecutions = 2   (trong ví dụ Mono.defer cold)
```

### 7.2. Hot publisher và nguồn đã bắt đầu xử lý

Không được phát biểu tuyệt đối “Publisher luôn chưa chạy gì trước khi subscribe”. Với hot sources, dữ liệu có thể được phát trước khi subscriber đến. `Mono.just(value)` có thể capture value eagerly lúc assembly; `Mono.fromFuture(future)` có thể wrap một Future đang chạy; kết quả đã được tính toán trước khi `Mono` được trả ra.

Vì thế `@AfterReturning` chỉ cho biết **Java method return bình thường**. Nó không tự nói publisher cold/hot, đang chạy/chưa chạy, còn active/đã complete.

### 7.3. Terminal signals

Theo Reactive Streams, subscriber thường thấy:

```text
onSubscribe
request(...)
onNext(value)   (0..N lần tùy Publisher)
onComplete()    (kết thúc bình thường)
```

hoặc:

```text
onSubscribe
request(...)
onError(error)  (kết thúc bằng lỗi)
```

`Mono<T>` có thể thành công **không có phần tử** (`Mono.empty()`), trong khi `Flux<T>` có thể phát nhiều phần tử trước terminal. **Không dùng riêng `doOnNext` để tính mọi successful completions**, vì empty Publisher không phát `onNext`.

Cancellation cũng là một kết quả quản lý subscription cần quan sát khi dùng timeout/client disconnect; nó không đồng nhất với `onComplete` hoặc `onError`.

### 7.4. `@AfterThrowing` không phải `doOnError`

`@AfterThrowing` là Spring AOP advice về **Java method exception**. `Mono.doOnError`, `Flux.doOnError` chạy trong **reactive signal pipeline** khi subscriber nhận error.

Một `@AfterThrowing` không tự lắng nghe `onError` của tất cả `Mono/Flux` target trả về. Muốn metric hoàn tất theo Publisher lifecycle, dùng reactive hooks/operators trong service hoặc dùng `@Around` **trả về Publisher đã được decorate**, và đảm bảo không thay đổi behavior ngoài ý muốn.

## 8. `@Around` có thể decorate Publisher nhưng phải trả về nó

Ví dụ:

```java
@Around("execution(* com.example.jobs.JobService.stream(..))")
public Object observePublisher(ProceedingJoinPoint pjp) throws Throwable {
    Mono<?> source = (Mono<?>) pjp.proceed();

    // Pipeline được decorate, callbacks chỉ chạy theo reactive lifecycle.
    return source
        .doOnSubscribe(subscription -> metrics.onSubscribe())
        .doOnSuccess(value -> metrics.onSuccess())
        .doOnError(error -> metrics.onError(error))
        .doFinally(signal -> metrics.onFinally(signal));
}
```

Đây là **fragment** dành cho method trả `Mono`, không phải generic code áp dụng vô điều kiện cho `Flux` hoặc bất cứ kiểu `Publisher` nào.

Điểm quan trọng:

1. `pjp.proceed()` chạy **Java method body**, trả `Mono`.
2. `doOn...` tạo ra **publisher chain được decorate**; muốn caller sử dụng chain đó, `@Around` phải **return publisher mới**.
3. Callbacks được gắn vào **subscription lifecycle**, không phải return lifecycle của Java method.
4. `doOnSuccess` là hook của `Mono`; với `Flux` dùng `doOnComplete` để quan sát terminal success và `doOnNext` cho từng phần tử.
5. `doFinally` có signal type như `ON_COMPLETE`, `ON_ERROR`, `CANCEL`; nó phù hợp cho cleanup/metrics theo subscription, nhưng timing của callback so với một số downstream operations cần được test.
6. Nhiều subscriptions vào cold publisher có thể khiến hooks chạy nhiều lần trong **một** method invocation.
7. Nếu `Mono` đã được chia sẻ/cache/hot, semantics có thể khác. Không assume một subscription luôn bằng một upstream job mới.

**Đừng làm như sau trong `@Around`:**

```java
Mono<?> source = (Mono<?>) pjp.proceed();
source.subscribe();  // Tự kích hoạt một subscription phụ.
return source;        // Caller có thể subscribe lần nữa.
```

Đây là **anti-pattern minh họa**. `subscribe()` thủ công bên trong aspect có thể kích hoạt công việc, HTTP call, DB access hoặc side effect **trước khi caller chủ động subscribe**, rồi caller lại kích hoạt một lần nữa. Nó phá ownership của subscription, làm khó error/cancellation propagation và có thể gây duplicate processing.

Nếu cần transform value, dùng `map`/`flatMap` đúng hợp đồng; nếu cần quan sát terminal, dùng phù hợp `doOnSuccess`/`doOnComplete`/`doOnError`/`doFinally`. Không tự block bằng `block()` trong aspect chỉ để “đợi hoàn tất”, đặc biệt trên thread non-blocking của WebFlux.

## 9. Ví dụ Java 17+ chạy được: method return trước Future completion và Mono subscription

Ví dụ dưới đây được thiết kế **để có stdout xác định**. Nó không dùng scheduler races:

- Method `startFuture()` tạo **pending CompletableFuture** và trả về ngay. Caller hoàn thành future sau đó bằng một method khác.
- Một future thứ hai được hoàn thành **exceptionally** sau khi đã return; `@AfterThrowing` không chạy cho lỗi muộn đó.
- Method `failImmediately()` **throw trước return**, nên `@AfterThrowing` chạy.
- Method `coldPublisher()` trả `Mono.defer`, công việc chỉ chạy khi subscribe; hai subscriptions vào cùng Mono làm công việc chạy hai lần.
- Hai methods `observedPublisher()` và `errorPublisher()` được `@Around` decorate để đếm **reactive success/error signals**, không dựa vào `@AfterReturning`.

Project sử dụng **Java 17+, Spring Framework 6.1.14, AspectJ Weaver 1.9.22.1, Reactor Core 3.6.11 và Maven**. Đây là bộ version minh họa cố định, không tuyên bố mới nhất. Chỉ dùng **Spring proxy-based AOP**, không cấu hình AspectJ weaving agent.

### 9.1. Cấu trúc thư mục

```text
spring-aop-completion-boundary/
├── pom.xml
└── src/main/java/com/example/completion/
    ├── JobService.java
    ├── CompletionAspect.java
    ├── AppConfig.java
    └── DemoApp.java
```

### 9.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-completion-boundary</artifactId>
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
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
            <version>3.6.11</version>
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

`spring-context` cung cấp `ApplicationContext` và kéo những Spring AOP dependencies cần thiết qua dependency tree; `aspectjweaver` dùng cho Spring `@AspectJ` pointcut parser. `reactor-core` cung cấp `Mono` và signal operators. Không cần web server để test sự phân biệt giữa method return và publisher lifecycle.

### 9.3. `src/main/java/com/example/completion/JobService.java`

```java
package com.example.completion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

public class JobService {
    private CompletableFuture<String> pending;
    private final AtomicInteger coldSubscriptions = new AtomicInteger();

    public CompletableFuture<String> startFuture() {
        pending = new CompletableFuture<>();
        System.out.println("[TARGET] returned pending Future");
        return pending;
    }

    public void completeFuture(String value) {
        pending.complete(value);
    }

    public void failFuture() {
        pending.completeExceptionally(
                new IllegalArgumentException("late-future"));
    }

    public CompletableFuture<String> failImmediately() {
        System.out.println("[TARGET] throwing before return");
        throw new IllegalStateException("early");
    }

    public Mono<String> coldPublisher() {
        System.out.println("[TARGET] returned cold Publisher");
        return Mono.defer(() -> {
            coldSubscriptions.incrementAndGet();
            System.out.println("[WORK] cold subscription");
            return Mono.just("M-OK");
        });
    }

    public Mono<String> observedPublisher() {
        System.out.println("[TARGET] returned observed Publisher");
        return Mono.defer(() -> Mono.just("OBS-OK"));
    }

    public Mono<String> errorPublisher() {
        System.out.println("[TARGET] returned error Publisher");
        return Mono.error(new IllegalArgumentException("late-mono"));
    }

    public int coldSubscriptions() {
        return coldSubscriptions.get();
    }
}
```

**Giải thích design:** `pending` chỉ là một field hỗ trợ demo chạy tuần tự, không phải cách tổ chức job state production. Trong ứng dụng nhiều requests, dùng per-request job IDs và state ownership phù hợp, không lưu một `pending` có thể bị ghi đè trong singleton service.

`completeFuture` và `failFuture` được caller gọi sau khi `startFuture` đã return. Chúng **không được các pointcuts trong demo chọn**, nên không tạo thêm advice events vào output.

`coldPublisher` tạo `Mono.defer`: phần `[WORK]` chỉ in khi có subscription. `observedPublisher` và `errorPublisher` có return type `Mono<String>` để `@Around` có thể attach reactive operators.

### 9.4. `src/main/java/com/example/completion/CompletionAspect.java`

```java
package com.example.completion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import reactor.core.publisher.Mono;

@Aspect
public class CompletionAspect {
    private final AtomicInteger futureReturns = new AtomicInteger();
    private final AtomicInteger syncThrows = new AtomicInteger();
    private final AtomicInteger publisherReturns = new AtomicInteger();
    private final AtomicInteger monoSuccesses = new AtomicInteger();
    private final AtomicInteger monoErrors = new AtomicInteger();

    @AfterReturning(
        pointcut = "execution(* com.example.completion.JobService.startFuture(..))",
        returning = "future",
        argNames = "future"
    )
    public void afterFutureReturned(CompletableFuture<?> future) {
        futureReturns.incrementAndGet();
        System.out.println("[AOP after-return] Future done=" + future.isDone());
    }

    @AfterThrowing(
        pointcut = "execution(* com.example.completion.JobService.startFuture(..))"
                 + " || execution(* com.example.completion.JobService.failImmediately(..))",
        throwing = "error",
        argNames = "error"
    )
    public void afterSynchronousThrow(Throwable error) {
        syncThrows.incrementAndGet();
        System.out.println("[AOP after-throw] "
                + error.getClass().getSimpleName());
    }

    @AfterReturning(
        pointcut = "execution(* com.example.completion.JobService.coldPublisher(..))",
        returning = "publisher",
        argNames = "publisher"
    )
    public void afterPublisherReturned(Mono<?> publisher) {
        publisherReturns.incrementAndGet();
        System.out.println("[AOP after-return] Publisher returned");
    }

    @Around(
        "execution(* com.example.completion.JobService.observedPublisher(..))"
        + " || execution(* com.example.completion.JobService.errorPublisher(..))"
    )
    public Object monitorPublisher(ProceedingJoinPoint pjp) throws Throwable {
        Mono<?> source = (Mono<?>) pjp.proceed();
        System.out.println("[AOP around] returned Publisher: "
                + pjp.getSignature().getName());

        return source
                .doOnSuccess(value -> monoSuccesses.incrementAndGet())
                .doOnError(error -> monoErrors.incrementAndGet());
    }

    public int futureReturns() {
        return futureReturns.get();
    }

    public int syncThrows() {
        return syncThrows.get();
    }

    public int publisherReturns() {
        return publisherReturns.get();
    }

    public int monoSuccesses() {
        return monoSuccesses.get();
    }

    public int monoErrors() {
        return monoErrors.get();
    }
}
```

**Ý nghĩa từng advice:**

- `afterFutureReturned` nhận **Future reference** khi `startFuture` normal return. `isDone=false` là trạng thái cố định trong demo vì caller chưa gọi `completeFuture`.
- `afterSynchronousThrow` chỉ quan sát **Java exception từ method execution** tương ứng. Future sau này complete exceptionally **không quay lại gọi** advice này.
- `afterPublisherReturned` đếm **một lần trả Mono**, không đếm subscriptions.
- `monitorPublisher` dùng `@Around` để **return một Mono đã decorate**, nên `monoSuccesses`/`monoErrors` tăng khi subscriber nhận signal thích hợp.
- Các pointcuts trong ví dụ không chồng chéo giữa `@Around` và `@AfterReturning` trên cùng method; vì thế stdout không bị phụ thuộc vào ordering giữa nhiều advice trên một join point.

### 9.5. `src/main/java/com/example/completion/AppConfig.java`

```java
package com.example.completion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public JobService jobService() {
        return new JobService();
    }

    @Bean
    public CompletionAspect completionAspect() {
        return new CompletionAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật Spring AOP auto-proxy infrastructure. `proxyTargetClass=true` chọn CGLIB class proxy cho `JobService` không implement business interface. `CompletionAspect` là Spring bean do `@Bean`; `@Aspect` đơn lẻ chưa tự đăng ký bean.

### 9.6. `src/main/java/com/example/completion/DemoApp.java`

```java
package com.example.completion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Mono;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            JobService jobs = context.getBean(JobService.class);
            CompletionAspect aspect = context.getBean(CompletionAspect.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(jobs));

            CompletableFuture<String> success = jobs.startFuture();
            System.out.println("futureBeforeComplete=" + success.isDone());
            jobs.completeFuture("F-OK");
            System.out.println("futureValue=" + success.join());

            CompletableFuture<String> failure = jobs.startFuture();
            jobs.failFuture();
            try {
                failure.join();
            } catch (CompletionException error) {
                System.out.println("futureFailure="
                        + error.getCause().getClass().getSimpleName());
            }

            try {
                jobs.failImmediately();
            } catch (IllegalStateException error) {
                System.out.println("syncFailure=" + error.getMessage());
            }
            System.out.println("futureReturns=" + aspect.futureReturns());
            System.out.println("syncThrows=" + aspect.syncThrows());

            Mono<String> cold = jobs.coldPublisher();
            System.out.println("coldBeforeSubscribe="
                    + jobs.coldSubscriptions());

            System.out.println("coldValue1=" + cold.block());
            System.out.println("coldValue2=" + cold.block());
            System.out.println("coldAfterSubscribe="
                    + jobs.coldSubscriptions());
            System.out.println("publisherReturns=" + aspect.publisherReturns());

            Mono<String> monitored = jobs.observedPublisher();
            System.out.println("monoSuccessBefore=" + aspect.monoSuccesses());
            System.out.println("monitoredValue=" + monitored.block());
            System.out.println("monoSuccessAfter=" + aspect.monoSuccesses());

            try {
                jobs.errorPublisher().block();
            } catch (IllegalArgumentException error) {
                System.out.println("monoError=" + error.getMessage());
            }

            System.out.println("monoSignalErrors=" + aspect.monoErrors());
            System.out.println("syncThrowsFinal=" + aspect.syncThrows());
        }
    }
}
```

Demo không có background threads trong `JobService` và các `Mono` dùng sync cold sources, nên thứ tự các `System.out.println` do code tạo ra **có thể được đối chiếu trực tiếp**. Trong production, thread scheduling và Reactor schedulers có thể khiến timestamp/log interleave; đó là một vấn đề khác.

### 9.7. Chạy Maven và stdout dự kiến

Với JDK 17+ và Maven tải được dependencies, trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.completion.DemoApp
```

Các dòng của chương trình (bỏ qua thông báo Maven/SLF4J bổ sung):

```text
isAopProxy=true
[TARGET] returned pending Future
[AOP after-return] Future done=false
futureBeforeComplete=false
futureValue=F-OK
[TARGET] returned pending Future
[AOP after-return] Future done=false
futureFailure=IllegalArgumentException
[TARGET] throwing before return
[AOP after-throw] IllegalStateException
syncFailure=early
futureReturns=2
syncThrows=1
[TARGET] returned cold Publisher
[AOP after-return] Publisher returned
coldBeforeSubscribe=0
[WORK] cold subscription
coldValue1=M-OK
[WORK] cold subscription
coldValue2=M-OK
coldAfterSubscribe=2
publisherReturns=1
[TARGET] returned observed Publisher
[AOP around] returned Publisher: observedPublisher
monoSuccessBefore=0
monitoredValue=OBS-OK
monoSuccessAfter=1
[TARGET] returned error Publisher
[AOP around] returned Publisher: errorPublisher
monoError=late-mono
monoSignalErrors=1
syncThrowsFinal=1
```

### 9.8. Đọc đúng các kết quả

1. `startFuture()` **đã return hai lần** nên `futureReturns=2`, và `done=false` tại cả hai `@AfterReturning`. Điều này không nói futures sẽ thành công hay lỗi.
2. Future thứ nhất được caller hoàn thành **sau khi** method và `@AfterReturning` đã chạy, rồi `join()` trả `F-OK`.
3. Future thứ hai hoàn thành exceptionally sau return. `join()` ném `CompletionException` với cause `IllegalArgumentException`, nhưng **`@AfterThrowing` không chạy** cho event completion muộn ấy.
4. `failImmediately()` **ném lỗi trước khi trả Future**, nên `@AfterThrowing` chạy đúng một lần: `syncThrows=1`.
5. `coldPublisher()` chỉ được **gọi một lần**, nên `publisherReturns=1`. Nhưng `cold.block()` hai lần tạo hai **subscriptions**; `[WORK]` xuất hiện hai lần và `coldAfterSubscribe=2`.
6. `monitorPublisher` thực thi khi `observedPublisher()` return, nhưng `monoSuccessBefore=0`. Chỉ khi `monitored.block()` subscribe và Mono success mới thấy `monoSuccessAfter=1`.
7. `errorPublisher()` return bình thường và `@Around` đã trả decorated Publisher. Error xuất hiện khi `block()` subscribe, tăng `monoSignalErrors=1`, **không tăng** `syncThrowsFinal` vì không có synchronous Java throw từ `JobService.errorPublisher()`.
8. Toàn bộ lời gọi target đi qua Spring bean proxy, không phải `new JobService()` bên ngoài context.

Đây là demo cố ý tách **method return**, **Future completion** và **reactive subscription/signal**. Output dự kiến được tính theo source code đã trình bày; Java/Maven cần được chạy trong môi trường thực nếu muốn kiểm tra build artifacts hoặc tương thích dependency mirror.

## 10. Đo duration trong Spring Aspect: bạn đang đo khoảng nào?

Một `@Around` truyền thống:

```java
@Around("execution(* com.example.jobs.JobService.*(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        metrics.record(elapsed);
    }
}
```

Đây là **fragment**. Với service trả `Future` hoặc Publisher ngay, `elapsed` là **thời gian Java method invocation/assembly**, **không tự đo** thời gian đến completion của Future hoặc terminal signal của Publisher.

Muốn phân biệt đúng, đặt tên metric:

| Metric | Start | End | Đo điều gì? |
| --- | --- | --- | --- |
| `method.return.latency` | Vào Spring advice | Java method return/throw | Invocation/Publisher assembly. |
| `future.completion.latency` | Bắt đầu tác vụ/đăng ký callback | Completion/cancel của Future | Công việc được Future đại diện, theo định nghĩa start. |
| `publisher.subscription.duration` | Subscription bắt đầu | Terminal/cancel signal | Duration của **một subscription**, không nhất thiết lifetime object publisher. |
| `request.duration` | HTTP request bắt đầu | HTTP response/cancellation hoàn tất | Cần HTTP/web instrumentation, không suy từ service `@AfterReturning`. |

Một `Mono` có thể được subscribe nhiều lần. Nếu dùng **một timestamp** tính lúc method assembly rồi đo tới nhiều subscription completions, metrics có thể bao gồm **thời gian chờ trước subscription** và tạo ra kết quả khó diễn giải. Để đo subscriber lifecycle, lấy start time **lúc subscribe** với `Mono.defer`, `doOnSubscribe` hoặc instrumentation có context per-subscription thích hợp.

### ThreadLocal/MDC và Reactor Context

Trong Spring AOP, advice ban đầu chạy trên thread gọi Java method. Completion callbacks có thể chạy trên **thread khác**. Reactor signals có thể đi qua các schedulers khác nhau.

- Không giả định `ThreadLocal`, MDC, transaction context hoặc `SecurityContextHolder` lúc advice thực thi tự xuất hiện ở future callback.
- Reactor có `Context` dành cho dữ liệu gắn với subscription; dùng những cơ chế context propagation tích hợp thích hợp với Spring/Reactor version.
- Tốt nhất gắn correlation ID/trace context theo cơ chế framework đã hỗ trợ, không tự lưu `ProceedingJoinPoint` hoặc request-specific state trong field của singleton aspect.
- Nếu cần ghi log nhạy cảm, không đưa toàn bộ Future result/Publisher elements vào log một cách mặc định.

## 11. Spring WebFlux: hoàn tất method không có nghĩa response đã gửi xong

Giả sử một Spring WebFlux controller hoặc service method trả:

```java
public Mono<ResponseDto> get(String id) {
    return service.fetch(id).map(ResponseDto::from);
}
```

Đây là fragment cần `ResponseDto`, `service` tồn tại. `@AfterReturning` nhắm method `get` hoặc `fetch` có thể thấy **Mono reference** khi Java method return. Nhưng:

1. Dispatcher/HTTP handling vẫn có thể chưa subscribe pipeline tại thời điểm đó.
2. `fetch` có thể được thực thi về sau.
3. `Mono` có thể complete empty/error/cancel.
4. HTTP server còn phải encode/write response; reactive terminal signal **không tự đồng nhất** với “client đã nhận mọi byte”.
5. Một controller/service method có `@Transactional` hoặc các Spring transaction facilities có thể dùng behavior chuyên biệt cho reactive transactions; không suy mọi transaction commit xảy ra ngay lúc Java method return.

Nếu cần observability cho **HTTP request lifecycle**, sử dụng WebFlux filters/observation/tracing facilities ở tầng web; nếu cần observability cho **publisher subscription**, sử dụng Reactor operators hoặc Spring-compatible reactive instrumentation. Spring service AOP `@AfterReturning` đo **method return**, không có nghĩa nó thay thế các facility này.

## 12. Những lỗi suy luận thường gặp

| Triệu chứng / kết luận sai | Điều thực sự đang xảy ra | Hướng kiểm tra |
| --- | --- | --- |
| “`@AfterReturning` đã chạy, vậy async job thành công.” | Method trả Future bình thường, job có thể còn pending hoặc fail về sau. | Đăng ký `whenComplete`/đọc terminal outcome. |
| “`@AfterThrowing` không chạy, vậy CompletableFuture không lỗi.” | Future có thể complete exceptionally **sau** method return. | `whenComplete`, `join()`/`get()`, exception cause. |
| “`@AfterReturning` đã chạy, vậy Mono pipeline đã được subscribe.” | Method có thể chỉ trả cold Publisher chưa có subscriber. | Dùng `doOnSubscribe` và observability trên pipeline. |
| “`Mono.error(ex)` khiến Spring `@AfterThrowing` chạy.” | Method trả Mono bình thường; `onError` là reactive signal về sau. | Dùng `doOnError` hoặc terminal signal subscriber. |
| “Mỗi method invocation tương đương một `Flux` execution.” | Một cold Publisher có thể được subscribe nhiều lần; hot publishers có semantics khác. | Đếm riêng method calls và subscriptions. |
| “Future `isDone=true` nghĩa là success.” | Future có thể exceptional hoặc cancelled. | Kiểm tra completed state/outcome có phân loại. |
| “Gọi `join()` trong `@AfterReturning` để đo completion là vô hại.” | Có thể block caller, phá async và gây deadlock. | Attach callback non-blocking hoặc instrument nơi tạo task. |
| “`@Around` đã gắn `doOnError`, không cần return Mono đã decorate.” | Reactor operators trả publisher mới; bỏ wrapper thì caller không dùng hooks. | `return source.doOnError(...)`. |
| “Subscribe trong aspect chỉ để log.” | Có thể khởi động một subscription phụ và duplicate side effects. | Tránh tự subscribe; return decorated Publisher. |
| “`doOnNext` đủ để đếm mọi Mono success.” | `Mono.empty()` success không phát `onNext`. | `doOnSuccess` hoặc terminal hooks thích hợp. |
| “`doFinally` lúc nào cũng là success.” | Có `CANCEL`, `ON_ERROR`, `ON_COMPLETE`. | Phân loại `SignalType`. |
| “`@Async` và custom AOP advice luôn chạy cùng thread.” | Phụ thuộc proxy/advisor ordering và executor. | Kiểm tra `@EnableAsync`, `@Order`, advisor chain, test thực. |
| “`@AfterReturning` đo HTTP response duration.” | Nó đo Java method return, không phải network write lifecycle. | Instrument WebFlux/HTTP request boundary. |
| “Future callback tự kế thừa MDC/transaction context.” | Completion có thể ở thread khác. | Cấu hình context propagation phù hợp. |
| “`isAopProxy=true` chứng minh advice quan sát terminal signal.” | Proxy chỉ chứng minh đã có hạ tầng interception phù hợp, không tự đăng ký Future/Reactive callbacks. | Assert counters sau completion/subscription thật. |

## 13. Checklist để review một Spring Aspect xử lý Future/Publisher

- [ ] Định nghĩa rõ cần quan sát **method return** hay **actual async/reactive completion**.
- [ ] `@AfterReturning` chỉ được dùng để kết luận **normal method return**, không suy tác vụ đã thành công.
- [ ] `@AfterThrowing` chỉ áp dụng cho **method exception path**, không dùng thay `onError`/future exceptional completion.
- [ ] Với `Future` thuần, không giả định có `whenComplete`; xác định type API thực tế.
- [ ] Với `CompletionStage`, callbacks được attach đúng, không biến đổi result/exception ngoài chủ đích.
- [ ] Không block `join()/get()` trong advice trên đường gọi latency-sensitive.
- [ ] Với Publisher, phân biệt cold/hot, assembly/subscription/emission/terminal/cancellation.
- [ ] Không gọi `subscribe()` trong aspect chỉ để quan sát kết quả.
- [ ] Nếu dùng `@Around` decorate Publisher, **trả về publisher mới** có operators cần thiết.
- [ ] Đo per-subscription metrics khi Publisher có thể nhiều subscribers.
- [ ] Dùng `doOnSuccess`/`doOnComplete`/`doOnError`/`doFinally` đúng loại Publisher và ý nghĩa.
- [ ] Nếu dùng `@Async`, đã xác minh advisor ordering, executor thread và exception policy.
- [ ] Đã đánh giá ThreadLocal/MDC/transaction/security context propagation tới callbacks.
- [ ] Biết proxy boundary: Spring bean, `@EnableAspectJAutoProxy`, không self-invocation hoặc `new` target.
- [ ] Có integration tests cho pending→success, pending→error, synchronous throw, empty/error Publisher, cancellation và multiple subscriptions khi áp dụng.
- [ ] Phân biệt method duration với job/subscription/request duration trong tên metrics.

### Tài liệu tham khảo chính thức

- Spring Framework — **Declaring Advice**, `@AfterReturning`/`@AfterThrowing`/`@Around`: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **Task Execution and Scheduling**, `@Async`/`Future`/`CompletableFuture`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — **Proxying Mechanisms** và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Reactor Core — **Hot Versus Cold**: https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html
- Reactor Core — **Introduction to Reactive Programming** và subscription lifecycle: https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html
- Project Reactor — `Mono`/ `Flux` Javadoc: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html

**Kết luận:** Khi Spring Aspect thấy một method **trả về `Future` hoặc `Publisher`**, điều chắc chắn duy nhất theo `@AfterReturning` là **Java method đã normal-return một object handle/publisher**. Công việc mà object đó đại diện có **lifecycle khác**, gồm Future completion hoặc Publisher subscription, signals và cancellation. Muốn audit/metrics cho **completion thực tế**, hãy gắn `CompletionStage` callbacks hoặc trả publisher được decorate bằng Reactor operators (qua `@Around` nếu cần), thay vì suy hoàn tất công việc từ việc `@AfterReturning` đã chạy.


</details>

- [Quay lại đầu trang](#back-to-top)
