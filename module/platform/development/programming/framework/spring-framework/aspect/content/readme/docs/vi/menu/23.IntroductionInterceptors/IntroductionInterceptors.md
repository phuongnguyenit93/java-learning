<a id="back-to-top"></a>

# Introduction

## Menu
- [Thêm interface cho proxy bằng introduction trong Spring AOP](#introduction-interceptors-001)
- [@DeclareParents và lớp triển khai mặc định](#introduction-interceptors-002)
- [Giới hạn phạm vi kiểu được bổ sung interface](#introduction-interceptors-003)
- [Truy cập interface mới thông qua proxy](#introduction-interceptors-004)
- [IntroductionInterceptor và IntroductionAdvisor](#introduction-interceptors-005)
- [DelegatingIntroductionInterceptor trong API Spring](#introduction-interceptors-006)
- [Trạng thái của phần triển khai bổ sung và phạm vi chia sẻ](#introduction-interceptors-007)
- [Khác biệt giữa interface trên proxy và lớp đích ban đầu](#introduction-interceptors-008)
- [Kiểm thử xung đột interface, kiểu ép và phạm vi áp dụng](#introduction-interceptors-009)

## <a id="introduction-interceptors-001">Thêm interface cho proxy bằng introduction trong Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm interface cho proxy bằng introduction trong Spring AOP

## 1. Introduction giải quyết vấn đề gì?

Trong Spring AOP, **introduction** (còn gọi là *inter-type declaration*) cho phép một **Spring AOP proxy** công bố rằng nó triển khai thêm một interface mà lớp target ban đầu chưa hề implements. Spring đồng thời gắn một implementation để xử lý các lời gọi tới interface mới đó.

Ví dụ, lớp `OrderService` chỉ có phương thức nghiệp vụ `submit(...)`. Ta muốn các bean `OrderService` do Spring quản lý có thêm khả năng `UsageTracked` với hai method `incrementUseCount()` và `getUseCount()`, mà không chỉnh sửa mã của `OrderService`. Một Spring aspect khai báo introduction có thể làm được điều này.

~~~text
Java class ban đầu: OrderService
    └── submit(orderId)

Spring AOP introduction qua @DeclareParents
    ├── Chọn bean có target type khớp type pattern
    ├── Giới thiệu thêm interface UsageTracked
    └── Ủy quyền method UsageTracked cho UsageTrackedImpl

Object trả ra từ Spring ApplicationContext:
    ├── hành vi OrderService.submit(...)
    └── interface UsageTracked
          ├── incrementUseCount()
          └── getUseCount()
~~~

Điểm cần nhớ: **lớp `OrderService` không được sửa để implements `UsageTracked`**. Phần mở rộng hiển thị ở **proxy runtime** mà Spring tạo và trả từ container. `new OrderService()` bên ngoài container vẫn không tự nhiên có interface mới.

Bài này đi sâu cách triển khai introduction trong **Spring Aspect / Spring AOP** qua `@DeclareParents`, cách truy cập interface được thêm, cách giới hạn phạm vi và các lỗi khi sử dụng proxy. Các khái niệm AOP chung chỉ được nhắc ở mức cần thiết.

## 2. Thành phần của một introduction

Một introduction kiểu annotation thường gồm bốn thành phần:

| Thành phần | Vai trò |
| --- | --- |
| Interface mới | Hợp đồng công khai mà proxy sẽ implements, ví dụ `UsageTracked` |
| Implementation mặc định | Lớp implements hợp đồng mới, ví dụ `UsageTrackedImpl` |
| `@Aspect` đăng ký trong Spring | Nơi khai báo introduction bằng `@DeclareParents` |
| Type pattern | Chọn target class nào nhận interface mở rộng |

Cú pháp quan trọng:

~~~java
@Aspect
public class UsageIntroductionAspect {
    @DeclareParents(
        value = "com.example.intro.OrderService+",
        defaultImpl = UsageTrackedImpl.class
    )
    public static UsageTracked mixin;
}
~~~

- `@DeclareParents` là annotation từ `org.aspectj.lang.annotation` mà Spring hiểu khi bật hỗ trợ `@AspectJ`.
- **Kiểu của field** (`UsageTracked`) chỉ định interface cần bổ sung. Field thường được khai báo `public static` như tài liệu Spring; nó là **khai báo metadata cho aspect**, không phải một dependency để tự gọi `UsageIntroductionAspect.mixin.incrementUseCount()`.
- `value` là **AspectJ type pattern** áp dụng cho kiểu target, không phải bean name. `com.example.intro.OrderService+` chọn `OrderService` và các subtype của nó; một pattern như `com.example.intro.service.*+` có thể chọn rộng hơn, nên cần kiểm soát phạm vi.
- `defaultImpl` chỉ định lớp cung cấp method cho interface bổ sung. Lớp này phải triển khai interface đó và nên có public no-arg constructor để Spring có thể tạo delegate thích hợp.

Spring dùng introduction advisor bên trong hạ tầng AOP để nối các phần này. Khi một bean thuộc type pattern cần được proxy, proxy trình bày interface mới bên cạnh các interface/hành vi vốn có.

## 3. Ví dụ hoàn chỉnh: thêm UsageTracked vào OrderService

Ví dụ dưới đây sử dụng Java 17+, Spring Framework 6.1.x và cấu hình `AnnotationConfigApplicationContext`. Ta tạo một Spring bean `OrderService`, khai báo introduction và chứng minh object từ Spring hỗ trợ `UsageTracked`, trong khi đối tượng tự tạo không hỗ trợ.

Cấu trúc file:

~~~text
build.gradle
src/main/java/com/example/intro/
    IntroductionDemo.java
    OrderService.java
    UsageTracked.java
    UsageTrackedImpl.java
    UsageIntroductionAspect.java
~~~

### 3.1. Interface cần bổ sung

**`src/main/java/com/example/intro/UsageTracked.java`**

~~~java
package com.example.intro;

public interface UsageTracked {
    void incrementUseCount();
    int getUseCount();
}
~~~

Đây là hợp đồng công khai cho code muốn quan sát hoặc cập nhật bộ đếm. Nó không bắt `OrderService` kế thừa hoặc implements trực tiếp.

### 3.2. Delegate implementation

**`src/main/java/com/example/intro/UsageTrackedImpl.java`**

~~~java
package com.example.intro;

public class UsageTrackedImpl implements UsageTracked {
    private int useCount;

    public UsageTrackedImpl() {
    }

    @Override
    public void incrementUseCount() {
        useCount++;
    }

    @Override
    public int getUseCount() {
        return useCount;
    }
}
~~~

Implementation có state `useCount` để minh họa. Đây chỉ là ví dụ trong luồng đơn; nếu một trạng thái introduction được thay đổi đồng thời giữa nhiều thread hoặc có scope/lifecycle phức tạp, cần thiết kế thread safety và quản lý trạng thái rõ ràng, không dùng `int++` như một bộ đếm concurrent.

### 3.3. Target bean không biết interface mở rộng

**`src/main/java/com/example/intro/OrderService.java`**

~~~java
package com.example.intro;

public class OrderService {
    public void submit(String orderId) {
        System.out.println("order submitted: " + orderId);
    }
}
~~~

`OrderService` không import hoặc implements `UsageTracked`. Method `submit` là method business bình thường mà Spring proxy vẫn phải chuyển tiếp được.

### 3.4. Spring Aspect khai báo introduction

**`src/main/java/com/example/intro/UsageIntroductionAspect.java`**

~~~java
package com.example.intro;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

@Aspect
public class UsageIntroductionAspect {

    @DeclareParents(
        value = "com.example.intro.OrderService+",
        defaultImpl = UsageTrackedImpl.class
    )
    public static UsageTracked mixin;
}
~~~

Ý nghĩa: mọi Spring AOP proxy cho target thuộc kiểu `OrderService+` có thể bổ sung interface `UsageTracked`; các lời gọi phương thức của interface đó được xử lý bằng implementation đã chỉ định.

Ở ví dụ này, aspect **không cần một method `@Before` hay `@Around`**. Bản thân introduction đã cung cấp khả năng thêm interface vào proxy. Ta sẽ bàn về việc kết hợp introduction với advice sau.

### 3.5. Bật Spring AOP và kiểm chứng proxy

**`src/main/java/com/example/intro/IntroductionDemo.java`**

~~~java
package com.example.intro;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class IntroductionDemo {

    @Bean
    OrderService orderService() {
        return new OrderService();
    }

    @Bean
    UsageIntroductionAspect usageIntroductionAspect() {
        return new UsageIntroductionAspect();
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(IntroductionDemo.class)) {

            OrderService service = context.getBean(OrderService.class);

            System.out.println("spring proxy=" + AopUtils.isAopProxy(service));
            System.out.println("proxy has UsageTracked=" + (service instanceof UsageTracked));

            UsageTracked tracked =
                    context.getBean("orderService", UsageTracked.class);
            tracked.incrementUseCount();
            tracked.incrementUseCount();
            System.out.println("use count=" + tracked.getUseCount());

            service.submit("A42");

            OrderService raw = new OrderService();
            System.out.println("raw has UsageTracked="
                    + UsageTracked.class.isInstance(raw));
        }
    }
}
~~~

Ở đây `@EnableAspectJAutoProxy(proxyTargetClass = true)` yêu cầu Spring dùng **class-based proxy (CGLIB)**. Nhờ vậy `context.getBean(OrderService.class)` vẫn làm việc với kiểu class cụ thể, đồng thời proxy có thể implements `UsageTracked`. Khi triển khai thực tế, lựa chọn JDK proxy hay CGLIB phụ thuộc cách các bean được khai báo và yêu cầu của ứng dụng; không cần ép CGLIB nếu chỉ dùng interface và thiết kế đã phù hợp với JDK proxy.

Lưu ý: `@Aspect` tự nó không nhất thiết kích hoạt component scanning. Ví dụ chủ động đăng ký `UsageIntroductionAspect` bằng `@Bean`; nếu sử dụng component scanning, cần đăng ký nó bằng một stereotype thích hợp như `@Component` và bật scan tương ứng.

### 3.6. Dependency và cách chạy

Một file **`build.gradle`** độc lập tối giản:

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
    mainClass = 'com.example.intro.IntroductionDemo'
}
~~~

`spring-context` lấy thêm các module Spring liên quan, trong đó có `spring-aop`, qua dependency transitively. `aspectjweaver` cung cấp các annotation và hỗ trợ diễn giải biểu thức AspectJ dùng trong `@AspectJ`. Spring AOP vẫn dùng **Spring runtime proxy**; ví dụ này không yêu cầu biên dịch bằng `ajc` hoặc load-time weaving.

Chạy từ thư mục có Gradle wrapper:

~~~bash
./gradlew run
~~~

Trên Windows:

~~~powershell
.\gradlew.bat run
~~~

**Các dòng do chương trình in ra** (không tính log Gradle/Spring):

~~~text
spring proxy=true
proxy has UsageTracked=true
use count=2
order submitted: A42
raw has UsageTracked=false
~~~

Giải thích:
1. Bean `orderService` khớp type pattern của introduction, nên Spring tạo AOP proxy cho nó.
2. Proxy công bố thêm `UsageTracked`; kiểm tra `instanceof` trên object Spring trả về thành công.
3. `context.getBean("orderService", UsageTracked.class)` lấy **cùng Spring bean** qua interface mới và gọi các method delegate.
4. Method `submit` vẫn đến `OrderService` ban đầu.
5. `new OrderService()` không đi qua Spring, vì vậy không có interface `UsageTracked`.

Không nên ép kiểu `(UsageTracked) new OrderService()`: đối tượng raw không triển khai interface này và phép cast sẽ thất bại.

## 4. Tại sao this(interface) hữu ích với introduction?

Spring AOP cho phép advice dùng designator `this(...)` để ràng buộc **proxy đang nhận lời gọi**. Đây là điểm quan trọng khi interface được thêm bởi introduction: proxy implements `UsageTracked`, còn target `OrderService` gốc thì không.

Để **tự động tăng bộ đếm khi `submit(...)` chạy qua proxy**, có thể thêm method sau vào `UsageIntroductionAspect` (đồng thời import `org.aspectj.lang.annotation.Before`):

~~~java
@Before(
    "execution(* com.example.intro.OrderService.submit(..)) && this(tracked)"
)
public void countSubmit(UsageTracked tracked) {
    tracked.incrementUseCount();
}
~~~

Đây là **đoạn bổ sung cho class aspect ở mục 3.4**, không phải file Java chạy độc lập. Sau khi thêm vào và chạy lại demo trên, hai lời gọi tăng thủ công vẫn giữ nguyên, nhưng `service.submit("A42")` sẽ làm tăng thêm một lượt; nếu in `tracked.getUseCount()` **sau** dòng `service.submit(...)`, giá trị tương ứng là `3`.

Phân biệt chính xác:
- `this(tracked)` kiểm tra/bind **Spring AOP proxy** và có thể thấy interface `UsageTracked` được introduction bổ sung.
- `target(tracked)` kiểm tra/bind **target object**; ở ví dụ, target `OrderService` không implements `UsageTracked` nên không phù hợp cho mục đích trên.
- Lời gọi `new OrderService().submit(...)`, hoặc lời gọi nội bộ tránh proxy, không kích hoạt advice Spring AOP của ví dụ.

Không nên dùng một advice quá rộng như `execution(* *(..))` khi advice lại gọi `tracked.incrementUseCount()`: nếu pointcut bao gồm luôn method introduction thì có thể tạo logic đếm không mong muốn hoặc vòng gọi. Giới hạn rõ method nghiệp vụ cần theo dõi.

## 5. Type pattern chọn bean nào nhận interface?

Thuộc tính `value` trong `@DeclareParents` mô tả **kiểu target** qua cú pháp AspectJ, không phải tên bean, URL hay package scan của Spring.

| Pattern ví dụ | Phạm vi dự kiến |
| --- | --- |
| `com.example.intro.OrderService+` | `OrderService` và các subtype |
| `com.example.intro.service.*+` | Các type khớp trong package `service` và subtype của chúng |
| `com.example.intro.api.OrderOperations+` | Các class implement `OrderOperations` và các subtype phù hợp |

Các pattern trên chỉ có ý nghĩa nếu đúng fully qualified name trong dự án. Trong ví dụ đã xây dựng, pattern **chỉ chọn `OrderService+`**. Nó không làm cho mọi bean trong context tự động implements `UsageTracked`.

Nên bắt đầu với phạm vi hẹp, kiểm tra proxy của từng bean mục tiêu và chỉ mở rộng khi rõ ràng rằng interface bổ sung có ý nghĩa đối với tất cả target được chọn. Pattern quá rộng có thể khiến một loạt bean bất ngờ mang thêm interface hoặc bị tạo proxy dù không có advice thông thường.

Nếu bạn áp dụng introduction cho **một interface nghiệp vụ làm type pattern**, phải bảo đảm các implementation thực sự là Spring bean đủ điều kiện proxy. Không phải cứ một Java class xuất hiện trên classpath là Spring sẽ tự bọc instance của class đó.

## 6. Introduction liên hệ với Spring AOP proxy thế nào?

### 6.1. Đây là một introduction advisor

Khai báo `@DeclareParents` được Spring chuyển thành cấu hình introduction advisor (chẳng hạn hạ tầng `DeclareParentsAdvisor`). Introduction advisor thông báo **interface bổ sung** và cung cấp logic delegate.

Khi Spring tạo proxy cho bean khớp type pattern, interface được giới thiệu trở thành một phần hợp đồng runtime của proxy. Đây không phải cách trực tiếp thêm declaration `implements` vào file `OrderService.java`, và không thay đổi superclass/bytecode của đối tượng do `new` tạo ra bên ngoài container.

### 6.2. JDK proxy và CGLIB

- **JDK dynamic proxy** implement một tập hợp interface (bao gồm các interface gốc thích hợp và interface được introduction). Consumer nên inject hoặc truy cập qua interface; không giả định object là instance của implementation class.
- **CGLIB class-based proxy** kế thừa class target và có thể bổ sung interface. Ví dụ chọn `proxyTargetClass = true` để vẫn truy cập bean theo `OrderService.class`.

Với class-based proxy, lớp target không được là `final`; các giới hạn proxy của CGLIB vẫn áp dụng cho khả năng intercept method. Tương tự, các lời gọi **bỏ qua proxy** không nhận hành vi giới thiệu thông qua object proxy.

Trong code, phương án rõ ràng nhất để tiêu thụ introduction là:

~~~java
UsageTracked tracked =
        context.getBean("orderService", UsageTracked.class);
~~~

Cách này thể hiện đúng mục tiêu: caller cần **interface được bổ sung**, chứ không cần biết implementation delegate hoặc kiểu class proxy.

### 6.3. Không nhầm với interface Java có default method

Một interface Java có `default method` yêu cầu lớp triển khai interface đó tại compile time (trực tiếp hoặc gián tiếp). Introduction của Spring AOP làm cho **object proxy** có thêm interface và implementation khi ứng dụng chạy. Đây là hai cơ chế khác nhau.

Nếu cần mở rộng giao diện miền nghiệp vụ cho **mọi Java instance** kể cả object tạo trực tiếp, hãy cân nhắc thiết kế lại interface/delegation ở mã Java thông thường. Introduction phù hợp khi khả năng mở rộng thuộc nhóm hành vi cắt ngang dành cho **Spring-managed proxy**.

## 7. XML declaration: lựa chọn thay thế trong Spring

Nếu ứng dụng Spring sử dụng cấu hình XML AOP, cơ chế tương đương là phần tử `<aop:declare-parents>`:

~~~xml
<aop:config>
    <aop:aspect id="usageAspect" ref="usageIntroductionBean">
        <aop:declare-parents
            types-matching="com.example.intro.OrderService+"
            implement-interface="com.example.intro.UsageTracked"
            default-impl="com.example.intro.UsageTrackedImpl"/>
    </aop:aspect>
</aop:config>
~~~

Đây là **trích đoạn bên trong một cấu hình XML có khai báo namespace `aop` và bean `usageIntroductionBean` phù hợp**, không phải file XML hoàn chỉnh để chạy riêng. Ba thuộc tính tương ứng với type pattern, interface bổ sung và lớp delegate. Với dự án dùng `@EnableAspectJAutoProxy` và annotation, `@DeclareParents` thường dễ bảo trì hơn vì khai báo gần class aspect.

## 8. Những lỗi phổ biến và cách xử lý

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `instanceof UsageTracked` luôn `false` | Đang sử dụng object do `new` tạo; không đi qua Spring proxy | Lấy bean từ `ApplicationContext` và kiểm tra bằng `AopUtils.isAopProxy` |
| Spring bean có nhưng không được giới thiệu interface | Aspect chưa đăng ký; chưa bật auto-proxy; type pattern không match | Kiểm tra bean `UsageIntroductionAspect`, `@EnableAspectJAutoProxy` và tên package/class trong `value` |
| `ClassCastException` khi cast proxy về implementation class | Proxy được tạo bằng JDK dynamic proxy | Truy cập qua interface nghiệp vụ/interface introduction, hoặc cân nhắc class-based proxy khi thật cần |
| Không tạo được proxy cho class target | Class `final` hoặc môi trường gây hạn chế CGLIB | Bỏ `final` nếu thiết kế cho phép, hoặc điều chỉnh kiến trúc/interface và kiểu proxy |
| Lỗi tạo delegate hoặc thiếu method | `defaultImpl` không implements interface, constructor không khả dụng | Kiểm tra class concrete, public constructor và chữ ký method |
| Counter/logic của introduction sai khi gọi từ nhiều thread | Delegate chứa mutable state nhưng không đồng bộ | Dùng giải pháp thread-safe, làm rõ lifecycle và scope; không giả định `int++` an toàn |
| Advice không chạy khi gọi method từ target nội bộ | Lời gọi `this.method()` bypass proxy | Gọi qua Spring proxy ở đường đi nghiệp vụ phù hợp |
| `this(usageTracked)` hoạt động còn `target(usageTracked)` không | Interface chỉ tồn tại ở proxy, không ở target raw | Dùng `this(...)` khi bind interface được introduction bổ sung |

**Quy trình debug nhanh:**

1. Xác nhận object đang dùng xuất phát từ Spring context, không phải kết quả của `new`.
2. Log `AopUtils.isAopProxy(bean)`, `bean.getClass().getName()`, `UsageTracked.class.isInstance(bean)`; không suy luận chỉ từ tên class runtime.
3. Kiểm tra bean aspect đã đăng ký và auto-proxy đã bật trong **đúng `ApplicationContext`**.
4. Đối chiếu `@DeclareParents.value` với **fully qualified class name** của target.
5. Nếu object là `org.springframework.aop.framework.Advised`, kiểm tra danh sách `getAdvisors()` để xem introduction advisor đã được gắn hay chưa.
6. Kiểm tra các bean được tạo quá sớm trong quá trình đăng ký `BeanPostProcessor`: các bean đó có thể không được tất cả auto-proxy processor xử lý.
7. Nếu interface tồn tại mà lời gọi method trả sai kết quả, rà soát implementation `defaultImpl`, state và thread safety.

## 9. Khi nào nên dùng introduction?

Dùng introduction khi muốn gắn **một hợp đồng phụ trợ thống nhất** cho nhiều Spring-managed bean mà không sửa class nghiệp vụ, ví dụ cung cấp interface chẩn đoán, thống kê sử dụng hoặc đánh dấu trạng thái cho các proxy thuộc một nhóm target rõ ràng.

Cân nhắc kỹ nếu:
- API mới là **hành vi cốt lõi** của domain: thường phù hợp hơn khi khai báo trực tiếp qua interface/service.
- Caller dự kiến thường xuyên tạo object bằng `new`: introduction không xuất hiện trên object đó.
- Tính năng đòi hỏi kiểm soát state chia sẻ, thread safety, serialization hoặc lifecycle phức tạp: delegate cần được thiết kế và kiểm chứng riêng.
- Dự án cần truy cập class cụ thể nhưng lại đang dùng JDK proxy: ưu tiên thiết kế caller theo interface, chỉ cấu hình CGLIB khi có lý do hợp lý.
- Type pattern quá rộng hoặc áp vào bean hạ tầng của Spring: giới hạn phạm vi và thử nghiệm từng bean trước khi mở rộng.

Introduction mở rộng **kiểu quan sát được của Spring AOP proxy**. Nó không cho phép tùy ý chèn method vào mọi instance Java, cũng không thay thế bytecode weaving của AspectJ.

## 10. Câu hỏi tự kiểm tra

**1. `@DeclareParents` xác định interface được thêm bằng thuộc tính `defaultImpl` hay bằng field type?**

Bằng **kiểu khai báo của field**. `defaultImpl` chỉ định implementation cung cấp các method của interface đó.

**2. Tại sao `new OrderService() instanceof UsageTracked` không đúng như proxy từ context?**

Vì `new` tạo target Java nguyên bản. Introduction chỉ được đưa vào object proxy mà Spring AOP tạo cho bean phù hợp.

**3. Khi dùng introduction, nên dùng `this(usageTracked)` hay `target(usageTracked)` để advice bắt interface mới?**

Dùng `this(usageTracked)` vì đây là kiểu của **proxy**. Target raw chưa implements interface mới.

**4. Nếu thay `value` bằng tên bean `"orderService"`, Spring có match được không?**

Không theo ý nghĩa mong muốn. `value` là **AspectJ type pattern**, không phải Spring bean name.

**5. Chỉ viết class có `@Aspect` mà không đăng ký bean và không bật AOP đã đủ chưa?**

Chưa. Spring phải biết bean aspect và có auto-proxy creator hoạt động trong context tương ứng.

**6. Trong ví dụ, vì sao `context.getBean("orderService", UsageTracked.class)` có thể hoạt động?**

Vì Spring trả một bean mà runtime proxy đã công bố interface `UsageTracked` sau introduction. Type check dựa trên đối tượng Spring quản lý được trả ra, không phải việc `OrderService.java` có khai báo implements interface đó.

## 11. Tài liệu Spring chính thức

- [Introductions — Spring AOP @AspectJ](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html): `@DeclareParents`, `value`, `defaultImpl` và truy cập interface bằng `ApplicationContext`.
- [Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html): đăng ký aspect như Spring bean.
- [AOP Proxies / Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK dynamic proxy, CGLIB và giới hạn proxy.
- [Schema-based AOP — Introductions](https://docs.spring.io/spring-framework/reference/core/aop/schema.html): cấu hình thay thế bằng `<aop:declare-parents>`.
- [DeclareParentsAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/aspectj/DeclareParentsAdvisor.html): introduction advisor ở mức API Spring.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-002">@DeclareParents và lớp triển khai mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# @DeclareParents và lớp triển khai mặc định trong Spring AOP

## 1. Khi nào chọn @DeclareParents?

`@DeclareParents` là cách khai báo **introduction bằng annotation** trong Spring AOP: proxy của các bean có target type phù hợp sẽ công bố một interface mới và chuyển các lời gọi của interface đó tới `defaultImpl`. Ví dụ, `OrderServiceImpl` vẫn chỉ implements `OrderService`, nhưng proxy Spring có thể đồng thời implements `UsageTracked` để phục vụ `incrementUseCount()` và `getUseCount()`.

Bài này tập trung vào **field `@DeclareParents`, `value` type pattern và `defaultImpl`**. Nếu chỉ cần chạy logic trước/sau method có sẵn, dùng advice như `@Before` hoặc `@Around`; introduction cần thiết khi client thật sự phải cast hoặc inject proxy qua **interface được bổ sung**. Lớp target và object do `new` tạo vẫn không được thay đổi interface/bytecode. Các điều kiện cấu hình và vòng đời delegate được kiểm chứng ở các mục tiếp theo.

## 2. Cú pháp và ý nghĩa từng phần

Khai báo tiêu biểu trong một Spring aspect:

```java
@Aspect
public class UsageTrackingAspect {

    @DeclareParents(
        value = "demo.service.OrderServiceImpl",
        defaultImpl = DefaultUsageTracked.class
    )
    public static UsageTracked usageTracked;
}
```

| Thành phần | Vai trò trong Spring AOP |
| --- | --- |
| `@Aspect` | Cho Spring biết lớp mang các khai báo aspect theo phong cách @AspectJ |
| `@DeclareParents` | Yêu cầu tạo introduction advisor cho các target khớp điều kiện |
| `value` | **AspectJ type pattern** dùng để chọn các lớp/interface target cần được giới thiệu interface mới; ví dụ chọn đúng `demo.service.OrderServiceImpl` |
| Kiểu của field `UsageTracked` | Xác định **interface được thêm vào proxy**; không phải tên field |
| `defaultImpl = DefaultUsageTracked.class` | Xác định class thực hiện các phương thức của interface được giới thiệu |
| `public static UsageTracked usageTracked;` | Trường khai báo dùng làm metadata cho Spring; không phải chỗ để ứng dụng tự gán/call một instance mới |

Các điều kiện thực dụng cho `defaultImpl`: class phải **thực hiện interface được khai báo trên field**, là kiểu concrete có thể tạo instance, và nên có **constructor không đối số có thể truy cập**. Đừng phụ thuộc vào việc Spring autowire trực tiếp các field/constructor của implementation mặc định như một `@Component` thông thường: instance introduction delegate do cơ chế Spring AOP quản lý. Nếu implementation cần quá nhiều dependency hoặc vòng đời IoC phức tạp, cân nhắc thay đổi thiết kế hoặc dùng một introduction advisor/delegate có cấu hình riêng.

`value` nhận **type pattern**, không phải Spring bean name. Cú pháp `demo.service.*+` thường dùng để match các kiểu phù hợp trong package `demo.service` và subtype theo pattern. Dấu `+` theo ngôn ngữ type pattern của AspectJ có ý nghĩa bao gồm subtype; không nên hiểu `+` là cú pháp tên package Java. Với một lớp mục tiêu duy nhất, dùng tên đầy đủ `demo.service.OrderServiceImpl` giúp dễ xác minh và tránh áp dụng ngoài ý muốn.

**Đừng khai báo field như một phương thức advice:** `@DeclareParents` đặt trên **field**; Spring suy ra interface từ **type của field**, không phải return type của method hoặc tên `usageTracked`. Trong triển khai thông thường, giữ field ở dạng `public static` và không tự khởi tạo field đó.

## 3. Điều kiện để introduction chạy

Một khai báo `@DeclareParents` nằm trong class có `@Aspect` chưa tự động thay đổi các object. Spring phải nhận diện aspect và tạo AOP proxy cho các bean được match.

Với cấu hình Spring Framework thuần:

1. Đặt aspect vào Spring container, bằng `@Component` + component scan hoặc đăng ký `@Bean`.
2. Bật `@EnableAspectJAutoProxy` ở cấu hình Spring (hoặc XML `<aop:aspectj-autoproxy/>`).
3. Có `spring-aop` và thư viện `org.aspectj:aspectjweaver` trên classpath; Spring dùng thư viện AspectJ để đọc/parse annotation và type pattern, nhưng ví dụ này **không cần biên dịch bằng ajc hay load-time weaving**.
4. Target là **Spring-managed bean**, class/type của target khớp pattern `value`, và lời gọi thực tế sử dụng **proxy bean** nhận từ context.
5. Interface mà field `@DeclareParents` khai báo có implementation tương thích từ `defaultImpl`.

Khi Spring auto-proxying áp dụng, bean có thể được lấy theo interface nghiệp vụ cũ rồi cast sang interface mới. Với JDK dynamic proxy, object đó thường **không cast được sang class implementation**; nên sử dụng hợp đồng interface nghiệp vụ và interface introduction để truy cập.

## 4. Ví dụ chạy được với Java 17, Spring 6.2

Ví dụ dưới đây giới thiệu `UsageTracked` cho `OrderServiceImpl`. Class xử lý đơn hàng **không implements `UsageTracked`**; Spring proxy nhận được cả hai interface. Một `DefaultUsageTracked` lưu bộ đếm riêng cho delegate của target.

### 4.1. Cấu trúc project

```text
spring-declare-parents-demo/
├── pom.xml
└── src/main/java/
    └── demo/
        ├── App.java
        ├── api/OrderService.java
        ├── service/OrderServiceImpl.java
        └── aop/
            ├── UsageTracked.java
            ├── DefaultUsageTracked.java
            └── UsageTrackingAspect.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>demo</groupId>
  <artifactId>spring-declare-parents-demo</artifactId>
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
      </plugin>
    </plugins>
  </build>
</project>
```

### 4.2. Interface và target nghiệp vụ gốc

`src/main/java/demo/api/OrderService.java`:

```java
package demo.api;

public interface OrderService {
    void placeOrder(String code);
}
```

`src/main/java/demo/service/OrderServiceImpl.java`:

```java
package demo.service;

import demo.api.OrderService;

public class OrderServiceImpl implements OrderService {
    @Override
    public void placeOrder(String code) {
        System.out.println("order=" + code);
    }
}
```

`OrderServiceImpl` chỉ implement `OrderService`. Hãy để nguyên trạng thái đó để nhìn rõ việc proxy có thêm interface không đến từ source code của lớp target.

### 4.3. Interface được giới thiệu và implementation mặc định

`src/main/java/demo/aop/UsageTracked.java`:

```java
package demo.aop;

public interface UsageTracked {
    void incrementUseCount();
    int getUseCount();
}
```

`src/main/java/demo/aop/DefaultUsageTracked.java`:

```java
package demo.aop;

public class DefaultUsageTracked implements UsageTracked {
    private int count;

    // Constructor không đối số public được Java cung cấp ngầm định.

    @Override
    public void incrementUseCount() {
        count++;
    }

    @Override
    public int getUseCount() {
        return count;
    }
}
```

`DefaultUsageTracked` đảm nhận việc triển khai những phương thức **không có trên target gốc**. Bộ đếm trong ví dụ thuộc delegate introduction; đây không phải field mới đã được thêm vào `OrderServiceImpl`.

### 4.4. Aspect khai báo @DeclareParents

`src/main/java/demo/aop/UsageTrackingAspect.java`:

```java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

@Aspect
public class UsageTrackingAspect {

    @DeclareParents(
        value = "demo.service.OrderServiceImpl",
        defaultImpl = DefaultUsageTracked.class
    )
    public static UsageTracked usageTracked;
}
```

Spring đọc field và tạo introduction advisor:

- Target type được chọn bởi `value`: `demo.service.OrderServiceImpl`.
- Interface thêm cho proxy được suy ra từ field `UsageTracked usageTracked`.
- Lời gọi tới hai method của `UsageTracked` được chuyển tới `DefaultUsageTracked`.
- Không cần viết `@Before`/`@Around` để chức năng giới thiệu interface hoạt động.

### 4.5. Bật Spring AOP và quan sát kết quả

`src/main/java/demo/App.java`:

```java
package demo;

import demo.api.OrderService;
import demo.aop.UsageTracked;
import demo.aop.UsageTrackingAspect;
import demo.service.OrderServiceImpl;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        UsageTrackingAspect usageTrackingAspect() {
            return new UsageTrackingAspect();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            OrderService service = context.getBean(OrderService.class);

            System.out.println("proxy has UsageTracked: "
                    + (service instanceof UsageTracked));
            System.out.println("class has UsageTracked: "
                    + UsageTracked.class.isAssignableFrom(OrderServiceImpl.class));

            service.placeOrder("A-101");

            UsageTracked tracked = (UsageTracked) service;
            tracked.incrementUseCount();
            tracked.incrementUseCount();

            System.out.println("count=" + tracked.getUseCount());
            System.out.println("same service bean: "
                    + (service == context.getBean(OrderService.class)));
        }
    }
}
```

Chạy tại thư mục project:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Các dòng do chương trình in ra theo thứ tự (không tính thông điệp riêng của Maven/SLF4J nếu môi trường có):

```text
proxy has UsageTracked: true
class has UsageTracked: false
order=A-101
count=2
same service bean: true
```

**Giải thích output:**

- `proxy has UsageTracked: true`: object do context trả về là AOP proxy, có interface bổ sung từ introduction.
- `class has UsageTracked: false`: `OrderServiceImpl.class` không khai báo hoặc được sửa để implement interface này; mô hình proxy không sửa bytecode target.
- `order=A-101`: lời gọi method thuộc `OrderService` tiếp tục được chuyển tới target nghiệp vụ.
- `count=2`: method thuộc `UsageTracked` được delegate tới `DefaultUsageTracked` có state riêng.
- `same service bean: true`: cấu hình `@Bean` mặc định có scope singleton; Spring trả lại cùng bean proxy trong context.

Ví dụ cung cấp đủ `pom.xml` và sáu file Java, không phải những đoạn mã rời phải đoán class đi kèm. Spring 6.x yêu cầu Java 17+. Đây là **Spring AOP** sử dụng annotation phong cách @AspectJ; việc có `aspectjweaver` trong dependency nhằm phục vụ đọc và phân tích biểu thức, không có bước weaving bằng AspectJ.

## 5. defaultImpl được tạo và gắn với đối tượng nào?

`defaultImpl` không phải một object singleton chia sẻ mặc định cho mọi bean match pattern. Với cách khai báo `@DeclareParents(defaultImpl=...)`, Spring tạo `DeclareParentsAdvisor` sử dụng cơ chế `DelegatePerTargetObjectIntroductionInterceptor`: **mỗi target object được hỗ trợ bởi một instance delegate riêng**, chứ không chia sẻ state của một delegate duy nhất cho toàn bộ target khác nhau.

Điều này có hệ quả:

- Nếu target là singleton bean, các lần gọi cùng target thường gặp cùng delegate/state trong suốt vòng đời được giữ lại của target.
- Nếu có **hai target object khác nhau** cùng match pattern, mỗi target có delegate riêng; đếm của đối tượng A không tự cộng sang đối tượng B.
- Nếu target source tạo target mới theo invocation, sự tồn tại của state delegate có thể ngắn; phải xét kỹ vòng đời target/proxy trước khi kỳ vọng bộ đếm tồn tại lâu dài.
- `defaultImpl` không biến field của target thành field delegate, và việc cast proxy sang interface mới không làm cast target gốc hợp lệ.

Đây là khác biệt đáng lưu ý so với `DelegatingIntroductionInterceptor` có thể dùng cùng một delegate đã cung cấp cho nhiều target. Lớp `DeclareParentsAdvisor` dùng introduction advisor và implementation mặc định để giữ semantics tương ứng.

Đừng lạm dụng các class delegate có biến mutable dùng chung cho nhiều thread. Đối với singleton target, hai thread có thể gọi `incrementUseCount()` đồng thời trên cùng delegate; `count++` trong ví dụ **không thread-safe**. Trong ứng dụng thực tế, dùng `AtomicInteger` hoặc cơ chế đồng bộ thích hợp nếu muốn đếm chính xác trong môi trường concurrency.

## 6. Kết hợp introduction với advice của Spring

`@DeclareParents` vừa giới thiệu interface, vừa cho phép advice Spring khác nhìn proxy dưới interface đó. Ví dụ, có thể gắn thêm method bên trong chính `UsageTrackingAspect`:

```java
@Before("execution(* demo.api.OrderService.placeOrder(..)) && this(tracked)")
public void recordUse(UsageTracked tracked) {
    tracked.incrementUseCount();
}
```

Đây là **đoạn bổ sung minh họa**, không thuộc ví dụ Java tự chạy ở phần 4. Cần import `org.aspectj.lang.annotation.Before`. Với `this(tracked)`, Spring AOP bind **proxy** mà caller tương tác — proxy có interface vừa được introduced. Nếu advice dùng `target(tracked)`, nó sẽ cố bind **đối tượng target gốc**, trong khi `OrderServiceImpl` chưa implements `UsageTracked`, nên không đạt ý định. Kết hợp này hữu ích nếu muốn tự động tăng bộ đếm khi nghiệp vụ chạy.

Nên phân biệt rõ hai loại lời gọi:

1. Khi gọi `service.placeOrder(...)`, proxy chuyển nghiệp vụ tới `OrderServiceImpl`, và các advice phù hợp có thể chạy.
2. Khi gọi `((UsageTracked) service).incrementUseCount()`, proxy chuyển method mới tới instance `DefaultUsageTracked`, không cần `OrderServiceImpl` biết interface này.

Nếu chỉ muốn quan sát hoặc logging, một advice thông thường có thể đơn giản hơn việc public thêm một interface lên proxy. Dùng introduction khi **client thực sự cần hợp đồng interface bổ sung**.

## 7. Khác biệt với các cách thêm hành vi khác trong Spring

| Cách tiếp cận | Kết quả quan sát được | Có thêm interface cho proxy? |
| --- | --- | --- |
| `@Before` / `@AfterReturning` / `@Around` | Chạy logic gắn với method invocation | Không |
| `@DeclareParents` + `defaultImpl` | Proxy implement thêm một interface, method mới do delegate thực thi | Có |
| Sửa class target thành `implements UsageTracked` | Chính class target implement interface từ source code | Không cần introduction |
| Dùng decorator thủ công | Viết class wrapper implement các interface mong muốn | Có thể, tùy class được viết |
| XML Spring AOP `<aop:declare-parents>` | Introduction cùng ý tưởng thông qua XML | Có |

Với hệ thống dùng cấu hình XML, Spring AOP hỗ trợ khái niệm tương đương:

```xml
<aop:aspect id="usageAspect" ref="usageTrackingAspect">
    <aop:declare-parents
        types-matching="demo.service.OrderServiceImpl"
        implement-interface="demo.aop.UsageTracked"
        default-impl="demo.aop.DefaultUsageTracked"/>
</aop:aspect>
```

Đây là **mảnh cấu hình bên trong `<aop:config>`** và cần namespace/schema AOP, bean aspect cùng các bean ứng dụng tương ứng; nó không phải tài liệu XML hoàn chỉnh độc lập. Phần chính của bài vẫn là API annotation `@DeclareParents` trong Spring.

## 8. Giới hạn của Spring AOP introduction

### Chỉ áp dụng cho bean qua proxy

`new OrderServiceImpl()` và gọi trực tiếp object đó không tự có `UsageTracked`. Một object chưa từng được Spring proxy cũng không nhận được introduction, dù class name khớp type pattern. Nên lấy object qua Spring context/DI và dùng chính bean proxy.

### JDK proxy và kiểu trả về khi inject

Với `@EnableAspectJAutoProxy` mặc định, Spring thường sử dụng JDK dynamic proxy khi target có interface. Khi đó proxy implements `OrderService` và `UsageTracked`, nhưng **không phải một `OrderServiceImpl` instance để cast trực tiếp**. Inject theo interface, ví dụ `OrderService` hoặc `UsageTracked` nếu sự lựa chọn bean không mơ hồ. Không gắn assumption rằng mọi proxy đều assignable sang concrete implementation.

Nếu đổi sang `proxyTargetClass = true`, Spring có thể dùng proxy dựa trên class (CGLIB); vẫn cần để ý giới hạn của các method/class `final`, `private` và việc tự gọi nội bộ không đi qua proxy. Introduction không biến Spring AOP thành AspectJ compile-time weaving.

### Type pattern phải đúng target

`value` phải khớp **kiểu target thực tế**, không phải chỉ đúng tên bean hoặc tên package theo suy đoán. Nếu không match, Spring không thêm interface và cast sẽ ném `ClassCastException`. Trong ví dụ, kiểm tra `service instanceof UsageTracked` trước khi cast là cách xác thực trực quan.

### Implementation mặc định không tương thích

Nếu `defaultImpl` không implements interface của annotated field, cấu hình có thể bị từ chối hoặc invocation thất bại. Nếu class delegate không có constructor phù hợp, không thể khởi tạo delegate cho introduced method. Khi xem stack trace, chú ý lỗi tại thời điểm tạo proxy/delegate, khác với lỗi nghiệp vụ từ target.

### Xung đột method và quan hệ interface

Không coi `@DeclareParents` là công cụ thay đổi hoặc ghi đè tự do các method Java của target. Nếu interface mới có signature trùng method/interface đang có, việc dispatch và thứ tự advisor có thể khó hiểu. Ưu tiên hợp đồng introduction khác biệt, dễ phân biệt. Đặc biệt, field `public static UsageTracked usageTracked` **không phải bean hay nơi giữ state**, không nên truy cập trực tiếp như một biến dùng cho xử lý nghiệp vụ.

### Vòng đời, concurrency và bảo mật

Delegate có thể giữ state; phải thiết kế đồng bộ nếu nhiều thread gọi cùng proxy. Việc giới thiệu một interface quản trị làm các method đó trở nên gọi được qua proxy ở phía client có quyền tham chiếu đến bean; không tự động mang lại kiểm tra phân quyền hay giới hạn truy cập. Nếu thêm thao tác nhạy cảm, áp dụng bảo vệ phù hợp tại boundary đó.

## 9. Debug khi @DeclareParents chưa hoạt động

Đọc lần lượt theo thứ tự:

1. **Có aspect bean không?** Lớp `@Aspect` phải được Spring quản lý. Chỉ viết annotation mà quên đăng ký bean là lỗi thường gặp.
2. **Đã bật AOP chưa?** Xác nhận `@EnableAspectJAutoProxy` (hoặc cấu hình tương đương) hoạt động trong đúng `ApplicationContext` chứa bean đích.
3. **Classpath đủ chưa?** Xác nhận dependency `spring-aop` và `aspectjweaver`. Thiếu AspectJ runtime/parser thường làm cấu hình @AspectJ thất bại.
4. **Pattern có match không?** So tên package và FQCN của class target với `@DeclareParents.value`; đừng nhầm bean ID với class name.
5. **Interface và defaultImpl khớp không?** Field phải có kiểu interface; `defaultImpl` phải thực hiện toàn bộ contract và tạo instance được.
6. **Đang sử dụng object nào?** In `bean.getClass()` và kiểm tra `bean instanceof UsageTracked`. Nếu bạn `new` target bằng tay, đó không phải Spring AOP proxy.
7. **Cast đúng interface không?** Cast proxy sang `UsageTracked`; không cast sang concrete target implementation trong trường hợp JDK proxy.
8. **Có nhiều bean cùng interface?** `context.getBean(UsageTracked.class)` có thể không xác định duy nhất nếu nhiều proxy cùng introduced interface. Ưu tiên tên bean hoặc qualifier cụ thể.
9. **State có đúng không?** Nếu bộ đếm không như mong đợi, kiểm tra bean scope/target source và các lời gọi trên các proxy/target khác nhau, sau đó kiểm tra tính an toàn thread của implementation.

Ví dụ chẩn đoán chỉ cần quan sát, không cần sửa cấu trúc bean:

```java
Object bean = context.getBean("orderService");
System.out.println("proxy type=" + bean.getClass().getName());
System.out.println("introduced=" + (bean instanceof UsageTracked));
```

Đây là **snippet chèn trong `main` hoặc phần debug**, không phải chương trình độc lập. Tên class proxy có thể khác nhau giữa JDK và CGLIB, nên kiểm tra `instanceof` ổn định hơn dựa vào tên class sinh ra.

## 10. Câu hỏi tự kiểm tra

**1. `@DeclareParents` thay đổi class `OrderServiceImpl` trên đĩa thành `implements UsageTracked` phải không?**  
Không. Spring AOP cung cấp **proxy** implements thêm interface; class bytecode gốc vẫn như cũ.

**2. Interface được thêm lấy từ `value` hay từ kiểu của field?**  
Interface mới là kiểu của field annotate (`UsageTracked`). `value` chọn **kiểu target** được áp dụng introduction.

**3. Lớp `defaultImpl` dùng vào việc gì?**  
Cung cấp implementation cho các phương thức của interface được introduced khi client gọi chúng qua proxy. Nó cần tương thích interface và có thể khởi tạo.

**4. Có cần chạy AspectJ compiler `ajc` để ví dụ hoạt động?**  
Không. Spring dùng cơ chế AOP proxy runtime; dependency `aspectjweaver` hỗ trợ Spring @AspectJ expression handling.

**5. Vì sao `target(tracked)` không thay thế được `this(tracked)` trong lời gọi advice kết hợp introduction?**  
`this` tham chiếu đến **proxy**, vốn implements interface mới; `target` tham chiếu đến **target gốc**, chưa implements interface này.

**6. Với hai Spring bean target khác nhau cùng match pattern, bộ đếm delegate có bắt buộc chung không?**  
Không. `DeclareParentsAdvisor` sử dụng per-target delegate; không được giả định mọi target chia sẻ một instance mutable mặc định.

## Tài liệu chính thức để tra cứu

- Spring Framework Reference — Introductions: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html
- Spring Framework Reference — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework Javadoc — DeclareParentsAdvisor: https://docs.spring.io/spring-framework/docs/6.2.2/javadoc-api/org/springframework/aop/aspectj/DeclareParentsAdvisor.html
- Spring Framework Javadoc — DelegatePerTargetObjectIntroductionInterceptor: https://docs.spring.io/spring-framework/docs/6.2.0-M5/javadoc-api/org/springframework/aop/support/DelegatePerTargetObjectIntroductionInterceptor.html
- Spring Framework Reference — Schema-based introductions: https://docs.spring.io/spring-framework/reference/core/aop/schema.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-003">Giới hạn phạm vi kiểu được bổ sung interface</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn phạm vi kiểu được bổ sung interface trong Spring AOP Introduction

## 1. Bài toán: chỉ một số Spring bean cần interface mới

Trong Spring AOP, **introduction** (hay *interface introduction*) cho phép một proxy của Spring bean thể hiện rằng nó triển khai **một interface bổ sung** mà class target nguyên bản không khai báo. Spring chuyển những lời gọi đến method của interface mới tới một implementation/delegate thích hợp.

Ví dụ, hệ thống có hàng chục service, nhưng chỉ một nhóm nghiệp vụ cần expose API `UsageTracked` để xem số lượt xử lý. Nếu áp dụng introduction cho tất cả bean, những bean không liên quan cũng bị mở rộng contract, có thể gây nhầm lẫn khi dependency lookup, tăng proxy overhead hoặc khiến code khác cast được sang một interface vốn không phù hợp.

Giải pháp là **giới hạn tập kiểu được giới thiệu interface**. Ở Spring Framework, có hai con đường chính:

- **`@DeclareParents` trong `@Aspect`**: thuộc tính `value` chứa một **AspectJ type pattern** để chọn những kiểu target cần bổ sung interface; `defaultImpl` nêu implementation.
- **Spring AOP `IntroductionAdvisor`**: `ClassFilter` của advisor quyết định target class nào đủ điều kiện; `IntroductionInfo` mô tả các interface sẽ được proxy expose.

Bài tập trung vào cách Spring **chọn đúng kiểu được bổ sung interface**, kiểm thử kết quả bằng Spring bean/proxy và xử lý các lỗi về phạm vi. Không mở rộng sang compile-time weaving hoặc các kiến thức AspectJ không dùng trong Spring proxy mode.

Sau khi học, bạn có thể thiết kế type pattern rõ ràng, tránh áp dụng đại trà, phân biệt chọn class với chọn method, và xác minh target nào thực sự nhận interface introduced.

## 2. Spring AOP Introduction thay đổi đối tượng nào?

Giả sử target `OrderService` chỉ implement marker interface `TrackableService`. Một Spring aspect bổ sung `UsageTracked`:

~~~text
Class target thật:       OrderService implements TrackableService

Spring AOP proxy:        OrderService proxy
                          + implements TrackableService
                          + implements UsageTracked  <-- interface introduction
                          + delegates nghiệp vụ đến OrderService target
                          + delegates UsageTracked methods đến mixin

Caller qua Spring bean:  proxy instanceof UsageTracked == true
Target new thủ công:     new OrderService() instanceof UsageTracked == false
~~~

Với JDK dynamic proxy, proxy implement các interface đã cấu hình, cộng interface được introduction. Với class-based proxy (Spring CGLIB), proxy là subclass tạo từ class target phù hợp đồng thời có thể expose các interface mới. **Introduction bổ sung khả năng của proxy, không sửa file `.class` của target**.

Một target được chọn nếu **type pattern / `ClassFilter` match kiểu của Spring-managed target bean**. Không phải cứ method có tên phù hợp là interface xuất hiện. Đây là điểm phân biệt quan trọng giữa introduction và pointcut thông thường:

| Cơ chế | Câu hỏi quyết định | Mức áp dụng |
| --- | --- | --- |
| `@DeclareParents(value="...")` | Kiểu target nào nằm trong pattern? | **Type/bean proxy** |
| `IntroductionAdvisor.getClassFilter()` | Target class nào match? | **Type/bean proxy** |
| `@Before("execution(...)")` | Method execution nào match? | **Method invocation qua proxy** |
| `@Around("@annotation(...)")` | Method mang annotation nào được advice? | **Method invocation qua proxy** |

Spring `IntroductionAdvisor` không có `MethodMatcher` dành riêng để quyết định method nào “được bổ sung interface”. Nó dùng **class filter**, vì mục đích của introduction là proxy **triển khai toàn bộ interface contract** ở cấp type; sau đó implementation của introduced methods xử lý lời gọi tương ứng.

## 3. `@DeclareParents`: bốn thành phần phải đúng

Dạng thường dùng trong Spring:

~~~java
@Aspect
public class TrackingIntroductionAspect {

    @DeclareParents(
        value = "demo.api.TrackableService+",
        defaultImpl = DefaultUsageTracked.class
    )
    public static UsageTracked mixin;
}
~~~

Đây là mẫu minh họa; để chạy cần định nghĩa `TrackableService`, `UsageTracked`, `DefaultUsageTracked`, đăng ký class aspect thành Spring bean và bật hỗ trợ `@AspectJ`.

Các thành phần:

1. **Field type `UsageTracked`:** quyết định interface mới mà proxy expose; phải là interface phù hợp. Field `mixin` là khai báo metadata được Spring xử lý, **không phải biến singleton để ứng dụng tự gọi `mixin.incrementUseCount()`**.
2. **`value` type pattern:** chọn target types. Trong ví dụ, `demo.api.TrackableService+` match `TrackableService` và các subtype, bao gồm implementation classes của interface này. Nhờ vậy, chỉ bean có type thực sự thuộc nhóm đó được giới thiệu `UsageTracked`.
3. **`defaultImpl`:** class dùng để triển khai interface `UsageTracked` cho những proxy phù hợp. Class này phải implement interface và có cách khởi tạo phù hợp (thông thường constructor không tham số công khai).
4. **`@Aspect` được Spring quản lý:** `@EnableAspectJAutoProxy` hoặc `<aop:aspectj-autoproxy/>` kích hoạt Spring AOP `@AspectJ`, đồng thời aspect cần được đăng ký bằng `@Component`/component scanning hoặc `@Bean`/`<bean>`.

Quan trọng: giá trị `defaultImpl` không chỉ một Spring bean ID; nó là **Java class literal**. Với `@DeclareParents` thông thường, Spring tạo implementation cho introduction theo contract của nó, không nên giả định instance delegate mặc nhiên là bean `@Component` singleton có autowiring/lifecycle như target. Nếu delegate cần dependency injection, hãy thiết kế rõ việc cấp delegate/Advisor thay vì ngầm kỳ vọng `@Autowired` hoạt động trong implementation do introduction tự tạo.

### Dấu `+` có ý nghĩa gì?

Trong type pattern được Spring AOP thông qua AspectJ parser, hậu tố `+` chỉ **các subtype của kiểu đã nêu, bao gồm bản thân kiểu ấy**. Điều này hữu ích khi muốn chọn những service thực thi một marker/business interface:

~~~java
@DeclareParents(
    value = "demo.api.TrackableService+",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked mixin;
~~~

Các class implement `TrackableService`, dù đặt tại `demo.service` hoặc `demo.feature`, đều nằm trong tập candidate miễn là chúng **là Spring beans được Spring AOP xử lý**. Một class không implement marker thì không match chỉ vì tên chứa “Service”.

Ngược lại, nếu giới hạn bằng pattern package như `demo.service.*`, đó là lựa chọn theo **tên kiểu/package**; package chính xác phải được xác định rõ, tránh dùng `..` quá rộng khi chưa cần. Đừng nhầm `+` thành “match mọi method của class”: nó vẫn là toán tử trên **type pattern**.

## 4. Hai cách giới hạn phạm vi: package và marker interface

### 4.1. Giới hạn theo package hoặc quy ước tên

~~~java
@DeclareParents(
    value = "demo.service.*",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked mixin;
~~~

Ý tưởng: chỉ những target types có tên khớp pattern được chọn. Với cấu trúc module có ranh giới package rõ ràng, cách này thuận tiện. Nếu sau này di chuyển class sang package khác, nó có thể không còn được introduction; nếu có một class không liên quan nhưng được đưa vào đúng vùng type pattern, nó có thể vô tình nhận interface.

Spring documentation cũng minh họa pattern kiểu `com.xyz.service.*+` để nhắm đến cả các implementors của các service interfaces. Cần hiểu từng wildcard và dấu `+` trên **kiểu**, không đọc nó như regular expression Java hay glob file system. Hãy kiểm tra pattern với một tập class thực tế bằng Spring integration test thay vì chỉ đoán dựa vào tên.

### 4.2. Giới hạn bằng marker interface: ổn định theo hợp đồng

~~~java
package demo.api;

public interface TrackableService {
    // Marker cho các Spring services cần UsageTracked.
}
~~~

Khi một service thực sự thuộc nhóm được theo dõi:

~~~java
public class OrderService implements TrackableService {
    // Business methods
}
~~~

`@DeclareParents(value = "demo.api.TrackableService+")` chọn theo quan hệ **implements/extends** thay vì hard-code package của tất cả implementation. Cách này thường rõ chủ ý hơn nếu nhiều module có class ở các package khác nhau.

Marker interface không phải phép thay thế tự động cho business interface. Chỉ thêm marker khi nó thể hiện một contract/phân nhóm có ý nghĩa, không gắn mọi bean chỉ để có một biểu thức AOP ngắn.

### 4.3. Giới hạn theo annotation có được không?

Nếu mục tiêu là chọn class mang một annotation custom như `@TrackUsage`, có thể viết logic đăng ký advisor/class filter để xét metadata của target type. Đừng đánh đồng `@DeclareParents.value` với `@annotation(...)`: `@annotation` là pointcut designator xét **method annotation**, còn `@DeclareParents.value` đòi một **type pattern**.

Với bean selection động/phức tạp, lập kế hoạch dùng `IntroductionAdvisor` và `ClassFilter` hoặc một component Spring quản lý policy đăng ký proxy, thay vì cố nhét một phép kiểm tra request runtime vào type pattern. Giới thiệu interface là quyết định ở **lúc tạo/cấu hình proxy**, không nên bật/tắt theo argument của từng method call.

## 5. Ví dụ Java 17 chạy được: hai service nhận interface, một service bị loại

Ví dụ dùng Java 17, Maven, Spring Framework 6.1.15 và `@EnableAspectJAutoProxy(proxyTargetClass = true)`. Hai service implement marker `TrackableService`, còn `InternalService` không implement. Aspect thêm `UsageTracked` đúng cho hai bean được chọn.

Cấu trúc dự án:

~~~text
spring-introduction-scope/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── api/TrackableService.java
    ├── tracking/UsageTracked.java
    ├── tracking/DefaultUsageTracked.java
    ├── service/OrderService.java
    ├── service/PaymentService.java
    ├── internal/InternalService.java
    └── aop/UsageIntroductionAspect.java
~~~

### 5.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-introduction-scope</artifactId>
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

Spring `spring-context` tạo `ApplicationContext` và component scanning; `spring-aop` triển khai proxy/advisor; `aspectjweaver` chứa các API AspectJ cần cho xử lý `@Aspect`/type pattern. Chương trình dùng **Spring AOP proxy mode**, không bật AspectJ compile-time weaving hoặc load-time weaving.

### 5.2. Marker và interface muốn bổ sung

**src/main/java/demo/api/TrackableService.java**

~~~java
package demo.api;

public interface TrackableService {
    // Marker: các service thuộc nhóm có tracking.
}
~~~

**src/main/java/demo/tracking/UsageTracked.java**

~~~java
package demo.tracking;

public interface UsageTracked {
    void incrementUseCount();
    int getUseCount();
}
~~~

`TrackableService` là điều kiện **chọn kiểu**, còn `UsageTracked` là **interface mới** mà proxy sẽ expose. Chúng có vai trò khác nhau.

### 5.3. Implementation cho interface introduced

**src/main/java/demo/tracking/DefaultUsageTracked.java**

~~~java
package demo.tracking;

import java.util.concurrent.atomic.AtomicInteger;

public class DefaultUsageTracked implements UsageTracked {

    private final AtomicInteger count = new AtomicInteger();

    public DefaultUsageTracked() {
    }

    @Override
    public void incrementUseCount() {
        count.incrementAndGet();
    }

    @Override
    public int getUseCount() {
        return count.get();
    }
}
~~~

Ví dụ dùng `AtomicInteger` để có counter thread-safe đối với thao tác tăng/đọc trong một delegate. Đây là công cụ minh họa trạng thái của một introduced interface; không xem counter tự động đại diện cho usage của cả hệ thống phân tán. Các method `incrementUseCount` và `getUseCount` **không nằm trong class target**.

### 5.4. Hai service được chọn

**src/main/java/demo/service/OrderService.java**

~~~java
package demo.service;

import demo.api.TrackableService;
import org.springframework.stereotype.Service;

@Service
public class OrderService implements TrackableService {

    public String place(String id) {
        return "order:" + id;
    }
}
~~~

**src/main/java/demo/service/PaymentService.java**

~~~java
package demo.service;

import demo.api.TrackableService;
import org.springframework.stereotype.Service;

@Service
public class PaymentService implements TrackableService {

    public String charge(String id) {
        return "payment:" + id;
    }
}
~~~

Hai service đều implement marker, nên type pattern `demo.api.TrackableService+` match cả hai. Lớp target chỉ khai báo business method; `UsageTracked` sẽ là interface **của proxy được Spring tạo**, không phải class target nguyên bản.

### 5.5. Một service nằm ngoài phạm vi

**src/main/java/demo/internal/InternalService.java**

~~~java
package demo.internal;

import org.springframework.stereotype.Service;

@Service
public class InternalService {

    public String ping() {
        return "internal:ok";
    }
}
~~~

`InternalService` là Spring bean hợp lệ, nhưng không implement marker `TrackableService`; vì vậy nó không match introduction pattern trong ví dụ. Nó không cần và không nên bị cast sang `UsageTracked`.

### 5.6. Aspect giới thiệu interface chỉ cho kiểu đã chọn

**src/main/java/demo/aop/UsageIntroductionAspect.java**

~~~java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.stereotype.Component;
import demo.tracking.UsageTracked;
import demo.tracking.DefaultUsageTracked;

@Aspect
@Component
public class UsageIntroductionAspect {

    @DeclareParents(
        value = "demo.api.TrackableService+",
        defaultImpl = DefaultUsageTracked.class
    )
    public static UsageTracked mixin;
}
~~~

Aspect trên chỉ có nhiệm vụ **introduction**; không bắt buộc phải viết thêm `@Before` hay `@Around` để proxy nhận interface mới. Field `mixin` là cách khai báo cho Spring/AspectJ annotation parser, không cần tự khởi tạo field bằng `new DefaultUsageTracked()`.

Nếu một `@Aspect` khác có advice thông thường, các pointcut method của nó hoạt động độc lập; việc “bean đã được introduction” không làm mọi advice khác tự nhiên chạy.

### 5.7. Spring configuration và chương trình kiểm chứng

**src/main/java/demo/App.java**

~~~java
package demo;

import demo.internal.InternalService;
import demo.service.OrderService;
import demo.service.PaymentService;
import demo.tracking.UsageTracked;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan("demo")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(App.class)) {
            OrderService orders = context.getBean(OrderService.class);
            PaymentService payments = context.getBean(PaymentService.class);
            InternalService internal = context.getBean(InternalService.class);

            System.out.println("ordersProxy=" + AopUtils.isAopProxy(orders));
            System.out.println("paymentsProxy=" + AopUtils.isAopProxy(payments));
            System.out.println("internalIntroduced=" + (internal instanceof UsageTracked));

            System.out.println("ordersIntroduced=" + (orders instanceof UsageTracked));
            System.out.println("paymentsIntroduced=" + (payments instanceof UsageTracked));

            UsageTracked orderUsage = (UsageTracked) orders;
            UsageTracked paymentUsage = (UsageTracked) payments;

            orderUsage.incrementUseCount();
            orderUsage.incrementUseCount();
            paymentUsage.incrementUseCount();

            System.out.println("orderCount=" + orderUsage.getUseCount());
            System.out.println("paymentCount=" + paymentUsage.getUseCount());
            System.out.println(orders.place("O-1"));
            System.out.println(payments.charge("P-1"));
            System.out.println(internal.ping());
        }
    }
}
~~~

Chạy tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng chương trình tự in theo thứ tự trong cấu hình ví dụ:

~~~text
ordersProxy=true
paymentsProxy=true
internalIntroduced=false
ordersIntroduced=true
paymentsIntroduced=true
orderCount=2
paymentCount=1
order:O-1
payment:P-1
internal:ok
~~~

Maven hoặc logger Spring có thể tạo thông báo bổ sung tùy môi trường; chuỗi output trên là kết quả dự kiến của những lệnh `System.out.println` trong ứng dụng.

**Giải thích kết quả:**

1. `@ComponentScan("demo")` đăng ký aspect và ba service. `@EnableAspectJAutoProxy` bật Spring AOP `@AspectJ` cho context.
2. `orders` và `payments` là Spring bean proxies vì introduction advisor có type pattern khớp hai target.
3. `internal` không thuộc marker interface nên không nhận `UsageTracked` từ aspect này.
4. `instanceof UsageTracked` xác minh **contract runtime trên object Spring đang trả**; chỉ đọc `OrderService.class.getInterfaces()` sẽ không thấy một interface mà source class chưa khai báo.
5. `orderCount=2` và `paymentCount=1` minh họa logic introduced methods chạy trên các proxy tương ứng. Đây không phải thống kê tự động method nghiệp vụ; chương trình chủ động gọi `incrementUseCount()`.
6. `orders.place`, `payments.charge`, `internal.ping` vẫn thực hiện đúng nghiệp vụ. Interface mới không tự thay đổi kết quả các method ấy.
7. Trong ví dụ, `proxyTargetClass=true` tạo class-based proxy để có thể lấy bean bằng `OrderService.class` và `PaymentService.class`; không dùng lựa chọn này như một cách sửa mọi lỗi AOP.

## 6. Nếu muốn tự động cập nhật interface được introduction khi gọi service

Spring documentation minh họa kết hợp introduction với advice bình thường bằng `this(usageTracked)`:

~~~java
@Before(
    value = "execution(* demo.service.OrderService.place(..)) && this(usageTracked)",
    argNames = "usageTracked"
)
public void incrementOnPlace(UsageTracked usageTracked) {
    usageTracked.incrementUseCount();
}
~~~

Đây là **biến thể minh họa**, không được đặt nguyên trong ví dụ chính nếu chưa bổ sung vào một class `@Aspect` có imports `org.aspectj.lang.annotation.Before`. `this(usageTracked)` ràng buộc **đối tượng Spring proxy đang được advice**, tức proxy đã implement interface `UsageTracked`, và `incrementUseCount` gọi implementation của interface bổ sung.

Để tránh recursion ngoài dự kiến, `execution(...place(..))` chỉ chọn method nghiệp vụ `place`; lời gọi `incrementUseCount()` không match biểu thức đó. Nếu pointcut được viết quá rộng và tự match introduced methods, có thể gây advice chồng chéo hoặc vòng lặp.

Lưu ý rằng **phạm vi introduction** và **phạm vi lời gọi tự động đếm** là hai quyết định khác nhau:

- Type pattern `demo.api.TrackableService+` chọn những target beans có thể expose `UsageTracked`.
- `execution(* demo.service.OrderService.place(..))` chọn method invocation mà advice tự động đếm.
- `this(usageTracked)` yêu cầu proxy được gọi có interface introduced để bind object.

Đừng dùng một method pointcut để thay thế type pattern của `@DeclareParents`; nếu muốn thêm interface cho một tập bean, quyết định phải diễn ra tại cấp **type/proxy configuration**.

## 7. Cấu hình XML tương đương với `aop:declare-parents`

Spring AOP schema-based XML có cú pháp chỉ rõ **types-matching**:

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

    <bean id="orderService" class="demo.service.OrderService"/>
    <bean id="paymentService" class="demo.service.PaymentService"/>
    <bean id="internalService" class="demo.internal.InternalService"/>

    <!-- Backing aspect bean chỉ để khai báo introduction kiểu XML. -->
    <bean id="xmlTrackingAspect" class="demo.aop.XmlTrackingAspect"/>

    <aop:config proxy-target-class="true">
        <aop:aspect ref="xmlTrackingAspect">
            <aop:declare-parents
                types-matching="demo.api.TrackableService+"
                implement-interface="demo.tracking.UsageTracked"
                default-impl="demo.tracking.DefaultUsageTracked"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

Đoạn cấu hình trên là **biến thể dùng để thay thế cách `@DeclareParents`**; không nên đồng thời bật cả hai introduction trùng chức năng trong cùng context. `demo.aop.XmlTrackingAspect` cần được định nghĩa như một public class Java thích hợp (có thể là lớp trống với constructor không tham số) để Spring tạo bean; ở ví dụ chính đã dùng `UsageIntroductionAspect` kiểu annotation thay cho cấu hình XML này.

Ý nghĩa các thuộc tính:

- `types-matching`: **type pattern** chọn target classes cần introduction, tương ứng `@DeclareParents.value`.
- `implement-interface`: tên interface mà proxy sẽ expose, tương ứng kiểu field của `@DeclareParents`.
- `default-impl`: class cung cấp implementation cho interface mới.
- `proxy-target-class="true"`: chỉ định class-based proxy nếu cần lookup/cast theo concrete target class; không phải một phần logic chọn type.

Không nhầm `types-matching` với một method expression `execution(...)` hoặc bean-name pattern `bean(...)`. Giới hạn này được diễn giải như **AspectJ type pattern** cho introduction.

## 8. Programmatic Spring AOP: dùng IntroductionAdvisor và ClassFilter

Khi tạo Spring proxy programmatically, giao diện mở rộng là `org.springframework.aop.IntroductionAdvisor`. Nó cung cấp:

~~~java
ClassFilter getClassFilter();
Class<?>[] getInterfaces();
void validateInterfaces();
~~~

Đây là sơ đồ rút gọn các method của `IntroductionAdvisor` và `IntroductionInfo`, **không phải interface cần tạo lại**. `ClassFilter` là thành phần xác định **target class** được áp dụng introduction. Khác với `PointcutAdvisor`, introduction không có `MethodMatcher` dùng để chọn từng method.

Spring cung cấp `DefaultIntroductionAdvisor` và `DelegatingIntroductionInterceptor`. Ví dụ **snippet** chỉ minh họa thao tác tạo advisor:

~~~java
var delegate = new DefaultUsageTracked();
var interceptor = new DelegatingIntroductionInterceptor(delegate);
var advisor = new DefaultIntroductionAdvisor(
    interceptor, UsageTracked.class
);
~~~

Các imports tương ứng:

~~~java
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
~~~

`DefaultIntroductionAdvisor` theo mặc định **áp dụng cho mọi target class** nếu không giới hạn thêm. Vì vậy đoạn code ngắn trên **chưa đáp ứng** yêu cầu “chỉ một số service nhận interface mới”. Nếu ứng dụng tự tạo proxy riêng cho từng bean, cách rõ ràng là **chỉ gắn advisor introduction khi target type đạt điều kiện**:

~~~java
ProxyFactory factory = new ProxyFactory();
factory.setTarget(target);
factory.setProxyTargetClass(true);

if (TrackableService.class.isAssignableFrom(target.getClass())) {
    var delegate = new DefaultUsageTracked();
    var intro = new DelegatingIntroductionInterceptor(delegate);
    factory.addAdvisor(new DefaultIntroductionAdvisor(
        intro, UsageTracked.class
    ));
}

Object proxy = factory.getProxy();
~~~

Đoạn trên là **snippet minh họa trong code tạo proxy**, giả định `target` là một object không final, `TrackableService` và `UsageTracked` đã được định nghĩa, và các imports của Spring AOP `ProxyFactory` có mặt. Đây là quyết định **chọn advisor cho từng proxy**; Spring không tự áp dụng nó cho tất cả beans trong `ApplicationContext`.

Khi cần cho Spring auto-proxying quyết định tập target, sử dụng `IntroductionAdvisor.getClassFilter()` trong implementation phù hợp. Nếu dùng `DefaultIntroductionAdvisor` mà muốn giới hạn theo class, có thể tạo subclass override `matches(Class<?> clazz)` (vì lớp này tự triển khai `ClassFilter`):

~~~java
DefaultIntroductionAdvisor advisor =
    new DefaultIntroductionAdvisor(
        new DelegatingIntroductionInterceptor(new DefaultUsageTracked()),
        UsageTracked.class
    ) {
        @Override
        public boolean matches(Class<?> clazz) {
            return TrackableService.class.isAssignableFrom(clazz);
        }
    };
~~~

Đoạn code trên là **snippet minh họa**, không phải một ứng dụng chạy độc lập; cần đăng ký advisor vào hạ tầng auto-proxy tương ứng, không đủ chỉ tạo instance advisor bằng `new`. Đặc biệt, nếu bạn tạo `ProxyFactory` đã được quyết định chỉ cho **một target**, nên tránh add introduction advisor không match target chỉ để mong method matching lọc giúp: introduction là quyết định **theo class**, không theo per-method call.

## 9. Phạm vi type không đồng nghĩa phạm vi của Spring bean name

Ba cách giới hạn thường bị nhầm:

| Ý định | Cơ chế Spring | Ví dụ | Ý nghĩa |
| --- | --- | --- | --- |
| Chọn **type** được thêm interface | `@DeclareParents.value` hoặc XML `types-matching` | `demo.api.TrackableService+` | Mọi Spring bean có target type phù hợp |
| Chọn bean theo **tên** trong một advice method | Spring pointcut `bean(namePattern)` | `bean(orderService)` | Match lời gọi của bean có tên phù hợp |
| Chọn **method** được chạy before/around advice | `execution(...)`, `@annotation(...)` | `execution(* *..place(..))` | Match method invocation qua proxy |

Vì thế nếu cùng class target được đăng ký thành hai bean tên `primaryService` và `backupService`, một `@DeclareParents` có type pattern match class ấy thường có thể áp dụng cho **cả hai proxy**, không phân biệt bean ID. Nếu mục tiêu khác nhau **chỉ theo bean name**, phải thiết kế chính sách proxy/advisor registration phù hợp, không tự giả định `@DeclareParents.value` có cú pháp `bean(...)`.

Một kiểu phù hợp nhưng không thuộc phạm vi Spring quản lý cũng **không** được Spring tự sinh proxy:

~~~java
OrderService raw = new OrderService();
System.out.println(raw instanceof UsageTracked); // false
~~~

Code này giả định dùng class của ví dụ mục 5. `@DeclareParents` được Spring xử lý khi tạo AOP proxy cho các bean đủ điều kiện; nó không sửa class của mọi object được tạo bằng `new`.

## 10. JDK proxy, CGLIB và khả năng nhìn thấy interface introduced

Trong ví dụ chính dùng `proxyTargetClass=true` để có thể lấy bean bằng concrete class. Nếu dùng JDK proxy, interface introduced có thể vẫn hiện diện nhưng caller nên lấy bean qua **business interface** đã được JDK proxy expose hoặc interface mới. Không được giả định JDK proxy có thể cast sang implementation class.

~~~java
Object bean = context.getBean("orderService");
boolean hasIntroduction = bean instanceof UsageTracked;
~~~

Đây là cách kiểm tra vững hơn việc xem `bean.getClass().getInterfaces()` của **target source class**, vì introduced interface thuộc **Spring proxy instance**.

Với JDK proxy:

~~~java
UsageTracked tracked =
    context.getBean("orderService", UsageTracked.class);
~~~

Đoạn code này có thể hoạt động nếu bean tên `orderService` được introduction advisor match và proxy expose `UsageTracked`. Không bảo đảm nó hoạt động nếu bean không thuộc phạm vi, Spring AOP chưa bật hoặc chưa có proxy.

Với CGLIB, target cần đủ điều kiện subclass: final class không thể subclass; final method không được intercept bằng override; module/package restrictions có thể ảnh hưởng. Không nên ép class proxy chỉ vì muốn thêm interface nếu hệ thống đã có business interface rõ ràng.

**Spring AOP chỉ áp dụng trong phạm vi method execution qua proxy.** Nếu target object tự gọi `this.someMethod()`, lời gọi nội bộ thường bypass method advice. Nhưng giới hạn này **không phủ nhận** việc outer proxy object đã được introduction; cần phân biệt lúc proxy expose interface với lúc từng method advice thực thi.

## 11. Kiểm tra chính xác type scope bằng integration test

Với Spring integration test, lấy **Spring bean thật**, không dùng `new` target:

~~~java
Object order = context.getBean("orderService");
Object payment = context.getBean("paymentService");
Object internal = context.getBean("internalService");

assertTrue(order instanceof UsageTracked);
assertTrue(payment instanceof UsageTracked);
assertFalse(internal instanceof UsageTracked);
~~~

Đây là **JUnit snippet**, cần `context` và các static imports `org.junit.jupiter.api.Assertions.assertTrue/assertFalse`. Nó kiểm tra **mặt ngoài của proxy**, không chỉ xác minh Java class định nghĩa method.

Các trường hợp nên test:

1. **Matched target type:** marker/type pattern đúng → Spring bean proxy `instanceof UsageTracked` là `true`.
2. **Unmatched target type:** bean vẫn tồn tại, business method vẫn chạy, nhưng không nhận interface mới.
3. **Subtype ngoài package:** nếu sử dụng `TrackableService+`, implementation đặt ở package khác nhưng cùng subtype vẫn phải match nếu Spring quét/đăng ký bean ấy.
4. **Bean mới trong package:** nếu chọn bằng wildcard `demo.service.*`, thêm một class unrelated vào package có thể thay đổi tập match — cần test để phát hiện.
5. **Hai bean cùng target class:** khi dùng type pattern, cả hai có khả năng nhận interface; không mặc nhiên giới hạn theo bean name.
6. **Raw instance:** `new OrderService()` không tự implement `UsageTracked`.
7. **Proxy strategy:** JDK/class-based đều expose introduced interface trong trường hợp đủ điều kiện; kiểm thử lookup/cast phù hợp.
8. **Counter state:** nếu implementation introduced giữ state, kiểm tra hai proxy không vô tình chia sẻ state khi yêu cầu là per-proxy.
9. **Multiple introductions:** nếu thêm nhiều aspect/intro advisors, kiểm tra trùng interface, xung đột signature, order và target coverage.
10. **Lifecycle:** nếu scan package hoặc bật `@EnableAspectJAutoProxy` thay đổi, kiểm tra BeanFactory vẫn đăng ký aspect/proxy đúng.

Không sử dụng tên generated CGLIB/JDK proxy như dữ liệu ổn định trong assertion. `AopUtils.isAopProxy(bean)` cùng `instanceof UsageTracked` và hành vi method introduced là những phép xác minh có ý nghĩa hơn.

## 12. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Cách sửa |
| --- | --- | --- |
| Mọi bean đều nhận interface introduced ngoài ý định | Type pattern quá rộng (ví dụ `*`), ClassFilter mặc định match mọi class | Giới hạn theo marker interface, package chính xác hoặc ClassFilter rõ ràng |
| Bean mong muốn không nhận interface | Pattern không match target type, thiếu `+` khi muốn subtype, aspect chưa được đăng ký | Test class hierarchy, kiểm tra aspect bean và auto-proxy configuration |
| Target `new` không cast được sang introduced interface | Chỉ Spring proxy mới được giới thiệu interface | Lấy Spring bean từ ApplicationContext |
| Bean không match vẫn bị ép cast `UsageTracked` | Caller cho rằng mọi service đều có mixin | Kiểm tra `instanceof` hoặc contract/qualifier của bean |
| `@DeclareParents` hiện diện nhưng không tác dụng | `@Aspect` chưa được Spring xử lý, thiếu `aspectjweaver`, không bật auto-proxy | Đăng ký aspect bean và `@EnableAspectJAutoProxy` |
| Không lấy được bean theo concrete implementation | Spring dùng JDK interface proxy | Lấy theo business interface hoặc chọn class proxy có chủ đích |
| `defaultImpl` không implement interface hoặc không tạo được | Mixin class không thỏa contract/constructor phù hợp | Cung cấp class implement đúng introduced interface và constructor hợp lệ |
| Mỗi service có counter ngoài dự kiến | Delegate state semantics chưa được thiết kế/kiểm thử | Xác định per-proxy vs shared state, viết test và dùng concurrency-safe fields |
| Method được gọi nhưng intro không tự cập nhật usage | Introduction chỉ bổ sung interface, không tự gọi method counters | Tạo advice có pointcut rõ ràng hoặc caller gọi introduced API |
| Dùng `@annotation` để giới hạn kiểu class trong `@DeclareParents` | Trộn method pointcut với type pattern | Chọn target types bằng pattern/`ClassFilter` |
| Dùng `bean(name)` trong `@DeclareParents.value` | Type pattern không phải Spring bean method designator | Chọn theo type hoặc dùng custom proxy/advisor registration theo bean |
| `ClassFilter` match đúng nhưng proxy vẫn không expose interface | IntroductionAdvisor chưa được gắn đúng vào proxy/auto-proxy creator hoặc validate thất bại | Kiểm tra `Advised.getAdvisors()` và `validateInterfaces` |
| Interface trùng nhiều introductions | Mâu thuẫn delegate hoặc khó dự đoán đường method | Không đăng ký trùng; kiểm thử advisor chain |
| Thay đổi pattern làm bean bên ngoài module nhận interface | Phạm vi package/subtype vượt chủ đích | Dùng marker hay policy class filter, kiểm thử positive/negative coverage |

### Checklist debug nhanh

1. Bean đã được Spring quản lý và aspect đã được Spring đăng ký chưa?
2. Pattern `@DeclareParents.value` có thực sự là **type pattern** cho đúng target class không?
3. Với hậu tố `+`, các subtype/implementations được kỳ vọng có nằm trong tập cần match không?
4. Interface muốn bổ sung có đúng là **kiểu field** chứa `@DeclareParents`?
5. `defaultImpl` có implement interface và có cách Spring khởi tạo hợp lệ?
6. Spring đã tạo AOP proxy cho matched bean chưa, và caller đang dùng đúng proxy ấy?
7. Những bean ngoài phạm vi có giữ nguyên contract cũ hay bị introduction rộng hơn dự kiến?
8. Có dùng JDK proxy nhưng caller lại cố cast proxy sang concrete implementation?
9. Nếu dùng `DefaultIntroductionAdvisor`, đã xét rằng mặc định không có giới hạn type chưa?
10. Có test đúng tập target beans sau mỗi lần refactor package/type hierarchy?

## 13. Nguyên tắc thiết kế phạm vi introduction

**Ưu tiên kiểu có ý nghĩa nghiệp vụ:** một marker interface hoặc một superinterface chung phản ánh rõ “dịch vụ nào cần chức năng introduced” thường dễ review hơn wildcard bao phủ rất nhiều package. Nhưng marker cũng là một contract mới và cần được duy trì đúng.

**Mỗi target cần một contract proxy rõ ràng:** introduction không nên là một công cụ bí mật để biến mọi Spring bean thành đối tượng đa năng. Hãy giữ interface bổ sung đủ nhỏ, tên rõ và áp dụng đúng nơi cần. Người dùng API có thể kiểm tra `instanceof` trên Spring bean nếu intro là tùy chọn.

**Test cả positive và negative coverage:** một introduction pattern match tất cả beans vẫn có thể khiến những test chỉ kiểm tra positive pass. Test bean nằm ngoài phạm vi mới phát hiện introduction quá rộng.

**Không nhầm package scope với method scope:** nếu muốn một số methods được advice, dùng method pointcut; nếu muốn một số proxy có thêm interface, dùng type pattern/`ClassFilter`. Có thể kết hợp hai cơ chế, nhưng chúng không thay thế cho nhau.

**Giữ semantics proxy-based:** giới hạn introduction không đổi bản chất class target, không intercept constructor/field và không khắc phục self-invocation. Tài liệu Java class hierarchy của target chưa được biến đổi; đối tượng proxy do Spring cung cấp mới có interface introduced.

## 14. Kết luận

Trong Spring AOP, **giới hạn phạm vi kiểu được bổ sung interface** nghĩa là chọn **Spring target bean types** phù hợp để áp dụng introduction. `@DeclareParents(value="...")` và XML `types-matching` dùng AspectJ **type pattern**; `IntroductionAdvisor.getClassFilter()` cung cấp cách chọn class ở API programmatic. Interface mới là contract của Spring proxy, do field type/`implement-interface` xác định, và `defaultImpl` cung cấp implementation.

Lựa chọn an toàn là bắt đầu từ **một type pattern hẹp, có ý nghĩa**, kiểm thử một bean được giới thiệu và một bean bị loại, rồi kiểm chứng `instanceof` trên **object lấy từ Spring ApplicationContext**. Việc đã nhận interface không làm mọi method advice khác tự động chạy; đó là một cơ chế độc lập trong Spring AOP.

### Tài liệu tham khảo Spring

- [Spring Framework — Introduction trong @AspectJ](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html)
- [Spring Framework — Schema-based AOP, phần Introductions](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring Framework — IntroductionAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/IntroductionAdvisor.html)
- [Spring Framework — DefaultIntroductionAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-004">Truy cập interface mới thông qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập interface mới thông qua proxy trong Spring AOP Introduction

## 1. Mục tiêu của bài học

Trong **Spring AOP**, một đối tượng nghiệp vụ có thể được bọc bởi **AOP proxy** được cấu hình để **triển khai thêm một interface** mà lớp đích (target class) chưa hề khai báo. Cơ chế này được Spring gọi là **Introduction**. Ứng dụng muốn gọi phương thức thuộc interface mới phải sử dụng **đúng proxy đã được áp dụng Introduction**, thay vì truy cập trực tiếp đối tượng target gốc.

Ví dụ: một service chỉ có nghiệp vụ **greet()**; Spring AOP có thể cung cấp thêm năng lực **Taggable.tag()** trên proxy. Không cần sửa source code của service, nhưng ở runtime client có thể ép kiểu **proxy** sang **Taggable** và gọi **tag()**.

Bài này chỉ đi sâu vào Spring AOP: nơi interface mới được gắn vào proxy, cách gọi qua biến và qua Spring container, ví dụ **ProxyFactory** và **@DeclareParents**, quy tắc về cast, type, vòng đời và những lỗi hay gặp. Các lý thuyết AOP nói chung không phải trọng tâm.

Sau khi đọc, bạn sẽ:
- Hiểu **target**, **proxy**, interface gốc và interface được introduce khác nhau thế nào trong Spring AOP.
- Gọi đúng phương thức interface mới qua **instanceof**, ép kiểu và **ApplicationContext.getBean(...)**.
- Biết cách **IntroductionAdvisor** và **IntroductionInterceptor** cung cấp triển khai cho interface đó.
- Nhận diện lúc nào Spring có thể tạo JDK dynamic proxy hoặc class-based proxy và vì sao điều này ảnh hưởng đến kiểu mà client truy cập.
- Biết cách kiểm tra và xử lý **ClassCastException**, lỗi gọi trực tiếp target hoặc lấy sai bean.

## 2. Interface mới thuộc về đối tượng nào?

Giả sử ta có một service bình thường:

~~~java
public interface Greeting {
    String greet(String name);
}

public class GreetingService implements Greeting {
    @Override
    public String greet(String name) {
        return "Xin chào, " + name;
    }
}
~~~

Hai type trên không khai báo **Taggable**. Khi Spring tạo AOP proxy có Introduction, cấu trúc có thể được hình dung như sau:

~~~text
                         +--------------------------------------+
 Client                  | Spring AOP proxy                     |
   |                     | implements Greeting, Taggable        |
   |                     |                                      |
   |  Greeting.greet()   |    gọi sang target GreetingService  |
   +-------------------->+-----------------------+--------------+
   |                     |                       |
   |  Taggable.tag()     |    IntroductionInterceptor
   +-------------------->+      gọi implementation Taggable
                         |                       |
                         +-----------------------+--------------+
                                                 |
                                             original target
                                             chỉ implements Greeting
~~~

Phải phân biệt rõ:

| Đối tượng / type | Có thể gọi phương thức gì? | Lý do |
| --- | --- | --- |
| **GreetingService target** | **greet()** | Class đã implement **Greeting** |
| **Spring AOP proxy có Introduction** | **greet()** và **tag()** | Proxy được Spring cấu hình expose cả interface gốc và mới |
| **Biến kiểu Greeting** trỏ đến proxy | Gọi **greet()** trực tiếp; muốn gọi **tag()** phải ép kiểu | Kiểu khai báo của biến chỉ có method của **Greeting** |
| **Biến kiểu Taggable** trỏ đến cùng proxy | Gọi **tag()** trực tiếp | Proxy runtime là instance của **Taggable** |

**Introduction không sửa bytecode của target và cũng không tự thêm mệnh đề implements vào lớp Java ban đầu.** Interface mới là hợp đồng mà **proxy** hiện thực ở runtime nhờ Spring AOP. Một object được tạo bằng **new GreetingService()** vẫn không phải **Taggable**.

Điều kiện cần để ép kiểu thành công là **runtime object thật sự implement interface mới**, chứ không phải code có khai báo aspect hay đã import tên interface.

## 3. Spring AOP triển khai Introduction bằng gì?

Có hai lối cấu hình Spring thường gặp:

1. **Spring AOP API**: tạo **IntroductionInterceptor** (ví dụ **DelegatingIntroductionInterceptor**), đóng gói trong **IntroductionAdvisor** (ví dụ **DefaultIntroductionAdvisor**), rồi thêm vào **ProxyFactory** hoặc một hệ thống tạo proxy phù hợp.
2. **Spring @AspectJ support**: khai báo **@DeclareParents** trong **@Aspect** để Spring tự nhận diện type phù hợp và đưa interface mới lên AOP proxy của bean được quản lý.

### 3.1. IntroductionAdvisor và IntroductionInterceptor

**IntroductionAdvisor** công bố danh sách các interface được giới thiệu cùng một **ClassFilter** để xác định những kiểu target có thể nhận Introduction. Khác một method-level pointcut, cơ chế này giới thiệu một interface ở cấp kiểu, nên **IntroductionAdvisor không dùng MethodMatcher**.

**IntroductionInterceptor** phải xử lý những invocation của interface mới. Nếu target chưa implement interface đó, interceptor **không thể chỉ gọi invocation.proceed()** để hy vọng target tự thực thi. Với **DelegatingIntroductionInterceptor**, Spring chuyển lời gọi interface mới cho một delegate có triển khai thực tế; lời gọi nghiệp vụ thông thường vẫn đi tiếp đến target.

**DefaultIntroductionAdvisor** đảm nhiệm việc công bố interface; **DelegatingIntroductionInterceptor** đảm nhiệm việc chuyển lời gọi. Hai vai trò này thường được sử dụng cùng nhau.

### 3.2. @DeclareParents

Trong Spring @AspectJ, **@DeclareParents** là cách khai báo interface sẽ được introduce vào các target type khớp AspectJ type pattern. Interface được xác định bởi kiểu của trường được chú thích; **defaultImpl** cung cấp triển khai mặc định cho interface đó.

**@DeclareParents** cần Spring AOP xử lý bean qua proxy (thường dùng **@EnableAspectJAutoProxy** và một **@Aspect** được đăng ký làm Spring bean). Chỉ đặt annotation trong code rồi gọi **new Service()** sẽ không làm object đó có interface mới.

## 4. Ví dụ Java 17+ chạy được: truy cập interface mới qua ProxyFactory

Ví dụ này cố ý dùng **ProxyFactory** để thấy rõ target, proxy và implementation của interface mới. Ta tạo một target nghiệp vụ, sau đó công bố interface **Taggable** trên proxy bằng **DefaultIntroductionAdvisor**.

Cấu trúc Maven:

~~~text
spring-introduction-demo/
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
    <artifactId>spring-introduction-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
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
~~~

**src/main/java/Main.java**:

~~~java
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

public class Main {

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            return "Xin chào, " + name;
        }
    }

    // Interface mới không có trong lớp GreetingService.
    public interface Taggable {
        String tag();
    }

    // Delegate có triển khai thật của interface được introduce.
    public static class TagImplementation implements Taggable {
        @Override
        public String tag() {
            return "spring-aop";
        }
    }

    public static void main(String[] args) {
        GreetingService target = new GreetingService();

        DelegatingIntroductionInterceptor interceptor =
                new DelegatingIntroductionInterceptor(new TagImplementation());

        DefaultIntroductionAdvisor advisor =
                new DefaultIntroductionAdvisor(interceptor, Taggable.class);

        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvisor(advisor);

        Object proxy = factory.getProxy();

        System.out.println("Target Taggable: " +
                Taggable.class.isInstance(target));
        System.out.println("Proxy Taggable: " +
                Taggable.class.isInstance(proxy));

        Greeting greeting = (Greeting) proxy;
        System.out.println(greeting.greet("Lan"));

        Taggable tagged = (Taggable) proxy;
        System.out.println("Tag: " + tagged.tag());
        System.out.println("Same proxy: " + (greeting == tagged));
    }
}
~~~

Chạy trong thư mục chứa **pom.xml**:

~~~bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=Main
~~~

**Các dòng stdout của Main** (không tính dòng thông báo của Maven):

~~~text
Target Taggable: false
Proxy Taggable: true
Xin chào, Lan
Tag: spring-aop
Same proxy: true
~~~

Giải thích kết quả:

1. **Taggable.class.isInstance(target)** trả **false** vì class **GreetingService** không khai báo interface đó.
2. **factory.addAdvisor(advisor)** khiến Spring AOP công bố interface **Taggable** trên proxy và dùng interceptor/delegate tương ứng để xử lý **tag()**.
3. **greeting.greet("Lan")** vẫn thực thi nghiệp vụ trên target và trả về **Xin chào, Lan**.
4. **tagged.tag()** được xử lý bởi **TagImplementation** thông qua Introduction, trả **spring-aop**.
5. Hai biến **greeting** và **tagged** đều tham chiếu **cùng một proxy**, chỉ khác kiểu khai báo. Không hề có phép biến target gốc thành một class mới.

Lưu ý: Java không cho gọi **greeting.tag()** khi **greeting** có kiểu tĩnh là **Greeting** vì **Greeting** không khai báo phương thức **tag()**. Phải cast (an toàn khi proxy runtime đã expose interface) hoặc giữ sẵn reference kiểu **Taggable**.

## 5. Các cách truy cập interface mới đúng với Spring proxy

### 5.1. Kiểm tra runtime rồi ép kiểu

Nếu client đang nắm một tham chiếu kiểu interface gốc:

~~~java
Greeting greeting = (Greeting) proxy;

if (greeting instanceof Taggable tagged) {
    System.out.println(tagged.tag());
}
~~~

Trong ví dụ trên, kết quả là **spring-aop**. Cú pháp **instanceof** pattern binding khả dụng từ Java 16, nên phù hợp Java 17+.

Nếu cần xử lý lỗi rõ ràng:

~~~java
if (!(greeting instanceof Taggable)) {
    throw new IllegalStateException(
            "Bean chưa được Spring AOP introduce interface Taggable");
}

Taggable tagged = (Taggable) greeting;
String result = tagged.tag();
~~~

Đây là những đoạn minh họa dùng các type/biến của ví dụ đầy đủ tại mục 4. **Không cast thẳng mọi bean sang interface mới mà chưa xác minh**: nếu bean không được proxy hoặc Introduction không áp dụng, cast sẽ ném **ClassCastException**.

### 5.2. Lấy Spring bean bằng tên và interface được introduce

Với **@DeclareParents**, Spring reference minh họa trực tiếp dạng:

~~~java
Taggable tagged = context.getBean("greetingService", Taggable.class);
System.out.println(tagged.tag());
~~~

Điều này hợp lệ khi:
- **greetingService** là bean thực sự được quản lý trong **ApplicationContext**.
- Cấu hình Introduction khớp target type của bean ấy.
- Bean trả về từ container là **AOP proxy** được công bố như **Taggable**.

Đoạn trên dùng biến **context** thuộc Spring container và tên bean **greetingService** trong giả định; **không phải** chương trình độc lập của mục 4 (vốn dùng **ProxyFactory** trực tiếp, không tạo ApplicationContext).

Có thể dùng:

~~~java
Object bean = context.getBean("greetingService");
if (bean instanceof Taggable tagged) {
    System.out.println(tagged.tag());
}
~~~

Khi có nhiều bean cùng expose **Taggable**, tra cứu thuần theo type có thể không xác định duy nhất bean mong muốn. Dùng **tên bean**, qualifier hoặc cơ chế injection thích hợp của Spring.

### 5.3. Inject bằng interface được introduce

Khi Spring thực sự expose interface mới trên bean tương ứng, một thành phần phụ thuộc có thể dùng **Taggable** để gọi năng lực được introduce:

~~~java
public class TagReader {
    private final Taggable taggable;

    public TagReader(Taggable taggable) {
        this.taggable = taggable;
    }

    public void printTag() {
        System.out.println(taggable.tag());
    }
}
~~~

Code này minh họa constructor injection vào một thành phần ứng dụng; cần có **Taggable** và cấu hình Spring bean phù hợp. Nếu container có nhiều bean cùng interface **Taggable**, phải chọn đúng bean bằng **@Qualifier** hoặc cách định danh phù hợp. Nếu không có proxy nào expose **Taggable**, injection theo interface sẽ không thành công.

## 6. Spring @AspectJ: công bố interface với @DeclareParents

Với các Spring bean được tạo tự động, code AOP có thể dùng **@DeclareParents** để thêm interface vào đúng target type mà không tự gọi **ProxyFactory** cho từng bean.

Ví dụ các class riêng trong package xác định (mỗi đoạn nằm trong file Java tương ứng):

**src/main/java/example/service/ReportService.java**

~~~java
package example.service;

public class ReportService {
    public String render() {
        return "report-ready";
    }
}
~~~

**src/main/java/example/feature/Trackable.java**

~~~java
package example.feature;

public interface Trackable {
    String trackingCode();
}
~~~

**src/main/java/example/feature/DefaultTrackable.java**

~~~java
package example.feature;

public class DefaultTrackable implements Trackable {
    @Override
    public String trackingCode() {
        return "tracked-by-spring";
    }
}
~~~

**src/main/java/example/config/IntroductionAspect.java**

~~~java
package example.config;

import example.feature.DefaultTrackable;
import example.feature.Trackable;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

@Aspect
public class IntroductionAspect {
    @DeclareParents(
        value = "example.service.ReportService+",
        defaultImpl = DefaultTrackable.class
    )
    public static Trackable introducedTracking;
}
~~~

**src/main/java/example/config/AppConfig.java**

~~~java
package example.config;

import example.service.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    ReportService reportService() {
        return new ReportService();
    }

    @Bean
    IntroductionAspect introductionAspect() {
        return new IntroductionAspect();
    }
}
~~~

**src/main/java/example/App.java**

~~~java
package example;

import example.config.AppConfig;
import example.feature.Trackable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Object bean = context.getBean("reportService");
            System.out.println("Proxy Trackable: " +
                    (bean instanceof Trackable));

            Trackable tracking =
                    context.getBean("reportService", Trackable.class);
            System.out.println(tracking.trackingCode());
        }
    }
}
~~~

Với các file đúng package như trên, cùng dependencies **spring-context**, **spring-aop** và **aspectjweaver** tương thích Spring Framework 6.2, đầu ra do class **App** in sẽ là:

~~~text
Proxy Trackable: true
tracked-by-spring
~~~

Đây là ví dụ minh họa cấu hình **Spring-managed Introduction**; chưa kèm Maven project đầy đủ như mục 4. Muốn chạy riêng ví dụ này, thêm các dependencies nêu trên vào POM, thay đổi main class thành **example.App** và đặt các file đúng đường dẫn đã ghi.

Những điểm đáng chú ý:
- **value = "example.service.ReportService+"** là **AspectJ type pattern** dùng để chọn type phù hợp (class này và các subtype thích hợp), không phải Spring bean name hay tên phương thức.
- Interface mới lấy từ kiểu trường **Trackable introducedTracking**.
- **defaultImpl = DefaultTrackable.class** cung cấp implementation của các method mới; class phải implement đúng interface và có constructor phù hợp cho việc khởi tạo của Spring AOP.
- **@EnableAspectJAutoProxy** bật khả năng xử lý **@Aspect** trong ApplicationContext; bean aspect cũng phải được đăng ký/scan.
- Gọi **context.getBean("reportService")** trả về bean/proxy Spring quản lý. Gọi **new ReportService()** trực tiếp sẽ không tự expose **Trackable**.
- Mục tiêu là access interface bổ sung thông qua proxy; việc có interface mới không có nghĩa **ReportService.class** đã thay đổi bytecode hay **ReportService** đã khai báo **implements Trackable**.

## 7. Kiểu proxy ảnh hưởng thế nào đến cách truy cập?

Spring AOP có thể dùng **JDK dynamic proxy** hoặc **class-based proxy** (CGLIB). Trong cả hai trường hợp, khi Introduction được cấu hình hợp lệ, **interface mới là interface của proxy**.

| Kiểu proxy | Runtime type của proxy | Cách truy cập đáng tin |
| --- | --- | --- |
| **JDK dynamic proxy** | Một class động thực thi các interface được proxy expose | Sử dụng interface gốc và interface được introduce; **không** ép proxy về concrete target class |
| **CGLIB/class-based proxy** | Một subclass được tạo cho target class và implement các interface được cấu hình | Vẫn ưu tiên lập trình qua các interface; ép sang class cụ thể chỉ khi kiểu proxy và khả năng proxy hóa cho phép |

Ví dụ khi dùng JDK proxy:

~~~java
Object bean = context.getBean("reportService");
Trackable tracking = (Trackable) bean; // Đúng nếu Introduction áp dụng
~~~

Nhưng dạng sau không phải luôn hợp lệ:

~~~java
ReportService raw = (ReportService) bean; // Có thể ClassCastException với JDK proxy
~~~

Đây là các đoạn minh họa dùng môi trường của mục 6. Khi muốn dùng method gốc từ bean có JDK proxy, nên định nghĩa và inject thông qua interface nghiệp vụ thay vì dựa vào concrete implementation class.

**proxyTargetClass=true** khiến Spring ưu tiên class-based proxy trong những tình huống hỗ trợ; nó không làm target gốc tự implement interface mới và không khắc phục mọi hạn chế của final class/final method. **Spring AOP vẫn dùng proxy runtime**.

## 8. Kiểm tra một bean có được Introduction thực sự hay chưa

Spring có sẵn các tiện ích kiểm tra AOP proxy:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

// bean là instance lấy từ ApplicationContext:
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(Trackable.class.isInstance(bean));

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getSimpleName());
    }
}
~~~

