<a id="back-to-top"></a>

# Pointcut trong Spring

## Menu
- [execution để chọn phương thức của bean theo chữ ký](#spring-pointcut-expressions-001)
- [within để giới hạn lớp hoặc gói của bean đích](#spring-pointcut-expressions-002)
- [this và target trong mô hình proxy của Spring](#spring-pointcut-expressions-003)
- [args theo kiểu đối số thực tế](#spring-pointcut-expressions-004)
- [@annotation để chọn phương thức mang annotation](#spring-pointcut-expressions-005)
- [@within và @target theo annotation của kiểu](#spring-pointcut-expressions-006)
- [@args theo annotation của kiểu đối số](#spring-pointcut-expressions-007)
- [bean để chọn theo tên bean trong Spring](#spring-pointcut-expressions-008)
- [Kết hợp điều kiện bằng &amp;&amp;, || và !](#spring-pointcut-expressions-009)
- [Mẫu tên, kiểu con, overload và danh sách tham số](#spring-pointcut-expressions-010)
- [Chọn đúng độ rộng pointcut để tránh can thiệp bean ngoài ý muốn](#spring-pointcut-expressions-011)
- [Những pointcut designator của AspectJ không được Spring AOP hỗ trợ](#spring-pointcut-expressions-012)

## <a id="spring-pointcut-expressions-001">execution để chọn phương thức của bean theo chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# execution để chọn phương thức của bean theo chữ ký trong Spring AOP

## 1. Mục tiêu bài học

Trong **Spring AOP**, pointcut quyết định **method execution nào của Spring-managed bean** có thể kích hoạt advice. Trong các pointcut designator mà Spring hỗ trợ, **execution(...)** là lựa chọn phổ biến nhất khi cần chọn phương thức dựa vào **chữ ký (method signature)**: quyền truy cập, kiểu trả về, lớp/interface khai báo, tên và danh sách kiểu tham số.

Sau khi học, bạn có thể:

1. Đọc và tự viết **execution(...)** theo cấu trúc đầy đủ hoặc rút gọn.
2. Phân biệt ký tự đại diện **\*** với **..** khi dùng trong kiểu, tên method, package và danh sách tham số.
3. Viết pointcut chọn một method cụ thể, một nhóm tên method hoặc toàn bộ service package trong **Spring ApplicationContext**.
4. Giải thích vì sao **execution** dựa vào kiểu khai báo của tham số khác với **args(...)** dựa trên argument tại thời điểm chạy.
5. Sử dụng **@Pointcut** đặt tên biểu thức và tái dùng trong **@Before**.
6. Chạy dự án Java 17+ có ví dụ match và không match, kiểm tra thứ tự log, rồi chẩn đoán các giới hạn do Spring AOP proxy.

**Phạm vi:** Chỉ tập trung vào **Spring Framework @AspectJ-style Spring AOP**, không chuyển sang các khái niệm AspectJ weaving tổng quát. Ngay cả khi dùng cú pháp pointcut của AspectJ, Spring AOP trong bài vẫn hoạt động bằng **proxy** và chỉ xử lý **method execution join points trên Spring beans**.

## 2. execution(...) chọn điều gì?

Hình dung client gọi một Spring bean:

~~~text
Client
  |
  | gọi method trên bean được Spring inject
  v
Spring AOP proxy
  |
  +-- advisor xét biểu thức execution(...)
  |      |
  |      +-- method signature khớp  -> advice có thể chạy
  |      +-- không khớp             -> không chạy advice này
  |
  v
Target method thực thi
~~~

Biểu thức không trực tiếp thay đổi thân method. Nó là **bộ lọc các method executions** mà advice có thể áp dụng. Ví dụ:

~~~java
@Before("execution(* demo.OrderApi.create(..))")
public void beforeCreate() {
    System.out.println("Trước create");
}
~~~

Pointcut này biểu thị những method-execution join points được Spring AOP hỗ trợ và có signature khớp method **create** trên **demo.OrderApi**, bất kể kiểu trả về hoặc số lượng tham số. Nếu một lời gọi đến create đi qua proxy được Spring cấu hình với advisor tương ứng, advice có thể chạy trước target.

Các điều kiện cần phân biệt:

- **Biểu thức parse hợp lệ**: cú pháp execution được AspectJ pointcut parser của Spring hiểu.
- **Signature match**: chữ ký phương thức thỏa biểu thức.
- **Bean eligible**: bean là Spring-managed bean và được Spring AOP xem xét áp dụng advisor.
- **Invocation qua proxy**: caller đi qua đối tượng proxy ở điểm có thể intercept.
- **Advice chạy**: tất cả các điều kiện trên cùng thỏa, cộng với điều kiện runtime khác nếu pointcut được kết hợp với các designator động.

Một expression đúng vẫn có thể không tạo log nếu bean không thuộc ApplicationContext, advice chưa được đăng ký, hoặc method được gọi bằng **this.method()** bên trong target.

## 3. Cú pháp đầy đủ của execution

Dạng khái quát được tài liệu Spring trình bày như sau:

~~~text
execution(
    modifiers-pattern?
    ret-type-pattern
    declaring-type-pattern?name-pattern(param-pattern)
    throws-pattern?
)
~~~

Dấu **?** trong sơ đồ trên chỉ rằng thành phần **có thể bỏ qua**, **không phải ký tự bạn cần gõ** vào mọi pointcut.

| Thành phần | Vai trò | Ví dụ |
| --- | --- | --- |
| **modifiers-pattern?** | Ràng buộc modifier của method, khi cần | public |
| **ret-type-pattern** | Kiểu trả về của method; **bắt buộc** | void, java.lang.String, * |
| **declaring-type-pattern?** | Class/interface hoặc pattern type sở hữu method | demo.OrderApi. |
| **name-pattern** | Tên method, có thể có wildcard | create, get*, * |
| **param-pattern** | Kiểu và số lượng tham số, **bắt buộc** dưới dạng ngoặc | (), (java.lang.String), (*), (..) |
| **throws-pattern?** | Ràng buộc kiểu ngoại lệ được khai báo trong throws clause | Chỉ dùng khi thật sự cần lọc checked-exception signature |

Trong mã thực tế, biểu thức thường được viết trên một dòng:

~~~java
execution(public java.lang.String demo.OrderApi.create(java.lang.String))
~~~

Đọc từ trái sang phải:

1. **public**: method phải công khai.
2. **java.lang.String**: kiểu trả về khai báo phải là String.
3. **demo.OrderApi.**: chọn method được mô tả qua type/interface OrderApi trong package demo.
4. **create**: tên chính xác.
5. **(java.lang.String)**: method có **đúng một tham số được khai báo kiểu String**.

Dấu chấm sau **demo.OrderApi** là dấu nối giữa **declaring type** và **method name**, không phải ký tự bất kỳ. Nếu không cần giới hạn method ở một type cụ thể, bạn có thể bỏ phần declaring type và chỉ dùng tên method.

Các thành phần được thiết kế để chọn method theo **khai báo**. Chúng không so sánh giá trị argument như mã đơn bằng "A-123"; trường hợp lọc theo giá trị runtime phải dùng cơ chế khác với điều kiện cụ thể và hiểu rõ hiệu ứng của proxy.

## 4. Hiểu chính xác dấu * và dấu ..

### 4.1. Dấu * trong kiểu trả về

~~~java
execution(* demo.OrderApi.create(..))
~~~

Dấu **\*** ở vị trí **ret-type-pattern** nghĩa là **bất kỳ kiểu trả về**, kể cả void. Nếu muốn chọn riêng method trả String:

~~~java
execution(java.lang.String demo.OrderApi.create(..))
~~~

Nếu muốn chọn riêng method trả void:

~~~java
execution(void demo.OrderApi.cancel(..))
~~~

Phần kiểu trả về phải phản ánh **return type được khai báo trong method signature**, không phải kiểu của object mà một nhánh code có thể trả về ở runtime. Ví dụ, một method khai báo trả **Object** nhưng return ra String không được chọn chỉ vì expression ghi **java.lang.String**.

### 4.2. Dấu * trong tên method

~~~java
execution(* demo.OrderApi.get*(..))
~~~

Chọn các method trên OrderApi có tên bắt đầu bằng **get**. Có thể dùng wildcard ở giữa/cuối tên để xây một naming pattern, chẳng hạn:

~~~java
execution(* demo.OrderApi.*Status(..))
~~~

Chọn method có tên kết thúc bằng **Status**.

Không nhầm wildcard của pointcut với toán tử nhân trong Java. Biểu thức nằm trong **chuỗi annotation** và được pointcut parser diễn giải.

### 4.3. Dấu .. trong danh sách tham số

Ba dạng phải nhớ:

~~~text
()   -> không có tham số
(*)  -> đúng một tham số, kiểu bất kỳ
(..) -> không, một hoặc nhiều tham số, với các kiểu bất kỳ
~~~

Ví dụ:

~~~java
execution(* demo.OrderApi.status())
execution(* demo.OrderApi.create(*))
execution(* demo.OrderApi.create(..))
~~~

Nếu method thực sự là **String create(String id)**:

- **create()** không match vì không có tham số.
- **create(*)** match vì có đúng một tham số.
- **create(..)** match vì chấp nhận mọi số tham số, bao gồm một.

Nếu muốn đúng một tham số String, dùng:

~~~java
execution(* demo.OrderApi.create(java.lang.String))
~~~

Nếu muốn hai tham số, tham số thứ nhất tùy kiểu, tham số thứ hai String:

~~~java
execution(* demo.OrderApi.someMethod(*,java.lang.String))
~~~

Đây là **mẫu cú pháp**, không khẳng định interface OrderApi của ví dụ ở mục 7 thực sự có method **someMethod**.

### 4.4. Dấu .. trong package/type pattern

~~~java
execution(* demo.service.*.*(..))
~~~

Chọn các method có chữ ký thuộc các type khớp pattern trong package **demo.service** trực tiếp, không tự mở rộng mọi subpackage.

~~~java
execution(* demo.service..*.*(..))
~~~

Cho phép chọn type trong **demo.service** và các subpackage bên dưới. Cần chú ý vị trí của **..**: ở đây nó nằm trong **declaring-type-pattern**, không phải danh sách tham số.

| Pattern | Đang mở rộng cái gì? |
| --- | --- |
| **demo.service.*.*(..)** | Type trực tiếp trong package demo.service và mọi tên method/số tham số |
| **demo.service..*.*(..)** | Type thuộc demo.service hoặc các subpackage và mọi method/số tham số |
| **demo.OrderApi.*(..)** | Mọi tên method trên type OrderApi, số tham số tùy ý |
| **demo.OrderApi.get*(..)** | Những method tên bắt đầu get |
| **demo.OrderApi.create(..)** | Method tên create, mọi số tham số |
| **demo.OrderApi.create()** | Method create **không** có tham số |

Đừng dùng **..** như wildcard toàn bộ string một cách mơ hồ. Trong **parameter pattern**, nó là **bất kỳ số lượng tham số**; trong **type pattern**, nó mô tả các mức package/type theo cú pháp AspectJ.

## 5. Tập hợp các biểu thức thường dùng với Spring beans

| Yêu cầu chọn method | Biểu thức |
| --- | --- |
| Mọi public method có thể intercept | **execution(public * *(..))** |
| Một method create có đúng một String argument | **execution(* demo.OrderApi.create(java.lang.String))** |
| Mọi overload của create, mọi kiểu trả về | **execution(* demo.OrderApi.create(..))** |
| Một method không có tham số | **execution(* demo.OrderApi.status())** |
| Method trả void, tên cancel, nhận long primitive | **execution(void demo.OrderApi.cancel(long))** |
| Mọi method của interface/type OrderApi | **execution(* demo.OrderApi.*(..))** |
| Method bắt đầu bằng find trên OrderApi | **execution(* demo.OrderApi.find*(..))** |
| Mọi public method của các service trong package cụ thể | **execution(public * demo.service.*.*(..))** |
| Mọi method trong package service và các subpackage | **execution(* demo.service..*.*(..))** |

Tất cả biểu thức trong bảng đều nói về **signature**, không phải việc Spring đã có bean nào. Nếu ứng dụng không đăng ký các service phù hợp, hoặc lời gọi bypass proxy, sẽ không có lời gọi AOP được xử lý.

**Lưu ý về primitive và wrapper**: tham số **long** trong chữ ký không giống tham số **java.lang.Long**. Nếu method định nghĩa **cancel(long id)**, hãy dùng **(long)** thay vì **(java.lang.Long)** khi muốn khớp chính xác declared parameter type. Autoboxing tại vị trí gọi Java không biến chữ ký phương thức thành Long.

## 6. Các ràng buộc riêng của Spring AOP

### 6.1. Chỉ chọn method execution trên Spring-managed beans

Spring AOP không thực hiện AspectJ compiler weaving. Nó không chặn **field access**, **constructor execution**, hoặc tùy ý mọi object tạo bằng **new**. Pointcut như:

~~~java
execution(* demo.OrderApi.*(..))
~~~

không có nghĩa mọi lời gọi Java tới object triển khai OrderApi đều bị aspect kiểm soát. Phải có bean được Spring quản lý và được expose qua proxy khi có advisor phù hợp.

### 6.2. JDK proxy và CGLIB proxy

**JDK dynamic proxy** hoạt động theo interface. Lời gọi public interface method có thể được Spring AOP intercept. Method chỉ có trên implementation nhưng không thuộc interface proxy không được expose qua JDK proxy đó.

**CGLIB class-based proxy** tạo subclass nên có thể intercept public method, và trong những điều kiện thích hợp cả protected hoặc package-visible method có thể override. Nhưng CGLIB không override **private** hay **final** method, cũng không tạo subclass cho final class.

Do đó:

~~~java
execution(public * demo.OrderApi.*(..))
~~~

là lựa chọn dễ kiểm soát cho giao diện service công khai. Nếu một expression bỏ modifier public, nó không tự vượt qua hạn chế kỹ thuật của proxy.

### 6.3. Self-invocation vẫn bỏ qua advice

Nếu **OrderServiceImpl.outer()** gọi **this.create(id)**, lời gọi nội bộ đó không đi qua proxy. Dù create khớp hoàn toàn chữ ký trong execution expression, Spring AOP không tự chạy thêm advice cho lần gọi nội bộ.

Cần phân biệt:

- **Signature match**: create phù hợp với biểu thức.
- **Proxy invocation**: caller gọi create thông qua Spring proxy.
- **Advice execution**: chỉ diễn ra khi có lời gọi phù hợp mà proxy có thể chặn.

### 6.4. Không dùng call(...) để thay thế

Trong **native AspectJ weaving**, có thể phân biệt execution và call join point. Nhưng **Spring AOP chỉ hỗ trợ method execution join points**, không hỗ trợ **call(...)** hoặc các pointcut designator chỉ có trong weaving. Dùng một pointcut designator không được Spring hỗ trợ có thể gây **IllegalArgumentException** khi Spring phân tích cấu hình.

Trong bài này nên tập trung vào **execution(...)**, thay vì tìm cách khắc phục self-invocation bằng một expression call(...).

## 7. Ví dụ Java 17+ chạy được: ba chữ ký match, một lời gọi nội bộ không match

Ví dụ dùng **Spring Framework 6.2.12**, **Java 17+**, **Maven 3.9+** và **aspectjweaver 1.9.24**, không cần Spring Boot. Chúng ta sẽ có một Spring service với bốn method:

- **create(String)** — advice chọn bằng chữ ký chính xác, có public + return String + parameter String.
- **cancel(long)** — advice chọn chính xác **public void** + **long**.
- **find(String)** — advice dùng wildcard **\*** cho kiểu trả về và **(..)** cho tham số.
- **outer(String)** — gọi **this.create(String)** từ bên trong target để đối chiếu giới hạn của proxy.

### 7.1. Cấu trúc dự án

~~~text
spring-aop-execution/
├── pom.xml
└── src/main/java/demo/
    ├── OrderApi.java
    ├── OrderServiceImpl.java
    ├── SignatureAspect.java
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
    <artifactId>spring-aop-execution</artifactId>
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

**spring-context** bao gồm hạ tầng container và kéo theo các module Spring AOP cần thiết. **aspectjweaver** cung cấp các kiểu và parser hỗ trợ AspectJ-style pointcut. Đây vẫn là **proxy-based Spring AOP**, không phải dự án thực hiện AspectJ weaving.

### 7.3. Service interface: chữ ký mà pointcut sẽ chọn

**src/main/java/demo/OrderApi.java**

~~~java
package demo;

public interface OrderApi {
    String create(String id);
    void cancel(long id);
    String find(String id);
    void outer(String id);
}
~~~

Toàn bộ method đều public theo quy tắc của Java interface. Đây là hợp đồng mà JDK proxy có thể công bố.

**create(String)**, **cancel(long)** và **find(String)** có chữ ký khác nhau về tên, return type và kiểu argument. Đối chiếu từng method với biểu thức tương ứng để thấy vì sao advice được chọn.

### 7.4. Implementation có một self-invocation

**src/main/java/demo/OrderServiceImpl.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderApi {

    @Override
    public String create(String id) {
        System.out.println("SERVICE create " + id);
        return "created:" + id;
    }

    @Override
    public void cancel(long id) {
        System.out.println("SERVICE cancel " + id);
    }

    @Override
    public String find(String id) {
        System.out.println("SERVICE find " + id);
        return id;
    }

    @Override
    public void outer(String id) {
        System.out.println("SERVICE outer " + id);
        this.create(id);
    }
}
~~~

**@Service** làm bean có thể được **@ComponentScan** đăng ký. Trong **outer**, lời gọi **this.create(id)** chạy trực tiếp trên target; nó không tự qua Spring AOP proxy.

### 7.5. Aspect dùng execution với ba signature pattern

**src/main/java/demo/SignatureAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SignatureAspect {

    @Pointcut(
        "execution(public java.lang.String " +
        "demo.OrderApi.create(java.lang.String))"
    )
    public void exactCreate() {
    }

    @Before("exactCreate()")
    public void beforeCreate(JoinPoint jp) {
        System.out.println("ADVICE create " + jp.getArgs()[0]);
    }

    @Before("execution(public void demo.OrderApi.cancel(long))")
    public void beforeCancel(JoinPoint jp) {
        System.out.println("ADVICE cancel " + jp.getArgs()[0]);
    }

    @Before("execution(* demo.OrderApi.find(..))")
    public void beforeFind(JoinPoint jp) {
        System.out.println("ADVICE find " + jp.getArgs()[0]);
    }
}
~~~

Giải thích:

1. **exactCreate()** là một **named pointcut**. Tên method giúp advice tái sử dụng biểu thức, còn thân method rỗng không được gọi như logic nghiệp vụ để “kiểm tra”.
2. **@Pointcut** giữ expression theo chữ ký cụ thể: public, String trả về, OrderApi, create và đúng một String parameter.
3. **@Before("exactCreate()")** tham chiếu named pointcut trong cùng aspect.
4. **@Before("execution(public void demo.OrderApi.cancel(long))")** là pointcut viết trực tiếp trong advice, chỉ chọn method cancel được mô tả.
5. **@Before("execution(* demo.OrderApi.find(..))")** dùng wildcard để không ràng buộc kiểu trả về và số lượng tham số, nhưng vẫn giới hạn tên method find.
6. **JoinPoint.getArgs()** giúp in đối số thực tế trong advice. Giá trị đối số dùng để **hiển thị**, không phải điều kiện match của các expression này.

**@Aspect** khai báo aspect, **@Component** đăng ký nó thành Spring bean khi scan. Nếu chỉ giữ @Aspect mà không đăng ký bean, Spring AOP không tự tạo instance aspect để áp dụng advice.

### 7.6. Bật Spring AOP trong ApplicationContext

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

**@ComponentScan** đăng ký **OrderServiceImpl** và **SignatureAspect**. **@EnableAspectJAutoProxy** kích hoạt hạ tầng Spring AOP để tìm advisor và proxy bean phù hợp.

Việc bật auto-proxy không có nghĩa toàn bộ bean trong context đều trở thành proxy. Spring xét khả năng áp dụng advisor cho mỗi bean.

### 7.7. Tạo context và gọi lần lượt các method

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderApi service = context.getBean(OrderApi.class);

            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(service));

            String created = service.create("A1");
            System.out.println("RESULT " + created);

            service.cancel(7L);

            String found = service.find("A1");
            System.out.println("FOUND " + found);

            service.outer("B2");
        }
    }
}
~~~

Chạy từ thư mục có **pom.xml**:

~~~bash
mvn -q clean compile exec:java
~~~

Các dòng **stdout logic kỳ vọng** theo thứ tự lời gọi (không tính các thông báo riêng của Maven/logging):

~~~text
IS_PROXY true
ADVICE create A1
SERVICE create A1
RESULT created:A1
ADVICE cancel 7
SERVICE cancel 7
ADVICE find A1
SERVICE find A1
FOUND A1
SERVICE outer B2
SERVICE create B2
~~~

### 7.8. Diễn giải output

- **IS_PROXY true**: Spring trả OrderApi bean dưới dạng một AOP proxy.
- **ADVICE create A1**: exactCreate() match phương thức create(String) được gọi qua proxy.
- **ADVICE cancel 7**: match chính xác return **void** và primitive parameter **long**.
- **ADVICE find A1**: wildcard return type và **(..)** cho phép match phương thức find có một String parameter.
- **SERVICE outer B2** rồi **SERVICE create B2**: outer được gọi qua proxy nhưng không thuộc ba advice; còn create được gọi trực tiếp bằng **this** trong target nên không có **ADVICE create B2**.

Đây là điểm phân biệt quan trọng: **create(String) khớp pointcut trong cả hai tình huống**, nhưng chỉ lần gọi từ bên ngoài qua proxy mới có advice.

## 8. Bảng match cụ thể từ dự án

| Expression | create(String) | cancel(long) | find(String) | outer(String) |
| --- | --- | --- | --- | --- |
| **execution(public java.lang.String demo.OrderApi.create(java.lang.String))** | Có | Không | Không | Không |
| **execution(public void demo.OrderApi.cancel(long))** | Không | Có | Không | Không |
| **execution(* demo.OrderApi.find(..))** | Không | Không | Có | Không |
| **execution(* demo.OrderApi.*(..))** | Có | Có | Có | Có |
| **execution(* demo.OrderApi.create())** | Không | Không | Không | Không |
| **execution(* demo.OrderApi.create(*))** | Có | Không | Không | Không |
| **execution(public * demo.OrderApi.*(..))** | Có | Có | Có | Có |
| **execution(void demo.OrderApi.*(..))** | Không | Có | Không | Có |

**Bảng mô tả signature match của các method**, không phải bảng số lần advice sẽ chạy. Khi **outer()** tự gọi **this.create()**, chữ ký create vẫn match expression nhưng invocation đó **không đi qua proxy**.

Những biểu thức rộng như **execution(* demo.OrderApi.*(..))** có thể chọn nhiều method không mong muốn nếu sử dụng cho logging quan trọng, phân quyền hoặc thay đổi kết quả nghiệp vụ. Nên giới hạn tên, layer, modifier hoặc signature phù hợp với mục tiêu thật sự.

## 9. Phân biệt execution với args ở Spring AOP

Một nguồn nhầm lẫn là **declared parameter type** và **runtime argument type**.

Với method Java:

~~~java
public void process(java.io.Serializable value) {
    // ...
}
~~~

Và caller truyền một String:

~~~java
process("hello");
~~~

**String** thực thi interface **Serializable**, nhưng **chữ ký khai báo** của method vẫn nhận **Serializable**, không phải String.

Hai pointcut có ý nghĩa khác nhau:

~~~java
execution(* *.process(java.io.Serializable))
~~~

Biểu thức trên mô tả method có **declared parameter type Serializable**.

~~~java
args(java.lang.String)
~~~

Biểu thức thứ hai là một designator khác do Spring AOP hỗ trợ, xét **kiểu argument tại invocation**. Nếu caller truyền **String**, có thể khớp điều kiện kiểu runtime này; nếu caller truyền một implementation Serializable khác không phải String, điều kiện đó có thể không khớp.

Các đoạn trên là **minh họa cú pháp**, chưa phải file Java chạy độc lập. Một số chi tiết type-matching có thể phụ thuộc type hierarchy và ngữ nghĩa pattern; điểm cần nhớ là **execution dựa vào chữ ký phương thức**, còn **args tham chiếu đối số được truyền lúc chạy**. Không thay execution bằng args khi đề bài thực sự yêu cầu lọc theo tên method hoặc return type.

### 9.1. Vì sao dùng (long) thay vì (java.lang.Long)?

Trong dự án, **cancel(long)** nhận primitive long. Mặc dù Java có boxing/unboxing giữa long và Long khi gọi, chữ ký vẫn mang kiểu **long**. Expression **execution(* demo.OrderApi.cancel(java.lang.Long))** không phải cách viết tương đương cho signature cancel(long).

### 9.2. Overload và chữ ký

Giả sử interface có:

~~~java
void submit(String id);
void submit(String id, int quantity);
void submit(long id);
~~~

Các biểu thức:

~~~text
execution(* *.submit(java.lang.String))
execution(* *.submit(java.lang.String,int))
execution(* *.submit(..))
~~~

Lần lượt chọn overload một String, overload String + int, và mọi overload tên submit. Đây chỉ là **fragment minh họa**; muốn compile cần đặt các method trong interface hoặc class hợp lệ.

Đặc biệt, **execution(* *.submit(*))** chọn các overload có một parameter bất kỳ, không chọn overload có hai parameter.

### 9.3. Varargs và array trong Java

Method:

~~~java
public void send(String... messages) {
    // ...
}
~~~

sử dụng varargs nhưng trong Java bytecode parameter được biểu diễn bằng **String[]**. Vì vậy pointcut cần được đối chiếu với signature được Spring/AspectJ parser hiểu đối với array type, thay vì giả định **(..)** là wildcard chuyên biệt cho varargs.

Nếu chỉ muốn bao quát mọi số lượng tham số của send, **execution(* *.send(..))** là cách diễn đạt dễ đọc. Nếu muốn match đúng array parameter, kiểm tra pattern và kết quả trong project thực tế.

### 9.4. Generics và type erasure

Trong Java, generic type arguments thường không có đầy đủ trong runtime method signature theo cách các lời gọi generic source được viết. Khi lựa chọn pointcut cho generic service interfaces hoặc bridge methods, kết quả matching cần được kiểm tra với type thực tế và Spring proxy.

Đừng giả định một expression chỉ vì ghi kiểu parameterized đầy đủ sẽ tự lọc chính xác **List<String>** khác **List<Integer>** ở runtime. Khi bài toán cần phân biệt loại dữ liệu sâu bên trong generic collection, điều kiện kiểm tra giá trị cần được thực hiện trong logic rõ ràng hoặc bằng metadata thích hợp.

## 10. Named @Pointcut: một tên tái sử dụng biểu thức

Trong ví dụ, chúng ta đã dùng:

~~~java
@Pointcut(
    "execution(public java.lang.String " +
    "demo.OrderApi.create(java.lang.String))"
)
public void exactCreate() {
}
~~~

Và:

~~~java
@Before("exactCreate()")
public void beforeCreate(JoinPoint jp) {
    // ...
}
~~~

Ở đây có **hai chữ ký khác nhau**:

- **Method signature trong execution(...)**: mô tả **method nghiệp vụ** muốn chọn (OrderApi.create(String)).
- **Method signature của exactCreate()**: là **tên pointcut** để tham chiếu trong aspect. Nó phải trả void và thân không chứa nghiệp vụ; **không phải phương thức service được advice**.

Bạn có thể nhóm những biểu thức thành các named pointcut dễ bảo trì hơn:

~~~java
@Pointcut("execution(public * demo.OrderApi.*(..))")
public void publicApiMethod() {
}

@Pointcut("execution(* demo.OrderApi.create(..))")
public void anyCreateOverload() {
}

@Pointcut("publicApiMethod() && anyCreateOverload()")
public void publicCreate() {
}
~~~

Đây là **đoạn minh họa thêm bên trong một class aspect**, không cần chép nguyên cụm vào project runnable nếu không muốn bổ sung advice. Trong Java annotation value, **&&** ở đây là toán tử kết hợp pointcut; không phải vòng lặp Java.

Tính **private/protected/public** của **method định nghĩa @Pointcut** quyết định quy tắc Java visibility khi các aspect khác tham chiếu pointcut đó; nó **không** tự biến expression thành chỉ chọn method nghiệp vụ có modifier tương ứng. Nếu muốn public-only, hãy ghi **public** trong **execution**.

Spring cũng cho phép tham chiếu named pointcut định nghĩa trong type khác bằng tên đầy đủ khi method accessible. Cách này hữu ích khi nhiều aspect của cùng module chia sẻ một quy ước lựa chọn service methods.

## 11. Kết hợp execution với phạm vi bean rõ ràng

Một expression quá rộng:

~~~java
@Before("execution(* *(..))")
public void aroundEverything() {
    // ...
}
~~~

là **mẫu minh họa không nên dùng tùy tiện**. Nó có thể áp dụng cho quá nhiều method phù hợp, tăng nhiễu log, chi phí tạo proxy hoặc gây side effects khó hiểu.

Thường nên thu hẹp theo **package**, **service interface**, hoặc **type**:

~~~java
execution(public * demo.order.service..*.*(..))
~~~

Cách này giới hạn signature vào **demo.order.service** và subpackages, với các public methods mà Spring proxy có thể intercept.

Hoặc chọn một interface contract ổn định:

~~~java
execution(* demo.OrderApi.*(..))
~~~

Có thể kết hợp với designator khác được Spring AOP hỗ trợ, ví dụ:

~~~java
execution(public * demo.OrderApi.*(..)) && bean(orderServiceImpl)
~~~

**bean(...)** là Spring-specific pointcut designator, so theo **bean name**; nó không phải một phần của cú pháp execution. Trong ví dụ có **@Service OrderServiceImpl** và default component naming, tên bean thường là **orderServiceImpl**.

Việc chọn theo **bean name** có thể hữu ích khi nhiều bean cùng interface nhưng bạn chỉ muốn một bean nhất định. Nó có rủi ro phụ thuộc vào tên bean và cách cấu hình context, nên cần cố ý dùng và test.

Các Spring pointcut designator khác như **within**, **@annotation**, **target** cũng có thể được kết hợp để bổ sung điều kiện. Tuy nhiên, nếu nhiệm vụ đơn thuần là chọn method theo signature, ưu tiên **execution** đủ cụ thể và dễ đọc.

## 12. Những lỗi viết execution phổ biến

| Hiện tượng | Nguyên nhân thường gặp | Cách sửa/kiểm tra |
| --- | --- | --- |
| Advice không chạy | Bean không phải Spring-managed proxy hoặc gọi **this.method()** | Lấy bean từ context, kiểm tra đường gọi |
| Method có tên đúng nhưng không match | Sai kiểu trả về, modifier, declared parameter type hoặc package/type | So chữ ký thật với từng phần của expression |
| Muốn match mọi số tham số nhưng dùng **(*)** | **(*)** chỉ một tham số, không phải mọi số | Dùng **(..)** khi cần mọi số lượng |
| Muốn method không tham số nhưng dùng **(..)** | **(..)** match cả 0, 1, nhiều args | Dùng **()** để chọn đúng 0 |
| Muốn tất cả subpackage nhưng pattern chỉ có **.*** | Wildcard type trực tiếp không tự xuống subpackage | Dùng đúng **..*** ở phần package/type |
| Expression trả String không match method trả Object | Return pattern xét declared return type | Dùng declared type thực tế hoặc **\*** nếu phù hợp |
| cancel(long) không match cancel(Long) | long và Long là hai kiểu declared khác nhau | Viết đúng chữ ký |
| Method chỉ tồn tại trên implementation nhưng bean dùng JDK proxy | JDK proxy công bố interface methods | Đưa method vào interface phù hợp hoặc xét CGLIB |
| Method private/final không được chặn bằng CGLIB | Không thể override theo cơ chế subclass proxy | Tạo public entry point hoặc thay thiết kế |
| Mở rộng pointcut quá nhiều bean | Pattern chọn cả layer không mong muốn | Thu hẹp package, type hoặc bean name |
| Named pointcut không resolve | Sai tên hoặc Java visibility không cho tham chiếu | Kiểm tra method @Pointcut, tên và class |
| Sử dụng **call(...)** như trong AspectJ weaving | Spring proxy-based AOP không hỗ trợ designator đó | Dùng execution và hiểu giới hạn proxy |
| Có **@Aspect** mà chưa có aspect bean | @Aspect không tự đăng ký trong component scanner nếu thiếu @Component hoặc @Bean | Đăng ký aspect rõ ràng |
| Không bật Spring AOP trong context phù hợp | Thiếu @EnableAspectJAutoProxy hoặc cấu hình tương đương | Bật AOP trong context chứa service/aspect |
| In sai số lần advice khi có overloaded methods | Expression broad **(..)** chọn nhiều overload | Giới hạn parameter pattern và viết test |
| Chỉ kiểm tra **AopUtils.isAopProxy == true** | Proxy có thể do advisor khác tạo | Kiểm tra log của advice cụ thể và pointcut thật |

Đọc bảng theo hướng **kiểm tra điều kiện hẹp trước**, không đổi hàng loạt annotation hoặc proxy type mà chưa biết mismatch nằm ở đâu.

## 13. Quy trình debug một execution pointcut

### Bước 1 — Ghi lại method signature thật

Ví dụ:

~~~java
public String create(String id)
~~~

Tách rõ:

- Modifier: **public**.
- Return type: **java.lang.String**.
- Declaring contract: **demo.OrderApi** (hoặc implementation thực tế).
- Name: **create**.
- Parameter count: **1**.
- Declared parameter type: **java.lang.String**.

### Bước 2 — Viết expression từ hẹp đến rộng

Bắt đầu bằng:

~~~java
execution(public java.lang.String demo.OrderApi.create(java.lang.String))
~~~

Nếu không match, có thể tạm bỏ ràng buộc return/argument để điều tra:

~~~java
execution(* demo.OrderApi.create(..))
~~~

Đây là **cách debug tạm thời**, không tự động nên giữ expression rộng khi đưa vào production.

### Bước 3 — Xác minh service là Spring AOP proxy

Trong ví dụ ở mục 7:

~~~java
System.out.println(
    org.springframework.aop.support.AopUtils.isAopProxy(service)
);
~~~

Nếu giá trị là false, kiểm tra bean được đăng ký và advisor có được Spring áp dụng cho bean không. Nếu là true nhưng advice không chạy, xác minh biểu thức và caller path. **True không chứng minh mọi method đều được advice**.

### Bước 4 — Kiểm tra self-invocation

Thử gọi **service.create("A1")** trực tiếp từ App và gọi **service.outer("B2")**. Nếu trước create từ App có advice nhưng trước this.create bên trong target không có, đó là giới hạn proxy, không phải chữ ký create sai.

### Bước 5 — Phân biệt syntax parse error và no match

- **Parse error**: Spring có thể fail khi tạo advisor/context; kiểm tra dấu ngoặc, full type name, vị trí wildcard, designator được hỗ trợ.
- **No match**: context vẫn chạy nhưng advice không xuất hiện; kiểm tra chữ ký, type, package và đường gọi.
- **Match nhưng advice không như mong đợi**: kiểm tra số lượng aspect beans, thứ tự advice, pointcut kết hợp, exception và logic trong advice.

Nếu dùng overload hoặc subclass/generic interface, viết integration test đủ đại diện thay vì chỉ nhìn một lượt log.

## 14. Bài tập thực hành

**Bài 1 — Chọn mọi method của OrderApi.**

Trong một bản thử riêng, đổi một advice thành:

~~~java
@Before("execution(* demo.OrderApi.*(..))")
public void allMethods(JoinPoint jp) {
    System.out.println("ALL " + jp.getSignature().getName());
}
~~~

Dự đoán log mới khi gọi **create**, **cancel**, **find** và **outer** từ App. Các lời gọi ngoài qua proxy có thể kích hoạt advice ALL; lời gọi **this.create()** trong outer vẫn không tạo invocation proxy riêng.

Lưu ý: đây là **đoạn minh họa để thay thế một advice**, không nên thêm bên cạnh toàn bộ advice cũ rồi so output với mục 7 mà không tính advice mới.

**Bài 2 — Chọn chỉ void methods.**

Dùng:

~~~java
execution(void demo.OrderApi.*(..))
~~~

Đối chiếu **cancel(long)** và **outer(String)**: return type của cả hai là void. Dự đoán vì sao **create(String)** và **find(String)** không khớp.

**Bài 3 — Chọn đúng một parameter bất kể type.**

Dùng:

~~~java
execution(* demo.OrderApi.*(*))
~~~

Mỗi method của ví dụ ở mục 7 đều có đúng một parameter, nên đều khớp ở mức signature. Thêm một method **status()** không có argument rồi kiểm tra nó bị loại.

**Bài 4 — Thêm overload.**

Mở rộng **OrderApi** bằng **create(String,int)**, cài đặt trong service, sau đó so:

~~~java
execution(* demo.OrderApi.create(..))
execution(* demo.OrderApi.create(java.lang.String))
execution(* demo.OrderApi.create(java.lang.String,int))
~~~

Biểu thức đầu chọn cả hai overload; hai biểu thức sau chọn overload riêng. Cần cập nhật source code hợp lệ và chạy lại Maven để tự xác minh.

**Bài 5 — Chọn public trong một package.**

Tách service class sang **demo.service** và sửa pointcut thành:

~~~java
execution(public * demo.service.*.*(..))
~~~

Nếu tạo thêm **demo.service.internal**, hãy so với:

~~~java
execution(public * demo.service..*.*(..))
~~~

để thấy khác biệt giữa package trực tiếp và subpackages. Thử đi qua Spring proxy từ một caller thật.

## 15. Checklist trước khi đưa execution pointcut vào ứng dụng

1. **Đúng phạm vi Spring AOP**: Spring-managed bean, method execution và không đòi intercept field/constructor.
2. **Đúng chữ ký**: return type, modifier, declaring type, method name, count và declared parameter types.
3. **Đúng wildcard**: **\*** là wildcard type/name theo ngữ cảnh; **(..)** là mọi số tham số; **..** trong package pattern có chức năng khác.
4. **Đúng loại proxy**: JDK theo interface hoặc CGLIB theo class; không kỳ vọng private/final method của CGLIB được override.
5. **Đúng đường gọi**: invocation đi từ caller qua proxy, không phải self-invocation trên target.
6. **Aspect đã là bean**: dùng @Component + component scan hoặc @Bean và bật @EnableAspectJAutoProxy trong context phù hợp.
7. **Pointcut đủ hẹp**: không chọn vô tình cả module/layer không liên quan.
8. **Test match và no-match**: có ít nhất một method phải được advice và một method tương tự nhưng không đủ chữ ký.
9. **Kiểm tra quá tải/overload**: không nhầm **(*)** với **(..)**, hoặc primitive với wrapper.
10. **Test hành vi thực tế**: chỉ có proxy không đủ, phải kiểm tra advice cụ thể đã chạy đúng phương thức.

## 16. Câu hỏi tự đánh giá

**1. Vì sao execution(* demo.OrderApi.create(..)) không quan tâm return type?**

Vì **\*** ở vị trí return type match mọi kiểu trả về. Biểu thức vẫn ràng buộc type OrderApi và method create.

**2. execution(* demo.OrderApi.create(*)) khác execution(* demo.OrderApi.create(..)) thế nào?**

Dạng đầu yêu cầu **đúng một parameter** bất kỳ; dạng sau chấp nhận **0 đến nhiều parameter**.

**3. Khi nào nên viết execution(public * *(..))?**

Khi thật sự muốn chọn mọi public method có thể intercept trong vùng AOP xem xét. Trong ứng dụng thực tế nên cân nhắc thêm phạm vi package/type để tránh match quá rộng.

**4. execution chọn theo declared parameter hay đối số runtime?**

Chủ yếu theo chữ ký method và declared type; designator **args(...)** dùng khi muốn quan tâm runtime argument type.

**5. Method trả Object nhưng return ra String ở runtime, execution(java.lang.String ... ) có match không?**

Không chỉ vì giá trị runtime là String; phải xem declared return type và ngữ nghĩa type pattern.

**6. Vì sao create(String) được advice khi App gọi nhưng không được advice khi outer gọi this.create?**

Vì gọi từ App đi qua Spring AOP proxy, trong khi this.create là lời gọi nội bộ trực tiếp trên target.

**7. Một biểu thức chọn private method có đủ để Spring AOP advice private method không?**

Không. Spring proxy không override private method; pointcut không thay đổi cơ chế intercept.

**8. Một named @Pointcut có private method thì có nghĩa chỉ chọn private business methods không?**

Không. Visibility của method định nghĩa named pointcut chỉ điều khiển khả năng tham chiếu Java. Muốn chọn method theo modifier, phải ghi modifier trong **execution**.

**9. Có nên dùng call(...) thay execution(...) để chặn self-invocation trong Spring AOP không?**

Không. Spring proxy-based AOP không hỗ trợ pointcut designator call theo kiểu AspectJ weaving.

**10. Vì sao cần so method signature từ interface khi dùng JDK proxy?**

Vì JDK proxy công bố các public interface methods; một implementation-only method không tự thành API của JDK proxy.

## 17. Tài liệu Spring chính thức

- Spring Framework 6.2 — **Declaring a Pointcut / execution syntax**: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html
- Spring Framework — **@AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — **Declaring Advice**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận:** **execution(...)** là công cụ chính của Spring AOP để lựa chọn **method execution theo chữ ký**. Muốn expression đúng cần xác định chính xác modifier, return type, declaring type, tên method và parameter pattern. Muốn **advice thật sự chạy**, method ấy còn phải thuộc Spring-managed bean và được gọi qua một Spring AOP proxy phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-002">within để giới hạn lớp hoặc gói của bean đích</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng within để giới hạn lớp hoặc gói của bean đích trong Spring AOP

## 1. within giải quyết vấn đề gì trong Spring?

Khi dùng `@Aspect` để thêm logging, tracing, auditing hoặc kiểm tra quy ước cho Spring service, ta thường không muốn advice áp dụng lên mọi Spring bean. Cần giới hạn chính xác **nhóm lớp hoặc package chứa những phương thức sẽ được chặn**.

Trong Spring AOP, `within(TypePattern)` là một **pointcut designator** dùng để giới hạn method-execution join point nằm trong những type khớp với `TypePattern`. Theo mô tả của Spring Framework, đó là **việc thực thi phương thức được khai báo trong một type phù hợp**. Trong ứng dụng được tổ chức theo package, `within` đặc biệt hữu ích để giới hạn logic của aspect vào service, controller hoặc repository layer.

Ví dụ:

```java
@Pointcut("within(com.example.orders.service..*)")
public void serviceLayer() {
}
```

Biểu thức trên chọn những method-execution join point thuộc các type trong package `com.example.orders.service` **và các package con**. Nó chỉ có tác dụng trong Spring AOP khi lời gọi phương thức đi qua **Spring AOP proxy của một Spring-managed bean** và đáp ứng những điều kiện đi kèm.

**Điểm quan trọng:** `within` là điều kiện theo **type/phạm vi khai báo method**, không phải điều kiện trực tiếp theo **tên Spring bean**, không phải phép quét file Java trên ổ đĩa và không phải cơ chế tự đăng ký thêm Spring bean. Một lớp có tên/package khớp nhưng không được Spring quản lý sẽ không tự nhận advice từ Spring AOP.

Bài này tập trung vào `within` trong Spring Framework theo cơ chế proxy, không ôn lại cú pháp AspectJ tổng quát hoặc weaving constructor/field access bên ngoài Spring AOP.

### Mục tiêu học tập

Sau bài học, bạn có thể:

1. Viết `within` đúng để chọn một lớp, một package hoặc nhiều package con.
2. Giải thích sự khác nhau giữa `within`, `execution`, `target`, `this`, `bean` và `@within` khi dùng Spring AOP.
3. Kết hợp `within` với `execution` để lọc cả **layer/type** và **chữ ký method**.
4. Hiểu vì sao một method nằm trong package khớp vẫn có thể không chạy advice nếu lời gọi bypass proxy.
5. Viết và chạy ví dụ Spring Framework/Java 17 kiểm tra một service ở package gốc, một service ở package con và một repository nằm ngoài phạm vi.

## 2. Cú pháp TypePattern của within

Biểu thức thường dùng:

```text
within(com.example.app.service..*)
```

Trong đó:
- `within(...)` đặt ràng buộc lên type chứa method-execution join point.
- `com.example.app.service` là đường package.
- `..` cho phép các mức package con, kể cả không có mức con.
- `*` là wildcard khớp tên type theo mẫu tương ứng.

Ví dụ đối chiếu:

| Pointcut | Ý nghĩa trong Spring AOP | Lưu ý |
| --- | --- | --- |
| `within(com.example.app.service.OrderService)` | Những method-execution join point trong type `OrderService` | Chọn một type cụ thể; lớp đó vẫn phải là target phù hợp của AOP proxy |
| `within(com.example.app.service.*)` | Những type trực tiếp trong package `service` | Không tự bao phủ package `service.internal` |
| `within(com.example.app.service..*)` | Type trong `service` và các package con ở mọi độ sâu | Mẫu thường dùng cho service layer |
| `within(com.example.app..*)` | Những type trong toàn bộ package cây `com.example.app` | Dễ quá rộng nếu ứng dụng có nhiều layer |
| `within(com.example.app.service..*) && execution(public * *(..))` | Chỉ public method execution thuộc service layer | Kết hợp điều kiện phạm vi và method signature |
| `within(com.example.app.service..*) && !within(com.example.app.service.internal..*)` | Service layer, trừ một cây package cụ thể | Loại trừ nhóm type bằng toán tử `!` |

### 2.1. Khác biệt giữa dấu chấm đơn và hai dấu chấm

Giả sử code có cấu trúc:

```text
com.example.app.service.OrderService
com.example.app.service.OrderQuery
com.example.app.service.internal.PriceService
com.example.app.service.internal.impl.LocalCalculator
com.example.app.repository.OrderRepository
```

- `within(com.example.app.service.*)` nhắm tới các type trực tiếp trong `service`, ví dụ `OrderService` và `OrderQuery`.
- `within(com.example.app.service..*)` còn bao phủ `service.internal.PriceService` và `service.internal.impl.LocalCalculator`.
- Cả hai biểu thức đều **không** chọn `com.example.app.repository.OrderRepository` vì repository nằm ngoài cây package service.

Dấu `..` không có nghĩa “chỉ thêm đúng một package con”. Nó cho phép nhiều mức package; trong mẫu `service..*` cũng bao gồm type ngay trong `service`.

### 2.2. Một lớp cụ thể

```java
@Before("within(com.example.app.service.OrderService)")
public void beforeOrderService() {
    System.out.println("inside OrderService");
}
```

Đây là snippet minh họa, giả định có một `@Aspect` Spring bean và lớp `OrderService` thích hợp. Pointcut không tự yêu cầu method tên `save`, `cancel` hay `find`; nó thu hẹp theo type. Nếu muốn chỉ một API cụ thể, kết hợp thêm `execution`.

### 2.3. Không dùng within để diễn tả tên bean

Tên Spring bean như `orderService` hoặc `pricingService` không xuất hiện trong biểu thức `within`; package Java cũng không bắt buộc trùng bean name. Nếu yêu cầu là chọn những bean có tên khớp mẫu `*Service`, Spring AOP có pointcut designator riêng là `bean(*Service)`. Hai cách chọn có thể kết hợp, nhưng chúng kiểm tra những điều kiện khác nhau.

## 3. within chỉ là điều kiện chọn, không phải cơ chế tạo proxy

Một aspect trong Spring thường được khai báo như sau:

```java
@Aspect
@Component
public class ServiceAspect {

    @Pointcut("within(com.example.app.service..*)")
    public void serviceLayer() {
    }

    @Before("serviceLayer()")
    public void beforeServiceMethod() {
        System.out.println("service call");
    }
}
```

Trong một Spring application đã bật `@AspectJ` support:
1. `@Component` hoặc `@Bean` khiến `ServiceAspect` được đăng ký thành Spring bean. `@Aspect` **không** tự hoạt động như `@Component`.
2. Spring phân tích `@Pointcut` và phương thức advice, rồi xây dựng advisor thích hợp.
3. Auto-proxy creator xét những Spring bean mà advisor có thể áp dụng.
4. Khi code khác gọi qua proxy, Spring kiểm tra method execution hiện tại có nằm trong `within(com.example.app.service..*)` hay không.
5. Chỉ khi pointcut và proxy boundary cùng cho phép, `beforeServiceMethod()` mới chạy.

**Không phải mọi object do `new` tạo ra đều được Spring AOP quản lý.** Chẳng hạn:

```java
new OrderService().placeOrder();
```

không đi qua Spring proxy, dù class nằm trong package `service`. Tương tự, một Spring bean có thể nằm trong package phù hợp nhưng không được proxy do chưa bật AOP, aspect chưa được đăng ký hoặc không có phương thức nào đủ điều kiện.

### Điều kiện Spring AOP hỗ trợ

Spring AOP dựa trên proxy hỗ trợ các **method-execution join point trên Spring beans**. Nó không dùng `within` để chặn field access (`get`/`set`), constructor execution hoặc lời gọi giữa hai phương thức nằm trong cùng target theo nghĩa Java source code. Đây là ranh giới hoạt động của Spring proxy, không phải lỗi cú pháp `within`.

## 4. within so với execution, target, this, bean và @within

Các designator này dễ bị nhầm vì đều có thể dùng để giới hạn phạm vi aspect.

| Designator | Spring AOP quan tâm điều gì? | Khi nào nên dùng? |
| --- | --- | --- |
| `within(TypePattern)` | Method execution được khai báo trong type khớp mẫu | Giới hạn theo package, lớp, layer |
| `execution(MethodPattern)` | Chữ ký/kiểu thực thi method khớp mẫu | Chọn tên method, return type, visibility, parameters, declaring type |
| `target(TypePattern)` | **Target object phía sau proxy** là instance của type | Chọn theo kiểu thực tế của đối tượng target |
| `this(TypePattern)` | **Spring proxy** là instance của type | Cần xét kiểu hoặc interface mà proxy expose |
| `bean(NamePattern)` | Spring bean name khớp mẫu | Chọn nhóm bean theo convention đặt tên |
| `@within(AnnotationType)` | Method execution được khai báo trong type có annotation phù hợp | Chọn theo marker annotation của type |
| `@annotation(AnnotationType)` | Method execution có annotation phù hợp trên method | Chọn thao tác theo method annotation |

### 4.1. within và execution

Hai pointcut sau nhìn có vẻ tương tự nhưng khác mục tiêu thiết kế:

```java
@Pointcut("within(com.example.app.service..*)")
public void inServiceLayer() {
}

@Pointcut("execution(public * com.example.app.service..*.*(..))")
public void publicServiceSignatures() {
}
```

- `within` nhấn mạnh **vùng type**, không trực tiếp yêu cầu public method.
- `execution` chỉ rõ method signature, ở ví dụ là public method với mọi return type, tên và arguments trong phạm vi declaring type được chỉ ra.
- Do Spring AOP chặn lời gọi qua proxy, phương thức nào thực sự được intercept còn phụ thuộc JDK/CGLIB và đường gọi.

Một mẫu **dễ đọc và dễ bảo trì** là tách hai điều kiện:

```java
@Pointcut("within(com.example.app.service..*)")
public void serviceLayer() {
}

@Pointcut("execution(public * *(..))")
public void publicMethod() {
}

@Pointcut("serviceLayer() && publicMethod()")
public void publicServiceMethod() {
}
```

Ở đây `serviceLayer` là điều kiện phạm vi, `publicMethod` là điều kiện chữ ký, `publicServiceMethod` kết hợp cả hai. Có thể tái sử dụng named pointcut này cho nhiều advice mà không nhân bản biểu thức dài.

### 4.2. within và target

Giả sử `com.example.app.service.SpecialOrderService` kế thừa một method từ superclass nằm **ngoài** package service. Cần phân biệt:
- `within(service..*)` mô tả **type khai báo nơi method execution thuộc về**.
- `target(SpecialOrderService)` mô tả **type instance của target runtime**.

Không nên viết một câu tuyệt đối “mọi method có thể gọi trên instance SpecialOrderService đều chắc chắn khớp within(service..*)”. Với **method kế thừa từ superclass được khai báo ở package khác**, hành vi xét method/declaring type có thể làm kết quả khác kỳ vọng. Nếu cần ràng buộc theo runtime target class/hierarchy, `target(...)` hoặc một `execution(...)` được thiết kế cẩn thận có thể phù hợp hơn; hãy kiểm thử với JDK/CGLIB proxy đang dùng.

### 4.3. within và this

Trong Spring proxy AOP:
- `this(Type)` nhìn vào **proxy**.
- `target(Type)` nhìn vào **target object**.

JDK proxy chỉ implements các interface thích hợp và thường **không** là instance của implementation class. CGLIB class proxy là subclass của implementation class. Vì vậy, một pointcut `this(com.example.app.service.OrderService)` có thể phụ thuộc proxy type trong khi `target(...)` được xét trên target thật.

`within` có ý nghĩa phân loại **method execution theo type pattern**. Nó không phải cú pháp thay thế cho `this` hoặc `target`.

### 4.4. within và bean

Ví dụ:

```java
@Pointcut("within(com.example.app.service..*) && bean(*Service)")
public void namedServiceInServicePackage() {
}
```

Ý nghĩa: chỉ chọn những invocation vừa thuộc layer service về type pattern, vừa xuất phát từ bean có tên Spring phù hợp `*Service`. Trong Spring AOP, `bean(...)` là phần mở rộng đặc thù cho bean instances, không phải designator chuẩn cho AspectJ weaving.

Cần xét naming convention thật: bean tên `orderGateway` có thể nằm trong package `service` nhưng không khớp `bean(*Service)`.

### 4.5. within và @within

`within(com.example.app.service..*)` lọc bằng **tên type/package**. Trong khi đó `@within(com.example.app.Monitored)` lọc các method executions được khai báo trong type có annotation phù hợp. Marker annotation cho phép type nằm ở nhiều package khác nhau mà vẫn được chọn.

Dạng:

```java
@Pointcut("within(com.example.app.service..*)"
        + " && @annotation(com.example.app.Audited)")
public void auditedServiceOperation() {
}
```

giới hạn theo layer và annotation **trên method**, nên khác `@within` là annotation **trên type**. Đây là minh họa cú pháp; ứng dụng thực tế phải có annotation `Audited` và aspect đã được đăng ký.

## 5. Kết hợp trong Spring: toán tử và named pointcut

Spring hỗ trợ kết hợp pointcut bằng:
- `&&`: cả hai điều kiện phải khớp.
- `||`: một trong hai điều kiện khớp.
- `!`: phủ định điều kiện.

Ví dụ:

```java
@Pointcut("within(com.example.app.service..*)"
        + " && execution(public * *(..))"
        + " && !within(com.example.app.service.internal..*)")
public void publicExternalServiceMethods() {
}
```

Biểu thức này chọn public method execution trong service layer, nhưng loại bỏ type nằm trong package `internal` và mọi package con. Ngoài việc xét pointcut, phương thức vẫn phải được gọi qua Spring proxy mới có advice.

Nếu có nhiều aspect cần dùng chung, có thể đặt pointcut vào một lớp chia sẻ:

```java
package com.example.app.aop;

import org.aspectj.lang.annotation.Pointcut;

public final class LayerPointcuts {
    private LayerPointcuts() {
    }

    @Pointcut("within(com.example.app.service..*)")
    public static void inService() {
    }
}
```

**Chú ý:** Đoạn trên chỉ minh họa khả năng chia sẻ định nghĩa pointcut, không phải mẫu khuyến nghị để đăng ký thành bean. Trong thực tế, các named pointcut được trình bày theo ví dụ chính thức là **phương thức trên lớp định nghĩa pointcut**, thường khai báo không static và có thân rỗng. Nếu chọn dùng `@Pointcut` trong một lớp không phải `@Aspect`, phải xác minh khả năng tham chiếu pointcut với cấu hình thư viện/phiên bản cụ thể. Cách đơn giản và chắc chắn hơn là khai báo named pointcut trong cùng lớp `@Aspect` như ví dụ chạy bên dưới.

## 6. Ví dụ chạy đầy đủ: package gốc, package con và repository ngoài phạm vi

Mục tiêu của ví dụ:
1. Một method trong `demo.service` nhận advice.
2. Một method trong `demo.service.internal` cũng nhận advice vì `..*` bao phủ package con.
3. Bean `demo.repository.InventoryRepository` không nhận advice vì ở ngoài `within(demo.service..*)`.
4. Lời gọi nội bộ `this.reserve(...)` trong target cho thấy Spring AOP chỉ chặn **lời gọi qua proxy**.

Ví dụ dùng **Java 17+, Spring Framework 6.1.14, Gradle**. Chúng ta tạo Spring Framework application thuần, dùng `@EnableAspectJAutoProxy(proxyTargetClass=true)` để đảm bảo các service không có interface cũng được class-based proxy. Không cần Spring Boot hoặc database.

### 6.1. Cấu trúc project

```text
spring-aop-within/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── DemoApplication.java
    ├── config/AppConfig.java
    ├── aop/LayerAspect.java
    ├── service/InventoryService.java
    ├── service/internal/PricingService.java
    └── repository/InventoryRepository.java
```

### 6.2. Gradle configuration

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-within'
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

`spring-context` cung cấp `AnnotationConfigApplicationContext` và cấu hình Spring bean. `spring-aop` cung cấp AOP infrastructure, còn `aspectjweaver` cung cấp thư viện AspectJ cần để Spring phân tích `@AspectJ` pointcut expression.

### 6.3. Spring service trong package gốc

Tệp `src/main/java/demo/service/InventoryService.java`:

```java
package demo.service;

public class InventoryService {

    public String reserve(String sku) {
        System.out.println("service:reserve:" + sku);
        return "reserved:" + sku;
    }

    public void reserveInternally() {
        System.out.println("service:reserveInternally");
        reserve("SELF");
    }
}
```

Cả `reserve` và `reserveInternally` được khai báo trong type thuộc `demo.service`. Hai method đều public và không final, nên Spring CGLIB proxy có thể chặn lời gọi **từ client qua proxy**.

Trong `reserveInternally()`, dòng `reserve("SELF")` là **self-invocation trên target**; nó không quay lại proxy. Vì thế, trong trường hợp gọi `reserveInternally()` từ client, advice chỉ chạy cho `reserveInternally` (lời gọi ngoài), không chạy lần hai cho `reserve` bên trong.

### 6.4. Spring service trong package con

Tệp `src/main/java/demo/service/internal/PricingService.java`:

```java
package demo.service.internal;

public class PricingService {

    public int price(String sku) {
        System.out.println("service.internal:price:" + sku);
        return 10;
    }
}
```

Type `PricingService` nằm dưới `demo.service.internal`. Biểu thức `within(demo.service..*)` **bao phủ package con**, nên method `price` sẽ khớp khi client gọi qua Spring proxy.

### 6.5. Repository nằm ngoài service layer

Tệp `src/main/java/demo/repository/InventoryRepository.java`:

```java
package demo.repository;

public class InventoryRepository {

    public void save(String sku) {
        System.out.println("repository:save:" + sku);
    }
}
```

`InventoryRepository` cũng là Spring bean nhưng có package `demo.repository`, nằm ngoài cây `demo.service`. Vì vậy aspect giới hạn bằng `within(demo.service..*)` không advise method `save`. Đây là bằng chứng về **phạm vi pointcut**, không phải vì repository không được Spring quản lý.

### 6.6. Aspect khai báo trong package riêng

Tệp `src/main/java/demo/aop/LayerAspect.java`:

```java
package demo.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class LayerAspect {

    @Pointcut("within(demo.service..*)")
    public void serviceTypes() {
    }

    @Pointcut("execution(public * *(..))")
    public void publicMethods() {
    }

    @Before("serviceTypes() && publicMethods()")
    public void beforeServiceCall(JoinPoint joinPoint) {
        String type = joinPoint.getTarget().getClass().getSimpleName();
        String method = joinPoint.getSignature().getName();
        System.out.println("advice:" + type + "." + method);
    }
}
```

Ở đây:
- `serviceTypes()` chỉ định phạm vi Java type nằm trong service và subpackages.
- `publicMethods()` yêu cầu public method execution.
- `@Before("serviceTypes() && publicMethods()")` chỉ chạy nếu **cả hai** điều kiện khớp.
- `JoinPoint.getTarget()` trả về target object, nên `getClass().getSimpleName()` hiển thị tên implementation ổn định trong ví dụ.
- `LayerAspect` không cần nằm cùng package với service. **Package của aspect** không phải thứ mà `within(demo.service..*)` đem ra kiểm tra.

### 6.7. Đăng ký bean và bật Spring AOP

Tệp `src/main/java/demo/config/AppConfig.java`:

```java
package demo.config;

import demo.aop.LayerAspect;
import demo.repository.InventoryRepository;
import demo.service.InventoryService;
import demo.service.internal.PricingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public InventoryService inventoryService() {
        return new InventoryService();
    }

    @Bean
    public PricingService pricingService() {
        return new PricingService();
    }

    @Bean
    public InventoryRepository inventoryRepository() {
        return new InventoryRepository();
    }

    @Bean
    public LayerAspect layerAspect() {
        return new LayerAspect();
    }
}
```

`@EnableAspectJAutoProxy` cho phép Spring phát hiện khai báo `@Aspect`. `LayerAspect` được đăng ký bằng `@Bean`, vì bản thân `@Aspect` không tự tạo Spring bean. Không dùng component scanning để ví dụ có đúng các bean đã liệt kê.

Bật `proxyTargetClass=true` giúp các service class không interface vẫn có AOP proxy theo lớp, nhưng không buộc `InventoryRepository` trở thành AOP proxy nếu nó không có advisor khớp.

### 6.8. Chương trình main và lệnh chạy

Tệp `src/main/java/demo/DemoApplication.java`:

```java
package demo;

import demo.config.AppConfig;
import demo.repository.InventoryRepository;
import demo.service.InventoryService;
import demo.service.internal.PricingService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            InventoryService inventory =
                    context.getBean(InventoryService.class);
            PricingService pricing =
                    context.getBean(PricingService.class);
            InventoryRepository repository =
                    context.getBean(InventoryRepository.class);

            System.out.println("result=" + inventory.reserve("P-7"));
            System.out.println("price=" + pricing.price("P-7"));
            repository.save("P-7");

            inventory.reserveInternally();

            System.out.println("serviceProxy=" +
                    AopUtils.isAopProxy(inventory));
            System.out.println("childProxy=" +
                    AopUtils.isAopProxy(pricing));
            System.out.println("repositoryProxy=" +
                    AopUtils.isAopProxy(repository));
        }
    }
}
```

Từ thư mục project, chạy bằng Gradle cài sẵn:

```bash
gradle run
```

Nếu project đã có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Những dòng do **ứng dụng Java** in ra, không tính thông tin build/logging của Gradle hoặc môi trường:

```text
advice:InventoryService.reserve
service:reserve:P-7
result=reserved:P-7
advice:PricingService.price
service.internal:price:P-7
price=10
repository:save:P-7
advice:InventoryService.reserveInternally
service:reserveInternally
service:reserve:SELF
serviceProxy=true
childProxy=true
repositoryProxy=false
```

### 6.9. Giải thích kết quả

- `advice:InventoryService.reserve` xuất hiện trước `service:reserve:P-7`: phương thức thuộc `demo.service` và được client gọi qua Spring AOP proxy.
- `advice:PricingService.price` xuất hiện trước `service.internal:price:P-7`: `..*` bao phủ type trong package con `demo.service.internal`.
- `repository:save:P-7` không có dòng advice đi trước: repository nằm ngoài phạm vi `within(demo.service..*)`.
- `advice:InventoryService.reserveInternally` xuất hiện khi client gọi method công khai đó qua proxy.
- **Không có** `advice:InventoryService.reserve` lần hai trước `service:reserve:SELF`: lời gọi nội bộ `this.reserve(...)` không đi qua proxy.
- `serviceProxy=true` và `childProxy=true` chứng minh hai service thực sự nhận Spring AOP proxy.
- `repositoryProxy=false` cho thấy repository không cần AOP proxy trong cấu hình ví dụ. Nó vẫn là một Spring bean được context quản lý.

Với cấu hình đơn giản này, ví dụ không phụ thuộc vào thứ tự nhiều aspect hay trạng thái cache/transaction. Nếu ứng dụng thật còn có những advisor khác, một repository có thể được proxy vì lý do khác mặc dù không khớp pointcut của `LayerAspect`.

## 7. Khi nào within không khớp như bạn kỳ vọng?

### 7.1. Package phù hợp nhưng gọi trực tiếp bằng new

```java
InventoryService service = new InventoryService();
service.reserve("MANUAL");
```

Đây là một object Java thường. Spring AOP proxy không được dùng nên không có advice của `LayerAspect`, dù tên package hoàn toàn khớp. Hãy lấy Spring-managed reference từ context hoặc inject qua Spring.

### 7.2. Package phù hợp nhưng self-invocation

`within` có thể mô tả đúng cả `reserve()` và `reserveInternally()`. Tuy nhiên, khi target thực hiện `this.reserve(...)`, lời gọi không đi qua AOP proxy. Việc đổi sang `@Around` hay mở rộng package pattern không khắc phục giới hạn đó.

Nếu cần hai method đều nhận advice, thiết kế một bean cộng tác khác rồi gọi qua Spring-managed proxy của bean đó. Chỉ dùng self-proxy access khi có lý do rõ ràng và đã hiểu chi phí phụ thuộc Spring AOP.

### 7.3. Chỉ dùng một dấu chấm wildcard

`within(demo.service.*)` không chọn các type trong `demo.service.internal`. Với cấu trúc package con thường xuyên phát triển, hãy cân nhắc `within(demo.service..*)` nếu yêu cầu thực sự bao phủ toàn bộ service layer.

Ngược lại, nếu chỉ muốn classes trực tiếp trong `demo.service`, dùng `.*` có thể chính xác hơn. Đừng tự động dùng `..*` cho toàn bộ ứng dụng nếu có package tiện ích, infrastructure hoặc implementation nội bộ không muốn advise.

### 7.4. Type được khai báo ở package khác nhưng target kế thừa method

Ví dụ một method triển khai ở `com.example.common.BaseService` được thừa kế bởi target trong `com.example.app.service`. Điểm cắt `within(com.example.app.service..*)` và điều kiện chọn target runtime không phải luôn tương đương. Điểm cần kiểm tra là **method implementation/declaring type mà Spring xét** khi xây interceptor chain.

Muốn chọn **target có kiểu service** thay vì giới hạn phạm vi khai báo method, cân nhắc `target(com.example.app.service.SomeService)` hoặc thêm các ràng buộc khác rồi kiểm thử. Đối với JDK dynamic proxy, phương thức expose qua interface cũng có thể khác với method implementation, nên integration test trên proxy type thực tế là bằng chứng đáng tin hơn suy luận chỉ từ tên package.

### 7.5. Method private/final hoặc constructor

Spring AOP theo proxy không advice constructor execution. Class-based proxy không thể override `final` và `private` method; JDK proxy chỉ expose các method thuộc interface được proxy. `within` không biến những lời gọi không thể intercept thành join point được Spring chặn.

Để kiểm tra proxy type:

```java
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
```

Đây là snippet chẩn đoán; cần biến `bean` là Spring-managed bean và import `AopUtils`. Không nên dựa vào tên class proxy vì naming có thể thay đổi theo phiên bản.

### 7.6. Annotation/service scanning bị nhầm với pointcut

`@ComponentScan("com.example.app.service")` là cấu hình **khám phá bean**. `within(com.example.app.service..*)` là cấu hình **chọn method join point** của Spring AOP. Đặt pointcut đúng mà bean chưa được register cũng không tạo bean. Ngược lại, scan toàn service thành Spring bean cũng không tự bật Spring AOP nếu thiếu hạ tầng/aspect phù hợp.

## 8. Phối hợp within với kiến trúc phân lớp

Giả sử ứng dụng có ba layer:

```text
com.example.shop
├── web
│   ├── OrderController
│   └── admin/AdminController
├── service
│   ├── OrderService
│   └── pricing/PriceService
└── repository
    ├── OrderRepository
    └── jdbc/JdbcOrderRepository
```

Có thể tạo named pointcut theo layer:

```java
@Pointcut("within(com.example.shop.web..*)")
public void webLayer() {
}

@Pointcut("within(com.example.shop.service..*)")
public void serviceLayer() {
}

@Pointcut("within(com.example.shop.repository..*)")
public void repositoryLayer() {
}

@Pointcut("serviceLayer() && execution(public * *(..))")
public void publicServiceOperation() {
}
```

Đây là **snippet trong một lớp `@Aspect`**, không phải chương trình độc lập; các method có thân rỗng và return type `void`. Điểm hay của việc đặt tên là nếu package layout thay đổi, chỉ cần chỉnh expression ở một nơi.

Tuy nhiên, “layer” là một quyết định thiết kế ứng dụng:
- Một lớp `OrderService` thuộc package service nhưng được đăng ký nhiều lần dưới các bean name khác nhau vẫn thuộc phạm vi `within` tương ứng.
- Một bean có tên `orderService` nhưng class thuộc package infrastructure **không tự** khớp `within(service..*)`.
- Một aspect được đặt trong `com.example.shop.aop` vẫn có thể advise service/repository, vì pointcut xét method execution của target chứ không bắt buộc aspect phải ở cùng package.

Nếu service API được định nghĩa bằng interface nằm ở các module/package khác implementation, kết hợp `within` với `execution`/`target` hoặc thiết kế pointcut theo business interface có thể rõ hơn. Tài liệu Spring chính thức cũng phân biệt pointcut theo **phạm vi layer** và pointcut theo **method trên service interface**.

## 9. Hiệu năng và phạm vi áp dụng

Pointcut `within` có thể giúp Spring/AspectJ expression matcher loại sớm những lớp không thuộc phạm vi cần xét, giảm phạm vi phải đánh giá các điều kiện khác. Điều này đặc biệt hữu ích khi aspect có nhiều advisor hoặc expression phức tạp.

Dù vậy, không nên xem `within` là giải pháp tối ưu hiệu năng duy nhất:
- Chi phí trong ứng dụng còn phụ thuộc số bean/proxy, số method được gọi, loại advice, logging I/O và công việc thực hiện trong advice.
- Pointcut quá rộng như `within(com.example..*)` có thể advise những bean không mong muốn.
- Các điều kiện runtime như `args` hoặc binding annotations có thể yêu cầu kiểm tra nhiều hơn so với các điều kiện type/method static.
- Cần đo trên workload thực khi có yêu cầu hiệu năng; đừng bịa ra mức tăng tốc cố định.

Thực hành tốt là **giới hạn theo type/package trước**, sau đó kết hợp điều kiện method signature hoặc annotation cho chính xác. Chẳng hạn:

```java
@Pointcut("within(com.example.shop.service..*)"
        + " && execution(public * *(..))"
        + " && @annotation(com.example.shop.Traceable)")
public void tracedPublicServiceMethod() {
}
```

Nếu `Traceable` là annotation runtime trên những method cần quan sát, biểu thức trên hạn chế advice đúng những thao tác cụ thể trong service layer. Khi không cần annotation, không nên thêm điều kiện chỉ để có biểu thức phức tạp hơn.

## 10. Bảng chẩn đoán nhanh

| Hiện tượng | Nguyên nhân khả dĩ | Cách xác minh |
| --- | --- | --- |
| Service trong package con không có advice | Đã dùng `within(service.*)` thay vì `within(service..*)` | Kiểm tra đầy đủ package của target và expression |
| Repository cũng có advice dù đang lọc service | Aspect khác hoặc advisor khác cũng khớp; repository nằm nhầm trong package service | Inspect advisor chain và package thật |
| Bean thuộc service package nhưng không được proxy | Aspect chưa thành bean, AOP chưa bật hoặc không có advisor phù hợp | Kiểm tra Spring context, `@EnableAspectJAutoProxy`, `AopUtils` |
| Cùng method advice chạy từ controller nhưng không chạy từ method nội bộ | Self-invocation bypass proxy | So sánh call path và tách bean nếu cần |
| Bean tạo qua `new` không có advice | Object không được container quản lý | Inject hoặc `getBean` từ Spring |
| Lời gọi public của superclass không khớp kỳ vọng | Declaring type của method và target runtime type khác nhau | Thử `target` hoặc `execution`; test trên proxy thật |
| Chỉ muốn bean name kết thúc `Service` | `within` lọc type, không lọc bean name | Dùng `bean(*Service)` hoặc kết hợp điều kiện |
| Muốn lớp có annotation bất kể package | Lọc package bằng `within` không thể hiện yêu cầu annotation | Dùng `@within` hoặc `@target` theo semantics mong muốn |
| `private`/`final` method không có advice qua CGLIB | Không thể override | Thay boundary/API hoặc chọn mô hình phù hợp |
| Service được scan nhưng `@Before` không in | Scan bean khác với kích hoạt AOP; có thể pointcut sai | Kiểm tra aspect registration, proxy, method và package |
| AOP có JDK proxy nhưng client cố cast về implementation | JDK proxy triển khai interface, không subclass | Inject interface hoặc xem lại cấu hình proxy type |

## 11. Checklist trước khi đưa within vào ứng dụng

1. **Xác định đúng package tree.** Cần type trong đúng package hay cả subpackages?
2. **Xác định method cần advice.** Mọi public method hay chỉ một tên/annotation cụ thể?
3. **Đăng ký aspect thành Spring bean.** `@Aspect` không tự thay cho `@Component` hoặc `@Bean`.
4. **Bật AOP trong ApplicationContext sở hữu target.** Với Spring Framework thuần dùng `@EnableAspectJAutoProxy`; Spring Boot có thể tự cấu hình khi đủ dependency.
5. **Kiểm tra proxy type.** JDK/CGLIB ảnh hưởng method có thể intercept; đối với type inheritance, cần test cụ thể.
6. **Chạy test qua Spring-managed reference.** `new` và self-invocation không kiểm chứng advice có khớp.
7. **Quan sát một method khớp và một method không khớp.** Nên có cả ví dụ thuộc package con và ngoài service để chứng minh phạm vi.
8. **Giữ expression đủ hẹp.** Không áp dụng logging, security validation hoặc tracing lên toàn package tree nếu không có lý do.

## 12. Kết luận

`within` là cách diễn đạt rõ ràng **ranh giới lớp và package** cho Spring AOP. Mẫu `within(com.example.service..*)` cho phép giới hạn method-execution join point vào service layer, kể cả các subpackages, còn `within(com.example.service.*)` hẹp hơn ở đúng package. Kết hợp `within` với `execution` hoặc annotation giúp vừa giới hạn layer vừa giới hạn thao tác.

Tuy nhiên, **type pattern khớp chưa đủ để advice chạy**: bean phải được Spring quản lý, AOP được bật, proxy phải được tạo và lời gọi phải đi qua proxy. Với JDK/CGLIB, inherited methods và self-invocation, cần kiểm tra hành vi thực tế thay vì chỉ nhìn đường package trong mã nguồn.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): `within`, `execution`, `this`, `target`, `bean`, wildcard và cách kết hợp pointcut.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` và kích hoạt Spring AOP.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): giới hạn Spring AOP proxy và self-invocation.
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher` và `AspectJExpressionPointcut`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-003">this và target trong mô hình proxy của Spring</a>

<details>
<summary>Xem chi tiết</summary>

# `this` và `target` trong mô hình proxy của Spring AOP

## 1. Bản chất vấn đề

Trong **Spring AOP**, một lời gọi của client đi vào **proxy** của Spring. Proxy chọn interceptor/advice phù hợp và cuối cùng, nếu chuỗi advice tiếp tục, lời gọi được chuyển đến **target object** đang thực thi nghiệp vụ. Vì proxy và target là hai vai trò khác nhau, pointcut của Spring phân biệt:

- **`this(Type)`:** điều kiện về **đối tượng Spring AOP proxy** nhận lời gọi. Join point khớp khi proxy có thể được coi là một instance của `Type`.
- **`target(Type)`:** điều kiện về **đối tượng đích (target object)** đứng sau proxy. Join point khớp khi target là instance của `Type`.

Đây là ý nghĩa của hai **pointcut designator** trong Spring AOP, không phải lời giải thích chung về từ khóa Java `this`. Trong mô hình weaving AspectJ thuần, hai designator có thể có ngữ nghĩa khác vì không có lớp Spring AOP proxy đứng giữa. Bài chỉ tập trung vào **Spring Framework và Spring AOP**.

Hãy hình dung:

```text
Caller bean
    |
    | Spring injects a bean reference
    v
+-------------------------+
| Spring AOP proxy        | <-- this(Type)
|  - JDK proxy hoặc CGLIB |
|  - advisor chain        |
+-------------------------+
    |
    | proceed() / delegate
    v
+-------------------------+
| Target object           | <-- target(Type)
| PaymentServiceImpl      |
| business method()       |
+-------------------------+
    |
    v
Kết quả / exception về lại proxy và caller
```

Cùng một lời gọi `pay()` có thể thỏa `target(PaymentServiceImpl)` nhưng **không thỏa** `this(PaymentServiceImpl)` nếu proxy là JDK dynamic proxy. Đây là khác biệt quan trọng nhất khi sử dụng `this` và `target` trong Spring pointcut.

## 2. Phân biệt proxy, target, interface và implementation

Xét một interface và implementation:

```java
package demo;

public interface PaymentService {
    String pay(String id);
}

public class PaymentServiceImpl implements PaymentService {
    @Override
    public String pay(String id) {
        return "PAID:" + id;
    }
}
```

Hai khai báo trên là mã minh họa, cần đặt vào các tệp Java tương ứng nếu chạy độc lập. Chúng ta giả sử Spring tạo AOP proxy cho `PaymentServiceImpl` vì có advice phù hợp.

### Trường hợp A: JDK dynamic proxy

JDK proxy triển khai `PaymentService`, nhưng **không phải subclass của `PaymentServiceImpl`**:

```text
JDK-generated proxy  -- implements --> PaymentService
        |
        | dispatch advice + delegate
        v
PaymentServiceImpl target  -- implements --> PaymentService
```

Trong một lời gọi qua JDK AOP proxy:

- `this(PaymentService)`: **khớp**, vì proxy triển khai interface.
- `this(PaymentServiceImpl)`: **không khớp**, vì JDK proxy không extends implementation.
- `target(PaymentService)`: **khớp**, vì target triển khai interface.
- `target(PaymentServiceImpl)`: **khớp**, vì đối tượng thật thuộc lớp implementation.

### Trường hợp B: CGLIB class-based proxy

CGLIB proxy do Spring tạo là **subclass runtime của target class**:

```text
CGLIB-generated subclass  -- extends --> PaymentServiceImpl
        |
        | dispatch advice + delegate
        v
PaymentServiceImpl target -- implements --> PaymentService
```

Với target/proxy hợp lệ như trên:

- `this(PaymentService)`: **khớp**, vì subclass cũng implement interface.
- `this(PaymentServiceImpl)`: **khớp**, vì proxy là subtype của implementation.
- `target(PaymentService)`: **khớp**, vì target implement interface.
- `target(PaymentServiceImpl)`: **khớp**, vì target thuộc implementation.

| Pointcut trên method `pay()` | JDK Spring AOP proxy | CGLIB Spring AOP proxy |
| --- | --- | --- |
| `this(demo.PaymentService)` | Khớp | Khớp |
| `this(demo.PaymentServiceImpl)` | Không khớp | Khớp |
| `target(demo.PaymentService)` | Khớp | Khớp |
| `target(demo.PaymentServiceImpl)` | Khớp | Khớp |

Bảng giả định đúng một `PaymentServiceImpl` target và method public có thể intercept, advisor phù hợp, caller **gọi qua Spring proxy**. Với target khác, proxy bổ sung các interface khác, proxy lồng nhau hoặc cơ chế đặc biệt, phải xác minh bằng runtime.

## 3. `this` trong Spring pointcut KHÁC từ khóa Java `this`

Hai dòng sau không mang cùng một ý nghĩa:

```java
// Java: tham chiếu tới instance đang thực thi method này
this.otherMethod();

// Pointcut: chọn method execution mà AOP proxy là instance của PaymentService
@Before("execution(* demo..*.pay(..)) && this(demo.PaymentService)")
public void before() {
    System.out.println("advice");
}
```

Khối trên là **minh họa**, kết hợp cú pháp từ hai vị trí khác nhau; method `@Before` phải nằm trong một Spring-managed `@Aspect` bean với các import phù hợp.

- **Java `this` trong method target:** là instance target đang chạy business method. Một lời gọi `this.otherMethod()` từ đây **thường không quay lại proxy**, nên không kích hoạt advice trên `otherMethod()` thông qua Spring AOP.
- **Designator `this(Type)` của Spring AOP:** ràng buộc/kiểm tra **đối tượng proxy** tại method execution join point mà Spring đang can thiệp.
- **`JoinPoint.getThis()`:** cung cấp proxy object tương ứng với ý nghĩa Spring AOP của `this` trong pointcut.
- **`JoinPoint.getTarget()`:** cung cấp target object tương ứng với ý nghĩa `target`.

Trong một lời gọi qua proxy thông thường, `joinPoint.getThis() == joinPoint.getTarget()` trả `false`. Mô hình Spring AOP tách đối tượng proxy và target dù CGLIB proxy extends target class.

Với Spring AOP, **self-invocation** vẫn bỏ qua proxy, kể cả khi dùng CGLIB; chuyển `proxyTargetClass=true` không tự làm `this.otherMethod()` bên trong target trở thành một lời gọi proxy mới.

## 4. `this(Type)` / `target(Type)` được áp dụng ở bước nào?

Một pointcut như:

```java
@Around("execution(* demo..*.pay(..)) && target(demo.PaymentServiceImpl)")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

được Spring sử dụng để xác định những **method execution join points** phù hợp trong các lời gọi **đi qua proxy**. `execution(...)` kiểm tra signature và phạm vi method; `target(...)` ràng buộc loại target object. Chúng hoạt động như hai điều kiện logic được kết hợp bằng `&&`.

Một biểu thức tương ứng dùng `this`:

```java
@Around("execution(* demo..*.pay(..)) && this(demo.PaymentServiceImpl)")
public Object aroundImplProxy(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Khi bean AOP proxy bằng JDK, điều kiện `this(demo.PaymentServiceImpl)` không thỏa, dù target `PaymentServiceImpl` có method `pay()` và method đó được client gọi qua interface. Khi cùng bean sử dụng CGLIB proxy, điều kiện có thể thỏa vì proxy subclass của `PaymentServiceImpl`.

**Quan trọng:** một pointcut chỉ khớp kiểu nhưng không có lời gọi qua proxy sẽ không khiến Spring “can thiệp từ xa” vào object tự `new` hoặc vào lệnh `this.foo()` bên trong target. Hai designator là **điều kiện cho join point vốn Spring AOP có thể intercept**, không phải cơ chế thay thế proxy.

### Không dùng `target(...)` như `execution(...)`

`target(Type)` chỉ nói target instance thỏa kiểu; nó không nêu method nào bạn muốn advice. Nên kết hợp với điều kiện method cụ thể và giới hạn package:

```java
@Before("execution(public * demo.service..*.*(..)) && target(demo.service.PaymentService)")
public void auditPaymentCall() {
    // ghi nhận các lời gọi dịch vụ phù hợp
}
```

Đây là **snippet** cần lớp trong package `demo.service`, bean `@Aspect` và Spring context tương ứng. Pointcut được giới hạn rõ hơn so với việc áp `target(...)` lên mọi method thuộc target.

## 5. Ví dụ chạy được: cùng target, JDK proxy so với CGLIB

Ví dụ sử dụng **Java 17+, Maven, Spring Framework 6.2.6**. Một service triển khai interface. Hai context riêng dùng cùng các bean definitions, khác nhau ở `@EnableAspectJAutoProxy(proxyTargetClass=...)`.

Aspect sử dụng **một `@Around` advice** khớp `perform()`, rồi quan sát trực tiếp `ProceedingJoinPoint.getThis()` và `getTarget()`. Vì chỉ có một advice và một method execution mục tiêu, stdout có thứ tự rõ ràng.

### 5.1. Cấu trúc tệp

```text
spring-aop-this-target/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ThisTargetDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-this-target</artifactId>
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
                    <mainClass>demo.ThisTargetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/demo/ThisTargetDemo.java`:

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
import org.springframework.context.annotation.Import;

public class ThisTargetDemo {

    public interface TaskApi {
        String perform(String input);
    }

    public static class TaskService implements TaskApi {
        @Override
        public String perform(String input) {
            System.out.println("TARGET perform " + input);
            return "DONE:" + input;
        }
    }

    @Aspect
    public static class TraceAspect {
        @Around("execution(* demo..*.perform(..))")
        public Object trace(ProceedingJoinPoint pjp) throws Throwable {
            Object proxy = pjp.getThis();
            Object target = pjp.getTarget();

            System.out.println("THIS_IS_API=" + (proxy instanceof TaskApi));
            System.out.println("THIS_IS_IMPL=" + (proxy instanceof TaskService));
            System.out.println("TARGET_IS_API=" + (target instanceof TaskApi));
            System.out.println("TARGET_IS_IMPL=" + (target instanceof TaskService));
            System.out.println("SAME_OBJECT=" + (proxy == target));

            return pjp.proceed();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SharedBeans {
        @Bean
        public TaskApi taskApi() {
            return new TaskService();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(SharedBeans.class)
    public static class JdkConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SharedBeans.class)
    public static class CglibConfig {
    }

    private static void inspect(String label, TaskApi bean) {
        System.out.println(label + "_AOP=" + AopUtils.isAopProxy(bean));
        System.out.println(label + "_JDK=" + AopUtils.isJdkDynamicProxy(bean));
        System.out.println(label + "_CGLIB=" + AopUtils.isCglibProxy(bean));
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(JdkConfig.class)) {
            TaskApi bean = context.getBean(TaskApi.class);
            System.out.println("=== JDK ===");
            inspect("JDK", bean);
            System.out.println("RESULT=" + bean.perform("A"));
        }

        try (var context =
                new AnnotationConfigApplicationContext(CglibConfig.class)) {
            TaskApi bean = context.getBean(TaskApi.class);
            System.out.println("=== CGLIB ===");
            inspect("CGLIB", bean);
            System.out.println("RESULT=" + bean.perform("B"));
        }
    }
}
```

Chạy từ thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

Stdout **các dòng do chương trình in** (Maven/Spring có thể ghi thêm logging riêng):

```text
=== JDK ===
JDK_AOP=true
JDK_JDK=true
JDK_CGLIB=false
THIS_IS_API=true
THIS_IS_IMPL=false
TARGET_IS_API=true
TARGET_IS_IMPL=true
SAME_OBJECT=false
TARGET perform A
RESULT=DONE:A
=== CGLIB ===
CGLIB_AOP=true
CGLIB_JDK=false
CGLIB_CGLIB=true
THIS_IS_API=true
THIS_IS_IMPL=true
TARGET_IS_API=true
TARGET_IS_IMPL=true
SAME_OBJECT=false
TARGET perform B
RESULT=DONE:B
```

Trong cả hai context, `SharedBeans.taskApi()` vẫn trả `TaskApi` và tạo `new TaskService()`. Chỉ thay đổi chiến lược tạo proxy:

- Ở context JDK, `pjp.getThis()` là JDK proxy nên `THIS_IS_IMPL=false`.
- Ở context CGLIB, `pjp.getThis()` là subclass proxy nên `THIS_IS_IMPL=true`.
- Trong cả hai context, `pjp.getTarget()` là `TaskService`, nên `TARGET_IS_IMPL=true`.
- `SAME_OBJECT=false` minh họa proxy và target là hai object khác nhau theo cấu hình Spring AOP chuẩn trong ví dụ.

Dù hai kiểu proxy khác nhau, lời gọi qua proxy vẫn được `@Around` advice intercept và `pjp.proceed()` đưa lời gọi đi tiếp tới method target. Chương trình không cần biết tên lớp proxy do runtime sinh.

## 6. Dùng `this(...)` và `target(...)` trực tiếp trong pointcut

Dưới đây là những **đoạn minh họa độc lập về pointcut**, không ghép đồng thời vào một aspect nếu muốn giữ stdout phần 5 nguyên vẹn.

### 6.1. Lọc theo interface mà proxy công bố

```java
@Before("execution(* demo..*.perform(..)) && this(demo.TaskApi)")
public void onlyApiProxyCalls() {
    System.out.println("THIS(TaskApi)");
}
```

Trong một ứng dụng có top-level interface `demo.TaskApi`, đoạn này khớp ở cả JDK và CGLIB nếu proxy expose `TaskApi`. Nếu sử dụng interface lồng bên trong class như ví dụ phần 5, cú pháp tên nested type trong biểu thức AspectJ cần được điều chỉnh theo tên Java/AspectJ hợp lệ; không copy đoạn top-level này nguyên xi và giả định kiểu đó đã tồn tại.

### 6.2. Lọc theo class implementation của target

```java
@Before("execution(* demo..*.perform(..)) && target(demo.TaskService)")
public void onlyTaskTargetCalls() {
    System.out.println("TARGET(TaskService)");
}
```

Điều kiện này khớp ở cả hai proxy strategy nếu target thực sự là `demo.TaskService`. Tên class ở đây là **kiểu top-level minh họa**; project runnable phần 5 định nghĩa nested type `ThisTargetDemo.TaskService`.

### 6.3. Điều kiện khiến JDK và CGLIB có kết quả khác nhau

```java
@Before("execution(* demo..*.perform(..)) && this(demo.TaskService)")
public void onlyImplSubtypeProxyCalls() {
    System.out.println("THIS(TaskService)");
}
```

Với target `TaskService implements TaskApi` và proxy strategy như đã mô tả, advice chỉ thỏa khi **proxy là subtype của `TaskService`**, tức trường hợp CGLIB. Trong JDK dynamic proxy mode, client gọi được `perform()` qua interface nhưng pointcut `this(TaskService)` không khớp.

**Không suy rộng một cách máy móc:** nếu Spring proxy công bố interface khác, target là subclass khác, dùng scoped proxy hoặc nhiều lớp proxy chồng nhau, kiểm tra runtime và target type thực tế trước khi kết luận.

## 7. Ràng buộc proxy và target vào tham số advice

Spring hỗ trợ **binding form** của hai designator bằng tên tham số, giúp advice nhận object đang match:

```java
@Aspect
@Component
public class BindingAspect {

    @Before(
        value = "execution(* demo..*.perform(..)) && this(proxy)",
        argNames = "proxy"
    )
    public void beforeProxy(TaskApi proxy) {
        // proxy là tham chiếu Spring AOP proxy được bind.
    }

    @Before(
        value = "execution(* demo..*.perform(..)) && target(service)",
        argNames = "service"
    )
    public void beforeTarget(TaskService service) {
        // service là target object được bind.
    }
}
```

Đây là **mẫu minh họa cần các top-level type và imports/scan phù hợp**, không phải class độc lập runnable. Ý nghĩa:

- `this(proxy)` kết hợp với tham số `TaskApi proxy` ràng buộc proxy phù hợp `TaskApi`.
- `target(service)` kết hợp với `TaskService service` ràng buộc target phù hợp `TaskService`.
- Tên trong biểu thức phải được Spring nhận diện đúng với tên tham số; `argNames` giúp tránh phụ thuộc vào việc trình biên dịch có lưu parameter names.
- Tham số nhận được ở advice là **đối tượng thực tế** do Spring AOP cung cấp; nó không phải bản sao trạng thái của target.

Nếu chỉ cần đọc thông tin kiểu hoặc tham số ngay tại join point, có thể sử dụng `ProceedingJoinPoint.getThis()` và `getTarget()` như phần 5. Nếu cần thể hiện điều kiện rõ tại pointcut level, sử dụng `this(Type)` và `target(Type)`.

### Chú ý đối với JDK proxy và injection type

Nếu một advice sử dụng `this(proxy)` nhưng khai báo tham số kiểu concrete implementation, JDK proxy **không** phải instance implementation và điều kiện không khớp. Ngược lại, `target(service)` với tham số kiểu implementation có thể khớp vì target object có đúng kiểu đó.

Đừng ép kiểu `pjp.getThis()` sang implementation class khi chương trình cho phép JDK dynamic proxy. Nên dùng interface cho caller và những tham số binding cần tương thích cả hai chiến lược.

## 8. Phân biệt `this`/`target` với các designator khác

Trong Spring AOP, những biểu thức dưới đây có ý nghĩa khác nhau:

| Designator | Căn cứ match | Ví dụ dùng |
| --- | --- | --- |
| `this(Type)` | **Kiểu của proxy object** | Advice chỉ khi proxy là instance của một interface |
| `target(Type)` | **Kiểu của target object** | Advice cho bean có target thuộc một implementation/base type |
| `execution(...)` | **Method execution signature** | Chỉ advice các method `pay(..)` ở package service |
| `within(TypePattern)` | **Kiểu khai báo nằm trong phạm vi pattern** | Hạn chế method execution theo package/lớp |
| `args(Type)` | **Kiểu runtime của tham số truyền vào** | Chọn invocation có tham số thuộc một kiểu |
| `@annotation(Annotation)` | **Annotation trên method execution phù hợp** | Chọn method được gắn một annotation xác định |
| `@target(Annotation)` | **Annotation trên class của target theo matcher** | Chọn target có class mang annotation |
| `@within(Annotation)` | **Annotation trên kiểu khai báo method execution phù hợp** | Chọn execution trong phạm vi kiểu có annotation |

Hai cặp dễ nhầm:

- **`target(Type)` và `@target(Annotation)`:** cái trước kiểm tra quan hệ kiểu Java của target object; cái sau dựa annotation.
- **`this(Type)` và `within(TypePattern)`:** cái trước xét proxy instance; cái sau giới hạn vị trí khai báo kiểu được pointcut matcher sử dụng.

Các biểu thức `call(...)`, `get(...)`, `set(...)` thuộc nhóm AspectJ pointcuts rộng hơn nhưng **không được Spring AOP proxy hỗ trợ để intercept method call site, field read/write**. Spring AOP tập trung vào *method execution join points* được gọi qua proxy.

Một biểu thức practical kết hợp đúng trách nhiệm của từng designator:

```java
@Around(
    "execution(public * demo..service..*Service.*(..)) " +
    "&& target(demo.service.PaymentService)"
)
public Object profile(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        System.out.println("elapsedNs=" + (System.nanoTime() - start));
    }
}
```

Đây là **method mẫu**, không phải chương trình chạy độc lập. Nó dùng `execution` để giới hạn method và `target` để giới hạn target type; return/exception từ `proceed()` tiếp tục ra caller. Giá trị thời gian phụ thuộc môi trường nên không có stdout cố định.

## 9. Self-invocation và hai khái niệm `this` khác nhau

Giả sử Spring đã tạo proxy cho bean:

```java
public class PaymentServiceImpl implements PaymentService {

    @Override
    public String pay(String id) {
        return save(id);
    }

    public String save(String id) {
        System.out.println("TARGET save " + id);
        return "OK:" + id;
    }
}
```

Đây là minh họa một method gọi method khác **trên cùng target object**. Nếu client gọi `proxy.pay("A")`, advice cho `pay()` có thể chạy. Nhưng khi target `pay()` chạy, lời gọi `save(id)` tương đương `this.save(id)` là lời gọi Java trong target, không quay lại Spring AOP proxy. Advice chỉ match `save()` không được chạy trong đường gọi nội bộ này.

```text
Client --> proxy.pay()
              |
              v
          advice(pay)
              |
              v
          target.pay()
              |
              | Java this.save()
              v
          target.save()
       (không qua proxy lần hai)
```

Ở đây:

- **Pointcut `this(Type)`** được đánh giá khi Spring AOP đang xử lý join point đi qua proxy.
- **Java `this`** trong `target.pay()` là target instance thực thi phương thức.
- Dù target có subtype/kiểu trùng với proxy class-related types, đường gọi `this.save()` vẫn không tự thêm một proxy boundary.

Nếu cần interceptor cho `save()` khi gọi từ `pay()`, cách dễ hiểu là **tách `save()` sang Spring bean khác rồi inject bean đó**; caller gọi sang một Spring proxy riêng. Có các kỹ thuật gọi lại proxy từ self-reference hoặc `AopContext.currentProxy()` với `exposeProxy=true`, nhưng chúng tạo ràng buộc với Spring AOP và cần cân nhắc thiết kế.

## 10. Khi nào nên chọn `this`, khi nào chọn `target`?

**Chọn `this(Type)` khi điều kiện thật sự phụ thuộc loại object mà Spring đã cấp cho client.** Ví dụ muốn giới hạn advice cho một proxy implement interface `ExternalApi`. Trường hợp này phù hợp với việc suy nghĩ theo **contract của proxy**. Lưu ý nếu thay cấu hình JDK → CGLIB, một pointcut ràng buộc implementation class qua `this(Implementation)` có thể thay kết quả match.

**Chọn `target(Type)` khi điều kiện nên đi theo lớp/kiểu của đối tượng nghiệp vụ.** Ví dụ chỉ advice những Spring beans có target implement `PaymentService` hoặc thuộc lớp `PaymentServiceImpl`, bất kể proxy strategy. Dù vậy, advice vẫn chỉ chạy ở những method execution mà proxy có thể intercept.

**Không dùng chúng chỉ vì trông giống nhau trên CGLIB.** Trên một CGLIB proxy, nhiều điều kiện `this(Type)` và `target(Type)` cùng trả true, nhưng khi cấu hình đổi về JDK proxy, sự khác biệt có thể xuất hiện và khiến advice không chạy.

Đối với một service layer, pointcut thường dễ bảo trì khi kết hợp **method execution scope** với **target type hoặc interface contract** phù hợp. Nếu ý định chỉ là “method trên một nhóm package”, `execution(...)` hoặc `within(...)` có thể diễn đạt trực tiếp hơn việc lạm dụng `this(...)`.

## 11. Các trường hợp đặc biệt và giới hạn

### 11.1. Bean không được proxy

Không phải mọi Spring bean đều có AOP proxy. Chỉ khai báo `@Aspect` hoặc `@EnableAspectJAutoProxy` chưa đủ để biến **mọi bean** thành proxy. Spring phải nhận diện advisor phù hợp và proxy được tạo. Nếu client nắm target trực tiếp mà không qua AOP, không có Spring AOP interception ở đường gọi đó.

### 11.2. CGLIB không thể intercept mọi method

CGLIB sinh subclass nên không thể override `final` hoặc `private` method, và không thể tạo subclass cho `final class`. Không nên suy rằng `this(Implementation)` khớp thì mọi method của implementation đều advice được.

### 11.3. JDK proxy không công bố method riêng ngoài interface

JDK proxy implement các interface được cấu hình/công bố. Một public method chỉ có trên implementation nhưng không nằm trên interface proxy thường không thể được client gọi qua kiểu implementation của JDK proxy. `target(Implementation)` khớp về đối tượng thật **không mở rộng API JDK proxy**.

### 11.4. Scoped proxy, proxy lồng nhau và dynamic TargetSource

Ví dụ bài học dùng một singleton target đơn giản được auto-proxy một lần. Spring cũng có thể dùng scoped proxies, proxy lồng nhau hoặc dynamic target source; lúc đó **target object có thể khác theo invocation**, các proxy layer có thể khác nhau và cần xác minh rõ bạn đang kiểm tra proxy nào.

Các helper như `AopUtils.getTargetClass(bean)` hỗ trợ nhận diện target class của Spring AOP proxy trong những tình huống thông thường, nhưng không nên dùng `bean.getClass()` đơn lẻ làm cơ sở suy mọi quan hệ proxy/target trong cấu hình phức tạp.

### 11.5. `@Aspect` bean và tự advice chính nó

Một Spring `@Aspect` bean được container quản lý để cung cấp pointcut/advice, nhưng Spring AOP auto-proxy thông thường loại aspect bean khỏi target auto-proxy. Vì vậy không nên kỳ vọng `this(AspectClass)` hoặc `target(AspectClass)` tự khiến một aspect khác chặn method trên chính `@Aspect` bean đó.

### 11.6. Type name trong pointcut có thể khác Java nested-type syntax

Trong project mẫu, `TaskApi` và `TaskService` được khai báo là các nested types của `ThisTargetDemo`. Khi viết biểu thức pointcut sử dụng **qualified type name**, cần tuân thủ cú pháp type pattern AspectJ cho nested type. Các snippet `demo.TaskApi` / `demo.TaskService` ở phần 6–7 cố ý mô tả trường hợp hai type **top-level** để tránh gắn một tên không tồn tại vào project runnable. Nếu muốn thử trực tiếp với project phần 5, chuyển type thành top-level files hoặc sử dụng tên type chính xác và test pointcut parser.

## 12. Quy trình debug `this` / `target` pointcut không chạy

1. **Lấy bean từ Spring context**, không tự `new` object để so kết quả.
2. **Xác nhận là AOP proxy** bằng `AopUtils.isAopProxy(bean)`.
3. **Phân biệt loại proxy** bằng `AopUtils.isJdkDynamicProxy(bean)` và `isCglibProxy(bean)`.
4. **Kiểm tra object runtime** bằng `bean instanceof Api`, `bean instanceof Impl`, hoặc `type.isInstance(bean)`.
5. **Tìm target class** bằng `AopUtils.getTargetClass(bean)`. Kết quả proxy class và target class có thể khác.
6. **Kiểm tra pointcut** có `execution(...)` khớp method/visibility/package không, trước khi xét điều kiện `this/target`.
7. **Kiểm tra điều kiện `this(Type)`**: Type có thực sự là interface/subtype của **proxy** không?
8. **Kiểm tra điều kiện `target(Type)`**: target object phía sau proxy có thực sự là instance của Type không?
9. **Kiểm tra đường gọi**: self-invocation, object ngoài Spring và method không intercept được không được cứu bằng cách đổi designator.
10. **Kiểm tra context/proxy strategy hiệu lực**: Spring Framework mặc định và Spring Boot auto-configuration có thể dùng strategy khác nhau; dựa vào runtime, không chỉ dựa một property.

Một log hữu ích trong một `@Around` advice:

```java
Object proxy = pjp.getThis();
Object target = pjp.getTarget();

System.out.println("proxyClass=" + proxy.getClass().getName());
System.out.println("targetClass=" + target.getClass().getName());
System.out.println("sameReference=" + (proxy == target));
```

Đây là **snippet bổ sung**, cần `pjp` của advice. Tên class sẽ tùy runtime; trong chương trình tự động, nên assert dựa trên `instanceof` và `AopUtils` thay vì hardcode tên generated proxy.

## 13. Tự kiểm tra

**Câu 1.** Target `InvoiceServiceImpl implements InvoiceService`, Spring AOP dùng JDK proxy. `this(InvoiceServiceImpl)` có khớp một method public đã được proxy intercept không?  
**Đáp án:** Không, vì object proxy không phải implementation subtype. `target(InvoiceServiceImpl)` thì khớp khi target đúng kiểu đó.

**Câu 2.** Cùng bean chuyển sang CGLIB proxy. `this(InvoiceServiceImpl)` có thể khớp không?  
**Đáp án:** Có, vì CGLIB proxy subclass của implementation class, nếu method và pointcut đủ điều kiện.

**Câu 3.** `pjp.getThis()` và `pjp.getTarget()` là cùng một object trong ví dụ Spring AOP chuẩn không?  
**Đáp án:** Không. `getThis()` là AOP proxy; `getTarget()` là object target đứng sau proxy.

**Câu 4.** Có thể sửa self-invocation bằng cách viết pointcut `target(Implementation)` thay cho `this(Implementation)` không?  
**Đáp án:** Không. Self-invocation không đi vào Spring proxy để pointcut được đánh giá cho lời gọi nội bộ.

**Câu 5.** Target implement interface `PaymentApi` và proxy JDK implement `PaymentApi`. Hai điều kiện `this(PaymentApi)` và `target(PaymentApi)` khác nhau về ý nghĩa chứ?  
**Đáp án:** Có. Dù cả hai có thể khớp, `this` xét proxy, `target` xét target.

**Câu 6.** `@target(Audited)` có giống `target(Audited)` không?  
**Đáp án:** Không. `@target` xét annotation trên target class; `target` xét quan hệ kiểu Java.

**Câu 7.** Một Spring bean không có AOP proxy, gọi method trực tiếp nhưng target class phù hợp `target(MyType)`. Spring AOP tự chạy advice không?  
**Đáp án:** Không. Phải có lời gọi mà Spring AOP thực sự intercept thông qua proxy phù hợp.

**Câu 8.** Một class `final` có business interface và được proxy bằng JDK, `target(FinalImplementation)` có thể khớp không?  
**Đáp án:** Có thể, vì target vẫn thuộc class đó, còn proxy là object JDK implement interface. Không suy rằng target class final làm JDK proxy hoàn toàn bất khả thi.

## 14. Tài liệu Spring chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `this`, `target`, `args`, `@target` và quy tắc match trong Spring AOP.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `JoinPoint.getThis()`, `getTarget()`, `@Around` và `proceed()`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): cơ chế JDK/CGLIB, proxy/target và self-invocation.
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): Spring pointcut matching và method interception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-004">args theo kiểu đối số thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# `args` theo kiểu đối số thực tế trong Spring AOP

## 1. Mục tiêu của `args(...)` khi viết Spring Aspect

Trong Spring AOP, pointcut `args(...)` chọn **lần thực thi phương thức trên Spring bean thông qua proxy** dựa trên **các đối số thực sự được truyền ở thời điểm gọi**. Khác với `execution(...)`, vốn có thể giới hạn theo chữ ký của phương thức được khai báo, `args` là một điều kiện có thể phụ thuộc vào dữ liệu runtime của từng invocation.

Ví dụ một bean có method:

```java
public void send(Object payload) {
    // ...
}
```

Cùng method `send(Object)` có thể được gọi với `"hello"` (một `String`), `Integer.valueOf(7)`, hoặc một đối tượng tùy chỉnh không triển khai `Serializable`. Một pointcut `args(java.io.Serializable)` có thể chọn những lần gọi mang **đối số thực tế thuộc kiểu `Serializable`**, dù method đã khai báo tham số là `Object`.

Điểm khác biệt cốt lõi:

```text
Method khai báo: send(Object value)

send("hello")           -> runtime value là String, implements Serializable
send(Integer.valueOf(7)) -> runtime value là Integer, implements Serializable
send(new PlainPayload()) -> runtime value là PlainPayload, không Serializable

execution(* *.send(java.io.Serializable)) -> xét tham số khai báo
args(java.io.Serializable)                -> xét đối số thực tế tại lời gọi
```

**Phạm vi:** Bài chỉ giải thích `args` trong **Spring @AspectJ-style AOP, áp dụng lên các Spring-managed beans được gọi qua AOP proxy**. Đây không phải bài AspectJ weaving độc lập, không bàn chung chung về pointcut ngoài phạm vi Spring.

## 2. Phân biệt chính xác `args` và `execution`

Hai biểu thức trông giống nhau nhưng không tương đương:

```java
// Chỉ áp dụng cho method có đúng một tham số khai báo kiểu Serializable:
execution(* *.send(java.io.Serializable))

// Xét một đối số được truyền lúc chạy có kiểu phù hợp với Serializable:
args(java.io.Serializable)
```

Giả sử một `MessageGateway` khai báo:

```java
void send(Object payload);
```

Khi caller đưa `String` vào, chữ ký Java **vẫn là** `send(Object)`; JVM không đổi chữ ký phương thức thành `send(String)`. Vì vậy:

- `execution(* *.send(java.io.Serializable))` **không khớp** chữ ký `send(Object)` chỉ vì caller truyền `String`.
- `args(java.io.Serializable)` có thể **khớp lần thực thi** khi giá trị runtime implements `Serializable`.
- Lần gọi tiếp theo truyền đối tượng không implements `Serializable` có thể **không khớp**, dù method và proxy vẫn là những đối tượng cũ.

Tài liệu chính thức Spring đưa đúng sự phân biệt này qua `args(java.io.Serializable)` và `execution(* *(java.io.Serializable))`: cái thứ nhất xét argument runtime, cái thứ hai xét khai báo chữ ký.

| Tiêu chí | `execution(...)` | `args(...)` |
| --- | --- | --- |
| Thông tin trọng tâm | Method execution, tên method, return type, kiểu tham số **khai báo** | Kiểu các **đối số thực tế** được truyền |
| Một method nhận các kiểu object khác nhau | Điều kiện chữ ký vẫn vậy | Kết quả match có thể khác giữa hai lần gọi |
| Thời điểm Spring đánh giá | Nhiều phần có thể xác định trước khi invoke | Phần cần runtime args phải kiểm tra ở mỗi invocation |
| Dùng để giới hạn package/class/method | Rất phù hợp | Không thay thế được scoping theo class/method |
| Dùng để lấy giá trị argument trong advice | Không trực tiếp bind giá trị chỉ nhờ chữ ký | Dùng `args(name)` kết hợp tham số advice để bind |
| Đối tượng ngoài Spring container | Không được Spring AOP auto-proxy | Cũng không được Spring AOP auto-proxy |

**Đừng dùng `args` để thay thế toàn bộ `execution`.** Thực hành tốt trong Spring là kết hợp một điều kiện tĩnh hẹp với điều kiện runtime:

```java
@Before(
  "execution(public * com.example.app.MessageGateway.send(..))" +
  " && args(java.io.Serializable)"
)
public void beforeSerializable() {
    // ...
}
```

Đây là **fragment minh họa** cần được đặt trong một Spring `@Aspect` đã đăng ký. Biểu thức ngoài giới hạn đến method `send` của service, còn `args` chỉ lọc kiểu runtime. Tránh `args(java.io.Serializable)` đứng đơn lẻ trên toàn bộ application nếu không có lý do rõ: nó có thể chọn nhiều method ngoài dự kiến.

## 3. Cú pháp `args` trong Spring pointcut expressions

### 3.1. Một đối số thuộc kiểu nhất định

```text
args(java.io.Serializable)
args(com.example.domain.OrderRequest)
```

`args(java.io.Serializable)` chọn join point có **đúng một đối số** phù hợp với `Serializable`. Tương tự, `args(com.example.domain.OrderRequest)` dùng kiểu `OrderRequest` mà ứng dụng khai báo.

AspectJ type matching xét quan hệ assignability: đối tượng `String` implements `Serializable`; một subclass của `OrderRequest` cũng phù hợp nếu có quan hệ kiểu phù hợp. Spring đánh giá điều kiện đó trên lời gọi method của Spring bean mà proxy có thể intercept, chứ không phải mọi lời gọi Java trong JVM.

### 3.2. Nhiều đối số và dấu `..`

```text
args(java.io.Serializable, java.lang.String)
args(java.io.Serializable, ..)
args(.., java.lang.String)
args(*, java.lang.String)
args(..)
```

- `args(Serializable, String)`: phương thức được gọi với **đúng hai** arguments phù hợp theo thứ tự đó.
- `args(Serializable, ..)`: **đối số đầu tiên** có kiểu phù hợp với `Serializable`, sau đó có thể có **không hoặc nhiều** đối số khác.
- `args(.., String)`: đối số **cuối cùng** có kiểu phù hợp với `String`, trước đó có thể có bất kỳ số lượng arguments nào.
- `args(*, String)`: có đúng hai arguments; `*` là pattern một argument bất kỳ trong vị trí đầu.
- `args(..)`: không giới hạn kiểu hoặc số arguments; đứng một mình thường không phải một bộ lọc đủ hẹp.

Khi pattern có nhiều đối số, **vị trí rất quan trọng**. Với method `save(String id, Object payload)`, `args(Serializable, ..)` xét `id`, không tự nhảy sang `payload` ở vị trí thứ hai. Chọn pattern hoặc bind theo đúng vị trí cần kiểm tra.

### 3.3. `args(type)` và `args(variable)` là hai hình thức sử dụng khác nhau

Trong annotation pointcut:

```java
args(java.io.Serializable)
```

là **type pattern**. Còn:

```java
args(payload)
```

có thể là **binding** khi `payload` được khai báo là tham số trong phương thức advice hoặc named pointcut. Type của tham số Java được bind góp phần quyết định kiểu argument hợp lệ.

Đừng viết `args(payload)` trong annotation nhưng lại quên khai báo `payload` trong advice hoặc không cung cấp thông tin binding mà Spring có thể xác định. Kết quả thường là lỗi khởi tạo aspect/pointcut, không phải lời advice bí mật bỏ qua mọi invocation.

### 3.4. `args` khác `@args`

`args(com.example.domain.OrderRequest)` xét **kiểu đối số** có tương thích với `OrderRequest` không.

`@args(com.example.annotations.Classified)` xét **annotation có trên kiểu runtime của đối số** theo cơ chế AspectJ, không phải kiểu `Classified` của đối số và cũng không phải annotation đặt trên tham số trong chữ ký method.

Đây là **hai pointcut designators riêng biệt được Spring AOP hỗ trợ**. Nếu mục tiêu là annotation của method đang được gọi, hãy xem `@annotation`, không nhầm với `@args`.

## 4. Binding giá trị argument vào phương thức advice

Một tính năng rất hữu ích trong Spring `@Aspect` là đưa đối số runtime vào method advice dưới dạng Java parameter có kiểu rõ ràng.

Ví dụ fragment:

```java
@Aspect
public class AuditAspect {
    @Before(
        value = "execution(public * com.example.app.MessageGateway.send(..))"
              + " && args(payload)",
        argNames = "payload"
    )
    public void before(Serializable payload) {
        System.out.println(payload.getClass().getSimpleName());
    }
}
```

Ở đây:

1. `execution(...send(..))` giới hạn phạm vi method.
2. `args(payload)` yêu cầu một đối số khớp và **bind** đối số đó vào tên `payload`.
3. Advice khai báo `Serializable payload`, vì vậy kiểu bind được kiểm tra theo Java type đó.
4. `argNames = "payload"` làm rõ tên tham số dùng khi liên kết advice; không phải tên field hay bean trong container.
5. Khi advice chạy, `payload` chính là object được truyền lúc gọi, không phải một object Spring tự tạo để hợp với pointcut.

**Spring phải tìm được tên parameter cho binding.** Có thể dùng `argNames`, compile giữ thông tin tên tham số thích hợp hoặc những cơ chế khám phá tên mà phiên bản Spring hỗ trợ. Việc khai báo `argNames` rõ ràng là cách giảm phụ thuộc build flags trong ví dụ.

### 4.1. `JoinPoint.getArgs()` và `args(name)`

Hai cách lấy arguments thường gặp:

```java
@Before("execution(* com.example.app.MessageGateway.send(..))")
public void observe(org.aspectj.lang.JoinPoint jp) {
    Object[] arguments = jp.getArgs();
    // Tự kiểm tra argument types khi cần.
}
```

và:

```java
@Before(
    value = "execution(* com.example.app.MessageGateway.send(..))"
          + " && args(payload)",
    argNames = "payload"
)
public void observe(Serializable payload) {
    // Có argument được bind theo kiểu.
}
```

Đây là **hai fragments thay thế nhau**, không phải hai advice bắt buộc chạy cùng lúc.

- `JoinPoint.getArgs()` cung cấp mảng đối số cho lời gọi mà advice đã match, giúp inspect tùy điều kiện.
- `args(payload)` làm việc **lọc và bind** ngay trong pointcut, giảm số kiểm tra kiểu thủ công trong advice.
- Nếu ứng dụng cần lọc theo **giá trị** (ví dụ `amount > 1000`) chứ không chỉ kiểu, vẫn phải kiểm tra giá trị trong advice hoặc thiết kế predicate khác; `args(java.lang.Number)` không tự kiểm tra khoảng giá trị.

### 4.2. Ví dụ hai đối số

Với method `reserve(String orderId, Object data)`:

```java
@Before(
    value = "execution(* com.example..ReservationService.reserve(..))"
          + " && args(orderId, data)",
    argNames = "orderId,data"
)
public void beforeReserve(String orderId, Serializable data) {
    // Runtime data phải thỏa điều kiện Serializable.
}
```

Đây là fragment cần class/package thích hợp. Nó bind **hai vị trí** theo thứ tự. Nếu signature method có thêm tham số thứ ba, expression `args(orderId,data)` không còn khớp vì nó mô tả đúng hai arguments. Khi muốn giữ phần cuối linh hoạt, dùng dấu `..` hợp lý và đối chiếu vị trí thực tế.

## 5. Ví dụ chạy được: `send(Object)` nhưng advice chọn kiểu runtime

Ví dụ đầy đủ sau sử dụng **Java 17+, Spring Framework 6.1.14, AspectJ weaver 1.9.22.1 và Maven**.

Thiết kế có chủ đích:

- `MessageGateway.send(Object)` luôn có **chữ ký tham số Object**.
- Caller lần lượt truyền `String`, `PlainPayload` không implements `Serializable`, và `Integer`.
- Một advice **dùng `args(payload)` với tham số `Serializable`** để chọn `String`/`Integer` theo kiểu runtime.
- Một advice đối chứng dùng `execution(...send(java.io.Serializable))` để cho thấy phương thức khai báo `Object` không đột ngột biến thành chữ ký `Serializable`.
- Spring AOP dùng **JDK proxy** qua business interface; target không tự gọi nội bộ để tránh nhiễu ranh giới proxy.

### 5.1. Cấu trúc project

```text
spring-aop-runtime-args/
├── pom.xml
└── src/main/java/com/example/argsdemo/
    ├── AppConfig.java
    ├── MessageGateway.java
    ├── MessageService.java
    ├── ArgsAuditAspect.java
    └── DemoApp.java
```

### 5.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-runtime-args</artifactId>
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

`spring-context` cung cấp Spring container và kéo các module Spring AOP nền tảng theo cây dependency phù hợp của bộ ví dụ. `aspectjweaver` cho phép Spring xử lý khai báo `@Aspect`/pointcut. Mục tiêu là **Spring proxy-based AOP**; không bật AspectJ compiler hay weaving agent.

### 5.3. `src/main/java/com/example/argsdemo/MessageGateway.java`

```java
package com.example.argsdemo;

public interface MessageGateway {
    void send(Object payload);
}
```

Method chỉ có một tham số **khai báo `Object`**, bất kể caller sau này truyền object thuộc class nào. Interface giúp ví dụ sử dụng JDK dynamic proxy mà không cần class-based proxy.

### 5.4. `src/main/java/com/example/argsdemo/MessageService.java`

```java
package com.example.argsdemo;

public class MessageService implements MessageGateway {
    @Override
    public void send(Object payload) {
        String type = payload.getClass().getSimpleName();
        System.out.println("[TARGET] " + type + ": " + payload);
    }
}
```

Để in output ổn định và tránh lẫn với quy tắc `null`, ví dụ chỉ truyền các objects khác `null`. Với ứng dụng thật, nếu API cho phép null thì target phải kiểm tra `payload == null` trước khi gọi `getClass()`.

### 5.5. `src/main/java/com/example/argsdemo/ArgsAuditAspect.java`

```java
package com.example.argsdemo;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ArgsAuditAspect {
    private final AtomicInteger runtimeMatches = new AtomicInteger();
    private final AtomicInteger signatureMatches = new AtomicInteger();

    @Before(
        value = "execution(public * com.example.argsdemo.MessageGateway.send(..))"
              + " && args(payload)",
        argNames = "payload"
    )
    public void runtimeSerializable(Serializable payload) {
        runtimeMatches.incrementAndGet();
        System.out.println("[AOP args] " + payload.getClass().getSimpleName());
    }

    @Before(
        "execution(public * com.example.argsdemo.MessageGateway.send(java.io.Serializable))"
    )
    public void declaredSerializableSignature() {
        signatureMatches.incrementAndGet();
        System.out.println("[AOP execution] Serializable signature");
    }

    public int runtimeMatches() {
        return runtimeMatches.get();
    }

    public int signatureMatches() {
        return signatureMatches.get();
    }
}
```

Phương thức `runtimeSerializable` bind giá trị kiểu `Serializable` được truyền qua `args(payload)`. Spring phải xem argument runtime của từng lời gọi để quyết định advice có chạy không. Phương thức `declaredSerializableSignature` chỉ khớp nếu method thật sự **khai báo một parameter kiểu Serializable**, điều không đúng với `MessageGateway.send(Object)`.

Hai biến đếm giúp test minh bạch: số lần `@Before` được gọi cho từng quy tắc, không phải số lần object aspect được tạo.

### 5.6. `src/main/java/com/example/argsdemo/AppConfig.java`

```java
package com.example.argsdemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
    @Bean
    public MessageGateway messageGateway() {
        return new MessageService();
    }

    @Bean
    public ArgsAuditAspect argsAuditAspect() {
        return new ArgsAuditAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật Spring auto-proxy creator. `MessageGateway` được Spring quản lý; `ArgsAuditAspect` vừa có `@Aspect` vừa được khai báo `@Bean` để Spring nhận diện. Cờ `false` cho phép JDK proxy khi có interface thích hợp, không có cấu hình khác ép CGLIB.

### 5.7. `src/main/java/com/example/argsdemo/DemoApp.java`

```java
package com.example.argsdemo;

import java.io.Serializable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    private static final class PlainPayload {
        @Override
        public String toString() {
            return "payload";
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            MessageGateway gateway = context.getBean(MessageGateway.class);
            ArgsAuditAspect aspect = context.getBean(ArgsAuditAspect.class);

            System.out.println("isJdkProxy="
                    + AopUtils.isJdkDynamicProxy(gateway));

            gateway.send("hello");
            gateway.send(new PlainPayload());
            gateway.send(Integer.valueOf(7));

            System.out.println("runtimeMatches=" + aspect.runtimeMatches());
            System.out.println("signatureMatches=" + aspect.signatureMatches());
        }
    }
}
```

`PlainPayload` không khai báo `implements Serializable`. `String` và `Integer` đều triển khai `Serializable` trên JDK 17+. Nếu thấy import `java.io.Serializable` trong `DemoApp` mà không được dùng trực tiếp, có thể bỏ import đó mà không ảnh hưởng kết quả; Java compiler cho phép unused imports nên project vẫn biên dịch.

### 5.8. Chạy và output dự kiến

Từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.argsdemo.DemoApp
```

Các dòng chương trình in ra theo đúng thứ tự; thông báo Maven/SLF4J nếu có không nằm trong phần output minh họa:

```text
isJdkProxy=true
[AOP args] String
[TARGET] String: hello
[TARGET] PlainPayload: payload
[AOP args] Integer
[TARGET] Integer: 7
runtimeMatches=2
signatureMatches=0
```

**Giải thích đầu ra:**

1. Spring tạo JDK proxy vì bean implement `MessageGateway` và advisor có thể áp dụng vào method `send(Object)`.
2. `"hello"` là `String`, implements `Serializable`, nên lời gọi qua proxy chạy `runtimeSerializable` trước khi target in nội dung.
3. `PlainPayload` không implements `Serializable`, vì vậy advice có điều kiện `args(payload)` với parameter `Serializable` không chạy; target vẫn thực hiện method.
4. `Integer.valueOf(7)` là `Integer`, cũng implements `Serializable`, nên advice chạy lần thứ hai.
5. `signatureMatches=0` vì method **khai báo Object**, bất kể runtime arguments là gì. Không có lời gọi nào thay đổi method signature ở bytecode.
6. Việc target chạy cả ba lần nhưng advice chỉ chạy hai lần cho thấy `args` đánh giá **mỗi invocation**; kết quả không phải cấu hình cố định của target object.

Code này là demo về **type matching**. Trong production, không coi `Serializable` là dấu hiệu đối số an toàn để ghi log hoặc serialize; nó là một interface marker của Java, không đại diện cho mức độ bảo mật hoặc độ tin cậy của payload.

## 6. Tại sao phải hiểu sự phụ thuộc vào runtime?

Một phần pointcut có thể được Spring đánh giá bằng class/method metadata khi dựng proxy. Nhưng với `args`, **cùng một method trên cùng một proxy** có thể cho kết quả khác nhau theo argument đang được truyền.

Trong API pointcut của Spring, một `MethodMatcher` có:

```java
boolean matches(Method method, Class<?> targetClass);
boolean isRuntime();
boolean matches(Method method, Class<?> targetClass, Object... args);
```

Đây là **các chữ ký giao diện được giản lược để giải thích**, không phải một class có thể biên dịch riêng. Khi `isRuntime()` đúng, Spring có thể cần phương thức kiểm tra có arguments cho từng method invocation. Điều này phản ánh chi phí thực tế của pointcut động, dù Spring còn tối ưu bằng static matching để loại nhanh những target methods không thể khớp.

Thực hành hữu ích:

- **Khoanh class/method bằng `execution` hoặc `within`**, rồi mới bổ sung `args` để lọc kiểu argument.
- Tránh chỉ khai báo `args(java.io.Serializable)` trên toàn application nếu chỉ muốn audit một business API cụ thể.
- Nếu yêu cầu là một method với chữ ký xác định, hãy cân nhắc chỉ dùng `execution` — có thể đơn giản hơn và tránh dynamic matching không cần thiết.
- Không dựa vào lời đồn rằng `args` tự check giá trị, nội dung object hoặc annotation trên class: các yêu cầu đó cần điều kiện khác.

**Vấn đề không chỉ là performance.** Pointcut rộng có thể khiến một advice phụ thuộc dữ liệu runtime được áp dụng tới nhiều module không mong đợi, làm tăng coupling hoặc tạo audit log chứa thông tin không phù hợp.

## 7. Kiểu kế thừa, interface và polymorphism

Giả sử:

```java
interface Event {}

class OrderEvent implements Event {}

class PaymentEvent implements Event {}
```

Đây là fragments minh họa; đặt trong file/package phù hợp khi triển khai.

Khi method khai báo `void handle(Object event)`, pointcut:

```text
args(com.example.domain.Event)
```

có thể khớp runtime arguments là `OrderEvent` hoặc `PaymentEvent` vì chúng implement `Event`. Với:

```text
args(com.example.domain.OrderEvent)
```

bộ lọc hẹp hơn và chỉ chọn đối số thuộc kiểu `OrderEvent` hoặc kiểu con phù hợp.

Trường hợp method khai báo `handle(Event event)` nhưng caller thực tế truyền `OrderEvent`, `execution(* *.handle(com.example.domain.Event))` xét **kiểu khai báo Event**, còn `args(com.example.domain.OrderEvent)` xét **đối tượng thực tế OrderEvent**. Cả hai có thể được kết hợp nếu mục đích là chọn method theo API và subtype theo runtime:

```text
execution(* com.example..EventService.handle(com.example.domain.Event))
&& args(com.example.domain.OrderEvent)
```

Kết hợp này thường dễ diễn giải hơn một pointcut chỉ dựa vào tên method hoặc đối số runtime.

### 7.1. Overload vẫn được chọn trước khi Spring AOP xét `args`

Nếu class có:

```java
void process(Object value)
void process(String value)
```

Java compiler sẽ quyết định overload theo **kiểu compile-time của biểu thức tại call site** (cùng các quy tắc overload resolution); Spring AOP `args` không quyết định method overload nào được gọi. Sau khi method đã được chọn và invocation đi qua proxy, pointcut mới có thể xem runtime arguments.

Ví dụ `Object x = "abc"; service.process(x);` chọn overload `process(Object)` tại compile time, mặc dù giá trị runtime là `String`. `args(String)` vẫn có thể khớp giá trị runtime, nhưng `execution(* *.process(String))` không đổi chữ ký method đã được gọi.

Điều này đặc biệt quan trọng khi debug Spring aspects trên overloaded service APIs.

## 8. Những trường hợp biên cần kiểm thử

### 8.1. Đối số `null`

`null` không có một concrete runtime class để gọi `getClass()`, nhưng các cơ chế matching/binding của AspectJ và Spring còn có thể xem xét chữ ký khai báo và quy tắc tương thích. Vì vậy, **không nên suy luận một cách tuyệt đối** rằng `args(SomeType)` lúc nào cũng match hoặc lúc nào cũng không match khi đối số là null.

Nếu method cho phép `null`, cần:

1. Viết integration test trên **Spring version và pointcut thực tế**.
2. Xác nhận lời advice có chạy hay không với các signatures khác nhau.
3. Nếu advice nhận được `null`, không gọi `payload.getClass()` khi chưa kiểm tra.
4. Nếu logic cần phân biệt null/non-null, cân nhắc dùng advice với `JoinPoint.getArgs()` hoặc kiểm tra explicit trong business validation, thay vì giả định một điều kiện `args(Type)` tương đương `value != null`.

Ví dụ bảo vệ:

```java
String typeName = (payload == null)
        ? "<null>"
        : payload.getClass().getSimpleName();
```

Đây là fragment Java, không có hệ quả Spring AOP riêng nếu không đặt trong advice/bean đúng cách.

### 8.2. Primitive và boxing

Method khai báo `void count(int n)` sử dụng primitive ở signature, nhưng Spring advice binding nhận values qua hệ thống object arguments và có liên quan đến boxing/unboxing. Một ví dụ `args(Integer)` trên primitive method có thể cần kiểm tra chi tiết matching theo Spring/AspectJ version, thay vì suy giản rằng runtime luôn có cùng kiểu `Integer` như đối tượng do caller tạo.

**Khuyến nghị:** khi business API dựa trên primitive signature, hãy dùng `execution(...int)` để định danh signature nếu đó mới là thứ cần chọn; chỉ đưa `args` vào khi bạn thực sự cần điều kiện runtime và có integration tests.

### 8.3. Varargs

Method `void send(String... messages)` được biên dịch nhận **một mảng String[]** ở vị trí varargs; lời gọi có thể viết `send("a","b")` nhưng signature JVM là một array parameter. Không nên tự mặc định `args(String, String)` sẽ match như một method khai báo hai tham số riêng. Kiểm tra quy tắc matching varargs/array trên Spring version sử dụng; nhiều trường hợp cần biểu thức phù hợp với **array argument** hơn là số phần tử varargs.

### 8.4. Generic type erasure

Spring docs nêu hạn chế khi dùng `args` để ràng buộc kiểu generic của collection như `Collection<MyType>`: runtime không chứa thông tin generic đầy đủ để Spring/AspectJ tự kiểm tra mọi phần tử trong collection.

Ví dụ mong muốn không nên viết:

```java
@Before(
    "execution(* com.example..Repository.*(..)) && args(list)"
)
public void before(java.util.Collection<MyType> list) {
    // Không thể yêu cầu Spring AOP xác nhận generic element type bằng args.
}
```

Đây là **ví dụ không phù hợp**, không phải code được khuyến nghị để chạy. Nếu cần nhận collection, sử dụng `Collection<?>` rồi **kiểm tra phần tử thực tế** theo nhu cầu nghiệp vụ, hoặc thiết kế API với một concrete request type chứa contract rõ ràng.

### 8.5. Runtime proxies, JDK/CGLIB và Spring bean boundary

`args` chỉ được đánh giá khi lời gọi **đã vào một Spring AOP proxy phù hợp**:

- Caller dùng `new MessageService().send("x")`: Spring AOP không chặn vì object không được quản lý qua proxy.
- Bean tự gọi `this.send("x")`: self-invocation thường bypass Spring AOP proxy, nên `args` không có dịp được kiểm tra.
- Cùng method được gọi qua JDK proxy: method phải nằm trong proxied interface và advice/pointcut phải khớp.
- CGLIB class proxy: chịu giới hạn như class/method `final`, `private`, những method không thể override.
- `@PostConstruct` gọi method trên raw target lúc khởi tạo: không tự tạo một Spring AOP interception cho lời gọi nội bộ.

**Đừng chẩn đoán vấn đề đường gọi bằng cách đổi `args` sang `execution`.** Nếu caller không đi qua proxy, cả hai đều không biến lời gọi raw target thành một join point mà Spring AOP có thể advice.

## 9. Những sai lầm thường gặp

| Triệu chứng | Nguyên nhân có thể | Cách sửa/kiểm tra |
| --- | --- | --- |
| `args(Serializable)` chạy với `String` dù method nhận `Object` | Đây là runtime type matching, đúng thiết kế. | So sánh với `execution(* *.method(Serializable))` để thấy chữ ký vẫn Object. |
| Advice chạy với lần gọi này nhưng không với lần gọi sau trên cùng method | Runtime argument types khác nhau. | Log an toàn tên kiểu, kiểm tra arguments và pointcut trong integration test. |
| `args(payload)` báo không bind được tham số | Advice thiếu parameter có tên/kiểu phù hợp hoặc metadata tên không được xác định. | Dùng `argNames`, khai báo parameter đúng kiểu và thứ tự, kiểm tra Spring Aspect binding. |
| `args(String,..)` không khớp như dự kiến | Đối số String không ở vị trí đầu, hoặc signature không tương ứng. | Kiểm tra vị trí, dấu `..` và argument count. |
| Pointcut muốn lọc `String` nhưng lại dùng `execution(...String)` | `execution` lọc kiểu tham số khai báo. | Dùng `args(String)` nếu cần lọc giá trị runtime và giới hạn với `execution` method. |
| Khai báo `@args(Serializable)` thay `args(Serializable)` | `@args` xét annotation trên class runtime, không phải interface/type `Serializable`. | Phân biệt ký hiệu có `@` và yêu cầu bài toán. |
| `args(Collection<Payment>)` không lọc theo phần tử | Java type erasure và giới hạn matching generics. | Bind `Collection<?>`, kiểm tra elements bằng code nếu thật sự cần. |
| Pointcut chọn cả nhiều service không mong đợi | `args(...)` không giới hạn bean/class/method nếu đứng riêng. | Kết hợp `execution`/`within` hẹp theo package/service. |
| Có `@Aspect` nhưng không chạy với bất kỳ argument nào | Aspect chưa là Spring bean, auto-proxy chưa bật, hoặc caller bypass proxy. | Kiểm tra bean registration, `@EnableAspectJAutoProxy`, `AopUtils` và đường gọi. |
| Advice gây `NullPointerException` ở `getClass()` | Chưa bảo vệ dữ liệu null khi advice được gọi. | Kiểm tra null và test riêng hành vi của pointcut/binding. |
| Advice kỳ vọng lọc `amount > 1000` chỉ bằng `args(Number)` | `args` là điều kiện kiểu, không phải điều kiện giá trị. | Kiểm tra `number.doubleValue()` hoặc logic nghiệp vụ rõ ràng bên trong advice. |

## 10. Cách chọn giữa `args`, `execution`, `@args` và việc đọc `JoinPoint`

| Yêu cầu thực tế trong Spring | Cơ chế nên cân nhắc |
| --- | --- |
| Chọn method `send` có chữ ký `send(Object)` | `execution(* ..MessageGateway.send(java.lang.Object))`. |
| Chỉ chạy advice khi runtime argument implements `Serializable` | `execution(* ..MessageGateway.send(..)) && args(java.io.Serializable)`. |
| Lấy đối số thực tế vào advice theo kiểu cụ thể | `args(payload)` và khai báo advice parameter `Serializable payload`, kèm `argNames` khi cần. |
| Chỉ chọn argument có class runtime mang một annotation | `@args(com.example.MyMarker)`, cần cấu hình annotation runtime phù hợp. |
| Chọn method có annotation `@Audit` | `@annotation(com.example.Audit)`, không phải `@args`. |
| Chọn method của service package trước khi lọc argument runtime | Dùng `execution` hoặc `within` như scope + `args`. |
| Cần so sánh giá trị/khoảng tiền/id | Advice dùng `JoinPoint.getArgs()` hoặc binding rồi kiểm tra giá trị, không chỉ `args(Type)`. |

Ví dụ cách khai báo pointcut có tên để tái sử dụng:

```java
@Pointcut(
    value = "execution(public * com.example..MessageGateway.send(..))"
          + " && args(payload)",
    argNames = "payload"
)
public void serializableMessages(Serializable payload) {}

@Before(value = "serializableMessages(payload)", argNames = "payload")
public void audit(Serializable payload) {
    // ...
}
```

Đây là **fragment trong một class `@Aspect`**, cần imports `@Pointcut`, `@Before` và `Serializable`. Nhiều advices có thể tham chiếu named pointcut để dùng chung logic, nhưng vẫn cần đặt tên binding nhất quán ở từng advice.

## 11. Kiểm thử cho `args` trong một ứng dụng Spring

Một integration test tốt nên dùng **Spring context thật** và một bean được trả về dưới dạng proxy:

1. Xác minh aspect bean và target bean được đăng ký đúng context.
2. Xác minh `AopUtils.isAopProxy(bean)`; nếu dùng JDK proxy, có thể kiểm tra `isJdkDynamicProxy(bean)`.
3. Chạy method một lần với argument **đúng kiểu runtime**; kỳ vọng counter advice tăng.
4. Chạy lại **cùng method/cùng proxy** với argument khác kiểu; kỳ vọng counter không tăng.
5. So với pointcut `execution` lọc chữ ký để chắc chắn không nhầm signature/runtime types.
6. Test polymorphism: interface/supertype và subtype runtime.
7. Test method overloads/varargs, primitive, null và generic collection nếu API hỗ trợ.
8. Kiểm tra values/exception của target không bị thay đổi vô tình khi thêm `@Before`/`@Around`.
9. Kiểm tra mỗi advice chạy **đúng số lần** nếu nhiều `@Aspect` cùng khớp method.
10. Giữ pointcut đủ hẹp để không áp dụng nhầm trên beans khác.

Ví dụ assertion khái quát cho ba lời gọi trong bài:

```text
send("hello")             -> runtime Serializable -> advice +1
send(new PlainPayload())  -> runtime không Serializable -> advice +0
send(Integer.valueOf(7)) -> runtime Serializable -> advice +1

Tổng: target chạy 3 lần; args advice chạy 2 lần.
```

Đây là một kết quả cho **cấu hình demo đã nêu**, không phải khẳng định rằng mọi pointcut `args` trong mọi Spring application đều có số lần advice tương tự.

### Tài liệu chính thức

- Spring Framework — Declaring a Pointcut / Supported Pointcut Designators: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — Declaring Advice / Passing Parameters to Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Pointcut API / Static and Dynamic MethodMatcher: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận:** Trong Spring Aspect, `args` lọc lời gọi theo **kiểu đối số được truyền thực tế** và có thể bind đối số đó vào method advice; `execution` chọn phương thức theo **chữ ký khai báo**. Kết hợp cả hai trên **Spring bean được gọi qua proxy** giúp định nghĩa pointcut vừa đúng ý nghiệp vụ, vừa tránh áp dụng advice quá rộng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-005">@annotation để chọn phương thức mang annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Sử dụng @annotation để chọn phương thức mang annotation trong Spring AOP

## 1. Mục tiêu bài học

Trong ứng dụng Spring Framework, thay vì khớp tên method theo một quy ước cố định, ta có thể chủ động đánh dấu những **phương thức Spring bean** cần áp dụng cross-cutting logic bằng annotation và viết pointcut Spring AOP sử dụng `@annotation`.

Bài này trả lời cụ thể:

1. `@annotation(...)` chọn method execution nào trong Spring AOP và khác gì với `execution(...)` thuần túy?
2. Làm thế nào viết custom annotation `@Retention(RUNTIME)`, `@Target(METHOD)`, rồi dùng `@Before` chỉ chặn method được đánh dấu?
3. Làm thế nào **bind annotation instance vào advice** để đọc thuộc tính `value`/`tag` của annotation?
4. Có thể kết hợp `@annotation` với `execution` để giới hạn đúng package, class, tầng service không?
5. Vì sao cùng method có annotation vẫn không chạy advice khi gọi bằng `this.method()`, `new` object, hoặc method không thể intercept qua Spring proxy?
6. Làm thế nào quan sát và chẩn đoán kết quả bằng Spring AOP proxy và bộ đếm advice?

**Giới hạn phạm vi:** Đây là **Spring AOP trên Spring-managed beans**, sử dụng cú pháp `@AspectJ` nhưng thực thi bằng proxy JDK/CGLIB. Không nhầm với AspectJ bytecode weaving và không biến annotation thành interceptor JVM cho mọi object.

## 2. Hiểu `@annotation` trong ngữ cảnh Spring AOP

Spring AOP hỗ trợ pointcut designator `@annotation` để chọn **method execution mà method tương ứng có annotation được chỉ định**. Đây là một điều kiện khớp join point khi lời gọi **đi qua Spring AOP proxy**.

Ví dụ annotation:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

Với method của Spring bean:

~~~java
@TrackedAction("checkout")
public void checkout() {
    // ...
}

public void preview() {
    // ...
}
~~~

Nếu cả hai lời gọi đều đi qua bean proxy và đủ điều kiện advice, chỉ `checkout()` khớp `@annotation(demo.TrackedAction)`; `preview()` không khớp chỉ vì nằm cùng class.

### Hai dạng sử dụng quan trọng

**Dạng chọn annotation theo tên kiểu:**

~~~java
@Before("@annotation(demo.TrackedAction)")
public void before() {
    System.out.println("Annotated method");
}
~~~

`demo.TrackedAction` là tên đầy đủ của **loại annotation**. Advice này chỉ cần biết phương thức được đánh dấu, không cần đọc thuộc tính của annotation.

**Dạng bind instance annotation vào tham số advice:**

~~~java
@Before(value = "@annotation(trackedAction)", argNames = "trackedAction")
public void before(TrackedAction trackedAction) {
    System.out.println(trackedAction.value());
}
~~~

Trong dạng này, `trackedAction` là **tên binding variable**, không phải tên class annotation. Spring suy ra kiểu `TrackedAction` từ tham số advice, bind đối tượng annotation phù hợp và cho phép đọc `trackedAction.value()`.

Đây là hai snippet minh họa cần đặt vào một `@Aspect` đã đăng ký làm Spring bean, không phải các chương trình Java đầy đủ. Không đặt **cả hai** advice vào cùng ví dụ nếu bạn chỉ muốn một dòng log: khi hai advice cùng khớp, cả hai đều có thể chạy.

Lưu ý cú pháp:

- `@annotation(demo.TrackedAction)`: khớp theo **loại annotation**.
- `@annotation(trackedAction)`: bind annotation theo **tên biến**; tên đó phải tương ứng tham số nhận annotation của advice (hoặc được Spring suy ra đúng).
- Có thể ghi `argNames = "trackedAction"` rõ ràng để tránh phụ thuộc tên tham số sau khi biên dịch. Khi advice có `JoinPoint` ở tham số **đầu**, không cần khai báo tên tham số `JoinPoint` trong `argNames`.
- Với điểm cắt đa điều kiện, dùng `&&` và `||` giống biểu thức pointcut AspectJ, ví dụ `execution(public * demo.AuditService.*(..)) && @annotation(trackedAction)`.

### `@annotation` khác `execution` như thế nào?

| Pointcut | Quy tắc khớp chính | Khi nên dùng |
| --- | --- | --- |
| `execution(* demo.AuditService.ship(..))` | Theo chữ ký/method execution | Áp dụng cho tên method hoặc nhóm method xác định |
| `execution(public * demo..service..*(..))` | Theo visibility, return type, package/type, method | Áp dụng thống nhất cho service layer |
| `@annotation(demo.TrackedAction)` | Theo method có annotation `@TrackedAction` | Chỉ áp dụng cho method được đánh dấu chủ động |
| `execution(* demo.AuditService.*(..)) && @annotation(demo.TrackedAction)` | Phải thỏa cả class/method scope và annotation | Giảm vùng xét/giảm match không mong muốn |
| `@annotation(trackedAction)` | Theo annotation và bind giá trị cho advice | Advice cần đọc annotation attribute để audit/log/tag |

`@annotation` không thay thế việc cần một **Spring AOP join point có thể chặn qua proxy**. Một method có annotation nhưng bị gọi trực tiếp trên target vẫn không tự chạy advice.

## 3. Tạo annotation đúng cách

Hai meta-annotation Java đặc biệt quan trọng:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

- `@Retention(RetentionPolicy.RUNTIME)`: giữ annotation ở runtime để cơ chế AOP dùng metadata tại runtime. Nếu dùng `SOURCE` thì annotation biến mất sau compile; dùng `CLASS` không đảm bảo sẵn qua Java reflection runtime.
- `@Target(ElementType.METHOD)`: cho phép gắn vào **method** (không tự biến thành annotation cấp class/field/parameter).
- `String value()`: attribute bắt buộc. Khi viết `@TrackedAction("fulfillment")`, `"fulfillment"` là giá trị của `value`.
- Có thể thêm default như `String category() default "general";`. Nếu advice cần dùng attribute, bind annotation thay vì parse annotation bằng chuỗi.

**Không cần thêm `@Inherited`** cho bài này. `@Inherited` của Java chủ yếu có ý nghĩa đối với annotation trên **class**, không tự khiến annotation trên method của interface hoặc superclass được xem như annotation trực tiếp của mọi method override. Khi làm việc với interface method, overriding method hoặc composed/meta-annotation của Spring, cần kiểm tra cách Spring/AspectJ resolver thực sự xác định method annotation theo proxy type, không áp dụng giả định kế thừa tùy ý.

Và đừng nhầm `@TrackedAction` với `@Aspect`:

- `@TrackedAction` là annotation đánh dấu **method nghiệp vụ** để pointcut tìm.
- `@Aspect` đánh dấu **class chứa advice**, Spring chỉ tìm nó nếu class đó đã được **đăng ký thành Spring bean**.
- `@EnableAspectJAutoProxy` (hoặc auto-configuration tương ứng) bật hỗ trợ Spring AOP để advice thực thi qua proxy.

## 4. Ví dụ chạy được: hai method có annotation, một method không có

Dự án Maven nhỏ dưới đây sử dụng **Java 17+**, **Spring Framework 6.2.0** và **AspectJ Weaver 1.9.22**. Đây là Spring Framework thuần, không phụ thuộc Spring Boot và không dùng AspectJ compiler/agent.

Cấu trúc đầy đủ:

~~~text
spring-annotation-pointcut/
├── pom.xml
└── src/main/java/demo/
    ├── Demo.java
    ├── TrackedAction.java
    ├── AuditService.java
    └── TraceAspect.java
~~~

### 4.1. Cấu hình Maven

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-annotation-pointcut</artifactId>
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
                    <mainClass>demo.Demo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` tạo `ApplicationContext` và xử lý Java config; `spring-aop` cung cấp proxy/interceptor/`AopUtils`. `aspectjweaver` cung cấp thư viện dùng khi Spring diễn giải biểu thức pointcut/`@AspectJ`; **không có `-javaagent` ở đây**, vì runtime vẫn là Spring AOP dựa trên proxy.

`<parameters>true</parameters>` giúp javac giữ tên tham số để Spring có thể tra cứu khi cần. Ví dụ vẫn khai báo `argNames` rõ ràng để người học thấy chính xác binding variable nào được sử dụng.

### 4.2. File annotation có retention runtime

Tạo `src/main/java/demo/TrackedAction.java`:

~~~java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

### 4.3. Service với method được/không được đánh dấu

Tạo `src/main/java/demo/AuditService.java`:

~~~java
package demo;

public class AuditService {
    @TrackedAction("fulfillment")
    public void ship(String orderId) {
        System.out.println("SERVICE: ship " + orderId);
    }

    public void ping() {
        System.out.println("SERVICE: ping");
    }

    @TrackedAction("account")
    public void adjust(String customerId) {
        System.out.println("SERVICE: adjust " + customerId);
    }

    public void callShipInside() {
        System.out.println("SERVICE: callShipInside");
        this.ship("A-101");
    }
}
~~~

Với `ship(...)` và `adjust(...)`, annotation ở **method triển khai** sẽ được `@annotation` nhận diện khi caller gọi qua proxy. `ping()` không gắn annotation nên không match. `callShipInside()` cũng không gắn annotation; bên trong nó gọi `this.ship(...)`, cho phép quan sát giới hạn **self-invocation** của Spring AOP proxy.

Các method đều `public` và class không `final`, để CGLIB class proxy có thể intercept chúng. Trong project tối thiểu, không cần `@Service` vì service sẽ được tạo bằng `@Bean` từ `Demo`.

### 4.4. Aspect bind giá trị annotation

Tạo `src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger adviceCount = new AtomicInteger();

    @Before(
        value = "execution(* demo.AuditService.*(..)) && @annotation(trackedAction)",
        argNames = "trackedAction"
    )
    public void before(JoinPoint point, TrackedAction trackedAction) {
        adviceCount.incrementAndGet();
        System.out.println(
            "ADVICE: tag=" + trackedAction.value()
            + " method=" + point.getSignature().getName()
        );
    }

    public int count() {
        return adviceCount.get();
    }
}
~~~

Phân tích chính xác cách advice khớp:

1. `execution(* demo.AuditService.*(..))` giới hạn các method execution trên `AuditService`. Đây là ràng buộc scope bổ sung, tránh annotation trùng tên ở class khác vô tình tham gia.
2. `@annotation(trackedAction)` chỉ chấp nhận method tương ứng có annotation có kiểu bind được vào tham số `TrackedAction trackedAction`.
3. `argNames = "trackedAction"` tường minh tên biến cần bind trong pointcut với tham số của advice. Tham số đầu `JoinPoint point` được Spring xử lý đặc biệt, nên không cần nằm trong `argNames`.
4. `trackedAction.value()` đọc attribute của **annotation gắn trên method đang được Spring AOP xét**. Ví dụ in `fulfillment` hoặc `account` mà không cần logic đọc annotation bằng reflection thủ công.
5. `point.getSignature().getName()` lấy tên method đang được advice ở join point. Trong ví dụ kết quả là `ship` hoặc `adjust`.
6. `adviceCount` chỉ tăng bên trong advice; dùng `AtomicInteger` để phép đo rõ ràng. Chương trình chỉ gọi tuần tự nên không cần giả lập nhiều thread.

Điểm dễ nhầm: `trackedAction` ở biểu thức pointcut **không phải** biến Java bên ngoài method; nó là tên binding dùng bởi Spring/AspectJ để truyền annotation instance vào method advice.

### 4.5. Bật AOP, đăng ký các bean và kiểm tra kết quả

Tạo `src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Demo {
    @Bean
    public AuditService auditService() {
        return new AuditService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Demo.class)) {
            AuditService service = context.getBean(AuditService.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);

            boolean isProxy = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + isProxy);

            service.ship("A-100");
            service.ping();
            service.adjust("C-200");
            service.callShipInside();

            int count = aspect.count();
            System.out.println("ADVICE_COUNT: " + count);

            if (!isProxy || count != 2) {
                throw new IllegalStateException(
                    "Expected @annotation advice exactly twice");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Tại sao `@EnableAspectJAutoProxy(proxyTargetClass = true)` được dùng?

- Nó kích hoạt **Spring AOP auto-proxy**, cho phép Spring tạo subclass proxy cho `AuditService`, dù class không triển khai interface.
- Nó không tự kích hoạt toàn bộ mọi method; proxy/advisor vẫn xem xét method nào khớp pointcut.
- `@Bean auditService()` đăng ký service như một Spring-managed bean. `@Bean traceAspect()` đăng ký aspect; chỉ đặt `@Aspect` trên class mà không đăng ký bean sẽ không đủ.
- `context.getBean(AuditService.class)` trả Spring reference; nếu có advisor phù hợp, đây là proxy chứ không phải object tự `new` bên ngoài container.
- `service.callShipInside()` đi vào target method, rồi `this.ship("A-101")` là lời gọi **nội bộ trực tiếp**. Dù `ship` có annotation, lời gọi nội bộ bypass proxy nên `before` không chạy lần thứ ba.

### 4.6. Chạy Maven và stdout mong đợi

Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã Java trong ví dụ in ra, theo đúng thứ tự:

~~~text
PROXY: true
ADVICE: tag=fulfillment method=ship
SERVICE: ship A-100
SERVICE: ping
ADVICE: tag=account method=adjust
SERVICE: adjust C-200
SERVICE: callShipInside
SERVICE: ship A-101
ADVICE_COUNT: 2
CHECK: PASS
~~~

Maven hoặc logger trong môi trường chạy có thể có thêm output. Mười dòng trên là các message `System.out.println` của ví dụ.

Diễn giải:

- `PROXY: true`: service bean đã được Spring AOP xử lý thành proxy.
- `ship("A-100")` có annotation `@TrackedAction("fulfillment")` và gọi qua proxy, nên advice in giá trị `fulfillment` trước business code.
- `ping()` không có `@TrackedAction`, do đó không có dòng `ADVICE` tương ứng.
- `adjust("C-200")` có annotation `@TrackedAction("account")`, advice được gọi và bind value `account`.
- `callShipInside()` không annotated. Lời gọi `this.ship("A-101")` vẫn thực thi mã service nhưng **không quay qua proxy**, nên không xuất hiện `ADVICE: tag=fulfillment` cho `A-101`.
- `ADVICE_COUNT: 2` xác minh advice thực sự chạy đúng hai lần trên những lời gọi qua proxy đã được đánh dấu.
- `CHECK: PASS` chỉ xuất hiện sau khi proxy tồn tại và count đúng bằng `2`.

Mục tiêu chính là chứng minh **cả điều kiện annotation và ranh giới Spring proxy**, tránh kết luận sai rằng `@annotation` can thiệp vào mọi lời gọi Java tới annotated method.

## 5. Tách scope và annotation bằng @Pointcut

Trong project thực tế, nhiều advice có thể dùng cùng một ràng buộc. Có thể đặt tên pointcut:

~~~java
@Pointcut("execution(public * demo.AuditService.*(..))")
public void auditServiceOperations() {
}

@Before(
    value = "auditServiceOperations() && @annotation(trackedAction)",
    argNames = "trackedAction"
)
public void before(JoinPoint jp, TrackedAction trackedAction) {
    System.out.println("Tracked: " + trackedAction.value());
}
~~~

Đây là **đoạn code trích để đặt bên trong một `@Aspect`**, không thêm nguyên xi vào `TraceAspect` khi nó đã có `before(...)` trùng nhiệm vụ. Cần import `org.aspectj.lang.annotation.Pointcut` và `Before`, `JoinPoint` cùng annotation `TrackedAction`. Method `auditServiceOperations()` chỉ mô tả pointcut; **nội dung method phải rỗng** và không chứa business logic.

Điểm lợi:

- `auditServiceOperations()` diễn tả **phạm vi của Spring service**; `@annotation(trackedAction)` thêm yêu cầu method có annotation.
- Có thể tái dùng named pointcut cho `@Before` hoặc `@Around` mà vẫn giữ code dễ đọc.
- Khi đổi package/class scope, sửa định nghĩa named pointcut thay vì lặp nhiều chuỗi biểu thức.
- Kết hợp scoping designator làm intent rõ ràng hơn, tránh pointcut `@annotation(...)` quá rộng khắp toàn application.

Nếu muốn áp dụng cho nhiều class service trong cùng package, thay ràng buộc `demo.AuditService.*(..)` bằng một `execution(...)` phù hợp package convention và test lại match trên bean thực tế. Không cố “sửa” annotation match bằng cách nới pointcut tới toàn JVM.

## 6. Phân biệt @annotation với các designator @-khác trong Spring

Spring AOP hỗ trợ một tập designator theo ngữ cảnh; phần tiền tố `@` không có nghĩa tất cả kiểm tra cùng một nơi:

| Designator | Ý nghĩa đối với method execution Spring AOP | Ví dụ mục đích |
| --- | --- | --- |
| `@annotation(X)` | Method ở join point có annotation kiểu `X` | Audit riêng các method được đánh dấu |
| `@within(X)` | Join point nằm trong kiểu class có annotation tương ứng | Chọn các method trong class được đánh dấu |
| `@target(X)` | Runtime target object có kiểu được đánh dấu bằng annotation đó | Chọn theo annotation trên loại đối tượng đích |
| `@args(X)` | Dựa vào annotation trên kiểu runtime của argument đi vào method | Trường hợp chọn theo metadata của object đầu vào |
| `execution(...)` | Chọn method execution theo chữ ký và phạm vi | Chọn service/package/method cần advice |
| `within(...)` | Giới hạn theo kiểu/phạm vi khai báo method | Thu hẹp phạm vi xét advisor |

Ví dụ, một class được đánh dấu:

~~~java
@TrackedService
public class OrderService {
    public void submit() {
    }
}
~~~

Nếu `@TrackedService` là annotation có `@Target(TYPE)` và `@Retention(RUNTIME)`, các biểu thức `@within(demo.TrackedService)` hoặc `@target(demo.TrackedService)` có ý nghĩa **khác** `@annotation(demo.TrackedService)`: một annotation chỉ đánh dấu **class** không tự xuất hiện trên từng method. Đoạn trên là ví dụ minh họa về annotation `@TrackedService` chưa định nghĩa trong project demo; không phải file chạy độc lập.

Tương tự, `@args` không kiểm tra annotation trên tham số khai báo của method; nó dựa trên **kiểu runtime của argument**. Khi chỉ cần đánh dấu một method nghiệp vụ để AOP xử lý, `@annotation` phù hợp và dễ kiểm soát hơn.

## 7. Những giới hạn quan trọng trong Spring AOP

### 7.1. Có annotation chưa đủ nếu không đi qua proxy

Một biểu thức pointcut chính xác vẫn không chạy trong các tình huống sau:

- Đối tượng được tạo bằng `new AuditService()` bên ngoài `ApplicationContext`.
- `@Aspect` class không được đăng ký làm Spring bean, hoặc `@EnableAspectJAutoProxy` chưa được bật trong context đang dùng.
- Caller gọi annotated method bằng `this.ship()` từ một method khác trên chính target (self-invocation).
- Method `private`/`final` không thể bị chặn theo cách class-based subclass proxy; `final` class không thể được CGLIB subclass.
- Pointcut scope `execution(...)` quá hẹp khiến method có annotation nằm ngoài phạm vi đã chỉ định.
- Bean bị khởi tạo ở giai đoạn quá sớm nên chưa được auto-proxy, hoặc lời gọi đi tới raw target thay vì Spring bean reference.

Đặc biệt, **`proxyTargetClass = true` không chữa self-invocation**. CGLIB vẫn là Spring proxy đứng ở ranh giới caller-target. Giải pháp ít phụ thuộc framework là tách method cần advice sang bean khác và gọi qua dependency được Spring inject.

### 7.2. Annotation trên interface và implementation

Trong Java, annotation cấp method không được kế thừa tự động như quan hệ interface/implementation. Với Spring AOP, match thực tế có thể bị ảnh hưởng bởi:

- JDK proxy hay CGLIB proxy;
- annotation đặt ở interface method, implementation method hoặc method override;
- cách Spring phân giải method cụ thể/bridge method của target;
- một số cơ chế meta-annotation/composed annotation đặc thù Spring.

**Khuyến nghị dễ kiểm chứng:** trong demo đơn giản, đặt annotation trực tiếp trên **implementation method** là join point mong muốn, như `AuditService.ship` và `AuditService.adjust`; viết integration test dùng proxy configuration thật nếu project cố ý đặt annotation ở interface hoặc base class. Không mặc định `@Inherited` sẽ tự làm annotation của interface method xuất hiện trên implementation method.

Nếu một thư viện sử dụng annotation chuyên biệt với Spring, đừng tự suy rằng `@annotation(...)` luôn tuân theo quy tắc tìm **merged/meta-annotations** giống tất cả Spring annotation utilities. AspectJ pointcut matching và Spring annotation discovery không hoàn toàn là cùng một API. Khi cần semantic “annotation trực tiếp hoặc meta-annotation”, hãy kiểm tra thực nghiệm và cân nhắc truy xuất metadata cụ thể bằng Spring utilities phù hợp thay vì chỉ đổi chuỗi pointcut.

### 7.3. @annotation không biến mọi annotation thành nghiệp vụ đặc biệt

`@TrackedAction` chỉ là dấu mốc của ứng dụng. Bản thân việc khai báo annotation không tự tạo log, transaction, validation hay security. Cần **aspect/advisor** phù hợp, Spring context quản lý target và proxy có thể chặn method.

Tương tự, nếu ứng dụng đã có `@Transactional` hoặc `@Cacheable`, các annotation đó hoạt động thông qua cơ chế hạ tầng Spring riêng khi đã được bật. Đừng nghĩ mọi annotation tự động được pointcut custom của mình nhận diện hoặc có cùng thứ tự advisor. Khi nhiều advice chạy trên một method, kiểm tra thứ tự bằng `@Order` hoặc cơ chế ordering tương ứng nếu logic cần sự nhất quán.

### 7.4. Có nên dùng @Around thay @Before?

`@Before` phù hợp nếu mục tiêu là audit hoặc ghi nhận metadata trước khi method chạy. Nếu cần đo thời gian thực thi/quan sát return value hay exception quanh lời gọi, có thể chọn `@Around` nhưng **phải gọi `proceed()`** khi muốn business method tiếp tục:

~~~java
@Around(
    value = "execution(* demo.AuditService.*(..)) && @annotation(trackedAction)",
    argNames = "trackedAction"
)
public Object around(
        ProceedingJoinPoint pjp, TrackedAction trackedAction) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long nanos = System.nanoTime() - started;
        System.out.println(trackedAction.value() + ": " + nanos + " ns");
    }
}
~~~

Đây là **đoạn minh họa thay advice** trong một `@Aspect`, cần import `org.aspectj.lang.ProceedingJoinPoint` và `org.aspectj.lang.annotation.Around`. Nó không phải program chạy độc lập và không nên đồng thời giữ cả `@Before` lẫn `@Around` nếu bạn đang kiểm tra một dòng advice duy nhất.

Chú ý `System.nanoTime()` đo elapsed duration tương đối, **không phải** timestamp tuyệt đối. Thời gian chạy và stdout số nanosecond biến thiên theo máy, vì vậy bài thực hành chính dùng `@Before` với output xác định.

## 8. Gỡ lỗi khi method có annotation mà advice không chạy

Một quy trình chẩn đoán thực tế:

1. **Xác nhận annotation có thể thấy ở runtime:** kiểm tra `@Retention(RUNTIME)` và `@Target(METHOD)`, placement chính xác trên method dự kiến. Một annotation cấp class không tự khớp `@annotation` method.
2. **Kiểm tra Spring bean:** method đang được gọi trên bean do `ApplicationContext` xuất ra hay object tự `new`? Aspect đã đăng ký thành bean và AOP đã bật chưa?
3. **Kiểm tra proxy:** dùng `AopUtils.isAopProxy(service)`, `isJdkDynamicProxy(service)` và `isCglibProxy(service)` trên **reference thực sự được caller sử dụng**.
4. **Đọc nguyên biểu thức pointcut:** `execution(...)` có khớp đúng package/class/method không, `@annotation(...)` là kiểu annotation hay tên binding variable?
5. **Kiểm tra advice binding:** khi dùng `@annotation(variable)`, method advice phải khai báo tham số có kiểu annotation phù hợp; nếu không dùng `-parameters` hoặc không thể suy tên tham số, khai báo `argNames` rõ ràng.
6. **Đo số lần và thứ tự chạy:** tăng counter bên trong advice, ghi marker riêng để biết advice chạy trước business method. Không chỉ dựa vào bean có proxy.
7. **Xem self-invocation và visibility:** `this.method()`, `private`, `final`, JDK vs CGLIB, hoặc method không được proxy expose có thể khiến advice không chạy.
8. **Kiểm tra annotation placement qua interface/override:** đặt annotation ở implementation để kiểm tra đối chứng; test lại với proxy strategy thực tế.
9. **Nếu nhiều context/auto-proxy creators:** đảm bảo aspect và target thuộc context đúng; cấu hình AOP thường có phạm vi theo `ApplicationContext`.
10. **Sau khi sửa:** chạy lại kiểm tra trong context hoàn chỉnh và xác nhận marker, counter, kết quả business method.

### Lỗi phổ biến với binding annotation

**Lỗi 1 — tưởng `@annotation(trackedAction)` là tên class**

~~~java
@Before("@annotation(trackedAction)")
public void before() {
    // Không có tham số annotation để bind như dự định
}
~~~

Trong trường hợp này, bạn dùng **tên binding** nhưng advice không khai báo biến `trackedAction` tương ứng. Hãy đổi thành `@annotation(demo.TrackedAction)` nếu chỉ cần match type, hoặc thêm tham số `TrackedAction trackedAction` như ví dụ đúng.

**Lỗi 2 — viết annotation không có `RUNTIME` retention**

~~~java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface TrackedAction {
    String value();
}
~~~

Đây là **ví dụ sai retention dành cho nhu cầu runtime matching**: `CLASS` không giữ annotation sẵn ở runtime theo cách yêu cầu. Đừng đưa đoạn này vào project hoạt động đồng thời với `TrackedAction` chuẩn.

**Lỗi 3 — method được đánh dấu nhưng nằm ngoài scope**

~~~java
@Before("execution(* demo.AuditService.ship(..)) && @annotation(demo.TrackedAction)")
public void before() {
}
~~~

Nếu bạn gắn `@TrackedAction` cho `adjust`, biểu thức này vẫn **không match `adjust`** vì nó chỉ chọn `ship`. Annotation matching và method-signature matching là quan hệ **AND** ở đây.

**Lỗi 4 — dùng proxy nhưng self-call**

~~~java
public void outer() {
    this.ship("A-101");
}
~~~

Khi `outer` được gọi trên Spring bean proxy, code bên trong target gọi `this.ship` sẽ đi thẳng tới target; đừng quy lỗi cho `@annotation`. Đây là một giới hạn của Spring proxy-based AOP.

## 9. Bài tập mở rộng thực hành

### Bài 1: Chọn ba method bằng một annotation

Thêm một method vào `AuditService`:

~~~java
@TrackedAction("refund")
public void refund(String orderId) {
    System.out.println("SERVICE: refund " + orderId);
}
~~~

Gọi `service.refund("R-300")` từ `Demo.main()` sau khi lấy bean proxy. Khi đó có thêm:

~~~text
ADVICE: tag=refund method=refund
SERVICE: refund R-300
~~~

Nếu vẫn gọi hai method annotated ban đầu đúng một lần và chỉ thêm một lời gọi `refund` qua proxy, `ADVICE_COUNT` phải là `3`. Nhớ sửa assertion `count != 2` thành `count != 3`; nếu không, check sẽ báo lỗi vì kỳ vọng cũ, không phải vì advice hỏng.

### Bài 2: Chỉ match annotation trong một service cụ thể

So sánh:

~~~java
@Before("@annotation(demo.TrackedAction)")
public void logAnnotated() {
}
~~~

và:

~~~java
@Before("execution(* demo.AuditService.*(..)) && @annotation(demo.TrackedAction)")
public void logServiceAnnotated() {
}
~~~

Hai đoạn là **hai lựa chọn minh họa**, không nên thêm đồng thời vào aspect demo với advice cũ nếu bạn muốn đếm từng join point một lần. Dạng đầu rộng hơn: chọn annotated method thuộc các Spring beans bất kỳ nếu interceptable; dạng sau chỉ chọn scope `AuditService`.

### Bài 3: Tách self-invocation sang bean khác

Đặt method annotated `ship` vào `ShippingService` là Spring bean, rồi để `AuditService` nhận `ShippingService` qua constructor injection; khi `AuditService` gọi `shippingService.ship("A-101")`, lời gọi qua dependency proxy có thể được advice. Trước khi kết luận, xác nhận Spring tạo bean và advisor match. Không sửa bằng cách gọi `new ShippingService()` bên trong target.

## 10. Checklist ghi nhớ

- [ ] Custom annotation dùng `@Retention(RUNTIME)` và `@Target(METHOD)`.
- [ ] Class có `@Aspect` đã được đăng ký như một **Spring bean**.
- [ ] Context đã bật Spring AOP bằng `@EnableAspectJAutoProxy` (hoặc auto-configuration tương đương).
- [ ] Target service cũng là Spring bean; caller gọi method qua proxy.
- [ ] `@annotation(demo.TrackedAction)` là **chọn loại**; `@annotation(trackedAction)` là **bind instance**.
- [ ] Khi binding, tham số advice có kiểu `TrackedAction` phù hợp và tên binding được xác định bằng `argNames` hoặc thông tin tham số compile-time.
- [ ] Pointcut scope `execution(...)` không vô tình loại method annotated mong muốn.
- [ ] Method không phải `private`/`final` không interceptable theo proxy đang dùng; không có self-invocation bỏ qua proxy.
- [ ] Đã xác minh cả proxy status và số lần advice chạy, không chỉ dựa vào sự tồn tại annotation.

## 11. Tài liệu Spring Framework

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `@annotation` trong các pointcut designators mà Spring AOP hỗ trợ.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): binding annotation instance, `argNames`, `@Before` và `@Around`.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy` và cách Spring đăng ký aspect.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): lời gọi đi qua proxy, self-invocation và giới hạn JDK/CGLIB.
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring dùng kiểu khai báo `@AspectJ` nhưng vẫn là Spring proxy-based AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-006">@within và @target theo annotation của kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# @within và @target theo annotation của kiểu trong Spring AOP

## 1. Bài toán: chọn Spring bean theo annotation trên class

Khi nhiều Spring service triển khai những tác vụ khác nhau, ta có thể muốn áp cùng một advice (ghi audit, đo thời gian, log chẩn đoán) lên **những service có đánh dấu một annotation ở cấp kiểu**, thay vì thêm annotation vào từng method hoặc hard-code danh sách tên class.

Spring AOP hỗ trợ hai pointcut designator gần nhau:

- `@within(com.example.aop.TracedType)`: chọn các **method execution join point nằm trong những kiểu có annotation được chỉ định**. Theo cách diễn đạt của Spring Framework, với Spring AOP đây là các method execution **được khai báo trong kiểu có annotation đó**.
- `@target(com.example.aop.TracedType)`: chọn method execution join point khi **class của đối tượng target đang thực thi** mang annotation được chỉ định.

Chúng đều sử dụng annotation **trên kiểu**, nhưng **không cùng ý nghĩa khái niệm**. `@within` quan tâm phạm vi kiểu **khai báo execution**, còn `@target` quan tâm **class runtime của target object** phía sau Spring proxy.

**Phạm vi bài:** cú pháp, matching, annotation placement, proxy và những bẫy thực tế của **Spring Framework / Spring AOP @AspectJ**. Không xem `@within`/`@target` là AspectJ weaving; Spring AOP vẫn chỉ hỗ trợ join point dạng **method execution của Spring-managed bean thông qua proxy**.

### Khi hai designator có vẻ giống nhau

Nếu một class `PaymentService` được gắn `@TracedType` và tự khai báo method `pay()`, thì một lời gọi `pay()` từ bean khác qua Spring proxy thường đồng thời phù hợp `@within(TracedType)` và `@target(TracedType)`.

Vì thế, **quan sát cả hai advice cùng chạy trong một ví dụ đơn giản là bình thường**, không chứng minh hai biểu thức đồng nghĩa trong mọi trường hợp. Sự phân biệt quan trọng khi xem **nơi khai báo method, kế thừa, kiểu thực thi, interface, proxy kind** và việc annotation có thật sự hiện diện trên target class hay không.

## 2. Cú pháp Spring AOP và điều kiện tiên quyết

Một annotation đánh dấu kiểu:

```java
package com.example.typecut;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedType {
    String value() default "";
}
```

Hai pointcut:

```java
@Aspect
public class TypeSelectionAspect {
    @Before("@within(com.example.typecut.TracedType)")
    public void beforeWithin() {
        // Chạy khi method execution khớp @within.
    }

    @Before("@target(com.example.typecut.TracedType)")
    public void beforeTarget() {
        // Chạy khi method execution khớp @target.
    }
}
```

Đây là **đoạn Java minh họa**; muốn chạy trong Spring cần đưa `TypeSelectionAspect` thành bean, bật AOP support, cung cấp dependencies, có target Spring bean và có caller đi qua proxy. Cần import `org.aspectj.lang.annotation.Aspect` và `Before`.

Các điều kiện Spring-specific:

1. **Annotation có `@Target(ElementType.TYPE)`.** Cả hai designator ở đây đều chọn annotation cấp type, không phải `@Target(ElementType.METHOD)`.
2. **Dùng `@Retention(RetentionPolicy.RUNTIME)` cho ví dụ này.** Nó giúp Spring/AspectJ pointcut xử lý và bind annotation tại runtime; không trông cậy vào annotation chỉ có `SOURCE` hoặc suy rằng một retention policy khác sẽ được Spring AOP hỗ trợ giống nhau.
3. **`@Aspect` class là Spring bean.** Dùng `@Aspect` + `@Component` trong vùng scan hoặc trả về aspect từ phương thức `@Bean`.
4. **Bật cơ chế Spring AOP @AspectJ.** Trong Java-config Spring Framework dùng `@EnableAspectJAutoProxy`; Spring Boot thường tự cấu hình khi có AOP starter phù hợp.
5. **Target cũng phải là Spring bean.** `new PaymentService()` ngoài container sẽ không được interceptor Spring AOP nhận diện.
6. **Call path phải đi qua proxy** và method phải proxy được; `this.method()`, constructor, field access, class/method `final` trong CGLIB có giới hạn riêng.
7. **Có `aspectjweaver` trên classpath** để Spring đọc/khớp biểu thức pointcut. Không cần `-javaagent` cho Spring proxy AOP thông thường.

Điều quan trọng: `@target` trong biểu thức pointcut **không phải** annotation Java `@Target` dùng để khai báo nơi được phép đặt annotation. Một bên là **AspectJ pointcut expression**, một bên là **meta-annotation của Java**.

## 3. Phân biệt ý nghĩa của @within và @target

| Tiêu chí | `@within(TracedType)` | `@target(TracedType)` |
| --- | --- | --- |
| Ý nghĩa Spring AOP | Method execution nằm trong kiểu được đánh dấu; tài liệu Spring nhấn mạnh **kiểu khai báo method** | Class của **target object** đang thực thi mang annotation |
| Yếu tố cần kiểm tra | Kiểu chứa method execution, annotation trên kiểu ấy | Kiểu runtime của đối tượng target đứng sau Spring proxy và annotation của kiểu |
| Đặt `@TracedType` ở đâu trong ví dụ điển hình? | Trên class khai báo method | Trên class của target bean |
| Chọn các method theo annotation của chính method? | **Không**; việc ấy dành cho `@annotation` | **Không**; cũng là annotation cấp type |
| Với method khai báo trong một class mang annotation và target cũng là class đó | Thường match | Thường match |
| Khi method được kế thừa, override, khai báo qua interface | Cần kiểm tra kiểu khai báo và cách Spring resolve method | Cần kiểm tra annotation trên class target thực tế và cách Spring resolve type/proxy |
| Với class không mang annotation | Không match theo kiểu khai báo thông thường | Không match nếu target class thực tế cũng không mang annotation |
| Có thay đổi giới hạn Spring proxy? | Không | Không |

Định nghĩa trên dựa vào tài liệu **Spring Framework Pointcuts**. Không tự chuyển chúng thành câu “`@within` luôn match superclass/subclass” hay “`@target` luôn match mọi method kế thừa”. Với kế thừa và interface, **chính annotation placement, cơ chế Java annotation inheritance và khả năng resolve method của Spring** quyết định kết quả runtime.

### Khác với `@annotation`

```java
@TracedType
public class PaymentService {
    public void pay() { }

    @AuditedOperation
    public void refund() { }
}
```

Giả sử `@AuditedOperation` là annotation `METHOD` tự định nghĩa và class là một Spring bean:

- `@within(TracedType)` và `@target(TracedType)` có mục đích chọn execution theo **kiểu PaymentService**; không yêu cầu method `pay`/`refund` có riêng `@TracedType`.
- `@annotation(AuditedOperation)` chọn theo **annotation trên method**, không tự áp dụng lên `pay()` chỉ vì class có `@TracedType`.
- Nếu cần vừa annotation cấp class vừa giới hạn method cụ thể, kết hợp `execution(...) && @within(...)` hoặc `execution(...) && @target(...)`; nếu cần **hai annotation đồng thời**, dùng thêm `@annotation(...)`.

Đừng dùng `@target` để tìm annotation trên **tham số** hoặc trên **return type**; hai trường hợp ấy có cơ chế/biểu thức khác và không phải nội dung của điểm cắt này.

## 4. Giới hạn kiểu trong pointcut: vì sao không nên dùng biểu thức quá rộng?

Biểu thức ngắn:

```java
@Before("@target(com.example.typecut.TracedType)")
public void trace() {
    // ...
}
```

có thể khiến Spring phải xét **rất nhiều ứng viên bean/method** trước khi xác định annotation của target; nhất là `@target` có yếu tố target runtime. Trong ứng dụng có nhiều bean hạ tầng, pointcut quá rộng có thể làm auto-proxy creator phải làm thêm việc, hoặc gây lỗi proxy ở bean hạ tầng không mong muốn (ví dụ bean final được xét để proxy trong cấu hình class-based).

Nên giới hạn type/package/method rõ ràng:

```java
@Before(
    "execution(public * com.example.typecut..*Service.perform(..))"
    + " && @target(com.example.typecut.TracedType)"
)
public void beforeSpecificServiceMethod() {
    // ...
}
```

- `execution(public * ... )` yêu cầu public method execution.
- `com.example.typecut..*Service` giới hạn vùng package và tên class.
- `perform(..)` giới hạn method có tên cụ thể, bất kỳ số tham số.
- `&& @target(TracedType)` còn yêu cầu class target có annotation.

Nếu muốn cùng logic theo kiểu khai báo method, thay `@target` bằng `@within`. Hai đoạn trên là **mã minh họa**, không phải project chạy độc lập.

Trong project thật, chọn scope **theo package/bean và contract** thay vì chọn tất cả class trong ứng dụng. Khi loại bỏ một method khỏi interception, viết test để xác minh nó thực sự bị loại bỏ.

## 5. Demo Java 17+ chạy được: cùng annotation cấp class, hai counters

Ví dụ Spring Framework thuần dưới đây xác nhận **một class được gắn annotation** kích hoạt cả `@within` và `@target`; một class không gắn annotation không kích hoạt advice theo type annotation trong ví dụ. Hai advice tăng counter nhưng **không in log**, tránh phụ thuộc vào thứ tự chạy giữa các advisors.

Môi trường: **Java 17+**, **Spring Framework 6.2.18**, **AspectJ Weaver 1.9.24**, Gradle có plugin `application`. Các phiên bản dependency này có trên Maven Central. Không dùng Spring Boot trong demo để quan sát rõ `@EnableAspectJAutoProxy`.

### 5.1. Cây thư mục

```text
spring-type-annotation-pointcuts/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/typecut/
    ├── AppConfig.java
    ├── TracedType.java
    ├── MarkedService.java
    ├── PlainService.java
    ├── TypeSelectionAspect.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-type-annotation-pointcuts'
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
    implementation 'org.springframework:spring-context:6.2.18'
    implementation 'org.springframework:spring-aop:6.2.18'
    implementation 'org.aspectj:aspectjweaver:1.9.24'
}

application {
    mainClass = 'com.example.typecut.Main'
}
```

Spring Framework 6.x yêu cầu Java 17+. Ở project Spring Boot thực tế, nên dựa vào dependency management/BOM của Boot, không ghép phiên bản Spring Framework độc lập khi chưa kiểm tra tương thích.

### 5.2. Annotation cấp type

**`TracedType.java`**

```java
package com.example.typecut;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedType {
    String value() default "";
}
```

Annotation giữ thông tin `value` để có thể mở rộng sang binding, nhưng hai advice trong demo đầu tiên chỉ kiểm tra **đúng type annotation** mà chưa dùng giá trị.

### 5.3. Một service có annotation, một service không có

**`MarkedService.java`**

```java
package com.example.typecut;

@TracedType("checkout")
public class MarkedService {
    public String perform(String id) {
        return "marked:" + id;
    }
}
```

**`PlainService.java`**

```java
package com.example.typecut;

public class PlainService {
    public String perform(String id) {
        return "plain:" + id;
    }
}
```

Cả hai class đều không final, cả hai method đều public/non-final; không có self-call. Như vậy khác biệt quan trọng giữa hai loại service là **annotation cấp type**, không phải method visibility hoặc proxy eligibility.

### 5.4. Một aspect có hai advice độc lập

**`TypeSelectionAspect.java`**

```java
package com.example.typecut;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TypeSelectionAspect {
    private final AtomicInteger withinCalls = new AtomicInteger();
    private final AtomicInteger targetCalls = new AtomicInteger();

    @Before(
        "execution(public * com.example.typecut..*Service.perform(..))"
        + " && @within(com.example.typecut.TracedType)"
    )
    public void beforeWithin() {
        withinCalls.incrementAndGet();
    }

    @Before(
        "execution(public * com.example.typecut..*Service.perform(..))"
        + " && @target(com.example.typecut.TracedType)"
    )
    public void beforeTarget() {
        targetCalls.incrementAndGet();
    }

    public int withinCount() {
        return withinCalls.get();
    }

    public int targetCount() {
        return targetCalls.get();
    }
}
```

**Vì sao không in thứ tự advice?** Hai advices đều là `@Before` trên cùng join point; không khóa một thứ tự thực thi nếu không có cấu hình order rõ ràng. Dùng counter rồi in sau khi method hoàn thành cho output kiểm chứng **tất định**, không tự khẳng định `@within` luôn trước `@target` hoặc ngược lại.

`AtomicInteger` bảo đảm increment/get an toàn khi có concurrent calls. Tuy nhiên, counter trong singleton aspect chỉ là demo quan sát interception, không phải hệ thống metrics production hay counter toàn cluster.

### 5.5. Đăng ký beans, bật @AspectJ support

**`AppConfig.java`**

```java
package com.example.typecut;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    TypeSelectionAspect typeSelectionAspect() {
        return new TypeSelectionAspect();
    }

    @Bean
    MarkedService markedService() {
        return new MarkedService();
    }

    @Bean
    PlainService plainService() {
        return new PlainService();
    }
}
```

`TypeSelectionAspect` là **Spring bean nhờ `@Bean`**, không cần `@Component`. Hai service cũng là Spring beans nhờ `@Bean`, không cần `@Service` hoặc `@ComponentScan`.

`@EnableAspectJAutoProxy` cho phép Spring lấy các advice từ `@Aspect` bean để xét target beans. `proxyTargetClass=true` khiến proxy có thể truy cập qua kiểu class trong ví dụ; điều này **không thay đổi ý nghĩa** annotation đặt trên **target class**.

### 5.6. Main kiểm tra hành vi, không dựa vào thứ tự advices

**`Main.java`**

```java
package com.example.typecut;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            MarkedService marked = context.getBean(MarkedService.class);
            PlainService plain = context.getBean(PlainService.class);
            TypeSelectionAspect aspect =
                    context.getBean(TypeSelectionAspect.class);

            System.out.println("markedProxy=" + AopUtils.isAopProxy(marked));
            System.out.println("marked=" + marked.perform("A"));
            System.out.println("plain=" + plain.perform("B"));
            System.out.println("withinCalls=" + aspect.withinCount());
            System.out.println("targetCalls=" + aspect.targetCount());
        }
    }
}
```

Chạy từ root project với Gradle đã cài:

```bash
gradle run --quiet
```

Nếu đã có Gradle Wrapper: `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Stdout ứng dụng mong đợi theo thứ tự** (không tính log môi trường/Gradle):

```text
markedProxy=true
marked=marked:A
plain=plain:B
withinCalls=1
targetCalls=1
```

### 5.7. Đọc kết quả cho đúng

1. `markedProxy=true`: `MarkedService` được Spring trả về dưới dạng Spring AOP proxy vì có advisors có thể áp dụng.
2. `marked=marked:A`: method `MarkedService.perform("A")` thực thi, còn hai advices tăng counter ở proxy.
3. `plain=plain:B`: method cùng tên của `PlainService` chạy nhưng **không có annotation `@TracedType` ở type**, nên không tăng hai counters trong demo.
4. `withinCalls=1`: method thuộc kiểu được đánh dấu, `@within` chọn execution.
5. `targetCalls=1`: target runtime class `MarkedService` có annotation, `@target` chọn execution.

**Không in/khẳng định `plainProxy=false` trong ví dụ.** Tùy việc Spring phân tích pointcut động, các advisor khác và phiên bản cấu hình, một bean không được annotation đánh dấu có thể vẫn được xem xét khi tạo proxy. Điều cần assert là **advice không ghi event cho `PlainService.perform`**, không phải ép toàn bộ ứng dụng phải có cùng một kết quả `AopUtils.isAopProxy(plain)`.

Và lưu ý: demo này **không phân biệt về mặt output** hai designator trên class/method đơn giản. Nó xác nhận cách bật, kiểu annotation và việc pointcut hoạt động trong Spring. Để phân biệt trong codebase thực tế, phải xây ma trận có kế thừa/annotation placement, không thay thế bằng suy đoán.

## 6. Kế thừa class, override method: vì sao cần phân biệt declared type và runtime target?

Xét ba loại tình huống. Chúng là **mô hình phân tích**, không phải những kết quả stdout tất định của demo chính.

### 6.1. Class khai báo method đồng thời có annotation

```java
@TracedType
public class TracedBillingService {
    public void bill() { }
}
```

- `bill()` được khai báo trong `TracedBillingService`.
- Target bean runtime class cũng là `TracedBillingService`.
- Khi gọi qua Spring proxy, cả `@within(TracedType)` và `@target(TracedType)` có điều kiện rõ ràng để match.

Đây là tình huống phù hợp nhất để bắt đầu học và test cả hai.

### 6.2. Method được khai báo trong superclass không có annotation

```java
public class BaseBillingService {
    public void bill() { }
}

@TracedType
public class SpecialBillingService extends BaseBillingService {
    // bill() được kế thừa, không khai báo lại ở đây
}
```

Phân tích về mặt ý nghĩa:

- **Kiểu khai báo method** là `BaseBillingService`, không có `@TracedType`.
- **Target object thực tế** có thể là `SpecialBillingService`, có `@TracedType`.
- Theo sự phân biệt của pointcut designator, đây là ca cần kiểm tra `@within` theo declaring type và `@target` theo runtime target type, thay vì coi chúng có chung nghĩa.

**Giới hạn của khẳng định:** Spring AOP ủy quyền một phần matching cho AspectJ pointcut parser và có logic tìm method tương ứng từ target class/interface/bridge methods. Với inheritance và các proxy types cụ thể, cách Spring resolve method có thể làm kết quả khó suy chỉ từ một đoạn source. Hãy **thử trên đúng Spring version và runtime proxy đang dùng**; không áp đặt bảng output duy nhất chưa được thực nghiệm.

Một biến thể khác: `SpecialBillingService` **override `bill()` và khai báo method ngay trong annotated class**. Khi đó declaring type đã thay đổi, vì vậy kết quả có thể khác inherited method. Khi test, ghi lại rõ method được **inherited** hay **overridden**.

### 6.3. Annotation trên superclass, target là subclass

```java
@TracedType
public class ParentBillingService {
    public void bill() { }
}

public class ChildBillingService extends ParentBillingService {
    // Không tự gắn @TracedType
}
```

Annotation Java `@Inherited` quyết định một phần khả năng class con **kế thừa annotation cấp class** khi tra cứu bằng reflection. Nếu `TracedType` **không** được meta-annotate bằng `@Inherited`, `ChildBillingService.class.isAnnotationPresent(TracedType.class)` không mặc nhiên thành true chỉ vì parent có annotation. Nếu muốn annotation có hành vi kế thừa ở Java class hierarchy, cân nhắc:

```java
import java.lang.annotation.Inherited;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedType {
    String value() default "";
}
```

Đây là đoạn **minh họa annotation variant**; muốn biên dịch thành file độc lập cần thêm các imports Java và package; không thay trực tiếp `TracedType.java` của demo nếu chưa kiểm tra test.

`@Inherited` **không làm annotation trên interface được triển khai trở thành annotation kế thừa trên implementation class**. Các utility annotation lookup của Spring có thể có cơ chế merge/meta-annotation khác reflection Java thông thường, nên không lẫn các lookup semantics ấy với điều kiện pointcut runtime cụ thể.

**Bài học:** khi policy bắt buộc áp cho cả class con, kiểm thử reflection/annotation lookup lẫn **actual advice invocation** trên subclass thực. Đừng đặt policy bảo mật vào một annotation và suy rằng annotation ấy tự lan sang mọi target class.

## 7. Annotation đặt trên interface: bẫy thường gặp trong Spring AOP

Trong thiết kế Spring, một service hay có interface:

```java
@TracedType
public interface PaymentPort {
    String pay(String id);
}

public class PaymentService implements PaymentPort {
    @Override
    public String pay(String id) {
        return "paid:" + id;
    }
}
```

Các đoạn minh họa, không thuộc demo `MarkedService` chạy được phía trên.

Cần phân biệt:

- **`PaymentPort` có annotation**; **`PaymentService` chưa chắc có annotation đó** trong kết quả Java reflection. `@Inherited` không truyền annotation từ interface sang implementing class.
- Với `@target(TracedType)`, tài liệu Spring mô tả annotation **trên class của target object**. Không suy rằng annotation chỉ đặt trên interface luôn đủ để pointcut này match implementation.
- Với `@within(TracedType)`, method declaring type, kiểu interface được proxy và cách Spring resolve implementation method có thể ảnh hưởng kết quả. Cần test trên **JDK proxy** và **CGLIB proxy**, đặc biệt khi method annotation / type annotation xuất hiện ở interface nhưng không xuất hiện trên implementation.
- Kết quả của một Spring feature có hỗ trợ **merged annotations** không phải bằng chứng `@within`/`@target` sẽ áp dụng cùng semantics; chúng là AspectJ expression pointcuts do Spring AOP diễn giải.

**Hướng thực tế dễ kiểm soát:** nếu policy phải dựa vào **annotation cấp class của runtime target**, đặt annotation trực tiếp trên class implementation mục tiêu (hoặc dùng inheritance được kiểm chứng và tài liệu hóa). Nếu policy thuộc **contract của interface**, cân nhắc pointcut theo `execution(* PaymentPort.*(..))` / `this(PaymentPort)` hoặc một marker method annotation phù hợp, rồi **test** với loại proxy thực tế. Không dùng lời giải dựa trên phép thừa kế annotation interface mà Java không bảo đảm.

## 8. Khi nào dùng @within, khi nào dùng @target?

Câu trả lời không phải “một cái luôn tốt hơn cái kia”. Chọn theo **nơi lưu ý nghĩa của annotation** và những ràng buộc cần bảo đảm:

**Dùng `@within` khi:**

- Bạn chủ ý chọn **code thuộc kiểu được đánh dấu** và muốn diễn tả phạm vi type khai báo method.
- Method được khai báo trực tiếp trong những service class có annotation là trường hợp chính.
- Bạn đã kiểm thử behavior với inherited/overridden/interface methods có liên quan, thay vì mở rộng nghĩa quá mức.
- Bạn muốn biểu thức rõ “method executions bên trong annotated type”, không chỉ dựa vào class runtime.

**Dùng `@target` khi:**

- Bạn muốn ràng buộc “object target mà Spring proxy chuyển tới có class mang annotation này”.
- Runtime implementation class là nơi **đặt marker type annotation**.
- Bạn cần binding/đọc metadata của annotation trên target trong advice và có test xác nhận.
- Bạn kết hợp với một `execution` cụ thể để Spring không phải xét quá nhiều loại bean và method.

**Dùng `@annotation` thay thế khi:**

- Chỉ một số method trên một class cần interception.
- Annotation policy là thuộc tính của **operation**, không phải cả target service.
- Cần phân biệt method này được audit, method khác trong cùng class không audit.

Nếu mục tiêu chỉ là **một Spring bean có tên** (ví dụ `paymentService`) thì pointcut extension `bean(paymentService)` có thể chính xác hơn type annotation. `bean` là extension **riêng của Spring AOP**; nó không phải pointcut designator chung của AspectJ weaving.

## 9. Kết hợp @within/@target với execution, bean và các biểu thức khác

### 9.1. Chặn public operation trong package nghiệp vụ

```java
@Pointcut("execution(public * com.example.billing..*Service.*(..))")
public void billingOperations() { }

@Pointcut("@target(com.example.typecut.TracedType)")
public void tracedTarget() { }

@Before("billingOperations() && tracedTarget()")
public void beforeBillingOperation() {
    // ...
}
```

Đây là **đoạn minh họa bên trong cùng một class `@Aspect`**, cần import `Pointcut`, `Before`. Chia pointcut thành tên nhỏ giúp đọc và tái sử dụng; Spring vẫn cần aspect bean và target Spring-managed.

### 9.2. Chặn theo kiểu khai báo và tên method

```java
@Before(
    "execution(public * com.example.typecut..*Service.perform(..))"
    + " && @within(com.example.typecut.TracedType)"
)
public void beforePerform() {
    // ...
}
```

Điều này chọn phạm vi kiểu và operation. Nếu class có nhiều method, chỉ `perform` thuộc biểu thức `execution` mới được xét.

### 9.3. Dùng OR khi thật sự chấp nhận một trong hai điều kiện

```java
@Before(
    "execution(public * com.example.typecut..*Service.perform(..))"
    + " && ("
    + "@within(com.example.typecut.TracedType)"
    + " || @target(com.example.typecut.TracedType)"
    + ")"
)
public void beforeAnyMarkedCase() {
    // ...
}
```

`||` không làm Spring “chạy advice hai lần nếu cả hai cùng match”; **một advice method** gắn trên biểu thức OR sẽ chạy **một lần cho mỗi join point invocation** mà expression match, trừ khi ứng dụng còn có các advisors/advice khác hoặc invocation lặp lại. Nhưng biểu thức OR làm phạm vi match rộng hơn; với policy quan trọng cần test trường hợp inheritance/interface để biết chính xác vì sao target được chọn.

**Lưu ý phân biệt:** nếu viết **hai advice methods** riêng `@within` và `@target` như demo, một method invocation có thể gọi **cả hai advice methods**, nên counter mỗi bên tăng một. Đây không phải biểu thức OR “tự chạy hai lần”.

### 9.4. Loại trừ package hoặc bean không mong muốn

```java
@Before(
    "execution(public * com.example.typecut..*Service.perform(..))"
    + " && @target(com.example.typecut.TracedType)"
    + " && !bean(internal*Service)"
)
public void beforePublicFacingTarget() {
    // ...
}
```

`bean(...)` là **Spring-specific** bean-name PCD. Pattern `internal*Service` chỉ là ví dụ; nếu bean name thực tế khác, rule exclusion không hoạt động. Không dùng biểu thức exclude theo tên để thay thế architecture/security boundary chắc chắn.

## 10. Binding annotation instance vào advice

Spring AOP `@AspectJ` hỗ trợ bind annotation của `@target`/`@within` theo tham số advice. Dùng khi cần đọc metadata như `value` của `@TracedType` thay vì chỉ hỏi annotation tồn tại hay không.

Ví dụ **minh họa riêng**, không thêm vào demo chính vì sẽ thay đổi bộ đếm và stdout:

```java
@Aspect
public class MetadataAspect {
    @Before(
        value = "execution(public * com.example.typecut..*Service.perform(..))"
              + " && @target(marker)",
        argNames = "marker"
    )
    public void beforeTarget(TracedType marker) {
        System.out.println("typeTag=" + marker.value());
    }
}
```

Nếu target class có `@TracedType("checkout")` và method join point phù hợp, Spring có thể bind annotation instance vào `marker` để in:

```text
typeTag=checkout
```

Điều kiện để code chạy thật: class aspect phải là Spring bean, `TracedType` phải có `RUNTIME` retention, các pointcut bindings phải tương thích, method target phải qua proxy. `argNames = "marker"` làm tham số binding tường minh, tránh dựa vào thông tin tên tham số Java có thể thiếu khi build không dùng `-parameters`.

Tương tự có thể viết `@within(marker)` khi muốn bind annotation của kiểu liên quan đến `@within`, nhưng hãy xem annotation đó có thực sự ở declaring type mà pointcut chọn không. Binding không biến annotation method thành annotation cấp class.

**Không nhầm `@target(marker)` với `target(targetObject)`:**

- `@target(marker)` bind **annotation object**.
- `target(targetObject)` bind **Spring target instance** tương ứng type parameter, nếu biểu thức và kiểu hợp lệ.

Nếu chỉ cần annotation tồn tại chứ không dùng metadata, viết `@target(com.example.typecut.TracedType)` sẽ dễ kiểm tra và không cần binding.

## 11. Một ma trận test nhỏ nhưng đủ bao phủ

Khi áp dụng pointcut annotation kiểu trong Spring, nên chuẩn bị integration tests theo **các đường match** thay vì suy đoán từ tên designator.

| Case | Nơi gắn type annotation | Method entry | Cần kiểm chứng |
| --- | --- | --- | --- |
| A | Trực tiếp trên class target | Public method khai báo tại chính class | Cả `@within` và `@target` có đúng số events dự kiến? |
| B | Không có annotation trên class | Public method cùng tên | Không có advice ngoài ý muốn? |
| C | Annotation ở subclass, method kế thừa từ base | Method không override | Type declaring và runtime target có tạo khác biệt? |
| D | Annotation ở subclass, method được override tại subclass | Override public method | Kết quả có khác case C không? |
| E | Annotation trên superclass có/không `@Inherited` | Method target thuộc subclass | Runtime annotation semantics có đúng ý định? |
| F | Annotation chỉ trên interface | Public method implementation | JDK/CGLIB và method resolution có khác nhau không? |
| G | Annotation trực tiếp trên implementation | Interface method gọi qua JDK proxy | `@target` có bắt được target class đúng không? |
| H | Annotation đúng, nhưng target được tạo bằng `new` | Gọi ngoài container | Không có Spring AOP advice? |
| I | Annotation đúng, self-invocation trên target | `this.perform()` | Advice trên `perform` có bị bypass? |
| J | Annotation đúng, CGLIB method `final` hoặc class `final` | Method không override được | Proxy có bị giới hạn/fail startup như dự kiến? |

Không cần biến mỗi case thành một README mới. Hãy tạo **test context tối giản** đúng proxy mode và chỉ assert **số advice invocations**/side effect. Với cấu hình có nhiều advisors, đếm theo từng advice thay vì kết luận proxy tồn tại nghĩa là tất cả advices match.

### Ví dụ kiểm thử trên context demo

```java
// Đoạn minh họa bên trong một Spring integration test hoặc main
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    MarkedService marked = context.getBean(MarkedService.class);
    PlainService plain = context.getBean(PlainService.class);
    TypeSelectionAspect aspect = context.getBean(TypeSelectionAspect.class);

    marked.perform("T1");
    marked.perform("T2");
    plain.perform("T3");

    if (aspect.withinCount() != 2 || aspect.targetCount() != 2) {
        throw new AssertionError("Unexpected type-annotation pointcut count");
    }
}
```

Đây là một biến thể **đứng riêng** chạy được trong package demo với import `AnnotationConfigApplicationContext`. Không chạy nó chung trong cùng `Main` nếu vẫn muốn stdout tại mục 5.7, vì nó là một context khác. Logic khẳng định hai method calls tới annotated class được tính, một call tới plain class không được tính.

Với `@Before`, counter đo **điểm advice trước khi target chạy**, không phải số business transaction đã commit. Nếu method target ném exception sau `@Before`, counter vẫn đã tăng; nếu muốn đo “thành công”, chọn thời điểm advice và điều kiện nghiệp vụ tương ứng.

## 12. Những bẫy phổ biến và cách xử lý

### 12.1. Gắn annotation lên method nhưng viết @within hoặc @target

```java
public class ExampleService {
    @TracedOperation
    public void execute() { }
}
```

Nếu `@TracedOperation` chỉ có `@Target(ElementType.METHOD)`, `@within(TracedOperation)`/`@target(TracedOperation)` không phải biểu thức tương ứng với annotation ấy. Nếu annotation là **method policy**, chọn `@annotation(com.example.TracedOperation)` và bảo đảm target method được proxy.

### 12.2. Class mang annotation nhưng không phải Spring bean

```java
@TracedType
class TracedService {
    public void perform() { }
}
```

Một class được đánh dấu `@TracedType` không tự đăng ký bean trong Spring. Đăng ký bằng `@Component`/`@Service` và component scan, hoặc `@Bean` trên `@Configuration`. Phía aspect cũng cần Spring-managed bean.

### 12.3. Coi @within và @target luôn giống nhau trong mọi phép kế thừa

Hai biểu thức có thể cùng match trên ví dụ cơ bản, nhưng ý nghĩa **declaring type** và **runtime target type** khác nhau. Khi có inherited/overridden/interface methods, hãy test Spring version/proxy của project. Không tự tạo một bảng output tuyệt đối rồi xem đó là contract.

### 12.4. Nhầm Java @Inherited với interface inheritance

`@Inherited` hoạt động theo **class inheritance**, không theo interface implements. Interface mang annotation không tự biến implementation thành runtime annotated target class; những cơ chế annotation search riêng của framework còn cần phân biệt rõ.

### 12.5. Điểm cắt quá rộng

`@target(TracedType)` trần có thể làm AOP infrastructure xét quá nhiều ứng viên. Ưu tiên `execution(...) && @target(...)` hoặc một pointcut kết hợp vùng package. Kiểm tra các bean internal/security/framework có vô tình bị áp advisor hay không.

### 12.6. Đổi proxy mode để “sửa” một vấn đề annotation mà chưa test

JDK interface proxy và CGLIB class proxy khác nhau về method exposure và điều kiện class/method. Nếu annotation nằm trên interface, việc đổi loại proxy có thể làm matching thay đổi, nhưng không phải cam kết Spring tự sao chép annotation lên implementation. Dùng test xác nhận.

### 12.7. Test đối tượng tạo bằng new

`new MarkedService().perform("A")` không đi qua Spring AOP proxy; annotation class vẫn tồn tại nhưng advice không chạy. Test AOP phải lấy bean từ `ApplicationContext`.

### 12.8. Self-invocation

`markedService.outer()` gọi `this.perform()` trên target thì `perform` không tự đi lại qua proxy; dù class có annotation, advice riêng trên `perform` vẫn có thể bị bypass. Đổi `@within` sang `@target` không sửa được self-invocation.

### 12.9. Annotation policy bị đặt trên class final hoặc method final

Với CGLIB proxy, Spring không subclass được class `final` và không override được method `final`. Annotation đúng vẫn không thể biến bytecode không override được thành Spring proxy interception. Nếu có business interface và không ép CGLIB, JDK proxy có thể là lựa chọn phù hợp; method phải expose qua interface proxy.

### 12.10. Suy từ isAopProxy rằng annotation pointcut đã chạy

`AopUtils.isAopProxy(bean)` chỉ chứng minh có một Spring AOP proxy. Một bean có thể có proxy vì advisor khác, một method có thể không match pointcut đang test, và một self-call có thể bypass. Luôn assert advice event/counter của chính `@within` hoặc `@target`.

## 13. Quy trình debug khi @within/@target không chạy như dự kiến

1. **Xác nhận annotation placement.** `TracedType` có `@Target(TYPE)` và `@Retention(RUNTIME)` không? Annotation nằm trên implementation class, superclass hay interface?
2. **Xác nhận Spring context.** Aspect có thực sự là bean? Target là bean? Có đúng ApplicationContext và AOP auto-configuration không?
3. **Nhìn đúng pointcut.** `@within` xét kiểu khai báo method, `@target` xét runtime class target; thêm `execution` để tránh match ngoài phạm vi.
4. **Kiểm tra proxy.** `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy`, `isCglibProxy`; method entry có thuộc interface hay override được không?
5. **Gọi method qua proxy.** Lấy Spring bean từ context, inject vào một bean khác; không dùng `new` và không dựa vào `this.method()`.
6. **Đo riêng từng advice.** Tạo counters/test recorder cho `@within` và `@target`; không chỉ quan sát return value của method.
7. **Xét inheritance/bridge methods nếu cần.** Ghi rõ method có được override, annotation có `@Inherited`, class runtime nào đang làm target. Đừng dùng `proxy.getClass().isAnnotationPresent(...)` để thay cho việc kiểm tra annotation trên target class.
8. **Tạo integration test theo proxy mode thực tế.** Kiểm thử cùng Spring/Boot version và JDK, đặc biệt với interface annotations, generic bridge methods hoặc Kotlin final classes.
9. **Rà soát tác động.** Advice dùng cho audit/security/transaction cần test hành vi nghiệp vụ thực sự, không suy từ một dòng log rằng policy đã được bảo vệ toàn diện.

### Xem target class thay vì generated proxy class

```java
Object bean = context.getBean("markedService");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Đây là **đoạn minh họa**, cần import `org.springframework.aop.support.AopUtils`, và context có bean tên `markedService`. Ở demo, `getTargetClass` xác định class nghiệp vụ `MarkedService`, tránh nhầm generated proxy subclass hoặc JDK proxy runtime class với nơi đặt type annotation.

Khi muốn biết annotation có thật trên target type theo Java reflection:

```java
Class<?> targetType = AopUtils.getTargetClass(bean);
boolean directlyOrInherited =
        targetType.isAnnotationPresent(TracedType.class);
```

Đoạn code là công cụ chẩn đoán annotation `RUNTIME` theo Java reflection (và `@Inherited` nếu có); nó **không thay thế** Spring/AspectJ pointcut matching engine hoặc phép đo advice thực tế.

## 14. Checklist thực hành

- [ ] Annotation đánh dấu **kiểu**, có `@Target(TYPE)` và `@Retention(RUNTIME)`?
- [ ] Phân biệt rõ **kiểu khai báo method** của `@within` và **class target runtime** của `@target`?
- [ ] Không nhầm `@within`/`@target` với `@annotation` trên method hoặc meta-annotation Java `@Target`?
- [ ] Aspect đã là Spring bean; target cũng là Spring bean; @AspectJ support được bật?
- [ ] Pointcut được giới hạn bằng `execution(...)` phù hợp, không vô tình áp lên quá nhiều bean?
- [ ] Gọi từ bean khác/context qua **Spring proxy**, không từ `new` hoặc `this.method()`?
- [ ] Đã xác nhận loại JDK/CGLIB proxy và khả năng intercept public/interface/non-final method?
- [ ] Nếu annotation nằm trên superclass/interface, đã test inheritance/override/Java `@Inherited` thật thay vì giả định?
- [ ] Nếu cần giá trị của annotation, đã dùng binding `@target(marker)` hoặc `@within(marker)` với tên tham số đúng?
- [ ] Integration tests kiểm tra riêng số lần `@within` và `@target` advice chạy cho class có/không marker?
- [ ] Không suy `isAopProxy=true` tương đương “mọi method đều được advice”?
- [ ] Policy liên quan bảo mật/giao dịch có test hiệu ứng thực tế, kể cả đường gọi bypass?

**Ghi nhớ:** Trong Spring AOP, `@within` nhấn mạnh **method execution nằm trong kiểu được annotate**; `@target` nhấn mạnh **annotation trên class target object**. Với class đơn giản cả hai thường cùng match, nhưng sự tương đồng đó không loại bỏ khác biệt về kiểu khai báo/runtime khi có kế thừa, proxy và annotation placement. Luôn giới hạn pointcut và kiểm chứng bằng Spring context cùng lời gọi thực sự qua proxy.

## Tài liệu tham khảo

- [Spring Framework 6.2 — Declaring a Pointcut: @within, @target, @annotation, supported PCDs](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 — Declaring Advice: binding annotation vào tham số advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Maven Central — spring-context 6.2.18](https://central.sonatype.com/artifact/org.springframework/spring-context/6.2.18)
- [Maven Central — aspectjweaver 1.9.24](https://central.sonatype.com/artifact/org.aspectj/aspectjweaver/1.9.24)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-007">@args theo annotation của kiểu đối số</a>

<details>
<summary>Xem chi tiết</summary>

# @args trong Spring AOP: lọc lời gọi theo annotation trên kiểu đối số runtime

## 1. Ý nghĩa thực tế của @args trong Spring

Một Spring service có thể nhận tham số khai báo rất rộng như `Object`, một interface, hoặc lớp cha. Khi ứng dụng gọi service, đối số thực tế có thể là nhiều lớp khác nhau. Có lúc ta muốn advice chỉ chạy nếu **lớp thực tế của đối số được đánh dấu bằng annotation**, thay vì chọn theo kiểu khai báo trong method signature.

Trong Spring AOP, pointcut designator `@args` giải quyết chính xác yêu cầu này:

> `@args` giới hạn những method-execution join point mà **kiểu runtime của các đối số thực tế được truyền vào** mang annotation tương ứng.

Ví dụ:

```java
@Before("execution(* com.example.app.service..*.*(..))" +
        " && @args(com.example.app.aop.Reviewed)")
public void beforeReviewedArgument() {
    // Áp dụng cho method nhận đúng một đối số,
    // khi lớp runtime của đối số mang @Reviewed.
}
```

Đây là snippet minh họa, giả sử annotation `Reviewed` có tồn tại và aspect đã được đăng ký là Spring bean. Biểu thức `@args(Reviewed)` một vị trí yêu cầu **một đối số**, có kiểu runtime mang `@Reviewed`. Phần `execution(...)` đồng thời giới hạn các Spring service method cần xét.

**Trọng tâm:** `@args` không kiểm tra annotation trên **tham số method** (`@Target(PARAMETER)`), cũng không kiểm tra annotation trên **chính method** (`@annotation`). Nó dựa vào annotation trên **kiểu runtime của giá trị đối số**. Vì vậy, dù method khai báo `submit(Object value)`, lần gọi truyền `new ReviewedOrder()` có thể khớp, trong khi `new PlainOrder()` thì không.

Bài học chỉ tập trung vào `@args` trong **Spring Framework / Spring AOP dựa trên proxy**, không mở rộng sang những join point của AspectJ weaving ngoài cơ chế Spring.

### Kết quả mong đợi sau bài học

Bạn có thể:
1. Viết annotation dùng với `@args` có `@Target(TYPE)` và `@Retention(RUNTIME)`.
2. Phân biệt kiểu tham số khai báo với lớp runtime của đối số khi Spring chọn advice.
3. Viết `@args` cho method một đối số, hiểu yêu cầu số lượng/vị trí và cách kết hợp với `execution`.
4. Phân biệt `@args` với `args`, `@annotation`, `@within`, `@target` và annotation trực tiếp trên parameter.
5. Kiểm chứng bằng Java 17 + Spring Framework ví dụ hoàn chỉnh gồm đối số có/không có annotation.
6. Nhận biết các hạn chế thực tế: `null`, proxy làm đối số, subtype, retention, self-invocation và chi phí matching runtime.

## 2. Trước tiên: annotation nằm ở đâu?

Giả sử có annotation dùng để đánh dấu **lớp dữ liệu nhạy cảm**:

```java
package com.example.app.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Reviewed {
    String value() default "review";
}
```

Và hai lớp payload:

```java
@Reviewed("sensitive")
public class ReviewedOrder {
}

public class PlainOrder {
}
```

Trong Spring service:

```java
public void submit(Object payload) {
    // Xử lý payload.
}
```

Hai lời gọi có cùng method signature nhưng khác **lớp runtime**:

```java
service.submit(new ReviewedOrder()); // @args(Reviewed) có thể khớp.
service.submit(new PlainOrder());    // @args(Reviewed) không khớp.
```

Các snippet trên không phải project độc lập; project biên dịch được ở phần 7.

Vì sao `@Target(TYPE)` quan trọng? Annotation phải gắn với **type** của đối số để `@args` có thể tìm thấy nó. `@Retention(RUNTIME)` giúp annotation còn khả dụng khi chương trình đang chạy. Một annotation chỉ tồn tại ở mức `SOURCE` hoặc `CLASS` không cung cấp thông tin phản chiếu runtime tương ứng mà Spring AOP cần cho ví dụ này.

**Một điều dễ nhầm:** Annotate lên field chứa object, biến cục bộ chứa object, hay tham số trong định nghĩa method không có nghĩa lớp runtime của object đã được annotate.

## 3. Kiểu khai báo và kiểu runtime: khác nhau quyết định kết quả

Xem method:

```java
public void publish(Object event) {
    System.out.println(event);
}
```

Kiểu khai báo của parameter `event` là `Object`, nhưng tại runtime:

```java
Object first = new ReviewedOrder();
Object second = new PlainOrder();

service.publish(first);
service.publish(second);
```

Đối số thứ nhất vẫn có lớp runtime là `ReviewedOrder`; đối số thứ hai là `PlainOrder`. `@args(Reviewed)` sẽ phân biệt được hai lời gọi dù bytecode signature của method giống hệt nhau.

Ta có thể mô tả:

```text
Caller -> Spring AOP proxy -> publish(Object event)
                         |
                         +-- actual argument: ReviewedOrder instance
                         |       |
                         |       +-- runtime class has @Reviewed?
                         |               YES -> advice can run
                         |
                         +-- actual argument: PlainOrder instance
                                 |
                                 +-- runtime class has @Reviewed?
                                         NO -> advice skipped
```

Trong Spring, cả hai lời gọi cần qua **proxy của service** trước đã. Nếu client tạo target bằng `new` hoặc service tự gọi `this.publish(...)` từ method khác, advice có thể không chạy bất kể annotation của đối số.

### Bảng dự đoán đơn giản

Giả sử method nhận **một** đối số, Spring AOP đã bật, `@Before("@args(com.example.app.aop.Reviewed)")` được áp dụng qua proxy và không có ràng buộc pointcut khác:

| Đối số runtime | Đối số có lớp runtime mang @Reviewed? | Advice tương ứng |
| --- | --- | --- |
| `new ReviewedOrder()`, lớp được đánh dấu `@Reviewed` | Có | Có thể chạy |
| `new PlainOrder()`, lớp không đánh dấu | Không | Không chạy |
| Biến kiểu `Object` chứa `new ReviewedOrder()` | Có | Có thể chạy, vì xét giá trị runtime |
| Biến kiểu interface chứa instance annotated implementation | Phụ thuộc annotation của runtime class | Dựa vào runtime class, không chỉ interface |
| `null` | Không có runtime class để đọc annotation | Không dùng để suy ra được kiểu annotated; kiểm tra runtime và thiết kế rõ chính sách null |

Ở dòng cuối, tránh viết logic nghiệp vụ dựa trên giả định rằng `null` “mang annotation” của kiểu khai báo. Với pointcut ràng buộc kiểu runtime, không có object để lấy annotation khi đối số là `null`. Nếu hành vi trên null quan trọng, xử lý null bằng validation trong method/advice riêng và kiểm thử trên phiên bản Spring/AspectJ thực tế.

## 4. @args so với những pointcut dễ nhầm

### 4.1. @args và args không giống nhau

Cả hai đều nhìn vào actual arguments, nhưng kiểm tra khác nhau:

```java
@Pointcut("args(com.example.app.model.ReviewedOrder)")
public void hasReviewedOrderRuntimeType() {
}
```

`args(TypePattern)` xem **kiểu runtime của đối số**, ví dụ một object có kiểu `ReviewedOrder` hoặc subtype phù hợp.

```java
@Pointcut("@args(com.example.app.aop.Reviewed)")
public void hasReviewedAnnotationOnRuntimeType() {
}
```

`@args(AnnotationTypePattern)` xem **annotation trên kiểu runtime của đối số**, không đòi đúng một class cụ thể. Nếu cả `ReviewedOrder` và `FlaggedDocument` đều mang `@Reviewed`, `@args(Reviewed)` có thể khớp cả hai mà không cần viết hai type patterns.

| Thiết kế | Chọn theo | Ví dụ |
| --- | --- | --- |
| `args(com.example.app.model.ReviewedOrder)` | Class/interface runtime của actual argument | Object thuộc `ReviewedOrder`/subtype phù hợp |
| `@args(com.example.app.aop.Reviewed)` | Annotation trên runtime class của actual argument | Bất kỳ runtime class mang `@Reviewed` |
| `execution(* *.publish(com.example.app.model.ReviewedOrder))` | Chữ ký method khai báo kiểu parameter | Method declaration nhận `ReviewedOrder`; không tương đương xét object runtime |
| `execution(* *.publish(Object))` | Chữ ký method khai báo `Object` | Cùng một method có thể nhận cả annotated/unannotated objects |

Ví dụ `execution(* *.publish(Object))` tự nó không biết actual payload class được annotate hay không. Vì thế `@args` có thể bổ sung khả năng lọc dynamic cho một method signature chung.

### 4.2. @args và @annotation

`@annotation` kiểm tra annotation trên **method đang được thực thi**:

```java
@Before("@annotation(com.example.app.aop.AuditedOperation)")
public void beforeAnnotatedMethod() {
}
```

Đây là snippet, giả sử `AuditedOperation` là annotation dành cho `ElementType.METHOD`. Method được annotate có thể khớp bất kể đối số là `ReviewedOrder` hay `PlainOrder`.

Ngược lại:

```java
@Before("@args(com.example.app.aop.Reviewed)")
public void beforeAnnotatedArgumentType() {
}
```

chọn theo runtime annotation của đối số, không yêu cầu method có `@AuditedOperation`. Hai điều kiện có thể kết hợp nếu cần.

### 4.3. @args và @within / @target

- `@within(TypeAnnotation)`: chọn những method executions được khai báo trong các type có annotation đó, tùy cách Spring xét type/declaring type.
- `@target(TypeAnnotation)`: nhìn annotation trên **runtime target object type** phía sau Spring proxy.
- `@args(TypeAnnotation)`: nhìn annotation trên **runtime type của actual arguments** truyền vào method, không phải target.

Giả sử Spring target `OrderService` không mang `@Reviewed`, nhưng input `ReviewedOrder` mang annotation: `@args(Reviewed)` có thể khớp; `@target(Reviewed)` không khớp bởi target service không được đánh dấu. Đổi vị trí annotation từ payload class sang service class sẽ làm thay đổi semantics.

### 4.4. Annotation trên tham số method không phải @args

Ví dụ:

```java
public void publish(@NotNull Object event) {
}
```

`@NotNull` được gắn vào **parameter declaration**, không gắn lên `event.getClass()`. Vì vậy, nó **không tạo điều kiện** để `@args(NotNull)` khớp như annotation trên runtime argument type. Bản thân `@NotNull` ở ví dụ cũng là khái niệm validation khác, không phải lý do Spring AOP tự áp dụng một aspect.

Nếu mục tiêu là kiểm tra annotation trực tiếp trên parameter của Java method, cần thiết kế giải pháp phù hợp (ví dụ validation hay reflection trong advisor riêng), không dùng `@args` để kỳ vọng nó kiểm tra `Method.getParameterAnnotations()`.

## 5. Số lượng và vị trí của đối số trong @args

### 5.1. Một vị trí: method có một argument

```java
@Pointcut("@args(com.example.app.aop.Reviewed)")
public void oneAnnotatedArgumentType() {
}
```

Đây là trường hợp tài liệu Spring mô tả tường minh: method execution có **một** argument, với runtime type mang `@Reviewed`. Nó không có nghĩa “method có bao nhiêu đối số cũng được, miễn có một đối số annotated”.

### 5.2. Nhiều vị trí và dấu đại diện

AspectJ-style annotation pattern hỗ trợ tổ hợp vị trí. Một ví dụ định hướng:

```java
@Pointcut("@args(*, com.example.app.aop.Reviewed)")
public void secondArgumentAnnotated() {
}
```

Mục tiêu của biểu thức là chọn method có **hai** đối số, trong đó runtime type của đối số **thứ hai** mang `@Reviewed` và vị trí đầu được dùng wildcard. Ví dụ này là snippet minh họa cho cú pháp vị trí, không phải một phần của project chạy ở mục 7.

Trong những hệ thống có nhiều overload, varargs, nested collections hoặc mixed annotations, hãy **xác minh cú pháp pattern cụ thể và test trên Spring/AspectJ version đang dùng**; đừng dùng ý tưởng “bất kỳ một argument nào annotated là được” như thể mọi biến thể `@args` đều có semantics đó.

### 5.3. Trường hợp List<T>, mảng, varargs

Nếu method nhận `List<Object>` chứa một `ReviewedOrder`, actual argument duy nhất ở runtime là một **List implementation**, không phải từng phần tử bên trong. `@args(Reviewed)` sẽ xét runtime type của chính List object; nó **không tự duyệt** từng item tìm annotation.

Tương tự, khi method nhận một mảng `ReviewedOrder[]`, actual argument runtime là **array class**, không tự coi nó tương đương annotation trên kiểu thành phần của mảng. Varargs trong Java có thể được biên dịch thành array parameter; do đó cần phân biệt call-site syntax và actual arguments mà Spring AOP nhìn thấy.

Nếu yêu cầu là “bất kỳ item nào trong collection được annotate”, viết business validation/advice logic duyệt collection hoặc dùng cơ chế validation phù hợp. Không kỳ vọng `@args` tự làm việc đó.

## 6. Ràng buộc @args vào phạm vi Spring service

`@args` có tính chất động: annotation của runtime argument có thể thay đổi giữa những lời gọi đến cùng một method.

Nên giới hạn trước bằng điều kiện **ổn định**:

```java
@Aspect
public class ReviewedPayloadAspect {

    @Pointcut("within(com.example.app.service..*)")
    public void serviceLayer() {
    }

    @Pointcut("execution(public * *(..))")
    public void publicMethods() {
    }

    @Pointcut("@args(com.example.app.aop.Reviewed)")
    public void reviewedArgumentType() {
    }

    @Before("serviceLayer() && publicMethods() && reviewedArgumentType()")
    public void beforeReviewed() {
        // Chỉ chạy cho lời gọi phù hợp cả layer, chữ ký và runtime arg.
    }
}
```

Đoạn trên là snippet phụ thuộc package/annotation của ứng dụng, không phải một program standalone. Nó diễn giải ba lớp điều kiện:

1. `within(service..*)`: target method execution thuộc service layer.
2. `execution(public * *(..))`: method public.
3. `@args(Reviewed)`: actual arguments tương ứng có runtime annotation phù hợp.

**Tránh dùng `@args` như một quy tắc ủy quyền bảo mật duy nhất.** Nếu kiểu đối số được tạo hoặc điều khiển từ bên ngoài, runtime annotation là thông tin metadata, không phải bằng chứng caller được phép thực hiện nghiệp vụ. Validation, authentication và authorization phải có ranh giới tin cậy phù hợp. Spring AOP proxy cũng có các hạn chế về self-invocation, final/private methods.

## 7. Ví dụ chạy được: một Object parameter, hai runtime payload types

Ví dụ này dùng **Java 17, Spring Framework 6.1.14 và Gradle**, không cần Spring Boot, web server hoặc database. Ta cố tình cho method khai báo `submit(Object payload)` để chứng minh `@args` nhìn vào **class của actual payload** thay vì kiểu khai báo `Object`.

Các điều kiện được kiểm soát:
- Chỉ một `AnnotationConfigApplicationContext`.
- Aspect được đăng ký bằng `@Bean`.
- `@EnableAspectJAutoProxy(proxyTargetClass = true)` tạo class-based proxy cho `OrderProcessor`.
- Có một payload class mang `@Reviewed` và một class không có.
- Mọi lời gọi đến `submit(...)` trong `main` đều qua Spring-managed service proxy.

### 7.1. Cấu trúc project

```text
spring-aop-at-args/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── DemoApplication.java
    ├── AppConfig.java
    ├── annotations/Reviewed.java
    ├── model/FlaggedPayload.java
    ├── model/PlainPayload.java
    ├── service/OrderProcessor.java
    └── aop/ArgumentAspect.java
```

### 7.2. Build Gradle

Tệp `settings.gradle`:

```groovy
rootProject.name = 'spring-aop-at-args'
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

`spring-context` cung cấp `AnnotationConfigApplicationContext` và `@Configuration`/`@Bean`; `spring-aop` chứa hạ tầng Spring AOP proxy; `aspectjweaver` giúp Spring phân tích pointcut expression của `@Aspect`. Bài này dùng bản Spring cố định để tránh phụ thuộc vào các cấu hình AOP mặc định có thể thay đổi theo Spring Boot.

### 7.3. Annotation nằm trên type và còn tồn tại ở runtime

Tệp `src/main/java/demo/annotations/Reviewed.java`:

```java
package demo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Reviewed {
    String value() default "review";
}
```

- `@Target(ElementType.TYPE)`: annotation được gắn vào class/type declaration.
- `@Retention(RetentionPolicy.RUNTIME)`: annotation có thể được kiểm tra tại runtime.
- `value` là metadata minh họa; pointcut chỉ cần annotation hiện diện. Nếu muốn đọc giá trị annotation trong advice, xem phần binding ở mục 8.

### 7.4. Hai kiểu payload đối lập

Tệp `src/main/java/demo/model/FlaggedPayload.java`:

```java
package demo.model;

import demo.annotations.Reviewed;

@Reviewed("sensitive")
public final class FlaggedPayload {
}
```

Tệp `src/main/java/demo/model/PlainPayload.java`:

```java
package demo.model;

public final class PlainPayload {
}
```

Hai class đều `final` vẫn dùng được **làm đối số** cho service. Spring chỉ cần proxy **service target**, chứ không cần tạo CGLIB proxy cho `FlaggedPayload` hoặc `PlainPayload`. Đừng nhầm `final` của *argument object* với `final` của *Spring bean cần proxy*.

### 7.5. Spring service nhận Object

Tệp `src/main/java/demo/service/OrderProcessor.java`:

```java
package demo.service;

public class OrderProcessor {
    private int targetCalls;

    public void submit(Object payload) {
        targetCalls++;
        String type = payload.getClass().getSimpleName();
        System.out.println("target:" + type);
    }

    public int targetCallCount() {
        return targetCalls;
    }
}
```

`submit` khai báo parameter là `Object`. Method này không bị annotation `@Reviewed` đánh dấu, nhưng actual payload class có thể mang annotation đó. Biến `targetCalls` đếm các lần method target chạy; mọi lời gọi trong demo đều tuần tự nên dùng `int` để output đơn giản.

Để tập trung vào `@args`, chương trình không truyền `null` vào `submit`. Nếu đưa `null` vào thì code `payload.getClass()` phải được sửa xử lý null trước.

### 7.6. Aspect lọc theo runtime annotation

Tệp `src/main/java/demo/aop/ArgumentAspect.java`:

```java
package demo.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ArgumentAspect {
    private int adviceCalls;

    @Before(
        "execution(public void demo.service.OrderProcessor.submit(..))" +
        " && @args(demo.annotations.Reviewed)"
    )
    public void beforeReviewed(JoinPoint joinPoint) {
        adviceCalls++;
        Object argument = joinPoint.getArgs()[0];
        System.out.println(
            "advice:" + argument.getClass().getSimpleName());
    }

    public int adviceCallCount() {
        return adviceCalls;
    }
}
```

Trong lời khai báo:
- `execution(public void demo.service.OrderProcessor.submit(..))` chỉ chọn method phù hợp trong service.
- `@args(demo.annotations.Reviewed)` chỉ chọn lời gọi có **một actual argument**, runtime class mang `@Reviewed`.
- `JoinPoint.getArgs()[0]` đọc object đã được truyền vào lần invocation đang được advice.
- Lời gọi đến `PlainPayload` không làm advice chạy; do đó `beforeReviewed` không được gọi với payload đó.

Advice là `@Before` nên in log **trước** phần method target. Ta dùng một aspect singleton để đếm số lần advice chạy; bài chỉ chạy tuần tự, không dùng bộ đếm mutable này làm mô hình concurrency production.

### 7.7. Đăng ký bean và bật Spring AOP

Tệp `src/main/java/demo/AppConfig.java`:

```java
package demo;

import demo.aop.ArgumentAspect;
import demo.service.OrderProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public OrderProcessor orderProcessor() {
        return new OrderProcessor();
    }

    @Bean
    public ArgumentAspect argumentAspect() {
        return new ArgumentAspect();
    }
}
```

Spring bean `orderProcessor` được xử lý bởi auto-proxy creator sau khi Spring phân tích advisor từ `ArgumentAspect`. `@Aspect` không tự đăng ký bean, nhưng `@Bean argumentAspect()` bảo đảm aspect nằm trong context. `proxyTargetClass=true` dùng class-based proxy để `context.getBean(OrderProcessor.class)` có thể trả đúng kiểu.

### 7.8. Entry point

Tệp `src/main/java/demo/DemoApplication.java`:

```java
package demo;

import demo.aop.ArgumentAspect;
import demo.model.FlaggedPayload;
import demo.model.PlainPayload;
import demo.service.OrderProcessor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderProcessor processor =
                context.getBean(OrderProcessor.class);
            ArgumentAspect aspect =
                context.getBean(ArgumentAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(processor));

            processor.submit(new FlaggedPayload());
            processor.submit(new PlainPayload());

            Object polymorphic = new FlaggedPayload();
            processor.submit(polymorphic);

            System.out.println("adviceCount=" + aspect.adviceCallCount());
            System.out.println("targetCount=" + processor.targetCallCount());
        }
    }
}
```

Dòng `Object polymorphic = new FlaggedPayload();` là kiểm chứng quan trọng: kiểu khai báo biến là `Object`, còn runtime class vẫn là `FlaggedPayload` được annotate. Spring AOP nên xử lý giống lời gọi thứ nhất.

### 7.9. Chạy và kiểm tra kết quả

Ở thư mục chứa `build.gradle`, chạy với một bản Gradle hỗ trợ Java 17:

```bash
gradle run
```

Nếu đã có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Các dòng ứng dụng Java in ra**, không kể log/banner của Gradle hoặc logging môi trường:

```text
proxy=true
advice:FlaggedPayload
target:FlaggedPayload
target:PlainPayload
advice:FlaggedPayload
target:FlaggedPayload
adviceCount=2
targetCount=3
```

Diễn giải:
1. `proxy=true` chứng minh `OrderProcessor` được Spring AOP proxy.
2. Lời gọi có `FlaggedPayload` thứ nhất in `advice` rồi `target`.
3. Lời gọi với `PlainPayload` **chỉ** in `target` vì runtime class không có `@Reviewed`.
4. Lời gọi qua biến `Object polymorphic` lại được advice vì runtime class thực tế vẫn là `FlaggedPayload`.
5. `adviceCount=2` và `targetCount=3` chứng minh chỉ hai trong ba method invocations khớp `@args`, mặc dù cả ba đều vào cùng method signature `submit(Object)`.

Nếu thử thay `@args(demo.annotations.Reviewed)` bằng chỉ `execution(...)`, advice có thể chạy cho cả ba lời gọi vì điều kiện runtime annotation không còn. Đây là cách phân biệt rõ vai trò của từng mảnh pointcut.

## 8. Binding trong @args: lấy annotation object hoặc đối số

Spring cho phép những pointcut binding như `args` và `@args` đưa dữ liệu đến advice method. Đây là cơ chế **khác** với chỉ kiểm tra annotation có tồn tại.

### 8.1. Bind annotation

Giả sử `@Reviewed` có `value()`, có thể dùng tên tham số trong pointcut:

```java
@Before(
    value = "execution(public void demo.service.OrderProcessor.submit(..))" +
            " && @args(reviewed)",
    argNames = "reviewed"
)
public void beforeReviewed(Reviewed reviewed) {
    System.out.println("label=" + reviewed.value());
}
```

Snippet này thay thế method advice ở mục 7, không đăng ký thêm song song nếu chỉ muốn một log. Cần import `demo.annotations.Reviewed`. Tên `reviewed` trong `@args(reviewed)` được bind vào **annotation instance**, còn giá trị đối số thật không phải chính annotation đó.

`argNames="reviewed"` biểu thị tên binding tường minh; có ích khi mã biên dịch không lưu parameter names, dù Spring cũng có các cơ chế phát hiện tên khác.

### 8.2. Bind actual argument cùng annotation

Khi cần cả đối số và metadata của annotation, biểu thức có thể kết hợp `args(payload)` với `@args(reviewed)`:

```java
@Before(
    value = "execution(public void demo.service.OrderProcessor.submit(..))" +
            " && args(payload) && @args(reviewed)",
    argNames = "payload,reviewed"
)
public void before(Object payload, Reviewed reviewed) {
    System.out.println(payload.getClass().getSimpleName());
    System.out.println(reviewed.value());
}
```

Đây cũng là **snippet thay thế** trong một `@Aspect`, không phải code cần dán bổ sung đồng thời vào project đang chạy. Với phương thức có một đối số, `args(payload)` lấy actual object còn `@args(reviewed)` lấy annotation trên runtime type. Khi dùng annotation/binding trong production, nên có integration test cho advice parameter binding và loại proxy của version Spring đang chạy.

Nếu chỉ cần xem object mà không cần bind annotation, `JoinPoint.getArgs()` như ở ví dụ chính ít có yêu cầu discovery tên tham số hơn.

## 9. Những giới hạn phải kiểm tra trong hệ thống thật

### 9.1. Annotation phải tồn tại ở runtime

Một annotation định nghĩa như sau không thích hợp cho `@args`:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface SourceOnlyFlag {
}
```

`RetentionPolicy.SOURCE` không giữ annotation ở runtime để Spring/AspectJ matcher đọc. Dù code compile, lời gọi runtime sẽ không khớp như mong muốn. Với marker type cho `@args`, dùng `RUNTIME` trừ khi có lý do thiết kế khác.

### 9.2. Annotation trên interface và class không luôn tương đương

Nếu chỉ annotation lên một **interface**, không nên mặc định `getClass()` của implementation mang annotation đó. Java `@Inherited` (nếu sử dụng) tác động tới một số trường hợp **kế thừa annotation từ superclass**, không làm type annotation tự lan từ interface sang implementation class.

`@args` xét annotation theo kiểu runtime của actual argument; vì vậy cần kiểm tra:
- Annotation được đặt trên **implementation class thật** hay chỉ trên interface?
- Runtime class có phải một subclass hoặc proxy class không?
- Annotation có `@Inherited` hay không, và có áp dụng cho hierarchy đang xét không?
- Spring/AspectJ expression matcher sẽ xem runtime object như thế nào trên cấu hình thực tế?

Giải pháp an toàn là **thiết kế annotation boundary rõ ràng** và viết test trên class/runtime argument thực. Không lấy `@args` để thay thế mọi kiểu tra cứu annotation trong hierarchy.

### 9.3. Argument bản thân là một proxy

Nếu truyền **một Spring AOP proxy** làm đối số cho service khác, lớp runtime của đối số có thể là JDK dynamic proxy hoặc CGLIB subclass, không giống class implementation trong source. Sự hiện diện của annotation trên implementation không bảo đảm proxy class sẽ biểu lộ cùng metadata theo đúng cách `@args` matcher kỳ vọng.

Khi áp dụng `@args` lên những đối số là framework proxy:
1. Xác định kiểu proxy của **argument object**, không chỉ proxy của service được advice.
2. Kiểm chứng matcher bằng test với runtime argument thực.
3. Nếu nghiệp vụ cần annotation trên **target class phía sau proxy**, cân nhắc truy xuất target type bằng Spring utility trong code kiểm tra metadata có chủ đích, thay vì giả định `@args` đã unwrap mọi proxy.

### 9.4. null không có runtime class

Một biến `Object value = null` không có class runtime để đọc type annotation. Đừng giả định pointcut có thể suy ra rằng “đây là annotated type” chỉ từ parameter declaration hoặc từ generic type. Dùng null validation rõ ràng, và nếu null là input hợp lệ, kiểm tra hành vi Spring AOP và advice của phiên bản thực tế trước khi dựa vào đó.

### 9.5. Bộ sưu tập không được kiểm tra đệ quy

Method `submit(List<ReviewedOrder> values)` nhận một `List` object. `@args(Reviewed)` không tự duyệt phần tử để tìm `@Reviewed`. Tương tự, một wrapper `Envelope<ReviewedOrder>` không trở thành annotated runtime class chỉ vì generic argument của nó mang annotation. Nếu muốn kiểm tra nested contents, cần logic duyệt cụ thể hoặc validation layer thích hợp.

### 9.6. Spring AOP chỉ thấy lời gọi qua proxy

Trong Spring Framework, `@args` vẫn chịu mọi ranh giới của Spring AOP proxy:

- Target bean phải do Spring quản lý và được advice infrastructure phát hiện.
- Lời gọi phải qua Spring-managed proxy, không phải object tạo bằng `new`.
- Self-invocation như `this.submit(payload)` trong cùng target không đi qua advice.
- Class-based proxy không thể chặn method `final`/`private` qua override.
- JDK proxy chỉ expose các method thuộc interface proxy thích hợp; tính khả dụng của method có thể khác CGLIB.
- `@args` không biến Spring proxy AOP thành cơ chế intercept constructor, field access hay method calls không được proxy hóa.

**Đừng thay đổi pointcut để sửa self-invocation.** Nếu business rule cần advice trên cả method gọi nội bộ, tách thao tác sang bean cộng tác và gọi qua Spring bean proxy từ bên ngoài.

### 9.7. Match động có chi phí runtime

`@args` phụ thuộc **actual arguments**, có thể khác nhau giữa hai lời gọi cùng một method. Vì vậy điều kiện này thường không thể quyết định hoàn toàn chỉ từ method signature khi Spring tạo proxy. AOP infrastructure có thể phải đánh giá thêm trong từng invocation.

Tài liệu Spring AOP phân biệt `MethodMatcher` static và runtime; runtime matching nhận `Object... args` để xét argument values. Do đó:
- Giới hạn bằng `execution(...)`/`within(...)` cho những service methods thật sự cần thiết.
- Không lạm dụng `@args` cho toàn bộ application chỉ để kiểm tra annotation mà service có thể kiểm tra rõ ràng một lần.
- Đo hiệu năng trong workload thực nếu đây là hot path; không suy luận một con số overhead cố định.
- Không giữ business state thay đổi theo từng invocation trong field mutable của singleton aspect nếu hệ thống concurrent, trừ khi đã thiết kế thread safety.

## 10. Cách chọn pointcut đúng theo yêu cầu

| Yêu cầu | Công cụ phù hợp |
| --- | --- |
| Chọn method `submit` có parameter **khai báo** là `Object` | `execution(* *.submit(Object))` |
| Chọn lời gọi có runtime argument thuộc một class/interface cụ thể | `args(TypePattern)` |
| Chọn lời gọi mà **runtime class của actual argument** mang `@Reviewed` | `@args(Reviewed)` |
| Chọn method được đánh dấu `@AuditedOperation` | `@annotation(AuditedOperation)` |
| Chọn lời gọi trên service target type được annotate | `@target(Marker)` nếu semantics là target runtime annotation |
| Chọn method được khai báo trong annotated type | `@within(Marker)` |
| Chọn các Spring beans theo tên | `bean(namePattern)` |
| Chọn cả layer lẫn runtime argument annotation | `within(service..*) && @args(Reviewed)` |
| Kiểm tra annotation trực tiếp trên Java parameter declaration | Validation hoặc reflection/advisor logic riêng; `@args` không phải phép kiểm tra này |
| Kiểm tra từng item trong `List` hay nested object | Duyệt đối số hoặc dùng validation phù hợp; `@args` không tự duyệt sâu |

### Câu hỏi tự kiểm tra

**Tình huống A:** Service khai báo `void save(Object x)`, đối số thực tế là instance `AnnotatedInvoice` được gắn `@Reviewed`. Nếu lời gọi qua Spring proxy và pointcut là `@args(Reviewed)`, advice có thể chạy không?

**Đáp án:** Có. Runtime type của actual argument được annotate.

**Tình huống B:** Service khai báo `void save(@Reviewed Object x)`, còn object truyền vào thuộc `PlainInvoice` không mang annotation ở type. Advice `@args(Reviewed)` có chạy do annotation trên parameter declaration không?

**Đáp án:** Không thể lấy annotation trên method parameter để làm căn cứ cho `@args`; hơn nữa `@Reviewed` dành cho `TYPE` trong ví dụ này nên cách annotate parameter như thế còn không hợp lệ. Nếu muốn đánh dấu parameter, cần annotation có `@Target(PARAMETER)` và một cơ chế kiểm tra khác.

**Tình huống C:** `@Reviewed` chỉ đặt trên `List` element type mà method nhận `List<Object>`. Có nên kỳ vọng `@args(Reviewed)` tự inspect mỗi item?

**Đáp án:** Không. Nó xét runtime type của actual argument là đối tượng List, không tự duyệt phần tử.

**Tình huống D:** Trong bean target, `process()` tự gọi `this.submit(new FlaggedPayload())`. Lớp payload có `@Reviewed`. Advice có chạy không?

**Đáp án:** Với Spring AOP theo proxy, self-invocation bỏ qua proxy nên không chạy advice cho lời gọi nội bộ đó, dù điểm cắt dựa trên runtime argument sẽ phù hợp nếu invocation đi qua proxy.

## 11. Quy trình debug một @args pointcut không chạy

1. **Xác nhận annotation definition.** Có `@Target(TYPE)` và `@Retention(RUNTIME)` chưa? Annotation được đặt trên runtime class thật chưa?
2. **Xác nhận số lượng đối số.** `@args(Reviewed)` một vị trí phù hợp method có một actual argument; nếu method nhận nhiều đối số cần pattern phù hợp vị trí.
3. **Xác nhận runtime class.** In `argument.getClass()` trong code test khi non-null; không chỉ nhìn kiểu khai báo của parameter.
4. **Xác nhận Spring bean/proxy.** Aspect là bean, `@EnableAspectJAutoProxy` hoặc Boot AOP auto-config đang hoạt động và target được gọi qua proxy.
5. **Xác nhận điều kiện static.** Nếu kết hợp `execution(...)` hoặc `within(...)`, method/service có khớp không?
6. **Xác nhận không self-invocation.** Nếu gọi từ method khác trên `this`, proxy advice bị bypass.
7. **Xác nhận argument không phải proxy bất ngờ.** Nếu argument object do Spring hoặc framework proxy, runtime class và metadata có thể khác dự kiến.
8. **Xác nhận null/collection semantics.** Không suy ra annotation của nested elements hoặc của biến null.
9. **Kiểm tra advice binding.** Nếu dùng `@args(reviewed)`, tham số advice phải có kiểu annotation tương ứng và tên binding phải được Spring xác định.
10. **Viết integration test** với ít nhất annotated input, unannotated input và biến khai báo kiểu rộng như `Object` để phát hiện nhầm static và runtime types.

## 12. Kết luận

Trong Spring Aspect, `@args` là pointcut designator để chọn lời gọi method dựa vào **annotation trên runtime type của actual arguments**. Nó phù hợp khi nhiều payload classes đi qua cùng một service API khai báo kiểu rộng nhưng chỉ một số loại payload được đánh dấu cần xử lý bổ sung.

Hãy nhớ ba ranh giới:

- **Type:** `@args` quan tâm runtime type annotation của đối số, không phải method annotation hay parameter-declaration annotation.
- **Shape:** `@args(Annotation)` một vị trí có ràng buộc số lượng/vị trí đối số; collection không được duyệt sâu.
- **Proxy:** Chỉ invocation qua Spring AOP proxy mới được advice; pointcut phù hợp nhưng self-invocation vẫn bỏ qua proxy.

Thiết kế bền vững thường kết hợp `execution`/`within` để thu hẹp layer và `@args` để kiểm tra điều kiện runtime, dùng annotation `RUNTIME`, và có integration tests cho subtype, proxy, null cũng như các method overload quan trọng.

### Tài liệu Spring tham khảo

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): mô tả `@args` là runtime annotation trên actual argument type, cùng ví dụ với một parameter; so sánh với `args`, `@target`, `@within` và `@annotation`.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): tham số advice, `JoinPoint.getArgs()` và binding của `@args`.
- [Spring Framework — Spring AOP Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `MethodMatcher` tĩnh/động và kiểm tra `matches(Method, Class, Object...)`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy boundary, JDK/CGLIB và self-invocation.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): cấu hình Spring AOP và đăng ký `@Aspect` làm Spring bean.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-008">bean để chọn theo tên bean trong Spring</a>

<details>
<summary>Xem chi tiết</summary>

# `bean(...)` — chọn pointcut theo tên Spring bean

## 1. Khi nào cần `bean(...)` trong Spring AOP?

Spring AOP thường dùng biểu thức `execution(...)`, `within(...)`, `target(...)` hoặc `@annotation(...)` để chọn những *method execution* sẽ được advice. Nhưng có một nhu cầu rất đặc trưng của Spring: **hai Spring bean có cùng một lớp triển khai, cùng chữ ký method, nhưng chỉ muốn advice một bean có tên cụ thể**.

Spring cung cấp pointcut designator (PCD) **`bean`** để giải quyết trường hợp này:

```java
@Before("execution(* demo..*.process(..)) && bean(primaryJobService)")
public void audit() {
    System.out.println("AUDIT");
}
```

Đây là **đoạn advice minh họa**; method cần nằm trong một class mang `@Aspect`, class đó phải được đăng ký thành Spring bean và hỗ trợ Spring AOP phải được bật.

Trong biểu thức trên:

- `execution(* demo..*.process(..))`: chỉ chọn **method execution** có tên `process` và nằm trong phạm vi pattern.
- `bean(primaryJobService)`: chỉ chọn **Spring bean có tên `primaryJobService`**.
- `&&`: cả hai điều kiện đều phải thỏa mới áp dụng advice.

**Điểm chính:** `bean(...)` lọc theo **tên bean trong Spring container**, không lọc trực tiếp theo tên class, tên interface, tên method, tên biến Java hay giá trị `@Qualifier` bất kỳ.

Bài này chỉ bàn về **Spring AOP / Spring Framework**, không đi sâu vào các khái niệm Aspect tổng quát. `bean(...)` là một **Spring-specific extension**, không thuộc danh sách PCD tiêu chuẩn mà native AspectJ weaving có thể dùng tương đương.

## 2. Cú pháp và ý nghĩa

Cú pháp Spring AOP:

```text
bean(idOrNameOfBean)
```

Trong đó `idOrNameOfBean` là tên bean được Spring quản lý, có thể chứa ký tự đại diện `*`:

| Biểu thức | Ý nghĩa |
| --- | --- |
| `bean(primaryJobService)` | Chọn bean mang tên `primaryJobService` |
| `bean(*Service)` | Chọn các bean có tên kết thúc bằng `Service` |
| `bean(order*)` | Chọn các bean có tên bắt đầu bằng `order` |
| `bean(*Audit*)` | Chọn các bean có tên chứa `Audit` |
| `!bean(backupJobService)` | Loại trừ bean có tên `backupJobService` |
| `bean(primaryJobService) || bean(backupJobService)` | Chọn một trong hai tên |
| `bean(*Service) && !bean(backupJobService)` | Chọn các bean hậu tố `Service`, trừ bean dự phòng |
| `bean(*Service) && execution(public * *(..))` | Chọn method public phù hợp trên các bean có hậu tố tên `Service` |

Spring hỗ trợ **wildcard đơn giản bằng `*`**, kết hợp logic với `&&`, `||`, `!`. Đây không phải biểu thức regex. Không thay `*` bằng cú pháp regex như `.*`, và không coi pattern này như đường dẫn package của `execution` hoặc `within`.

Điều kiện `bean` áp dụng ở **bean-instance/named-bean level**. Nó có thể phân biệt **hai instance bean cùng class** nếu hai bean có tên khác nhau. Đây là lý do Spring AOP hỗ trợ `bean`: Spring BeanFactory biết danh tính bean theo tên.

### Khi chỉ có `bean(...)` thì sao?

Spring AOP vẫn chỉ chạy advice trên những **method execution mà proxy có thể intercept**. Một expression như `bean(primaryJobService)` không thể khiến Spring advice constructor, field access hoặc lời gọi trên object do `new` tạo ngoài container. Nó cũng không bảo đảm mọi method của bean đều intercept được (ví dụ CGLIB không override được `final/private` method).

Trong code thực tế, nên kết hợp với `execution(...)` để chỉ rõ ranh giới method và giữ pointcut dễ đọc:

```java
@Pointcut("bean(*Service) && execution(public * demo..service..*.*(..))")
public void serviceMethodsOnNamedBeans() {
}
```

Đây là **named-pointcut minh họa**, chưa phải class độc lập. Trong Java string literal, có thể viết toán tử `&&` trực tiếp; khi cấu hình XML, phải escape thành `&amp;&amp;` hoặc dùng các toán tử từ `and/or/not` theo cú pháp hỗ trợ.

## 3. Spring bean name đến từ đâu?

Spring xác định bean name khi đăng ký bean definition. Một số cách phổ biến:

### 3.1. `@Bean` với tên tường minh

```java
@Configuration
class JobConfig {
    @Bean("primaryJobService")
    JobService primary() {
        return new JobServiceImpl("PRIMARY");
    }

    @Bean("backupJobService")
    JobService backup() {
        return new JobServiceImpl("BACKUP");
    }
}
```

Trong đoạn này, **tên bean là `primaryJobService` và `backupJobService`**. Không phải `primary`/`backup` chỉ vì đó là tên hai Java methods.

Các type `JobService` và `JobServiceImpl` sẽ được định nghĩa đầy đủ trong chương trình ở phần 5. Đoạn trên chỉ minh họa cách đặt tên qua `@Bean`.

### 3.2. `@Bean` không có tên tường minh

```java
@Configuration
class JobConfig {
    @Bean
    JobService primaryJobService() {
        return new JobServiceImpl("PRIMARY");
    }
}
```

Trong cách dùng thông thường, bean mang tên mặc định là **`primaryJobService`**, trùng với tên method khai báo `@Bean`.

### 3.3. Component scanning

```java
@Service("inventoryService")
public class InventoryService {
    public void check() {
        System.out.println("CHECK");
    }
}
```

Đây là class minh họa. Nếu Spring component-scan phát hiện class, tên tường minh là `inventoryService`. Khi không cung cấp tên tường minh, Spring thường sinh tên theo quy tắc đặt tên bean (chẳng hạn `InventoryService` thành `inventoryService`); có những trường hợp tên viết hoa đặc biệt, custom `BeanNameGenerator` hoặc cấu hình khác. Với pointcut dựa vào tên, **nên xác minh tên thực tế** thay vì đoán từ class.

### 3.4. Bean alias và đặt tên lại

Spring có thể hỗ trợ **nhiều tên/alias trỏ tới cùng một bean definition**. Nếu ứng dụng có alias hoặc dùng registry tùy biến, cần kiểm tra tên và alias mà Spring AOP matcher thực tế nhận diện, cũng như ảnh hưởng của việc đổi tên bean. Một alias **không phải** một target instance mới.

Không nên dựa vào chi tiết alias không được kiểm thử như một bảo đảm nghiệp vụ; hãy chọn một tên bean ổn định, đặt tường minh và test pointcut trong context thật. Đổi `@Bean("primaryJobService")` thành tên khác **có thể làm advice ngừng khớp** dù class và method không đổi.

## 4. Vì sao `bean(...)` khác `execution(...)`, `within(...)` và `target(...)`?

Giả sử hai bean khác nhau nhưng cùng target class:

```text
Spring ApplicationContext
   |
   +-- bean name = primaryJobService
   |      class = JobServiceImpl
   |      method = process(String)
   |
   +-- bean name = backupJobService
          class = JobServiceImpl
          method = process(String)
```

Nếu dùng:

```java
@Before("execution(* demo..*.process(..))")
```

cả hai bean có thể được advice, vì Spring chọn theo **method execution** và target type/signature phù hợp.

Nếu dùng:

```java
@Before("execution(* demo..*.process(..)) && bean(primaryJobService)")
```

chỉ bean có tên `primaryJobService` được chọn.

| Điều kiện | Đang chọn theo | Có thể tách hai bean cùng class khác tên? |
| --- | --- | --- |
| `execution(* demo..*.process(..))` | Method signature / execution | Không, nếu các method tương ứng giống nhau |
| `within(demo..*)` | Kiểu/phạm vi khai báo method | Không, nếu cùng implementation |
| `target(demo.JobServiceImpl)` | Kiểu của target object | Không, nếu cả hai là instance cùng class |
| `this(demo.JobService)` | Kiểu của Spring AOP proxy | Không, nếu cả hai proxy cùng expose interface đó |
| `@annotation(demo.Audited)` | Annotation trên method execution | Không, nếu cùng một method được gắn annotation |
| **`bean(primaryJobService)`** | **Tên của Spring bean** | **Có** |

Không nên viết `bean(JobServiceImpl)` nếu ý định là “chọn mọi bean có class `JobServiceImpl`”. Biểu thức đó sẽ tìm bean **tên `JobServiceImpl`**, không phải tự kiểm tra class. Nếu muốn chọn theo type, sử dụng `target(...)`, `this(...)` hoặc `execution(...)` theo mục đích.

### Đặc điểm Spring-specific

Spring Framework xác nhận `bean(...)` là **extension dành cho Spring AOP**, bởi framework gắn chặt với BeanFactory và có thể biết bean name. Native AspectJ weaving tập trung vào join point/type-level semantics và **không có PCD `bean` tương đương**. Aspect được khai báo bằng `@Aspect` **và do Spring AOP xử lý** vẫn có thể dùng extension này; điều không tương thích là chuyển nguyên pointcut sang AspectJ compiler/weaver để weaving ngoài Spring AOP.

## 5. Demo chạy được: hai bean cùng implementation nhưng advice khác nhau

Ví dụ sử dụng **Java 17+, Maven, Spring Framework 6.2.19**. Nó tạo **hai Spring application context độc lập** để cho thấy hai biểu thức:

1. **Exact:** `bean(primaryJobService)` chỉ áp dụng cho một bean.
2. **Wildcard:** `bean(*JobService)` áp dụng cho cả hai bean có hậu tố tên `JobService`.

Cả hai context đăng ký **cùng hai target classes/implementations**, chỉ đổi `@Aspect` bean. Code sử dụng `@Bean` có tên tường minh để không phụ thuộc bean-name generator. Target `JobServiceImpl` implement interface `JobService` nên ví dụ chạy được với JDK dynamic proxy mặc định của cấu hình Spring Framework.

### 5.1. Cấu trúc dự án

```text
spring-aop-bean-pointcut/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanPointcutDemo.java
```

### 5.2. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-bean-pointcut</artifactId>
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
                    <mainClass>demo.BeanPointcutDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 5.3. Tệp `src/main/java/demo/BeanPointcutDemo.java`

```java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class BeanPointcutDemo {

    public interface JobService {
        String process(String id);
    }

    public static class JobServiceImpl implements JobService {
        private final String label;

        public JobServiceImpl(String label) {
            this.label = label;
        }

        @Override
        public String process(String id) {
            System.out.println("TARGET " + label + " " + id);
            return label + ":" + id;
        }
    }

    @Aspect
    public static class ExactAspect {
        @Before(
            "execution(* demo..*.process(..)) " +
            "&& bean(primaryJobService)"
        )
        public void trace(JoinPoint jp) {
            System.out.println("ADVICE exact " + jp.getSignature().getName());
        }
    }

    @Aspect
    public static class WildcardAspect {
        @Before(
            "execution(* demo..*.process(..)) " +
            "&& bean(*JobService)"
        )
        public void trace(JoinPoint jp) {
            System.out.println("ADVICE wildcard " + jp.getSignature().getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SharedBeans {
        @Bean("primaryJobService")
        public JobService primaryJobService() {
            return new JobServiceImpl("PRIMARY");
        }

        @Bean("backupJobService")
        public JobService backupJobService() {
            return new JobServiceImpl("BACKUP");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(SharedBeans.class)
    public static class ExactConfig {
        @Bean
        public ExactAspect exactAspect() {
            return new ExactAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(SharedBeans.class)
    public static class WildcardConfig {
        @Bean
        public WildcardAspect wildcardAspect() {
            return new WildcardAspect();
        }
    }

    static void run(String title, Class<?> config) {
        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            JobService primary =
                    ctx.getBean("primaryJobService", JobService.class);
            JobService backup =
                    ctx.getBean("backupJobService", JobService.class);

            System.out.println("=== " + title + " ===");
            System.out.println("PRIMARY_PROXY=" + AopUtils.isAopProxy(primary));
            System.out.println("BACKUP_PROXY=" + AopUtils.isAopProxy(backup));

            System.out.println("RESULT " + primary.process("A"));
            System.out.println("RESULT " + backup.process("B"));
        }
    }

    public static void main(String[] args) {
        run("EXACT", ExactConfig.class);
        run("WILDCARD", WildcardConfig.class);
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng do chương trình in, theo thứ tự kỳ vọng** (bỏ qua log bổ sung từ Maven/Spring nếu có):

```text
=== EXACT ===
PRIMARY_PROXY=true
BACKUP_PROXY=false
ADVICE exact process
TARGET PRIMARY A
RESULT PRIMARY:A
TARGET BACKUP B
RESULT BACKUP:B
=== WILDCARD ===
PRIMARY_PROXY=true
BACKUP_PROXY=true
ADVICE wildcard process
TARGET PRIMARY A
RESULT PRIMARY:A
ADVICE wildcard process
TARGET BACKUP B
RESULT BACKUP:B
```

Vì sao kết quả khác nhau?

- Hai bean trong mỗi context **đều dùng `JobServiceImpl`**, đều expose `JobService.process(String)`, và đều do Spring quản lý.
- Trong `ExactConfig`, Spring chỉ tạo AOP proxy cho `primaryJobService` vì chỉ bean name đó match pointcut. `backupJobService` không nhận proxy Spring AOP từ advice này.
- Trong `WildcardConfig`, cả hai bean name kết thúc bằng `JobService`, nên advisor áp cho cả hai target và chúng đều có Spring AOP proxy.
- Advice `@Before` chạy trước target method nếu lời gọi qua proxy và pointcut match.
- Hai context độc lập giúp tránh tình trạng nhiều advice chạy trên cùng join point với thứ tự tương đối không được khai báo.

**Ranh giới quan trọng:** output mô tả demo với chỉ một advisor liên quan trong mỗi context. Nếu ứng dụng có transaction/caching hoặc aspect khác, một bean không match `bean(...)` vẫn có thể nhận proxy bởi **advisor khác**. Do đó `AopUtils.isAopProxy(backup)==false` ở nhánh EXACT chỉ đúng trong setup được nêu, không phải quy luật tổng quát.

## 6. Cách giới hạn nhiều bean theo pattern và điều kiện phủ định

Sau khi nắm demo, có thể viết các pointcut phức hợp rõ chủ đích.

### 6.1. Chọn một nhóm bean đặt tên theo convention

```java
@Pointcut("bean(*Service)")
public void namedServices() {
}

@Pointcut("execution(public * demo..*.process(..))")
public void publicProcessingMethods() {
}

@Before("namedServices() && publicProcessingMethods()")
public void auditServices() {
    System.out.println("AUDIT");
}
```

Đây là các method mẫu **nằm trong cùng một Spring `@Aspect` bean**; chưa phải chương trình độc lập. Biểu thức có thể tái sử dụng tên pointcut thay vì lặp pattern dài.

### 6.2. Loại trừ một bean cụ thể

```java
@Before(
    "bean(*JobService) && !bean(backupJobService) " +
    "&& execution(* demo..*.process(..))"
)
public void auditNonBackup() {
    System.out.println("AUDIT-NON-BACKUP");
}
```

Trên hai bean của demo, chỉ `primaryJobService` còn match. Điều kiện `!bean(backupJobService)` là **phủ định match theo tên**, không phải phủ định class hay việc bean có được Spring quản lý hay không.

### 6.3. Chọn một tập tên rõ ràng

```java
@Before(
    "(bean(primaryJobService) || bean(backupJobService)) " +
    "&& execution(* demo..*.process(..))"
)
public void auditSelected() {
    System.out.println("AUDIT-SELECTED");
}
```

Biểu thức `(...)` nhóm điều kiện trước khi kết hợp với `execution`; mục đích là chỉ chọn method `process(..)` của hai bean được đặt tên cụ thể.

### 6.4. Phạm vi tên không phải phạm vi class/package

```text
bean(*Service)                 -> kiểm tra tên Spring bean
execution(* demo.service..*.*(..))
                               -> kiểm tra method execution theo package/type
within(demo.service..*)        -> kiểm tra phạm vi kiểu khai báo method
target(demo.service.JobService)-> kiểm tra target object type
```

Các dòng trên là **minh họa ý nghĩa biểu thức**. Một `@Bean("primaryJobService")` có thể trả instance của class nằm ngoài package `demo.service`, và `bean(primaryJobService)` vẫn có thể match theo tên. Ngược lại, class trong package `demo.service` không được chọn bởi `bean(*Service)` nếu tên Spring bean của nó không khớp pattern.

## 7. `bean(...)` không đồng nghĩa với `@Qualifier` hoặc tên biến

`@Qualifier` chủ yếu hỗ trợ Spring dependency injection: nó thu hẹp **ứng viên dependency theo type**. Còn `bean(...)` trong Spring AOP quyết định **advisor áp dụng cho bean mang tên nào**.

Ví dụ:

```java
public JobRunner(
        @Qualifier("primaryJobService") JobService jobService) {
    this.jobService = jobService;
}
```

Đây là **constructor mẫu** cần field, class và import. `@Qualifier("primaryJobService")` có thể giúp chọn bean phù hợp khi inject, nhưng không phải nó “tạo một tên bean mới” hoặc làm `bean(primaryJobService)` bỗng khớp. Tên bean đã tồn tại trong registry.

Nếu có một custom qualifier `@Qualifier("critical")` gắn cho bean tên `mainProcessor`, biểu thức `bean(critical)` **không tự match** tên custom qualifier đó. Muốn chọn theo annotation/type, cần dùng cơ chế pointcut tương ứng hoặc đặt tên Spring bean theo convention phù hợp.

Tương tự, tên field Java `private JobService jobService;` hoặc tên local variable không quyết định bean name. Khi cần kiểm tra thực tế, có thể gọi `context.containsBean("primaryJobService")` hoặc lấy bean bằng tên từ `ApplicationContext`.

## 8. Spring Boot và các quy tắc tạo proxy liên quan

Trong Spring Framework thuần, `@EnableAspectJAutoProxy` kích hoạt auto-proxy cho `@AspectJ` style khi có các dependency phù hợp. Trong Spring Boot với `spring-boot-starter-aop` và điều kiện classpath, AOP thường được tự cấu hình; không nhất thiết phải thêm annotation đó thủ công.

Một khác biệt cấu hình thường gặp:

- Spring Framework thuần: target có interface thường được JDK dynamic proxy khi `proxyTargetClass=false`.
- Spring Boot auto-configuration AOP mặc định thường ưu tiên **class-based proxy**; có thể dùng `spring.aop.proxy-target-class=false` để cho phép JDK proxy khi có interface.

**Dù chọn JDK hay CGLIB, `bean(...)` vẫn xét tên Spring bean**, nhưng còn cần method có thể được proxy intercept. Nếu chuyển sang JDK proxy, method chỉ có trên implementation và không expose qua interface sẽ không tự trở thành callable qua JDK proxy. Nếu CGLIB, `final/private` method không được override để advice.

Không mặc định rằng thiết lập `spring.aop.auto=false` tắt mọi nguồn tạo proxy của ứng dụng; các hạ tầng transaction, custom advisors hoặc cấu hình khác vẫn có thể tạo proxy. Khi debug `bean(...)`, phải tách rõ **bean name matching** và **có proxy/advisor thực sự áp dụng hay không**.

## 9. Giới hạn quan trọng của Spring AOP `bean(...)`

### 9.1. Chỉ dành cho Spring AOP proxy-based pointcuts

`bean(...)` là phần mở rộng gắn với Spring BeanFactory, không phải PCD chuẩn dùng chung cho native AspectJ weaving. Nếu code chuyển từ Spring AOP sang compile-time/load-time AspectJ weaving, cần thiết kế lại pointcut theo các type/annotation/logic khả dụng ở môi trường weaving.

### 9.2. Lời gọi phải đi qua Spring proxy

Nếu một service gọi `this.process()` bên trong chính target của nó, lời gọi đó **không quay lại proxy**. Dù `bean(primaryJobService)` khớp tên bean, advice không được thực thi thêm lần nữa ở lời gọi nội bộ này. Tương tự, object tạo bằng `new JobServiceImpl(...)` ngoài Spring không được advisor lựa chọn chỉ vì có class tương ứng.

### 9.3. Không phải phân vùng dữ liệu bảo mật

`bean(...)` giúp chọn advisor theo bean name, nhưng **không phải một cơ chế phân quyền hoặc sandbox**. Một tên bị đổi, bean được alias, hoặc logic bị gọi theo đường không qua proxy có thể làm kỳ vọng advice thay đổi. Với nhu cầu bảo mật, cần kiểm tra authorization tại ranh giới thích hợp và có kiểm thử; không chỉ trông chờ tên bean hay việc log/audit advice đã chạy.

### 9.4. Kết quả có thể khác khi bean được tạo bởi factory/proxy khác

Các đối tượng như `FactoryBean`, scoped proxy, proxy lồng nhau, target source động và bean definitions đặc biệt làm việc tra tên hoặc đối tượng runtime phức tạp hơn. Trong những tình huống đó, nên đo tên bean thực tế, loại proxy và các advisors đang gắn vào bean. Không tự khẳng định `bean(pattern)` luôn phản ánh alias hay factory product theo đúng một cách cho mọi dạng factory/proxy.

### 9.5. Tên bean không nhất thiết ổn định khi refactor

Chuyển từ `@Bean("primaryJobService")` sang `@Component` sinh tên tự động, đổi tên `@Bean` method hoặc dùng custom naming strategy có thể làm pointcut không còn khớp. Vì vậy, khi `bean(...)` mang ý nghĩa nghiệp vụ cần lâu dài, ưu tiên tên tường minh và thêm integration test kiểm tra advice.

## 10. Quy trình chẩn đoán pointcut `bean(...)` không chạy

1. **Tên thực tế:** xác minh trong context đang chạy có `primaryJobService` không; không đoán từ tên class, field hoặc `@Qualifier`.
2. **Có nhiều instance cùng class:** kiểm tra bean được gọi từ client là **bean nào**, không chỉ kiểm `getClass()`.
3. **Đã bật @AspectJ auto-proxy:** aspect class có `@Aspect` và được đăng ký thành Spring bean chưa?
4. **Pointcut có đúng chữ hoa/thường và wildcard:** kiểm tra `bean(*Service)` có khớp hậu tố tên thực tế không; phân biệt `*` với regex.
5. **Điều kiện kết hợp:** `execution(...)` có match method đang gọi? Nếu `bean(...)` đúng nhưng `execution(...)` sai, tổng `&&` vẫn không match.
6. **Bean có Spring AOP proxy không:** dùng `AopUtils.isAopProxy(bean)`. Không có proxy thường là dấu hiệu không có advisor nào áp vào bean trong context đó.
7. **Các advisor khác:** có thể bean nhận proxy vì transaction/cache/advice khác dù `bean(...)` bạn viết không khớp. Vì vậy proxy tồn tại không đủ chứng minh chính advice của bạn đã chạy.
8. **Phương thức được gọi qua proxy:** tránh `new` object hoặc self-invocation `this.method()` trên target.
9. **Khả năng chặn method:** JDK proxy chỉ expose interface methods; CGLIB bị giới hạn bởi `final/private` và khả năng kế thừa.
10. **Nhiều context/proxy layer:** bảo đảm đang nhìn đúng Spring `ApplicationContext` và đúng bean reference; context test và context production có thể đăng ký tên khác nhau.

Có thể in các bean names theo type trong **ứng dụng của bạn** (đây là đoạn code bổ sung, không phải chương trình standalone):

```java
String[] names = context.getBeanNamesForType(JobService.class);
for (String name : names) {
    Object bean = context.getBean(name);
    System.out.println(name + " -> aop=" + AopUtils.isAopProxy(bean));
}
```

Nếu cùng type có nhiều bean, đoạn trên hữu ích để kiểm tra tên thực tế, nhưng một advisor chỉ áp trên một bean không làm các bean khác cùng class tự được advice.

## 11. Những nhận định sai thường gặp

| Nhận định | Sự thật |
| --- | --- |
| “`bean(JobServiceImpl)` là match mọi bean thuộc class `JobServiceImpl`.” | `bean(...)` match theo **tên Spring bean**, không phải tên class. |
| “Hai bean cùng class luôn nhận cùng advice.” | `bean(name)` có thể chỉ chọn một instance bean theo tên. |
| “`bean(*Service)` là regex.” | Đây là wildcard đơn giản `*` trong pattern tên bean. |
| “Dùng `@Qualifier("critical")` thì `bean(critical)` sẽ match.” | Qualifier metadata không tự trở thành tên bean. |
| “Chỉ cần `@Aspect` class có pointcut `bean(...)` là hoạt động.” | Aspect còn phải được đăng ký thành Spring bean; AOP support cần được bật phù hợp. |
| “`bean(...)` advice mọi method trên bean kể cả private/constructor.” | Spring AOP chỉ intercept method execution qua proxy, còn chịu giới hạn proxy strategy. |
| “`bean(...)` dùng được như nhau trong AspectJ weaving native.” | Đây là Spring-specific pointcut designator cho Spring AOP. |
| “Một bean không match `bean(...)` chắc chắn không có proxy.” | Advisor khác có thể tạo AOP proxy cho bean đó. |
| “Đổi bean name không tác động pointcut.” | Refactor tên có thể làm biểu thức ngừng khớp. |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Hai Spring bean `primaryJobService` và `backupJobService` có cùng class `JobServiceImpl`. `bean(primaryJobService)` chọn mấy bean?  
**Đáp án:** Chọn bean có đúng tên `primaryJobService` (một trong hai, giả định không có alias/đăng ký đặc biệt và có method match).

**Câu 2.** `bean(*JobService)` chọn bean nào nếu cả hai tên đều kết thúc bằng `JobService`?  
**Đáp án:** Cả hai bean, trong phạm vi những method execution được proxy và điều kiện pointcut còn lại.

**Câu 3.** `bean(JobServiceImpl)` có tự hiểu đó là tên Java class không?  
**Đáp án:** Không. Spring xét bean name `JobServiceImpl` theo pattern.

**Câu 4.** Một method nằm ngoài package được `execution(...)` chọn, nhưng bean name phù hợp `bean(primaryJobService)`. Biểu thức ghép `execution(...) && bean(...)` có match không?  
**Đáp án:** Không, vì toán tử AND đòi hỏi cả hai điều kiện.

**Câu 5.** Dùng custom `@Qualifier("fast")` cho bean `primaryJobService`. `bean(fast)` chắc chắn match không?  
**Đáp án:** Không. Custom qualifier không tự tạo bean name `fast`.

**Câu 6.** Nếu Spring AOP proxy `primaryJobService` có advice cho `process()`, target gọi nội bộ `this.process()` sẽ chạy lại advice không?  
**Đáp án:** Không có lời gọi vào proxy mới; self-invocation bỏ qua Spring AOP interception.

**Câu 7.** Nếu bean `backupJobService` không match exact pointcut nhưng có `@Transactional`, nó có thể vẫn là proxy không?  
**Đáp án:** Có, vì cơ chế/advisor khác có thể tạo proxy.

**Câu 8.** Có thể chuyển nguyên biểu thức `bean(*Service)` sang native AspectJ weaving không?  
**Đáp án:** Không. `bean` là extension dành riêng cho Spring AOP tích hợp BeanFactory.

## 13. Tài liệu Spring Framework chính thức

- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `bean(name)`, wildcard `*`, toán tử `&&`/`||`/`!`, Spring-specific instance-level matching.
- [Spring Framework — Bean Overview](https://docs.spring.io/spring-framework/reference/6.2/core/beans/definition.html): bean name, id, alias và naming conventions.
- [Spring Framework — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): đặt tên bean qua `@Bean`.
- [Spring Framework — Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): hạn chế proxy, method execution và self-invocation.
- [Spring Framework — Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): phân biệt semantic qualifier và bean name.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-009">Kết hợp điều kiện bằng &amp;&amp;, || và !</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP Pointcut: kết hợp điều kiện bằng `&&`, `||` và `!`

## 1. Mục tiêu và phạm vi

Khi cấu hình aspect trong **Spring AOP**, một biểu thức pointcut thường cần mô tả nhiều điều kiện cùng lúc: chỉ advice service trong một package, chọn hai nhóm method và loại bỏ phương thức nội bộ. Spring hỗ trợ kết hợp các pointcut expression bằng ba toán tử logic:

- `&&` — **AND (giao)**: tất cả điều kiện phải khớp.
- `||` — **OR (hợp)**: ít nhất một điều kiện khớp.
- `!` — **NOT (phủ định)**: loại bỏ các join point mà điều kiện phía sau khớp.

Ví dụ diễn đạt yêu cầu “advice method công khai thuộc service, có tên bắt đầu bằng create hoặc cancel, nhưng loại bỏ method kết thúc bằng Internal”:

```java
@Before(
    "within(demo.pointcuts.OrderService)"
  + " && execution(public * *(..))"
  + " && (execution(* create*(..)) || execution(* cancel*(..)))"
  + " && !execution(* *Internal(..))"
)
public void beforeOperation(JoinPoint joinPoint) {
    System.out.println("[advice] " + joinPoint.getSignature().getName());
}
```

Đây là fragment bên trong một class `@Aspect`. Các chuỗi được Java nối bằng `+` trước khi Spring phân tích thành một pointcut expression. Điều kiện trên là:

```text
Nằm trong OrderService
         AND
Là phương thức public
         AND
 (tên bắt đầu create OR tên bắt đầu cancel)
         AND
Tên không kết thúc bằng Internal
```

**Phạm vi bài:** đây là cú pháp pointcut được Spring AOP diễn giải bằng ngôn ngữ biểu thức AspectJ. Spring AOP dựa trên **proxy** và chỉ hỗ trợ các *method-execution join points* trên Spring-managed beans; không vì thêm toán tử logic mà trở thành bytecode weaving hay chặn được mọi lời gọi Java.

## 2. Hiểu ý nghĩa AND, OR, NOT ở thời điểm Spring chọn lời gọi

Gọi A, B, C là những điều kiện pointcut đã hợp lệ trên **một method execution mà Spring AOP có thể quan sát**:

| A | B | `A && B` | `A \|\| B` | `!A` |
| --- | --- | --- | --- | --- |
| false | false | false | false | true |
| false | true | false | true | true |
| true | false | false | true | false |
| true | true | true | true | false |

Trong bảng, `A \|\| B` dùng escape trong ô Markdown để không bị hiểu nhầm là dấu phân tách cột; biểu thức pointcut thật sự viết `A || B`.

### 2.1. `&&`: nhiều điều kiện phải cùng đúng

```java
@Pointcut("within(demo.pointcuts.OrderService)"
        + " && execution(public * *(..))")
private void publicOrderServiceMethods() {}
```

Điều này chỉ match method execution được xác định nằm trong `OrderService` **và** là public. Một public method ở service khác không khớp; một private helper cũng không trở thành Spring AOP advised method chỉ vì khai báo cùng class.

Ứng dụng thực tế:

- Giao điều kiện `execution(...)` với `within(package..*)` để giới hạn tầng service.
- Giao method pattern với `@annotation(...)` để chỉ advice những method được đánh dấu.
- Giao `bean(*Service)` với một method pattern khi muốn lọc theo tên Spring bean.

### 2.2. `||`: gom hai nhóm lời gọi

```java
@Pointcut("execution(* create*(..)) || execution(* cancel*(..))")
private void mutatingOperations() {}
```

Các method có tên bắt đầu `create` **hoặc** `cancel` có thể khớp, nếu chúng là những execution mà Spring proxy có thể intercept. Không phải cả hai điều kiện phải đồng thời đúng.

**Lưu ý:** `||` là OR logic, không phải “chạy advice hai lần”. Nếu cả hai vế cùng khớp, **một advice được khai báo một lần** trên biểu thức hợp vẫn chỉ được áp dụng như một advice cho một invocation; việc có nhiều advice độc lập là tình huống khác.

### 2.3. `!`: loại trừ một nhóm

```java
@Pointcut("execution(* demo.pointcuts.OrderService.*(..))"
        + " && !execution(* *Internal(..))")
private void publicApiExceptInternal() {}
```

Trong fragment này, vế phủ định loại những method có tên kết thúc bằng `Internal`. Tên `publicApiExceptInternal` chỉ là ví dụ tên method `@Pointcut`; phần `execution(* ...*)` đầu tiên **không tự giới hạn public**. Muốn chắc chắn chỉ public, thêm `&& execution(public * *(..))`.

Đây là nguyên tắc quan trọng: **phủ định không có nghĩa phép lọc security hoặc một “danh sách cấm tuyệt đối”**. `!B` chỉ là điều kiện logic trên tập những method execution nằm trong phạm vi Spring AOP, và có thể làm tập match rộng hơn nếu không gắn thêm điều kiện dương để giới hạn.

## 3. Dấu ngoặc và độ ưu tiên của toán tử

Trong cú pháp pointcut của AspectJ được Spring hỗ trợ, cách hiểu thông thường của các toán tử logic là:

1. `!` có độ ưu tiên cao nhất.
2. `&&` ưu tiên hơn `||`.
3. `||` có độ ưu tiên thấp nhất.

Vì vậy, các biểu thức sau **khác nhau**:

```text
A || B && C
```

tương đương:

```text
A || (B && C)
```

nhưng không tương đương:

```text
(A || B) && C
```

**Hệ quả dễ mắc:** nếu muốn “(create hoặc cancel) và không phải Internal”, hãy viết:

```java
@Pointcut("(execution(* create*(..)) || execution(* cancel*(..)))"
        + " && !execution(* *Internal(..))")
private void allowedMutations() {}
```

Không nên dùng:

```java
@Pointcut("execution(* create*(..))"
        + " || execution(* cancel*(..))"
        + " && !execution(* *Internal(..))")
private void riskyMutations() {}
```

Vế sau được hiểu là **create OR (cancel AND NOT Internal)**. Method `createInternal()` vẫn khớp do nhánh `create*` ở bên trái `||` được chọn, mặc dù tên method kết thúc `Internal`. Dấu ngoặc tránh biến một điều kiện chung thành điều kiện chỉ áp dụng cho nhánh thứ hai.

### Bảng kiểm tra trường hợp loại trừ Internal

Giả sử `A = create*`, `B = cancel*`, `C = *Internal`:

| Method | A | B | C | `(A \|\| B) && !C` | `A \|\| (B && !C)` |
| --- | --- | --- | --- | --- | --- |
| `createOrder()` | true | false | false | true | true |
| `cancelOrder()` | false | true | false | true | true |
| `createInternal()` | true | false | true | **false** | **true** |
| `cancelInternal()` | false | true | true | false | false |
| `findOrder()` | false | false | false | false | false |

Bảng này mô tả **điều kiện tên method**. Trong ứng dụng Spring AOP, lời gọi còn cần đi qua Spring-managed bean proxy và phù hợp với các điều kiện khác.

**Khuyến nghị:** luôn thêm dấu ngoặc khi phối hợp `||` với `&&` hoặc `!`, ngay cả khi đã nhớ độ ưu tiên. Nó khiến ý định rõ ràng khi review hoặc sửa pointcut sau này.

## 4. Kết hợp các pointcut designator của Spring

Ba toán tử logic được đặt **giữa các biểu thức pointcut hoặc các named pointcut**, không viết vào chữ ký method Java thông thường. Những pointcut designator Spring AOP thường dùng gồm:

| Designator | Điều kiện được diễn giải trong Spring AOP | Ví dụ |
| --- | --- | --- |
| `execution(...)` | Chữ ký method execution | `execution(public * demo.shop..*Service.*(..))` |
| `within(...)` | Loại/type trong phạm vi thực thi | `within(demo.shop.service..*)` |
| `this(...)` | Type của **Spring AOP proxy** | `this(demo.shop.OrderOperations)` |
| `target(...)` | Type của **target object** | `target(demo.shop.OrderService)` |
| `args(...)` | Kiểu runtime của argument | `args(java.lang.String)` |
| `@annotation(...)` | Annotation của method execution | `@annotation(demo.shop.Audited)` |
| `@within(...)`, `@target(...)` | Annotation liên quan type/target | `@within(demo.shop.Secured)` |
| `bean(...)` | Tên Spring bean (phần mở rộng của Spring) | `bean(*Service)` |

Dưới đây là các tổ hợp **minh họa**; class hoặc annotation được nhắc tới phải thật sự tồn tại trong ứng dụng.

**Chọn API service công khai trong package:**

```java
@Pointcut("execution(public * demo.shop.service..*.*(..))"
        + " && !execution(* demo.shop.service..*.healthCheck(..))")
void serviceApiExceptHealthCheck() {}
```

**Chọn method có annotation trong một type cụ thể:**

```java
@Pointcut("within(demo.shop.OrderService)"
        + " && @annotation(demo.shop.Audited)")
void auditedOrderMethod() {}
```

**Chọn một trong hai nhóm operation nhưng không phải bean thử nghiệm:**

```java
@Pointcut("(execution(* *Service.save*(..))"
        + " || execution(* *Service.delete*(..)))"
        + " && !bean(test*Service)")
void productionWriteOperations() {}
```

`bean(...)` là Spring-specific pointcut designator, có thể lọc theo tên bean chứa wildcard. Ví dụ tên `test*Service` chỉ là quy ước; nếu không có bean cùng tên, vế phủ định không loại gì. Đừng dùng logic tên bean làm cơ chế security; pointcut vốn là cách chọn lời gọi advice.

### Chú ý `this` và `target` khi phối hợp

Với JDK dynamic proxy:

- `this(demo.shop.OrderOperations)` có thể khớp vì proxy triển khai interface.
- `this(demo.shop.OrderService)` có thể **không** khớp nếu proxy JDK không phải subclass của implementation.
- `target(demo.shop.OrderService)` có thể khớp vì target object là implementation class.

Do đó, `execution(...) && this(...)` và `execution(...) && target(...)` có thể chọn tập method khác nhau. Nên viết pointcut theo interface/target một cách chủ đích và test trên **loại proxy thực tế**.

## 5. Named pointcut: cách tổ chức biểu thức phức tạp

Spring khuyến nghị ghép các pointcut nhỏ, được đặt tên rõ ràng. Thay vì một chuỗi `@Before` dài khó bảo trì, tạo những method `@Pointcut` **không có logic Java**:

```java
@Aspect
@Component
public class PolicyAspect {
    @Pointcut("within(demo.pointcuts.OrderService)")
    private void orderServiceType() {}

    @Pointcut("execution(public * *(..))")
    private void publicMethod() {}

    @Pointcut("execution(* create*(..))")
    private void createOperation() {}

    @Pointcut("execution(* cancel*(..))")
    private void cancelOperation() {}

    @Pointcut("execution(* *Internal(..))")
    private void internalOperation() {}

    @Pointcut("orderServiceType() && publicMethod()")
    private void servicePublicMethod() {}

    @Before("servicePublicMethod()"
          + " && (createOperation() || cancelOperation())"
          + " && !internalOperation()")
    public void before(JoinPoint jp) {
        System.out.println("[advice] " + jp.getSignature().getName());
    }
}
```

Đây là fragment class, chưa gồm imports/package; ví dụ đầy đủ ở mục 7.

Cách đọc:

- `servicePublicMethod()` là một **named pointcut**, không phải Java method thực sự chạy khi business method được gọi.
- `createOperation() || cancelOperation()` là **hợp hai tập match**.
- `!internalOperation()` **loại** cả `createInternal()` và `cancelInternal()`.
- Advice `before(...)` chỉ chạy khi toàn bộ điều kiện cuối match một invocation qua Spring AOP proxy.

Khi tham chiếu named pointcut ở **cùng aspect class**, cú pháp `servicePublicMethod()` là đủ. Khi chia sẻ từ class khác, dùng tên fully qualified phù hợp và đảm bảo method pointcut có **visibility Java** cho phép tham chiếu (ví dụ public). Visibility của method `@Pointcut` chỉ điều khiển khả năng tham chiếu nó trong source, không làm tập method matched thay đổi.

Nếu biểu thức có tham số binding như `args(orderId)` hay `@annotation(audited)`, pointcut method và advice method cần khai báo/bind tên tham số tương thích; toán tử logic không tự truyền Java method arguments vào advice. Ví dụ minh họa:

```java
@Pointcut("execution(* demo.shop.OrderService.submit(..)) && args(orderId)")
private void submitOrder(String orderId) {}

@Before("submitOrder(orderId)")
public void beforeSubmit(String orderId) {
    System.out.println(orderId);
}
```

Đây là fragment, cần class `demo.shop.OrderService` và cấu hình biên dịch giữ parameter names hoặc khai báo tên binding rõ ràng khi cần. Với `||` và parameter binding, hai nhánh phải thống nhất binding cần thiết; không tham chiếu biến chỉ có ở một nhánh rồi giả định nhánh kia cũng truyền biến đó.

## 6. Phạm vi mà toán tử logic **không** thay đổi

### 6.1. Chỉ method execution trên Spring AOP proxy

Dù biểu thức là `A && B`, `A || B` hay `!A`, Spring AOP vẫn chỉ thấy các method-execution join points có thể intercept bằng proxy. Các pointcut designator như `call`, `get`, `set`, `initialization`, `cflow` không được Spring AOP proxy hỗ trợ như trong full AspectJ weaving.

Một biểu thức phủ định như `!execution(* privateHelper(..))` **không** biến private method thành advised method: Spring proxy đã không thể intercept lời gọi đó từ đầu. Tương tự, static calls không đi qua instance proxy.

### 6.2. Self-invocation vẫn bypass advice

```java
@Service
class OrderService {
    public void runBatch() {
        this.createOrder(); // Gọi trực tiếp target, bypass Spring AOP proxy.
    }

    public void createOrder() {
        // ...
    }
}
```

Đây là fragment minh họa. Dù `createOrder()` phù hợp `execution(* create*(..))`, lời gọi `this.createOrder()` bên trong target không đi qua proxy. Việc mở rộng `||` hoặc phủ định `!` không giải quyết được self-invocation. Nên đặt boundary ở một Spring bean khác và inject dependency nếu operation thực sự cần advice riêng.

### 6.3. Không tạo proxy chỉ vì viết `@Pointcut`

Method `@Pointcut` định nghĩa **điều kiện**. Để có proxy/interceptor còn cần:

1. Spring quản lý target bean.
2. Aspect được đăng ký thành Spring bean (ví dụ `@Aspect @Component`).
3. Hạ tầng Spring AOP được bật: `@EnableAspectJAutoProxy` trong Spring Framework thuần hoặc auto-configuration thích hợp.
4. Có một advice thực sự tham chiếu pointcut và phù hợp target.
5. Consumer gọi bean thông qua **tham chiếu proxy**.

Nếu một trong các yếu tố đó thiếu, việc viết biểu thức logic hợp lệ vẫn có thể không tạo kết quả quan sát được.

## 7. Dự án Java 17 chạy được: chứng minh `&&`, `||`, `!` bằng log

Ví dụ chạy bằng **Java 17, Maven và Spring Framework 6.2.x** (không cần Spring Boot). Có một Spring-managed `OrderService` với những method có tên khác nhau; Spring tự tạo AOP proxy khi advisor match. Aspect dùng named pointcuts để kết hợp các điều kiện.

### 7.1. Cấu trúc thư mục

```text
spring-pointcut-operators/
├── pom.xml
└── src/main/java/demo/pointcuts/
    ├── AppConfig.java
    ├── OrderService.java
    ├── PolicyAspect.java
    └── Main.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-operators</artifactId>
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

`spring-context` cung cấp IoC/annotation context, `spring-aop` hỗ trợ proxy/advisor, và `aspectjweaver` giúp Spring phân tích các biểu thức @AspectJ. Không cần áp dụng AspectJ compile-time hay load-time weaving trong ví dụ.

### 7.3. `AppConfig.java`

```java
package demo.pointcuts;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

`@EnableAspectJAutoProxy` bật hạ tầng AOP; `@ComponentScan` phát hiện service/aspect. Target ví dụ không khai báo interface nên Spring sẽ chọn class-based proxy cho nó. Việc dùng `&&`/`||`/`!` là độc lập với lựa chọn JDK proxy hay CGLIB, nhưng lời gọi được intercept vẫn phải đi qua proxy.

### 7.4. `OrderService.java`

```java
package demo.pointcuts;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public void createOrder() {
        System.out.println("[service] createOrder");
    }

    public void cancelOrder() {
        System.out.println("[service] cancelOrder");
    }

    public void createInternal() {
        System.out.println("[service] createInternal");
    }

    public void findOrder() {
        System.out.println("[service] findOrder");
    }

    public void batch() {
        System.out.println("[service] batch");
        this.createOrder(); // Self-invocation: không qua AOP proxy.
    }
}
```

Nhóm method: `createOrder` và `cancelOrder` được phép; `createInternal` bị phủ định; `findOrder` không thuộc create/cancel; `batch` dùng để minh họa self-invocation.

### 7.5. `PolicyAspect.java`

```java
package demo.pointcuts;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PolicyAspect {
    @Pointcut("within(demo.pointcuts.OrderService)")
    private void inOrderService() {}

    @Pointcut("execution(public * *(..))")
    private void publicMethods() {}

    @Pointcut("execution(* create*(..))")
    private void createMethods() {}

    @Pointcut("execution(* cancel*(..))")
    private void cancelMethods() {}

    @Pointcut("execution(* *Internal(..))")
    private void internalMethods() {}

    @Pointcut("inOrderService() && publicMethods()")
    private void orderPublicApi() {}

    @Before("orderPublicApi()"
          + " && (createMethods() || cancelMethods())"
          + " && !internalMethods()")
    public void beforeAllowedOperation(JoinPoint joinPoint) {
        System.out.println("[advice] "
                + joinPoint.getSignature().getName());
    }
}
```

Spring gọi advice khi method execution **thuộc `OrderService`**, **public**, tên **bắt đầu create hoặc cancel**, và **không kết thúc bằng Internal**.

Các method `@Pointcut` không được tự gọi như logic Java nghiệp vụ. Chúng là các định nghĩa mang tên để pointcut expression tham chiếu. `@Component` đăng ký aspect bean; nếu chỉ có `@Aspect` mà không đăng ký bean, Spring component scan không nhất thiết phát hiện aspect.

### 7.6. `Main.java`

```java
package demo.pointcuts;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orders = context.getBean(OrderService.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(orders));
            orders.createOrder();
            orders.cancelOrder();
            orders.createInternal();
            orders.findOrder();
            orders.batch();
        }
    }
}
```

Lệnh chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.pointcuts.Main
```

**Dòng stdout do chương trình in** (bỏ qua log của Maven hoặc Spring nếu có):

```text
isAopProxy=true
[advice] createOrder
[service] createOrder
[advice] cancelOrder
[service] cancelOrder
[service] createInternal
[service] findOrder
[service] batch
[service] createOrder
```

Giải thích chính xác:

1. `isAopProxy=true` cho thấy Spring cấp proxy khi advisor áp dụng cho các method.
2. `createOrder()` match `createMethods()`, không match `internalMethods()`; advice chạy trước service.
3. `cancelOrder()` match vế `cancelMethods()`; advice chạy trước service.
4. `createInternal()` match `createMethods()` **nhưng** cũng match `internalMethods()`, nên `!internalMethods()` là false; không có advice.
5. `findOrder()` không match vế `create || cancel`; không có advice.
6. `batch()` không match `create/cancel`; không có advice cho chính method `batch()`.
7. Bên trong `batch()`, target gọi `this.createOrder()` trực tiếp; dù chữ ký method có thể khớp, **không đi qua Spring AOP proxy**, nên lần `createOrder()` này không in dòng advice.

### 7.7. Thử nghiệm: bỏ ngoặc gây match sai

Giữ các named pointcuts như trên, thay biểu thức advice bằng:

```java
@Before("orderPublicApi() && createMethods()"
      + " || cancelMethods() && !internalMethods()")
```

Đây là **fragment thay thế annotation**; trong thực tế nên nhóm các điều kiện chung lại để không vô tình mở rộng phạm vi. Vế `orderPublicApi()` không còn áp dụng cho toàn bộ nhánh `cancelMethods()`. Điều kiện được hiểu theo kiểu:

```text
(orderPublicApi AND createMethods)
OR
(cancelMethods AND NOT internalMethods)
```

Thậm chí `createInternal()` có thể được advice trở lại qua nhánh create vì `!internalMethods()` chỉ còn kiểm soát nhánh cancel. Nếu có bean khác trong context với method `cancel*`, vế thứ hai còn có nguy cơ chọn cả bean đó khi không bị khóa bởi `inOrderService()`.

Sửa bằng cách luôn đặt **toàn bộ phần OR trong ngoặc** rồi mới giao với các điều kiện chung:

```java
@Before("orderPublicApi()"
      + " && (createMethods() || cancelMethods())"
      + " && !internalMethods()")
```

### 7.8. Thử nghiệm: thay phủ định bằng điều kiện dương sai

Đổi `!internalMethods()` thành `internalMethods()`:

```java
@Before("orderPublicApi()"
      + " && (createMethods() || cancelMethods())"
      + " && internalMethods()")
```

Khi đó chỉ method tên create/cancel **và** kết thúc bằng Internal mới match. Với service trên, `createInternal()` được advice, còn `createOrder()` và `cancelOrder()` bị loại. Đây là cách kiểm chứng ý nghĩa phủ định của `!`, không phải lỗi Spring proxy.

## 8. Viết biểu thức trong annotation, XML và cấu hình khác

### 8.1. Trong Java annotation

```java
@Before("execution(* demo.pointcuts..*Service.create*(..))"
      + " && !execution(* demo.pointcuts..*Service.createInternal(..))")
public void beforeCreate() {
    // Advice.
}
```

`&&`, `||`, `!` được đặt **trong Java String** như trên. Không viết `&amp;&amp;` trong Java string; đó là escape của XML/HTML, không phải cú pháp cần cho annotation.

### 8.2. Trong Spring AOP XML

Ví dụ **fragment** AOP namespace:

```xml
<aop:config>
    <aop:pointcut
        id="safeCreate"
        expression="execution(* demo.pointcuts..*Service.create*(..)) and
                    !execution(* demo.pointcuts..*Service.createInternal(..))"/>
</aop:config>
```

Trong XML, dấu `&` là ký tự đặc biệt trong **attribute**, nên nếu dùng ký hiệu `&&` thì phải ghi `&amp;&amp;` để XML parser hiểu đúng:

```xml
<aop:pointcut
    id="safeCreate"
    expression="execution(* demo.pointcuts..*Service.create*(..))
                &amp;&amp; !execution(* demo.pointcuts..*Service.createInternal(..))"/>
```

Đây là các fragment, không phải file XML Spring application context hoàn chỉnh. Viết `and` và `or` như các từ khóa logic thường dễ đọc hơn trong XML, nhưng cần phân biệt biến thể cú pháp của nơi cấu hình. XML chưa được escape `&` có thể gây lỗi parse trước khi Spring AOP kịp xét pointcut.

### 8.3. Biểu thức được tạo từ properties/config text

Nếu application đọc một expression từ cấu hình riêng để cấp vào API Spring AOP, phải biết **lớp parser** nào nhận expression và cách escape của định dạng bao ngoài (XML, YAML, Java, properties). Toán tử pointcut khác với cú pháp ngôn ngữ của file chứa nó. Không coi một chuỗi hợp lệ trong `@Before(...)` đương nhiên là literal hợp lệ trong mọi loại file.

## 9. Những lỗi thường gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `createInternal()` vẫn được advice dù có `!internalMethods()` | Viết `A || B && !C` thay vì `(A || B) && !C` | Nhóm OR bằng dấu ngoặc |
| Advice chạy trên bean ngoài phạm vi mong muốn | `within(...)` đặt chỉ trong một nhánh OR | Đưa điều kiện phạm vi **ra ngoài** nhóm OR và nối bằng `&&` |
| Không có advice cho mọi method | Dùng `&&` giữa các điều kiện loại trừ nhau | Cân nhắc `||` khi ý định là hợp |
| Match quá nhiều method | Chỉ viết `!execution(...)` | Kèm điều kiện dương `execution(...) && within(...)` để giới hạn |
| Pointcut expression hợp lệ nhưng không có proxy | Aspect chưa đăng ký Spring bean hoặc không advisor nào khớp | Kiểm tra `@Aspect @Component`, auto-proxy và AOP advisor |
| XML báo lỗi parse `&` | Ghi `&&` nguyên văn trong attribute | Đổi sang `&amp;&amp;` hoặc dùng biểu thức từ khóa phù hợp |
| Spring ném lỗi unsupported pointcut | Dùng `call`, `get`, `set`, `cflow` hoặc designator ngoài phạm vi | Dùng designator được Spring AOP hỗ trợ, thường là `execution` |
| Method `this.createOrder()` không có advice | Self-invocation bypass proxy | Tách method cần advice sang Spring bean khác |
| Method private/static được pointcut “match trên giấy” | Spring proxy không intercept lời gọi đó | Chuyển policy sang public method trên bean proxy |
| `this(ImplementationClass)` không khớp với JDK proxy | `this` kiểm tra proxy, không phải target | Dùng interface phù hợp, hoặc `target(ImplementationClass)` có cân nhắc |
| `@annotation(...)` không khớp như dự kiến | Annotation đặt khác vị trí hoặc kiểu method/proxy ảnh hưởng lookup | Kiểm tra annotation trên method thực thi và test integration |
| Đổi tên named pointcut nhưng advice không chạy | Method tham chiếu sai tên/signature/visibility | Đảm bảo `@Pointcut` tồn tại, đúng tên và có thể tham chiếu |

**Checklist rà soát khi viết pointcut ghép:**

1. Ghi yêu cầu nghiệp vụ thành câu: “chỉ trong …; chọn A hoặc B; loại C”.
2. Viết theo cấu trúc `SCOPE && (A || B) && !C`, thay vì dồn toàn bộ vào một chuỗi chưa nhóm.
3. Khi điều kiện dài, đặt tên `@Pointcut` nhỏ, rõ và dùng lại được.
4. Kiểm tra thứ tự ưu tiên bằng bảng ví dụ **được chọn / bị loại** ít nhất 4–5 phương thức.
5. Bảo đảm target/aspect đã được Spring quản lý; caller gọi thông qua proxy.
6. Kiểm tra giới hạn JDK/CGLIB, self-invocation và method visibility.
7. Trong XML, escape ký tự `&` và kiểm tra định dạng file trước khi debug logic AOP.
8. Thêm integration test xác nhận method mục tiêu có advice, method bị phủ định không có advice.

## 10. Câu hỏi ôn tập

**1. Khác biệt giữa `A && B` và `A || B`?** Giao yêu cầu cả hai đúng, hợp yêu cầu ít nhất một đúng trên method execution được xét.

**2. `!internalMethods()` có làm Spring chặn private methods không?** Không. Nó chỉ phủ định điều kiện lọc; khả năng intercept phụ thuộc Spring proxy và phạm vi method-execution join point.

**3. `A || B && !C` có giống `(A || B) && !C` không?** Không. Biểu thức đầu được hiểu như `A || (B && !C)`; nhánh A thoát khỏi điều kiện phủ định C.

**4. Vì sao dùng named pointcuts?** Để tách điều kiện nhỏ, tái sử dụng, tránh lặp chuỗi và làm điểm giao/hợp/phủ định rõ ràng.

**5. Viết `&&` trong XML attribute như thế nào?** Cần escape thành `&amp;&amp;`, hoặc dùng dạng biểu thức từ khóa phù hợp; trong Java string dùng `&&` trực tiếp.

**6. Có thể dùng `@Before("A() || B()")` để chạy hai lần advice nếu cả A, B khớp không?** Không. Một advice declaration dùng một biểu thức OR được xét như một điều kiện match; nếu match thì áp dụng advice đó cho invocation.

**7. Vì sao `this.createOrder()` không chạy advice dù thỏa mọi điều kiện tên method?** Vì lời gọi nội bộ trực tiếp trên target bỏ qua Spring AOP proxy.

## Tài liệu Spring chính thức

- [Spring Framework — Declaring a Pointcut, Combining Pointcut Expressions](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Pointcut API (union/intersection)](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-010">Mẫu tên, kiểu con, overload và danh sách tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu tên, kiểu con, overload và danh sách tham số trong Spring AOP pointcut

## 1. Mục tiêu bài học

Trong Spring Framework, một `@Aspect` có thể sử dụng biểu thức `execution(...)` để chọn những **method execution** cụ thể trên các **Spring-managed beans**. Khi một service chứa nhiều phương thức có tên tương tự, có overload, được triển khai qua interface/class hierarchy và có danh sách tham số khác nhau, việc viết biểu thức chính xác quan trọng hơn việc chỉ dùng một wildcard thật rộng.

Bài học tập trung vào bốn kỹ thuật chọn phương thức:

1. **Mẫu tên**: tên chính xác, tiền tố, hậu tố hoặc wildcard `*` trong tên method.
2. **Kiểu con**: ký hiệu `+` sau **type pattern** để tính cả type đó và các subtype/implementation phù hợp.
3. **Overload**: phân biệt các method **cùng tên** dựa trên **số lượng và kiểu tham số được khai báo**.
4. **Danh sách tham số**: phân biệt `()`, `(*)`, `(..)`, và các danh sách hỗn hợp như `(java.lang.String,int)`, `(java.lang.String,..)`.

Sau bài này, bạn có thể tự dự đoán một `@Before` advice có chạy hay không, thay vì sửa pointcut bằng cách đoán. Bạn cũng sẽ biết tại sao **giá trị argument ở runtime**, **phép overload resolution lúc biên dịch Java** và **Spring AOP proxy invocation** là ba phần khác nhau.

**Giới hạn nội dung:** Chỉ học cách viết và kiểm chứng các pointcut trong **Spring AOP dựa trên proxy**, dùng annotation style `@Aspect`, không học các kiểu AspectJ weaving ngoài Spring. Ví dụ dùng **Java 17+**, **Spring Framework 6.2.x** và Maven.

## 2. Điều kiện để pointcut thực sự kích hoạt advice

`execution(...)` đối chiếu **chữ ký method**; lời gọi chỉ được advice khi bean và aspect đã được Spring đăng ký, hạ tầng AOP hoạt động và caller đi qua **Spring proxy**. Một chữ ký khớp vẫn không tạo advice nếu gọi trực tiếp đối tượng tạo bằng `new`, gọi `this.method()` nội bộ, hoặc proxy không thể chặn method đó (như `private`/`final` trong class-based proxy). JDK proxy chỉ expose những method trên interface của proxy.

Khi thử các mẫu tên/kiểu con/overload bên dưới, luôn đối chiếu **ba câu hỏi riêng**: Java đã chọn chữ ký nào, expression có khớp chữ ký ấy không, và invocation có đi qua proxy không? Spring AOP giới hạn ở method execution của Spring-managed beans.

## 3. Nhắc nhanh cú pháp `execution(...)`

Dạng rút gọn: `execution(modifiers? return-type declaring-type?.method-name(parameter-pattern))`; đây là ký hiệu mô tả, **dấu `?` không phải ký tự nhập vào biểu thức**. Ví dụ:

~~~java
@Before("execution(public java.lang.String demo.TaskApi.lookup(java.lang.String))")
public void beforeLookup() {
    // Khớp method public lookup(String) trả về String trên TaskApi.
}
~~~

Đoạn trên nằm trong một `@Aspect` được đăng ký. `*` ở vị trí tên/kiểu và `..` trong phần package/tham số có ý nghĩa khác nhau; các ví dụ tiếp theo lần lượt khai triển từng vị trí. Chú ý `TaskApi+` ở phần **type pattern** để diễn tả cả subtype, còn `(*)` và `(..)` trong **parameter pattern** có ranh giới số tham số khác nhau.

## 4. Mẫu tên phương thức: exact, prefix, suffix và wildcard

### 4.1. Tên chính xác

~~~text
execution(* demo.TaskApi.lookup(..))
~~~

Chọn những method execution tên **lookup** phù hợp với TaskApi; dấu `(..)` cho phép mọi số lượng và kiểu tham số. Nếu TaskApi có ba overload lookup, cả ba có thể được chọn.

### 4.2. Tiền tố

~~~text
execution(* demo.TaskApi.list*(..))
~~~

Tên bắt đầu bằng **list**, ví dụ `listAll` và `listByOwner`. Method `lookup` hoặc `inspect` không khớp chỉ vì nằm cùng bean.

### 4.3. Hậu tố

~~~text
execution(* demo.TaskApi.*Status(..))
~~~

Tên kết thúc bằng **Status**, ví dụ `getStatus`, `updateStatus`. Không chọn `statusCode` vì hậu tố không đúng.

### 4.4. Có ký tự bất kỳ ở giữa

~~~text
execution(* demo.TaskApi.find*By*(..))
~~~

Chọn tên có phần đầu **find**, tiếp theo một đoạn bất kỳ, rồi **By**, tiếp theo một đoạn bất kỳ. Chẳng hạn `findOrderById` phù hợp, nhưng `findAll` thì không.

### 4.5. Mọi tên method

~~~text
execution(* demo.TaskApi.*(..))
~~~

Phần `*` tại **name-pattern** bỏ ràng buộc tên. Điều này dễ dùng để debug nhưng có thể khiến rất nhiều method bị advice khi đem vào ứng dụng thật.

**Cách đọc an toàn:** viết type và method name tách biệt: `demo.TaskApi.list*(..)` là một type cụ thể kết hợp prefix method, không có nghĩa Spring quét toàn bộ package `demo` theo tên bean.

## 5. Phạm vi package và các cấp thư mục package

Điểm hay gây nhầm nhất là phân biệt `.` và `..` trong phần type.

~~~text
execution(* demo.service.*.*(..))
~~~

Mô tả method trên các type **trực tiếp thuộc package** `demo.service`.

~~~text
execution(* demo.service..*.*(..))
~~~

Mở rộng xuống **demo.service và các subpackage** như `demo.service.internal`, `demo.service.payment` khi các type/method thực sự phù hợp.

| Type thực tế | `demo.service.*` | `demo.service..*` |
| --- | --- | --- |
| `demo.service.OrderService` | Khớp phạm vi type | Khớp phạm vi type |
| `demo.service.billing.BillingService` | Không | Khớp phạm vi type |
| `demo.repository.OrderRepository` | Không | Không |
| `demo.service_internal.LegacyService` | Không | Không |

Bảng chỉ đối chiếu **type-pattern**, chưa nói tới việc bean tương ứng có proxy/advisor hay không.

Đừng nhầm với biểu thức bean name. Ví dụ `bean(*Service)` là **pointcut designator riêng của Spring** xét **tên bean**, không phải một phần của `execution` và không suy ra package class.

## 6. Kiểu con: vai trò của dấu + sau type pattern

Trong type pattern kiểu AspectJ mà Spring AOP sử dụng, hậu tố **`+`** biểu thị **type được ghi trong biểu thức hoặc bất kỳ subtype phù hợp của nó**, bao gồm subclass và implementation của interface.

Ví dụ:

~~~text
demo.TaskApi+
~~~

Được đọc là `TaskApi` **và các subtype/implementation** của TaskApi, trong phạm vi ngữ nghĩa pointcut. Ký hiệu này khác hoàn toàn dấu `+` để nối String của Java: trong annotation, nó chỉ là ký tự bên trong biểu thức Spring AOP.

Khi kết hợp với execution:

~~~text
execution(* demo.TaskApi+.lookup(..))
~~~

ý định là chọn những execution phù hợp của method **lookup** trên TaskApi hoặc type con tương ứng. Cách này hữu ích khi:

- nhiều implementation của cùng một interface được đăng ký thành các bean khác nhau;
- một interface con khai báo thêm method;
- một base class được mở rộng bởi nhiều service class;
- muốn đặt một quy ước pointcut ở mức hợp đồng thay vì liệt kê từng implementation.

**Không phải mọi điểm matching đều tự động chứng minh có proxy.** Nếu Spring dùng JDK dynamic proxy, những method muốn được gọi qua proxy cần được công bố trong interface được proxy triển khai. Nếu dùng CGLIB, method vẫn phải overridable và được gọi qua proxy.

### 6.1. Phân biệt hai pattern

~~~text
execution(* demo.TaskApi.lookup(..))
execution(* demo.TaskApi+.lookup(..))
~~~

Dòng đầu tập trung vào type được đặt tên `TaskApi`. Dòng thứ hai thể hiện rõ chủ đích bao gồm type con. Trong Spring AOP, một execution biểu diễn bởi interface cũng có thể áp dụng cho method được triển khai trong concrete class; do đó **không nên khẳng định hai expression lúc nào cũng cho kết quả khác nhau** chỉ dựa vào dấu `+`. Với các hierarchy phức tạp, kiểm chứng thực tế qua Spring-managed proxy và các method mong muốn là cần thiết.

### 6.2. Dùng + ở pattern của tham số

Giả sử:

~~~java
package demo;

public class Payload {
}

public class UrgentPayload extends Payload {
}
~~~

Đây là **hai class minh họa đặt trong hai file riêng**, vì Java chỉ cho một public top-level class tương ứng mỗi file. Nếu service có hai overload:

~~~java
void update(Payload payload);
void update(UrgentPayload payload);
~~~

thì:

~~~text
execution(* demo.TaskApi.update(demo.Payload+))
~~~

có thể chọn các chữ ký với một tham số có kiểu là **Payload hoặc subtype của Payload**. `Payload+` là **type pattern cho declared parameter type**, không phải “chỉ match khi argument runtime là một subtype”.

### 6.3. So sánh với target(...) và this(...)

Các pointcut designator khác có thể chọn theo đối tượng hiện diện tại invocation:

~~~text
target(demo.TaskApi)
this(demo.TaskApi)
~~~

Trong Spring AOP, **target(...)** xét target object, **this(...)** xét proxy object. Đây không phải cú pháp chọn một overloaded method theo chữ ký; thường kết hợp chúng với execution khi có yêu cầu cụ thể.

**Không thay `Payload+` bằng `args(Payload)` một cách tùy tiện**: `args` quan tâm **kiểu argument tại runtime**, trong khi **execution** quan tâm chữ ký method. Hai phương thức với cùng argument runtime có thể match pointcut execution khác nhau do overload được chọn từ trước.

## 7. Danh sách tham số: (), (*), (..) và hỗn hợp

Đây là nhóm quy tắc cần nhớ chính xác:

| Parameter pattern | Ý nghĩa |
| --- | --- |
| `()` | **Không có** tham số |
| `(*)` | **Đúng một** tham số, kiểu bất kỳ |
| `(..)` | **0 hoặc nhiều** tham số, kiểu bất kỳ |
| `(java.lang.String)` | Đúng một tham số, declared type String |
| `(long)` | Đúng một tham số primitive long |
| `(java.lang.String,int)` | Đúng hai tham số, đúng thứ tự String rồi primitive int |
| `(*,java.lang.String)` | Đúng hai tham số, đầu tùy kiểu, thứ hai String |
| `(java.lang.String,..)` | Tham số đầu String; phía sau có thể 0 hoặc nhiều tham số |
| `(..,int)` | Tham số cuối int; trước đó có thể 0 hoặc nhiều tham số |

Ví dụ **TaskApi** có:

~~~java
String lookup(String code);
String lookup(long id);
String lookup(String code, int version);
~~~

Đối chiếu:

| Pattern | lookup(String) | lookup(long) | lookup(String,int) |
| --- | --- | --- | --- |
| `lookup()` | Không | Không | Không |
| `lookup(*)` | Có | Có | Không |
| `lookup(..)` | Có | Có | Có |
| `lookup(java.lang.String)` | Có | Không | Không |
| `lookup(long)` | Không | Có | Không |
| `lookup(java.lang.String,int)` | Không | Không | Có |
| `lookup(java.lang.String,..)` | Có | Không | Có |
| `lookup(*,int)` | Không | Không | Có |
| `lookup(..,int)` | Không | Không | Có |

**Thứ tự parameter là quan trọng.** `(String,int)` khác `(int,String)`. Nếu method được khai báo `lookup(String,int)`, biểu thức đảo thứ tự sẽ không khớp method ấy.

**Primitive khác wrapper.** `lookup(long)` không có declared parameter type `java.lang.Long`, dù Java tự động boxing/unboxing ở điểm gọi khác nhau. Muốn match chính xác theo chữ ký, dùng kiểu được khai báo.

### 7.1. Dấu hai chấm? Không, dấu hai chấm không có ý nghĩa wildcard

Đôi khi người học viết `lookup(...)` giống ký hiệu varargs của Java. Trong AspectJ pointcut:

~~~text
lookup(..)
~~~

mới là mẫu cho **bất kỳ số lượng tham số**. Không thêm dấu chấm thứ ba để chọn mọi tham số.

### 7.2. Dùng (*) không giống dùng (..)

Nếu service có `void refresh()`, pattern `refresh(*)` **không** match, còn `refresh(..)` có. Tương tự, với `refresh(String,int)`, `refresh(*)` không match vì có hai tham số.

Một lời khuyên thực hành: nếu muốn áp dụng advice cho **mọi overload** cùng tên, bắt đầu bằng `(..)`; nếu cần phân biệt overload, ghi từng kiểu hoặc đúng số lượng.

### 7.3. Varargs

Method Java:

~~~java
public void publish(String... events) {
    // ...
}
~~~

thực chất có kiểu tham số array `String[]` trong method signature. Pattern `publish(..)` có thể bao quát method này; nhưng đừng diễn giải nó là “chỉ varargs”. Nếu cần chọn đúng kiểu array, nghiên cứu và thử type pattern `java.lang.String[]` tương ứng.

Các fragment trong mục này **chỉ minh họa chữ ký**, không thay thế source files của dự án runnable bên dưới.

## 8. Overload: Spring AOP không lựa chọn overload thay Java

**Overloading** là nhiều method cùng tên, khác danh sách parameter. Ví dụ:

~~~java
void update(Payload payload);
void update(UrgentPayload payload);
~~~

Khi Java biên dịch:

~~~java
Payload value = new UrgentPayload();
service.update(value);
~~~

Overload **update(Payload)** được chọn bởi **kiểu compile-time của biến `value`** (giả sử những overload trên), dù runtime object là **UrgentPayload**. Spring AOP không thay Java overload resolution.

Ngược lại:

~~~java
service.update(new UrgentPayload());
~~~

biểu thức argument có compile-time type **UrgentPayload**, nên Java chọn overload **update(UrgentPayload)**.

Điểm quan trọng:

- **execution(...update(demo.Payload))** chú ý **declared parameter type** của method `update(Payload)`.
- **execution(...update(demo.UrgentPayload))** chú ý **declared parameter type** của `update(UrgentPayload)`.
- **execution(...update(demo.Payload+))** bao gồm cả các declared type là Payload và subtype tương ứng.
- **args(demo.UrgentPayload)** là điều kiện kiểu **argument runtime** trong Spring AOP; không đồng nghĩa việc Java đã chọn overload `update(UrgentPayload)`.

Một instance UrgentPayload có thể được truyền tới method `update(Payload)`. Do đó, một pointcut dùng **args** có thể quan sát type runtime UrgentPayload trong invocation tới signature `update(Payload)`, trong khi expression execution ràng buộc riêng signature vẫn nhận diện nó là `update(Payload)`.

Đây là nguyên nhân nhiều lỗi “pointcut không chọn đúng overload” thực ra đến từ việc hiểu nhầm kiểu biến Java khi gọi.

## 9. Ví dụ Maven chạy được: mẫu tên, kiểu con, overload và tham số

Ta tạo một ứng dụng Spring Framework thuần có một service triển khai **interface con** và hai overload **update** với kiểu cha/con. Aspect định nghĩa nhiều `@Before` pointcut **không chồng nhau** trên cùng một lời gọi trong kịch bản chính, để stdout có thứ tự dễ kiểm tra.

Yêu cầu môi trường:

- **JDK 17+**
- **Maven 3.9+**
- **Spring Framework 6.2.12**
- **AspectJ Weaver 1.9.24** trên classpath để Spring phân tích các annotation/pointcut.

Cấu trúc thư mục:

~~~text
spring-aop-signature-patterns/
├── pom.xml
└── src/main/java/demo/
    ├── TaskApi.java
    ├── ExtendedTaskApi.java
    ├── Payload.java
    ├── UrgentPayload.java
    ├── TaskService.java
    ├── PatternAspect.java
    ├── AppConfig.java
    └── App.java
~~~

### 9.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-signature-patterns</artifactId>
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

`spring-context` cung cấp ApplicationContext và kéo theo Spring AOP. `aspectjweaver` là dependency cần thiết cho cú pháp `@AspectJ` trong Spring AOP; project **không kích hoạt AspectJ weaving**.

### 9.2. Interface cha và interface con

**src/main/java/demo/TaskApi.java**

~~~java
package demo;

public interface TaskApi {
    void listAll();
    void listByOwner(String owner);

    String lookup(String code);
    String lookup(long id);
    String lookup(String code, int version);

    void update(Payload payload);
    void update(UrgentPayload payload);

    void inspect();
}
~~~

**src/main/java/demo/ExtendedTaskApi.java**

~~~java
package demo;

public interface ExtendedTaskApi extends TaskApi {
    void extra();
}
~~~

**TaskApi** định nghĩa ba overload `lookup`, hai overload `update` và hai method bắt đầu bằng **list**. **ExtendedTaskApi** là subtype của TaskApi và bổ sung `extra()`.

Nhờ `ExtendedTaskApi extends TaskApi`, type pattern `demo.TaskApi+` mô tả cả hợp đồng TaskApi và những subtype phù hợp. Không nên nhầm **inheritance của interface** với **component scanning**: Spring vẫn cần bean implementation.

### 9.3. Kiểu tham số cha/con

**src/main/java/demo/Payload.java**

~~~java
package demo;

public class Payload {

    private final String code;

    public Payload(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
~~~

**src/main/java/demo/UrgentPayload.java**

~~~java
package demo;

public class UrgentPayload extends Payload {

    public UrgentPayload(String code) {
        super(code);
    }
}
~~~

`UrgentPayload` kế thừa `Payload`, không override code(). Đây là hierarchy đơn giản để kiểm tra pointcut **Payload+** ở vị trí tham số. Không cần dùng annotation tùy chỉnh hoặc framework đặc biệt để tạo quan hệ subtype.

### 9.4. Service implementation

**src/main/java/demo/TaskService.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class TaskService implements ExtendedTaskApi {

    @Override
    public void listAll() {
        System.out.println("TARGET listAll");
    }

    @Override
    public void listByOwner(String owner) {
        System.out.println("TARGET listByOwner " + owner);
    }

    @Override
    public String lookup(String code) {
        System.out.println("TARGET lookup(String) " + code);
        return code;
    }

    @Override
    public String lookup(long id) {
        System.out.println("TARGET lookup(long) " + id);
        return Long.toString(id);
    }

    @Override
    public String lookup(String code, int version) {
        System.out.println("TARGET lookup(String,int) " + code + "/" + version);
        return code + ":" + version;
    }

    @Override
    public void update(Payload payload) {
        System.out.println("TARGET update(Payload) " + payload.code());
    }

    @Override
    public void update(UrgentPayload payload) {
        System.out.println("TARGET update(UrgentPayload) " + payload.code());
    }

    @Override
    public void extra() {
        System.out.println("TARGET extra");
    }

    @Override
    public void inspect() {
        System.out.println("TARGET inspect");
        this.listAll();
    }
}
~~~

Khai báo `@Service` khiến bean được phát hiện qua `@ComponentScan`. Method `inspect()` cố ý gọi `this.listAll()` để chỉ ra **self-invocation**: tuy tên listAll khớp expression `list*`, lời gọi nội bộ không đi qua proxy nên không có advice thứ hai.

### 9.5. Aspect chỉ dùng những signature pattern cần học

**src/main/java/demo/PatternAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PatternAspect {

    @Before("execution(* demo.TaskApi+.list*(..))")
    public void beforeList(JoinPoint jp) {
        System.out.println("AOP name " + jp.getSignature().getName());
    }

    @Before("execution(* demo.TaskApi+.lookup(java.lang.String))")
    public void beforeLookupString() {
        System.out.println("AOP lookup(String)");
    }

    @Before("execution(* demo.TaskApi+.lookup(long))")
    public void beforeLookupLong() {
        System.out.println("AOP lookup(long)");
    }

    @Before("execution(* demo.TaskApi+.lookup(java.lang.String,int))")
    public void beforeLookupTwo() {
        System.out.println("AOP lookup(String,int)");
    }

    @Before("execution(* demo.TaskApi+.update(demo.Payload+))")
    public void beforeUpdate() {
        System.out.println("AOP update(Payload+)");
    }

    @Before("execution(* demo.TaskApi+.extra(..))")
    public void beforeExtra() {
        System.out.println("AOP extra");
    }
}
~~~

Các expression thể hiện bốn yêu cầu:

- **Tên:** `list*` chọn `listAll()`, `listByOwner(String)`.
- **Kiểu con ở declaring type:** `TaskApi+` bao gồm các subtype phù hợp; `extra()` tồn tại trong `ExtendedTaskApi` và triển khai ở TaskService.
- **Overload:** ba expression `lookup` có danh sách parameter **String**, **long**, **String,int** khác nhau.
- **Kiểu con ở parameter:** `update(demo.Payload+)` bao quát overload khai báo `Payload` và overload khai báo `UrgentPayload`.

Các pointcut trong ví dụ được chọn để **mỗi lời gọi bên ngoài tương ứng một advice duy nhất**, tránh cần suy đoán thứ tự khi có nhiều advice của cùng aspect cùng match.

**@Aspect** khai báo advice, **@Component** cho Spring đăng ký aspect thành bean. Không được hiểu rằng `@Aspect` tự làm một object bất kỳ ngoài Spring thành advisor.

### 9.6. Bật AOP và component scan

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

Trong ví dụ Spring Framework thuần:

- **@Configuration**: nguồn cấu hình Java.
- **@ComponentScan**: đăng ký TaskService và PatternAspect.
- **@EnableAspectJAutoProxy**: bật hạ tầng dùng các `@AspectJ` advice và Spring AOP proxy.

Nếu Spring AOP tự động dùng **JDK dynamic proxy** vì target triển khai interface, client nên lấy theo `TaskApi` hoặc `ExtendedTaskApi`, không cast sang TaskService.

### 9.7. Main để kiểm tra chữ ký và hành vi

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            ExtendedTaskApi api = context.getBean(ExtendedTaskApi.class);
            System.out.println("IS_PROXY " + AopUtils.isAopProxy(api));

            api.listAll();
            api.listByOwner("team");

            System.out.println("RESULT " + api.lookup("A-1"));
            System.out.println("RESULT " + api.lookup(7L));
            System.out.println("RESULT " + api.lookup("A-2", 3));

            api.update(new Payload("B-1"));
            api.update(new UrgentPayload("U-1"));

            Payload declaredParent = new UrgentPayload("U-2");
            api.update(declaredParent);

            api.extra();
            api.inspect();
        }
    }
}
~~~

Ba lần `lookup(...)` giúp xác nhận overload; ba lần `update(...)` chứng minh việc Java chọn overload theo **compile-time type**, không chỉ theo runtime object.

Chạy từ thư mục chứa pom.xml:

~~~bash
mvn -q clean compile exec:java
~~~

### 9.8. Stdout logic kỳ vọng

~~~text
IS_PROXY true
AOP name listAll
TARGET listAll
AOP name listByOwner
TARGET listByOwner team
AOP lookup(String)
TARGET lookup(String) A-1
RESULT A-1
AOP lookup(long)
TARGET lookup(long) 7
RESULT 7
AOP lookup(String,int)
TARGET lookup(String,int) A-2/3
RESULT A-2:3
AOP update(Payload+)
TARGET update(Payload) B-1
AOP update(Payload+)
TARGET update(UrgentPayload) U-1
AOP update(Payload+)
TARGET update(Payload) U-2
AOP extra
TARGET extra
TARGET inspect
TARGET listAll
~~~

Đây là **đầu ra logic mong đợi** của mã ví dụ, bỏ qua logging riêng của Maven và Spring. Khi chạy thực tế, hãy so cả thứ tự **AOP** trước **TARGET** cho các method match, và xác minh **không có AOP name listAll** ở lời gọi `this.listAll()` bên trong inspect.

### 9.9. Phân tích các lần gọi tiêu biểu

**listAll và listByOwner.** Hai method cùng bắt đầu bằng `list` nên match `list*(..)`. Các method `lookup` không match prefix `list`.

**lookup("A-1").** Biểu thức argument có compile-time type String; Java chọn overload `lookup(String)`. Advice `beforeLookupString` chạy.

**lookup(7L).** Literal 7L là long primitive, nên overload `lookup(long)` được chọn và advice tương ứng chạy.

**lookup("A-2", 3).** Có hai parameter String và int, nên chỉ expression `lookup(java.lang.String,int)` match trong ba advice lookup.

**update(new UrgentPayload("U-1")).** Java chọn overload `update(UrgentPayload)`, `Payload+` match kiểu declared subtype.

**Payload declaredParent = new UrgentPayload("U-2"); api.update(declaredParent).** Dù object runtime là UrgentPayload, Java chọn `update(Payload)` theo type của biến. `Payload+` vẫn match vì bao gồm type Payload. Log chứng minh method thực sự được chọn là `update(Payload)`.

**api.extra().** Interface con `ExtendedTaskApi` công bố method extra; `TaskApi+` cho phép biểu thức theo interface cha tính đến subtype phù hợp.

**api.inspect().** Không có advice chọn tên inspect. Bên trong target `this.listAll()` vẫn in TARGET listAll nhưng không qua Spring proxy để chạy advice cho listAll một lần nữa.

## 10. Bảng dự đoán match từ chương trình

Bảng xét **method signature**, không tự thay thế điều kiện bean/proxy.

| Pattern | listAll() | lookup(String) | lookup(long) | lookup(String,int) | update(Payload) | update(UrgentPayload) |
| --- | --- | --- | --- | --- | --- | --- |
| `execution(* demo.TaskApi+.list*(..))` | Có | Không | Không | Không | Không | Không |
| `execution(* demo.TaskApi+.lookup(..))` | Không | Có | Có | Có | Không | Không |
| `execution(* demo.TaskApi+.lookup(*))` | Không | Có | Có | Không | Không | Không |
| `execution(* demo.TaskApi+.lookup(java.lang.String))` | Không | Có | Không | Không | Không | Không |
| `execution(* demo.TaskApi+.lookup(java.lang.String,int))` | Không | Không | Không | Có | Không | Không |
| `execution(* demo.TaskApi+.update(demo.Payload))` | Không | Không | Không | Không | Có | Không |
| `execution(* demo.TaskApi+.update(demo.UrgentPayload))` | Không | Không | Không | Không | Không | Có |
| `execution(* demo.TaskApi+.update(demo.Payload+))` | Không | Không | Không | Không | Có | Có |
| `execution(* demo.TaskApi+.*(..))` | Có | Có | Có | Có | Có | Có |

Khi muốn debug một expression, hãy dùng bảng riêng cho từng phương thức và từng type pattern. Nếu đọc khó, tách pointcut rộng thành các named pointcuts.

## 11. Named @Pointcut để tránh lặp lại điều kiện

Một aspect lớn có thể tái sử dụng những điều kiện nhiều lần:

~~~java
@Pointcut("execution(* demo.TaskApi+.list*(..))")
public void listing() {
}

@Pointcut("execution(* demo.TaskApi+.lookup(..))")
public void lookups() {
}

@Pointcut("listing() || lookups()")
public void reads() {
}
~~~

Đây là **các method mẫu để đặt bên trong một class @Aspect** có import `org.aspectj.lang.annotation.Pointcut`, không phải file Java độc lập. Trong một aspect đầy đủ, advice có thể tham chiếu:

~~~java
@Before("reads()")
public void beforeRead(JoinPoint jp) {
    System.out.println(jp.getSignature().getName());
}
~~~

Nếu bổ sung advice này vào `PatternAspect` đang chạy ở mục 9, một số method sẽ có **nhiều advice cùng match**, nên stdout không còn đúng với bản gốc. Hãy thay advice cũ bằng advice named pointcut hoặc dùng một context test riêng.

Cũng đừng nhầm **visibility của method @Pointcut** với **modifier của method nghiệp vụ**. Một named pointcut `private void reads()` vẫn có thể chọn các public service methods; ngược lại, expression có `execution(public ...)` mới giới hạn modifier của method được chọn.

## 12. Cách kết hợp các nhóm điều kiện

### 12.1. Chọn public lookup trên subtype

~~~text
execution(public * demo.TaskApi+.lookup(..))
~~~

Expression thể hiện:

- method public;
- return type tùy ý;
- declaring type trong họ TaskApi phù hợp;
- tên chính xác lookup;
- số tham số và type tùy ý.

Nhờ `(..)`, overload mới thêm sau này cũng có thể được match mà không phải cập nhật expression.

### 12.2. Chọn tên list với đúng một tham số

~~~text
execution(* demo.TaskApi+.list*(*))
~~~

Trong ví dụ, `listByOwner(String)` match còn `listAll()` không match. Nếu chọn `list*(..)`, cả hai match.

### 12.3. Chọn tên update và parameter là Payload hoặc subtype

~~~text
execution(* demo.TaskApi+.update(demo.Payload+))
~~~

Đây là dạng tiêu biểu cho **kiểu con trong parameter signature**. Nó không có nghĩa có thể advise một Java method không tồn tại chỉ vì argument object thuộc kiểu Payload.

### 12.4. Giới hạn theo Spring bean name

Giả sử có nhiều bean triển khai TaskApi, chỉ muốn bean tên `taskService`:

~~~text
execution(* demo.TaskApi+.lookup(..)) && bean(taskService)
~~~

`bean(...)` là designator **riêng Spring AOP** để match bean name. Nó không phải tên Java method và không phải quy tắc subtype. Với `@Service TaskService` và component scanning thông thường, bean name mặc định là `taskService`; nếu bean được đăng ký với tên khác, điều kiện này có thể không match.

### 12.5. Điều kiện runtime argument

~~~text
execution(* demo.TaskApi+.update(..))
&& args(demo.UrgentPayload)
~~~

Kết hợp trên nói: method tên update của type phù hợp, và **argument runtime** là UrgentPayload. Điều này có thể match một lời gọi `update(Payload)` được truyền instance UrgentPayload. Không đồng nhất nó với expression chỉ chọn overload khai báo `update(UrgentPayload)`.

Khi viết `@Before`, có thể nối hai dòng thành một chuỗi Java duy nhất; ví dụ:

~~~java
@Before(
    "execution(* demo.TaskApi+.update(..)) && " +
    "args(demo.UrgentPayload)"
)
public void beforeUrgent() {
    // ...
}
~~~

Đây là **fragment minh họa**; nếu đặt cạnh advice update có sẵn, một invocation phù hợp sẽ có thêm advice khác nên log thay đổi.

## 13. Java inheritance và giới hạn chọn subtype

### 13.1. Interface extends interface

~~~java
interface BaseApi {
    void run();
}

interface ExtendedApi extends BaseApi {
    void extra();
}
~~~

Pattern có hậu tố `BaseApi+` biểu thị BaseApi hoặc type con. Tuy nhiên, nếu service bean chỉ được expose bằng một JDK proxy không công bố `ExtendedApi`, client không thể tùy ý gọi `extra()` trên interface `BaseApi`. Trong ví dụ chính, bean thực sự triển khai **ExtendedTaskApi** và client lấy theo **ExtendedTaskApi.class**.

### 13.2. Class extends class

~~~java
class BaseProcessor {
    public void process() {
    }
}

class SpecialProcessor extends BaseProcessor {
    public void extra() {
    }
}
~~~

Type pattern có hậu tố `BaseProcessor+` bao hàm những subclass phù hợp. Nhưng để **Spring AOP** can thiệp method trên class-based proxy, target class/method vẫn phải cho phép subclass/override và caller phải đi qua proxy. Không dùng inheritance pattern để kỳ vọng chặn `final`, `private` hoặc constructor.

### 13.3. Khác package và method không visible

Nếu subclass nằm ở package khác, một method package-private của superclass có thể không visible cho subclass; CGLIB không thể override nó theo cách thông thường. Biểu thức subtype `+` **không xóa Java visibility constraints**.

### 13.4. Hierarchy không đồng nghĩa mọi overload được khai báo ở một nơi

Một interface con có thể bổ sung method cùng tên với interface cha nhưng parameter khác. Từ góc nhìn Java, đây vẫn là những overload khác nhau. Muốn chọn tất cả, dùng `name(..)` kết hợp phạm vi type phù hợp; muốn một overload cụ thể, ghi rõ parameter types.

## 14. Những góc cạnh thường gặp

### 14.1. Generic types và bridge methods

Nếu interface có:

~~~java
interface Repository<T> {
    T save(T value);
}
~~~

Và class triển khai `Repository<String>`, compiler có thể tạo **bridge method** để giữ tương thích sau type erasure. Đừng đoán rằng một pointcut ghi type argument tổng quát có thể phân biệt mọi `List<String>` và `List<Integer>` ở runtime. Khi dùng generic interface phức tạp, cần integration test thực tế trên proxy và signature được Spring resolve.

### 14.2. Varargs là array ở method signature

~~~java
void publish(String... items);
~~~

Tại cấp JVM, parameter type của phương thức là `String[]`. `publish(..)` match được danh sách tham số tùy ý, nhưng không có ý nghĩa “method varargs”. Muốn chọn riêng một phương thức varargs/array, kiểm tra array type pattern được hỗ trợ và signature thật.

### 14.3. Kiểu trả về không tham gia overload resolution của Java

Java **không cho phép** định nghĩa hai overload cùng tên và cùng parameter list mà **chỉ khác return type**:

~~~text
String read(String id)
int read(String id)
~~~

Đây là mô tả **không hợp lệ nếu đặt cả hai trong một class/interface**. Trong execution pattern bạn có thể lọc theo return type, nhưng đó không phải cách Java phân biệt hai overload ở source.

### 14.4. Dùng this() và target() đúng ngữ nghĩa Spring AOP

Trong Spring AOP, `this(demo.SomeType)` xét **proxy object**, còn `target(demo.SomeType)` xét **target object**. Với JDK dynamic proxy, proxy có thể không là instance của class implementation dù target là instance của class đó.

Nếu điều kiện của bạn liên quan đến **kiểu implementation tại runtime** thay vì signature, hãy cân nhắc `target`, nhưng không dùng nó thay một expression overload cụ thể khi yêu cầu dựa vào declared parameter types.

### 14.5. Spring Boot khác Spring Framework thuần ở auto-configuration

Trong ví dụ Maven Spring Framework thuần, `@EnableAspectJAutoProxy` được khai báo trực tiếp. Spring Boot có thể bật auto-proxy thông qua starter/auto-configuration phù hợp và có cấu hình loại proxy riêng. Vì vậy khi chép expression sang một ứng dụng Boot, hãy xác minh bean, proxy type và AOP config thực tế, không suy luận mọi context giống nhau.

## 15. Lỗi phổ biến và cách sửa

| Triệu chứng | Lý do thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Cả `lookup(String)` và `lookup(long)` bị advice dù chỉ muốn một overload | Dùng `lookup(..)` hoặc `lookup(*)` quá rộng | Dùng declared parameter type `java.lang.String` hoặc `long` |
| Overload có hai tham số không được advice | Dùng `(*)` | Dùng `(..)` hoặc `(java.lang.String,int)` |
| Biểu thức `lookup(java.lang.Long)` không match `lookup(long)` | Khác primitive/wrapper | Sửa về `long` |
| Aspect không thấy method ở interface con | Type pattern quá hẹp hoặc method không nằm trong proxy interface | Kiểm tra subtype `+`, concrete bean và interfaces được proxy |
| Method có argument UrgentPayload nhưng log overload Payload | Overload chọn theo compile-time type của biến | Đọc method thực sự được Java chọn; phân biệt execution và args |
| Biểu thức theo package không bao phủ subpackage | Nhầm `.*` với `..*` | Sửa declaring type pattern đúng |
| Advice áp dụng quá nhiều method | Pattern tên `*` hoặc `(..)` quá rộng | Thu hẹp method name, type hoặc danh sách parameter |
| `@Aspect` có rồi nhưng không thấy log | Aspect chưa là Spring bean hoặc AOP chưa bật | @Component/@Bean, component scan, @EnableAspectJAutoProxy |
| Advice không chạy khi `this.listAll()` | Self-invocation bỏ qua proxy | Tách lời gọi sang Spring bean khác hoặc gọi qua dependency |
| CGLIB không chặn private/final | Không thể override | Thiết kế entry method interceptable |
| Bean là proxy nhưng advice mong muốn không chạy | Proxy có thể do advisor khác, pointcut không match | Xem signature và quan sát advice cụ thể |
| Advice xuất hiện hai lần | Hai advisor cùng match hoặc gọi method qua proxy hai lần | Kiểm tra các pointcut giao nhau và đường gọi thực tế |
| Dùng `call(...)` và lỗi parse | Spring AOP không hỗ trợ call join point | Dùng execution, hiểu proxy boundary |
| Chọn overloaded method theo tên variable trong Java | Variable name không thuộc method parameter type signature | Lọc theo type/số lượng, không theo tên biến |

## 16. Quy trình debug theo ba lớp: chữ ký, proxy, invocation

**Bước 1 — Viết chữ ký thật ra giấy.** Ví dụ method `String lookup(String,int)` có return String, name lookup, hai tham số lần lượt String và int. Một expression như `lookup(*)` chắc chắn không match vì chỉ đúng một tham số.

**Bước 2 — Phân biệt class/interface và subtype.** Xem bean thực sự triển khai interface nào, liệu method chỉ xuất hiện trên interface con hay có mặt ở interface cha, và có cần `+` để thể hiện phạm vi intended hierarchy không.

**Bước 3 — Kiểm tra bean lấy từ Spring context.** Dùng `AopUtils.isAopProxy(bean)` trong một integration test, đồng thời nhớ rằng kết quả true không chứng minh một advice cụ thể đã match.

**Bước 4 — Gọi từng overload riêng từ bên ngoài proxy.** In log signature trong advice và target. Với code ở mục 9, từng overload lookup có advice riêng, giúp xác định match trực tiếp.

**Bước 5 — Thử cả compile-time parent reference.** Gán `Payload declaredParent = new UrgentPayload(...)`, gọi `update(declaredParent)`; kiểm tra overload thật là `update(Payload)`.

**Bước 6 — Thử một method không thuộc pointcut.** Ví dụ `inspect()` không có advice, dù nó gọi `this.listAll()`. Điều này cho thấy điều kiện match và đường đi invocation là hai chuyện.

**Bước 7 — Khi có nhiều advice, xem thứ tự và sự giao nhau.** Không phụ thuộc thứ tự khai báo các method @Before trong cùng class để suy ra thứ tự log. Nếu cần thứ tự xác định, thiết kế và cấu hình advisor/aspect order rõ ràng.

## 17. Bài tập tự thực hành

**Bài 1 — Thay tên list bằng wildcard hậu tố.** Thêm method `getStatus()` và `updateStatus()` vào interface/service rồi thử:

~~~text
execution(* demo.TaskApi+.*Status(..))
~~~

Dự đoán hai method trên match, còn `listAll()` không match.

**Bài 2 — Giới hạn đúng một parameter cho list.** Thay expression `list*(..)` bằng:

~~~text
execution(* demo.TaskApi+.list*(*))
~~~

Chạy lại: `listByOwner(String)` được advice; `listAll()` không được advice.

**Bài 3 — Dùng lookup(..) để bao phủ overload.** Thay ba advice lookup bằng một advice:

~~~java
@Before("execution(* demo.TaskApi+.lookup(..))")
public void beforeAnyLookup(JoinPoint jp) {
    System.out.println("AOP lookup " + jp.getSignature().toShortString());
}
~~~

Đây là **fragment thay thế**, không thêm cạnh ba advice lookup cũ nếu muốn giữ số log của bài. Mọi overload lookup đều có thể được chọn.

**Bài 4 — Chỉ chọn update(UrgentPayload).** Thay `update(demo.Payload+)` bằng:

~~~text
execution(* demo.TaskApi+.update(demo.UrgentPayload))
~~~

Dự đoán chỉ invocation tới overload `update(UrgentPayload)` được advice. Việc đưa một UrgentPayload object vào biến có declared type Payload **không** tự khiến Java đổi overload.

**Bài 5 — Thêm argument runtime.** Dùng điều kiện `args(demo.UrgentPayload)` cùng execution update, rồi gọi overload `update(Payload)` bằng runtime UrgentPayload. Xem sự khác biệt giữa type được truyền và chữ ký được chọn.

**Bài 6 — Tạo thêm Spring bean khác implement TaskApi.** So sánh pointcut theo type `TaskApi+` với điều kiện `bean(taskService)`. Nếu có nhiều bean cùng interface, phải chọn bean rõ ràng khi injection; tránh để `NoUniqueBeanDefinitionException` gây nhiễu thí nghiệm pointcut.

## 18. Checklist thiết kế pointcut theo signature

- Đã xác định chính xác mục tiêu là **tên**, **hierarchy**, **overload**, hay **runtime argument**?
- Pattern type có cần ký hiệu subtype **`+`** không?
- Pattern method name có đang quá rộng (`*`) hoặc quá hẹp không?
- Dùng `()`, `(*)` và `(..)` đúng số lượng tham số chưa?
- Declared parameter types có đúng **primitive/wrapper** và đúng thứ tự không?
- Với overload, đã phân biệt **compile-time overload resolution** với runtime object type chưa?
- Java interface/class của bean có được proxy expose đúng các methods cần thiết không?
- Service và aspect có nằm trong **Spring ApplicationContext** được cấu hình AOP không?
- Lời gọi test có đi qua proxy thay vì `this.method()` hoặc object tạo bằng `new` không?
- Có test ít nhất một method **match**, một method **không match** và một overloaded method chưa?
- Nếu nhiều advisor giao nhau, có kiểm tra hành vi advice và thứ tự thực tế không?

## 19. Câu hỏi tự đánh giá

**1. `list*(..)` khác `list*()` ra sao?**

Biểu thức đầu bao gồm những method tên bắt đầu list với mọi số lượng tham số; biểu thức sau chỉ chọn những method bắt đầu list có **không tham số**.

**2. Dấu `+` trong `TaskApi+` nghĩa là cộng thêm một method phải không?**

Không. Nó thuộc type pattern, nghĩa là type được chỉ định cùng các subtype/implementation phù hợp.

**3. `lookup(*)` có chọn được `lookup(String,int)` không?**

Không. `(*)` yêu cầu đúng một parameter, trong khi `lookup(String,int)` có hai.

**4. `lookup(String,..)` có chọn `lookup(String)` và `lookup(String,int)` không?**

Có, vì yêu cầu một String ở đầu và phần còn lại có thể có 0 hoặc nhiều parameter.

**5. Hai overload `update(Payload)` và `update(UrgentPayload)` có đều khớp `Payload+` không?**

Có, nếu Payload là superclass của UrgentPayload và expression chỉ định type pattern đó ở parameter position.

**6. Nếu `Payload p = new UrgentPayload()`, Java chọn overload nào khi gọi update(p)?**

Overload `update(Payload)` dựa vào compile-time type của biến p, dù object runtime là UrgentPayload.

**7. `args(UrgentPayload)` có đồng nghĩa overload `update(UrgentPayload)` không?**

Không. `args` xét runtime argument type, còn `execution` xét declared method signature.

**8. Vì sao Spring AOP không chặn `this.listAll()` dù method match wildcard?**

Lời gọi nội bộ `this` bỏ qua AOP proxy; signature match không đủ để advice chạy.

**9. Type pattern `demo.service.*` khác `demo.service..*` thế nào?**

Dạng đầu nói tới type trong package trực tiếp; dạng hai mở rộng cả các subpackage.

**10. Nên chọn kiểu nào nếu mục tiêu là mọi overload có tên lookup?**

Dùng `execution(* demo.TaskApi+.lookup(..))` cùng các điều kiện type/bean cần thiết.

## 20. Tài liệu chính thức

- Spring Framework — **Declaring a Pointcut**: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html
- Spring Framework — **@AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — **Declaring Advice**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Eclipse AspectJ — **Pointcut Semantics, Type/Subtype Patterns** (tham chiếu cú pháp được Spring sử dụng): https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html

**Kết luận:** Khi chọn method bằng Spring AOP, phải đọc **tên method**, **type hierarchy** (bao gồm `+` nếu cần), **overload** và **parameter pattern** như những ràng buộc riêng biệt. Một pointcut đúng về signature vẫn chỉ kích hoạt advice khi Spring quản lý bean, tạo proxy thích hợp và lời gọi thực sự đi qua proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-011">Chọn đúng độ rộng pointcut để tránh can thiệp bean ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn đúng độ rộng pointcut để tránh can thiệp bean ngoài ý muốn trong Spring AOP

## 1. Vì sao phải kiểm soát độ rộng pointcut?

Trong **Spring AOP**, một pointcut mô tả **các method execution trên Spring beans** mà advice có thể áp dụng. Một biểu thức hợp lệ về cú pháp vẫn có thể **quá rộng so với ý định nghiệp vụ**. Ví dụ, advice audit được thiết kế chỉ cho thao tác thanh toán nhưng lại match cả dịch vụ gửi báo cáo, dịch vụ đồng bộ hoặc các bean hỗ trợ khác.

Khi một advisor match thêm bean ngoài ý muốn, hậu quả có thể là:

- **Log/metrics sai phạm vi:** số lượt gọi và latency bị ghi nhận cho cả method không thuộc nghiệp vụ cần theo dõi.
- **Tăng chi phí xử lý:** advice không cần thiết chạy trên quá nhiều lời gọi; nếu advice có I/O, chi phí có thể lớn.
- **Sai hành vi:** `@Around` có thể thay đổi kết quả, xử lý exception, retry hoặc short-circuit những method không nên bị tác động.
- **Lộ dữ liệu:** advice ghi đối số/giá trị trả về vô điều kiện có thể đưa thông tin nhạy cảm vào log.
- **Khó debug:** một aspect tưởng chỉ phục vụ module A bất ngờ ảnh hưởng module B, nhất là khi thêm bean mới sau này.
- **Proxy hóa nhiều bean hơn cần thiết:** bean phù hợp với advisor có thể bị Spring AOP auto-proxy, thay đổi runtime type, đường gọi và các giới hạn proxy.

Mục tiêu của bài: học cách chọn phạm vi chính xác theo **package, lớp, phương thức, annotation, kiểu target và tên Spring bean**, đồng thời kiểm thử **những đối tượng không được match**. Đây là hướng dẫn dành riêng cho **Spring Aspect/Spring AOP proxy-based**, không lặp lại lý thuyết Aspect tổng quát.

## 2. Mô hình hai bước: advisor áp dụng cho bean, advice áp dụng cho invocation

Một biểu thức như:

```java
@Before("execution(public * example.service..*Service.*(..))")
```

có thể khiến Spring nhận diện nhiều lớp trong hệ thống có những method public tương ứng. Spring AOP auto-proxy xem một advisor có khả năng áp dụng cho **target class** khi khởi tạo bean, rồi xét các method và điều kiện invocation phù hợp.

```text
Spring @Aspect bean --> @Before / @Around và pointcut expression
                                   |
                            candidate advisor
                                   |
          Spring AOP xét bean theo type, method, bean name...
                                   |
                    target bean có advisor phù hợp?
                         /                         \
                       có                         không
                       |                            |
                 Có thể tạo proxy            Không proxy do advisor đó
                       |
                Caller gọi method
                       |
              Method / runtime match?
                    /       \
                  có        không
                  |           |
             Advice chạy   Bỏ qua advice này
                  |
             Target method
```

**Không đánh đồng các khẳng định sau:**

1. `AopUtils.isAopProxy(bean) == true`: bean là Spring AOP proxy; **không** khẳng định mọi method đều chạy một advice.
2. Advisor *có thể áp dụng cho class*: ít nhất một method/điều kiện có khả năng match; **không** khẳng định mọi lời gọi được can thiệp.
3. Pointcut match một method: advice chỉ chạy khi invocation đi **qua Spring proxy** và mọi điều kiện động đều thỏa.

Ngược lại, nếu pointcut quá rộng, Spring có thể proxy **nhiều target beans** hơn dự kiến, kể cả các bean được thêm vào trong tương lai có tên lớp/method trùng quy tắc.

## 3. Độ rộng pointcut được quyết định bởi những chiều nào?

| Chiều chọn | Cú pháp Spring AOP tiêu biểu | Cần chú ý |
| --- | --- | --- |
| Phạm vi package và lớp | `within(com.acme.payment..*)` | Giới hạn method execution của types trong package tương ứng và package con; không đồng nghĩa chỉ một bean |
| Chữ ký phương thức | `execution(public * com.acme.payment.PaymentService.pay(..))` | Giới hạn public, class, method name, tham số; biểu thức càng rõ càng ít match nhầm |
| Bean name | `bean(paymentService)` | Spring-specific PCD; tên bean phải đúng với cấu hình IoC |
| Annotation trên method | `@annotation(com.acme.audit.Audited)` | Chỉ match method có annotation tương ứng theo ngữ nghĩa Spring AOP |
| Annotation trên type | `@within(com.acme.audit.AuditedType)` | Giới hạn method thuộc type có annotation; cần kiểm tra ngữ nghĩa kế thừa/interface |
| Target type | `target(com.acme.payment.PaymentService)` | Kiểu của target object; khác `this(...)` khi dùng JDK proxy |
| Runtime arguments | `args(java.lang.String)` | Có thể phụ thuộc kiểu argument thực tế tại invocation, không chỉ signature |
| Kết hợp | `execution(...) && within(...) && bean(...)` | Giao các tập match: mọi điều kiện cùng đúng |
| Mở rộng | `execution(...) || @annotation(...)` | Hợp các tập match: thường **rộng hơn**, cần thử trường hợp ngoài phạm vi |
| Loại trừ | `!within(com.acme.internal..*)` | Trừ đi một nhóm; riêng điều kiện loại trừ không đủ tạo allowlist an toàn |

**Phạm vi Spring:** `execution`, `within`, `this`, `target`, `args`, `@annotation`, `@within` và `bean` là các cách giới hạn thường gặp. Spring AOP chỉ hỗ trợ **method execution join points trên Spring-managed beans**. Không sử dụng `call()`, `get()`, `set()` hoặc các PCD chỉ thuộc AspectJ weaving trong một Spring AOP expression vì chúng không được hỗ trợ ở đây.

## 4. Từ pointcut nguy hiểm đến pointcut có chủ đích

Giả sử dự án có hai loại bean:

- `PaymentService`: nghiệp vụ thanh toán, gồm `pay()` và `status()`.
- `ReportService`: tạo báo cáo, gồm `generate()`.

Yêu cầu nghiệp vụ: **chỉ ghi audit trước `PaymentService.pay()` của bean `paymentService`**.

### 4.1. Biểu thức quá rộng

```java
@Before("execution(public * example.service..*Service.*(..))")
```

Điều kiện này chọn **mọi method public** trên các lớp có tên phù hợp mẫu `*Service` trong và dưới package `example.service`. Nếu `PaymentService` và `ReportService` cùng match, advice chạy cả ba method `pay()`, `status()`, `generate()`.

Rủi ro thực tế: một team khác thêm `TaxService`, `EmailService` hay `HealthService` vào package tương tự. **Không cần sửa aspect**, advisor vẫn có thể bắt đầu can thiệp các bean mới nếu pointcut match. Cú pháp đúng nhưng **phạm vi nghiệp vụ không đúng**.

### 4.2. Chỉ giới hạn package

```java
@Before("execution(public * example.service.payment..*.*(..))")
```

Biểu thức này đã loại trừ báo cáo ở package `example.service.report`, nhưng **vẫn match mọi public method** của mọi class trong khu vực thanh toán. Nếu bạn chỉ cần `pay()`, nó vẫn rộng quá mức.

### 4.3. Giới hạn đúng method và bean

```java
@Before(
    "execution(public * example.service.payment.PaymentService.pay(..))" +
    " && bean(paymentService)"
)
```

Điều kiện trên chọn một chữ ký method cụ thể **và** Spring bean đúng tên. Đặc biệt hữu ích khi có nhiều bean cùng class hoặc bạn đang triển khai aspect theo đúng đối tượng/tenant/adapter được quản lý bởi Spring.

Đặt `bean(paymentService)` là Spring-specific pointcut designator: nó có ý nghĩa **trong Spring AOP**, không nên đưa nguyên xi sang AspectJ compiler/weaver với kỳ vọng được hỗ trợ.

Nếu ứng dụng đổi bean name vì refactor, pointcut sẽ phải cập nhật. Khi tên bean không phải hợp đồng ổn định, sử dụng **kiểu lớp, module/package hoặc annotation chuyên dụng** để diễn đạt ý định thay vì buộc nó vào tên.

### 4.4. Đừng dùng nhiều điều kiện chỉ để dài hơn

Không nhất thiết mọi pointcut cần vừa `execution` vừa `within` vừa `bean`. Nếu một chữ ký đã xác định chính xác nghiệp vụ và package, các điều kiện bổ sung có thể là dư thừa hoặc làm cấu hình quá nhạy với thay đổi. Mục tiêu là **phạm vi nhỏ nhất diễn đạt được ý định mà vẫn ổn định**, không phải biểu thức có nhiều toán tử nhất.

Một quy tắc thực hành hữu ích: chọn **phạm vi module/package** trước, sau đó giới hạn **loại method** cần advice; chỉ dùng `bean` hoặc `@annotation` nếu đó thật sự là một phần hợp đồng.

## 5. Ví dụ Maven/Java 17+ chạy được: rộng và hẹp

Ví dụ này tạo **hai Spring ApplicationContext độc lập** để so sánh hai pointcut trên **cùng các service classes**. Tách context giúp không bị lẫn hai aspect và không phụ thuộc thứ tự advisor.

Sử dụng **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**. Trong dự án thực tế, ưu tiên BOM của Spring Framework hoặc Spring Boot để quản lý phiên bản; ví dụ cố định chúng để tái hiện độc lập.

### 5.1. Cấu trúc thư mục

```text
pointcut-width-demo/
├── pom.xml
└── src/main/java/example/
    ├── Main.java
    ├── service/
    │   ├── payment/PaymentService.java
    │   └── report/ReportService.java
    ├── aop/
    │   ├── BroadAspect.java
    │   └── NarrowAspect.java
    └── config/
        ├── ServicesConfig.java
        ├── BroadConfig.java
        └── NarrowConfig.java
```

### 5.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>pointcut-width-demo</artifactId>
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
```

### 5.3. PaymentService

**`src/main/java/example/service/payment/PaymentService.java`**

```java
package example.service.payment;

public class PaymentService {

    public void pay() {
        System.out.println("payment: pay");
    }

    public void status() {
        System.out.println("payment: status");
    }
}
```

### 5.4. ReportService

**`src/main/java/example/service/report/ReportService.java`**

```java
package example.service.report;

public class ReportService {

    public void generate() {
        System.out.println("report: generate");
    }
}
```

### 5.5. Aspect quá rộng

**`src/main/java/example/aop/BroadAspect.java`**

```java
package example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BroadAspect {

    @Before("execution(public * example.service..*Service.*(..))")
    public void beforeAnyServiceMethod() {
        System.out.println("broad: before");
    }
}
```

Nó không có `@Component` vì sẽ được đăng ký qua `@Bean` trong `BroadConfig`. Biểu thức cố ý match cả payment/report services. Đây là **mẫu để thấy lỗi về phạm vi**, không phải khuyến nghị áp dụng rộng khắp production.

### 5.6. Aspect giới hạn đúng mục tiêu

**`src/main/java/example/aop/NarrowAspect.java`**

```java
package example.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class NarrowAspect {

    @Before(
        "execution(public * example.service.payment.PaymentService.pay(..))"
        + " && bean(paymentService)"
    )
    public void beforePayment() {
        System.out.println("narrow: before payment pay");
    }
}
```

Pointcut này không nhắm tới `PaymentService.status()`, `ReportService.generate()`, hay một bean khác chỉ tình cờ có method tên `pay()`.

### 5.7. Cấu hình các beans chung

**`src/main/java/example/config/ServicesConfig.java`**

```java
package example.config;

import example.service.payment.PaymentService;
import example.service.report.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ServicesConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public ReportService reportService() {
        return new ReportService();
    }
}
```

Vì tên method `@Bean` là `paymentService`, bean có tên này trong context, đáp ứng `bean(paymentService)`. Nếu đổi tên bean, cần sửa điểm cắt hoặc bỏ ràng buộc theo tên.

### 5.8. Context sử dụng broad pointcut

**`src/main/java/example/config/BroadConfig.java`**

```java
package example.config;

import example.aop.BroadAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(ServicesConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class BroadConfig {

    @Bean
    public BroadAspect broadAspect() {
        return new BroadAspect();
    }
}
```

### 5.9. Context sử dụng narrow pointcut

**`src/main/java/example/config/NarrowConfig.java`**

```java
package example.config;

import example.aop.NarrowAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(ServicesConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class NarrowConfig {

    @Bean
    public NarrowAspect narrowAspect() {
        return new NarrowAspect();
    }
}
```

Cả hai context đều dùng `@EnableAspectJAutoProxy` nhưng đăng ký **một aspect khác nhau**. Nhờ `proxyTargetClass=true`, demo có thể lấy service theo concrete class mà không lo vấn đề kiểu của JDK proxy; đây không phải yêu cầu bắt buộc để giới hạn pointcut.

### 5.10. Chạy và so sánh

**`src/main/java/example/Main.java`**

```java
package example;

import example.config.BroadConfig;
import example.config.NarrowConfig;
import example.service.payment.PaymentService;
import example.service.report.ReportService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        System.out.println("-- BROAD --");
        try (var ctx = new AnnotationConfigApplicationContext(BroadConfig.class)) {
            PaymentService payment = ctx.getBean(PaymentService.class);
            ReportService report = ctx.getBean(ReportService.class);

            System.out.println("payment proxy: " + AopUtils.isAopProxy(payment));
            System.out.println("report proxy: " + AopUtils.isAopProxy(report));

            payment.pay();
            payment.status();
            report.generate();
        }

        System.out.println("-- NARROW --");
        try (var ctx = new AnnotationConfigApplicationContext(NarrowConfig.class)) {
            PaymentService payment = ctx.getBean(PaymentService.class);
            ReportService report = ctx.getBean(ReportService.class);

            System.out.println("payment proxy: " + AopUtils.isAopProxy(payment));
            System.out.println("report proxy: " + AopUtils.isAopProxy(report));

            payment.pay();
            payment.status();
            report.generate();
        }
    }
}
```

Trong thư mục dự án:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

**Output của các lệnh `System.out.println` ở đúng các file trên:**

```text
-- BROAD --
payment proxy: true
report proxy: true
broad: before
payment: pay
broad: before
payment: status
broad: before
report: generate
-- NARROW --
payment proxy: true
report proxy: false
narrow: before payment pay
payment: pay
payment: status
report: generate
```

Có thể có log build/runtime khác của Maven hoặc framework; bảng chỉ liệt kê những dòng do chương trình mẫu in ra.

### 5.11. Ý nghĩa kết quả

- **BROAD:** cả PaymentService và ReportService bị pointcut match, nên đều có Spring AOP proxy. Advice chạy trước cả `pay()`, `status()` và `generate()`. Như vậy ba lời gọi bị audit dù nhu cầu ban đầu chỉ có một.
- **NARROW:** chỉ `paymentService.pay()` đủ điều kiện, nên PaymentService có proxy. `payment.status()` được gọi trên cùng proxy nhưng **không chạy narrow advice**, vì method không match. ReportService không cần proxy **do advisor của ví dụ** và `AopUtils.isAopProxy(report)` là `false` trong context này.
- Không thay đổi các target classes, chỉ thay **độ rộng pointcut** đã đổi phạm vi bean và lời gọi thực sự bị can thiệp.
- Không suy từ `isAopProxy(payment) == true` rằng mọi method của PaymentService đều chạy advice. Cấp **bean** và cấp **method** có quyết định khác nhau.

Nếu ứng dụng bổ sung advisor khác (transaction/cache, v.v.), một bean không match narrow advice vẫn có thể là proxy do **advisor khác**. Kết quả `report proxy: false` được dự đoán theo đúng cấu hình demo tối giản.

## 6. Viết pointcut theo module/package thay vì match toàn ứng dụng

Trong codebase có nhiều module, package là một giới hạn tự nhiên. Ví dụ ứng dụng thật có:

```text
com.acme.payment.service
com.acme.payment.repository
com.acme.reporting.service
com.acme.inventory.service
```

Nếu advice chỉ dành cho service thanh toán, chọn:

```java
@Pointcut("within(com.acme.payment.service..*)")
public void inPaymentServiceLayer() {}

@Pointcut("execution(public * *(..))")
public void anyPublicMethod() {}

@Pointcut("inPaymentServiceLayer() && anyPublicMethod()")
public void paymentPublicOperations() {}
```

Đây là **snippet bên trong một class `@Aspect`**, không phải chương trình Java độc lập. Nó dùng **named pointcuts** để đặt tên cho ý định. `within(com.acme.payment.service..*)` giới hạn theo types trong vùng code; `execution(public * *(..))` giới hạn method public; giao nhau diễn đạt “public operations thuộc service layer thanh toán”.

Tránh:

```java
@Before("execution(* com.acme..*(..))")
```

Biểu thức kiểu này có phạm vi rất rộng và khó hiểu/kiểm soát. Nó còn có thể khiến class/method ngoài phạm vi nghiệp vụ được xem xét khi ứng dụng phát triển.

**Lưu ý:** Không phải cứ có package tên `service` là tất cả method trong đó đều cần advice. Giới hạn package là bước đầu, **không thay thế ràng buộc theo chức năng thực sự cần can thiệp**.

## 7. Khi nào nên dùng annotation làm hợp đồng opt-in?

Nếu các method cần advice phân tán ở nhiều package, hoặc chỉ **một số** method nghiệp vụ được lựa chọn, một annotation chuyên dụng có thể diễn đạt ý định bền vững hơn so với tên method hoặc bean.

**`AuditedOperation.java` — đoạn minh họa đầy đủ annotation type:**

```java
package com.acme.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {
}
```

Một service có thể đánh dấu **đúng method cần audit**:

```java
// Snippet bên trong một service đã được Spring quản lý.
@AuditedOperation
public void approvePayment() {
    // ...
}

public void previewPayment() {
    // Không có annotation audit.
}
```

Aspect chọn marker trong phạm vi module:

```java
@Aspect
public class PaymentAuditAspect {
    @Before(
        "within(com.acme.payment..*)"
        + " && @annotation(com.acme.audit.AuditedOperation)"
    )
    public void beforeAuditedPayment() {
        // Chỉ method được đánh dấu và nằm trong module payment.
    }
}
```

Đây là **snippet bổ sung**: trong ứng dụng thật class aspect cần đăng ký thành Spring bean, cần AOP được bật và class annotation phải nằm trên classpath. Không cho rằng `@Aspect` tự đăng ký qua component scanning.

**Lợi ích:** team đọc code sẽ nhận ra method nào *opt-in* cho advice. Nếu method bị đổi tên hoặc refactor sang class khác nhưng vẫn giữ annotation trong module cho phép, ý định có thể được giữ lại.

**Hạn chế:** Cần thống nhất vị trí annotation khi proxy interface hoặc implementation, và viết kiểm thử xác nhận pointcut thực sự match trong chiến lược JDK/CGLIB đang chạy. `@annotation` match annotation của **method**, không phải annotation trên mọi class hoặc field.

Một marker annotation không thể bảo vệ dữ liệu nhạy cảm nếu advice vẫn log bừa bãi các tham số; hãy thiết kế nội dung log riêng.

## 8. Điều kiện `bean()`: chính xác nhưng có coupling

Spring cung cấp `bean(idOrNamePattern)`, một pointcut designator **riêng của Spring AOP**. Ví dụ:

```java
@Before("bean(paymentService) && execution(public * *.pay(..))")
```

Khi các bean khác nhau cùng implements interface thanh toán hoặc cùng class, tên bean có thể tách đối tượng cần audit. Dùng `bean()` khi **tên bean là một phần hợp đồng triển khai**, chẳng hạn một adapter được cấu hình có chủ đích.

Lưu ý:

- Pattern bean name có thể sử dụng ký tự `*`, ví dụ `bean(*PaymentService)`. Biểu thức dùng tên bean, **không phải** tên class Java.
- Nếu component name do Spring tự suy luận hoặc bị thay bởi naming strategy, biểu thức có thể không còn match.
- Nếu sử dụng bean alias/FactoryBean hoặc context cha-con, hãy kiểm tra cách tên bean thực tế được resolve.
- Một phép thử thuần `AopUtils.canApply(pointcut, SomeClass.class)` **không thay thế** kiểm thử ở context thật với bean name cụ thể.
- `bean()` là Spring-specific, không thuộc danh sách PCD được AspectJ weaving trực tiếp hỗ trợ.

Trong một số hệ thống, kiểu/type + annotation có thể là hợp đồng ổn định hơn tên bean; đừng biến bean name thành quy tắc mặc định cho mọi aspect.

## 9. Kết hợp `&&`, `||` và `!` mà không mở rộng vô ý

Giả sử:

- `A` = method public trong module thanh toán.
- `B` = method có annotation `@AuditedOperation`.
- `C` = method có tên `pay`.

| Biểu thức | Tập hợp match |
| --- | --- |
| `A && B` | Chỉ method thỏa cả hai; thường **hẹp hơn** A hoặc B |
| `A && C` | Method public trong payment có tên phù hợp |
| `A || B` | Method thuộc A **hoặc** được đánh dấu B ở **bất cứ vùng nào**; có thể rộng bất ngờ |
| `A && !C` | Chọn tất cả method thuộc A trừ C; có thể rất rộng |
| `A && (B || C)` | Vẫn giới hạn trong A, rồi chọn method đánh dấu hoặc tên phù hợp |

Khi dùng dấu phủ định, nhớ rằng `!within(com.acme.internal..*)` chỉ loại bỏ vùng `internal`; nó **vẫn chứa toàn bộ ứng dụng ngoài vùng đó** nếu không kết hợp với một điều kiện allowlist tích cực.

Ví dụ dễ sai:

```java
@Before(
    "within(com.acme.payment..*)"
    + " || @annotation(com.acme.audit.AuditedOperation)"
)
```

Đọc chính xác: **tất cả method của payment module**, cộng **mọi method được đánh dấu annotation ở package khác**. Nếu mong muốn chỉ các method được đánh dấu **bên trong payment module**, phải dùng `&&`:

```java
@Before(
    "within(com.acme.payment..*)"
    + " && @annotation(com.acme.audit.AuditedOperation)"
)
```

Không tự đổi `&&` thành `||` chỉ để “bao phủ nhiều hơn”, đặc biệt với advice có side effects.

## 10. Kiểm thử cả trường hợp match và không match

Một pointcut chính xác phải được kiểm chứng bằng **positive cases** và **negative cases**.

### 10.1. Ma trận kiểm thử phạm vi

Đối với `NarrowAspect` ở mục 5:

| Trường hợp gọi qua Spring bean reference | Mong đợi chạy narrow advice? | Lý do |
| --- | --- | --- |
| `paymentService.pay()` | **Có** | Đúng class, method và bean name |
| `paymentService.status()` | **Không** | Khác method |
| `reportService.generate()` | **Không** | Khác class/package và bean name |
| Một bean `backupPaymentService` cùng class, nếu được đăng ký | **Không** | Không match `bean(paymentService)` |
| `new PaymentService().pay()` ngoài Spring | **Không** | Không có Spring AOP proxy |
| `this.pay()` nội bộ target | **Không** với lời gọi nội bộ đó | Self-invocation bỏ qua Spring proxy |

Không nên chỉ assert “advice đã chạy một lần” cho happy path. Một thay đổi pointcut có thể làm advice tiếp tục chạy ở case đúng **và** chạy thêm ở những case sai, khiến test cũ vẫn xanh.

### 10.2. Kiểm tra qua integration test với Spring

Có thể gắn một counter hoặc test sink vào advice thay cho `System.out.println`, rồi tạo Spring context và gọi các phương thức. Với broad/narrow demo:

1. Assert advice của narrow chạy **đúng một lần** khi gọi `payment.pay()`.
2. Assert counter **không tăng** khi gọi `payment.status()`.
3. Assert counter **không tăng** khi gọi `report.generate()`.
4. Nếu dùng `bean()`, tạo thêm một bean cùng kiểu khác tên và xác nhận không bị advice.
5. Nếu thay đổi phạm vi package, thêm một lớp thử nghiệm tương tự ở package láng giềng để kiểm tra tính cách ly.

Đếm log ở ví dụ là đủ để học cơ chế; dự án production nên viết **JUnit integration tests** và sử dụng dependency/test profile để xác minh không có side-effect ngoài phạm vi.

### 10.3. Kiểm tra pointcut bằng Spring API

Spring AOP có `org.springframework.aop.aspectj.AspectJExpressionPointcut` để phân tích một expression cụ thể:

```java
// Snippet minh họa; không phải chương trình độc lập.
AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
pointcut.setExpression(
    "execution(public * example.service.payment.PaymentService.pay(..))"
);

boolean paymentClassCanMatch =
    AopUtils.canApply(pointcut, PaymentService.class);

boolean reportClassCanMatch =
    AopUtils.canApply(pointcut, ReportService.class);
```

Với chữ ký expression được chỉ ra, dự kiến kết quả lần lượt là `true` và `false`. Cần import `AspectJExpressionPointcut` và `AopUtils` cùng các model classes.

**Giới hạn:** `canApply` ở đây trả lời câu hỏi class có thể có method match hay không. Nó không xác nhận một proxy cụ thể đã cài advisor, không kiểm tra bean name nếu thiếu ngữ cảnh và không thay thế kiểm thử invocation runtime. Với pointcut dùng `bean()`, cần đánh giá trong Spring bean factory/context và các advisor thực sự đã được auto-proxy creator chọn.

## 11. Pointcut rộng còn gây thay đổi proxy type và tương tác framework

Một aspect rộng có thể tạo proxy cho những bean trước đây vốn là object gốc. Điều này có thể làm:

- Caller từng inject theo **concrete implementation type** gặp lỗi khi AOP chuyển sang JDK dynamic proxy.
- Những bean cấu hình `final` method không được can thiệp theo class-based proxy dù bean bị proxy.
- Các method gọi nội bộ qua `this` vẫn bỏ qua Spring AOP, khiến phạm vi tác động thực tế không đồng đều.
- Transaction/cache/security advisors cùng tham gia một proxy, làm thứ tự interceptor khó theo dõi hơn.
- `equals()`, `toString()`, introspection hoặc công cụ quan sát class runtime trở nên khác so với plain Java object.
- Một `@Around` advice có lỗi trong `proceed()`/return/exception flow ảnh hưởng những method hoàn toàn không thuộc nghiệp vụ ban đầu.

**Quan trọng:** Proxy được tạo không có nghĩa *tất cả* những hậu quả trên chắc chắn xuất hiện. Đó là các rủi ro cần kiểm chứng khi **mở rộng phạm vi AOP**. Cách phòng ngừa hiệu quả nhất là làm pointcut khớp đúng lớp/method/bean có mục đích rõ ràng.

## 12. Spring Boot: tự bật AOP không đồng nghĩa nên dùng pointcut rộng

Trong Spring Boot, khi AOP/AspectJ starter và auto-configuration được bật, hệ thống có thể tự bật hỗ trợ `@AspectJ` proxies mà không cần bạn viết `@EnableAspectJAutoProxy`. Nhưng cách Spring chọn các bean/method chịu advice **vẫn phụ thuộc pointcut**.

- **Spring Boot 3.x:** thông thường dùng `spring-boot-starter-aop`.
- **Spring Boot 4.x:** starter tương ứng được đổi tên thành `spring-boot-starter-aspectj`.
- Một lớp `@Aspect` phải được đăng ký làm Spring bean (thường `@Component` hoặc `@Bean`).
- `spring.aop.proxy-target-class` ảnh hưởng chiến lược JDK/CGLIB, **không tự thu hẹp pointcut**.
- Một aspect dùng `execution(* *(..))` có thể match nhiều method khi chúng thỏa quy tắc Spring AOP, kể cả nếu trước đó project chưa có AOP proxy cho những bean ấy.

Đặc biệt không sao chép pointcut demo quá rộng từ dự án Spring Framework nhỏ sang toàn bộ Spring Boot application có nhiều service/infrastructure beans.

## 13. Phòng tránh lỗi khi nâng cấp hoặc mở rộng ứng dụng

### 13.1. Thêm class/method mới

Pattern dựa trên tên như `*Service` hoặc `save*` có thể tự động bao phủ những class/method mới dù team viết chúng không biết đến aspect. Review các biểu thức pointcut như review **public API boundary**; mỗi lần thêm module/package mới nên xem lại các pointcut shared.

### 13.2. Đổi package hoặc tên bean

Pointcut quá cụ thể về tên class/bean có thể mất hiệu lực sau refactor. Để cân bằng an toàn và khả năng bảo trì:

- Dùng named pointcuts gắn với **ngữ nghĩa module** thay vì lặp chuỗi rất dài.
- Dùng annotation opt-in khi method cần advice không có mô hình tên/package ổn định.
- Nếu dùng bean name, đặt tên tường minh và bảo vệ bằng integration test.
- Không chuyển ngay sang `execution(* *(..))` chỉ để test “đỡ thất bại” sau refactor.

### 13.3. Advice có side effects

Các advice audit, retry, transaction hoặc security cần chú ý hơn vì việc match nhầm có thể ảnh hưởng đúng/sai nghiệp vụ. Có thể dùng cả hai lớp bảo vệ:

1. Pointcut **giới hạn cứng** vào module/type/method.
2. Logic advice **không log secrets** và thực hiện hành vi an toàn khi gặp đối số ngoài dự kiến.

Nhưng guard điều kiện nằm bên trong advice **không thay thế** pointcut hẹp: proxy/advisor vẫn có thể can thiệp những bean/method không cần thiết.

### 13.4. Runtime arguments và performance

Pointcut dựa trên `args(...)` hoặc điều kiện phụ thuộc runtime có thể cần đánh giá theo từng invocation. Chọn biểu thức theo ngữ nghĩa chứ không chỉ tối ưu hiệu năng. Spring hỗ trợ `ClassFilter`, `MethodMatcher` và kiểm tra static/dynamic; **narrow scoping** có thể giúp giảm số ứng viên cần kiểm tra. Đừng đoán chi phí từ độ dài biểu thức, hãy đo hiệu năng ở ứng dụng thật nếu advice nằm trên hot path.

## 14. Checklist thiết kế pointcut

Trước khi merge một Spring Aspect, hãy trả lời:

1. **Ý định cụ thể:** advice nhằm tác động vào thao tác nghiệp vụ nào?
2. **Allowlist:** package, type, method hoặc annotation nào định nghĩa đúng phạm vi?
3. **Không được tác động:** bean/package/method nào tuyệt đối không nên bị match?
4. **Thay đổi tương lai:** thêm lớp `*Service` hoặc chuyển package có gây match bất ngờ không?
5. **Proxy boundary:** caller có gọi qua Spring proxy không, method có proxy được không?
6. **Bean name:** tên có ổn định nếu pointcut dùng `bean(...)` không?
7. **Hành vi advice:** có log bí mật, retry, đổi return value, suppress exception hoặc transaction side effects không?
8. **Test:** có positive **và** negative integration tests không?
9. **Framework:** transaction/cache/async advisors có thể khiến bean vẫn là proxy khi custom aspect không match không?
10. **Bảo trì:** named pointcut, chú thích ý định và test có giúp người khác hiểu biểu thức không?

Chỉ khi danh sách trên rõ ràng mới nên tăng phạm vi. “Chạy được” khác với “chỉ chạy ở đúng nơi”.

## 15. Bài tập

**Tình huống A.** Yêu cầu audit chỉ cho `PaymentService.pay()`. Aspect dùng `execution(public * example.service..*Service.*(..))`. Một `ReportService.generate()` mới được tạo ở package `example.service.report`. Advice có thể chạy không?

**Đáp án:** Có. Type name `ReportService` và public method `generate()` đáp ứng pattern rộng, mặc dù không thuộc thanh toán. Thu hẹp class/method hoặc kết hợp scope khác.

**Tình huống B.** Pointcut `within(example.service.payment..*)` có chắc chắn chỉ match `pay()` không?

**Đáp án:** Không. Nó chọn method executions trong các types thuộc package đó, bao gồm các method khác. Cần thêm điều kiện theo method hoặc annotation.

**Tình huống C.** Có expression `A || B`, trong đó A chọn service thanh toán và B chọn mọi method có annotation audit trên toàn ứng dụng. Nó có thể chọn method ngoài payment không?

**Đáp án:** Có. Một method ở package khác vẫn có thể thỏa B. Để buộc cùng phạm vi payment, dùng `A && B` nếu đúng ý định.

**Tình huống D.** Narrow pointcut được cấu hình đúng. `paymentService` là AOP proxy nhưng gọi `status()` không in log audit. Có mâu thuẫn không?

**Đáp án:** Không. Proxy tồn tại ở cấp bean vì ít nhất một method có advisor; advice dành cho `pay()` không match `status()`.

**Tình huống E.** Pointcut chỉ dùng `bean(paymentService)` để ghi log mọi method. Có vấn đề gì?

**Đáp án:** Nó hạn chế theo bean, nhưng **không** hạn chế method. Nếu chỉ cần `pay()` thì còn quá rộng; thêm `execution(...)` hoặc annotation hợp lệ.

**Tình huống F.** Sau refactor đổi tên bean `paymentService` thành `primaryPaymentService`, advice không chạy. Có nên sửa thành match tất cả service beans không?

**Đáp án:** Không. Cập nhật đúng hợp đồng bean name hoặc chuyển sang annotation/type rõ ràng và bổ sung test. Mở rộng không kiểm soát sẽ tạo rủi ro can thiệp bean ngoài ý muốn.

## 16. Nguồn tham khảo Spring chính thức

- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): các PCD Spring AOP hỗ trợ, `bean()`, cách kết hợp named pointcuts và khuyến nghị viết pointcut hẹp.
- [Spring Framework — Spring Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher`, static/dynamic matching.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): proxy boundaries, JDK/CGLIB, self-invocation.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): bật Spring AOP và yêu cầu aspect bean.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): advice kết hợp pointcut và cách khai báo `@Before`, `@Around`.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): auto-configuration AOP và proxy strategy trong Spring Boot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-pointcut-expressions-012">Những pointcut designator của AspectJ không được Spring AOP hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Những pointcut designator của AspectJ không được Spring AOP hỗ trợ

## 1. Vấn đề: dùng đúng cú pháp AspectJ chưa chắc chạy trong Spring AOP

Spring Framework hỗ trợ khai báo aspect bằng annotation `@Aspect`, `@Pointcut`, `@Before`, `@Around` và sử dụng cú pháp biểu thức pointcut bắt nguồn từ AspectJ. Tuy nhiên, **Spring AOP không triển khai toàn bộ ngôn ngữ pointcut của AspectJ**. Hạ tầng proxy của Spring chỉ cung cấp các **method execution join point trên Spring-managed beans**, không quan sát mọi kiểu sự kiện như lời gọi method ở mọi vị trí bytecode, truy cập field hay khởi tạo constructor.

Vì vậy biểu thức:

~~~java
@Before("execution(* demo.OrderService.submit(..))")
public void beforeSubmit() {
    // Spring AOP có thể sử dụng.
}
~~~

có thể hợp lệ, trong khi biểu thức:

~~~java
@Before("call(* demo.OrderService.submit(..))")
public void beforeCall() {
    // Spring AOP không hỗ trợ call(...) designator.
}
~~~

**không hợp lệ đối với pointcut parser mà Spring AOP sử dụng**. Khi một pointcut expression chứa designator không được Spring AOP hỗ trợ và framework cố phân tích/chuẩn bị pointcut, nó ném `IllegalArgumentException`. Đây thường là **lỗi cấu hình/chạy khởi tạo application context**, không phải đơn giản là “advice không match nên bị bỏ qua”.

Hai đoạn trên chỉ minh họa method advice bên trong lớp `@Aspect`; chúng không phải hai tệp Java độc lập và không nên cùng đặt trong ứng dụng chạy bình thường. Bài này tập trung vào tập designator hợp lệ và không hợp lệ **trong Spring AOP**, phương án thay thế dùng Spring-managed bean, cách tái hiện lỗi và cách chẩn đoán. Không mở rộng sang hướng dẫn sử dụng toàn bộ AspectJ weaving.

## 2. Vì sao Spring AOP giới hạn method execution?

Spring AOP vận hành theo mô hình **proxy**. Spring bean được auto-proxy creator xét sau khi container tạo bean; nếu có advisor phù hợp, caller nhận một JDK dynamic proxy hoặc class-based proxy. Khi caller gọi một method **qua proxy**, interceptor chain xử lý advice rồi chuyển tiếp tới target.

~~~text
Caller
   |
   | gọi bean do Spring cung cấp
   v
Spring AOP proxy
   |
   | method invocation / advisor matching
   v
@Before / @Around / ... advice
   |
   v
Target bean.method(...)
~~~

Những hoạt động mà lời gọi đi qua proxy *không trực tiếp cung cấp* thành join point gồm:

- Chỉ dẫn tại **vị trí gọi** method trong code của một caller cụ thể, khác với sự kiện method target đang được thực thi.
- Đọc/ghi **field** của object (`get`/`set` theo nghĩa field join point, không phải method có tên `getX`/`setX`).
- Constructor initialization và static initialization.
- Phân tích bao quanh một join point theo control flow của toàn call stack bằng chính designator `cflow`/`cflowbelow`.
- Các designator cần mức thông tin bytecode/lexical context mà proxy Spring AOP không biểu diễn.

Do đó, biểu thức `execution(...)` mới là designator chủ yếu trong Spring AOP. Spring vẫn cho phép ràng buộc kiểu, annotation, argument và bean name **trên method execution đã có thể được proxy**.

Một phân biệt cần ghi nhớ: **`call(...)` và `execution(...)` không cùng nghĩa**. `call` mô tả *nơi code phát ra lời gọi*, còn `execution` mô tả *sự kiện thực thi method*. Trong Spring AOP, lời gọi từ bên ngoài tới một Spring proxy có thể kích hoạt advice trên **execution join point**; Spring không hỗ trợ trực tiếp chọn những call site bằng `call(...)`.

## 3. Danh sách designator Spring AOP hỗ trợ

Theo tài liệu chính thức của Spring Framework 6.2, Spring AOP chấp nhận **chín** AspectJ pointcut designators sau:

| Designator | Ý nghĩa khi sử dụng trong Spring AOP | Ví dụ biểu thức |
| --- | --- | --- |
| `execution` | Chọn method execution theo method signature | `execution(public * demo.OrderService.*(..))` |
| `within` | Thu hẹp phạm vi theo kiểu khai báo method | `within(demo..service..*)` |
| `this` | Kiểm tra **proxy reference** là instance của kiểu được chỉ định | `this(demo.OrderOperations)` |
| `target` | Kiểm tra **target object** sau proxy là instance của kiểu đó | `target(demo.OrderService)` |
| `args` | Chọn theo loại runtime của các argument được truyền | `args(java.lang.String)` |
| `@target` | Kiểm tra annotation trên loại runtime của target object | `@target(demo.AuditedType)` |
| `@args` | Kiểm tra annotation trên loại runtime của argument object | `@args(demo.ValidatedPayload)` |
| `@within` | Thu hẹp method execution trong các kiểu mang annotation | `@within(demo.AuditedType)` |
| `@annotation` | Chọn method có annotation tương ứng | `@annotation(demo.AuditedOperation)` |

Các ví dụ chứa `demo.OrderOperations`, `demo.AuditedType` và `demo.ValidatedPayload` chỉ nhằm minh họa syntax và cần những type/annotation tương ứng trong project khi dùng thật. Cần kiểm tra interface/proxy type với `this`, target implementation với `target`; hai thứ **khác nhau trong Spring AOP** do có object proxy nằm giữa caller và target.

Spring AOP còn bổ sung designator **đặc thù của Spring**:

~~~text
bean(orderService)
bean(*Service)
~~~

`bean(namePattern)` giới hạn theo **tên Spring bean** và có hỗ trợ wildcard `*`. Ví dụ:

~~~java
@Before("execution(* demo.OrderService.submit(..)) && bean(orderService)")
public void beforeOrderService() {
    // Áp dụng khi bean name và method execution đều khớp.
}
~~~

Đây là method advice minh họa trong `@Aspect`. `bean()` là extension của **Spring AOP**, không phải designator chuẩn để dùng trong native AspectJ weaving. Nó có ý nghĩa vì Spring container quản lý **bean name** và tạo proxy cho **instance cụ thể**.

Ký hiệu logic có thể kết hợp:

- `&&`: cả hai điều kiện phải khớp;
- `||`: ít nhất một điều kiện khớp;
- `!`: phủ định;
- dấu ngoặc đơn khi cần nhóm điều kiện.

Chỉ sử dụng operator không làm một PCD **không hỗ trợ** trở thành hợp lệ. Ví dụ `execution(...) && call(...)` vẫn chứa `call` và bị parser Spring AOP từ chối.

## 4. Danh sách 14 designator không được Spring AOP hỗ trợ

Tài liệu Spring Framework nêu **14** designator thuộc ngôn ngữ AspectJ đầy đủ nhưng **không được Spring AOP hỗ trợ** ở lớp pointcut expression:

| Designator | Mục đích trong ngôn ngữ AspectJ đầy đủ | Kết luận với Spring AOP |
| --- | --- | --- |
| `call` | Chọn điểm **gọi** method/constructor | Không được chấp nhận |
| `get` | Chọn join point **đọc field** | Không được chấp nhận |
| `set` | Chọn join point **ghi field** | Không được chấp nhận |
| `preinitialization` | Chọn giai đoạn trước khi constructor initialization | Không được chấp nhận |
| `staticinitialization` | Chọn static initialization của type | Không được chấp nhận |
| `initialization` | Chọn constructor initialization | Không được chấp nhận |
| `handler` | Chọn điểm xử lý exception handler | Không được chấp nhận |
| `adviceexecution` | Chọn khi một advice đang thực thi | Không được chấp nhận |
| `withincode` | Chọn join point theo phạm vi lexical bên trong code của method/constructor | Không được chấp nhận |
| `cflow` | Chọn join point nằm trong control flow của join point khác | Không được chấp nhận |
| `cflowbelow` | Chọn các join point *bên dưới* join point nguồn trong control flow | Không được chấp nhận |
| `if` | Điều kiện động đặc biệt trong biểu thức AspectJ | Không được chấp nhận |
| `@this` | Chọn theo annotation trên `this` trong ngữ nghĩa AspectJ | Không được chấp nhận |
| `@withincode` | Chọn theo annotation của vùng code chứa join point | Không được chấp nhận |

**Đối chiếu chính xác:** `this` được hỗ trợ, nhưng `@this` không; `within` được hỗ trợ, nhưng `withincode` không; `@within` được hỗ trợ, nhưng `@withincode` không. Các cặp này dễ nhầm khi sửa code từ native AspectJ sang Spring AOP.

Khi đưa một PCD không hỗ trợ vào biểu thức Spring AOP, Spring ném `IllegalArgumentException` trong quá trình pointcut được phân tích hoặc khởi tạo. **Không cần có bean hay method thực sự khớp** mới chứng minh được PCD đó không hợp lệ; lỗi đến từ bộ phân tích designator. Thời điểm lỗi được quan sát có thể khác nhau tùy cách khởi tạo pointcut và application context.

Danh sách ở đây dựa vào **Spring Framework 6.2**; khi nâng phiên bản, luôn đọc tài liệu của phiên bản đang chạy thay vì giả định tập designator cố định vĩnh viễn.

## 5. Các nhầm lẫn đặc biệt hay gặp

### 5.1. `call` khác `execution`

Hai pointcut:

~~~text
call(* demo.OrderService.submit(..))
execution(* demo.OrderService.submit(..))
~~~

không thể thay thế nhau tùy tiện về **ngữ nghĩa**. Ví dụ sau chọn method execution trên Spring bean, trong khi designator `call` thuộc nhóm không hỗ trợ. Nếu mục tiêu chỉ là log trước khi một Spring service method chạy, giải pháp Spring AOP tự nhiên là `execution(...)` (có thể kết hợp `within`, `@annotation` hoặc `bean()`).

Nếu mục tiêu thực sự là “chỉ advice khi method được gọi **từ một call site cụ thể**”, `execution` sẽ **không bảo đảm cùng phạm vi**. Spring AOP proxy không quan sát call site lexical ở mức `call`. Hãy thiết kế lại ranh giới service/adapter hoặc đánh giá kỹ cơ chế weaving riêng khi yêu cầu thật sự cần call-site join points.

### 5.2. `get`/`set` không tương đương Java getters/setters

Với Java class:

~~~java
public class AccountService {
    private int balance;

    public int getBalance() {
        return balance;
    }

    public void setBalance(int amount) {
        this.balance = amount;
    }
}
~~~

Điểm cần phân biệt:

- `get(int demo.AccountService.balance)` và `set(int demo.AccountService.balance)` là **field access** designators của AspectJ; không hoạt động trong Spring AOP.
- `execution(* demo.AccountService.getBalance())` hoặc `execution(* demo.AccountService.setBalance(..))` là **method execution** pointcut, Spring AOP có thể áp dụng nếu method được gọi qua Spring proxy.
- Thao tác trực tiếp `balance = amount` hoặc `int value = balance` bên trong target vẫn là field read/write. Spring AOP proxy không chặn field access chỉ vì các method khác của class đã được proxy.

Đoạn Java minh họa class có thể biên dịch độc lập, nhưng chỉ trở thành target của Spring AOP nếu được đăng ký như Spring bean và có advisor phù hợp.

### 5.3. Constructor/static initialization không phải method execution

~~~text
preinitialization(demo.OrderService.new(..))
initialization(demo.OrderService.new(..))
staticinitialization(demo.OrderService)
~~~

Ba biểu thức minh họa designator không hỗ trợ. Spring tạo bean qua constructor/factory và có thể post-process để trả proxy **sau giai đoạn tạo đối tượng**; proxy thông thường không chặn constructor execution hoặc static initializer thành join point advice.

Nếu cần hành vi khi Spring tạo bean, dùng cơ chế **lifecycle/extension point của container** đúng nhiệm vụ như `@PostConstruct`, init callback, `BeanPostProcessor` hoặc event ứng dụng; đừng ép một biểu thức `initialization(...)` vào `@Before` rồi mong Spring AOP hiểu.

Một điều quan trọng: `@PostConstruct` là lifecycle callback, **không phải** cách làm cho Spring AOP bắt mọi lần khởi tạo object. Khi một method được gọi ngay từ constructor/init của target, lời gọi thường cũng **chưa đi qua Spring proxy cuối cùng**.

### 5.4. `handler` không phải lời khuyên để advice exception

`handler(...)` dùng để chọn exception handler join point trong ngôn ngữ AspectJ đầy đủ; Spring AOP không hỗ trợ. Nếu mục tiêu là quan sát **exception được ném từ method execution của Spring bean**, dùng advice `@AfterThrowing`:

~~~java
@AfterThrowing(
    pointcut = "execution(* demo.OrderService.submit(..))",
    throwing = "ex"
)
public void whenSubmitFails(IllegalStateException ex) {
    System.out.println("EXCEPTION: " + ex.getMessage());
}
~~~

Đoạn trên là **method advice minh họa**, cần nằm trong Spring bean `@Aspect` với import `org.aspectj.lang.annotation.AfterThrowing`. Nó chạy khi method execution được advice ném ngoại lệ phù hợp và lời gọi qua proxy. Nó **không** tương đương bắt đầu execution của mọi `catch` block, cũng không quan sát exception đã được target tự catch/nuốt bên trong.

### 5.5. `cflow`/`cflowbelow`: phân biệt PCD với Spring Pointcut API

~~~text
cflow(execution(* demo.Controller.handle(..)))
cflowbelow(execution(* demo.Controller.handle(..)))
~~~

Các chuỗi trên không phải Spring AOP expressions hợp lệ. Spring AOP không nhận AspectJ PCD `cflow` hoặc `cflowbelow`.

**Ngoại lệ dễ bị hiểu sai:** Spring còn cung cấp `org.springframework.aop.support.ControlFlowPointcut` trong **Pointcut API programmatic**. Nó là một Spring API có thể chọn method execution theo thông tin call stack (kém mạnh hơn semantics của `cflow` AspectJ). Việc tồn tại **class `ControlFlowPointcut` không làm designator `cflow(...)` được phép dùng trong `@Pointcut`**. Đây là hai giao diện/cơ chế khác nhau.

Nếu mục tiêu là lấy một request-scoped context hoặc correlation ID khi method Spring service được gọi, cân nhắc truyền context rõ ràng hoặc dùng tầng phù hợp như filter/interceptor/request context, thay vì cố chuyển toàn bộ AspectJ call flow expression vào Spring AOP.

### 5.6. `withincode` và `within` không giống nhau

- `within(demo..service..*)` **được hỗ trợ**: giới hạn method execution theo **type scope**.
- `withincode(* demo.Controller.handle(..))` **không được hỗ trợ**: cần match join point theo phần code lexical nằm **bên trong** method.
- `@within(demo.AuditedType)` **được hỗ trợ**: type mang annotation tương ứng.
- `@withincode(demo.AuditedAction)` **không được hỗ trợ**: annotation trên code chứa join point.

Khi cần giới hạn pointcut theo **service layer**, dùng `execution(* demo..service..*.*(..))` hoặc `within(...)` kết hợp `execution(...)`. Nếu chỉ muốn khi một caller cụ thể gọi, cách khớp theo type/method execution không cung cấp đúng mô hình call-site của `withincode`.

### 5.7. `this`/`@this` và `target`/`@target`

Với Spring AOP proxy, `this(Type)` xét **proxy object**, còn `target(Type)` xét **target object**. Ngữ nghĩa này đặc biệt quan trọng khi JDK dynamic proxy chỉ implements interface, không phải subclass của implementation.

~~~text
this(demo.OrderOperations)      // Spring AOP hỗ trợ
target(demo.OrderService)       // Spring AOP hỗ trợ
@target(demo.AuditedType)       // Spring AOP hỗ trợ
@this(demo.AuditedType)         // Spring AOP không hỗ trợ
~~~

Dấu `@` tạo ra designator khác; đừng sửa `@this` thành `this` nếu mục tiêu ban đầu của bạn là match theo **annotation**. Hãy quyết định cần kiểm tra type hay annotation của target, rồi chọn `target` hoặc `@target` phù hợp.

## 6. Project Java 17+ minh họa pointcut hợp lệ và lỗi không hỗ trợ

Ví dụ sử dụng **Spring Framework 6.2.0**, **AspectJ Weaver 1.9.22**, Maven và JDK 17+. Ta tạo service Spring bean có một advice hợp lệ, rồi **tự kiểm tra một pointcut `call` không hợp lệ bằng API Spring** mà không làm application context thất bại lúc startup.

Cấu trúc:

~~~text
spring-aop-pcd-check/
├── pom.xml
└── src/main/java/demo/
    ├── Demo.java
    ├── OrderService.java
    └── TraceAspect.java
~~~

### 6.1. Cấu hình Maven

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-pcd-check</artifactId>
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

`spring-context` cung cấp Java configuration và container, `spring-aop` chứa proxy/pointcut API, `aspectjweaver` giúp Spring phân tích cú pháp kiểu `@AspectJ`. Dependency này **không tự kích hoạt AspectJ bytecode weaving**.

### 6.2. Business service

Tạo `src/main/java/demo/OrderService.java`:

~~~java
package demo;

public class OrderService {
    public void submit(String orderId) {
        System.out.println("SERVICE: submit " + orderId);
    }
}
~~~

Class này không `final`, method `submit` là `public`; Spring có thể bọc bằng class-based proxy để can thiệp vào method execution.

### 6.3. Aspect có expression được hỗ trợ

Tạo `src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* demo.OrderService.submit(..))"
          + " && within(demo.OrderService)")
    public void beforeSubmit() {
        System.out.println("ADVICE: before submit");
    }
}
~~~

Cả `execution` và `within` đều nằm trong tập Spring AOP hỗ trợ. Điều kiện `within(demo.OrderService)` còn minh họa cách ràng buộc type scope hợp lệ. Hai điều kiện khớp cùng method trong ví dụ; method không bị chặn hai lần chỉ vì biểu thức có hai designators.

### 6.4. Kiểm tra proxy rồi bắt lỗi parser đối với call(...)

Tạo `src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Demo {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Demo.class)) {
            OrderService service = context.getBean(OrderService.class);
            boolean proxied = AopUtils.isAopProxy(service);

            System.out.println("PROXY: " + proxied);
            service.submit("A-01");

            AspectJExpressionPointcut unsupported =
                    new AspectJExpressionPointcut();
            unsupported.setExpression(
                    "call(* demo.OrderService.submit(..))");

            boolean rejected = false;
            try {
                unsupported.getClassFilter().matches(OrderService.class);
            } catch (IllegalArgumentException ex) {
                rejected = true;
                System.out.println("UNSUPPORTED_CALL: rejected");
            }

            if (!proxied || !rejected) {
                throw new IllegalStateException(
                        "Spring AOP pointcut check failed");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Cơ chế trong đoạn này:

1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` cho phép Spring tạo class proxy cho bean `OrderService`. `@Bean traceAspect()` đăng ký aspect với advisor hợp lệ. Chỉ `@Aspect` mà không đăng ký làm bean sẽ không đủ.
2. `service.submit("A-01")` đi qua proxy nên `@Before` chạy trước target method.
3. `new AspectJExpressionPointcut()` là API Spring AOP để diễn giải một AspectJ pointcut expression. Ta **cố tình** đặt biểu thức chứa `call(...)` không hỗ trợ. Object pointcut này **không được đăng ký làm advisor trong context**, để không gây lỗi khởi động Spring application.
4. `getClassFilter().matches(OrderService.class)` buộc pointcut thực hiện bước phân tích/đánh giá điều kiện type; parser Spring AOP từ chối `call` và ném `IllegalArgumentException`.
5. `catch` chỉ in một marker ổn định, không in exception message vốn có thể thay đổi theo phiên bản parser. Biến `rejected` buộc ứng dụng phải chứng minh đã nhận lỗi mới in `CHECK: PASS`.

### 6.5. Lệnh chạy và output

Trong thư mục `spring-aop-pcd-check`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã ví dụ chủ động in ra:

~~~text
PROXY: true
ADVICE: before submit
SERVICE: submit A-01
UNSUPPORTED_CALL: rejected
CHECK: PASS
~~~

Thông báo từ Maven/framework có thể xuất hiện ngoài năm dòng trên. Hai kết quả cần phân biệt:

- `PROXY: true` và `ADVICE: before submit` xác nhận biểu thức `execution && within` **được Spring AOP chấp nhận và advice chạy qua proxy**.
- `UNSUPPORTED_CALL: rejected` chứng minh `call(...)` bị Spring AOP pointcut parser từ chối; không phải do `OrderService` thiếu method hoặc do advisor không khớp.

Đây là ví dụ tái hiện có kiểm soát và kiểm tra điểm cốt lõi của bài học: **pointcut syntax hợp lệ đối với native AspectJ chưa chắc hợp lệ đối với Spring AOP**.

## 7. Nếu viết sai designator trực tiếp trong @Aspect thì sao?

Nếu bạn sửa advice của `TraceAspect` thành:

~~~java
@Aspect
public class TraceAspect {
    @Before("call(* demo.OrderService.submit(..))")
    public void beforeSubmit() {
        System.out.println("ADVICE: before submit");
    }
}
~~~

đây là **đoạn thay thế minh họa cho class aspect**, không phải phương án chạy bình thường. Khi Spring chuẩn bị advisor/pointcut cho aspect và nhận thấy designator `call` không hỗ trợ, việc tạo bean/context hoặc khởi tạo AOP advisor sẽ thất bại với `IllegalArgumentException` trong chuỗi nguyên nhân (đôi khi được bọc trong exception tạo bean của Spring). Không có chuyện Spring âm thầm đổi `call` thành `execution`.

Với `@Pointcut` đặt tên cũng tương tự:

~~~java
@Pointcut("cflow(execution(* demo.OrderService.submit(..)))")
public void underSubmitFlow() {
}
~~~

Spring AOP không hỗ trợ `cflow` trong biểu thức pointcut, kể cả khi expression bên trong `execution(...)` được hỗ trợ. `@Pointcut` chỉ đặt tên/ghép biểu thức, không thêm khả năng nhận diện join point mới vào Spring proxy. Đây là snippet minh họa đặt trong `@Aspect`, không thêm vào project chuẩn ở phần 6.

Nếu muốn kiểm tra các designators không hỗ trợ khác bằng API `AspectJExpressionPointcut`, chỉ thay `setExpression(...)` trong demo rồi giữ cơ chế bắt `IllegalArgumentException`. Một số expression có thêm vấn đề cú pháp/kiểu tham số; hãy dùng ví dụ theo tài liệu và xem root cause thật khi debug. Đừng coi mọi parser error đều có cùng thông điệp.

## 8. Chuyển thiết kế pointcut sang khả năng Spring AOP

Không phải mọi nhu cầu có từ khóa AspectJ đều chuyển nguyên xi bằng cách thay tên designator. Bảng dưới đây phân biệt **mục đích của người viết** và **giải pháp Spring**:

| Nhu cầu ban đầu | Designator không hỗ trợ | Cách tiếp cận trong Spring AOP |
| --- | --- | --- |
| Log trước khi service method chạy | `call` | Advice `execution(...)` trên Spring bean, có thể kết hợp `bean(name)`/`within(...)` |
| Ghi log các lần setter method được gọi | `set` nếu đang nói về field assignment | `execution(* set*(..))` chọn **setter method**, không bắt các lần gán field trực tiếp |
| Quan sát getter method qua proxy | `get` nếu đang nói về field read | `execution(* get*(..))` chọn **getter method**, không bắt field read |
| Làm gì đó ngay khi Spring bean hoàn thành init | `initialization`, `preinitialization` | Lifecycle callback Spring hoặc `BeanPostProcessor`, không gọi đó là AspectJ join point |
| Cấu hình khi class được load/static initialize | `staticinitialization` | Đặt cấu hình khởi tạo ở application startup/lifecycle phù hợp; không mong AOP proxy intercept static initializer |
| Ghi nhận exception method trả ra | `handler` | `@AfterThrowing` trên Spring method execution; không bắt mọi catch handler |
| Chọn method theo layer | `withincode` | `execution(...)`/`within(...)` hoặc `bean(...)` để giới hạn target layer; không khớp theo source call site |
| Dựa trên call stack của caller | `cflow`, `cflowbelow` | Xem nhu cầu context propagation/adapter rõ ràng; nếu dùng Spring Pointcut API, `ControlFlowPointcut` là cơ chế khác và có hạn chế riêng |
| Chọn method theo annotation trên proxy this | `@this` | Xem `@target`, `@within`, `@annotation` theo đúng mục tiêu; không coi chúng là thay thế tương đương từng byte |
| Chọn vùng code mang annotation | `@withincode` | Đánh dấu method được advice bằng `@annotation` hoặc class bằng `@within`/`@target` rồi chặn qua proxy |
| Điều kiện runtime tùy biến trong pointcut | `if` | Chuyển điều kiện sang code advice `@Around`/`@Before` hoặc cơ chế chọn method phù hợp của Spring; không giả định cùng semantics |
| Chọn execution của advice khác | `adviceexecution` | Xem instrumentation/observability hạ tầng phù hợp thay vì advice lên chính AOP internals |

**Lưu ý:** bảng là gợi ý **thiết kế lại** theo Spring AOP, không khẳng định `execution(get*(..))` tương đương field `get`, hay `@AfterThrowing` tương đương exception `handler`. Nếu yêu cầu đích thực cần call-site, field-access, constructor hay lexical join points thì giới hạn proxy của Spring AOP không đủ; cần **đánh giá một cơ chế khác có chi phí/đặc tính riêng**. Nội dung bài không triển khai cơ chế ngoài Spring AOP.

## 9. Tình huống điển hình trong ứng dụng Spring

### 9.1. Chỉ log các method có @Audited

Thay vì mong `@withincode` kiểm tra annotation ở nơi gọi, hãy đánh dấu đúng **method cần advice**:

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
}
~~~

và bên trong một bean aspect:

~~~java
@Before("@annotation(demo.Audited)")
public void beforeAudited() {
    System.out.println("AUDIT");
}
~~~

Đây là hai snippet minh họa; cần import `java.lang.annotation.Retention`, `RetentionPolicy`, `Target`, `ElementType` cho annotation, và `@Aspect`/`@Before`/đăng ký bean cho advice. Chúng không phải project chạy độc lập. `@annotation` chỉ chọn **method mang annotation** khi method execution đi qua Spring proxy.

### 9.2. Phạm vi theo tên bean

Nếu các service có tên `orderService`, `paymentService`, dùng:

~~~java
@Before("execution(public * demo..service..*.*(..)) && bean(*Service)")
public void beforeBusinessCall() {
    // ...
}
~~~

Biểu thức này chỉ mang tính ví dụ: cú pháp package trong `execution` phải trùng package thực của project và bean name phải match suffix `Service`. `bean(*Service)` là extension đặc thù Spring, thích hợp khi Spring-managed bean name là một phần của convention. Nó không áp dụng cho object được tạo ngoài container.

### 9.3. Giới hạn theo loại proxy

Với JDK dynamic proxy, `this(demo.OrderOperations)` có thể match proxy theo interface; `target(demo.OrderService)` match target implementation. Với CGLIB proxy, proxy cũng có thể là subclass của target. Tránh nhầm `@this` (không hỗ trợ) với `this` (hỗ trợ) và tránh cast JDK proxy thành implementation class.

### 9.4. Lời gọi nội bộ không được chặn

~~~java
public class OrderService {
    public void batch() {
        this.submit("A-02");
    }

    public void submit(String orderId) {
        System.out.println("SERVICE: submit " + orderId);
    }
}
~~~

Cho dù `execution(* demo.OrderService.submit(..))` là pointcut hợp lệ, lời gọi `this.submit()` trong target **không quay lại qua Spring AOP proxy**. Đây không phải vì designator `execution` không được hỗ trợ, mà là vì cách caller đi qua ranh giới proxy. Cách sửa nên ở kiến trúc gọi qua một Spring-managed collaborator khác, không thêm `call` vào pointcut.

## 10. Phân biệt lỗi parser với lỗi pointcut không match

Khi startup hoặc lời gọi method không đúng mong đợi, có ba lớp lỗi khác nhau:

### A. Designator không hỗ trợ

Dấu hiệu:

- Pointcut có `call`, `set`, `get`, `cflow` hoặc một trong 14 PCD không hỗ trợ;
- `IllegalArgumentException` xuất hiện trong quá trình Spring chuẩn bị/phân tích expression;
- Ứng dụng có thể không khởi tạo được aspect/auto-proxy creator hoặc context.

Cách xử lý: tìm **root cause**, xác định designator không hỗ trợ và chuyển về một thiết kế phù hợp Spring AOP; đừng chỉ nới wildcard hoặc sửa method name.

### B. Designator được hỗ trợ nhưng biểu thức sai hoặc method không match

Dấu hiệu:

- Có `execution(...)`/`@annotation(...)`/`within(...)` hợp lệ, nhưng package/class/method/annotation không khớp, hoặc expression sai cú pháp;
- Nếu sai syntax vẫn có thể ném exception từ parser; nếu cú pháp đúng nhưng không match, Spring có thể không tạo proxy cho bean đó;
- Không có dòng advice vì advisor không áp dụng.

Cách xử lý: kiểm tra package đầy đủ, interface/implementation, runtime annotation, type/bean name, cú pháp tham số `(..)` và nơi Spring đăng ký aspect.

### C. Advisor match nhưng lời gọi không đi qua proxy

Dấu hiệu:

- Bean có advisor/proxy, nhưng code gọi `this.submit()`, gọi raw target, method `final`/`private` hay object `new` ngoài Spring;
- Logic method vẫn chạy nhưng advice không chạy, không nhất thiết có parser exception;
- Proxy strategy JDK/CGLIB và scope Spring bean ảnh hưởng những method có thể intercept.

Cách xử lý: đo `AopUtils.isAopProxy(bean)` và gọi method **qua reference do Spring container cung cấp**; tránh kết luận từ việc pointcut trông đúng.

### Checklist chẩn đoán

1. Đọc **toàn bộ biểu thức pointcut** trong `@Pointcut`, `@Before`, XML hoặc API `AspectJExpressionPointcut`.
2. Đối chiếu designator với tập Spring AOP hỗ trợ: chín PCD chuẩn cộng `bean()` extension.
3. Nếu là một trong 14 PCD không hỗ trợ, sửa **thiết kế**; không sửa kiểu Java import để cố làm parser nhận.
4. Xem exception ở **root cause**, đặc biệt `IllegalArgumentException` từ pointcut parser.
5. Kiểm tra Spring bean, `@EnableAspectJAutoProxy` và việc `@Aspect` đã được đăng ký như Spring bean.
6. Kiểm tra loại proxy `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`; đối chiếu method có khả năng được intercept.
7. Gọi một method qua proxy rồi dùng marker hoặc counter trong advice để xác minh thực thi.
8. Với case cần call-site/field-access/constructor, ghi rõ giới hạn Spring AOP và chọn cơ chế phù hợp thay vì giả vờ có PCD tương đương.
9. Nếu ứng dụng còn Spring Boot AOP auto-configuration, transaction, cache hoặc nhiều context, đối chiếu **ApplicationContext và cấu hình runtime thực tế**.
10. Sau sửa, chạy integration test thực hiện external caller → Spring proxy → method; đừng chỉ dựa trên syntax highlight trong IDE.

## 11. Các câu hỏi tự kiểm tra

**1. Spring AOP có hỗ trợ `call(...)` vì dùng cú pháp @AspectJ không?**  
Không. Spring AOP hiểu **một tập con** PCD và chỉ chặn method execution trên Spring beans qua proxy.

**2. `execution(* set*(..))` có thay thế được `set(* ..*)` không?**  
Không tương đương. `execution(set*(..))` chỉ match **setter method execution**; `set(...)` của AspectJ chọn field write.

**3. Nếu dùng `execution(...) && cflow(...)`, Spring chỉ bỏ qua cflow rồi chạy execution chứ?**  
Không. `cflow` nằm trong nhóm PCD không hỗ trợ; biểu thức bị parser từ chối, không phải điều kiện bị bỏ qua.

**4. `within(...)` được hỗ trợ còn `withincode(...)` thì sao?**  
`within` được hỗ trợ để thu hẹp scope kiểu; `withincode` không được Spring AOP parser chấp nhận.

**5. `this(...)` và `@this(...)` có giống nhau không?**  
Không. Trong Spring AOP, `this` kiểm tra proxy type; `@this` là PCD không được hỗ trợ.

**6. Vì sao Spring hỗ trợ `bean(name)` nhưng native AspectJ weaving không?**  
Spring AOP có khái niệm **Spring bean name**/instance trong container; `bean` là extension của Spring, không phải PCD chuẩn của native AspectJ.

**7. Có `ControlFlowPointcut` thì `cflow(...)` trong @Pointcut đã hợp lệ chưa?**  
Chưa. `ControlFlowPointcut` thuộc Spring Pointcut API programmatic, không làm AspectJ designator `cflow` trở thành hợp lệ trong parser Spring AOP.

**8. Lỗi `IllegalArgumentException` do PCD không hỗ trợ có nghĩa Spring AOP không chạy trên project?**  
Không. Nó nói một biểu thức không nằm trong phần ngôn ngữ Spring AOP hỗ trợ. Ví dụ `execution && within` vẫn có thể chạy bình thường, như project ở phần 6.

**9. Khi một method có pointcut hợp lệ nhưng advice không chạy, nguyên nhân duy nhất có phải unsupported designator không?**  
Không. Kiểm tra cả advisor có match, bean có thuộc Spring context và lời gọi có đi qua proxy hay không.

**10. Nếu cần intercept việc đọc field của mọi object, chỉ đổi JDK proxy sang CGLIB là đủ?**  
Không. Cả hai vẫn là **proxy Spring AOP cho method execution**. Chuyển loại proxy không biến field read thành join point.

## 12. Tài liệu Spring Framework chính thức

- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): danh sách 9 PCD chuẩn được hỗ trợ, 14 PCD không được hỗ trợ, `bean` extension và lưu ý Spring AOP method execution.
- [Spring Framework 6.2 — @AspectJ support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring dùng syntax AspectJ nhưng runtime AOP vẫn dựa trên Spring proxy.
- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Before`/`@AfterThrowing`/`@Around` trên các execution join point hợp lệ.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy, self-invocation, method restrictions.
- [Spring Framework 6.2 — Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): `AspectJExpressionPointcut` và Spring API `ControlFlowPointcut` (khác `cflow` PCD).


</details>

- [Quay lại đầu trang](#back-to-top)
