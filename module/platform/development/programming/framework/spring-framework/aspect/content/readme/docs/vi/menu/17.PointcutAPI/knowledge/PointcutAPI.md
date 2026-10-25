<a id="back-to-top"></a>

# Pointcut API

## Menu
- [Pointcut, ClassFilter và MethodMatcher trong Spring AOP](#pointcut-api-001)
- [Lọc ở cấp lớp và ở cấp phương thức](#pointcut-api-002)
- [MethodMatcher tĩnh và kiểm tra động theo đối số](#pointcut-api-003)
- [StaticMethodMatcherPointcut và trường hợp áp dụng](#pointcut-api-004)
- [NameMatchMethodPointcut và khớp theo tên phương thức](#pointcut-api-005)
- [AspectJExpressionPointcut để dùng biểu thức qua API](#pointcut-api-006)
- [ComposablePointcut và hợp, giao các điều kiện](#pointcut-api-007)
- [Pointcut.TRUE và việc áp dụng rộng có chủ đích](#pointcut-api-008)
- [Kiểm thử pointcut độc lập trước khi gắn vào advisor](#pointcut-api-009)
- [Chi phí khớp động so với khớp có thể tính trước](#pointcut-api-010)

## <a id="pointcut-api-001">Pointcut, ClassFilter và MethodMatcher trong Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut, ClassFilter và MethodMatcher trong Spring AOP

## 1. Vấn đề: Spring quyết định advice áp dụng cho lời gọi nào?

Một interceptor Spring AOP không nên được chạy cho mọi phương thức trong ứng dụng. Ta thường chỉ muốn đo thời gian các phương thức nghiệp vụ, kiểm tra một nhóm service hoặc ghi nhận thao tác có điều kiện. **Pointcut** là hợp đồng lựa chọn những *method execution* mà một advice được phép tham gia khi lời gọi đi qua **Spring AOP proxy**.

Spring cung cấp hai con đường quen thuộc:

- Khai báo pointcut bằng biểu thức AspectJ trong `@Aspect`/XML, thường phù hợp với các quy tắc tên method, annotation hay package.
- Sử dụng trực tiếp **Spring Pointcut API** khi cần cấu thành điều kiện bằng Java, tái sử dụng một bộ lọc hoặc quyết định có điều kiện theo đối số thực tế.

Bài này tập trung vào con đường thứ hai: ba giao diện trong Spring và cách nối chúng với một Spring advisor/proxy.

```text
Caller -> Spring proxy -> Advisor
                              |
                              v
                          Pointcut
                           /    \
                 ClassFilter    MethodMatcher
                 lớp phù hợp?    method phù hợp?
                       \          /
                        \        /
                           yes
                            |
                         advice
                            |
                       target method
```

Nếu `ClassFilter` từ chối class hoặc `MethodMatcher` từ chối method, advice tương ứng sẽ không tham gia lời gọi đó. Target method vẫn có thể chạy bình thường qua proxy nếu không có advice khác từ chối.

## 2. Ba giao diện cốt lõi của Spring

### 2.1. `Pointcut`: kết hợp bộ lọc lớp và bộ khớp phương thức

API `org.springframework.aop.Pointcut`:

```java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
```

Spring dùng **cả hai** để xác định advisor có áp dụng được cho một method trên một target class hay không. `Pointcut.TRUE` là pointcut luôn khớp, hữu ích trong tình huống cần advice toàn bộ, nhưng có thể quá rộng đối với nghiệp vụ.

Pointcut **chỉ mô tả điều kiện chọn**. Nó không chứa mã thực thi trước/sau target. Trong Spring AOP, `DefaultPointcutAdvisor` ghép một pointcut với một `Advice` để tạo một quy tắc interception.

### 2.2. `ClassFilter`: chọn nhóm target class

API `org.springframework.aop.ClassFilter`:

```java
public interface ClassFilter {
    boolean matches(Class<?> clazz);
}
```

Trong ví dụ service:

```java
ClassFilter orderClasses =
        clazz -> OrderService.class.isAssignableFrom(clazz);
```

Điều kiện này khớp `OrderService` và các lớp con phù hợp. Nếu chỉ dùng `clazz == OrderService.class`, bộ lọc sẽ loại subclass và có thể gây bất ngờ khi mở rộng ứng dụng. Ngược lại, bộ lọc `clazz.getSimpleName().endsWith("Service")` có thể rộng ngoài ý định.

`ClassFilter.TRUE` cho phép mọi class; hãy chọn nó chỉ khi bộ lọc method hoặc pointcut khác thực sự đủ hẹp.

**Chú ý:** đối số `clazz` là thông tin về **target class** mà Spring dùng khi khớp, không phải tên proxy được tạo ra để caller gọi. Một class filter phù hợp không bảo đảm mọi method trong class đều bị chặn: `MethodMatcher` vẫn phải khớp.

### 2.3. `MethodMatcher`: chọn method tĩnh và tùy chọn theo đối số

API `org.springframework.aop.MethodMatcher`:

```java
public interface MethodMatcher {
    boolean matches(Method method, Class<?> targetClass);
    boolean isRuntime();
    boolean matches(Method method, Class<?> targetClass, Object... args);
}
```

Hai giai đoạn khớp có ý nghĩa khác nhau:

1. `matches(Method, Class<?>)` là **kiểm tra tĩnh** dựa trên method và target class; có thể được Spring tối ưu/lưu kết quả theo đặc điểm proxy.
2. Nếu kết quả tĩnh là `true` **và** `isRuntime() == true`, Spring tiếp tục gọi `matches(Method, Class<?>, Object... args)` **cho từng lần gọi**, trước advice, để kiểm tra các đối số cụ thể.

Với `isRuntime() == false`, phép khớp 3 tham số không được dùng để quyết định advice ở runtime. Với `isRuntime() == true`, điều kiện tĩnh vẫn là **cửa đầu tiên**; trả `false` ở giai đoạn này có thể khiến phần kiểm tra đối số không bao giờ chạy.

| Loại điều kiện | Ví dụ | Khớp tại thời điểm nào? | Chi phí |
| --- | --- | --- | --- |
| `ClassFilter` | Chỉ `OrderService` | Khi Spring xác định khả năng áp dụng advisor cho target class | Thường ít lần |
| `MethodMatcher` tĩnh | Tên method là `submit` và có hai tham số | Có thể được kiểm tra/cache trong chuỗi proxy | Thấp hơn động |
| `MethodMatcher` động | `quantity >= 10` tại mỗi lần `submit` | Với từng lời gọi có method đã khớp tĩnh | Cao hơn; phải đọc args mỗi lần |

Một điều kiện như `quantity >= 10` **không thể khớp chỉ bằng `Method` reflection**: `Method` mô tả chữ ký, không chứa giá trị `quantity` của từng lần gọi.

## 3. Thứ tự quyết định advice trong Spring proxy

Xét một advisor chứa một pointcut động:

```text
1. Caller gọi proxy.submit("A-1", 12).
2. Spring xem target class có qua ClassFilter không?
   └─ Không -> advisor không áp dụng.
3. Method submit(String,int) có qua matches(Method,Class) không?
   └─ Không -> advisor không áp dụng.
4. Nếu isRuntime() là true, kiểm tra matches(Method,Class,args)
   └─ False -> bỏ advice cho lần gọi này.
5. Nếu mọi điều kiện đúng, chạy advice.
6. Nếu advice gọi proceed(), chuỗi tiếp tục đến target.
```

**Điểm đặc thù Spring AOP:** cùng một method có thể lúc chạy advice, lúc không, nếu dùng dynamic matcher và các lần gọi có đối số khác nhau. Nhưng Spring AOP vẫn là cơ chế dựa trên **proxy**: self-invocation `this.submit(...)` thường không đi qua proxy, dù các điều kiện match đều đúng. `final`/`private` có giới hạn riêng của proxy; pointcut không tự sửa được giới hạn này.

Một advisor có thể chưa được đưa vào chuỗi proxy nếu Spring đánh giá toàn bộ class/method không phù hợp; vì vậy không nên dựa vào hiệu ứng phụ của `matches` để thực hiện logic nghiệp vụ.

## 4. Chọn lớp hỗ trợ thay vì tự viết lại toàn bộ giao diện

Spring có các lớp tiện ích:

| API | Khi nào nên dùng? | Chi tiết |
| --- | --- | --- |
| `StaticMethodMatcherPointcut` | Điều kiện chỉ phụ thuộc class/method | Override `matches(Method,Class)`; `isRuntime() == false` |
| `DynamicMethodMatcherPointcut` | Phải đọc đối số từng lần gọi | Override phép kiểm tra động, đồng thời thu hẹp kiểm tra tĩnh nếu biết trước |
| `NameMatchMethodPointcut` | Chọn method qua tên/pattern đơn giản | Không cần tự viết matcher |
| `JdkRegexpMethodPointcut` | Chọn method bằng regex | Kiểm tra pattern và phạm vi trước khi áp dụng rộng |
| `AspectJExpressionPointcut` | Muốn dùng cú pháp pointcut AspectJ trong Spring | Thích hợp cho nhiều quy tắc thường gặp |
| `ComposablePointcut` / `Pointcuts` | Cần hợp/ giao các pointcut | Có thể kết hợp bộ lọc mà không tạo lớp mới |

`StaticMethodMatcherPointcut` đã triển khai `Pointcut` và `MethodMatcher`; mặc định `ClassFilter.TRUE`, có thể thay bằng `setClassFilter(...)`.

`DynamicMethodMatcherPointcut` cũng triển khai `Pointcut` và `MethodMatcher`; lớp này mặc định cho phép mọi class qua `getClassFilter()` nên có thể override để thu hẹp. Khác với static matcher, cơ chế dynamic phải kiểm tra đối số ở thời điểm method thực sự được gọi.

Nên ưu tiên **pointcut tĩnh** nếu điều kiện chọn có thể xác định từ class/method. Dynamic matcher phù hợp khi giá trị đối số quyết định *có chạy advice hay không*, nhưng nó làm phát sinh một phép kiểm tra thêm trên mỗi lời gọi phù hợp.

## 5. Ví dụ hoàn chỉnh Java 17+: static và dynamic matcher trên cùng Spring proxy

Ví dụ minh họa:

- Advisor thứ nhất chỉ áp dụng cho method `OrderService.submit(String, int)`. Nó dùng `StaticMethodMatcherPointcut` và `ClassFilter`.
- Advisor thứ hai chỉ chạy khi cũng là method `submit` **và** `quantity >= 10`. Nó dùng `DynamicMethodMatcherPointcut`.
- `OrderService.status()` không khớp cả hai method matcher nên không sinh log advice.
- `OtherService.submit(...)` có method cùng tên nhưng bị class filter từ chối.

Ta dùng `ProxyFactory` để quan sát trực tiếp Spring AOP API. `ProxyFactory` thuộc Spring Framework, tạo một Spring proxy từ target và advisor; ví dụ không dùng AspectJ weaving hay annotation `@Aspect`.

Cấu trúc:

```text
spring-pointcut-api/
├── pom.xml
└── src/
    ├── main/java/example/PointcutDemo.java
    └── test/java/example/PointcutDemoTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>spring-pointcut-api</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.8</version>
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

`src/main/java/example/PointcutDemo.java`:

```java
package example;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class PointcutDemo {
    public static class OrderService {
        private final List<String> events;

        public OrderService(List<String> events) {
            this.events = events;
        }

        public String submit(String sku, int quantity) {
            events.add("target:submit:" + quantity);
            return "OK:" + sku + ":" + quantity;
        }

        public String status() {
            events.add("target:status");
            return "READY";
        }
    }

    public static class OtherService {
        private final List<String> events;

        public OtherService(List<String> events) {
            this.events = events;
        }

        public String submit(String sku, int quantity) {
            events.add("other:submit");
            return "OTHER:" + sku + ":" + quantity;
        }
    }

    // Chỉ chọn OrderService (và subtype) + submit(String,int).
    public static class SubmitPointcut extends StaticMethodMatcherPointcut {
        public SubmitPointcut() {
            setClassFilter(OrderService.class::isAssignableFrom);
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("submit")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == int.class;
        }
    }

    // Tĩnh: chọn đúng method. Động: chỉ chạy advice khi quantity >= 10.
    public static class BulkPointcut extends DynamicMethodMatcherPointcut {
        @Override
        public ClassFilter getClassFilter() {
            return OrderService.class::isAssignableFrom;
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("submit")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == int.class;
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass,
                               Object... args) {
            return args.length == 2
                    && args[1] instanceof Integer quantity
                    && quantity >= 10;
        }
    }

    public record Fixture(OrderService service, List<String> events) {}

    public static Fixture createFixture() {
        List<String> events = new ArrayList<>();
        ProxyFactory factory = new ProxyFactory(new OrderService(events));
        factory.setProxyTargetClass(true);

        MethodInterceptor trace = invocation -> {
            events.add("static:before");
            Object result = invocation.proceed();
            events.add("static:after");
            return result;
        };

        MethodInterceptor bulk = invocation -> {
            events.add("bulk:before");
            Object result = invocation.proceed();
            events.add("bulk:after");
            return result;
        };

        // Advisor đầu tiên là outer; advisor thứ hai là inner.
        factory.addAdvisor(new DefaultPointcutAdvisor(
                new SubmitPointcut(), trace));
        factory.addAdvisor(new DefaultPointcutAdvisor(
                new BulkPointcut(), bulk));

        OrderService proxy = (OrderService) factory.getProxy();
        return new Fixture(proxy, events);
    }

    public static void main(String[] args) {
        Fixture fixture = createFixture();
        OrderService service = fixture.service();

        System.out.println(service.submit("A-1", 2));
        System.out.println(service.submit("B-2", 12));
        System.out.println(service.status());
        System.out.println(fixture.events());

        // Cùng tên method nhưng khác target class -> bị ClassFilter loại.
        List<String> otherEvents = new ArrayList<>();
        ProxyFactory otherFactory =
                new ProxyFactory(new OtherService(otherEvents));
        otherFactory.setProxyTargetClass(true);
        otherFactory.addAdvisor(new DefaultPointcutAdvisor(
                new SubmitPointcut(),
                (MethodInterceptor) invocation -> {
                    otherEvents.add("SHOULD_NOT_RUN");
                    return invocation.proceed();
                }));
        OtherService other = (OtherService) otherFactory.getProxy();
        System.out.println(other.submit("C-3", 99));
        System.out.println(otherEvents);
    }
}
```

Giải thích code:

1. `SubmitPointcut` chọn đúng class `OrderService` và method `submit(String,int)`. Spring không cần đọc args để quyết định advisor này.
2. `BulkPointcut` dùng cùng điều kiện tĩnh, sau đó chỉ kích hoạt advice nếu tham số thứ hai là `Integer` có giá trị ít nhất `10`. Java boxing biến đối số primitive `int` thành `Integer` trong mảng `Object... args`.
3. `ProxyFactory.addAdvisor(...)` ghép điều kiện khớp với `MethodInterceptor`. Với advisor thứ nhất ở ngoài, sự kiện trace bọc quanh bulk khi cả hai phù hợp.
4. `invocation.proceed()` chuyển sang interceptor kế tiếp/target; nếu bỏ nó, target method có thể không chạy. `ClassFilter` không có nhiệm vụ thay thế `proceed()`.
5. Các event được lưu vào `List` chỉ để làm minh chứng. Ví dụ chạy tuần tự; nếu dùng đa luồng, cần cấu trúc thread-safe và chính sách đồng bộ thích hợp.

Chạy bằng Java 17 và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.PointcutDemo
```

Output logic mong đợi (không tính log Maven/Spring phụ thuộc môi trường):

```text
OK:A-1:2
OK:B-2:12
READY
[static:before, target:submit:2, static:after, static:before, bulk:before, target:submit:12, bulk:after, static:after, target:status]
OTHER:C-3:99
[other:submit]
```

Quan sát quan trọng:

- `submit(...,2)` khớp static matcher nhưng dynamic matcher trả `false`, do đó chỉ có `static:*`.
- `submit(...,12)` khớp cả hai advisor, thấy `static:before` → `bulk:before` → target → `bulk:after` → `static:after`.
- `status()` chỉ thêm `target:status`.
- `OtherService.submit` không có `SHOULD_NOT_RUN` vì `ClassFilter` loại target class đó. Việc tạo proxy bằng `ProxyFactory` cho target không bảo đảm một advisor nào cũng được chạy.

## 6. Kiểm thử riêng ClassFilter, MethodMatcher và đường đi proxy

Trong hệ thống thực, test tốt nên chứng minh cả **điều kiện thuần** lẫn **hành vi qua Spring proxy**. Test chỉ gọi `matches(...)` có thể bỏ qua lỗi wiring advisor; ngược lại, test chỉ nhìn log có thể bỏ sót trường hợp động không được kiểm tra.

`src/test/java/example/PointcutDemoTest.java`:

```java
package example;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

import static org.junit.jupiter.api.Assertions.*;

class PointcutDemoTest {
    @Test
    void classAndMethodMatcherHaveExpectedContract() throws Exception {
        var staticPc = new PointcutDemo.SubmitPointcut();
        var dynamicPc = new PointcutDemo.BulkPointcut();

        Method submit = PointcutDemo.OrderService.class
                .getMethod("submit", String.class, int.class);
        Method status = PointcutDemo.OrderService.class
                .getMethod("status");

        assertTrue(staticPc.getClassFilter()
                .matches(PointcutDemo.OrderService.class));
        assertFalse(staticPc.getClassFilter()
                .matches(PointcutDemo.OtherService.class));
        assertTrue(staticPc.getMethodMatcher()
                .matches(submit, PointcutDemo.OrderService.class));
        assertFalse(staticPc.getMethodMatcher()
                .matches(status, PointcutDemo.OrderService.class));
        assertFalse(staticPc.getMethodMatcher().isRuntime());

        assertTrue(dynamicPc.getMethodMatcher().isRuntime());
        assertTrue(dynamicPc.getMethodMatcher()
                .matches(submit, PointcutDemo.OrderService.class));
        assertTrue(dynamicPc.getMethodMatcher()
                .matches(submit, PointcutDemo.OrderService.class, "A", 10));
        assertFalse(dynamicPc.getMethodMatcher()
                .matches(submit, PointcutDemo.OrderService.class, "A", 2));
    }

    @Test
    void proxyOnlyAppliesAdvisorsWhenFiltersMatch() {
        var fixture = PointcutDemo.createFixture();
        var service = fixture.service();

        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(service instanceof Advised);
        assertEquals("OK:A:2", service.submit("A", 2));
        assertEquals("OK:B:12", service.submit("B", 12));
        assertEquals("READY", service.status());

        assertEquals(List.of(
                "static:before",
                "target:submit:2",
                "static:after",
                "static:before",
                "bulk:before",
                "target:submit:12",
                "bulk:after",
                "static:after",
                "target:status"), fixture.events());
    }
}
```

Chạy:

```bash
mvn -q test
```

Hai test xác minh những hợp đồng khác nhau. Test thứ nhất trực tiếp kiểm tra `ClassFilter`, chữ ký method và `isRuntime()`, kể cả cùng method nhưng args khác nhau. Test thứ hai tạo Spring proxy và kiểm tra sự kiện thực tế; việc method `status` không sinh advice bảo vệ khỏi match quá rộng. Khi đổi tên method hay thay class, một test tương tự sẽ giúp bắt lỗi phạm vi.

**Lưu ý về thiết kế test:** `Advised` giúp kiểm tra proxy có hỗ trợ tra cứu advisor; test không nên ràng buộc vào chi tiết tên class sinh ra (ví dụ chuỗi `$$SpringCGLIB$$`) vì không phải API ổn định.

## 7. Có bắt buộc tự triển khai `Pointcut` không?

Không. Nếu có sẵn `ClassFilter` và `MethodMatcher`, có thể ghép bằng `ComposablePointcut`:

```java
// Đoạn minh họa áp dụng trong chương trình có hai điều kiện phù hợp.
ClassFilter filter = OrderService.class::isAssignableFrom;
MethodMatcher matcher = new SubmitPointcut().getMethodMatcher();

Pointcut result = new ComposablePointcut(filter, matcher);
```

Các import liên quan:

```java
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
```

Lưu ý `SubmitPointcut` là lớp ví dụ mục 5, nên khi dùng đoạn minh họa trong package khác cần gọi đúng tên `PointcutDemo.SubmitPointcut` hoặc import lớp lồng thích hợp.

`ComposablePointcut` cung cấp `intersection(...)` và `union(...)` để kết hợp các điều kiện. Có khác biệt quan trọng:

- **Giao (intersection):** advice áp dụng khi các điều kiện đều đúng. Chẳng hạn service class phù hợp **và** method có annotation nhất định.
- **Hợp (union):** advice áp dụng khi ít nhất một nhánh pointcut đúng. Chẳng hạn thao tác `submit` **hoặc** `cancel` cần cùng một audit advice.

Khi tổ hợp **hai Pointcut hoàn chỉnh** bằng union, Spring giữ liên hệ giữa từng nhánh `ClassFilter` và `MethodMatcher` của nhánh đó; không tùy ý ghép class filter của nhánh này với method matcher của nhánh kia. Điều này giúp tránh match thừa mà một phép union ngây thơ có thể gây ra.

Có thể tái sử dụng các lớp `ClassFilters`, `MethodMatchers` và `Pointcuts` thuộc `org.springframework.aop.support` để gộp điều kiện. Tuy nhiên với các điều kiện tên method, annotation và package phổ biến, `AspectJExpressionPointcut` hoặc `@Pointcut` thường đọc ngắn hơn; đừng tạo một hệ thống matcher phức tạp nếu không có nhu cầu rõ ràng.

## 8. Kết nối Pointcut với Advisor và Spring Bean

Sơ đồ liên hệ:

```text
Pointcut                          Advice
(ClassFilter + MethodMatcher)     (MethodInterceptor ...)
        |                                  |
        +-------------+--------------------+
                      |
             DefaultPointcutAdvisor
                      |
               Spring AOP proxy
                      |
                    Target
```

Trong ví dụ ở mục 5, `ProxyFactory` xây một proxy **bằng API** và gắn các advisor trực tiếp. Trong một ứng dụng Spring sử dụng IoC, advisor có thể được đăng ký thành bean và được một auto-proxy creator phù hợp phát hiện, hoặc cấu hình qua `@Aspect`/XML. Đừng nhầm việc đăng ký riêng một bean kiểu `Pointcut` với việc advisor đã hoạt động: **Pointcut chưa gắn với Advice và cơ chế proxy sẽ không tự chạy**.

Nếu chỉ muốn áp dụng cho một bean khi tạo bằng Java code, `ProxyFactory` trực tiếp là ví dụ đơn giản nhất. Nếu nhiều bean cần chính sách nhất quán, dùng cấu hình advisor/auto-proxy phù hợp hoặc pointcut expression trong `@Aspect`.

### Phân biệt `Pointcut` API và `@Pointcut` annotation

| API | Bản chất | Kết nối thông thường |
| --- | --- | --- |
| `org.springframework.aop.Pointcut` | Đối tượng Java cung cấp `ClassFilter` và `MethodMatcher` | `DefaultPointcutAdvisor` hoặc `ProxyFactory` |
| `org.aspectj.lang.annotation.Pointcut` (viết `@Pointcut`) | Annotation đánh dấu phương thức khai báo biểu thức trong `@Aspect` | Advice `@Before`, `@Around`,... |
| `aop:pointcut` trong XML | Phần tử schema khai báo biểu thức pointcut có tên | `pointcut-ref` trên advisor/aspect advice |

Cả ba có liên hệ với việc **chọn** join point, nhưng không hoán đổi trực tiếp mã Java annotation cho một instance `org.springframework.aop.Pointcut`. Điểm chung là chúng chỉ có hiệu lực trên đường đi chặn được của Spring AOP.

## 9. Khi nào chọn static hay dynamic matcher?

**Nên dùng static matcher** khi:

- Chọn bằng method name/signature, class, package, annotation mà giá trị không phụ thuộc lần gọi.
- Cần chi phí runtime thấp và hành vi dễ đoán.
- Muốn bảo vệ phạm vi interceptor chặt chẽ và ít logic có trạng thái.

**Chỉ dùng dynamic matcher khi thực sự cần** xem args hoặc trạng thái chỉ có tại thời điểm gọi, chẳng hạn ghi metrics cho các đơn hàng có số lượng từ 10 trở lên. Mỗi invocation phải kiểm tra điều kiện động, vì vậy đừng đặt logic I/O, query DB hoặc xử lý nặng trong `matches(...,args)`.

Dynamic matcher quyết định **advice có tham gia** chứ không biến pointcut thành cơ chế validation đủ tin cậy. Trong ví dụ, đơn hàng số lượng 2 vẫn được gửi đến target bình thường; chỉ có bulk advice bị bỏ qua. Nếu quy tắc là “số lượng phải dương”, validation phải nằm tại tầng nghiệp vụ hoặc một interceptor được thiết kế rõ trách nhiệm và có xử lý lỗi tương ứng.

**Không cache kết quả dynamic theo Method** vì cùng một Method có thể gọi với các args khác nhau; ví dụ `submit("A", 2)` và `submit("A", 12)` cần kết quả advice khác nhau.

## 10. Giới hạn và bẫy thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Advice không chạy dù matcher trực tiếp trả true | Lời gọi bỏ qua Spring proxy, chưa gắn advisor | Lấy bean/proxy thật, kiểm tra `AopUtils.isAopProxy`, gọi qua nó |
| Method cùng tên bị chặn ở class khác | Dùng `ClassFilter.TRUE` và matcher theo tên quá rộng | Thu hẹp `ClassFilter`; test lớp khác nhưng method cùng tên |
| Advice động luôn chạy hoặc không chạy | Quên giai đoạn tĩnh, sai `isRuntime`, kiểm tra args sai vị trí | Test trực tiếp cả overload `matches` và event khi gọi proxy |
| `matches(Method,Class)` trả false nhưng hy vọng dynamic cứu | Spring lọc tĩnh trước khi xét args | Đảm bảo kết quả static true cho method cần xét |
| Method vẫn chạy khi dynamic condition false | Pointcut chỉ lọc advice, không bắt buộc chặn target | Đặt validation ở nơi đúng hợp đồng |
| Lỗi khi `proceed()` không được gọi | Around interceptor đã chặn chuỗi | Đảm bảo gọi `invocation.proceed()` trừ khi cố ý thay thế kết quả |
| `ClassFilter` dựa trên tên proxy | Proxy name thay đổi theo runtime/framework | So khớp target class/interface/annotation phù hợp |
| Hiệu năng giảm khi quá nhiều lần gọi | Matcher động đọc args cho mỗi invocation | Ưu tiên static; benchmark đường nóng |
| `final`, `private`, self-invocation không bị chặn | Giới hạn proxy Spring AOP | Thiết kế điểm vào bean, không dựa vào nội bộ |
| Kết hợp union làm match thừa | Tổ hợp điều kiện sai cấp độ | Dùng `ComposablePointcut`, test từng nhánh và phần giao |

**Kiểm chứng trước khi đưa vào production:** một test match đúng, một test không match theo method, một test không match theo class, một test thay args để dynamic đổi kết quả, một test call qua Spring proxy. Nếu advisor kiểm tra bảo mật hoặc giao dịch, còn phải test trường hợp bị từ chối và thứ tự với các advisor khác.

## 11. Bài thực hành nâng cao

Từ chương trình ở mục 5:

1. Đổi `SubmitPointcut` để chọn cả `submit` và `status`; dự đoán danh sách sự kiện khi gọi `status`. Sau đó đổi lại và chạy test.
2. Thêm `OrderService.cancel(String sku)` và một matcher riêng; dùng `ComposablePointcut.union(...)` để gắn cùng trace interceptor cho cả `submit` và `cancel`. Đảm bảo `OtherService.submit` vẫn không bị chặn.
3. Đổi điều kiện bulk thành `quantity >= 20` và test các giá trị 19/20, bao gồm ranh giới. Kiểm tra `isRuntime() == true`.
4. Tạo một điểm chặn chỉ chọn method gắn annotation tùy chỉnh. So sánh triển khai Java matcher dựa trên reflection với `AspectJExpressionPointcut` trong Spring, đồng thời kiểm thử annotation trên interface/implementation.
5. Đo thời gian gọi nhiều lần ở môi trường kiểm thử riêng khi có hoặc không có matcher động, tránh logging I/O bên trong phép đo. Kết luận dựa trên số đo thật thay vì suy đoán.

Bạn cần giải thích được vì sao **`Pointcut = ClassFilter + MethodMatcher`**, vì sao dynamic matcher chỉ chạy sau bước static, cách Spring nối Pointcut vào Advisor, và giới hạn khi lời gọi không đi qua proxy.

### Nguồn tham khảo Spring

- Spring Framework – Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework 6.2 – `Pointcut` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/Pointcut.html
- `StaticMethodMatcherPointcut`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/support/StaticMethodMatcherPointcut.html
- `DynamicMethodMatcherPointcut`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/support/DynamicMethodMatcherPointcut.html
- `ComposablePointcut`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/support/ComposablePointcut.html
- Spring proxy & self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-002">Lọc ở cấp lớp và ở cấp phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Lọc ở cấp lớp và ở cấp phương thức bằng Spring Pointcut API

## 1. Mục tiêu của bài học

Trong Spring AOP, không phải mọi lời gọi trên một bean đều cần chạy advice. Ví dụ: chỉ ghi nhận thao tác `charge(String)` của nhóm service thanh toán, không ghi nhận `status()` trên cùng service và cũng không ghi nhận một method tên `charge` thuộc service vận chuyển.

**Spring Pointcut API** giải quyết yêu cầu đó bằng hai bộ lọc độc lập:

1. `ClassFilter`: xác định **lớp target nào** thuộc phạm vi pointcut.
2. `MethodMatcher`: xác định **phương thức nào trên lớp target đó** thuộc phạm vi pointcut.

Hai bộ lọc hợp thành một `org.springframework.aop.Pointcut`. Advice được áp dụng cho method execution qua Spring proxy khi **điều kiện cấp lớp và điều kiện cấp phương thức cùng thỏa**; nếu matcher động, còn phải kiểm tra giá trị argument tại mỗi lần gọi.

Sau bài này, bạn có thể đọc các interface Java của Spring Pointcut API, triển khai pointcut tĩnh cho cả lớp và phương thức, kết nối pointcut với advice thông qua advisor, phân biệt matcher tĩnh/động và kiểm tra những trường hợp tưởng đã khớp nhưng advice không chạy.

Phạm vi tập trung vào **Spring AOP proxy và Pointcut API**. Không đi sâu vào lý thuyết aspect nói chung hoặc AspectJ weaving.

## 2. Ghép hai cấp lọc để mô tả đúng nghiệp vụ

Một `org.springframework.aop.Pointcut` gồm `getClassFilter()` (chọn lớp đích) và `getMethodMatcher()` (chọn phương thức trên lớp ấy). Hai giao diện tương ứng có các phép kiểm tra `ClassFilter.matches(Class<?>)` và `MethodMatcher.matches(Method, Class<?>)`. Chỉ khi bộ lọc tĩnh khớp, và matcher báo `isRuntime() == true`, Spring mới cần kiểm tra thêm `matches(Method, Class<?>, Object... args)` cho lần gọi thực tế. Đây là cấu trúc matching của Spring Pointcut API, không phải lời gọi trực tiếp tới target.

Áp dụng vào ví dụ thanh toán của bài:

| Target class | Method | ClassFilter chỉ chọn BillingService | MethodMatcher chọn charge(String) | Advice? |
| --- | --- | --- | --- | --- |
| `BillingService` | `charge(String)` | Có | Có | Có, khi caller qua proxy |
| `BillingService` | `status()` | Có | Không | Không |
| `ShippingService` | `charge(String)` | Không | Có về chữ ký | Không, bị loại từ lớp |

Công thức kiểm tra là `classMatch && staticMethodMatch && (!isRuntime || runtimeArgsMatch)`. Kết quả tĩnh có thể được Spring lưu lại; kết quả động chỉ phụ thuộc một lần invocation. Nếu một advisor không khớp, method nghiệp vụ vẫn tiếp tục như bình thường trừ khi một interceptor khác thay đổi luồng. Các phần tiếp theo cài đặt từng cấp lọc và kiểm chứng đúng ba dòng của bảng trên bằng `ProxyFactory`.

## 3. Lọc ở cấp lớp: `ClassFilter`

Phương thức `matches(Class<?> clazz)` nhận **kiểu target** mà Spring đang xem xét. Nó trả về `true` nếu target class thuộc tập lớp cần advice.

Ví dụ chỉ nhận `BillingService` và các subclass của nó:

~~~java
ClassFilter billingOnly =
        clazz -> BillingService.class.isAssignableFrom(clazz);
~~~

Đoạn này giả định class `BillingService` đã được khai báo. Ý nghĩa của `isAssignableFrom` là target có thể dùng như `BillingService`, do đó chấp nhận chính lớp và lớp con. So sánh `clazz == BillingService.class` chỉ khớp đúng một class; cân nhắc rõ chính sách của ứng dụng.

Ví dụ khác, chọn qua marker interface:

~~~java
ClassFilter auditedServices =
        clazz -> AuditedOperations.class.isAssignableFrom(clazz);
~~~

Giả định interface `AuditedOperations` do ứng dụng định nghĩa. Đây thường là lựa chọn tốt khi các implementation khác nhau cùng thuộc một nghiệp vụ, vì không cần buộc tất cả kế thừa một superclass.

**Lưu ý quan trọng:** Class filter không dùng để kiểm tra tên method, giá trị tham số hoặc giá trị return. Một khi lớp thỏa `ClassFilter`, `MethodMatcher` sẽ xử lý việc chọn method cụ thể. Đừng dùng package-name string một cách vô ý: package chứa class do Spring tạo proxy, các subclass và lớp triển khai có thể dẫn tới giả định sai. Hãy xem xét **target class**, thay vì tự suy từ tên lớp proxy quan sát được ở caller.

### ClassFilter mặc định

Một số pointcut tiện ích cho phép đặt `ClassFilter` riêng; khi không giới hạn, filter mặc định có thể là `ClassFilter.TRUE` (khớp mọi target class). Điều đó không đồng nghĩa mọi method đều khớp, vì `MethodMatcher` vẫn có quyền từ chối.

## 4. Lọc ở cấp phương thức: `MethodMatcher`

### 4.1. Lọc tĩnh bằng chữ ký method

`matches(Method method, Class<?> targetClass)` có đủ thông tin để xét:

- Tên method: `method.getName()`.
- Kiểu và số parameter: `method.getParameterTypes()`, `method.getParameterCount()`.
- Kiểu trả về: `method.getReturnType()`.
- Modifier: `Modifier.isPublic(method.getModifiers())`, v.v.
- Annotation/metadata mà implementation chủ động tra cứu trên method và target class.

Ví dụ yêu cầu method đúng tên `charge`, có một argument `String`:

~~~java
@Override
public boolean matches(Method method, Class<?> targetClass) {
    return method.getName().equals("charge")
            && method.getParameterCount() == 1
            && method.getParameterTypes()[0] == String.class;
}
~~~

Đây là phần thân khi override `StaticMethodMatcherPointcut.matches`, chưa phải tệp độc lập. Kiểu `Method` trong method matching không luôn đồng nhất với cách caller nhìn phương thức qua interface; nếu cần annotation implementation hoặc xử lý bridge/generic, có thể tra most-specific method qua `AopUtils.getMostSpecificMethod(...)`.

### 4.2. Tĩnh nghĩa là không phụ thuộc giá trị arguments

Method `charge("NORMAL-1")` và `charge("VIP-1")` có **cùng tên và chữ ký**, nên matcher tĩnh ở trên không phân biệt chúng. Lợi thế là Spring có thể cache kết quả matching thay vì tính lại ở từng lần gọi.

Với `StaticMethodMatcherPointcut`, `isRuntime()` là `false`. Spring sẽ không yêu cầu `matches(method, targetClass, args)` cho mỗi invocation. Đây là lựa chọn mặc định khi tiêu chí lọc chỉ dựa vào class và metadata method.

### 4.3. Động nghĩa là phải xem arguments tại thời điểm gọi

Nếu chỉ muốn advice cho `charge` khi mã bắt đầu bằng `VIP-`, phải thêm bước kiểm tra `args` tại runtime. Spring cung cấp `DynamicMethodMatcherPointcut`; với nó, `isRuntime()` là `true`.

~~~java
// Ví dụ một lớp pointcut riêng; cần import các kiểu Spring/Java tương ứng.
class VipChargePointcut extends DynamicMethodMatcherPointcut {

    @Override
    public ClassFilter getClassFilter() {
        return clazz -> BillingService.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("charge")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass, Object... args) {
        return args.length == 1
                && args[0] instanceof String id
                && id.startsWith("VIP-");
    }
}
~~~

Lớp trên là **biến thể minh họa** đặt cạnh ví dụ đầy đủ ở mục 5. Khi dùng phải import `org.springframework.aop.ClassFilter`, `org.springframework.aop.support.DynamicMethodMatcherPointcut` và `java.lang.reflect.Method`, đồng thời có `BillingService`.

Điểm quan trọng: Spring chỉ gọi dynamic matcher sau khi kết quả matching ở cấp class và chữ ký method cho phép. Runtime matcher có overhead trên mỗi lời gọi phù hợp với điều kiện tĩnh. Chỉ dùng khi giá trị đối số thực sự là một phần chính sách chọn advice.

## 5. Ví dụ chạy được: kết hợp hai cấp lọc bằng `ProxyFactory`

Ví dụ dùng **Java 17** và **Spring Framework 6.1.x** (qua Spring Boot 3.3.0 dependency management) để chạy Spring AOP theo dạng chương trình thuần Java. Nó không cần server HTTP, không cần annotation `@Aspect` và không cần Spring Boot startup. Chúng ta tạo proxy trực tiếp qua `ProxyFactory` để thấy rõ quan hệ giữa Pointcut, Advisor và Advice.

### 5.1. Cấu trúc

~~~text
pom.xml
src/main/java/com/example/pointcut/Demo.java
~~~

### 5.2. Maven dependencies

Tệp `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>spring-pointcut-filters</artifactId>
    <version>1.0.0</version>
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
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Spring Boot parent ở đây dùng để quản lý phiên bản dependency; không có `@SpringBootApplication`. `spring-boot-starter-aop` đưa Spring AOP và các thư viện liên quan vào classpath. Cơ chế proxy do `ProxyFactory` tạo trực tiếp.

### 5.3. Mã Java hoàn chỉnh

Tệp `src/main/java/com/example/pointcut/Demo.java`:

~~~java
package com.example.pointcut;

import java.lang.reflect.Method;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class Demo {

    public static class BillingService {
        public String charge(String code) {
            System.out.println("target:billing-charge:" + code);
            return "charged:" + code;
        }

        public String status() {
            System.out.println("target:billing-status");
            return "ready";
        }
    }

    public static class ShippingService {
        public String charge(String code) {
            System.out.println("target:shipping-charge:" + code);
            return "shipped:" + code;
        }
    }

    // Pointcut có cả ClassFilter và MethodMatcher.
    public static class BillingChargePointcut
            extends StaticMethodMatcherPointcut {

        public BillingChargePointcut() {
            setClassFilter(clazz ->
                    BillingService.class.isAssignableFrom(clazz));
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("charge")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class;
        }
    }

    public static void main(String[] args) {
        var pointcut = new BillingChargePointcut();

        MethodBeforeAdvice advice = (method, arguments, target) -> {
            System.out.println(
                    "advice:" + target.getClass().getSimpleName()
                    + "." + method.getName()
                    + "(" + arguments[0] + ")");
        };

        var advisor = new DefaultPointcutAdvisor(pointcut, advice);

        ProxyFactory billingFactory = new ProxyFactory(new BillingService());
        billingFactory.setProxyTargetClass(true);
        billingFactory.addAdvisor(advisor);
        BillingService billing =
                (BillingService) billingFactory.getProxy();

        ProxyFactory shippingFactory = new ProxyFactory(new ShippingService());
        shippingFactory.setProxyTargetClass(true);
        shippingFactory.addAdvisor(advisor);
        ShippingService shipping =
                (ShippingService) shippingFactory.getProxy();

        System.out.println("== Billing charge ==");
        System.out.println("result=" + billing.charge("B-1"));

        System.out.println("== Billing status ==");
        System.out.println("result=" + billing.status());

        System.out.println("== Shipping charge ==");
        System.out.println("result=" + shipping.charge("S-1"));
    }
}
~~~

Trong mã này:

1. `BillingChargePointcut` kế thừa `StaticMethodMatcherPointcut`. Đây là tiện ích Spring vừa đóng vai `Pointcut`, vừa thực hiện phần `MethodMatcher`, nên ta chỉ override phương thức `matches(Method, Class)`.
2. Constructor gọi `setClassFilter` để chọn target thuộc `BillingService`. `ShippingService` bị loại từ bước lọc lớp, dù có method `charge(String)` trùng chữ ký.
3. `matches` chỉ chọn `charge(String)`. Method `status()` của `BillingService` vượt qua bộ lọc class nhưng bị loại ở bộ lọc method.
4. `DefaultPointcutAdvisor(pointcut, advice)` ghép **điều kiện chọn** với **hành vi thực thi**. Đây là sự nối kết cốt lõi của low-level Spring AOP API.
5. `MethodBeforeAdvice` dùng để in log trước khi target method chạy. Nó không tự chọn method; advisor mới xác định nơi áp dụng.
6. Mỗi `ProxyFactory` nhận một target thực, thêm cùng advisor rồi tạo class-based proxy. `setProxyTargetClass(true)` giúp nhận proxy về bằng đúng concrete service type trong demo.

Các method của service là `public`, không `final`, và class có thể được subclass. Đây là điều kiện thuận tiện cho class-based proxy.

### 5.4. Chạy

Từ thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=com.example.pointcut.Demo
~~~

Không tính log của Maven hoặc môi trường, output do chương trình phát ra là:

~~~text
== Billing charge ==
advice:BillingService.charge(B-1)
target:billing-charge:B-1
result=charged:B-1
== Billing status ==
target:billing-status
result=ready
== Shipping charge ==
target:shipping-charge:S-1
result=shipped:S-1
~~~

**Vì sao chỉ có một dòng `advice:`?**

| Lời gọi | ClassFilter | MethodMatcher | Advice? |
| --- | --- | --- | --- |
| `billing.charge("B-1")` | PASS: `BillingService` | PASS: `charge(String)` | Có |
| `billing.status()` | PASS: `BillingService` | FAIL: tên `status` | Không |
| `shipping.charge("S-1")` | FAIL: `ShippingService` | Cấp method không cần được chọn | Không |

Cả ba lời gọi vẫn chạy phần nghiệp vụ. Pointcut quyết định **advice áp dụng ở đâu**, không phải là bộ chặn quyền truy cập method. Nếu cần cấm thực thi trong trường hợp nào đó thì phải có advice và chính sách bảo vệ được thiết kế rõ ràng.

## 6. Tự kiểm tra từng bộ lọc trước khi tạo proxy

Một ưu điểm của Pointcut API là bạn có thể kiểm tra kết quả matching bằng Java mà không cần chạy toàn bộ hệ thống. Với `pointcut` đã tạo ở mục 5:

~~~java
boolean billingClass =
        pointcut.getClassFilter().matches(BillingService.class);

boolean shippingClass =
        pointcut.getClassFilter().matches(ShippingService.class);

Method charge =
        BillingService.class.getMethod("charge", String.class);
Method status =
        BillingService.class.getMethod("status");

boolean chargeMethod =
        pointcut.getMethodMatcher().matches(charge, BillingService.class);
boolean statusMethod =
        pointcut.getMethodMatcher().matches(status, BillingService.class);

System.out.println(billingClass);
System.out.println(shippingClass);
System.out.println(chargeMethod);
System.out.println(statusMethod);
~~~

Đây là **đoạn kiểm tra bổ sung** để đặt trong `main`; giả định có biến `pointcut` cùng các nested service classes đã định nghĩa, và `main` cần khai báo `throws NoSuchMethodException` hoặc xử lý ngoại lệ từ `getMethod`. Khi chèn đúng vào ví dụ, bốn dòng tương ứng là:

~~~text
true
false
true
false
~~~

Chú ý: gọi trực tiếp `MethodMatcher.matches` trên một method của class không khớp `ClassFilter` có thể vẫn trả `true`, vì method matcher riêng không nhất thiết kiểm tra class filter. Để dự đoán advisor có áp dụng, phải kiểm tra **cả hai điều kiện**, không chỉ hỏi riêng method matcher.

### Vì sao không dùng `getClass()` của proxy?

Trong ví dụ, `billing.getClass()` là một subclass proxy do Spring tạo, không phải `BillingService.class` thuần. API `ClassFilter` được thiết kế cho **target class** mà Spring đánh giá; trong kiểm thử trực tiếp, dùng class target đã biết. Với một bean có sẵn trong Spring context, có thể dùng `AopUtils.getTargetClass(bean)` để tra target class theo cơ chế của Spring. Không nên viết class filter dựa vào tên lớp proxy sinh động như `BillingService$$SpringCGLIB$$...`.

## 7. Cách chọn kiểu triển khai Pointcut API

Spring có những lớp hỗ trợ để tránh phải tự triển khai cả `Pointcut` và `MethodMatcher`:

| Nhu cầu | Kiểu Spring phù hợp | Đặc điểm |
| --- | --- | --- |
| Chọn theo class + metadata method | `StaticMethodMatcherPointcut` | `setClassFilter` và override `matches(Method, Class)`; `isRuntime=false` |
| Chọn method theo tên hoặc pattern tên đơn giản | `NameMatchMethodPointcut` | Matcher tĩnh theo tên method; có thể bổ sung class filter |
| Chọn method theo regular expression | `JdkRegexpMethodPointcut` | Matcher tĩnh theo biểu thức chính quy; hạn chế bằng target class khi cần |
| Chọn theo AspectJ expression | `AspectJExpressionPointcut` | Dùng expression trong Spring AOP proxy, ví dụ `execution(...)`, `within(...)` |
| Cần xem argument của mỗi lần gọi | `DynamicMethodMatcherPointcut` | Thêm `matches(method, targetClass, args)` ở runtime |
| Kết hợp nhiều pointcut | `ComposablePointcut` hoặc `Pointcuts` | Hợp/ giao các điều kiện đã có |

Một ví dụ ngắn khi chỉ muốn chọn method tên `charge`:

~~~java
var nameMatch = new NameMatchMethodPointcut();
nameMatch.setMappedName("charge");
nameMatch.setClassFilter(clazz ->
        BillingService.class.isAssignableFrom(clazz));
~~~

Đoạn trên cần import `org.springframework.aop.support.NameMatchMethodPointcut`. `NameMatchMethodPointcut` cung cấp matcher theo tên, có thể cấu hình tên/pattern đơn giản, nên không cần viết `MethodMatcher` thủ công cho trường hợp này. Nhưng nếu cần phân biệt overload `charge(String)` và `charge(BigDecimal)`, matcher theo tên đơn thuần không đủ; phải bổ sung điều kiện chữ ký.

### Cách ghép điều kiện AND/OR

Giả sử có hai pointcut `P1` và `P2`:

- **Intersection**: cả `P1` và `P2` phải match.
- **Union**: ít nhất một trong `P1` hoặc `P2` match.

Các tiện ích `org.springframework.aop.support.Pointcuts` và `ComposablePointcut` hỗ trợ kết hợp. Tránh nhầm **intersection của hai pointcut** với việc chỉ viết thêm điều kiện method vào một pointcut mà bỏ mất class filter. Khi kết hợp các advisor khác nhau, nhiều advice có thể cùng chạy nếu mỗi advisor đều match.

Trong đa số Spring application, có thể diễn đạt phạm vi bằng `AspectJExpressionPointcut` hoặc `@Pointcut` rõ ràng hơn. Pointcut API hữu ích khi **điều kiện lọc được xây dựng bằng Java, cần tái sử dụng hoặc cần tích hợp advisor tùy chỉnh**.

## 8. Lớp và method matching trong Spring application có DI

Ví dụ `ProxyFactory` tạo proxy trực tiếp để làm rõ cơ chế. Trong ứng dụng Spring Boot/Spring Framework thực tế, bean có thể được proxy tự động nhờ advisor và auto-proxy creator. Khi dùng Spring DI:

- Pointcut/advisor phải được đăng ký và gắn vào quá trình tạo proxy theo cấu hình Spring AOP của ứng dụng.
- Caller phải lấy **Spring-managed proxy**, không tự `new` target rồi chờ advice chạy.
- Với JDK dynamic proxy, caller thường truy cập public method từ interface được proxy. Với class-based proxy, lớp/method `final` và method `private` không được intercept tương tự ví dụ public method.
- Self-invocation `this.charge(...)` bên trong target không đi qua proxy, nên class/method có thỏa bộ lọc vẫn không làm lời gọi nội bộ kích hoạt advice.

**Đủ điều kiện matching không có nghĩa chắc chắn được chặn:** kết quả `ClassFilter=true` và `MethodMatcher=true` mới chứng minh method thuộc phạm vi pointcut; còn phải có advisor trên proxy và lời gọi thực sự đi qua proxy đó.

### Cấu hình theo annotation và biểu thức có liên quan ra sao?

Trong Spring AOP, expression `execution(* com.example.billing..*(..))` có thể làm nhiệm vụ gần giống lọc class/package + lọc method bằng chữ ký; `within(com.example.billing..*)` thiên về kiểu khai báo. Nhưng pointcut expression cần được Spring dịch sang matcher, và không phải mọi AspectJ join point đều được Spring proxy AOP hỗ trợ. Bài này nhấn mạnh **API Java lõi** `Pointcut`, `ClassFilter`, `MethodMatcher` thay vì thay thế cơ chế XML hoặc `@Aspect` bằng cách diễn giải tùy tiện.

## 9. Lỗi phổ biến

### 9.1. Class filter dùng so sánh chính xác trong khi có subclass

~~~java
clazz -> clazz == BillingService.class
~~~

Điều kiện này không khớp một subclass của `BillingService` khi chính subclass ấy là target class. Nếu muốn chấp nhận subclass, dùng `BillingService.class.isAssignableFrom(clazz)`. Nếu chính sách bảo mật hoặc nghiệp vụ chỉ cho đúng một loại, so sánh chính xác vẫn có thể hợp lệ; quyết định dựa vào phạm vi muốn chọn.

### 9.2. Method filter chỉ so tên, vô tình chọn nhiều overload

~~~java
method.getName().equals("charge")
~~~

Nó có thể match `charge(String)`, `charge(int)`, `charge(BigDecimal)` nếu tất cả được proxy intercept. Khi cần đúng signature, so `getParameterTypes()`, `getReturnType()` hoặc các thuộc tính phù hợp.

### 9.3. Nhầm method matcher với phép kiểm tra mọi lời gọi

Một matcher tĩnh không xem được `args[0] == "VIP-1"` ở từng invocation. `isRuntime=false` có nghĩa Spring không gọi biến thể `matches(..., args)`. Để chọn theo argument value, dùng dynamic matcher hoặc advice điều kiện, đồng thời tính tới overhead.

### 9.4. Kiểm tra annotation sai method

Method có thể được nhìn từ interface khi dùng JDK proxy. Annotation đặt trên implementation method không nhất thiết được tìm thấy khi chỉ gọi `method.getAnnotation(...)` trên method của interface. Nếu bài toán cần annotation ở implementation, dùng `AopUtils.getMostSpecificMethod(method, targetClass)` và xác định chính sách metadata. Không giả định Java kế thừa annotation method từ interface.

### 9.5. Proxy, self-invocation và final method

Nếu method không có advice dù đã thấy matcher trả `true`, hãy kiểm tra caller có giữ proxy hay target gốc, class/method có bị `final`, và lời gọi có đi từ bean khác qua proxy không. Không cố giải quyết vấn đề self-invocation bằng việc nới `ClassFilter`, vì bộ lọc không tạo thêm một lượt đi qua proxy.

### 9.6. Làm matcher chậm hoặc có tác dụng phụ

Matcher tĩnh nên dựa vào metadata ổn định. Không truy vấn database, gọi HTTP hoặc sửa state ở `matches`. Spring có thể gọi matcher trong quá trình dựng proxy và caching, nên điều kiện phụ thuộc state thay đổi theo từng lời gọi sẽ không an toàn khi đặt ở matcher tĩnh. Matcher động cần gọn nhẹ vì chạy nhiều lần; nếu cần quan sát dữ liệu nghiệp vụ phức tạp, cân nhắc thiết kế khác.

### 9.7. Quá rộng ở class level, quá hẹp ở method level

Ví dụ `ClassFilter.TRUE` kết hợp `MethodMatcher` chỉ chọn tên `charge` có thể áp dụng advice trên mọi bean có tên method đó, bao gồm service không liên quan. Ngược lại, giới hạn đúng một class khi ứng dụng dùng nhiều implementation của interface có thể làm mất các bean cần advice. Hãy coi class filter là ranh giới nghiệp vụ/kiến trúc và method matcher là lựa chọn operation.

## 10. Checklist kiểm thử

Đối với một custom pointcut trong Spring:

1. **Class scope:** test class cần chọn và class cần loại; nếu có inheritance, test cả subclass hoặc interface implementation.
2. **Method scope:** test method cần chọn, method khác tên, overload khác chữ ký và method không thể proxy intercept.
3. **Static/dynamic:** xác nhận `isRuntime()` đúng với mục đích; nếu dynamic, thử nhiều argument values; tránh dùng matcher tĩnh cho yêu cầu thay đổi theo lần gọi.
4. **Proxy:** gọi qua Spring proxy và xác nhận advice chạy đúng số lần; kiểm tra cả trường hợp self-invocation bị bỏ qua.
5. **Advisor:** bảo đảm pointcut được ghép với advice trên proxy thực tế; một class/method match đơn lẻ chưa đủ.
6. **Behavior:** method bị loại khỏi advice vẫn thực thi đúng nghiệp vụ; method được chọn vẫn trả kết quả/ném exception theo contract của advice tương ứng.
7. **Chi phí:** giữ filter rẻ, ổn định và giới hạn phạm vi hợp lý khi ứng dụng có nhiều bean hoặc tần suất gọi cao.

## 11. Ghi nhớ

**Spring `Pointcut` tách quyết định áp dụng advice thành `ClassFilter` và `MethodMatcher`.** Class filter xác định đối tượng target nào có thể được chặn; method matcher xác định operation nào phù hợp. Với static matcher, quyết định dựa vào class/method metadata và có thể được cache. Với dynamic matcher, Spring còn xét argument values khi gọi. Dù hai bộ lọc đều khớp, advice chỉ chạy nếu advisor được lắp vào Spring proxy và caller đi qua proxy.

Tài liệu chính thức:

- [Spring Framework Reference — Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [Spring Framework Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework API — Pointcut](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/aop/Pointcut.html)
- [Spring Framework API — MethodMatcher](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/aop/MethodMatcher.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-003">MethodMatcher tĩnh và kiểm tra động theo đối số</a>

<details>
<summary>Xem chi tiết</summary>

# MethodMatcher tĩnh và kiểm tra động theo đối số trong Spring AOP

## 1. Mục tiêu: cùng một chữ ký, nhiều quyết định theo đối số

Giả sử `TransferService.transfer(String, int)` luôn hợp lệ để ghi log tối thiểu, nhưng chỉ những lần gọi có `amount >= 1000` cần audit bổ sung. Spring AOP cho phép tách **điều kiện tĩnh** (class, tên method, kiểu/số tham số) khỏi **điều kiện động** (giá trị thật của `amount` trong từng invocation).

`Pointcut` chứa `ClassFilter` và `MethodMatcher`; `DefaultPointcutAdvisor` ghép Pointcut với advice trong Spring proxy. Với `MethodMatcher`, cần phân biệt ba phương thức:

- `matches(Method, Class<?>)`: kiểm tra tĩnh xem method có thể nhận advice.
- `isRuntime()`: cho biết có cần kiểm tra giá trị thực tế mỗi lời gọi.
- `matches(Method, Class<?>, Object... args)`: chỉ được gọi khi bước tĩnh true và `isRuntime()` true; có thể trả true cho lần gọi này và false cho lần khác.

## 2. Dự đoán khi method và argument thay đổi

| Lời gọi qua proxy | Static match transfer(String,int) | Dynamic match amount >= 1000 | Audit động |
| --- | --- | --- | --- |
| `transfer("A", 50)` | Có | Không | Bỏ qua |
| `transfer("B", 1500)` | Có | Có | Chạy |
| `balance("A")` | Không | Không cần gọi | Bỏ qua |

`ClassFilter` vẫn là cửa đầu tiên để loại toàn bộ target class không liên quan. Spring có thể cache quyết định tĩnh khi tạo hoặc phân giải proxy; đừng gắn side effect vào matcher hay assert nó chỉ chạy đúng một lần. Với một method đã qua bước tĩnh, runtime matcher được đánh giá trên từng invocation theo trạng thái argument lúc đó, rồi Spring mới quyết định chạy advice tương ứng.

Hai lớp Spring có sẵn là `StaticMethodMatcherPointcut` (`isRuntime() == false`) và `DynamicMethodMatcherPointcut` (`isRuntime() == true`). Matcher tĩnh phù hợp khi cùng chữ ký luôn có cùng quyết định; matcher động cần điều kiện tĩnh đủ hẹp và phép kiểm tra `args` nhẹ, thuần, không thay đổi nghiệp vụ. Ví dụ tiếp theo hiện thực đúng tình huống `transfer` trên bằng `ProxyFactory`.

## 3. Ví dụ hoàn chỉnh với Spring ProxyFactory

Yêu cầu: phương thức `transfer` của `TransferService` được audit. Cách **tĩnh** audit mọi lệnh transfer. Cách **động** chỉ audit khi đối số `amount >= 1000`. Ví dụ dùng Java 17+ và Spring AOP trực tiếp, không cần Spring Boot container hay annotation `@Aspect`.

**`build.gradle`** (Groovy DSL):

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-aop:6.1.21'
}

application {
    mainClass = 'example.MatchersDemo'
}
```

**`src/main/java/example/MatchersDemo.java`**:

```java
package example;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class MatchersDemo {

    public interface TransferService {
        void transfer(String account, int amount);
        void balance(String account);
    }

    static final class TransferServiceImpl implements TransferService {
        @Override
        public void transfer(String account, int amount) {
            System.out.println("TARGET transfer " + account + " " + amount);
        }

        @Override
        public void balance(String account) {
            System.out.println("TARGET balance " + account);
        }
    }

    private static boolean isTransfer(Method method, Class<?> targetClass) {
        return TransferService.class.isAssignableFrom(targetClass)
                && method.getName().equals("transfer")
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == String.class
                && method.getParameterTypes()[1] == int.class;
    }

    static final class StaticTransferPointcut
            extends StaticMethodMatcherPointcut {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return isTransfer(method, targetClass);
        }
    }

    static final class LargeTransferPointcut
            extends DynamicMethodMatcherPointcut {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            // Loại các method không liên quan trước khi xét args.
            return isTransfer(method, targetClass);
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass,
                               Object... args) {
            // int trong tham số được boxing thành Integer trong args.
            return args.length == 2
                    && args[1] instanceof Integer amount
                    && amount >= 1000;
        }
    }

    private static TransferService createProxy(Pointcut pointcut) {
        ProxyFactory factory = new ProxyFactory(new TransferServiceImpl());
        factory.setInterfaces(TransferService.class);
        factory.setProxyTargetClass(false);

        MethodInterceptor audit = invocation -> {
            System.out.println("ADVICE audit");
            return invocation.proceed();
        };
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, audit));
        return (TransferService) factory.getProxy();
    }

    public static void main(String[] args) {
        System.out.println("=== STATIC ===");
        TransferService a = createProxy(new StaticTransferPointcut());
        a.transfer("ACC", 100);
        a.transfer("ACC", 1500);
        a.balance("ACC");

        System.out.println("=== DYNAMIC ===");
        TransferService b = createProxy(new LargeTransferPointcut());
        b.transfer("ACC", 100);
        b.transfer("ACC", 1500);
        b.balance("ACC");
    }
}
```

Chạy tại thư mục có `build.gradle` bằng `gradle run` (nếu dự án có Gradle wrapper: `./gradlew run`; PowerShell: `.\gradlew.bat run`). Stdout của chương trình, không tính log Gradle:

```text
=== STATIC ===
ADVICE audit
TARGET transfer ACC 100
ADVICE audit
TARGET transfer ACC 1500
TARGET balance ACC
=== DYNAMIC ===
TARGET transfer ACC 100
ADVICE audit
TARGET transfer ACC 1500
TARGET balance ACC
```

Đọc kết quả:

1. Với pointcut **tĩnh**, cả hai lời gọi `transfer` đều nhận advice, vì `Method` và `targetClass` không đổi dù số tiền khác nhau.
2. Với pointcut **động**, bước tĩnh chọn `transfer`; bước runtime lấy `args[1]`. Khi amount là `100`, advice không chạy nhưng target vẫn chạy. Khi amount là `1500`, advice chạy trước target.
3. `balance` bị loại ngay trong `matches(Method, Class<?>)` của cả hai pointcut, nên không in dòng audit.
4. `setInterfaces` và `setProxyTargetClass(false)` khiến ví dụ dùng JDK interface proxy; gọi các phương thức thông qua interface proxy mới đi qua Spring AOP.

Lưu ý: `instanceof Integer amount` dùng pattern matching Java 17, và primitive `int` được boxing khi đặt vào `Object[] args`. Việc kiểm tra `args.length` cùng `instanceof` tránh lỗi khi chữ ký không như dự tính. Kiểm tra tên **và kiểu tham số** tại bước tĩnh cũng giúp tránh match nhầm overload.

## 4. Cách thiết kế matcher hiệu quả

Đặt điều kiện **không phụ thuộc giá trị đầu vào** vào overload hai tham số:

```java
@Override
public boolean matches(Method method, Class<?> targetClass) {
    return method.getName().equals("transfer")
            && method.getParameterCount() == 2;
}
```

Chỉ đặt điều kiện **cần giá trị lúc gọi** vào overload ba tham số:

```java
@Override
public boolean matches(Method method, Class<?> targetClass, Object... args) {
    return args.length == 2
            && args[1] instanceof Integer amount
            && amount >= 1000;
}
```

Hai đoạn trên nhằm minh họa sự phân chia trách nhiệm; ví dụ chạy đầy đủ ở mục 3 có kiểm tra chữ ký chặt chẽ hơn.

Với dịch vụ có nhiều phương thức nhưng chỉ `transfer` cần xét ngưỡng, bộ lọc tĩnh giúp tránh chi phí đọc `args` trên các phương thức khác. Không nên để overload tĩnh luôn trả `true` trừ khi thật sự muốn kiểm tra động cho toàn bộ chúng.

Matcher nên **nhanh, không có side effect, không I/O**, không sửa `args` và không phụ thuộc trạng thái có thể thay đổi một cách khó đoán. Với điều kiện rất tốn kém (DB, HTTP), cân nhắc thực hiện ở service/advice với chính sách cache và xử lý lỗi rõ ràng; dynamic matcher được gọi thường xuyên trong đường đi của phương thức.

## 5. Những bẫy thường gặp

**Nhầm `isRuntime()` với việc tạo proxy runtime.** Nó chỉ cho biết có cần thực hiện thêm overload có `args` hay không. `StaticMethodMatcherPointcut` có `isRuntime() == false`; `DynamicMethodMatcherPointcut` có `isRuntime() == true`. Khi tự cài `MethodMatcher` trực tiếp, ba phương thức phải nhất quán.

**Nhầm chữ ký method với đối số thực tế.** `method.getParameterTypes()` cho kiểu khai báo (`int.class`), còn `args` chứa giá trị của lần gọi (ví dụ `Integer(100)`, `Integer(1500)`). Kiểm tra tĩnh không thể phân biệt hai giá trị này.

**Tưởng matcher trả `false` sẽ dừng transaction/target.** `false` chỉ không áp dụng advice tương ứng. Muốn từ chối dữ liệu không hợp lệ phải thực hiện validation/authorization hoặc ném exception ở lớp nghiệp vụ thích hợp; không dùng matcher đơn thuần để đảm bảo chặn thao tác.

**Không lọc ở bước tĩnh.** Nếu chỉ cần `transfer`, kiểm tra tên/chữ ký trong overload hai tham số. Để overload tĩnh trả `true` với mọi method sẽ khiến Spring chạy dynamic check nhiều hơn mức cần thiết.

**Dựa vào số lần gọi overload tĩnh.** Spring có thể xét tĩnh khi tạo proxy hoặc phân giải phương thức và có caching; các chi tiết triển khai và số lần gọi có thể khác nhau. Chỉ nên dựa vào hợp đồng là runtime check cần static match và `isRuntime == true`.

**Quên thứ tự advice.** Runtime matching được thực hiện trước advice tương ứng; các advice đứng trước trong interceptor chain có thể đã chạy. Nếu một interceptor trước đó đổi đối số, thứ tự chuỗi có thể ảnh hưởng giá trị quan sát được. Vì vậy không nên viết matcher có side effect hoặc phụ thuộc trình tự gọi không được bảo đảm.

**Quên giới hạn proxy.** Lời gọi từ bên ngoài qua proxy được Spring AOP xử lý. Một self-invocation kiểu `this.transfer(...)` bên trong cùng target thường bỏ qua proxy; matcher sẽ không được dùng cho lời gọi nội bộ đó.

## 6. Chọn static, dynamic hay biểu thức AspectJ của Spring?

- Chọn **`StaticMethodMatcherPointcut`** cho điều kiện theo tên, chữ ký, lớp hoặc metadata ổn định. Đây là hướng ưu tiên khi không cần giá trị đầu vào.
- Chọn **`DynamicMethodMatcherPointcut`** khi cần *quyết định có chạy advice* dựa trên đối số mỗi lần gọi, chẳng hạn amount vượt ngưỡng.
- Với điều kiện pointcut thông dụng, biểu thức AspectJ được Spring AOP hỗ trợ (ví dụ `execution(...)`, `within(...)`, `args(...)`) thường gọn hơn. `args(...)` có thể ràng buộc/kiểm tra kiểu đối số ở runtime nhưng không phải cú pháp trực tiếp cho phép viết phép so sánh số `amount >= 1000`; trường hợp này có thể kiểm tra trong advice hoặc dùng custom dynamic MethodMatcher.

Không nên tạo dynamic matcher chỉ để thay một điều kiện có thể giải quyết hoàn toàn bằng matching tĩnh.

## 7. Câu hỏi tự kiểm tra

1. `matches(Method, Class) == false`, `isRuntime() == true`: overload có `args` có chạy không? **Không**; bước tĩnh đã loại method.
2. `isRuntime() == false`: có thể phân biệt hai lần gọi cùng chữ ký nhưng amount khác nhau không? **Không** bằng MethodMatcher đó.
3. Matcher động trả `false` khi amount=100: target còn chạy không? **Có**, khi các thành phần khác trong chain không chặn.
4. Chỉ cần audit mọi `transfer`: dùng lớp nào? **`StaticMethodMatcherPointcut`**, vì tiêu chí không phụ thuộc args.
5. Với `transfer(String, int)`, `getParameterTypes()[1]` và `args[1]` khác gì? **Kiểu khai báo `int.class`** so với **giá trị boxed `Integer` tại lần gọi**.

## Tài liệu Spring chính thức

- [Spring Reference — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [Spring 6.1 — MethodMatcher](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/MethodMatcher.html)
- [Spring 6.1 — StaticMethodMatcherPointcut](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/support/StaticMethodMatcherPointcut.html)
- [Spring 6.1 — DynamicMethodMatcherPointcut](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/support/DynamicMethodMatcherPointcut.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-004">StaticMethodMatcherPointcut và trường hợp áp dụng</a>

<details>
<summary>Xem chi tiết</summary>

# StaticMethodMatcherPointcut trong Spring AOP và các trường hợp áp dụng

## 1. Tại sao cần pointcut tĩnh?

Khi một Spring bean có nhiều phương thức, ta thường muốn gắn một advice chỉ với **một nhóm phương thức được xác định từ lớp và chữ ký**, chẳng hạn:

- Ghi nhận việc gọi `OrderService.submit(String, int)`, nhưng bỏ qua `status()`.
- Chỉ đo thời gian những phương thức public được đánh dấu bằng annotation nghiệp vụ.
- Áp dụng interceptor cho các method thuộc một nhóm service cụ thể.
- Giữ quy tắc lựa chọn phương thức ở Java code khi không muốn dùng biểu thức AspectJ/`@Aspect`.

Nếu phạm vi chặn có thể xác định **mà không cần nhìn vào giá trị đối số trong mỗi lần gọi**, Spring AOP cung cấp lớp cơ sở `StaticMethodMatcherPointcut`.

Đây là thành phần thuộc **Spring Framework AOP API**, không phải cơ chế chung của ngôn ngữ AspectJ. Nó giúp tạo một `Pointcut` có `MethodMatcher` tĩnh và có thể được gắn với `Advice` thông qua `Advisor` trên Spring proxy.

```text
Caller
  |
  v
Spring proxy
  |
  +--> Advisor
         |
         +--> ClassFilter.matches(targetClass)
         |       false -> bỏ advice của advisor này
         |
         +--> StaticMethodMatcher.matches(method, targetClass)
                 false -> bỏ advice của advisor này
                 true  -> cho phép advice tham gia chuỗi chặn
  |
  v
Target method (nếu advice cho phép tiếp tục)
```

**Điểm mấu chốt:** “static” nói về **điều kiện chọn method**, không có nghĩa advice chỉ chạy một lần. Nếu method khớp và caller gọi nó 1.000 lần qua proxy, advice có thể chạy 1.000 lần. Spring có thể lưu/ tái sử dụng kết quả khớp tĩnh để tránh kiểm tra lại không cần thiết; chính xác số lần gọi hàm `matches` là chi tiết triển khai và không nên trở thành hợp đồng nghiệp vụ.

## 2. Đặt StaticMethodMatcherPointcut vào kiến trúc Spring AOP

Giao diện `org.springframework.aop.Pointcut` gồm hai phần:

```java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
```

Trong đó `ClassFilter` xác định **target class** có thuộc phạm vi không:

```java
public interface ClassFilter {
    boolean matches(Class<?> clazz);
}
```

Còn `MethodMatcher` quyết định **method** có khớp hay không:

```java
public interface MethodMatcher {
    boolean matches(Method method, Class<?> targetClass);
    boolean isRuntime();
    boolean matches(Method method, Class<?> targetClass, Object... args);
}
```

Đây là các chữ ký API trích rút để giải thích, không phải các file bạn phải tạo thêm trong dự án.

`StaticMethodMatcherPointcut` là lớp trừu tượng trong package:

```java
org.springframework.aop.support.StaticMethodMatcherPointcut
```

Nó kế thừa `StaticMethodMatcher`, đồng thời triển khai `Pointcut`. Một subclass thông thường chỉ cần override:

```java
@Override
public boolean matches(Method method, Class<?> targetClass) {
    // Phân tích tên, chữ ký, annotation, targetClass...
    return ...;
}
```

Chú ý ba thuộc tính quan trọng:

| Thành phần | Hành vi của StaticMethodMatcherPointcut | Hệ quả |
| --- | --- | --- |
| `getClassFilter()` | Mặc định trả `ClassFilter.TRUE` | Nếu không thay, không giới hạn target class |
| `setClassFilter(ClassFilter)` | Đặt bộ lọc target class | Thu hẹp tập lớp trước khi xét method |
| `getMethodMatcher()` | Trả matcher của chính pointcut | Có thể đưa pointcut vào advisor |
| `isRuntime()` | Luôn `false` | Spring không chạy bước khớp theo `args` trên từng invocation |
| `matches(Method,Class,Object...)` | Không phải điểm mở rộng cho static matcher | Chỉ override `matches(Method,Class)` |

**Không cần tự viết cả ba phương thức `MethodMatcher`.** Đây chính là lý do dùng lớp cơ sở thay vì tự triển khai `Pointcut` và `MethodMatcher` từ đầu.

## 3. Phân biệt khớp tĩnh và khớp động

Giả sử muốn ghi metrics cho method `submit(String sku, int quantity)`.

**Điều kiện tĩnh**: “Đây có phải method tên `submit`, nhận `String` và `int` trên `OrderService`?” — Có thể trả lời bằng `Method` và `targetClass`.

**Điều kiện động**: “Giá trị `quantity` trong lần gọi hiện tại có lớn hơn 10 không?” — Phải nhìn mảng `args` mỗi lần method được gọi.

```text
submit("A", 2)   -> cùng Class và Method -> static matcher: TRUE
submit("A", 20)  -> cùng Class và Method -> static matcher: TRUE

Nếu yêu cầu quantity >= 10:
submit("A", 2)   -> dynamic matcher: FALSE
submit("A", 20)  -> dynamic matcher: TRUE
```

| Tiêu chí | StaticMethodMatcherPointcut | DynamicMethodMatcherPointcut |
| --- | --- | --- |
| Dữ liệu dùng để quyết định | `Method`, `Class<?>` | `Method`, target class và `Object... args` |
| `isRuntime()` | `false` | `true` |
| Phải kiểm tra đối số trên từng lần gọi? | Không | Có, sau khi vượt qua kiểm tra tĩnh |
| Trường hợp thường gặp | tên/chữ ký method, class, annotation | điều kiện dựa vào giá trị tham số của invocation |
| Chi phí khớp | Thường thấp, có thể tối ưu/cache | Có bước đánh giá bổ sung mỗi lần gọi |

**Không sử dụng `StaticMethodMatcherPointcut` để kiểm tra giá trị đầu vào** như `quantity > 0`. Ngay cả khi một static matcher trả `true`, mã của advice hoặc chính service vẫn phải xử lý hợp đồng nghiệp vụ nếu cần từ chối dữ liệu sai. Pointcut quyết định *advice có tham gia*, không phải nơi mặc định thực hiện validation.

## 4. Thiết kế điều kiện tĩnh đúng và đủ chặt

Một implementation cơ bản:

```java
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public final class SubmitPointcut extends StaticMethodMatcherPointcut {
    public SubmitPointcut() {
        setClassFilter(OrderService.class::isAssignableFrom);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("submit")
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == String.class
                && method.getParameterTypes()[1] == int.class;
    }
}
```

Đây là một trích đoạn: `OrderService` phải có trong cùng module. Bản chạy độc lập sẽ xuất hiện ở mục 6.

Ý nghĩa của hai bước:

1. `setClassFilter(OrderService.class::isAssignableFrom)` giới hạn những target class là `OrderService` hoặc subtype.
2. `matches(Method,Class)` xác định chính xác overload `submit(String,int)`. Nó không match overload `submit(String)` hay method `status()`.

### Chọn theo tên method có đủ không?

Điều kiện:

```java
return method.getName().equals("submit");
```

có thể vô tình match `submit(String)`, `submit(String,int)`, `submit(Order)`. Nếu interceptor cần đọc đối số ở vị trí cố định hoặc dựa vào kiểu trả về, phải kiểm tra chữ ký phù hợp. Trong một số nhu cầu, tên method là đủ; khi đó ghi rõ rằng **mọi overload** sẽ được chọn.

### Target class có ảnh hưởng gì?

Một lớp khác cùng có method `submit(String,int)` không nhất thiết thuộc phạm vi. Nếu giữ mặc định `ClassFilter.TRUE` và matcher chỉ kiểm tra tên/chữ ký, advisor có thể chặn cả lớp khác. Nếu mục tiêu là riêng `OrderService`, đặt class filter phù hợp.

Đừng so sánh tên proxy sinh ra như `clazz.getName().contains("CGLIB")`: đó là chi tiết triển khai, không phải đặc điểm nghiệp vụ. Dùng kiểu lớp, interface, annotation hay cấu trúc package có quy tắc rõ.

### Nên kiểm tra annotation như thế nào?

Sự hiện diện của một annotation runtime trên một method là **metadata tĩnh** đối với lời gọi. Tuy nhiên khi dùng JDK proxy, method có thể là phương thức khai báo trên interface; metadata của implementation có thể cần được tìm bằng API Spring:

```java
// Trích đoạn minh họa logic trong matches(...):
Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
return AnnotatedElementUtils.hasAnnotation(specific, Monitored.class);
```

Import:

```java
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
```

`Monitored` là annotation ứng dụng cần tự khai báo với `@Retention(RUNTIME)` và `@Target(METHOD)`. `AnnotatedElementUtils` hỗ trợ cách Spring xử lý một số trường hợp meta-annotation/merged annotation; nếu dự án có bridge method, generic/interface method hoặc override phức tạp, hãy test đúng kiểu proxy thật. Chỉ dùng `method.getAnnotation(...)` trong mọi hoàn cảnh có thể bỏ sót annotation trên implementation.

## 5. Từ Pointcut sang Advice: bản thân matcher không tạo interception

`StaticMethodMatcherPointcut` chỉ quyết định *có khớp hay không*. Để tác động tới lời gọi, phải kết nối:

```text
StaticMethodMatcherPointcut
         +
MethodInterceptor (Advice)
         |
         v
DefaultPointcutAdvisor
         |
         v
Spring AOP proxy
         |
         v
Service target
```

Trong mã Java, cấu trúc thường là:

```java
MethodInterceptor interceptor = invocation -> {
    System.out.println("before");
    try {
        return invocation.proceed();
    } finally {
        System.out.println("after");
    }
};

var advisor = new DefaultPointcutAdvisor(
        new SubmitPointcut(), interceptor);

var proxyFactory = new ProxyFactory(target);
proxyFactory.addAdvisor(advisor);
Object proxy = proxyFactory.getProxy();
```

Đây là đoạn mô tả wiring: cần khai báo `target` và `SubmitPointcut` trong chương trình. `DefaultPointcutAdvisor` ghép một `Pointcut` với một `Advice`; `ProxyFactory` tạo Spring AOP proxy.

Lệnh `invocation.proceed()` đưa lời gọi qua phần còn lại của interceptor chain đến target (trừ khi interceptor khác chặn tiếp). Nếu quên gọi mà cũng không chủ ý thay thế kết quả, target method sẽ không thực thi. Khối `finally` vẫn chạy khi target ném lỗi; đó là hành vi của **advice**, độc lập với việc static pointcut chọn method.

**Phân biệt `Pointcut` và `@Pointcut`:** `org.springframework.aop.Pointcut` là một đối tượng có `ClassFilter` và `MethodMatcher`; `org.aspectj.lang.annotation.Pointcut` là annotation dùng khai báo pointcut trong `@Aspect`. Hai API có liên hệ khái niệm nhưng cách cấu hình không giống nhau.

## 6. Ví dụ hoàn chỉnh với Java 17 và Spring ProxyFactory

Ví dụ bên dưới không cần `@Aspect` hay AspectJ Weaver. Ta dùng trực tiếp Spring AOP API để minh họa đúng cách `StaticMethodMatcherPointcut` được nối vào advisor.

Mục tiêu kiểm thử:

- Chặn `OrderService.submit(String,int)` ở mọi giá trị `quantity`.
- Không chặn `OrderService.submit(String)` (overload) hoặc `OrderService.status()`.
- Không chặn `OtherService.submit(String,int)`, dù trùng tên/chữ ký.
- Ghi nhận chính xác thứ tự lời gọi `before → target → after` và giữ nguyên giá trị trả về.

Cấu trúc:

```text
spring-static-pointcut/
├── pom.xml
└── src/
    ├── main/java/example/StaticPointcutDemo.java
    └── test/java/example/StaticPointcutDemoTest.java
```

### 6.1. File pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-static-pointcut</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.8</version>
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

### 6.2. File src/main/java/example/StaticPointcutDemo.java

```java
package example;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class StaticPointcutDemo {

    public static class OrderService {
        private final List<String> events;

        public OrderService(List<String> events) {
            this.events = events;
        }

        public String submit(String sku, int quantity) {
            events.add("target:submit:" + sku + ":" + quantity);
            return "OK:" + sku + ":" + quantity;
        }

        public String submit(String sku) {
            events.add("target:overload:" + sku);
            return "OVERLOAD:" + sku;
        }

        public String status() {
            events.add("target:status");
            return "READY";
        }
    }

    public static class OtherService {
        private final List<String> events;

        public OtherService(List<String> events) {
            this.events = events;
        }

        public String submit(String sku, int quantity) {
            events.add("other:submit:" + sku + ":" + quantity);
            return "OTHER:" + sku + ":" + quantity;
        }
    }

    public static class SubmitPointcut extends StaticMethodMatcherPointcut {
        public SubmitPointcut() {
            // Chỉ OrderService (hoặc subclass) đủ điều kiện.
            setClassFilter(OrderService.class::isAssignableFrom);
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("submit")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == int.class;
        }
    }

    public record Fixture(OrderService service, List<String> events) {}

    public static Fixture createFixture() {
        List<String> events = new ArrayList<>();
        OrderService target = new OrderService(events);

        MethodInterceptor trace = invocation -> {
            events.add("before:" + invocation.getMethod().getName());
            try {
                return invocation.proceed();
            } finally {
                events.add("after:" + invocation.getMethod().getName());
            }
        };

        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DefaultPointcutAdvisor(
                new SubmitPointcut(), trace));

        return new Fixture((OrderService) factory.getProxy(), events);
    }

    public static OtherService createOtherProxy(List<String> events) {
        ProxyFactory factory = new ProxyFactory(new OtherService(events));
        factory.setProxyTargetClass(true);

        MethodInterceptor trace = invocation -> {
            events.add("UNEXPECTED");
            return invocation.proceed();
        };

        // Cùng advisor nhưng ClassFilter không chọn OtherService.
        factory.addAdvisor(new DefaultPointcutAdvisor(
                new SubmitPointcut(), trace));

        return (OtherService) factory.getProxy();
    }

    public static void main(String[] args) {
        Fixture fixture = createFixture();
        OrderService order = fixture.service();

        System.out.println(order.submit("A", 2));
        System.out.println(order.submit("B", 12));
        System.out.println(order.submit("A"));
        System.out.println(order.status());

        List<String> otherEvents = new ArrayList<>();
        OtherService other = createOtherProxy(otherEvents);
        System.out.println(other.submit("C", 99));

        System.out.println("orderEvents=" + fixture.events());
        System.out.println("otherEvents=" + otherEvents);
    }
}
```

Giải thích theo từng thành phần:

1. `SubmitPointcut` là subclass của Spring `StaticMethodMatcherPointcut`; class filter loại `OtherService` trước khi xét method.
2. `matches` chỉ xem **tên và chữ ký**. Dù `quantity` là 2, 12 hay 0, kết quả khớp của `submit(String,int)` giống nhau.
3. `ProxyFactory` nhận target và thêm `DefaultPointcutAdvisor`. Phương thức `submit` qua proxy mới chạy interceptor; tạo trực tiếp `new OrderService(events)` rồi gọi sẽ không có advice.
4. `factory.setProxyTargetClass(true)` yêu cầu proxy dựa trên class để có thể ép kiểu kết quả thành `OrderService`/`OtherService`. Trong ứng dụng dùng interface, JDK proxy có thể là lựa chọn phù hợp nhưng phải lấy bean qua interface.
5. Advice sử dụng `try/finally`, do đó `after:submit` cũng xảy ra nếu target có exception. Đây là **điều khoản do advice viết**, không phải do pointcut tự sinh ra.

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.StaticPointcutDemo
```

Stdout logic kỳ vọng (không tính log phụ thuộc Maven/Spring nếu có):

```text
OK:A:2
OK:B:12
OVERLOAD:A
READY
OTHER:C:99
orderEvents=[before:submit, target:submit:A:2, after:submit, before:submit, target:submit:B:12, after:submit, target:overload:A, target:status]
otherEvents=[other:submit:C:99]
```

Bằng chứng từ output:

- Hai lời gọi `submit(String,int)` đều sinh `before`/`after` dù số lượng khác nhau. Đây là bản chất **static**.
- `submit(String)` có cùng tên nhưng khác overload, chỉ ghi `target:overload:A`.
- `status()` không bị advice chặn; `OtherService.submit(...)` cũng không có `UNEXPECTED` nhờ class filter.
- Các method đích vẫn trả về kết quả như bình thường, vì interceptor gọi `proceed()`.

## 7. Kiểm thử đúng hai tầng: hợp đồng matcher và proxy thật

Một test chỉ gọi `pointcut.matches(...)` chưa chứng minh advisor đã được gắn vào proxy. Một test chỉ kiểm tra log khi gọi service có thể bỏ sót việc matcher có `ClassFilter` quá rộng. Kết hợp hai tầng:

`src/test/java/example/StaticPointcutDemoTest.java`:

```java
package example;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;

import static org.junit.jupiter.api.Assertions.*;

class StaticPointcutDemoTest {

    @Test
    void staticMatcherMatchesOnlyExpectedClassAndSignature()
            throws Exception {
        var pointcut = new StaticPointcutDemo.SubmitPointcut();

        Method matched = StaticPointcutDemo.OrderService.class
                .getMethod("submit", String.class, int.class);
        Method overload = StaticPointcutDemo.OrderService.class
                .getMethod("submit", String.class);
        Method status = StaticPointcutDemo.OrderService.class
                .getMethod("status");

        assertTrue(pointcut.getClassFilter()
                .matches(StaticPointcutDemo.OrderService.class));
        assertFalse(pointcut.getClassFilter()
                .matches(StaticPointcutDemo.OtherService.class));

        assertTrue(pointcut.getMethodMatcher()
                .matches(matched, StaticPointcutDemo.OrderService.class));
        assertFalse(pointcut.getMethodMatcher()
                .matches(overload, StaticPointcutDemo.OrderService.class));
        assertFalse(pointcut.getMethodMatcher()
                .matches(status, StaticPointcutDemo.OrderService.class));

        assertFalse(pointcut.getMethodMatcher().isRuntime());
    }

    @Test
    void springProxyAppliesInterceptorToEveryMatchingInvocation() {
        var fixture = StaticPointcutDemo.createFixture();
        var order = fixture.service();

        assertTrue(AopUtils.isAopProxy(order));
        assertEquals("OK:A:2", order.submit("A", 2));
        assertEquals("OK:B:12", order.submit("B", 12));
        assertEquals("OVERLOAD:A", order.submit("A"));
        assertEquals("READY", order.status());

        assertEquals(List.of(
                "before:submit",
                "target:submit:A:2",
                "after:submit",
                "before:submit",
                "target:submit:B:12",
                "after:submit",
                "target:overload:A",
                "target:status"), fixture.events());
    }

    @Test
    void anotherClassWithSameMethodSignatureIsExcluded() {
        List<String> events = new ArrayList<>();
        var other = StaticPointcutDemo.createOtherProxy(events);

        assertEquals("OTHER:C:99", other.submit("C", 99));
        assertEquals(List.of("other:submit:C:99"), events);
    }
}
```

Chạy:

```bash
mvn -q test
```

Ba phép thử đáp ứng ba câu hỏi:

| Test | Kiểm chứng |
| --- | --- |
| `staticMatcherMatchesOnlyExpectedClassAndSignature` | ClassFilter và MethodMatcher phân biệt đúng class, overload, method khác; `isRuntime()==false` |
| `springProxyAppliesInterceptorToEveryMatchingInvocation` | Advice được gắn lên Spring proxy, chạy ở cả quantity=2 và quantity=12, giữ kết quả target |
| `anotherClassWithSameMethodSignatureIsExcluded` | ClassFilter ngăn chặn advisor của một class khác có cùng tên/chữ ký method |

**Tránh tạo test kiểm tra “`matches` chỉ chạy chính xác một lần”.** Spring có thể kiểm tra khả năng áp dụng advisor lúc tạo proxy, lúc gọi đầu tiên hoặc qua những nhánh xử lý khác. Hợp đồng công khai ở đây là không kiểm tra `args` ở runtime, không phải bộ đếm lời gọi phương thức matcher cố định.

## 8. Các trường hợp thực tế phù hợp

### 8.1. Ghi metrics cho một nhóm method cố định

Chọn các method public trên những service có tên nghiệp vụ phù hợp. Ví dụ bộ lọc như:

```java
@Override
public boolean matches(Method method, Class<?> targetClass) {
    return method.getName().startsWith("find")
            && method.getParameterCount() <= 2;
}
```

Đây là trích đoạn method trong subclass `StaticMethodMatcherPointcut`, không phải class chạy độc lập. Nếu muốn đo latency, advice dùng `System.nanoTime()` bao quanh `proceed()`; phần **chọn method** không phải đo latency. Khi service bất đồng bộ trả `CompletableFuture`, thời gian quanh `proceed()` không nhất thiết là thời gian hoàn thành future.

### 8.2. Áp dụng chính sách theo annotation method

Nếu chính sách được khai báo tĩnh bằng `@Measured` hoặc `@Audited`, matcher có thể xem annotation trên method và dùng `ClassFilter` thu hẹp tới service package/kiểu target. Điều kiện annotation ổn định giữa các lần gọi nên hợp lý với static pointcut. Tuy nhiên chính sách bên trong advice vẫn cần thiết kế, kiểm thử dữ liệu nhạy cảm và hậu quả của lỗi.

### 8.3. Lọc theo chữ ký, overload hoặc interface

Với API có nhiều overload cùng tên, static matcher giúp phân biệt chính xác `submit(String,int)` và `submit(String)`. Nếu dùng JDK dynamic proxy, method phản ánh interface có thể khác method implementation. Hãy test **cả kiểu proxy và vị trí annotation thực tế**. `ClassFilter` chỉ nhìn target class; không mặc định phải là tên proxy.

### 8.4. Một pointcut dùng lại cho nhiều advice

Vì pointcut độc lập với advice, có thể gắn cùng một instance hoặc cùng một class pointcut vào nhiều advisor:

```java
var pointcut = new SubmitPointcut();

factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, metricsAdvice));
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, auditAdvice));
```

Đây là trích đoạn wiring khi đã có `factory`, `metricsAdvice`, `auditAdvice`. Nếu hai advice cùng khớp, thứ tự advisor ảnh hưởng thứ tự quan sát. Dùng `@Order` ở một nơi khác không tự bảo đảm thứ tự các advisor đã thêm thủ công vào `ProxyFactory`; hãy cấu hình và kiểm thử thứ tự cụ thể.

### 8.5. Khi không nên dùng custom static matcher

- Điều kiện chặn đơn giản kiểu tên method: cân nhắc `NameMatchMethodPointcut` để giảm code.
- Điều kiện biểu thức package/annotation/interface thường gặp: `AspectJExpressionPointcut` hoặc `@Pointcut` trong một Spring `@Aspect` có thể dễ đọc hơn.
- Cần xem giá trị đối số mỗi lần gọi: dùng một `DynamicMethodMatcherPointcut` phù hợp hoặc đưa kiểm tra vào advice/service theo trách nhiệm.
- Cần chặn constructor, field access hoặc lời gọi nội bộ không đi qua proxy: Spring AOP proxy không hỗ trợ trực tiếp các join point đó, dù có custom pointcut.

## 9. Tổ hợp nhiều điều kiện tĩnh

Spring hỗ trợ `ComposablePointcut` và tiện ích `Pointcuts` để hợp (union) hoặc giao (intersection) các điều kiện.

- **Giao:** method phải thỏa **cả hai** yêu cầu, ví dụ class service phù hợp *và* method có annotation.
- **Hợp:** method thỏa **ít nhất một nhánh**, ví dụ `submit` *hoặc* `cancel`.

Ví dụ gợi ý dạng minh họa:

```java
// Một pointcut chọn submit(String,int).
var submit = new SubmitPointcut();

// Một pointcut khác chọn cancel(String).
var cancel = new CancelPointcut();

// Tạo pointcut gộp, rồi đưa vào DefaultPointcutAdvisor.
var combined = new ComposablePointcut(submit).union(cancel);
```

Đây là trích đoạn minh họa API: `SubmitPointcut` có trong ví dụ mục 6, còn `CancelPointcut` và method `cancel` cần được tạo trước khi chạy. Import `org.springframework.aop.support.ComposablePointcut`. Khi ghép các pointcut hoàn chỉnh, cần giữ đúng ý nghĩa quan hệ giữa **ClassFilter và MethodMatcher của từng nhánh**; không tự gộp “mọi class của hai nhánh” với “mọi method của hai nhánh” nếu điều đó làm match thừa.

Khi pointcut đã đơn giản, việc tự viết quá nhiều lớp matcher và tổ hợp có thể khó hiểu hơn một biểu thức pointcut rõ nghĩa. Chọn API vì nó làm hợp đồng chặn minh bạch và kiểm thử được, không vì muốn thay toàn bộ `@Aspect`.

## 10. Giới hạn của Spring proxy và những bẫy thiết kế

| Triệu chứng | Nguyên nhân | Cách kiểm chứng/khắc phục |
| --- | --- | --- |
| Test `matches` đúng nhưng advice không chạy | Advisor chưa gắn vào proxy hoặc caller dùng target trực tiếp | Dùng `ProxyFactory`/Spring bean proxy thật và `AopUtils.isAopProxy(...)` |
| Method khác cũng bị chặn | Matcher chỉ nhìn tên, không phân biệt overload/class | Kiểm tra tên, số lượng và kiểu tham số; đặt ClassFilter |
| Method đúng tên nhưng không bị chặn | `ClassFilter` loại target class hoặc chữ ký không trùng | Test riêng ClassFilter và MethodMatcher trên class thực |
| Cần phân biệt quantity=2 và quantity=12 nhưng advice luôn chạy | Static matcher không đọc giá trị `args` | Dùng dynamic matcher hoặc điều kiện trong advice/service |
| Overload interface/implementation khác nhau | Method nhận được có thể là khai báo trên interface | Dùng API tra method cụ thể và test kiểu proxy thực |
| Caller trong bean dùng `this.submit(...)` | Self-invocation bỏ qua proxy | Gọi qua một Spring-managed proxy/boundary thích hợp |
| Target là method `final`/`private` | Proxy-based AOP có giới hạn ghi đè/khả năng chặn | Thiết kế method có thể proxy được; kiểm thử integration |
| Advice không gọi target | Interceptor không gọi `proceed()` | Tiếp tục chuỗi trừ khi có chủ ý thay đổi hợp đồng |
| Matcher truy vấn DB/log I/O khi khớp | Side effect và chi phí ở giai đoạn chọn advisor | Giữ matcher thuần, nhẹ, không phụ thuộc trạng thái mutable |
| Kết quả test phụ thuộc số lần `matches` được gọi | Coi chiến lược cache là hợp đồng | Test kết quả match và hành vi advice, không test internal call count |

### Vì sao matcher nên là hàm thuần?

Method matcher nhận thông tin tĩnh về phương thức/lớp. Nếu nó phụ thuộc “thời điểm hiện tại”, biến toàn cục có thể thay đổi, trạng thái của request hoặc I/O ngoại vi, kết quả có thể không nhất quán với cách framework lưu kết quả match tĩnh. Đừng đưa logic nghiệp vụ có trạng thái vào `matches`. Hãy dùng điều kiện tĩnh thật sự, hoặc chuyển logic phụ thuộc invocation vào thành phần phù hợp.

### Self-invocation và kiểm thử trên bean thật

Giả sử `OrderService.status()` gọi `this.submit("A",2)`. Ở chế độ Spring proxy thường gặp, lần gọi nội bộ không qua proxy, nên advice cho `submit` có thể không xuất hiện. Điều này **không chứng minh matcher sai**. Khi kiểm thử hành vi AOP, luôn bắt đầu từ caller lấy được **proxy** chứ không dùng chính target object để xác nhận interception.

### Làm rõ tài nguyên và lỗi

Static pointcut có thể chọn đúng method nhưng vẫn chưa giải quyết vấn đề chạy advice khi exception, transaction, bất đồng bộ hoặc reactive. Những nội dung đó được xác định bởi **Advice/Advisor và chuỗi proxy**, không bởi tên `StaticMethodMatcherPointcut`. Ví dụ metrics có `finally` sẽ chạy ghi thời gian dù exception; còn `@AfterReturning` chỉ áp dụng khi lời gọi trả về bình thường.

## 11. Bài tập thực hành và tiêu chí hoàn thành

Từ chương trình `StaticPointcutDemo`:

1. Thêm method `submit(String,int,boolean)`. Giữ matcher hiện tại và kiểm tra overload mới **không bị chặn**.
2. Thay `ClassFilter` bằng `ClassFilter.TRUE`, tạo một `OtherService` proxy với cùng advisor và kiểm tra nó bắt đầu sinh log `UNEXPECTED`. Trả filter cũ về để sửa.
3. Thay matcher thành điều kiện chọn cả `submit` và `status`; dự đoán rồi xác nhận thứ tự các event.
4. Thêm annotation `@Measured` trên `submit` và sửa matcher chỉ chọn method có annotation. Kiểm thử một implementation có interface nếu ứng dụng dùng JDK proxy.
5. Muốn chỉ đo các lần có `quantity >= 10`: giải thích vì sao static matcher không đủ; chuyển sang dynamic matcher hoặc điều kiện có chủ đích trong interceptor rồi test ranh giới 9/10.
6. Đặt một counter vào chính advice và một counter chẩn đoán vào matcher. Chạy nhiều invocation, quan sát sự khác biệt, nhưng **không biến số lần Spring gọi matcher thành điều kiện pass/fail**, vì cơ chế tối ưu có thể khác nhau.

Một bài triển khai đạt yêu cầu khi bạn chứng minh được: đúng target class, đúng method/overload, advice chạy **mỗi lần method đã chọn được gọi qua proxy**, dữ liệu kết quả/exception của method được giữ đúng, method và class ngoài phạm vi không bị chặn, và không nhầm điều kiện tĩnh với kiểm tra đối số động.

### Tài liệu chính thức của Spring

- Pointcut API và static/dynamic matcher: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- `StaticMethodMatcherPointcut` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/StaticMethodMatcherPointcut.html
- `StaticMethodMatcher` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/StaticMethodMatcher.html
- Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- Spring proxy và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-005">NameMatchMethodPointcut và khớp theo tên phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# NameMatchMethodPointcut: khớp phương thức theo tên trong Spring AOP

## 1. Khi nào dùng?

`NameMatchMethodPointcut` là một `Pointcut` có sẵn trong **Spring AOP API** (`org.springframework.aop.support.NameMatchMethodPointcut`). Nó chọn phương thức dựa trên **tên phương thức**: tên chính xác hoặc một mẫu ký tự `*` đơn giản. Đây là lựa chọn gọn cho những quy ước tên rõ ràng như `save*`, `find*`, `*Async` mà không cần viết biểu thức AspectJ.

Ví dụ, một ứng dụng Spring muốn ghi log trước khi gọi các phương thức `saveOrder()`, `saveCustomer()` nhưng không ghi trước `deleteOrder()`. Pointcut cấu hình mẫu `save*`; một Spring `Advisor` ghép pointcut đó với `Advice`; Spring AOP proxy áp dụng advice cho các lời gọi phù hợp đi **qua proxy**.

Phạm vi bài này là API và cơ chế hoạt động **trong Spring AOP**, không đi sâu vào lý thuyết Aspect nói chung.

## 2. Vị trí của NameMatchMethodPointcut trong Spring

`NameMatchMethodPointcut` kế thừa `StaticMethodMatcherPointcut`, đồng thời cung cấp hai thành phần chuẩn của `org.springframework.aop.Pointcut`:

- `ClassFilter`: xác định lớp đích nào được xét. Mặc định không thu hẹp lớp; có thể dùng `setClassFilter(...)` để giới hạn.
- `MethodMatcher`: xác định phương thức nào được chọn. `matches(Method, Class<?>)` của `NameMatchMethodPointcut` xét tên `method.getName()` so với các mẫu đã cấu hình.

Đây là **static method matcher**: `isRuntime()` trả về `false`. Vì vậy Spring không phải so khớp lại theo đối số của *mỗi lần gọi*; Spring có thể cache chuỗi interceptor/advice áp dụng cho phương thức. Tính chất “static” ở đây nói về cách **chọn phương thức**, không có nghĩa advice chỉ chạy một lần: advice vẫn chạy ở mỗi lời gọi được proxy chặn.

Luồng xử lý:

```text
Lời gọi qua Spring AOP proxy
  → kiểm tra advisor/pointcut có áp dụng cho lớp và phương thức không
  → nếu tên phù hợp, chạy advice theo loại advice được cấu hình
  → gọi phương thức trên đối tượng đích
```

`NameMatchMethodPointcut` chỉ xác định **nơi áp dụng**; tự nó không ghi log, không kiểm soát transaction, không tạo proxy và không tự chạy advice. Để dùng, cần ghép với `Advice` thông qua `DefaultPointcutAdvisor` (hoặc cơ chế Spring AOP tương đương) và có proxy.

## 3. Quy tắc khớp tên và wildcard

| Mẫu cấu hình | Khớp | Không khớp | Giải thích |
| --- | --- | --- | --- |
| `saveOrder` | `saveOrder` | `saveOrders`, `saveCustomer` | Khớp chính xác |
| `save*` | `save`, `saveOrder` | `createOrder` | Bắt đầu bằng `save` |
| `*Async` | `sendAsync`, `saveAsync` | `sendSync` | Kết thúc bằng `Async` |
| `*Order*` | `getOrder`, `updateOrderStatus` | `getCustomer` | Chứa `Order` |
| `find*ById` | `findOrderById`, `findById` | `findByName` | Có tiền tố và hậu tố phù hợp |

Cú pháp `*` ở đây là so khớp chuỗi đơn giản của Spring (`PatternMatchUtils.simpleMatch`), **không phải regex** và **không phải biểu thức `execution(...)` của AspectJ**. Ký tự `*` biểu thị chuỗi ký tự bất kỳ (kể cả chuỗi rỗng); so khớp có phân biệt chữ hoa/chữ thường. Đừng dùng `save.*` theo thói quen regex để biểu thị “bắt đầu bằng save”; trong API này nên dùng `save*`.

Khi có nhiều mẫu, kết quả là phép **OR**: chỉ cần tên phương thức khớp một mẫu. Ví dụ `setMappedNames("save*", "findById")` chọn cả nhóm `save...` lẫn tên `findById`.

Điểm giới hạn quan trọng:

- **Không phân biệt overload:** `save(String)` và `save(long)` có cùng tên `save`; cả hai cùng được chọn nếu mẫu khớp `save`. Tham số, kiểu trả về, annotation hay exception khai báo không được xét bởi bộ matcher này.
- **Không tự giới hạn package/lớp:** nếu chỉ dùng mẫu `save*` trong một hệ thống có nhiều bean được Spring auto-proxy, những phương thức cùng tên ở các bean khác cũng có thể được chọn. Dùng `ClassFilter` hoặc cấu hình phạm vi proxy khi cần.
- **Không chọn theo giá trị đối số:** muốn điều kiện phụ thuộc `args` của từng lần gọi cần cơ chế pointcut/advice thích hợp khác.

## 4. Những API cần nhớ

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();

// Cấu hình đúng một mẫu:
pointcut.setMappedName("save*");

// Hoặc cấu hình nhiều mẫu, thay cho lệnh trên:
pointcut.setMappedNames("save*", "findById");

// Có thể thêm mẫu trước khi đưa proxy vào sử dụng:
pointcut.addMethodName("update*");

// Có thể giới hạn lớp đích:
pointcut.setClassFilter(type -> OrderServiceImpl.class.isAssignableFrom(type));
```

Các lệnh trong khối trên minh họa **những lựa chọn cấu hình**, không có ý yêu cầu chạy `setMappedName` rồi `setMappedNames` tuần tự: với cấu hình thực tế, hãy chọn một trong hai setter. `addMethodName(...)` dùng khi đang lắp đặt proxy, **trước khi proxy được sử dụng**; thay đổi danh sách mẫu sau đó có thể không tác dụng do Spring cache advice chain. Hãy coi cấu hình pointcut là bất biến sau khi khởi tạo proxy.

Cặp phương thức kiểm tra trực tiếp:

```java
Method method = OrderService.class.getMethod("saveOrder");
boolean matched = pointcut.matches(method, OrderServiceImpl.class);
```

`matched` là `true` nếu tên và cấu hình pointcut phù hợp. Trên thực tế Spring sẽ dùng `getClassFilter()` và `getMethodMatcher()` qua infrastructure AOP; kiểm tra trực tiếp như trên chỉ hữu ích khi thử logic match. Hai đoạn API này dựa trên các kiểu `OrderService` và `OrderServiceImpl` được định nghĩa ở ví dụ tiếp theo.

## 5. Ví dụ Java 17+ chạy được với Spring AOP API

Ví dụ tạo proxy có chủ đích bằng `ProxyFactory` để nhìn rõ cách kết hợp `NameMatchMethodPointcut`, `DefaultPointcutAdvisor` và `MethodBeforeAdvice`. Không cần bật AspectJ annotation processing hoặc tạo Spring Boot application cho ví dụ này.

Cấu trúc:

```text
name-match-demo/
├── build.gradle
└── src/main/java/demo/NameMatchDemo.java
```

Tệp `build.gradle` (Gradle Groovy DSL):

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-aop:6.2.14'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.NameMatchDemo'
}
```

Phiên bản `6.2.14` là phiên bản Spring Framework 6.x cố định dành cho ví dụ; trong dự án Spring Boot, nên dùng phiên bản `spring-aop` do Spring Boot quản lý để đồng bộ các thành phần Spring.

Tệp `src/main/java/demo/NameMatchDemo.java`:

```java
package demo;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class NameMatchDemo {

    public interface OrderService {
        void saveOrder();
        String findById(long id);
        void deleteOrder();
    }

    public static final class OrderServiceImpl implements OrderService {
        @Override
        public void saveOrder() {
            System.out.println("[service] saveOrder");
        }

        @Override
        public String findById(long id) {
            System.out.println("[service] findById: " + id);
            return "order-" + id;
        }

        @Override
        public void deleteOrder() {
            System.out.println("[service] deleteOrder");
        }
    }

    public static void main(String[] args) {
        // (1) Chọn tên phương thức cần can thiệp.
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames("save*", "findById");

        // (2) Định nghĩa hành vi chạy trước phương thức được chọn.
        MethodBeforeAdvice beforeAdvice = (method, methodArgs, target) ->
                System.out.println("[AOP before] " + method.getName());

        // (3) Ghép tiêu chí chọn phương thức với advice.
        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, beforeAdvice);

        // (4) Tạo Spring AOP proxy qua interface OrderService.
        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(advisor);
        OrderService proxy = (OrderService) factory.getProxy();

        // (5) Chỉ hai phương thức đầu khớp tên.
        proxy.saveOrder();
        proxy.findById(42L);
        proxy.deleteOrder();
    }
}
```

Chạy trong thư mục dự án bằng `gradle run` (cần Gradle và JDK 17+). Nếu dự án có Gradle Wrapper, dùng `./gradlew run` trên Unix/macOS hoặc `gradlew.bat run` trên Windows. Phần stdout do `main` sinh ra, không kể log của Gradle:

```text
[AOP before] saveOrder
[service] saveOrder
[AOP before] findById
[service] findById: 42
[service] deleteOrder
```

Giải thích chính xác:

1. Mẫu `save*` bắt `saveOrder`; mẫu `findById` bắt đúng `findById`.
2. `DefaultPointcutAdvisor` nối pointcut với một `MethodBeforeAdvice`. Khi proxy nhận lời gọi phù hợp, dòng `[AOP before] ...` xuất hiện trước dòng từ service.
3. `deleteOrder` không khớp bất kỳ mẫu nào nên chỉ phần triển khai service chạy.
4. `factory.setInterfaces(OrderService.class)` yêu cầu proxy theo interface. Biến `proxy` là đối tượng được tăng cường bởi Spring AOP; đối tượng đích `new OrderServiceImpl()` bên trong không tự được tăng cường nếu bị gọi trực tiếp.
5. Tên mẫu quyết định chọn phương thức, không dựa trên giá trị `42L`. Nếu gọi `findById(7L)`, advice vẫn được chọn.

## 6. Dùng với bean do Spring quản lý

Trong ứng dụng Spring sử dụng auto-proxy, có thể khai báo một `Advisor` dưới dạng bean thay vì tự gọi `ProxyFactory` cho từng service. Ví dụ cấu hình dưới đây minh họa phần Spring IoC (giả sử `OrderService` và `OrderServiceImpl` ở ví dụ trước là các kiểu ứng dụng và dự án có `spring-context` cùng hạ tầng AOP/AspectJ cần thiết, chẳng hạn qua `spring-boot-starter-aop`):

```java
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
class OrderAopConfiguration {
    @Bean
    OrderService orderService() {
        return new OrderServiceImpl();
    }

    @Bean
    DefaultPointcutAdvisor orderNameAdvisor() {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames("save*", "findById");
        pointcut.setClassFilter(
                type -> OrderServiceImpl.class.isAssignableFrom(type)
        );

        MethodBeforeAdvice advice = (method, args, target) ->
                System.out.println("[audit] " + method.getName());

        return new DefaultPointcutAdvisor(pointcut, advice);
    }
}
```

Đoạn này là **trích đoạn cấu hình**, không phải tệp độc lập chạy bằng `java`. Import thêm `OrderService`/`OrderServiceImpl` theo package thực tế. Khi hạ tầng auto-proxy của Spring đang hoạt động, advisor bean có thể được xét cho bean `orderService`; `ClassFilter` giúp tránh áp dụng nhầm cùng mẫu tên trên các loại bean khác. Lấy service qua `ApplicationContext.getBean(OrderService.class)` để sử dụng proxy do container tạo, thay vì tự `new OrderServiceImpl()` ở mã gọi nghiệp vụ. Việc chỉ khai báo một `DefaultPointcutAdvisor` không tự tạo proxy nếu ứng dụng chưa cấu hình hạ tầng auto-proxy.

Nếu ứng dụng đã dùng Spring Boot starter AOP, hãy kiểm tra cấu hình AOP hiện hữu trước khi bổ sung `@EnableAspectJAutoProxy`. `NameMatchMethodPointcut` thuộc **Spring AOP programmatic API**; nó không phải một chuỗi biểu thức hợp lệ để chèn trực tiếp vào `@Pointcut("...")`.

## 7. Những lỗi dễ gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `saveOrder()` không xuất hiện dòng advice | Gọi trực tiếp object đích thay vì proxy | Lấy object từ `factory.getProxy()` hoặc Spring container |
| Gọi từ `this.saveOrder()` trong cùng object không kích hoạt advice | Self-invocation bỏ qua proxy trong Spring AOP kiểu proxy | Tổ chức lời gọi đi qua bean/proxy phù hợp hoặc chỉnh ranh giới service |
| `save(String)` và `save(long)` đều được áp dụng | Matcher xét tên, không xét chữ ký overload | Dùng matcher/pointcut chi tiết hơn nếu cần phân biệt chữ ký |
| Cấu hình `save.*` không bắt `saveOrder` | Nhầm cú pháp regex với wildcard đơn giản | Dùng `save*` |
| Advice vô tình áp dụng cho nhiều service | Mẫu tên rộng và chưa giới hạn lớp/bean | Thêm `setClassFilter(...)` hoặc giới hạn phạm vi auto-proxy |
| Thêm `addMethodName(...)` sau khi app đã xử lý lời gọi mà không có hiệu lực | Advice chain có thể đã được cache | Hoàn thiện cấu hình trước khi sử dụng proxy |
| Phương thức `private`, `final` hoặc method không được proxy phơi ra không bị chặn | Giới hạn của proxy JDK/CGLIB trong Spring AOP | Xem kiểu proxy và khả năng intercept của phương thức; cần phương thức có thể được proxy chặn |

Lưu ý thêm: với JDK dynamic proxy, chỉ các phương thức được truy cập qua interface proxy mới có thể được can thiệp theo cách này; với proxy dựa trên subclass, `final` không thể bị override để intercept. Các giới hạn đó thuộc cơ chế proxy của Spring AOP, không phải lỗi của mẫu tên. Khớp tên được áp dụng cho **Java method name**, không phải đường dẫn HTTP, tên endpoint, tên bean hay tên class.

## 8. Chọn NameMatchMethodPointcut hay cách khác?

- Chọn `NameMatchMethodPointcut` khi quy tắc thực sự là **tên phương thức**, đơn giản và ổn định; ví dụ chỉ cần can thiệp `save*` trên một nhóm service được kiểm soát.
- Chọn `JdkRegexpMethodPointcut` hoặc pointcut dựa trên biểu thức AspectJ khi cần mẫu biểu thức phong phú hơn; chú ý mỗi loại có cú pháp khác nhau.
- Chọn `AspectJExpressionPointcut` / `@Aspect` với `execution(...)`, `within(...)`, annotation... khi ứng dụng đang tổ chức cross-cutting logic theo annotation và cần điều kiện về package, lớp, chữ ký hoặc metadata.
- Chọn matcher tự viết khi cần luật nghiệp vụ đặc thù không diễn đạt chính xác bằng mẫu tên đơn giản.

Một câu hỏi kiểm tra nhanh: cấu hình `setMappedNames("save*", "findById")` có chọn `saveOrder(String)`, `saveOrder(long)`, `findById(long)` và `deleteOrder()` không? **Có** với ba phương thức đầu, **không** với `deleteOrder()`. Lý do: phép OR theo *tên*, không theo tham số.

## 9. Tài liệu Spring chính thức

- [NameMatchMethodPointcut Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/NameMatchMethodPointcut.html): chữ ký API, wildcard, overload, thời điểm cấu hình.
- [Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `Pointcut`, `ClassFilter`, `MethodMatcher` và static/dynamic matching.
- [DefaultPointcutAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/DefaultPointcutAdvisor.html): cách ghép `Pointcut` với `Advice`.

Điểm cần nhớ khi đọc code Spring: **`NameMatchMethodPointcut` lọc phương thức theo tên; `Advisor` gắn advice; proxy quyết định lời gọi nào thực sự đi qua cơ chế AOP.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-006">AspectJExpressionPointcut để dùng biểu thức qua API</a>

<details>
<summary>Xem chi tiết</summary>

# AspectJExpressionPointcut: sử dụng biểu thức qua Spring AOP API

## 1. Mục tiêu và phạm vi

`AspectJExpressionPointcut` là hiện thực `org.springframework.aop.Pointcut` của **Spring AOP** cho phép khai báo phạm vi chặn method bằng chuỗi biểu thức kiểu AspectJ ngay trong Java API. Ta có thể kết hợp pointcut này với một `Advice` qua `Advisor`, rồi gắn advisor vào AOP proxy bằng `ProxyFactory`; không cần tạo lớp `@Aspect` cho ví dụ này.

Sau bài học, bạn có thể:

- Khởi tạo `AspectJExpressionPointcut`, thiết lập `setExpression(...)` và kiểm tra class/method có thể khớp.
- Ghép pointcut với `MethodInterceptor` trong `DefaultPointcutAdvisor` để advice chỉ áp dụng cho method được chọn.
- Hiểu cách `ClassFilter`, `MethodMatcher` và các kiểm tra tại runtime vận hành trong **cơ chế proxy của Spring AOP**.
- Phân biệt biểu thức phù hợp với Spring AOP và những giới hạn do proxy, visibility, lời gọi nội bộ gây ra.

Bài này chỉ xem **cách dùng API của Spring**. Cú pháp AspectJ được giải thích đủ để sử dụng biểu thức; không đi sâu vào AspectJ compiler, bytecode weaving hoặc lý thuyết AOP nói chung.

## 2. Đặt `AspectJExpressionPointcut` ở đâu trong Spring AOP?

Spring AOP tách logic chọn method và logic xử lý trước/sau method:

```text
Chuỗi expression
        │
        ▼
AspectJExpressionPointcut ──► ClassFilter + MethodMatcher
        │                            │
        └─────────► DefaultPointcutAdvisor ◄──────── MethodInterceptor (advice)
                                   │
                                   ▼
                         ProxyFactory / proxy
                                   │
                    lời gọi đi QUA proxy
                                   │
             method khớp → advice → target method
             method không khớp → target method
```

- `Pointcut` cung cấp `getClassFilter()` và `getMethodMatcher()`. `ClassFilter` loại những loại target không liên quan; `MethodMatcher` quyết định method nào phù hợp.
- `AspectJExpressionPointcut` cài đặt cả hai phần đó, sử dụng thư viện AspectJ để **phân tích và đánh giá** chuỗi biểu thức.
- `Advisor` là cặp quy tắc lựa chọn (pointcut) và hành vi cần áp dụng (advice). `DefaultPointcutAdvisor` là cách ghép trực tiếp, đơn giản.
- `ProxyFactory` tạo Spring AOP proxy theo cấu hình trên. Chỉ những lời gọi qua proxy mới được Spring AOP chặn.

Ví dụ khởi tạo một pointcut:

```java
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
pointcut.setExpression(
    "execution(* com.example.service..*Service.*(..))"
);
```

Biểu thức ở đây chọn các method của kiểu có tên kết thúc bằng `Service` trong package `com.example.service` và các package con. `execution` là pointcut designator chủ yếu đối với Spring AOP vì Spring AOP làm việc trên **method execution join point**.

## 3. Ví dụ Java 17 chạy được: chọn đúng một method

Ví dụ chạy thuần Java + Spring AOP, không cần khởi động Spring Boot/ApplicationContext. Ta tạo interface với hai method, chỉ chặn `greet` và để `ping` đi thẳng tới target. Cách này làm rõ ba bước riêng biệt: **khai báo pointcut → ghép advisor → tạo proxy**.

Cây thư mục:

```text
spring-pointcut-api/
├── pom.xml
└── src/main/java/demo/
    ├── GreetingService.java
    ├── GreetingServiceImpl.java
    └── App.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-api</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
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
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-aop` cung cấp pointcut, advisor và proxy; `aspectjweaver` cung cấp các thành phần để giải mã và đánh giá biểu thức. Phụ thuộc `aspectjweaver` **không** có nghĩa ví dụ này đang dùng compile-time hoặc load-time weaving.

**`src/main/java/demo/GreetingService.java`**

```java
package demo;

public interface GreetingService {
    String greet(String name);
    String ping();
}
```

**`src/main/java/demo/GreetingServiceImpl.java`**

```java
package demo;

public class GreetingServiceImpl implements GreetingService {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }

    @Override
    public String ping() {
        return "pong";
    }
}
```

**`src/main/java/demo/App.java`**

```java
package demo;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public class App {
    public static void main(String[] args) throws Exception {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(* demo.GreetingServiceImpl.greet(..))");

        // Kiểm tra khả năng khớp trước khi tạo proxy.
        Method greet = GreetingServiceImpl.class.getMethod("greet", String.class);
        Method ping = GreetingServiceImpl.class.getMethod("ping");
        System.out.println("match greet: " +
                pointcut.getMethodMatcher().matches(greet, GreetingServiceImpl.class));
        System.out.println("match ping: " +
                pointcut.getMethodMatcher().matches(ping, GreetingServiceImpl.class));

        MethodInterceptor advice = invocation -> {
            System.out.println("[advice] before " + invocation.getMethod().getName());
            Object result = invocation.proceed();
            System.out.println("[advice] after " + invocation.getMethod().getName());
            return result;
        };

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);

        ProxyFactory factory = new ProxyFactory(new GreetingServiceImpl());
        factory.addInterface(GreetingService.class);
        factory.addAdvisor(advisor);

        GreetingService proxy = (GreetingService) factory.getProxy();
        System.out.println(proxy.greet("An"));
        System.out.println(proxy.ping());
    }
}
```

Trong thư mục `spring-pointcut-api`, chạy bằng JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

**Stdout của phần Java (không tính thông báo riêng của công cụ build):**

```text
match greet: true
match ping: false
[advice] before greet
[advice] after greet
Hello, An
pong
```

Giải thích tuần tự:

1. `setExpression(...)` xác định method có tên `greet` trên `demo.GreetingServiceImpl`, với kiểu trả về và danh sách đối số bất kỳ.
2. Hai lệnh `matches(...)` thử trực tiếp trên method thực tế của target: `greet` khớp, `ping` không khớp.
3. `DefaultPointcutAdvisor` gắn rule với `MethodInterceptor`. Trong interceptor, `invocation.proceed()` thực thi bước tiếp theo trong interceptor chain hoặc method đích; kết quả trả về vẫn được chuyển ra ngoài.
4. `ProxyFactory` trả về proxy có interface `GreetingService`. Lời gọi `proxy.greet("An")` in hai thông báo advice; `proxy.ping()` không bị advisor này chặn nên chỉ trả về `pong`.

**Điểm đối chiếu:** gọi trực tiếp `new GreetingServiceImpl().greet("An")` sẽ không chạy advice vì lời gọi đó không qua Spring proxy. Lớp `GreetingServiceImpl` vẫn giữ nguyên mã nguồn business.

## 4. Cú pháp biểu thức khi truyền vào `setExpression(...)`

Các mẫu thông dụng cho **Spring AOP method execution**:

| Biểu thức | Ý nghĩa trong Spring AOP |
| --- | --- |
| `execution(* demo.GreetingServiceImpl.greet(..))` | Method `greet` của kiểu chỉ định, mọi kiểu trả về và số tham số. |
| `execution(public * demo..*Service.*(..))` | Public method trong các kiểu tên kết thúc bằng `Service` thuộc `demo` hoặc package con. |
| `within(demo.service..*)` | Giới hạn method theo nơi khai báo type trong package. |
| `execution(* demo.service..*(..)) && within(demo.service..*)` | Kết hợp điều kiện method và phạm vi type (điều chỉnh mẫu `execution` cho đúng hệ thống package thực tế). |
| `@annotation(demo.TrackCall)` | Method execution mang annotation `demo.TrackCall` (annotation phải hiện diện và dùng đúng target method). |
| `args(java.lang.String)` | Điều kiện theo **kiểu đối số runtime** của lời gọi một tham số. |
| `bean(*Service)` | Spring-specific designator lựa chọn Spring bean theo tên; dùng khi pointcut có ngữ cảnh BeanFactory/bean name phù hợp. |

`execution` có cấu trúc rút gọn `execution(modifiers? return-type declaring-type.method(arguments))`. Dấu `*` là wildcard; `(..)` là danh sách đối số dài bất kỳ; `()` nghĩa không có đối số. Các toán tử `&&`, `||`, `!` kết hợp điều kiện bằng AND, OR, NOT. Với `&&` trong chuỗi Java, viết dấu `&&` bình thường; không cần escape dấu `&`.

Nên **thu hẹp phạm vi** theo package/type và method vì biểu thức quá rộng như `execution(* *(..))` có thể áp dụng advice ngoài dự kiến. Đối với điều kiện annotation, chú ý cách interface, target implementation và proxy ảnh hưởng đến vị trí annotation được nhìn thấy.

**Giới hạn Spring AOP:** không dùng `call(...)`, `get(...)`, `set(...)`, `initialization(...)` hay `cflow(...)` như thể Spring AOP đang weave bytecode. Các pointcut designator này không được proxy-based Spring AOP hỗ trợ; biểu thức có thể bị từ chối. `bean(...)` là tiện ích riêng của Spring, vì vậy với `ProxyFactory` thủ công như ví dụ trên, nên ưu tiên biểu thức dựa trên method/type; việc match theo bean name cần ngữ cảnh Spring bean tương ứng.

## 5. Đọc trực tiếp `ClassFilter` và `MethodMatcher`

`AspectJExpressionPointcut` hiện thực `Pointcut`, do đó hai phép thử tiêu chuẩn là:

```java
boolean classMayMatch = pointcut.getClassFilter()
        .matches(GreetingServiceImpl.class);

Method method = GreetingServiceImpl.class
        .getMethod("greet", String.class);

boolean methodMayMatch = pointcut.getMethodMatcher()
        .matches(method, GreetingServiceImpl.class);
```

Đây là **đoạn trích dùng trong ngữ cảnh `App.main` phía trên**, nơi biến `pointcut` đã tồn tại và `Method` đã được import.

- `getClassFilter().matches(targetClass)` giúp biết kiểu target có thể chứa join point thích hợp không.
- `getMethodMatcher().matches(method, targetClass)` là **kiểm tra tĩnh/khả năng khớp**. Trả về `true` có thể chỉ có nghĩa method *có thể* phù hợp ở một lời gọi thực tế nếu expression còn điều kiện động.
- `getMethodMatcher().isRuntime()` cho biết Spring có cần đánh giá thêm bằng `matches(method, targetClass, args)` tại từng lời gọi hay không. Với điều kiện phụ thuộc tham số thực như `args(...)`, cần để Spring thực hiện đủ hai pha; đừng tự coi `matches(method, targetClass) == true` là kết quả cuối cùng cho mọi bộ arguments.
- Khi thực tế áp dụng `Advisor`, **Spring AOP xử lý phần kiểm tra này trong interceptor chain**. Ứng dụng hiếm khi phải tự gọi `matches(...)`; cách gọi tường minh chủ yếu hữu ích khi học API, tự xây dựng advisor hoặc gỡ lỗi.

Nếu `matches(...)` trả về `false` khi bạn nghĩ là phải `true`, trước tiên hãy kiểm tra `Method` đang lấy từ interface hay implementation, lớp target thực tế, các modifier, package và kiểu đối số. Dùng `getMostSpecificMethod` thông qua tiện ích Spring khi việc chọn method cụ thể là quan trọng; tránh suy luận kết quả chỉ từ tên method.

## 6. Dùng trong Spring IoC và phân biệt với `@Aspect`

`ProxyFactory` trong ví dụ cho thấy **cấu hình proxy bằng lập trình**. Trong hệ thống dùng Spring IoC, cũng có thể khai báo `AspectJExpressionPointcut`/`Advisor` như bean, rồi đưa advisor vào cơ chế tạo proxy phù hợp (ví dụ auto-proxy creator được cấu hình riêng). Không phải cứ khai báo một `Pointcut` bean hoặc `Advisor` bean bất kỳ là mọi bean khác tự động bị chặn trong tất cả cách khởi tạo context; cần có proxying mechanism thực sự sử dụng advisor đó.

So sánh cách diễn đạt cùng ý tưởng:

```java
// Spring AOP API:
AspectJExpressionPointcut p = new AspectJExpressionPointcut();
p.setExpression("execution(* demo.GreetingServiceImpl.greet(..))");
// Sau đó ghép p với Advice và gắn Advisor vào proxy.
```

```java
// Cách khai báo @Aspect (minh họa cú pháp trong một lớp aspect riêng):
@Around("execution(* demo.GreetingServiceImpl.greet(..))")
public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    return joinPoint.proceed();
}
```

Đoạn `@Around` chỉ là **mảnh minh họa**, yêu cầu import, aspect class và cấu hình Spring AOP riêng để chạy. Cả hai phương án có thể dùng biểu thức kiểu AspectJ; khác nhau ở **cách cung cấp advice/pointcut cho Spring**. API phù hợp khi bạn tạo proxy có chọn lọc, viết cấu hình lập trình hoặc tích hợp infrastructure; `@Aspect` phù hợp khi muốn gom cross-cutting behavior trong lớp aspect được Spring quản lý.

Không nhầm `AspectJExpressionPointcut` với bytecode weaving. Dùng pointcut implementation này qua `ProxyFactory` vẫn tuân theo giới hạn của **Spring AOP proxy**.

## 7. Những lỗi thường gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Chạy ứng dụng báo thiếu lớp `org.aspectj.weaver...` hoặc lỗi khởi tạo biểu thức | Thiếu runtime AspectJ weaver dependency | Thêm `org.aspectj:aspectjweaver` tương thích vào runtime classpath. |
| Method có chạy nhưng advice không xuất hiện | Đang gọi trực tiếp target hoặc `this.method()` trong cùng target, bỏ qua proxy | Gọi qua bean/proxy do Spring cấp; điều chỉnh thiết kế lời gọi nếu cần. |
| Một method không khớp | Sai tên package/type/signature hoặc ký hiệu `()`/`(..)` | In thông tin class/method, dùng hai bước `ClassFilter` và `MethodMatcher` để chẩn đoán. |
| Method ở interface không được advice gọi | Lựa chọn loại proxy, kiểu method được gọi hoặc vị trí annotation khác giả định | Kiểm tra proxy JDK/interface so với class-based proxy và method thực của target. |
| Biểu thức `call(...)`/`get(...)` không được chấp nhận | Cần join point ngoài phạm vi Spring proxy AOP | Dùng `execution(...)` cho Spring AOP; nếu thật sự cần field/constructor/call join point thì đó là bài toán weaving khác. |
| Advice bao phủ quá nhiều method | `execution` quá rộng, không thu hẹp package/type | Tăng độ cụ thể của expression, dùng `within(...)` hoặc ghép điều kiện. |
| Điều kiện runtime không hoạt động như khi tự thử `matches` | Chỉ dùng phép thử method tĩnh, bỏ bước đánh giá arguments | Kiểm tra `isRuntime()` và sử dụng cơ chế `Advisor`/proxy của Spring cho lời gọi thực. |
| `bean(name)` không khớp trong ví dụ `ProxyFactory` thủ công | Không có ngữ cảnh Spring bean name tương ứng | Dùng expression theo type/method hoặc cấu hình pointcut qua bean-aware infrastructure thích hợp. |

### Checklist áp dụng

1. Xác định chính xác **target class** và **method execution** muốn chặn.
2. Tạo `AspectJExpressionPointcut`, gọi `setExpression(...)` trước khi dùng làm advisor.
3. Giới hạn expression theo package/type/method thay vì quét toàn bộ ứng dụng.
4. Ghép pointcut với advice thông qua `DefaultPointcutAdvisor` hoặc advisor phù hợp.
5. Bảo đảm lời gọi đi **qua Spring AOP proxy** và chọn kiểu proxy phù hợp.
6. Dùng `ClassFilter`/`MethodMatcher` để gỡ lỗi, nhớ đến điều kiện runtime nếu có.
7. Kiểm tra cả **method cần chặn** và **method không cần chặn** để tránh advice tràn phạm vi.

## 8. Tự kiểm tra

**Câu 1.** Giữ nguyên ví dụ, nếu thay expression bằng `execution(* demo.GreetingServiceImpl.*(..))` thì chuyện gì xảy ra?

**Đáp án:** Cả `greet` và `ping` của implementation nằm trong phạm vi; lời gọi qua proxy đến hai method đều được advisor xem xét và được advice chặn.

**Câu 2.** Vì sao `new GreetingServiceImpl().greet("An")` không in `[advice]`?

**Đáp án:** Đây là lời gọi vào target object trực tiếp, không thông qua đối tượng proxy chứa advisor của Spring AOP.

**Câu 3.** Tại sao phép thử `matches(method, targetClass)` có thể chưa đủ khi biểu thức có `args(...)`?

**Đáp án:** Kết quả static cho biết method *có khả năng khớp*. Khi `MethodMatcher.isRuntime()` là `true`, Spring còn cần kiểm tra các argument của **lần gọi hiện tại** bằng bản `matches` nhận `args`.

### Tham khảo chính thức

- Spring Framework — Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework — Spring AOP pointcut expressions: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Javadoc — `AspectJExpressionPointcut`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/aspectj/AspectJExpressionPointcut.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-007">ComposablePointcut và hợp, giao các điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# ComposablePointcut trong Spring AOP: hợp và giao các điều kiện

## 1. Mục tiêu và phạm vi

Trong **Spring AOP**, `org.springframework.aop.support.ComposablePointcut` giúp kết hợp các điều kiện chọn phương thức cần áp dụng `Advice` bằng phép **hợp** (`union`, OR) và **giao** (`intersection`, AND). Đây là API Java của Spring để tạo `Pointcut` bằng lập trình, đặc biệt hữu ích khi đã có các `ClassFilter`, `MethodMatcher` hoặc `Pointcut` tái sử dụng được.

Sau bài này, bạn có thể xây dựng điều kiện “lớp thuộc phạm vi A **và** phương thức có tên X **hoặc** Y”, phân biệt ghép theo từng thành phần với ghép hai `Pointcut` hoàn chỉnh, gắn kết quả với `Advisor`/`ProxyFactory`, và tránh mở rộng phạm vi interception ngoài ý muốn.

Bài tập trung vào **Spring AOP proxy-based** và API `spring-aop`; không bàn lại AOP tổng quát hoặc cú pháp pointcut của AspectJ.

## 2. Một Pointcut trong Spring được đánh giá như thế nào?

`Pointcut` có hai thành phần:

- `ClassFilter`: kiểm tra **lớp target** bằng `matches(Class<?> targetClass)`.
- `MethodMatcher`: kiểm tra **phương thức** bằng `matches(Method method, Class<?> targetClass)`; nếu `isRuntime() == true` thì Spring còn xét `matches(Method method, Class<?> targetClass, Object... args)` ở thời điểm gọi.

Với một `Pointcut` thông thường, điều kiện phù hợp có dạng:

```text
MATCH(pointcut, targetClass, method, args)
  = ClassFilter.matches(targetClass)
    AND MethodMatcher.matches(method, targetClass[, args nếu runtime])
```

Hai thành phần kiểm tra hai khía cạnh khác nhau: điều kiện lớp không thay thế điều kiện phương thức. `ComposablePointcut` triển khai `Pointcut`, cung cấp `getClassFilter()` và `getMethodMatcher()`, đồng thời cho phép cập nhật hai thành phần qua những phép toán kết hợp.

**Lưu ý:** điều kiện `MethodMatcher` có thể là tĩnh hoặc động. Kết hợp điều kiện động có thể khiến Spring phải kiểm tra đối số mỗi lần gọi. Khi chỉ cần tên, chữ ký hoặc lớp target, nên dùng matcher tĩnh để giảm chi phí.

## 3. API của ComposablePointcut

Các constructor thường dùng:

```java
new ComposablePointcut();                         // ClassFilter.TRUE + MethodMatcher.TRUE
new ComposablePointcut(classFilter);               // giới hạn lớp; mọi phương thức
new ComposablePointcut(methodMatcher);             // mọi lớp; giới hạn phương thức
new ComposablePointcut(classFilter, methodMatcher);// giới hạn cả hai
new ComposablePointcut(existingPointcut);          // sao chép cấu hình pointcut ban đầu
```

Các phép kết hợp đều trả lại **chính đối tượng `ComposablePointcut` đang được chỉnh sửa**, nên có thể nối chuỗi:

| Phương thức | Phạm vi tác động | Ý nghĩa |
| --- | --- | --- |
| `union(ClassFilter)` | Điều kiện **lớp** | `classFilter = classFilter OR other` |
| `intersection(ClassFilter)` | Điều kiện **lớp** | `classFilter = classFilter AND other` |
| `union(MethodMatcher)` | Điều kiện **phương thức** | `methodMatcher = methodMatcher OR other` |
| `intersection(MethodMatcher)` | Điều kiện **phương thức** | `methodMatcher = methodMatcher AND other` |
| `union(Pointcut)` | **Toàn bộ pointcut** | Khớp nếu pointcut A **hoặc** B khớp |
| `intersection(Pointcut)` | **Toàn bộ pointcut** | Khớp nếu pointcut A **và** B khớp |

Quy tắc để ghi nhớ: khi truyền `ClassFilter` hoặc `MethodMatcher`, Spring chỉ ghép **thành phần được truyền**; khi truyền `Pointcut`, Spring ghép **cặp điều kiện lớp–phương thức** của từng nhánh. Vì vậy `union(Pointcut)` bảo toàn sự gắn kết giữa `ClassFilter` và `MethodMatcher` của từng pointcut gốc.

Giả sử `C` là lớp thuộc `OrderService`, `M1` là tên `save`, `M2` là tên `cancel`:

```text
C AND (M1 OR M2)  →  trong OrderService, áp dụng cho save hoặc cancel
C AND (M1 AND M2) →  chỉ phương thức thỏa cả hai matcher
(C1 AND M1) OR (C2 AND M2) →  hai nhánh Pointcut độc lập
```

**Bẫy khởi tạo:** `new ComposablePointcut()` bắt đầu từ `ClassFilter.TRUE` và `MethodMatcher.TRUE`, nên `union(classFilter)` trên thành phần đang là `TRUE` vẫn là `TRUE`. Nếu mục tiêu là thu hẹp từ điều kiện luôn đúng, hãy dùng `intersection(...)`; hoặc khởi tạo ngay bằng điều kiện đầu tiên rồi mới `union(...)`.

## 4. Ví dụ chạy được: hợp tên phương thức rồi giao chữ ký

Yêu cầu: chỉ intercept method của `OrderService` nếu tên là `save`, `cancel` hoặc `list`, **đồng thời** method phải có đúng một tham số `String`. Do đó chỉ `save(String)` và `cancel(String)` nhận advice; `list()` không nhận dù tên có trong phép hợp.

Ví dụ dùng **Java 17+** và **Spring Framework 6.2.x**, không cần khởi động Spring Boot. Tạo hai file sau.

**`build.gradle`** (Gradle project độc lập):

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-aop:6.2.14'
}

application {
    mainClass = 'PointcutCompositionDemo'
}
```

**`src/main/java/PointcutCompositionDemo.java`**:

```java
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.aop.support.RootClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class PointcutCompositionDemo {

    public interface OrderOperations {
        void save(String orderId);
        void cancel(String orderId);
        void list();
    }

    public static final class OrderService implements OrderOperations {
        @Override
        public void save(String orderId) {
            System.out.println("[service] save " + orderId);
        }

        @Override
        public void cancel(String orderId) {
            System.out.println("[service] cancel " + orderId);
        }

        @Override
        public void list() {
            System.out.println("[service] list");
        }
    }

    public static void main(String[] args) {
        NameMatchMethodPointcut save = new NameMatchMethodPointcut();
        save.setMappedName("save");

        NameMatchMethodPointcut cancelOrList = new NameMatchMethodPointcut();
        cancelOrList.setMappedNames("cancel", "list");

        MethodMatcher oneStringArgument = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class;
            }
        };

        ComposablePointcut pointcut = new ComposablePointcut(
                new RootClassFilter(OrderService.class),
                (MethodMatcher) save)
                .union((MethodMatcher) cancelOrList)
                .intersection(oneStringArgument);

        MethodInterceptor advice = invocation -> {
            System.out.println("[intercept] " + invocation.getMethod().getName());
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory(new OrderService());
        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));

        OrderOperations proxy = (OrderOperations) factory.getProxy();
        proxy.save("A-01");
        proxy.cancel("A-01");
        proxy.list();
    }
}
```

Tại thư mục project, chạy `gradle run` (hoặc `./gradlew run` nếu project đã có Gradle wrapper). Những dòng do chương trình in ra, theo đúng thứ tự:

```text
[intercept] save
[service] save A-01
[intercept] cancel
[service] cancel A-01
[service] list
```

Phân tích từng bước:

1. `RootClassFilter(OrderService.class)` chỉ chọn target thuộc `OrderService` hoặc lớp con phù hợp.
2. `save` và `cancelOrList` là `NameMatchMethodPointcut` của Spring. Phép `union((MethodMatcher) cancelOrList)` tạo điều kiện tên `save OR cancel OR list`, **không nới rộng điều kiện lớp**.
3. `intersection(oneStringArgument)` thêm điều kiện chữ ký: đúng một tham số `String`. `list()` bị loại tại đây.
4. `DefaultPointcutAdvisor` gắn pointcut với `MethodInterceptor`, và `ProxyFactory` tạo proxy thực hiện advice trước thân phương thức khớp.
5. `NameMatchMethodPointcut` cũng triển khai `Pointcut` và `MethodMatcher`; vì có hai overload cùng tên `union(Pointcut)` và `union(MethodMatcher)`, cast về `MethodMatcher` trong ví dụ là **cố ý** để chọn phép hợp riêng chiều phương thức.

Đây là ví dụ về **Spring AOP method execution**. Nó không lựa chọn call-site tùy ý hoặc sửa bytecode của đối tượng target.

## 5. Ghép hai Pointcut hoàn chỉnh: không được tráo điều kiện giữa các nhánh

Giả sử cần intercept `save` trên `OrderService` **hoặc** `refund` trên `BillingService`. Ký hiệu:

```text
P1 = (class is OrderService) AND (method is save)
P2 = (class is BillingService) AND (method is refund)
P1 union P2 = (OrderService AND save) OR (BillingService AND refund)
```

Bạn có thể xây từng `Pointcut` riêng rồi hợp:

```java
// Đoạn minh họa: OrderService và BillingService là các class đã có.
NameMatchMethodPointcut save = new NameMatchMethodPointcut();
save.setMappedName("save");

NameMatchMethodPointcut refund = new NameMatchMethodPointcut();
refund.setMappedName("refund");

org.springframework.aop.Pointcut orderSave =
        new ComposablePointcut(new RootClassFilter(OrderService.class),
                               (MethodMatcher) save);
org.springframework.aop.Pointcut billingRefund =
        new ComposablePointcut(new RootClassFilter(BillingService.class),
                               (MethodMatcher) refund);

org.springframework.aop.Pointcut either =
        new ComposablePointcut(orderSave).union(billingRefund);
```

Đoạn này minh họa **API ghép**, cần khai báo lớp `BillingService` nếu đưa vào chương trình độc lập. Khi `union(Pointcut)`, Spring giữ đúng sự ghép cặp của từng nhánh: method `refund` trong `OrderService` **không tự động** khớp, và method `save` trong `BillingService` cũng vậy.

Cách ghép dưới đây dễ sai về ngữ nghĩa:

```text
(ClassFilter(OrderService) OR ClassFilter(BillingService))
AND
(MethodMatcher(save) OR MethodMatcher(refund))
```

Biểu thức trên tạo **bốn** tổ hợp, kể cả `OrderService.refund` và `BillingService.save` nếu tồn tại. Trong khi hợp **hai Pointcut** chỉ cho phép hai tổ hợp ban đầu. Sự khác nhau này đặc biệt quan trọng khi các lớp có những method trùng tên.

Tương tự, `intersection(Pointcut)` chỉ phù hợp khi một method thực sự thỏa **cả hai pointcut**, bao gồm điều kiện lớp và phương thức của mỗi bên. Hai điều kiện lớp không thể cùng đúng sẽ dẫn đến một giao rỗng.

Nếu muốn kết quả mới tách biệt với pointcut ban đầu, tạo `new ComposablePointcut(existingPointcut)` trước khi tiếp tục ghép, thay vì chỉnh trực tiếp biến `ComposablePointcut` đang được nơi khác sử dụng.

## 6. Phân biệt AND/OR theo tầng

| Yêu cầu trong Spring AOP | Cách ghép phù hợp | Tập method được chọn |
| --- | --- | --- |
| Trên lớp A **và** tên bắt đầu bằng `save` | `new ComposablePointcut(classFilterA, methodMatcherSave)` | Chỉ method `save*` của A |
| Trên lớp A, tên là `save` **hoặc** `cancel` | khởi tạo với `classFilterA` và `save`, sau đó `union((MethodMatcher) cancel)` | Hai nhóm tên trên A |
| Trên A, tên là `save` **và** có chữ ký hợp lệ | `intersection(signatureMatcher)` | Phần giao của hai điều kiện method |
| `A.save` **hoặc** `B.refund` | `union(Pointcut)` giữa hai pointcut độc lập | Giữ đúng cặp lớp/method |
| Thêm cả lớp B nhưng **giữ nguyên** điều kiện tên | `union((ClassFilter) classFilterB)` | Cùng các tên đã chọn trên A hoặc B |
| Phải thỏa hai bộ ràng buộc đầy đủ | `intersection(Pointcut)` | Các lời gọi khớp cả hai pointcut |

Tổng quát, với `C` là điều kiện lớp và `M` là điều kiện method:

- `union(MethodMatcher N)`: `C AND (M OR N)`.
- `intersection(MethodMatcher N)`: `C AND (M AND N)`.
- `union(ClassFilter D)`: `(C OR D) AND M`.
- `intersection(ClassFilter D)`: `(C AND D) AND M`.
- `union(Pointcut(C2, M2))`: `(C AND M) OR (C2 AND M2)`.
- `intersection(Pointcut(C2, M2))`: `(C AND M) AND (C2 AND M2)`.

## 7. Khi sử dụng trong ứng dụng Spring

`ComposablePointcut` phù hợp khi cấu hình advice theo API Spring AOP thuần, ví dụ tạo `DefaultPointcutAdvisor`, đăng ký advisor cho proxy, hoặc xây điều kiện lựa chọn method từ các đối tượng `ClassFilter`/`MethodMatcher` tái sử dụng. Với một ứng dụng sử dụng Spring container, cùng `Pointcut` có thể được gắn vào advisor bean để advisor được AOP auto-proxy infrastructure nhận diện theo cấu hình thích hợp.

Nếu dự án chủ yếu khai báo `@Aspect` và `@Pointcut` với biểu thức AspectJ, biểu thức `execution(...)` kết hợp `&&`/`||` thường gọn hơn. `ComposablePointcut` là **API programmatic của Spring AOP**, không phải chuỗi biểu thức `@Pointcut`, cũng không tự kích hoạt proxy hay advice: vẫn cần một advisor và cơ chế tạo proxy/auto-proxy.

### Lưu ý khi debug

- **Dùng sai overload:** `NameMatchMethodPointcut` đồng thời là `Pointcut` và `MethodMatcher`. Hãy ép kiểu đúng ý định hoặc gán qua biến có kiểu `MethodMatcher`/`Pointcut` trước khi gọi `union` hoặc `intersection`.
- **Hợp từ TRUE:** `new ComposablePointcut().union(filter)` không lọc được như mong đợi vì `TRUE OR filter = TRUE`. Khởi tạo bằng filter hoặc dùng `intersection`.
- **Ghép nhầm thành phần:** nếu muốn hai cặp lớp–method riêng, dùng `union(Pointcut)`; tách ra `union(ClassFilter)` và `union(MethodMatcher)` có thể chọn thừa method.
- **Nhầm tĩnh và động:** `MethodMatcher.isRuntime() == false` không xét đối số thực tế. Muốn lọc theo giá trị đối số tại runtime, phải dùng matcher động có logic tương ứng, có chi phí kiểm tra khi gọi.
- **Tự gọi nội bộ:** `this.save(...)` từ method khác cùng target không đi qua Spring AOP proxy, nên advice có thể không chạy dù pointcut đúng. Đây là giới hạn của proxy-based Spring AOP.
- **Method không intercept được:** private method, final method khi dùng class-based proxy, hoặc method không được phơi ra qua interface của JDK proxy có thể không được intercept theo cách kỳ vọng. Kiểm tra loại proxy và method thực sự gọi.
- **Thay matcher sau khi chạy:** cấu hình `NameMatchMethodPointcut` trước lúc proxy được sử dụng, vì chuỗi interceptor/advice có thể đã được cache. Tạo cấu hình hoàn chỉnh trước khi xuất bản proxy.
- **Pointcut đổi theo ngữ cảnh:** `ComposablePointcut` là đối tượng có thể thay đổi thông qua các phép ghép. Tránh để nhiều nơi tùy ý chỉnh sửa cùng một instance sau khi đã cấu hình advisor.

## 8. Tự kiểm tra

**Câu 1.** Điểm khác nhau giữa `union((MethodMatcher) matcherB)` và `union((Pointcut) pointcutB)` trên một `ComposablePointcut` là gì?

**Đáp án:** cách thứ nhất chỉ OR điều kiện phương thức, giữ `ClassFilter` hiện tại; cách thứ hai OR hai bộ ràng buộc class–method hoàn chỉnh và không phối sai matcher của nhánh B với class filter của nhánh A.

**Câu 2.** Tại sao `new ComposablePointcut().union(myMatcher)` không giới hạn method theo `myMatcher`?

**Đáp án:** `MethodMatcher` mặc định đã là `TRUE`; `TRUE OR myMatcher` vẫn là `TRUE`. Dùng `new ComposablePointcut(myMatcher)` hoặc `new ComposablePointcut().intersection(myMatcher)`.

**Câu 3.** `OrderService.save(String)` được intercept còn `OrderService.list()` thì không, mặc dù cả hai tên đã thuộc phép `union` trong ví dụ. Vì sao?

**Đáp án:** phép `intersection(oneStringArgument)` yêu cầu mỗi phương thức có đúng một tham số `String`; `list()` không đáp ứng.

## Tài liệu chính thức

- [Spring Framework Reference — Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html).
- [Spring Framework Javadoc — ComposablePointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/ComposablePointcut.html).
- [Spring Framework Javadoc — NameMatchMethodPointcut](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/NameMatchMethodPointcut.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-008">Pointcut.TRUE và việc áp dụng rộng có chủ đích</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: `Pointcut.TRUE` và việc áp dụng rộng có chủ đích

## Mục tiêu học tập

Sau bài này, bạn có thể giải thích chính xác `org.springframework.aop.Pointcut.TRUE` khớp những gì, dùng nó trong `Advisor`/`ProxyFactory`, phân biệt “khớp mọi phương thức” với “can thiệp mọi lời gọi trong ứng dụng”, và quyết định khi nào một pointcut rộng là lựa chọn có kiểm soát.

## 1. `Pointcut.TRUE` là gì?

`Pointcut.TRUE` là hằng `static final Pointcut` có sẵn của Spring AOP, đại diện cho pointcut **luôn khớp**. Interface `org.springframework.aop.Pointcut` có hai thành phần:

- `getClassFilter()`: kiểm tra một *target class* có thuộc phạm vi hay không.
- `getMethodMatcher()`: kiểm tra một phương thức trên target class đó có khớp hay không.

Với `Pointcut.TRUE`, cả hai đều không áp điều kiện chọn lọc: class filter khớp các lớp được xét và method matcher khớp các phương thức được xét. Method matcher là loại tĩnh (`isRuntime() == false`), nên không cần đánh giá lại điều kiện theo đối số mỗi lần gọi.

```java
import org.springframework.aop.Pointcut;

Pointcut allMethods = Pointcut.TRUE;
boolean matchesClass = allMethods.getClassFilter().matches(MyService.class);
boolean matchesMethod = allMethods.getMethodMatcher()
        .matches(MyService.class.getMethod("run"), MyService.class);
// Trong điều kiện MyService có public method run(): cả hai biến đều true.
```

Đoạn trên minh họa API, **không phải chương trình độc lập**: `MyService` là lớp giả định có phương thức `public void run()`. `Pointcut.TRUE` thuộc **Spring AOP API**; đây không phải biểu thức `@Pointcut("execution(...)")` của cú pháp AspectJ. Không cần viết `execution(* *(..))` để có hằng này.

Điểm cốt lõi: “khớp tất cả” là kết quả của **quy tắc chọn class/method trong một advisor**, chưa phải mệnh lệnh cho Spring tự tạo proxy cho mọi đối tượng đang tồn tại.

## 2. Ba tầng quyết định advice có thực thi hay không

Hãy đọc chuỗi xử lý theo thứ tự:

1. **Chọn đối tượng được gắn AOP**: cấu hình chọn target/bean nào để tạo proxy (ví dụ, tạo `ProxyFactory` cho đúng một service hoặc cấu hình auto-proxy creator chọn một nhóm bean).
2. **Chọn advisor phù hợp**: trong những proxy đó, Spring xét `Pointcut` của advisor. `Pointcut.TRUE` không thu hẹp phạm vi class hay phương thức.
3. **Lời gọi thực sự qua proxy**: interceptor chỉ chạy khi lời gọi được proxy xử lý. Lời gọi trực tiếp trên target, đặc biệt `this.otherMethod()` nội bộ, đi vòng qua proxy.

Vì vậy, hai phát biểu sau **khác nhau**:

- “`Pointcut.TRUE` khớp mọi method được Spring AOP kiểm tra trong phạm vi advisor” → đúng.
- “Thêm `Pointcut.TRUE` thì mọi phương thức trong JVM đều tự động bị intercept” → sai.

Spring AOP hỗ trợ *method-execution join point* và hoạt động chủ yếu bằng **JDK dynamic proxy** hoặc **CGLIB proxy**. Với JDK proxy, lời gọi qua các interface được proxy là phạm vi chính. Với CGLIB, các hạn chế như phương thức `final`, `private` và lớp `final` ảnh hưởng khả năng can thiệp. Constructor không phải join point được advice theo kiểu Spring AOP proxy. `Pointcut.TRUE` không gỡ bỏ những giới hạn này.

## 3. Ví dụ chạy được: một service, hai phương thức đều được intercept

Mục đích của ví dụ là **chủ động giới hạn target xuống đúng một đối tượng `BillingServiceImpl`**, rồi cho advice chạy trên cả hai phương thức được gọi qua proxy. Tạo dự án Maven có cấu trúc:

```text
pointcut-true-demo/
├── pom.xml
└── src/main/java/example/PointcutTrueDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>pointcut-true-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>example.PointcutTrueDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/example/PointcutTrueDemo.java`:

```java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public class PointcutTrueDemo {

    public interface BillingService {
        String charge(String account);
        int refund(int amount);
    }

    public static class BillingServiceImpl implements BillingService {
        @Override
        public String charge(String account) {
            return "charged " + account;
        }

        @Override
        public int refund(int amount) {
            return amount / 2;
        }
    }

    public static void main(String[] args) {
        MethodInterceptor trace = invocation -> {
            String name = invocation.getMethod().getName();
            System.out.println("before: " + name);
            Object result = invocation.proceed();
            System.out.println("after: " + name);
            return result;
        };

        ProxyFactory factory = new ProxyFactory(new BillingServiceImpl());
        factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, trace));

        BillingService proxy = (BillingService) factory.getProxy();
        System.out.println(proxy.charge("A01"));
        System.out.println(proxy.refund(12));
    }
}
```

Chạy tại thư mục chứa `pom.xml`, với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Đầu ra do chương trình in ra:

```text
before: charge
after: charge
charged A01
before: refund
after: refund
6
```

**Giải thích từng khâu:**

1. `new ProxyFactory(new BillingServiceImpl())` chọn **duy nhất một target**. Ở đây target triển khai `BillingService`, nên mặc định có thể dùng JDK dynamic proxy cho interface đó.
2. `new DefaultPointcutAdvisor(Pointcut.TRUE, trace)` gắn interceptor `trace` với pointcut luôn khớp.
3. `factory.addAdvisor(...)` đưa advisor vào chuỗi xử lý của proxy này. `getProxy()` trả đối tượng proxy.
4. Cả `charge` và `refund` được gọi **qua `proxy`**, nên cùng đi qua `trace`. `invocation.proceed()` tiếp tục chuỗi interceptor/target và giữ lại kết quả của nghiệp vụ.
5. Gọi `new BillingServiceImpl().charge("A01")` trực tiếp sẽ **không** in `before/after` vì không dùng proxy.

Ví dụ chỉ minh họa đường đi trả về bình thường: nếu `proceed()` ném ngoại lệ thì lệnh in `after` ở phía sau sẽ không chạy. Nếu muốn logic dọn dẹp luôn chạy, dùng `try/finally`; nếu muốn phân biệt thành công/thất bại, dùng `try/catch` thích hợp và giữ nguyên ngoại lệ theo chính sách đã định.

## 4. Khi nào “rộng” là thiết kế đúng?

Dùng `Pointcut.TRUE` khi **mọi lời gọi phương thức đủ điều kiện trên tập target đã chọn đều cần cùng một hành vi**:

| Tình huống | Ranh giới cần xác định | Điểm cần kiểm chứng |
| --- | --- | --- |
| Trace trong một client/service wrapper | Chỉ proxy một target cụ thể bằng `ProxyFactory` hoặc bean được chọn tường minh | Log có chạy trên các method dự kiến và không rò dữ liệu nhạy cảm? |
| Đo thời gian cho một nhóm adapter được chọn | Bean/target được chọn theo tên hoặc vai trò | Các method kỹ thuật, gọi thường xuyên có tạo quá nhiều số liệu? |
| Kiểm tra điều kiện chung trước mọi operation của một API nội bộ | Interface service được proxy và phạm vi bean | Các method đọc, health-check, hay hàm hỗ trợ có nên chịu cùng luật? |
| Dùng cùng interceptor cho toàn bộ method đã proxy trong bài kiểm thử | Proxy được dựng riêng cho đối tượng thử nghiệm | Có vô tình kiểm tra lời gọi trực tiếp trên target thay vì proxy? |

**Nguyên tắc:** hãy làm **phạm vi target hẹp trước**, rồi mới để pointcut rộng trong phạm vi đó. Khi chỉ vài method cần advice, hãy dùng pointcut lọc method thay vì `TRUE`.

Ví dụ, có thể thay đúng một dòng trong chương trình để chỉ intercept `charge`:

```java
// Thêm import: org.springframework.aop.support.NameMatchMethodPointcut
NameMatchMethodPointcut onlyCharge = new NameMatchMethodPointcut();
onlyCharge.setMappedName("charge");
factory.addAdvisor(new DefaultPointcutAdvisor(onlyCharge, trace));
```

Đây là **đoạn thay thế** cho lời gọi `factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, trace))`, không phải dòng cần thêm đồng thời. Khi dùng pointcut chọn lọc, `refund` vẫn chạy nghiệp vụ nhưng không có hai dòng trace. Khi yêu cầu phụ thuộc vào tên method, annotation, kiểu class hoặc signature, một `NameMatchMethodPointcut`, `AnnotationMatchingPointcut` hay `AspectJExpressionPointcut` thường phù hợp hơn; chọn theo đúng điều kiện cần diễn đạt.

## 5. Tích hợp với Spring container: tránh nhầm cấp phạm vi

Trong một ứng dụng Spring, khai báo `@Bean` trả về `DefaultPointcutAdvisor(Pointcut.TRUE, advice)` mới chỉ tạo **advisor bean**. Việc bean nghiệp vụ nào được proxy phụ thuộc vào **auto-proxy creator** và cấu hình đăng ký của ứng dụng.

Hai cách dùng có ý nghĩa khác nhau:

- **Proxy tường minh**: `ProxyFactory` chỉ bao lấy target mà bạn chỉ định. Đây là cách dễ chứng minh phạm vi nhất cho bài học và các wrapper độc lập.
- **Auto-proxy trong ApplicationContext**: một `DefaultAdvisorAutoProxyCreator` có thể tìm các advisor phù hợp rồi tạo proxy cho nhiều bean đủ điều kiện. Với `Pointcut.TRUE`, việc “phù hợp” dễ trở nên rất rộng vì pointcut không loại class/method nào. Cần xem chính xác auto-proxy creator, bean exclusions và các proxy đã tồn tại.

Nếu chỉ muốn vài bean, hãy dùng cơ chế chọn bean có chủ đích (chẳng hạn `BeanNameAutoProxyCreator` với danh sách tên bean, hoặc cấu hình proxy rõ ràng cho bean mong muốn) và kiểm tra hành vi thực tế trong context. Không nên cắm một advisor `TRUE` vào cơ chế quét toàn bộ context chỉ vì ví dụ trong `ProxyFactory` hoạt động đúng trên một target. Các loại hạ tầng AOP và cấu hình proxy khác nhau có thể tương tác, nên luôn xác minh proxy cuối cùng và chuỗi advisor thay vì suy diễn từ một dòng cấu hình.

Lưu ý `@Aspect`/`@Around` với biểu thức `execution(...)` thuộc phong cách annotation-based AOP và **không trực tiếp sử dụng hằng `Pointcut.TRUE`** như đầu vào của annotation `@Pointcut`. Nếu bạn cần kiểm soát bằng `Pointcut.TRUE`, hãy dùng API `Pointcut`/`Advisor` tương ứng.

## 6. Giới hạn thực tế và các lỗi dễ gặp

**1. “TRUE thì self-invocation cũng bị intercept”.** Sai. `proxy.outer()` có thể được advice, nhưng khi target triển khai `outer()` rồi gọi `this.inner()`, lời gọi `inner()` đó thường không đi qua proxy. Giải pháp ưu tiên là tổ chức các tương tác giữa các bean qua proxy phù hợp, thay vì trông cậy vào `TRUE`.

**2. “TRUE áp dụng lên cả object chưa được proxy”.** Sai. `new SomeService()` trong code không trở thành Spring AOP proxy chỉ vì context có advisor. Khởi tạo và sử dụng target đúng cơ chế quản lý/proxy đã chọn.

**3. “TRUE vượt được `private`, `final` và constructor”.** Sai. Đó là giới hạn của proxy, không phải lựa chọn của pointcut. Với JDK proxy, chú ý lời gọi qua interface; với CGLIB, các phương thức không override được không thể được advise theo cách này.

**4. “Thêm advice là an toàn vì chỉ logging”.** Advice chạy rộng có thể làm tăng chi phí, ghi quá nhiều log, phát tán token/thông tin nhạy cảm hoặc tạo vòng gọi lại nếu interceptor tự gọi service đang bị advice. Thiết kế trace/metric nhẹ, có chính sách lọc dữ liệu và kiểm tra tương tác với transaction/security/retry.

**5. “Interceptor gọi `proceed()` thế nào cũng được”.** `MethodInterceptor` quyết định việc tiếp tục chuỗi. Không gọi `proceed()` sẽ chặn nghiệp vụ; gọi nhiều lần có thể lặp tác dụng phụ. Với logging thông thường, gọi **một lần**, trả nguyên kết quả và không nuốt ngoại lệ.

**6. “Kiểm tra `Pointcut.TRUE` trả true là đủ để test AOP”.** Chưa đủ: kiểm tra class/method matcher chỉ chứng minh thuật toán khớp. Cần gọi thực sự qua proxy, xác minh số lượt advice, kết quả và lỗi, đồng thời có case gọi target trực tiếp/self-invocation để thấy ranh giới.

## 7. Checklist áp dụng có kiểm soát

1. **Xác định nhu cầu:** mọi method trên đúng nhóm target đã chọn có thật sự cần cùng advice?
2. **Xác định tập target:** đối tượng qua `ProxyFactory` hay bean nào trong `ApplicationContext` được auto-proxy creator xử lý?
3. **Xác định loại proxy và lời gọi:** JDK proxy/interface hay CGLIB; có self-invocation, final/private, lời gọi không đi qua proxy hay không?
4. **Đánh giá advice:** `proceed()`, ngoại lệ, chi phí và dữ liệu nhạy cảm; tránh áp dụng vô tình lên bean hạ tầng.
5. **Kiểm tra hành vi:** gọi qua proxy các method đại diện, assert số lượt intercept và giá trị trả về; kiểm tra cả method không kỳ vọng được intercept.
6. **Thu hẹp nếu cần:** nếu chỉ một phần method cần advice, thay `Pointcut.TRUE` bằng pointcut có điều kiện.

## 8. Câu hỏi tự kiểm tra

- Nếu `Pointcut.TRUE.getMethodMatcher()` khớp `inner()`, vì sao `this.inner()` vẫn có thể không chạy advice?
- Vì sao `DefaultPointcutAdvisor(Pointcut.TRUE, advice)` gắn vào một `ProxyFactory` có phạm vi khác với advisor `TRUE` được auto-proxy creator tìm thấy trong một application context?
- Nếu chỉ có `charge()` cần trace, giữa `Pointcut.TRUE` và `NameMatchMethodPointcut` bạn chọn gì, vì sao?
- Khi đổi từ JDK proxy sang CGLIB, những giới hạn nào vẫn còn?

## Tài liệu Spring chính thức

- [Spring Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): class filter, method matcher, phép kết hợp pointcut.
- [Spring ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): dựng AOP proxy bằng code.
- [Spring Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation và các giới hạn của proxy.
- [Spring Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): `MethodInterceptor` và `MethodInvocation`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-009">Kiểm thử pointcut độc lập trước khi gắn vào advisor</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử Pointcut độc lập trước khi gắn vào Advisor (Spring AOP)

## 1. Mục tiêu và phạm vi

Trước khi kết nối `Pointcut` với `Advice` qua `Advisor`, hãy kiểm tra trực tiếp **quy tắc chọn lớp và phương thức** của Pointcut Spring AOP. Đây là một bài kiểm thử hợp đồng lựa chọn join point: với cùng một tập lớp, phương thức và (nếu có) đối số, Pointcut phải cho biết rõ trường hợp nào khớp, trường hợp nào bị loại. Không cần khởi động Spring Boot, tạo Spring context, tạo proxy hay thực thi advice để thực hiện bước kiểm thử này.

Phạm vi chỉ xét **Spring AOP**, nơi Pointcut được dùng để chọn các lần thực thi phương thức có thể được Spring proxy chặn. Kiểm thử matching độc lập giúp khoanh vùng lỗi cấu hình trước khi đi tiếp đến `Advisor`/`ProxyFactory`; nó **không chứng minh** advice đã chạy trong ứng dụng thật.

Sau bài này, bạn có thể:

- Đọc `Pointcut.getClassFilter()` và `Pointcut.getMethodMatcher()` để kiểm thử riêng hai tầng điều kiện.
- Phân biệt kết quả `matches(Method, Class<?>)` (kiểm tra tĩnh) và `matches(Method, Class<?>, Object...)` (kiểm tra đối số ở runtime).
- Xây dựng ma trận ca kiểm thử *positive*/*negative* bao gồm lớp, tên phương thức, overload, chữ ký và đối số.
- Đọc kết quả kiểm thử để phân biệt lỗi do pointcut với lỗi do proxy/advisor.

## 2. Hợp đồng matching của Spring AOP

Một `org.springframework.aop.Pointcut` công bố hai thành phần:

```java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
```

Thứ tự suy luận khi tự kiểm tra một phương thức ứng viên:

1. `pointcut.getClassFilter().matches(targetClass)` phải đúng. `targetClass` là kiểu lớp **đích** dự kiến được Spring AOP áp dụng, không phải tự động là lớp của proxy.
2. `pointcut.getMethodMatcher().matches(method, targetClass)` phải đúng. `method` là `java.lang.reflect.Method` cụ thể, cùng lớp đích tương ứng.
3. Chỉ khi hai bước trên thành công **và** `methodMatcher.isRuntime()` trả về `true`, kiểm tra thêm `methodMatcher.matches(method, targetClass, args)` với đối số của một lời gọi cụ thể.

Có thể mô tả quy tắc ở mức API như sau:

```text
classMatches = classFilter.matches(targetClass)
staticMatches = classMatches && methodMatcher.matches(method, targetClass)
matchesCall = staticMatches &&
              (!methodMatcher.isRuntime()
               || methodMatcher.matches(method, targetClass, args))
```

Đây là cách lập *oracle* để kiểm thử, không phải bản sao đầy đủ thuật toán nội bộ chọn advisor của Spring. Spring có thể xử lý phương thức interface/implementation, bridge method, introduction và proxy theo ngữ cảnh thực tế. Với kiểm thử độc lập, luôn truyền lớp đích và phương thức đã chủ ý lựa chọn.

| API | Trả lời câu hỏi | Khi nào cần gọi? |
| --- | --- | --- |
| `ClassFilter.matches(Class<?>)` | Lớp đích có thuộc phạm vi Pointcut? | Mọi Pointcut |
| `MethodMatcher.matches(Method, Class<?>)` | Phương thức có thể khớp khi xét kiểu/chữ ký? | Sau khi lớp khớp |
| `MethodMatcher.isRuntime()` | Cần kiểm tra đối số cho từng lời gọi không? | Sau kiểm tra tĩnh |
| `MethodMatcher.matches(Method, Class<?>, Object...)` | Lời gọi với đối số hiện tại có khớp không? | Chỉ khi `isRuntime() == true` và bước tĩnh đã khớp |

Pointcut tĩnh (`isRuntime() == false`) không cần gọi biến thể ba tham số trong luồng matching; đây cũng là lựa chọn thường tiết kiệm hơn do kết quả tĩnh có thể được Spring tận dụng/cached. Pointcut động cần cân nhắc chi phí kiểm tra cho từng lời gọi.

## 3. Ví dụ kiểm thử độc lập, chạy bằng JUnit 5

Trong dự án Spring Boot đã có `spring-boot-starter-aop` và `spring-boot-starter-test`, có thể dùng dependencies Gradle sau (phiên bản được quản lý bởi BOM của dự án):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Nếu dự án chỉ dùng Spring Framework thuần, cần đưa `spring-aop` vào test classpath và JUnit Jupiter; dùng `aspectjweaver` khi kiểm thử `AspectJExpressionPointcut`. Đoạn `dependencies` trên là cấu hình để **thêm vào project Spring Boot hiện có**, không phải toàn bộ tập tin build độc lập.

Tạo `src/test/java/demo/aop/PointcutIndependentTest.java`:

```java
package demo.aop;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointcutIndependentTest {

    static class OrderService {
        public void place(String orderId) {}
        public void place(long orderId) {}
        public void cancel(String orderId) {}
        public void transfer(long amount, String beneficiary) {}
    }

    static class AuditService {
        public void place(String orderId) {}
    }

    // Chỉ chọn place(String) trên OrderService.
    static class PlaceOrderPointcut extends StaticMethodMatcherPointcut {
        PlaceOrderPointcut() {
            setClassFilter(OrderService.class::isAssignableFrom);
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("place")
                    && Arrays.equals(
                            method.getParameterTypes(),
                            new Class<?>[] {String.class});
        }
    }

    // Điều kiện tĩnh: transfer(long, String).
    // Điều kiện động: đối số amount >= 1_000.
    static class LargeTransferPointcut extends DynamicMethodMatcherPointcut {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return OrderService.class.isAssignableFrom(targetClass)
                    && method.getName().equals("transfer")
                    && Arrays.equals(
                            method.getParameterTypes(),
                            new Class<?>[] {long.class, String.class});
        }

        @Override
        public boolean matches(
                Method method, Class<?> targetClass, Object... args) {
            return args.length == 2
                    && args[0] instanceof Long amount
                    && amount >= 1_000L;
        }
    }

    private static boolean staticMatch(
            Pointcut pointcut, Class<?> targetClass, Method method) {
        return pointcut.getClassFilter().matches(targetClass)
                && pointcut.getMethodMatcher().matches(method, targetClass);
    }

    @Test
    void staticPointcutSelectsOnlyExpectedMethodAndClass() throws Exception {
        Pointcut pointcut = new PlaceOrderPointcut();

        Method placeString = OrderService.class.getMethod("place", String.class);
        Method placeLong = OrderService.class.getMethod("place", long.class);
        Method cancel = OrderService.class.getMethod("cancel", String.class);
        Method auditPlace = AuditService.class.getMethod("place", String.class);

        assertTrue(staticMatch(pointcut, OrderService.class, placeString));
        assertFalse(staticMatch(pointcut, OrderService.class, placeLong));
        assertFalse(staticMatch(pointcut, OrderService.class, cancel));
        assertFalse(staticMatch(pointcut, AuditService.class, auditPlace));
        assertFalse(pointcut.getMethodMatcher().isRuntime());
    }

    @Test
    void dynamicPointcutChecksArgumentsAfterStaticMatch() throws Exception {
        Pointcut pointcut = new LargeTransferPointcut();
        Method transfer = OrderService.class.getMethod(
                "transfer", long.class, String.class);
        Method cancel = OrderService.class.getMethod("cancel", String.class);

        assertTrue(staticMatch(pointcut, OrderService.class, transfer));
        assertFalse(staticMatch(pointcut, OrderService.class, cancel));

        MethodMatcher matcher = pointcut.getMethodMatcher();
        assertTrue(matcher.isRuntime());
        assertFalse(matcher.matches(transfer, OrderService.class, 999L, "A"));
        assertTrue(matcher.matches(transfer, OrderService.class, 1_000L, "A"));
        assertTrue(matcher.matches(transfer, OrderService.class, 2_000L, "B"));
    }
}
```

Chạy trong thư mục dự án đã cấu hình dependencies tương ứng:

```bash
./gradlew test --tests 'demo.aop.PointcutIndependentTest'
```

Trên Windows PowerShell, dùng `.\gradlew.bat test --tests 'demo.aop.PointcutIndependentTest'`. Khi hai test chạy thành công, Gradle báo `BUILD SUCCESSFUL` (và không có lỗi assertion). Ví dụ không in `stdout`; JUnit trả kết quả qua runner. Có thể đổi đường dẫn nguồn nếu Gradle project dùng layout khác.

**Tại sao test có giá trị?**

- `place(String)` trên `OrderService` là ca dương tính.
- `place(long)` chứng minh chỉ trùng *tên* phương thức chưa đủ.
- `cancel(String)` chứng minh tên phương thức khác phải bị loại.
- `AuditService.place(String)` chứng minh cần kiểm tra `ClassFilter` ngay cả khi tên và chữ ký giống nhau.
- `transfer(long, String)` có điều kiện tĩnh đúng, nhưng đối số `999L` bị loại còn `1_000L` được nhận. Dùng giá trị biên giúp phát hiện lỗi `>`/`>=`.
- Test gọi API Pointcut trực tiếp, không có `Advisor`, proxy, advice hay ApplicationContext. Nếu assertion sai, sửa điều kiện chọn phương thức/lớp trước.

**Chi tiết đáng chú ý:** `LargeTransferPointcut` dùng `DynamicMethodMatcherPointcut` để minh họa lọc đối số; `ClassFilter` của lớp này mặc định chấp nhận mọi lớp, nhưng `matches(Method, Class<?>)` còn kiểm tra kiểu đích `OrderService`. Khi triển khai thật, có thể bổ sung `getClassFilter()` để loại sớm lớp không liên quan. Khi gọi trực tiếp `matches` ba tham số trong unit test, phải bảo đảm bước tĩnh đã khớp; Spring chỉ thực hiện kiểm tra động sau khi qua bộ lọc tĩnh.

## 4. Kiểm thử Pointcut sẵn có và biểu thức AspectJ

Trong Spring AOP, quy trình kiểm thử vẫn giống nhau với các implementation:

- `NameMatchMethodPointcut`: kiểm tra tên phương thức (ví dụ `setMappedNames("place", "cancel")`). Chú ý nó không tự biểu diễn toàn bộ điều kiện business theo kiểu tham số.
- `AnnotationMatchingPointcut`: kiểm tra annotation trên class hoặc method; bổ sung ca có/không có annotation, annotation ở interface so với implementation theo cách cấu hình thực tế.
- `AspectJExpressionPointcut`: dùng `setExpression(...)` và cùng cặp `getClassFilter()/getMethodMatcher()`. Đảm bảo `aspectjweaver` nằm trên classpath (Spring Boot AOP starter thường cung cấp).

Ví dụ mẫu minh họa cho ứng dụng đã có lớp đích thật `demo.billing.InvoiceService` với phương thức public `pay(String)`:

```java
import java.lang.reflect.Method;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
pointcut.setExpression(
    "execution(* demo.billing.InvoiceService.pay(java.lang.String))");

Method method = InvoiceService.class.getMethod("pay", String.class);
boolean selected =
    pointcut.getClassFilter().matches(InvoiceService.class)
    && pointcut.getMethodMatcher().matches(method, InvoiceService.class);
// Có thể assertTrue(selected) trong test khi InvoiceService tồn tại.
```

Đây là **mảnh mã minh họa**, không phải file Java độc lập: cần import/khai báo `InvoiceService` thật trong cùng package phù hợp. Biểu thức nhắm chính xác lớp đích và chữ ký trên; nếu đổi package/tên, hãy cập nhật expression và test để tránh một Pointcut luôn false. Với biểu thức phức tạp, kiểm tra cả `isRuntime()` trước khi đánh giá matching có đối số.

Đối với expression, nên có ít nhất các ca: đúng package, sai package; đúng tên/sai tên; đúng chữ ký/sai overload; annotation có/không; lớp triển khai method của interface. Với `bean(...)` designator hoặc expression phụ thuộc Spring container, cần biết ngữ cảnh bean/proxy: không giả định kiểm thử bằng `Class` và `Method` đơn thuần luôn đủ để kết luận.

## 5. Ma trận test trước khi gắn Advisor

| Tình huống | Kỳ vọng | Nguyên nhân cần xác minh |
| --- | --- | --- |
| Đúng lớp, đúng phương thức, đúng chữ ký | `true` | Positive case chuẩn |
| Lớp ngoài phạm vi | `false` | `ClassFilter` không bị quá rộng |
| Cùng tên, khác overload | `false` nếu chỉ chọn một chữ ký | `MethodMatcher` so đúng parameter types |
| Phương thức khác trong cùng lớp | `false` | Không match ngoài ý muốn |
| Runtime matcher: đối số ngay dưới ngưỡng | `false` | Điều kiện động và giá trị biên |
| Runtime matcher: đối số ở/ngay trên ngưỡng | `true` | Kiểm tra điều kiện biên |
| `isRuntime() == false` | Không gọi kiểm tra động | Không đánh đồng matching tĩnh và động |
| Method khai báo ở interface, thực thi ở class | Tùy quy tắc đã thiết kế | Cần dùng đúng cặp `Method`/`targetClass` và kiểm tra triển khai |

Một test tốt nên đặt tên theo kết quả mong muốn (ví dụ `selectsOnlyPlaceStringOnOrderService`, `rejectsAmountBelowThreshold`) và có *negative assertions*. Test chỉ toàn `assertTrue` thường bỏ sót pointcut quá rộng.

## 6. Các lỗi thường gặp và cách chẩn đoán

1. **Chỉ gọi `getMethodMatcher().matches(...)`, bỏ `ClassFilter`**: method đúng tên có thể bị đánh dấu khớp dù lớp bị loại. Sửa helper test để bắt buộc kiểm tra cả hai tầng.
2. **Đưa `proxy.getClass()` vào `targetClass`**: JDK proxy/CGLIB có kiểu runtime khác class ứng dụng; khi unit test phạm vi class, dùng kiểu đích bạn đang kiểm chứng. Với proxy thực tế cần bài test tích hợp riêng.
3. **Lấy sai overload bằng reflection**: `getMethod("place", String.class)` và `getMethod("place", long.class)` là hai ứng viên khác nhau. Đừng chỉ so tên phương thức.
4. **Gọi matcher ba tham số ngay cả khi `isRuntime() == false`**: không mô phỏng đúng giao ước framework. Chỉ kiểm tra runtime với Pointcut động đã qua bước tĩnh.
5. **Không có `aspectjweaver` hoặc expression sai tên package**: `AspectJExpressionPointcut` có thể không parse/match như dự tính. Kiểm tra classpath, expression và fixture cụ thể.
6. **Hiểu nhầm `selected == true` là advice sẽ chạy**: chọn phương thức chỉ là một điều kiện. Việc method call có đi qua Spring proxy, advice/advisor có đăng ký đúng không và self-invocation có bypass proxy không là các câu hỏi cần kiểm thử ở tầng sau.
7. **Test chỉ ca trúng đích**: pointcut quá rộng có thể vượt test. Thêm trường hợp lớp/phương thức/overload bị loại.
8. **Nhầm annotation trên interface và implementation**: kết quả phụ thuộc chiến lược matching và metadata thực tế. Test cả hai khi dùng annotation-based pointcut; đừng suy luận chỉ từ tên annotation.

## 7. Quy trình làm việc đề xuất

1. Viết rõ *hợp đồng chọn phương thức*: target classes, method names, overloads, annotations, điều kiện đối số.
2. Tạo fixture lớp và `Method` trực tiếp; chạy test `ClassFilter` trước, sau đó `MethodMatcher` tĩnh.
3. Với matcher động, chỉ sau khi static match thành công mới kiểm tra `isRuntime()` và các giá trị đối số đại diện/biên.
4. Bổ sung các ca âm tính để bảo đảm Pointcut không chọn nhầm.
5. Khi tất cả test độc lập đạt, mới đưa Pointcut vào Advisor và kiểm tra riêng proxy/advice trong test tích hợp. Nếu test tích hợp lỗi nhưng matching độc lập đúng, tập trung điều tra wiring, loại proxy, đối tượng được Spring quản lý và đường đi của lời gọi.

**Tiêu chí hoàn thành:** Các assertion về class, method, overload và runtime args (nếu có) đều đúng; không cần tạo Advisor để chứng minh Pointcut chọn đúng ứng viên. Sau đó mới chuyển sang bước kiểm thử Advisor/proxy.

### Tài liệu Spring tham khảo

- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [`Pointcut` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/Pointcut.html)
- [`AspectJExpressionPointcut` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/aspectj/AspectJExpressionPointcut.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-api-010">Chi phí khớp động so với khớp có thể tính trước</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí khớp động so với khớp có thể tính trước trong Spring AOP Pointcut API

## 1. Tình huống và mục tiêu

Trong Spring AOP, một advice chỉ có thể can thiệp vào lời gọi phương thức nếu **pointcut** của advisor cho biết lời gọi đó phù hợp. Có hai loại quyết định thường bị nhầm:

- **Khớp tĩnh (static matching):** có thể quyết định dựa trên lớp đích và đặc điểm của phương thức, ví dụ tên, chữ ký, annotation gắn với method. Không cần biết giá trị argument của lần gọi cụ thể.
- **Khớp động (runtime/dynamic matching):** cùng một method có thể match ở lần gọi này nhưng không match ở lần gọi khác, ví dụ chỉ ghi log khi tham số `amount >= 100`. Spring phải kiểm tra điều kiện phụ thuộc argument ở **từng invocation cần xét**.

Đây là quyết định của cơ chế **Spring AOP proxy và Pointcut API**, diễn ra tại điểm method execution mà proxy intercept. Bài không mở rộng sang AspectJ weaving, field access, constructor interception hay những kiến thức AOP chung không cần thiết.

Ví dụ: dịch vụ `place(orderId, amount)` có thể được mọi lời gọi tư vấn bởi một static advisor, nhưng chỉ những đơn hàng có `amount >= 100` mới chạy thêm advisor động.

Câu hỏi hiệu năng không chỉ là “method `matches` mất bao nhiêu nanosecond?”. Cần biết **khi nào Spring kiểm tra**, **khi nào có thể dùng lại kết quả**, **bao nhiêu lời gọi phải chịu runtime check**, và **phần nào thực sự tốn chi phí**.

## 2. Cấu trúc Pointcut API trong Spring

`org.springframework.aop.Pointcut` kết hợp hai thành phần:

~~~java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
~~~

Đây là chữ ký rút gọn từ Spring API để giải thích, không phải mã người học cần tự định nghĩa trong project.

- `ClassFilter.matches(Class<?> clazz)` xét **target class** có nằm trong phạm vi không. Nếu không, các phương thức của class ấy không cần đi tiếp vào phần match method cho advisor này.
- `MethodMatcher.matches(Method method, Class<?> targetClass)` kiểm tra điều kiện theo **method và target class**, chưa có argument runtime.
- `MethodMatcher.isRuntime()` báo liệu điểm khớp tĩnh thành công còn cần kiểm tra theo argument của mỗi lần gọi hay không.
- `MethodMatcher.matches(Method method, Class<?> targetClass, Object... args)` kiểm tra động bằng **argument values** của invocation.

Chữ ký quan trọng của `MethodMatcher`:

~~~java
public interface MethodMatcher {
    boolean matches(Method method, Class<?> targetClass);

    boolean isRuntime();

    boolean matches(
        Method method,
        Class<?> targetClass,
        Object... args
    );
}
~~~

Hai khối ở trên là **API schematic**: để dùng thực tế, import từ `org.springframework.aop` và sử dụng lớp triển khai tương ứng của Spring. Không tạo interface trùng tên với API có sẵn.

Một `Advisor` nối `Pointcut` với `Advice`. Ví dụ khi dùng Spring AOP programmatic API, `DefaultPointcutAdvisor` có thể kết hợp một custom pointcut với `MethodInterceptor`. `ProxyFactory` có thể tạo Spring proxy cho target có advisor đó. Các annotation `@Aspect` và biểu thức `execution(...)` là một cách khác để Spring tạo advisor/pointcut, còn bài này đi trực tiếp vào **Pointcut API** để thấy chi phí matching.

## 3. Quy trình quyết định của Spring và nơi phát sinh chi phí

Mô hình logic, đơn giản hóa từ đường xử lý Spring AOP:

~~~text
Target class -> ClassFilter.matches(targetClass)
                     |
                 false -> không áp dụng advisor
                     |
                    true
                     v
Method -> MethodMatcher.matches(method, targetClass)
                     |
                 false -> không áp dụng advisor cho method
                     |
                    true
                     v
                isRuntime() ?
                 /         \
               false       true
                |           |
   Có thể dùng kết quả    Invocation tới proxy với args
   tĩnh / advisor chain       |
                |            v
                |     matches(method, targetClass, args)
                |         /               \
                |       false             true
                |         |                |
                v         v                v
             Advice     Bỏ qua         Chạy advice
                \         |                /
                 \        v               /
                    Target method
~~~

Sơ đồ mô tả đường quyết định, **không cam kết số lần gọi `matches` ở tầng nội bộ hay thời điểm cache cụ thể của mọi Spring version**. Spring có thể phân tích class/method lúc tạo proxy hoặc khi method được gọi lần đầu, tái sử dụng kết quả thích hợp qua cơ chế cache advisor chain. Cache có thể bị ảnh hưởng khi advisor/proxy configuration thay đổi. Vì vậy đừng biến câu “khớp tĩnh” thành giả định rằng `matches(Method, Class)` được gọi đúng một lần trong cả vòng đời ứng dụng.

Quan trọng hơn:

1. Nếu **ClassFilter** không match, advisor có thể được loại sớm trước bước matching method.
2. Nếu `matches(Method, Class)` trả `false`, Spring **không gọi** phép kiểm tra runtime cho method đó.
3. Nếu `matches(Method, Class)` trả `true` và `isRuntime()` là `false`, Spring không cần gọi overload `matches(..., args)`.
4. Nếu match tĩnh thành công và `isRuntime()` là `true`, phép kiểm tra runtime phải được thực hiện cho **mỗi invocation thực sự đi tới vị trí advisor đó trong interceptor chain**.
5. Advice đứng trước có thể trả về mà không `proceed()`, khiến interceptor phía sau không được đi tới; do đó câu “mỗi invocation” luôn gắn với việc invocation thực sự đi đến matcher trong chain.
6. Lời gọi bên trong cùng target qua `this.otherMethod()` thường **không đi qua Spring proxy**, nên cả static và dynamic advisor trên `otherMethod` không tự được kích hoạt.

Một điểm đáng chú ý: runtime matcher không nhất thiết chạy trước mọi advice. Javadoc Spring nêu phép match động được xét **ngay trước advice tương ứng**, sau khi các advice đứng trước đã chạy. Vì vậy nếu advice phía trước có quyền thay đổi invocation arguments, phép kiểm tra động có thể thấy trạng thái argument tại thời điểm đó.

## 4. So sánh bản chất và chi phí

| Tiêu chí | Khớp tĩnh | Khớp động |
| --- | --- | --- |
| Dữ liệu quyết định | Class/method metadata: tên, parameter types, modifiers, annotations... | Có thể thêm giá trị `Object... args` của invocation |
| `isRuntime()` | `false` | `true` |
| Overload `matches(Method, Class, Object...)` | Không chạy | Chạy khi static prefilter thành công và chain đi tới advisor |
| Có thể tái sử dụng kết quả giữa các lần gọi cùng method? | Có thể, vì điều kiện không phụ thuộc args | Không thể tái sử dụng **kết quả runtime** cho các argument khác nhau |
| Phần kiểm tra phải trả ở mỗi lần gọi | Không cần recheck dynamic condition; vẫn có proxy/advice invocation overhead | Thêm chi phí kiểm tra argument mỗi lần invocation liên quan |
| Ví dụ | Method tên `place` trong service; phương thức có `@Audited` | `amount >= 100`, flag trong argument, ngữ cảnh call stack |
| Nên ưu tiên | Khi tính đúng hoàn toàn từ metadata | Khi chính sách thực sự thay đổi giữa các invocations của cùng method |

**Khớp tĩnh không đồng nghĩa advice chạy một lần.** Nếu method match pointcut, advice tĩnh vẫn chạy với **từng lời gọi** đi qua proxy. Thành phần có thể tái sử dụng là **kết quả lựa chọn advisor cho method**, không phải một lần thực thi advice.

**Khớp động không đồng nghĩa luôn đắt nhất về tổng thời gian hệ thống.** Một điều kiện nhẹ như so sánh số nguyên có thể nhỏ hơn nhiều so với I/O/logging ở chính advice. Ngược lại, kiểm tra động có reflection, phân tích chuỗi phức tạp, truy vấn, allocation hoặc stack walking có thể tạo chi phí đáng kể khi tần suất gọi lớn.

Chi phí tổng còn gồm tạo proxy, dựng interceptor chain, invocation dispatch, đo/log, GC, độ phức tạp của advice, mức độ tối ưu JIT và loại method. Không gán một con số nanosecond hay tỷ lệ cố định nếu chưa benchmark trong điều kiện thực tế.

## 5. Hai lớp hỗ trợ trong Spring AOP

Spring cung cấp base class để tránh tự cài đặt đầy đủ `Pointcut`:

**`StaticMethodMatcherPointcut`**

~~~java
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public final class PlaceMethods extends StaticMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("place");
    }
}
~~~

Đoạn này minh họa một lớp custom pointcut độc lập. Nó chỉ cần override phương thức kiểm tra tĩnh; `isRuntime()` do superclass cung cấp giá trị `false`.

**`DynamicMethodMatcherPointcut`**

~~~java
import java.lang.reflect.Method;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;

public final class PremiumPlaces extends DynamicMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getName().equals("place");
    }

    @Override
    public boolean matches(
            Method method, Class<?> targetClass, Object... args) {
        return args.length == 2
                && args[1] instanceof Integer amount
                && amount >= 100;
    }
}
~~~

Ví dụ khai báo method `place(String orderId, int amount)`. Kiểm tra tĩnh cắt bỏ những method khác như `health()`. Chỉ sau đó Spring mới cần xét `args[1]` tại runtime.

**Lưu ý:** `DynamicMethodMatcherPointcut` kế thừa một lớp `DynamicMethodMatcher` có `isRuntime()` là `final` và trả `true`; không override nó để “tối ưu”. Nếu một điều kiện chỉ phụ thuộc metadata, hãy dùng lớp static thay vì tạo dynamic matcher rồi luôn trả cùng một kết quả.

Hai ví dụ Java ngắn trên là các class tự dùng được khi đặt trong project Spring phù hợp; ví dụ hoàn chỉnh có `main` nằm ở phần tiếp theo.

## 6. Ví dụ Java 17 chạy được: cùng method, hai loại matching

Ví dụ này dùng **Spring Framework 6.1.15**, Java 17 và Maven. Cả hai advisor đều nhắm tới `place`, nhưng advisor tĩnh áp dụng mọi đơn hàng, trong khi advisor động chỉ áp dụng đơn hàng `amount >= 100`.

Cấu trúc:

~~~text
spring-pointcut-match-cost/
├── pom.xml
└── src/main/java/demo/App.java
~~~

### 6.1. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-match-cost</artifactId>
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
                <version>3.13.0</version>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration><release>17</release></configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration><mainClass>demo.App</mainClass></configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.2. `src/main/java/demo/App.java`

~~~java
package demo;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class App {

    public interface OrderOperations {
        String place(String orderId, int amount);
        String health();
    }

    public static final class OrderService implements OrderOperations {
        @Override
        public String place(String orderId, int amount) {
            System.out.println("target: place " + orderId + " " + amount);
            return "accepted " + orderId;
        }

        @Override
        public String health() {
            System.out.println("target: health");
            return "UP";
        }
    }

    public static final class PlacePointcut
            extends StaticMethodMatcherPointcut {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getName().equals("place");
        }
    }

    public static final class PremiumPlacePointcut
            extends DynamicMethodMatcherPointcut {

        private final AtomicInteger runtimeChecks = new AtomicInteger();

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            // Bước lọc trước có thể quyết định từ method metadata.
            return method.getName().equals("place");
        }

        @Override
        public boolean matches(
                Method method, Class<?> targetClass, Object... args) {
            runtimeChecks.incrementAndGet();
            // Đây mới là điều kiện phụ thuộc dữ liệu của invocation.
            return args.length == 2
                    && args[1] instanceof Integer amount
                    && amount >= 100;
        }

        public int getRuntimeChecks() {
            return runtimeChecks.get();
        }
    }

    public static void main(String[] args) {
        var staticPointcut = new PlacePointcut();
        var dynamicPointcut = new PremiumPlacePointcut();

        MethodInterceptor staticAdvice = invocation -> {
            System.out.println("static advice: "
                    + invocation.getMethod().getName());
            return invocation.proceed();
        };

        MethodInterceptor premiumAdvice = invocation -> {
            System.out.println("dynamic advice: premium");
            return invocation.proceed();
        };

        // Giữ đúng thứ tự: static advice đứng ngoài, premium bên trong.
        var staticAdvisor =
                new DefaultPointcutAdvisor(staticPointcut, staticAdvice);
        var premiumAdvisor =
                new DefaultPointcutAdvisor(dynamicPointcut, premiumAdvice);
        staticAdvisor.setOrder(0);
        premiumAdvisor.setOrder(1);

        var factory = new ProxyFactory();
        factory.setTarget(new OrderService());
        factory.setInterfaces(OrderOperations.class);
        factory.setProxyTargetClass(false);
        factory.addAdvisor(staticAdvisor);
        factory.addAdvisor(premiumAdvisor);

        OrderOperations service = (OrderOperations) factory.getProxy();

        System.out.println("static isRuntime="
                + staticPointcut.getMethodMatcher().isRuntime());
        System.out.println("dynamic isRuntime="
                + dynamicPointcut.getMethodMatcher().isRuntime());

        System.out.println("-- small --");
        System.out.println("result=" + service.place("A-1", 40));

        System.out.println("-- premium --");
        System.out.println("result=" + service.place("B-2", 140));

        System.out.println("-- health --");
        System.out.println("health=" + service.health());

        System.out.println("dynamic runtime checks="
                + dynamicPointcut.getRuntimeChecks());
    }
}
~~~

Chạy tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Output do các lệnh `System.out.println` trong chương trình tạo ra:

~~~text
static isRuntime=false
dynamic isRuntime=true
-- small --
static advice: place
target: place A-1 40
result=accepted A-1
-- premium --
static advice: place
dynamic advice: premium
target: place B-2 140
result=accepted B-2
-- health --
target: health
health=UP
dynamic runtime checks=2
~~~

Đọc kết quả theo **thực thi qua proxy**:

1. `place("A-1", 40)`: điều kiện tĩnh match, static advice chạy. Điều kiện động được kiểm tra nhưng không match nên premium advice bị bỏ qua; target vẫn xử lý order.
2. `place("B-2", 140)`: static advice chạy, dynamic matcher đọc `args[1]` và trả `true`, premium advice chạy rồi mới tới target.
3. `health()`: bộ lọc tĩnh `matches(Method, Class)` của **cả hai pointcut** loại method này. Không có lời gọi nào tới runtime matcher của premium pointcut; target `health` vẫn chạy.
4. `dynamic runtime checks=2` do chỉ có hai lần gọi `place` được xét ở giai đoạn runtime. Có thể thấy advice premium chỉ chạy **một lần**, trong khi runtime condition phải được **kiểm tra cả hai lần**.
5. `getMethodMatcher().isRuntime()` phơi bày thuộc tính của matcher. Nó không phải một phương pháp đo thời gian; `true` báo Spring cần kiểm tra thêm runtime condition khi static prefilter match.

Ví dụ dùng `ProxyFactory` thay vì `@Aspect` để phơi bày trực tiếp Spring Pointcut API. Ứng dụng cấu hình XML hoặc `@Aspect` có thể tạo ra advisor chain cùng bản chất nhưng cách đăng ký khác. `OrderService` là final class trong ví dụ nhưng được truy cập qua JDK interface proxy, không đòi subclass target; điều này tránh nhầm cơ chế proxy.

**Không dựa vào số lần kiểm tra tĩnh cố định** khi chạy đoạn này. Spring có thể tính trước hoặc cache matching theo method/advisor chain; số lần kiểm tra tĩnh nội bộ không phải một contract để so sánh bằng `assertEquals(1)`. Con số runtime check của ví dụ là minh họa chính xác cho **hai invocation đi đến dynamic advisor** trong chương trình đơn giản này.

## 7. Khi nào phép kiểm tra được cache?

Một cache chỉ hợp lệ khi đầu vào có ý nghĩa quyết định **không thay đổi** giữa các invocations.

- Với `matches(Method, Class)`, `Method` và target class xác định được điều kiện dựa trên method metadata. Spring có cơ sở để tính một lần (tại thời điểm tạo proxy hoặc khi cần lần đầu) và tái sử dụng kết quả trong cấu hình tương ứng.
- Với `matches(Method, Class, args)`, các giá trị `args` là dữ liệu runtime. Đơn hàng 40 và đơn hàng 140 đi qua cùng method nhưng chỉ một đơn hàng thỏa điều kiện. Cache theo method sẽ cho kết quả sai.
- Nếu dynamic matcher tự đọc cấu hình mutable bên ngoài args hoặc thời điểm hiện tại, nó càng phải bảo đảm logic của lần gọi; không nên giả vờ `isRuntime() == false` để tiết kiệm một phép kiểm tra.
- Với target class/method khác, proxy khác hoặc advisor configuration bị thay đổi, Spring có thể xây lại/chọn lại chain. Không hứa hẹn “một lần cho toàn ứng dụng” hoặc “một lần mỗi Spring bean” như một quy tắc API.

Tuy nhiên, quyết định **có cần dynamic matcher hay không** vẫn có thể được chốt bằng static prefilter. Nhờ vậy, chỉ các method tiềm năng mới trả thêm chi phí theo argument.

### Vì sao static prefilter quan trọng với dynamic matcher?

Hãy tưởng tượng một service có 50 method nhưng chỉ `place` cần xét `amount`:

~~~java
@Override
public boolean matches(Method method, Class<?> targetClass) {
    return method.getName().equals("place");
}
~~~

Phương thức static này lọc 49 method khác khỏi runtime condition. Nếu để lớp `DynamicMethodMatcher` dùng static matcher mặc định match mọi method, Spring có thể phải xét argument cho các invocation không liên quan, hoặc runtime matcher phải tự kiểm tra `method.getName()` lặp lại. Bộ lọc tĩnh vừa giúp rõ phạm vi, vừa tránh các lookup/cast argument ngoài đối tượng áp dụng.

Trong ứng dụng lớn, có thể bổ sung `getClassFilter()` để giới hạn target class trước khi xét method:

~~~java
@Override
public org.springframework.aop.ClassFilter getClassFilter() {
    return clazz -> OrderOperations.class.isAssignableFrom(clazz);
}
~~~

Đây là **biến thể minh họa bổ sung** cho pointcut; class thực thi cần import `OrderOperations` đúng package và target phải implement interface đó. Không dùng class filter dựa vào dữ liệu request vì class filter cũng là quyết định theo type, không phải theo invocation arguments.

## 8. Không nhầm dynamic matching với chạy advice

Với một method có static prefilter true, dynamic advice có thể được **bỏ qua** khi argument không thỏa, nhưng **runtime matcher vẫn phải tính kết quả false**. Vì vậy:

~~~text
100.000 lần gọi place(...)
    -> 100.000 lần dynamic predicate check (nếu đều đi tới advisor)
    -> 2.000 lần match true
    -> 2.000 lần premium advice chạy
~~~

Đây là **ví dụ giả định về số lượng**, không phải benchmark hoặc số đo ứng dụng. Nếu 98% invocations trả false, ta vẫn phải trả chi phí dynamic predicate ở 100% invocation đến matcher. Ưu điểm nằm ở việc không chạy logic premium advice cho 98% còn lại. Giá trị thực tế phụ thuộc chi phí của predicate so với chi phí của advice mà nó tránh được.

Một mô hình sơ bộ khi so hai thiết kế:

~~~text
Static pointcut:
  per-call = proxy/chain dispatch + static advice (nếu method match)

Dynamic pointcut:
  per-call = proxy/chain dispatch + dynamic predicate check
             + (premium advice nếu runtime match)

Dynamic runtime cost trên N invocations:
  xấp xỉ N * cost(predicate)
  cộng chi phí advice của các lần match true
~~~

Công thức chỉ mang tính khái niệm, không tính JIT optimization, branch prediction, logging I/O, cache, allocation, contention hay số lần Spring thật sự xây chain. Không dùng nó làm kết luận về độ trễ production nếu chưa đo.

Ngược lại, một static advice có thể kiểm tra `amount >= 100` **bên trong** nó:

~~~java
MethodInterceptor oneAdvice = invocation -> {
    Object[] values = invocation.getArguments();
    if (values.length == 2 && values[1] instanceof Integer amount
            && amount >= 100) {
        System.out.println("premium");
    }
    return invocation.proceed();
};
~~~

Đây là mã **minh họa không độc lập**, cần một `MethodInterceptor` được gắn bằng advisor vào đúng method. Cách này không tự động loại chi phí kiểm tra argument; nó chuyển phép kiểm tra từ runtime matcher vào body của advice. Tùy thiết kế, dynamic pointcut giúp biểu đạt rõ sự phân tách *eligibility* và *advice execution*, nhưng không thể coi runtime predicate miễn phí.

## 9. Spring `@Aspect` pointcut expressions có thể dynamic hay không?

Đối với cách viết `@Aspect`, Spring thường dùng `AspectJExpressionPointcut` bên dưới. Người dùng không gọi trực tiếp `isRuntime()` mỗi khi viết `@Before`, nhưng bản chất static/dynamic vẫn phụ thuộc biểu thức.

Ví dụ mô tả phạm vi method dựa trên metadata:

~~~java
@Before("execution(* demo..*Service.place(..))")
public void beforePlace() {
    // Chạy cho lời gọi proxy khớp method signature.
}
~~~

Trong trường hợp này, yêu cầu “đúng method `place`” có thể xác định bằng kiểu và signature. Spring có thể tận dụng kết quả tĩnh.

Nếu pointcut dựa vào kiểu runtime của arguments (như `args(...)`, `@args(...)` trong những biểu thức thích hợp), một phần điều kiện có thể cần kiểm tra tại runtime; Spring/AspectJ expression parser quyết định matcher thực tế. Không đồng nhất mọi biểu thức `args` với một số lượng runtime checks nhất định, vì cách optimize và loại điều kiện khác nhau theo expression.

Lưu ý: `args(order)` trong annotation-style cũng thường dùng để **bind tham số cho advice**; việc bind dữ liệu và việc kiểm tra condition động là hai trách nhiệm liên quan nhưng không đồng nhất. Với điều kiện phụ thuộc **giá trị** như `amount > 100`, không viết tưởng tượng một toán tử số học trong AspectJ pointcut expression. Có thể dùng programmatic `DynamicMethodMatcherPointcut` hoặc xét điều kiện trong advice/service theo yêu cầu.

Đối với ứng dụng đã dùng annotation, sự khác biệt static/dynamic là lý do để giữ expression vừa đủ cụ thể: filter package/class/method metadata trước, rồi chỉ dùng argument-dependent condition khi thực sự cần.

## 10. Các trường hợp cần cân nhắc kỹ

### 10.1. `ControlFlowPointcut`: phụ thuộc call stack

Spring có `org.springframework.aop.support.ControlFlowPointcut`, một dynamic pointcut có thể kiểm tra call stack. Việc xét stack thường đắt hơn phép so sánh argument và cũng khó dự đoán hơn trên workload nhiều lời gọi ngắn. Không suy diễn rằng `cflow` của AspectJ được hỗ trợ trực tiếp trong Spring AOP proxy pointcut expressions; đây là **Spring AOP API riêng**.

Nếu yêu cầu là “chỉ advice khi do web layer gọi”, cân nhắc thiết kế metadata/call boundary tĩnh, Spring context hoặc cơ chế request context phù hợp trước khi chọn scan stack cho từng invocation. Đo chi phí thật nếu vẫn cần.

### 10.2. Mutable state trong matcher

Custom pointcut hoặc advice thường là đối tượng dùng chung giữa nhiều thread. Không lưu `currentOrderId` vào field rồi đọc ở lần gọi sau. Ví dụ dùng `AtomicInteger` để đếm runtime checks, chỉ nhằm quan sát số lần matcher được gọi.

Nếu predicate phải đọc flags thay đổi theo request, dữ liệu đó cần được lấy đúng từ argument/context của invocation. Đồng bộ, cache invalidation và tính nhất quán trở thành một phần của yêu cầu nghiệp vụ; không dùng static matching để làm “cache” cho điều kiện đang biến đổi.

### 10.3. Method có lời gọi bất đồng bộ

Khi method qua Spring proxy trả `Mono`, `Flux`, `CompletionStage`, phép match `MethodMatcher` ở Spring AOP vẫn diễn ra **khi lời gọi method được proxy intercept**. Nó không tự chạy lại mỗi lần subscriber subscribe vào `Publisher` hoặc mỗi khi completion callback xảy ra.

Nếu dynamic matcher dựa trên method arguments thì nó đánh giá chúng tại thời điểm invocation. Chi phí và state của một subscription reactive là bài toán khác và phải được quan sát qua API reactive phù hợp. Đừng dùng `isRuntime()` để suy ra “runtime của pipeline đã hoàn tất”.

### 10.4. Advice chain và thứ tự

Dynamic predicate được kiểm tra khi interceptor tương ứng đến lượt thực thi. Advice trước đó có thể sửa method arguments, short-circuit hoặc ném exception. Điều này ảnh hưởng số lượt runtime check có thể quan sát và có thể ảnh hưởng dữ liệu nhận vào matcher. Khi có nhiều advisors, thứ tự `@Order` / `Ordered` hay thứ tự add advisor thủ công đều cần được xác định đúng.

### 10.5. Việc tạo proxy cũng có chi phí

Dùng pointcut tĩnh cho hot-path giảm runtime matching, nhưng **vẫn còn proxy dispatch, advice và tác động của JIT**. Nếu advice đắt do logging hoặc gọi network, tối ưu một lần so sánh `amount >= 100` chưa chắc đem lại lợi ích có thể đo được. Đừng bỏ một điều kiện nghiệp vụ cần thiết chỉ vì chạy dynamic.

## 11. Đo và xác minh hiệu năng đúng cách

Để kiểm tra giả thuyết “dynamic matcher đang là điểm nghẽn”, thực hiện theo thứ tự:

1. **Xác minh chức năng**: viết test gọi cả method match và không match; với một method có hai bộ arguments khác nhau, static eligibility phải giống nhau nhưng dynamic decision có thể khác.
2. **Đếm số lần match**: `AtomicInteger/LongAdder` trong runtime matcher (chỉ dùng làm instrumentation) để xem tần suất; không khẳng định số lần static matching cố định do proxy cache và khởi tạo có thể khác nhau.
3. **Tách chi phí**: so invocation trực tiếp, Spring proxy không advisor, proxy + static advisor, proxy + dynamic matcher trả false/true, và advice thật. Đo riêng predicate rẻ/đắt.
4. **Warm-up và công cụ**: dùng JMH nếu cần microbenchmark đáng tin, chạy trên cùng JVM/Spring version, warm-up JIT, tránh compiler loại bỏ công việc; đo throughput và latency distribution qua đủ lần lặp.
5. **Đo production phù hợp**: profile CPU/allocation và các metrics latency tại method hot path, thay vì kết luận từ một vài dòng `System.nanoTime()` có I/O logging xen vào.
6. **Kiểm thử concurrency**: nếu pointcut có shared counters hoặc dynamic state, dùng dữ liệu thread-safe và xác minh kết quả khi nhiều requests cùng gọi.

Không nên benchmark bằng cách in `System.out` trong mỗi invocation: console I/O có thể lấn át chi phí matching. Ví dụ phần 6 dùng log để thấy **đường thực thi**, không dùng số log để suy ra phần trăm hiệu năng.

### Một ma trận kiểm thử tối thiểu

| Trường hợp | Static match của `PlacePointcut` | Static prefilter của `PremiumPlacePointcut` | Runtime matcher premium? | Premium advice? |
| --- | --- | --- | --- | --- |
| `place("A-1", 40)` | true | true | Có, false | Không |
| `place("B-2", 140)` | true | true | Có, true | Có |
| `health()` | false | false | Không | Không |
| `place("C-3", 100)` | true | true | Có, true | Có |

Nếu thêm dòng `place("C-3", 100)` vào ví dụ, `getRuntimeChecks()` tăng lên 3, còn số premium advice invocations tăng lên 2. Đây là phép kiểm tra trực quan việc decision phụ thuộc argument.

## 12. Các lỗi thường gặp trong dự án Spring

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Nghĩ static pointcut làm advice chạy đúng một lần | Đánh giá sai hành vi và số lần log | Phân biệt **cache matching** với **advice execution mỗi invocation** |
| `isRuntime() == true` nhưng quên static prefilter | Runtime predicate chạy cả các method không liên quan | Override `matches(Method, Class)` để loại sớm |
| Khai báo static pointcut cho điều kiện phụ thuộc `args` | Một kết quả dùng cho nhiều lời gọi khác argument ⇒ sai logic | Chọn dynamic matcher hoặc kiểm tra trong advice/service |
| Nghĩ runtime predicate false nên không tốn phí | Bỏ qua chi phí **kiểm tra để biết false** | Đếm checks và profile phần predicate |
| Coi mọi `@Aspect` expression đều dynamic | Tối ưu sai phạm vi và kỳ vọng không đúng | Xét chính xác kiểu metadata hay args cần check |
| Gọi `matches(..., args)` trên mọi method mà không kiểm tra eligibility | Dễ cast sai hoặc lãng phí | Dùng class filter, static method filter |
| Trông đợi proxy intercept self-invocation | Advice không chạy khi gọi `this.method()` | Thiết kế lời gọi qua Spring bean proxy |
| So sánh thời gian console logs trong test | Bị I/O chi phối | Dùng benchmark/profile chuyên dụng |
| Dùng field mutable thường trong shared matcher | Race, đếm sai hoặc rò rỉ trạng thái invocation | Local state, AtomicInteger/LongAdder hay context phù hợp |
| Đổi Spring phiên bản mà assert static match count = 1 | Test phụ thuộc chi tiết cache nội bộ | Assert kết quả match/advice, không hard-code số static checks |
| Nhầm dynamic pointcut với async lifecycle | Tưởng matcher chạy lúc Publisher subscribe | Theo dõi subscription bằng API reactive riêng khi cần |

## 13. Hướng chọn trong thiết kế Spring AOP

Nếu nhu cầu chỉ phụ thuộc **method signature/class/annotation metadata**, ưu tiên `StaticMethodMatcherPointcut`, hoặc khai báo pointcut expression xác định phạm vi bằng metadata. Spring có thể loại những method không match và tái sử dụng kết quả.

Nếu hành vi thực sự cần đọc **argument của invocation**, dùng dynamic method matcher; đẩy các điều kiện xác định trước (class, method name/type) vào **static prefilter** để chỉ kiểm tra động trong tập nhỏ nhất có thể. Không chuyển điều kiện per-request thành static chỉ để tiết kiệm CPU.

Nếu logic nghiệp vụ phải kiểm tra dữ liệu dù có hay không có AOP (ví dụ bất biến domain hoặc rule cần bảo đảm an toàn), thiết kế lớp service/policy kiểm tra riêng và chỉ dùng AOP cho cơ chế cắt ngang phù hợp. Lựa chọn dùng pointcut động cần cân bằng tính minh bạch, khả năng kiểm thử và chi phí trên hot path.

**Ghi nhớ:** Trong Spring AOP, `matches(Method, Class)` có thể được xử lý/cached theo metadata, còn `matches(Method, Class, args)` đòi kiểm tra ở những invocation runtime đạt bước lọc tĩnh. Match tĩnh tiết kiệm chi phí **ra quyết định**, không loại bỏ chi phí **thực thi advice** trên các lời gọi đã match.

### Tài liệu Spring chính thức

- [Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [MethodMatcher, Spring Framework 6.1](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/MethodMatcher.html)
- [DynamicMethodMatcherPointcut, Spring Framework 6.1](https://docs.spring.io/spring-framework/docs/6.1.4/javadoc-api/org/springframework/aop/support/DynamicMethodMatcherPointcut.html)
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)


</details>

- [Quay lại đầu trang](#back-to-top)