Đây là đoạn chẩn đoán thêm vào một chương trình đã có **bean** và **Trackable**. Kết quả **isAopProxy=true** chỉ cho biết có proxy AOP, **không chứng minh interface cụ thể đã được introduce**. Phải dùng **isInstance/instanceof** với interface mục tiêu.

Để kiểm tra khác biệt giữa bean/proxy và target:
- **AopUtils.isAopProxy(bean)**: xác nhận object đang được Spring AOP proxy hóa.
- **Trackable.class.isInstance(bean)**: xác nhận bean runtime expose interface mới.
- **bean instanceof Advised**: nếu Spring cho phép introspection cấu hình AOP, có thể xem advisor liên quan.
- **Target object gốc**: có thể vẫn không implement interface mới; đây là hành vi đúng của Introduction.

Khi debug **IntroductionAdvisor**, nhớ rằng advisor công bố interface cho proxy còn interceptor/delegate đảm bảo method của interface mới thực sự có nơi triển khai. Nếu advisor công bố interface không tương thích interceptor, Spring có thể từ chối cấu hình trong giai đoạn tạo proxy.

Không dùng việc cast thành công để suy luận target object vốn thuộc interface đó: cast thành công chỉ phản ánh **runtime proxy** mà biến tham chiếu.

## 9. Lỗi phổ biến

