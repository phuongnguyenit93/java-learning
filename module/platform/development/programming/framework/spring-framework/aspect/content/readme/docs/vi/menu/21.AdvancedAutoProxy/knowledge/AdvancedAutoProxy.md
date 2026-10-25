<a id="back-to-top"></a>

# Auto-proxy nâng cao

## Menu
- [BeanNameAutoProxyCreator và lựa chọn bean theo tên](#advanced-auto-proxy-001)
- [DefaultAdvisorAutoProxyCreator và tự tìm advisor phù hợp](#advanced-auto-proxy-002)
- [Quan hệ giữa auto-proxy creator và BeanPostProcessor](#advanced-auto-proxy-003)
- [Đăng ký advisor dạng bean để dùng chung trong container](#advanced-auto-proxy-004)
- [Vai trò các thành phần auto-proxy dành cho @AspectJ](#advanced-auto-proxy-005)
- [Thứ tự xử lý với các BeanPostProcessor khác](#advanced-auto-proxy-006)
- [Nguy cơ tự tạo nhiều auto-proxy creator không phối hợp](#advanced-auto-proxy-007)
- [Chẩn đoán bean không đủ điều kiện được xử lý đầy đủ](#advanced-auto-proxy-008)
- [Giới hạn mở rộng hạ tầng AOP bằng API nội bộ theo phiên bản Spring](#advanced-auto-proxy-009)

## <a id="advanced-auto-proxy-001">BeanNameAutoProxyCreator và lựa chọn bean theo tên</a>

<details>
<summary>Xem chi tiết</summary>

# BeanNameAutoProxyCreator và lựa chọn bean theo tên trong Spring AOP

## 1. Mục tiêu và vị trí trong Spring AOP

`BeanNameAutoProxyCreator` thuộc gói `org.springframework.aop.framework.autoproxy`. Đây là một `BeanPostProcessor` của Spring có nhiệm vụ tự tạo AOP proxy cho những bean có **tên** khớp danh sách cấu hình. Thay vì khai báo một `ProxyFactoryBean` cho từng đối tượng, ta đăng ký một auto-proxy creator và dùng chung chuỗi interceptor/advisor cho nhiều bean.

Sau bài này, bạn có thể:

- Phân biệt **chọn bean bằng tên** với **chọn phương thức bằng pointcut**.
- Cấu hình `beanNames`, `interceptorNames` và `proxyTargetClass` một cách có chủ đích.
- Dự đoán bean nào trở thành proxy khi dùng tên chính xác hoặc dấu `*`.
- Kiểm tra proxy đang hoạt động, xử lý các lỗi thường gặp và quyết định khi nào nên dùng lựa chọn này trong Spring.

Phạm vi ở đây là **cơ chế proxy và cấu hình trong Spring AOP**; không cần đi lại lý thuyết Aspect/AOP tổng quát.

## 2. Cơ chế hoạt động trong Spring container

Trong quá trình Spring tạo bean, `BeanNameAutoProxyCreator` tham gia vào chuỗi `BeanPostProcessor`. Với mỗi bean đủ điều kiện, nó kiểm tra tên bean, xác định có cần bọc proxy hay không rồi gắn các advice/advisor đã cấu hình vào proxy. Đối tượng mà các thành phần khác nhận từ `ApplicationContext` có thể là proxy thay vì target trực tiếp.

```text
Spring ApplicationContext
    │
    ├─ Đăng ký BeanNameAutoProxyCreator (BeanPostProcessor)
    │        beanNames = ["payment*", "*Report"]
    │        interceptorNames = ["auditInterceptor"]
    │
    ├─ Tạo bean "paymentService" ── khớp payment* ──> AOP proxy
    ├─ Tạo bean "monthlyReport"  ── khớp *Report  ──> AOP proxy
    └─ Tạo bean "inventoryService" ── không khớp ──> bean thông thường
```

Luồng gọi điển hình là `caller → proxy → interceptor/advisor → target method`. Chỉ những lời gọi **đi qua proxy** mới được xử lý theo chuỗi advice của proxy.

Hai bước chọn cần phân biệt:

1. **Chọn đối tượng:** `beanNames` quyết định bean nào được bọc proxy. Đây là bước theo *tên bean trong container*, không phải theo tên lớp, package hoặc annotation.
2. **Chọn hành vi:** `interceptorNames` đặt các interceptor/advisor được áp dụng lên proxy đã chọn. Một interceptor thông thường nhận mọi lời gọi phương thức được proxy chuyển qua nó; nếu cần lọc phương thức, hãy cung cấp một `Advisor` chứa `Pointcut`.

`BeanNameAutoProxyCreator` kế thừa `AbstractAutoProxyCreator`; Spring tự chuyển các advice hợp lệ sang dạng advisor khi xây chuỗi xử lý. Bean hạ tầng AOP và một số trường hợp đặc biệt không được tự bọc như bean nghiệp vụ.

## 3. Quy tắc khớp `beanNames`

Các chuỗi được truyền qua `setBeanNames(String...)`. Cơ chế khớp tên đơn giản của Spring hỗ trợ tên chính xác và wildcard `*`:

| Mẫu | Ví dụ khớp | Ví dụ không khớp | Ý nghĩa |
| --- | --- | --- | --- |
| `paymentService` | `paymentService` | `paymentServiceV2` | Tên chính xác |
| `payment*` | `paymentService`, `paymentJob` | `invoiceService` | Tiền tố |
| `*Report` | `dailyReport`, `monthlyReport` | `reportService` | Hậu tố |
| `*Audit*` | `orderAuditService` | `orderService` | Có đoạn tên ở giữa |

Một bean chỉ cần khớp **một** mẫu trong mảng là đủ. Đây là khớp kiểu wildcard đơn giản, **không phải biểu thức chính quy**; tên có phân biệt chữ hoa/chữ thường. Một mẫu quá rộng như `*Service` có thể vô tình bao gồm nhiều bean mà bạn không muốn gắn advice.

Tên được đối chiếu là **bean name do Spring quản lý**, chẳng hạn tên trong `@Bean("paymentService")`, tên mặc định của phương thức `@Bean`, hoặc tên component khi đăng ký vào container. Vì vậy, đổi tên bean có thể làm thay đổi phạm vi proxy dù code lớp đích không đổi.

**Lưu ý với `FactoryBean`:** cấu hình `beanNames = "myFactoryBean"` nhắm tới đối tượng sản phẩm do factory tạo. Nếu thật sự muốn proxy bản thân `FactoryBean`, dùng tên có tiền tố `&`, ví dụ `&myFactoryBean`. Trường hợp này hiếm gặp; không nhầm nó với một bean nghiệp vụ thông thường.

## 4. Những thuộc tính cấu hình quan trọng

| Thuộc tính | Vai trò |
| --- | --- |
| `beanNames` | Danh sách tên/mẫu tên của các bean mục tiêu. |
| `interceptorNames` | **Tên các bean** advice, `MethodInterceptor` hoặc `Advisor` được gắn vào proxy. Thứ tự trong danh sách ảnh hưởng đến chuỗi gọi. |
| `proxyTargetClass` | `true` để yêu cầu proxy theo lớp đích (class-based); `false` cho phép cơ chế Spring ưu tiên proxy dựa trên interface khi thích hợp. |
| `order` | Thứ tự ưu tiên của `BeanPostProcessor` trong quá trình khởi tạo; không phải thứ tự các advice bên trong `interceptorNames`. |
| `exposeProxy` | Cho phép tra cứu proxy hiện tại bằng `AopContext.currentProxy()` trong những điều kiện phù hợp; thường không cần bật. |

Đáng chú ý, `interceptorNames` nhận **chuỗi tên bean**, không phải mảng tham chiếu đối tượng. Cách này giúp Spring tra cứu advice/advisor từ factory vào đúng thời điểm, kể cả trường hợp advice có scope `prototype`. Khi có nhiều interceptor, interceptor được khai báo trước sẽ là lớp ngoài trong chuỗi gọi thông thường.

Ví dụ cấu hình XML truyền thống:

```xml
<bean class="org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator">
    <property name="beanNames" value="payment*,*Report"/>
    <property name="interceptorNames">
        <list>
            <value>auditInterceptor</value>
        </list>
    </property>
</bean>
```

Bean `auditInterceptor` phải tồn tại với đúng tên trong bean factory. Hai giá trị `payment*` và `*Report` là **mẫu tên target**, còn `auditInterceptor` là **tên bean xử lý**; không được nhầm hai loại tên này.

## 5. Ví dụ chạy được với Spring Framework và Java 17+

Ví dụ sử dụng Java configuration, `AnnotationConfigApplicationContext` và ba bean có cùng interface. Hai bean khớp tên sẽ được proxy, bean còn lại giữ nguyên. Điều này cho thấy lựa chọn dựa vào **tên bean**, không dựa vào kiểu Java của target.

Tạo cấu trúc:

```text
bean-name-aop/
├── pom.xml
└── src/main/java/demo/BeanNameProxyDemo.java
```

**`pom.xml`** (Spring Framework 6.2.x, Java 17):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-name-aop</artifactId>
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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.BeanNameProxyDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**`src/main/java/demo/BeanNameProxyDemo.java`:**

```java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class BeanNameProxyDemo {
    public interface Job {
        void run();
    }

    static class JobImpl implements Job {
        private final String label;

        JobImpl(String label) {
            this.label = label;
        }

        @Override
        public void run() {
            System.out.println(label + " executed");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        // static: đăng ký BeanPostProcessor sớm, tránh khởi tạo Config quá sớm.
        @Bean
        static BeanNameAutoProxyCreator beanNameProxyCreator() {
            BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
            creator.setBeanNames("payment*", "*Report");
            creator.setInterceptorNames("auditInterceptor");
            return creator;
        }

        @Bean
        MethodInterceptor auditInterceptor() {
            return invocation -> {
                System.out.println("[before] " + invocation.getMethod().getName());
                try {
                    return invocation.proceed();
                } finally {
                    System.out.println("[after] " + invocation.getMethod().getName());
                }
            };
        }

        @Bean("paymentJob")
        Job paymentJob() {
            return new JobImpl("payment");
        }

        @Bean("monthlyReport")
        Job monthlyReport() {
            return new JobImpl("report");
        }

        @Bean("inventoryJob")
        Job inventoryJob() {
            return new JobImpl("inventory");
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            for (String beanName : new String[] {
                    "paymentJob", "monthlyReport", "inventoryJob"
            }) {
                Job job = (Job) context.getBean(beanName);
                System.out.println(beanName + " proxy=" + AopUtils.isAopProxy(job));
                job.run();
            }
        }
    }
}
```

Chạy trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

Kết quả mong đợi (bỏ qua thông báo riêng của Maven/JVM nếu môi trường in thêm):

```text
paymentJob proxy=true
[before] run
payment executed
[after] run
monthlyReport proxy=true
[before] run
report executed
[after] run
inventoryJob proxy=false
inventory executed
```

**Giải thích kết quả:**

- `paymentJob` khớp `payment*`, `monthlyReport` khớp `*Report`: đối tượng lấy từ context là AOP proxy, nên interceptor chạy trước/sau `run()`.
- `inventoryJob` không khớp bất kỳ mẫu nào: không được proxy bởi creator này, dù **cả ba bean cùng dùng `JobImpl`**.
- `AopUtils.isAopProxy(...)` là phép kiểm tra thực tế đáng tin hơn việc đoán qua tên lớp proxy.
- `invocation.proceed()` là bước chuyển tiếp đến lời gọi tiếp theo hoặc target; nếu bỏ bước đó, target sẽ không được thực thi.
- `try/finally` in dòng `[after]` kể cả khi target ném ngoại lệ; đó là log kết thúc, **không có nghĩa lời gọi thành công**.

Phương thức `@Bean` tạo `BeanNameAutoProxyCreator` được khai báo `static` vì nó sinh ra một `BeanPostProcessor`, cần được Spring đăng ký sớm. Với một phương thức `@Bean` không tĩnh thuộc lớp `@Configuration`, Spring có thể phải tạo configuration instance sớm hơn thông thường, gây hệ quả khó hiểu cho quá trình post-processing.

## 6. Nếu muốn chỉ áp dụng cho một số phương thức?

Tên bean và tên phương thức là **hai chiều lọc khác nhau**. `beanNames = "payment*"` không có nghĩa chỉ bắt các phương thức bắt đầu bằng `payment`.

Khi cần chỉ áp dụng interceptor cho phương thức tên `run`, có thể đăng ký một `Advisor` có `NameMatchMethodPointcut`, rồi đổi `interceptorNames` sang tên advisor:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

// Minh họa: thêm vào lớp Config trong ví dụ trước, thay cấu hình creator tương ứng.
@Bean
Advisor runOnlyAdvisor(MethodInterceptor auditInterceptor) {
    NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
    pointcut.setMappedName("run");
    return new DefaultPointcutAdvisor(pointcut, auditInterceptor);
}
```

Cập nhật **duy nhất** dòng cấu hình interceptor của creator thành:

```java
creator.setInterceptorNames("runOnlyAdvisor");
```

Lúc này `BeanNameAutoProxyCreator` vẫn chỉ tạo proxy cho các **bean khớp tên**, còn `runOnlyAdvisor` quyết định phương thức `run` có được áp dụng advice hay không. Đây là đoạn thay thế minh họa trong `Config`, không phải một chương trình Java độc lập.

Nếu cần quy tắc chọn phương thức phức tạp, hãy dùng pointcut/advisor phù hợp của Spring AOP, hoặc biểu thức pointcut qua `@Aspect` khi ứng dụng đang dùng cấu hình đó.

## 7. Quan hệ với những cách tạo proxy khác trong Spring

| Công cụ | Cách chọn target | Khi nào thích hợp |
| --- | --- | --- |
| `BeanNameAutoProxyCreator` | Tên hoặc mẫu tên bean | Nhiều bean theo quy ước tên cần cùng interceptor/advisor. |
| `ProxyFactoryBean` | Khai báo proxy cho target cụ thể | Cần điều khiển cấu hình proxy riêng cho một target. |
| `DefaultAdvisorAutoProxyCreator` | Xét các advisor/pointcut khả dụng | Chính sách áp dụng dựa vào advisor thay vì liệt kê tên target. |
| `@Aspect` + `@EnableAspectJAutoProxy` | Biểu thức pointcut của Spring AOP | Chính sách cắt ngang gắn với join point/phương thức, dễ tổ chức theo aspect. |

`BeanNameAutoProxyCreator` vẫn tạo **Spring AOP proxy**; nó không thay thế cơ chế dệt bytecode của AspectJ. Trong Spring Boot, thành phần này có thể được đăng ký qua `@Bean` như ví dụ, nhưng cần kiểm tra các cơ chế proxy đã có trong ứng dụng để tránh chuỗi proxy chồng lấp ngoài ý muốn. Với giao dịch khai báo hay bảo mật phương thức, nên ưu tiên hạ tầng chuyên dụng (`@Transactional`, method security) thay vì tự gắn interceptor tùy tiện vào mọi bean có một mẫu tên.

## 8. Proxy theo interface hay theo lớp?

Spring AOP thường dùng JDK dynamic proxy khi target có interface phù hợp; class-based proxy dùng cơ chế tạo lớp con. Trong ví dụ, cả ba bean được truy cập qua `Job`, nên có thể dùng interface-based proxy.

Khi cần ép proxy theo lớp:

```java
creator.setProxyTargetClass(true);
```

Các lưu ý thực hành:

- Với JDK proxy, hãy inject hoặc lấy bean theo **interface**. Ép kiểu từ JDK proxy sang lớp triển khai cụ thể có thể gây `ClassCastException`.
- Class-based proxy không thể ghi đè phương thức `final`/`private`; vì vậy các phương thức đó không thể được advise bằng cơ chế override của proxy lớp. Lớp `final` cũng không phù hợp với class-based proxy.
- Lời gọi từ một phương thức target sang phương thức khác trên chính `this` thường **bỏ qua proxy**, nên advice không chạy cho self-invocation.
- Chỉ những đối tượng do Spring container quản lý mới được `BeanPostProcessor` này xử lý; đối tượng tự `new` ở ngoài container không tự được bọc.
- Lựa chọn thực tế có thể chịu ảnh hưởng bởi cấu hình proxy khác trong cùng application context; hãy kiểm tra bằng `AopUtils`, kiểu bean và các advisor thực sự được gắn.

Không nên bật `proxyTargetClass=true` chỉ để khắc phục một lỗi inject khi ứng dụng có thể phụ thuộc đúng interface.

## 9. Lỗi phổ biến, cách kiểm tra

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra hoặc sửa |
| --- | --- | --- |
| Bean không được proxy | Bean name thực tế không khớp `beanNames`; bean không qua Spring container | Kiểm tra tên thật trong `ApplicationContext`, pattern và kết quả `AopUtils.isAopProxy`. |
| Lỗi không tìm thấy advice/advisor | `interceptorNames` trỏ tới tên chưa đăng ký | Xác nhận có bean interceptor/advisor với đúng tên và cùng context khả dụng. |
| Interceptor chạy cho quá nhiều bean | Wildcard quá rộng | Thu hẹp mẫu; dùng tên tường minh nếu chỉ có ít target. |
| Proxy tồn tại nhưng method không bị intercept | Advisor có pointcut không khớp, method không đi qua proxy hoặc thuộc giới hạn proxy | Kiểm tra advisor/pointcut, nơi gọi method, các modifier `final`/`private`. |
| Cast sang implementation thất bại | JDK proxy chỉ triển khai interface | Inject qua interface, hoặc cân nhắc class-based proxy nếu thật sự cần. |
| Cùng một hành vi chạy hai lần | Có nhiều hạ tầng proxy/advisor tác động cùng target | Rà soát cấu hình AOP, transaction, các `BeanPostProcessor` liên quan. |
| Test có kết quả khác dự đoán | Dùng tên bean mặc định khác với tên kỳ vọng | Đặt tên tường minh qua `@Bean("...")`, đối chiếu tên của bean đang được lấy. |

Có thể thêm phép kiểm tra trong test:

```java
Object payment = context.getBean("paymentJob");
Object inventory = context.getBean("inventoryJob");

assert org.springframework.aop.support.AopUtils.isAopProxy(payment);
assert !org.springframework.aop.support.AopUtils.isAopProxy(inventory);
```

Đây là đoạn kiểm tra thêm trong phạm vi `context` của ví dụ; để sử dụng `assert`, chạy JVM với `-ea` hoặc thay bằng assertion của JUnit. Nó không thay thế việc thử lời gọi thật để xác nhận advice được thực thi.

## 10. Bài tập vận dụng

1. Thay mẫu `payment*` bằng `*Job`: dự đoán những bean nào được proxy, sau đó chạy và đối chiếu.
2. Đổi tên `monthlyReport` thành `monthlySummary`, giữ `*Report`: giải thích vì sao interceptor không còn chạy ở bean đó.
3. Đăng ký hai `MethodInterceptor` khác nhau và đặt cả hai tên trong `interceptorNames`: quan sát thứ tự dòng log trước/sau `proceed()`.
4. Tạo một bean không có interface; so sánh việc proxy theo lớp với bean `Job` có interface.
5. Thử gọi `run()` qua tham chiếu bean do container trả về và qua một lời gọi nội bộ trong target để thấy giới hạn self-invocation.

**Checklist khi đưa vào dự án:** tên bean ổn định; wildcard vừa đủ hẹp; advisor/interceptor đăng ký hợp lệ; thứ tự advice rõ ràng; kiểu proxy tương thích với injection; lời gọi đi qua proxy; có kiểm tra hành vi thực tế. Nếu quy tắc chọn không còn dựa tự nhiên vào tên bean, nên chuyển sang cách diễn đạt bằng advisor/pointcut thay vì duy trì danh sách tên ngày càng khó hiểu.

## 11. Tài liệu Spring chính thức

- [Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html) — mô tả `BeanNameAutoProxyCreator`, `beanNames` và `interceptorNames`.
- [BeanNameAutoProxyCreator Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/autoproxy/BeanNameAutoProxyCreator.html) — API và quy tắc khớp tên, kể cả `FactoryBean`.
- [AbstractAutoProxyCreator Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html) — vòng đời post-processor và common interceptors.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) — giới hạn JDK/class-based proxy và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-002">DefaultAdvisorAutoProxyCreator và tự tìm advisor phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# DefaultAdvisorAutoProxyCreator và cách Spring tự tìm Advisor phù hợp

## 1. Vai trò trong Spring AOP

**DefaultAdvisorAutoProxyCreator** là một `BeanPostProcessor` của Spring AOP. Sau khi được đăng ký trong cùng `ApplicationContext`, nó tự tìm các bean kiểu `org.springframework.aop.Advisor`, xác định advisor nào có thể áp dụng cho từng bean nghiệp vụ rồi tạo AOP proxy khi có ít nhất một advisor phù hợp. Nhờ đó, cấu hình không phải chỉ định thủ công danh sách bean đích hoặc gọi `ProxyFactory` cho từng bean.

Đây là cơ chế **auto-proxy dựa trên Advisor có sẵn trong Spring container**. Nó không tự biến mọi `@Aspect` thành advisor: khả năng diễn giải các lớp `@Aspect` thuộc về `AnnotationAwareAspectJAutoProxyCreator` và cấu hình `@EnableAspectJAutoProxy` tương ứng. Phạm vi bài này là hạ tầng Spring AOP, cách phát hiện, lọc và áp dụng advisor; không đi lại lý thuyết Aspect nói chung.

Điều kiện cốt lõi:
- Có một bean `DefaultAdvisorAutoProxyCreator` được container đăng ký đủ sớm để làm `BeanPostProcessor`.
- Trong `BeanFactory` có các bean **Advisor**, ví dụ `DefaultPointcutAdvisor`; một bean chỉ là `MethodInterceptor` hoặc `Advice` riêng lẻ không tự được chọn theo cơ chế tìm Advisor này.
- Ít nhất một Advisor áp dụng được lên class hoặc một phương thức của bean đang xét.
- Bean được tạo và lấy qua Spring container; lời gọi tới bean phải đi qua proxy thì advice mới chạy.

## 2. Luồng tìm Advisor và chọn bean cần proxy

Chuỗi xử lý khái quát khi Spring khởi tạo một bean thông thường:

~~~text
BeanFactory tạo bean nghiệp vụ
  → BeanPostProcessor: DefaultAdvisorAutoProxyCreator
    → findCandidateAdvisors(): tìm các bean kiểu Advisor trong BeanFactory
    → lọc những tên advisor đủ điều kiện (isEligibleAdvisorBean)
    → findAdvisorsThatCanApply(...): đối chiếu với bean class
    → extendAdvisors(...), sortAdvisors(...)
    → Không có advisor phù hợp: giữ nguyên bean
    → Có advisor phù hợp: tạo AOP proxy chứa chuỗi advisor
Lời gọi bên ngoài → proxy → interceptor chain (nếu method match) → target method
~~~

Các phương thức nòng cốt nằm ở lớp cha `AbstractAdvisorAutoProxyCreator`:

| Phương thức | Việc Spring thực hiện |
| --- | --- |
| `findCandidateAdvisors()` | Lấy danh sách ứng viên là bean `Advisor` từ `BeanFactory`. |
| `isEligibleAdvisorBean(beanName)` | Có cho phép advisor theo tên này tham gia không; `DefaultAdvisorAutoProxyCreator` có tuỳ chọn lọc prefix. |
| `findAdvisorsThatCanApply(...)` | Xét một Advisor có áp dụng được lên bean class, trong đó gồm điều kiện class và phương thức của Pointcut. |
| `findEligibleAdvisors(beanClass, beanName)` | Kết hợp tìm ứng viên, kiểm tra khả dụng, bổ sung và sắp xếp advisor. |
| `sortAdvisors(...)` | Sắp thứ tự advisor theo `Ordered` / `@Order` khi có chỉ định. |
| `getAdvicesAndAdvisorsForBean(...)` | Trả về các advisor để dựng proxy; danh sách rỗng dẫn tới không proxy theo creator này. |

**Phân biệt hai tầng matching:**

1. **Chọn bean để proxy:** một `PointcutAdvisor` cần có `ClassFilter` phù hợp và `MethodMatcher` khớp ít nhất một phương thức thích hợp của target. `IntroductionAdvisor` có cơ chế kiểm tra class tương ứng. Nếu không có advisor áp dụng, bean không cần proxy bởi creator này.
2. **Chọn advice khi gọi method:** sau khi bean đã được proxy, interceptor chain còn dựa vào phương thức đang gọi. Vì vậy cùng một proxy có thể xử lý `placeOrder()` nhưng để `checkStatus()` đi thẳng tới target.

Spring có các kiểm tra để tránh tự proxy những đối tượng hạ tầng AOP/đối tượng đặc biệt và tránh tình huống khởi tạo vòng lặp. Việc duyệt và tạo advisor không có nghĩa mỗi request nghiệp vụ sẽ quét lại toàn bộ container; auto-proxy chủ yếu được quyết định trong vòng đời tạo bean.

## 3. Ví dụ chạy được với Spring Framework 6.2 và Java 17

Ví dụ tạo hai bean nghiệp vụ, một Advisor chỉ khớp tên method `placeOrder`, và đăng ký `DefaultAdvisorAutoProxyCreator`. Không sử dụng `@Aspect` hay `@EnableAspectJAutoProxy` vì mục tiêu là quan sát rõ chức năng của creator này.

Cấu trúc dự án Maven:

~~~text
spring-advisor-demo/
├── pom.xml
└── src/main/java/demo/App.java
~~~

**pom.xml:**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-advisor-demo</artifactId>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/App.java:**

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class App {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            OrderService orders = context.getBean(OrderService.class);
            HealthService health = context.getBean(HealthService.class);

            System.out.println("orders proxied = " + AopUtils.isAopProxy(orders));
            System.out.println("health proxied = " + AopUtils.isAopProxy(health));

            orders.placeOrder();
            orders.checkStatus();
            health.ping();
        }
    }

    public interface OrderService {
        void placeOrder();
        void checkStatus();
    }

    public static class OrderServiceImpl implements OrderService {
        @Override
        public void placeOrder() {
            System.out.println("[target] placeOrder");
        }

        @Override
        public void checkStatus() {
            System.out.println("[target] checkStatus");
        }
    }

    public static class HealthService {
        public void ping() {
            System.out.println("[health] ping");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        public static DefaultAdvisorAutoProxyCreator autoProxyCreator() {
            return new DefaultAdvisorAutoProxyCreator();
        }

        @Bean
        public Advisor orderAdvisor() {
            NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
            pointcut.setMappedName("placeOrder");

            MethodInterceptor advice = invocation -> {
                System.out.println("[advice] before " + invocation.getMethod().getName());
                Object result = invocation.proceed();
                System.out.println("[advice] after " + invocation.getMethod().getName());
                return result;
            };

            return new DefaultPointcutAdvisor(pointcut, advice);
        }

        @Bean
        public OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        public HealthService healthService() {
            return new HealthService();
        }
    }
}
~~~

Lệnh chạy tại thư mục chứa `pom.xml`:

~~~bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.App
~~~

Stdout do chương trình in ra (không tính các thông báo của Maven/môi trường):

~~~text
orders proxied = true
health proxied = false
[advice] before placeOrder
[target] placeOrder
[advice] after placeOrder
[target] checkStatus
[health] ping
~~~

**Giải thích từng điểm quan sát:**

- `autoProxyCreator()` trả về **BeanPostProcessor**; khai báo `@Bean static` cho phép Spring tạo và đăng ký post-processor sớm, độc lập với việc khởi tạo instance `Config`.
- `orderAdvisor()` là bean kiểu `Advisor`; Spring tự khám phá nó. `NameMatchMethodPointcut` chỉ khớp phương thức có tên `placeOrder`. Với hệ thống lớn, nên bổ sung ràng buộc class/package/annotation để không vô tình khớp các class khác cùng tên method.
- `OrderServiceImpl` có method được Pointcut nhận diện nên Spring dựng proxy; `HealthService` không có method phù hợp nên creator này không proxy nó.
- `checkStatus()` vẫn gọi qua đối tượng `orders` đã proxy, nhưng không chạy advice vì tên method không khớp.
- Lấy bean qua `context.getBean(...)` đảm bảo sử dụng đối tượng do Spring quản lý. `new OrderServiceImpl()` bên ngoài container không kích hoạt cơ chế này.

Ví dụ dùng interface `OrderService`, nên proxy Spring AOP thông thường có thể là JDK dynamic proxy. Vì thế lấy bean qua interface và không ép kiểu proxy thành `OrderServiceImpl`. Cấu hình `proxyTargetClass=true` mới yêu cầu proxy dựa trên class (với các hạn chế của class/method final).

## 4. “Tự tìm” khác gì “tự áp dụng cho tất cả”?

`DefaultAdvisorAutoProxyCreator` tìm **toàn bộ bean Advisor đủ điều kiện** (mặc định không lọc tên). Nó không tự động gắn advice của mọi Advisor lên mọi method:

- Nếu advisor dùng Pointcut hẹp, chỉ bean/method phù hợp mới chịu tác động.
- Nếu advisor được tạo với Pointcut bao phủ rộng, nhiều bean có thể được proxy. Chẳng hạn `DefaultPointcutAdvisor` dùng Pointcut mặc định `Pointcut.TRUE` trong cấu hình tương ứng sẽ có phạm vi rất rộng.
- Nếu trong container có ba Advisor nhưng với một bean chỉ có hai Advisor matching, proxy của bean đó nhận hai Advisor phù hợp. Việc lọc diễn ra cho **từng bean**, không phải một danh sách duy nhất cho toàn ứng dụng.
- Một Advisor được đăng ký sau khi các singleton đích đã hoàn tất tạo proxy sẽ không bảo đảm tự cập nhật các proxy trước đó; nên hoàn thiện đăng ký Advisor trước khi khởi tạo bean nghiệp vụ.

Đừng nhầm “Spring thấy một `MethodInterceptor`” với “Spring tự tạo Advisor cho interceptor đó”. Trong cấu hình của ví dụ, advice được **bọc bởi `DefaultPointcutAdvisor`**, vì creator này tìm bean `Advisor`.

## 5. Lọc tên advisor bằng usePrefix

Mặc định `usePrefix=false`: creator cân nhắc mọi bean `Advisor` đủ điều kiện trong BeanFactory. Khi cần một tập advisor theo tên, có thể bật `usePrefix`.

Cấu hình **thay thế** phần đăng ký creator và Advisor ở ví dụ trên (không đăng ký thêm creator thứ hai):

~~~java
@Bean(name = "routingCreator")
public static DefaultAdvisorAutoProxyCreator routingCreator() {
    DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
    creator.setUsePrefix(true);
    return creator;
}

@Bean(name = "routingCreator.orderAdvisor")
public Advisor orderAdvisor() {
    // Tạo và trả về PointcutAdvisor giống ví dụ đầy đủ ở mục 3.
    // Khi dùng thật, đặt phần thân tạo advisor tại đây.
    throw new UnsupportedOperationException("Ví dụ chỉ minh họa quy tắc đặt tên");
}
~~~

Đoạn trên **chỉ minh họa API và quy tắc đặt tên**, không phải chương trình độc lập để chạy. Khi `usePrefix=true`, prefix mặc định là **tên bean của creator + dấu chấm**: ở đây là `routingCreator.`. Vì vậy, tên `routingCreator.orderAdvisor` được xét, còn `otherAdvisor` thì bị loại ở bước chọn ứng viên theo tên. Có thể đặt prefix riêng bằng `setAdvisorBeanNamePrefix(...)`; cần giữ quy tắc prefix nhất quán.

Việc lọc prefix giới hạn **advisor được tìm**, không thay thế Pointcut. Một Advisor có tên hợp lệ vẫn phải vượt qua bước matching với class/method. Nếu bật prefix mà không có Advisor mang tên tương ứng, sẽ không có advisor áp dụng theo creator này.

## 6. Nhiều Advisor: kết hợp và thứ tự chạy

Giả sử bổ sung một `DefaultPointcutAdvisor` khác cũng khớp `placeOrder()`. Spring sẽ tổng hợp các advisor phù hợp cho `OrderServiceImpl`. Để xác định thứ tự, có thể đặt order trên đối tượng advisor:

~~~java
DefaultPointcutAdvisor metricsAdvisor =
        new DefaultPointcutAdvisor(metricsPointcut, metricsInterceptor);
metricsAdvisor.setOrder(10);

DefaultPointcutAdvisor auditAdvisor =
        new DefaultPointcutAdvisor(auditPointcut, auditInterceptor);
auditAdvisor.setOrder(20);
~~~

Đây là đoạn cấu hình minh họa, giả định bốn biến Pointcut/Interceptor đã được tạo. Số order nhỏ hơn có độ ưu tiên cao hơn trong chuỗi interceptor; với advice kiểu around, thành phần đứng ngoài sẽ xử lý phần *before* sớm hơn và phần *after* muộn hơn khi gọi `proceed()`. Những advisor không có thứ tự rõ ràng không nên bị phụ thuộc vào thứ tự xuất hiện ngẫu nhiên.

Cần phân biệt **thứ tự advisor trong proxy** với **thứ tự tạo bean** và **thứ tự các BeanPostProcessor**: chúng không phải cùng một phép sắp xếp.

## 7. Kiểm tra proxy và chẩn đoán không matching

Khi cần biết Spring đã gắn advisor nào, dùng API của Spring AOP:

~~~java
Object bean = context.getBean(OrderService.class);
System.out.println(AopUtils.isAopProxy(bean));

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

`AopUtils.isAopProxy(bean)` xác nhận đối tượng là Spring AOP proxy; `Advised#getAdvisors()` cho biết advisor đã gắn. Một số proxy có thể được cấu hình opaque nên giao diện `Advised` không nhất thiết luôn hiện diện. Kết quả có thể gồm advisor/hạ tầng khác nếu context bật thêm cơ chế AOP, giao dịch hoặc bảo mật.

Nếu advice không chạy, kiểm tra theo thứ tự:

1. **Đúng context chưa?** Bean đích và bean `Advisor` có cùng phạm vi `BeanFactory`/context phù hợp không? Không mặc định giả định advisor ở một context con sẽ được tìm từ context cha theo chiều ngược lại.
2. **Creator có đăng ký đủ sớm không?** `DefaultAdvisorAutoProxyCreator` phải là BeanPostProcessor do container quản lý. Đăng ký dạng `@Bean static` là lựa chọn an toàn trong cấu hình Java.
3. **Bean advisor có đúng kiểu không?** Một bean chỉ chứa `MethodInterceptor` mà không có `Advisor` sẽ không tham gia danh sách được tìm theo cơ chế này.
4. **Có bật usePrefix không?** Nếu bật, kiểm tra tên advisor có prefix tương ứng.
5. **Pointcut có match không?** Kiểm tra class, method name, tham số, kiểu khai báo và những giới hạn của phương thức có thể bị Spring AOP proxy. `NameMatchMethodPointcut` xét tên method, không phải tên bean.
6. **Lời gọi có đi qua proxy không?** Self-invocation như `this.placeOrder()` từ cùng target thường bỏ qua advice của Spring proxy; lời gọi trực tiếp tới đối tượng `new` cũng vậy.
7. **Loại proxy có phù hợp không?** JDK dynamic proxy bộc lộ interface; class-based proxy cần class/method có thể override. Private/final method không thể được advice theo cơ chế proxy override thông thường.
8. **Có nhiều cơ chế tạo proxy đồng thời không?** Cấu hình `@EnableAspectJAutoProxy`, transaction và creator tự đăng ký cần được kiểm soát; các cơ chế Spring có thể nâng cấp/hợp nhất auto-proxy creator nội bộ hoặc gây proxy chồng nếu thiết lập không hợp lý. Tránh đăng ký thêm creator khi framework đã có cơ chế đáp ứng nhu cầu.

Để xem Spring quyết định proxy trên thực tế, có thể tăng log cho `org.springframework.aop.framework.autoproxy` ở mức DEBUG/TRACE trong cấu hình logging của ứng dụng, tuỳ backend logger. Không kết luận chỉ từ việc `Advisor` tồn tại; hãy kiểm tra bean đích và lời gọi cụ thể.

## 8. Chọn cơ chế auto-proxy trong Spring

| Cơ chế | Cách chọn mục tiêu | Khi phù hợp |
| --- | --- | --- |
| `BeanNameAutoProxyCreator` | Chọn bean theo tên/biểu thức tên bean đã cấu hình; có thể áp dụng advice hoặc advisor đã chỉ định. | Muốn định tuyến AOP theo danh sách tên bean. |
| `DefaultAdvisorAutoProxyCreator` | Tự tìm bean `Advisor`, rồi xét Pointcut để chọn bean và method. | Đã xây dựng các `Advisor` rõ ràng và muốn chúng tự được áp dụng đúng nơi. |
| `AnnotationAwareAspectJAutoProxyCreator` | Bổ sung việc tạo advisor từ các lớp `@Aspect` cho quá trình auto-proxy. | Cấu hình Spring AOP bằng `@Aspect` / `@EnableAspectJAutoProxy`. |

Trong nhiều ứng dụng Spring Boot, AOP/transaction được cấu hình qua cơ chế sẵn có; việc tự thêm một `DefaultAdvisorAutoProxyCreator` cần có lý do cụ thể. Không nên xem nó là bước bắt buộc của mọi dự án Spring AOP.

## 9. Ghi nhớ khi thiết kế

- Dùng **Advisor** để kết hợp advice và điều kiện áp dụng, rồi để Spring chọn advisor cho từng bean; không để một Pointcut quá rộng áp dụng ngoài dự kiến.
- Đăng ký BeanPostProcessor đúng vòng đời, cung cấp advisor trước khi các singleton nghiệp vụ được khởi tạo và kiểm tra lại proxy bằng `AopUtils`.
- `usePrefix` là **bộ lọc theo tên bean advisor**, không phải bộ lọc bean nghiệp vụ.
- Phạm vi của proxy là **bean**; phạm vi thi hành advice là **method/lời gọi** sau khi match.
- Nếu cần diễn giải `@Aspect`, chọn cơ chế auto-proxy hỗ trợ `@AspectJ` thay vì giả định `DefaultAdvisorAutoProxyCreator` làm việc đó.
- Khi debug, tách rõ ba câu hỏi: **Advisor đã được phát hiện chưa? Bean có được proxy chưa? Method call có đi qua advisor chain không?**

## Tài liệu Spring để đối chiếu

- Spring Framework — AOP API / Auto-proxying: https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html
- Javadoc `DefaultAdvisorAutoProxyCreator`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/framework/autoproxy/DefaultAdvisorAutoProxyCreator.html
- Javadoc `AbstractAdvisorAutoProxyCreator`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAdvisorAutoProxyCreator.html
- Spring Framework — Proxying mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-003">Quan hệ giữa auto-proxy creator và BeanPostProcessor</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Quan hệ giữa auto-proxy creator và BeanPostProcessor

## Mục tiêu

Spring AOP có thể tự động trả về một **proxy** khi application gọi `ApplicationContext.getBean(...)`, dù lập trình viên không hề tạo `ProxyFactory` cho từng service. Cơ chế cốt lõi là **auto-proxy creator**, một nhóm thành phần của Spring AOP hoạt động trong vòng đời bean thông qua **BeanPostProcessor**.

Sau bài này, bạn có thể giải thích:

- Vì sao auto-proxy creator là một `BeanPostProcessor` đặc biệt, không phải một advisor hay aspect.
- Các pha tạo bean mà Spring có thể chèn proxy; đặc biệt là `postProcessAfterInitialization` và `getEarlyBeanReference`.
- Vai trò của `AbstractAutoProxyCreator`, `BeanNameAutoProxyCreator`, `DefaultAdvisorAutoProxyCreator` và `AnnotationAwareAspectJAutoProxyCreator`.
- Vì sao một số bean không đủ điều kiện auto-proxying do khởi tạo quá sớm.
- Cách kiểm tra Spring AOP proxy qua `AopUtils` và kiểm chứng hạ tầng bằng chương trình Java 17+ chạy được.

Bài học tập trung vào **Spring AOP trong Spring Framework**. Các khái niệm chung về AOP và lifecycle chỉ được đưa vào khi cần giải thích hành vi thật của Spring.

## 1. Quan hệ cốt lõi

`org.springframework.beans.factory.config.BeanPostProcessor` cho phép container xử lý **object bean** trong quá trình khởi tạo. Hai callback của interface:

~~~java
Object postProcessBeforeInitialization(Object bean, String beanName);

Object postProcessAfterInitialization(Object bean, String beanName);
~~~

Một `BeanPostProcessor` có thể giữ nguyên object, điều chỉnh object hoặc trả về **một object khác** để container sử dụng. Các implementation hiện đại có thể chỉ override những callback cần thiết; mặc định callback giữ nguyên `bean`.

Trong Spring AOP, `org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator` là base class quan trọng. Nó triển khai hạ tầng BeanPostProcessor và các interface mở rộng liên quan để phát hiện bean đủ điều kiện rồi **thay object target bằng Spring AOP proxy** khi thích hợp.

Ví dụ về sự khác nhau giữa các vai trò:

| Thành phần | Trách nhiệm |
| --- | --- |
| `BeanPostProcessor` | Can thiệp vào vòng đời object bean; có thể trả về object khác |
| Auto-proxy creator | Xác định bean nào cần AOP proxy và tạo proxy từ advisor/interceptor thích hợp |
| `Advisor` | Mang advice và, nếu là `PointcutAdvisor`, điều kiện class/method |
| `Pointcut` | Lọc lớp/phương thức được áp dụng advice |
| `MethodInterceptor` | Thực thi logic bổ sung trong interceptor chain khi caller gọi qua proxy |

**Không phải mọi BeanPostProcessor đều tạo proxy.** Ví dụ có processor chuyên inject dependency hoặc kiểm tra annotation. Ngược lại, những auto-proxy creator của Spring AOP sử dụng BeanPostProcessor như một cơ chế tích hợp vào container.

Một lớp được đánh dấu `@Aspect` cũng **không tự động là một BeanPostProcessor**: Spring dùng hạ tầng auto-proxy để biến khai báo aspect thành advisor và áp dụng lên các bean phù hợp.

## 2. Bean lifecycle có auto-proxy

Trình tự giản lược cho một singleton bean thông thường trong `ApplicationContext`:

~~~text
ApplicationContext.refresh()
        |
        v
Phân tích bean definitions;
chạy BeanFactoryPostProcessor nếu được đăng ký
        |
        v
Phát hiện, khởi tạo và đăng ký BeanPostProcessor
(bao gồm auto-proxy creator)
        |
        v
Tạo target bean bằng constructor/factory method
        |
        v
Inject dependency, populate properties
        |
        v
BeanPostProcessor.postProcessBeforeInitialization(...)
        |
        v
Initialization callbacks
(@PostConstruct / InitializingBean / init-method nếu có)
        |
        v
BeanPostProcessor.postProcessAfterInitialization(...)
        |
        +-- Bean không phù hợp -> trả target
        |
        +-- Bean phù hợp -> tạo và trả AOP proxy
        |
        v
getBean(...) hoặc nơi inject dependency nhận bean/proxy
        |
        v
Lời gọi business method qua proxy
        |
        v
Interceptor chain -> target
~~~

Đây là **đường đi điển hình**, không phải cam kết mọi bean đều đi qua từng nhánh. `FactoryBean`, scoped bean, initialization đặc biệt hoặc early singleton reference có thể tạo đường đi khác.

### Vì sao phải đăng ký auto-proxy creator sớm?

`ApplicationContext` tự tìm và đăng ký BeanPostProcessor trước khi khởi tạo phần lớn singleton nghiệp vụ. Nếu bean nghiệp vụ được tạo **trước pha đăng ký hoàn tất**, bean đó có thể không được đầy đủ các processor xử lý, trong đó có auto-proxy creator.

Spring có thể ghi cảnh báo:

~~~text
Bean 'exampleBean' of type [...] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying)
~~~

Đây là thông báo minh họa từ tài liệu Spring, **không phải output của chương trình bên dưới**. Nó thường gợi ý bean bị kéo vào pha khởi tạo BeanPostProcessor quá sớm; cần xem dependency trực tiếp hoặc factory method của processor.

## 3. Hệ phân cấp auto-proxy creator

Các kiểu liên quan trong Spring Framework 6.x:

~~~text
BeanPostProcessor
       ^
InstantiationAwareBeanPostProcessor
       ^
SmartInstantiationAwareBeanPostProcessor
       ^
AbstractAutoProxyCreator
       |
       +-- BeanNameAutoProxyCreator
       |
       +-- AbstractAdvisorAutoProxyCreator
                 |
                 +-- DefaultAdvisorAutoProxyCreator
                 |
                 +-- AspectJAwareAdvisorAutoProxyCreator
                           |
                           +-- AnnotationAwareAspectJAutoProxyCreator
~~~

Sơ đồ cố ý lược bớt các interface và superclass hỗ trợ khác như `Ordered`, `BeanFactoryAware`, `AopInfrastructureBean` và `ProxyProcessorSupport`. Nó chỉ cho thấy vì sao auto-proxy creator có thể tham gia các callback của lifecycle.

### `AbstractAutoProxyCreator`

Class nền tảng này xử lý các phần chung: bỏ qua một số bean hạ tầng, xem bean có đủ điều kiện proxy, lắp advisors/interceptors, quyết định tạo JDK dynamic proxy hay CGLIB, và phối hợp với early bean references khi cần. Các subclass quyết định **cách nhận diện bean/advisor đủ điều kiện**.

### `BeanNameAutoProxyCreator`

Chọn bean dựa trên **tên bean**. Có thể khai báo một tên cụ thể hoặc pattern tên được Spring hỗ trợ.

~~~java
BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
creator.setBeanNames("paymentService");
creator.setInterceptorNames("auditInterceptor");
~~~

Đây là **fragment**, chưa có bean `auditInterceptor` và chưa đăng ký creator vào context. `setInterceptorNames` nhận các **tên bean advice/advisor**, thay vì instance được truyền trực tiếp; cách thiết kế này cũng hỗ trợ advisor có scope khác nhau.

### `DefaultAdvisorAutoProxyCreator`

Tìm các bean `Advisor` từ container, xét khả năng áp dụng của advisor lên bean ứng viên, rồi tạo proxy nếu có advisor phù hợp. Với `PointcutAdvisor`, Spring xét class filter và method matcher. Khác biệt với loại theo tên:

| Auto-proxy creator | Chọn ứng viên theo | Thành phần cần |
| --- | --- | --- |
| `BeanNameAutoProxyCreator` | Tên bean/pattern tên | Những tên advice/advisor cấu hình trong `interceptorNames` |
| `DefaultAdvisorAutoProxyCreator` | Advisor thích hợp với bean và method | Các bean `Advisor` và pointcut tương ứng |
| `AnnotationAwareAspectJAutoProxyCreator` | Advisor bao gồm những advisor suy ra từ `@Aspect` | Hạ tầng @AspectJ-style và aspect beans phù hợp |

Không thêm nhiều auto-proxy creator trùng chức năng chỉ vì muốn “chắc chắn mọi aspect chạy”; Spring có cơ chế hợp nhất/nâng cấp hạ tầng auto-proxy trong các cấu hình chuẩn, và việc chồng chéo đăng ký thủ công dễ gây hành vi khó hiểu.

## 4. Ví dụ Java 17+: auto-proxy creator là BeanPostProcessor

Ví dụ tạo một `ApplicationContext` chứa hai bean:

- `paymentService` được `BeanNameAutoProxyCreator` chọn, nên caller nhận **AOP proxy**.
- `healthService` không khớp tên, nên không bị auto-proxy creator này xử lý.

Để nhìn lifecycle, ta thêm `InspectingProcessor`. Processor này chỉ in trạng thái proxy khi bean có tên `paymentService` đi qua hai callback.

### 4.1. Tạo dự án Maven

~~~text
spring-auto-proxy-lifecycle/
├── pom.xml
└── src/main/java/example/AutoProxyLifecycleDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-auto-proxy-lifecycle</artifactId>
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
                    <mainClass>example.AutoProxyLifecycleDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Tạo `src/main/java/example/AutoProxyLifecycleDemo.java`

~~~java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class AutoProxyLifecycleDemo {
    public interface PaymentService {
        String pay(String orderId);
    }

    public static class PaymentServiceImpl implements PaymentService {
        @Override
        public String pay(String orderId) {
            return "paid:" + orderId;
        }
    }

    public static class HealthService {
        public String ping() {
            return "pong";
        }
    }

    public static class InspectingProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(
                Object bean, String beanName) {
            if ("paymentService".equals(beanName)) {
                System.out.println("BPP before: " +
                        AopUtils.isAopProxy(bean));
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String beanName) {
            if ("paymentService".equals(beanName)) {
                System.out.println("BPP after: " +
                        AopUtils.isAopProxy(bean));
            }
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean
        public static BeanNameAutoProxyCreator autoProxyCreator() {
            BeanNameAutoProxyCreator creator =
                    new BeanNameAutoProxyCreator();
            creator.setBeanNames("paymentService");
            creator.setInterceptorNames("auditInterceptor");
            return creator;
        }

        @Bean
        public static InspectingProcessor inspectingProcessor() {
            return new InspectingProcessor();
        }

        @Bean("auditInterceptor")
        public MethodInterceptor auditInterceptor() {
            return invocation -> {
                System.out.println("ADVICE " +
                        invocation.getMethod().getName());
                return invocation.proceed();
            };
        }

        @Bean
        public PaymentService paymentService() {
            return new PaymentServiceImpl();
        }

        @Bean
        public HealthService healthService() {
            return new HealthService();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            PaymentService payment =
                    context.getBean("paymentService", PaymentService.class);
            HealthService health = context.getBean(HealthService.class);

            System.out.println("payment proxy: " +
                    AopUtils.isAopProxy(payment));
            System.out.println("payment JDK: " +
                    AopUtils.isJdkDynamicProxy(payment));
            System.out.println("health proxy: " +
                    AopUtils.isAopProxy(health));
            System.out.println("payment result: " + payment.pay("O-1"));
            System.out.println("health result: " + health.ping());
        }
    }
}
~~~

### 4.3. Lệnh chạy và output

Chạy tại thư mục chứa `pom.xml` với JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chính chương trình in ra, theo thứ tự trên đường khởi tạo thông thường:

~~~text
BPP before: false
BPP after: true
payment proxy: true
payment JDK: true
health proxy: false
ADVICE pay
payment result: paid:O-1
health result: pong
~~~

Output từ Maven hoặc logging của môi trường có thể xuất hiện riêng.

### 4.4. Phân tích

1. `@Bean static BeanNameAutoProxyCreator` cho phép Spring phát hiện type và khởi tạo BeanPostProcessor sớm, không buộc khởi tạo instance `AppConfig` chỉ vì factory method của processor.
2. `setBeanNames("paymentService")` khiến auto-proxy creator lựa chọn đúng bean có tên đó. `healthService` không match, nên không được proxy bởi creator này.
3. `setInterceptorNames("auditInterceptor")` tham chiếu tới interceptor bean. `MethodInterceptor.invoke` ghi `ADVICE pay` rồi gọi `proceed()` để tiếp tục đến target.
4. `InspectingProcessor` được đăng ký dưới dạng BPP không khai báo `Ordered`; còn `BeanNameAutoProxyCreator` thuộc nhóm `Ordered`. Trong ví dụ này, khi callback after-initialization của observer chạy, auto-proxy creator đã có cơ hội thay `paymentService` bằng proxy: kết quả chuyển từ `false` sang `true`.
5. `context.getBean(...)` trả một JDK proxy triển khai `PaymentService`. Caller chỉ thấy `ADVICE pay` khi **gọi method qua proxy**; việc tạo bean không tự động chạy `pay`.
6. `healthService` được lấy từ context dưới dạng service thường vì không match điều kiện chọn của creator.

**Chú ý về thứ tự:** Khi ứng dụng có nhiều BeanPostProcessor, một processor quan sát ở `postProcessAfterInitialization` có thể thấy target hoặc proxy tùy thứ tự được đăng ký và áp dụng. Vì vậy không nên suy từ một callback của bất kỳ BPP nào rằng Spring AOP luôn được gắn trước processor đó.

## 5. Đăng ký BeanPostProcessor đúng thời điểm

Auto-proxy creator là một `BeanPostProcessor`, nên Spring phải phát hiện và đăng ký nó **trước khi khởi tạo phần lớn singleton nghiệp vụ**. Một bean bị tạo ngay khi container đang khởi tạo các processor có thể không được xử lý bởi toàn bộ BPP, dẫn đến thiếu advice dù pointcut đúng.

Khi tự khai báo một BPP bằng Java configuration:

- Đặt kiểu trả về `@Bean` đủ cụ thể (chẳng hạn `BeanNameAutoProxyCreator` hoặc `BeanPostProcessor`) để Spring nhận diện nó sớm.
- Ưu tiên `static @Bean` để tạo processor mà không buộc khởi tạo sớm chính `@Configuration` instance.
- Giữ constructor/factory method của BPP ít dependency; đặc biệt tránh yêu cầu service nghiệp vụ ngay trong lúc đăng ký processor.

Ví dụ từ phần 4:

~~~java
@Bean
static BeanNameAutoProxyCreator autoProxyCreator() {
    BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
    creator.setBeanNames("paymentService");
    creator.setInterceptorNames("auditInterceptor");
    return creator;
}
~~~

`ApplicationContext` tự phát hiện BPP trong `refresh()`. Với `BeanFactory` cấp thấp, BPP có thể phải được thêm thủ công qua `addBeanPostProcessor(...)`; thứ tự thêm thủ công có quy tắc riêng. `BeanFactoryPostProcessor` là loại hook khác, thao tác trên **bean definitions** trước khi tạo instance.

Nếu Spring cảnh báo một bean **not eligible for auto-proxying**, phải truy nguyên BPP nào kéo bean đó vào giai đoạn sớm, kiểm tra object thực tế từ context và thử lời gọi qua proxy. Cảnh báo, nguyên nhân và cách sửa chuyên sâu được phân tích ở bài chẩn đoán bean không đủ điều kiện của chương.

## 6. Early bean reference: vì sao không chỉ có after-initialization?

### Trường hợp bình thường

Một bean thường hoàn tất initialization rồi auto-proxy creator mới bọc bean và trả proxy từ `postProcessAfterInitialization`. Đây là điều ví dụ minh họa.

### Khi circular dependency cần tham chiếu sớm

`AbstractAutoProxyCreator` còn tham gia `SmartInstantiationAwareBeanPostProcessor` với hook:

~~~java
Object getEarlyBeanReference(Object bean, String beanName);
~~~

Trong một số trường hợp singleton circular dependency mà Spring **cho phép**, container có thể cần đưa reference của bean A cho bean B trước khi A hoàn tất initialization. Nếu B nhận target nguyên bản nhưng sau đó Spring công bố AOP proxy cho A, B sẽ giữ reference bỏ qua AOP. Hook early reference cho phép auto-proxy creator cung cấp proxy sớm và phối hợp tránh khác biệt không cần thiết giữa reference sớm và object được công bố.

~~~text
Tạo A, A chưa init xong
   |
   +-- B cần tham chiếu tới A
   |      |
   |      +-- getEarlyBeanReference(A)
   |                 |
   |                 +-- có thể cung cấp AOP proxy sớm
   |
   +-- hoàn thành khởi tạo A
   |
   +-- công bố reference phù hợp với vòng đời
~~~

Hook này **không giải quyết mọi circular dependency**; constructor cycles thường không thể giải quyết theo cách đó. Spring Boot cũng có các chính sách riêng về circular references. Cách bền vững hơn thường là **thiết kế lại dependency graph** để tránh vòng phụ thuộc.

### Các callback không có nghĩa là luôn được gọi

- `postProcessBeforeInstantiation(...)`: hook thuộc `InstantiationAwareBeanPostProcessor` có thể can thiệp trước khi Spring khởi tạo target theo lộ trình thông thường.
- `getEarlyBeanReference(...)`: hook của `SmartInstantiationAwareBeanPostProcessor` khi một reference sớm được yêu cầu.
- `postProcessAfterInitialization(...)`: đường đi tiêu biểu để bọc bean đã được khởi tạo thành AOP proxy.

Một bean không nhất thiết đi qua cả ba hook. Không tự viết kiểm thử với giả định callback early reference luôn xuất hiện.

## 7. Liên hệ với @Aspect và các advisor khác

### Khi dùng `@EnableAspectJAutoProxy`

~~~java
@Configuration
@EnableAspectJAutoProxy
class AopConfig { }
~~~

Đây là fragment, cần import `Configuration` và `EnableAspectJAutoProxy`, khai báo `@Aspect` bean và có các dependency hỗ trợ @AspectJ-style (thường gồm AspectJ weaver) phù hợp với phiên bản Spring. Hạ tầng này đăng ký auto-proxy creator có khả năng phát hiện advisor từ các aspect beans.

- `@Aspect` đánh dấu nơi khai báo advice và pointcut.
- Auto-proxy creator (dựa trên BeanPostProcessor) xác định bean nào cần proxy.
- Proxy intercept lời gọi theo advisor/interceptor chain.
- `proxyTargetClass = true` trong `@EnableAspectJAutoProxy` điều khiển việc ưu tiên class proxy, không chuyển cơ chế Spring AOP thành bytecode weaving.

### Với `DefaultAdvisorAutoProxyCreator`

Ví dụ một advisor có pointcut:

~~~java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;

MethodInterceptor trace = invocation -> {
    System.out.println("Trace " + invocation.getMethod().getName());
    return invocation.proceed();
};
DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(Pointcut.TRUE, trace);
~~~

Đây là **fragment**. `Pointcut.TRUE` khớp rộng, nhưng **không tạo proxy một mình**. Nó cần được gắn qua `ProxyFactory` hoặc được một auto-proxy creator đánh giá từ context. Khi dùng `DefaultAdvisorAutoProxyCreator`, hãy giới hạn class/method bằng pointcut thích hợp để tránh advice lan sang các bean không mong muốn.

Spring có thể có transaction, caching, security và `@Aspect` advisors cùng tham gia AOP infrastructure. Việc tích hợp các cơ chế này cần xem **thứ tự và cấu hình auto-proxy cuối cùng** chứ không đoán từ thứ tự xuất hiện annotation.

## 8. BeanPostProcessor order khác Advisor order

Hai loại thứ tự thường bị nhầm:

| Loại | Thời điểm có ý nghĩa | Ảnh hưởng |
| --- | --- | --- |
| BeanPostProcessor order | Trong quá trình khởi tạo bean | Processor nào nhận target/proxy trước, khả năng bao object hoặc nhìn thấy object đã bị biến đổi |
| Advisor/interceptor order | Khi method được gọi qua proxy | Advice nào chạy trước/sau `proceed()`, transaction/retry/exception behavior |

Thay `@Order` trên một aspect/advisor có thể thay thứ tự interceptor; nó **không làm Spring quay lại auto-proxy** một bean đã được tạo trước khi auto-proxy creator được đăng ký.

Tương tự, đổi thứ tự BeanPostProcessor không bảo đảm thứ tự business advice giống như kỳ vọng; cần kiểm tra cấu hình advisor của proxy và lời gọi thực.

## 9. Chẩn đoán vì sao auto-proxy không hoạt động

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra |
| --- | --- | --- |
| `AopUtils.isAopProxy(bean)` trả `false` | Bean không match hoặc creator chưa xử lý | Bean name, advisor/pointcut, pha đăng ký BPP |
| Có cảnh báo không eligible for auto-proxy | Bean khởi tạo sớm trong pha BeanPostProcessor | Dependencies của BPP, `static @Bean`, return type |
| Có advisor nhưng không tạo proxy | Advisor không match bean/method, hoặc creator chưa kích hoạt | Kiểm tra `DefaultAdvisorAutoProxyCreator` và pointcut |
| `@Aspect` có tồn tại nhưng advice không chạy | Hạ tầng @AspectJ-style chưa bật, thiếu dependency, pointcut không match | Config, dependency, method và proxy |
| Proxy được tạo nhưng method không được advice | Self-invocation, gọi target trực tiếp, không match pointcut hoặc giới hạn JDK/CGLIB | Gọi qua bean lấy từ context; kiểm tra method/loại proxy |
| Bean dùng CGLIB dù có interface | Có cấu hình ép class proxy | Kiểm tra `proxyTargetClass`, Spring Boot AOP properties và `AopUtils.isCglibProxy` |
| Reference raw và proxy khác nhau | Có dependency cycle/early initialization | Early reference, sơ đồ dependency, bean lifecycle |

### Kiểm tra runtime

~~~java
import org.springframework.aop.support.AopUtils;

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
~~~

Đoạn trên là fragment với `bean` đã được lấy từ Spring context. Nếu proxy expose `Advised`, có thể kiểm tra:

~~~java
import org.springframework.aop.framework.Advised;

if (bean instanceof Advised advised) {
    System.out.println(advised.getAdvisors().length);
}
~~~

Chú ý `getAdvisors().length` chỉ là số advisor **gắn trên proxy**, không phải số advice chắc chắn chạy ở mọi method. Điểm cuối cần xác minh là **method được gọi qua proxy** và có match pointcut hay không.

Quy trình debug:

1. Kiểm tra `ApplicationContext` đã đăng ký auto-proxy creator đúng thời điểm.
2. Xác nhận bean không bị tạo quá sớm hoặc bị bỏ qua là hạ tầng AOP.
3. Kiểm tra chiến lược chọn bean: theo tên hay theo advisors/pointcuts.
4. Xác nhận object từ context là proxy bằng `AopUtils`.
5. Dùng `Advised` xem advisors khi có thể; không dựa vào điều này nếu proxy opaque.
6. Gọi business method qua proxy, kiểm tra behavior, kết quả và ngoại lệ.
7. Nếu có vòng phụ thuộc, đánh giá early references rồi tách dependency cycle.

## 10. Lỗi hiểu sai thường gặp

**“Auto-proxy creator là một @Aspect.”** Sai: auto-proxy creator là thành phần hạ tầng dựa trên BeanPostProcessor. Aspect chứa các khai báo advice/pointcut theo một phong cách cấu hình.

**“BPP chỉ có thể sửa field của bean.”** Sai: callback có thể trả một **object khác**, và auto-proxy là ví dụ của hành vi này.

**“Một advisor có `Pointcut.TRUE` làm mọi object được proxy.”** Sai: cần một proxy creator đang hoạt động và phạm vi bean cần được xem xét; pointcut chỉ là một phần điều kiện.

**“Mọi bean được proxy ở postProcessAfterInitialization.”** Sai: bean không phù hợp được giữ nguyên, một số bean khởi tạo sớm có thể bỏ lỡ BPP, và có các hook proxy sớm cho tình huống đặc biệt.

**“`getEarlyBeanReference` luôn giải quyết được circular dependency.”** Sai: Spring còn ràng buộc về pha tạo bean, constructor cycles và chính sách container.

**“Nếu có annotation @Aspect, không cần cấu hình Spring AOP.”** Sai trong Spring Framework thuần: cần đăng ký hạ tầng @AspectJ-style thích hợp. Spring Boot có thể auto-configure một số thành phần tùy dependency/property.

**“Có proxy tức mọi method đều được advice.”** Sai: lời gọi qua proxy, khả năng intercept, advisor chain và pointcut quyết định điều đó. `this.internalMethod()` thường bypass proxy.

**“Tăng thứ tự advice sửa được bean bị tạo quá sớm.”** Sai: cần sửa pha đăng ký BeanPostProcessor và dependencies gây eager initialization.

## 11. Bài tập tự kiểm tra

1. Trong ví dụ, đổi `setBeanNames("paymentService")` thành `setBeanNames("healthService")`. Bean nào thành proxy, dòng `ADVICE` xuất hiện khi gọi method nào?
2. Vì sao `@Bean static` và return type `BeanNameAutoProxyCreator` giúp container đăng ký processor sớm?
3. Vì sao `InspectingProcessor` có thể thấy `false` ở before-initialization và `true` ở after-initialization?
4. Nếu một BeanPostProcessor autowire trực tiếp service được kỳ vọng có AOP, rủi ro nào có thể xảy ra?
5. Phân biệt vai trò của `BeanNameAutoProxyCreator` và `DefaultAdvisorAutoProxyCreator`.
6. Vì sao `@Aspect` không đồng nghĩa với `BeanPostProcessor`?
7. `getEarlyBeanReference` có vai trò gì và vì sao nó không đảm bảo mọi vòng dependency đều thành công?
8. Một method không được advice dù `AopUtils.isAopProxy(bean)` là `true`: bạn kiểm tra gì tiếp theo?

## Tài liệu Spring chính thức

- [Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): BeanPostProcessor, đăng ký sớm, thứ tự và cảnh báo early initialization.
- [Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/autoproxy.html): BeanNameAutoProxyCreator và DefaultAdvisorAutoProxyCreator.
- [BeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html): chữ ký callback.
- [AbstractAutoProxyCreator Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html): Spring AOP auto-proxy base class.
- [SmartInstantiationAwareBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): early bean reference.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-004">Đăng ký advisor dạng bean để dùng chung trong container</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký Advisor thành bean để dùng chung trong Spring IoC container

## 1. Mục tiêu và ngữ cảnh Spring AOP

Khi dùng `ProxyFactory` thủ công, mỗi lần tạo proxy ta phải lấy target và tự gọi `addAdvisor(...)`. Trong ứng dụng có **Spring IoC container**, cách làm phổ biến hơn là đăng ký một `Advisor` thành **Spring bean**, bật hạ tầng **auto-proxy**, rồi để Spring tìm những service phù hợp và gắn advisor vào proxy của chúng.

Ví dụ, ứng dụng có `OrderService` và `CustomerService`. Cả hai có các phương thức bắt đầu bằng `save` cần được ghi audit trước khi chạy. Thay vì cấu hình riêng cho từng bean:

1. Tạo một `Pointcut` chọn tên phương thức `save*` và giới hạn các lớp mục tiêu.
2. Tạo `MethodBeforeAdvice` thực hiện audit.
3. Ghép chúng thành một `DefaultPointcutAdvisor` và trả về từ một phương thức `@Bean`.
4. Kích hoạt một auto-proxy creator của Spring, chẳng hạn `DefaultAdvisorAutoProxyCreator`.
5. Truy cập service **từ `ApplicationContext`**. Nếu advisor phù hợp, Spring cung cấp AOP proxy cho bean đó.

Một advisor bean có thể được **dùng lại bởi nhiều proxy/bean**; điều này không có nghĩa tất cả service dùng chung một target hay một proxy. Mỗi service giữ vai trò nghiệp vụ riêng, còn advisor chứa quy tắc áp dụng và advice chung.

Bài này chỉ tập trung vào **Spring AOP và việc đăng ký advisor trong Spring IoC**, không đi sâu vào khái niệm Aspect tổng quát.

## 2. Advisor bean đi vào auto-proxy như thế nào?

Sơ đồ:

```text
            Spring ApplicationContext
                     |
       +-------------+--------------+
       |             |              |
  auditAdvisor    orderService   customerService
   (@Bean)          (@Bean)         (@Bean)
       |             |              |
       +------> AutoProxyCreator <--+
                       |
              đánh giá Pointcut
                       |
         +-------------+-------------+
         |                           |
   OrderService proxy         CustomerService proxy
         |                           |
    OrderServiceImpl             CustomerServiceImpl
```

Các khái niệm cần phân biệt:

| Thành phần | API Spring | Vai trò |
| --- | --- | --- |
| `Advice` | `org.aopalliance.aop.Advice` và các subtype | Hành vi chạy trước/sau/quanh lời gọi |
| `Pointcut` | `org.springframework.aop.Pointcut` | Chọn class và method được áp dụng advice |
| `PointcutAdvisor` | `org.springframework.aop.PointcutAdvisor` | Kết hợp một pointcut và một advice |
| `DefaultPointcutAdvisor` | `org.springframework.aop.support.DefaultPointcutAdvisor` | Triển khai thông dụng, có thể khai báo trực tiếp thành bean |
| Auto-proxy creator | `org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator` | `BeanPostProcessor` tìm các advisor bean hợp lệ và tạo proxy cho bean phù hợp |
| Spring IoC | `ApplicationContext`/`BeanFactory` | Sở hữu bean definition, dependency injection và vòng đời bean |
| Proxy | JDK dynamic proxy hoặc CGLIB proxy | Đối tượng caller nhận từ container khi bean được advice |

Cần tách bạch **đăng ký advisor** với **kích hoạt proxy**: chỉ có `@Bean DefaultPointcutAdvisor` không đủ. Container phải có auto-proxy creator phù hợp. Khi hạ tầng đó hoạt động, nó xét các candidate `Advisor` và đối chiếu pointcut với bean đang được tạo.

### Tại sao Advisor khác một Advice bean thuần?

`MethodBeforeAdvice` hoặc `MethodInterceptor` chỉ mô tả hành vi. Với `DefaultAdvisorAutoProxyCreator`, Spring tìm **`Advisor` beans** để xác định áp dụng cho bean nào; một advice bean đứng riêng không mang pointcut nên không tự động được lựa chọn như advisor. Cách thông thường là xuất một `DefaultPointcutAdvisor(pointcut, advice)` thành bean.

Đối với advisor không cần giới hạn phương thức, `new DefaultPointcutAdvisor(advice)` mặc định dùng `Pointcut.TRUE`, tức là match các phương thức mà Spring proxy có thể intercept. Trong một container nhiều service, kiểu cấu hình đó có thể áp dụng quá rộng; nên ưu tiên pointcut và class filter rõ ràng.

## 3. Hai con đường kích hoạt auto-proxy trong Spring

### 3.1. DefaultAdvisorAutoProxyCreator: làm việc trực tiếp với Advisor beans

`DefaultAdvisorAutoProxyCreator` là một `BeanPostProcessor` chuyên tìm các `Advisor` trong container rồi tạo proxy cho những bean phù hợp. Đây là lựa chọn minh họa rõ nhất khi bạn học **Advisor API** mà không cần khai báo `@Aspect`.

Ví dụ cấu hình:

```java
@Configuration(proxyBeanMethods = false)
class AopConfiguration {

    @Bean
    static DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }

    @Bean
    DefaultPointcutAdvisor auditAdvisor() {
        // Tạo pointcut + advice, rồi trả về advisor.
        // Xem ví dụ chạy đầy đủ ở phần 5.
        return buildAuditAdvisor();
    }
}
```

Đây là *fragment* cấu hình, không phải chương trình độc lập: `buildAuditAdvisor()` đại diện cho một method tự định nghĩa. Sử dụng `static @Bean` cho auto-proxy creator là cách chủ động cho Spring đăng ký BeanPostProcessor sớm mà không buộc tạo sớm instance cấu hình chỉ để gọi factory method.

Khi Spring tạo những bean nghiệp vụ sau khi post-processor đã được đăng ký, `DefaultAdvisorAutoProxyCreator` xem xét các advisor hiện có. Bean không khớp bất kỳ advisor hợp lệ nào thường không cần tạo AOP proxy.

### 3.2. @EnableAspectJAutoProxy: hạ tầng AOP annotation-aware

Trong ứng dụng sử dụng `@Aspect`, Spring hỗ trợ:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class AopConfiguration {
    @Bean
    DefaultPointcutAdvisor auditAdvisor() {
        return buildAuditAdvisor();
    }
}
```

Đoạn này là fragment. `@EnableAspectJAutoProxy` kích hoạt hạ tầng annotation-aware auto-proxying, có thể áp dụng cả những `Advisor` bean phù hợp ngoài các `@Aspect`. Cần có dependency AspectJ Weaver trên classpath để dùng cơ chế này.

Trong ứng dụng Spring Boot có `spring-boot-starter-aop`, auto-proxy có thể đã được cấu hình. Kiểm tra cấu hình dự án trước khi đăng ký thêm creator thủ công. **Không tạo đồng thời nhiều auto-proxy creator cạnh tranh** cho cùng một bean factory để “chắc chắn AOP hoạt động”; điều đó có thể gây cấu hình khó dự đoán hoặc proxy lồng nhau.

Bài thực hành tiếp theo dùng *con đường 3.1* để quan sát trực tiếp quan hệ giữa `Advisor` bean và `DefaultAdvisorAutoProxyCreator`.

## 4. Tạo một advisor có phạm vi kiểm soát

Một advisor tái sử dụng nên có **cả điều kiện chọn phương thức lẫn điều kiện chọn class**:

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("save*");

pointcut.setClassFilter(type ->
        OrderService.class.isAssignableFrom(type)
        || CustomerService.class.isAssignableFrom(type));

MethodBeforeAdvice audit = (method, args, target) ->
        System.out.println("[audit] " + method.getName());

DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(pointcut, audit);
advisor.setOrder(50);
```

Fragment này cần các interface nghiệp vụ `OrderService` và `CustomerService` được khai báo (xem ví dụ ở phần 5).

- `NameMatchMethodPointcut` so khớp **tên method** với `save*`. Dấu `*` là wildcard đơn giản của Spring, không phải biểu thức regex/AspectJ.
- `setClassFilter(...)` giới hạn các target class phù hợp. Nhờ vậy, một bean unrelated có phương thức `saveSetting()` sẽ không bị audit.
- `MethodBeforeAdvice` ghi log ngay trước lời gọi method được Spring AOP intercept.
- `DefaultPointcutAdvisor` đóng gói pointcut và advice để auto-proxy creator có thể đánh giá, sau đó đưa advice vào chain.
- `setOrder(50)` đặt thứ tự ưu tiên advisor theo hợp đồng Spring `Ordered` nếu có nhiều advisor được hạ tầng sắp xếp. Một advisor duy nhất không cho thấy khác biệt về order.

Nếu một advice được dùng chung giữa nhiều proxy, trạng thái của advice cũng là **trạng thái có thể chia sẻ**. Lambda stateless trong ví dụ là phù hợp; nếu advice giữ bộ đếm hay bộ đệm mutable thì cần cân nhắc thread safety, khả năng đồng thời và vòng đời bean.

## 5. Ví dụ Java 17+ hoàn chỉnh: một advisor bean, nhiều service bean

Mục tiêu ví dụ: một `auditAdvisor` duy nhất áp dụng cho `saveOrder()` và `saveCustomer()` trên hai service; không áp dụng cho `find...`; một `UtilityService` cũng có method `saveSetting()` nhưng nằm ngoài class filter nên không bị advice.

Cấu trúc dự án:

```text
shared-advisor-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/SharedAdvisorDemo.java
```

Tệp `settings.gradle`:

```groovy
rootProject.name = 'shared-advisor-demo'
```

Tệp `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.12'
    implementation 'org.springframework:spring-aop:6.2.12'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.SharedAdvisorDemo'
}
```

`spring-context` cung cấp `AnnotationConfigApplicationContext` và `@Configuration`; `spring-aop` cung cấp advisor, advice, pointcut và auto-proxy creator. Mẫu dùng `DefaultAdvisorAutoProxyCreator` nên **không cần bật `@EnableAspectJAutoProxy` hay thêm AspectJ Weaver** chỉ để chạy ví dụ này.

Tệp `src/main/java/demo/SharedAdvisorDemo.java`:

```java
package demo;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class SharedAdvisorDemo {

    public interface OrderService {
        void saveOrder();
        void findOrder();
    }

    public interface CustomerService {
        void saveCustomer();
        void findCustomer();
    }

    public interface UtilityService {
        void saveSetting();
    }

    public static class OrderServiceImpl implements OrderService {
        @Override
        public void saveOrder() {
            System.out.println("[order] saveOrder");
        }

        @Override
        public void findOrder() {
            System.out.println("[order] findOrder");
        }
    }

    public static class CustomerServiceImpl implements CustomerService {
        @Override
        public void saveCustomer() {
            System.out.println("[customer] saveCustomer");
        }

        @Override
        public void findCustomer() {
            System.out.println("[customer] findCustomer");
        }
    }

    public static class UtilityServiceImpl implements UtilityService {
        @Override
        public void saveSetting() {
            System.out.println("[utility] saveSetting");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        // Spring nhận diện bean này là BeanPostProcessor.
        @Bean
        public static DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
            return new DefaultAdvisorAutoProxyCreator();
        }

        // Chỉ một Advisor bean được dùng lại cho các service phù hợp.
        @Bean
        public DefaultPointcutAdvisor auditAdvisor() {
            NameMatchMethodPointcut pointcut =
                    new NameMatchMethodPointcut();
            pointcut.setMappedName("save*");
            pointcut.setClassFilter(type ->
                    OrderService.class.isAssignableFrom(type)
                    || CustomerService.class.isAssignableFrom(type));

            MethodBeforeAdvice advice = (method, args, target) ->
                    System.out.println("[audit] " + method.getName());

            DefaultPointcutAdvisor advisor =
                    new DefaultPointcutAdvisor(pointcut, advice);
            advisor.setOrder(50);
            return advisor;
        }

        @Bean
        public OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        public CustomerService customerService() {
            return new CustomerServiceImpl();
        }

        @Bean
        public UtilityService utilityService() {
            return new UtilityServiceImpl();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService orders = context.getBean(OrderService.class);
            CustomerService customers = context.getBean(CustomerService.class);
            UtilityService utility = context.getBean(UtilityService.class);

            System.out.println("Order proxy: " + AopUtils.isAopProxy(orders));
            System.out.println("Customer proxy: " + AopUtils.isAopProxy(customers));
            System.out.println("Utility proxy: " + AopUtils.isAopProxy(utility));

            orders.saveOrder();
            orders.findOrder();
            customers.saveCustomer();
            customers.findCustomer();
            utility.saveSetting();
        }
    }
}
```

Chạy trong thư mục chứa `build.gradle` bằng JDK 17+ và Gradle:

```bash
gradle run
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Stdout từ chương trình, không bao gồm các thông báo Gradle:

```text
Order proxy: true
Customer proxy: true
Utility proxy: false
[audit] saveOrder
[order] saveOrder
[order] findOrder
[audit] saveCustomer
[customer] saveCustomer
[customer] findCustomer
[utility] saveSetting
```

Giải thích kết quả:

1. `AnnotationConfigApplicationContext` phát hiện `@Configuration`, đăng ký `DefaultAdvisorAutoProxyCreator` và các bean, rồi xây dựng context.
2. `auditAdvisor()` tạo **một singleton advisor bean**. Creator tìm advisor đó khi xem xét các service.
3. Với `OrderServiceImpl`, class filter khớp vì lớp triển khai `OrderService`; `saveOrder` khớp mẫu `save*`. Spring trả proxy và in `[audit] saveOrder` trước phương thức target.
4. Với `CustomerServiceImpl`, cơ chế tương tự: **cùng advisor** được sử dụng khi proxy hóa service thứ hai; `saveCustomer` chạy audit.
5. `findOrder` và `findCustomer` không khớp mẫu `save*`. Chúng vẫn được gọi qua proxy của service tương ứng nhưng không chạy audit.
6. `UtilityServiceImpl` có `saveSetting` khớp tên `save*`, nhưng lớp **không khớp class filter**. Do không có advisor phù hợp khác trong ví dụ, Spring không cần tạo AOP proxy cho utility; vì thế `Utility proxy: false` và không có dòng audit.
7. `context.close()` được kích hoạt bởi `try-with-resources`, kết thúc vòng đời bean do Spring container quản lý.

Hai service dùng cùng advisor bean **không đồng nghĩa chúng dùng cùng target**, và advice của một lời gọi không tự động chạy cho mọi lời gọi khác. Spring quyết định chain cho từng method theo pointcut.

## 6. Phân biệt đăng ký Advisor với thêm advice thủ công

Khi không dùng IoC, một đoạn cấu hình có thể như sau:

```java
ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
factory.addAdvisor(auditAdvisor);
OrderService proxy = (OrderService) factory.getProxy();
```

Đây chỉ là fragment, giả định `auditAdvisor` đã được tạo. Mã ứng dụng chịu trách nhiệm tạo proxy và gắn advisor.

Trong Spring IoC, pattern khác:

```java
@Bean
DefaultPointcutAdvisor auditAdvisor() {
    return new DefaultPointcutAdvisor(pointcut, advice);
}
```

Đây cũng là fragment, phụ thuộc `pointcut` và `advice` đã được khai báo. Auto-proxy creator sẽ tìm `Advisor` bean và áp dụng nếu phù hợp; không phải viết `factory.addAdvisor(...)` cho từng service.

Bảng so sánh:

| Vấn đề | `ProxyFactory` thủ công | Advisor bean + auto-proxy |
| --- | --- | --- |
| Ai tạo proxy? | Mã ứng dụng gọi `getProxy()` | Spring `BeanPostProcessor` tạo khi xây dựng bean |
| Ai gắn advisor? | Caller gọi `addAdvisor` | Auto-proxy creator xét các `Advisor` bean |
| Ai quản lý target lifecycle? | Bên tạo/owner của target | Spring IoC nếu target là Spring bean |
| Chia sẻ advisor cho nhiều target | Caller gắn vào mỗi factory | Advisor bean có thể được áp dụng tự động cho nhiều bean |
| Lấy object để gọi | Giữ object trả về từ `getProxy()` | Lấy từ `ApplicationContext`/inject theo kiểu phù hợp |
| Điều kiện để advice chạy | Qua proxy, pointcut match | Qua proxy do container tạo, pointcut match |

Không nên dùng `new OrderServiceImpl()` ở caller và kỳ vọng advisor bean tự áp dụng; đối tượng tự tạo bên ngoài container không đi qua auto-proxy creator.

## 7. Cấu hình phạm vi áp dụng để không “phủ toàn bộ container”

Advisor được đăng ký trong container không nhất thiết phải ảnh hưởng mọi bean. Phạm vi do `ClassFilter`, `MethodMatcher` và loại auto-proxy creator quyết định.

### 7.1. Lọc theo tên phương thức

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedNames("save*", "update*");
```

Mẫu tên đơn giản có thể chọn một nhóm method mà không xét annotation, tham số hoặc giá trị runtime. Đừng dùng kiểu `save.*` như regex khi `NameMatchMethodPointcut` yêu cầu `save*`.

### 7.2. Lọc theo class/contract

```java
pointcut.setClassFilter(type ->
        OrderService.class.isAssignableFrom(type)
        || CustomerService.class.isAssignableFrom(type));
```

Điều này giúp không vô tình áp dụng advisor lên một bean khác cũng có method tên `save...`.

### 7.3. Lọc theo bean name khác với pointcut class/method

`BeanNameAutoProxyCreator` là một lựa chọn khác nếu ranh giới áp dụng là **tên bean**; nó có thể được cấu hình với các bean name pattern và interceptor/advisor. `DefaultAdvisorAutoProxyCreator` ưu tiên đánh giá eligibility của **Advisor bean/Pointcut** để chọn target, phù hợp khi một advisor dùng cho các nhóm class và method có quan hệ kỹ thuật.

Một advisor bean đơn thuần không thể diễn đạt đầy đủ điều kiện dựa trên bean name chỉ bằng `NameMatchMethodPointcut`. Nếu yêu cầu chính là quy tắc tên bean, hãy chọn cơ chế auto-proxy phù hợp hoặc pointcut/class filter chuyên biệt.

### 7.4. Tránh advisor áp dụng ngoài ý muốn

- Đừng dùng `Pointcut.TRUE` cho logging/audit chuyên biệt nếu container có nhiều service.
- Đặt điều kiện class và method rõ ràng; kiểm thử ít nhất một method match và một method không match.
- Đặt tên bean advisor mô tả rõ ý nghĩa, ví dụ `orderAndCustomerAuditAdvisor`, thay vì tên quá chung khi có nhiều mô-đun.
- Nếu nhiều advisor cùng match, tất cả advisor hợp lệ có thể tham gia chain; cần quản lý thứ tự và tác dụng kết hợp.
- Không giả định method `private`, `final` hay self-invocation sẽ được intercept chỉ vì pointcut match tên.

## 8. Nhiều Advisor beans và thứ tự

Spring AOP có thể nhận nhiều advisor bean như audit, metrics và permission check. Các advisor hợp lệ cho cùng method được kết hợp thành interceptor chain theo cơ chế sắp xếp của hạ tầng AOP.

Ví dụ các bean advisor khác nhau:

```java
@Bean
DefaultPointcutAdvisor auditAdvisor() {
    DefaultPointcutAdvisor advisor =
            new DefaultPointcutAdvisor(auditPointcut, auditAdvice);
    advisor.setOrder(10);
    return advisor;
}

@Bean
DefaultPointcutAdvisor metricsAdvisor() {
    DefaultPointcutAdvisor advisor =
            new DefaultPointcutAdvisor(metricsPointcut, metricsAdvice);
    advisor.setOrder(20);
    return advisor;
}
```

Đây là fragment cấu hình, yêu cầu bốn biến pointcut/advice đã tồn tại. `setOrder` dựa trên `Ordered`; số nhỏ hơn có ưu tiên cao hơn trong quá trình sắp xếp advisor áp dụng của Spring. Nếu cả hai là around interceptor, interceptor ngoài chạy phần “before” trước, còn phần “after” thường quay về theo thứ tự ngược; kết quả thực tế còn tùy loại advice và logic `proceed()`.

Không lấy thứ tự khai báo `@Bean` hoặc alphabet của tên bean làm cam kết về thứ tự advice. Khi ứng dụng có advisor do transaction, cache hoặc security infrastructure tạo, hãy kiểm tra toàn bộ chain và thứ tự với bài test tích hợp.

## 9. Advisor bean và phạm vi bean trong Spring

Mặc định `@Bean` có scope **singleton trong ApplicationContext**. Vì vậy `auditAdvisor` ở ví dụ là một advisor object dùng chung; advice được giữ trong object đó cũng có thể được chia sẻ giữa nhiều proxy.

Điều này đem lại lợi ích:

- Tập trung cấu hình audit/metrics trong một chỗ.
- Các service mới khớp pointcut có thể nhận cùng logic mà không sửa từng service.
- Dễ kiểm thử pointcut/advice riêng và kiểm tra danh sách advisor thực tế.
- Có thể phối hợp với dependency injection của Spring cho chính advice, ví dụ logger, clock hoặc audit publisher.

Nhưng cũng có những ràng buộc:

- Advice có state mutable phải được thiết kế thread-safe nếu được dùng đồng thời.
- Advisor/advice có dependency Spring bean sẽ tuân theo quan hệ scope và lifecycle của những bean đó; cần tránh dùng dependency đã đóng.
- Không nên cố ý thay đổi cấu hình pointcut/advisor của singleton đang được dùng bởi nhiều proxy; interceptor chain có thể được cache theo phương thức. Hoàn thiện cấu hình trước khi công bố service.
- Nếu một advice cần trạng thái theo request, đừng lưu request-specific mutable fields trực tiếp trên singleton advice; dùng cơ chế lưu ngữ cảnh phù hợp.
- Singleton advisor **không ép các target bean thành singleton**. Scope target độc lập với scope advisor, còn AOP sẽ áp dụng theo khả năng proxy của từng bean.

Việc “dùng chung” nên được hiểu là **tái sử dụng cấu hình và hành vi cross-cutting**, không phải biến mọi service thành một instance duy nhất.

## 10. Cơ chế với @EnableAspectJAutoProxy và Spring Boot

Trong ứng dụng hiện đại, bạn có thể đã bật Spring AOP bằng `@EnableAspectJAutoProxy` hoặc Spring Boot starter.

Cấu hình điển hình:

```java
@Configuration
@EnableAspectJAutoProxy
class AopConfig {

    @Bean
    DefaultPointcutAdvisor auditAdvisor() {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("save*");

        MethodBeforeAdvice advice = (method, args, target) ->
                System.out.println("[audit] " + method.getName());

        return new DefaultPointcutAdvisor(pointcut, advice);
    }
}
```

Đây là *fragment*, cần import và dependency tương ứng. Annotation-aware auto-proxy creator của Spring cũng biết xem xét các `Advisor` bean, nên có thể khai báo những advisor API-style cạnh `@Aspect` trong cùng context.

**Nếu Spring Boot đã cung cấp auto-proxy infrastructure**, thông thường chỉ cần khai báo bean advisor; đừng tự thêm `DefaultAdvisorAutoProxyCreator` vào ứng dụng khi chưa hiểu creator đang được đăng ký. Cấu hình proxy class/interface trong Boot có thể khác ví dụ framework thuần; cần kiểm tra `spring.aop.proxy-target-class` và infrastructure liên quan.

Một số lưu ý:

- `@EnableAspectJAutoProxy` cần AspectJ Weaver trên classpath; `spring-boot-starter-aop` thường cung cấp các dependency cần thiết.
- Các auto-proxy creator có thể được Spring hợp nhất/nâng cấp theo cơ chế đăng ký nội bộ; không nên cố đăng ký nhiều creator cạnh tranh hoặc tạo proxy lồng nhau một cách tùy tiện.
- `@Transactional`, `@Cacheable` và Spring Security có advisor/infrastructure riêng. Dùng cơ chế chuyên dụng của Spring cho các khả năng này thay vì viết một `DefaultPointcutAdvisor` bắt chước tính năng giao dịch/bảo mật.
- Nếu advisor bean nằm trong một context riêng, child context hoặc module cấu hình chưa được import, nó có thể không được creator tại nơi tạo target nhìn thấy. Hãy đặt advisor trong phạm vi container phù hợp.

## 11. Quan sát và kiểm tra một Advisor bean đã được áp dụng

`AopUtils` và `Advised` giúp kiểm tra khi dùng Spring AOP:

```java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object service = context.getBean(OrderService.class);

System.out.println(AopUtils.isAopProxy(service));

if (service instanceof Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getSimpleName());
    }
}
```

Đây là fragment chẩn đoán, giả định `context` đã được khởi tạo. Khi proxy có `opaque=true`, nó có thể không công khai interface `Advised`, nên kiểm tra `instanceof` là cần thiết. Không nên hardcode kỳ vọng danh sách advisor chỉ có một item trong ứng dụng thực tế, vì Spring infrastructure cũng có thể thêm advisor riêng.

Để chứng minh tái sử dụng advisor bean, có thể kiểm tra từng proxy:

1. Lấy `OrderService` và `CustomerService` qua `context.getBean(...)`; xác nhận cả hai có AOP proxy khi advisor match.
2. Gọi lần lượt `saveOrder`, `findOrder`, `saveCustomer`, `findCustomer`.
3. Xác nhận chỉ hai method `save*` chạy dòng audit.
4. Lấy `UtilityService`, kiểm tra class filter khiến nó không bị advice.
5. Nếu một service không được proxy, xem advisor đã là Spring bean chưa, auto-proxy creator có hoạt động không, class filter và method matcher có match không.

**Lưu ý:** một bean chỉ có proxy khi infrastructure thấy cần và có thể proxy; có advisor bean trong container không đảm bảo mọi bean đều trở thành proxy.

## 12. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra hoặc sửa |
| --- | --- | --- |
| `@Bean Advisor` tồn tại nhưng không có audit | Chưa bật auto-proxy creator | Bật đúng một cơ chế auto-proxy phù hợp trong context |
| `@Bean MethodBeforeAdvice` tồn tại nhưng không tự áp dụng | Advice bean chưa được ghép thành Advisor bean | Dùng `DefaultPointcutAdvisor(pointcut, advice)` |
| Chỉ một service được audit | Service kia không khớp `ClassFilter`/`MethodMatcher` hoặc nằm ngoài phạm vi creator | Kiểm tra target class, method và context |
| Service phụ bị audit ngoài ý muốn | Dùng `Pointcut.TRUE` hoặc mẫu `save*` quá rộng | Thu hẹp class filter, method matcher |
| Mã tự `new ServiceImpl()` không chạy advice | Không lấy bean proxy từ Spring container | Sử dụng dependency injection hoặc `getBean` |
| `this.save()` bên trong cùng target không kích hoạt audit | Self-invocation bỏ qua proxy | Tổ chức lời gọi qua service/proxy phù hợp |
| Cast proxy về concrete implementation gây lỗi | JDK dynamic proxy thường chỉ triển khai interface | Injection theo interface hoặc cân nhắc proxy theo class |
| Advisor không thấy trong context con/cha mong đợi | Đăng ký cấu hình ở phạm vi BeanFactory khác | Đặt cấu hình advisor/creator phù hợp với context tạo bean |
| Thứ tự nhiều advice khó hiểu | Nhiều advisor match và có order khác nhau | Thiết lập order và kiểm tra chain qua test |
| Cấu hình AOP bị áp dụng hai lần | Tự tạo proxy quanh bean vốn đã được Spring proxy | Kiểm tra `AopUtils.isAopProxy` và infrastructure |
| Stateful advice gây lệch dữ liệu | Singleton advice bị gọi đồng thời | Thiết kế stateless/thread-safe hoặc tách scope dữ liệu |

Việc hạ tầng auto-proxy hoạt động thông qua `BeanPostProcessor` còn khiến **thời điểm đăng ký** quan trọng. Đăng ký creator quá muộn hoặc tạo bean nghiệp vụ sớm trong quá trình khởi tạo BeanPostProcessor có thể khiến chúng không đi qua đầy đủ auto-proxy processing. Trong cấu hình `@Bean` thủ công, dùng method `static @Bean` cho creator và tránh việc lấy service bean quá sớm trong post-processor initialization.

## 13. Khi nào nên chọn Advisor bean dùng chung?

Chọn cách này khi:

- Nhiều Spring-managed service phải dùng cùng một policy audit, tracing hoặc metrics.
- Bạn muốn điều kiện match được viết tập trung bằng `Pointcut` và `ClassFilter`.
- Dự án đã vận hành với AOP infrastructure và muốn cung cấp thư viện/advice tái sử dụng thông qua bean configuration.
- Cần bổ sung nhiều advisor độc lập và quản lý thứ tự bằng `Ordered`.

Nếu chỉ có một proxy thủ công ngoài container, `ProxyFactory` thường đơn giản hơn. Nếu cần mô tả pointcut theo annotation/AspectJ expression và codebase đang theo `@Aspect`, có thể dùng cơ chế `@Aspect` của Spring. Cả hai mô hình có thể cùng tồn tại trong một context khi cấu hình hạ tầng AOP thống nhất.

Một checklist trước khi triển khai:

1. Bean `Advisor` đã được đăng ký đúng context chưa?
2. Có auto-proxy creator được kích hoạt và đăng ký đủ sớm chưa?
3. Class filter và method matcher có đúng phạm vi không?
4. Caller đang lấy bean từ container hay tạo target bằng `new`?
5. Advice dùng chung có stateless/thread-safe và tương thích lifecycle không?
6. Nếu nhiều advisor match, thứ tự và kết quả có được kiểm thử không?

## 14. Tài liệu Spring Framework chính thức

- [Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/autoproxy.html): cơ chế `DefaultAdvisorAutoProxyCreator` tự tìm Advisor beans và áp dụng cho nhiều bean phù hợp.
- [Spring AOP APIs: Advisor](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html): quan hệ `Advisor`, `Pointcut` và `Advice`.
- [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy`, dependency AspectJ Weaver và cơ chế auto-proxy.
- [DefaultAdvisorAutoProxyCreator Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/framework/autoproxy/DefaultAdvisorAutoProxyCreator.html): `BeanPostProcessor` tự động tìm và xử lý candidate `Advisor`.
- [DefaultPointcutAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/support/DefaultPointcutAdvisor.html): constructor, pointcut và order.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB và giới hạn của Spring AOP proxy.

**Ghi nhớ:** Muốn một advisor dùng chung trong Spring container, hãy **đăng ký nó thành bean** và bảo đảm **hạ tầng auto-proxy đang hoạt động**. Spring sẽ so pointcut với từng bean/method, tạo proxy khi cần và dùng cùng policy advice trên những service đủ điều kiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-005">Vai trò các thành phần auto-proxy dành cho @AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò các thành phần auto-proxy dành cho @AspectJ trong Spring AOP

## 1. Bức tranh tổng thể

Khi đặt `@Aspect`, `@Around`, `@Before` hoặc một advice annotation khác lên class Java, ta **mô tả** các advice và pointcut. Spring vẫn cần hạ tầng để tìm các aspect bean, chuyển các advice method thành `Advisor`, xác định bean nào cần proxy và tạo proxy phù hợp. Nhóm thành phần xử lý công việc đó được gọi là **auto-proxy infrastructure**.

Trong bài này, **@AspectJ** chỉ kiểu khai báo aspect bằng annotation của AspectJ được Spring AOP hỗ trợ. Lời gọi được can thiệp bằng **Spring AOP proxy**, không cần biên dịch bằng `ajc` và không tự bật AspectJ load-time weaving. Thư viện `aspectjweaver` vẫn cần trên classpath để Spring phân tích và đối sánh các biểu thức pointcut kiểu AspectJ.

Các mục tiêu cụ thể:

- Phân biệt vai trò của `@EnableAspectJAutoProxy`, `AspectJAutoProxyRegistrar` và `AnnotationAwareAspectJAutoProxyCreator`.
- Hiểu dòng xử lý `@Aspect` → `Advisor` → tìm bean phù hợp → tạo proxy → chạy interceptor.
- Biết `BeanFactoryAspectJAdvisorsBuilder`, `ReflectiveAspectJAdvisorFactory`, `AbstractAdvisorAutoProxyCreator` và `AspectJAwareAdvisorAutoProxyCreator` nằm ở đâu trong kiến trúc.
- Kiểm chứng qua Spring context thực: service bị proxy, bean khác giữ nguyên, hai advice theo thứ tự và việc tự gọi nội bộ không đi qua proxy.
- Nhận ra những trường hợp có aspect bean nhưng advice không chạy.

Phạm vi là **Spring Framework AOP**, đặc biệt Spring 6.x. Tên các lớp hạ tầng giúp đọc stack trace và chẩn đoán; mã ứng dụng thông thường nên sử dụng annotation/cấu hình cấp cao thay vì khởi tạo thủ công các class nội bộ.

## 2. Các thành phần và trách nhiệm

| Thành phần | Vai trò trong Spring AOP |
| --- | --- |
| `@EnableAspectJAutoProxy` | Bật hỗ trợ Spring AOP cho `@AspectJ` trong application context hiện tại |
| `AspectJAutoProxyRegistrar` | Được import bởi annotation trên, đăng ký hoặc nâng cấp bean definition của auto-proxy creator |
| `AopConfigUtils` | Tiện ích cấu hình dùng để đăng ký/nâng cấp creator dùng chung, xử lý các thiết lập như `proxyTargetClass` và `exposeProxy` |
| `AnnotationAwareAspectJAutoProxyCreator` | Auto-proxy creator hiểu cả Spring `Advisor` bean và aspect khai báo bằng `@Aspect` |
| `BeanFactoryAspectJAdvisorsBuilder` | Dò các aspect bean và xây dựng danh sách Spring `Advisor` đại diện cho advice method |
| `ReflectiveAspectJAdvisorFactory` | Phân tích metadata `@Aspect`, `@Pointcut`, `@Around`, `@Before`... và tạo advisor/advice tương ứng |
| `AbstractAdvisorAutoProxyCreator` | Tìm advisor có thể áp dụng cho từng bean, chọn ứng viên và tổ chức chúng theo thứ tự |
| `AspectJAwareAdvisorAutoProxyCreator` | Bổ sung quy tắc ưu tiên/điều kiện của advice AspectJ và ngữ cảnh invocation |
| `AbstractAutoProxyCreator` | Cung cấp hạ tầng `BeanPostProcessor` để quyết định và tạo Spring AOP proxy |
| `AspectJExpressionPointcut` | Spring `Pointcut` dùng biểu thức AspectJ để lọc class/method (và điều kiện runtime khi cần) |
| `ProxyFactory` / Spring `AopProxy` | Xây dựng proxy thực tế theo interface hoặc class, gắn advisor và target source |
| `Advisor` / `MethodInterceptor` | Đại diện advice có điều kiện; được chuyển thành chuỗi xử lý khi method được gọi |

**Không phải tất cả các hàng trong bảng là những bean độc lập do người dùng đăng ký.** Nhiều thành phần là object hỗ trợ bên trong auto-proxy creator. Chẳng hạn `BeanFactoryAspectJAdvisorsBuilder` là helper do creator sử dụng, còn `AnnotationAwareAspectJAutoProxyCreator` chính là một bean hạ tầng có vai trò `BeanPostProcessor`.

Một số class thuộc gói `org.springframework.aop.aspectj.annotation`, `org.springframework.aop.aspectj.autoproxy`, `org.springframework.aop.framework.autoproxy` hoặc `org.springframework.context.annotation`. Vị trí package phản ánh trách nhiệm; ứng dụng thường chỉ cần import `@EnableAspectJAutoProxy`, `@Aspect`, advice annotations và các annotation Spring container.

## 3. Sơ đồ vòng đời từ cấu hình đến lời gọi

```text
@Configuration + @EnableAspectJAutoProxy
                |
                v
   AspectJAutoProxyRegistrar / AopConfigUtils
                |
       đăng ký auto-proxy creator
                |
                v
 AnnotationAwareAspectJAutoProxyCreator
     (BeanPostProcessor hạ tầng)
                |
       +--------+---------+
       |                  |
       v                  v
 Spring Advisor bean   @Aspect bean
       |                  |
       |       BeanFactoryAspectJAdvisorsBuilder
       |                  |
       |       ReflectiveAspectJAdvisorFactory
       |                  |
       +--------+---------+
                |
          danh sách Advisor
                |
                v
  Chọn advisor phù hợp với bean class
  qua ClassFilter / MethodMatcher,
  xử lý precedence / order
                |
        có advisor áp dụng?
         /               \
       không              có
        |                  |
        v                  v
    bean gốc        tạo Spring AOP proxy
                           |
                           v
              ApplicationContext trả proxy
                           |
                           v
                  Client gọi method
                           |
                           v
                interceptor chain
                           |
                           v
                    target method
```

Hai pha cần phân biệt:

1. **Khi container khởi tạo bean:** Spring nhận diện các advisor/aspect, xét bean nào có thể được advice, chuẩn bị và trả proxy khi cần. Một số bước có thể diễn ra lazily/có caching, nên không suy ra số lần gọi helper từ sơ đồ.
2. **Khi client gọi method qua proxy:** Spring xác định các interceptor thích hợp cho method và điều kiện runtime của lời gọi, rồi chạy `proceed()` qua chain đến target.

Spring không nhất thiết tạo proxy cho **mọi** bean chỉ vì context bật `@EnableAspectJAutoProxy`. Những bean không khớp advisor, bean hạ tầng và bean không đủ điều kiện có thể được trả trực tiếp; số lượng advisor và điều kiện pointcut quyết định việc proxy thực sự.

## 4. @EnableAspectJAutoProxy và AspectJAutoProxyRegistrar

Cấu hình Java tối thiểu:

```java
@Configuration
@EnableAspectJAutoProxy
class AopConfiguration {
}
```

**`@EnableAspectJAutoProxy`** thuộc `org.springframework.context.annotation`. Nó yêu cầu Spring đọc các aspect bean kiểu `@AspectJ` và chủ động proxy những bean phù hợp. Annotation này sử dụng `@Import(AspectJAutoProxyRegistrar.class)`; registrar đăng ký định nghĩa auto-proxy creator trong BeanFactory.

Ở Spring 6.x, hạ tầng dùng tên bean nội bộ phổ biến:

```text
org.springframework.aop.config.internalAutoProxyCreator
```

Tên đó được biểu diễn bằng `AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME`. Đây là **bean hạ tầng**, không phải bean service mà client cần inject. Nó có nhiệm vụ tham gia vòng đời khởi tạo các bean khác.

Nếu context có nhiều cơ chế AOP như transaction và `@AspectJ`, Spring có logic đăng ký/nâng cấp creator dùng chung và hợp nhất cấu hình proxy; không nên tự tạo vài auto-proxy creator cạnh tranh nhau cho cùng một context chỉ để “đủ tính năng”. Khi một nơi yêu cầu class proxy (`proxyTargetClass=true`), cấu hình chung có thể làm những proxy liên quan cũng chọn cách proxy theo class.

Hai tùy chọn đáng biết:

```java
@EnableAspectJAutoProxy(
    proxyTargetClass = false,
    exposeProxy = false
)
```

- `proxyTargetClass=false`: cho phép chọn JDK interface proxy khi bean có interface phù hợp, tùy chính sách cấu hình. `true` ưu tiên class-based proxy (Spring thường dùng CGLIB); method final/private và các ràng buộc class proxy vẫn có giới hạn.
- `exposeProxy=false`: mặc định không expose proxy hiện tại qua `AopContext.currentProxy()`; `true` cho phép sử dụng cơ chế đó trong phạm vi invocation thích hợp, có chi phí và coupling. Không nên bật chỉ để chữa mọi vấn đề self-invocation; thường tách trách nhiệm hoặc đi qua bean proxy rõ ràng hơn.

**Phạm vi context:** `@EnableAspectJAutoProxy` áp dụng trong **application context khai báo nó**. Nếu ứng dụng có parent/child context, bật ở một context không tự động đồng nghĩa mọi bean trong context khác đều được auto-proxy bởi creator đó. Kiểm tra context nào sở hữu service và aspect khi advice không chạy.

**Không tự đăng ký aspect bean:** `@EnableAspectJAutoProxy` chỉ bật hạ tầng; class gắn `@Aspect` vẫn phải trở thành Spring bean, ví dụ nhờ `@Component` cùng component scan hoặc method `@Bean`. Chỉ đặt `@Aspect` lên class không nằm trong context thì creator không có aspect bean để xử lý.

Ngoài Java config, cấu hình XML `<aop:aspectj-autoproxy/>` cung cấp ý nghĩa tương tự: Spring đăng ký hạ tầng proxy cho các aspect bean kiểu @AspectJ.

## 5. AnnotationAwareAspectJAutoProxyCreator: bộ điều phối chính

`org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator` là subclass của `AspectJAwareAdvisorAutoProxyCreator`. Nó mở rộng cơ chế auto-proxy dựa trên Spring `Advisor` sẵn có bằng khả năng **nhận diện `@Aspect` bean và biến advice annotation thành advisor**.

Ở mức khái niệm, bước tìm advisor có dạng:

```java
// Pseudocode giải thích hành vi, không phải bản chép triển khai nội bộ.
List<Advisor> findCandidates() {
    List<Advisor> candidates = findSpringAdvisorBeans();
    candidates.addAll(buildAdvisorsFromAspectJBeans());
    return candidates;
}
```

Ở Spring thực, creator phối hợp:
- Luồng do superclass cung cấp để tìm **Spring Advisor bean** trong BeanFactory (ví dụ advisor dùng bởi infrastructure khác).
- `BeanFactoryAspectJAdvisorsBuilder.buildAspectJAdvisors()` để bổ sung các advisor sinh từ các `@Aspect` bean.

Do đó một bean service có thể đồng thời nhận advice do `@Aspect` định nghĩa và advisor do Spring hạ tầng khác đăng ký. Một method được proxy không có nghĩa chỉ duy nhất một aspect áp dụng cho nó.

**Tại sao không khởi tạo creator bằng `new` trong ứng dụng?** Creator cần tích hợp BeanFactory, lifecycle `BeanPostProcessor`, điều kiện loại trừ bean hạ tầng và cấu hình dùng chung. Sử dụng `@EnableAspectJAutoProxy` cho Java config (hoặc cơ chế auto-config của Spring Boot đã thiết lập AOP) giúp container quản lý đúng hạ tầng.

### Chuỗi kế thừa có ý nghĩa

```text
AbstractAutoProxyCreator
   ^
   |
AbstractAdvisorAutoProxyCreator
   ^
   |
AspectJAwareAdvisorAutoProxyCreator
   ^
   |
AnnotationAwareAspectJAutoProxyCreator
```

- `AbstractAutoProxyCreator` đảm nhiệm phần chung của `BeanPostProcessor`, proxy config và thời điểm wrap bean.
- `AbstractAdvisorAutoProxyCreator` biết tìm và lọc `Advisor` dựa trên bean.
- `AspectJAwareAdvisorAutoProxyCreator` thêm kiến thức về precedence của các advice khai báo theo phong cách AspectJ, cũng như ngữ cảnh invocation.
- `AnnotationAwareAspectJAutoProxyCreator` nhận diện và xây advisor từ các `@Aspect` bean.

**Đây là mô tả kiến trúc**, không phải API extension bắt buộc cho ứng dụng. Khi đọc log hoặc stack trace, tên superclass có thể xuất hiện dù developer chỉ dùng `@Aspect`.

## 6. BeanFactoryAspectJAdvisorsBuilder và ReflectiveAspectJAdvisorFactory

### 6.1. BeanFactoryAspectJAdvisorsBuilder tìm aspect bean

`BeanFactoryAspectJAdvisorsBuilder` sử dụng `ListableBeanFactory` để tìm những bean mang metadata `@Aspect` được hỗ trợ, quản lý thông tin về aspect bean và xây dựng các advisor. Với aspect singleton thông thường, những thông tin advisor có thể được tái sử dụng thay vì phân tích mọi annotation cho mỗi invocation.

Nó không quyết định toàn bộ proxy nào được tạo; vai trò của builder là **chuẩn bị ứng viên advisor từ các aspect**. Bước tìm xem advisor nào áp dụng cho một target bean thuộc tầng auto-proxy creator.

### 6.2. ReflectiveAspectJAdvisorFactory chuyển annotation thành advisor

Khi thấy các advice annotations, Spring không gọi tùy tiện mọi method của aspect. `ReflectiveAspectJAdvisorFactory` phân tích metadata, thiết lập pointcut và bao advice trong các đối tượng Spring AOP thích hợp.

Ví dụ aspect minh họa:

```java
@Aspect
class LoggingAspect {
    @Pointcut("execution(* example.service..*.*(..))")
    void serviceOperation() {}

    @Before("serviceOperation()")
    public void beforeCall() {
        System.out.println("Before service call");
    }
}
```

Đoạn này chỉ có **aspect class**, chưa phải chương trình độc lập: muốn chạy phải đăng ký class thành Spring bean, bật hỗ trợ auto-proxy và tạo service method khớp pointcut. Khi được khai báo hợp lệ, Spring có thể biểu diễn advice method bằng một Spring `Advisor` chứa pointcut và advice implementation thích hợp (ví dụ `AspectJMethodBeforeAdvice`).

Các loại advice khác được Spring thích nghi tương ứng, chẳng hạn `@Around` với `AspectJAroundAdvice`. Cơ chế đó cho phép các annotation kiểu AspectJ tham gia Spring `Advisor`/`MethodInterceptor` chain. Không nên tự instantiate những advice adapter nội bộ trong business code.

### 6.3. Pointcut expression vẫn dùng cơ chế của Spring AOP

`AspectJExpressionPointcut` hiểu biểu thức như `execution(...)`, `within(...)`, `@annotation(...)` và điều kiện runtime được Spring AOP hỗ trợ. Pointcut được dùng để:

1. Kiểm tra class target có khả năng khớp.
2. Kiểm tra method tĩnh.
3. Nếu cần, kiểm tra runtime theo đối số khi invocation thực sự đi qua proxy.

Điểm quan trọng: một biểu thức `execution(...)` có thể chọn method, nhưng `@AspectJ` trong Spring AOP chỉ can thiệp vào **method execution join point đi qua Spring proxy**. Việc dùng cú pháp AspectJ không tự mở rộng Spring proxy thành full AspectJ weaving cho constructor, field access hoặc self-invocation.

## 7. AbstractAdvisorAutoProxyCreator và cơ chế chọn proxy

Khi Spring khởi tạo một bean thường, auto-proxy creator cần trả lời: **bean này có Advisor phù hợp không?**

Với `AbstractAdvisorAutoProxyCreator`, logic khái niệm:

```text
Bean đang khởi tạo: PaymentServiceImpl
    |
    v
Lấy candidate advisors:
  - Advisor bean Spring
  - Advisors từ @AspectJ
    |
    v
Filter advisor bằng class filter / method matcher
    |
    +-- không advisor phù hợp --> trả bean gốc
    |
    +-- có advisor phù hợp --> sắp xếp/chuẩn bị advisor
                                   |
                                   v
                       Spring AOP ProxyFactory
                                   |
                                   v
                         proxy thay mặt bean
```

Phần này giải thích vì sao **có `@Aspect` bean không đảm bảo mọi bean được proxy**. Pointcut chỉ nhắm vào một tập class/method. Bean ngoài tập đó thường được giữ nguyên.

### BeanPostProcessor và thời điểm wrap

`AbstractAutoProxyCreator` được tích hợp như một `BeanPostProcessor` của BeanFactory. Trong trường hợp điển hình, khi bean đã trải qua quá trình khởi tạo thích hợp, auto-proxy creator cân nhắc wrap bean bằng proxy và container trả proxy cho bên dùng. Một số chu trình có **early reference** để hỗ trợ phụ thuộc vòng tròn; vì vậy không nên ràng buộc nhận định rằng Spring lúc nào cũng gọi một callback duy nhất hay luôn tạo proxy đúng một thời điểm cứng.

Spring còn loại trừ nhiều loại **infrastructure bean** (advisor, advice, pointcut, auto-proxy creator, aspect bean) khỏi quá trình proxy thông thường để tránh tự proxy hạ tầng và gây đệ quy. Đây là lý do aspect bean thường không phải đối tượng được advice bởi chính hạ tầng @AspectJ vừa tạo.

### Tạo proxy theo interface hoặc class

Khi cần proxy, Spring sử dụng `ProxyFactory` cùng cấu hình target/advisor/interface để tạo proxy. Với interface phù hợp, JDK dynamic proxy thường được chọn; khi yêu cầu proxy theo class, Spring dùng cơ chế class-based proxy. Spring Boot hoặc những annotation AOP khác có thể đã tác động đến cấu hình lựa chọn, nên trong debug cần xem proxy **thực tế** bằng `AopUtils.isJdkDynamicProxy(bean)` và `AopUtils.isCglibProxy(bean)` thay vì dựa vào giả định.

## 8. AspectJAwareAdvisorAutoProxyCreator và thứ tự advice

Khi nhiều advisor khớp cùng method, Spring phải xếp thứ tự theo precedence được hỗ trợ. `AspectJAwareAdvisorAutoProxyCreator` hiểu thêm ngữ nghĩa ưu tiên giữa advice có nguồn gốc từ các aspect.

Các quy tắc thực dụng:

- Với hai aspect khác nhau, `@Order` hoặc `Ordered` giúp khai báo ưu tiên. **Giá trị số thấp hơn tương ứng precedence cao hơn**.
- Với hai `@Around` thuộc hai aspect khác nhau, aspect ưu tiên cao hơn đi **vào trước** và **ra sau** do `ProceedingJoinPoint.proceed()` lồng nhau.
- Nếu cần thứ tự ổn định, đừng để hai aspect liên quan phụ thuộc vào default/số order bằng nhau.
- Advice có kiểu khác nhau (`@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`) có ngữ nghĩa thời điểm chạy khác nhau; không diễn giải mọi advice như một interceptor around luôn có hai nhánh “vào/ra”.
- Spring có thể chèn advisor/hạ tầng bổ sung, nên `Advised.getAdvisors()` trên bean proxy và trace thực tế là bằng chứng tốt hơn việc đếm method có annotation trong source.

Đừng nhầm hai công việc: **builder chuyển annotation thành advisor**; **auto-proxy creator tìm advisor phù hợp và tổ chức chuỗi**. Những thành phần đó phối hợp nhưng có trách nhiệm khác nhau.

## 9. Ví dụ Java 17+ chạy được: từ @EnableAspectJAutoProxy đến trace

Ví dụ chủ động tạo `AnnotationConfigApplicationContext` (không cần Spring Boot) với:

- Một service có method `greet` khớp pointcut.
- Một bean khác chỉ có `ping`, không khớp pointcut và không cần AOP proxy.
- Hai `@Aspect` bean dùng `@Around` với `@Order(10)` và `@Order(20)`.
- Kiểm tra rằng auto-proxy creator đã được đăng ký, service thực sự là Spring AOP proxy, còn bean không khớp giữ nguyên.

### 9.1. Cấu hình Gradle

**`settings.gradle`:**

```groovy
rootProject.name = 'spring-aspect-autoproxy-lab'
```

**`build.gradle`:**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.21'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'example.AutoProxyDemo'
}
```

`spring-context` mang các API container của Spring và phụ thuộc `spring-aop`; `aspectjweaver` cung cấp parser/metadata cần cho @AspectJ pointcut. Đây vẫn là Spring AOP proxy, **không** chạy load-time weaver. Tùy môi trường triển khai, hãy quản lý tất cả phiên bản Spring thống nhất bằng BOM/Boot để tránh lẫn minor version.

### 9.2. Java source

**`src/main/java/example/AutoProxyDemo.java`:**

```java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

public final class AutoProxyDemo {

    public interface GreetingService {
        String greet(String name);
    }

    public static final class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            System.out.println("TARGET greet " + name);
            return "Hello " + name;
        }
    }

    public static final class PlainTool {
        public String ping() {
            System.out.println("TARGET ping");
            return "pong";
        }
    }

    @Aspect
    @Order(10)
    public static final class TraceAspect {
        @Around("execution(* example..*.greet(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("TRACE enter");
            try {
                return pjp.proceed();
            } finally {
                System.out.println("TRACE exit");
            }
        }
    }

    @Aspect
    @Order(20)
    public static final class AuditAspect {
        @Around("execution(* example..*.greet(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("AUDIT enter");
            try {
                return pjp.proceed();
            } finally {
                System.out.println("AUDIT exit");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {

        @Bean
        GreetingService greetingService() {
            return new GreetingServiceImpl();
        }

        @Bean
        PlainTool plainTool() {
            return new PlainTool();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            Object creator = context.getBean(
                    AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
            GreetingService greeting = context.getBean(GreetingService.class);
            PlainTool plain = context.getBean(PlainTool.class);

            System.out.println("creator="
                    + creator.getClass().getSimpleName());
            System.out.println("greeting.isProxy="
                    + AopUtils.isAopProxy(greeting));
            System.out.println("greeting.isAdvised="
                    + (greeting instanceof Advised));
            System.out.println("plain.isProxy="
                    + AopUtils.isAopProxy(plain));

            if (!AopUtils.isAopProxy(greeting)
                    || !(greeting instanceof Advised advised)
                    || advised.getAdvisors().length == 0) {
                throw new AssertionError("Missing Spring AOP advisors");
            }
            if (AopUtils.isAopProxy(plain)) {
                throw new AssertionError("Plain bean should not be proxied");
            }

            String result = greeting.greet("Lan");
            System.out.println("result=" + result);

            String pong = plain.ping();
            System.out.println("ping=" + pong);
        }
    }
}
```

Chạy tại thư mục có `build.gradle`:

```bash
gradle run
```

Nếu đã có Gradle wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `.\gradlew.bat run` trên Windows PowerShell.

**Stdout do `main` sinh ra (không tính log Gradle):**

```text
creator=AnnotationAwareAspectJAutoProxyCreator
greeting.isProxy=true
greeting.isAdvised=true
plain.isProxy=false
TRACE enter
AUDIT enter
TARGET greet Lan
AUDIT exit
TRACE exit
result=Hello Lan
TARGET ping
ping=pong
```

### 9.3. Giải thích kết quả theo từng thành phần

1. **Creator tồn tại:** `@EnableAspectJAutoProxy` khiến registrar/`AopConfigUtils` đăng ký auto-proxy creator. Lấy bean theo `AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME` cho thấy runtime class `AnnotationAwareAspectJAutoProxyCreator` trong cấu hình đơn giản này.
2. **Phát hiện aspect bean:** `traceAspect()` và `auditAspect()` là các bean đã được Spring đăng ký. Builder/Advisor factory chuyển hai `@Around` thành advisor ứng viên.
3. **Chọn service:** Pointcut `execution(* example..*.greet(..))` khớp `greet` của service trong package `example`. Creator tạo AOP proxy cho `greetingService`, vì vậy `AopUtils.isAopProxy(greeting)` là `true`. Proxy thường có thể expose `Advised`; ví dụ này không bật opaque nên kiểm tra thành công.
4. **Không proxy bean không khớp:** `PlainTool` chỉ có `ping`, không có `greet`; advisor không áp dụng và `AopUtils.isAopProxy(plain)` là `false`.
5. **Order là precedence:** `TraceAspect` với order 10 có precedence cao hơn `AuditAspect` với order 20, nên TRACE bao ngoài AUDIT. Đường vào `TRACE → AUDIT → TARGET`; đường ra `AUDIT → TRACE`.
6. **Thực sự chạy qua proxy:** `greeting.greet("Lan")` lấy qua context nên lời gọi đi qua chain. `plain.ping()` đi thẳng vào bean bình thường.
7. **Tại sao không in số advisor tuyệt đối?** Spring có thể thêm advisor hạ tầng khi chuẩn bị chain. Bài kiểm chứng điều cốt lõi: proxy có advisor và advice chạy đúng hành vi, không phụ thuộc số advisor nội bộ phải luôn bằng 2.

Đầu ra này minh họa cấu hình **đã cho** trong Spring Framework 6.1.x; nếu ứng dụng thêm transaction/security/async infrastructure, thứ tự log thực tế còn phụ thuộc các advisor khác.

## 10. Tự quan sát auto-proxy creator và advisors trong context

Với một proxy cho phép xem cấu hình qua `Advised`, ta có thể kiểm tra các advisor đang cấu hình:

```java
Object service = context.getBean("greetingService");
System.out.println(AopUtils.isAopProxy(service));

if (service instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

**Đoạn này là mã minh họa**, giả định đang ở method có `context` và import `AopUtils`, `Advised` như ví dụ đầy đủ. Không có output cố định vì loại/số advisor tùy nội dung aspect, hạ tầng khác và phiên bản Spring.

Khi cần phân tích sâu, phân biệt ba mức:

- **Bean definition hạ tầng:** có một auto-proxy creator được container quản lý.
- **Danh sách advisor trên proxy:** `Advised.getAdvisors()` cho biết cấu hình advisor của proxy đó.
- **Interceptor chain cho lời gọi cụ thể:** chỉ các advisor match method/đối số mới tham gia; cần quan sát qua trace, test hoặc debug invocation.

`getAdvisors()` không cho biết một advisor đã thực sự chạy ở lần gọi nào. Pointcut động có thể match invocation đầu nhưng bỏ qua invocation tiếp theo của cùng một method.

## 11. Các lỗi riêng khi bật hạ tầng @AspectJ

Cần phân biệt **creator đã được đăng ký**, **advisor đã được tạo**, **proxy đã được dựng** và **advice thực sự được gọi**. Bốn điều kiện không tự suy ra lẫn nhau.

### 11.1. Hạ tầng và aspect bean

- Chỉ khai báo `@Aspect` **không** tạo một Spring bean; cần `@Bean` hoặc `@Component` cùng component scan phù hợp.
- Spring Framework thuần cần bật `@EnableAspectJAutoProxy` hoặc cấu hình tương ứng; Spring Boot có thể đã bật AOP qua starter/auto-configuration.
- Thư viện `aspectjweaver` cần có trên classpath để parse biểu thức @AspectJ; điều này **không** bật AspectJ bytecode weaving.
- Hạ tầng và aspect phải khả dụng đối với **context nơi bean nghiệp vụ được tạo**. Parent/child contexts không dùng chung một auto-proxy creator theo cách tự động.

### 11.2. Pointcut, proxy và đường gọi

- Pointcut có thể không khớp package, target class, method signature hoặc annotation. Một creator tồn tại không có nghĩa tất cả bean đều cần proxy.
- JDK proxy chỉ expose các interface được proxy; class-based proxy chịu giới hạn `final`, `private` và khả năng override.
- Tạo target bằng `new` hoặc gọi `this.otherMethod()` bên trong target thường bỏ qua Spring proxy, nên không kích hoạt advice tương ứng.
- Nhiều advisors từ aspect, transaction hay security có thể cùng áp dụng. `@Order` ảnh hưởng precedence của advice; nó không sửa được bean bị tạo quá sớm khi chưa đủ `BeanPostProcessor`.

Khi gặp lỗi, giữ lại ba bằng chứng ở runtime: proxy type bằng `AopUtils`, advisor list khi proxy expose `Advised`, và **một lời gọi method thật** có kiểm chứng advice output/return/exception. Nếu nghi ngờ nhiều creator riêng biệt hoặc thiếu BPP, kiểm tra lần lượt hạ tầng và lifecycle; không thêm creator mới như một bản vá mặc định.

## 12. Cách debug theo từng tầng

Khi có biểu hiện “@Aspect không chạy”, nên kiểm tra theo luồng:

1. **Configuration:** context có enable @AspectJ chưa? Thử `context.containsBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME)` trong môi trường phù hợp.
2. **Aspect registration:** aspect class thực sự là Spring bean không? Kiểm tra `context.getBean(TraceAspect.class)` hoặc tên bean.
3. **Advisor discovery:** có Spring Advisor từ advice tương ứng không? Nếu bean đã được proxy và expose `Advised`, đọc `getAdvisors()`.
4. **Pointcut eligibility:** kiểu target, method và các điều kiện runtime có khớp không? Một advisor có thể tồn tại mà không match lời gọi đang thử.
5. **Proxy creation:** `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy`/`isCglibProxy`, và interface/method có thể proxy.
6. **Invocation:** call có đi qua bean proxy không? Kiểm tra self-invocation, instance được tạo thủ công và exception/short-circuit trong chain.
7. **Ordering:** in log trước và sau `proceed()`, xác minh precedence giữa các aspect và advisor khác.

Một log `creator=AnnotationAwareAspectJAutoProxyCreator` chứng minh auto-proxy infrastructure đã được khởi tạo; nó **không tự chứng minh** rằng mọi advice đã match hoặc target đã chạy qua proxy. Cần xác minh thêm hai tầng advisor và invocation.

## 13. Tình huống thiết kế và giới hạn

**Khi nào dùng @EnableAspectJAutoProxy?** Khi tự cấu hình Spring Framework context và muốn Spring tự tạo proxy từ `@Aspect` bean. Trong Spring Boot, ưu tiên quan sát auto-configuration AOP hiện có để tránh bật/tắt một cách mù quáng.

**Khi nào dùng ProxyFactory hoặc AspectJProxyFactory?** Khi chủ động tạo một proxy cho một target cụ thể bằng API lập trình. `AspectJProxyFactory` nhận aspect class/instance kiểu @AspectJ; `ProxyFactory` thường nhận advisor/advice trực tiếp. Đây không phải quá trình auto-proxy của Spring container cho toàn bộ bean.

**Khi nào cần hiểu class hạ tầng?** Khi đọc stack trace, xác minh vì sao bean được/không được proxy, kiểm soát order, hoặc thiết kế extension hạ tầng. Mã ứng dụng thông thường chỉ cấu hình aspect, pointcut và order.

**Có cần bật exposeProxy chỉ để gọi method nội bộ?** Thường không nên. Giữ lời gọi đi qua các bean có trách nhiệm rõ ràng và tránh phụ thuộc `AopContext.currentProxy()` nếu không có yêu cầu đặc biệt.

**Auto-proxy có làm tất cả advice chạy trên mọi lần gọi?** Không. Advisor được chọn theo class/method, có thể kiểm tra runtime theo args; interceptor ngoài cùng có thể dừng chain trước target. Trường hợp mặc định minh họa bằng `@Around` gọi `proceed()`, nhưng ứng dụng thật cần xét toàn bộ advisor liên quan.

## 14. Câu hỏi tự kiểm tra

1. `@EnableAspectJAutoProxy` đăng ký gì? **Hạ tầng auto-proxy Spring AOP**, chủ yếu qua registrar và auto-proxy creator.
2. `@Aspect` đặt trên class không phải Spring bean có được builder tự tìm thấy không? **Không**, cần đưa aspect vào container.
3. Ai đưa advice method @AspectJ thành Spring Advisor? **`BeanFactoryAspectJAdvisorsBuilder` phối hợp `ReflectiveAspectJAdvisorFactory`**.
4. Ai quyết định advisor có áp dụng cho một bean để tạo proxy? **Tầng `AbstractAdvisorAutoProxyCreator`/`AnnotationAwareAspectJAutoProxyCreator`**, sử dụng pointcut matching.
5. `AnnotationAwareAspectJAutoProxyCreator` chỉ hiểu @AspectJ, hay cả advisor bean Spring? **Cả hai**.
6. Tại sao `AopUtils.isAopProxy(plainTool)` là false ở ví dụ? **Không advisor nào khớp method của bean đó**.
7. Hai aspect có order 10 và 20 cùng chạy @Around: ai vào trước và ra sau? **Aspect order 10**.
8. Dùng aspectjweaver JAR trong Spring AOP có đồng nghĩa đang bật LTW? **Không**; thư viện cung cấp hỗ trợ phân tích biểu thức, còn interception vẫn dựa trên proxy.
9. Khi auto-proxy creator tồn tại nhưng advice không chạy, nên kiểm tra tiếp gì? **Aspect bean, advisor/pointcut match, object có phải proxy và đường gọi qua proxy**.
10. Muốn biết proxy đã được cấu hình advisor nào, API phù hợp là gì? **`Advised.getAdvisors()` khi proxy công khai `Advised`**, sau đó dùng trace để xác nhận invocation.

## 15. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework Reference — @AspectJ Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework Javadoc 6.1 — AnnotationAwareAspectJAutoProxyCreator](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html)
- [Spring Framework Javadoc 6.1 — BeanFactoryAspectJAdvisorsBuilder](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/aspectj/annotation/BeanFactoryAspectJAdvisorsBuilder.html)
- [Spring Framework Javadoc 6.1 — EnableAspectJAutoProxy](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-006">Thứ tự xử lý với các BeanPostProcessor khác</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự xử lý với các BeanPostProcessor khác trong Spring AOP

## 1. Bài toán trong Spring Aspect

Spring tạo AOP proxy thông qua hạ tầng `BeanPostProcessor` (BPP). Nếu ứng dụng có BPP tùy chỉnh để kiểm tra, khởi tạo hoặc bọc bean, **thứ tự đăng ký các BPP** ảnh hưởng đến việc thành phần đó nhìn thấy bean gốc hay đối tượng đã được AOP proxy. Đây là kiến thức chuyên sâu về cách Spring phối hợp auto-proxy creator với vòng đời bean, không phải bài giới thiệu AOP nói chung.

Sau bài học, bạn cần phân biệt được:
- **Thứ tự BPP lúc tạo bean**: callback nào chạy trước/sau việc Spring tạo proxy.
- **Thứ tự advisor/advice lúc gọi phương thức**: advice nào chạy trước khi method được gọi qua proxy.
- **Thứ tự các callback vòng đời**: injection, `@PostConstruct`, init method, after-initialization không diễn ra ở cùng một thời điểm.

Trọng tâm minh họa là Spring Framework 6.x / Spring Boot 3.x, với auto-proxy dựa trên Spring AOP. Chi tiết triển khai nội bộ có thể khác theo phiên bản.

## 2. Auto-proxy creator cũng là BeanPostProcessor

Spring cung cấp các auto-proxy creator dựa trên `AbstractAutoProxyCreator`. Một số lớp quan trọng:
- `AnnotationAwareAspectJAutoProxyCreator`: tìm advisor và các aspect khai báo `@Aspect` khi bật `@EnableAspectJAutoProxy`.
- `AspectJAwareAdvisorAutoProxyCreator`: tạo proxy từ advisor, phối hợp thứ tự tương thích các quy tắc AOP.
- `InfrastructureAdvisorAutoProxyCreator`: chỉ xét các advisor hạ tầng tương ứng.

Trong đường đi **thông thường** khi Spring tạo một singleton bean:

~~~text
Tạo instance của target bean
      ↓
Populate properties, inject dependency
      ↓
Các BPP: postProcessBeforeInitialization(...)
      ↓
@PostConstruct / InitializingBean.afterPropertiesSet() / init-method
      ↓
Các BPP: postProcessAfterInitialization(...) theo thứ tự đăng ký
      ├── BPP A: quan sát/chỉnh bean chưa được AOP bọc
      ├── AutoProxyCreator: xét advisor, có thể trả về AOP proxy
      └── BPP B: nhận proxy nếu nó đã được tạo
      ↓
Bean cuối cùng được công bố cho các consumer
      ↓
Gọi method qua proxy → advisor/interceptor → target
~~~

Điểm chính: `AbstractAutoProxyCreator.postProcessAfterInitialization(...)` thường gọi `wrapIfNecessary(...)` để tạo proxy **khi có advisor áp dụng và bean đủ điều kiện**. Nếu không khớp advisor, hoặc bean thuộc nhóm cần bỏ qua, kết quả có thể chính là đối tượng đầu vào.

Không nên tuyệt đối hóa sơ đồ trên. `AbstractAutoProxyCreator` còn có:
- `postProcessBeforeInstantiation(...)`: trong trường hợp `TargetSource` đặc biệt, Spring có thể tạo proxy trước khi tạo target theo cách thông thường và rút ngắn quy trình tạo bean.
- `getEarlyBeanReference(...)`: có thể xuất proxy sớm cho một số tình huống circular reference; đây không phải giải pháp khuyến khích thiết kế phụ thuộc vòng.
- `postProcessAfterInitialization(...)`: phối hợp trạng thái early reference để tránh bọc lại proxy một cách không phù hợp.

Vì thế, **“AOP proxy luôn được tạo cuối cùng”** không phải bảo đảm tuyệt đối của mọi đường đi khởi tạo.

## 3. Quy tắc sắp xếp BeanPostProcessor

Trong `ApplicationContext`, các BPP được Spring **tự phát hiện từ bean definition** thường được đăng ký theo nhóm:

| Nhóm | Thứ tự tương đối | Đặc điểm |
| --- | --- | --- |
| `PriorityOrdered` | Trước các BPP nhóm `Ordered` | Dành cho xử lý có mức ưu tiên đặc biệt |
| `Ordered` | Sau `PriorityOrdered`, trước BPP không có order | So sánh giá trị `getOrder()` |
| Không triển khai hai interface trên | Sau nhóm có order | Phụ thuộc trình tự đăng ký thực tế |

Trong cùng nhóm có order, **giá trị nhỏ hơn chạy trước**. Các hằng `Ordered.HIGHEST_PRECEDENCE` và `Ordered.LOWEST_PRECEDENCE` là mốc cao/thấp thường dùng. Không dựa vào thứ tự ngẫu nhiên nếu hai processor có cùng mức ưu tiên.

Các chi tiết đặc thù của Spring cần nhớ:

1. `AbstractAutoProxyCreator` triển khai `Ordered` và mặc định có `LOWEST_PRECEDENCE`. Một BPP `PriorityOrdered` thông thường sẽ được đăng ký trước nó; một BPP không triển khai `Ordered` thường được đăng ký sau.
2. Trong quá trình đăng ký, Spring đưa các BPP nội bộ triển khai `MergedBeanDefinitionPostProcessor` về cuối chuỗi theo logic riêng. Đây là một ngoại lệ đáng lưu ý khi phân tích thứ tự thực tế.
3. `@Order` trên **lớp BeanPostProcessor** không được dùng để sắp thứ tự như hai interface nêu trên. Nếu cần điều khiển thứ tự BPP, triển khai `PriorityOrdered` hoặc `Ordered`. Ngược lại, `@Order` trên **lớp `@Aspect`** có ý nghĩa điều chỉnh thứ tự precedence của advice.
4. `ConfigurableBeanFactory.addBeanPostProcessor(...)` là đường **đăng ký thủ công**: BPP chạy theo thứ tự được thêm vào factory, bỏ qua `getOrder()` đối với nhóm đăng ký này. BPP thêm thủ công được xử lý trước các BPP tự phát hiện.
5. Khi khai báo BPP bằng `@Bean`, nên khai báo **kiểu trả về cụ thể**, đánh dấu `static` nếu có thể và tránh yêu cầu khởi tạo sớm bean nghiệp vụ. Điều này giúp Spring nhận diện BPP và thông tin về `PriorityOrdered`/`Ordered` từ giai đoạn đăng ký.

Ở cả hai callback `postProcessBeforeInitialization` và `postProcessAfterInitialization`, Spring chuyển kết quả từ BPP này sang BPP kế tiếp **theo chuỗi áp dụng đã đăng ký**. Không có quy tắc “after-initialization phải chạy ngược thứ tự before-initialization”.

## 4. BPP trước và sau auto-proxy creator nhìn thấy gì?

Giả sử `paymentService` có một method khớp pointcut của Spring Aspect:

| Vị trí callback | Đối tượng thường nhận được | Điều cần chú ý |
| --- | --- | --- |
| `postProcessBeforeInitialization` | Bean trước bước AOP bọc thông thường | Có thể đã được wrapper khác thay thế |
| BPP chạy trước APC ở `postProcessAfterInitialization` | Target hoặc wrapper từ BPP trước nó | `AopUtils.isAopProxy(bean)` có thể là `false` |
| `AbstractAutoProxyCreator.postProcessAfterInitialization` | Kết quả các BPP trước | Có thể tạo proxy hoặc trả lại bean |
| BPP chạy sau APC | Đối tượng sau quyết định auto-proxy | Có thể là AOP proxy nếu pointcut/advisor áp dụng |

`BeanPostProcessor` không chỉ để quan sát: phương thức có thể trả về **một object khác**. Spring dùng object được trả về cho processor kế tiếp và cho bean cuối cùng. Vì vậy:
- BPP phía sau AOP trả về **target gốc** có nguy cơ khiến consumer mất proxy, từ đó advice không chạy.
- BPP phía trước AOP tự bọc bean bằng kiểu proxy khác có thể ảnh hưởng quá trình Spring nhận diện class và advisor.
- Không ép kiểu về lớp triển khai khi đối tượng có thể là JDK dynamic proxy; sử dụng interface nghiệp vụ hoặc kiểm tra bằng `AopUtils`.
- Một BPP chỉ ghi log nên trả về **chính `bean` đầu vào** và tránh gọi method nghiệp vụ trong callback.

Các công cụ quan sát hữu ích: `AopUtils.isAopProxy(bean)`, `AopUtils.isJdkDynamicProxy(bean)`, `AopUtils.isCglibProxy(bean)` và `AopUtils.getTargetClass(bean)`. `bean.getClass()` phản ánh class runtime của proxy nếu có, có thể khác lớp target.

## 5. Ví dụ Java 17+: quan sát đối tượng trước và sau Spring AOP

Ví dụ có hai observer:
- `EarlyObserver` triển khai `PriorityOrdered`, chạy trước auto-proxy creator.
- `LateObserver` không triển khai `Ordered`, quan sát kết quả sau khi auto-proxy creator đã xử lý.
- `TraceAspect` dùng `@Before` để xác nhận lời gọi qua AOP proxy.

Tạo **một file** `src/main/java/dev/example/aop/AopOrderDemo.java`:

~~~java
package dev.example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopOrderDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(AopOrderDemo.class)) {
            PaymentService service = ctx.getBean(PaymentService.class);
            System.out.println("client sees proxy=" + AopUtils.isAopProxy(service));
            service.pay();
        }
    }

    @Bean
    PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }

    // static và kiểu trả về cụ thể giúp Spring nhận diện BPP sớm.
    @Bean
    static EarlyObserver earlyObserver() {
        return new EarlyObserver();
    }

    @Bean
    static LateObserver lateObserver() {
        return new LateObserver();
    }
}

class PaymentService {
    public void pay() {
        System.out.println("target: pay");
    }
}

@Aspect
class TraceAspect {
    @Before("execution(* dev.example.aop.PaymentService.pay(..))")
    public void beforePay() {
        System.out.println("advice: before pay");
    }
}

class EarlyObserver implements BeanPostProcessor, PriorityOrdered {
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("paymentService".equals(beanName)) {
            System.out.println("early sees proxy=" + AopUtils.isAopProxy(bean));
        }
        return bean;
    }
}

class LateObserver implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("paymentService".equals(beanName)) {
            System.out.println("late sees proxy=" + AopUtils.isAopProxy(bean));
        }
        return bean;
    }
}
~~~

Trong dự án Spring Boot đã có `spring-boot-starter-aop`, dùng các dependency Spring hiện hành của module. Để chạy ví dụ như một ứng dụng Java độc lập, có thể dùng file `build.gradle` sau:

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'dev.example.aop.AopOrderDemo'
}
~~~

`spring-context` lấy `spring-aop` theo dependency bắc cầu. Lệnh chạy: `./gradlew run` (macOS/Linux) hoặc `gradlew.bat run` (Windows). Dùng Gradle wrapper phù hợp với dự án; nếu dự án đã quản lý phiên bản Spring bằng BOM thì dùng BOM hiện hữu.

**Những dòng từ `System.out` trong luồng bình thường** (bỏ qua log build và log thư viện):

~~~text
early sees proxy=false
late sees proxy=true
client sees proxy=true
advice: before pay
target: pay
~~~

Giải thích thứ tự:
1. `EarlyObserver` có mức `PriorityOrdered` cao, quan sát bean chưa được APC bọc.
2. `AnnotationAwareAspectJAutoProxyCreator` phát hiện `@Aspect` và pointcut khớp `PaymentService.pay()`, rồi tạo CGLIB proxy vì `proxyTargetClass=true`.
3. `LateObserver` nhận proxy đó; đối tượng consumer nhận từ `getBean` cũng là proxy.
4. Lời gọi `service.pay()` qua proxy kích hoạt `TraceAspect.beforePay()` trước target.

Hãy thử đổi `LateObserver` thành `implements BeanPostProcessor, PriorityOrdered` và trả về mức ưu tiên cao, rồi quan sát lại thứ tự. Đây là một **thí nghiệm về thứ tự BPP**, không phải gợi ý áp dụng đại trà vì thay đổi thứ tự hạ tầng có thể có hệ quả.

## 6. Tương tác với những BeanPostProcessor Spring khác

### 6.1. Injection và callback khởi tạo

`AutowiredAnnotationBeanPostProcessor` tham gia xử lý injection và metadata bean; `CommonAnnotationBeanPostProcessor` có thể xử lý các annotation vòng đời như `@PostConstruct` trong cấu hình có hỗ trợ. Chúng hoạt động ở **các mốc khác nhau** của lifecycle; việc so sánh duy nhất `getOrder()` không đủ để suy luận toàn bộ thứ tự các hành động.

Ở đường đi phổ biến, `@PostConstruct` được gọi trên target trước khi auto-proxy creator bọc bean trong `postProcessAfterInitialization`. Bởi vậy, gọi method nghiệp vụ từ `@PostConstruct` thường **không đi qua Spring AOP proxy**. Tương tự, lời gọi `this.otherMethod()` trong cùng bean bỏ qua proxy.

Nếu một hành vi cần advice, hãy kích hoạt qua reference bean đã được Spring cung cấp từ context ở thời điểm thích hợp, không dựa vào lời gọi nội bộ lúc initialization.

### 6.2. Transaction, caching và nhiều advisor

Các tính năng như `@Transactional` hoặc `@Cacheable` có thể đăng ký advisor/interceptor để tạo behavior tại thời điểm **method invocation**. Chúng có thể được auto-proxy creator tích hợp vào cùng chuỗi advisor phù hợp. **Thứ tự advisor** thuộc cơ chế khác với **thứ tự BeanPostProcessor**.

Nếu muốn một `@Aspect` thực thi trước aspect khác, hãy xét `@Order` hoặc `Ordered` của **aspect**; nếu muốn một BPP tùy chỉnh nhận bean trước khi AOP bọc, hãy xét **thứ tự BPP**. Thay đổi cái này không bảo đảm thay đổi cái kia.

### 6.3. Nhiều cấu hình cùng bật proxy

Spring phối hợp các đăng ký auto-proxy thông qua cơ chế nâng cấp creator, chẳng hạn `AopConfigUtils`. Trong cấu hình AOP chuẩn Spring 6.x, `@EnableAspectJAutoProxy`, quản lý transaction ở proxy mode và một số cơ chế tương ứng không đồng nghĩa mỗi annotation nhất định tạo một proxy lồng bên ngoài proxy khác; hạ tầng chuẩn có thể hợp nhất và áp dụng chính sách proxy hiệu lực.

Tuy vậy, BPP tự viết hoặc framework bổ sung có thể tạo wrapper riêng. Không suy luận số proxy từ số annotation; kiểm tra runtime proxy và danh sách advisor.

## 7. Vì sao bean không được proxy: giai đoạn đăng ký BPP

`BeanPostProcessor` phải được khởi tạo **sớm** để có mặt xử lý các bean tạo sau. Các BPP cùng những bean được tạo sớm do phụ thuộc trực tiếp của BPP **không được bảo đảm nhận đủ chuỗi BPP**, trong đó có AOP auto-proxy. Spring có thể ghi cảnh báo tương tự:

~~~text
Bean 'someBean' of type [...] is not eligible for getting processed
by all BeanPostProcessors (for example: not eligible for auto-proxying)
~~~

Cần phân biệt tình trạng này với “pointcut không khớp”. Nếu bean chưa qua đủ BPP thì dù khai báo pointcut hợp lệ, proxy cũng có thể chưa được tạo. Cách giảm rủi ro:
- Khai báo `@Bean static` cho BPP, dùng kiểu trả về phù hợp.
- Tránh inject service nghiệp vụ vào constructor của BPP nếu không cần thiết.
- Không gọi `getBean(...)` hoặc khởi tạo nhiều service quá sớm trong logic đăng ký BPP.
- Khi thấy cảnh báo trên, kiểm tra dependency trực tiếp và gián tiếp của BPP.

Đây là lý do việc khởi tạo sớm có thể làm mất AOP ở một số bean, khác với lỗi thứ tự advice.

## 8. Debug theo dấu proxy thực tế

| Triệu chứng | Giả thuyết cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| BPP A báo `proxy=false`, BPP B báo `true` | APC nằm giữa hai callback | Kiểm tra thứ tự đăng ký, không xem đó là lỗi nếu proxy cuối đúng |
| Không thấy advice chạy mặc dù pointcut hợp lệ | Bean bị tạo quá sớm, gọi method không qua proxy, hoặc advisor không match target thực | Kiểm tra cảnh báo startup, đối tượng trả từ context và pointcut |
| `ClassCastException` khi BPP ép sang lớp target | BPP nhận JDK proxy | Làm việc với interface hoặc xét loại proxy phù hợp |
| Proxy biến mất sau callback tùy chỉnh | BPP sau APC trả về target/đối tượng mới | Trả lại chính `bean` nếu chỉ quan sát; rà soát wrapper được tạo |
| Đổi `@Order` của aspect nhưng callback BPP không đổi | Hai cơ chế thứ tự khác nhau | Dùng `Ordered`/`PriorityOrdered` cho BPP |
| Nhiều cơ chế AOP cùng bật và behavior khó đoán | Nhiều advisor trên proxy hoặc wrapper bổ sung | Quan sát runtime advisor/proxy thực tế |

Quy trình debug hữu ích:

1. Lấy đối tượng thật sự mà consumer sử dụng từ `ApplicationContext`. Log `AopUtils.isAopProxy(bean)` và `AopUtils.getTargetClass(bean)`.
2. Nếu proxy triển khai `org.springframework.aop.framework.Advised`, đọc `getAdvisors()` để xác nhận advisor được lắp vào chuỗi. Không ép mọi proxy thành `Advised` một cách mù quáng.
3. Đặt log có điều kiện theo `beanName` trong BPP tùy chỉnh: tên class, `isAopProxy` và thời điểm callback. Đừng tự gọi method nghiệp vụ trong log.
4. Đối chiếu các BPP: được auto-detect hay đăng ký thủ công, có `PriorityOrdered`/`Ordered`, và có tạo object mới không.
5. Nếu nghi ngờ Spring internals, đặt breakpoint ở `PostProcessorRegistrationDelegate.registerBeanPostProcessors(...)`, `AbstractAutoProxyCreator.postProcessAfterInitialization(...)`, `wrapIfNecessary(...)` và `getEarlyBeanReference(...)`. Đây là điểm phục vụ điều tra, không phải API để code nghiệp vụ phụ thuộc.

## 9. Câu hỏi kiểm tra

**1. Vì sao observer trước auto-proxy creator thấy target nhưng consumer nhận proxy?**

Vì các callback `postProcessAfterInitialization` nối tiếp nhau. APC có thể thay giá trị đang truyền trong chuỗi bằng một proxy sau khi observer đó đã chạy.

**2. Gắn `@Order(-100)` vào một lớp `@Aspect` có ép APC chạy trước tất cả BPP không?**

Không. `@Order` của aspect quyết định precedence các advisor/advice, không sắp vị trí của một `BeanPostProcessor` trong chuỗi đăng ký.

**3. Một BPP được thêm bằng `addBeanPostProcessor(...)` có chạy theo số `getOrder()` không?**

Không. Với BPP đăng ký thủ công, thứ tự thêm vào factory quyết định thứ tự áp dụng.

**4. Có phải mọi bean đều được proxy khi qua APC không?**

Không. Spring xét advisor, class và điều kiện áp dụng; một số bean không cần hoặc không được proxy. Các nhánh early reference và before-instantiation còn khiến thời điểm tạo proxy khác luồng mặc định.

**5. Nếu một BPP chỉ muốn đo trạng thái AOP thì nên trả về gì?**

Trả về đúng đối tượng `bean` nhận vào, để không làm mất hoặc thay wrapper Spring đã tạo.

## 10. Tài liệu Spring chính thức

- [Spring Container Extension Points – BeanPostProcessor](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): thứ tự, đăng ký thủ công, vấn đề khởi tạo sớm.
- [Spring AOP – Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy và cơ chế phối hợp cấu hình.
- [Spring AOP – Declaring Advice / Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): precedence của advice.
- [AbstractAutoProxyCreator Javadoc, Spring 6.1](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html): `postProcessAfterInitialization`, `postProcessBeforeInstantiation`, `getEarlyBeanReference`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-007">Nguy cơ tự tạo nhiều auto-proxy creator không phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ tự tạo nhiều auto-proxy creator không phối hợp trong Spring AOP

## 1. Bối cảnh và mục tiêu

Trong Spring AOP, nhiều tính năng có thể cần tạo proxy quanh cùng một Spring bean: aspect tự viết, advisor nghiệp vụ, hoặc hạ tầng dựa trên AOP như transaction. **Auto-proxy creator** là thành phần Spring có thể tự phát hiện bean phù hợp và trả về AOP proxy khi bean được container khởi tạo.

Bài học này tập trung vào **Spring AOP và cách Spring container đăng ký, phối hợp các auto-proxy creator**. Vấn đề cần tránh là **tự đăng ký nhiều creator hoạt động độc lập, cùng có khả năng xử lý một bean mà không có chiến lược phối hợp**. Điều này khác với việc khai báo nhiều aspect hoặc nhiều advisor: một creator duy nhất hoàn toàn có thể gắn nhiều advisor vào cùng một proxy.

Sau bài học, bạn có thể:
- Nhận diện auto-proxy creator trong chu kỳ xử lý bean của Spring.
- Phân biệt cơ chế hợp nhất creator chuẩn của Spring với creator tùy biến được đăng ký riêng.
- Giải thích khi nào nhiều creator gây proxy lồng nhau, advice chạy lặp hoặc hành vi proxy khó dự đoán.
- Viết cấu hình tập trung vào advisor/aspect và kiểm tra thực trạng AOP tại runtime.

## 2. Auto-proxy creator hoạt động thế nào trong Spring?

Trong Spring Framework, các creator như **AbstractAutoProxyCreator**, **DefaultAdvisorAutoProxyCreator**, **BeanNameAutoProxyCreator** và **AnnotationAwareAspectJAutoProxyCreator** tham gia cơ chế **BeanPostProcessor** của Spring container. Chúng xem bean mới được tạo có phù hợp để gắn advice hay không rồi có thể trả về một proxy thay cho đối tượng ban đầu.

~~~text
Bean definition
      |
Spring tạo bean nghiệp vụ
      |
BeanPostProcessor / auto-proxy creator
      |-- tra cứu advisor, pointcut, aspect liên quan
      |-- nếu phù hợp: tạo Spring AOP proxy
      '-- nếu không phù hợp: giữ nguyên bean
      |
ApplicationContext cung cấp bean/proxy cho client
~~~

Các loại creator có cách chọn bean khác nhau:

| Thành phần Spring | Cách chọn bean/advice | Lưu ý |
| --- | --- | --- |
| **BeanNameAutoProxyCreator** | So tên bean với danh sách tên/wildcard; áp dụng các interceptor/advisor được cấu hình | Dễ dùng cho nhóm bean theo tên, nhưng cần cân nhắc khi bên cạnh đã có auto-proxy hạ tầng khác |
| **DefaultAdvisorAutoProxyCreator** | Tìm các Spring **Advisor** phù hợp với bean | Không tự hiểu mọi phương thức khai báo aspect như AnnotationAwareAspectJAutoProxyCreator |
| **AnnotationAwareAspectJAutoProxyCreator** | Nhận diện Spring **Advisor** và các bean chứa **@Aspect** tương thích | Thường được kích hoạt bởi **@EnableAspectJAutoProxy** |
| **InfrastructureAdvisorAutoProxyCreator** | Xem các advisor hạ tầng Spring phù hợp | Có thể được nâng cấp thành creator khác khi cấu hình cần năng lực rộng hơn |

Một số creator là các **BeanPostProcessor** thông thường trong cùng container. Nhiều BeanPostProcessor tồn tại là chuyện bình thường; vấn đề là **nhiều bộ xử lý độc lập cùng tạo AOP proxy cho một bean mà không có cấu hình thống nhất**.

## 3. Spring phối hợp creator chuẩn bằng cơ chế nào?

Với cách khai báo AOP chuẩn của Spring Framework 6.2, nhiều cấu hình AOP không có nghĩa Spring tạo một creator mới cho mỗi cấu hình. Spring dùng cơ chế đăng ký hoặc **nâng cấp (escalation)** creator hạ tầng dưới một bean name nội bộ:

~~~text
org.springframework.aop.config.internalAutoProxyCreator
~~~

Trong nhánh hạ tầng Spring AOP này, các năng lực có thể được nâng cấp theo nhu cầu:

~~~text
InfrastructureAdvisorAutoProxyCreator
             |
             v
AspectJAwareAdvisorAutoProxyCreator
             |
             v
AnnotationAwareAspectJAutoProxyCreator
~~~

Điểm cần hiểu:
1. Các annotation cấu hình như **@EnableAspectJAutoProxy** và **@EnableTransactionManagement** dùng các bộ đăng ký hạ tầng phù hợp để hợp tác thông qua creator nội bộ.
2. Các phần tử XML Spring AOP như **<aop:config>**, **<aop:aspectj-autoproxy>** và **<tx:annotation-driven>** cũng được hợp nhất thay vì tạo một tầng proxy tự động riêng cho từng phần tử.
3. Trong cơ chế hợp nhất này, tùy chọn proxy mạnh hơn như **proxyTargetClass=true** có thể tác động đến cách tạo proxy dùng chung. Không nên mặc định rằng mỗi nhóm cấu hình AOP được quyền chọn loại proxy độc lập.
4. Danh sách trên nói về **hạ tầng AOP phối hợp theo quy tắc Spring**, không phải mọi BeanPostProcessor hay mọi kiểu proxy trong toàn ứng dụng.

**Giới hạn rất quan trọng:** nếu lập trình viên đăng ký **thêm** một **BeanNameAutoProxyCreator**, **DefaultAdvisorAutoProxyCreator**, một subclass **AbstractAutoProxyCreator** dưới tên riêng, hoặc tự thêm một BeanPostProcessor tạo proxy, việc đó **không tự động tham gia** cùng cơ chế nâng cấp creator nội bộ. Vì thế, Spring có thể tồn tại một creator chuẩn và một hoặc nhiều creator độc lập trong **cùng ApplicationContext**.

Không nên diễn giải quy tắc hợp nhất thành tuyên bố "Spring tuyệt đối cấm nhiều auto-proxy creator". Trên thực tế, Spring cho phép cấu hình thủ công các loại creator; người cấu hình chịu trách nhiệm xem chúng có đụng vào cùng bean hay không.

## 4. Tình huống rủi ro: hai creator cùng chọn một service

Giả sử module có:
- **Creator A**, do **@EnableAspectJAutoProxy** khởi tạo, chọn một service vì advisor gắn với phương thức **execute()**.
- **Creator B**, do nhóm phát triển tự đăng ký qua **@Bean BeanNameAutoProxyCreator**, cũng chọn service này theo tên bean và áp dụng một interceptor khác.

Khi cả hai thực sự tạo proxy quanh cùng bean, đường gọi có thể trở thành:

~~~text
Client
  |
  v
Proxy B (interceptor B)
  |
  v
Proxy A (advisor A)
  |
  v
Target service.execute()
~~~

Đây là **proxy lồng nhau (nested/double proxy)**. Ví dụ sơ đồ chỉ mô tả một kết quả *có thể xảy ra*; không được kết luận cứ đăng ký hai creator là chắc chắn sẽ có hai proxy. Kết quả phụ thuộc điểm khớp, loại proxy, cách Spring nhận diện đối tượng đã được proxy, thứ tự xử lý và cấu hình từng creator.

Nếu mỗi tầng gắn **một advice khác nhau**, việc có hai lần ghi log hoặc hai lần kiểm tra không nhất thiết là lỗi: đó có thể là hai advice hợp lệ. Nguy cơ **advice bị áp dụng hai lần ngoài ý muốn** xảy ra khi cùng một logic/advisor được gắn lại tại nhiều tầng hoặc khi người viết không dự liệu hiệu ứng của các tầng proxy.

Các hậu quả cần theo dõi:

| Điều kiện xuất hiện | Triệu chứng có thể gặp | Tác động |
| --- | --- | --- |
| Hai creator độc lập cùng match bean | **AopUtils.isAopProxy(bean)** là true và target của proxy lại cũng là proxy | Khó theo dõi đường đi lời gọi và thứ tự interceptor |
| Cùng logic advice được áp dụng ở nhiều tầng | Log/metric/validation thực thi nhiều lần | Đếm trùng, tác dụng phụ ngoài dự kiến |
| Creator sau nhìn thấy một proxy thay vì target ban đầu | Không nhận diện đúng target type, interface hay pointcut cần khớp | Advice không khớp như mong đợi |
| Thiết lập proxy khác nhau (JDK interface proxy so với proxy theo class) | Kiểu bean expose ra container thay đổi | Có thể phát sinh lỗi inject/cast nếu mã phụ thuộc class cụ thể |
| Nhiều tầng đều quản lý advice với thứ tự riêng | Thứ tự before/after khó kiểm soát | Transaction/logging/security behavior khó diễn giải |
| Tạo proxy sớm trong chu kỳ phụ thuộc vòng | Bean được tham chiếu sớm có thể không trùng đối tượng cuối | Có thể phát sinh lỗi nhất quán tham chiếu hoặc khởi tạo, tùy tình huống |

Không phải hiện tượng nào ở bảng cũng là bằng chứng của nhiều auto-proxy creator. Ví dụ **self-invocation** không được advice chặn vốn là đặc tính của Spring AOP proxy, có thể xảy ra ngay cả với **một** creator. Phải kiểm tra các creator và proxy thực tế trước khi quy nguyên nhân.

## 5. Ví dụ tốt: để Spring phối hợp một creator, gắn nhiều hành vi bằng Advisor

Ví dụ Java 17+ với **Spring Framework 6.2.x** minh họa đồng thời bật hạ tầng **@EnableAspectJAutoProxy** và **@EnableTransactionManagement**, rồi đăng ký **một Spring Advisor** để chặn phương thức nghiệp vụ. Việc bật hai cấu hình này không tạo thêm một auto-proxy creator độc lập do người dùng định nghĩa.

Cấu trúc dự án Maven:

~~~text
apc-coordination/
├── pom.xml
└── src/main/java/Main.java
~~~

**pom.xml**:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>apc-coordination</artifactId>
    <version>1.0-SNAPSHOT</version>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
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
                <version>3.14.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/Main.java**:

~~~java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

public class Main {
    public interface JobService {
        void execute();
    }

    public static class JobServiceImpl implements JobService {
        @Override
        public void execute() {
            System.out.println("business-execute");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    public static class AppConfig {
        @Bean
        JobService jobService() {
            return new JobServiceImpl();
        }

        @Bean
        Advisor auditAdvisor() {
            NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
            pointcut.setMappedName("execute");

            MethodInterceptor advice = invocation -> {
                System.out.println("audit-before");
                return invocation.proceed();
            };
            return new DefaultPointcutAdvisor(pointcut, advice);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            int creatorCount =
                    context.getBeansOfType(AbstractAutoProxyCreator.class).size();
            JobService service = context.getBean(JobService.class);

            System.out.println("APC beans: " + creatorCount);
            System.out.println("Service is proxy: " + AopUtils.isAopProxy(service));
            service.execute();
        }
    }
}
~~~

Chạy bằng Maven tại thư mục chứa **pom.xml**:

~~~bash
mvn -q compile
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=Main
~~~

**Những dòng do Main in ra** (không tính thông tin riêng từ Maven hoặc logging môi trường):

~~~text
APC beans: 1
Service is proxy: true
audit-before
business-execute
~~~

Giải thích:

1. **@EnableAspectJAutoProxy** và **@EnableTransactionManagement** cùng tham gia cấu hình hạ tầng Spring AOP. Ở ví dụ này, container chỉ đăng ký một bean creator chuẩn thuộc **AbstractAutoProxyCreator**.
2. **auditAdvisor()** cung cấp một advisor bình thường, nên creator hạ tầng có thể tìm thấy và gắn vào proxy cho **JobService**.
3. Method **execute()** được gọi qua proxy, in **audit-before** rồi tới nghiệp vụ **business-execute**.
4. Không có phương thức đánh dấu **@Transactional** trong ví dụ, vì thế ví dụ **không mở transaction thực tế** và không cần triển khai **PlatformTransactionManager**. Mục đích của annotation transaction là minh họa cơ chế cấu hình phối hợp, không minh họa giao dịch.

Số đếm trên phản ánh **các bean creator được đăng ký trong ApplicationContext**; không bao gồm BeanPostProcessor được chèn vào factory hoàn toàn bằng mã mà không đăng ký thành bean, và không phải phép đo số tầng proxy của từng service.

## 6. Ví dụ đối chứng: tự đăng ký thêm một creator độc lập

Giả sử nhóm phát triển chèn **hai @Bean dưới đây** vào class **AppConfig** của ví dụ trước:

~~~java
@Bean
static MethodInterceptor traceInterceptor() {
    return invocation -> {
        System.out.println("trace-before");
        return invocation.proceed();
    };
}

@Bean
static org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator
extraAutoProxyCreator() {
    var creator =
            new org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator();
    creator.setBeanNames("jobService");
    creator.setInterceptorNames("traceInterceptor");
    return creator;
}
~~~

Đoạn trên là **phần bổ sung vào AppConfig**, không phải class Java độc lập. Khác với việc khai báo thêm một advisor, **extraAutoProxyCreator()** chủ động tạo thêm một **BeanPostProcessor** chuyên tạo AOP proxy theo tên bean. Bean đó được đăng ký bằng **tên riêng**, bên cạnh creator nội bộ do Spring quản lý.

Với phần bổ sung này, phép đếm **getBeansOfType(AbstractAutoProxyCreator.class)** có thể cho thấy **hai bean creator** trong cùng context. Hai creator cùng có điều kiện chọn **jobService**, nên phải kiểm tra proxy thực tế và cấu hình advice để biết cách các tầng chồng lên nhau. Đừng sao chép cấu hình này vào ứng dụng chỉ để thêm một thao tác log.

Đường sửa tốt hơn nếu mục tiêu chỉ là thêm **trace-before**:
- Bỏ định nghĩa **extraAutoProxyCreator()**.
- Biểu diễn yêu cầu trace bằng một **Advisor** khác, có pointcut rõ ràng, hoặc bằng một Spring **@Aspect** mà creator chuẩn đã hiểu.
- Quy định thứ tự advisor/advice tại đúng vị trí nếu thứ tự là yêu cầu nghiệp vụ. **@Order** sắp xếp advice/aspect theo cơ chế tương ứng; nó không hợp nhất hai auto-proxy creator độc lập.

Ví dụ bổ sung **Advisor** có thể thay cho creator trên:

~~~java
@Bean
Advisor traceAdvisor() {
    NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
    pointcut.setMappedName("execute");
    MethodInterceptor advice = invocation -> {
        System.out.println("trace-before");
        return invocation.proceed();
    };
    return new DefaultPointcutAdvisor(pointcut, advice);
}
~~~

Đây cũng chỉ là một phương thức khai báo bên trong **AppConfig** đã có các import từ ví dụ chính. Khi nhiều advisor cùng khớp một method, thứ tự log cần được xác định bằng cấu hình sắp xếp advisor; không nên dựa vào thứ tự hai phương thức **@Bean** trong file.

## 7. Kiểm tra Spring AOP tại runtime

Khi nghi ngờ creator bị đăng ký trùng hoặc độc lập, kiểm tra bằng chứng theo thứ tự:

### 7.1. Liệt kê creator trong cùng ApplicationContext

Thêm đoạn sau vào **main**, trước khi đóng **context**:

~~~java
context.getBeansOfType(AbstractAutoProxyCreator.class)
        .forEach((beanName, creator) ->
                System.out.println(beanName + " -> " +
                        creator.getClass().getSimpleName()));
~~~

Với ứng dụng mẫu trước khi thêm creator tùy biến, sẽ thấy **một** creator chuẩn. Khi có creator thêm theo tên riêng, bạn có thể thấy cả **internalAutoProxyCreator** và **extraAutoProxyCreator**. Đừng dùng đoạn này để kết luận trên **tất cả context cha/con**; từng ApplicationContext có bean registry và lifecycle riêng.

### 7.2. Kiểm tra proxy và target source

Spring cung cấp **AopUtils** và giao diện **Advised** để xem proxy đã tạo. Mã sau minh họa trường hợp target source là **SingletonTargetSource** (target cố định), nối vào **main** của ví dụ:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.target.SingletonTargetSource;

// Nằm bên trong main, sau khi lấy service từ context:
Object layer = service;
int depth = 0;

while (AopUtils.isAopProxy(layer) && layer instanceof Advised advised) {
    depth++;
    if (!(advised.getTargetSource() instanceof SingletonTargetSource source)) {
        break;
    }
    Object target = source.getTarget();
    if (target == layer) {
        break;
    }
    layer = target;
}
System.out.println("Observed static proxy depth: " + depth);
~~~

Các import trong đoạn trên đặt ở đầu file khi ghép mã. Với **ví dụ tốt** ở mục 5, **Observed static proxy depth: 1** là kết quả mong đợi. Nếu hai tầng proxy cố định thực sự lồng nhau, đoạn kiểm tra có thể cho độ sâu **2**. Đây là **phép kiểm tra có điều kiện**, không phải công cụ tổng quát để tự ý lấy target động: với TargetSource có quản lý vòng đời, việc gọi **getTarget()** trực tiếp có thể cần **releaseTarget()**, nên không dùng vòng lặp này cho loại TargetSource chưa biết.

Để xem advice của một tầng proxy:

~~~java
if (service instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

Nếu proxy lồng nhau, danh sách advisor của **lớp ngoài** không nhất thiết phản ánh mọi advice tại các tầng bên trong. Kết hợp thông tin với sơ đồ target chain.

### 7.3. Soi cấu hình và log

- Tìm nơi đăng ký **@EnableAspectJAutoProxy**, **@EnableTransactionManagement**, XML **aop:config**, **aop:aspectj-autoproxy**, cùng các **@Bean** trả về lớp kế thừa **AbstractAutoProxyCreator**.
- Xem có **BeanNameAutoProxyCreator** hoặc **DefaultAdvisorAutoProxyCreator** tự khai báo dưới tên khác với creator hạ tầng hay không.
- Kiểm tra mã thêm BeanPostProcessor trực tiếp vào BeanFactory: các instance này có thể không xuất hiện trong **getBeansOfType(...)**.
- Bật log ở **org.springframework.aop.framework.autoproxy** ở mức DEBUG/TRACE trong môi trường thử nghiệm; đối chiếu bean nào được tạo proxy và bởi creator nào. Nội dung thông điệp log phụ thuộc phiên bản Spring.

Ví dụ cho **application.properties** của ứng dụng Spring Boot (không dùng trực tiếp cho ví dụ Maven với AnnotationConfigApplicationContext ở trên):

~~~properties
logging.level.org.springframework.aop.framework.autoproxy=TRACE
~~~

Trước khi kết luận, xem cả **AopUtils.isJdkDynamicProxy(bean)**, **AopUtils.isCglibProxy(bean)** và kiểu interface được expose. Một proxy có thật nhưng không expose class cụ thể có thể khiến injection theo class gặp trục trặc.

## 8. Cách thiết kế tránh nhiều creator không phối hợp

**Ưu tiên phối hợp qua cấu hình hạ tầng Spring sẵn có**: dùng các annotation enable, advisor và aspect theo nhu cầu. Không thêm auto-proxy creator thứ hai chỉ vì một advisor mới cần áp dụng lên một nhóm bean.

**Dùng một cơ chế chọn bean nhất quán**: nếu cần pointcut theo method, hãy chọn advisor/pointcut; nếu dùng **BeanNameAutoProxyCreator** với mục đích đặc thù, đánh giá tương tác với mọi hạ tầng AOP đang hoạt động trong cùng container.

**Giới hạn logic chồng chéo**: lập bảng bean được match, advisor áp dụng và thứ tự mong muốn. Một method có hai advice hợp lệ vẫn có thể là đúng; quan trọng là tránh **cùng một hành vi bị cấu hình hai lần ngoài ý muốn**.

**Giữ proxy type có chủ đích**: cân nhắc kiểu interface proxy và class proxy, đặc biệt ở bean được inject bằng class cụ thể. Khi bật **proxyTargetClass=true**, hiểu rằng cấu hình AOP chuẩn có thể áp dụng chọn lựa này trên creator hợp nhất.

**Kiểm tra bằng test integration khi cần**: trong một **ApplicationContext** thực, assert số creator hạ tầng phù hợp, **AopUtils.isAopProxy**, kiểu proxy và số lần advice chạy trong một kịch bản nghiệp vụ. Chỉ assert số tầng proxy bằng kỹ thuật truy cập target khi biết TargetSource hỗ trợ kiểu kiểm tra đó.

**Không xử lý nhầm vấn đề**: nếu advice không chạy do self-invocation, method final/private hoặc đối tượng được tạo bằng **new** ngoài Spring container, thêm một creator thường không giải quyết được. Hãy xem bean có thực sự do Spring quản lý, client có gọi qua proxy, và proxy có khả năng chặn method hay không.

## 9. Tình huống suy luận nhanh

**Tình huống A:** Một ứng dụng khai báo **@EnableAspectJAutoProxy**, **@EnableTransactionManagement** và nhiều **@Aspect**. Có chắc xuất hiện nhiều auto-proxy creator không?

**Không.** Các cấu hình hạ tầng chuẩn có cơ chế hợp nhất/nâng cấp creator; nhiều aspect có thể được áp dụng bởi cùng một creator. Cần xác minh container thực tế nếu có cấu hình tùy biến ngoài chuẩn.

**Tình huống B:** Để log tất cả bean có tên **jobService**, nhóm tự tạo **BeanNameAutoProxyCreator** cạnh creator Spring đang dùng. Có an toàn vì đây là class Spring cung cấp không?

**Không có bảo đảm.** Class là của Spring, nhưng bean creator mới được đăng ký độc lập. Cần kiểm tra nó và creator sẵn có có cùng match bean không. Nếu chỉ cần thêm hành vi, nên cân nhắc Advisor/Aspect.

**Tình huống C:** Quan sát hai lời log **before** khi gọi **service.execute()**. Điều đó đủ kết luận double proxy chưa?

**Chưa.** Một proxy có thể chứa hai advice, hoặc một advice có thể được gắn hai lần. Kiểm tra advisor chain, creator và target chain để xác định nguyên nhân.

**Tình huống D:** Sau khi bật class-based proxy ở một cấu hình, service của một tính năng khác cũng chuyển sang class proxy. Đây nhất thiết là bug?

**Không.** Với cơ chế hợp nhất chuẩn, tùy chọn proxy mạnh hơn có thể chi phối creator dùng chung. Hãy rà soát mục đích cấu hình và yêu cầu inject kiểu cụ thể.

## 10. Tài liệu Spring tham khảo

- **Spring Framework 6.2 — Proxying Mechanisms:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- **Spring Framework 6.2 — Using the auto-proxy facility:** https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/autoproxy.html
- **AnnotationAwareAspectJAutoProxyCreator Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html
- **AbstractAutoProxyCreator Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html

**Ghi nhớ:** Spring AOP đã có cơ chế phối hợp các auto-proxy creator hạ tầng. Rủi ro đến từ việc thêm những creator hoạt động độc lập mà không hiểu giao điểm chọn bean, thứ tự xử lý và cấu trúc proxy thực tế. Khi yêu cầu chỉ là thêm cross-cutting behavior, hãy bắt đầu bằng **Advisor/Aspect trên cấu hình Spring AOP hiện hữu**, rồi xác minh bằng context và proxy runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-008">Chẩn đoán bean không đủ điều kiện được xử lý đầy đủ</a>

<details>
<summary>Xem chi tiết</summary>

# Auto-proxy nâng cao: chẩn đoán bean không đủ điều kiện được xử lý đầy đủ

## 1. Vấn đề cần nhận diện

Trong Spring Framework, đôi khi lúc khởi động ứng dụng xuất hiện cảnh báo tương tự:

~~~text
WARN ... PostProcessorRegistrationDelegate$BeanPostProcessorChecker :
Bean 'orderService' of type [com.example.OrderService] is not eligible
for getting processed by all BeanPostProcessors
(for example: not eligible for auto-proxying).
~~~

Ở nhiều bản Spring 6.x, log còn gợi ý tên một `BeanPostProcessor` đang được tạo và yêu cầu xem lại các dependency/advisor mà nó kéo theo. **Cảnh báo này cho biết bean có thể được khởi tạo trong lúc tập hợp `BeanPostProcessor` chưa được đăng ký đầy đủ**. Vì auto-proxy của Spring AOP cũng được triển khai thông qua một `BeanPostProcessor`, bean được tạo quá sớm có thể không được bọc proxy như mong đợi.

Phạm vi bài này là **chẩn đoán cơ chế auto-proxy của Spring**: tìm bean được tạo sớm, xác định vì sao Spring kéo bean đó vào quá trình tạo post-processor, kiểm chứng tác động thực tế lên advice và sửa chuỗi phụ thuộc. Đây không phải là lời khẳng định rằng mọi bean đều phải có proxy.

### Phân biệt ba kết luận

| Quan sát | Diễn giải đúng | Cần kiểm tra |
|---|---|---|
| Có log `not eligible for getting processed by all BeanPostProcessors` | Bean không đi qua toàn bộ các post-processor dự kiến tại thời điểm khởi tạo; Spring nêu auto-proxy như một hệ quả **có thể** xảy ra | Thời điểm khởi tạo, dependency của post-processor, proxy/advice thực tế |
| Bean không phải AOP proxy, nhưng **không có** cảnh báo trên | Có thể không có advisor phù hợp, điểm cắt không khớp, bean không thuộc phạm vi hỗ trợ, hoặc không bật cơ chế liên quan | Advisor, pointcut, `@EnableAspectJAutoProxy`/cấu hình tương ứng |
| Bean là proxy nhưng advice không chạy ở một lời gọi | Có thể gọi sai đường đi qua proxy, ví dụ self-invocation; hoặc method không thể được advice như cấu hình | Kiểu proxy, method, đường gọi thực tế |

**Không dùng riêng một cảnh báo hoặc riêng `AopUtils.isAopProxy(...)` để kết luận toàn bộ hệ thống AOP bị lỗi.** Đánh giá phải dựa trên chức năng mà bean cụ thể cần được áp dụng.

## 2. Vì sao bean được tạo quá sớm?

`ApplicationContext` tự phát hiện các bean định nghĩa `BeanPostProcessor` và phải tạo, sắp xếp, đăng ký chúng trước khi khởi tạo phần lớn bean nghiệp vụ. Trong giai đoạn này, để tạo một post-processor, Spring cũng cần tạo **mọi dependency được yêu cầu ngay** bởi constructor, tham số factory method hoặc autowiring của post-processor đó.

Diễn tiến giản lược:

~~~text
Đọc định nghĩa bean và phát hiện BeanPostProcessor
            |
            v
Khởi tạo / đăng ký các BeanPostProcessor theo pha, thứ tự
            |
            +-- BPP_A yêu cầu OrderService ngay trong constructor/@Bean
            |         |
            |         +--> OrderService được khởi tạo quá sớm
            |              khi chưa đủ BPP (có thể thiếu auto-proxy creator)
            |
            v
Đăng ký xong các BeanPostProcessor còn lại
            |
            v
Khởi tạo các bean nghiệp vụ bình thường
            |
            +--> auto-proxy creator xét advisor và tạo proxy nếu phù hợp
~~~

Các thành phần như `AnnotationAwareAspectJAutoProxyCreator` và `InfrastructureAdvisorAutoProxyCreator` thuộc hạ tầng auto-proxy; chúng tham gia vào vòng đời post-processing. Việc bean `OrderService` đã được tạo trong pha đăng ký post-processor có nghĩa nó **không tự động được chạy lại toàn bộ chuỗi post-processor** khi danh sách sau đó hoàn thiện.

`BeanPostProcessorChecker` là cơ chế báo hiệu của container, không phải một exception chứng minh bean bị lỗi hoặc Spring đã từ chối mọi post-processor. Nội dung log, mức log và tên bean được gợi ý có thể thay đổi giữa phiên bản Spring; cần đọc log đang chạy thực tế.

### Tại sao post-processor cũng là trường hợp đặc biệt?

Bản thân bean `BeanPostProcessor` được tạo để xử lý các bean khác. Spring không bảo đảm chính nó hay các bean mà nó trực tiếp kéo vào trong pha đăng ký được xử lý đầy đủ bởi mọi post-processor, bao gồm auto-proxy creator. Đây là giới hạn về **thứ tự khởi tạo của container**, không phải cách hoạt động riêng của một annotation advice.

## 3. Các nguyên nhân cần điều tra theo thứ tự

1. **`BeanPostProcessor` phụ thuộc trực tiếp vào bean nghiệp vụ.** Ví dụ `new AuditProcessor(orderService)` hoặc phương thức `@Bean` nhận `OrderService` làm tham số. Container buộc phải tạo `OrderService` để tạo `AuditProcessor` trong giai đoạn sớm.
2. **Phương thức `@Bean` tạo `BeanPostProcessor` là phương thức instance.** Để gọi phương thức này, Spring có thể phải tạo sớm chính bean `@Configuration` và chuỗi dependency liên quan. Với phương thức factory tạo post-processor, Spring khuyến nghị `static` và lý tưởng là không có dependency nghiệp vụ.
3. **Factory method khai báo kiểu trả về không đủ rõ.** Nếu `@Bean` thực sự trả về `BeanPostProcessor` nhưng chữ ký khai báo `Object` hoặc một interface không phản ánh tính chất post-processor, việc nhận diện sớm bằng kiểu có thể bị ảnh hưởng. Khai báo lớp post-processor cụ thể hoặc ít nhất `BeanPostProcessor`.
4. **Dependency ẩn khi tìm advisor hoặc resolve theo kiểu.** Một advisor, aspect, factory, post-processor hoặc đường autowiring/`@Resource` có thể khiến container truy vấn và tạo bean khác sớm hơn dự kiến. Không chỉ xem constructor của bean đang bị cảnh báo; phải lần ngược tới thành phần gây ra lần `getBean` sớm.
5. **Mã khởi động chủ động lấy bean quá sớm.** Một `BeanFactoryPostProcessor`, `BeanDefinitionRegistryPostProcessor` hoặc custom infrastructure gọi `getBean(...)` không đúng thời điểm có thể kéo theo toàn bộ đồ thị phụ thuộc nghiệp vụ.

Các nguyên nhân trên cần được xác minh theo đồ thị tạo bean thực tế. **Không suy ra nguyên nhân chỉ từ tên bean trong log**: bean được nêu là đối tượng bị ảnh hưởng; tên post-processor trong phần gợi ý (nếu có) là điểm bắt đầu điều tra.

## 4. Ví dụ thực hành: cùng một service, khác thời điểm khởi tạo

Ví dụ độc lập dưới đây dùng Spring Framework 6.x, Java 17+ và auto-proxy với `@Aspect`. Ta khai báo một advice áp dụng cho bean có tên `workService`. Ở trường hợp sai, post-processor ưu tiên cao yêu cầu `WorkService` khi chính nó đang được đăng ký. Ở trường hợp đúng, post-processor không kéo `WorkService` vào giai đoạn sớm.

### Khai báo Gradle

Tạo project Java thông thường với hai tệp:

`settings.gradle`

~~~groovy
rootProject.name = 'spring-auto-proxy-diagnostic'
~~~

`build.gradle`

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-aop:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.EligibilityDemo'
}
~~~

Tạo `src/main/java/demo/EligibilityDemo.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

public class EligibilityDemo {
    public static void main(String[] args) {
        run("BAD", BadConfig.class);
        run("GOOD", GoodConfig.class);
    }

    private static void run(String label, Class<?> config) {
        try (var context = new AnnotationConfigApplicationContext(config)) {
            WorkService service = context.getBean(WorkService.class);
            System.out.println(label + " proxied=" + AopUtils.isAopProxy(service));
            service.work();
        }
    }

    public static class WorkService {
        public void work() {
            System.out.println("work");
        }
    }

    @Aspect
    public static class TraceAspect {
        // Spring AOP hỗ trợ pointcut designator bean(...)
        @Before("bean(workService)")
        public void beforeWork() {
            System.out.println("advice");
        }
    }

    public static class EarlyObserver implements BeanPostProcessor, PriorityOrdered {
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
        // Dùng callback mặc định của BeanPostProcessor.
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class BadConfig {
        @Bean
        WorkService workService() {
            return new WorkService();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }

        @Bean
        static EarlyObserver earlyObserver(WorkService dependency) {
            // Chỉ riêng việc yêu cầu tham số cũng kích hoạt tạo bean sớm.
            return new EarlyObserver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class GoodConfig {
        @Bean
        WorkService workService() {
            return new WorkService();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }

        @Bean
        static EarlyObserver earlyObserver() {
            return new EarlyObserver();
        }
    }
}
~~~

Chạy bằng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows), với Gradle wrapper đã có trong project. Phần stdout liên quan đến ví dụ **dự kiến** như sau (đã lược bỏ thông báo khởi động, cảnh báo và log framework):

~~~text
BAD proxied=false
work
GOOD proxied=true
advice
work
~~~

Trong `BadConfig`, `EarlyObserver` thuộc nhóm `PriorityOrdered`, vì vậy container tạo nó khi đăng ký nhóm post-processor ưu tiên sớm; tham số `WorkService dependency` khiến service được lấy ra trước khi auto-proxy creator hoàn tất đăng ký. `GoodConfig` bỏ dependency đó nên service có thể được tạo sau, khi auto-proxy creator có cơ hội xét `TraceAspect`.

**Chú ý:** `static` ở cả hai factory method chỉ loại trừ nhu cầu tạo sớm instance cấu hình để gọi factory method. Nó **không** giải quyết việc factory method vẫn yêu cầu bean nghiệp vụ bằng tham số. Đây là lý do ví dụ cố ý giữ `static` trong trường hợp sai.

Nếu không thấy cảnh báo nhưng `BAD proxied=false`, vẫn cần xét thứ tự đăng ký trên phiên bản thực tế. Nếu cả hai trường hợp không tạo proxy, kiểm tra có dependency `aspectjweaver`, có `@EnableAspectJAutoProxy`, có `TraceAspect` được quản lý bởi container, có pointcut phù hợp và không bị trùng/xung đột cấu hình.

## 5. Quy trình chẩn đoán trên ứng dụng thật

### Bước 1: Ghi lại chứng cứ từ log

Tìm các dòng chứa `BeanPostProcessorChecker` hoặc `not eligible for getting processed by all BeanPostProcessors`. Ghi chính xác:

- Tên và lớp của bean bị ảnh hưởng.
- Tên post-processor được gợi ý sau cụm `currently created BeanPostProcessor`, nếu phiên bản hiện tại cung cấp.
- Những bean bị cảnh báo gần nhau về thời gian; chúng có thể thuộc cùng một đồ thị dependency.
- Cấu hình AOP áp dụng cho bean: advisor, aspect, pointcut, `@EnableAspectJAutoProxy` hoặc cấu hình proxy tương ứng của ứng dụng.

Với Spring Boot có thể bật log chẩn đoán trong môi trường phát triển:

~~~properties
logging.level.org.springframework.aop.framework.autoproxy=TRACE
logging.level.org.springframework.beans.factory.support=DEBUG
~~~

Các logger này giúp quan sát thêm việc quyết định proxy và việc tạo bean, nhưng mức chi tiết thay đổi tùy bản Spring, có thể rất nhiều dòng và không thay thế được việc kiểm tra proxy thực tế. Không cần bật TRACE lâu dài trong production. Riêng thông báo `BeanPostProcessorChecker` thường đã ở mức WARN trên các bản Spring 6.x liên quan.

### Bước 2: Kiểm tra bean mà container thực sự cung cấp

Với bean đã lấy **sau khi `ApplicationContext` khởi động hoàn tất**:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("orderService");

System.out.println("proxy=" + AopUtils.isAopProxy(bean));
System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglibProxy=" + AopUtils.isCglibProxy(bean));
System.out.println("targetType=" + AopUtils.getTargetClass(bean).getName());

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor);
    }
}
~~~

Đây là **đoạn mã chèn vào ứng dụng đang có biến `context`**, không phải file chạy độc lập. `AopUtils.isAopProxy(bean)` cho biết đối tượng trả về có phải Spring AOP proxy hay không. Danh sách `Advisor` của `Advised` hỗ trợ đối chiếu advice cụ thể; không phải mọi proxy do framework khác tạo đều là `Advised`.

Nếu bean cần `@Transactional`, caching, async hoặc custom advice, hãy gọi đúng method qua bean mà container cấp phát rồi quan sát hiệu ứng nghiệp vụ tương ứng. Đừng chỉ kiểm tra tên lớp có hậu tố `$$SpringCGLIB$$`; đó không phải phép thử đủ mạnh cho advice mong muốn.

### Bước 3: Lần ngược phụ thuộc gây khởi tạo sớm

Bắt đầu tại post-processor được log gợi ý. Kiểm tra chữ ký `@Bean`, constructor, trường `@Autowired`/`@Resource` và các tham số của factory method. Vẽ chuỗi:

~~~text
postProcessorA
  -> advisorFactory / helper / configuration
      -> orderService
          -> repository ...
~~~

Tìm thời điểm service bị tạo lần đầu. Khi cần chẩn đoán sâu, đặt breakpoint tại factory method/constructor của service và xem call stack; xác nhận nhánh gọi bắt đầu từ quá trình tạo `BeanPostProcessor`. Đây là chứng cứ mạnh hơn việc đoán dựa vào tên bean.

Một lưu ý riêng cho `@Resource`: nếu không khớp theo tên và phải tìm dependency theo kiểu, Spring có thể chạm đến nhiều bean candidate ngoài dự kiến. Nên kiểm tra cả tên khai báo và quá trình resolve theo kiểu ở những dependency trong post-processor.

### Bước 4: Phân loại tác động

- **Bean nghiệp vụ cần Spring AOP nhưng không được proxy, advice không chạy:** lỗi chức năng có thể ảnh hưởng transaction, retry, audit hoặc quy tắc bảo vệ do advisor triển khai; ưu tiên sửa dependency khởi tạo sớm.
- **Bean hạ tầng được tạo sớm, không cần mọi post-processor/advice:** có thể là đặc tính dự kiến; xác minh vai trò trước khi giảm mức độ cảnh báo.
- **Bean là proxy, nhưng một advice chưa chạy:** điều tra advisor, pointcut, thứ tự advice và đường gọi trước khi quy toàn bộ lỗi cho `BeanPostProcessorChecker`.

### Bước 5: Kiểm chứng sau khi sửa

Khởi động lại một context mới, lấy bean sau `refresh()` và so sánh: cảnh báo có còn không, bean có proxy như yêu cầu không, advisor dự kiến có nằm trên proxy không, gọi từ một bean khác qua proxy có kích hoạt đúng advice không. Thêm test hành vi nếu advice liên quan đến chức năng quan trọng; chỉ kiểm tra log biến mất chưa đủ.

## 6. Cách sửa và giới hạn của từng cách

| Cách làm | Khi nào hợp lý | Điều cần nhớ |
|---|---|---|
| Khai báo factory tạo `BeanPostProcessor` bằng `static @Bean`, kiểu trả về thể hiện đúng post-processor | Tạo custom post-processor trong cấu hình Java | `static` giúp tránh tạo sớm cấu hình; vẫn cần loại dependency nghiệp vụ |
| Loại bỏ dependency nghiệp vụ khỏi constructor/tham số `@Bean` của post-processor | Khi service bị kéo vào lúc đăng ký post-processor | Thường là cách sửa trực tiếp nhất; đưa logic nghiệp vụ ra lớp chạy sau khi context sẵn sàng |
| Chỉ dùng các dependency hạ tầng thực sự cần để đăng ký post-processor | Khi post-processor cần metadata hoặc cấu hình đơn giản | Kiểm tra dependency gián tiếp và advisor của nó |
| Hoãn truy cập bằng `ObjectProvider<T>`/`@Lazy` trong thiết kế phù hợp | Khi dependency có thể được lấy **sau** giai đoạn đăng ký | Chỉ hiệu quả nếu **không gọi** `getObject()`/method trên lazy proxy ngay trong constructor, factory hoặc callback quá sớm |
| Kiểm tra `ROLE_INFRASTRUCTURE` cho bean hạ tầng thực sự không cần post-processing | Khi đã chứng minh bean chỉ thuộc hạ tầng và cảnh báo vô hại | Đánh dấu vai trò giúp container nhận diện hạ tầng/giảm cảnh báo ở nhánh phù hợp; **không tạo lại proxy đã bỏ lỡ** |

Ví dụ sửa ngắn nhất thường là thay:

~~~java
@Bean
static AuditBeanPostProcessor auditProcessor(OrderService service) {
    return new AuditBeanPostProcessor(service);
}
~~~

bằng thiết kế post-processor không cần `OrderService` khi khởi tạo:

~~~java
@Bean
static AuditBeanPostProcessor auditProcessor() {
    return new AuditBeanPostProcessor();
}
~~~

Hai đoạn này minh họa **chữ ký factory method**, giả sử các lớp đã được định nghĩa ở ứng dụng; không phải ví dụ Java độc lập. Bản sửa thực tế còn phải chuyển phần chức năng trước đây phụ thuộc `OrderService` đến nơi thích hợp trong vòng đời ứng dụng.

**Không coi các thao tác sau là giải pháp mặc định:**

- Chỉ thêm `@Order`, `@DependsOn` hoặc đổi thứ tự các `@Bean` mà vẫn giữ dependency khởi tạo sớm: không bảo đảm bean nghiệp vụ được xử lý bởi đủ post-processor.
- Đặt `@Lazy` nhưng sử dụng dependency ngay trong quá trình tạo post-processor: bean vẫn có thể bị tạo sớm.
- Ép `proxyTargetClass=true`: thay đổi chiến lược proxy JDK/CGLIB, không bổ sung post-processor chưa kịp đăng ký.
- Chuyển mọi bean bị cảnh báo thành `ROLE_INFRASTRUCTURE` hoặc tắt logger trên diện rộng: có thể che giấu bean nghiệp vụ thực sự bị thiếu advice.
- Tự bọc proxy mới sau khi context đã khởi động để “bù” một cách đại trà: có thể không thay thế được các tham chiếu thô đã được inject trước đó và không phục hồi mọi callback bị bỏ lỡ.

## 7. Các tình huống dễ chẩn đoán nhầm

### Có cảnh báo nhưng không có yêu cầu proxy

Ví dụ một bean chỉ cung cấp metadata cho framework và không có advisor/logic nào cần áp dụng cho nó. Khi đó cảnh báo chỉ nói về khả năng xử lý chưa đủ, không tự chứng minh lỗi chức năng. Ghi nhận vai trò của bean rồi đánh giá xem cần thay đổi gì; đừng sửa bừa thứ tự khởi tạo.

### Không có cảnh báo nhưng thiếu advice

Đừng tập trung sai vào `BeanPostProcessorChecker`. Hãy kiểm tra phạm vi bean Spring quản lý, aspect/advisor có được đăng ký, pointcut có khớp class và method, kiểu proxy, giới hạn `final` và lời gọi đi qua proxy hay không. Đặc biệt, một lời gọi `this.someMethod()` trong chính target thường bỏ qua đường gọi qua Spring AOP proxy.

### Bean có proxy, nhưng không phải advice mong muốn

Một proxy có thể tồn tại nhờ advisor khác. `isAopProxy=true` không có nghĩa advisor đang điều tra đã được áp dụng. Kiểm tra `Advised.getAdvisors()` (khi dùng được), ghi nhận method thực sự được gọi và xác minh hành vi của từng advice cần thiết.

### Chỉ xuất hiện sau khi thêm starter/auto-configuration

Một starter có thể thêm `BeanPostProcessor`, `Advisor` hoặc dependency khiến đồ thị khởi tạo sớm thay đổi. So sánh cảnh báo và call stack trước/sau khi thêm dependency; không mặc định rằng chính bean có tên trong warning là thủ phạm. Nếu thuộc auto-configuration bên thứ ba, tối thiểu hóa ví dụ tái hiện, đối chiếu phiên bản tương thích trước khi áp dụng workaround.

## 8. Checklist hoàn tất

- [ ] Đã xác định chính xác bean bị cảnh báo, post-processor liên quan và nơi bean được tạo lần đầu.
- [ ] Đã chứng minh có hay không dependency sớm từ `BeanPostProcessor`/advisor/factory đến bean nghiệp vụ.
- [ ] Đã kiểm tra đối tượng lấy từ container sau `refresh()` bằng `AopUtils` và, khi thích hợp, danh sách advisor.
- [ ] Đã kiểm tra **hành vi advice** qua lời gọi đi qua proxy, không chỉ nhìn log hoặc class name.
- [ ] Đã loại bỏ dependency khởi tạo sớm hoặc giải thích rõ vì sao bean hạ tầng không cần post-processing.
- [ ] Đã khởi động lại context, xác nhận kết quả chức năng và không tạo thêm chuỗi phụ thuộc sớm khác.

## 9. Tài liệu tham khảo chính thức

- Spring Framework — Container Extension Points, phần `BeanPostProcessor` và early initialization: https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html
- Spring Framework — Auto-proxy facility: https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Javadoc — `AopUtils`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html

**Điểm kiểm chứng quan trọng nhất:** từ cảnh báo, truy ngược tới bean được tạo trong pha đăng ký post-processor, sau đó xác nhận trên **đúng bean lấy từ Spring container** rằng proxy và advice nghiệp vụ đã hoạt động theo yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-auto-proxy-009">Giới hạn mở rộng hạ tầng AOP bằng API nội bộ theo phiên bản Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn mở rộng hạ tầng Spring AOP bằng API nội bộ theo phiên bản Spring

> **Phạm vi:** Bài này chỉ bàn về Spring AOP, đặc biệt cơ chế auto-proxy và việc mở rộng nó khi dùng các lớp/hook nằm sâu trong hạ tầng Spring. Đây không phải bài tổng quan AOP hay hướng dẫn AspectJ weaving. Các khái niệm pointcut, advice, advisor được giải thích theo cách Spring dùng chúng để quyết định tạo proxy.

## 1. Vấn đề cần giải quyết

Giả sử một thư viện nội bộ muốn tự động gắn logging, kiểm toán hoặc chính sách bảo vệ cho hàng trăm Spring bean. Cách dễ gặp là kế thừa `AbstractAutoProxyCreator`, sửa cách tìm `Advisor`, hoặc đăng ký thêm một bean auto-proxy creator để can thiệp vòng đời khởi tạo bean. Cách này có thể hoạt động trong một bản Spring cụ thể, nhưng dễ phát sinh lỗi sau khi nâng phiên bản:

- Bean không được proxy, được proxy hai lần hoặc nhận advisor theo thứ tự không mong muốn.
- Một bean được tạo quá sớm, trước khi toàn bộ `BeanPostProcessor` có hiệu lực.
- Hạ tầng `@Aspect`, `@Transactional` hoặc `@Async` tác động đến cùng một bean theo cơ chế cấu hình proxy thống nhất.
- Phương thức `protected`/`package-private`, tên bean hoặc cách sắp xếp advisors từng phụ thuộc vào triển khai cụ thể có thể thay đổi.
- Lỗi chỉ xuất hiện lúc chạy: code vẫn biên dịch nhưng hook bị gọi vào thời điểm khác, cách loại trừ infrastructure bean khác hoặc proxy trở thành kiểu khác.

**Nguyên tắc:** Trước tiên xác định điều cần mở rộng là *advice/pointcut*, *quy tắc chọn advisor*, *kiểu proxy* hay chính *chu trình tạo proxy*. Ba nhu cầu đầu thường có điểm mở rộng được tài liệu hóa; nhu cầu cuối cần thận trọng hơn nhiều.

## 2. Auto-proxy trong Spring thực sự vận hành ở đâu?

Luồng khái niệm (đã giản lược, không đại diện chính xác mọi method nội bộ):

~~~text
Đăng ký BeanDefinition và BeanPostProcessor
                   |
Khởi tạo bean theo vòng đời của Spring IoC
                   |
Auto-proxy creator xem xét bean đủ điều kiện
                   |
Tìm Advisor / @Aspect phù hợp và áp dụng pointcut
                   |
       +-----------+-----------+
       |                       |
     Có advice                Không có
       |                       |
Tạo JDK/CGLIB proxy        Giữ bean gốc
       |                       |
Đưa bean tới các caller qua ApplicationContext
~~~

Trong Spring AOP, một `Advisor` liên kết `Advice` với `Pointcut`. Hạ tầng quyết định những advisor nào áp dụng lên bean, rồi tạo chuỗi interceptor trong proxy. Với cấu hình `@EnableAspectJAutoProxy`, lớp `AnnotationAwareAspectJAutoProxyCreator` tham gia tìm cả Spring `Advisor` và các aspect khai báo bằng `@Aspect`. Nó kế thừa qua các lớp như `AspectJAwareAdvisorAutoProxyCreator`, `AbstractAdvisorAutoProxyCreator` và `AbstractAutoProxyCreator`.

Auto-proxy creator cũng là một `BeanPostProcessor`. Vì vậy, đây là phần của **vòng đời container**, không phải chỉ là hàm bọc method thông thường. Thêm một processor sai lúc, ép tạo bean sớm hoặc thay cơ chế tìm advisor có thể ảnh hưởng tới transaction, cache và những tính năng khác cũng dùng proxy.

**Phân biệt bốn tầng mở rộng:**

| Tầng | Nhu cầu | Điểm mở rộng nên cân nhắc | Mức phụ thuộc vào triển khai |
| --- | --- | --- | --- |
| Khai báo | Thêm logic trước/sau/quanh lời gọi method | `@Aspect`, `@Before`, `@Around`, expression pointcut | Thấp, theo tài liệu Spring AOP |
| Lựa chọn method | Tạo điều kiện match tùy biến | `Pointcut`, `MethodMatcher`, `DefaultPointcutAdvisor`, `MethodInterceptor` | Thấp đến trung bình; phải kiểm thử ngữ nghĩa |
| Tạo proxy có kiểm soát | Bọc một object theo nhu cầu riêng | `ProxyFactory`, `AspectJProxyFactory` | Trung bình; người dùng tự quản lý vòng đời proxy |
| Hạ tầng tự động | Thay thuật toán bao bọc bean, cache proxy, phân loại infrastructure | Kế thừa/thay `AbstractAutoProxyCreator` và các lớp liên quan | Cao; phụ thuộc lifecycle và hook nội bộ |

Một lớp `public` trong Javadoc **không tự động đồng nghĩa** toàn bộ cách sử dụng của nó là cam kết tương thích lâu dài. Hãy phân biệt API được Spring mô tả để ứng dụng sử dụng với các lớp `public` chủ yếu phục vụ cho việc triển khai framework.

## 3. Ranh giới của các lớp và hook hạ tầng

### 3.1. Những thành phần dễ bị lạm dụng

- `AbstractAutoProxyCreator` là nền tảng tạo proxy cho các bean đủ điều kiện. Những hook như chọn advice/advisor cho bean, kiểm tra bỏ qua bean và tạo proxy nằm trong một quy trình lifecycle có nhiều nhánh: singleton, factory bean, circular reference, early reference, infrastructure bean.
- `AbstractAdvisorAutoProxyCreator` và `AnnotationAwareAspectJAutoProxyCreator` quyết định cách thu thập, lọc, sắp xếp advisors; tự subclass có thể va chạm với hạ tầng `@Aspect` và những advisor khác.
- `AopConfigUtils` được Spring dùng để đăng ký và nâng cấp mức năng lực của **một auto-proxy creator được hạ tầng quản lý**. Việc cố định tên `org.springframework.aop.config.internalAutoProxyCreator`, thay bean definition hoặc tự tạo nhiều auto-proxy creator không phải là đường mở rộng an toàn cho ứng dụng.
- `BeanFactoryAdvisorRetrievalHelper`, helper phân tích aspect, cache nội bộ, các field/hook `protected` hay package-private có thể là chi tiết cài đặt. Tồn tại trong Javadoc không bảo đảm có cùng chữ ký và cùng ngữ nghĩa ở phiên bản sau.
- `BeanPostProcessor` tự viết vẫn là API được hỗ trợ, nhưng nếu tự proxy bean phải xử lý đầy đủ thứ tự processor, tránh proxy lồng nhau và phải hiểu cách tương tác với auto-proxy creator hiện có.

Một override biên dịch thành công chưa đủ: nếu lớp cha bổ sung đường xử lý mới, hook có thể không được gọi cho mọi bean như kỳ vọng. Khi sửa phần này, kiểm thử hành vi sau bootstrap bắt buộc quan trọng hơn kiểm tra chữ ký hàm.

### 3.2. Tại sao đăng ký hai auto-proxy creator nguy hiểm?

Ví dụ ý tưởng cấu hình sau **không nên dùng như phương án mở rộng mặc định**:

~~~java
// Minh họa cách làm rủi ro, không phải cấu hình khuyến nghị:
@Bean
static BeanPostProcessor customProxyCreator() {
    return new MyAutoProxyCreator(); // Tự bao bọc những bean mà Spring cũng đang proxy
}
~~~

`MyAutoProxyCreator` chỉ là tên giả định để minh họa (đoạn trên không độc lập/không biên dịch nếu chưa định nghĩa lớp đó). Tác hại tùy cách hai processor vận hành: proxy bọc proxy, advisor bị áp dụng lặp, hoặc một processor không còn quan sát đúng target class. Nếu đang sử dụng `@EnableAspectJAutoProxy`, hãy cung cấp advisor/aspect cho auto-proxy creator đã có; đừng tự thay đường đăng ký của container chỉ để thêm một interceptor.

### 3.3. Proxy type cũng là một ranh giới

Spring AOP sử dụng JDK dynamic proxy cho interface hoặc proxy dựa trên subclass (CGLIB), tùy cấu hình và kiểu bean. CGLIB không thể override `final`/`private` methods và không thể kế thừa `final` class. Gọi nội bộ `this.otherMethod()` đi thẳng tới target, bỏ qua advice gắn ở proxy; thay auto-proxy creator không tự giải quyết được vấn đề này. JDK proxy chỉ phơi bày các interface được proxy và có thể làm thay đổi cách lấy bean theo concrete type.

Cấu hình `proxyTargetClass` có thể có tác động rộng trên container vì nhiều cấu hình proxy được thống nhất. Đừng giả định mỗi annotation bật AOP tạo ra một proxy processor độc lập chỉ quản lý tính năng của chính nó.

## 4. Khác biệt giữa các dòng phiên bản Spring

Đây là **bảng định hướng rủi ro**, không phải danh sách các thay đổi nội bộ đầy đủ. Luôn tra Javadoc/source đúng artifact đã khóa bằng dependency management; kể cả hai bản patch khác nhau cũng cần kiểm chứng các hook không thuộc hợp đồng public.

| Dòng Spring Framework | Điểm cần xác minh khi mở rộng Spring AOP | Hướng xử lý |
| --- | --- | --- |
| **5.3.x** | Cấu trúc auto-proxy lâu đời vẫn có `AbstractAutoProxyCreator`, `Advisor` và `BeanPostProcessor`. Nhiều ví dụ trên mạng nhắm phiên bản này. | Không bê nguyên subclass, tên field/hook hay ví dụ reflection sang phiên bản mới. |
| **6.0.x** | Framework chuyển sang baseline Java 17 và hệ sinh thái Jakarta. Phần lớn khái niệm AOP tiếp tục tồn tại, nhưng nâng major version vẫn phải kiểm tra tương thích source/binary/lifecycle. | Biên dịch lại, xem deprecation/removal và chạy kiểm thử trong app thực tế. |
| **6.1.x / 6.2.x** | Cùng họ Spring 6 nhưng code path tự động phát hiện bean/aspect, lựa chọn proxy và thứ tự processor có thể được chỉnh sửa. | Cố định bản dependency, tránh dựa vào method hoặc field không được tài liệu hóa; xác minh proxy/chuỗi advisors sau bootstrap. |
| **7.0.x** | Tài liệu Spring 7 nêu việc hợp nhất thiết lập proxy mặc định còn áp dụng cho các processor như `@EnableAsync`; `@Proxyable` cho phép yêu cầu kiểu proxy trên từng bean khi phù hợp. | Đánh giá lại mọi giả định về default proxy type, cấu hình toàn cục và thứ tự cấu hình; dùng API chính thức của 7.0 sau khi nâng, không gọi từ code vẫn chạy Spring 6. |

Ví dụ khác biệt **cần có kiểm tra phiên bản**: với Spring 6.x, đừng sao chép `@Proxyable` từ tài liệu 7.x vào code rồi kỳ vọng vẫn biên dịch. Ngược lại, khi chạy 7.x, không nên mặc nhiên dự đoán cấu hình `@EnableAsync` sẽ độc lập với proxy defaults do AOP/transaction thiết lập.

Không có quy tắc “một major mới luôn phá mọi hook” hay “public protected method luôn ổn qua minor”. **Mức bảo đảm** cần dựa trên tài liệu của chính API cụ thể; một method nội bộ có thể không đổi chữ ký mà vẫn đổi thời điểm gọi hoặc đối tượng đầu vào.

## 5. Giải pháp ưu tiên: thêm Advisor qua API của Spring

Nếu yêu cầu là “tự động kiểm toán lời gọi `greet` của một loại bean”, hãy cho hạ tầng AOP hiện có **một advisor** để nó tự tìm bean và tạo proxy. Đây là cách chạm vào tầng mở rộng đã được tài liệu hóa, không cần subclass auto-proxy creator.

Ví dụ Java 17+, một dự án Gradle tối giản.

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
    runtimeOnly 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.App'
}
~~~

`src/main/java/demo/App.java`:

~~~java
package demo;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            GreetingService service = context.getBean(GreetingService.class);
            System.out.println(service.greet("Lan"));
            System.out.println("AOP proxy: " + AopUtils.isAopProxy(service));
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        GreetingService greetingService() {
            return new GreetingService();
        }

        @Bean
        Advisor auditAdvisor() {
            var pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    return GreetingService.class.isAssignableFrom(targetClass)
                            && method.getName().equals("greet");
                }
            };

            MethodInterceptor advice = invocation -> {
                System.out.println("[audit] " + invocation.getMethod().getName());
                return invocation.proceed();
            };

            return new DefaultPointcutAdvisor(pointcut, advice);
        }
    }

    public static class GreetingService {
        public String greet(String name) {
            return "Xin chào " + name;
        }
    }
}
~~~

Chạy từ thư mục dự án Gradle (với Gradle và JDK 17+ đã cài): `gradle run`. Kết quả kỳ vọng từ chương trình (không tính log khởi động Gradle):

~~~text
[audit] greet
Xin chào Lan
AOP proxy: true
~~~

**Tại sao ví dụ này phù hợp?**

1. `@EnableAspectJAutoProxy` đăng ký cơ chế auto-proxy chuẩn. `AnnotationAwareAspectJAutoProxyCreator` xử lý cả các `Advisor` thông thường, dù ví dụ không khai báo aspect bằng `@Aspect`. `aspectjweaver` nằm trên runtime classpath theo yêu cầu của chế độ `@AspectJ`.
2. `StaticMethodMatcherPointcut` chỉ khớp method tên `greet` trên `GreetingService`; tránh can thiệp toàn bộ bean. `MethodInterceptor` chạy ở chuỗi advice của proxy.
3. `DefaultPointcutAdvisor` đóng gói pointcut và advice; không viết code gọi hook nội bộ, không thay bean post-processor hoặc tên bean framework.
4. `proxyTargetClass = true` được chọn vì `GreetingService` trong ví dụ không cung cấp interface; trong hệ thống thực tế phải đánh giá tác động toàn cục của tùy chọn này.
5. Kiểm tra `AopUtils.isAopProxy(service)` xác nhận Spring đã tạo proxy, nhưng **chưa đủ** để chứng minh đúng semantics trên mọi method. Cần kiểm thử advice chạy đúng một lần, thứ tự lời gọi và nhánh ném exception.

Hai import `AnnotationAwareAspectJAutoProxyCreator` và `Advised` chỉ có ích khi đọc hạ tầng/kiểm tra advisor; đoạn ví dụ không cần trực tiếp tham chiếu chúng. Khi phát triển ứng dụng, có thể bỏ hai import đó để code gọn hơn.

Đối với yêu cầu chỉ cần logging/metrics tại method, một `@Aspect` với expression pointcut cũng thường ngắn hơn ví dụ này. Dùng `Advisor` khi cần đóng gói chính sách bằng Spring AOP SPI hay ghép pointcut động từ cấu hình ứng dụng. Cả hai cách đều không yêu cầu override auto-proxy creator.

## 6. Khi nào mới cân nhắc đi sâu vào nội bộ?

Chỉ cân nhắc khi đã chứng minh API công khai không đáp ứng yêu cầu, chẳng hạn nghiên cứu một framework tùy biến cần thay chiến lược chọn `TargetSource` hoặc cơ chế tạo proxy cho một loại bean đặc biệt. Lúc đó:

1. **Khoanh vùng phiên bản:** ghi chính xác `spring-aop`, `spring-context`, `spring-core` và phiên bản Spring Boot (nếu dùng); tránh trộn major/minor không tương thích.
2. **Đọc Javadoc và source đúng version:** xác định constructor/hook đang override, mức truy cập, lifecycle callback, các điều kiện loại trừ bean và thứ tự gọi. Không dựa vào source branch `main` để suy luận bản đã phát hành.
3. **Giảm phạm vi can thiệp:** ưu tiên custom `Advisor` hoặc `TargetSource` theo API đã tài liệu hóa; nếu buộc subclass, gói code trong adapter nội bộ duy nhất, không để ứng dụng phụ thuộc khắp nơi.
4. **Tránh xung đột với auto-proxy creator có sẵn:** rà soát `@EnableAspectJAutoProxy`, `@EnableTransactionManagement`, `@EnableAsync`, Spring Boot auto configuration và các `BeanPostProcessor` khác.
5. **Có đường thoát:** chuẩn bị khả năng tắt custom extension mà không làm mất bean hay giao diện public của ứng dụng.

**Dấu hiệu không nên tiếp tục với API nội bộ:** phải sửa bean definition có tên reserved của Spring; phải dùng reflection để chạm field/cache của auto-proxy creator; phụ thuộc thứ tự bean registration ngẫu nhiên; cần thay trực tiếp một singleton processor đang phục vụ transaction và các aspect hiện tại.

## 7. Ma trận kiểm thử khi nâng Spring

| Tình huống | Điều cần quan sát |
| --- | --- |
| Bean khớp / không khớp pointcut | Chỉ bean được chọn nhận proxy; advice chỉ chạy đúng method |
| Bean có / không có interface | Proxy type đúng và bean lấy qua ApplicationContext theo type phù hợp |
| Có `@Aspect` cùng `Advisor` | Không mất advice, không chạy trùng; thứ tự được kiểm tra theo yêu cầu |
| Có transaction và async trên cùng bean | Hành vi vẫn đúng khi các processor/thiết lập proxy được phối hợp |
| Method `final`, `private` và self-invocation | Biết chính xác nhánh không thể được advice bởi Spring proxy |
| Vòng đời khởi tạo sớm hoặc circular dependency | Không lộ raw bean ngoài ý muốn, không tạo proxy lặp; không tự ý kết luận mọi vòng phụ thuộc đều được hỗ trợ |
| Exception từ target method | Advice xử lý/lan truyền exception đúng hợp đồng; không nuốt lỗi |
| Upgrade phiên bản | Build và chạy trên bản mới; so advisor chain, cấu hình proxy và output hành vi với bản cũ |

Ở runtime, có thể dùng `AopUtils.isAopProxy(bean)` và (khi proxy cho phép) kiểm tra `((Advised) bean).getAdvisors()`. Nên so sánh **loại và thứ tự advisor**, không chỉ số lượng, vì transaction/advice khác có thể được Spring thêm vào chuỗi. Logging của `org.springframework.aop` hữu ích khi chẩn đoán, nhưng định dạng log không phải API để viết assertion lâu dài.

Một phép kiểm thử tương thích nên bao gồm ít nhất bản Spring đang triển khai và bản đích nâng cấp, trên JDK và Spring Boot tương thích. Không suy từ việc unit test dùng `new ProxyFactory(target)` chạy thành công rằng auto-proxy trong ApplicationContext cũng sẽ an toàn: hai đường chạy khác nhau về bean lifecycle.

## 8. Sai lầm phổ biến và cách tránh

- **Sao chép code của Spring 5.x rồi kế thừa ở Spring 6/7:** chỉ biên dịch lại chưa kiểm chứng được lifecycle. Đối chiếu Javadoc/source từng bản và kiểm thử hành vi.
- **Dùng reflection sửa cache/proxy creator:** can thiệp trạng thái riêng của Spring, dễ bị thay đổi hoặc bị giới hạn truy cập khi dùng JPMS. Tìm lại điểm mở rộng `Advisor`/`Pointcut`.
- **Tự đăng ký auto-proxy creator thứ hai để thêm logging:** dễ ảnh hưởng toàn bộ proxy chain. Đăng ký `Advisor` hoặc `@Aspect` với cơ chế hiện hữu.
- **Cho rằng method được match là method chắc chắn bị advice:** `final`, `private`, self-invocation và kiểu proxy vẫn giới hạn đường đi qua proxy.
- **Mặc định thứ tự interceptor bằng thứ tự khai báo bean:** dùng cơ chế ordering được hỗ trợ và kiểm thử thứ tự thực tế; nhiều aspect/advisor có thể có mức ưu tiên như nhau.
- **Chỉ thử bản framework mà bỏ qua Spring Boot:** các default cấu hình của Boot và auto configuration cũng có thể quyết định cách tạo proxy; kiểm tra đúng tổ hợp dependency.
- **Dùng tính năng Spring 7 trong project Spring 6:** kiểm tra availability của annotation/API theo version; ví dụ `@Proxyable` không phải lựa chọn có sẵn ở 6.x.

## 9. Tiêu chí chọn giải pháp

Có thể quyết định nhanh theo câu hỏi:

- Muốn gắn logic cross-cutting lên lời gọi method theo expression? **`@Aspect` và `@Around`/`@Before`**.
- Muốn điều kiện match hoặc interceptor được cấu hình bằng Java/Spring bean? **`Pointcut` + `Advisor` + `MethodInterceptor`**.
- Muốn tạo proxy cho một object riêng ngoài auto-proxy của container? **`ProxyFactory` hoặc `AspectJProxyFactory`**, tự chịu trách nhiệm xuất bản đúng object proxy.
- Muốn đổi cơ chế chọn và bao bọc mọi bean của container? Trước tiên chứng minh nhu cầu; nếu vẫn phải kế thừa **`AbstractAutoProxyCreator`**, xem đó là extension phụ thuộc version, phải có adapter và compatibility tests, không coi là cấu hình ứng dụng thông thường.

**Kết luận:** Ranh giới an toàn trong Spring AOP nằm ở API mô tả *điều cần advice* (`@Aspect`, `Pointcut`, `Advisor`, `Advice`) và API tạo proxy được tài liệu hóa. Các lớp điều phối auto-proxy và hook vòng đời container có thể nhìn thấy trong Javadoc nhưng phụ thuộc sâu vào cách Spring triển khai ở từng version. Dùng chúng cần khóa phiên bản và chứng minh tương thích bằng test; chỉ cần thêm một chính sách Spring AOP thì thường không nên sửa hạ tầng auto-proxy.

## Tài liệu đối chiếu theo phiên bản

- [Spring AOP API – Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop-api.html)
- [Using the auto-proxy facility – Spring Framework Reference](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html)
- [Proxying Mechanisms – Spring Framework 6.2](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Proxying Mechanisms – Spring Framework 7.0](https://docs.spring.io/spring-framework/reference/7.0/core/aop/proxying.html)
- [AnnotationAwareAspectJAutoProxyCreator – Javadoc Spring 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html)
- [AopConfigUtils – Javadoc Spring 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/config/AopConfigUtils.html)

Khi nâng phiên bản, đọc lại trang tương ứng với chính minor/patch được dự án sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
