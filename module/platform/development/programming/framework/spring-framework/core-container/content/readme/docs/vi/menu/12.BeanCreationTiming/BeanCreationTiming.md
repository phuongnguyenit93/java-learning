<a id="back-to-top"></a>

# Thời điểm tạo bean

## Menu
- [Singleton khởi tạo sớm và quá trình tiền khởi tạo](#bean-creation-timing-001)
- [Trì hoãn tạo bean bằng @Lazy và lazy-init](#bean-creation-timing-002)
- [Bean lazy vẫn có thể được tạo bởi phụ thuộc không lazy](#bean-creation-timing-003)
- [Proxy tại điểm tiêm @Lazy và thời điểm lỗi xuất hiện](#bean-creation-timing-004)
- [@DependsOn cho phụ thuộc khởi tạo không thể hiện bằng DI](#bean-creation-timing-005)
- [Thứ tự khởi tạo xuất phát từ đồ thị phụ thuộc](#bean-creation-timing-006)
- [Phân biệt @Order, @DependsOn và thứ tự định nghĩa](#bean-creation-timing-007)
- [Quan hệ phụ thuộc và thứ tự hủy singleton](#bean-creation-timing-008)

## <a id="bean-creation-timing-001">Singleton khởi tạo sớm và quá trình tiền khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Singleton khởi tạo sớm và quá trình tiền khởi tạo trong Spring Core Container

## 1. Mục tiêu bài học

Một trong những hành vi quan trọng nhất của Spring IoC Container là **thời điểm tạo bean**. Với `ApplicationContext`, Spring mặc định **tạo và cấu hình các singleton không lazy ngay trong quá trình khởi động context** (*eager singleton initialization / singleton pre-instantiation*). Nhờ đó, những lỗi như thiếu dependency, constructor thất bại, cấu hình sai hoặc callback khởi tạo lỗi thường được phát hiện trước khi ứng dụng bắt đầu xử lý công việc.

Khái niệm này dễ bị hiểu nhầm theo hai chiều:

- “Singleton chỉ được tạo khi có `getBean()` lần đầu” — **không đúng mặc định với `ApplicationContext`**.
- “Có bean definition nghĩa là object đã được tạo” — **không đúng**. Definition là metadata, còn instance xuất hiện ở một giai đoạn sau.
- “`@Lazy` bảo đảm bean không bao giờ được tạo khi startup” — **không tuyệt đối**. Một singleton eager phụ thuộc bắt buộc vào lazy bean có thể buộc Spring tạo lazy bean để hoàn thành dependency.
- “Pre-instantiation nghĩa là tạo tất cả bean của mọi scope” — **không đúng**. Nó chủ yếu liên quan **singleton không lazy**, có ngoại lệ và chi tiết riêng cho `FactoryBean`, dependency graph và cơ chế bootstrap.

Bài học này thuộc phần **Thời điểm tạo bean** của **Spring Framework – Core Container**, nhằm giúp bạn:

1. Phân biệt `BeanDefinition`, bean instance, scope singleton và cache singleton.
2. Nắm được thời điểm `ApplicationContext.refresh()` thực hiện pre-instantiation.
3. Hiểu hành vi mặc định **eager singleton** và lý do Spring ưu tiên phát hiện lỗi sớm.
4. Dùng `@Lazy`, XML `lazy-init`, `default-lazy-init` và hiểu cách dependency làm lazy bean bị tạo sớm.
5. So sánh `ApplicationContext` với `BeanFactory` sử dụng ở mức thấp.
6. Biết sự khác nhau giữa *được đăng ký*, *đang được tạo*, *đã khởi tạo đầy đủ* và *đang tồn tại trong singleton cache*.
7. Hiểu mối liên hệ của constructor, dependency injection, `@PostConstruct`, `InitializingBean`, `initMethod` và `BeanPostProcessor`.
8. Hiểu callback `SmartInitializingSingleton.afterSingletonsInstantiated()` tại cuối giai đoạn pre-instantiation.
9. Biết vì sao `@DependsOn` biểu diễn ordering chứ không thay thế constructor dependency hay job orchestration.
10. Chẩn đoán startup chậm, lỗi bean creation, vòng dependency và side effects trong startup.
11. Viết và chạy ví dụ **Java 17 + Spring Framework 6.2** có output ổn định, không phụ thuộc thứ tự tạo bean tình cờ.
12. Kiểm tra bean có thật sự được tạo bằng API phù hợp và xây integration tests theo lifecycle.

**Nguyên tắc trung tâm:** **singleton** trả lời câu hỏi “bao nhiêu instance được quản lý cho một bean definition trong một container?”, còn **eager/lazy** trả lời câu hỏi “instance được tạo lúc nào?”. Hai trục này độc lập về khái niệm.

## 2. Singleton trong Spring là gì?

Mặc định, một bean definition Spring có scope `singleton`:

```java
@Configuration(proxyBeanMethods = false)
class ServiceConfig {

    @Bean
    AccountService accountService() {
        return new AccountService();
    }
}
```

Đây là **đoạn minh họa**, cần class `AccountService` thực tế. Khi BeanFactory quản lý bean `accountService` theo singleton scope, những lần tra cứu bean này trong cùng container thường nhận cùng reference:

```java
// Đoạn minh họa dùng biến context đã được tạo.
Object first = context.getBean("accountService");
Object second = context.getBean("accountService");
System.out.println(first == second); // true
```

Spring lưu và trả lại instance singleton đã được quản lý; không gọi factory method một lần cho mỗi `getBean()`.

### Singleton của Spring không phải GoF Singleton pattern

| Khía cạnh | Spring singleton scope | GoF Singleton pattern |
| --- | --- | --- |
| Đơn vị quản lý | Mỗi **bean definition**, trong một **container** | Thường do class/loader hoặc static field kiểm soát |
| Cơ chế tạo | BeanFactory theo bean metadata và lifecycle | Logic trong class như private constructor + static method |
| Tính linh hoạt | Một class có thể có nhiều bean definitions tên khác nhau | Thường nhắm tới một instance toàn cục |
| Thử nghiệm | Mỗi test ApplicationContext có thể có các singleton riêng | Global static instance dễ gây chia sẻ state |
| AOP/proxy | Reference bean có thể là proxy được quản lý | Không phải đặc trưng của pattern |

Nếu một ApplicationContext khai báo hai bean names cùng dùng class `Client`, container có thể tạo **hai singleton độc lập**. “Singleton” không có nghĩa mỗi Java class chỉ có đúng một object trên toàn JVM.

### Scope và thời điểm tạo là hai thuộc tính khác nhau

Bean `singleton` có thể:

- **Eager:** được tạo khi context refresh, trước khi application code gọi `getBean()`.
- **Lazy:** được tạo khi container cần instance lần đầu.
- **Lazy nhưng bị dependency kéo lên sớm:** một singleton eager cần nó làm constructor parameter.
- **Được đăng ký sau khi context đã hoạt động:** các thao tác dynamic registration có lifecycle riêng, không nên mặc định diễn giải giống luồng refresh ban đầu.

Bean `prototype` thường được tạo mỗi lần được yêu cầu; nó không được “pre-instantiate toàn bộ” bằng một lượt refresh như singleton thông thường.

## 3. BeanDefinition không phải là bean instance

Spring đọc metadata từ nhiều nguồn: `@Configuration`/`@Bean`, component scanning, XML `<bean>`, hoặc registration qua `BeanDefinitionRegistry`. Từ các nguồn đó, nó biết **bean cần tồn tại** trước khi nhất thiết dựng object.

Sơ đồ:

```text
@Configuration / @Bean / @Component / XML
                     |
                     v
               BeanDefinition
         tên bean, class/factory method,
         scope, lazy, dependencies, init...
                     |
                     v
        BeanFactory đăng ký definition
                     |
                Theo nhu cầu
        hoặc singleton pre-instantiation
                     |
                     v
         Constructor / factory method
                     |
                     v
           Dependency injection
                     |
                     v
            Initialization phase
                     |
                     v
       Fully initialized managed bean
                     |
                     v
       Singleton cache / bean exposure
```

Một số bước có thể lồng nhau và Spring còn xử lý `BeanPostProcessor`, AOP proxies, circular-reference protection, awareness callbacks và hạ tầng liên quan. Sơ đồ là mô hình tư duy, **không phải mô tả đầy đủ từng bước nội bộ** của mọi BeanFactory.

### Kiểm tra “definition có” khác “singleton đã được tạo”

Với `ConfigurableApplicationContext`:

```java
// Minh họa khi context đang active.
boolean defined =
        context.getBeanFactory().containsBeanDefinition("client");
boolean instantiated =
        context.getBeanFactory().containsSingleton("client");
```

- `containsBeanDefinition("client")` hỏi registry có **local definition** tên đó không; không có nghĩa đã instantiate.
- `containsSingleton("client")` hỏi singleton registry hiện giữ **singleton object** cho tên đó hay chưa.
- `containsBean("client")` nói tên bean có thể resolve được hay không (có thể xét parent/alias theo API), **không** phải bài test “đã xây object”.
- `getBean("client")` **có thể tự làm bean được tạo**, nên đừng dùng nó để kiểm tra trạng thái trước-instantiation rồi nói rằng không ảnh hưởng quá trình quan sát.

Ngoài ra, hạ tầng có thể đăng ký singleton objects không xuất phát từ một BeanDefinition thông thường; đó là lý do đừng kết luận hai API kiểm tra definition và singleton cache luôn đồng nhất.

## 4. Eager singleton: hành vi mặc định của ApplicationContext

Trong `ApplicationContext` thông dụng, Spring tạo và cấu hình **những singleton bean bình thường, không lazy** khi context được khởi tạo/refresh.

```java
try (var context =
        new AnnotationConfigApplicationContext(AppConfig.class)) {
    // Khi constructor trả về, refresh đã thực hiện
    // giai đoạn pre-instantiation mặc định.
}
```

Đây là đoạn minh họa, `AppConfig` cần được khai báo và import API `org.springframework.context.annotation.AnnotationConfigApplicationContext`.

Điều quan trọng là **trước khi đi vào body của `try`**, constructor `AnnotationConfigApplicationContext(AppConfig.class)` đã tạo context và refresh. Vì vậy một singleton eager có thể đã gọi constructor/init callback ngay cả khi user code chưa tra cứu bean nào.

### Vì sao Spring tạo sớm?

- **Fail fast:** Thiếu bean, lỗi converter, sai property hoặc constructor exception lộ ra khi startup.
- **Dependency graph:** Các singleton thường sẵn sàng phục vụ lần gọi đầu tiên của application.
- **Lifecycle ổn định hơn:** Init callbacks của các bean cần thiết được thực thi trước khi context được coi là hoàn tất khởi tạo.
- **Khả năng quan sát:** Lỗi cấu hình có nguyên nhân rõ tại startup thay vì phát sinh ngẫu nhiên khi người dùng đầu tiên gọi API.
- **Nghĩa vụ của container:** `ApplicationContext` có thể chủ động chuẩn bị một loạt infrastructure beans như listener, lifecycle processors và bean post-processors đúng giai đoạn.

Đổi lại, việc tạo nhiều singleton có thể làm startup tốn CPU, RAM, filesystem, I/O và thời gian kết nối external dependencies. Cần phân biệt **chi phí dựng bean hợp lệ** với **business workflow nặng bị nhét vào constructor/init callback**.

### Chính xác Spring tạo cái gì?

Cách diễn đạt hữu ích là: Spring pre-instantiates **regular, non-lazy singleton beans** trong BeanFactory. Đây **không** phải tuyên bố mọi bean ở mọi scope, lazy dependencies hay mọi external resource đều đã sẵn sàng.

Một bean prototype không tự được tạo chỉ vì đã có definition. Nhưng nếu constructor của eager singleton cần prototype, Spring có thể **tạo một prototype instance** để inject vào singleton. Đó là **dependency-driven creation**, không phải “preinstantiate tất cả prototype beans”.

## 5. Giai đoạn pre-instantiation nằm ở đâu trong refresh?

`ApplicationContext.refresh()` thực hiện nhiều bước để biến metadata thành container có thể phục vụ. Một luồng khái niệm:

```text
ApplicationContext.refresh()
   |
   +--> Chuẩn bị Environment và context
   |
   +--> Tạo / lấy BeanFactory, nạp bean definitions
   |
   +--> BeanFactoryPostProcessor / registry processing
   |
   +--> Đăng ký BeanPostProcessor
   |
   +--> Hạ tầng application events, message source, lifecycle...
   |
   +--> finishBeanFactoryInitialization(...)
   |       |
   |       +--> preInstantiateSingletons()
   |                |
   |                +--> Tạo regular singleton non-lazy
   |                +--> Resolve dependencies
   |                +--> Chạy bean-local initialization
   |                +--> SmartInitializingSingleton callbacks
   |
   +--> finishRefresh(...)
           |
           +--> Lifecycle startup theo cơ chế tương ứng
           +--> ContextRefreshedEvent
```

Đây là **sơ đồ đơn giản hóa** cho ApplicationContext chuẩn, không cam kết chi tiết ordering của mọi extension point, bean definition, event multicaster hay công việc background trong mọi phiên bản.

### preInstantiateSingletons() là API nào?

`org.springframework.beans.factory.config.ConfigurableListableBeanFactory` công bố:

```java
void preInstantiateSingletons();
```

Method này đảm bảo pre-instantiation cho **non-lazy singletons**, với xử lý riêng liên quan `FactoryBean`, và có thể ném `BeansException` nếu không tạo được một bean.

Trong `ApplicationContext`, bạn thường **không cần gọi method này bằng tay**, vì lifecycle `refresh()` đảm trách. API này hữu ích để hiểu cơ chế hoặc khi bạn làm việc với một `DefaultListableBeanFactory` ở mức thấp.

### “Đã pre-instantiate” không có nghĩa “đã chạy mọi business task”

Pre-instantiation hoàn thiện nhóm bean instances theo lifecycle của chúng. Nó không tự:

- Seed cơ sở dữ liệu hoặc chạy migration nghiệp vụ nếu không có code/hạ tầng được đăng ký làm việc đó.
- Bảo đảm message broker, database cluster, external APIs đã healthy.
- Tạo tất cả singleton lazy không cần dùng.
- Bảo đảm scheduler/consumer lifecycle đã start xong trước mọi mốc lifecycle khác.
- Biến một callback init thành cơ chế exactly-once trong môi trường nhiều replicas.

## 6. Phân biệt BeanFactory thuần và ApplicationContext

`BeanFactory` là giao diện IoC container nền tảng. Một số chương trình có thể dùng `DefaultListableBeanFactory` và tự đăng ký `BeanDefinition`, rồi gọi `getBean` khi cần.

Nếu bạn chỉ đăng ký definition vào một `DefaultListableBeanFactory` mới, nó **không tự giả định bạn muốn làm đầy toàn bộ non-lazy singletons ngay lúc registration**. Bạn có thể gọi `preInstantiateSingletons()` khi cần hoặc để chúng được tạo qua `getBean()`.

Ngược lại, `AnnotationConfigApplicationContext`/`ClassPathXmlApplicationContext` thường điều phối cả BeanFactory setup, BeanPostProcessors và pre-instantiation trong `refresh()`.

### Ví dụ minh họa

```java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
factory.registerBeanDefinition(
        "tool", new RootBeanDefinition(Tool.class));

// Có definition, chưa chắc đã có instance.
boolean before = factory.containsSingleton("tool");

// Kích hoạt pre-instantiation bằng tay:
factory.preInstantiateSingletons();
boolean after = factory.containsSingleton("tool");
```

Đây là đoạn minh họa, `Tool` phải là class phù hợp với public constructor và cần imports `DefaultListableBeanFactory`/`RootBeanDefinition`. Một phiên bản runnable riêng của ý tưởng này nằm ở mục 14.

**Không dùng raw BeanFactory demo để suy ra toàn bộ tính năng ApplicationContext hoạt động tự động**: BeanPostProcessors, annotation-driven features, application events và lifecycle orchestration có những bước registration riêng.

## 7. Lazy singleton: trì hoãn việc tạo

`@Lazy` khiến một bean definition có chủ đích **không bị pre-instantiate** như singleton eager thông thường.

```java
@Configuration(proxyBeanMethods = false)
class Config {

    @Bean
    @Lazy
    HeavyCatalog heavyCatalog() {
        return new HeavyCatalog();
    }
}
```

Đây là minh họa cần `HeavyCatalog`. Nếu không có eager bean nào phụ thuộc bắt buộc vào `HeavyCatalog`, container có thể trì hoãn khởi tạo cho đến lần đầu nó được yêu cầu.

Trong XML:

```xml
<bean id="heavyCatalog"
      class="example.HeavyCatalog"
      lazy-init="true"/>
```

### Eager mặc định và lazy tường minh

| Bean definition | Scope | Lazy? | Khi nào thường tạo? |
| --- | --- | --- | --- |
| `@Bean CatalogRepository repository()` | singleton mặc định | Không | Trong `ApplicationContext.refresh()` |
| `@Bean @Lazy CacheClient cacheClient()` | singleton | Có | Khi được yêu cầu lần đầu nếu không bị eager dependency kéo lên |
| XML `<bean id="repo" .../>` | singleton mặc định | Không | Khi ApplicationContext refresh |
| XML `<bean id="cache" ... lazy-init="true"/>` | singleton | Có | On demand, trừ khi cần cho bean eager |
| `@Scope("prototype")` | prototype | Không gắn eager singleton semantics | Theo nhu cầu, kể cả khi làm dependency |
| Lazy singleton dependency của eager singleton | singleton | Có | Có thể **bị tạo lúc startup** |

Đặc biệt: **lazy không phải một scope mới**. Một singleton lazy sau khi được tạo vẫn có singleton identity như thường.

### @Lazy trên class @Configuration

```java
@Configuration
@Lazy
class LazyConfiguration {
    @Bean
    ExpensiveClient client() {
        return new ExpensiveClient();
    }
}
```

Theo Spring reference, `@Lazy` trên `@Configuration` có thể đặt default lazy initialization cho các bean definitions thuộc configuration đó. Nhưng những singleton bị eager beans khác yêu cầu vẫn có thể được tạo, và infrastructure/configuration-processing mechanics có quy tắc riêng. Không mặc định toàn context trở thành “không khởi tạo bean nào”.

### XML default-lazy-init

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       default-lazy-init="true">
    <bean id="client" class="example.Client"/>
    <bean id="service" class="example.Service"/>
</beans>
```

Đây là minh họa giản lược namespace; trong ứng dụng thật, nên khai báo XML schema và các namespace đầy đủ. `default-lazy-init="true"` đặt default lazy cho beans trong phạm vi metadata đó. Bean có `lazy-init="false"` tường minh có thể được đánh eager, và dependency graph vẫn có thể tạo sớm lazy collaborators.

## 8. Bẫy: lazy bean là dependency của eager singleton

Đây là ngoại lệ được Spring docs nhấn mạnh.

Giả sử:

```java
@Configuration(proxyBeanMethods = false)
class Config {

    @Bean
    @Lazy
    PriceRules priceRules() {
        return new PriceRules();
    }

    @Bean
    CheckoutService checkoutService(PriceRules priceRules) {
        return new CheckoutService(priceRules);
    }
}
```

Hai kiểu ở đây là minh họa. `priceRules` có metadata lazy; nhưng `checkoutService` là singleton không lazy. Khi Spring cần tạo `checkoutService` lúc refresh, nó phải truyền một `PriceRules` instance vào constructor, vì vậy bean `priceRules` được tạo **lúc startup**.

```text
checkoutService (eager singleton)
         |
         +--> constructor parameter: PriceRules
                            |
                            v
                   priceRules (lazy definition)
                            |
                            v
              Vẫn bị instantiate lúc refresh
```

**Ghi nhớ:** `@Lazy` trên **bean definition** không tự tạo proxy cho mọi dependency injection point. Nếu eager consumer cần instance thật để hoàn thành constructor, BeanFactory sẽ resolve nó.

### @Lazy ở injection point khác lazy ở definition

Spring cũng hỗ trợ `@Lazy` trên điểm injection để tạo một **lazy-resolution proxy** trong những tình huống phù hợp:

```java
final class CheckoutService {
    private final PriceRules rules;

    CheckoutService(@Lazy PriceRules rules) {
        this.rules = rules;
    }
}
```

Đoạn này cần import `org.springframework.context.annotation.Lazy`. Tại injection point, Spring có thể inject proxy trì hoãn target resolution. Nhưng việc dùng proxy có giới hạn type/proxy semantics, và **không sửa vòng dependency/thiết kế sai** chỉ bằng trì hoãn.

Hơn nữa, nếu code gọi method của proxy trong constructor hoặc init callback, target vẫn có thể bị tạo rất sớm. Đừng coi `@Lazy` injection point là bảo đảm tuyệt đối cho mọi trạng thái startup.

## 9. Prototype khác pre-instantiated singleton như thế nào?

`prototype` có nghĩa BeanFactory tạo một instance mới mỗi lần được yêu cầu theo semantics của scope đó.

```java
@Bean
@Scope("prototype")
Worker worker() {
    return new Worker();
}
```

Đây là minh họa cần `Worker` và import `Scope`. Bean `worker` sẽ không bị bulk pre-instantiated chỉ vì nằm trong registry.

Tuy nhiên:

```java
@Bean
JobRunner jobRunner(Worker worker) {
    return new JobRunner(worker);
}
```

Nếu `jobRunner` là singleton eager, Spring tạo một `Worker` prototype để inject **khi JobRunner được tạo**. `JobRunner` giữ cùng worker ấy cho đến khi logic của nó tự thay đổi reference; không tự nhận prototype mới cho mỗi lần gọi `run()`.

### Factory/Provider cho nhu cầu object mới mỗi lần

```java
final class JobRunner {
    private final ObjectProvider<Worker> workers;

    JobRunner(ObjectProvider<Worker> workers) {
        this.workers = workers;
    }

    void runJob() {
        Worker fresh = workers.getObject();
        fresh.run();
    }
}
```

Đây là minh họa cần import `org.springframework.beans.factory.ObjectProvider` và method `Worker.run()`. Mỗi lượt `getObject()` đối với prototype có thể tạo một instance mới. Nhưng đừng dùng provider chỉ để che một vòng constructor hay dependency bắt buộc bị thiếu.

### Prototype destruction

Container thực hiện initialization cho prototype bean được tạo, nhưng không tự quản lý destroy callbacks của mọi prototype giống singleton sau khi phát instance cho caller. Nếu worker sở hữu file/socket/resource, application phải thiết kế ownership và cleanup.

## 10. Lifecycle của một eager singleton

Pre-instantiation không đơn giản là gọi constructor rồi lưu object. Với Spring-managed bean, có thể có:

1. Đăng ký bean definition.
2. Chọn constructor hoặc `@Bean` factory method.
3. Resolve các bean dependencies bắt buộc.
4. Construct raw instance.
5. Dependency/property injection.
6. `Aware` callbacks phù hợp.
7. `BeanPostProcessor` hooks và initialization annotations.
8. `@PostConstruct`, `InitializingBean.afterPropertiesSet()`, custom `initMethod` theo cơ chế Spring.
9. Proxy wrapping và các post-processing hoàn tất.
10. Publish bean instance trong singleton registry theo semantics của container.

Các bước có thể khác trong những infrastructure beans hoặc extension points; nhất là AOP/proxy, factory methods, `FactoryBean` và early singleton references. Điều cốt lõi là chỉ khi bean đã trải qua lifecycle thích hợp mới nên coi nó “sẵn sàng”.

### Constructor chạy trước @PostConstruct

```java
final class LocalIndex {
    private boolean ready;

    public LocalIndex() {
        ready = false;
    }

    void initialize() {
        ready = true;
    }
}
```

Nếu khai báo `@Bean(initMethod="initialize")`, Spring gọi init method sau khi factory tạo instance và chuẩn bị injection tương ứng. Trong init callback của bean, không nên giả định mọi bean khác trong toàn context đều sẵn sàng.

### Không chạy nghiệp vụ nặng trong constructor và @PostConstruct

Spring 6.2 documentation lưu ý initialization callbacks của singleton thường chạy trong phạm vi quản lý **singleton creation lock**. Công việc nặng, external I/O hoặc truy cập lẫn lộn sang bean khác từ init callback có thể kéo dài startup hoặc phát sinh deadlock.

Phù hợp:

- Validate local settings.
- Chuẩn bị một bảng tra cứu nội bộ nhỏ.
- Xác nhận invariant của bean.
- Khởi tạo resource mà bean thực sự sở hữu theo lifecycle được thiết kế, với timeout/cleanup.

Cần cân nhắc cơ chế muộn hơn:

- Seed dữ liệu nghiệp vụ.
- Gọi nhiều API ngoài để chuẩn bị hệ thống.
- Start background worker dài hạn.
- Điều phối transaction và readiness giữa nhiều modules.

Các việc này nên có startup coordinator hoặc lifecycle extension point thích hợp, không gắn ngẫu nhiên vào mọi constructor.

## 11. SmartInitializingSingleton: hook cuối pha pre-instantiation

`org.springframework.beans.factory.SmartInitializingSingleton` cung cấp callback:

```java
public interface SmartInitializingSingleton {
    void afterSingletonsInstantiated();
}
```

Một singleton non-lazy implement interface này có thể được gọi **sau khi BeanFactory hoàn tất pre-instantiation các singleton thông thường**.

Lợi ích:

- Bean muốn làm một bước hậu khởi tạo có thể giả định các **regular non-lazy singleton beans** đã tạo xong.
- Giảm nguy cơ một lời gọi `getBeansOfType(...)` vô tình gây early initialization trong chính giai đoạn tạo singleton.
- Phù hợp cho công việc hậu khởi tạo nhiều bean nhưng không cần ApplicationContext event model.
- Cơ chế được hỗ trợ bởi `ListableBeanFactory` thích hợp, không chỉ listener của `ApplicationContext`.

### Callback có chạy cho một lazy singleton tạo sau startup?

**Không mặc định.** Javadoc nêu callback này không được kích hoạt cho singleton vốn lazy và chỉ được tạo theo nhu cầu **sau BeanFactory bootstrap**, cũng không được kích hoạt cho các scope khác.

Nếu coordinator cần chạy ở startup, hãy bảo đảm chính coordinator là managed **non-lazy singleton** được tạo trong pre-instantiation phase.

### Callback này có phải ApplicationReadyEvent của Spring Boot?

Không. `SmartInitializingSingleton` nằm trong Spring Framework beans module và chạy ở cuối **singleton pre-instantiation**, không mang nghĩa toàn bộ web server đã công bố readiness hay toàn hệ thống bên ngoài đã sẵn sàng.

`ContextRefreshedEvent` được phát sau các giai đoạn refresh phù hợp. `ApplicationReadyEvent` thuộc **Spring Boot**, thường xuất hiện muộn hơn trong lifecycle Boot. Đừng gộp những mốc này thành một khái niệm “ứng dụng đã làm xong mọi thứ”.

### Khi nào nên dùng SmartLifecycle thay thế?

Nếu thành phần có **start/stop theo phase** như consumer, poller hoặc long-running listener, Spring khuyến nghị `Lifecycle`/`SmartLifecycle` để quản lý start/stop, shutdown callbacks và phase ordering. `SmartInitializingSingleton` thích hợp cho callback ngắn sau pre-instantiation, không phải nơi đặt một vòng lặp vô hạn.

## 12. Ví dụ runnable: eager, lazy, lazy bị kéo lên sớm

Ví dụ dùng **Java 17, Spring Framework 6.2.19 và Maven**. Nó không cần Spring Boot, DB, network hay các file ngoài project.

Để tránh dựa vào **thứ tự constructor output** của Spring, ta dùng `Tracker` ghi lại các mốc sự kiện. Chương trình chỉ in các **trạng thái đúng theo hợp đồng dependency** sau khi context refresh hoàn thành, nhờ đó stdout ổn định.

### 12.1. Cấu trúc dự án

```text
spring-eager-singletons/
├── pom.xml
└── src/main/java/demo/
    └── EagerSingletonDemo.java
```

### 12.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-eager-singletons</artifactId>
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

### 12.3. File Java đầy đủ

Tạo `src/main/java/demo/EagerSingletonDemo.java`:

```java
package demo;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public class EagerSingletonDemo {

    public static final class Tracker {
        private final Set<String> events = new HashSet<>();

        public void mark(String event) {
            events.add(event);
        }

        public boolean has(String event) {
            return events.contains(event);
        }
    }

    public static final class EagerCache {
        private final Tracker tracker;

        public EagerCache(Tracker tracker) {
            this.tracker = tracker;
            tracker.mark("eager.constructed");
        }

        public void initialize() {
            tracker.mark("eager.initialized");
        }

        public void shutdown() {
            tracker.mark("eager.destroyed");
        }
    }

    public static final class RequiredIndex {
        public RequiredIndex(Tracker tracker) {
            tracker.mark("required.created");
        }
    }

    public static final class OptionalGateway {
        public OptionalGateway(Tracker tracker) {
            tracker.mark("optional.created");
        }
    }

    public static final class EagerConsumer {
        private final RequiredIndex index;
        private final EagerCache cache;

        public EagerConsumer(RequiredIndex index, EagerCache cache) {
            this.index = index;
            this.cache = cache;
        }

        public boolean hasDependencies() {
            return index != null && cache != null;
        }
    }

    public static final class StartupCheck
            implements SmartInitializingSingleton {
        private final Tracker tracker;

        public StartupCheck(Tracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void afterSingletonsInstantiated() {
            tracker.mark("afterSingletons");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public Tracker tracker() {
            return new Tracker();
        }

        @Bean(initMethod = "initialize", destroyMethod = "shutdown")
        public EagerCache eagerCache(Tracker tracker) {
            return new EagerCache(tracker);
        }

        @Bean
        @Lazy
        public RequiredIndex requiredIndex(Tracker tracker) {
            return new RequiredIndex(tracker);
        }

        @Bean
        @Lazy
        public OptionalGateway optionalGateway(Tracker tracker) {
            return new OptionalGateway(tracker);
        }

        @Bean
        public EagerConsumer eagerConsumer(
                RequiredIndex index, EagerCache cache) {
            return new EagerConsumer(index, cache);
        }

        @Bean
        public StartupCheck startupCheck(Tracker tracker) {
            return new StartupCheck(tracker);
        }
    }

    public static void main(String[] args) {
        Tracker tracker;

        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            tracker = context.getBean(Tracker.class);

            System.out.println("eager.constructed=" +
                    tracker.has("eager.constructed"));
            System.out.println("eager.initialized=" +
                    tracker.has("eager.initialized"));
            System.out.println("requiredLazy.created=" +
                    tracker.has("required.created"));
            System.out.println("optionalLazy.createdBefore=" +
                    tracker.has("optional.created"));
            System.out.println("afterSingletons.called=" +
                    tracker.has("afterSingletons"));
            System.out.println("consumer.dependencies=" +
                    context.getBean(EagerConsumer.class).hasDependencies());

            OptionalGateway first =
                    context.getBean(OptionalGateway.class);
            OptionalGateway second =
                    context.getBean(OptionalGateway.class);

            System.out.println("optionalLazy.createdAfter=" +
                    tracker.has("optional.created"));
            System.out.println("optionalLazy.sameSingleton=" +
                    (first == second));
        }

        System.out.println("eager.destroyedAfterClose=" +
                tracker.has("eager.destroyed"));
    }
}
```

### 12.4. Lệnh chạy

Ở thư mục chứa `pom.xml`:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.EagerSingletonDemo
```

### 12.5. Output dự kiến chính xác

Đây là **các dòng do chương trình in**; log của Maven và các framework components (nếu có) không tính vào stdout ứng dụng:

```text
eager.constructed=true
eager.initialized=true
requiredLazy.created=true
optionalLazy.createdBefore=false
afterSingletons.called=true
consumer.dependencies=true
optionalLazy.createdAfter=true
optionalLazy.sameSingleton=true
eager.destroyedAfterClose=true
```

### 12.6. Giải thích từng nhóm dòng

**`eager.constructed=true`:** `eagerCache` là singleton không lazy. Spring tạo nó trong quá trình refresh, nên constructor đã đánh dấu `eager.constructed` trước khi code trong `main` kiểm tra.

**`eager.initialized=true`:** `@Bean(initMethod = "initialize")` bảo Spring gọi `EagerCache.initialize()` sau khi tạo bean và chuẩn bị dependency. Do đó lúc constructor ApplicationContext trả về, bean đã hoàn tất local init callback.

**`requiredLazy.created=true`:** `requiredIndex` có `@Lazy`, nhưng `eagerConsumer` là eager singleton cần `RequiredIndex` qua constructor. Container buộc phải tạo `requiredIndex` để có thể hoàn tất `eagerConsumer`.

**`optionalLazy.createdBefore=false`:** `optionalGateway` cũng có `@Lazy` nhưng không bean eager nào yêu cầu nó. Vì vậy ngay sau refresh trong ví dụ, object này chưa được tạo.

**`afterSingletons.called=true`:** `startupCheck` là eager singleton thực thi `SmartInitializingSingleton`. Spring gọi callback sau khi hoàn tất pre-instantiation các regular non-lazy singleton beans, nên marker đã được ghi.

**`consumer.dependencies=true`:** `EagerConsumer` nhận đủ `RequiredIndex` và `EagerCache` qua parameters của `@Bean` method. Cả hai references không null.

**`optionalLazy.createdAfter=true`:** Khi `main` gọi `context.getBean(OptionalGateway.class)`, Spring tạo bean lazy còn thiếu. Constructor ghi dấu mới vào Tracker.

**`optionalLazy.sameSingleton=true`:** Bean lazy sau khi tạo vẫn là singleton. Lấy hai lần trong cùng context trả cùng reference.

**`eager.destroyedAfterClose=true`:** Kết thúc `try-with-resources` gọi `ApplicationContext.close()`, kích hoạt `@Bean(destroyMethod = "shutdown")` của `eagerCache`. Marker được đọc qua Java reference `tracker` vẫn còn trong local variable sau khi context đóng, chỉ để chứng minh lifecycle callback; không nên tiếp tục sử dụng managed bean phục vụ nghiệp vụ sau khi container đã close.

### 12.7. Những điểm làm ví dụ đáng tin cậy

- Mọi bean classes đều là `public static` nested classes; Spring có thể tạo chúng từ Java `@Bean` methods mà không cần outer object.
- Không có static mutable global counter; các trạng thái thuộc `Tracker` bean của context hiện tại.
- Không dùng thứ tự duyệt `HashSet` để in kết quả, chỉ dùng membership checks. Vì vậy thứ tự bean construction nội bộ không ảnh hưởng thứ tự stdout.
- Không gọi `getBean(OptionalGateway.class)` **trước** khi in `optionalLazy.createdBefore`.
- `@Configuration(proxyBeanMethods=false)` an toàn vì dependencies được truyền bằng **factory method parameters**, không gọi trực tiếp một `@Bean` method khác.
- Không dùng Spring Boot, `@SpringBootApplication` hoặc web server; `spring-context` đủ cho code này.
- Dùng `try-with-resources` để thể hiện cleanup của managed singleton.

## 13. Thử nghiệm đơn lẻ với BeanFactory thuần

Để hiểu sự khác biệt **registration** và **pre-instantiation**, sau đây là project nhỏ chạy với dependency Maven ở mục 12. Tạo file Java thứ hai `src/main/java/demo/BeanFactoryPreInstantiationDemo.java`:

```java
package demo;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class BeanFactoryPreInstantiationDemo {

    public static final class Tool {
        public Tool() {
        }
    }

    public static void main(String[] args) {
        DefaultListableBeanFactory factory =
                new DefaultListableBeanFactory();

        factory.registerBeanDefinition(
                "tool", new RootBeanDefinition(Tool.class));

        System.out.println("definition=" +
                factory.containsBeanDefinition("tool"));
        System.out.println("before=" +
                factory.containsSingleton("tool"));

        factory.preInstantiateSingletons();

        System.out.println("after=" +
                factory.containsSingleton("tool"));

        factory.destroySingletons();

        System.out.println("afterDestroy=" +
                factory.containsSingleton("tool"));
    }
}
```

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.BeanFactoryPreInstantiationDemo
```

Output:

```text
definition=true
before=false
after=true
afterDestroy=false
```

**Giải thích:**

1. `registerBeanDefinition` đăng ký metadata, nên `definition=true`.
2. Chưa yêu cầu instance và chưa chạy pre-instantiation, nên `before=false`.
3. `preInstantiateSingletons()` kích hoạt tạo singleton non-lazy `tool`, nên `after=true`.
4. `destroySingletons()` giải phóng singleton instances trong registry ở ví dụ BeanFactory thấp cấp này, nên `afterDestroy=false`.

**Giới hạn:** Demo BeanFactory này minh họa registration và singleton cache, không tái hiện toàn bộ lifecycle `ApplicationContext.refresh()`. Không nên thay ApplicationContext trong ứng dụng thông thường chỉ để tự kiểm soát thời điểm khởi tạo nếu bạn vẫn cần full annotations, events và lifecycle infrastructure.

## 14. Cách cấu hình trong Spring XML

Mặc dù ví dụ chính dùng Java configuration, cơ chế eager/lazy cũng áp dụng cho bean definitions đọc từ XML.

### 14.1. Singleton eager mặc định

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalog" class="example.CatalogService"/>

</beans>
```

Giả sử `example.CatalogService` có constructor hợp lệ và classpath đầy đủ, bean singleton này sẽ được pre-instantiate trong `ClassPathXmlApplicationContext` theo mặc định.

### 14.2. Lazy trên một bean

```xml
<bean id="optionalClient"
      class="example.OptionalClient"
      lazy-init="true"/>
```

Bean này không bị pre-instantiate mặc định **nếu không có eager dependency khác cần nó**.

### 14.3. Default lazy cho nhóm beans

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       default-lazy-init="true">
    <bean id="clientA" class="example.ClientA"/>
    <bean id="clientB" class="example.ClientB"/>
</beans>
```

Snippet lược bớt schema để tập trung vào thuộc tính. Một BeanFactory đọc XML có thể áp dụng default laziness cho definitions trong scope đó. Cần nhớ infrastructure beans và eager dependency requirements có thể khiến một số objects vẫn được tạo lúc startup.

### 14.4. Lazy bean bị reference từ eager bean

```xml
<beans xmlns="http://www.springframework.org/schema/beans">
    <bean id="lazyRules"
          class="example.PriceRules"
          lazy-init="true"/>

    <bean id="checkoutService"
          class="example.CheckoutService">
        <constructor-arg ref="lazyRules"/>
    </bean>
</beans>
```

Theo quy tắc dependency resolution, `checkoutService` mặc định eager, nên `lazyRules` được tạo đủ sớm để có thể truyền vào constructor. Đây là **XML tương đương** với trường hợp `@Bean @Lazy` + eager consumer trong Java.

### 14.5. Scope prototype trong XML

```xml
<bean id="jobWorker"
      class="example.JobWorker"
      scope="prototype"/>
```

Không có khái niệm pre-instantiating toàn bộ prototype beans theo một lượt vì số instances phụ thuộc yêu cầu của caller.

## 15. @DependsOn và điều khiển thứ tự

`@DependsOn`/XML `depends-on` có thể làm BeanFactory **khởi tạo một bean trước một bean khác** khi có ordering requirement ngoài constructor/property dependency.

Java:

```java
@Bean
@DependsOn("schemaValidator")
ReportService reportService() {
    return new ReportService();
}
```

XML:

```xml
<bean id="reportService"
      class="example.ReportService"
      depends-on="schemaValidator"/>
```

Đây là các đoạn minh họa cần định nghĩa `schemaValidator` và class `ReportService`. Nếu `reportService` được tạo, Spring bảo đảm bean `schemaValidator` được tạo trước theo dependency ordering mà metadata yêu cầu.

### Quan hệ đã có qua constructor không cần @DependsOn nữa

```java
@Bean
ReportService reportService(ReportRepository repository) {
    return new ReportService(repository);
}
```

Ở đây BeanFactory phải có `ReportRepository` để gọi method factory tạo `ReportService`. Vì vậy thông thường không cần thêm `@DependsOn("reportRepository")` chỉ để lặp lại dependency đã rõ.

### @DependsOn không bảo đảm application readiness

`@DependsOn` không phải job scheduler hay business orchestration. Nó không có transaction, retry, readiness, idempotency hoặc phân tán coordination mặc định.

Nếu `schemaValidator` chỉ tồn tại để chạy nghiệp vụ khi constructor gọi, hãy xem liệu đó có phải tác vụ cần startup coordinator/hạ tầng migration riêng không. Không gắn side effects nặng vào constructors chỉ để ép ordering.

### Phụ thuộc vòng

Nếu A `@DependsOn("b")` và B `@DependsOn("a")`, bạn đã khai báo thứ tự bất khả giải. Spring không thể vừa tạo A trước B vừa B trước A. Tương tự, constructor dependencies A→B→A phải sửa ở dependency graph, không thể chữa bằng đảo thứ tự bean definitions.

## 16. Pre-instantiation và BeanPostProcessor/AOP

Trong `ApplicationContext`, Spring đăng ký nhiều loại bean infrastructure **trước** khi tạo các regular non-lazy singleton beans. `BeanPostProcessor` có thể tham gia giai đoạn bean initialization và wrap target bằng proxy, ví dụ để hỗ trợ một số AOP features.

Điều này tạo ra các quy tắc quan trọng:

- Đừng gọi `getBean` tùy tiện trong một `BeanFactoryPostProcessor` để cố “làm nóng” các business services; nó có thể khiến bean được tạo **quá sớm** trước khi các BeanPostProcessors cần thiết được đăng ký.
- Những `BeanFactoryPostProcessor`/`BeanPostProcessor` definitions có thể cần được tạo rất sớm để container hoạt động, không đơn giản tuân theo mọi quy tắc “eager regular singleton” như application service thông thường.
- Bean khởi tạo sớm ngoài chu kỳ post-processing có thể không được AOP proxy như kỳ vọng và Spring có thể đưa ra diagnostic về eligibility.
- Nếu `@Configuration` định nghĩa một `BeanFactoryPostProcessor` qua non-static `@Bean`, framework có thể phải instantiate configuration class sớm. Khi phù hợp, khai báo `static @Bean` cho post-processor để tránh early config instance creation.
- Khi đọc log, hãy phân biệt target object, proxy object và bean definition; không mặc định mọi constructor log chứng minh bean sẽ được caller nhìn thấy cùng raw reference.

### Có thể dựa vào thứ tự constructor của các beans không?

**Không nên.** Với những bean độc lập, thứ tự nội bộ có thể thay đổi theo registration, dependencies, version và tùy chọn bootstrap. Spring bảo đảm **dependency ordering cần thiết**, không bảo đảm thứ tự business initialization nếu bạn chỉ dựa vào dòng log “bean A xuất hiện trước bean B”.

Muốn có ordering có ý nghĩa, mô tả dependency hoặc dùng lifecycle orchestration phù hợp. Ví dụ chính cố tình kiểm tra **trạng thái tại một mốc ổn định** thay vì đưa stdout phụ thuộc thứ tự tạo beans.

## 17. FactoryBean trong giai đoạn pre-instantiation

`FactoryBean<T>` là một extension point Spring có thể tạo **product bean**. Nó khác `@Bean` factory method và khác GoF factory class thông thường.

Với BeanFactory, khi tên bean ứng với một `FactoryBean`:

- `getBean("name")` thường trả **product** của factory.
- `getBean("&name")` (dấu `&`) trả chính **FactoryBean object**.
- `FactoryBean.isSingleton()` xác định product do factory cung cấp có singleton semantics hay không.
- Việc pre-instantiation có logic riêng để tạo/check `FactoryBean` và trong các trường hợp được đánh dấu eager-init, có thể khởi tạo product sớm theo hợp đồng `SmartFactoryBean`/container.

Không nên kết luận “mọi FactoryBean product đều eager” hoặc “mọi FactoryBean product đều lazy”. Cần xem:

1. Definition của factory itself có non-lazy singleton hay không.
2. Product có singleton semantics không.
3. Factory có hỗ trợ eager product initialization hay không.
4. Dependency graph có yêu cầu product sớm hay không.
5. Type inspection có buộc Spring initialize factory nhằm xác định type không.

### Cẩn trọng với getBeansOfType và allowEagerInit

Một số overloads của `getBeanNamesForType`/`getBeansOfType` có tham số `allowEagerInit`. Với `FactoryBean`, type introspection đôi khi đòi initialize factory để biết `getObjectType()`. Vì vậy một phép “khảo sát beans theo type” không phải lúc nào cũng hoàn toàn không có side effects.

Nếu mục tiêu chỉ là kiểm tra một singleton đã tồn tại tại một thời điểm, `containsSingleton(beanName)` thích hợp hơn việc dùng `getBean` hoặc cố duyệt mọi beans.

## 18. Pre-instantiation và vòng phụ thuộc

Constructor cycle:

```java
final class Alpha {
    Alpha(Beta beta) {
    }
}

final class Beta {
    Beta(Alpha alpha) {
    }
}
```

Nếu cả hai là singleton eager với constructors yêu cầu nhau, BeanFactory không thể hoàn thành khởi tạo:

```text
alpha -> beta -> alpha (currently in creation)
```

Spring có thể ném `BeanCurrentlyInCreationException` hoặc exception chain `UnsatisfiedDependencyException`/`BeanCreationException`, tùy giai đoạn gây lỗi.

### Fail-fast là lợi ích, không phải nhược điểm

Nếu dependency graph thực sự sai, lỗi ở startup giúp bạn phát hiện trước khi người dùng thao tác. Chuyển tất cả beans sang lazy có thể chỉ đẩy lỗi tới lần đầu API được gọi, khiến runtime failures khó đoán hơn.

### Setter/field cycles cũng nên sửa thiết kế

Một số vòng setter/field injection trong Spring có thể được xử lý bằng early singleton references nếu cơ chế được phép; nhưng code có thể thấy incomplete state hoặc proxy semantics khó lường. Constructor cycle thường vẫn không giải quyết được.

**Giải pháp bền vững** là chỉnh đồ thị dependency:

```text
Trước:
OrderService <--> PaymentService

Sau:
CheckoutCoordinator --> OrderService
                   \-> PaymentService
```

Hoặc trích abstraction/policy chung để services không phải inject lẫn nhau. Không bật cờ circular-reference support chỉ để bỏ qua thiết kế thiếu rõ ràng.

## 19. Startup nhanh hơn bằng lazy có phải luôn tốt?

Không. Lazy giúp trì hoãn **chi phí tạo instance**, chứ không xóa chi phí đó.

Nếu bean chậm vì kết nối remote service hoặc tải dữ liệu lớn, chuyển bean sang lazy có thể:

- Làm startup nhanh hơn.
- Làm **request đầu tiên** chậm hoặc thất bại.
- Dồn việc kết nối vào một thời điểm chịu áp lực traffic.
- Che lỗi cấu hình cho đến khi tính năng hiếm khi dùng được kích hoạt.
- Khi nhiều threads gọi cùng lúc, khiến concurrent callers chờ initialization.

### Khi nên dùng lazy

- Tính năng ít dùng.
- Một bean có chi phí dựng cao nhưng nhiều lượt chạy ứng dụng không cần đến.
- Optional integration chỉ được gọi khi người dùng bật feature.
- Tooling/admin bean không thuộc đường phục vụ request quan trọng.
- Muốn defer construction có chủ đích, với monitoring và timeout.

### Khi nên giữ eager

- Service cốt lõi được dùng ngay sau startup.
- Cần phát hiện lỗi cấu hình trước khi sẵn sàng phục vụ.
- Dependency phải được xác thực sớm vì ảnh hưởng tính an toàn của application.
- Cần hoàn tất resource initialization trước khi chấp nhận traffic.

**Quy tắc thực dụng:** Ưu tiên sửa nguyên nhân làm construction nặng trước. Nếu constructor tải hàng triệu records, vấn đề có thể là business initialization đặt sai lifecycle; `@Lazy` chỉ dời khối lượng việc sang request khác.

## 20. Startup side effects và tính idempotent

Đặt lệnh nghiệp vụ trong constructor của eager singleton:

```java
// Phản ví dụ; các kiểu ứng dụng cần được định nghĩa riêng.
final class WelcomeMailService {
    WelcomeMailService(MailGateway gateway) {
        gateway.sendWelcomeToAllUsers();
    }
}
```

vô tình khiến mỗi lần ApplicationContext pre-instantiate bean là một lần chạy mail workflow. Nhiều contexts trong tests, restart ứng dụng, nhiều replicas hoặc staging environment có thể làm nghiệp vụ chạy lặp.

Tách ra:

- Constructor: lưu dependency và validate invariant.
- Bean init callback: chuẩn bị local resource/state nhỏ, theo hợp đồng bean.
- Startup coordinator: quyết định có gọi seed/check/migration hay không ở lifecycle phù hợp.
- Service method: chứa use case nghiệp vụ.
- Storage/transaction: bảo đảm idempotency nếu tác vụ có thể chạy lặp.

`SmartInitializingSingleton` có thể làm điểm kích hoạt một bước hậu khởi tạo, nhưng **không** tự đảm bảo exactly-once toàn cluster. Với DB seed cần unique constraints/UPSERT hoặc cơ chế coordination phù hợp.

## 21. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Giải thích khả dĩ | Cách kiểm tra/sửa |
| --- | --- | --- |
| Constructor chạy dù chưa `getBean()` | Eager singleton pre-instantiation ở `ApplicationContext.refresh()` | Xem scope và `@Lazy`/XML `lazy-init` |
| Bean `@Lazy` vẫn tạo khi startup | Là dependency của eager singleton | Vẽ dependency graph; xem injection point |
| `containsBeanDefinition` trả true, `containsSingleton` false | Có metadata nhưng instance chưa được tạo | Đúng với lazy hoặc BeanFactory chưa pre-instantiate |
| Prototype xuất hiện trong startup log | Eager singleton đã yêu cầu prototype làm dependency | Xem factory parameters/constructor injection |
| Bean bị tạo hai lần dù scope singleton | Có hai contexts, hai bean names, direct `new` hoặc factory method call ngoài container | Kiểm tra registry/identity/scope |
| Test từ IDE chạy được, startup prod lỗi | Different profiles/config/resource dependencies; eager validation lộ sai | Chạy integration test với config prod tương ứng |
| Startup quá chậm | Eager beans nhiều hoặc init callbacks làm I/O nặng | Profile startup, xem constructors và init methods |
| Bean lazy lỗi sau request đầu | Dependency/config chỉ được kiểm tra khi cần | Test feature path và readiness/fail-fast policy |
| `BeanCurrentlyInCreationException` | Circular dependency hoặc early bean access | Vẽ bean graph; bỏ vòng constructors |
| `@PostConstruct` gây treo startup | Callback init làm tác vụ nặng/call bean ngoài | Dời sang lifecycle coordinator phù hợp |
| `SmartInitializingSingleton` callback không chạy | Coordinator bean lazy, prototype, hoặc không được đăng ký vào BeanFactory | Đảm bảo eager managed singleton |
| Callback chạy nhưng external system chưa sẵn | Hook chỉ bảo đảm regular singleton pre-instantiation | Thiết kế explicit dependency checks và readiness |
| `@Transactional` không áp dụng ở init | Callback trước proxy interception/self-invocation | Gọi service qua managed proxy ở phase phù hợp |
| `@DependsOn` thêm vòng | Ordering A→B và B→A | Xóa vòng dependency |
| BeanPostProcessor không xử lý một bean | Bean bị tạo quá sớm trong post-processor/bootstrap | Kiểm tra early getBean/configuration static @Bean |
| FactoryBean được initialize khi type lookup | `allowEagerInit` hoặc product type introspection | Dùng API inspection đúng nhu cầu |
| `getBeanNamesForType` xuất hiện trước bean creation | Có thể đọc metadata type, không phải chứng minh instance đã có | Dùng `containsSingleton`/constructor instrumentation khi cần |
| Shutdown không cleanup resource | Không đóng ApplicationContext, thiếu destroy callback, hoặc object ngoài container | Dùng try-with-resources và lifecycle cleanup |
| Spring Boot start nhanh nhưng endpoint đầu chậm | Lazy global / deferred resource startup | Đánh giá startup vs first-use latency |

### Debug theo trình tự

1. Xác định bean name và scope.
2. Kiểm tra bean definition có `lazyInit` flag hay không.
3. Xem bean có được yêu cầu bởi một eager singleton khác không.
4. Kiểm tra loại context và thời điểm `refresh()`.
5. Xem constructor/factory method, `@PostConstruct`, custom init callbacks đang làm gì.
6. Xác định lỗi ở definition registration, bean instantiation hay lifecycle callback.
7. Kiểm tra bean đã nằm trong singleton cache chưa mà không làm phát sinh creation.
8. Xem các factories/post-processors có khởi tạo bean sớm ngoài ý định hay không.
9. Kiểm tra profile, properties, conditional beans và classpath thực.
10. Sửa nguyên nhân, sau đó chạy một context startup test và một first-use test cho beans lazy.

## 22. Testing: chứng minh eager và lazy không gây side effect ngoài ý muốn

### Test eager đã được khởi tạo trước client code

Trong ví dụ mục 12, bạn có thể thêm assert:

```java
if (!tracker.has("eager.constructed")) {
    throw new AssertionError("Eager singleton was not created");
}
if (!tracker.has("eager.initialized")) {
    throw new AssertionError("Eager init method did not run");
}
```

Đặt trong body của `try` sau khi tạo `context` và lấy `tracker`; assertions không in thêm khi pass.

### Test lazy chưa tạo trước khi được yêu cầu

```java
if (tracker.has("optional.created")) {
    throw new AssertionError("Optional lazy bean created too early");
}
```

Không gọi `context.getBean(OptionalGateway.class)` trước assertion này; nếu gọi, test đã thay đổi điều kiện đang đo.

### Test lazy bị kéo lên sớm bởi eager consumer

```java
if (!tracker.has("required.created")) {
    throw new AssertionError("Required lazy dependency was not resolved");
}
```

Bởi `EagerConsumer` là eager singleton bắt buộc nhận `RequiredIndex`, marker đó phải được ghi sau refresh.

### Test singleton identity sau first-use

```java
OptionalGateway first = context.getBean(OptionalGateway.class);
OptionalGateway second = context.getBean(OptionalGateway.class);

if (first != second) {
    throw new AssertionError("Lazy singleton identity is broken");
}
```

Đây là check identity **trong cùng một context**, không phải qua hai contexts độc lập.

### Test shutdown

Sau khi close context trong ví dụ, `tracker.has("eager.destroyed")` phải là true. Không tiếp tục gọi business methods trên beans đã bị Spring hủy, dù Java object reference còn tồn tại.

### Tránh test phụ thuộc thứ tự tạo beans không có hợp đồng

Không viết assertions kiểu “constructor A nhất định được gọi ngay trước constructor B” trừ khi A↔B có dependency/ordering contract rõ. Kiểm tra các mốc **trước refresh / sau refresh / sau first use / sau close** đáng tin và ít flaky hơn.

## 23. So sánh với Spring Boot

Spring Boot dùng Spring Framework Core Container bên dưới, nên vẫn có eager/lazy, singleton scopes và dependency-driven creation. Ngoài ra Boot có một số convenience settings như:

```properties
spring.main.lazy-initialization=true
```

Đây là **Spring Boot property**, không phải Java annotation hoặc generic property mà plain `AnnotationConfigApplicationContext` tự đọc để bật global laziness.

Boot global lazy initialization có thể cải thiện startup times trong một số ứng dụng nhưng trì hoãn lỗi cấu hình đến lần dùng đầu; đặc biệt không bảo đảm mọi infrastructure beans trở nên lazy.

Phân biệt các lifecycle hooks:

| Mốc | Thuộc tầng | Ý nghĩa chính |
| --- | --- | --- |
| `preInstantiateSingletons()` | Spring BeanFactory | Dựng regular non-lazy singletons |
| `SmartInitializingSingleton` | Spring Beans | Callback cuối pha pre-instantiation |
| `ContextRefreshedEvent` | Spring Context | Context đã initialized/refreshed |
| `SmartLifecycle.start()` | Spring Context | Thành phần có vòng đời được start qua lifecycle processor |
| `ApplicationRunner`/`CommandLineRunner` | Spring Boot | Startup runners do Boot gọi |
| `ApplicationReadyEvent` | Spring Boot | Tín hiệu Boot đã hoàn thành startup sequence thường dùng |
| Readiness/liveness probes | Spring Boot/operations | Quan sát trạng thái vận hành, không thay thế dependency graph |

**Không đồng nhất những mốc này** thành “mọi external service đã thành công”. Readiness thực tế cần kiểm tra business-critical dependencies phù hợp; pre-instantiation một object client không nhất thiết đã chứng minh network endpoint đang healthy.

## 24. Câu hỏi thường gặp

### Có phải singleton chỉ tạo một lần cho toàn JVM?

Không. Spring singleton mặc định là **mỗi bean definition trong mỗi container**. Hai ApplicationContexts có thể có hai instances cho cùng một class/bean name.

### Singleton eager có nghĩa phải gọi getBean() một lần?

Không. ApplicationContext thường pre-instantiate non-lazy singletons trong refresh; caller không phải lấy từng bean thủ công.

### @Lazy có nghĩa prototype?

Không. Lazy nói về thời điểm; prototype nói về số instances. Lazy singleton sau first-use vẫn trả cùng reference.

### Tại sao @Lazy vẫn chạy constructor lúc startup?

Bean đó có thể là dependency bắt buộc của eager singleton, hoặc bị một cơ chế startup khác yêu cầu. Kiểm tra full dependency graph và early lookups.

### Context refresh có tạo mọi bean?

Không. Prototype, lazy singleton không bị yêu cầu và một số scoped objects không thuộc bulk singleton pre-instantiation.

### Pre-instantiation có phải là constructor order theo source code?

Không nên dựa vào source order hay tên methods. Dependencies và infrastructure processing mới quyết định thứ tự cần thiết; những independent beans không nên có behavior phụ thuộc thứ tự.

### Có thể bỏ eager để che thiếu dependency không?

Không nên. Nếu dependency là bắt buộc, chuyển bean sang lazy có thể chỉ dời lỗi tới lúc phục vụ request, làm reliability tệ hơn. Hãy sửa cấu hình hoặc xác định điều kiện feature availability.

### @PostConstruct chạy khi nào so với SmartInitializingSingleton?

`@PostConstruct` là callback **cục bộ của từng bean** khi bean đang được khởi tạo; `SmartInitializingSingleton` chạy **sau giai đoạn pre-instantiation của các regular non-lazy singleton beans**.

### Một lazy singleton vừa tạo sau refresh có được gọi afterSingletonsInstantiated không?

Theo contract, **không**. Callback đó dành cho thời điểm kết thúc singleton pre-instantiation, không phải mỗi lần Spring tạo bất kỳ singleton mới về sau.

### Nếu getBeanNamesForType không lấy instance thì an toàn tuyệt đối?

Không. Một số type introspection, đặc biệt với `FactoryBean` và `allowEagerInit`, có thể làm factory được tạo để biết product type. Chọn API phù hợp nếu muốn quan sát mà không thay đổi trạng thái.

### Spring có đảm bảo singleton thread-safe?

Spring cung cấp safe publication của managed singleton initialization state, nhưng **không biến mọi mutable business state trong singleton thành thread-safe**. Nếu bean giữ counters, maps hoặc state thay đổi sau startup, bạn vẫn cần synchronization/concurrent data structures phù hợp.

### Một bean @Bean có tự được gọi initMethod nếu mình new trực tiếp từ Java?

Không. `new` gọi constructor Java, nhưng các lifecycle callbacks do Spring quản lý chỉ được gọi tự động khi object được BeanFactory tạo/quản lý theo definition có metadata phù hợp.

### @DependsOn có thay constructor dependency không?

Không. `@DependsOn` nói về ordering, còn constructor parameter injection cung cấp collaborator instance. Với dependency qua parameter đã có, thường không cần `@DependsOn` nữa.

## 25. Bài tập thực hành có đáp án

### Bài 1 — Định nghĩa và instance

Bạn đăng ký bean definition tên `mailClient` vào `DefaultListableBeanFactory` nhưng chưa gọi `getBean` hay `preInstantiateSingletons`. Có chắc instance đã tồn tại?

**Đáp án:** Không. Registration thêm metadata; chưa bắt buộc instantiate. Kiểm tra `containsBeanDefinition` và `containsSingleton` để phân biệt.

### Bài 2 — Eager singleton mặc định

Một `@Bean Repository repository()` có scope singleton và không `@Lazy`. Tạo `AnnotationConfigApplicationContext(AppConfig.class)` thành công rồi mới gọi `getBean(Repository.class)`. Constructor Repository thường chạy ở mốc nào?

**Đáp án:** Trong `refresh()` của ApplicationContext, trước khi context constructor trả về. `getBean` sau đó lấy managed singleton đã tạo.

### Bài 3 — @Lazy riêng lẻ

Bean `mailGateway` có `@Lazy` và không eager bean nào cần nó. Sau refresh, bean đã nhất thiết được tạo chưa?

**Đáp án:** Không. Thông thường nó còn deferred cho tới lần đầu được request.

### Bài 4 — Lazy bị dependency kéo lên

`A` eager singleton có constructor `A(B b)`; `B` singleton nhưng `@Lazy`. Sau refresh, `B` có thể đã được tạo không?

**Đáp án:** Có. Spring phải cung cấp `B` để tạo eager `A`, trừ khi injection được thiết kế để trì hoãn bằng proxy/provider theo hợp đồng riêng.

### Bài 5 — Prototype

`Worker` prototype là constructor dependency của eager `Scheduler` singleton. Có bao nhiêu `Worker` được tạo cho Scheduler trong constructor?

**Đáp án:** Một instance được resolve để inject tại thời điểm Scheduler được tạo, với constructor một parameter như mô tả. Không phải Spring pre-instantiate mọi prototype trên classpath.

### Bài 6 — SmartInitializingSingleton

Callback `afterSingletonsInstantiated` có bảo đảm lazy singleton chưa bao giờ được tạo không?

**Đáp án:** Không. Một lazy definition có thể đã bị eager dependency yêu cầu. Hook bảo đảm những regular non-lazy singleton beans đã qua pre-instantiation, chứ không đảm bảo lazy beans đều vắng.

### Bài 7 — Lazy coordinator

Một bean implement `SmartInitializingSingleton` nhưng được `@Lazy` và không ai cần nó khi refresh. Callback có tự chạy lúc bootstrap không?

**Đáp án:** Không. Theo Javadoc, callback không chạy cho lazy singleton chỉ được tạo sau khi BeanFactory bootstrap kết thúc.

### Bài 8 — Quan sát trạng thái

Bạn muốn biết bean đã có instance chưa mà không làm nó bị tạo. Chọn `getBean("x")` hay `containsSingleton("x")`?

**Đáp án:** `containsSingleton("x")` kiểm tra cache mà không yêu cầu tạo. `getBean("x")` có thể kích hoạt bean creation.

### Bài 9 — Thứ tự constructor

Hai singleton A, B độc lập. Có nên assert A constructor luôn chạy trước B chỉ vì `@Bean a()` xuất hiện trên source trước `@Bean b()`?

**Đáp án:** Không. Chỉ kiểm tra thứ tự khi có explicit dependency/lifecycle contract; tránh phụ thuộc thứ tự implementation.

### Bài 10 — Startup chậm

Một eager singleton constructor gọi API tải 100 MB dữ liệu. Bật `@Lazy` có phải sửa tận gốc?

**Đáp án:** Không. Nó dời chi phí/khả năng lỗi sang first use. Cần xem có phải business/bootstrap workflow đặt sai trong constructor và chọn cơ chế startup/async/readiness phù hợp.

### Bài 11 — BeanFactory thuần

Với `DefaultListableBeanFactory`, API nào giúp chủ động khởi tạo các singleton non-lazy đã đăng ký?

**Đáp án:** `preInstantiateSingletons()` trên `ConfigurableListableBeanFactory`/implementation tương ứng.

### Bài 12 — Scope và context

Hai ApplicationContexts khác nhau đều khai báo singleton bean tên `reportService` cùng class. Có bắt buộc chúng là cùng object reference không?

**Đáp án:** Không. Singleton scope là theo bean definition trong từng container.

### Bài 13 — BeanFactory vs ApplicationContext

Tại sao chương trình BeanFactory tự đăng ký definition nhưng chưa có singleton instance, trong khi ApplicationContext tạo singleton trước lời gọi `getBean`?

**Đáp án:** ApplicationContext `refresh()` thường điều phối pre-instantiation tự động; registration ở BeanFactory thấp cấp không tương đương full refresh.

### Bài 14 — Trạng thái sau close

Sau khi `try (ApplicationContext ...)` kết thúc, một Java local reference tới singleton vẫn tồn tại. Có nên dùng reference đó làm bean hoạt động bình thường?

**Đáp án:** Không. Container đã close/destroy callbacks có thể đã chạy; trạng thái runtime và resource ownership không còn được bảo đảm. Dùng reference sau close trong ví dụ chỉ để quan sát marker học thuật.

### Bài 15 — @DependsOn và vòng

`@DependsOn("b")` trên A, `@DependsOn("a")` trên B. Eager initialization có thể tuân thủ cả hai không?

**Đáp án:** Không. Đây là ordering cycle. Phải sửa dependency direction.

### Bài 16 — FactoryBean

`FactoryBean` singleton được pre-instantiated có nhất thiết đồng nghĩa product được tạo sớm không?

**Đáp án:** Không. Cần phân biệt factory object, product singleton flag, `SmartFactoryBean` eager-init contract, type introspection và dependency demand.

### Bài 17 — Spring Boot global lazy

Property `spring.main.lazy-initialization=true` có tự hoạt động trong chương trình plain Spring `new AnnotationConfigApplicationContext(AppConfig.class)` không?

**Đáp án:** Không. Đây là cấu hình Spring Boot, không phải property universal được plain ApplicationContext tự đọc để bật global laziness.

### Bài 18 — Dự đoán output

Trong ví dụ mục 12, vì sao `requiredLazy.created=true` nhưng `optionalLazy.createdBefore=false`?

**Đáp án:** Cả hai bean definitions là lazy, nhưng `EagerConsumer` eager bắt buộc cần `RequiredIndex`; không eager bean nào cần `OptionalGateway` trước khi main gọi nó.

## 26. Checklist áp dụng vào dự án

- [ ] Phân biệt **bean definition đã đăng ký** và **bean instance đã được tạo**.
- [ ] Hiểu singleton là per-bean, per-container, không mặc nhiên là per-JVM.
- [ ] Biết `ApplicationContext.refresh()` pre-instantiate regular non-lazy singletons.
- [ ] Dùng `@Lazy` hoặc XML `lazy-init` theo yêu cầu first-use, không dùng để giấu lỗi.
- [ ] Kiểm tra eager singleton nào phụ thuộc vào lazy singleton.
- [ ] Không kỳ vọng prototype được bulk pre-instantiated.
- [ ] Nếu singleton cần prototype mới theo từng job, sử dụng provider/factory phù hợp.
- [ ] Constructor và bean-local init callbacks nhẹ, deterministic và có cleanup.
- [ ] Không chạy business side effects không idempotent chỉ vì eager bean được tạo.
- [ ] Biết đúng ý nghĩa `SmartInitializingSingleton` và giới hạn của lazy beans.
- [ ] Tách startup readiness, refresh event và Boot application ready khỏi singleton pre-instantiation.
- [ ] Không khởi tạo business beans quá sớm từ BeanFactoryPostProcessor.
- [ ] FactoryBean type queries/eager semantics được cân nhắc nếu ứng dụng dùng chúng.
- [ ] Không dựa vào thứ tự các constructors độc lập cho business ordering.
- [ ] Không tự động bật circular references để xử lý dependency design sai.
- [ ] Integration tests kiểm tra trạng thái **sau refresh**, **sau first use** và **sau close**.
- [ ] Không dùng `getBean` để khảo sát xem lazy bean đã tạo hay chưa nếu mục tiêu là không tác động trạng thái.
- [ ] Có startup metrics/logs giúp phát hiện cost và root cause, không ghi secrets.
- [ ] Nếu dùng Boot global lazy, đã đánh giá startup speed đổi lấy first-request latency.
- [ ] Tài nguyên kết nối/threads có timeout và destruction/lifecycle phù hợp.

## Kết luận

**Eager singleton pre-instantiation** là hành vi mặc định quan trọng của `ApplicationContext`: Spring tạo và cấu hình các **regular singleton không lazy** ngay trong lúc refresh, phát hiện lỗi cấu hình trước khi application code bắt đầu gọi services. Điều đó khác với chỉ đăng ký `BeanDefinition` trong `DefaultListableBeanFactory`.

Cần nhớ bốn quy tắc:

1. **Singleton là identity/scope**, **eager/lazy là thời điểm**.
2. `@Lazy` không ngăn bean bị tạo sớm nếu **eager dependency** bắt buộc cần nó.
3. `preInstantiateSingletons()` hoàn tất giai đoạn bulk singleton creation và liên quan callback `SmartInitializingSingleton`, nhưng **không** có nghĩa tất cả prototype/lazy beans/external services đã sẵn sàng.
4. Constructor/init callbacks nên xử lý trách nhiệm **cục bộ của bean**, còn workflow nghiệp vụ và readiness phải có điểm kích hoạt/chính sách riêng.

Hãy đo trạng thái ở những mốc rõ ràng, tôn trọng dependency graph, viết tests cho startup và first-use, rồi chỉ dùng lazy khi việc trì hoãn thật sự phù hợp yêu cầu ứng dụng.

## Tài liệu chính thức của Spring

- [Spring Framework 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): `ApplicationContext` eager pre-instantiation, `@Lazy`/`lazy-init` và lazy dependencies của eager singletons.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton per-container/per-bean, prototype semantics.
- [Spring Framework 6.2 — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): lifecycle, init callbacks, `SmartInitializingSingleton` và `SmartLifecycle`.
- [Spring Framework 6.2 — ConfigurableListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/ConfigurableListableBeanFactory.html): `preInstantiateSingletons()`.
- [Spring Framework 6.2 — SmartInitializingSingleton Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/SmartInitializingSingleton.html): callback sau regular singleton pre-instantiation và giới hạn đối với lazy beans.
- [Spring Framework 6.2 — Using depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html): dependency ordering.
- [Spring Framework 6.2 — The IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans.html): BeanFactory, ApplicationContext và bean definitions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-002">Trì hoãn tạo bean bằng @Lazy và lazy-init</a>

<details>
<summary>Xem chi tiết</summary>

# Trì hoãn tạo bean bằng @Lazy và lazy-init trong Spring Core Container

## 1. Đặt vấn đề

Khi một ứng dụng Spring khởi động, `ApplicationContext` thông thường **tạo sẵn các singleton beans không đánh dấu lazy** trước khi hoàn tất `refresh()`. Đây là chiến lược *eager pre-instantiation*: container kiểm tra constructor, dependency injection và nhiều lỗi cấu hình ngay lúc startup, thay vì để lỗi chỉ xuất hiện khi người dùng gọi chức năng vào một thời điểm muộn hơn.

Tuy nhiên, một số bean có thể rất tốn chi phí tạo: client kết nối dịch vụ tùy chọn, bộ giải mã lớn, thành phần chỉ được sử dụng ở tính năng ít gặp, adapter dùng trong một workflow đặc biệt. Nếu eager initialization không cần thiết, Spring hỗ trợ **lazy initialization**:

- Dùng **`@Lazy`** trên Java `@Bean` method hoặc component class.
- Dùng **`lazy-init="true"`** trên XML `<bean>`.
- Đặt **`@Lazy`** trên một `@Configuration` class để áp dụng mặc định cho các `@Bean` methods của class đó.
- Đặt **`default-lazy-init="true"`** trên XML `<beans>` để đặt mặc định cho các bean definitions trong tài liệu đó.
- Dùng **`@Lazy` ở injection point** để inject *lazy-resolution proxy* thay vì resolve ngay target bean tại thời điểm tạo consumer.

Những lựa chọn này **không tương đương về tác dụng trong mọi tình huống**. Một bean được đánh dấu lazy vẫn có thể bị tạo lúc startup nếu một non-lazy singleton cần nó. Và `@Lazy` đặt trên bean definition không giống `@Lazy` trên tham số injection.

### Mục tiêu của bài

Sau bài, bạn có thể:

1. Giải thích các pha **đăng ký BeanDefinition → tạo singleton lúc refresh → lookup/use**.
2. Phân biệt eager, lazy, singleton, prototype và request/session/custom scope.
3. Cấu hình Java `@Lazy` và XML `lazy-init` đúng vị trí; hiểu giá trị `true`/`false`.
4. Giải thích tác động của eager dependencies, `@DependsOn`, XML `depends-on`, inject vào collections và các callback bootstrap.
5. Dùng `@Lazy` tại injection point để trì hoãn resolution với lazy proxy; biết các hạn chế về optional dependency, type/proxy và thời điểm lỗi.
6. So sánh `@Lazy` với `ObjectProvider<T>`, `Provider<T>`, `@Scope` và cơ chế bootstrap chạy nền.
7. Chạy một ví dụ Java 17+ với Spring Framework 6.2.19, quan sát bộ đếm constructors trước/sau lookup.
8. Quyết định nơi nên dùng lazy, cân nhắc độ trễ request đầu tiên, phát hiện lỗi muộn và quản lý tài nguyên.

**Ngữ cảnh:** Đây là chủ đề trong nhóm **Thời điểm tạo bean** của Spring Framework Core Container. Bài tập trung vào **lifecycle và thời điểm instantiation**, không nhầm với deferred configuration parsing hoặc runtime asynchronous programming.

## 2. Eager và lazy thực chất kiểm soát điều gì?

Một `BeanDefinition` là metadata về tên, Java type, scope, factory method, arguments, lazy flag và lifecycle. Nó có thể tồn tại trong registry **trước khi bean instance được tạo**.

```text
XML <bean> / Java @Bean / @ComponentScan
                |
                v
       BeanDefinitionRegistry
                |
                v
          refresh() của context
                |
                +--> tạo non-lazy singleton beans
                |
                +--> giữ lazy singleton definitions chưa tạo
                |
                v
          context đã khởi động
                |
          getBean(lazyName) / dependency lookup
                |
                v
          tạo lazy singleton nếu cần
                |
                v
          tái sử dụng instance trong context
```

Trên `ApplicationContext` thông thường:

- **Non-lazy singleton (mặc định):** Spring cố gắng pre-instantiate nó lúc container khởi động.
- **Lazy singleton:** Spring không pre-instantiate chỉ vì definition tồn tại; bean được tạo khi có yêu cầu giải quyết thực sự.
- **Prototype:** không được pre-instantiate như singleton thông thường; container tạo instance mỗi lần bean được yêu cầu, dù lazy flag có thể xuất hiện trong metadata.
- **Scoped beans (request/session/custom):** thời điểm tạo phụ thuộc scope implementation và ngữ cảnh hoạt động; không thể suy luận giống singleton.

Với lazy singleton, hai lần `getBean("reportClient")` trong **cùng context** sau khi bean đã được tạo thường trả **cùng một instance**. `@Lazy` thay *thời điểm tạo*, không thay singleton thành prototype.

### 2.1. Tại sao Spring mặc định eager?

Lợi ích quan trọng của pre-instantiation:

1. Phát hiện sớm thiếu bean và xung đột injection.
2. Phát hiện sớm lỗi constructor/factory method.
3. Kiểm tra cấu hình/tài nguyên cần thiết trước khi nhận lưu lượng.
4. Tránh chuyển chi phí khởi tạo sang request đầu tiên.
5. Làm startup diagnostics dễ lý giải.

Lazy initialization đổi lấy khả năng rút ngắn một phần startup khi bean chưa cần dùng, nhưng có thể **hoãn lỗi** và **dồn latency** sang lần sử dụng đầu tiên. Do đó, không nên bật cho mọi bean mà không đo hiệu quả.

## 3. @Lazy trên Java @Bean

Một Java configuration:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class AdapterConfig {
    @Bean
    @Lazy
    ExternalReportClient reportClient() {
        return new ExternalReportClient();
    }

    @Bean
    RequestRouter requestRouter() {
        return new RequestRouter();
    }
}
```

Đây là ví dụ **cấu trúc**; `ExternalReportClient` và `RequestRouter` phải là Java classes có constructors phù hợp và được đưa vào project. Khi configuration được nạp:

- `reportClient` có bean definition được đánh dấu **lazy**.
- `requestRouter` là non-lazy singleton theo mặc định.
- Nếu `requestRouter` không phụ thuộc vào `reportClient` và không có infrastructure nào lấy reportClient sớm, Spring có thể khởi tạo requestRouter trong refresh nhưng hoãn reportClient.
- Khi lần đầu lookup `reportClient`, Spring gọi factory method `reportClient()` và quản lý kết quả.

**Lưu ý:** annotation cần import đúng package `org.springframework.context.annotation.Lazy`. Nó khác các annotation `Lazy` của thư viện bên thứ ba hoặc tự định nghĩa.

### 3.1. @Lazy có giá trị mặc định true

```java
@Lazy
@Bean
ExternalReportClient reportClient() {
    return new ExternalReportClient();
}
```

tương đương:

```java
@Lazy(true)
@Bean
ExternalReportClient reportClient() {
    return new ExternalReportClient();
}
```

`@Lazy(false)` là một cách tường minh chọn **eager initialization**, đặc biệt khi kế thừa mặc định lazy từ configuration class hoặc các chính sách khác.

### 3.2. Vị trí annotation

`@Lazy` có thể dùng trên:

- Một type mang `@Component` hoặc stereotype hợp lệ.
- Method `@Bean`.
- Class `@Configuration`, ảnh hưởng mặc định lazy của các `@Bean` methods trong configuration class ấy.
- Field, constructor hoặc parameter ở **injection point** cùng `@Autowired`/`@Inject`, với semantics **lazy-resolution proxy**.

**Đừng gộp hai semantics:** `@Lazy` trên **definition** điều khiển target bean có bị pre-instantiate không; `@Lazy` trên **injection point** điều khiển resolution dependency khi inject consumer.

## 4. @Lazy trên component class

Ví dụ service mang stereotype:

```java
package com.example.report;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Lazy
public class ReportRenderer {
    public String render() {
        return "report";
    }
}
```

Nếu class nằm trong package mà `@ComponentScan` hoặc XML `<context:component-scan>` bao phủ, Spring đăng ký bean definition `reportRenderer` dưới dạng lazy. Bean không bị khởi tạo chỉ vì scanner tìm thấy class; khi `getBean(ReportRenderer.class)` hoặc dependency eager thực sự cần nó, Spring tạo bean.

### 4.1. @Lazy không tự bật component scanning

Class có `@Service @Lazy` nhưng **chưa được Spring đăng ký** thì không có bean definition để lazy/eager. Cần `@ComponentScan`, XML component-scan hoặc một con đường registration phù hợp.

### 4.2. @Lazy trên class không do Spring tạo

```java
ReportRenderer renderer = new ReportRenderer();
```

Object tạo qua `new` ở Java code ngoài BeanFactory không được áp lifecycle lazy của Spring. Annotation là metadata được container diễn giải khi class được Spring quản lý; nó không chặn constructor của Java.

## 5. @Lazy trên @Configuration class

`@Lazy` đặt trên class `@Configuration` thiết lập mặc định lazy cho các `@Bean` methods bên trong:

```java
@Configuration(proxyBeanMethods = false)
@Lazy
public class OptionalModuleConfig {
    @Bean
    ArchiveReader archiveReader() {
        return new ArchiveReader();
    }

    @Bean
    ReportExporter reportExporter() {
        return new ReportExporter();
    }

    @Bean
    @Lazy(false)
    HealthIndicator healthIndicator() {
        return new HealthIndicator();
    }
}
```

Đây là snippet Java minh họa; cần imports `@Bean`, `@Configuration`, `@Lazy` và ba Java types thực. Ý nghĩa:

- `archiveReader` và `reportExporter` mặc định lazy.
- `healthIndicator` override thành non-lazy nhờ `@Lazy(false)`.
- `@Configuration` class vẫn cần được Spring **đăng ký/parse** trong context.
- Không nên đồng nhất việc tạo configuration class instance với việc chạy mỗi `@Bean` factory method. Configuration metadata được xử lý trong startup dù một số target bean instances bị hoãn.

### 5.1. @Lazy trên component stereotype với @Bean methods

Spring hỗ trợ `@Lazy` trên type được đánh dấu `@Component`. Tuy nhiên, nếu muốn mặc định lazy cho **toàn bộ các factory methods**, hãy dùng `@Configuration @Lazy` đúng mục đích và test rõ imported configurations cùng các method overrides. Đừng suy luận mọi class do `@Import` từ một configuration lazy đều tự inherit lazy flag; phạm vi annotation phải được hiểu trên từng definition.

## 6. XML lazy-init="true" trên <bean>

Trong XML configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="eagerClient" class="com.example.EagerClient"/>

    <bean id="lazyClient"
          class="com.example.ExpensiveClient"
          lazy-init="true"/>
</beans>
```

Trong ví dụ này:

- `eagerClient` là non-lazy singleton theo mặc định.
- `lazyClient` có bean definition lazy và thường không được tạo trong pre-instantiation.
- `context.getBean("lazyClient")` có thể làm Spring tạo bean ngay lần đầu được gọi.
- Nếu một singleton eager khác cần `lazyClient`, Spring vẫn phải tạo lazyClient để thỏa dependency (xem mục 8).

`lazy-init` là **XML bean-definition attribute**, không phải Java field, không phải property của target class, cũng không phải cơ chế network lazy connect riêng của thư viện.

### 6.1. Explicit false

```xml
<bean id="importantClient"
      class="com.example.ImportantClient"
      lazy-init="false"/>
```

Dùng `false` khi muốn rõ rằng bean này cần được tạo lúc startup; nó đặc biệt hữu ích để override default-lazy-init của XML resource.

### 6.2. Không nhầm với scope="prototype"

```xml
<bean id="builder"
      class="com.example.Builder"
      scope="prototype"
      lazy-init="true"/>
```

Scope prototype thường đã có semantics **tạo khi yêu cầu**; nó không có singleton pre-instantiation thông thường. Đặt lazy-init vào prototype thường không đem lại điểm khác biệt như singleton. Hai lần lookup prototype vẫn có thể tạo hai instances.

## 7. XML default-lazy-init="true"

Spring hỗ trợ đặt mặc định ở root `<beans>`:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       default-lazy-init="true">

    <bean id="optionalClient"
          class="com.example.OptionalClient"/>

    <bean id="criticalMonitor"
          class="com.example.CriticalMonitor"
          lazy-init="false"/>
</beans>
```

Đây là một snippet cấu trúc; trong file thật nên khai báo schemaLocation như những XML hoàn chỉnh ở phần demo. Mọi `<bean>` không override có thể kế thừa mặc định lazy của tài liệu XML. `criticalMonitor` được đánh dấu rõ **eager**.

### 7.1. Tác dụng là theo metadata XML resource

`default-lazy-init` không phải một “global JVM flag” áp lên mọi bean của mọi context. Nó là mặc định trong tài liệu XML tương ứng, theo cơ chế parse BeanDefinitions. Những definitions có nguồn Java configuration, programmatic registration hoặc file XML khác có thể có lazy flags riêng.

Với nhiều XML imports hoặc nested `<beans>`, hãy xem cấu hình thực tế từng resource và định nghĩa. Không kết luận một flag trong `settings.xml` tự thay metadata của mọi bean trong ứng dụng.

### 7.2. Có nên bật toàn bộ?

Chỉ nên bật sau khi cân nhắc:

- Những bean nào phải fail-fast trước khi app nhận traffic?
- Có startup event listener hoặc health check cần bean ngay không?
- Tài nguyên cần thiết có được kiểm tra ở startup không?
- Request đầu tiên của feature có chịu được thời gian khởi tạo không?
- Các bean lazy có bị dependency eager kéo vào startup không?

Nếu dùng Spring Boot, còn có `spring.main.lazy-initialization=true`; đó là **tính năng Boot**, không đồng nhất với một XML `default-lazy-init` trong Spring Framework thuần.

## 8. Bẫy quan trọng: eager dependency có thể tạo lazy bean ngay lúc startup

Giả sử `ExpensiveService` là singleton `@Lazy`, nhưng `EagerController` là singleton không lazy và nhận trực tiếp `ExpensiveService` trong factory method:

```java
@Bean @Lazy
ExpensiveService expensiveService() { return new ExpensiveService(); }

@Bean
EagerController eagerController(ExpensiveService service) {
    return new EagerController(service);
}
```

Khi `ApplicationContext.refresh()` tạo controller, Spring **phải phân giải dependency** trước khi gọi factory method. Vì thế `expensiveService` bị tạo trong startup; `@Lazy` trên definition chỉ loại bean khỏi lượt tự pre-instantiate, không từ chối một yêu cầu dependency thực tế.

Lúc debug, xác định đường nào yêu cầu target: constructor, field/setter, tham số `@Bean`, `depends-on`, component infrastructure hoặc explicit `getBean`. XML `lazy-init="true"` cũng không tránh được một eager `<constructor-arg ref="..."/>`. Đừng chỉ di chuyển vị trí khai báo bean hoặc thêm `@DependsOn`: những cách đó không hoãn dependency.

Nếu consumer thực sự có thể chạy trước khi cần target, hãy cân nhắc **`@Lazy` ở injection point** (proxy trì hoãn việc tra cứu) hoặc `ObjectProvider<T>` (lookup tường minh tại thời điểm xử lý). Bài tiếp theo kiểm tra riêng chuỗi eager→lazy qua nhiều cấp phụ thuộc; mục dưới đây tập trung vào cách cấu hình lazy-resolution proxy.

## 9. @Lazy trên injection point: lazy-resolution proxy

`@Lazy` có thể đặt trên constructor parameter, field hoặc method injection point phù hợp. Ví dụ:

```java
@Component
public class CheckoutService {
    private final InvoiceApi invoiceApi;

    public CheckoutService(@Lazy InvoiceApi invoiceApi) {
        this.invoiceApi = invoiceApi;
    }

    public String issueInvoice() {
        return invoiceApi.issue();
    }
}
```

Đây là snippet minh họa; `InvoiceApi` cần là interface/bean type phù hợp và imports `@Component`, `@Lazy`. Khi Spring tạo `CheckoutService`, nó có thể inject một **lazy-resolution proxy** thay vì instantiate target ngay.

### 9.1. Hai cách đặt @Lazy khác nhau

**Đặt trên target bean definition:**

```java
@Bean
@Lazy
InvoiceApi invoiceApi() {
    return new DefaultInvoiceApi();
}
```

tác động đến **pre-instantiation của target**.

**Đặt tại injection point:**

```java
@Bean
CheckoutService checkoutService(@Lazy InvoiceApi invoiceApi) {
    return new CheckoutService(invoiceApi);
}
```

tác động đến **thời điểm dependency được resolve/đưa target thật vào consumer**.

Để target thật sự chưa được tạo lúc startup trong một chương trình đơn giản, thông thường cần bảo đảm **cả target definition là lazy** *và* **consumer nhận lazy proxy**, đồng thời không có eager bean khác buộc tạo target.

### 9.2. Khi proxy được sử dụng lần đầu

Một lần gọi business method trên proxy khiến Spring resolve target thực. Với singleton target, lần resolution sau thường dùng target singleton đã tạo. Với non-singleton scope, proxy có thể tái resolve target ở các lần gọi thích hợp theo phạm vi/lifecycle.

Proxy không phải target instance; kiểm tra `proxy.getClass() == target.getClass()` không phải cách xác nhận target đã được khởi tạo. Và việc gọi một số method trên proxy có thể kích hoạt target resolution, kể cả khi bạn chỉ định log hoặc inspect proxy.

### 9.3. Không dùng @Lazy injection proxy như optional dependency detector

Javadoc Spring nêu rõ lazy-resolution proxy **luôn được inject** tại điểm tiêm phù hợp. Nếu target dependency thực tế không có, lỗi có thể chỉ phát sinh khi gọi method trên proxy, khiến các checks kiểu `invoiceApi != null` không phát hiện thiếu dependency.

Với optional/conditional dependencies, Spring khuyến nghị những API rõ nghĩa hơn như:

```java
public CheckoutService(ObjectProvider<InvoiceApi> invoiceApiProvider) {
    this.invoiceApiProvider = invoiceApiProvider;
}
```

hoặc một interface `Provider<T>` tương ứng khi dependency và stack có hỗ trợ. Với `ObjectProvider`, bạn chủ động gọi `getIfAvailable()`/`getObject()`, kiểm tra absence và chính sách fallback. Cần import `org.springframework.beans.factory.ObjectProvider` trong đoạn Java hoàn chỉnh.

### 9.4. Proxy có những giới hạn thiết kế nào?

- Interface injection point thường phù hợp với JDK proxy; injection theo concrete class có thể cần class-based proxy.
- Final classes, final methods, visibility và module access có thể làm proxy khó hoặc không thể tạo.
- Sử dụng proxy cho method dựa trên identity/`equals`/`hashCode` cần hiểu proxy semantics.
- Không nên buộc constructor của bean consumer gọi ngay method của lazy proxy nếu mục tiêu là trì hoãn target.
- Nếu target bean là eager do cấu hình khác, lazy proxy **không đảm bảo target chưa được tạo**.
- Proxy có thể làm exception phát sinh ở request đầu tiên thay vì tại startup.

**Nguyên tắc:** lazy proxy là một cơ chế **deferred dependency resolution**, không phải phép màu biến mọi class thành lazy-safe.

## 10. Ví dụ thực hành Java 17+ với Spring Framework 6.2.19

Bài thực hành dùng:

1. **XML bean lazy-init:** so sánh eager và lazy singleton.
2. **XML default-lazy-init:** áp dụng mặc định lazy và override một bean thành eager.
3. **Java @Bean @Lazy:** xem Java config tương đương XML.
4. **Eager consumer ép lazy dependency khởi tạo:** chứng minh anti-assumption quan trọng.
5. **@Lazy tại injection point:** tạo consumer eager nhưng chỉ khởi tạo target khi thực sự gọi method.

Để kết quả quan sát **không bị nhiễu bởi logging hay đồng hồ**, mỗi Java class được kiểm chứng bằng `AtomicInteger` counter tăng trong constructor. Chương trình in giá trị counter ngay sau `refresh()`, sau `getBean()` hoặc sau method invocation.

### 10.1. Cấu trúc project

```text
spring-lazy-timing-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── LazyTimingDemo.java
        └── resources/
            ├── xml-lazy.xml
            └── xml-default-lazy.xml
```

Toàn bộ Java demo nằm trong **một source file**, dùng các nested static classes để dễ copy/paste. Trong XML, tên binary của nested classes là `demo.LazyTimingDemo$NestedClass`. Ký tự `$` ở đây biểu thị dấu đô la trong Java binary name; trong hai file XML hoàn chỉnh dưới đây, cú pháp dùng đúng tên nested class.

### 10.2. Gradle build

**`settings.gradle`**

```groovy
rootProject.name = 'spring-lazy-timing-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.LazyTimingDemo'
}
```

Dùng Java 17+, một phiên bản Gradle tương thích Java toolchains và kết nối Maven Central khi cần lấy dependency lần đầu. Không có Spring Boot, servlet container hoặc database.

### 10.3. XML #1 — một eager, một lazy singleton

**`src/main/resources/xml-lazy.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="xmlEager" class="demo.LazyTimingDemo$XmlEager"/>

    <bean id="xmlLazy"
          class="demo.LazyTimingDemo$XmlLazy"
          lazy-init="true"/>
</beans>
```

`XmlEager` phải được tạo khi context khởi động, còn `XmlLazy` sẽ chưa tạo cho đến khi lookup nếu không có dependency ép khởi tạo.

### 10.4. XML #2 — default-lazy-init và explicit override

**`src/main/resources/xml-default-lazy.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd"
       default-lazy-init="true">

    <bean id="xmlDefaultLazy"
          class="demo.LazyTimingDemo$XmlDefaultLazy"/>

    <bean id="xmlOverrideEager"
          class="demo.LazyTimingDemo$XmlOverrideEager"
          lazy-init="false"/>
</beans>
```

Vì `default-lazy-init="true"`, `xmlDefaultLazy` không pre-instantiate; vì explicit `lazy-init="false"`, `xmlOverrideEager` được tạo trong refresh.

### 10.5. Java source đầy đủ

**`src/main/java/demo/LazyTimingDemo.java`**

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class LazyTimingDemo {

    private static final AtomicInteger XML_EAGER = new AtomicInteger();
    private static final AtomicInteger XML_LAZY = new AtomicInteger();
    private static final AtomicInteger XML_DEFAULT = new AtomicInteger();
    private static final AtomicInteger XML_OVERRIDE = new AtomicInteger();

    private static final AtomicInteger JAVA_EAGER = new AtomicInteger();
    private static final AtomicInteger JAVA_LAZY = new AtomicInteger();
    private static final AtomicInteger FORCED = new AtomicInteger();
    private static final AtomicInteger PROXY_TARGET = new AtomicInteger();

    public static final class XmlEager {
        public XmlEager() {
            XML_EAGER.incrementAndGet();
        }
    }

    public static final class XmlLazy {
        public XmlLazy() {
            XML_LAZY.incrementAndGet();
        }
    }

    public static final class XmlDefaultLazy {
        public XmlDefaultLazy() {
            XML_DEFAULT.incrementAndGet();
        }
    }

    public static final class XmlOverrideEager {
        public XmlOverrideEager() {
            XML_OVERRIDE.incrementAndGet();
        }
    }

    public static final class JavaEager {
        public JavaEager() {
            JAVA_EAGER.incrementAndGet();
        }
    }

    public static final class JavaLazy {
        public JavaLazy() {
            JAVA_LAZY.incrementAndGet();
        }
    }

    public static final class ForcedDependency {
        public ForcedDependency() {
            FORCED.incrementAndGet();
        }
    }

    public static final class ForcedConsumer {
        private final ForcedDependency dependency;

        public ForcedConsumer(ForcedDependency dependency) {
            this.dependency = dependency;
        }

        public boolean ready() {
            return dependency != null;
        }
    }

    public interface JobWorker {
        String work();
    }

    public static final class RealWorker implements JobWorker {
        public RealWorker() {
            PROXY_TARGET.incrementAndGet();
        }

        @Override
        public String work() {
            return "work-done";
        }
    }

    public static final class JobConsumer {
        private final JobWorker worker;

        public JobConsumer(JobWorker worker) {
            this.worker = worker;
        }

        public String run() {
            return worker.work();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JavaBasicConfig {
        @Bean
        JavaEager javaEager() {
            return new JavaEager();
        }

        @Bean
        @Lazy
        JavaLazy javaLazy() {
            return new JavaLazy();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JavaForcedConfig {
        @Bean
        @Lazy
        ForcedDependency forcedDependency() {
            return new ForcedDependency();
        }

        @Bean
        ForcedConsumer forcedConsumer(ForcedDependency dependency) {
            return new ForcedConsumer(dependency);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProxyConfig {
        @Bean
        @Lazy
        JobWorker jobWorker() {
            return new RealWorker();
        }

        @Bean
        JobConsumer jobConsumer(@Lazy JobWorker worker) {
            return new JobConsumer(worker);
        }
    }

    public static void main(String[] args) {

        // 1. XML: eager bean tạo lúc refresh, lazy tạo khi lookup.
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("xml-lazy.xml")) {

            System.out.println("xml.eager.after-refresh="
                    + XML_EAGER.get());
            System.out.println("xml.lazy.after-refresh="
                    + XML_LAZY.get());

            XmlLazy first = context.getBean("xmlLazy", XmlLazy.class);
            System.out.println("xml.lazy.after-get=" + XML_LAZY.get());
            System.out.println("xml.lazy.singleton="
                    + (first == context.getBean(XmlLazy.class)));
        }

        // 2. XML: default lazy và override eager.
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext(
                             "xml-default-lazy.xml")) {

            System.out.println("xml.default.after-refresh="
                    + XML_DEFAULT.get());
            System.out.println("xml.override.after-refresh="
                    + XML_OVERRIDE.get());

            context.getBean(XmlDefaultLazy.class);
            System.out.println("xml.default.after-get="
                    + XML_DEFAULT.get());
        }

        // 3. Java @Bean @Lazy.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             JavaBasicConfig.class)) {

            System.out.println("java.eager.after-refresh="
                    + JAVA_EAGER.get());
            System.out.println("java.lazy.after-refresh="
                    + JAVA_LAZY.get());

            context.getBean(JavaLazy.class);
            System.out.println("java.lazy.after-get="
                    + JAVA_LAZY.get());
        }

        // 4. Non-lazy consumer ép lazy dependency tạo sớm.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             JavaForcedConfig.class)) {

            System.out.println("forced.after-refresh="
                    + FORCED.get());
            System.out.println("forced.consumer.ready="
                    + context.getBean(ForcedConsumer.class).ready());
        }

        // 5. Lazy bean + @Lazy injection point: proxy trì hoãn target.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ProxyConfig.class)) {

            System.out.println("proxy.target.after-refresh="
                    + PROXY_TARGET.get());

            JobConsumer consumer = context.getBean(JobConsumer.class);
            System.out.println("proxy.result=" + consumer.run());
            System.out.println("proxy.target.after-use="
                    + PROXY_TARGET.get());
            System.out.println("proxy.again=" + consumer.run());
            System.out.println("proxy.target.after-second-use="
                    + PROXY_TARGET.get());
        }
    }
}
```

### 10.6. Lệnh biên dịch/chạy

Trong thư mục `spring-lazy-timing-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

Cách dùng `javac LazyTimingDemo.java` đơn độc **không đủ** vì phải cung cấp Spring libraries qua compile/runtime classpath. Plugin `application` của Gradle xử lý việc build, chạy và copy `src/main/resources` lên classpath.

### 10.7. Stdout dự kiến của chương trình

```text
xml.eager.after-refresh=1
xml.lazy.after-refresh=0
xml.lazy.after-get=1
xml.lazy.singleton=true
xml.default.after-refresh=0
xml.override.after-refresh=1
xml.default.after-get=1
java.eager.after-refresh=1
java.lazy.after-refresh=0
java.lazy.after-get=1
forced.after-refresh=1
forced.consumer.ready=true
proxy.target.after-refresh=0
proxy.result=work-done
proxy.target.after-use=1
proxy.again=work-done
proxy.target.after-second-use=1
```

Chương trình chủ động in **17 dòng** này bằng `System.out.println`. Gradle/framework có thể in thêm log tùy môi trường; log không thuộc các kết quả mà chương trình tính.

### 10.8. Giải thích chính xác từng cụm kết quả

**Cụm XML một bean lazy:** `XmlEager` không có lazy flag, nên constructor chạy trong refresh và counter bằng 1. `XmlLazy` có `lazy-init="true"`, nên counter vẫn 0 sau refresh. Lookup lần đầu tạo instance (counter 1), lookup lần hai trả **cùng singleton**.

**Cụm XML mặc định:** `default-lazy-init="true"` làm `xmlDefaultLazy` chưa tạo sau refresh; `lazy-init="false"` override khiến `xmlOverrideEager` khởi tạo sớm. Lấy `XmlDefaultLazy` sau đó làm count 1.

**Cụm Java:** `JavaEager` là non-lazy singleton và `JavaLazy` được đánh dấu `@Lazy`. Sau refresh counters tương ứng 1 và 0; sau `getBean(JavaLazy.class)` count lazy thành 1.

**Cụm dependency bắt buộc:** `forcedDependency` được đánh dấu `@Lazy` nhưng `forcedConsumer` là eager singleton có factory method parameter cần `ForcedDependency`. Spring phải tạo dependency trong refresh, nên `forced.after-refresh=1`. Consumer giữ dependency non-null.

**Cụm proxy:** `jobWorker` có `@Lazy` trên bean definition; `jobConsumer` inject `@Lazy JobWorker worker` ở **tham số factory method**. Consumer được tạo sớm với lazy-resolution proxy, target `RealWorker` chưa được tạo (`counter=0`). Gọi `consumer.run()` resolve `jobWorker` lần đầu, tăng counter thành 1. Gọi lần hai vẫn dùng singleton target đã được tạo, nên counter giữ 1.

### 10.9. Những giới hạn của ví dụ

Bộ đếm chỉ ghi nhận constructors của các classes được đo. Nó **không** tuyên bố rằng Spring chưa tạo bất kỳ infrastructure bean hoặc configuration class instance nào trong startup. `@Configuration` metadata vẫn được xử lý, và container vẫn cần processors/registry.

Không dùng counter snapshot để ước lượng hiệu năng production: thời gian thực tế còn phụ thuộc I/O, DNS, network handshake, classloading, AOT, GC, cache và thread scheduling. Với production, đo bằng profiling/metrics thay vì đếm constructors.

## 11. @Lazy(false) khi muốn chủ động eager

Trên Java config:

```java
@Configuration(proxyBeanMethods = false)
@Lazy
public class MostlyLazyConfig {
    @Bean
    Formatter formatter() {
        return new Formatter();
    }

    @Bean
    @Lazy(false)
    CriticalHealthCheck criticalHealthCheck() {
        return new CriticalHealthCheck();
    }
}
```

Đây là snippet có kiểu `Formatter`/`CriticalHealthCheck` minh họa. `criticalHealthCheck` vẫn là non-lazy singleton dù class configuration đặt `@Lazy`; `formatter` theo mặc định lazy. Điều này tương tự XML `default-lazy-init="true"` với một `<bean lazy-init="false">`.

### Khi nào nên override?

- Health-check validator cần chứng minh cấu hình chính xác trước khi service nhận requests.
- Data schema/migration preflight cần fail-fast trước traffic.
- Security policy/key validation không nên trì hoãn đến request đầu tiên.
- Publisher/background scheduler cần được khởi tạo chủ động, không phải lần đầu ai đó gọi `getBean()`.
- Component cần đăng ký callback/event handler trước khi context bắt đầu phát sự kiện quan trọng.

Để tránh bất ngờ, những beans có vai trò bootstrap nên được tài liệu hóa và test rõ thời điểm init.

## 12. Eager dependency và @DependsOn

### 12.1. Dependency thực qua constructor/factory method

Nếu `CheckoutService` eager cần `PricingClient` lazy trong constructor, Spring tạo pricingClient trước khi CheckoutService được tạo. Đây là **dependency graph** bình thường.

### 12.2. depends-on là ràng buộc khởi tạo tường minh

```xml
<bean id="metricsRegistry"
      class="com.example.MetricsRegistry"
      lazy-init="true"/>

<bean id="appCoordinator"
      class="com.example.AppCoordinator"
      depends-on="metricsRegistry"/>
```

Giả sử `appCoordinator` là non-lazy singleton, Spring phải khởi tạo `metricsRegistry` trước để đáp ứng `depends-on`, mặc dù target registry là lazy. Với `@DependsOn("metricsRegistry")` trên bean/configuration tương ứng, ý nghĩa tương tự.

`depends-on` không cung cấp object reference như constructor injection; nó thể hiện **initialization prerequisite**. Nếu thực sự consumer dùng object, hãy biểu diễn dependency trực tiếp thay vì chỉ kiểm soát thứ tự.

### 12.3. @DependsOn có giúp lazy hoãn hơn không?

Không. `@DependsOn` thường làm **nhiều** beans phải init sớm hơn, không phải ít hơn. Nếu muốn trì hoãn, cần xác định vì sao bean eager đang yêu cầu target, không thêm depends-on tùy tiện.

## 13. @Lazy khác ObjectProvider<T> như thế nào?

Hai cơ chế đều hữu ích để tránh lấy dependency quá sớm, nhưng dùng cách khác nhau.

### 13.1. Lazy-resolution proxy

```java
public ReportJob(@Lazy ReportClient reportClient) {
    this.reportClient = reportClient;
}
```

Spring inject proxy để consumer giữ một dependency có cùng API; lúc gọi method, proxy resolve target. Cách này ít thay đổi business method signatures, nhưng ẩn thời điểm lookup và có các caveat của proxy.

### 13.2. ObjectProvider

```java
public ReportJob(ObjectProvider<ReportClient> provider) {
    this.provider = provider;
}

public void execute() {
    ReportClient reportClient = provider.getObject();
    reportClient.send();
}
```

Snippet minh họa, cần import `org.springframework.beans.factory.ObjectProvider` và types phù hợp. `ObjectProvider` làm **điểm lookup** tường minh. Nó hỗ trợ `getIfAvailable()`, `ifAvailable(...)`, `getIfUnique()` trong các trường hợp optional/multiple candidates.

### 13.3. Bảng đối chiếu

| Khía cạnh | @Lazy trên injection point | ObjectProvider<T> |
| --- | --- | --- |
| Hình thức consumer nhận | Proxy implement/extend type phù hợp | Provider object |
| Lúc lookup target | Khi proxy được gọi (có thể ở method đầu) | Khi caller gọi provider API |
| Optional target | Có thể nhận proxy dù target vắng; lỗi trễ | Có API `getIfAvailable()` |
| Scope prototype | Có thể tái resolve mỗi lần proxy interaction | Có thể gọi `getObject()` mỗi lần cần |
| Proxy limitations | Có: final class, method, type proxy | Thường không cần proxy target injection |
| Tính rõ ràng | Dễ dùng nhưng timing ẩn | Lookup tường minh trong code |
| Khi phù hợp | API ổn định, dependency thường có mặt | Optional/conditional/sophisticated runtime lookup |

**Quan trọng:** cả hai không nhất thiết giúp target chưa được tạo nếu bean definition của target **vẫn eager** và context đã pre-instantiate nó trong startup. Khi cần trì hoãn thật, phải xét cả **target definition** lẫn **consumer graph**.

## 14. Lazy, prototype, scoped proxy và singleton không giống nhau

### Singleton lazy

Một instance trên một container khi target lần đầu được tạo, sau đó tái sử dụng. Bean definition vẫn được đăng ký từ đầu.

### Prototype

Mỗi lần `BeanFactory.getBean(...)` được yêu cầu thường tạo instance mới. Scope không đồng nghĩa lazy annotation, dù cả hai liên quan timing.

### Request/session scopes

Những web scopes yêu cầu context/request/session hạ tầng. Có thể dùng scoped proxies để injection vào singleton; proxy trỏ tới instance theo scope đang active. Đây là cơ chế khác lazy-resolution proxy đơn giản. Đừng đặt `@Lazy` và cho rằng request scope tự vận hành trong một standalone `AnnotationConfigApplicationContext` không có web context.

### FactoryBean và SmartFactoryBean

Các beans triển khai `FactoryBean<T>` hoặc `SmartFactoryBean<T>` có semantics riêng về factory/product, type prediction và eager-init hints. Một thao tác tra type/metadata có thể có side effects trong một số trường hợp. Nếu lazy behavior bất ngờ khi dùng FactoryBean, cần kiểm tra factory vs product và hạ tầng đang thực hiện lookup.

## 15. Lazy ảnh hưởng lifecycle như thế nào?

### 15.1. Constructor và init callback

Nếu bean lazy chưa được tạo, constructor và initialization callbacks của target chưa chạy. Khi bean được lấy lần đầu, Spring tạo instance, resolve dependencies và áp BeanPostProcessors/init callbacks trước khi trả managed instance phù hợp.

### 15.2. @PostConstruct

`@PostConstruct` không chạy lúc context khởi động nếu bean lazy thực sự chưa được tạo. Khi bean được tạo, callback chạy nếu Jakarta annotation API và `CommonAnnotationBeanPostProcessor` infrastructure tương ứng có mặt.

**Không đồng nhất**: có annotation trên class chưa đủ để callback hoạt động. Và `@Lazy` không tự kích hoạt `@PostConstruct`.

### 15.3. Destroy callback

Một **singleton lazy chưa từng được tạo** không có target instance để Spring destroy. Một singleton lazy **đã được tạo và được context quản lý** có thể nhận destroy callback khi context đóng, nếu có destroy-method/`@PreDestroy`/interface tương ứng và infrastructure phù hợp.

Prototype beans thường có lifecycle destruction responsibility khác singleton; đừng suy luận container tự hủy mọi prototype khi close chỉ vì nó mang `@Lazy`.

### 15.4. Resource ownership

Nếu bean mở network connection, file, executor hoặc pool trong constructor/init:

- Trì hoãn constructor có thể dời thời điểm acquire resource.
- Lỗi network có thể chuyển từ startup sang request đầu tiên.
- Khi target đã tạo, vẫn cần đóng resource đúng cách.
- Có thể dùng explicit warm-up sau khi container sẵn sàng nếu feature quan trọng.

### 15.5. Thread safety

Singleton lazy được BeanFactory quản lý với cơ chế singleton registration/creation thread-safe ở cấp container. Nhưng class nghiệp vụ vẫn phải tự bảo đảm thread safety của mutable state. `@Lazy` không tự biến một class có mutable fields không đồng bộ thành thread-safe.

## 16. Có nên bật lazy cho bean hạ tầng?

Không nên blanket cho mọi bean. Đặc biệt, những BeanFactoryPostProcessors/BeanPostProcessors và một số framework infrastructures cần chạy trước regular singleton creation để container hoạt động đúng. Một số loại infrastructure có cơ chế đăng ký sớm hoặc cần được khởi tạo dù thông thường bean khác có thể lazy.

Nếu muốn trì hoãn resource đắt tiền mà bean hạ tầng cần để điều khiển toàn container, hãy thiết kế bootstrap boundaries rõ ràng thay vì cố ép mọi processor lazy. Không dựa vào “lazy được đánh dấu” như hợp đồng tuyệt đối khi có framework internals/eager dependencies.

### BeanPostProcessor và vấn đề eager creation

Một BeanPostProcessor custom không nên constructor-inject quá nhiều business services, vì điều đó có thể tạo chúng trước giai đoạn autoproxy/processor bình thường. Tách configuration metadata khỏi runtime collaborators khi cần. Đây không chỉ là vấn đề lazy; còn liên quan **eligibility for processing by all post-processors**.

## 17. Spring Boot lazy initialization: điểm đối chiếu

Nếu ứng dụng dùng **Spring Boot** (khác demo Spring Framework thuần), Boot hỗ trợ:

```properties
spring.main.lazy-initialization=true
```

để yêu cầu lazy initialization rộng hơn theo cơ chế `SpringApplication`. Có thể bật/tắt qua API hoặc property, và override một số beans bằng `@Lazy(false)`.

Nhưng:

1. Đây là **tính năng Boot**, không phải property tự được Spring Framework Core Container thuần xử lý.
2. Nó không bảo đảm mọi bean ở mọi infrastructure/version đều chờ đến request đầu tiên.
3. Eager dependency và bootstrap requirements vẫn có thể làm target được tạo sớm.
4. Nó có thể làm **startup nhanh hơn** ở một số hệ thống nhưng chuyển chi phí/lỗi sang lần sử dụng đầu tiên.
5. Cần quan sát memory usage vì khi traffic thực tế kích hoạt nhiều beans, heap cần đủ chứa tất cả instances.
6. Bật toàn cục nên có monitoring, warm-up phù hợp và kế hoạch fail-fast cho resources quan trọng.

Nếu project chỉ khai báo dependency `spring-context` như demo, `spring.main.lazy-initialization=true` trong một file properties không tự kích hoạt cơ chế Boot vì Boot không có trong runtime.

## 18. Lazy vs những cơ chế bị nhầm tên

| Cơ chế | Chức năng chính | Có phải thay thế @Lazy? |
| --- | --- | --- |
| `@Lazy` trên bean definition | Bỏ qua singleton eager pre-instantiation | Chính là cơ chế được học |
| XML `lazy-init="true"` | Bỏ qua eager singleton creation cho XML bean | Cơ chế tương đương trong XML |
| `@Lazy` trên injection point | Inject lazy-resolution proxy | Bổ sung, không giống target definition |
| `ObjectProvider<T>` | Lookup lúc consumer gọi provider API | Lựa chọn cho dynamic/optional lookup |
| `@Scope("prototype")` | Tạo object mỗi lần lookup | Khác scope, không chỉ timing |
| `@DependsOn` | Ép prerequisite initialization order | Thường khiến init sớm hơn |
| `DeferredImportSelector` | Trì hoãn **parse/import selection** trong configuration processing | Không trực tiếp trì hoãn bean instantiation |
| `@Bean(bootstrap=BACKGROUND)` trong Spring 6.2 | Hỗ trợ initialization trên background thread trong startup | Khác lazy, có interaction riêng |
| `@Async` | Method invocation chạy bất đồng bộ qua proxy/executor | Không điều khiển bean pre-instantiation |
| Application event listener | Phản ứng với lifecycle/events | Không phải cách mặc định để trì hoãn bean |
| Request/session scope | Định nghĩa vòng đời object theo web scope | Khác lazy flag |

### 18.1. DeferredImportSelector không phải @Lazy

`DeferredImportSelector` điều khiển **thời điểm chọn import config class** khi Spring phân tích metadata, còn `@Lazy` điều khiển **bean instance creation**. Deferred imported configuration vẫn có thể chứa eagerly instantiated singletons.

### 18.2. Background initialization trong Spring Framework 6.2

Spring Framework 6.2 có option `@Bean(bootstrap=BACKGROUND)` cho background initialization của một số beans. Đây là cơ chế nhằm **thực hiện creation trên background thread trong startup**, không phải chỉ trì hoãn đến lần lookup đầu tiên; các dependencies và việc chờ kết quả có quy tắc riêng. Chỉ dùng khi đã xem tài liệu/khả năng version và quản lý executor/lifecycle phù hợp.

Đừng bật background bootstrap vì chỉ thấy một bean startup chậm mà chưa xác định nguyên nhân và thread safety.

## 19. Các lỗi thường gặp

### Lỗi 1 — Đặt @Lazy nhưng bean vẫn tạo lúc startup

Thường vì một non-lazy singleton cần nó qua constructor, method parameter, field hoặc `depends-on`. Xem dependency graph.

### Lỗi 2 — Nghĩ @Lazy trên bean definition tự inject proxy

Không. Target bean lazy không đồng nghĩa consumer sẽ nhận proxy. Với eager consumer và dependency trực tiếp, container vẫn có thể tạo target ngay. Muốn proxy, đặt `@Lazy` tại injection point phù hợp.

### Lỗi 3 — Tưởng @Lazy ở injection point luôn làm target chưa tạo

Nếu target definition vẫn eager hoặc được bean khác lookup, target có thể đã được tạo lúc refresh. Phải xem cả bean definition lazy flag và dependency graph.

### Lỗi 4 — Không đăng ký @Component nên @Lazy không có hiệu lực

Annotation không tự tạo bean. Cần component scan/`@Import`/Java `@Bean` hoặc programmatic registration.

### Lỗi 5 — Ghi lazy-init="true" nhưng bean ở scope prototype

Prototype vốn không được pre-instantiated như singleton. Flag thường không mang ý nghĩa tối ưu tương tự singleton; cần hiểu scope khi đo.

### Lỗi 6 — Dùng default-lazy-init cho toàn hệ thống mà không warm-up

Có thể làm startup có vẻ tốt hơn nhưng request đầu tiên rất chậm; lỗi tài nguyên cũng xuất hiện muộn. Chọn critical bean eager hoặc warm-up riêng.

### Lỗi 7 — Gọi getBean trong test/bootstrapping rồi kết luận Spring không lazy

`getBean` là yêu cầu tạo target. Nếu gọi trong startup hook/test setup, bạn đã kích hoạt lazy bean. Muốn đo sau refresh, dùng constructor counters hoặc `containsSingleton` thích hợp trước lookup.

### Lỗi 8 — Nhầm containsBean với instance đã tạo

`context.containsBean("lazyClient")` có thể true vì definition tồn tại dù target instance chưa được tạo. Không dùng containsBean làm thước đo constructor execution.

### Lỗi 9 — Cấu hình @Lazy(false) nhưng vẫn tạo muộn

Kiểm tra scope, configuration registration, outer lazy lifecycle hoặc target thực tế là một bean khác. `@Lazy(false)` liên quan eager singleton pre-instantiation trong bean factories có hỗ trợ startup pre-instantiation, không ép prototype thành singleton.

### Lỗi 10 — Proxy lazy không hoạt động với final class

Một lazy-resolution proxy có thể cần tạo subclass nếu injection type không phải interface. Final type/final methods, visibility hoặc module boundaries có thể làm proxy không khả dụng. Ưu tiên interface hoặc `ObjectProvider<T>` tùy trường hợp.

### Lỗi 11 — Dùng @Lazy proxy để kiểm tra optional dependency

Proxy có thể vẫn được inject dù target không tồn tại, lỗi chỉ xuất hiện khi gọi method. Với optional target, dùng `ObjectProvider.getIfAvailable()` hoặc hợp đồng nullable/conditional rõ.

### Lỗi 12 — Đo println trong constructor mà không hiểu contexts

Hai independent ApplicationContexts có singleton caches riêng; nếu khởi động cả hai, mỗi context có thể tạo bean tương ứng của riêng nó. Không suy luận “singleton toàn JVM”.

### Lỗi 13 — Bean lazy có @PostConstruct nhưng callback không chạy lúc startup

Đó là behavior mong đợi nếu target chưa được tạo. Callback thực hiện khi bean được khởi tạo, với annotation processing phù hợp.

### Lỗi 14 — Gọi một method trên proxy chỉ để logging

Method invocation có thể trigger target resolution. Để quan sát state, dùng metrics/counters hoặc BeanFactory metadata APIs đúng mức, tránh làm thay đổi thời điểm bằng thao tác đo.

### Lỗi 15 — Trì hoãn resource critical khiến readiness sai

Health check có thể trả UP trong khi một bean chỉ dùng khi request đến chưa kết nối được database/remote service. Nếu service không thể hoạt động thiếu resource, cân nhắc tạo/validate eager hoặc thêm readiness check thực sự.

### Lỗi 16 — Cho rằng lazy luôn tiết kiệm memory lâu dài

Lazy chỉ tránh tạo bean khi **chưa dùng**. Khi sử dụng, singleton vẫn tồn tại và chiếm memory đến lúc context đóng. Có thể giảm startup memory nhưng không nhất thiết giảm steady-state heap.

### Lỗi 17 — Dùng @DependsOn để “sửa” lazy

`@DependsOn` làm dependency phải khởi tạo trước, thường càng khiến lazy target được tạo sớm hơn. Sử dụng để diễn đạt initialization prerequisite thật sự.

### Lỗi 18 — Nhầm Spring Boot property với Core XML

`spring.main.lazy-initialization` là Boot feature. Cấu hình XML thuần vẫn dùng `lazy-init` hoặc Java `@Lazy`/custom registry API.

### Lỗi 19 — Ngộ nhận @Lazy là asynchronous startup

`@Lazy` không tạo thread nền; nó hoãn việc instantiate cho đến khi cần. `@Async`/background init là cơ chế khác.

### Lỗi 20 — Lười kiểm tra lỗi dependency đến lần sử dụng đầu tiên

Lazy bean có thể fail do thiếu class, password sai, constructor exception, unresolved property hoặc resource unavailable khi được gọi lần đầu. Integration test/health checks nên chủ động exercise chức năng quan trọng.

## 20. Debug một bean có thật sự lazy không

### 20.1. Kiểm tra metadata, không tạo instance

Với `AnnotationConfigApplicationContext` hoặc một `ConfigurableApplicationContext` có BeanFactory truy cập được:

```java
var factory = context.getBeanFactory();
var definition = factory.getBeanDefinition("javaLazy");

System.out.println("lazy-flag=" + definition.isLazyInit());
System.out.println("created=" + factory.containsSingleton("javaLazy"));
```

Đoạn này dùng Java context `JavaBasicConfig` của project demo. `isLazyInit()` cho biết BeanDefinition lazy flag. `containsSingleton(name)` quan sát singleton instance đang nằm trong cache, không chủ động gọi `getBean`. Cả hai cung cấp thông tin hỗ trợ debug, nhưng với `FactoryBean`, aliases và special scopes phải phân biệt bean factory cache/bean product semantics.

### 20.2. Không dùng getBean để kiểm tra “chưa tạo”

```java
context.getBean("javaLazy");
```

**Thao tác này chính là một yêu cầu tạo/lookup bean**. Nếu target chưa tồn tại, nó có thể được tạo ngay. Bài demo deliberately đọc counters **trước** lời gọi đó.

### 20.3. Thứ tự kiểm tra

1. Bean name có được đăng ký?
2. Scope là singleton, prototype hay custom?
3. BeanDefinition lazy flag có đúng không?
4. Bean có được lookup trước khi đo không?
5. Có non-lazy singleton hoặc post-processor nào cần target không?
6. Có `@DependsOn`/`depends-on`/constructor dependency bắt buộc không?
7. Có injection point proxy được bật không?
8. Có configuration class chứa `@Lazy` nhưng method đã `@Lazy(false)` không?
9. Có framework-specific startup policies/Boot global lazy options không?
10. Có thể đo constructor/initialization callback rõ ràng trong integration test không?

### 20.4. Kiểm tra request đầu tiên

Nếu lazy bean giữ client đắt tiền, hãy đo thời gian riêng cho:

- startup toàn app;
- bước lazy target creation;
- lần business call đầu;
- những business calls kế tiếp;
- memory/threads sau warm-up;
- lỗi dưới concurrent first use.

Một cấu hình làm startup ngắn hơn nhưng tăng tail latency request đầu rất lớn có thể không phù hợp yêu cầu vận hành.

## 21. Thực hành mở rộng

### Bài 1 — Thử Java @Lazy(false)

Đặt `@Lazy` lên class `JavaBasicConfig`, sau đó thêm `@Lazy(false)` cho method `javaEager()`. Chạy lại. Giải thích vì sao bean `JavaEager` vẫn được pre-instantiate và `JavaLazy` được trì hoãn.

### Bài 2 — Bỏ lazy-init trên XML

Đổi `xmlLazy` thành `lazy-init="false"` trong `xml-lazy.xml`. Kỳ vọng `xml.lazy.after-refresh=1`; `xml.lazy.after-get` vẫn là 1 do singleton reuse.

### Bài 3 — XML default-lazy-init

Trong `xml-default-lazy.xml`, bỏ `lazy-init="false"` trên `xmlOverrideEager`. Kỳ vọng `xml.override.after-refresh=0`. Sau đó gọi `getBean(XmlOverrideEager.class)` và xác minh constructor counter tăng thành 1.

### Bài 4 — Trực tiếp inject eager consumer

Ở `ProxyConfig`, bỏ `@Lazy` trên parameter của `jobConsumer(...)` trong khi vẫn giữ `@Lazy` trên `jobWorker()`. Dự đoán `proxy.target.after-refresh=1` vì eager consumer yêu cầu target thật. Đây là bài tập quan trọng để phân biệt lazy target và lazy injection point.

### Bài 5 — Đổi JobWorker thành concrete final class

Cho `JobConsumer` constructor nhận concrete `RealWorker` đã khai báo `final` rồi đặt `@Lazy` trên parameter. Cân nhắc vì sao class-based proxy có thể không tạo được và thử chuyển sang interface `JobWorker` hoặc `ObjectProvider<RealWorker>`. Đừng dùng final type như một mặc định khi thiết kế target cần proxy.

### Bài 6 — Sử dụng ObjectProvider

Đổi `JobConsumer` nhận `ObjectProvider<JobWorker>`. Gọi `getObject().work()` trong `run()`, quan sát target creation timing. Thử `getIfAvailable()` cho optional target để hiểu semantics khi bean không tồn tại.

### Bài 7 — Đo readiness

Thêm một critical bean mô phỏng kiểm tra cấu hình ở startup và một optional bean khởi tạo đắt tiền. Chọn bean nào phải eager để fail-fast, bean nào có thể lazy, rồi viết integration test xác nhận lỗi xuất hiện đúng thời điểm dự định.

### Bài 8 — Prototype

Đổi `jobWorker` thành prototype đồng thời vẫn dùng proxy hoặc ObjectProvider. Theo dõi số constructor calls qua nhiều lần use. Phân biệt lifetime singleton proxy/consumer với các target instances theo scope.

## 22. Câu hỏi ôn tập có đáp án

**Câu 1.** Mặc định `ApplicationContext` thường tạo beans nào ngay trong startup?

**Đáp:** Các singleton non-lazy thông thường; còn scopes đặc biệt và infrastructure có quy tắc riêng.

**Câu 2.** `@Lazy` trên `@Bean` method có tạo bean vào thread khác không?

**Đáp:** Không. Nó hoãn pre-instantiation cho đến khi bean được thực sự yêu cầu.

**Câu 3.** `lazy-init="true"` là thuộc tính XML của class Java không?

**Đáp:** Không. Nó là metadata trên `<bean>` XML mà Spring BeanFactory xử lý.

**Câu 4.** Bean `@Lazy` vẫn bị tạo trong refresh có phải Spring sai không?

**Đáp:** Không. Nếu eager singleton cần bean ấy làm dependency, Spring phải tạo target để thỏa dependency.

**Câu 5.** `@Lazy` trên injection point khác trên bean definition thế nào?

**Đáp:** Injection-point `@Lazy` tạo lazy-resolution proxy; bean-definition `@Lazy` ngăn singleton pre-instantiation bình thường. Để hoãn target khi consumer eager, có thể cần cả hai.

**Câu 6.** `@Lazy` trên `@Configuration` ảnh hưởng các `@Bean` methods ra sao?

**Đáp:** Đặt mặc định lazy cho các `@Bean` methods trong class; method `@Lazy(false)` có thể override.

**Câu 7.** `<beans default-lazy-init="true">` có biến mọi bean trong JVM thành lazy không?

**Đáp:** Không. Nó thiết lập default cho definitions được parse trong XML resource tương ứng; những registration sources khác có metadata riêng.

**Câu 8.** Lazy singleton có trở thành prototype không?

**Đáp:** Không. Khi được tạo, singleton vẫn được tái sử dụng trong container.

**Câu 9.** Vì sao `containsBean("lazy")` có thể true mà constructor chưa chạy?

**Đáp:** Bean definition đã đăng ký, còn instance chưa cần tạo.

**Câu 10.** Tại sao lazy proxy không phù hợp để kiểm tra optional dependency bằng `!= null`?

**Đáp:** Proxy có thể được inject dù target vắng; lỗi xuất hiện khi invoke. Dùng `ObjectProvider.getIfAvailable()` nếu cần optional semantics rõ.

**Câu 11.** `@DependsOn` thường làm bean lazy tạo muộn hơn không?

**Đáp:** Không. Nó buộc dependency khởi tạo trước dependent bean, nên có thể kéo lazy bean vào startup.

**Câu 12.** Nếu bean chưa từng được tạo, Spring có gọi destroy callback lên target instance không?

**Đáp:** Không có instance target để destroy. Nếu singleton lazy đã tạo và được quản lý, callback phù hợp có thể chạy khi context đóng.

**Câu 13.** `spring.main.lazy-initialization=true` có chạy trong project chỉ có `spring-context` không?

**Đáp:** Không tự động. Đây là property được Spring Boot xử lý.

**Câu 14.** DeferredImportSelector có cùng nghĩa với @Lazy không?

**Đáp:** Không. DeferredImportSelector liên quan import selection trong configuration parsing; `@Lazy` liên quan bean instantiation.

**Câu 15.** Dùng global lazy có thể gây ảnh hưởng vận hành gì?

**Đáp:** Có thể giảm một phần startup work nhưng trì hoãn lỗi, tăng độ trễ sử dụng đầu, thay cách đánh giá readiness và ảnh hưởng memory footprint sau warm-up.

## 23. Checklist triển khai thực tế

- [ ] Xác định bean nào **không cần** cho startup/health-critical workflows.
- [ ] Với Java bean, đặt `@Lazy` đúng trên definition hoặc `@Configuration` scope.
- [ ] Với XML bean, đặt `lazy-init="true"`; cân nhắc `default-lazy-init` và overrides.
- [ ] Kiểm tra target có bị eager singleton khác cần trực tiếp không.
- [ ] Nếu consumer eager cần dependency lazy, cân nhắc `@Lazy` trên injection point hoặc `ObjectProvider`.
- [ ] Dùng interface/proxy-friendly type khi chọn lazy proxy.
- [ ] Không dùng lazy proxy làm optional dependency existence test.
- [ ] Kiểm tra bean scope trước khi suy luận singleton identity.
- [ ] Không suy luận lazy flag là bảo đảm không ai gọi `getBean()` trong startup.
- [ ] Đảm bảo critical configuration/resources fail-fast khi cần.
- [ ] Kiểm tra initialization/destruction lifecycle với lazy target.
- [ ] Dùng integration test thật để đo trạng thái **sau refresh, trước lookup, sau lookup**.
- [ ] Đo startup, first-use latency và memory dưới workload thực tế.
- [ ] Phân biệt Core `@Lazy`/XML lazy-init với Spring Boot property, background bootstrap và deferred parsing.

## 24. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): mặc định eager singleton, `@Lazy`, `lazy-init`, `default-lazy-init` và eager dependency.
- [Spring Framework 6.2.19 — Lazy Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Lazy.html): TYPE/METHOD/CONSTRUCTOR/PARAMETER/FIELD, config-class defaults, lazy-resolution proxies.
- [Spring Framework 6.2 — Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/6.2/core/beans/classpath-scanning.html): `@Bean`, injection-point `@Lazy` và `ObjectProvider`.
- [Spring Framework 6.2 — Using depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html): phụ thuộc khởi tạo, destroy ordering.
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): eager singleton pre-instantiation và graph injection.
- [Spring Framework 6.2 — Java Config Composition](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Lazy`, `@DependsOn` và background initialization options trong Spring 6.2.
- [Spring Boot — SpringApplication Lazy Initialization](https://docs.spring.io/spring-boot/reference/features/spring-application.html): property `spring.main.lazy-initialization`, trade-off startup/first-use.

**Tóm lại:** Spring thường pre-instantiate **non-lazy singleton beans** trong startup. `@Lazy` và XML `lazy-init="true"` trì hoãn **target bean instance creation**, nhưng một dependency từ eager singleton vẫn có thể tạo target sớm. `@Lazy` trên **injection point** tạo lazy-resolution proxy và nên được phân biệt rõ với `@Lazy` trên **bean definition**. Hãy kiểm thử bằng lifecycle/counter, cân nhắc fail-fast, tài nguyên và độ trễ lần dùng đầu trước khi áp dụng lazy trên diện rộng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-003">Bean lazy vẫn có thể được tạo bởi phụ thuộc không lazy</a>

<details>
<summary>Xem chi tiết</summary>

# Bean lazy vẫn có thể được tạo bởi phụ thuộc không lazy trong Spring Core Container

## 1. Bài học giải quyết vấn đề gì?

Một bean được đánh dấu `@Lazy` không nhất thiết “sống sót” qua quá trình khởi động mà chưa có instance. Trong Spring Framework, **singleton không lazy (*eager singleton*) có thể kéo một bean lazy được khai báo là dependency vào quá trình khởi tạo sớm**. Đây là một quy tắc trọng tâm của chủ đề **Thời điểm tạo bean** trong Spring Core Container.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    @Lazy
    PricingRules pricingRules() {
        return new PricingRules();
    }

    @Bean
    CheckoutService checkoutService(PricingRules pricingRules) {
        return new CheckoutService(pricingRules);
    }
}
```

Đây là **mẫu minh họa**, giả định `PricingRules` và `CheckoutService` có constructor hợp lệ. Có hai bean definitions:

- `pricingRules`: singleton lazy vì phương thức `@Bean` có `@Lazy`.
- `checkoutService`: singleton **không lazy** vì không có `@Lazy`.

Khi `ApplicationContext.refresh()` thực hiện pre-instantiation, Spring phải tạo `checkoutService`. Nhưng để gọi factory method `checkoutService(PricingRules pricingRules)`, container **phải cung cấp một object `PricingRules`**. Vì vậy `pricingRules` được tạo ngay trong startup, bất chấp chính bean definition mang dấu `@Lazy`.

Đó không phải bug của Spring. Nó phản ánh **hợp đồng dependency**: lazy initialization có tính ưu tiên thấp hơn nhu cầu tạo thành công một bean eager đang phụ thuộc vào nó.

### Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Giải thích đúng `singleton`, `eager`, `lazy` và `dependency-driven instantiation`.
2. Phân biệt **lazy trên bean definition** với **lazy trên injection point**.
3. Biết tại sao constructor/factory method parameter của eager bean khiến lazy dependency được tạo.
4. Nhìn được chuỗi A→B→C khi A eager kéo nhiều lazy beans.
5. Dự đoán thời điểm constructor, init callbacks và post-processors chạy.
6. Hiểu `@Lazy` trên `@Bean`, `@Component`, `@Configuration` và XML `lazy-init`.
7. Dùng `@Lazy` tại injection point để nhận một lazy-resolution proxy **khi phù hợp**.
8. Dùng `ObjectProvider<T>` để lookup tại thời điểm cần thật sự.
9. Tránh hiểu sai `@DependsOn`, `prototype` và `FactoryBean`.
10. Quan sát `BeanFactory.containsSingleton(...)` để kiểm tra trạng thái mà không vô tình tạo bean.
11. Chạy ví dụ Java 17/Spring Framework độc lập, có output deterministic, chứng minh đủ các nhánh.
12. Áp dụng cách thiết kế startup, latency, reliability và testing hợp lý trong dự án.

**Quy tắc cần thuộc:** Đánh dấu một bean `@Lazy` chỉ nói rằng Spring **không chủ động pre-instantiate nó như một singleton eager thông thường**. Nếu một bean eager **cần instance thật của bean đó**, Spring sẽ tạo nó để hoàn thành dependency.

## 2. Đọc đúng scope, lazy flag và cạnh phụ thuộc

Ba thuộc tính cần được xem **cùng lúc** khi dự đoán bean được tạo ở thời điểm nào:

| Trục | Câu hỏi | Ví dụ |
| --- | --- | --- |
| Scope | Container dùng lại hay tạo instance mới? | `singleton` thường tái sử dụng cùng bean instance trong một container; `prototype` tạo theo từng lookup |
| Lazy flag của definition | Bean có bị chủ động pre-instantiate không? | `@Lazy` bỏ qua lượt chủ động tạo singleton non-lazy |
| Cạnh dependency | Bean nào buộc phải lấy instance của bean khác? | A eager nhận B lazy trực tiếp thì vẫn phải resolve B khi tạo A |

```text
A (singleton, eager)
    └── constructor cần B (singleton, @Lazy)
                              └── B được tạo khi A khởi tạo
```

Với `AnnotationConfigApplicationContext`, một singleton bình thường không lazy thường được tạo khi `refresh()` hoàn tất. Nhưng **B có lazy flag không có nghĩa mọi caller phải chờ tới sau startup**; nó vẫn được tạo nếu một eager consumer đòi target thật. Ngược lại, A nhận proxy bằng `@Lazy` tại injection point hoặc giữ `ObjectProvider<B>` mà chưa lookup thì riêng cạnh đó không cần B ngay.

Đừng nhầm `BeanDefinition` đã đăng ký với instance đã tạo. Muốn quan sát lazy singleton trước first use, đọc `containsSingleton("beanName")` thay cho `getBean()`, vì `getBean()` chính là thao tác có thể khiến B được tạo. Các khái niệm scope và pre-instantiation đã được giải thích ở bài mở đầu; bài này theo dấu **cạnh phụ thuộc gây ra việc tạo sớm**.

## 3. Spring pre-instantiate singleton khi nào?

Trong Spring Framework, `ApplicationContext.refresh()` bao gồm giai đoạn tạo các non-lazy singleton beans, thông qua BeanFactory và cơ chế `preInstantiateSingletons()` tương ứng.

Mô hình rút gọn:

```text
Đọc @Configuration / XML / component scan
                  |
                  v
       Đăng ký BeanDefinitions
                  |
                  v
  BeanFactoryPostProcessor, BeanPostProcessor
                  |
                  v
   preInstantiateSingletons() khi refresh
                  |
        Duyệt non-lazy singletons
                  |
           Tạo eager bean A
                  |
           Resolve dependencies
                  |
          B lazy được yêu cầu?
             /          \
           Có           Không
            |             |
       Tạo B sớm       B vẫn deferred
            |
       Hoàn tất A
                  |
                  v
          Refresh hoàn tất
```

Một số infrastructure beans có lifecycle đặc biệt và được tạo trước giai đoạn này; sơ đồ chỉ minh họa logic **regular beans**.

### “Bean lazy” có nghĩa bỏ qua lượt pre-instantiation, không phải từ chối mọi yêu cầu tạo

Spring không bảo trì một lệnh cấm “không được tạo B trước request đầu tiên” cho bean `@Lazy`. Container chỉ có **lazy-init flag** tác động đến đường đi khởi tạo chủ động. Những yêu cầu thật từ `getBean`, dependency injection, lookup của một bean khác và một số extension points vẫn có thể khiến bean ấy được tạo.

### Pre-instantiation không đồng nghĩa tạo tất cả bean trên classpath

Chỉ class có bean definition phù hợp mới được container quản lý. Prototype và các scope khác không được pre-instantiate hàng loạt như regular singleton non-lazy. Nhưng chúng **có thể bị tạo theo nhu cầu khi làm dependency của eager singleton**.

### Vì sao Spring làm như vậy?

Nếu Spring bỏ qua một dependency bắt buộc chỉ vì bean được đánh lazy, bean A sẽ thiếu parameter của constructor, tạo object không hợp lệ hoặc không thể được tạo. Spring ưu tiên đảm bảo một eager bean được hoàn tất đúng dependency contract.

## 4. Ví dụ A eager, B lazy: kết quả thật sự

Cho hai POJO:

```java
public final class ExchangeRateTable {
    public ExchangeRateTable() {
        System.out.println("ExchangeRateTable constructed");
    }
}

public final class InvoiceService {
    private final ExchangeRateTable rates;

    public InvoiceService(ExchangeRateTable rates) {
        this.rates = rates;
        System.out.println("InvoiceService constructed");
    }
}
```

Đây là ví dụ **minh họa**, hai public top-level classes cần đặt trong hai file .java riêng nếu copy, không phải chương trình độc lập hoàn chỉnh.

Cấu hình:

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    @Lazy
    ExchangeRateTable exchangeRateTable() {
        return new ExchangeRateTable();
    }

    @Bean
    InvoiceService invoiceService(ExchangeRateTable table) {
        return new InvoiceService(table);
    }
}
```

Khi context được tạo và refresh:

```java
try (var context = new AnnotationConfigApplicationContext(Config.class)) {
    System.out.println("Context ready");
}
```

Các dòng từ **hai bean constructors** có thể xuất hiện trước `Context ready`; thứ tự logic cần thiết là **ExchangeRateTable phải được tạo trước khi InvoiceService constructor nhận nó**. Các bean độc lập khác hoặc logging có thể xen vào, do vậy ví dụ runnable ở mục 10 sẽ không dựa vào thứ tự log toàn cục.

Điểm rút ra: Thêm `@Lazy` vào `exchangeRateTable` mà không đổi cách `invoiceService` phụ thuộc nó **không trì hoãn được chi phí construction của rate table**.

## 5. Chuỗi phụ thuộc: eager bean có thể kéo nhiều lazy beans

Không chỉ A→B. Một eager bean có thể kéo theo cả một graph:

```text
WebFacade            eager
    |
    v
CheckoutService      lazy
    |
    v
PriceCalculator      lazy
    |
    +---> TaxTable    lazy
    |
    +---> ExchangeRates lazy
```

Nếu `WebFacade` constructor cần `CheckoutService`, service constructor cần calculator, calculator cần tax table và rates, Spring phải phân giải cả chuỗi để xây facade. Không quan trọng ở file Java nào hoặc module cấu hình nào các `@Bean` nằm.

Từ đây có hai hệ quả:

- **Đánh dấu mọi bean ở tầng dưới là `@Lazy` không đảm bảo startup nhanh hơn** nếu một root eager bean vẫn cần toàn bộ graph.
- Để thực sự trì hoãn, phải thay đổi **cạnh dependency**, ví dụ một injection point là proxy lazy hoặc bean consumer giữ provider để lấy collaborator ở thời điểm sử dụng.

### Đồ thị này khác import graph

`@Import(ServiceConfig.class)` chỉ đưa cấu hình khác vào cùng context. Nó không đồng nghĩa BeanFactory phải tạo ngay mọi singleton lazy khai báo ở config được import. Ngược lại, chỉ cần một eager bean trong toàn context cần một lazy bean thuộc module đó, nó vẫn có thể bị khởi tạo.

### Đừng dựa vào thứ tự các @Bean methods

Khi `@Bean a()` xuất hiện trước `@Bean b()` trong source code, điều đó không phải cách xác định “a được tạo trước b” theo business semantics. Spring resolve dependencies và lifecycle. Để dự đoán lazy bean bị tạo hay chưa, hãy nhìn vào **dependency graph**, không phải thứ tự methods.

## 6. @Lazy trên bean definition và @Lazy tại injection point khác nhau

Đây là khác biệt quan trọng nhất sau quy tắc ở đầu bài.

### 6.1. @Lazy trên bean definition

```java
@Bean
@Lazy
RulesEngine rulesEngine() {
    return new RulesEngine();
}
```

Nghĩa là bean `rulesEngine` không bị chọn cho eager pre-instantiation mặc định. Nhưng nếu một eager service cần `RulesEngine` như constructor parameter bình thường, Spring vẫn tạo instance và inject.

### 6.2. @Lazy trên injection point

```java
@Bean
BillingService billingService(@Lazy RulesEngine rulesEngine) {
    return new BillingService(rulesEngine);
}
```

Spring hỗ trợ `@Lazy` trên injection point: với điều kiện dependency/proxying phù hợp, nó có thể truyền **lazy-resolution proxy** thay vì tạo target `RulesEngine` ngay. Khi code thực sự gọi method trên proxy, Spring mới resolve target bean.

Điều này khác với `@Lazy` trên chính bean factory method. Hai annotation điều chỉnh hai khía cạnh:

| Nơi đánh dấu | Điều chỉnh | Kết quả điển hình |
| --- | --- | --- |
| `@Lazy` trên `@Bean rulesEngine()` | Definition `rulesEngine` là lazy | Bỏ lượt chủ động pre-instantiate, nhưng vẫn tạo nếu eager consumer cần trực tiếp |
| `@Lazy` trên tham số `billingService(@Lazy RulesEngine r)` | Cách resolve dependency tại injection point | Consumer có thể nhận proxy, target được resolve khi proxy sử dụng |
| Cả hai | Definition lazy + injection point lazy | Target có cơ hội không bị tạo khi consumer eager được xây |
| Chỉ `@Lazy` trên consumer `@Bean billingService()` | Bản thân consumer được trì hoãn | Consumer không bị pre-instantiate trực tiếp nếu không có eager dependency khác |

### 6.3. Proxy là object thật, nhưng chưa chắc target đã có

Khi Spring inject proxy, constructor consumer **đã nhận được một Java reference**. Nhưng reference đó có thể là proxy làm nhiệm vụ **resolve target khi được gọi**, không phải target instance đã khởi tạo. Do đó cần phân biệt:

- **Proxy được tạo** để inject vào eager bean.
- **Target bean được tạo** khi proxy bị dereference/invoked.
- **Eager consumer bean được tạo** trong refresh.

Nếu code gọi method trên proxy ngay trong constructor hoặc `@PostConstruct` của consumer, Spring vẫn có thể phải khởi tạo target trong startup. Annotation không có “phép màu” khiến target luôn bị trì hoãn bất kể consumer làm gì.

### 6.4. Proxy không áp dụng tùy tiện với mọi type

Một interface là target dependency thuận lợi cho JDK dynamic proxy. Với concrete class, Spring có thể cần class-based proxy với điều kiện bytecode/type phù hợp. Những kiểu `final` hoặc method `final` và các trường hợp không proxy được có giới hạn đáng kể. Mẫu runnable sẽ dùng **interface `RemoteGateway`** cho deferred proxy.

### 6.5. @Lazy injection point không biến dependency bắt buộc thành optional

Một lazy-resolution proxy có thể được inject mà không cần target ngay. Nếu target bean không tồn tại hoặc resolution thất bại, lỗi có thể chỉ xuất hiện lúc proxy được sử dụng. Vì vậy **không dùng `@Lazy` để che dependency thiếu**. Nếu collaborator là tùy chọn thật, xem `ObjectProvider`/`Optional` theo hợp đồng rõ.

## 7. ObjectProvider<T>: trì hoãn lookup bằng code tường minh

Spring `ObjectProvider<T>` cho phép consumer giữ một handle, rồi yêu cầu target khi cần:

```java
import org.springframework.beans.factory.ObjectProvider;

final class InvoiceExporter {
    private final ObjectProvider<RemoteGateway> gateways;

    InvoiceExporter(ObjectProvider<RemoteGateway> gateways) {
        this.gateways = gateways;
    }

    String export(String payload) {
        return gateways.getObject().send(payload);
    }
}
```

Mã là đoạn minh họa, cần interface `RemoteGateway` và bean definition tương ứng. Với provider, constructor không buộc phải lấy target instance; provider làm việc khi `getObject()` được gọi.

### Ưu điểm so với proxy ẩn

- Code nói rõ **khi nào** lookup xảy ra.
- Có thể sử dụng `getIfAvailable()`/`getIfUnique()` khi dependency thực sự optional.
- Dễ điều khiển phạm vi mỗi lần lookup với prototype/scope.
- Không phải giả định consumer nhận “đúng target reference ngay tại construction”.

### Giới hạn

- Nếu gọi `getObject()` trong constructor hoặc init callback của eager bean, target vẫn được tạo sớm.
- Dùng provider ở mọi chỗ có thể che dependency graph, tạo service locator style khó test.
- `getIfAvailable()` không phải cách hợp lý để im lặng bỏ qua một collaborator bắt buộc.
- Nếu có nhiều beans cùng type, `getObject()` vẫn có thể bị ambiguity; cần qualifier hoặc cấu hình resolution tương ứng.
- Provider chỉ hoãn lookup; nếu object target tạo chậm, first-use latency vẫn tồn tại.

### Khi nào chọn @Lazy injection point, khi nào provider?

| Nhu cầu | Nên cân nhắc |
| --- | --- |
| Muốn interface collaborator “trông như một bean bình thường”, resolution khi gọi method | `@Lazy` injection point |
| Cần gọi lookup một cách tường minh ở một method cụ thể | `ObjectProvider<T>` |
| Cần prototype mới cho mỗi job | `ObjectProvider<T>` thường rõ hơn |
| Constructor cần target để thiết lập invariant ngay | Trực tiếp inject bean, chấp nhận tạo sớm |
| Dependency optional thật | Provider/Optional và rõ default behavior |
| Muốn né vòng constructor mà không đổi thiết kế | Không ưu tiên cả hai; refactor graph trước |

## 8. @Lazy trên @Component và @Configuration

### @Lazy trên component class

```java
@Component
@Lazy
public class OptionalAdapter {
    // ...
}
```

Nếu component scanning đăng ký bean này và không eager bean nào yêu cầu nó, Spring thường không tạo ngay lúc refresh. Nhưng một singleton eager constructor-inject `OptionalAdapter` sẽ làm adapter bị khởi tạo như trường hợp `@Bean`.

### @Lazy trên class @Configuration

```java
@Configuration
@Lazy
class FeatureConfig {
    @Bean
    FeatureCatalog featureCatalog() {
        return new FeatureCatalog();
    }
}
```

`@Lazy` đặt ở configuration class có thể đặt default lazy-init cho các `@Bean` definitions của configuration đó theo contract annotation. Tuy vậy:

- Bean khác vẫn có thể yêu cầu `featureCatalog` và khiến nó khởi tạo sớm.
- Infrastructure/processing của `@Configuration` có quy tắc riêng; đừng mặc định configuration instance và mọi helper đều tuyệt đối lazy.
- Nếu `@Bean` đã có `@Lazy(false)` tường minh, có thể override default laziness.
- Cần test trong context thực theo version/phạm vi ứng dụng.

### Đừng nhầm với @Primary hoặc @DependsOn

`@Primary` giải quyết ưu tiên ứng viên injection. `@DependsOn` thêm ràng buộc thứ tự bean creation. Không annotation nào trong hai loại đó tự tạo lazy-resolution proxy cho injection point.

## 9. XML: lazy-init="true" cũng chịu cùng quy tắc

Spring XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="priceRules"
          class="example.PriceRules"
          lazy-init="true"/>

    <bean id="checkoutService"
          class="example.CheckoutService">
        <constructor-arg ref="priceRules"/>
    </bean>
</beans>
```

Nếu class `CheckoutService` có constructor cần `PriceRules`, eager singleton `checkoutService` khiến `priceRules` được tạo lúc startup dù XML của `priceRules` có `lazy-init="true"`.

### XML default-lazy-init

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       default-lazy-init="true">
    <bean id="a" class="example.A"/>
    <bean id="b" class="example.B"/>
</beans>
```

Đây là snippet cú pháp rút gọn, cần schema đầy đủ trong file thật. `default-lazy-init="true"` có thể thay default cho definitions trong phạm vi tương ứng; vẫn có thể khai báo `lazy-init="false"` tường minh cho một bean. Nếu bean eager khác yêu cầu A, A vẫn được tạo.

### @ImportResource không thay ngữ nghĩa lazy

Dù XML được đọc từ `ClassPathXmlApplicationContext` hay được nhập qua Java:

```java
@Configuration
@ImportResource("classpath:legacy.xml")
class RootConfig {
}
```

bean definitions sau khi đăng ký vẫn dùng chung BeanFactory, scope và dependency rules. XML resource nằm trong module riêng không giúp lazy bean tránh bị eager consumer trong Java `@Bean` inject.

## 10. Dự án runnable Java 17: ba cách quan sát khác nhau

Ví dụ đầy đủ dưới đây chứng minh trong cùng một `ApplicationContext`:

- **`requiredIndex` được đánh `@Lazy` nhưng bị `eagerCatalogService` kéo vào startup.**
- **`idleReport` lazy và không được eager bean nào yêu cầu, nên vẫn chưa tạo sau refresh.**
- **`remoteGateway` lazy nhưng `deferredConsumer` eager nhận `@Lazy RemoteGateway` tại injection point**, nên consumer có thể được tạo mà target gateway chưa được tạo; khi gọi method qua proxy mới khởi tạo.
- `containsSingleton(name)` quan sát singleton cache, không làm bean lazy bị tạo.
- Lazy singleton sau khi tạo vẫn giữ identity.
- Output xác định, không phụ thuộc thứ tự các constructors của các singleton độc lập.

### 10.1. Cấu trúc dự án

```text
lazy-dependency-demo/
├── pom.xml
└── src/main/java/demo/
    └── LazyDependencyDemo.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>lazy-dependency-demo</artifactId>
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

### 10.3. File LazyDependencyDemo.java hoàn chỉnh

```java
package demo;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

public class LazyDependencyDemo {

    public static final class Tracker {
        private final Set<String> created = new HashSet<>();

        public void mark(String name) {
            created.add(name);
        }

        public boolean has(String name) {
            return created.contains(name);
        }
    }

    public static final class RequiredIndex {
        public RequiredIndex(Tracker tracker) {
            tracker.mark("requiredIndex");
        }

        public String lookup(String key) {
            return "INDEX:" + key;
        }
    }

    public static final class EagerCatalogService {
        private final RequiredIndex index;

        public EagerCatalogService(RequiredIndex index, Tracker tracker) {
            this.index = index;
            tracker.mark("eagerCatalogService");
        }

        public String catalog(String key) {
            return index.lookup(key);
        }
    }

    public static final class IdleReport {
        public IdleReport(Tracker tracker) {
            tracker.mark("idleReport");
        }
    }

    public interface RemoteGateway {
        String send(String request);
    }

    public static final class RemoteGatewayImpl implements RemoteGateway {
        public RemoteGatewayImpl(Tracker tracker) {
            tracker.mark("remoteGateway");
        }

        @Override
        public String send(String request) {
            return "REMOTE:" + request;
        }
    }

    public static final class DeferredConsumer {
        private final RemoteGateway gateway;

        public DeferredConsumer(RemoteGateway gateway, Tracker tracker) {
            this.gateway = gateway;
            tracker.mark("deferredConsumer");
        }

        public String useGateway(String request) {
            return gateway.send(request);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public Tracker tracker() {
            return new Tracker();
        }

        @Bean
        @Lazy
        public RequiredIndex requiredIndex(Tracker tracker) {
            return new RequiredIndex(tracker);
        }

        @Bean
        public EagerCatalogService eagerCatalogService(
                RequiredIndex requiredIndex, Tracker tracker) {
            return new EagerCatalogService(requiredIndex, tracker);
        }

        @Bean
        @Lazy
        public IdleReport idleReport(Tracker tracker) {
            return new IdleReport(tracker);
        }

        @Bean
        @Lazy
        public RemoteGateway remoteGateway(Tracker tracker) {
            return new RemoteGatewayImpl(tracker);
        }

        @Bean
        public DeferredConsumer deferredConsumer(
                @Lazy RemoteGateway remoteGateway, Tracker tracker) {
            return new DeferredConsumer(remoteGateway, tracker);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Tracker tracker = context.getBean(Tracker.class);

            System.out.println("required.createdAtRefresh=" +
                    tracker.has("requiredIndex"));
            System.out.println("eager.createdAtRefresh=" +
                    tracker.has("eagerCatalogService"));
            System.out.println("idle.createdAtRefresh=" +
                    tracker.has("idleReport"));
            System.out.println("gateway.createdAtRefresh=" +
                    tracker.has("remoteGateway"));
            System.out.println("consumer.createdAtRefresh=" +
                    tracker.has("deferredConsumer"));

            System.out.println("gateway.hasSingletonBefore=" +
                    context.getBeanFactory().containsSingleton("remoteGateway"));

            EagerCatalogService eager =
                    context.getBean(EagerCatalogService.class);
            System.out.println("catalog=" + eager.catalog("B-17"));

            DeferredConsumer consumer =
                    context.getBean(DeferredConsumer.class);
            System.out.println("deferredCall=" +
                    consumer.useGateway("ping"));

            System.out.println("gateway.createdAfterCall=" +
                    tracker.has("remoteGateway"));

            IdleReport first = context.getBean(IdleReport.class);
            IdleReport second = context.getBean(IdleReport.class);

            System.out.println("idle.createdAfterLookup=" +
                    tracker.has("idleReport"));
            System.out.println("idle.sameSingleton=" +
                    (first == second));
        }
    }
}
```

### 10.4. Lệnh chạy

Cần JDK 17+, Maven và tải được dependency Spring từ repository phù hợp nếu máy chưa cache:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.LazyDependencyDemo
```

### 10.5. Output do chương trình in ra

Không tính các log Maven/Spring có thể xuất hiện ngoài các dòng do `System.out.println` tạo:

```text
required.createdAtRefresh=true
eager.createdAtRefresh=true
idle.createdAtRefresh=false
gateway.createdAtRefresh=false
consumer.createdAtRefresh=true
gateway.hasSingletonBefore=false
catalog=INDEX:B-17
deferredCall=REMOTE:ping
gateway.createdAfterCall=true
idle.createdAfterLookup=true
idle.sameSingleton=true
```

### 10.6. Phân tích chính xác từng nhóm dòng

**`required.createdAtRefresh=true`:** Dù `requiredIndex` mang `@Lazy`, Spring phải tạo nó khi pre-instantiate `eagerCatalogService`. Đây là trọng tâm bài học.

**`eager.createdAtRefresh=true`:** `eagerCatalogService` là singleton không lazy nên được tạo trong refresh. Constructor đánh dấu `Tracker`.

**`idle.createdAtRefresh=false`:** `idleReport` có `@Lazy` và không có eager consumer yêu cầu. Vì vậy constructor chưa chạy trước khi code `main` lấy nó.

**`gateway.createdAtRefresh=false`:** `remoteGateway` là singleton lazy. `deferredConsumer` eager không đòi target ngay vì tham số factory method là `@Lazy RemoteGateway`; Spring có thể inject lazy-resolution proxy. Constructor của `RemoteGatewayImpl` chưa chạy.

**`consumer.createdAtRefresh=true`:** `deferredConsumer` là eager singleton và có thể được tạo với proxy reference, trong khi remote gateway target vẫn deferred.

**`gateway.hasSingletonBefore=false`:** `containsSingleton("remoteGateway")` kiểm tra singleton cache, không gây tạo target. Proxy được inject vào `deferredConsumer` không làm singleton target `remoteGateway` xuất hiện trong registry.

**`catalog=INDEX:B-17`:** Eager service đã có instance `RequiredIndex` thật, dùng nó để trả chuỗi. Không phải gọi `getBean(RequiredIndex.class)` tại đây.

**`deferredCall=REMOTE:ping`:** Khi `DeferredConsumer.useGateway("ping")` gọi method `send` trên proxy, Spring resolve target `remoteGateway` và tạo `RemoteGatewayImpl` nếu target chưa tồn tại. Sau đó method trả chuỗi.

**`gateway.createdAfterCall=true`:** Constructor implementation đã ghi marker trong lần sử dụng đầu tiên của proxy.

**`idle.createdAfterLookup=true`:** Hai lời gọi `context.getBean(IdleReport.class)` đầu tiên và thứ hai khiến lazy bean được tạo và trả về từ singleton cache.

**`idle.sameSingleton=true`:** Lazy không làm đổi scope. Hai lượt tra cứu trong cùng context trả về cùng `IdleReport` instance.

### 10.7. Vì sao ví dụ tránh flakiness?

- `Tracker` được tạo trong cùng context; markers chỉ được đọc sau refresh.
- Không in constructor logs để suy luận thứ tự giữa các eager beans không có dependency.
- `Set` được dùng để hỏi membership, không duyệt và không phụ thuộc iteration order.
- Không có network calls, DB, clocks hoặc threads nền.
- `RemoteGateway` là **interface**, nên lazy-resolution proxy có thể hoạt động mà không gặp hạn chế của final concrete class.
- `@Configuration(proxyBeanMethods=false)` hợp lệ vì tất cả dependencies đi qua tham số `@Bean`, không direct-call các `@Bean` methods.
- Các bean là `public static` nested classes; không cần instance outer `LazyDependencyDemo`.
- Không đọc `remoteGateway` qua `getBean` trước khi kiểm tra trạng thái cache.
- Chỉ kiểm tra singleton identity sau khi bean lazy đã thực sự được tra cứu.
- `try-with-resources` đóng context và kích hoạt lifecycle cleanup phù hợp.

## 11. Thay đổi nhỏ để thấy bản chất

### Thử 1 — Xóa @Lazy ở requiredIndex

```java
@Bean
RequiredIndex requiredIndex(Tracker tracker) {
    return new RequiredIndex(tracker);
}
```

Output `required.createdAtRefresh` vẫn là `true`, nhưng **lý do** khác: bản thân bean giờ eager; không cần consumer để kéo nó lên. Bài học: chỉ nhìn output “created=true” không đủ để biết nguyên nhân; phải đọc bean definition và graph.

### Thử 2 — Đặt @Lazy trên eagerCatalogService

```java
@Bean
@Lazy
EagerCatalogService eagerCatalogService(
        RequiredIndex index, Tracker tracker) {
    return new EagerCatalogService(index, tracker);
}
```

Nếu không có eager bean khác cần service và không có lookup nào trước bước kiểm tra, cả service và required index có thể vẫn chưa tạo lúc refresh. Nhưng trong `main` của dự án này, gọi `getBean(EagerCatalogService.class)` sau đó sẽ tạo cả chuỗi.

### Thử 3 — Bỏ @Lazy tại injection point của deferredConsumer

Từ:

```java
DeferredConsumer deferredConsumer(
        @Lazy RemoteGateway gateway, Tracker tracker)
```

thành:

```java
DeferredConsumer deferredConsumer(
        RemoteGateway gateway, Tracker tracker)
```

Lúc đó eager `deferredConsumer` cần target thật, nên `remoteGateway` dù có `@Lazy` trên definition vẫn bị tạo trong refresh. `gateway.createdAtRefresh` sẽ là `true`. Đây là phép thử cho khác biệt **definition lazy** và **injection point lazy**.

### Thử 4 — Gọi proxy trong constructor

Nếu `DeferredConsumer` constructor thực thi `gateway.send("health")` ngay, nó sẽ dereference proxy khi eager consumer được tạo. `remoteGateway` lại bị tạo sớm. Proxy hoãn lúc tạo target **cho tới khi sử dụng**, không bảo đảm hoãn qua mốc startup nếu chính constructor dùng target.

### Thử 5 — Đổi IdleReport sang prototype

```java
@Bean
@Scope("prototype")
IdleReport idleReport(Tracker tracker) {
    return new IdleReport(tracker);
}
```

Mỗi lần `getBean(IdleReport.class)` có thể cho instance mới, nên `idle.sameSingleton` đổi thành `false`. Đó là thay đổi **scope**, không phải do lazy annotation. Nhớ import `org.springframework.context.annotation.Scope` nếu thử.

### Thử 6 — Làm gateway có dependency từ eager bean khác

Nếu thêm một eager bean mới constructor-inject `RemoteGateway` **không có lazy proxy**, target vẫn bị tạo trong refresh, bất kể `deferredConsumer` có injection point `@Lazy`. Lazy strategy ở **một consumer** không thể kiểm soát những consumers khác.

## 12. @Lazy trên consumer và lazy trên dependency: ma trận trường hợp

Giả sử B là collaborator, A là consumer. Không có bên thứ ba yêu cầu B.

| A consumer | B definition | Injection A→B | B thường được tạo lúc refresh? |
| --- | --- | --- | --- |
| Eager singleton | Eager singleton | Trực tiếp | Có |
| Eager singleton | Lazy singleton | Trực tiếp | **Có**, vì A cần B |
| Eager singleton | Lazy singleton | `@Lazy` injection point phù hợp | Thường **không**, target hoãn tới khi proxy sử dụng |
| Eager singleton | Lazy singleton | `ObjectProvider<B>` | Thường **không**, tới khi `getObject()` |
| Lazy singleton | Lazy singleton | Trực tiếp | Không nếu A/B chưa bị bean khác yêu cầu |
| Lazy singleton | Eager singleton | Trực tiếp | Có, B tự eager |
| Prototype A được eager singleton khác yêu cầu | Lazy singleton | Trực tiếp | Có thể có, vì prototype A bị tạo để inject rồi cần B |
| Eager A có `@DependsOn("b")` | Lazy B | Không có constructor reference | B có thể vẫn được tạo trước A do explicit ordering |

Không coi bảng là hợp đồng tuyệt đối cho mọi plugin/`FactoryBean`/custom scope/proxy configuration; nó mô tả các trường hợp regular bean thông dụng. Những custom post-processors hoặc explicit lookups trong startup có thể thay đổi thời điểm.

## 13. @DependsOn có thể buộc tạo lazy bean sớm

`@DependsOn` có mục đích **ràng buộc thứ tự khởi tạo**. Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    @Lazy
    CacheWarmup cacheWarmup() {
        return new CacheWarmup();
    }

    @Bean
    @DependsOn("cacheWarmup")
    BusinessService businessService() {
        return new BusinessService();
    }
}
```

Đây là ví dụ minh họa cần các classes `CacheWarmup` và `BusinessService`. Nếu `businessService` là eager singleton, BeanFactory cần đảm bảo `cacheWarmup` được khởi tạo trước theo metadata `dependsOn`. Một bean lazy có thể được tạo sớm dù không được truyền làm constructor parameter.

**Điểm quan trọng:** Đừng coi `@DependsOn` là một cách giảm eager creation. Ngược lại, nó có thể **thêm một cạnh bắt buộc khởi tạo**.

### @DependsOn không thay thế dependency injection

Nếu service thực sự cần đối tượng rules trong constructor, khai báo `BusinessService(Rules rules)` hoặc `@Bean businessService(Rules rules)`. `@DependsOn("rules")` chỉ làm rules được tạo trước, **không truyền reference** vào constructor.

### Không dùng @DependsOn làm workflow scheduler

Nếu `CacheWarmup` constructor gọi hàng loạt external APIs, tạo nó qua `@DependsOn` có thể kéo business side effect vào startup. Đó là vấn đề lifecycle/job orchestration chứ không phải riêng lazy flag.

## 14. Một singleton eager có thể kéo prototype bean lên sớm

`prototype` và `lazy` khác nhau. Giả sử:

```java
@Bean
@Scope("prototype")
WorkBuffer workBuffer() {
    return new WorkBuffer();
}

@Bean
BatchScheduler batchScheduler(WorkBuffer buffer) {
    return new BatchScheduler(buffer);
}
```

Nếu `batchScheduler` eager, Spring phải tạo một `WorkBuffer` prototype **để inject vào scheduler** trong startup. Không phải Spring pre-instantiated mọi prototype: chỉ tạo dependency được yêu cầu.

### Prototype không tự làm consumer nhận buffer mới mỗi lần

Một singleton scheduler constructor nhận buffer một lần và thường giữ cùng reference. Nếu mỗi job cần buffer mới, inject `ObjectProvider<WorkBuffer>` và gọi `getObject()` cho từng job, hoặc dùng factory có hợp đồng rõ.

### Lazy flag trên prototype

`@Lazy` chủ yếu hữu ích để điều khiển pre-instantiation của singleton definitions; prototype vốn thường chỉ tạo khi được yêu cầu. Đánh `@Lazy` lên prototype không biến nó thành singleton hay đổi prototype semantics. Một prototype đang là dependency của eager singleton vẫn có thể được tạo.

## 15. @Autowired field và setter injection có khác không?

Không phải cứ không dùng constructor là lazy bean thoát khỏi startup.

Ví dụ field injection:

```java
@Component
public class OrderFacade {

    @Autowired
    private TaxRules taxRules;
}
```

Nếu `OrderFacade` là eager singleton, Spring phải xử lý `@Autowired TaxRules` trong giai đoạn population. Nếu `TaxRules` bean là lazy, container vẫn có thể tạo nó để inject vào eager facade.

Setter injection:

```java
@Component
public class OrderFacade {
    private TaxRules taxRules;

    @Autowired
    void setTaxRules(TaxRules taxRules) {
        this.taxRules = taxRules;
    }
}
```

Tương tự: Spring cần có collaborator object phù hợp để gọi setter. Các class trên là **hai phiên bản minh họa**, không đặt cùng tên trong một file.

### Khi @Lazy nằm trên injection point

```java
@Autowired
@Lazy
private TaxRules taxRules;
```

hoặc:

```java
OrderFacade(@Lazy TaxRules rules) {
    this.taxRules = rules;
}
```

Spring có thể truyền lazy-resolution proxy cho injection point khi type hỗ trợ. Tuy nhiên, **constructor injection** vẫn thường dễ test, thể hiện dependency bắt buộc rõ hơn field injection. Dùng provider hoặc injection point lazy có chủ đích thay vì chuyển dependency từ constructor sang field chỉ để che graph.

## 16. Một dependency lazy có thể bị gọi sớm qua BeanPostProcessor

Spring Framework có nhiều extension points trong quá trình bootstrap:

- `BeanDefinitionRegistryPostProcessor` / `BeanFactoryPostProcessor`.
- `BeanPostProcessor`.
- `FactoryBean` và type lookup.
- Init callbacks như `@PostConstruct`.
- `SmartInitializingSingleton`.
- ApplicationContext event listeners/lifecycle processors.

Một post-processor hoặc startup callback có thể gọi `getBean("lazyBean")` hoặc một method trên proxy/provider, khiến lazy target được tạo **trước khi request bên ngoài xuất hiện**.

### Không gọi getBean bừa trong BeanFactoryPostProcessor

Các BeanFactory post-processors chạy rất sớm, trước khi toàn bộ regular BeanPostProcessors được đăng ký. Ép business bean tạo từ giai đoạn này có thể khiến bean không nhận AOP/proxy/lifecycle behavior như mong đợi.

Một startup issue có thể do việc đọc metadata bị nhầm với việc tạo instance. Nếu chỉ cần kiểm tra bean definition, dùng API definition registry; đừng gọi `getBean()`.

### SmartInitializingSingleton không bảo đảm mọi lazy bean còn vắng

Một callback `afterSingletonsInstantiated()` chạy sau lượt pre-instantiation các regular non-lazy singletons. Nhưng các lazy dependencies của eager beans đã có thể được tạo trong lượt đó. Vì vậy callback không thể khẳng định “tất cả lazy beans vẫn chưa tạo”.

Ngược lại, một `SmartInitializingSingleton` bản thân lazy và chỉ được tạo về sau thường **không** được gọi callback cuối pre-instantiation tại thời điểm tạo muộn. Không dùng nó như per-bean constructor event.

### FactoryBean và type introspection

Với `FactoryBean<T>`, container phân biệt factory object và product. Một số type-lookups cần initialize factory để xác định type, tùy flags/contract như `allowEagerInit`. Do vậy “chỉ query danh sách bean names theo type” không phải luôn 100% side-effect-free trong mọi cấu hình.

Đối với việc quan sát một singleton lazy thông thường mà không muốn tạo nó, `containsSingleton("beanName")` phù hợp hơn `getBean()`.

## 17. Tác động đến hiệu năng: startup nhanh hay request đầu tiên nhanh?

Giả sử bean `RemotePricingClient` mở kết nối, xây thread pool hoặc tải metadata khi constructor chạy. Khi bị eager service yêu cầu, chi phí vẫn nằm ở **startup** dù bean có `@Lazy`.

Nếu bạn đổi injection point sang proxy hoặc provider, chi phí chuyển sang **first use**, có thể rơi đúng vào request đầu tiên của người dùng.

| Chiến lược | Startup | First use | Khả năng phát hiện lỗi |
| --- | --- | --- | --- |
| Eager bean với dependency trực tiếp | Tạo các dependencies ngay | Có thể nhanh hơn sau startup | Fail-fast |
| Lazy definition nhưng bị eager consumer kéo lên | **Vẫn tốn chi phí lúc startup** | Thường không phải tạo lại singleton | Fail-fast cho dependency đó |
| Lazy injection-point proxy | Consumer khởi tạo mà target có thể chưa tạo | Target resolution + creation cost tại lần gọi đầu | Lỗi có thể tới muộn |
| ObjectProvider lookup tại runtime | Provider thường nhẹ | `getObject()` chịu latency và lỗi | Lỗi có thể tới muộn |
| Refactor tác vụ nặng ra khỏi constructor | Construction nhẹ hơn | Tùy lifecycle/use case design | Có thể chủ động startup validation |

**Không có lựa chọn tối ưu tuyệt đối.** Với một dependency bắt buộc cho endpoint cốt lõi, fail-fast và sẵn sàng ngay có thể quan trọng hơn tối ưu thời gian tạo context. Với feature tùy chọn ít dùng, lazy giúp giảm startup cost hợp lý.

### Nếu cần readiness chính xác

Một Java object được tạo thành công không chứng minh tất cả dịch vụ mạng/DB bên ngoài đang healthy. Hãy có timeout, health checks, readiness gating và kết nối theo hợp đồng phù hợp. Đừng lẫn singleton creation với distributed system readiness.

### Tránh đưa công việc kinh doanh vào bean constructor

Một bean eager chạy `sendWelcomeEmailToAllUsers()` trong constructor có thể gây side effects trên mỗi instance/context/restart. Nếu sau đó bạn thêm lazy proxy, tác vụ chỉ chuyển sang request đầu tiên, không thành giải pháp an toàn.

Tách business workflow sang service method/startup coordinator với policy idempotency, retry và transaction phù hợp.

## 18. Quan sát thời điểm tạo mà không làm thay đổi thí nghiệm

### containsBeanDefinition

```java
boolean definitionExists =
        context.getBeanFactory().containsBeanDefinition("remoteGateway");
```

Có bean definition không đồng nghĩa singleton instance đã được tạo.

### containsSingleton

```java
boolean instantiated =
        context.getBeanFactory().containsSingleton("remoteGateway");
```

Kiểm tra singleton cache không yêu cầu BeanFactory instantiate bean mới. Trong ví dụ chính, trước khi gọi deferred proxy, kết quả là `false`; sau khi gọi `send`, là `true` với singleton target.

### getBean

```java
RemoteGateway gateway =
        context.getBean(RemoteGateway.class);
```

Đây là **hành động yêu cầu instance**. Nếu target lazy chưa tạo, chính lời gọi này có thể khiến nó tạo ra. Dùng `getBean` rồi mới kiểm tra `containsSingleton` sẽ không chứng minh target “đã tạo sẵn trước lookup”.

### Logs và constructor counters

Logging trong constructor là phương tiện dễ nhìn nhưng có thể làm thứ tự output phụ thuộc nội bộ container. Nếu muốn assert chính xác, dùng marker/atomic counter, kiểm tra ở các mốc **trước refresh**, **sau refresh**, **sau first use** và **sau close**.

Mẫu chương trình dùng `Tracker` thay vì suy ra thứ tự từ logs. Với môi trường đa luồng thực tế, lựa chọn `ConcurrentHashMap.newKeySet()`/`AtomicInteger` tùy yêu cầu; `HashSet` của demo đủ vì không có worker threads truy cập đồng thời.

### Có thể có Spring proxy trong singleton cache không?

Có. BeanFactory có thể expose proxy thay vì raw target trong những tính năng như AOP; “bean reference” mà consumer nhận có thể là proxy. Tuy nhiên, lazy injection-point proxy là cơ chế đặc thù: nó không nhất thiết được lưu dưới **tên target bean** trong singleton cache trước khi target được yêu cầu. Tránh kết luận dựa trên Java class của reference một mình.

## 19. Các lỗi thường gặp

| Dấu hiệu | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| Đánh `@Lazy` nhưng constructor vẫn chạy lúc startup | Bean là dependency của eager singleton | Xác định consumer eager và đường injection |
| `@Lazy` trên một bean mà initMethod vẫn chạy | Bean bị dependency khác tạo sớm | Xem graph và callback |
| Eager bean bị tạo sớm ngoài ý định | Không có `@Lazy` ở consumer | Chọn consumer laziness nếu tính năng thật sự optional |
| `@Lazy` tại injection point nhưng target vẫn tạo lúc refresh | Constructor/init của consumer dùng proxy; consumer khác yêu cầu target trực tiếp | Kiểm tra tất cả đường gọi và lookups |
| @Lazy proxy không hoạt động với concrete type | Proxying limitation như final class/method | Ưu tiên interface hoặc provider; review proxy restrictions |
| `containsBean` true làm tưởng instance đã tạo | API kiểm tra resolvability, không phải cache | Dùng `containsSingleton` |
| `getBean` làm kết quả quan sát thay đổi | Lookup kích hoạt bean creation | Kiểm tra markers/cache trước lookup |
| Bean bị tạo ở nhiều contexts | Singleton scope per-container | Kiểm tra test/application contexts |
| Prototype được tạo lúc startup | Eager singleton cần một prototype dependency | Dùng provider/factory nếu cần instance từng lượt |
| Thay tất cả bằng `@Lazy` nhưng startup không nhanh | Dependency graph vẫn kéo các bean vào startup | Phân tích graph và điểm tạo chi phí cao |
| Request đầu tiên chậm sau refactor lazy | Deferred target construction/resolution | Đánh giá trade-off và warmup/readiness policy |
| Lazy target thiếu bean, lỗi xuất hiện ở request đầu | Proxy/provider hoãn resolution | Test feature path; fail-fast nếu dependency bắt buộc |
| NoUniqueBeanDefinitionException khi provider lookup | Nhiều target beans cùng type | Qualifier/Primary/naming policy |
| BeanCurrentlyInCreationException | Circular dependency/early resolution | Sửa graph, không dùng lazy để che vòng |
| @DependsOn làm lazy bean tạo sớm | Ordering metadata buộc pre-create target | Kiểm tra dependsOn declarations |
| BeanPostProcessor cảnh báo bean không eligible | Có bean được tạo quá sớm ở infrastructure phase | Tránh early getBean trong post-processors |
| FactoryBean product tạo sớm ngoài dự đoán | Eager product/FactoryBean hoặc dependency/type lookup | Xem isSingleton/isEagerInit/allowEagerInit |
| Startup coordinator dùng SmartInitializingSingleton nhưng vẫn gặp remote target lỗi | Callback gọi proxy/provider; target bắt đầu tạo | Quản lý startup validation riêng, timeout rõ |
| Test pass local nhưng fail production | Khác profile, beans, component scanning, auto-config | Test đúng root/profile/conditions |

### Quy trình debug tuần tự

1. Lấy **bean name** của target và các constructors/`@Bean` methods tạo nó.
2. Kiểm tra scope: `singleton` hay `prototype`?
3. Kiểm tra lazy flag của target definition: `@Lazy`, XML `lazy-init` hoặc global config.
4. Tìm mọi **eager singleton consumers** yêu cầu target qua constructor, setter, field, `@Bean` parameter hoặc `@DependsOn`.
5. Xác định target được inject trực tiếp, qua `@Lazy` proxy hay `ObjectProvider`.
6. Kiểm tra constructors/init callbacks có gọi proxy/provider sớm không.
7. Kiểm tra lookups từ post-processors, application events, listeners và `FactoryBean`.
8. Quan sát bằng `containsSingleton`/markers **trước** khi dùng `getBean`.
9. Xem nguyên nhân gốc exception; đừng tự kết luận có vòng khi chỉ thấy `UnsatisfiedDependencyException`.
10. Refactor dependency/use-case boundary hoặc điều chỉnh eager/lazy có chủ đích; test cả startup và first-use.

## 20. Test khi nào bean lazy được tạo

Có thể thêm assertions sau vào `main` ở ví dụ mục 10 khi `tracker` và `context` còn tồn tại, **trước khi gọi deferred consumer hoặc lookup idle report**:

```java
if (!tracker.has("requiredIndex")) {
    throw new AssertionError("Eager dependency should create requiredIndex");
}
if (tracker.has("idleReport")) {
    throw new AssertionError("Idle lazy bean created too soon");
}
if (tracker.has("remoteGateway")) {
    throw new AssertionError("Lazy proxy target resolved too soon");
}
if (context.getBeanFactory().containsSingleton("remoteGateway")) {
    throw new AssertionError("Gateway target already in singleton cache");
}
```

Sau khi gọi `consumer.useGateway("ping")`:

```java
if (!tracker.has("remoteGateway")) {
    throw new AssertionError("Gateway target not created on first use");
}
if (!context.getBeanFactory().containsSingleton("remoteGateway")) {
    throw new AssertionError("Gateway target missing from cache");
}
```

Đây là **hai đoạn bổ sung**, không phải chương trình Java độc lập. Chúng mô tả ranh giới thời điểm rất rõ: target không có trước first use, có sau khi sử dụng proxy. Tránh thêm lệnh `getBean(RemoteGateway.class)` vào giữa để không phá giả định của bài test.

### Test với nhiều profiles

Khi profile dev/prod đăng ký consumers khác nhau, lazy target có thể bị kéo lên chỉ trong một profile:

```text
dev:
  AdminTool (lazy) --> ReportEngine (lazy)
  => ReportEngine có thể còn deferred.

prod:
  StartupReportService (eager) --> ReportEngine (lazy)
  => ReportEngine bị tạo khi startup.
```

Test từ root configuration của mỗi profile, không chỉ test bean definition riêng lẻ. Kiểm tra cả giá trị của `@Conditional`/`@Profile` trong đúng môi trường.

### Unit test business logic không cần mở Spring

Nếu `EagerCatalogService` chỉ dùng `RequiredIndex` POJO, unit test có thể tự tạo collaborators và gọi method. Phần “Spring tạo instance khi nào” cần **integration test bằng context thật**, vì annotation/proxy/lifecycle thuộc container.

### Tránh assertions dựa vào số bean names

`getBeanNamesForType(RemoteGateway.class)` có thể cho biết một bean **được khai báo theo type** mà target chưa được tạo. Danh sách names không chứng minh bean instance đã tồn tại. Nếu test thời điểm khởi tạo, đọc cache/constructor markers.

## 21. Circular dependency: lazy có giải quyết được không?

Cho:

```java
class ServiceA {
    ServiceA(ServiceB b) {
    }
}
class ServiceB {
    ServiceB(ServiceA a) {
    }
}
```

Với constructor cycle A→B→A, BeanFactory thường không thể tạo đủ hai instances. `@Lazy` trên **bean definition** của B không sửa vòng nếu A eager vẫn phải tạo B. Bạn có thể dùng `@Lazy` ở injection point để trì hoãn một cạnh qua proxy trong một số trường hợp, nhưng:

- Vòng logic vẫn tồn tại.
- Proxy có thể bị dereference trong constructor/init và tái tạo vòng.
- Type/proxy limitations có thể khiến solution không hoạt động.
- Lifecycle/invariants khó reasoning hơn.

Ưu tiên trích interface chung hoặc đưa orchestration lên một coordinator:

```text
Trước:
ServiceA <----> ServiceB

Sau:
Orchestrator ---> ServiceA
             \-> ServiceB
```

`spring.main.allow-circular-references` là option của Spring Boot, không phải cách chuẩn sửa vòng của Spring Framework thuần; kể cả khi hỗ trợ early references cho một số setter cycles, constructor cycles thường vẫn bất khả giải.

## 22. FactoryBean, AOP và các trường hợp cần cẩn trọng

### FactoryBean không giống @Bean factory method

`FactoryBean<T>` có hai khái niệm khác nhau: factory object và product. BeanFactory có thể tạo/check factory object sớm để biết metadata/type, trong khi product có eager/lazy semantics phụ thuộc `FactoryBean`/`SmartFactoryBean` và consumers.

Đừng kết luận “nếu BeanDefinition là lazy thì không object liên quan nào được tạo khi startup” trong mọi combination của `FactoryBean`, type introspection và dependency resolution.

### AOP proxy và @Lazy injection-point proxy không đồng nhất

Một bean có `@Transactional`/AOP có thể được Spring expose thông qua proxy. Một `@Lazy` injection point cũng có thể dùng proxy, nhưng vì **lý do khác**: defer target resolution. Một bean có thể chịu cả hai cơ chế. Cần quan sát target instantiation ở đúng layer, không suy đoán chỉ bằng class name của reference.

### Factory method có thể chạy code ngoài ý định

Nếu Java `@Bean` method vừa tạo bean vừa gọi external API tốn thời gian, việc target bị eager consumer yêu cầu sẽ kích hoạt toàn bộ factory method ấy trong startup. Dời sang lazy proxy chuyển tác vụ tới lần đầu dùng, không thay thế timeout/retry/idempotency.

### Static @Bean và infrastructure

Những `BeanFactoryPostProcessor`/`BeanPostProcessor` có thời điểm tạo sớm, và static `@Bean` có thể phù hợp cho factory methods cung cấp một số post-processors để tránh instantiate configuration object quá sớm. Đây là mảng lifecycle hạ tầng riêng, không nên xem static là cách sửa lazy dependency thông thường.

## 23. Spring Boot global lazy và hệ quả

Spring Boot có cấu hình:

```properties
spring.main.lazy-initialization=true
```

Khi bật, Boot cố đặt nhiều bean definitions thành lazy để cải thiện thời gian startup. Tuy nhiên:

- Đây là **tùy chọn của Boot**, không phải generic setting mà plain `AnnotationConfigApplicationContext` tự đọc.
- Nếu một bean vẫn eager và phụ thuộc vào bean lazy, dependency vẫn có thể bị tạo.
- Một số infrastructure beans có ngoại lệ hoặc cần hoạt động sớm.
- Lỗi cấu hình có thể bị đẩy tới request đầu tiên.
- Bean được dùng qua proxy/provider có thể chịu extra first-use latency.

**Đừng dùng global lazy như giải pháp duy nhất** cho startup chậm do business initialization trong constructor. Hãy đo đúng cost, thiết kế startup readiness và giữ fail-fast cho các dependencies bắt buộc.

## 24. Bài tập có đáp án

### Bài 1 — Phân biệt definition và instance

Một `@Bean @Lazy Repository repository()` đã được Spring đăng ký. `containsBeanDefinition("repository")` là true. Điều này có đủ chứng minh constructor Repository đã chạy không?

**Đáp án:** Không. Definition tồn tại chỉ chứng minh container biết cách tạo. Cần quan sát instance/cache hoặc một marker; đừng tự kích hoạt `getBean()` khi muốn đo trạng thái ban đầu.

### Bài 2 — Eager A cần lazy B

Bean A là singleton eager, constructor cần B; B singleton lazy. Khi ApplicationContext refresh, B có bị tạo không?

**Đáp án:** Thông thường có. Để gọi A constructor, Spring phải resolve B instance theo dependency bắt buộc.

### Bài 3 — A là lazy

A và B đều lazy; A cần B, nhưng không bean eager nào cần A hay B. Sau refresh, có chắc chúng đã tạo chưa?

**Đáp án:** Không. Chúng có thể vẫn deferred. Khi caller yêu cầu A, Spring tạo B để hoàn thành dependency rồi tạo A.

### Bài 4 — Lazy trên parameter

Eager A nhận `@Lazy B b` (injection point) và B lazy singleton; interface B có thể proxy. Khi nào target B được tạo?

**Đáp án:** Thường tới lần đầu proxy được sử dụng; constructor A có thể chỉ nhận proxy. Nhưng nếu constructor/init A gọi method trên proxy hoặc consumer khác cần B trực tiếp, B có thể vẫn tạo trong startup.

### Bài 5 — Một consumer khác

`CheckoutService` dùng lazy proxy tới RemoteGateway, nhưng `MonitoringService` eager inject gateway trực tiếp. Gateway có còn deferred sau refresh không?

**Đáp án:** Thường không. Eager MonitoringService buộc tạo target. Lazy injection ở CheckoutService chỉ ảnh hưởng cạnh dependency riêng của CheckoutService.

### Bài 6 — @DependsOn

A eager có `@DependsOn("b")`; B lazy singleton. Có thể B vẫn được tạo lúc startup không?

**Đáp án:** Có. Depends-on yêu cầu B được khởi tạo trước A theo ordering metadata.

### Bài 7 — ObjectProvider

Consumer eager giữ `ObjectProvider<B>` nhưng không gọi `getObject()` cho tới method `serve()`. B lazy có nhất thiết tạo lúc startup không?

**Đáp án:** Không vì riêng cạnh provider đó. Khi `serve()` gọi `getObject()`, target mới được resolve, trừ khi một consumer khác tạo nó sớm.

### Bài 8 — Observability

Bạn muốn biết lazy bean `remoteGateway` đã được tạo chưa mà không làm nó tạo. Nên gọi `containsSingleton` hay `getBean`?

**Đáp án:** `containsSingleton("remoteGateway")`. `getBean` có thể tạo object, phá điều kiện quan sát.

### Bài 9 — XML

`<bean id="b" class="..." lazy-init="true"/>` và `<bean id="a" ...><constructor-arg ref="b"/></bean>` với A eager. Đảo thứ tự hai thẻ `<bean>` giúp B tránh tạo sớm không?

**Đáp án:** Không. Spring dựa vào dependency graph, không phải vị trí khai báo XML, để resolve B cho A.

### Bài 10 — Scope

B prototype, A singleton eager nhận B qua constructor. B có thể được tạo lúc refresh không? A nhận B mới mỗi lần gọi business method?

**Đáp án:** B có thể được tạo để inject khi A khởi tạo. A thường giữ instance prototype đó; không tự đổi mỗi method call. Nếu cần B mới mỗi lượt, dùng provider/factory phù hợp.

### Bài 11 — Bean missing

Một consumer eager nhận `@Lazy PaymentGateway gateway` nhưng không có PaymentGateway target bean. Context startup có thể vẫn qua trong một số lazy-proxy scenarios. Có nên chấp nhận?

**Đáp án:** Không nếu gateway là dependency bắt buộc. Target resolution failure bị dời muộn. Nên test path dùng gateway hoặc fail-fast chủ động theo reliability contract.

### Bài 12 — Vòng A↔B

A eager constructor cần B lazy, B constructor cần A. Chỉ thêm `@Lazy` trên B definition có giải quyết vòng không?

**Đáp án:** Không. A vẫn cần B ngay, B lại cần A đang xây. Sửa dependency graph hoặc trích coordinator/abstraction phù hợp.

### Bài 13 — Singleton sau lần đầu dùng

Một lazy singleton được tạo bởi `getBean` ở request đầu. Request sau lấy cùng bean từ context. Có phải mỗi lần trả instance mới không?

**Đáp án:** Không. Lazy không đổi singleton identity; các lần lookup trong cùng container dùng cùng managed singleton.

### Bài 14 — Giá phải trả

Một bean tạo chậm 800 ms. Dùng proxy để defer target. Thời gian 800 ms biến mất không?

**Đáp án:** Không. Nó có thể chuyển sang first-use latency. Cần đo và quản lý đường đi người dùng/thời gian timeout, không chỉ tối ưu metric startup.

### Bài 15 — Callback sau singleton pre-instantiation

`SmartInitializingSingleton.afterSingletonsInstantiated()` có đảm bảo chưa có lazy bean nào được tạo không?

**Đáp án:** Không. Lazy beans bị eager dependencies kéo lên đã có thể được tạo trong pre-instantiation phase.

### Bài 16 — Dự đoán output

Trong ví dụ mục 10, tại sao `required.createdAtRefresh=true` nhưng `idle.createdAtRefresh=false`?

**Đáp án:** `requiredIndex` là dependency bắt buộc của eager `eagerCatalogService`; `idleReport` không có eager consumer yêu cầu.

### Bài 17 — Quan sát proxy

Tại sao `gateway.hasSingletonBefore=false` dù `deferredConsumer` đã được tạo thành công?

**Đáp án:** Consumer giữ lazy-resolution proxy cho RemoteGateway; target singleton `remoteGateway` chưa phải instantiate tại thời điểm đó.

### Bài 18 — Refactor

Ứng dụng có eager controller cần 5 clients lazy nhưng chỉ một client được dùng thường xuyên. Nêu một thiết kế để giảm startup cost mà không che dependency bắt buộc.

**Đáp án định hướng:** Chỉ giữ eager initialization cho client cốt lõi cần fail-fast; với các integrations ít dùng, inject interface qua `@Lazy` injection point hoặc provider tại use-case boundary. Constructor không dereference lazy proxy. Viết tests cho feature path, timeout/health/readiness cho external services, theo dõi latency first-use và không dùng global lazy để che thiếu configuration.

## 25. Checklist kiểm tra trong dự án

- [ ] Đã phân biệt scope `singleton` với thời điểm `eager`/`lazy`.
- [ ] Biết `ApplicationContext.refresh()` chủ động tạo regular non-lazy singletons.
- [ ] Không hiểu `@Lazy` bean definition như lệnh cấm khởi tạo trong startup.
- [ ] Đã kiểm tra **mọi eager consumers** của target lazy.
- [ ] Kiểm tra cả constructor, `@Bean` method parameter, setter và field injection.
- [ ] Không nhầm `@Lazy` tại injection point với `@Lazy` trên bean definition.
- [ ] Khi dùng lazy proxy, type có thể proxy được và constructor/init không dereference sớm.
- [ ] Nếu dùng provider, chỉ lookup đúng thời điểm cần và xử lý lỗi minh bạch.
- [ ] Không dùng provider/optional lookup để âm thầm bỏ qua mandatory dependency.
- [ ] Không dùng `@DependsOn` như cách giữ một bean lazy.
- [ ] Không mong prototype không bao giờ xuất hiện khi startup.
- [ ] Với `FactoryBean`, phân biệt factory instance, product và eager type lookup.
- [ ] Không ép business beans khởi tạo sớm từ BeanFactory post-processors.
- [ ] Đo lazy state qua `containsSingleton` hoặc markers trước first-use.
- [ ] Không gọi `getBean` khi chỉ muốn hỏi target lazy đã tạo hay chưa.
- [ ] Integration tests bao gồm startup, first-use, extra eager consumer và failure paths.
- [ ] Kiểm soát lỗi cấu hình bị trì hoãn bởi lazy proxy/provider.
- [ ] Có kế hoạch đối với first-use latency và startup readiness.
- [ ] Không phụ thuộc vào constructor log ordering giữa beans độc lập.
- [ ] Giữ constructor và init callbacks nhẹ, không đặt workflow không idempotent.
- [ ] Không dùng lazy đơn thuần để che circular dependency.
- [ ] Đã đánh giá ngoại lệ của Boot global lazy nếu chạy trên Boot.

## Kết luận

**Một bean có `@Lazy` vẫn có thể được tạo bởi phụ thuộc không lazy** vì Spring phải hoàn thành dependency của eager consumer trong khi `ApplicationContext.refresh()` tạo các singleton không lazy.

Ba tình huống cần phân biệt:

1. **Lazy bean definition + eager consumer inject trực tiếp:** Spring vẫn tạo target trong startup.
2. **Lazy injection-point proxy:** eager consumer có thể được tạo với proxy, còn target hoãn tới khi proxy được sử dụng — trừ khi một đường khác tạo nó sớm.
3. **`ObjectProvider<T>`:** consumer giữ provider và yêu cầu target khi cần, phù hợp nếu dynamic lookup là chủ đích.

`@Lazy` không đổi singleton scope, không làm dependency bắt buộc thành optional, không xóa construction cost và không tự sửa circular dependencies. Hãy đọc toàn bộ bean graph, kiểm thử bằng context thật và đo cả **startup** lẫn **first use** trước khi quyết định mức laziness.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): cơ chế lazy và trường hợp eager singleton buộc lazy dependency khởi tạo.
- [Spring Framework 6.2 — @Lazy Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Lazy.html): lazy bean definitions, lazy-resolution proxy cho injection points và các giới hạn.
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor/property dependency resolution.
- [Spring Framework 6.2 — ObjectProvider API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): lazy/dynamic lookup.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton/prototype semantics.
- [Spring Framework 6.2 — Bean Dependencies: depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html): ordering metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-004">Proxy tại điểm tiêm @Lazy và thời điểm lỗi xuất hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Proxy tại điểm tiêm `@Lazy` và thời điểm lỗi xuất hiện trong Spring Framework

## 1. Bài toán: bean được tạo lúc nào, và lỗi bị trì hoãn đến khi nào?

Spring IoC Container quản lý việc đăng ký bean definitions, giải quyết dependency và khởi tạo bean. Theo mặc định, nhiều singleton trong một `ApplicationContext` được tạo ngay khi context khởi động. Điều này có ích vì ứng dụng sớm phát hiện lỗi cấu hình, dependency và initialization.

Tuy nhiên, đôi khi một collaborator chỉ thực sự cần trong một nhánh hiếm gặp, mất thời gian khởi tạo hoặc có chi phí kết nối đến hệ thống bên ngoài. Spring cung cấp `@Lazy` để kiểm soát thời điểm tạo bean và **lazy-resolution proxy** để trì hoãn việc giải quyết dependency ở **điểm tiêm (injection point)**.

Hai đoạn code trông gần giống nhưng có ý nghĩa khác nhau:

```java
// 1. @Lazy trên bean definition.
@Bean
@Lazy
PaymentClient paymentClient() {
    return new RemotePaymentClient();
}
```

```java
// 2. @Lazy trên injection point.
@Bean
CheckoutService checkoutService(@Lazy PaymentClient client) {
    return new CheckoutService(client);
}
```

Các đoạn là **trích đoạn minh họa**, cần khai báo đầy đủ imports và domain classes mới compile. Trường hợp (1) điều khiển **có pre-instantiate bean `paymentClient` hay không**. Trường hợp (2) yêu cầu Spring cung cấp **một proxy** tại tham số `client`, để giải quyết bean thực khi proxy được sử dụng.

Đây không phải hai từ đồng nghĩa:

- Bean definition có `@Lazy` nhưng được **tiêm trực tiếp** vào eager singleton thì Spring vẫn có thể phải tạo bean ngay khi context khởi động.
- Injection point có `@Lazy` nhưng target bean là **eager singleton thông thường** thì target vẫn có thể đã được tạo trước lần gọi proxy đầu tiên.
- Khi cả target definition **và** injection point đều lazy, trong bối cảnh không có đường dependency/eager lookup khác kéo target vào, target có thể được tạo khi proxy thực sự được gọi.
- Nếu target bean **không tồn tại**, lazy proxy ở injection point vẫn có thể được tiêm; lỗi chỉ lộ ra khi proxy được gọi.
- Nếu target bean được chọn nhưng tạo thất bại, lỗi bean creation có thể xuất hiện trong lời gọi proxy đầu tiên — thay vì lúc tạo consumer.

Mục tiêu bài học:

1. Phân biệt `@Lazy` ở `@Bean`/`@Component`/`@Configuration` với `@Lazy` ở field, constructor parameter và method parameter injection.
2. Hiểu một lazy-resolution proxy làm gì và không làm gì.
3. Biết cách suy luận các thời điểm `register`, `refresh`, constructor, init callback, proxy invocation và destroy.
4. Nhận diện các lỗi startup, injection, first invocation và later invocation.
5. Hiểu quy tắc singleton target được cache sau khi resolve và non-singleton có thể được resolve lại mỗi lần truy cập.
6. Phân biệt lazy dependency với dependency tùy chọn; sử dụng `ObjectProvider` đúng khi không có bean.
7. Chạy ví dụ Java 17+ / Spring Framework 6.2 có nhiều context kiểm chứng các biến thể.
8. Có phương pháp kiểm thử, debug và áp dụng an toàn cho production.

## 2. Chọn đúng vị trí `@Lazy` để kiểm soát thời điểm lỗi

`@Lazy` trên **bean definition** (`@Bean`, component, configuration) quyết định liệu singleton target có bị eager pre-instantiation thông thường không. `@Lazy` trên **injection point** (tham số constructor/`@Bean`, field hoặc setter được Spring xử lý) yêu cầu một lazy-resolution **proxy** để consumer có thể tạo trước lúc cần target.

| Target bean | Cạnh injection vào eager consumer | Kết quả quan sát |
| --- | --- | --- |
| Singleton eager | `@Lazy Gateway` proxy | Proxy được tiêm, nhưng target vẫn có thể tạo ngay trong refresh |
| Singleton `@Lazy` | `Gateway` trực tiếp | Target bị tạo sớm để hoàn tất consumer |
| Singleton `@Lazy` | `@Lazy Gateway` proxy | Target có thể chờ đến lần gọi proxy đầu tiên |
| Không có target hợp lệ | `@Lazy Gateway` proxy | Proxy vẫn có thể được tiêm; lỗi thiếu dependency chuyển sang lúc invoke |
| Prototype | Lazy proxy | Target có thể được resolve lại cho từng method invocation; tránh dùng cho nhiều calls cần chung mutable state |

Ví dụ `@Bean @Lazy Gateway gateway()` kết hợp `consumer(@Lazy Gateway gateway)` chỉ thật sự hoãn target khi **không có đường eager khác** lấy bean trước. Cần dùng interface hoặc type có thể proxy; final class/method và những giới hạn của proxy có thể gây lỗi **ngay tại injection**, không chờ first use.

Bài này phân tích kỹ **vị trí và loại lỗi**: proxy creation error, bean lookup error, bean creation error và business invocation error là những tình huống khác nhau. Kiểm tra mốc refresh so với lần gọi đầu bằng marker hoặc `containsSingleton` trước khi dereference proxy.

## 3. Cơ chế lazy-resolution proxy tại điểm tiêm

Spring tạo một object đứng giữa consumer và target. Consumer giữ reference đến proxy; proxy giữ cơ chế tìm target thông qua bean factory/dependency descriptor.

```text
                     ApplicationContext.refresh()
                                |
                                v
                    EagerConsumer được tạo
                                |
                                v
            @Lazy dependency ở injection point
                                |
                                v
            Spring tạo lazy-resolution proxy
                                |
                                v
              Consumer giữ proxy reference
                                |
                    [chưa cần target]
                                |
               User gọi consumer.execute()
                                |
                                v
                  Proxy intercept lời gọi
                                |
                                v
          BeanFactory resolve target dependency
                 /             |             \
          tồn tại         không tồn tại      tạo lỗi
             |                 |               |
             v                 v               v
       Tạo/lấy target   thiếu bean error   BeanCreationException
             |
             v
       Gọi method của target
             |
             v
        Trả kết quả cho consumer
```

### 3.1. Proxy là một object thật được tiêm

`@Lazy` trên injection point không đưa vào Java `null` rồi tự đổi thành bean về sau. Nó đưa vào **một object proxy** mà consumer có thể giữ trong field, trả về qua getter hoặc truyền tiếp.

```java
class ClientConsumer {
    private final Client client;

    ClientConsumer(@Lazy Client client) {
        this.client = client;
    }

    Client getClient() {
        return client;
    }
}
```

Đây là snippet minh họa; cần imports, bean registration và interface `Client`. Khi container hỗ trợ proxy, `getClient() != null` không chứng minh có target bean. Nó chỉ chứng minh field giữ proxy reference.

### 3.2. Proxy không phải bản sao của target

Một lazy-resolution proxy không phải một object domain `new ExpensiveClient()` thứ hai. Nó là **cầu nối** tới container để lấy target. Khi target là singleton, proxy có thể cache singleton target sau lần resolution đầu tiên; những lần gọi sau sử dụng target ấy. Khi target không phải singleton, proxy có thể resolve target lại trên mỗi access, theo Javadoc của `@Lazy`.

Cần phân biệt:
- **Proxy identity:** consumer giữ cùng một proxy reference.
- **Target identity:** object đằng sau proxy có thể singleton, prototype hoặc scoped proxy target.
- **Bean definition name:** tên được container dùng để đăng ký target, không nhất thiết là Java class name.

### 3.3. Interface proxy và class-based proxy

Với injection type là interface, Spring có thể tạo JDK dynamic proxy implement interface ấy. Với concrete class injection, Spring có thể cần cơ chế class-based proxy; các class final, method final, visibility và cấu hình AOP có thể đặt ra giới hạn cho việc tạo/intercept proxy.

Đối với bài demo, chúng ta cố ý inject **interface `Gateway`** để JDK proxy semantics đơn giản và dễ xác minh. Đừng mặc định mọi `@Lazy` proxy sẽ có tên class cố định hoặc luôn dùng CGLIB.

### 3.4. Vì sao `getClass()` và `instanceof` không phải bằng chứng target đã tạo?

`proxy.getClass()` lấy Java runtime class của **proxy**, không tạo thành chứng cứ target bên trong đã được khởi tạo. `proxy instanceof Gateway` chỉ cho biết proxy implements interface/type `Gateway`. Cách kiểm tra target creation đáng tin hơn là:
- bộ đếm trong factory method/constructor;
- `beanFactory.containsSingleton("gateway")` khi target singleton;
- side effects được ghi log có chủ đích;
- assertion behavior sau lời gọi.

Đừng gọi `proxy.toString()`, `equals()` hoặc một business method chỉ để “kiểm tra là proxy” nếu bạn không muốn vô tình làm Spring resolve target. Tùy proxy implementation, những lời gọi đó có thể đi qua interceptor/target source.

## 4. Ba thời điểm khác nhau: inject proxy, resolve target, khởi tạo bean

Một ứng dụng có thể trải qua:

1. **Bean definition registration:** Spring biết metadata `gateway` và `consumer`, chưa chắc có instance.
2. **Consumer bean instantiation:** Spring tạo consumer và cung cấp proxy vào parameter `@Lazy Gateway`.
3. **Target dependency resolution:** proxy được gọi, Spring xác định bean target theo kiểu/qualifier/name và candidate rules.
4. **Target bean instantiation/initialization:** nếu target chưa tồn tại, BeanFactory tạo object, inject dependency, chạy lifecycle callbacks/post-processors.
5. **Target method invocation:** business method chạy trên target/proxy chain.
6. **Context closing:** container gọi destroy callbacks của managed singletons theo semantics scope/lifecycle.

Giai đoạn 3 và 4 có thể xảy ra đồng thời vào lời gọi đầu tiên, nhưng không bắt buộc. Nếu target eager singleton được tạo sẵn bởi context, proxy chỉ lấy bean đã tồn tại. Nếu target là prototype, nhiều lần gọi có thể dẫn đến nhiều target instances.

### 4.1. Phân biệt ẩn delay của resolve và delay của instantiate

`@Lazy` ở injection point trì hoãn **việc resolve target cho consumer đó**. Nó không phải công tắc đảm bảo target bean **không bao giờ** được tạo trước. Một bean khác có thể inject trực tiếp target, `@DependsOn` có thể kéo startup hoặc application code có thể gọi `context.getBean(Target.class)` từ sớm.

Bởi vậy, khi cần đo “lazy thực sự”, phải kiểm tra **mọi đường tạo target** trong context và target bean definition lazy policy.

### 4.2. Đừng tự nhận lazy là async

Lazy proxy xử lý lookup khi có lời gọi; mặc định business method vẫn chạy **đồng bộ trong luồng đang gọi**, trừ khi có một cơ chế async độc lập như `@Async` hoặc reactive pipeline.

Nếu target constructor block trên network, lời gọi proxy đầu tiên cũng có thể block trên network. Lazy chỉ dời latency tới thời điểm khác; nó không biến latency thành miễn phí.

### 4.3. Lazy không phải hot swap

Một singleton target sau khi được tạo không tự bị thay thế bằng một implementation khác khi property thay đổi. `@Lazy` không thay `@RefreshScope`, routing layer hay cơ chế runtime reconfiguration. Với prototype/scoped beans, behavior resolution theo scope có thể khác, nhưng không suy ra rằng thay config file sau `refresh()` sẽ tự đổi bean definitions.

## 5. Ma trận thời điểm khởi tạo

Giả sử có một singleton `Consumer` được tạo eagerly và một `Gateway`:

| Target definition | Injection point của Consumer | Đường tạo Gateway khác? | Kết quả điển hình lúc startup |
| --- | --- | --- | --- |
| Eager singleton | Inject trực tiếp | Không | Gateway được tạo khi startup |
| `@Lazy` singleton | Inject trực tiếp | Không | Gateway vẫn được tạo vì consumer cần target |
| Eager singleton | `@Lazy` proxy | Không | Gateway được pre-instantiate do chính definition eager |
| `@Lazy` singleton | `@Lazy` proxy | Không | Consumer tạo được, Gateway chưa cần khởi tạo |
| `@Lazy` singleton | `@Lazy` proxy | Có eager bean khác cần target | Gateway có thể vẫn được tạo khi startup |
| Prototype | Inject trực tiếp vào singleton | Không | Một prototype được cấp khi consumer tạo |
| Prototype | `@Lazy` proxy | Không | Target có thể được resolve mới cho mỗi method invocation |
| Không có bean | Inject trực tiếp required | Không | Thường lỗi trong startup/injection |
| Không có bean | `@Lazy` proxy | Không | Consumer có thể tạo được, lỗi khi gọi proxy |

Bảng giả định Spring injection-point proxy infrastructure đang hoạt động và những dependency types có thể proxy. Đây không phải cam kết cho mọi lifecycle/FactoryBean/custom BeanPostProcessor ngoài ví dụ.

### 5.1. `@Lazy` trên target **chưa đủ** nếu consumer eager

```java
@Bean
@Lazy
Gateway gateway() {
    return new RealGateway();
}

@Bean
Consumer consumer(Gateway gateway) {
    return new Consumer(gateway);
}
```

Spring cần Gateway để tạo Consumer; target bị kéo vào dù được đánh dấu lazy. Đây là nguyên tắc quan trọng của Spring reference.

### 5.2. `@Lazy` trên injection point **chưa đủ** nếu target eager

```java
@Bean
Gateway gateway() {
    return new RealGateway();
}

@Bean
Consumer consumer(@Lazy Gateway gateway) {
    return new Consumer(gateway);
}
```

Consumer nhận proxy, nhưng nếu `gateway` là non-lazy singleton được context eager pre-instantiate, target có thể đã được tạo trong startup. Đừng nhìn thấy injection proxy rồi khẳng định đã giảm thời gian tạo toàn bộ singleton.

### 5.3. Kết hợp đúng khi thực sự cần trì hoãn

```java
@Bean
@Lazy
Gateway gateway() {
    return new RealGateway();
}

@Bean
Consumer consumer(@Lazy Gateway gateway) {
    return new Consumer(gateway);
}
```

Trong một context không có đường nào khác resolve gateway, consumer có thể khởi động mà không phải khởi tạo gateway. Business method đầu tiên chạm proxy sẽ kích hoạt target resolution.

## 6. Khi nào lỗi xuất hiện?

Điểm quan trọng nhất của bài này là **thời điểm báo lỗi thay đổi theo dependency route**, dù bản chất lỗi giống nhau.

### 6.1. Target bean không tồn tại

Không có `Gateway` trong registry:

```java
@Component
class Consumer {
    private final Gateway gateway;

    Consumer(@Lazy Gateway gateway) {
        this.gateway = gateway;
    }

    String charge() {
        return gateway.pay();
    }
}
```

Đây là minh họa, cần imports/interface và bean registration. Theo Javadoc `@Lazy`, lazy-resolution proxy **vẫn được tiêm**, dù target chưa tồn tại. `ApplicationContext` có thể refresh thành công, nhưng khi `charge()` gọi `gateway.pay()`, proxy resolve dependency và Spring ném exception như `NoSuchBeanDefinitionException` trong một số đường lookup.

Nếu bỏ `@Lazy` tại injection point và vẫn required dependency, Spring thường phát hiện thiếu bean khi tạo Consumer. Đây là ý nghĩa của **fail-fast** so với **fail-late**.

### 6.2. Target bean definition có nhưng factory method/constructor lỗi

```java
@Bean
@Lazy
Gateway gateway() {
    throw new IllegalStateException("Remote configuration invalid");
}
```

Nếu Consumer nhận target qua `@Lazy Gateway` proxy và không ai tạo target lúc startup, context có thể refresh. Lời gọi đầu tiên đi qua proxy sẽ khiến BeanFactory thực thi factory method, dẫn tới `BeanCreationException` (thường bọc `IllegalStateException`).

Đừng nói “`@Lazy` bắt lỗi thay Spring” — lỗi vẫn xảy ra, chỉ được **dời sang mốc sử dụng target**. Trong production, điều này có thể là một request/queue message đầu tiên, khó quan sát hơn lỗi startup nếu không có logs/metrics đúng.

### 6.3. Target là eager singleton nhưng tạo lỗi

Nếu bean target không `@Lazy`:

```java
@Bean
Gateway gateway() {
    throw new IllegalStateException("Configuration invalid");
}
```

dù một consumer có `@Lazy Gateway` injection point, context vẫn có thể eager instantiate target singleton và thất bại **ngay lúc refresh**. Injection proxy không ngăn cơ chế eager initialization của target definition.

### 6.4. Target thành công nhưng business method lỗi

Target được tạo và initialized thành công không có nghĩa business method sẽ không throw. Lỗi nghiệp vụ như validation, network timeout hoặc database conflict có thể xảy ra ở **bất kỳ method call** nào, không riêng lời gọi đầu tiên qua proxy.

Hãy tách:
- **Dependency resolution failure:** không có/không chọn được ứng viên.
- **Bean creation failure:** constructor/factory/init callback lỗi.
- **Business invocation failure:** method chạy nhưng phát sinh lỗi nghiệp vụ.
- **Proxy creation failure:** không thể tạo proxy phù hợp cho injection type (ví dụ ràng buộc proxying của concrete/final class).

### 6.5. Lỗi proxy type có thể xuất hiện sớm

Nếu injection point yêu cầu một concrete final class không thể được subclass/proxy bằng chiến lược hiện có, Spring có thể thất bại **lúc tạo proxy/inject consumer**, trước khi bất kỳ business method nào chạy.

Do đó mệnh đề “mọi lỗi với `@Lazy` đều bị trì hoãn” là sai. Javadoc đảm bảo lazy target resolution semantics trong bối cảnh proxy được tạo thành công; lỗi cấu hình AOP/proxy và một số lỗi metadata vẫn có thể xảy ra khi context khởi động.

## 7. Optional dependency: đừng dùng proxy như phép kiểm tra có bean

Một mẫu dễ nhầm:

```java
@Autowired(required = false)
@Lazy
private OptionalGateway optionalGateway;
```

Ngay cả khi target không tồn tại, lazy-resolution proxy có thể được injected. Vì thế:

```java
if (optionalGateway != null) {
    optionalGateway.execute();
}
```

không chứng minh target có mặt. Chính lời gọi `execute()` có thể mới ném exception. Spring Javadoc cảnh báo injection-point lazy proxy có hành vi khó trực giác cho optional dependencies.

### 7.1. Dùng `ObjectProvider<T>` khi cần optional, lazy và điều khiển thời điểm lookup

```java
@Component
class NotificationService {
    private final ObjectProvider<OptionalGateway> gateways;

    NotificationService(
            ObjectProvider<OptionalGateway> gateways) {
        this.gateways = gateways;
    }

    void notifyIfConfigured() {
        OptionalGateway gateway = gateways.getIfAvailable();
        if (gateway != null) {
            gateway.execute();
        }
    }
}
```

Đây là code minh họa; cần imports và loại `OptionalGateway`. `ObjectProvider` thuộc `org.springframework.beans.factory`. Nó giữ **handle để tra cứu**, không phải dependency target đã resolve.

- `getObject()`: yêu cầu một object phù hợp, có thể throw khi không có hoặc ambiguous.
- `getIfAvailable()`: trả `null` khi không có, nhưng vẫn có thể báo lỗi nếu có nhiều ứng viên không phân giải được hoặc bean creation hỏng.
- `ifAvailable(Consumer<T>)`: chạy callback nếu có object; tránh nhầm “không có” với business method throw.
- `getIfUnique()`: hữu ích khi chỉ chấp nhận một ứng viên duy nhất; cần đọc semantics của qualifier/primary ở phiên bản Spring.

Dùng `ObjectProvider` không bảo đảm tránh exception nếu target **có definition nhưng tạo lỗi**. Nó giúp biểu diễn optional/late lookup rõ hơn injection proxy bị coi nhầm như một nullable field.

### 7.2. `Optional<T>` và `@Nullable` giải quyết vấn đề khác

Spring hỗ trợ optional dependency injection với `Optional<Gateway>` hoặc `@Nullable Gateway` ở những injection points phù hợp. Nhưng nếu consumer cần lookup lại tại **thời điểm sử dụng**, một provider thích hợp hơn: `Optional` được resolve tại injection time, trong khi `ObjectProvider` cho phép chủ động hỏi container khi cần.

Không gộp `@Lazy` và `Optional<T>` rồi giả định chúng luôn có cùng lifecycle semantics. Cần test cấu hình cụ thể.

### 7.3. Đừng dùng `@Lazy` để che config thiếu

Nếu Gateway là required business dependency, việc đẩy lỗi từ startup sang request đầu tiên thường làm hệ thống **kém tin cậy hơn**, không tốt hơn. Có thể dùng lazy để giảm chi phí khởi tạo khi chức năng chưa được gọi, nhưng vẫn cần readiness checks/integration tests đảm bảo những nhánh bắt buộc có đủ resources.

## 8. Singleton và prototype dưới lazy-resolution proxy

Javadoc Spring `@Lazy` nêu cơ chế caching khi target singleton được resolve lần đầu, và **re-resolution ở mỗi lần truy cập** khi target không phải singleton.

### 8.1. Singleton target

```java
@Bean
@Lazy
Gateway gateway() {
    return new DefaultGateway();
}

@Bean
Consumer consumer(@Lazy Gateway gateway) {
    return new Consumer(gateway);
}
```

Lần đầu consumer gọi `gateway.call()`, Spring tìm và khởi tạo singleton. Những lần gọi sau thường dùng cùng target singleton. Proxy không tạo một bean mới mỗi method invocation chỉ vì injection point là `@Lazy`.

### 8.2. Prototype target

```java
@Bean
@Scope("prototype")
RequestToken requestToken() {
    return new DefaultRequestToken();
}

@Bean
TokenService tokenService(@Lazy RequestToken token) {
    return new TokenService(token);
}
```

Một lazy-resolution proxy tới prototype dependency có thể yêu cầu BeanFactory resolve target **mỗi lần method được gọi qua proxy**. Điều này khác injection prototype trực tiếp vào singleton, nơi prototype thường chỉ được tạo một lần lúc singleton được khởi tạo.

Nhưng không đồng nhất proxy-per-invocation với **request scope** hoặc thread-local isolation. Nếu cần một object duy nhất xuyên suốt một HTTP request, dùng đúng web request scope/scoped proxy, không tạo prototype mới theo từng method invocation rồi kỳ vọng cùng state.

### 8.3. Lưu ý về stateful prototype

Giả sử `RequestToken` là mutable:

```java
token.setId("one");
return token.getId();
```

Với lazy prototype proxy re-resolving target cho mỗi method call, `setId(...)` và `getId()` **có thể thao tác trên hai prototype instances khác nhau**. Đây là một bẫy nghiêm trọng.

Nếu cần stateful object trong một use-case:
- dùng `ObjectProvider<RequestToken>.getObject()` **một lần**, lưu target trong local variable rồi thực hiện các methods liên tiếp;
- hoặc sử dụng scoped bean phù hợp với lifecycle use-case;
- không trông chờ proxy prototype bảo toàn state xuyên các lời gọi.

### 8.4. Singleton consumer giữ một proxy reference

`TokenService` là singleton và field `token` chỉ chứa **một proxy object**. Nhưng proxy có thể re-resolve prototype target khi cần. Vì thế field reference identity ổn định không đồng nghĩa target identity cố định.

## 9. Demo Java 17+ đầy đủ: 6 tình huống kiểm chứng

Dự án này dùng **Spring Framework 6.2.19** và Gradle, không cần Spring Boot, database hay network. Nó tạo các `AnnotationConfigApplicationContext` độc lập để chứng minh:

1. Target `@Lazy` + injection-point `@Lazy`: consumer khởi động trước target; lần gọi đầu tạo target singleton.
2. Prototype được inject qua lazy proxy: mỗi method invocation resolve target mới.
3. Thiếu target: proxy vẫn injected và lỗi xuất hiện lúc gọi.
4. `ObjectProvider`: dependency tùy chọn không có target được xử lý rõ ràng.
5. Target `@Lazy` nhưng consumer inject **trực tiếp**: target bị tạo lúc refresh để thỏa dependency.
6. Target eager hoặc factory lỗi: injection proxy không tự biến eager bean thành lazy, và lỗi bean creation có thể xuất hiện ở startup hoặc first invocation tùy cấu hình.

### 9.1. Cấu trúc

```text
lazy-injection-point-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── LazyInjectionDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'lazy-injection-point-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'LazyInjectionDemo'
}
```

Cần JDK 17+ và bản Gradle phù hợp. `spring-context` cung cấp `AnnotationConfigApplicationContext`, `@Bean`, `@Lazy`, bean factory injection và kéo theo những Spring modules cần cho proxy infrastructure.

### 9.2. Toàn bộ Java source

Lưu `src/main/java/LazyInjectionDemo.java`:

```java
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

public class LazyInjectionDemo {

    static final AtomicInteger lazyCreated = new AtomicInteger();
    static final AtomicInteger prototypeCreated = new AtomicInteger();
    static final AtomicInteger directCreated = new AtomicInteger();
    static final AtomicInteger eagerCreated = new AtomicInteger();

    public interface Gateway {
        String call();
    }

    public record DefaultGateway() implements Gateway {
        @Override
        public String call() {
            return "ok";
        }
    }

    public record GatewayConsumer(Gateway gateway) {
        public String use() {
            return gateway.call();
        }

        public boolean holdsJdkProxy() {
            return Proxy.isProxyClass(gateway.getClass());
        }
    }

    public interface SessionToken {
        String id();
    }

    public record DefaultToken(String value) implements SessionToken {
        @Override
        public String id() {
            return value;
        }
    }

    public record TokenConsumer(SessionToken token) {
        public String id() {
            return token.id();
        }
    }

    public interface MissingPort {
        String ping();
    }

    public record MissingConsumer(MissingPort port) {
        public boolean holdsJdkProxy() {
            return Proxy.isProxyClass(port.getClass());
        }

        public String invoke() {
            return port.ping();
        }
    }

    public record OptionalConsumer(
            ObjectProvider<MissingPort> provider) {
        public boolean exists() {
            return provider.getIfAvailable() != null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyConfig {

        @Bean
        @Lazy
        Gateway gateway() {
            lazyCreated.incrementAndGet();
            return new DefaultGateway();
        }

        @Bean
        GatewayConsumer gatewayConsumer(@Lazy Gateway gateway) {
            return new GatewayConsumer(gateway);
        }

        @Bean
        @Scope("prototype")
        SessionToken sessionToken() {
            int number = prototypeCreated.incrementAndGet();
            return new DefaultToken("token-" + number);
        }

        @Bean
        TokenConsumer tokenConsumer(@Lazy SessionToken sessionToken) {
            return new TokenConsumer(sessionToken);
        }

        @Bean
        MissingConsumer missingConsumer(@Lazy MissingPort missing) {
            return new MissingConsumer(missing);
        }

        @Bean
        OptionalConsumer optionalConsumer(
                ObjectProvider<MissingPort> provider) {
            return new OptionalConsumer(provider);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DirectConfig {

        @Bean
        @Lazy
        Gateway gateway() {
            directCreated.incrementAndGet();
            return new DefaultGateway();
        }

        @Bean
        GatewayConsumer gatewayConsumer(Gateway gateway) {
            return new GatewayConsumer(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class EagerConfig {

        @Bean
        Gateway gateway() {
            eagerCreated.incrementAndGet();
            return new DefaultGateway();
        }

        @Bean
        GatewayConsumer gatewayConsumer(@Lazy Gateway gateway) {
            return new GatewayConsumer(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DeferredFailureConfig {

        @Bean
        @Lazy
        Gateway gateway() {
            throw new IllegalStateException("late factory failure");
        }

        @Bean
        GatewayConsumer gatewayConsumer(@Lazy Gateway gateway) {
            return new GatewayConsumer(gateway);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class EagerFailureConfig {

        @Bean
        Gateway gateway() {
            throw new IllegalStateException("startup factory failure");
        }

        @Bean
        GatewayConsumer gatewayConsumer(@Lazy Gateway gateway) {
            return new GatewayConsumer(gateway);
        }
    }

    private static void check(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }

    private static void lazyAndOptionalCases() {
        try (var context = new AnnotationConfigApplicationContext(
                LazyConfig.class)) {

            GatewayConsumer gateway =
                    context.getBean(GatewayConsumer.class);
            TokenConsumer tokens =
                    context.getBean(TokenConsumer.class);
            MissingConsumer missing =
                    context.getBean(MissingConsumer.class);
            OptionalConsumer optional =
                    context.getBean(OptionalConsumer.class);

            boolean before = context.getBeanFactory()
                    .containsSingleton("gateway");
            check(!before, "gateway must not be created yet");

            boolean proxy = gateway.holdsJdkProxy();
            check(proxy, "expected interface lazy proxy");

            System.out.println("lazy.before=" + before);
            System.out.println("lazy.proxy=" + proxy);

            String first = gateway.use();
            boolean after = context.getBeanFactory()
                    .containsSingleton("gateway");
            String second = gateway.use();

            check("ok".equals(first) && "ok".equals(second),
                    "unexpected gateway result");
            check(after && lazyCreated.get() == 1,
                    "singleton target should be created once");

            System.out.println("lazy.call1=" + first);
            System.out.println("lazy.after=" + after);
            System.out.println("lazy.call2=" + second);
            System.out.println("lazy.created=" +
                    lazyCreated.get());

            String tokenOne = tokens.id();
            String tokenTwo = tokens.id();
            check(!tokenOne.equals(tokenTwo),
                    "prototype target should change");
            check(prototypeCreated.get() == 2,
                    "expected two prototype instances");

            System.out.println("prototype.first=" + tokenOne);
            System.out.println("prototype.second=" + tokenTwo);
            System.out.println("prototype.created=" +
                    prototypeCreated.get());

            boolean missingProxy = missing.holdsJdkProxy();
            check(missingProxy, "expected missing-port proxy");
            boolean missingFailure = false;
            try {
                missing.invoke();
            } catch (BeansException expected) {
                missingFailure = true;
            }
            check(missingFailure,
                    "missing target must fail on invocation");

            boolean optionalExists = optional.exists();
            check(!optionalExists, "missing port must be absent");

            System.out.println("missing.proxy=" + missingProxy);
            System.out.println("missing.failsOnCall=" +
                    missingFailure);
            System.out.println("optional.exists=" +
                    optionalExists);
        }
    }

    private static void directCase() {
        try (var context = new AnnotationConfigApplicationContext(
                DirectConfig.class)) {

            GatewayConsumer consumer =
                    context.getBean(GatewayConsumer.class);
            check(directCreated.get() == 1,
                    "direct injection should create lazy target");
            check(!consumer.holdsJdkProxy(),
                    "direct injection should hold target");

            System.out.println("direct.createdAtRefresh=" +
                    directCreated.get());
            System.out.println("direct.proxy=" +
                    consumer.holdsJdkProxy());
        }
    }

    private static void eagerCase() {
        try (var context = new AnnotationConfigApplicationContext(
                EagerConfig.class)) {

            GatewayConsumer consumer =
                    context.getBean(GatewayConsumer.class);
            check(eagerCreated.get() == 1,
                    "eager singleton should exist at refresh");

            System.out.println("eager.createdAtRefresh=" +
                    eagerCreated.get());
            System.out.println("eager.proxy=" +
                    consumer.holdsJdkProxy());
        }
    }

    private static void deferredFailureCase() {
        try (var context = new AnnotationConfigApplicationContext(
                DeferredFailureConfig.class)) {

            GatewayConsumer consumer =
                    context.getBean(GatewayConsumer.class);
            boolean failed = false;
            try {
                consumer.use();
            } catch (BeansException expected) {
                failed = true;
            }
            check(failed, "expected deferred bean creation failure");

            System.out.println("deferred.failureOnCall=" +
                    failed);
        }
    }

    private static void eagerFailureCase() {
        boolean failedAtRefresh = false;
        try (var context = new AnnotationConfigApplicationContext(
                EagerFailureConfig.class)) {
            throw new AssertionError("expected refresh to fail");
        } catch (BeansException expected) {
            failedAtRefresh = true;
        }
        check(failedAtRefresh, "expected startup failure");
        System.out.println("eager.failureAtRefresh=" +
                failedAtRefresh);
    }

    public static void main(String[] args) {
        lazyAndOptionalCases();
        directCase();
        eagerCase();
        deferredFailureCase();
        eagerFailureCase();
    }
}
```

### 9.3. Lệnh chạy

Trong thư mục `lazy-injection-point-demo`:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper trên Linux/macOS:

```bash
./gradlew run --quiet
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Đầu ra từ `System.out.println`:

```text
lazy.before=false
lazy.proxy=true
lazy.call1=ok
lazy.after=true
lazy.call2=ok
lazy.created=1
prototype.first=token-1
prototype.second=token-2
prototype.created=2
missing.proxy=true
missing.failsOnCall=true
optional.exists=false
direct.createdAtRefresh=1
direct.proxy=false
eager.createdAtRefresh=1
eager.proxy=true
deferred.failureOnCall=true
eager.failureAtRefresh=true
```

Các Spring/Gradle logging messages có thể xuất hiện thêm, đặc biệt ở các contexts **cố ý throw** exceptions. Các dòng trên là output nghiệp vụ chủ động in; demo dùng `check(...)` để ném `AssertionError` nếu giả định không đúng.

### 9.4. Vì sao case 1 thật sự trì hoãn target?

`gateway()` được khai báo `@Bean @Lazy` nên singleton definition không bị pre-instantiate tự động. `gatewayConsumer(@Lazy Gateway gateway)` nhận một lazy proxy, không bắt BeanFactory tạo Gateway ngay. Khi gọi `gateway.use()` lần đầu, proxy resolve target và `gateway()` mới chạy. Bộ đếm tăng từ 0 lên 1; lần gọi thứ hai vẫn dùng singleton, bộ đếm giữ 1.

`containsSingleton("gateway")` kiểm tra singleton cache trong BeanFactory; nó không kiểm tra **có hay không bean definition**. Ngay cả khi `containsSingleton` trả false, bean definition vẫn có thể đã tồn tại hợp lệ.

### 9.5. Vì sao prototype tạo hai instances?

`sessionToken()` có `@Scope("prototype")`; lazy proxy được tiêm vào `TokenConsumer`. Khi consumer gọi `tokens.id()` hai lần, target resolution có thể tạo hai `DefaultToken`, từ `token-1` sang `token-2`. Đây là demonstration của Spring `@Lazy` non-singleton re-resolution, không phải một token tự thay đổi value trên cùng instance.

### 9.6. Vì sao missing target không phá startup?

`MissingConsumer` nhận `@Lazy MissingPort missing`. Spring có thể tạo proxy cho interface `MissingPort` mà chưa resolve target. `getClass()` trên proxy không làm bean xuất hiện. Chỉ khi `missing.invoke()` gọi `port.ping()`, BeanFactory mới cố resolve `MissingPort` và báo lỗi. Trong demo catch một `BeansException` để xác định vị trí xảy ra lỗi; production code nên log root cause và phân biệt thiếu bean với lỗi logic khác.

### 9.7. Vì sao `ObjectProvider` trả false?

`OptionalConsumer` nhận `ObjectProvider<MissingPort>`. Khi `exists()` gọi `getIfAvailable()`, không có bean `MissingPort` nên nó trả null; test in `optional.exists=false`. Đây là API hợp lý hơn khi muốn kiểm tra sự tồn tại của một dependency tùy chọn.

### 9.8. Vì sao direct injection kéo target lazy vào startup?

Trong `DirectConfig`, target Gateway được đánh dấu `@Lazy` nhưng `gatewayConsumer(Gateway gateway)` không có `@Lazy` trên parameter. Eager consumer cần target thật để chạy factory method, nên `gateway()` được gọi trong `refresh()`; `direct.createdAtRefresh=1` và `direct.proxy=false`.

### 9.9. Vì sao eager target vẫn được tạo dù consumer có proxy?

`EagerConfig.gateway()` không mang `@Lazy`. `AnnotationConfigApplicationContext` thường pre-instantiate non-lazy singletons, nên sau `refresh()` bộ đếm đã là 1. Consumer có injection-point `@Lazy` nên field vẫn có proxy, nhưng target đã được tạo; vì thế `eager.proxy=true` **không** có nghĩa `eager.createdAtRefresh` bằng 0.

### 9.10. Hai lỗi factory cùng một nguyên nhân nhưng khác thời điểm

`DeferredFailureConfig` đánh dấu target bean `@Lazy`, consumer nhận proxy. `refresh()` hoàn tất; lần gọi đầu mới tạo Gateway và throw `IllegalStateException` được Spring bọc thành bean creation error. Output `deferred.failureOnCall=true`.

`EagerFailureConfig` khai báo target eager, nên BeanFactory thực thi factory method lỗi trong startup, trước khi application kịp lấy consumer. Output `eager.failureAtRefresh=true`.

**Kết luận demo:** lựa chọn lazy/eager ảnh hưởng mốc lỗi xuất hiện, không làm lỗi biến mất.

## 10. Những lỗi dễ mắc khi dùng proxy ở điểm tiêm

| Triệu chứng / quan niệm | Nguyên nhân | Cách giải quyết |
| --- | --- | --- |
| “Đã dùng `@Lazy` nhưng startup vẫn chậm” | Target definition vẫn eager hoặc consumer khác cần target | Đánh dấu target lazy và kiểm tra mọi dependency edges |
| “Consumer tạo được nghĩa là target tồn tại” | Proxy được injected dù target chưa resolve | Kiểm tra target bằng provider/readiness test hoặc gọi use case |
| `@Autowired(required=false) @Lazy` vẫn không null | Lazy proxy luôn được tạo khi hỗ trợ | Dùng `ObjectProvider`/`Optional` phù hợp |
| Mất bean chỉ báo lỗi tại request đầu | Injection-point proxy làm fail-late | Test startup contract, readiness và critical path |
| Constructor target throw nhưng context vẫn refresh | Target lazy và proxy trì hoãn bean creation | Phân biệt startup và first-invocation failures |
| Context fail dù injection point lazy | Target eager hoặc proxy creation/config errors | Kiểm tra target definition/proxyability |
| `proxy.getClass()` không đúng implementation | Đó là class của proxy | Kiểm tra interface/behavior, không dựa exact proxy class |
| `proxy == target` là false | Proxy là object trung gian | Dùng contract method và bean identity thích hợp |
| Prototype method calls không chia sẻ state | Proxy resolve mới từng access | Lấy target một lần bằng provider nếu cần stateful use case |
| Self-invocation không qua proxy | Method call nội bộ trên target bỏ qua wrapper | Tái thiết kế call boundary, không coi lazy là AOP business feature |
| Final class/method proxying lỗi | Proxy strategy không thể subclass/intercept | Ưu tiên interface, kiểm tra proxy constraints |
| Lời gọi đầu có latency spike | Target initialization được chuyển vào request | Warmup/readiness hoặc pre-initialize có chủ đích |
| `@Lazy` nghĩ là chạy async | Nó chỉ dời thời điểm lookup/init | Dùng async infrastructure riêng nếu cần |
| Bean target có lỗi credentials đến khi user gọi mới biết | Missing config được phát hiện muộn | Validate configuration tại startup hoặc health check |
| Quên đóng context | Resource target được khởi tạo nhưng không cleanup | Đóng ApplicationContext khi thích hợp |
| `@Lazy` bị xóa trong refactor | Dependency trở thành eager và đổi mốc lỗi | Viết integration tests cho creation timing |
| Thay property sau refresh không làm target đổi | Lazy không phải hot reload | Dùng chính sách refresh/routing phù hợp |
| Dùng `@Lazy` để khắc phục vòng phụ thuộc | Chỉ trì hoãn một đường resolution | Refactor graph; test cycles/proxies/early references |
| Prototype được inject trực tiếp vào singleton | Target chỉ được giải quyết khi singleton tạo | Dùng provider hoặc lazy proxy nếu muốn mỗi-use lookup |

### 10.1. Đừng chẩn đoán qua thông báo exception ngắn

Một lỗi như `BeanCreationException` có thể bao nhiều root causes:
- một constructor ném `IllegalArgumentException`;
- bean definition thiếu property;
- condition/profile khiến dependency không được đăng ký;
- network client initialization fail;
- circular dependency;
- một proxy không được tạo với target type.

Hãy đọc full exception chain, tên bean, injection point, thread và mốc lỗi (during refresh vs first business call).

### 10.2. Lưu ý về AOP proxy xếp chồng

Target bean có thể đã được proxy bởi Spring AOP (transaction, cache, security). Lazy-resolution proxy là lớp trung gian cho **dependency resolution**, không tự cung cấp transaction/authorization. Khi gọi method, có thể đi qua lazy proxy rồi đến target bean vốn là AOP proxy.

Không unwrap/proxy-cast sang implementation class trong business code. Inject interface hoặc một public contract rõ. Kiểm thử transaction/caching behavior qua managed reference, không gọi object bằng `new`.

### 10.3. Hạn chế dùng lazy cho infrastructure bắt buộc

Transaction manager, security policy, database schema validation hoặc một client luôn được dùng ngay sau startup có thể cần fail-fast. Việc che lỗi cho tới khi traffic đến sẽ làm failure blast radius khó quản lý hơn. Nếu chọn lazy có lý do (startup cost lớn/feature optional), bổ sung readiness contract và test khởi tạo trên đường đi được yêu cầu.

## 11. So sánh lazy proxy với `ObjectProvider`, `Supplier` và explicit factory

| Đặc điểm | Injection-point `@Lazy` | `ObjectProvider<T>` | Inject trực tiếp `T` |
| --- | --- | --- | --- |
| Consumer nhận | Proxy typed as T | Provider handle | Target bean/proxy do container trả |
| Khi resolve target | Khi gọi qua proxy | Khi code gọi `getObject/getIfAvailable` | Trong DI, trước consumer ready |
| Missing target | Có thể fail khi invoke proxy | `getIfAvailable` biểu thị vắng mặt; `getObject` báo lỗi | Required injection thường fail sớm |
| Singleton caching | Theo target scope/proxy semantics | BeanFactory singleton semantics | Cùng reference singleton |
| Prototype mỗi-use | Có thể re-resolve từng proxy invocation | Mỗi `getObject()` có thể lấy prototype mới | Singleton consumer giữ một prototype đã inject |
| Kiểm soát lookup | Ngầm qua lời gọi method | Tường minh tại điểm use | Không có |
| Dễ review dependencies | Vừa, proxy ẩn resolution | Rõ nếu provider được đặt tên đúng | Rõ |
| Nhu cầu | Hoãn gọi target mà vẫn dùng interface trực tiếp | Optional/multi-candidate/runtime lookup | Required DI, fail-fast |

### 11.1. Khi nào chọn `ObjectProvider`?

Dùng provider khi bạn cần:
- kiểm tra một dependency có tồn tại không;
- lấy một prototype mới **một lần cho một unit of work**;
- chủ động xử lý thiếu/multiple candidates;
- chọn thời điểm lookup minh bạch để tính toán cache/retry;
- triển khai fallback service khi feature không được cài.

Ví dụ:

```java
void process(ObjectProvider<SessionToken> provider) {
    SessionToken token = provider.getObject();
    token.assign("order-1");
    token.consume();
}
```

Snippet trên cần `SessionToken` API có hai methods minh họa. Cả hai calls trên cùng **local target instance**. Nếu thay bằng lazy prototype proxy và gọi hai methods trên proxy, target có thể khác nhau giữa hai lời gọi.

### 11.2. Khi nào chọn injection-point `@Lazy`?

Khi dependency bắt buộc **sẽ tồn tại**, nhưng consumer không cần gọi nó lúc tạo; API interface ổn định; bạn muốn tránh lookup boilerplate và chấp nhận rủi ro phát hiện lỗi muộn cùng chi phí proxy.

Ví dụ một module reporting lâu lâu mới chạy, trong khi phần main business không phụ thuộc module reporting trong mỗi request. Dù vậy, vẫn nên validate module quan trọng trước khi nhận traffic cần đến nó.

### 11.3. Khi nào nên tránh cả hai?

Nếu consumer luôn dùng dependency ngay trong constructor/init method, hoặc resource bắt buộc để application hoạt động, direct constructor DI có thể rõ và fail-fast hơn. Nếu cần async/background creation, hãy sử dụng startup scheduling/background initialization features tương ứng; `@Lazy` đơn thuần không lập lịch thread.

## 12. Kiểm thử đúng thời điểm và đúng ranh giới

### 12.1. Test 1 — target singleton chưa được tạo sau refresh

```java
try (var context = new AnnotationConfigApplicationContext(
        LazyInjectionDemo.LazyConfig.class)) {
    boolean created = context.getBeanFactory()
            .containsSingleton("gateway");
    if (created) {
        throw new AssertionError("Gateway created too early");
    }
}
```

Đây là **test fragment** theo demo mục 9, cần imports/tên package phù hợp. Nó kiểm chứng mốc tạo bean mà chỉ nhìn log startup có thể bỏ sót.

### 12.2. Test 2 — target tạo đúng một lần

```java
GatewayConsumer consumer = context.getBean(
        GatewayConsumer.class);
consumer.use();
consumer.use();
if (createdCounter.get() != 1) {
    throw new AssertionError("Expected singleton init once");
}
```

Đây là **pseudo-test fragment**: `createdCounter` là counter do test setup cung cấp. Nó biểu diễn cách xác minh singleton cache/creation thay vì chỉ quan sát output hai calls giống nhau.

### 12.3. Test 3 — dependency không tồn tại thì exception ở invocation

- Tạo context với consumer `@Lazy MissingPort` nhưng không khai bean MissingPort.
- Xác nhận context refresh thành công.
- Xác nhận proxy reference tồn tại.
- Gọi `port.ping()` và kiểm tra exception family/root cause.
- Ghi rõ “first method invocation failed” trong assertion message.

Điều này giúp tránh regression: một refactor bỏ `@Lazy` có thể chuyển lỗi từ invocation sang refresh; test sẽ chỉ ra sự thay đổi.

### 12.4. Test 4 — cùng lỗi nhưng eager bean gây fail-fast

Định nghĩa bean target eager với factory method throw. Đảm bảo `new AnnotationConfigApplicationContext(Config.class)` thất bại. Nếu bạn chỉ catch mọi `Exception` rồi coi test pass, có thể che sai lỗi XML/annotation/constructor khác; nên inspect root cause và bean name.

### 12.5. Test 5 — prototype re-resolution

```java
String first = tokenConsumer.id();
String second = tokenConsumer.id();
if (first.equals(second)) {
    throw new AssertionError("Expected distinct targets");
}
```

Trích đoạn demo giả định target tạo ID khác nhau. Đây là test của scope target, không phải test `proxy != target`.

### 12.6. Test 6 — không tạo target khi chỉ inspect proxy class

Nếu muốn bảo đảm một getter/proxy check không kích hoạt target, so counter trước/sau `Proxy.isProxyClass(proxy.getClass())`. Hạn chế việc vô tình gọi `toString` trong logger hoặc debugger watches khi test “chưa resolve target”.

### 12.7. Test 7 — optional dependency thay bằng provider

Khai `ObjectProvider<MissingPort>` và kiểm tra `getIfAvailable() == null` khi không có provider. Sau đó đăng ký bean phù hợp trong **context khác** để thử branch có provider. Tránh đăng ký runtime vào một context đã refresh khi đang phục vụ ứng dụng, vì bean registry concurrent mutation không phải workflow an toàn mặc định.

## 13. Những câu hỏi thường gặp

### 13.1. `@Lazy` ở constructor có làm constructor chạy muộn không?

Nếu annotation đặt trên **tham số constructor** `Consumer(@Lazy Gateway gateway)`, constructor Consumer **vẫn chạy** khi Consumer bean được tạo. Nó nhận proxy. Annotation đó không làm chính Consumer bean lazy. Muốn Consumer lazy, annotate **definition** của Consumer với `@Lazy` hoặc cấu hình tương ứng.

### 13.2. Có phải bean target luôn được tạo tại business method đầu tiên?

Không. Nếu target eager singleton, nó có thể được tạo lúc refresh; nếu dependency khác cần nó, cũng có thể bị kéo vào sớm; nếu target đã có, proxy chỉ lookup. Câu đúng hơn: injection-point proxy **resolve target khi bị gọi**, còn **target creation** chỉ xảy ra nếu bean chưa tồn tại và scope/lifecycle yêu cầu tạo.

### 13.3. Injection point `@Lazy` có bắt buộc dùng với `@Autowired`?

Javadoc mô tả nó trên injection points được đánh dấu `@Autowired` hoặc `@Inject`, và Spring bean factory method parameters cũng có cơ chế dependency resolution tương ứng. Trong JavaConfig, `@Bean Consumer consumer(@Lazy Gateway gateway)` là ví dụ đã được thiết kế để hoạt động. Nếu bạn tự tạo Consumer bằng `new Consumer(...)` ngoài Spring, annotation trên parameter không được JVM tự diễn giải thành proxy.

### 13.4. Một annotation `@Lazy` trên interface có tự làm mọi implementation lazy không?

Không theo mặc định. Bean definitions và injection points mới là nơi Spring áp dụng metadata phù hợp. Hãy kiểm tra target implementation beans thực tế và injection points; không dựa vào annotation tùy ý trên một interface để suy ra tất cả candidate beans đều lazy.

### 13.5. Vì sao interface injection thường dễ hơn với proxy?

JDK dynamic proxy chỉ cần implement interface. Concrete class proxy có thể phụ thuộc subclassing và gặp ràng buộc final classes/methods. Interface contract giúp giảm coupling giữa consumer và implementation và dễ tạo proxy hơn. Đây là khuyến nghị, không phải yêu cầu rằng mọi dependency trong Spring phải là interface.

### 13.6. Proxy có cache kết quả method không?

Không. Singleton target caching và **method result caching** là hai chuyện khác nhau. Lazy-resolution proxy có thể giữ singleton target reference nhưng không tự ghi nhớ output của `gateway.call()`. Nếu method gọi database hai lần, hai calls vẫn có thể thực hiện nghiệp vụ hai lần, trừ khi có caching khác.

### 13.7. Proxy tạo ra có phải `@Transactional` proxy không?

Không chỉ vì `@Lazy`. Lazy-resolution proxy phục vụ deferred dependency resolution. Một target bean có thể được các post-processors AOP tạo thêm proxy cho transactions/caching/security, nhưng đó là cơ chế độc lập cần configuration phù hợp.

### 13.8. Nếu gọi proxy từ nhiều threads cùng lúc thì sao?

Với singleton bean, BeanFactory thường bảo đảm singleton creation semantics, nhưng thời gian chờ/latency khi nhiều threads cùng chạm lazy target lần đầu cần được đo trong workload thật. Với prototype, nhiều invocations có thể lấy nhiều instances. Tránh dựa vào số lượng factory calls/ordering trong concurrency tests nếu chưa kiểm tra bean scope và application synchronization.

### 13.9. Lazy giúp xử lý circular dependency không?

Nó có thể trì hoãn một edge trong dependency graph và giúp một số trường hợp khởi tạo qua proxy, nhưng không biến graph vòng thành thiết kế tốt; errors có thể chuyển từ startup sang runtime. Hãy tái thiết kế module boundaries, event/callback hoặc factory abstraction để loại vòng khi có thể.

### 13.10. Có nên cho mọi bean đều lazy để startup nhanh?

Không mặc nhiên. Eager singleton creation giúp fail-fast, warm caches và kiểm chứng infrastructure trước khi nhận traffic. Lazy quá rộng dời lỗi, memory/CPU spikes sang critical path đầu tiên; nên tối ưu bằng profiling, startup metrics và feature-specific policies.

## 14. Bài tập có đáp án

### Bài 1 — Annotation đặt ở đâu?

`@Bean @Lazy Gateway gateway()` và `@Bean Consumer consumer(Gateway gateway)`. Khi Consumer eager, Gateway có chắc không được tạo ở startup?

**Đáp án:** Không. Dependency của eager Consumer kéo target lazy vào bean creation.

### Bài 2 — Chỉ injection point lazy

`@Bean Gateway gateway()` (eager) và `consumer(@Lazy Gateway gateway)`. `proxy` xuất hiện có chứng minh target chưa tạo không?

**Đáp án:** Không. Target eager singleton có thể được pre-instantiate khi refresh.

### Bài 3 — Cả definition và injection point lazy

`@Bean @Lazy Gateway gateway()` và consumer nhận `@Lazy Gateway`, không có reference khác tới Gateway. Mốc tạo target dự kiến?

**Đáp án:** Lần gọi phương thức đầu tiên qua lazy proxy (hoặc một lookup/đường sử dụng khác tạo bean trước).

### Bài 4 — Thiếu target

Không có `MissingPort` bean nhưng consumer có `@Lazy MissingPort`. Context có thể refresh thành công không?

**Đáp án:** Có nếu proxy được tạo hợp lệ. Khi gọi method trên proxy, BeanFactory có thể báo missing bean.

### Bài 5 — Nullable field

`@Autowired(required=false) @Lazy Gateway gateway`. `gateway != null` có khẳng định bean Gateway thực sự tồn tại không?

**Đáp án:** Không. Proxy reference có thể không null dù target missing. Dùng `ObjectProvider.getIfAvailable()` hoặc policy optional rõ.

### Bài 6 — Proxy là bean thật?

Một consumer giữ `Gateway` proxy và một lần gọi method kích hoạt target singleton. Có phải hai Gateway domain instances được tạo?

**Đáp án:** Không. Proxy là object trung gian; target singleton là managed domain bean. Proxy identity và target identity khác nhau.

### Bài 7 — Singleton target

Consumer gọi cùng method qua lazy proxy năm lần. Có bắt buộc factory `@Bean Gateway` chạy năm lần không?

**Đáp án:** Không; với target singleton và không có reset, factory thường chạy một lần khi target được tạo.

### Bài 8 — Prototype target

Consumer có `@Lazy Token token`, target Token prototype. Hai lời gọi `token.id()` có thể lấy hai target objects không?

**Đáp án:** Có. Lazy-resolution proxy có thể re-resolve non-singleton mỗi access.

### Bài 9 — Stateful prototype

`token.setValue("A")` và ngay sau đó `token.getValue()` trên một lazy prototype proxy. Có bảo đảm get trả A không?

**Đáp án:** Không. Hai calls có thể resolve hai instances khác nhau. Dùng provider để lấy một prototype target duy nhất cho unit of work.

### Bài 10 — Lỗi lúc nào?

Target `@Bean @Lazy Gateway gateway()` ném exception trong factory method, consumer nhận `@Lazy Gateway`. Không có lookup khác. Lỗi nhiều khả năng lộ ra ở đâu?

**Đáp án:** Khi method đầu tiên qua proxy làm BeanFactory tạo target.

### Bài 11 — Factory eager lỗi

Bỏ `@Lazy` khỏi target definition, giữ `@Lazy` ở consumer injection point. Factory ném exception. Có chắc context vẫn refresh không?

**Đáp án:** Không. Eager singleton creation có thể fail trong refresh.

### Bài 12 — Lazy là async?

Một Gateway constructor mất 5 giây. Sử dụng lazy proxy có làm call đầu không chờ 5 giây không?

**Đáp án:** Không. Call đầu có thể chờ creation đồng bộ. Async cần cơ chế riêng.

### Bài 13 — Missing candidate và provider

`ObjectProvider<Gateway>.getIfAvailable()` khi không có target thường trả gì?

**Đáp án:** `null` theo contract khi không có ứng viên. Nếu candidate tồn tại nhưng khởi tạo lỗi hoặc ambiguity, vẫn có thể có exception.

### Bài 14 — Proxy getClass

`Proxy.isProxyClass(client.getClass())` là true. Có chứng minh lazy target đã được tạo không?

**Đáp án:** Không. Nó nhận diện class proxy, không khẳng định target instantiation.

### Bài 15 — Annotation trên bean definition

`@Lazy` chỉ đặt trên `@Component HeavyJob`, một singleton khác inject `HeavyJob` trực tiếp. Container có thể tạo HeavyJob khi startup?

**Đáp án:** Có, vì phải thỏa dependency eager.

### Bài 16 — Proxy và transaction

Một `@Lazy` proxy giữ target có `@Transactional`. Có thể loại bỏ transaction infrastructure vì đã có proxy lazy không?

**Đáp án:** Không. Lazy resolution không thay transaction interception/advisor/manager.

### Bài 17 — Thời điểm khởi tạo Consumer

Consumer có constructor parameter `@Lazy Gateway` và Consumer bản thân là eager singleton. Consumer constructor chạy khi nào?

**Đáp án:** Khi Spring khởi tạo Consumer trong startup. Chỉ dependency target resolution được trì hoãn theo proxy.

### Bài 18 — Exception delayed

Production chạy bình thường suốt 2 giờ, đến request sử dụng hiếm gặp mới fail tạo một lazy target. Vì sao đây là trade-off?

**Đáp án:** Startup nhanh/fail-late đánh đổi khả năng phát hiện config/dependency errors sớm. Cần readiness/integration test/warmup policy tương ứng với mức độ quan trọng.

## 15. Checklist áp dụng `@Lazy` tại injection point

- Đã phân biệt `@Lazy` trên **target definition** với `@Lazy` trên **injection point** chưa?
- Target thực sự cần lazy hay đang được eager tạo qua một dependency khác?
- Consumer có inject một **proxy** đúng type (thường là interface) không?
- Có concrete final types/methods hoặc class-based proxy constraints không?
- Dependency có thật sự tùy chọn không? Nếu có, `ObjectProvider` có diễn đạt tốt hơn không?
- Có đang dùng `proxy != null` để xác nhận bean tồn tại một cách sai lầm không?
- Lỗi target creation có bị chuyển từ startup sang request đầu, gây downtime không?
- Có test cả no-target, broken-factory, eager-factory và healthy-target cases không?
- Scope target là singleton hay prototype? Mỗi proxy invocation resolve cùng hay khác target?
- Với stateful prototype, code có vô tình gọi nhiều methods qua proxy trên các instances khác nhau không?
- Lazy proxy có làm chậm critical path đầu tiên? Có readiness/warmup strategy không?
- Business method có được gọi qua Spring-managed proxy chain khi cần transactions/caching không?
- Có đang nhầm lazy với async/background execution hoặc runtime refresh không?
- Test dùng creation counters/`containsSingleton` và assert thời điểm thay vì chỉ inspect proxy class chưa?
- Context cleanup và errors/logs/metrics có được xử lý đúng không?

## 16. Tổng kết

Trong Spring Core Container, **`@Lazy` trên bean definition** điều chỉnh việc bean có được eagerly pre-instantiated không; **`@Lazy` tại injection point** tạo một **lazy-resolution proxy** để hoãn việc giải quyết target dependency tới khi proxy được sử dụng. Cần xem cả hai phía và mọi dependency edges để kết luận target thực sự được tạo muộn.

Proxy luôn là object được injected thành công khi proxy creation hợp lệ; **một target thiếu có thể chỉ bị phát hiện khi gọi proxy**. Điều đó làm optional dependencies khó trực giác. `ObjectProvider` biểu đạt việc lookup tùy chọn/chủ động tốt hơn. Với singleton target, lazy proxy có thể cache target sau lần resolve đầu; với non-singleton, nó có thể resolve lại từng access. Vì thế phải cẩn thận với prototype stateful beans.

Hãy lựa chọn lazy vì **lý do startup/chi phí/lifecycle cụ thể**, không coi nó như cách sửa dependency graph thiếu hoặc cách làm biến mất exception. Điều quan trọng không chỉ là “bean được tạo muộn hơn”, mà là **lỗi được phát hiện muộn hơn ở đâu**, consumer nào chịu latency và các đường sử dụng quan trọng có được kiểm chứng trước production traffic hay chưa.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — `@Lazy` Javadoc (definition vs injection-point proxy, missing target và singleton/prototype)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Lazy.html).
- [Spring Framework 6.2 — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html).
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html).
- [Spring Framework 6.2 — Using `@Autowired`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html).
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html).
- [Spring Framework 6.2 — `ContextAnnotationAutowireCandidateResolver` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/ContextAnnotationAutowireCandidateResolver.html).
- [Spring Framework 6.2 — `ObjectProvider` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ObjectProvider.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-005">@DependsOn cho phụ thuộc khởi tạo không thể hiện bằng DI</a>

<details>
<summary>Xem chi tiết</summary>

# @DependsOn cho phụ thuộc khởi tạo không thể hiện bằng DI trong Spring Core Container

## 1. Vấn đề: hai bean không inject nhau nhưng vẫn cần thứ tự khởi tạo

Trong Spring IoC Container, cách đúng nhất để biểu diễn **một object cần dùng object khác** thường là dependency injection (DI): constructor argument, `@Autowired` hoặc XML `ref`. Nếu `OrderService` cần `OrderRepository`, việc đưa repository vào constructor khiến Spring biết phải chuẩn bị repository trước khi tạo service.

Nhưng có một kiểu phụ thuộc khác: bean B **không nhận bean A như một tham số Java**, tuy nhiên B chỉ hoạt động đúng nếu **A đã được khởi tạo và hoàn tất initialization callback**. Ví dụ:

- Một component đăng ký một handler vào shared infrastructure.
- Một bean thiết lập registry toàn cục, trong khi bean khác chỉ sử dụng static/global registry.
- Một initializer cần chạy trước bean consumer nhưng consumer không cần giữ object initializer.
- Một bean nạp dữ liệu tham chiếu vào một nơi dùng chung trước khi bean khác bắt đầu công việc.

Spring cung cấp **`@DependsOn`** trong Java configuration và **`depends-on`** trong XML để diễn đạt loại **phụ thuộc thứ tự khởi tạo** này.

```java
@Bean
@DependsOn("registryInitializer")
JobDispatcher jobDispatcher() {
    return new JobDispatcher();
}
```

Ý nghĩa: Spring phải **tạo và khởi tạo bean có tên `registryInitializer` trước** khi tạo `jobDispatcher`, mặc dù phương thức `jobDispatcher()` không nhận tham số nào.

**Quan trọng:** `@DependsOn` không phải một dạng injection; nó không chuyển object `registryInitializer` vào `JobDispatcher`. Nó diễn đạt **lifecycle ordering constraint** trong BeanFactory.

### Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Phân biệt **dependency được thể hiện bằng DI** với **dependency chỉ yêu cầu thứ tự khởi tạo**.
2. Hiểu `@DependsOn("beanName")`, `@DependsOn({"beanA","beanB"})` và XML `depends-on="..."`.
3. Giải thích chính xác Spring đảm bảo gì về **initialization** và **destruction** của singleton beans.
4. Phân biệt thứ tự được bảo đảm bởi quan hệ phụ thuộc với thứ tự in code, classpath scan, annotation `@Order` hoặc thứ tự khai báo `@Bean`.
5. Dùng `@DependsOn` trên `@Bean` methods và trên class `@Component` được component-scanned; biết giới hạn của class-level annotation khi đăng ký bằng XML.
6. Kiểm tra tương tác với `@Lazy`, `lazy-init`, prototype và các scoped beans.
7. Phát hiện circular depends-on, missing bean name, bean aliases và các trường hợp side effect không còn hợp lý.
8. Chạy ví dụ Java 17+ / Spring Framework 6.2.19 dùng Java configuration, XML, component scan và lazy bean, đồng thời kiểm chứng cả startup lẫn shutdown order.
9. Biết khi nào **không nên dùng @DependsOn** và nên chọn constructor injection, lifecycle hooks hay sự kiện ứng dụng thay thế.

**Phạm vi:** Chủ đề thuộc **Thời điểm tạo bean** của Spring Framework Core Container. Mục tiêu là đồng bộ **creation/initialization order của bean definitions**, không mô phỏng một task scheduler hay cơ chế dependency orchestration phân tán.

## 2. DI đã biểu diễn thứ tự trong trường hợp bình thường

Một dependency thật bằng constructor injection:

```java
public final class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

Nếu Spring tạo `OrderService` thông qua bean definition hợp lệ và resolve `OrderRepository`, nó phải tạo/chuẩn bị repository trước khi truyền vào constructor. Đây là **phụ thuộc object trực tiếp**, có thể được biểu đạt bằng:

```java
@Bean
OrderService orderService(OrderRepository repository) {
    return new OrderService(repository);
}
```

hoặc XML:

```xml
<bean id="orderRepository" class="com.example.OrderRepository"/>
<bean id="orderService" class="com.example.OrderService">
    <constructor-arg ref="orderRepository"/>
</bean>
```

Những snippets trên cần Java classes `OrderService` và `OrderRepository` phù hợp.

### Không thêm @DependsOn nếu constructor đã diễn đạt đúng dependency

Trong trường hợp này, thêm:

```java
@DependsOn("orderRepository")
@Bean
OrderService orderService(OrderRepository repository) {
    return new OrderService(repository);
}
```

thường **thừa**: tham số `repository` đã làm Spring biết phải có repository. `@DependsOn` không cải thiện type safety và có thể làm người đọc nhầm rằng tồn tại một lifecycle side effect riêng.

**Quy tắc:** Nếu bean B **thực sự cần giữ/thao tác trên object A**, hãy **inject A**, thay vì chỉ thêm tên A vào `@DependsOn`.

## 3. Khi nào DI không biểu diễn được phụ thuộc?

Có những requirements không truyền object trực tiếp.

### 3.1. Registry global của thư viện legacy

Một bean `PluginRegistrar` gọi API đăng ký plugin trong init callback; một bean `PluginLauncher` sau đó dùng một static registry, không nhận `PluginRegistrar` vào constructor:

```text
PluginRegistrar.initialize()
    |
    +--> SharedPlugins.register("pdf", PdfPlugin)
    |
    v
PluginLauncher được phép bắt đầu hoạt động
    |
    +--> SharedPlugins.lookup("pdf")
```

Thư viện cũ không expose một injectable registry abstraction, nên có thể cần:

```java
@Bean("pluginRegistrar")
PluginRegistrar pluginRegistrar() {
    return new PluginRegistrar();
}

@Bean("pluginLauncher")
@DependsOn("pluginRegistrar")
PluginLauncher pluginLauncher() {
    return new PluginLauncher();
}
```

Đây là cấu trúc minh họa, chưa phải source runnable; các Java types và lifecycle code phải được cung cấp. `@DependsOn` buộc registrar **được khởi tạo trước**, qua đó initialization callback của nó có cơ hội đăng ký plugin. Nó không tự hiểu plugin registry và cũng không truyền plugin object cho launcher.

### 3.2. Cấu hình native/static initialization

Một bean thực hiện side effect ban đầu như nạp native library, đăng ký driver, cài đặt global registry hoặc warm-up một cơ chế đồng bộ. Bean B phụ thuộc vào side effect đó, dù không có reference đến initializer. Tài liệu Spring nêu tình huống tương tự khi một **static initializer cần được trigger**.

### 3.3. Chuyển tiếp kiến trúc legacy

Khi chưa thể refactor code thành DI thuần, `@DependsOn` là bridge rõ nghĩa để loại bỏ sự phụ thuộc tình cờ vào thứ tự component scan hoặc thứ tự khai báo bean.

Tuy vậy, **đừng dùng @DependsOn để duy trì mãi một global mutable state** nếu có thể thay thế bằng dependency được inject, interface/registry bean thực sự, hoặc hạ tầng lifecycle có contract rõ.

## 4. @DependsOn đảm bảo điều gì?

Javadoc Spring mô tả: các beans được liệt kê trong `@DependsOn` được container tạo **trước** bean mang annotation. Ngữ cảnh gồm việc thực hiện initialization theo lifecycle do Spring quản lý.

```text
bean "appWorker" có @DependsOn("setup")
                 |
                 v
BeanFactory kiểm tra "setup"
                 |
                 +--> tạo setup nếu chưa có
                 +--> resolve dependencies của setup
                 +--> chạy các init callbacks đồng bộ của setup
                 |
                 v
BeanFactory tạo "appWorker"
                 |
                 +--> resolve dependencies của appWorker
                 +--> chạy init callbacks của appWorker
                 |
                 v
Cả hai sẵn sàng theo bean lifecycle
```

**Giới hạn quan trọng:** nếu `setup` khởi động một asynchronous task và `init()` return **trước khi task hoàn thành**, Spring chỉ biết bước initialization đồng bộ của setup hoàn tất. `@DependsOn` **không mặc định chờ mọi Future, thread, remote service hoặc distributed operation** đạt trạng thái ready.

### 4.1. Initialization order ≠ invocation order của mọi method

Spring có thể bảo đảm:

```text
setup.init trước appWorker.init
```

nhưng không vì thế mà bảo đảm:

```text
mọi lời gọi appWorker.run() đều chờ setup's background task thật sự xong
```

Nếu workload cần barrier rõ ràng, hãy thiết kế readiness contract (ví dụ `CompletableFuture`/latch/health state với timeout) hoặc lifecycle orchestration phù hợp, thay vì chỉ dùng annotation tên bean.

### 4.2. Tên dùng trong @DependsOn là bean name

```java
@DependsOn("registryInitializer")
```

`registryInitializer` là **bean name** (hoặc một alias có thể resolve trong container), không phải tên field, Java type đơn lẻ hoặc package name. Khi dùng `@Bean("registryInitializer")`, tên này là contract khai báo.

Nếu đổi tên bean mà quên cập nhật annotation, context có thể fail trong quá trình tạo bean phụ thuộc.

## 5. Cú pháp @DependsOn trên @Bean method

Một dependency:

```java
@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean("bootstrapState")
    BootstrapState bootstrapState() {
        return new BootstrapState();
    }

    @Bean("reportWorker")
    @DependsOn("bootstrapState")
    ReportWorker reportWorker() {
        return new ReportWorker();
    }
}
```

Đoạn minh họa yêu cầu imports `Configuration`, `Bean`, `DependsOn` và Java types thật. Method `reportWorker()` **không nhận `BootstrapState`**: relation được khai báo qua metadata, không phải tham số DI.

**Đặt `@DependsOn` lên method bean cần được tạo *sau***, không đặt nó lên prerequisite rồi kỳ vọng nghĩa đảo chiều.

### 5.1. Nhiều prerequisites

```java
@Bean("dataReporter")
@DependsOn({"driverRegistration", "schemaValidation"})
DataReporter dataReporter() {
    return new DataReporter();
}
```

Ý nghĩa: cả hai named beans phải được initialized trước `dataReporter`. Nhưng `@DependsOn({"A","B"})` **không nên được coi là API buộc A trước B**. Nếu có quan hệ A phải trước B, hãy khai báo trực tiếp **B depends on A**, rồi C depends on B hoặc biểu diễn graph tương ứng.

### 5.2. Một mảng names, không phải expression language

```java
@DependsOn({"cacheBootstrap", "metricsBootstrap"})
```

là literal array of bean names. Nó không tự parse Spring Expression Language thành các bean khác hay khám phá mọi implementation của một interface. Nếu bean names động/condition-dependent, cân nhắc thiết kế khác hoặc programmatic BeanDefinition registration với explicit dependency metadata.

### 5.3. Constructor injection và @DependsOn có thể cùng tồn tại

Nếu consumer cần bean repository thực sự **và** cần side effect của bootstrap:

```java
@Bean("orderProcessor")
@DependsOn("globalCodecRegistrar")
OrderProcessor orderProcessor(OrderRepository repository) {
    return new OrderProcessor(repository);
}
```

Đây là snippet cấu trúc; DI xử lý repository object, `@DependsOn` xử lý side effect prerequisite. Hai quan hệ khác bản chất, có thể dùng chung.

## 6. @DependsOn trên @Component: giới hạn quan trọng

Javadoc `@DependsOn` cho phép dùng ở **TYPE** và **METHOD**. Nhưng class-level annotation có caveat:

> Class-level @DependsOn chỉ có hiệu lực khi component scanning được dùng để đăng ký class ấy.

Ví dụ:

```java
@Component("searchIndexer")
@DependsOn("indexRegistry")
public class SearchIndexer {
    public SearchIndexer() {
    }
}
```

Nếu scanner tìm `SearchIndexer`, metadata `@DependsOn("indexRegistry")` có thể được áp cho bean definition của component.

**Nếu class đó chỉ được khai báo qua XML:**

```xml
<bean id="searchIndexer" class="com.example.SearchIndexer"/>
```

thì annotation cấp class `@DependsOn` **không được áp dụng theo cách XML BeanDefinition này**; hãy cấu hình:

```xml
<bean id="searchIndexer"
      class="com.example.SearchIndexer"
      depends-on="indexRegistry"/>
```

Điểm này được Javadoc Spring nêu rất rõ, nên không thể chỉ nhìn annotation trên Java class mà bỏ qua **đường đăng ký bean**.

### 6.1. @Configuration class có thể là @Component specialization

Một `@Configuration` class cũng thuộc hệ thống component stereotypes. Nhưng `@DependsOn` cấp class không nên được hiểu như cách ép **tất cả `@Bean` methods** trong class đó lần lượt phụ thuộc vào cùng một prerequisite theo mọi cách đăng ký.

Nếu mục tiêu là thứ tự khởi tạo của một bean cụ thể, cách rõ ràng nhất là gắn `@DependsOn` **trực tiếp lên `@Bean` method** tương ứng. Nếu mục tiêu là hạ tầng bootstrap toàn context, cân nhắc đúng extension point/lifecycle hook thay vì ẩn order trong annotation trên configuration container.

## 7. XML depends-on="..." tương đương ở tầng bean definition

Trong XML:

```xml
<bean id="initializationRegistry"
      class="com.example.InitializationRegistry"/>

<bean id="backgroundWorker"
      class="com.example.BackgroundWorker"
      depends-on="initializationRegistry"/>
```

XML attribute `depends-on` mô tả **initialization order constraint**. Nó không tự tạo reference tới registry bên trong Java `BackgroundWorker`.

### 7.1. Nhiều bean names trong XML

```xml
<bean id="reportWorker"
      class="com.example.ReportWorker"
      depends-on="cacheWarmup, schemaCheck"/>
```

Spring cho phép phân cách các names bằng dấu **phẩy, khoảng trắng hoặc chấm phẩy** trong XML `depends-on`.

Tương đương ý nghĩa trong Java:

```java
@DependsOn({"cacheWarmup", "schemaCheck"})
```

**Không đưa class names vào depends-on** nếu class được đăng ký bằng một bean name khác. Ví dụ nếu `@Bean("cacheWarmup")` trả `CacheWarmupService`, tên constraint là `cacheWarmup`.

### 7.2. XML ref không thay cho depends-on trong trường hợp side effect

```xml
<bean id="reportWorker" class="com.example.ReportWorker">
    <constructor-arg ref="cacheWarmup"/>
</bean>
```

dùng `ref` để **đưa object dependency vào constructor**; Java class phải có constructor phù hợp. Nếu worker không nhận/không dùng object đó, chỉ tạo constructor parameter giả để điều khiển thứ tự là một anti-pattern. Dùng `depends-on` mới đúng nghĩa khi thật sự chỉ yêu cầu initialization side effect.

### 7.3. Có thể dùng cả hai

```xml
<bean id="reportWorker"
      class="com.example.ReportWorker"
      depends-on="bootstrapRegistrar">
    <constructor-arg ref="businessRepository"/>
</bean>
```

DI cung cấp `businessRepository`, depends-on buộc `bootstrapRegistrar` init trước. Đây là hai loại dependency riêng.

## 8. Hệ quả khi đóng context: hủy dependent trước prerequisite

`@DependsOn("registry")` trên bean `worker` tạo quan hệ **worker phụ thuộc registry**. Khi cả hai là managed singleton có destruction callbacks, Spring có thể giữ registry tồn tại tới khi worker đã được hủy:

```text
Initialization: registry -> worker
Destruction:    worker   -> registry
```

Điều này hữu ích nếu worker cần unregister callback hoặc flush qua registry lúc `destroy()`. Nó không tự đóng unmanaged threads, chờ future, hoàn tất external transaction hay tạo total order cho những bean không liên hệ.

Ví dụ Java: `@Bean @DependsOn("registry") Worker worker()`; XML: `<bean id="worker" depends-on="registry" .../>`. Với **prototype**, container không tự áp dụng toàn bộ destruction callback như singleton. Bài cuối chương đi sâu vào dependency registry (`getDependentBeans`, `registerDependentBean`), DI relationships và graceful shutdown; ở đây chỉ cần lưu ý hệ quả lifecycle của metadata `@DependsOn`.

## 9. @DependsOn với @Lazy: không phải cách trì hoãn

Giả sử:

```java
@Bean("slowSetup")
@Lazy
SlowSetup slowSetup() {
    return new SlowSetup();
}

@Bean("readyWorker")
@DependsOn("slowSetup")
ReadyWorker readyWorker() {
    return new ReadyWorker();
}
```

Nếu `readyWorker` là non-lazy singleton, Spring cần tạo `slowSetup` **trước** readyWorker, dù slowSetup được đánh dấu lazy. Tức là depends-on có thể **kéo một lazy bean vào startup**.

Một bảng:

| Bean prerequisite | Bean dependent | Dự kiến |
| --- | --- | --- |
| Non-lazy singleton | Non-lazy singleton | prerequisite initialized trước dependent |
| Lazy singleton | Non-lazy singleton có `@DependsOn` | lazy prerequisite bị tạo sớm để thỏa constraint |
| Lazy singleton | Lazy singleton dependent chưa được lookup | có thể cả hai chưa tạo |
| Lazy singleton | Lazy dependent được lookup | prerequisite được tạo trước dependent |
| Prototype prerequisite | Singleton dependent | prerequisite có thể được tạo khi dependent được tạo; destroy semantics khác singleton |
| Scoped prerequisite | Bean ở scope khác | còn phụ thuộc active scope và cách lookup; không xem như ordering toàn cục |

**Đừng dùng @DependsOn để tối ưu startup time.** Nó thêm prerequisite, thường khiến thứ tự và chi phí khởi tạo chặt hơn. Muốn tránh lấy dependency quá sớm, dùng `@Lazy` tại injection point, `ObjectProvider<T>` hoặc điều chỉnh lifecycle contract theo tình huống — nhưng phải bảo toàn correctness.

## 10. Ví dụ Java 17+: Java @Bean, XML, component scan và lazy prerequisite

Ví dụ sau kiểm chứng bằng **thứ tự sự kiện**, không dựa vào thứ tự đăng ký bean hay thời gian wall-clock. Tất cả beans trong demo đều là Spring-managed objects bình thường.

Có bốn contexts **độc lập** để so sánh:

1. `JavaConfig`: `@DependsOn` trên một `@Bean` method.
2. `xml-depends.xml`: XML `depends-on`.
3. `ScanConfig`: `@DependsOn` trên một class được scanner tìm thấy.
4. `LazyConfig`: target `@Lazy` vẫn bị eager dependent kéo vào startup.

Trong mỗi context, một `Tracked` class ghi `construct`, `init` và `destroy` qua `InitializingBean` và `DisposableBean`. `worker` không giữ reference đến `setup`; quan hệ chỉ nằm trong metadata depends-on.

### 10.1. Cây thư mục

```text
depends-on-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── DependsOnDemo.java
        └── resources/
            └── xml-depends.xml
```

Một file Java chứa nested static classes, Spring config và `main`. XML sử dụng **binary class name** có ký tự đô la cho Java nested class: `demo.DependsOnDemo$Tracked`.

### 10.2. Cấu hình Gradle

**`settings.gradle`**

```groovy
rootProject.name = 'depends-on-demo'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.DependsOnDemo'
}
```

Java 17+ và một Gradle version hỗ trợ Java toolchains là đủ. Không cần Spring Boot, database, Jakarta lifecycle annotation hay web server. Spring Context kéo theo Beans/Core và các dependencies cần thiết.

### 10.3. xml-depends.xml

**`src/main/resources/xml-depends.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Khai báo dependent trước để thấy ordering không dựa vào thứ tự dòng. -->
    <bean id="xmlWorker"
          class="demo.DependsOnDemo$Tracked"
          depends-on="xmlSetup">
        <constructor-arg value="xmlWorker"/>
    </bean>

    <bean id="xmlSetup"
          class="demo.DependsOnDemo$Tracked">
        <constructor-arg value="xmlSetup"/>
    </bean>

</beans>
```

Cả hai là singleton mặc định. `xmlWorker` được khai báo **trước** nhưng `depends-on` buộc `xmlSetup` khởi tạo trước.

### 10.4. Java source đầy đủ

**`src/main/java/demo/DependsOnDemo.java`**

```java
package demo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

public final class DependsOnDemo {

    private static final List<String> EVENTS = new ArrayList<>();

    // Không field dependency nào tới bean setup khác.
    public static class Tracked
            implements InitializingBean, DisposableBean {

        private final String name;

        public Tracked(String name) {
            this.name = name;
            EVENTS.add(name + ".construct");
        }

        @Override
        public void afterPropertiesSet() {
            EVENTS.add(name + ".init");
        }

        @Override
        public void destroy() {
            EVENTS.add(name + ".destroy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class JavaConfig {

        // Deliberately declared before its prerequisite.
        @Bean("javaWorker")
        @DependsOn("javaSetup")
        Tracked javaWorker() {
            return new Tracked("javaWorker");
        }

        @Bean("javaSetup")
        Tracked javaSetup() {
            return new Tracked("javaSetup");
        }
    }

    // Class-level annotation is honored on a scanned component.
    @Component("scanWorker")
    @DependsOn("scanSetup")
    public static class ScanWorker extends Tracked {
        public ScanWorker() {
            super("scanWorker");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = DependsOnDemo.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = ScanWorker.class
        )
    )
    public static class ScanConfig {
        @Bean("scanSetup")
        Tracked scanSetup() {
            return new Tracked("scanSetup");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyConfig {
        @Bean("lazySetup")
        @Lazy
        Tracked lazySetup() {
            return new Tracked("lazySetup");
        }

        @Bean("activeWorker")
        @DependsOn("lazySetup")
        Tracked activeWorker() {
            return new Tracked("activeWorker");
        }
    }

    private static void exercise(
            String label,
            Supplier<? extends ConfigurableApplicationContext> factory) {

        EVENTS.clear();
        int beforeClose;

        try (ConfigurableApplicationContext context = factory.get()) {
            // Construct + init callbacks xảy ra trong refresh().
            System.out.println(label + ".start="
                    + String.join(" > ", EVENTS));

            beforeClose = EVENTS.size();

            // Không chủ động getBean() các prerequisites ở đây.
            // Context close() sẽ gọi destroy callbacks.
        }

        System.out.println(label + ".destroy="
                + String.join(" > ",
                    EVENTS.subList(beforeClose, EVENTS.size())));
    }

    public static void main(String[] args) {
        exercise("java",
                () -> new AnnotationConfigApplicationContext(
                        JavaConfig.class));

        exercise("xml",
                () -> new ClassPathXmlApplicationContext(
                        "xml-depends.xml"));

        exercise("scan",
                () -> new AnnotationConfigApplicationContext(
                        ScanConfig.class));

        exercise("lazy",
                () -> new AnnotationConfigApplicationContext(
                        LazyConfig.class));
    }
}
```

### 10.5. Chạy

Ở thư mục `depends-on-demo`:

```bash
gradle run --quiet
```

Nếu đã có Gradle Wrapper trong project, dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows). Dùng `javac DependsOnDemo.java` đơn lẻ không đủ khi không có Spring libraries trong compile/runtime classpath; Gradle cấu hình dependencies và copy XML lên classpath.

### 10.6. Stdout dự kiến

```text
java.start=javaSetup.construct > javaSetup.init > javaWorker.construct > javaWorker.init
java.destroy=javaWorker.destroy > javaSetup.destroy
xml.start=xmlSetup.construct > xmlSetup.init > xmlWorker.construct > xmlWorker.init
xml.destroy=xmlWorker.destroy > xmlSetup.destroy
scan.start=scanSetup.construct > scanSetup.init > scanWorker.construct > scanWorker.init
scan.destroy=scanWorker.destroy > scanSetup.destroy
lazy.start=lazySetup.construct > lazySetup.init > activeWorker.construct > activeWorker.init
lazy.destroy=activeWorker.destroy > lazySetup.destroy
```

Chương trình in tám dòng theo `System.out.println`. Gradle/framework có thể in log tùy môi trường, không tính vào tám dòng chủ động này.

### 10.7. Phân tích kết quả Java config

- `javaWorker` được khai báo ở `JavaConfig` **trước** method `javaSetup`.
- `@DependsOn("javaSetup")` yêu cầu BeanFactory tạo/setup prerequisite trước worker.
- `Tracked` ghi constructor và `afterPropertiesSet()`; vì thế `javaSetup.init` được in trước `javaWorker.construct`.
- Khi context đóng, `javaWorker.destroy` đến trước `javaSetup.destroy`.
- `javaWorker` không có reference tới `javaSetup`; quan hệ hoàn toàn ở bean definition metadata.

Điều này minh họa đúng tình huống **phụ thuộc khởi tạo không thể hiện bằng DI**.

### 10.8. Phân tích kết quả XML

- `xmlWorker` xuất hiện trước `xmlSetup` trong XML.
- BeanFactory vẫn phải tạo `xmlSetup` trước để thỏa `depends-on="xmlSetup"`.
- Vì cả hai implement `InitializingBean` và `DisposableBean`, demo ghi được init/destroy qua Spring interfaces, không cần Java `@PostConstruct` hay `@PreDestroy`.
- Shutdown order ngược với startup relation.

### 10.9. Phân tích kết quả component scan

- `ScanConfig` bật `@ComponentScan` nhưng **tắt default filters** để demo chỉ scan chính class `ScanWorker` qua một include filter assignable type.
- `ScanWorker` là public static nested class, có `@Component("scanWorker")` và `@DependsOn("scanSetup")` ở **class level**.
- `scanSetup` được đăng ký bởi method `@Bean` của `ScanConfig`.
- Khi scanner đăng ký ScanWorker, constraint class-level được nhận diện và áp dụng; lifecycle cho thấy setup init trước worker constructor.
- Cách này khác khai báo trực tiếp `<bean class="...ScanWorker"/>` trong XML, vì Javadoc lưu ý annotation class-level không được áp dụng cho BeanDefinition chỉ đăng ký theo XML.

### 10.10. Phân tích kết quả lazy prerequisite

- `lazySetup` mang `@Lazy`, bình thường có thể không được pre-instantiate.
- `activeWorker` là **non-lazy singleton** và có `@DependsOn("lazySetup")`.
- Spring cần tạo lazySetup **trong refresh**, nên nó thực thi `lazySetup.construct` và `lazySetup.init` trước activeWorker.
- Ví dụ này chứng minh **`@Lazy` không ghi đè dependency ordering**.
- Khi đóng context, activeWorker bị destroy trước lazySetup.

### 10.11. Vì sao dùng counter/events thay vì timestamp?

Timestamp và thread scheduling tạo ra các kết quả không ổn định. `List<String> EVENTS` trong demo không bị concurrent mutation vì các configs này dùng startup đồng bộ bình thường và `exercise` chạy tuần tự. Việc in đúng tên và thứ tự của callbacks giúp kiểm chứng hợp đồng `depends-on`.

Nếu chuyển demo sang background initialization hoặc async init, sẽ cần một recorder thread-safe và assertions phù hợp, không được giả định các callbacks luôn chạy cùng thread.

## 11. @DependsOn so với @Order và @Priority

Đây là nhóm annotations thường bị nhầm:

| Annotation | Dùng để làm gì? | Có bảo đảm prerequisite init trước? |
| --- | --- | --- |
| `@DependsOn` | Tạo initialization relationship giữa bean definitions theo names | Có, trong phạm vi Spring BeanFactory lifecycle |
| XML `depends-on` | Tương đương metadata ordering cho XML bean | Có |
| `@Order` | Precedence của ordered components, lists of injected beans hoặc extension point phù hợp | Không phải general-purpose bean initialization dependency |
| `@Priority` | Priority selection/ordering trong những framework APIs tương ứng | Không phải general-purpose init dependency |
| `@Primary` | Ưu tiên candidate trong dependency injection theo type | Không |
| `@Qualifier` | Thu hẹp/chọn dependency candidate | Không trực tiếp |
| `@Lazy` | Trì hoãn eager singleton pre-instantiation | Không; có thể bị depends-on kéo vào startup |
| `@DependsOn` cùng `@Bean` | Ép target named beans init trước bean method target | Có |
| `@Import` / `DeferredImportSelector` | Chọn configuration classes tham gia parsing | Không phải bean instance ordering |
| `@Conditional` | Quyết định một definition có được register không | Không |
| `@Bean(bootstrap=BACKGROUND)` | Hỗ trợ concurrent bean initialization ở Spring 6.2 khi có bootstrap executor | Ordering/await có quy tắc riêng, không đồng nghĩa @DependsOn |

**Đặc biệt:** `@Order(1)` trên service A và `@Order(2)` trên service B không phải cách tin cậy để bảo đảm A được instantiate trước B. Nếu B phải init sau A, dùng **DI** hoặc **`@DependsOn("A")`** theo bản chất quan hệ.

## 12. Khi nào BeanDefinition reader áp dụng depends-on?

Không phải processor tìm runtime annotations mỗi khi gọi method nghiệp vụ. Việc khai báo depends-on trở thành **metadata của BeanDefinition**. BeanFactory biết names cần resolve trước khi tạo bean phụ thuộc.

Quy trình khái niệm:

```text
Đọc @DependsOn trên @Bean hoặc scanned component
   / đọc depends-on từ XML
                  |
                  v
   BeanDefinition.setDependsOn("setup", ...)
                  |
                  v
       BeanFactory.getBean("dependent")
                  |
                  +-- kiểm tra registered prerequisite names
                  +-- detect dependency cycle theo graph
                  +-- getBean("setup") trước
                  +-- sau đó instantiate dependent
                  |
                  v
         register dependency relationship
                  |
                  v
       destruction order cho singletons
```

Đừng dùng diagram này để suy ra mọi bước nội bộ/version giống hệt nhau; nó mô tả **hợp đồng quan hệ** và vì sao dependency order không cần inject object trực tiếp.

### 12.1. Đọc BeanDefinition metadata khi debug

Trong một `AnnotationConfigApplicationContext`:

```java
var definition =
        context.getBeanFactory().getBeanDefinition("javaWorker");
String[] required = definition.getDependsOn();
```

Với demo, mảng này chứa `"javaSetup"`. `getDependsOn()` có thể trả `null` nếu bean không có explicit depends-on. Đây là cách xác minh metadata mà không cần in constructor timestamps.

### 12.2. Bean names và aliases

Spring BeanFactory có cơ chế resolve aliases. Dùng bean name đúng/đã đăng ký hoặc alias hợp lệ trong `@DependsOn`. Tuy nhiên, đối với maintainability nên trỏ tới **canonical bean name** ổn định, đặc biệt khi nhiều XML sources và Java configs được ghép vào một context.

Nếu prerequisite được đăng ký dưới tên `"pluginRegistry"` mà `@DependsOn("PluginRegistry")` dùng Java class short name sai, context không tự đoán type. Hãy kiểm tra tên thực tế bằng `getBeanDefinitionNames()` hoặc metadata.

## 13. Nhiều prerequisites và thứ tự giữa chúng

Ví dụ:

```java
@Bean("worker")
@DependsOn({"cacheSetup", "metricsSetup"})
Worker worker() {
    return new Worker();
}
```

Quan hệ được yêu cầu:

```text
cacheSetup -----+
                +--> worker
metricsSetup ---+
```

Không có yêu cầu nghiệp vụ nào nói `cacheSetup` phải xảy ra trước `metricsSetup`, trừ khi bạn khai báo thêm quan hệ đó. Nếu thực tế cần:

```text
cacheSetup --> metricsSetup --> worker
```

thì **metricsSetup** cần phụ thuộc cacheSetup, **worker** cần phụ thuộc metricsSetup. Không nên chỉ đặt hai names trong một mảng và dựa vào thứ tự liệt kê như chính sách bắt buộc giữa hai prerequisites.

### 13.1. Nguyên tắc thiết kế dependency graph

- Mỗi quan hệ chỉ nên tồn tại khi có một invariant thật.
- Tránh gắn “mọi bean phụ thuộc globalSetup” nếu không liên quan.
- Dependency graph nên là đồ thị có hướng không chu trình.
- Xác định rõ ai sở hữu initialization side effect.
- Coi ordering metadata là API cấu hình, cần regression test.

### 13.2. Vì sao không nên chỉ sắp @Bean methods?

Java source order không phải cơ chế quản lý dependency graph mà application nên dựa vào. Spring có thể parse metadata, resolve constructor dependencies, ưu tiên infrastructure và hoạt động khác qua versions. Sử dụng depends-on/DI để diễn đạt **điều kiện bắt buộc**, không phải thứ tự dòng mã.

## 14. Lỗi vòng phụ thuộc depends-on

Vòng constraint:

```java
@Bean("alpha")
@DependsOn("beta")
Alpha alpha() {
    return new Alpha();
}

@Bean("beta")
@DependsOn("alpha")
Beta beta() {
    return new Beta();
}
```

là một graph không thể thỏa: alpha yêu cầu beta tạo trước, beta lại yêu cầu alpha tạo trước. Spring có thể báo lỗi circular depends-on/circular dependency trong startup hoặc lookup. Không sửa bằng đổi thứ tự khai báo methods: **phải phá vòng phụ thuộc thiết kế**.

Một chain hợp lệ:

```text
driverRegistration --> registrySetup --> worker
```

Trong khi cycle:

```text
A --> B --> C --> A
```

không thể có total init order.

### 14.1. Circular DI và circular depends-on khác nhau

Circular constructor injection giữa A/B cũng là vấn đề, nhưng ở đó các constructors yêu cầu object instances của nhau. Với circular depends-on, có thể cả hai classes đều no-arg constructors mà vẫn lỗi chỉ vì metadata ordering tạo vòng. Hai loại cycle có nguyên nhân và cách khắc phục khác nhau.

### 14.2. Debug graph

Khi gặp exception liên quan `Circular depends-on relationship`, xem `@DependsOn`/XML `depends-on` của **tất cả bean names trong cycle**, bao gồm alias resolution và registrations từ nhiều config sources. Đừng chỉ xem Java fields vì depends-on có thể không được phản ánh trong type-level dependency graph.

## 15. Missing bean và điều kiện đăng ký

`@DependsOn("registry")` yêu cầu bean name `registry` tồn tại khi dependent được tạo. Nếu registry không được đăng ký (không scan, profile tắt, `@Conditional` loại, thiếu XML import), Spring không tự tạo bean tương ứng bằng cách tìm class cùng tên.

### Ví dụ với @Profile

```java
@Configuration
class FeaturesConfig {
    @Bean("optionalRegistry")
    @Profile("reporting")
    Registry optionalRegistry() {
        return new Registry();
    }

    @Bean("reportWorker")
    @DependsOn("optionalRegistry")
    ReportWorker reportWorker() {
        return new ReportWorker();
    }
}
```

Đây là snippet minh họa cần imports và types phù hợp. Nếu profile `reporting` không active, `optionalRegistry` không được register, còn `reportWorker` vẫn active, việc tạo reportWorker sẽ lỗi vì missing required depends-on bean. Nếu worker chỉ có ý nghĩa khi feature bật, đặt profile/condition phù hợp cho **cả hai**, hoặc thiết kế optional dependency contract rõ ràng.

**Đừng dùng @DependsOn để chỉ ra một *optional* bean không chắc tồn tại.** Đây là hard prerequisite.

## 16. @DependsOn và Spring singleton destruction

Nhắc lại quy tắc:

```text
A depends on B
Init: B trước A
Destroy (singletons): A trước B
```

Điều này đặc biệt có ích khi A trong `destroy()` muốn dùng infrastructure B:

```java
final class ClientSession implements DisposableBean {
    private final Registry registry;

    ClientSession(Registry registry) {
        this.registry = registry;
    }

    @Override
    public void destroy() {
        registry.unregister(this);
    }
}
```

Ở snippet này **DI đã tự thể hiện dependency** qua constructor, Spring có thể theo dõi dependent registration khi resolve dependencies, nên không tự động phải thêm `@DependsOn` nếu chỉ để làm rõ thứ tự. Nếu legacy client dùng static `Registry.global()` (không có injected reference), `@DependsOn("registry")` có thể có giá trị thêm để hướng dẫn destruction order.

### 16.1. Những điều không được đảm bảo

- Không quản lý thứ tự dừng của bất kỳ thread tự sinh nào nếu chúng nằm ngoài Spring lifecycle.
- Không đảm bảo thời điểm close resources theo hệ phân tán nhiều JVM/Pods.
- Không thay quyền sở hữu tài nguyên giữa hai contexts khác nhau.
- Không tạo một tổng thứ tự destruction chính xác cho mọi bean không liên hệ.
- Không thay thế các semantics của `SmartLifecycle` phases khi cần startup/shutdown coordination nhiều bước.
- Không tự đảm bảo rằng external database hay broker thật sự sẵn sàng khi bean init kết thúc.

## 17. Sự khác biệt giữa init callback và async ready

Giả sử:

```java
class AsyncWarmup {
    public void initialize() {
        new Thread(this::loadCache).start();
    }

    private void loadCache() {
        // Tải dữ liệu đắt tiền ở thread khác.
    }
}
```

Đây là mã minh họa, không khuyến nghị tạo unmanaged thread trong initializer. Nếu `initialize()` return ngay, Spring có thể coi bean đã init hoàn tất theo callback contract và tiếp tục tạo dependent bean, mặc dù `loadCache()` còn chạy.

### Khi cần readiness thật

Cân nhắc:

- Một init operation đồng bộ có timeout rõ, nếu startup buộc phải chờ.
- `SmartLifecycle` và thứ tự phases khi cần quản lý start/stop.
- `ApplicationListener<ContextRefreshedEvent>` hoặc `@EventListener` nếu logic chỉ nên chạy sau refresh.
- Một readiness signal/health check được đo riêng với quá trình tạo bean.
- Dependency injection qua service có method `awaitReady()` hoặc API explicit, nếu caller cần chờ readiness.
- External orchestration nếu phụ thuộc database, Kafka broker hoặc service mạng khác.

**Không coi `@DependsOn` là distributed readiness barrier.** Nó chỉ quản lý thứ tự lifecycle mà Spring biết và thực thi.

## 18. Spring 6.2 background initialization và phụ thuộc

Spring Framework 6.2 hỗ trợ option `@Bean(bootstrap=BACKGROUND)` cho một số singleton bean initializations khi có hạ tầng bootstrap executor phù hợp. Đây là cơ chế **concurrent startup**, khác với `@Lazy` và `@DependsOn`.

Nếu background bean có prerequisite, Spring phải giải quyết dependency constraints trước khi thực sự có instance dependent hợp lệ. Spring docs nêu background initialization và `@DependsOn` phối hợp để một bean required được initialized trước khi background initialization của bean phụ thuộc khởi động, theo startup infrastructure.

Nhưng do có thread concurrency, bạn **không nên dựa vào thứ tự log/timestamp của hai unrelated background beans** như hợp đồng. Với dependency thực, dùng DI hoặc depends-on; với completion of asynchronous external work, cần readiness contract rõ ràng. Demo mục 10 cố ý dùng **synchronous startup** để output ổn định.

## 19. @DependsOn không phải global @Order cho tất cả beans

Một anti-pattern thường thấy:

```java
@DependsOn("allInfrastructureReady")
@Component
public class EveryService {
}
```

Nếu mọi service đều phụ thuộc một “mega initializer” không có clear lifecycle ownership, điều này biến application startup thành chuỗi cứng, khó test và dễ tạo cycle khi phát triển.

Một thiết kế tốt hơn:

- Chỉ annotate **những beans thực sự cần side effect**.
- Nếu nhiều consumers dùng resource/API, expose injectable service thay cho global shared mutable state.
- Dùng infrastructure-specific lifecycle hook nếu cần một phase tổng thể.
- Chia feature dependencies thành graph nhỏ dễ hiểu.
- Dùng integration tests đo chính xác order của một vài critical beans.

### @DependsOn không bảo đảm bean hoàn tất business workflow

Nếu `OrderConsumer` được tạo sau `PaymentGateway`, điều đó không nói `PaymentGateway` đã xử lý hết outstanding payments. Bean initialization chỉ liên quan readiness cơ bản của instance theo callback contract, không điều khiển mọi runtime workflow.

## 20. Checklist khi dùng @DependsOn

1. Có một side effect prerequisite **thật sự cần** trước khi dependent bean được khởi tạo không?
2. Nếu phụ thuộc là object thật, có thể thay bằng constructor injection không?
3. Prerequisite bean đã có **tên ổn định** trong context chưa?
4. Target được đăng ký qua `@Bean`, component scan hay XML? Chọn đúng vị trí annotation/attribute.
5. Đã tránh nhầm class-level `@DependsOn` cho bean chỉ được đăng ký bằng XML chưa?
6. Quan hệ phụ thuộc có làm lazy prerequisite khởi tạo sớm không?
7. Có nhiều prerequisites không? Nếu chúng phụ thuộc lẫn nhau, đã biểu diễn edges riêng chưa?
8. Graph có cycle nào không?
9. Nếu bean profile-/condition-dependent, prerequisite và dependent có active cùng điều kiện hợp lý không?
10. Initialization callback của prerequisite có **đồng bộ thật sự** hoàn thành side effect cần thiết không?
11. Shutdown order của singleton beans có cần thiết/đúng semantics không?
12. Có thread/remote system nào cần readiness riêng ngoài BeanFactory lifecycle không?
13. Đã test startup **và** shutdown order bằng recorded events chưa?
14. Trong code review, annotation có giải thích “vì sao cần ordering” không?

## 21. Cách debug khi @DependsOn không như mong đợi

### 21.1. Bean name sai

Nếu `@DependsOn("cacheInit")` mà bean thực có name `cacheInitializer`, Spring không chọn target theo Java type. Kiểm tra names đã được register, bao gồm aliases.

### 21.2. Class-level annotation không được áp dụng

Nếu `@DependsOn` đặt trên một class rồi class được đăng ký bằng XML `<bean>`, hãy dùng XML `depends-on`. Javadoc lưu ý điểm khác biệt này.

### 21.3. Bean chưa được đăng ký

Nếu target chưa được component scan, XML import hoặc Java config xử lý, depends-on không thể “tự tìm class trên classpath”.

### 21.4. Dependency cycle

Xác định graph constraints theo names. Cắt quan hệ vòng thay vì đổi thứ tự source code.

### 21.5. Lazy bean vẫn init sớm

Đó là semantics đúng nếu dependent non-lazy cần nó. Muốn true laziness, loại bỏ ordering constraint không cần thiết hoặc thiết kế cách resolve khác.

### 21.6. Init callback return trước khi side effect sẵn sàng

Tự đồng bộ readiness, thay đổi lifecycle hook hoặc design API. Depends-on chỉ bảo đảm callback đồng bộ đã return.

### 21.7. Có nhiều contexts

Depends-on chỉ có ý nghĩa với BeanFactory/context mà names được resolve. Root và child contexts không phải một global mutable registry duy nhất; object ownership và shutdown order liên-context cần thiết kế riêng.

### 21.8. Test bằng log không ổn định

Dùng một recorder trong constructor/`afterPropertiesSet`/`destroy` như demo. Assertions có thể kiểm tra quan hệ `setup.init < worker.construct` và `worker.destroy < setup.destroy` (với singletons).

### 21.9. Không thực sự dùng container tạo bean

Nếu Java code tự `new Worker()`, metadata `@DependsOn` không tự điều khiển constructor. Bean lifecycle ordering cần Spring quản lý object.

### 21.10. @Order được thêm mà lỗi vẫn còn

`@Order` không tạo depends-on metadata. Dùng đúng annotation/constructor injection.

## 22. Câu hỏi ôn tập có đáp án

**Câu 1.** Khi nào `@DependsOn` có giá trị hơn constructor injection?

**Đáp:** Khi quan hệ chỉ là prerequisite side effect / initialization order, không cần truyền object dependency vào constructor.

**Câu 2.** `@DependsOn("setup")` đặt trên bean A nghĩa là gì?

**Đáp:** Bean `setup` phải được Spring tạo/khởi tạo trước A, theo container lifecycle.

**Câu 3.** `@DependsOn` có inject object setup vào A không?

**Đáp:** Không. Muốn A giữ object setup, phải dùng DI.

**Câu 4.** Với A depends on B, destruction order singleton thế nào?

**Đáp:** A được destroy trước B; thứ tự init là B trước A.

**Câu 5.** `@Order(1)` trên A và `@Order(2)` trên B có bảo đảm A init trước B không?

**Đáp:** Không. @Order không phải lifecycle dependency declaration.

**Câu 6.** XML `depends-on` cho hai prerequisites viết ra sao?

**Đáp:** `depends-on="registry,metrics"` hoặc danh sách names dùng delimiter hợp lệ.

**Câu 7.** Class-level `@DependsOn` có áp dụng khi class chỉ được XML `<bean>` đăng ký không?

**Đáp:** Theo Javadoc, không; dùng `depends-on` XML.

**Câu 8.** Bean prerequisite có `@Lazy` nhưng dependent là eager singleton, prerequisite có thể init trong refresh không?

**Đáp:** Có. Ordering constraint buộc Spring tạo prerequisite trước dependent.

**Câu 9.** Một initializer mở thread nền và return ngay, `@DependsOn` có đợi thread đó xử lý xong không?

**Đáp:** Không tự động. Cần đồng bộ readiness theo hợp đồng khác.

**Câu 10.** `@DependsOn({"A","B"})` có yêu cầu A được init trước B không?

**Đáp:** Mục đích chính là cả A và B init trước dependent. Nếu A cần trước B, khai báo thêm B depends on A.

**Câu 11.** Tại sao cycle A depends on B, B depends on A là lỗi dù constructors không có tham số?

**Đáp:** Vì metadata ordering tạo constraint bất khả thi.

**Câu 12.** Có thể đặt `@DependsOn` trên method `@Bean` không?

**Đáp:** Có, đây là một vị trí được Javadoc hỗ trợ và phù hợp khi muốn điều khiển thứ tự bean cụ thể.

**Câu 13.** Vì sao @DependsOn không phải cách kiểm tra service mạng đã sẵn sàng?

**Đáp:** Nó quản lý Spring bean initialization, không xác minh external service health hoặc asynchronous task completion.

**Câu 14.** Nếu metadata `getDependsOn()` của BeanDefinition là null, điều đó nói gì?

**Đáp:** Definition không có explicit depends-on names trong metadata đó; không có nghĩa không có constructor/setter DI dependency.

**Câu 15.** Một singleton dependent được destroy trước prerequisite có ích khi nào?

**Đáp:** Khi dependent cần prerequisite infrastructure để unregister/flush/cleanup trong lúc shutdown.

**Câu 16.** Nếu JavaConfig khai báo worker trước setup trong source, có thể dựa vào thứ tự dòng để tạo đúng không?

**Đáp:** Không. Đặt `@DependsOn("setup")` hoặc dùng DI là cách nói rõ constraint.

## 23. Bài tập thực hành

### Bài 1 — Bỏ @DependsOn

Xóa `@DependsOn("javaSetup")` của `javaWorker`. Dự đoán ordering có còn được bảo đảm không. Chạy demo để xem kết quả trong môi trường của bạn, nhưng nhớ: **một lần chạy có thể tình cờ setup trước worker không đồng nghĩa có contract**.

### Bài 2 — Trỏ tới tên bean không tồn tại

Đổi `@DependsOn("javaSetup")` thành `@DependsOn("wrongSetup")`. Dự đoán context creation failure vì bean prerequisite không tồn tại.

### Bài 3 — Tạo cycle

Gắn thêm `@DependsOn("javaWorker")` lên method `javaSetup`. Dự đoán dependency cycle error. Không sửa bằng đổi thứ tự khai báo.

### Bài 4 — XML nhiều prerequisites

Thêm bean `xmlMetrics` và đặt `depends-on="xmlSetup,xmlMetrics"` trên `xmlWorker`. Kiểm chứng cả hai prerequisites có init events trước worker. Đừng dùng output này để suy ra thứ tự business-level giữa hai prerequisites không liên quan.

### Bài 5 — Chuyển scan sang XML

Thử nạp class `ScanWorker` bằng XML `<bean id="scanWorker"...>`, không dùng component scan, và **không** đặt `depends-on` XML. So sánh behavior với scanner-based case; sau đó thêm `depends-on="scanSetup"` vào XML để khôi phục constraint.

### Bài 6 — Lazy prerequisite

Đổi `activeWorker` thành `@Lazy` và không gọi `getBean("activeWorker")` ngay. Xem liệu `lazySetup` còn bị tạo trong refresh không. Sau đó lookup activeWorker và quan sát thứ tự hai constructors.

### Bài 7 — Async initializer

Cho `Tracked.afterPropertiesSet()` của prerequisite mở task async return ngay, rồi mô phỏng dependent cần dữ liệu từ task. Thiết kế lại synchronization/readiness để không có race. Tránh lấy `Thread.sleep()` ngẫu nhiên làm giải pháp production.

### Bài 8 — Tách side effect khỏi business bean

Nếu bạn có `GlobalRegistry` static, thử refactor thành một interface `Registry` và inject vào consumer qua constructor. Giải thích vì sao type-safe DI dễ test hơn và có thể loại bỏ `@DependsOn` không cần thiết.

## 24. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html): lý do sử dụng, XML syntax, nhiều prerequisites và shutdown order.
- [Spring Framework 6.2.19 Javadoc — DependsOn](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DependsOn.html): đối tượng/điểm gắn annotation, constructor/initialization semantics và caveat với class-level XML registration.
- [Spring Framework Reference — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html): DI dependency đã đủ biểu diễn thứ tự khởi tạo.
- [Spring Framework Reference — Java @Bean](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html): phương thức factory và dependency method parameters.
- [Spring Framework Reference — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html): lazy singleton có thể bị eager dependency kéo vào startup.
- [Spring Framework Reference — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/core/beans/java/composing-configuration-classes.html): `@DependsOn` và background initialization trong Spring 6.2.
- [Spring Framework Reference — Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): `InitializingBean`, `DisposableBean`, lifecycle ordering.
- [Spring Framework 6.2.19 Javadoc — BeanDefinition](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html): `getDependsOn()` và bean metadata.

**Kết luận:** `@DependsOn` (Java) và `depends-on` (XML) dành cho các **phụ thuộc khởi tạo không được thể hiện trực tiếp bằng object injection**. Spring tạo các prerequisite beans trước dependent bean và, với singleton relationships, destroy dependent trước prerequisite. Đây là **bean lifecycle ordering**, không phải DI, `@Order`, background-task completion, hay readiness của một hệ thống phân tán. Hãy ưu tiên constructor injection cho object dependencies thật; chỉ sử dụng depends-on khi có side effect prerequisite rõ ràng và kiểm chứng bằng integration tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-006">Thứ tự khởi tạo xuất phát từ đồ thị phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự khởi tạo xuất phát từ đồ thị phụ thuộc trong Spring Core Container

## 1. Mục tiêu bài học

Trong Spring Framework, việc một bean được khởi tạo trước hay sau bean khác **chủ yếu được quyết định bởi quan hệ phụ thuộc**, chứ không phải thứ tự bạn đặt phương thức `@Bean`, thứ tự class trong `@Import`, vị trí `<bean>` trong XML hay giá trị `@Order` của bean. Một bean cần một dependency để hoàn tất constructor hoặc factory method phải đợi dependency ấy được giải quyết.

Ví dụ:

```java
@Configuration(proxyBeanMethods = false)
class CatalogConfig {
    @Bean
    CatalogRepository catalogRepository() {
        return new InMemoryCatalogRepository();
    }

    @Bean
    CatalogService catalogService(CatalogRepository repository) {
        return new CatalogService(repository);
    }

    @Bean
    CatalogController catalogController(CatalogService service) {
        return new CatalogController(service);
    }
}
```

Đây là **minh họa**, cần các kiểu Java phù hợp. Nhìn vào chữ ký của các factory methods, Spring biết:

```text
catalogController -> catalogService -> catalogRepository
```

Mũi tên nghĩa là **bean bên trái cần bean bên phải**. Do đó khi phải tạo `catalogController`, Spring đảm bảo `catalogRepository` và `catalogService` được tạo/giải quyết đủ để truyền vào constructors/factory methods trước khi hoàn tất controller.

Nhưng nếu có thêm `metricsExporter` không liên quan ba bean này, **không thể kết luận** nó chắc chắn tạo trước hoặc sau `catalogRepository` chỉ vì method `metricsExporter()` viết ở dòng đầu của class.

Sau bài học, bạn có thể:

1. Vẽ và đọc **đồ thị phụ thuộc có hướng** từ constructors, setter/property injection, factory method parameters và metadata `depends-on`.
2. Phân biệt **thứ tự từng phần (partial order)** với một thứ tự toàn phần cố định.
3. Hiểu Spring tạo regular singleton không lazy trong `ApplicationContext.refresh()` nhưng vẫn giải quyết những dependencies trước.
4. Biết Spring có thể tạo một lazy bean sớm vì là dependency của eager bean.
5. Dùng `@Bean` method parameters và constructor injection để công khai quan hệ phụ thuộc.
6. Dùng `@DependsOn`/XML `depends-on` cho **ràng buộc khởi tạo không thể hiện qua reference trực tiếp**, và hiểu hệ quả đối với destruction order.
7. Phân biệt `@Order`, `Ordered` và `@Priority` dùng cho sorting/selection với bean startup order.
8. Phân biệt `@Import`/`@ImportResource`, XML file order và actual bean dependency order.
9. Nhận ra circular dependencies và chọn cách tái cấu trúc thích hợp.
10. Hiểu điểm mở rộng như `SmartInitializingSingleton`, `SmartLifecycle` và `ContextRefreshedEvent` không đồng nghĩa với constructor dependency ordering.
11. Chạy ví dụ **Java 17 + Spring Framework 6.2** có các assertions về thứ tự đúng hợp đồng nhưng không phụ thuộc thứ tự tình cờ của các beans độc lập.
12. Viết tests và debug các lỗi initialization/circular dependency trong ứng dụng thật.

**Tư tưởng chính:** Spring bảo đảm **A đã có dependency B cần thiết khi A được tạo**; Spring không bảo đảm **mọi bean độc lập có thứ tự startup theo một quy tắc mà bạn có thể dùng làm workflow nghiệp vụ**.

## 2. Đồ thị phụ thuộc là gì?

Mô hình hóa mỗi bean definition/bean instance cần tạo bằng một **đỉnh**. Một dependency từ bean A tới bean B được vẽ:

```text
A --> B
```

Nghĩa là “để khởi tạo A, container cần B”, không phải “A chắc chắn sẽ gọi B trong runtime”.

Cho các bean:

- `Controller` cần `Service`.
- `Service` cần `Repository` và `Clock`.
- `Repository` không cần `Service`.
- `MetricsExporter` độc lập với ba bean còn lại.

Đồ thị:

```text
                   Controller
                       |
                       v
                    Service
                    /     \
                   v       v
             Repository   Clock

             MetricsExporter
              (độc lập)
```

Spring biết các **ràng buộc**:

- Repository phải được tạo/giải quyết trước khi Service được hoàn tất.
- Clock phải được tạo/giải quyết trước khi Service được hoàn tất.
- Service phải được tạo/giải quyết trước khi Controller được hoàn tất.
- Theo tính bắc cầu, Repository và Clock cần sẵn sàng trước Controller.

Nhưng giữa **Repository và Clock** không có cạnh trực tiếp; cả hai có thể được tạo theo một trong các thứ tự hợp lệ. **MetricsExporter** cũng không có cạnh nối, nên không được giả định vị trí của nó trong một thứ tự toàn phần.

### 2.1. Thứ tự từng phần, không phải lịch trình cố định

Trong lý thuyết đồ thị, tập điều kiện “B trước A” như trên tạo **partial order** khi không có chu trình. Có nhiều **topological orders** hợp lệ:

```text
Repository, Clock, Service, Controller, MetricsExporter
Clock, Repository, MetricsExporter, Service, Controller
MetricsExporter, Clock, Repository, Service, Controller
```

Ba chuỗi chỉ là **những thứ tự có thể thỏa cùng constraints**, không phải cam kết Spring sẽ sử dụng chính một trong ba chuỗi này. Quyền ưu tiên ở đây là đáp ứng đúng dependencies và lifecycle; thời điểm chính xác giữa các node độc lập là chi tiết không nên dựa vào.

### 2.2. Thứ tự tạo instance khác thứ tự đăng ký definition

`BeanDefinition` là metadata về bean: class, factory method, scope, lazy, constructor args, properties, depends-on và các lựa chọn khác. Các definitions có thể được đăng ký trước khi instance nào được tạo.

```text
register definitions:
    Controller
    MetricsExporter
    Service
    Repository
    Clock

instantiate as needed:
    Repository / Clock -> Service -> Controller
    MetricsExporter (độc lập)
```

Nếu đọc danh sách bean definition names, bạn có thể thấy thứ tự đăng ký. **Đừng dùng thứ tự đó như thứ tự nghiệp vụ khởi tạo**.

## 3. Cách Spring quyết định thời điểm tạo bean

Một `ApplicationContext` thông thường làm nhiều bước khi `refresh()`:

```text
Tìm/đọc Java config, XML, component metadata
                    |
                    v
Đăng ký BeanDefinitions
                    |
                    v
Xử lý BeanFactoryPostProcessor / BeanPostProcessor
                    |
                    v
Pre-instantiate regular non-lazy singletons
                    |
                    +--> lấy bean A
                    |      |
                    |      +--> A cần bean B
                    |                |
                    |                +--> B cần bean C
                    |                |       |
                    |                |       v
                    |                |     tạo C
                    |                v
                    |              tạo B
                    v
                  tạo A
                    |
                    v
Kết thúc pre-instantiation / refresh
```

Đây là **mô hình khái niệm**. Spring có hạ tầng đặc biệt cho proxy, bean post-processors, `FactoryBean`, circular reference handling, scoped beans và Spring 6.2 background bootstrap; không diễn giải sơ đồ như thuật toán từng dòng cho mọi implementation.

### 3.1. Spring không chỉ tuân theo thứ tự khai báo methods

```java
@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    Controller controller(Service service) {
        return new Controller(service);
    }

    @Bean
    Service service(Repository repository) {
        return new Service(repository);
    }

    @Bean
    Repository repository() {
        return new Repository();
    }
}
```

Nếu `controller` được yêu cầu, container vẫn phải giải quyết `Service` và `Repository` trước khi có thể gọi constructor/factory method với các arguments cần thiết, dù factory method `controller` được viết trước trong source.

### 3.2. Spring không cần một bean đã được tạo để biết dependency

Spring biết type/metadata của constructors, factory methods, `@Autowired` và XML references. Nó có thể resolve dependencies **lúc tạo bean**, dựa trên registry chứa các definitions.

Một Java bean implementation có thể được khai báo tại config khác, nhưng BeanFactory vẫn tìm được nếu tất cả definitions thuộc cùng context.

### 3.3. Bean được đánh lazy có thể xuất hiện trong thứ tự startup

```java
@Bean
@Lazy
Repository lazyRepository() {
    return new Repository();
}

@Bean
Service eagerService(Repository lazyRepository) {
    return new Service(lazyRepository);
}
```

Ở ví dụ minh họa này, nếu `eagerService` là singleton không lazy, Spring phải resolve `Repository` ngay trong startup. `@Lazy` trên definition của repository chỉ ngăn chủ động pre-instantiate độc lập, không ngăn nó được tạo như một dependency bắt buộc của bean eager.

## 4. Những kiểu cạnh dependency mà Spring nhận ra

Spring có thể hình thành dependency relationships qua nhiều cơ chế.

### 4.1. Constructor injection

```java
final class OrderService {
    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

Nếu Spring tạo `OrderService` và constructor yêu cầu `OrderRepository`, repository phải có trước khi lời gọi constructor hoàn thành.

### 4.2. Tham số của @Bean factory method

```java
@Bean
OrderService orderService(OrderRepository repository) {
    return new OrderService(repository);
}
```

BeanFactory phải resolve `OrderRepository` trước khi gọi `orderService(repository)`. Đây là cách diễn đạt rất phù hợp khi một class `@Configuration` tạo nhiều beans từ các modules khác nhau.

### 4.3. Setter/property injection

```java
final class TaskProcessor {
    private TaskRepository repository;

    public void setRepository(TaskRepository repository) {
        this.repository = repository;
    }
}
```

XML:

```xml
<bean id="taskProcessor" class="example.TaskProcessor">
    <property name="repository" ref="taskRepository"/>
</bean>
```

BeanFactory cần repository khi populate properties trước các bước initialization tương ứng. Ở setter injection, **raw object TaskProcessor có thể đã qua constructor trước khi repository được inject**. Vì vậy câu “dependency trước consumer” thường phải hiểu là **dependency phải được resolve trước khi consumer hoàn tất initialization**, không phải mọi constructor của mọi consumer đều bắt buộc chạy sau mọi constructor của dependencies, nhất là khi dùng setter/field injection hoặc circular-reference mechanisms.

### 4.4. @Autowired field

```java
@Component
class BillingFacade {
    @Autowired
    BillingService billingService;
}
```

Field injection cũng khiến container phải resolve `BillingService` trong giai đoạn population của `BillingFacade`. Tuy nhiên constructor của facade có thể chạy **trước** lúc field được inject. Đó là một lý do constructor injection thường giúp dependency graph minh bạch hơn và bảo đảm invariant sớm hơn.

### 4.5. @DependsOn / depends-on metadata

```java
@Bean
@DependsOn("schemaValidator")
DataAccessService dataAccessService() {
    return new DataAccessService();
}
```

Ở đây `dataAccessService` không nhận reference `schemaValidator` qua tham số, nhưng metadata nói phải khởi tạo `schemaValidator` trước khi tạo bean service. Đó là một **cạnh ordering explicit**.

### 4.6. Lookup động là cạnh phụ thuộc muộn

```java
final class Exporter {
    private final ObjectProvider<RemoteGateway> gateway;

    Exporter(ObjectProvider<RemoteGateway> gateway) {
        this.gateway = gateway;
    }

    void sendLater() {
        gateway.getObject().send();
    }
}
```

Đây là minh họa cần `ObjectProvider`/`RemoteGateway` phù hợp. Constructor `Exporter` có thể được tạo mà không cần instantiate gateway target ngay. Dependency được resolve **khi method gọi provider**, nên không phải cạnh “gateway target phải tạo trước constructor Exporter”.

## 5. Phân biệt dependency graph và import graph

`@Import` là cơ chế **ghép bean definitions/configuration classes**, không phải ràng buộc mọi bean trong config A đều được tạo trước mọi bean trong config B.

```java
@Configuration(proxyBeanMethods = false)
@Import({RepositoryConfig.class, ServiceConfig.class})
class RootConfig {
}
```

Điều này đưa hai classes cấu hình vào pipeline, tạo metadata bean definitions trong cùng container. Nhưng thứ tự:

```java
@Import({RepositoryConfig.class, ServiceConfig.class})
```

không nên được dùng để nói “tất cả repositories sẽ hoàn tất trước tất cả services”. Nếu một bean service cần repository, hãy đặt constructor/factory method dependency. Nếu hai beans độc lập, không có ordering contract nghiệp vụ.

### Ví dụ đúng: ghép theo root, inject theo type

```java
@Configuration(proxyBeanMethods = false)
class RepositoryConfig {
    @Bean
    PurchaseRepository purchaseRepository() {
        return new InMemoryPurchaseRepository();
    }
}

@Configuration(proxyBeanMethods = false)
class ServiceConfig {
    @Bean
    PurchaseService purchaseService(PurchaseRepository repository) {
        return new PurchaseService(repository);
    }
}

@Configuration(proxyBeanMethods = false)
@Import({RepositoryConfig.class, ServiceConfig.class})
class RootConfig {
}
```

Đây là ví dụ minh họa, cần các Java classes/imports tương ứng. **Không phải** `ServiceConfig` phải constructor-inject `RepositoryConfig` rồi gọi `repositoryConfig.purchaseRepository()`; BeanFactory tự resolve `PurchaseRepository` đã được khai báo trong cùng context.

### XML imports cũng chỉ ghép metadata

```xml
<import resource="modules/services.xml"/>
<import resource="modules/repositories.xml"/>
```

Nếu `services.xml` chứa `<constructor-arg ref="purchaseRepository"/>`, Spring vẫn biết service cần repository. Thứ tự liệt kê files không biến thành thứ tự phải chạy business logic, và Spring không tạo hai BeanFactories tách biệt chỉ vì dùng hai XML.

## 6. `@DependsOn` thêm một cạnh vào đồ thị, không cấp object reference

Với constructor dependency, cạnh A→B nghĩa là A **cần instance B**. Với `@DependsOn("schemaValidator")` trên `reportService`, cạnh ấy chỉ có nghĩa BeanFactory phải **initialize schemaValidator trước reportService** dù reportService không nhận validator trong constructor:

```java
@Bean
@DependsOn("schemaValidator")
ReportService reportService() {
    return new ReportService();
}
```

Trong graph, hai dạng cạnh đều áp ràng buộc prerequisite → dependent khi tạo bean, nhưng chỉ **DI qua constructor/`@Bean` parameter** cấp collaborator object. Đừng dùng `@DependsOn` để lặp lại một dependency đã được biểu đạt rõ bằng parameter; dùng nó khi cần side effect khởi tạo riêng.

XML tương đương có `depends-on="schemaValidator"`; tên trong `@DependsOn` và XML là **bean name**, không phải tên Java class. Nếu dependent eager còn prerequisite `@Lazy`, cạnh này vẫn có thể làm prerequisite tạo trong refresh. Nếu hai bean có vòng A→B→A, đổi thứ tự `@Bean` methods hoặc thêm `@Order` không sửa được graph.

Ở đây vai trò của `@DependsOn` là **một dạng cạnh bổ sung khi suy ra partial order**. Bài riêng về `@DependsOn` đã giải thích metadata, nhiều prerequisites, XML/component scan và cách debug tên bean; bài này áp dụng chúng vào toàn bộ đồ thị dependency.

## 7. Destruction order: hướng ngược với initialization dependencies

Một thông tin dễ bỏ qua: `depends-on` không chỉ ảnh hưởng khởi tạo; với **singleton beans**, nó còn có thể chi phối **thứ tự hủy ngược**.

Giả sử A phụ thuộc B:

```text
Startup:
      B được initialize
      A được initialize

Shutdown:
      A được destroy
      B được destroy
```

Vì A có thể cần B để cleanup, container phải hủy A trước khi hủy B. Spring documentation xác nhận `depends-on` đối với singleton beans quy định dependent bean được destroy trước dependency.

### Ví dụ XML

```xml
<bean id="eventStore" class="example.EventStore"
      destroy-method="close"/>

<bean id="eventDispatcher" class="example.EventDispatcher"
      depends-on="eventStore"
      destroy-method="stop"/>
```

Nếu hai beans là managed singletons và context đóng bình thường, ordering relationship nhằm giữ `eventStore` sẵn trong lúc `eventDispatcher` shutdown.

### Giới hạn

- Ngữ nghĩa hủy phụ thuộc scope và lifecycle. Prototype beans không được BeanFactory quản lý toàn bộ destruction callbacks như singleton.
- Nếu chính destroy callback chạy I/O dài, bị treo hoặc lỗi, vẫn cần cơ chế quản lý tài nguyên, timeout và observability.
- Không dựa vào thứ tự destroy của hai beans hoàn toàn độc lập.
- Với custom scopes, proxy, parent-child contexts và external resources, phải kiểm tra hợp đồng lifecycle cụ thể.

**Đặc biệt:** `@DependsOn` hữu ích khi bạn thật sự cần thứ tự cả startup và cleanup của infrastructure beans; nhưng hãy nêu rõ lý do trong code review.

## 8. @Order KHÔNG điều khiển singleton startup order

Đây là lỗi hiểu phổ biến nhất trong chủ đề này.

`org.springframework.core.annotation.Order` dùng làm **ordering hint cho nhiều đối tượng/beans được sắp xếp**, ví dụ injection một `List<Handler>`, `Ordered` components, bean post-processors hoặc extension-point ordering theo hợp đồng. **Javadoc Spring ghi rõ giá trị @Order không quyết định thứ tự startup của singleton beans**; đó là một concern khác do dependency graph và `@DependsOn` ảnh hưởng.

Ví dụ:

```java
@Bean
@Order(20)
Notifier backupNotifier() {
    return new BasicNotifier("backup");
}

@Bean
@Order(10)
Notifier priorityNotifier() {
    return new BasicNotifier("priority");
}

@Bean
NotifierRegistry notifierRegistry(List<Notifier> notifiers) {
    return new NotifierRegistry(notifiers);
}
```

Nếu cả hai notifier beans được resolve vào `List<Notifier>`, Spring dùng `@Order` trên `@Bean` methods để sắp xếp **collection được inject**, nên `priority` đứng trước `backup`. Nhưng từ điều đó **không được suy ra** constructor của `priorityNotifier` đã chạy trước constructor của `backupNotifier`.

### @Order trên @Configuration class

Annotation `@Order` trên configuration classes có thể ảnh hưởng **thứ tự Spring đánh giá các configuration classes** trong pipeline tương ứng, nhưng không tự điều khiển thứ tự khởi tạo của mọi `@Bean` nằm bên trong chúng. Để ordering của beans, phải dựa trên dependency relations.

### @Primary không quyết định thứ tự tạo

`@Primary` giúp chọn bean khi injection point có nhiều candidates cùng type. Nó không có nghĩa bean primary sẽ luôn được construct trước bean khác.

### @Priority và Ordered cũng cần đọc đúng ngữ cảnh

`@Priority` và `Ordered` có thể ảnh hưởng việc lựa chọn/sắp xếp candidates trong các điểm mở rộng được thiết kế hỗ trợ chúng. Không coi các priority values là một lịch tạo tất cả singleton beans.

### Ví dụ đối chiếu

| Nhu cầu | Cơ chế đúng hơn |
| --- | --- |
| Service phải có repository khi xây constructor | Constructor injection / `@Bean` parameter |
| Client cần schema initializer được hoàn tất trước nhưng không giữ ref | `@DependsOn`/`depends-on` khi phù hợp |
| Pipeline nhận `List<Handler>` đúng thứ tự ưu tiên | `@Order`/`Ordered` hoặc ordering contract |
| Chọn một implementation mặc định trong nhiều candidates | `@Primary`/`@Qualifier` |
| Hoãn bean tốn kém cho tới first-use | `@Lazy`/`ObjectProvider` có chủ đích |
| Phối hợp start/stop listeners theo phase | `SmartLifecycle`/lifecycle coordinator |
| Chọn configuration classes nào được nạp | `@Import`/`ImportSelector` hoặc XML imports |

## 9. Thứ tự initialization và thứ tự *method execution* khác nhau

Giả sử `PaymentService` phụ thuộc `PaymentRepository`. Điều Spring bảo đảm là dependency được phân giải theo yêu cầu của bean creation, **không** phải tất cả những lời gọi method nghiệp vụ về sau đều có thứ tự cố định.

```java
PaymentService service = context.getBean(PaymentService.class);
service.pay(orderA);
service.pay(orderB);
```

Thứ tự hai lời gọi `pay` do **client code** điều phối, không được suy ra từ bean creation graph. Nếu có threads, events, reactive streams hoặc executors, runtime concurrency cần được thiết kế riêng.

### Constructor chạy xong chưa chắc bean đã initialized xong

Bean có thể còn trải qua:

- Dependency population/property injection.
- Aware callbacks.
- BeanPostProcessor hooks.
- `@PostConstruct`.
- `InitializingBean.afterPropertiesSet()`.
- Custom init methods.
- AOP proxy wrapping/exposure.

Với constructor injection, dependency được resolve trước constructor consumer. Với setter/field injection, constructor raw consumer có thể chạy trước injection rồi init callbacks mới diễn ra. Do đó “thứ tự khởi tạo” nên chỉ rõ bạn đang nói về **constructor start**, **constructor completion**, **property population** hay **bean fully initialized**.

## 10. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2

Ví dụ runnable tập trung vào **những ràng buộc thứ tự thực sự**, không ép thứ tự giữa các beans độc lập.

Bốn cơ chế được chứng minh:

1. `Repository` là dependency trực tiếp của `OrderService`.
2. `AuditInitializer` không được truyền vào constructor service, nhưng `@DependsOn("auditInitializer")` buộc nó initialize trước service.
3. `OrderController` phụ thuộc service, nên service được initialize trước controller.
4. `OptionalReport` lazy không có eager consumer nên chưa được tạo lúc refresh.
5. Hai `AlertChannel` được sắp theo `@Order` **khi inject vào `List<AlertChannel>`**, không dùng ordering ấy để assert constructor startup sequence.

Tất cả output được tính bằng các **quan hệ chắc chắn đúng**, tránh in một startup log dựa vào thứ tự tạo các beans không liên quan.

### 10.1. Cây dự án

```text
bean-dependency-order/
├── pom.xml
└── src/main/java/
    └── demo/
        └── BeanOrderDemo.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>bean-dependency-order</artifactId>
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

### 10.3. Mã Java đầy đủ

Tạo `src/main/java/demo/BeanOrderDemo.java`:

```java
package demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

public class BeanOrderDemo {

    public static final class Trace {
        private final List<String> events = new ArrayList<>();

        public void mark(String event) {
            events.add(event);
        }

        public boolean contains(String event) {
            return events.contains(event);
        }

        public boolean before(String first, String second) {
            int a = events.indexOf(first);
            int b = events.indexOf(second);
            return a >= 0 && b >= 0 && a < b;
        }
    }

    public interface Repository {
        String find(String sku);
    }

    public static final class MemoryRepository implements Repository {
        public MemoryRepository(Trace trace) {
            trace.mark("repository.ready");
        }

        @Override
        public String find(String sku) {
            return "item:" + sku;
        }
    }

    public static final class AuditInitializer {
        public AuditInitializer(Trace trace) {
            trace.mark("audit.ready");
        }
    }

    public static final class OrderService {
        private final Repository repository;

        public OrderService(Repository repository, Trace trace) {
            this.repository = repository;
            trace.mark("service.ready");
        }

        public Repository repository() {
            return repository;
        }

        public String order(String sku) {
            return repository.find(sku);
        }
    }

    public static final class OrderController {
        private final OrderService service;

        public OrderController(OrderService service, Trace trace) {
            this.service = service;
            trace.mark("controller.ready");
        }

        public String show(String sku) {
            return "view:" + service.order(sku);
        }
    }

    public static final class OptionalReport {
        public OptionalReport(Trace trace) {
            trace.mark("optional.ready");
        }
    }

    public interface AlertChannel {
        String label();
    }

    public static final class BasicAlertChannel implements AlertChannel {
        private final String value;

        public BasicAlertChannel(String value) {
            this.value = value;
        }

        @Override
        public String label() {
            return value;
        }
    }

    public static final class AlertCatalog {
        private final List<AlertChannel> channels;

        public AlertCatalog(List<AlertChannel> channels) {
            this.channels = List.copyOf(channels);
        }

        public String labels() {
            return String.join(",",
                    channels.stream().map(AlertChannel::label).toList());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        @Bean
        public Trace trace() {
            return new Trace();
        }

        @Bean
        public Repository repository(Trace trace) {
            return new MemoryRepository(trace);
        }

        @Bean
        public AuditInitializer auditInitializer(Trace trace) {
            return new AuditInitializer(trace);
        }

        @Bean
        @DependsOn("auditInitializer")
        public OrderService orderService(
                Repository repository, Trace trace) {
            return new OrderService(repository, trace);
        }

        @Bean
        public OrderController orderController(
                OrderService service, Trace trace) {
            return new OrderController(service, trace);
        }

        @Bean
        @Lazy
        public OptionalReport optionalReport(Trace trace) {
            return new OptionalReport(trace);
        }

        @Bean
        @Order(20)
        public AlertChannel backupAlertChannel() {
            return new BasicAlertChannel("backup");
        }

        @Bean
        @Order(10)
        public AlertChannel priorityAlertChannel() {
            return new BasicAlertChannel("priority");
        }

        @Bean
        public AlertCatalog alertCatalog(List<AlertChannel> channels) {
            return new AlertCatalog(channels);
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Trace trace = context.getBean(Trace.class);
            OrderService service = context.getBean(OrderService.class);
            OrderController controller =
                    context.getBean(OrderController.class);
            AlertCatalog alerts = context.getBean(AlertCatalog.class);

            System.out.println("repository.before.service=" +
                    trace.before("repository.ready", "service.ready"));
            System.out.println("audit.before.service=" +
                    trace.before("audit.ready", "service.ready"));
            System.out.println("service.before.controller=" +
                    trace.before("service.ready", "controller.ready"));
            System.out.println("managed.repository.same=" +
                    (service.repository() == context.getBean(Repository.class)));
            System.out.println("controller=" +
                    controller.show("BK-7"));
            System.out.println("alerts=" + alerts.labels());
            System.out.println("optional.before.lookup=" +
                    trace.contains("optional.ready"));

            OptionalReport first =
                    context.getBean(OptionalReport.class);
            OptionalReport second =
                    context.getBean(OptionalReport.class);

            System.out.println("optional.after.lookup=" +
                    trace.contains("optional.ready"));
            System.out.println("optional.sameSingleton=" + (first == second));
        }
    }
}
```

### 10.4. Lệnh chạy

Cần Java 17+, Maven và dependencies trong cache hoặc truy cập Maven repository để tải lần đầu:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=demo.BeanOrderDemo
```

### 10.5. Output dự kiến

Đây là các dòng **do chương trình in**, không tính các dòng Maven/Spring logs do môi trường cấu hình:

```text
repository.before.service=true
audit.before.service=true
service.before.controller=true
managed.repository.same=true
controller=view:item:BK-7
alerts=priority,backup
optional.before.lookup=false
optional.after.lookup=true
optional.sameSingleton=true
```

### 10.6. Giải thích output

**`repository.before.service=true`:** `orderService(Repository repository, Trace trace)` chỉ chạy khi Spring đã resolve repository singleton. `MemoryRepository` constructor đánh dấu `repository.ready` trước khi factory method tạo `OrderService` đánh dấu `service.ready`.

**`audit.before.service=true`:** `@DependsOn("auditInitializer")` bổ sung ordering edge, dù service constructor không nhận `AuditInitializer`. Spring phải tạo audit initializer trước order service.

**`service.before.controller=true`:** Controller yêu cầu `OrderService` ở factory method parameter. Spring hoàn tất tạo service trước khi factory method tạo controller đánh dấu `controller.ready`.

**`managed.repository.same=true`:** Service nhận managed Repository singleton qua BeanFactory resolution, không gọi trực tiếp `new MemoryRepository(...)` trong business code.

**`controller=view:item:BK-7`:** Controller sử dụng service và repository đã inject để tạo chuỗi. Thứ tự **sử dụng method runtime** do lời gọi `controller.show(...)` trong `main`, không phải do order annotations.

**`alerts=priority,backup`:** `@Order(10)` của `priorityAlertChannel` và `@Order(20)` của `backupAlertChannel` sắp xếp `List<AlertChannel>` injected vào `AlertCatalog`. Nó không chứng minh constructor priority channel chạy trước constructor backup channel.

**`optional.before.lookup=false`:** `OptionalReport` là lazy singleton, không có eager bean nào cần nó; constructor chưa đánh dấu sự kiện khi app refresh hoàn tất.

**`optional.after.lookup=true`:** Sau `context.getBean(OptionalReport.class)`, lazy bean được tạo và đánh dấu vào Trace.

**`optional.sameSingleton=true`:** Lazy không thay scope; hai tra cứu trong cùng context trả về cùng bean singleton.

### 10.7. Vì sao ví dụ không dựa vào thứ tự không được bảo đảm?

Ta không in toàn bộ `Trace.events` dưới dạng một thứ tự duy nhất. Nếu repository và audit initializer là hai prerequisites độc lập của service, không cần khẳng định ai được tạo trước ai. Hàm `trace.before(a,b)` chỉ kiểm tra những edges thực sự tồn tại. Đây là **phép kiểm đúng loại thuộc tính mà Spring cam kết**.

Chương trình cũng không dựa vào thứ tự các `@Bean` methods trong `AppConfig`, không cần component scan, không dùng static global mutable list, không có background threads và không gọi optional report trước khi đo laziness.

### 10.8. Tại sao @Configuration(proxyBeanMethods=false) vẫn đúng?

Tất cả dependencies trong demo được yêu cầu qua **tham số của `@Bean` methods**. Không có factory method nào gọi trực tiếp một `@Bean` method khác để tạo collaborator. Vì vậy không cần CGLIB interception của full configuration để bảo toàn singleton identity.

Nếu bạn đổi sang gọi `repository(trace())` như Java method trực tiếp trong `orderService()` khi `proxyBeanMethods=false`, bạn có thể tạo những Java objects ngoài BeanFactory và phá managed dependency identity. Đó không phải cách biểu diễn đồ thị dependencies.

## 11. Bài tập mở rộng project: minh họa @Order khác @DependsOn

Bạn có thể thử hai thay đổi riêng biệt để thấy chúng điều khiển những khía cạnh khác nhau.

### Thử A — Đảo @Order của các AlertChannel

Đổi:

```java
@Bean
@Order(5)
AlertChannel backupAlertChannel() {
    return new BasicAlertChannel("backup");
}

@Bean
@Order(50)
AlertChannel priorityAlertChannel() {
    return new BasicAlertChannel("priority");
}
```

Khi inject `List<AlertChannel>`, danh sách được sắp thành `backup,priority`. Nhưng **không** được suy luận Spring đã đảo construction order của hai singleton đó. Chỉ kiểm tra collection semantics.

### Thử B — Bỏ @DependsOn khỏi orderService

Bỏ:

```java
@DependsOn("auditInitializer")
```

Service vẫn có repository dependency, vì factory method nhận Repository. Nhưng **ràng buộc audit trước service không còn được khai báo**. Vì `auditInitializer` và `orderService` đều eager singletons độc lập ngoài các shared dependencies, startup có thể tình cờ vẫn đánh dấu audit trước service trong một build. **Không dùng một lần chạy như vậy để kết luận đảm bảo còn tồn tại.**

Test đúng là: khi bỏ annotation, **không còn hợp đồng** buộc audit initializer xong trước service. Nếu ứng dụng thực sự cần điều đó, giữ explicit dependency hoặc refactor để constructor service nhận collaborator cần thiết.

## 12. XML constructor-arg, property và depends-on

XML cũng xây dependency graph chung trong BeanFactory.

### Constructor dependency

```xml
<bean id="repository"
      class="example.InMemoryRepository"/>

<bean id="service"
      class="example.OrderService">
    <constructor-arg ref="repository"/>
</bean>

<bean id="controller"
      class="example.OrderController">
    <constructor-arg ref="service"/>
</bean>
```

Từ refs, có graph:

```text
controller -> service -> repository
```

Ngay cả khi ba `<bean>` được viết theo thứ tự controller, service, repository, Spring vẫn resolve refs để gọi constructors phù hợp. Các class ví dụ phải có constructor tương thích.

### Property dependency

```xml
<bean id="service" class="example.MutableService">
    <property name="repository" ref="repository"/>
</bean>
```

Spring tạo raw `MutableService` object rồi inject repository qua setter/property trước các init callbacks bình thường. Không nên hiểu rằng constructor của service nhất thiết chỉ bắt đầu **sau** constructor repository trong mọi setter injection scenario.

### Explicit depends-on

```xml
<bean id="eventStore" class="example.EventStore"/>

<bean id="dispatcher"
      class="example.Dispatcher"
      depends-on="eventStore"/>
```

Dù `Dispatcher` không có constructor/property reference đến `EventStore`, `depends-on` buộc store initialized trước dispatcher. Nếu singleton beans được destroy theo dependencies, dependent dispatcher được hủy trước event store.

### Một file XML hay nhiều file XML không làm đổi graph

`<import resource="repositories.xml"/>` và `<import resource="services.xml"/>` chỉ nhập bean definitions từ nhiều resources vào **cùng container**. Đồ thị DI vẫn thể hiện qua `ref` và explicit depends-on metadata. Không dựa vào vị trí imports làm thứ tự tạo tất cả beans trong từng file.

## 13. Dependency được tạo trước nhưng bean factory còn lifecycle

Khi nói “bean B phải được khởi tạo trước A”, có thể có nhiều dấu mốc:

- B constructor được gọi.
- B properties đã được populate.
- B đã chạy `@PostConstruct`.
- B đã chạy `InitializingBean.afterPropertiesSet()` hoặc custom init.
- B đã qua `BeanPostProcessor` và có thể được wrap thành proxy.
- A constructor/factory method nhận object phù hợp.
- A hoàn tất initialization.

### 13.1. Constructor injection và bean hoàn tất initialization

Với một non-circular constructor reference A(B), Spring thường resolve B thông qua BeanFactory trước khi có thể gọi constructor A. B được tạo và đi qua lifecycle thông thường để cung cấp reference phù hợp cho A.

### 13.2. Circular setter/field injection là trường hợp đặc biệt

Spring có thể hỗ trợ một số circular references bằng early singleton references khi được cấu hình cho phép. Trong những trường hợp đó, A có thể nhìn thấy reference tới bean B **đang trong quá trình tạo**, chưa hoàn toàn initialized. Vì vậy không thể phát biểu tuyệt đối rằng mọi dependency graph có cycle luôn có một topological initialization order.

### 13.3. BeanPostProcessor có thể trả proxy

A có thể nhận proxy cho B, không phải raw B object. Proxying không tự phá dependency contracts; nhưng các lazy-resolution proxies hoặc scoped proxies có thể trì hoãn việc tạo **target**. Hãy phân biệt “đã có proxy reference” với “target đã được construct”.

### 13.4. @PostConstruct không phải mốc mọi bean trong context đều xong

`@PostConstruct` của một bean được gọi trong lifecycle **của bean đó**. Nó không bảo đảm mọi bean không liên quan đã khởi tạo. Nếu callback cần nhìn thấy tất cả regular non-lazy singleton beans, xét `SmartInitializingSingleton` hoặc hook phù hợp thay vì dùng `@PostConstruct` ngẫu nhiên.

## 14. Circular dependencies và không tồn tại topological order

Nếu đồ thị có chu trình:

```text
A -> B -> C -> A
```

không có thứ tự nào thỏa cả “B trước A”, “C trước B” và “A trước C”. Đây là lý do constructor injection cycles thường dẫn đến lỗi creation như `BeanCurrentlyInCreationException` trong exception chain.

### 14.1. Ví dụ không giải được với constructors

```java
final class PaymentService {
    PaymentService(OrderService orders) {
    }
}

final class OrderService {
    OrderService(PaymentService payments) {
    }
}
```

Hai constructors yêu cầu bên kia đã tồn tại để tạo mình, không có điểm bắt đầu.

### 14.2. Đổi vị trí @Bean methods không sửa vòng

```java
@Bean
PaymentService paymentService(OrderService orders) {
    return new PaymentService(orders);
}

@Bean
OrderService orderService(PaymentService payments) {
    return new OrderService(payments);
}
```

Đảo hai methods không thay đổi graph A↔B.

### 14.3. @DependsOn vòng còn làm tình hình xấu hơn

```java
@Bean
@DependsOn("b")
A a() {
    return new A();
}

@Bean
@DependsOn("a")
B b() {
    return new B();
}
```

Nếu A phải được tạo sau B, B lại phải sau A, metadata ordering đã tự mâu thuẫn. Không khắc phục bằng cách thêm `@Order` vì `@Order` không phải startup ordering.

### 14.4. Cách refactor

Trước:

```text
OrderService <----> PaymentService
```

Sau:

```text
           CheckoutCoordinator
              /          \
             v            v
       OrderService    PaymentService
```

Hoặc trích `PaymentPolicy`/`SharedRules` để hai services phụ thuộc abstraction chung thay vì phụ thuộc ngược. Mục tiêu là dependency graph **không quay lại đỉnh đang được tạo**.

### 14.5. @Lazy và ObjectProvider có phá vòng không?

Một `@Lazy` injection point có thể cung cấp lazy-resolution proxy cho một cạnh và cho phép constructor tạo xong trong vài trường hợp. `ObjectProvider<T>` có thể trì hoãn lookup tới method khác. Nhưng quan hệ logic hai chiều vẫn có thể tồn tại và lỗi quay lại khi dereference quá sớm. Chúng **không thay thế** cho dependency inversion.

## 15. Scope ảnh hưởng khi nào các cạnh được thực thi

### Singleton không lazy

Bean singleton non-lazy thường được Spring `ApplicationContext` tạo trong refresh. Khi tạo một singleton eager, dependencies của nó được resolve. Đây là bối cảnh dễ thấy nhất cho startup ordering.

### Singleton lazy

```java
@Bean
@Lazy
ExpensiveRules expensiveRules() {
    return new ExpensiveRules();
}
```

Bean này có thể không xuất hiện trong pre-instantiation. Nhưng nếu eager consumer cần nó bằng constructor parameter, nó vẫn được tạo. Nếu chỉ được gọi qua provider/proxy lần đầu sau refresh, dependency creation xảy ra muộn hơn.

### Prototype

```java
@Bean
@Scope("prototype")
Worker worker() {
    return new Worker();
}
```

Bean prototype không được bulk pre-instantiated như singleton, nhưng có thể được tạo **trong startup** nếu eager singleton constructor cần một Worker để inject. Singleton giữ Worker đã inject; không tự nhận instance mới cho mỗi business invocation.

### Request/session scopes

Một scoped proxy có thể được inject vào singleton khi target request/session bean chưa tồn tại trong scope tương ứng. Dependency edge giữa singleton và **proxy** khác với việc khởi tạo target trong một request sau này. Đừng giả định request-scoped target phải hoàn tất lúc ApplicationContext refresh.

### @DependsOn với lazy và scope

`@DependsOn("x")` có thể làm một lazy singleton `x` được tạo sớm khi dependent eager singleton tạo. Còn cách destroy/shutdown bị chi phối bởi singleton lifecycle; không suy rộng nguyên vẹn sang prototype hoặc mọi custom scope.

## 16. SmartInitializingSingleton và thời điểm “sau các eager beans”

`org.springframework.beans.factory.SmartInitializingSingleton` có callback:

```java
@Override
public void afterSingletonsInstantiated() {
    // Logic ngắn sau pre-instantiation của regular non-lazy singletons.
}
```

Nếu một eager singleton thực thi interface này, callback chạy sau khi BeanFactory hoàn tất pre-instantiation các regular non-lazy singletons. Vì thế nó có thể phù hợp khi cần một bước khởi tạo sau các singletons thông thường, thay vì giả định thứ tự giữa những beans không có dependencies.

### Không phải tất cả beans đều đã tồn tại

- Lazy singleton chưa bị yêu cầu có thể chưa được tạo.
- Prototype bean chỉ tạo theo nhu cầu.
- Một số scoped targets cần request/session active.
- External services có thể chưa ready.
- Callback của lazy bean được tạo sau bootstrap thường không tự động được gọi muộn như một “hậu kiểm mọi singleton mới”.

### SmartInitializingSingleton khác SmartLifecycle

`SmartInitializingSingleton` phù hợp callback kết thúc pre-instantiation. `SmartLifecycle` hỗ trợ start/stop và phase ordering cho thành phần có lifecycle. Nếu application có consumer, listener, scheduler hay process dài hạn, dùng lifecycle abstraction phù hợp thay vì ép các bean constructors chạy theo lịch.

### ContextRefreshedEvent và Boot readiness

`ContextRefreshedEvent` được publish theo Spring Context refresh lifecycle sau những bước thích hợp; Spring Boot `ApplicationReadyEvent` ở một giai đoạn cao hơn của Boot startup. Không đồng nhất cả hai với “mọi dependency ngoài hệ thống đã healthy”.

## 17. @Order trong collection injection: hiểu đúng phạm vi

Giả sử một bean cần tất cả `Handler` implementations:

```java
@Bean
Pipeline pipeline(List<Handler> handlers) {
    return new Pipeline(handlers);
}
```

Spring có thể sắp `List<Handler>` dựa trên:

- `@Order` trên component classes.
- `@Order` trên `@Bean` methods.
- `Ordered` của object phù hợp.
- Các priority/ordering contracts khác tùy injection mechanism.

Nếu có:

```java
@Bean
@Order(50)
Handler standardHandler() {
    return new StandardHandler();
}

@Bean
@Order(5)
Handler urgentHandler() {
    return new UrgentHandler();
}
```

`Pipeline` thường nhận handlers theo thứ tự urgent trước standard. Nhưng câu đó chỉ nói về **vị trí các elements trong List**. Có thể Spring đã tạo standard bean trước, vì một dependency khác đòi nó sớm. Đừng kiểm thử `@Order` bằng constructor log order.

### Nếu cần stage execution ordering

Với `List<Handler>` đã được Spring sắp đúng, **Pipeline tự lặp List** để thực thi các stages. Đó mới là thứ tự thực thi nghiệp vụ có chủ đích. `@Order` không tự gọi methods của beans trong thứ tự nào cả.

## 18. Một bẫy khác: direct call giữa các @Bean methods

```java
@Configuration(proxyBeanMethods = false)
class UnsafeConfig {
    @Bean
    Repository repository() {
        return new Repository();
    }

    @Bean
    Service service() {
        return new Service(repository());
    }
}
```

Với `proxyBeanMethods=false`, lời gọi `repository()` trong `service()` là **Java method call** bình thường, có thể tự tạo thêm Repository object khác ngoài managed bean `repository`. Khi đó bạn đã **bỏ qua** graph reference mà Spring cần biết.

Cách đúng:

```java
@Configuration(proxyBeanMethods = false)
class SafeConfig {
    @Bean
    Repository repository() {
        return new Repository();
    }

    @Bean
    Service service(Repository repository) {
        return new Service(repository);
    }
}
```

Spring sẽ resolve và truyền managed Repository bean, từ đó có thể áp dụng ordering/dependency tracking/lifecycle đúng.

### Full @Configuration thì sao?

Với `@Configuration(proxyBeanMethods=true)` trong managed enhanced config, các inter-bean method calls có thể được CGLIB intercept để lấy Spring-managed bean. Nhưng việc coupling factory methods bằng direct calls vẫn khó refactor hơn method parameter injection. Trong lite mode hoặc gọi trên object `new Config()` tự tạo, CGLIB semantics không áp dụng như bạn mong đợi.

## 19. Background initialization trong Spring Framework 6.2

Spring Framework 6.2 bổ sung tùy chọn bootstrap background cho `@Bean` methods trong những trường hợp hỗ trợ, ví dụ `@Bean(bootstrap = Bean.Bootstrap.BACKGROUND)`.

Cơ chế này cho phép một số beans được khởi tạo nền trong startup thay vì mọi bean creation chạy tuần tự trên refresh thread. Đây là **lý do bổ sung để tránh suy luận thứ tự toàn phần** từ thứ tự lines trong source hoặc logs.

Những giới hạn khái niệm cần nhớ:

- Các dependency injection points không lazy cần instance thật **phải đợi** dependency đủ để sử dụng.
- Với background initialization, một số beans có thể bắt đầu construction song song khi không có dependency edge bắt buộc.
- BeanFactory/context orchestration vẫn cần đảm bảo contract startup tương ứng.
- Đừng assume mọi regular bean constructor chạy trên main thread.
- Các dependencies/proxies cần thread safety và lifecycle đúng.
- Khi background beans được truy cập từ `@PostConstruct` của bean khác hoặc từ custom infrastructure, phải xem semantics của bootstrap mode.

Ví dụ cú pháp minh họa:

```java
@Bean(bootstrap = Bean.Bootstrap.BACKGROUND)
RemoteMetadataClient remoteMetadataClient() {
    return new RemoteMetadataClient();
}
```

Trong Spring Framework, không phải cấu hình background nào cũng tự có executor phù hợp hoặc cho cùng kết quả trên mọi environment; cần đọc tài liệu/thiết lập phiên bản đúng trước khi áp dụng production. Ví dụ runnable chính **không bật background initialization**, nhằm giữ minh họa graph đơn giản.

**Điểm không thay đổi:** Tăng concurrency không xóa dependency edges; consumer không thể được dùng như đã hoàn tất nếu collaborator bắt buộc chưa sẵn sàng.

## 20. Cấu hình hạ tầng và early initialization

Một số beans không tuân một lịch khởi tạo ứng dụng đơn giản vì chúng tham gia **thiết lập chính BeanFactory**:

- `BeanDefinitionRegistryPostProcessor`.
- `BeanFactoryPostProcessor`.
- `BeanPostProcessor`.
- Các bean liên quan resource loading, proxies, property resolution.
- `FactoryBean` với product/factory semantics.

Những components này có thể được xử lý **trước pre-instantiation của regular application singletons**. Vì vậy không nên dùng chúng để “lấy mọi business bean sớm” bằng `getBean()`.

### BeanFactoryPostProcessor không phải nơi khởi tạo business graph

Một post-processor nên thao tác bean metadata theo hợp đồng. Gọi `getBean("billingService")` ở giai đoạn này có thể tạo billing service trước khi các BeanPostProcessors/AOP features đã đăng ký đầy đủ, gây cảnh báo “not eligible for processing” hoặc behavior khó đoán.

### Static @Bean dành cho infrastructure post-processors

Trong những tình huống Spring docs khuyến nghị, khai báo `static @Bean` khi định nghĩa `BeanFactoryPostProcessor` có thể tránh buộc `@Configuration` instance khởi tạo quá sớm. Đây là kỹ thuật lifecycle hạ tầng, không phải cách buộc hai business beans có ordering tùy ý.

### FactoryBean

Với `FactoryBean<T>`, có factory object và product bean. BeanFactory có thể cần inspect factory để xác định product type; một số factory/product có eager initialization semantics riêng. Vì vậy “definition registration order” và “product construction order” càng không nên bị coi là tương đương.

## 21. Lỗi phổ biến và cách chẩn đoán

| Hiện tượng | Vì sao xảy ra? | Hướng xử lý |
| --- | --- | --- |
| Constructor B chạy trước A dù `@Bean a()` viết trước | A phụ thuộc B | Đây là expected DI behavior |
| Thứ tự startup đổi sau refactor | Một vài beans độc lập, không có ordering constraint | Không phụ thuộc thứ tự ngẫu nhiên; thêm dependency nếu thật sự cần |
| `@Order(1)` không làm bean khởi tạo trước | `@Order` không điều khiển singleton startup | Dùng dependency/`@DependsOn` theo đúng ý nghĩa |
| XML service khai báo trước repo nhưng vẫn chạy | `ref` trỏ repo; Spring resolve dependency | Đúng; đừng dùng XML line order để điều khiển |
| `@Import` thứ tự A,B khác startup beans | Import ghép config metadata, không phải startup schedule | Dùng `@Bean` method parameters |
| Bean `@Lazy` vẫn tạo trong refresh | Eager bean yêu cầu nó | Kiểm tra dependency graph |
| Bean prototype xuất hiện lúc startup | Eager singleton inject prototype | Không phải bulk prototype pre-instantiation |
| `@DependsOn` không truyền object vào constructor | Nó chỉ đặt ordering constraint | Dùng constructor param/ref cho instance dependency |
| Vòng `BeanCurrentlyInCreationException` | Constructor/metadata graph có cycle | Refactor dependency direction |
| Constructor của bean chạy trước setter dependency | Raw instance tạo trước property population | Đừng nhầm constructor-start với fully initialized |
| Handler order đúng nhưng constructor logs ngược | Collection sorting khác startup order | Test danh sách/selection, không test constructor order |
| Resource shutdown sớm hơn consumer | Thiếu/mô hình sai lifecycle dependency | Có explicit dependency và destroy callbacks phù hợp |
| `SmartInitializingSingleton` callback chạy khi lazy bean chưa tạo | Hook chỉ sau regular non-lazy pre-instantiation | Không giả định tất cả lazy/prototype beans đã có |
| Startup lâu vì dependency chain nặng | Eager bean kéo nhiều collaborators | Đo constructors/init, xem lazy/provider nếu phù hợp |
| Thời điểm khởi tạo khác trên prod | Profiles/conditions/scopes/post-processors/background bootstrap khác | Test đúng root context và runtime config |
| AOP không áp dụng với bean được tạo quá sớm | Bean bị khởi tạo trước BeanPostProcessor registration | Tránh early getBean trong infrastructure |
| @Bean trả duplicate object | Direct method call trong `proxyBeanMethods=false` | Dùng factory method parameter injection |
| @DependsOn tạo vòng mới | A depends B và B depends A | Bỏ cạnh ordering sai |
| Beans có cùng type gây ambiguity | Nhiều definitions sau import/scan | Qualifier/Primary đúng |
| Context refresh thành công nhưng external service lỗi | Instance initialized không chứng minh remote readiness | Health checks/timeout/retry và readiness policy |

### Quy trình điều tra

1. Xác định **bean name** đang báo lỗi và giai đoạn (definition parsing, creation, injection, initialization hay shutdown).
2. Tìm constructor và `@Bean` factory method signatures.
3. Vẽ tất cả dependency edges: constructor, property, `@Autowired`, refs, `@DependsOn`.
4. Xác định scope/lazy; tìm các eager beans kéo targets lên startup.
5. Tách các beans **có đường phụ thuộc** với **beans độc lập**.
6. Nếu có chu trình, tái cấu trúc graph thay vì đổi thứ tự khai báo.
7. Nếu chỉ cần sorting danh sách, dùng `@Order`/`Ordered` đúng điểm injection.
8. Xem sự khác nhau giữa constructor, init callback, SmartInitializingSingleton và lifecycle startup.
9. Với framework 6.2 background init/AOP/FactoryBean, xem các cơ chế exception và thread semantics.
10. Viết integration test dựa trên **ràng buộc phải giữ**; không assert toàn bộ log order.

## 22. Kiểm thử thứ tự bằng assertions đúng hợp đồng

Trong ví dụ mục 10, thay vì kiểm tra `Trace.events` chính xác là `[repository, audit, service, controller]`, ta kiểm tra các bất đẳng thức:

```java
if (!trace.before("repository.ready", "service.ready")) {
    throw new AssertionError("Repository must precede service");
}
if (!trace.before("audit.ready", "service.ready")) {
    throw new AssertionError("Audit must precede service");
}
if (!trace.before("service.ready", "controller.ready")) {
    throw new AssertionError("Service must precede controller");
}
```

Đây là các dòng **bổ sung bên trong `main` sau khi context được tạo**, dùng `Trace` từ project ví dụ. Chúng kiểm tra edges bắt buộc nhưng không áp đặt thứ tự giữa repository và audit.

### Nếu test muốn chắc chắn có bean definition

Có thể kiểm tra:

```java
boolean exists =
        context.getBeanFactory().containsBeanDefinition("orderService");
```

Đây là kiểm tra metadata. Nó không chứng minh orderService đã được tạo nếu bean lazy hoặc registration theo cơ chế khác.

### Kiểm tra singleton đã được tạo mà không gây creation

```java
boolean created =
        context.getBeanFactory().containsSingleton("optionalReport");
```

Với lazy `optionalReport` chưa bị yêu cầu, kết quả thường false. **Không gọi `getBean("optionalReport")` trước khi kiểm tra** nếu mục tiêu đo trạng thái trước first use.

### Kiểm tra lifecycle destruction

Nếu service và repository giữ resources, thêm destroy callbacks ghi marker rồi đóng ApplicationContext; assert rằng consumer được destroy trước dependency trong trường hợp dependency đã được đăng ký phù hợp. Không dùng direct `new` ngoài container trong test lifetime semantics.

### Vì sao không kiểm tra đúng toàn bộ thứ tự?

BeanFactory có thể thay đổi registration order, thêm infrastructure beans, thay đổi background initialization, refactor config hoặc beans độc lập được tạo theo thứ tự khác. Một test ép toàn bộ chuỗi constructor logs thường **flaky** và không kiểm chứng yêu cầu nghiệp vụ thực.

## 23. Bài tập có đáp án

### Bài 1 — Vẽ đồ thị

A nhận B và C trong constructor; B nhận D; C độc lập với D. Vẽ dependency graph và nêu constraints.

**Đáp án:**

```text
      A
     / \
    v   v
    B   C
    |
    v
    D
```

D cần được tạo/giải quyết trước B, B và C trước khi A constructor được gọi với references thích hợp. Không có ràng buộc trực tiếp về thứ tự C và D.

### Bài 2 — Hai thứ tự hợp lệ

Với graph ở bài 1, liệt kê hai linearizations hợp lệ.

**Đáp án:** `D,B,C,A` và `C,D,B,A` đều thỏa constraints. Đây là những thứ tự toán học có thể xảy ra, không phải cam kết Spring sẽ chọn chúng.

### Bài 3 — Đảo thứ tự @Bean

Factory method `@Bean Controller controller(Service s)` nằm trước `@Bean Service service(Repository r)` trong Java. Có làm Spring tạo Controller trước Service không?

**Đáp án:** Không, Spring phải resolve tham số `Service` trước khi gọi method factory tạo controller.

### Bài 4 — XML ref

`<bean id="service">` nằm trước `<bean id="repo">` trong XML, nhưng service constructor-arg `ref="repo"`. Có cần đổi thứ tự file để chạy?

**Đáp án:** Không. BeanFactory có thể resolve repo từ definitions đã đăng ký, independent of thẻ khai báo đứng trước/sau trong file.

### Bài 5 — @Order

Hai bean `Handler` có `@Order(1)` và `@Order(20)`. Có phải bean order 1 chắc chắn construct trước?

**Đáp án:** Không. `@Order` ảnh hưởng sorting trong những điểm hỗ trợ như `List<Handler>` injection, không bảo đảm singleton startup order.

### Bài 6 — @DependsOn

Bean `ReportService` không nhận `SchemaValidator` reference nhưng phải khởi tạo sau khi validator xong. Viết annotation.

**Đáp án:**

```java
@Bean
@DependsOn("schemaValidator")
ReportService reportService() {
    return new ReportService();
}
```

Tên `schemaValidator` phải khớp bean name thực sự.

### Bài 7 — Destruction

Nếu A singleton `depends-on="b"` và cả hai có destroy callbacks, ai thường được hủy trước?

**Đáp án:** A (dependent) được destroy trước B (dependency), để B còn sẵn trong quá trình A cleanup.

### Bài 8 — Lazy dependency

A eager singleton constructor cần B lazy singleton. Khi Spring refresh, B có thể được tạo không?

**Đáp án:** Có, thường là cần; A không thể hoàn thành constructor nếu thiếu B instance.

### Bài 9 — @DependsOn và lazy

A eager có `@DependsOn("b")`, B lazy. B có thể bị khởi tạo trước A không?

**Đáp án:** Có. Explicit ordering là một nguyên nhân dependency-driven creation, dù B lazy.

### Bài 10 — Setter injection

`new Consumer()` constructor chạy trước khi BeanFactory gọi `setRepository(...)`. Có vi phạm câu “dependency trước consumer” không?

**Đáp án:** Không. Với setter injection, raw object consumer có thể đã được construct; Spring resolve dependency để **populate property trước khi bean hoàn tất normal initialization**. Cần nói rõ mốc nào được so.

### Bài 11 — Multiple @Import

`@Import({ServiceConfig.class, RepoConfig.class})`, service factory nhận `Repository`. Có cần đảo thành RepoConfig trước không?

**Đáp án:** Không vì DI dependency graph mới quyết định Repo phải được resolve trước Service khi service creation cần repo.

### Bài 12 — Prototype

A eager singleton constructor nhận B prototype. B có thể được tạo trong startup không? A nhận B mới mỗi method call?

**Đáp án:** B được tạo để inject vào A khi A xây. A thường giữ instance đã nhận, không tự đổi mỗi call; dùng provider nếu cần prototype per-operation.

### Bài 13 — Vòng

A constructor cần B, B constructor cần C, C constructor cần A. Đảo thứ tự khai báo có giúp không?

**Đáp án:** Không. Directed cycle không có topological order hợp lệ; cần chỉnh kiến trúc/refactor dependency direction.

### Bài 14 — SmartInitializingSingleton

Callback `afterSingletonsInstantiated` có nghĩa mọi lazy bean đã tạo không?

**Đáp án:** Không. Nó gắn với cuối regular non-lazy singleton pre-instantiation; lazy bean không bị yêu cầu vẫn có thể chưa được tạo.

### Bài 15 — Method call runtime

Spring tạo A trước B theo một dependency edge. Có nghĩa khi request đến B luôn chạy business method sau A không?

**Đáp án:** Không. Runtime method execution do caller, threads, events và orchestration quyết định, không phải bean initialization ordering.

### Bài 16 — Dự đoán output ví dụ

Tại sao `repository.before.service` và `audit.before.service` đều true nhưng không assert `repository.before.audit`?

**Đáp án:** Repo là constructor dependency của service, audit là `@DependsOn` dependency của service. Giữa repo và audit không có edge buộc thứ tự tương đối.

### Bài 17 — Thay @Order

Nếu đổi `@Order(10)`/`@Order(20)` cho các `AlertChannel`, chuyện gì thay đổi rõ nhất?

**Đáp án:** Thứ tự các elements trong `List<AlertChannel>` mà `AlertCatalog` nhận. Không suy ra constructor startup order đổi.

### Bài 18 — One-direction design

Ứng dụng có `RepositoryService` và `BillingService` cần nhau ở constructors. Đề xuất refactor.

**Đáp án định hướng:** Xác định ai thực sự sở hữu business orchestration; đưa việc phối hợp vào bean thứ ba hoặc trích abstraction/policy chung. Giữ tầng repository không phụ thuộc ngược vào application service, làm dependency graph đi một chiều.

### Bài 19 — Test một phần thay vì toàn bộ order

Giả sử log startup tình cờ in `audit, repo, service, controller`. Khi refactor framework nó in `repo, audit, service, controller`. Có nên coi là regression?

**Đáp án:** Không nếu contract chỉ là audit trước service, repo trước service và service trước controller. Hai chuỗi đều hợp lệ.

### Bài 20 — @Bean bootstrap BACKGROUND

Nếu B được tạo trên background thread trong Spring Framework 6.2 và A constructor cần B thật, có được đưa A vào trạng thái initialized hoàn chỉnh trước khi B sẵn sàng không?

**Đáp án:** Không đối với non-lazy dependency instance bắt buộc theo hợp đồng; container cần chờ/resolve B phù hợp. Nhưng các independent beans có thể tiến hành đồng thời, nên càng không được assert một global constructor order.

## 24. Checklist review cấu hình Spring

- [ ] Đã vẽ các dependency edges thật từ constructors, setters, fields và factory method params.
- [ ] Có phân biệt bean definition registration order với instance creation order.
- [ ] Hiểu Spring bảo đảm constraints, không nhất thiết một global total order.
- [ ] Không đặt nghiệp vụ phụ thuộc vào thứ tự hai singleton độc lập được khởi tạo.
- [ ] `@Bean` methods dùng parameters để nhận collaborators thay vì direct-call trong lite mode.
- [ ] `@Import`/XML imports chỉ ghép definitions, không thay dependency graph.
- [ ] `@DependsOn` có reason rõ và bean names chính xác.
- [ ] Không thêm `@DependsOn` chỉ để lặp constructor dependencies.
- [ ] Có hiểu `@DependsOn` còn ảnh hưởng singleton destruction order.
- [ ] Không dùng `@Order` như điều khiển startup; chỉ sorting/selection đúng contract.
- [ ] Dependency graph không có constructor cycle.
- [ ] Eager/lazy settings phù hợp dependency chain và startup goals.
- [ ] Một lazy bean được eager consumer yêu cầu có thể vẫn bị tạo sớm.
- [ ] Setter/field injection được hiểu là population lifecycle, không nhầm constructor order.
- [ ] Scoped/lazy proxies và ObjectProvider được xem là deferred target resolution nếu thật sự dùng.
- [ ] Startup hooks/`SmartInitializingSingleton` không bị nhầm với runtime readiness.
- [ ] `SmartLifecycle` được dùng khi cần start/stop theo phase.
- [ ] BeanFactory post-processors không tùy tiện gọi business beans sớm.
- [ ] Nếu bật background bootstrap 6.2, đã xét concurrency và dependency waiting.
- [ ] Test assert từng edge bắt buộc, không assert order giữa node độc lập.
- [ ] Test startup, first-use lazy, destroy callbacks và cấu hình theo profile nếu quan trọng.
- [ ] Không dùng `getBean` để kiểm tra một lazy bean đã tạo chưa nếu không muốn gây creation.
- [ ] Không đưa business side effects nặng vào constructors chỉ để ép ordering.
- [ ] Startup health/readiness được thiết kế riêng với BeanFactory creation.

## Kết luận

**Thứ tự khởi tạo bean trong Spring xuất phát từ đồ thị phụ thuộc**, theo nghĩa container resolve những collaborators mà một bean cần để hoàn thành việc tạo và initialization. `@DependsOn` có thể thêm ordering edges cho những phụ thuộc không thể hiện qua constructor/property reference. Kết quả là một **thứ tự từng phần**: các dependency constraints được đảm bảo, còn thứ tự giữa các bean độc lập **không nên được coi là hợp đồng**.

Đặc biệt:

- `@Bean` declaration order, `@Import` order và XML source order **không thay thế dependency edges**.
- `@Order` điều khiển những ordering semantics như injected collections, **không** điều khiển singleton startup order.
- `@Lazy` và scope tác động *khi nào* target được cần; eager dependency vẫn có thể tạo lazy bean.
- Một cycle constructor A→B→A không thể sửa bằng việc đổi thứ tự khai báo.
- Cleanup singleton thường theo hướng ngược với dependency cần giữ; explicit `depends-on` có thể ảnh hưởng destruction order.

Hãy dùng constructor/factory method parameter injection để mô tả dependency thực, `@DependsOn` khi thật sự cần implicit initialization ordering, và lifecycle coordinator khi yêu cầu là **startup/shutdown workflow** chứ không chỉ tạo object. Khi kiểm thử, kiểm chứng các **ràng buộc bắt buộc** thay vì một chuỗi constructor logs tình cờ.

## Tài liệu chính thức

- [Spring Framework — Using depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html): initialization order, XML `depends-on`, destruction order của singleton.
- [Spring Framework — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): pre-instantiation, lazy dependency bị eager singleton yêu cầu.
- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor/property dependency resolution.
- [Spring Framework — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): `@Order`/`Ordered` tại injection collections khác với singleton startup order.
- [Spring Framework — Composing Java-based Configurations](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html): `@Import`, `@Bean` wiring, `@DependsOn` và Spring 6.2 background initialization.
- [Spring Framework 6.2 — @Order API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/annotation/Order.html): ordering priority vs singleton startup.
- [Spring Framework 6.2 — @DependsOn API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DependsOn.html): bean names và initialization/destruction order.
- [Spring Framework 6.2 — SmartInitializingSingleton API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/SmartInitializingSingleton.html): callback sau regular singleton pre-instantiation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-007">Phân biệt @Order, @DependsOn và thứ tự định nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `@Order`, `@DependsOn` và thứ tự định nghĩa bean trong Spring Framework

## 1. Ba loại “thứ tự” khác nhau

Trong Spring IoC Container, rất nhiều câu hỏi được diễn đạt bằng cụm từ “bean nào chạy trước?”; tuy nhiên, ít nhất **ba vấn đề độc lập** thường bị gộp chung:

1. **Thứ tự sử dụng các beans trong một tập:** khi inject `List<Handler>`, handler nào đứng trước để pipeline gọi trước?
2. **Thứ tự khởi tạo và hủy beans:** khi một singleton có side effect tạo infrastructure, bean nào phải được tạo xong trước bean khác?
3. **Thứ tự đăng ký/khai báo bean definitions:** developer viết `@Bean` method hoặc XML `<bean>` nào trước trong source file, và BeanFactory ghi nhận định nghĩa theo thứ tự nào?

Ba vấn đề này có cơ chế và mức bảo đảm khác nhau:

| Câu hỏi | Cơ chế thích hợp | Điều không nên suy diễn |
| --- | --- | --- |
| Một `List<Stage>` được tiêm có thứ tự nào? | `@Order`, `Ordered`, `@Priority` trong ngữ cảnh được hỗ trợ | Không suy ra target beans được khởi tạo theo thứ tự list |
| Bean B phải **được khởi tạo sau** bean A? | Dependency injection thực sự hoặc `@DependsOn("A")` | `@Order(1)` trên A không bảo đảm A tạo trước B |
| B phải đóng trước A khi shutdown? | Quan hệ dependency/`@DependsOn` với singleton, lifecycle phù hợp | `@Order` dùng để sort list không định nghĩa destruction order |
| Không có `@Order` thì list có thứ tự gì? | Thường theo thứ tự đăng ký target bean definitions | Không phải bảo đảm lâu dài khi scan/import/refactor đổi |
| Viết `@Bean first()` trên `second()` thì sao? | Chỉ là thứ tự trong source code | Không phải hợp đồng init-before |
| Cần start/stop những services theo phase? | `SmartLifecycle.getPhase()` và dependency graph | Không dùng `@Order` để thay `SmartLifecycle` |

Trong bài này, `@Order` thuộc package `org.springframework.core.annotation`, còn `@DependsOn` thuộc `org.springframework.context.annotation`. Hai annotations không phải các cách viết khác nhau của một tính năng.

**Điểm then chốt từ Spring Framework 6.2:** `@Order` được dùng cho thứ tự ở nhiều điểm tiêu thụ (đặc biệt collection injection), **không điều khiển thứ tự startup singleton**. `@DependsOn` tạo quan hệ bảo đảm giữa các bean definitions: dependency được khởi tạo trước dependent và, với singleton, dependent bị hủy trước dependency.

Mục tiêu học tập:

- Phân biệt các pha **đăng ký bean definition**, **khởi tạo bean instance**, **sort injection collection**, **init/start** và **destroy/stop**.
- Dùng đúng `@Order` trên component classes/`@Bean` methods; hiểu số nhỏ có ưu tiên cao.
- Dùng `@DependsOn` ở đúng bean cần dependency, bằng **bean name**, không phải class name.
- Biết thứ tự fallback theo registration đối với collection không có order metadata và giới hạn của cơ chế.
- Phân biệt `@Order` trên `@Configuration` class với `@Order` trên **từng `@Bean` method**.
- Hiểu `@Primary`/`@Qualifier` và `@Priority` không hoàn toàn đồng nghĩa `@Order`.
- Viết và chạy demo Java 17+ với Spring Context 6.2, có assertions và stdout.
- Kiểm thử lỗi cấu hình, shutdown order, event pipeline và các tác động của lazy/prototype.

## 2. Mô hình thời gian của Spring Container

Một sơ đồ được đơn giản hóa:

```text
ApplicationContext được cấu hình
             |
             v
Đọc @Configuration / @Component / XML / imports
             |
             v
Đăng ký BeanDefinition và metadata liên quan
  name, scope, lazy, dependsOn, qualifiers, order...
             |
             v
Xử lý BeanFactoryPostProcessor / bean metadata
             |
             v
Pre-instantiate eligible non-lazy singleton beans
   với dependency graph được tôn trọng
             |
             v
Cấp dependency cho consumers
  + sort List<T>/T[] tại injection point thích hợp
             |
             v
Gọi business methods, SmartLifecycle.start(), ...
             |
             v
Đóng context
   lifecycle stop, singleton destruction ...
```

Sơ đồ không phải toàn bộ vòng đời Spring: bean post-processors, AOP proxies, factory methods, custom scopes và Spring 6.2 background initialization có thể làm chi tiết phức tạp hơn. Nhưng một phân biệt rất quan trọng là:

- **Registration**: container biết *có* bean definition; instance có thể chưa được tạo.
- **Instantiation/initialization**: container tạo object và hoàn tất initialization.
- **Collection resolution**: container chọn và sắp các candidate objects cho injection point.
- **Invocation**: application gọi methods theo thứ tự mà application quyết định.
- **Destruction**: container giải phóng managed singletons khi context đóng theo dependency/lifecycle rules.

Bạn có thể có một `List<Stage>` được sắp `A, B, C` nhưng thứ tự constructor hoặc static init events hoàn toàn không phải `A, B, C`. Không được đồng nhất “sorted list” với “startup order”.

## 3. `@Order`: dùng khi một tập phần tử cần thứ tự

Annotation:

```java
import org.springframework.core.annotation.Order;

@Order(-100)
class SecurityFilter implements FilterStep {
    // ...
}
```

Đây là **trích đoạn minh họa**; cần interface `FilterStep` và method bodies phù hợp để biên dịch. Ý nghĩa `@Order(-100)` là khai báo một **giá trị thứ tự** dùng khi Spring hoặc infrastructure liên quan sắp xếp các elements.

### 3.1. Số nhỏ đứng trước

Thứ tự số học:

```text
@Order(-10)  → trước
@Order(0)    → giữa
@Order(20)   → sau
@Order(100)  → sau nữa
```

Theo contract `Ordered`, **giá trị nhỏ hơn có độ ưu tiên cao hơn** trong sorting. Spring định nghĩa hai hằng:

```java
Ordered.HIGHEST_PRECEDENCE   // Integer.MIN_VALUE
Ordered.LOWEST_PRECEDENCE    // Integer.MAX_VALUE
```

Đây là ví dụ tham chiếu constants, cần import `org.springframework.core.Ordered` khi viết Java độc lập.

`@Order` có default value `Ordered.LOWEST_PRECEDENCE`. Nếu nhiều phần tử không có chỉ dẫn order hoặc có cùng order value, đừng thiết kế nghiệp vụ dựa vào một tie-breaking thứ tự không được contract bảo đảm; có thể cần thêm quy tắc rõ ở application layer.

### 3.2. Ví dụ inject `List<Step>`

Giả sử có:

```java
public interface Step {
    String code();
}
```

Và ba implementations/component beans:

```java
@Component
@Order(30)
class StepValidate implements Step {
    public String code() { return "validate"; }
}

@Component
@Order(-10)
class StepAuthenticate implements Step {
    public String code() { return "authenticate"; }
}

@Component
@Order(10)
class StepEnrich implements Step {
    public String code() { return "enrich"; }
}
```

Hai đoạn là snippets minh họa; cần imports và component scanning để beans tồn tại. Với consumer:

```java
@Component
class Pipeline {
    private final List<Step> steps;

    Pipeline(List<Step> steps) {
        this.steps = steps;
    }
}
```

Spring có thể inject `steps` theo thứ tự `authenticate, enrich, validate` vì giá trị `-10 < 10 < 30`.

**Đây là thứ tự phần tử trong collection tại injection point.** Container không bị yêu cầu khởi tạo `StepAuthenticate` trước `StepEnrich` trước `StepValidate` chỉ vì chúng có `@Order`.

### 3.3. Dùng `@Order` trên `@Bean` method

Một số types không mang `@Component` hoặc bạn muốn có nhiều bean definitions trả về cùng implementation nhưng mỗi bean có priority khác:

```java
@Bean("secondStep")
@Order(20)
Step secondStep() {
    return new NamedStep("second");
}

@Bean("firstStep")
@Order(0)
Step firstStep() {
    return new NamedStep("first");
}
```

Snippet này cần `Step`, `NamedStep` và `@Bean` import. Khi inject `List<Step>`, Spring 6.2 đọc `@Order` trên **target `@Bean` methods** và có thể sắp `first` trước `second`.

Điều này quan trọng nếu hai bean definitions dùng cùng class: `@Order` trên class không đủ để diễn đạt mức ưu tiên khác nhau cho từng bean definition, trong khi method annotations có thể làm được.

### 3.4. `Ordered` interface khi order tính theo instance

Thay vì hard-code annotation metadata, một object có thể:

```java
public final class ConfigurableStep
        implements Step, Ordered {

    private final String code;
    private final int order;

    public ConfigurableStep(String code, int order) {
        this.code = code;
        this.order = order;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
```

Cần imports của `Ordered` và `Step`. Khi infrastructure dùng `AnnotationAwareOrderComparator`/order-aware sorting, giá trị từ `Ordered.getOrder()` có thể được dùng để sắp elements. Nó hữu ích khi cấu hình thứ tự ở bean instance level thay vì một annotation cố định trên class.

**Giới hạn:** implement `Ordered` không khiến Spring tự gọi business method `execute()` theo thứ tự. Một consumer/pipeline phải thực sự lặp qua tập đã được order, hoặc hạ tầng cụ thể phải áp dụng comparator ấy.

### 3.5. `@Order` trên `@Configuration` không lan xuống `@Bean` methods

Đây là một khác biệt được tài liệu Spring 6.2 nói rõ:

```java
@Configuration
@Order(-100)
class AlphaConfiguration {

    @Bean
    Step alphaStep() {
        return new NamedStep("alpha");
    }
}

@Configuration
@Order(100)
class BetaConfiguration {

    @Bean
    Step betaStep() {
        return new NamedStep("beta");
    }
}
```

Mã minh họa trên thiếu imports/types. `@Order` trên **configuration classes** có thể ảnh hưởng **evaluation order trong tập các configuration classes**, không tự gán `-100` và `100` cho các `Step` beans được factory methods tạo.

Muốn list `List<Step>` được sort theo các giá trị cụ thể, đặt `@Order` trên **mỗi `@Bean` method** (hoặc trên target classes/`Ordered` phù hợp).

**Không suy ra**: config Alpha được đánh giá trước → bean `alphaStep` nhất định được tạo trước → list được order như vậy. Đây là ba tầng khác nhau.

## 4. Collection injection khi không có `@Order`

Spring Reference nói rằng với typed array/list injection, nếu candidates không có `Ordered`, `@Order` hoặc `@Priority` để điều khiển, thứ tự thường theo **registration order của target bean definitions** trong container.

Ví dụ:

```java
@Bean("eastStep")
Step eastStep() { return new NamedStep("east"); }

@Bean("westStep")
Step westStep() { return new NamedStep("west"); }
```

Không có order metadata, Spring dùng registration order làm fallback cho List. Nhưng **đừng xem thứ tự dòng code là hợp đồng luôn bất biến**. Registration order có thể thay đổi khi:
- thêm `@Import` hoặc thay composition root;
- đổi component scan packages/filtering;
- thêm `BeanDefinitionRegistryPostProcessor`/registrar;
- dịch từ XML sang JavaConfig hoặc đổi build packaging;
- thay dependency/framework version;
- thay cách programmatic registration được gọi.

**Nhận diện hai loại order khác nhau**:

| Tên | Nghĩa | Dùng khi nào? |
| --- | --- | --- |
| Source declaration order | Method/class/`<bean>` xuất hiện vị trí nào trong source | Con người đọc và maintain |
| Bean definition registration order | Container ghi nhận target bean definitions theo thứ tự nào | Fallback cho unordered List/array candidates |
| Bean instance creation order | Constructor/factory/init chạy thực tế vào thời điểm nào | Dependency graph, eager/lazy, background/bootstrap |
| Consumer invocation order | Pipeline gọi các Step theo sequence nào | Consumer logic và sorted collection |

Không có phép suy ra chung “source declaration = registration = instantiation = invocation”.

### 4.1. XML `<bean>` từ trên xuống dưới có đảm bảo init thứ tự không?

Không nên phụ thuộc vào **vị trí thẻ** để đòi một side effect phải xảy ra trước. Trong một số cấu hình thông thường, non-lazy singleton pre-instantiation có thể quan sát thấy gần với registration order; tuy nhiên, dependency graph, lazy beans, FactoryBeans, imports, BeanPostProcessors và những cơ chế khác có thể làm thứ tự khác đi.

Nếu Bean B bắt buộc chờ Bean A hoàn tất initialization, khai báo quan hệ đúng bằng `<ref>`/constructor dependency hoặc `depends-on`, thay vì di chuyển các thẻ XML.

### 4.2. Java reflection/method declaration order không phải scheduling API

Trong JavaConfig, Spring phân tích annotations và factory methods để đăng ký bean definitions. Đừng thiết kế logic chỉ dựa trên dòng nào viết trước trong `@Configuration` class. Java source method order không phải API contract để ràng buộc thứ tự khởi tạo managed beans.

### 4.3. XML import/Java import có thể thay candidate set

Khi một configuration import thêm beans, tổng số candidates và registration order có thể thay đổi. Nếu list là một **pipeline quan trọng** (security, validation, routing), dùng `@Order` hoặc order contract rõ ràng. Nếu stage order không ảnh hưởng nghiệp vụ, đừng đặt order values chỉ vì cảm giác cần một thứ tự.

## 5. `@DependsOn`: khởi tạo prerequisite trước, hủy dependent trước

`@DependsOn("bootstrap")` đặt trên bean `worker` diễn đạt một quan hệ **lifecycle**, độc lập với thứ tự handler trong collection:

```java
@Bean("worker")
@DependsOn("bootstrap")
Worker worker() {
    return new Worker();
}
```

Tên `bootstrap` phải là bean name hoặc alias hợp lệ. Khi worker được khởi tạo, Spring phải tạo và initialize prerequisite trước; với hai managed singleton có destroy callback, worker được hủy trước bootstrap.

```text
INIT:    bootstrap -> worker
DESTROY: worker    -> bootstrap
```

XML sử dụng `<bean id="worker" depends-on="bootstrap" ... />`. `@DependsOn` **không inject object**: nếu `Worker` cần dùng chính `Bootstrap` instance, constructor hoặc factory-method parameter là cách khai báo phù hợp. Class-level `@DependsOn` được xử lý theo đường component scanning; nếu class được đăng ký trực tiếp bằng XML, dùng XML `depends-on`.

`@DependsOn({"bootstrap","metrics"})` bắt cả hai prerequisites có trước worker, nhưng không nên xem thứ tự liệt kê trong array là một quy trình nghiệp vụ bắt bootstrap phải chạy trước metrics. Cũng đừng dùng nó để trì hoãn bean: eager dependent có thể kéo lazy prerequisite vào startup.

Bài trước về `@DependsOn` dạy chi tiết declaration và diagnostics. Ở bài so sánh này, điểm cần kiểm chứng là **`@DependsOn` tạo init/destroy relation, trong khi `@Order` chỉ sort tập bean**. Hai yêu cầu có thể cùng tồn tại trên một component và cần kiểm thử tách biệt.

## 6. Vì sao `@Order` không thay thế `@DependsOn`?

Giả sử có một pipeline với hai `Handler`:

```java
@Component
@Order(-100)
class BootstrapHandler implements Handler {
    // ...
}

@Component
@Order(100)
class AuditHandler implements Handler {
    // ...
}
```

Collection `List<Handler>` có thể xếp BootstrapHandler trước AuditHandler. Nhưng Spring **không bị yêu cầu** gọi constructor/init của BootstrapHandler trước AuditHandler chỉ vì `@Order(-100)`.

Trong một ứng dụng dùng singleton beans:
- khi context pre-instantiates beans, thứ tự còn chịu ảnh hưởng graph và bootstrap operations;
- `@Order` có thể chỉ được đọc lúc BeanFactory resolve candidates cho một List;
- nếu AuditHandler có `@PostConstruct` side effect cần hạ tầng BootstrapHandler đã sẵn sàng, `@Order` không giải quyết requirement đó.

Nếu cần init-before, dùng:

```java
@Component
@DependsOn("bootstrapHandler")
class AuditHandler implements Handler {
    // ...
}
```

Giải pháp này giả định bean name `bootstrapHandler` được đăng ký đúng theo naming convention hoặc chỉ định explicit. Vẫn có thể giữ `@Order` **đồng thời** để điều khiển vị trí trong List khi nghiệp vụ cần.

### 6.1. Cùng lúc cần cả hai cơ chế

Một hệ thống có thể:
- phải khởi tạo authentication infrastructure trước handler audit;
- phải thực thi authentication handler trước audit handler trong request pipeline.

Khi đó cần **hai hợp đồng riêng**:
- `@DependsOn` hoặc DI edge cho initialization graph;
- `@Order` cho collection processing order.

Chỉ một annotation không thể thay thế annotation kia.

### 6.2. Thứ tự List không đồng nghĩa một handler được gọi trước

Nếu consumer không sử dụng `List<Handler>` mà lookup bean bằng name rồi gọi trực tiếp, `@Order` không ép caller gọi handlers theo order.

Ngược lại, một consumer đã nhận ordered list nhưng cố ý gọi `list.get(2)` trước cũng không bị Spring ngăn. Framework sắp **collection**, còn application quyết định **invocation**.

## 7. `@Order`, `@Priority`, `Ordered`, `@Primary` và `@Qualifier`

Những tên này dễ gây nhầm, nhưng giải quyết các vấn đề khác nhau:

| Cơ chế | Mục đích chính | Ví dụ |
| --- | --- | --- |
| `@Order(5)` | Khai báo sort order khi xử lý tập phần tử | Pipeline List |
| `Ordered#getOrder()` | Trả order value từ bean instance | Plugin order từ config |
| `@Priority(5)` (Jakarta) | Priority trong ordering; có semantics bổ sung ở một số single-bean selection contexts | Framework-level prioritization |
| `@Primary` | Chọn default khi có nhiều bean cùng type cho **single injection point** | Một `DataSource` mặc định |
| `@Qualifier("east")` | Thu hẹp candidate set cho dependency | Chọn bean cụ thể |
| `@DependsOn("bootstrap")` | Init-before/destruction-after relation | Resource initialization |
| Registration order | Fallback sorting cho List/array không có order metadata | Hậu quả cách đăng ký definitions |

### 7.1. `@Order` không mặc định chọn một bean duy nhất

Hai beans đều implement `PaymentGateway`:

```java
@Bean
@Order(-100)
PaymentGateway fastGateway() { return new FastGateway(); }

@Bean
@Order(100)
PaymentGateway safeGateway() { return new SafeGateway(); }
```

Nếu consumer yêu cầu **một** `PaymentGateway` không qualifier:

```java
@Bean
CheckoutService checkoutService(PaymentGateway gateway) {
    return new CheckoutService(gateway);
}
```

Đừng suy ra Spring sẽ tự chọn `fastGateway` chỉ vì `@Order(-100)`. `@Order` chủ yếu xử lý **order trong tập candidates**. Với một dependency đơn, có thể cần `@Primary`, `@Qualifier` hoặc naming policy thích hợp. `@Priority` có thể có semantics bổ sung trong trường hợp chọn một phần tử, vì vậy cũng không nên đánh đồng nó với `@Order`.

### 7.2. `@Primary` không sort List

Nếu một trong ba handlers có `@Primary`, điều đó không tự đưa handler này lên đầu `List<Handler>`. Injection point là collection thì Spring thường thu thập nhiều candidates và áp dụng order metadata riêng.

### 7.3. Qualifier lọc candidates trước/đồng thời với ordering

Một List injection point có `@Qualifier("critical")` có thể chỉ nhận nhóm handlers gắn qualifier phù hợp, rồi sắp order trong **tập đã lọc**.

Cần kiểm tra annotations và bean definitions cụ thể: `@Qualifier` không phải thứ tự, `@Order` không phải bộ lọc.

### 7.4. `Set` và `Map`: đọc contract trước khi hứa thứ tự

Spring reference mô tả order sorting rõ cho **array** và **typed List** injection. `Map<String, Handler>` cung cấp bean names làm keys và beans làm values. Không nên viết business pipeline dựa trên giả định mọi traversal của Set/Map sẽ tuân `@Order` như List.

Nếu thứ tự chính là nghiệp vụ, inject `List<Handler>`, hoặc rõ ràng tạo một ordered sequence từ map values bằng comparator trong code, và viết tests cho contract ấy.

## 8. Ví dụ XML đối chiếu

XML beans:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="worker"
          class="example.Worker"
          depends-on="bootstrap"/>

    <bean id="bootstrap"
          class="example.Bootstrap"/>
</beans>
```

Đoạn này là **cấu hình minh họa**: `example.Worker`/`example.Bootstrap` cần tồn tại. Worker được viết trước Bootstrap trong XML nhưng dependency metadata buộc Bootstrap được khởi tạo trước Worker. Chuyển gần tương đương:

```java
@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {

    @Bean("worker")
    @DependsOn("bootstrap")
    Worker worker() {
        return new Worker();
    }

    @Bean("bootstrap")
    Bootstrap bootstrap() {
        return new Bootstrap();
    }
}
```

Đây là snippet Java minh họa, cần imports/types. `@DependsOn` áp dụng trên factory method `worker()`; cấu hình không cần gọi `bootstrap()` trực tiếp từ `worker()` để tạo ordering dependency.

### 8.1. XML declaration order không tạo dependency edge

Nếu bỏ `depends-on="bootstrap"` và Worker không inject Bootstrap, chỉ còn hai bean definitions độc lập. Việc Worker nằm trước hoặc sau Bootstrap trong file có thể làm thay đổi **quan sát** trong một phiên bản/cách loading cụ thể, nhưng đó không phải một dependency contract tốt để đảm bảo side effect.

### 8.2. Bean có alias thì `depends-on` có thể dùng name đã đăng ký

Nếu `bootstrap` có alias `initSubsystem`, một `depends-on="initSubsystem"` có thể resolve tên alias hợp lệ. Tuy nhiên, dependency naming nên ổn định và được tests bảo vệ; đổi bean names/aliases trong migration có thể phá `@DependsOn`.

### 8.3. Đừng ép bằng `<import>` file order

Chia cấu hình thành `00-bootstrap.xml` và `10-worker.xml` không phải thay thế logic dependency graph. Khi import nhiều XML, file load order có thể tạo ảo giác “bootstrap lúc nào cũng trước”. Dùng `depends-on` hoặc constructor/reference injection cho yêu cầu thật.

## 9. Demo chạy được: Java 17+ và Spring Framework 6.2

Demo này tách hai khái niệm ngay trong một application context:

- **Collection order:** `@Order` đặt trên ba `@Bean` methods: bước `first` đứng trước `middle` và `last` dù method `lastStep` được viết trước.
- **Initialization/destruction order:** bean `worker` có `@DependsOn("bootstrap")`. Hai beans không inject reference của nhau; mục tiêu là chứng minh metadata thứ tự độc lập.
- **Bean definition order inspection:** có thể đọc names đã đăng ký để kiểm tra rằng registration không tự là creation contract.
- **Assertions:** không chỉ in log rồi phỏng đoán, chương trình xác minh sorted List, created-before và destroyed-after.

Không cần Spring Boot, database, HTTP hoặc external service. Demo dùng một Java file có nested types và bản Gradle đơn giản.

### 9.1. Cấu trúc dự án

```text
bean-ordering-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        └── java/
            └── BeanOrderingDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'bean-ordering-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

application {
    mainClass = 'BeanOrderingDemo'
}
```

JDK 17+ và một bản Gradle hỗ trợ JDK tương ứng là đủ cho source sử dụng records, `List.of` và `AnnotationConfigApplicationContext`.

### 9.2. Mã Java đầy đủ

Lưu `src/main/java/BeanOrderingDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;

public class BeanOrderingDemo {

    public interface Stage {
        String label();
    }

    public record NamedStage(String label) implements Stage {
    }

    public record Pipeline(List<Stage> stages) {
        public List<String> labels() {
            return stages.stream()
                    .map(Stage::label)
                    .toList();
        }
    }

    public static final class Events {
        private final List<String> values = new ArrayList<>();

        public void add(String event) {
            values.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(values);
        }
    }

    public static final class Bootstrap implements AutoCloseable {
        private final Events events;

        public Bootstrap(Events events) {
            this.events = events;
            events.add("init:bootstrap");
        }

        @Override
        public void close() {
            events.add("destroy:bootstrap");
        }
    }

    public static final class Worker implements AutoCloseable {
        private final Events events;

        public Worker(Events events) {
            this.events = events;
            events.add("init:worker");
        }

        @Override
        public void close() {
            events.add("destroy:worker");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DemoConfig {

        // Method declared first, but not permitted to initialize
        // before the bean named "bootstrap".
        @Bean(name = "worker", destroyMethod = "close")
        @DependsOn("bootstrap")
        Worker worker(Events events) {
            return new Worker(events);
        }

        @Bean(name = "bootstrap", destroyMethod = "close")
        Bootstrap bootstrap(Events events) {
            return new Bootstrap(events);
        }

        @Bean
        Events events() {
            return new Events();
        }

        // The source order of these factory methods is purposely
        // different from the desired order in List<Stage>.
        @Bean("lastStage")
        @Order(30)
        Stage lastStage() {
            return new NamedStage("last");
        }

        @Bean("firstStage")
        @Order(-10)
        Stage firstStage() {
            return new NamedStage("first");
        }

        @Bean("middleStage")
        @Order(10)
        Stage middleStage() {
            return new NamedStage("middle");
        }

        @Bean
        Pipeline pipeline(List<Stage> stages) {
            return new Pipeline(stages);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Events events;

        try (var context = new AnnotationConfigApplicationContext(
                DemoConfig.class)) {

            events = context.getBean(Events.class);
            Pipeline pipeline = context.getBean(Pipeline.class);

            List<String> order = pipeline.labels();
            List<String> init = events.snapshot();

            String[] dependsOn = context.getBeanFactory()
                    .getBeanDefinition("worker")
                    .getDependsOn();

            require(
                    order.equals(List.of("first", "middle", "last")),
                    "Collection @Order contract failed");

            require(
                    init.equals(List.of(
                            "init:bootstrap", "init:worker")),
                    "@DependsOn initialization contract failed");

            require(
                    Arrays.equals(
                            dependsOn, new String[] {"bootstrap"}),
                    "Missing depends-on bean definition metadata");

            System.out.println("pipeline=" + order);
            System.out.println("initialization=" + init);
            System.out.println("worker.dependsOn=" +
                    Arrays.toString(dependsOn));
        }

        List<String> afterClose = events.snapshot();
        require(
                afterClose.equals(List.of(
                        "init:bootstrap", "init:worker",
                        "destroy:worker", "destroy:bootstrap")),
                "@DependsOn destruction contract failed");

        System.out.println("shutdown=" + afterClose);
    }
}
```

### 9.3. Lệnh chạy

Từ thư mục dự án:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Windows PowerShell:

```powershell
.\gradlew.bat run --quiet
```

Stdout **do chương trình chủ động in**:

```text
pipeline=[first, middle, last]
initialization=[init:bootstrap, init:worker]
worker.dependsOn=[bootstrap]
shutdown=[init:bootstrap, init:worker, destroy:worker, destroy:bootstrap]
```

Gradle hay Spring logging backend có thể xuất thêm dòng bên ngoài `System.out.println`. Các assertions sẽ khiến chương trình fail nếu behavior quan sát được sai với contract đã khai báo.

### 9.4. Phân tích từng dòng

**`pipeline=[first, middle, last]`:** Ba target bean definitions có order metadata `-10`, `10` và `30`. Khi BeanFactory resolve tham số `List<Stage> stages` cho `pipeline`, nó sắp thứ tự candidates. Pipeline chỉ lưu List ấy và in labels theo thứ tự.

**`initialization=[init:bootstrap, init:worker]`:** Bean `worker` mang `@DependsOn("bootstrap")`. Dù `worker()` được khai báo trước `bootstrap()` trong Java source, BeanFactory phải bảo đảm bootstrap đã được khởi tạo trước worker khi worker được tạo. Các stage beans không thêm events vào log, nên snapshot chỉ chứa hai init events và giá trị dự đoán ổn định.

**`worker.dependsOn=[bootstrap]`:** Metadata được lưu trong `BeanDefinition`. Đây là cách xác minh chính xác rằng annotation trên factory method đã tạo ra dependency relation mà bạn muốn.

**`shutdown=[init:bootstrap, init:worker, destroy:worker, destroy:bootstrap]`:** Khi context đóng, singleton dependent `worker` được hủy trước `bootstrap`. Hai classes implement `AutoCloseable`; `@Bean(destroyMethod="close")` được khai báo tường minh để tránh lệ thuộc vào inferred destruction callbacks.

### 9.5. Vì sao việc khai báo method theo thứ tự ngược lại quan trọng?

`worker()` được viết trước `bootstrap()` một cách có chủ đích. Điều này giúp người đọc không lầm source order với business rule. Program không **dựa vào** source order; nó dựa vào `@DependsOn`. Nếu về sau chuyển configuration sang nhiều files hoặc đổi method order, dependency relation vẫn thể hiện cùng yêu cầu.

### 9.6. Vì sao không lấy thứ tự constructor của Stage để làm bằng chứng `@Order`?

`@Order` chỉ khẳng định thứ tự khi **List được resolve**; nó không yêu cầu Stage constructors/factory methods được gọi theo thứ tự đó. Để tránh đưa ra một kết luận sai, demo kiểm tra **pipeline.labels()** mà không dùng creation events cho Stage. Ngược lại, demo dùng creation/destruction events cho `@DependsOn` vì đó mới là contract của annotation ấy.

### 9.7. Tại sao `Events` không có `@Order`?

`Events` chỉ là collector. Nó không được inject vào `List<Stage>` và không phải một stage. Order metadata trên nó không giúp thay đổi khởi tạo bootstrap/worker. Nó được tạo bởi BeanFactory để ghi events, không tự đưa ra một policy ordering.

### 9.8. Có thể thay bằng constructor injection không?

Nếu Worker thực sự cần Bootstrap object, hãy dùng:

```java
@Bean("worker")
Worker worker(Bootstrap bootstrap, Events events) {
    return new Worker(events);
}
```

Khi đó Bootstrap được resolve trước lời gọi `worker(...)` vì factory method parameter. `@DependsOn` trở nên ít cần thiết nếu chỉ yêu cầu object dependency này; tuy nhiên demo cố ý không inject Bootstrap để minh họa trường hợp ordering dependency không trực tiếp bằng object reference.

Đây là **snippet thay thế minh họa**, không phải toàn bộ bản demo và không bảo toàn destruction order metadata độc lập trong mọi setup nếu bạn loại bỏ quan hệ explicit; cần test lifecycle khi thay cách diễn đạt.

## 10. Kịch bản test âm tính: những cách dùng sai

### 10.1. Dùng `@Order` để ép init-before

Sai về mục tiêu:

```java
@Bean
@Order(-100)
Bootstrap bootstrap() { return new Bootstrap(); }

@Bean
@Order(100)
Worker worker() { return new Worker(); }
```

Các snippets giả định constructors no-arg. **Không có dependency edge** giữa hai definitions. Nếu Worker yêu cầu Bootstrap init trước, dùng `@DependsOn` hoặc DI thật thay vì dựa vào order values.

### 10.2. Dùng `@DependsOn` để sort pipeline

```java
@Bean
@DependsOn("authenticationStage")
Stage auditStage() { return new AuditStage(); }
```

Khai báo này có thể làm `authenticationStage` khởi tạo trước `auditStage`, nhưng không tự sắp `List<Stage>` theo “authentication rồi audit” khi collection được inject. Cần `@Order`/`Ordered` hoặc consumer sorting contract.

### 10.3. Dùng `@Order` trên configuration class cho bean methods

```java
@Configuration
@Order(-100)
class MyConfig {
    @Bean Stage x() { return new StageX(); }
    @Bean Stage y() { return new StageY(); }
}
```

`@Order(-100)` trên `MyConfig` là metadata configuration-level; không trở thành order -100 cho cả `x` và `y`. Nếu muốn x đứng trước y trong injected List, annotate từng `@Bean` method hoặc target classes.

### 10.4. Trông chờ thứ tự XML/Java source

`worker` viết dưới `bootstrap` hôm nay không ngụ ý Worker luôn init sau Bootstrap ở mọi composition và startup mode. Một refactor import/scan có thể đổi cách definitions được đăng ký. Dùng explicit dependencies.

### 10.5. Một dependency tên sai

```java
@Bean
@DependsOn("bootstarp")
Worker worker() {
    return new Worker();
}
```

Tên `bootstarp` bị typo. BeanFactory có thể ném `NoSuchBeanDefinitionException`/`BeanCreationException` khi tạo `worker` bởi named dependency không tồn tại. `@DependsOn` không tự tìm bean có tên gần giống. Viết integration test kiểm tra context refresh và metadata khi rename.

### 10.6. Vòng dependency

Nếu A `@DependsOn("B")` và B `@DependsOn("A")`, BeanFactory sẽ gặp vòng dependency và bean creation có thể thất bại. Đừng sử dụng `@Order` để “giải” vòng: một annotation sort list không sửa dependency graph.

### 10.7. `@Lazy` và `@DependsOn`

Một lazy bean Worker có `@DependsOn("bootstrap")`. Nó chưa bị tạo tại refresh nếu không có consumer kéo vào. Khi Worker thực sự được yêu cầu, container cần khởi tạo Bootstrap trước Worker. `@DependsOn` không tự có nghĩa “mọi named dependency phải được tạo ngay ở refresh” nếu bản thân dependent chưa được instantiate.

### 10.8. Prototype và shutdown

`@DependsOn` có thể điều khiển việc khởi tạo prototype khi BeanFactory tạo prototype, nhưng **Spring không tự gọi destroy callbacks của mọi prototype instances khi context close** như với singletons. Vì vậy không suy ra destruction-order guarantee giống singleton nếu bên phụ thuộc là prototype.

## 11. Phạm vi `@Order` trong các framework components khác

Thứ tự có mặt nhiều nơi trong Spring, nhưng phải xác định **đơn vị nào thực sự được sắp**:

- `List<Handler>` hoặc `Handler[]` được autowire: BeanFactory sắp candidates theo `Ordered`/`@Order`.
- `ObjectProvider<T>.orderedStream()`: API stream có ordering semantics dựa trên comparator phù hợp.
- BeanPostProcessors/BeanFactoryPostProcessors có ordering/registration rules đặc thù; không giả định mọi nơi chỉ cần gắn `@Order` trên class.
- MVC interceptors, security filters, AOP advisors: có thể có ordering APIs riêng; một annotation đặt sai type không thay quy tắc hạ tầng đó.
- `@Configuration` classes: `@Order` tác động evaluation order trong tập configuration classes; không điều khiển bean product startup theo cùng số.

### 11.1. `ObjectProvider.orderedStream()`

Trong Spring, `ObjectProvider<T>` có thể dùng để lấy các candidates của kiểu T theo ordered stream, ví dụ:

```java
List<Step> ordered = provider.orderedStream().toList();
```

Cần một `ObjectProvider<Step> provider` hợp lệ. `orderedStream()` hữu ích khi muốn **hoãn việc lấy một tập plugins đến thời điểm sử dụng**, đồng thời yêu cầu order metadata được áp dụng. Không đồng nhất thứ tự stream với init startup.

### 11.2. `SmartLifecycle` khi cần thứ tự start/stop

Nếu vấn đề của bạn là **dịch vụ nền start/stop theo phases** (scheduler, broker consumer, server, network listener), có thể cần:

```java
public final class WorkerLifecycle implements SmartLifecycle {
    @Override
    public int getPhase() {
        return 100;
    }

    // Các methods isRunning, start, stop,
    // isAutoStartup, stop(Runnable) phải triển khai đầy đủ.
}
```

Đây là trích đoạn **không compile độc lập**, mục đích minh họa phase. Spring `SmartLifecycle` bắt đầu các phases số thấp trước, dừng theo thứ tự ngược lại. Cần phân biệt:
- **Bean creation/init** thường xảy ra trước vòng lifecycle start;
- **SmartLifecycle.start/stop** có các phase/order rules riêng;
- `@DependsOn` cũng có thể ảnh hưởng startup/shutdown dependency relationships;
- `@Order` của pipeline không phải phase start/stop.

Nếu cần startup order của hạ tầng thực, xem cả `SmartLifecycle.getPhase()` thay vì tìm cách ép mọi thứ bằng `@Order` hoặc đảo `@Bean` source methods.

### 11.3. Annotation `@Priority` có khác `@Order` không?

Spring hỗ trợ `jakarta.annotation.Priority` cho nhiều order scenarios (cần Jakarta annotation API phù hợp trên classpath). `@Priority` còn có semantics ở một số tình huống Spring cần chọn **một** element giữa nhiều candidates; `@Order` không có cùng bảo đảm về single-bean selection. `@Priority` không dùng cho `@Bean` method nếu Java target của annotation đó không cho phép methods. Khi muốn từng `@Bean` definition có collection order, dùng `@Order` trên method.

## 12. Kiểm thử thực tế: viết assertions nào?

Một bộ test tốt tách các contract thay vì in một chuỗi events rồi suy đoán.

### 12.1. Test list order

```java
List<String> names = pipeline.labels();
if (!names.equals(List.of("first", "middle", "last"))) {
    throw new AssertionError("Wrong pipeline order");
}
```

Đây là fragment dựa trên demo ở mục 9. Nó xác nhận **thứ tự tại injection point**; không xác nhận init order.

### 12.2. Test depends-on metadata

```java
String[] requires = context.getBeanFactory()
        .getBeanDefinition("worker").getDependsOn();

if (!Arrays.equals(
        requires, new String[] {"bootstrap"})) {
    throw new AssertionError("Missing depends-on");
}
```

Đây là fragment kiểm tra container metadata. Nó chứng minh đúng bean name dependency đã được đăng ký, nhưng không tự thay integration test lifecycle nếu behavior phức tạp.

### 12.3. Test init-before

Tạo hai beans có callbacks/constructors ghi vào `Events`. Xác nhận thứ tự `bootstrap` trước `worker`. Tránh đưa nhiều unrelated constructors vào log rồi kỳ vọng toàn bộ events có một total order không cần thiết.

### 12.4. Test shutdown

Đóng context bằng try-with-resources hoặc explicit `close()`; kiểm tra dependent destroy trước dependency destroy. Nếu bean implement `SmartLifecycle` hoặc có async shutdown, cần xác minh đúng phase/callback contract riêng.

### 12.5. Test khi đổi thứ tự khai báo

Chuyển factory methods sang config class khác, hoặc đảo method source order trong test fixture. Nếu contract được thể hiện bằng `@Order` và `@DependsOn` đúng, list order và init-before relationship vẫn phải đúng. Đây là test rất tốt chống một sự phụ thuộc tình cờ vào source order.

### 12.6. Test thiếu bean

Cố ý đổi `@DependsOn("bootstrap")` thành `@DependsOn("missingBootstrap")` trong một **test configuration riêng**, kỳ vọng startup hoặc lookup của dependent bean thất bại. Đọc exception root cause; tránh bắt mọi `Exception` rồi coi bất kỳ lỗi nào cũng chứng minh dependency check.

### 12.7. Test nhiều beans cùng order value

Nếu hai stages có `@Order(10)` bằng nhau, xây test xem **nghiệp vụ có yêu cầu tie-breaking hay không**. Nếu cần tổng thứ tự deterministic qua nhiều refactors, cấp order values khác nhau hoặc sort bằng explicit application key. Không cần assert một thứ tự tie ngẫu nhiên nếu không phải requirement.

## 13. Bảng lỗi và cách giải quyết

| Triệu chứng | Giải thích | Cách xử lý |
| --- | --- | --- |
| Stage có `@Order(-100)` nhưng tạo sau Stage khác | `@Order` không điều khiển startup | Dùng dependency graph/`@DependsOn` nếu cần init-before |
| List không đúng thứ tự khi gắn `@Order` trên config class | Configuration-level order không truyền xuống `@Bean` methods | Annotate target class hoặc từng `@Bean` method |
| `@DependsOn` đã có nhưng List vẫn sai thứ tự | `@DependsOn` không sort collection | Dùng `@Order`/`Ordered` |
| Đổi XML bean order rồi thứ tự init thay | Chỉ đang dựa vào registration side effect | Khai báo dependency rõ |
| `NoSuchBeanDefinitionException` với `@DependsOn` | Sai bean name/bean không đăng ký | Kiểm tra names/aliases/profiles |
| Circular depends-on | A↔B explicit dependencies | Refactor graph, loại vòng |
| `@Primary` được gắn nhưng List vẫn chứa nhiều beans | Primary chỉ chọn single injection candidate | Dùng `@Order` cho list, hoặc qualifier lọc |
| `@Order` không tự chọn một implementation | Single-bean resolution khác collection sorting | `@Qualifier`/`@Primary` |
| Handler được tiêm đúng List nhưng business calls khác thứ tự | Consumer bỏ qua thứ tự collection | Duyệt List theo contract |
| Bean lazy với `@DependsOn` chưa được tạo lúc startup | Dependent chưa bị instantiate | Kiểm tra trigger tạo dependent |
| Bean prototype không destroy theo shutdown order | Prototype lifecycle khác singleton | Cleanup instance ở owner/application |
| Bean có `@DependsOn` trên class nhưng XML vẫn không tôn trọng | Đăng ký XML bỏ qua class-level annotation trong trường hợp Javadoc nêu | Dùng XML `depends-on` |
| `SmartLifecycle` start order sai | Nhầm phase startup với bean init | Dùng `getPhase()`/dependency và test |
| Unordered List đổi order khi thêm package scan | Registration order/candidate set đổi | Set explicit `@Order` |
| Ordered interface chưa có tác dụng | Consumer không sử dụng comparator/ordered injection | Xác định điểm ordering áp dụng |
| XML `<import>` thay đổi thứ tự nhưng không sửa lỗi | File order không phải dependency contract | Thiết kế init dependency |
| BeanPostProcessor chạy thứ tự khác | Processor infrastructure có rules riêng | Tra cứu `PriorityOrdered`/`Ordered`, registration semantics |
| Cố ép mọi bean chạy theo một số thứ tự duy nhất | Thứ tự thực tế là một partial dependency graph | Chỉ khai báo dependencies cần thiết |

### 13.1. Cách debug ưu tiên bằng chứng

1. **Đọc câu hỏi thật:** đang cần thứ tự List, init callback, start/stop, hay chỉ để hiển thị?
2. **Kiểm tra bean candidates:** bean names/types/qualifiers nào thực sự có trong context?
3. **Với List:** xem annotations trên **target beans/`@Bean` methods** và implementation `Ordered`; kiểm tra order values.
4. **Với startup:** xem constructor injection, `@DependsOn` metadata, `@Lazy` và các infrastructure beans.
5. **Với shutdown:** đóng đúng context; xem destroy callbacks và dependency direction.
6. **Với source order:** ghi lại registration order nếu cần debug, nhưng đừng biến trace đó thành một API guarantee.
7. **Với lifecycle services:** phân biệt bean initialization với `SmartLifecycle.start()`/`stop()` phases.
8. **Đọc root cause:** lỗi missing dependency/circular reference khác hoàn toàn lỗi ambiguous collection hoặc wrong sorting.
9. **Kiểm thử trên Spring version thực tế:** Spring 6.2 có background initialization và các candidate-selection rules có thể ảnh hưởng những giả định cũ.
10. **Không sửa bằng cách “đổi vị trí hai methods”** trước khi xem một dependency contract có thiếu hay không.

## 14. Bài tập có đáp án

### Bài 1 — Lựa chọn annotation

Một hệ thống có ba `Handler`, muốn handlers trong injected `List<Handler>` theo thứ tự xác thực → validate → audit. Dùng gì?

**Đáp án:** `@Order` hoặc `Ordered` trên handler beans (hoặc sort explicit) và consumer duyệt ordered List. Không dùng `@DependsOn` chỉ để sort List.

### Bài 2 — Side effect phải sẵn sàng trước

Bean `worker` cần một `schemaMigrator` chạy initialization trước khi worker init, nhưng không có reference đến migrator. Dùng gì?

**Đáp án:** `@DependsOn("schemaMigrator")` trên worker hoặc tạo một dependency thật nếu nghiệp vụ có object relation.

### Bài 3 — Hai beans được viết theo thứ tự trong Java

Method `@Bean Service a()` nằm trước `@Bean Service b()`. Có tự bảo đảm a được tạo trước b không?

**Đáp án:** Không có dependency contract như vậy. Bean creation chịu BeanFactory graph, scopes và startup processing.

### Bài 4 — Âm hay dương?

`@Order(-5)` và `@Order(15)` trên hai Step. Ai đứng trước trong ordered List?

**Đáp án:** `-5` đứng trước vì số nhỏ có ưu tiên cao.

### Bài 5 — `@Order` trên configuration

`@Configuration @Order(-100)` có hai `@Bean Step` methods không order metadata. Cả hai có tự nhận `@Order(-100)` không?

**Đáp án:** Không. Configuration-level `@Order` ảnh hưởng evaluation order của config classes, không tự áp lên product beans.

### Bài 6 — Fallback registration

Không Stage nào có `@Order`. Spring docs nói List order thường dựa vào yếu tố nào?

**Đáp án:** Registration order của target bean definitions. Nhưng không nên dựa vào source declaration order như một bảo đảm dài hạn.

### Bài 7 — Destroy sequence

`@DependsOn("bootstrap")` đặt trên bean `worker`, cả hai singleton có destroy callbacks. Ai hủy trước?

**Đáp án:** Worker bị hủy trước Bootstrap khi context shutdown theo dependency relation.

### Bài 8 — `@Order` và singleton startup

`@Order(0)` trên FastHandler, `@Order(100)` trên SlowHandler. Điều đó ép FastHandler constructor luôn chạy trước SlowHandler không?

**Đáp án:** Không. `@Order` không điều khiển singleton initialization order.

### Bài 9 — Bean name

Bean được đặt `@Bean("primaryCache")`, consumer `@DependsOn("Cache")`. Spring có tự match theo class type không?

**Đáp án:** Không. DependsOn dùng names/aliases, nên hãy dùng `"primaryCache"` hoặc tên alias hợp lệ.

### Bài 10 — Constructor injection thay ordering

Bean B có constructor `B(A a)` và A là bean Spring. Có cần `@DependsOn("a")` chỉ để đảm bảo A đã được resolve trước khi B constructed?

**Đáp án:** Thông thường không; constructor injection là dependency edge thực đã buộc Spring resolve A. `@DependsOn` hữu ích cho side effects khác không có tham số.

### Bài 11 — `@Primary`

Có ba Stage beans, một `@Primary`. Khi inject `List<Stage>`, Spring chỉ inject primary bean?

**Đáp án:** Không. Collection injection thường nhận mọi eligible beans; order/filter riêng mới quyết định tập và thứ tự.

### Bài 12 — `@Qualifier`

Consumer cần một `List<Stage>` chỉ gồm nhóm `critical` và được sort. Cần những bước nào?

**Đáp án:** Lọc bằng qualifier/selection metadata thích hợp, rồi áp dụng ordering trong candidate set bằng `@Order`/`Ordered` hoặc explicit sort.

### Bài 13 — Vòng `@DependsOn`

Bean A dependsOn B, B dependsOn A. Có thể giải bằng `@Order(0)` và `@Order(1)` không?

**Đáp án:** Không. Cycle dependency graph phải được thiết kế lại; `@Order` không xóa cạnh phụ thuộc.

### Bài 14 — Prototype và destruction

Prototype Worker dependsOn Singleton Bootstrap. Khi context close, Spring có tự tìm mọi prototype Workers đã cấp để chạy destroy đúng trước bootstrap không?

**Đáp án:** Không. Spring không quản lý tự động việc hủy mọi prototype như singleton; owner cần cleanup đúng.

### Bài 15 — SmartLifecycle

Một server cần Listener A start trước Listener B và stop sau B; hai beans implement `SmartLifecycle`. Nên xem cơ chế nào?

**Đáp án:** `getPhase()` và các dependencies tương ứng; phase nhỏ start trước, stop sau, không dùng `@Order` của List để ép lifecycle.

### Bài 16 — Order bằng nhau

Hai beans có `@Order(10)`, thứ tự tuyệt đối giữa chúng rất quan trọng. Có nên dựa vào thứ tự scan/register không?

**Đáp án:** Không; cấp thứ tự phân biệt hoặc dùng application comparator/key explicit và test.

### Bài 17 — Test scope

Trong demo, `Pipeline` in order đúng nhưng `Events` cho init order ngược. Có thể cả hai đúng theo Spring contract không?

**Đáp án:** Có. Collection sorting và singleton initialization là hai mục tiêu độc lập; phải biết dependency graph.

### Bài 18 — Annotation trên XML-declared class

Class `Worker` mang `@DependsOn("bootstrap")` nhưng được khai `<bean class="...">` trong legacy XML. Annotation trên class có chắc được áp dụng không?

**Đáp án:** Theo `@DependsOn` Javadoc, class-level annotation được xử lý theo component scanning; khi đăng ký bằng XML theo con đường nêu trong Javadoc, cần dùng XML `depends-on` để bảo đảm.

### Bài 19 — Thêm `@Order` vào một bean hạ tầng

Một BeanFactoryPostProcessor chạy quá muộn; team chỉ thêm `@Order(-999)` mà không kiểm tra cách processor được register. Đã đủ chưa?

**Đáp án:** Chưa. Infrastructure extension points có ordering policies riêng (`PriorityOrdered`/`Ordered` và registration rules); cần tra đúng extension point.

### Bài 20 — Yêu cầu nhiều dependencies

Service phải chờ Migrator và CacheWarmup, CacheWarmup phải chờ Migrator. Có nên chỉ đặt `@DependsOn({"migrator","cacheWarmup"})` trên service không?

**Đáp án:** Chưa biểu đạt trực tiếp dependency giữa CacheWarmup và Migrator. Hãy khai `cacheWarmup` dependsOn `migrator`, service dependsOn cacheWarmup (và những quan hệ khác thật sự cần).

## 15. Checklist trước khi merge cấu hình bean ordering

- Requirement là **List order** hay **bean init-before** hay **runtime start/stop**?
- Với List, `@Order` có đặt trên **target beans/`@Bean` methods** chứ không chỉ config class?
- Có đúng convention số nhỏ đứng trước? Có ties ảnh hưởng nghiệp vụ?
- Consumer có thực sự duyệt ordered List hay lại tự lookup theo name/map?
- `@Primary`/`@Qualifier` có được dùng đúng để chọn/giới hạn candidates, không nhầm order?
- Nếu không có order metadata, có đang dựa vào registration fallback như một hợp đồng mong manh?
- Với init dependency, `@DependsOn`/constructor injection có biểu diễn cạnh phụ thuộc thật không?
- Những names trong `@DependsOn` có tồn tại đúng theo XML/JavaConfig/alias registration?
- `@DependsOn` ở class có thực sự được Spring đọc theo phương thức đăng ký bean hiện tại?
- Lazy/prototype/FactoryBean/background init có thay đổi thời điểm tạo dependency?
- Với singleton destroy, đã test dependent hủy trước dependency khi context close chưa?
- Có vô tình tạo vòng dependsOn/dependency graph không?
- Với `SmartLifecycle` đã dùng phase/start/stop semantics riêng khi cần?
- Tests có kiểm tra **cả** injected List và startup/shutdown log thay vì gộp thành một assertion?
- Refactor imports, scan roots hoặc source file method order có làm thay đổi bất kỳ contract thực sự nào không?
- Metadata ordering có được ghi rõ trong tài liệu module để người bảo trì không sửa sai?

## 16. Tổng kết

**`@Order`** trả lời “nếu có nhiều ứng viên cùng loại được tiêu thụ như một danh sách, phần tử nào đứng trước?”; số nhỏ được ưu tiên. Annotation có thể đặt trên bean class hoặc từng `@Bean` method; khi đặt trên `@Configuration` class, nó không tự truyền order xuống từng product bean. `@Order` **không** bảo đảm thứ tự tạo singletons.

**`@DependsOn`** trả lời “để tạo bean này, những bean nào phải được khởi tạo trước?” và, với singleton beans, “khi shutdown, dependent phải được hủy trước dependency nào?”. Nó dùng **bean names**, không phải Java class names, và không sắp List.

**Thứ tự định nghĩa/đăng ký** là một trục khác: Spring có thể dùng registration order làm fallback cho unordered injected List, nhưng source method/XML order **không phải** cách chuẩn để ràng buộc init-before. Hãy dùng đúng abstraction cho requirement: `@Order` cho tập cần sort; DI hoặc `@DependsOn` cho dependency initialization; `SmartLifecycle` phase cho start/stop services.

### Tài liệu Spring Framework 6.2 chính thức

- [Spring Framework 6.2 — Using `@Autowired`, section Arrays and Collections](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html).
- [Spring Framework 6.2 — Using `depends-on`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html).
- [Spring Framework 6.2 — Composing Java-based Configurations, startup singletons](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/composing-configuration-classes.html).
- [Spring Framework 6.2 — `@Order` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/annotation/Order.html).
- [Spring Framework 6.2 — `@DependsOn` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/DependsOn.html).
- [Spring Framework 6.2 — Customizing the Nature of a Bean: Lifecycle and `SmartLifecycle`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html).
- [Spring Framework 6.2 — `@Bean` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-creation-timing-008">Quan hệ phụ thuộc và thứ tự hủy singleton</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ phụ thuộc và thứ tự hủy singleton trong Spring Core Container

## 1. Vì sao thứ tự hủy bean quan trọng?

Khi xây dựng ứng dụng Spring, chúng ta thường tập trung vào **cách tạo bean** và **cách inject dependency**. Nhưng nếu nhiều singleton bean cùng nắm tài nguyên, thứ tự **dừng và giải phóng** chúng cũng quan trọng không kém.

Giả sử:

- `dataStore` đại diện cho một client/connection pool dùng chung.
- `reportWriter` cần `dataStore` để ghi nốt kết quả trong `destroy()`.
- `jobCoordinator` cần `reportWriter` để hoàn tất hoặc yêu cầu flush một công việc trước khi đóng.

Quan hệ phụ thuộc là:

```text
jobCoordinator --> reportWriter --> dataStore
       "dùng"          "dùng"
```

Khi tạo, dependency phải sẵn sàng trước dependent:

```text
dataStore.init
reportWriter.init
jobCoordinator.init
```

Khi hủy, thứ tự mong muốn là **ngược lại**:

```text
jobCoordinator.destroy
reportWriter.destroy
dataStore.destroy
```

Nếu `dataStore` bị đóng trước `reportWriter`, lời gọi flush lúc shutdown của writer có thể thất bại. Đó là lý do Spring quản lý **dependency relationships** và sử dụng chúng cả ở **initialization** lẫn **destruction** của singleton beans.

**Quy tắc cốt lõi:**

> Nếu bean A phụ thuộc bean B theo quan hệ mà Spring biết, Spring cố gắng khởi tạo B trước A và, đối với các singleton được quản lý phù hợp, hủy A trước B.

Quy tắc này đặc biệt được tài liệu hóa cho **`@DependsOn` và XML `depends-on`**. Container còn theo dõi nhiều quan hệ phát sinh khi resolve dependency injection. Nhưng không phải **mọi Java reference ngoài container** đều tự trở thành một quan hệ mà Spring biết.

### Mục tiêu học tập

Sau bài học, bạn có thể:

1. Phân biệt **phụ thuộc logic**, **injection dependency** và **registered dependency relationship**.
2. Hiểu tại sao thứ tự tạo thường đi từ dependency đến dependent, còn thứ tự hủy đi ngược lại.
3. Dùng `@DependsOn` và XML `depends-on` để biểu thị ràng buộc lifecycle không thể hiện bằng DI.
4. Hiểu các quan hệ được Spring ghi nhận qua constructor/method injection và API `registerDependentBean`.
5. Sử dụng `getDependentBeans(name)` và `getDependenciesForBean(name)` để chẩn đoán dependency graph.
6. Nắm vai trò của `@PreDestroy`, `DisposableBean`, `destroyMethod` và `ApplicationContext.close()`.
7. Phân biệt singleton với prototype, lazy singleton, custom scopes, inner beans và objects nằm ngoài IoC Container.
8. Hiểu những thứ Spring **không** cam kết: thứ tự toàn cục giữa beans không liên hệ, đóng thread tự tạo, readiness của remote services hoặc ordering giữa những ApplicationContexts độc lập.
9. Chạy ví dụ Java 17+/Spring Framework 6.2.19 để kiểm chứng **chain phụ thuộc, DI, XML, lazy và prototype**.
10. Thiết kế shutdown có kiểm thử, tránh mất dữ liệu và lỗi dùng tài nguyên sau khi đã đóng.

## 2. Ba loại quan hệ cần phân biệt

### 2.1. Quan hệ object thực sự: dependency injection

```java
public final class ReportWriter {
    private final DataStore store;

    public ReportWriter(DataStore store) {
        this.store = store;
    }
}
```

Đây là snippet minh họa, cần class/interface `DataStore`. Constructor thể hiện rõ object `ReportWriter` cần object `DataStore`. Khi Spring resolve dependency này, container phải chuẩn bị một candidate phù hợp trước khi có thể truyền vào constructor.

Ví dụ Java configuration:

```java
@Bean
ReportWriter reportWriter(DataStore store) {
    return new ReportWriter(store);
}
```

Hoặc XML:

```xml
<bean id="reportWriter" class="com.example.ReportWriter">
    <constructor-arg ref="dataStore"/>
</bean>
```

Spring còn có thể ghi nhận relation lúc giải quyết dependency để quản lý destruction ordering. Điều này khác với một reference mà code Java tự tạo và giữ bên ngoài bean factory, vì Spring không thể suy luận mọi đối tượng trong JVM.

### 2.2. Phụ thuộc side effect: @DependsOn

```java
@Bean("reportWriter")
@DependsOn("registryInitializer")
ReportWriter reportWriter(DataStore store) {
    return new ReportWriter(store);
}
```

Ở đây có **hai** relation:

- `reportWriter` nhận trực tiếp `DataStore` qua DI.
- `reportWriter` phải đợi `registryInitializer` thực hiện initialization dù không giữ reference của initializer.

`@DependsOn` chính là cách khai báo ràng buộc **bean lifecycle ordering** theo tên khi không có object injection tương ứng. Khi cả hai đều là singletons có destroy callbacks, relation này còn ảnh hưởng thứ tự hủy.

### 2.3. Quan hệ chỉ tồn tại trong business code

```java
public void run() {
    GlobalRegistry.getInstance().publish("report");
}
```

Nếu `GlobalRegistry` do một thư viện tự quản lý, Spring có thể **không biết** bean nào cần sống lâu hơn bean nào. Tương tự, gọi `new ExternalClient()` trong business method không làm external client trở thành một Spring-managed singleton.

**Không được nhầm:** việc “một class có sử dụng một class khác” ở runtime không đồng nghĩa Spring đã lưu một dependency edge để hủy theo đúng thứ tự. Nếu sự tồn tại của dependency là quan trọng, hãy inject một managed bean hoặc khai báo ordering relation đúng nghĩa.

## 3. Từ đồ thị tạo bean đến đồ thị hủy bean

Xem ba singleton:

```text
service -> repository -> connectionPool
```

Mỗi mũi tên `A -> B` nghĩa là **A depends on B**. Ta có thể mô hình hóa:

| Bean | Những dependency cần có trước |
| --- | --- |
| `connectionPool` | Không có |
| `repository` | `connectionPool` |
| `service` | `repository` |

Với quan hệ được container biết, một thứ tự tạo hợp lệ là:

```text
connectionPool -> repository -> service
```

Khi context đóng, một thứ tự hủy hợp lệ là:

```text
service -> repository -> connectionPool
```

Spring không bắt buộc tạo một **thứ tự tuyến tính duy nhất** cho các beans hoàn toàn độc lập. Ví dụ thêm `metricsFormatter` không liên hệ, không nên viết test đòi hỏi formatter nhất định được destroy trước hay sau pool nếu không khai báo quan hệ nào.

### 3.1. Vì sao thứ tự ngược lại?

Dependent bean cần dependency vẫn còn sống trong lúc cleanup. Một ví dụ shutdown:

```text
service.destroy():
    gửi nốt báo cáo qua repository
repository.destroy():
    flush dữ liệu chưa ghi qua connectionPool
connectionPool.destroy():
    đóng pool
```

Nếu connectionPool bị đóng đầu tiên, hai callbacks phía trên có thể không còn sử dụng được nó. Thứ tự ngược với dependency direction giữ resource sống đủ lâu cho dependent cleanup.

### 3.2. “Destroy” chính xác là gì?

Trong Spring, bean destruction có thể kích hoạt các cơ chế như:

- `@PreDestroy` khi hạ tầng Jakarta annotations tương ứng được đăng ký.
- Interface `org.springframework.beans.factory.DisposableBean` với method `destroy()`.
- Custom/inferred destroy method, ví dụ `@Bean(destroyMethod="close")` hoặc `destroy-method="close"`.
- Destruction-aware BeanPostProcessors và callbacks của những scope đặc biệt.

Cơ chế nào chạy, thứ tự callback **bên trong cùng một bean**, và việc suy ra destroy method phụ thuộc metadata/version. **Ordering giữa beans** là một vấn đề khác so với **callback order trong cùng bean**.

## 4. Thứ tự hủy được Spring thực hiện ở đâu?

`ConfigurableApplicationContext.close()` kích hoạt quá trình đóng context. Trong Spring Core Container, phần hủy singleton được thực hiện qua `BeanFactory`/singleton registry, trong đó `DefaultSingletonBeanRegistry` quản lý cached singletons, disposable bean registrations và dependency relations.

Một mô hình khái niệm:

```text
ApplicationContext.close()
             |
             v
        Context shutdown
             |
             v
      Destroy registered singletons
             |
             +--> Nếu X có các dependent beans:
             |       destroy các dependent beans trước
             |
             +--> callback destroy của X
             |
             +--> xử lý liên hệ containment/dependencies
             |
             v
       Singleton registry cleanup
```

**Quan trọng:** thứ tự hủy không được quyết định đơn giản bằng cách đảo ngược thứ tự các dòng XML, tên class, hay `@Bean` method declarations. Dependency graph được ghi nhận trong container có vai trò chủ đạo.

### 4.1. DefaultSingletonBeanRegistry lưu loại dữ liệu nào?

API của `DefaultSingletonBeanRegistry` có các phương thức:

```java
registerDependentBean(String beanName, String dependentBeanName);
getDependentBeans(String beanName);
getDependenciesForBean(String beanName);
```

Cách hiểu:

- `registerDependentBean("store", "writer")`: **writer là dependent của store**. Khi destroy store, phải destroy writer trước.
- `getDependentBeans("store")`: trả tên các beans phụ thuộc store mà registry biết.
- `getDependenciesForBean("writer")`: trả tên các bean mà writer phụ thuộc theo registered dependency relation.

Đây là **Spring container metadata**. Chúng không phải phương thức của application domain `DataStore`, cũng không tự dò mọi Java field reference.

### 4.2. Hai cách đọc ngược chiều

Với `writer depends on store`:

```text
getDependentBeans("store")          -> ["writer"] (quan hệ trực tiếp)
getDependenciesForBean("writer")    -> ["store"]  (quan hệ trực tiếp)
```

Danh sách chính xác còn phụ thuộc những registrations khác trong context. Đừng dựa vào **thứ tự phần tử trả về từ các mảng** để tạo global shutdown order; mục tiêu của hai API là quan sát relation names, không phải cung cấp một schedule đầy đủ cho mọi bean.

### 4.3. Đừng nhầm Dependency API với DI candidate API

`getBeanNamesForType(ReportWriter.class)` tìm beans **theo type**. `getDependentBeans("store")` đọc **registered relations**. Một type có thể tồn tại và được Spring quản lý nhưng không phụ thuộc store; ngược lại quan hệ `depends-on` không bắt buộc Java class chứa field của store.

## 5. Khai báo depends-on trong Java configuration

Một config thường dùng:

```java
@Configuration(proxyBeanMethods = false)
public class ReportsConfig {

    @Bean("store")
    DataStore store() {
        return new DataStore();
    }

    @Bean("writer")
    @DependsOn("store")
    ReportWriter writer() {
        return new ReportWriter();
    }
}
```

Đây là snippet cấu trúc, cần imports `Bean`, `Configuration`, `DependsOn` và hai Java types có constructors phù hợp.

`@DependsOn("store")` đặt trên bean **writer**, nghĩa là writer không được khởi tạo trước store. Nếu là hai managed singletons có destroy callbacks, Spring cũng cần destroy writer trước store.

### 5.1. Không đảo ngược mũi tên

Nếu mục tiêu **writer phải đóng trước store**, đặt:

```java
@Bean("writer")
@DependsOn("store")
ReportWriter writer() {
    return new ReportWriter();
}
```

**Không phải:**

```java
@Bean("store")
@DependsOn("writer")
DataStore store() {
    return new DataStore();
}
```

Đoạn thứ hai lại nói **store phụ thuộc writer** và đảo semantics. Mẹo nhớ: *dependent* là bean đang mang `@DependsOn`; bean được liệt kê là **prerequisite**.

### 5.2. Nhiều dependency names

```java
@Bean("reportProcessor")
@DependsOn({"metricsRegistry", "writeAheadLog"})
ReportProcessor reportProcessor() {
    return new ReportProcessor();
}
```

Cả hai prerequisites được yêu cầu khởi tạo trước processor. Khi hủy singletons, processor bị destroy trước cả hai prerequisites.

**Không suy ra** Spring bắt buộc metricsRegistry được destroy trước hay sau writeAheadLog chỉ vì hai tên xuất hiện theo một thứ tự trong mảng. Nếu hai prerequisites còn quan hệ thứ tự riêng, biểu diễn relation đó trực tiếp.

### 5.3. Annotation class-level và component scan

`@DependsOn` có thể đặt trên một class được component scanning đăng ký:

```java
@Component("reportWorker")
@DependsOn("metricsRegistry")
public class ReportWorker {
}
```

**Giới hạn:** Javadoc `@DependsOn` nêu rằng class-level annotation **chỉ có hiệu lực khi component scanning được dùng**. Nếu class chỉ được đăng ký bằng XML `<bean>`, Spring tôn trọng XML `depends-on` thay vì annotation class-level. Đây là bẫy khi chuyển cấu hình Java/annotation sang XML legacy.

## 6. Khai báo depends-on trong XML

XML có `depends-on` trên phần tử `<bean>`:

```xml
<bean id="reportWriter"
      class="com.example.ReportWriter"
      depends-on="dataStore"/>

<bean id="dataStore"
      class="com.example.DataStore"/>
```

Dù writer xuất hiện **trước store trong file XML**, store vẫn là bean prerequisite. Với hai singleton có destroy callbacks:

```text
Initialization: dataStore -> reportWriter
Destruction:    reportWriter -> dataStore
```

Khi có nhiều prerequisite names:

```xml
<bean id="reportWriter"
      class="com.example.ReportWriter"
      depends-on="metricsRegistry, dataStore"/>
```

Spring hỗ trợ danh sách names phân cách bằng **phẩy, chấm phẩy hoặc khoảng trắng**.

### 6.1. ref và depends-on không giống nhau

XML reference:

```xml
<bean id="reportWriter" class="com.example.ReportWriter">
    <constructor-arg ref="dataStore"/>
</bean>
```

sẽ inject **object bean dataStore** vào constructor.

XML depends-on:

```xml
<bean id="reportWriter" class="com.example.ReportWriter"
      depends-on="dataStore"/>
```

chỉ thêm **ordering constraint**. Nó không truyền object dataStore vào ReportWriter. Nếu writer thực sự cần sử dụng store, constructor/setter injection mới thể hiện dependency đúng về Java type.

### 6.2. XML khai báo bean tên gì?

`depends-on="dataStore"` dùng **bean name hoặc alias hợp lệ**, không phải class name tùy ý. Nếu XML khai báo `<bean id="primaryStore" class="com.example.DataStore"/>`, bạn không thể tự đoán tên `dataStore` từ Java type và mong Spring resolve. Chọn một canonical name ổn định và document quan hệ.

## 7. Phụ thuộc đã có DI còn cần @DependsOn không?

Không phải lúc nào cũng cần. Ví dụ:

```java
@Bean
DataStore store() {
    return new DataStore();
}

@Bean
ReportWriter writer(DataStore store) {
    return new ReportWriter(store);
}
```

Spring phải resolve DataStore trước khi gọi `writer(DataStore store)`. Trong những đường DI tiêu chuẩn, framework còn đăng ký dependent relationship tương ứng nhằm hỗ trợ quản lý destruction.

Nhưng **đừng phát biểu** rằng chỉ vì class A có một field kiểu B thì framework chắc chắn ghi relationship: chỉ những dependencies được container **thực sự resolve và đăng ký** mới có thể quan sát trong registry. Nếu tự `new B()` hoặc gọi một static accessor ngoài Spring, không có guarantee như vậy.

### 7.1. Khi nào thêm @DependsOn có ý nghĩa dù có DI?

Ví dụ service inject `DataStore` nhưng cũng cần `globalCodecRegistrar` đăng ký một static codec không được biểu diễn qua constructor:

```java
@Bean
@DependsOn("globalCodecRegistrar")
ReportWriter writer(DataStore store) {
    return new ReportWriter(store);
}
```

Relation qua constructor bảo đảm DataStore; `@DependsOn` bảo đảm initializer. Điều này hợp lý nếu initializer thực sự cần thiết và có lifecycle managed.

### 7.2. Tránh @DependsOn thừa

Thêm `@DependsOn("store")` cho mọi bean vốn đã constructor-inject store chỉ để “cho chắc” tạo noise và dễ dẫn tới coupling bằng string names. DI rõ kiểu đã diễn đạt object dependency. Chỉ khai báo ordering metadata riêng khi nó có **nghĩa bổ sung**.

## 8. Lifecycle callbacks tham gia destruction

### 8.1. DisposableBean

```java
import org.springframework.beans.factory.DisposableBean;

public final class FileWriterService implements DisposableBean {
    @Override
    public void destroy() {
        // Flush rồi đóng file writer.
    }
}
```

Container gọi `destroy()` khi managed singleton được hủy theo lifecycle phù hợp. Interface phụ thuộc Spring API, nhưng là lựa chọn rõ ràng cho demo kiểm chứng thứ tự.

### 8.2. @PreDestroy

```java
import jakarta.annotation.PreDestroy;

public final class NetworkClient {
    @PreDestroy
    void shutdown() {
        // Gửi tín hiệu đóng tài nguyên.
    }
}
```

Đây là snippet minh họa. Với Spring 6+, cần `jakarta.annotation-api` trên classpath nếu source dùng `jakarta.annotation.PreDestroy` và context có post-processor thích hợp. Java 17 không tự cung cấp tất cả Jakarta annotations.

`@PreDestroy` là **callback của một bean**, không phải cách khai báo một bean cần destroy trước bean khác. Để ràng buộc ordering **giữa beans**, dùng DI/depends-on phù hợp.

### 8.3. @Bean(destroyMethod = "...")

```java
@Bean(destroyMethod = "close")
ClientConnection clientConnection() {
    return new ClientConnection();
}
```

Spring có thể gọi method `close()` khi singleton đóng nếu method hợp lệ và bean được container quản lý. Đây là cách cấu hình không đòi class implement Spring `DisposableBean`.

### 8.4. XML destroy-method

```xml
<bean id="fileClient"
      class="com.example.FileClient"
      destroy-method="close"/>
```

Container biết method cleanup. Nếu `reportWorker` còn dùng fileClient khi destroy, hãy đảm bảo relation qua DI hoặc:

```xml
<bean id="reportWorker"
      class="com.example.ReportWorker"
      depends-on="fileClient"/>
```

### 8.5. AutoCloseable không phải toàn bộ hợp đồng

Spring có khả năng suy luận destroy methods như `close` hoặc `shutdown` trong nhiều trường hợp bean factory methods có default destroy method inference; tuy nhiên metadata và cách đăng ký bean quyết định việc thực tế callback nào được gọi. Đừng chỉ thấy Java object implement `AutoCloseable` rồi khẳng định mọi container/scope sẽ tự đóng nó. Với tài nguyên critical, cấu hình và kiểm thử destruction path rõ ràng.

### 8.6. Những callback chạy trong một bean

Một bean có thể vừa có `@PreDestroy`, vừa implement `DisposableBean`, vừa có custom destroy method. Spring có thứ tự callback phù hợp, nhưng bài này tập trung vào **thứ tự hủy giữa beans**. Không được suy luận “destroy callback của bean A đã chạy” chỉ từ việc bean B có một annotation khác.

## 9. Ví dụ hoàn chỉnh Java 17+: quan sát cả khởi tạo và hủy

Ví dụ sau dùng **Spring Framework 6.2.19** thuần (không cần Boot), Gradle và hai ApplicationContext APIs.

Nó tạo **năm trường hợp độc lập**:

1. Chuỗi ba singleton với `@DependsOn`.
2. Hai singleton khai báo bằng XML `depends-on`.
3. Hai singleton được kết nối bằng **constructor/method-parameter DI**.
4. Một `@Lazy` singleton vẫn bị non-lazy dependent kéo vào startup.
5. Một prototype bean được tạo, nhưng **không tự có destroy callback khi context đóng**.

Các beans ghi sự kiện thông qua `InitializingBean`/`DisposableBean`. Chương trình in **một thứ tự callback thực tế của các quan hệ đã cấu hình**, không suy đoán từ timestamps hoặc XML line ordering.

### 9.1. Cây thư mục

```text
singleton-shutdown-demo/
├── settings.gradle
├── build.gradle
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       └── ShutdownDemo.java
        └── resources/
            └── beans.xml
```

Một file Java duy nhất với nested public static classes và một resource XML. Tên nested class trong XML dùng **Java binary name** `demo.ShutdownDemo$Tracked`.

### 9.2. settings.gradle

```groovy
rootProject.name = 'singleton-shutdown-demo'
```

### 9.3. build.gradle

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ShutdownDemo'
}
```

Project chỉ cần Spring Context cùng transitive dependencies, Java 17+ và Gradle tương thích Java toolchains. Không có database, network hoặc thư viện Jakarta extra vì demo dùng Spring lifecycle interfaces.

### 9.4. beans.xml

**`src/main/resources/beans.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Worker nằm trước trong XML nhưng phụ thuộc store. -->
    <bean id="xmlWorker"
          class="demo.ShutdownDemo$Tracked"
          depends-on="xmlStore">
        <constructor-arg value="xmlWorker"/>
    </bean>

    <bean id="xmlStore"
          class="demo.ShutdownDemo$Tracked">
        <constructor-arg value="xmlStore"/>
    </bean>

</beans>
```

Hai beans đều là singleton mặc định. `depends-on="xmlStore"` buộc XML store khởi tạo trước worker, và worker hủy trước store.

### 9.5. ShutdownDemo.java

**`src/main/java/demo/ShutdownDemo.java`**

```java
package demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class ShutdownDemo {

    private static final List<String> EVENTS = new ArrayList<>();

    public static class Tracked
            implements InitializingBean, DisposableBean {

        private final String name;

        public Tracked(String name) {
            this.name = name;
            EVENTS.add(name + ".new");
        }

        @Override
        public void afterPropertiesSet() {
            EVENTS.add(name + ".init");
        }

        @Override
        public void destroy() {
            EVENTS.add(name + ".destroy");
        }
    }

    public static class Client
            implements InitializingBean, DisposableBean {

        private final Tracked store;

        public Client(Tracked store) {
            this.store = store;
            EVENTS.add("diClient.new");
        }

        @Override
        public void afterPropertiesSet() {
            if (store == null) {
                throw new IllegalStateException("missing store");
            }
            EVENTS.add("diClient.init");
        }

        @Override
        public void destroy() {
            EVENTS.add("diClient.destroy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChainConfig {

        @Bean("service")
        @DependsOn("repository")
        Tracked service() {
            return new Tracked("service");
        }

        @Bean("repository")
        @DependsOn("cache")
        Tracked repository() {
            return new Tracked("repository");
        }

        @Bean("cache")
        Tracked cache() {
            return new Tracked("cache");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DiConfig {

        @Bean("diStore")
        Tracked diStore() {
            return new Tracked("diStore");
        }

        @Bean("diClient")
        Client diClient(Tracked diStore) {
            return new Client(diStore);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyConfig {

        @Bean("lazyStore")
        @Lazy
        Tracked lazyStore() {
            return new Tracked("lazyStore");
        }

        @Bean("lazyWorker")
        @DependsOn("lazyStore")
        Tracked lazyWorker() {
            return new Tracked("lazyWorker");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrototypeConfig {

        @Bean("prototype")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        Tracked prototype() {
            return new Tracked("prototype");
        }
    }

    private static void run(
            String label,
            Supplier<? extends ConfigurableApplicationContext> supplier) {

        EVENTS.clear();
        int cutoff;

        try (ConfigurableApplicationContext context = supplier.get()) {
            System.out.println(label + ".init="
                    + String.join(",", EVENTS));

            if ("chain".equals(label)) {
                var factory = context.getBeanFactory();

                boolean cacheHasRepository = Arrays.asList(
                    factory.getDependentBeans("cache")
                ).contains("repository");

                boolean serviceNeedsRepository = Arrays.asList(
                    factory.getDependenciesForBean("service")
                ).contains("repository");

                System.out.println("chain.relations="
                    + cacheHasRepository + ","
                    + serviceNeedsRepository);
            }

            if ("di".equals(label)) {
                var factory = context.getBeanFactory();
                boolean clientDependsOnStore = Arrays.asList(
                    factory.getDependentBeans("diStore")
                ).contains("diClient");

                System.out.println("di.relation="
                    + clientDependsOnStore);
            }

            cutoff = EVENTS.size();
        }

        System.out.println(label + ".destroy="
            + String.join(",",
                EVENTS.subList(cutoff, EVENTS.size())));
    }

    public static void main(String[] args) {

        run("chain", () -> new AnnotationConfigApplicationContext(
            ChainConfig.class));

        run("xml", () -> new ClassPathXmlApplicationContext(
            "beans.xml"));

        run("di", () -> new AnnotationConfigApplicationContext(
            DiConfig.class));

        run("lazy", () -> new AnnotationConfigApplicationContext(
            LazyConfig.class));

        EVENTS.clear();

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                    PrototypeConfig.class)) {

            context.getBean("prototype");
            System.out.println("prototype.init="
                + String.join(",", EVENTS));
        }

        System.out.println("prototype.destroy-after-close="
            + EVENTS.contains("prototype.destroy"));
    }
}
```

### 9.6. Chạy

Từ thư mục `singleton-shutdown-demo`:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper:

```bash
./gradlew run --quiet
```

Windows sử dụng:

```bat
gradlew.bat run --quiet
```

Chạy `javac ShutdownDemo.java` độc lập sẽ thiếu Spring classes nếu chưa đặt dependencies vào classpath. Gradle tự compile, resolve thư viện và copy `src/main/resources/beans.xml` vào runtime classpath.

### 9.7. Stdout dự kiến từ các println

```text
chain.init=cache.new,cache.init,repository.new,repository.init,service.new,service.init
chain.relations=true,true
chain.destroy=service.destroy,repository.destroy,cache.destroy
xml.init=xmlStore.new,xmlStore.init,xmlWorker.new,xmlWorker.init
xml.destroy=xmlWorker.destroy,xmlStore.destroy
di.init=diStore.new,diStore.init,diClient.new,diClient.init
di.relation=true
di.destroy=diClient.destroy,diStore.destroy
lazy.init=lazyStore.new,lazyStore.init,lazyWorker.new,lazyWorker.init
lazy.destroy=lazyWorker.destroy,lazyStore.destroy
prototype.init=prototype.new,prototype.init
prototype.destroy-after-close=false
```

Đây là các dòng được chương trình chủ động in bằng `System.out.println`; log từ Gradle/framework, nếu có, không thuộc những dòng này. Recorder chỉ theo dõi các classes `Tracked`/`Client` trong demo, không giả định ghi toàn bộ Spring infrastructure events.

### 9.8. Giải thích case chuỗi @DependsOn

Quan hệ:

```text
service depends on repository
repository depends on cache
```

làm các callbacks chạy theo chuỗi:

```text
cache.new -> cache.init
repository.new -> repository.init
service.new -> service.init
```

Mặc dù `@Bean("service")` được **viết trước** `repository` và `cache` trong source, bean graph quyết định prerequisite ordering.

Khi đóng context:

```text
service.destroy -> repository.destroy -> cache.destroy
```

Đây là **reverse dependency order**, không phải ngẫu nhiên đảo ngược thứ tự source code.

`chain.relations=true,true` xác nhận registry có quan hệ `repository` phụ thuộc `cache` và `service` phụ thuộc `repository`. Hai APIs chỉ đọc metadata relations, không tạo một global shutdown list.

### 9.9. Giải thích case XML

`xmlWorker` được viết trước `xmlStore` nhưng có `depends-on="xmlStore"`. Spring khởi tạo store trước worker. Khi close:

```text
xmlWorker.destroy
xmlStore.destroy
```

Đây là một ví dụ cho thấy **thứ tự dòng trong XML không thay thế dependency declaration**.

### 9.10. Giải thích case DI

`DiConfig.diClient(Tracked diStore)` cần một bean `Tracked`. Trong context `DiConfig` chỉ có một bean type `Tracked` là `diStore`; Spring resolve tham số và ghi nhận quan hệ dependency.

Vì vậy `di.relation=true` từ `getDependentBeans("diStore")` chứng minh BeanFactory đã ghi nhận `diClient` là dependent của `diStore`. Khi close, `diClient.destroy` chạy trước `diStore.destroy`.

Đây là quan hệ do **DI resolution** sinh ra, không cần đặt `@DependsOn("diStore")` trùng lặp. Không mở rộng kết luận này sang mọi cách tự lấy object ngoài container.

### 9.11. Giải thích case lazy

`lazyStore` có `@Lazy` nhưng `lazyWorker` là singleton eager có `@DependsOn("lazyStore")`. Khi BeanFactory tạo lazyWorker trong refresh, nó buộc tạo lazyStore trước; vì vậy `lazy.init` hiển thị cả hai beans đã được khởi tạo trước khi main in.

Khi close, dependent `lazyWorker` bị hủy trước prerequisite `lazyStore`. **@Lazy không làm vô hiệu destruction relation**.

### 9.12. Giải thích case prototype

`PrototypeConfig` khai báo `Tracked` scope prototype. `context.getBean("prototype")` tạo instance và gọi init callback; tuy nhiên Spring **không tự đăng ký destroy callback cho prototype instances như với managed singleton cache**.

Bởi vậy, sau `context.close()`, `prototype.destroy-after-close=false`. Nếu prototype nắm resource cần cleanup, code chủ sở hữu object hoặc cơ chế custom thích hợp phải giải phóng; không đặt kỳ vọng vào singleton destruction order.

### 9.13. Vì sao ví dụ dùng một event list dùng chung?

Năm contexts chạy **tuần tự**, mỗi case gọi `EVENTS.clear()` trước khi tạo context, nên sự kiện được tách biệt. Các beans trong ví dụ sử dụng synchronized-free constructors/init/destruction đồng bộ; không cấu hình background bootstrap hoặc custom threads.

Trong ứng dụng thật, có thể có concurrent startup/background init; khi đó recorder để test cần thread-safe collections/appropriate synchronization và không được đòi hỏi thứ tự của những unrelated beans.

## 10. Tương tác với @Lazy và lazy-init

Một singleton lazy chưa từng được tạo **không có instance để destroy**. Nếu context chỉ đăng ký bean definition:

```java
@Bean
@Lazy
OptionalClient optionalClient() {
    return new OptionalClient();
}
```

mà không ai lookup/inject bắt buộc bean đó, callback cleanup của target có thể không chạy vì chưa có target instance nào tồn tại.

Nhưng:

```java
@Bean
@DependsOn("optionalClient")
CriticalWorker criticalWorker() {
    return new CriticalWorker();
}
```

khi criticalWorker là eager singleton, dependency relation sẽ khiến optionalClient được tạo sớm dù đánh dấu lazy. Khi các singleton này được destroy, criticalWorker bị destroy trước optionalClient.

**Đừng thiết kế cleanup theo giả định tất cả `@Lazy` beans chắc chắn đã được instantiate**. Nếu cần một bean chạy init/destroy không điều kiện trong lifecycle, nó thường không nên là lazy, hoặc cần có cơ chế khởi tạo/warm-up rõ.

## 11. Singleton và prototype có cùng quy tắc hủy không?

Không. `singleton` và `prototype` có lifecycle management khác nhau.

| Trường hợp | Tạo instance | Tự destroy khi ApplicationContext.close()? |
| --- | --- | --- |
| Eager singleton đã tạo | Khi refresh/được yêu cầu | Có, nếu destruction callback thích hợp được đăng ký |
| Lazy singleton chưa tạo | Chưa có instance | Không có target để destroy |
| Lazy singleton đã tạo | Khi được yêu cầu | Có, nếu callback thích hợp |
| Prototype được lookup | Mỗi request tạo instance mới | Container mặc định **không tự gọi configured destruction callbacks** cho prototype |
| Web request/session scope | Theo active scope | Theo semantics và callback management của scope implementation |
| Manually `new` object | Java code tạo | Spring không tự quản lý |
| Inner bean | Khi tạo enclosing bean theo semantics tương ứng | Lifecycle/containment có quy tắc riêng, không đồng nhất với top-level singleton |

### 11.1. Prototype cần explicit cleanup

Nếu prototype implement `AutoCloseable`, caller lấy nó bằng `getBean()` có thể giữ trách nhiệm gọi `close()`. Hoặc ứng dụng dùng custom scope/post-processor để đăng ký destruction theo contract. `ApplicationContext.close()` không phải bộ gom rác tự đóng mọi prototype instances đã từng tạo.

### 11.2. Singleton inject prototype

Một singleton consumer được inject một prototype dependency **một lần lúc consumer được dựng** thường giữ nguyên instance prototype ấy. BeanFactory không tự thay prototype theo mỗi business call; nếu cần object mới cho mỗi call, dùng `ObjectProvider<T>` hoặc phương thức lookup phù hợp. Việc destroy singleton cũng không tự giải quyết tài nguyên của mọi prototype như thể tất cả đều là top-level singleton resources.

### 11.3. Inner bean không đơn giản là prototype

Một `<bean>` nằm trong XML `<constructor-arg>`/`<property>` là inner bean, gắn với enclosing bean. Spring còn có cơ chế **containment relationships** ở singleton registry; đừng nhầm nó với top-level prototype có tên public. Khi inner bean nắm resource cần destroy, cần xem metadata/lifecycle cụ thể và test ownership thực tế.

## 12. Shutdown callbacks và thứ tự của chính một bean

Việc BeanFactory chọn **bean nào được hủy trước** khác với việc Spring chạy **callback nào bên trong bean ấy**.

Ví dụ bean có:

```java
@PreDestroy
void preDestroy() {
}

@Override
public void destroy() {
}

public void close() {
}
```

Snippet này chỉ minh họa các kiểu callback: nó cần import `jakarta.annotation.PreDestroy`, class implement `DisposableBean` và metadata custom `close` mới có đầy đủ semantics.

Một context có thể gọi lifecycle callbacks theo quy tắc xử lý của Spring; không nên viết application logic dựa vào một callback “tình cờ” có mặt khi context thiếu processor/metadata tương ứng.

### 12.1. Không gọi destroy method bằng tay khi container đã quản lý

Nếu lấy singleton từ Spring và tự gọi `destroy()` giữa lúc ứng dụng chạy, Spring vẫn có thể coi bean đang sống trong container, và sau đó gọi destroy callback lúc close. Điều này có thể gây **double-close** hoặc làm những consumers khác dùng resource đã đóng.

Nếu ứng dụng cần chủ động stop/start nhiều lần, hãy thiết kế lifecycle interface/service có contract phù hợp; không coi `DisposableBean.destroy()` là business method để tùy tiện gọi giữa runtime.

### 12.2. Idempotent cleanup

Dù Spring cố điều phối destruction, mã cleanup tài nguyên quan trọng vẫn nên được thiết kế **idempotent** nếu có thể: gọi close hai lần không làm thất thoát/ghi đè dữ liệu hoặc ném lỗi khó phục hồi. Với những thư viện không idempotent, cần một wrapper quản lý trạng thái dừng.

## 13. registerDependentBean: API cho hạ tầng nâng cao

Ngoài metadata `@DependsOn` và XML, `ConfigurableBeanFactory`/`DefaultSingletonBeanRegistry` có API đăng ký quan hệ động:

```java
factory.registerDependentBean("store", "writer");
```

Ý nghĩa **writer depends on store**, nên phải destroy writer trước store.

Đây là API dành cho code hạ tầng/container integration, không phải gợi ý mặc định cho mọi service. Gọi tùy tiện có thể gây side effects khó chẩn đoán; nên khai báo relation sớm, trước quá trình destroy và phù hợp với graph thực tế.

### 13.1. registerDependentBean không inject object

Đăng ký relation không làm:

```java
writer.store = store;
```

hoặc tự gọi một setter. Đây là metadata cho dependency graph và cleanup ordering. Nếu object thực sự cần được truyền vào consumer, vẫn phải dùng DI hoặc lookup có chủ đích.

### 13.2. Relation direction phải chính xác

```java
factory.registerDependentBean("store", "writer");
```

đúng cho relation **writer → store**.

Đảo lại:

```java
factory.registerDependentBean("writer", "store");
```

mô tả **store → writer**, làm đảo shutdown order kỳ vọng. Trong code review hãy viết rõ chú thích “dependent = X, prerequisite = Y”.

### 13.3. Vòng quan hệ

Không tạo cycle như:

```text
A depends on B
B depends on A
```

Framework có cơ chế phát hiện nhiều circular dependency/depends-on relationships và có thể lỗi khi tạo beans. Nếu dùng API registry mức thấp, không dựa vào cách framework có thể tự đoán rồi sửa graph. **Vòng đời không thể có một thứ tự trước/sau hợp lệ cho cycle.**

### 13.4. Factory.getDependenciesForBean là công cụ chẩn đoán

```java
String[] deps = factory.getDependenciesForBean("writer");
```

Nếu mảng chứa `store`, container biết relation này. Không đồng nghĩa writer có Java field tên store, và cũng không mô tả toàn bộ call graph của chương trình.

## 14. @Order không điều khiển thứ tự hủy singleton

`@Order`, `Ordered`, `@Priority` hoặc danh sách beans có thứ tự phục vụ **những extension points cụ thể**; chúng không phải general-purpose shutdown ordering metadata.

Ví dụ:

```java
@Order(1)
@Component
class FirstHandler {
}

@Order(2)
@Component
class SecondHandler {
}
```

Không thể suy luận Spring sẽ destroy `SecondHandler` trước `FirstHandler` chỉ vì giá trị `@Order`. Nếu hai handlers không có dependency relation, không nên xây logic cleanup dựa trên thứ tự in logs tình cờ của một version.

Để điều phối lifecycle khi chúng có dependency thật, dùng **DI** hoặc **`@DependsOn`**. Với một nhóm components cần startup/shutdown phase orchestration, xem `SmartLifecycle` thay vì ép một chuỗi `@Order`.

## 15. @DependsOn so với SmartLifecycle

`@DependsOn` và `SmartLifecycle` giải quyết hai lớp vấn đề:

| Cơ chế | Trách nhiệm |
| --- | --- |
| `@DependsOn` | Bean prerequisite **initialized before** dependent; destroy dependent before prerequisite cho singleton |
| XML `depends-on` | Cùng ý nghĩa ở XML bean definition |
| Constructor injection | Cung cấp object dependency và bảo đảm resolution phù hợp |
| `SmartLifecycle` | Quản lý **start/stop phases** của components có lifecycle chạy/dừng |
| `@Order` | Ordering/precedence của các extension points phù hợp, không phải bean shutdown graph |
| `ApplicationContext.close()` | Kích hoạt context shutdown và bean destruction |
| `@PreDestroy`/`DisposableBean` | Callback cleanup của **một bean instance** |
| `ObjectProvider<T>` | Dynamic dependency lookup, có thể trì hoãn resolution |
| Kubernetes preStop/SIGTERM | Điều phối bên ngoài JVM, không trực tiếp thay bean dependency graph |

### 15.1. Khi cần stop trước nhưng không destroy ngay

Một web server, message listener và queue consumer thường cần **ngừng nhận công việc mới**, **drain công việc đang chạy**, rồi mới **đóng database/pools**. `SmartLifecycle` cung cấp mô hình phase start/stop và callbacks phù hợp hơn cho **coordinated stop**.

`@DependsOn` giúp quản lý bean construction/destruction order, nhưng không thay thế logic **drain**, **timeout** hay ngừng worker threads.

### 15.2. Shutdown của application phức tạp có nhiều giai đoạn

```text
1. Ngừng nhận traffic/message mới
2. Đợi in-flight work hoàn tất trong timeout
3. Flush writers/buffers
4. Đóng các client/pools phụ thuộc
5. Giải phóng infrastructure còn lại
```

Trong một Spring application, có thể cần kết hợp:
- lifecycle stop phases;
- registered depends-on relationships;
- bean destroy callbacks;
- timeout/retry/error handling cho external resources.

Không nên nghĩ chỉ khai báo depends-on names là hoàn thành mọi yêu cầu graceful shutdown của một hệ phân tán.

## 16. Thứ tự hủy có áp dụng giữa hai ApplicationContexts không?

Không như một **global singleton registry**.

Ví dụ:

```java
var parent = new AnnotationConfigApplicationContext(ParentConfig.class);
var child = new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
```

Đây là snippet minh họa, cần `ParentConfig` và `ChildConfig` thật. Root/child contexts có lifecycle và BeanFactories khác nhau. Một bean của child có thể lookup bean của parent theo hierarchy, nhưng không tự suy ra một shutdown order toàn JVM khi ai đó đóng parent trước child.

Nếu một child consumer còn cần parent resource lúc destroy, ứng dụng nên đóng **child trước parent** và có lifecycle ownership rõ ràng. Với contexts độc lập hoàn toàn, một relation theo tên bean trong context A không tạo dependency relation trong registry của context B.

**Không gắn @DependsOn để điều phối shutdown giữa hai microservices hoặc nhiều JVM.** Đó là chuyện orchestration/distributed protocols.

## 17. Phụ thuộc vào @Lazy singleton

Một bean `@Lazy` có bean definition nhưng chưa được tạo. Nếu không bao giờ được yêu cầu, sẽ không có target instance để gọi destroy callback.

```java
@Bean("optionalIndex")
@Lazy
IndexClient optionalIndex() {
    return new IndexClient();
}
```

Nếu không có consumer hoặc getBean nào yêu cầu optionalIndex, context có thể đóng mà không bao giờ tạo `IndexClient`.

Ngược lại:

```java
@Bean("activeSearch")
@DependsOn("optionalIndex")
SearchService activeSearch() {
    return new SearchService();
}
```

Nếu `activeSearch` là eager singleton, BeanFactory sẽ tạo `optionalIndex` trước activeSearch để thỏa depends-on. Khi đóng context, activeSearch phải destroy trước optionalIndex.

### 17.1. Lazy không có nghĩa destroy trước/sau tùy ý

Lazy chỉ ảnh hưởng **thời điểm instantiation**. Khi bean đã được tạo và relation được đăng ký, destruction order vẫn do container graph quyết định. Không dùng `@Lazy` như công cụ “giữ tài nguyên lâu hơn khi shutdown”.

### 17.2. @Lazy trên injection point

`@Lazy` đặt ở injection point có thể tạo lazy-resolution proxy thay vì resolve target ngay. Nếu target được resolve muộn, thời điểm **registered dependency relationship** và destruction semantics có thể khác với direct eager injection; phải kiểm tra loại proxy/scope và quan hệ đang được container ghi nhận. Khi resource cần cleanup ordering chắc chắn, không nên dựa vào việc proxy tình cờ gọi target trước hay sau.

## 18. FactoryBean, aliases và tên bean khi shutdown

### 18.1. Alias là tên tham chiếu, không phải bean instance riêng

```xml
<bean id="primaryStore" class="com.example.Store"/>
<alias name="primaryStore" alias="reportStore"/>
```

`primaryStore` và `reportStore` thường quy về **một bean definition**. Singleton identity và destroy callback gắn với target bean; không có một callback destroy độc lập chỉ vì thêm alias.

### 18.2. FactoryBean có factory và product

Một `FactoryBean<T>` sinh ra product bean, và `&factoryName` thường được dùng để lookup chính factory instance. Đây không phải một alias bình thường. Nếu product/factory nắm tài nguyên khác nhau, hãy kiểm tra bean definition, scope và lifecycle callbacks thay vì chỉ nhìn tên từ `getBean()`.

### 18.3. Synthetic/infrastructure beans

Một số bean definitions do framework tự đăng ký có lifecycle sớm/đặc biệt. `@DependsOn` trên business service không nên được xem là một cơ chế thay đổi toàn bộ processor registration pipeline. Để quản lý framework extension, hãy dùng extension point đã được công bố và kiểm thử startup/shutdown.

## 19. Thực hành chẩn đoán bằng BeanFactory API

Từ `AnnotationConfigApplicationContext` trong ví dụ:

```java
var factory = context.getBeanFactory();

System.out.println(
    Arrays.toString(factory.getDependentBeans("cache")));

System.out.println(
    Arrays.toString(factory.getDependenciesForBean("repository")));
```

Trong `ChainConfig`, điều quan trọng là:

- `getDependentBeans("cache")` chứa `repository`.
- `getDependenciesForBean("repository")` chứa `cache`.

Không nên hard-code một exact string của mảng nếu context có thêm dependent registrations hoặc thứ tự phần tử khác. Dùng:

```java
boolean relationExists = Arrays.asList(
    factory.getDependentBeans("cache")
).contains("repository");
```

### 19.1. Kiểm tra BeanDefinition metadata

```java
var definition =
    factory.getBeanDefinition("service");

String[] explicitNames = definition.getDependsOn();
```

Với ChainConfig, explicitNames bao gồm `repository`. Nhưng `getDependsOn()` là **explicit metadata** từ bean definition; nếu null/rỗng không có nghĩa bean hoàn toàn không có injection dependencies. Muốn xem registered runtime relationships, dùng `getDependenciesForBean`/`getDependentBeans`.

### 19.2. Kiểm tra singleton đã được tạo

```java
boolean created = factory.containsSingleton("cache");
```

Có thể dùng khi debug `@Lazy`: bean definition có thể tồn tại nhưng instance chưa được tạo. Không gọi `getBean("cache")` chỉ để “kiểm tra chưa tạo”, vì lời gọi ấy có thể **kích hoạt tạo bean**.

### 19.3. Đừng quên đóng context trong test

Nếu bạn chỉ tạo `AnnotationConfigApplicationContext` rồi bỏ reference mà không đóng context, không nên trông chờ Spring tự chạy destroy callbacks ngay. Dùng `try-with-resources` hoặc `context.close()` trong test để kích hoạt lifecycle như ví dụ.

## 20. Các lỗi và hiểu lầm phổ biến

### Lỗi 1 — Đọc sai chiều depends-on

`@DependsOn("store")` trên bean writer nghĩa là **writer phụ thuộc store**, không phải store phụ thuộc writer. Init: store trước writer. Destroy: writer trước store.

### Lỗi 2 — Dùng @Order để ép destroy thứ tự

`@Order` không tạo bean dependency relationship. Nó dành cho ordering ở các extension points được hỗ trợ.

### Lỗi 3 — Dựa vào thứ tự khai báo @Bean/XML

Source order không phải hợp đồng lifecycle. Nếu ordering thật sự quan trọng, khai báo DI hoặc depends-on.

### Lỗi 4 — Chỉ nhìn Java fields và đoán container biết dependency

Nếu field được đặt bằng code ngoài Spring hoặc qua static holder, BeanFactory có thể không có registered relation. Đọc dependency graph của container.

### Lỗi 5 — Thêm @DependsOn dù DI đã đủ rõ

Nếu constructor đã inject dependency, `@DependsOn` cùng tên thường thừa. Chỉ thêm khi có side effect prerequisite riêng.

### Lỗi 6 — Đảo tham số registerDependentBean

`registerDependentBean("store","writer")` ghi **writer depends on store**. Đảo tham số sẽ đảo relation.

### Lỗi 7 — Class-level @DependsOn trên bean đăng ký bằng XML

Javadoc nói class-level annotation cần component scanning để áp dụng. Với bean thuần XML, dùng `depends-on` attribute.

### Lỗi 8 — Depends-on trỏ tới bean không tồn tại

Nếu name sai, profile/condition loại target hoặc resource chưa nạp, creation của dependent có thể thất bại. `@DependsOn` không tự khám phá Java class theo type.

### Lỗi 9 — Cycle A phụ thuộc B, B phụ thuộc A

Đây là ordering constraint không thể thỏa. Đổi thứ tự khai báo không sửa được; phải thay thiết kế dependency graph.

### Lỗi 10 — Nghĩ @Lazy sẽ ngăn mọi initialization

Một eager singleton có `@DependsOn` đến lazy bean vẫn có thể buộc lazy bean tạo trong refresh.

### Lỗi 11 — Trông chờ destroy của prototype khi close

Prototype được Spring tạo nhưng container mặc định không theo dõi destruction callback như singleton. Caller phải quản lý tài nguyên.

### Lỗi 12 — Context không được close

Không đóng context thì không có đảm bảo shutdown callbacks sẽ chạy ở thời điểm mong muốn. Khi chạy application, thiết lập shutdown/close handling phù hợp.

### Lỗi 13 — Chỉ có @PreDestroy nhưng thiếu processor/API

Với Spring 6+, cần đúng Jakarta annotation dependencies và annotation processing. Nếu callback không được đăng ký, ordering graph không giúp code shutdown chạy.

### Lỗi 14 — Đóng resource bằng tay trước khi Spring destroy consumers

Một pool do singleton quản lý nhưng bị business code gọi `close()` sớm khiến các consumers vẫn tồn tại mà resource đã đóng. Chọn một lifecycle owner.

### Lỗi 15 — Tin rằng destroy thứ tự giải quyết async readiness

`@DependsOn` không đợi mọi background task hoàn thành. Bean init callback return có thể xảy ra trước task. Khi stop, cần drain/await với timeout.

### Lỗi 16 — Tin rằng shutdown của hai contexts là tự động đồng bộ

Root/child contexts không phải một bean factory duy nhất. Đóng các contexts theo ownership chính xác, thường child trước parent khi child phụ thuộc parent.

### Lỗi 17 — Kiểm thử string order của unrelated beans

Nếu hai beans không có registered dependency edge, không có lý do bắt buộc chúng destroy theo một thứ tự trong test. Chỉ assert relation có hợp đồng.

### Lỗi 18 — Dùng static/global registries vô điều kiện

`@DependsOn` có thể vá order cho legacy, nhưng object graph type-safe qua DI thường rõ nghĩa và dễ test hơn.

### Lỗi 19 — Coi @PreDestroy là graceful-shutdown orchestration đầy đủ

Callback chỉ là bước cleanup của một object. Với message listener, HTTP server, thread pool, cần stop/drain/lifecycle strategy trước khi đóng resources quan trọng.

### Lỗi 20 — Giữ tài nguyên trong unmanaged thread

Spring có thể destroy beans đúng thứ tự nhưng một thread khác vẫn truy cập singleton đã đóng. Thread ownership và shutdown coordination phải được thiết kế riêng.

### Lỗi 21 — Suy luận callback luôn chạy trong shutdown bất thường

Nếu JVM bị `kill -9`, process crash hoặc host dừng đột ngột, callback Spring có thể không có cơ hội chạy. Critical persistence cần transaction/durable queues/flush guarantees phù hợp, không dựa duy nhất vào shutdown hook.

### Lỗi 22 — Không phân biệt lỗi callback và lỗi graph

Nếu dependent.destroy() ném exception, Spring có thể vẫn cố cleanup các beans khác theo contract của container; không nên đặt kỳ vọng một exception làm mọi cleanup dừng hoàn toàn. Log và xử lý lỗi cleanup cẩn thận, tránh che nguyên nhân trước đó.

## 21. Thiết kế graceful shutdown cho service thực tế

Một ứng dụng nhận messages và lưu database thường có:

```text
MessageListener --> JobExecutor --> TransactionalWriter --> DataSource
```

Những bước mong muốn:

1. Ngừng nhận message mới để không phát sinh thêm công việc.
2. Đợi các job đang chạy kết thúc hoặc cancel theo timeout.
3. Flush buffered writes.
4. Hủy writer/adapter đã ngừng sử dụng DataSource.
5. Đóng DataSource/pool sau cùng.

Bean destruction order theo DI/depends-on có thể hỗ trợ một số bước **4 → 5**. Nhưng các bước **1 → 3** cần lifecycle API hoặc application-specific draining policy.

### 21.1. Dùng SmartLifecycle khi phù hợp

`SmartLifecycle` hỗ trợ phases cho lifecycle start/stop của components. Nếu một listener cần dừng trước khi pool bị destroy, có thể đưa listener vào lifecycle orchestration thay vì dựa toàn bộ vào `destroy()`.

Lưu ý **stop phase order không tự động bằng bean destruction order**. Mỗi cơ chế có contract và vị trí trong quá trình close riêng. Hãy test cả việc dừng tiếp nhận công việc và thứ tự đóng tài nguyên.

### 21.2. Bảo đảm destroy idempotent

`destroy()` nên có khả năng chịu được trạng thái đã đóng/dừng nếu thư viện cho phép, và cố gắng không để một failure ở cleanup làm mất thông tin lỗi gốc. Sử dụng logging rõ ràng, resource ownership duy nhất, timeout hữu hạn.

### 21.3. External resources không phải Spring beans

Một broker hoặc database server chạy trên máy khác sẽ không được Spring shutdown theo bean graph trong JVM. Spring chỉ đóng **client/session/pool wrappers** mà application đang sở hữu; external service availability phải được quản lý ở lớp khác.

## 22. Mẫu kiểm thử xác nhận thứ tự

### 22.1. Assert relation thay vì source order

Nếu bean B depends on A, có thể kiểm tra:

```java
int aInit = events.indexOf("A.init");
int bInit = events.indexOf("B.init");

if (!(aInit >= 0 && bInit > aInit)) {
    throw new AssertionError("B initialized before A");
}
```

Đây là snippet minh họa; `events` là một `List<String>` ghi callback events. Với singleton chain, có thể kiểm tra tương tự:

```java
int bDestroy = events.indexOf("B.destroy");
int aDestroy = events.indexOf("A.destroy");

if (!(bDestroy >= 0 && aDestroy > bDestroy)) {
    throw new AssertionError("A destroyed before B");
}
```

### 22.2. Test cần kích hoạt context.close()

Để test destruction callback, buộc context close thực tế:

```java
try (var context =
         new AnnotationConfigApplicationContext(TestConfig.class)) {
    // assert initialization / relations
}
// assert destruction events SAU khi close
```

`TestConfig` là class test minh họa, cần implementation thật. Đừng assert destroy events **trước** khi đóng context.

### 22.3. Test startup graph và shutdown graph

Đừng chỉ assert `A.init < B.init`. Hãy kiểm tra thêm `B.destroy < A.destroy` nếu yêu cầu cleanup order quan trọng. Một configuration có thể khởi tạo đúng do tình cờ nhưng thiếu registered relation nên teardown không có guarantee phù hợp.

### 22.4. Integration test khi dependency được scan

Nếu dùng `@DependsOn` trên class `@Component`, test thực tế với component scan. Nếu đổi sang XML `<bean>`, test lại bằng XML `depends-on` để tránh annotation class-level bị bỏ qua.

### 22.5. Prototype test

Tạo prototype, xác nhận constructor/init chạy; close context rồi xác nhận destroy chưa chạy tự động. Nếu prototype nắm tài nguyên, test riêng cleanup do owner thực hiện.

## 23. Câu hỏi ôn tập có đáp án

**Câu 1.** Với A depends on B, bean nào khởi tạo trước?

**Đáp:** B trước A.

**Câu 2.** Với hai singleton A depends on B, bean nào được hủy trước?

**Đáp:** A trước B.

**Câu 3.** `@DependsOn("store")` có tự inject store vào field của bean không?

**Đáp:** Không. Nó khai báo initialization/destruction relationship theo tên, không cấp object reference.

**Câu 4.** Constructor injection có thể ảnh hưởng shutdown order không?

**Đáp:** Có. Với các dependency được BeanFactory resolve và đăng ký quan hệ, container có thể dùng relation đó để hủy dependent trước dependency.

**Câu 5.** `getDependentBeans("store")` khác `getDependenciesForBean("store")` thế nào?

**Đáp:** Cái đầu liệt kê beans phụ thuộc store; cái sau liệt kê beans mà store phụ thuộc.

**Câu 6.** Vì sao `@Order(1)` không đủ để quản lý shutdown?

**Đáp:** `@Order` không tạo explicit bean dependency relationship cho destruction registry.

**Câu 7.** XML `depends-on="cache,metrics"` có yêu cầu cache hủy trước metrics không?

**Đáp:** Không theo nghĩa hai prerequisites phải có total order với nhau; nó buộc bean dependent hủy trước từng prerequisite.

**Câu 8.** Bean có `@Lazy` nhưng chưa bao giờ tạo có chạy destroy không?

**Đáp:** Không có instance để destroy.

**Câu 9.** Singleton eager A depends on lazy singleton B, B có thể bị tạo trong startup không?

**Đáp:** Có. A buộc phải đợi B khởi tạo.

**Câu 10.** Prototype bean implement `DisposableBean` sẽ tự destroy khi context close không?

**Đáp:** Mặc định không. Client/cơ chế quản lý phù hợp phải cleanup prototype instances.

**Câu 11.** `registerDependentBean("pool","worker")` biểu thị chiều nào?

**Đáp:** Worker depends on pool; destroy worker trước pool.

**Câu 12.** `getDependenciesForBean("worker")` có thể trả null vì thiếu depends-on?

**Đáp:** API trả mảng tên hoặc mảng rỗng khi không có registered dependencies; `BeanDefinition.getDependsOn()` mới có thể trả null khi không khai báo explicit depends-on metadata.

**Câu 13.** @PreDestroy và @DependsOn có cùng tác dụng không?

**Đáp:** Không. `@PreDestroy` là callback cleanup của một bean; `@DependsOn` xác định thứ tự giữa các beans.

**Câu 14.** Có thể dựa vào thứ tự khai báo bean trong Java file không?

**Đáp:** Không để bảo đảm lifecycle ordering. Cần DI hoặc explicit depends-on.

**Câu 15.** Tại sao một static global registry khó an toàn khi shutdown?

**Đáp:** Container có thể không thấy quan hệ giữa consumers với registry. Prefer DI hoặc declare explicit dependency relationship khi phù hợp.

**Câu 16.** Nếu background task chưa kết thúc khi initializer return, @DependsOn có tự chờ không?

**Đáp:** Không. Nó chờ bean initialization theo Spring lifecycle, không chờ mọi tác vụ bên ngoài.

**Câu 17.** Spring có tự điều khiển thứ tự hủy beans ở hai independent ApplicationContexts không?

**Đáp:** Không. Cần quản lý close order giữa contexts riêng.

**Câu 18.** Khi nào cần SmartLifecycle hơn chỉ destroy callbacks?

**Đáp:** Khi cần có giai đoạn start/stop/drain, phase ordering và coordinated shutdown trước khi singleton resources bị hủy.

## 24. Checklist ứng dụng

- [ ] Vẽ graph “A dùng B” cho các singleton quan trọng.
- [ ] Dùng constructor injection khi B là object dependency thật sự của A.
- [ ] Dùng `@DependsOn`/`depends-on` cho side effect/init prerequisite không biểu diễn qua DI.
- [ ] Kiểm tra đúng chiều: dependent mang annotation, prerequisite nằm trong list names.
- [ ] Không tạo cycle trong graph.
- [ ] Đặt tên bean ổn định; xem xét aliases/profiles/conditions.
- [ ] Xác nhận bean thực sự được container quản lý, không phải manually `new`.
- [ ] Kiểm tra các beans cleanup có `@PreDestroy`/`DisposableBean`/destroyMethod hợp lệ.
- [ ] Test cả initialization **và** destruction relation.
- [ ] Không assert order cho unrelated beans.
- [ ] Kiểm tra prototype/lazy/custom scopes khác singleton.
- [ ] Không bỏ qua parent-child contexts và resource ownership.
- [ ] Dùng `getDependentBeans`/`getDependenciesForBean` để debug graph thực tế.
- [ ] Với task async/web consumers, thiết kế stop/drain và timeouts bên cạnh destroy callback.
- [ ] Đừng dựa vào shutdown callback để thay thế data durability trên process crash.
- [ ] Quan sát logs, resource leaks và warnings khi deploy/stop thực tế.

## 25. Tài liệu chính thức

- [Spring Framework 6.2 — Using depends-on](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-dependson.html): bảo đảm initialization và destruction ngược chiều của singleton.
- [Spring Framework 6.2.19 — DependsOn Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/DependsOn.html): định nghĩa, TYPE/METHOD targets, giới hạn class-level khi đăng ký XML.
- [Spring Framework 6.2.19 — DefaultSingletonBeanRegistry Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/support/DefaultSingletonBeanRegistry.html): `registerDependentBean`, `getDependentBeans`, `getDependenciesForBean`, `destroySingletons`.
- [Spring Framework 6.2.19 — ConfigurableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/ConfigurableBeanFactory.html): xem registered bean dependencies và quản lý destruction.
- [Spring Framework — Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): init/destroy methods, `InitializingBean`, `DisposableBean` và annotation callbacks.
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton, prototype và giới hạn destruction của prototype.
- [Spring Framework — ApplicationContext Shutdown](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): lifecycle khi đóng container.
- [Spring Framework — SmartLifecycle](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/SmartLifecycle.html): phase-based startup/shutdown orchestration.

**Kết luận:** Spring không chỉ tạo beans theo dependency graph; với những managed singletons có quan hệ phụ thuộc được container nhận biết, nó còn **hủy dependent trước dependency** để tài nguyên prerequisite còn sống trong lúc dependent cleanup. `@DependsOn`/XML `depends-on` là cách khai báo explicit cho quan hệ không biểu diễn bằng DI; `getDependentBeans` và `getDependenciesForBean` giúp kiểm chứng graph. Đừng nhầm thứ tự hủy singleton với prototype destruction, thứ tự `@Order`, hay graceful shutdown của threads và external services. Hãy để **DI biểu diễn object dependency, depends-on biểu diễn lifecycle constraint và lifecycle APIs xử lý stop/drain**.


</details>

- [Quay lại đầu trang](#back-to-top)