### Lỗi 1: Ép kiểu target gốc thay vì proxy

~~~java
GreetingService target = new GreetingService();
Taggable taggable = (Taggable) (Object) target;
~~~

Code có thể compile nhờ cast trung gian sang **Object**, nhưng lúc chạy ném **ClassCastException** vì target không implement **Taggable**. Hãy lấy **proxy** do Spring AOP tạo hoặc bean đã được Spring quản lý.

### Lỗi 2: Tưởng khai báo @DeclareParents là đủ cho đối tượng tạo bằng new

**@DeclareParents** chỉ có hiệu lực khi infrastructure Spring AOP phát hiện aspect và tạo proxy cho bean được quản lý. Đối tượng tự tạo bằng **new** không được áp dụng Introduction nếu bạn không tự đưa nó qua cơ chế proxy tương ứng.

### Lỗi 3: Dùng sai type hoặc tên Spring bean

Interface mới xuất hiện ở runtime proxy. Nếu code inject theo concrete class trong khi proxy thực tế là **JDK dynamic proxy**, injection/cast có thể thất bại. Nếu **getBean("...")** trỏ sang bean không match type pattern của **@DeclareParents**, cũng không có interface mới. Kiểm tra đúng tên bean và kiểu thực tế của proxy.

### Lỗi 4: Hiểu nhầm Introduction chỉ là thêm một method vào class

