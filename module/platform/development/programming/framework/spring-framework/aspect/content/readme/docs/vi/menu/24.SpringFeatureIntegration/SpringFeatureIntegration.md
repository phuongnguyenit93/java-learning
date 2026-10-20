<a id="back-to-top"></a>

# Tích hợp cơ chế Spring

## Menu
- [Quan hệ giữa aspect tự viết và proxy quản lý giao dịch](#spring-feature-integration-001)
- [Thứ tự aspect đo đạc, retry và interceptor giao dịch](#spring-feature-integration-002)
- [Rủi ro retry sai phạm vi giao dịch hoặc lặp tác dụng phụ](#spring-feature-integration-003)
- [Quan hệ với proxy của @Cacheable và thời điểm phương thức đích thực sự chạy](#spring-feature-integration-004)
- [Quan hệ với @Async và ranh giới chuyển luồng](#spring-feature-integration-005)
- [Advice quanh phương thức trả Future khác xử lý khi Future hoàn tất](#spring-feature-integration-006)
- [Advice quanh phương thức reactive khác xử lý lúc subscribe](#spring-feature-integration-007)
- [Tái sử dụng cơ chế Spring có sẵn trước khi viết aspect tương đương](#spring-feature-integration-008)
- [Phân biệt AOP với filter, interceptor web và callback của framework](#spring-feature-integration-009)
- [Thiết kế thử nghiệm để xác định chuỗi advisor thực tế](#spring-feature-integration-010)

## <a id="spring-feature-integration-001">Quan hệ giữa aspect tự viết và proxy quản lý giao dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa aspect tự viết và proxy quản lý giao dịch trong Spring

## 1. Vấn đề thực tế: cùng một method có cả @Aspect và @Transactional

Trong ứng dụng Spring, một phương thức service có thể đồng thời được Spring AOP áp dụng **advice do lập trình viên viết** (logging, profiling, audit, xác thực, metrics...) và **advice quản lý giao dịch** của `@Transactional`. Đây là hai loại xử lý có thể cùng nằm trong **chuỗi interceptor/advisor của AOP proxy**.

Ví dụ:

```java
@Service
public class OrderService {
    @Transactional
    public void placeOrder() {
        // Ghi dữ liệu qua JDBC/JPA...
    }
}

@Aspect
@Component
public class AuditAspect {
    @Around("execution(* com.example.OrderService.placeOrder(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("before");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("after");
        }
    }
}
```

Đây là **hai snippet minh họa** cho nguyên lý, không phải project chạy độc lập; ví dụ hoàn chỉnh ở phần 5.

Câu hỏi cần giải quyết không chỉ là "aspect nào chạy trước?" mà còn:

- `before` của aspect có chạy **trước hay sau khi transaction bắt đầu**?
- `finally`/`@AfterReturning` của aspect có chạy **trước hay sau khi transaction commit**?
- Khi aspect bắt, nuốt hoặc ném exception, **rollback có bị ảnh hưởng**?
- Spring có tạo **một proxy chứa nhiều advisor**, hay luôn có hai proxy lồng nhau?
- Làm sao cấu hình **thứ tự** rõ ràng và kiểm chứng bằng code?

Trọng tâm bài này là cơ chế của **Spring AOP và Spring Transaction Management**. Những khái niệm AOP chung chỉ được đề cập khi cần hiểu kết quả thực thi trong Spring.

## 2. @Transactional được thực thi như một advice của Spring AOP

Ở chế độ mặc định `@EnableTransactionManagement(mode = AdviceMode.PROXY)`, `@Transactional` chỉ mang **metadata khai báo**; Spring dựng hạ tầng quản lý giao dịch để phát hiện các method khớp và gắn transactional advisor vào proxy.

Các thành phần cốt lõi:

| Thành phần Spring | Nhiệm vụ |
| --- | --- |
| `@EnableTransactionManagement` | Bật hạ tầng xử lý annotation `@Transactional` trong application context tương ứng |
| `@Transactional` | Mô tả propagation, isolation, readOnly, rollback rules, transaction manager... |
| `BeanFactoryTransactionAttributeSourceAdvisor` | Advisor có pointcut chọn method với transaction metadata |
| `TransactionInterceptor` | Method interceptor mở/join/suspend transaction, chạy phần tiếp theo, rồi commit/rollback theo quy tắc |
| `PlatformTransactionManager` | Quản lý transaction vật lý/cục bộ tương ứng với JDBC, JPA,... |
| Spring AOP proxy | Nhận method call từ client và đi qua chuỗi advisor/interceptor trước khi tới target |

Luồng nhìn từ ngoài, nếu transaction advisor đi ngay trước target:

```text
client
  -> Spring AOP proxy
      -> TransactionInterceptor
          -> TransactionManager: begin / join transaction
          -> target.serviceMethod(...)
          -> TransactionManager: commit hoặc rollback
  <- return hoặc throw
```

Một custom `@Aspect` cũng được Spring chuyển thành các advisor/advice phù hợp. Khi **cả aspect và transaction advisor áp dụng cùng Spring bean/method**, Spring AOP sắp xếp chúng thành chuỗi gọi. Không phải cứ có hai loại advice thì Spring chắc chắn tạo hai proxy; cấu hình auto-proxy thông thường có thể dùng **một proxy duy nhất với nhiều advisor**. Trường hợp tự bọc proxy bằng `ProxyFactory`, cấu hình nhiều tầng context hoặc cơ chế proxy khác có thể tạo **proxy lồng nhau**; khi đó phải xem thực tế cấu hình thay vì giả định.

**Điểm quan trọng:** `@Transactional` được áp dụng theo invocation **đi qua proxy**. Gọi `this.someTransactionalMethod()` trong cùng target không tự đi qua `TransactionInterceptor` một lần nữa. Custom Spring AOP advice cũng gặp giới hạn self-invocation tương tự.

## 3. Thứ tự advisor quyết định ranh giới giao dịch

Spring sắp xếp advice theo **precedence**. Với `@Order(n)` hoặc `Ordered.getOrder()`, **số càng nhỏ thì precedence càng cao**, tức ở ngoài hơn khi lời gọi đi vào chuỗi. Với `@Around`, advice ngoài nhất chạy đầu tiên trước `proceed()` và cuối cùng sau `proceed()`.

Thứ tự của transaction advisor cấu hình qua:

```java
@EnableTransactionManagement(order = 100)
```

Nếu có hai aspect:

```java
@Aspect
@Order(50)
public class OuterAspect { /* @Around(...) */ }

@Aspect
@Order(200)
public class InnerAspect { /* @Around(...) */ }
```

thì với method đồng thời match cả ba advisor:

```text
Call tới proxy
  -> @Order(50)  OuterAspect: trước proceed() [transaction CHƯA bắt đầu]
    -> transaction advisor order=100
      -> bắt đầu transaction
        -> @Order(200) InnerAspect: trước proceed() [transaction ĐANG hoạt động]
          -> target @Transactional method
        <- InnerAspect: sau proceed() / finally [transaction vẫn đang hoạt động]
      -> commit hoặc rollback
    <- transaction advisor
  <- OuterAspect: sau proceed() / finally [transaction đã kết thúc]
Client nhận kết quả/exception
```

Từ mô hình này có thể rút ra ba kết luận cho **một transaction mới được tạo tại method đó**:

1. **Aspect ngoài transaction** (`order=50`) có thể đo toàn bộ thời gian: begin, target, commit/rollback. Trong `before` và `finally` của nó, `TransactionSynchronizationManager.isActualTransactionActive()` thường là `false` nếu không có transaction bên ngoài.
2. **Aspect trong transaction** (`order=200`) chạy với transaction đang hoạt động. Nhưng phần `finally` của nó chạy **trước** khi transaction advisor thực sự commit/rollback. Vì vậy một `@AfterReturning` ở bên trong transaction cũng chưa chứng minh dữ liệu đã commit.
3. `@Order` điều khiển **vị trí advice** trong chain; nó **không** thay đổi `@Transactional.propagation`, isolation hoặc rollback rules. Nếu method đã được gọi bên trong transaction đang có (ví dụ `REQUIRED` tham gia transaction cấp trên), log `isActualTransactionActive()` ở aspect ngoài vẫn có thể là `true`.

**Giá trị mặc định:** `@EnableTransactionManagement.order` mặc định là `Ordered.LOWEST_PRECEDENCE` (= `2147483647`). Nếu custom aspect có `@Order(1)`, aspect đó thường nằm ngoài transaction advice mặc định. Nếu muốn một custom aspect chạy **bên trong** giao dịch một cách tường minh, nên đặt order cho transaction advisor, ví dụ `100`, rồi cho aspect bên trong order `200` như trên.

Nếu có nhiều advisor **cùng order** hoặc không được chỉ định order rõ ràng, đừng dựa vào thứ tự quan sát ở một lần chạy để kết luận rằng nó là hợp đồng ổn định. Hãy khai báo precedence có chủ đích khi ranh giới transaction ảnh hưởng correctness.

## 4. “Trước”, “sau” và rollback: đừng nhầm các thời điểm

Giả sử method `save()` chạy thành công và có một transaction mới:

| Vị trí custom aspect | Trước `proceed()` | Sau `proceed()` thành công | Trong `finally` |
| --- | --- | --- | --- |
| Ngoài transaction advisor | Trước begin | Sau commit | Sau transaction advisor hoàn tất commit/rollback |
| Trong transaction advisor | Sau begin | Trước commit | Trước commit/rollback của transaction advisor |

Các điều kiện thường gây lỗi nghiệp vụ:

- Log `"saved successfully"` trong advice **bên trong** transaction ngay sau `proceed()` có thể xuất hiện **trước khi commit**. Commit vẫn có thể thất bại; nếu log mang nghĩa dữ liệu đã bền vững thì không chính xác.
- Dùng một audit table chung transaction bên trong: nếu transaction rollback, bản ghi audit ấy có thể rollback cùng, tùy cách tạo transaction và resource manager.
- Advice **bên ngoài** thấy exception ném ra từ target **sau khi transaction interceptor đã xử lý rollback**; nếu outer advice bắt exception rồi trả một giá trị bình thường, rollback đã xảy ra trước đó.
- Advice **bên trong** bắt `RuntimeException` từ target và **không ném tiếp**, transaction advisor có thể thấy invocation thành công và commit, trừ khi transaction bị đánh dấu rollback-only hoặc có tác động khác.
- Nếu custom advice bên trong tự **ném exception** sau khi target vừa thành công, transaction interceptor sẽ nhìn thấy exception ấy và áp dụng rollback rules. Trong khi đó, exception do outer advice ném **sau khi transaction interceptor commit xong** không thể quay ngược transaction vừa commit.

Đừng sử dụng việc gọi `@AfterReturning` hay `@AfterThrowing` như một cam kết tự động "sau commit"/"sau rollback". Hai annotation này mô tả **luồng trả về/exception của join point theo vị trí advisor**, không tự biến thành transaction lifecycle callback.

Khi cần đúng **sau khi commit thực sự thành công**, sử dụng cơ chế của Spring transaction, như `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` hoặc `TransactionSynchronization.afterCommit()`. Cần cân nhắc transaction mới nếu muốn thực hiện một database write bền vững **sau commit**, chẳng hạn service riêng với `@Transactional(propagation = Propagation.REQUIRES_NEW)` được gọi qua proxy. Không coi callback sau commit là một giao dịch mới tự động.

## 5. Ví dụ Java 17 chạy được: aspect ngoài và trong transaction

Ví dụ dùng Spring Framework 6.2, Java 17, Spring AOP annotation, `DataSourceTransactionManager` và H2 in-memory. Chúng ta cố tình đặt:

- `OuterTraceAspect` có `@Order(50)`, **ngoài** transaction.
- Transaction advisor có `@EnableTransactionManagement(order = 100)`.
- `InnerTraceAspect` có `@Order(200)`, **trong** transaction.

Khi chạy thành công, `save(1)` commit một hàng. Khi `saveAndFail(2)` ném `IllegalStateException`, mặc định `@Transactional` rollback vì đây là unchecked exception. Hai aspect cho biết thời điểm transaction active.

### 5.1. Cấu trúc project và Maven

```text
spring-aspect-tx-demo/
  pom.xml
  src/main/java/demo/
    App.java
    OrderService.java
    OrderServiceImpl.java
    OuterTraceAspect.java
    InnerTraceAspect.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aspect-tx-demo</artifactId>
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
      <groupId>org.springframework</groupId>
      <artifactId>spring-jdbc</artifactId>
      <version>6.2.14</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-tx</artifactId>
      <version>6.2.14</version>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
      <version>1.9.24</version>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.3.232</version>
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

### 5.2. Service interface và implementation

`src/main/java/demo/OrderService.java`:

```java
package demo;

public interface OrderService {
    void save(int id);
    void saveAndFail(int id);
}
```

`src/main/java/demo/OrderServiceImpl.java`:

```java
package demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class OrderServiceImpl implements OrderService {
    private final JdbcTemplate jdbc;

    public OrderServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(int id) {
        jdbc.update("INSERT INTO orders_demo(id) VALUES (?)", id);
        System.out.println("target tx="
                + TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Override
    @Transactional
    public void saveAndFail(int id) {
        jdbc.update("INSERT INTO orders_demo(id) VALUES (?)", id);
        System.out.println("target tx="
                + TransactionSynchronizationManager.isActualTransactionActive());
        throw new IllegalStateException("boom");
    }
}
```

Ở đây annotation nằm trên **method của class implementation**, đúng cách Spring khuyến nghị trong chế độ proxy. Cả hai method đều public và được client gọi qua interface `OrderService`, giúp tránh phụ thuộc vào việc cast proxy sang class cụ thể.

### 5.3. Hai aspect với order rõ ràng

`src/main/java/demo/OuterTraceAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Order(50)
public class OuterTraceAspect {
    @Around("execution(* demo.OrderService.save*(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("outer before tx="
                + TransactionSynchronizationManager.isActualTransactionActive());
        try {
            return pjp.proceed();
        } finally {
            System.out.println("outer finally tx="
                    + TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
```

`src/main/java/demo/InnerTraceAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Order(200)
public class InnerTraceAspect {
    @Around("execution(* demo.OrderService.save*(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("inner before tx="
                + TransactionSynchronizationManager.isActualTransactionActive());
        try {
            return pjp.proceed();
        } finally {
            System.out.println("inner finally tx="
                    + TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
```

Biểu thức `execution(* demo.OrderService.save*(..))` chọn `save` và `saveAndFail` trên hợp đồng interface `OrderService`. Hai lời gọi `pjp.proceed()` cho Spring tiếp tục chuỗi advisor. `finally` chỉ in trạng thái transaction, **không nuốt exception**.

### 5.4. Cấu hình proxy, transaction manager và chạy chương trình

`src/main/java/demo/App.java`:

```java
package demo;

import javax.sql.DataSource;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

public class App {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableTransactionManagement(order = 100)
    public static class Config {
        @Bean
        DataSource dataSource() {
            var ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:aspect_demo;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        OrderService orderService(JdbcTemplate jdbc) {
            return new OrderServiceImpl(jdbc);
        }

        @Bean
        OuterTraceAspect outerTraceAspect() {
            return new OuterTraceAspect();
        }

        @Bean
        InnerTraceAspect innerTraceAspect() {
            return new InnerTraceAspect();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("CREATE TABLE orders_demo (id INT PRIMARY KEY)");

            OrderService service = context.getBean(OrderService.class);
            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            System.out.println("--- success");
            service.save(1);
            System.out.println("rows=" + jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders_demo", Integer.class));

            System.out.println("--- failure");
            try {
                service.saveAndFail(2);
            } catch (IllegalStateException e) {
                System.out.println("caught=" + e.getMessage());
            }

            System.out.println("rows=" + jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders_demo", Integer.class));
        }
    }
}
```

Import `org.springframework.core.annotation.Order` trong `App.java` là dư về mặt kỹ thuật (không ảnh hưởng biên dịch); `@Order` được đặt trên hai class aspect ở file riêng. Có thể bỏ import đó nếu muốn giữ danh sách import tối giản.

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Những dòng do ứng dụng in theo thứ tự (bỏ qua thông điệp phụ của Maven hoặc logging backend):

```text
proxy=true
--- success
outer before tx=false
inner before tx=true
target tx=true
inner finally tx=true
outer finally tx=false
rows=1
--- failure
outer before tx=false
inner before tx=true
target tx=true
inner finally tx=true
outer finally tx=false
caught=boom
rows=1
```

Đọc kỹ theo thứ tự:

- `proxy=true` chứng minh Spring trả AOP proxy cho service.
- `outer before tx=false` vì advisor order 50 đi vào chain **trước** transaction interceptor order 100; chưa có transaction đang hoạt động ở luồng `main`.
- `inner before tx=true` và `target tx=true` vì advisor order 200 nằm **bên trong** ranh giới transaction.
- `inner finally tx=true` xuất hiện khi transaction interceptor vẫn chưa rời `proceed()` để commit hoặc rollback.
- `outer finally tx=false` xuất hiện sau khi transaction interceptor đã hoàn thành và dọn transaction context.
- `rows=1` sau `save(1)` chứng tỏ insert đã được commit. Sau `saveAndFail(2)` ném unchecked exception, row ID 2 rollback và tổng số bản ghi **vẫn bằng 1**.
- `caught=boom` xuất hiện **sau** `outer finally` vì client chỉ bắt lỗi khi lời gọi qua proxy đã trả control/throw trở lại.

Trong ví dụ, `JdbcTemplate` tham gia giao dịch qua transaction-aware resource binding của Spring và `DataSourceTransactionManager`. Một transaction manager chỉ xử lý các resource mà nó thực sự quản lý; không suy ra các thao tác mạng/file hoặc những `DataSource` khác tự động rollback.

## 6. Cách thử đổi thứ tự để quan sát hành vi

Sau khi hiểu ví dụ gốc, có thể thử các thay đổi **từng cái một**:

### Trường hợp A: dùng transaction advisor mặc định

Đổi trong `App.Config`:

```java
@EnableTransactionManagement
```

Spring dùng order mặc định `Ordered.LOWEST_PRECEDENCE`. Hai aspect có `50` và `200` đều có precedence cao hơn transaction advisor, nên trong mô hình không có transaction phía ngoài, các dòng `inner before tx` / `inner finally tx` có thể chuyển thành `false`. Ý nghĩa: **cả hai aspect nằm ngoài transaction**. Đừng kỳ vọng `@Order(200)` tự làm advice ở trong transaction khi transaction advisor đang ở order lớn nhất.

### Trường hợp B: cho một aspect chạy trong transaction

Khôi phục `@EnableTransactionManagement(order = 100)` và `InnerTraceAspect @Order(200)`. Khi đó inner advice thấy `true`, như output ví dụ chính. Cần kiểm tra cả begin/commit/rollback bằng log nếu đang dùng nhiều interceptors, nhiều transaction hoặc propagation đặc biệt.

### Trường hợp C: đổi @Order(200) thành @Order(30)

Cả hai custom aspect đều có order nhỏ hơn transaction advisor; đều chạy ngoài transaction. Inner aspect không còn "inside" dù class vẫn tên `InnerTraceAspect`. Tên class không có tác dụng điều khiển Spring AOP order.

### Trường hợp D: không đặt @Order

Khi các advisor trùng precedence hoặc Spring quyết định thứ tự, kết quả có thể phụ thuộc cấu hình và framework internals. Đối với logging thuần túy điều này có thể không nghiêm trọng, nhưng đối với validation/audit/transaction semantics, phải cấu hình precedence rõ.

Đây là các thay đổi để hiểu **vị trí advisor**, không phải phương pháp tự ý nâng/giảm isolation, thay đổi propagation hay bật transaction cho lời gọi self-invocation.

## 7. Các tình huống lỗi khó hơn với exception

### 7.1. Aspect nuốt exception nằm trong transaction

Giả sử advice `@Order(200)` catch `IllegalStateException` ném từ target và trả kết quả bình thường:

```java
@Around("execution(* demo.OrderService.saveAndFail(..))")
public Object swallowInside(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        System.out.println("swallowed inside");
        return null;
    }
}
```

Đây là **snippet giả định để phân tích**, không thêm vào ví dụ chạy ở phần 5 (vì nó sẽ làm thay đổi kết quả rollback). Nếu method chưa đánh dấu rollback-only và không có cơ chế khác đánh dấu rollback, transaction interceptor ở ngoài nhận một lần thực thi **không ném exception** và có thể commit insert trước đó. Việc nuốt exception do đó làm thay đổi giao thức lỗi mà transaction advisor quan sát.

### 7.2. Aspect nuốt exception nằm ngoài transaction

Nếu thay vào đó advice `@Order(50)` bắt exception **sau `pjp.proceed()` ném lỗi**, transaction interceptor ở bên trong đã nhận exception và xử lý rollback trước. Việc outer advice trả kết quả bình thường về client lúc này không biến rollback thành commit. Vì thế việc catch ở hai vị trí advisor khác nhau không tương đương.

### 7.3. Advice ném exception sau target

Nếu inner advice gọi `pjp.proceed()` thành công rồi ném unchecked exception **trước khi rời TransactionInterceptor**, transaction advisor có thể rollback. Nếu outer advice ném exception **sau khi transaction đã commit**, exception đó không thể tự đảo ngược commit đã hoàn thành.

Trong thực tế, tuyệt đối không nuốt lỗi trong audit/logging chỉ để che một exception mà tầng transaction còn cần quan sát. Nếu cần chuyển đổi exception, hãy biết conversion xảy ra **ở trong hay ngoài** advisor transaction và cấu hình `rollbackFor`/`noRollbackFor` phù hợp.

### 7.4. Checked exception và rollback rule

Với `@Transactional` mặc định của Spring 6.2, rollback xảy ra với `RuntimeException` và `Error`; **checked exception** thường không tự gây rollback nếu không có rule tùy chỉnh. Có thể khai báo:

```java
@Transactional(rollbackFor = IOException.class)
public void saveAndSend() throws IOException {
    // code nghiệp vụ minh họa
}
```

Đây cũng là **snippet lý thuyết**, không phải method trong project ở phần 5. Cần import `java.io.IOException` và `org.springframework.transaction.annotation.Transactional`. Phạm vi và ngoại lệ cụ thể phải phù hợp logic nghiệp vụ.

## 8. Quan hệ với propagation và transaction có sẵn

Sơ đồ và output ở phần 5 giả định **caller chưa có transaction**, method được `REQUIRED` mở một transaction mới. Với application thực tế:

- `REQUIRED` khi đã có transaction: interceptor tham gia transaction hiện có; outer advice có thể thấy `isActualTransactionActive() = true` ngay từ lúc bắt đầu.
- `REQUIRES_NEW`: interceptor có thể suspend transaction cũ, tạo transaction mới rồi resume transaction cũ sau khi hoàn thành. Aspect ngoài transaction advisor vẫn có thể thấy transaction của caller trước/sau; nên chỉ nhìn boolean active không cho biết đang ở transaction nào.
- `NOT_SUPPORTED`: interceptor có thể tạm suspend transaction đang có, dẫn đến advice bên trong thấy không có actual transaction.
- `SUPPORTS` nếu không có transaction có sẵn: có thể không tạo actual transaction mới.
- `@Transactional(readOnly = true)` chủ yếu cung cấp read-only hint cho transaction manager/resource; không thay thế mọi cơ chế bảo vệ ghi dữ liệu.
- Với `@Async` hoặc chạy việc trên thread khác, `TransactionSynchronizationManager` dùng context ràng với thread trong mô hình imperative; custom advice không được mặc định coi transaction sẽ tự truyền sang thread khác.

Vì vậy `@Order` xác định vị trí interceptor, còn **`Propagation` xác định hành vi transaction tại thời điểm đó**. Không đồng nhất hai cơ chế này.

## 9. Một proxy hay hai proxy? Cách kiểm tra

Với `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` trong **cùng một ApplicationContext**, hạ tầng Spring có thể áp dụng custom aspect và `TransactionInterceptor` qua **một proxy chứa nhiều advisors**. Hai annotation không đồng nghĩa có hai proxy độc lập. Nested proxy vẫn có thể xuất hiện nếu ứng dụng tự tạo `ProxyFactory`, dùng scoped proxies hoặc lắp ghép nhiều context.

Để kiểm tra bean thật:

```java
Object bean = context.getBean("orderService");
System.out.println(AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
```

Đây là snippet trong scope có `ApplicationContext context`; cần import `AopUtils`, `Advised`, `Advisor`. Danh sách ở **một lớp proxy** chưa phản ánh nested proxy bên trong, và việc advisor tồn tại chưa chứng minh nó match một invocation cụ thể. Hãy kết hợp danh sách advisor với event trace lúc gọi service qua proxy. Bài **“Thiết kế thử nghiệm để xác định chuỗi advisor thực tế”** hướng dẫn phép đo đầy đủ.

Khi self-invocation `this.save()` hoặc tạo target bằng `new`, custom aspect và transaction advice dành cho lời gọi đó đều có thể bị bypass. Gọi qua Spring-injected bean proxy, dùng service interface khi runtime chọn JDK proxy. Với transaction không hoạt động dù có proxy, kiểm tra thêm transaction manager, propagation và resource thực sự tham gia.

## 10. Quyết định vị trí custom aspect theo mục đích

| Mục đích | Vị trí thường phù hợp | Lưu ý về tính đúng |
| --- | --- | --- |
| Đo thời gian toàn bộ method gồm begin và commit/rollback | Ngoài transaction advisor | `@Around` ngoài cùng đo luôn chi phí transaction |
| Validation không cần database transaction | Thường ngoài, trước transaction | Tránh mở transaction vô ích nếu reject sớm |
| Trace/debug muốn đọc dữ liệu đang trong cùng transaction | Trong transaction advisor | Có thể chưa commit; quan sát side effect trong transaction |
| Kiểm tra trạng thái transaction trước target | Trong transaction advisor | Kiểm tra propagation thực tế, không chỉ order |
| Ghi audit chỉ khi transaction đã commit thành công | Callback `AFTER_COMMIT` hoặc event transaction | `@AfterReturning` bên trong chưa chắc sau commit |
| Ghi nhật ký lỗi bất kể rollback | Thiết kế ngoài transaction hoặc tài nguyên audit độc lập | Tránh audit cùng transaction rồi bị rollback |
| Thay đổi/translate exception | Cân nhắc rất kỹ trong/ngoài transaction | Nơi exception bị nuốt/ném quyết định rollback advisor quan sát gì |

Không có một thứ tự đúng cho mọi custom aspect. Chọn thứ tự dựa trên mục tiêu: ví dụ **profiler cần tổng thời gian transaction** nên nằm ngoài, còn **logic cần thấy transaction active** nên nằm trong. Với kiểm soát giao dịch nghiệp vụ, ưu tiên để service và `@Transactional` diễn tả boundary rõ ràng, dùng advice cho hành vi cross-cutting thực sự.

## 11. Checklist QA khi kết hợp Spring @Aspect với giao dịch

1. Spring container đã nhận diện aspect, `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` (hoặc Boot auto-configuration tương đương) trong context đúng.
2. Bean được lấy qua DI/context là proxy; kiểm tra `AopUtils.isAopProxy(bean)` và danh sách advisors khi cần.
3. Aspect pointcut match đúng service method; nếu không match sẽ không ảnh hưởng transaction order.
4. Có transaction manager và resource Spring JDBC/JPA đang tham gia đúng manager.
5. Thứ tự Spring transaction advisor và aspect được chỉ định bằng `order`/`@Order` có giá trị **khác nhau**, không phụ thuộc mặc định.
6. Dùng `TransactionSynchronizationManager.isActualTransactionActive()` để quan sát context ở đúng thread; đo thêm log transaction manager khi propagation phức tạp.
7. Test cả đường **commit thành công**, **rollback khi RuntimeException**, và trường hợp advice bắt/biến đổi exception.
8. Nếu có self-invocation, `@Async` hoặc nested proxy, xác minh lời gọi thực sự đi qua chain mong muốn.
9. Không đánh đồng "advice chạy sau method" với "transaction đã commit"; dùng Spring transaction synchronization khi thật sự cần AFTER_COMMIT.
10. Khi audit/metrics có side effect, xác định rõ nó phải đi cùng transaction hay độc lập với rollback của nghiệp vụ.

## 12. Câu hỏi tự kiểm tra

**Hỏi:** `@Order(10)` hay `@Order(200)` nằm ngoài nếu transaction advisor order=100?  
**Đáp:** Aspect order=10 nằm ngoài; aspect order=200 nằm trong.

**Hỏi:** `@AfterReturning` của aspect bên trong transaction có chắc chạy sau commit?  
**Đáp:** Không. Nó thường chạy trước khi transaction advisor hoàn tất commit.

**Hỏi:** Một custom aspect và `@Transactional` có bắt buộc tạo hai proxy khác nhau?  
**Đáp:** Không. Spring thường có thể gắn các advisor vào một proxy; nested proxy chỉ xuất hiện trong những cấu hình liên quan.

**Hỏi:** Khi aspect bên trong nuốt RuntimeException do target ném, transaction interceptor chắc chắn rollback?  
**Đáp:** Không. Nếu không có rollback-only hoặc rule khác can thiệp, interceptor nhận normal return và có thể commit.

**Hỏi:** Vì sao `@Transactional` trên `this.innerMethod()` không mở transaction riêng?  
**Đáp:** Trong proxy mode, self-invocation không đi qua transaction advisor.

**Hỏi:** Nếu cần thông báo cho hệ thống khác chỉ sau commit, nên dùng gì?  
**Đáp:** Cân nhắc `@TransactionalEventListener(AFTER_COMMIT)`, transaction synchronization hoặc outbox pattern theo yêu cầu độ tin cậy; không chỉ đặt `@AfterReturning` và giả định commit đã thành công.

## Tài liệu Spring chính thức

- Spring Framework — Understanding the Spring Framework's Declarative Transaction Implementation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Spring Framework — Using @Transactional: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Framework — Advising Transactional Operations: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/applying-more-than-just-tx-advice.html
- Spring Framework — Advice Ordering: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#advice-ordering
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Transaction-bound Events: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-002">Thứ tự aspect đo đạc, retry và interceptor giao dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự aspect đo đạc, retry và interceptor giao dịch trong Spring AOP

## 1. Mục tiêu

Trong Spring AOP, một lời gọi service có thể đồng thời chịu tác động bởi ba loại advisor/interceptor:

- **Measurement aspect**: Spring **@Aspect** với **@Around** để ghi duration và kết quả thực thi.
- **Retry advice**: Spring Retry kết hợp **@EnableRetry** và **@Retryable**, cho phép thực hiện lại lời gọi khi gặp lỗi được lựa chọn.
- **TransactionInterceptor**: Spring Transaction Management với **@EnableTransactionManagement** và **@Transactional**, mở/tham gia transaction và quyết định commit/rollback.

Điều quan trọng không nằm ở thứ tự các annotation được viết trên method, mà ở **thứ tự Spring AOP advisor thực thi**. Nó quyết định metric đo một logical operation hay từng attempt, retry nằm trong hay ngoài transaction, và measurement có tính thời gian commit/rollback và backoff không.

Bài học chỉ bàn về cách các cơ chế này được tích hợp trong **Spring AOP**, không trình bày lại lý thuyết AOP chung hoặc toàn bộ hoạt động của database transaction.

Sau bài này bạn sẽ:
1. Đọc được interceptor chain và quy tắc **@Order** của Spring.
2. Phân biệt các bố cục **Measure → Retry → Transaction**, **Retry → Measure → Transaction** và **Transaction → Retry**.
3. Cấu hình order tường minh bằng **@Order**, **@EnableRetry(order=...)** và **@EnableTransactionManagement(order=...)**.
4. Kiểm chứng hành vi Spring AOP bằng ví dụ Java 17+ và log transaction begin, rollback, commit.

## 2. Thứ tự interceptor: ngoài vào trong, trong ra ngoài

Trong Spring AOP, **giá trị order nhỏ hơn có precedence cao hơn**. Với advice bao quanh, precedence cao hơn nghĩa là advice đó vào trước và ra sau:

~~~text
Client
  |
Spring AOP proxy
  |
  +-- Measurement (order=50)       begin
        |
        +-- Retry (order=100)      attempts
              |
              +-- Transaction (order=200)
                    |
                    +-- Target service method
                    |
              <---- transaction commit/rollback
        <---- retry hoặc trả kết quả
  <---- Measurement                  end
  |
Client
~~~

Đây là thứ tự **lồng nhau**, không phải danh sách tác vụ chạy tuần tự rồi kết thúc theo cùng một chiều. Với **@Around**, method **proceed()** điều khiển chuyển tiếp phần chain bên trong. Aspect đo đạc bình thường chỉ gọi **proceed()** một lần; chính retry advice có trách nhiệm gọi lại chain bên trong khi gặp ngoại lệ phù hợp.

Ví dụ đo đạc nằm ngoài retry: nếu target thất bại lần 1 và thành công lần 2, **measurement** chỉ đi qua một lần. Ngược lại, measurement bên trong retry được gọi mỗi attempt, có thể ghi một attempt lỗi và một attempt thành công.

**Điều kiện:** lời gọi phải đi qua Spring AOP proxy. Khi một method tự gọi **this.otherMethod()** bên trong target hoặc code dùng **new** tạo object ngoài Spring, các annotation gắn với method đích chưa chắc được interceptor kích hoạt. Không lấy ví dụ interceptor chain làm bằng chứng rằng mọi lời gọi Java trong ứng dụng đều được intercept.

## 3. Spring cấu hình precedence ở ba vị trí

| Thành phần | Điểm cấu hình | Ý nghĩa |
| --- | --- | --- |
| Measurement Spring **@Aspect** | **@Order(50)** trên class aspect hoặc **Ordered** | Sắp thứ tự advisor của aspect tương ứng |
| Spring Retry 2.0.x | **@EnableRetry(order=100)** | Order của retry advice do Spring Retry đăng ký |
| Spring transaction | **@EnableTransactionManagement(order=200)** | Order của transaction advisor sử dụng **TransactionInterceptor** |

**Giá trị mặc định đáng biết:** Spring Retry 2.0.11 đặt **@EnableRetry.order** mặc định là **Ordered.LOWEST_PRECEDENCE - 1** (**2147483646**) để nằm trước transaction advice mặc định. Spring Framework 6.2 đặt **@EnableTransactionManagement.order** mặc định là **Ordered.LOWEST_PRECEDENCE** (**2147483647**).

Tuy nhiên, **order của measurement aspect** là quyết định riêng của ứng dụng. Trong bài này chọn các số nhỏ hơn và tách biệt **50, 100, 200** để dễ đọc, dễ kiểm chứng. Không nên phụ thuộc vào thứ tự khai báo annotation, tên bean hay thứ tự @Bean trong source.

Một đoạn cấu hình:

~~~java
@Configuration
@EnableAspectJAutoProxy
@EnableRetry(order = 100)
@EnableTransactionManagement(order = 200)
class AopConfig {
    // Các Spring beans
}
~~~

Đây là mã minh họa rút gọn, ví dụ có đủ imports/dependencies ở phần tiếp theo.

## 4. Các thứ tự có ý nghĩa khác nhau

Giả sử service thất bại lần đầu, retry một lần nữa rồi thành công.

| Chain ngoài → trong | Số lần measurement | Phạm vi đo | Ranh giới transaction |
| --- | --- | --- | --- |
| **Measurement → Retry → Transaction → Target** | Một lần | Toàn bộ logical operation: attempts, backoff, begin/rollback/commit nằm trong chain | Transaction advice được gọi mỗi attempt |
| **Retry → Measurement → Transaction → Target** | Mỗi attempt một lần | Duration từng attempt, bao gồm chi phí transaction bên trong | Transaction advice được gọi mỗi attempt |
| **Retry → Transaction → Measurement → Target** | Mỗi attempt một lần | Method body và advice bên trong, không tính đầy đủ begin/commit/rollback của advisor ngoài | Measurement nằm bên trong transaction advice |
| **Transaction → Retry → Target** | Tùy vị trí measurement | Có thể retry nhiều lần trong một transaction bao ngoài | Rủi ro chia sẻ transaction đã lỗi |

**Retry ngoài Transaction** thường phù hợp khi cần thử lại transaction thất bại: retry bắt exception sau khi transaction advice đã rollback, rồi lần sau vào transaction advice lần nữa.

Tuy nhiên, **transaction advice chạy nhiều lần không tự chứng minh mỗi attempt có một physical transaction mới**. Với propagation mặc định **REQUIRED**, nếu caller đã mở transaction, interceptor bên trong có thể **join transaction đó**. Muốn phân tích đúng phải xét ranh giới giao dịch của toàn bộ lời gọi, propagation và transaction manager thực tế.

**Transaction ngoài Retry** có thể nguy hiểm với lỗi database: transaction có thể đã bị đánh dấu **rollback-only** sau attempt đầu, khiến attempt sau có vẻ thành công nhưng commit cuối cùng vẫn lỗi. Việc retry nhiều lần trong cùng transaction không đảm bảo một khởi đầu sạch.

## 5. Ví dụ Spring AOP hoàn chỉnh có thể chạy với Java 17+

Dùng Java 17+, Spring Framework **6.2.14**, Spring Retry **2.0.11**, Maven. Để không cần database, ví dụ có một **transaction manager minh họa** ghi callback begin/commit/rollback. Manager này **không phải transaction manager cho môi trường production** và không hề cam kết ACID hoặc lưu dữ liệu.

Cấu trúc thư mục:

~~~text
aop-order-demo/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── JobService.java
    ├── JobServiceImpl.java
    ├── MeasurementAspect.java
    └── RecordingTransactionManager.java
~~~

**pom.xml**:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>aop-order-demo</artifactId>
    <version>1.0-SNAPSHOT</version>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
            <version>6.2.14</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
            <version>2.0.11</version>
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
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/JobService.java**:

~~~java
package demo;

public interface JobService {
    String perform();
}
~~~

**src/main/java/demo/JobServiceImpl.java**:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

public class JobServiceImpl implements JobService {
    private final AtomicInteger attempt = new AtomicInteger();

    @Override
    @Retryable(
        retryFor = IllegalStateException.class,
        maxAttempts = 2,
        backoff = @Backoff(delay = 1)
    )
    @Transactional
    public String perform() {
        int current = attempt.incrementAndGet();
        System.out.println("target-attempt-" + current);

        if (current == 1) {
            throw new IllegalStateException("temporary-failure");
        }
        return "ok";
    }
}
~~~

**maxAttempts=2** tính cả lần đầu. **AtomicInteger** chỉ để đếm số attempt của demo, không được transaction manager giả lập rollback. Ví dụ thực tế cần cân nhắc tính idempotent trước khi retry side effects.

**src/main/java/demo/MeasurementAspect.java**:

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(50)
public class MeasurementAspect {
    @Around("execution(* demo.JobService.perform(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("measure-begin");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("measure-end");
        }
    }
}
~~~

Measurement bao ngoài retry; dùng **finally** để chắc chắn in marker kết thúc dù có exception. Nếu muốn đo độ trễ thật, thay marker bằng **System.nanoTime()** quanh **proceed()** và ghi metric. Nếu muốn biết success/failure, phải ghi thêm outcome mà không nuốt exception.

**src/main/java/demo/RecordingTransactionManager.java**:

~~~java
package demo;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

public class RecordingTransactionManager
        extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction,
                           TransactionDefinition definition) {
        System.out.println("tx-begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        System.out.println("tx-commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        System.out.println("tx-rollback");
    }
}
~~~

Manager này chỉ ghi callback khi **TransactionInterceptor** chạy. Nó không quản lý JDBC connection, lock, rollback-only hoặc tài nguyên cơ sở dữ liệu.

**src/main/java/demo/App.java**:

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableRetry(order = 100)
    @EnableTransactionManagement(order = 200)
    public static class Config {
        @Bean
        JobService jobService() {
            return new JobServiceImpl();
        }

        @Bean
        MeasurementAspect measurementAspect() {
            return new MeasurementAspect();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            JobService job = context.getBean(JobService.class);
            String result = job.perform();
            System.out.println("result=" + result);
        }
    }
}
~~~

Chạy trong thư mục chứa **pom.xml**:

~~~bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=demo.App
~~~

Theo chain và dữ liệu demo, **stdout do các class demo in**, không tính logging bổ sung từ Maven/Spring, là:

~~~text
measure-begin
tx-begin
target-attempt-1
tx-rollback
tx-begin
target-attempt-2
tx-commit
measure-end
result=ok
~~~

Giải thích: measurement(order 50) vào một lần. Retry(order 100) gọi transaction(order 200), target lần 1 ném **IllegalStateException**, transaction advice rollback rồi chuyển exception về retry. Retry chạy lần 2 qua transaction advice, target trả **ok**, transaction commit, measurement kết thúc một lần, caller nhận **ok**.

Điểm mấu chốt là **retry bao ngoài transaction advice** khiến mỗi attempt đi vào transaction interceptor riêng biệt. Với transaction manager thật, phải kiểm tra thêm transaction đang có ở caller và propagation để biết có thật sự tạo transaction vật lý mới hay không.

## 6. Đổi sang metric cho từng attempt

Giữ mọi file, chỉ sửa annotation trong **MeasurementAspect** thành:

~~~java
@Order(150)
~~~

Chain mới: **Retry(100) → Measurement(150) → Transaction(200) → Target**.

Với cùng dữ liệu, các dòng do demo in:

~~~text
measure-begin
tx-begin
target-attempt-1
tx-rollback
measure-end
measure-begin
tx-begin
target-attempt-2
tx-commit
measure-end
result=ok
~~~

Measurement chạy **hai lần**, vì retry gọi phần chain bên trong hai lần. Với metric outcome, attempt 1 phải được ghi failed, attempt 2 ghi succeeded. Nếu chỉ đặt **@Order(50)**, metric logical operation sau cùng ghi success và không trực tiếp biểu lộ lỗi attempt 1.

Nếu đổi thành **@Order(250)** thì measurement ở **trong TransactionInterceptor**: **Retry(100) → Transaction(200) → Measurement(250) → Target**. Timer đo mỗi attempt nhưng không gồm đầy đủ thời gian mở transaction và commit/rollback, bởi bước kết thúc measurement chạy trước transaction advice hoàn tất.

Trong ứng dụng cần quan sát đầy đủ, nên tách **logical-operation metric** (ngoài retry) và **attempt metric** (giữa retry và transaction), dùng tên/nhãn thể hiện rõ đơn vị đo. Không tính hai kiểu count chung rồi gọi đó là số request.

## 7. Đảo transaction ra ngoài retry có hậu quả gì?

Giả sử sửa **@EnableTransactionManagement(order=75)** trong khi measurement vẫn **@Order(50)**, retry vẫn **100**:

~~~text
Measurement (50)
  |
  Transaction (75)
    |
    Retry (100)
      |
      Target
~~~

Một lời gọi qua Spring AOP proxy sẽ đi vào transaction advisor **trước** retry. Nếu attempt đầu ném exception nhưng retry tự xử lý và attempt sau thành công, transaction ngoài có thể nhìn thấy kết quả cuối cùng là thành công. Với manager giả lập, bạn có thể chỉ thấy **một begin và một commit**.

Với database/ORM thật, kết quả có thể khác nghiêm trọng: attempt đầu có thể khiến transaction bị đánh dấu rollback-only hoặc tài nguyên rơi vào trạng thái không thể tiếp tục. Attempt tiếp theo chạy Java thành công không bảo đảm commit được. Kết quả có thể là **UnexpectedRollbackException** hoặc lỗi resource. Đây là lý do cần cân nhắc **Retry ngoài TransactionInterceptor** khi mục đích là thử lại một transaction hỏng.

Ngược lại, không có nguyên tắc tuyệt đối rằng mọi transaction phải nằm trong retry. Nếu chủ đích là dùng chung một transaction cho toàn bộ các attempts, chính sách đó cần được thiết kế với loại lỗi, database, transaction manager và side effects cụ thể.

## 8. Kiểm tra advisor chain trong Spring runtime

Khi cùng bean có đo đạc, retry và transaction, **đọc chain thực tế** thay vì chỉ nhìn vị trí ba annotation trong source:

~~~java
Object bean = context.getBean("jobService");
System.out.println(AopUtils.isAopProxy(bean));
if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Đây là fragment cần `ApplicationContext context` và imports `AopUtils`, `Advised`, `Advisor`; danh sách phản ánh proxy đang inspect. Với nested proxies phải xem thêm lớp bên trong. Thứ tự advisor ứng viên vẫn phải được đối chiếu **trace method thực chạy**, số lần target/measurement, cùng commit/rollback của từng attempt. Bài cuối chương **“Thiết kế thử nghiệm để xác định chuỗi advisor thực tế”** cung cấp fixture và tiêu chí kiểm chứng có đối chứng.

## 9. Các lỗi phổ biến

Những lỗi có ảnh hưởng trực tiếp đến **đơn vị đo** và **tính đúng giao dịch**:

| Biểu hiện | Nguyên nhân / cách kiểm chứng |
| --- | --- |
| Measurement một lần dù target chạy nhiều lần | Timer ở **ngoài retry**; đây là logical-operation metric, không phải attempt metric |
| Measurement nhiều lần nhưng bỏ qua commit | Timer ở **trong transaction interceptor**; nếu cần đo commit, đặt ở ngoài transaction |
| Retry chạy nhưng transaction không sạch sau attempt lỗi | Transaction nằm ngoài retry, hoặc `REQUIRED` tham gia outer transaction; test rollback-only/resource |
| Retry lặp side effect | DB rollback không đảo HTTP/email/message; cần idempotency key, outbox hoặc compensation |
| Thứ tự thay đổi khi sửa annotation | Precedence do advisor `order` quyết định, không phải vị trí annotation |
| Không có retry/tx/measurement | Target không qua proxy, method/pointcut không match hoặc hạ tầng chưa bật |
| Lần cuối thất bại không có failure metric | Chưa test `maxAttempts` exhausted và policy exception cuối |

Đừng gộp attempt count, business-request count và transaction count vào cùng một metric. Bài **“Rủi ro retry sai phạm vi giao dịch hoặc lặp tác dụng phụ”** phân tích idempotency, các side effects và outer transaction sâu hơn.

## 10. Checklist kiểm thử thích hợp

- Tạo Spring **ApplicationContext** thật trong integration test; lấy service **qua context**, xác nhận **AopUtils.isAopProxy**.
- Xác nhận đủ ba advisor thật sự áp dụng cho cùng method, đặt order khác nhau một cách tường minh.
- Giả lập thất bại ở attempt 1, thành công attempt 2, xác nhận target chạy hai lần.
- Với order **50 → 100 → 200**, xác nhận measurement một lần; với **100 → 150 → 200**, xác nhận measurement hai lần.
- Với transaction manager thật, kiểm tra callback begin/commit/rollback và resource transaction của từng attempt, kể cả transaction bao ngoài và **rollback-only**.
- Kiểm tra path cuối cùng thất bại, exception type, backoff, transaction propagation và các side effects.
- Khi ứng dụng có asynchronous/reactive execution hoặc nhiều tầng proxy, kiểm chứng theo cơ chế của stack thực tế; không dùng sơ đồ đồng bộ này để suy đoán toàn bộ hành vi.

## 11. Tài liệu Spring và điều cần nhớ

~~~text
Một measurement cho toàn bộ lời gọi:
  Measurement
    Retry
      Transaction
        Target

Một measurement cho mỗi attempt:
  Retry
    Measurement
      Transaction
        Target
~~~

Tài liệu chính thức:
- Spring Framework — Advice Ordering: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework 6.2 — EnableTransactionManagement: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/transaction/annotation/EnableTransactionManagement.html
- Spring Framework — Declarative Transaction Annotations: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Retry 2.0.11 — EnableRetry: https://docs.spring.io/spring-retry/docs/2.0.11/apidocs/org/springframework/retry/annotation/EnableRetry.html
- Spring Retry 2.0.11 — RetryOperationsInterceptor: https://docs.spring.io/spring-retry/docs/2.0.11/apidocs/org/springframework/retry/interceptor/RetryOperationsInterceptor.html

**Ghi nhớ:** Thứ tự trong Spring AOP quyết định ranh giới của measurement, retry và transaction. **Số order càng nhỏ càng ở ngoài**; measurement ngoài retry đo một logical operation, measurement giữa retry và transaction đo từng attempt. Để thử lại transaction đã hỏng, thường cần retry bao ngoài transaction interceptor cùng ranh giới propagation đúng và kiểm chứng bằng transaction manager thực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-003">Rủi ro retry sai phạm vi giao dịch hoặc lặp tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro retry sai phạm vi giao dịch hoặc lặp tác dụng phụ trong Spring AOP

## 1. Vấn đề cần giải quyết

Trong một ứng dụng Spring, `@Retryable` và `@Transactional` thường được đặt trên cùng một method để xử lý lỗi tạm thời như deadlock, timeout hoặc kết nối chập chờn. Hai annotation này không chỉ là metadata: khi các tính năng tương ứng được kích hoạt, chúng tạo **những Spring AOP advisors/interceptors** bao quanh lời gọi method qua proxy. **Thứ tự advice quyết định phạm vi transaction của mỗi lần retry.**

Nếu transaction bao cả vòng lặp retry, một lỗi khiến transaction bị đánh dấu rollback-only có thể làm những lần thử sau chạy trong transaction đã không còn khả năng commit. Ngược lại, nếu retry bao ngoài transaction, mỗi lần thử có thể đi vào một transaction mới, rollback attempt thất bại trước khi thử lại — **với điều kiện không có một transaction bên ngoài mà các attempt đang tham gia**.

Rủi ro thứ hai độc lập nhưng thường xuất hiện cùng lúc: **rollback database không rollback được side effects ngoài database**. Một method có thể gửi email, publish message, gọi cổng thanh toán hoặc cập nhật hệ thống khác ở attempt thứ nhất, sau đó gặp lỗi transaction. Retry có thể thực hiện lại hành động bên ngoài ở attempt thứ hai. Kết quả: một giao dịch database commit một lần nhưng phía ngoài ghi nhận nhiều tác dụng phụ.

Bài tập trung vào cách **Spring AOP proxy phối hợp retry advice và transaction advice**, cùng các thiết kế an toàn cho business side effects. Không đi sâu vào AspectJ weaving hay kiến thức AOP chung ngoài những gì cần để giải thích Spring.

### Mục tiêu

Sau bài học, bạn có thể:

1. Vẽ đúng thứ tự interceptor chain của Spring Retry và Spring transaction.
2. Phân biệt `Retry → Transaction → Method` với `Transaction → Retry → Method`.
3. Xác định khi nào `REQUIRED` vẫn tham gia outer transaction dù retry advice ở ngoài.
4. Hiểu chính xác phạm vi rollback và tại sao non-transactional side effects có thể lặp.
5. Cấu hình `@EnableRetry`, `@EnableTransactionManagement`, `@Retryable`, `@Transactional` có chủ đích.
6. Kiểm chứng bằng Spring integration test/SQL, không chỉ nhìn annotation hoặc số lần gọi.
7. Chọn idempotency, transaction outbox, deduplication và compensation phù hợp với hệ thống.

## 2. Spring thực thi retry và transaction dưới dạng advice như thế nào?

Khi ứng dụng bật:

~~~java
@EnableRetry
@EnableTransactionManagement
~~~

Spring đăng ký hạ tầng advisors để xử lý các method phù hợp. Với `spring-retry` 2.x, `@EnableRetry` làm các Spring bean có method `@Retryable` được xử lý theo retry metadata. `@EnableTransactionManagement` bật transaction advice của Spring Framework khi bean/method có metadata `@Transactional`.

Cả hai hoạt động theo mô hình **proxy-based method interception**. Một caller phải gọi bean proxy thích hợp; không phải mọi lời gọi Java đến một target object đều đi qua advisors.

Một `@Retryable` có thể gọi `MethodInvocation.proceed()` nhiều lần theo policy. Một `@Transactional` cần thiết lập, hoàn tất hoặc rollback transaction quanh từng invocation mà transaction interceptor thực sự nhận. **Advice nào là lớp ngoài cùng sẽ quyết định việc retry lặp qua transaction interceptor hay lặp chỉ bên trong một transaction interceptor.**

### Thứ tự lý tưởng: retry ngoài, transaction trong

~~~text
Client
  |
  v
[Spring AOP proxy]
  |
  v
Retry advice (outer)
  |
  +-- attempt 1
  |      Transaction advice -> begin Tx-1
  |          Business method
  |          -> throws retryable exception
  |      Transaction advice -> rollback Tx-1
  |
  +-- attempt 2
  |      Transaction advice -> begin Tx-2
  |          Business method
  |          -> throws retryable exception
  |      Transaction advice -> rollback Tx-2
  |
  +-- attempt 3
         Transaction advice -> begin Tx-3
             Business method
             -> returns success
         Transaction advice -> commit Tx-3
  |
  v
Caller nhận kết quả thành công
~~~

Sơ đồ giả định **không có outer transaction đang hoạt động**, transaction manager xử lý rollback theo policy, và mỗi attempt đều thực sự đi qua transaction advisor. Trong giả định này, `Propagation.REQUIRED` tạo transaction mới cho từng attempt vì chưa có transaction hiện hữu.

### Thứ tự nguy hiểm: transaction ngoài, retry trong

~~~text
Client
  |
  v
[Spring AOP proxy]
  |
  v
Transaction advice -> begin Tx-ONE
  |
  v
Retry advice (inner)
  |
  +-- attempt 1 -> Business method -> exception
  +-- attempt 2 -> Business method -> exception
  +-- attempt 3 -> Business method -> success?
  |
  v
Transaction advice -> commit/rollback Tx-ONE
  |
  v
Caller
~~~

Ở cách này, các attempts cùng nằm trong một transaction boundary. Nếu resource hoặc logic đã đánh dấu transaction rollback-only, retry lần hai/ba **không** tạo một transaction mới chỉ bằng cách lặp business method. Commit cuối cùng có thể thất bại, ví dụ `UnexpectedRollbackException`, hoặc transaction không còn ở trạng thái hợp lệ.

Không khẳng định rằng mọi transaction tự đánh dấu rollback-only ngay khi một exception bị catch ở bên trong: điều này phụ thuộc việc exception có đi qua transaction interceptor, transaction manager, JDBC/JPA provider, database và cách application đánh dấu rollback. Nguy cơ cốt lõi là **không cô lập mỗi attempt thành một transaction hoàn chỉnh**.

## 3. Thứ tự advisors của Spring Retry và Spring transaction

Với **Spring Retry 2.0.13**, `@EnableRetry.order` có default `Ordered.LOWEST_PRECEDENCE - 1` để retry advice thường chạy trước transaction advice có order mặc định `Ordered.LOWEST_PRECEDENCE`. Theo thứ tự `Ordered` của Spring, **giá trị order nhỏ hơn có độ ưu tiên cao hơn** và trở thành outer advice khi bắt đầu invocation.

Dù default hiện tại thường đúng cho hai advisors này, không nên dựa vào giả định ấy khi ứng dụng cấu hình các advisors khác, đổi order transaction hoặc xây chain programmatically.

Cấu hình rõ ràng:

~~~java
@Configuration
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 10)
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE)
public class AppConfig {
    // Spring bean definitions ...
}
~~~

Cần imports `org.springframework.context.annotation.Configuration`, `org.springframework.retry.annotation.EnableRetry`, `org.springframework.transaction.annotation.EnableTransactionManagement`, `org.springframework.core.Ordered`. Đoạn này là một **configuration snippet**, không phải chương trình độc lập.

Với cấu hình trên:

- Retry advice có order thấp hơn, được áp dụng bên ngoài.
- Transaction advice có order cao hơn, được áp dụng bên trong.
- Mỗi `proceed()` từ retry có thể đi qua transaction advice một lần.
- `@Transactional` phải thật sự match method và proxy phải do Spring quản lý.
- Một outer transaction ở caller hoặc một outer advice khác vẫn có thể làm thay đổi phạm vi thực tế; thứ tự này **không có nghĩa mọi attempt chắc chắn có một physical transaction độc lập**.

**Kiểm tra version khi dùng tài liệu**: defaults và annotations cần đối chiếu đúng phiên bản Spring Retry/Spring Framework ứng dụng đang chạy. Đừng lấy precedence từ một snippet trực tuyến rồi xem nó là invariant bất biến cho mọi release.

### Ordering của advice phải được kiểm tra bằng hành vi

Khi có nhiều `@Around`, tracing, security, transaction và retry advisors, thứ tự có thể phức tạp. Có thể inspect proxy thông qua:

~~~java
Object bean = context.getBean("paymentService");
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Snippet giả định có Spring `ApplicationContext context` và bean `paymentService`. `Advised` không nhất thiết được expose bởi mọi proxy; hơn nữa danh sách advisors không thay thế cho một integration test xác minh transaction boundary trên **từng attempt**.

## 4. Outer transaction: bẫy còn lại dù order đã đúng

Xét:

~~~java
@Service
public class CheckoutWorkflow {
    private final PaymentService paymentService;

    public CheckoutWorkflow(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Transactional
    public void checkout(String orderId) {
        paymentService.charge(orderId); // @Retryable + @Transactional(REQUIRED)
    }
}
~~~

Đây là snippet minh họa cần có interface/bean `PaymentService` và infrastructure transaction/retry. Mặc dù `paymentService` được gọi **qua proxy của nó** và retry advice ngoài transaction advice trên `charge`, transaction của `checkout` đã tồn tại.

Nếu `charge` dùng `Propagation.REQUIRED`, transaction advisor trong mỗi attempt **tham gia vào transaction của checkout** thay vì tạo transaction vật lý mới. Một attempt làm transaction bị đánh dấu rollback-only có thể ảnh hưởng mọi attempt sau và cả checkout. Đây chính là trường hợp không thể kết luận “order đúng tức mỗi attempt độc lập”.

### Các chiến lược ranh giới transaction

| Thiết kế | Ranh giới có thể có | Khi thích hợp/cảnh báo |
| --- | --- | --- |
| Retry outer + `@Transactional(REQUIRED)`, **không có outer transaction** | Mỗi attempt bắt đầu transaction mới | Phù hợp nhiều thao tác database ngắn cần rollback giữa retries |
| Retry outer + `@Transactional(REQUIRED)`, **có outer transaction** | Attempts tham gia transaction của caller | Nguy cơ rollback-only và không cô lập; cần xem lại boundary |
| Retry outer + `@Transactional(REQUIRES_NEW)` | Mỗi attempt đi qua interceptor sẽ suspend outer transaction (nếu có) và bắt đầu transaction mới | Có isolation riêng; cần transaction manager hỗ trợ, connection pool/locking hợp lý |
| Retry trong transaction outer | Nhiều attempts trong cùng transaction | Dễ gặp resource state hỏng/rollback-only, nhất là database/JPA failures |
| Retry service gọi sang transactional worker bean khác qua proxy | Mỗi attempt có cơ hội đi qua worker transaction interceptor | Thiết kế ranh giới rõ, dễ kiểm thử, đặc biệt khi tránh self-invocation |

`REQUIRES_NEW` không phải công cụ thay thế bừa bãi: một attempt thành công có thể commit độc lập dù outer transaction sau đó rollback. Đó có thể là bug tính nhất quán mới. Phải xác định trước **unit of work** và điều gì được phép commit độc lập.

`NESTED` (nếu transaction manager hỗ trợ savepoints) không đồng nhất với `REQUIRES_NEW`, và không rollback các tác dụng phụ bên ngoài database. `SUPPORTS` hoặc `NOT_SUPPORTED` lại có semantics khác. Đừng đổi propagation chỉ để xóa một exception mà không xác định transaction semantics.

## 5. Self-invocation: annotation có nhưng advice không được gọi

Spring AOP áp dụng qua proxy. Đoạn code sau có thể khiến cả retry lẫn transaction interceptor của `charge` bị bypass:

~~~java
@Service
public class PaymentServiceImpl {
    public void checkout(String id) {
        charge(id);           // tương đương this.charge(id)
    }

    @Retryable(retryFor = TransientPaymentException.class)
    @Transactional
    public void charge(String id) {
        // ...
    }
}
~~~

Đây là snippet minh họa; cần class `TransientPaymentException`, imports annotations và Spring configuration hợp lệ. Khi `checkout` gọi `charge` **trên cùng target**, lời gọi nội bộ không qua proxy method `charge`. Việc có annotation trên method là chưa đủ.

Cũng không nên lấy raw target từ `Advised.getTargetSource().getTarget()` rồi gọi nó để né một lỗi cast JDK proxy. Đó là đường gọi trực tiếp target, bỏ qua advisors và có thể phá lifecycle scoped/pooled target.

Cách ưu tiên:

~~~text
CheckoutWorkflow bean
        |
        +-- injected PaymentService proxy (@Retryable)
                |
                +-- injected DatabasePaymentWorker proxy (@Transactional)
                        |
                        +-- JDBC / JPA unit of work
~~~

Mô hình tách hai bean giúp minh bạch rằng mỗi attempt retry gọi một **Spring-managed transactional boundary**. Nếu caller bên ngoài đã có transaction, vẫn cần xác minh propagation của worker.

Không đổi `proxyTargetClass=true` chỉ để “sửa” self-invocation; JDK/CGLIB Spring proxies đều có giới hạn này.

## 6. Retry điều gì, và vì sao không retry mọi exception?

`@Retryable` cho phép chỉ rõ `retryFor`, `noRetryFor`, `maxAttempts` và `backoff`. Chỉ retry những lỗi tạm thời mà thao tác thực sự an toàn để thử lại.

~~~java
@Retryable(
    retryFor = TransientPaymentException.class,
    noRetryFor = InvalidPaymentRequestException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 200, multiplier = 2.0)
)
public String charge(String id) {
    // ...
}
~~~

Đây là đoạn minh họa: cả hai exception classes cần được định nghĩa, và `TransientPaymentException` nên đại diện một lỗi được xác định **retryable**. `maxAttempts = 3` tính **cả lần đầu**, không phải 1 + 3 retries. Backoff giảm áp lực lên dependency đang lỗi; production thường cần jitter, giới hạn retry, deadline và phân biệt lỗi có thể khắc phục.

Không retry:

- Validation failure, request sai dữ liệu hoặc vi phạm rule nghiệp vụ vĩnh viễn.
- Lỗi authorization hoặc permission không thay đổi trong cùng request.
- Lỗi không xác định đã commit hay chưa mà thao tác **không có idempotency**.
- Exception cho thấy một giao dịch đã ở trạng thái không thể tiếp tục mà code lại định retry **trong chính transaction đó**.
- Mọi `Exception.class` theo thói quen; policy quá rộng có thể biến bug lập trình thành nhiều tác dụng phụ.

Phân biệt **retry policy** và **transaction rollback policy**: một exception có thể retryable nhưng mặc định không kích hoạt rollback theo rule transaction, hoặc ngược lại. Spring transaction mặc định rollback trên `RuntimeException` và `Error`; checked exceptions thông thường cần `rollbackFor` nếu muốn rollback. Nếu method bắt exception rồi trả về thành công, transaction interceptor có thể không biết phải rollback trừ khi business code chủ động đánh dấu rollback-only hoặc lỗi đã được hạ tầng xử lý khác.

## 7. Lặp tác dụng phụ: rollback database không rollback một HTTP call

Một luồng xử lý rủi ro:

~~~text
Attempt 1:
  INSERT payment row vào database (trong Tx-1)
  HTTP POST /charge sang payment provider -> provider đã thu tiền
  Database gặp lỗi -> Tx-1 rollback
  [Provider không thuộc Tx-1]

Attempt 2:
  INSERT payment row lại (trong Tx-2)
  HTTP POST /charge sang provider -> thu tiền lần hai
  Database commit -> Tx-2 commit

Kết quả:
  Database: 1 row
  Provider: có thể đã thu tiền 2 lần
~~~

**Thông tin từ transaction manager Spring chỉ bảo đảm resource được enlist trong transaction ấy**. Nó không thể tự undo một cuộc gọi HTTP, gửi email, push notification, in hóa đơn hoặc thao tác tại external service vốn không cùng transaction. Thậm chí nếu dùng `@Transactional` bao đúng phương thức, side effects thực thi trước khi commit database vẫn có thể rò ra ngoài.

Các tác dụng phụ thường gặp:

| Loại tác dụng phụ | Có được rollback bởi local JDBC transaction không? | Biện pháp |
| --- | --- | --- |
| DML tới cùng datasource trong transaction | Có, theo database/transaction semantics | Đảm bảo cùng transaction manager/connection |
| HTTP charge tại payment provider | Không | Idempotency key tại provider, reconciliation, compensation |
| Gửi email/SMS trực tiếp | Không | Outbox/dedupe message ID, không gửi trong mỗi attempt không kiểm soát |
| Publish message ngoài transaction database | Không mặc định | Transactional outbox và publisher có retry/dedupe |
| Gọi một service qua network | Không | Contract idempotent, correlation ID, bounded retry |
| Ghi file/object storage | Không qua JDBC transaction | Idempotent object key, staging/finalization, cleanup |
| Tăng `AtomicInteger` hoặc mutable field trong JVM | Không | Chỉ coi là volatile state; không dùng làm chứng cứ commit |

Ngay cả việc side effect xảy ra **sau** một database write không có nghĩa nó xảy ra sau **database commit**. Với một transaction bao method, commit thường diễn ra khi method trả qua transaction interceptor, tức ngoài method body. Vì vậy code:

~~~java
@Transactional
public void doPayment() {
    repository.save(payment);
    externalApi.charge(payment); // Thường vẫn trước commit DB.
}
~~~

là rủi ro nếu người viết nghĩ lệnh đầu đã commit. Đây là snippet minh họa trong Spring bean, không phải chương trình chạy độc lập.

## 8. Ví dụ Java 17 chạy được: mỗi retry có DB transaction riêng nhưng side effect vẫn lặp

Ví dụ sau dùng **Spring Framework 6.1.15**, **Spring Retry 2.0.13**, **H2 in-memory database** và Java 17. Chúng ta đặt retry advice ngoài transaction advice, mỗi attempt insert một dòng vào H2 rồi gọi một gateway mô phỏng side effect bên ngoài. Hai attempts đầu ném exception, dẫn đến rollback; attempt thứ ba thành công và commit. Gateway chỉ ghi nhận số lần gọi trong một `AtomicInteger`, không tham gia transaction H2.

Đây là ví dụ tối giản nhưng có **transaction database thật**, không giả lập rollback bằng cách xóa phần tử thủ công.

### 8.1. Cấu trúc

~~~text
spring-retry-transaction-demo/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── PaymentOperations.java
    ├── PaymentService.java
    ├── ExternalGateway.java
    └── TransientPaymentException.java
~~~

### 8.2. Maven

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-retry-transaction-demo</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
            <version>2.0.13</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.2.224</version>
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

`spring-jdbc` mang transaction abstraction và `JdbcTemplate`, `spring-retry` mang retry advisor, `spring-aop` mang proxy infrastructure, `aspectjweaver` phục vụ cấu hình proxy/AspectJ pointcut facilities, H2 là database thật trong bộ nhớ để rollback có thể quan sát được.

### 8.3. Service contract

**src/main/java/demo/PaymentOperations.java**

~~~java
package demo;

public interface PaymentOperations {
    String charge(String id);
}
~~~

### 8.4. Exception chỉ lỗi tạm thời

**src/main/java/demo/TransientPaymentException.java**

~~~java
package demo;

public class TransientPaymentException extends RuntimeException {
    public TransientPaymentException(String message) {
        super(message);
    }
}
~~~

Kế thừa `RuntimeException` khiến Spring transaction rollback theo default rule khi exception thoát khỏi transactional method. Trong hệ thống thực tế cần phân loại cụ thể theo exception của driver/remote provider thay vì tự giả định mọi lỗi đều retryable.

### 8.5. Gateway mô phỏng side effect **ngoài** database transaction

**src/main/java/demo/ExternalGateway.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class ExternalGateway {
    private final AtomicInteger calls = new AtomicInteger();

    public void send(String paymentId) {
        int current = calls.incrementAndGet();
        System.out.println("external-send " + paymentId + " count=" + current);
    }

    public int getCalls() {
        return calls.get();
    }
}
~~~

Đây chỉ là mô phỏng tác dụng phụ không transactional qua `AtomicInteger`; không gọi API tài chính thật. Counter này **không** được rollback khi H2 transaction thất bại.

### 8.6. Service có retry và transaction

**src/main/java/demo/PaymentService.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService implements PaymentOperations {

    private final JdbcTemplate jdbc;
    private final ExternalGateway gateway;
    private final AtomicInteger attempts = new AtomicInteger();

    public PaymentService(JdbcTemplate jdbc, ExternalGateway gateway) {
        this.jdbc = jdbc;
        this.gateway = gateway;
    }

    @Override
    @Retryable(
        retryFor = TransientPaymentException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1)
    )
    @Transactional
    public String charge(String id) {
        int attempt = attempts.incrementAndGet();
        System.out.println("attempt=" + attempt);

        // DML trong transaction của attempt hiện tại.
        jdbc.update(
            "INSERT INTO payment_log(payment_id, attempt_no) VALUES (?, ?)",
            id, attempt
        );

        // Side effect này KHÔNG thuộc transaction H2.
        gateway.send(id);

        if (attempt < 3) {
            throw new TransientPaymentException("temporary #" + attempt);
        }

        return "PAID " + id;
    }
}
~~~

`attempts` là counter minh họa; với ứng dụng nhiều requests, nó không đại diện số attempt cho từng operation và cần thay bằng tracking theo request/RetryContext thích hợp. Trong demo chỉ có **một** lời gọi `charge` duy nhất, nên chuỗi `1, 2, 3` đáng tin.

Quan trọng: ném `RuntimeException` **sau** câu lệnh INSERT và gateway call khiến Spring rollback DML của attempt thất bại, nhưng không undo gateway counter.

### 8.7. Spring configuration và main

**src/main/java/demo/App.java**

~~~java
package demo;

import javax.sql.DataSource;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ComponentScan("demo")
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 10)
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE)
public class App {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:retrydemo;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public ExternalGateway externalGateway() {
        return new ExternalGateway();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute(
                "CREATE TABLE payment_log ("
                    + "payment_id VARCHAR(64), attempt_no INT)"
            );

            PaymentOperations service =
                    context.getBean(PaymentOperations.class);
            ExternalGateway gateway =
                    context.getBean(ExternalGateway.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("result=" + service.charge("O-42"));

            Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_log", Integer.class
            );
            System.out.println("db-rows=" + rows);
            System.out.println("external-calls=" + gateway.getCalls());
        }
    }
}
~~~

Maven chạy ở thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng `System.out` của ứng dụng trong demo này theo thứ tự:

~~~text
proxy=true
attempt=1
external-send O-42 count=1
attempt=2
external-send O-42 count=2
attempt=3
external-send O-42 count=3
result=PAID O-42
db-rows=1
external-calls=3
~~~

Có thể có log Maven/Spring khác tùy môi trường; chúng không phải một phần stdout ứng dụng được liệt kê.

**Đọc kết quả:**

1. `proxy=true`: Spring tạo AOP proxy cho bean áp dụng retry/transaction.
2. Attempts 1 và 2 thực hiện `INSERT` trong hai transactions riêng, gọi gateway, rồi ném retryable runtime exception. Transaction advice rollback các INSERT.
3. Attempt 3 thực hiện `INSERT`, gọi gateway và trả về `PAID O-42`. Transaction advice commit.
4. `db-rows=1`: chỉ hàng của attempt thành công còn tồn tại trong H2 sau các rollbacks.
5. `external-calls=3`: tất cả các lần gọi gateway đều có hiệu ứng bên ngoài JDBC transaction, dù chỉ một DB transaction commit.
6. Cấu hình order cố ý cho retry ngoài transaction. Nếu thay đổi order hoặc có outer transaction, kết luận về ba transaction độc lập **không còn được đảm bảo**; cần test lại.

**Ví dụ không mô phỏng tiền thật.** `ExternalGateway` là counter; trong hệ thống thực tế HTTP/payment provider có retry semantics, idempotency, timeout và failure mode phức tạp hơn.

## 9. Ba cách sửa sai phạm vi retry/transaction

### 9.1. Đặt retry ngoài transaction bằng proxy boundary rõ ràng

Một pattern dễ review:

~~~java
@Service
public class RetryFacade {
    private final TransactionalWorker worker;

    public RetryFacade(TransactionalWorker worker) {
        this.worker = worker;
    }

    @Retryable(
        retryFor = TransientPaymentException.class,
        maxAttempts = 3
    )
    public void execute(String id) {
        worker.doDatabaseWork(id);
    }
}

@Service
public class TransactionalWorker {
    @Transactional
    public void doDatabaseWork(String id) {
        // Database unit of work.
    }
}
~~~

Đây là **mẫu hai class minh họa**, cần imports và Spring configuration tương ứng. `RetryFacade` nhận `TransactionalWorker` do container inject. Caller gọi facade qua Spring proxy; mỗi attempt retry đi qua proxy của worker, nơi transaction interceptor có thể bắt đầu/rollback/commit.

Đừng gọi `this.doDatabaseWork` trong cùng bean và mong Spring intercept lại. Nếu caller của facade có outer transaction, propagation của worker vẫn phải được xét riêng.

### 9.2. Không kéo dài transaction qua backoff hoặc network call

Nếu retry chứa backoff đáng kể, việc transaction advice nằm **ngoài** retry có thể giữ database connection/lock qua lúc sleep giữa attempts. Đặt transaction boundary **ngắn gọn quanh phần DML**; một operation gọi remote API có thể cần tách thành bước khác, không giữ database transaction mở chỉ để chờ network response.

Nhưng không phải cứ di chuyển remote call ra khỏi transaction là đã bảo đảm consistency. Nếu sự kiện remote xảy ra sau DB commit mà request thất bại trước khi ghi nhận kết quả, vẫn cần idempotency và reconciliation.

### 9.3. Test với transaction manager thật

Thay vì assert `@Retryable` và `@Transactional` cùng tồn tại, hãy gây ra **hai lỗi tạm thời rồi một thành công**, đo rằng:

- Số attempts đúng bằng cấu hình retry.
- Hai attempts đầu rollback DML.
- Attempt cuối commit.
- Transaction manager có đúng boundary mong muốn khi caller không có transaction và khi caller có outer transaction.
- Side effects ngoài DB không tự rollback.
- `@Recover`/recovery path nếu có không gây một tác dụng phụ mới không kiểm soát.

Với JPA/Hibernate, lỗi có thể làm persistence context hoặc transaction hỏng; việc retry trong cùng transaction càng nguy hiểm. Với một lỗi SQL deadlock, một physical transaction mới sau rollback thường có cơ sở tốt hơn so với tiếp tục trong connection/transaction bị lỗi.

## 10. Idempotency key: lớp bảo vệ bên ngoài transaction

**Idempotency** nghĩa là nhiều attempts cùng một business operation không tạo nhiều kết quả nghiệp vụ ngoài ý muốn. Ví dụ một payment provider chấp nhận request idempotency key:

~~~text
POST /charge
Idempotency-Key: payment:O-42:v1

{ orderId: "O-42", amount: 100000 }
~~~

Đây là **mô hình giao thức**; header/payload cụ thể phải theo contract của provider. Caller cần sử dụng **cùng key cho mọi retries của cùng một logical payment**, không sinh UUID mới ở mỗi attempt. Phía provider phải có contract idempotent thực sự; chỉ gửi header không đảm bảo duplicate được chống.

Một chiến lược phối hợp:

1. Ứng dụng lưu `payment_operation_id` ổn định cho một yêu cầu nghiệp vụ.
2. Mỗi retry gửi cùng operation ID/idempotency key.
3. Receiver ghi nhận key + kết quả trong storage đáng tin; request trùng key trả lại kết quả tương ứng hoặc trạng thái hiện có.
4. Nếu request timeout, phía client truy vấn trạng thái/correlate response bằng ID trước khi quyết định tạo operation mới.
5. Hệ thống có reconciliation để xử lý “remote đã làm, local chưa ghi nhận”.

**Unique constraint ở database nội bộ không đủ** để ngăn external side effect nếu external call xảy ra trước khi database transaction commit và commit sau đó bị rollback. Một key chỉ có giá trị khi hệ thống thực thi side effect **tôn trọng và deduplicate** nó.

Ví dụ schema hỗ trợ idempotency tại bên thực thi:

~~~sql
CREATE TABLE processed_commands (
    operation_id VARCHAR(100) PRIMARY KEY,
    result_code VARCHAR(30) NOT NULL
);
~~~

Đây là SQL minh họa một phần cơ chế receiver; phải thiết kế transaction atomic với thao tác thực sự và trả response phù hợp khi gặp duplicate. Câu lệnh CREATE TABLE tự nó không cung cấp idempotency.

## 11. Transactional outbox và after-commit

### 11.1. Vì sao publish message trực tiếp trong transaction là nguy hiểm?

~~~java
@Transactional
public void createOrder(Order order) {
    repository.save(order);
    messageBroker.send(new OrderCreated(order.getId()));
}
~~~

Đây là snippet minh họa. Nếu DB rollback sau khi broker đã nhận message, consumer có thể xử lý `OrderCreated` cho đơn hàng không tồn tại. Nếu retry chạy lại, broker có thể nhận nhiều events cho cùng operation.

### 11.2. Transactional outbox

Thay vì gửi message ngay trong method transactional:

~~~text
Trong MỘT local DB transaction:
    INSERT/UPDATE business row
    INSERT outbox event row (event_id duy nhất)
    COMMIT

Sau khi commit:
    Outbox publisher đọc event đã commit
    -> publish message
    -> đánh dấu delivered, có retry khi broker lỗi

Consumer:
    Dedupe theo event_id hoặc business operation id
~~~

Outbox giải quyết “DB commit nhưng event mất” bằng cách lưu ý định publish trong cùng transaction. Nhưng publisher có thể crash **sau khi broker nhận message, trước khi đánh dấu delivered**; event có thể được publish lại. Vì vậy consumer vẫn nên idempotent; outbox thường mang semantics **at-least-once**, không mặc nhiên exactly-once end to end.

Nếu chỉ dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`, event listener chạy sau DB commit đối với Spring transaction thích hợp, tránh gửi message trước commit. Tuy nhiên nếu process chết sau commit trước lúc listener hoàn tất, event có thể mất. **AFTER_COMMIT không thay thế durable outbox** khi cần bảo đảm không mất event.

### 11.3. Remote payment cần chính sách khác gửi message

Với thanh toán, thường cần ghi **payment intent** và stable idempotency key, sau đó thực hiện lệnh ở worker/adapter thích hợp và cập nhật trạng thái. Chọn bước nào transactional, bước nào retryable và bước nào cần compensation phải theo hợp đồng cổng thanh toán. Không gói nhiều HTTP attempts vào một transaction database dài hạn và hy vọng rollback local sẽ thu hồi tiền.

## 12. Recovery và khi retry hết số lần

Sau `maxAttempts`, Spring Retry có thể ném exception ra caller hoặc chuyển đến một method `@Recover` phù hợp nếu ứng dụng khai báo và Spring Retry xử lý được.

Ví dụ minh họa:

~~~java
@Retryable(
    retryFor = TransientPaymentException.class,
    maxAttempts = 3
)
public String charge(String id) {
    // May throw TransientPaymentException.
}

@Recover
public String recover(TransientPaymentException cause, String id) {
    // Return a deliberately defined result or raise an appropriate failure.
    return "PENDING_RECONCILIATION " + id;
}
~~~

Đây là **snippet** phải được đặt trong Spring bean có retry advisor, có imports `@Retryable`/`@Recover` và class exception. `@Recover` không tự động “undo mọi side effect”; nếu không biết remote charge đã xảy ra chưa, trả `SUCCESS` hoặc thử charge thêm một lần có thể sai. Trạng thái `PENDING_RECONCILIATION` chỉ mang ý nghĩa nếu hệ thống thực sự có một workflow reconciliation.

Nếu retry advice nằm ngoài transaction advice và recovery được gọi từ retry interceptor sau những attempts đã kết thúc, recovery **không tự nhiên nằm trong transaction của attempt trước**. Nếu recovery phải ghi database, hãy chỉ định một transactional boundary riêng qua Spring proxy hoặc transaction API phù hợp. Không giả định `@Transactional` trên method nội bộ sẽ được áp dụng qua self-invocation.

Một chính sách an toàn phải quyết định:

- Trả lỗi cho caller hay trả trạng thái pending?
- Có phát sự kiện cần operator xử lý không?
- Làm sao phân biệt “remote chưa làm gì” với “remote đã làm nhưng response mất”?
- Những counters/metrics nào phản ánh attempts, successes, commits, side effects?
- Khi client retry request HTTP ở tầng trên, logical operation key có được tái sử dụng?

## 13. Các lỗi khác làm retry không an toàn

### 13.1. Checked exception nhưng rollback rule không phù hợp

Spring transaction mặc định rollback khi `RuntimeException`/`Error` thoát khỏi transactional method. Một checked exception có thể khiến transaction được commit theo default behavior dù retry policy lại cho phép thử lại.

~~~java
@Transactional(rollbackFor = ExternalTimeoutException.class)
@Retryable(
    retryFor = ExternalTimeoutException.class,
    maxAttempts = 3
)
public void execute() throws ExternalTimeoutException {
    // ...
}
~~~

Đây là snippet cần một class `ExternalTimeoutException extends Exception` và thích hợp với nghiệp vụ. `rollbackFor` bảo đảm transaction interceptor quyết định rollback khi checked exception thoát ra; nhưng **nếu không có transaction boundary riêng giữa attempts**, chỉ thêm annotation này vẫn chưa đảm bảo isolation.

### 13.2. Swallow exception trong transactional method

~~~java
@Transactional
public void saveAndSend() {
    try {
        repository.save(entity);
        externalApi.send();
    } catch (TemporaryException ex) {
        // swallow: transaction advice may observe normal return
    }
}
~~~

Đoạn này là ví dụ phản diện. Nếu method nuốt lỗi rồi trả về bình thường, retry advice ở ngoài không thấy exception để retry. Transaction advice có thể coi lời gọi là thành công trừ khi hạ tầng đã đánh dấu rollback-only. Chỉ catch khi có kế hoạch retry/rollback/recovery rõ ràng; không swallow lỗi để làm log “xanh”.

### 13.3. Retry policy được đặt quá rộng

Nếu retry hết ba lần không thành công, tổng số attempts là 3. Nếu tầng HTTP caller cũng tự retry request ba lần **với operation ID mới mỗi lần**, tổng số invocations logic có thể lên tới 9 hoặc hơn, trước khi xét worker/broker retries. Đây là một dạng **retry amplification**.

Dùng retry budget/deadline, backoff với jitter, circuit breaker thích hợp và correlation ID thống nhất. Cần có owner rõ ràng cho retry ở từng ranh giới, tránh mọi tầng tự nhân số attempts.

### 13.4. Side effect sau khi transaction rollback

Một `@AfterThrowing` advice hoặc `@Around` advice bắt exception rồi tự gửi notification có thể bị thực thi mỗi attempt. Ngay cả khi annotation side effect không nằm trong method nghiệp vụ, nó vẫn nằm trong Spring AOP chain, nên thứ tự advisor cũng ảnh hưởng số lần nó chạy.

Nếu muốn **một notification sau toàn bộ retry exhausted**, đặt notification ở ranh giới bên ngoài retry hoặc recovery/workflow phù hợp; nếu muốn **một metric cho mỗi attempt**, có thể đặt advice bên trong retry. Đây là hai ý đồ khác nhau.

### 13.5. Async, reactive hoặc đa luồng

Transaction context dựa trên ThreadLocal đối với imperative `PlatformTransactionManager`; đổi thread có thể thay đổi phạm vi transaction. Với reactive transaction manager, context propagation khác. Không giả định một retry annotation quanh method trả `Mono`/`Flux` sẽ tự kiểm soát toàn bộ subscription lifecycle như retry operator của Reactor; cần kiểm tra chính xác kiểu return và thời điểm method body chạy.

Phạm vi bài này là Spring AOP imperative method calls như ví dụ JDBC. Với reactive publisher, cân nhắc `retryWhen`, reactive transaction operator và idempotency riêng, không sao chép máy móc assumptions từ demo imperative.

## 14. Ma trận kiểm thử những rủi ro chính

| Tình huống | Số transaction/attempt dự kiến | Rủi ro | Assert chính |
| --- | --- | --- | --- |
| Retry ngoài, transaction trong, không có outer Tx | Thường một Tx mới mỗi attempt | Side effects ngoài Tx vẫn lặp | Failed rows rollback, final DB row đúng, gateway attempts đúng |
| Transaction ngoài, retry trong | Cùng transaction | Rollback-only/không có fresh Tx | Kiểm tra transaction state, commit result |
| Retry ngoài nhưng `REQUIRED` tham gia outer Tx | Có thể chung Tx của caller | Attempts không độc lập | Test gọi qua outer transactional workflow |
| Retry ngoài + `REQUIRES_NEW` | Mỗi attempt có thể là Tx riêng | Commit độc lập với caller | Test outer rollback sau inner commit theo business contract |
| Exception checked không có `rollbackFor` | Tx có thể commit dù retry | DML lặp/unique violations | Retry + DB rows sau từng failure |
| Self-invocation gọi annotated method | Advice có thể bị bypass hoàn toàn | Không retry/transaction như kỳ vọng | Compare internal call vs injected proxy call |
| Remote operation idempotent với stable key | DB và remote có lifecycle riêng | Response loss vẫn cần reconcile | Duplicate request không nhân side effects |
| Outbox + idempotent consumer | DB event atomic, publish có thể lặp | At-least-once delivery | Publisher crash/restart và dedupe event ID |
| `@Recover` sau retry exhausted | Recovery ngoài/khác Tx tùy chain | Recovery thêm side effect không kiểm soát | No duplicate, pending state và policy rõ |

Một integration test đáng tin phải có lỗi injected **có thể dự đoán**, database thật hoặc transaction manager đúng, cùng assert trạng thái DB và external mock. Không đánh giá transaction chỉ bằng số dòng `System.out`, vì log cũng không transactional.

### Kịch bản test tối thiểu cho ví dụ H2

1. Gọi `service.charge("O-42")` đúng một lần qua Spring bean proxy.
2. Xác minh log attempts là 1/2/3, result cuối là `PAID O-42`.
3. Query DB: có đúng **1** dòng `payment_log`.
4. Query một mock gateway/counter: có đúng **3** side effects.
5. Cấu hình retryFor bỏ loại exception này: lần đầu ném lỗi, transaction rollback, không có retries.
6. Thêm outer `@Transactional` caller với `REQUIRED`: chạy riêng test để phát hiện phạm vi transaction bị thay đổi; không ghép thay đổi ấy vào expected stdout của demo gốc.
7. Thay gateway mock bằng một implementation **idempotent theo id**: cả ba attempts có thể gửi request, nhưng business effect chỉ xuất hiện một lần; test retry sau timeout sau khi receiver đã xử lý.
8. Giả lập crash sau DB commit nhưng trước external send: thiết kế outbox/recovery bảo đảm không làm mất event hoặc lặp ngoài contract.

## 15. Checklist code review cho Spring AOP retry + transaction

- Caller có dùng **Spring proxy** thật không, hay gọi raw target / self-invocation?
- Có bật `@EnableRetry` và `@EnableTransactionManagement` trong đúng ApplicationContext không?
- Đã xác minh **order** retry vs transaction advisors, kể cả custom `@Order` khác chưa?
- Method đang được retry có thực sự đi qua transaction interceptor cho **mỗi attempt** không?
- Có một **outer transaction** làm `REQUIRED` join vào transaction hiện hữu không?
- Chọn propagation dựa trên yêu cầu consistency hay chỉ để loại bỏ một lỗi?
- Exception có đúng loại tạm thời nên retry? `maxAttempts` đã giới hạn chưa?
- Rollback rules với checked/un-checked exceptions có khớp retry policy không?
- Có bắt/nuốt exception khiến retry hoặc rollback mất tín hiệu không?
- Method/advice có gọi email, HTTP, payment gateway, message broker hoặc mutate state ngoài transaction không?
- Với mỗi side effect, có stable idempotency key, dedupe hoặc cơ chế compensation/reconciliation không?
- Nếu event phải được publish sau DB commit, cần durable outbox hay after-commit listener đã đủ?
- Khi retry exhausted, `@Recover` và caller có trạng thái/exception rõ ràng, không thêm charge vô ý không?
- Có retry ở tầng client, gateway, worker hoặc message consumer làm tổng số attempts nhân lên không?
- Có test cả **DB final state** và **external effect count**, thay vì chỉ test response?

## 16. Kết luận

Trong Spring AOP, điều kiện “có `@Retryable` và `@Transactional`” chưa đủ để retry an toàn. **Phạm vi advice và transaction mới quyết định** mỗi lần thử có rollback/commit độc lập hay không. Retry bao ngoài transaction thường phù hợp với database transient failures, nhưng một outer transaction và `Propagation.REQUIRED` có thể khiến nhiều attempts vẫn chia sẻ cùng transaction.

Ngay cả khi mỗi attempt có transaction riêng và rollback hoạt động hoàn hảo, **side effect bên ngoài transaction vẫn có thể lặp**. Vì vậy phải kết hợp proxy boundary rõ ràng, order/propagation được kiểm chứng, retry policy hẹp, idempotency key ổn định, outbox/deduplication hoặc compensation phù hợp. Không dựa vào một annotation đơn lẻ để bảo đảm exactly-once behavior.

### Tài liệu Spring chính thức

- [Spring Retry 2.0.12 — EnableRetry API và default advisor order](https://docs.spring.io/spring-retry/docs/2.0.12/apidocs/org/springframework/retry/annotation/EnableRetry.html).
- [Spring Retry 2.0.12 — Retryable API, maxAttempts và retryFor](https://docs.spring.io/spring-retry/docs/2.0.12/apidocs/org/springframework/retry/annotation/Retryable.html).
- [Spring Retry — README, stateless/stateful retry](https://github.com/spring-projects/spring-retry).
- [Spring Framework — Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).
- [Spring Framework — Transaction Propagation, REQUIRED và REQUIRES_NEW](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).
- [Spring Framework — Proxying Mechanisms, self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Integration — Ordering Advice Chains](https://docs.spring.io/spring-integration/reference/handler-advice/order.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-004">Quan hệ với proxy của @Cacheable và thời điểm phương thức đích thực sự chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ với proxy của @Cacheable và thời điểm phương thức đích thực sự chạy trong Spring AOP

## 1. Câu hỏi trọng tâm

Với một Spring bean có phương thức gắn `@Cacheable`, mỗi lần code viết `service.find("A")` có thực sự chạy thân phương thức `find()` hay không?

**Không nhất thiết.** Khi ứng dụng bật Spring Cache ở chế độ mặc định **proxy**, lời gọi đi qua Spring AOP proxy và **cache interceptor** trước. Nếu tra cứu cache thành công với key phù hợp, interceptor trả giá trị đã lưu ngay trong chuỗi xử lý proxy: **thân phương thức của target không chạy**. Nếu cache miss và các điều kiện cho phép, interceptor gọi tiếp chuỗi invocation để target thực thi, rồi lưu kết quả theo chính sách cache.

Ví dụ ngắn về ý nghĩa:

```java
@Cacheable(cacheNames = "products", key = "#p0")
public Product find(String id) {
    System.out.println("TARGET EXECUTED");
    return repository.load(id);
}
```

Đây là **đoạn tích hợp** vào Spring bean, chưa phải chương trình chạy độc lập. Giả sử cùng key `"A"`, cache ban đầu trống, phương thức được gọi qua proxy, và không có thao tác evict:

- Lượt đầu: cache miss → log `TARGET EXECUTED` → target chạy → kết quả được lưu.
- Lượt sau: cache hit → proxy trả giá trị từ cache → **không in log**, không gọi `repository.load(id)`.
- Lượt tiếp theo sau khi cache bị xóa/expire: cache miss → target được chạy lại.

Vì vậy khi phân tích Spring AOP cần tách **“client đã gọi phương thức trên proxy”** khỏi **“target thực sự đã thực thi phương thức”**. Một lần gọi proxy có thể hoàn thành mà không đi tới target.

## 2. Spring tạo cache advice/proxy ra sao?

Spring Cache dùng cơ sở hạ tầng Spring AOP ở chế độ mặc định:

| Thành phần Spring | Trách nhiệm liên quan đến lời gọi |
| --- | --- |
| `@Cacheable` | Khai báo chính sách cache cho method hoặc class; bản thân annotation không thực thi nghiệp vụ |
| `@EnableCaching` | Bật xử lý annotation caching và đăng ký cơ sở hạ tầng advice/autoproxy |
| `CacheOperationSource` | Đọc metadata của `@Cacheable`, `@CachePut`, `@CacheEvict`... |
| `BeanFactoryCacheOperationSourceAdvisor` | Advisor kết nối thông tin method đủ điều kiện với cache advice |
| `CacheInterceptor` | Spring AOP `MethodInterceptor` quyết định tra cache, dùng kết quả cache, hay gọi tiếp invocation |
| `CacheAspectSupport` | Hỗ trợ xử lý cache operation, key, condition, unless và tương tác với cache |
| `CacheManager` / `CacheResolver` | Tìm cache thích hợp; chính cache provider lưu, đọc, evict giá trị |
| Target bean | Chạy code nghiệp vụ **khi interceptor cho invocation đi tiếp tới target** |

Trong chế độ mặc định `@EnableCaching(mode = AdviceMode.PROXY)`, Spring dùng proxy cho Spring-managed bean có cache operation hợp lệ. Proxy có thể là JDK dynamic proxy hoặc class-based proxy, tùy interface và cấu hình. Dù loại proxy khác nhau, nguyên lý cache hit bỏ qua thân target vẫn giống nhau.

```text
Mã ứng dụng
   |
   | service.find("A")
   v
Spring AOP proxy
   |
   v
CacheInterceptor / CacheAspectSupport
   |
   +--> tính key, tra cache
   |       |
   |       +--> HIT -------> trả giá trị cache
   |       |                  [target method KHÔNG chạy]
   |       |
   |       +--> MISS
   |             |
   |             v
   |       invocation.proceed()
   |             |
   |             v
   |       Target.find("A")
   |             |
   |             v
   |       kết quả -> kiểm tra unless -> put cache nếu hợp lệ
   |             |
   +-------------+------------> trả kết quả cho caller
```

Sơ đồ thể hiện **một thao tác `@Cacheable` đồng bộ đơn giản**. Nhiều cache, các cache operation khác, advice sắp xếp xen kẽ, hoặc kiểu trả về bất đồng bộ có thể thay đổi chi tiết nhưng không thay đổi nguyên lý proxy quyết định có gọi thân target hay không.

## 3. Thời điểm chính xác target chạy ở một lượt gọi thông thường

### 3.1. Cache hit

Một lượt `proxy.find(key)` dẫn tới cache interceptor. Nó resolve cache, tính key, kiểm tra giá trị trong cache và tìm thấy giá trị hợp lệ.

```text
Caller -> proxy -> cache advice -> cache.get(key): HIT
                                         |
                                         +--> return cached result
Caller <---------------------------------+
Target.find(key): 0 lần thực thi
```

Không xảy ra `invocation.proceed()` từ cache interceptor vào phần method được bao bọc. Do đó câu lệnh trong thân target, truy vấn DB, log trong thân method, cập nhật counter và exception có thể phát sinh **nếu target chạy** đều không xuất hiện trong lượt hit đó. Tuy nhiên **những advice đứng ngoài cache advice** vẫn có thể thực thi; nội dung này được giải thích ở phần thứ tự advisor.

### 3.2. Cache miss

Không tìm được giá trị tương ứng với key:

```text
Caller -> proxy -> cache advice -> cache.get(key): MISS
                                        |
                                        +--> proceed()
                                                |
                                                v
                                         Target.find(key)
                                                |
                                                v
                                           return value
                                        |
                           evaluate unless, cache put
                                        |
                                        v
                                 return to caller
```

Trong luồng đồng bộ, không có ngoại lệ và cache operation bình thường, target được gọi **một lần** cho lượt miss này. Sau khi target trả về, Spring mới có kết quả để xử lý `unless` và lưu giá trị vào cache.

**Điểm hay nhầm:** target chạy **trước** thao tác cache put do lượt miss, chứ không phải `@Cacheable` lưu giá trị rồi mới chạy target.

### 3.3. Trường hợp không lookup cache do condition

```java
@Cacheable(cacheNames = "products",
           condition = "#p0.length() < 4")
public Product find(String id) { /* ... */ }
```

Đây là **mã minh họa**. Khi điều kiện `condition` trả `false`, method vẫn chạy qua proxy nhưng **bỏ qua thao tác caching này**; không dùng hit từ cache để thay kết quả, và không put kết quả theo operation ấy. Vì vậy với `id` dài, target có thể chạy trong mọi lần gọi.

### 3.4. Trường hợp unless không cho lưu giá trị

```java
@Cacheable(cacheNames = "products",
           unless = "#result == null")
public Product find(String id) { /* ... */ }
```

`unless` được đánh giá **sau khi target trả về kết quả của cache miss**. Nếu true, Spring không put kết quả đó. Một lượt gọi cùng key sau đó lại miss và lại chạy target, trừ khi cache đã được điền bằng cách khác.

**Phân biệt:** `condition` kiểm soát có áp dụng cache operation trước khi chạy method hay không; `unless` dùng kết quả để từ chối **lưu giá trị sau lượt chạy**. `unless` không phải một điều kiện buộc method chạy trên mỗi cache hit đã tồn tại.

### 3.5. Khi target ném ngoại lệ

Nếu cache miss và target throw, lượt gọi thường truyền exception ra ngoài; Spring không có kết quả thành công để put theo `@Cacheable`. Không nên suy ra `@Cacheable` mặc định cache luôn cả exception. Khi cache provider gặp lỗi đọc/ghi, `CacheErrorHandler` và cấu hình provider có thể tác động khác tới đường xử lý: lỗi có thể được truyền ra hoặc bị xử lý, không bảo đảm target luôn chạy trong mọi lỗi.

## 4. Bảng quyết định: proxy gọi hay target thực sự chạy?

| Điều kiện ở lượt gọi qua proxy | Target method chạy? | Điều cần chú ý |
| --- | --- | --- |
| Cache hoạt động, `condition` true, cache HIT | **Không** | Caller nhận cached value; những advice ngoài cache interceptor vẫn có thể chạy |
| Cache hoạt động, `condition` true, cache MISS | **Có** | Target chạy trước khi interceptor quyết định put |
| `condition` false | **Có** | Operation cache bị bỏ qua cho lượt đó |
| MISS nhưng `unless` true | **Có** | Trả giá trị vừa tính, nhưng không lưu vì veto |
| MISS rồi target throw | **Có**, nhưng thất bại | Không put kết quả thành công của method |
| Cache entry đã evict/expire hoặc key khác | **Có khi miss** | “Cùng method” chưa đủ, phải là cùng cache + key còn hợp lệ |
| Gọi `this.find()` từ bên trong target | **Có**, bỏ qua cache advice của method đó | Self-invocation không qua proxy |
| Gọi trực tiếp instance target thay vì Spring bean proxy | **Có**, nếu gọi method bình thường | Annotation không tự chèn interceptor vào raw object |
| `@CachePut` thay vì `@Cacheable` | **Có** | `@CachePut` dùng kết quả chạy method để cập nhật cache |
| `@CacheEvict(beforeInvocation = false)` | **Có** | Mặc định evict sau khi method thực thi thành công |
| `@Cacheable(sync = true)` đồng thời cùng key | **Phụ thuộc hit/miss và provider** | Tối ưu tránh nhiều thread cùng tính; không bảo đảm mọi provider khóa giống nhau |

Bảng giả định lời gọi thông thường và method chưa bị advice khác chặn trước. Một `@Around` bên ngoài hoàn toàn có thể quyết định không gọi `proceed()`; khi đó cả cache advice lẫn target ở bên trong có thể không chạy.

## 5. Ví dụ chạy được với Java 17+ và Spring Framework

Ví dụ sử dụng Spring Framework 6.2, `@EnableCaching`, `ConcurrentMapCacheManager` và Spring AOP proxy. Không cần Spring Boot, database, web server hay thư viện cache ngoài.

Kết quả cho thấy ba hiện tượng riêng biệt: **cache miss chạy target**, **cache hit không chạy target**, **self-invocation bỏ qua cache proxy**.

### Bước 1: pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-cache-proxy-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
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

### Bước 2: src/main/java/CacheProxyDemo.java

```java
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class CacheProxyDemo {

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    public static class Config {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("items");
        }

        @Bean
        public Catalog catalog() {
            return new Catalog();
        }
    }

    // Class cần không final; method được cache là public và không final.
    public static class Catalog {
        private final AtomicInteger executions = new AtomicInteger();

        @Cacheable(cacheNames = "items", key = "#p0")
        public String find(String id) {
            int run = executions.incrementAndGet();
            System.out.println("TARGET find " + id + " run=" + run);
            return id + "#" + run;
        }

        // this.find(...) gọi trực tiếp trên target, bỏ qua cache advice.
        public String inside(String id) {
            return this.find(id);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            Catalog service = context.getBean(Catalog.class);
            System.out.println("proxy: " + AopUtils.isAopProxy(service));

            System.out.println("external 1: " + service.find("A"));
            System.out.println("external 2: " + service.find("A"));
            System.out.println("self call: " + service.inside("A"));
            System.out.println("external 3: " + service.find("A"));
            System.out.println("external B: " + service.find("B"));

            Cache cache = context.getBean(CacheManager.class).getCache("items");
            if (cache == null) {
                throw new IllegalStateException("Cache items not configured");
            }
            cache.evict("A");

            System.out.println("after evict: " + service.find("A"));
        }
    }
}
```

### Bước 3: chạy

Từ thư mục chứa `pom.xml`:

```bash
mvn -q -Dexec.mainClass=CacheProxyDemo compile exec:java
```

Stdout dự kiến từ chương trình (không tính thông báo Maven hoặc log môi trường):

```text
proxy: true
TARGET find A run=1
external 1: A#1
external 2: A#1
TARGET find A run=2
self call: A#2
external 3: A#1
TARGET find B run=3
external B: B#3
TARGET find A run=4
after evict: A#4
```

### Đọc kết quả từng lượt

1. `proxy: true`: bean lấy từ Spring context đã được Spring AOP proxy.
2. `external 1`: key `"A"` chưa có, cache interceptor gọi `Catalog.find` thực; counter tăng lên 1; lưu `A#1`.
3. `external 2`: key `"A"` hit; không có thêm `TARGET find A`; kết quả vẫn `A#1`.
4. `self call`: client gọi `inside()` trên proxy nhưng **bên trong target** có `this.find("A")`, bỏ qua interceptor dành cho `find()`. Target tăng counter tới 2 và trả `A#2`; **không cập nhật** cache cho key `"A"`.
5. `external 3`: đi qua proxy và cache hit cũ, vẫn là `A#1`, chứng minh self-invocation không can thiệp cache entry.
6. `external B`: key `"B"` chưa có; target thực thi lần thứ 3.
7. `cache.evict("A")`: xóa key `"A"` trực tiếp qua `Cache`. Lượt sau miss, target chạy lần thứ 4 và trả `A#4`.

**Giới hạn của ví dụ:** `ConcurrentMapCacheManager` chỉ dùng cache trong bộ nhớ của JVM. Nó thích hợp để kiểm chứng cơ chế Spring AOP, không minh họa TTL, phân tán hay tính bền vững của Redis/Caffeine. Số đếm dùng `AtomicInteger` để tránh race đơn giản khi có nhiều lượt chạy đồng thời, nhưng chính sách cache đồng thời là chủ đề riêng.

## 6. CacheInterceptor có phải “Aspect” tùy biến của ứng dụng không?

Trong chế độ proxy, Spring cache advice là **cơ sở hạ tầng Spring AOP**. Bạn không nhất thiết cần tự định nghĩa `@Aspect` hoặc `@Around`:

```java
@Configuration
@EnableCaching
class CachingConfig {
    // CacheManager bean và các bean khác của ứng dụng.
}
```

Đoạn trên chỉ minh họa cấu hình; cần đăng ký `CacheManager` và các bean thích hợp để chạy thật. `@EnableCaching` chịu trách nhiệm bật advisor/interceptor dành cho cache annotation, tương tự cách các cơ chế Spring khác dùng advisor.

`CacheInterceptor` implement `MethodInterceptor`. Trong khía cạnh điều khiển luồng, nó giống một interceptor “around” vì có thể chọn **gọi tiếp `invocation.proceed()` hoặc trả kết quả ngay**. Nó khác việc tự viết `@Around` ở chỗ hoạt động gắn với `CacheOperationSource`, `CacheManager`, key/condition/unless và hợp đồng cache của Spring.

Có thể thấy một Spring AOP proxy chứa nhiều advisor: cache, transaction, security hoặc advice của ứng dụng. Việc có một cache hit cho method **không có nghĩa toàn bộ proxy không làm gì**; các interceptor đứng ngoài cache interceptor vẫn có thể chạy.

## 7. Quan hệ với TargetSource: đã có proxy chưa chắc target method chạy

Trong Spring AOP, `TargetSource` và `CacheInterceptor` giải quyết hai câu hỏi độc lập:

- `TargetSource`: **proxy dùng target object nào** cho invocation này? Nó có thể trả singleton, object theo scope, object được mượn từ pool hoặc target vừa hoán đổi.
- `CacheInterceptor`: **có cần gọi tiếp invocation xuống target để tính kết quả không**, hay trả kết quả cache ngay?

```text
Spring AOP proxy
   |
   +-- TargetSource ----> target object được chọn cho invocation
   |
   +-- interceptor chain
          |
          +-- CacheInterceptor
                  |
                  +-- HIT -> return result (method body không chạy)
                  |
                  +-- MISS -> proceed() -> target.method()
```

**Một phân biệt quan trọng:** `cache hit` chứng minh **thân phương thức target không thực thi**, nhưng **không bảo đảm target object chưa được truy xuất từ `TargetSource`**. Với luồng gọi tiêu chuẩn của một số Spring AOP proxy, target có thể đã được lấy để chuẩn bị interceptor chain trước khi cache advice quyết định hit. Với scoped proxy, có thể có nhiều lớp proxy và nhiều bước lookup.

Vì thế:

- Không dùng log trong constructor, `TargetSource.getTarget()` hay scope lookup **một mình** để kết luận thân method có chạy.
- Muốn biết thân method chạy, đặt counter/log **trong chính thân method** hoặc quan sát tác dụng cụ thể của method.
- Muốn biết proxy có cache hit, xem thêm cache provider, key, interceptor và metric cache hit/miss.
- Một target instance có thể tồn tại và vẫn **không** được gọi cho lượt cache hit.

Nếu `TargetSource` động hoặc target theo request, thời điểm target được lấy còn phụ thuộc **vị trí của cache advice so với scoped proxy/target resolver**. Không được khẳng định rằng cache hit luôn bỏ qua mọi bước resolve target hoặc mọi scope.

## 8. Thứ tự advisor: cache advice đứng trong hay ngoài advice khác?

Khi cùng một method có `@Cacheable`, `@Transactional` hoặc custom `@Around`, thứ tự interceptor quyết định phần nào vẫn chạy trên cache hit.

### Trường hợp A: cache ở ngoài transaction

```text
Caller
  -> CacheInterceptor
      -> HIT: return cached value
      -> MISS: TransactionInterceptor
                  -> target.method()
```

Ở cache hit, transaction interceptor **không được đi vào** trong sơ đồ này. Điều đó có nghĩa không mở transaction mới **do interceptor nằm trong cache advice** chỉ để trả kết quả cache. Nếu target chạy do miss, transaction interceptor mới tham gia.

### Trường hợp B: transaction ở ngoài cache

```text
Caller
  -> TransactionInterceptor
       -> CacheInterceptor
           -> HIT: cached value
           -> MISS: target.method()
```

Ở cache hit, transaction interceptor **vẫn đã được vào** và có thể tạo/tiếp tục transaction tùy propagation và trạng thái hiện có, cho dù method body không chạy.

### Trường hợp C: custom logging aspect

```text
@Around logging bên ngoài
    -> CacheInterceptor
        -> HIT: return cached value
    <- logging nhận kết quả cache, chạy after logic
```

Nếu logging aspect được sắp **bên trong** cache interceptor, khi cache hit logging advice **có thể không chạy**. Do đó thiếu log trong một `@Aspect` không tự chứng minh request không vào proxy; cần biết **advisor order**.

Trong Spring, `@EnableCaching(order = ...)` cấu hình thứ tự advisor cache, còn `@EnableTransactionManagement(order = ...)` cấu hình advisor transaction. Thứ tự ưu tiên cao hơn (giá trị order nhỏ hơn) thường nằm **bên ngoài** và được gọi trước. Cả hai có default order `Ordered.LOWEST_PRECEDENCE`; nếu không đặt thứ tự cụ thể, **không nên dựa vào giả định** luôn cache ngoài transaction hoặc ngược lại.

Ví dụ định hướng thứ tự, **mảnh cấu hình** trong ứng dụng có cả hai cơ chế:

```java
@Configuration
@EnableCaching(order = 10)
@EnableTransactionManagement(order = 20)
class AdviceOrderingConfig {
    // Beans: CacheManager và transactionManager phải được cấu hình.
}
```

Trong mô hình interceptor cùng áp dụng một join point và không có advisor khác chen vào, cache advisor order 10 có ưu tiên trước transaction advisor order 20. Đây là **quyết định mang ý nghĩa nghiệp vụ**, cần test riêng với giao dịch ghi, thao tác cache evict và cache consistency.

**Không nhầm “method body không chạy” với “không có transaction” hoặc “không có security check”**: kết luận còn phụ thuộc advice bên ngoài và điều kiện propagation. Với security, đặc biệt cần tránh mô hình cache có thể trả dữ liệu trước khi áp dụng kiểm soát truy cập dự kiến.

## 9. Self-invocation và các giới hạn của proxy @Cacheable

Khi `@EnableCaching` ở chế độ proxy, `@Cacheable` cần lời gọi **qua Spring bean proxy**. Ví dụ:

```java
@Service
class ProductService {
    public String batch(String id) {
        return this.find(id); // bypass cache interceptor của find
    }

    @Cacheable(cacheNames = "products", key = "#p0")
    public String find(String id) {
        return "loaded:" + id;
    }
}
```

Đây là fragment; `batch` tự gọi `find` trên target nên không tra cache cho bước nội bộ. Nếu caller gọi `proxy.find("A")` trực tiếp, interceptor mới có cơ hội trả cached value. Giải pháp rõ ràng là tách `find` sang bean khác, inject bean đó và gọi qua proxy.

Với Spring AOP proxy: public method trên concrete class là hợp đồng an toàn nhất; JDK proxy expose interface methods, còn class-based proxy không override private/final methods hoặc final class. Annotation trong constructor/`@PostConstruct` không bảo đảm lời gọi được advice. Không tự lấy `new ProductService()` để kiểm thử caching.

Cấu hình weaving bằng `@EnableCaching(mode = AdviceMode.ASPECTJ)` là mô hình khác, cần `spring-aspects` và weaving được thiết lập; nó không tự xảy ra khi chỉ thêm `spring-aop`.

## 10. Các annotation cache khác có ảnh hưởng gì tới target execution?

| Annotation | Quan hệ với thực thi target khi gọi qua proxy |
| --- | --- |
| `@Cacheable` | Có thể **bỏ qua target method** nếu cache hit; miss thì gọi và có thể put kết quả |
| `@CachePut` | **Luôn dự kiến thực thi target method** để có kết quả mới cập nhật cache, nếu không bị advice khác chặn |
| `@CacheEvict` | **Không tự skip method**; mặc định evict sau invocation thành công; `beforeInvocation=true` thực hiện eviction trước method |
| `@Caching` | Nhóm nhiều operation; đường chạy phải xét từng operation và condition tương ứng |
| `@CacheConfig` | Chia sẻ cấu hình như cacheNames, keyGenerator, cacheManager; không tự bật thao tác cache |

Không nên gắn `@Cacheable` và `@CachePut` vào cùng method nếu không có các điều kiện loại trừ nhau rõ ràng: `@Cacheable` muốn skip target trên hit còn `@CachePut` muốn luôn chạy method để cập nhật cache. Sự kết hợp tùy tiện dễ dẫn đến hành vi khó dự đoán.

### `sync = true`: trường hợp đồng thời

```java
@Cacheable(cacheNames = "products", key = "#p0", sync = true)
public Product find(String id) {
    return loadProduct(id);
}
```

Đây là **mã tích hợp minh họa**. Khi nhiều thread gọi cùng key đang miss, Spring đưa việc tải giá trị vào callback của cache provider, giúp provider có thể đồng bộ để hạn chế tính toán lặp. Không phải mọi provider đều bảo đảm giống nhau; cần xem khả năng `Cache.get(key, Callable)`.

Các hạn chế của `sync=true` trong Spring: chỉ cho một cache, không dùng `unless`, và không kết hợp với các cache operation khác trên cùng method. Vẫn phải phân biệt **lời gọi đến proxy** với **số lần loader/target thực sự chạy**. Mặc định `sync=false`, các thread đồng thời có thể cùng miss rồi cùng chạy target.

### Async, CompletableFuture và reactive

Từ Spring Framework 6.1, cache abstraction có hỗ trợ thích nghi một số kiểu trả về như `CompletableFuture` và reactive publisher nếu cache provider phù hợp. Khi đó “thời điểm chạy thân Java method” và “thời điểm async/reactive computation hoàn tất” có thể khác nhau. Cần quan sát cả invocation lẫn completion, không đánh đồng thời điểm publisher được tạo với lúc dữ liệu được phát ra. Ví dụ đồng bộ trước đó cố ý tránh các khác biệt này để thể hiện chính xác đường đi proxy cơ bản.

## 11. Cache + scoped proxy hoặc transaction: cần quan sát đúng lớp proxy

Một bean có thể chịu nhiều cơ chế Spring: scope proxy để resolve request/session bean, AOP proxy để áp dụng caching/transaction, và advisor do ứng dụng đăng ký. Không được mặc định lúc nào cũng chỉ có một proxy hoặc chỉ có một target source.

Ví dụ khi singleton giữ tham chiếu tới request-scoped bean có `@Cacheable`, có thể có:

```text
Singleton client
   |
   v
Scoped proxy / Spring AOP proxy (cấu trúc cụ thể tùy cấu hình)
   |
   +--> resolve target theo request scope (khi cần)
   |
   +--> cache advice (tùy lớp và thứ tự)
   |
   +--> target.method() nếu invocation còn đi tới đó
```

Hai sự kiện cần đo riêng:

1. **Lấy target:** scope/target source xác định instance nào đang phục vụ.
2. **Thực thi thân method:** cache interceptor trên đường đi có thể hit và bỏ qua bước này.

Có một log “bean request được resolve” **không chứng minh method body đã chạy**. Ngược lại, cache hit không chứng minh không có bất kỳ proxy/scoped lookup nào xảy ra. Nếu cache key dùng dữ liệu request, user, locale, tenant hay security context, cần tính rõ vào key hoặc chính sách cache để tránh dùng kết quả sai ngữ cảnh.

Đặc biệt, nếu cache advisor và transaction advisor cùng áp dụng, cache invalidation/put cần phù hợp với **ranh giới commit**. Đừng giả định thao tác cache luôn xảy ra sau commit chỉ vì method được gắn `@Transactional`; kiểm tra thứ tự interceptor và cơ chế transaction-aware cache nếu có yêu cầu nhất quán dữ liệu.

## 12. Cách debug đúng khi nghi cache không hoạt động

Thay vì chỉ kiểm tra `@Cacheable`, nên thu thập **ba loại bằng chứng**:

1. **Proxy có thực sự tồn tại?** Lấy Spring bean từ context và kiểm tra `AopUtils.isAopProxy(bean)`. Có thể xem advisor nếu bean implement `Advised`.
2. **Cache có thật sự hit/miss?** Kiểm tra đúng cache name, key, condition, cache provider, TTL/eviction, `CacheErrorHandler`, metric/log của cache.
3. **Thân method đã chạy?** Đặt counter/log trong thân method; kiểm tra gọi qua proxy hay `this`; đối chiếu DB query hoặc side effect cần quan sát.

Mảnh code chẩn đoán trong ứng dụng Spring (không phải chương trình độc lập):

```java
Object bean = applicationContext.getBean("catalog");

System.out.println(
    org.springframework.aop.support.AopUtils.isAopProxy(bean)
);

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Nếu lớp được quan sát là proxy ngoài, danh sách advisor có thể chưa phản ánh các proxy lồng bên trong. Cần kiểm tra cả phương thức được gọi, target class, annotation trên concrete class, và kết quả cache ở đúng provider. **Không nên tự gọi `TargetSource.getTarget()` trong production chỉ để nhìn object** nếu không quản lý release/lifecycle tương ứng.

### Lỗi thực tế và hướng xử lý

| Triệu chứng | Nguyên nhân Spring thường gặp | Kiểm tra / xử lý |
| --- | --- | --- |
| `@Cacheable` vẫn chạy query mọi lần | Không có `@EnableCaching`, gọi raw target, self-invocation, key thay đổi, entry bị evict | Xác minh proxy và trace cache key/hit/miss |
| Không thấy log trong method ở request thứ hai | Cache hit làm method body không chạy | Đối chiếu counter bên trong method với metric cache |
| Log `@Around` vẫn xuất hiện dù cache hit | Logging advice nằm ngoài cache advice | Kiểm tra advisor order |
| Không thấy log `@Around` khi cache hit | Logging advice nằm trong cache advice | Kiểm tra `@Order`, `@EnableCaching(order)` và luồng `proceed()` |
| Cache hit nhưng transaction vẫn mở | Transaction interceptor nằm ngoài cache interceptor | Kiểm tra thứ tự advisor và propagation |
| Self-call tạo kết quả khác rồi external call vẫn trả cũ | Lời gọi `this.find()` bypass cache; cache entry cũ chưa cập nhật | Tách bean, gọi qua proxy, hoặc thiết kế lại entry point |
| Hai request cùng key đều chạy target | Miss đồng thời khi không đồng bộ provider | Kiểm tra `sync`, provider và metric concurrent load |
| `unless` true mà method vẫn chạy lại | Kết quả bị từ chối cache sau miss | Đây là hành vi đúng; xem điều kiện và `#result` |
| Method final/private có annotation nhưng không hiệu lực | Spring proxy không intercept được đúng method | Dùng public non-final method và gọi qua proxy |
| Có proxy nhưng vẫn bypass caching | Advisor không match method, điều kiện false, hoặc có proxy khác/chưa đúng ApplicationContext | Kiểm tra `CacheOperationSource`, scope đăng ký và advisor chain |

## 13. Kiểm thử nên chứng minh những gì?

Với một service có `@Cacheable`, một bài test tích hợp Spring đáng tin nên kiểm tra **cả giá trị trả về lẫn số lần target chạy**:

- **Miss lần đầu:** target counter tăng; giá trị được trả và cache nhận entry đúng key.
- **Hit lần hai:** kết quả trả về đúng, nhưng target counter không tăng.
- **Evict:** sau eviction, lời gọi ngoài proxy tiếp theo làm target counter tăng.
- **Condition/unless:** kiểm tra bypass trước method và veto sau method là hai trạng thái khác nhau.
- **Self-invocation:** phương thức nội bộ không tạo cache lookup như lời gọi ngoài proxy.
- **Advice ordering:** khi có `@Transactional` hoặc `@Around`, assert chính xác loại advice nào chạy trong hit và miss.
- **Provider thực:** nếu ứng dụng dùng Redis, Caffeine hoặc multi-instance, test với provider đó vì `ConcurrentMapCacheManager` không mô phỏng TTL, consistency hoặc đồng bộ phân tán.

Với thay đổi cache trong đường nghiệp vụ quan trọng, test cần bắt lỗi do **side effect của method bị bỏ qua khi hit**. Một method dùng cache nên được thiết kế như tác vụ truy vấn mà việc tái sử dụng kết quả là hợp lệ; không nên đặt thao tác bắt buộc phải xảy ra mỗi lượt (audit, gửi email, cập nhật DB) vào thân method nếu `@Cacheable` có thể skip nó.

## 14. Tài liệu Spring chính thức

- Spring Framework 6.2 — *Declarative Annotation-based Caching*: https://docs.spring.io/spring-framework/reference/6.2/integration/cache/annotations.html
- Spring Framework 6.2 — *Understanding the Cache Abstraction*: https://docs.spring.io/spring-framework/reference/6.2/integration/cache/strategies.html
- Spring Framework 6.2 — *Proxying Mechanisms*: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Javadoc `@Cacheable` (Spring 6.2.18): https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/cache/annotation/Cacheable.html
- Javadoc `@EnableCaching` (Spring 6.2.18): https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/cache/annotation/EnableCaching.html
- Javadoc `CacheInterceptor` (Spring 6.2.18): https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/cache/interceptor/CacheInterceptor.html

**Ghi nhớ:** Lời gọi `@Cacheable` đến **proxy** không đồng nghĩa thân **target method** chạy. Spring cache advice có quyền trả cached value mà không gọi tiếp invocation. Muốn lý giải đầy đủ, phải xét cache key và hit/miss, phạm vi proxy/self-invocation, thứ tự advice, và phân biệt resolve target với thực thi phương thức nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-005">Quan hệ với @Async và ranh giới chuyển luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Quan hệ với @Async và ranh giới chuyển luồng

## Mục tiêu

Trong Spring Framework, `@Async` làm cho một lời gọi method đủ điều kiện được **đưa sang `Executor`**, còn Spring AOP áp dụng **advisor/interceptor chain** lên các lời gọi **đi qua proxy**. Khi cả hai cơ chế cùng áp dụng, phải xác định chính xác **advice nào chạy trên thread của caller, advice nào chạy trên worker, lời gọi nào không được intercept và ngữ cảnh nào không tự đi theo khi đổi thread**.

Bài này tập trung vào cách Spring AOP tích hợp với `org.springframework.scheduling.annotation.Async`, không đi sâu vào lý thuyết multithreading tổng quát. Sau bài học, bạn có thể:

- Giải thích vai trò của `@EnableAsync`, `AsyncAnnotationBeanPostProcessor`, `AsyncAnnotationAdvisor`, `AnnotationAsyncExecutionInterceptor` và Spring AOP proxy.
- Phân biệt lời gọi `@Async` thông qua proxy với `this.asyncMethod()` trong cùng bean.
- Dự đoán tác động của **thứ tự advisor** đến thread thực thi `@Before`, `@Around`, `@AfterReturning` và `@AfterThrowing`.
- Dùng `TaskExecutor` tường minh và `CompletableFuture` để quan sát ranh giới caller/worker.
- Hiểu tại sao `ThreadLocal`, transaction, request context và các target dựa trên thread không tự được truyền theo invocation async.
- Kiểm thử những tình huống đồng bộ ngoài ý muốn, lỗi bất đồng bộ và context propagation một cách có kiểm soát.

## 1. Hai lớp can thiệp: AOP proxy và Executor

Khi bật Spring async theo chế độ mặc định `AdviceMode.PROXY`, annotation `@Async` được xử lý bởi một **advisor** gắn vào Spring AOP proxy. Đây không phải thao tác tạo một thread bất kỳ ngay lúc Spring đọc annotation.

Đường đi tiêu biểu:

~~~text
Caller thread
    |
    | Gọi bean proxy.asyncMethod(...)
    v
Spring AOP proxy
    |
    v
AsyncAnnotationAdvisor / async interceptor
    |
    | Submit task vào TaskExecutor
    +-----------------------------+
    |                             |
    v                             v
Caller nhận Future/void      Executor worker thread
                                  |
                                  v
                         invocation.proceed()
                                  |
                                  v
                        Advice còn lại (nếu có)
                                  |
                                  v
                            Target method
                                  |
                                  v
                           Completion/result
~~~

Đây là sơ đồ của trường hợp **async advisor nằm ngoài các advisor khác**, đúng với cấu hình phổ biến của `AsyncAnnotationBeanPostProcessor`. Nó không phải lời cam kết mọi cấu hình advisor đều có thứ tự y hệt.

Cần tách ba điểm:

1. **AOP interception:** Spring phải nhìn thấy lời gọi qua proxy để interceptor có thể chạy.
2. **Scheduling boundary:** async interceptor gửi phần tiếp theo của invocation sang một `Executor` phù hợp.
3. **Continuation/target execution:** những interceptor **nằm bên trong async interceptor** và business method được worker thực thi khi task bắt đầu.

`@Async` không tự bảo đảm mọi advice chạy bên worker; điều đó còn phụ thuộc advisor ordering và proxy chain thực tế.

## 2. Hạ tầng Spring đứng sau @Async

Các thành phần quan trọng:

| Thành phần | Vai trò trong Spring |
| --- | --- |
| `@Async` | Đánh dấu class/method cần được ứng viên xử lý bất đồng bộ |
| `@EnableAsync` | Bật xử lý annotation async cho `ApplicationContext` có cấu hình đó |
| `AsyncAnnotationBeanPostProcessor` | Bean post-processor gắn `AsyncAnnotationAdvisor` vào AOP proxy hiện hữu hoặc tạo proxy mới nếu cần |
| `AsyncAnnotationAdvisor` | Advisor dùng pointcut theo annotation async và async execution advice |
| `AnnotationAsyncExecutionInterceptor` | Interceptor chuyên xử lý annotation, quyết định executor và submit công việc |
| `TaskExecutor` / `Executor` | Nơi thực thi task sau khi rời thread gọi |
| `AsyncConfigurer` | Tùy chỉnh executor mặc định và handler cho lỗi của `void @Async` |

`@EnableAsync` không đồng nghĩa với `@EnableAspectJAutoProxy`:

- `@EnableAsync` đăng ký hạ tầng async annotation.
- `@EnableAspectJAutoProxy` bật hạ tầng dùng các `@Aspect` bean theo phong cách @AspectJ trong Spring AOP, cần dependency hỗ trợ phù hợp.
- Bạn có thể sử dụng `@Async` **không cần tự viết `@Aspect`**; async vẫn dùng Spring AOP proxy ở chế độ mặc định.
- Khi dùng cả hai, advisors có thể được Spring phối hợp trên proxy. Để biết hành vi chính xác, cần quan sát bean và chain thực tế.

### Cấu hình tối thiểu

~~~java
@Configuration
@EnableAsync
class AsyncConfig {
}
~~~

Đây là **fragment**, cần import `org.springframework.context.annotation.Configuration` và `org.springframework.scheduling.annotation.EnableAsync`, đồng thời đăng ký config trong Spring context. Không chỉ thêm `@Async` vào một method rồi gọi `new Service()` là có async.

### Executor được lựa chọn thế nào?

Spring có quy tắc tìm executor mặc định, chẳng hạn một `TaskExecutor` phù hợp/duy nhất hoặc bean `Executor` mang tên `taskExecutor`; nếu không có executor được phân giải, framework có thể dùng `SimpleAsyncTaskExecutor`. Để hành vi và tải hệ thống có thể dự đoán, **đặt tên executor tường minh**:

~~~java
@Async("businessExecutor")
public CompletableFuture<String> run() {
    return CompletableFuture.completedFuture("done");
}
~~~

Đoạn trên chỉ là **method minh họa**, cần bean executor có tên `businessExecutor` và Spring async được bật. `@Async("businessExecutor")` lựa chọn executor theo tên/qualifier được cấu hình; method vẫn phải được gọi qua proxy nếu dùng `AdviceMode.PROXY`.

Trong môi trường production, cần cấu hình giới hạn thread/queue/rejection/shutdown và giám sát độ trễ queue. Annotation async không thay cho một chính sách quản lý tải.

## 3. Ranh giới quan trọng nhất: gọi qua proxy hay gọi nội bộ

Trong Spring AOP mặc định, lời gọi phải đi **qua proxy**.

~~~java
@Service
class ExportService {
    @Async
    public CompletableFuture<String> export(String id) {
        return CompletableFuture.completedFuture("ok:" + id);
    }

    public String exportLocally(String id) {
        return this.export(id).join();
    }
}
~~~

Đây là **fragment** cần Spring context có `@EnableAsync` và import `Service`, `Async`, `CompletableFuture`. Ý nghĩa hai đường gọi:

- `anotherBean.exportService.export("A")`: nếu `exportService` là Spring-managed proxy và method đủ điều kiện, async interceptor xử lý lời gọi; caller thường nhận một `CompletableFuture` trước khi công việc hoàn tất.
- `exportLocally("B")` gọi `this.export("B")` **trực tiếp trên target**, bỏ qua Spring AOP proxy. Annotation `@Async` trên `export` **không** khiến lời gọi nội bộ tự chuyển sang executor. Trong fragment này, target trả `completedFuture` nên `join()` xảy ra đồng bộ trên thread đang thực thi `exportLocally`.

**Đổi JDK dynamic proxy sang CGLIB không sửa self-invocation.** Với proxy-based AOP, `this.method()` ở bên trong target vẫn bypass proxy. Cách thiết kế thường rõ ràng nhất là tách async method sang một Spring bean khác và để caller inject proxy của bean đó. Self-injection hoặc `AopContext.currentProxy()` có thể dùng trong trường hợp đặc biệt nhưng làm tăng coupling đến hạ tầng AOP.

### Ranh giới proxy type

- JDK dynamic proxy thường expose các method qua interface được proxy.
- CGLIB dựa vào subclass; class/method `final` và method `private` gây các giới hạn không thể advice theo override.
- `@EnableAsync(proxyTargetClass = true)` có thể ép class proxy trong phạm vi Spring proxy configuration; cần kiểm tra thiết lập cuối cùng khi ứng dụng còn có transaction/caching/AOP khác.
- Dù proxy hợp lệ, pointcut/advisor phải match method đang được gọi.

Không thể dựa vào việc method được annotate để kết luận executor chắc chắn đã được gọi.

## 4. Thứ tự async advisor quyết định thread của AOP advice

`AsyncAnnotationBeanPostProcessor` dùng `AsyncAnnotationAdvisor` và theo mặc định **đặt advisor async trước các advisor đã có**, nhằm chuyển việc thực thi sang async sớm nhất có thể. `@EnableAsync.order` là **thứ tự của BeanPostProcessor**, còn `@Order` trên aspect/advisor thường điều khiển **thứ tự advice**. Chúng không phải cùng một loại thứ tự.

### Trường hợp A — async ở ngoài

~~~text
caller
  |
  v
ASYNC interceptor
  | submit()
  +--------------------> worker
                             |
                             v
                       Logging @Around
                             |
                             v
                        Business method
~~~

Trong cách sắp xếp này, cả logging advice bên trong và target method chạy trên **worker**. Caller nhận handle async, chứ không đợi logging/target hoàn tất (trừ khi có cơ chế chờ hoặc executor đặc biệt).

### Trường hợp B — một advice khác ở ngoài async

~~~text
caller
  |
  v
Outer logging advice
  |
  v
ASYNC interceptor
  | submit()
  +--------------------> worker -> target / inner advice
  |
  v
Outer logging advice returns to caller
~~~

Trong cách sắp xếp này, advice bên ngoài nhìn thấy thao tác **submit và handle trả về**, không mặc nhiên là thời điểm method bên worker đã hoàn thành. Nếu outer advice dùng:

~~~java
Object result = invocation.proceed();
System.out.println("after proceed");
return result;
~~~

đây là **pseudocode MethodInterceptor**. Khi `proceed()` trả về một `Future` từ async interceptor, câu `after proceed` có thể chạy trên **caller**, trước khi công việc async hoàn tất.

### Đối với từng loại advice Spring AOP

| Loại advice | Nếu ở bên trong async interceptor | Nếu ở bên ngoài async interceptor |
| --- | --- | --- |
| `@Before` | Chạy trên worker trước business method | Có thể chạy ở caller trước khi submit |
| `@Around` | `proceed()` đi tiếp tới business target trên worker | `proceed()` có thể chỉ submit task và trả async handle |
| `@AfterReturning` | Quan sát đường trả về bình thường của phần bên trong worker | Có thể quan sát handle trả về thành công ở caller, dù task sau đó lỗi |
| `@AfterThrowing` | Có thể quan sát lỗi target ném trên worker nếu exception đi qua advisor đó | Không mặc nhiên thấy lỗi phát sinh muộn từ worker sau khi async handle đã được trả |

Đây là bảng **semantics theo vị trí trong chain**, không khẳng định mọi phương pháp annotation đều có đúng một thứ tự khi nhiều hạ tầng proxy cùng áp dụng. Đặc biệt, exception từ task bất đồng bộ không tự biến thành exception đồng bộ tại caller.

**Hệ quả cho observability:** nếu bạn đo duration bằng `@Around` ngoài async interceptor, kết quả có thể chỉ là **thời gian lập lịch**. Muốn đo thời gian xử lý business method, hãy đặt advice trong phần chạy worker hoặc đo lifecycle của `CompletableFuture` một cách rõ ràng.

## 5. Ví dụ đầy đủ: @Async và @Aspect trên một Spring AOP proxy

Ví dụ dùng Spring Framework 6.2.19, JDK 17+, Maven. Nó kiểm tra bằng **boolean và bộ đếm** để output ổn định, không phụ thuộc tên thread của Maven main thread hay thứ tự xen kẽ của log.

- Gọi `process("A")` **qua proxy**: `@Async` đưa công việc sang executor một worker tên `demo-worker-...`; `@Aspect @Around` theo thứ tự mặc định được chạy trên worker.
- Gọi `callInternally("B")` qua proxy: method này tự gọi `this.process("B")` bên trong target; async và aspect trên `process` không được kích hoạt cho lời gọi nội bộ.
- Dùng `join()` trước khi đọc quan sát của aspect để không race với completion.

### 5.1. Cấu trúc

~~~text
spring-async-aop-boundary/
├── pom.xml
└── src/main/java/example/AsyncBoundaryDemo.java
~~~

### 5.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-async-aop-boundary</artifactId>
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
            <version>1.9.24</version>
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
                    <mainClass>example.AsyncBoundaryDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Có cả `spring-context` (container, async, task executor), `spring-aop` (proxy/advisor) và `aspectjweaver` (hỗ trợ xử lý biểu thức pointcut của `@Aspect`). Ví dụ vẫn là **Spring AOP proxy-based**, không bật AspectJ compile-time weaving.

### 5.3. `src/main/java/example/AsyncBoundaryDemo.java`

~~~java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Chỉ một class public trong file này; các class còn lại package-private.
public class AsyncBoundaryDemo {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DemoConfig.class)) {
            WorkApi service = context.getBean(WorkApi.class);

            System.out.println("Spring proxy=" +
                    AopUtils.isAopProxy(service));

            String asyncResult = service.process("A").join();
            System.out.println("Async target on worker=" +
                    asyncResult.startsWith("demo-worker-"));
            System.out.println("Aspect on worker=" +
                    TraceAspect.LAST_THREAD.get().startsWith("demo-worker-"));

            String localResult = service.callInternally("B");
            System.out.println("Local self-call on caller=" +
                    localResult.equals(Thread.currentThread().getName() + ":B"));
            System.out.println("Aspect invocation count=" +
                    TraceAspect.INVOCATIONS.get());
        }
    }
}

interface WorkApi {
    CompletableFuture<String> process(String id);
    String callInternally(String id);
}

class WorkImpl implements WorkApi {
    @Override
    @Async("demoExecutor")
    public CompletableFuture<String> process(String id) {
        String result = Thread.currentThread().getName() + ":" + id;
        // Future tạm từ target; Spring async proxy cung cấp handle
        // cho task thực thi trên Executor tới caller.
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public String callInternally(String id) {
        // this.process(...) KHÔNG đi qua Spring AOP proxy.
        return this.process(id).join();
    }
}

@Aspect
class TraceAspect {
    static final AtomicReference<String> LAST_THREAD =
            new AtomicReference<>("not-called");
    static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Around("execution(* example.WorkImpl.process(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        LAST_THREAD.set(Thread.currentThread().getName());
        INVOCATIONS.incrementAndGet();
        return joinPoint.proceed();
    }
}

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableAspectJAutoProxy
class DemoConfig {
    @Bean
    public WorkApi workApi() {
        return new WorkImpl();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean("demoExecutor")
    public ThreadPoolTaskExecutor demoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("demo-worker-");
        executor.initialize();
        return executor;
    }
}
~~~

### 5.4. Chạy

~~~bash
mvn -q compile exec:java
~~~

Stdout do chương trình sinh ra trong cấu hình advisor mặc định:

~~~text
Spring proxy=true
Async target on worker=true
Aspect on worker=true
Local self-call on caller=true
Aspect invocation count=1
~~~

**Ý nghĩa output:**

- `Spring proxy=true`: bean do context trả về đã được Spring AOP proxy.
- `Async target on worker=true`: business method khi gọi qua proxy chạy trên worker được chọn từ `ThreadPoolTaskExecutor`.
- `Aspect on worker=true`: `TraceAspect` đứng sau async interceptor trong **cấu hình này**, nên advice ghi nhận worker thread.
- `Local self-call on caller=true`: `callInternally` gọi `this.process(...)` trực tiếp nên không submit task cho lần gọi đó.
- `Aspect invocation count=1`: aspect intercept một invocation `process("A")` qua proxy, **không intercept** `this.process("B")`.

Bảng và output không dựa vào một chuỗi thread name cố định của Maven. Nếu bạn tùy chỉnh thứ tự advisors/BeanPostProcessors hoặc đặt executor đặc biệt (ví dụ executor thực thi inline), không nên dùng chính output này như quy luật cho mọi cấu hình.

### 5.5. Vì sao method implementation trả `completedFuture`?

Spring `@Async` cho phép method khai báo trả `void` hoặc `Future` (trong đó có `CompletableFuture`). Với method kiểu `CompletableFuture<T>`, target có thể thực hiện tính toán trên worker và trả một **future tạm chứa kết quả**, như `CompletableFuture.completedFuture(result)`. Proxy cung cấp cho caller handle `CompletableFuture` gắn với việc thực thi đã được submit.

Không nên viết một `@Async` method trả `String` chỉ vì muốn Spring tự tạo một `CompletableFuture<String>` khác; cần tuân thủ kiểu trả về được Spring hỗ trợ.

## 6. @Async không tự truyền ThreadLocal hoặc request context

Chuyển từ caller thread sang executor worker là **ranh giới thread rõ ràng**:

~~~text
Caller ThreadLocal / MDC / transaction context
                |
                | submit task
                v
        Executor worker thread
                |
                +--> ThreadLocal riêng của worker
                +--> Transaction context riêng
                +--> Request context không tự động tương đương
~~~

`ThreadLocal` thông thường là dữ liệu **theo thread**, không tự copy an toàn khi `@Async` chuyển sang thread khác. Nếu worker thread được reuse, nó còn có thể chứa dữ liệu **còn sót lại từ tác vụ trước** nếu code tự đặt context nhưng không xóa đúng cách.

| Ngữ cảnh | Mặc định qua thread boundary | Giải pháp theo Spring |
| --- | --- | --- |
| `ThreadLocal` tùy biến | Không tự truyền | Truyền dữ liệu qua tham số hoặc dùng wrapper/decorator có capture và cleanup rõ ràng |
| MDC / tracing observation | Không bảo đảm nếu không bật propagation | `TaskDecorator`/`ContextPropagatingTaskDecorator` và context propagation đã cấu hình |
| Spring transaction của caller | Không tự đi theo sang worker | Tạo transaction mới đúng ranh giới worker nếu cần, qua Spring transaction advisor/proxy |
| Spring MVC request attributes | Không tự bảo đảm truy cập được ở worker | Truyền snapshot dữ liệu thực sự cần; tránh giữ request object quá vòng đời |
| `SecurityContextHolder` mặc định theo thread | Không mặc nhiên là caller context trên worker | Dùng cơ chế Spring Security context propagation được hỗ trợ nếu ứng dụng có Spring Security |
| `ThreadLocalTargetSource` | Worker có target riêng theo **worker thread** | Không coi nó là per-request; kiểm tra thread reuse |

### Minh họa fragment `TaskDecorator`

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(2);
executor.setThreadNamePrefix("app-");
executor.setTaskDecorator(task -> {
    // Capture dữ liệu bất biến cần thiết ở lúc submit.
    String captured = RequestTag.get();
    return () -> {
        String previous = RequestTag.get();
        try {
            RequestTag.set(captured);
            task.run();
        } finally {
            // Phục hồi chính xác state trước task để không rò rỉ
            // context giữa các task trên worker được tái sử dụng.
            if (previous == null) RequestTag.clear();
            else RequestTag.set(previous);
        }
    };
});
executor.initialize();
~~~

Đây là **fragment minh họa**, `RequestTag` là helper giả định cung cấp `get()`/`set(String)`/`clear()`; đoạn này không chạy độc lập. Mục tiêu là chỉ ra đúng bốn giai đoạn: capture ở phía submit, install ở worker, chạy task, và restore trong `finally`. Không nên copy nguyên cả một object HTTP request hoặc mutable context vào worker chỉ để tiện truyền dữ liệu.

Spring cung cấp `ContextPropagatingTaskDecorator` phục vụ logging/observation context khi cấu hình Micrometer Context Propagation phù hợp. **Không phải tất cả ThreadLocal đều được propagate tự động**; cần có accessor/integration tương ứng và đánh giá chi phí.

## 7. @Transactional với @Async: nơi nào có transaction?

Giả sử một method đồng bộ `@Transactional` gọi một method `@Async` ở **bean khác qua proxy**:

~~~text
Caller thread:
  @Transactional begin
     |
     +-- asyncService.doWork(...) -> submit future
     |
  @Transactional commit/rollback theo caller method

Worker thread:
  asyncService.doWork(...) business code
  (không tự dùng cùng transaction context của caller)
~~~

Context transaction của Spring thông thường gắn với thread. Công việc chạy trên worker **không tiếp tục trong cùng transaction** chỉ vì caller đã có `@Transactional`.

Nếu công việc worker **tự cần transaction**, có thể thiết kế service riêng:

~~~java
@Service
class AsyncCoordinator {
    private final TransactionalWorker worker;

    AsyncCoordinator(TransactionalWorker worker) {
        this.worker = worker;
    }

    @Async("businessExecutor")
    public CompletableFuture<String> execute(String id) {
        return CompletableFuture.completedFuture(worker.save(id));
    }
}

@Service
class TransactionalWorker {
    @Transactional
    public String save(String id) {
        return "saved:" + id;
    }
}
~~~

Đây là **fragment** cần import `@Service`, `@Async`, `@Transactional`, `CompletableFuture`, khai báo executor và bật transaction management cùng transaction manager hợp lệ. `TransactionalWorker` là bean riêng: khi async coordinator gọi `worker.save(id)` qua Spring proxy, transaction advice có thể mở/đóng transaction **trên worker thread**. Nó không phải transaction của caller được truyền qua.

**Hai giới hạn cần tránh:**

1. `@Async` và `@Transactional` trên **cùng method** có thể hoạt động nếu proxy/advisor order thích hợp, nhưng ngữ nghĩa chính xác phụ thuộc advisor chain. Tách bean/ràng giới rõ ràng thường dễ đọc và dễ test hơn.
2. **Self-invocation** khiến cả async và transaction advice có thể bị bỏ qua trong chế độ proxy mặc định. Việc đặt hai annotation không sửa self-invocation.

Đối với dữ liệu cần commit trước khi async task xử lý, cân nhắc phát task sau commit hoặc dùng outbox/event pattern phù hợp, thay vì giả định task được submit chỉ sau khi transaction caller commit.

## 8. @Async và ThreadLocalTargetSource

Một điểm đặc thù của Spring AOP: nếu proxy dùng `ThreadLocalTargetSource` để cấp target riêng theo thread, thì `@Async` có thể làm **thay đổi thread quyết định target**.

Giả sử:

~~~text
Caller thread T1:
  proxy.@AsyncMethod() -> async advisor submits task

Worker thread T2:
  Spring AOP tiếp tục invocation
  ThreadLocalTargetSource.getTarget() -> target của T2
~~~

Nếu target được lấy **sau khi async interceptor đổi thread**, source cấp target theo **worker T2**, không phải T1. Ngược lại, nếu interceptor/logic nào đó truy cập target sớm hoặc order khác đi, phải kiểm tra cụ thể nơi target được lấy. Đừng suy ra target identity chỉ từ nơi method ban đầu được gọi.

Đặc biệt, `ThreadPoolTaskExecutor` thường dùng lại worker threads. Vì vậy `ThreadLocalTargetSource` có thể tái sử dụng cùng target ở **nhiều async tasks độc lập** chạy nối tiếp trên một worker. **Thread-local target không phải task-scoped hoặc request-scoped target.**

Đây là lý do không nên đặt dữ liệu tenant, request token hoặc mutable state chỉ có hiệu lực một lần trong field của target và dựa vào `ThreadLocalTargetSource` để cách ly request. Dùng parameters, request scope hợp lệ hoặc cơ chế lifecycle/context đúng ranh giới nghiệp vụ.

## 9. Lỗi trong lời gọi bất đồng bộ nhìn thấy ở đâu?

Spring phân biệt **lỗi trong lúc submit** và **lỗi khi business method thực thi trên worker**.

### `CompletableFuture<T>`

~~~java
@Async("businessExecutor")
public CompletableFuture<String> process(String id) {
    if (id.isBlank()) {
        throw new IllegalArgumentException("blank id");
    }
    return CompletableFuture.completedFuture("ok:" + id);
}
~~~

Đây là method fragment, cần config async hợp lệ. Khi exception phát sinh trên worker, future mà caller nhận thường **hoàn tất exceptionally**. Caller có thể xử lý:

~~~java
try {
    String result = proxy.process("").join();
} catch (CompletionException ex) {
    System.out.println(ex.getCause().getClass().getSimpleName());
}
~~~

Đoạn này cần biến `proxy` và import `CompletionException`. `join()` bọc lỗi đã lưu trong future bằng `CompletionException`; `get()` dùng `ExecutionException` theo API Java. Đừng dự đoán lỗi worker sẽ tự bị ném ngay ở `proxy.process("")` trên caller như một lời gọi đồng bộ.

### `void @Async`

Method trả `void` không có future để caller nhận lỗi. Spring hỗ trợ `AsyncUncaughtExceptionHandler` cho các lỗi không bắt trong method kiểu void, có thể cấu hình qua `AsyncConfigurer`. Nếu chưa cấu hình, lỗi có thể được ghi log theo mặc định; caller không thể dùng `join()` để biết trạng thái hoàn tất.

### Tác động lên after-returning/after-throwing advice

Một `@AfterReturning` **bên ngoài** async advisor có thể thấy proxy trả thành công một future, dù future sau đó hoàn tất exceptionally. Một `@AfterThrowing` **bên trong** async advisor có thể quan sát exception target ném trên worker, nhưng nó không làm caller tự động nhận exception trực tiếp; kết quả được phản ánh qua async future.

Nếu muốn audit chính xác thành công/thất bại **sau khi task hoàn tất**, phải xem xét semantic completion (`whenComplete`/`handle` trên `CompletableFuture` hoặc điều khiển theo cơ chế được hỗ trợ), thay vì đồng nhất “method trả về future” với “nghiệp vụ thành công”.

## 10. Đo đúng thời gian và đặt đúng advice

Khi đặt custom advice quanh `@Async`, xác định **đơn vị latency** trước:

- **Submit latency:** advice ở ngoài async interceptor trên caller thread đo `pjp.proceed()` đến lúc nhận handle `Future`; chưa phản ánh task kết thúc.
- **Worker invocation latency:** advice ở trong async interceptor đo target method khi worker thực thi, nhưng không nhất thiết đo công việc bất đồng bộ khác được target trả về dưới dạng Future còn pending.
- **End-to-end completion latency:** quan sát Future mà caller thực sự nhận (`whenComplete` khi là `CompletionStage`) hoặc dùng task/observation instrumentation để đo từ submit tới success/failure/cancel.

Ví dụ worker-side:

~~~java
@Around("execution(* example.WorkImpl.process(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        recordWorkerDuration(System.nanoTime() - start);
    }
}
~~~

`recordWorkerDuration` là helper của ứng dụng; đoạn trên là fragment. Không đo worker execution bằng stopwatch quanh lời gọi trên caller rồi gọi đó là business duration. Bài **“Advice quanh phương thức trả Future khác xử lý khi Future hoàn tất”** dạy đầy đủ callbacks, exceptional completion, cancellation và thời điểm transaction quan sát Future.

## 11. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Gọi `this.asyncMethod()` | Bỏ qua proxy; chạy đồng bộ | Gọi qua bean proxy khác, thiết kế lại ranh giới |
| Chỉ thêm `@Async` nhưng không bật xử lý | Annotation không được interceptor áp dụng | Cấu hình `@EnableAsync` hoặc auto-configuration tương ứng |
| Không xác định executor | Thread pool/tải hệ thống khó dự đoán | Đặt tên `@Async("businessExecutor")` và quản lý queue/pool |
| Cho rằng mọi `@Before` luôn chạy worker | Advisor có thể nằm ngoài async interceptor | Kiểm tra ordering và thực tế thread name trong advice |
| `@Around` ngoài async đo xử lý nghiệp vụ | Thực chất đo submit latency | Đặt advice trong worker chain/đo future completion |
| Cho rằng `@Transactional` của caller đi theo worker | Worker không có transaction của caller | Mở transaction đúng nơi làm việc ở worker |
| Đọc `ThreadLocal` từ worker như từ caller | Context thiếu hoặc bị nhiễm task cũ | Truyền dữ liệu tường minh hoặc TaskDecorator có cleanup |
| Đưa `@Async` lên method `private`/`final` trong CGLIB proxy | Method không intercept được | Expose public overridable method hoặc interface proxy phù hợp |
| Chờ synchronous exception ở caller | Lỗi worker nằm trong future | Dùng `join()`/`get()`/completion callbacks |
| Gọi `@Async void` rồi kỳ vọng nhận exception | Caller không có completion handle | Dùng `CompletableFuture` hoặc handler rõ ràng |
| Dùng ThreadLocalTargetSource như per-request | State có thể tồn tại qua nhiều tasks cùng worker | Dùng request/task scope và kiểm thử thread reuse |
| Test output bằng thread name của Maven main | Khác nhau tùy launcher | So sánh prefix executor và boolean/assertions |

### Ba tầng kiểm tra khi thấy “async không chạy”

1. **Hạ tầng:** `@EnableAsync`/context đúng? `AsyncAnnotationBeanPostProcessor` có được đăng ký không? Executor bean có tồn tại không?
2. **Proxy:** object lấy từ context có là proxy? method `@Async` có public/đủ điều kiện không? Có self-invocation không?
3. **Execution:** task có được submit? Worker pool có đủ capacity? Advice chạy ở đâu? Future có hoàn tất hoặc lỗi không? Context cần thiết có được truyền an toàn không?

Đừng tự sửa bằng cách thêm `new Thread(...)` vào business method khi nguyên nhân nằm ở Spring proxy; làm vậy chỉ che mất vấn đề và bỏ qua quản lý executor/context của Spring.

## 12. Bài tập tự kiểm tra

1. Vì sao `@Async` ở chế độ mặc định được xem là một loại advice trong Spring AOP proxy?
2. `@EnableAsync` và `@EnableAspectJAutoProxy` phục vụ hai cơ chế khác nhau như thế nào?
3. Khi async advisor ở ngoài `@Around`, advice có thể chạy trên caller hay worker? Vì sao?
4. Nếu `@Around` ở ngoài async advisor, câu lệnh sau `proceed()` có chắc chạy **sau khi worker hoàn tất** không?
5. Vì sao `this.process()` trong ví dụ Java không làm bộ đếm `TraceAspect.INVOCATIONS` tăng?
6. Vì sao method `CompletableFuture<String>` bên target có thể trả `completedFuture(...)`, nhưng caller vẫn nhận future bất đồng bộ?
7. Một transaction của caller có tự đi sang executor worker không? Nếu worker cần transaction thì nên cấu hình ở đâu?
8. Vì sao state trong `ThreadLocalTargetSource` có thể bị reuse giữa các `@Async` tasks khác nhau?
9. `@AfterReturning` ghi nhận future trả về bình thường có chứng minh công việc hoàn tất thành công không?
10. Bạn sẽ kiểm chứng bằng cách nào rằng logging/tracing advice đặt đúng bên worker, không chỉ bao quanh thao tác submit?
11. Vì sao context propagation cần bước restore/cleanup trong `finally` đối với thread pool?
12. Điều gì thay đổi nếu bạn cấu hình một executor thực thi task ngay trên caller thread thay vì worker thread thực sự?

## 13. Tài liệu Spring chính thức

- [Task Execution and Scheduling — `@Async`](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@Async`, `@EnableAsync`, executors, return values và giới hạn proxy.
- [AsyncAnnotationBeanPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/AsyncAnnotationBeanPostProcessor.html): gắn `AsyncAnnotationAdvisor` vào proxy và vị trí advisor mặc định.
- [EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): `AdviceMode.PROXY`, `proxyTargetClass`, BPP order và executor selection.
- [Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Async.html): method signatures, `Future`/`CompletableFuture` và executor qualifier.
- [Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB và self-invocation.
- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html): advisor và pointcut trong interceptor chain.
- [TaskDecorator Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskDecorator.html): decorator cho task và context propagation.
- [ContextPropagatingTaskDecorator Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html): hỗ trợ logging/observation context.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-006">Advice quanh phương thức trả Future khác xử lý khi Future hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Advice quanh phương thức trả Future khác xử lý khi Future hoàn tất trong Spring AOP

## 1. Hai mốc khác nhau trong Spring AOP

Khi Spring AOP proxy gọi một phương thức trả về `Future`, `CompletableFuture` hoặc `CompletionStage`, phải phân biệt **phương thức đã trả về một handle** với **tác vụ mà handle đó biểu diễn đã hoàn tất**. Method có thể return một `CompletableFuture<String>` còn đang *pending*; kết quả chỉ sẵn sàng sau vài giây, có thể trên một thread khác và có thể hoàn tất thất bại.

Đối với Spring AOP:

- `@Before`: chạy trước lời gọi được match.
- `@AfterReturning`: chạy khi lời gọi method return bình thường, cho dù Future trả về còn incomplete hoặc về sau failed.
- `@AfterThrowing`: xử lý exception được **ném trực tiếp từ invocation**; không tự trở thành callback cho một Future bị `completeExceptionally(...)` về sau.
- `@After`: có semantics tương tự `finally` trên invocation, nên cũng có thể chạy ngay khi Future mới được tạo.
- `@Around`: code trước/sau `ProceedingJoinPoint.proceed()` đo phần của invocation; không mặc định chờ Future hoàn tất.
- `whenComplete(...)` của `CompletionStage`/`CompletableFuture`: được đăng ký để quan sát thời điểm kết quả cuối hoàn tất, bên ngoài lifecycle trực tiếp của method invocation.

Nếu một phương thức trả Future bình thường, nói “`@AfterReturning` đã chạy nên tác vụ thành công” là **sai**. Bài này tập trung riêng vào **Spring AOP và Spring `@Async`**, không trình bày lại Java concurrency tổng quát hoặc AspectJ bytecode weaving.

## 2. Timeline: thời điểm return và completion

~~~text
Thời gian →
Caller               Spring AOP proxy / target                  Async work
  |                            |                                    |
  +-- gọi service.start() ---->| @Around trước proceed()             |
  |                            | target.start()                      |
  |                            | trả CompletableFuture chưa xong     |
  |                            | @AfterReturning thấy Future handle  |
  |                            | @Around sau proceed()               |
  |                            | (có thể gắn whenComplete callback)  |
  |<-- nhận Future ------------+                                    |
  |                                                                 |
  |              [Future chưa xong: task tiếp tục]                   |
  |                                                                 |
  |                                              complete(value)     |
  |                                              hoặc error          |
  |                                             -> callback chạy     |
  +---------------------------------------------------------------   |
~~~

Có **hai thước đo**:

| Metric | Bắt đầu và kết thúc | Trả lời câu hỏi |
| --- | --- | --- |
| *Method/invocation latency* | Trước `proceed()` → sau `proceed()` | Spring proxy/target mất bao lâu để trả giá trị theo vị trí aspect trong advisor chain? |
| *Future completion latency* | Bắt đầu quan sát → Future completed/failed/cancelled | Bao lâu caller mới có kết quả cuối, kể cả khoảng chờ task nếu handle bao gồm nó? |

Cả hai metric đều hợp lệ nhưng phục vụ mục đích khác nhau. Khi có Spring `@Async`, vị trí `AsyncExecutionInterceptor` so với advice đo đạc còn quyết định code nào chạy trên caller thread, code nào chạy trong executor. Vì vậy hãy đo thực tế, không mặc định một `@Around` luôn ở bên ngoài hay bên trong phần async.

## 3. @AfterReturning và @AfterThrowing không phải completion handlers

Ví dụ phương thức **ném lỗi đồng bộ**:

~~~java
CompletableFuture<String> failNow() {
    throw new IllegalStateException("thrown from method");
}
~~~

Nếu đi qua Spring AOP proxy có `@AfterThrowing` khớp join point, nó có thể nhận exception này vì lỗi thực sự được ném ra khỏi lời gọi.

Ngược lại, phương thức sau **trả về bình thường**:

~~~java
CompletableFuture<String> failLater() {
    CompletableFuture<String> future = new CompletableFuture<>();
    // Một thành phần khác completeExceptionally(...) ở thời điểm sau.
    return future;
}
~~~

Hai khối trên là fragments, không phải project chạy độc lập. Với `failLater()`, Spring AOP thấy method đã return bình thường; một exception được đặt vào Future sau đó **không thể quay ngược thời gian** để biến return thành một lời gọi đã ném lỗi.

Ngay cả:

~~~java
return CompletableFuture.failedFuture(new IllegalStateException("failed"));
~~~

vẫn là một câu lệnh **return Future**, khác với `throw`. Trạng thái exceptional của handle được quan sát bằng API Future/Stage. Có một ngoại lệ chuyên biệt trong **Spring transaction interceptor** được trình bày ở mục 8; điều đó không thay đổi hợp đồng chung của `@AfterThrowing`.

## 4. Ví dụ chạy được với Spring @Around và CompletableFuture

Ví dụ dùng một Future **chưa hoàn tất** được tạo đồng bộ rồi cho caller chủ động hoàn tất, nhờ vậy không cần `Thread.sleep()` và output có thứ tự xác định. Đây vẫn là **Spring AOP thực sự**: bean target và aspect được quản lý trong `AnnotationConfigApplicationContext`, proxy được tạo qua `@EnableAspectJAutoProxy`.

Thư mục dự án:

~~~text
future-aop-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/Demo.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'future-aop-demo'
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
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.Demo'
}
~~~

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class Demo {
    public interface WorkService {
        CompletableFuture<String> start();
    }

    public static class PendingWork implements WorkService {
        @Override
        public CompletableFuture<String> start() {
            System.out.println("target: return incomplete Future");
            return new CompletableFuture<>();
        }
    }

    @Aspect
    public static class CompletionAspect {
        @Around("execution(* demo..*.start(..))")
        public Object trace(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("advice: before proceed");
            Object returned = pjp.proceed();

            if (returned instanceof CompletableFuture<?> future) {
                System.out.println(
                    "advice: method returned; done=" + future.isDone()
                );
                future.whenComplete((value, error) -> {
                    if (error == null) {
                        System.out.println("completion: success " + value);
                    } else {
                        System.out.println(
                            "completion: failure "
                            + error.getClass().getSimpleName()
                        );
                    }
                });
            }
            return returned;
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    public static class Config {
        @Bean
        WorkService workService() {
            return new PendingWork();
        }

        @Bean
        CompletionAspect completionAspect() {
            return new CompletionAspect();
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            WorkService service = context.getBean(WorkService.class);

            CompletableFuture<String> first = service.start();
            System.out.println("caller: done=" + first.isDone());
            first.complete("OK");
            System.out.println("caller: result=" + first.join());

            CompletableFuture<String> second = service.start();
            second.completeExceptionally(
                    new IllegalStateException("boom")
            );
            try {
                second.join();
            } catch (CompletionException ex) {
                System.out.println(
                    "caller: caught " + ex.getCause().getMessage()
                );
            }
        }
    }
}
~~~

Dùng JDK 17+ và Gradle: chạy `gradle run` trong thư mục dự án, hoặc `./gradlew run`/`gradlew.bat run` nếu đã tạo Gradle Wrapper. Stdout ứng dụng (không tính các dòng build của Gradle):

~~~text
advice: before proceed
target: return incomplete Future
advice: method returned; done=false
caller: done=false
completion: success OK
caller: result=OK
advice: before proceed
target: return incomplete Future
advice: method returned; done=false
completion: failure IllegalStateException
caller: caught boom
~~~

**Đọc kết quả:**

1. `@EnableAspectJAutoProxy` đăng ký Spring AOP auto-proxy. Pointcut `execution(* demo..*.start(..))` khớp `PendingWork.start()` trong package `demo`.
2. Advice chạy phần trước/sau `proceed()` khi target **chỉ mới trả incomplete Future**; `done=false` được in hai lần trước khi tác vụ thành công.
3. `future.whenComplete(...)` là callback được gắn vào Future. `first.complete("OK")` làm callback success chạy; trong ví dụ, `complete` được gọi trực tiếp trên main thread nên callback không-Async có thể chạy ngay trong lời gọi đó.
4. Với `second`, method cũng return bình thường. Sau đó `completeExceptionally` mới làm observer báo failure. `join()` của caller ném `CompletionException` bọc nguyên nhân `IllegalStateException("boom")`.
5. Advice **trả lại original Future**. Stage phụ do `whenComplete` tạo ra chỉ phục vụ observer; caller không bị thay đổi kết quả.

Ví dụ không dùng `@Async` có chủ ý: **chỉ cần method trả Future chưa xong** là đã có hai mốc thời gian. Khi thêm executor thật, callback có thể chạy trên thread khác, nhưng sự khác biệt về semantics vẫn giữ nguyên.

## 5. Viết advice đo hai loại latency

Một `@Around` thực tế có thể phân biệt exception đồng bộ và exceptional completion:

~~~java
@Around("execution(* com.example.app..*(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    long startNanos = System.nanoTime();
    Object returned;

    try {
        returned = pjp.proceed();
    } catch (Throwable synchronousError) {
        recordSynchronousFailure(synchronousError,
                System.nanoTime() - startNanos);
        throw synchronousError;
    }

    recordMethodReturn(System.nanoTime() - startNanos);

    if (returned instanceof java.util.concurrent.CompletionStage<?> stage) {
        stage.whenComplete((value, error) -> {
            long completionNanos = System.nanoTime() - startNanos;
            recordCompletion(error, completionNanos);
        });
    }
    return returned;
}
~~~

Đây là **fragment minh họa**, giả định `recordSynchronousFailure`, `recordMethodReturn`, `recordCompletion` đã tồn tại; cần bao trong bean `@Aspect` có imports tương ứng. Trong production, snapshot operation/trace ID và metadata nhỏ trước khi method return, tránh capture `ProceedingJoinPoint` hoặc cả object request trong callback sống lâu.

Ba nhánh được tách rõ:

- `proceed()` **throw ngay** → synchronous failure, ném lại đúng lỗi.
- `proceed()` **return bình thường** → ghi nhận method-return latency; đó chưa phải success cuối.
- Return value là **`CompletionStage`** → đăng ký completion callback để biết success/failure, đo end-to-end latency.

Nếu Stage **đã hoàn tất trước khi gắn callback**, `whenComplete` có thể được gọi ngay lúc đăng ký; advice vẫn không được giả định callback luôn chạy “về sau”.

### Future thuần không có callback

`java.util.concurrent.Future` hỗ trợ `get()`, `cancel()`, `isDone()` nhưng không định nghĩa `whenComplete`. Vì vậy không cast `Future` bất kỳ sang `CompletableFuture` và không gọi `future.get()` ngay trong advice chỉ để lấy kết quả cuối: việc chờ sẽ **block thread gọi proxy**, làm mất semantics bất đồng bộ và có thể gây cạn thread/deadlock.

Khi kiểm soát API, dùng `CompletionStage`/`CompletableFuture` cho khả năng composition; nếu API chỉ có `Future`, theo dõi tại tầng `TaskExecutor`/task lifecycle hoặc provider-specific listener. Không tạo vòng polling bận trong Spring advice.

## 6. whenComplete, handle và exceptionally ảnh hưởng ra sao?

| Phương thức trên CompletionStage | Ý nghĩa | Nếu trả Stage mới cho caller |
| --- | --- | --- |
| `whenComplete((value,error)->...)` | Observe cả success/failure, thích hợp metrics/log | Stage phụ có thể bị lỗi nếu callback ném exception; không mặc định giống original handle |
| `handle((value,error)->replacement)` | Transform kết quả hoặc chuyển lỗi thành giá trị khác | Thay đổi semantics caller theo logic transform |
| `exceptionally(error->fallback)` | Recover từ lỗi | Có thể biến failure thành success |
| `whenCompleteAsync(callback, executor)` | Chạy callback theo executor chỉ định | Stage phụ được hoàn tất sau khi callback thực thi, có thể có độ trễ |

Nếu callback chỉ quan sát, đừng để nó chặn lâu, ghi log không an toàn hoặc ném exception ra ngoài. `whenComplete` không có hậu tố `Async` có thể chạy trên thread thực hiện `complete`, hoặc ngay trên thread đăng ký khi Future đã hoàn tất. Có thể dùng executor rõ ràng nếu cần tách tải quan sát, nhưng lúc đó độ trễ callback và backpressure phải được tính đến.

## 7. Spring @Async: thread và advisor ordering

Với `@Async`, **proxy trả cho caller một Future handle** đại diện cho task đã submit, trong khi method target chạy trên executor có thể trả một Future tạm để cung cấp kết quả:

~~~java
@Service
class InvoiceService {
    @Async("invoiceExecutor")
    public CompletableFuture<String> issue() {
        String id = doBusinessWork();
        return CompletableFuture.completedFuture(id);
    }
}
~~~

Đây là fragment cần `@EnableAsync`, bean executor `invoiceExecutor`, imports và implementation `doBusinessWork()`. Method phải được gọi qua Spring proxy trong `AdviceMode.PROXY`; self-invocation `this.issue()` không tự schedule.

Async interceptor có thể đứng **ngoài** hoặc **trong** custom aspect theo cấu hình. Advice nằm ngoài thường chỉ thấy caller handle và thời gian submit; advice bên trong có thể chạy trên worker. Dù đứng ở đâu, nếu cần biết lúc Future của caller hoàn tất thì đo/ghi callback trên handle phù hợp; `@Async void` cần `AsyncUncaughtExceptionHandler` để xử lý lỗi không có handle. Bài **“Quan hệ với @Async và ranh giới chuyển luồng”** kiểm chứng thứ tự advisor, ThreadLocal và transaction context ở worker.

## 8. Ghi chú quan trọng về Spring transaction 6.1+

Spring Framework **6.1+** có xử lý đặc biệt trong transaction interceptor: nếu method trả `Future`/`CompletableFuture` đã **completed exceptionally ngay tại thời điểm return**, trạng thái đó có thể kích hoạt rollback theo rollback rules.

~~~java
@Transactional
@Async
public CompletableFuture<String> save() {
    try {
        return CompletableFuture.completedFuture(doSave());
    } catch (DataAccessException ex) {
        return CompletableFuture.failedFuture(ex);
    }
}
~~~

Đây là **fragment** (cần cấu hình transaction manager, async executor, `doSave()` và imports).

Đừng suy rộng tính năng này:

- Transaction interceptor xét trạng thái Future **tại mốc method return** theo luật chuyên biệt. Nó không mặc định giữ transaction mở đến khi incomplete Future hoàn tất nhiều giây sau.
- Đây không phải hành vi tổng quát của `@AfterThrowing`.
- Khi `@Transactional` kết hợp `@Async`, ranh giới transaction còn phụ thuộc interceptor order và worker thread. Cần kiểm thử trên tổ hợp Spring/Boot đang dùng.
- Với workflow bất đồng bộ nhiều giai đoạn, thiết kế transaction/compensation theo ranh giới task thực tế thay vì giả định một transaction bọc cả Future.

## 9. Failure, cancellation, context và resource lifecycle

### Failure có thể được bọc

`CompletableFuture.join()` thường ném `CompletionException` cho lỗi, `Future.get()` ném `ExecutionException`, cancellation có thể ném `CancellationException`. Callback nhận `Throwable`; chuỗi stage có thể bọc root cause trong `CompletionException`. Log nên nhận diện đúng root cause nhưng **không thay đổi original error cho caller** khi chỉ cần instrumentation.

### Cancellation và timeout không đồng nghĩa worker đã dừng

`future.cancel(...)` đánh dấu trạng thái hủy theo contract của implementation, không chứng minh task nền đã ngừng. Timeout có thể làm Future fail nhưng tác vụ vẫn đang chạy. Metric và cleanup nên phân biệt `success`, `failure`, `cancel`, `timeout` và tránh đếm một request nhiều lần.

### Context propagation

Completion callback có thể chạy trên thread khác thread ban đầu của Spring AOP invocation. `MDC`, `SecurityContext`, transaction synchronization hay `ThreadLocal` không tự động theo callback. Nếu cần correlation, snapshot metadata cần thiết và dùng context-propagation của hệ thống task đã cấu hình; không giữ cả request hoặc dữ liệu nhạy cảm trong closure dài hạn.

### Đóng resource đúng lúc

~~~java
// Fragment sai nếu async task vẫn cần resource sau khi method trả handle.
@Around("execution(* com.example..*.start(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    Resource resource = openResource();
    try {
        return pjp.proceed();
    } finally {
        resource.close();
    }
}
~~~

Ở đây `finally` chạy khi invocation trả Future, có thể **đóng resource quá sớm** nếu task còn dùng nó. Nhưng nếu resource chỉ phục vụ giai đoạn synchronous thì cleanup trong finally là đúng. Quyết định phụ thuộc ai sở hữu resource và **lifetime thực**, không phải loại annotation. Với resource thread-bound (ví dụ transaction connection), không tự chuyển cleanup sang một callback chạy trên thread ngẫu nhiên.

## 10. Kiểm thử và các lỗi phổ biến

| Kịch bản | Kết quả đúng cần kiểm chứng |
| --- | --- |
| Method trả incomplete Future | Advice sau `proceed()` chạy trong khi Future chưa complete |
| Method ném exception trực tiếp | `@AfterThrowing`/synchronous catch nhìn thấy lỗi |
| Future exceptional sau return | Callback completion thấy lỗi; method vẫn đã return bình thường |
| Return `completedFuture`/`failedFuture` | Callback đăng ký sau có thể chạy ngay |
| `Future` thuần | Không giả định nó có `whenComplete` |
| `@Async` + aspect | Đo thread, thứ tự advisor, handle từ proxy |
| `@Transactional` + Future | Kiểm tra rule rollback và trạng thái lúc return |
| Cancellation/timeout | Không đồng nhất với worker chắc chắn đã dừng |
| Callback gặp error | Không vô tình thay original Future outcome |
| Nhiều request song song | Completion metrics thread-safe, không đếm trùng |
| Context propagation | Trace/correlation vẫn đúng trên worker thread |
| Resource cần cho task | Không cleanup quá sớm |
| Self-invocation | Không kỳ vọng Spring AOP proxy tự chặn lời gọi `this.method()` |

Những sai lầm cần tránh:

1. Ghi “job success” ngay trong `@AfterReturning` dù mới trả Future.
2. Dùng `@AfterThrowing` để mong bắt mọi `completeExceptionally` sau return.
3. Gọi blocking `get()` trong `@Around` chỉ để đo end-to-end latency.
4. Trả Stage đã `handle`/`exceptionally` biến đổi dù chỉ định ghi metrics.
5. Coi callback luôn được chạy async hoặc chạy trên thread caller.
6. Đóng tài nguyên trong `@After` khi công việc async còn sử dụng.
7. Giả định `@Async` và `@Transactional` cùng đi qua một thread và transaction luôn sống đến Future completion.
8. Dùng `Thread.sleep` để test timing thay vì Future/latch do test kiểm soát.

**Kết luận:** Spring AOP bao quanh **invocation của một phương thức**; Future biểu diễn **vòng đời kết quả riêng**. Advice thấy method return chưa đồng nghĩa async work thành công. Khi cần quan sát outcome cuối, dùng `CompletionStage` callback hoặc instrumentation ở tầng `TaskExecutor`/async runtime thích hợp, đồng thời giữ đúng ngữ nghĩa proxy, context và lifecycle tài nguyên.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — @Async Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html)
- [Spring Framework 6.2 — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2.14 — @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/Async.html)

Khi nâng Spring Framework/Boot, kiểm tra lại advisor ordering, callback execution thread, proxy Future handle và transaction boundary bằng integration test với phiên bản dự án thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-007">Advice quanh phương thức reactive khác xử lý lúc subscribe</a>

<details>
<summary>Xem chi tiết</summary>

# Advice quanh phương thức reactive khác xử lý lúc subscribe trong Spring AOP

## 1. Vì sao thời điểm advice và thời điểm xử lý reactive có thể khác nhau?

Một phương thức của Spring bean có thể trả về `Mono<T>` hoặc `Flux<T>`. Khi caller gọi phương thức ấy **qua Spring AOP proxy**, Spring AOP có cơ hội chạy advice **ngay tại lời gọi Java**, tức lúc phương thức được thực thi để trả về một đối tượng `Publisher`. Tuy nhiên, trong một pipeline Reactor thông thường, công việc được mô tả bởi publisher chỉ bắt đầu **khi có subscription**.

Điều này tạo ra hai lớp thời điểm khác nhau:

- **Method invocation / assembly**: caller gọi method qua proxy; `@Before`, `@Around` và `@AfterReturning` có thể chạy; target tạo và trả về `Mono`/`Flux`. Việc *tạo pipeline* có thể có một số thao tác đồng bộ.
- **Subscription / signal processing**: một subscriber đăng ký; Reactor kích hoạt publisher và phát các tín hiệu `onSubscribe`, `onNext`, `onError`, `onComplete` theo hợp đồng của nguồn. Những operator như `doOnSubscribe`, `doOnNext`, `doOnError` hoặc `doFinally` quan sát hoạt động này.

**Spring AOP proxy không tự động biến tất cả advice thành subscription hooks.** Một `@Before` trên phương thức trả về `Mono` không mặc nhiên chạy lại cho từng subscriber. Một `@AfterReturning` nhận **publisher được trả về**, không đợi publisher phát kết quả thành công. Đây là khác biệt cốt lõi khi tích hợp Spring Aspect với WebFlux/Reactor.

~~~text
Caller
  │
  ├── service.fetch("A") qua Spring AOP proxy
  │        ├── @Before / phần đầu @Around
  │        ├── target tạo Mono/Flux rồi return
  │        ├── @AfterReturning / phần sau proceed() của @Around
  │        └── trả Publisher cho caller
  │
  ├── chưa subscribe → pipeline cold thường chưa thực hiện tác vụ
  │
  ├── subscriber 1 subscribe
  │        ├── doOnSubscribe / Mono.defer(...)
  │        ├── công việc bất đồng bộ hoặc đồng bộ trong pipeline
  │        └── onNext/onError/onComplete và doFinally
  │
  └── subscriber 2 subscribe cùng Publisher
           └── có thể chạy lại pipeline (cold publisher)
                nhưng Spring @Before ở lần gọi method trước đó không chạy lại
~~~

Sơ đồ nói về **trường hợp phổ biến: method được gọi một lần, trả về một cold publisher, rồi có nhiều subscription**. Với hot publisher, `cache()`, memoization hoặc code thực hiện side effect ngay khi tạo pipeline, hành vi phát sinh công việc có thể khác. Không đồng nhất “lazy” với “mọi dòng Java trong method đều đợi subscribe”.

## 2. Từng loại advice thực sự quan sát điều gì?

Giả sử `findInvoice(...)` trả về `Mono<Invoice>` và được một Spring aspect match bằng pointcut `execution(...)`.

| Thành phần | Thời điểm điển hình | Quan sát được |
| --- | --- | --- |
| `@Before` | Trước lời gọi method target qua proxy | Arguments và ngữ cảnh tại **method invocation** |
| `@Around` trước `pjp.proceed()` | Lúc method được gọi qua proxy | Thời điểm bắt đầu gọi method Java |
| `@Around` ngay sau `pjp.proceed()` | Khi target trả về `Publisher` | Thời gian tạo/trả pipeline, **không phải** thời gian xử lý toàn pipeline |
| `@AfterReturning` | Sau khi target method trả bình thường | `Mono`/`Flux` object; chưa chắc có giá trị hoặc completion |
| `@AfterThrowing` | Method Java ném exception đồng bộ | Exception từ lời gọi method, không tự động bắt `onError` về sau |
| `@After` | Kết thúc join point đồng bộ (trả về hoặc ném) | Không đồng nghĩa reactive stream đã hoàn tất |
| `Mono.defer(...)` / `Flux.defer(...)` | Mỗi lần subscriber đi vào publisher được bọc | Có thể tạo logic riêng cho mỗi subscription |
| Reactor `doOnSubscribe` | Trong tiến trình thiết lập subscription | Subscription hook |
| Reactor `doOnNext`, `doOnError` | Khi nhận signal tương ứng | Value hoặc error phát ra trong pipeline |
| Reactor `doFinally` | Sau terminal/cancel signal đã đi về downstream | `ON_COMPLETE`, `ON_ERROR` hoặc `CANCEL` |

**Hai ví dụ về lỗi suy luận:**

~~~java
@Before("execution(reactor.core.publisher.Mono demo.reactive.GreetingService.greet(..))")
public void beforeMethod() {
    System.out.println("Spring AOP: method is being invoked");
}
~~~

Nếu caller gọi `greet("Lan")` một lần rồi subscribe hai lần vào cùng `Mono`, advice này **thông thường chỉ chạy một lần** vì chỉ có một method invocation qua proxy. Đây là snippet thêm vào một Spring `@Aspect` đã đăng ký, không phải class chạy độc lập.

~~~java
@AfterReturning(
    pointcut = "execution(reactor.core.publisher.Mono demo.reactive.GreetingService.greet(..))",
    returning = "result"
)
public void afterMethod(Object result) {
    System.out.println("Spring AOP: returned " + result.getClass().getSimpleName());
}
~~~

Callback trên chạy khi **target trả về Mono object**, ngay cả nếu Mono chưa được subscribe hoặc sau đó phát `onError`. Không dùng `@AfterReturning` như chỉ báo “request xử lý thành công”.

## 3. Ví dụ chạy được: một method invocation, hai lần subscription

Ví dụ sử dụng **Spring Framework 6.1.x + Reactor Core**, không cần chạy web server. Aspect `@Around` ghi một log ở thời điểm gọi method, rồi trả lại một `Mono` bọc bằng `Mono.defer(...)` để ghi log tại mỗi lần subscribe.

Cấu trúc project:

~~~text
build.gradle
src/main/java/demo/reactive/
    ReactiveAdviceDemo.java
    GreetingService.java
    ReactiveTraceAspect.java
~~~

### 3.1. Target bean xây dựng publisher

**`src/main/java/demo/reactive/GreetingService.java`**

~~~java
package demo.reactive;

import reactor.core.publisher.Mono;

public class GreetingService {

    public Mono<String> greet(String name) {
        System.out.println("target: assemble");
        return Mono.fromSupplier(() -> {
            System.out.println("target: execute");
            return "Hello, " + name;
        });
    }
}
~~~

Dòng `target: assemble` chạy **lúc method được gọi**. Hàm supplier của `Mono.fromSupplier(...)` được thực thi khi Mono nhận subscription; với một cold source như ví dụ, có thể chạy lại cho mỗi subscriber. Vì vậy, hai dòng log không cùng thời điểm.

### 3.2. Spring Aspect bọc Mono đúng thời điểm

**`src/main/java/demo/reactive/ReactiveTraceAspect.java`**

~~~java
package demo.reactive;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import reactor.core.publisher.Mono;

@Aspect
public class ReactiveTraceAspect {

    @Around("execution(reactor.core.publisher.Mono demo.reactive.GreetingService.greet(..))")
    public Object aroundGreet(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("aspect: method invocation");

        @SuppressWarnings("unchecked")
        Mono<String> source = (Mono<String>) pjp.proceed();

        return Mono.defer(() -> {
            System.out.println("aspect: new subscription");
            return source.doOnNext(value ->
                System.out.println("aspect: onNext " + value));
        });
    }
}
~~~

Giải thích chi tiết:
1. `@Around` chỉ chạy khi có method invocation **qua Spring proxy**. Nó gọi `pjp.proceed()` đúng một lần trong mỗi lần invocation.
2. `pjp.proceed()` gọi target và nhận về một `Mono<String>`; target chỉ mới tạo pipeline.
3. Spring Aspect trả về `Mono.defer(...)` thay cho publisher gốc. **Hợp đồng trả về vẫn là `Mono<String>`**, không phá kiểu dữ liệu của caller.
4. Lambda của `Mono.defer` chạy khi có subscriber, tạo mới đoạn quan sát bằng `doOnNext` cho subscription đó. Nó không tự subscribe, không `block()`, không chiếm quyền điều khiển lifecycle.
5. Bản thân `doOnNext` quan sát khi nguồn emit giá trị. Nếu Mono rỗng, hook này không có giá trị để in; nếu Mono lỗi, cần `doOnError` để quan sát lỗi.

Trong ví dụ, cast sang `Mono<String>` an toàn vì pointcut cố ý giới hạn đúng method `GreetingService.greet` có chữ ký `Mono<String>`. **Đừng cast mọi `Publisher` sang `Mono<String>` trong một aspect có pointcut bao phủ nhiều kiểu trả về**.

### 3.3. Đăng ký aspect và chạy

**`src/main/java/demo/reactive/ReactiveAdviceDemo.java`**

~~~java
package demo.reactive;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ReactiveAdviceDemo {

    @Bean
    GreetingService greetingService() {
        return new GreetingService();
    }

    @Bean
    ReactiveTraceAspect reactiveTraceAspect() {
        return new ReactiveTraceAspect();
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(ReactiveAdviceDemo.class)) {

            GreetingService service = ctx.getBean(GreetingService.class);

            System.out.println("client: calling method");
            Mono<String> publisher = service.greet("Lan");

            System.out.println("client: no subscription yet");

            System.out.println("client: first result=" + publisher.block());
            System.out.println("client: second result=" + publisher.block());
        }
    }
}
~~~

Cấu hình `proxyTargetClass = true` dùng CGLIB proxy để caller lấy bean qua `GreetingService.class`. Aspect được đăng ký thành bean qua `@Bean`; cấu hình này không cần component scanning. `block()` chỉ xuất hiện trong **main demo dạng console** nhằm kích hoạt hai subscription có thứ tự dễ đọc. Trong ứng dụng WebFlux thực tế, service/controller phải trả publisher cho framework quản lý và **không được chèn `block()` vào đường xử lý non-blocking**.

### 3.4. Dependency và lệnh chạy

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
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
    implementation 'io.projectreactor:reactor-core:3.6.11'
}

application {
    mainClass = 'demo.reactive.ReactiveAdviceDemo'
}
~~~

Yêu cầu JDK 17 trở lên. Chạy trên macOS/Linux:

~~~bash
./gradlew run
~~~

Chạy trên Windows:

~~~powershell
.\gradlew.bat run
~~~

Với ví dụ đồng bộ `Mono.fromSupplier` nói trên, **các dòng do chương trình in ra** (bỏ qua log Gradle/Spring) là:

~~~text
client: calling method
aspect: method invocation
target: assemble
client: no subscription yet
aspect: new subscription
target: execute
aspect: onNext Hello, Lan
client: first result=Hello, Lan
aspect: new subscription
target: execute
aspect: onNext Hello, Lan
client: second result=Hello, Lan
~~~

Đọc output theo hai mốc:
- Lời gọi `service.greet("Lan")` diễn ra **một lần**. Vì vậy `aspect: method invocation` và `target: assemble` mỗi dòng chỉ xuất hiện một lần.
- `publisher.block()` tạo một subscription mỗi lần gọi. Vì vậy `aspect: new subscription`, `target: execute` và `aspect: onNext ...` xuất hiện **hai lần**. Lưu ý đây là behavior của nguồn cold đã chọn; đừng suy rộng thành luật mọi publisher đều thực thi lại công việc.

## 4. Làm sao dùng @Around để theo dõi toàn bộ lifecycle reactive?

Nếu mục tiêu là đo **thời gian xử lý thật**, trạng thái thành công/thất bại và cancellation, việc đặt stopwatch quanh `pjp.proceed()` thường chỉ đo **thời gian tạo publisher**:

~~~java
long start = System.nanoTime();
Object publisher = pjp.proceed();
long assemblyNanos = System.nanoTime() - start;
// Đây chủ yếu là thời gian gọi method và tạo pipeline.
return publisher;
~~~

Thay vào đó, hãy lấy publisher rồi trả một publisher **đã được thêm operator**. Ví dụ cho **method trả về `Mono<?>`**:

~~~java
@Around("execution(reactor.core.publisher.Mono demo.reactive.GreetingService.greet(..))")
public Object observeSubscription(ProceedingJoinPoint pjp) throws Throwable {
    Mono<?> source = (Mono<?>) pjp.proceed();

    return Mono.defer(() -> {
        long startedAt = System.nanoTime();

        return source
            .doOnSubscribe(subscription ->
                System.out.println("subscribed"))
            .doOnError(error ->
                System.out.println("reactive error: " + error.getClass().getSimpleName()))
            .doFinally(signal -> {
                long duration = System.nanoTime() - startedAt;
                System.out.println("finished via " + signal
                    + ", duration ns=" + duration);
            });
    });
}
~~~

Đây là **phương án thay thế method `aroundGreet` trong class aspect**, không khai báo song song cả hai method quanh cùng pointcut trong demo nếu muốn output được mô tả trước đó giữ nguyên.

Ở đây:
- `Mono.defer` tạo mốc `startedAt` **theo mỗi subscription**.
- `doOnSubscribe` xác nhận một subscription được thiết lập.
- `doOnError` quan sát tín hiệu reactive error, khác exception đồng bộ phát sinh từ `pjp.proceed()`.
- `doFinally` nhận `SignalType.ON_COMPLETE`, `ON_ERROR` hoặc `CANCEL`, phù hợp cho đo tổng thời gian đến termination/cancellation.
- `doFinally` thực hiện **sau khi terminal/cancel đã được truyền xuống downstream**. Trong code bất đồng bộ hoặc test, việc subscriber nhìn thấy complete không bảo đảm side effect của `doFinally` đã hoàn tất ngay tại dòng quan sát tiếp theo.

**Không tự gọi `subscribe()` bên trong advice** để bắt đầu tác vụ. Việc đó tạo một subscription độc lập, có thể dẫn đến thực thi hai lần, mất liên hệ cancellation, Reactor Context, backpressure và lifecycle do caller/WebFlux quản lý. Advice chỉ nên **trả publisher được bọc**.

**Không tự gọi `block()` trong advice**: nó biến đoạn xử lý thành blocking, dễ làm hỏng đường xử lý WebFlux, và nếu chạy trên event-loop có thể gây exception hoặc tắc nghẽn.

## 5. pjp.proceed() đặt ngoài hay trong Mono.defer()?

Hai thiết kế có ý nghĩa khác nhau; không nên đổi vị trí vì tưởng chỉ là tối ưu cú pháp.

### 5.1. Proceed một lần tại invocation, xử lý signal lúc subscribe

~~~java
Mono<?> source = (Mono<?>) pjp.proceed();

return Mono.defer(() -> {
    System.out.println("one subscription starts");
    return source;
});
~~~

Với **một** method invocation, `pjp.proceed()` chạy một lần để dựng source; mỗi subscription tới wrapper đăng ký lại **cùng publisher source**. Đây là lựa chọn thường hợp lý cho việc bổ sung hook như metric/log/trace mà **không thay đổi số lần gọi method target**.

Nếu source là cold `Mono.fromSupplier`, callback bên trong source vẫn có thể thực thi mỗi lần subscribe. Nếu source nóng, được cache hoặc có side effect khi tạo, việc subscribe không bảo đảm chạy lại cùng công việc.

### 5.2. Proceed được dời vào từng subscription

~~~java
return Mono.defer(() -> {
    try {
        return (Mono<?>) pjp.proceed();
    } catch (Throwable error) {
        return Mono.error(error);
    }
});
~~~

Đoạn này chỉ là **mẫu minh họa trong method `@Around` trả về Mono**. Nó làm **target method Java được gọi lại mỗi subscription**, chứ không chỉ subscribe lại publisher đã được target trả về.

Hệ quả cần cân nhắc:
- Code chạy ngay trong target method, bao gồm validation hoặc side effect đồng bộ, có thể bị lặp cho từng subscriber.
- Một số advice khác trong chuỗi AOP có thể chạy theo vị trí của chúng so với advice này; không mặc định toàn bộ advice tự động chuyển sang thời điểm subscription.
- Exception đồng bộ phát sinh khi gọi target được chuyển thành reactive `onError` ở biến thể trên, có thể làm đổi hợp đồng lỗi.
- Lifecycle của request-scoped beans, transaction, Reactor Context và thời điểm read mutable state có thể thay đổi.

Nếu bạn chỉ cần log/metric khi subscribe, **thường nên proceed một lần và bọc publisher**; chỉ defer `pjp.proceed()` khi mục tiêu là chủ đích tái tạo source/lời gọi method theo mỗi subscriber và đã chấp nhận các hệ quả.

## 6. Mono, Flux và các kiểu trả về khác

Không thể thay tùy tiện kiểu trả về của một method vì Java caller và Spring proxy có hợp đồng return type.

### 6.1. Với Mono<T>

Đối với method trả về `Mono<T>`, ví dụ chuyên biệt sau giữ lại kiểu reactive:

~~~java
Mono<?> original = (Mono<?>) pjp.proceed();
return Mono.defer(() -> original.doOnSubscribe(s -> traceStart()));
~~~

`traceStart()` là hàm ứng dụng tự triển khai, snippet trên minh họa hook, **không phải một chương trình đầy đủ**. Với Mono, 0 hoặc 1 `onNext` rồi complete là bình thường; nếu Mono là `Mono<Void>`, không có `onNext`, vì vậy đừng dùng `doOnNext` để xác định việc kết thúc.

### 6.2. Với Flux<T>

Nếu method trả về `Flux<T>`, bọc nó bằng `Flux.defer(...)`:

~~~java
Flux<?> original = (Flux<?>) pjp.proceed();
return Flux.defer(() ->
    original
        .doOnSubscribe(s -> traceStart())
        .doOnNext(value -> traceItem(value))
        .doOnError(error -> traceError(error))
        .doFinally(signal -> traceFinish(signal))
);
~~~

Đây cũng là snippet giả định các hàm `traceStart`, `traceItem`, `traceError` và `traceFinish` đã được ứng dụng định nghĩa. Một subscription tới Flux có thể phát **nhiều `onNext`**, nên đếm item không giống đếm invocation hoặc đếm subscription.

### 6.3. Với Publisher<T>, CompletionStage và adapter khác

Nếu method công bố `org.reactivestreams.Publisher<T>`, có thể cần dùng `Mono.from(...)` hoặc `Flux.from(...)` tùy hợp đồng lượng item. Tuy nhiên, **đừng luôn trả `Flux` cho một method khai báo `Mono`** hoặc luôn trả `Mono` cho một method khai báo `Flux`, vì proxy phải trả object đúng kiểu method. Nếu hệ thống có các reactive adapter khác, cần xác định kiểu mà Spring và caller mong đợi trước khi tạo một aspect bao phủ rộng.

`CompletableFuture`/`CompletionStage` không phải Reactor `Publisher`; logic hoàn thành của chúng cần callback thích hợp (`whenComplete`, `handle`) thay vì giả định `doOnSubscribe` tồn tại.

## 7. Phân biệt error đồng bộ và reactive onError

Giả sử target method có thể có một trong hai kiểu thất bại:

~~~java
public Mono<String> synchronousFailure() {
    throw new IllegalArgumentException("bad argument");
}
~~~

Khi gọi method này **qua proxy**, exception phát sinh trước khi method trả về Publisher. `@AfterThrowing` hoặc `@Around` bao quanh `pjp.proceed()` có thể quan sát nó như exception đồng bộ.

~~~java
public Mono<String> reactiveFailure() {
    return Mono.error(new IllegalStateException("upstream failed"));
}
~~~

Method này **trả về bình thường** một `Mono`. `@AfterReturning` có thể chạy tại invocation; error được phát thành `onError` khi subscribe. Không mong đợi `@AfterThrowing` của join point target tự chạy khi luồng reactive emit error về sau. Với error reactive, dùng `doOnError`/`onErrorResume` hoặc các cơ chế xử lý lỗi phù hợp ở pipeline.

Cũng cần phân biệt:
- `onComplete`: luồng kết thúc thành công, có thể **không có giá trị**.
- `onError`: luồng kết thúc bằng lỗi.
- `cancel`: subscriber hủy subscription, không nhất thiết có terminal `onComplete`/`onError` để downstream quan sát.

Do đó, để ghi nhận toàn bộ kết thúc, `doFinally` với `SignalType` hữu ích hơn chỉ dùng `doOnSuccess` hoặc `doOnNext`.

## 8. Retry, repeat và nhiều subscriber: nên đếm gì?

Giả sử cùng một publisher được:
- subscribe hai lần;
- subscribe lại do `retry(...)` sau lỗi;
- subscribe lại do `repeat(...)`;
- chia sẻ qua `cache()` hoặc hot source.

Thống kê **method invocation**, **subscription vào wrapper**, **attempt tới upstream**, **signal onNext** và **request business duy nhất** có thể là các số khác nhau.

Ví dụ đối với code:

~~~java
Mono<String> result = service.greet("Lan");
result.retry(1).subscribe();
result.subscribe();
~~~

Spring AOP `@Around` gắn vào lời gọi `greet` thường chỉ chạy **một lần**. Nhưng wrapper có thể được subscribe nhiều lần. `retry(1)` chỉ tạo lượt subscribe lại khi có lỗi; không được nói chắc rằng ví dụ trên luôn ba subscription. Với source không lỗi như ví dụ mục 3, có hai subscription.

Vị trí đặt hook trong chuỗi operator cũng quan trọng: nếu một số operator được đặt *trước* hoặc *sau* `retry`/`cache`, bạn có thể đo các attempt nguồn hoặc subscriber ngoài ở cấp khác nhau. Đặt tên metric rõ như `method.invocations`, `publisher.subscriptions`, `publisher.errors`, `publisher.cancellations` để không đánh đồng chúng.

Với cold publisher bình thường, cùng source có thể thực hiện lại tác vụ mỗi subscription. Với hot hoặc cached publisher, subscriber đến sau có thể dùng dữ liệu đã có và không kích hoạt lại toàn bộ upstream. Method advice vốn chỉ gắn với **lời gọi method qua proxy**.

## 9. Reactor Context và ngữ cảnh tại thời điểm advice

Spring AOP `@Before` chạy ở thời điểm invocation trên thread đang gọi method. Reactive pipeline có thể chuyển thread khi dùng scheduler hoặc được subscription ở ngữ cảnh khác. Vì vậy:

- `ThreadLocal` hoặc MDC đọc tại method invocation **không mặc nhiên** có mặt khi các operator chạy trên thread khác.
- Thông tin theo subscriber thường thuộc **Reactor Context**; hãy dùng `Mono.deferContextual(...)` hoặc `Flux.deferContextual(...)` khi cần đọc context cho subscription cụ thể.
- Không dựa vào việc một aspect ghi `ThreadLocal` trước `pjp.proceed()` rồi mong mọi async callback nhìn thấy nó. Nếu tích hợp logging/tracing với MDC, dùng cơ chế context propagation tương thích với framework và phiên bản dự án.

Ví dụ **chỉ để đọc trace id từ Reactor Context**, trong một advice trả `Mono<?>`:

~~~java
Mono<?> source = (Mono<?>) pjp.proceed();

return Mono.deferContextual(contextView -> {
    String traceId = contextView.getOrDefault("traceId", "unknown");
    return source.doOnSubscribe(s ->
        System.out.println("subscribe traceId=" + traceId));
});
~~~

Để đặt giá trị từ caller, có thể dùng:

~~~java
Mono<?> observed = (Mono<?>) service.greet("Lan")
    .contextWrite(context -> context.put("traceId", "req-42"));
~~~

Hai đoạn là minh họa cách **Reactor Context đi cùng subscription**; chúng không cần `block()` trong Spring WebFlux. Trong hệ thống production, tracing nên được xử lý thống nhất qua thư viện quan sát/context propagation thay vì tự tạo một chuẩn trace id tùy ý cho mọi service.

## 10. Quan hệ với Spring Reactive Transaction

`@Transactional` trên method trả reactive type có một trường hợp đặc biệt đáng nhớ. Khi cấu hình **`ReactiveTransactionManager`**, Spring transaction interceptor nhận một method trả `Publisher` và tạo pipeline quản lý giao dịch reactive; **ngữ cảnh transaction và thời điểm mở/hoàn tất giao dịch gắn với subscription**, sử dụng Reactor Context thay vì thread-bound transaction.

Điều này không có nghĩa mọi `@Aspect @Before` trong cùng ứng dụng cũng chạy lúc subscribe. Cơ chế hạ tầng transaction **chủ động bọc publisher** để thực thi transaction ở đúng lifecycle reactive. Một advice tùy chỉnh chỉ log trước `pjp.proceed()` vẫn là advice tại **method invocation**.

Sơ đồ khái quát:

~~~text
service.findById(id) qua AOP proxy
    ├─ custom @Before: có thể log ở method invocation
    ├─ transaction interceptor: tạo publisher có quản lý transaction
    └─ trả publisher

subscriber subscribe publisher
    ├─ reactive transaction bắt đầu theo pipeline
    ├─ database operation trong Reactor Context của transaction
    └─ complete/error/cancel → transaction commit/rollback/cleanup
       theo semantics của transaction manager và signal
~~~

Không nên giả định thứ tự advice/transaction advisor giống nhau trong mọi cấu hình. Nếu metric cần đo **thời gian transaction thực sự**, phải xác định nó bao quanh đoạn pipeline nào và thứ tự operator/advisor tương ứng.

**Lỗi thường gặp:** dùng `PlatformTransactionManager` và transaction dựa trên thread-bound context cho đoạn reactive thực thi chuyển thread, rồi mong nó tự lan truyền như transaction của `ReactiveTransactionManager`. Reactive data access cần transaction manager/pipeline tương thích và tham gia cùng Reactor Context.

## 11. Spring WebFlux: Controller gọi service khi nào?

Trong WebFlux, một controller/service thường trả `Mono`/`Flux` cho framework. Ví dụ (snippet trong một `@RestController` đã đăng ký):

~~~java
@GetMapping("/greetings/{name}")
public Mono<String> greeting(@PathVariable String name) {
    return greetingService.greet(name);
}
~~~

Khi handler được gọi, method `greet(name)` có thể được gọi **để tạo publisher**. Framework sau đó subscribe để xử lý HTTP response. Vì vậy:
- Spring AOP advice tại method invocation có thể được thực thi **trước thời điểm subscription**.
- Các side effect được đặt trong `Mono.defer` hoặc `doOnSubscribe` chạy khi framework tạo subscription.
- Response được stream, kết thúc lỗi hoặc bị client hủy có lifecycle khác với thời điểm method controller đã trả về Publisher.

Nếu một method trả `Mono.just(expensiveCalculation())`, phép tính trong argument `expensiveCalculation()` diễn ra **ngay lúc method được gọi**, dù `Mono.just(...)` tạo một publisher. Để trì hoãn công việc, dùng một source phù hợp như `Mono.fromSupplier(...)`, `Mono.defer(...)`, hoặc một API reactive non-blocking thực sự. Không mô tả mọi method trả Mono đều lazy hoàn toàn.

Với Spring AOP, lời gọi nội bộ `this.greet(...)` trong cùng target class bỏ qua proxy. Nếu controller/service gọi một method cùng bean bằng `this`, advice ở method đó có thể **không chạy**, bất kể subscription xảy ra lúc nào. Đây là giới hạn proxy của Spring, cần xử lý bằng ranh giới bean/service rõ ràng.

## 12. Checklist chọn kỹ thuật đúng mục tiêu

| Mục tiêu | Cơ chế phù hợp | Tránh hiểu sai |
| --- | --- | --- |
| Đếm số lần Java method được caller gọi qua proxy | `@Before` hoặc đoạn trước `pjp.proceed()` | Không đồng nghĩa số subscription |
| Đếm số subscription vào publisher trả về | Bọc bằng `Mono.defer`/`Flux.defer` hoặc hook `doOnSubscribe` ở vị trí phù hợp | Một invocation có thể dẫn đến nhiều subscription |
| Log giá trị emit | `doOnNext` | `Mono<Void>`/publisher empty không có onNext |
| Bắt error phát trong pipeline | `doOnError` | `@AfterThrowing` chỉ thấy exception đồng bộ từ join point |
| Đo completion/error/cancel | `doFinally` và `SignalType` | `@AfterReturning` không chờ reactive completion |
| Đọc context theo subscriber | `deferContextual` và Reactor Context | Không giả định `ThreadLocal` luôn theo pipeline |
| Bắt đầu reactive transaction | Spring `ReactiveTransactionManager`/`TransactionalOperator` và publisher tương thích | Không dùng logic transaction thread-bound cho pipeline bất đồng bộ |
| Trả non-blocking flow từ WebFlux | Trả publisher đã được bọc cho framework | Không `subscribe()` hoặc `block()` trong advice |

Khi review một aspect trên service reactive, hãy đặt ba câu hỏi theo thứ tự:
1. **Join point nào thực sự được gọi qua Spring proxy?** Xác nhận pointcut, visibility, cơ chế JDK/CGLIB và self-invocation.
2. **Advice chạy ở mốc invocation hay đang trả một publisher có hooks cho subscriber?** Nhìn vị trí `pjp.proceed()`, `defer`, `doOn...` và `doFinally`.
3. **Metric hoặc side effect cần tính theo method invocation, subscription, retry attempt, item hay completion/cancel?** Mỗi mục tiêu cần hook phù hợp.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Một service trả `Mono<String>`, code gọi method một lần nhưng `subscribe()` hai lần. `@Before` của Spring AOP có chắc chạy hai lần không?

Không. Với một method invocation qua proxy, `@Before` thông thường chạy một lần. Hai subscription xử lý publisher đã được tạo, không tự gọi lại Java service method.

**Câu 2.** `@AfterReturning` có đủ để khẳng định Mono thành công không?

Không. Callback này xác nhận method trả về một publisher **bình thường**; subscriber vẫn có thể gặp `onError`, complete rỗng hoặc hủy.

**Câu 3.** Muốn báo cáo thời gian từ subscription đến cancel/complete/error, nên bọc bằng gì?

Trả một `Mono`/`Flux` có các operator tại subscribe, đo start trong `defer` và kết thúc trong `doFinally`. Cần lưu ý callback `doFinally` chạy sau khi signal đã được truyền về downstream.

**Câu 4.** Đưa `pjp.proceed()` vào `Mono.defer` có hoàn toàn tương đương với gọi nó trước rồi trả `Mono.defer(() -> source)` không?

Không. Cách đầu có thể **gọi lại target method cho mỗi subscriber**, thay đổi side effect và cách exception được chuyển thành reactive error. Cách sau giữ target invocation ban đầu và chỉ subscribe vào publisher được tạo.

**Câu 5.** Vì sao transaction reactive có thể bắt đầu lúc subscribe trong khi custom `@Before` chạy sớm hơn?

Vì Spring reactive transaction interceptor chủ động xây pipeline giao dịch dựa trên `ReactiveTransactionManager`/Reactor Context. `@Before` chỉ chạy tại join point Java mà proxy intercept.

**Câu 6.** Đã có aspect thì có thể gọi `publisher.block()` ngay trong `@Around` để đợi xong rồi log không?

Không nên. Đó là blocking call, có nguy cơ phá non-blocking workflow WebFlux và tạo subscription sai lifecycle. Ghi log bằng operator rồi trả publisher.

## 14. Tài liệu tham khảo

- [Spring Framework — Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): lời gọi qua proxy và self-invocation.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): semantics của advice quanh method join point.
- [Spring Framework — Reactive Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): `ReactiveTransactionManager`, reactive return type và Reactor Context.
- [Project Reactor — Mono API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): `defer`, `doOnSubscribe`, `doOnError`, `doFinally` và `deferContextual`.
- [Project Reactor — Flux API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): hook và tín hiệu trên luồng nhiều phần tử.
- [Project Reactor — Reference Guide](https://projectreactor.io/docs/core/release/reference/): assembly, subscription, backpressure và context.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-008">Tái sử dụng cơ chế Spring có sẵn trước khi viết aspect tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# Tái sử dụng cơ chế Spring có sẵn trước khi viết aspect tương đương

## 1. Vấn đề: một @Aspect có thể giải quyết được, nhưng Spring đã có cơ chế đó

Trong ứng dụng Spring, nhiều yêu cầu cắt ngang service như mở transaction, cache kết quả, kiểm tra quyền, xác thực dữ liệu đầu vào, chạy bất đồng bộ hoặc ghi nhận metrics **đã có hạ tầng chuyên biệt trong Spring Framework và hệ sinh thái Spring**. Viết ngay một `@Aspect` với `@Around` để làm lại chức năng đó có vẻ nhanh lúc đầu, nhưng dễ bỏ sót chính sách, vòng đời, exception, đồng thời và khả năng cấu hình đã được Spring giải quyết.

Ví dụ, với một method cần cache theo mã sản phẩm:

```java
@Around("execution(* example.ProductService.find(..))")
public Object handWrittenCache(ProceedingJoinPoint pjp) throws Throwable {
    // Tự tạo key, tự kiểm tra Map, tự gọi proceed(),
    // tự giải quyết cache eviction/concurrency/exception...
    return pjp.proceed();
}
```

Đây chỉ là **đoạn giả lập điều cần tránh**, không phải một cache aspect hoàn chỉnh. Spring có `@Cacheable`, `@CacheEvict`, `@CachePut`, `CacheManager` và `CacheResolver`. Ta nên dùng abstraction này khi yêu cầu thực sự là cache kết quả method, thay vì tạo một cache aspect khác với các semantics không nhất quán.

**Nguyên tắc thiết kế trong Spring AOP:** trước khi tự viết advisor, interceptor hoặc `@Aspect`, xác định xem Spring đã có **advisor/annotation/infrastructure** cho đúng mục tiêu hay chưa. Nếu có, ưu tiên cấu hình/cắm extension point của hạ tầng ấy; chỉ viết aspect riêng khi hành vi cần thiết khác về bản chất hoặc không được hỗ trợ hợp lý.

Lưu ý: **không phải mọi tính năng Spring đều được triển khai bằng AOP proxy**. `@Transactional` và caching ở proxy mode dùng interceptor/advisor; `@Scheduled` được xử lý bằng hạ tầng scheduling, không đơn giản là một `@Around` chạy lúc client gọi method. Bài này so sánh các lựa chọn để tránh làm lại hành vi vốn đã có, chứ không đồng nhất mọi module Spring thành một cơ chế proxy.

## 2. Phân loại yêu cầu trước khi chọn @Aspect

Khi có ý định viết một custom `@Aspect`, hãy đặt câu hỏi:

1. **Mục đích thực chất là gì?** Ví dụ đảm bảo atomicity cho JDBC, cache kết quả, kiểm tra quyền người dùng, ghi số liệu hay một quy tắc nghiệp vụ riêng?
2. **Spring nào đã sở hữu chính sách này?** Spring Transaction, Spring Cache, Spring Security, Spring Validation, Task Execution, Spring Retry hoặc hệ thống observability?
3. **Yêu cầu cần thay đổi chính sách hay thay đổi cách thực thi?** Nếu chỉ cần đổi key cache, transaction propagation hoặc role expression, annotation/configuration có sẵn thường tốt hơn một custom aspect.
4. **Đường gọi có đi qua Spring proxy không?** Nếu cơ chế tích hợp dùng proxy mode, phải xét self-invocation, JDK/CGLIB và method visibility.
5. **Hành vi cần xảy ra lúc nào?** Trước target, trong transaction, sau commit, trên một thread khác, khi cache hit hay chỉ cache miss?
6. **Có extension point chuyên dụng không?** Trước khi viết advice can thiệp sâu, kiểm tra `CacheResolver`, `KeyGenerator`, `PlatformTransactionManager`, `AuthorizationManager`, `Validator`, `TaskExecutor`, handler/observation conventions hoặc event listener.

Một cơ chế built-in mang cả **contract** mà framework và các thư viện tích hợp đã hiểu. Chẳng hạn, `@Transactional` không chỉ là một lệnh begin/commit thủ công: nó quản lý transaction synchronization, propagation, rollback rules và transaction manager phù hợp.

## 3. Bảng quyết định: dùng Spring trước khi tự viết aspect

| Yêu cầu định viết bằng @Aspect | Cơ chế Spring nên xét trước | Vì sao dùng cơ chế có sẵn |
| --- | --- | --- |
| Quản lý begin/commit/rollback quanh service | `@Transactional` + `@EnableTransactionManagement` + `PlatformTransactionManager` | Hỗ trợ propagation, isolation, rollback rules, resource binding |
| Cache kết quả method hoặc xóa cache | `@Cacheable`, `@CachePut`, `@CacheEvict` + `@EnableCaching` | Có key generation, cache providers, cache resolution, eviction |
| Kiểm tra quyền gọi method | Spring Security `@PreAuthorize`, `@PostAuthorize` + `@EnableMethodSecurity` | Dựa trên SecurityContext và policy engine của Spring Security |
| Ràng buộc input/output method | Jakarta Bean Validation với Spring `@Validated` và `MethodValidationPostProcessor` (hoặc Boot auto-configuration phù hợp) | Dùng constraint, validation groups, báo lỗi có cấu trúc |
| Chuyển công việc sang executor | `@Async` + `@EnableAsync`, `TaskExecutor` | Điều khiển executor, exception handler và kiểu trả về async |
| Retry khi exception tạm thời | Spring Retry `@Retryable` + `@EnableRetry` khi sử dụng thư viện tương ứng | Cung cấp chính sách attempts, backoff, retry classification |
| Chạy công việc theo lịch | `@Scheduled` + `@EnableScheduling`/`TaskScheduler` | Hạ tầng scheduler nắm lịch kích hoạt, thay vì advice quanh request thông thường |
| Thu metrics/traces chuẩn | Micrometer Observation, instrumentation hoặc Spring Boot Actuator phù hợp | Có registry, context propagation, naming/cardinality và integration |
| Logic phải chạy chỉ sau commit | Spring `TransactionSynchronization` hoặc `@TransactionalEventListener(AFTER_COMMIT)` | Callback gắn lifecycle giao dịch thật, không đoán từ `@AfterReturning` |
| Xử lý exception trả API HTTP | `@ControllerAdvice`/`@ExceptionHandler` trong Spring MVC | Chuyển lỗi ở web boundary, giữ nguyên semantics của exception nghiệp vụ |
| Ghi nhận create/update/by của entity | Spring Data auditing nếu dùng Spring Data tương ứng | Tích hợp persistence metadata và auditing lifecycle |

**Ranh giới module:** `@Transactional` và `@Cacheable` nằm trong Spring Framework; `@PreAuthorize` là Spring Security; `@Retryable` là Spring Retry; Spring Data auditing và Boot Actuator có dependency và điều kiện kích hoạt riêng. Không phải chỉ thêm `spring-aop` là mọi annotation trong bảng tự hoạt động.

## 4. Transaction: đừng tự viết aspect begin/commit/rollback

### Cách dễ gây sai

Một `@Around` tự lấy JDBC connection, `setAutoCommit(false)`, `commit()` hoặc `rollback()` rồi gọi `pjp.proceed()` có thể bỏ qua transaction manager và resource synchronization mà các repository/JdbcTemplate sử dụng. Kết quả là transaction do aspect tạo **không nhất thiết** là transaction mà data-access layer đang tham gia.

Những vấn đề khó cần xử lý gồm `REQUIRED`, `REQUIRES_NEW`, nested/savepoint, rollback-only, checked exception, đồng bộ transaction context, thread binding và nhiều nguồn dữ liệu. Đây là logic đã được Spring Transaction Management cung cấp.

### Cách Spring khuyến nghị

```java
@Service
public class BillingService {
    @Transactional(
        propagation = Propagation.REQUIRED,
        rollbackFor = IOException.class
    )
    public void charge() throws IOException {
        // Thao tác dùng resource được Spring transaction manager quản lý.
    }
}
```

**Snippet minh họa**, cần import `org.springframework.stereotype.Service`, `org.springframework.transaction.annotation.Transactional`, `org.springframework.transaction.annotation.Propagation` và `java.io.IOException`; không phải ứng dụng hoàn chỉnh. Bật transaction management và cấu hình transaction manager đúng DataSource/JPA provider.

Spring `TransactionInterceptor` sẽ đọc annotation khi lời gọi đi qua proxy. Đây chính là một interceptor của Spring AOP trong proxy mode; việc dùng `@Transactional` có nghĩa **tái sử dụng Spring AOP đã có**, không né hoàn toàn AOP.

### Nếu cần xử lý sau commit

Không dùng:

```java
@AfterReturning("execution(* demo.BillingService.charge(..))")
public void publish() {
    // Không chắc lúc này transaction đã commit!
}
```

vì advice có thể chạy **bên trong** transaction advisor. Cần chọn event listener hoặc synchronization cho đúng phase; ví dụ Spring:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(BillingCompleted event) {
    // Chỉ khi transaction gắn với event đã commit.
}
```

Đây là snippet minh họa có type event `BillingCompleted` do ứng dụng định nghĩa; cần import `org.springframework.transaction.event.TransactionalEventListener` và `org.springframework.transaction.event.TransactionPhase`. Listener mặc định không chạy nếu không có transaction đang hoạt động, trừ khi chủ động cấu hình `fallbackExecution`. Nếu listener cần ghi dữ liệu database mới sau commit, phải xét transaction mới hoặc outbox; phase AFTER_COMMIT không tự tạo transaction mới.

## 5. Cache: thay một @Around tự làm Map bằng Spring Cache

### Vấn đề của cache aspect tự viết

Một aspect với `ConcurrentHashMap<MethodAndArguments, Object>` nghe có vẻ đủ nhưng thường phải giải quyết thêm:

- Cache key khi tham số có object mutable, equals/hashCode khác mong đợi, hoặc method overloaded.
- Phân biệt `null`, lỗi, cache misses và kết quả thành công.
- Cache eviction/invalidation khi dữ liệu bị cập nhật.
- TTL, dung lượng, serialization, multi-instance application, distributed cache.
- Tính nhất quán khi nhiều thread đồng thời gọi cùng key.
- Vị trí so với transaction advisor, authorization, validation và metrics.
- Không để cache giá trị không thuộc cùng tenant/security context.

Spring Cache đã có abstraction cho cache provider và các annotation điều khiển operation. Nó **không tự tạo TTL/distributed behavior** khi provider không hỗ trợ: chọn provider/điều kiện cấu hình thích hợp vẫn là trách nhiệm của ứng dụng.

### Spring Cache đúng trách nhiệm

```java
@Cacheable(cacheNames = "prices", key = "#p0")
public String findPrice(String productId) {
    return repository.lookupPrice(productId);
}
```

`@Cacheable` giúp Spring kiểm tra cache và chỉ gọi method thật khi chưa có kết quả phù hợp. `@CacheEvict` và `@CachePut` phục vụ invalidation/update. Cần `@EnableCaching` và `CacheManager` tương ứng (hoặc Boot auto-configuration đúng dependency).

Đừng viết thêm một `@Around` "cache thứ hai" bao quanh `@Cacheable` chỉ để xử lý key/TTL/invalidation. Hãy cân nhắc `KeyGenerator`, `CacheResolver`, `CacheManager`, custom cache implementation hoặc chính sách provider. Nếu việc cache liên quan đến dữ liệu vừa ghi trong transaction, cần xác định thời điểm invalidation và visibility; đặc biệt không coi cache write là DB commit.

## 6. Ví dụ Java 17 có thể chạy: Spring Cache + một custom aspect thật sự bổ trợ

Ví dụ cố tình **không viết custom cache aspect**. Spring quản lý cache qua `@Cacheable`; `@Aspect` riêng chỉ ghi trace ở ranh giới proxy. Nhờ `@Order(50)` và `@EnableCaching(order = 100)`, trace nằm **ngoài** cache advisor, nên ghi lại cả cache hit và cache miss.

Ứng dụng Spring Framework thuần, không cần Spring Boot hoặc database. Chỉ có bốn file Java và `pom.xml`.

### 6.1. Cấu trúc

```text
spring-cache-aspect-demo/
  pom.xml
  src/main/java/demo/
    App.java
    PriceService.java
    PriceServiceImpl.java
    TraceAspect.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-cache-aspect-demo</artifactId>
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

### 6.2. Hợp đồng service và target implementation

`src/main/java/demo/PriceService.java`:

```java
package demo;

public interface PriceService {
    String price(String sku);
}
```

`src/main/java/demo/PriceServiceImpl.java`:

```java
package demo;

import org.springframework.cache.annotation.Cacheable;

public class PriceServiceImpl implements PriceService {
    private int lookups;

    @Override
    @Cacheable(cacheNames = "prices", key = "#p0")
    public String price(String sku) {
        lookups++;
        System.out.println("compute " + sku + " #" + lookups);
        return "V-" + sku;
    }
}
```

`#p0` trong Spring Cache SpEL nghĩa là tham số đầu tiên, nên không phụ thuộc compiler có phát hành tên tham số `sku` hay không. Mỗi lần method thực sự chạy, nó in `compute`; cache hit sẽ **không** gọi method target. Counter không dùng trong production; chỉ làm bằng chứng số lần target bị gọi, và ví dụ chạy tuần tự nên không có race condition.

### 6.3. Aspect chỉ đảm nhiệm trace, không quản lý cache

`src/main/java/demo/TraceAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(50)
public class TraceAspect {
    @Around("execution(* demo.PriceService.price(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("trace-before " + pjp.getArgs()[0]);
        try {
            return pjp.proceed();
        } finally {
            System.out.println("trace-after");
        }
    }
}
```

Trace này là một concern riêng: chạy trước/sau lời gọi proxy vào service. `@Around` dùng `try/finally` để `trace-after` vẫn xảy ra khi method ném exception. Không đặt caching logic, không tự xây key hoặc cache store ở aspect.

`@Order(50)` chỉ có ý nghĩa chắc chắn ở đây vì caching advisor được đặt `order = 100`. Số thấp hơn có precedence cao hơn, chạy phía ngoài khi đi vào chuỗi.

### 6.4. Cấu hình Spring và chạy

`src/main/java/demo/App.java`:

```java
package demo;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableCaching(order = 100)
    public static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("prices");
        }

        @Bean
        PriceService priceService() {
            return new PriceServiceImpl();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            PriceService service = context.getBean(PriceService.class);
            System.out.println("first=" + service.price("A"));
            System.out.println("second=" + service.price("A"));
            System.out.println("third=" + service.price("B"));
        }
    }
}
```

Chạy từ thư mục có `pom.xml`, yêu cầu JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Các dòng `System.out` do ứng dụng sinh theo thứ tự (không tính log riêng của Maven):

```text
trace-before A
compute A #1
trace-after
first=V-A
trace-before A
trace-after
second=V-A
trace-before B
compute B #2
trace-after
third=V-B
```

**Đọc output:**

1. Lần đầu gọi `price("A")`, trace chạy; cache miss nên target in `compute A #1`, kết quả `V-A` được lưu.
2. Lần thứ hai gọi `price("A")`, trace vẫn chạy, nhưng **không có** dòng `compute A #2` vì cache hit; Spring Cache trả kết quả `V-A` trực tiếp.
3. Lần gọi `price("B")` dùng cache key khác, target chạy thêm một lần: `compute B #2`.
4. Trace xuất hiện trên cả hit và miss vì aspect order 50 ở ngoài caching advisor order 100. Như vậy ta kết hợp **hạ tầng Spring làm cache** và **aspect tự viết chỉ làm trace bổ sung**.
5. `ConcurrentMapCacheManager` dùng cache bộ nhớ cho demo; không mang TTL, distributed invalidation hoặc durability. Không xem đây là cấu hình production mặc định.

Trong ví dụ này, Spring AOP có thể gom cả advisor cho `@Cacheable` và custom `@Aspect` trên một proxy của service. `PriceServiceImpl` vẫn là target; caller lấy interface `PriceService` từ Spring context và gọi **qua proxy**. Với JDK dynamic proxy, không ép kiểu `service` sang `PriceServiceImpl`.

### 6.5. Đổi thứ tự để hiểu ý nghĩa

Nếu đặt `@Order(200)` cho `TraceAspect` và giữ `@EnableCaching(order = 100)`, caching advisor nằm **ngoài** trace. Khi cache hit, caching interceptor có thể trả kết quả ngay mà không gọi `proceed()` đến trace/target; trace sẽ chỉ xuất hiện trên cache miss. Đây là hành vi cần hiểu rõ nếu viết metrics/logging: **muốn đo mọi request tới service hay chỉ việc tính toán thực sự?**

Thử đổi đúng một cấu hình, đừng tự thêm aspect cache thứ hai để sửa việc trace không xuất hiện khi hit.

## 7. Validation: đừng dựng aspect lặp lại constraint engine

Một `@Before` kiểm tra null, độ dài string, khoảng số và tự ném `IllegalArgumentException` có thể trùng khả năng của Jakarta Bean Validation và Spring Method Validation.

Ví dụ service:

```java
@Validated
public class UserService {
    public void register(@NotBlank String username,
                         @Min(18) int age) {
        // Business operation
    }
}
```

Đây là snippet minh họa; cần Jakarta Validation API, một Bean Validation provider thực tế, `@Validated` và `MethodValidationPostProcessor` hoặc cơ chế tương ứng của Spring Boot. Spring method validation ở service thường dùng proxy/advice. Ở Spring MVC 6.1+, controller còn có **built-in method validation**; không nên viết thêm một validator aspect rồi vô tình xác thực hai lần.

Nếu cần ràng buộc đặc thù, trước tiên thử **custom constraint annotation và ConstraintValidator** của Bean Validation. Khi quy tắc phụ thuộc nhiều service hoặc boundary không phù hợp với validation API, custom aspect mới có thể đáng xét, nhưng phải quyết định vị trí và error type rõ ràng.

Đừng diễn giải điều này thành "đặt `@Validated` ở đâu cũng tự chạy". Framework cần nhận diện bean, có validator và được cấu hình hỗ trợ đúng nơi; proxy mode cũng có giới hạn self-invocation.

## 8. Bảo mật: ưu tiên Spring Security Method Security

Một custom aspect kiểu:

```java
@Before("@annotation(OnlyAdmin)")
public void checkRole() {
    // Tự đọc ThreadLocal/user và tự so quyền...
}
```

dễ lệch khỏi hệ thống authentication/authorization thực tế. Nếu ứng dụng đã dùng Spring Security, `@EnableMethodSecurity` và `@PreAuthorize` thường là hướng phù hợp hơn:

```java
@PreAuthorize("hasAuthority('ORDER_APPROVE')")
public void approveOrder(long orderId) {
    // ...
}
```

Snippet minh họa cần Spring Security method security đã bật; `@PreAuthorize` không thuộc riêng dependency `spring-aop`. Hạ tầng Spring Security method authorization đã có advisor/interceptor, hỗ trợ expressions và context bảo mật.

Cần phân biệt:

- **Authorization**: Spring Security quyết định caller có được chạy method.
- **Business validation**: service kiểm tra điều kiện nghiệp vụ (ví dụ đơn hàng đã hủy không thể duyệt).
- **Audit**: ghi lại ai làm gì và kết quả, cần xử lý transaction và dữ liệu nhạy cảm cẩn thận.

Ba mối quan tâm này có thể nằm gần nhau nhưng không nhất thiết thay thế nhau. Một audit aspect tự viết **không đủ** thay thế method security.

## 9. Async, scheduling và retry: không tự đổi thread trong @Around

**Async:** yêu cầu là dispatch công việc qua executor thì chọn `@Async` / `@EnableAsync` và `TaskExecutor` đã cấu hình, thay vì tự khởi tạo thread trong `@Around`. Ví dụ `@Async("mailExecutor") CompletableFuture<Void> sendReceipt(...)` cần executor bean, method đủ điều kiện và lời gọi qua proxy. Caller transaction/thread context không mặc nhiên đi theo worker; Future completion được xử lý riêng với method return.

**Scheduling:** yêu cầu là tự kích hoạt theo lịch thì dùng `@Scheduled` / `@EnableScheduling` hoặc `TaskScheduler`. Đây là hạ tầng scheduler, không phải một custom AOP method interceptor; nhiều instance ứng dụng có thể cùng chạy job nếu chưa có điều phối riêng.

**Retry:** nếu cần thử lại transient failure, xét Spring Retry (`@Retryable`, `@EnableRetry`, dependency riêng), cấu hình exception classification, `maxAttempts` và backoff. Không viết vòng `while(true)` trong aspect để thay policy, và không coi retry là phép bảo đảm idempotency: cần thiết kế ranh giới transaction, side effect và key ổn định. Bài **“Rủi ro retry sai phạm vi giao dịch hoặc lặp tác dụng phụ”** có ví dụ DB rollback + external effect đầy đủ.

Các công cụ chuyên biệt trên có ownership khác nhau; sự tương đồng ở hành vi “chạy trước/sau/thử lại” không có nghĩa chúng cần được thay bằng cùng một custom `@Around`.

## 10. Metrics, traces và auditing: chọn vị trí tích hợp đúng

### Metrics và tracing

Một aspect đo thời gian bằng `System.nanoTime()` có thể có ích khi cần đo một operation rất riêng, nhưng nếu toàn hệ thống muốn chuẩn hóa trace/span/metrics, ưu tiên instrumentation, Micrometer Observation và Spring Boot Actuator/Observability khi có. Các giải pháp đó xử lý naming convention, tags, export, context propagation và các integration có sẵn.

Trong ứng dụng nhiều endpoint, một aspect tự gắn nhãn bằng toàn bộ URL hoặc ID người dùng có thể làm tăng **metric cardinality** rất lớn và vô tình lộ dữ liệu. Cân nhắc observation convention/handler hoặc filter chuyên dụng trước.

### Auditing

Nếu chỉ muốn các field `createdAt`, `modifiedAt`, `createdBy`, `modifiedBy` khi lưu entity Spring Data, kiểm tra Spring Data Auditing và các callback persistence tương ứng. Một Spring AOP aspect quanh repository method không luôn quan sát được chính xác thời điểm flush/commit hoặc mọi mutation của persistence context.

Nếu yêu cầu thật sự là **sự kiện nghiệp vụ đã commit thành công**, hãy kiểm tra `@TransactionalEventListener(AFTER_COMMIT)` hoặc transactional outbox. `@AfterReturning` chạy sau Java method return theo vị trí advice; không luôn có nghĩa database đã commit.

Nếu yêu cầu là **audit thất bại dù transaction rollback**, một audit write vào cùng transaction thường bị rollback theo. Hãy xác định transaction/resource độc lập hoặc cơ chế lưu thông tin lỗi theo chính sách hệ thống.

## 11. Khi nào custom @Aspect vẫn là lựa chọn tốt?

Không có nghĩa "đừng bao giờ tự viết aspect". Custom `@Aspect` phù hợp khi ứng dụng cần một **cross-cutting concern thật sự riêng**, ví dụ:

- Áp một quy tắc phân loại request nghiệp vụ theo annotation tự định nghĩa mà Spring không có abstraction tương đương.
- Thu một dấu mốc chẩn đoán riêng quanh các service có naming hoặc annotation contract rõ, không lẫn với cache/transaction.
- Bổ sung log có cấu trúc cho một tập method, sau khi kiểm tra rằng instrumentation/framework filter hiện tại chưa đáp ứng.
- Kiểm tra một invariant ở boundary gọi service mà không phù hợp hơn với Bean Validation, domain service hoặc security policy.

Tiêu chí tốt cho custom aspect:

1. **Một trách nhiệm rõ:** `@Around` không vừa cache, vừa transaction, vừa security, vừa retry.
2. **Pointcut chính xác:** giới hạn theo package, annotation hoặc interface; không bắt mọi method public vô cớ.
3. **Không phá hợp đồng phương thức:** trả đúng kiểu, forward return value, xử lý `Throwable` và `finally` cẩn thận.
4. **Có cơ chế tương tác với Spring rõ ràng:** biết aspect nằm ngoài/trong transaction/cache/security advisor; nếu order quan trọng thì chỉ định `@Order` và order hạ tầng.
5. **Có kiểm thử hành vi qua proxy:** cache hit/miss, exception/rollback, quyền cho phép/từ chối, self-invocation.
6. **Tránh state mutable trong singleton aspect:** Spring aspect bean mặc định thường singleton; cần thread-safety.
7. **Không tạo vòng gọi ngược không chủ ý:** advice có thể gọi service khác lại match pointcut và tạo recursion hoặc duplicate instrumentation.

Nếu mục đích chỉ là thay cache key, retry delay, validation message hoặc transaction rollback rule, custom aspect thường là sai tầng mở rộng.

## 12. Hai cơ chế cùng áp dụng một method: xem advisor order

Khi `@Aspect` tự viết được áp dụng cùng `@Transactional`, `@Cacheable`, `@Async` hoặc method security, chúng có thể tham gia **cùng chuỗi proxy interception**. Mỗi interceptor có thể:

- **Chạy trước target**, sau đó gọi `proceed()`.
- **Bỏ qua target có chủ đích**, ví dụ cache hit hoặc authorization deny.
- **Thay đổi luồng xử lý**, ví dụ dispatch async, retry hoặc transaction completion.
- **Ném exception** trước khi vào target hoặc khi kết thúc invocation.

Vì vậy custom aspect muốn "log tất cả lời gọi tới service" nên đặt sao cho log vẫn chạy khi một advisor khác **short-circuit**. Ngược lại, aspect muốn đo thời gian **thực sự chạy target** có thể nằm trong caching advisor.

Các annotation order liên quan:

```java
@Aspect
@Order(50)
public class TraceAspect {
    // Advice custom, ngoài advisor order=100.
}

@EnableCaching(order = 100)
@EnableTransactionManagement(order = 150)
class InfrastructureConfiguration {
    // Cấu hình minh họa, không phải project chạy độc lập.
}
```

Thứ tự nhỏ hơn nghĩa là precedence cao hơn trong Spring AOP, chạy trước khi đi vào, chạy sau khi trả về. Nhưng **không thể kết luận mọi advisor đều chỉ dựa trên @Order**: một số infrastructure như async có cách thêm proxy/interceptor khác, một số module dùng lifecycle ngoài AOP, và nhiều tầng proxy có thể làm đường đi khác. Hãy inspect advisor thực tế của bean và kiểm tra luồng chạy.

Một snippet debug trong scope có `ApplicationContext context`:

```java
Object bean = context.getBean("priceService");
System.out.println(AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
```

Snippet cần import `org.springframework.aop.Advisor`, `org.springframework.aop.framework.Advised` và `org.springframework.aop.support.AopUtils`; dùng để quan sát **proxy thực tế**, không phải một chương trình độc lập. Các advisor được in có thể gồm framework infrastructure. Nếu chương trình tự bọc proxy hoặc có context phức tạp, tiếp tục xem các tầng wrapper thay vì giả định chỉ một proxy.

## 13. Self-invocation và method visibility: giới hạn chung cần biết

Các Spring features dựa trên **proxy mode** có chung giới hạn: bean phải được Spring quản lý, caller phải gọi qua proxy, method phải intercept được. Vì thế:

~~~java
public String outer() {
    return this.findCached("A"); // không vào @Cacheable interceptor lần nữa
}
~~~

Đây là fragment nằm trong target bean có `findCached` phù hợp. Cách rõ ràng nhất là đưa method được advice sang bean cộng tác được inject rồi gọi qua proxy. JDK dynamic proxy expose interface; class proxy không override private/final methods và không proxy được final class. Không dựa vào `@PostConstruct` để thử một advice vốn cần proxy hoàn tất.

Những giới hạn này **không phải lý do viết lại transaction/cache/security bằng custom aspect**: nếu cũng là Spring AOP proxy, aspect mới sẽ gặp cùng ranh giới đó. Hãy sửa đường gọi và cấu hình của cơ chế Spring hiện có trước.

## 14. Quy trình review trước khi mở một PR có @Aspect mới

Giả sử có đề xuất: **"Viết `@Around` cho mọi method service để retry, cache và log."** Review theo thứ tự:

### Bước 1: chia từng trách nhiệm

- Retry: exception nào? tối đa mấy lần? thao tác có idempotent không?
- Cache: key, eviction, TTL và tenant isolation?
- Log: cần thấy cả cache hit hay chỉ lần target thật chạy?

Một `@Around` có cả ba trách nhiệm khó kiểm chứng hơn các policy chuyên biệt.

### Bước 2: chọn built-in và extension points

Dùng Spring Cache cho cache, framework retry thích hợp nếu có, logging/observability qua công cụ phù hợp. Mỗi cơ chế tự chịu policy của nó. Cần custom aspect cho log thì giữ aspect **chỉ làm log** như ví dụ ở phần 6.

### Bước 3: định nghĩa thứ tự và ranh giới

Làm rõ trace/log ở ngoài hay trong cache/transaction; retry ở ngoài transaction có thể tạo mỗi attempt một transaction mới, trong khi retry bên trong cùng transaction có thể không phục hồi được resource/rollback-only. Không đặt thứ tự tùy tiện.

### Bước 4: kiểm tra proxy và đường gọi

Service phải là Spring bean, invocation đi qua proxy, advisor match method. Đặc biệt test self-invocation, method visibility và các loại exception.

### Bước 5: kiểm thử sự kiện thực tế

- Cache hit có bypass target không? Cache eviction sau update có đúng không?
- Transaction rollback/commit có đúng khi exception ném từ target/advice không?
- Authorization deny có chặn method trước side effect không?
- Retry có chỉ lặp operation được phép và không tạo duplicate nghiệp vụ không?
- Metrics/logging có quan sát đúng số lượng invocation cần đo không?

Chỉ khi chưa có cơ chế Spring phù hợp hoặc extension point không thỏa requirement, mới viết advisor/aspect mới với contract độc lập, dễ review.

## 15. Các lỗi phổ biến cần tránh

| Dấu hiệu | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| `@Transactional` không rollback | Không bật transaction management, sai manager, self-invocation, exception rule | Xác minh proxy, manager, rollbackFor và đường gọi |
| `@Cacheable` vẫn chạy target mỗi lần | Không bật caching, bean gọi trực tiếp, key khác, self-invocation | Kiểm tra `@EnableCaching`, `CacheManager`, key và bean proxy |
| Custom log aspect không chạy khi cache hit | Aspect ở trong caching advisor | Xác định cần hit/miss nào rồi đặt order và kiểm thử |
| Ghi audit `@AfterReturning` nhưng DB sau đó rollback | Advice chạy trước transaction commit | Dùng transaction lifecycle callback/outbox đúng mục tiêu |
| Tự viết role-check aspect thiếu context | Trùng trách nhiệm Spring Security | Dùng method security và authorization policy |
| Aspect validation xung đột controller validation | Kiểm tra cùng dữ liệu ở nhiều tầng | Chọn đúng Spring MVC/Bean Validation boundary |
| Tự gửi `new Thread()` từ @Around | Bỏ qua executor/context propagation | Dùng `@Async`/`TaskExecutor` và quản lý async lifecycle |
| Tự retry operation và sinh bản ghi kép | Không có idempotency/policy, order với transaction sai | Dùng retry abstraction, idempotency key và transaction boundary |
| Truy vấn dữ liệu cached sai tenant | Cache key thiếu tenant/security dimension | Thiết kế key và phân vùng cache theo data isolation |
| Aspect ném exception nhưng transaction đã commit | Aspect nằm ngoài transaction và throw sau proceed | Kiểm tra advisor order, đừng nhầm post-method với pre-commit |

## 16. Checklist chọn Spring built-in trước custom aspect

1. Viết rõ **một yêu cầu** cần giải quyết: transaction, cache, auth, validation, retry, async, auditing hay custom instrumentation.
2. Đọc Spring/reference module tương ứng và xác nhận dependency cùng cơ chế kích hoạt.
3. Kiểm tra annotation và configuration có sẵn; nếu cần tùy biến thì tìm extension point chuyên dụng.
4. Xác minh feature thực sự hoạt động trên bean **được Spring quản lý** và trong đúng `ApplicationContext`.
5. Với proxy-based features, kiểm tra JDK/CGLIB, lời gọi qua proxy, self-invocation và method visibility.
6. Nếu kết hợp nhiều advisors, xác định order và behavior khi một advisor short-circuit, ném exception hoặc chuyển thread.
7. Test đường thành công và thất bại, cache hit/miss, rollback/commit, authorization deny và vòng đời async tương ứng.
8. Chỉ tạo custom `@Aspect` khi thật sự có hành vi riêng, giữ trách nhiệm nhỏ và không thay thế một infrastructure đã có.
9. Review thông tin nhạy cảm và thread-safety khi advice logging/auditing giữ state hoặc ghi parameter.
10. Tài liệu hóa lý do không dùng built-in khi PR có aspect tự triển khai một chính sách thường gặp.

## 17. Câu hỏi tự kiểm tra

**1. Vì sao không tự viết một @Around gọi commit/rollback cho mọi service?**  
Vì Spring Transaction đã cung cấp transaction manager, interceptor, propagation, resource synchronization và rollback rules; custom advice dễ tạo transaction không đồng bộ với tài nguyên thật.

**2. Vì sao trong ví dụ, trace xuất hiện hai lần cho `price("A")` nhưng `compute A` chỉ xuất hiện một lần?**  
Trace aspect order 50 chạy ngoài cache advisor order 100, còn lần gọi thứ hai cache hit trả giá trị mà không chạy target.

**3. Có phải mọi feature trong bảng đều do một Spring AOP proxy triển khai?**  
Không. Nhiều tính năng dùng proxy advisor, nhưng scheduling và các tích hợp khác có infrastructure riêng; cần xem hợp đồng của từng module.

**4. Nếu Spring Cache thiếu TTL, nên viết lại caching bằng @Aspect?**  
Thông thường không. Trước tiên chọn cache provider có TTL và cấu hình theo provider, hoặc mở rộng `CacheManager`/cache abstraction phù hợp.

**5. Khi cần ghi một sự kiện sau DB commit, `@AfterReturning` có đủ không?**  
Không. Cần Spring transaction lifecycle mechanism, chẳng hạn `@TransactionalEventListener(AFTER_COMMIT)`, hoặc transactional outbox nếu cần độ tin cậy cao.

**6. Nếu validation cần ràng buộc riêng, có phải luôn cần custom @Aspect?**  
Không. Có thể định nghĩa custom Bean Validation constraint/`ConstraintValidator` trước.

**7. Vì sao tự viết auth aspect có thể nguy hiểm hơn `@PreAuthorize`?**  
Dễ bỏ sót Spring Security context, chính sách quyền, exception handling và khả năng compose với method security.

## Tham khảo tài liệu chính thức

- Spring Framework — Spring AOP @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — AOP proxying mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Declarative transaction implementation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
- Spring Framework — Declarative caching annotations: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
- Spring Framework — Task execution and scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — MethodValidationPostProcessor Javadoc: https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/validation/beanvalidation/MethodValidationPostProcessor.html
- Spring Security — Method security: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-009">Phân biệt AOP với filter, interceptor web và callback của framework</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Spring AOP với Filter, Web Interceptor và Callback của Framework

## 1. Bốn cơ chế cùng “chạy trước/sau” nhưng ở các ranh giới khác nhau

Trong một ứng dụng Spring Web, cùng một request có thể đi qua `Filter`, `HandlerInterceptor`, method của controller/service có Spring AOP advice, rồi được xử lý bởi các callback như `ResponseBodyAdvice` hoặc `@ExceptionHandler`. Vì tất cả đều có thể ghi log trước/sau một hành động, chúng rất dễ bị nhầm là các cách tương đương để viết aspect.

**Chúng không thay thế trực tiếp cho nhau.** Trong Spring, điểm khác nhau quyết định là **đối tượng bị chặn, thời điểm hoạt động, phạm vi và hợp đồng API**:

- **Spring AOP**: advice được áp dụng cho **method execution đi qua Spring AOP proxy** của bean được quản lý. Pointcut chọn method và kiểu đối tượng; nó không tự theo toàn bộ vòng đời HTTP.
- **Servlet `Filter`**: làm việc tại ranh giới **Servlet request/response**, bao quanh `FilterChain` và servlet (thường là `DispatcherServlet` của Spring MVC). Nó có thể chạy trước khi Spring chọn controller.
- **Spring MVC `HandlerInterceptor`**: chạy trong chuỗi xử lý **handler** sau khi Spring MVC đã tìm thấy handler qua `HandlerMapping`. Nó cung cấp `preHandle`, `postHandle` và `afterCompletion`.
- **Callback khác của Spring**: thực thi tại **hook cụ thể** của framework, như khởi tạo bean (`BeanPostProcessor`), trước khi ghi HTTP response body (`ResponseBodyAdvice`), giải quyết exception (`@ExceptionHandler`), hoặc phát sinh application event (`@EventListener`). Mỗi callback có hợp đồng và lifecycle riêng.

Bài này chỉ tập trung **Spring AOP và mối quan hệ của nó với các cơ chế Spring**, không trình bày AOP nói chung hay toàn bộ Servlet API như một khóa học riêng.

## 2. Vẽ đúng đường đi của một HTTP request Spring MVC

Một trường hợp thường gặp khi sử dụng Spring Boot Servlet stack:

~~~text
Client HTTP request
      |
      v
Servlet container
      |
      v
Filter chain (Servlet Filter / Spring Security filter chain nếu có)
      |
      v
DispatcherServlet (Spring MVC)
      |
      +--> HandlerMapping chọn handler
      |
      +--> HandlerInterceptor.preHandle(...)
      |
      +--> HandlerAdapter gọi method @Controller / @RestController
      |           |
      |           +--> controller gọi service qua Spring AOP proxy
      |                     |
      |                     +--> @Around/@Before advice
      |                     +--> service method trên target
      |                     +--> @AfterReturning/@AfterThrowing...
      |
      +--> HandlerInterceptor.postHandle(...) nếu nhánh xử lý phù hợp
      |
      +--> HandlerInterceptor.afterCompletion(...) khi hoàn tất request phù hợp
      |
      v
Quay ra các Filter trong chain
      |
      v
HTTP response
~~~

Đây là **sơ đồ logic cho request đồng bộ thành công**; thứ tự chi tiết còn phụ thuộc các filter khác, error handling, asynchronous dispatch, Spring Security và loại handler. Response body cho `@ResponseBody`/`ResponseEntity` có thể được ghi **bên trong `HandlerAdapter` trước `postHandle`**, do vậy không được coi `postHandle` là hook luôn thích hợp để sửa nội dung hay header của response.

Vị trí AOP trong sơ đồ có điều kiện: chỉ xuất hiện khi lời gọi method **thực sự đi qua proxy** và pointcut match. Một method của Spring service được gọi từ scheduled task, message consumer hay command-line runner vẫn có thể chịu cùng AOP advice, dù không có HTTP request, Filter hoặc MVC interceptor nào.

## 3. Bảng so sánh ở mức Spring Framework

| Tiêu chí | Spring AOP | Servlet Filter | Spring MVC HandlerInterceptor | Framework callback |
| --- | --- | --- | --- | --- |
| Ranh giới | Spring bean method đi qua AOP proxy | Servlet request/response và filter chain | Handler được Spring MVC chọn | Hook cụ thể của component/lifecycle |
| API điển hình | `@Aspect`, `@Around`, `ProceedingJoinPoint` | `jakarta.servlet.Filter`, `OncePerRequestFilter` | `HandlerInterceptor` | `BeanPostProcessor`, `ResponseBodyAdvice`, `@EventListener`, v.v. |
| Cần HTTP request? | Không | Có | Có, trên MVC handler phù hợp | Tùy loại callback |
| Biết request/response trực tiếp? | Không mặc định; phải lấy qua ngữ cảnh nếu thật cần | Có | Có | Tùy callback |
| Chọn mục tiêu bằng gì? | Pointcut biểu diễn method/bean/type phù hợp | URL/dispatcher type/filter registration | HandlerMapping, interceptor registration, path pattern | Quy tắc của hook |
| Bao quanh method nghiệp vụ? | Có khi gọi qua proxy | Chỉ bao quanh processing request nói chung | Bao quanh xử lý handler, không tự chặn mọi service method | Không trừ hook đó thật sự cho phép |
| Chạy cho gọi service không qua HTTP? | Có thể | Không | Không | Tùy callback |
| Bắt self-invocation `this.method()`? | Không qua Spring proxy thông thường | Không phải chức năng của Filter | Không phải chức năng của interceptor | Tùy cơ chế |
| Khi nào áp dụng? | Mỗi method invocation hợp lệ | Mỗi lần đi qua filter theo dispatcher/registration | Trong lifecycle MVC request được map | Khi framework kích hoạt hook |

**Không dùng kết luận “chạy trước” để suy ra chúng tương đương:** một Filter có thể chạy trước cả `DispatcherServlet`; một `HandlerInterceptor` chỉ thấy request sau khi Spring chọn handler; Spring AOP lại chạy ở lớp service nếu controller gọi service proxy. Các chức năng khác nhau có thể xếp lồng nhau trong *một* request.

## 4. Spring AOP: chọn method của bean, không chọn URL

Spring AOP chọn **Spring bean method execution** thông qua pointcut, không chọn URL hoặc Servlet request lifecycle. Một service vẫn có thể bị advice khi được gọi từ job/message listener, dù không có web request.

~~~java
@Aspect
@Component
class ServiceTraceAspect {
    @Around("execution(* com.example.trace.GreetingService.greet(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("aop: before");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("aop: finally");
        }
    }
}
~~~

Đây là class fragment cần imports và bean registration; ví dụ đầy đủ ở mục 7 dùng một `GreetingService` thật, Filter và MVC interceptor. Khi controller gọi service **qua injected Spring proxy**, advice này chạy quanh `greet()`. Nếu gọi raw object bằng `new` hoặc `this.greet()` nội bộ, advice của lời gọi đó bị bypass. `@Order` chỉ sắp xếp precedence trong AOP advisor chain, không quyết định Filter hay MVC interceptor chạy lúc nào.

`pjp.proceed()` tiếp tục method invocation, trong khi `FilterChain.doFilter(...)` tiếp tục HTTP filter chain. Hai API có cách chuyển tiếp tương tự nhưng **phạm vi được kiểm soát khác nhau**; một custom AOP aspect không thay thế được request filter, HTTP exception resolution hoặc response conversion.

## 5. Servlet Filter: nhìn request/response trước MVC

Với Spring MVC chạy trên Servlet stack, Filter triển khai `jakarta.servlet.Filter` hoặc tiện ích Spring `OncePerRequestFilter`:

~~~java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

    long start = System.nanoTime();
    try {
        chain.doFilter(request, response);
    } finally {
        long elapsed = System.nanoTime() - start;
        System.out.println("filter ns=" + elapsed);
    }
}
~~~

Đây là **method minh họa nằm trong một class extends `OncePerRequestFilter`**, không phải file chạy riêng. Filter được container gọi theo registration/order và có thể truy cập request/response **trước khi biết handler MVC nào sẽ xử lý**. Vì thế Filter phù hợp cho việc liên quan toàn bộ HTTP exchange: cross-origin policy, encoding, headers, logging cấp request, Spring Security chain và early rejection trong phạm vi Servlet.

Các giới hạn/phân biệt:

- Filter thường áp dụng rộng theo URL pattern/dispatcher registration, kể cả tài nguyên hoặc request không match controller, trong phạm vi cấu hình.
- `chain.doFilter(...)` chuyển request sang mắt xích tiếp theo, có thể đến một servlet hoặc filter khác. Nếu không gọi nó, xử lý có thể dừng trước MVC.
- `OncePerRequestFilter` có cơ chế tránh chạy lặp trong một số dispatch và hook cấu hình cho `ASYNC`/`ERROR`; “once” **không được hiểu đơn giản là mọi vòng đời request chỉ một callback duy nhất bất kể dispatch**.
- Nếu Spring Security hiện diện, Security quản lý một filter chain riêng qua `FilterChainProxy` và có precedence cần kiểm chứng. **Không thay Spring Security bằng MVC interceptor hay service aspect để áp dụng chính sách bảo vệ request**.
- Trong một filter thông thường, không có `ProceedingJoinPoint`, pointcut method hay logic tự động match service invocation.

Filter **không bắt** mọi lời gọi Java method trong hệ thống. Một message listener hoặc job gọi `GreetingService.greet()` vẫn có thể được AOP advise nhưng không hề chạy Servlet Filter.

## 6. Spring MVC HandlerInterceptor: đã có handler cụ thể

`HandlerInterceptor` chạy trong Spring MVC sau khi `HandlerMapping` xác định request phù hợp:

~~~java
public class AuditInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        System.out.println("interceptor: pre");
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {

        System.out.println("interceptor: post");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        System.out.println("interceptor: complete");
    }
}
~~~

Đây là **class mẫu**; cần import `HttpServletRequest`/`HttpServletResponse`, `ModelAndView` và `HandlerInterceptor` từ Spring/Servlet. Muốn callback này chạy, đăng ký interceptor trong MVC qua `WebMvcConfigurer.addInterceptors(...)` hoặc cấu hình tương ứng. Chỉ khai báo một class implements interface chưa đủ nếu nó không được thêm vào handler mapping.

Ý nghĩa từng hook:
- `preHandle(...)`: trước handler; trả `false` có thể dừng chuỗi handler. Nếu interceptor ngăn handler chạy, không được mặc định các callback còn lại sẽ giống request thành công.
- `postHandle(...)`: sau khi handler adapter xử lý, trên nhánh thực thi bình thường. Với `@ResponseBody` hay `ResponseEntity`, body có thể đã được ghi/commit **trước** callback này; muốn tùy chỉnh response body tại thời điểm phù hợp, xét `ResponseBodyAdvice`.
- `afterCompletion(...)`: callback cleanup khi request tương ứng hoàn thành, dành cho interceptor có bước `preHandle` thành công. Khi có exception, tham số `ex` không nhất thiết chứa exception đã được `HandlerExceptionResolver` giải quyết; không dùng nó như nguồn toàn diện mọi lỗi.

**Async MVC:** với `Callable`, `DeferredResult` hoặc xử lý bất đồng bộ, một request có thể có nhiều dispatch và chuỗi hook thay đổi. `AsyncHandlerInterceptor.afterConcurrentHandlingStarted(...)` xử lý trường hợp request được chuyển sang chế độ async; `postHandle`/`afterCompletion` có thể không được gọi ngay trên dispatch đầu tiên, rồi chạy trong dispatch hoàn tất nếu thích hợp. Không dựa vào thứ tự tuyến tính cố định của request đồng bộ cho mọi loại async.

## 7. Ví dụ chạy được: Filter → MVC Interceptor → Controller → Spring AOP Service

Để thấy chính xác từng tầng, tạo ứng dụng **Spring Boot 3.3.x / Java 17+** với một endpoint `GET /demo`. Chúng ta đặt:

- Một `OncePerRequestFilter` cho đường dẫn `/demo`.
- Một `HandlerInterceptor` đăng ký vào Spring MVC.
- Một `@RestController` gọi Spring service.
- Một `@Aspect` chỉ match method `GreetingService.greet()`.

Các dòng log tự in sử dụng `System.out.println` để minh họa thứ tự trong **một request đồng bộ, thành công**. Trong production nên dùng structured logging/tracing với chính sách dữ liệu phù hợp.

### 7.1. Cấu trúc tệp

~~~text
build.gradle
src/main/java/com/example/trace/
    TraceApplication.java
    HttpTraceFilter.java
    RequestTraceInterceptor.java
    MvcTraceConfig.java
    GreetingController.java
    GreetingService.java
    ServiceTraceAspect.java
~~~

**`build.gradle`**:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
~~~

Plugin Boot quản lý phần lớn dependencies của web stack và AOP, bao gồm các thư viện Spring Framework/AspectJ tương thích với BOM. Chạy ví dụ với Gradle wrapper của project đã được cấu hình. Nếu ứng dụng của bạn đã dùng BOM Spring Boot, sử dụng BOM hiện tại thay vì lắp thêm các phiên bản Spring riêng rẽ.

### 7.2. Ứng dụng Spring Boot

**`src/main/java/com/example/trace/TraceApplication.java`**:

~~~java
package com.example.trace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TraceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceApplication.class, args);
    }
}
~~~

`@SpringBootApplication` component-scan những class `@Component`, `@Service`, `@RestController`, `@Configuration` trong cùng package `com.example.trace`. Starter AOP kích hoạt hạ tầng AOP thích hợp trong cấu hình Boot này.

### 7.3. Servlet Filter

**`src/main/java/com/example/trace/HttpTraceFilter.java`**:

~~~java
package com.example.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!"/demo".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("filter: before");
        try {
            filterChain.doFilter(request, response);
        } finally {
            System.out.println("filter: after");
        }
    }
}
~~~

Filter nhận request ngay trong Servlet layer. Ở ví dụ này nó chỉ in log cho `/demo`, nên các request khác có thể vẫn được chain xử lý nhưng không có hai dòng log đó. `@Order` điều khiển vị trí tương đối trong việc đăng ký filter của ứng dụng; thứ tự hiệu lực vẫn cần tính đến các filter hạ tầng.

### 7.4. MVC HandlerInterceptor

**`src/main/java/com/example/trace/RequestTraceInterceptor.java`**:

~~~java
package com.example.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class RequestTraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        System.out.println("mvc: preHandle");
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {

        System.out.println("mvc: postHandle");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        System.out.println("mvc: afterCompletion");
    }
}
~~~

Đăng ký interceptor cho đường dẫn `/demo` bằng **`src/main/java/com/example/trace/MvcTraceConfig.java`**:

~~~java
package com.example.trace;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcTraceConfig implements WebMvcConfigurer {

    private final RequestTraceInterceptor interceptor;

    public MvcTraceConfig(RequestTraceInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/demo");
    }
}
~~~

`HandlerInterceptor` được chọn theo mapping do Spring MVC điều khiển. Nó không hoạt động như một advisor cho mọi service method.

### 7.5. Controller và Service

**`src/main/java/com/example/trace/GreetingController.java`**:

~~~java
package com.example.trace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final GreetingService service;

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping("/demo")
    public String demo() {
        System.out.println("controller: handle");
        return service.greet();
    }
}
~~~

**`src/main/java/com/example/trace/GreetingService.java`**:

~~~java
package com.example.trace;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet() {
        System.out.println("service: greet");
        return "hello";
    }
}
~~~

Controller gọi bean `GreetingService` **thông qua dependency được Spring inject**. Khi service được AOP proxy, invocation từ controller đi qua advice. Đây là điều kiện đủ rõ để đặt aspect lên method service. Nếu controller tự tạo `new GreetingService()`, lời gọi sẽ không đi qua Spring AOP proxy.

### 7.6. Aspect chỉ chặn method service

**`src/main/java/com/example/trace/ServiceTraceAspect.java`**:

~~~java
package com.example.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceTraceAspect {

    @Around("execution(* com.example.trace.GreetingService.greet(..))")
    public Object aroundGreeting(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("aop: before greet");
        try {
            Object result = pjp.proceed();
            System.out.println("aop: after greet");
            return result;
        } catch (Throwable ex) {
            System.out.println("aop: greet threw");
            throw ex;
        }
    }
}
~~~

Pointcut này chỉ match `GreetingService.greet()`. Nó không chạy ở `DispatcherServlet`, không chặn filter chain và cũng không tự biết HTTP path `/demo`. Nếu một scheduled job khác gọi service **qua proxy**, aspect vẫn chạy mà không có `filter:*` hay `mvc:*`.

### 7.7. Chạy và quan sát thứ tự

Khởi động:

~~~bash
./gradlew bootRun
~~~

Với Windows:

~~~powershell
.\gradlew.bat bootRun
~~~

Gửi request trong terminal khác:

~~~bash
curl -s http://localhost:8080/demo
~~~

HTTP response body:

~~~text
hello
~~~

Với request `GET /demo` bình thường, không có async dispatch hoặc filter/handler bổ sung thay đổi control flow, **các dòng `System.out.println` của ví dụ** trong server xuất hiện theo thứ tự:

~~~text
filter: before
mvc: preHandle
controller: handle
aop: before greet
service: greet
aop: after greet
mvc: postHandle
mvc: afterCompletion
filter: after
~~~

Điều này cho thấy:
1. Filter bao quanh xử lý request ở Servlet container boundary.
2. MVC interceptor bao quanh handler được `DispatcherServlet` lựa chọn.
3. AOP aspect chỉ bao quanh invocation tới `GreetingService.greet()`.
4. Khi method service trả bình thường, chuỗi xử lý rời khỏi advice, controller/handler adapter rồi interceptor, cuối cùng quay về Filter.
5. Log startup, access log của Tomcat hoặc framework khác (nếu bật) là **các dòng bổ sung**, không nằm trong output do ứng dụng minh họa tự in.

**Điểm chính của output:** ba cơ chế xuất hiện lồng nhau cho cùng HTTP request, nhưng mỗi cơ chế sở hữu một hook riêng. Việc chúng cùng in “before/after” không làm cho pointcut Spring AOP trở thành URL filter hoặc khiến MVC interceptor chạy cho mọi service call.

## 8. Khi nào AOP chạy mà Filter/Interceptor không chạy?

Một background task chạy:

~~~java
@Component
public class GreetingJob {

    private final GreetingService service;

    public GreetingJob(GreetingService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    public void run() {
        service.greet();
    }
}
~~~

Đây là **snippet thêm vào một ứng dụng đã bật `@EnableScheduling`**, không phải file độc lập của ví dụ mục 7. Method `run()` gọi Spring-injected `GreetingService`; do đó advice trên `greet()` có thể chạy, dù lời gọi không bắt nguồn từ request HTTP.

Ngược lại, một HTTP request có thể chạy Filter mà không bao giờ gọi service có AOP:
- URL không match controller và kết thúc 404.
- Filter chặn và tự ghi response mà không gọi tiếp `chain.doFilter(...)`.
- Spring Security chặn trước controller.
- Controller trả về data tĩnh, không gọi method được aspect match.

Interceptor MVC chỉ chạy nếu request thực sự đi vào chuỗi handler mapping tương ứng, và các điều kiện `preHandle` cho phép tiến tiếp.

## 9. Callback framework: hook theo lifecycle chứ không phải mọi method

Spring có nhiều callback. Tên “interceptor” hoặc “advice” đôi khi xuất hiện trong nhiều API, nhưng lifecycle rất khác.

### 9.1. BeanPostProcessor và @PostConstruct

`BeanPostProcessor` có callback `postProcessBeforeInitialization`/`postProcessAfterInitialization` trong quá trình khởi tạo bean. `@PostConstruct` cũng thuộc initialization lifecycle. Chúng dùng để tùy biến quá trình tạo/chuẩn bị bean, **không phải** callback cho từng HTTP request hoặc từng lần gọi method service.

Spring AOP auto-proxy creator **chính nó có vai trò BeanPostProcessor** và thường tạo proxy ở `postProcessAfterInitialization`. Tuy nhiên, khi proxy đã được tạo, advice được trigger bởi **method invocation qua proxy**, không phải khi mỗi BPP callback chạy.

Không viết một BPP chỉ để log “mỗi lần người dùng gọi service method”: nó sẽ quan sát **khởi tạo bean** và cho ra metric sai đối tượng đo lường.

### 9.2. ResponseBodyAdvice

Trong Spring MVC, `ResponseBodyAdvice.beforeBodyWrite(...)` được gọi trong quá trình xử lý kết quả controller trước khi body được ghi bởi `HttpMessageConverter`. Nó thích hợp cho việc biến đổi response body ở mức framework, khác với:
- `HandlerInterceptor.postHandle(...)`: có thể chạy sau khi `@ResponseBody` đã được converter ghi.
- `@AfterReturning` của Spring AOP trên controller: quan sát **Java return value**, trước/ngoài logic serialization và HTTP converter.
- Servlet Filter: có thể truy cập `HttpServletResponse` nhưng sửa serialized body đòi hỏi response wrapping, không phải đặc tính mặc định của AOP.

Nếu muốn bao một DTO trả ra theo chuẩn API, cân nhắc `ResponseBodyAdvice`. Nếu muốn áp dụng logging/metric cho một service method dù caller là HTTP hoặc scheduler, cân nhắc Spring AOP.

### 9.3. @ExceptionHandler và @ControllerAdvice

`@ControllerAdvice` cùng `@ExceptionHandler` thuộc cơ chế **MVC exception resolution**, không đồng nghĩa `@AfterThrowing` của Spring AOP.

Ví dụ:
- Service ném exception qua proxy: một `@AfterThrowing` phù hợp có thể thấy exception từ **method join point**.
- Controller không bắt exception: `DispatcherServlet` có thể chuyển exception cho các `HandlerExceptionResolver` và method `@ExceptionHandler` phù hợp.
- Filter ném exception **trước DispatcherServlet**: không mặc định được giải quyết bởi `@ExceptionHandler` của MVC; cần thiết kế error handling tại layer phù hợp.
- Service tự bắt exception và trả fallback: `@AfterThrowing` của join point không chạy vì method đã trả bình thường.
- Reactive publisher phát `onError` sau khi method trả `Mono`: không đồng nghĩa Java method đã throw tại join point.

Dùng Spring MVC exception handling khi cần ánh xạ lỗi handler/controller sang HTTP status/body; dùng aspect khi xử lý hành vi cắt ngang của invocation; dùng Filter/security framework khi cần quyết định HTTP request sớm.

### 9.4. @EventListener, ApplicationReadyEvent và lifecycle hook

`@EventListener` chạy khi Spring phát application event; `ApplicationReadyEvent` biểu thị một giai đoạn khởi động của Spring Boot. `@Scheduled` được kích hoạt bởi scheduling infrastructure. Chúng đều là **callback do framework gọi khi điều kiện đặc thù xảy ra**.

Một callback có thể *bên trong* gọi service bean thông qua AOP proxy, khiến advice service chạy; nhưng bản thân callback không phải Servlet Filter và không diễn ra đúng một lần trên mỗi HTTP request. Tương tự, Spring AOP có thể advise method listener nếu invocation thực sự đi qua proxy và pointcut phù hợp, nhưng đó là vấn đề wiring/proxy cụ thể, không phải bảo đảm chung cho mọi callback.

## 10. Các request và lỗi: phạm vi quan sát khác nhau

| Tình huống | Filter | MVC Interceptor | Spring AOP service |
| --- | --- | --- | --- |
| Request GET `/demo` bình thường | Có nếu được đăng ký cho request | Có nếu handler/path match | Có nếu controller gọi service qua proxy và pointcut match |
| Request 404 | Có thể chạy trước khi MVC biết không tìm thấy handler | Phụ thuộc handler mapping; không bảo đảm | Không nếu không có invocation phù hợp |
| Spring Security reject | Security/filter chain xử lý request sớm | Có thể không tới MVC | Không nếu service không được gọi |
| Request không qua HTTP (scheduled/message) | Không | Không | Có thể nếu method được gọi qua proxy |
| Controller gọi `this.otherMethod()` | Filter vẫn bao quanh HTTP request | Interceptor vẫn bao quanh handler | Advice cho `otherMethod` **bị bỏ qua** bởi self-invocation nếu chỉ dựa Spring proxy |
| Method service throw đồng bộ | Filter có thể chứng kiến failure ở chain hoặc response đã được xử lý | MVC callback tùy quá trình exception resolution | `@AfterThrowing` ở method match có thể thấy throwable |
| Response body serialize lỗi | Filter có thể quan sát request/response lifecycle | Có thể tham gia callback hoàn tất | Service aspect không mặc nhiên quan sát lỗi serializer |
| Async MVC | Tùy dispatcher/async policy | Có `AsyncHandlerInterceptor` và dispatch lifecycle | Advice vẫn theo method invocation; không tự theo mọi async signal |
| WebFlux reactive server | Dùng `WebFilter`, không dùng Servlet Filter của MVC stack theo cùng mô hình | Dùng cơ chế WebFlux thích hợp, **không áp MVC HandlerInterceptor** | Spring AOP vẫn proxy-based, cần chú ý method-return vs subscribe |

**Tránh câu “Filter luôn chạy trước AOP” trong mọi trường hợp.** Nó đúng như một quan sát về request Servlet đi tới controller gọi service trong ví dụ, nhưng Spring AOP còn áp dụng cho lời gọi ngoài HTTP. Ngay trong Servlet stack, một Filter được Spring quản lý có thể gọi service proxy trực tiếp; khi đó AOP chạy **bên trong Filter**, trước khi tới MVC.

## 11. Spring WebFlux: WebFilter khác Servlet Filter và MVC Interceptor

Với Spring WebFlux **reactive stack**, API tích hợp request-level thường là `org.springframework.web.server.WebFilter`. `WebFilter.filter(ServerWebExchange, WebFilterChain)` trả về `Mono<Void>`, cho phép bọc pipeline xử lý request bất đồng bộ.

Ví dụ **snippet bên trong một class implements WebFilter**:

~~~java
@Override
public Mono<Void> filter(
        ServerWebExchange exchange,
        WebFilterChain chain) {

    return Mono.defer(() -> {
        long started = System.nanoTime();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    long elapsed = System.nanoTime() - started;
                    System.out.println("webflux: " + signal
                            + ", elapsedNs=" + elapsed);
                });
    });
}
~~~

Cần import `reactor.core.publisher.Mono`, `ServerWebExchange` và `WebFilterChain`. Đây là snippet **cho ứng dụng WebFlux**, không thêm vào dự án Spring MVC ở mục 7. Cách viết này đo lifecycle theo subscription/terminal/cancellation trong Reactor, thay vì đo thời gian Java method tạo publisher.

Với WebFlux:
- `WebFilter` xử lý request/response ở web-chain reactive; **không thay thế bằng Servlet `OncePerRequestFilter`** trong mô hình WebFlux reactive server.
- `HandlerFilterFunction` phục vụ filtering trong **functional routing** nếu ứng dụng dùng `RouterFunction`, có phạm vi khác với global `WebFilter`.
- `HandlerInterceptor` ở mục 6 là API của **Spring MVC**; không mặc định áp dụng cho WebFlux annotated controllers.
- Spring AOP trên một service trả `Mono<T>` thường được kích hoạt tại **method invocation qua proxy**. Công việc trong publisher có thể xảy ra **sau lúc subscribe**; nếu muốn đo reactive signal, hãy trả publisher đã thêm operator, không chỉ đo `pjp.proceed()`.
- Không tự gọi `subscribe()` hoặc `block()` trong aspect chỉ để ghi log, vì có thể phá lifecycle, backpressure hoặc đặc tính non-blocking.

Nếu cùng dự án có dependency Servlet và Reactive, Spring Boot thường xác định một loại web application hiệu lực theo cấu hình và classpath. Phải kiểm tra **runtime stack thực tế** trước khi chọn `Filter` hoặc `WebFilter`.

## 12. Bảo mật, transaction, caching và observability: chọn tầng phù hợp

### 12.1. Bảo mật HTTP và phân quyền method

Đối với xác thực request, CORS/security headers và security context trên **HTTP entry point**, Spring Security filter chain là lựa chọn chính. `HandlerInterceptor` không được Spring khuyến nghị như một security layer tổng quát vì có thể lệch so với quy tắc handler matching. Một custom Spring AOP aspect trên service cũng không thay thế đầy đủ security filter chain.

Nếu cần **method-level authorization**, Spring Security hỗ trợ method security (ví dụ `@PreAuthorize` qua cơ chế interceptor/advisor của nó) để kiểm soát lời gọi bean method. Chọn policy phù hợp: bảo vệ HTTP entry point và bảo vệ hành vi nghiệp vụ là hai mục tiêu có thể kết hợp.

### 12.2. Transaction

`@Transactional` trong Spring thường đi qua cơ chế proxy/advisor quanh **method invocation**. Nó có thể hoạt động cả khi service được gọi từ HTTP controller, scheduler hay message consumer. Servlet Filter và MVC interceptor không tự quản lý transaction của từng service method.

Nếu một service gọi method transactional bằng `this.inner()`, self-invocation có thể bỏ qua transactional interceptor như một lời gọi Spring AOP khác. Với reactive transaction, lifecycle giao dịch có thể gắn với **subscription** và Reactor Context thông qua `ReactiveTransactionManager`; không đánh đồng mọi `@Before` aspect với transaction reactive interceptor.

### 12.3. Metric và tracing

Chọn điểm đo theo **đơn vị cần quan sát**:
- **HTTP request latency, status, response size**: filter, WebFlux `WebFilter` hoặc cơ chế observability HTTP của Spring.
- **Handler mapping / controller handler metadata**: MVC interceptor, handler adapter hoặc bộ đo tích hợp của framework.
- **Service method execution time, argument-based logging hoặc service audit**: Spring AOP trên bean method phù hợp.
- **Serialization/response mapping**: `ResponseBodyAdvice`, message converters và HTTP response instrumentation.
- **Reactive stream completion/error/cancel**: Reactor signal hooks và observability của reactive pipeline.

Không triển khai nhiều tầng log với cùng tên metric mà không phân biệt `http.server.requests`, `mvc.handler.invocations`, `service.method.calls` hoặc `reactive.subscriptions`: một request có thể gọi nhiều service method và một service method cũng có thể chạy ngoài HTTP.

## 13. Một số lỗi thiết kế hay gặp

### 13.1. Dùng AOP để xử lý mọi URL

**Hiện tượng:** aspect match một số controller method nhưng không chạy với static resources, request bị reject từ Filter, lỗi trước handler hoặc luồng ngoài target pointcut.

**Nguyên nhân:** Spring AOP chỉ chặn **method execution trên proxy**, không sở hữu toàn bộ Servlet request lifecycle.

**Cách sửa:** những thao tác chung cho HTTP exchange nên đặt ở Filter/WebFilter hoặc cơ chế web framework phù hợp. Giữ AOP cho hành vi cắt ngang ở bean method.

### 13.2. Dùng HandlerInterceptor để sửa @ResponseBody sau handler

**Hiện tượng:** code `postHandle` cố thêm header hoặc đổi response body nhưng response đã committed.

**Nguyên nhân:** với `@ResponseBody`/`ResponseEntity`, quá trình ghi body có thể đã xảy ra trong `HandlerAdapter` trước `postHandle`.

**Cách sửa:** với trường hợp cần thao tác body trước serialization, cân nhắc `ResponseBodyAdvice`; thao tác sớm về headers/policy, cân nhắc Filter hoặc hook HTTP thích hợp.

### 13.3. Dùng @AfterThrowing như bộ xử lý tất cả lỗi HTTP

**Hiện tượng:** lỗi JSON serialization, lỗi Filter hoặc reactive `onError` không xuất hiện trong advice.

**Nguyên nhân:** `@AfterThrowing` chỉ quan sát exception **được throw bởi method execution match pointcut** trong luồng đồng bộ của join point, không phải mọi lỗi HTTP hoặc future reactive signal.

**Cách sửa:** đặt error handling theo tầng Servlet/MVC/WebFlux/Reactive tương ứng; AOP dùng để ghi nhận lỗi của method được advise.

### 13.4. Cho rằng @PostConstruct luôn chịu advice

**Hiện tượng:** gọi method trong `@PostConstruct` nhưng aspect không chạy.

**Nguyên nhân:** target bean có thể chưa được tạo AOP proxy hoàn chỉnh khi chạy initialization callback, và lời gọi `this.method()` không đi qua proxy.

**Cách sửa:** không dựa vào AOP để xử lý self-invocation/lifecycle initialization; tách logic sang bean khác hoặc hook sau initialization phù hợp.

### 13.5. Quên đăng ký MVC interceptor

**Hiện tượng:** class implements `HandlerInterceptor` và `@Component` đã có trong context nhưng callback không được gọi.

**Nguyên nhân:** bean interceptor **cần được đăng ký vào MVC HandlerMapping**, chẳng hạn `WebMvcConfigurer.addInterceptors(...)`. Có bean trong IoC chưa đồng nghĩa Spring MVC tự áp nó lên mọi handler.

### 13.6. Nghĩ @Order thống nhất toàn ứng dụng

**Hiện tượng:** thêm `@Order(1)` cho aspect, Filter, interceptor nhưng thứ tự tổng thể không như kỳ vọng.

**Nguyên nhân:** mỗi framework chain có quy trình sắp xếp riêng. `@Order` cho AOP advisor, filter registration và interceptor registration không tạo ra một hàng đợi chung. Vị trí của advice chỉ có ý nghĩa **bên trong AOP join point**; filter/interceptor chỉ có ý nghĩa trong web request chain.

### 13.7. Ghi trạng thái request trong field của aspect singleton

**Hiện tượng:** log request khác nhau bị lẫn hoặc leak dữ liệu.

**Nguyên nhân:** `@Aspect` thông thường là Spring bean singleton và phục vụ nhiều lời gọi/request; mutable field không tự cô lập theo HTTP request. Tương tự, singleton Filter/interceptor cũng phải xử lý concurrency.

**Cách sửa:** dùng local variables trong method, request attributes, security/context propagation hoặc tracing infrastructure phù hợp; không dùng field mutable chung để lưu request-specific state.

## 14. Sơ đồ chọn cơ chế theo yêu cầu

~~~text
Bạn cần hook ở đâu?
|
+-- Trước khi xác định controller / quản lý HTTP request/response?
|      |
|      +-- Servlet Spring MVC → Filter / Spring Security filter chain
|      +-- Reactive WebFlux → WebFilter
|
+-- Sau khi MVC đã chọn handler? → HandlerInterceptor
|      |
|      +-- Thay đổi body JSON trước khi serialize? → ResponseBodyAdvice
|      +-- Map exception handler ra HTTP response? → @ExceptionHandler
|
+-- Mỗi lần Spring bean method được gọi qua proxy? → Spring AOP
|      |
|      +-- Transaction hoặc method authorization? → dùng cơ chế Spring chuyên biệt
|      +-- Metric theo service method? → pointcut + advice
|      +-- Reactive signal/subscribe? → operator Reactor trong Publisher
|
+-- Bean đang khởi tạo? → BeanPostProcessor / lifecycle callback
|
+-- Application event hoặc scheduled task? → @EventListener / @Scheduled
~~~

**Nguyên tắc:** xác định **ranh giới cần kiểm soát** trước, sau đó chọn API của Spring bảo đảm gọi callback tại ranh giới đó. Không chọn công nghệ chỉ vì nó có từ “before”, “after”, “filter”, “interceptor” hoặc “advice”.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Request bị Spring Security từ chối trước `DispatcherServlet`. `HandlerInterceptor.preHandle` có chắc chạy không?

Không. Request có thể không đến MVC HandlerMapping. Filter/Security chain mới là tầng đang xử lý rejection này.

**Câu 2.** Một scheduler gọi `GreetingService.greet()` qua bean injected. Spring AOP aspect có thể chạy không?

Có. Spring AOP chỉ cần method invocation phù hợp đi qua proxy; nó không cần request HTTP.

**Câu 3.** Khi method `@RestController` trả String bình thường, `postHandle` có luôn là điểm tốt để đổi response body không?

Không. `@ResponseBody` có thể đã được message converter ghi trong `HandlerAdapter`. Dùng hook được thiết kế cho body, ví dụ `ResponseBodyAdvice`.

**Câu 4.** `@AfterThrowing` trên service có bắt được exception từ Servlet Filter ném trước controller không?

Không. Không có method join point phù hợp của service trong đường đi đó. Xử lý exception tại layer của Filter hoặc HTTP error framework.

**Câu 5.** Tại sao có `@Aspect` trên method `inner()` mà `outer()` gọi `this.inner()` không chạy advice?

Bởi self-invocation không đi qua Spring AOP proxy. Đây là giới hạn của proxy-based AOP.

**Câu 6.** Khi Spring bean được khởi tạo, `BeanPostProcessor` có phải được gọi mỗi lần client gọi HTTP endpoint không?

Không. BPP phục vụ bean creation/initialization. HTTP callback và method invocation thuộc lifecycle khác.

**Câu 7.** Với WebFlux, có nên dùng `HandlerInterceptor` của Spring MVC để theo dõi `Mono<Void>` request không?

Không. Dùng `WebFilter` hoặc công cụ WebFlux/Reactive tương ứng; Spring MVC `HandlerInterceptor` không đại diện cho WebFlux request lifecycle.

**Câu 8.** Aspect có thể dùng thay mọi Spring Security Filter để bảo vệ REST API không?

Không. Method-level guard và HTTP security entry point là các ranh giới khác nhau; cần dùng cơ chế Spring Security phù hợp để bảo đảm toàn bộ đường truy cập.

## 16. Tài liệu Spring tham khảo

- [Spring Framework — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): proxy, method invocation và self-invocation.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Before`, `@Around`, `@AfterReturning` và `@AfterThrowing`.
- [Spring MVC — Interception](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-interceptor.html): `preHandle`, `postHandle`, `afterCompletion`, `@ResponseBody` và hạn chế khi dùng cho security.
- [Spring MVC — Configuring Interceptors](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/interceptors.html): đăng ký `HandlerInterceptor` và handler matching.
- [Spring MVC — Filters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html): Servlet `Filter`, `GenericFilterBean` và `OncePerRequestFilter`.
- [Spring MVC — ResponseBodyAdvice](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html): callback trước khi response body được ghi.
- [Spring WebFlux — WebFilter](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html): web handler chain và filter reactive.
- [Spring Framework — BeanPostProcessor](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): callback tạo bean, khác với advice mỗi method invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-feature-integration-010">Thiết kế thử nghiệm để xác định chuỗi advisor thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế thử nghiệm để xác định chuỗi advisor thực tế trong Spring AOP

## 1. Mục tiêu và câu hỏi cần trả lời

Trong ứng dụng Spring, một method có thể chịu tác động của nhiều `@Aspect` và nhiều advisor do framework tạo ra (chẳng hạn transaction, cache hoặc method security, tùy cấu hình). Câu hỏi cần kiểm chứng không chỉ là “advisor nào đã được đăng ký?” mà còn là “với **bean, method, đối số và đường gọi cụ thể**, advisor nào thực sự can thiệp, theo thứ tự nào và kết thúc theo thứ tự nào?”.

Sau thử nghiệm, cần có ba loại bằng chứng:

1. **Nhận diện proxy**: bean lấy từ Spring container có phải AOP proxy không và kiểu proxy là gì.
2. **Ảnh chụp cấu hình**: thứ tự `Advisor[]` từ `Advised.getAdvisors()`, loại advisor/advice, điều kiện pointcut phù hợp với method đang khảo sát.
3. **Trace lời gọi**: dấu vết `enter → target → exit` khi gọi **qua proxy**, kèm assertion để phát hiện thứ tự thay đổi.

Phạm vi bài là **Spring AOP dựa trên proxy**. Đây là phép đo tại một điểm gọi xác định; không suy diễn rằng cùng chuỗi đó áp dụng cho tất cả method, các bean khác hoặc mọi lần gọi trong ứng dụng.

## 2. Hiểu đúng những thứ đang đo

- `@Aspect` mô tả một nhóm advice; khi Spring xử lý `@AspectJ` annotations, từng advice phù hợp có thể được biểu diễn qua advisor trong proxy.
- `Advisor` gắn advice với khả năng áp dụng (ví dụ `PointcutAdvisor`). Spring chuyển các dạng advice thích hợp thành interceptor để thực thi qua proxy.
- `Advised.getAdvisors()` trả về **danh sách advisor đã gắn vào proxy, theo thứ tự trong cấu hình proxy**. Danh sách có thể chứa advisor nội bộ phục vụ hạ tầng AOP. Một advisor có trong mảng chưa đảm bảo advice đó sẽ chạy với mọi method và mọi bộ đối số.
- Spring thường kiểm tra class filter, method matcher và có thể kiểm tra điều kiện runtime với đối số trước khi cho một interceptor tham gia lời gọi. Các advisor kiểu introduction cần được xét riêng, không nên ép vào cùng phép kiểm `PointcutAdvisor`.
- Với hai aspect độc lập được đặt `@Order(10)` và `@Order(20)`, giá trị **nhỏ hơn có precedence cao hơn**. Advice `@Around` precedence cao hơn đi vào trước và đi ra sau: `A.enter → B.enter → target → B.exit → A.exit`.
- Thứ tự log của `@After`, `@AfterReturning`, `@AfterThrowing` phải hiểu theo loại advice và kết quả thực thi; không áp dụng một quy tắc đảo thứ tự đơn giản cho mọi loại advice. Nếu hai aspect cùng precedence mà không được xác định thêm, **không được coi một thứ tự quan sát tình cờ là hợp đồng ổn định**.

**Nguyên tắc thực nghiệm:** danh sách advisor là *cấu hình ứng viên*, trace là *bằng chứng thực thi*. Cần đối chiếu cả hai trước khi kết luận.

## 3. Thiết kế phép thử có thể lặp lại

Giữ nguyên Spring context và dữ liệu đầu vào, chỉ thay đổi một biến mỗi lượt:

| Thành phần | Thiết lập gốc | Bằng chứng cần ghi |
| --- | --- | --- |
| Điểm gọi | `context.getBean(ProbeService.class).work()` | Gọi qua proxy của Spring |
| Hai aspect | `OuterAspect @Order(10)`, `InnerAspect @Order(20)` | `enter/exit` của từng aspect |
| Pointcut | Cùng match `ProbeService.work()` | Method thực sự được advice |
| Target | `ProbeService.work()` ghi `target`, trả `ok` | Điểm đến trong chuỗi |
| Công cụ quan sát | `AopUtils`, `Advised`, `Advisor`, `PointcutAdvisor` | Proxy + cấu hình advisor |
| Tiêu chí đạt | Assertion trên trace chính xác | Thất bại rõ khi thứ tự sai |

Đừng dựa vào thời gian log để suy thứ tự: các lời gọi có thể rất nhanh, logging đa luồng có thể xen kẽ, và message định dạng có thể trùng nhau. Thí nghiệm dùng **một luồng và một danh sách event có thứ tự**, không cần đo thời gian.

### 3.1. Cấu trúc dự án nhỏ

Ví dụ dùng **Java 17+**, **Spring Framework 6.2.x** và AspectJ annotations chỉ để khai báo Spring AOP proxy; không yêu cầu load-time weaving.

```text
advisor-chain-probe/
├── pom.xml
└── src/main/java/lab/AdvisorChainProbe.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>advisor-chain-probe</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.19</spring.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
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

`src/main/java/lab/AdvisorChainProbe.java`:

```java
package lab;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.Advisor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

public class AdvisorChainProbe {
    public static void main(String[] args) throws Exception {
        try (var context =
                 new AnnotationConfigApplicationContext(ProbeConfiguration.class)) {
            ProbeService service = context.getBean(ProbeService.class);
            Trace trace = context.getBean(Trace.class);

            System.out.println("isSpringProxy=" + AopUtils.isAopProxy(service));
            System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(service));

            if (!(service instanceof Advised advised)) {
                throw new AssertionError("Bean is not an Advised Spring AOP proxy");
            }

            Class<?> targetType = AopUtils.getTargetClass(service);
            Method work = ProbeService.class.getMethod("work");
            Advisor[] advisors = advised.getAdvisors();

            System.out.println("advisors=" + advisors.length);
            for (int i = 0; i < advisors.length; i++) {
                Advisor advisor = advisors[i];
                Advice advice = advisor.getAdvice();
                String applicability = "requires separate inspection";

                if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
                    ClassFilter filter =
                        pointcutAdvisor.getPointcut().getClassFilter();
                    MethodMatcher matcher =
                        pointcutAdvisor.getPointcut().getMethodMatcher();

                    boolean staticMatch = filter.matches(targetType)
                        && matcher.matches(work, targetType);

                    applicability = "staticMatch=" + staticMatch
                        + ", runtimeMatcher=" + matcher.isRuntime();
                }

                System.out.printf(
                    "#%d advisor=%s advice=%s %s%n",
                    i,
                    advisor.getClass().getSimpleName(),
                    advice.getClass().getSimpleName(),
                    applicability
                );
            }

            String result = service.work(); // QUAN TRỌNG: đi qua proxy
            List<String> expected = List.of(
                "outer.enter",
                "inner.enter",
                "target",
                "inner.exit",
                "outer.exit"
            );

            if (!"ok".equals(result) || !expected.equals(trace.events())) {
                throw new AssertionError(
                    "Unexpected result/events: " + result + " / " + trace.events()
                );
            }

            System.out.println("result=" + result);
            System.out.println("events=" + String.join(" > ", trace.events()));
            System.out.println("assertion=PASS");
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ProbeConfiguration {
    @Bean
    public Trace trace() {
        return new Trace();
    }

    @Bean
    public ProbeService probeService(Trace trace) {
        return new ProbeService(trace);
    }

    @Bean
    public OuterAspect outerAspect(Trace trace) {
        return new OuterAspect(trace);
    }

    @Bean
    public InnerAspect innerAspect(Trace trace) {
        return new InnerAspect(trace);
    }
}

class Trace {
    private final List<String> events = new ArrayList<>();

    void add(String event) {
        events.add(event);
    }

    List<String> events() {
        return List.copyOf(events);
    }
}

class ProbeService {
    private final Trace trace;

    ProbeService(Trace trace) {
        this.trace = trace;
    }

    public String work() {
        trace.add("target");
        return "ok";
    }
}

@Aspect
@Order(10)
class OuterAspect {
    private final Trace trace;

    OuterAspect(Trace trace) {
        this.trace = trace;
    }

    @Around("execution(* lab.ProbeService.work(..))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        trace.add("outer.enter");
        try {
            return joinPoint.proceed();
        } finally {
            trace.add("outer.exit");
        }
    }
}

@Aspect
@Order(20)
class InnerAspect {
    private final Trace trace;

    InnerAspect(Trace trace) {
        this.trace = trace;
    }

    @Around("execution(* lab.ProbeService.work(..))")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        trace.add("inner.enter");
        try {
            return joinPoint.proceed();
        } finally {
            trace.add("inner.exit");
        }
    }
}
```

Chú ý: `@EnableAspectJAutoProxy` kích hoạt xử lý Spring AOP, còn `proxyTargetClass = true` giúp ví dụ dùng class-based proxy rõ ràng. `@Aspect` và `@Around` không khiến toàn bộ hệ thống dùng AspectJ weaving. Bean được lấy từ context, **không tạo bằng `new ProbeService(...)` trong phương thức `main`**. Hai aspect chỉ ghi event nên không thay đổi kết quả nghiệp vụ.

### 3.2. Chạy và đọc kết quả

Từ thư mục chứa `pom.xml`:

```bash
mvn -q -DskipTests compile
mvn -q exec:java -Dexec.mainClass=lab.AdvisorChainProbe
```

Các dòng kiểm chứng được thiết kế để cho kết quả ổn định:

```text
isSpringProxy=true
isCglibProxy=true
result=ok
events=outer.enter > inner.enter > target > inner.exit > outer.exit
assertion=PASS
```

**Giữa hai dòng proxy và dòng result**, chương trình còn in `advisors=N` cùng các dòng `#0`, `#1`, … mô tả từng advisor. **Không cố định số lượng, tên lớp triển khai và vị trí tuyệt đối** của advisor hạ tầng trong ví dụ này: các chi tiết đó phụ thuộc phiên bản/cấu hình và chính là dữ liệu cần ghi nhận khi chạy. Sự xuất hiện của advisor hạ tầng không làm thay đổi assertion đã thiết kế cho event của hai aspect và target.

`staticMatch=true` chứng minh điều kiện tĩnh của `PointcutAdvisor` có thể phù hợp với `work`; nếu `runtimeMatcher=true`, muốn kết luận có áp dụng ở lời gọi cụ thể còn phải xét đối số/thời điểm chạy. Advisor không thuộc `PointcutAdvisor` cũng có thể là advisor hợp lệ: nhãn `requires separate inspection` **không có nghĩa nó bị bỏ qua**. Đừng dùng đếm advisor thay cho đếm advice đã thực thi.

## 4. Phân tích chuỗi từ bằng chứng quan sát

Xét event khẳng định được:

```text
caller
  → Spring proxy
      → OuterAspect.enter  (@Order 10)
          → InnerAspect.enter (@Order 20)
              → ProbeService.work() [target]
          ← InnerAspect.exit
      ← OuterAspect.exit
  ← caller nhận "ok"
```

`joinPoint.proceed()` cho phép advice `@Around` giao quyền cho phần còn lại của chuỗi. `finally` bảo đảm các event `exit` của ví dụ được ghi cả khi target ném exception; nhưng kết quả trả về và event nghiệp vụ sẽ khác khi lỗi xảy ra. Cần giữ riêng **thứ tự vào**, **thứ tự ra**, **kết quả** và **exception**.

Tổng quát, với danh sách `Advisor[] = [A, B, C]`, không được tự động kết luận `A, B, C` đều chạy khi gọi `work()`. Spring có thể lọc advisor theo target/method, kiểm tra điều kiện runtime, và còn có interceptor nội bộ. Trace của từng advice (hoặc instrumentation có kiểm soát) mới chỉ ra chuỗi thực thi. Khi muốn gắn advisor trong trace với từng vị trí cấu hình, đối chiếu pointcut/advice class, `@Order` và dữ liệu method cụ thể; tên đơn giản của class không phải định danh duy nhất.

## 5. Các vòng thử nghiệm đối chứng

Sau khi chạy baseline thành công, **mỗi vòng chỉ đổi một biến**, chạy lại và lưu cả bảng advisor lẫn trace.

| Biến thay đổi | Dự đoán cần kiểm tra | Ý nghĩa |
| --- | --- | --- |
| Đổi `@Order(10)` và `@Order(20)` cho nhau | Thứ tự `outer/inner` trong `events` đảo lại; assertion baseline sẽ FAIL cho đến khi cập nhật kỳ vọng | Xác nhận precedence do Spring áp dụng |
| Đổi pointcut của `InnerAspect` thành `execution(* lab.ProbeService.other(..))` (chỉ sửa biểu thức, không cần tạo `other` để so sự không khớp của `work`) | `inner.enter/exit` biến mất khỏi trace của `work` | Advisor có thể tồn tại nhưng không match method được gọi |
| Bỏ `@EnableAspectJAutoProxy` | Với context thuần của ví dụ, bean `ProbeService` không còn AOP proxy; chương trình dừng ở assertion `Advised` | Chỉ khai báo aspect không đủ để có interceptor chain |
| Gọi trực tiếp trên đối tượng tự `new` ngoài container | Không có `outer/inner` | Spring proxy không nằm trên đường gọi |
| Tạo `work()` gọi `this.innerWork()` và pointcut riêng cho `innerWork` | Advice dành cho lời gọi nội bộ `this` có thể không chạy | Self-invocation bỏ qua proxy |
| Cùng `@Order` cho cả hai aspect | Thứ tự có thể thay đổi giữa cấu hình/phiên bản, không dùng làm kỳ vọng chắc chắn | Phát hiện rủi ro precedence không rõ |

Thử nghiệm pointcut ở hàng thứ hai cần **cập nhật lại assertion** của lượt kiểm tra theo giả thuyết mới: trace mong đợi `outer.enter > target > outer.exit`. Nếu giữ assertion baseline và nó thất bại, đó là bằng chứng quan sát đúng; **không được báo thử nghiệm thất bại về mặt kỹ thuật chỉ vì giả thuyết baseline không còn đúng**.

Với self-invocation, ví dụ `this.innerWork()` xảy ra bên trong target; nếu muốn so sánh, thêm một đường gọi `context.getBean(ProbeService.class).innerWork()` trực tiếp qua proxy và giữ điểm cắt tương ứng. Method phải phù hợp với loại proxy và phạm vi Spring AOP; không dùng `private` hay `final` làm đối chứng nếu muốn bảo đảm intercept được.

## 6. Áp dụng khi ứng dụng có nhiều cơ chế Spring

Trên bean thật có `@Transactional`, caching, authorization, retry, async hay các aspect tự viết, nên lập **phiếu kết quả** cho đúng bean/method:

```text
Application/context:
Spring Framework + Spring Boot version:
Bean name / proxy class / target class:
Method signature và args:
Các @Aspect/@Order và annotation của method:
advised.getAdvisors() theo index:
  [0] class / advice class / pointcut / matches?
  [1] ...
Trace thực thi (event id + thread + request/correlation id):
Return value / thrown exception:
Dự đoán ban đầu:
Kết quả so với dự đoán:
```

Quy trình kiểm tra:

1. **Lấy bean thực từ container** (ví dụ `ApplicationContext.getBean(...)`), xác định `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy`/`isCglibProxy`, target class, và thử `bean instanceof Advised`.
2. **Ghi snapshot `getAdvisors()`** trước lời gọi, không gọi `addAdvisor`/`removeAdvisor` chỉ để debug; những thao tác đó sửa chain và làm phép đo sai lệch.
3. **Xác định method thực sự được proxy nhìn thấy** và pointcut tĩnh; chú ý interface vs implementation, bridge method, target class, method matcher cần args và advisor được tạo từ annotation của Spring.
4. **Gọi một kịch bản rõ ràng qua proxy**, dùng event/trace có correlation id; đo cả hai phía của `proceed()` và target, sau đó so sánh với snapshot. Khi có transaction, có thể kiểm tra trạng thái transaction tại vị trí cần quan sát; khi có async, phải ghi thread và phân biệt lời gọi gọi phương thức với công việc chạy trên executor.
5. **Đổi một điều kiện** (`@Order`, pointcut, bean được gọi, entry point, annotation) và chạy lại. Kết quả đo được mới quyết định chain thực tế trong trường hợp đó.

Đặc biệt với `@Transactional`, transaction advisor do Spring quản lý có thứ tự riêng; không suy ra toàn bộ thứ tự từ `@Order` của hai aspect tự tạo. Nếu có `@Async`, lời gọi có thể chuyển thread hoặc đi qua một tầng proxy khác; lúc đó phải phân biệt **chuỗi proxy ở phía caller**, **chuỗi trên worker thread** và **ranh giới transaction**. Một snapshot advisor trên một proxy không đại diện đương nhiên cho mọi proxy lồng nhau.

Khi debug ứng dụng đang chạy, log chính xác tên bean, target class, advisor index, advice class, method signature và event theo request; đừng in dữ liệu nhạy cảm của argument, token hoặc payload nghiệp vụ vào log. Với bài kiểm thử, assertion tập trung vào event xác định trước để tránh phụ thuộc tên lớp nội bộ Spring.

## 7. Các bẫy kết luận sai thường gặp

- **Nhìn `@Order` rồi đoán hết**: thứ tự phụ thuộc các advisor thực sự gắn vào proxy; infrastructure advisors, pointcuts và proxy khác cũng tham gia.
- **Thấy advisor trong mảng rồi khẳng định đã chạy**: pointcut có thể không match phương thức; runtime matcher còn cần đối số.
- **Gọi target trực tiếp hoặc qua `this`**: không đi qua proxy nên aspect không chạy; kết quả âm tính không chứng minh advisor bị thiếu.
- **Đồng nhất index advisor với số lần advice chạy**: một adapter/interceptor có thể có hành vi riêng, advice có thể gọi `proceed()` không lần nào hoặc nhiều lần, và các advisor có thể được lọc.
- **Suy diễn thứ tự từ console log đa luồng**: thread scheduling có thể làm log xen kẽ; gắn correlation id và kiểm tra đường gọi.
- **Lấy class proxy làm target class**: với CGLIB/JDK dynamic proxy, method signature và target type cần được xác định đúng.
- **Dựa vào thứ tự không xác định**: nhiều aspect cùng precedence, hoặc thay đổi cấu hình sau khởi tạo, không tạo hợp đồng thứ tự đáng tin.
- **Sửa cấu hình trong lúc đo**: test phải đóng băng cấu hình từng vòng và tái lập context khi đổi giả thuyết.

## 8. Tiêu chí hoàn thành và kết luận

Một phép thử xác định chuỗi advisor thực tế được xem là đủ bằng chứng khi: (1) xác nhận bean được gọi qua đúng Spring proxy; (2) lưu được danh sách advisor của **proxy ấy** và thông tin pointcut/target method; (3) có trace `enter/target/exit` và assertion hoặc kiểm chứng tương đương; (4) nêu rõ method, đối số, context, thread và các giới hạn; (5) tái lập được bằng một biến đối chứng.

Trong baseline của bài, trace mong đợi chứng minh `OuterAspect` bọc `InnerAspect` quanh `ProbeService.work()`. **Kết luận ấy chỉ áp dụng cho đúng fixture và lời gọi đã kiểm chứng.** Trong ứng dụng lớn, hãy làm lại cùng quy trình trên bean thật để biết advisor nào cùng tham gia, vị trí nào bị pointcut lọc, và transaction/async/security thay đổi ranh giới quan sát ra sao.

### Tài liệu Spring để đối chiếu

- [Spring Framework — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): API `Advised.getAdvisors()` và cấu hình proxy.
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): precedence của advice/`@Order` và ngữ nghĩa `@Around`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): cách proxy intercept lời gọi và giới hạn self-invocation.
- [Spring Framework — The Advisor API](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html): `Advisor`, `DefaultPointcutAdvisor`, phối hợp các loại advice.


</details>

- [Quay lại đầu trang](#back-to-top)
