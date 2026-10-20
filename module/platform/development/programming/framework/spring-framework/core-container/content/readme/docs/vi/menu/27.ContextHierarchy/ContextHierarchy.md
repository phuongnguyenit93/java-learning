<a id="back-to-top"></a>

# Phân cấp context

## Menu
- [Quan hệ context cha và context con](#context-hierarchy-001)
- [Quy tắc con nhìn thấy cha và cha không nhìn thấy con](#context-hierarchy-002)
- [Tên bean ở context con che khuất bean của cha](#context-hierarchy-003)
- [Tra cứu cục bộ so với tra cứu có xét tổ tiên](#context-hierarchy-004)
- [Post-processor hoạt động trong context nơi được đăng ký](#context-hierarchy-005)
- [Lan truyền sự kiện từ con lên cha và tránh xử lý lặp](#context-hierarchy-006)
- [Kế thừa MessageSource và phối hợp Environment](#context-hierarchy-007)
- [Phân biệt phân cấp context với kế thừa định nghĩa bean](#context-hierarchy-008)

## <a id="context-hierarchy-001">Quan hệ context cha và context con</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ context cha và context con trong Spring Framework Core Container

## 1. Vì sao Spring có phân cấp ApplicationContext?

Trong một ứng dụng Java lớn, không phải tất cả bean đều cần cùng một phạm vi cấu hình và lifecycle. Một số bean thuộc hạ tầng dùng chung như `DataSource`, `Clock`, cấu hình bảo mật hoặc business services; các bean khác lại chỉ phục vụ một module, servlet, batch job hay một giao diện cụ thể.

Spring Framework cho phép tạo **cây ApplicationContext**:

~~~text
                   Root / Parent ApplicationContext
                   ├── SharedService
                   ├── DataSource
                   ├── MessageSource
                   └── shared infrastructure
                         ▲
            ┌────────────┴──────────────┐
            │                           │
 Child A ApplicationContext    Child B ApplicationContext
 ├── Controller A              ├── Controller B
 ├── Feature A beans           ├── Feature B beans
 └── local config              └── local config
~~~

Quan hệ **cha–con** chủ yếu tạo cơ chế **tra cứu bean một chiều có fallback**: context con có thể tham chiếu bean của context cha khi bản thân con không cung cấp bean phù hợp. Context cha không tự nhìn thấy bean được định nghĩa riêng ở context con; hai context con cùng cha cũng không tự nhìn thấy bean riêng của nhau.

Bài này giải thích chính xác mối quan hệ đó cùng những hệ quả quan trọng về **trùng tên bean, dependency injection, type lookup, Environment, message source, event propagation, bean post-processors, lifecycle và kiểm thử**.

### Mục tiêu sau khi học

Bạn có thể:

1. Tạo parent và child contexts bằng `AnnotationConfigApplicationContext`/`setParent` trước `refresh()`.
2. Phân biệt `getBean(name)`, `containsLocalBean(name)`, `containsBean(name)`, `getBeansOfType(type)` và `BeanFactoryUtils.beansOfTypeIncludingAncestors(...)`.
3. Dự đoán bean nào thắng khi parent và child có **cùng tên**.
4. Inject một parent service vào child bean mà không đưa toàn bộ parent config vào child.
5. Hiểu bean lifecycle và container extension points không được tự trộn giữa hai contexts.
6. Dự đoán chiều lan truyền event **từ con lên cha**, không ngược lại.
7. Hiểu `Environment.merge(...)` và MessageSource fallback trong hierarchy.
8. Thiết kế root–child architecture không tạo coupling vòng hoặc dựa vào truy cập giữa siblings.
9. Chạy một ví dụ **Java 17 / Spring 6.2.x** với root, Child A, Child B, assertion và stdout xác định.

## 2. ApplicationContext là một container; hierarchy nối nhiều container riêng biệt

`ApplicationContext` mở rộng nhiều API, bao gồm `ListableBeanFactory`, `HierarchicalBeanFactory`, `MessageSource`, `ApplicationEventPublisher` và `ResourcePatternResolver`. `ConfigurableApplicationContext.setParent(ApplicationContext)` cho phép chỉ định parent trước khi refresh. Một số implementation, như `GenericApplicationContext`, còn liên kết bean factory của child với parent bean factory khi thiết lập parent.

Điều này **không** khiến ba contexts biến thành một BeanFactory toàn cục. Mỗi context có:

- Những BeanDefinitions và singleton cache do chính nó quản lý.
- Tập `BeanPostProcessor` và `BeanFactoryPostProcessor` áp dụng cho bean tại context đó.
- Trạng thái refresh/close riêng.
- Event multicaster và listeners riêng.
- MessageSource có thể fallback theo hierarchy.
- Environment có thể nhận các property sources/profile information được merge từ parent theo implementation.

Ví dụ quan hệ:

~~~text
ROOT [R]
 ├─ bean "sharedService" = S
 ├─ bean "label" = "ROOT"
 │
 ├─ CHILD A [A]
 │    ├─ bean "label" = "A"
 │    └─ bean "onlyA"
 │
 └─ CHILD B [B]
      └─ bean "onlyB"
~~~

Kết quả tra cứu theo tên trong ví dụ:

| Điểm tra cứu | `"sharedService"` | `"label"` | `"onlyA"` | `"onlyB"` |
| --- | --- | --- | --- | --- |
| Root | S | ROOT | Không thấy | Không thấy |
| Child A | S từ root | A (che root) | Có | Không thấy |
| Child B | S từ root | ROOT từ root | Không thấy | Có |

Những kết quả này giả định không có alias phức tạp, bean factory tùy chỉnh hoặc scope/resolver khác làm thay đổi lookup. Quan hệ ở đây là **root → children**, không phải các module hoàn toàn độc lập.

## 3. Tra cứu từ context con: đi lên khi cần

Với `child.getBean("sharedService")`, BeanFactory của con tìm bean **cùng tên tại chính nó** trước; nếu không có, tra parent rồi các ancestors khác. Parent không tìm ngược xuống child và hai sibling contexts không tự truy cập bean của nhau.

```text
Root:       sharedService, defaultClock
  |
  +-- ChildA: featureA
  +-- ChildB: featureB

ChildA.getBean("sharedService")  -> Root bean
Root.getBean("featureA")         -> not found
ChildB.getBean("featureA")       -> not found
```

Trong ví dụ này `sharedService` là singleton thuộc root: Child A/B chỉ nhận **reference được root quản lý**, không copy bean definition hoặc tạo singleton tương đương ở mỗi child. Để kiểm tra phạm vi của bean, `child.containsBean("sharedService")` có thể trả `true` trong khi `child.containsLocalBean("sharedService")` trả `false`.

## 4. Shadowing là theo **tên** giữa hai BeanFactory

```text
Parent: formatPolicy -> ParentFormatPolicy
Child : formatPolicy -> ChildFormatPolicy

child.getBean("formatPolicy")  -> ChildFormatPolicy
parent.getBean("formatPolicy") -> ParentFormatPolicy
```

Child che bean cùng tên từ parent khi lookup bắt đầu ở child, nhưng **không xóa** definition hoặc instance parent. Đây là *shadowing across contexts*, khác với việc đăng ký trùng bean definitions vào **một** BeanFactory (chịu chính sách overriding của registry). Nếu bean child trùng tên nhưng **khác kiểu**, lookup theo tên kèm required type còn có thể lỗi type; không mặc nhiên bỏ qua child để quay về parent. Bài riêng về bean-name shadowing xử lý các trường hợp type/qualifier/alias phức tạp.

## 5. API tra một bean khác API liệt kê local beans

| Câu hỏi | API phù hợp |
| --- | --- |
| Tìm một bean theo tên, có parent fallback | `child.getBean(name)` |
| Bean name khả kiến ở local hoặc ancestors? | `containsBean(name)` |
| Bean name có thuộc factory này? | `containsLocalBean(name)` |
| Definition có được đăng ký tại factory này? | `containsBeanDefinition(name)` |
| Liệt kê beans cùng kiểu **tại child** | `getBeansOfType(type)` |
| Liệt kê cả ancestors có xét name shadowing | `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, type)` |

`ListableBeanFactory.getBeansOfType(...)` chỉ liệt kê local beans, dù `child.getBean(Type.class)` có thể tìm lên parent trong trường hợp phù hợp. Vì vậy kết quả map rỗng ở child **không chứng minh** parent thiếu bean. Bài về tra cứu local/ancestor sẽ diễn giải các API và trường hợp masking cụ thể.

## 6. Dependency injection từ parent vào child

Một child bean có thể khai báo dependency có type chỉ tồn tại ở parent:

~~~java
@Configuration(proxyBeanMethods = false)
class ChildConfig {

    @Bean
    CheckoutController checkoutController(SharedOrderService service) {
        return new CheckoutController(service);
    }
}
~~~

Đây là snippet, cần định nghĩa `CheckoutController` và `SharedOrderService`. Nếu parent có bean `SharedOrderService` duy nhất phù hợp, Spring có thể resolve dependency cho child bean mà không cần copy bean definition sang child.

**Ownership khác injection:** child đang cầm reference tới object **do parent tạo/quản lý**, chứ không biến object đó thành child-owned singleton.

### Điều này hữu ích cho shared infrastructure

- Parent giữ những services dùng chung cho nhiều servlet/module.
- Child giữ controllers/presentation adapters của module mình.
- Parent có thể được tạo một lần, nhiều child contexts cùng dùng chung một instance parent service.
- Child đóng mà parent vẫn hoạt động thì parent service vẫn sống và phục vụ child khác.

### Điểm cần tránh

- Đừng cho parent bean yêu cầu inject trực tiếp child-only bean rồi kỳ vọng Spring tự tìm xuống children.
- Đừng để parent service tham chiếu child context như service locator chỉ để giải quyết một dependency ngược chiều.
- Đừng dựa vào automatic reuse parent singleton cho child-scoped state nếu bean thực ra không thread-safe.
- Nếu cần module-specific behavior, truyền interface/strategy ở child boundary hoặc thiết kế một registry do parent kiểm soát rõ ràng.

## 7. Java 17 runnable: root context với hai child contexts

Ví dụ dùng **Java 17**, **Maven**, **Spring Framework 6.2.19** thuần, không Spring Boot/web server/database.

Ta sẽ tạo:

- Parent context: `ParentService`, bean `"label" = "ROOT"` và một `Trace` chung.
- Child A: bean `"label" = "A"` (che giá trị parent), bean `"onlyA"` và listener của module A.
- Child B: bean `"onlyB"` và listener của module B, **không** khai báo `"label"` nên lấy từ parent.
- Hai children cùng tra `ParentService` **cùng instance** của parent.
- Khi child phát `PingEvent`, listener local được gọi rồi event **được propagate lên parent**, nơi parent listener xử lý.
- Parent phát event thì chỉ parent listener được gọi, không broadcast ngược xuống children.

### Cấu trúc

~~~text
spring-context-hierarchy/
├── pom.xml
└── src/main/java/example/ContextHierarchyDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-context-hierarchy</artifactId>
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
~~~

### src/main/java/example/ContextHierarchyDemo.java

~~~java
package example;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class ContextHierarchyDemo {

    public static void main(String[] args) {
        try (var parent =
                     new AnnotationConfigApplicationContext(
                             ParentConfig.class)) {

            try (var childA = new AnnotationConfigApplicationContext();
                 var childB = new AnnotationConfigApplicationContext()) {

                // Hierarchy phải được kết nối trước khi refresh child.
                childA.setParent(parent);
                childA.register(ChildAConfig.class);
                childA.refresh();

                childB.setParent(parent);
                childB.register(ChildBConfig.class);
                childB.refresh();

                String rootLabel = parent.getBean("label", String.class);
                String aLabel = childA.getBean("label", String.class);
                String bLabel = childB.getBean("label", String.class);

                ParentService rootService =
                        parent.getBean(ParentService.class);
                ParentService aService =
                        childA.getBean(ParentService.class);
                ParentService bService =
                        childB.getBean(ParentService.class);

                boolean sharedInstance =
                        rootService == aService && rootService == bService;
                boolean aLocalService =
                        childA.containsLocalBean("parentService");

                int aAncestors =
                        BeanFactoryUtils.beansOfTypeIncludingAncestors(
                                childA, ParentService.class).size();

                boolean siblingsIsolated =
                        !childB.containsBean("onlyA")
                        && !childA.containsBean("onlyB")
                        && !parent.containsBean("onlyA");

                // Bean factory ở child chỉ liệt kê local bean definitions.
                boolean aLocalTypeMapEmpty =
                        childA.getBeansOfType(ParentService.class).isEmpty();

                Trace trace = parent.getBean(Trace.class);
                childA.publishEvent(new PingEvent(childA, "A"));
                parent.publishEvent(new PingEvent(parent, "ROOT"));
                childB.publishEvent(new PingEvent(childB, "B"));

                List<String> expectedEvents = List.of(
                        "childA:A",
                        "parent:A",
                        "parent:ROOT",
                        "childB:B",
                        "parent:B");

                require("ROOT".equals(rootLabel), "root label");
                require("A".equals(aLabel), "child A shadowing");
                require("ROOT".equals(bLabel), "child B fallback");
                require(sharedInstance, "parent service identity");
                require(!aLocalService, "not child-owned");
                require(aAncestors == 1, "ancestor-aware query");
                require(aLocalTypeMapEmpty, "local-only type query");
                require(siblingsIsolated, "sibling isolation");
                require(expectedEvents.equals(trace.snapshot()),
                        "event propagation");

                System.out.println("Root label: " + rootLabel);
                System.out.println("Child A label: " + aLabel);
                System.out.println("Child B label: " + bLabel);
                System.out.println("ParentService shared: " + sharedInstance);
                System.out.println("Child A owns ParentService: "
                        + aLocalService);
                System.out.println("Child A ancestor services: " + aAncestors);
                System.out.println("Child A local type map empty: "
                        + aLocalTypeMapEmpty);
                System.out.println("Sibling isolation: " + siblingsIsolated);
                System.out.println("Events: "
                        + String.join(" -> ", trace.snapshot()));
                System.out.println("Hierarchy check: PASS");
            }
        }
    }

    private static void require(boolean ok, String description) {
        if (!ok) {
            throw new IllegalStateException(
                    "Hierarchy assertion failed: " + description);
        }
    }

    public static final class ParentService {
        public String name() {
            return "shared";
        }
    }

    public static final class Trace {
        private final List<String> entries = new ArrayList<>();

        public void add(String entry) {
            entries.add(entry);
        }

        public List<String> snapshot() {
            return List.copyOf(entries);
        }
    }

    public static final class PingEvent extends ApplicationEvent {
        private final String label;

        public PingEvent(Object source, String label) {
            super(source);
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class ParentListener {
        private final Trace trace;

        public ParentListener(Trace trace) {
            this.trace = trace;
        }

        @EventListener
        public void onPing(PingEvent event) {
            trace.add("parent:" + event.label());
        }
    }

    public static final class ChildAListener {
        private final Trace trace;

        public ChildAListener(Trace trace) {
            this.trace = trace;
        }

        @EventListener
        public void onPing(PingEvent event) {
            trace.add("childA:" + event.label());
        }
    }

    public static final class ChildBListener {
        private final Trace trace;

        public ChildBListener(Trace trace) {
            this.trace = trace;
        }

        @EventListener
        public void onPing(PingEvent event) {
            trace.add("childB:" + event.label());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {
        @Bean
        public ParentService parentService() {
            return new ParentService();
        }

        @Bean("label")
        public String label() {
            return "ROOT";
        }

        @Bean
        public Trace trace() {
            return new Trace();
        }

        @Bean
        public ParentListener parentListener(Trace trace) {
            return new ParentListener(trace);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildAConfig {
        @Bean("label")
        public String label() {
            return "A";
        }

        @Bean("onlyA")
        public String onlyA() {
            return "A-only";
        }

        @Bean
        public ChildAListener childAListener(Trace trace) {
            return new ChildAListener(trace);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildBConfig {
        @Bean("onlyB")
        public String onlyB() {
            return "B-only";
        }

        @Bean
        public ChildBListener childBListener(Trace trace) {
            return new ChildBListener(trace);
        }
    }
}
~~~

### Chạy

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.ContextHierarchyDemo
~~~

Stdout do chính các lệnh `System.out.println` tạo ra, không tính Maven/Spring logging tùy môi trường:

~~~text
Root label: ROOT
Child A label: A
Child B label: ROOT
ParentService shared: true
Child A owns ParentService: false
Child A ancestor services: 1
Child A local type map empty: true
Sibling isolation: true
Events: childA:A -> parent:A -> parent:ROOT -> childB:B -> parent:B
Hierarchy check: PASS
~~~

### Diễn giải từng kết quả

**Root label: ROOT.** Parent sở hữu bean tên `label` với giá trị `ROOT`.

**Child A label: A.** Child A có bean local cùng tên nên lookup từ A chọn bean của A, **không phải** sửa hoặc xóa bean parent. Parent vẫn trả `ROOT`.

**Child B label: ROOT.** B không có bean local tên `label` nên tra lên parent và lấy giá trị root.

**ParentService shared: true.** Parent sở hữu singleton `ParentService`. Khi A và B tra type duy nhất ấy, cả hai nhận cùng instance; object thuộc parent, không bị copy sang hai child caches.

**Child A owns ParentService: false.** `containsLocalBean("parentService")` chỉ kiểm tra bean local nên là false.

**Child A ancestor services: 1.** `BeanFactoryUtils.beansOfTypeIncludingAncestors` truy vấn cả hierarchy, tìm đúng parent bean `ParentService`.

**Child A local type map empty: true.** `getBeansOfType(ParentService.class)` theo contract `ListableBeanFactory` chỉ liệt kê local beans, nên map rỗng, dù `getBean(ParentService.class)` vẫn có thể trả parent bean.

**Sibling isolation: true.** Child A không nhìn thấy `onlyB`, Child B không nhìn thấy `onlyA`, root cũng không nhìn thấy `onlyA`. Sibling modules không được kết nối lookup trực tiếp chỉ vì có chung parent.

**Events.** Khi A publish `PingEvent("A")`, listener A được gọi và sau đó event được propagate lên parent, cho trace `childA:A → parent:A`. Root publish `PingEvent("ROOT")` chỉ kích hoạt parent listener. B publish tương tự `childB:B → parent:B`. Không có `@Async`/executor riêng, nên các callback đồng bộ và thứ tự trong ví dụ ổn định.

### Những gì ví dụ không khẳng định

- Không có broadcast event tự động **từ parent xuống mọi children**.
- Không có parent tự quản lý lifecycle của children đã tạo riêng.
- Không có bảo đảm thread-safety cho `Trace` khi chuyển sang async dispatch.
- Không có chuyện `getBeansOfType` của child luôn bao gồm parent beans.
- Không nói rằng hai child có thể sửa/sở hữu cùng một singleton độc lập; ở đây chúng **dùng chung reference** do parent sở hữu.
- Không có distributed messaging, cross-JVM propagation hoặc transaction semantics.

## 8. Environment của parent và child

`AbstractApplicationContext.setParent(parent)` có thể **merge Environment của parent vào Environment của child** nếu parent environment là `ConfigurableEnvironment`. Theo Javadoc Spring 6.2, `ConfigurableEnvironment.merge(...)` append property sources/profiles từ parent; các PropertySources trùng **tên nguồn** giữ source của child và bỏ source cùng tên từ parent.

Điều này khác với bean lookup fallback, và có một số đặc điểm quan trọng.

### Nguồn properties và độ ưu tiên

Giả sử trước khi liên kết hierarchy:

~~~text
Parent Environment:
  parentSettings:
    service.region = GLOBAL
    service.timeout = 1000

Child Environment:
  childSettings:
    service.timeout = 2000
    service.feature = A
~~~

Sau khi setParent, child có thể resolve:

| Key | Child lookup | Parent lookup |
| --- | --- | --- |
| `service.region` | GLOBAL | GLOBAL |
| `service.timeout` | 2000 nếu child source ưu tiên | 1000 |
| `service.feature` | A | Không có |

**Chú ý:** precedence xảy ra theo danh sách `PropertySources`, **không** đơn thuần do root/child BeanFactory. Một nguồn nằm ở child nhưng ưu tiên thấp có thể cho kết quả khác ví dụ; nên inspect `getEnvironment().getPropertySources()` để xác nhận thực tế.

### Trùng tên PropertySource

Nếu cả parent và child cùng có một source tên `"applicationSettings"`, phép `merge` giữ bản child và không tự merge từng key của hai MapPropertySources cùng tên. Điều này có thể làm những keys chỉ ở parent source **không xuất hiện** trong child nếu parent source bị bỏ qua vì trùng tên.

### Thời điểm thiết lập parent quan trọng

Theo Javadoc, merge xảy ra khi setParent; những thay đổi **sau thời điểm merge** trên parent Environment không nhất thiết được phản ánh trong child, đặc biệt với việc thêm/reorder sources hoặc active profiles. Cấu hình parent Environment **trước khi thiết lập parent cho child** và trước refresh khi values tham gia `@Profile`, `@Conditional`, `@PropertySource`, placeholders.

Nếu child cần override, tạo một `MapPropertySource` có tên riêng, dùng `addFirst` hoặc thiết lập precedence rõ, thay vì dựa vào default OS/JVM environment.

### Active/default profiles

`ConfigurableEnvironment.merge(...)` cũng có thể append active/default profile names từ parent (lọc trùng). Profile có thể quyết định BeanDefinitions được đăng ký; vì vậy cần thiết lập profiles **trước khi refresh** child. Không sửa profile sau refresh rồi kỳ vọng các conditional beans được tự tạo/xóa.

## 9. BeanPostProcessor và BeanFactoryPostProcessor có phạm vi local

Đây là ranh giới rất quan trọng khi dùng hierarchy.

Spring Reference nêu rõ:

- `BeanPostProcessor` hoạt động **trên bean instances thuộc container nơi processor được đăng ký**.
- `BeanFactoryPostProcessor` xử lý **metadata/definitions của chính container đó**.
- Processors trong parent **không tự được áp dụng lên beans thuộc child**.
- Processors trong child **không chạy ngược lên beans của parent**.

~~~text
Parent Context
  ParentBeanPostProcessor  ───> xử lý ParentBean
                     │
                     X  (không tự đi xuống)
                     │
Child Context
  ChildBeanPostProcessor   ───> xử lý ChildBean
                     │
                     X  (không tự đi lên)
                     │
ParentBean vẫn là object do parent quản lý
~~~

### Hậu quả đối với Spring AOP

Spring AOP auto-proxy infrastructure thường dựa trên `BeanPostProcessor`. Nếu auto-proxy processor/aspect setup chỉ có ở child, đừng tự kết luận nó sẽ proxy các parent singleton được inject vào child. Ngược lại, parent proxy hạ tầng không tự xử lý mọi bean mới tạo ở child.

Nếu một parent service cần AOP/transaction semantics, hãy cấu hình processor/auto-proxying **ở context thực sự sở hữu parent service**. Nếu child beans cần tương tự, child cần infrastructure phù hợp.

### @Autowired không có nghĩa mọi processor đều chia sẻ

Một child bean có thể resolve dependency từ parent thông qua bean factory hierarchy. Nhưng đó **khác** việc nhận các BeanPostProcessors của parent. Child JavaConfig context thông thường có annotation support của nó, được thiết lập tại child; parent processor không được copy tự động.

### BFPP: PropertySourcesPlaceholderConfigurer

Nếu parent định nghĩa một `PropertySourcesPlaceholderConfigurer`, processor ấy sửa metadata của parent. Không kỳ vọng nó thay placeholder strings bên trong bean definitions của child. Cần một cơ chế phù hợp với child Environment/embedded resolver khi child có placeholders của riêng mình.

## 10. Event propagation: từ child lên parent, không broadcast xuống

Spring `AbstractApplicationContext.publishEvent(...)` xử lý event trong context hiện tại, sau đó **propagate lên parent** nếu có. Đây là một chiều đi lên.

~~~text
Child A.publishEvent(E)
       │
       ├── listeners trong Child A nhận E
       │
       └── Parent.publishEvent(E)
               └── listeners trong Parent nhận E

Parent.publishEvent(E)
       │
       └── listeners trong Parent nhận E
             (Child A, Child B không tự nhận)
~~~

Child B cũng propagate lên cùng parent. Do đó một parent listener quan tâm events từ cả hai children sẽ nhận các events đó, nhưng một Child A listener không nhận event do Child B phát trực tiếp, trừ khi ứng dụng viết một cơ chế forwarding/broadcast riêng.

### Hệ quả cho ContextRefreshedEvent và ContextClosedEvent

Spring có các lifecycle events như `ContextRefreshedEvent`, `ContextClosedEvent`. Trong hierarchy, parent listener có thể nhận event được phát từ child do upward propagation. Vì vậy parent listeners cần phân biệt event **do context nào tạo** nếu chỉ muốn xử lý sự kiện của chính parent.

Ví dụ:

~~~java
@EventListener
public void refreshed(ContextRefreshedEvent event) {
    if (event.getApplicationContext() == parentContext) {
        // Chỉ xử lý refresh của parent.
    }
}
~~~

Đây là snippet, giả định `parentContext` được inject/định nghĩa đúng. Không phải mọi listener trên parent đều được gọi một lần duy nhất trong toàn hệ thống khi nhiều children refresh/close.

### Trùng xử lý event

Một parent context listener và một child context listener có thể cùng nhận một event phát từ child. Nếu cả hai làm một side effect giống nhau (gửi mail/ghi log), event sẽ được xử lý hai lần ở những tầng khác nhau. Đây không phải Spring bug; nó phản ánh phạm vi listener của hierarchy.

### Asynchronous dispatch

Chiều propagate vẫn mang ý nghĩa hierarchy, nhưng nếu multicaster ở child/parent có executor hoặc listener dùng `@Async` thì thứ tự **hoàn tất** không được đảm bảo theo sơ đồ đồng bộ. Đừng dựa vào thời gian log để suy ra sai chiều dispatch.

## 11. MessageSource và sự kế thừa đa ngôn ngữ

`ApplicationContext` cũng triển khai `MessageSource`. Spring có `HierarchicalMessageSource` để một message source của child có thể thử nguồn của parent khi không giải quyết được mã thông điệp.

Ví dụ:

~~~text
Parent MessageSource:
  common.save = Save
  common.cancel = Cancel

Child A MessageSource:
  featureA.title = Feature A
  common.save = Save A

Child A message lookup:
  "featureA.title" → Child A bundle
  "common.save"    → Child A bundle (override)
  "common.cancel"  → Parent bundle (fallback)
~~~

Chi tiết precedence còn phụ thuộc implementation, basenames, Locale, `useCodeAsDefaultMessage` và default-message policy.

### Bean name messageSource

Trong mỗi ApplicationContext, Spring tìm bean tên `"messageSource"` của chính context để kết nối hierarchy. Nếu child không có source local, delegation có thể được thiết lập. Nếu child định nghĩa `messageSource` riêng, Spring có thể liên kết parent MessageSource thông qua `HierarchicalMessageSource` khi phù hợp.

Không nhầm cơ chế i18n fallback với bean lookup: child `MessageSource.getMessage` trả một translation, không nhất thiết là `getBean("messageSource")` đang trỏ tới cùng instance parent. Context có thể dựng một delegating message source cục bộ.

### Missing-key policy

Khi parent có key nhưng child source bật `useCodeAsDefaultMessage=true`, value trả về có thể gây hiểu nhầm vì code có thể được coi như default quá sớm. Cần test cả happy path, missing child key/parent fallback, locale chưa hỗ trợ và absence of default.

## 12. Lifecycle: ai refresh và ai close?

Parent và child đều là contexts với vòng đời riêng. Cách lập trình thông thường:

~~~java
var parent = new AnnotationConfigApplicationContext(ParentConfig.class);

var child = new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();

// ... dùng child

child.close();
parent.close();
~~~

Đây là snippet; cần định nghĩa `ParentConfig`/`ChildConfig` và imports.

### Trình tự khuyến nghị

1. Tạo/configure parent.
2. Refresh parent trước để shared infrastructure đã sẵn sàng.
3. Tạo child, setParent **trước child.refresh()**.
4. Register bean definitions của child.
5. Refresh child.
6. Khi dừng, **đóng child trước parent** để child destroy callbacks không phụ thuộc một parent bean đã bị hủy.

Spring không đảm bảo tự đóng mọi child đã được ứng dụng tạo riêng khi bạn đóng parent, hoặc đóng parent khi đóng một child. Trong code tự tạo contexts, **ứng dụng sở hữu lifecycle của từng context** và phải quản lý close rõ ràng.

### Parent singleton được destroy khi nào?

Nếu parent sở hữu singleton `DataSource`/`ParentService`, việc đóng Child A không tự destroy bean parent. Parent singleton được quản lý bởi parent; đóng parent sẽ kích hoạt destroy lifecycle của nó, và điều đó có thể làm những child còn sống không dùng được dependency nữa.

**Sai thứ tự nguy hiểm:** parent.close() trước khi child.close(), trong khi child còn định dùng shared DataSource ở destroy callback. Do đó root nên sống lâu hơn những children phụ thuộc nó.

### Refresh riêng và reuse

Một số context implementation hỗ trợ refresh lại; `GenericApplicationContext` chỉ hỗ trợ refresh một lần. Đừng tự gọi `refresh()` nhiều lần vào mọi context và mong Spring tự copy definitions hoặc restore shared state. Nếu cần reconfigure module, lifecycle manager phải xác định loại context và strategy recreate/reload cụ thể.

## 13. Parent/child không phải bean definition inheritance

Đây là một tên gần giống nhưng **hoàn toàn khác**:

- **ApplicationContext hierarchy:** hai (hoặc nhiều) containers nối với nhau theo quan hệ parent/child, với bean lookup fallback và boundary khác.
- **BeanDefinition inheritance:** một bean definition dùng metadata từ parent **bean definition** (ví dụ XML `parent="templateBean"`) trong cấu hình, thường không hề yêu cầu hai ApplicationContexts.

Ví dụ XML bean definition inheritance:

~~~xml
<bean id="baseService"
      abstract="true"
      class="example.Service"
      scope="prototype">
    <property name="timeout" value="1000"/>
</bean>

<bean id="realService" parent="baseService">
    <property name="timeout" value="2000"/>
</bean>
~~~

Đây là XML snippet; cần document `<beans>` hợp lệ và class thật tương ứng. Nó mô tả kế thừa **metadata cấu hình** chứ không phải `childContext.setParent(parentContext)`.

### Không nói “child bean kế thừa singleton của parent” theo nghĩa Java subclass

Khi child gọi parent singleton, child nhận một **reference đến instance parent**. Object không trở thành subclass hoặc copy của parent bean. Cách hiểu đúng là **delegated lookup và shared instance**, không phải object-oriented inheritance.

## 14. Nhiều cấp hierarchy và sibling boundaries

Spring không giới hạn ở hai cấp:

~~~text
Root
  |
  +-- RegionContext
        |
        +-- TenantContext
              |
              +-- RequestFeatureContext
~~~

Nếu một bean không có ở leaf, lookup có thể đi ngược dọc nhiều ancestors. Tuy nhiên, hierarchy nhiều tầng có thể làm khó biết bean thực nằm ở đâu, và shadowing cùng tên có thể xảy ra ở mỗi cấp.

### Khi nào hierarchy nhiều cấp có ích?

- Web layer có root shared infrastructure, các servlet contexts riêng.
- Batch system có shared scheduler/data access và mỗi job một child context.
- Plugin subsystem có shared parent services và isolated child modules.
- Test fixtures có shared root configuration và context phụ thuộc.

### Khi nào không nên dùng?

- Ứng dụng nhỏ chỉ có vài beans.
- Cấu trúc không có một boundary/lifecycle rõ ràng giữa modules.
- Team cần truy vấn ngược từ root xuống children thường xuyên.
- Shared mutable singleton làm mọi module phụ thuộc state global.
- Quá nhiều parent/child overlapping bean names khiến override khó dự đoán.

Đừng tạo child ApplicationContext mới cho **mỗi HTTP request** chỉ để áp request-scoped dependencies; Spring đã có request/session scopes phù hợp trong web contexts. Tạo/refresh/close context thường có chi phí và lifecycle phức tạp hơn rất nhiều so với tạo scoped bean.

## 15. Parent singleton, scope và state sharing

Một parent singleton được nhiều children inject là **một object duy nhất trong JVM/context**, không phải hai singleton riêng.

~~~text
ParentContext.singletonObjects:
  sharedService -> object S

ChildA gets sharedService -> object S
ChildB gets sharedService -> object S
~~~

Nếu object S có state mutable không thread-safe, hai modules có thể làm nó race. `@Scope("singleton")` chỉ đảm bảo một instance trong **một bean factory**, không tự đảm bảo thread safety.

### Prototype ở parent

Nếu bean trong parent có scope `prototype`, child gọi lookup có thể khiến parent tạo object mới theo scope semantics. Nhưng chính parent factory vẫn quản lý registration/type/scope của bean definition ấy. Với prototype, container thường không tự quản lý trọn destroy lifecycle sau khi trao instance; caller phải thiết kế cleanup nếu cần.

### Request/session scopes

Trong web application, request/session scopes phụ thuộc scope registration và thread/request context. Không giả định request scope trong child có thể áp vào parent như một global context một cách tùy tiện. Cần đặt scoped beans ở context có web scope infrastructure phù hợp, và dùng scoped proxies/ObjectProvider khi singleton cần lấy request bean theo request.

### Các types nằm cùng tên ở parent/child

Nếu một child có local bean tên `sharedService` nhưng là một type khác, normal name-based lookups chọn local; các injection points theo type/qualifier có thể cần phân giải riêng. Nên tránh shadow tên của shared infrastructure trừ khi thực sự có chủ đích để thay thế implementation.

## 16. Những sai lầm thường gặp trong configuration

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Parent không tìm thấy ChildController | Lookup chỉ đi lên từ child, không đi xuống từ parent | Giữ controller ở child; đừng inject ngược |
| Child getBean được service nhưng getBeansOfType rỗng | ListableBeanFactory local-only | Dùng BeanFactoryUtils nếu cần ancestor-aware query |
| Child được bean khác cùng tên parent | Local bean shadowing | Kiểm tra bean name và context ownership |
| Child B không thấy bean riêng Child A | Siblings không tra nhau | Đưa shared service vào parent hoặc phối hợp qua interface |
| @Autowired theo type có ambiguity | Nhiều candidates across hierarchy | Qualifier/name/Primary và thiết kế rõ |
| Parent BPP không tạo proxy cho child bean | BPP scoped per container | Bật infrastructure ở child nếu cần |
| Child BFPP không sửa parent BeanDefinitions | BFPP scoped per container | Cấu hình BFPP trong đúng context |
| Child @Value không đúng config parent | Environment merge/precedence/timing | SetParent và nguồn trước refresh |
| Parent thấy property mới nhưng child không | Merge không tự refresh mọi thay đổi sau đó | Configure parent sources trước merge |
| Trùng PropertySource name làm mất parent keys | Merge giữ source name ở child | Dùng tên sources khác hoặc gộp tường minh |
| Parent listener nhận event hai lần tưởng duplicate | Child event propagate + event khác | Lọc nguồn/event type, idempotency |
| Child listener không nhận parent event | Event không broadcast xuống | Forward/broadcast explicit nếu cần |
| ContextRefreshedEvent bị xử lý nhiều lần | Parent nghe cả refresh của child | Lọc event.getApplicationContext() |
| Đóng parent trước làm child destroy lỗi | Child phụ thuộc parent resources | Close child trước parent |
| Parent.close() tưởng đóng mọi child | Lifecycle contexts độc lập | Theo dõi và close từng context |
| Singleton parent bị thay đổi từ child B | Shared mutable object | Immutable/stateless/thread-safe design |
| @Profile child không bật đúng | Profiles được merge/cấu hình sai thời điểm | Set profiles trước refresh |
| MessageSource child không fallback | Child policy/parent linkage | Test HierarchicalMessageSource |
| XML `parent` bị hiểu là context cha | Bean definition metadata inheritance | Phân biệt hai cơ chế |
| Tests pass khi một context, fail khi tách | Bean ownership/processor scope/qualifier | Integration tests hierarchy thực |

## 17. Checklist thiết kế hệ thống

1. Có lý do rõ ràng để dùng nhiều ApplicationContexts thay vì một context không?
2. Shared infrastructure có thuộc parent, module-specific beans có thuộc đúng child?
3. Parent có cần biết beans của child không? Nếu có, đã xem lại chiều dependency?
4. Bean names có bị trùng không? Nếu có, shadowing có thật sự chủ đích?
5. Những dependency theo type có nhiều candidates giữa parent/child không?
6. Có dùng `containsLocalBean` và `containsBean` đúng mục đích?
7. Có hiểu `getBeansOfType` là local-only và khi nào cần BeanFactoryUtils?
8. Parent singleton có immutable/stateless hoặc thread-safe khi nhiều children dùng chung?
9. Các child beans có được post-process/proxy bằng infrastructure riêng phù hợp?
10. BFPP/BeanPostProcessor có được khai báo trong context sở hữu beans cần xử lý?
11. Parent Environment sources, active profiles có được cấu hình trước setParent/refresh?
12. Có kiểm tra property source name collisions và precedence ở child?
13. MessageSource fallback giữa parent-child đã được test cho nhiều Locale?
14. Events phát từ child có bị parent xử lý thêm ngoài ý định không?
15. Parent listeners có lọc ContextRefreshedEvent/ContextClosedEvent theo source?
16. Có tránh giả định parent publish tự lan xuống children?
17. Từng context có refresh/close ownership rõ ràng?
18. Shutdown sequence có đóng children trước parent?
19. Tests có tạo hierarchy thật và kiểm tra shadowing, lookup, singleton identity, events?
20. Có phân biệt hierarchy với BeanDefinition inheritance và scopes?

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Child có thể getBean từ parent nếu child không có bean cùng tên không?

**Đáp án:** Có, vì child bean factory có parent lookup fallback.

**Câu 2.** Parent có tự thấy beans riêng của child không?

**Đáp án:** Không. Parent không tự duyệt xuống children.

**Câu 3.** Child A và Child B cùng cha có tự nhìn thấy bean riêng của nhau không?

**Đáp án:** Không. Đưa bean chia sẻ lên parent hoặc tạo cơ chế phối hợp rõ nếu cần.

**Câu 4.** Parent và child đều định nghĩa bean `"label"` thì `child.getBean("label")` chọn cái nào?

**Đáp án:** Child local bean che parent bean cùng tên.

**Câu 5.** `child.getBeansOfType(ParentService.class)` có chắc bao gồm beans của parent không?

**Đáp án:** Không. `ListableBeanFactory` APIs thường chỉ xét local factory; dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors` khi cần duyệt parent.

**Câu 6.** `child.getBean(ParentService.class)` có thể vẫn tìm thấy bean parent dù getBeansOfType local rỗng không?

**Đáp án:** Có, đó là khác biệt single-bean lookup và local type enumeration.

**Câu 7.** BeanPostProcessor ở parent có tự áp dụng lên child beans không?

**Đáp án:** Không. Processors hoạt động trong từng container, cần đặt ở nơi sở hữu bean tương ứng.

**Câu 8.** setParent(child,parent) có tự clone tất cả parent beans sang child không?

**Đáp án:** Không. Nó thiết lập hierarchy và fallback lookup, không clone bean definitions/singletons.

**Câu 9.** Environment merge có thể làm child ưu tiên source của mình khi trùng name không?

**Đáp án:** Có. `ConfigurableEnvironment.merge` giữ source child khi cùng tên nguồn; parent sources chỉ được append nếu phù hợp.

**Câu 10.** Child publish event thì listener của parent có thể nhận không?

**Đáp án:** Có. `AbstractApplicationContext` propagate event lên parent.

**Câu 11.** Parent publish event thì listener của tất cả children có tự nhận không?

**Đáp án:** Không. Spring không broadcast tự động từ parent xuống children.

**Câu 12.** Đóng Child A có tự destroy parent singleton đang được Child B sử dụng không?

**Đáp án:** Không. Parent quản lý singleton của parent; parent còn mở thì object có thể tiếp tục được child khác dùng.

**Câu 13.** Nếu root và child dùng chung bean name `messageSource`, việc lấy bản dịch có luôn bỏ parent messages không?

**Đáp án:** Không mặc định. MessageSource có hierarchy riêng; child có thể fallback sang parent tùy `HierarchicalMessageSource`/cấu hình.

**Câu 14.** Bean definition `parent="template"` trong XML có phải tạo một parent ApplicationContext không?

**Đáp án:** Không. Đây là kế thừa bean metadata, không phải quan hệ hai contexts.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — ApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationContext.html)
- [Spring Framework 6.2 — ConfigurableApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ConfigurableApplicationContext.html)
- [Spring Framework 6.2 — GenericApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html)
- [Spring Framework 6.2 — AbstractApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html)
- [Spring Framework 6.2 — BeanFactoryUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html)
- [Spring Framework 6.2 — HierarchicalBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html)
- [Spring Framework 6.2 — ListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)
- [Spring Framework 6.2 — ConfigurableEnvironment Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html)
- [Spring Framework Reference — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Framework Reference — ApplicationContext Capabilities](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Framework Reference — Context Hierarchies in Integration Tests](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/hierarchies.html)
- [Spring Framework Reference — Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-002">Quy tắc con nhìn thấy cha và cha không nhìn thấy con</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc con nhìn thấy cha và cha không nhìn thấy con trong Spring ApplicationContext

## 1. Vấn đề cần hiểu

Khi một ứng dụng Spring lớn dùng **nhiều ApplicationContext**, các bean không còn nằm trong một kho chứa duy nhất. Spring cho phép xây dựng **cây context**: context gốc (*parent*) chứa tài nguyên hoặc services dùng chung, còn mỗi context con (*child*) chứa beans riêng của một module, một servlet hoặc một phần ứng dụng.

Quy tắc cốt lõi:

> **Context con có thể tra cứu bean từ context cha khi không tìm thấy bean phù hợp cục bộ; context cha không tự tra cứu bean trong context con.**

Đây là quan hệ **một chiều**, không phải cơ chế “merge tất cả bean definitions” giữa các contexts. Nắm vững hướng tra cứu giúp giải thích vì sao một web controller có thể inject service ở root context nhưng root service không tự inject controller nằm ở child context.

Bài học trình bày:

- `ApplicationContext` và `HierarchicalBeanFactory`.
- `setParent(...)`, thời điểm kết nối parent/child và lifecycle.
- Lookup theo tên, theo kiểu, autowiring và **shadowing** khi trùng bean name.
- `containsBean`, `containsLocalBean`, `containsBeanDefinition`, `getBean`, `getBeansOfType` và `BeanFactoryUtils.*IncludingAncestors`.
- Vì sao **ListableBeanFactory APIs không mặc nhiên duyệt ancestor**.
- Những điểm khác của event propagation, `MessageSource`, `Environment` và bean lifecycle.
- Dùng nhiều child contexts, kiểm soát visibility, tránh phụ thuộc ngược.
- Một chương trình Java 17, Spring Framework 6.2.18, Gradle, output xác định.
- Các lỗi cấu hình, tình huống test và chiến lược tổ chức module.

## 2. Cây context và sơ đồ visibility

Ví dụ:

```text
                   Parent ApplicationContext
             +---------------------------------+
             | rootService                     |
             | dataSource                      |
             | sharedSettings                  |
             +---------------------------------+
                     ^              ^
                     |              |
            lookup fallback    lookup fallback
                     |              |
       +-----------------+    +-----------------+
       | Child A Context |    | Child B Context |
       | controllerA     |    | controllerB     |
       | handlerA        |    | handlerB        |
       +-----------------+    +-----------------+
```

Các hướng mặc định:

- Child A → Parent: **được**, khi bean không tồn tại cục bộ hoặc dependency được resolve từ ancestor.
- Child B → Parent: **được**.
- Parent → Child A hoặc Child B: **không có lookup tự động**.
- Child A → Child B: **không có lookup tự động** qua parent; hai siblings độc lập.
- Child A → chính Child A: được, và **local bean name ưu tiên trước parent**.

Một bean tồn tại ở parent **không được sao chép** sang mỗi child. Với scope singleton ở parent, child được ủy quyền lookup và nhận chính object singleton do parent quản lý.

**Ngoại lệ theo thiết kế:** code có thể cầm trực tiếp một reference tới child context hoặc một object child rồi gọi nó. Đó là **tham chiếu explicit do ứng dụng truyền vào**, không phải quy tắc dependency lookup tự động từ parent xuống child.

## 3. Interface nền tảng: HierarchicalBeanFactory

`org.springframework.beans.factory.HierarchicalBeanFactory` extends `BeanFactory` và khai báo hai method chính:

```java
BeanFactory getParentBeanFactory();

boolean containsLocalBean(String name);
```

Đây là phần chữ ký API rút gọn, không phải chương trình Java độc lập. `getParentBeanFactory()` có thể trả `null` nếu factory không có parent.

Một `ApplicationContext` cũng là `HierarchicalBeanFactory`, và `ConfigurableApplicationContext` cung cấp:

```java
void setParent(ApplicationContext parent);
```

Hệ thống BeanFactory underlying của các implementation thông dụng liên kết tới parent factory tương ứng. Spring Javadoc `BeanFactory` mô tả rõ: operations thuộc `BeanFactory` có thể hỏi parent factory khi không tìm thấy bean trong factory hiện tại, và bean cục bộ có cùng name ưu tiên bean ancestor.

**Nhưng:** `ApplicationContext` còn implement `ListableBeanFactory`, và **các API liệt kê beans của `ListableBeanFactory` có quy tắc khác**. Không thể suy ra “con nhìn thấy cha” nghĩa là **mọi method** luôn gộp toàn bộ hierarchy.

## 4. Tạo parent và child đúng thời điểm

Với `AnnotationConfigApplicationContext`:

```java
AnnotationConfigApplicationContext parent =
        new AnnotationConfigApplicationContext();
parent.register(ParentConfig.class);
parent.refresh();

AnnotationConfigApplicationContext child =
        new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
```

Đây là **mảnh bootstrap minh họa**; `ParentConfig` và `ChildConfig` phải tồn tại, contexts cần đóng sau sử dụng.

Thứ tự được khuyến nghị:

1. Tạo parent.
2. Register parent configurations.
3. `parent.refresh()` để parent sẵn sàng.
4. Tạo child.
5. **`child.setParent(parent)` trước `child.refresh()`**.
6. Register child configurations.
7. `child.refresh()`.
8. Khi shutdown, đóng child trước parent.

`AnnotationConfigApplicationContext(SomeConfig.class)` tự register/refresh trong constructor. Nếu bạn muốn **setParent trước refresh**, hãy dùng **constructor rỗng**, gọi `setParent` và `register` rồi tự `refresh` như trên.

Spring có thể cho phép các thao tác cấu hình theo nhiều trình tự ở một vài implementation, nhưng **không** nên dựa vào việc thay parent sau refresh để tạo lại dependency graph đã được instantiating. Hãy chốt hierarchy trước khi tạo singletons.

### Một child chỉ có một parent trực tiếp

`setParent` thiết lập một parent context, không nhận mảng parents. Có thể có **nhiều tầng**:

```text
Grandparent
     ^
     |
  Parent
     ^
     |
   Child
```

Lookup theo tên có thể tiếp tục từ child → parent → grandparent cho đến khi tìm thấy bean hoặc hết chuỗi ancestor.

## 5. Tra cứu bean theo tên: local trước, rồi parent

Giả sử parent chứa:

```java
@Bean("catalog")
Catalog parentCatalog() {
    return new Catalog("parent");
}
```

Child **không** khai báo bean tên `catalog`. Khi đó:

```java
Catalog fromChild = child.getBean("catalog", Catalog.class);
Catalog fromParent = parent.getBean("catalog", Catalog.class);

boolean same = fromChild == fromParent;
```

Đây là mảnh minh họa; cần định nghĩa `Catalog` và các contexts. Với parent bean singleton mặc định, `same` là `true`. Child **ủy quyền lên parent**, không tạo lại Catalog.

Nếu parent không có bean đó nhưng child có, `parent.getBean("childOnly")` không tìm ngược xuống child và thường ném `NoSuchBeanDefinitionException`.

Lookup theo tên không tự tìm ở **siblings**. Nếu Child A định nghĩa bean `reportController`, Child B không thể tự getBean bean này chỉ vì cả hai có cùng một parent.

## 6. Local bean cùng tên sẽ che parent bean

Giả sử parent khai báo `@Bean("transport")` trả `Transport("parent")` và child khai báo `@Bean("transport")` trả `Transport("child")` (đây là hai factory methods **ở hai context riêng**, với class `Transport` do ứng dụng định nghĩa). Khi gọi `child.getBean("transport")`, child chọn instance của chính nó; `parent.getBean("transport")` vẫn là parent instance. Nếu child không định nghĩa tên đó, child mới delegate lên parent.

Shadowing **khác** `allowBeanDefinitionOverriding` trong cùng một registry. Nó áp dụng theo tên ngay cả khi types khác nhau: `child.getBean("transport", ExpectedParentType.class)` có thể ném `BeanNotOfRequiredTypeException` thay vì né bean child để lấy bean cha. Bài về tên bean trùng giữa hai context sẽ phân tích trường hợp này bằng runnable demo.

## 7. Ba phép kiểm tra tồn tại có phạm vi khác nhau

```java
child.containsBean("rootService");
child.containsLocalBean("rootService");
child.containsBeanDefinition("rootService");
```

Nếu `rootService` chỉ ở parent, ba kết quả lần lượt là `true`, `false`, `false`. `containsBean` hỏi liệu tên đó **khả kiến**, `containsLocalBean` hỏi nó có thuộc local BeanFactory và `containsBeanDefinition` hỏi registry hiện tại có definition tương ứng. Local manually registered singleton/alias khiến hai phép local không hoàn toàn đồng nghĩa. Một kết quả `containsBean=true` cũng không bảo đảm khởi tạo thành công nếu còn lỗi dependency hoặc scope.

## 8. Liệt kê bean cùng kiểu mặc định là local-only

```java
Map<String, RootService> local = child.getBeansOfType(RootService.class);
```

Đây là snippet cần class `RootService` cùng import `Map`. Nếu loại bean này chỉ được khai báo ở parent, map local có thể rỗng dù `child.getBean(RootService.class)` lấy được parent bean. Đây là hợp đồng của `ListableBeanFactory`, không phải sai lệch của hierarchy.

## 9. Khi thật sự cần tìm cả ancestors

```java
Map<String, RootService> all =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
                child, RootService.class);
```

Helper trên (cần import `BeanFactoryUtils`) gộp candidates khả kiến theo các BeanFactory trong hierarchy, có **name shadowing**. Nó không đồng nghĩa inventory toàn bộ physical objects khi parent bean bị che bởi child name. Bài về tra cứu local/ancestor phân tích API này cùng `getBeanNamesForType`, qualifiers và side effects của type introspection.

## 10. Autowiring của child bean dùng parent bean

Một child-managed bean có thể inject dependency định nghĩa ở parent.

```java
public record RootService(String id) {
}

public record ChildProcessor(RootService rootService) {
}
```

Và trong child configuration:

```java
@Bean
ChildProcessor childProcessor(RootService rootService) {
    return new ChildProcessor(rootService);
}
```

Đây là các mảnh Java minh họa thuộc cùng package/lớp phù hợp, cần parent cung cấp một bean `RootService` có thể resolve.

Khi child tạo `childProcessor`, Spring sẽ tìm dependency ở child trước rồi có thể resolve `RootService` từ parent. Điều này làm việc dùng chung `DataSource`, application services hoặc security infrastructure từ root context trở nên thuận tiện.

### Parent bean không tự inject child bean

Giả sử parent khai báo:

```java
@Bean
ParentCoordinator coordinator(ChildProcessor childProcessor) {
    return new ParentCoordinator(childProcessor);
}
```

Nếu `ChildProcessor` chỉ ở child, parent không thể resolve dependency đó trong quá trình tạo `ParentCoordinator`. Kết quả thường là lỗi unsatisfied dependency khi parent refresh, **dù child sau đó có định nghĩa bean đúng type**.

Đây là cấu hình sai hướng dependency. Parent nên chứa abstraction/contract chung và logic không lệ thuộc một child cụ thể; child inject parent services để mở rộng thêm hành vi.

## 11. Lookup theo type và shadowing: lưu ý nâng cao

`getBean(Class<T>)` là API BeanFactory có thể xét hierarchy, nhưng autowiring theo type cần xử lý **candidate discovery, generic qualifiers, primary, fallback, hierarchy priority**. Không rút gọn thành quy tắc “chỉ cần một type bất kỳ ở parent thì luôn thắng”.

Các tình huống:

- Child không có ứng viên local, parent có một dependency hợp lệ: thường inject được parent bean.
- Child có bean **cùng name** parent bean: child name che parent bean trên lookup.
- Child và parent có nhiều beans cùng type nhưng **tên khác nhau**: ứng dụng phải xem `@Primary`, `@Qualifier`, tên dependency hoặc phương án thiết kế explicit.
- Bean parent có một injection point cần type chỉ tồn tại ở child: không tự “quay xuống” child.
- `ObjectProvider`, collections và map injection có những API/lựa chọn candidates đặc thù, cần integration test khi nhiều contexts và nhiều candidates.

**Không phụ thuộc thứ tự tạo bean** để “đổi hướng lookup”. Dù child được refresh xong sau parent, parent cũng không tự có quyền tìm bean definitions của child.

## 12. Ví dụ chạy đầy đủ — parent thấy chính mình, child thấy cả hai

Demo sau dùng **Spring Framework 6.2.18** và **Java 17**. Mục tiêu là trực tiếp quan sát:

1. Bean `rootOnly` chỉ tồn tại trong parent nhưng child lookup được.
2. Bean `childOnly` chỉ tồn tại trong child; parent không lookup được.
3. Parent và child đều có bean tên `sharedResource`; child được ưu tiên khi lookup từ child.
4. Child bean `childProcessor` inject singleton `RootOnly` từ parent.
5. `containsBean`/`containsLocalBean`/`containsBeanDefinition` phân biệt rõ.
6. `getBeansOfType(RootOnly.class)` trên child trả local map rỗng, còn `BeanFactoryUtils.beansOfTypeIncludingAncestors(...)` trả parent bean.
7. Shutdown đóng child trước parent.
8. Kết quả `System.out` cố định, không in identity hashcode hay tên class proxy phụ thuộc runtime.

### 12.1. Cấu trúc dự án

```text
context-hierarchy-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/ContextHierarchyDemo.java
```

### 12.2. settings.gradle

```groovy
rootProject.name = 'context-hierarchy-demo'
```

### 12.3. build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'ContextHierarchyDemo'
}
```

### 12.4. src/main/java/ContextHierarchyDemo.java

```java
import java.util.Map;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ContextHierarchyDemo {

    public record RootOnly(String source) {
    }

    public record ChildOnly(String source) {
    }

    public record SharedResource(String source) {
    }

    public record ChildProcessor(RootOnly rootOnly) {
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("rootOnly")
        public RootOnly rootOnly() {
            return new RootOnly("root");
        }

        @Bean("sharedResource")
        public SharedResource sharedResource() {
            return new SharedResource("parent");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("childOnly")
        public ChildOnly childOnly() {
            return new ChildOnly("child");
        }

        @Bean("sharedResource")
        public SharedResource childSharedResource() {
            return new SharedResource("child");
        }

        @Bean("childProcessor")
        public ChildProcessor childProcessor(RootOnly rootOnly) {
            return new ChildProcessor(rootOnly);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext()) {

            parent.register(ParentConfig.class);
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                RootOnly rootFromParent =
                        parent.getBean("rootOnly", RootOnly.class);
                RootOnly rootFromChild =
                        child.getBean("rootOnly", RootOnly.class);

                System.out.println("parent.root="
                        + rootFromParent.source());
                System.out.println("child.seesRoot="
                        + child.containsBean("rootOnly"));
                System.out.println("child.localRoot="
                        + child.containsLocalBean("rootOnly"));
                System.out.println("child.rootDefinition="
                        + child.containsBeanDefinition("rootOnly"));
                System.out.println("sameRootSingleton="
                        + (rootFromParent == rootFromChild));

                System.out.println("parent.shared="
                        + parent.getBean(
                                "sharedResource",
                                SharedResource.class).source());
                System.out.println("child.shared="
                        + child.getBean(
                                "sharedResource",
                                SharedResource.class).source());

                System.out.println("parent.seesChild="
                        + parent.containsBean("childOnly"));
                System.out.println("child.seesChild="
                        + child.containsBean("childOnly"));

                System.out.println("child.injectedParent="
                        + (child.getBean(ChildProcessor.class).rootOnly()
                                == rootFromParent));

                Map<String, RootOnly> childLocal =
                        child.getBeansOfType(RootOnly.class);
                Map<String, RootOnly> childWithAncestors =
                        BeanFactoryUtils.beansOfTypeIncludingAncestors(
                                child, RootOnly.class);

                System.out.println("list.localRootCount="
                        + childLocal.size());
                System.out.println("list.withAncestorsRootCount="
                        + childWithAncestors.size());

                System.out.println("parent.isParentOfChild="
                        + (child.getParent() == parent));

                try {
                    parent.getBean("childOnly", ChildOnly.class);
                    throw new AssertionError(
                            "Parent unexpectedly found child bean");
                } catch (NoSuchBeanDefinitionException expected) {
                    System.out.println("parent.lookupChild=NOT_FOUND");
                }
            }
        }
    }
}
```

### 12.5. Chạy

Cần **JDK 17+** và **Gradle 8+**:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu đã tạo Gradle Wrapper.
```

### 12.6. Kết quả System.out dự kiến

```text
parent.root=root
child.seesRoot=true
child.localRoot=false
child.rootDefinition=false
sameRootSingleton=true
parent.shared=parent
child.shared=child
parent.seesChild=false
child.seesChild=true
child.injectedParent=true
list.localRootCount=0
list.withAncestorsRootCount=1
parent.isParentOfChild=true
parent.lookupChild=NOT_FOUND
```

Đây là những dòng **chương trình tự in ra**. Logging của Spring/Gradle (nếu có) không thuộc output dự kiến.

### 12.7. Từng kết quả có ý nghĩa gì?

- `parent.root=root`: bean `rootOnly` được khai báo trong parent.
- `child.seesRoot=true`: `containsBean` của child có thể hỏi parent.
- `child.localRoot=false`: `containsLocalBean` không nhìn ancestor.
- `child.rootDefinition=false`: bean definition `rootOnly` không nằm trong registry của child.
- `sameRootSingleton=true`: parent singleton instance được cả hai contexts nhìn thấy, không có clone riêng cho child.
- `parent.shared=parent`: lookup từ parent lấy own bean `sharedResource`.
- `child.shared=child`: bean cùng name ở child che parent bean khi lookup từ child.
- `parent.seesChild=false`: lookup của parent không dò ngược về child.
- `child.seesChild=true`: local bean trong child tồn tại.
- `child.injectedParent=true`: factory method `childProcessor(RootOnly rootOnly)` được Spring autowire parent bean.
- `list.localRootCount=0`: `ListableBeanFactory.getBeansOfType` chỉ tìm child-local beans.
- `list.withAncestorsRootCount=1`: `BeanFactoryUtils` mở rộng tìm kiếm tới parent.
- `parent.isParentOfChild=true`: `setParent` đã gắn hierarchy.
- `parent.lookupChild=NOT_FOUND`: `getBean` trên parent không lấy bean `childOnly` mà chỉ được đăng ký trong child.

Ví dụ **không** dùng `@ComponentScan`; toàn bộ bean definitions được khai báo qua `@Bean`. Cách này tránh ảnh hưởng từ những classes khác trên classpath và giữ output ổn định.

## 13. Parent–child và lifecycle ownership

Parent và child là **hai containers có lifecycle riêng**.

- Bean chỉ khai báo ở parent do parent tạo, quản lý scope và callbacks.
- Child gọi `getBean` tới parent không chuyển quyền sở hữu bean sang child.
- Child đóng không nên được hiểu là “destroy toàn bộ parent singletons”.
- Parent đóng trong khi child còn chạy có thể làm những bean mà child tham chiếu không còn khả dụng, đặc biệt resources có vòng đời như connection pools.
- Thường đóng **child trước parent**, như thứ tự `try-with-resources` trong demo.
- Không để context con nắm giữ parent singleton sau khi parent đã đóng mà không có contract lifecycle hợp lệ.

Một singleton bean ở parent có thể được **chia sẻ** cho nhiều children. Nó phải có thread-safety phù hợp nếu nhiều child flows truy cập đồng thời. Scope `singleton` trong Spring là singleton **theo bean factory/bean definition**, không có nghĩa một instance toàn JVM ở mọi independent contexts.

Nếu mỗi child tự định nghĩa bean cùng type nhưng khác name, đó là các instances độc lập; parent sẽ không tự thu thập các instances đó.

## 14. Sibling contexts không nhìn thấy nhau

Một parent có thể có nhiều children:

```text
                  ROOT
                 /    \
                /      \
          CHILD-API    CHILD-JOBS
          apiHandler   jobScheduler
```

Cả hai children nhìn `ROOT.sharedService`. Nhưng API context không thể tự gọi:

```java
apiContext.getBean("jobScheduler");
```

nếu `jobScheduler` chỉ thuộc `jobsContext`. Lookup path của API context là `API → ROOT → ROOT.parent...`, **không phải** `API → ROOT → JOBS`.

Nếu hai modules cần cộng tác:
- Đưa **contract chung** và service interface ở parent/shared library.
- Hoặc định nghĩa một adapter/event/broker có boundary rõ ràng.
- Hoặc kiến trúc lại dependency direction để module cần dùng nằm tại ancestor phù hợp.
- Không phá tính độc lập bằng cách từ parent cố dò mọi descendants rồi giữ references tùy tiện.

### Parent context không quản lý danh sách children tổng quát

API `ApplicationContext.getParent()` chỉ trỏ lên parent. Không có một API chuẩn của BeanFactory bảo đảm parent tự biết mọi children và có thể enumerate beans của chúng. Một framework cụ thể có thể có registry riêng cho web servlets/modules, nhưng đó không phải quy tắc lookup cơ sở của Spring.

## 15. Bean name shadowing và nhiều beans cùng type

Có hai dạng gây nhầm:

### Cùng tên

```text
Parent:
  @Bean("client") Client parentClient

Child:
  @Bean("client") Client childClient
```

`child.getBean("client")` lấy **childClient**. Parent vẫn lấy parentClient. `BeanFactoryUtils.*IncludingAncestors` cũng xử lý name shadowing khi gộp danh sách.

### Khác tên, cùng type

```text
Parent:
  @Bean("primaryClient") Client one

Child:
  @Bean("backupClient") Client two
```

Cả hai có thể là type candidates trong một dependency lookup từ child. Đối với autowiring một `Client`, có thể cần `@Qualifier("backupClient")` hoặc chiến lược `@Primary` phù hợp. **Đừng suy rằng child tự che toàn bộ beans cùng type ở parent**; shadowing theo **bean name** mới là quy tắc nền tảng.

`@Primary`/`@Qualifier` không thay đổi hướng lookup của parent: dù parent có `@Primary` và child có bean đúng type, parent vẫn không tự trông xuống child để inject.

Trong cấu hình nhiều modules, đặt names rõ ràng và dùng constructor injection vào abstractions giúp giảm ambiguity.

## 16. Profile, property sources và Environment trong hierarchy

Parent–child không chỉ ảnh hưởng BeanFactory. `ConfigurableApplicationContext.setParent(parent)` có thể làm **child Environment merge với parent Environment** nếu parent dùng `ConfigurableEnvironment`.

Theo Javadoc `ConfigurableEnvironment.merge(parent)`:
- Ghép active/default profiles của parent vào child, tránh trùng.
- Thêm property sources của parent vào cuối collections của child, trừ sources trùng tên đã tồn tại ở child.
- **Child property source có cùng name ưu tiên**, và child-specific property sources có thể có precedence cao hơn parent được thêm sau.
- Parent Environment **không bị sửa**.
- Đây không phải cơ chế liên kết mọi thay đổi động của Environment sau này: chỉnh parent property source/profile sau khi merge không mặc nhiên làm child phản ánh thay đổi đó.

Vì thế:

**Quy tắc “con nhìn thấy cha” của beans không đồng nhất với cơ chế Environment.** BeanFactory sử dụng delegation lookup; Environment có **merge** của profiles/property sources, và precedence/copy timing có semantics riêng.

Một mảnh minh họa:

```java
parent.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
                "rootConfig",
                Map.<String, Object>of("app.region", "north")));

child.setParent(parent);
```

Đây là mảnh Java minh họa, cần imports `Map`/`MapPropertySource` và contexts. Cấu hình parent nên hoàn tất **trước child.setParent(...)` và child.refresh()`**. Nếu child có property source cùng tên `rootConfig` với nội dung khác, property source của child có thể được giữ khi merge.

Không dùng Environment properties để “kéo” child bean ngược lên parent; đó là hai hệ thống khác nhau.

## 17. MessageSource cũng có fallback lên parent

`ApplicationContext` đồng thời triển khai `MessageSource` (i18n). Khi child không có message code, nó có thể ủy quyền cho parent's MessageSource, tùy cấu hình `HierarchicalMessageSource`.

Ví dụ:

```java
String text = child.getMessage(
        "app.title",
        null,
        "Untitled",
        Locale.ENGLISH);
```

Đây là mảnh minh họa, cần `Locale` và message sources thích hợp. Không nhầm:
- **Bean lookup:** tìm bean name qua parent BeanFactory.
- **Message resolution:** tra message code, locale, resource bundles và fallback tới parent MessageSource.

Child có thể có bean tên `messageSource` riêng và parent cũng có, nhưng Spring gắn chúng theo message hierarchy khi strategy hỗ trợ. Đây **không phải** việc parent có thể lấy bean của child.

Nếu không có message source cục bộ, Spring có thể dùng `DelegatingMessageSource` để chuyển message resolution lên parent. Người dùng web có thể cần locale fallback, khác bean shadowing policy.

## 18. ApplicationEvent propagation khác chiều bean lookup?

Spring `AbstractApplicationContext` có cơ chế publish event trong context hiện tại rồi propagate event **từ child lên parent**.

```text
Child publishes DomainEvent
     |
     +--> child listeners
     |
     v
   Parent ApplicationContext
     |
     +--> parent listeners
```

Điều này **không có nghĩa parent nhìn thấy bean definitions của child**. Nó chỉ có nghĩa child **chủ động chuyển một event lên parent publisher**.

Ngược lại, một event publish tại parent không tự broadcast xuống tất cả children. Nếu parent có listener broad type `ApplicationEvent`, nó có thể nhận `ContextRefreshedEvent` của child và nhầm đó là refresh của parent. Có thể filter `event.getApplicationContext()` để phân biệt lifecycle events.

Vì event propagation và bean lookup đều nói tới “cha/con” nhưng thực hiện những việc khác nhau, hãy diễn đạt riêng trong tài liệu thiết kế:
- **Bean visibility:** child lookup lên parent, parent không lookup xuống child.
- **Event propagation:** child published event có thể lan lên parent listeners, không mặc định lan xuống child.
- **Message fallback:** child có thể resolve message của parent.
- **Environment merge:** child nhận property sources/profiles của parent theo merge timing.

## 19. Hạ tầng BeanPostProcessor và BeanFactoryPostProcessor

Một BeanFactoryPostProcessor/BeanPostProcessor thuộc **một BeanFactory nhất định**. Không mặc định các processors đăng ký ở parent áp dụng cho bean definitions hoặc instances trong child.

Ví dụ parent có bean post-processor custom thêm proxy cho `RootService` ở parent. Điều đó không có nghĩa cùng processor tự chạy trên `ChildProcessor` tạo tại child. Nếu child cần behavior đó, đăng ký processor/infrastructure phù hợp **ở child**, hoặc dùng một ApplicationContext configuration được chia sẻ rõ ràng và đăng ký riêng ở từng context.

Phân biệt:
- Child có thể **sử dụng** một singleton `RootService` đã được parent tạo và post-process.
- Child **không tự áp dụng lại** các parent BeanPostProcessors lên object đó.
- Parent cũng không tự post-process `ChildOnly` bean.

Đây là một trong những hệ quả lớn của việc chia ứng dụng thành nhiều contexts: không chỉ lookup mà cả lifecycle, scopes và interception infrastructure đều có boundaries.

## 20. Những lỗi phổ biến khi tạo hierarchy

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Child `getBean("rootService")` fail | Chưa `setParent` hoặc parent không có bean đó | Liên kết parent trước refresh; kiểm tra parent |
| Parent `getBean("childService")` fail | Parent không có lookup ngược xuống child | Đổi hướng dependency/đặt abstraction ở parent |
| Child `getBeansOfType(RootService.class)` rỗng | API ListableBeanFactory local-only | Dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors` |
| `child.containsBean("rootService")` true nhưng `containsLocalBean` false | Bean chỉ ở parent | Hành vi đúng, không phải lỗi |
| Child lấy nhầm implementation parent | Child chưa đăng ký bean cùng name hoặc qualifier/selection không đúng | Kiểm tra name và config |
| Child che parent bean ngoài ý định | Duplicate bean names across contexts | Đặt tên rõ hoặc dùng `@Qualifier` khi appropriate |
| Child local bean cùng name nhưng type khác, getBean(name,type) lỗi | Local name shadowing | Không tái sử dụng name sai type |
| Một singleton có state dùng chung ngoài ý muốn | Parent singleton dùng bởi nhiều children | Thiết kế thread-safety, scope hoặc chuyển bean xuống child |
| Parent refresh fail vì dependency của child | Parent khai báo injection ngược | Đặt dependency trong parent/contract chung |
| Sibling A không tìm thấy bean của sibling B | Hierarchy chỉ đi qua ancestor | Chia sẻ qua parent/abstraction phù hợp |
| Child property không giống parent | Environment merge precedence/timing | Cấu hình trước `setParent`, kiểm tra `PropertySources` |
| Parent event listener xử lý cả child events | Events có thể propagate lên parent | Filter event source/context |
| Child shutdown làm parent resource bị dùng sai | Lẫn lifecycle ownership | Đóng child trước parent, quản lý resource rõ |
| Parent BPP không chạy trên child beans | Processor local to BeanFactory | Đăng ký đúng infrastructure tại child |
| Test pass khi tạo một context nhưng fail khi tách | Bean exposure khác context hierarchy | Integration test parent/child thực |

## 21. Cách kiểm thử đúng

**Unit test** có thể kiểm tra một service nhận dependency qua constructor, nhưng không chứng minh Spring hierarchy lookup hoạt động. Để test parent–child thật, tạo **hai ApplicationContexts** và đặt parent trước khi refresh child.

Các assertions nên có:

1. `child.getParent() == parent`.
2. Parent có bean `rootOnly`.
3. Child `getBean("rootOnly")` trả **cùng reference** parent singleton.
4. Child `containsBean("rootOnly") == true` và `containsLocalBean("rootOnly") == false`.
5. `child.getBeansOfType(RootOnly.class)` rỗng khi chỉ có parent bean.
6. `BeanFactoryUtils.beansOfTypeIncludingAncestors(child,RootOnly.class)` trả một entry.
7. Parent không thấy `childOnly` qua `getBean` hoặc `containsBean`.
8. Parent và child cùng name `sharedResource`, lookup từ mỗi bên nhận implementation tương ứng.
9. Child bean có constructor/factory method parameter `RootOnly` được autowire từ parent.
10. Child đóng trước parent, và chỉ parent đóng hủy parent-owned resource.
11. Sibling A và sibling B không tự thấy local-only beans của nhau.
12. Các bean name clashes, type ambiguity và qualifiers được kiểm thử rõ ràng.

Ví dụ assertion từ demo mục 12:

```java
if (!child.containsBean("rootOnly")) {
    throw new AssertionError("Expected ancestor bean");
}

if (child.containsLocalBean("rootOnly")) {
    throw new AssertionError("Ancestor bean is not child-local");
}

if (parent.containsBean("childOnly")) {
    throw new AssertionError("Parent must not see child beans");
}
```

Đây là mảnh code nằm trong scope của hai contexts, không phải một class độc lập.

Với JUnit, có thể dùng `assertSame` cho parent singleton và child lookup. Không dùng so sánh bằng `equals` để chứng minh cùng instance nếu record/object có value-based equality.

## 22. Thiết kế nhiều tầng và module boundaries

Ứng dụng phức tạp đôi khi có:

```text
Organization Root
      |
      v
Tenant Context
      |
      v
Feature Context
```

Một bean ở `Feature Context` có thể dùng bean của `Tenant Context` và `Organization Root` theo chuỗi lookup. Nhưng thiết kế nhiều tầng tăng nguy cơ:

- Trùng bean names và shadowing khó nhận biết.
- Resource scope/lifecycle không rõ.
- Cùng một type có nhiều candidates từ nhiều tầng.
- Environment/profile merge gây bất ngờ.
- BeanPostProcessor/AOP/transaction infrastructure khác nhau giữa contexts.
- Event parent propagation tạo callbacks ngoài dự kiến.

Thực hành tốt:

- Giới hạn chiều sâu hierarchy khi không cần thiết.
- Ưu tiên **constructor injection + interface contracts** thay vì lấy ApplicationContext ở mọi bean.
- Đặt phần **thật sự chia sẻ** lên parent, phần **cục bộ** xuống child.
- Không đưa bean phụ thuộc child vào parent; nếu cần giao tiếp hai chiều, dùng abstraction/event/callback registration có quyền sở hữu rõ.
- Tránh trùng bean names giữa parent và child trừ khi **cố tình override visibility**.
- Với multi-tenant, không dùng một parent singleton có mutable tenant-specific state khi tenant isolation là yêu cầu bắt buộc.
- Test từng boundary và shutdown lifecycle.

## 23. So sánh hierarchy với @Import và component scanning

Một số developers tạo nhiều contexts vì muốn tách cấu hình, nhưng thực tế có thể không cần hierarchy.

**`@Import(SomeConfig.class)`**: thêm configuration vào **cùng một ApplicationContext**. Bean definitions nằm chung registry, cùng lifecycle và scope boundary. Không có “cha/con” chỉ vì các Java config classes import nhau.

**`@ComponentScan`**: quét components vào **cùng context** đang xử lý scan; package structure không tự sinh parent–child contexts.

**Parent–child ApplicationContexts**: tạo **hai containers thật sự**, mỗi container có registry, BPP chain, lifecycle và local singleton cache riêng. Bean lookup của child có ancestor fallback; parent không thể tự lookup descendant beans.

Chỉ chọn hierarchy khi cần boundary thật: web root vs servlet-specific contexts, module isolation, tenant-level scopes hoặc phần ứng dụng cần cấu hình/lifecycle riêng. Với một ứng dụng Spring Boot thông thường, nhiều module Java vẫn có thể chạy tốt trong một context mà không cần hierarchy.

## 24. Quy trình debug vấn đề “không nhìn thấy bean”

Khi `NoSuchBeanDefinitionException` xuất hiện:

1. **Xác định context nào đang thực hiện lookup.** Đừng chỉ nhìn tên class bean.
2. Kiểm tra `context.getParent()` và cây parent references.
3. Dùng `containsLocalBean(name)` để xem bean ở local hay ancestor.
4. Dùng `containsBean(name)` để kiểm tra khả năng lookup theo name.
5. Dùng `getBeanDefinitionNames()` nếu cần liệt kê local definitions.
6. Dùng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(...)` nếu cần type candidates ở toàn hierarchy.
7. Kiểm tra trùng bean names gây shadowing.
8. Kiểm tra type mismatch/`@Qualifier`/`@Primary` khi inject theo type.
9. Kiểm tra `@Profile`/`@Conditional` có khiến bean definition bị loại không.
10. Kiểm tra thứ tự tạo parent, `setParent` và `refresh`.
11. Kiểm tra bean đang nằm trong sibling context thay vì ancestor.
12. Với phụ thuộc parent→child, refactor cấu trúc thay vì cố “refresh lại” để đổi hướng.

Đừng tìm cách “fix” bằng cách cho tất cả bean thành static singleton toàn JVM, vì như vậy mất isolation và lifecycle container semantics.

## 25. Tài liệu chính thức

1. [Spring Framework 6.2 Javadoc — ApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationContext.html): hierarchy và quy tắc definitions của descendant ưu tiên ancestor.
2. [Spring Framework 6.2 Javadoc — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactory.html): fallback lên parent và local name shadowing.
3. [Spring Framework 6.2 Javadoc — HierarchicalBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html): `containsLocalBean` và `getParentBeanFactory`.
4. [Spring Framework 6.2 Javadoc — ListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html): local enumeration semantics.
5. [Spring Framework 6.2 Javadoc — BeanFactoryUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html): `beansOfTypeIncludingAncestors`, `beanNamesForTypeIncludingAncestors` và name shadowing.
6. [Spring Framework 6.2 Javadoc — ConfigurableApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ConfigurableApplicationContext.html): `setParent` và lifecycle.
7. [Spring Framework 6.2 Javadoc — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): liên kết parent BeanFactory.
8. [Spring Framework 6.2 Javadoc — AbstractApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html): message source fallback, event propagation và context lifecycle.
9. [Spring Framework 6.2 Javadoc — ConfigurableEnvironment](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html): `merge` và precedence property sources/profiles.
10. [Spring Framework Reference — Additional Capabilities of the ApplicationContext](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): context hierarchy, i18n và application events.

**Ghi nhớ:** Spring parent–child hierarchy là **tra cứu một chiều**: child dùng được bean ở parent nếu không bị local name shadow, nhưng parent không tự tra cứu bean của child. `BeanFactory.getBean` và `containsBean` có thể delegate lên ancestor, còn các API `ListableBeanFactory` liệt kê local-only. Đây là hai hành vi bổ sung, không mâu thuẫn. Tổ chức parent chứa tài nguyên chung, child chứa phần mở rộng riêng và giữ dependency direction rõ ràng sẽ giúp tránh các lỗi visibility, lifecycle, scope và cấu hình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-003">Tên bean ở context con che khuất bean của cha</a>

<details>
<summary>Xem chi tiết</summary>

# Tên bean ở context con che khuất bean của cha trong Spring Framework

## 1. Vấn đề cốt lõi

Spring Framework cho phép tổ chức `ApplicationContext` theo cấu trúc **cha – con (parent–child)**. Hai context có BeanFactory riêng, nhưng context con có thể tìm những beans ở context cha.

Nếu **cùng một bean name tồn tại ở cả hai cấp**:

~~~text
Parent ApplicationContext
  service -> GreetingService("parent")

Child ApplicationContext (parent = Parent)
  service -> GreetingService("child")
~~~

thì:

~~~java
parent.getBean("service"); // Parent bean
child.getBean("service");  // Child bean
~~~

Bean `service` của child **che khuất (shadows/hides)** bean cùng tên ở parent khi lookup khởi đầu tại child. Nhưng parent bean **không bị xóa hoặc ghi đè**; nó vẫn được parent quản lý.

> **Quy tắc nhớ:** Tra cứu từ context nào thì ưu tiên bean local của context đó. Chỉ khi không có local bean phù hợp cho việc tra cứu theo tên, lookup mới chuyển lên ancestor. Parent không tự nhìn thấy bean riêng trong child.

Bài này giải thích trực tiếp chủ đề **“Tên bean ở context con che khuất bean của cha”**, thuộc nhóm *Phân cấp context* của Spring Core Container. Tài liệu có ví dụ runnable bằng **Java 17 + Spring Context 6.1.13**, nêu rõ hành vi khi trùng tên cùng type, trùng tên khác type, dependency injection và type enumeration.

## 2. Context hierarchy vận hành như thế nào?

Một ứng dụng có thể tổ chức:

~~~text
Root / Parent Context
    shared database configuration
    shared services
       |
       v
Child Context A             Child Context B
    module A controllers        module B controllers
    module A services           module B services
~~~

Hai child contexts cùng nhìn lên parent nhưng không tự nhìn thấy bean riêng của nhau. Parent cũng không tự có khả năng lookup xuống child.

Trong Spring Java configuration, tạo parent trước:

~~~java
AnnotationConfigApplicationContext parent =
    new AnnotationConfigApplicationContext(ParentConfig.class);

AnnotationConfigApplicationContext child =
    new AnnotationConfigApplicationContext();

child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
~~~

Đây là **snippet**, cần import `AnnotationConfigApplicationContext` và có hai config classes tương ứng.

**Quan trọng:** `setParent(parent)` nên được gọi **trước `refresh()`** của child. Từ đầu vòng đời, bean creation và infrastructure setup cần nhìn đúng hierarchy.

### Hướng tìm kiếm

~~~text
child.getBean("name")
    |
    +-- Child có bean local tên name? -> trả local
    |
    +-- Nếu không -> tìm ở parent
                           |
                           +-- Parent có local name? -> trả parent
                           |
                           +-- Nếu không -> tiếp tục ancestor
~~~

Lập luận trên dành cho **tra cứu theo bean name**. API tra theo type và các thao tác liệt kê beans có thêm quy tắc riêng, sẽ được trình bày bên dưới.

## 3. Bean cùng tên, cùng type

Parent:

~~~java
@Configuration
class ParentConfig {

    @Bean("service")
    GreetingService parentService() {
        return new GreetingService("parent");
    }
}
~~~

Child:

~~~java
@Configuration
class ChildConfig {

    @Bean("service")
    GreetingService childService() {
        return new GreetingService("child");
    }
}
~~~

Hai đoạn này là **các snippets minh họa**, cần class `GreetingService` và imports Spring `Bean`/`Configuration`.

Nếu child đã được gắn parent và refresh đúng thứ tự:

~~~java
GreetingService fromParent =
    parent.getBean("service", GreetingService.class);

GreetingService fromChild =
    child.getBean("service", GreetingService.class);
~~~

Giá trị của `fromParent` đến từ parent configuration, còn `fromChild` đến từ child configuration.

### Không tự cập nhật parent

Child không đăng ký lại bean vào parent BeanFactory. Nó chỉ có **định nghĩa local cũng tên**. Parent vẫn có instance của mình và bất kỳ service nào do parent tạo trước đó vẫn có thể tiếp tục sử dụng reference đó.

Nếu có thêm child thứ hai **không** định nghĩa `service` local, child thứ hai vẫn có thể kế thừa `service` của parent. Shadowing của một child không ảnh hưởng tới những child khác.

## 4. Trùng tên nhưng khác class vẫn che khuất

Giả sử:

~~~text
Parent:
  hiddenType -> ParentSpecial

Child:
  hiddenType -> ChildReplacement
~~~

Hai beans này có types hoàn toàn khác nhau. Nhưng **tên** là `hiddenType` ở cả hai.

Tra từ child:

~~~java
Object local = child.getBean("hiddenType");
~~~

lấy `ChildReplacement`. Và:

~~~java
child.getBean("hiddenType", ParentSpecial.class);
~~~

có thể ném `BeanNotOfRequiredTypeException`, **không** tự bỏ qua child để tìm bean của parent đúng type.

Tra trực tiếp từ parent:

~~~java
ParentSpecial original =
    parent.getBean("hiddenType", ParentSpecial.class);
~~~

vẫn thành công.

### Đây là lỗi thiết kế hay gặp

Một nhóm developer thay một bean ở child bằng implementation khác nhưng dùng cùng tên. Nếu cả hai cùng interface, hệ thống có thể âm thầm lấy child version. Nếu types không tương thích, nó thường bộc lộ thành type mismatch lúc runtime.

**Lựa chọn an toàn:** nếu child không có ý định thay implementation của bean parent, dùng tên khác nhau hoặc naming convention rõ ràng.

## 5. Shadowing khác overriding cùng BeanFactory

Hai khái niệm cần phân biệt nghiêm ngặt:

| Tiêu chí | Shadowing trong hierarchy | BeanDefinition overriding |
| --- | --- | --- |
| Có bao nhiêu BeanFactory? | Ít nhất hai | Một |
| Cùng tên ở đâu? | Một tại parent, một tại child | Hai definitions tranh cùng tên trong một registry |
| Parent bean còn tồn tại? | Có | Không có khái niệm parent riêng |
| Quy tắc chính | Lookup local-first | Chính sách duplicate registration |
| `allowBeanDefinitionOverriding` | Không phải cơ chế chọn bean cha/con | Liên quan trực tiếp |
| Lấy phiên bản cũ | Tra trực tiếp parent | Tùy registry/overriding policy |

### Ví dụ hai factories

~~~text
Parent registry:
  service -> A

Child registry:
  service -> B
~~~

Cả hai definitions vẫn được lưu ở hai nơi.

### Ví dụ cùng một factory

~~~text
One registry:
  service -> A
  service -> B
~~~

Spring phải xét chính sách có cho đăng ký trùng tên để ghi đè hay không. Spring Boot còn có các defaults riêng theo phiên bản. Điều đó khác với parent–child bean name masking.

**Đừng bật `spring.main.allow-bean-definition-overriding` chỉ để cố giải quyết hierarchy shadowing** trước khi biết bean definitions đang nằm trong cùng factory hay ở hai factories.

## 6. Không nhầm với BeanDefinition inheritance

Spring còn có tính năng *bean definition inheritance*, ví dụ XML:

~~~xml
<bean id="base" abstract="true"
      class="com.example.ServiceSettings">
    <property name="timeout" value="30"/>
</bean>

<bean id="concrete" parent="base"/>
~~~

Đây là snippet XML **minh họa**; cần class, setters và XML container cấu hình phù hợp.

`parent="base"` ở đây là **quan hệ giữa hai BeanDefinitions**, cho phép con kế thừa metadata của definition cha.

Trong bài này, parent–child **ApplicationContext** là quan hệ giữa **containers**. Bean cùng tên ở child **che đường tra cứu**, chứ không kế thừa property values của bean cùng tên từ parent.

## 7. Khi child không có tên được hỏi

Giả sử:

~~~text
Parent:
  service
  parentOnly

Child:
  service
  childOnly
~~~

Từ child:

~~~java
child.getBean("parentOnly");
~~~

sẽ tìm lên parent nếu child không có bean `parentOnly` local.

Từ parent:

~~~java
parent.getBean("childOnly");
~~~

không tự tìm xuống child và thường ném `NoSuchBeanDefinitionException`.

### Tại sao hierarchy một chiều hữu ích?

Root context có thể tạo các shared dependencies; module contexts tiêu thụ chúng mà không cần root phụ thuộc các module. Khi module được gỡ, root không cần biết toàn bộ cấu trúc module.

Điều kiện là bạn **không vô tình đặt một bean cùng tên** ở child khiến shared bean bị che.

## 8. Kiểm tra name shadowing mà không nhầm ownership

Giả sử bean `parentOnly` chỉ có ở context cha:

```java
child.containsBean("parentOnly");           // true
child.containsLocalBean("parentOnly");      // false
child.containsBeanDefinition("parentOnly"); // false
```

Ở đây `containsBean` tra visibility theo tên; `containsLocalBean` hỏi local ownership, còn `containsBeanDefinition` chỉ tra local registry metadata. Một singleton local được đăng ký thủ công có thể có `containsLocalBean=true` nhưng không có definition tương ứng. Để điều tra lỗi che khuất, xác định **tên bean ở từng context** trước khi xét type.

## 9. Liệt kê bean theo type không tự bao gồm parent

`child.getBeansOfType(Service.class)` và `child.getBeanNamesForType(Service.class)` là các phép liệt kê **local** của `ListableBeanFactory`. Nếu cần danh sách có xét tổ tiên, dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, Service.class)` (với imports và class `Service` phù hợp). Tuy nhiên helper này không phải phép cộng trơn hai danh sách: một local bean cùng tên sẽ che entry của parent.

## 10. Bẫy quan trọng: trùng **name** có thể che bean đúng **type** ở parent

```text
Parent: hiddenType -> ParentSpecial
Child : hiddenType -> ChildReplacement

BeanFactoryUtils.beansOfTypeIncludingAncestors(
    child, ParentSpecial.class)
    -> không trả ParentSpecial dưới tên hiddenType
```

Vì local child đã sở hữu `hiddenType`, helper không đưa ancestor entry cùng tên vào map—even khi `ChildReplacement` **không** implement `ParentSpecial`. Đây là kết quả name masking, không phải bean parent đã bị destroy. Muốn inventory tất cả instances thật, liệt kê **từng context riêng** kèm origin; muốn lấy parent bean có chủ đích, giữ reference tới parent và lookup trên nó.

## 11. Lấy một bean không giống tạo map bean

`child.getBean(Service.class)` chọn một bean theo type/candidate resolution và có thể thất bại khi thiếu hoặc mơ hồ; `child.getBeansOfType(Service.class)` liệt kê local candidates; `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, Service.class)` tạo map khả kiến qua ancestors. **Dùng đúng API mà production thực sự sử dụng** để test.

Đặc biệt, `@Primary` không vượt quy tắc **named lookup**: nếu child có bean tên `service`, `child.getBean("service")` không lấy parent bean cùng tên chỉ vì parent đánh dấu `@Primary`.

## 12. @Qualifier có giải quyết bean name shadowing không?

Một method:

~~~java
@Bean
public Consumer consumer(
        @Qualifier("service") GreetingService service) {
    return new Consumer(service);
}
~~~

Đây là snippet minh họa cần class `Consumer`, `GreetingService` và imports `Bean`, `Qualifier`.

`@Qualifier("service")` hỗ trợ narrowing dependency candidate theo qualifier/name. Nhưng nó **không** có nghĩa “luôn lấy bean `service` của parent”. Khi consumer được tạo trong child và child có local bean cùng tên, local bean có ưu tiên theo hierarchy.

### Consumer ở parent

Một bean được **parent** tạo và inject dependency sẽ dùng candidates của parent; nó không tự nhìn xuống child. Vì thế parent consumer vẫn dùng parent service dù sau đó child có service cùng tên.

### Consumer ở child

Một bean được **child** tạo có thể nhận local service cùng tên `service`. Nếu child không có local service, nó có thể lấy service từ parent theo dependency resolution phù hợp.

**Kết luận:** Khi cần lựa chọn implementation theo module, đặt consumer và bean implementation ở đúng context, đừng kỳ vọng qualifier sẽ chỉ định được một cấp context mà không thiết kế rõ.

## 13. Ví dụ runnable Java 17 + Spring Context 6.1.13

Ví dụ sau dùng **hai AnnotationConfigApplicationContext thật** và các ordinary beans. Chương trình thể hiện:

1. Bean `service` ở cả cha và con, cùng class nhưng khác giá trị.
2. Bean `parentOnly` chỉ ở cha được child nhìn thấy.
3. Bean `childOnly` chỉ ở con.
4. Consumer tạo tại cha nhận parent service; consumer tạo tại con nhận child service.
5. Bean `hiddenType` ở hai cấp **cùng tên khác class**.
6. Lookup theo tên + type sai báo `BeanNotOfRequiredTypeException`.
7. `containsLocalBean` khác `containsBean` và `containsBeanDefinition`.
8. `BeanFactoryUtils` che ancestor bean cùng tên kể cả khi khác type.
9. Lifecycle đóng con trước cha bằng try-with-resources.

### 13.1. Cấu trúc

~~~text
context-shadowing-demo/
├── pom.xml
└── src/main/java/com/example/hierarchy/ShadowingDemo.java
~~~

### 13.2. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>context-shadowing-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
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
                <configuration>
                    <mainClass>com.example.hierarchy.ShadowingDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 13.3. File src/main/java/com/example/hierarchy/ShadowingDemo.java

~~~java
package com.example.hierarchy;

import java.util.Map;
import java.util.TreeSet;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ShadowingDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext parent =
                 new AnnotationConfigApplicationContext(ParentConfig.class);
             AnnotationConfigApplicationContext child =
                 new AnnotationConfigApplicationContext()) {

            child.setParent(parent);
            child.register(ChildConfig.class);
            child.refresh();

            System.out.println("parent.service=" +
                parent.getBean("service", GreetingService.class).message());

            System.out.println("child.service=" +
                child.getBean("service", GreetingService.class).message());

            System.out.println("child.parentOnly=" +
                child.getBean("parentOnly", OnlyParent.class).value());

            System.out.println("child.childOnly=" +
                child.getBean("childOnly", OnlyChild.class).value());

            System.out.println("parentConsumer=" +
                child.getBean("parentConsumer", ConsumerView.class).describe());

            System.out.println("childConsumer=" +
                child.getBean("childConsumer", ConsumerView.class).describe());

            System.out.println("parent.hiddenType=" +
                parent.getBean("hiddenType", ParentSpecial.class).value());

            System.out.println("child.hiddenType=" +
                child.getBean("hiddenType", ChildReplacement.class).value());

            System.out.println("child.local.service=" +
                child.containsLocalBean("service"));

            System.out.println("child.local.parentOnly=" +
                child.containsLocalBean("parentOnly"));

            System.out.println("child.contains.parentOnly=" +
                child.containsBean("parentOnly"));

            System.out.println("child.definition.parentOnly=" +
                child.containsBeanDefinition("parentOnly"));

            System.out.println("child.parent-is-parent=" +
                (child.getParent() == parent));

            boolean wrongType = false;
            try {
                child.getBean("hiddenType", ParentSpecial.class);
            } catch (BeanNotOfRequiredTypeException expected) {
                wrongType = true;
            }
            System.out.println("child.wrong-type-error=" + wrongType);

            System.out.println("child.local-greeting-count=" +
                child.getBeansOfType(GreetingService.class).size());

            Map<String, GreetingService> greetings =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    child, GreetingService.class);

            System.out.println("child.visible-greeting-names=" +
                String.join(",", new TreeSet<>(greetings.keySet())));

            System.out.println("parent.visible-parent-special=" +
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    parent, ParentSpecial.class).size());

            System.out.println("child.visible-parent-special=" +
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                    child, ParentSpecial.class).size());
        }
    }

    public static final class GreetingService {
        private final String message;

        public GreetingService(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public record OnlyParent(String value) {}
    public record OnlyChild(String value) {}
    public record ParentSpecial(String value) {}
    public record ChildReplacement(String value) {}

    public record ConsumerView(
            String name, GreetingService service) {

        public String describe() {
            return name + ":" + service.message();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("service")
        public GreetingService parentService() {
            return new GreetingService("parent");
        }

        @Bean("parentOnly")
        public OnlyParent parentOnly() {
            return new OnlyParent("parent-only");
        }

        @Bean("hiddenType")
        public ParentSpecial parentSpecial() {
            return new ParentSpecial("parent-special");
        }

        @Bean("parentConsumer")
        public ConsumerView parentConsumer(
                @Qualifier("service") GreetingService service) {
            return new ConsumerView("parent-consumer", service);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("service")
        public GreetingService childService() {
            return new GreetingService("child");
        }

        @Bean("childOnly")
        public OnlyChild childOnly() {
            return new OnlyChild("child-only");
        }

        @Bean("hiddenType")
        public ChildReplacement childReplacement() {
            return new ChildReplacement("child-replacement");
        }

        @Bean("childConsumer")
        public ConsumerView childConsumer(
                @Qualifier("service") GreetingService service) {
            return new ConsumerView("child-consumer", service);
        }
    }
}
~~~

### 13.4. Chạy

Yêu cầu Java 17+ và Maven 3.9.x. Ở thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình chủ động in:

~~~text
parent.service=parent
child.service=child
child.parentOnly=parent-only
child.childOnly=child-only
parentConsumer=parent-consumer:parent
childConsumer=child-consumer:child
parent.hiddenType=parent-special
child.hiddenType=child-replacement
child.local.service=true
child.local.parentOnly=false
child.contains.parentOnly=true
child.definition.parentOnly=false
child.parent-is-parent=true
child.wrong-type-error=true
child.local-greeting-count=1
child.visible-greeting-names=service
parent.visible-parent-special=1
child.visible-parent-special=0
~~~

Maven/logging có thể ghi thêm các dòng khác. Project không dùng random values hoặc threads, nên thứ tự các dòng `System.out.println` được kiểm soát bởi method `main`.

### 13.5. Giải thích từng kết quả

**parent.service=parent:** Lookup từ parent chọn bean `service` của parent.

**child.service=child:** Lookup từ child chọn bean `service` của child, che parent.

**child.parentOnly=parent-only:** Không có local `parentOnly` nên child tìm được bean từ parent.

**child.childOnly=child-only:** Child sở hữu bean `childOnly`; parent không biết bean này.

**parentConsumer=parent-consumer:parent:** Parent configuration tạo consumer và inject service trong parent.

**childConsumer=child-consumer:child:** Child configuration tạo consumer và inject local child service.

**parent.hiddenType=parent-special:** Parent bean khác type vẫn tồn tại.

**child.hiddenType=child-replacement:** Child same-name bean thắng khi lookup từ child.

**child.local.service=true:** Child có local bean tên service.

**child.local.parentOnly=false:** Bean parentOnly không phải local trong child.

**child.contains.parentOnly=true:** Hierarchical lookup nhìn thấy parentOnly.

**child.definition.parentOnly=false:** Child registry không chứa BeanDefinition của parentOnly.

**child.parent-is-parent=true:** Context hierarchy được gắn đúng.

**child.wrong-type-error=true:** Lookup theo tên `hiddenType` yêu cầu `ParentSpecial` bị type mismatch tại child, không tự fallback.

**child.local-greeting-count=1:** Chỉ có một local GreetingService instance ở child.

**child.visible-greeting-names=service:** Ancestor-inclusive lookup chỉ giữ child service, bỏ parent service trùng tên.

**parent.visible-parent-special=1:** Parent tự thấy ParentSpecial.

**child.visible-parent-special=0:** ParentSpecial của parent bị child local `hiddenType` che trong `BeanFactoryUtils`, ngay cả khi child bean khác type.

### 13.6. Bài tập mở rộng

**Bài tập A:** Đổi bean child `"service"` thành `"moduleService"`. Bây giờ hai names không trùng. Dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, GreetingService.class)` để xem cả hai names. Nếu childConsumer vẫn `@Qualifier("service")`, nó có thể dùng parent service.

**Bài tập B:** Đổi bean child `"hiddenType"` thành `"localReplacement"`. Parent `hiddenType` không còn bị shadowed; thử `child.getBean("hiddenType", ParentSpecial.class)` và kiểm tra kết quả.

**Bài tập C:** Tạo thêm một child thứ hai không đăng ký service local. Xác nhận child thứ hai dùng parent service, trong khi child thứ nhất vẫn dùng service của mình.

Các bài tập này **thay đổi source**; stdout ở mục 13.4 chỉ áp dụng cho source gốc.

## 14. Singleton scope thuộc về từng BeanFactory

Hai local singleton beans cùng tên trong hai contexts là hai đối tượng:

~~~text
Parent singleton cache:
  "service" -> Object P

Child singleton cache:
  "service" -> Object C
~~~

Tên bean không unique toàn JVM; nó unique trong phạm vi bean registration tương ứng. Child singleton không là “bản sao reference” của parent singleton trừ khi bạn chủ động đăng ký cùng một instance.

### Tác động thực tế

Nếu root giữ connection pool chung và child vô tình định nghĩa một `dataSource` local, child services có thể dùng pool khác. Nếu không có chủ đích, điều này vừa tăng tài nguyên vừa gây khác biệt transaction behavior.

Ngược lại, một module có thể chủ động shadow một implementation interface bằng local service để cô lập hành vi. Dù trường hợp nào cũng cần rõ origin context.

## 15. Parent consumer đã inject sẽ không đổi theo child

Giả sử parent tạo:

~~~text
Parent:
  service=A
  reportService.injectedService=A
~~~

Sau đó child định nghĩa:

~~~text
Child:
  service=B
~~~

Parent reportService vẫn giữ reference A. Shadowing **không phải cơ chế reinjection hoặc hot reload**.

Muốn runtime switching giữa các implementations, hãy đưa policy vào một router/provider/strategy có contract rõ ràng, không phụ thuộc vào việc tạo child context sau khi singleton đã được khởi tạo.

## 16. @Primary không có quyền ưu tiên vượt named shadowing

`@Primary` là một tín hiệu hỗ trợ lựa chọn candidate khi resolution theo type có nhiều lựa chọn. Nhưng:

~~~java
child.getBean("service");
~~~

là lookup theo **tên**. Nếu child có local service, việc parent bean `service` có `@Primary` không khiến lookup từ child chuyển về parent.

### Khi @Primary có ý nghĩa

Nếu có nhiều beans cùng type **với các names khác nhau**, `@Primary` có thể quyết định candidate cho một injection point. `@Qualifier` có thể chọn bean theo qualifier/name. Nhưng đó là bài toán type candidate selection, không phủ định name masking.

**Tránh nhầm** “bean default theo type” với “bean ưu tiên trong hierarchy khi tên trùng”.

## 17. Tra cứu theo type có thể ambiguous

Ví dụ:

~~~text
Parent: "serviceA" -> GreetingService
Child : "serviceB" -> GreetingService
~~~

Khi không trùng tên, cả hai là objects khác nhau có thể được nhìn thấy trong một ancestor-inclusive lookup.

Nhưng gọi:

~~~java
child.getBean(GreetingService.class);
~~~

có thể gặp ambiguity tùy candidates/qualifier/primary policy. Cần test API thực tế và dùng explicit qualifier hoặc bean name khi kiến trúc yêu cầu một implementation cụ thể.

### Không dựa vào registration order

Thứ tự bean definitions hoặc methods trong class không phải quy tắc ổn định để phân giải multiple candidates. Nếu cần chọn một default, dùng `@Primary` hoặc explicit wiring. Nếu cần list, lấy collection phù hợp với scope/ancestor rules.

## 18. Tên bean và aliases

Spring hỗ trợ aliases, và một bean có thể có nhiều tên logic. Ở hierarchy, aliases/bean name transformation có thể ảnh hưởng cách lookup thực tế.

Không nên tạo alias trùng tên giữa parent và child để “lách” shadowing rồi giả định nó sẽ luôn trả parent object. Các trường hợp alias resolution đáng kiểm thử với đúng classloader/factory config.

### FactoryBean và prefix &

Một `FactoryBean` có thể được lấy qua tên bean bình thường (sản phẩm) hoặc special prefix `&` (factory object). Name shadowing và dereference syntax có thể kết hợp theo bean-name transformation của Spring.

Ví dụ ý niệm:

~~~text
getBean("builder")  -> object do FactoryBean tạo
getBean("&builder") -> chính FactoryBean instance
~~~

Đừng nhầm một tên `FactoryBean` với tên của sản phẩm khi chẩn đoán hierarchy conflicts. Ví dụ runnable dùng regular beans để tránh các quy tắc nâng cao không cần thiết.

## 19. Component scanning có thể tạo shadowing vô ý

Bean local có thể được đăng ký bằng:

~~~java
@Bean("service")
GreetingService service() {
    return new GreetingService("child");
}
~~~

hoặc:

~~~java
@Component("service")
class ModuleGreetingService implements GreetingService {
    // ...
}
~~~

Đây là **hai snippets tách biệt**, không nên đồng thời tạo bean cùng tên trong **một** registry nếu không có policy overriding rõ.

Trong một hệ thống chia contexts, component scanning/import phạm vi quá rộng có thể làm child tạo lại hạ tầng vốn chỉ nên tồn tại ở parent.

### Hướng phòng ngừa

- Phân chia package/config modules rõ ràng.
- Root scan infrastructure; child scan đúng feature/module.
- Viết integration test kiểm tra các names trọng yếu.
- Xem các bean cùng tên khác type như cảnh báo nghiêm trọng, trừ khi được document có chủ đích.
- Với shared infrastructure, dùng các bean names ổn định và hạn chế local shadowing.

## 20. Vòng đời đóng context

Parent và child quản lý bean lifecycles riêng.

Trong Java:

~~~java
try (AnnotationConfigApplicationContext parent =
         new AnnotationConfigApplicationContext(ParentConfig.class);
     AnnotationConfigApplicationContext child =
         new AnnotationConfigApplicationContext()) {

    child.setParent(parent);
    child.register(ChildConfig.class);
    child.refresh();

    // Sử dụng contexts.
}
~~~

Vì try-with-resources đóng từ sau về trước, **child đóng trước parent**.

### Vì sao cần như vậy?

Child beans có thể phụ thuộc vào shared services parent. Nếu parent bị đóng trước khi child hoàn tất, những dependencies như datasource hoặc executor có thể đã shutdown.

Khi đóng child, parent bean cùng tên không bị destroy chỉ vì nó đang bị shadowed. Parent vẫn quản lý lifecycle của parent singleton cho đến khi parent đóng.

## 21. BeanPostProcessor và BeanFactoryPostProcessor theo từng context

Các Spring extension points như `BeanFactoryPostProcessor` và `BeanPostProcessor` thường có hiệu lực trong phạm vi BeanFactory/container nơi chúng được đăng ký.

Khi child định nghĩa lại một bean cùng tên:

- Nó không làm parent bean chạy lại init callbacks.
- Nó không làm parent BeanDefinitions bị xử lý lại.
- Nó không tự đổi các dependency đã được inject vào parent beans.
- Nó không có nghĩa mọi BeanPostProcessors từ child cũng xử lý parent singletons.

Đặc biệt đừng dùng bean shadowing để thay infrastructure đã khởi tạo ở parent với kỳ vọng toàn bộ context hierarchy được rebuild.

## 22. Khi nào shadowing là một lựa chọn có chủ đích?

Một số use cases:

1. **Module customization:** Parent cung cấp default interface implementation; child chủ động dùng local variant cùng tên.
2. **Test isolation:** Child context ghi đè cách nhìn bean để test feature mà không đổi parent context.
3. **Multi-tenant modules:** Mỗi child có riêng service implementation nhưng vẫn chia sẻ infrastructure parent.
4. **Extension systems:** Plugin child cung cấp bean thay thế cho một contract rõ ràng ở root.

Điểm chung: code biết rõ bean nào được tạo ở cấp nào và việc lookup bắt đầu từ đâu.

### Khi nào nên chọn cách khác?

Nếu ứng dụng chỉ có một context, không dựng hierarchy chỉ để thay một bean. Có thể dùng `@Profile`, `@Conditional`, `@Primary`, `@Qualifier` hoặc explicit constructor wiring theo nhu cầu.

Nếu phải thay đổi implementation tại runtime cho một singleton đã tồn tại, cân nhắc provider/strategy design; shadowing không tự cập nhật dependencies cũ.

## 23. Spring Boot và context hierarchy

Spring Boot thường tạo một application context chính. Nhiều modules Maven/Gradle không tự sinh parent–child contexts.

Một web Spring MVC truyền thống có thể có root application context và child context cho DispatcherServlet; Boot cũng có thể tạo hierarchy khi application cấu hình tường minh, nhưng không nên suy ra từ sự hiện diện của nhiều packages.

Nếu ứng dụng báo duplicate bean definitions trong cùng context, kiểm tra component scanning và overriding settings. **Đừng đánh đồng lỗi trùng definition local với việc child shadow parent**.

## 24. Debug lookup khi ứng dụng dùng nhầm bean

Làm lần lượt:

1. **Có bao nhiêu ApplicationContext?** Xác định chính xác parent và child.
2. **Có gắn parent trước child.refresh() không?**
3. **Bean được lookup từ context nào?** Parent lookup không thể tự chọn child.
4. **Tên bean chính xác là gì?** Kiểm tra aliases, qualifiers.
5. **Child có local bean tên đó không?** Gọi `containsLocalBean`.
6. **Parent có local bean tên đó không?** Gọi `parent.containsLocalBean`.
7. **Types có tương thích không?** Cùng tên khác type dễ gây type mismatch.
8. **Đang dùng API named lookup hay by-type?** Phân biệt `getBean`, `getBeansOfType` và `BeanFactoryUtils`.
9. **Consumer được tạo ở context nào?** Parent consumer không thấy child.
10. **Bean đã được inject trước khi child xuất hiện không?** Shadowing không rewire.
11. **Có component scanning/import quá rộng không?**
12. **Có lifecycle/close order làm shared dependencies unavailable không?**
13. **Có custom FactoryBeans hoặc aliases không?**
14. **Test một context pair nhỏ** với bean names/types dễ phân biệt.

Không dùng log toàn bộ BeanFactory nếu nó có chứa secrets/sensitive configuration; chỉ ghi tên/type/context origin cần thiết.

## 25. Phát hiện collisions từ danh sách bean definitions

Một phép kiểm tra đơn giản:

~~~java
String[] parentDefinitions = parent.getBeanDefinitionNames();
String[] childDefinitions = child.getBeanDefinitionNames();
~~~

Hai arrays là **local bean definitions** của từng context. Lấy giao hai sets có thể tìm các names giống nhau giữa parent/child.

### Không đánh giá mọi collision là lỗi

Có những collisions là chủ ý: module customization, test overrides, context-specific `messageSource`… Quan trọng là:

- Tên trùng nào được cho phép?
- Hai types có cùng contract không?
- Consumer của parent có bị kỳ vọng dùng child service không?
- Liệt kê bean theo type có vô tình thiếu ancestor instance không?
- Khi đóng child thì parent vẫn hoạt động đúng không?

Nếu nhiều contexts và nhiều plugins, kiểm tra collisions bằng integration test cho từng cấu hình triển khai sẽ đáng tin hơn chỉ đọc source code.

## 26. Test matrix

| Tình huống | Kỳ vọng |
| --- | --- |
| Parent/child cùng tên cùng type | Child lookup trả local child |
| Lookup trực tiếp parent | Parent bean không đổi |
| Child không có tên, parent có | Child lookup fallback lên parent |
| Parent không có tên, child có | Parent không tự lookup xuống child |
| Parent/child cùng tên khác type | Child named lookup chọn local hoặc báo type mismatch |
| `containsBean` với parent-only | true ở child |
| `containsLocalBean` với parent-only | false ở child |
| `containsBeanDefinition` với parent-only | false ở child |
| `getBeansOfType` ở child | Chỉ local listable beans |
| `BeanFactoryUtils` cùng tên ở hai cấp | Loại bean ancestor bị shadow |
| Parent consumer | Vẫn dùng parent service |
| Child consumer | Dùng child service |
| Hai child siblings | Shadowing ở A không thay B |
| Context close | Child trước parent |
| @Primary trên parent same-name bean | Không làm named lookup từ child bỏ local |
| Component scan vô ý | Integration test phát hiện duplicate local names |

### Negative test bắt buộc

Test chỉ chứng minh child lấy được local service **chưa đủ**. Phải kiểm tra parent vẫn có service ban đầu và `BeanFactoryUtils` không thấy ancestor bean bị che. Đây mới là bằng chứng đúng của **shadowing chứ không phải overriding**.

## 27. FAQ

**Câu 1.** Child và parent có bean cùng tên, parent bean có bị ghi đè không?

Không. Hai factories giữ definitions và instances riêng.

**Câu 2.** Child không có bean tên `x` thì làm gì?

Tra lên parent theo hierarchical lookup.

**Câu 3.** Parent có thể tìm bean chỉ ở child không?

Không theo cơ chế tra cứu hierarchy mặc định.

**Câu 4.** `containsBean("x")` và `containsLocalBean("x")` khác nhau thế nào?

`containsBean` có thể thấy ancestor; `containsLocalBean` chỉ xét local factory.

**Câu 5.** `getBeansOfType` có trả toàn hierarchy không?

Không. Dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors` nếu muốn ancestor-inclusive visibility.

**Câu 6.** Nếu child type B cùng tên parent type A, ancestor-inclusive lookup theo A có trả parent bean không?

Không theo name-shadowing semantics của BeanFactoryUtils.

**Câu 7.** `@Primary` ở parent có thắng local bean cùng tên trong child không?

Không đối với named lookup từ child.

**Câu 8.** Parent singleton đã inject có tự đổi thành child instance không?

Không. Shadowing không phải runtime rebinding.

**Câu 9.** Đặt hai definitions cùng tên trong một BeanFactory có phải shadowing không?

Không. Đó là bean definition overriding hoặc duplicate registration issue.

**Câu 10.** BeanDefinition inheritance có liên quan trực tiếp đến child ApplicationContext không?

Không. Đó là metadata inheritance của definitions, không phải hierarchy lookup masking.

**Câu 11.** Muốn lấy parent bean đang bị che, nên làm gì?

Tra trực tiếp parent context theo tên và type, hoặc thiết kế explicit dependency/name không trùng.

**Câu 12.** Nên đóng context nào trước?

Đóng child trước rồi parent để shared dependencies còn khả dụng trong thời gian child hoạt động.

## 28. Checklist code review

- [ ] Đã xác định đúng hierarchy và parent được gắn trước child.refresh()?
- [ ] Đã kiểm tra tên bean ở parent và child có trùng không?
- [ ] Tất cả trường hợp trùng tên đều có chủ đích?
- [ ] Bean cùng tên khác type có được kiểm thử?
- [ ] Không nhầm shadowing với definition overriding trong cùng registry?
- [ ] Không nhầm shadowing với bean definition inheritance?
- [ ] Khi debug đã dùng `containsLocalBean`, không chỉ `containsBean`?
- [ ] `getBeansOfType` local-only được hiểu rõ?
- [ ] `BeanFactoryUtils` ancestor-inclusive vẫn shadow names được hiểu rõ?
- [ ] @Primary/@Qualifier không bị coi là cách chọn cấp context tự động?
- [ ] Parent consumer và child consumer nhận đúng dependencies?
- [ ] Không kỳ vọng parent singleton tự được inject lại?
- [ ] Component scans có bị duplicate shared infrastructure beans?
- [ ] Có test both same-name/same-type và same-name/different-type?
- [ ] Có lifecycle close child trước parent?
- [ ] Có quản lý naming conventions, module origins và collisions?

## 29. Tóm tắt cuối

- **Bean cùng tên trong child che khuất bean của parent** khi tra cứu từ child.
- **Bean parent không bị thay hoặc xóa**; parent lookup vẫn lấy object ban đầu.
- **Shadowing dựa trên tên**, không phụ thuộc việc hai beans có cùng class hay không.
- `containsBean` nhìn được cả ancestor, `containsLocalBean` chỉ local, `containsBeanDefinition` hỏi local registry.
- `ListableBeanFactory.getBeansOfType` chỉ liệt kê local beans; `BeanFactoryUtils.beansOfTypeIncludingAncestors` gộp hierarchy nhưng **loại parent bean bị trùng tên local** ngay cả khi khác type.
- **@Primary không đổi local-first named lookup**, và @Qualifier không tự chọn parent context.
- Consumer được tạo trong parent không nhìn xuống child; bean dependency đã inject không tự cập nhật.
- Cần phân biệt **hierarchy shadowing**, **same-factory overriding**, **BeanDefinition inheritance** và type-candidate selection.
- Test với hai contexts thật cùng lifecycle đúng thứ tự là cách kiểm chứng rõ nhất.

## 30. Tài liệu Spring chính thức

- [Spring Framework 6.1 — HierarchicalBeanFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html): `getParentBeanFactory`, `containsLocalBean`.
- [Spring Framework 6.1 — BeanFactoryUtils](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html): `beansOfTypeIncludingAncestors` và shadowing khi lookup theo type.
- [Spring Framework 6.1 — ListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html): phạm vi local của enumeration APIs.
- [Spring Framework 6.1 — BeanFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/BeanFactory.html): lookup theo name, type và containsBean.
- [Spring Framework 6.1 — AbstractBeanFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/support/AbstractBeanFactory.html): hỗ trợ parent factory delegation.
- [Spring Framework 6.1 — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): Java config và refresh lifecycle.
- [Spring Framework Reference — ApplicationContext](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): context hierarchy và bean services.
- [Spring Framework Reference — Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html): khái niệm riêng về metadata inheritance.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-004">Tra cứu cục bộ so với tra cứu có xét tổ tiên</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu cục bộ so với tra cứu có xét tổ tiên trong Spring Framework

## 1. Bài toán và mục tiêu

Một ứng dụng Spring có thể tổ chức các `ApplicationContext` theo **phân cấp cha–con (parent–child hierarchy)**. Ví dụ, root context chứa những service dùng chung, còn một child context chứa controller, bean và cấu hình riêng cho một module. Một bean ở parent có thể được child sử dụng, trong khi bean chỉ có ở child không tự được parent nhìn thấy.

Điểm dễ nhầm là **“tra cứu một bean” và “liệt kê các beans” có phạm vi khác nhau**. Các phương thức của `BeanFactory` như `getBean("name")` hoặc `containsBean("name")` có thể lần lên parent. Ngược lại, những phương thức liệt kê của `ListableBeanFactory` như `getBeanDefinitionNames()`, `getBeanNamesForType(...)` và `getBeansOfType(...)` chỉ xét **factory hiện tại**; chúng không tự gom tất cả ancestor contexts.

Spring cung cấp `BeanFactoryUtils` với các phương thức `...IncludingAncestors` để tổng hợp những beans có thể nhìn thấy từ child trong hệ phân cấp. Khi child và parent khai báo cùng một bean name, **bean ở child che bean ở parent**, kể cả khi chúng khác Java type.

~~~text
Root / Parent ApplicationContext
  parentOnly : Tag("parent-only")
  shared     : Tag("parent-shared")
  masked     : Tag("parent-masked")
             |
             | setParent(parent)
             v
Child ApplicationContext
  childOnly  : Tag("child-only")
  shared     : Tag("child-shared")
  masked     : String("child-string")

Lookup theo tên từ child:
  getBean("parentOnly") → parent
  getBean("shared")     → child
  getBean("masked")     → child (String)

Liệt kê Tag cục bộ ở child:
  childOnly, shared

Liệt kê Tag bao gồm tổ tiên và tôn trọng shadowing:
  childOnly, parentOnly, shared
  Không có masked: parent Tag bị child String che theo TÊN.
~~~

Bài thuộc nhóm **Phân cấp context** của module **Spring Framework - core-container**. Kết thúc bài học, bạn sẽ:

1. Phân biệt `BeanFactory`, `HierarchicalBeanFactory`, `ListableBeanFactory` và `ApplicationContext`.
2. Biết rõ API nào có parent fallback, API nào chỉ liệt kê local beans.
3. Dùng `containsBean`, `containsLocalBean` và `containsBeanDefinition` đúng mục đích.
4. Dùng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors` và `beansOfTypeIncludingAncestors` để quét cả hierarchy.
5. Hiểu **shadowing cùng tên** và tại sao parent có một bean đúng type vẫn có thể bị loại khỏi kết quả.
6. Tránh nhầm trùng tên **ở hai contexts khác nhau** với bean definition overriding **trong cùng một factory**.
7. Biết các caveats của type lookup, `FactoryBean`, `@Primary`, `@Qualifier`, bean scopes và dependency injection.
8. Chạy ví dụ đầy đủ Java 17 + Maven + Spring Framework 6.2.9, kiểm tra stdout và test hierarchy một cách xác định.

## 2. Phân cấp BeanFactory hoạt động như thế nào?

`BeanFactory` là interface lõi dùng để truy cập beans. `HierarchicalBeanFactory` mở rộng nó bằng:

~~~java
BeanFactory getParentBeanFactory();
boolean containsLocalBean(String name);
~~~

Đây là các chữ ký API rút gọn; không phải source code cần định nghĩa lại. Một factory hierarchical có thể trỏ tới parent, parent lại trỏ tới grandparent.

~~~text
Grandparent
    |
    v
Parent
    |
    v
Child
~~~

Một lookup theo bean name bắt đầu tại child:

1. Kiểm tra bean có tên phù hợp trong **child hiện tại** hay không.
2. Nếu có, child bean được chọn.
3. Nếu không, chuyển sang parent.
4. Lặp lên ancestors cho đến khi tìm thấy hoặc không còn parent.

Các nguồn của hierarchy không được hiểu như một danh sách global phẳng mà mọi layer có thể sửa lẫn nhau. Mỗi context/factory có **registry** và lifecycle riêng; child chỉ **tra cứu** parent khi cần.

### Quan hệ một chiều

- Child có thể thấy những bean được parent công bố qua các lookup có hierarchy semantics.
- Parent không tự thấy những beans chỉ khai báo trong child.
- Sibling contexts không tự thấy beans của nhau.
- Khi child đóng, parent không nhất thiết đóng theo; hãy quản lý lifecycle hai contexts đúng thứ tự.

Đây không phải tính chất của Java inheritance giữa classes mà là **quan hệ giữa các IoC containers**.

### ApplicationContext và BeanFactory

`ApplicationContext` kế thừa những interface như `ListableBeanFactory` và `HierarchicalBeanFactory`, nên bạn có thể gọi cả:

~~~java
context.getBean("name");
context.containsLocalBean("name");
context.getBeanNamesForType(MyType.class);
~~~

Nhưng các phương thức này **không có cùng phạm vi tìm kiếm**. Tên interface cung cấp contract chính xác hơn nhận định chung “context biết parent”.

## 3. Phân biệt tra cứu theo tên và liệt kê theo type

### 3.1. BeanFactory: parent fallback theo tên

Các phương thức `BeanFactory` quan trọng:

~~~java
Object getBean(String name);
<T> T getBean(String name, Class<T> requiredType);
boolean containsBean(String name);
Class<?> getType(String name);
boolean isTypeMatch(String name, Class<?> type);
boolean isSingleton(String name);
~~~

Danh sách này rút gọn, các API có overload và exception semantics khác nhau. Theo `BeanFactory` Javadoc, các operations thuộc giao diện này có thể kiểm tra parent factory khi factory đang dùng là `HierarchicalBeanFactory` và bean chưa có local.

Ví dụ:

~~~java
Marker marker = child.getBean("parentOnly", Marker.class);
~~~

Nếu child **không** khai báo `parentOnly` nhưng parent khai báo, lời gọi tìm thấy parent bean. Nếu child có cùng bean name, child ưu tiên.

### 3.2. ListableBeanFactory: chỉ liệt kê cục bộ

Các phương thức:

~~~java
String[] getBeanDefinitionNames();
String[] getBeanNamesForType(Class<?> type);
<T> Map<String, T> getBeansOfType(Class<T> type);
int getBeanDefinitionCount();
~~~

chỉ xét các bean definitions/beans của **factory hiện tại**, không gộp parent. Đây là điểm được ghi rõ trong `ListableBeanFactory` Javadoc.

Ví dụ:

~~~java
String[] localNames = child.getBeanNamesForType(Marker.class);
~~~

Nếu child có hai `Marker` beans và parent có thêm ba, mảng trả về vẫn chỉ gồm những bean `Marker` **của child**, không tự thành năm. Muốn thấy ancestors, dùng helper ở mục sau.

### 3.3. Bảng phạm vi cần nhớ

| API | Chỉ local? | Có khả năng xét ancestors? | Mục đích |
| --- | --- | --- | --- |
| `child.getBean("name")` | Không | **Có**, nếu local vắng | Lấy một bean theo tên |
| `child.getBean("name", Type.class)` | Không | **Có**, nếu local vắng | Lấy theo tên, kiểm tra type |
| `child.containsBean("name")` | Không | **Có**, nếu local vắng | Kiểm tra tên bean có tồn tại trong hierarchy |
| `child.containsLocalBean("name")` | **Có** | Không | Kiểm tra tên ngay factory này |
| `child.containsBeanDefinition("name")` | **Có** | Không | Kiểm tra definition local, không tính mọi registered singleton |
| `child.getBeanNamesForType(Type.class)` | **Có** | Không | Liệt kê local beans phù hợp type |
| `child.getBeansOfType(Type.class)` | **Có** | Không | Lấy map các local beans phù hợp type |
| `child.getBeanDefinitionNames()` | **Có** | Không | Danh sách local bean definitions |
| `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Type.class)` | Không | **Có** | Tổng hợp tên theo type từ các tầng có thể nhìn thấy |
| `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, Type.class)` | Không | **Có** | Tổng hợp map beans theo type với shadowing |
| `BeanFactoryUtils.beanNamesIncludingAncestors(child)` | Không | **Có** | Tổng hợp tên bean không giới hạn type |
| `BeanFactoryUtils.countBeansIncludingAncestors(child)` | Không | **Có** | Đếm những bean names nhìn thấy xuyên hierarchy |

Lưu ý `getBean(Class<T>)`/`getBeanProvider(...)` là **truy xuất theo type** với quy tắc resolution/uniqueness riêng; đừng coi chúng là cách thay thế trực tiếp cho thao tác **liệt kê đầy đủ**. Khi bạn cần danh sách tất cả candidates ở ancestors, hãy chọn API `BeanFactoryUtils...IncludingAncestors` một cách tường minh.

## 4. containsBean, containsLocalBean và containsBeanDefinition

Ba phép kiểm tra thường bị dùng lẫn nhau.

### 4.1. containsBean(name)

~~~java
boolean visible = child.containsBean("parentOnly");
~~~

Nếu parent có bean này còn child không có, kết quả có thể **true**. Đây là kiểm tra **visibility theo tên**, không xác nhận bean được khai báo trong child.

Javadoc còn lưu ý `containsBean` trả true với definition abstract/lazy/scope chưa active trong vài tình huống, nên **true không luôn chứng minh `getBean` chắc chắn tạo/lấy được instance thành công**.

### 4.2. containsLocalBean(name)

~~~java
boolean locallyPresent =
        child.containsLocalBean("parentOnly");
~~~

`containsLocalBean` thuộc `HierarchicalBeanFactory` và bỏ qua ancestors. Nếu bean chỉ có ở parent, kết quả **false**. Nó xét cả local definitions lẫn registered singleton instances theo nghĩa local bean presence phù hợp implementation.

### 4.3. containsBeanDefinition(name)

~~~java
boolean definedHere =
        child.containsBeanDefinition("parentOnly");
~~~

Đây là method `BeanDefinitionRegistry` / `GenericApplicationContext` hoặc bean factory implementation phù hợp, **không phải method thuộc mọi interface `ApplicationContext`**. Trong `AnnotationConfigApplicationContext` của demo, có thể dùng trực tiếp vì implementation hỗ trợ registry.

Khác `containsLocalBean`, `containsBeanDefinition` chỉ hỏi về **bean definition đã đăng ký**, không phải mọi singleton instance được đăng ký programmatically. Nếu code gọi:

~~~java
context.getBeanFactory().registerSingleton(
        "externalSingleton", new Object());
~~~

thì bean có thể local và lấy được nhưng không nhất thiết có `BeanDefinition` tương ứng. Đây là snippet phải chạy **trước/đúng lifecycle thích hợp**; tránh đăng ký singleton thủ công tùy tiện trong production.

### 4.4. Khi nào dùng API nào?

- Kiểm tra **child có thể sử dụng theo tên không**: `containsBean`.
- Kiểm tra **child có một bean cùng tên local không**: `containsLocalBean`.
- Kiểm tra **bean definition do child registry khai báo không**: `containsBeanDefinition`.
- Kiểm tra **loại bean nào khả dụng theo toàn hierarchy**: `BeanFactoryUtils.beanNamesForTypeIncludingAncestors`.
- Kiểm tra **local bean types**: `getBeanNamesForType`.

Đừng viết một check `containsBean` rồi kết luận bean là “cấu hình riêng của module con”.

## 5. BeanFactoryUtils: tra cứu có xét tổ tiên

`org.springframework.beans.factory.BeanFactoryUtils` cung cấp các helper static cho `ListableBeanFactory` và hierarchy.

Các method tiêu biểu:

~~~java
String[] beanNamesIncludingAncestors(ListableBeanFactory lbf);

String[] beanNamesForTypeIncludingAncestors(
        ListableBeanFactory lbf, Class<?> type);

<T> Map<String, T> beansOfTypeIncludingAncestors(
        ListableBeanFactory lbf, Class<T> type);

<T> T beanOfTypeIncludingAncestors(
        ListableBeanFactory lbf, Class<T> type);
~~~

Đây là các signatures rút gọn. Spring còn có những overload nhận `ResolvableType`, `includeNonSingletons`, `allowEagerInit`.

### 5.1. Chỉ lấy tên

~~~java
String[] names = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
        child, PaymentClient.class);
~~~

Method này lấy tên các beans phù hợp `PaymentClient` trong child và ancestor factories phù hợp, đồng thời loại parent names bị local bean che. Nếu một tên được định nghĩa ở nhiều tầng, kết quả chỉ có tên phù hợp từ tầng gần child hơn.

### 5.2. Lấy cả beans

~~~java
Map<String, PaymentClient> clients =
        BeanFactoryUtils.beansOfTypeIncludingAncestors(
                child, PaymentClient.class);
~~~

Đây là snippet khi `child` là `ListableBeanFactory` hoặc `ApplicationContext`, `PaymentClient` là type do ứng dụng định nghĩa. Lấy beans có thể khởi tạo instances và kích hoạt lifecycle của những beans được resolve. Chỉ tìm **tên** và lấy **instance** có tác động khác nhau; với singleton lazy hoặc `FactoryBean` nên cân nhắc `allowEagerInit` overload nếu cần tránh eager factory initialization.

### 5.3. Tìm một bean duy nhất

~~~java
PaymentClient one =
        BeanFactoryUtils.beanOfTypeIncludingAncestors(
                child, PaymentClient.class);
~~~

Chỉ phù hợp khi có **một bean phù hợp, khả kiến** theo contract. Nếu không có thì ném `NoSuchBeanDefinitionException`; nếu có nhiều candidates không thể xác định duy nhất thì `NoUniqueBeanDefinitionException`. Đừng dùng method này để “chọn ngẫu nhiên” một bean khi có nhiều implementations.

### 5.4. Lưu ý return ordering

Thứ tự result names có thể phản ánh bean definition registration/parent traversal; **không coi đó là thứ tự nghiệp vụ ổn định xuyên mọi cấu hình**. Khi cần báo cáo/debug, sort names trước khi in. Khi cần chọn preferred bean, dùng bean name hoặc DI qualifiers theo contract rõ ràng, không chọn `names[0]`.

## 6. Shadowing: bean tên trùng ở child che bean ở parent

Đây là quy tắc quan trọng nhất của lookup xuyên hierarchy.

~~~text
Parent:
  "shared" → Service A
  "masked" → Marker instance

Child:
  "shared" → Service B
  "masked" → String instance
~~~

Khi gọi:

~~~java
child.getBean("shared");
~~~

Spring lấy **child bean** vì có local name. Không tự đi lên parent chỉ vì parent có type bạn thích hơn.

### Che theo tên, không theo type

Nếu parent `masked` là `Marker` nhưng child `masked` là `String`:

~~~java
child.getBean("masked", Marker.class);
~~~

Không tự lấy parent Marker. Local bean cùng tên tồn tại, nên Spring chọn local rồi kiểm tra type; do không đúng type, lời gọi có thể ném `BeanNotOfRequiredTypeException`.

Tương tự, kết quả:

~~~java
BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
        child, Marker.class);
~~~

**không đưa `masked` của parent vào**, dù parent bean khớp `Marker.class`. Helper kiểm tra việc **có bean local cùng tên** để loại parent bean, ngay cả khi child bean ấy **không match type đang lọc**.

Đây là bẫy phổ biến: nhìn danh sách kiểu `Marker` trong child + parent rồi tính hợp nhất bằng phép cộng thủ công sẽ sai nếu có shadowing.

### Parent bean có bị xóa không?

Không. Parent vẫn có:

~~~java
parent.getBean("masked", Marker.class);
~~~

và trả về parent Marker. Child chỉ **che nó trong lookups bắt đầu từ child**. Các registry độc lập nên đây không phải thao tác sửa/xóa bean ở parent.

### Shadowing khác overriding trong cùng factory

- **Hierarchy shadowing:** cùng bean name xuất hiện ở **hai factories khác nhau**. Child được ưu tiên trong lookups từ child. Không có yêu cầu phải bật bean-definition-overriding trên cùng factory.
- **Bean definition overriding:** hai definitions trùng bean name **trong một factory duy nhất**. Điều này phụ thuộc cấu hình cho phép/không cho phép overriding và có thể lỗi registration.
- **@Primary:** giải quyết ưu tiên injection theo type trong một tập candidates, không làm parent cùng tên tự thoát khỏi shadowing.
- **@Qualifier:** chọn candidate có qualifier/name phù hợp nhưng không tự mở một “cửa sau” để lấy parent bean cùng tên đã bị child che theo canonical lookup.

Nếu cần cụ thể parent instance đang bị che, hãy dùng parent reference có chủ đích (`parent.getBean(...)`) hoặc đổi tên beans để tránh xung đột ngoài ý muốn.

## 7. Sơ đồ phạm vi: lookup từ mỗi tầng

Giả sử ba tầng:

~~~text
Root:
  rootOnly = RootService
  shared   = RootShared

Module:
  moduleOnly = ModuleService
  shared     = ModuleShared

Feature:
  featureOnly = FeatureService
~~~

Các câu hỏi:

- `feature.getBean("rootOnly")` có thể đi lên `Module` rồi `Root`.
- `feature.getBean("shared")` lấy `ModuleShared` vì Module gần Feature hơn.
- `root.getBean("featureOnly")` không tự đi xuống.
- `feature.getBeanNamesForType(Service.class)` chỉ xét Feature.
- `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(feature, Service.class)` xét những candidates khả kiến từ Feature ở cả ba tầng, loại những ancestor beans bị che theo tên.

Quy tắc áp dụng đệ quy: **tầng gần lookup origin hơn thắng**. Nếu một name đã tồn tại ở Feature, cả Module và Root cùng tên đều bị che. Nếu Feature không có name nhưng Module có, Root cùng tên bị che bởi Module.

### BeanFactory parent không phải Java class extends

`class ChildConfig extends ParentConfig` là inheritance ở cấp Java class; nó **không** tự tạo quan hệ parent–child `ApplicationContext`. Ngược lại, `child.setParent(parent)` tạo hierarchy giữa containers dù hai `@Configuration` classes hoàn toàn độc lập.

Cũng không nên nhầm với XML bean definition inheritance (`parent="baseBean"`) — cơ chế đó dùng lại bean metadata trong cùng factory, không phải quan hệ parent context.

## 8. Ví dụ đầy đủ Java 17 + Maven + Spring Framework 6.2.9

Chương trình cố ý có ba beans `Tag` ở parent, hai `Tag` ở child, một bean `String` local che parent `Tag` cùng tên, cùng một dependency chỉ parent có để chứng minh child có thể inject từ parent.

Kết quả hiển thị rõ sự khác biệt giữa:

- `containsBean` và `containsLocalBean`.
- `getBean(name)` và `getBeanNamesForType(type)`.
- `getBeansOfType` local và `BeanFactoryUtils.beansOfTypeIncludingAncestors`.
- Shadowing **khác type**.
- Parent không nhìn thấy child bean.
- Child `@Bean` factory method dùng parent-only dependency.

### 8.1. Cấu trúc

~~~text
bean-hierarchy-lookup/
├── pom.xml
└── src/main/java/demo/
    └── HierarchyLookupDemo.java
~~~

### 8.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>bean-hierarchy-lookup</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
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
~~~

### 8.3. src/main/java/demo/HierarchyLookupDemo.java

~~~java
package demo;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class HierarchyLookupDemo {

    public record Tag(String value) {
    }

    public record ParentContract(String value) {
    }

    public record ChildConsumer(ParentContract contract) {
    }

    private static String sorted(String[] names) {
        return Arrays.stream(names)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String sorted(Map<String, ?> beans) {
        return beans.keySet().stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static void main(String[] args) {

        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(
                             ParentConfig.class)) {

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                // Đặt parent TRƯỚC khi refresh child.
                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                System.out.println("local.parentOnly="
                        + child.containsLocalBean("parentOnly"));

                System.out.println("visible.parentOnly="
                        + child.containsBean("parentOnly"));

                System.out.println("definition.parentOnly="
                        + child.containsBeanDefinition("parentOnly"));

                System.out.println("lookup.parentOnly="
                        + child.getBean("parentOnly", Tag.class).value());

                System.out.println("parent.seesChildOnly="
                        + parent.containsBean("childOnly"));

                System.out.println("parent.local.tags="
                        + sorted(parent.getBeanNamesForType(Tag.class)));

                System.out.println("child.local.tags="
                        + sorted(child.getBeanNamesForType(Tag.class)));

                System.out.println("child.all.tags="
                        + sorted(BeanFactoryUtils
                                .beanNamesForTypeIncludingAncestors(
                                        child, Tag.class)));

                System.out.println("child.local.map="
                        + sorted(child.getBeansOfType(Tag.class)));

                System.out.println("child.all.map="
                        + sorted(BeanFactoryUtils
                                .beansOfTypeIncludingAncestors(
                                        child, Tag.class)));

                System.out.println("child.shared="
                        + child.getBean("shared", Tag.class).value());

                System.out.println("parent.shared="
                        + parent.getBean("shared", Tag.class).value());

                System.out.println("child.masked.type="
                        + child.getBean("masked").getClass()
                                .getSimpleName());

                System.out.println("parent.masked="
                        + parent.getBean("masked", Tag.class).value());

                try {
                    child.getBean("masked", Tag.class);
                } catch (BeanNotOfRequiredTypeException expected) {
                    System.out.println("child.masked.asTag="
                            + expected.getClass().getSimpleName());
                }

                System.out.println("child.consumer.parent="
                        + child.getBean(ChildConsumer.class)
                                .contract().value());

                try {
                    parent.getBean("childOnly", Tag.class);
                } catch (NoSuchBeanDefinitionException expected) {
                    System.out.println("parent.childOnly="
                            + expected.getClass().getSimpleName());
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("parentOnly")
        public Tag parentOnly() {
            return new Tag("parent-only");
        }

        @Bean("shared")
        public Tag parentShared() {
            return new Tag("parent-shared");
        }

        @Bean("masked")
        public Tag parentMasked() {
            return new Tag("parent-masked");
        }

        @Bean
        public ParentContract parentContract() {
            return new ParentContract("parent-contract");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("childOnly")
        public Tag childOnly() {
            return new Tag("child-only");
        }

        @Bean("shared")
        public Tag childShared() {
            return new Tag("child-shared");
        }

        // Cùng tên 'masked' với parent, nhưng khác Java type.
        @Bean("masked")
        public String childMasked() {
            return "child-string";
        }

        @Bean
        public ChildConsumer childConsumer(
                ParentContract parentContract) {
            return new ChildConsumer(parentContract);
        }
    }
}
~~~

### 8.4. Lệnh chạy

Tại thư mục `bean-hierarchy-lookup`, yêu cầu JDK 17+ và Maven:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.HierarchyLookupDemo
~~~

**Stdout của chương trình**, không tính Maven/Spring logging và với đúng cấu hình trên:

~~~text
local.parentOnly=false
visible.parentOnly=true
definition.parentOnly=false
lookup.parentOnly=parent-only
parent.seesChildOnly=false
parent.local.tags=masked,parentOnly,shared
child.local.tags=childOnly,shared
child.all.tags=childOnly,parentOnly,shared
child.local.map=childOnly,shared
child.all.map=childOnly,parentOnly,shared
child.shared=child-shared
parent.shared=parent-shared
child.masked.type=String
parent.masked=parent-masked
child.masked.asTag=BeanNotOfRequiredTypeException
child.consumer.parent=parent-contract
parent.childOnly=NoSuchBeanDefinitionException
~~~

### 8.5. Giải thích theo từng nhóm output

| Nhóm | Ý nghĩa |
| --- | --- |
| `local.parentOnly=false` | Bean `parentOnly` không khai báo ở child |
| `visible.parentOnly=true` | `containsBean` từ child tìm được ở parent |
| `definition.parentOnly=false` | Child không có bean definition local mang tên đó |
| `lookup.parentOnly=parent-only` | `getBean(name)` fallback sang parent |
| `parent.seesChildOnly=false` | Parent không thấy child-only bean |
| `parent.local.tags` | Parent tự liệt kê ba local `Tag` beans |
| `child.local.tags` | Child tự liệt kê hai local `Tag` beans |
| `child.all.tags` | BeanFactoryUtils thêm `parentOnly` nhưng không thêm parent `shared`/`masked` vì bị child che |
| `child.local.map` | `getBeansOfType` chỉ lấy local beans |
| `child.all.map` | `beansOfTypeIncludingAncestors` tổng hợp theo visibility và shadowing |
| `child.shared` vs `parent.shared` | Cùng bean name ở hai contexts, hai object khác nhau |
| `child.masked.type=String` | Child local String được chọn theo tên |
| `parent.masked=parent-masked` | Parent Tag vẫn tồn tại trong parent registry |
| `child.masked.asTag=BeanNotOfRequiredTypeException` | Lookup từ child theo tên không bỏ qua child chỉ vì type sai |
| `child.consumer.parent=parent-contract` | Child bean nhận một dependency chỉ parent khai báo |
| `parent.childOnly=NoSuchBeanDefinitionException` | Parent lookup không đi xuống child |

### 8.6. Vì sao danh sách tổng hợp không có masked?

Ở parent: `masked` là `Tag`. Ở child: `masked` là `String`. Nếu chỉ lọc type `Tag` ở child, `masked` không xuất hiện. Nếu chỉ lọc parent, nó xuất hiện.

Nhưng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Tag.class)` thực hiện **lọc parent entries theo việc có local bean cùng tên**, không chỉ theo việc local có bean cùng type. Child `String masked` đã che tên `masked`, nên parent `Tag masked` không được đưa vào result. Điều này đúng với semantics lookup theo tên từ child và giúp không tạo một danh sách type candidates mà thực tế không thể lấy bằng `child.getBean("masked", Tag.class)`.

### 8.7. Tại sao demo tránh gọi getBean(Tag.class)?

Có nhiều `Tag` candidates; một by-type lookup yêu cầu unique match có thể ném `NoUniqueBeanDefinitionException`. Bài muốn chứng minh **liệt kê** local so với ancestor-aware, nên dùng `getBeanNamesForType` và `BeanFactoryUtils`. Để chọn một bean cụ thể, dùng bean name/qualifier rõ ràng thay vì tùy tiện pick candidate đầu tiên.

### 8.8. Phạm vi lifecycle

Parent được tạo/refresh trước. Child `setParent(parent)` trước `refresh()`. Khi chạy hết demo, `try-with-resources` đóng child trước rồi đóng parent. Nếu đóng parent trước khi child hoàn tất, child có thể mất dependencies/beans đang sử dụng. Không nên gọi `child.setParent` sau khi đã dùng child refresh như một cách thay đổi runtime dependency graph.

## 9. Các câu hỏi API thường gây nhầm

### 9.1. getBean(name) và getBean(type) có giống nhau không?

Không. `getBean("name")` lấy theo **tên**, parent fallback khi không có local cùng tên. `getBean(Type.class)` lấy một candidate phù hợp **theo type** và có uniqueness rules (`NoSuchBeanDefinitionException`/`NoUniqueBeanDefinitionException`). Không coi `getBean(Type.class)` là API để **liệt kê tất cả** across hierarchy; dùng `BeanFactoryUtils` khi cần tất cả names/maps.

### 9.2. getBeanNamesForType có gọi parent không?

Không. Theo `ListableBeanFactory` Javadoc, collection-style lookup chỉ xem factory hiện tại. `child.getBeanNamesForType(Service.class)` không gộp ancestor beans.

### 9.3. Có thể chỉ ghép arrays từ child và parent không?

Không nên. Nếu có trùng names, kết quả sẽ có duplicates và có thể lộ parent beans vốn bị child che. `BeanFactoryUtils.beanNamesForTypeIncludingAncestors` và `beansOfTypeIncludingAncestors` thực hiện merge theo hierarchy semantics.

### 9.4. containsBeanDefinition là alias của containsLocalBean?

Không. Definition registry và singleton registry có thể khác nhau. Một singleton programmatically registered có thể được `containsLocalBean` công nhận nhưng không có local `BeanDefinition`.

### 9.5. @Primary có thể thay đổi kết quả BeanFactoryUtils không?

`@Primary` ảnh hưởng candidate selection trong một số tình huống **dependency injection/by-type single bean resolution**; nó không làm `BeanFactoryUtils.beanNamesForTypeIncludingAncestors` tự thêm lại một parent bean đang bị shadow cùng tên, cũng không khiến `getBeanNamesForType` mở rộng sang parent.

### 9.6. @Qualifier có lấy được bean ở ancestor đang bị che không?

Đừng dựa vào điều đó. Với name clash, child name thắng trong lookup từ child. Khi thực sự cần parent instance, có thể truyền dependency explicit từ parent hoặc dùng bean name không trùng; không coi qualifier là cách vượt qua hierarchy masking.

### 9.7. Nếu parent có bean A và child có bean B, cùng type?

Kết quả tổng hợp theo type có thể chứa **cả hai** nếu chúng có **khác tên**. Một injection point yêu cầu **một** bean có thể gặp ambiguity hoặc áp các quy tắc candidate selection. Với collection injection, semantics còn phụ thuộc candidate resolution của bean factory; test trên context cấu hình thực tế chứ không suy diễn từ một `BeanFactoryUtils` call bất kỳ.

### 9.8. FactoryBean làm việc lọc type phức tạp hơn thế nào?

`ListableBeanFactory.getBeanNamesForType` và helpers có thể xét product type của `FactoryBean` thông qua `getObjectType()`. Một số overload có `allowEagerInit` để kiểm soát mức độ eager initialization khi type lookup. Nếu factory type metadata không ổn định hoặc trả null, danh sách candidates có thể khác kỳ vọng. Đây là lý do phải phân biệt **tra cứu tên** và **có thể khởi tạo sản phẩm**.

### 9.9. Scoped beans có gây tác dụng phụ khi quét map không?

`getBeansOfType` trả **instances**, nên bean creation hoặc scope resolution có thể diễn ra. Với request/session scope ở context không có request active, lấy instance có thể thất bại dù `getBeanNamesForType` vẫn liệt kê metadata. Nếu chỉ cần inspect names, ưu tiên method trả tên thay vì lấy tất cả objects.

### 9.10. Alias cùng parent có bị che?

Spring xử lý aliases khi lookup tên; canonical name và aliases có thể ảnh hưởng resolution, nhưng cơ chế trùng tên/same-name shadowing phải được kiểm tra với **bean name thực** và các aliases đã đăng ký. Không giả định alias luôn là một bean definition độc lập. Với configuration phức tạp, tránh reuse names/aliases qua nhiều tầng hoặc viết tests cho behavior mong muốn.

## 10. Autowiring qua parent: có nhưng không vô điều kiện

Một child bean có thể nhận dependency từ parent, ví dụ demo:

~~~java
@Bean
public ChildConsumer childConsumer(ParentContract parentContract) {
    return new ChildConsumer(parentContract);
}
~~~

Spring khi resolve tham số có thể tìm một parent `ParentContract`, vì child không có local candidate cạnh tranh. Nhưng đây không đồng nghĩa mọi injection đều “thấy tất cả parent definitions bất kể tên”:

- Child local bean có thể che parent cùng name.
- Nhiều candidates cùng type có thể dẫn tới ambiguity.
- `@Primary` và `@Qualifier` tham gia chọn candidate theo injection metadata.
- Parent không thể inject dependency chỉ child có theo một reverse lookup mặc định.
- `@Resource(name="...")` theo tên có semantics khác `@Autowired` theo type.
- Có thể cần giải pháp rõ ràng hơn nếu chia sẻ resource giữa nhiều child contexts.

### @Autowired theo type khác @Resource theo tên

Ví dụ minh họa:

~~~java
@Autowired
private PaymentClient client;
~~~

và:

~~~java
@Resource(name = "primaryPaymentClient")
private PaymentClient client;
~~~

Đây là các field snippets, `PaymentClient` do ứng dụng định nghĩa, `@Autowired` là Spring annotation, `@Resource` thường từ Jakarta annotations. Path chọn candidate khác: by-type injection không nên suy diễn hệt `getBean("primaryPaymentClient")`.

Trong dự án có hierarchy và bean shadowing, **constructor injection với @Qualifier được document rõ** thường dễ kiểm thử hơn field injection không có tên rõ.

### Spring Boot/web contexts

Trong ứng dụng web MVC truyền thống có root `WebApplicationContext` và một hoặc nhiều servlet-specific child contexts. Root thường có services/repositories dùng chung, servlet child có controllers/view-related beans.

Nếu listener, resource hoặc controller bị “không tìm thấy”, cần xác định bạn đang gọi từ **root hay child context**. Có thể bean tồn tại trong sibling hoặc child khác nhưng không khả kiến từ lookup origin. Không giải thích bằng việc “cả ứng dụng Spring chỉ có một BeanFactory” khi thực tế có nhiều contexts.

## 11. Tra cứu theo bean names so với bean definitions

Hai thao tác thường gặp:

~~~java
String[] localNames = child.getBeanDefinitionNames();
String[] visibleNames =
        BeanFactoryUtils.beanNamesIncludingAncestors(child);
~~~

`getBeanDefinitionNames()` chỉ liệt kê **registered definitions local**. `beanNamesIncludingAncestors(...)` tổng hợp **bean names across hierarchy** theo contract helpers.

### Tại sao số đếm khác nhau?

Một `ApplicationContext` có thể chứa nhiều infrastructure bean definitions ngoài những beans bạn tự khai báo:

- `@Configuration` classes cũng có bean definitions.
- Internal annotation processors.
- Event listener infrastructure.
- Các bean được đăng ký bởi imports/configuration modules.

Do đó `getBeanDefinitionCount()` không nhất thiết bằng “số @Bean methods bạn viết”. `BeanFactoryUtils.countBeansIncludingAncestors(...)` không phải phép cộng thô tất cả counts nếu có shadowing; cần hiểu semantics “unique visible names”.

Trong demo chỉ sort/list **một type do mình định nghĩa** để tránh nhiễu bởi các infrastructure beans.

### Annotation lookup thường cũng là local

`ListableBeanFactory.getBeanNamesForAnnotation(...)` thuộc nhóm listing APIs và không mặc định đi qua ancestors. Nếu muốn tổng hợp metadata annotations xuyên hierarchy, phải thiết kế traversal hoặc dùng API hỗ trợ tương ứng với yêu cầu; không tự giả định có helper cho mọi biến thể. Khi duyệt thủ công, nhớ áp shadowing theo tên.

## 12. Định nghĩa parent trước child và lifecycle

Code đúng:

~~~java
AnnotationConfigApplicationContext parent =
        new AnnotationConfigApplicationContext(ParentConfig.class);

AnnotationConfigApplicationContext child =
        new AnnotationConfigApplicationContext();

child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
~~~

Đây là snippet tạo hierarchy; ứng dụng phải đóng contexts sau khi dùng xong. Trong bài chạy đầy đủ, `try-with-resources` đóng child trước parent.

### Vì sao cần setParent trước refresh?

Khi child refresh, Spring tạo beans, resolve dependencies, chuẩn bị post-processors. Parent phải được biết trước để child tìm dependencies từ parent ngay trong quá trình đó. Đổi parent giữa chừng không phải cách chính thức để “hot swap” toàn bộ registry/lifecycle một cách an toàn.

### Singleton scope thuộc factory nào?

Một bean singleton ở parent là **singleton đối với parent factory**. Nếu hai child contexts dùng chung cùng parent bean, họ có thể nhận cùng instance từ parent. Nhưng child và parent đều có `shared` riêng thì đây là **hai singletons ở hai factories**.

### Đóng context

Parent bean có thể được nhiều child sử dụng. Đóng parent trước các children đang chạy có thể phá hỏng dependencies của children. Thường đóng children trước rồi parent, và không để child giữ reference tới parent bean đã destroy.

### Không nhầm parent context với package scanning

Component scanning package rộng/hẹp chỉ quyết định bean definitions được đăng ký vào factory nào. Nếu parent scan controller vô tình, điều đó không làm controller tự chuyển thành bean của child. Nếu child và parent scan cùng package, có thể tạo **hai instances local** và shadow names, gây lifecycle bất ngờ.

## 13. Hiệu năng và side effects khi introspection

Tra cứu names theo type thường sử dụng bean definition metadata; tùy `FactoryBean`, proxy và type prediction, Spring có thể cần eager-init một số factories trong những overload. Lấy beans bằng `getBeansOfType` rõ ràng có thể khởi tạo instances.

Trong production diagnostics:

- Chỉ lấy tên nếu không cần instances.
- Cân nhắc overload `allowEagerInit=false` khi phù hợp và hiểu dữ liệu có thể thiếu type chưa xác định.
- Không gọi `getBeansOfType` trên quá nhiều prototype/request-scoped beans chỉ để “đếm”.
- Không tự duyệt hierarchy và gọi `getBean` cho mọi name trong request nóng.
- Sort output khi kiểm tra ổn định, không dùng ordering ngẫu nhiên làm policy lựa chọn.
- Đo các side effects/lifecycle callback nếu type inspection diễn ra quá sớm.

## 14. Những lỗi phổ biến và cách khắc phục

| Hiện tượng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| `child.getBean("parentOnly")` chạy nhưng `getBeanNamesForType` không thấy | getBean theo tên có parent fallback, listing chỉ local | Dùng `BeanFactoryUtils...IncludingAncestors` |
| `containsBean` true nhưng `containsLocalBean` false | Bean chỉ nằm trong parent | Phân biệt visibility với ownership |
| `containsBeanDefinition` false nhưng `containsLocalBean` true | Singleton local được đăng ký trực tiếp, không có definition | Kiểm tra singleton registry |
| Parent `Tag masked` biến mất khỏi ancestor-aware type list | Child có bean cùng name khác type | Hiểu same-name shadowing |
| `child.getBean("masked", Tag.class)` ném BeanNotOfRequiredTypeException | Child String cùng name che parent Tag | Đổi tên hoặc lấy tường minh từ parent |
| `parent.getBean("childOnly")` không thấy | Lookup không đi xuống descendants | Gọi từ child hoặc chuyển bean cần chia sẻ lên parent |
| `getBeansOfType` ít hơn kỳ vọng | Local-only enumeration | Dùng `beansOfTypeIncludingAncestors` |
| Ghép arrays parent+child ra tên trùng | Không áp masking | Dùng `BeanFactoryUtils` |
| BeanFactoryUtils có nhiều candidates | Nhiều names cùng type khả kiến | Qualifier/name hoặc chiến lược lựa chọn rõ |
| `@Primary` không đưa parent bean bị che trở lại | @Primary không đổi name precedence | Tránh cùng name |
| Child không inject được parent service | Parent chưa được set trước refresh, bean không tồn tại, ambiguity | Xem hierarchy, candidate metadata |
| Parent service dùng child controller không được | Parent không biết child | Tách trách nhiệm hoặc explicit wiring |
| Scope exception khi lấy map bean instances | Request/session scope không active | Chỉ inspect names hoặc tạo scope context đúng |
| Eager FactoryBean khi scan type | Type metadata/allowEagerInit behavior | Xem overload và FactoryBean.getObjectType |
| Nhiều instances singleton tưởng “trùng” | Parent/child mỗi factory có singleton riêng | Kiểm tra owner context và bean name |
| Root web context có controller ngoài ý muốn | Scan scopes quá rộng/nhiều context | Tách config classes/component scan boundaries |
| Alias làm lookup khó hiểu | Bean alias khác canonical name | Xem registry và aliases, test lookup |
| Hai bean definitions cùng tên trong child gây lỗi | Overriding trong cùng factory bị cấm | Đổi tên hoặc sửa registration/config |
| Code duyệt toàn bộ beans quá chậm | Introspection gây eager bean creation | Lọc type/name, tránh gọi instances hàng loạt |
| Unit test pass, integration test fail | Test chỉ dựng một context, production có hierarchy | Tạo parent-child trong tests |

### Quy trình chẩn đoán

1. Xác định lookup được thực hiện **từ context/factory nào**.
2. In bean names và classes ở **local** và **parent** riêng biệt, không in credentials/secrets.
3. Phân biệt `getBean(name)` với `getBeanNamesForType(type)`.
4. Kiểm tra `containsLocalBean(name)` và `containsBean(name)`.
5. Nếu không thấy parent type, thử `BeanFactoryUtils.beanNamesForTypeIncludingAncestors`.
6. Nếu vẫn thiếu, kiểm tra **shadowing cùng tên**, kể cả khác type.
7. Kiểm tra `FactoryBean.getObjectType()` và eager-init settings nếu có.
8. Với dependency injection, xem `@Qualifier`, `@Primary`, candidate count.
9. Xem bean nào thuộc parent, child hoặc sibling context; kiểm tra `setParent` trước refresh.
10. Test packaged/runtime context layout thực tế, không chỉ một flat unit-test context.

## 15. Kiểm thử parent-child hierarchy

### 15.1. Test visibility theo tên

~~~java
if (child.containsLocalBean("parentOnly")) {
    throw new AssertionError("parentOnly should not be local");
}

if (!child.containsBean("parentOnly")) {
    throw new AssertionError("parentOnly should be visible");
}

String actual = child.getBean("parentOnly", Tag.class).value();
if (!"parent-only".equals(actual)) {
    throw new AssertionError("Unexpected parent bean");
}
~~~

Đây là snippet dùng type `Tag` và child context của demo. Nó kiểm tra đồng thời local presence, ancestor visibility và object thực tế.

### 15.2. Test local-only type listing

~~~java
Set<String> localNames =
        Set.of(child.getBeanNamesForType(Tag.class));

if (!localNames.equals(Set.of("childOnly", "shared"))) {
    throw new AssertionError("Local type lookup incorrect");
}
~~~

Snippet cần `java.util.Set` và `Tag` type; không giả định ordering của array.

### 15.3. Test ancestor-aware helper

~~~java
Set<String> visibleNames = Set.of(
        BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                child, Tag.class));

if (!visibleNames.equals(
        Set.of("childOnly", "parentOnly", "shared"))) {
    throw new AssertionError("Ancestor-aware lookup incorrect");
}
~~~

Nếu `masked` xuất hiện, test đã vi phạm expected shadowing semantics (child same-name String phải che parent Tag).

### 15.4. Test wrong type do shadowing

~~~java
boolean mismatch = false;
try {
    child.getBean("masked", Tag.class);
} catch (BeanNotOfRequiredTypeException expected) {
    mismatch = true;
}

if (!mismatch) {
    throw new AssertionError("Expected child name to mask parent Tag");
}
~~~

Đây là snippet; không dùng `NoSuchBeanDefinitionException` vì name `masked` **có tồn tại local**, chỉ sai Java type.

### 15.5. Test parent không thấy child

~~~java
if (parent.containsBean("childOnly")) {
    throw new AssertionError("Parent must not see child-only beans");
}
~~~

Nếu test trả true, hãy xem có bean cùng name `childOnly` đã vô tình đăng ký ở parent không, thay vì vội kết luận lookup tự đi từ parent xuống child.

### 15.6. Test dependency injection từ parent

Demo `ChildConsumer` có tham số `ParentContract` duy nhất trong parent. Assert:

~~~java
String value =
        child.getBean(ChildConsumer.class).contract().value();

if (!"parent-contract".equals(value)) {
    throw new AssertionError("Parent dependency injection failed");
}
~~~

Đây là snippet. Nếu parent có nhiều `ParentContract` beans hoặc child có name shadowing, cần cập nhật test để phản ánh candidate selection, chứ không mặc định lựa chọn luôn unique.

## 16. Checklist áp dụng

- [ ] Hiểu rõ cấu trúc parent/child contexts của ứng dụng.
- [ ] `setParent(parent)` trước khi refresh child.
- [ ] Không nhầm Java class inheritance với context hierarchy.
- [ ] Dùng `containsLocalBean` để hỏi về bean name thuộc local factory.
- [ ] Dùng `containsBean` để hỏi về name khả kiến qua ancestors.
- [ ] Dùng `containsBeanDefinition` khi cần biết local definition, không phải mọi singleton.
- [ ] Dùng `getBean(name)` khi muốn lấy một bean cụ thể theo name.
- [ ] Dùng `getBeanNamesForType` / `getBeansOfType` khi chỉ cần local beans.
- [ ] Dùng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors` khi cần mọi visible names theo type.
- [ ] Dùng `BeanFactoryUtils.beansOfTypeIncludingAncestors` khi cần visible instances.
- [ ] Không cộng arrays parent + child mà bỏ qua same-name shadowing.
- [ ] Hiểu child same-name bean che parent bean **kể cả khác type**.
- [ ] Không dùng `@Primary` để giải quyết vấn đề cùng-name masking.
- [ ] Có chiến lược names/qualifiers rõ nếu nhiều beans cùng type.
- [ ] Cẩn thận eager FactoryBean/type lookup/scope initialization.
- [ ] Parent không tự thấy child-only beans.
- [ ] Đóng child trước parent để bảo toàn lifecycle.
- [ ] Test hierarchy thật, không chỉ flat context.
- [ ] Không dùng thứ tự bean enumeration để quyết định nghiệp vụ.

## 17. Câu hỏi tự kiểm tra

**1. `child.getBean("parentOnly")` có thể tìm bean ở parent không?**  
Có, nếu child không có bean cùng name và factory được cấu hình hierarchy hợp lệ.

**2. `child.getBeanNamesForType(Tag.class)` có liệt kê Tag ở parent không?**  
Không. Đây là `ListableBeanFactory` method chỉ xét local factory.

**3. Làm sao lấy tất cả tên Tag khả kiến từ child, có cả parent?**  
Dùng `BeanFactoryUtils.beanNamesForTypeIncludingAncestors(child, Tag.class)`.

**4. `containsBean` và `containsLocalBean` khác gì?**  
`containsBean` có thể tìm parent; `containsLocalBean` bỏ qua ancestors.

**5. `containsBeanDefinition` có luôn giống `containsLocalBean` không?**  
Không. Registered singleton có thể local nhưng không có bean definition.

**6. Nếu parent và child đều có bean name `shared` thì child lấy cái nào?**  
Child bean thắng trong lookup bắt đầu từ child.

**7. Nếu child `masked` là String, parent `masked` là Tag, gọi `child.getBean("masked", Tag.class)` có lấy parent Tag không?**  
Không. Child cùng name che parent; có thể ném `BeanNotOfRequiredTypeException`.

**8. Trong tình huống trên, BeanFactoryUtils ancestor-aware type list có bao gồm parent masked Tag không?**  
Không, vì child local bean same-name đã che nó ngay cả khi khác type.

**9. Parent có lấy `childOnly` của child qua `parent.getBean` được không?**  
Không theo hierarchy lookup thông thường; parent không duyệt xuống descendants.

**10. `@Primary` có làm parent cùng-name bean xuất hiện trong ancestor scan không?**  
Không. Nó phục vụ chọn autowire candidate, không bỏ name shadowing.

**11. `getBeansOfType(Tag.class)` và `beansOfTypeIncludingAncestors` khác gì?**  
Một cái trả local map; cái kia tổng hợp map các beans khả kiến có xét ancestors.

**12. Có nên gọi `getBeansOfType` chỉ để liệt kê names trên request nóng không?**  
Không, vì việc lấy instances có thể trigger bean creation/scope work; dùng names API khi phù hợp.

**13. Một singleton bean ở parent được hai child dùng có thể cùng instance không?**  
Có, vì cả hai child có thể tham chiếu tới cùng singleton thuộc parent factory.

**14. Vì sao type-based injection từ parent có thể gặp ambiguity?**  
Nhiều candidates cùng type khả kiến hoặc candidate selection khác; cần names/qualifiers/primary rules.

**15. Context child nên đóng trước parent hay ngược lại?**  
Thường đóng child trước parent để dependencies trong parent còn khả dụng cho child khi cleanup.

## Tài liệu chính thức

- [Spring Framework 6.2 Javadoc — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/beans/factory/BeanFactory.html): parent fallback của `BeanFactory`, bean name precedence và typed name lookup.
- [Spring Framework 6.2 Javadoc — HierarchicalBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html): `getParentBeanFactory` và `containsLocalBean`.
- [Spring Framework 6.2 Javadoc — ListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html): local-only listing, `getBeanNamesForType`, `getBeansOfType`.
- [Spring Framework 6.2 Javadoc — BeanFactoryUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html): `beanNamesForTypeIncludingAncestors`, `beansOfTypeIncludingAncestors` và shadowing.
- [Spring Framework Reference — BeanFactory API](https://docs.spring.io/spring-framework/reference/core/beans/beanfactory.html): các loại IoC containers và interface contract.
- [Spring Framework Reference — Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html).
- [Spring Framework Reference — Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html): phân biệt bean definition inheritance với hierarchy của contexts.
- [Spring Framework 6.2 Javadoc — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-005">Post-processor hoạt động trong context nơi được đăng ký</a>

<details>
<summary>Xem chi tiết</summary>

# Post-processor hoạt động trong context nơi được đăng ký — Spring Core Container

## 1. Bài toán: cùng một hệ thống có nhiều ApplicationContext

Trong Spring, một ứng dụng có thể được chia thành **parent ApplicationContext** chứa các dịch vụ dùng chung và **child ApplicationContext** chứa các bean dành riêng cho một phần ứng dụng, chẳng hạn một web module, servlet hoặc test module.

Giả sử:

```text
ParentApplicationContext
  ├── parentService
  ├── parentBeanFactoryPostProcessor
  └── parentBeanPostProcessor
          ▲
          │ parent
ChildApplicationContext
  ├── childService
  ├── childBeanFactoryPostProcessor
  └── childBeanPostProcessor
```

Child có thể **tra cứu một số bean trong parent** khi bean tương ứng không được định nghĩa tại child. Tuy nhiên, điều đó **không có nghĩa** các post-processors sẽ tự được kế thừa và chạy xuyên qua hai container.

Đây là nội dung chính của bài:

> **`BeanFactoryPostProcessor` và `BeanPostProcessor` hoạt động theo phạm vi container sở hữu chúng. Một post-processor được đăng ký tại parent không mặc nhiên xử lý definitions/instances của child, và post-processor tại child cũng không mặc nhiên xử lý các beans do parent tạo.**

Tài liệu Spring gọi quy tắc này là **per-container scope**. Điều kiện này đặc biệt quan trọng khi một ứng dụng có parent-child contexts: sự **nhìn thấy bean** trong hierarchy không tương đương với sự **lan truyền pipeline post-processing**.

Bài giải thích rõ hai extension points, thời điểm xử lý, lookup giữa các contexts, tác động lên Spring AOP/`@Autowired`, những trường hợp cần cấu hình lại tại child, và một ví dụ **Java 17 + Spring Framework 6.2** chạy được.

## 2. Hai extension points thường bị nhầm

Spring IoC Container hỗ trợ hai loại post-processor quan trọng:

| Tiêu chí | `BeanFactoryPostProcessor` (BFPP) | `BeanPostProcessor` (BPP) |
|---|---|---|
| Tác động chính | **BeanDefinition metadata** | **Bean instance** trong quá trình tạo |
| Callback quen thuộc | `postProcessBeanFactory(ConfigurableListableBeanFactory)` | `postProcessBeforeInitialization(Object,String)`; `postProcessAfterInitialization(Object,String)` |
| Thời điểm | Bootstrap/refresh, **trước khi khởi tạo application singletons thông thường** | Trong pipeline khởi tạo từng bean ở bean factory sở hữu nó |
| Có thể chỉnh gì? | Property values, scope/metadata, registry qua BDRPP extension | Field/instance state, kiểm tra annotation, wrapping/proxy |
| Không nên làm gì? | Không gọi `getBean()` để ép application bean tạo sớm | Không xem đây là nơi sửa blueprint của mọi bean đã được tạo |
| Phạm vi trong hierarchy | **BFPP của context nào xử lý definitions của context đó** | **BPP của context nào xử lý instances do factory đó tạo** |
| Ví dụ thực tế | `PropertySourcesPlaceholderConfigurer`, custom definition mutation | Annotation injection, lifecycle annotation processors, auto-proxying |
| Nếu đăng ký ở parent | Không tự sửa child definitions | Không tự áp lên child-created beans |
| Nếu đăng ký ở child | Không tự sửa parent definitions | Không tự áp lên parent-created beans |

Spring còn có **`BeanDefinitionRegistryPostProcessor` (BDRPP)**, mở rộng `BeanFactoryPostProcessor`, có thể đăng ký thêm definitions vào **registry của factory đang được xử lý**. Nó cũng không phải phép tự động mở rộng phạm vi sang parent và child.

### 2.1. Điều quan trọng nhất: ownership của BeanFactory

Mỗi `ApplicationContext` tương ứng với **vòng đời và BeanFactory riêng**. Parent và child có thể liên hệ qua hierarchy, nhưng `refresh()` của child chạy các bước bootstrap trên **child bean factory**.

Không suy rằng:

```text
child.getBean("parentService") thành công
    ⇒ childBeanPostProcessor xử lý parentService
```

Kết luận đó **sai**. `child.getBean(...)` có thể delegate sang **parent bean factory**, nơi parent tạo hoặc trả instance, và chỉ áp pipeline post-processors đã đăng ký tại parent.

### 2.2. Vì sao framework chọn per-container?

Per-container giúp:

1. **Cô lập cấu hình**: một child web module có BPP riêng không vô tình can thiệp service của parent.
2. **Phân biệt lifecycle**: parent và child có thể được refresh/close vào những thời điểm khác nhau.
3. **Giữ ownership rõ**: BFPP sửa đúng registry chứa definitions mà nó có quyền xử lý.
4. **Tránh double wrapping**: một singleton của parent không tự bị bọc lại khi child truy cập.
5. **Dễ vận hành module hóa**: từng context có extension points phù hợp vai trò của nó.

Việc child lookup được parent bean là **khả năng resolution/dependency injection**, không phải chỉ dẫn để chạy lại các post-processing callbacks.

## 3. Phân cấp ApplicationContext hoạt động thế nào?

### 3.1. Parent-child relationship

Một ví dụ với Java Config:

```java
var parent = new AnnotationConfigApplicationContext();
parent.register(ParentConfig.class);
parent.refresh();

var child = new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
```

Đây là **snippet tích hợp**; cần hai configuration classes, import `AnnotationConfigApplicationContext`, và cần đóng child trước parent sau khi sử dụng. Ví dụ đầy đủ ở phần 9.

Child được gắn parent **trước `refresh()`** để Spring có cơ sở resolve những dependencies từ ancestor và xây hierarchy thích hợp.

### 3.2. Local definitions và parent beans

Giả sử parent khai báo:

```java
@Bean("sharedCache")
CacheService sharedCache() {
    return new CacheService();
}
```

Và child không khai báo `sharedCache`. Khi code gọi:

```java
CacheService cache = child.getBean(
        "sharedCache", CacheService.class);
```

Đây là snippets minh họa cần class `CacheService` và config tương ứng. Child có thể tìm thấy bean parent thông qua hierarchy, nhưng `CacheService` đó **vẫn do parent bean factory quản lý**.

Nếu child tự có bean tên `sharedCache`, local bean có thể **shadow** bean cùng tên của parent trong lookup theo tên. Điều này không sửa hoặc destroy parent definition; hai factories có namespaces/ownership riêng.

### 3.3. containsBean so với containsBeanDefinition

Hai câu hỏi khác nhau:

```java
child.containsBean("sharedCache");
child.containsBeanDefinition("sharedCache");
```

- `containsBean` có thể thấy bean từ ancestor hierarchy khi local bean không có.
- `containsBeanDefinition` kiểm tra **definition nằm trong registry của context được hỏi**, không tự đồng nhất với inherited bean visibility.

Ví dụ child có thể báo:

```text
child.containsBean("sharedCache")           = true
child.containsBeanDefinition("sharedCache") = false
```

Và điều đó **không mâu thuẫn**. Nó cho thấy bean có thể resolve nhưng definition không thuộc child.

### 3.4. Parent và child là quan hệ tra cứu một chiều

Child có thể tham chiếu lên parent; parent **không tự biết** các beans ở child để inject xuống. Parent BFPP không tự nhận child registry. Parent BPP không tự quan sát beans mới do child tạo.

Không coi parent-child hierarchy là hai container bị gộp thành một `BeanFactory` lớn có chung `BeanDefinitionRegistry` và chung mọi BPPs.

## 4. BeanFactoryPostProcessor chỉ chỉnh metadata cục bộ

`BeanFactoryPostProcessor` nhận callback:

```java
void postProcessBeanFactory(
        ConfigurableListableBeanFactory beanFactory)
        throws BeansException;
```

Chữ ký trên là **trích lược** của Spring SPI. Spring truyền vào **bean factory của context đang chạy refresh**, không phải một cấu trúc magically bao gồm mọi parent/child factories.

Ví dụ:

```java
@Bean
public static BeanFactoryPostProcessor changeTimeout() {
    return factory -> factory
            .getBeanDefinition("localClient")
            .getPropertyValues()
            .add("timeoutSeconds", 15);
}
```

Đây là **snippet tích hợp**, cần một `@Configuration` đã được register, import `@Bean`, `BeanFactoryPostProcessor` và một bean definition `localClient` có writable property `timeoutSeconds`.

Nếu snippet được đặt trong `ChildConfig`, nó chỉnh **localClient của child**, không phải một bean `localClient` chỉ nằm ở parent.

### 4.1. Metadata đã có, instance chưa được tạo

Trong refresh, Spring gọi các BFPPs sau khi phần lớn cấu hình definitions đã được chuẩn bị nhưng **trước khi pre-instantiate application singletons thông thường**. Vì vậy BFPP có thể thay giá trị property mà Spring sẽ áp khi tạo bean sau đó.

Đừng viết:

```java
@Bean
public static BeanFactoryPostProcessor unsafe() {
    return factory -> {
        Object bean = factory.getBean("businessService");
        // Không ép service tạo sớm chỉ để chỉnh state.
    };
}
```

Đây là **anti-pattern**. Việc gọi `getBean()` trong BFPP có thể dẫn đến eager instantiation trước khi BPPs/lifecycle infrastructure được đăng ký đầy đủ. Kết quả có thể làm mất AOP proxy hoặc bỏ qua các initialization hooks mong đợi.

### 4.2. Có thể “với tay” vào parent bằng code riêng không?

Một application code có thể giữ reference tới parent context rồi gọi methods để tương tác parent. Nhưng đó là **một thao tác tường minh khác**, không phải cơ chế BFPP scope mặc định.

Cụ thể, nếu child BFPP gọi `context.getParent().getBean(...)`, nó có thể lookup/instantiate một parent bean (thậm chí gây side effects nguy hiểm); điều đó **không chứng minh Spring đã áp child BFPP lên parent BeanDefinitionRegistry**.

Nếu cố chỉnh parent definitions sau khi parent đã refresh, các parent singletons có thể đã được tạo; thay metadata muộn thường **không hồi tố** object đang tồn tại.

### 4.3. BDRPP cũng gắn với local BeanDefinitionRegistry

`BeanDefinitionRegistryPostProcessor` có method:

```java
void postProcessBeanDefinitionRegistry(
        BeanDefinitionRegistry registry);
```

Đây là chữ ký rút gọn, không phải một class hoàn chỉnh. Nếu một BDRPP ở child gọi `registry.registerBeanDefinition(...)`, definition mới được đăng ký vào **child registry**.

Ví dụ minh họa:

```java
registry.registerBeanDefinition(
        "localAddon",
        new RootBeanDefinition(AddonService.class));
```

Đây là snippet tích hợp; cần `BeanDefinitionRegistry registry`, `RootBeanDefinition`, `AddonService`. Đừng nghĩ nó tự tạo cùng definition trong parent.

### 4.4. Vì sao BeanFactoryPostProcessor nên được đăng ký sớm?

Với `@Bean` trả về BFPP, khai báo method `static` giúp Spring khởi tạo processor **không cần khởi tạo sớm instance của `@Configuration` class**, tránh cản trở `@Autowired`, `@Value`, `@PostConstruct` và các callbacks vốn cần infrastructure tạo đủ.

```java
@Configuration(proxyBeanMethods = false)
class LocalConfig {
    @Bean
    public static BeanFactoryPostProcessor localBfpp() {
        return beanFactory -> {
            // Read/change local BeanDefinitions here.
        };
    }
}
```

Đây là snippet tích hợp. `static` không làm processor global; nó chỉ giúp đăng ký BFPP đúng thời điểm.

## 5. BeanPostProcessor chỉ chạy trên những instances do local factory tạo

`BeanPostProcessor` định nghĩa hai extension hooks:

```java
Object postProcessBeforeInitialization(
        Object bean, String beanName);

Object postProcessAfterInitialization(
        Object bean, String beanName);
```

Đây là chữ ký trích lược. Một BPP có thể quan sát, thay đổi hoặc thay thế bean object, kể cả trả proxy để hỗ trợ AOP.

Ví dụ:

```java
public final class LabelProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(
            Object bean, String beanName) {
        if (bean instanceof LabelledService service) {
            service.setLabel(service.getLabel() + " | local-bpp");
        }
        return bean;
    }
}
```

Snippet này cần `BeanPostProcessor` và class `LabelledService` có getters/setters. Khi BPP được đăng ký ở child, callbacks áp dụng cho **child-created beans** phù hợp, không phải instances do parent factory sở hữu.

### 5.1. Lookup parent bean thông qua child có bị xử lý lại không?

**Không**, trong hành vi mặc định.

Dòng:

```java
Object fromChild = child.getBean("parentService");
```

có thể **trả chính parent instance đã tồn tại**. `BeanPostProcessor` ở child không tự nhận `postProcessAfterInitialization` chỉ vì `getBean` trả object qua hierarchy.

Nếu parent tạo bean chưa từng được instantiate, lookup qua child có thể khiến parent instantiate nó tại thời điểm đó. Nhưng nó vẫn được tạo trong **parent factory**, sử dụng **parent post-processors**.

### 5.2. BPP ở parent có xử lý child-created beans không?

Cũng **không**. Một BPP đăng ký ở parent được thêm vào **danh sách BeanPostProcessors của parent bean factory**, không phải danh sách của child.

Đừng hiểu việc child inject `parentService` làm BPP của parent “đi xuống” xử lý mọi collaborator ở child. Parent BPP chỉ xử lý bean tạo ở parent; child beans thuộc child creation pipeline.

### 5.3. BeanPostProcessor registration diễn ra trước normal singleton creation

Trong `AbstractApplicationContext.refresh()`, Spring chủ động tìm và đăng ký **local `BeanPostProcessor` beans** trước bước tạo phần lớn application singletons.

Nếu BPP được đăng ký **quá muộn**, các singleton đã tạo không tự đi qua các callbacks lần nữa. Vì thế `ApplicationContext` có auto-detection, còn `BeanFactory` thuần thường cần setup thủ công.

### 5.4. Vì sao static @Bean cũng có lợi với BPP?

Spring Reference khuyến nghị `@Bean` method khai báo BPP nên là `static` khi phù hợp và có return type nhận diện được (implementation type hoặc `BeanPostProcessor`). Nếu muốn dựng BPP mà phải tạo `@Configuration` instance hoặc eager instantiate nhiều dependencies, một số beans có thể được tạo **trước khi toàn bộ BPPs được đăng ký**, gây warning không được xử lý bởi tất cả processors, ví dụ không eligible for auto-proxying.

```java
@Bean
public static BeanPostProcessor localProcessor() {
    return new LabelProcessor();
}
```

Đây là snippet tích hợp. Nó chỉ cải thiện thời điểm đăng ký; **không thay đổi phạm vi theo context**.

## 6. BeanDefinition registry, visible beans và post-processing là ba trục khác nhau

Có ít nhất ba câu hỏi cần hỏi riêng:

1. **BeanDefinition được đăng ký ở đâu?** Parent hay child?
2. **Bean instance thực sự được tạo bởi BeanFactory nào?** Thường là factory chứa local definition được chọn sau lookup.
3. **Post-processors nào được đăng ký với factory đó?** Danh sách BPP/BFPP cục bộ của context chủ sở hữu.

Một ví dụ phân tích:

| Bean được lookup từ | Definition thuộc | Object do ai tạo | BFPP nào từng chỉnh definition | BPP nào xử lý object |
|---|---|---|---|---|
| `parent.getBean("parentService")` | Parent | Parent | Parent BFPP | Parent BPP |
| `child.getBean("parentService")` | Parent | Parent | Parent BFPP | Parent BPP |
| `child.getBean("childService")` | Child | Child | Child BFPP | Child BPP |
| `parent.getBean("childService")` | Child | N/A | N/A | N/A (parent không thấy child) |
| `child.getBean("shared")` khi child shadow parent `shared` | Child | Child | Child BFPP | Child BPP |
| `parent.getBean("shared")` khi cả hai định nghĩa `shared` | Parent | Parent | Parent BFPP | Parent BPP |

Bảng giả định không có custom `BeanFactory`, `FactoryBean`, scoped proxy hoặc thủ công thay ownership; đó là mô hình tiêu chuẩn để hiểu hierarchy.

### 6.1. Child nhìn thấy parent bean không phải kế thừa object lifecycle

Khi child đóng, không có nghĩa parent-owned singleton đó bị destroy. Parent sẽ quản lý cleanup của parent bean vào lúc parent đóng. Vì vậy, parent-created bean lấy qua child **không nên bị child BPP thay thế bằng một object có lifecycle riêng** theo suy đoán.

### 6.2. Shadowing không có nghĩa parent BeanDefinition biến mất

Nếu parent và child đều khai báo tên `sharedClient`, child local definition che khuất bean parent trong **lookup từ child**. Parent bean vẫn tồn tại, vẫn được parent BFPP/BPP xử lý. Parent `getBean("sharedClient")` trả bean của parent.

Nếu shadowing tình cờ vì trùng tên khi chia modules, DI có thể chọn implementation khác dự kiến. Test tên và kiểu trong context hierarchy, không chỉ kiểm tra interface tồn tại.

## 7. Spring AOP, annotation processors và DI trong nhiều context

Nhiều tính năng Spring dựa trên `BeanPostProcessor` hoặc hạ tầng tương tự. Vì vậy per-container scope có hệ quả trực tiếp.

### 7.1. AOP/proxy

Spring AOP thường dùng `BeanPostProcessor` để tạo proxy cho các beans phù hợp với advisors, pointcuts hoặc annotations. Nếu một auto-proxy infrastructure được bật **chỉ ở child**, nó không tự proxy **parent-created service** dù service ấy được inject vào child controller.

Ví dụ kiến trúc:

```text
Parent:
    @Service BillingService
    không có auto-proxy setup tương ứng

Child:
    web controllers
    custom auto-proxy BeanPostProcessor
    controller gọi parent BillingService
```

Trong setup này, child auto-proxy BPP **không có cơ hội xử lý** instance `BillingService` do parent tạo. Vì vậy các tính năng áp dụng qua AOP proxy **phải được cấu hình tại context chứa bean mục tiêu**.

**Không khẳng định** mọi annotation trong Spring đều luôn do cùng một BPP xử lý: `@Transactional`, `@Async`, `@Cacheable`, `@Autowired` sử dụng những hạ tầng cụ thể, gồm registrars, advisors và post-processors khác nhau. Tuy nhiên, hậu quả phổ quát là **đăng ký infrastructure ở sai context có thể khiến bean mục tiêu không nhận hành vi mong muốn**.

### 7.2. @Transactional và transaction management

Với proxy-based transaction management, cấu hình transaction infrastructure ở **context nơi services được tạo** là quy tắc quan trọng. Một child web context bật `@EnableTransactionManagement` không mặc định khiến parent service có proxy transaction nếu parent chưa cấu hình.

Ví dụ:

```java
@Configuration
@EnableTransactionManagement
class TransactionConfig {
    // PlatformTransactionManager bean/infrastructure ...
}
```

Đây là snippet tích hợp; cần `spring-tx`, transaction manager và context phù hợp. Việc bật `@EnableTransactionManagement` sai tầng sẽ gây lỗi “method có `@Transactional` nhưng không có transaction” dù code compile.

### 7.3. @Autowired và annotation injection

`AnnotationConfigApplicationContext` chuẩn đăng ký common annotation post-processors trong **mỗi context được tạo bằng cơ chế này**. Vì vậy, trong nhiều ví dụ thực tế, cả parent và child đều có cơ sở tự xử lý `@Autowired` cho các local beans.

Nhưng nếu dùng một **`DefaultListableBeanFactory` thuần** không có infrastructure tương ứng, Spring không tự auto-detect/register mọi processors như `ApplicationContext`. Đừng suy rằng cha có autowiring infrastructure thì child factory thuần chắc chắn được thừa hưởng.

### 7.4. MVC/web annotations

Một child web context cấu hình MVC có thể cài nhiều adapters, resolvers và scoped/annotation infrastructure liên quan web layer. Một parent root context có những services, repositories riêng. Không đăng ký web-specific post-processor ở parent chỉ vì nghĩ context hierarchy sẽ tự tái sử dụng tất cả.

Hãy đặt infrastructure ở **tầng tạo bean mà nó cần xử lý**, trừ khi tài liệu của feature xác định một cơ chế hierarchy-aware khác.

## 8. Quy trình refresh trong từng context

Với parent-child `AnnotationConfigApplicationContext`:

```text
parent.register(ParentConfig)
parent.refresh()
   -> phân tích parent configuration
   -> gọi parent BFPP và BDRPP
   -> đăng ký parent BPP
   -> tạo/khởi tạo parent application beans

child.setParent(parent)
child.register(ChildConfig)
child.refresh()
   -> phân tích child configuration
   -> gọi child BFPP và BDRPP
   -> đăng ký child BPP
   -> tạo/khởi tạo child application beans
   -> khi cần parent dependency thì lookup parent
```

Mỗi phần xử lý được thực hiện bởi **context tương ứng**. Child có thể cần parent đã refresh trước để parent beans sẵn sàng và dependency lookup hoạt động ổn định.

### 8.1. BFPP không nhận parent registry ngầm

Khi `child.refresh()`, BFPP callback nhận **child `ConfigurableListableBeanFactory`**. `getBeanDefinitionNames()` trả **local definitions** theo factory đó. Nó không có nghĩa toàn bộ parent beans được copy thành local definitions để mutate.

### 8.2. BPP không tự bao quanh delegated getBean

Khi child không có bean name và hỏi parent, delegation đến parent bean factory xảy ra. Đường xử lý bean creation diễn ra tại **parent**. Child BPP không bị insert vào giữa lúc parent tạo bean rồi trả object.

### 8.3. Tạo context xong mới đăng ký processor không tự xử lý lại bean

Nếu bạn tự gọi `child.getBeanFactory().addBeanPostProcessor(...)` **sau khi child đã refresh và tạo singletons**, nó chỉ có thể ảnh hưởng **những beans còn được tạo về sau** theo contract của factory; không tự hồi tố singleton instances hiện có.

Với BFPP, sau khi bean đã được tạo, sửa definition không đồng nghĩa chạy lại constructor/setter/init callbacks. Nên chuẩn bị toàn bộ processors **trước refresh**.

### 8.4. Close và lifecycle ownership

Một parent singleton có thể được child inject, nhưng lifecycle destroy của singleton đó vẫn thuộc parent factory. Đóng child nên hủy **child-managed beans**, không tự hủy parent-managed singletons. Đóng parent sau child giúp tránh child còn đang chạy và gọi dependency parent đã bị destroy.

Không biến BFPP/BPP scope thành cơ chế kiểm soát thời điểm close: dùng `DisposableBean`, `@PreDestroy`, `SmartLifecycle` và quản lý parent-child shutdown order khi thích hợp.

## 9. Ví dụ Java 17 + Spring Framework 6.2: kiểm chứng per-container

Đây là chương trình độc lập với **hai `AnnotationConfigApplicationContext`** trong cùng JVM. Nó chứng minh:

- Parent BFPP chỉ chỉnh definition `parentService`.
- Child BFPP chỉ chỉnh definition `childService`.
- Parent BPP chỉ ghi dấu lên `parentService`.
- Child BPP chỉ ghi dấu lên `childService`.
- Child lookup `parentService` thành công, nhưng **không** thêm nhãn `child-bpp` vào parent instance.
- Child không có local BeanDefinition mang tên `parentService`, dù `containsBean("parentService")` có thể true qua delegation.
- Hai context có thể cùng tồn tại và đóng theo thứ tự child trước parent.

### 9.1. Cấu trúc dự án

```text
processor-scope-demo/
├── pom.xml
└── src/main/java/example/ProcessorScopeDemo.java
```

Yêu cầu JDK 17+ và Maven 3.9+. Không dùng Spring Boot hoặc web server.

### 9.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>processor-scope-demo</artifactId>
    <version>1.0.0</version>

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

### 9.3. src/main/java/example/ProcessorScopeDemo.java

```java
package example;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class ProcessorScopeDemo {

    public static void main(String[] args) {

        try (var parent = new AnnotationConfigApplicationContext()) {
            parent.register(ParentConfig.class);
            parent.refresh();

            try (var child = new AnnotationConfigApplicationContext()) {
                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                Service parentObject = parent.getBean(
                        "parentService", Service.class);
                Service fromChild = child.getBean(
                        "parentService", Service.class);
                Service childObject = child.getBean(
                        "childService", Service.class);

                System.out.println("parent result: "
                        + parentObject.getLabel());
                System.out.println("child sees parent: "
                        + fromChild.getLabel());
                System.out.println("child result: "
                        + childObject.getLabel());

                System.out.println("same parent object: "
                        + (parentObject == fromChild));
                System.out.println("child resolves parent: "
                        + child.containsBean("parentService"));
                System.out.println("child has local parent definition: "
                        + child.containsBeanDefinition("parentService"));
            }
        }
    }

    public static final class Service {
        private String label;

        public void setLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // Cùng một processor class có thể được tạo RIÊNG cho mỗi context.
    public static final class LocalLabelProcessor
            implements BeanPostProcessor {

        private final String contextTag;

        public LocalLabelProcessor(String contextTag) {
            this.contextTag = contextTag;
        }

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String beanName) {

            if (bean instanceof Service service) {
                service.setLabel(
                        service.getLabel() + "|" + contextTag);
                System.out.println(contextTag + " BPP: " + beanName);
            }
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean
        public static BeanFactoryPostProcessor parentMetadata() {
            return factory -> {
                factory.getBeanDefinition("parentService")
                        .getPropertyValues()
                        .add("label", "parent-bfpp");
                System.out.println("parent BFPP: parentService");
            };
        }

        @Bean
        public static BeanPostProcessor parentProcessor() {
            return new LocalLabelProcessor("parent");
        }

        @Bean("parentService")
        public Service parentService() {
            return new Service();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean
        public static BeanFactoryPostProcessor childMetadata() {
            return factory -> {
                factory.getBeanDefinition("childService")
                        .getPropertyValues()
                        .add("label", "child-bfpp");
                System.out.println("child BFPP: childService");
            };
        }

        @Bean
        public static BeanPostProcessor childProcessor() {
            return new LocalLabelProcessor("child");
        }

        @Bean("childService")
        public Service childService() {
            return new Service();
        }
    }
}
```

### 9.4. Chạy chương trình

Đứng ở thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.ProcessorScopeDemo
```

Các dòng do chương trình in (không kể logging riêng của Maven/Spring):

```text
parent BFPP: parentService
parent BPP: parentService
child BFPP: childService
child BPP: childService
parent result: parent-bfpp|parent
child sees parent: parent-bfpp|parent
child result: child-bfpp|child
same parent object: true
child resolves parent: true
child has local parent definition: false
```

### 9.5. Giải thích từng kết quả

**`parent BFPP: parentService`**

Khi `parent.refresh()`, `parentMetadata` được đăng ký thành BFPP của **parent factory**. Nó thêm property metadata `label = "parent-bfpp"` vào local definition `parentService`. Nó không cần `getBean` và không ép `Service` tạo sớm.

**`parent BPP: parentService`**

Sau khi Spring tạo `Service` từ `@Bean("parentService")`, bean property population áp giá trị `"parent-bfpp"` thông qua setter. `parentProcessor` được đăng ký với parent bean factory, nên callback `postProcessAfterInitialization` nối `"|parent"` vào label.

**`child BFPP: childService`**

Khi `child.refresh()`, `childMetadata` nhận **child BeanFactory** và chỉ chỉnh `childService`. Nó không được đưa local parent definition `parentService` vào phạm vi xử lý.

**`child BPP: childService`**

Child tạo `childService` và gọi **child BPP**. `LocalLabelProcessor("child")` nối `"|child"` vào local instance. Child BPP **không** được gọi cho `parentService`.

**`parent result` và `child sees parent`**

Cả hai đều là `parent-bfpp|parent`. Việc child lookup parent object **không làm xuất hiện** một hậu tố `|child`. Đây là bằng chứng trực tiếp về BPP per-container.

**`child result`**

Kết quả `child-bfpp|child` thể hiện chỉ child BFPP/BPP tác động vào child-owned instance.

**`same parent object: true`**

Vì parent bean scope mặc định `singleton`, cả parent và child lookup đều trả cùng parent-managed singleton object. Child không tạo một bản sao để tự xử lý qua child BPP. Nếu parent bean là `prototype`, mỗi yêu cầu có thể tạo instance khác, nhưng **vẫn do parent factory tạo và post-process**.

**`child resolves parent: true` nhưng `child has local parent definition: false`**

Một bean **visible qua ancestor** không phải một local `BeanDefinition`. `containsBeanDefinition` là một công cụ debug hữu ích khi kiểm tra đúng ownership.

### 9.6. Vì sao @Bean methods trả BFPP/BPP được khai báo static?

Trong cả hai configurations, `parentMetadata`, `childMetadata`, `parentProcessor`, `childProcessor` là `static @Bean` methods. Processor beans cần được chuẩn bị **sớm**; static factory methods tránh ép Spring khởi tạo configuration instance trước hạ tầng cần thiết.

Đây là best practice khi khai báo post-processors bằng Java Config, **không phải** lý do khiến processors “chạy toàn JVM”.

### 9.7. Vì sao BFPP đặt property thay vì gọi setter trực tiếp?

`BeanFactoryPostProcessor` được dùng để sửa **BeanDefinition property values**:

```java
factory.getBeanDefinition("childService")
        .getPropertyValues()
        .add("label", "child-bfpp");
```

Khi Spring tạo instance sau đó, property population gọi setter thích hợp. Nếu bạn gọi `factory.getBean("childService")` trong BFPP rồi `setLabel` trực tiếp, bạn có nguy cơ tạo bean quá sớm, bỏ qua hoặc thay đổi thứ tự post-processing.

### 9.8. Những điều demo không cố chứng minh

- Không minh họa `@Transactional` hoặc auto-proxying thực tế; BPP demo chỉ nối label để quan sát.
- Không minh họa `BeanDefinitionRegistryPostProcessor` đăng ký thêm bean; chỉ nhắc SPI để hiểu scope.
- Không dùng Spring Boot, web application root/servlet child contexts hoặc custom hierarchical BeanFactory.
- Không dùng lazy/prototype trong stdout, nhằm giữ thứ tự creation dễ xác định.
- Không sửa parent metadata từ child bằng code tường minh, vì đó **không phải default semantics** của post-processors.

## 10. Một chiều inheritance và quy tắc override tên bean

Một hierarchy có thể có bean trùng tên:

```text
parent:
    @Bean("customerService") ParentCustomerService

child:
    @Bean("customerService") ChildCustomerService
```

Khi child yêu cầu `customerService` theo tên, local bean thường thắng. Nhưng bean parent vẫn tồn tại và chịu post-processing ở parent.

### 10.1. Parent và child cùng có BPP type giống nhau thì sao?

Hai containers có thể cùng đăng ký instances riêng của **cùng một `BeanPostProcessor` class**. Điều đó **không biến chúng thành một processor global**. Mỗi instance ở một factory xử lý những beans do factory đó tạo.

Ví dụ trong demo, `LocalLabelProcessor` class được dùng ở cả parent và child, nhưng hai instances khác nhau với `contextTag` khác nhau.

### 10.2. “Cho parent BPP xử lý child beans” bằng cách nào?

Nếu thật sự muốn cùng logic được áp cho nhiều contexts, có các lựa chọn:

1. **Đăng ký một processor instance mới** trong từng context, cùng một implementation class.
2. Đăng ký cùng một **configuration module** ở cả parent và child, với điều kiện không vô tình tạo lại các business beans hoặc gây collisions.
3. Với use case đặc biệt, **đăng ký một BPP bằng code** vào mỗi target `BeanFactory`, lưu ý state/thread safety và ordering.
4. Tránh “chạy BPP bằng tay” trên beans đã tạo trừ khi kiểm soát chặt proxy semantics và bean lifecycle.

**Khuyến nghị phổ biến:** tái sử dụng **class/cấu hình**, không chia sẻ một BPP instance stateful duy nhất xuyên nhiều factories.

### 10.3. Không phải mọi bean đều cần cùng BPP

Một web-specific BPP có thể chỉ hợp lý cho controllers/views trong child; một transaction proxy processor thường cần ở context chứa transactional services. Đăng ký mọi thứ ở cả hai contexts có thể tốn memory, tạo proxy/chồng cấu hình không cần thiết hoặc tạo side effects khó chẩn đoán.

Tư duy đúng là: **processor cần áp cho bean thuộc context nào thì đăng ký vào context đó**.

## 11. BeanFactoryPostProcessor và BeanPostProcessor trong context bootstrap sâu hơn

### 11.1. Thứ tự các bước quan trọng

Trong một `AbstractApplicationContext.refresh()` điển hình:

```text
prepareRefresh()
obtainFreshBeanFactory()
prepareBeanFactory()
postProcessBeanFactory()
invokeBeanFactoryPostProcessors()  <-- BFPP/BDRPP xử lý definitions
registerBeanPostProcessors()       <-- đăng ký BPPs cục bộ
initMessageSource()
initApplicationEventMulticaster()
onRefresh()
registerListeners()
finishBeanFactoryInitialization()   <-- non-lazy singleton app beans
finishRefresh()                     <-- context lifecycle finish
```

Đây là sơ đồ thứ tự khái quát, không phải toàn bộ template method callbacks, và một số infrastructure beans có thể cần được khởi tạo sớm trước application singletons.

**Điểm cốt lõi:** BFPP chạy đủ sớm để sửa metadata; BPP được đăng ký trước khi các application beans thông thường được khởi tạo; và tất cả điều đó được orchestrate **cho bean factory của context hiện tại**.

### 11.2. Processor ordering

Các BFPP/BPP beans được autodetect trong `ApplicationContext` có thứ tự theo `PriorityOrdered`/`Ordered` và các quy tắc cụ thể của Spring. **Đừng nhầm processor ordering với hierarchy propagation**.

Một BPP có `@Order(1)` trong child **không thể** làm nó nhảy sang xử lý bean trong parent. `Ordered` chỉ sắp thứ tự giữa processors được đưa vào cùng pipeline.

Với processors được **programmatic registration**, ordering semantics có thể khác: Spring Javadoc nêu rằng processors được thêm bằng API trực tiếp thường phụ thuộc registration order, không phải tự sort như autodetected processor beans.

### 11.3. BDRPP registration phases

BDRPP được Spring ưu tiên xử lý vì nó có thể đăng ký thêm definitions mà những BFPPs khác cần thấy. `ConfigurationClassPostProcessor` là một BDRPP đặc biệt tham gia xử lý Java configuration.

Không nên tự tạo BDRPP phụ thuộc parent child để đăng ký definitions không idempotent. Một lần refresh local có thể gọi processors theo nhiều phases; code nên tuân contracts, không dựa vào một counter global.

### 11.4. FactoryBean và product instances

`FactoryBean<T>` bản thân là bean do một factory quản lý, còn object sản phẩm lấy qua `getBean("name")` có creation semantics riêng. Trong những đường xử lý thông dụng, post-processing sản phẩm `FactoryBean` cũng sử dụng các processors thuộc **factory tạo sản phẩm**, không phải processors thuộc child chỉ vì child lookup name qua parent.

Nếu xử lý factory products/proxies phức tạp, hãy test với đúng scope/synthetic flags và API `FactoryBean` implementation. Không lấy demo ordinary `Service` làm kết luận về mọi đường hạ tầng ít phổ biến.

### 11.5. BeanPostProcessor instances có được xử lý bởi chính mình không?

Processor registration là giai đoạn đặc biệt, có những limitations: BPP instances và các dependencies được tạo sớm thường **không eligible for full post-processing** bởi mọi BPPs, gồm AOP auto-proxying. Không dựa vào BPP tự proxy chính nó hoặc đủ mọi `@PostConstruct` trong một construction graph phức tạp.

Tạo BPP càng đơn giản càng tốt, tránh phụ thuộc business service beans trong constructor để không gây eager initialization. Khi cần collaborators, cân nhắc chỉ dùng infrastructure dependencies và kiểm tra initialization order.

## 12. Các trường hợp ứng dụng thực tế

### 12.1. Root context và DispatcherServlet child context

Trong ứng dụng Spring MVC truyền thống:

```text
Root WebApplicationContext
  ├── repositories
  ├── domain services
  ├── transaction manager
  └── service-oriented processors

DispatcherServlet child WebApplicationContext
  ├── controllers
  ├── MVC adapters/resolvers
  └── web-specific processors
```

Nếu bật một annotation-driven proxy mechanism **chỉ ở child**, nó chủ yếu xử lý **child-owned beans**. Parent service có thể inject vào child controllers, nhưng không tự nhận child proxy.

Ví dụ `@EnableTransactionManagement` đặt trong root nơi tạo transactional services; MVC-specific annotation infrastructure đặt ở servlet child. Đây là guideline về ownership, không phải quy tắc rằng mọi ứng dụng đều buộc phải có hai contexts.

### 12.2. Plugin context được tạo động

Một nền tảng plugin có parent core context và mỗi plugin một child context:

- Core services ở parent.
- Plugin handlers ở child riêng.
- Plugin-specific BPP áp cho plugin-owned beans.
- Core global BPP không tự xuống plugin children.

Nếu plugin đòi nhận một shared tracing/proxy policy, đăng ký tracing BPP trong từng plugin context qua cấu hình tái sử dụng; xác định đúng classloader, lifecycle và shutdown order.

### 12.3. Test context khác production

Một test có thể vô tình dùng một context duy nhất, khiến custom BPP áp mọi beans và test pass. Production dùng root + child, khiến processor chỉ ở child và bean mục tiêu thuộc parent, nên behavior không xuất hiện.

Để tránh false confidence, viết **integration test với cùng context hierarchy** production, assert bean owner và processor effects, không chỉ test method output.

### 12.4. Parent bean bị “thiếu @Transactional” khi gọi từ child

Checklist:

1. Transactional service được định nghĩa ở parent hay child?
2. `@EnableTransactionManagement` và transaction auto-proxy infrastructure được đăng ký ở context nào?
3. Bean trả về có proxy type phù hợp không (`AopUtils.isAopProxy` trong Spring AOP test)?
4. Có self-invocation bypass proxy không?
5. Có transaction manager phù hợp không?
6. Có expectation child processor xử lý parent-created bean sai không?

**Không mặc định** mọi lỗi transaction đều do scope; cần kiểm tra proxy path, method visibility, transaction manager và caller semantics.

### 12.5. Property placeholder configurer

Một `PropertySourcesPlaceholderConfigurer` là BFPP. Nếu bạn đặt nó chỉ ở parent, nó không mặc nhiên xử lý child bean definitions khi child refresh.

Tuy nhiên child có thể kế thừa/merge `Environment` property sources theo context initialization, và Spring còn có embedded value resolution/configuration mechanisms. Do đó một `@Value` ở child **vẫn có thể resolve** dù parent BFPP không chạy tại child — nhưng đó có thể là **một đường hạ tầng khác** chứ không phản chứng per-container BFPP.

**Phân biệt**: “property visible qua Environment” không đồng nghĩa “parent PropertySourcesPlaceholderConfigurer đã mutate child BeanDefinitions”.

### 12.6. Các BeanPostProcessor cho domain markers

Một team có custom `@AuditTracked` annotation processor trong parent, nhưng controllers ở child có marker ấy. Nếu muốn custom BPP áp lên controllers, nó phải được đăng ký đúng child factory. Ngược lại, nếu annotation xuất hiện trên parent services, cần processor ở parent.

Với phạm vi cross-module, dùng một cấu hình reusable `@Import(AuditInfrastructure.class)` đặt ở mỗi context chứa target beans, và giữ registration idempotent.

## 13. Không nhầm với bean definition inheritance

Spring còn có khái niệm **bean definition inheritance** (ví dụ XML `parent="templateBean"`). Nó không giống **ApplicationContext parent-child hierarchy**.

| Bean definition inheritance | ApplicationContext hierarchy |
|---|---|
| Một bean definition tham chiếu parent definition/template | Một ApplicationContext tham chiếu parent ApplicationContext |
| Kế thừa một số configuration metadata để tái sử dụng | Child bean lookup có thể delegate lên parent |
| Có thể nằm trong cùng bean factory | Có hai bean factories riêng |
| Không tự tạo context hierarchy | Không tự copy definitions hoặc processors |
| Vấn đề là blueprint inheritance | Vấn đề là container ownership và visibility |

Một `ChildBeanDefinition` trong cùng một BeanFactory có thể thừa hưởng các thuộc tính như scope/constructor args/property values từ template, nhưng **vẫn do local factory tạo**, nên local BPP áp vào instance của nó.

Không nhầm tên “child bean definition” với “bean ở child ApplicationContext”.

## 14. Debug và kiểm tra ownership trong thực tế

### 14.1. Đọc đúng API

```java
boolean existsViaHierarchy =
        child.containsBean("parentService");

boolean localDefinition =
        child.containsBeanDefinition("parentService");

Object resolved = child.getBean("parentService");
```

Đây là snippet tích hợp. Để xác định origin, kiểm tra local definitions của child, parent definitions và object identity.

Nếu child có local bean cùng tên, `containsBeanDefinition` true; nếu chỉ parent có, local definition false nhưng bean lookup có thể thành công.

### 14.2. In danh sách post-processors local

Với `ConfigurableListableBeanFactory` của `AnnotationConfigApplicationContext`, bạn có thể lấy:

```java
String[] localBppBeans = child.getBeanFactory()
        .getBeanNamesForType(
                BeanPostProcessor.class, false, false);
```

Đây là snippet tích hợp cần import `BeanPostProcessor`. `getBeanNamesForType` trên factory này phục vụ tra cứu local names; `allowEagerInit=false` tránh eager initialization chỉ để type-match khi có thể.

**Lưu ý:** không phải tất cả BPPs được đăng ký **programmatically** đều tồn tại như named bean definitions. Vì vậy danh sách `getBeanNamesForType` **không phải** là kiểm kê tuyệt đối mọi BPP instance đang chạy trong factory.

Nếu cần quan sát invocation thật, ghi log trong `postProcessAfterInitialization` với beanName, bean class và logical context identifier.

### 14.3. Theo dõi BFPP đúng cách

Trong BFPP, log local registry:

```java
@Bean
public static BeanFactoryPostProcessor diagnostic() {
    return factory -> {
        System.out.println(
                "local definitions=" + factory.getBeanDefinitionCount());
    };
}
```

Đây là snippet tích hợp trong `@Configuration`. `getBeanDefinitionCount()` đếm local definitions trong factory được đưa tới callback, **không phải tổng cộng parent + child**.

Đừng log toàn bộ properties hoặc secrets từ bean definitions trên production. Chỉ in type/name/count có chủ đích khi debug.

### 14.4. Chú ý proxy và bean class

Sau BPP, object trả về có thể là proxy, nên `bean.getClass()` không nhất thiết bằng application target class. Nếu test AOP, dùng `AopUtils.isAopProxy` hoặc kiểm tra behavior thật, không chỉ so sánh `getClass().getSimpleName()`.

### 14.5. Thiết lập breakpoints

Điểm đặt breakpoint hợp lý:

- `AbstractApplicationContext.invokeBeanFactoryPostProcessors`
- `AbstractApplicationContext.registerBeanPostProcessors`
- `BeanFactoryPostProcessor.postProcessBeanFactory` implementation của bạn
- `BeanPostProcessor.postProcessBeforeInitialization`
- `BeanPostProcessor.postProcessAfterInitialization`
- `AbstractBeanFactory.doGetBean` để hiểu parent delegation
- `DefaultListableBeanFactory.getBeanDefinition` khi metadata lookup

Điều cần quan sát là **factory instance identity** nhận callback và **beanName** được xử lý, không chỉ tên class của processor.

## 15. Những sai lầm thường gặp

| Hiểu nhầm | Thực tế | Cách sửa |
|---|---|---|
| “Child getBean được parent bean ⇒ child BPP áp lên bean đó” | Parent sở hữu instance và pipeline creation | Đăng ký BPP ở context chứa target |
| “Parent BPP tự xử lý child beans” | BPP là per-container | Cấu hình riêng trong child khi cần |
| “Parent BFPP tự mutate child definitions” | BFPP nhận local BeanFactory | Đăng ký BFPP thích hợp ở child |
| “Child BFPP tự sửa parent definitions” | Child registry không sở hữu parent definitions | Không suy từ bean visibility thành metadata ownership |
| “Tất cả beans ở hai contexts được merge thành một registry” | Mỗi context có factory/registry riêng | Dùng containsBeanDefinition local để debug |
| “Parent có annotation proxy là child cũng có proxy” | Proxy infrastructure cần local registration với target factory | Đặt `@Enable...` đúng tầng |
| “BFPP và BPP giống nhau” | Metadata vs object instance | Chọn extension point theo mục đích |
| “Gọi getBean trong BFPP là bình thường” | Có thể tạo bean sớm, mất BPP/AOP | Thao tác BeanDefinition, không instantiate |
| “Đăng ký BPP sau refresh sẽ hồi tố mọi bean đã tạo” | Không tự chạy lại callbacks | Đăng ký trước normal bean creation |
| “static @Bean biến processor thành global singleton” | Static chỉ giảm early instantiation của config | Hiểu processor vẫn thuộc bean factory |
| “Một @Order ở child cho phép áp trước parent processors” | Ordering local, không cross-factory | Định nghĩa pipeline local riêng |
| “Parent có @Autowired BPP nên child BeanFactory thuần tự inherit” | BeanFactory thuần cần setup own infrastructure | Dùng ApplicationContext hoặc register BPPs local |
| “Một @Conditional bean bị bỏ tại parent sẽ tồn tại qua child BPP” | BPP không đăng ký definition bị skip | Dùng config/conditions đúng container |
| “Child close nên destroy parent singleton” | Parent quản lý lifecycle parent beans | Close child trước parent |
| “Bean definition inheritance = context hierarchy” | Hai khái niệm độc lập | Tách blueprint vs context ownership |
| “BPP instances luôn có bean name” | Programmatic BPP có thể không là named bean | Quan sát factory registered processors |
| “Post-processor class dùng chung nghĩa instance tự shared” | Mỗi context có thể tạo instance riêng | Reuse implementation, register per context |
| “Một processor dùng chung không có vấn đề thread-safety” | BPP có thể giữ mutable state | Dùng stateless hoặc thread-safe design |
| “Context chỉ có bean type BPP là đủ khi return type @Bean quá chung” | Type detection sớm cần accurate return type | Khai báo return BPP interface/implementation |
| “Mọi integration test 1 context phản ánh production 2 contexts” | Test architecture khác làm processor scope khác | Test hierarchy thực tế |

### 15.1. Câu chuyện một bug điển hình

Một ứng dụng có hai contexts:

```text
root (parent):
    PaymentService @Transactional

web (child):
    Controllers
    @EnableTransactionManagement
```

Controller inject được `PaymentService`, nên team nghĩ infrastructure đã hoạt động. Nhưng proxy post-processing thuộc web child **không tự chạy trên parent PaymentService**, vì root tạo service. Khi thực thi, transaction advice có thể không có trên service.

Cách xử lý là đặt transaction infrastructure ở đúng root context hoặc chuyển service vào context được cấu hình đúng, chứ không gọi lại child `getBean` hoặc gắn thêm `@Transactional` nhiều lần.

**Thận trọng:** Trong ứng dụng Boot auto-configuration thực tế, infrastructure có thể được đăng ký tự động ở đúng context. Không suy lỗi scope nếu bạn chưa kiểm tra bean/proxy owners thật.

## 16. Checklist cấu hình nhiều contexts

Trước khi triển khai, hãy kiểm tra:

1. Có bao nhiêu `ApplicationContext`? Parent-child hay sibling?
2. Mỗi bean được định nghĩa ở context nào? Có cùng tên ở nhiều tầng không?
3. BFPP/BDRPP được khai báo ở đâu? Đích mutation có local definition không?
4. BPPs cần tác động bean nào? Những beans ấy có do cùng factory tạo không?
5. Infrastructure proxy/transaction/cache/async được bật ở **context sở hữu targets** chưa?
6. `@Bean` processor methods có return type rõ và `static` khi phù hợp không?
7. BPP có dependencies nào bị eagerly instantiated trong registration phase không?
8. Parent đã refresh trước khi child cần lookup parent dependencies chưa?
9. Context có `@Profile`/`@Conditional` làm processors/targets biến mất không?
10. Bean có `@Lazy`, prototype, FactoryBean hoặc proxy làm khác thời điểm creation không?
11. Test có dựng đúng hierarchy production không?
12. Có assertions cho cả `containsBean` và `containsBeanDefinition` không?
13. Có kiểm tra lifecycle ownership và close child trước parent không?
14. Có vô tình đăng ký processor giống nhau hai lần trong một factory không?
15. Có logging/instrumentation để xác định **factory owner và processor caller** khi sự cố xảy ra không?

## 17. Câu hỏi tự kiểm tra

**1. BeanPostProcessor ở parent có tự xử lý bean của child không?**  
Không. BPP hoạt động theo phạm vi bean factory của context nơi nó được đăng ký.

**2. BeanPostProcessor ở child có xử lý một singleton được lấy từ parent qua `child.getBean()` không?**  
Không. Instance được parent factory quản lý, nên chịu pipeline parent BPP.

**3. Vì sao `child.containsBean("x")` có thể true còn `child.containsBeanDefinition("x")` false?**  
Vì `x` visible qua parent hierarchy nhưng local registry không có definition `x`.

**4. BFPP thay đổi dữ liệu nào?**  
BeanDefinition metadata, trước khi tạo normal beans.

**5. BFPP ở parent có tự đổi property values của bean definition ở child không?**  
Không. Nó nhận local factory của parent.

**6. BDRPP ở child đăng ký bean definition vào registry nào?**  
Registry của child đang được xử lý, nếu nó dùng registry argument được Spring truyền.

**7. Vì sao cần static @Bean cho BFPP?**  
Để Spring tạo BFPP sớm mà không ép configuration instance khởi tạo, tránh xung đột annotation/lifecycle processing.

**8. Khi nào BPP chạy so với init callback?**  
`postProcessBeforeInitialization` trước init callbacks, `postProcessAfterInitialization` sau init callbacks trong bean creation pipeline.

**9. Nếu child lookup parent singleton, bean đó có bị clone để áp child BPP không?**  
Không. Child có thể nhận cùng parent singleton object.

**10. Nếu child khai báo bean trùng tên parent, kết quả lookup thế nào?**  
Local child definition thường shadow parent name đối với lookup từ child; parent vẫn giữ bean của riêng mình.

**11. BFPP có nên gọi `getBean("service")` để mutate service instance không?**  
Không nên; có nguy cơ early instantiation và bỏ qua processor infrastructure.

**12. Có thể cùng một class BPP được đăng ký ở cả hai contexts không?**  
Có. Mỗi context có BPP instance/configuration riêng; đó là cách để áp logic tương tự cho local beans của từng context.

**13. `@EnableTransactionManagement` ở child có tự proxy parent services không?**  
Không mặc nhiên. Infrastructure proxy xử lý beans do factory phù hợp tạo; cần config ở context chứa transactional targets.

**14. Có thể thêm BPP sau refresh để xử lý lại singleton đã tạo không?**  
Không tự động. Cần đăng ký processor trước creation hoặc thiết kế re-creation mechanism riêng.

**15. BeanFactory thuần có tự autodetect tất cả BPP/BFPP beans như ApplicationContext không?**  
Không. Plain `BeanFactory` thường cần registration/invocation thủ công khi muốn dùng extension points.

**16. Bean definition inheritance có phải ApplicationContext hierarchy không?**  
Không. Một cái tái sử dụng metadata; cái kia nối nhiều containers.

**17. Đóng child có hủy luôn parent singleton không?**  
Không theo ownership thông thường; parent quản lý lifecycle của bean đó.

**18. Một BPP có `@Order(1)` trong child có áp lên parent trước parent BPP khác không?**  
Không. Ordering chỉ có ý nghĩa trong pipeline của factory nhận đăng ký processor.

**19. Demo dùng cùng class `LocalLabelProcessor` hai lần thì có shared instance không?**  
Không. Parent/child cấu hình hai instances với contextTag khác nhau.

**20. Cách kiểm chứng chắc chắn nhất là gì?**  
Theo dõi callback với factory owner và beanName, kiểm tra local definitions vs hierarchical lookup, và integration test đúng parent-child setup.

## 18. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): mô tả tường minh **per-container scope** của `BeanPostProcessor` và `BeanFactoryPostProcessor`, đăng ký và caveats.
- [Spring Framework 6.2 Reference — BeanFactory vs ApplicationContext](https://docs.spring.io/spring-framework/reference/6.2/core/beans/beanfactory.html): auto-detection của processor beans và khác biệt khi dùng plain BeanFactory.
- [Spring Framework 6.2 API — BeanFactoryPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/factory/config/BeanFactoryPostProcessor.html): callback metadata, registration và ordering.
- [Spring Framework 6.2 API — BeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html): instance callbacks và registration rules.
- [Spring Framework 6.2 API — BeanDefinitionRegistryPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionRegistryPostProcessor.html): extension để đăng ký definitions.
- [Spring Framework 6.2 API — AbstractApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html): refresh sequence, invoke BFPP/register BPP, context parent.
- [Spring Framework Reference — Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/core/beans/child-bean-definitions.html): phân biệt inheritance metadata với ApplicationContext hierarchy.
- [Spring Framework Reference — Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): proxy-based transaction semantics và annotation-driven configuration scope.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-006">Lan truyền sự kiện từ con lên cha và tránh xử lý lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền sự kiện từ con lên cha và tránh xử lý lặp trong Spring ApplicationContext

## 1. Mục tiêu và bài toán thực tế

Một ứng dụng Spring không nhất thiết chỉ có một `ApplicationContext`. Ví dụ, hệ thống web truyền thống có **root context** chứa repository, domain services và bean dùng chung, cùng một hoặc nhiều **child contexts** gắn với servlet/module. Child thường truy cập được bean của parent; parent không nhìn thấy bean được khai báo riêng ở child. Nhưng **sự kiện** được truyền thế nào?

Trong `AbstractApplicationContext` tiêu chuẩn, **event phát ở child được thông báo tới listeners cục bộ rồi được chuyển tiếp lên parent**, tiếp tục qua từng cấp tổ tiên. Event phát ở parent **không tự truyền xuống child**, và một child **không tự gửi sang sibling child**.

```text
                      ROOT ApplicationContext
                     +-------------------------+
                     | RootEventListener       |
                     | common services         |
                     +------------^------------+
                                  |
                        events đi LÊN
                         /        |       \
                        /         |        \
             +---------+--+  +----+------+  +-----------+
             | Child A    |  | Child B   |  | Child C   |
             | listener A |  | listener B|  | listener C|
             +------------+  +-----------+  +-----------+

Child A.publishEvent(X):
  listener A nhận X → root listener nhận X
  listener B/C KHÔNG tự nhận X

Root.publishEvent(Y):
  root listener nhận Y
  listener A/B/C KHÔNG tự nhận Y
```

Đây là cơ chế **trong một hierarchy của các context**, không phải message broker, cluster broadcast hay sự bảo đảm durable delivery.

Tình huống dễ lỗi: một listener được đăng ký cả ở root và child; sự kiện do child phát đến listener đó **hai lần** (một lần qua multicaster child, một lần qua multicaster root). Một tình huống khác: root listener nhận hai event cùng loại `ContextRefreshedEvent`, lần đầu từ chính root, lần thứ hai do child được refresh; đó là **hai lần refresh khác nhau**, không phải cùng một event được phát hai lần.

README này giải thích ranh giới context, luồng lan truyền, những cơ chế gây xử lý lặp, cách lọc events đúng nguồn, cách dùng event ID/idempotency, thứ tự listener, exceptions và concurrency, cùng demo Spring Core Java 17+ chạy và kiểm thử được.

## 2. Parent-child context: quan hệ bean khác quan hệ event

`org.springframework.context.ApplicationContext` biểu diễn IoC container ở mức cao. Một child có thể gắn parent qua `ConfigurableApplicationContext.setParent(...)` trước khi `refresh()`:

```java
AnnotationConfigApplicationContext parent =
        new AnnotationConfigApplicationContext();
parent.register(RootConfig.class);
parent.refresh();

AnnotationConfigApplicationContext child =
        new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(WebConfig.class);
child.refresh();
```

Đây là **fragment**, cần định nghĩa `RootConfig` và `WebConfig` cùng imports, và phải đóng context sau khi sử dụng. Ví dụ runnable ở mục 10 không đòi hai classes cấu hình này.

Có hai cấu trúc khác nhau:

**Bean lookup:**
- Bean local trong child được ưu tiên khi tra trong child theo quy tắc tên/type tương ứng.
- Nếu child không có bean phù hợp, nó có thể tìm thấy bean ở parent.
- Parent không thể tự tìm child-only bean chỉ qua quan hệ `setParent`.
- Bean name trùng giữa parent/child thường được xem là *shadowing* ở child, nhưng việc injection theo type, collections và các factory methods có chi tiết riêng.
- Mỗi context có bean lifecycle, registry và `ApplicationEventMulticaster` riêng.

**Event routing:**
- Child publish → local multicaster → parent publish.
- Parent publish → local multicaster của parent; không có default recursion xuống descendants.
- Bean parent được child truy cập **không có nghĩa** listener parent tự “được copy” vào registry listener child.
- Parent nhận event con là do **cơ chế chuyển tiếp event**, không phải vì Spring nhân bản tất cả listener beans xuống con.

Không nhầm `@Import`, component scanning hoặc bean visibility với event propagation. Khi cấu hình bị quét trùng ở nhiều context, có thể có **hai listener bean instances khác nhau**; đó là một nguy cơ khác với một listener instance được đăng ký hai multicaster.

## 3. Cơ chế publishEvent của AbstractApplicationContext

Spring Framework 6.2 có một đường đi được định nghĩa trong `org.springframework.context.support.AbstractApplicationContext`. Với `ApplicationEvent` đã tạo:

```text
child.publishEvent(applicationEvent)
    |
    +--> child.applicationEventMulticaster.multicastEvent(...)
    |           |
    |           +--> listeners cục bộ của CHILD
    |
    +--> child.getParent()
                |
                +--> parent.publishEvent(applicationEvent)
                           |
                           +--> listeners cục bộ của PARENT
                           |
                           +--> parent.getParent() (nếu có)
                                        |
                                        +--> tổ tiên tiếp theo
```

Phần `protected publishEvent(Object event, ResolvableType typeHint)` là delegate nội bộ để truyền event giữa contexts; Javadoc cho biết nó **có thể được override trong subclass** nếu muốn cơ chế propagation đặc biệt. Hành vi nêu ở đây áp dụng cho các `AbstractApplicationContext` chuẩn, chẳng hạn `AnnotationConfigApplicationContext`, không phải quy luật của mọi custom implementation có thể viết được.

Những điểm quan trọng:
1. Spring phát sự kiện tại multicaster của context hiện tại trước.
2. Nếu không bị exception làm dừng ở cơ chế đồng bộ, Spring chuyển event tới parent.
3. Parent chọn listeners của **chính nó** theo event type, annotations và registration.
4. Nếu parent còn parent, event tiếp tục lên cấp kế tiếp.
5. Không có bước tự gửi xuống các children khác.
6. Nếu event là một `ApplicationEvent` object, Spring có thể chuyển **chính object đó** lên cha, không bắt buộc tạo bản sao cho mỗi cấp.
7. Với object payload không kế thừa `ApplicationEvent`, Spring có thể wrap trong `PayloadApplicationEvent`; type metadata được chuyển theo context.
8. Mỗi cấp có thể có executor, listener order và ErrorHandler riêng, dẫn đến timing/error behavior khác nhau.

### ApplicationEventPublisher là hand-off

Interface `ApplicationEventPublisher` mô tả việc bàn giao event cho multicaster, **không hứa mọi listeners chạy đồng bộ, ngay lập tức hoặc ở cùng thread** trong mọi cấu hình. Ở context mặc định, `SimpleApplicationEventMulticaster` gọi listener đồng bộ nếu không có executor, nên child listeners thường chạy xong rồi tới parent listeners. Nhưng sau khi tùy chỉnh executor/`@Async`, việc hoàn tất callback có thể chồng chéo.

Không coi sơ đồ “child trước parent” là global ordering guarantee giữa các async tasks: đó là thứ tự các lượt multicasting/publish dispatch khi default sync.

## 4. Hai chiều truyền sự kiện và ví dụ nhiều cấp

Giả sử hierarchy `root → sales → web`:

```text
web.publish(E1)
  web listeners → sales listeners → root listeners

sales.publish(E2)
  sales listeners → root listeners

root.publish(E3)
  root listeners

web-sibling.publish(E4)
  listeners web-sibling → ... tổ tiên chung
  web listeners không tự nhận E4
```

**Hướng đi lên không có nghĩa child tự biết mọi event ở ancestor.** Muốn parent điều phối child-specific action, thường dùng:
- Gọi service trong child qua ranh giới rõ nếu kiến trúc cho phép.
- Định nghĩa messaging/API cụ thể giữa modules.
- Cho child listen vào cùng một nguồn external event nếu thực sự cần.
- Tránh “đảo chiều event” bằng cách tùy tiện override Spring internals khi không có test mạnh.

### Có thể phát từ bean parent mà event lại bắt đầu ở child không?

Đường đi phụ thuộc **đối tượng `ApplicationEventPublisher` mà bean đang sử dụng**. Nếu một singleton ở parent được inject publisher của parent, nó phát event qua parent, dù method được gọi bởi controller ở child. Không thể kết luận “request xuất phát từ child thì event luôn được child publish”.

Ví dụ:

```java
public final class SharedService {
    private final ApplicationEventPublisher publisher;

    public SharedService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void perform() {
        publisher.publishEvent(new SharedActionEvent(this));
    }
}
```

Đây là fragment; `SharedActionEvent` cần định nghĩa. Nếu `SharedService` là bean parent, publisher được cấp bởi parent context, nên child listener sẽ **không tự nhận** event phát từ method này. Nếu producer thực sự cần notify child, xem lại ownership và nơi phát event, không coi HTTP request origin là routing metadata.

### Context ID có thể giúp debug, không phải tự động routing key

`ApplicationContext.getId()` dùng làm identity/logical ID hữu ích. Nhưng `getId()` không phải business event ID. Để phân biệt hai hoạt động nghiệp vụ, nên có `eventId` riêng; để biết context nơi event được phát, cần metadata/contract thích hợp.

## 5. Vì sao cùng một listener có thể chạy lặp?

Cần phân biệt ba tình huống thường bị gom chung thành “Spring tự gọi hai lần”.

### Tình huống A — một instance đăng ký ở cả child và parent

```text
                  Parent multicaster
                   listener instance L
                           ^
                           |
Child multicaster       child.publish(E)
 listener instance L           |
                           +---+
```

Nếu L nằm trong cả hai registry, event E do child phát có thể làm:
1. Child multicaster gọi L.
2. Parent multicaster gọi **cùng object L** lần nữa.

Hai calls không phải hai independent messages, mà là cùng event đã được dispatch qua hai cấp và cả hai multicaster đều có listener L. `ApplicationEventMulticaster` không có bộ dedup toàn hierarchy dựa trên object identity/eventId.

Điều này có thể xảy ra khi code đăng ký shared listener thủ công ở hai cấp hoặc một module tự đưa cùng listener vào nhiều registries.

### Tình huống B — có hai bean listener riêng vì cấu hình bị quét trùng

Ví dụ root và child đều component-scan cùng package `com.example.shared.listeners` hoặc cùng `@Import(SharedListenersConfig.class)`. Khi đó mỗi context có thể dựng một instance listener của riêng mình. Event child đi lên parent và cả hai instances đều xử lý — hiệu ứng nghiệp vụ có thể trùng dù object identity khác.

Sửa tận gốc bằng cách tách package/config scanning responsibilities:
- Root quét/cấu hình service chung và listeners toàn cục.
- Child quét/cấu hình web-specific controller và listeners của module.
- Không quét lại nguyên package root trong child nếu không chủ đích.
- Test số bean/listener instances trên từng cấp.
- Dùng explicit `@ComponentScan(basePackages=...)` hoặc cấu hình modules rành mạch.

### Tình huống C — hai **ContextRefreshedEvent** khác nhau

Trong hierarchy, khi refresh parent có thể phát `ContextRefreshedEvent(source=parent)`. Sau đó child refresh, child phát `ContextRefreshedEvent(source=child)` rồi event này được chuyển tiếp lên parent. Root listener cùng type nhận hai events:

```text
parent.refresh() 
  → ContextRefreshedEvent(parent)         → parent listener

child.refresh()
  → ContextRefreshedEvent(child)          → child listener
  → chuyển tiếp nguyên event lên parent   → parent listener
```

Đây là **hai sự kiện của hai context**, không phải cùng một event được phát hai lần. Nếu listener root thực hiện warm-up cache toàn hệ thống mà không lọc nguồn, warm-up có thể bị chạy khi child refresh. Filter theo `getApplicationContext()` là giải pháp chuẩn cho lifecycle events; xem mục 7.

### Tình huống D — nghiệp vụ publish nhiều lần

Đừng bỏ qua khả năng service thực sự publish event hai lần (gọi lặp, retry request, duplicate scheduler, component reentrancy, listener phát lại event). Hierarchy không giải thích mọi duplicate. Có thể kiểm trace/correlation ID tại call site trước khi thay đổi cấu hình context.

## 6. Phân biệt cùng một event với hai event cùng type

Hai khái niệm:

**Cùng event object được truyền giữa contexts:**

```java
OrderSignal event = new OrderSignal(child, "EV-100");
child.publishEvent(event);
```

Đây là fragment; event type cần định nghĩa. Child và parent có thể nhận **cùng event object** qua cơ chế chuyển tiếp. Nếu một listener instance được đăng ký ở cả hai nơi, nó có thể nhận object này hai lần.

**Hai event objects được tạo độc lập:**

```java
parent.publishEvent(new OrderSignal(parent, "EV-100"));
child.publishEvent(new OrderSignal(child, "EV-100"));
```

Đây là hai lời gọi publish khác nhau dù bạn vô tình dùng cùng chuỗi ID. Không được coi `eventId` bị tái sử dụng như bằng chứng chúng là cùng occurrence. Thiết kế event ID nên sinh duy nhất hoặc dựa business idempotency key có contract.

Bảng chẩn đoán:

| Quan sát | Nguyên nhân có thể | Hướng kiểm tra |
| --- | --- | --- |
| Cùng listener identity nhận cùng event object 2 lần | Đăng ký ở cả child và parent | Kiểm listener registries/scanning |
| Một root listener nhận hai ContextRefreshedEvent khác source | Parent refresh rồi child refresh | Log event source context ID |
| Hai listener beans cùng class xử lý cùng event | Bean được tạo ở hai contexts | Kiểm bean owner/context IDs |
| Event ID khác nhau nhưng payload giống nhau | Có hai publish nghiệp vụ | Kiểm producer/retry/trigger |
| Cùng event ID đến lại sau restart | Nguồn messaging/durable replay khác | Consumer idempotency, inbox |
| Chỉ listener root xử lý child event | Routing upward mặc định | Có thể đúng, không phải duplicate |
| Listener child không thấy parent event | Không có downward propagation | Đúng contract |

Không đo duplicate bằng cách **chỉ so Java class event**. Mọi event cùng loại sẽ có class giống nhau; cần event ID, source/origin metadata và execution trace.

## 7. Lọc theo context nguồn đúng cách

### 7.1. Lifecycle events có nguồn context chuẩn

Với `ContextRefreshedEvent`, `ContextClosedEvent`, `ContextStartedEvent`, `ContextStoppedEvent` thuộc họ `ApplicationContextEvent`, Spring giữ reference tới `ApplicationContext` đã phát. Listener muốn xử lý **chính context sở hữu listener**, không xử lý lifecycle events từ descendants:

```java
@Component
public final class OwnContextLifecycleListener
        implements ApplicationContextAware {

    private ApplicationContext owner;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.owner = applicationContext;
    }

    @EventListener
    public void onRefresh(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != owner) {
            return; // Event đến từ child/descendant.
        }
        // Chỉ refresh của owner context.
    }
}
```

Đây là **fragment**: cần imports `ApplicationContext`, `ApplicationContextAware`, `ContextRefreshedEvent`, `Component`, `EventListener` và class được đăng ký làm bean. Hành vi `ApplicationContextAware` cấp context sở hữu bean; trong parent bean, nó là parent, không phải context nơi event từ child khởi phát.

Có thể dùng identity comparison `==` vì `getApplicationContext()` là reference thực tới context khi framework tạo event. Đừng dùng display name string nếu có hai contexts tên trùng, và đừng gán “mọi ContextRefreshedEvent là của tôi” chỉ vì listener đang nằm trong root.

### 7.2. Phân biệt ContextClosedEvent của child và parent

Khi child được đóng, một `ContextClosedEvent(source=child)` có thể tới root. Khi root được đóng, root phát event riêng. Một root cleanup listener thiếu filter có thể đóng tài nguyên dùng chung **quá sớm** khi child shutdown. Điều kiện `event.getApplicationContext() == owner` đặc biệt quan trọng với shutdown actions.

### 7.3. Không giả định source của custom event luôn là context

Trong `ApplicationEvent`, `event.getSource()` là object được caller truyền vào constructor `ApplicationEvent(Object source)`. Nó thường là service publisher, entity hoặc business object — **không bắt buộc** là ApplicationContext. Với payload event, Spring wrapper có semantics riêng.

Do đó ví dụ:

```java
if (event.getSource() == applicationContext) {
    // ...
}
```

**chỉ hợp lệ** nếu event contract đã yêu cầu source là đúng context. Không dùng điều kiện này chung cho mọi custom event. Khi muốn filter theo originating context, hãy **đưa origin context vào metadata có kiểm soát**, hoặc thiết kế event class nhận/giữ explicit origin string/context ID.

### 7.4. Explicit origin cho application event

```java
public record BusinessNotice(
        String eventId,
        String originContextId,
        String orderId) {
}
```

Record là Java 17 thuần, có thể publish bằng `ApplicationEventPublisher.publishEvent(Object)`. Một listener có thể kiểm `originContextId` theo allowlist của module. Nhưng **originContextId chỉ là metadata do producer khai**, không phải bảo mật/authorization evidence; event từ module không tin cậy có thể giả mạo chuỗi nếu không có trust boundary.

Đối với business action phải xử lý **đúng một lần** giữa nhiều contexts, lọc origin chỉ giảm fan-out không mong muốn. Nó **không thay thế event-ID idempotency** cho retries/duplicates.

## 8. Chiến lược tránh xử lý lặp: chọn đúng nguyên nhân

### 8.1. Một listener chịu trách nhiệm ở một context

Giải pháp ưu tiên cho duplicate do cấu hình:
- Đặt shared/global listener ở root **một lần**.
- Đặt child-specific listener ở child **một lần**.
- Tránh tạo cùng implementation ở root lẫn child qua component scanning chồng lấn.
- Không gọi `child.addApplicationListener(shared)` và `parent.addApplicationListener(shared)` trừ khi biết mình muốn xử lý ở cả hai cấp.
- Nếu một listener cần cả child và parent events, đăng ký nó ở root và phân biệt event origin, thay vì đăng ký lặp.

Cách này loại **duplicate registration**, không ảnh hưởng routing upward hợp lệ.

### 8.2. Filter lifecycle event theo owner

Với startup/shutdown listeners:
- Check `event.getApplicationContext() == owner` khi chỉ muốn owner-level event.
- Nếu muốn xử lý descendants để quản lý tổng thể, đừng filter tất cả; thay vào đó ghi rõ policy per child và event type.
- Kiểm cả refresh và close events trong integration tests.

### 8.3. Business idempotency bằng event ID

Nếu việc xử lý có side effect quan trọng (gửi email, ghi external API), một listener không nên nghĩ rằng “được gọi lần đầu trong process” là bảo đảm exactly-once. Cần:
1. Event ID đại diện một event occurrence.
2. Một nơi giữ trạng thái đã xử lý theo ID và consumer identity.
3. Atomicity phù hợp giữa lưu dedup status và business side effect.
4. Cơ chế retry/resume nếu xử lý dở.
5. Expiry/retention hợp lý khi ID có lifetime hữu hạn.
6. Idempotency key của downstream provider khi external effect.

Một in-memory `Set<String>` có thể ngăn xử lý lặp **trong cùng JVM** nhưng mất khi restart và dễ race nếu không atomic; nó không đủ cho yêu cầu durable.

```java
Set<String> processed = ConcurrentHashMap.newKeySet();
if (!processed.add(event.eventId())) {
    return; // Trong cùng process: đã thấy ID.
}
handle(event);
```

Đây là **fragment minh họa có rủi ro**: `handle(event)` chưa định nghĩa. Nếu `handle` lỗi sau khi `add`, ID đã được đánh dấu dù side effect chưa hoàn tất; retry sẽ bị bỏ lỡ. Đừng copy nguyên mẫu này thành production idempotency. Dùng durable inbox, trạng thái claim/complete, transaction hoặc provider idempotency contract theo yêu cầu.

### 8.4. Đừng republish tới parent bằng tay

Nếu child đã `publishEvent` và Spring tự propagate lên parent, code như sau dễ nhân đôi:

```java
child.publishEvent(event);
parent.publishEvent(event); // Thừa nếu parent đã nhận qua hierarchy.
```

Đây là fragment anti-pattern. Root nhận event một lần qua propagation rồi một lần trực tiếp. Nếu thực sự cần publish độc lập hai domains, hãy mô tả thành **hai event occurrences** với ID/semantics rõ, không dùng cùng event object và cùng business side effect vô ý.

### 8.5. Không dựa vào BeanFactory hierarchy để dedup

Đôi khi developer nghĩ `containsBean("listener")` hoặc `getBeansOfType` trên child sẽ tự thể hiện mọi listener đã đăng ký ở parent. Nhưng BeanFactory inheritance và multicast registries là hai cơ chế khác. `child.getBean("listener")` có thể trả instance của parent; điều đó **không tự nói** instance đang đăng ký tại bao nhiêu multicasters.

## 9. Thứ tự xử lý, exceptions và async trong hierarchy

Mỗi context có một multicaster. Trong synchronous default, khi child phát event:
1. Child multicaster dispatch tới local listeners (có thể sắp theo `@Order`/`Ordered`).
2. Nếu dispatch thành công, call stack trở lại `AbstractApplicationContext.publishEvent` của child.
3. Event được chuyển lên parent, parent multicaster dispatch tới parent listeners theo order cục bộ.

**`@Order` không tạo một thứ tự toàn cục giữa tất cả contexts.** Parent listener order 1 vẫn thường chạy **sau** child listener order 100 trong đường publish child đồng bộ, vì parent chỉ nhận event sau local multicast. Đừng dựa vào numeric order để buộc root listener chạy trước child listener trong một event child-origin; hãy đổi nơi publish hoặc thiết kế orchestration khác.

### Exception có thể chặn propagation lên parent

Nếu một child listener ném unchecked exception trong `SimpleApplicationEventMulticaster` synchronous và không có `ErrorHandler` xử lý, lỗi có thể thoát ngay khỏi `publishEvent`, nên parent **chưa được gọi**. Vì vậy câu “mọi event từ child chắc chắn đến parent” phải có điều kiện: nếu listener failure, custom multicaster hoặc các đường thực thi khác cắt ngang, propagation không bảo đảm đến đủ mọi listener.

Nếu parent listener ném lỗi thì child listeners có thể **đã hoàn tất** trước đó; publisher ở child vẫn có thể nhận exception từ parent. Không có rollback tự động cho side effects đã thực hiện ở child.

### ErrorHandler theo từng multicaster

`SimpleApplicationEventMulticaster.setErrorHandler(...)` quy định xử lý exception của listeners thuộc multicaster đó. Một handler suppress lỗi ở child có thể cho phép propagation tiếp tục; handler ở parent có thể cho phép các parent listeners tiếp theo chạy. Tuy nhiên:
- Handler không tự retry/rollback.
- Không mặc định có một ErrorHandler toàn hierarchy.
- Cần phân biệt critical failures với best-effort observers.
- Tránh nuốt lỗi business bắt buộc để “tránh duplicate”.

### Async thay đổi timing, không thay routing

Khi context có executor hoặc listener `@Async`:
- Event dispatch có thể được schedule qua threads.
- Parent propagation vẫn theo policy của context, nhưng completion time/order có thể khác nhau.
- Một listener child có thể chưa hoàn tất khi parent listener bắt đầu.
- Lỗi async thường không truyền về publisher call stack như lỗi sync, trừ lỗi submit/rejection hoặc hạ tầng khác.
- ThreadLocal transaction/security/MDC context không tự được chia sẻ sang mọi thread.

Vì vậy demo runnable dùng synchronous default, không thêm `@Async`, để thứ tự stdout có thể kiểm chính xác.

### TransactionalEventListener theo hierarchy

Một `@TransactionalEventListener` ở root có thể nhận event được child gửi lên và đăng ký callback theo transaction đang active trên **thread/process tương ứng** nếu transaction synchronization hợp lệ. Nhưng điều này không có nghĩa mọi parent transaction manager có liên hệ tự động với child datasource. Với reactive transaction, phải tuân theo contract Reactor context/`TransactionalEventPublisher`, không dùng giả định ThreadLocal. Bài học trọng tâm vẫn là **origin và duplicate dispatch**, không xem hierarchy là hàng đợi giao dịch bền.

## 10. Ví dụ runnable Java 17+: parent/child, event đi lên và cùng listener bị gọi hai lần

Ví dụ dùng **Java 17+, Maven 3.9+ và Spring Framework 6.2.18**, chỉ dependency `spring-context`. Không cần Spring Boot, web server, database hay network runtime.

Chương trình:
- Tạo root `AnnotationConfigApplicationContext`.
- Gắn listener `parent-all` nhận mọi `SignalEvent` từ root hoặc descendant.
- Gắn listener `parent-own-only` chỉ xử lý event có `source == parent`; hợp đồng này được demo chủ động tuân thủ.
- Tạo child context, `setParent(parent)` **trước** `refresh()`, gắn listener `child-only`.
- Phát event từ child: child listener rồi parent-all; parent-own-only bỏ qua.
- Phát event từ root: chỉ parent listeners; child-only không nhận.
- **Cố ý đăng ký cùng một `TraceListener` object ở cả child lẫn root** để thấy event child thứ hai làm listener này in **hai dòng**.
- Không in lifecycle events vì `TraceListener` nhận chính xác `SignalEvent` qua generic `ApplicationListener<SignalEvent>`.

### 10.1. Cấu trúc project

```text
context-event-hierarchy/
├── pom.xml
└── src/main/java/edu/example/hierarchy/
    └── Main.java
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>context-event-hierarchy</artifactId>
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
                <version>3.5.0</version>
                <configuration>
                    <mainClass>edu.example.hierarchy.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/java/edu/example/hierarchy/Main.java**

```java
package edu.example.hierarchy;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;

public final class Main {

    public static final class SignalEvent extends ApplicationEvent {
        private final String id;

        public SignalEvent(ApplicationContext source, String id) {
            super(source);
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public static final class TraceListener
            implements ApplicationListener<SignalEvent>, Ordered {

        private final String name;
        private final int order;
        private final ApplicationContext acceptedSource;

        public TraceListener(String name,
                             int order,
                             ApplicationContext acceptedSource) {
            this.name = name;
            this.order = order;
            this.acceptedSource = acceptedSource;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void onApplicationEvent(SignalEvent event) {
            if (acceptedSource != null
                    && event.getSource() != acceptedSource) {
                return;
            }
            System.out.println(name + ": " + event.id());
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext()) {
            parent.setId("root");
            parent.addApplicationListener(
                    new TraceListener("parent-all", 10, null));
            parent.addApplicationListener(
                    new TraceListener("parent-own-only", 20, parent));
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {
                child.setId("web");
                child.setParent(parent);
                child.addApplicationListener(
                        new TraceListener("child-only", 10, null));
                child.refresh();

                System.out.println("== child publish ==");
                child.publishEvent(new SignalEvent(child, "C-1"));

                System.out.println("== parent publish ==");
                parent.publishEvent(new SignalEvent(parent, "P-1"));

                // Chủ ý tạo duplicate registration qua hai multicasters.
                TraceListener shared =
                        new TraceListener("shared", 30, null);
                child.addApplicationListener(shared);
                parent.addApplicationListener(shared);

                System.out.println("== shared listener in both ==");
                child.publishEvent(new SignalEvent(child, "C-2"));
            }
        }
    }
}
```

**Lệnh chạy trong thư mục chứa pom.xml:**

```bash
mvn -q compile exec:java
```

**Các dòng `System.out.println` từ chương trình theo thứ tự:**

```text
== child publish ==
child-only: C-1
parent-all: C-1
== parent publish ==
parent-all: P-1
parent-own-only: P-1
== shared listener in both ==
child-only: C-2
shared: C-2
parent-all: C-2
shared: C-2
```

Không có dòng `parent-own-only: C-1` hoặc `parent-own-only: C-2` vì demo truyền `child` làm `ApplicationEvent.source` và listener kiểm source identity với `parent`.

Cũng không có dòng `child-only: P-1`, bởi parent không publish xuống child.

Với event C-2, hai dòng `shared: C-2` là **cố ý**: cùng một listener object đã được đăng ký vào hai multicaster. Maven hoặc Spring có thể in logging riêng; khối trên chỉ là stdout do các `System.out.println` của demo.

### 10.2. Giải thích source identity trong demo

`SignalEvent` nhận `ApplicationContext source` và gọi `super(source)`. Vì vậy:
- `new SignalEvent(child, "C-1")` có `getSource() == child`.
- Khi child chuyển event lên parent, `getSource()` **không được biến thành parent**.
- `TraceListener("parent-own-only", ..., parent)` bỏ qua event C-1/C-2.
- `new SignalEvent(parent, "P-1")` được parent-own-only xử lý.

Lựa chọn “source là context” trong demo **không phải quy tắc mặc định của mọi domain event**. Với custom events thực, nhiều producer truyền `this` là service hoặc một business object. Muốn filter theo origin, hãy thống nhất hợp đồng event, hoặc dùng một trường `originContextId` rõ ràng.

### 10.3. Vì sao không dùng @Configuration/@Component trong demo?

Để chứng minh **chính xác** các listener được đăng ký ở cấp nào, demo dùng `addApplicationListener(...)` thay vì component scanning. Điều này tránh sự nhập nhằng “listener này được quét từ package nào” và giữ output ổn định. Trong dự án thực dùng `@Configuration`/`@ComponentScan`, nguyên lý routing không đổi, nhưng bạn cần kiểm tra registration ở từng context.

### 10.4. Sau khi refresh vẫn có thể addApplicationListener?

`ConfigurableApplicationContext.addApplicationListener(...)` hỗ trợ thêm listener; khi context đã active, `AbstractApplicationContext` có thể áp dụng nó vào multicaster hiện tại. Demo cố ý thêm `shared` **sau khi** hai contexts refresh, nên nó không nhận sự kiện refresh ban đầu và chỉ góp mặt ở bước C-2.

### 10.5. Một event, nhiều callback không phải at-least-once queue

Hai lần gọi `shared` trong demo xảy ra cùng process/lượt publication và không kèm persistent acknowledgement. Nó không có retry semantics của broker. Nếu bạn bỏ duplicate registration, số lần xử lý biến mất mà không cần thiết kế distributed dedup. Nếu dữ liệu nghiệp vụ có thể đến lặp từ nơi khác, vẫn cần event ID/idempotency riêng.

## 11. Kiểm ContextRefreshedEvent khi có parent và child

Một root listener định nghĩa như sau dễ chạy hai lần:

```java
@Component
public class RefreshMetrics {

    @EventListener
    public void onRefreshed(ContextRefreshedEvent event) {
        refreshSharedCache();
    }
}
```

Đây là fragment, cần imports và `refreshSharedCache()`. Nếu bean chỉ nằm trong root, nó vẫn có thể nhận `ContextRefreshedEvent` của root **và** của child do upward propagation.

Phiên bản rõ ràng hơn:

```java
@Component
public final class RefreshMetrics implements ApplicationContextAware {

    private ApplicationContext owner;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        owner = ctx;
    }

    @EventListener
    public void onRefreshed(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != owner) {
            return;
        }
        refreshSharedCache();
    }
}
```

Đây cũng là fragment, cần imports phù hợp và method `refreshSharedCache()`. Khi bean sống ở root, nó chỉ xử lý root refresh. Nếu ứng dụng cần nhận cả child refresh để cập nhật cấu hình tổng thể, **không áp filter này**; thay vào đó xử lý theo nguồn rõ ràng.

### Có nên dùng @EventListener(condition = "...") để lọc?

Spring hỗ trợ `@EventListener(condition = "...")` bằng SpEL. Có thể kiểm event attributes trong condition, nhưng expression phải dùng đúng evaluation context/parameter names và sở hữu context identity rõ. Với lifecycle event, điều kiện Java `event.getApplicationContext() == owner` dễ đọc và dễ unit test hơn một chuỗi SpEL phức tạp.

### Child root-sibling lifecycle events

Nếu root có hai children và cả hai refresh, root listener có thể nhận:
- 1 lần root refresh (nếu root đã được refresh).
- 1 lần child A refresh.
- 1 lần child B refresh.

Đó là ba `ContextRefreshedEvent` khác nhau. Nếu root listener chỉ cần khởi tạo singleton dùng chung, filter owner. Nếu root cần thống kê health của các child modules, ghi metadata từng child và xác định logic idempotency theo context ID + lifecycle round.

## 12. Cấu hình phổ biến trong web: root và DispatcherServlet contexts

Một cấu trúc Spring MVC truyền thống:

```text
Servlet container
    |
    +-- Root WebApplicationContext
    |       DataSource, transaction manager, services
    |       shared/global event listeners
    |
    +-- DispatcherServlet A → Child WebApplicationContext A
    |       controllers, MVC adapters, child-specific listeners
    |
    +-- DispatcherServlet B → Child WebApplicationContext B
            controllers, MVC adapters, child-specific listeners
```

Một request đi vào DispatcherServlet A và controller ở child A publish event qua publisher của **child A** có thể khiến:
1. Listeners trong child A được gọi.
2. Listeners ở root nhận event do propagation.
3. Listeners child B không tự nhận event.

Tuy nhiên nếu controller gọi một **service bean của root** và service đó sử dụng publisher inject từ root, nó sẽ publish **tại root**. Khi ấy không nên kỳ vọng child A/child B listeners tự nhận event. Đây là nguyên nhân một số đội thấy “event khi phát từ controller có, phát trong shared service thì thiếu”.

Trong Spring Boot hiện đại, một ứng dụng web thường có **một** main ApplicationContext; không được suy mọi ứng dụng Boot tự có cấu trúc root+DispatcherServlet child. Chỉ áp dụng hướng dẫn hierarchy khi ứng dụng thực sự tạo nhiều context với quan hệ parent/child.

### Cấu hình package scanning không trùng

Ví dụ minh họa:

```java
@Configuration
@ComponentScan("com.example.app.core")
class RootConfig {}

@Configuration
@ComponentScan("com.example.app.web")
class WebConfig {}
```

Đây là fragments, không thuộc demo runnable. Điều quan trọng là các package này **không quét chồng** listener classes ngoài ý muốn. Nếu listener global được scan cả ở root lẫn child, child event có thể kích hoạt cả hai instances.

### Multiple DispatcherServlet contexts không tạo cross-child bus

Tính năng event propagation không biến child A/B thành siblings có message routing. Nếu cần broadcast sang mọi web modules, nên đặt shared observer ở root hoặc dùng service/messaging contract thích hợp; đừng dựa vào parent có thể tự chuyển event xuống tất cả descendants.

## 13. Lỗi tại child/parent listener ảnh hưởng thế nào?

Trong default synchronous `SimpleApplicationEventMulticaster`:

```text
child.publish(event)
  |
  +-- child listener #1: OK
  +-- child listener #2: THROWS
  |                |
  |                +-- exception escapes
  |
  X parent publication may never happen
```

Nếu child listener ném exception chưa xử lý, call stack có thể dừng **trước khi code chuyển event lên parent**. Do đó:
- Không bảo đảm root audit observer luôn thấy child event nếu child observer khác fail.
- Một listener ở root không nên là nơi duy nhất ghi audit bắt buộc nếu event chỉ in-memory và cần durability.
- Với noncritical child observers, có thể cấu hình ErrorHandler để log/suppress, nhưng phải chấp nhận mất task nếu không có recovery.

Ở chiều ngược:

```text
child publishes event
  child listeners: OK
  parent listener: THROWS
  exception có thể propagate về child publisher
```

Lỗi parent có thể khiến code child nhận exception dù listeners child đã hoàn tất và có side effects. Không tự retry nguyên nghiệp vụ mà không xét idempotency.

### Khi dùng @Async

Custom multicaster executor hoặc `@Async @EventListener` có thể tách thread, nhưng:
- Không đổi hướng propagation.
- Không bảo đảm mọi listener chạy hoàn tất theo thứ tự.
- Không sửa duplicate registration.
- Không làm event bền qua process crash.
- Exception handling phải quan sát trên executor/error handler; publisher catch thường không bắt được lỗi async xảy ra sau đó.

### Transaction concerns

Event publish trong transaction có thể khiến child/root listeners synchronous quan sát transaction context trên publisher thread. Nhưng transaction không tự bao gồm mọi side effects, parent/child beans có thể dùng transaction managers/data sources khác nhau. `@TransactionalEventListener` chỉ xử lý phase nếu có transaction synchronization phù hợp; nó không biến hierarchy thành một distributed transaction.

## 14. Kiểm thử: đếm theo owner, origin và event ID

Một integration test tốt cần ít nhất bốn trường hợp:
1. Child publish event → child và parent đúng một lần theo registration đã thiết kế.
2. Parent publish event → parent, **không** child.
3. ContextRefreshedEvent của child → root listener có nhận nhưng handler owner-only bỏ qua.
4. Cố tình duplicate registration → số callback tăng như mong đợi; refactor xong không còn lặp.

### Recorder để assert, không chỉ đọc console

```java
public final class Recorder {
    private final List<String> calls =
            new CopyOnWriteArrayList<>();

    public void add(String name, String id) {
        calls.add(name + ":" + id);
    }

    public List<String> snapshot() {
        return List.copyOf(calls);
    }
}
```

Đây là một **test helper class fragment**, cần imports `List` và `CopyOnWriteArrayList`. Các listeners trong test phải gọi `recorder.add(...)` thật thì assert mới có ý nghĩa.

Ví dụ assertions:
- Sau child C-1: `["child:C-1", "root:C-1"]`.
- Sau parent P-1: chỉ có `"root:P-1"` mới trong recorder.
- Sau shared listener đăng ký hai cấp: count của `shared:C-2` là 2.

Không viết unit test tạo list bằng tay rồi assert list đó, vì không hề kiểm Spring propagation.

### Test cùng listener instance và hai beans riêng

- **Cùng instance:** tạo object listener L một lần, add vào cả hai contexts; kiểm hai callbacks.
- **Hai instances:** root/child tạo listener class giống nhau từ hai configs; kiểm hai callbacks và khác identity.
- Dùng `System.identityHashCode(listener)` trong debug **chỉ như dấu hiệu process-local**, không phải stable ID hay security token.
- Đừng log raw event payload có dữ liệu nhạy cảm để phân biệt duplicates.

### Test ContextRefreshedEvent riêng

Khi chạy test context hierarchy, parent refresh được thực hiện trước child refresh. Root listener có thể nhận lifecycle event từ cả hai. Dùng `ContextRefreshedEvent.getApplicationContext()` để assert object identity, thay vì chỉ check event class.

### Test đóng context và ContextClosedEvent

Đóng child rồi parent, root có thể nhận child close và root close. Nếu teardown listener xóa shared resource ở lần child close, parent vẫn đang chạy nhưng shared resource đã bị hủy. Dùng owner filter hoặc phân loại shutdown event rõ.

## 15. Giao thức idempotency khi tác vụ quan trọng

Hierarchy có thể dẫn đến double callback, nhưng cũng có các nguồn duplicate độc lập (HTTP retry, scheduled task chạy lặp, queue redelivery). Một consumer quan trọng nên có:
- `eventId` duy nhất và `eventType`.
- `aggregateId` nếu sự kiện liên quan entity.
- Consumer identity nếu nhiều tác vụ khác nhau cùng xử lý một event.
- Một transaction/claim protocol bền theo yêu cầu nghiệp vụ.
- Thao tác provider ngoài hệ thống có idempotency key khi provider hỗ trợ.
- Retry limit/backoff, DLQ/alert với broker use case.
- Metrics tách `published`, `delivered`, `processed`, `deduplicated`.

### Ví dụ logical key

Nếu có `OrderCreated(eventId="EV-100",orderId="O-1")`, consumer gửi email có thể dùng key:

```text
consumer=email-notifier
event_id=EV-100
processing_state=COMPLETED
```

Consumer index unique `(consumer,event_id)` trong database đảm bảo **mỗi consumer** xử lý một event có kiểm soát, không làm event bị chặn bởi một consumer khác. Với external email provider, transaction database không thể tự rollback email; cần provider idempotency key hoặc state machine/compensation thích hợp.

### Không đánh đồng event ID với source context ID

`root` và `web` có thể tồn tại qua nhiều deploy/restart, không đại diện một event occurrence. Nếu dùng key chỉ là `originContextId` để dedup, bạn sẽ vô tình chặn **mọi** event hợp lệ tiếp theo từ cùng module. Sử dụng event ID độc lập, có thể bổ sung context/module metadata để debug.

### Đừng lưu object event toàn bộ vào Set

Một `ApplicationEvent` có `source` là ApplicationContext có thể tham chiếu toàn bộ container object graph; giữ event object trong cache/Set lâu dài có thể khiến GC không thu hồi contexts sau restart/redeploy. Dùng small immutable IDs/records và TTL theo chính sách nếu cần dedup cache.

## 16. Monitoring: phân biệt nơi publish với nơi xử lý

Một log hữu ích trong hệ thống có hierarchy nên ghi **logical IDs đã duyệt**, ví dụ:

```text
event_published type=OrderCreated eventId=EV-100 origin=web
event_handled   type=OrderCreated eventId=EV-100 listener=web-notifier context=web
event_handled   type=OrderCreated eventId=EV-100 listener=root-audit context=root
```

Các dòng là ví dụ, không phải output chương trình. Từ đây biết:
- Một event occurrence có thể có **nhiều consumers hợp lệ**.
- Parent listener nhận child-origin event là expected.
- Nếu cùng listener identity + cùng event ID có hai `event_handled` trong cùng processing scope, cần kiểm registration/hierarchy.
- Nếu root nhận `ContextRefreshedEvent` của các children, cần phân biệt origin context trước khi kết luận trùng xử lý.

Không log full event payload, password, token, raw request headers hay exception messages chứa credentials. Với high-cardinality event ID, không đưa mỗi event ID thành label của metrics; dùng trace/log hoặc sampled diagnostics.

### Instrumentation theo context

Có thể gắn context ID và listener ID vào spans, nhưng `@Async`/executor cần propagation của trace/MDC. ThreadLocal context không tự chuyển sang mọi async task. Nếu child/root có hai executor khác nhau, timing của spans có thể chồng chéo; đây không phải bằng chứng duplicate.

## 17. Các lỗi phổ biến và cách sửa nhanh

| Triệu chứng | Nguyên nhân | Giải pháp |
| --- | --- | --- |
| Root listener chạy cả khi child refresh | Child lifecycle event đi lên parent | Filter `event.getApplicationContext() == owner` |
| Listener cùng tên chạy hai lần với child event | Listener đăng ký root và child | Đăng ký tại đúng một context |
| Hai listener instances cùng class đều xử lý | Component scanning/@Import trùng | Tách root/child config packages |
| Child listener không nhận parent event | Event không đi xuống hierarchy | Đúng contract; thay architecture nếu muốn downward |
| Child A event không tới child B | Không có sibling routing | Dùng root aggregator/message channel rõ |
| Shared service phát nhưng child không nghe | Service giữ publisher của parent | Kiểm bean ownership/publisher injection |
| Root listener không nhận mọi child event | Child listener lỗi trước bước propagate | Xử lý errors, không dựa vào in-memory delivery cho audit bắt buộc |
| @Order ở root nhỏ hơn nhưng chạy sau child | Ordering theo từng multicaster | Không dùng order xuyên contexts |
| Event getSource không phải context | Custom ApplicationEvent dùng service/entity làm source | Thêm explicit origin metadata |
| ContextClosedEvent child khiến root cleanup sớm | Không lọc owner lifecycle | Check getApplicationContext identity |
| Sau @Async thứ tự log thay đổi | Concurrency giữa callbacks | Test completion bằng latch, không assert ordering async |
| @TransactionalEventListener không chạy | Không có transaction, phase không phù hợp | Kiểm tx boundary/fallbackExecution |
| Parent/child scan package như nhau | Root và child cùng tạo listener beans | Restrict component scan |
| Duplicate vẫn còn sau khi bỏ listener ở child | Producer có thể publish hai lần | Trace event ID và publish call sites |
| Một listener nhận cùng event hai lần với chính cùng object | Một object đăng ký hai multicaster | Kiểm registration tại từng context |
| Dedupe Set làm mất event khi handler fail | Đánh dấu processed trước khi hoàn tất | State machine/inbox transaction |
| Hết memory sau nhiều context redeploy | Giữ event/source context references trong cache | Cache immutable IDs, bounded retention |
| Listener root phát lại event sang parent | Republish thủ công không cần thiết | Dựa propagation mặc định |
| Test chỉ có một context không tái hiện | Hierarchy thiếu trong integration test | Tạo root+child thật và kiểm cả refresh/close |
| Có listener ở child nhưng không chạy | Chưa refresh/register đúng bean | Kiểm context local definitions/adapter |
| Event “vắng” do payload type mismatch | Generic type/listener method không phù hợp | Kiểm typed listener và PayloadApplicationEvent |

## 18. Checklist thiết kế cho ứng dụng nhiều context

- [ ] Có sơ đồ context hierarchy: root, children, siblings.
- [ ] Xác định rõ bean owner và publisher owner ở mỗi module.
- [ ] Biết event từ child đi lên ancestors, không tự đi xuống/sang sibling.
- [ ] Root global listeners đăng ký một lần, child listeners chỉ ở child phù hợp.
- [ ] Không quét listener package chồng lấn không chủ đích.
- [ ] Lifecycle listeners quan trọng lọc source/owner khi cần.
- [ ] Custom events có origin metadata rõ thay vì đoán `getSource()` luôn là context.
- [ ] Có event ID độc lập với context ID và aggregate ID.
- [ ] Có idempotency cho side effects phải chịu retries/duplicate.
- [ ] Không dùng một `ConcurrentHashMap.newKeySet()` như exactly-once bền.
- [ ] Không republish child event lên parent bằng tay nếu Spring đã propagate.
- [ ] Với child listener lỗi, biết nó có thể chặn parent notification.
- [ ] Với async, không dựa global callback completion order.
- [ ] Với @Order, hiểu sắp xếp cục bộ từng multicaster.
- [ ] Với transaction listeners, kiểm phase/tx boundary thay vì coi event như durable queue.
- [ ] Có test parent refresh rồi child refresh và child close rồi parent close.
- [ ] Có test parent publish không chạy child listener.
- [ ] Có test child publish chạy đúng child+parent handlers.
- [ ] Có test deliberate duplicate listener registration rồi chứng minh policy sửa.
- [ ] Có monitoring theo event ID, origin và listener/context identity.
- [ ] Không log thông tin nhạy cảm của events/exception.
- [ ] Không giữ context/event object tham chiếu lâu trong caches.
- [ ] Đánh giá service call/durable messaging nếu cơ chế in-process không phù hợp.

## 19. Câu hỏi tự kiểm tra và đáp án

1. Context child có thể nhìn thấy beans của parent theo cách nào?
2. Parent context có tự nhìn thấy child-only beans không?
3. Event phát ở child được gửi tới đâu trong default AbstractApplicationContext?
4. Event phát ở parent có tự tới child không?
5. Event phát ở child A có tự tới sibling child B không?
6. Tên bean đặc biệt cho multicaster là gì?
7. `ApplicationEventPublisher.publishEvent(...)` có luôn là async không?
8. Khi cùng listener object đăng ký ở root và child, child event có thể gọi object đó mấy lần?
9. Hai listener beans khác instances nhưng cùng class có thể cùng nhận một child event không?
10. Vì sao root listener có thể thấy hai ContextRefreshedEvent sau khi root và child khởi động?
11. Với `ContextRefreshedEvent`, lấy context phát event bằng method nào?
12. Làm sao chỉ xử lý lifecycle event của context sở hữu listener?
13. `ApplicationEvent.getSource()` có luôn là ApplicationContext không?
14. Nên dùng gì để biểu diễn originating context của custom business event?
15. Nếu shared root bean giữ publisher root, nó có phát được child-origin event mặc định không?
16. `@Order` có sắp xếp toàn bộ listeners across root/child không?
17. Child listener throw trước propagation thì parent có chắc nhận event không?
18. ErrorHandler mặc định có bảo đảm retry không?
19. Async executor có sửa được duplicate registrations không?
20. In-process event có tồn tại qua JVM crash/restart không?
21. Dùng event ID để phân biệt duplicate khác gì context ID?
22. Vì sao một in-memory Set không phải idempotency bền?
23. Trong demo, dòng `child-only: P-1` có xuất hiện không?
24. Trong demo, shared listener in C-2 mấy lần và tại sao?
25. Vì sao parent-own-only không in C-1?
26. Bean parent listener nhận child event do bean lookup hay event propagation?
27. ContextClosedEvent child có thể ảnh hưởng root cleanup như thế nào?
28. Vì sao việc listener trong root được gọi khi child publish không có nghĩa cross-process messaging?
29. Nên kiểm điều gì trước khi chỉnh sửa @ComponentScan?
30. Một sự kiện được xử lý 2 lần có luôn là bug không?

**Đáp án ngắn:** (1) Qua bean lookup kế thừa/parent delegation. (2) Không. (3) Local listeners, rồi các ancestor listeners theo upward propagation. (4) Không. (5) Không. (6) `applicationEventMulticaster`. (7) Không, mặc định đồng bộ với SimpleApplicationEventMulticaster. (8) Hai, nếu đăng ký cả hai multicaster và không filter/dedupe. (9) Có. (10) Có hai sự kiện khác origin context. (11) `event.getApplicationContext()`. (12) So sánh identity với injected owner context. (13) Không. (14) Explicit origin metadata do event contract định nghĩa. (15) Nó phát tại parent, không tự chuyển event xuống child. (16) Không. (17) Không. (18) Không. (19) Không. (20) Không. (21) Context ID chỉ là module/context identity; event ID biểu diễn một occurrence. (22) Mất khi restart, race, có thể đánh dấu trước khi processing xong. (23) Không. (24) Hai, do cùng object ở child và parent multicaster. (25) Source == child, không phải parent. (26) Do event propagation. (27) Có thể chạy cleanup quá sớm nếu không lọc owner. (28) Chỉ cùng context hierarchy/JVM, không có network durability. (29) Phân biệt scan trùng và producer publish lặp bằng instance/event IDs. (30) Không; có thể là nhiều consumers hợp lệ, chỉ là bug khi cùng effect diễn ra trái policy.

## 20. Nguồn tham khảo chính thức và đọc thêm

- Spring Framework 6.2 — `AbstractApplicationContext` Javadoc (`protected publishEvent(...)` là cơ chế propagation giữa contexts): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html
- Spring Framework 6.2 — `ApplicationEventPublisher` Javadoc (event hand-off): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html
- Spring Framework Reference — ApplicationContext Events: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events
- Spring Framework 6.2 — `ApplicationContextEvent` Javadoc (getApplicationContext): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/ApplicationContextEvent.html
- Spring Framework 6.2 — `ContextRefreshedEvent` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/ContextRefreshedEvent.html
- Spring Framework 6.2 — `ConfigurableApplicationContext` Javadoc (`setParent`, `addApplicationListener`): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ConfigurableApplicationContext.html
- Spring Framework 6.2 — `SimpleApplicationEventMulticaster` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html
- Spring Framework — Test Context Hierarchies: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/hierarchies.html
- Spring Boot — SpringApplication, event hierarchy note and context identity filter: https://docs.spring.io/spring-boot/reference/features/spring-application.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-007">Kế thừa MessageSource và phối hợp Environment</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa MessageSource và phối hợp Environment trong phân cấp Spring ApplicationContext

## 1. Vấn đề và mục tiêu

Trong Spring Framework, một ứng dụng có thể có **ApplicationContext cha (parent/root)** chứa những thành phần dùng chung, và các **ApplicationContext con (child)** dành cho web module, servlet, plugin hoặc nhóm chức năng cụ thể. Hai yêu cầu thường xuất hiện đồng thời:

- **Thông điệp đa ngôn ngữ:** module con dùng bản dịch của chính mình, có thể **ghi đè message code** của cha, nhưng vẫn muốn lấy những messages chung từ cha khi child bundle không có key.
- **Cấu hình môi trường:** module con cần đọc settings chung (ví dụ `app.sharedName`) của cha, nhưng được phép ưu tiên cấu hình riêng (ví dụ `app.mode`), cùng với active profiles phục vụ `@Profile` và `@Conditional`.

Spring hỗ trợ hai bài toán này, nhưng bằng **hai cơ chế hoàn toàn khác nhau**:

1. **`MessageSource` hierarchy:** chuỗi resolve messages thông qua `HierarchicalMessageSource` và parent context, hoạt động theo mã message + arguments + `Locale`.
2. **`Environment` merge:** ghép một số `PropertySource` và profile settings của môi trường parent vào môi trường child, có quy tắc ưu tiên/tên nguồn và thời điểm merge.

**Đừng đồng nhất hai cơ chế.** Một key trong `messages_vi.properties` không phải mặc nhiên là property có thể đọc bằng `environment.getProperty(...)`. Một key `app.mode` từ `MapPropertySource` cũng không được `MessageSource` tự xem thành bản dịch. Cả hai đều có khái niệm fallback/override, nhưng chúng không chia sẻ lookup path, cache hay lifecycle mặc định.

### Mục tiêu sau khi học

- Biết bean đặc biệt tên chính xác `messageSource` được khởi tạo trong mỗi ApplicationContext thế nào.
- Hiểu `HierarchicalMessageSource`, `DelegatingMessageSource` và hướng fallback **từ child lên parent**.
- Dự đoán kết quả khi child/parent đều định nghĩa cùng message code, khi chỉ parent có code và khi cả hai không có code.
- Hiểu cơ chế locale fallback **bên trong từng MessageSource** và message-source parent fallback là **hai tầng khác nhau**.
- Hiểu `ConfigurableEnvironment.merge(parent)`: thứ tự property sources, trường hợp trùng tên `PropertySource`, active/default profiles và thời điểm thay đổi.
- Cấu hình `setParent(...)` trước `refresh()`; xử lý `@Value`, `@Profile`, bean ownership và lifecycle có chủ đích.
- Chạy ví dụ **Java 17 + Spring Framework 6.2.19 + Maven**: parent/child contexts, bốn tệp message, MapPropertySource, kiểm tra override, inherited message, property precedence, inherited profile, missing-key behavior.
- Kiểm thử đúng các ranh giới: không tự lan từ parent xuống child, không coi merge là cơ chế refresh realtime hoặc các messages là environment properties.

## 2. Sơ đồ tổng quan

~~~text
                         PARENT ApplicationContext
                   ┌────────────────────────────────┐
                   │ bean "messageSource"            │
                   │   basename: i18n/parent/messages│
                   │   common.greet                  │
                   │   parent.only                   │
                   │                                │
                   │ Environment                    │
                   │   parentSettings                │
                   │   app.mode = PARENT             │
                   │   app.shared = SHARED           │
                   │   active profile = shared       │
                   └──────────────┬─────────────────┘
                                  │ child.setParent(parent)
                                  ▼
                          CHILD ApplicationContext
                   ┌────────────────────────────────┐
                   │ bean "messageSource"            │
                   │   basename: i18n/child/messages │
                   │   common.greet  (override)      │
                   │   child.only                    │
                   │   parentMessageSource → parent  │
                   │                                │
                   │ Environment (merge lúc setParent)│
                   │   childSettings                 │
                   │   app.mode = CHILD              │
                   │   app.local = LOCAL             │
                   │   inherited parentSettings      │
                   │   app.shared = SHARED           │
                   │   inherited profile = shared    │
                   └────────────────────────────────┘
~~~

Hai luồng khác nhau:

~~~text
child.getMessage("parent.only", null, vi)
  → tìm trong child MessageSource
  → không thấy
  → thử parent MessageSource
  → trả thông điệp của cha

child.getEnvironment().getProperty("app.shared")
  → duyệt PropertySources đã có ở Environment child
  → tìm được property từ parentSettings đã merge
  → trả "SHARED"
~~~

**Khác biệt then chốt:** MessageSource fallback có thể tiếp tục hỏi parent source khi lookup message tại runtime. Environment child thường nắm một **danh sách property sources đã được merge ở thời điểm setParent**; không có quy tắc “mỗi lần child getProperty là gọi ngược parent.getProperty” giống hệt MessageSource.

## 3. ApplicationContext làm gì với bean tên messageSource?

`ApplicationContext` extends `MessageSource`. Trong `AbstractApplicationContext.refresh()`, container chạy bước `initMessageSource()`. Nó tìm **bean có tên `messageSource`** trong chính context, rồi liên kết với source của parent nếu có và phù hợp.

Các khả năng chính:

| Trường hợp | Behavior cần nhớ |
| --- | --- |
| Parent có `messageSource`; child cũng có bean tên `messageSource` triển khai `HierarchicalMessageSource` | Child source được dùng để tìm local codes, đồng thời có thể được nối với parent source để fallback |
| Parent có `messageSource`; child không khai báo local bean `messageSource` | Context child thiết lập `DelegatingMessageSource` có thể delegate đến parent source |
| Parent và child đều không có `messageSource` | Context có một delegating/empty source; key không có thì default/exception theo overload |
| Child đăng ký bean bất kỳ tên `translations` mà không tên `messageSource` | Không tự được dùng làm special MessageSource của ApplicationContext |
| Child có bean tên `messageSource` nhưng không implement `MessageSource` | Không phải một configuration hợp lệ; context có thể fail lúc khởi tạo message source |
| Một MessageSource custom không triển khai hierarchy protocol | Không nên giả định sẽ được container gắn parent fallback bằng cùng cơ chế |

Điểm quan trọng: bean phải có **tên đúng `messageSource`**, không chỉ có type `MessageSource`. Trong Spring JavaConfig, có thể viết `@Bean("messageSource")` để explicit tên.

### MessageSource không phải chỉ là getBean theo tên

Khi gọi:

~~~java
child.getMessage("parent.only", null, locale);
~~~

bạn đang gọi **MessageSource API của child ApplicationContext**, nó thực hiện fallback theo hierarchy của MessageSource. Đây không phải một lần gọi `child.getBean("messageSource")` rồi tự yêu cầu bean factory tìm bean tên đó ở cha mỗi lần.

Một child có bean `messageSource` local vẫn có thể tra parent messages vì source local được nối với parent source. Do đó **bean shadowing** và **message fallback** là hai chuyện khác nhau:

- `child.getBean("messageSource")` lấy **bean local của child**, nếu có.
- `child.getMessage(...)` tra child codes rồi có thể fallback sang parent source.
- `parent.getMessage(...)` không tự tìm ngược xuống child bundles.

## 4. HierarchicalMessageSource: hợp đồng phân cấp

`org.springframework.context.HierarchicalMessageSource` mở rộng `MessageSource`, định nghĩa hai method trọng tâm:

~~~java
void setParentMessageSource(MessageSource parent);
MessageSource getParentMessageSource();
~~~

Đây là chữ ký rút gọn. Các implementation Spring phổ biến như `ResourceBundleMessageSource` và `ReloadableResourceBundleMessageSource` kế thừa `AbstractMessageSource`, vốn triển khai `HierarchicalMessageSource`.

### Trình tự lookup tổng quát

~~~text
child MessageSource.getMessage(code, args, locale)
             │
             ├── Child tìm code trong basenames/properties
             │      └── Nếu có → format và trả value
             │
             ├── Child không tìm được
             │      └── parentMessageSource.resolve(code, args, locale)
             │              ├── Nếu có → trả value từ parent
             │              └── Nếu không → không resolve được
             │
             └── Default message / NoSuchMessageException
~~~

**Trong từng nguồn**, Spring có thể tra bundles theo locale variants, default locale và root bundle trước khi quyết định code không tìm thấy. Vì vậy một child root bundle có key `common.greet` sẽ có thể che bản dịch parent cho code đó, dù parent có bản dịch theo locale cụ thể. Đây là lý do key precedence phải được test theo **code lẫn Locale**.

### Cha cũng có thể có parent

Hierarchy có thể có Root → Feature → Page. Child source có thể được nối với parent source, rồi parent source tiếp tục nối với grandparent source. Cơ chế này hỗ trợ modules nhiều tầng, nhưng khó debug nếu ba tầng dùng chung một code. Ưu tiên namespace message keys và một cấu trúc fallback có chủ đích.

### Quan hệ MessageSource không sửa bundles của cha

Child `common.greet` che parent `common.greet` trong lookup từ child, nhưng không ghi đè file cha hay thay bean parent. Parent tiếp tục phục vụ parent translations cho các calls bắt nguồn tại root hoặc các sibling children khác.

## 5. DelegatingMessageSource khi child không có local bundle

Nếu child không định nghĩa bean `messageSource`, Spring vẫn cần cung cấp MessageSource service vì ApplicationContext implement `MessageSource`. `DelegatingMessageSource` có thể được thiết lập để delegate đến parent MessageSource.

~~~text
Parent context:
    messageSource → ResourceBundleMessageSource(parent bundles)
                            ▲
                            │
Child without local source:
    internal DelegatingMessageSource
        parentMessageSource ┘
~~~

Ví dụ:

~~~java
try (var parent = new AnnotationConfigApplicationContext(
        ParentConfig.class)) {

    try (var child = new AnnotationConfigApplicationContext()) {
        child.setParent(parent);
        child.register(FeatureConfigWithoutMessageSource.class);
        child.refresh();

        String text = child.getMessage(
                "parent.only", null, Locale.forLanguageTag("vi-VN"));
    }
}
~~~

Đây là snippet: `ParentConfig` và `FeatureConfigWithoutMessageSource` phải được khai báo, và parent có `messageSource`. Nếu code `parent.only` tồn tại ở parent, child vẫn có thể lấy text nhờ delegation.

### Quan trọng về bean lookup

Không suy rằng có `DelegatingMessageSource` nội bộ nghĩa là child đã đăng ký một **local bean definition** tên `messageSource` giống một `@Bean` do developer khai báo. Khi debug, phải phân biệt internal MessageSource của context với bean definitions/parent bean visibility.

Nếu thực sự cần kiểm tra, dùng cả `getBean("messageSource")`, `containsLocalBean("messageSource")`, `getMessage(...)` và `HierarchicalMessageSource.getParentMessageSource()` khi phù hợp. Không mặc định các API ấy sẽ cho cùng một đối tượng hoặc cùng ý nghĩa ownership.

## 6. ResourceBundleMessageSource và ReloadableResourceBundleMessageSource trong hierarchy

Hai implementation đều có thể được dùng làm child/parent sources.

| Tiêu chí | ResourceBundleMessageSource | ReloadableResourceBundleMessageSource |
| --- | --- | --- |
| Basename thường dùng | `i18n/parent/messages` | `classpath:i18n/parent/messages` hoặc `file:/.../messages` |
| Đọc từ | JDK `ResourceBundle` trên classpath | Spring `Resource`/`PropertiesPersister` |
| Parent fallback | Thông qua `HierarchicalMessageSource` | Thông qua `HierarchicalMessageSource` |
| Bản dịch theo `Locale` | Có | Có |
| Format `{0}` | Có | Có |
| Thích hợp cho bundles trong JAR | Có | Có, nhưng thường không cần reload |
| Reload file ngoài JAR | Không phải use case chính | Có hỗ trợ kiểm tra file changes |
| Phối hợp Environment tự động | Không; API độc lập | Không; API độc lập |

Cấu hình parent:

~~~java
@Bean("messageSource")
static MessageSource parentMessages() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("i18n/parent/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
}
~~~

Cấu hình child:

~~~java
@Bean("messageSource")
static MessageSource childMessages() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("i18n/child/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
}
~~~

Hai snippets khai báo **trong hai context configuration classes riêng**. Chúng không phải hai bean cùng tên trong cùng một BeanFactory. Khi child refresh với parent đã được thiết lập, Spring liên kết MessageSource hierarchy theo contract hỗ trợ.

### Đừng thêm basename của parent vào child để mô phỏng quan hệ

Có thể cấu hình nhiều basenames ở child, nhưng đó là **nhiều bundles trong một MessageSource**, không hẳn là một context hierarchy đúng nghĩa. Nếu bạn vừa add parent basename vào child vừa có parent messageSource, bạn có thể vô tình load trùng/che các codes theo precedence khác kỳ vọng.

Khi cần parent-child context rõ, hãy giữ bundles thuộc context sở hữu chúng và kiểm tra fallback bằng API công khai.

## 7. Locale fallback và parent MessageSource fallback là hai lớp riêng

Giả sử:

~~~text
parent/messages.properties:
  common.greet = Hello from parent, {0}!
  parent.only = Parent only

parent/messages_vi.properties:
  common.greet = Xin chào từ cha, {0}!
  parent.only = Chỉ có ở cha

child/messages.properties:
  common.greet = Hello from child, {0}!
  child.only = Child only

child/messages_vi.properties:
  common.greet = Xin chào từ con, {0}!
  child.only = Chỉ có ở con
~~~

Với `Locale.forLanguageTag("vi-VN")`:

| Message code | Child có? | Parent có? | Child lookup |
| --- | --- | --- | --- |
| `common.greet` | Có ở child Vietnamese bundle | Có ở parent Vietnamese bundle | `Xin chào từ con, Lan!` |
| `child.only` | Có ở child | Không cần | `Chỉ có ở con` |
| `parent.only` | Không | Có ở parent | `Chỉ có ở cha` |
| `unknown.code` | Không | Không | Default message hoặc `NoSuchMessageException` |

Một điểm tinh tế: nếu child có `common.greet` **chỉ trong child root bundle** nhưng không có ở child Vietnamese bundle, Spring có thể trả child root value. Parent Vietnamese translation không nhất thiết được ưu tiên, vì child đã tìm được code ở local source. Nếu nghiệp vụ muốn parent Vietnamese translation thắng child root value, cần thiết kế precedence/custom source hoặc bỏ key khỏi child root và test cụ thể. **Hierarchy message fallback xảy ra khi child source không resolve được mã**, không phải “chỉ cần child thiếu bản dịch của đúng locale thì luôn nhảy parent ngay”.

### Tắt fallback dựa vào máy chủ

~~~java
ms.setFallbackToSystemLocale(false);
~~~

Trong backend phục vụ nhiều Locale, system default locale phụ thuộc JVM/server và có thể khác giữa dev/test/prod. Tắt tùy chọn này thường giúp lookup predictable khi có bundle gốc.

`Locale.getDefault()` của server **không** phải requested locale của từng người dùng. Truyền Locale rõ ràng từ user/web layer/job, không dùng `Locale.setDefault(...)` theo từng request.

### Các biến thể filenames

Basename `i18n/child/messages` có thể đi kèm:

~~~text
messages.properties
messages_vi.properties
messages_vi_VN.properties
messages_en.properties
messages_en_US.properties
~~~

Tên file dùng dấu gạch dưới; `Locale.forLanguageTag("vi-VN")` dùng BCP 47 hyphen. Không tự thay path thành `messages_vi-VN.properties` nếu vẫn theo convention resource bundles truyền thống.

## 8. Missing key, default và NoSuchMessageException

Ba overload nổi bật của `MessageSource`:

~~~java
String getMessage(
        String code, Object[] args, String defaultMessage, Locale locale);

String getMessage(
        String code, Object[] args, Locale locale);

String getMessage(
        MessageSourceResolvable resolvable, Locale locale);
~~~

Trong phân cấp context:

- Nếu child hoặc parent resolve được code, trả bản dịch tương ứng.
- Nếu không nơi nào resolve được, overload nhận `defaultMessage` có thể trả chuỗi default.
- Nếu không có default phù hợp, overload strict có thể ném `NoSuchMessageException`.
- Nếu bật `useCodeAsDefaultMessage(true)`, source có thể trả mã message làm default thay vì ném exception.

**Lưu ý về `useCodeAsDefaultMessage`:** Spring `AbstractMessageSource` cảnh báo bật tùy chọn này trong parent, nhất là khi dùng `MessageSourceResolvable` chứa nhiều codes (như validation errors), có thể làm fallback codes hoạt động khác mong đợi. Đừng bật “in luôn code khi missing” như một policy production im lặng. Kiểm thử missing-key trong từng source/hierarchy.

### MessageFormat có vai trò gì?

Message like `Xin chào từ con, {0}!` được format khi gọi:

~~~java
child.getMessage(
        "common.greet",
        new Object[]{"Lan"},
        Locale.forLanguageTag("vi-VN"));
~~~

Kết quả `Xin chào từ con, Lan!`. `{0}` là placeholder của `java.text.MessageFormat`, khác với `${app.mode}` của Spring Environment. Apostrophe trong MessageFormat cần escape theo grammar, ví dụ `Don''t` nếu cần output `Don't` trong pattern có args.

## 9. Environment hoạt động khác MessageSource như thế nào?

`ConfigurableEnvironment` lưu một danh sách `PropertySources` để resolve externalized config như feature flags, profile conditions, URLs, timeout hoặc mode. Những properties không có Locale translation semantics mặc định.

Ví dụ:

~~~text
Parent Environment:
  parentSettings:
    app.mode = PARENT
    app.shared = SHARED

Child Environment trước merge:
  childSettings:
    app.mode = CHILD
    app.local = LOCAL
~~~

Khi `child.setParent(parent)` và parent Environment là `ConfigurableEnvironment`, `AbstractApplicationContext` có thể gọi `childEnv.merge(parentEnv)`. Parent property sources được **append** vào child property sources trừ nguồn trùng tên; active/default profiles cũng được merge có kiểm soát.

Kết quả kỳ vọng (với sources được thêm bằng `addFirst`, tên khác nhau):

| Property key | Parent Environment | Child Environment |
| --- | --- | --- |
| `app.mode` | `PARENT` | `CHILD` |
| `app.shared` | `SHARED` | `SHARED` (từ parentSettings được merge) |
| `app.local` | Không có | `LOCAL` |

**MessageSource fallback:** một code thiếu được lookup tiếp ở parent source **khi gọi getMessage**.

**Environment merge:** parent sources đã được thêm vào danh sách của child **khi thiết lập hierarchy**. Sau đó `child.getProperty` duyệt các sources của chính child theo thứ tự ưu tiên hiện hành.

Đừng implement một child `PropertySource` giả để gọi `parent.getEnvironment().getProperty` với mọi key rồi gọi đó là hành vi chuẩn Spring merge. Điều đó là một strategy khác, có semantics cập nhật và precedence khác.

## 10. Chính xác ConfigurableEnvironment.merge(...) làm gì?

Theo Spring 6.2 Javadoc, `ConfigurableEnvironment.merge(parent)`:

1. Append những **parent `PropertySource` objects** vào danh sách child, trừ các source trùng tên đã có.
2. Append những **active profile names** từ parent, lọc trùng.
3. Append những **default profile names** từ parent, lọc trùng.
4. Giữ parent Environment **không bị thay đổi** bởi thao tác merge.
5. Giữ `PropertySource` của child khi child và parent có **cùng tên source**.
6. Không bảo đảm các thay đổi xảy ra trong parent Environment **sau khi merge** sẽ tự được phản ánh đầy đủ ở child.

### Parent PropertySource không nhất thiết thắng

Vì parent sources được append sau child sources, child sources có thể ưu tiên. Nhưng **thứ tự thực tế còn chịu ảnh hưởng** bởi default system properties/environment sources, `addFirst`/`addLast`, `@PropertySource`, web/Boot-specific sources và các tùy biến khác. Kiểm tra danh sách thực trước khi kết luận một key lấy giá trị nào.

Ví dụ:

~~~java
child.getEnvironment().getPropertySources().addFirst(
    new MapPropertySource("childSettings", Map.of(
        "app.mode", "CHILD",
        "app.local", "LOCAL")));
~~~

Đây là snippet, cần import `Map` và `MapPropertySource`, chạy trước child refresh. Nếu parent thêm một source tên `parentSettings` có `app.mode=PARENT`, thì childSettings đứng trước nên `CHILD` được ưu tiên.

### Cùng tên PropertySource không merge từng key

Giả sử:

~~~text
Parent có MapPropertySource "sharedConfig":
  app.shared = SHARED
  app.timeout = 1000

Child có MapPropertySource "sharedConfig":
  app.mode = CHILD
~~~

Sau `childEnv.merge(parentEnv)`, source `"sharedConfig"` ở child được giữ lại, **source trùng tên của parent bị bỏ qua**, không có thao tác merge từng entry của hai maps. Do đó child có thể **không thấy app.shared/app.timeout** từ source trùng tên parent. Đây là một trong những nguyên nhân phổ biến khiến “con kế thừa environment cha” nhưng một key vẫn thiếu.

**Giải pháp:** đặt source names khác nhau và ưu tiên rõ, hoặc tự tổng hợp dữ liệu có kiểm soát trước khi đăng ký một source, không giả định Spring deep-merge maps.

### Child giữ bản override không làm sửa parent

Nếu child `app.mode=CHILD` nhưng parent `app.mode=PARENT`, parent vẫn trả `PARENT`. Đây là precedence trong resolver của child, không phải mutation parent MapPropertySource.

## 11. Thời điểm setParent quyết định kết quả merge

Cấu hình đúng trong Java code:

~~~java
var parent = new AnnotationConfigApplicationContext();

// Thiết lập nguồn và profiles trước khi parent refresh.
parent.getEnvironment().getPropertySources().addFirst(
    new MapPropertySource("parentSettings", Map.of(
        "app.mode", "PARENT",
        "app.shared", "SHARED")));
parent.getEnvironment().setActiveProfiles("shared");
parent.register(ParentConfig.class);
parent.refresh();

var child = new AnnotationConfigApplicationContext();
child.getEnvironment().getPropertySources().addFirst(
    new MapPropertySource("childSettings", Map.of(
        "app.mode", "CHILD",
        "app.local", "LOCAL")));
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
~~~

Đây là snippet; cần imports và cấu hình/close contexts. Ví dụ hoàn chỉnh có trong mục 14.

Tại sao không dùng ngay:

~~~java
new AnnotationConfigApplicationContext(ChildConfig.class);
~~~

Constructor nhận configuration class **refresh context ngay**. Nếu bạn muốn `setParent(...)`, thêm property sources và profiles **trước refresh**, constructor no-arg + `register(...); refresh();` là đường phù hợp.

### Parent Environment thay đổi sau merge

Spring Javadoc nêu rõ những thay đổi parent Environment sau merge sẽ không được phản ánh đầy đủ trong child. Ví dụ thêm một **PropertySource mới** hoặc thay active profiles ở parent sau `child.setParent(...)` không làm child tự merge lần nữa.

Một số object `PropertySource` có thể được hai môi trường cùng tham chiếu; nếu mutate nội dung của chính map object dùng chung, lần đọc sau ở child **có thể** thấy thay đổi tùy implementation. Tuy nhiên điều đó không biến Environment merge thành một hệ thống **dynamic refresh với snapshot consistency**. Thiết kế reconfiguration runtime phải có lifecycle/boundary/validation riêng.

### @Profile cũng chịu thời điểm configuration

Nếu parent active profile `shared` trước `setParent`, child merge profile này. Một `@Bean @Profile("shared")` trong child có thể được đăng ký khi child refresh.

Nếu thay active profile sau refresh, Spring không tự tạo/xóa các bean definitions đã được condition evaluation ở startup. Đặt profiles **trước khi register/refresh** hoặc ít nhất trước khi các conditions được đánh giá.

## 12. @Value và typed configuration trong child

Một child bean có thể được inject giá trị từ child Environment, bao gồm những parent sources đã merge:

~~~java
@Component
public final class CheckoutSettings {

    private final String mode;
    private final String shared;

    public CheckoutSettings(
            @Value("${app.mode}") String mode,
            @Value("${app.shared}") String shared) {
        this.mode = mode;
        this.shared = shared;
    }
}
~~~

Đây là snippet cần imports `@Component`/`@Value`, component registration và suitable placeholder resolution. Với childSettings ở trên, kết quả là `mode=CHILD`, `shared=SHARED`.

### Thời điểm inject

Với singleton, `@Value` thường được resolve khi bean được tạo. Sau đó đổi một PropertySource không tự cập nhật field đã inject. Đây là **typed/config snapshot** theo bean lifecycle; khác với gọi `environment.getProperty(...)` ở một thời điểm mới.

### PropertySourcesPlaceholderConfigurer đặt ở đâu?

`PropertySourcesPlaceholderConfigurer` là `BeanFactoryPostProcessor` hỗ trợ placeholder trong bean metadata/injection khi được đăng ký đúng. BFPP có **scope theo container**. BFPP của parent không tự xử lý bean definitions thuộc child. Nếu child cần resolver/configurer riêng, hãy khai báo ở đúng child configuration:

~~~java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
~~~

Trong JavaConfig, `static @Bean` đặc biệt phù hợp cho BFPP để tránh instantiate configuration class sớm. Parent cũng có thể cần configurer của mình.

Đừng nhầm `PropertySourcesPlaceholderConfigurer` với `MessageSource` dù cả hai có thể xử lý strings trong quá trình khởi tạo. PSPC làm việc với configuration placeholders; MessageSource làm việc với localized message codes.

## 13. @Profile và @Conditional trong hierarchy

Profiles là tập tên môi trường có thể ảnh hưởng bean graph:

~~~java
@Bean
@Profile("shared")
public SharedProfileFlag sharedProfileFlag() {
    return new SharedProfileFlag();
}
~~~

Đây là snippet; cần type `SharedProfileFlag`. Nếu child có profile `shared` được merge từ parent trước child refresh, bean có thể được đăng ký.

Phân biệt:

| Khái niệm | Dùng để làm gì | Phụ thuộc vào |
| --- | --- | --- |
| `@Profile("shared")` | Chọn bean definition có được đăng ký | Active/default profiles của Environment tại evaluation |
| `@Conditional` | Chọn bean theo custom condition | Context, Environment, metadata tùy condition |
| `MessageSource.getMessage(..., vi)` | Chọn text theo Locale | Bundle, Locale, code, parent messages |
| `@Value("${app.mode}")` | Inject property vào bean | Child Environment/placeholder resolution |
| `@Order` | Sắp xếp khi framework dùng comparator | Order metadata |

Profile `shared` **không phải** locale `vi-VN`. Không tạo một profile cho mỗi user language nếu một server phải đồng thời xử lý nhiều languages; `Locale` do method getMessage nhận vào.

### Default profiles

`ConfigurableEnvironment.merge` cũng phối hợp default profiles từ parent. “Default” không có nghĩa luôn active: nếu có active profiles cụ thể, default profiles có semantics riêng. Khi debug `@Profile`, đọc `getActiveProfiles()` và `getDefaultProfiles()` của child sau merge, không chỉ nhìn parent config.

## 14. Ví dụ Java 17 hoàn chỉnh: MessageSource fallback + Environment merge

Ví dụ chạy với **Java 17**, **Maven**, **Spring Framework 6.2.19**, không sử dụng Spring Boot hoặc web server.

Chương trình:

- Dùng **hai context riêng** với `setParent` trước refresh.
- Parent có resource bundles `i18n/parent/messages`, child có `i18n/child/messages`.
- Cả parent/child đều định nghĩa bean **tên `messageSource`**, với implementation `ResourceBundleMessageSource`.
- Parent có `parentSettings` và profile `shared`.
- Child có `childSettings` ghi đè `app.mode`, kế thừa `app.shared`, và có bean chỉ active khi profile `shared`.
- Kiểm tra message code `common.greet` được child override, `parent.only` fallback cha, `child.only` chỉ tồn tại ở child.
- Kiểm tra key thiếu, parent không thể đọc child-only message, property precedence và profile đã được merge.
- Fail fast với `IllegalStateException` nếu bất kỳ assertion nào sai.

### Cấu trúc thư mục

~~~text
spring-hierarchy-message-env/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── HierarchyMessageEnvironmentDemo.java
        └── resources/
            └── i18n/
                ├── parent/
                │   ├── messages.properties
                │   └── messages_vi.properties
                └── child/
                    ├── messages.properties
                    └── messages_vi.properties
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-hierarchy-message-env</artifactId>
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
~~~

### src/main/resources/i18n/parent/messages.properties

~~~properties
common.greet=Hello from parent, {0}!
parent.only=Parent only
~~~

### src/main/resources/i18n/parent/messages_vi.properties

~~~properties
common.greet=Xin chào từ cha, {0}!
parent.only=Chỉ có ở cha
~~~

### src/main/resources/i18n/child/messages.properties

~~~properties
common.greet=Hello from child, {0}!
child.only=Child only
~~~

### src/main/resources/i18n/child/messages_vi.properties

~~~properties
common.greet=Xin chào từ con, {0}!
child.only=Chỉ có ở con
~~~

Lưu cả bốn files bằng **UTF-8**, không thêm Markdown fences vào file thật.

### src/main/java/example/HierarchyMessageEnvironmentDemo.java

~~~java
package example;

import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

public final class HierarchyMessageEnvironmentDemo {

    private static final Locale VI =
            Locale.forLanguageTag("vi-VN");

    public static void main(String[] args) {
        try (var parent = new AnnotationConfigApplicationContext()) {

            parent.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("parentSettings",
                            Map.<String, Object>of(
                                    "app.mode", "PARENT",
                                    "app.shared", "SHARED")));

            parent.getEnvironment().setActiveProfiles("shared");
            parent.register(ParentConfig.class);
            parent.refresh();

            try (var child = new AnnotationConfigApplicationContext()) {
                child.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("childSettings",
                                Map.<String, Object>of(
                                        "app.mode", "CHILD",
                                        "app.local", "LOCAL")));

                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                String parentGreeting = parent.getMessage(
                        "common.greet", new Object[]{"Lan"}, VI);
                String childGreeting = child.getMessage(
                        "common.greet", new Object[]{"Lan"}, VI);
                String inheritedMessage = child.getMessage(
                        "parent.only", null, VI);
                String childMessage = child.getMessage(
                        "child.only", null, VI);
                String parentMissing = parent.getMessage(
                        "child.only", null, "MISSING", VI);

                String parentMode = parent.getEnvironment()
                        .getProperty("app.mode");
                String childMode = child.getEnvironment()
                        .getProperty("app.mode");
                String shared = child.getEnvironment()
                        .getProperty("app.shared");
                String local = child.getEnvironment()
                        .getProperty("app.local");
                String parentLocal = parent.getEnvironment()
                        .getProperty("app.local", "MISSING");

                boolean inheritedProfile = child.getEnvironment()
                        .acceptsProfiles(Profiles.of("shared"));
                boolean profileBeanLocal =
                        child.containsLocalBean("featureFlag");

                boolean parentAttached =
                        ((org.springframework.context.HierarchicalMessageSource)
                                child.getBean("messageSource"))
                        .getParentMessageSource() != null;

                boolean missingException = false;
                try {
                    child.getMessage("unknown.code", null, VI);
                } catch (NoSuchMessageException ex) {
                    missingException = true;
                }

                check("Xin chào từ cha, Lan!", parentGreeting);
                check("Xin chào từ con, Lan!", childGreeting);
                check("Chỉ có ở cha", inheritedMessage);
                check("Chỉ có ở con", childMessage);
                check("MISSING", parentMissing);
                check("PARENT", parentMode);
                check("CHILD", childMode);
                check("SHARED", shared);
                check("LOCAL", local);
                check("MISSING", parentLocal);
                require(inheritedProfile, "inherited profile");
                require(profileBeanLocal, "profile bean local");
                require(parentAttached, "parent message source");
                require(missingException, "missing code exception");

                System.out.println("Parent greet: " + parentGreeting);
                System.out.println("Child greet: " + childGreeting);
                System.out.println("Inherited text: " + inheritedMessage);
                System.out.println("Child-only text: " + childMessage);
                System.out.println("Parent sees child text: " + parentMissing);
                System.out.println("Parent mode: " + parentMode);
                System.out.println("Child mode: " + childMode);
                System.out.println("Child shared: " + shared);
                System.out.println("Child local: " + local);
                System.out.println("Parent local: " + parentLocal);
                System.out.println("Inherited profile: " + inheritedProfile);
                System.out.println("Profile bean local: " + profileBeanLocal);
                System.out.println("Message parent attached: "
                        + parentAttached);
                System.out.println("Missing code exception: "
                        + missingException);
                System.out.println("Hierarchy verification: PASS");
            }
        }
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Expected [" + expected + "] but was [" + actual + "]");
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new IllegalStateException("Failed: " + name);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {
        @Bean("messageSource")
        public static MessageSource messages() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("i18n/parent/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {
        @Bean("messageSource")
        public static MessageSource messages() {
            var source = new ResourceBundleMessageSource();
            source.setBasename("i18n/child/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }

        @Bean("featureFlag")
        @Profile("shared")
        public String featureFlag() {
            return "ENABLED";
        }
    }
}
~~~

### Chạy bằng Maven

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.HierarchyMessageEnvironmentDemo
~~~

Stdout của chính các `System.out.println`, không tính Maven/logging:

~~~text
Parent greet: Xin chào từ cha, Lan!
Child greet: Xin chào từ con, Lan!
Inherited text: Chỉ có ở cha
Child-only text: Chỉ có ở con
Parent sees child text: MISSING
Parent mode: PARENT
Child mode: CHILD
Child shared: SHARED
Child local: LOCAL
Parent local: MISSING
Inherited profile: true
Profile bean local: true
Message parent attached: true
Missing code exception: true
Hierarchy verification: PASS
~~~

### Vì sao ví dụ hoạt động?

1. Parent được tạo bằng constructor không đối số nên có thể thêm property sources và active profiles **trước refresh**.
2. Parent `ParentConfig` định nghĩa bean đặc biệt tên `messageSource`, sử dụng basename `i18n/parent/messages`.
3. Child được tạo chưa refresh. `childSettings` được thêm bằng `addFirst`, ưu tiên `app.mode=CHILD`.
4. `child.setParent(parent)` nối BeanFactory hierarchy và merge Environment parent vào child đúng thời điểm.
5. `child.register(ChildConfig.class); child.refresh();` tạo bean `messageSource` local của child, Spring gắn parent source do implementation có `HierarchicalMessageSource`.
6. `common.greet` có ở cả child và parent, child local value thắng khi lookup từ child. Parent lookup vẫn trả parent value.
7. `parent.only` không có trong child bundles nên child MessageSource fallback lên parent. `child.only` không được root nhìn thấy.
8. `app.mode` hiện diện ở cả childSettings/parentSettings, childSettings ưu tiên vì được đặt đầu. `app.shared` chỉ có ở parentSettings nên child đọc được qua merge. `app.local` chỉ child có.
9. Active profile `shared` được thêm vào parent Environment trước khi setParent, nên child thấy nó và `@Profile("shared")` bean `featureFlag` được tạo local.
10. `HierarchicalMessageSource.getParentMessageSource() != null` kiểm chứng wiring parent. `NoSuchMessageException` được bắt khi code không có ở cả hai nguồn.
11. Các assertions được kiểm tra **trước khi in kết quả**. Nếu một giả định sai, chương trình ném exception và không in dòng `Hierarchy verification: PASS`.
12. `try-with-resources` đóng child trước parent, tránh destroy child bean trong khi parent resource đã bị đóng.

### Điều ví dụ không tuyên bố

- Không kiểm thử hot reload các bundle files trong JAR.
- Không dùng external HTTP, database, Spring Boot hoặc transaction.
- Không chứng minh parent Environment tự re-merge khi có một PropertySource mới sau `setParent`.
- Không minh họa mutable shared map property values ở runtime.
- Không dùng tên parent/child basenames giống nhau trong một context rồi trông chờ tự merge text.
- Không cho phép parent tự nhìn thấy child-only bean/property/message.
- Không có giả định rằng Locale theo máy chủ phù hợp với Locale của mọi người dùng.

## 15. Chứng minh môi trường không deep-merge hai nguồn trùng tên

Ngoài ví dụ chạy ở mục 14, có thể cô lập `ConfigurableEnvironment.merge` bằng Java code:

~~~java
var parentEnv = new StandardEnvironment();
parentEnv.getPropertySources().addFirst(
        new MapPropertySource("sharedConfig",
                Map.of("app.shared", "SHARED")));

var childEnv = new StandardEnvironment();
childEnv.getPropertySources().addFirst(
        new MapPropertySource("sharedConfig",
                Map.of("app.mode", "CHILD")));

childEnv.merge(parentEnv);

String mode = childEnv.getProperty("app.mode"); // "CHILD"
String shared = childEnv.getProperty("app.shared"); // có thể null
~~~

Đây là snippet; cần imports `StandardEnvironment`, `MapPropertySource` và `Map`. Vì hai sources có cùng **tên `sharedConfig`**, bản child được giữ và source parent cùng tên bị loại. Trong ví dụ cô lập này, `app.shared` không có ở các sources còn lại nên `shared` là `null`. Không nên gọi đây là “child ưu tiên key mode nhưng vẫn kế thừa mọi key khác từ cùng source name”.

### Sửa thế nào?

Cách rõ ràng nhất là đặt tên nguồn khác:

~~~java
parentEnv.getPropertySources().addFirst(
        new MapPropertySource("parentSettings",
                Map.of("app.shared", "SHARED")));

childEnv.getPropertySources().addFirst(
        new MapPropertySource("childSettings",
                Map.of("app.mode", "CHILD")));
~~~

Trong code thật cần đảm bảo merge thực hiện **sau khi** xây hai Environments. Khi source names khác nhau, child có thể chứa cả hai và resolver chọn theo thứ tự property sources.

### Trùng key nhưng khác source name

Nếu hai sources khác tên cùng chứa `app.mode`, ưu tiên do thứ tự duyệt. Đây là **key override**; còn khi source names trùng, đó là **source replacement/drop-on-merge**. Hai trường hợp có kết quả khác nhau với keys chỉ tồn tại trong parent source.

## 16. MessageSource fallback không bị điều khiển bởi @Profile hoặc @Value một cách tự động

Khi Spring resolve:

~~~java
String text = child.getMessage(
        "common.greet",
        new Object[]{"Lan"},
        Locale.forLanguageTag("vi-VN"));
~~~

nó không đọc `app.mode`, không tự kiểm tra `@Profile("shared")` và không tự dùng active profile như một Locale. Bản dịch được chọn theo **message source hierarchy + Locale + code + fallback**.

Nếu ứng dụng muốn chọn nhiều MessageSource beans theo profile (ví dụ dev vs prod), có thể dùng `@Profile` hoặc `@Conditional` khi đăng ký bean definitions, nhưng đó là **quyết định khởi tạo source**, không phải cơ chế Spring tự map profile thành translation locale.

### @Value và message codes

~~~java
@Value("${app.mode}")
private String mode;
~~~

Đây là property placeholder, không phải localized message lookup. Để lấy bản dịch, inject `MessageSource` rồi gọi `getMessage(...)` hoặc dùng `MessageSourceAware` trong tình huống framework integration thích hợp.

### @PropertySource và message bundles

`@PropertySource("classpath:config.properties")` thêm một `PropertySource` vào Environment. Nó **không tự định nghĩa bean `messageSource`** và không biến mọi key trong file đó thành localized message. Dù đều có đuôi `.properties`, vai trò và lookup contract khác nhau.

## 17. Không cùng lifecycle và không cùng refresh semantics

Hai cơ chế có cache và update rules độc lập:

| Vấn đề | MessageSource hierarchy | Environment merge |
| --- | --- | --- |
| Lúc nối parent | Khi ApplicationContext init messageSource trong refresh | Trong quá trình `setParent`/`merge` |
| Cách tìm dữ liệu | Theo message code, Locale, bundles, parent MessageSource | Theo key và thứ tự PropertySources trong child |
| Override | Child source resolve code trước parent source | Source đứng trước thắng key; source trùng tên có quy tắc merge |
| Cache | Có thể cache ResourceBundle/MessageFormat/properties | API Environment là resolver; bản thân sources có thể mutable |
| Update parent sau khi child chạy | Depends on source/caching/lifecycle | Không tự merge lại tất cả thay đổi parent |
| Localized fallback | Có `Locale` variants, root bundle, parent MessageSource | Không hỗ trợ Locale tự động |
| Lifecycle | Source bean local thuộc từng context | Environment thuộc từng context, có thể chia sẻ references đến PropertySource objects |

### Nếu cần cập nhật bản dịch lúc runtime

Chọn `ReloadableResourceBundleMessageSource` với file resources **nằm ngoài classpath/JAR** và cấu hình cache interval; không hứa hot reload một resource nằm bên trong executable JAR. Khi mỗi context có MessageSource riêng, child/parent có thể có những cache riêng, và fallback có thể trộn phiên bản bản dịch nếu rollout không đồng bộ.

### Nếu cần cập nhật cấu hình lúc runtime

Việc sửa MapPropertySource hay thay Environment source không có nghĩa `@Value` đã inject vào singleton sẽ tự chạy lại; các `@Profile`/`@Conditional` đã được đánh giá cũng không tự re-evaluate. Thiết kế dynamic configuration phải quản lý atomic snapshot, validation, thread safety và reconfiguration callbacks riêng.

### Đóng context

Đóng child trước parent. Một child MessageSource có thể fallback sang parent source trong thời gian child còn sống; nếu parent đã đóng, behavior truy cập bean/resources của parent có thể không còn hợp lệ. Spring không tự tạo một global owner quản lý đóng mọi child contexts do app tạo riêng.

## 18. Thứ tự lifecycle: initialize parent, then child

Một chuỗi khởi động khuyến nghị:

~~~text
1. Tạo parent context (chưa refresh)
2. Nạp parent settings/property sources/profiles
3. Register parent config, refresh parent
4. Tạo child context (chưa refresh)
5. Nạp child-specific property sources
6. child.setParent(parent) để merge Environment
7. Register child config, refresh child
8. Chạy business flow; MessageSource và Environment đã sẵn sàng
9. Đóng child
10. Đóng parent
~~~

Tại sao `setParent` phải trước `refresh`? Vì refresh bao gồm nạp BeanDefinitions, xử lý `@Profile`, khởi tạo property resolvers, xử lý `BeanFactoryPostProcessor`, khởi tạo `MessageSource` và singleton beans. Nếu thiết lập parent sau khi child đã hoạt động, các kết quả lúc bootstrap có thể không được re-run; đừng sử dụng pattern ấy.

### @PropertySource và annotation scanning

Nếu child config dùng `@PropertySource`, thứ tự property source registration có thể ảnh hưởng precedence. Không mặc định `@PropertySource` luôn thắng một `MapPropertySource.addFirst(...)`, và không giả định mọi property source names là duy nhất. Có thể inspect:

~~~java
child.getEnvironment().getPropertySources()
        .forEach(ps -> System.out.println(ps.getName()));
~~~

Đây là diagnostic snippet; chỉ nên dùng trong test/debug, tránh dump values chứa secrets.

## 19. Common mistakes và cách khắc phục

| Triệu chứng | Nguyên nhân | Hướng sửa |
| --- | --- | --- |
| Child không đọc được parent message | Chưa `setParent` trước refresh, không có parent source, code sai | Kiểm tra wiring/tên bean/bundles |
| Child có bean messageSource nhưng parent fallback không có | Custom source không hỗ trợ `HierarchicalMessageSource` | Dùng source hỗ trợ hierarchy hoặc tự delegate rõ |
| Parent lookup lấy nhầm message của child | Nhầm chiều visibility | Parent không tự hỏi child; kiểm tra chính code/bean đang gọi |
| Child không khai báo messageSource nhưng vẫn nhận bản dịch cha | `DelegatingMessageSource` | Đây là behavior hierarchy được hỗ trợ |
| Code trùng nhưng child chưa hiện bản dịch parent theo locale | Child root bundle đã resolve code | Hiểu local root bundle có thể che parent locale-specific |
| Key missing bị in nguyên code | `useCodeAsDefaultMessage` hoặc UI fallback | Dùng strict tests/missing-key policy |
| “Không có message bean” nhưng @Value vẫn làm việc | Environment độc lập MessageSource | Hai subsystems khác nhau |
| Child `app.shared` là null sau merge | Parent/child PropertySource trùng tên hoặc source thêm sau merge | Đặt source names riêng; chuẩn bị parent trước setParent |
| Child `app.mode` không ưu tiên như dự định | Thứ tự property sources khác assumptions | Dùng addFirst/inspect chain |
| Parent bị đổi config vì child override | Hiểu nhầm Environment mutation | Key override của child không sửa parent |
| @Profile bean child không được tạo | Active profiles được cấu hình/merge quá muộn | Thiết lập trước refresh |
| Một key translated nhưng property getProperty null | Messages không tự thành environment property | Dùng MessageSource.getMessage |
| Parent BFPP không resolve @Value ở child | BFPP scoped per container | Cấu hình resolver/PSPC tại đúng child |
| Child singleton giữ property cũ sau update | @Value snapshot lúc bean tạo | Thiết kế refresh/rebind explicit |
| Bản dịch đổi file nhưng child vẫn thấy cũ | ResourceBundle cache/JAR, source cache khác | Chọn reloadable external files, đo interval |
| Parent MessageSource và child MessageSource khác Locale policy | fallbackToSystemLocale/defaultLocale không đồng bộ | Chuẩn hóa config và test locale matrix |
| Child đóng rồi parent bean vẫn còn | Ownership là của parent | Đây là lifecycle được kỳ vọng |
| Parent đóng khi child còn sống | Child phụ thuộc parent resources | Close child trước parent |
| Logs sự kiện refresh lặp | Child events propagate lên parent | Phân biệt event source context |
| Spring Boot config khác plain Spring | Boot có auto-configuration/sources precedence riêng | Kiểm tra runtime thực, không copy giả định |
| Text tiếng Việt mất dấu | Encoding bundle sai hoặc build filtering | UTF-8, kiểm thử raw bytes và getMessage |
| MessageFormat bỏ dấu nháy | Apostrophe là quote marker | Escape theo MessageFormat grammar |

## 20. Test matrix trước khi đưa vào production

| Kiểm thử | Thiết lập | Kỳ vọng |
| --- | --- | --- |
| Child override message | Cả hai có `common.greet` | Child gọi trả bản child; parent vẫn trả bản parent |
| Parent-only message | Chỉ parent có `parent.only` | Child fallback được parent |
| Child-only message | Chỉ child có `child.only` | Parent không tìm thấy |
| Missing all messages | `unknown.code` vắng ở cả hai | Default hoặc exception theo overload |
| Vi locale | `vi-VN`, có `messages_vi.properties` | Chuỗi tiếng Việt UTF-8 |
| Unsupported locale | Không có file riêng, có root | Theo root/default policy |
| Missing locale-specific child code | Child root có/không có code | Check precedence với parent source |
| Child overriding config | `app.mode` ở hai MapPropertySources khác tên | Child mode thắng |
| Parent config inherited | `app.shared` chỉ parent có | Child đọc được |
| Parent cannot see child config | `app.local` chỉ child có | Parent getProperty là null/default |
| Parent source-name collision | Source trùng tên child | Source parent cùng tên bị bỏ khi merge |
| Profiles merged | Parent active profile `shared` | Child `@Profile("shared")` được chọn |
| Profile added after merge | Parent thay profiles sau `setParent` | Không mặc định child cập nhật |
| @Value singleton | Thay property sau bean creation | Bean không tự re-inject |
| Child without MessageSource | Không khai báo source local | Child có thể delegate parent |
| Custom MessageSource | Không implement HierarchicalMessageSource | Test/delegate parent explicit |
| Parent closed too early | Đóng root trước module | Có thể làm dependency/fallback bị ảnh hưởng |
| Multiple child contexts | Shared parent, hai child bundles | Each child overrides independently |

### Kiểm thử chính xác thứ tự sources

Một integration test nên assert cả source name precedence lẫn effective values, không chỉ việc một key tồn tại. Khi bug xảy ra, log tên các PropertySources theo thứ tự (không log secret values), active profiles và basename của MessageSource.

### Kiểm thử i18n theo code và locale

Mỗi message code quan trọng nên test ít nhất một locale có dịch đầy đủ, một locale thiếu key hoặc file riêng để thử fallback, và một missing code để thử default/exception. Đặt expected strings exact Unicode để bắt lỗi encoding và hierarchy precedence.

## 21. Gợi ý kiến trúc cho hệ thống nhiều module

### Parent nên chứa gì?

- Các services/stateless infrastructure được chia sẻ.
- Config chung/allowlisted environment sources.
- MessageSource cho common codes như `common.save`, `common.cancel`, `validation.required`.
- Các policy chung cần áp dụng xuyên modules khi phù hợp với ownership của parent.

### Child nên chứa gì?

- Controllers/adapters của feature.
- Các module-specific services chỉ dùng trong module.
- MessageSource cho localized feature codes như `checkout.pay` hoặc `catalog.search`.
- PropertySources có tên riêng và giá trị override được tài liệu hóa.

### Tránh shadowing không chủ đích

Dùng namespaces và naming convention:

~~~text
Message codes:
  common.save
  common.cancel
  checkout.title
  checkout.payment.failed

Environment keys:
  app.shared.timeout
  checkout.mode
  checkout.max-items

PropertySource names:
  rootSharedSettings
  checkoutLocalSettings
~~~

Một key `common.save` cố ý override từ child thì phải có test; tránh dùng cùng tên `error` hoặc `title` trong nhiều bundles mà không rõ precedence.

### Khi nào không dùng phân cấp?

Nếu toàn bộ ứng dụng chỉ cần một message source với nhiều basenames và một Environment duy nhất, tạo nhiều ApplicationContexts có thể là sự phức tạp không cần thiết. Dùng phân cấp khi thực sự có module boundaries, scope của BeanFactory, lifecycle hoặc independent configuration needs.

**Không nên tạo ApplicationContext mới cho từng request/Locale** chỉ để bản dịch khác nhau. Một MessageSource singleton có thể resolve nhiều Locales trong cùng context; request scopes/explicit Locale arguments giải quyết nhu cầu này tốt hơn.

## 22. Những câu hỏi tự kiểm tra

**Câu 1.** Parent có `messageSource`; child không có bean `messageSource`. Child có thể lấy code chỉ có ở parent không?

**Đáp án:** Có, trong ApplicationContext hierarchy, child có thể dùng `DelegatingMessageSource` để delegate sang parent.

**Câu 2.** Parent và child đều có message code `common.greet`. Child gọi getMessage sẽ trả value nào?

**Đáp án:** Local child source được thử trước. Nếu child resolve được code theo locale/root policy, nó trả value child; không tự ưu tiên parent.

**Câu 3.** Nếu child không có code `parent.only` và parent có code ấy?

**Đáp án:** Với source child hỗ trợ `HierarchicalMessageSource` và parent wiring đúng, child fallback sang parent.

**Câu 4.** Parent gọi getMessage có thể tự nhận key chỉ tồn tại ở child không?

**Đáp án:** Không. MessageSource hierarchy mặc định không duyệt ngược xuống children.

**Câu 5.** Child có `messageSource` local thì parent messageSource có bị xóa không?

**Đáp án:** Không. Hai beans thuộc hai contexts riêng. Child có thể override code qua hierarchy mà không sửa parent.

**Câu 6.** `Environment.getProperty("common.greet")` có mặc nhiên đọc `messages_vi.properties` không?

**Đáp án:** Không. MessageSource bundles khác với Environment PropertySources.

**Câu 7.** `ConfigurableEnvironment.merge` có duyệt parent Environment mỗi lần child getProperty không?

**Đáp án:** Không. Merge append property sources/profiles vào child tại thời điểm gọi; đây không phải live delegated lookup theo cách MessageSource fallback.

**Câu 8.** Nếu hai source đều tên `sharedConfig`, merge có deep-merge từng key không?

**Đáp án:** Không. Source child cùng tên được giữ và parent source cùng tên bị bỏ qua.

**Câu 9.** Child `app.mode=CHILD` có làm parent `app.mode` đổi thành CHILD không?

**Đáp án:** Không. Precedence local không phải mutation parent.

**Câu 10.** Parent bật profile `shared` sau khi child setParent. Child có chắc nhìn thấy profile mới không?

**Đáp án:** Không. Merge không tự phản ánh mọi thay đổi parent sau đó. Thiết lập profiles trước hierarchy wiring/refresh.

**Câu 11.** `@Value("${app.mode}")` đã inject trong singleton có tự cập nhật nếu source thay đổi không?

**Đáp án:** Không. Injection xảy ra theo bean lifecycle; dynamic refresh cần một cơ chế riêng.

**Câu 12.** `@Profile("shared")` có tương đương `Locale.forLanguageTag("vi-VN")` không?

**Đáp án:** Không. Profile chọn bean definitions theo environment; Locale chọn thông điệp theo ngôn ngữ.

**Câu 13.** Child root bundle có `common.greet` tiếng Anh, parent Vietnamese bundle có cùng key tiếng Việt. Child có chắc lấy parent Vietnamese không?

**Đáp án:** Không. Child root bundle có thể resolve code trước parent fallback. Nếu yêu cầu ngôn ngữ ưu tiên khác, cần thiết kế precedence và kiểm thử.

**Câu 14.** BFPP parent có tự apply vào bean definitions của child không?

**Đáp án:** Không. BeanFactoryPostProcessors xử lý container nơi chúng được đăng ký.

**Câu 15.** Đóng child có đóng parent không?

**Đáp án:** Không tự động theo cách ứng dụng tự dựng contexts; quản lý lifecycle tường minh và đóng child trước parent.

## 23. Checklist triển khai

1. Có xác định rõ context nào là parent và child?
2. `setParent(parent)` được gọi **trước child.refresh()`?
3. Parent Environment sources/profiles được thiết lập **trước khi merge**?
4. Child Environment source names có tránh collision vô tình với parent?
5. PropertySource precedence và kết quả effective keys đã được kiểm tra?
6. Bean `messageSource` có tên đúng trong context cần dùng?
7. Child source có hỗ trợ parent linkage qua `HierarchicalMessageSource`?
8. Đã test child local code thắng parent code theo Locale?
9. Đã test child fallback khi code chỉ có ở parent?
10. Đã test parent không nhìn thấy child-only message code?
11. Root bundles và locale-specific bundles được đặt đúng basename?
12. Message files được lưu UTF-8, `setDefaultEncoding("UTF-8")`?
13. `setFallbackToSystemLocale`/default locale policy rõ?
14. Có test missing-code default/NoSuchMessageException?
15. `useCodeAsDefaultMessage` có được tránh/làm rõ trong hierarchy?
16. Không dùng `Environment.getProperty` thay thế message lookup cho i18n?
17. Không dùng MessageSource thay cho typed configuration/`@Value`?
18. Nếu child dùng `@Value`, placeholder processing infrastructure đã đúng context?
19. Parent BFPP/BPP không bị giả định tự áp dụng xuống child?
20. Active/default profiles đã được kiểm tra sau merge?
21. Không kỳ vọng thay Environment sau refresh tự rebind singletons?
22. Nếu cần hot reload messages, sources ngoài JAR và cache policy phù hợp?
23. Child được đóng trước parent để bảo toàn dependency lifecycle?
24. Có integration tests mô phỏng đúng root/child và built artifact không?
25. Có ghi nhận context ownership/source names/locale trong chẩn đoán mà không in secret values?

## 24. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — ApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationContext.html)
- [Spring Framework 6.2 — AbstractApplicationContext (initMessageSource, setParent)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html)
- [Spring Framework 6.2 — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/MessageSource.html)
- [Spring Framework 6.2 — HierarchicalMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/HierarchicalMessageSource.html)
- [Spring Framework 6.2 — DelegatingMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/DelegatingMessageSource.html)
- [Spring Framework 6.2 — AbstractMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/AbstractMessageSource.html)
- [Spring Framework 6.2 — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html)
- [Spring Framework 6.2 — ConfigurableEnvironment.merge](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/ConfigurableEnvironment.html)
- [Spring Framework 6.2 — MapPropertySource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/env/MapPropertySource.html)
- [Spring Framework Reference — ApplicationContext Capabilities](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Framework Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/core/beans/environment.html)
- [Spring Framework Reference — @Profile](https://docs.spring.io/spring-framework/reference/core/beans/environment.html#beans-definition-profiles-java)
- [Spring Framework Reference — BeanFactoryPostProcessor](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy-008">Phân biệt phân cấp context với kế thừa định nghĩa bean</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phân cấp ApplicationContext với kế thừa định nghĩa bean trong Spring Framework

## 1. Hai chữ “cha – con” nhưng hai cơ chế khác nhau

Trong Spring Core Container, cụm từ **parent/child** xuất hiện ở **hai tầng cấu trúc hoàn toàn khác nhau**. Rất nhiều lỗi thiết kế bắt nguồn từ việc tưởng chúng là một.

**Cơ chế A — Phân cấp context (*ApplicationContext hierarchy*):** có **hai hoặc nhiều container thật**, mỗi container có bean factory, registry, singleton cache và lifecycle riêng. Context con biết context cha. Khi bean không nằm trong con, con có thể tìm lên cha. Cha không tự tìm bean ở con.

**Cơ chế B — Kế thừa bean definition (*bean definition inheritance*):** một **bean definition** chỉ định parent definition để **kế thừa metadata cấu hình** như bean class, property values, constructor arguments và scope. Spring **gộp metadata** để tạo instance cho child definition; đây là một dạng **template cấu hình**, không phải quan hệ giữa hai object Java, cũng không tự đòi hỏi hai ApplicationContexts.

Tóm tắt một câu:

> **Context hierarchy quyết định bean lookup và container ownership; bean definition inheritance quyết định recipe tạo bean nào kế thừa recipe khác.**

Bài này tập trung phân biệt chính xác hai cơ chế, giới hạn và điểm giao nhau của chúng, đưa ra hai sơ đồ, bảng API, ví dụ XML và một chương trình Java 17 hoàn chỉnh với Spring Framework 6.2.18.

## 2. Hai sơ đồ so sánh

### A. Hai ApplicationContexts

```text
        Parent ApplicationContext
           BeanFactory: parent
         +--------------------+
         | parentOnly         |
         | shared = "parent"  |
         +--------------------+
                   ^
                   | getBean fallback
                   |
         Child ApplicationContext
           BeanFactory: child
         +--------------------+
         | childOnly          |
         | shared = "child"   |
         +--------------------+

child.getBean("parentOnly")  --> parent bean
child.getBean("shared")      --> child bean (che tên)
parent.getBean("childOnly")  --> không tìm thấy
```

Điểm quan trọng: **hai bean factories**, mỗi factory có bean definitions và singleton cache riêng. Không có bước “copy tất cả definitions của parent vào child” chỉ vì `setParent` được gọi.

### B. Một BeanFactory với parent và child definitions

```text
        One BeanFactory / One ApplicationContext
         +--------------------------------------+
         | bean definition: template            |
         |   abstract=true                      |
         |   beanClass=ServerOptions             |
         |   endpoint="default"                 |
         |   timeout=3000                       |
         |                |                     |
         |                | getParentName()     |
         |                v                     |
         | bean definition: eastOptions         |
         |   parentName="template"              |
         |   endpoint="east"                    |
         |                                      |
         | merged definition: eastOptions       |
         |   beanClass=ServerOptions            |
         |   endpoint="east"                    |
         |   timeout=3000                       |
         +--------------------------------------+
```

Điểm quan trọng: **định nghĩa con mới kế thừa metadata của định nghĩa cha**; một class `ServerOptions` có thể được sử dụng để tạo nhiều bean instances khác nhau. Không có ý nghĩa “`eastOptions` là một child ApplicationContext”.

## 3. Bảng đối chiếu căn bản

| Tiêu chí | Context hierarchy | Bean definition inheritance |
| --- | --- | --- |
| Quan hệ giữa | Hai/multiple `ApplicationContext` hoặc BeanFactory | Hai `BeanDefinition` recipes |
| Thiết lập bằng | `child.setParent(parent)`, constructor nhận parent | `parent="template"` trong XML hoặc `ChildBeanDefinition("template")` |
| Tầng ảnh hưởng | Bean lookup, registry visibility, lifecycle, environment/message/event integration | Merged metadata dùng để tạo bean |
| Có tạo container mới? | **Có** | **Không bắt buộc**; thường ngay trong một factory |
| “Con nhìn cha” là | `child.getBean` có thể delegate parent | Child definition lấy bean class/property/constructor data từ parent definition |
| “Cha nhìn con” là | Parent không tự lookup descendants | Parent definition không tự nhận property overrides từ child definition |
| Trùng tên | Local bean ở child **shadow** parent bean | Child definition có **tên riêng**, trỏ `parentName` tới tên template |
| Có thể dùng parent bean instance? | Có, child lookup chính parent singleton | Không bắt buộc tạo instance của parent template |
| Abstract parent | Context cha vẫn có thể là active context thông thường | `abstract=true` cho template không được instantiate |
| API tìm kiếm | `getBean`, `containsBean`, `BeanFactoryUtils` | `BeanDefinition.getParentName`, `ChildBeanDefinition`, merged definitions |
| Use case | Root/servlet contexts, module boundaries | Template hóa bean metadata, XML legacy configuration |

Điểm liên hệ duy nhất cần xử lý sâu: bean definition inheritance **có thể được cấu hình tường minh cùng với một BeanFactory hierarchy**, bao gồm trường hợp tra parent definition từ ancestor. Nhưng không bao giờ suy rằng thiết lập parent context **tự làm** bean definitions kế thừa nhau.

## 4. Context hierarchy có **hai factory owners**

```java
childContext.setParent(parentContext);
```

Đây là snippet cần hai context hợp lệ và thực hiện trước khi child refresh. Child có thể lookup bean ở parent nếu không có local bean cùng tên; parent không đi xuống child. Child và parent vẫn sở hữu bean definitions, singleton caches và lifecycle riêng, kể cả khi child dùng chung reference đến parent singleton. Không có bước tự động **copy hoặc merge bean definitions** chỉ nhờ `setParent`.

## 5. Shadowing không tạo quan hệ metadata parent–child

```text
Parent context: client -> Client("parent")
Child context : client -> Client("child")

child.getBean("client")  -> child Client
parent.getBean("client") -> parent Client
```

Hai bean cùng tên là **hai local definitions của hai factories**, không phải một child definition lấy metadata từ parent definition. Nếu types khác nhau, typed lookup bằng tên có thể ném `BeanNotOfRequiredTypeException`; Spring không sửa parent bean để phù hợp child.

## 6. Local listing và lookup qua hierarchy không giống nhau

`child.getBean("parentOnly")` hoặc `containsBean("parentOnly")` có thể đi lên tổ tiên khi child không có local. Nhưng `containsLocalBean`, `containsBeanDefinition`, `getBeanDefinitionNames` và `getBeansOfType` trên child xét phạm vi local. `BeanFactoryUtils.beansOfTypeIncludingAncestors(...)` có thể liệt kê cả ancestors với quy tắc name shadowing. Chênh lệch kết quả giữa các API không chứng minh bean definition inheritance đã xảy ra.

## 7. Injection từ child vào parent bean vẫn chỉ là **dùng reference**

```java
@Bean
ChildService childService(ParentService parentService) {
    return new ChildService(parentService);
}
```

Đây là factory method minh họa trong child config, với hai kiểu `ChildService` và `ParentService` được định nghĩa sẵn. Nếu `ParentService` chỉ có ở parent và candidate resolution phù hợp, child có thể inject **chính instance parent-managed**, không cần và không tự tạo `ChildBeanDefinition(parentName)`. Parent bean không tự inject ngược child bean; phân cấp quy định lookup direction, còn metadata inheritance sẽ được giải thích từ mục 8.

## 8. Kế thừa bean definition là kế thừa “công thức”, không phải instance

`BeanDefinition` chứa metadata cho cách Spring tạo một bean:

- Bean class hoặc factory method.
- Constructor argument values.
- Property values.
- Scope.
- Lifecycle callbacks: init/destroy method.
- Method overrides/lookup method injection metadata.
- Một số attributes và container-specific settings.

Spring cho phép child definition chỉ định **`parentName`**. Container sẽ tạo **merged bean definition** áp dụng thông tin từ parent definition và child definition. Định nghĩa con có thể override property hoặc bổ sung config.

Kế thừa này có thể thực hiện **chỉ trong một GenericApplicationContext**, hoàn toàn không cần gọi `setParent` trên context nào.

Hai bean definitions `serviceA` và `serviceB` kế thừa cùng template vẫn là **hai definitions có tên riêng**, có thể tạo ra **hai bean instances độc lập** nếu scope/default construction như vậy. Đừng suy rằng “child definition luôn trả reference của parent bean”.

## 9. XML bean definition inheritance

Cú pháp XML truyền thống:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="baseOptions"
          class="com.example.ServerOptions"
          abstract="true">
        <property name="endpoint" value="https://default.invalid"/>
        <property name="timeoutMillis" value="3000"/>
    </bean>

    <bean id="eastOptions" parent="baseOptions">
        <property name="endpoint" value="https://east.invalid"/>
    </bean>

    <bean id="westOptions" parent="baseOptions">
        <property name="endpoint" value="https://west.invalid"/>
        <property name="timeoutMillis" value="1000"/>
    </bean>
</beans>
```

Đây là **file XML minh họa đầy đủ về metadata**; class `com.example.ServerOptions` cần tồn tại, có constructor và setters tương thích.

Ở đây:

- `baseOptions` là **bean definition cha** chỉ phục vụ template.
- `eastOptions` và `westOptions` là **bean definitions con**.
- Không có bất kỳ yêu cầu tạo hai ApplicationContexts.
- `eastOptions` được merge: endpoint east, timeout 3000.
- `westOptions` được merge: endpoint west, timeout 1000.
- `abstract="true"` làm base không được tạo như normal bean.

Dù có chữ `parent="baseOptions"`, XML đó **không** tương đương `childApplicationContext.setParent(parentApplicationContext)`.

### Bean definition cha không nhất thiết phải abstract

Spring cho phép một parent definition có thể là concrete. Khi đó `baseOptions` cũng có thể được tạo và sử dụng như bean thực tế. Nhưng nếu mục tiêu của nó **chỉ là template**, nên đặt `abstract=true` để tránh vô tình instantiate parent template.

Đặc biệt nếu parent definition không có class/factory đầy đủ, việc đánh dấu abstract là cần thiết: một bean definition thiếu dữ liệu tạo bean không nên bị pre-instantiated như singleton trong `ApplicationContext.refresh()`.

## 10. ChildBeanDefinition API trong Java

Spring có `org.springframework.beans.factory.support.ChildBeanDefinition`:

```java
ChildBeanDefinition east = new ChildBeanDefinition("baseOptions");

east.getPropertyValues().add(
        "endpoint", "https://east.invalid");

context.registerBeanDefinition("eastOptions", east);
```

Đây là **mảnh mã minh họa** với `context` là `GenericApplicationContext` hoặc `BeanDefinitionRegistry` phù hợp và parent definition `baseOptions` đã đăng ký/resolve được.

`ChildBeanDefinition("baseOptions")` nghĩa là metadata `eastOptions` phải lấy từ **definition có name `baseOptions`** khi merge. API `getParentName()` trả tên được tham chiếu, không phải một reference trực tiếp đến Java object instance.

`RootBeanDefinition` và `GenericBeanDefinition` là các implementation khác. `GenericBeanDefinition` cũng có thể đặt `parentName`; không nhất thiết mọi child definition đều phải instantiate class `ChildBeanDefinition`. Nhưng `ChildBeanDefinition` truyền đạt rõ nhu cầu kế thừa.

### Parent bean không nhất thiết được instantiate

Nếu `baseOptions` là `abstract=true`, child vẫn có thể kế thừa property values từ metadata của parent. Container không cần tạo instance `baseOptions` trước, vì việc merge là trên **định nghĩa**, không phải copy field từ object parent.

Không viết code kiểu:

```java
ServerOptions parentBean = context.getBean(
        "baseOptions", ServerOptions.class);
```

cho abstract template; đây là **anti-example**. Việc gọi `getBean` cho abstract bean definition không phải một cách đọc template hợp lệ.

## 11. Metadata nào được kế thừa?

Theo tài liệu Spring về Bean Definition Inheritance:

- Child có thể kế thừa **bean class** nếu không tự chỉ định.
- Có thể kế thừa **constructor argument values**.
- Có thể kế thừa **property values** và override/thêm property.
- Có thể kế thừa **method overrides**.
- Có thể kế thừa **scope**, trừ khi child tự khai báo scope khác.
- Child có thể override **init method**, **destroy method**, **static factory method** theo cấu hình.
- Các metadata còn lại có những quy tắc riêng; không được suy rằng **mọi field của BeanDefinition** đều luôn kế thừa.

Spring Reference nêu một số settings được lấy từ child definition, chẳng hạn `depends-on`, autowire mode, dependency check, singleton và lazy init theo bản tài liệu. Cần đọc tài liệu đúng **version** và hiểu defaults của API Java khi tự xây definitions.

Bảng ví dụ:

| Thuộc tính | Parent template | Child east | Merged |
| --- | --- | --- | --- |
| beanClass | `ServerOptions` | không khai báo | `ServerOptions` |
| endpoint | `https://default.invalid` | `https://east.invalid` | `https://east.invalid` |
| timeoutMillis | `3000` | không khai báo | `3000` |
| scope | singleton | không khai báo cụ thể | theo merge/default của Spring |
| init method | `initialize` | `start` | `start` nếu child override |

**Không nhầm với Java class inheritance:** class `ServerOptions` không bắt buộc `extends` một class khác. Một Java object tạo từ child definition không phải subclass của object parent; chỉ **metadata** được kế thừa và merge.

## 12. Bean definition template và Java object inheritance

Ba trường hợp thường bị lẫn:

### Java inheritance

```java
class BaseClient {
    String name() {
        return "base";
    }
}

class SpecialClient extends BaseClient {
    @Override
    String name() {
        return "special";
    }
}
```

Đây là **quan hệ class extends class** trong Java, được compiler/JVM thực thi, không cần Spring. Nó quyết định subtyping, method override, inherited Java fields/methods.

### Bean definition inheritance

```text
template BeanDefinition
      |
      | parentName
      v
special BeanDefinition
```

Chỉ là **metadata**. Child definition có thể dùng cùng bean class với parent hoặc class khác tương thích cấu hình. Nó không khiến các Java classes tự có quan hệ `extends`.

### Context hierarchy

```text
Parent ApplicationContext
         ^
         | setParent
Child ApplicationContext
```

Đây là **container relationship**. Nó không yêu cầu Java class inheritance hoặc BeanDefinition `parentName`.

Một ứng dụng có thể dùng cả ba loại đồng thời, nhưng chúng không thay thế được nhau.

## 13. Abstract bean definition có nghĩa gì?

`BeanDefinition.setAbstract(true)` mô tả một **definition chỉ dùng làm template** và không nên được instantiate thành bean. Nó **không** đồng nghĩa:

- Java `abstract class`.
- Abstract method trong Java.
- Parent `ApplicationContext`.
- Bean thuộc parent context.
- Bean được để `@Lazy` và sẽ được khởi tạo sau này.

Phân biệt:

| Cấu hình | Ý nghĩa |
| --- | --- |
| `abstract=true` trên BeanDefinition | **Không tạo instance** của definition; chỉ dùng làm template |
| `lazy-init=true` trên bean concrete | Cho phép tạo instance khi được yêu cầu, không pre-instantiate bình thường |
| Java `abstract class` | Không thể trực tiếp `new` class; cần concrete subclass/factory |
| Parent ApplicationContext | Container cấp trên, có thể đang active và chứa normal singleton beans |

Một parent template có `abstract=true` có thể chứa bean class Java **concrete**. Mục đích là tái sử dụng cấu hình, không phải vì Java type không thể instantiate.

Nếu quên `abstract=true` với một template concrete và context pre-instantiates non-lazy singletons, Spring có thể vô tình tạo parent bean không mong đợi. Nếu template thiếu class, có thể gây lỗi khi context cố tạo một bean chưa hoàn chỉnh.

## 14. Scope và lifecycle trong hai cơ chế

### Context hierarchy

Parent sở hữu singleton `sharedClient`. Child lookup parent nhận **cùng instance** (trừ trường hợp shadow/local khác). Destroy callbacks của instance đó do **parent** quản lý. Đóng child không tự đồng nghĩa đóng parent.

Hai contexts có thể mỗi bên có bean `sharedClient` cùng tên. Đây là **hai definitions khác nhau**, thường cho ra **hai instances khác nhau**. Parent và child có singleton caches riêng.

### Bean definition inheritance

Một context có `baseOptions abstract` và hai child definitions `eastOptions`, `westOptions`. Parent template không có instance. Mỗi concrete definition có thể tạo singleton riêng theo scope. Nếu cả hai cùng kế thừa `scope=prototype`, mỗi `getBean` có thể tạo instance mới, tùy merge configuration.

Lifecycle callbacks `init`/`destroy` được áp dụng lên các bean **được tạo thực**, dựa trên merged definition. “Child definition” không có nghĩa instance đó tự phụ thuộc vào một parent **instance**.

### Quan trọng với property references

Child definition có property kiểu `RuntimeBeanReference` đến một bean khác thì Spring vẫn phải resolve reference đó theo BeanFactory lookup. Việc kế thừa **giá trị metadata là một reference** khác với kế thừa **chính bean instance**. Có thể có một merged definition kế thừa reference rồi khi tạo child bean nó mới tra bean mục tiêu.

## 15. Java configuration @Bean không đồng nghĩa kế thừa definition

Java config:

```java
@Configuration
class BaseConfiguration {
    @Bean
    PaymentClient paymentClient() {
        return new PaymentClient();
    }
}

@Configuration
class FeatureConfiguration {
    @Bean
    FeatureService featureService(PaymentClient paymentClient) {
        return new FeatureService(paymentClient);
    }
}
```

Đây là **mảnh minh họa**; classes `PaymentClient`/`FeatureService` cần định nghĩa.

Nếu cả hai configs được đăng ký trong **cùng context**, các `@Bean` definitions tồn tại chung. Nếu `FeatureConfiguration` ở child còn `BaseConfiguration` ở parent, child có thể autowire parent bean. **Không trường hợp nào tự làm `featureService` “kế thừa bean definition” của `paymentClient`.**

Khi dùng `@Import(BaseConfiguration.class)`, Spring đưa definitions vào **cùng container**, chứ không tạo parent ApplicationContext.

Khi một `@Configuration` class **extends một Java class cấu hình**, đó lại là một quan hệ class inheritance; những `@Bean` methods và override behavior phụ thuộc annotation processing của Spring, **không** đồng nghĩa với metadata `ChildBeanDefinition("...")`.

## 16. Một chương trình Java 17 kiểm chứng cả hai cơ chế

Phần này chứa **một chương trình chạy được** với Spring Framework **6.2.18**. Nó lần lượt dựng hai thí nghiệm độc lập:

**Thí nghiệm A — hierarchy:** parent và child là **hai AnnotationConfigApplicationContext**. Parent định nghĩa `parentService` và `channel`; child định nghĩa `childOnly`, cũng định nghĩa `channel` để shadow parent, và có `childService` inject `ParentService` từ parent.

**Thí nghiệm B — bean definition inheritance:** một **GenericApplicationContext** với `settingsTemplate` là `RootBeanDefinition` abstract và `eastSettings`/`copySettings` là hai `ChildBeanDefinition`. Không có `setParent`; không có context thứ hai. Child definitions kế thừa bean class và timeout; một child override endpoint.

Output được thiết kế cố định, không có timestamp, random values, thread scheduling hoặc iteration order không ổn định.

### 16.1. Cấu trúc

```text
spring-hierarchy-vs-definition-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/HierarchyVsDefinitionDemo.java
```

### 16.2. settings.gradle

```groovy
rootProject.name = 'spring-hierarchy-vs-definition-demo'
```

### 16.3. build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.18'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'HierarchyVsDefinitionDemo'
}
```

### 16.4. src/main/java/HierarchyVsDefinitionDemo.java

```java
import java.util.Map;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

public class HierarchyVsDefinitionDemo {

    // --- Thí nghiệm A: các beans của hai containers ---

    public record ParentService(String origin) {
    }

    public record ChildOnly(String origin) {
    }

    public record Channel(String origin) {
    }

    public record ChildService(ParentService parentService) {
    }

    @Configuration(proxyBeanMethods = false)
    public static class ParentConfig {

        @Bean("parentService")
        public ParentService parentService() {
            return new ParentService("parent");
        }

        @Bean("channel")
        public Channel channel() {
            return new Channel("parent");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ChildConfig {

        @Bean("childOnly")
        public ChildOnly childOnly() {
            return new ChildOnly("child");
        }

        @Bean("channel")
        public Channel childChannel() {
            return new Channel("child");
        }

        @Bean("childService")
        public ChildService childService(
                ParentService parentService) {
            return new ChildService(parentService);
        }
    }

    // --- Thí nghiệm B: cùng một factory, 3 definitions ---

    public static class ServerOptions {
        private String endpoint;
        private int timeoutMillis;
        private boolean enabled;

        public ServerOptions() {
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static void contextHierarchy() {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext()) {
            parent.register(ParentConfig.class);
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {
                child.setParent(parent);
                child.register(ChildConfig.class);
                child.refresh();

                ParentService fromParent =
                        parent.getBean(ParentService.class);
                ParentService fromChild =
                        child.getBean(ParentService.class);

                System.out.println("context.hasParent="
                        + (child.getParent() == parent));
                System.out.println("context.childSeesParent="
                        + child.containsBean("parentService"));
                System.out.println("context.childLocalParent="
                        + child.containsLocalBean("parentService"));
                System.out.println("context.sameParentInstance="
                        + (fromParent == fromChild));
                System.out.println("context.parentSeesChild="
                        + parent.containsBean("childOnly"));
                System.out.println("context.parentChannel="
                        + parent.getBean(Channel.class).origin());
                System.out.println("context.childChannel="
                        + child.getBean(Channel.class).origin());
                System.out.println("context.childInjectedParent="
                        + (child.getBean(ChildService.class)
                                .parentService() == fromParent));

                Map<String, ParentService> local =
                        child.getBeansOfType(ParentService.class);
                Map<String, ParentService> includingParents =
                        BeanFactoryUtils.beansOfTypeIncludingAncestors(
                                child, ParentService.class);

                System.out.println("context.localTypeCount="
                        + local.size());
                System.out.println("context.ancestorTypeCount="
                        + includingParents.size());

                try {
                    parent.getBean("childOnly");
                    throw new AssertionError(
                            "Parent unexpectedly found a child bean");
                } catch (NoSuchBeanDefinitionException expected) {
                    System.out.println(
                            "context.parentLookupChild=NOT_FOUND");
                }
            }
        }
    }

    private static void beanDefinitionInheritance() {
        try (GenericApplicationContext context =
                     new GenericApplicationContext()) {

            RootBeanDefinition template =
                    new RootBeanDefinition(ServerOptions.class);
            template.setAbstract(true);
            template.getPropertyValues()
                    .add("endpoint", "https://default.invalid");
            template.getPropertyValues()
                    .add("timeoutMillis", 3000);
            template.getPropertyValues()
                    .add("enabled", true);

            context.registerBeanDefinition(
                    "settingsTemplate", template);

            ChildBeanDefinition east =
                    new ChildBeanDefinition("settingsTemplate");
            east.getPropertyValues()
                    .add("endpoint", "https://east.invalid");
            context.registerBeanDefinition(
                    "eastSettings", east);

            ChildBeanDefinition copy =
                    new ChildBeanDefinition("settingsTemplate");
            context.registerBeanDefinition(
                    "copySettings", copy);

            context.refresh();

            ServerOptions eastBean =
                    context.getBean(
                            "eastSettings", ServerOptions.class);
            ServerOptions copyBean =
                    context.getBean(
                            "copySettings", ServerOptions.class);

            System.out.println("definition.contextParent="
                    + (context.getParent() == null));
            System.out.println("definition.templateAbstract="
                    + context.getBeanDefinition(
                            "settingsTemplate").isAbstract());
            System.out.println("definition.eastParentName="
                    + context.getBeanDefinition(
                            "eastSettings").getParentName());
            System.out.println("definition.eastEndpoint="
                    + eastBean.getEndpoint());
            System.out.println("definition.eastTimeout="
                    + eastBean.getTimeoutMillis());
            System.out.println("definition.eastEnabled="
                    + eastBean.isEnabled());
            System.out.println("definition.copyEndpoint="
                    + copyBean.getEndpoint());
            System.out.println("definition.copyTimeout="
                    + copyBean.getTimeoutMillis());
            System.out.println("definition.separateInstances="
                    + (eastBean != copyBean));
        }
    }

    public static void main(String[] args) {
        contextHierarchy();
        beanDefinitionInheritance();
    }
}
```

### 16.5. Lệnh chạy

Yêu cầu JDK 17+ và Gradle 8+; từ thư mục chứa build.gradle:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu dự án đã có Gradle Wrapper.
```

### 16.6. Những dòng System.out dự kiến

```text
context.hasParent=true
context.childSeesParent=true
context.childLocalParent=false
context.sameParentInstance=true
context.parentSeesChild=false
context.parentChannel=parent
context.childChannel=child
context.childInjectedParent=true
context.localTypeCount=0
context.ancestorTypeCount=1
context.parentLookupChild=NOT_FOUND
definition.contextParent=true
definition.templateAbstract=true
definition.eastParentName=settingsTemplate
definition.eastEndpoint=https://east.invalid
definition.eastTimeout=3000
definition.eastEnabled=true
definition.copyEndpoint=https://default.invalid
definition.copyTimeout=3000
definition.separateInstances=true
```

Đây là 20 dòng **chính chương trình in**; Spring/Gradle có thể có log khác tùy cấu hình môi trường.

### 16.7. Diễn giải thí nghiệm A

- `context.hasParent=true`: child đã thiết lập parent context.
- `context.childSeesParent=true`: child tìm được `parentService` qua parent factory.
- `context.childLocalParent=false`: bean đó không được đăng ký local ở child.
- `context.sameParentInstance=true`: parent singleton instance được child truy cập trực tiếp.
- `context.parentSeesChild=false` và `parentLookupChild=NOT_FOUND`: parent không nhìn xuống child.
- `context.parentChannel=parent` và `context.childChannel=child`: cả hai factories có bean name `channel` nhưng child ưu tiên local.
- `context.childInjectedParent=true`: bean `ChildService` trong child nhận dependency `ParentService` của parent qua injection.
- `context.localTypeCount=0`: liệt kê `getBeansOfType` trên child chỉ xét local factory.
- `context.ancestorTypeCount=1`: `BeanFactoryUtils` gộp cả ancestors.

Không có `ChildBeanDefinition` trong thí nghiệm A: những kết quả trên chỉ do **context hierarchy**, không hề do “kế thừa định nghĩa”.

### 16.8. Diễn giải thí nghiệm B

- `definition.contextParent=true`: `GenericApplicationContext` này **không có parent context**.
- `definition.templateAbstract=true`: `settingsTemplate` là abstract BeanDefinition, không instantiate.
- `definition.eastParentName=settingsTemplate`: child definition có property `parentName`.
- `definition.eastEndpoint=https://east.invalid`: child metadata override property `endpoint`.
- `definition.eastTimeout=3000` và `eastEnabled=true`: metadata kế thừa từ template.
- `definition.copyEndpoint=https://default.invalid` và `copyTimeout=3000`: child không override thì dùng values từ parent definition.
- `definition.separateInstances=true`: hai child definitions tạo **hai singleton beans riêng**. Không có chuyện “child instance chính là parent template instance”.

Không có `setParent(...)` giữa contexts trong thí nghiệm B. Chính điều này chứng minh **bean definition inheritance không cần context hierarchy**.

### 16.9. Vì sao mã chạy được?

- File name trùng public class `HierarchyVsDefinitionDemo`.
- `ServerOptions` là public static JavaBean có public no-arg constructor và các getters/setters; BeanFactory có thể instantiate và apply property values.
- `RootBeanDefinition(ServerOptions.class)` tồn tại trong Spring `spring-beans`.
- `ChildBeanDefinition("settingsTemplate")` kế thừa class và property metadata từ template.
- Abstract template không bị Spring eager instantiate như normal singleton.
- `AnnotationConfigApplicationContext` có Java config annotation infrastructure.
- `GenericApplicationContext` cho phép đăng ký bean definitions programmatically trước `refresh()`.
- Parent/child contexts đóng đúng thứ tự child trước parent bằng try-with-resources.
- Không cần Spring Boot, database, web server, environment-dependent properties hay thread scheduling.
- Output không phụ thuộc identity hash code, map order hay locale.

## 17. Có thể kết hợp cả hai cơ chế không?

**Có**, nhưng phải phân biệt **cấu hình tường minh** với **lookup tự động**.

Giả sử có parent BeanFactory và child BeanFactory. Mặc định child BeanFactory chỉ **tra bean ở ancestor khi không có local bean**. Điều đó **không** làm cho mỗi child bean definition tự kế thừa cùng tên parent definition.

Tuy nhiên Spring `AbstractBeanFactory` có logic **merge bean definition** và có thể truy cập `ConfigurableBeanFactory` parent để resolve parent definition trong một số tình huống — nổi bật khi một bean definition cục bộ có **`parentName` trùng chính bean name của nó**, cần lấy parent definition của **ancestor factory**. Đây là một trường hợp **explicit bean-definition inheritance qua factory hierarchy**, không được nhầm với lookup thông thường.

Mô hình:

```text
Parent BeanFactory:
  bean definition "connection"
      endpoint = parent.example

Child BeanFactory:
  bean definition "connection"
      parentName = "connection"   <-- explicit metadata relationship
      timeout = 1000

=> child merged definition kế thừa parent metadata
   và override timeout
```

Nếu chỉ có:

```text
Parent BeanFactory: bean "connection"
Child BeanFactory:  bean "connection" (independent @Bean)
```

thì **chỉ có name shadowing**, không có merging.

### Tại sao không nên nói “bean definition inheritance chỉ hoạt động trong cùng factory”?

Trường hợp phổ biến và dễ dạy nhất là **cùng factory** như thí nghiệm B. Nhưng Spring có logic đặc biệt cho **parent definition tại ancestor** trong một số trường hợp, nên phát biểu “không thể kế thừa bean definition qua contexts” là quá tuyệt đối. Mệnh đề chính xác hơn:

> **Context hierarchy tự nó không tạo bean-definition inheritance; metadata inheritance chỉ xảy ra khi definition chỉ định parentName và Spring có thể resolve được parent definition phù hợp.**

Không sử dụng đường nâng cao này một cách ngầm định trong kiến trúc lớn. Nếu thật sự cần, hãy viết integration test dựa trên đúng Spring version và xác minh merged metadata, overriding/lifecycle trong cả hai factories.

## 18. Parent BeanDefinition khác “parent bean instance”

Một parent definition có thể abstract:

```xml
<bean id="template"
      class="com.example.ServerOptions"
      abstract="true">
    <property name="timeoutMillis" value="3000"/>
</bean>
```

Spring dùng template để **gộp metadata**, không cần một object instance `template`. Gọi:

```java
context.getBean("template");
```

là mảnh **anti-example**: với abstract definition, yêu cầu tạo bean là không hợp lệ.

Ngược lại, trong context hierarchy, bean `parentService` là một bean **thật**, và child `getBean("parentService")` trả object đó. Đây chính là khác biệt quan trọng nhất giữa hai khái niệm “cha”.

Một child definition cũng **không tự nhận parent object qua Java field** hoặc có một `getParent()` của domain class. Nó nhận **merged recipe** để container tạo object theo Spring lifecycle.

## 19. Khi thiếu parent definition

Nếu bạn viết:

```java
ChildBeanDefinition child =
        new ChildBeanDefinition("doesNotExist");
context.registerBeanDefinition("someBean", child);
```

đây là một **mảnh minh họa lỗi thiết kế**, với `context` là `GenericApplicationContext`. Không có definition tên `doesNotExist` local hay ancestor phù hợp cho merge.

Lỗi có thể xuất hiện khi container cần merge definition lúc khởi tạo `someBean`; thường là `BeanDefinitionStoreException` do không resolve được parent definition. Đừng tự coi `parentName` là một chuỗi description không quan trọng.

### Reference tới normal bean khác với parentName

```xml
<bean id="worker" class="com.example.Worker">
    <property name="client" ref="clientBean"/>
</bean>
```

`ref="clientBean"` là **dependency reference tới một bean**; khác với:

```xml
<bean id="worker" parent="baseWorker">
    <!-- kế thừa metadata từ baseWorker -->
</bean>
```

Trong tình huống thứ nhất, container resolve một bean dependency để inject. Tình huống thứ hai, container merge **definition** rồi tạo instance. Hai dạng dùng khác nhau dù đều dựa trên tên.

## 20. Java @Bean inheritance và @Import không tạo context hierarchy

Một số cú pháp khác dễ gây hiểu sai:

| Cú pháp/khái niệm | Tạo hai contexts? | BeanDefinition parentName tự có? |
| --- | --- | --- |
| `@Import(OtherConfig.class)` | Không | Không mặc định |
| `@ComponentScan` | Không | Không mặc định |
| Java `class Child extends Parent` | Không | Không mặc định |
| Java `@Configuration` class extends một configuration class | Không | Không mặc định |
| `childContext.setParent(parentContext)` | **Có hai contexts đã tạo riêng** và liên kết | Không mặc định |
| XML `<bean id="x" parent="template"/>` | Không | **Có** |
| `new ChildBeanDefinition("template")` | Không cần | **Có** |

Chú ý `@Bean` methods được phát hiện/inherited hoặc overridden trong Java config class hierarchy là một vấn đề của Spring configuration class processing và Java method semantics. Đừng gọi nó là **BeanDefinition inheritance** trừ khi thực tế đã có `parentName` metadata.

Một common Spring Boot application thường chỉ cần một context với nhiều `@Configuration` classes và module packages. Chỉ thêm parent–child contexts nếu thực sự có lý do isolation/lifecycle hoặc web context boundary.

## 21. BeanFactoryPostProcessor và BeanPostProcessor thuộc factory nào?

Đây là phép kiểm tra kiến trúc hữu ích.

Với **context hierarchy**, parent BeanFactoryPostProcessors / BeanPostProcessors **không tự được áp dụng** lên bean definitions/instances của child factory. Parent-owned singleton có thể đã qua parent post-processors trước khi child lấy nó; nhưng child instance có chain riêng.

Với **bean definition inheritance trong một factory**, sau merge metadata, child instance được tạo và post-process bằng **cùng BeanFactory infrastructure** của factory đó như các normal bean khác.

Một số hệ quả:

- Parent `@Transactional` proxy không có nghĩa child beans tự được parent's transaction BPP xử lý.
- Child BeanDefinition thừa hưởng property values không có nghĩa nó thừa hưởng **BeanPostProcessor registration**.
- Có hai ApplicationContexts tức là có boundaries về singleton cache, custom scopes, lifecycle và bean post-processors.
- Có hai child BeanDefinitions trong một context vẫn có thể cùng chia sẻ AOP/BeanPostProcessors của context đó.

Nếu thấy proxy hoặc `@Autowired` hoạt động khác nhau giữa parent và child contexts, kiểm tra infrastructure cục bộ trước, không phải metadata `parentName`.

## 22. Event, MessageSource và Environment trong context hierarchy

ApplicationContext hierarchy còn có những behaviors khác, nhưng **không chứng minh definition inheritance**:

### Event propagation

Child `ApplicationContext.publishEvent(...)` có thể phát event cho listeners cục bộ rồi propagate lên parent context. Parent context nhận event không có nghĩa nó tự tìm được beans nằm trong child.

### MessageSource

Child `MessageSource` có thể fallback sang parent message source khi thiếu code, tùy implementation `HierarchicalMessageSource`. Đây là **message lookup** riêng, không phải merging bean definitions.

### Environment

`ConfigurableEnvironment.merge(...)` có thể kết hợp property sources và profiles từ parent vào child khi setParent/khởi tạo. Nó có precedence và thời điểm merge riêng, không phải “bean definition con copy property values từ parent definition”. 

### Lifecycle

Đóng child không có nghĩa đóng parent. Khi parent đóng mà child còn sống, các dependencies ở parent có thể trở nên không khả dụng. Thứ tự shutdown thường là child trước parent.

### Resolvers và conversion

BeanFactory configuration như `ConversionService`, custom scopes, BeanPostProcessors không tự sao chép mọi đăng ký từ parent sang child. Nếu cần behavior ở cả hai factories, cấu hình rõ tại đúng tầng.

**Kết luận của phần này:** một số tính năng của ApplicationContext hỗ trợ ancestor delegation, nhưng điều đó không biến quan hệ context cha/con thành quan hệ `BeanDefinition.getParentName()`.

## 23. Chọn cơ chế nào theo tình huống?

| Nhu cầu | Hướng ưu tiên |
| --- | --- |
| Child web context cần root services | Parent–child ApplicationContext |
| Hai servlet contexts dùng chung root DataSource | Context hierarchy, root sở hữu DataSource |
| Một module cần bean riêng và scope/lifecycle riêng | Context hierarchy nếu isolation thực sự cần thiết |
| Nhiều bean giống nhau ở vài property nhưng khác endpoint | Bean definition inheritance (thường XML/low-level API), hoặc Java factory/configuration reuse |
| Muốn default values chung cho các Java services | Constructor/configuration abstraction, không nhất thiết cần BeanDefinition inheritance |
| Cần Java subtype/polymorphism | Java class/interface inheritance, không liên quan context hierarchy |
| Muốn đưa config module khác vào cùng container | `@Import` / component scanning |
| Cần parent service gọi child service | Đổi hướng phụ thuộc hoặc định nghĩa contract/adapter ở parent, không trông chờ ancestor lookup ngược |
| Cần template trừu tượng không instantiate | Abstract BeanDefinition |
| Cần tránh duplicate bean instances across child contexts | Đặt singleton dùng chung trong parent thay vì copy definitions |
| Cần override một bean name ở child | Local name shadowing, nhớ kiểm tra type/qualifiers |
| Cần thay đổi BeanFactory post-processing cho mỗi module | Cấu hình infrastructure ở từng child factory |

Cả hai cơ chế có thể cùng tồn tại, nhưng nên được mô tả tách biệt trong design documents. Đừng dùng tên biến `parentBean` để vừa chỉ parent context vừa chỉ parent bean definition template trong một codebase lớn.

## 24. Lỗi phổ biến và nguyên nhân

| Triệu chứng | Nguyên nhân | Khắc phục |
| --- | --- | --- |
| Child không tìm thấy parent bean | Chưa `setParent` hoặc parent chưa register đúng bean | Liên kết trước refresh, kiểm tra lookup |
| Parent không tìm thấy child bean | Lookup một chiều | Thiết kế dependency direction đúng |
| `getBeansOfType` trên child bỏ sót parent | ListableBeanFactory là local-only | `BeanFactoryUtils.beansOfTypeIncludingAncestors` |
| Child lấy bean khác parent cùng name | Local bean name shadowing | Dùng distinct names/qualifiers khi cần |
| Nghĩ shadowing là definition inheritance | Hai factories có bean cùng name nhưng không có parentName | Phân biệt metadata và lookup |
| `ChildBeanDefinition` không tìm thấy template | `parentName` sai hoặc template không resolve được | Đăng ký/resolve parent definition trước khi instantiate |
| Template concrete bị tạo ngoài ý muốn | Quên `abstract=true` | Đánh dấu definition dùng làm template |
| Gọi `getBean("template")` ném exception | Abstract bean definition không instantiate | Dùng merged child definition thay vì getBean template |
| Child definition không inherit property | Sai parentName hoặc property name setter không khớp | Kiểm tra BeanDefinition và JavaBean setters |
| Override property sai type | Setter không nhận type phù hợp | Khai báo ConversionService/typed value phù hợp |
| Hai child definitions trả hai objects | Mỗi concrete definition có lifecycle/instance riêng | Đây có thể là đúng, không phải lỗi |
| Dùng `@Import` tưởng tạo context con | @Import chỉ đưa config vào cùng context | Tạo context riêng và setParent khi cần |
| Parent BPP không tác động lên child | Processor local to factory | Đăng ký infrastructure tại child |
| Khó debug nhiều contexts | Names/type candidates và scope bị nhầm | Log context identity, factory ownership, bean name |
| Property từ Environment khác template property | Property source merge và bean definition metadata là hai hệ thống | Kiểm tra từng cơ chế riêng |
| Test chỉ dùng một context không phát hiện lỗi hierarchy | Không kiểm tra ancestor lookup | Integration test thực với parent/child contexts |
| BeanDefinition inheritance bị nhầm với Java extends | Metadata recipe không phải subtype | Kiểm tra Java type hierarchy riêng |

## 25. Kiểm thử có hệ thống

### Test context hierarchy

Khởi tạo **hai** contexts, rồi assert:

1. `child.getParent() == parent`.
2. `child.containsBean("parentService") == true`.
3. `child.containsLocalBean("parentService") == false`.
4. `child.getBean(ParentService.class) == parent.getBean(ParentService.class)`.
5. `parent.containsBean("childOnly") == false`.
6. Parent và child có bean name `channel` riêng, mỗi nơi trả instance đúng.
7. `child.getBeansOfType(ParentService.class)` rỗng khi không có local bean.
8. `BeanFactoryUtils.beansOfTypeIncludingAncestors(child, ParentService.class)` tìm ra parent bean.
9. Child injection lấy parent dependency đúng.
10. Đóng child không destroy parent singleton ngay lập tức.

### Test BeanDefinition inheritance

Chỉ cần **một** `GenericApplicationContext`, assert:

1. `context.getParent() == null`.
2. `context.getBeanDefinition("settingsTemplate").isAbstract()` đúng.
3. `context.getBeanDefinition("eastSettings").getParentName()` là `settingsTemplate`.
4. Bean `eastSettings` inherit class, timeout, enabled.
5. Endpoint override đúng ở `eastSettings`.
6. Bean `copySettings` nhận các property defaults.
7. `eastSettings != copySettings` nếu mỗi concrete bean definition là singleton riêng.
8. Không có instance `settingsTemplate` được tạo.
9. Một child definition trỏ tới template không tồn tại bị từ chối khi merge/instantiation.
10. Nếu test case nâng cao cross-factory parentName, xác minh kết quả trên Spring version thật.

### Test cấu hình quan trọng

Đừng chỉ so sánh chuỗi console. Dùng JUnit assertions về instance identity (`assertSame`/`assertNotSame`), BeanDefinition parentName, scope và property values. Thử profile/conditional configuration để xác định definitions có thật được đăng ký trước khi test inheritance.

## 26. Checklist thiết kế trước production

1. “Parent” đang nói tới **container**, **bean definition**, hay **Java superclass**?
2. Nếu là context hierarchy, contexts có được tạo riêng và `setParent` trước refresh?
3. Nếu là bean definition inheritance, child có `parentName` rõ ràng?
4. Một parent definition chỉ làm template đã được đánh dấu `abstract=true`?
5. Có đang nhầm child lookup parent bean với việc copy parent singleton?
6. Trùng bean name giữa contexts là shadowing có chủ đích hay tình cờ?
7. Các ứng viên autowiring theo type có qualifiers/primary rõ không?
8. Có dùng local-only API mà mong thấy ancestors?
9. Parent có bị thiết kế phụ thuộc bean ở child không?
10. Có nhầm `@Import` hoặc class extends với ApplicationContext hierarchy?
11. Scope, lifecycle ownership và shutdown order có được ghi rõ?
12. Parent BPP/AOP infrastructure có được áp dụng đúng factory không?
13. Context hierarchy có thật sự cần thiết hay một context đơn giản hơn?
14. Bean definition templates có thật sự đáng dùng hay Java @Bean factories dễ bảo trì hơn?
15. Nếu kết hợp cả hai cơ chế, có integration tests trên đúng Spring version?

## 27. Tài liệu tham khảo chính thức

1. [Spring Framework 6.2 Reference — Bean Definition Inheritance](https://docs.spring.io/spring-framework/reference/6.2/core/beans/child-bean-definitions.html): template metadata, XML `parent`, `abstract`, property/constructor inheritance và override.
2. [Spring Framework 6.2 Javadoc — BeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactory.html): bean lookup fallback qua ancestor và local bean name priority.
3. [Spring Framework 6.2 Javadoc — HierarchicalBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/HierarchicalBeanFactory.html): `containsLocalBean`, `getParentBeanFactory`.
4. [Spring Framework 6.2 Javadoc — ListableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html): các thao tác liệt kê local-only.
5. [Spring Framework 6.2 Javadoc — BeanFactoryUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanFactoryUtils.html): `beansOfTypeIncludingAncestors` và name shadowing.
6. [Spring Framework 6.2 Javadoc — ChildBeanDefinition](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/ChildBeanDefinition.html): `getParentName`/`setParentName` và kế thừa metadata.
7. [Spring Framework 6.2 Javadoc — RootBeanDefinition](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/RootBeanDefinition.html): bean definition tổng hợp và định nghĩa root.
8. [Spring Framework 6.2 Javadoc — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html): register BeanDefinitions, setup parent, refresh lifecycle.
9. [Spring Framework 6.2 Javadoc — AbstractBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/AbstractBeanFactory.html): merged bean definitions và ancestor BeanFactory handling.
10. [Spring Framework Reference — Additional Capabilities of ApplicationContext](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): context hierarchy, message sources và application events.

**Kết luận:** Đừng coi “context con nhìn thấy context cha” là “bean definition con kế thừa definition cha”. **Context hierarchy** là quan hệ giữa **containers** và điều khiển tra cứu instances, ownership, lifecycle. **Bean definition inheritance** là quan hệ **metadata template → merged recipe** để tạo beans. Hai cơ chế có thể cùng tồn tại, nhưng **không tự sinh ra nhau**; chỉ một `parentName` được khai báo tường minh và resolvable mới kích hoạt metadata inheritance.


</details>

- [Quay lại đầu trang](#back-to-top)