Spring Introduction expose **một interface kèm triển khai**. Những method chỉ có trong class delegate mà **không nằm trong interface được introduce** không tự nhiên xuất hiện trên proxy như các method API công khai.

### Lỗi 5: Quên triển khai interface mới

Chỉ công bố interface không đủ: interceptor/delegate phải hỗ trợ lời gọi của nó. Trong API Spring AOP, **DefaultIntroductionAdvisor.validateInterfaces()** phục vụ kiểm tra tính tương thích với introduction advice. Đừng tạo delegate không implement interface mà advisor công bố.

### Lỗi 6: Dùng @AspectJ pattern sai

Pattern **value** của **@DeclareParents** là kiểu pattern của AspectJ. Tên bean hay glob của file không phải thay thế tương đương. Hãy xác minh class đúng package, khớp pattern và bean được Spring AOP quản lý.

### Lỗi 7: Lạm dụng Introduction cho dữ liệu động hoặc shared mutable state

Nếu implementation của interface mới có state, hãy xem xét **phạm vi chia sẻ instance** của interceptor/delegate hoặc chính sách instance theo target. Với một **DelegatingIntroductionInterceptor** được dùng chung cho nhiều proxy, delegate có thể là một object dùng chung. Không mặc định mỗi target có một trạng thái độc lập nếu chưa thiết kế rõ. Khi yêu cầu gắn state riêng theo từng target, cần cân nhắc hỗ trợ Spring như **DelegatePerTargetObjectIntroductionInterceptor** hoặc chiến lược tương ứng.

### Lỗi 8: Gọi method khác của target và kỳ vọng nó tự truy cập được interface mới

Bên trong target, **this** là target object gốc; không phải tự nhiên trở thành proxy có interface mới. **Self-invocation** cũng có thể bypass advice Spring AOP. Nếu muốn gọi feature mới, chuyển dependency/proxy phù hợp từ bên ngoài thay vì giả định ép kiểu **this** sẽ chạy.

## 10. Quy trình kiểm chứng khi giao tiếp với interface mới

1. **Kiểm tra bean source**: target được Spring quản lý hay do code tự tạo bằng **new**?
2. **Kiểm tra cấu hình Introduction**: có **IntroductionAdvisor** hoặc **@DeclareParents** phù hợp target không? Aspect được đăng ký và AOP được bật chưa?
3. **Kiểm tra đúng object**: tham chiếu client nhận được có phải chính AOP proxy được cấu hình không?
4. **Kiểm tra interface runtime**: **proxy instanceof NewInterface** trả true không?
5. **Chọn cách truy cập**: cast một lần, inject interface mới hoặc lấy bean theo tên và interface; tránh tự tạo target mới.
6. **Kiểm tra thực thi**: gọi method mới và quan sát delegate; gọi method nghiệp vụ gốc và quan sát target, xác nhận không nhầm hai nơi nhận lời gọi.

Một câu hỏi thực hành: **hai biến khác kiểu cùng trỏ một Spring AOP proxy thì có hai instance khác nhau không?** Không. Đoạn code **Greeting greeting = (Greeting) proxy; Taggable taggable = (Taggable) proxy;** trong ví dụ mục 4 chứng minh hai biến chỉ là **hai góc nhìn kiểu Java của cùng runtime object**, nên **greeting == taggable** là **true**.

## 11. Tài liệu Spring tham khảo

- **Spring Framework — Introductions với @DeclareParents**: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/introductions.html
- **Spring Framework — Introduction Advice trong AOP API**: https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html
- **DefaultIntroductionAdvisor Javadoc (6.2.14)**: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html
- **DelegatingIntroductionInterceptor Javadoc (6.2.14)**: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DelegatingIntroductionInterceptor.html
- **Spring Framework — Proxying mechanisms**: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html

**Ghi nhớ:** Interface được introduce là năng lực **của Spring AOP proxy tại runtime**. Muốn gọi phương thức mới, hãy giữ hoặc lấy đúng proxy, xác nhận nó expose interface đó, rồi sử dụng kiểu interface mới thông qua cast, Spring bean lookup hoặc dependency injection. Target gốc vẫn giữ nguyên class và các interface vốn có.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-005">IntroductionInterceptor và IntroductionAdvisor</a>

<details>
<summary>Xem chi tiết</summary>

# IntroductionInterceptor và IntroductionAdvisor trong Spring AOP

## 1. Hai vai trò hạ tầng của Spring introduction

Giả sử `OrderService` có `submitOrder()` còn client muốn sử dụng thêm `UsageCounter.incrementUseCount()` trên cùng Spring AOP proxy. **Introduction** làm proxy triển khai `UsageCounter`, nhưng không thay `OrderService.class` hay object target gốc.

Bài này đi vào hai hợp đồng Java đứng sau khả năng đó: **`IntroductionAdvisor` công bố interface được thêm và giới hạn target class**; **`IntroductionInterceptor` xử lý invocation của interface mới**, đồng thời có thể cho method nghiệp vụ tiếp tục tới target. Cần cả metadata proxy và implementation method: một `MethodInterceptor` bình thường không tự khiến proxy implements interface mới. Ví dụ mục 7 dựng chính hai thành phần này qua `ProxyFactory`.

## 2. Phân biệt với advice và pointcut thông thường

| Khía cạnh | `MethodInterceptor` qua `DefaultPointcutAdvisor` | Introduction qua `IntroductionInterceptor` + `IntroductionAdvisor` |
| --- | --- | --- |
| Mục tiêu chính | Thực hiện logic quanh phương thức vốn có trên proxy | Làm proxy implement interface bổ sung và thực thi phương thức của interface đó |
| Tiêu chí áp dụng | `Pointcut` gồm class filter và method matcher | `IntroductionAdvisor.getClassFilter()` chọn class; không có method matcher cho introduction |
| Phương thức mới | Không tự làm xuất hiện API mới trên proxy | `getInterfaces()` khai báo interface mới để proxy công bố |
| Khi invocation tới method mới | Không có implement trong target để `proceed()` tới | Introduction interceptor phải xử lý method, thường ủy quyền cho mixin |
| Khi invocation tới method cũ | Advice có thể làm việc rồi `proceed()` | Introduction interceptor có thể `proceed()` để giữ luồng gọi cũ |
| Trạng thái bổ sung | Có thể stateless hoặc stateful tùy advice | Mixin thường giữ trạng thái riêng, cần thiết kế vòng đời theo từng proxy/target |

Một `MethodInterceptor` đơn thuần, dù viết tốt, **không tự quảng bá interface mới**. Tương tự, chỉ cấu hình `IntroductionInterceptor` mà bỏ `IntroductionAdvisor` không tạo đủ metadata để proxy công bố interface cho client. Với API introduction cấp thấp, **hai vai trò này phải đi cùng nhau**.

## 3. Các interface Spring liên quan

Các type chính (rút gọn để thấy quan hệ; không phải một file Java tự biên dịch):

```java
// org.springframework.aop
public interface DynamicIntroductionAdvice extends Advice {
    boolean implementsInterface(Class<?> intf);
}

public interface IntroductionInterceptor
        extends MethodInterceptor, DynamicIntroductionAdvice {
    // invoke(MethodInvocation) kế thừa từ MethodInterceptor.
}

public interface IntroductionInfo {
    Class<?>[] getInterfaces();
}

public interface IntroductionAdvisor extends Advisor, IntroductionInfo {
    ClassFilter getClassFilter();
    void validateInterfaces() throws IllegalArgumentException;
}
```

Đọc theo hướng trách nhiệm:

1. `implementsInterface(Class<?>)` hỏi **advice có thực thi được interface cụ thể hay không**.
2. `MethodInterceptor.invoke(MethodInvocation)` cho phép advice nhận lời gọi. Khi phương thức thuộc interface mới, introduction advice phải cung cấp implementation; không thể coi `proceed()` tới target là đủ nếu target không có phương thức đó.
3. `IntroductionInfo.getInterfaces()` cho biết **advisor sẽ công bố những interface nào** trên proxy.
4. `IntroductionAdvisor.getClassFilter()` chọn class target áp dụng. Không có `MethodMatcher` cho introduction, vì khả năng implement một interface là thuộc tính ở **cấp type/proxy**.
5. `validateInterfaces()` kiểm tra advice có hỗ trợ những interface advisor khai báo; Spring gọi bước kiểm tra trong quá trình gắn introduction advisor.

**Hai tập interface không nhất thiết trùng nhau:** interceptor có thể hỗ trợ nhiều interface, nhưng advisor có quyền chỉ chọn một vài interface để đưa ra bên ngoài. Vì thế cần kiểm tra cả `implementsInterface()` và `getInterfaces()`, không suy đoán interface của proxy chỉ từ các interface mà delegate implement.

## 4. Vai trò cụ thể của IntroductionInterceptor

`IntroductionInterceptor` vừa là `MethodInterceptor` vừa là `DynamicIntroductionAdvice`. Đó là lý do nó có thể:

- Nhận invocation thông thường qua `invoke(...)`.
- Phát hiện invocation thuộc interface được thêm vào proxy.
- Chuyển invocation đó sang implementation của interface mới.
- Chuyển tiếp lời gọi không thuộc interface giới thiệu xuống chuỗi interceptor/target bằng `proceed()`.
- Tham gia logic quanh những lời gọi khác nếu việc mở rộng yêu cầu, chẳng hạn kiểm tra trạng thái “locked” để từ chối setter.

Spring cung cấp `org.springframework.aop.support.DelegatingIntroductionInterceptor`, thường là lựa chọn đầu tiên thay vì triển khai interface bằng tay. Có hai cách dùng:

**Cách 1 — Truyền một delegate khác:**

```java
// CounterImpl implements Counter; ví dụ rút gọn phần lắp ráp.
DelegatingIntroductionInterceptor interceptor =
        new DelegatingIntroductionInterceptor(new CounterImpl());
```

Lúc này interceptor phát hiện các interface mà delegate hỗ trợ và chuyển lời gọi introduced-method sang delegate.

**Cách 2 — Subclass chính là mixin:**

```java
// Mixin này cũng là implementation của interface được introduce.
class LockMixin extends DelegatingIntroductionInterceptor implements Lockable {
    private boolean locked;

    @Override
    public void lock() { locked = true; }

    @Override
    public void unlock() { locked = false; }

    @Override
    public boolean locked() { return locked; }
}
```

Constructor no-arg của `DelegatingIntroductionInterceptor` là `protected`, dành cho subclass. Khi subclass dùng mô hình này, delegate mặc định là chính instance subclass. `super.invoke(invocation)` đã xử lý routing giữa introduced methods và target methods trong trường hợp thông dụng. Có thể override `invoke` để thêm hành vi, nhưng cần giữ cơ chế routing đó.

Khi một delegate implement các interface nội bộ ngoài ý muốn, `suppressInterface(SomeInternalInterface.class)` loại chúng khỏi tập được giới thiệu. Việc ẩn bớt interface giúp API của proxy chỉ chứa khả năng thực sự muốn công bố.

## 5. Vai trò cụ thể của IntroductionAdvisor

`IntroductionAdvisor` là một `Advisor` đặc biệt. Những method quan trọng:

- `getAdvice()`: trả về instance advice, trong ví dụ là introduction interceptor.
- `getInterfaces()`: tập interface proxy sẽ công bố.
- `getClassFilter()`: chọn những **class target** đủ điều kiện.
- `validateInterfaces()`: phát hiện advisor công bố interface mà advice không thực hiện được.

Spring cung cấp `org.springframework.aop.support.DefaultIntroductionAdvisor` để tiết kiệm phần cài đặt. Cách cấu hình phổ biến:

```java
DelegatingIntroductionInterceptor mixin =
        new DelegatingIntroductionInterceptor(new CounterImpl());
DefaultIntroductionAdvisor advisor =
        new DefaultIntroductionAdvisor(mixin, Counter.class);
proxyFactory.addAdvisor(advisor);
```

Ở đây:

- `mixin` **thực hiện** các method của `Counter`.
- `advisor` **công bố** `Counter.class` cho proxy và giữ `mixin` trong `getAdvice()`.
- `proxyFactory.addAdvisor(...)` khiến cấu hình AOP biết phải thêm interface đó khi tạo proxy.

`DefaultIntroductionAdvisor` mặc định chấp nhận mọi class (`matches(Class<?>)` trả về `true`). Nếu muốn hạn chế theo loại target, có thể viết introduction advisor riêng hoặc subclass phù hợp để chỉ chấp nhận các target cần thiết. Đừng dùng một method pointcut như `execution(* *.foo(..))` để quyết định một type có được thêm interface hay không: introduction là quyết định cấp class.

`DefaultIntroductionAdvisor` cũng hỗ trợ `addInterface(Class<?>)`, `getInterfaces()`, `setOrder(int)` và `getOrder()`. Giá trị order ảnh hưởng thứ tự khi có các advisor khác; nếu mixin stateful, thứ tự advice vẫn cần xem xét cùng interceptor chain thực tế.

## 6. Minh họa đường đi của một lời gọi

```text
       raw target: JobService implements Job
                         |
                         v
          ProxyFactory.setTarget(raw target)
          ProxyFactory.setInterfaces(Job.class)
          addAdvisor(IntroductionAdvisor)
                         |
                         v
            Spring tạo Job + Counter proxy
                         |
               +---------+-----------+
               |                     |
      ((Job) proxy).run()   ((Counter) proxy).next()
               |                     |
         interceptor chain      interceptor chain
               |                     |
      non-introduced method   introduced method
               |                     |
      interceptor proceeds     introduction interceptor
               |                     |
         JobService.run()         CounterImpl.next()
```

Hai điều cần chú ý: (1) `JobService` **không** bị sửa thành implement `Counter`; (2) `CounterImpl` không nhất thiết là Spring bean, bởi `ProxyFactory` có thể nhận delegate khởi tạo bằng `new`. Tùy kiến trúc ứng dụng, delegate cũng có thể được quản lý qua container.

Nếu target tình cờ **đã implement cùng interface** mà advisor giới thiệu, introduction có thể che implementation của target đối với những method của interface ấy. Đây là một tình huống dễ gây hiểu nhầm: target có `next()` nhưng lời gọi trên proxy được định tuyến vào mixin. Tránh công bố introduction trùng interface nghiệp vụ trừ khi thật sự chủ ý thay đổi hành vi.

## 7. Ví dụ Java 17 chạy độc lập với Spring AOP ProxyFactory

Ví dụ tạo hai proxy riêng, mỗi proxy có một `Counter` mixin stateful riêng. Chỉ cần thư viện Spring AOP; không cần Spring Boot, `ApplicationContext` hay AspectJ weaving.

**Bước 1 — `pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-introduction-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.18</version>
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

**Bước 2 — `src/main/java/IntroductionDemo.java`**

```java
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

public class IntroductionDemo {

    // Giao diện nghiệp vụ mà target thực sự implement.
    public interface Job {
        String run();
    }

    // Giao diện mới chỉ được công bố trên Spring AOP proxy.
    public interface Counter {
        int next();
        int current();
    }

    public static final class JobService implements Job {
        private final String name;

        public JobService(String name) {
            this.name = name;
        }

        @Override
        public String run() {
            return "job:" + name;
        }
    }

    public static final class CounterImpl implements Counter {
        private final AtomicInteger value = new AtomicInteger();

        @Override
        public int next() {
            return value.incrementAndGet();
        }

        @Override
        public int current() {
            return value.get();
        }
    }

    private static Object createProxy(String name) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(new JobService(name));
        factory.setInterfaces(Job.class);

        DelegatingIntroductionInterceptor mixin =
                new DelegatingIntroductionInterceptor(new CounterImpl());

        DefaultIntroductionAdvisor advisor =
                new DefaultIntroductionAdvisor(mixin, Counter.class);
        factory.addAdvisor(advisor);

        return factory.getProxy();
    }

    public static void main(String[] args) {
        Object first = createProxy("alpha");
        Object second = createProxy("beta");

        System.out.println(((Job) first).run());
        System.out.println(((Counter) first).next());
        System.out.println(((Counter) first).next());
        System.out.println(((Counter) first).current());

        System.out.println(((Job) second).run());
        System.out.println(((Counter) second).current());

        System.out.println(first instanceof Counter);
        System.out.println(Counter.class.isInstance(new JobService("raw")));
    }
}
```

**Bước 3 — chạy**

```bash
mvn -q -Dexec.mainClass=IntroductionDemo compile exec:java
```

**Stdout dự kiến** (không kể log Maven):

```text
job:alpha
1
2
2
job:beta
0
true
false
```

**Phân tích từng điểm:**

1. `JobService` chỉ implement `Job`. Dòng cuối cho biết target gốc **không** implement `Counter`.
2. `first instanceof Counter` là `true`, chứng tỏ interface được công bố trên **proxy**.
3. `((Job) first).run()` chạy implementation gốc của `JobService`.
4. `((Counter) first).next()` đi qua `DelegatingIntroductionInterceptor` và gọi `CounterImpl`; target `JobService` không có `next()`.
5. `first` gọi `next()` hai lần nên `current() == 2`; `second` có delegate khác nên `current() == 0`. Đây là ví dụ **state riêng theo proxy** nhờ mỗi lần `createProxy` đều tạo một advisor và delegate mới.
6. `AtomicInteger` giữ bộ đếm an toàn khi nhiều luồng gọi cùng một proxy trong ví dụ; an toàn của **toàn bộ trạng thái đối tượng** vẫn là trách nhiệm thiết kế.

Ví dụ chủ động không sử dụng `@Aspect` hoặc proxy tự động để cô lập đúng hai API được học: `IntroductionInterceptor` và `IntroductionAdvisor`.

## 8. Khi viết IntroductionInterceptor tùy biến

Bạn có thể subclass `DelegatingIntroductionInterceptor` nếu muốn mixin vừa cung cấp method mới, vừa tác động tới method cũ. Ví dụ Spring thường dùng cơ chế `Lockable`: khi proxy đã lock thì không cho setter chạy.

Đoạn sau **chỉ minh họa subclass**, không phải file Java độc lập; `Lockable` là interface do ứng dụng định nghĩa:

```java
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

public class LockMixin extends DelegatingIntroductionInterceptor implements Lockable {
    private boolean locked;

    @Override
    public void lock() {
        locked = true;
    }

    @Override
    public void unlock() {
        locked = false;
    }

    @Override
    public boolean locked() {
        return locked;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        boolean setter = invocation.getMethod().getName().startsWith("set");
        if (locked && setter && !isMethodOnIntroducedInterface(invocation)) {
            throw new IllegalStateException("Target is locked");
        }
        return super.invoke(invocation);
    }
}

// Khi cấu hình ProxyFactory (ở mã tích hợp):
// factory.addAdvisor(new DefaultIntroductionAdvisor(new LockMixin(), Lockable.class));
```

Đường đi mong muốn:

- `lock()`, `unlock()`, `locked()` là method introduced, `super.invoke` đưa tới mixin.
- Với method nghiệp vụ thông thường, `super.invoke` tiếp tục xuống chuỗi interceptor và target.
- Khi đang lock, invocation tới setter nghiệp vụ bị chặn.
- Mỗi proxy cần **một `LockMixin` riêng** nếu trạng thái `locked` phải độc lập.

Không nên chỉ gọi `invocation.proceed()` cho method introduced khi target không implement interface, vì điều đó cố đẩy lời gọi tới nơi không có implementation và có thể ném lỗi. `DelegatingIntroductionInterceptor` đã xử lý trường hợp này.

**Lưu ý về state:** `locked` ở đây chưa có cơ chế đồng bộ. Nếu proxy được dùng đồng thời, hãy xem xét visibility, atomicity và quy tắc truy cập trạng thái; ví dụ này chỉ minh họa Spring introduction, không phải mẫu khóa tài nguyên production.

## 9. Vòng đời của mixin: per proxy, per target và chia sẻ advice

Introduction thường thêm trạng thái vào đối tượng được proxy, nên việc tái sử dụng một interceptor không đúng chỗ dễ tạo lỗi:

```text
Đúng khi muốn state độc lập:
proxy A -> advisor A -> mixin A (count = 2)
proxy B -> advisor B -> mixin B (count = 0)

Nguy hiểm nếu lỡ dùng chung:
proxy A ----+
            +--> một advisor / mixin (shared state)
proxy B ----+
```

Có ba mô hình cần phân biệt:

- **Một delegate cho mỗi proxy:** cách dùng `new DelegatingIntroductionInterceptor(new Delegate())` khi tạo từng proxy như ví dụ. Phù hợp với proxy gắn target cố định và state chỉ thuộc proxy đó.
- **Một delegate cho mỗi target object:** `DelegatePerTargetObjectIntroductionInterceptor` được Spring cung cấp cho tình huống trạng thái mixin cần gắn với **identity của target**. Điều này đặc biệt liên quan khi cấu hình target source động, một proxy có thể phục vụ nhiều target khác nhau.
- **Delegate được dùng chung:** có thể chấp nhận khi hành vi hoàn toàn stateless hoặc trạng thái được chủ đích dùng chung và đồng bộ đúng cách. Không dùng một mixin stateful được chia sẻ trên nhiều proxy nếu ngữ nghĩa cần độc lập.

Đừng nhầm `getTarget()` của `TargetSource` với delegate của introduction: `TargetSource` lựa chọn đối tượng nghiệp vụ được proxy gọi tới, còn introduction delegate trả lời những method của interface mới. Chúng có thể có vòng đời khác nhau.

Khi giới thiệu phương thức mới trên proxy request/session/prototype, hãy xác định rõ state thuộc **proxy**, **target hiện tại**, **request**, hay **session**. Một proxy sống lâu hơn nhiều target nhưng giữ mixin singleton có thể vô tình trộn trạng thái giữa người dùng hoặc request.

## 10. Khi dùng @DeclareParents thay cho API cấp thấp

Trong ứng dụng Spring sử dụng cú pháp `@AspectJ`, một con đường khác là `@DeclareParents`. Đây vẫn có thể là Spring AOP qua runtime proxy; annotation mô tả **interface cần introduce và implementation mặc định**. Spring chuyển cấu hình thành infrastructure introduction advisor phù hợp.

Ví dụ ý tưởng (các class/package cần tồn tại trong ứng dụng):

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

@Aspect
public class TrackingIntroduction {
    @DeclareParents(
        value = "com.example.service.*+",
        defaultImpl = DefaultTracked.class
    )
    public static Tracked mixin;
}
```

Ở đây `Tracked` là interface bổ sung và `DefaultTracked` là class hiện thực phù hợp. Chỉ những Spring bean khớp type pattern và được AOP proxy mới được công bố thêm giao diện. Muốn Spring xử lý `@Aspect` trong cấu hình Java thông thường, cần bật `@EnableAspectJAutoProxy` (hoặc auto-configuration tương ứng), đăng ký aspect làm Spring bean, đồng thời có dependencies AspectJ cần thiết cho cơ chế `@AspectJ`.

Lựa chọn:

| API | Phù hợp khi |
| --- | --- |
| `ProxyFactory` + `DefaultIntroductionAdvisor` + `IntroductionInterceptor` | Cần dựng proxy thủ công hoặc kiểm soát rõ delegate, class filter và vòng đời mixin |
| `@Aspect` + `@DeclareParents` | Đã sử dụng cú pháp `@AspectJ` trong Spring, muốn khai báo introduction cho nhóm bean theo type pattern |
| XML `<aop:declare-parents>` | Ứng dụng dùng schema AOP XML và cấu hình nhóm target theo `types-matching` |

**Không được suy ra** rằng Spring AOP ở trên sẽ biến object gốc ngoài Spring container thành interface mới. Runtime proxy chỉ thay đổi bề mặt type của **proxy được tạo/quản lý phù hợp**. Nếu ứng dụng cần biến đổi bytecode/class thực tế cho các object không qua proxy, đó là phạm vi AspectJ weaving, không phải API introduction proxy đã thảo luận.

## 11. Giới hạn của proxy và ảnh hưởng tới kiểu dữ liệu

- **Ép kiểu đúng đối tượng:** `((Counter) proxy).next()` hợp lệ khi proxy công bố `Counter`; `((Counter) rawTarget)` thất bại nếu raw target không implement interface.
- **JDK proxy:** client nhìn qua các interface Spring cấu hình, bao gồm interface gốc và introduced interfaces. Đừng kỳ vọng ép sang concrete class `JobService` sẽ thành công trên JDK dynamic proxy.
- **Class-based proxy:** có thể dựa vào subclass proxy, nhưng vẫn là proxy, không phải class target được sửa bytecode. Những hạn chế của proxy đối với class/method final hoặc non-overridable vẫn áp dụng cho việc advice thông thường.
- **Interface phải thực sự có thể dùng:** nên thiết kế interface được introduce là một contract rõ ràng và đảm bảo visibility/classloader tương thích. Không công bố API nội bộ vô tình qua delegate.
- **Method trùng tên/interface:** nếu target và mixin đều có cùng interface, implementation của introduction được ưu tiên trên proxy cho interface được giới thiệu. Phải xem đây là thay đổi hành vi có chủ đích.
- **Self invocation:** `this.someMethod()` trong target không quay lại proxy, nên những lời gọi nội bộ đó không tự áp dụng advice hay bề mặt interface của proxy một lần nữa.
- **Advisor class filter:** filter xét class target. Đừng áp một introduction cho class không đáp ứng contract nghiệp vụ mà phần mixin sẽ phụ thuộc.
- **Luồng đồng thời:** trạng thái do mixin nắm giữ phải được bảo vệ nếu một proxy được gọi từ nhiều luồng. Scope riêng của Spring bean không tự giải quyết mọi race trong mixin.

## 12. Những lỗi thường gặp và cách kiểm tra

| Dấu hiệu | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| Cast sang interface mới nhận `ClassCastException` | Client giữ raw target hoặc advisor không được gắn vào proxy | Kiểm tra `AopUtils.isAopProxy(bean)`, cấu hình `addAdvisor` và danh sách interface được công bố |
| `implementsInterface(Counter.class)` đúng nhưng proxy vẫn chưa cast được | Interceptor biết thực hiện interface, nhưng advisor chưa công bố interface đó | Đối chiếu `IntroductionAdvisor.getInterfaces()` và cấu hình proxy |
| Invocation của method introduced báo thiếu method ở target | Interceptor chuyển tiếp `proceed()` thay vì xử lý method mới | Dùng `DelegatingIntroductionInterceptor` hoặc route tới delegate đúng cách |
| `validateInterfaces()` thất bại | Advisor công bố một interface mà advice không implement nổi | Chỉ khai báo các interface phù hợp; dùng `suppressInterface` để ẩn các interface không muốn lộ |
| Hai proxy chia sẻ bộ đếm / trạng thái lock | Cùng sử dụng một mixin stateful | Tạo mixin/advisor theo từng proxy, hoặc chọn cơ chế delegate per target |
| Một method có advice khác hoạt động không như kỳ vọng | Thứ tự interceptor/advisor ảnh hưởng hành vi | Xem order và interceptor chain, test cả method introduced lẫn method nghiệp vụ |
| Introduction không áp dụng cho một bean | Class filter hoặc pattern `@DeclareParents` không khớp; bean không được proxy | Xác minh class thực, điều kiện khớp và bước tạo AOP proxy |
| Gọi raw instance vẫn không thấy interface mới | Introduction chỉ hiện trên proxy | Inject/lấy bean đã được Spring proxy hoặc dùng proxy `ProxyFactory.getProxy()` |
| Kỳ vọng giới thiệu field mới trực tiếp vào target | Spring AOP runtime proxy không thay class gốc | Lưu state ở mixin/delegate; cân nhắc cơ chế khác nếu bắt buộc đổi class thật |

Để chẩn đoán proxy đã tạo bằng Spring:

```java
// Mảnh mã tích hợp; giả sử "bean" là Spring AOP proxy đã tồn tại.
boolean proxy = org.springframework.aop.support.AopUtils.isAopProxy(bean);
boolean introduced = bean instanceof Counter;

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getSimpleName());
    }
}
```

Việc kiểm tra danh sách advisor cho biết cấu hình proxy thực nhận, nhưng không nên dựa vào tên class làm chứng cứ duy nhất: kiểm tra thêm `IntroductionAdvisor.getInterfaces()`, loại advice và kết quả gọi method mới.

## 13. Checklist trước khi đưa Introduction vào ứng dụng

1. Định nghĩa interface mới nhỏ, có ngữ nghĩa rõ ràng và thật sự cần được client nhìn thấy.
2. Đảm bảo mixin/delegate implement **đủ** các method contract, bao gồm xử lý ngoại lệ và thread safety.
3. Chọn lifecycle của mixin: mỗi proxy, mỗi target hay chia sẻ có chủ đích.
4. Chỉ công bố interface cần thiết qua `IntroductionAdvisor` và loại bỏ interface nội bộ.
5. Gắn advisor trước khi công bố proxy cho client; nếu động cập nhật advisor trên proxy đã sống, cẩn trọng vì khả năng interface của proxy đã được xác định lúc tạo.
6. Test cả lời gọi method nghiệp vụ lẫn method mới và kiểm tra cast trên proxy/raw target.
7. Nếu dùng `@DeclareParents`, xác nhận target pattern thực sự khớp và auto-proxy infrastructure đã bật.
8. Đánh giá interaction với `@Transactional`, security, caching và các advisor khác, đặc biệt nếu thứ tự advice ảnh hưởng hành vi.
9. Với target source động, xác định state của mixin cần gắn với proxy hay target, tránh rò rỉ trạng thái giữa nhiều target.

## 14. Tài liệu chính thức của Spring

- Spring Framework 6.2 — *Advice API: Introduction Advice*: https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html
- `IntroductionInterceptor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/IntroductionInterceptor.html
- `IntroductionAdvisor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/IntroductionAdvisor.html
- `DefaultIntroductionAdvisor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html
- `DelegatingIntroductionInterceptor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/support/DelegatingIntroductionInterceptor.html
- Spring Framework — *Introductions with @DeclareParents*: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html

**Ghi nhớ khi đọc mã Spring AOP:** `IntroductionInterceptor` cung cấp hành vi của interface mới; `IntroductionAdvisor` quyết định việc công bố interface và phạm vi class; proxy là đối tượng mang interface đó, còn target gốc vẫn giữ nguyên kiểu Java ban đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-006">DelegatingIntroductionInterceptor trong API Spring</a>

<details>
<summary>Xem chi tiết</summary>

# DelegatingIntroductionInterceptor trong API Spring AOP

## 1. Bài toán: thêm một interface vào bean qua Spring AOP proxy

Giả sử một `GreetingService` chỉ có nhiệm vụ chào người dùng. Ứng dụng cần một khả năng khác, chẳng hạn lưu một nhãn trạng thái `Tagged` trên *proxy* của service, trong khi không muốn sửa source code, cho service kế thừa class mới hoặc buộc tất cả implementation của nó biết về giao diện đó. **Introduction** (còn gọi là *mixin* trong tài liệu Spring AOP) cho phép Spring AOP proxy **công bố thêm interface** và cung cấp implementation cho các method thuộc interface ấy.

`org.springframework.aop.support.DelegatingIntroductionInterceptor` là lớp hỗ trợ trong API Spring AOP thực hiện phần chuyển lời gọi cho introduction. Nó giúp ta không phải tự phân biệt và thực thi từng `MethodInvocation` trên interface mới.

**Kết quả cần hiểu:** cùng một Spring AOP proxy có thể được nhìn qua hai interface:

- `GreetingService`: contract nghiệp vụ, lời gọi thông thường tiếp tục xuống target.
- `Tagged`: contract được giới thiệu, method thuộc nó được xử lý bởi delegate của `DelegatingIntroductionInterceptor`.

Đối tượng target gốc **không biến thành một instance `Tagged`**; khả năng mới nằm trên proxy. Vì thế phải lấy/gọi bean qua Spring AOP proxy, không trông chờ cast trực tiếp raw target. Đây là cơ chế **Spring AOP ở runtime**, không thay bytecode của class đích.

> Phạm vi bài học: API introduction trong Spring Framework, trọng tâm `DelegatingIntroductionInterceptor`, `IntroductionAdvisor`, `ProxyFactory` và cách tích hợp với hệ thống proxy của Spring. Không đi sâu lý thuyết AOP tổng quát hoặc biên dịch/weaving của AspectJ.

## 2. Vị trí của DelegatingIntroductionInterceptor trong Spring AOP

Các thành phần phối hợp:

| Thành phần Spring | Vai trò |
| --- | --- |
| `IntroductionInfo` | Mô tả các interface mà introduction công bố qua `getInterfaces()`. |
| `DynamicIntroductionAdvice` | Cho biết interceptor có thể implement một interface thông qua `implementsInterface(Class<?>)`. |
| `IntroductionInterceptor` | Kết hợp `MethodInterceptor` và `DynamicIntroductionAdvice`, nhận method invocation và phải xử lý invocation thuộc interface được giới thiệu. |
| `IntroductionInfoSupport` | Lớp cơ sở giữ tập interface đã công bố; có `suppressInterface(Class<?>)` và `isMethodOnIntroducedInterface(...)`. |
| **`DelegatingIntroductionInterceptor`** | Implementation tiện dụng của `IntroductionInterceptor`. Tự tìm interface mà delegate hỗ trợ và chuyển lời gọi tới delegate nếu method thuộc interface mới. |
| `IntroductionAdvisor` | Gắn introduction advice với tập interface được expose và `ClassFilter` để quyết định lớp target được áp dụng. |
| `DefaultIntroductionAdvisor` | Implementation thông dụng của `IntroductionAdvisor`; có constructor nhận interceptor và interface muốn introduce. |
| `ProxyFactory` / auto-proxy creator | Tạo proxy chứa cả interface nghiệp vụ và những interface mà IntroductionAdvisor công bố. |

Quan hệ rút gọn:

~~~text
client
  |
  v
Spring AOP proxy: GreetingService + Tagged
  |
  +-- gọi GreetingService.greet(...)
  |     DelegatingIntroductionInterceptor -> proceed() -> target GreetingService
  |
  +-- gọi Tagged.setTag(...) / Tagged.getTag()
        DelegatingIntroductionInterceptor -> delegate Tagged
        (không proceed tới target không implement Tagged)
~~~

### Tại sao không dùng MethodInterceptor thông thường?

Một `MethodInterceptor` có thể quan sát, thay đổi hoặc chặn method invocation, nhưng **chỉ interceptor đơn thuần không làm proxy bổ sung interface mới**. Trong Spring AOP, cần `IntroductionAdvisor` để proxy được tạo với interface mới; introduction advice sau đó xử lý lời gọi của interface ấy.

Introduction được áp dụng theo **cấp class**. `IntroductionAdvisor` có `ClassFilter`, nhưng **không mang `MethodMatcher`/`Pointcut` của một `PointcutAdvisor`**, vì mục tiêu của introduction là công bố thêm contract của object, không chỉ chọn method trong contract đã có. Sự phân biệt này là chủ ý của Spring API.

## 3. API quan trọng của DelegatingIntroductionInterceptor

Lớp Spring chính:

~~~java
org.springframework.aop.support.DelegatingIntroductionInterceptor
~~~

Kế thừa `IntroductionInfoSupport` và implement `IntroductionInterceptor`. Các điểm sử dụng chính:

| API | Công dụng và lưu ý |
| --- | --- |
| `new DelegatingIntroductionInterceptor(Object delegate)` | Delegate bên ngoài implement interface muốn thêm. Lớp interceptor tự dò các interface delegate implement, ngoại trừ interface hạ tầng `IntroductionInterceptor`. |
| `protected DelegatingIntroductionInterceptor()` | Dành cho subclass; delegate mặc định là `this`, chính subclass phải implement interface được introduce. Không khởi tạo trực tiếp ngoài subclass bằng no-arg constructor vì nó là `protected`. |
| `invoke(MethodInvocation invocation)` | Với method của interface được introduce, gọi method trên delegate; với method thông thường, tiếp tục xuống target qua chain. |
| `protected doProceed(MethodInvocation invocation)` | Hook cho subclass muốn quan sát/điều chỉnh method **đi xuống target**, không được gọi cho method của introduced interface. |
| `getInterfaces()` | Trả tập interface được advice công bố; advisor cuối cùng quyết định interface nào thực sự xuất hiện trên proxy. |
| `implementsInterface(Class<?> intf)` | Trả lời liệu introduction advice hỗ trợ interface được hỏi. |
| `suppressInterface(Class<?> intf)` | Loại interface khỏi danh sách công bố khi delegate tình cờ implement interface không nên expose. |

### Constructor nhận delegate

~~~java
Tagged implementation = new MutableTag();
var interceptor = new DelegatingIntroductionInterceptor(implementation);
var advisor = new DefaultIntroductionAdvisor(interceptor, Tagged.class);
~~~

Đoạn này minh họa wiring khi đã có `Tagged` và `MutableTag`; mã chạy độc lập được trình bày ở mục 5. `MutableTag` chịu trách nhiệm thực hiện `Tagged.setTag/getTag`. Interceptor cung cấp cơ chế chuyển tiếp; advisor cho biết interface nào được công bố ra proxy.

### Constructor cho subclass

~~~java
// Fragment minh họa API, sử dụng interface Tagged của mục 5.
class TaggedMixin extends DelegatingIntroductionInterceptor implements Tagged {
    private String tag = "none";

    @Override
    public void setTag(String value) {
        tag = value;
    }

    @Override
    public String getTag() {
        return tag;
    }
}
~~~

Trong constructor mặc định (được kế thừa và truy cập từ subclass), delegate là instance `TaggedMixin` hiện tại, nên `Tagged` được nhận diện tự động. Một trường hợp phải cẩn thận: vì Java interface có thể kế thừa nhau, delegate có thể công bố nhiều interface hơn mong muốn. Hãy giới hạn bằng một `IntroductionAdvisor` khai báo rõ interface cần expose, và sử dụng `suppressInterface(...)` khi cần.

## 4. Quy tắc xử lý phương thức: delegate hay proceed?

`DelegatingIntroductionInterceptor.invoke(...)` đi theo ý tưởng:

1. Spring AOP proxy nhận lời gọi.
2. Interceptor kiểm tra method có thuộc interface introduction mà nó công bố hay không.
3. **Nếu có:** gọi method tương ứng trên delegate; không gọi tiếp `proceed()` để target thường xuyên thiếu interface ấy nhận method.
4. **Nếu không:** tiếp tục invocation chain bằng `doProceed(...)`; implementation mặc định dùng `MethodInvocation.proceed()`.

Mã giả để hiểu quyết định, **không phải source code Spring hoặc một implementation thay thế có thể drop-in**:

~~~java
Object invokeConceptually(MethodInvocation invocation) throws Throwable {
    if (isIntroducedMethod(invocation)) {
        return invokeMethodOnDelegate(invocation);
    }
    return doProceed(invocation); // Mặc định: invocation.proceed()
}
~~~

Phần xử lý invocation thực tế có chi tiết của Spring (ví dụ reflection, đối tượng trả về, exception, interface metadata). Không nên copy đoạn mã giả thành custom interceptor vì dễ bỏ qua các invariant của Spring.

**Điểm cần nhớ:** method introduction có thể cùng tên với method sẵn có ở target nhưng là contract của *interface được introduce*. Nếu hai interface cùng có method signature, hãy tránh thiết kế nhập nhằng: interface trên proxy và luật resolve method/chain có thể khiến kỳ vọng định tuyến không đúng. Tài liệu Spring cũng nói introduction của một interface có thể che implementation cùng interface ở target. Cách an toàn là đặt contract bổ sung khác biệt và kiểm thử lời gọi qua mọi kiểu reference.

## 5. Ví dụ Java 17+ chạy được: hai interface trên một Spring AOP proxy

Mục tiêu kiểm chứng:

- Target chỉ implement `Greeter`.
- Proxy implement cả `Greeter` và `Tagged`.
- Lời gọi `greet` chạy ở target; `setTag/getTag` chạy ở delegate.
- Tạo hai proxy với **hai delegate độc lập** nên trạng thái nhãn không bị chia sẻ.

Ví dụ dùng Spring AOP API `ProxyFactory` để tập trung vào introduction, không yêu cầu Spring Boot hoặc chạy server.

Cấu trúc:

~~~text
spring-introduction-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/IntroductionDemo.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-introduction-demo'
~~~

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
    implementation platform('org.springframework:spring-framework-bom:6.2.14')
    implementation 'org.springframework:spring-aop'
}

application {
    mainClass = 'demo.IntroductionDemo'
}
~~~

`src/main/java/demo/IntroductionDemo.java`:

~~~java
package demo;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

public class IntroductionDemo {

    // Contract nghiệp vụ của target.
    public interface Greeter {
        String greet(String name);
    }

    // Contract mới mà ta muốn công bố từ proxy.
    public interface Tagged {
        void setTag(String tag);
        String getTag();
    }

    public static final class PlainGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    // Implementation mixin nằm riêng khỏi target.
    public static final class MutableTag implements Tagged {
        private String tag = "none";

        @Override
        public void setTag(String tag) {
            this.tag = tag;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }

    private static Greeter newGreeterProxy() {
        ProxyFactory factory = new ProxyFactory(new PlainGreeter());

        var delegate = new MutableTag();
        var interceptor = new DelegatingIntroductionInterceptor(delegate);
        var introductionAdvisor =
                new DefaultIntroductionAdvisor(interceptor, Tagged.class);

        factory.addAdvisor(introductionAdvisor);
        return (Greeter) factory.getProxy();
    }

    public static void main(String[] args) {
        Greeter first = newGreeterProxy();
        Greeter second = newGreeterProxy();

        System.out.println("business: " + first.greet("Lan"));
        System.out.println("introduced: " + (first instanceof Tagged));

        Tagged firstTag = (Tagged) first;
        firstTag.setTag("vip");
        System.out.println("first tag: " + firstTag.getTag());
        System.out.println("second tag: " + ((Tagged) second).getTag());

        Object rawTarget = new PlainGreeter();
        System.out.println("raw is tagged: " + (rawTarget instanceof Tagged));
    }
}
~~~

Chạy trong thư mục dự án với JDK 17+ và Gradle (nếu có Gradle Wrapper thì dùng `./gradlew run` trên Unix/macOS hoặc `gradlew.bat run` trên Windows; nếu không, dùng `gradle run`). Stdout của **ứng dụng** (bỏ log Gradle):

~~~text
business: Hello, Lan
introduced: true
first tag: vip
second tag: none
raw is tagged: false
~~~

**Giải thích từng bước:**

1. `new ProxyFactory(new PlainGreeter())` nhận target có contract `Greeter`. Khi tạo proxy, Spring có thể expose interface target và những interface đến từ introduction advisor.
2. `new DelegatingIntroductionInterceptor(new MutableTag())` nhận `MutableTag` làm delegate. Interceptor biết delegate hỗ trợ `Tagged` nhờ metadata từ `IntroductionInfoSupport`.
3. `new DefaultIntroductionAdvisor(interceptor, Tagged.class)` chỉ rõ Spring AOP cần công bố `Tagged`. Không chỉ có `MethodInterceptor` là đủ; chính advisor mang metadata introduction.
4. `factory.addAdvisor(...)` trước `getProxy()` giúp Spring xây proxy với các interface mong muốn. Ép kiểu proxy sang `Tagged` thành công; ép kiểu raw `PlainGreeter` sang `Tagged` không hợp lệ vì target không implement interface đó.
5. `first.greet(...)` là lời gọi nghiệp vụ; interceptor cho phép chain đi xuống `PlainGreeter`. `firstTag.setTag(...)` và `firstTag.getTag()` được delegate `MutableTag` phục vụ, target không phải biết method mới.
6. Mỗi lần `newGreeterProxy()` tạo **một MutableTag** và **một interceptor/advisor**. Vì vậy sửa tag của `first` không làm tag của `second` thay đổi.

**Giới hạn diễn giải:** ví dụ cố định một proxy cho một target và tránh cạnh tranh đa luồng. `MutableTag` là mutable state đơn giản, không thread-safe. Muốn đưa vào ứng dụng nhiều thread, phải xác định đúng thời gian sống và bảo vệ trạng thái theo yêu cầu thực tế.

## 6. Cách dùng subclass làm chính delegate

Khi muốn gói logic mixin và interceptor trong cùng một class, cách Spring Reference minh họa là subclass `DelegatingIntroductionInterceptor` và implement interface mới:

~~~java
// Fragment dùng Tagged từ ví dụ trước; không phải chương trình độc lập.
public static class TaggedMixin
        extends DelegatingIntroductionInterceptor
        implements Tagged {

    private String tag = "none";

    @Override
    public void setTag(String value) {
        this.tag = value;
    }

    @Override
    public String getTag() {
        return tag;
    }
}

// Khi dựng một proxy riêng:
TaggedMixin mixin = new TaggedMixin();
DefaultIntroductionAdvisor advisor =
        new DefaultIntroductionAdvisor(mixin, Tagged.class);
factory.addAdvisor(advisor);
~~~

Không cần viết `invoke(...)` chỉ để chuyển lời gọi `Tagged`. `DelegatingIntroductionInterceptor` đã làm việc này; ưu tiên constructor có delegate riêng khi composition làm trách nhiệm rõ hơn.

### Tùy biến `invoke(...)` khi thật sự cần

Một introduction có thể còn quan sát lời gọi **trên target**. Ví dụ thường gặp trong tài liệu Spring là `LockMixin`: ngoài việc cung cấp các method `lock()`/`unlock()`/`locked()`, nó chặn các setter nghiệp vụ khi đang locked. Đây là lý do có thể cần override `invoke(...)`.

Ví dụ minh họa một phần lớp mixin, giả định `isLocked()` là hàm đọc trạng thái và `LockedException` là exception của ứng dụng:

~~~java
@Override
public Object invoke(MethodInvocation invocation) throws Throwable {
    if (isLocked() && invocation.getMethod().getName().startsWith("set")) {
        throw new LockedException();
    }
    return super.invoke(invocation);
}
~~~

Mã trên là **fragment**; cần một subclass, interface trạng thái, exception và import `org.aopalliance.intercept.MethodInvocation` đầy đủ. Chú ý `super.invoke(invocation)` quan trọng: nó giúp các method thuộc interface mới tiếp tục được delegate. Nếu thay bằng `invocation.proceed()` cho tất cả trường hợp, introduction có thể hỏng khi target không có implementation của interface mới.

### Khác biệt với `doProceed(...)`

`doProceed(MethodInvocation)` là extension hook được Spring Javadoc mô tả để tùy biến đường **chạy xuống target**. Nó **không chạy** cho method thuộc interface introduction. Ví dụ fragment ghi log trước khi đi đến method target:

~~~java
@Override
protected Object doProceed(MethodInvocation invocation) throws Throwable {
    System.out.println("target method: " + invocation.getMethod().getName());
    return super.doProceed(invocation);
}
~~~

Không kỳ vọng đoạn này ghi log khi gọi `Tagged.getTag()`. Muốn bao cả hai nhánh thì xem xét override `invoke(...)` nhưng vẫn phải gọi `super.invoke(...)` để bảo toàn định tuyến.

## 7. Tự động áp dụng cho bean trong Spring container

Ví dụ `ProxyFactory` chủ động dựng proxy. Trong Spring container, auto-proxy creator có thể áp dụng introduction advisor lên các bean phù hợp. Điều cần hiểu:

1. Đăng ký một `IntroductionAdvisor` để Spring nhận biết interface được công bố.
2. Lọc **class** sẽ nhận introduction (nếu cần), vì introduction advisor có `ClassFilter`.
3. Bảo đảm advice có trạng thái không bị chia sẻ sai giữa các proxy. Một advisor singleton chứa một `DelegatingIntroductionInterceptor` có delegate mutable có thể làm nhiều bean chia sẻ trạng thái.
4. Lấy bean qua `ApplicationContext`/proxy, không tự `new` target rồi hy vọng target có interface introduced.
5. Test type của bean sau khởi tạo và test các method của cả interface gốc và mới.

Một `DefaultIntroductionAdvisor` mặc định có class filter phù hợp với mọi class; trong ví dụ `ProxyFactory` ta tự quyết định proxy nào được dựng nên không có rủi ro quét container. Nếu đưa advisor rộng đó vào auto-proxy setup toàn hệ thống, phạm vi áp dụng phải được kiểm soát. Để điều kiện theo class, dùng implementation `IntroductionAdvisor` có `ClassFilter` phù hợp hoặc cấu hình pointcut declarative introduction như `@DeclareParents` khi khớp nhu cầu.

Đừng giả định chỉ cần một `@Bean` trả `DefaultIntroductionAdvisor` là auto-proxy chắc chắn hoạt động trong mọi Spring ApplicationContext: phải có cơ chế auto-proxy được kích hoạt đúng cách, và integration với nhiều bean/state cần được thiết kế, thử nghiệm. `ProxyFactory` ở mục 5 loại bỏ sự phụ thuộc đó để minh họa chính xác API.

## 8. Giới hạn trạng thái: một interceptor cho nhiều target có thể gây lỗi

`DelegatingIntroductionInterceptor` được dùng chung như một object bình thường nếu tự cấu hình như vậy. Với mutable delegate, **chia sẻ cùng instance advisor/interceptor** cũng là chia sẻ state của delegate. Ví dụ anti-pattern:

~~~java
// Minh họa sai khi Tagged lưu trạng thái riêng cho từng proxy:
MutableTag sharedState = new MutableTag();
var sharedInterceptor =
        new DelegatingIntroductionInterceptor(sharedState);
var sharedAdvisor =
        new DefaultIntroductionAdvisor(sharedInterceptor, Tagged.class);

// Gắn sharedAdvisor vào hai ProxyFactory khác nhau:
// Khi đổi tag ở proxy A, proxy B nhìn thấy cùng sharedState.
~~~

Đây không phải lỗi Spring AOP; đây là hệ quả bình thường của việc dùng cùng một object stateful.

Có ba cách triển khai tùy mục tiêu:

| Mục tiêu | Cách xử lý |
| --- | --- |
| Introduction stateless, dùng được trên nhiều proxy | Có thể dùng delegate stateless chia sẻ nếu implementation thực sự không mang state riêng theo target. |
| Mỗi proxy giữ state độc lập | Tạo interceptor/delegate/advisor mới cho mỗi proxy như ví dụ mục 5. |
| Mỗi target cần implementation introduction riêng, nhất là khi chạy trong auto-proxy | Xem `DelegatePerTargetObjectIntroductionInterceptor` của Spring AOP, được thiết kế tạo delegate theo target; kiểm tra constructor và lifecycle tương thích phiên bản Spring đang dùng. |

`DelegatePerTargetObjectIntroductionInterceptor` là một **implementation khác** của `IntroductionInterceptor`, không phải option bật bằng flag trên `DelegatingIntroductionInterceptor`. Không tự giả định nó có cùng vòng đời hay cùng cách làm sạch state; khi cần, đọc Javadoc/version source và kiểm thử target replacement, scope cùng concurrency.

### State lifecycle cần rõ ràng

Trong ví dụ `Tagged`, state là chuỗi ngắn và tồn tại cùng proxy. Với object thực tế, delegate có thể giữ cache, reference tới tài nguyên, kết nối hoặc thông tin request. Mặc định việc introduce interface **không tự tạo cleanup callback** cho delegate. Người viết code phải chỉ rõ ai tạo, ai đóng và thời điểm đóng; không cất request-specific state vào delegate được dùng chung giữa nhiều user. Nếu delegate cần xem target hiện tại, `DelegatingIntroductionInterceptor` không tự động đồng bộ state với target; chọn extension pattern phù hợp và tránh reference vòng đời dài không cần thiết.

## 9. Khác biệt giữa DelegatingIntroductionInterceptor và @DeclareParents

Spring hỗ trợ introduction ở hai tầng:

| API | Cách khai báo | Phù hợp khi |
| --- | --- | --- |
| `DelegatingIntroductionInterceptor` + `DefaultIntroductionAdvisor` | Java/Spring AOP SPI; tự tạo advisor và wiring proxy/auto-proxy | Muốn kiểm soát delegation, metadata, class filter hoặc state của mixin bằng Java |
| `@DeclareParents` trong `@Aspect` | Khai báo interface mới, pattern lớp được match và `defaultImpl` | Muốn introduction declarative trong cấu hình `@AspectJ` của Spring |

Ví dụ minh họa **Spring `@AspectJ` introduction**:

~~~java
// Fragment; cần AspectJ annotations, cấu hình auto-proxy phù hợp,
// interface Tagged và implementation MutableTag không-args constructor.
@Aspect
public class TagIntroductionAspect {

    @DeclareParents(
        value = "com.example.service.*+",
        defaultImpl = MutableTag.class
    )
    public static Tagged tagged;
}
~~~

`@DeclareParents` là annotation AspectJ được Spring AOP sử dụng để tổ chức proxy introduction. Trong Spring AOP, nó **không biến object gốc thành class kế thừa Java mới**, và không tạo ra woven field trong bytecode business class. Spring chỉ cung cấp interface thông qua proxy đối với bean khớp type pattern. Đừng suy rằng implementation decorator xây tay ở mục 5 tự động áp dụng cho cả package: nó chỉ ảnh hưởng `ProxyFactory` nơi ta gọi `addAdvisor(...)`.

Nếu chỉ muốn thêm interface theo expression pointcut có sẵn, `@DeclareParents` thường dễ đọc hơn. Nếu cần trực tiếp thực thi Spring AOP SPI, `DelegatingIntroductionInterceptor` cho phép quản lý delegate và advisor cụ thể.

## 10. Các rủi ro kỹ thuật khi triển khai

### 10.1. Cast raw target, không phải proxy

~~~java
Greeter original = new PlainGreeter();
// Không hợp lệ: raw target không tự implement Tagged.
Tagged wrong = (Tagged) original;
~~~

Đây là **mã sai có chủ ý**: compiler có thể bác bỏ hoặc runtime ném `ClassCastException` tùy kiểu compile-time. Chỉ proxy được advisor giới thiệu interface mới thì việc cast sang `Tagged` mới có căn cứ.

### 10.2. Chỉ thêm MethodInterceptor mà không dùng IntroductionAdvisor

Nếu dùng `DefaultPointcutAdvisor` cho một `DelegatingIntroductionInterceptor`, cấu trúc pointcut advisor **không công bố introduced interfaces**; proxy không thể được giả định `instanceof Tagged`. Hãy dùng đúng `DefaultIntroductionAdvisor` hoặc custom `IntroductionAdvisor`.

### 10.3. Gọi proceed() khi method thuộc interface mới

Target không có method `Tagged.getTag()`. Introduction interceptor phải tự xử lý bằng delegate. Khi override `invoke`, gọi `super.invoke` để nó định tuyến; đừng gọi `proceed` cho mọi invocation.

### 10.4. Delegate implement nhiều interface và làm lộ interface nội bộ

Ví dụ delegate đồng thời implement `Tagged` và `InternalAdmin`, nhưng consumer chỉ được thấy `Tagged`. Giới hạn interface bằng `DefaultIntroductionAdvisor(interceptor, Tagged.class)`. Nếu đang dùng advisor lấy metadata từ `IntroductionInfoSupport.getInterfaces()`, gọi `suppressInterface(InternalAdmin.class)` trên interceptor để loại bỏ interface không chủ đích.

Cần phân biệt **interceptor hỗ trợ interface nào** với **advisor cho proxy expose interface nào**; cả hai điều kiện đều phải khớp. Đừng expose interface mà implementation không hỗ trợ: `IntroductionAdvisor.validateInterfaces()` có trách nhiệm xác nhận compatibility và có thể báo lỗi sớm.

### 10.5. Dùng advisor singleton giữ mutable delegate cho mọi bean

Khi một advisor/interceptor/delegate được sử dụng nhiều lần, state có thể bị chia sẻ; ví dụ hai request điều chỉnh chung trạng thái `locked` hoặc `tag`. Áp dụng mỗi proxy một delegate, hoặc chọn implementation per-target phù hợp, hoặc thiết kế delegate stateless và thread-safe.

### 10.6. Kỳ vọng Spring AOP áp dụng trên object tự tạo hay self-invocation

Spring AOP advice áp dụng khi lời gọi **đi qua proxy**. Tạo target bằng `new`, gọi thẳng target reference hoặc self-invocation bên trong target không tự biến thành lời gọi qua proxy introduction. Điều này đặc biệt quan trọng khi application có những code path trao đổi raw reference.

### 10.7. Nhầm lẫn với Spring AOP giới hạn method matching

Introduction công bố *interface* ở mức class; nó không thích hợp để giới thiệu một method mới lên proxy mà không có interface khai báo method. Nếu muốn áp dụng logging, transaction hoặc check quyền trên method sẵn có, dùng `@Aspect` hoặc `Advisor`/`Pointcut` thông thường. Với introduction, consumer nên phụ thuộc interface mới và được Spring cấp proxy tương ứng.

### 10.8. Lưu state thread/request trong instance dài hạn

Một Spring singleton proxy có thể phục vụ nhiều thread đồng thời. Delegate mutable đơn giản như `MutableTag` ở ví dụ **không an toàn cho case này**. Cần quyết định state thuộc per-proxy, per-target, per-request hay per-invocation rồi triển khai lifecycle/đồng bộ phù hợp. `DelegatingIntroductionInterceptor` không tự quản lý scope đó.

## 11. Kiểm thử đúng đường đi của Spring AOP introduction

Checklist hữu ích khi review mã có `DelegatingIntroductionInterceptor`:

| Kiểm thử | Ý nghĩa |
| --- | --- |
| `proxy instanceof Greeter` và `proxy instanceof Tagged` | Proxy công bố cả contract gốc và interface introduce. |
| `rawTarget instanceof Tagged` là `false` | Introduction chỉ thuộc proxy, target gốc không bị biến đổi. |
| `proxy.greet(...)` gọi đúng target | Đường method không thuộc introduction tiếp tục qua chain. |
| `((Tagged) proxy).setTag/getTag(...)` dùng delegate | Đường introduced method được xử lý mà không đòi target hỗ trợ. |
| Hai proxy có hai delegate riêng | State của mixin không bị lẫn. |
| Advisor chỉ công bố interface được phép | `suppressInterface`/tập interface của advisor hoạt động như kỳ vọng. |
| Tạo proxy bằng container với auto-proxy | Cơ chế auto-proxy thực sự được kích hoạt; bean nhận advisor đúng phạm vi. |
| Thread đồng thời hoặc bean có scope khác nhau | Không race hoặc lộ state giữa request/target. |
| Cùng nhiều advisor/aspect khác | Không phá chain và không đổi thứ tự ngoài yêu cầu. |
| Nâng Spring Framework | Compile, chạy test cast/dispatch/state với version hiện dùng và version đích. |

Một assert đáng tin cần **gọi cả method nghiệp vụ và method của interface được introduce**, không chỉ kiểm tra `getAdvisors().length`. Việc có advisor không chứng minh proxy expose đúng interface, và việc `instanceof Tagged` cũng chưa chứng minh delegate xử lý đúng nghiệp vụ hoặc không chia sẻ state.

## 12. Chọn giải pháp và ghi nhớ

- Dùng **`DelegatingIntroductionInterceptor(delegate)`** khi cần đóng gói interface implementation riêng, dễ test, và đưa vào proxy bằng **`DefaultIntroductionAdvisor`**.
- Dùng **subclass `DelegatingIntroductionInterceptor`** khi class mixin trực tiếp implement interface mới và muốn hook `invoke(...)`/`doProceed(...)`; vẫn phải bảo toàn đường `super.invoke(...)` cho introduced methods.
- Dùng **`@DeclareParents`** khi muốn mô tả introduction declarative trong hệ Spring `@AspectJ`.
- Dùng **`DelegatePerTargetObjectIntroductionInterceptor`** khi chủ đích cần delegate khác nhau theo target; kiểm tra vòng đời và cách chọn loại proxy.

**Điểm cốt lõi:** `DelegatingIntroductionInterceptor` xử lý **method dispatch** của interface mới; `IntroductionAdvisor` công bố interface và phạm vi áp dụng trên Spring AOP proxy; **delegate** thực hiện hành vi, **target** tiếp tục thực hiện các method sẵn có. Chọn lifecycle/state của delegate đúng với lifecycle của proxy hoặc target là yêu cầu thiết yếu khi dùng introduction trong ứng dụng Spring.

## Tài liệu Spring để đối chiếu

- [Spring Framework Reference 6.2 — Advice API: Introduction Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html)
- [Spring Framework 6.2 — DelegatingIntroductionInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DelegatingIntroductionInterceptor.html)
- [Spring Framework 6.2 — DefaultIntroductionAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html)
- [Spring Framework 6.2 — IntroductionInfoSupport Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/IntroductionInfoSupport.html)
- [Spring Framework 6.2 — DelegatePerTargetObjectIntroductionInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DelegatePerTargetObjectIntroductionInterceptor.html)
- [Spring Framework 6.2 — @AspectJ Introductions](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/introductions.html)

Khi dự án dùng Spring Framework khác phiên bản minh họa, hãy đọc Javadoc đúng version và kiểm chứng integration với Spring Boot/auto-proxy configuration đang thực tế chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-007">Trạng thái của phần triển khai bổ sung và phạm vi chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP Introduction: Trạng thái của phần triển khai bổ sung và phạm vi chia sẻ

## 1. Mục tiêu học tập

Trong Spring AOP, **Introduction** cho phép một **proxy** triển khai thêm interface mà class target ban đầu không triển khai. Phần hiện thực method của interface mới thường do một **delegate/mixin** hoặc một `IntroductionInterceptor` cung cấp. Nếu delegate có field mutable, câu hỏi quan trọng không chỉ là “proxy có interface mới không?” mà còn là:

**Trạng thái của phần triển khai mới được chia sẻ giữa những proxy, những target và những lời gọi nào?**

Bài học tập trung vào **Spring AOP Introduction API**, đặc biệt:

- `org.springframework.aop.IntroductionAdvisor` và `DefaultIntroductionAdvisor`.
- `org.springframework.aop.IntroductionInterceptor` và `DelegatingIntroductionInterceptor`.
- `DelegatePerTargetObjectIntroductionInterceptor` và việc tách state theo **target object**.
- Vòng đời của advice/advisor khi áp dụng vào một hay nhiều Spring AOP proxy.
- Kiểm soát mutable state, concurrent calls, bean scope, target source, proxy reuse và giới hạn của Spring proxy.

Sau bài, bạn có thể viết chương trình Java 17+ để minh họa **state chung** và **state riêng theo target** mà không phải sửa business class.

> Phạm vi: chỉ phân tích introduction trong **Spring Framework AOP**. Các mô hình AspectJ weaving hoặc khái niệm AOP chung chỉ được nhắc khi cần đối chiếu cách Spring làm việc.

## 2. Interface mới được giới thiệu vào proxy, không vào target class

Giả sử một class nghiệp vụ chỉ biết API này:

~~~java
public interface OrderService {
    String submit(String orderId);
}

public class OrderServiceImpl implements OrderService {
    @Override
    public String submit(String orderId) {
        return "accepted:" + orderId;
    }
}
~~~

Hai khai báo là fragment minh họa, cần đặt trong các file Java tương ứng. Ban đầu, `OrderServiceImpl` **không** implements `AuditCounter`.

Một introduction có thể khiến **Spring AOP proxy** của `OrderServiceImpl` triển khai thêm:

~~~java
public interface AuditCounter {
    int increment();
    int current();
}
~~~

Khi Spring cấu hình introduction hợp lệ, cùng một reference proxy có thể dùng như:

~~~java
OrderService service = /* proxy do Spring AOP tạo */;
AuditCounter counter = (AuditCounter) service;

System.out.println(service.submit("O-1"));
System.out.println(counter.increment());
~~~

Đây là fragment: `service` phải là **proxy đã cấu hình introduction**, không phải `new OrderServiceImpl()` trực tiếp. Dù ép kiểu thành công, `OrderServiceImpl.class` không được Spring sửa bytecode để tự implements `AuditCounter`. Interface được expose bởi object **proxy**.

Sơ đồ:

~~~text
Caller giữ một Spring AOP proxy
                |
        +-------+--------+
        |                |
        v                v
   OrderService      AuditCounter
   (business API)    (introduced API)
        |                |
        v                v
     Target          IntroductionInterceptor
  OrderServiceImpl           |
                             v
                      Delegate/Mixin + state
~~~

Hai đường gọi cùng đến một proxy, nhưng được xử lý khác nhau. Các method introduction được delegate xử lý; các method business thông thường được đưa tiếp đến target qua interceptor chain.

## 3. Trạng thái nằm ở đâu?

Khi một introduced interface có field mutable, **nơi chứa field** quyết định phạm vi chia sẻ; không thể suy luận state “tự động thuộc từng proxy” chỉ vì proxy là một object riêng.

| Vị trí state | Phạm vi chia sẻ thực tế |
| --- | --- |
| Field trong một delegate duy nhất, được một `DelegatingIntroductionInterceptor` dùng chung | Tất cả lời gọi đi qua **cùng delegate**, có thể bao gồm nhiều target/proxy |
| Field trong delegate riêng cho một `DelegatingIntroductionInterceptor` mới tạo | Chia sẻ trong những proxy/target thực sự sử dụng interceptor đó; có thể tách khi mỗi proxy được cấp instance riêng |
| Field trong delegate do `DelegatePerTargetObjectIntroductionInterceptor` quản lý | Gắn theo **target object** của invocation |
| Field trong business target | Phụ thuộc lifecycle của target (singleton, prototype, TargetSource khác...) |
| Field `static` của class delegate | Có thể dùng chung giữa mọi instance của class trong cùng classloader, bất kể kiểu introduction |
| State ngoài delegate (database/cache/shared service) | Phụ thuộc dependency dùng chung; không tự trở thành per-target |

Hai cơ chế dễ gây nhầm lẫn:

**`DelegatingIntroductionInterceptor`:** mỗi instance giữ một delegate cụ thể. Nếu một instance introduction interceptor được dùng để advise **nhiều target objects**, tất cả dùng cùng delegate và cùng state. Đây là đặc điểm được Javadoc Spring nêu rõ.

**`DelegatePerTargetObjectIntroductionInterceptor`:** một interceptor có thể được dùng chung cho nhiều target, nhưng nó tạo/quản lý delegate tương ứng với từng **target object**. Đây là công cụ phù hợp khi introduction phải giữ trạng thái bổ sung riêng cho mỗi object đích.

### Điều cần nhớ

- **Per-target** không có nghĩa **per-invocation**.
- **Per-target** không có nghĩa **per-request** hoặc **per-user**.
- **Per-target** không có nghĩa **per-thread**.
- Hai proxy khác nhau có thể giữ **cùng target object**; nếu dùng cùng per-target introduction interceptor, chúng có thể dùng cùng phần state được gắn với target đó.
- Một proxy dùng một `TargetSource` thay target liên tục có thể quan sát nhiều state per-target khác nhau theo object được trả về.

Vì vậy, phải xem **ai tạo advice, ai sở hữu target, ai reuse advisor và ai giữ reference proxy**.

## 4. Các Spring API liên quan

### `IntroductionInterceptor`

`org.springframework.aop.IntroductionInterceptor` kết hợp vai trò `MethodInterceptor` với `DynamicIntroductionAdvice`. Nó biết xác định interface được introduction hỗ trợ và intercept lời gọi method.

Về mặt ý tưởng:

~~~java
// Pseudocode: mô hình xử lý khái niệm, không phải implementation gốc
Object invoke(MethodInvocation invocation) throws Throwable {
    if (isIntroducedInterfaceMethod(invocation.getMethod())) {
        return invokeMethodOnDelegate(invocation);
    }
    return invocation.proceed();
}
~~~

Với lời gọi tới interface vừa được introduction, interceptor xử lý bằng delegate. Với lời gọi khác, thông thường interceptor chuyển tiếp về chuỗi bằng `proceed()`.

### `IntroductionAdvisor` và `DefaultIntroductionAdvisor`

`org.springframework.aop.IntroductionAdvisor` mô tả những interface mà advisor làm cho proxy expose, bên cạnh advice thực thi method. Khác với `PointcutAdvisor` dành cho matching method, introduction liên quan đến việc **bổ sung giao diện ở cấp type** và kiểm tra target class qua `ClassFilter`.

Một cách tạo advisor:

~~~java
DelegatingIntroductionInterceptor intro =
        new DelegatingIntroductionInterceptor(new CounterMixin());

DefaultIntroductionAdvisor advisor =
        new DefaultIntroductionAdvisor(intro, Counter.class);
~~~

Đây là fragment, cần `CounterMixin` và `Counter` trong chương trình bên dưới. Constructor `DefaultIntroductionAdvisor(DynamicIntroductionAdvice, Class<?>)` chỉ rõ interface cần expose. Nếu delegate implement nhiều interface, **không phải tất cả đều mặc nhiên được công bố qua proxy**: `IntroductionAdvisor` kiểm soát danh sách interface.

### `DelegatingIntroductionInterceptor`

Một cách hiện thực:

~~~java
CounterMixin delegate = new CounterMixin();
DelegatingIntroductionInterceptor intro =
        new DelegatingIntroductionInterceptor(delegate);
~~~

Interceptor giữ reference tới delegate mà bạn cung cấp. Nếu tái dùng `intro` trong hai proxy, bạn đang tái dùng cùng `delegate`.

Có thể subclass `DelegatingIntroductionInterceptor` rồi tự implement interface được introduction. Khi dùng constructor không tham số (được bảo vệ), delegate mặc định là `this`. Field của subclass khi đó vẫn là field của **instance introduction interceptor**; nếu cùng instance được tái dùng cho nhiều target thì state cũng dùng chung.

### `DelegatePerTargetObjectIntroductionInterceptor`

Constructor:

~~~java
DelegatePerTargetObjectIntroductionInterceptor intro =
        new DelegatePerTargetObjectIntroductionInterceptor(
                CounterMixin.class, Counter.class);
~~~

Đây là fragment. `CounterMixin` phải có khả năng được Spring khởi tạo từ class type (ví dụ class public, concrete và có public no-arg constructor), và implements interface `Counter` đã khai báo. Chỉ định `Counter.class` là interface được introduction hỗ trợ.

Khác với việc cung cấp một object delegate duy nhất, interceptor này **tạo delegate cho từng target object** khi thực sự cần. Cần hiểu đúng từ khóa *target object*: đây không phải một delegate mới cho mỗi proxy call.

## 5. Ví dụ Maven/Java 17+ hoàn chỉnh: cùng advisor, hai phạm vi state

Chương trình này tạo hai cặp Spring AOP proxy để so sánh:

1. **Shared case:** hai proxy có target khác nhau nhưng **cùng `DelegatingIntroductionInterceptor`** → cùng bộ đếm.
2. **Per-target case:** hai proxy có target khác nhau nhưng **cùng `DelegatePerTargetObjectIntroductionInterceptor`** → mỗi target có bộ đếm riêng.
3. **Same-target case:** hai proxy khác nhau trỏ tới **cùng target object** và dùng cùng per-target interceptor → cùng phần state theo target đó.

Chúng ta dùng `AtomicInteger` bên trong delegate để demo vẫn an toàn ở thao tác increment nếu nhiều thread cùng gọi; một counter có thể an toàn về mặt atomicity nhưng vẫn sai về mặt **phạm vi state** nếu chia sẻ ngoài ý muốn.

### 5.1. Cấu trúc thư mục

~~~text
spring-introduction-state/
├── pom.xml
└── src/main/java/example/IntroductionStateDemo.java
~~~

### 5.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-introduction-state</artifactId>
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
                    <mainClass>example.IntroductionStateDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 5.3. `src/main/java/example/IntroductionStateDemo.java`

~~~java
package example;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.aop.support.DelegatePerTargetObjectIntroductionInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class IntroductionStateDemo {

    // Giao diện nghiệp vụ mà target thực sự triển khai.
    public interface Service {
        String name();
    }

    public static class ServiceImpl implements Service {
        private final String id;

        public ServiceImpl(String id) {
            this.id = id;
        }

        @Override
        public String name() {
            return id;
        }
    }

    // Giao diện sẽ được INTRODUCE vào Spring AOP proxy.
    public interface Counter {
        int increment();
        int value();
    }

    // Mixin stateful; có public no-arg constructor mặc định.
    // Đây là kiểu concrete mà per-target interceptor có thể tạo.
    public static class CounterMixin implements Counter {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public int increment() {
            return count.incrementAndGet();
        }

        @Override
        public int value() {
            return count.get();
        }
    }

    private static Service proxy(
            Service target, DefaultIntroductionAdvisor advisor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(Service.class);
        factory.setProxyTargetClass(false);
        factory.addAdvisor(advisor);
        return (Service) factory.getProxy();
    }

    public static void main(String[] args) {
        // A: một delegate chung cho hai proxy/target khác nhau.
        DefaultIntroductionAdvisor sharedAdvisor =
                new DefaultIntroductionAdvisor(
                    new DelegatingIntroductionInterceptor(new CounterMixin()),
                    Counter.class);

        Service sharedA = proxy(new ServiceImpl("A"), sharedAdvisor);
        Service sharedB = proxy(new ServiceImpl("B"), sharedAdvisor);

        Counter aShared = (Counter) sharedA;
        Counter bShared = (Counter) sharedB;

        System.out.println("Shared A increment=" + aShared.increment());
        System.out.println("Shared B value=" + bShared.value());
        System.out.println("Shared B increment=" + bShared.increment());
        System.out.println("Shared A value=" + aShared.value());

        // B: một interceptor nhưng delegate RIÊNG từng target.
        DefaultIntroductionAdvisor perTargetAdvisor =
                new DefaultIntroductionAdvisor(
                    new DelegatePerTargetObjectIntroductionInterceptor(
                        CounterMixin.class, Counter.class),
                    Counter.class);

        Service ownA = proxy(new ServiceImpl("C"), perTargetAdvisor);
        Service ownB = proxy(new ServiceImpl("D"), perTargetAdvisor);

        Counter aOwn = (Counter) ownA;
        Counter bOwn = (Counter) ownB;

        System.out.println("Per-target A increment=" + aOwn.increment());
        System.out.println("Per-target B value=" + bOwn.value());
        System.out.println("Per-target A increment=" + aOwn.increment());
        System.out.println("Per-target B increment=" + bOwn.increment());

        // C: hai proxy KHÁC NHAU, nhưng đúng một target instance.
        ServiceImpl sameTarget = new ServiceImpl("SAME");
        Service firstProxy = proxy(sameTarget, perTargetAdvisor);
        Service secondProxy = proxy(sameTarget, perTargetAdvisor);

        Counter firstCounter = (Counter) firstProxy;
        Counter secondCounter = (Counter) secondProxy;

        System.out.println("Same-target first increment=" +
                firstCounter.increment());
        System.out.println("Same-target second value=" +
                secondCounter.value());

        System.out.println("Business A=" + sharedA.name());
        System.out.println("Business B=" + sharedB.name());
        System.out.println("Introduced A=" + (sharedA instanceof Counter));
        System.out.println("JDK proxy A=" +
                AopUtils.isJdkDynamicProxy(sharedA));
        System.out.println("Target is Counter=" +
                (new ServiceImpl("raw") instanceof Counter));
    }
}
~~~

### 5.4. Chạy

Tại thư mục `spring-introduction-state` với Maven và JDK 17+:

~~~bash
mvn -q compile exec:java
~~~

**Stdout do chương trình in ra:**

~~~text
Shared A increment=1
Shared B value=1
Shared B increment=2
Shared A value=2
Per-target A increment=1
Per-target B value=0
Per-target A increment=2
Per-target B increment=1
Same-target first increment=1
Same-target second value=1
Business A=A
Business B=B
Introduced A=true
JDK proxy A=true
Target is Counter=false
~~~

Maven có thể in những thông báo của công cụ riêng. Các dòng trên là kết quả logic của chương trình một luồng, không phụ thuộc số thread hoặc scheduler.

### 5.5. Phân tích kết quả

**Shared case:**

~~~text
Spring proxy A -----> ServiceImpl("A")
      |
      +---- shared IntroductionAdvisor
                   |
Spring proxy B -----+---- DelegatingIntroductionInterceptor
      |                     |
      v                     v
ServiceImpl("B")       CounterMixin(count)
                          (một instance)
~~~

Lượt `aShared.increment()` tăng count từ 0 thành 1. Vì `bShared` dùng cùng delegate, `bShared.value()` nhìn thấy 1; tăng từ B thành 2 thì A cũng nhìn thấy 2.

**Per-target case:**

~~~text
Spring proxy A -----> ServiceImpl("C")
      |                       |
      |               delegate CounterMixin C
      |
      +---- shared DelegatePerTargetObjectIntroductionInterceptor
      |
      |               delegate CounterMixin D
      |                       |
Spring proxy B -----> ServiceImpl("D")
~~~

Cả hai proxy cùng giữ reference tới một advisor/interceptor, nhưng interceptor tra delegate theo **target object**. Target C có count 1 rồi 2; D độc lập và bắt đầu bằng 0 rồi 1.

**Same-target case:** lần này hai proxy trỏ đúng cùng reference `ServiceImpl sameTarget`. Chúng cũng dùng cùng per-target interceptor, nên cả hai cùng sử dụng delegate gắn với object `sameTarget`. Điều đó chứng minh **“per-target” không đồng nghĩa “per-proxy”**.

**Business method:** `sharedA.name()` và `sharedB.name()` được forward đến business target tương ứng. Method `increment` và `value` được xử lý bởi introduced implementation. `new ServiceImpl("raw")` không implements `Counter`; interface bổ sung chỉ hiện diện trên proxy.

## 6. Phạm vi chia sẻ phụ thuộc cách tạo advice/advisor

Cùng một class `DelegatingIntroductionInterceptor` có thể tạo ra hành vi khác nhau nếu cách đăng ký khác nhau.

### 6.1. Dùng chung một advisor và interceptor

~~~java
DefaultIntroductionAdvisor shared = new DefaultIntroductionAdvisor(
        new DelegatingIntroductionInterceptor(new CounterMixin()),
        Counter.class);

factoryA.addAdvisor(shared);
factoryB.addAdvisor(shared);
~~~

Đây là **fragment** với hai `ProxyFactory` đã được tạo. Vì cả hai proxy dùng cùng advisor và interceptor, chúng chia sẻ cùng delegate.

Một trường hợp tương tự có thể xảy ra khi một `IntroductionAdvisor` bean singleton được một auto-proxy creator áp dụng cho nhiều target beans. “Bean singleton” ở đây không có nghĩa mỗi target có state riêng; nếu advice giữ một delegate stateful, state đó có thể bị chia sẻ.

### 6.2. Tạo delegate/advisor riêng cho từng proxy

~~~java
factoryA.addAdvisor(new DefaultIntroductionAdvisor(
        new DelegatingIntroductionInterceptor(new CounterMixin()),
        Counter.class));

factoryB.addAdvisor(new DefaultIntroductionAdvisor(
        new DelegatingIntroductionInterceptor(new CounterMixin()),
        Counter.class));
~~~

Mỗi proxy được cấu hình với **một interceptor/delegate mới**, nên state độc lập ở cấu hình này. Tuy nhiên điều đó chỉ đúng vì ứng dụng đã **tạo instance riêng**; nó không phải lời hứa rằng `DelegatingIntroductionInterceptor` tự tách state theo target trong mọi cách dùng.

Nếu tự viết factory tạo proxy nhiều lần, hãy xem lại liệu code có tạo delegate bên trong factory hay truyền một delegate field/singleton từ bên ngoài. Chính **object identity** của delegate mới quyết định có bị chia sẻ hay không.

### 6.3. Dùng per-target interceptor chung

~~~java
var perTarget = new DelegatePerTargetObjectIntroductionInterceptor(
        CounterMixin.class, Counter.class);

var advisor = new DefaultIntroductionAdvisor(perTarget, Counter.class);

factoryA.addAdvisor(advisor);
factoryB.addAdvisor(advisor);
~~~

Đây là fragment; `factoryA` và `factoryB` cần có target object phù hợp. Theo semantics Spring, interceptor duy nhất có thể xử lý nhiều target và tạo delegate riêng cho từng target. Điều này hữu ích khi hạ tầng auto-proxy cần chia sẻ advisor/interceptor nhưng introduction vẫn phải có state riêng theo đối tượng đích.

**Tránh hiểu nhầm:** nếu hai proxy bao **cùng target**, hoặc một target object được reuse theo cách khác, chúng có thể vẫn chia sẻ phần state được gắn với object đó. Nếu muốn mỗi proxy có state riêng thì phải thiết kế phạm vi theo proxy, không chỉ dùng một per-target interceptor chung.

## 7. Bean scope và vòng đời không tự động bằng phạm vi introduction

Spring IoC có singleton, prototype, request/session scope và nhiều `TargetSource`. Các khái niệm đó không được phép đánh đồng với vòng đời của introduction delegate.

| Cấu hình | Những gì thực tế được nhân bản |
| --- | --- |
| Target bean singleton, introduction singleton kiểu `DelegatingIntroductionInterceptor` | Có thể một target và một delegate chung |
| Nhiều target beans khác nhau, cùng `DelegatingIntroductionInterceptor` | **Một delegate chung** dù target khác nhau |
| Nhiều target beans khác nhau, cùng `DelegatePerTargetObjectIntroductionInterceptor` | Một delegate riêng theo **target object** |
| Proxy prototype, introduction advisor singleton kiểu delegating | Proxy mới không có nghĩa delegate mới |
| TargetSource thay target theo invocation, per-target introduction | Mỗi target identity có thể có phần state riêng; cần phân tích thêm lifecycle |
| Delegate class dùng static field | Static state vẫn có thể chia sẻ ngoài phạm vi mỗi instance |

### `TargetSource` thay đổi target

Nếu proxy dùng `HotSwappableTargetSource` hoặc một target source khác, đối tượng thực được cung cấp cho invocation có thể thay đổi. Per-target interceptor gắn state với **target được Spring AOP xử lý**, không nhất thiết với một reference proxy cố định.

Điều này có ý nghĩa khi:

- Thay target v1 bằng v2: introduction state có thể chuyển sang delegate của v2 thay vì tiếp tục dùng state của v1.
- Trả lại target v1: nếu phần mapping tương ứng vẫn còn tồn tại, state gắn với v1 có thể được dùng lại; không coi đây là chính sách khôi phục trạng thái được bảo đảm ở mọi lifecycle.
- TargetSource tạo target mới liên tục: per-target introduction có thể tạo nhiều delegate; phải xem xét chi phí và giải phóng tài nguyên.

Đừng thiết kế nghiệp vụ đòi hỏi state per-request nhưng dùng một target singleton với per-target introduction: như vậy state vẫn tồn tại xuyên nhiều request trên cùng object.

### `@Scope("prototype")` trên advice có đủ không?

Không nhất thiết. Một prototype advice bean chỉ tạo **instance khác khi bean được yêu cầu tạo mới**. Nếu code lấy một instance prototype rồi gắn nó vào advisor singleton được tái dùng, phần state bên trong instance ấy vẫn được chia sẻ theo advisor đó.

Muốn tách state, phải kiểm tra **lúc nào Spring lấy bean mới, ai giữ instance ấy, advisor được tái sử dụng ra sao**. Tên scope một mình không xác nhận mỗi lời gọi hoặc mỗi proxy có delegate riêng.

## 8. Concurrency: state riêng target vẫn có thể dùng đồng thời

Phân biệt ba vấn đề:

1. **Scope:** caller nào nhìn thấy cùng phần state?
2. **Thread safety:** khi nhiều thread cùng cập nhật state đó, giá trị có đúng không?
3. **Lifecycle:** state tồn tại bao lâu và được thu hồi lúc nào?

Chúng độc lập. Một `DelegatingIntroductionInterceptor` dùng delegate chung và field `int count` có thể vừa **chia sẻ sai phạm vi**, vừa **race condition**. Đổi `count` thành `AtomicInteger` chỉ giải quyết atomic increment/read, **không thay đổi phạm vi chia sẻ**.

Với `DelegatePerTargetObjectIntroductionInterceptor`, nhiều thread có thể cùng gọi **một target singleton** thông qua Spring proxy, nên các thread vẫn dùng một delegate per-target. Per-target **không bằng per-thread**. Nếu state mutable, cần đồng bộ thích hợp, thiết kế immutable hoặc ràng buộc truy cập rõ ràng.

Ví dụ không thread-safe:

~~~java
public class UnsafeCounterMixin implements Counter {
    private int count;

    public int increment() {
        return ++count; // read-modify-write không atomic
    }

    public int value() {
        return count;
    }
}
~~~

Đây là fragment class minh họa, không chạy độc lập nếu không khai báo interface `Counter`. Nhiều lời gọi đồng thời có thể mất lượt increment.

Ví dụ dùng trong chương trình `CounterMixin` với `AtomicInteger` cho phép tăng/đọc count thread-safe trong phạm vi số nguyên. Nhưng nó **không biến một delegate dùng chung giữa nhiều tenant/request thành trạng thái đúng đắn về nghiệp vụ**. Bạn vẫn phải chọn phạm vi chia sẻ phù hợp.

### State per-request nên làm thế nào?

Nếu trạng thái cần thuộc về một request, hãy truyền qua tham số, request-scoped bean hoặc context được Spring quản lý theo ranh giới phù hợp. Không dựa vào “per-target” trong khi target là singleton dùng cho tất cả request. Nếu cần audit/metrics thống kê toàn ứng dụng, state chung lại có thể là chủ ý, nhưng cần cách đếm và quan sát thread-safe.

## 9. Những sai lầm thường gặp

**Sai 1 — “Introduction thêm interface vào class target.”** Spring AOP thêm interface lên **proxy**. `new ServiceImpl()` nguyên bản vẫn không implements interface được introduce.

**Sai 2 — “Hai proxy là hai object nên mixin state chắc chắn riêng.”** Sai nếu cả hai dùng cùng `DelegatingIntroductionInterceptor` hoặc cùng delegate.

**Sai 3 — “Introduction advisor singleton an toàn vì interceptor không mang state.”** Không đúng cho mọi interceptor. `DelegatingIntroductionInterceptor` có thể giữ một delegate stateful; singleton advisor có thể lan state tới nhiều target.

**Sai 4 — “Per-target nghĩa là mỗi request có state riêng.”** Sai khi target object được dùng chung xuyên requests.

**Sai 5 — “Per-target nghĩa là mỗi proxy có state riêng.”** Sai nếu hai proxy dùng chung cùng target object và cùng per-target introduction interceptor.

**Sai 6 — “Đổi sang CGLIB giải quyết state sharing.”** Sai. JDK vs CGLIB quyết định chiến lược proxy, không đổi việc một advice/delegate instance có được tái dùng không.

**Sai 7 — “AtomicInteger làm state không còn bị chia sẻ.”** Sai. Nó hỗ trợ atomicity trong concurrent calls; phạm vi share vẫn phụ thuộc identity của delegate.

**Sai 8 — “Prototype advice bean tức một advice cho mỗi invocation.”** Sai. Prototype chỉ nói Spring tạo instance khi được yêu cầu; nếu một instance sau đó được advisor giữ lâu dài thì nó vẫn được tái dùng.

**Sai 9 — “Mọi introduced interface trên delegate đều xuất hiện.”** Không phải. `IntroductionAdvisor` kiểm soát những interface được expose; implementation có thể hỗ trợ nhiều interface hơn danh sách công bố.

**Sai 10 — “Method introduction luôn chạy trên target thật.”** Không. Các method do interface bổ sung giới thiệu thường được delegate của introduction interceptor xử lý; business method mới chuyển tiếp vào target.

**Sai 11 — “Một proxy chia sẻ với nhiều thread nên luôn cần per-target interceptor.”** Per-target không thay cho thread safety: nếu một target singleton bị gọi đồng thời, delegate của nó cũng có thể bị gọi đồng thời.

**Sai 12 — “Thêm advisor introduction vào proxy đang chạy luôn có thể mở thêm interface.”** Tập interface của proxy được xác định lúc tạo proxy; nếu cần expose interface mới cho proxy hiện hữu, có thể phải dựng proxy mới và cấu hình introduction từ đầu. Không phụ thuộc vào việc chỉnh runtime advisor list để thay đổi type surface.

## 10. Checklist thiết kế Introduction trong Spring AOP

1. Ghi rõ introduced interface, danh sách method và ai sẽ cung cấp implementation.
2. Xác định state có cần chia sẻ toàn ứng dụng, theo target, theo proxy hay theo request.
3. Nếu reuse cùng `DelegatingIntroductionInterceptor`, chấp nhận và kiểm soát việc **dùng chung delegate**.
4. Nếu cần state riêng theo target, cân nhắc `DelegatePerTargetObjectIntroductionInterceptor` và dùng implementation class có constructor phù hợp.
5. Kiểm tra target identity thực tế: nhiều proxy có thể cùng một target; TargetSource có thể đổi target.
6. Kiểm tra advisor được tạo riêng hay singleton và có tái sử dụng ở nhiều proxy không.
7. Nếu delegate stateful có concurrent calls, dùng cơ chế đồng bộ hoặc data structures an toàn.
8. Không đặt state per-request nhạy cảm vào delegate dùng chung hoặc target singleton.
9. Với implementation cần cleanup, xác định lifecycle và cách thu hồi tài nguyên, nhất là khi có target động.
10. Test bằng **hai proxy/two targets** rồi test thêm **hai proxy cùng target**, không chỉ test một proxy.
11. Kiểm tra được cast sang introduced interface trên proxy; xác nhận target raw không có interface đó.
12. Kiểm tra cả business method và introduced method qua proxy, giữ nguyên giới hạn self-invocation/pointcut của Spring AOP.

## 11. Câu hỏi tự kiểm tra

1. Nếu hai Spring AOP proxy dùng cùng instance `DelegatingIntroductionInterceptor(new CounterMixin())`, count tăng ở proxy thứ nhất có nhìn thấy ở proxy thứ hai không? Vì sao?
2. Nếu mỗi proxy có một `DelegatingIntroductionInterceptor` riêng và một delegate riêng, kết quả thay đổi như thế nào?
3. `DelegatePerTargetObjectIntroductionInterceptor` tạo state mới theo proxy, theo method call hay theo target object?
4. Nếu hai proxy khác nhau bao cùng reference target, chúng dùng chung hay tách state với cùng per-target introduction interceptor?
5. Vì sao target singleton có per-target introduction vẫn có thể có race condition?
6. `AtomicInteger` giải quyết vấn đề gì, và không giải quyết vấn đề gì?
7. Vì sao không thể dùng `@Scope("prototype")` làm bằng chứng đủ rằng introduction state là per-invocation?
8. Có thể cast `new ServiceImpl("raw")` sang interface được introduce không? Tại sao proxy thì làm được?
9. Một `HotSwappableTargetSource` thay target nhiều lần có tác động gì đến cách suy nghĩ về scope của per-target delegate?
10. Nếu muốn trạng thái riêng cho HTTP request, nên kiểm soát lifecycle ở đâu thay vì gắn mọi state vào một introduction trên singleton target?

## 12. Tài liệu Spring chính thức

- [Advice API in Spring — Advice Lifecycles and Introduction Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html): per-class/per-instance advice, `DelegatingIntroductionInterceptor` và `IntroductionAdvisor`.
- [DelegatingIntroductionInterceptor Javadoc (Spring Framework 6.2)](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DelegatingIntroductionInterceptor.html): delegate và các interface được hỗ trợ.
- [DelegatePerTargetObjectIntroductionInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DelegatePerTargetObjectIntroductionInterceptor.html): per-target delegate và hành vi khác với delegating interceptor.
- [DefaultIntroductionAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html): khai báo interface cần giới thiệu.
- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html): mối quan hệ advisor/advice.
- [Creating AOP Proxies with ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): dựng proxy và gắn advisor bằng Java.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK dynamic proxy, CGLIB và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-008">Khác biệt giữa interface trên proxy và lớp đích ban đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa interface trên proxy và lớp đích ban đầu trong Spring AOP Introduction

## 1. Ba đối tượng khác nhau khi kiểm tra introduction

Giả sử Spring tạo proxy cho `BillingService` và thêm interface `Auditable`. Để xác minh đúng behavior, hãy phân biệt:

- **`BillingService.class`**: định nghĩa class Java gốc; `Auditable.class.isAssignableFrom(BillingService.class)` vẫn là `false` nếu class không khai báo interface.
- **Raw target**, ví dụ `new BillingService()`: có thể gọi `charge(...)` trực tiếp nhưng không cast được thành `Auditable`.
- **Spring AOP proxy**: object client lấy từ context; nếu introduction thực sự áp dụng, `proxy instanceof Auditable` là `true` và `Auditable.markAudited(...)` được xử lý bởi delegate, trong khi `charge(...)` tiếp tục tới target.

Vì vậy câu “bean có thêm interface” cần hiểu là **proxy của bean công bố interface mới**. Khi kiểm tra reflection, `AopUtils.getTargetClass(proxy)` trả về **target class**, còn `proxy.getClass()` là **runtime proxy type**. Hai kết quả phục vụ hai câu hỏi khác nhau, nhất là với JDK dynamic proxy và CGLIB.

## 2. @DeclareParents và nguồn tạo interface trên proxy

~~~java
@Aspect
public class AuditableIntroductionAspect {
    @DeclareParents(
        value = "demo.intro.BillingService+",
        defaultImpl = DefaultAuditable.class
    )
    public static Auditable introducedInterface;
}
~~~

`@DeclareParents` nằm trên **field static**, lấy kiểu field làm interface giới thiệu, `value` làm **target type pattern**, và `defaultImpl` làm implementation cho method mới. Aspect phải được Spring đăng ký, AOP proxy support phải hoạt động, và target type phải match pattern. Field chỉ là metadata, không phải nơi chứa một bean `Auditable` để gọi trực tiếp. `BillingService` không tự sửa `implements` và instance do `new` tạo không hưởng introduction.

Ví dụ từ mục 4 sử dụng `@EnableAspectJAutoProxy(proxyTargetClass=true)` để kiểm tra cả khả năng cast về concrete class và `Auditable`. Với JDK proxy, việc cast sang concrete implementation class có thể thất bại dù proxy vẫn implements introduced interface.

## 3. Bảng so sánh chính xác: target class, raw target và proxy

Giả sử target class là `BillingService`, introduction thêm `Auditable` và cấu hình dùng CGLIB proxy để thuận tiện truy cập theo concrete class.

| Câu hỏi | `BillingService.class` / raw target | Spring AOP proxy |
| --- | --- | --- |
| Có khai báo `implements Auditable` trong source? | Không | Proxy là class được tạo runtime, công bố thêm interface |
| `Auditable.class.isAssignableFrom(BillingService.class)` | `false` | Không nên dùng target class để xét proxy; kiểm tra **object/class runtime của proxy** |
| `Auditable.class.isInstance(object)` | `false` đối với `new BillingService()` | `true` nếu introduction đã được áp dụng |
| Có thể gọi `charge(...)`? | Có, chạy thẳng trên target | Có; proxy chuyển tiếp đến target |
| Có thể gọi `markAudited(...)` qua `Auditable`? | Không, raw target không có interface này | Có; proxy chuyển đến delegate |
| `AopUtils.isAopProxy(object)` | `false` đối với raw object | `true` đối với proxy AOP |
| `AopUtils.getTargetClass(object)` | Lớp của raw object | Phục hồi **target class**, không có nghĩa trả về tập interface mới trên proxy |
| `object.getClass()` | Kiểu runtime của raw object | Kiểu proxy, có thể là JDK dynamic proxy hoặc subclass CGLIB |

Từ bảng trên, một lớp Java có thể vẫn hoàn toàn độc lập với tính năng theo dõi phụ trợ, trong khi Spring tạo object có thêm hợp đồng phụ trợ cho các consumer cần dùng tính năng ấy.

**Quan trọng về kiểu:** “Proxy triển khai interface mới” không bảo đảm rằng mọi proxy đều có thể ép về **concrete target class**. Khả năng đó phụ thuộc lựa chọn JDK dynamic proxy hay CGLIB, được phân tích tại mục 5.

## 4. Ví dụ Java 17+ kiểm tra interface trên proxy và target

Ví dụ dùng Spring Framework 6.x, phong cách `@AspectJ`, với hai khả năng:
- `BillingService` chỉ khai báo method nghiệp vụ `charge`, không implements `Auditable`.
- Proxy Spring do introduction tạo có thêm `Auditable`, gọi method `markAudited` của delegate `DefaultAuditable`.

Cấu trúc project:

~~~text
build.gradle
src/main/java/demo/intro/
    Auditable.java
    DefaultAuditable.java
    BillingService.java
    AuditableIntroductionAspect.java
    ProxyVsTargetDemo.java
~~~

### 4.1. Interface được introduction bổ sung

**`src/main/java/demo/intro/Auditable.java`**

~~~java
package demo.intro;

public interface Auditable {
    void markAudited(String auditId);
    String lastAuditId();
}
~~~

### 4.2. Implementation dành cho interface mới

**`src/main/java/demo/intro/DefaultAuditable.java`**

~~~java
package demo.intro;

public class DefaultAuditable implements Auditable {

    private String lastAuditId = "none";

    public DefaultAuditable() {
    }

    @Override
    public void markAudited(String auditId) {
        lastAuditId = auditId;
    }

    @Override
    public String lastAuditId() {
        return lastAuditId;
    }
}
~~~

Delegate này giữ trạng thái ví dụ; nó **không nằm trong source class `BillingService`**. Đây là bản tối giản dành cho luồng đơn. Nếu nhiều thread cùng cập nhật trạng thái được giới thiệu, hãy thiết kế cơ chế đồng bộ và vòng đời state phù hợp; không mặc định một field `String` mutable là an toàn cho thao tác concurrent.

### 4.3. Lớp đích không có interface Auditable

**`src/main/java/demo/intro/BillingService.java`**

~~~java
package demo.intro;

public class BillingService {

    public void charge(int amount) {
        System.out.println("charged: " + amount);
    }
}
~~~

Bạn có thể kiểm tra ngay trong code: không có `implements Auditable`, cũng không có method `markAudited` hay `lastAuditId`. Đây là điều kiện để nhìn thấy rõ khác biệt.

### 4.4. Spring Aspect khai báo introduction

**`src/main/java/demo/intro/AuditableIntroductionAspect.java`**

~~~java
package demo.intro;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;

@Aspect
public class AuditableIntroductionAspect {

    @DeclareParents(
        value = "demo.intro.BillingService+",
        defaultImpl = DefaultAuditable.class
    )
    public static Auditable introducedInterface;
}
~~~

`@DeclareParents` dùng tên type đầy đủ `demo.intro.BillingService+`. Nếu đổi package hoặc class mà không cập nhật pattern, target có thể không khớp và proxy sẽ không được bổ sung interface.

### 4.5. Cấu hình Spring và lớp chạy

**`src/main/java/demo/intro/ProxyVsTargetDemo.java`**

~~~java
package demo.intro;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ProxyVsTargetDemo {

    @Bean
    BillingService billingService() {
        return new BillingService();
    }

    @Bean
    AuditableIntroductionAspect auditableIntroductionAspect() {
        return new AuditableIntroductionAspect();
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(ProxyVsTargetDemo.class)) {

            BillingService springBean = ctx.getBean(BillingService.class);
            BillingService raw = new BillingService();

            System.out.println("target declares Auditable="
                    + Auditable.class.isAssignableFrom(BillingService.class));
            System.out.println("bean is AOP proxy="
                    + AopUtils.isAopProxy(springBean));
            System.out.println("bean is BillingService="
                    + (springBean instanceof BillingService));
            System.out.println("bean exposes Auditable="
                    + Auditable.class.isInstance(springBean));
            System.out.println("raw exposes Auditable="
                    + Auditable.class.isInstance(raw));
            System.out.println("target class="
                    + AopUtils.getTargetClass(springBean).getSimpleName());

            Auditable audit = ctx.getBean("billingService", Auditable.class);
            audit.markAudited("audit-42");
            System.out.println("last audit=" + audit.lastAuditId());

            springBean.charge(100);
            raw.charge(50);
        }
    }
}
~~~

Trong ví dụ, `proxyTargetClass = true` yêu cầu CGLIB proxy kiểu lớp. Do đó `springBean` vừa tương thích `BillingService`, vừa triển khai `Auditable`. Spring tạo class proxy runtime; **class gốc `BillingService.class` vẫn không có thêm interface**.

Aspect được đăng ký rõ ràng qua `@Bean`, không cần component scanning. Trong Spring Boot có thể khai báo aspect với `@Component` và bảo đảm package scanning bao phủ nó.

### 4.6. Dependency Gradle và chạy

**`build.gradle`**

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
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.intro.ProxyVsTargetDemo'
}
~~~

`spring-context` kéo theo các module Spring cần thiết, trong đó có `spring-aop`. `aspectjweaver` cung cấp annotation AspectJ và hỗ trợ phân tích type/pointcut pattern mà Spring sử dụng; cơ chế AOP trong ví dụ vẫn là **Spring proxy-based AOP**, không phải compile-time weaving.

Chạy với Gradle wrapper:

~~~bash
./gradlew run
~~~

Hoặc trên Windows:

~~~powershell
.\gradlew.bat run
~~~

**Các dòng do chương trình in ra trong ví dụ** (không bao gồm log Gradle hoặc Spring):

~~~text
target declares Auditable=false
bean is AOP proxy=true
bean is BillingService=true
bean exposes Auditable=true
raw exposes Auditable=false
target class=BillingService
last audit=audit-42
charged: 100
charged: 50
~~~

Chúng chứng minh ba kết luận tách biệt:
1. `BillingService.class` **không implements** `Auditable`, kể cả sau khi cấu hình Spring AOP.
2. `springBean` là một **Spring AOP proxy**, nên có thể dùng interface `Auditable` mà introduction gắn vào.
3. `raw` tạo bằng `new` không đi qua context và không được tự động bọc; nó không có `Auditable`.

Điểm đáng chú ý: `AopUtils.getTargetClass(springBean)` trả về `BillingService.class`, vì phương thức này hỏi **target type**. Không dùng kết quả đó để kết luận rằng proxy không triển khai `Auditable`; hãy kiểm tra `Auditable.class.isInstance(springBean)` để trả lời câu hỏi về **runtime proxy**.

## 5. Sự khác biệt khi dùng JDK dynamic proxy và CGLIB

Spring AOP hỗ trợ hai dạng proxy. Dù cả hai đều có thể công bố interface do introduction bổ sung, **khả năng xem proxy như một instance của concrete target class không giống nhau**.

| Kiểm tra | JDK dynamic proxy (khi target có business interface phù hợp, cấu hình chọn interface-based proxy) | CGLIB class-based proxy |
| --- | --- | --- |
| Proxy có interface được introduction bổ sung? | Có, khi advisor áp dụng | Có, khi advisor áp dụng |
| Proxy là instance của concrete target class? | Thông thường **không** | Có, do proxy là subclass runtime |
| Proxy là instance của business interface target vốn implements? | Có, nếu interface đó được đưa lên proxy | Có |
| `Auditable.class.isInstance(proxy)` | `true` khi introduction đã áp dụng | `true` khi introduction đã áp dụng |
| `BillingService.class.isInstance(proxy)` | `false` với JDK proxy | `true` nếu target là `BillingService` và có thể proxy qua CGLIB |
| Raw target class tự implements Auditable? | Không | Không |

**Ví dụ bổ sung để hiểu JDK proxy** (đây là biến thể thiết kế, **không** ghép trực tiếp vào các file ở mục 4):

~~~java
public interface BillingOperations {
    void charge(int amount);
}

public class BillingService implements BillingOperations {
    @Override
    public void charge(int amount) {
        System.out.println("charged: " + amount);
    }
}
~~~

Khi cấu hình chọn JDK proxy (ví dụ `@EnableAspectJAutoProxy(proxyTargetClass = false)` và không có cấu hình khác ép class proxy), proxy có thể implements cả `BillingOperations` và `Auditable`, nhưng **không phải** instance của `BillingService`. Consumer nên inject/truy cập qua `BillingOperations` và ép sang `Auditable` khi cần dùng khả năng bổ sung.

Trong dự án Spring Boot, cấu hình proxy mặc định có thể khác Spring Framework core, và các cấu hình AOP/transaction cùng context có thể thống nhất thành một chính sách proxy hiệu lực. Vì vậy, **quan sát proxy thật** bằng `AopUtils.isJdkDynamicProxy(bean)` hoặc `AopUtils.isCglibProxy(bean)` trước khi đưa ra kết luận về kiểu.

Một **interface introduction mới** không tự bảo đảm rằng Spring luôn chọn JDK proxy cho target trước đó không có interface. Hãy kiểm tra sự tồn tại của business interface trên target và chính sách proxy thực tế.

## 6. Các câu lệnh Java nào hợp lệ?

### 6.1. Cast đối tượng Spring trả về sang interface introduction

Với introduction đã hoạt động:

~~~java
Object bean = ctx.getBean("billingService");
Auditable audit = (Auditable) bean;
audit.markAudited("ok");
~~~

Hoặc dùng API thuận tiện hơn:

~~~java
Auditable audit = ctx.getBean("billingService", Auditable.class);
audit.markAudited("ok");
~~~

Hai đoạn trên giả định context chứa bean `billingService` và Spring đã gắn introduction. Đây là **snippet sử dụng bên trong context có sẵn**, không phải chương trình Java độc lập.

### 6.2. Cast raw target sang interface mới

~~~java
BillingService raw = new BillingService();
System.out.println(Auditable.class.isInstance(raw)); // false
~~~

Raw target không nhận introduction nên không được phép giả định phép ép `(Auditable) raw` sẽ chạy được. Nếu cố cast bằng cách qua kiểu `Object`, bạn sẽ gặp `ClassCastException` tại runtime. Với kiểu Java cụ thể không tương thích, compiler cũng có thể từ chối một phép ép kiểu ngay từ compile time tùy biểu thức và đặc tính final/sealed của class.

### 6.3. Reflection: phân biệt Class gốc và object runtime

~~~java
Class<?> targetClass = BillingService.class;
Object proxy = ctx.getBean("billingService");

System.out.println(
        Auditable.class.isAssignableFrom(targetClass)); // false
System.out.println(
        Auditable.class.isInstance(proxy));             // true
~~~

**Không nhầm `Class.getInterfaces()` của target với interface đã được thêm vào proxy.** Với proxy, `proxy.getClass()` là class runtime do framework tạo. Các interface xuất hiện trực tiếp hoặc được kế thừa ở kiểu proxy phụ thuộc cách tạo proxy; phép `Auditable.class.isInstance(proxy)` thường chính xác và dễ hiểu hơn khi câu hỏi là *object có hỗ trợ hợp đồng này không?*

## 7. “Proxy có interface” ảnh hưởng đến dependency injection thế nào?

Với bean có introduction, việc inject/cast cần phân biệt **kiểu khai báo bean**, **kiểu được Spring dự đoán trong quá trình tìm candidate** và **kiểu thực tế của object sau khi proxy hoàn tất**.

- Nếu consumer đã có reference tới bean Spring, có thể kiểm tra và cast reference đó sang `Auditable` khi introduction đã áp dụng.
- Nếu lấy bean theo **tên và kiểu** `ctx.getBean("billingService", Auditable.class)`, Spring kiểm tra object được trả về có phù hợp với `Auditable`.
- Nếu tìm bean **chỉ theo interface mới** (`ctx.getBean(Auditable.class)` hoặc autowire `Auditable`) thì kết quả phụ thuộc khả năng container dự đoán type của proxy, phạm vi bean và việc có nhiều candidate. Đừng mặc định đây luôn là cách ổn định nhất trong mọi cấu hình; nếu cần bean cụ thể, tên/qualifier và hợp đồng được thiết kế rõ ràng sẽ dễ kiểm soát hơn.
- Khi dùng JDK proxy, inject theo concrete target class có thể lỗi dù proxy vẫn chạy đúng theo business interface. Trong trường hợp này, consumer nên dùng interface mà proxy công bố.

Đừng sửa `BillingService` để thêm `implements Auditable` chỉ nhằm “làm Spring injection nhìn thấy interface”, bởi việc đó đã thay đổi mô hình nghiệp vụ: raw instance cũng có hợp đồng mới, mất ý nghĩa **introduction chỉ nằm trên proxy**.

## 8. Lời gọi method nào đi đâu?

Với ví dụ ở mục 4, hai lời gọi qua cùng proxy có tuyến xử lý khác nhau:

~~~text
springBean.charge(100)
    └── proxy (Spring AOP) → target BillingService.charge(100)

((Auditable) springBean).markAudited("audit-42")
    └── proxy (Spring AOP) → introduction delegate DefaultAuditable.markAudited(...)
~~~

Do vậy, khi nhìn thấy `Auditable` trên proxy, không có cơ sở để cho rằng:
- Source class của target có method `markAudited`.
- Raw target có một field `lastAuditId`.
- Gọi `this.markAudited(...)` trong code của `BillingService` sẽ hợp lệ.
- Instance `new BillingService()` có behavior introduction.
- Spring đã thực hiện compile-time weaving hay thay đổi bytecode target.

Với advice sử dụng `this(...)` và `target(...)`, sự khác biệt càng rõ:
- `this(auditable)` ràng buộc **proxy**; proxy đã có thể implements `Auditable` sau introduction.
- `target(auditable)` ràng buộc **target object**; raw target trong ví dụ không implements `Auditable`.

Ví dụ một advice bổ sung vào **`AuditableIntroductionAspect` ở mục 4.4** (thêm import `org.aspectj.lang.annotation.Before`):

~~~java
@Before("execution(* demo.intro.BillingService.charge(..)) && this(auditable)")
public void markWhenCharging(Auditable auditable) {
    auditable.markAudited("charge-called");
}
~~~

Đây là **đoạn bổ sung, không phải toàn bộ một class chạy độc lập**. Khi gọi `springBean.charge(100)` qua proxy, advice có thể bind `Auditable` trên proxy và gọi delegate. Nếu pointcut thay thành `target(auditable)` với cùng `Auditable`, target `BillingService` không có interface đó, nên điều kiện ràng buộc không phù hợp.

Nên giới hạn pointcut theo method nghiệp vụ cụ thể. Một advice quá rộng bao gồm cả method introduction do chính advice gọi có thể sinh hành vi đệ quy hoặc đếm sai.

## 9. Giới hạn thực tế khi sử dụng introduction trong Spring

### 9.1. Proxy không làm thay đổi object tạo ngoài container

`new BillingService()` không được AOP bọc. Ngay cả trong Spring, nếu bạn nắm reference tới raw target từ giai đoạn sớm hoặc lấy target trực tiếp từ cấu trúc proxy để debug, đừng giả định reference ấy có interface introduction.

### 9.2. Self-invocation và lời gọi nội bộ

Spring AOP áp dụng trên lời gọi đi qua proxy. Khi target gọi `this.otherMethod()`, lời gọi ấy không đi qua lớp interceptor bên ngoài. Introduction không giải quyết bản chất giới hạn self-invocation này. Đối tượng `this` bên trong target cũng không tự biến thành proxy có interface mới.

### 9.3. Class final, method final và proxy class-based

Nếu dùng CGLIB, class target `final` không thể bị subclass; `final/private` method không thể được advise theo cơ chế override. Còn JDK dynamic proxy hoạt động theo interface và không cung cấp khả năng ép về concrete target class. Một introduction phù hợp về type pattern vẫn có thể thất bại nếu loại proxy được chọn không áp dụng được cho target hoặc cấu hình mâu thuẫn.

### 9.4. Delegate introduction có state và lifecycle

`DefaultAuditable` trong ví dụ có trạng thái thay đổi. Cần quyết định rõ ý nghĩa của trạng thái khi bean/proxy có scope khác nhau, có nhiều request đồng thời hoặc được sử dụng trong môi trường concurrent. Không giả định delegate luôn có lifecycle như một `@Component` thông thường nếu Spring tạo nó bằng cơ chế `defaultImpl`; đừng dựa vào injection hay callback lifecycle của nó mà chưa kiểm chứng cách instantiate thực tế.

### 9.5. Type pattern chọn sai đối tượng

`@DeclareParents(value=...)` chọn **target type**, không chọn theo bean name. Khi pattern quá rộng, nhiều proxy có thể nhận một interface mà ứng dụng không mong đợi. Khi pattern quá hẹp hoặc sai package, proxy không có interface mới. Hãy dùng fully qualified name và kiểm tra theo từng bean cần mở rộng.

## 10. Debug nhanh khi proxy và target có interface khác nhau

| Triệu chứng | Cách giải thích cần kiểm tra | Hành động |
| --- | --- | --- |
| `Auditable.class.isAssignableFrom(BillingService.class)` là `false` | Điều này **đúng theo thiết kế introduction** | Kiểm tra object Spring trả về, không sửa target class vội |
| `Auditable.class.isInstance(springBean)` là `false` | Aspect không được đăng ký, auto-proxy chưa bật, type pattern không match hoặc bean chưa qua đủ BPP | Kiểm tra `@Aspect`, `@EnableAspectJAutoProxy`, scope/context và pattern |
| Object lấy từ context có `Auditable`, raw object không có | Introduction chỉ áp dụng trên proxy | Dùng bean Spring tại những điểm cần hành vi bổ sung |
| `ClassCastException` khi cast JDK proxy sang target class | JDK proxy không kế thừa concrete target | Lấy bean qua business interface hoặc chọn CGLIB khi thật cần |
| `AopUtils.getTargetClass(proxy)` trả `BillingService` | Phương thức đang trả kiểu target | Dùng `Auditable.class.isInstance(proxy)` để xem interface introduction |
| Gọi method introduction trên target thất bại | Raw target không implements interface | Gọi trên Spring proxy, không gọi trực tiếp raw target |
| Method mới không hoạt động như mong đợi | `defaultImpl` sai hoặc trạng thái delegate không được thiết kế đúng | Kiểm tra signature, constructor, mutable state, phạm vi và concurrency |
| Advice chỉ chạy lúc gọi từ bên ngoài bean | Self-invocation không đi qua proxy | Thiết kế ranh giới service hoặc gọi qua reference proxy được Spring quản lý |

Có thể kiểm tra lần lượt trong code chẩn đoán:

~~~java
Object bean = ctx.getBean("billingService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(Auditable.class.isInstance(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
~~~

Đoạn trên cần import `org.springframework.aop.support.AopUtils` và có biến `ctx` là `ApplicationContext` đang hoạt động. Nếu cần xem advisor đã gắn, kiểm tra `bean instanceof org.springframework.aop.framework.Advised` trước khi đọc `getAdvisors()`.

Khi tiến hành debug, phân biệt chính xác câu hỏi:
- **“Target class có implements interface này không?”** → `isAssignableFrom` với `BillingService.class`.
- **“Bean object mà consumer nhận có hỗ trợ interface này không?”** → `Auditable.class.isInstance(bean)`.
- **“Spring có trả về AOP proxy không?”** → `AopUtils.isAopProxy(bean)`.
- **“Proxy là JDK hay CGLIB?”** → `isJdkDynamicProxy` / `isCglibProxy`.
- **“Interface method của introduction chạy ở đâu?”** → implementation `defaultImpl`/introduction interceptor; không tự suy ra target có method đó.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Sau khi bật `@DeclareParents`, tại sao `Auditable.class.isAssignableFrom(BillingService.class)` vẫn `false`?

Vì Spring bổ sung `Auditable` cho **proxy runtime**, không thay đổi `BillingService.class`. Đây là biểu hiện đúng của Spring AOP introduction.

**Câu 2.** Nếu `ctx.getBean("billingService", Auditable.class)` thành công, có nghĩa `new BillingService()` cũng ép được sang `Auditable`?

Không. Đối tượng tạo bằng `new` không đi qua Spring auto-proxy creator và không nhận interface introduction.

**Câu 3.** Vì sao một JDK proxy có thể ép sang `Auditable` nhưng không ép được sang `BillingService`?

JDK proxy implements các interface được cấu hình/giới thiệu, nhưng không subclass concrete target class. CGLIB proxy có quan hệ kế thừa class và có thể thêm interface.

**Câu 4.** `AopUtils.getTargetClass(proxy)` trả `BillingService.class`. Điều này có phủ định việc proxy implements `Auditable` không?

Không. Một API nói về **target type**, API còn lại nói về **hợp đồng của proxy object**.

**Câu 5.** Một advice muốn bind interface `Auditable` được introduction đưa lên proxy nên dùng `this(auditable)` hay `target(auditable)`?

`this(auditable)`. Target ban đầu không implements `Auditable`; `target(...)` xét target object.

**Câu 6.** Nếu `@DeclareParents.value` được viết bằng tên bean `"billingService"` thay vì class pattern thì sao?

`value` là **AspectJ type pattern**, vì thế cần dùng type phù hợp với target. Nó không phải bộ lọc tên bean.

## 12. Tài liệu Spring chính thức

- [Spring Framework — Introductions với @DeclareParents](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html): interface được khai báo từ field type, type pattern và `defaultImpl`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK proxy, CGLIB và giới hạn khi gọi qua proxy.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): đăng ký aspect và enable auto-proxy.
- [Spring Framework — DeclareParentsAdvisor API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/aspectj/DeclareParentsAdvisor.html): hạ tầng introduction advisor.
- [Spring Framework — AopUtils API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html): phương thức xác định Spring AOP proxy và target class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-interceptors-009">Kiểm thử xung đột interface, kiểu ép và phạm vi áp dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP Introduction: kiểm thử xung đột interface, kiểu ép và phạm vi áp dụng

## 1. Mục tiêu của bài

Khi dùng **Introduction trong Spring AOP**, một aspect có thể bổ sung interface và implementation cho **proxy của Spring bean** mà không sửa lớp nghiệp vụ. Tính năng này thường được cấu hình với `@DeclareParents` hoặc, ở API mức thấp, với `IntroductionAdvisor` và `IntroductionInterceptor`.

Một introduction chỉ có ích khi client thực sự lấy đúng proxy và interface thêm vào không phá vỡ hợp đồng hiện có. Vì vậy, trước khi đưa vào ứng dụng, cần kiểm thử ba nhóm rủi ro:

1. **Xung đột interface**: interface mới có method trùng chữ ký với interface/target cũ nhưng khác kiểu trả về, ngữ nghĩa hoặc implementation; nhiều introductions có thể tranh cùng một method.
2. **Kiểu ép (casting)**: Spring proxy có thực sự implement interface vừa thêm? Có ép được về class target cụ thể không? Client dùng interface hay phụ thuộc vào class?
3. **Phạm vi áp dụng**: type pattern trong `@DeclareParents` có match đúng những bean cần thêm interface và bỏ qua bean ngoài phạm vi không? Bean tạo bằng `new` có bị hiểu nhầm là bean đã được intro không?

Bài này tập trung hoàn toàn vào **Spring AOP runtime proxy và kiểm thử Introduction**, không trình bày lại kiến thức AOP nói chung hoặc cơ chế AspectJ compile-time/load-time weaving.

## 2. Cơ chế Spring cần hiểu để viết test đúng

Một ví dụ cấu hình:

~~~java
@Aspect
public class MonitoringIntroduction {
    @DeclareParents(
        value = "demo.intro.OrderService",
        defaultImpl = DefaultMonitorable.class
    )
    public static Monitorable introduced;
}
~~~

Đây là **đoạn minh họa**, các lớp sẽ được định nghĩa đầy đủ ở phần thực hành. Ý nghĩa từng phần:

- `@Aspect` đánh dấu một lớp chứa khai báo mà Spring AOP có thể nhận diện khi đã bật auto-proxy.
- `@DeclareParents` mô tả introduction: **kiểu trường** `Monitorable` chính là interface được thêm vào proxy; `defaultImpl` là lớp hiện thực interface ấy; `value` là **AspectJ type pattern** dùng để chọn kiểu target.
- `value = "demo.intro.OrderService"` chỉ match kiểu target được đặt tên chính xác trong ví dụ. Nếu muốn bao gồm subtype, có thể dùng toán tử `+` trong type pattern phù hợp; nếu dùng wildcard quá rộng, số bean bị ảnh hưởng tăng và phải test phạm vi rộng hơn.
- Client phải lấy bean từ Spring container và gọi **qua proxy**. Một instance tạo thủ công bằng `new OrderService(...)` không tự có interface `Monitorable`.

Trong Spring AOP, introduction là phần của proxy. Nó **không sửa bytecode của `OrderService` để lớp gốc thật sự khai báo `implements Monitorable`**. Bởi vậy kết quả `instanceof`/cast có thể khác nhau giữa bean lấy từ context và đối tượng do `new` tạo.

~~~text
ApplicationContext
   |
   +-- bean "orderService" có kiểu target OrderService
           |
           +-- @DeclareParents type pattern match
                    |
                    v
              Spring AOP proxy
              |             |
       OrderPort API    Monitorable API
              |             |
       target method    delegate/mixin method

new OrderService(...) -> object gốc; KHÔNG tự có Monitorable
~~~

Introduction được quản lý qua advisor ở mức **class**, không phải pointcut ghép vào từng method tùy ý. `IntroductionAdvisor` dùng `ClassFilter` để giới hạn kiểu target; một `IntroductionInterceptor` thực thi phần method mới. Điều này khác với `MethodInterceptor` thường dựa vào method matcher.

## 3. Rủi ro xung đột interface

### 3.1. Trùng chữ ký nhưng khác kiểu trả về

Giả sử service đã được expose qua interface:

~~~java
public interface ExistingStatus {
    String state();
}
~~~

Introduction định bổ sung:

~~~java
public interface NumericStatus {
    int state();
}
~~~

Hai method cùng tên `state()`, cùng tập tham số rỗng nhưng kiểu trả về `String` và `int` không tương thích. Với **JDK dynamic proxy** được yêu cầu implement cả hai interface, đây là cấu hình không hợp lệ. Không thể kỳ vọng JVM tự chọn một return type dựa trên kiểu ép ở vị trí gọi. Khi tạo proxy, Java/Spring có thể báo lỗi trước khi có bất kỳ lời gọi nghiệp vụ nào.

**Quy tắc kiểm thử:** kiểm tra collision khi dựng context/proxy và xác định không thể khởi động một proxy JDK với hợp đồng mâu thuẫn. Đừng chỉ viết test cho hai lời gọi tách rời khi proxy chưa được dựng thành công.

### 3.2. Method trùng tên, trả về tương thích nhưng khác ý nghĩa

Nguy hiểm hơn là hai interface cùng khai báo `boolean enabled()` hoặc `String name()` nhưng đại diện cho hai ý nghĩa khác nhau. Proxy có thể tạo thành công; tuy nhiên interceptor/delegate nào thực thi method phải phụ thuộc vào cấu hình thực tế. Điều này có thể tạo **sai logic âm thầm**, khó phát hiện hơn lỗi tạo proxy.

Tài liệu API Spring nêu rõ introduced interface có thể che khuất implementation cùng interface trên target khi dùng introduction delegate. Do đó không nên giả định nếu target đã có method thì target sẽ luôn được gọi. Hãy test qua từng giao diện client thực tế sử dụng và kiểm tra giá trị trả về/side effect.

Ví dụ cần test:

| Cặp contract | Rủi ro | Test cần làm |
|---|---|---|
| `String state()` và `int state()` | Không thể cung cấp cả hai bằng JDK proxy | Khởi tạo proxy phải bị chặn; kiểm tra nguyên nhân là chữ ký không tương thích |
| Hai `String label()` mang nghĩa khác nhau | Proxy có thể tạo, nhưng method được phân phối sai delegate | Gọi qua cả interface và khẳng định kết quả đúng từng contract |
| Target vốn đã implement interface được introduce | Introduction có thể thay đổi đường thực thi của method cũ | Test cả trước/sau introduction, advisor và state |
| Hai introductions thêm cùng interface/method | Thứ tự/đường xử lý có thể không đúng kỳ vọng | Test dispatch và state trên từng bean, không dựa vào thứ tự ngẫu nhiên |

Cách phòng tránh ưu tiên là **đặt tên interface/method đủ rõ**, tách vai trò nghiệp vụ và vai trò mixin, kiểm tra trước public API của bean; không dùng `@Order` để “chữa” hai interface vốn có hợp đồng không tương thích.

### 3.3. Không dùng class cast để suy ra giải pháp cho collision

Ép proxy về `ExistingStatus` hoặc `NumericStatus` không biến một cặp chữ ký bất hợp lệ thành hợp lệ. Kiểu proxy được quyết định lúc tạo; client cast chỉ kiểm tra đối tượng runtime có implement kiểu mong muốn hay không. Nếu Java proxy không thể implement cả hai interface, giải pháp là thiết kế lại contract hoặc đổi chiến lược composition/adapter; không phải thêm cast.

## 4. Kiểu ép: JDK proxy khác CGLIB

Spring AOP thường dùng **JDK dynamic proxy** cho target có interface khi không ép proxy theo class; trong cấu hình class-based proxy, Spring dùng **CGLIB**.

| Phép kiểm tra trên bean đã được introduction match | JDK proxy | CGLIB proxy |
|---|---|---|
| `bean instanceof BusinessInterface` | Đúng nếu interface được proxy expose | Đúng nếu class implement interface |
| `bean instanceof IntroducedInterface` | Đúng nếu introduction được áp dụng thành công | Đúng nếu introduction được áp dụng thành công |
| `bean instanceof ConcreteTargetClass` | Thường **sai**: JDK proxy không kế thừa class target | Thường **đúng**: CGLIB proxy kế thừa class target hợp lệ |
| `AopUtils.isJdkDynamicProxy(bean)` | `true` | `false` |
| `AopUtils.isCglibProxy(bean)` | `false` | `true` |

Một introduction được cấu hình đúng **không đòi phải ép proxy sang class target**. Client nên khai báo dependency bằng interface nghiệp vụ hoặc interface được introduce, ví dụ:

~~~java
OrderPort order = context.getBean("orderService", OrderPort.class);
Monitorable mixin = context.getBean("orderService", Monitorable.class);
~~~

Đây là đoạn minh họa dựa trên project hoàn chỉnh ở phần sau.

Nếu ứng dụng có code kiểu:

~~~java
OrderService concrete = (OrderService) context.getBean("orderService");
~~~

thì với JDK proxy, ép kiểu này có thể ném `ClassCastException` **dù introduction hoạt động hoàn toàn đúng**. Muốn client thấy một interface mới thì lấy proxy và ép về **interface mới**; không dùng cast về concrete type để “mở” interface.

CGLIB cũng có giới hạn: lớp `final` không thể được subclass; method `final`/`private` không được advice theo cách proxy subclass thông thường; những method không nằm trong đường gọi qua proxy cũng không được áp dụng advice. Chọn CGLIB chỉ để cứu một cast thường là dấu hiệu dependency đang quá phụ thuộc implementation.

Lưu ý cấu hình ứng dụng có thể đã ép class-based proxy bởi cơ chế auto-proxy khác, ví dụ quản lý transaction. Vì vậy test phải xác nhận bằng `AopUtils`, không suy ra JDK/CGLIB chỉ từ một annotation đơn lẻ.

## 5. Phạm vi áp dụng là phạm vi của kiểu target

Trong `@DeclareParents`, `value` là type pattern dành cho kiểu target, **không phải bean-name pattern hay method pointcut**.

- `demo.intro.OrderService` trong ví dụ dưới chọn bean có target class `OrderService`.
- Hai bean khác tên nhưng cùng target class đều phù hợp.
- Bean target class `ArchiveService` không match dù cùng implement `OrderPort`.
- Đối tượng `new OrderService("manual")` không nằm trong IoC container; nó không trở thành Spring proxy chỉ vì class có match pattern.
- Nếu dùng wildcard/type hierarchy quá rộng, một introduction có thể gắn vào những bean không mong đợi. Khi đó phải có negative tests cho từng vùng cần loại trừ.

Nếu cần giới hạn bằng tên bean/qualifier, đừng giả định có thể viết tên bean vào `@DeclareParents.value`. Có thể cần thay mô hình bean, tách kiểu target, cấu hình advisor với `ClassFilter` hoặc thiết kế interface rõ hơn tùy nhu cầu. Đối với introduction, **class matching mới là biên giới cần kiểm thử**, không phải chỉ tên bean.

### So sánh với điều kiện `this(...)` và `target(...)`

Trong Spring AOP pointcut:
- `this(Monitorable)` kiểm tra proxy có là instance của kiểu `Monitorable` hay không.
- `target(Monitorable)` xét target thực tế; nếu target gốc không implement `Monitorable`, việc proxy có interface mới **không làm target gốc tự implement** interface đó.

Hai điều kiện có thể cho kết quả khác nhau sau introduction. Nếu advice đi kèm intro dựa trên pointcut interface, hãy kiểm thử lời gọi thực tế với `this(...)`/`target(...)` phù hợp thay vì đổi qua lại theo cảm tính.

## 6. Ví dụ chạy được: test positive, negative, casting, collision

Ví dụ sau dùng Spring Framework 6.2.19 và Java 17+. Toàn bộ class nghiệp vụ được đặt cùng package `demo.intro`; các class có quyền truy cập `public`, để không phụ thuộc vào chi tiết reflection của package-private types. Chạy lần lượt hai `ApplicationContext` độc lập nhằm kiểm tra JDK và CGLIB mà không để chế độ proxy của context này tác động context khác.

### 6.1. Cấu hình Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-introduction-contracts'
~~~

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
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-aop:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.22'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.intro.IntroductionDemo'
}
~~~

Với Gradle wrapper được tạo/sẵn có cho project, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Dependencies cung cấp auto-proxy, Spring AOP và parser annotation/pointcut AspectJ mà Spring sử dụng; ví dụ **không chạy AspectJ weaving agent**.

### 6.2. Interface nghiệp vụ và implementation

Tạo `src/main/java/demo/intro/OrderPort.java`:

~~~java
package demo.intro;

public interface OrderPort {
    String id();
}
~~~

Tạo `src/main/java/demo/intro/OrderService.java`:

~~~java
package demo.intro;

public class OrderService implements OrderPort {
    private final String id;

    public OrderService(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
~~~

Tạo `src/main/java/demo/intro/ArchiveService.java`:

~~~java
package demo.intro;

public class ArchiveService implements OrderPort {
    @Override
    public String id() {
        return "archive";
    }
}
~~~

Cả hai service đều implement `OrderPort`. Chúng cố ý có **target class khác nhau** để negative test kiểm tra đúng type pattern thay vì vô tình kiểm tra tên bean.

### 6.3. Interface được introduction bổ sung

Tạo `src/main/java/demo/intro/Monitorable.java`:

~~~java
package demo.intro;

public interface Monitorable {
    void record();
    int count();
}
~~~

Tạo `src/main/java/demo/intro/DefaultMonitorable.java`:

~~~java
package demo.intro;

public class DefaultMonitorable implements Monitorable {
    private int count;

    @Override
    public void record() {
        count++;
    }

    @Override
    public int count() {
        return count;
    }
}
~~~

`DefaultMonitorable` dùng state mutable để **minh họa** sự độc lập giữa mixin instances; bản production có thể cần đồng bộ phù hợp nếu có nhiều luồng đồng thời gọi cùng một proxy. Không dùng implementation này làm counter production khi chưa xử lý concurrency.

### 6.4. Aspect, hai kiểu proxy và chương trình tự kiểm thử

Tạo `src/main/java/demo/intro/IntroductionDemo.java`:

~~~java
package demo.intro;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class IntroductionDemo {

    @Aspect
    public static class MonitoringAspect {
        @DeclareParents(
            value = "demo.intro.OrderService",
            defaultImpl = DefaultMonitorable.class
        )
        public static Monitorable introduced;
    }

    @Configuration(proxyBeanMethods = false)
    public static class Beans {
        @Bean
        public OrderService orderService() {
            return new OrderService("main");
        }

        @Bean
        public OrderService anotherOrderService() {
            return new OrderService("second");
        }

        @Bean
        public ArchiveService archiveService() {
            return new ArchiveService();
        }

        @Bean
        public MonitoringAspect monitoringAspect() {
            return new MonitoringAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class JdkConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class CglibConfig {
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkContext(
            String label, Class<?> config, boolean expectConcreteCast) {

        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            Object matched = ctx.getBean("orderService");
            Object matchedAgain = ctx.getBean("anotherOrderService");
            Object excluded = ctx.getBean("archiveService");
            Object manuallyConstructed = new OrderService("manual");

            require(matched instanceof OrderPort,
                    "Business interface missing");
            require(matched instanceof Monitorable,
                    "Introduced interface missing on matched bean");
            require(matchedAgain instanceof Monitorable,
                    "Second bean of matching target type must be introduced");
            require(!(excluded instanceof Monitorable),
                    "Introduction leaked to non-matching target class");
            require(!(manuallyConstructed instanceof Monitorable),
                    "A raw object must not be treated as Spring proxy");
            require((matched instanceof OrderService) == expectConcreteCast,
                    "Concrete-class cast does not match chosen proxy mode");

            boolean jdk = AopUtils.isJdkDynamicProxy(matched);
            boolean cglib = AopUtils.isCglibProxy(matched);
            require(jdk != cglib, "Expected one Spring AOP proxy strategy");

            // Spring must return the same bean for its introduced interface.
            Monitorable first = ctx.getBean("orderService", Monitorable.class);
            Monitorable second = (Monitorable) matchedAgain;
            require(first == matched, "Typed lookup returned another bean");

            first.record();
            require(first.count() == 1, "First mixin did not keep its state");
            require(second.count() == 0, "Mixin state leaked to other bean");

            OrderPort service = (OrderPort) matched;
            require("main".equals(service.id()), "Original contract broken");

            System.out.println(label + ".proxy=" + (jdk ? "JDK" : "CGLIB"));
            System.out.println(label + ".introduced=true");
            System.out.println(label + ".concreteCast="
                    + (matched instanceof OrderService));
            System.out.println(label + ".outside=false");
            System.out.println(label + ".raw=false");
            System.out.println(label + ".counts="
                    + first.count() + "," + second.count());
        }
    }

    public interface TextStatus {
        String state();
    }

    public interface NumericStatus {
        int state();
    }

    public static class TextStatusTarget implements TextStatus {
        @Override
        public String state() {
            return "ready";
        }
    }

    public static class NumericStatusDelegate implements NumericStatus {
        @Override
        public int state() {
            return 1;
        }
    }

    private static void verifyInterfaceConflict() {
        var factory = new ProxyFactory(new TextStatusTarget());
        factory.setInterfaces(TextStatus.class);
        factory.setProxyTargetClass(false); // Cố ý chọn JDK proxy.

        var introduction = new DelegatingIntroductionInterceptor(
                new NumericStatusDelegate());
        factory.addAdvisor(new DefaultIntroductionAdvisor(
                introduction, NumericStatus.class));

        boolean rejected = false;
        try {
            factory.getProxy();
        } catch (RuntimeException expected) {
            // JDK proxy không thể implement hai state() có
            // return type String và int không tương thích.
            rejected = true;
        }

        require(rejected, "Incompatible interfaces must be rejected");
        System.out.println("conflict.rejected=" + rejected);
    }

    public static void main(String[] args) {
        checkContext("jdk", JdkConfig.class, false);
        checkContext("cglib", CglibConfig.class, true);
        verifyInterfaceConflict();
    }
}
~~~

### 6.5. Kết quả mong đợi và ý nghĩa

Các dòng do chương trình `System.out.println` tạo ra theo logic test:

~~~text
jdk.proxy=JDK
jdk.introduced=true
jdk.concreteCast=false
jdk.outside=false
jdk.raw=false
jdk.counts=1,0
cglib.proxy=CGLIB
cglib.introduced=true
cglib.concreteCast=true
cglib.outside=false
cglib.raw=false
cglib.counts=1,0
conflict.rejected=true
~~~

Có thể có log khởi động độc lập với các dòng trên tùy cấu hình logging. Chương trình cũng dùng `AssertionError` để dừng nếu bất kỳ điều kiện nào sai; không chỉ in thông báo thành công theo kịch bản giả định.

**Tại sao có hai bean `OrderService`?** Cả hai target có cùng class và đều được introduction dù tên bean khác nhau; đồng thời counter của mixin thứ nhất không làm tăng counter của bean thứ hai.

**Tại sao có `ArchiveService`?** Nó cũng implement `OrderPort` nhưng target class không match `demo.intro.OrderService`, nên introduction không lan sang đó. Đây là negative test quan trọng khi kiểm tra wildcard/type pattern.

**Tại sao `new OrderService(...)` không có `Monitorable`?** Spring AOP không biến đổi class bytecode gốc; chỉ proxy được tạo trong context expose interface mới.

**Tại sao JDK cast về `OrderService` sai?** Proxy là đối tượng implement các interface, không kế thừa `OrderService`. Trái lại CGLIB class-based proxy có thể là subclass của `OrderService` khi các điều kiện proxying cho phép.

**Tại sao phần conflict dùng `ProxyFactory`?** Nó cô lập xung đột chữ ký trên hai interface trong Spring AOP API, không cần làm toàn bộ application context thất bại. `DefaultIntroductionAdvisor` phối hợp `DelegatingIntroductionInterceptor` để khai báo interface `NumericStatus`, trong khi target vẫn expose `TextStatus`. Hai method `state()` với return type không tương thích bị JDK proxy từ chối. Test chỉ dựa vào sự thất bại (`RuntimeException`), **không khóa cứng thông điệp exception**, vì cách Spring/JDK bọc lỗi có thể thay đổi. Nếu không bị từ chối, test cố ý thất bại bằng `AssertionError`.

Ví dụ này kiểm chứng hành vi cho Spring 6.2.x ở chế độ auto-proxy đang chỉ định. Trong ứng dụng có Spring Boot hoặc nhiều auto-proxy config, lựa chọn proxy thực tế có thể khác và cần kiểm tra bằng `AopUtils`.

## 7. Bổ sung JUnit tests cho project thật

Ví dụ `main()` kiểm thử toàn bộ hành vi trọng tâm. Khi đưa vào CI, chuyển từng nhóm `require(...)` thành test JUnit để biết chính xác nhóm nào hỏng:

~~~groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
~~~

Đây là **đoạn thêm vào** `build.gradle` đã có, không phải một file Gradle thay thế hoàn chỉnh. Với JUnit 5, nên có những test riêng:

| Test | Thiết lập | Assertion cần có |
|---|---|---|
| `introducesOnlyMatchingTargetClass` | Hai `OrderService`, một `ArchiveService` | Hai bean đầu `instanceof Monitorable`, bean ngoài phạm vi không |
| `introducedInterfaceAccessibleThroughContainer` | Context có `@DeclareParents` | `getBean(name, Monitorable.class)` thành công và cùng proxy với bean theo tên |
| `rawInstanceNotIntroduced` | `new OrderService(...)` | Không là `Monitorable` |
| `jdkProxySupportsInterfaceNotConcreteCast` | `proxyTargetClass=false` trong context cô lập | `isJdkDynamicProxy=true`, cast interface thành công, cast target class thất bại |
| `cglibProxyAllowsConcreteTypeWherePossible` | `proxyTargetClass=true` với class không `final` | `isCglibProxy=true`, có cả interface mới lẫn kiểu concrete |
| `stateIsolationAcrossMatchingBeans` | Hai target bean khác nhau | Counter mixin của bean A không thay đổi bean B |
| `incompatibleMethodSignaturesAreRejected` | `TextStatus.state(): String` và `NumericStatus.state(): int` | Proxy JDK không được khởi tạo thành công |
| `originalBehaviorPreserved` | Lấy `OrderPort` qua proxy | Method nghiệp vụ trả đúng `id()`, không bị intro thay thế |
| `signatureCollisionWithCompatibleReturnNeedsBehaviorTest` | Hai interface có cùng chữ ký nhưng ngữ nghĩa khác nhau | Giá trị/side effect đúng khi gọi qua từng interface, hoặc cấu hình được chủ động từ chối |

Khi cần kiểm thử xung đột interface có method hợp lệ nhưng ý nghĩa khác nhau, đừng chỉ assert khởi động thành công. Hãy khẳng định trực tiếp số lần gọi delegate, kết quả, state và contract từ cả hai góc nhìn interface.

### Test kiểu ép nên dùng `assertThrows`

Với JDK proxy, phép ép `(OrderService) proxy` nên bị kiểm tra tường minh bằng một negative assertion, ví dụ trong bài test JUnit:

~~~java
assertThrows(ClassCastException.class, () -> {
    OrderService ignored = (OrderService) context.getBean("orderService");
});
~~~

Đây là **snippet** nằm trong method test JUnit có `context` và static import `assertThrows`; nó minh họa điều kiện cần khẳng định. Trong Spring, `getBean("orderService", OrderService.class)` có thể báo `BeanNotOfRequiredTypeException` thay vì `ClassCastException` vì API của container tự kiểm tra required type. Test phải phân biệt **Java cast** với **typed bean lookup**.

### Test phạm vi nên kiểm tra cả bằng type pattern rộng

Với production pattern như `com.example.billing..*Service+`, nên chọn ít nhất một bean trong package match, một subclass (nếu `+` có chủ đích), một bean ngoài package và một bean có tên tương tự nhưng class khác. Assert **đúng cả tập được áp dụng lẫn tập bị loại trừ**. Tránh dùng test chỉ có một bean match: pattern sai quá rộng vẫn có thể qua test đó.

## 8. Kiểm tra nguyên nhân khi test thất bại

| Biểu hiện | Giả thuyết cần kiểm tra | Hành động |
|---|---|---|
| `(Monitorable) bean` ném `ClassCastException` | Bean không được proxy, aspect không được đăng ký, type pattern không match hoặc lấy raw target | Xem bean được lấy từ context chưa; kiểm tra `@EnableAspectJAutoProxy`, `@Aspect` bean, `@DeclareParents` và `AopUtils` |
| `getBean(name, Monitorable.class)` báo không đúng kiểu | Proxy không expose interface intro | Kiểm tra kết quả getBean theo tên, type pattern, advisor và proxy thật |
| Cast về target class hỏng | JDK proxy | Chuyển dependency sang interface; nếu thật sự cần class, đánh giá class-based proxy và hạn chế `final` |
| Cả bean ngoài phạm vi cũng có interface intro | Pattern quá rộng hoặc advisor khác áp dụng introduction | Thu hẹp type pattern; kiểm tra các `IntroductionAdvisor` hiện có |
| Mixin counter giữa hai bean bị lẫn | Delegate dùng chung/state tĩnh hoặc cấu hình per-instance sai | Kiểm tra lifecycle của delegate/mixin, dùng test trên hai bean và concurrency |
| Xung đột method nhưng startup vẫn chạy | Return type tương thích nên proxy có thể tạo; rủi ro nằm ở dispatch/semantics | Bổ sung test hành vi, xem interceptor/advisor chain |
| Context báo lỗi khi thêm intro | Interface collision, default implementation không hợp contract, class/proxy không hợp lệ | Kiểm tra full stack trace, signature method và khả năng proxy |
| Chỉ một số lời gọi thấy advice hoặc intro | Gọi trực tiếp raw target, self-invocation hoặc lấy nhầm bean | Dùng bean proxy từ context; kiểm tra luồng gọi qua proxy |

Để quan sát proxy trong ứng dụng thật, có thể dùng snippet sau **sau khi context đã khởi động**:

~~~java
Object bean = context.getBean("orderService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Snippet cần biến `context`, import `AopUtils` và bean `orderService` do ứng dụng thật khai báo; **không phải một chương trình độc lập**. Advisor có mặt chỉ chứng minh metadata proxy; vẫn phải gọi method và assert hành vi để xác nhận introduction hoạt động đúng.

## 9. Lỗi thiết kế thường gặp và cách phòng tránh

**Lỗi 1 — Cho rằng introduction làm class thực sự implement interface.** Thực tế interface được expose bởi proxy Spring AOP; object tự tạo bên ngoài container không thay đổi. Test cả proxy và raw instance.

**Lỗi 2 — Ép về concrete class trong khi AOP dùng JDK proxy.** Một proxy có thể implement `OrderPort` và `Monitorable` nhưng vẫn không phải `OrderService`. Ưu tiên inject theo interface.

**Lỗi 3 — Đặt pattern như một tên bean.** `@DeclareParents.value` là type pattern; nhiều bean của cùng kiểu cùng bị tác động. Test theo target class, không theo tên.

**Lỗi 4 — Đưa interface trùng method với target/interface cũ mà không xem contract.** Return type xung đột có thể làm proxy không tạo được; return type tương thích nhưng nghĩa khác nhau có thể tạo lỗi hành vi âm thầm. Kiểm tra signature và dispatch trước khi triển khai.

**Lỗi 5 — Giả định mọi intro có state đều an toàn khi được chia sẻ.** Introduction có thể cần state riêng cho mỗi target/proxy. Với mutable state, xem xét per-instance delegate, thread safety và lifecycle, nhất là trong singleton Spring beans phục vụ đồng thời.

**Lỗi 6 — Chuyển sang CGLIB một cách đại trà để cứu cast.** CGLIB có hạn chế `final`, visibility và các ràng buộc JVM; việc thay proxy strategy có thể ảnh hưởng nhiều bean. Sửa API sử dụng interface thường rõ hơn.

**Lỗi 7 — Chỉ kiểm tra `instanceof` trên một bean.** Điều đó không chứng minh kiểu method được gọi đúng, không rò state, không match nhầm bean và không xung đột với advisor khác.

## 10. Checklist trước khi chấp nhận một Introduction

- [ ] Interface mới là một contract công khai có ý nghĩa riêng; method không xung đột tên, tham số, return type hoặc semantics với interface đã có.
- [ ] `defaultImpl` (hoặc delegate) thực sự implement toàn bộ interface được thêm và có lifecycle phù hợp.
- [ ] Test JDK/CGLIB khẳng định những kiểu cast mà caller thật sự cần; production dùng interface ở nơi có thể.
- [ ] Test positive bao trùm tất cả target class/subclass dự định match; test negative bao trùm class/package/bean không muốn match.
- [ ] Test raw object và bean lấy qua Spring context chứng minh Introduction chỉ xuất hiện trên proxy.
- [ ] Test original business method vẫn đúng; introduced method thực thi đúng delegate và giữ state theo từng bean.
- [ ] Test giao diện khác có method trùng chữ ký; với kiểu trả về xung đột, không cho phép cấu hình lỗi lọt qua CI.
- [ ] Test exception, concurrency và thứ tự advisor thêm vào nếu introduced method có state hoặc tác động nghiệp vụ.
- [ ] Quan sát `AopUtils` và advisor chain của bean được inject thật trong ứng dụng, không suy luận từ annotation đơn lẻ.

**Tiêu chí đạt:** người dùng interface nghiệp vụ tiếp tục nhận kết quả cũ; người dùng interface được introduction nhận hành vi mới; class cast và giới hạn kiểu proxy được hiểu rõ; bean không thuộc type pattern không bị thay đổi; mọi xung đột interface được phát hiện trước triển khai.

## 11. Tài liệu Spring Framework

- Spring Framework 6.2 — Introduction với `@DeclareParents`: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/introductions.html
- Spring Framework 6.2 — Introduction Advice và `IntroductionAdvisor`: https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html
- Spring Framework 6.2 — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework Javadoc — `AopUtils`: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html
- Spring Framework Javadoc — `DefaultIntroductionAdvisor`: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html

</details>

- [Quay lại đầu trang](#back-to-top)
