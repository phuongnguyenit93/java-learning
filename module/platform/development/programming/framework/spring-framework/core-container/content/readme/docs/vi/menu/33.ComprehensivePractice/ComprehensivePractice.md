<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Thiết kế đồ thị bean cho một luồng nghiệp vụ nhỏ](#comprehensive-practice-001)
- [Thay thế cách triển khai bằng cấu hình và qualifier](#comprehensive-practice-002)
- [Đối chiếu cùng một đồ thị bằng Java, annotation và XML](#comprehensive-practice-003)
- [Thực nghiệm singleton, prototype, lazy và provider](#comprehensive-practice-004)
- [Bổ sung cấu hình môi trường, tài nguyên và thông điệp](#comprehensive-practice-005)
- [Phát sự kiện nội bộ và theo dõi toàn bộ vòng đời](#comprehensive-practice-006)
- [Viết post-processor nhỏ với giới hạn tác động rõ ràng](#comprehensive-practice-007)
- [Tái hiện lỗi cấu hình rồi kiểm chứng cách sửa](#comprehensive-practice-008)
- [Đánh giá thiết kế theo khả năng kiểm thử và mức độ ghép nối](#comprehensive-practice-009)

## <a id="comprehensive-practice-001">Thiết kế đồ thị bean cho một luồng nghiệp vụ nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế đồ thị bean cho một luồng nghiệp vụ nhỏ trong Spring Framework

## 1. Mục tiêu bài thực hành

Một **đồ thị bean** (*bean dependency graph*) cho biết bean nào **cần** bean nào để được Spring IoC container lắp ghép. Đây là mô hình rất hữu ích trước khi bắt đầu viết nhiều `@Service`, `@Repository`, `@Configuration` hoặc `@Bean` methods.

Bài thực hành xây dựng một luồng nghiệp vụ nhỏ: **đặt đơn hàng cho một sản phẩm**, gồm định giá, áp dụng giảm giá, kiểm tra/trừ tồn kho, lưu đơn và ghi audit về việc đơn đã được đặt. Mục tiêu không phải xây một hệ thống thương mại điện tử hoàn chỉnh mà là chứng minh **Spring có thể lắp ghép một tập bean đúng trách nhiệm, đúng chiều dependency và kiểm thử được**.

Sau khi học, bạn có thể:

- Rút các thành phần IoC từ một yêu cầu nghiệp vụ.
- Phân biệt **đỉnh bean**, **cạnh dependency khi khởi tạo** và **lời gọi runtime**.
- Vẽ một đồ thị hướng **không có vòng** (DAG) trước khi viết Spring configuration.
- Chọn constructor injection và `@Bean` method parameter injection.
- Xác định bean nào singleton, bean nào chứa request data; tránh nhầm scope.
- Tách business policy khỏi orchestration và hạ tầng lưu dữ liệu.
- Không đưa event listener vào constructor dependency ngược với producer.
- Dùng `AnnotationConfigApplicationContext` để kiểm tra graph thật.
- Viết JUnit tests cho happy path, missing bean, tồn kho không đủ và context isolation.
- Nhận biết giới hạn về concurrency, database transaction, AOP và event delivery.

Bài thuộc module **Spring Framework — core-container**, mục **Thực hành tổng hợp**, đề tài **“Thiết kế đồ thị bean cho một luồng nghiệp vụ nhỏ”**. Ví dụ đầy đủ dùng **Java 17 + Spring Context 6.2.19 + JUnit Jupiter 5**, không cần Spring Boot, web server hay database.

> Kết quả quan trọng nhất là **mô hình bean graph rõ ràng và có thể kiểm chứng**, không phải số lượng annotation nhiều. Đồ thị dependency chỉ mô tả việc cung cấp collaborators; trình tự nghiệp vụ còn phải được mô tả bằng lời gọi method và event flow.

## 2. Đề bài nghiệp vụ

Một ứng dụng nhận yêu cầu đặt hàng:

~~~text
orderId = "ORD-100"
sku     = "BOOK"
quantity = 2
~~~

Quy tắc cố định trong ví dụ:

1. Catalog có một sản phẩm `BOOK`, giá **10.000 đơn vị tiền nhỏ** mỗi chiếc.
2. Policy giảm giá hiện tại là **10% trên subtotal**.
3. Kho ban đầu có **5 BOOK**.
4. Chỉ chấp nhận quantity dương, SKU hợp lệ và đủ tồn kho.
5. Giá được tính trước khi trừ tồn kho.
6. Sau khi trừ tồn kho, lưu một bản ghi đơn hàng trong repository in-memory.
7. Phát event `OrderPlaced` chứa ID và số tiền cuối cùng.
8. Listener audit ghi một record từ event.
9. Nếu SKU không tồn tại hoặc kho không đủ, không được tạo đơn và không phát audit event.
10. Mỗi order ID chỉ được đặt một lần trong demo để tránh ghi đè bản ghi.

Kỳ vọng khi đặt hai BOOK:

~~~text
subtotal = 2 * 10.000 = 20.000
discount = 10% * 20.000 = 2.000
total = 18.000
stock after = 5 - 2 = 3
orders saved = 1
audit record = PLACED:ORD-100:18000
~~~

Đơn vị tiền ở đây được biểu diễn bằng `int cents` (đơn vị tiền nhỏ), **không phải số thực**. Trong production cần domain Money/currency/rounding chính xác và kiểm tra giới hạn số học.

## 3. Bước đầu tiên: không vẽ bean từ mọi danh từ

Có thể vẽ rất nhiều class nếu biến mọi danh từ thành Spring bean: `OrderIdBean`, `SkuBean`, `QuantityBean`, `ReceiptBean`, `OrderEventBean`... nhưng cách đó làm graph khó hiểu.

Phân biệt:

| Khái niệm | Nên là Spring bean? | Lý do |
| --- | --- | --- |
| `CheckoutService` | Có | Một application service điều phối use case |
| `PricingService` | Có | Một năng lực tính giá có thể inject |
| `ProductCatalog` | Có | Dịch vụ đọc metadata/giá sản phẩm |
| `DiscountPolicy` implementation | Có | Chính sách được chọn theo cấu hình |
| `InventoryService` | Có | Quản lý tồn kho của demo |
| `OrderStore` | Có | Repository/in-memory adapter |
| `ApplicationEventPublisher` | Có, infrastructure của context | Producer cần publish event |
| `OrderAuditListener` | Có | Xử lý một business fact |
| `AuditTrail` | Có | Adapter nhận ghi audit |
| `Quote` / `OrderReceipt` | Không | Value objects được tạo cho từng lời gọi |
| `OrderPlaced` | Không | Event payload, không phải singleton collaborator |
| `String orderId` / `int quantity` | Không | Input dữ liệu của request |

**Bean graph mô tả các đối tượng được container quản lý và dependencies lâu dài.** Value objects và request inputs đi qua method parameters không tự động trở thành beans.

## 4. Xác định boundaries và trách nhiệm

Cần tránh một `OrderService` vừa tính giá, truy cập stock, gửi email, SQL và format receipt. Phân nhỏ theo trách nhiệm:

**Application layer**

`CheckoutService` điều phối các bước, nhận input, gọi collaborators và quyết định success/failure.

**Domain/application policies**

`PricingService` tính quote, dựa vào `ProductCatalog` và `DiscountPolicy`.

**Infrastructure/adapters**

`InventoryService` lưu tồn kho trong memory; `OrderStore` lưu order; `AuditTrail` lưu audit records.

**Event consumer**

`OrderAuditListener` nhận `OrderPlaced` qua Spring event mechanism và ghi `AuditTrail`, không phụ thuộc ngược `CheckoutService`.

Tách responsibility theo lý do thay đổi: đổi giảm giá không nên sửa CheckoutService; đổi repository sang JPA không nên viết lại pricing; thêm audit consumer không nên làm CheckoutService inject listener.

## 5. Liệt kê các đỉnh của đồ thị bean

Bảng các nodes:

| Node (bean) | Tên bean trong demo | Vai trò |
| --- | --- | --- |
| `CheckoutService` | `checkoutService` | Orchestrator của đặt đơn |
| `PricingService` | `pricingService` | Tính giá và giảm giá |
| `ProductCatalog` | `productCatalog` | Giá và danh mục sản phẩm |
| `DiscountPolicy` | `discountPolicy` | Chọn chính sách 10% |
| `InventoryService` | `inventoryService` | Kiểm tra và trừ stock |
| `OrderStore` | `orderStore` | Lưu đơn |
| `ApplicationEventPublisher` | Spring infrastructure | Publish fact |
| `OrderAuditListener` | `orderAuditListener` | Subscriber của `OrderPlaced` |
| `AuditTrail` | `auditTrail` | Lưu record audit |

Spring còn đăng ký các internal configuration processors/event infrastructure beans cần thiết; bảng chỉ liệt kê **các business beans và một capability hạ tầng quan trọng**, không tuyên bố đây là toàn bộ bean definitions trong container.

## 6. Cạnh dependency phải có hướng rõ ràng

Ký hiệu `A --> B` trong bài có nghĩa: **A yêu cầu reference đến B** trong constructor hoặc `@Bean` factory method.

Các cạnh business:

~~~text
CheckoutService --> PricingService
CheckoutService --> InventoryService
CheckoutService --> OrderStore
CheckoutService --> ApplicationEventPublisher

PricingService --> ProductCatalog
PricingService --> DiscountPolicy

OrderAuditListener --> AuditTrail
~~~

Không có:

~~~text
PricingService --> CheckoutService
InventoryService --> CheckoutService
OrderStore --> CheckoutService
OrderAuditListener --> CheckoutService
~~~

Vì vậy không có đường bắt buộc nào quay ngược về CheckoutService.

### Tại sao đây là một DAG?

Mỗi business service phụ thuộc vào collaborators ở tầng ngang hoặc thấp hơn, nhưng collaborators không inject ngược orchestrator. Các leaves như ProductCatalog, DiscountPolicy, InventoryService, OrderStore, AuditTrail không cần CheckoutService để được tạo.

Với các dependencies nói trên, có thể sắp thứ tự topo các **dependency prerequisites**:

~~~text
ProductCatalog, DiscountPolicy, InventoryService,
OrderStore, AuditTrail, Spring event infrastructure
   |
   +--> PricingService
   +--> OrderAuditListener
   |
   +--> CheckoutService (sau khi PricingService sẵn sàng)
~~~

Đây là **thứ tự logic theo prerequisites**, không phải bảo đảm rằng Spring thực tế tạo các bean không phụ thuộc theo đúng thứ tự liệt kê. Spring có thể quyết định thứ tự initialization giữa những singleton không có ràng buộc phụ thuộc.

## 7. Sơ đồ bean graph bằng Mermaid

~~~mermaid
flowchart TD
    Checkout[CheckoutService] --> Pricing[PricingService]
    Checkout --> Stock[InventoryService]
    Checkout --> Store[OrderStore]
    Checkout --> Publisher[ApplicationEventPublisher]
    Pricing --> Catalog[ProductCatalog]
    Pricing --> Policy[DiscountPolicy]
    Listener[OrderAuditListener] --> Audit[AuditTrail]
~~~

Các arrows là **constructor dependency edges**. Listener là một nhánh riêng. Event delivery **không** tương đương một constructor edge từ CheckoutService sang OrderAuditListener.

### Dễ nhầm ở bước này

Nếu thêm cạnh `OrderAuditListener → CheckoutService` chỉ vì listener muốn hỏi thông tin order, graph có thể lại sinh coupling không cần thiết. Nên thiết kế event payload đủ dùng hoặc inject read port độc lập nếu thật sự cần truy vấn.

## 8. Sơ đồ thứ tự gọi nghiệp vụ khác bean graph

Một use case thực thi theo thứ tự:

~~~text
Caller
  |
  v
CheckoutService.place(id, sku, quantity)
  |
  +--> OrderStore.exists(id)
  |
  +--> PricingService.quote(sku, quantity)
  |      +--> ProductCatalog.unitPriceCents(sku)
  |      +--> DiscountPolicy.discountCents(subtotal)
  |
  +--> InventoryService.reserve(sku, quantity)
  |
  +--> OrderStore.save(OrderRow)
  |
  +--> ApplicationEventPublisher.publishEvent(OrderPlaced)
  |      +--> OrderAuditListener.onOrderPlaced(event)
  |               +--> AuditTrail.record(...)
  |
  +--> OrderReceipt
~~~

Đây là **runtime call graph**. Nó có nhiều lời gọi theo thời gian, trong khi bean graph chỉ giữ các dependency references mà beans cần để hoạt động.

Một bean có thể là leaf trong dependency graph nhưng có nhiều method calls; một event listener có thể chạy đồng bộ theo publisher dù không inject producer. Phân biệt hai đồ thị giúp phát hiện runtime loops và sai transaction boundaries.

## 9. Tại sao chọn constructor injection?

Constructor injection làm dependencies bắt buộc hiện rõ:

~~~java
public CheckoutService(
        PricingService pricing,
        InventoryService inventory,
        OrderStore orders,
        ApplicationEventPublisher publisher) {
    this.pricing = pricing;
    this.inventory = inventory;
    this.orders = orders;
    this.publisher = publisher;
}
~~~

Đây là snippet; các classes và fields cần được khai báo trong class thực.

Lợi ích:

- Graph đọc được ngay từ constructor signature.
- Dùng `final` fields, không có bean ở trạng thái thiếu collaborator bắt buộc.
- Unit tests tạo service bằng fake collaborators một cách rõ ràng.
- Missing dependency fail-fast khi Spring tạo singleton.
- Circular constructor dependencies được phát hiện sớm.

### Tại sao không inject cả ApplicationContext?

Cho CheckoutService tự gọi `context.getBean(PricingService.class)` ẩn cạnh dependency và làm test phụ thuộc container. Chỉ dùng runtime bean lookup nếu bài toán thật sự cần dynamic resolution, không phải để tránh khai báo dependency.

## 10. Tại sao @Bean method parameters tốt cho lắp ghép?

Trong `@Configuration`, bean factory method có thể nhận đúng những dependencies cần thiết:

~~~java
@Bean
CheckoutService checkoutService(
        PricingService pricing,
        InventoryService stock,
        OrderStore store,
        ApplicationEventPublisher publisher) {
    return new CheckoutService(pricing, stock, store, publisher);
}
~~~

Spring resolve parameters của method theo bean graph. Bạn không cần gọi trực tiếp `pricingService()` từ method trên.

Khi cấu hình `@Configuration(proxyBeanMethods = false)`, direct call tới `@Bean` method là Java method call thông thường; nó có thể tạo object mới ngoài container semantics. Dùng **method parameter injection** như trên giúp wiring tường minh và tương thích với chế độ này.

Xem Spring Reference về `@Bean` parameter injection để hiểu sự tương đương với constructor dependency resolution.

## 11. Interface và port: khi nào cần?

Bản demo giữ `OrderStore` và `InventoryService` làm concrete in-memory implementations để dễ theo dõi. Trong codebase lớn, bạn có thể tạo:

~~~java
interface OrderRepository {
    void save(OrderRow row);
    boolean exists(String id);
}

interface InventoryPort {
    void reserve(String sku, int quantity);
    int remaining(String sku);
}
~~~

Đây là snippets minh họa; production cần định nghĩa `OrderRow`.

Sau đó CheckoutService inject `OrderRepository` và `InventoryPort`; JPA/HTTP/in-memory adapters implement interfaces.

**Đừng tạo interface cho mọi class chỉ vì nó là bean.** Chọn abstraction tại ranh giới có nhu cầu thay đổi implementation hoặc cần fake thật sự trong tests. Một interface có hai implementations và không có `@Qualifier`/`@Primary` thích hợp có thể gây `NoUniqueBeanDefinitionException`.

## 12. Chọn bean scope cho luồng này

| Bean | Scope đề xuất | Lý do |
| --- | --- | --- |
| CheckoutService | singleton | Không giữ request state |
| PricingService | singleton | Logic tính giá/stateless |
| ProductCatalog | singleton | Dữ liệu catalog trong demo |
| DiscountPolicy | singleton | Chính sách cấu hình |
| InventoryService | singleton | Một nguồn tồn kho cho context |
| OrderStore | singleton | Một repository chung cho context |
| AuditTrail | singleton | Nơi ghi records chung |
| OrderAuditListener | singleton | Listener không giữ request state riêng |
| Quote, Receipt, OrderPlaced | Không phải beans | Value objects theo từng request |

Singleton mặc định của Spring là **một bean instance cho mỗi bean name trong mỗi BeanFactory**, không phải singleton toàn JVM.

Vì InventoryService và OrderStore giữ **mutable state**, cần có synchronization/test isolation. Demo chỉ đảm bảo thao tác đơn giản trong một process, không cung cấp transaction hay distributed consistency.

### Không giữ orderId trong singleton field

Nếu CheckoutService có field mutable `currentOrderId` được ghi mỗi request, requests đồng thời sẽ dễ ghi đè nhau. Input request và result nên nằm trong local variables/records, không nên là mutable fields của singleton service.

## 13. Cần kiểm tra vòng phụ thuộc ở đâu?

Vòng có thể xuất hiện nếu một collaborator gọi ngược orchestrator:

~~~text
CheckoutService --> PricingService --> CheckoutService
~~~

Ví dụ PricingService inject CheckoutService chỉ để hỏi SKU của request. Cách sửa:

- Truyền SKU/quantity bằng method input.
- Dùng một ProductCatalog độc lập.
- Nếu cần read capability, inject read port ở tầng thấp hơn.
- Nếu chỉ cần notification, dùng business event thay vì synchronous callback.

Không dùng `@Lazy`, `ObjectProvider` hay setter injection như cách chữa vòng kiến trúc mặc định. Chúng có thể trì hoãn việc resolve bean, nhưng dependency logic vẫn tồn tại.

## 14. Không nhầm bean graph với module/package graph

Có ba sơ đồ khác nhau:

1. **Bean dependency graph:** instance A inject instance B ở runtime.
2. **Java package/module dependency graph:** class/package A import B theo compile-time.
3. **Business call/event flow:** method A gọi B hoặc publish event cho listener.

Một interface có thể làm compile-time graph sạch hơn nhưng bean implementation phía sau vẫn inject ngược, nên Spring constructor cycle vẫn tồn tại.

Ngược lại, runtime event graph có thể loop dù constructor dependency graph là DAG. Một bài thực hành tốt phải kiểm tra cả ba góc nhìn khi cần, không coi mọi arrow là cùng loại.

## 15. Chia cấu hình theo phạm vi

Demo có hai configuration classes:

**CoreConfig** đăng ký những beans cốt lõi:

- ProductCatalog
- PricingService
- InventoryService
- OrderStore
- CheckoutService
- AuditTrail
- OrderAuditListener

**DiscountConfig** đăng ký một `DiscountPolicy` cụ thể: giảm giá 10%.

### Tại sao tách DiscountConfig?

Policy là lựa chọn cấu hình có thể thay bằng implementation khác mà không viết lại CheckoutService/PricingService. Nó cũng cho phép negative wiring test:

~~~java
context.register(CoreConfig.class);
context.refresh(); // thiếu DiscountPolicy -> fail
~~~

Nếu mọi thứ đều được tạo bằng một static factory tự `new` bên trong CheckoutService, test không còn kiểm tra Spring DI graph rõ ràng.

## 16. Event audit: vì sao không inject listener vào CheckoutService?

Nếu CheckoutService cần công bố “đơn đã đặt”:

~~~java
publisher.publishEvent(
    new OrderPlaced(orderId, totalCents));
~~~

Spring có thể chuyển payload tới bean listener:

~~~java
@EventListener
public void onOrderPlaced(OrderPlaced event) {
    audit.record("PLACED:" +
        event.orderId() + ":" + event.totalCents());
}
~~~

Đây là snippets, cần imports `ApplicationEventPublisher`/`EventListener` và các types tương ứng.

Producer chỉ inject Spring-provided `ApplicationEventPublisher`, không inject `OrderAuditListener`. Listener chỉ phụ thuộc `AuditTrail`. Như vậy add/remove audit consumer không bắt CheckoutService thay constructor.

### Nhưng sự kiện không tự bất đồng bộ

Với default Spring event multicaster thông thường, `@EventListener` chạy **đồng bộ** trong publisher thread. Listener ném exception có thể ảnh hưởng caller. Không có retry/durability mặc định.

Trong demo, audit record xuất hiện trước khi `CheckoutService.place(...)` trả về. Đây không phải bảo đảm durable event delivery, transaction commit hay exactly-once trong production.

## 17. Chọn cách xử lý giá tiền

Giá được biểu diễn bằng số nguyên đơn vị tiền nhỏ:

~~~java
int subtotal = Math.multiplyExact(unitPriceCents, quantity);
int discount = policy.discountCents(subtotal);
int total = Math.subtractExact(subtotal, discount);
~~~

Không dùng `double` cho số tiền kế toán cần chính xác.

Trong ví dụ, giảm 10% bằng phép nhân `long` rồi chia 100 và chuyển lại về `int`:

~~~java
return Math.toIntExact((long) subtotalCents * percent / 100);
~~~

Phép toán làm tròn **xuống theo chia số nguyên**. Với dữ liệu minh họa 20.000, kết quả là đúng 2.000 nên không có phần lẻ.

Trong production, cần xác định currency, rounding mode, VAT, credit handling, overflow/limits và chính sách discount stacking; đây là phần domain modeling, không trực tiếp là bài IoC.

## 18. Transaction boundary: giới hạn có chủ đích

Một luồng thực tế có các cập nhật liên quan:

~~~text
reserve stock
save order
publish event
~~~

Nếu sau khi reserve stock, repository.save thất bại, stock có thể đã bị giảm nhưng order chưa lưu. Với in-memory demo không có transaction manager, **không có rollback tự động**.

### Khi chuyển sang database

- Xác định các thay đổi cần nằm trong cùng database transaction.
- Dùng transaction manager, repository và `@Transactional` đúng proxy boundary.
- Chú ý optimistic/pessimistic locking hoặc atomic stock decrement để tránh overselling.
- Nếu phát email/message ngoài DB sau commit, cân nhắc `@TransactionalEventListener(AFTER_COMMIT)` hoặc transactional outbox.
- Không coi `ApplicationEventPublisher` là message broker bền vững.

Bài thực hành **không tuyên bố đảm bảo ACID hay chống mọi race condition**. Nó tập trung vào **cấu trúc Spring bean graph** để dễ nâng cấp về sau.

## 19. Cấu trúc project chạy được

~~~text
spring-bean-graph-practice/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── example/
    │               └── graph/
    │                   └── OrderFlowDemo.java
    └── test/
        └── java/
            └── com/
                └── example/
                    └── graph/
                        └── OrderFlowDemoTest.java
~~~

Dùng **một public class `OrderFlowDemo`** chứa các nested static classes/records/configurations để ví dụ được build dễ dàng mà không phải tạo quá nhiều files. Trong code production, nên tách mỗi domain type lớn thành file/package thích hợp.

## 20. pom.xml — Spring Context 6.2.19, Java 17, JUnit 5

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-bean-graph-practice</artifactId>
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
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.1</version>
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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.graph.OrderFlowDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Maven có thể download dependencies ở lần chạy đầu tiên. Project không khai báo Spring Boot starters nên nó không có automatic Boot application configuration.

## 21. File OrderFlowDemo.java — mã đầy đủ

Đường dẫn `src/main/java/com/example/graph/OrderFlowDemo.java`:

~~~java
package com.example.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

public class OrderFlowDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 openContext()) {

            DiscountPolicy policy =
                context.getBean(DiscountPolicy.class);
            PricingService pricing =
                context.getBean(PricingService.class);
            CheckoutService checkout =
                context.getBean(CheckoutService.class);
            InventoryService inventory =
                context.getBean(InventoryService.class);
            OrderStore store =
                context.getBean(OrderStore.class);
            AuditTrail audit =
                context.getBean(AuditTrail.class);

            System.out.println("policy=" + policy.name());
            System.out.println("quote=" +
                pricing.quote("BOOK", 2));

            OrderReceipt receipt =
                checkout.place("ORD-100", "BOOK", 2);

            System.out.println("order=" + receipt);
            System.out.println("stock.remaining=" +
                inventory.remaining("BOOK"));
            System.out.println("orders.count=" + store.count());
            System.out.println("audit=" + audit.entries());
            System.out.println("checkout.singleton=" +
                (checkout == context.getBean(CheckoutService.class)));

            try {
                checkout.place("ORD-101", "BOOK", 8);
            } catch (IllegalStateException expected) {
                System.out.println("rejected=" +
                    expected.getMessage());
            }

            System.out.println("stock.after-reject=" +
                inventory.remaining("BOOK"));
            System.out.println("orders.after-reject=" +
                store.count());
        }
    }

    public static AnnotationConfigApplicationContext openContext() {
        return new AnnotationConfigApplicationContext(
            CoreConfig.class, DiscountConfig.class);
    }

    public record Quote(
            int subtotalCents,
            int discountCents,
            int totalCents) {
    }

    public record OrderReceipt(
            String orderId,
            int totalCents) {
    }

    public record OrderRow(
            String orderId,
            String sku,
            int quantity,
            int totalCents) {
    }

    public record OrderPlaced(
            String orderId,
            int totalCents) {
    }

    public static final class ProductCatalog {

        public int unitPriceCents(String sku) {
            if (!"BOOK".equals(sku)) {
                throw new IllegalArgumentException(
                    "Unknown sku: " + sku);
            }
            return 10_000;
        }
    }

    public interface DiscountPolicy {
        String name();
        int discountCents(int subtotalCents);
    }

    public record PercentageDiscount(
            int percentage) implements DiscountPolicy {

        public PercentageDiscount {
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException(
                    "percentage out of range");
            }
        }

        @Override
        public String name() {
            return "percent-" + percentage;
        }

        @Override
        public int discountCents(int subtotalCents) {
            return Math.toIntExact(
                (long) subtotalCents * percentage / 100);
        }
    }

    public static final class PricingService {
        private final ProductCatalog catalog;
        private final DiscountPolicy discount;

        public PricingService(
                ProductCatalog catalog,
                DiscountPolicy discount) {
            this.catalog = catalog;
            this.discount = discount;
        }

        public Quote quote(String sku, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                    "Quantity must be positive");
            }

            int subtotal = Math.multiplyExact(
                catalog.unitPriceCents(sku), quantity);
            int saved = discount.discountCents(subtotal);

            return new Quote(
                subtotal, saved,
                Math.subtractExact(subtotal, saved));
        }
    }

    public static final class InventoryService {
        private int availableBooks = 5;

        public synchronized void reserve(
                String sku, int quantity) {
            if (!"BOOK".equals(sku)) {
                throw new IllegalArgumentException(
                    "Unknown sku: " + sku);
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                    "Quantity must be positive");
            }
            if (quantity > availableBooks) {
                throw new IllegalStateException(
                    "Insufficient stock: " + sku);
            }
            availableBooks -= quantity;
        }

        public synchronized int remaining(String sku) {
            if (!"BOOK".equals(sku)) {
                throw new IllegalArgumentException(
                    "Unknown sku: " + sku);
            }
            return availableBooks;
        }
    }

    public static final class OrderStore {
        private final Map<String, OrderRow> orders =
            new LinkedHashMap<>();

        public synchronized boolean exists(String id) {
            return orders.containsKey(id);
        }

        public synchronized void save(OrderRow row) {
            if (orders.containsKey(row.orderId())) {
                throw new IllegalArgumentException(
                    "Duplicate order: " + row.orderId());
            }
            orders.put(row.orderId(), row);
        }

        public synchronized int count() {
            return orders.size();
        }
    }

    public static final class CheckoutService {
        private final PricingService pricing;
        private final InventoryService inventory;
        private final OrderStore store;
        private final ApplicationEventPublisher publisher;

        public CheckoutService(
                PricingService pricing,
                InventoryService inventory,
                OrderStore store,
                ApplicationEventPublisher publisher) {
            this.pricing = pricing;
            this.inventory = inventory;
            this.store = store;
            this.publisher = publisher;
        }

        public OrderReceipt place(
                String id, String sku, int quantity) {
            if (store.exists(id)) {
                throw new IllegalArgumentException(
                    "Duplicate order: " + id);
            }

            Quote quote = pricing.quote(sku, quantity);

            // Các bước này KHÔNG có database transaction.
            inventory.reserve(sku, quantity);
            store.save(new OrderRow(
                id, sku, quantity, quote.totalCents()));

            publisher.publishEvent(new OrderPlaced(
                id, quote.totalCents()));

            return new OrderReceipt(
                id, quote.totalCents());
        }
    }

    public static final class AuditTrail {
        private final List<String> records =
            new CopyOnWriteArrayList<>();

        public void record(String entry) {
            records.add(entry);
        }

        public List<String> entries() {
            return List.copyOf(records);
        }
    }

    public static final class OrderAuditListener {
        private final AuditTrail audit;

        public OrderAuditListener(AuditTrail audit) {
            this.audit = audit;
        }

        @EventListener
        public void onOrderPlaced(OrderPlaced event) {
            audit.record(
                "PLACED:" + event.orderId() +
                ":" + event.totalCents());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {

        @Bean
        public ProductCatalog productCatalog() {
            return new ProductCatalog();
        }

        @Bean
        public PricingService pricingService(
                ProductCatalog catalog,
                DiscountPolicy discount) {
            return new PricingService(catalog, discount);
        }

        @Bean
        public InventoryService inventoryService() {
            return new InventoryService();
        }

        @Bean
        public OrderStore orderStore() {
            return new OrderStore();
        }

        @Bean
        public CheckoutService checkoutService(
                PricingService pricing,
                InventoryService inventory,
                OrderStore store,
                ApplicationEventPublisher publisher) {
            return new CheckoutService(
                pricing, inventory, store, publisher);
        }

        @Bean
        public AuditTrail auditTrail() {
            return new AuditTrail();
        }

        @Bean
        public OrderAuditListener orderAuditListener(
                AuditTrail audit) {
            return new OrderAuditListener(audit);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DiscountConfig {

        @Bean
        public DiscountPolicy discountPolicy() {
            return new PercentageDiscount(10);
        }
    }
}
~~~

### Quan sát về khả năng biên dịch

- Có đúng một top-level public class `OrderFlowDemo`, nên filename khớp Java rules.
- Nested public static classes và records có thể được dùng từ JUnit test bằng tên `OrderFlowDemo.PricingService`, v.v.
- Record compact constructor `PercentageDiscount { ... }` hợp lệ trong Java 17.
- Spring-managed classes có constructors public và được tạo từ `@Bean` methods.
- `@Configuration(proxyBeanMethods = false)` không có direct inter-bean factory-method call; mọi dependencies cần thiết đều đến qua method parameters.
- Spring `ApplicationEventPublisher` được inject từ ApplicationContext infrastructure.
- Annotated `@EventListener` method được Spring context phát hiện khi handler bean đăng ký trước lúc phát event.

## 22. Chạy chương trình

Từ thư mục có `pom.xml`, với JDK 17+ và Maven 3.9.x:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do code `System.out.println` chủ động in:

~~~text
policy=percent-10
quote=Quote[subtotalCents=20000, discountCents=2000, totalCents=18000]
order=OrderReceipt[orderId=ORD-100, totalCents=18000]
stock.remaining=3
orders.count=1
audit=[PLACED:ORD-100:18000]
checkout.singleton=true
rejected=Insufficient stock: BOOK
stock.after-reject=3
orders.after-reject=1
~~~

Maven/Spring logging có thể in thêm các dòng khác. Output trên dựa vào business state của **một context mới** và một `OrderAuditListener` duy nhất.

### Vì sao audit đã có khi println chạy?

Trong project này không bật asynchronous event multicasting. Listener chạy đồng bộ theo default event dispatch, hoàn thành trước khi `place` trả về. Vì vậy `audit.entries()` đã chứa record.

Trong hệ thống có executor/`@Async`, không được dùng giả định timing này mà không có synchronization/test riêng.

## 23. Diễn giải kết quả và graph

**policy=percent-10:** Bean `DiscountPolicy` được cung cấp bởi DiscountConfig, PricingService không tự tạo policy bên trong.

**quote=...:** ProductCatalog trả đơn giá 10.000; `Math.multiplyExact` tạo subtotal 20.000; giảm 10% là 2.000; total 18.000.

**order=...:** `CheckoutService` đã điều phối và trả immutable OrderReceipt value object.

**stock.remaining=3:** InventoryService singleton đã giảm từ 5 xuống 3.

**orders.count=1:** OrderStore singleton giữ một OrderRow.

**audit=[...]**: OrderAuditListener độc lập với CheckoutService đã nhận OrderPlaced event qua Spring.

**checkout.singleton=true:** Lấy cùng bean từ context hai lần có cùng reference, xác nhận singleton behavior trong context đó.

**rejected=Insufficient stock: BOOK:** Lần đặt tiếp yêu cầu 8 BOOK khi chỉ còn 3, InventoryService ném exception.

**stock.after-reject=3, orders.after-reject=1:** Vì `reserve` kiểm tra quantity trước khi trừ, đường insufficient stock không thay đổi state, nên không lưu đơn và không phát event.

## 24. JUnit 5: kiểm thử bean graph và behavior

Tạo file `src/test/java/com/example/graph/OrderFlowDemoTest.java`:

~~~java
package com.example.graph;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class OrderFlowDemoTest {

    @Test
    void wiresTheRequiredBeanGraph() {
        try (AnnotationConfigApplicationContext context =
                 OrderFlowDemo.openContext()) {

            OrderFlowDemo.CheckoutService checkout =
                context.getBean(
                    OrderFlowDemo.CheckoutService.class);

            assertNotNull(checkout);
            assertNotNull(context.getBean(
                OrderFlowDemo.PricingService.class));
            assertNotNull(context.getBean(
                OrderFlowDemo.DiscountPolicy.class));
            assertNotNull(context.getBean(
                OrderFlowDemo.InventoryService.class));
            assertNotNull(context.getBean(
                OrderFlowDemo.OrderStore.class));
            assertNotNull(context.getBean(
                OrderFlowDemo.OrderAuditListener.class));

            assertSame(
                checkout,
                context.getBean(
                    OrderFlowDemo.CheckoutService.class));
        }
    }

    @Test
    void successfulOrderUpdatesStockStoreAndAudit() {
        try (AnnotationConfigApplicationContext context =
                 OrderFlowDemo.openContext()) {

            OrderFlowDemo.CheckoutService checkout =
                context.getBean(
                    OrderFlowDemo.CheckoutService.class);

            OrderFlowDemo.OrderReceipt receipt =
                checkout.place("ORD-100", "BOOK", 2);

            assertEquals(
                new OrderFlowDemo.OrderReceipt(
                    "ORD-100", 18_000),
                receipt);

            assertEquals(
                3,
                context.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));

            assertEquals(
                1,
                context.getBean(
                    OrderFlowDemo.OrderStore.class).count());

            assertEquals(
                java.util.List.of("PLACED:ORD-100:18000"),
                context.getBean(
                    OrderFlowDemo.AuditTrail.class).entries());
        }
    }

    @Test
    void insufficientStockDoesNotSaveOrAudit() {
        try (AnnotationConfigApplicationContext context =
                 OrderFlowDemo.openContext()) {

            assertThrows(
                IllegalStateException.class,
                () -> context.getBean(
                    OrderFlowDemo.CheckoutService.class)
                    .place("ORD-200", "BOOK", 8));

            assertEquals(
                5,
                context.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));
            assertEquals(
                0,
                context.getBean(
                    OrderFlowDemo.OrderStore.class).count());
            assertTrue(context.getBean(
                OrderFlowDemo.AuditTrail.class)
                .entries().isEmpty());
        }
    }

    @Test
    void unknownProductRejectedBeforeStockChange() {
        try (AnnotationConfigApplicationContext context =
                 OrderFlowDemo.openContext()) {

            assertThrows(
                IllegalArgumentException.class,
                () -> context.getBean(
                    OrderFlowDemo.CheckoutService.class)
                    .place("ORD-300", "UNKNOWN", 1));

            assertEquals(
                5,
                context.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));
            assertEquals(
                0,
                context.getBean(
                    OrderFlowDemo.OrderStore.class).count());
        }
    }

    @Test
    void duplicateIdRejectedWithoutExtraReservation() {
        try (AnnotationConfigApplicationContext context =
                 OrderFlowDemo.openContext()) {

            OrderFlowDemo.CheckoutService checkout =
                context.getBean(
                    OrderFlowDemo.CheckoutService.class);

            checkout.place("ORD-1", "BOOK", 1);

            assertThrows(
                IllegalArgumentException.class,
                () -> checkout.place("ORD-1", "BOOK", 1));

            assertEquals(
                4,
                context.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));
            assertEquals(
                1,
                context.getBean(
                    OrderFlowDemo.OrderStore.class).count());
            assertEquals(
                1,
                context.getBean(
                    OrderFlowDemo.AuditTrail.class)
                    .entries().size());
        }
    }

    @Test
    void missingDiscountPolicyFailsAtContextRefresh() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {

            // Cố ý KHÔNG đăng ký DiscountConfig.
            context.register(OrderFlowDemo.CoreConfig.class);

            assertThrows(
                BeanCreationException.class,
                context::refresh);
        }
    }

    @Test
    void separateContextsHaveSeparateSingletonState() {
        try (AnnotationConfigApplicationContext first =
                 OrderFlowDemo.openContext();
             AnnotationConfigApplicationContext second =
                 OrderFlowDemo.openContext()) {

            assertNotSame(
                first.getBean(
                    OrderFlowDemo.InventoryService.class),
                second.getBean(
                    OrderFlowDemo.InventoryService.class));

            first.getBean(
                OrderFlowDemo.CheckoutService.class)
                .place("ORD-X", "BOOK", 1);

            assertEquals(
                4,
                first.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));
            assertEquals(
                5,
                second.getBean(
                    OrderFlowDemo.InventoryService.class)
                    .remaining("BOOK"));
            assertEquals(
                0,
                second.getBean(
                    OrderFlowDemo.AuditTrail.class)
                    .entries().size());
        }
    }
}
~~~

### Lệnh kiểm thử

~~~bash
mvn -q test
~~~

Project gồm **7 JUnit Jupiter tests**; kỳ vọng **7 tests, 0 failures, 0 errors**. `maven-surefire-plugin` tạo báo cáo trong `target/surefire-reports`. Test thiếu DiscountPolicy cố tình khiến Spring ghi startup warning khi context refresh thất bại, nhưng `assertThrows` xác nhận đây là kết quả mong đợi, không phải test failure.

### Giá trị của negative wiring test

Nếu chỉ test Java business objects bằng `new`, bạn có thể không nhận ra DiscountPolicy bean bị quên trong config. Test thiếu policy tạo một Spring context thật chỉ với CoreConfig, nên Spring buộc phải resolve collaborator của PricingService và fail-fast. Đây là bằng chứng rằng graph có required dependency được khai báo thật trong container.

## 25. Ma trận test từ graph đến hành vi

| Mục cần bảo vệ | Test tương ứng | Lỗi bắt được |
| --- | --- | --- |
| Required bean graph hợp lệ | `wiresTheRequiredBeanGraph` | Thiếu bean, sai wiring |
| Luồng đặt đơn thành công | `successfulOrderUpdatesStockStoreAndAudit` | Sai pricing/state/event |
| Kho không đủ | `insufficientStockDoesNotSaveOrAudit` | Mutation khi request bị từ chối |
| SKU không có | `unknownProductRejectedBeforeStockChange` | Validation đặt sau mutation |
| Trùng order ID | `duplicateIdRejectedWithoutExtraReservation` | Ghi đè/lặp side effects |
| Thiếu policy | `missingDiscountPolicyFailsAtContextRefresh` | Sai bean registration |
| Hai contexts độc lập | `separateContextsHaveSeparateSingletonState` | Shared mutable/static state |

Mỗi test tạo context mới và close bằng try-with-resources. Do đó in-memory mutable singleton state không làm tests lệ thuộc thứ tự chạy, miễn là source code không tự dùng static shared mutable state.

## 26. Cách xác định graph có vòng bằng suy luận

Một vòng bắt buộc:

~~~text
A --> B --> C --> A
~~~

không có thứ tự topo hợp lệ cho constructor injection. Spring có thể ném `BeanCurrentlyInCreationException` khi context refresh.

Trong graph demo:

~~~text
CheckoutService
      |
      +--> PricingService --> ProductCatalog
      |                 |
      |                 +--> DiscountPolicy
      |
      +--> InventoryService
      +--> OrderStore
      +--> ApplicationEventPublisher

OrderAuditListener --> AuditTrail
~~~

Không có cạnh quay về CheckoutService hay PricingService. Điều này được củng cố bằng thiết kế constructors; test refresh thành công chỉ kiểm tra wiring **với đúng configuration đang được đăng ký**, không chứng minh mọi cấu hình tùy chọn khác đều acyclic.

### Thuật toán cho graph lớn

Khi có hàng trăm beans, có thể mô hình hóa directed graph rồi tìm **strongly connected components (SCC)** bằng Tarjan/Kosaraju. Các SCC có nhiều nodes hoặc self-loop là candidates cần xem xét. Tuy nhiên static source dependency không hoàn toàn bằng Spring runtime bean graph vì profiles, qualifiers, conditions, factory beans và proxies có thể thay đổi bindings.

## 27. Đo kiểm dependency graph mà không đọc mọi bean internals

Trong test context, có thể kiểm tra dependency relationship qua `ConfigurableListableBeanFactory`:

~~~java
String[] directDependencies =
    context.getBeanFactory()
        .getDependenciesForBean("checkoutService");
~~~

Đây là snippet với context từ project. BeanFactory có thể ghi nhận dependencies từ runtime bean creation/autowiring, tuy nhiên **không bảo đảm API này là một visualizer hoàn chỉnh cho tất cả quan hệ runtime/callbacks/events**.

### Tại sao không dựa hoàn toàn vào reflection?

Reflection trên constructor fields mô tả declared Java types, không tự biết profile-selected actual implementation, qualifiers, proxies, FactoryBeans hoặc programmatic registration.

### Tại sao không assert beanDefinitionCount?

Spring đăng ký internal processors và configuration infrastructure; con số toàn bộ definitions phụ thuộc configuration/version. Kiểm tra các bean names/types cần thiết và interactions thật đáng tin hơn.

## 28. Cơ chế inject ApplicationEventPublisher

Trong `@Bean CheckoutService checkoutService(..., ApplicationEventPublisher publisher)`, Spring `ApplicationContext` có thể cung cấp một `ApplicationEventPublisher` implementation/capability phù hợp.

`ApplicationEventPublisher` là một framework-level dependency, không phải user-defined `@Bean` method trong CoreConfig. Nó có thể xuất hiện như một **resolvable dependency** do context cung cấp, và không nhất thiết xuất hiện như một application BeanDefinition có tên bạn dự đoán.

Vì vậy đừng vội viết thêm:

~~~java
@Bean
ApplicationEventPublisher applicationEventPublisher() {
    return ...;
}
~~~

khi đang dùng context chuẩn. Muốn test code không có Spring infrastructure, có thể mock/fake publisher ở unit test thuần; với integration wiring test hãy dùng real ApplicationContext.

## 29. Khi event làm graph không còn đúng?

Nếu listener giữ constructor reference tới CheckoutService:

~~~java
class OrderAuditListener {
    OrderAuditListener(CheckoutService checkout) {
        // ...
    }
}
~~~

một constructor edge `OrderAuditListener → CheckoutService` được thêm, nhưng chưa có edge ngược từ CheckoutService tới listener vì producer inject Publisher chứ không phải listener. Về mặt constructor graph, chưa chắc xuất hiện vòng ngay, nhưng listener nay đã coupling với cả orchestrator.

Nếu listener gọi checkout.place trong callback, nó còn có thể gây **runtime event feedback loop**. Nên tránh injecting producer vào listener chỉ để đọc order; dùng event payload hoặc một read port.

### Chọn event đúng ý nghĩa

`OrderPlaced` là business fact. Đừng thay payment authorization bắt buộc bằng `PleaseAuthorizePayment` event rồi polling kết quả từ global map: đó là RPC bị che giấu. Khi caller cần kết quả ngay, giữ method call trực tiếp qua port.

## 30. Khi nào nên dùng profile cho DiscountPolicy?

Bản demo có một DiscountPolicy đơn giản và không dùng profiles để tránh tăng độ phức tạp của graph. Trong môi trường khác nhau, có thể có:

~~~java
@Bean
@Profile("demo")
DiscountPolicy demoPolicy() {
    return new PercentageDiscount(10);
}

@Bean
@Profile("prod")
DiscountPolicy prodPolicy() {
    return new PercentageDiscount(0);
}
~~~

Đây là snippets minh họa trong config class, cần imports Spring `@Bean` và `@Profile`. Nếu không có active profile phù hợp, PricingService không có DiscountPolicy và startup fail. Nếu cả hai profiles active, có thể xuất hiện hai implementations và ambiguity trừ khi có candidate-selection policy.

Set profiles **trước refresh**. `@Profile` là registration-time decision, không phải công tắc thay bean đang active bằng thay đổi profile runtime.

## 31. FactoryBean, lazy, prototype: có cần cho ví dụ này không?

**Không.** Luồng đặt hàng nhỏ có required singleton dependencies, nên constructor injection và `@Bean` methods là đủ.

Thêm `FactoryBean`, scoped proxies, `ObjectProvider`, `@Lazy` hoặc prototype vào ví dụ chỉ để có thêm khái niệm sẽ làm graph khó theo dõi.

Chỉ dùng các cơ chế đó khi có nhu cầu thật:

- Prototype mỗi lần request object mới.
- Request/session scope của web application.
- Dynamic lookup/on-demand collaborator.
- Factory product construction.
- Proxy interception cho AOP/transactions.

Nếu singleton inject một prototype trực tiếp qua constructor, instance prototype được resolve **ở thời điểm singleton được tạo**, không phải tự động thay đổi mỗi method call. Có thể dùng provider/proxy phù hợp nếu yêu cầu mới mỗi lần.

## 32. Xử lý lỗi missing hoặc nhiều candidates

| Triệu chứng | Điều nên xem |
| --- | --- |
| `NoSuchBeanDefinitionException` | Bean missing do chưa đăng ký/import, profile/conditional không match |
| `UnsatisfiedDependencyException` | Constructor/`@Bean` parameter không được thỏa mãn |
| `NoUniqueBeanDefinitionException` | Hai beans cùng type; qualifier/primary/naming |
| `BeanCurrentlyInCreationException` | Vòng dependencies, đặc biệt constructor |
| `ScopeNotActiveException` | Bean thuộc web/custom scope chưa active |
| `BeanCreationException` từ code | Constructor/factory/init method ném exception |
| `BeanNotOfRequiredTypeException` | Named bean không có required type |
| Event listener không chạy | Listener không đăng ký, publish quá sớm hoặc khác context |
| Event listener chạy nhiều lần | Multiple subscribers hoặc event re-publication |

Không sửa missing bean bằng cách bật component scan toàn package rộng nếu đúng nguyên nhân là DiscountConfig chưa import. Không sửa constructor cycle bằng `@Lazy` mặc định. Đồ thị rõ ràng giúp sửa đúng cạnh.

## 33. Scope và concurrency: vì sao demo vẫn có giới hạn?

`InventoryService.reserve` sử dụng `synchronized` để tránh hai threads đồng thời khiến local `availableBooks` âm. `OrderStore` cũng dùng synchronized cho các methods của map. Tuy nhiên **mỗi operation được lock riêng**, không có một transaction bao trùm:

~~~text
checkout A: check id A không tồn tại
checkout B: check id A không tồn tại
checkout A: reserve 1
checkout B: reserve 1
checkout A: save row A
checkout B: save row A -> duplicate error
~~~

Ở trường hợp này, stock đã có thể giảm hai lần nhưng chỉ một order lưu thành công. Demo có **đồng bộ cục bộ**, không có atomicity cross-bean.

### Cách nâng cấp

- DB transaction bao cả inventory reservation và order insert.
- Unique constraint trên order ID.
- Optimistic locking/version column hoặc conditional stock update.
- Retry chính sách rõ ràng cho optimistic conflicts.
- Idempotency key cho external retries.
- Outbox nếu event phải bền vững theo commit.
- Integration tests đồng thời với real datastore.

Không tuyên bố graph là DAG thì tự động chống race conditions; **dependency architecture** và **transaction/concurrency correctness** là hai yêu cầu riêng biệt.

## 34. Thêm adapter interface khi dùng database

Khi chuyển in-memory sang JPA, graph có thể thành:

~~~text
CheckoutService --> OrderRepository (port)
                            |
                            v
                   JpaOrderRepository (adapter)
                            |
                            v
                       EntityManager
~~~

Về **runtime bean injection**, CheckoutService inject interface OrderRepository nhưng thực tế nhận bean JpaOrderRepository; arrow port→adapter trong hình là **implementation binding**, không phải constructor dependency của port (interface không phải bean instance).

Nên ký hiệu tách biệt:

~~~text
CheckoutService --injects--> OrderRepository port
JpaOrderRepository --implements--> OrderRepository port
JpaOrderRepository --depends-on--> EntityManager
~~~

Phân biệt annotation/compile-time relation với actual bean reference giúp tránh vẽ sai mũi tên hoặc kết luận vòng không tồn tại khi một adapter inject ngược service.

## 35. Theo dõi thay đổi use case

Giả sử cần thêm “gửi thông báo”. Đặt câu hỏi:

1. Notification cần trả kết quả bắt buộc cho checkout không?
2. Nó cần order ID, total, customer email từ đâu?
3. Có cần gửi sau commit?
4. Nếu gửi lỗi, order có nên rollback?
5. Có nhiều consumers độc lập không?

Nếu chỉ là post-order notification, thêm `OrderNotificationListener` và `NotificationSender` bên dưới event. CheckoutService không cần inject thêm listener.

Nếu payment authorization là bước bắt buộc, thêm `PaymentPort` trực tiếp vào CheckoutService và test failure path; không che request-response qua event.

Graph nên phát triển theo **semantics**, không chỉ theo tính tiện lợi của DI annotation.

## 36. Phân biệt test mock và test container

**Unit test** cho PricingService có thể trực tiếp tạo `ProductCatalog` và `PercentageDiscount`. Nó kiểm tra toán và validation, không có startup Spring.

**Minimal container test** tạo `AnnotationConfigApplicationContext(CoreConfig, DiscountConfig)`. Nó xác minh @Bean registrations, method parameter resolution và event listener infrastructure.

**Integration test có database** kiểm tra persistence, transaction, rollback, stock consistency và outbox. Không nên dùng in-memory HashMap rồi tuyên bố database transaction đã được test.

Từng tầng test có vai trò, và một bài hoàn chỉnh thường kết hợp unit tests cho policies với wiring tests cho graph và integration tests cho infrastructure.

## 37. Cách review đồ thị trước khi merge

Một checklist có thể áp dụng cho bất kỳ workflow nhỏ:

- [ ] Mô tả input, output, business steps và failure cases.
- [ ] Danh sách beans khác với value objects/request inputs.
- [ ] Mỗi bean có trách nhiệm và owner rõ.
- [ ] Cạnh A→B biểu diễn đúng constructor/@Bean method dependency.
- [ ] Không nhầm runtime method calls với bean references.
- [ ] Graph constructor required dependencies không có vòng.
- [ ] Không dùng ApplicationContext lookup trong business services để che dependency.
- [ ] Bean state phù hợp singleton/prototype/request scope.
- [ ] Không giữ mutable request data trong singleton fields.
- [ ] Policy được cấu hình bằng một implementation rõ.
- [ ] Multiple candidates có qualifier/primary policy nếu cần.
- [ ] @Configuration/@Bean methods sử dụng parameters thay vì tùy tiện direct calls.
- [ ] Event consumer không bắt producer inject nó.
- [ ] Event payload chứa đủ facts mà listener cần.
- [ ] Nếu event sync, exception semantics được định nghĩa.
- [ ] Boundary transaction và rollback được thiết kế theo data consistency.
- [ ] Nếu external events cần reliable delivery, có outbox/idempotency.
- [ ] Missing policy/bean làm context fail-fast.
- [ ] Test context refresh thành công cho cấu hình đúng.
- [ ] Positive và negative business tests tồn tại.
- [ ] Hai contexts được test không chia sẻ mutable singleton state.
- [ ] @Profile/@Conditional không vô tình loại bean ở deployment.
- [ ] Có diagnostic khi BeanCreationException/NoUnique xảy ra.
- [ ] Unit-of-money/rounding và overflow được quyết định.
- [ ] Concurrent duplicate order/reservation được kiểm thử nếu production yêu cầu.

## 38. Bài tập tự luyện

### Bài 1 — thay DiscountPolicy

Tạo `FixedDiscountPolicy` trả một số cents cố định nhưng không lớn hơn subtotal. Thay bean trong DiscountConfig. Vẽ graph mới và kiểm tra **số cạnh, hướng cạnh vẫn không đổi**; chỉ thay implementation strategy.

### Bài 2 — thêm PaymentPort

Thêm interface `PaymentPort.authorize(orderId,totalCents)` và adapter in-memory. Inject vào CheckoutService khi payment là bước bắt buộc. Vẽ mũi tên mới và chọn vị trí gọi theo failure semantics.

### Bài 3 — thêm listener metrics

Thêm `OrderMetricsListener` nghe `OrderPlaced` và bean `MetricsCounter`. Đảm bảo CheckoutService không thay constructor. Test event được xử lý bởi cả audit và metrics consumers.

### Bài 4 — cố ý tạo constructor cycle

Cho PricingService phụ thuộc ngược CheckoutService. Thực hiện context refresh trong negative JUnit test và quan sát `BeanCurrentlyInCreationException` trong cause chain. Sau đó sửa bằng truyền SKU/value object thay vì inject ngược.

### Bài 5 — thử hai profiles

Cung cấp DiscountPolicy demo=10% và prod=0%, đánh dấu `@Profile` cho hai configs. Chọn active profile trước refresh và test kết quả quote. Tránh bật cả hai profiles đồng thời nếu không có lựa chọn unique candidate.

### Bài 6 — thay repository bằng fake

Tạo interface OrderRepository, adapter fake có mode `failOnSave`. Chạy test để nhìn thấy stock reservation đã xảy ra nhưng save failed. Đây là bài học về transaction/compensating action, không phải lỗi bean wiring.

### Bài 7 — xây ứng dụng multi-module

Đặt policy contracts trong module domain, use case trong application và adapters trong infrastructure. Vẽ **module dependency graph** riêng, rồi so sánh với actual Spring bean injection graph.

### Bài 8 — kiểm tra event feedback

Tạo listener cố ý gọi checkout.place lại. Xác định vì sao có thể xuất hiện duplicate/event loop, sau đó thiết kế listener chỉ xử lý fact hoặc dùng read port hẹp.

## 39. Những sai lầm phổ biến

**Sai 1:** Mọi class phải là bean. Thực tế records, request inputs và domain value objects không cần singleton registration.

**Sai 2:** Vẽ mũi tên event delivery như constructor injection. Hai quan hệ khác nhau; có thể sai khi phân tích cycle.

**Sai 3:** Để PricingService inject CheckoutService chỉ để lấy SKU. Hãy truyền input hoặc dùng ProductCatalog độc lập.

**Sai 4:** Gọi trực tiếp các `@Bean` methods với `proxyBeanMethods=false` và tưởng được cùng singleton bean. Hãy dùng method parameter DI.

**Sai 5:** Coi một interface tự loại circular dependencies. Nếu implementation vẫn inject ngược orchestrator, cycle vẫn tồn tại.

**Sai 6:** Đặt orderId/quantity vào mutable fields của singleton service. Dữ liệu mỗi request phải nằm trong parameters/local values.

**Sai 7:** Dùng event để thay mọi command. Payment validation cần result ngay có thể không phù hợp event.

**Sai 8:** Tưởng `ApplicationEventPublisher` mặc định async/durable. Default delivery thường synchronous và in-process.

**Sai 9:** Coi `synchronized` từng method là transaction giữa nhiều beans. Không, cross-bean atomicity vẫn cần thiết kế riêng.

**Sai 10:** Chỉ test `new CheckoutService(...)` mà không có Spring context test. Unit test không phát hiện missing `DiscountPolicy` bean.

**Sai 11:** Đếm toàn bộ BeanDefinitions và kỳ vọng đúng số domain nodes. Spring có internal infrastructure registrations.

**Sai 12:** Dùng `@Lazy` làm giải pháp thiết kế mọi vòng, dẫn đến lỗi chỉ xuất hiện khi request chạy.

**Sai 13:** Bật `@ComponentScan` toàn app để sửa một bean thiếu, kéo theo dependencies và candidates không liên quan.

**Sai 14:** Dùng Java `double` cho money mà không quy định rounding và currency.

**Sai 15:** Coi graph không vòng là đủ cho production. Còn transaction, retry, concurrency, security và observability.

## 40. Quy trình áp dụng vào một yêu cầu nghiệp vụ khác

1. Viết happy path và error paths bằng dữ liệu cụ thể.
2. Liệt kê actors, external systems, business capabilities.
3. Chỉ định một application service orchestrator.
4. Trích domain policies/stateless services.
5. Chọn ports/adapters cho I/O bên ngoài.
6. Tách value objects khỏi Spring beans.
7. Vẽ directed required constructor edges.
8. Tìm cycles hoặc reverse-layer dependencies.
9. Chọn scopes và mutable state ownership.
10. Quyết định synchronous calls hay event notification.
11. Ghi transaction boundary/failure semantics.
12. Chọn `@Configuration` classes/`@Bean` methods.
13. Dùng constructor/method parameter DI.
14. Tạo minimal ApplicationContext.
15. Test context refresh; test missing dependency.
16. Test positive/negative business state transitions.
17. Test isolation và lifecycle.
18. Ghi chú những production concerns chưa được giải quyết.
19. Review graph sau khi thêm feature.
20. Chỉ tăng kiến trúc complexity khi có yêu cầu thực.

## 41. Tóm tắt kiến thức cốt lõi

**Đỉnh** là bean do Spring quản lý; **cạnh** là dependency từ consumer đến provider; **DAG** nghĩa là required constructor dependency graph không có chu trình.

**Application service** nên điều phối, **policies** độc lập với orchestrator, **repositories/adapters** quản lý I/O, và **value objects** truyền dữ liệu trong runtime method calls.

**@Bean parameters** biểu diễn những cạnh này rất rõ. **@Configuration(proxyBeanMethods=false)** hợp lý khi không phụ thuộc direct inter-bean method calls. **AnnotationConfigApplicationContext** cho phép kiểm tra thật một graph nhỏ mà không cần full application.

**Event publisher/listener** giúp producer không inject concrete consumers, nhưng không xóa nhu cầu thiết kế exception, transaction và runtime loops.

**Singleton** là scope trên mỗi BeanFactory, không phải singleton toàn JVM, và mutable singleton state đòi hỏi concurrency design riêng.

## 42. Tổng kết bài thực hành

Từ yêu cầu “đặt hai BOOK”, ta suy ra những beans cần thiết, vẽ edges, xác định leaves, rồi implement bằng Java configuration. Chương trình chạy và tests xác nhận:

- Đúng chính sách 10%: 20.000 subtotal → 18.000 total.
- Đúng tồn kho: 5 → 3.
- Một đơn được lưu.
- Audit listener xử lý OrderPlaced event.
- Không lưu đơn khi tồn kho thiếu hoặc SKU sai.
- Không nhận trùng order ID trong demo đơn luồng.
- Thiếu DiscountPolicy gây fail-fast khi context refresh.
- Singleton state độc lập giữa hai ApplicationContexts.

Đây là kết quả của **một bean graph được thiết kế trước**, không phải ngẫu nhiên đặt annotation lên mọi class. Khi mở rộng thực tế, giữ graph sạch và bổ sung transaction/ports/observability phù hợp thay vì nhồi mọi logic vào CheckoutService.

## 43. Tài liệu chính thức

- [Spring Framework Reference — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html): `@Configuration`, `@Bean` và bootstrap Java config.
- [Spring Framework Reference — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html): Bean method parameter injection, scope và lifecycle.
- [Spring Framework Reference — Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html): constructor/setter injection, circular dependencies.
- [Spring Framework 6.1 — AnnotationConfigApplicationContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): register/refresh/close behavior.
- [Spring Framework Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype và các scopes khác.
- [Spring Framework Reference — Application Events](https://docs.spring.io/spring-framework/reference/6.1/core/beans/context-introduction.html): event publishing và annotated listeners.
- [Spring Framework 6.1 — ApplicationEventPublisher Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html): event publication contract.
- [Spring Framework 6.1 — EventListener Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/EventListener.html): type-based application events.
- [Spring Framework Reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.1/data-access/transaction/event.html): `@TransactionalEventListener` khi có transaction manager phù hợp.
- [Spring Framework Reference — Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html): explicit initialization dependencies và limitations.
- [Spring Framework 6.1 — ConfigurableListableBeanFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/beans/factory/config/ConfigurableListableBeanFactory.html): inspecting bean metadata và dependencies.
- [JUnit 5 User Guide](https://docs.junit.org/5.11.1/user-guide/): test cases, assertions và exception testing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-002">Thay thế cách triển khai bằng cấu hình và qualifier</a>

<details>
<summary>Xem chi tiết</summary>

# Thay thế cách triển khai bằng cấu hình và qualifier trong Spring Core Container

## 1. Bài toán thực hành: giữ nguyên mã nghiệp vụ, thay implementation qua DI

Giả sử một ứng dụng có chức năng gửi thông báo khi đơn hàng hoàn tất. Trong quá trình phát triển ta gửi bằng `EmailSender`, nhưng một môi trường khác muốn dùng `SmsSender`, hoặc khi chạy thử nghiệm cần một `FakeSender`. Nếu `CheckoutService` trực tiếp viết `new EmailSender()`, muốn chuyển qua SMS phải sửa mã nghiệp vụ, biên dịch lại service và thay đổi các tests phụ thuộc.

**Dependency Injection (DI)** trong Spring cho phép tách:

- **Contract**: interface `NotificationSender` mô tả khả năng gửi.
- **Implementations**: `EmailSender`, `SmsSender`, hoặc adapter thật.
- **Wiring configuration**: Spring `@Configuration`, `@Bean`, `@Qualifier`, `@Primary`, `@Fallback`, `@Profile` hoặc một giá trị `Environment` quyết định dependency nào sẽ được cung cấp.
- **Consumer**: `CheckoutService` chỉ biết `NotificationSender`, không cần biết class triển khai.

```text
                 +---------------------------+
                 |     CheckoutService       |
                 | depends on interface      |
                 |   NotificationSender      |
                 +-------------+-------------+
                               ^
                               |
                     constructor injection
                               |
                   Spring configuration decides
                       /                 \
                EmailSender          SmsSender
```

**Mục tiêu bài tập:** Thay implementation **mà không sửa mã của consumer**; kiểm tra lựa chọn đúng bean; giải thích cơ chế chọn ứng viên; biết cách xử lý khi hai beans cùng type; và không nhầm việc chọn bean ở lúc **khởi tạo context** với chuyển đổi động theo **từng request**.

Spring Framework Core 6.2 được dùng làm mốc để có `@Fallback` (được giới thiệu trong 6.2), nhưng phần lớn cách dùng `@Configuration`, `@Bean`, `@Qualifier`, `@Primary` đã tồn tại từ những nhánh trước.

## 2. Thiết kế trước và sau refactoring

### 2.1. Trước: consumer tự quyết định implementation

```java
public final class CheckoutService {
    private final EmailSender sender = new EmailSender();

    public String checkout(String orderId) {
        return sender.send(orderId);
    }
}
```

Đây là **snippet minh họa**, cần class `EmailSender`. Nó chứa hai vấn đề:

1. **Tight coupling**: consumer phụ thuộc class cụ thể.
2. **Không test-isolated dễ dàng**: muốn dùng fake implementation phải sửa source hoặc thêm seam khác.

Khi chuyển sang SMS, service bị sửa trực tiếp và có nguy cơ gây regression.

### 2.2. Sau: dependency inversion + constructor injection

```java
public interface NotificationSender {
    String send(String orderId);
}

public final class CheckoutService {
    private final NotificationSender sender;

    public CheckoutService(NotificationSender sender) {
        this.sender = sender;
    }

    public String checkout(String orderId) {
        return sender.send(orderId);
    }
}
```

Đây là **snippet tích hợp** gồm interface/consumer, chưa bao gồm implementations/registration. `CheckoutService` chỉ phụ thuộc contract; quyết định triển khai ở composition root.

**Ưu điểm:** Có thể thay `EmailSender` thành `SmsSender` hoặc fake bằng cách cấu hình BeanFactory, không sửa `CheckoutService`. Nếu interface không thay đổi, mã nghiệp vụ vẫn compile.

### 2.3. Constructor injection được ưu tiên

Constructor injection phù hợp dependency **bắt buộc**:

- Có thể đánh dấu field `final`.
- Không tạo object ở trạng thái thiếu dependency.
- Có thể unit test bằng `new CheckoutService(fakeSender)`, không phải khởi động Spring.
- Phát hiện vòng phụ thuộc bắt buộc sớm thay vì dựa vào early singleton references.
- Phụ thuộc hiển thị rõ trong API của consumer.

Với component chỉ có một constructor, Spring thường không yêu cầu `@Autowired` trên constructor. Tuy nhiên, `@Qualifier` trên tham số vẫn hữu ích để thu hẹp ứng viên.

## 3. Đăng ký hai implementations cùng interface

```java
@Configuration(proxyBeanMethods = false)
class SenderConfig {

    @Bean("emailSender")
    NotificationSender emailSender() {
        return new EmailSender();
    }

    @Bean("smsSender")
    NotificationSender smsSender() {
        return new SmsSender();
    }
}
```

Đây là **snippet tích hợp**, cần `NotificationSender`, `EmailSender`, `SmsSender` và Spring imports.

Sau registration, container có:

| Bean name | Declared type | Runtime implementation |
|---|---|---|
| `emailSender` | `NotificationSender` | `EmailSender` |
| `smsSender` | `NotificationSender` | `SmsSender` |

Hai bean names khác nhau nên **không có bean definition override conflict**. Nhưng vì có **hai candidates cùng type**, một injection không nêu lựa chọn có thể trở nên mơ hồ.

### 3.1. @Bean name không phải Java method name khi khai báo tường minh

Trong ví dụ:

```java
@Bean("emailSender")
NotificationSender createEmailAdapter() {
    return new EmailSender();
}
```

Tên bean là **`emailSender`**, không phải `createEmailAdapter`. Nếu không đặt `@Bean("...")`, mặc định tên bean là tên method. Việc đặt names rõ ràng giúp wiring dễ hiểu và giảm nguy cơ đổi tên Java method làm thay injection theo name fallback.

### 3.2. Dùng @Component và @Service có khác không?

Nếu các implementations do Spring tự scan:

```java
@Component("emailSender")
public class EmailSender implements NotificationSender {
    // ...
}

@Component("smsSender")
public class SmsSender implements NotificationSender {
    // ...
}
```

Đây là snippets từ **hai files độc lập**, cần methods `send` để implements interface. Điểm khác nằm ở **registration path**: component scanning tạo bean definitions từ annotated classes, còn `@Bean` factory methods cho phép tạo instances tường minh, đặc biệt hữu ích với third-party library classes không thể annotate.

**Không nên đồng thời `@Component` và `@Bean` cho cùng object mà không hiểu duplication**, vì có thể tạo hai beans cùng type hoặc xung đột names.

## 4. @Qualifier: thu hẹp ứng viên theo ngữ nghĩa

Nếu một constructor nhận `NotificationSender`:

```java
public CheckoutService(NotificationSender sender) {
    this.sender = sender;
}
```

Spring ban đầu có nhiều candidates cùng type; nếu không có primary/fallback/qualifier/name resolution phù hợp, context có thể gặp `NoUniqueBeanDefinitionException`, thường được wrap trong `UnsatisfiedDependencyException`.

Để chọn email:

```java
public CheckoutService(
        @Qualifier("emailSender") NotificationSender sender) {
    this.sender = sender;
}
```

Đây là constructor snippet tích hợp, cần import `org.springframework.beans.factory.annotation.Qualifier`. Để chọn SMS, thay **qualifier ở composition/wiring point** bằng `@Qualifier("smsSender")`. Trong thiết kế tốt, không phải sửa consumer mỗi khi muốn đổi lựa chọn triển khai; hãy để `@Configuration` method lựa chọn thay.

### 4.1. Qualifier không đơn thuần là getBean(name)

Theo Spring Reference, `@Autowired` có **ngữ nghĩa dựa trên type**, và `@Qualifier` giúp **lọc trong tập candidates theo type**. Một qualifier string có thể match bean name như fallback, nhưng cũng có thể là **nhãn ngữ nghĩa** dùng chung cho nhiều beans.

```java
@Bean("mailProvider")
@Qualifier("outbound")
NotificationSender emailSender() {
    return new EmailSender();
}

@Bean("textProvider")
@Qualifier("outbound")
NotificationSender smsSender() {
    return new SmsSender();
}
```

Đây là snippets tích hợp. Nếu inject:

```java
public AuditGroup(
        @Qualifier("outbound") List<NotificationSender> senders) {
    this.senders = senders;
}
```

thì **cả hai** có thể match nhãn `outbound`. Qualifier không nhất thiết unique. Khi inject một `NotificationSender` duy nhất với `@Qualifier("outbound")`, vẫn có thể ambiguous nếu có hai candidates.

### 4.2. Bean name fallback và qualifier

Một bean có tên `emailSender` có thể match `@Qualifier("emailSender")`, dù `@Bean` method không có annotation `@Qualifier` tương ứng. Nhưng `@Qualifier` **không bỏ qua type matching**: nếu name trùng nhưng object type không assignable cho injection type, Spring không biến nó thành đúng type nhờ string.

Để inject tuyệt đối bằng tên, `@Resource(name = "emailSender")` (Jakarta Common Annotations) có semantics khác; đừng thay `@Qualifier` bằng `@Resource` mà không đánh giá type, proxy và dependency rules.

### 4.3. Dùng @Qualifier trên @Bean factory method parameters

```java
@Bean
CheckoutService checkoutService(
        @Qualifier("smsSender") NotificationSender sender) {
    return new CheckoutService(sender);
}
```

Đây là snippet bên trong `@Configuration`. Nó minh họa cách thay implementation **chỉ bằng thay wiring configuration**, trong khi class `CheckoutService` giữ nguyên và không biết bean name.

Đây là approach ưu tiên trong bài thực hành tổng hợp khi cần sản phẩm dễ kiểm thử, ít framework coupling trong domain layer.

## 5. Ba cách thay implementation mà không sửa consumer

### Cách A — Chọn qua một @Configuration class

```java
@Configuration(proxyBeanMethods = false)
@Import(SenderConfig.class)
class EmailWiringConfig {

    @Bean
    CheckoutService checkoutService(
            @Qualifier("emailSender") NotificationSender sender) {
        return new CheckoutService(sender);
    }
}
```

Một configuration tương tự:

```java
@Configuration(proxyBeanMethods = false)
@Import(SenderConfig.class)
class SmsWiringConfig {

    @Bean
    CheckoutService checkoutService(
            @Qualifier("smsSender") NotificationSender sender) {
        return new CheckoutService(sender);
    }
}
```

Hai configs là **hai lựa chọn thay thế**, không nên đăng ký đồng thời nếu cả hai khai báo bean `checkoutService` cùng name. Mỗi context chỉ chọn một composition root phù hợp.

Dùng cách này khi lựa chọn **mang tính build/deployment**, được kiểm soát bởi module cấu hình hoặc testing scenario.

### Cách B — Một configuration đọc property lúc startup

```java
@Bean
CheckoutService checkoutService(
        Environment environment,
        @Qualifier("emailSender") NotificationSender email,
        @Qualifier("smsSender") NotificationSender sms) {

    String channel =
            environment.getRequiredProperty("delivery.channel");

    NotificationSender chosen = switch (channel) {
        case "email" -> email;
        case "sms" -> sms;
        default -> throw new IllegalArgumentException(
                "Unsupported channel: " + channel);
    };

    return new CheckoutService(chosen);
}
```

Đây là **snippet tích hợp** trong một `@Configuration` chứa hai candidates và import `org.springframework.core.env.Environment`.

Ở runtime startup, `checkoutService` được tạo với implementation đã được lựa chọn. Nếu đổi `delivery.channel` trong một source config **sau khi singleton đã tạo**, container **không tự inject lại** một sender khác. Với yêu cầu thay implementation theo deployment, đây là thiết kế tốt; với yêu cầu thay **theo từng request**, cần một runtime strategy/router khác.

**Ưu điểm:** Consumer không biết `Environment` hoặc `@Qualifier`; configuration là chỗ duy nhất diễn đạt lựa chọn. Hai implementations vẫn có thể được tạo sớm nếu là eager singletons, nên cân nhắc chi phí resource khi mỗi implementation là HTTP client/DB adapter.

### Cách C — @Profile để chọn một BeanDefinition

```java
@Configuration(proxyBeanMethods = false)
class ProfileSenderConfig {

    @Bean("notificationSender")
    @Profile("email")
    NotificationSender emailSender() {
        return new EmailSender();
    }

    @Bean("notificationSender")
    @Profile("sms")
    NotificationSender smsSender() {
        return new SmsSender();
    }
}
```

Đây là **snippet tích hợp**, cần imports/types. Khi chỉ profile `email` active, chỉ email variant được chọn; khi chỉ `sms` active, chỉ SMS variant được chọn.

**Bẫy quan trọng:** `@Profile("email")` và `@Profile("sms")` không mặc định loại trừ nhau. Nếu cả hai active, cùng bean name `notificationSender` có thể conflict/override tùy policy và registration path. Nếu không profile nào active, **không có** notificationSender (trừ khi có default). Test tất cả combinations, đừng mặc định “một trong hai luôn tồn tại”.

### 5.1. @Profile không nhất thiết thay đổi tên BeanDefinition

Bạn có thể chọn cùng tên `notificationSender` khi conditions mutually exclusive, để consumer inject không phải biết email/SMS. Ngược lại, có thể giữ names riêng cho cả hai beans rồi chọn bằng qualifier/property. Hai thiết kế có trade-offs khác nhau:

- **Một bean name và mutually exclusive conditions**: runtime context chỉ có một variant; phù hợp deployment-specific infrastructure.
- **Hai bean names, composition chọn một**: cả hai beans có thể tồn tại; phù hợp tests, canary routing, strategy registry.
- **Map/router**: chọn theo request mà không rebuild context; phù hợp dynamic behavior.

## 6. @Primary và @Fallback: default candidate, không phải dynamic switch

Nếu có nhiều beans cùng interface và nhiều consumers chỉ muốn **một default**, `@Primary` là cách đơn giản:

```java
@Bean("emailSender")
@Primary
NotificationSender emailSender() {
    return new EmailSender();
}

@Bean("smsSender")
NotificationSender smsSender() {
    return new SmsSender();
}
```

Khi inject unqualified `NotificationSender` và có đúng một primary trong candidates, Spring chọn `emailSender`. Nhưng một injection `@Qualifier("smsSender")` vẫn được phép chọn SMS. Primary chỉ ảnh hưởng candidate selection cho **single-valued injection** khi nhiều candidates; khi inject collection/array/map, beans tương ứng vẫn có mặt.

### 6.1. @Fallback (Spring Framework 6.2+)

```java
@Bean("emailSender")
NotificationSender emailSender() {
    return new EmailSender();
}

@Bean("smsSender")
@Fallback
NotificationSender smsSender() {
    return new SmsSender();
}
```

Với Spring 6.2, nếu tất cả matching candidates ngoại trừ một bean được đánh dấu fallback, bean không fallback được ưu tiên như primary về mặt effective selection. Một explicit qualifier vẫn có thể chọn fallback bean.

**Không dùng @Fallback trong source phải biên dịch với Spring 6.1**, vì annotation này được giới thiệu từ 6.2. `@Primary` và `@Fallback` là **metadata chọn ứng viên**, không làm đổi implementation của một object đã inject xong.

### 6.2. @Bean(defaultCandidate = false) từ 6.2

```java
@Bean(value = "internalSmsSender", defaultCandidate = false)
NotificationSender internalSmsSender() {
    return new SmsSender();
}
```

Đây là snippet tích hợp Spring 6.2+. Bean này không được xem là normal candidate cho **plain type injection** khi chưa có dấu hiệu bổ sung; nhưng có thể được chọn ở injection point có qualifier phù hợp. Nó **khác** `@Bean(autowireCandidate = false)`, vốn loại bean khỏi by-type autowiring rộng hơn.

Dùng cho một restricted implementation không nên cạnh tranh với default mà vẫn cần sẵn để một consumer được chỉ định rõ.

### 6.3. Thứ tự ưu tiên không phải quy tắc “@Qualifier thắng @Primary” cho mọi trường hợp

Cách hiểu đúng là Spring **lọc type và qualifier phù hợp**, xét primary/fallback/default-candidate/name metadata và các lựa chọn khác theo algorithm. Với injection point có `@Qualifier("smsSender")`, nếu chỉ SMS match qualifier, `@Primary` trên Email không làm Email trở thành SMS.

Không viết một “bảng ưu tiên tuyệt đối” áp cho mọi corner case gồm generics, custom qualifier annotations và collection injections. Luôn test outcome cụ thể với Spring version đã pin.

## 7. Ví dụ Java 17 + Spring Framework 6.2 chạy độc lập

Chương trình demo dưới đây cho thấy **bảy kết quả**:

1. `EmailWiring` chọn Email bằng `@Qualifier`.
2. `SmsWiring` chọn SMS mà **không sửa class CheckoutFlow**.
3. Một config đọc property `delivery.channel=email`.
4. Cùng config đọc `delivery.channel=sms`.
5. `@Primary` chọn Email khi consumer không ghi qualifier.
6. `@Fallback` cho SMS làm Email trở thành regular candidate được chọn.
7. Nếu hai beans cùng type nhưng không có qualifier/primary/fallback và tên injection point không match bean, Spring từ chối **ambiguous dependency**.

Ví dụ dùng `AnnotationConfigApplicationContext` thuần, không dùng Boot, không mở network, không chạy email/SMS thật.

### 7.1. Cấu trúc thư mục

```text
sender-switch-demo/
├── pom.xml
└── src/main/java/example/SenderSwitchDemo.java
```

JDK 17+, Maven 3.9+.

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>sender-switch-demo</artifactId>
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
```

### 7.3. src/main/java/example/SenderSwitchDemo.java

```java
package example;

import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

public final class SenderSwitchDemo {

    public static void main(String[] args) {
        run("qualifier-email", EmailWiring.class, "email");
        run("qualifier-sms", SmsWiring.class, "sms");

        run("property-email", PropertyWiring.class, "email");
        run("property-sms", PropertyWiring.class, "sms");

        run("primary", PrimaryWiring.class, "email");
        run("fallback", FallbackWiring.class, "email");

        checkAmbiguous();
    }

    private static void run(
            String label, Class<?> config, String channel) {

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "demo",
                            Map.of("delivery.channel", channel)));

            context.register(config);
            context.refresh();

            CheckoutFlow checkout = context.getBean(CheckoutFlow.class);

            if (context.getBeanNamesForType(Sender.class).length != 2) {
                throw new AssertionError("Expected exactly two Sender beans");
            }

            System.out.println(
                    label + "=" + checkout.complete("invoice-9"));
        }
    }

    private static void checkAmbiguous() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AmbiguousWiring.class);
            try {
                context.refresh();
                throw new AssertionError(
                        "Expected dependency ambiguity");
            } catch (BeansException ex) {
                if (!hasCause(ex, NoUniqueBeanDefinitionException.class)) {
                    throw new AssertionError(
                            "Wrong exception family", ex);
                }
                System.out.println(
                        "ambiguous=NoUniqueBeanDefinitionException");
            }
        }
    }

    private static boolean hasCause(
            Throwable failure, Class<?> expected) {

        for (Throwable cur = failure;
                cur != null; cur = cur.getCause()) {
            if (expected.isInstance(cur)) {
                return true;
            }
        }
        return false;
    }

    public interface Sender {
        String deliver(String invoiceId);
    }

    public static final class EmailSender implements Sender {
        @Override
        public String deliver(String invoiceId) {
            return "EMAIL:" + invoiceId;
        }
    }

    public static final class SmsSender implements Sender {
        @Override
        public String deliver(String invoiceId) {
            return "SMS:" + invoiceId;
        }
    }

    public static final class CheckoutFlow {
        private final Sender sender;

        public CheckoutFlow(Sender sender) {
            this.sender = sender;
        }

        public String complete(String invoiceId) {
            return sender.deliver(invoiceId);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Candidates {
        @Bean("emailSender")
        public Sender emailSender() {
            return new EmailSender();
        }

        @Bean("smsSender")
        public Sender smsSender() {
            return new SmsSender();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Candidates.class)
    public static class EmailWiring {
        @Bean
        public CheckoutFlow checkoutFlow(
                @Qualifier("emailSender") Sender sender) {
            return new CheckoutFlow(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Candidates.class)
    public static class SmsWiring {
        @Bean
        public CheckoutFlow checkoutFlow(
                @Qualifier("smsSender") Sender sender) {
            return new CheckoutFlow(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Candidates.class)
    public static class PropertyWiring {
        @Bean
        public CheckoutFlow checkoutFlow(
                Environment environment,
                @Qualifier("emailSender") Sender email,
                @Qualifier("smsSender") Sender sms) {

            String channel =
                    environment.getRequiredProperty("delivery.channel");
            Sender chosen = switch (channel) {
                case "email" -> email;
                case "sms" -> sms;
                default -> throw new IllegalArgumentException(
                        "Unsupported channel: " + channel);
            };
            return new CheckoutFlow(chosen);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryWiring {
        @Bean("emailSender")
        @Primary
        public Sender emailSender() {
            return new EmailSender();
        }

        @Bean("smsSender")
        public Sender smsSender() {
            return new SmsSender();
        }

        @Bean
        public CheckoutFlow checkoutFlow(Sender sender) {
            return new CheckoutFlow(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackWiring {
        @Bean("emailSender")
        public Sender emailSender() {
            return new EmailSender();
        }

        @Bean("smsSender")
        @Fallback
        public Sender smsSender() {
            return new SmsSender();
        }

        @Bean
        public CheckoutFlow checkoutFlow(Sender sender) {
            return new CheckoutFlow(sender);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(Candidates.class)
    public static class AmbiguousWiring {
        @Bean
        public CheckoutFlow checkoutFlow(Sender sender) {
            return new CheckoutFlow(sender);
        }
    }
}
```

### 7.4. Chạy

Tại thư mục `sender-switch-demo`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.SenderSwitchDemo
```

**Stdout dự kiến do chương trình in** (Spring có thể ghi warning khi `AmbiguousWiring` refresh thất bại):

```text
qualifier-email=EMAIL:invoice-9
qualifier-sms=SMS:invoice-9
property-email=EMAIL:invoice-9
property-sms=SMS:invoice-9
primary=EMAIL:invoice-9
fallback=EMAIL:invoice-9
ambiguous=NoUniqueBeanDefinitionException
```

### 7.5. Giải thích hai trường hợp qualifier

`EmailWiring` và `SmsWiring` import cùng `Candidates`, nên mỗi context có hai `Sender` beans. Tuy nhiên `checkoutFlow` bean được tạo qua factory method chỉ nhận một candidate khớp `@Qualifier("emailSender")` hoặc `@Qualifier("smsSender")`.

Class `CheckoutFlow` **không hề thay đổi** giữa hai executions. Đó là trọng tâm của bài thực hành: **đổi wiring thay vì sửa business code**.

### 7.6. Giải thích property-based selection

`PropertyWiring` được sử dụng hai lần nhưng mỗi lần tạo một context mới. Trước `refresh()`, demo thêm `MapPropertySource` với `delivery.channel=email` hoặc `sms`. Factory method resolve cả hai bean candidates bằng qualifiers, sau đó switch chọn reference nào sẽ được truyền vào `CheckoutFlow`.

**Không suy diễn** rằng thay property ở context đã refresh khiến singleton CheckoutFlow đổi sender. Nó giữ constructor reference đã chọn tại creation time.

### 7.7. Giải thích @Primary và @Fallback

Trong `PrimaryWiring`, Email có `@Primary`, nên `CheckoutFlow(Sender sender)` chọn Email. Trong `FallbackWiring`, SMS mang `@Fallback`, chỉ còn Email là regular candidate nên Email được chọn.

Cả hai contexts vẫn có hai beans assignable cho `Sender`. `@Primary`/`@Fallback` **không xóa bean còn lại**; nếu inject `List<Sender>` hoặc `Map<String, Sender>`, các beans matching vẫn có thể hiện diện.

### 7.8. Giải thích lỗi ambiguous

`AmbiguousWiring` import `Candidates` (email + sms), nhưng `checkoutFlow(Sender sender)` không có qualifier; không có primary/fallback. Vì parameter name `sender` cũng không match hai bean names, context không chọn được một candidate. Root cause là `NoUniqueBeanDefinitionException`, thường được wrap trong `UnsatisfiedDependencyException`/ `BeanCreationException`.

Demo kiểm tra **nested cause**, không hardcode outer exception type hay full exception message, giúp kiểm thử chính xác ý nghĩa hơn.

### 7.9. Vì sao demo dùng AnnotationConfigApplicationContext?

Đây là Spring Framework Core thuần, không có auto-configuration hoặc starter-specific property binding. Các ví dụ hoạt động dựa trên:
- `@Configuration` + `@Import` + `@Bean`;
- Constructor/factory-method argument resolution;
- `@Qualifier`, `@Primary`, `@Fallback`;
- `Environment` và `MapPropertySource`.

Nếu chuyển code sang Spring Boot, những primitive DI vẫn là Spring Framework APIs; Boot thêm configuration, dependency management và auto-configuration.

## 8. Viết JUnit tests để xác nhận implementation được chọn

Một test chỉ assert `context.refresh()` không ném exception **không đủ**: app có thể chạy với sai sender. Cần assert **hành vi cụ thể** hoặc object identity đúng của implementation.

### 8.1. Unit test không cần Spring

```java
Sender fake = id -> "FAKE:" + id;
CheckoutFlow checkout = new CheckoutFlow(fake);
assertEquals("FAKE:invoice-9", checkout.complete("invoice-9"));
```

Đây là **JUnit-style snippet**, cần các types từ demo và static import `assertEquals`. Vì `Sender` có một abstract method, nó là functional interface hợp lệ để lambda implement.

Unit test này chứng minh consumer không phụ thuộc framework hoặc implementation thật. Đây là lợi ích trực tiếp của constructor injection.

### 8.2. Integration test thực sự kiểm tra wiring

```java
@Test
void smsConfigurationSelectsSmsSender() {
    try (var context = new AnnotationConfigApplicationContext()) {
        context.register(SmsWiring.class);
        context.refresh();

        CheckoutFlow checkout = context.getBean(CheckoutFlow.class);
        assertEquals("SMS:test-1", checkout.complete("test-1"));
    }
}
```

Đây là JUnit 5 snippet, cần JUnit dependency/imports và demo classes. Chạy một context tách biệt giúp không bị cấu hình khác của test suite chen vào.

### 8.3. Test fail-fast cho value không hợp lệ

```java
context.getEnvironment().getPropertySources().addFirst(
    new MapPropertySource("test",
        Map.of("delivery.channel", "telegram")));
context.register(PropertyWiring.class);
assertThrows(BeansException.class, context::refresh);
```

Đây là snippet trong JUnit test với context mới và imports. Không khẳng định outer exception luôn là đúng một subtype; `IllegalArgumentException` từ switch có thể bị Spring wrap. Tìm root cause `Unsupported channel` để chứng minh fail-fast đúng thay vì vô tình thất bại do missing dependency.

### 8.4. Test đủ các profile combinations

Nếu dùng `@Profile("email")` và `@Profile("sms")` trên hai alternatives, thử:
- Chỉ `email` active.
- Chỉ `sms` active.
- Cả `email` và `sms` active.
- Không profile nào active.

Expected behavior phải được **thiết kế cụ thể** cho cả bốn cases. Không test duy nhất happy-path và giả định hai profiles tự loại trừ.

### 8.5. Kiểm tra cả bean identity khi cần

```java
Sender injected = context.getBean(CheckoutFlow.class).sender();
Sender expected = context.getBean("smsSender", Sender.class);
assertSame(expected, injected);
```

Đây là **snippet khái niệm**, yêu cầu `CheckoutFlow` expose method `sender()` chỉ trong test adapter hoặc test-support view; demo production class cố ý không cung cấp getter này. Với AOP proxies, assert **exposed bean identity** hoặc behavior, không giả định raw target object chính là Spring proxy.

## 9. Phân biệt việc chọn khi startup và runtime routing

### 9.1. Startup selection (bài này)

```text
Config chooses SMS
        |
        v
new CheckoutFlow(smsSender)
        |
        v
CheckoutFlow giữ reference SMS
        |
        v
Mọi call tiếp theo trên instance đó -> SMS
```

Đổi property trong `Environment` sau khi bean được dựng không tự thay constructor dependency.

### 9.2. Runtime routing theo từng request

Nếu mỗi request cần chọn implementation khác nhau, một resolver/strategy registry có thể phù hợp:

```java
public final class SenderRouter {
    private final Map<String, Sender> senders;

    public SenderRouter(Map<String, Sender> senders) {
        this.senders = Map.copyOf(senders);
    }

    public String deliver(String channel, String invoiceId) {
        Sender selected = senders.get(channel);
        if (selected == null) {
            throw new IllegalArgumentException(
                "Unknown sender: " + channel);
        }
        return selected.deliver(invoiceId);
    }
}
```

Đây là snippet tích hợp với `Map` import. Để dùng trực tiếp Spring `Map<String, Sender>` injection, Spring thường cung cấp map với **keys là bean names**. Nếu muốn keys `email` và `sms` thay vì `emailSender` và `smsSender`, cần tạo map riêng:

```java
@Bean
SenderRouter router(
        @Qualifier("emailSender") Sender email,
        @Qualifier("smsSender") Sender sms) {
    return new SenderRouter(Map.of("email", email, "sms", sms));
}
```

Đây là snippet tích hợp trong `@Configuration`, cần `SenderRouter`, imports và các beans. Nó giúp chọn implementation mỗi lần gọi mà **không tạo lại context**.

### 9.3. Chọn chiến lược phù hợp

| Yêu cầu | Thiết kế |
|---|---|
| Dev dùng fake, prod dùng adapter thật | `@Profile` hoặc config riêng với conditions exclusive |
| Mỗi deployment chọn email hay SMS | `Environment` property + composition root |
| Một implementation mặc định, vẫn hỗ trợ explicit alternative | `@Primary` hoặc `@Fallback` |
| Một consumer cần chính xác một implementation | `@Qualifier` trên injection point/composition method |
| Cần tất cả implementations | `List<Sender>` hoặc `Map<String,Sender>` |
| Chọn theo từng order/request | Router/strategy, không phải thay @Qualifier runtime |
| Third-party class không annotate | `@Bean` factory method |
| Test thay real adapter bằng fake | Constructor injection cho unit test; test-specific `@Bean`/Spring Test override cho integration test |
| Plugin implementations xuất hiện và biến mất runtime | Thiết kế registry/lifecycle riêng; tránh phụ thuộc static singleton wiring |

## 10. @Profile, @Conditional và Spring Boot khác Spring Core thế nào?

`@Profile` và `@Conditional` thuộc Spring Framework. Trong Spring Boot còn có tiện ích như `@ConditionalOnProperty`, `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConfigurationProperties`.

Ví dụ Boot-only:

```java
@Bean
@ConditionalOnProperty(
    prefix = "delivery",
    name = "channel",
    havingValue = "sms")
Sender smsSender() {
    return new SmsSender();
}
```

Snippet này cần **Spring Boot auto-configure dependency** và imports thích hợp; chỉ dùng `spring-context` không có `@ConditionalOnProperty`. Nếu muốn hai variants mutually exclusive, phải kiểm tra condition cho Email variant và trường hợp property missing, không chỉ annotate SMS rồi để Email luôn đăng ký.

### 10.1. Boot properties và Spring Core Environment

Với Spring Boot, giá trị `delivery.channel` có thể đến từ `application.yml`, `application.properties`, environment variables hoặc command-line arguments theo property-source precedence.

Với plain `AnnotationConfigApplicationContext` như demo, **không có Boot automatic application.yml loader**. Ta chủ động cung cấp `MapPropertySource`. Nếu cần đọc file trong Spring Core, có thể dùng `@PropertySource("classpath:delivery.properties")` khi phù hợp; YAML cần cơ chế property-source loader riêng.

### 10.2. Spring AOT và runtime property changes

Với Spring AOT, những điều kiện `@Profile`/`@Conditional` quyết định **bean presence** có thể được đánh giá ở build time. Artifact AOT đã build với profile Email không tự sinh lại SMS bean đã bị bỏ khi đổi profile runtime.

Ngược lại, một **stable bean graph** chứa hai implementations và một runtime router có thể thay đường gọi theo config lúc chạy nếu router được thiết kế như vậy. Đừng dùng AOT-optimized conditional registration như một dynamic feature flag.

### 10.3. @Conditional chỉ chọn bean, không validate mọi business setting

Nếu application require một trong hai channels, config nên fail fast khi value không hợp lệ. Không lặng lẽ fallback sang Email khi user gõ `smss`, vì gửi sai kênh có thể dẫn tới lỗi vận hành hoặc rò thông tin. Cho default chỉ khi requirement định nghĩa rõ.

## 11. Các lỗi thường gặp và cách nhận diện

| Triệu chứng | Nguyên nhân | Khắc phục |
|---|---|---|
| `NoUniqueBeanDefinitionException` | Có nhiều beans cùng type, injection không phân biệt được | `@Qualifier`, `@Primary`, `@Fallback`, config selection |
| `NoSuchBeanDefinitionException` | Selected bean không đăng ký do scan/profile/condition | Kiểm tra bean name, active profiles và imports |
| `BeanDefinitionOverrideException` | Hai definitions cùng bean name và override bị cấm | Đặt unique bean names, không chỉ thêm qualifier |
| `ConflictingBeanDefinitionException` | Scan hai component classes trùng generated name | Explicit component names hoặc name generator |
| `NoUniqueBeanDefinitionException` dù có qualifier | Nhiều beans mang cùng semantic qualifier | Dùng nhãn cụ thể hơn hoặc collection injection |
| `NoUniqueBeanDefinitionException` sau đổi @Bean method name | Previously bean-name fallback không còn match | Dùng explicit bean name và qualifier |
| Property mới không đổi sender đang chạy | Startup-time dependency đã inject vào singleton | Recreate context hoặc runtime router |
| `@Profile` alternatives đồng thời active | Profiles không exclusive | Thiết kế selection rõ, test combinations |
| Không có sender khi không active profile | Không cấu hình default | Khai báo explicit default/fail-fast |
| @Primary bị “bỏ qua” khi có @Qualifier | Qualifier thu hẹp candidates theo type | Đọc candidate selection semantics |
| @Fallback import fail khi dùng 6.1 | Annotation mới từ Spring 6.2 | Upgrade hoặc dùng @Primary/@Qualifier |
| Bean name đúng nhưng type không khớp | Qualifier string không bypass type matching | Sửa interface/type registration |
| Inject `Map<String, Sender>` mà key không phải "email" | Keys mặc định là bean names | Tạo explicit map hoặc dùng bean names |
| ApplicationContext test pass nhưng Boot fail | Extra auto-config beans thay candidate set | Test Boot full context khi production chạy Boot |
| Unit test gọi new object không nhận @Autowired | Object không do Spring khởi tạo | Dùng constructor injection và fake |
| Gọi `new EmailSender()` trong consumer | Bypass DI selection | Chuyển tạo object sang composition root |
| Two eager adapters khởi tạo cả hai tốn resource | Cả hai singleton definitions đều active | Chọn một variant bằng condition/profile |
| Final class bị proxy lỗi | AOP proxy strategy không phù hợp | Inject interface, kiểm tra JDK/class proxy |
| Factory method gọi trực tiếp @Bean method khác | Có thể bypass container với proxyBeanMethods=false | Dùng method injection parameters |
| Thay Environment từ test sau refresh | Không recreate existing CheckoutFlow | Đặt property trước refresh |
| Caller cần prototype mới mỗi request | Singleton constructor chỉ resolve một prototype | Dùng `ObjectProvider<Sender>` hoặc scoped proxy đúng semantics |

### 11.1. Phân biệt NoUniqueBeanDefinitionException và override conflict

Nếu đăng ký:

```text
emailSender : Sender
smsSender   : Sender
```

hai definitions **khác tên** nên hợp lệ. Lỗi xuất hiện khi một consumer yêu cầu **một** `Sender` không qualifier và không có quy tắc chọn.

Nếu đăng ký:

```text
sender : EmailSender
sender : SmsSender
```

trùng registry name `sender`, lỗi liên quan **BeanDefinition overriding** có thể xuất hiện ngay trước injection. Hai tình huống phải xử lý riêng.

### 11.2. Không dùng @Primary thay cho tên bean duy nhất

`@Primary` đánh dấu một candidate ưu tiên khi nhiều beans cùng type. Nó không tạo hai slots cho một bean name; cũng không sửa conflict component scanning khi hai classes trùng explicit bean name.

### 11.3. Parameter name fallback và -parameters

Trong Spring Framework 6.1+, việc matching constructor/method parameter names cần Java bytecode chứa `-parameters` khi dựa vào tên tham số. Đừng thiết kế một workflow dựa tình cờ vào parameter `emailSender` để được chọn đúng; dùng `@Qualifier("emailSender")` làm intent tường minh.

Với Maven compiler plugin:

```xml
<configuration>
    <release>17</release>
    <parameters>true</parameters>
</configuration>
```

Đây là snippet plugin config. Dù thiết lập `-parameters` giúp parameter name resolution, demo vẫn dùng qualifiers để tránh phụ thuộc vào method parameter names cho các lựa chọn.

## 12. Custom qualifier và meta-annotations

Khi dự án lớn có nhiều adapters cùng type, các string qualifier như `"emailSender"` có thể rải rác và khó refactor. Spring hỗ trợ **custom qualifier annotations** bằng meta-annotation `@Qualifier`.

```java
@Target({ElementType.FIELD, ElementType.PARAMETER,
         ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface ReliableOutbound {
}
```

Đây là snippet annotation type, cần imports `java.lang.annotation.*` và Spring `Qualifier`. Dùng trên một implementation bean/producer và injection point muốn match:

```java
@Bean
@ReliableOutbound
Sender emailSender() {
    return new EmailSender();
}

@Bean
CheckoutFlow checkoutFlow(@ReliableOutbound Sender sender) {
    return new CheckoutFlow(sender);
}
```

Đây là snippets tích hợp. Custom qualifier có thể diễn đạt đặc điểm nghiệp vụ (`@ReliableOutbound`) thay vì hardcode tên bean. Nhưng nếu có **nhiều beans mang cùng annotation**, injection đơn lẻ vẫn có thể ambiguous. Qualifier không đảm bảo unique candidate; phải quản lý tập candidates có chủ đích.

### 12.1. Qualifier cho generics

Spring có thể dùng **generic type arguments** như implicit qualifier. Ví dụ `Repository<Customer>` và `Repository<Order>` có thể được phân biệt trong type matching mà không cần string qualifier trong nhiều trường hợp.

Tuy nhiên, Java type erasure, proxies/raw types và factory method return type declarations có thể ảnh hưởng metadata; đừng giả định `Repository<Object>` sẽ match tất cả. Đọc Spring versioned reference và test actual bean definitions.

### 12.2. @Resource(name) khác @Qualifier

`@Resource(name="emailSender")` là Jakarta annotation với **by-name** oriented semantics, trong khi `@Autowired @Qualifier("emailSender")` là **by-type rồi thu hẹp theo qualifier**.

Một constructor-based design thường ưu tiên `@Qualifier` trên parameter hoặc wiring method hơn field `@Resource`, nhưng cần quyết định theo contract và Java/Jakarta version.

## 13. Scope và lifecycle khi thay implementation

### 13.1. Singleton selection

Mặc định `@Bean` singleton, `CheckoutFlow` cũng thường singleton. Khi `CheckoutFlow` được tạo, Spring resolve selected `Sender` và truyền reference vào constructor. Dù `Environment` thay đổi, field `final Sender sender` không tự đổi.

### 13.2. Prototype adapter

Nếu `@Scope("prototype")` được đặt trên `Sender` nhưng inject trực tiếp vào singleton `CheckoutFlow`, một instance prototype được resolve **khi tạo CheckoutFlow**, không phải một instance mới cho mỗi invoice.

Nếu thật sự muốn một instance mỗi thao tác, dùng `ObjectProvider<Sender>`, `@Lookup` hoặc strategy factory với đúng lifecycle responsibilities. Không nhầm “prototype scope” với “tự cập nhật dependency mỗi method call”.

### 13.3. External resources

Hai adapters tạo connection pools hoặc clients có thể đều khởi tạo khi refresh, dù chỉ một adapter được consumer sử dụng. Nếu one-of-N deployment variant tiết kiệm resources, hãy dùng mutually exclusive registrations để chỉ tạo đúng implementation hoặc `@Lazy` có kiểm soát.

Những resources do Spring tạo từ `@Bean` có thể dùng inferred destroy `close()`/`shutdown()`. Đảm bảo adapters implement lifecycle cleanup đúng; không bật/tắt bằng cách giữ orphan singleton resources.

### 13.4. Transactions, caching và proxies

Nếu `EmailSender`/ `SmsSender` được AOP proxy, injection thường nhận **exposed proxy**. Đừng tự tạo object trong config rồi truyền raw object không qua Spring mà kỳ vọng `@Transactional` hoặc `@Cacheable` tự hoạt động.

Ví dụ factory method `return new CheckoutFlow(new EmailSender())` bypasses managed `emailSender` bean and its proxy. Hãy nhận `@Qualifier("emailSender") Sender sender` từ method parameters để Spring resolve đúng bean.

## 14. Cách mở rộng sang một cấu hình production

Ví dụ real adapters thường cần dependencies:

```java
@Bean("emailSender")
Sender emailSender(
        EmailApiClient api,
        @Value("${delivery.email.from}") String from) {
    return new RealEmailSender(api, from);
}
```

Đây là snippet tích hợp; cần `EmailApiClient`, `RealEmailSender`, Spring `@Value` và property source đủ sớm. Khi dùng Spring Boot, có thể bind settings qua `@ConfigurationProperties` có validation thay vì nhiều `@Value`.

Để tránh secrets trong source, API credentials phải đi từ external secret manager/environment, không hardcode trong factory method hay generated config code.

### 14.1. Feature selection vs secrets

Property `delivery.channel=email|sms` là config điều khiển **chọn implementation**. `email.api.token` là secret dùng bởi Email adapter. Không dùng một Condition kiểm tra token có tồn tại để âm thầm thay đổi kênh: missing secret phải là lỗi config được báo rõ nếu Email được chọn, không tự fallback sang SMS và gửi khác kỳ vọng.

### 14.2. Khởi tạo thực sự theo nhu cầu

Nếu SMS không được chọn, không muốn tạo SMS client ở startup, cấu hình hai factory methods `@Lazy` hoặc mutually exclusive `@Profile`/`@Conditional` phù hợp; nhưng `@Lazy` trên bean definition **không cứu được** nếu một factory method lại eager-inject cả Email và SMS như PropertyWiring.

Cần kiểm tra **dependency resolution graph thực tế**, không chỉ nhìn một annotation lazy trên class.

### 14.3. Kiểm thử rollback khi chuyển kênh

Khi chuyển Email → SMS trong deployment:
- Build context với property SMS.
- Assert CheckoutFlow thực sự gọi SMS fake/spy.
- Assert Email adapter không được gọi (nếu requirement cấm gửi trùng).
- Test invalid/missing config fail sớm.
- Test resource cleanup khi context close.
- Test rollback với property Email.
- Nếu có traffic đang xử lý, xác định routing consistency và idempotency của thông báo.

Đây là hành vi nghiệp vụ cần kiểm tra, không phải Spring tự bảo đảm chỉ vì `@Qualifier` được cấu hình đúng.

## 15. Checklist thực hành và tiêu chí hoàn thành

### 15.1. Bài tập 1 — Thay bằng configuration class

Từ source demo phần 7:
1. Chạy `EmailWiring`, xác nhận `EMAIL:invoice-9`.
2. Chạy `SmsWiring`, xác nhận `SMS:invoice-9`.
3. So sánh source `CheckoutFlow`: **không có thay đổi**.
4. Kiểm tra mỗi context có đủ hai beans riêng tên.
5. Thử thay `@Qualifier("smsSender")` bằng tên không tồn tại để quan sát fail-fast.

**Đạt:** Implementation được thay ở composition root, consumer không sửa.

### 15.2. Bài tập 2 — Chọn bằng một property

1. Chạy `PropertyWiring` với `delivery.channel=email`.
2. Chạy lại trong **context mới** với `delivery.channel=sms`.
3. Thử `delivery.channel=whatsapp` và kiểm tra error.
4. Thử thay MapPropertySource sau refresh rồi gọi CheckoutFlow cũ.

**Đạt:** Hiểu lựa chọn startup-time và không lẫn với runtime hot-switch.

### 15.3. Bài tập 3 — Candidate selection và ambiguity

1. Chạy `PrimaryWiring`.
2. Chạy `FallbackWiring`.
3. Chạy `AmbiguousWiring`, xác nhận `NoUniqueBeanDefinitionException` trong cause chain.
4. Thêm `@Qualifier("smsSender")` vào factory method của consumer để giải quyết ambiguity.
5. Inject `List<Sender>` và kiểm tra có hai beans.

**Đạt:** Phân biệt bean registration, qualifier filtering và primary/fallback selection.

### 15.4. Bài tập 4 — Profiles

1. Viết `@Profile("email")` và `@Profile("sms")` trên two alternative definitions.
2. Test only-email, only-sms, both, none.
3. Xác định rõ behavior khi both/none; sửa design thay vì dựa registration order.

**Đạt:** Conditions deterministic và có guard khi config không hợp lệ.

### 15.5. Bài tập 5 — Runtime router

1. Giữ Email + SMS cùng là managed beans.
2. Viết `SenderRouter` nhận explicit map `email` → bean Email, `sms` → bean SMS.
3. Gọi hai invoices theo hai channels khác nhau trong cùng context.
4. Không gọi `context.refresh()` thêm lần nữa; không đăng ký lại BeanDefinitions.

**Đạt:** Nhận ra runtime behavior routing khác với startup-time dependency wiring.

## 16. Các câu hỏi tự kiểm tra

**1. Vì sao cần interface chung?**  
Để consumer phụ thuộc vào contract thay vì class triển khai.

**2. @Qualifier có tự tạo bean không?**  
Không. Nó giúp chọn/lọc trong các candidates đã được đăng ký.

**3. Hai beans khác tên nhưng cùng type có hợp lệ không?**  
Có. Vấn đề chỉ xuất hiện khi dependency resolution không phân biệt được nếu yêu cầu single bean.

**4. Nếu hai @Bean methods cùng tên mặc định thì sao?**  
Có thể dẫn đến bean name conflict/override, khác với type ambiguity.

**5. Làm sao đổi từ Email sang SMS mà không sửa CheckoutFlow?**  
Dùng @Configuration factory method nhận `@Qualifier("smsSender") Sender`, hoặc chọn trong property-aware configuration.

**6. @Qualifier có luôn là lookup bean name chính xác không?**  
Không; semantics chính là type-driven candidate filtering. Tên bean có thể match qualifier như một fallback.

**7. Có thể hai beans cùng @Qualifier("outbound") không?**  
Có. Khi đó collection injection có thể nhận cả hai; single injection có thể ambiguous.

**8. @Primary làm gì?**  
Ưu tiên một candidate cho single-valued injection khi nhiều candidates phù hợp.

**9. @Fallback bắt đầu từ phiên bản nào?**  
Spring Framework 6.2.

**10. @Fallback có xóa SMS khỏi registry không?**  
Không. SMS vẫn là một bean và có thể được chọn explicit.

**11. @Bean(defaultCandidate=false) khác @Bean(autowireCandidate=false) thế nào?**  
DefaultCandidate=false loại khỏi plain-type autowiring nếu thiếu chỉ dẫn, còn autowireCandidate=false loại khỏi by-type autowiring rộng hơn.

**12. Nếu property đổi sau refresh, CheckoutFlow singleton đổi sender ngay không?**  
Không; constructor reference đã được chọn lúc tạo bean.

**13. Nếu cần chọn theo từng request?**  
Dùng runtime router/strategy, không đổi @Qualifier của một bean đã instantiate.

**14. @Profile("email") và @Profile("sms") có đảm bảo exclusive không?**  
Không. Nhiều profiles có thể active.

**15. Nếu không có active profile?**  
Có thể không có implementation; cần default/fail-fast theo requirement.

**16. Vì sao nên test bằng output chứ không chỉ context loads?**  
Vì context có thể chạy nhưng inject sai implementation.

**17. Có nên gọi new EmailSender() trong CheckoutFlow không?**  
Không, nếu muốn Spring quyết định implementation và hỗ trợ AOP/lifecycle.

**18. Boot @ConditionalOnProperty có trong spring-context thuần không?**  
Không; nó thuộc Spring Boot.

**19. Vì sao nhiều lazy beans vẫn có thể khởi tạo sớm?**  
Một eager factory method có thể resolve chúng làm dependencies ngay khi startup.

**20. Nếu adapter là prototype thì mỗi call checkout có instance mới không?**  
Không tự động khi prototype được inject một lần vào singleton.

**21. Map<String, Sender> injection có keys là gì?**  
Thông thường là bean names, không phải custom channel values tự suy đoán.

**22. @Resource(name) có giống @Qualifier không?**  
Không hoàn toàn; @Resource hướng tới lookup theo tên, @Qualifier lọc type candidates.

**23. Nếu production cần chọn một implementation, làm sao tránh khởi tạo cả hai?**  
Dùng mutually exclusive registrations/conditions hoặc lazy strategy được thiết kế cẩn thận.

**24. Nếu email credential không có, có nên âm thầm chuyển sang SMS?**  
Không trừ khi yêu cầu nghiệp vụ định nghĩa rõ; nên fail-fast nếu Email mode được yêu cầu.

**25. Tiêu chí hoàn thành quan trọng nhất?**  
Một consumer giữ nguyên source, nhưng configurations/qualifiers/property và tests chứng minh đã chọn đúng implementation.

## 17. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Fine-tuning Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html): type-driven qualifier semantics, bean name fallback, multiple qualifiers và collections.
- [Spring Framework 6.2 — @Primary and @Fallback](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html): @Primary, @Fallback và single-candidate resolution.
- [Spring Framework 6.2 — Using @Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/bean-annotation.html): factory methods, naming và Java configuration.
- [Spring Framework 6.2 — Environment Abstraction](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html): active/default profiles, properties, @Profile.
- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html): constructor injection và dependencies.
- [Spring Framework 6.2 — @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html): constructor resolution và multiple candidates.
- [Spring Framework 6.2 API — Qualifier](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/annotation/Qualifier.html): allowed targets và semantic labels.
- [Spring Framework 6.2 API — Bean](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html): name/value, defaultCandidate, autowireCandidate.
- [Spring Framework 6.2 API — Fallback](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Fallback.html): annotation từ 6.2.
- [Spring Framework 6.2 — Generics as Autowiring Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/generics-as-qualifiers.html): type generic narrowing.
- [Spring Framework 6.2 — CustomAutowireConfigurer](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/custom-autowire-configurer.html): qualifier annotations tùy chỉnh.
- [Spring Framework 6.2 — Ahead-of-Time Optimizations](https://docs.spring.io/spring-framework/reference/6.2/core/aot.html): build-time bean graph conditions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-003">Đối chiếu cùng một đồ thị bằng Java, annotation và XML</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu cùng một đồ thị bằng Java, annotation và XML trong Spring Core Container

## 1. Mục tiêu thực hành

Spring IoC Container không gắn chặt với một cú pháp khai báo bean duy nhất. Cùng một mạng đối tượng có thể được lắp ráp bằng **đăng ký Java programmatic**, **Java annotation configuration** hoặc **XML bean definitions**. Điểm chung là Spring nắm giữ BeanDefinitions, tạo các objects, kết nối dependencies, quản lý scopes/lifecycle và cung cấp API lookup qua BeanFactory/ApplicationContext.

Bài này xây dựng **một đồ thị bốn bean singleton có ba cạnh**, thực hiện ba lần bằng các cấu hình khác nhau, rồi **kiểm chứng sự tương đương quan sát được** bằng một chương trình Java 17 có thể chạy:

~~~text
                          shopFacade
                               |
                               | constructor dependency
                               v
                         checkoutService
                          /          \
                         /            \
                        v              v
              catalogRepository    taxPolicy

Các cạnh theo quy ước "A → B nghĩa là A cần B":
  shopFacade      → checkoutService
  checkoutService → catalogRepository
  checkoutService → taxPolicy
~~~

Các beans có cùng **tên**, cùng **runtime business types**, cùng **constructor arguments**, cùng **singleton scope** và cùng **identity edges bên trong context**. Mỗi cấu hình tạo một `ApplicationContext` riêng; **không** chia sẻ singleton giữa ba contexts.

### Ba cơ chế được đối chiếu

1. **Java programmatic**: tạo `GenericApplicationContext`, gọi `registerBean(name, type, Supplier)`, rồi `refresh()`.
2. **Annotation-based Java configuration**: đăng ký một `@Configuration(proxyBeanMethods = false)` chứa bốn `@Bean` methods; method parameters biểu diễn dependencies.
3. **XML**: nạp `graph/graph.xml` bằng `ClassPathXmlApplicationContext`; mỗi `<bean>` và `<constructor-arg ref=...>` tương ứng với một node/cạnh.

Ở đây, “Java” được dùng cụ thể cho **đăng ký bằng mã**, còn “annotation” cho **declarative JavaConfig**. Cả hai đều viết bằng Java, nhưng cơ chế mô tả bean khác nhau. Một cách annotation khác là `@Component` + `@ComponentScan` + `@Autowired`; bài này cũng giải thích nhưng không trộn vào chương trình chính để giữ graph không bị duplicate registrations.

### Kết quả cần đạt

- Hiểu tại sao cùng object graph có thể được tạo từ nhiều kiểu metadata.
- Xác định chính xác nodes, directed edges, bean names, scopes và behavior cần giữ tương đương.
- Phân biệt **configuration representation** với **runtime object graph**.
- Viết code Java 17/Spring 6.2.19/Maven thực thi được theo cấu trúc tệp hoàn chỉnh.
- Biết nhận ra sự khác biệt quan trọng: infrastructure beans, post-processors, auto-wiring conventions, proxying, lifecycle và startup timing.
- Viết regression tests khi di chuyển từ XML sang annotation/Java programmatic, tránh “migrate trông tương đương” nhưng làm thay đổi behavior.
- Chẩn đoán lỗi thiếu dependency, sai bean name/type, sai scope, placeholder/conversion, cycle và refresh phase.
- Chứng minh các beans trong **cùng context** chia sẻ đúng singleton và các beans **khác context** là instance độc lập.

## 2. Đồ thị phụ thuộc G = (V, E)

Gọi:

~~~text
V = {
  catalogRepository,
  taxPolicy,
  checkoutService,
  shopFacade
}

E = {
  checkoutService -> catalogRepository,
  checkoutService -> taxPolicy,
  shopFacade -> checkoutService
}
~~~

Trong bài, một cạnh `A → B` có nghĩa bean A giữ tham chiếu tới bean B do constructor nhận vào. Mọi node đều là singleton. Đây là một **DAG** (đồ thị có hướng không chu trình), nên container có thể xây các dependencies trước dependents theo thứ tự hợp lệ.

### Không có cạnh nào khác trong business graph

`catalogRepository` chỉ giữ catalog id và cung cấp giá SKU. `taxPolicy` chỉ giữ thuế suất nguyên 10%. `checkoutService` giữ repository + policy. `shopFacade` chỉ giữ checkout service.

Có thể có các **Spring infrastructure BeanDefinitions** bên ngoài tập bốn business beans trong một context, nhất là ở cấu hình `@Configuration`, nhưng chúng **không thuộc business subgraph** cần so sánh. Đừng yêu cầu `getBeanDefinitionCount()` của ba contexts bằng nhau.

### Node identity và type

| Bean name | Type Java | Constructor arguments | Scope |
| --- | --- | --- | --- |
| `catalogRepository` | `CatalogRepository` | String `"catalog-v1"` | singleton |
| `taxPolicy` | `TaxPolicy` | int `10` | singleton |
| `checkoutService` | `CheckoutService` | tham chiếu CatalogRepository, TaxPolicy | singleton |
| `shopFacade` | `ShopFacade` | tham chiếu CheckoutService | singleton |

### Business contract

Giả sử SKU `book` có giá `1.200 cents`, mua `2` units, thuế `10%`:

~~~text
subtotal = 1200 × 2 = 2400 cents
tax      = 2400 × 10 / 100 = 240 cents
total    = 2400 + 240 = 2640 cents
~~~

Kết quả receipt:

~~~text
catalog-v1|book|2|2640
~~~

Để tránh tranh luận về binary floating-point, ví dụ dùng **integer cents** và một chính sách thuế rất đơn giản. Đây là data minh họa, không phải engine tính thuế production: thực tế cần quy tắc làm tròn, multi-currency, giảm giá, refund, thuế nhiều cấp, overflow và tính toàn vẹn giao dịch.

## 3. Ba cách cấu hình khác nhau ở đâu?

| Khía cạnh | Java programmatic | Annotation @Configuration/@Bean | XML bean definitions |
| --- | --- | --- | --- |
| Điểm vào | `new GenericApplicationContext()` | `new AnnotationConfigApplicationContext(...)` | `new ClassPathXmlApplicationContext(...)` |
| Khai báo node | `registerBean(name,type,supplier)` | `@Bean` method trả type | `<bean id=... class=...>` |
| Khai báo constructor edge | Supplier gọi `getBean(...)` | Tham số method `@Bean` | `<constructor-arg ref=...>` |
| Giá trị scalar | Literal Java trong Supplier | Literal hoặc `@Value`/Environment | `value="..."`, placeholder nếu có processor |
| Bean name | Truyền chính xác vào registerBean | Tên method hoặc `@Bean("name")` | `id` hoặc `name` |
| Singleton default | Có | Có | Có |
| Prototype | BeanDefinitionCustomizer setScope | `@Scope("prototype")` | `scope="prototype"` |
| Annotation processing | Không cần cho Supplier đã wiring hết | Có ConfigClass processing | Không bắt buộc cho constructor refs thuần XML |
| Ngôn ngữ | Java imperative | Java annotations/declarative factories | XML declarative |
| Refactor kiểm tra bởi compiler | Cao ở class/constructor types | Cao với JavaConfig types | XML bean names/class names được kiểm tra khi nạp |
| Dễ chỉnh deployment không biên dịch | Thường cần build mã mới | Thường cần build mã mới | Có thể sửa file được deploy, nhưng vẫn cần đảm bảo consistency/build |
| Điều kiện profile | Registration bằng mã | `@Profile`/conditional | `profile` trên beans sections khi loader hỗ trợ |

**Không kết luận một cách là “Spring thật” hơn hai cách còn lại.** Chúng đều tạo BeanDefinitions cho IoC Container. Lựa chọn ảnh hưởng maintainability, code organization, testability và khả năng diễn đạt metadata, chứ không thay đổi luật Java object references.

## 4. Business classes phải độc lập với kiểu cấu hình

Một phép so sánh công bằng đòi hỏi **ba contexts dùng đúng cùng business classes**, không viết ba implementations có hành vi khác nhau rồi chỉ so stdout. Trong ví dụ:

~~~java
public static final class CheckoutService {
    private final CatalogRepository repository;
    private final TaxPolicy taxPolicy;

    public CheckoutService(
            CatalogRepository repository, TaxPolicy taxPolicy) {
        this.repository = repository;
        this.taxPolicy = taxPolicy;
    }

    public int totalCents(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        int subtotal = Math.multiplyExact(
                repository.priceCents(sku), quantity);
        return Math.addExact(
                subtotal, taxPolicy.taxCents(subtotal));
    }

    public CatalogRepository repository() {
        return repository;
    }

    public TaxPolicy taxPolicy() {
        return taxPolicy;
    }
}
~~~

Các class này không có `@Component` hay `@Autowired`. Constructor public nhận explicit dependencies. Nhờ vậy **tầng business không biết cấu hình được viết bằng Java, annotation hay XML**.

Các getters chỉ phục vụ việc quan sát cạnh bằng identity trong bài thực hành. Trong business production, không cần public expose toàn bộ collaborators nếu không có contract cho việc đó; có thể kiểm tra wiring bằng behavior-oriented tests và structured instrumentation thích hợp.

### Không có hidden singleton/static state

Nếu các class tự giữ một `static INSTANCE` hoặc singleton enum, ba contexts có thể vô tình dùng chung object dù cấu hình khác nhau. Ví dụ cố ý không dùng static mutable caches, global service locator hoặc shared mutable registry để kiểm tra **Spring singleton đúng theo từng container**.

## 5. Phương pháp 1 — Java programmatic với GenericApplicationContext

~~~java
GenericApplicationContext context =
        new GenericApplicationContext();

context.registerBean(
        "catalogRepository", CatalogRepository.class,
        () -> new CatalogRepository("catalog-v1"));

context.registerBean(
        "taxPolicy", TaxPolicy.class,
        () -> new TaxPolicy(10));

context.registerBean(
        "checkoutService", CheckoutService.class,
        () -> new CheckoutService(
                context.getBean(
                        "catalogRepository", CatalogRepository.class),
                context.getBean("taxPolicy", TaxPolicy.class)));

context.registerBean(
        "shopFacade", ShopFacade.class,
        () -> new ShopFacade(
                context.getBean(
                        "checkoutService", CheckoutService.class)));

context.refresh();
~~~

Đây là snippet trích từ chương trình đầy đủ bên dưới, có imports/definitions đầy đủ. Các `registerBean` calls chỉ **đăng ký BeanDefinitions** và Supplier callback; target objects chưa cần được khởi tạo ngay lúc đăng ký. Trong quá trình `refresh()`, container instantiate singleton beans và gọi Supplier khi cần.

### Vì sao closure được phép gọi context.getBean?

Các Supplier của `checkoutService`/`shopFacade` được Spring gọi **trong quá trình tạo bean**, sau khi BeanDefinitions đã đăng ký. Lúc đó các dependencies có thể được resolve bằng tên và required type. Không gọi `getBean` trước refresh trong ví dụ.

### Thứ tự registration và thứ tự instantiation

Ta đăng ký repository/policy trước checkout/facade để code dễ đọc. Tuy nhiên, một DAG với reference resolution đúng không nên dựa vào “đăng ký trước thì chắc chắn tạo trước” khi có lazy scopes, BPP, factory beans hoặc dynamic injection. Thứ tự quan trọng là **dependency B cần sẵn trước lúc A sử dụng B**.

~~~text
Register definitions:
  repository, policy, service, facade

Resolve constructor dependencies:
  create repository / policy (khi cần)
  create service with repository + policy
  create facade with service
~~~

### Vì sao GenericApplicationContext.refresh chỉ một lần?

`GenericApplicationContext` giữ một `DefaultListableBeanFactory` nội bộ; theo Javadoc, `refresh()` chỉ nên/được gọi **một lần** trong vòng đời context ấy. Nếu muốn đồ thị khác, tạo context mới hoặc dùng API đăng ký đúng ở bootstrap phase; đừng gọi refresh lần hai trên chính GenericApplicationContext sau khi nó đã initialized.

### Supplier không tự động có DI

Nếu đăng ký `new CheckoutService(null,null)` qua Supplier, Spring không tự sửa các tham số null. Khi bạn cung cấp callback tạo object, **callback chịu trách nhiệm truyền đúng collaborators**. Vì thế chương trình kiểm tra reference identity thay vì chỉ in kết quả.

## 6. Phương pháp 2 — annotation với @Configuration và @Bean

~~~java
@Configuration(proxyBeanMethods = false)
public static class AnnotatedGraph {

    @Bean("catalogRepository")
    public CatalogRepository catalogRepository() {
        return new CatalogRepository("catalog-v1");
    }

    @Bean("taxPolicy")
    public TaxPolicy taxPolicy() {
        return new TaxPolicy(10);
    }

    @Bean("checkoutService")
    public CheckoutService checkoutService(
            CatalogRepository repository,
            TaxPolicy taxPolicy) {
        return new CheckoutService(repository, taxPolicy);
    }

    @Bean("shopFacade")
    public ShopFacade shopFacade(CheckoutService checkoutService) {
        return new ShopFacade(checkoutService);
    }
}
~~~

`AnnotationConfigApplicationContext(AnnotatedGraph.class)` xử lý class `@Configuration` và các `@Bean` methods; Spring resolve method parameters theo type/candidate rules trước khi gọi factory method.

### Tại sao proxyBeanMethods = false?

Mọi factory method dùng **tham số** thay vì gọi trực tiếp `this.catalogRepository()`. Do đó không cần intercept `@Bean` method calls để enforce singleton semantics giữa các methods. Với `proxyBeanMethods = false`, một lời gọi Java trực tiếp `catalogRepository()` (nếu ta tự viết) chỉ là gọi method bình thường và có thể tạo một object mới. Tránh trộn direct inter-method calls với container-managed injection.

### Bean names chính xác

`@Bean("catalogRepository")` đăng ký tên bean rõ ràng, tránh phụ thuộc vào việc method bị rename trong quá trình refactor. Trong ví dụ, bean names và Java method names trùng nhau để dễ đối chiếu; cơ chế `@Bean` không yêu cầu chúng luôn trùng.

### @Configuration class không phải node nghiệp vụ

Spring có thể thêm chính bean definition cho `AnnotatedGraph` và các infrastructure processors; vì vậy tổng bean definition count khác `GenericApplicationContext` hoặc XML là chuyện bình thường. Ta so **bốn business nodes và ba identity edges**, không so những bean definitions nội bộ.

### @ComponentScan là biến thể annotation khác

Ví dụ dự án lớn có thể dùng:

~~~java
@Configuration(proxyBeanMethods = false)
@ComponentScan("example.shop")
class ShopScanConfig { }
~~~

Với `@Component` trên implementations và `@Autowired` constructor injection, Spring cũng có thể dựng cùng graph. Nhưng nếu đồng thời đăng ký `@Bean` factories cho cùng classes rồi component-scan lại, có nguy cơ duplicate/ambiguous candidates; hãy chọn một registry strategy hoặc qualifiers phù hợp.

## 7. Phương pháp 3 — XML với <bean> và constructor-arg

Trong `src/main/resources/graph/graph.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
          http://www.springframework.org/schema/beans
          https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalogRepository"
          class="example.GraphComparisonDemo$CatalogRepository">
        <constructor-arg index="0" value="catalog-v1"/>
    </bean>

    <bean id="taxPolicy"
          class="example.GraphComparisonDemo$TaxPolicy">
        <constructor-arg index="0" value="10"/>
    </bean>

    <bean id="checkoutService"
          class="example.GraphComparisonDemo$CheckoutService">
        <constructor-arg index="0" ref="catalogRepository"/>
        <constructor-arg index="1" ref="taxPolicy"/>
    </bean>

    <bean id="shopFacade"
          class="example.GraphComparisonDemo$ShopFacade">
        <constructor-arg index="0" ref="checkoutService"/>
    </bean>

</beans>
~~~

Đây là **toàn bộ XML config** cho chương trình. Các tên `example.GraphComparisonDemo$TaxPolicy` dùng **binary names** của public static nested Java classes. Dấu `$` trong XML class attribute là bình thường với tên inner/nested class Java theo reflection; nó không phải placeholder Spring.

### index và ref

- `<constructor-arg index="0" value="catalog-v1"/>` là **giá trị literal** truyền tới String constructor.
- `<constructor-arg index="0" value="10"/>` cần Spring chuyển chuỗi thành int; chuẩn Spring BeanFactory hỗ trợ conversion đơn giản.
- `<constructor-arg index="0" ref="catalogRepository"/>` là tham chiếu tới **bean name**, không phải Java class name.
- `index` bắt đầu từ 0 và làm constructor overload resolution rõ hơn.
- `<bean>` default scope là singleton nếu không có `scope`; phù hợp hai kiểu cấu hình còn lại.

### Cách nạp XML

~~~java
ClassPathXmlApplicationContext context =
        new ClassPathXmlApplicationContext("graph/graph.xml");
~~~

Constructor này load resource theo classpath và refresh context. Đường dẫn phải tồn tại đúng trong `src/main/resources/graph/graph.xml` của dự án Maven. Nếu XML nằm nơi khác, có thể dùng `classpath:...` hoặc `FileSystemXmlApplicationContext` theo nhu cầu, nhưng đừng nhầm classpath resource với filesystem absolute path.

### Không có annotation processing vẫn wiring được

Các constructors được mô tả bằng `<constructor-arg>` rõ ràng; XML thuần không cần quét `@Autowired` trên business types. Nếu ứng dụng XML muốn bật annotation injection, có thể dùng `<context:annotation-config/>` hoặc `<context:component-scan/>` với namespaces tương ứng. Đừng tự giả định XML loader luôn component-scan cả package.

## 8. Kiểm chứng tương đương phải so những gì?

Ta định nghĩa một hợp đồng kiểm tra cho **mỗi** context.

### A. Có đủ và đúng tên beans

~~~java
CatalogRepository repository =
        context.getBean("catalogRepository", CatalogRepository.class);
TaxPolicy policy =
        context.getBean("taxPolicy", TaxPolicy.class);
CheckoutService service =
        context.getBean("checkoutService", CheckoutService.class);
ShopFacade facade =
        context.getBean("shopFacade", ShopFacade.class);
~~~

`getBean(name, requiredType)` xác nhận đồng thời bean name hiện hữu và object assignable tới type. Nếu chỉ dùng `containsBean(name)`, bạn có thể bỏ sót một bean sai class/sai exposed proxy type.

### B. Đúng identity edges

~~~java
require(service.repository() == repository,
        "checkoutService -> catalogRepository");
require(service.taxPolicy() == policy,
        "checkoutService -> taxPolicy");
require(facade.checkoutService() == service,
        "shopFacade -> checkoutService");
~~~

Dùng `==` vì ta đang chứng minh **reference identity**, không phải business equality. Nếu service được dựng bằng `new CatalogRepository(...)` ngoài container, behavior có thể giống nhưng cạnh không trỏ tới singleton bean đúng.

### C. Đúng singleton scope

~~~java
require(context.isSingleton("catalogRepository"), "repository scope");
require(context.isSingleton("taxPolicy"), "policy scope");
require(context.isSingleton("checkoutService"), "service scope");
require(context.isSingleton("shopFacade"), "facade scope");
~~~

`isSingleton` là hợp đồng container; kiểm tra thêm hai lần `getBean` trả cùng object củng cố quan sát runtime.

### D. Đúng output nghiệp vụ

~~~text
catalog-v1|book|2|2640
~~~

Cùng output là cần nhưng **chưa đủ**: ba graphs có thể tạo một chuỗi giống nhau dù khác scope, số instances, dependencies hoặc lifecycle. Vì vậy phải kết hợp A–D.

### E. Đúng tách biệt giữa contexts

~~~java
programmatic.getBean("catalogRepository") !=
        annotated.getBean("catalogRepository")
~~~

Hai contexts độc lập đăng ký singleton riêng, nên không mong chia sẻ cùng Java object. “Cùng graph” nghĩa **isomorphic structure/behavior trên business subgraph**, không có nghĩa ba contexts dùng chung heap instances.

## 9. Dự án mẫu đầy đủ: Java 17, Maven và Spring 6.2.19

Cấu trúc:

~~~text
spring-graph-parity/
├── pom.xml
└── src/main/
    ├── java/example/GraphComparisonDemo.java
    └── resources/graph/graph.xml
~~~

Không cần Spring Boot, database, JUnit, AOP hay HTTP server cho bài này. `spring-context` kéo theo những Spring Core/Beans dependencies cần thiết. Code trong `GraphComparisonDemo.java` chứa business classes dưới dạng **public static nested classes**, vì vậy XML có thể tham chiếu qua binary class names.

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-graph-parity</artifactId>
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

### src/main/java/example/GraphComparisonDemo.java

~~~java
package example;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public final class GraphComparisonDemo {

    public static void main(String[] args) {
        try (GenericApplicationContext javaContext =
                     buildProgrammaticContext();
             AnnotationConfigApplicationContext annotationContext =
                     new AnnotationConfigApplicationContext(
                             AnnotatedGraph.class);
             ClassPathXmlApplicationContext xmlContext =
                     new ClassPathXmlApplicationContext(
                             "graph/graph.xml")) {

            CheckResult javaResult =
                    verifyGraph(javaContext, "Java");
            CheckResult annotationResult =
                    verifyGraph(annotationContext, "Annotation");
            CheckResult xmlResult =
                    verifyGraph(xmlContext, "XML");

            boolean equivalent =
                    javaResult.receipt().equals(
                            annotationResult.receipt())
                    && annotationResult.receipt().equals(
                            xmlResult.receipt());

            boolean singletonIdentities =
                    javaResult.singletonIdentities()
                    && annotationResult.singletonIdentities()
                    && xmlResult.singletonIdentities();

            boolean independentContexts =
                    javaContext.getBean("catalogRepository")
                        != annotationContext.getBean("catalogRepository")
                    && annotationContext.getBean("catalogRepository")
                        != xmlContext.getBean("catalogRepository")
                    && javaContext.getBean("catalogRepository")
                        != xmlContext.getBean("catalogRepository");

            require(equivalent, "business results differ");
            require(singletonIdentities,
                    "singleton/edge identity differs");
            require(independentContexts,
                    "contexts unexpectedly share bean objects");

            System.out.println(
                    "Java: " + javaResult.receipt());
            System.out.println(
                    "Annotation: " + annotationResult.receipt());
            System.out.println(
                    "XML: " + xmlResult.receipt());
            System.out.println(
                    "Graph equivalence: " + equivalent);
            System.out.println(
                    "Singleton identities: " + singletonIdentities);
            System.out.println(
                    "Independent contexts: " + independentContexts);
            System.out.println("Verification: PASS");
        }
    }

    private static GenericApplicationContext
            buildProgrammaticContext() {

        GenericApplicationContext context =
                new GenericApplicationContext();

        context.registerBean(
                "catalogRepository", CatalogRepository.class,
                () -> new CatalogRepository("catalog-v1"));

        context.registerBean(
                "taxPolicy", TaxPolicy.class,
                () -> new TaxPolicy(10));

        context.registerBean(
                "checkoutService", CheckoutService.class,
                () -> new CheckoutService(
                        context.getBean(
                                "catalogRepository",
                                CatalogRepository.class),
                        context.getBean(
                                "taxPolicy", TaxPolicy.class)));

        context.registerBean(
                "shopFacade", ShopFacade.class,
                () -> new ShopFacade(
                        context.getBean(
                                "checkoutService",
                                CheckoutService.class)));

        context.refresh();
        return context;
    }

    private static CheckResult verifyGraph(
            ApplicationContext context, String variant) {

        CatalogRepository repository =
                context.getBean(
                        "catalogRepository", CatalogRepository.class);
        TaxPolicy policy =
                context.getBean("taxPolicy", TaxPolicy.class);
        CheckoutService service =
                context.getBean(
                        "checkoutService", CheckoutService.class);
        ShopFacade facade =
                context.getBean("shopFacade", ShopFacade.class);

        boolean correctEdges =
                service.repository() == repository
                && service.taxPolicy() == policy
                && facade.checkoutService() == service;

        boolean correctScopes =
                context.isSingleton("catalogRepository")
                && context.isSingleton("taxPolicy")
                && context.isSingleton("checkoutService")
                && context.isSingleton("shopFacade");

        boolean sameInstances =
                context.getBean("catalogRepository") == repository
                && context.getBean("taxPolicy") == policy
                && context.getBean("checkoutService") == service
                && context.getBean("shopFacade") == facade;

        String receipt = facade.receipt("book", 2);

        require(correctEdges, variant + ": dependency edges");
        require(correctScopes, variant + ": singleton scopes");
        require(sameInstances, variant + ": singleton references");
        require("catalog-v1|book|2|2640".equals(receipt),
                variant + ": business receipt");

        return new CheckResult(
                receipt, correctEdges && correctScopes && sameInstances);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalStateException(
                    "Graph verification failed: " + reason);
        }
    }

    public record CheckResult(
            String receipt, boolean singletonIdentities) {
    }

    public static final class CatalogRepository {
        private final String catalogId;

        public CatalogRepository(String catalogId) {
            if (catalogId == null || catalogId.isBlank()) {
                throw new IllegalArgumentException("Catalog id required");
            }
            this.catalogId = catalogId;
        }

        public String catalogId() {
            return catalogId;
        }

        public int priceCents(String sku) {
            if (!"book".equals(sku)) {
                throw new IllegalArgumentException("Unknown SKU");
            }
            return 1200;
        }
    }

    public static final class TaxPolicy {
        private final int percent;

        public TaxPolicy(int percent) {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException(
                        "Tax percent out of range");
            }
            this.percent = percent;
        }

        public int taxCents(int subtotal) {
            if (subtotal < 0) {
                throw new IllegalArgumentException(
                        "Subtotal must be nonnegative");
            }
            return Math.multiplyExact(
                    subtotal, percent) / 100;
        }
    }

    public static final class CheckoutService {
        private final CatalogRepository repository;
        private final TaxPolicy taxPolicy;

        public CheckoutService(
                CatalogRepository repository,
                TaxPolicy taxPolicy) {
            this.repository =
                    java.util.Objects.requireNonNull(repository);
            this.taxPolicy =
                    java.util.Objects.requireNonNull(taxPolicy);
        }

        public CatalogRepository repository() {
            return repository;
        }

        public TaxPolicy taxPolicy() {
            return taxPolicy;
        }

        public int totalCents(String sku, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be positive");
            }
            int subtotal = Math.multiplyExact(
                    repository.priceCents(sku), quantity);
            return Math.addExact(
                    subtotal, taxPolicy.taxCents(subtotal));
        }
    }

    public static final class ShopFacade {
        private final CheckoutService checkoutService;

        public ShopFacade(CheckoutService checkoutService) {
            this.checkoutService =
                    java.util.Objects.requireNonNull(checkoutService);
        }

        public CheckoutService checkoutService() {
            return checkoutService;
        }

        public String receipt(String sku, int quantity) {
            return checkoutService.repository().catalogId()
                    + "|" + sku
                    + "|" + quantity
                    + "|" + checkoutService.totalCents(sku, quantity);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AnnotatedGraph {
        @Bean("catalogRepository")
        public CatalogRepository catalogRepository() {
            return new CatalogRepository("catalog-v1");
        }

        @Bean("taxPolicy")
        public TaxPolicy taxPolicy() {
            return new TaxPolicy(10);
        }

        @Bean("checkoutService")
        public CheckoutService checkoutService(
                CatalogRepository repository,
                TaxPolicy policy) {
            return new CheckoutService(repository, policy);
        }

        @Bean("shopFacade")
        public ShopFacade shopFacade(
                CheckoutService service) {
            return new ShopFacade(service);
        }
    }
}
~~~

### src/main/resources/graph/graph.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
          http://www.springframework.org/schema/beans
          https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="catalogRepository"
          class="example.GraphComparisonDemo$CatalogRepository">
        <constructor-arg index="0" value="catalog-v1"/>
    </bean>

    <bean id="taxPolicy"
          class="example.GraphComparisonDemo$TaxPolicy">
        <constructor-arg index="0" value="10"/>
    </bean>

    <bean id="checkoutService"
          class="example.GraphComparisonDemo$CheckoutService">
        <constructor-arg index="0" ref="catalogRepository"/>
        <constructor-arg index="1" ref="taxPolicy"/>
    </bean>

    <bean id="shopFacade"
          class="example.GraphComparisonDemo$ShopFacade">
        <constructor-arg index="0" ref="checkoutService"/>
    </bean>

</beans>
~~~

### Chạy bằng Maven

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.GraphComparisonDemo
~~~

Các dòng sau là **stdout dự kiến** từ `System.out.println` của chương trình (không tính Spring/Maven diagnostic logs, và không khẳng định Maven đã được chạy ở đây):

~~~text
Java: catalog-v1|book|2|2640
Annotation: catalog-v1|book|2|2640
XML: catalog-v1|book|2|2640
Graph equivalence: true
Singleton identities: true
Independent contexts: true
Verification: PASS
~~~

Lưu `pom.xml` ở root, `GraphComparisonDemo.java` trong đúng package `example` và XML trong `src/main/resources/graph/`. Chạy bằng JDK 17+ và Maven có khả năng tải các dependencies. `spring-context` 6.2.19 đã bao gồm các APIs dùng trong bài, không cần `spring-boot-starter`.

## 10. Vì sao ví dụ này chứng minh điều chúng ta cần?

### Cùng bốn BeanDefinition names

Cả ba contexts đều expose:

~~~text
catalogRepository
taxPolicy
checkoutService
shopFacade
~~~

Java suppliers truyền tên explicit vào `registerBean`. Annotation methods đặt tên qua `@Bean("...")`. XML khai báo `id`. Khi `verifyGraph` dùng `getBean(name, Class)`, tên lẫn type đều được kiểm tra.

### Cùng ba cạnh reference thực

~~~java
service.repository() == repository
service.taxPolicy() == policy
facade.checkoutService() == service
~~~

Đây là kiểm tra sâu hơn `facade.receipt(...)`: một developer có thể vô tình `new CatalogRepository("catalog-v1")` trong supplier thay vì tham chiếu bean, và output vẫn giống, nhưng identity assertion sẽ fail.

### Cùng singleton scope

Chương trình dùng `context.isSingleton("...")` cho cả bốn names, rồi dùng `getBean` lần nữa để bảo đảm object identities không đổi. Đây là một trong những đặc điểm phải giữ khi chuyển cấu hình.

### Ba contexts độc lập

So sánh identity giữa cùng bean name ở Java, annotation và XML bảo đảm instances không bị chia sẻ toàn cục. Thực tế Spring singleton scope có nghĩa **một instance per bean definition per container**, không phải singleton duy nhất cho cả JVM.

### Cùng behavior tính giá

`CheckoutService.totalCents("book",2)` được gọi thông qua ShopFacade. Repository trả 1200, tax policy 10%, nên receipt luôn `catalog-v1|book|2|2640`. Phần nghiệp vụ được giữ nguyên trong cùng Java classes.

### Không so toàn bộ infrastructure beans

`AnnotationConfigApplicationContext` có thể đăng ký annotation processors, configuration class và support beans khác. Chúng không làm hỏng kết quả. Nếu bạn assert `getBeanDefinitionCount()==4` cho cả ba contexts, test có thể fail vì so sánh **tổng registry** thay vì business graph đã định nghĩa.

## 11. Annotation style còn có @ComponentScan/@Autowired

Một biến thể annotation thực tế dùng stereotype trên từng implementation:

~~~java
@Component("catalogRepository")
class CatalogRepository {
    // ...
}

@Component("checkoutService")
class CheckoutService {
    private final CatalogRepository repository;
    private final TaxPolicy policy;

    @Autowired
    CheckoutService(CatalogRepository repository, TaxPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }
}
~~~

Đây là **snippet khác, không phải mã để chép thêm vào chương trình mục 9**. Để nó compile độc lập, cần types, constructors, imports và một nơi cấu hình nguồn giá trị `catalog-v1`/`10`.

Cấu hình scanning:

~~~java
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "example.shop")
class ScannedGraph {
}
~~~

Trong Spring 4.3+, nếu component có một constructor duy nhất, `@Autowired` trên constructor thường không bắt buộc. Mặc dù vậy, khi có nhiều constructors, cần chỉ định injection points rõ ràng để tránh ambiguity.

### Cẩn trọng double registration

Nếu dùng `@ComponentScan` trên package chứa implementations và vẫn có một `@Configuration` dùng `@Bean` methods trả cùng type, bạn có thể tạo hai beans cho một interface. Khi constructor nhận một type mà có nhiều candidates, `NoUniqueBeanDefinitionException` có thể xuất hiện.

Giải pháp là scope scan chính xác, không trùng registration, hoặc dùng `@Qualifier`/`@Primary` đúng intent. Không rename các beans ngẫu nhiên rồi bỏ qua type ambiguity.

## 12. Một BeanDefinition khác một Java object

Ở cả ba kiểu, cần phân biệt:

~~~text
Configuration metadata / BeanDefinition
    bean name
    bean class / factory method / Supplier
    constructor args / properties
    scope / lazy / depends-on / init-destroy metadata
                |
                | BeanFactory instantiates
                v
         Java object instance
                |
                | property population, awareness,
                | BeanPostProcessor, initialization
                v
        Exposed bean reference
~~~

`@Bean` method, `<bean>` XML và `registerBean` không phải lúc nào cũng tạo object ngay lúc chúng xuất hiện trong source code. Metadata được thu thập rồi container thực hiện việc tạo beans khi refresh hoặc khi lookup, tùy scope/lazy/dependencies.

### Factory method khác instance supplier

Annotation `@Bean` sử dụng factory method; `GenericApplicationContext.registerBean(..., Supplier)` cung cấp instance supplier callback. Chúng có thể tạo cùng class nhưng metadata/factory method resolution không hoàn toàn giống.

### FactoryBean lại là abstraction khác

`FactoryBean<T>` là một Spring bean có chức năng tạo **product bean** qua `getObject()`. Nó khác `@Bean` factory method và `Supplier`. Khi thêm `FactoryBean` vào một trong ba cách cấu hình, cần xét product scope, getObjectType/type inference và prefix `&` để lấy factory. Đừng gộp ba cơ chế chỉ vì từ “factory”.

## 13. Constructor injection làm ba cách dễ đối chiếu

Trong ví dụ, ba edges đều đi qua constructors; Spring cần dependencies trước khi gọi constructors của dependent beans. Đây là kiểu dễ so nhất vì:

- Object chưa thể được tạo nếu required collaborators thiếu.
- References có thể `final` và immutable.
- Graph rõ ở constructors và metadata.
- Lỗi thiếu dependency thường xuất hiện khi khởi tạo context.
- Không có trạng thái tạm giữa constructor xong nhưng setter chưa gọi.

### Setter injection cũng làm được nhưng khó so hơn

XML:

~~~xml
<bean id="checkoutService" class="example.CheckoutService">
    <property name="repository" ref="catalogRepository"/>
</bean>
~~~

Annotation:

~~~java
@Autowired
void setRepository(CatalogRepository repository) {
    this.repository = repository;
}
~~~

Programmatic:

~~~java
context.registerBean("checkoutService", CheckoutService.class,
        () -> {
            CheckoutService service = new CheckoutService();
            service.setRepository(
                    context.getBean(CatalogRepository.class));
            return service;
        });
~~~

Đây là ba **snippets riêng** có giả định `CheckoutService` khác chương trình chính (có no-arg constructor, setter) và không được ghép thẳng vào ví dụ mục 9. Nếu chuyển từ setter sang constructor, vòng phụ thuộc có thể bộc lộ vì constructor cycles khó/không thể giải so với một số singleton setter cycles.

### Đừng mặc định circular dependencies luôn được giải

Với `A(B)` và `B(A)`, constructor cycle không có thứ tự tạo hợp lệ. Đổi XML sang annotation không loại bỏ vòng; `BeanCurrentlyInCreationException` vẫn có thể xảy ra. Điều cần thay đổi là **đồ thị dependency**, không chỉ cú pháp cấu hình.

## 14. Bean names: giữ contract để migrate an toàn

Tên bean là một phần quan trọng của hệ sinh thái Spring khi code dùng `@Qualifier`, `@Resource(name=...)`, bean aliases, `@DependsOn` hoặc explicit `getBean("name")`.

| Cơ chế | Đặt tên | Ví dụ |
| --- | --- | --- |
| Programmatic | tham số thứ nhất của `registerBean` | `"checkoutService"` |
| Annotation @Bean | `@Bean("checkoutService")` | method có thể tên khác |
| Annotation @Component | `@Component("checkoutService")` | generated name nếu không explicit |
| XML | `id="checkoutService"` hoặc aliases | ref dùng bean name |

### Những lỗi hay gặp

- Tên XML `checkout-service` chuyển sang Java method `checkoutService` nhưng nơi khác vẫn dùng `@Qualifier("checkout-service")`.
- Class name đúng nhưng không có bean definition phù hợp trong context.
- Programmatic registration không đặt name explicit, bean name mặc định có thể là fully qualified class name.
- Dùng annotation scanning và XML cùng lúc, tạo duplicate beans.
- FactoryBean product và factory prefix `&` bị nhầm.
- Hai implementations cùng interface không có `@Primary`/`@Qualifier`.
- AOP biến exposed type thành JDK interface proxy, nơi khác lại lookup concrete type.

**Migrate có kiểm soát:** giữ name, alias và qualifiers trước; sau đó có thể đổi contracts cùng tests nếu muốn refactor.

## 15. Scope: singleton/prototype phải được chuyển tương ứng

Trong bài, cả bốn beans là singleton theo mặc định. Nếu thay đổi `taxPolicy` thành prototype ở một variant, graph behavior có thể khác dù receipt vẫn đúng.

Programmatic:

~~~java
context.registerBean(
        "taxPolicy", TaxPolicy.class,
        () -> new TaxPolicy(10),
        bd -> bd.setScope(
                org.springframework.beans.factory.config.BeanDefinition
                        .SCOPE_PROTOTYPE));
~~~

Annotation:

~~~java
@Bean("taxPolicy")
@Scope("prototype")
TaxPolicy taxPolicy() {
    return new TaxPolicy(10);
}
~~~

XML:

~~~xml
<bean id="taxPolicy"
      class="example.GraphComparisonDemo$TaxPolicy"
      scope="prototype">
    <constructor-arg value="10"/>
</bean>
~~~

Đây là **ba snippets minh họa thay đổi scope riêng**, không thuộc chương trình chính. Chúng diễn tả bean definition scope nhưng có nuance rất quan trọng: nếu một singleton `CheckoutService` được inject một prototype `TaxPolicy` một lần, CheckoutService thường **giữ chính instance prototype đó** trong đời singleton. Không tự suy rằng mỗi lần gọi `CheckoutService.totalCents` sẽ nhận một TaxPolicy mới.

### Lấy prototype theo mỗi lần dùng

Nếu thật sự cần resolve prototype mỗi operation, có thể dùng `ObjectProvider<TaxPolicy>` hoặc method injection/scoped proxy phù hợp. Khi chuyển giữa cấu hình, phải giữ **resolution timing semantics**. Chỉ đặt scope prototype ở metadata mà không sửa consumer không làm behavior per-call thay đổi.

### Scope và context boundaries

Spring singleton là per bean definition/per BeanFactory. Một child context và parent context có thể chia sẻ/lookup bean parent theo rules, nhưng không phải mọi singleton mặc định global. Bài có ba **root contexts độc lập**, tránh thừa hưởng parent-bean identities.

## 16. Lifecycle và thứ tự khởi tạo

### Lifecycle phases

~~~text
Register / parse BeanDefinitions
       |
       v
BeanFactoryPostProcessor / registry post-processors
       |
       v
Singleton instantiation
       |
       v
Constructor invocation / dependencies
       |
       v
Aware callbacks, property population
       |
       v
BeanPostProcessor before initialization
       |
       v
Init callbacks
       |
       v
BeanPostProcessor after initialization (có thể proxy)
       |
       v
Expose bean
       |
       v
Context close -> destruction callbacks
~~~

Đây là luồng rút gọn; chi tiết Spring có nhiều processors/phases và singleton early exposure. Mục tiêu là **không nhầm cấu hình node với lifecycle runtime**.

### Cách khai báo init/destroy khác nhau

XML:

~~~xml
<bean id="specialClient"
      class="example.SpecialClient"
      init-method="connect"
      destroy-method="close"/>
~~~

Annotation:

~~~java
@Bean(initMethod = "connect", destroyMethod = "close")
SpecialClient specialClient() {
    return new SpecialClient();
}
~~~

Programmatic:

~~~java
context.registerBean("specialClient", SpecialClient.class,
        SpecialClient::new,
        bd -> {
            bd.setInitMethodName("connect");
            bd.setDestroyMethodName("close");
        });
~~~

Các snippets cần class `SpecialClient` có public methods connect/close; không thuộc chương trình mẫu. Khi migrate, phải mang cả metadata init/destroy, không chỉ constructor args. Nếu không, một resource client có thể được tạo nhưng không được initialize/destroy đúng.

### Không dựa vào startup order ngoài dependencies

Nếu `catalogRepository` và `taxPolicy` độc lập, thứ tự khởi tạo giữa hai bean không phải business contract. Khi xác thực tương đương, không assert chúng được tạo theo cùng một order trừ khi bạn đã khai báo ordering dependency có ý nghĩa. `@DependsOn` và `depends-on` có thể diễn đạt init ordering, nhưng không nên thêm chúng chỉ để “fix” một bài kiểm thử sai.

## 17. Các khác biệt có chủ đích không phá graph equivalence

Ba biến thể không bắt buộc giống nhau ở mọi thông tin nội bộ.

| Khác biệt | Có ảnh hưởng tới business graph? | Vì sao |
| --- | --- | --- |
| Có bean `AnnotatedGraph` trong annotation context | Không nếu không inject nó vào business beans | Hạ tầng cấu hình |
| Annotation processors hiện diện | Không nhất thiết | Chỉ ảnh hưởng nếu xử lý metadata liên quan |
| XML parser/schema resolution | Không sau khi BeanDefinitions tương ứng nạp xong | Cơ chế build metadata |
| Supplier callback vs @Bean factory method vs reflective constructor | Không nếu object/classes/args/edges giống | Đường instantiation khác |
| Thứ tự bean definition registration | Không mặc nhiên | Lookup resolution xác định dependencies |
| BeanDefinition resource description | Không | Metadata nguồn/diagnostics |
| Context id/display name | Không trong ví dụ | Không dùng trong nghiệp vụ |
| AOP proxy làm object không cùng runtime class | Có thể | Type/identity/interception có thể đổi |
| Scope/lazy flag khác | Có | Lifecycle và object identity có thể đổi |
| @Profile/conditional registration khác | Có | Tập BeanDefinitions có thể đổi |
| @Value/property precedence khác | Có | Constructor values thay đổi |
| Custom BeanPostProcessor khác | Có thể | Có thể wrap/replace exposed reference |

**Định nghĩa tương đương trước khi so.** Nếu yêu cầu chỉ là business output, phép so yếu hơn. Nếu cần migrate production an toàn, đưa cả identity, scope, qualifiers, lifecycle, AOP, property resolution và error behavior vào contract.

## 18. Profile và conditional registration giữa ba phong cách

Nếu production muốn chọn `TaxPolicy` khác nhau theo môi trường, có nhiều cách:

Annotation:

~~~java
@Bean
@Profile("prod")
TaxPolicy productionPolicy() {
    return new TaxPolicy(10);
}
~~~

XML:

~~~xml
<beans profile="prod">
    <bean id="taxPolicy"
          class="example.GraphComparisonDemo$TaxPolicy">
        <constructor-arg value="10"/>
    </bean>
</beans>
~~~

Programmatic Java:

~~~java
if (context.getEnvironment().acceptsProfiles(
        org.springframework.core.env.Profiles.of("prod"))) {
    context.registerBean("taxPolicy", TaxPolicy.class,
            () -> new TaxPolicy(10));
}
~~~

**Đây là các snippets khác nhau**, không đồng thời ghép vào cấu hình đang đăng ký `taxPolicy` mặc định của ví dụ. Tất cả phải được xử lý **trước refresh** để tạo đúng graph theo profile.

### Tại sao thuộc tính không tự chọn profile?

`@Value("${app.mode}")` chỉ resolve một property; `@Profile("prod")` là condition trên bean definition. `app.mode=prod` không tự đồng nghĩa active profile `prod`. Nếu hai mechanisms cần đồng bộ, khai báo source-of-truth rõ và test cả profile selection lẫn value.

### @Conditional

Annotation `@Conditional` hỗ trợ điều kiện bean registration khác với `@Profile`. Programmatic Java có thể dùng if/branch; XML có XML profile sections nhưng không tự có mọi custom Conditional semantics. Khi migrate, cần xét **tập business nodes được đăng ký** cho mỗi environment, không chỉ refactoring cú pháp.

## 19. Properties và type conversion phải nhất quán

Bài mẫu dùng scalar literals `"catalog-v1"`/`10` để tập trung vào graph. Dự án thực tế có thể lấy chúng từ external configuration; khi đó, cả ba versions phải đọc cùng effective values:

Programmatic:

~~~java
String catalogId =
        context.getEnvironment().getRequiredProperty(
                "app.catalog.id");
~~~

Annotation:

~~~java
@Bean
CatalogRepository catalogRepository(
        @Value("${app.catalog.id}") String id) {
    return new CatalogRepository(id);
}
~~~

XML:

~~~xml
<bean id="catalogRepository"
      class="example.GraphComparisonDemo$CatalogRepository">
    <constructor-arg value="${app.catalog.id}"/>
</bean>
~~~

Các snippets cần đặt đúng environment/property source. XML placeholder có thể cần `PropertySourcesPlaceholderConfigurer` hoặc infrastructure tương ứng; không tự giả định mọi XML context đều resolve `${...}` bằng cùng cơ chế annotation context.

### Environment/BeanFactory type conversion

- `Environment.getProperty(key, Integer.class)` dùng property resolver conversion service.
- Constructor arg trong XML `value="10"` được BeanFactory type converter convert sang `int`.
- `@Value("${app.percent}") int` cũng dùng conversion infrastructure phù hợp ở injection phase.
- Domain type tự định nghĩa có thể cần `Converter<String,T>`/`ConversionService`.
- Converter được đăng ký ở Environment không tự chứng minh `@Value`/BeanFactory dùng cùng converter.

Đây là nơi một migration XML→annotation có thể thất bại dù graph types nhìn tương tự: property source precedence, placeholders hoặc converter registration timing khác.

### Không đặt cấu hình bí mật vào README/test logs

Đừng in passwords, API keys hay database connection strings khi debug property sources. Ghi bean names/keys và nguồn property đã được sanitized; secret values nên được cấp từ môi trường hoặc secrets manager với quyền thích hợp.

## 20. XML vs annotation: việc xử lý container metadata

XML configuration được đọc qua `XmlBeanDefinitionReader` hoặc tiện ích context XML. Annotation configuration được xử lý bởi `ConfigurationClassPostProcessor`/`@Bean` method metadata. Programmatic registration thêm BeanDefinition trực tiếp.

~~~text
                   +--------------------+
Java registerBean ->|                    |
                   | BeanDefinition     |
@Configuration --->| Registry           |----> BeanFactory
                   |                    |         |
XML <bean> ------->|                    |         v
                   +--------------------+     Live objects
~~~

Đồ thị sinh ra có thể giống, nhưng các registry entries có metadata khác nhau:

- XML BeanDefinition có resource description/tên factory metadata khác.
- `@Bean` method có declaring configuration class và factory method metadata.
- Supplier BeanDefinition có instance supplier callback.
- Lifecycle/AOP/conditions có thể được đóng góp từ annotation processors, không tự xuất hiện trong programmatic/XML contexts trừ khi được cấu hình.
- BeanDefinition names và aliases khác nhau nếu không chuẩn hóa.

### Không phản ánh mọi cạnh bằng getDependsOn

`BeanDefinition.getDependsOn()` mô tả explicit depends-on ordering metadata, **không phải danh sách đầy đủ constructor dependencies**. Trong ba implementations, service có thể cần repository/policy qua constructor parameters dù `getDependsOn()` null. Để kiểm chứng graph này, ta so actual object identity edges sau khi context được load.

### Đồ thị đối tượng có proxy có thể khác

Nếu `BeanPostProcessor` trả proxy thay cho target, reference identity/type cần được xét trên **exposed beans**. Test không nên bóc raw target ra để “làm cho identity match” nếu mục đích là kiểm tra consumer contract. Trong bài mẫu không bật AOP/transaction proxies nên phép so trực tiếp rất rõ.

## 21. Chuyển XML sang annotation một cách kiểm soát

Một lộ trình migration:

1. **Chụp lại contract**: tên beans/aliases, concrete/exposed types, scopes, constructor references, factory-bean behavior, init/destroy callbacks, profile/conditional rules.
2. **Chạy baseline**: dựng XML context và dùng assertions cho graph/behavior như ví dụ.
3. **Viết annotation configuration** với `@Bean("sameName")`, required constructor parameters, `@Scope`/`@Lazy`/`@DependsOn` tương ứng nếu cần.
4. **Tách tránh duplicate registration**: không import XML cùng annotation config mà tạo hai beans cho cùng business type/name ngoài chủ đích.
5. **So effective configuration**: property source precedence, placeholders, conversion service, environment active profiles.
6. **Chạy integration tests** trên từng representation độc lập; không test bằng cách load cả hai vào cùng context rồi để one override the other.
7. **Kiểm tra lifecycle/identity** chứ không chỉ response strings.
8. **Kiểm tra AOP** nếu beans có advice; proxy strategy có thể khác khi bật processors.
9. **Di chuyển từng feature** với regression tests trước/sau; giữ rollback nếu external deployment phụ thuộc XML.
10. **Loại bỏ registry cũ sau khi ổn định**, tránh hai sources of truth song song dài hạn.

### Vì sao không load hai cấu hình cùng lúc để so?

Nếu XML đăng ký `shopFacade` và JavaConfig cũng đăng ký `shopFacade` trong cùng context, bean-definition overriding policy sẽ quyết định fail hay ghi đè. Bạn sẽ không còn hai implementations độc lập để so. Hãy dùng **ba contexts riêng** với cùng verification function.

### Rollback strategy

Trong dự án lớn, migration có thể được thực hiện theo module: lưu XML version sẵn để rollback, kiểm thử cùng integration contracts trong CI và deployment profiles khác nhau. Đừng xem một demo graph đơn giản là bằng chứng toàn bộ ứng dụng legacy đã tương đương.

## 22. Chuyển annotation sang Java programmatic

Động cơ có thể là module/plugin registration programmatically, AOT-friendly functional registration trong vài bối cảnh hoặc yêu cầu inject dependencies qua Supplier.

### Những lợi ích

- Dễ kiểm soát đăng ký theo runtime bootstrap flags **trước refresh**.
- BeanDefinitionCustomizer cho phép cấu hình scope/primary/lazy/dependsOn.
- Không cần component scanning cho những beans đã đăng ký.
- Logic registration có thể nằm trong một module registrar/test bootstrap class.

### Những điểm cần giữ

- Bean name giống annotation version.
- Required type ở `registerBean(name, type, supplier)` chính xác.
- Supplier không bỏ sót collaborator hoặc truyền wrong instance.
- Tất cả scalar configuration cần nguồn/validation tương ứng.
- Không gọi `getBean` trước `refresh()` khi BeanFactory chưa chuẩn bị.
- Không cố tạo bean singleton bằng `new` từ code client rồi kỳ vọng container quản lý lifecycle object ấy.
- Nếu bắt exception trong Supplier, đừng nuốt khiến BeanFactory expose một bean chưa hợp lệ.

### Lỗi loại “same output, wrong graph”

~~~java
context.registerBean(
        "checkoutService", CheckoutService.class,
        () -> new CheckoutService(
                new CatalogRepository("catalog-v1"),
                new TaxPolicy(10)));
~~~

Đoạn này có thể **in cùng receipt** nhưng references trỏ tới **objects mới ngoài hai bean definitions**. `service.repository() == context.getBean("catalogRepository")` sẽ là false. Đó là lý do identity-edge tests rất quan trọng.

## 23. Tests ngoài happy path

### Thiếu một bean dependency

Nếu XML `ref="taxPolicy"` không có definition, hoặc JavaConfig service method nhận type không có matching bean, container thường fail khi cần instantiate `checkoutService`. Có thể thấy `NoSuchBeanDefinitionException` được bọc trong `UnsatisfiedDependencyException` hoặc `BeanCreationException`.

### Sai kiểu được đăng ký theo tên

Nếu programmatic registration đặt bean tên `taxPolicy` nhưng actual type không assignable sang TaxPolicy, `getBean("taxPolicy", TaxPolicy.class)` fail. Đừng sửa bằng unchecked cast.

### Multiple candidates

Nếu annotation scanning tạo hai TaxPolicy beans và service method chỉ nhận một `TaxPolicy`, cần `@Qualifier`/`@Primary` hoặc cấu hình registration rõ. XML dùng explicit ref có thể không gặp ambiguity tương tự; migration phải đảm bảo **resolution outcome tương đương**.

### Sai scope

Nếu một variant dùng prototype trong khi hai variants singleton, `isSingleton` của nó khác và identity semantics thay đổi.

### Constructor cycle

Nếu vô ý thêm edge `catalogRepository → shopFacade`, toàn business graph có vòng `shopFacade → checkoutService → catalogRepository → shopFacade`. Với constructor dependency cứng, context có thể fail `BeanCurrentlyInCreationException`. Đổi representation không xóa vòng.

### XML class name sai

Vì business types là nested classes, `example.GraphComparisonDemo$CheckoutService` phải match Java binary class name. Nếu viết sai dấu dollar hoặc package, XML loader có thể báo `CannotLoadBeanClassException` trong quá trình tạo BeanDefinition/bean.

### XML constructor arg conversion sai

`TaxPolicy(int)` yêu cầu integer percentage; `value="ten"` không convert được. Điều đó khác lỗi thiếu reference `ref="taxPolicy"`. Đọc root cause để phân loại đúng.

### Resource không nằm trên classpath

Nếu `src/main/resources/graph/graph.xml` không được Maven đóng gói đúng hoặc typo đường dẫn, `ClassPathXmlApplicationContext` không load được resource; lỗi có thể là `BeanDefinitionStoreException`/FileNotFoundException tùy đường gọi. Trước khi sửa Java classes, xác nhận file path/case sensitivity.

## 24. Bộ checklist xác nhận graph equivalence

Dùng checklist này khi áp dụng vào dự án thật:

1. Có xác định đúng các **business nodes** và không lẫn Spring infrastructure beans?
2. Tất cả beans có cùng **names**/aliases theo contracts?
3. Declared types và exposed types có tương thích injection points?
4. Đồ thị constructor/reference edges có đúng?
5. Các scalar arguments/values có bằng nhau?
6. Các scopes có tương đương?
7. Các beans lazy/eager có cùng semantics?
8. Các factory methods/Suppliers có trả đúng object không?
9. Java programmatic có gọi refresh chính xác một lần?
10. XML file/class names đúng package/resource paths?
11. @Bean methods có tránh tự gọi khi proxyBeanMethods=false?
12. @ComponentScan có vô tình thêm candidates/duplicates?
13. Profile/conditional registrations có chọn cùng graph?
14. Property sources và placeholders có cùng effective values?
15. ConversionService/PropertyEditors có được cấu hình đúng chỗ?
16. AOP proxying có làm exposed type/identity khác?
17. Init/destroy callbacks/aware interfaces có được giữ?
18. BeanPostProcessors/FactoryBean semantics có được chuyển tương đương?
19. Singleton identity được kiểm chứng bằng `==`?
20. Prototype/scoped dependencies có resolution timing đúng?
21. Behavior-oriented outputs có được kiểm chứng?
22. Tình huống thiếu bean/sai type/conversion được test?
23. Có dùng ba independent contexts để đối chiếu không?
24. Có tránh ép bean-definition-count equality không?
25. Có đóng contexts/resources ở cuối tests/CLI?
26. Không dùng static mutable state làm leak giữa contexts?
27. Nếu parallel testing, fixture side effects có độc lập?
28. Có ghi nhận các khác biệt infrastructure được phép?
29. Có integration tests chạy ở profiles production quan trọng?
30. Mọi changes của migration có contract tests và rollback rõ?

## 25. Câu hỏi tự kiểm tra

**Câu 1.** Cùng một graph có nhất thiết có cùng BeanDefinition count trong Java, annotation và XML không?

**Trả lời:** Không. Annotation context có thể thêm infrastructure/configuration beans; so business subgraph và contracts cần thiết.

**Câu 2.** Vì sao identity-edge tests hữu ích hơn chỉ so receipt?

**Trả lời:** Hai graphs có thể cùng output nhưng một graph inject object ngoài container hoặc sai singleton reference.

**Câu 3.** `GenericApplicationContext.registerBean(..., Supplier)` có tạo object ngay lúc đăng ký không?

**Trả lời:** Không mặc nhiên; Supplier thường được gọi khi BeanFactory cần instantiate bean, ví dụ trong refresh cho eager singleton.

**Câu 4.** `@Bean checkoutService(CatalogRepository,TaxPolicy)` biểu diễn cạnh như thế nào?

**Trả lời:** Spring resolve bean candidates cho hai factory method parameters và cung cấp actual references trước khi gọi method.

**Câu 5.** Trong XML, `ref` và `value` khác nhau ra sao?

**Trả lời:** `ref` tra một bean theo name; `value` là scalar string cần conversion theo required constructor/property type.

**Câu 6.** Tên `GraphComparisonDemo$TaxPolicy` trong XML có phải một Spring placeholder không?

**Trả lời:** Không. Nó là Java binary name của static nested class.

**Câu 7.** Vì sao ba contexts không dùng chung cùng Java singleton object?

**Trả lời:** Spring singleton scope giới hạn theo bean definition/BeanFactory; ba containers độc lập tạo ba instance sets.

**Câu 8.** Có nên gọi `getBean` trong Supplier trước refresh không?

**Trả lời:** Không trong bootstrap flow của bài; chỉ resolve collaborators khi Supplier được BeanFactory gọi trong creation phase.

**Câu 9.** Với `proxyBeanMethods=false`, gọi trực tiếp `catalogRepository()` từ @Bean method khác có chắc trả singleton không?

**Trả lời:** Không. Đó là Java method call bình thường, có thể tạo object khác; dùng @Bean method parameters để container inject.

**Câu 10.** `@ComponentScan` có bắt buộc cho @Configuration/@Bean không?

**Trả lời:** Không. Đăng ký explicit @Configuration class đủ để xử lý @Bean methods, nếu annotation infrastructure được cấu hình.

**Câu 11.** Nếu một XML bean chuyển thành @Component mặc định, bean name có luôn giống id XML không?

**Trả lời:** Không. Cần dùng explicit names hoặc điều chỉnh dependent qualifiers/refs.

**Câu 12.** Chuyển singleton thành prototype có giữ tương đương chỉ vì output không đổi không?

**Trả lời:** Không; scope/identity và lookup timing khác.

**Câu 13.** `BeanDefinition.getDependsOn()` có luôn liệt kê hai constructor dependencies của checkoutService không?

**Trả lời:** Không. Nó chủ yếu phản ánh explicit depends-on metadata, không phải mọi dependency edge.

**Câu 14.** Vì sao không nên load XML và annotation version vào cùng context để “so”?

**Trả lời:** Hai configurations có thể đăng ký trùng bean names, bị override hoặc gây ambiguity; không còn là hai contexts độc lập để kiểm chứng.

**Câu 15.** Programmatic Supplier tạo `new CatalogRepository` riêng có thể sai dù receipt giống không?

**Trả lời:** Có; reference không còn trỏ tới catalogRepository singleton đã đăng ký.

**Câu 16.** Nếu app dùng @Profile("prod"), đặt một property `app.mode=prod` có tự bật bean không?

**Trả lời:** Không. Active profile là metadata điều kiện riêng.

**Câu 17.** Khi XML conversion `value="ten"` thành int fail, nên thêm @DependsOn không?

**Trả lời:** Không. Lỗi conversion, không phải init ordering; cần sửa scalar value/conversion policy.

**Câu 18.** Mục tiêu chính của migration từ XML sang annotation là gì?

**Trả lời:** Thay cách khai báo mà giữ behavior, bean names/types, identity edges, scope, lifecycle, configuration semantics tương đương theo contract.

## 26. Tài liệu tham khảo

- [Spring Framework 6.2 Javadoc — GenericApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/GenericApplicationContext.html)
- [Spring Framework 6.2 — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java/instantiating-container.html)
- [Spring Framework 6.2 — Bean Definition DSL / functional registration](https://docs.spring.io/spring-framework/reference/6.2/languages/kotlin/bean-definition-dsl.html)
- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html)
- [Spring Framework 6.2 — XML-based Configuration Metadata](https://docs.spring.io/spring-framework/reference/6.2/core/beans/basics.html)
- [Spring Framework 6.2 — Dependencies and Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 6.2 — Constructor-based Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html)
- [Spring Framework 6.2 — Bean Factory Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html)
- [Spring Framework 6.2 Javadoc — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html)
- [Spring Framework 6.2 Javadoc — ClassPathXmlApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/ClassPathXmlApplicationContext.html)
- [Spring Framework 6.2 Javadoc — @Configuration](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Configuration.html)
- [Spring Framework 6.2 Javadoc — @Bean](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html)
- [Spring Framework 6.2 Javadoc — BeanDefinition](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanDefinition.html)
- [Spring Framework 6.2 Javadoc — ApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/ApplicationContext.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-004">Thực nghiệm singleton, prototype, lazy và provider</a>

<details>
<summary>Xem chi tiết</summary>

# Thực nghiệm singleton, prototype, lazy và provider trong Spring Core Container

## 1. Mục tiêu: đo hành vi thực thay vì chỉ học tên annotation

Một ứng dụng Spring thường có nhiều bean với vòng đời và thời điểm khởi tạo khác nhau. Nếu chỉ nhìn annotation `@Scope("singleton")`, `@Scope("prototype")` và `@Lazy`, rất dễ kết luận sai rằng prototype luôn được tạo “theo mỗi lần gọi method”, rằng lazy luôn tránh tạo bean khi context khởi động, hoặc rằng `ObjectProvider<T>` luôn trả object mới.

Bài này thuộc nhóm **Thực hành tổng hợp** của module **Spring Framework - core-container**, với chủ đề trực tiếp **Thực nghiệm singleton, prototype, lazy và provider**. Thay vì chỉ nêu lý thuyết, chúng ta xây **các ApplicationContext độc lập**, dùng counters và identity comparisons để quan sát:

1. Singleton mặc định thường được khởi tạo khi `ApplicationContext.refresh()`.
2. Singleton trả **cùng exposed bean instance** cho các lần lookup trong một container.
3. Prototype tạo **instance khác** khi có yêu cầu mới từ BeanFactory.
4. Inject prototype trực tiếp vào singleton chỉ cấp **một instance ở thời điểm singleton được xây dựng**.
5. `ObjectProvider<Prototype>` cho phép singleton tự yêu cầu prototype mới mỗi lần cần.
6. `ObjectProvider<Singleton>` vẫn trả singleton được chia sẻ, không tạo mới.
7. `@Lazy` trên singleton definition trì hoãn tạo cho đến lần cần đầu tiên.
8. `@Lazy` không tránh việc khởi tạo khi một eager singleton yêu cầu dependency ấy ngay lúc startup.
9. `@Lazy` tại injection point có thể cấp lazy-resolution proxy giúp consumer được tạo trước target.
10. `getIfAvailable()`/`getIfUnique()` khác `getObject()` về semantics thiếu và không duy nhất.
11. Prototype destroy callbacks thường **không** được container tự quản lý như singleton.
12. Thời điểm tạo bean, bean definition, proxy reference, scope, cleanup và thread-safety là những chiều khác nhau.

Tất cả thí nghiệm chính chạy với **Java 17 + Maven + Spring Framework 6.2.19**, không yêu cầu Spring Boot, HTTP server hay database.

## 2. Ba trục cần phân biệt

| Trục | Câu hỏi | Ví dụ |
| --- | --- | --- |
| Scope | Mỗi lần lookup có cùng instance không? | singleton, prototype, request |
| Eagerness | Bean được khởi tạo khi nào? | eager, lazy |
| Retrieval | Consumer nhận object bằng cách nào? | constructor injection, getBean, ObjectProvider, lazy proxy |

Không có quy tắc cố định “lazy = prototype” hoặc “provider = prototype”. Một singleton có thể lazy; một prototype thường được khởi tạo theo yêu cầu; `ObjectProvider` có thể resolve singleton hoặc prototype tùy target bean definition.

~~~text
BeanDefinition
  ├── scope = singleton / prototype / web / custom
  ├── lazyInit = true / false
  └── dependency metadata

ApplicationContext
  ├── refresh()
  │    ├── eager singleton instances
  │    └── lazy/prototype definitions chưa cần instantiate
  |
  ├── getBean("singleton") ──> shared singleton
  ├── getBean("prototype") ──> fresh prototype
  └── provider.getObject() ──> kết quả phụ thuộc scope/lookup hiện tại
~~~

`BeanDefinition` là metadata; việc có definition không chứng minh constructor đã chạy. Chỉ sau khi BeanFactory resolve và tạo instance mới có actual object để so sánh identity hoặc sử dụng.

## 3. Singleton: một bean instance được chia sẻ trong **một container**

Singleton là default scope của Spring. Trong cùng một container, đối với một named singleton, các lời gọi lookup thông thường trả về **cùng exposed object** đã được container công bố:

~~~java
Object first = context.getBean("pricingService");
Object second = context.getBean("pricingService");
boolean same = first == second;
~~~

Đây là snippet, giả sử pricingService được đăng ký singleton, không có bất thường từ custom FactoryBean/proxy machinery. Khi đó `same` là true.

### Spring singleton khác GoF Singleton pattern

GoF singleton thường kiểm soát một instance cấp JVM/classloader bằng static field hoặc private constructor. Spring singleton được quản lý **theo bean name và bean container**, không có bảo đảm toàn ứng dụng chỉ một Java object thuộc class ấy.

~~~text
Context A: "pricingService" ──> instance A
Context B: "pricingService" ──> instance B
~~~

Ngay cả hai BeanDefinitions cùng implementation class trong **một** context có thể tạo hai singleton instances riêng, do chúng có hai bean names/definitions.

### Eager singleton mặc định

`ApplicationContext` thường pre-instantiate non-lazy singleton beans khi `refresh()`. Điều này giúp phát hiện missing dependencies/configuration failures sớm. `DefaultListableBeanFactory` thuần dùng programmatic API có thể hoạt động khác nếu không gọi pre-instantiation; vì vậy demo dùng ApplicationContext để chứng minh **startup eager semantics**.

### Singleton và thread safety

Spring **không tự đồng bộ** các mutable fields trong một singleton. Nếu nhiều requests gọi cùng singleton, các threads có thể cùng đụng một object. Dùng immutable collaborators, stateless service hoặc concurrency mechanisms thích hợp. Scope chỉ quy định instance lifecycle/lookup, không làm code thread-safe.

### Singleton và proxy

Nếu bean được AOP proxied, `getBean` có thể trả cùng singleton proxy thay vì raw target. Thí nghiệm identity `==` đang nói về **object được container expose**, không có nghĩa proxy và target underlying là cùng Java object.

## 4. Prototype: tạo mỗi khi BeanFactory được yêu cầu

Prototype scope được cấu hình bằng:

~~~java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public WorkerUnit workerUnit() {
    return new WorkerUnit();
}
~~~

Đây là snippet; cần imports `Bean`, `Scope`, `ConfigurableBeanFactory` và domain type WorkerUnit. Khi gọi:

~~~java
WorkerUnit a = context.getBean(WorkerUnit.class);
WorkerUnit b = context.getBean(WorkerUnit.class);
~~~

Spring thực hiện creation riêng cho mỗi lookup. Với factory tạo một object mới như trên, `a != b`.

### Prototype không được eager tạo đại trà lúc refresh

Thông thường container **không pre-instantiate tất cả prototype definitions**. Một prototype sẽ được tạo khi dependency resolution hoặc lookup yêu cầu, kể cả trong quá trình refresh nếu nó là dependency của eager singleton.

### Mỗi lần resolve, không phải mỗi lần business method call

Nếu consumer đã được inject một prototype object, lời gọi `consumer.process()` nhiều lần có thể tiếp tục sử dụng **cùng object đó**. Prototype scope không tự thêm một proxy dynamic lookup vào mọi injection site.

### Prototype có lifecycle khác singleton

Spring tạo object, inject dependencies, áp init callbacks/BPP rồi giao instance cho caller. Tuy nhiên container **không tự gọi destroy callbacks cho mọi prototype instance** khi context close như với singleton được quản lý. Caller cần tự giải phóng resources khi prototype sở hữu resource lifecycle, hoặc dùng custom lifecycle/disposal strategy.

Đừng tạo prototype AutoCloseable rồi kỳ vọng Spring tự gọi `close()` sau mỗi `getBean(...)` hoặc mỗi method invocation.

## 5. Prototype inject vào singleton: thí nghiệm gây hiểu nhầm nhiều nhất

Giả sử:

~~~java
@Component
public class ReportManager {

    private final ReportJob job;

    public ReportManager(ReportJob job) {
        this.job = job;
    }

    public void run() {
        job.execute();
    }
}
~~~

`ReportManager` là singleton và `ReportJob` prototype. Khi Spring tạo ReportManager, nó resolve ReportJob **một lần** cho constructor, rồi singleton giữ reference đó.

~~~text
refresh()
    |
    v
create ReportManager singleton
    |
    v
resolve ReportJob prototype -> Job #1
    |
    v
ReportManager.job = Job #1
    |
    v
run() -> Job #1
run() -> Job #1
run() -> Job #1
~~~

Do đó không viết logic “mỗi run sẽ có Job mới” chỉ vì ReportJob có prototype scope.

### Vì sao điều này hợp lý?

Dependency injection xảy ra tại **điểm xây object owner**. Spring không tự ghi đè private final field theo mỗi method invocation. Một constructor-injected collaborator là reference bình thường, bất kể bean definition kia là prototype.

### Khi nào inject trực tiếp prototype vẫn đúng?

Nếu singleton manager chỉ muốn giữ một collaborator có initialization riêng, việc inject prototype một lần là hợp lệ, dù thường dùng singleton cũng đủ. Prototype đặc biệt hữu ích khi **mỗi lần caller yêu cầu object có state riêng**; lúc ấy nên chuyển sang deferred lookup/factory.

## 6. ObjectProvider<T>: trì hoãn resolution đến lúc gọi

`org.springframework.beans.factory.ObjectProvider<T>` là Spring-specific extension của `ObjectFactory<T>`, có các methods như:

~~~java
T value = provider.getObject();
T optional = provider.getIfAvailable();
T unique = provider.getIfUnique();
~~~

Đây là API snippets: `provider` là ObjectProvider<T> với T được xác định khi inject.

### Provider là handle đến BeanFactory, không tự quyết scope

~~~text
Singleton service
      |
      v
ObjectProvider<ReportJob>
      |
      +-- getObject() -> resolve ReportJob -> Job #1
      +-- getObject() -> resolve ReportJob -> Job #2
      +-- getObject() -> resolve ReportJob -> Job #3
~~~

Với prototype target, mỗi `getObject()` yêu cầu factory tạo một instance mới.

~~~text
Singleton service
      |
      v
ObjectProvider<SharedCache>
      |
      +-- getObject() -> Cache singleton
      +-- getObject() -> same Cache singleton
~~~

Với singleton target, mỗi provider lookup vẫn trả **cùng cached singleton**. Provider không có logic “clone” object bất kể scope.

### Cách dùng trong consumer

~~~java
public final class ReportManager {

    private final ObjectProvider<ReportJob> jobs;

    public ReportManager(ObjectProvider<ReportJob> jobs) {
        this.jobs = jobs;
    }

    public void runOneReport() {
        ReportJob job = jobs.getObject();
        job.execute();
    }
}
~~~

Đây là snippet cần ReportJob interface/class thật. `ReportManager` có thể là singleton; mỗi `runOneReport` gọi provider để nhận prototype mới nếu BeanDefinition là prototype.

### Ưu và nhược điểm

Ưu điểm: không cần expose ApplicationContext trong business service, dễ biểu diễn creation on demand, Spring sẽ thực hiện dependency injection/lifecycle init cho mỗi prototype.

Nhược điểm: component biết Spring ObjectProvider API; lỗi missing/ambiguous/deferred creation có thể xuất hiện tại **runtime call**; caller phải quyết định cách cleanup prototype; cần test concurrency nếu các jobs có shared resources.

Một phương án ít coupling hơn là tự định nghĩa domain factory interface `JobFactory` và cung cấp Spring-managed implementation dùng ObjectProvider.

## 7. getObject, getIfAvailable và getIfUnique

Spring 6.2 Javadoc phân biệt:

| Provider API | Không có bean match | Nhiều beans, không chọn được duy nhất | Có unique bean |
| --- | --- | --- | --- |
| `getObject()` | Ném `NoSuchBeanDefinitionException` | Ném `NoUniqueBeanDefinitionException` | Trả bean |
| `getIfAvailable()` | Trả null | Vẫn có thể ném `NoUniqueBeanDefinitionException` | Trả bean |
| `getIfUnique()` | Trả null | Trả null | Trả bean |
| `getIfAvailable(supplier)` | Dùng supplier tạo fallback | Vẫn có thể ném ambiguity | Trả bean |
| `getIfUnique(supplier)` | Dùng supplier tạo fallback | Dùng supplier | Trả bean |

`getIfAvailable()` **không** phải lời hứa “sẽ trả null trong mọi lỗi resolution”; nếu bean tồn tại nhưng factory method fail, exception creation vẫn có thể propagate. `getIfUnique()` null khi không có unique candidate, nhưng nếu một candidate được chọn rồi quá trình construct fail, đó vẫn là lỗi.

### @Primary / @Fallback và uniqueness

Uniqueness được giải theo Spring candidate-resolution rules, không đơn giản dựa vào `count(getBeanNamesForType) == 1`. Một `@Primary` hoặc `@Fallback` có thể dẫn đến một winner hợp lệ. Từ Spring 6.2, `defaultCandidate` cũng ảnh hưởng một số form resolution. Nên kiểm tra actual provider calls bằng context fixture có đầy đủ BeanDefinitions.

### Supplier fallback không biến object thành Spring bean

~~~java
ExtraConfig fallback =
        provider.getIfAvailable(ExtraConfig::new);
~~~

Đây là snippet với ExtraConfig constructor phù hợp. Nếu không có managed bean, supplier trả một Java object từ application code, **không tự được đăng ký thành BeanDefinition**, không mặc nhiên được Spring inject dependencies hoặc quản lý destroy callbacks.

### ObjectProvider.stream và orderedStream

`stream()` trả những matching objects theo semantics provider/qualifier, không bảo đảm ordering rõ; `orderedStream()` xét common Spring order comparator (ví dụ `@Order`/`Ordered`). Với stream trên prototype candidates, việc iterate có thể instantiate instances; mỗi lần stream/iteration không nên bị coi là cache bền vững.

Trong thí nghiệm cần output ổn định, đừng dựa vào thứ tự ngẫu nhiên; sort names hoặc kiểm tra theo set/identity.

## 8. @Lazy trên bean definition: hoãn tới lần đầu cần

~~~java
@Bean
@Lazy
public ExpensiveCatalog catalog() {
    return new ExpensiveCatalog();
}
~~~

`@Lazy` trên bean definition nói với Spring: **đừng pre-instantiate singleton đó khi startup** chỉ vì nó là singleton. Khi lần đầu lookup hoặc dependency resolution yêu cầu instance, Spring tạo nó; các lần sau cùng bean name/definition được cache singleton.

~~~text
refresh() -> catalog chưa tạo
getBean("catalog") -> tạo Catalog #1
getBean("catalog") -> trả Catalog #1
~~~

### @Lazy không thay scope

Bean trên vẫn singleton, không tự trở thành prototype. Ngược lại, đánh dấu một prototype `@Lazy` thường không thay đổi điều quan trọng: prototype vốn không được pre-instantiate đại trà; tạo khi factory cần.

### Eager dependency vẫn buộc lazy bean khởi tạo

Đây là điểm đặc biệt quan trọng. Nếu có eager singleton:

~~~java
@Bean
public Controller controller(ExpensiveCatalog catalog) {
    return new Controller(catalog);
}
~~~

và ExpensiveCatalog được `@Lazy` ở bean method, BeanFactory vẫn phải tạo catalog để xây Controller khi refresh. Vì thế catalog được tạo **ngay trong startup**.

~~~text
refresh()
    |
    v
create eager Controller
    |
    v
resolve ExpensiveCatalog
    |
    v
create @Lazy Catalog
    |
    v
inject into Controller
~~~

Không kết luận lazy “không hoạt động”. `@Lazy` chỉ hoãn pre-instantiation độc lập; nó không cho phép Spring bỏ qua required dependencies của một eager consumer.

## 9. @Lazy tại injection point: lazy-resolution proxy

Spring hỗ trợ `@Lazy` trên injection point:

~~~java
@Bean
public Dashboard dashboard(
        @Lazy AnalyticsService analytics) {

    return new Dashboard(analytics);
}
~~~

Đây là snippet; AnalyticsService phải là bean hợp lệ và Dashboard có constructor phù hợp.

Đặt annotation **trên parameter** có thể khiến Spring inject một **lazy-resolution proxy** vào Dashboard. Dashboard được tạo trước target, và target chỉ được resolve khi method được gọi trên proxy.

~~~text
refresh()
    |
    v
create eager Dashboard
    |
    v
inject lazy proxy for AnalyticsService
    |
    v
Dashboard ready, actual AnalyticsService chưa tạo
    |
  dashboard.render()
    |
    v
proxy resolves AnalyticsService
    |
    v
create target, call method
~~~

### Hai vị trí annotation mang ý nghĩa khác nhau

| Vị trí | Ý nghĩa chính |
| --- | --- |
| `@Lazy` trên `@Bean`/`@Component` definition | Hoãn pre-instantiation của chính bean |
| `@Lazy` trên field/constructor/method parameter | Inject lazy-resolution proxy cho dependency |
| `ObjectProvider<T>` injection | Inject provider handle; caller quyết định khi nào gọi `getObject()` |

Nếu một eager consumer inject dependency trực tiếp (không lazy proxy), dù target bean definition là lazy, target vẫn có thể phải instantiate trong refresh.

### Hạn chế lazy proxy

JDK interface proxy expose interface methods, không tự là instance concrete implementation; class proxy chịu giới hạn final class/final/private methods. Proxy có thể làm exception missing bean xuất hiện **lúc first method call** chứ không phải startup. Không dùng `@Lazy` để giấu lỗi quan trọng đến lúc request production đầu tiên.

### Lazy proxy khác provider

- Lazy proxy: consumer gọi interface như object bình thường; proxy tự tìm target.
- ObjectProvider: consumer **chủ động** gọi `getObject()`/`getIfAvailable()`.
- Nếu target là prototype, proxy semantics có thể lấy target theo mỗi invocation/target source, **không nên giả định** reference proxy == singleton target hoặc cache một prototype duy nhất; test đúng configuration.
- Nếu target singleton, sau lần resolve đầu, những lời gọi qua proxy thường delegate tới cùng singleton.

## 10. Thực nghiệm chính: bốn nhóm scopes và lookup

Chương trình hoàn chỉnh ở mục 14 sẽ quan sát ba contexts:

**Context A – ScopeConfig:**

- Eager singleton được tạo trong refresh.
- Lazy singleton chỉ tạo ở first getBean.
- Prototype được inject một lần vào eager singleton `FixedHolder`.
- Hai lần `getBean(Task.class)` tạo hai prototypes tiếp theo.
- Hai lần `ObjectProvider<Task>.getObject()` tạo hai prototypes tiếp nữa.
- Provider của singleton vẫn trả cùng singleton.
- Provider của bean không tồn tại thể hiện getIfAvailable/getIfUnique/getObject.

**Context B – ForcedLazyConfig:**

- `@Lazy ForcedCatalog` được eager singleton `RequiredFacade` yêu cầu trực tiếp.
- Khi context refresh xong, ForcedCatalog **đã tạo**, dù definition có `@Lazy`.

**Context C – DeferredConfig:**

- Eager `DeferredClient` nhận `@Lazy Greeting` injection proxy.
- Greeting target chưa khởi tạo sau refresh.
- First invocation qua proxy tạo Greeting target, lần sau dùng lại singleton.

### Vì sao tách contexts?

Nếu ép tất cả thử nghiệm vào một context, eager dependencies từ thử nghiệm này có thể kích hoạt lazy beans của thử nghiệm khác, làm counters khó giải thích và stdout trở nên phụ thuộc cấu hình không liên quan. Isolated contexts giúp chứng minh **một cơ chế mỗi lần**.

### Vì sao dùng AtomicInteger?

Counters nhận id dễ quan sát, không phụ thuộc JVM identityHashCode/địa chỉ memory. `AtomicInteger` còn dễ hoạt động an toàn hơn khi thêm concurrency tests, mặc dù các thí nghiệm dưới đây gọi tuần tự.

## 11. Chuẩn bị Java/Maven

Yêu cầu:

- JDK 17 hoặc mới hơn, với source-level Java 17.
- Apache Maven cài đặt và kết nối repository để tải Spring dependencies lần đầu.
- Spring Framework 6.2.19 `spring-context`.
- Không cần Spring Boot, web container, Jakarta Servlet hoặc GraalVM.

Cấu trúc:

~~~text
scope-provider-lab/
├── pom.xml
└── src/main/java/demo/
    └── ScopeProviderLab.java
~~~

### pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>scope-provider-lab</artifactId>
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

## 12. Source code chạy độc lập

File **`src/main/java/demo/ScopeProviderLab.java`**:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

public class ScopeProviderLab {

    private static final AtomicInteger eagerCreated =
            new AtomicInteger();
    private static final AtomicInteger lazyCreated =
            new AtomicInteger();
    private static final AtomicInteger taskIds =
            new AtomicInteger();
    private static final AtomicInteger forcedCreated =
            new AtomicInteger();
    private static final AtomicInteger deferredCreated =
            new AtomicInteger();

    public static final class EagerResource {
        public EagerResource() {
            eagerCreated.incrementAndGet();
        }
    }

    public static final class LazyResource {
        public LazyResource() {
            lazyCreated.incrementAndGet();
        }
    }

    public record Task(int id) {}

    public record FixedHolder(Task task) {}

    public record DynamicHolder(
            ObjectProvider<Task> tasks) {

        public Task nextTask() {
            return tasks.getObject();
        }
    }

    public static final class MissingResource {}

    @Configuration(proxyBeanMethods = false)
    public static class ScopeConfig {

        @Bean
        public EagerResource eagerResource() {
            return new EagerResource();
        }

        @Bean
        @Lazy
        public LazyResource lazyResource() {
            return new LazyResource();
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public Task prototypeTask() {
            return new Task(taskIds.incrementAndGet());
        }

        @Bean
        public FixedHolder fixedHolder(Task task) {
            return new FixedHolder(task);
        }

        @Bean
        public DynamicHolder dynamicHolder(
                ObjectProvider<Task> tasks) {
            return new DynamicHolder(tasks);
        }
    }

    public static final class ForcedCatalog {
        public ForcedCatalog() {
            forcedCreated.incrementAndGet();
        }
    }

    public record RequiredFacade(ForcedCatalog catalog) {}

    @Configuration(proxyBeanMethods = false)
    public static class ForcedLazyConfig {

        @Bean
        @Lazy
        public ForcedCatalog forcedCatalog() {
            return new ForcedCatalog();
        }

        @Bean
        public RequiredFacade requiredFacade(
                ForcedCatalog catalog) {
            return new RequiredFacade(catalog);
        }
    }

    public interface Greeting {
        String greet();
    }

    public static final class GreetingImpl
            implements Greeting {

        public GreetingImpl() {
            deferredCreated.incrementAndGet();
        }

        @Override
        public String greet() {
            return "xin-chao";
        }
    }

    public record DeferredClient(Greeting greeting) {

        public String call() {
            return greeting.greet();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DeferredConfig {

        @Bean
        @Lazy
        public Greeting greeting() {
            return new GreetingImpl();
        }

        // Annotation này nằm ở INJECTION POINT.
        @Bean
        public DeferredClient deferredClient(
                @Lazy Greeting greeting) {
            return new DeferredClient(greeting);
        }
    }

    public static void main(String[] args) {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ScopeConfig.class)) {

            System.out.println("A.afterRefresh.eager="
                    + eagerCreated.get());

            System.out.println("A.afterRefresh.lazy="
                    + lazyCreated.get());

            System.out.println("A.afterRefresh.prototype="
                    + taskIds.get());

            EagerResource singletonA =
                    context.getBean(EagerResource.class);

            EagerResource singletonB =
                    context.getBean(EagerResource.class);

            System.out.println("A.singleton.same="
                    + (singletonA == singletonB));

            System.out.println("A.lazy.beforeFirst="
                    + lazyCreated.get());

            LazyResource lazyA =
                    context.getBean(LazyResource.class);

            LazyResource lazyB =
                    context.getBean(LazyResource.class);

            System.out.println("A.lazy.same="
                    + (lazyA == lazyB));

            System.out.println("A.lazy.afterFirst="
                    + lazyCreated.get());

            FixedHolder fixed =
                    context.getBean(FixedHolder.class);

            System.out.println("A.fixed.task.id="
                    + fixed.task().id());

            Task directA = context.getBean(Task.class);
            Task directB = context.getBean(Task.class);

            System.out.println("A.direct.ids="
                    + directA.id() + "," + directB.id());

            System.out.println("A.direct.same="
                    + (directA == directB));

            DynamicHolder dynamic =
                    context.getBean(DynamicHolder.class);

            Task viaProviderA = dynamic.nextTask();
            Task viaProviderB = dynamic.nextTask();

            System.out.println("A.provider.ids="
                    + viaProviderA.id() + "," + viaProviderB.id());

            System.out.println("A.provider.same="
                    + (viaProviderA == viaProviderB));

            System.out.println("A.fixed.still.id="
                    + fixed.task().id());

            ObjectProvider<EagerResource> singletonProvider =
                    context.getBeanProvider(EagerResource.class);

            System.out.println("A.provider.singleton.same="
                    + (singletonProvider.getObject() == singletonA));

            ObjectProvider<MissingResource> missingProvider =
                    context.getBeanProvider(MissingResource.class);

            System.out.println("A.missing.ifAvailable.null="
                    + (missingProvider.getIfAvailable() == null));

            System.out.println("A.missing.ifUnique.null="
                    + (missingProvider.getIfUnique() == null));

            boolean throwsMissing = false;
            try {
                missingProvider.getObject();
            } catch (NoSuchBeanDefinitionException expected) {
                throwsMissing = true;
            }

            System.out.println("A.missing.getObject.throws="
                    + throwsMissing);

            System.out.println("A.prototype.total="
                    + taskIds.get());
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ForcedLazyConfig.class)) {

            System.out.println("B.forced.atRefresh="
                    + forcedCreated.get());

            RequiredFacade facade =
                    context.getBean(RequiredFacade.class);

            System.out.println("B.forced.same="
                    + (facade.catalog()
                            == context.getBean(ForcedCatalog.class)));
        }

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             DeferredConfig.class)) {

            System.out.println("C.deferred.atRefresh="
                    + deferredCreated.get());

            DeferredClient client =
                    context.getBean(DeferredClient.class);

            System.out.println("C.deferred.first="
                    + client.call());

            System.out.println("C.deferred.afterFirst="
                    + deferredCreated.get());

            System.out.println("C.deferred.second="
                    + client.call());

            System.out.println("C.deferred.afterSecond="
                    + deferredCreated.get());
        }
    }
}
~~~

## 13. Chạy và kết quả có thể đối chiếu

Đứng tại thư mục `scope-provider-lab`:

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.ScopeProviderLab
~~~

Đây là **stdout do chương trình tự in**, không bao gồm Maven messages hoặc logging nếu có:

~~~text
A.afterRefresh.eager=1
A.afterRefresh.lazy=0
A.afterRefresh.prototype=1
A.singleton.same=true
A.lazy.beforeFirst=0
A.lazy.same=true
A.lazy.afterFirst=1
A.fixed.task.id=1
A.direct.ids=2,3
A.direct.same=false
A.provider.ids=4,5
A.provider.same=false
A.fixed.still.id=1
A.provider.singleton.same=true
A.missing.ifAvailable.null=true
A.missing.ifUnique.null=true
A.missing.getObject.throws=true
A.prototype.total=5
B.forced.atRefresh=1
B.forced.same=true
C.deferred.atRefresh=0
C.deferred.first=xin-chao
C.deferred.afterFirst=1
C.deferred.second=xin-chao
C.deferred.afterSecond=1
~~~

### Tại sao A.afterRefresh.prototype bằng 1?

`ScopeConfig` có eager singleton `FixedHolder`. Để xây nó, container phải resolve `Task` ngay lúc refresh. Vì Task là prototype, Spring tạo `Task(id=1)` rồi inject vào FixedHolder. Context không tự pre-instantiate prototype để “test prototype”; **chính dependency của eager singleton** tạo Task đầu tiên.

### Tại sao direct ids là 2,3?

Sau refresh đã tạo Task #1. Hai câu gọi `context.getBean(Task.class)` khác nhau yêu cầu prototype mới, lần lượt nhận Task #2 và #3.

### Tại sao provider ids là 4,5?

DynamicHolder giữ `ObjectProvider<Task>`, không giữ một Task. Mỗi lần `dynamic.nextTask()` gọi provider `getObject()`, BeanFactory tạo một prototype mới. Kết quả Task #4 và #5.

### Tại sao fixed vẫn là 1?

Dù BeanFactory đã tạo #2,#3,#4,#5, `FixedHolder.task()` vẫn là reference constructor-injected tới #1. Spring không thay reference trong một singleton theo mỗi prototype lookup ở nơi khác.

### Tại sao provider.singleton.same=true?

Provider chỉ quyết định **thời điểm resolve**, không override scope. Lookup EagerResource trả cùng singleton đã tạo. Cùng một provider được gọi nhiều lần không biến singleton thành prototype.

### Tại sao B.forced.atRefresh=1?

`ForcedCatalog` mang `@Lazy`, nhưng eager `RequiredFacade` yêu cầu nó bằng constructor parameter. BeanFactory phải tạo catalog để tạo facade ngay trong refresh, nên constructor chạy trước dòng output.

### Tại sao C.deferred.atRefresh=0 rồi thành 1?

`DeferredClient` là eager singleton, nhưng parameter `@Lazy Greeting` là lazy-resolution proxy. Spring có thể tạo consumer trước khi target; lần đầu `client.call()` khiến proxy resolve target singleton. Các lần tiếp theo vẫn delegate đến target singleton ấy.

### Những điều demo cố ý không làm

- Không phụ thuộc `System.identityHashCode` vì giá trị không xác định giữa runs.
- Không gọi prototype task constructors ngoài BeanFactory.
- Không dùng `@Transactional` hoặc `@Async` khi chưa cấu hình infrastructure.
- Không giả định background threads hoặc requests chạy theo timing ngẫu nhiên.
- Không in destruction order vì có thể tùy lifecycle/dependency graph.
- Không dùng JUnit để người học có thể kiểm tra ngay bằng Maven exec plugin.

## 14. Giải thích nâng cao: @Bean, @Scope, @Lazy và parameter resolution

### @Bean method không phải một Java factory tùy ý

Với:

~~~java
@Bean
@Scope("prototype")
Task prototypeTask() {
    return new Task(nextId());
}
~~~

đây là snippet bên trong configuration class. Spring đăng ký method làm factory method của BeanDefinition. Mỗi BeanFactory request cho prototype gọi factory method để lấy object theo scope policy.

Nếu bạn **tự gọi trực tiếp** method Java trên instance configuration hoặc tạo `new Config()`, hành vi có thể khác Spring-managed BeanFactory lookup, đặc biệt với `@Configuration(proxyBeanMethods=false)` trong demo. Vì vậy mọi thí nghiệm đều gọi `context.getBean(...)` hoặc `ObjectProvider.getObject()` để đo đúng container.

### proxyBeanMethods=false không tắt BeanPostProcessor

`@Configuration(proxyBeanMethods=false)` tránh CGLIB inter-bean method interception, thích hợp khi `@Bean` methods là independent factories và dependencies được thể hiện bằng method parameters. Nó **không** tắt @Autowired, scope management hoặc ObjectProvider. Nếu một `@Bean` method trực tiếp gọi `this.otherBean()` trong lite mode, đó là Java call bình thường, có thể bypass singleton/prototype semantics mong muốn. Demo không làm vậy.

### @Scope annotation ở bean factory method

Spring dùng scope metadata của BeanDefinition, không dựa vào việc implementation class của Task có `@Scope` hay không. Nếu annotation đặt ở component class thay vì @Bean method, registration path khác nhưng khái niệm scope giữ nguyên.

### @Lazy và provider cùng có thể hoãn resolution

- `@Lazy` trên bean definition: delay singleton pre-instantiation, trừ khi required dependency.
- `@Lazy` trên injection point: consumer giữ proxy, target được resolve khi dùng.
- `ObjectProvider`: consumer giữ lookup handle và chọn chính xác khi gọi `getObject()`.
- Có thể dùng đồng thời nhưng không đồng nghĩa cần dùng cả ba: một hợp đồng rõ ràng là quan trọng nhất.

## 15. Thực hành biến thể 1: singleton trong hai contexts

Mục tiêu: chứng minh scope không phải static global singleton.

~~~java
try (AnnotationConfigApplicationContext first =
             new AnnotationConfigApplicationContext(ScopeConfig.class);
     AnnotationConfigApplicationContext second =
             new AnnotationConfigApplicationContext(ScopeConfig.class)) {

    EagerResource a = first.getBean(EagerResource.class);
    EagerResource b = second.getBean(EagerResource.class);

    System.out.println("sameAcrossContexts=" + (a == b));
}
~~~

Đây là snippet phải đặt nơi các nested types `ScopeConfig`/`EagerResource` khả kiến, hoặc thêm prefix `ScopeProviderLab`. Kết quả mong đợi `sameAcrossContexts=false` trong fixture đang trả `new EagerResource()` cho mỗi context.

**Lưu ý:** Counters static trong demo nếu dùng lại JVM có thể tiếp tục tăng, nhưng identity across contexts vẫn chứng minh đúng scope. Nếu một factory method cố ý trả một static shared object, đó là application behavior ngoài quy tắc scope bình thường; scope singleton không cấm bean factory trả shared global reference do chính bạn thiết kế.

## 16. Thực hành biến thể 2: ObjectProvider trên prototype và vòng lặp

Một singleton cần 100 jobs riêng:

~~~java
public final class BatchRunner {

    private final ObjectProvider<Task> jobs;

    public BatchRunner(ObjectProvider<Task> jobs) {
        this.jobs = jobs;
    }

    public void runBatch(int count) {
        for (int i = 0; i < count; i++) {
            Task task = jobs.getObject();
            consume(task);
        }
    }

    private void consume(Task task) {
        System.out.println(task.id());
    }
}
~~~

Đây là class snippet cần imports và type Task từ demo. Provider gọi mỗi lượt sẽ yêu cầu tạo 100 prototypes; nếu Task có constructor nặng hoặc cần closing resources, performance và cleanup policy quan trọng.

### Provider có tốt hơn một factory tự viết không?

Nếu Task là application domain object cần dynamic arguments khác nhau, một domain factory có phương thức `create(Parameters)` thường rõ hơn. `ObjectProvider.getObject(args)` có overload nhận explicit args, nhưng constructor/factory method resolution và AOT/native behavior có thể có constraints. Đừng dùng varargs method như một API business chung mà không test parameter binding.

### Provider làm test dễ hay khó?

Đối với unit tests, mock/fake `ObjectProvider<Task>` hoặc dùng implementation đơn giản trả fake instances. Trong Spring 6.2, interface ObjectProvider đã có default method implementations hỗ trợ custom test implementations. Nhưng hãy chỉ implement những methods mà client thật sự gọi hoặc `stream()` theo contract; đừng trả ngẫu nhiên instances không khớp scope mà test đang mô phỏng.

## 17. Thực hành biến thể 3: getIfAvailable/getIfUnique và ambiguity

Giả sử có hai implementations của `Transport`:

~~~java
@Bean("rail")
public Transport rail() {
    return new RailTransport();
}

@Bean("road")
public Transport road() {
    return new RoadTransport();
}
~~~

Đây là configuration snippets, cần types Transport/RailTransport/RoadTransport. Nếu không có @Primary/@Fallback/qualifier chọn được winner, `ObjectProvider<Transport>` có thể:

~~~java
Transport available = provider.getIfAvailable(); // ném NoUnique
Transport unique = provider.getIfUnique();       // null
Transport required = provider.getObject();       // ném NoUnique
~~~

Đây là snippets nên chạy trong context riêng, không nằm trong runnable demo chính.

### Phân biệt hai lỗi provider

- `NoSuchBeanDefinitionException`: không tìm được ứng viên.
- `NoUniqueBeanDefinitionException`: tìm thấy nhiều ứng viên mà không có winner. `NoUniqueBeanDefinitionException` kế thừa `NoSuchBeanDefinitionException`, nên trong diagnostics test NoUnique **trước**.

### Khi có @Primary

Nếu chỉ một candidate được đánh dấu `@Primary` phù hợp, provider single-value lookup có thể resolve nó thay vì ném NoUnique. `getIfUnique()` không phải phép kiểm tra số BeanDefinitions raw đơn thuần; nó dùng resolution policy.

### Nếu không muốn code biết null

Bạn có thể dùng provider `ifAvailable(Consumer)` hoặc fallback supplier methods, nhưng semantics business phải chấp nhận absence. Không che required dependency bằng default instance âm thầm nếu missing dependency là lỗi cấu hình.

## 18. Thực hành biến thể 4: @Lazy tại injection point vs bean definition

Hai đoạn:

~~~java
@Bean
@Lazy
public ReportService reportService() {
    return new ReportService();
}

@Bean
public ReportController reportController(ReportService service) {
    return new ReportController(service);
}
~~~

Nếu ReportController eager, nó có thể buộc ReportService được tạo trong refresh. Bây giờ thay:

~~~java
@Bean
public ReportController reportController(
        @Lazy ReportService service) {
    return new ReportController(service);
}
~~~

Spring có thể inject proxy, giúp ReportController init trước target. Tuy nhiên ReportService class phải proxy được; với `final` class, class-based proxy có thể gặp giới hạn. Dùng interface contract khi phù hợp.

### Lựa chọn nào nên dùng?

- Dependency thực sự *required ngay lập tức*: inject trực tiếp, fail fast.
- Dependency nặng nhưng chỉ được dùng trong một path: bean `@Lazy` + injection-point proxy có thể hợp lý.
- Cần nhiều instances mới theo scope: `ObjectProvider<Prototype>` rõ hơn.
- Không có bean cũng là trạng thái hợp lệ: `ObjectProvider.getIfAvailable`/`Optional<T>`.
- Per-request state trong web application: request scope/scoped proxy thích hợp; không thay bằng application-wide singleton mutable state.

### Cẩn thận deferred errors

Một lỗi cấu hình có thể bị hoãn từ startup sang request đầu tiên khi dùng lazy proxy/provider. Với critical infrastructure, cần readiness checks hoặc warmup tests. Lazy không phải chiến lược chữa lỗi dependency graph.

## 19. Thực hành biến thể 5: destruction và lifecycle

### Singleton destruction

Một Spring-managed singleton đã khởi tạo có thể nhận `@PreDestroy`, `DisposableBean.destroy()` hoặc custom destroy method khi context close, tùy metadata. Vì vậy dùng `try-with-resources` với `AnnotationConfigApplicationContext` là thói quen tốt.

### Prototype destruction

Spring không theo dõi từng prototype instance để destroy tự động sau khi handed off. Một worker prototype mở file/network connection cần client code hoặc lifecycle abstraction rõ ràng giải phóng resource.

~~~java
try (SessionWorker worker = jobs.getObject()) {
    worker.runOnce();
}
~~~

Đây là snippet: `SessionWorker implements AutoCloseable`; bean được quản lý prototype; việc `close()` ở try-with-resources do **application code**, không phải container tự gọi trong mỗi lookup.

### Đừng gọi close() vào pooled/shared singleton tùy tiện

Nếu provider target là singleton hoặc scoped proxy, dùng try-with-resources như trên có thể làm đóng một object vẫn đang được các callers khác dùng. Lựa chọn cleanup phải biết **scope thực tế**.

### Custom scopes

Request/session/application scope cần web-aware context. Bare `AnnotationConfigApplicationContext` không tự có active HTTP request; `@Scope("request")` cần đúng scope infrastructure. Scoped proxy có thể giữ reference ổn định ở singleton consumer trong khi target thay đổi theo request. Đây là cơ chế khác với prototype provider, dù cùng liên quan scoped instance resolution.

## 20. Thread safety và concurrency trong các experiments

### Singleton

Mọi threads có thể dùng cùng singleton, do đó mutable state trong singleton phải được bảo vệ:

~~~java
public final class CounterService {
    private final AtomicInteger value = new AtomicInteger();

    public int increment() {
        return value.incrementAndGet();
    }
}
~~~

Class này giữ concurrency safety cho counter increment nhưng không tự xử lý thread-safety của những object khác.

### Prototype

Hai calls có thể tạo hai instances, nhưng các instances vẫn có thể dùng **shared collaborators** (singleton database pools, caches, shared static state). Prototype không bảo đảm mỗi instance hoàn toàn độc lập về dữ liệu ngoài phạm vi object.

### Provider under concurrency

`ObjectProvider.getObject()` có thể được nhiều threads gọi; BeanFactory quản lý scope resolution, nhưng constructor/state của prototype phải an toàn nếu có static/global shared mutation. `AtomicInteger` trong demo dùng để đánh số, không phải bảo đảm toàn bộ business prototype đều thread-safe.

### Không dùng ThreadLocal làm substitute cho scope đúng

Nếu muốn per-request data trong web application, Spring request scope hoặc explicit request context injection thường rõ hơn singleton chứa mutable ThreadLocal không được cleanup. Nếu dùng ThreadLocal, cần kiểm soát request lifecycle và tránh leaks với thread pools.

## 21. Lỗi thường gặp và hướng điều tra

| Triệu chứng | Nguyên nhân có khả năng | Hướng giải quyết |
| --- | --- | --- |
| getBean cùng singleton trả cùng object | Đúng scope singleton | Không phải lỗi |
| Hai named singletons cùng class nhưng khác identity | Hai definitions/contexts | Xác định bean names |
| Prototype constructor chạy trong refresh | Eager singleton đang inject prototype | Kiểm tra dependency graph |
| Prototype không đổi giữa các method calls trên singleton | Chỉ inject prototype một lần | Inject ObjectProvider/factory |
| ObjectProvider.getObject() vẫn trả cùng object | Target là singleton | Dùng prototype nếu cần instances mới |
| Lazy constructor chạy trong refresh | Eager consumer cần nó | Dùng injection-point @Lazy hoặc ObjectProvider nếu phù hợp |
| Lazy singleton tạo một lần rồi tái sử dụng | Đúng behavior | Không nhầm với prototype |
| @Lazy parameter không hoãn target | Gọi method trên proxy trong init hoặc proxy config sai | Kiểm tra first invocation |
| @Lazy proxy không tạo được | Final class/method, type mismatch | Sử dụng interface hoặc đổi design |
| getIfAvailable vẫn throw | Nhiều candidates hoặc bean creation fail | Đọc NoUnique/root cause |
| getIfUnique null dù có nhiều beans | Không có unique winner | Dùng qualifier/primary/conditions |
| getObject() với missing type ném exception | Required dependency không có | Bổ sung bean hoặc optional policy |
| Provider fallback object không được @Autowired | Supplier tự tạo Java object | Đăng ký bean hoặc tự wiring rõ ràng |
| Prototype @PreDestroy không chạy khi close context | Spring không tự destroy prototypes | Caller disposal/owning scope |
| Singleton @PreDestroy chưa chạy | Context chưa close hoặc bean chưa instantiate | Kiểm tra lifecycle |
| Test assert identityHashCode bị flaky | Địa chỉ/identity hash không cố định | So reference/id counters |
| @Bean method trực tiếp gọi method khác sai scope | proxyBeanMethods=false, Java self-call | Qua BeanFactory/method arguments |
| Tests thay đổi counters không lường trước | Contexts hoặc @Bean registration khác | Tách context, reset counter |
| Prototype không có state độc lập như tưởng | Có shared singleton dependencies/static data | Phân tích object graph |
| App startup chậm dù @Lazy | Eager dependency ép init | Dò bean creation graph |
| Chuyển mọi bean sang lazy che lỗi startup | Deferred validation failure | Test first access/readiness |

## 22. Bộ kiểm thử hồi quy đề xuất

### 22.1. Singleton identity test

Dựng một context:

~~~java
EagerResource a = context.getBean(EagerResource.class);
EagerResource b = context.getBean(EagerResource.class);

if (a != b) {
    throw new AssertionError("Expected same singleton");
}
~~~

Đây là snippet với fixture ScopeConfig và context hợp lệ. Đừng assert instance equality across **two separate contexts**.

### 22.2. Prototype retrieval test

~~~java
Task a = context.getBean(Task.class);
Task b = context.getBean(Task.class);

if (a == b) {
    throw new AssertionError("Expected distinct prototypes");
}
~~~

Đây là snippet; nếu BeanDefinition đổi sang singleton, test sẽ fail — đúng mục tiêu.

### 22.3. Singleton holds initial prototype test

~~~java
FixedHolder owner = context.getBean(FixedHolder.class);
Task held = owner.task();

if (held != owner.task()) {
    throw new AssertionError("Constructor-injected field changed");
}
~~~

Kiểm tra identity reference giữ ổn định, chứ không cần biết id chính xác nếu test chạy cùng fixtures khác.

### 22.4. Provider fresh instance test

~~~java
DynamicHolder owner = context.getBean(DynamicHolder.class);
Task a = owner.nextTask();
Task b = owner.nextTask();

if (a == b) {
    throw new AssertionError("Expected provider prototypes");
}
~~~

Đừng test mock provider trả cùng fake Task rồi nghĩ đó là lỗi Spring; unit test stub phải mô phỏng expected scope behavior.

### 22.5. Lazy initialization test

Chỉ assert lazy counter sau context refresh nếu không có eager dependency nào ép bean ấy tạo. Đừng dựa vào một context test lớn vô tình instantiate bean trong auto-configuration/other tests.

### 22.6. Forced eager dependency test

Context có eager consumer và `@Lazy` dependency: assert target đã tạo sau refresh. Đây là test xác nhận **giới hạn của @Lazy definition**, không phải bug.

### 22.7. Lazy injection point test

Context có `@Lazy` method parameter, target interface và method invocation: assert target chưa created trước first invocation và được created đúng một lần sau first call với singleton target.

### 22.8. Missing / ambiguous provider test

Isolated fixture cho no bean: assert `getIfAvailable()==null`, `getIfUnique()==null` và `getObject()` throws. Fixture hai regular candidates: assert `getIfUnique()==null` và getObject throws NoUnique; thêm @Primary rồi assert provider resolves primary.

### 22.9. Lifecycle cleanup test

Singleton cleanup: assert registered destroy callback chạy sau context.close. Prototype cleanup: assert caller resource owner đóng prototype; đừng kỳ vọng container close tự enumerate instances tạo trước.

### 22.10. Integration test vs unit test

Unit test constructor nhận một Task/fake provider có thể kiểm tra thuật toán ReportManager, nhưng không chứng minh @Scope, @Lazy, @Bean dependencies hoặc proxy resolution. Khi yêu cầu là container semantics, dùng `AnnotationConfigApplicationContext`/Spring TestContext thật.

## 23. Mẫu thiết kế gợi ý

**Singleton stateless service**: một bean giữ dependencies ổn định, không chứa mutable per-request fields.

**Prototype unit-of-work**: stateful short-lived object, được tạo qua provider/factory và được caller sở hữu cleanup khi cần.

**Lazy singleton heavy resource**: khởi tạo chỉ khi feature được dùng, nhưng phải có startup/readiness test để phát hiện cấu hình hỏng trước production traffic nếu rủi ro cao.

**Optional integration**: dùng provider.getIfAvailable hoặc condition-based registration khi vắng service là trạng thái hợp lệ.

**Web request state**: dùng request scope/scoped proxy thay vì chia một singleton mutable state cho tất cả requests.

**Dynamic runtime factory**: một application-level factory interface có thể bọc ObjectProvider để domain không phụ thuộc Spring, đồng thời cho phép đổi implementation/test dễ hơn.

Không có một pattern duy nhất tốt cho mọi ứng dụng. Chọn dựa trên **ai sở hữu object, khi nào cần object, có cần instance mới và ai chịu trách nhiệm hủy**.

## 24. Checklist thực nghiệm

- [ ] Có ít nhất một eager singleton và kiểm tra sau refresh.
- [ ] Xác minh hai getBean của cùng singleton trả cùng reference.
- [ ] Prototype BeanDefinition có @Scope(PROTOTYPE) thật.
- [ ] Xác minh hai getBean prototype trả hai instances.
- [ ] Kiểm tra prototype được inject vào singleton một lần.
- [ ] Kiểm tra method nhiều lần trên singleton không tự tạo prototype mới.
- [ ] Dùng ObjectProvider.getObject() để yêu cầu prototype mới mỗi lần.
- [ ] Kiểm tra ObjectProvider của singleton vẫn trả same singleton.
- [ ] Kiểm tra missing provider getIfAvailable/getIfUnique/getObject.
- [ ] Hiểu NoUniqueBeanDefinitionException khi có nhiều candidates.
- [ ] Kiểm tra @Lazy trên bean definition qua counter trước first lookup.
- [ ] Kiểm tra eager singleton dependency có thể ép @Lazy target tạo khi refresh.
- [ ] Kiểm tra @Lazy injection point tạo lazy proxy và trì hoãn target.
- [ ] Phân biệt BeanDefinition existence và actual instance creation.
- [ ] Dùng identity comparisons, không hardcode identityHashCode.
- [ ] Biết Spring singleton scope là per container/bean definition.
- [ ] Cân nhắc thread safety cho mutable singleton fields.
- [ ] Biết Spring không tự gọi prototype destroy callbacks sau lookup.
- [ ] Cân nhắc cleanup khi prototype sở hữu resources.
- [ ] Không dùng @Lazy để giấu configuration errors.
- [ ] Không tự gọi @Bean method trong lite configuration rồi suy luận scope.
- [ ] Test Spring context thực, không chỉ new Java objects.
- [ ] Đọc root BeanCreationException khi deferred lookup fail.
- [ ] Xác định đúng consumer ownership và lifetime trong design.

## 25. Câu hỏi tự kiểm tra

**1. Singleton có bảo đảm một instance duy nhất cho cả JVM không?**  
Không. Spring singleton là một instance chia sẻ theo bean definition/container. Hai contexts độc lập thường có hai instances.

**2. Prototype có luôn được tạo khi context refresh không?**  
Không. Nó được tạo khi BeanFactory cần, có thể trong refresh nếu eager singleton inject prototype.

**3. Inject prototype vào singleton có cấp object mới mỗi lần singleton gọi method không?**  
Không. Constructor/setter injection chỉ resolve ở thời điểm injection, nên owner giữ cùng reference trừ khi tự thay.

**4. Làm thế nào singleton tạo prototype mới mỗi lần cần?**  
Inject ObjectProvider<Prototype> hoặc domain factory và gọi getObject/create ở từng operation.

**5. ObjectProvider<Singleton>.getObject() có tạo instance mới không?**  
Không; nó resolve cùng singleton theo scope.

**6. @Lazy trên bean definition có đổi singleton thành prototype không?**  
Không. Nó ảnh hưởng thời điểm khởi tạo, không đổi scope.

**7. Tại sao @Lazy singleton vẫn tạo trong refresh?**  
Một eager singleton khác cần dependency đó để hoàn tất chính nó.

**8. @Lazy đặt trên injection point có gì đặc biệt?**  
Spring có thể inject lazy-resolution proxy, hoãn target resolution đến lần invocation.

**9. getIfAvailable() có trả null khi có hai beans mơ hồ không?**  
Không mặc định; nó có thể throw NoUniqueBeanDefinitionException.

**10. getIfUnique() với hai candidates không có winner trả gì?**  
Trả null, theo provider semantics.

**11. getObject() khi không có bean?**  
Ném NoSuchBeanDefinitionException.

**12. Provider có tự quản lý cleanup prototype không?**  
Không. Spring chỉ quản lý creation/init; caller cần chính sách disposal.

**13. Một prototype có tự an toàn concurrency không?**  
Không. Instance có thể chứa shared collaborators/static state và code vẫn cần synchronization khi được chia sẻ.

**14. Tại sao không dùng @Lazy cho mọi bean?**  
Có thể hoãn lỗi cấu hình tới runtime, làm readiness khó kiểm tra và startup behavior kém dự đoán.

**15. @Configuration(proxyBeanMethods=false) ảnh hưởng gì?**  
Nó bỏ inter-bean method interception; BeanFactory scope/injection vẫn hoạt động, nhưng tự gọi factory method như Java call có thể bypass container semantics.

**16. Bean definition có tồn tại là constructor đã chạy chưa?**  
Không. Lazy/prototype definitions có thể chưa instantiate.

**17. Khi nào ưu tiên provider thay vì lazy proxy?**  
Khi consumer cần điều khiển tường minh thời điểm lookup, nhận nhiều prototype instances hoặc xử lý absence/uniqueness.

**18. Tại sao phải tách ba contexts trong demo?**  
Tránh một eager dependency ở experiment này vô tình khởi tạo lazy bean của experiment khác.

**19. Đâu là kết quả quan trọng nhất của demo?**  
FixedHolder giữ Task #1, trong khi direct getBean và provider tạo #2,#3,#4,#5; đồng thời @Lazy definition và @Lazy injection point có hiệu ứng khác nhau.

**20. Công thức lựa chọn ngắn gọn?**  
**Scope = vòng đời và chia sẻ instance. Lazy = thời điểm khởi tạo. Provider = cách và thời điểm lookup.**

## 26. Tài liệu chính thức

- [Spring Framework 6.2 Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton/prototype, prototype injected into singleton, prototype destruction.
- [Spring Framework 6.2 Reference — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html): eager singleton dependency có thể ép lazy target tạo trong refresh.
- [Spring Framework 6.2 Reference — Method Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-method-injection.html): lookup-method injection là phương án khác khi cần prototype inside singleton.
- [Spring Framework 6.2 Reference — Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html).
- [Spring Framework 6.2 Reference — Java-based Bean Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html).
- [Spring Framework 6.2 Javadoc — ObjectProvider](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/ObjectProvider.html): getObject/getIfAvailable/getIfUnique và streams.
- [Spring Framework 6.2 Javadoc — Lazy](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Lazy.html): definition và injection-point semantics.
- [Spring Framework 6.2 Javadoc — Scope](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/Scope.html).
- [Spring Framework 6.2 Javadoc — ConfigurableBeanFactory](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/config/ConfigurableBeanFactory.html).
- [Spring Framework 6.2 Javadoc — AnnotationConfigApplicationContext](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html).
- [Spring Framework 6.2 Javadoc — NoSuchBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/NoSuchBeanDefinitionException.html).
- [Spring Framework 6.2 Javadoc — NoUniqueBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-005">Bổ sung cấu hình môi trường, tài nguyên và thông điệp</a>

<details>
<summary>Xem chi tiết</summary>

# Bổ sung cấu hình môi trường, tài nguyên và thông điệp trong Spring Core Container

## 1. Mục tiêu và tình huống tổng hợp

Một ứng dụng Spring không chỉ có `@Bean` và dependency injection. Container còn cung cấp các cơ chế quản lý **môi trường (`Environment`)**, **tài nguyên (`Resource` và `ResourceLoader`)**, và **thông điệp theo ngôn ngữ (`MessageSource`)**. Kết hợp ba cơ chế cho phép cùng một nghiệp vụ dùng cấu hình phù hợp môi trường, đọc mẫu nội dung từ classpath/JAR và trả thông điệp đúng `Locale` mà không hardcode mọi thứ trong Java class.

Tình huống của bài:

> Một dịch vụ xử lý hóa đơn cần biết tên ứng dụng và số lần thử lại từ file cấu hình, có thể được override khi khởi động thử nghiệm; nó đọc mẫu biên nhận từ resources, ghép mã đơn hàng vào template và hiển thị thông điệp trạng thái bằng tiếng Việt hoặc tiếng Anh.

Phần thực hành dùng **Java 17, Spring Framework 6.2.19, Gradle, JUnit Jupiter 5.11.4** và `AnnotationConfigApplicationContext`. Đây là ứng dụng **Spring Framework thuần**, không dùng Spring Boot để tự động tải `application.properties`. Mọi nguồn configuration và resource đều được khai báo/tải tường minh.

Mục tiêu hoàn thành:

1. Hiểu `Environment` chứa profiles và một tập `PropertySource` có thứ tự ưu tiên.
2. Dùng `@PropertySource` tải cấu hình từ classpath.
3. Dùng `MapPropertySource` để override một giá trị với độ ưu tiên được quy định rõ.
4. Đọc property bằng `getProperty`, `getRequiredProperty`, kiểu chuyển đổi `Integer.class` và `@Value`.
5. Hiểu `@Profile` được xử lý khi cấu hình context, không phải “đổi nóng” các BeanDefinitions sau `refresh`.
6. Đọc tài nguyên qua `ResourceLoader`/`Resource.getInputStream` an toàn trong JAR, không nhầm với file vật lý.
7. Dùng `PathMatchingResourcePatternResolver` tìm tài nguyên theo `classpath*:`/wildcard và hiểu giới hạn của cách quét này.
8. Đăng ký đúng bean tên **`messageSource`** dùng `ResourceBundleMessageSource`.
9. Dùng `Locale` và `MessageFormat` arguments để hiển thị các mã thông điệp tiếng Việt/tiếng Anh.
10. Phân biệt lookup bắt buộc và fallback mặc định cho property, resource, message.
11. Kiểm thử kết quả, thứ tự ưu tiên properties, profile, resource và i18n trong các contexts độc lập.
12. Tách rõ những convenience features thuộc Spring Boot ra khỏi Spring Framework Core Container.

## 2. Bức tranh kết nối

```text
                AnnotationConfigApplicationContext
                              |
                 register(AppConfig.class)
                              |
                              v
                           refresh()
                              |
          +-------------------+--------------------+
          |                   |                    |
          v                   v                    v
      Environment         ResourceLoader       MessageSource
          |                   |                    |
    PropertySources      Resource objects       messageSource
          |                   |                    |
    @PropertySource      classpath:...           messages_vi
    MapPropertySource    getInputStream()        messages_en
          |                   |                    |
          +-------------------+--------------------+
                              |
                              v
                      ReceiptService bean
                              |
                    receipt("A-100")
                    ready("Lan", vi)
                    ready("Lan", en)
```

Đây là các **dịch vụ container phối hợp** chứ không phải một API duy nhất. `Environment` giải quyết cấu hình, `ResourceLoader` truy cập nội dung vật lý/logic, còn `MessageSource` giải mã những message codes sang chuỗi đã định dạng theo locale.

## 3. Environment gồm profiles và properties

Spring `Environment` gói hai khía cạnh:

- **Profiles**: tập tên logic được kích hoạt trước khi phân tích `@Profile` trên class/method config.
- **Properties**: nguồn giá trị theo tên, ví dụ `app.name`, `app.retry-count`, JVM system properties, environment variables, configuration files, map values.

`ConfigurableEnvironment` cho phép thay đổi profiles và `MutablePropertySources` trước khi context khởi tạo.

Mảnh API:

```java
ConfigurableEnvironment env = context.getEnvironment();

env.setActiveProfiles("dev");

String name = env.getProperty("app.name");
Integer retries = env.getProperty(
        "app.retry-count", Integer.class);
```

Đây chỉ là mảnh minh họa: các properties ở ví dụ chỉ tồn tại sau khi được đăng ký bằng `@PropertySource` hoặc nguồn khác. Với `AnnotationConfigApplicationContext`, hãy đặt active profiles **trước `refresh()`** để `@Profile` được đánh giá đúng.

## 4. PropertySource là gì và nguồn nào thắng?

`Environment` tra property key theo thứ tự những `PropertySource` đã được đăng ký. Một source đứng **trước** source khác thường có ưu tiên cao hơn cho key trùng tên. Nguồn có độ ưu tiên cao cung cấp toàn bộ value, không cộng dồn hai values của một key.

Ví dụ:

```text
Highest precedence
  1. MapPropertySource("exerciseOverride")
       app.retry-count = 5

  2. JVM system properties / process environment
       (các giá trị được Spring StandardEnvironment cung cấp)

  3. @PropertySource("classpath:demo/application.properties")
       app.retry-count = 3

Lowest precedence
```

Sơ đồ minh họa **cách sắp xếp được thiết kế trong demo**, không phải một thứ tự tuyệt đối cho mọi context/platform. Trong code, việc gọi `getPropertySources().addFirst(...)` bảo đảm source tên `exerciseOverride` thắng các sources khác khi cùng key.

Spring Framework **không** tự động đọc file `application.properties` theo quy ước Spring Boot. Trong dự án thuần Spring của bài này, chúng ta khai báo tường minh:

```java
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:demo/application.properties")
class AppConfig {
}
```

Điều đó đưa property file vào `Environment` khi Spring xử lý configuration class.

## 5. Thứ tự xử lý @PropertySource

Với nhiều `@PropertySource`, thứ tự khai báo/registration có thể quyết định key nào ghi đè key nào. Theo Javadoc của `@PropertySource`, source được xử lý **sau** có thể ghi đè những key trùng từ source xử lý trước. Nhưng order phụ thuộc cách các configuration classes được register và parse.

Ví dụ:

```java
@Configuration
@PropertySource("classpath:config/base.properties")
class BaseConfig {
}

@Configuration
@PropertySource("classpath:config/feature.properties")
class FeatureConfig {
}
```

Đây là hai class snippets; đường dẫn chỉ có ý nghĩa khi các files tương ứng tồn tại.

Khi logic phụ thuộc tuyệt đối vào precedence, tốt nhất:
- Dùng `MutablePropertySources.addFirst/addLast/addBefore/addAfter` với thứ tự rõ.
- Không giả định thứ tự component scanning hoặc imports là policy cấu hình bền vững.
- Viết test khóa có cùng tên ở nhiều sources để xác nhận giá trị nào thắng.

Trong demo hoàn chỉnh, `MapPropertySource` được đưa lên **đầu danh sách** nên hành vi override có thể kiểm tra chính xác.

## 6. @Value và PropertySourcesPlaceholderConfigurer

Hai cách lấy giá trị:

```java
String applicationName =
        environment.getRequiredProperty("app.name");
```

```java
public ReceiptService(
        @Value("${app.name}") String name,
        @Value("${app.retry-count}") int retries) {
    // ...
}
```

Các mảnh minh họa dùng placeholder syntax Spring; khi lưu vào README, ký hiệu `$` được chuyển về `$`.

`@Value` có thể resolve placeholders qua Environment/BeanFactory embedded value resolver trong annotation-enabled application contexts. Để thể hiện rõ infrastructure và xử lý chặt chẽ placeholders, dự án đăng ký:

```java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

**Để @Bean method tạo BeanFactoryPostProcessor ở dạng `static`** là thực hành tốt: tránh phải tạo configuration class quá sớm chỉ để instantiate processor. Đừng hiểu rằng mọi annotation-enabled Spring application bắt buộc tự khai báo bean này mới có `@Value` resolution; chính xác là ví dụ dùng nó để xác định tường minh path placeholder handling.

## 7. Required property, default và conversion

`Environment` có các lựa chọn:

| API | Khi nào dùng |
| --- | --- |
| `getProperty("key")` | Giá trị string hoặc null nếu vắng |
| `getProperty("key", "default")` | String mặc định nếu key vắng |
| `getProperty("key", Integer.class)` | Đọc và chuyển thành integer bằng conversion service |
| `getRequiredProperty("key")` | Ném `IllegalStateException` khi required property thiếu |
| `containsProperty("key")` | Kiểm tra có key resolvable hay không |
| `resolvePlaceholders("...")` | Resolve placeholders không nhất thiết fail nếu key thiếu |
| `resolveRequiredPlaceholders("...")` | Fail khi required placeholder chưa resolve |

Trong constructor:

```java
@Value("${app.retry-count:3}") int retryCount
```

Đây là một ví dụ placeholder có default `3`. Tránh đặt defaults cho những biến **bắt buộc** như database credentials, vì app có thể chạy sai môi trường một cách âm thầm.

Conversion errors khác missing errors. Nếu `app.retry-count=abc` nhưng injection point cần `int`, lỗi là không chuyển đổi được giá trị, không thể “chữa” bằng cách thêm một bean `Integer` tùy ý.

## 8. Profiles là điều kiện cho BeanDefinitions

`@Profile("dev")` giúp đưa một bean vào context chỉ khi profile `dev` active:

```java
@Bean
@Profile("dev")
DevMarker devMarker() {
    return new DevMarker("development");
}
```

Đây là mảnh method minh họa.

Điểm quan trọng:
- Set profile trước `register/config parsing/refresh` như demo.
- Profile đánh giá **registration** của bean, không thay đổi Java object có sẵn.
- Nếu đổi `Environment.setActiveProfiles("prod")` **sau refresh**, Spring không tự xóa bean dev hay thêm bean prod vào registry.
- Spring Boot có file conventions `application-dev.properties`, nhưng cơ chế đó **không có sẵn trong plain spring-context** nếu bạn không đưa source vào Environment.
- Một profile có thể kích hoạt group of bean definitions khác, chứ không chỉ đổi một string property.

## 9. Resource: một abstraction thay cho File

`Resource` là giao diện Spring để đại diện một nguồn nội dung, bất kể nó nằm trên disk, classpath, JAR, URL hay bộ nhớ.

Các methods hữu ích:

```java
boolean exists();
boolean isReadable();
InputStream getInputStream() throws IOException;
String getFilename();
String getDescription();
URL getURL() throws IOException;
File getFile() throws IOException;
```

Đây là đoạn chữ ký API được rút gọn, không phải class hoàn chỉnh.

**Quan trọng nhất:** không phải mọi Resource đều là `java.io.File`. Một `ClassPathResource` nằm bên trong JAR có thể đọc qua `getInputStream()` nhưng `getFile()` không cung cấp một đường dẫn file vật lý hợp lệ.

Một sai lầm nổi bật: code chạy trong IDE khi resources nằm trong thư mục `build/resources/main`, nhưng fail sau khi đóng gói JAR vì đã gọi `resource.getFile()`. Ưu tiên streams khi chỉ cần đọc nội dung.

## 10. ResourceLoader và location prefixes

`ResourceLoader.getResource(String location)` chuyển tên location thành một `Resource`.

| Prefix | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `classpath:` | `classpath:demo/templates/receipt.txt` | Nguồn trong classpath |
| `file:` | `file:/opt/app/config.txt` | Nguồn file system |
| `https:` | `https://example.org/config.txt` | Nguồn URL, có thể cần network |
| Không prefix | `demo/templates/receipt.txt` | Cách xử lý tùy concrete ApplicationContext |

Với dữ liệu đóng cùng ứng dụng, dùng `classpath:` rõ ràng để tránh phụ thuộc current working directory.

Trong demo:
```java
Resource template =
        resourceLoader.getResource(
                "classpath:demo/templates/receipt.txt");

if (!template.exists()) {
    throw new FileNotFoundException(template.getDescription());
}

try (InputStream in = template.getInputStream()) {
    String body = new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
}
```

Mảnh này cần imports `Resource`, `InputStream`, `StandardCharsets`, `FileNotFoundException`. Việc đóng stream bằng try-with-resources là trách nhiệm của caller.

## 11. Dùng ResourceReader an toàn trong JAR

Đừng phụ thuộc `getFile()` khi thiết kế thư viện được đóng gói:

```java
// Không portable nếu resource đã nằm trong JAR:
File file = resource.getFile();
```

Thay bằng:

```java
try (InputStream stream = resource.getInputStream()) {
    byte[] bytes = stream.readAllBytes();
}
```

Mảnh thứ hai hoạt động với `ClassPathResource` từ directory hoặc JAR thông thường. Các trường hợp special như `InputStreamResource` có semantics single-use stream; nên đọc đúng một lần khi nguồn vốn đã mở sẵn.

Một Resource descriptor thường có thể dùng để mở stream nhiều lần, nhưng không giả định mọi implementation đều có semantics đó. Với large resources, đọc bằng `BufferedReader` hoặc streaming parser thay vì `readAllBytes()` để tránh peak memory không cần thiết.

## 12. ResourcePatternResolver và classpath*:

`ResourceLoader` giải quyết **một location**. `ResourcePatternResolver` mở rộng để resolve **nhiều matching resources**.

```java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver(context);

Resource[] messages = resolver.getResources(
        "classpath*:demo/i18n/messages_*.properties");
```

Đây là mảnh Java minh họa.

- `classpath*:` tìm matching resources qua ClassLoader classpath locations.
- `*` là wildcard theo pattern, không phải tên chính xác một file.
- Kết quả có thể đến từ nhiều JARs/directories.
- Không dựa vào thứ tự iteration của results.
- Khi dùng `classpath*:` với wildcards, nhiều ClassLoader/JAR URL implementations có portability caveats; nên test trên packaging/deployment thực.

Trong project nhỏ này, có đúng hai files `messages_en.properties` và `messages_vi.properties`, nên demo dự kiến đếm hai matching resources. Trong ứng dụng lớn có modules/JARs khác, số file có thể khác.

## 13. Đặt resources ở đâu trong Gradle project?

Đối với Gradle Java plugin, resources dưới:

```text
src/main/resources/
    demo/
        application.properties
        templates/
            receipt.txt
        i18n/
            messages_en.properties
            messages_vi.properties
```

được copy vào runtime classpath (thường `build/resources/main`), sau đó có thể được đóng vào JAR. Java class `demo.ReceiptDemo` được compile riêng ở `src/main/java`.

Không tự dùng đường dẫn tương đối như:

```java
Files.readString(Path.of(
        "src/main/resources/demo/templates/receipt.txt"));
```

trong application runtime code. Đường dẫn này chỉ tình cờ đúng khi chạy từ project root, không đúng khi container/JAR được chạy ở nơi khác.

## 14. MessageSource là gì?

`MessageSource` cung cấp lookup thông điệp theo **mã (code)**, **danh sách arguments** và **Locale**.

Các method cốt lõi:

```java
String getMessage(
        String code, Object[] args, Locale locale)
        throws NoSuchMessageException;

String getMessage(
        String code, Object[] args,
        String defaultMessage, Locale locale);
```

Đây là chữ ký rút gọn.

Ví dụ:
- Code `status.ready`.
- Argument `"Lan"`.
- Locale `vi` → `Đã sẵn sàng, Lan!`.
- Locale `en` → `Ready, Lan!`.

Spring ApplicationContext cũng implements `MessageSource`. Khi context refresh, framework tìm bean tên **`messageSource`**. Nếu có, các lời gọi `context.getMessage(...)` được delegate đến bean này.

**Đặt đúng tên bean rất quan trọng.** Một bean tên `myTranslationSource` cùng type có thể được ứng dụng inject tường minh, nhưng ApplicationContext không tự chọn nó làm MessageSource chính theo quy ước.

## 15. ResourceBundleMessageSource

`ResourceBundleMessageSource` dùng Java `ResourceBundle` với các basenames:

```java
ResourceBundleMessageSource source =
        new ResourceBundleMessageSource();

source.setBasename("demo/i18n/messages");
source.setDefaultEncoding("UTF-8");
source.setFallbackToSystemLocale(false);
```

Spring tìm files theo locale, chẳng hạn:
- `demo/i18n/messages_vi.properties`.
- `demo/i18n/messages_en.properties`.
- `demo/i18n/messages.properties` nếu có và cần fallback.

**Basename không chứa `classpath:` hoặc `.properties`** khi cấu hình `ResourceBundleMessageSource` kiểu chuẩn.

Dùng UTF-8 là lựa chọn thực tế cho tiếng Việt. Java 9+ ResourceBundle properties cũng hỗ trợ UTF-8 mặc định; khai báo `setDefaultEncoding("UTF-8")` làm intent rõ trong Spring implementation, tránh bị nhầm với xử lý `Properties.load(InputStream)` truyền thống vốn có mặc định encoding khác.

`setFallbackToSystemLocale(false)` tránh chọn locale của JVM host một cách tình cờ khi không tìm được message bundle cho locale yêu cầu. Trong production, tùy nhu cầu có thể có bundle mặc định và fallback strategy khác.

## 16. MessageFormat placeholders

File `messages_vi.properties` có:

```properties
status.ready=Đã sẵn sàng, {0}!
```

Khi gọi:

```java
messageSource.getMessage(
        "status.ready",
        new Object[]{"Lan"},
        Locale.forLanguageTag("vi"));
```

kết quả là:

```text
Đã sẵn sàng, Lan!
```

Các placeholder `{0}`, `{1}` dùng quy tắc `java.text.MessageFormat`, không phải placeholder property `${...}` của Spring. Hai cú pháp phục vụ hai tầng:
- `${app.name}` → đọc cấu hình Environment, thường dùng trong `@Value`.
- `{0}` → tham số định dạng message i18n.

Cần kiểm tra escaping ký tự nháy đơn trong `MessageFormat` khi dịch các ngôn ngữ có apostrophe. Không nối tên người dùng trực tiếp vào một message pattern như code thay thế lỏng lẻo; truyền arguments qua `getMessage`.

## 17. Missing message code và fallback

Hai overload có semantics khác nhau:

```java
String defaulted = messageSource.getMessage(
        "status.unknown",
        null,
        "Unknown message",
        Locale.ENGLISH);
```

Overload này trả `"Unknown message"` nếu không tìm thấy translation.

```java
String required = messageSource.getMessage(
        "status.unknown",
        null,
        Locale.ENGLISH);
```

Overload này ném `NoSuchMessageException` khi không có message phù hợp và không có cách resolve khác.

Đừng che tất cả message code typos bằng `useCodeAsDefaultMessage=true` trong production nếu muốn phát hiện thiếu bản dịch. Với optional user-facing labels, default message hợp lý có thể là một fallback có chủ đích.

## 18. Locale được chọn ở đâu?

`Locale` là một giá trị Java có thể được truyền tường minh:

```java
Locale vietnamese = Locale.forLanguageTag("vi");
Locale english = Locale.ENGLISH;
```

Trong demo CLI, caller tự chọn locale. Trong web application, locale có thể đến từ request, header, session hoặc một `LocaleResolver` của Spring MVC — đây là integration ở tầng web, **không tự xảy ra** vì bạn khai báo MessageSource bean.

Không dùng `Locale.getDefault()` cho một test muốn output cố định, vì JVM trên máy khác có locale mặc định khác. Tạo Locale tường minh giúp test deterministic.

## 19. MessageSource khác ResourceLoader

Có một điểm dễ lẫn:

- `ResourceLoader` tải **nội dung file cụ thể**: template, CSV, JSON, XML, binary asset.
- `MessageSource` trả **chuỗi được dịch theo locale**, có thể đọc từ bundle properties nhưng có lookup/format/fallback semantics riêng.
- `Environment` đọc **cấu hình key-value** điều khiển ứng dụng, không phải danh mục chuỗi dịch theo locale.

Không nên lấy `Environment.getProperty("status.ready")` để thực hiện i18n. Nó không nhận Locale và MessageFormat arguments như `MessageSource`.

Tương tự, không dùng `ResourceLoader` để tự đọc từng `messages_vi.properties` rồi tự triển khai fallback/format trừ khi thật sự đang xây một message framework khác.

## 20. Cơ chế override không nên làm thay đổi BeanDefinitions sau refresh

Trong demo, `MapPropertySource` được addFirst **trước `refresh()`**, nên `@Value` và `Environment` cùng thấy `app.retry-count=5` trong giai đoạn tạo bean.

Nếu bạn gọi `addFirst` **sau** khi singleton `ReceiptService` đã tạo:
- `Environment.getProperty("app.retry-count")` có thể trả giá trị mới.
- Nhưng field/constructor argument `@Value int retryCount` trong singleton **không tự cập nhật**.
- Những singleton đã tạo vẫn giữ giá trị injected cũ trừ khi ứng dụng tự thiết kế refresh mechanism.
- `@Profile` / `@Conditional` không tự reevaluate bean graph.

Đừng dùng động tác sửa Environment runtime rồi giả định mọi beans tự reload.

Với Spring Cloud Config/refresh scope, có thể có cơ chế refresh thêm, nhưng đó là module và lifecycle khác, không phải default Spring Core behavior.

## 21. Dự án Java 17 chạy được

Bài thực hành tích hợp tất cả ba cơ chế trong một service:

- `@PropertySource` nạp `demo/application.properties`.
- `MapPropertySource("exerciseOverride")` override retry count từ 3 thành 5.
- `@Bean static PropertySourcesPlaceholderConfigurer` giải quyết `@Value`.
- `@Bean(name="messageSource")` dùng `ResourceBundleMessageSource` với UTF-8.
- `@Profile("dev")` đăng ký một `DevMarker`.
- `ResourceLoader` đọc template hóa đơn từ classpath.
- `PathMatchingResourcePatternResolver` tìm cả hai message bundles.
- `Environment` và `ReceiptService` in kết quả nhất quán.
- Một context thứ hai với profile prod và không override minh họa config isolation.

### 21.1 Cây thư mục

```text
spring-env-resource-message/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/
    │   ├── java/demo/ReceiptDemo.java
    │   └── resources/demo/
    │       ├── application.properties
    │       ├── templates/receipt.txt
    │       └── i18n/
    │           ├── messages_vi.properties
    │           └── messages_en.properties
    └── test/
        └── java/demo/ReceiptDemoTest.java
```

### 21.2 settings.gradle

```groovy
rootProject.name = 'spring-env-resource-message'
```

### 21.3 build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ReceiptDemo'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Không cần Spring Boot starter, server hoặc database. Các dependencies Spring core đi cùng `spring-context`.

### 21.4 src/main/resources/demo/application.properties

```properties
app.name=Container Lab
app.retry-count=3
app.template=classpath:demo/templates/receipt.txt
```

Tên `application.properties` chỉ là lựa chọn của ví dụ. **Plain Spring Framework không tự đọc nó**; việc nạp xảy ra do `@PropertySource("classpath:demo/application.properties")`.

### 21.5 src/main/resources/demo/templates/receipt.txt

```text
Order: {orderId}
Status: READY
```

Dấu `{orderId}` là placeholder template do business service thay thế bằng `String.replace`, **không phải** `MessageFormat` placeholder `{0}` và cũng không phải Spring Environment `${...}`.

### 21.6 src/main/resources/demo/i18n/messages_vi.properties

```properties
status.ready=Đã sẵn sàng, {0}!
status.order=Đơn hàng {0} đã sẵn sàng.
```

Lưu file UTF-8 thực sự, không phải legacy encoding tùy editor. Spring's `ResourceBundleMessageSource` được cấu hình UTF-8.

### 21.7 src/main/resources/demo/i18n/messages_en.properties

```properties
status.ready=Ready, {0}!
status.order=Order {0} is ready.
```

Cùng message codes ở các locales giúp business code không phải chuyển `if (locale == vi)` thủ công. Nếu thiếu key ở một locale, bundle/default/fallback semantics sẽ quyết định kết quả.

### 21.8 src/main/java/demo/ReceiptDemo.java

```java
package demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public final class ReceiptDemo {

    private ReceiptDemo() {
    }

    public record DevMarker(String label) {
    }

    public static final class ReceiptService {
        private final ResourceLoader resourceLoader;
        private final MessageSource messages;
        private final String appName;
        private final int retryCount;
        private final String templateLocation;

        public ReceiptService(
                ResourceLoader resourceLoader,
                MessageSource messages,
                String appName,
                int retryCount,
                String templateLocation) {
            this.resourceLoader = resourceLoader;
            this.messages = messages;
            this.appName = appName;
            this.retryCount = retryCount;
            this.templateLocation = templateLocation;
        }

        public String appName() {
            return appName;
        }

        public int retryCount() {
            return retryCount;
        }

        public Resource templateResource() {
            return resourceLoader.getResource(templateLocation);
        }

        public String receipt(String orderId) throws IOException {
            Resource resource = templateResource();

            try (InputStream in = resource.getInputStream()) {
                String template = new String(
                        in.readAllBytes(), StandardCharsets.UTF_8);

                String filled = template.replace(
                        "{orderId}", orderId);

                return filled.lines().collect(
                        Collectors.joining("|"));
            }
        }

        public String ready(String who, Locale locale) {
            return messages.getMessage(
                    "status.ready",
                    new Object[]{who},
                    locale);
        }

        public String withDefault(
                String code, String fallback, Locale locale) {
            return messages.getMessage(
                    code, null, fallback, locale);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @PropertySource("classpath:demo/application.properties")
    public static class AppConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer
                placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean(name = "messageSource")
        public ResourceBundleMessageSource messageSource() {
            ResourceBundleMessageSource source =
                    new ResourceBundleMessageSource();
            source.setBasename("demo/i18n/messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            return source;
        }

        @Bean
        public ReceiptService receiptService(
                ResourceLoader resourceLoader,
                MessageSource messages,
                @Value("${app.name}") String name,
                @Value("${app.retry-count}") int retries,
                @Value("${app.template}") String location) {
            return new ReceiptService(
                    resourceLoader, messages,
                    name, retries, location);
        }

        @Bean
        @Profile("dev")
        public DevMarker devMarker() {
            return new DevMarker("development");
        }
    }

    public static AnnotationConfigApplicationContext open(
            String profile, Map<String, Object> overrides) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();

        context.getEnvironment().setActiveProfiles(profile);

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                        "exerciseOverride", overrides));

        context.register(AppConfig.class);
        context.refresh();
        return context;
    }

    public static int countMessageFiles(
            ResourceLoader loader) throws IOException {
        ResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(loader);
        return resolver.getResources(
                "classpath*:demo/i18n/messages_*.properties").length;
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     open("dev", Map.of(
                             "app.retry-count", "5",
                             "app.stage", "interactive"))) {

            Environment env = context.getEnvironment();
            ReceiptService service =
                    context.getBean(ReceiptService.class);

            System.out.println("profile.dev="
                    + context.containsBean("devMarker"));
            System.out.println("env.app.name="
                    + env.getRequiredProperty("app.name"));
            System.out.println("env.retry="
                    + env.getProperty(
                            "app.retry-count", Integer.class));
            System.out.println("bean.retry="
                    + service.retryCount());
            System.out.println("env.stage="
                    + env.getProperty("app.stage"));
            System.out.println("resource.exists="
                    + service.templateResource().exists());
            System.out.println("resource.receipt="
                    + service.receipt("A-100"));
            System.out.println("message.vi="
                    + service.ready(
                            "Lan", Locale.forLanguageTag("vi")));
            System.out.println("message.en="
                    + service.ready("Lan", Locale.ENGLISH));
            System.out.println("message.default="
                    + service.withDefault(
                            "status.missing",
                            "Unknown message", Locale.ENGLISH));
            System.out.println("messages.files="
                    + countMessageFiles(context));
        }

        try (AnnotationConfigApplicationContext context =
                     open("prod", Map.of())) {
            ReceiptService service =
                    context.getBean(ReceiptService.class);

            System.out.println("profile.prodDevMarker="
                    + context.containsBean("devMarker"));
            System.out.println("prod.retry="
                    + service.retryCount());
        }
    }
}
```

### 21.9 Chạy ứng dụng

Yêu cầu JDK 17+ và Gradle 8+:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, có thể thay bằng `./gradlew run --quiet` trên Unix-like OS hoặc `gradlew.bat run --quiet` trên Windows.

### 21.10 System.out dự kiến

```text
profile.dev=true
env.app.name=Container Lab
env.retry=5
bean.retry=5
env.stage=interactive
resource.exists=true
resource.receipt=Order: A-100|Status: READY
message.vi=Đã sẵn sàng, Lan!
message.en=Ready, Lan!
message.default=Unknown message
messages.files=2
profile.prodDevMarker=false
prod.retry=3
```

Đây là các dòng do `System.out.println` của demo in; không tính Gradle output hoặc logger/backend được môi trường bổ sung. File template được normalize qua `String.lines()` và join bằng `|` nên line endings Windows/Unix không làm thay đổi kết quả hiển thị.

### 21.11 Vì sao output có retry = 5?

Classpath `demo/application.properties` ghi `app.retry-count=3`. Trước refresh, `MapPropertySource("exerciseOverride")` được thêm bằng `addFirst` với cùng key có giá trị `"5"`. Do đó:
- `Environment.getProperty("app.retry-count", Integer.class)` → `5`.
- `@Value("${app.retry-count}") int retries` được resolve khi bean tạo → `5`.
- `ReceiptService.retryCount()` → `5`.
- Khi tạo context prod **không override**, file base value `3` được sử dụng.

Hai contexts độc lập có own BeanFactories/singletons; không phải đang “đổi” một bean giữa dev/prod lúc runtime.

### 21.12 Vì sao messageSource hoạt động?

Bean `@Bean(name="messageSource")` được framework nhận theo convention lúc refresh. `ResourceBundleMessageSource` đọc bundle theo basename `demo/i18n/messages` và locale request. Khi `ready("Lan", vi)`, key `status.ready` được định dạng bằng argument `{0}` từ `messages_vi.properties`.

Việc method signature nhận `MessageSource` không phải yêu cầu mọi service giữ nguyên ApplicationContext; đó là interface để tách service ra khỏi IoC API.

### 21.13 Vì sao không dùng getFile()?

`ReceiptService` nhận `Resource` bằng classpath location. Nó mở stream và đọc UTF-8; cách đó làm việc khi assets được copy vào runtime classpath hoặc đóng trong JAR. Gọi `resource.getFile()` có thể fail nếu resource nằm trong JAR, nên bài này tránh.

### 21.14 Vì sao wildcard trả hai files?

Pattern `classpath*:demo/i18n/messages_*.properties` trùng `messages_en.properties` và `messages_vi.properties`. Trong project demo không có các bundles khác cùng path, nên expected count là 2. Không suy kết quả này thành một hằng số bất biến cho mọi ứng dụng có nhiều dependency JAR.

## 22. Kiểm thử tích hợp với JUnit Jupiter

Tests nên mở context mới cho mỗi case, để property sources/profiles không rò sang test khác. Vì `open(...)` nhận config profile và override, mỗi test có thể kiểm chứng một decision độc lập.

Các kịch bản:
1. Override property được `Environment` và `@Value` nhìn thấy nhất quán.
2. Không override → dùng giá trị từ classpath property file.
3. `@Profile("dev")` chỉ tạo bean trong dev context.
4. Classpath resource đọc đúng ngay cả khi không phải file system path.
5. `MessageSource` có output tiếng Việt và tiếng Anh.
6. Missing message code: overload required ném `NoSuchMessageException`, overload default trả fallback.
7. Wildcard resolver tìm đủ hai locale bundles.
8. Missing resource: `exists()` false; việc mở stream sẽ fail.
9. Bean convention: context có named `messageSource` và gọi qua `context.getMessage` được.

### 22.1 src/test/java/demo/ReceiptDemoTest.java

```java
package demo;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptDemoTest {

    private AnnotationConfigApplicationContext open(
            String profile, Map<String, Object> overrides) {
        return ReceiptDemo.open(profile, overrides);
    }

    @Test
    void propertyOverrideWinsAndValueIsConverted() {
        try (var context = open(
                "dev", Map.of("app.retry-count", "5"))) {

            var env = context.getEnvironment();
            var service = context.getBean(
                    ReceiptDemo.ReceiptService.class);

            assertEquals(5,
                    env.getProperty(
                            "app.retry-count", Integer.class));
            assertEquals(5, service.retryCount());
            assertEquals("Container Lab", service.appName());
        }
    }

    @Test
    void withoutOverrideUsesClasspathPropertyFile() {
        try (var context = open("prod", Map.of())) {
            var service = context.getBean(
                    ReceiptDemo.ReceiptService.class);

            assertEquals(3, service.retryCount());
            assertEquals("Container Lab",
                    context.getEnvironment()
                            .getRequiredProperty("app.name"));
        }
    }

    @Test
    void profileAffectsBeanDefinitionPresence() {
        try (var dev = open("dev", Map.of());
             var prod = open("prod", Map.of())) {

            assertTrue(dev.containsBean("devMarker"));
            assertFalse(prod.containsBean("devMarker"));
        }
    }

    @Test
    void readsClasspathTemplateThroughInputStream()
            throws IOException {
        try (var context = open("dev", Map.of())) {
            var service = context.getBean(
                    ReceiptDemo.ReceiptService.class);

            assertTrue(service.templateResource().exists());
            assertEquals(
                    "Order: A-100|Status: READY",
                    service.receipt("A-100"));
        }
    }

    @Test
    void messagesAreLocalizedUsingMessageArguments() {
        try (var context = open("dev", Map.of())) {
            var service = context.getBean(
                    ReceiptDemo.ReceiptService.class);

            assertEquals(
                    "Đã sẵn sàng, Lan!",
                    service.ready(
                            "Lan", Locale.forLanguageTag("vi")));
            assertEquals(
                    "Ready, Lan!",
                    service.ready("Lan", Locale.ENGLISH));
        }
    }

    @Test
    void missingMessageHasRequiredAndFallbackModes() {
        try (var context = open("dev", Map.of())) {
            var service = context.getBean(
                    ReceiptDemo.ReceiptService.class);

            assertThrows(
                    NoSuchMessageException.class,
                    () -> context.getMessage(
                            "status.missing", null, Locale.ENGLISH));

            assertEquals(
                    "Unknown message",
                    service.withDefault(
                            "status.missing",
                            "Unknown message", Locale.ENGLISH));
        }
    }

    @Test
    void patternResolverFindsBothLocaleBundles()
            throws IOException {
        try (var context = open("dev", Map.of())) {
            assertEquals(
                    2, ReceiptDemo.countMessageFiles(context));
        }
    }

    @Test
    void missingResourceDoesNotSilentlyBecomeEmpty()
            throws IOException {
        try (var context = open("dev", Map.of())) {
            var resource = context.getResource(
                    "classpath:demo/templates/missing.txt");

            assertFalse(resource.exists());

            assertThrows(
                    IOException.class,
                    () -> {
                        try (var ignored =
                                     resource.getInputStream()) {
                            // Should not be reached.
                        }
                    });
        }
    }

    @Test
    void contextDelegatesToMessageSourceBean() {
        try (var context = open("dev", Map.of())) {
            assertTrue(context.containsBean("messageSource"));

            assertEquals(
                    "Ready, Lan!",
                    context.getMessage(
                            "status.ready",
                            new Object[]{"Lan"},
                            Locale.ENGLISH));
        }
    }
}
```

### 22.2 Chạy tests

```bash
gradle test
```

Kết quả logic dự kiến: **9 JUnit tests pass**. Gradle có thể đặt HTML report ở `build/reports/tests/test/index.html`. Các tests dùng contexts độc lập nên không phụ thuộc thứ tự chạy và không cần `@DirtiesContext`.

**Giới hạn xác minh:** Code, signatures và expected output được rà soát tĩnh theo Spring 6.2 contracts. Cần chạy Gradle/JUnit trong toolchain thực tế để xác nhận build/runtime. Đừng hiểu stdout mẫu là kết quả tool đã thực thi sẵn.

## 23. Khi nào cần test thiếu property?

Ví dụ một property bắt buộc:

```java
String name = environment.getRequiredProperty(
        "app.name");
```

Nếu không có `app.name` trong bất kỳ property source nào, method sẽ ném `IllegalStateException`. Nhưng trong project bài này có file `demo/application.properties` chứa key đó, nên bạn phải tạo **context/config độc lập** không import file hoặc loại key khỏi tất cả sources để test failure mode.

Đừng lặng lẽ xóa property khỏi file dự án chính rồi yêu cầu các tests khác vẫn thành công. Một failure test tốt chủ động cô lập config lỗi, không phá môi trường của toàn bộ test suite.

Với `@Value("${app.retry-count}") int retries`, giá trị `abc` gây conversion failure khi tạo bean. Có thể dùng một test context override `app.retry-count=abc` và assert `BeansException` lúc refresh; tránh assert toàn bộ exception string.

## 24. Tài nguyên bị thiếu và error handling

`ResourceLoader.getResource("classpath:...")` thường trả **Resource descriptor** kể cả khi path không tồn tại. Lỗi xuất hiện khi:
- `exists()` trả false.
- `getInputStream()` không mở được nguồn, thường ném `FileNotFoundException` (thuộc `IOException`).
- Parser xử lý input/format lỗi.

Vì vậy:

```java
Resource resource = loader.getResource(location);

if (!resource.exists()) {
    throw new IllegalStateException(
            "Resource missing: " + location);
}
```

Mảnh này phù hợp nếu file bắt buộc. Nhưng giữa check `exists()` và open stream, file bên ngoài vẫn có thể bị xóa (time-of-check/time-of-use). Production cần bắt `IOException` từ `getInputStream()`, không giả định precheck bảo đảm tuyệt đối.

Đối với classpath resources đóng cùng JAR, missing file thường là lỗi packaging/build configuration. Đối với file: external resources, có thể thêm validation permission/ownership.

## 25. Resource loading khác file scanning

Một URL có thể chỉ tới tài nguyên cụ thể, còn wildcard pattern là yêu cầu tìm nhiều resources:

```java
Resource single = loader.getResource(
        "classpath:demo/templates/receipt.txt");

Resource[] many = resolver.getResources(
        "classpath*:demo/i18n/messages_*.properties");
```

Không đưa wildcard vào `ResourceLoader.getResource()` rồi mong API luôn mở rộng thành một mảng resources. `ResourceLoader` không phải `ResourcePatternResolver`.

Khi wildcard resolution quan trọng với deploy JARs phức tạp, nên viết integration test trên **packaged artifact**, không chỉ test dưới Gradle class directories.

## 26. Cấu hình file ngoài JAR

Một ứng dụng production có thể cần thay cấu hình mà không rebuild JAR. Khi đó:
- Dùng `file:` location hoặc external property source được đăng ký sớm.
- Xác định rõ precedence: default classpath file trước, external overrides sau hoặc ngược lại tùy policy.
- Kiểm soát file permissions và secrets.
- Fail fast nếu required configuration thiếu.
- Không dùng arbitrary URL resource fetch ở startup mà không timeout/retry/security policy.

Mẫu minh họa:

```java
Resource external = loader.getResource(
        "file:/etc/myapp/receipt-template.txt");
```

Đây không phải path phải tồn tại trong demo. `file:` chỉ dẫn cách tìm nội dung ở filesystem; nó **không** làm file được tạo tự động.

Nếu cần reload file thông điệp theo thời gian, `ReloadableResourceBundleMessageSource` có caching/reload controls, nhưng filesystem và classpath semantics khác nhau, cần kiểm tra target runtime.

## 27. ResourceBundleMessageSource so với ReloadableResourceBundleMessageSource

| Khía cạnh | ResourceBundleMessageSource | ReloadableResourceBundleMessageSource |
| --- | --- | --- |
| Cơ chế chính | Java ResourceBundle | Spring resource-based properties |
| Basename mẫu | `demo/i18n/messages` | `classpath:demo/i18n/messages` hoặc `file:...` |
| File UTF-8 | Có encoding configuration | Có encoding configuration |
| Caching | Theo ResourceBundle/MessageSource caching | `cacheSeconds` và reload logic riêng |
| Classpath trong packaged JAR | Phù hợp cho bundles đóng gói | Có thể đọc, nhưng hot reload classpath trong JAR không phải mục tiêu tin cậy |
| File ngoài JAR thay đổi | Không phải use case chuẩn của classpath bundles | Phù hợp hơn khi được cấu hình với external file resources |
| Cần web server? | Không | Không |

Không dùng `ReloadableResourceBundleMessageSource` chỉ vì tên nghe “tốt hơn”. Nếu translations đóng cố định trong app, `ResourceBundleMessageSource` gọn và ổn định. Với vận hành đòi cập nhật bản dịch ngoài process, cân nhắc nguồn external, cache policy và đồng bộ khi nhiều nodes chạy.

## 28. Message formatting: tránh lỗi dấu nháy

`java.text.MessageFormat` có quy tắc escaping riêng cho dấu nháy đơn. Ví dụ bản dịch tiếng Anh có apostrophe như `It's ready` và arguments là `{0}` có thể cần escape apostrophe theo MessageFormat semantics trong pattern phù hợp.

Không coi các message values chỉ là template literal rồi dùng một generic `String.replace("{0}", name)` cho mọi ngôn ngữ. `MessageSource`/`MessageFormat` có thể xử lý number/date formatting theo locale, nhưng cần pattern đúng và test localization với dữ liệu thật.

Khi translator chỉnh files, nên có automated tests kiểm tra:
- Mã quan trọng xuất hiện đủ trong `vi` và `en` bundles.
- MessageFormat patterns parse đúng.
- Parameters không bị đảo sai ý nghĩa.
- Encoding không làm mất dấu tiếng Việt.
- Fallback policy đã thống nhất.

## 29. Hệ quả khi inject @Value vào singleton

`ReceiptService` của bài nhận tên và retry count bằng `@Value` trong factory method rồi lưu vào final fields. Nó phản ánh configuration **tại thời điểm bean được tạo**.

Tình huống:

```text
1. refresh()
2. ReceiptService.retryCount = 3
3. environment.properties.addFirst(override retry-count=5)
4. environment.getProperty("app.retry-count") = "5"
5. ReceiptService.retryCount vẫn = 3
```

Sơ đồ này giả định service đã được instantiate ở bước 2 và không có custom refresh. Đây là hành vi Java object bình thường: final field không tự cập nhật khi một map trong Environment thay đổi.

Nếu cần feature flags dynamic, có thể inject một object `Environment`/configuration service và truy vấn giá trị khi cần, nhưng phải xử lý caching, consistency và multi-thread safety. Không làm mọi giá trị phải truy vấn runtime nếu nó chỉ là static startup configuration.

## 30. @Profile và cấu hình giá trị là hai cơ chế tách biệt

Profile:
```java
@Profile("dev")
@Bean
DevMarker devMarker() { /* ... */ }
```

Property:
```java
@Value("${app.retry-count}")
int retries;
```

`@Profile` quyết định **BeanDefinition có được đăng ký**. `@Value` cung cấp **giá trị** cho dependency injection khi bean được tạo.

Khi muốn dev/prod có hai providers khác nhau, profiles hoặc `@Conditional` có thể thích hợp. Khi chỉ muốn đổi retry count, property source override là đủ; không nhất thiết tạo hai Java config classes/beans chỉ vì một con số thay đổi.

Trong Boot, profiles còn liên quan nhiều file configuration conventions, nhưng bài này vẫn dùng Core APIs trực tiếp.

## 31. Không đặt secrets trong classpath property file

Một ứng dụng đóng JAR thường có thể bị inspect. Đừng đưa production passwords/API keys vào `src/main/resources/application.properties` rồi tin rằng chúng được bảo mật vì đã nằm trong JAR.

Đối với secrets:
- Sử dụng secret manager, environment variables hoặc external configuration an toàn.
- Không in toàn bộ property sources hoặc values nhạy cảm vào logs.
- Hạn chế quyền truy cập file configuration external.
- Không commit secrets vào source repository.
- Phân biệt public configuration như retry count với private credentials.

`Environment` cung cấp abstraction để đọc values, **không tự mã hóa** hoặc phân quyền chúng.

## 32. Những lỗi thường gặp khi kết hợp

| Triệu chứng | Nguyên nhân có thể | Cách sửa |
| --- | --- | --- |
| `@Value` không resolve | Chưa đăng ký property source/processor, key sai | Kiểm tra `@PropertySource`, Environment và placeholder |
| app.properties có trên disk nhưng không được đọc | Core Spring không tự auto-load file | Khai báo `@PropertySource` tường minh |
| Override không có tác dụng | Added source sau source có precedence cao hơn | `addFirst`/`addBefore` đúng thứ tự |
| Environment trả 5, singleton field vẫn 3 | Thay properties sau khi bean đã tạo | Recreate context/thiết kế refresh riêng |
| `@Profile("dev")` không tạo bean | Profile active sai hoặc đặt sau refresh | Set trước refresh |
| Resource có trong IDE, lỗi trong JAR | Dùng `getFile()` | Dùng `getInputStream()` |
| `resource.exists()` false | File không đóng gói/classpath location sai | Kiểm tra `src/main/resources` và path |
| Đọc sai tiếng Việt | File encoding sai | Lưu UTF-8 + khai báo encoding |
| MessageSource bean có type đúng nhưng context không dùng | Bean name không phải `messageSource` | Đặt đúng name |
| `NoSuchMessageException` | Missing code/locale bundle | Thêm key hoặc fallback được chủ đích |
| Message arguments không thay | Dùng sai `${...}` thay `{0}` | Dùng MessageFormat syntax |
| Fallback sang ngôn ngữ JVM host | Không cấu hình locale fallback | Set fallbackToSystemLocale(false) khi cần |
| Resolver trả 0 files khi deploy JAR | Wildcard/ClassLoader portability issue | Test classpath* packaging hoặc dùng explicit file paths |
| `getBeansOfType` tạo bean sớm khi diagnostics | API trả instances | Dùng `getBeanNamesForType` cho discovery |
| JUnit tests phụ thuộc thứ tự | Dùng context/static values chung | Mỗi test context riêng, cleanup rõ |
| Bean sources đúng mà thiếu resources | Dependencies resources không vào runtime classpath | Kiểm tra build resources task |
| Sử dụng Boot guide trên plain Spring app | Boot auto-configuration không tồn tại | Dùng `@PropertySource` và explicit `@Bean` |

## 33. Kế hoạch kiểm tra khi ứng dụng thật bị lỗi

1. In active profiles **trước refresh**.
2. Kiểm tra `@Configuration` class thực sự được register/import.
3. Xem `Environment.getPropertySources()` theo thứ tự (chỉ source names, tránh in secrets).
4. Kiểm tra mỗi required key qua `getRequiredProperty` hoặc validation.
5. Kiểm tra typed conversion (int, Duration, URL, Locale tùy converter).
6. Kiểm tra bean `messageSource` hiện diện theo name.
7. Gọi `getMessage` với Locale explicit và known test code.
8. Kiểm tra resource `exists` và đọc qua stream.
9. Xác định resource path trong packaged JAR.
10. Với wildcard, kiểm tra matched filenames nhưng không dựa vào thứ tự.
11. Với @Profile, thử contexts riêng cho dev/prod.
12. Với lỗi startup, xem cause chain và bean factory method/injection point.
13. Có negative tests cho missing key/resource/message.
14. Không để default fallback che mất misconfiguration quan trọng.
15. Không suy config runtime thay đổi đã refresh singleton.
16. Đảm bảo các file translation dùng encoding đúng.
17. Không lẫn MessageSource với Environment property strings.
18. Chạy Gradle/JUnit trong cùng Java version như production.

## 34. Mở rộng bài tập

### Bài 1 — Thử thứ tự property sources

Trong `ReceiptDemo.main`, thay `addFirst` bằng `addLast` cho MapPropertySource, giữ `app.retry-count=5`. Dự đoán nguồn nào thắng và vì sao. Lưu ý Spring StandardEnvironment có JVM system properties và environment variables đứng trước source thêm cuối; nếu máy có override key cùng tên, kết quả có thể khác.

Viết một JUnit test assert specific order của `MutablePropertySources` hoặc chọn test environment đã kiểm soát rõ system properties.

### Bài 2 — Thêm tiếng Pháp

Tạo `messages_fr.properties` với `status.ready=Prêt, {0}!` và gọi `Locale.FRENCH`. Update test `countMessageFiles` thành 3 vì lúc đó có thêm một matching bundle. Không chỉ thêm source code xử lý `if ("fr")`.

### Bài 3 — Đổi template

Tạo `receipt-compact.txt` chứa một dòng, override `app.template` trong MapPropertySource bằng new classpath location trước refresh. Kiểm tra service đọc đúng template mới. Đừng dùng đường dẫn file tuyệt đối trong Java code cho template đóng theo JAR.

### Bài 4 — Property conversion failure

Tạo context nhỏ override `app.retry-count=not-a-number`, expect failure ở `refresh` và assert cause chain chứa type conversion related exception (không snapshot exact message). Giữ tất cả source files chính ổn định.

### Bài 5 — Đo missing Resource

Gọi `context.getResource("classpath:demo/templates/absent.txt")` và assert `exists=false`, mở stream ném `IOException`. Không tự tạo file này chỉ để test đọc được; đây là negative test.

### Bài 6 — MessageFormat escaping

Thêm một message với apostrophe và tham số `{0}`. Quan sát khác biệt khi dấu nháy được escape đúng so với pattern sai. Viết test cố định cả vi/en output.

### Bài 7 — Phân tích fallback locales

Thêm base file `messages.properties` và một locale không có bundle riêng. Chạy với `fallbackToSystemLocale=false`; phân tích khi nào base message được dùng và khi nào `NoSuchMessageException` xuất hiện.

### Bài 8 — Package và chạy từ JAR

Sau khi thêm plugin/config packaging phù hợp, đưa resources vào JAR và chạy chương trình từ artifact/classpath được thiết lập chính xác. Chứng minh `getInputStream` vẫn hoạt động; `getFile()` có thể không. Với Gradle application distribution, cũng có thể chạy generated scripts mà không cần tự tạo fat JAR.

## 35. Thiết kế cho production

**Môi trường:** Nếu ứng dụng có nhiều environments, định nghĩa rõ priority (default config → environment-specific overrides → secrets/command-line override) và test ít nhất một config matrix. Có thể sử dụng `ApplicationContextInitializer` để gắn PropertySource trước refresh, hoặc dùng Boot config data khi chọn Boot stack.

**Tài nguyên:** Dùng classpath resources cho assets gắn với phiên bản ứng dụng, file resources cho dữ liệu có owner/vòng đời external, remote resources khi có timeout/security/retry policy. Đóng InputStream và cân nhắc input size.

**Thông điệp:** Dùng stable message codes, locale selection ở boundary phù hợp, test Unicode/MessageFormat patterns, và fallback policy minh bạch. Tránh chuyển MessageSource thành kho giá trị cấu hình bảo mật.

**Khả năng kiểm thử:** Constructor injection tường minh các dependencies cần dùng, context bootstrap ít side effects, mô hình test có fixture property/resource/messages rõ. Đừng đẩy dữ liệu environment lên static mutable fields rồi biến test order thành vấn đề.

**Vận hành:** Khi cấu hình nguồn ngoài thay đổi, quyết định rõ có cần restart/rebuild context hay một refresh protocol riêng; Spring Core không tự cập nhật @Value trong beans đã được tạo.

## 36. Câu hỏi ôn tập

**Hỏi:** Có file `application.properties` trên classpath; tại sao Spring Core không đọc?

**Đáp:** Trong Spring Framework thuần, tên file không kích hoạt auto-loading kiểu Spring Boot; cần đăng ký property source bằng `@PropertySource` hoặc code tương đương.

**Hỏi:** Vì sao `MapPropertySource.addFirst` cho retry count bằng 5 dù file gốc ghi 3?

**Đáp:** `Environment` tra sources theo thứ tự ưu tiên; source đứng đầu thắng khi key trùng nhau.

**Hỏi:** `Resource.getFile()` chạy trong IDE nhưng lỗi sau đóng JAR; tại sao?

**Đáp:** Resource bên trong JAR không phải một file path vật lý bình thường. Dùng `getInputStream()`.

**Hỏi:** `@Profile("dev")` có thay đổi bean graph sau khi đặt active profiles muộn không?

**Đáp:** Không tự động. Spring đã xử lý các BeanDefinitions khi refresh; cần tạo/rebuild context theo cơ chế đúng.

**Hỏi:** Khi missing i18n message code, cách nào trả fallback?

**Đáp:** Overload `getMessage(code, args, defaultMessage, locale)` trả fallback được chỉ định; overload không có default thường ném `NoSuchMessageException`.

**Hỏi:** Tại sao `@Bean ResourceBundleMessageSource mySource()` có thể không được context dùng tự động?

**Đáp:** Spring ApplicationContext nhận message source chính theo bean name convention **`messageSource`**.

**Hỏi:** `classpath*:...*.properties` có phải bảo đảm số resources tuyệt đối giống nhau trong mọi app server không?

**Đáp:** Không. Wildcard resolution phụ thuộc ClassLoader/JAR layout, phải test trong deployment target.

## 37. Nguồn tham khảo chính thức

1. [Spring Framework 6.2 Reference — Environment Abstraction](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html): profiles, property sources, @PropertySource, override precedence và placeholder resolution.
2. [Spring Framework 6.2 Reference — Resource Abstraction](https://docs.spring.io/spring-framework/reference/6.2/core/resources.html): Resource, ResourceLoader, ResourcePatternResolver, classpath/JAR caveats.
3. [Spring Framework 6.2 Reference — ApplicationContext Additional Capabilities](https://docs.spring.io/spring-framework/reference/6.2/core/beans/context-introduction.html): `messageSource` convention, MessageSource, ResourceLoader và events.
4. [Spring Framework 6.2 Reference — Using @Value](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html): placeholder injection and property conversion.
5. [Spring Framework 6.2.19 Javadoc — PropertySource](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/PropertySource.html): file loading và source ordering.
6. [Spring Framework 6.2.19 Javadoc — MapPropertySource](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/env/MapPropertySource.html): map-backed property source.
7. [Spring Framework 6.2.19 Javadoc — ResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/io/ResourceLoader.html): getResource semantics.
8. [Spring Framework 6.2.19 Javadoc — PathMatchingResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html): wildcards và classpath*: traversal.
9. [Spring Framework 6.2.19 Javadoc — ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html): basename, caching, locale bundle lookup.
10. [Spring Framework 6.2.19 Javadoc — MessageSource](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/MessageSource.html): required/default lookup overloads.
11. [Spring Framework 6.2.19 Javadoc — PropertySourcesPlaceholderConfigurer](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html): placeholder resolution infrastructure.
12. [JUnit Jupiter User Guide 5.11.4](https://docs.junit.org/5.11.4/user-guide/): @Test and Assertions APIs.

**Kết luận:** `Environment` quản lý cấu hình theo profiles và property source precedence; `ResourceLoader` đọc assets an toàn qua `Resource`/streams; `MessageSource` cung cấp mã thông điệp được dịch và format theo Locale. Khi tích hợp cả ba, đặt sources/profiles **trước refresh**, dùng explicit classpath locations, cấu hình bean tên `messageSource`, kiểm soát fallback/error behavior và xác minh bằng JUnit contexts độc lập. Đó là cách tạo một ứng dụng Spring Core vừa cấu hình được theo môi trường, vừa portable trong JAR, vừa có khả năng hiển thị nhiều ngôn ngữ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-006">Phát sự kiện nội bộ và theo dõi toàn bộ vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Phát sự kiện nội bộ và theo dõi toàn bộ vòng đời trong Spring Framework

## 1. Bài thực hành này giải quyết vấn đề gì?

Trong một ứng dụng dùng Spring Framework Core Container, **vòng đời** không chỉ là thời điểm một Java object được tạo rồi bị hủy. Cần phân biệt ít nhất ba dòng sự kiện:

1. **Vòng đời bean:** bean được tạo, inject dependencies, chạy callback khởi tạo và cuối cùng được hủy khi container quản lý nó đóng.
2. **Vòng đời ApplicationContext:** context được `refresh()`, có thể được `start()`, `stop()` và `close()`; Spring phát những sự kiện framework tương ứng.
3. **Sự kiện nghiệp vụ nội bộ:** một service phát thông báo như `WorkCompleted`, những listener được đăng ký trong cùng context xử lý mà producer không cần inject từng listener.

Bài thực hành dùng một **timeline** lưu các dấu mốc để quan sát cả ba dòng trên cùng một chương trình.

Sau khi học, bạn sẽ:

- Phát một POJO/record event bằng `ApplicationEventPublisher`.
- Xử lý event qua `@EventListener`, không tạo constructor dependency ngược về publisher.
- Theo dõi `ContextRefreshedEvent`, `ContextStartedEvent`, `ContextStoppedEvent` và `ContextClosedEvent`.
- Ghi nhận `@Bean(initMethod = ...)` và `@Bean(destroyMethod = ...)`.
- Biết lúc nào `refresh()` hoàn tất và lúc nào listeners thực sự được đăng ký.
- Đọc timeline để phân biệt lời gọi đồng bộ với sự kiện bất đồng bộ.
- Viết Java 17/Spring Context 6.2.19 và JUnit 5 tests kiểm chứng event order, lifecycle và cleanup.
- Xử lý đúng giới hạn về transaction, lỗi listener, event feedback loops và message durability.

Bài thuộc mục **Thực hành tổng hợp** của module **Spring Framework — core-container**, đề tài **“Phát sự kiện nội bộ và theo dõi toàn bộ vòng đời”**.

> Mục tiêu là một chương trình chạy được và tạo **bằng chứng quan sát**, không chỉ nói chung rằng “Spring có events”. Ví dụ dùng container thật nhưng không chạy web server, database, broker hoặc Spring Boot.

## 2. Phân biệt ba khái niệm ngay từ đầu

| Khái niệm | Ai điều khiển? | Ví dụ quan sát |
| --- | --- | --- |
| Bean initialization | Spring BeanFactory | `bean.init` |
| ApplicationContext lifecycle | `ConfigurableApplicationContext` | `context.refreshed`, `context.started` |
| Business event | Application service | `listener.work:J-7` |
| Bean destruction | Spring lúc destroy singleton | `bean.destroy` |
| Runtime method call | Chính application code | `work.before:J-7`, `work.after:J-7` |

Một **business event** có thể được publish khi context đang active, nhưng nó **không** đồng nghĩa `ContextRefreshedEvent` hay một bean init callback.

Một bean cũng có thể đã được khởi tạo từ lâu trước khi một nghiệp vụ đầu tiên chạy. Việc gọi `context.start()` không phải “lần đầu tiên tạo tất cả bean”; startup/refresh/bean initialization có semantics riêng.

## 3. Đề bài thực hành

Ứng dụng có một `WorkService` xử lý job có ID `J-7`:

1. Khi Spring tạo bean `LifecycleProbe`, callback `initialize()` ghi `bean.init`.
2. Khi container `refresh()` hoàn tất, listener nhận `ContextRefreshedEvent` và ghi `context.refreshed`.
3. Khi gọi `context.start()`, listener ghi `context.started`.
4. Khi gọi `workService.execute("J-7")`, service ghi `work.before:J-7`.
5. Service publish một immutable record `WorkCompleted("J-7")`.
6. Listener ghi `listener.work:J-7`.
7. Khi `publishEvent` trả về trong cấu hình mặc định đồng bộ, service ghi `work.after:J-7` và trả `DONE:J-7`.
8. Khi gọi `context.stop()`, listener ghi `context.stopped`.
9. Khi đóng context, listener ghi `context.closed`.
10. Spring hủy singleton `LifecycleProbe`, callback `destroy()` ghi `bean.destroy`.

Timeline phải đủ rõ để thấy **một event được phát và nhận lúc nào** so với các callbacks.

### Điều không nằm trong demo

- Không có database transaction.
- Không có HTTP requests.
- Không dùng `@Async` hoặc custom asynchronous multicaster.
- Không có durable queue/retry/exactly-once.
- Không khẳng định context `stop()` là context đã `close()`.
- Không tạo event trong constructor/init method rồi kỳ vọng listener bắt được.

## 4. Các actor và bean graph

Ứng dụng gồm những beans:

| Bean | Vai trò | Dependency |
| --- | --- | --- |
| `Timeline` | Lưu trace in-memory | Không |
| `LifecycleProbe` | Theo dõi init/destroy | Timeline |
| `WorkService` | Thực hiện job và publish event | LifecycleProbe, Timeline, ApplicationEventPublisher |
| `TimelineListener` | Nhận framework events và WorkCompleted | Timeline |
| `FailingListener` | Dùng trong negative test | Không |

Trong cấu hình chạy chính, chỉ có `Timeline`, `LifecycleProbe`, `WorkService` và `TimelineListener`. `FailingListener` được đăng ký trong context test riêng khi cần mô phỏng lỗi listener.

Graph constructor:

~~~mermaid
flowchart TD
  Service[WorkService] --> Probe[LifecycleProbe]
  Service --> Trace[Timeline]
  Service --> Pub[ApplicationEventPublisher]
  Probe --> Trace
  Listener[TimelineListener] --> Trace
~~~

**Quan trọng:** Không có dependency `WorkService → TimelineListener`. Runtime event dispatch là một mũi tên nghiệp vụ khác, không phải cạnh constructor injection.

Nếu listener lại inject WorkService chỉ để đọc ID job, việc đó làm coupling tăng và có nguy cơ feedback loop. Trong demo, payload `WorkCompleted` mang đủ thông tin listener cần.

## 5. Timeline và thứ tự mong đợi

~~~text
new AnnotationConfigApplicationContext(...)
       |
       v
Spring tạo non-lazy singleton, gọi init callbacks
       +--> bean.init
       |
       v
refresh() hoàn tất, phát ContextRefreshedEvent
       +--> context.refreshed
       |
       v
context.start()
       +--> context.started
       |
       v
workService.execute("J-7")
       +--> work.before:J-7
       +--> publishEvent(WorkCompleted)
       |       +--> listener.work:J-7
       +--> work.after:J-7
       |
       v
context.stop()
       +--> context.stopped
       |
       v
context.close()
       +--> context.closed
       +--> bean.destroy
~~~

Timeline này phản ánh **cấu hình cụ thể của demo** (một listener, synchronous multicaster, explicit `start/stop`). Không dùng thứ tự của demo để khẳng định mọi ứng dụng Spring đều tạo tất cả unrelated beans theo một thứ tự toàn cục duy nhất.

## 6. ApplicationEventPublisher làm gì?

`ApplicationEventPublisher` là hợp đồng của Spring dùng để phát thông báo tới các listener phù hợp trong context:

~~~java
publisher.publishEvent(new WorkCompleted("J-7"));
~~~

Từ Spring Framework 4.2, đối tượng event **không bắt buộc** extends `ApplicationEvent`. Khi truyền một POJO/record vào overload `publishEvent(Object)`, Spring có thể bọc nó bằng `PayloadApplicationEvent` để đi qua event infrastructure.

### Event payload

~~~java
public record WorkCompleted(String jobId) {
}
~~~

Immutable record là lựa chọn đơn giản cho dữ liệu đã xảy ra. Chỉ chứa giá trị cần dùng và tránh buộc listener phải inject ngược WorkService.

### API không hứa async/durability

Bản thân hợp đồng `publishEvent` chỉ là hand-off đến multicaster. Nó **không bắt buộc phải đồng bộ trong mọi cấu hình**, không tự là message broker, không bảo đảm exactly-once và không tự lưu event để phát lại.

Trong default `SimpleApplicationEventMulticaster` không có executor riêng, listeners thường được gọi **đồng bộ trong thread của publisher**. Demo dựa vào mặc định này và ghi rõ điều kiện.

## 7. @EventListener đăng ký consumer

Listener:

~~~java
public final class TimelineListener {
    private final Timeline timeline;

    public TimelineListener(Timeline timeline) {
        this.timeline = timeline;
    }

    @EventListener
    public void onWorkCompleted(WorkCompleted event) {
        timeline.add("listener.work:" + event.jobId());
    }
}
~~~

Đây là snippet cần imports `org.springframework.context.event.EventListener` và definitions `Timeline`/`WorkCompleted`.

Spring xử lý `@EventListener` trên một **bean đã được đăng ký**, không phải trên mọi Java object nằm trong classpath. Nếu bạn gọi `new TimelineListener(...)` và không đăng ký với context, annotated method không tự động trở thành listener.

Trong project, `@Bean TimelineListener timelineListener(Timeline)` tạo bean; `EventListenerMethodProcessor` nhận diện method annotation khi singleton bootstrap hoàn thành.

### Một bean có thể có nhiều listener methods

Ví dụ có thể đặt các methods xử lý `WorkCompleted`, `ContextRefreshedEvent` và `ContextClosedEvent` trong một class. Spring dùng parameter type hoặc classes attribute để xác định loại event mỗi method nhận.

## 8. Khi nào @EventListener được đăng ký?

Một điểm dễ gây sai lầm:

~~~java
@Bean(initMethod = "initialize")
LifecycleProbe lifecycleProbe(...) { ... }
~~~

Các callback khởi tạo bean có thể chạy trong singleton pre-instantiation. Trong khi đó, `EventListenerMethodProcessor` là `SmartInitializingSingleton` và đăng ký annotated listener methods ở cuối giai đoạn pre-instantiation.

Vì thế, **phát application event trong constructor hoặc init callback có thể quá sớm** để listener annotation tương ứng đã được đăng ký.

Đừng dựa vào `@EventListener` chắc chắn bắt được một event do chính bean khác publish trong `@PostConstruct` trong mọi cấu hình.

### Nếu cần thực hiện sau khi context sẵn sàng

- Chờ `ContextRefreshedEvent` cho tác vụ startup sau refresh khi phù hợp.
- Hoặc sử dụng `SmartInitializingSingleton.afterSingletonsInstantiated()` khi đúng lifecycle semantics.
- Hoặc `SmartLifecycle` cho component có hoạt động start/stop riêng.
- Với web/Boot app, chọn lifecycle hook theo đúng startup/readiness contract.

Ngay cả khi callback khởi tạo publish event được Spring early-event mechanism giữ lại trong một số giai đoạn, thứ tự và listener availability không nên bị suy diễn như một guaranteed business messaging system.

## 9. Những framework events chính

Spring `ApplicationContext` cung cấp một số event tiêu chuẩn:

| Event | Được phát khi | Lưu ý |
| --- | --- | --- |
| `ContextRefreshedEvent` | Context đã initialize/refresh hoàn tất | Beans/postprocessors chuẩn đã sẵn sàng |
| `ContextStartedEvent` | `ConfigurableApplicationContext.start()` | Là start signal, không tự xuất hiện chỉ vì refresh |
| `ContextStoppedEvent` | `ConfigurableApplicationContext.stop()` | Không đồng nghĩa close |
| `ContextClosedEvent` | `close()`/shutdown hook đóng context | Phát trong quá trình đóng, trước khi singleton destruction hoàn tất |

Bảng dùng contract của Spring ApplicationContext. Một số ApplicationContext implementations hỗ trợ refresh lặp lại, nhưng `GenericApplicationContext`/`AnnotationConfigApplicationContext` **không phải container hot-refresh tùy tiện nhiều lần**; tạo context mới để chạy lại kịch bản.

### Event nào xuất hiện khi new AnnotationConfigApplicationContext(AppConfig.class)?

Constructor nhận config classes sẽ khởi tạo và **refresh** context. Vì thế `ContextRefreshedEvent` xuất hiện trong quá trình constructor. Nó **không tự gọi `start()` hay `stop()`** theo ý nghĩa application context start/stop signals.

## 10. Event handler cho context lifecycle

Trong listener:

~~~java
@EventListener
public void onRefreshed(ContextRefreshedEvent event) {
    timeline.add("context.refreshed");
}

@EventListener
public void onStarted(ContextStartedEvent event) {
    timeline.add("context.started");
}

@EventListener
public void onStopped(ContextStoppedEvent event) {
    timeline.add("context.stopped");
}

@EventListener
public void onClosed(ContextClosedEvent event) {
    timeline.add("context.closed");
}
~~~

Đây là snippets cần imports Spring context event classes và instance `timeline`.

### Với context hierarchy

Nếu ứng dụng có parent/child contexts, một event có thể được truyền theo parent hierarchy; listener ở parent có thể thấy sự kiện gốc từ child. Khi cần theo dõi **chỉ đúng context hiện tại**, so sánh `event.getApplicationContext()` với context instance mong muốn hoặc dùng một context identifier được thiết kế rõ.

Trong demo chỉ có một context nên không cần lọc parent/child.

## 11. Bean lifecycle khác ApplicationContext lifecycle

Bean creation thường trải qua:

~~~text
BeanDefinition được xử lý
     |
     v
Instantiate (constructor/factory method)
     |
     v
Populate properties / inject dependencies
     |
     v
Aware callbacks / BeanPostProcessor before initialization
     |
     v
@PostConstruct (nếu được xử lý)
     |
     v
InitializingBean.afterPropertiesSet (nếu implement)
     |
     v
Custom init-method / @Bean(initMethod)
     |
     v
BeanPostProcessor after initialization
     |
     v
Ready for normal use
~~~

Trình tự này là **khái quát**: Spring cho phép custom BeanPostProcessors, scopes, proxies và FactoryBeans làm chi tiết phức tạp hơn.

Trong ví dụ runnable, chỉ dùng custom `initMethod` để giảm imports và tập trung vào timeline.

### Constructor không phải lifecycle callback hoàn chỉnh

Một constructor được gọi khi tạo object, nhưng không bảo đảm mọi dependency/lifecycle/proxy đã hoàn tất. Tránh publish event hoặc gọi business workflow dài trong constructor.

## 12. Khai báo init và destroy bằng @Bean

~~~java
@Bean(initMethod = "initialize", destroyMethod = "destroy")
public LifecycleProbe lifecycleProbe(Timeline timeline) {
    return new LifecycleProbe(timeline);
}
~~~

Bean:

~~~java
public static final class LifecycleProbe {
    private final Timeline timeline;

    public LifecycleProbe(Timeline timeline) {
        this.timeline = timeline;
    }

    public void initialize() {
        timeline.add("bean.init");
    }

    public void destroy() {
        timeline.add("bean.destroy");
    }
}
~~~

Đây là snippets hợp lệ trong context class phù hợp.

Spring gọi `initialize()` khi bean được tạo và dependencies được cấp; `destroy()` được gọi khi container hủy singleton managed bean ở shutdown.

### Chú ý lifecycle ownership

Default singleton được Spring quản lý đầy đủ, bao gồm configured destruction. Với prototype bean, Spring không tự theo dõi và hủy mọi instance khi context close. Vì vậy don't expect `destroyMethod` on prototype to run automatically upon container shutdown.

Nếu resource được tạo ngoài Spring hoặc thread/file handle không thuộc bean lifecycle, bạn phải tự bảo đảm cleanup.

## 13. Tại sao chọn @Bean(initMethod) cho demo?

Ba cách phổ biến:

| Cơ chế | Ưu điểm | Hạn chế |
| --- | --- | --- |
| `@PostConstruct`/`@PreDestroy` | Logic nằm trong class, theo Jakarta annotations | Cần annotation support; coupling nhẹ vào lifecycle |
| `InitializingBean`/`DisposableBean` | Contract Spring rõ | Class phụ thuộc trực tiếp Spring interfaces |
| `@Bean(initMethod/destroyMethod)` | Class có thể là POJO không cần Spring imports | Config phải biết tên methods |

Ví dụ dùng `@Bean` metadata để class `LifecycleProbe` thuần Java. Trong production, chọn cách phù hợp coding conventions và framework setup; không phải cứ khai báo ba kiểu cho cùng callback thì sẽ chạy ba lần.

### Kết hợp nhiều lifecycle mechanisms

Nếu các methods khác tên được đăng ký qua `@PostConstruct`, `InitializingBean`, custom init, Spring thường gọi theo thứ tự đó. Tương tự, destruction callbacks có thứ tự `@PreDestroy`, `DisposableBean.destroy`, custom destroy. Nếu trùng tên method, Spring có cơ chế tránh gọi lặp ở những trường hợp phù hợp.

## 14. Context.start và context.stop không chỉ là events

`ConfigurableApplicationContext.start()` và `stop()` giao cho lifecycle processor quản lý các beans implement `Lifecycle`/`SmartLifecycle` rồi phát context events.

Ví dụ bean implement `Lifecycle`:

~~~java
public final class WorkerLifecycle implements Lifecycle {
    private boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
~~~

Đây là snippet cần import `org.springframework.context.Lifecycle`.

Nếu đăng ký bean đó vào context, calls `start()/stop()` có thể gọi các lifecycle methods theo framework contract. **Demo runnable không đăng ký WorkerLifecycle**; nó chỉ ghi nhận context events để timeline ngắn và xác định.

### SmartLifecycle

`SmartLifecycle` mở rộng lifecycle với auto-startup, phase/ordering và shutdown callback hỗ trợ graceful stop. Auto-start SmartLifecycle có thể chạy trong quá trình `refresh()` khi context lifecycle processor kích hoạt; đừng nhầm đó với explicit `ContextStartedEvent`.

Khi cần quản lý consumer threads, socket listeners hoặc polling jobs thật, cân nhắc SmartLifecycle thay vì chỉ @EventListener(ContextRefreshedEvent).

## 15. Sự khác nhau giữa stop và close

Đây là hai lệnh rất khác:

**`stop()`**

- Gửi stop signal đến managed lifecycle beans.
- Phát `ContextStoppedEvent`.
- Không hủy toàn bộ singleton bean registry.
- Context nhìn chung vẫn tồn tại; có thể start lại theo lifecycle contract.

**`close()`**

- Phát `ContextClosedEvent` trong quá trình shutdown.
- Dừng lifecycle beans phù hợp và destroy managed singletons.
- Context không còn hoạt động như context phục vụ lookup thông thường.
- Không thể restart container đã close chỉ bằng `start()`.

Một `ContextStoppedEvent` không có nghĩa mọi singleton custom destroy callback đã chạy. Demo cố tình in timeline sau stop nhưng trước close để cho thấy sự khác biệt.

## 16. Synchronous event propagation và exception semantics

Trong demo, `publishEvent(new WorkCompleted(id))` gọi một matching listener ngay trong publisher thread. Trình tự:

~~~text
service: add work.before
    |
    v
publishEvent
    |
    v
listener: add listener.work
    |
    v
publishEvent returns
    |
    v
service: add work.after
~~~

Nếu listener ném unchecked exception và multicaster không có error handler để chuyển đổi/xử lý, exception thường propagate về caller của `publishEvent`. Khi đó service có thể **không** chạy đoạn sau publish.

### Negative test minh họa

~~~java
@EventListener
public void failOnWork(WorkCompleted event) {
    throw new IllegalStateException(
        "Listener refused: " + event.jobId());
}
~~~

Nếu đây là listener phù hợp và không cấu hình error handler/async executor, `WorkService.execute` có thể ném exception ngay tại `publishEvent`.

**Không nên phụ thuộc vào sự kiện cho một bước bắt buộc cần kết quả typed ngay**, ví dụ kiểm tra payment authorization. Dùng method call/port rõ semantics khi cần command result.

## 17. Sự kiện nội bộ không phải hàng đợi tin nhắn

Spring application events có ưu điểm:

- Không cần producer inject concrete consumers.
- Decoupling hữu ích cho audit, metrics, notifications trong một process.
- Event payload đơn giản, type-safe.
- Không cần broker trong demo/lớp học.

Nhưng không có bảo đảm mặc định:

- Durable event storage.
- Delivery sau JVM restart.
- Cross-process broadcast.
- Retry/backoff tự động.
- Exactly-once.
- Persistence/transaction commit.
- Global event ordering khi thêm executors.

Một event được phát nhưng **không có listener đăng ký** thường chỉ không có consumer xử lý; `publishEvent` không tự giữ nó để giao lại khi listener được thêm sau.

Đừng gọi `ApplicationEventPublisher` là Kafka/RabbitMQ thu nhỏ.

## 18. Transaction và thời điểm nhận event

Giả sử ứng dụng thật có database transaction:

~~~text
BEGIN
  save order
  publish OrderPlaced
  @EventListener gửi email
ROLLBACK
~~~

Nếu listener đồng bộ gửi email ngay, email đã được gửi dù database cuối cùng rollback. Event publication không tự làm thay đổi commit semantics.

Spring có:

~~~java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT)
public void afterCommit(OrderPlaced event) {
    // ...
}
~~~

Đây là snippet minh họa, cần transaction manager và imports `TransactionalEventListener`, `TransactionPhase`, `OrderPlaced`.

### Không có transaction thì sao?

`@TransactionalEventListener` **mặc định không xử lý event nếu không có transaction đang active**, trừ khi bật `fallbackExecution = true`. Vì project runnable không có transaction manager, nó dùng `@EventListener` thông thường.

### Sau commit vẫn có thể mất tin

AFTER_COMMIT giúp căn thời điểm side effect theo DB transaction; không đảm bảo durable delivery qua process crash. Nếu sự kiện phải gửi đáng tin qua external system, cân nhắc **transactional outbox**, broker, idempotent consumer và dead-letter/retry policy.

## 19. @Async và custom multicaster sẽ thay timeline

Nếu listener được xử lý async, thứ tự có thể thành:

~~~text
work.before:J-7
work.after:J-7
listener.work:J-7
~~~

hoặc listener có thể hoàn thành sớm hơn trong tình huống thực. Không nên assert fixed order giữa publisher và async listeners trừ khi test có synchronization rõ.

### @Async

Có thể dùng `@Async` trên listener cùng `@EnableAsync`/executor configuration phù hợp. Với async listener, exception không luôn truyền ngược về publisher như synchronous flow; cần `AsyncUncaughtExceptionHandler`, futures hoặc cơ chế quan sát lỗi theo API.

### ApplicationEventMulticaster

Nếu đăng ký bean tên `applicationEventMulticaster` với executor/error handler, bạn thay dispatch semantics. Đừng dùng expected stdout của demo mặc định cho một multicaster đã được cấu hình khác.

### Context events và thread safety

Dù event dispatch async, bean init/destroy vẫn có lifecycle riêng. Timeline dùng collection thread-safe hoặc synchronized access; với async, cần test chờ đến event completion để không có race/flaky assertions.

## 20. @Order chỉ ảnh hưởng thứ tự listeners, không phải mọi callback

Spring `@Order` có thể điều khiển thứ tự xử lý khi nhiều matching listeners được hỗ trợ trong cùng synchronous multicaster.

~~~java
@Order(10)
@EventListener
public void first(WorkCompleted event) { ... }
~~~

Snippet cần `@Order` import và method body.

**Không** dùng `@Order` để giả định BeanFactory tạo unrelated singleton beans theo cùng thứ tự. Bean creation order dựa vào dependencies, definitions và các mechanisms riêng.

Khi async, @Order có thể xác định submission/invocation ordering ở một lớp, nhưng **không bảo đảm completion order trên các threads**.

Trong runnable demo chỉ có một listener cho WorkCompleted, nên thứ tự bắt/ghi rõ ràng mà không cần @Order.

## 21. @EventListener có thể lọc hoặc tái phát sự kiện

Spring cho phép `@EventListener(condition = "...")` với SpEL phù hợp:

~~~java
@EventListener(condition = "#event.jobId() == 'J-7'")
public void onlySelected(WorkCompleted event) { ... }
~~~

Đây là snippet minh họa; cách đặt tên parameter trong SpEL phụ thuộc compiler debug/parameter metadata và Spring expression context. Trong môi trường thiếu parameter-name metadata, tham chiếu argument bằng chỉ số/index alias mà API hỗ trợ sẽ ổn định hơn. Demo tránh SpEL filter để dễ chạy lại.

### Method có non-void return

Một annotated listener method có thể trả về object và framework có thể phát object đó như một event tiếp theo theo contract, tùy chế độ dispatch. Nếu trả về chính event type được handle mà không có điều kiện dừng, có nguy cơ vòng sự kiện.

Vì vậy demo dùng `void` listeners và trace một chiều.

## 22. Phân biệt ApplicationListener với @EventListener

Có hai kiểu registration:

**Interface-based:**

~~~java
public class WorkListener
        implements ApplicationListener<WorkCompletedEvent> {

    @Override
    public void onApplicationEvent(WorkCompletedEvent event) {
        // ...
    }
}
~~~

Snippet cần `WorkCompletedEvent extends ApplicationEvent` và import `ApplicationListener`.

**Annotation-based:**

~~~java
@EventListener
public void onWork(WorkCompleted event) {
    // ...
}
~~~

POJO payload không cần extend `ApplicationEvent`. Trong demo, annotation-based listener thuận tiện vì một class xử lý nhiều framework event types.

Khi phải đăng ký listener từ rất sớm, cần hiểu rõ khác biệt: một `ApplicationListener` bean hoặc statically registered listener có lifecycle registration khác một method được chuyển thành listener trong `EventListenerMethodProcessor`. Đừng mặc định chúng nghe được mọi early event như nhau.

## 23. Có nên phát event trong @PostConstruct?

Thường **không nên** cho các business notifications quan trọng. Tại thời điểm `@PostConstruct`:

- Bean còn trong initialization process.
- AOP proxy cuối cùng có thể chưa được áp cho target.
- Annotated event listener methods có thể chưa được đăng ký.
- Events có thể kích hoạt collaborators còn đang tạo.
- Thứ tự xử lý dễ lệ thuộc implementation details của bootstrap.

Nếu cần “bắt đầu xử lý sau khi container sẵn sàng”, cân nhắc `ContextRefreshedEvent`, `SmartInitializingSingleton` hoặc `SmartLifecycle`, và xác định rõ startup failure/retry semantics.

Với nghiệp vụ được trigger từ request/job queue, tốt nhất publish event **sau khi command hoàn tất business milestone** thay vì phát chỉ vì một Spring bean được khởi tạo.

## 24. Một bản trace hữu ích cần những gì?

Một trace entry tối thiểu trong demo là string như `context.refreshed` hoặc `listener.work:J-7`.

Trong ứng dụng production, có thể dùng structured logs:

~~~text
timestamp | contextId | eventType | correlationId |
beanName | phase | thread | duration | outcome
~~~

Ví dụ:

~~~text
2026-10-17T10:00:00+07:00 | ctx-main | WorkCompleted |
J-7 | timelineListener | handled | main | 2ms | success
~~~

Đây là **dòng minh họa định dạng**, không phải output thật từ demo. Đừng tự tạo “timestamp chính xác” trong expected stdout khi ứng dụng lấy `Instant.now()`.

### Cảnh báo bảo mật

Không log event payload chứa passwords, tokens, medical details hay dữ liệu cá nhân nhạy cảm. Nên chọn correlation ID, business key và metadata vừa đủ chẩn đoán; xử lý log retention/access phù hợp.

## 25. Project Java 17 + Spring Context 6.2.19 + JUnit Jupiter 5

Ví dụ runnable có hai configuration classes:

- `CoreConfig` tạo Timeline, LifecycleProbe và WorkService.
- `ListenerConfig` đăng ký TimelineListener.

Một cấu hình riêng:

- `FailingListenerConfig` đăng ký listener cố tình ném lỗi để test sự lan truyền của exception trong default synchronous dispatch.

Các beans được tạo bằng constructor hoặc `@Bean` method parameters. Spring tự cung cấp `ApplicationEventPublisher` như một infrastructure dependency phù hợp.

### File tree

~~~text
spring-event-lifecycle/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/example/lifecycle/
    │           └── LifecycleEventDemo.java
    └── test/
        └── java/
            └── com/example/lifecycle/
                └── LifecycleEventDemoTest.java
~~~

Chỉ một top-level public class trong mỗi file phù hợp tên file. Các component classes/configurations là nested static types để người học dễ copy/run và tránh phải tạo nhiều source files.

## 26. File pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-event-lifecycle</artifactId>
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
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.1</version>
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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.lifecycle.LifecycleEventDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Không cần `spring-test` hoặc Spring Boot chỉ để tạo ApplicationContext thật trong JUnit test; Spring Context dependency đã đủ cho manual context lifecycle.

## 27. File LifecycleEventDemo.java — mã nguồn đầy đủ

Đường dẫn `src/main/java/com/example/lifecycle/LifecycleEventDemo.java`:

~~~java
package com.example.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;

public class LifecycleEventDemo {

    public static void main(String[] args) {
        Timeline saved;

        try (AnnotationConfigApplicationContext context =
                 openContext()) {

            saved = context.getBean(Timeline.class);

            System.out.println("after.refresh=" +
                saved.snapshot());

            context.start();
            System.out.println("after.start=" +
                saved.snapshot());

            String result = context.getBean(WorkService.class)
                .execute("J-7");

            System.out.println("result=" + result);
            System.out.println("after.work=" +
                saved.snapshot());

            context.stop();
            System.out.println("after.stop=" +
                saved.snapshot());
        }

        // Timeline được giữ lại trong biến Java local
        // để xem hai dấu mốc đã ghi khi close().
        System.out.println("after.close=" +
            saved.snapshot());
    }

    public static AnnotationConfigApplicationContext openContext() {
        return new AnnotationConfigApplicationContext(
            CoreConfig.class,
            ListenerConfig.class);
    }

    public static final class Timeline {
        private final List<String> records = new ArrayList<>();

        public synchronized void add(String text) {
            records.add(text);
        }

        public synchronized List<String> snapshot() {
            return List.copyOf(records);
        }
    }

    public record WorkCompleted(String jobId) {
    }

    public static final class LifecycleProbe {
        private final Timeline timeline;

        public LifecycleProbe(Timeline timeline) {
            this.timeline = timeline;
        }

        public void initialize() {
            timeline.add("bean.init");
        }

        public void destroy() {
            timeline.add("bean.destroy");
        }
    }

    public static final class WorkService {
        private final LifecycleProbe probe;
        private final Timeline timeline;
        private final ApplicationEventPublisher publisher;

        public WorkService(
                LifecycleProbe probe,
                Timeline timeline,
                ApplicationEventPublisher publisher) {
            this.probe = probe;
            this.timeline = timeline;
            this.publisher = publisher;
        }

        public String execute(String jobId) {
            if (jobId == null || jobId.isBlank()) {
                throw new IllegalArgumentException(
                    "jobId must not be blank");
            }

            timeline.add("work.before:" + jobId);
            publisher.publishEvent(new WorkCompleted(jobId));
            timeline.add("work.after:" + jobId);

            return "DONE:" + jobId;
        }
    }

    public static final class TimelineListener {
        private final Timeline timeline;

        public TimelineListener(Timeline timeline) {
            this.timeline = timeline;
        }

        @EventListener
        public void onRefreshed(ContextRefreshedEvent event) {
            timeline.add("context.refreshed");
        }

        @EventListener
        public void onStarted(ContextStartedEvent event) {
            timeline.add("context.started");
        }

        @EventListener
        public void onStopped(ContextStoppedEvent event) {
            timeline.add("context.stopped");
        }

        @EventListener
        public void onClosed(ContextClosedEvent event) {
            timeline.add("context.closed");
        }

        @EventListener
        public void onWorkCompleted(WorkCompleted event) {
            timeline.add(
                "listener.work:" + event.jobId());
        }
    }

    public static final class FailingListener {

        @EventListener
        public void onWorkCompleted(WorkCompleted event) {
            throw new IllegalStateException(
                "Listener refused: " + event.jobId());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CoreConfig {

        @Bean
        public Timeline timeline() {
            return new Timeline();
        }

        @Bean(initMethod = "initialize",
              destroyMethod = "destroy")
        public LifecycleProbe lifecycleProbe(Timeline timeline) {
            return new LifecycleProbe(timeline);
        }

        @Bean
        public WorkService workService(
                LifecycleProbe probe,
                Timeline timeline,
                ApplicationEventPublisher publisher) {
            return new WorkService(
                probe, timeline, publisher);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ListenerConfig {

        @Bean
        public TimelineListener timelineListener(
                Timeline timeline) {
            return new TimelineListener(timeline);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FailingListenerConfig {

        @Bean
        public FailingListener failingListener() {
            return new FailingListener();
        }
    }
}
~~~

### Mã trên kiểm tra được những điều gì?

- `CoreConfig` dùng `@Bean` method parameters, không gọi factory methods trực tiếp.
- `LifecycleProbe` nhận Timeline và được Spring gọi `initialize/destroy` qua metadata.
- `WorkService` inject framework `ApplicationEventPublisher` mà không inject listener.
- `WorkCompleted` là Java record, không extends `ApplicationEvent`.
- `TimelineListener` có đúng năm typed handlers, một business event và bốn lifecycle events.
- `Timeline` dùng `synchronized` và snapshot immutable, nên đọc/ghi list không tự gây data race trong demo.
- `FailingListenerConfig` chỉ dành cho negative test, không load cùng ListenerConfig khi chạy main.
- Không có transaction manager hoặc executor riêng.
- `try-with-resources` gọi `context.close()` kể cả khi block thoát bằng exception.

### Vì sao constructor WorkService nhận LifecycleProbe dù không trực tiếp gọi probe?

Đây là một **explicit lifecycle prerequisite** được đưa vào demo: WorkService không cần bắt đầu xử lý trước khi probe được tạo/initialized. Nó cũng làm cạnh dependency trong bean graph rõ.

Trong production, chỉ inject collaborator nếu thật sự có contract/chức năng cần thiết. Nếu chỉ cần kiểm soát startup ordering, cân nhắc `@DependsOn` hoặc lifecycle API phù hợp; không thêm dummy dependency vào mọi service. Bài dùng nó để học và test trình tự.

## 28. Chạy chương trình và stdout

Yêu cầu JDK 17+ và Maven 3.9.x. Từ thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng chính do `System.out.println` in ra:

~~~text
after.refresh=[bean.init, context.refreshed]
after.start=[bean.init, context.refreshed, context.started]
result=DONE:J-7
after.work=[bean.init, context.refreshed, context.started, work.before:J-7, listener.work:J-7, work.after:J-7]
after.stop=[bean.init, context.refreshed, context.started, work.before:J-7, listener.work:J-7, work.after:J-7, context.stopped]
after.close=[bean.init, context.refreshed, context.started, work.before:J-7, listener.work:J-7, work.after:J-7, context.stopped, context.closed, bean.destroy]
~~~

Maven và Spring logging có thể thêm dòng riêng; bảng trên mô tả stdout **từ chương trình**. Với project không có custom async multicaster và không có parent context, timeline xác định như trên.

### Đọc từ trái sang phải

- `bean.init` đứng trước `context.refreshed` vì non-lazy singleton probe được tạo trong refresh trước khi context thông báo sẵn sàng.
- `context.started` chỉ xuất hiện sau explicit `context.start()`.
- `listener.work:J-7` nằm giữa `work.before` và `work.after` vì default event dispatch đồng bộ.
- `context.stopped` xuất hiện sau explicit `context.stop()`, nhưng **không** có `bean.destroy` ở dòng after.stop.
- `context.closed` xuất hiện trước `bean.destroy` trong quá trình close.
- `bean.destroy` được ghi bởi callback thuộc một managed singleton.
- Không có dấu mốc nào sau `bean.destroy` vì demo không chạy các tasks sau context close.

## 29. JUnit 5: kiểm thử đầy đủ chuỗi sự kiện

Tạo file `src/test/java/com/example/lifecycle/LifecycleEventDemoTest.java`:

~~~java
package com.example.lifecycle;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleEventDemoTest {

    @Test
    void refreshInitializesProbeBeforeContextEvent() {
        try (AnnotationConfigApplicationContext context =
                 LifecycleEventDemo.openContext()) {

            assertEquals(
                List.of("bean.init", "context.refreshed"),
                context.getBean(
                    LifecycleEventDemo.Timeline.class).snapshot());
        }
    }

    @Test
    void startAndStopPublishExplicitContextEvents() {
        try (AnnotationConfigApplicationContext context =
                 LifecycleEventDemo.openContext()) {

            LifecycleEventDemo.Timeline timeline =
                context.getBean(
                    LifecycleEventDemo.Timeline.class);

            context.start();

            assertEquals(
                List.of("bean.init",
                        "context.refreshed",
                        "context.started"),
                timeline.snapshot());

            context.stop();

            assertEquals(
                List.of("bean.init",
                        "context.refreshed",
                        "context.started",
                        "context.stopped"),
                timeline.snapshot());
        }
    }

    @Test
    void synchronousBusinessEventIsHandledBeforeReturn() {
        try (AnnotationConfigApplicationContext context =
                 LifecycleEventDemo.openContext()) {

            String result =
                context.getBean(
                    LifecycleEventDemo.WorkService.class)
                    .execute("JOB-1");

            assertEquals("DONE:JOB-1", result);

            assertEquals(
                List.of("bean.init",
                        "context.refreshed",
                        "work.before:JOB-1",
                        "listener.work:JOB-1",
                        "work.after:JOB-1"),
                context.getBean(
                    LifecycleEventDemo.Timeline.class).snapshot());
        }
    }

    @Test
    void closingContextPublishesEventBeforeDestroy() {
        LifecycleEventDemo.Timeline saved;

        try (AnnotationConfigApplicationContext context =
                 LifecycleEventDemo.openContext()) {

            saved = context.getBean(
                LifecycleEventDemo.Timeline.class);

            assertEquals(
                List.of("bean.init", "context.refreshed"),
                saved.snapshot());
        }

        assertEquals(
            List.of("bean.init",
                    "context.refreshed",
                    "context.closed",
                    "bean.destroy"),
            saved.snapshot());
    }

    @Test
    void contextsDoNotShareTimelineState() {
        try (AnnotationConfigApplicationContext first =
                 LifecycleEventDemo.openContext();
             AnnotationConfigApplicationContext second =
                 LifecycleEventDemo.openContext()) {

            LifecycleEventDemo.Timeline left =
                first.getBean(
                    LifecycleEventDemo.Timeline.class);
            LifecycleEventDemo.Timeline right =
                second.getBean(
                    LifecycleEventDemo.Timeline.class);

            assertNotSame(left, right);

            first.getBean(
                LifecycleEventDemo.WorkService.class)
                .execute("LEFT");

            assertEquals(
                List.of("bean.init",
                        "context.refreshed",
                        "work.before:LEFT",
                        "listener.work:LEFT",
                        "work.after:LEFT"),
                left.snapshot());

            assertEquals(
                List.of("bean.init",
                        "context.refreshed"),
                right.snapshot());
        }
    }

    @Test
    void missingBusinessListenerDoesNotBufferForLater() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     LifecycleEventDemo.CoreConfig.class)) {

            String result =
                context.getBean(
                    LifecycleEventDemo.WorkService.class)
                    .execute("NO-LISTENER");

            assertEquals("DONE:NO-LISTENER", result);

            assertEquals(
                List.of("bean.init",
                        "work.before:NO-LISTENER",
                        "work.after:NO-LISTENER"),
                context.getBean(
                    LifecycleEventDemo.Timeline.class).snapshot());
        }
    }

    @Test
    void failingSynchronousListenerPropagatesException() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     LifecycleEventDemo.CoreConfig.class,
                     LifecycleEventDemo.FailingListenerConfig.class)) {

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> context.getBean(
                    LifecycleEventDemo.WorkService.class)
                    .execute("FAIL"));

            assertEquals(
                "Listener refused: FAIL",
                exception.getMessage());

            assertEquals(
                List.of("bean.init", "work.before:FAIL"),
                context.getBean(
                    LifecycleEventDemo.Timeline.class).snapshot());
        }
    }

    @Test
    void blankJobIdFailsBeforeEventPublication() {
        try (AnnotationConfigApplicationContext context =
                 LifecycleEventDemo.openContext()) {

            assertThrows(
                IllegalArgumentException.class,
                () -> context.getBean(
                    LifecycleEventDemo.WorkService.class)
                    .execute("  "));

            assertEquals(
                List.of("bean.init", "context.refreshed"),
                context.getBean(
                    LifecycleEventDemo.Timeline.class).snapshot());
        }
    }
}
~~~

### Chạy tests

~~~bash
mvn -q test
~~~

Dự kiến **8 JUnit Jupiter tests**, **0 failures**, **0 errors**. `maven-surefire-plugin` tạo báo cáo trong `target/surefire-reports`. Một số negative tests cố tình tạo exceptions, nhưng `assertThrows` xác nhận chúng là expected results, không phải build failures.

### Vì sao có test “không đăng ký listener”?

Nó chứng minh một producer có thể publish event mà **không có consumer**. Spring không tự giữ event trong durable queue để giao lại sau. Timeline chỉ ghi trước/sau publish từ service.

### Vì sao có test listener ném exception?

Nó chứng minh callback đồng bộ có thể ảnh hưởng control flow: `work.after:FAIL` không xuất hiện. Đây là behavior cần được thiết kế và test trong application thực.

## 30. Bảng nối từng test với một contract

| Test | Điều được xác nhận | Sai sót bắt được |
| --- | --- | --- |
| `refreshInitializesProbeBeforeContextEvent` | Bean init trước refreshed event | Hiểu sai startup |
| `startAndStopPublishExplicitContextEvents` | Explicit start/stop signals | Nhầm refresh với start |
| `synchronousBusinessEventIsHandledBeforeReturn` | Listener chạy trong publish call | Đoán nhầm async |
| `closingContextPublishesEventBeforeDestroy` | Closed event trước destroy | Cleanup/ordering |
| `contextsDoNotShareTimelineState` | Mỗi context có timeline singleton riêng | Static/global state |
| `missingBusinessListenerDoesNotBufferForLater` | Event không được replay tự động | Đoán nhầm durable queue |
| `failingSynchronousListenerPropagatesException` | Lỗi listener ảnh hưởng publisher | Nuốt/lẫn exception |
| `blankJobIdFailsBeforeEventPublication` | Validation xảy ra trước side effect | Phát event cho input sai |

Đây là **minimal container integration tests** dù không dùng Spring TestContext framework. Unit tests thuần Mockito không thể một mình xác minh lifecycle events do ApplicationContext phát.

## 31. Thứ tự listener có bảo đảm tuyệt đối không?

Không. Trong demo:

- Có một listener cho mỗi event type.
- Mỗi handler chỉ append một trace entry.
- Multicaster mặc định không async.
- Context không có parent.
- Không có scheduled tasks hay custom BeanPostProcessors.

Vì vậy expected timeline ổn định.

Trong project lớn:

- Có thể có nhiều matching listeners.
- `@Order` có thể ảnh hưởng thứ tự.
- Listeners có thể chạy async.
- Parent contexts có thể nhận events từ children.
- Custom multicaster/error handler có thể đổi behavior.
- Lifecycle callbacks của unrelated beans có thể không có total order như bạn tưởng.

**Chỉ assert thứ tự được contract và test configuration thực sự bảo đảm.** Với async/eventual effects, dùng synchronization, latches hoặc Awaitility tương ứng, đừng chỉ `Thread.sleep` một khoảng tùy ý.

## 32. Event payload thiết kế như thế nào?

Một event tốt mô tả **sự kiện đã xảy ra**:

~~~java
public record OrderPlaced(
    String orderId,
    long totalCents,
    String correlationId) {
}
~~~

Đây là snippet mở rộng, không phải record dùng trong executable demo.

Các nguyên tắc:

- Dùng immutable values khi có thể.
- Tránh truyền EntityManager, Connection, HttpServletRequest hoặc mutable transaction-bound object vào payload.
- Chọn business event name ở thì quá khứ khi là fact: `OrderPlaced`, `PaymentCaptured`, `WorkCompleted`.
- Đưa correlation ID nếu cần tracing.
- Không đưa credentials/PII không cần thiết.
- Quy định version/evolution nếu event trở thành integration event công khai giữa services.

Không dùng event như request-response command trá hình. Nếu caller cần validation result đồng bộ, dùng typed service interface/port.

## 33. Event feedback loop và tái nhập

Một lỗi nguy hiểm:

~~~text
WorkService.execute()
    -> publish WorkCompleted
        -> Listener.onWorkCompleted()
            -> WorkService.execute()
                -> publish WorkCompleted
                    -> ...
~~~

Bean constructor graph vẫn có thể không vòng, nhưng **runtime event flow** có thể recursion vô hạn.

Cách phòng ngừa:

- Không gọi lại command tạo cùng event trong listener nếu không có state transition/termination rule.
- Dùng idempotency keys.
- Phân biệt command events và domain facts.
- Viết tests giới hạn số event/side effects.
- Dùng correlation/causation IDs để nhìn thấy chains.
- Tránh listener publish lại cùng event type vô điều kiện.

Không thể dùng `BeanCurrentlyInCreationException` để phát hiện runtime event feedback loops; đó là loại lỗi khác.

## 34. ContextClosedEvent có nên dùng để làm hết cleanup không?

`ContextClosedEvent` hữu ích để quan sát application context đang đóng hoặc bắt đầu shutdown coordination. Tuy nhiên việc giải phóng resource **thuộc ownership của bean** thường nên nằm trong:

- `@PreDestroy`.
- `DisposableBean.destroy()`.
- `@Bean(destroyMethod = "...")`.
- `AutoCloseable`/inferred close method.
- `SmartLifecycle.stop(...)` nếu là running background component.

Nếu một listener `ContextClosedEvent` cố lookup và khởi tạo thêm nhiều beans trong lúc container đang shutdown, có thể gây lỗi lifecycle. Đừng dùng event như lý do trì hoãn mọi cleanup khỏi bean destroy contract.

Demo dùng `ContextClosedEvent` để **ghi trace**, còn `LifecycleProbe.destroy()` làm callback hủy riêng để thấy sự khác biệt rõ.

## 35. @PostConstruct và @PreDestroy trong Spring 6.x

Spring Framework 6 sử dụng namespace **`jakarta.annotation`** khi cần `@PostConstruct` và `@PreDestroy`. Đừng dùng nhầm `javax.annotation` của các phiên bản Java EE cũ mà không xem project/framework version.

Ví dụ:

~~~java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class DatabaseClient {
    @PostConstruct
    public void open() {
        // initialize connection pool state
    }

    @PreDestroy
    public void shutdown() {
        // release resources
    }
}
~~~

Đây là **snippet minh họa**, không phải database client chạy thật. Cần annotation API/annotation processor infrastructure theo Maven dependencies tương ứng. Spring Context 6.x chuẩn có common annotation processing support khi API có mặt.

Trong runnable demo, custom @Bean init/destroy methods không cần những Jakarta annotations nên tránh phụ thuộc thêm để chạy bài tập.

## 36. BeanPostProcessor và AOP: timeline có thể thay đổi

Spring `BeanPostProcessor` có thể can thiệp trước/sau initialization. Các proxies từ `@Transactional`, `@Async`, caching, custom aspects thường xuất hiện sau một số initialization callbacks.

Do đó:

- Đừng gọi transactional/proxied method từ init callback rồi kỳ vọng advice luôn được áp.
- Self-invocation bằng `this.method()` thường bypass proxy-based AOP.
- Early references trong circular dependencies dễ gặp raw-bean-vs-proxy issues.
- Bean init callback không đồng nghĩa ứng dụng đã ready nhận requests.
- Nếu logic cần after-all-singletons timing, dùng hook được thiết kế cho giai đoạn đó.

### Theo dõi chính xác hơn bằng extension points

`BeanPostProcessor`, `BeanFactoryPostProcessor` và `SmartInitializingSingleton` hữu ích cho framework extensions, nhưng có thứ tự, lifecycle và initialization constraints riêng. Không nên đưa tracing logic phức tạp vào post processors chỉ để quan sát một event đơn giản.

## 37. Context refresh không phải “đăng nhập ứng dụng” hay “start tất cả workers”

`ContextRefreshedEvent` nghĩa là context đạt trạng thái đã refresh; không tự bảo đảm:

- HTTP server đã bind xong mọi endpoint trong mọi framework.
- External database/broker đã healthy.
- Distributed dependencies đang reachable.
- SmartLifecycle tasks đã hoàn tất mọi async startup work.
- Business data đã được load đầy đủ.
- Một job chỉ chạy một lần trong toàn cluster.

Nếu chạy tác vụ startup quan trọng:

1. Chọn lifecycle signal phù hợp.
2. Định nghĩa idempotency.
3. Quy định failure behavior.
4. Tách external readiness khỏi bean initialization.
5. Có metrics/readiness probes khi dùng production orchestration.
6. Test với infrastructure thật ở tầng integration thích hợp.

## 38. @TransactionalEventListener là loại listener khác

Một listener thông thường:

~~~java
@EventListener
void onOrderPlaced(OrderPlaced event) {
    // executes according to configured event dispatch
}
~~~

Một transaction-bound listener:

~~~java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT)
void afterOrderCommit(OrderPlaced event) {
    // called when suitable transaction successfully commits
}
~~~

Hai snippets minh họa cần classes/imports và transaction infrastructure. Không kết luận listener thứ hai chạy sau commit khi **không có active transaction**.

Các phases hợp lệ gồm `BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK` và `AFTER_COMPLETION`. Default là AFTER_COMMIT. Khi không có transaction phù hợp và `fallbackExecution=false` (default), listener không xử lý event.

Để bảo đảm ngoại tác quan trọng không mất sau crash, xây transactional outbox hoặc hạ tầng delivery bền vững; transaction synchronization đơn thuần không phải hệ thống queue.

## 39. Monitoring thực tế nên đo gì?

Các metrics hữu ích:

| Metric | Ý nghĩa |
| --- | --- |
| Count of published events | Có bao nhiêu business facts được publish |
| Count of consumed events | Bao nhiêu listener executions |
| Listener latency | Thời gian xử lý và bottleneck |
| Listener error count | Lỗi listener đồng bộ/async |
| Event backlog | Chỉ có ý nghĩa nếu có queue/executor tương ứng |
| Context refresh duration | Chi phí bootstrap |
| Init/destroy callback duration | Startup/shutdown bottlenecks |
| Lifecycle stop duration | Graceful shutdown delay |
| Correlation chain | Luồng causal events cho mỗi job/order |

Demo chỉ thu thập **trace strings**, không phải metrics thực có timestamp/duration. Một hệ thống observability có thể dùng Micrometer, OpenTelemetry và structured logging. Đừng dùng ví dụ này để tuyên bố độ trễ chính xác hay thread scheduling.

## 40. Các lỗi thường gặp

| Nhầm lẫn | Thực tế |
| --- | --- |
| POJO event phải extends ApplicationEvent | Không, Spring hỗ trợ object payload |
| `publishEvent` luôn async | Không; default context thường synchronous |
| Event luôn được retry khi listener fail | Không có retry mặc định |
| Event không có listener sẽ tự được replay | Không, không phải durable queue |
| `ContextRefreshedEvent` là `ContextStartedEvent` | Hai event khác nhau |
| Constructor context auto gọi `start()` | Constructor refresh; explicit start event khác |
| `stop()` hủy toàn bộ singletons | Không; close/destroy là bước khác |
| `ContextClosedEvent` xảy ra sau mọi destroy callbacks | Nó được phát trong quá trình đóng trước destruction |
| Init callback bảo đảm mọi listener đăng ký | Không nên giả định |
| @PostConstruct là thời điểm thích hợp publish mọi business event | Có thể quá sớm |
| @Order quyết định mọi bean creation order | Không |
| Thêm @Async không cần error policy | Sai; phải xử lý lỗi/ordering |
| @TransactionalEventListener chạy cả khi không transaction | Mặc định không |
| Không có constructor cycle thì không thể event loop | Runtime feedback loop vẫn có thể |
| Close context xong còn gọi getBean bình thường | Context đã inactive |
| Prototype có destroy callback khi context close như singleton | Không mặc định |
| Event nội bộ thay thế broker và outbox | Không, hai mục đích khác nhau |

## 41. Checklist trước khi merge

- [ ] Đã phân biệt bean init/destroy với context lifecycle?
- [ ] Đã phân biệt event nghiệp vụ với ContextRefreshedEvent?
- [ ] Payload là immutable/value-oriented khi có thể?
- [ ] Event name mô tả fact thay vì command giả?
- [ ] Producer chỉ cần ApplicationEventPublisher, không inject listener?
- [ ] Listener đã được đăng ký làm Spring bean?
- [ ] Không publish business event quan trọng quá sớm trong constructor/init?
- [ ] Đã biết multicaster sync hay async?
- [ ] Có error policy khi listener throw?
- [ ] Có test side-effect chỉ thực hiện khi event phù hợp?
- [ ] Có kiểm tra trace order dưới cấu hình được hỗ trợ?
- [ ] `context.start()`/`stop()` được gọi explicit nếu cần started/stopped events?
- [ ] Context được close với try-with-resources?
- [ ] Bean-owned resources có destroy/stop callback rõ?
- [ ] Có test destroy sau ContextClosedEvent?
- [ ] Không dùng context event như thay thế mọi cleanup?
- [ ] Có giải pháp runtime event feedback/idempotency?
- [ ] Có kiểm tra transaction phase khi side effect cần after commit?
- [ ] Có outbox/broker nếu yêu cầu durable event delivery?
- [ ] Nếu có async, tests không dựa vào arbitrary Thread.sleep?
- [ ] Không log secrets/PII trong payload?
- [ ] Listener không giữ mutable request data trong singleton fields?
- [ ] Context hierarchies và event source được lọc nếu cần?
- [ ] Test logic/negative cases độc lập với external systems?
- [ ] Có test trạng thái khi không có business listener?
- [ ] Các câu lệnh Maven/JUnit vẫn dùng đúng source files và versions?

## 42. Bài tập mở rộng

### A. Đăng ký hai listeners

Tạo thêm `MetricsListener` nghe `WorkCompleted` và ghi `metric.work:J-7`. So sánh timeline khi có hai listeners. Nếu muốn thứ tự quyết định trong synchronous dispatch, cân nhắc `@Order` trên methods.

### B. Chuyển listener sang @Async

Thêm `@EnableAsync`, executor và `@Async` cho listener. Viết JUnit test dùng `CountDownLatch` hoặc một synchronization primitive phù hợp để đợi listener hoàn tất; không assert synchronous order từ demo cũ.

### C. Thêm SmartLifecycle bean

Tạo `BackgroundWorker implements SmartLifecycle`, ghi `worker.start` và `worker.stop`. Thử autoStartup và phase ordering, đối chiếu với ContextRefreshedEvent và ContextStartedEvent. Đảm bảo implementation không mở thread không được stop.

### D. Thêm negative event loop test

Cố ý tạo listener publish lại WorkCompleted; kiểm thử số events với termination condition thay vì thật sự tạo recursion vô hạn. Sau đó sửa thành listener chỉ ghi metric/audit.

### E. Context hierarchy

Tạo parent/child contexts và listener ở parent. Ghi nhận nguồn `event.getApplicationContext()` để tránh ghi duplicate lifecycle events nếu parent chỉ muốn xử lý sự kiện của chính mình.

### F. Transaction-bound listener

Chỉ trong project có transaction manager thật, tạo entity update + `@TransactionalEventListener(AFTER_COMMIT)`. Test commit và rollback. Không dùng bare demo để giả lập ACID.

### G. Lifecycle cleanup failure

Cho destroy method ném exception có chủ đích trong isolated test context, quan sát logging và policy xử lý khi shutdown. Đừng dùng production resources hoặc khiến test suite treo.

### H. Tracing correlation ID

Thêm `correlationId` vào WorkCompleted record và propagate vào structured logs; bảo đảm event payload vẫn immutable và không chứa secrets.

## 43. Câu hỏi ôn tập và đáp án

**1. Spring nhận object event không extends ApplicationEvent không?** Có. `publishEvent(Object)` hỗ trợ POJO payload.

**2. @EventListener có hoạt động trên một class không được Spring đăng ký bean không?** Không tự động. Phải được phát hiện/đăng ký theo event infrastructure.

**3. Vì sao không publish business event từ constructor?** Bean và listener infrastructure có thể chưa sẵn sàng, thứ tự initialization dễ phụ thuộc chi tiết nội bộ.

**4. `new AnnotationConfigApplicationContext(AppConfig.class)` kích hoạt event nào?** Nó thực hiện refresh, dẫn đến ContextRefreshedEvent khi context sẵn sàng.

**5. Khi nào xuất hiện ContextStartedEvent?** Khi gọi `context.start()`, không tự đồng nghĩa refresh.

**6. Khi nào xuất hiện ContextStoppedEvent?** Khi gọi `context.stop()`.

**7. ContextClosedEvent đứng trước hay sau singleton destruction trong demo?** Trước callback `bean.destroy`.

**8. @Bean(initMethod) và @Bean(destroyMethod) có cần bean implement Spring interface không?** Không, có thể dùng POJO methods.

**9. Spring mặc định luôn xử lý event async?** Không; default multicaster thường synchronous khi không cấu hình executor.

**10. Listener throw khi sync publish có thể ảnh hưởng service?** Có; exception thường propagate nếu không có handler policy khác.

**11. Event không có listener có được giữ chờ listener mới không?** Không; core application events không là durable queue.

**12. @TransactionalEventListener hoạt động nếu không có transaction không?** Mặc định không, trừ fallbackExecution được bật.

**13. @Async giúp listener error tự truyền lại về publisher không?** Không mặc định; cần thiết kế async exception reporting riêng.

**14. Bean singleton có phải singleton toàn JVM không?** Không, scope singleton được quản lý trong BeanFactory/context.

**15. Context stop và close có tương đương nhau không?** Không, stop là lifecycle signal, close hủy context và managed singletons.

**16. Vì sao thử context isolation bằng hai ApplicationContexts?** Để xác nhận trace/singleton state không rò qua test scenarios.

**17. Làm sao tránh event feedback loop?** Định nghĩa transitions, idempotency, không gọi lại command phát cùng fact vô điều kiện.

**18. Muốn gửi event đáng tin sau database commit thì sao?** Dùng transaction-bound timing và outbox/broker nếu yêu cầu durable delivery.

## 44. Tổng kết

Bài thực hành đã kết nối **three independent concepts** trong cùng một timeline có thể kiểm chứng:

- **Bean lifecycle:** `bean.init` khi Spring tạo/initialize singleton và `bean.destroy` khi context đóng.
- **Context lifecycle:** `context.refreshed`, `context.started`, `context.stopped`, `context.closed` tại các signal tương ứng.
- **Internal business events:** `WorkService` publish immutable `WorkCompleted`; `@EventListener` xử lý nó mà producer không inject concrete consumer.

Trong project mặc định, timeline ghi `listener.work:J-7` **giữa** `work.before` và `work.after` vì event dispatch đồng bộ. `stop()` khác `close()`; close phát context event trước khi bean destruction callback ghi trace.

Thiết kế này cho phép bạn quan sát và kiểm thử Spring thật mà không nhầm framework lifecycle với transaction commit, async messaging hoặc bean creation order của cả hệ thống. Khi mở rộng, bảo vệ event payload, xử lý lỗi, tránh feedback loops, và chọn transaction/outbox khi nghiệp vụ yêu cầu.

## 45. Tài liệu chính thức

- [Spring Framework Reference — Additional Capabilities of the ApplicationContext](https://docs.spring.io/spring-framework/reference/6.1/core/beans/context-introduction.html): built-in events, custom events, default synchronous listeners.
- [Spring 6.1 — ApplicationEventPublisher Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/ApplicationEventPublisher.html): `publishEvent` contract cho ApplicationEvent/object.
- [Spring 6.1 — EventListener Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/EventListener.html): annotated event methods.
- [Spring 6.1 — EventListenerMethodProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/EventListenerMethodProcessor.html): đăng ký annotated listener methods sau singleton pre-instantiation.
- [Spring 6.1 — ContextRefreshedEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/ContextRefreshedEvent.html): event sau context refresh.
- [Spring 6.1 — ContextStartedEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/ContextStartedEvent.html): explicit context start.
- [Spring 6.1 — ContextStoppedEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/ContextStoppedEvent.html): explicit context stop.
- [Spring 6.1 — ContextClosedEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/ContextClosedEvent.html): context close event.
- [Spring Framework Reference — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.1/core/beans/factory-nature.html): initialization/destruction callbacks, Lifecycle/SmartLifecycle.
- [Spring Framework Reference — Using @Bean](https://docs.spring.io/spring-framework/reference/6.1/core/beans/java/bean-annotation.html): initMethod/destroyMethod lifecycle metadata.
- [Spring Framework Reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.1/data-access/transaction/event.html): transaction phases và fallbackExecution.
- [Spring 6.1 — SimpleApplicationEventMulticaster Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html): synchronous default/executor/error handling.
- [JUnit 5 User Guide](https://docs.junit.org/5.11.1/user-guide/): JUnit Jupiter annotations, assertions và tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-007">Viết post-processor nhỏ với giới hạn tác động rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Viết post-processor nhỏ với giới hạn tác động rõ ràng trong Spring Core Container

## 1. Yêu cầu thực hành và tiêu chí hoàn thành

Một **post-processor** của Spring là một extension point cho phép can thiệp tại một giai đoạn do container quản lý. Nó rất hữu ích khi bạn phải áp dụng một quy tắc chung cho một tập bean mà không sửa từng lớp nghiệp vụ. Nhưng chính vì được Spring gọi trong lifecycle, một post-processor viết quá rộng có thể tác động vào hạ tầng không liên quan, làm thay đổi object identity, khiến khởi tạo bị đệ quy, hoặc phá AOP proxy.

Trong bài thực hành này, ta viết một **`BeanPostProcessor` (BPP) rất nhỏ** để theo dõi lifecycle của **duy nhất một bean có tên `observedService` và đúng kiểu `EchoService`**, trong khi một bean thứ hai `ignoredService` cùng kiểu phải **hoàn toàn không bị BPP ghi nhận**. Processor chỉ ghi event `before` và `after`, **không wrap, không sửa properties, không thêm bean, không truy xuất bean khác và trả lại đúng object input**.

Mục tiêu có thể kiểm chứng:

1. Biết vì sao chọn `BeanPostProcessor` thay vì `BeanFactoryPostProcessor`/ `BeanDefinitionRegistryPostProcessor`.
2. Viết bộ lọc **allowlist bean name + kiểm tra type**, không áp dụng bừa trên mọi bean.
3. Đăng ký processor bằng `static @Bean` để giảm nguy cơ early initialization khi Spring chuẩn bị các BPPs.
4. Xác nhận `postProcessBeforeInitialization` chạy trước custom init, `postProcessAfterInitialization` chạy sau custom init của bean đích.
5. Xác nhận một bean khác **cùng class** nhưng **khác name** không bị processor tác động.
6. Xác nhận **object không bị thay bằng proxy/wrapper** trong demo, callback count chính xác và destroy vẫn thuộc lifecycle riêng.
7. Phân biệt kết quả hợp lệ với những assumptions không được Spring bảo đảm, đặc biệt relative order của nhiều BPPs và behavior của AOP/custom FactoryBean.
8. Hoàn thành ví dụ **Java 17 + Spring Framework 6.2.19 + Maven** với stdout và các kiểm tra fail-fast.

> **Giới hạn thiết kế chính:** Processor có nhiệm vụ quan sát hai mốc initialization của một bean được phép. Nó **không** là một general-purpose logging framework, không thay thế AOP/interceptor, không thay logic service, và không tự quản lý destroy.

## 2. Một post-processor có thể làm gì và ở giai đoạn nào?

Trong Spring Framework Core Container có nhiều loại extension points cùng chứa chữ “post-processor”, nhưng thời điểm và loại dữ liệu mà chúng xử lý rất khác nhau.

| Extension point | Khi nào được Spring gọi? | Đối tượng tác động | Nên dùng khi |
|---|---|---|---|
| `BeanDefinitionRegistryPostProcessor` | Giai đoạn rất sớm, trước bean factory post-processing thông thường | BeanDefinitionRegistry / definitions | Cần thêm/xóa/sửa bean definitions trước khi factory xử lý |
| `BeanFactoryPostProcessor` | Sau khi definitions được load, trước khi instantiate ordinary application beans | Metadata trong BeanFactory | Cần chỉnh property/metadata của bean definitions |
| `BeanPostProcessor` | Trong creation/init của từng bean instance | Object bean đã được instantiate | Validate, quan sát, hoặc wrap một số bean instances |
| `InstantiationAwareBeanPostProcessor` | Các điểm sâu hơn quanh instantiate/populate properties | Instance/metadata/phương thức injection | Cần customize instantiation/property population thực sự |
| `SmartInstantiationAwareBeanPostProcessor` | Type prediction, constructor discovery, early references | Constructors/types/early bean ref | Framework/proxy infrastructure nâng cao |
| `DestructionAwareBeanPostProcessor` | Trước destruction của bean phù hợp | Bean instance đang được destroy | Cần callback hủy xuyên suốt một số types |
| `BeanRegistrationAotProcessor` | Spring AOT build time | Registration code/runtime hints | Library/integration phải sinh registration phù hợp với AOT |

Bài này cần ghi event trên **bean instance**, vậy dùng **`BeanPostProcessor`**. Nếu yêu cầu là đặt `lazyInit=true` cho definition của một bean, thay vì chờ object được tạo mới can thiệp, đó là bài toán `BeanFactoryPostProcessor`.

### 2.1. BeanPostProcessor không sửa metadata như BeanFactoryPostProcessor

Một BPP nhận:

```java
Object postProcessBeforeInitialization(Object bean, String beanName);
Object postProcessAfterInitialization(Object bean, String beanName);
```

Trong khi BFPP nhận:

```java
void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory);
```

BPP thấy **instance đang được tạo**, BFPP thấy **factory definitions**. Khi bạn cần lọc một bean theo tên và quan sát lúc init, BPP đúng thời điểm và không cần can thiệp BeanDefinitionRegistry.

**Không dùng `beanFactory.getBean("target")` từ một BFPP để “lấy bean ra sửa”**: thao tác này có thể buộc instantiate bean quá sớm, trước khi tất cả BPPs được đăng ký, gây behavior không nhất quán hoặc mất proxying.

### 2.2. BeanPostProcessor không phải BeanFactoryPostProcessor với tên khác

Sự khác nhau còn thể hiện qua số lần chạy:

- BFPP thường xử lý factory trong refresh một lần cho mỗi context/factory.
- BPP có thể được gọi **trên nhiều bean instances** ở từng lần creation, kể cả prototypes.
- BPP methods có thể được gọi trên `FactoryBean` và sản phẩm do factory trả về theo API contract.
- BPP chịu ảnh hưởng lifecycle và scope, nên không được giả định “mỗi bean name luôn có đúng một callback” trong mọi hệ thống.

Với ví dụ có hai singleton `EchoService` và một context mới, ta có thể assert mỗi event của **bean đích** đúng một lần. Đó là contract của **scenario đã giới hạn**, không phải lời hứa cho mọi custom scope.

## 3. Hiểu chính xác hai callbacks của BeanPostProcessor

Spring 6.2 Javadoc định nghĩa:

```java
default Object postProcessBeforeInitialization(
        Object bean, String beanName) {
    return bean;
}

default Object postProcessAfterInitialization(
        Object bean, String beanName) {
    return bean;
}
```

Đây là mô hình giản lược signatures, không cần tự khai báo cả hai nếu chỉ muốn override một callback vì interface đã có default implementation.

### 3.1. Before initialization

`postProcessBeforeInitialization(bean, name)` được gọi sau khi bean đã được instantiate và property values thông thường được population, **trước** `InitializingBean.afterPropertiesSet()` và custom init method.

Tuy nhiên `@PostConstruct` cũng được xử lý bởi một BPP (ví dụ `CommonAnnotationBeanPostProcessor`/`InitDestroyAnnotationBeanPostProcessor`). Vì vậy, **không mặc định thứ tự giữa custom BPP-before và @PostConstruct** nếu không kiểm soát registration/ordering của processors.

### 3.2. After initialization

`postProcessAfterInitialization(bean, name)` được gọi sau các initialization callbacks như `afterPropertiesSet()` và custom init method. Đây là giai đoạn mà nhiều proxy creators có thể wrap một object thành proxy.

Trong demo, method trả:

```java
return bean;
```

cho cả bean được quan sát và các beans khác. Nhờ đó, **riêng processor của chúng ta** không làm thay identity, không thêm interceptor và không sửa object state.

### 3.3. Không trả null như cách “bỏ qua” một bean

Javadoc `BeanPostProcessor` cho biết nếu một processor trả `null`, **các BPPs tiếp theo không còn được gọi** trong chuỗi processing tương ứng. Đây không phải phương pháp lọc bean.

**Cách đúng để không xử lý:** khi predicate không match thì **return nguyên object**. Chỉ dùng `null` nếu thực sự có lý do framework-level và hiểu đầy đủ consequences; thông thường user-written BPP **không nên** trả null.

### 3.4. Không tự gọi method đời sống của bean

BPP-before không có nghĩa bạn nên gọi `bean.init()` hoặc `@PostConstruct` thủ công. Spring đã có giai đoạn init; gọi lại có thể làm double initialization. BPP-after cũng không phải nơi tự gọi `@PreDestroy`.

Nếu cần lifecycle của một bean cụ thể, cân nhắc **bean-local `@PostConstruct`/`@PreDestroy`** trước khi dùng BPP global. Dùng BPP khi feature thuộc cross-cutting concern được scope rất rõ.

## 4. Đặt giới hạn tác động trước khi viết processor

Một processor “nhỏ” không chỉ là ít dòng code. Nó phải có một **hợp đồng tác động** được kiểm tra.

Trong bài này:

```text
Allowed bean name    = observedService
Allowed runtime type = EchoService
Allowed lifecycle    = BPP-before + BPP-after
Allowed side effect  = Trace.add(event)
Returned object      = exactly the input object
Forbidden            = mutate fields, create proxy, call getBean,
                       touch ignoredService, query secrets/network
```

Chính vì hai beans **cùng class**, predicate chỉ dựa vào `instanceof EchoService` là **quá rộng**. Chỉ so bean name cũng chưa đủ nếu ai đó thay class đăng ký dưới cùng name bằng một bean khác. Kết hợp hai điều kiện giúp giới hạn rõ ràng:

```java
private static boolean accepts(Object bean, String beanName) {
    return "observedService".equals(beanName)
            && bean instanceof EchoService;
}
```

Đây là method snippet, cần class `EchoService`; trong ví dụ hoàn chỉnh bên dưới method này nằm trong processor.

### 4.1. Khi nào nên lọc bằng tên, annotation hoặc interface?

| Bộ lọc | Ưu điểm | Giới hạn |
|---|---|---|
| Bean name allowlist | Tác động đúng một/tập beans đã xác định | Rename bean làm mất match |
| `instanceof` marker interface | Không lệ thuộc name, diễn đạt khả năng | Có thể match nhiều implementations |
| Annotation trên target class | Dễ opt-in bằng metadata | Proxy classes/target class resolution có nuances |
| Custom qualifier metadata | Diễn đạt business role qua BeanDefinition | BPP xử lý instance phải đọc metadata đúng cách |
| Base package prefix | Có thể chọn một module | Rủi ro quét rộng/infrastructure không mong muốn |
| Predicate kết hợp name + type | Cụ thể, dễ regression-test | Cần cập nhật allowlist khi naming thay đổi |

Đối với một bài thực hành có đúng một bean đích, **name + type** dễ kiểm tra và tránh tạo thêm reflection logic.

### 4.2. Không ghi secrets hoặc full toString()

Không dùng:

```java
System.out.println("after " + bean);
```

cho mọi bean. `toString()` có thể lộ passwords, tokens, dữ liệu cá nhân hoặc kích hoạt side effects; nhiều Spring infrastructure objects còn có output dài và thay đổi giữa patch versions. Trace của demo chỉ chứa **constant event labels**, không chứa business payload.

### 4.3. Không được gây tác dụng phụ nặng trong lifecycle

Không gọi remote API, DB migration, queue consumer hoặc tạo threads từ BPP chỉ để log init. BPP thường nằm trong startup flow; một network timeout ở đó có thể khiến toàn bộ context refresh thất bại hoặc kéo dài. Monitoring production nên có cơ chế bounded/non-blocking phù hợp, hoặc dùng framework telemetry được hỗ trợ.

## 5. Đăng ký post-processor: vì sao dùng static @Bean?

Một `ApplicationContext` tự phát hiện `BeanPostProcessor` definitions và đăng ký chúng trong quá trình chuẩn bị factory. Trong Java configuration:

```java
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    public static BeanPostProcessor restrictedProcessor() {
        return new RestrictedProcessor();
    }
}
```

Đây là **snippet tích hợp** cần class `RestrictedProcessor`. Javadoc Spring 6.2 khuyến nghị `static` factory method, **lý tưởng là không có dependencies**, để tránh việc phải instantiate configuration class quá sớm, khiến các beans liên quan không eligible for complete BPP processing.

Trong demo, static BPP factory method nhận một dependency **`Trace` đơn giản**, và `Trace` cũng được tạo bởi một **static @Bean method**. Đây là trade-off có chủ đích, chỉ để kiểm thử without global state. `Trace` có thể được tạo sớm trong processor setup, vì vậy **không yêu cầu Trace tự được BPP xử lý/proxy**.

### 5.1. Chuyện gì xảy ra nếu @Bean processor không static?

Nếu viết một instance `@Bean` method trả `BeanPostProcessor`, Spring có thể cần instantiate **cả class @Configuration** sớm để gọi method đó. Configuration bean, dependencies của nó hoặc infrastructure liên quan có thể bị tạo **trước khi mọi processors sẵn sàng**, dẫn tới cảnh báo “not eligible for getting processed by all BeanPostProcessors”.

Không có nghĩa mọi non-static BPP factory method đều thất bại, nhưng đây là rủi ro không cần thiết. Cấu hình BPP nên tách riêng, static khi phù hợp, hạn chế inject business collaborators.

### 5.2. Dùng @Component cho BPP có được không?

Có. BPP tự nó có thể là một bean được component scan phát hiện, nhưng với library hoặc bài demo cần tính xác định, explicit `static @Bean` giúp thấy registration rõ hơn. Không đăng ký cùng processor hai lần bằng cả `@Component` và `@Bean` nếu không chủ đích.

### 5.3. BeanFactory thuần phải thêm processor programmatically

Một `DefaultListableBeanFactory` không có toàn bộ tiện ích `ApplicationContext`. Khi cần:

```java
DefaultListableBeanFactory factory =
        new DefaultListableBeanFactory();
factory.addBeanPostProcessor(new RestrictedProcessor());
```

Đây là snippet tích hợp, cần `RestrictedProcessor` constructor và BeanDefinitions. Một `ApplicationContext` phát hiện BPP beans trong registration phase; plain BeanFactory khi programmatic setup **không tự tương đương** Java Config bootstrap.

### 5.4. Thứ tự registration

Spring 6.2 Javadoc quy định với BPP **autodetected trong ApplicationContext**, thứ tự xét `PriorityOrdered`, `Ordered` và registration semantics. Với BPPs **registered programmatically** vào BeanFactory, thứ tự gọi theo **registration order**, `PriorityOrdered`/`Ordered` interface có thể bị bỏ qua cho ordering của lượt registration đó. `@Order` annotation **không quyết định** BPP ordering như bạn có thể nghĩ.

Vì demo chỉ cần đúng một custom probe, nó không phụ thuộc vào một thứ tự tuyệt đối giữa probe và processor nội bộ `CommonAnnotationBeanPostProcessor`.

## 6. Cấu trúc bài thực hành

Ta dùng cùng class `EchoService` để tạo hai beans:

```text
AnnotationConfigApplicationContext
    |
    +-- AppConfig
           |
           +-- Trace                           (recorder, được tạo sớm)
           +-- RestrictedProbe                 (BeanPostProcessor)
           +-- observedService : EchoService   (được phép tác động)
           +-- ignoredService  : EchoService   (cùng class, không được tác động)
```

Nếu processor chỉ kiểm tra type, **cả observed và ignored** sẽ bị tác động → test FAIL. Nếu nó kiểm tra name **và** type, chỉ observed có events before/after → test PASS.

### 6.1. Callback trace cần có

```text
observed.before
observed.init
observed.after
observed.destroy
```

Các event này phải có một lần mỗi event theo **partial order**. Event `ignored.init` và `ignored.destroy` vẫn xảy ra do BeanDefinition init/destroy riêng, nhưng **không có** `ignored.before` hoặc `ignored.after` từ `RestrictedProbe`.

### 6.2. Khác biệt giữa custom init và BPP

`@Bean(initMethod = "initialize", destroyMethod = "cleanup")` gắn lifecycle callbacks cho **từng instance EchoService**. Nó không phải BPP, nên cả observed và ignored đều chạy init/cleanup.

`RestrictedProbe` mới là cross-cutting processor, có predicate và ghi additional before/after events **chỉ cho observed**.

### 6.3. Tại sao không dùng @PostConstruct để assert trace tuyệt đối?

`@PostConstruct` do một BPP khác xử lý. Để tránh khóa cứng thứ tự giữa nhiều BPP-before implementations, demo dùng **custom initMethod**; contract bảo đảm `postProcessBeforeInitialization` trước custom init và `postProcessAfterInitialization` sau custom init.

Nếu muốn thêm `@PostConstruct` trong một bài mở rộng, chỉ assert nó trước `InitializingBean.afterPropertiesSet()` và custom init, **không** buộc custom BPP-before luôn đứng trước hoặc sau annotation BPP mà không thiết kế ordering rõ.

## 7. Ví dụ đầy đủ Java 17 + Spring Framework 6.2.19 + Maven

Chương trình không cần Spring Boot, database, servlet, môi trường cloud hoặc network. Tất cả assertions nằm ngay trong `main` và ném `AssertionError` nếu kết quả khác kỳ vọng. Java source không cần package scanning; nó đăng ký một `@Configuration` duy nhất.

### 7.1. Cấu trúc thư mục

```text
bounded-bpp-demo/
├── pom.xml
└── src/main/java/example/BoundedPostProcessorDemo.java
```

Yêu cầu JDK 17+ và Maven 3.9+.

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>bounded-bpp-demo</artifactId>
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
```

### 7.3. src/main/java/example/BoundedPostProcessorDemo.java

```java
package example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class BoundedPostProcessorDemo {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            context.refresh();

            Trace trace = context.getBean(Trace.class);
            EchoService observed =
                    context.getBean("observedService", EchoService.class);
            EchoService ignored =
                    context.getBean("ignoredService", EchoService.class);

            System.out.println("TARGET=" + observed.echo("ping"));
            System.out.println("OTHER=" + ignored.echo("ping"));

            List<String> afterRefresh = trace.snapshot();
            requireOrder(
                    afterRefresh,
                    "observed.before", "observed.init", "observed.after");

            System.out.println("OBSERVED_ORDER=PASS");

            requireExactlyOnce(afterRefresh, "ignored.init");
            requireAbsent(afterRefresh, "ignored.before");
            requireAbsent(afterRefresh, "ignored.after");
            requireExactlyOnce(afterRefresh, "observed.before");
            requireExactlyOnce(afterRefresh, "observed.after");

            System.out.println("NON_TARGET_UNTOUCHED=PASS");

            // Destroy is a separate lifecycle phase, not a BPP hook.
            context.close();
            List<String> afterClose = trace.snapshot();

            requireOrder(
                    afterClose,
                    "observed.after", "observed.destroy");
            requireExactlyOnce(afterClose, "observed.destroy");
            requireExactlyOnce(afterClose, "ignored.destroy");
            requireAbsent(afterClose, "ignored.before");
            requireAbsent(afterClose, "ignored.after");

            System.out.println("CLEANUP=PASS");
        }
    }

    private static void requireOrder(
            List<String> events, String... labels) {

        for (String label : labels) {
            requireExactlyOnce(events, label);
        }

        for (int i = 1; i < labels.length; i++) {
            if (events.indexOf(labels[i - 1])
                    >= events.indexOf(labels[i])) {
                throw new AssertionError(
                        "Wrong order " + labels[i - 1]
                        + " -> " + labels[i]
                        + ": " + events);
            }
        }
    }

    private static void requireExactlyOnce(
            List<String> events, String label) {

        int count = Collections.frequency(events, label);
        if (count != 1) {
            throw new AssertionError(
                    "Expected once: " + label
                    + ", got " + count + ": " + events);
        }
    }

    private static void requireAbsent(
            List<String> events, String label) {

        if (events.contains(label)) {
            throw new AssertionError(
                    "Unexpected BPP effect on " + label
                    + ": " + events);
        }
    }

    // Recorder does not need AOP/proxy or Spring lifecycle callbacks.
    public static final class Trace {
        private final List<String> events = new ArrayList<>();

        public synchronized void add(String event) {
            events.add(event);
        }

        public synchronized List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static final class EchoService {
        private final String label;
        private final Trace trace;

        public EchoService(String label, Trace trace) {
            this.label = label;
            this.trace = trace;
        }

        public String echo(String text) {
            return label + ":" + text;
        }

        public void initialize() {
            trace.add(label + ".init");
        }

        public void cleanup() {
            trace.add(label + ".destroy");
        }
    }

    public static final class RestrictedProbe
            implements BeanPostProcessor {

        private final Trace trace;

        public RestrictedProbe(Trace trace) {
            this.trace = trace;
        }

        private boolean isSelected(Object bean, String beanName) {
            return "observedService".equals(beanName)
                    && bean instanceof EchoService;
        }

        @Override
        public Object postProcessBeforeInitialization(
                Object bean, String beanName) {

            if (isSelected(bean, beanName)) {
                trace.add("observed.before");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(
                Object bean, String beanName) {

            if (isSelected(bean, beanName)) {
                trace.add("observed.after");
            }
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {

        // Static because Trace is intentionally available during
        // early BPP registration. It needs no post-processing itself.
        @Bean
        public static Trace trace() {
            return new Trace();
        }

        // Static @Bean limits premature AppConfig instantiation.
        @Bean
        public static BeanPostProcessor restrictedProbe(Trace trace) {
            return new RestrictedProbe(trace);
        }

        @Bean(initMethod = "initialize",
              destroyMethod = "cleanup")
        public EchoService observedService(Trace trace) {
            return new EchoService("observed", trace);
        }

        @Bean(initMethod = "initialize",
              destroyMethod = "cleanup")
        public EchoService ignoredService(Trace trace) {
            return new EchoService("ignored", trace);
        }
    }
}
```

### 7.4. Lệnh chạy

Đứng ở thư mục `bounded-bpp-demo`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.BoundedPostProcessorDemo
```

Stdout do **code tự in**; Spring/Maven có thể thêm logging khác tùy môi trường:

```text
TARGET=observed:ping
OTHER=ignored:ping
OBSERVED_ORDER=PASS
NON_TARGET_UNTOUCHED=PASS
CLEANUP=PASS
```

Chỉ in `PASS` **sau khi assertion tương ứng đạt**. Nếu BPP tác động nhầm `ignoredService`, chương trình ném `AssertionError` và không in những PASS phía sau.

### 7.5. Tại sao OBSERVED_ORDER=PASS?

Khi tạo `observedService`, Spring đã instantiate `EchoService` và cung cấp dependency `Trace`. Trước custom `initialize()`, custom processor ghi `observed.before`; method init ghi `observed.init`; sau init, processor ghi `observed.after`.

Thứ tự được assert:

```text
observed.before < observed.init < observed.after
```

Đây là contract của `BeanPostProcessor` callbacks quanh custom init, không phụ thuộc thứ tự của `ignoredService` đối với `observedService`.

### 7.6. Tại sao NON_TARGET_UNTOUCHED=PASS?

Hai beans đều là `EchoService` objects và đều chạy lifecycle `initialize()`. Nhưng `RestrictedProbe.isSelected` chỉ match bean name `observedService`. Vì vậy:

- `ignored.init` có **một lần** — custom `@Bean(initMethod)` vẫn hoạt động.
- `ignored.before` và `ignored.after` **không xuất hiện** — probe không tác động vào bean khác.
- `observed.before` và `observed.after` đều **đúng một lần** trong context demo.

Nếu bỏ điều kiện kiểm tra name và giữ lại `bean instanceof EchoService`, processor sẽ ghi events cho cả hai (nếu chỉnh code trace labels tương ứng) và tiêu chí giới hạn tác động sẽ bị phá.

### 7.7. Tại sao CLEANUP=PASS?

`context.close()` destroy managed singleton beans, bao gồm cả hai `EchoService`. Mỗi bean có `destroyMethod="cleanup"`, nên trace ghi `observed.destroy` và `ignored.destroy`.

BPP **không ghi** callback destroy. Do đó test chỉ đòi `observed.after < observed.destroy` và mỗi bean được cleanup đúng một lần. Không assert thứ tự destroy giữa observed/ignored vì **hai beans độc lập** không có quan hệ `depends-on` nào buộc một thứ tự business-level.

### 7.8. Vì sao Trace bean cũng có thể được tạo sớm?

Trong startup, Spring phải đăng ký BPP trước khi ordinary application beans được khởi tạo. `restrictedProbe(Trace trace)` đòi một `Trace` instance; `Trace` được tạo từ static `@Bean`, không cần BPP processing cho chính nó.

**Trong code production**, nếu một BPP cần một service đã bị auto-proxy hoặc cần @Transactional, early injection vào processor có thể khiến service không eligible for all BPPs. Tránh dependencies như vậy; cấu hình simple immutable primitives hoặc dùng facilities framework-native.

### 7.9. Vì sao không ghi trace của mọi bean?

Một context chứa nhiều Spring infrastructure beans và user-defined beans. Ghi tất cả callback names làm test phụ thuộc version-specific graph và rất nhiễu. Processor có filter sớm, nên overhead đối với unrelated beans chỉ là kiểm tra name/type đơn giản rồi return.

### 7.10. Vì sao processor không tạo proxy?

Bài học kiểm chứng **scope và lifecycle**, không phải AOP. Proxy wrapping sẽ thêm concerns: interfaces, JDK vs class-based proxies, checked exceptions, target identity, method interception và circular early references. Đó là một bài nâng cao khác. `return bean` bảo đảm processor này không đổi identity, nhưng **không bảo đảm các BPPs khác của ứng dụng không tạo proxy**.

## 8. Một BPP tốt cần đặc tả giới hạn tác động thế nào?

### 8.1. Allowlist và fail-safe default

Dùng allowlist là an toàn hơn blacklist:

```java
private boolean selected(Object bean, String beanName) {
    return allowedNames.contains(beanName)
            && bean instanceof EchoService;
}
```

Đây là snippet minh họa. Với default allowlist rỗng, processor không tác động gì. Chỉ khi cấu hình tên bean rõ mới bật. Tránh condition kiểu `!beanName.startsWith("spring")`, vì bất kỳ third-party user bean nào cũng có thể bị tác động.

Nếu cần nhiều tên, dùng immutable `Set.of("observedService", "otherObservedService")` và validate trùng tên/kiểu trước khi chạy.

### 8.2. Idempotence

Một processor chỉ ghi init events không làm bean state thay đổi, nên không gây side effect trên object. Tuy nhiên **metrics/log counters** vẫn có thể tăng nếu framework gọi BPP callback trên nhiều instances (prototype/recreated contexts). Nếu cần exactly-once semantics xuyên scope, phải xác định key theo **object identity/context id**, không dùng global static boolean trên bean name.

Trong demo, các asserts exactly-once chỉ áp vào một singleton/one context/one normal refresh.

### 8.3. Error policy

Một BPP có thể ném `BeansException` hoặc runtime exception, khiến bean creation fail. Đây là điều hợp lý nếu nó làm **required validation**; nhưng một probe quan sát không thiết yếu mà ném exception do I/O có thể làm application không start.

Tách hai loại:

- **Invariant enforcement:** validation error → fail-fast, message có beanName và constraint, không lộ secrets.
- **Optional telemetry:** ưu tiên bounded/no-fail mechanism, tránh fail startup do logger/monitoring unavailable.

Trong demo, `Trace` ghi in-memory không có network; assertions của program chạy **sau refresh**, không nằm trong BPP callback.

### 8.4. Thread-safety và scopes

BPP singleton có thể được dùng khi các prototypes được tạo ở nhiều threads sau startup. Trường `Trace` dùng synchronized access nhằm tránh race trong recorder. Nhưng production telemetry cần queue/counters đã được thiết kế thread-safe, không dùng unsynchronized `ArrayList` global.

Đừng dùng mutable per-bean state trong BPP instance mà không phân biệt bean name/object identity; nếu hai beans tạo song song, events có thể bị ghi đè.

### 8.5. Không dùng bean.toString để log đầy đủ object

Tránh gọi `bean.toString()` trong lifecycle probe. Nó có thể expose credentials, kích hoạt lazy loading, hoặc tạo các nested calls gây circular initialization. Chỉ log bean name, simple class name và event label nếu đã đánh giá độ nhạy thông tin.

### 8.6. Không getBean() trong BPP

Đặc biệt nguy hiểm:

```java
@Override
public Object postProcessBeforeInitialization(Object bean, String name) {
    Object service = applicationContext.getBean("businessService");
    // Không nên eager-lookup một bean khác ở đây.
    return bean;
}
```

Đây là **anti-pattern snippet** cần field `applicationContext` được cung cấp. Nó có thể tạo early lookup/reentrancy, circular dependency hoặc instantiate bean trước khi mọi processors/app infrastructure sẵn sàng.

Thay vì lookup dynamic trong callback, giới hạn processor vào input `bean` và metadata hợp lệ; dùng constructor dependencies nhỏ, immutable nếu thật sự cần.

## 9. Bài tập mở rộng 1 — Thêm marker interface thay cho tên

Nếu yêu cầu thay đổi từ một bean cụ thể sang “mọi beans opt-in vào observation”, có thể định nghĩa:

```java
public interface LifecycleObserved {
}
```

và implement trên classes được phép:

```java
public final class PaymentAdapter implements LifecycleObserved {
}
```

Snippets này không độc lập nếu còn methods/interfaces khác. Processor khi ấy:

```java
private boolean selected(Object bean) {
    return bean instanceof LifecycleObserved;
}
```

Đây là giải pháp **marker interface opt-in**; dễ kiểm tra type, nhưng áp dụng cho **mọi instances** implement interface. Để thật sự bounded trong một module hoặc vài bean names, kết hợp marker với allowlist.

### 9.1. Marker annotation và proxies

Có thể tạo annotation:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObserveLifecycle {
}
```

Đây là snippet annotation độc lập, cần `java.lang.annotation.*`. Một check `bean.getClass().isAnnotationPresent(ObserveLifecycle.class)` chỉ đáng tin khi runtime class tương ứng với annotated target. Nếu bean đã được wrapped bằng **JDK proxy**, proxy class thường không mang annotation của target implementation, khiến condition không match.

Với AOP/proxy environment, có thể dùng Spring `AopUtils.getTargetClass(bean)`/annotation utilities khi phù hợp, nhưng cần đảm bảo không kích hoạt lazy target creation hoặc đụng proxy identity; test với real proxy configuration.

### 9.2. Khi nào dùng annotation hơn interface?

Annotation diễn đạt opt-in nhưng không ép class implement method contract. Interface thuận tiện khi có behavior shared hoặc type predicate đơn giản. Chọn cách **dễ nhìn thấy, ít side effect** nhất cho requirement.

## 10. Bài tập mở rộng 2 — Chỉ xử lý tên được cấu hình

Một allowlist có thể đọc từ `Environment` ở **cấu hình của processor**, không phải ở mỗi callback:

```java
@Bean
public static BeanPostProcessor restrictedProbe(
        Environment environment) {
    String name = environment.getRequiredProperty(
            "lifecycle.probe.bean-name");
    return new NamedProbe(name);
}
```

Đây là snippet tích hợp cần `NamedProbe` với contract như demo, và import `Environment`. Nhờ đọc property **một lần** khi tạo BPP, callback tránh property lookup liên tục trên mọi bean.

Tuy nhiên, đây vẫn là startup-time configuration; thay Environment property khi context đã refresh **không tự cập nhật** processor's allowlist. Nếu yêu cầu dynamic configuration, dùng một thread-safe snapshot hoặc control interface riêng, không sửa BeanDefinitionRegistry trong callback.

### 10.1. Validation tên được cấu hình

Đừng bỏ qua typo một cách im lặng nếu quan sát bean là chức năng bắt buộc. Có thể dùng `SmartInitializingSingleton`/context startup validation để assert target bean có tồn tại; không dùng `getBean` trong BPP constructor để xác nhận, vì nó có thể gây early creation.

Nếu probe là optional, log một warning có cấu trúc sau refresh khi allowlist không match bất kỳ bean nào, không fail toàn bộ app nếu yêu cầu cho phép.

### 10.2. Qualifier và bean name không đồng nhất

`@Qualifier("audit")` có thể là nhãn chung cho nhiều beans. Nếu requirement là “processor chỉ tác động đúng **bean name**”, kiểm tra `beanName`, không nhầm string qualifier với một unique bean name. Một semantic qualifier có thể match nhiều definitions, nên cần giới hạn chính xác.

## 11. Bài tập mở rộng 3 — Nếu phải sửa BeanDefinition metadata

Giả sử yêu cầu mới: “`optionalCache` không khởi tạo eager nếu chưa được dùng”. Đây là metadata `lazyInit`, không thuộc responsibility của BeanPostProcessor.

```java
@Bean
public static BeanFactoryPostProcessor optionalCacheLazy() {
    return beanFactory -> {
        if (!beanFactory.containsBeanDefinition("optionalCache")) {
            throw new IllegalStateException(
                    "Missing expected bean: optionalCache");
        }

        beanFactory.getBeanDefinition("optionalCache")
                .setLazyInit(true);
    };
}
```

Đây là **snippet tích hợp**, cần import `BeanFactoryPostProcessor` và existing definition `optionalCache`. BFPP chỉ sửa metadata, **không instantiate optionalCache**. Đánh dấu lazy cũng không bảo đảm bean không được tạo sớm nếu một eager singleton khác **yêu cầu nó như dependency**.

### 11.1. Đặt giới hạn BFPP rõ

- Target definition name exact.
- Chỉ sửa `lazyInit`, không tự đổi scope/class/qualifiers.
- Không gọi `beanFactory.getBean` khi metadata processing.
- Fail hoặc no-op khi target missing theo requirement đã định.
- Test `BeanDefinition.isLazyInit()` sau BFPP phase, và test actual instantiation khi dependency graph có eager consumer.

### 11.2. Nếu cần thêm bean definitions

Dùng `BeanDefinitionRegistryPostProcessor` hoặc `ImportBeanDefinitionRegistrar` cho proper registration timing. Đừng cố `registerBeanDefinition` từ `postProcessAfterInitialization` của một business bean khi graph đã đang dựng; điều đó có thể vượt quá contract và gây những bean chưa nhận processor đầy đủ.

### 11.3. Không chạy post-processor như một tiện ích general-purpose

Mỗi extension point nên có single responsibility. Nếu một class vừa rewrite definitions, vừa tạo proxies, vừa khởi chạy background threads, vừa publish events, sẽ rất khó xác định lỗi xảy ra ở giai đoạn nào. Tách thành nhiều components đúng lifecycle, có tests riêng và hooks rõ.

## 12. Sự tương tác với AOP proxy

Một `BeanPostProcessor` khác có thể trả proxy từ `postProcessAfterInitialization`:

```text
raw PaymentAdapter
    |
    +-- BPP-A: return same bean
    |
    +-- AutoProxyCreator: return proxy
    |
    +-- BPP-B: sees proxy or target depending ordering
    |
    v
ApplicationContext exposes final bean reference
```

Đây là mô hình khái quát: exact ordering phụ thuộc Spring processor registration và những processors liên quan.

### 12.1. Không wrap một bean tùy tiện

Một custom BPP sau init có thể chọn `Proxy.newProxyInstance` hoặc `ProxyFactory`, nhưng proxying đúng đòi quản lý interfaces, method handlers, exception types, identity, advice order, AOP early references và type matching.

Trong bài này chọn **quan sát** thay vì **thay object**. Nếu mục tiêu thật là intercept method calls, ưu tiên `@Aspect`/Spring AOP hay một decorator bean được cấu hình rõ, thay vì tự tạo proxy trong một BPP chưa được thiết kế đầy đủ.

### 12.2. Circular dependency và early reference

Nếu A/B có singleton setter cycle, Spring có thể cấp early references. Một BPP tự tạo **proxy chỉ sau initialization** có nguy cơ làm một dependent giữ raw A còn context expose proxy A, gây inconsistent behavior.

`SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference` tồn tại nhằm hỗ trợ advanced proxy creators trong tình huống này. Không tự implement nó chỉ để logging lifecycle; đơn giản hóa graph hoặc tránh wrapping là biện pháp an toàn hơn.

### 12.3. @Transactional và self-invocation

Nếu target bean mang `@Transactional`, không được giả định method gọi từ chính `@PostConstruct` hoặc cùng `this.someMethod()` sẽ đi qua proxy advice. Đây là hạn chế của proxy-based AOP, không phải lỗi riêng của RestrictedProbe.

Test call path qua bean exposed by context khi cần chứng minh transaction/caching/security advice được áp dụng.

## 13. Scope, FactoryBean và context hierarchy

### 13.1. Singleton

Một singleton bean do Spring quản lý bình thường được tạo một lần trong một BeanFactory. Trong demo, `observedService` và `ignoredService` là eager singletons; mỗi callback BPP được gọi tương ứng một lần trong scenario này.

### 13.2. Prototype

Một prototype có thể được tạo nhiều lần. BPP callbacks chạy mỗi lần instance được tạo/initialize, nên recorder có thể có nhiều entries cho cùng bean name.

Nếu giữ exact-name allowlist, processor sẽ áp dụng cho **mọi prototype instances** có bean name `observedService`; không đúng với requirement “chỉ instance đầu tiên”. Khi phải giới hạn theo instance identity, cần contract khác và cân nhắc memory leaks/caching.

### 13.3. Bean trong custom scope

Request/session scopes có lifecycle tùy scope activation. BPP có thể được gọi khi target được tạo tại request đầu tiên, không nhất thiết lúc `ApplicationContext.refresh()`. Vì vậy một test chỉ khởi động context không đủ để kiểm chứng mọi scoped target callbacks.

### 13.4. FactoryBean

Javadoc BPP lưu ý `postProcessAfterInitialization` có thể được gọi cho cả `FactoryBean` và **product objects**. Nếu bạn lọc theo beanName nhưng không type guard, có thể vô tình tác động lên factory object thay vì product.

Trong demo, `bean instanceof EchoService` ngăn những objects không thuộc target type bị ghi sự kiện. Với FactoryBean-based production integration, test rõ product/factory dereference và lifecycle.

### 13.5. Parent-child contexts

Một BPP được đăng ký vào một BeanFactory chỉ tác động lên beans **do factory ấy tạo**, không tự xử lý parent-owned singleton objects dù child có thể lookup chúng. Nếu muốn instrumentation ở parent, processor phải được đăng ký trong parent factory hoặc có architecture tương ứng.

Không suy rằng “bean name observedService tồn tại trong hierarchy” thì child processor chắc chắn đã chạy trên parent object.

## 14. Spring AOT và native image

Spring AOT có thể phân tích bean definitions ở build time rồi generate runtime initialization code. Các BPPs có đóng góp AOT riêng tùy framework; không được giả định mọi reflection/dynamic proxy trick đều có thể hoạt động trên GraalVM native image như regular JVM.

Một RestrictedProbe như demo:

- Chỉ dùng public interface/instanceof và string beanName.
- Không tự tạo classes hay dynamic JDK proxies.
- Không scan package bằng reflection.
- Không lookup arbitrary class bằng string.
- Trả lại cùng object.

Nhờ đó nó **đơn giản để AOT phân tích hơn** một custom proxy generator, nhưng điều này chưa chứng minh rằng một dự án native-image đầy đủ không cần RuntimeHints khác. Nếu thay bằng reflection-heavy annotation scanning, kiểm tra hints và test native artifact thật.

### 14.1. Build-time feature choices

Nếu đăng ký RestrictedProbe qua `@Profile` hoặc `@Conditional`, lựa chọn processor **có tồn tại trong graph hay không** có thể được quyết định trong AOT processing. Thay runtime profile sau khi AOT artifact được build không tự regenerate bean graph/processor registrations.

### 14.2. Không đóng băng thông tin nhạy cảm vào generated code

Một custom post-processor/AOT contribution không được nhúng API keys, passwords hoặc user information vào generated sources. Log/trace chỉ ghi non-sensitive lifecycle metadata.

## 15. Viết integration tests chắc chắn, không chỉ in stdout

Demo `main` chứa assertions đủ cho bài tập, nhưng trong dự án lớn nên dùng JUnit 5 với context riêng:

```java
@Test
void probeOnlyObservesTheAllowedBean() {
    try (var context = new AnnotationConfigApplicationContext()) {
        context.register(AppConfig.class);
        context.refresh();

        Trace trace = context.getBean(Trace.class);
        List<String> events = trace.snapshot();

        assertEquals(1, Collections.frequency(
                events, "observed.before"));
        assertEquals(1, Collections.frequency(
                events, "observed.after"));
        assertFalse(events.contains("ignored.before"));
        assertFalse(events.contains("ignored.after"));
    }
}
```

Đây là **JUnit 5-style snippet**, cần test dependencies/imports và các nested classes từ demo. `try-with-resources` đóng context khi test kết thúc; test không bắt buộc dùng `@SpringBootTest` vì mục tiêu chỉ là Spring Core infrastructure.

### 15.1. Test ordering, không chụp nguyên log

```java
assertTrue(events.indexOf("observed.before")
        < events.indexOf("observed.init"));
assertTrue(events.indexOf("observed.init")
        < events.indexOf("observed.after"));
```

Đây là snippet JUnit, cần events chứa đúng một lần từng label. Không assert thứ tự `ignored.init` tương đối với `observed.init`, vì hai beans không có explicit dependency edge.

### 15.2. Negative test quan trọng hơn happy path

Một bộ test đủ tốt có ít nhất:

- Positive case: expected bean match name+type → có before/after đúng một lần.
- Negative same type: ignoredService cũng là EchoService → **không bị probe tác động**.
- Negative same name/wrong type: nếu thay definition dưới name observedService bằng class khác, type guard phải không match.
- Renamed bean: đổi tên observedService → default không match, test thất bại nếu instrumentation là requirement.
- Prototype: tạo hai instances → events lặp tương ứng, không khẳng định singleton exact-once.
- Proxy: đăng ký AOP cho target → kiểm tra type predicate/identity và placement trong BPP ordering.
- Context hierarchy: parent-owned bean không tự được child probe quan sát.
- Error injection: recorder throw exception theo kiểm soát → lựa chọn fail-fast hay no-fail như policy.

**Không cần đưa tất cả variants vào cùng một `main`**. Bài demo tập trung một scenario rõ, còn các tests mở rộng được tách biệt để tránh trạng thái các contexts bị trộn.

### 15.3. Test no-wrap contract

Nếu processor của bạn được thiết kế “observation only”, assert nó trả chính object input:

```java
Object instance = new EchoService("observed", new Trace());
Object result = new RestrictedProbe(new Trace())
        .postProcessAfterInitialization(instance, "observedService");
assertSame(instance, result);
```

Đây là JUnit snippet tích hợp, dùng classes trong demo. Nó chứng minh **chính RestrictedProbe** không wrap input. Không assert toàn bộ Spring context exposed bean luôn cùng raw object khi application có các AOP processors khác.

### 15.4. Test callback count trong lifecycle đúng

Một context có cache/test reuse có thể khiến bean BPP events chỉ được ghi một lần cho nhiều test methods. Tạo isolated `AnnotationConfigApplicationContext` hoặc dùng `@DirtiesContext` khi thực sự cần force close/recreate, thay vì kỳ vọng @SpringBootTest context cache tự refresh mỗi lần.

## 16. Debug các lỗi điển hình

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
|---|---|---|
| Processor không được gọi | Chưa đăng ký BPP hoặc bean không do factory tạo | Kiểm tra ApplicationContext/BeanFactory registration |
| Processor chỉ gọi sau init | Chỉ override after callback | Override đúng method theo yêu cầu |
| Target name không match | @Bean method/alias/name thay đổi | Kiểm tra actual beanName passed vào BPP |
| Mọi beans đều bị log | Không có allowlist/type guard | Predicate fail-safe mặc định false |
| Bean khác cùng type cũng bị log | Chỉ dùng `instanceof` | Thêm exact bean-name check |
| Bean cùng name nhưng đổi type vẫn bị log | Chỉ dùng string name | Kết hợp type guard |
| @PostConstruct xuất hiện trước custom BPP-before | BPP ordering khác dự đoán | Không assert relative order không được bảo đảm |
| Missing @PostConstruct | Jakarta annotation processor chưa active | Dùng AnnotationConfigApplicationContext/setup processor |
| Log “not eligible for all BeanPostProcessors” | BPP/dependencies instantiate sớm | Static @Bean, giảm dependencies |
| `BeanCurrentlyInCreationException` | Callback/getBean gây reentrant lookup | Bỏ getBean trong BPP, sửa cycle |
| Bean bị double-init | Processor tự gọi init method | Để container quản lý lifecycle |
| Bean mất @Transactional | Raw/proxy mismatch hoặc self-invocation | Kiểm tra AOP exposed proxy, tránh tự wrap |
| ClassCastException sau wrapping | Proxy không implement expected type | Dùng đúng interface, proxy strategy |
| BPP trả null | Cắt chuỗi processors về sau | Return input khi không xử lý |
| Prototype có nhiều events cùng bean name | Mỗi instance được tạo lại | Tracking theo instance/context nếu cần |
| Destroy không được probe ghi | BPP thường không phải destroy hook | Custom destroy/DestructionAwareBPP |
| Context child không log parent bean | BPP có phạm vi một factory | Đăng ký ở bean owner factory |
| Native-image test fail | Runtime reflection/proxy hints thiếu | Kiểm tra AOT-generated registrations/hints |
| Processor tự truy cập remote API lúc build/start | Side-effect/timeout không kiểm soát | Bỏ I/O khỏi BPP |
| Logs chứa secrets | Dùng `bean.toString()` hoặc payload | Chỉ ghi metadata non-sensitive |
| Test đôi khi fail bởi order giữa beans khác nhau | Ordering chưa có dependency contract | Assert partial order trong một bean |

### 16.1. Quy trình chẩn đoán có hệ thống

1. Xác nhận `BeanPostProcessor` bean được đăng ký trong **BeanFactory sở hữu target**.
2. Kiểm tra name thực tế mà Spring đưa vào callback; aliases không luôn được truyền như bạn đoán.
3. Kiểm tra target runtime type và khả năng proxy khác implementation class.
4. Kiểm tra `@Bean` method có static khi trả BPP và các dependencies bị tạo sớm không.
5. Kiểm tra processor có side effects, blocking I/O hay business bean lookups không.
6. Kiểm tra `return bean` trên **tất cả nhánh**, đặc biệt nhánh không match.
7. Đọc root cause của startup exception, không chỉ outer `BeanCreationException`.
8. Bật appropriate Spring bean factory logs nếu cần, nhưng không đặt TRACE vĩnh viễn ở prod.
9. Tạo một minimal context có hai beans cùng type như demo để kiểm tra bounded behavior.
10. Tách errors liên quan init callbacks, proxying, registration và destruction thành các tests riêng.
11. Chỉ triển khai processor sau khi negative tests chứng minh unrelated beans **không bị tác động**.

## 17. Những việc không nên làm với BeanPostProcessor

### 17.1. Chỉnh tất cả beans để thêm một field chung

Nếu code nghiệp vụ có một contract `setAuditClient(...)`, thường nên dùng **constructor injection** hoặc một decorator explicit. BPP can thiệp vào mọi objects khiến dependencies khó nhìn thấy và tests không phản ánh thực tế khi `new` class ngoài Spring.

### 17.2. Thay return bean bằng một object khác khi chưa định nghĩa proxy contract

```java
@Override
public Object postProcessAfterInitialization(Object bean, String name) {
    return new SomeWrapper(bean);
}
```

Đây là anti-pattern snippet, cần `SomeWrapper`. Nếu wrapper không cùng type/interfaces, callers có thể gặp `BeanNotOfRequiredTypeException` hoặc proxy selection lỗi. Nếu wrapper có side effects/AOP, phải xử lý identity, early bean reference và advised methods. Bài này không yêu cầu điều đó.

### 17.3. Gọi close/destroy trong after-init

Post-init có nghĩa bean vừa được chuẩn bị sử dụng, không phải thời điểm cleanup. Để hủy một bean, dùng appropriate destroy callback, context close, `DisposableBean`, `DestructionAwareBeanPostProcessor` hoặc owner lifecycle.

### 17.4. Dùng global static mutable registry

Một static `Map<String,Object>` giữ mọi bean qua nhiều contexts có thể gây memory leaks, cross-test contamination và giữ references sau close. Trong ví dụ, `Trace` là một bean context-local, nên lifetime được giới hạn bởi context. Tránh cache object references trong BPP khi không cần.

### 17.5. Giả định @Order annotation luôn quyết định processor order

Javadoc nói `@Order` trên BPP không được tính vào BPP ordering như đối với một số lists khác. Dùng `PriorityOrdered`/`Ordered` theo rules của auto-detected processors hoặc programmatic registration order khi có yêu cầu ordering rõ, và test actual behavior.

### 17.6. Đăng ký processor sau khi targets đã được tạo

Nếu gọi `addBeanPostProcessor` sau khi một singleton đã được instantiate/initialized, nó **không retroactively chạy trên existing bean**. Cần đăng ký BPP trong phase sớm trước ordinary bean creation.

### 17.7. Dùng BPP để điều khiển AOT condition ở runtime

Nếu một bean definition bị `@Profile`/`@Conditional` loại ở AOT build, BPP runtime không thể “đánh thức” definition không có bằng cách can thiệp instance. Chọn đúng phase: definition selection ở config/AOT, bean instance post-processing ở creation runtime.

## 18. Checklist nghiệm thu cho bài tập

### 18.1. Trước khi code

- [ ] Chốt đúng **một** responsibility: quan sát before/after init của observedService.
- [ ] Xác định allowlist bean name và runtime type; mọi case khác no-op.
- [ ] Quyết định processor không proxy, không mutate target, không tạo thread/network I/O.
- [ ] Dùng BPP vì requirement thuộc **bean instance initialization**, không phải metadata registration.
- [ ] Xác định cách Spring tự phát hiện BPP qua ApplicationContext.

### 18.2. Sau khi viết

- [ ] BPP implements `BeanPostProcessor` và override đúng hai callbacks.
- [ ] Predicate so `"observedService".equals(beanName)` và `instanceof EchoService`.
- [ ] Mọi branch đều `return bean;`.
- [ ] `@Bean` cho BPP là static; không cần inject business bean vào BPP.
- [ ] Trace ghi only fixed event labels, không tiết lộ payload.
- [ ] Hai `EchoService` có bean names khác nhau trong một context.

### 18.3. Khi chạy test

- [ ] `TARGET=observed:ping`, `OTHER=ignored:ping`.
- [ ] `observed.before < observed.init < observed.after`.
- [ ] `ignored.init` vẫn chạy nhưng `ignored.before` và `ignored.after` không có.
- [ ] Exactly-once assertions chỉ áp dụng context có singletons.
- [ ] Context close gọi `cleanup` trên cả observed và ignored.
- [ ] Không assert thứ tự giữa unrelated singleton beans.
- [ ] Không assert trước/sau tương đối giữa custom BPP-before và `@PostConstruct` khi chưa kiểm soát các BPPs.
- [ ] Không assert exposed object luôn là raw target nếu app có AOP khác.

### 18.4. Tiêu chí chấp nhận

**Đạt yêu cầu** khi processor **chỉ làm đúng việc đã cho phép**, không thay đổi logic của service, hai beans vẫn hoạt động bình thường, callback order đúng theo Spring contract, và negative test xác nhận một bean cùng type nằm ngoài allowlist không chịu ảnh hưởng.

## 19. Câu hỏi tự kiểm tra

**1. BeanPostProcessor khác BeanFactoryPostProcessor ở đâu?**  
BPP làm việc với bean **instance** khi creation/initialization; BFPP sửa **bean definition metadata** trước ordinary instantiation.

**2. BeanDefinitionRegistryPostProcessor dùng khi nào?**  
Khi cần đăng ký/sửa definitions tại phase registry sớm hơn.

**3. Vì sao bài này dùng BPP?**  
Vì cần quan sát callbacks quanh init của một instance, không chỉnh định nghĩa bean.

**4. Vì sao lọc bằng bean name và type cùng lúc?**  
Để tránh tác động vào beans cùng class nhưng khác tên, hoặc beans khác class vô tình mang cùng name.

**5. BPP có được áp cho tất cả beans mặc định không?**  
Một registered BPP có thể được Spring gọi trên nhiều created beans, nên phải tự lọc scope tác động.

**6. BPP-before xảy ra sau constructor và property population không?**  
Có trong normal bean creation path; nó xảy ra trước init callbacks như `afterPropertiesSet()`.

**7. BPP-after có được trả proxy không?**  
Có, nhưng demo chủ động return input bean.

**8. Tại sao không trả null để bỏ qua?**  
Trả null có thể dừng việc gọi các BPPs tiếp theo trong chain.

**9. @PostConstruct có luôn chạy sau custom BPP-before không?**  
Không, vì @PostConstruct được xử lý qua BPP khác, ordering có thể khác.

**10. `@Bean` trả BPP nên static vì sao?**  
Để giảm nhu cầu instantiate configuration class sớm, hạn chế beans không eligible for all BPPs.

**11. Processor có nên inject một @Transactional business service vào constructor không?**  
Thường không; dependency có thể được tạo quá sớm trước khi proxy processors đăng ký.

**12. Vì sao Trace cũng là static @Bean?**  
Để nó được tạo sớm một cách đơn giản và không yêu cầu full BPP/proxy processing.

**13. Hai EchoService cùng type có bị probe log cả hai không?**  
Không; predicate còn kiểm tra đúng beanName observedService.

**14. Custom @Bean initMethod chạy với ignoredService không?**  
Có; custom init thuộc BeanDefinition riêng, không phụ thuộc probe filter.

**15. BPP có phải cơ chế hủy bean không?**  
Không; destruction dùng callbacks riêng hoặc `DestructionAwareBeanPostProcessor`.

**16. Prototype có callbacks mỗi lần tạo instance không?**  
Có thể có mỗi lần instance được tạo, nên event count không còn là singleton exactly-once.

**17. Parent context bean có được child BPP xử lý mặc định không?**  
Không. BPP có phạm vi bean owner factory.

**18. FactoryBean product có thể qua BPP-after không?**  
Có, theo BPP contract; cần xác định factory object và product.

**19. BeanPostProcessor có nên gọi context.getBean trong callback không?**  
Không như pattern mặc định; có nguy cơ reentrancy/early initialization/cycle.

**20. @Order có luôn quyết định BPP order không?**  
Không; xem PriorityOrdered/Ordered và programmatic registration rules.

**21. Nếu cần chỉnh lazyInit property của một BeanDefinition nên dùng gì?**  
`BeanFactoryPostProcessor` ở phase metadata, không dùng BPP-after.

**22. Nếu chỉ cần intercept method calls, có nên viết một BPP tạo proxy thủ công không?**  
Thường ưu tiên Spring AOP hoặc decorator explicit, tránh tự xử lý proxy infrastructure.

**23. Vì sao log bean.toString nguy hiểm?**  
Có thể lộ secrets, kích hoạt side effects hoặc tạo output khó kiểm soát.

**24. Khi nào @Lazy target vẫn có thể được tạo?**  
Khi một eager dependency yêu cầu nó, hoặc scoped/lifecycle infrastructure quyết định instantiate.

**25. Bài thực hành đạt khi nào?**  
Khi BPP có scope tác động được mô tả và chứng minh bằng positive/negative tests, return object không đổi và callbacks đúng phase.

## 20. Tài liệu tham khảo chính thức

- [Spring Framework 6.2 Reference — Container Extension Points](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-extension.html): `BeanPostProcessor`/`BeanFactoryPostProcessor`, registration and early initialization caveats.
- [Spring Framework 6.2 API — BeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html): callback contract, null-return behavior, ordering, FactoryBean products.
- [Spring Framework 6.2 API — BeanFactoryPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/BeanFactoryPostProcessor.html): metadata processing phase and early-instantiation caveats.
- [Spring Framework 6.2 API — BeanDefinitionRegistryPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/support/BeanDefinitionRegistryPostProcessor.html): registry phase and bean definition extension point.
- [Spring Framework 6.2 API — InstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/InstantiationAwareBeanPostProcessor.html): hooks beyond normal BPP.
- [Spring Framework 6.2 API — SmartInstantiationAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html): prediction/early bean references for proxy infrastructure.
- [Spring Framework 6.2 API — DestructionAwareBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/config/DestructionAwareBeanPostProcessor.html): per-bean destruction hook.
- [Spring Framework 6.2 Reference — Customizing Bean Nature](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): callback order and lifecycle.
- [Spring Framework 6.2 Reference — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html): `@Configuration`, `@Bean`, `AnnotationConfigApplicationContext`.
- [Spring Framework 6.2 Reference — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html): singleton/prototype callback behavior and ownership.
- [Spring Framework 6.2 Reference — Ahead-of-Time Optimizations](https://docs.spring.io/spring-framework/reference/6.2/core/aot.html): AOT-specific processors and native constraints.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-008">Tái hiện lỗi cấu hình rồi kiểm chứng cách sửa</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện lỗi cấu hình rồi kiểm chứng cách sửa — thực hành tổng hợp Spring Core Container

## 1. Mục tiêu và phương pháp

Trong một ứng dụng Spring, “chạy được” không có nghĩa cấu hình đã đúng; ngược lại, một startup failure thường là tín hiệu hữu ích cho biết container không thể tạo bean graph như mong muốn. Kỹ thuật chẩn đoán có thể lặp lại là **cố ý tái hiện lỗi nhỏ nhất → đọc exception chain → xác định nguyên nhân IoC → sửa cấu hình → tạo context mới → kiểm chứng behavior**, thay vì đoán và thêm annotations ngẫu nhiên.

Bài thực hành này tập trung vào năm nhóm lỗi điển hình trong Spring Framework Core Container:

1. **Thiếu dependency:** cần `PaymentPort` nhưng không có candidate.
2. **Nhiều candidates:** tồn tại hai `PaymentPort`, nhưng một injection point cần đúng một.
3. **Vòng constructor:** bean A cần B, bean B cần A; container không thể hoàn tất constructor graph.
4. **Thiếu cấu hình ngoài:** `@Value` yêu cầu một placeholder chưa có trong `Environment`.
5. **Initialization callback thất bại:** bean tạo được object nhưng vi phạm invariant trong `afterPropertiesSet()`; phiên bản `@Lazy` có thể đẩy lỗi sang lần truy cập đầu tiên.

Mỗi lỗi có **phiên bản sửa** và assertion tương ứng. Ta không coi việc catch exception là một “fix”. Một fix chỉ được chấp nhận khi context có thể refresh **và** một phép kiểm thực tế chứng minh đã cấp đúng bean, chọn đúng implementation, cấu trúc graph hợp lý hoặc nạp đúng property.

### Tiêu chí hoàn thành

- Tái hiện được từng lỗi từ một `@Configuration` tối giản, không phụ thuộc môi trường production.
- Đọc được wrapper `BeansException` và nguyên nhân chuyên biệt sâu trong `getCause()` chain.
- Phân biệt **bị thiếu bean**, **nhiều bean**, **bean đang trong quá trình tạo**, **placeholder sai** và **callback khởi tạo sai**.
- Hiểu vì sao bean lazy có thể hoãn lỗi thay vì sửa nguyên nhân.
- Viết Maven project Java 17+ chạy độc lập bằng Spring Framework `6.2.19` và JUnit Jupiter `5.11.4`.
- Có cả **regression tests cho cấu hình hỏng** (xác nhận đúng loại lỗi) và **tests cho bản sửa** (assert hành vi nghiệp vụ/metadata).
- Biết cách chuyển bài tập này thành quy trình debug an toàn với logs, profile và readiness trong ứng dụng thật.

**Phạm vi:** Đây là bài về IoC/Dependency Injection, `@Bean`, qualifiers, property placeholders và lifecycle. Nó không thiết lập Spring Boot, web server, JDBC, Kafka hoặc external payment provider; endpoint trong ví dụ chỉ là giá trị cấu hình chuỗi và không gửi HTTP request.

## 2. Tư duy “red → diagnose → fix → green”

Một quy trình chất lượng dùng một checklist cố định:

```text
RED: context hoặc lookup FAIL
           |
           v
Capture wrapper exception + most-specific cause
           |
           v
Classify: missing / ambiguous / circular / property / init
           |
           v
Inspect only related BeanDefinitions and dependencies
           |
           v
Minimal change expresses business intent
           |
           v
GREEN: fresh context refreshes
           |
           v
Assert behavior (not only isActive==true)
           |
           v
Run regression suite under expected profiles/configurations
```

**Red test** ở đây có thể là một test `assertThrows(...)` chạy **PASS**, bởi mục đích của test là chứng minh cấu hình *cố tình sai* tái hiện loại lỗi đã biết. Không đồng nhất một test pass với một application healthy: luôn ghi rõ config/class đang thuộc case hỏng hay case đã sửa.

Ví dụ đối lập:

```java
// RED: thử refresh một cấu hình thiếu dependency.
assertThrows(BeansException.class, brokenContext::refresh);

// GREEN: thử refresh cấu hình đã sửa rồi kiểm kết quả.
assertDoesNotThrow(fixedContext::refresh);
assertEquals("CARD:O-7",
        fixedContext.getBean(Checkout.class).charge("O-7"));
```

Đây là **JUnit fragments**, `brokenContext`, `fixedContext`, `Checkout` và imports được khai trong project runnable ở phần sau. Đoạn đầu không thay đoạn thứ hai; cả hai giải quyết các câu hỏi khác nhau.

### “Thêm @Lazy” có phải fix lỗi startup?

Không nhất thiết. Nếu bean tạo lỗi nhưng nay chỉ bị postpone tới `getBean()` hoặc proxy invocation, startup PASS có thể che một failure tới traffic đầu tiên. Với dependency thiết yếu, ta cần **sửa constructor/property/invariant**; lazy chỉ hợp lý nếu business requirement cho phép trì hoãn và có error-handling/readiness phù hợp.

## 3. Xác định chính xác giai đoạn lỗi trong quá trình tạo bean

Spring đọc `@Configuration`, `@Import` hoặc `@ComponentScan` thành `BeanDefinition`, xử lý metadata và giải quyết các injection points khi bean thực sự cần được tạo. Đối với bài **chẩn đoán lỗi**, ta không cần dựng lại toàn bộ bean dependency graph từ đầu; hãy xác định **lỗi bắt đầu ở giai đoạn nào**:

| Giai đoạn | Dấu hiệu thường gặp | Kiểm chứng trước khi sửa |
| --- | --- | --- |
| Đăng ký/đọc definitions | Bean không được scan, profile không match, placeholder cấu hình thiếu | Kiểm tra `register(...)`, package scan, active profiles và `Environment` |
| Chọn dependency | Không có ứng viên hoặc có nhiều ứng viên `PaymentPort` | Đối chiếu candidate types/names, `@Qualifier`, `@Primary` và nested cause |
| Tạo graph | Constructor/factory throw hoặc A → B → A | Vẽ lại **cạnh constructor thực tế**, tìm chu trình, không chèn `@Lazy` chỉ để che lỗi |
| Khởi tạo một instance | `@PostConstruct`, `InitializingBean`, custom init hoặc `BeanPostProcessor` throw | Phân biệt bean lỗi với dependencies đã tạo thành công; kiểm tra cleanup state |
| Sau refresh | Lazy singleton, prototype hoặc lazy-resolution proxy mới bị yêu cầu | Viết test kích hoạt **đúng lookup** thay vì chỉ assert `refresh()` thành công |
| Khi chạy use case | Business method ném lỗi sau khi bean đã sẵn sàng | Tách business failure khỏi container startup exception |

`AnnotationConfigApplicationContext.refresh()` thường pre-instantiate non-lazy singleton beans; **không** có nghĩa mọi scoped/lazy bean đã được tạo. Một `BeanCreationException` có thể bao ngoài nhiều nguyên nhân khác nhau, nên bài thực hành sẽ kiểm **root cause theo từng kịch bản riêng**, không sửa chỉ vì tên exception trên dòng đầu.

## 4. Đọc stacktrace đúng cách

Một lỗi dependency thường có ba lớp:

```text
UnsatisfiedDependencyException: creating bean 'checkout'
  constructor/factory method parameter 0 ...
    Caused by: NoSuchBeanDefinitionException:
      No qualifying bean of type PaymentPort available
```

Trong kịch bản khác:

```text
UnsatisfiedDependencyException: creating bean 'checkout'
  constructor/factory method parameter 0 ...
    Caused by: NoUniqueBeanDefinitionException:
      expected single matching bean but found 2:
      cardPayment,bankPayment
```

Cả hai block trên là **stacktrace minh họa**, không phải console output chính xác của từng Spring patch version. Ý nghĩa:
- Wrapper chỉ điểm bean đang tạo và injection point.
- Nested cause nói lý do dependency resolution thất bại.
- Bean names/type cho biết tập ứng viên và nguồn cấu hình cần kiểm.
- `NoUniqueBeanDefinitionException` extends `NoSuchBeanDefinitionException`; khi cần phân loại, kiểm **NoUnique trước NoSuch** để tránh gọi nhầm ambiguity là missing.

### API liên quan

- `BeansException`: superclass của nhiều lỗi Spring container.
- `BeanCreationException`: không thể tạo hoặc initialize một bean.
- `UnsatisfiedDependencyException`: dependency constructor/field/setter chưa được thỏa.
- `NoSuchBeanDefinitionException`: yêu cầu nhưng không tìm được bean đáp ứng.
- `NoUniqueBeanDefinitionException`: nhiều matches mà chỉ cần một.
- `BeanCurrentlyInCreationException`: bean đang tạo bị yêu cầu trở lại, thường do constructor cycle.
- `BeanInstantiationException`: gọi constructor/factory method thất bại.
- `PropertySourcesPlaceholderConfigurer` có thể khiến unresolved `${...}` bị reject trong context refresh.
- `IllegalStateException` từ validation có thể là root cause do chính ứng dụng ném.

Một nguyên tắc cho tests: **assert subtype và behavior**, không assert nguyên văn full stacktrace vì formatting, class path, stack frames và wrappers có thể thay đổi.

## 5. Lỗi 1 — thiếu một bean bắt buộc

**Cấu hình hỏng:**

```java
@Configuration(proxyBeanMethods = false)
class MissingConfig {
    @Bean
    Checkout checkout(PaymentPort port) {
        return new Checkout(port);
    }
}
```

Đây là config fragment; cần types/imports. Khi không có bất kỳ `PaymentPort` bean nào, container không thể resolve argument của factory method `checkout`. Trong `refresh()`, việc tạo eager `Checkout` fail.

**Không chữa bằng `@Primary` trên Checkout**: nó không sinh ra missing PaymentPort. Cần chọn một trong các hướng:
1. Register một concrete adapter thực sự vào context dưới type phù hợp.
2. Sửa `@ComponentScan`/`@Import` nếu bean phải tồn tại nhưng đang không được phát hiện.
3. Nếu dependency thật sự **optional**, thiết kế rõ `ObjectProvider<PaymentPort>`, `Optional<PaymentPort>` hoặc một no-op fallback thích hợp — không mù quáng biến dependency thiết yếu thành optional.
4. Nếu bean được lọc bởi `@Profile`/`@Conditional`, kiểm active environment và configuration contract.

**Phiên bản sửa:**

```java
@Configuration(proxyBeanMethods = false)
class MissingFixedConfig {
    @Bean PaymentPort cardPayment() {
        return new CardPayment();
    }
    @Bean Checkout checkout(PaymentPort port) {
        return new Checkout(port);
    }
}
```

Ở cấu hình này chỉ có một PaymentPort candidate, nên container có thể cấp cho Checkout. Green test không dừng tại `ctx.isActive()` mà phải gọi `checkout.charge("O-7")` và kiểm chính xác text do adapter card tạo.

### Tình huống production tương tự

Bạn vừa di chuyển implementation sang một Maven module mới nhưng không include dependency/module vào application; hoặc bean nằm ngoài base package của `@ComponentScan`; hoặc cấu hình profile không bật implementation cần dùng. Sửa đúng **registration/configuration boundary** thay vì thêm bean ngẫu nhiên ở test.

## 6. Lỗi 2 — nhiều candidates cùng type

**Cấu hình hỏng:** hai beans implement `PaymentPort` và một `Checkout` yêu cầu một PaymentPort.

```java
@Bean PaymentPort cardPayment() { return new CardPayment(); }
@Bean PaymentPort bankPayment() { return new BankPayment(); }
@Bean Checkout checkout(PaymentPort port) {
    return new Checkout(port);
}
```

Đây là các method fragments. Khi có hai candidates không được phân biệt, Spring ném `NoUniqueBeanDefinitionException` trong lúc resolve dependency.

**Lưu ý:** lỗi “nhiều beans có cùng **type**” khác lỗi “hai definitions dùng cùng **bean name**”. Duplicate name/override là một vấn đề registry khác; không nên xóa vội implementation hợp lệ chỉ vì cả hai cùng interface.

### Fix A — @Qualifier khi consumer phải chọn một implementation

```java
@Bean
Checkout checkout(
        @Qualifier("cardPayment") PaymentPort port) {
    return new Checkout(port);
}
```

Fragment này khai business intent rõ: Checkout này dùng card. `@Qualifier` là narrowing semantic trên tập candidates cùng type; nó không biến bean type sai thành compatible.

### Fix B — @Primary khi cần mặc định global cho single-valued injection

```java
@Bean
@Primary
PaymentPort bankPayment() {
    return new BankPayment();
}
```

Fragment này cho biết bank là candidate mặc định khi inject `PaymentPort` đơn và có nhiều matches. Card vẫn tồn tại trong context; `List<PaymentPort>` và `Map<String,PaymentPort>` có thể thu thập cả hai theo rule tương ứng.

### Fix C — @Fallback Spring 6.2 nếu một implementation thứ yếu

```java
@Bean
@Fallback
PaymentPort sandboxPayment() {
    return new SandboxPayment();
}
```

Fragment này cần `SandboxPayment` và Spring Framework 6.2+. Khi có một non-fallback candidate duy nhất, nó được ưu tiên trong single-valued resolution. `@Fallback` không giải bài toán có **hai non-fallback** hoặc nhiều fallback nhưng không có candidate duy nhất.

### Khi nào không chọn qualifier hoặc primary?

Nếu business cần chọn payment method theo request, injection một `Map<String,PaymentPort>` hoặc list cùng router phân loại strategy là mô hình đúng hơn. Không lấy `list.get(0)` vì thứ tự đăng ký hay `@Order` không tự có nghĩa “implementation mặc định”.

## 7. Lỗi 3 — constructor circular dependency

**Graph hỏng:**

```text
A(B) ----> B(A)
  ^          |
  |__________|
```

Hai beans yêu cầu constructor arguments của nhau:

```java
@Bean CycleA cycleA(CycleB b) { return new CycleA(b); }
@Bean CycleB cycleB(CycleA a) { return new CycleB(a); }
```

Đây là method fragments. Spring bắt đầu tạo A, cần B; bắt đầu tạo B, cần A đang tạo. Container thường báo `BeanCurrentlyInCreationException` nằm trong creation/dependency exception chain.

**Không sửa chỉ bằng** `setAllowCircularReferences(true)`. Early singleton references có thể giúp một số setter/field cycles, nhưng constructor cycle không có đầy đủ instance để đưa vào constructor của nhau. Tương tự, đánh bean `@Lazy` để startup pass có thể chỉ dời cycle tới lần sử dụng.

### Refactor acyclic: đưa dependency chung vào một collaborator

```text
       +--> CommonPolicy <--+
       |                    |
       A                    B
```

```java
@Bean CommonPolicy commonPolicy() {
    return new CommonPolicy();
}
@Bean FixedA fixedA(CommonPolicy policy) {
    return new FixedA(policy);
}
@Bean FixedB fixedB(CommonPolicy policy) {
    return new FixedB(policy);
}
```

Ba method fragments. A và B không cần có reference vòng; logic normalization/dữ liệu chia sẻ nằm ở CommonPolicy. Một green test phải tạo được **cả hai** và gọi behaviors của chúng. Thêm `@Lazy` injection proxy chỉ là một lựa chọn deferred edge có chủ đích khi architecture thật cho phép, không thay thế refactor trong bài tập này.

### Làm thế nào phát hiện vòng bằng stacktrace?

Đọc bean names theo thứ tự resolution:
- Bean A đang tạo.
- A yêu cầu bean B.
- B yêu cầu bean A.
- Bean A đã marked currently in creation.
- Container ném exception.

Có thể có nhiều wrappers; hãy tìm `BeanCurrentlyInCreationException` trong cause chain hoặc Spring diagnostics. Không sử dụng một regex duy nhất trên raw console rồi suy rằng graph đã được sửa khi regex không match.

## 8. Lỗi 4 — thiếu placeholder cấu hình

**Bài toán:** một bean cần cấu hình `billing.endpoint` được truyền qua `Environment`/PropertySources, nhưng property không tồn tại.

```java
@Bean
GatewayEndpoint gatewayEndpoint(
        @Value("${billing.endpoint}") String endpoint) {
    return new GatewayEndpoint(endpoint);
}
```

Đây là bean method fragment. Với `PropertySourcesPlaceholderConfigurer` được đăng ký strict, Spring resolve placeholder trước khi gọi factory method. Nếu không có property, container có thể fail khi refresh.

### Vì sao cần explicit placeholder configurer?

Trong một số `ApplicationContext`, default embedded resolver có thể **giữ nguyên unresolved placeholder** dưới dạng literal nếu không có strict configurer. Spring 6.2 Reference về `@Value` hướng dẫn khai:

```java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

Đây là method fragment **được khai static** trong `@Configuration` để processor có thể được tạo sớm mà không buộc instantiate enclosing configuration class. Với default `ignoreUnresolvablePlaceholders=false`, missing property trở thành lỗi rõ. Demo runnable dùng chính cách này, không dựa vào behavior implicit của một project Boot hay một default resolver.

### Fix: cấp property cho context trước refresh

```java
context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("testOverrides",
                Map.of("billing.endpoint",
                        "https://payments.example.invalid")));
context.refresh();
```

Đây là Java fragment. `MapPropertySource` có thể được add **trước khi `refresh()`** trong test, để configurer nhìn thấy property. `.invalid` là reserved domain suffix nhằm tránh endpoint thật; code không thực hiện HTTP request.

Trong production, property có thể đến từ environment, profile-specific config hoặc secret store. Việc “fix” phải đảm bảo **đúng key**, **đúng source priority**, **đúng deployment profile**, và không đưa API token thật vào logs/test fixtures.

### Default value không phải luôn là fix

```java
@Value("${billing.endpoint:https://localhost.invalid}")
```

Đây là annotation fragment minh họa syntax. Default value có thể phù hợp cho một feature optional; với endpoint phải có ở production, cho default giả có thể che lỗi deployment. Bài tập chọn strict failure + explicit config để minh họa fail fast.

## 9. Lỗi 5 — initialization callback hỏng

Một object có thể construct thành công nhưng fail khi Spring gọi init callback. Ví dụ:

```java
public final class ValidatedPolicy implements InitializingBean {
    private final String code;

    public ValidatedPolicy(String code) {
        this.code = code;
    }

    @Override
    public void afterPropertiesSet() {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException(
                    "policy code must not be blank");
        }
    }
}
```

Đây là class fragment với import `org.springframework.beans.factory.InitializingBean`. Nếu config cung cấp `" "`, callback throw. Spring có thể bọc trong `BeanCreationException` và refresh fail nếu bean eager.

**Fix:** cung cấp giá trị non-blank thực sự và assert business behavior:

```java
@Bean
ValidatedPolicy validatedPolicy() {
    return new ValidatedPolicy("READY");
}
```

Đừng catch/ignore init exception chỉ để context startup pass. Nếu invariant thật bắt buộc, làm vậy cho phép bean tồn tại ở trạng thái không hợp lệ.

### Lazy có thể biến startup fail thành first-use fail

```java
@Bean
@Lazy
ValidatedPolicy validatedPolicy() {
    return new ValidatedPolicy(" ");
}
```

Cùng initialization error vẫn còn. Nếu không có eager bean nào yêu cầu target, `refresh()` có thể pass nhưng `getBean(ValidatedPolicy.class)` fail. Bài kiểm thử **phải truy cập bean** để xác nhận deferred failure. Nếu target là required dependency của eager singleton, Spring vẫn có thể tạo nó ở startup bất chấp annotation lazy.

## 10. Thiết kế một test harness nhỏ và đáng tin

Bài thực hành không cần full Spring Boot startup. Một `AnnotationConfigApplicationContext` được tạo trực tiếp cho mỗi kịch bản:
- `ctx.register(Config.class)` đăng ký class configuration.
- Có thể đưa `MapPropertySource` vào `Environment` **trước refresh**.
- `ctx.refresh()` kích hoạt wiring và eager lifecycle.
- `ctx.getBean(...)` kiểm object đã được chọn/tạo đúng.
- `ctx.close()` dọn lifecycle/resources bằng try-with-resources.

Bộ test dùng các nested static `@Configuration(proxyBeanMethods=false)` và `@Bean` methods explicit, **không bật component scanning** để tránh xuất hiện bean khác làm thay đổi ambiguity. Điều này làm case có thể dự đoán và không lệ thuộc application package của người học.

### Vì sao không tạo tất cả broken configs trong cùng context?

Nếu một context chứa MissingConfig, AmbiguousConfig, CycleConfig và BadInitConfig:
- Một lỗi eager đầu tiên sẽ chặn refresh, bạn không quan sát được lỗi khác.
- Các bean method names có thể trùng, phát sinh overriding conflicts.
- Candidate count thay đổi theo các definitions không liên quan.
- Thứ tự lỗi tùy graph traversal, khó viết regression assertion.

**Mỗi lỗi một context, mỗi fix một context** tạo điều kiện kiểm nguyên nhân độc lập. Bản ứng dụng thật sau đó có thể có integration suite bổ sung xác nhận toàn graph tương tác.

### Exception assertion mạnh hơn việc tìm chữ trong log

```java
BeansException error =
        assertThrows(BeansException.class, ctx::refresh);

NoUniqueBeanDefinitionException cause =
        findCause(error, NoUniqueBeanDefinitionException.class);

assertNotNull(cause);
```

Đây là JUnit fragment; `findCause` được viết trong class `App` của demo. Để tránh catch nhầm, hàm này kiểm chính xác subclass tìm trong cause chain, không chỉ tìm một substring có thể trùng ngẫu nhiên.

## 11. Dự án Maven Java 17+ hoàn chỉnh

Các file và class dưới đây tạo thành **một project runnable**:
- Java 17+ và Maven 3.9+.
- `spring-context:6.2.19` (kéo theo `spring-beans`, `spring-core`).
- `junit-jupiter:5.11.4` và Maven Surefire `3.5.2` cho tests.
- Không có Spring Boot; không có network/database dependency ở runtime.
- `App.main()` chạy các RED/GREEN examples và in kết quả được kiểm.
- `AppTest` chứa các test chuyên biệt cho từng failure/fix.

### 11.1. Cấu trúc

```text
spring-config-red-green/
├── pom.xml
└── src/
    ├── main/java/edu/example/config/ App.java
    └── test/java/edu/example/config/ AppTest.java
```

Trong cây trên, tên file nguồn thực tế là `App.java` và `AppTest.java` **không có khoảng trắng đầu tên**; định dạng đường dẫn nên dùng `src/main/java/edu/example/config/App.java` và `src/test/java/edu/example/config/AppTest.java` khi tạo file.

### 11.2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-config-red-green</artifactId>
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
                <configuration>
                    <mainClass>edu.example.config.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 11.3. src/main/java/edu/example/config/App.java

```java
package edu.example.config;

import java.util.Locale;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

public final class App {

    public interface PaymentPort {
        String charge(String orderId);
    }

    public static final class CardPayment implements PaymentPort {
        @Override
        public String charge(String orderId) {
            return "CARD:" + orderId;
        }
    }

    public static final class BankPayment implements PaymentPort {
        @Override
        public String charge(String orderId) {
            return "BANK:" + orderId;
        }
    }

    public static final class Checkout {
        private final PaymentPort payment;

        public Checkout(PaymentPort payment) {
            this.payment = payment;
        }

        public String charge(String orderId) {
            return payment.charge(orderId);
        }
    }

    public static final class CycleA {
        private final CycleB b;

        public CycleA(CycleB b) {
            this.b = b;
        }
    }

    public static final class CycleB {
        private final CycleA a;

        public CycleB(CycleA a) {
            this.a = a;
        }
    }

    public static final class CommonPolicy {
        public String normalize(String raw) {
            return raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    public static final class FixedA {
        private final CommonPolicy common;

        public FixedA(CommonPolicy common) {
            this.common = common;
        }

        public String run() {
            return "A:" + common.normalize(" OK ");
        }
    }

    public static final class FixedB {
        private final CommonPolicy common;

        public FixedB(CommonPolicy common) {
            this.common = common;
        }

        public String run() {
            return "B:" + common.normalize(" OK ");
        }
    }

    public static final class GatewayEndpoint {
        private final String url;

        public GatewayEndpoint(String url) {
            this.url = url;
        }

        public String url() {
            return url;
        }
    }

    public static final class ValidatedPolicy
            implements InitializingBean {
        private final String code;

        public ValidatedPolicy(String code) {
            this.code = code;
        }

        @Override
        public void afterPropertiesSet() {
            if (code == null || code.isBlank()) {
                throw new IllegalStateException(
                        "policy code must not be blank");
            }
        }

        public String label() {
            return "policy:" + code;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MissingConfig {
        @Bean
        public Checkout checkout(PaymentPort payment) {
            return new Checkout(payment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class MissingFixedConfig {
        @Bean
        public PaymentPort cardPayment() {
            return new CardPayment();
        }

        @Bean
        public Checkout checkout(PaymentPort payment) {
            return new Checkout(payment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AmbiguousConfig {
        @Bean
        public PaymentPort cardPayment() {
            return new CardPayment();
        }

        @Bean
        public PaymentPort bankPayment() {
            return new BankPayment();
        }

        @Bean
        public Checkout checkout(PaymentPort payment) {
            return new Checkout(payment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class QualifiedConfig {
        @Bean
        public PaymentPort cardPayment() {
            return new CardPayment();
        }

        @Bean
        public PaymentPort bankPayment() {
            return new BankPayment();
        }

        @Bean
        public Checkout checkout(
                @Qualifier("cardPayment") PaymentPort payment) {
            return new Checkout(payment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrimaryConfig {
        @Bean
        public PaymentPort cardPayment() {
            return new CardPayment();
        }

        @Bean
        @Primary
        public PaymentPort bankPayment() {
            return new BankPayment();
        }

        @Bean
        public Checkout checkout(PaymentPort payment) {
            return new Checkout(payment);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class CircularConfig {
        @Bean
        public CycleA cycleA(CycleB b) {
            return new CycleA(b);
        }

        @Bean
        public CycleB cycleB(CycleA a) {
            return new CycleB(a);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AcyclicConfig {
        @Bean
        public CommonPolicy commonPolicy() {
            return new CommonPolicy();
        }

        @Bean
        public FixedA fixedA(CommonPolicy common) {
            return new FixedA(common);
        }

        @Bean
        public FixedB fixedB(CommonPolicy common) {
            return new FixedB(common);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PropertyConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public GatewayEndpoint gatewayEndpoint(
                @Value("${billing.endpoint}") String endpoint) {
            return new GatewayEndpoint(endpoint);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class BadInitConfig {
        @Bean
        public ValidatedPolicy validatedPolicy() {
            return new ValidatedPolicy(" ");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class GoodInitConfig {
        @Bean
        public ValidatedPolicy validatedPolicy() {
            return new ValidatedPolicy("READY");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyBadInitConfig {
        @Bean
        @Lazy
        public ValidatedPolicy validatedPolicy() {
            return new ValidatedPolicy(" ");
        }
    }

    public static AnnotationConfigApplicationContext context(
            Class<?> configuration) {
        return context(configuration, Map.of());
    }

    public static AnnotationConfigApplicationContext context(
            Class<?> configuration, Map<String, Object> properties) {
        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            ctx.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("scenarioOverrides",
                            properties));
        }
        ctx.register(configuration);
        return ctx;
    }

    public static <T extends Throwable> T findCause(
            Throwable error, Class<T> expectedType) {
        for (Throwable current = error;
                current != null;
                current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
        }
        throw new AssertionError(
                "Missing cause " + expectedType.getSimpleName(),
                error);
    }

    public static <T extends Throwable> String expectedFailure(
            Class<?> configuration, Class<T> causeType) {
        try (AnnotationConfigApplicationContext ctx =
                context(configuration)) {
            try {
                ctx.refresh();
            } catch (BeansException failure) {
                findCause(failure, causeType);
                return causeType.getSimpleName();
            }
            throw new AssertionError(
                    "Expected refresh failure but context started");
        }
    }

    private static void missingScenario() {
        System.out.println("== missing bean ==");
        System.out.println("bad=" +
                expectedFailure(MissingConfig.class,
                        NoSuchBeanDefinitionException.class));
        try (var ctx = context(MissingFixedConfig.class)) {
            ctx.refresh();
            System.out.println("fixed=" +
                    ctx.getBean(Checkout.class).charge("O-7"));
        }
    }

    private static void ambiguousScenario() {
        System.out.println("== ambiguous beans ==");
        System.out.println("bad=" +
                expectedFailure(AmbiguousConfig.class,
                        NoUniqueBeanDefinitionException.class));
        try (var ctx = context(QualifiedConfig.class)) {
            ctx.refresh();
            System.out.println("qualifier=" +
                    ctx.getBean(Checkout.class).charge("O-7"));
        }
        try (var ctx = context(PrimaryConfig.class)) {
            ctx.refresh();
            System.out.println("primary=" +
                    ctx.getBean(Checkout.class).charge("O-7"));
        }
    }

    private static void circularScenario() {
        System.out.println("== constructor cycle ==");
        System.out.println("bad=" +
                expectedFailure(CircularConfig.class,
                        BeanCurrentlyInCreationException.class));
        try (var ctx = context(AcyclicConfig.class)) {
            ctx.refresh();
            System.out.println("fixed=" +
                    ctx.getBean(FixedA.class).run() + "|" +
                    ctx.getBean(FixedB.class).run());
        }
    }

    private static void propertyScenario() {
        System.out.println("== missing property ==");
        System.out.println("bad=" +
                expectedFailure(PropertyConfig.class,
                        IllegalArgumentException.class));
        try (var ctx = context(PropertyConfig.class, Map.of(
                "billing.endpoint",
                "https://payments.example.invalid"))) {
            ctx.refresh();
            System.out.println("fixed=" +
                    ctx.getBean(GatewayEndpoint.class).url());
        }
    }

    private static void initScenario() {
        System.out.println("== invalid init ==");
        System.out.println("bad=" +
                expectedFailure(BadInitConfig.class,
                        IllegalStateException.class));
        try (var ctx = context(GoodInitConfig.class)) {
            ctx.refresh();
            System.out.println("fixed=" +
                    ctx.getBean(ValidatedPolicy.class).label());
        }
        try (var ctx = context(LazyBadInitConfig.class)) {
            ctx.refresh();
            System.out.println("lazy-refresh=" + ctx.isActive());
            try {
                ctx.getBean(ValidatedPolicy.class);
                throw new AssertionError(
                        "Expected lazy target creation to fail");
            } catch (BeansException failure) {
                System.out.println("lazy-first-use=" +
                        findCause(failure,
                                IllegalStateException.class)
                                .getClass().getSimpleName());
            }
        }
    }

    public static void main(String[] args) {
        missingScenario();
        ambiguousScenario();
        circularScenario();
        propertyScenario();
        initScenario();
        System.out.println("RESULT PASS");
    }
}
```

### 11.4. src/test/java/edu/example/config/AppTest.java

```java
package edu.example.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;

final class AppTest {

    @Test
    void missingDependencyFailsAtRefresh() {
        try (var ctx = App.context(App.MissingConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            App.findCause(error, NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    void registeredDependencyFixesMissingBean() {
        try (var ctx = App.context(App.MissingFixedConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("CARD:O-7",
                    ctx.getBean(App.Checkout.class).charge("O-7"));
        }
    }

    @Test
    void multipleBeansCauseAmbiguity() {
        try (var ctx = App.context(App.AmbiguousConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            NoUniqueBeanDefinitionException cause =
                    App.findCause(error,
                            NoUniqueBeanDefinitionException.class);
            assertEquals(2, cause.getNumberOfBeansFound());
        }
    }

    @Test
    void qualifierSelectsCardImplementation() {
        try (var ctx = App.context(App.QualifiedConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("CARD:O-7",
                    ctx.getBean(App.Checkout.class).charge("O-7"));
        }
    }

    @Test
    void primarySelectsBankImplementation() {
        try (var ctx = App.context(App.PrimaryConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("BANK:O-7",
                    ctx.getBean(App.Checkout.class).charge("O-7"));
        }
    }

    @Test
    void circularConstructorsFailDuringRefresh() {
        try (var ctx = App.context(App.CircularConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            App.findCause(error,
                    BeanCurrentlyInCreationException.class);
        }
    }

    @Test
    void sharedCollaboratorRemovesConstructorCycle() {
        try (var ctx = App.context(App.AcyclicConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("A:ok",
                    ctx.getBean(App.FixedA.class).run());
            assertEquals("B:ok",
                    ctx.getBean(App.FixedB.class).run());
        }
    }

    @Test
    void strictPlaceholderFailureIsVisible() {
        try (var ctx = App.context(App.PropertyConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            IllegalArgumentException cause =
                    App.findCause(error, IllegalArgumentException.class);
            assertTrue(cause.getMessage().contains("billing.endpoint"));
        }
    }

    @Test
    void propertyBeforeRefreshFixesPlaceholder() {
        try (var ctx = App.context(App.PropertyConfig.class,
                Map.of("billing.endpoint",
                        "https://payments.example.invalid"))) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("https://payments.example.invalid",
                    ctx.getBean(App.GatewayEndpoint.class).url());
        }
    }

    @Test
    void invalidInitializingBeanFailsAtRefresh() {
        try (var ctx = App.context(App.BadInitConfig.class)) {
            BeansException error =
                    assertThrows(BeansException.class, ctx::refresh);
            IllegalStateException cause =
                    App.findCause(error, IllegalStateException.class);
            assertEquals("policy code must not be blank",
                    cause.getMessage());
        }
    }

    @Test
    void validatedPolicyRunsAfterCorrection() {
        try (var ctx = App.context(App.GoodInitConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertEquals("policy:READY",
                    ctx.getBean(App.ValidatedPolicy.class).label());
        }
    }

    @Test
    void lazyDoesNotRepairInvalidInitConfiguration() {
        try (var ctx = App.context(App.LazyBadInitConfig.class)) {
            assertDoesNotThrow(ctx::refresh);
            assertTrue(ctx.isActive());
            BeansException error =
                    assertThrows(BeansException.class,
                            () -> ctx.getBean(App.ValidatedPolicy.class));
            App.findCause(error, IllegalStateException.class);
        }
    }
}
```

### 11.5. Lệnh chạy

```bash
mvn -q test
mvn -q compile exec:java
```

Lệnh đầu dùng JUnit/Surefire chạy **12 test methods**: sáu trường hợp cố ý hỏng? Các test được thiết kế thành cặp red/green và một test riêng chứng minh lazy không phải fix. Kỳ vọng report là **12 tests, 0 failures, 0 errors, 0 skipped** nếu files/POM khớp.

Lệnh thứ hai chạy `App.main()`, tái hiện các errors có chủ đích, rồi chạy fixed configurations. **Các dòng do App tự in theo đúng thứ tự:**

```text
== missing bean ==
bad=NoSuchBeanDefinitionException
fixed=CARD:O-7
== ambiguous beans ==
bad=NoUniqueBeanDefinitionException
qualifier=CARD:O-7
primary=BANK:O-7
== constructor cycle ==
bad=BeanCurrentlyInCreationException
fixed=A:ok|B:ok
== missing property ==
bad=IllegalArgumentException
fixed=https://payments.example.invalid
== invalid init ==
bad=IllegalStateException
fixed=policy:READY
lazy-refresh=true
lazy-first-use=IllegalStateException
RESULT PASS
```

Vì các cấu hình hỏng **cố ý throw** trong `refresh()`, Spring có thể phát thêm WARN/stacktrace qua logging backend. Những dòng đó không nằm trong block `System.out.println` của App; **không assert console logs y hệt giữa các máy**. Nhờ fake payment ports và endpoint `.invalid`, code business không mở network connections.

### 11.6. Vì sao kết quả này đáng tin?

- Lỗi missing: test tìm `NoSuchBeanDefinitionException` **trong cause chain**, không chỉ một generic BeanCreationException.
- Lỗi ambiguous: test tìm đúng `NoUniqueBeanDefinitionException` và assert **2** candidates.
- Fix qualifier: output CARD, nên không chỉ biết context active mà còn biết **đúng adapter** được inject.
- Fix primary: output BANK, nên xác minh selection policy.
- Lỗi constructor cycle: tìm `BeanCurrentlyInCreationException` trong chain.
- Fix acyclic: gọi cả FixedA và FixedB; invariant graph mới có hai phụ thuộc cùng hướng vào CommonPolicy.
- Lỗi placeholder: strict configurer và test kiểm property name trong original cause.
- Fix placeholder: cung cấp config trước refresh và kiểm url được inject chính xác.
- Lỗi init: test phân biệt constructor thành công với callback failure và kiểm domain error.
- Fix init: callback pass và object trả label có giá trị.
- Lazy variant: refresh thành công **nhưng first lookup thất bại**, tránh false sense of resolution.

**Giới hạn trung thực:** Không thể khẳng định Maven/JUnit thực sự được chạy trên máy người đọc chỉ từ code trong README. Output là **kết quả dự kiến của đúng các files đã cung cấp**, có thể tự kiểm bằng hai lệnh trên. Nếu Maven dependencies chưa có local cache, lần chạy đầu cần truy cập repository theo policy môi trường.

## 12. Từng bước thao tác trên một sự cố thật

Giả sử deploy phát sinh `UnsatisfiedDependencyException` cho bean `checkout`. Thay vì tự thêm annotations:
1. Ghi version Spring, JDK, profile active và bean type cần inject.
2. Lấy innermost relevant cause; phân biệt NoSuch và NoUnique.
3. Xác định nơi ứng viên được tạo: `@Component`, `@Bean`, `@Import`, FactoryBean, Boot auto-configuration, test override.
4. Kiểm classpath và component scan boundaries.
5. Kiểm `@Profile`/`@Conditional` và dynamic properties.
6. Tạo một configuration test **chỉ chứa những beans liên quan**.
7. Viết test red tái hiện exception type.
8. Sửa một điều theo đúng intent nghiệp vụ.
9. Viết green test lấy bean và gọi hành vi quan trọng.
10. Chạy integration test của cấu hình production dưới profile tương ứng.
11. Kiểm CI/readiness để tránh lỗi tái xuất khi thêm module hoặc đổi config.
12. Không tiết lộ secrets trong logs hoặc test fixtures.

### Trường hợp lỗi chỉ có ở production

Đôi khi test machine không có issue vì:
- Một profile khác làm bean implementation xuất hiện/mất đi.
- Boot auto-config có conditions khác do classpath/profile.
- Environment/property sources có precedence khác.
- Production có thêm module hoặc một custom bean definition.
- Một bean là lazy nên lỗi chưa xuất hiện khi context test chỉ refresh.
- Request/session scope không active trong unit test.
- Một proxy infrastructure (transaction/cache/async) chỉ có ở full app.

Do đó minimal reproduction phải **giữ các điều kiện kích hoạt liên quan**, không loại bỏ yếu tố then chốt để rồi kết luận lỗi “không tái hiện được”.

## 13. Đừng đánh tráo lỗi cấu hình với lỗi của external system

Một `GatewayEndpoint` nhận URL đúng không chứng minh endpoint đang healthy. Ba lớp kiểm tra nên tách:

| Hợp đồng | Ví dụ | Test |
| --- | --- | --- |
| Cấu hình cú pháp | billing.endpoint được cung cấp đúng | Spring context test |
| Bean graph/injection | Checkout có một PaymentPort đúng | Spring context test |
| Adapter tích hợp | HTTP/TLS/auth/timeout xử lý đúng | Integration/contract test với mock server |
| Nghiệp vụ | Invoice được tính đúng | Pure unit test |
| Giao dịch | Rollback/propagation đúng | Context + transaction manager + DB thật |
| Deployment readiness | Critical dependencies có thể phục vụ traffic | Health/readiness/staging test |

Tương tự, chọn `@Primary` đúng trong context test không chứng minh external payment network hoạt động; gọi fake adapter trả chuỗi CARD chỉ chứng minh injection.

**Không biến một mini Spring unit thành test giả lập toàn bộ distributed system.** Hãy giữ mỗi test đúng tầng và tổ chức pipeline kiểm chứng tăng dần.

## 14. Vì sao không nên cố chữa bằng catch và fallbacks mù?

Một số anti-patterns:

```java
try {
    context.getBean(PaymentPort.class);
} catch (Exception ignored) {
    return new CardPayment(); // Bỏ qua bean graph hiện tại.
}
```

Đây là **anti-example fragment** và không phải một method Java hoàn chỉnh (cần enclosing method return type). Nó nguy hiểm vì:
- Che exception nguyên nhân (NoSuch, NoUnique, creation failure).
- Tạo object bên ngoài Spring lifecycle và injection.
- Có thể bỏ qua AOP advice/transaction/caching/security của bean.
- Khi deployment thiếu secrets thật, fallback dẫn tới behavior ngoài ý muốn.

Một anti-pattern khác:

```java
// Giữ config hỏng nhưng đánh @Lazy cho mọi bean chỉ để startup PASS.
```

Hoãn lỗi không sửa invariant. Nếu feature thật sự optional, thiết kế fallback **rõ ràng trong business contract**, log/metric có context, và có test rằng fallback được chọn theo điều kiện đã định.

## 15. Cách xác nhận bản sửa không gây hồi quy

### Bean names/types

Trong integration test, có thể inspect:

```java
String[] paymentBeanNames =
        ctx.getBeanNamesForType(App.PaymentPort.class);
```

Đây là fragment. Cần biết expectation: một bean nếu missing-fix, hai beans nếu qualifier/primary cases. Đừng gọi `getBeansOfType()` tùy tiện trong early lifecycle vì API có thể instantiate các beans/lazy FactoryBean products tùy options. Dùng metadata và deliberate lookup trong isolated tests.

### Profiles/conditionals

Nếu ứng dụng có profiles `dev`, `test`, `prod`, hãy test combinations được deploy. Khi dùng Spring AOT, nhiều quyết định bean presence đã được chốt ở build-time profiles/properties; một runtime toggle không nhất thiết tạo lại graph. Không áp các giả định regular JVM vào AOT native artifact.

### Test callback/proxy

Một test tự `new Checkout(fakePort)` giúp kiểm business logic nhưng không chứng minh Spring đã inject đúng bean. Ngược lại, context test xác minh wiring nhưng không chứng minh `@Transactional` rollback hoặc `@Async` chạy đúng executor nếu chưa có những infrastructure/resources đó. Với requirements đó cần test riêng theo proxy boundary.

### Test lỗi lazy

Nếu fix được tuyên bố cho một bean lazy, test phải thực sự `getBean()` hoặc gọi proxy method sau refresh. Chỉ assert `context.isActive()` làm regression test yếu.

### Test diagnostics an toàn

Nên log:
- Bean name/type.
- Injection point class/member.
- Exception category/root cause.
- Active profiles và tên config keys.

Không log:
- Raw passwords/tokens.
- Dump toàn bộ properties có secrets.
- Request personal data không cần thiết.
- Toàn bộ object graph qua `toString()` nếu có sensitive fields.

## 16. Debug checklist theo symptom

| Symptom | Root class có thể gặp | Điều cần làm |
| --- | --- | --- |
| No qualifying bean of type | `NoSuchBeanDefinitionException` | Register/scan đúng dependency |
| Expected single bean but found two | `NoUniqueBeanDefinitionException` | Qualifier/primary/map theo intent |
| Currently in creation | `BeanCurrentlyInCreationException` | Refactor constructor cycle |
| Could not resolve placeholder | `IllegalArgumentException` qua configurer | Cấp property trước refresh |
| Init method failed | `BeanCreationException` bọc domain cause | Sửa invariant/init input |
| Refresh PASS nhưng getBean lazy FAIL | `BeanCreationException` lúc lookup | Fix target; test first use |
| Context PASS nhưng proxy method FAIL | Target lazy/scoped/underlying method | Inspect actual invocation path |
| Dev PASS, prod FAIL | Profile/classpath/condition khác | Reproduce exact env |
| Unit new SUT PASS, container FAIL | Test không có wiring lifecycle | Add context integration test |
| Chỉ fail khi đồng thời nhiều requests | Lazy first-use/concurrency/remote | Load test + timeout + readiness |
| Added @Primary nhưng còn ambiguous | Có 2 primary hoặc qualifier filter | Inspect all candidate metadata |
| Duplicate bean name override blocked | Registry name collision | Fix names/import/scanning |
| Bean @Lazy vẫn fail startup | Eager dependent forces instantiation | Refactor/defer injection point deliberately |
| Hidden root cause trong wrapper | Multiple nested BeansExceptions | Read chain/related causes |
| Test pass vì caught broad Exception | Assertion quá yếu | Assert subtype and behavior |

### Mười câu hỏi bắt buộc trước khi chốt fix

1. Bean cụ thể nào không tạo được?
2. Nó được register bằng cơ chế nào?
3. Thời điểm failure là refresh hay first-use?
4. Nó cần những dependencies nào?
5. Có bao nhiêu candidates theo type?
6. Annotation/qualifier/primary/condition nào ảnh hưởng selection?
7. PropertySources có key/value hợp lệ trước refresh không?
8. Constructor/init callback nào ném exception?
9. Fix có diễn tả đúng business architecture hay chỉ hoãn lỗi?
10. Integration test nào sẽ fail nếu cùng lỗi quay lại?

## 17. Bài tập mở rộng có hướng dẫn

**Bài 1 — thay đổi không gian ứng viên:** trong AmbiguousConfig, thêm candidate thứ ba. Dự đoán `getNumberOfBeansFound()` và cập nhật test để không hard-code count sai. Không thêm class nếu ứng dụng chỉ cần hai implementations.

**Bài 2 — hai @Primary:** gắn `@Primary` lên cả card và bank. Quan sát ambiguity theo Spring 6.2, sửa bằng đúng một default hoặc qualifier trên consumer.

**Bài 3 — @Fallback:** dùng `@Fallback` cho card trong Spring 6.2, để bank normal. Viết context test nhận bank; kiểm rằng map injection vẫn có hai beans.

**Bài 4 — qualifier semantic:** khai `@Qualifier("regional")` tại provider và injection point. Nếu cả hai adapters có qualifier regional, phân tích vì sao consumer single vẫn ambiguous.

**Bài 5 — Optional dependency:** biến PaymentPort thành `ObjectProvider<PaymentPort>` nhưng cố ý gọi `getObject()` trong business method. Kiểm failure dời thời điểm chứ không tự sửa missing/ambiguity; thiết kế explicit fallback khi nghiệp vụ thật cho phép.

**Bài 6 — property priority:** add hai `MapPropertySource` trước refresh với cùng key nhưng khác priority. Kiểm precedence và không log giá trị secret khi làm thực tế.

**Bài 7 — init callback:** thay `InitializingBean` bằng `jakarta.annotation.PostConstruct`, thêm Jakarta Annotation API cho JDK 17+, giữ cùng invariant. Test lỗi eager, fixed và lazy first-use.

**Bài 8 — constructor circularity:** đặt `@Lazy` trên một injection point để quan sát lazy proxy, sau đó gọi business method thực sự; so với giải pháp A/B phụ thuộc CommonPolicy và lý giải trade-offs.

**Bài 9 — @Profile:** đặt card ở profile dev và bank ở prod. Test hai profiles riêng bằng `ctx.getEnvironment().setActiveProfiles(...)` **trước refresh**. Kiểm trường hợp hai profiles cùng active.

**Bài 10 — raw BeanFactory:** dựng definitions bằng `DefaultListableBeanFactory` và `RootBeanDefinition` rồi tạo missing/ambiguous scenarios. So sánh annotation processing mà `AnnotationConfigApplicationContext` cung cấp.

**Bài 11 — readiness:** mô phỏng một critical lazy bean chưa tạo trong refresh. Thiết kế một startup validation component chỉ kiểm dependency đó và fail trước khi nhận traffic, không kích hoạt mọi bean.

**Bài 12 — test drift:** thay đổi `checkout(PaymentPort)` thành `checkout(@Qualifier("wrongName") PaymentPort)`. Kiểm green test fail với missing qualifier để chứng minh test đủ nhạy.

## 18. Câu hỏi và đáp án tự đánh giá

1. Cấu hình thiếu bean gây gì khi eager consumer yêu cầu dependency? — Dependency resolution fail.
2. Tại sao NoUnique khác NoSuch? — Nhiều candidates so với không có candidate.
3. NoUnique kế thừa NoSuch có hệ quả gì? — Phân loại/catch subtype trước.
4. @Primary tạo bean mới không? — Không, chỉ thay preference trong single injection.
5. @Qualifier có thể dùng trên @Bean method parameter không? — Có.
6. @Fallback có trong Spring từ nhánh nào? — 6.2.
7. Dùng List/Map có hợp lý khi business cần tất cả implementations không? — Có.
8. Tại sao list.get(0) không phải fix tốt? — Phụ thuộc thứ tự, thiếu business intent.
9. Constructor cycle tại sao không dễ giải bằng early singleton reference? — Target object chưa xây xong để truyền qua constructors.
10. Sửa A↔B thành A→C←B giúp gì? — Loại edge vòng và tách shared responsibility.
11. Strict placeholder configurer cần được đăng ký thế nào với Java config? — static @Bean method.
12. Property test cần được add vào Environment khi nào? — Trước refresh.
13. Một endpoint string đúng có chứng minh remote server reachable không? — Không.
14. Callback afterPropertiesSet chạy khi nào? — Trong bean initialization, sau property population.
15. Nếu bean lazy mang callback lỗi, refresh luôn fail không? — Không, nếu target chưa bị buộc tạo.
16. Tại sao test lazy phải gọi getBean? — Để kiểm lỗi deferred.
17. Pure unit test new Checkout có kiểm Spring bean selection không? — Không.
18. Context test có kiểm JDBC rollback tự động không? — Không nếu thiếu transaction manager/resource và use case.
19. Vì sao tạo contexts riêng mỗi case? — Isolate definitions và failure timing.
20. Khi catch BeansException, cần đọc gì? — Bean name, injection point, cause chain.
21. Tại sao không assert exact exception message? — Spring versions/wrappers đổi wording.
22. Trong demo, MissingFixedConfig chọn adapter nào? — Card.
23. QualifiedConfig chọn gì? — Card.
24. PrimaryConfig chọn gì? — Bank.
25. PropertyConfig thiếu key báo root cause nào trong ví dụ? — IllegalArgumentException.
26. BadInitConfig root cause nào? — IllegalStateException.
27. LazyBadInitConfig có thể refresh pass không? — Có.
28. Tại sao không coi startup PASS là xong? — Lazy/first-use hoặc business behavior có thể fail.
29. Red test có pass được khi application lỗi không? — Có, nếu assert đúng expected failure.
30. Green test cần thêm gì ngoài context active? — Behavioral assertion về bean đã sửa.
31. Khi profile khác làm bean mất đi, cần làm gì? — Reproduce build/runtime environment và profile.
32. Có nên log API token để debug property resolution? — Không.
33. @Bean factory method trả object sai type có thể gây gì? — Type/autowiring mismatches.
34. BeanCreationException luôn chỉ một root cause không? — Không, có thể có related causes.
35. Nhiệm vụ thực hành được coi hoàn tất khi nào? — Các lỗi tái hiện đúng, fixes giải đúng nguyên nhân, tests/behaviors đều được kiểm.

## 19. Checklist trước khi đưa thay đổi vào CI

- [ ] Test red reproduction cho từng lỗi có thể quay lại.
- [ ] Test green behavior cụ thể sau sửa.
- [ ] Không so nguyên văn full Spring logs.
- [ ] Không gộp tất cả broken configurations vào một context.
- [ ] Đọc exception subtype sâu trong causes, không chỉ outer class.
- [ ] Phân biệt missing bean, ambiguous bean và circular graph.
- [ ] @Qualifier đặt đúng injection point khi cần một adapter riêng.
- [ ] @Primary chỉ định một default rõ.
- [ ] Không lạm dụng @Fallback, @Order hoặc list.get(0) để che ambiguity.
- [ ] @Value placeholders cần strict validation khi property là bắt buộc.
- [ ] Thêm property vào Environment trước refresh.
- [ ] Không đưa secret thật vào test fixtures/logs.
- [ ] Lifecycle validation phải thật sự pass sau fix.
- [ ] Có first-use test khi cấu hình lazy.
- [ ] Không coi @Lazy là universal repair.
- [ ] Có integration test cho production profile/conditions/scan.
- [ ] Không sửa runtime bean registry khi phục vụ traffic để che config issue.
- [ ] Có readiness/fail-fast policy cho critical beans.
- [ ] Test được cả context wiring lẫn business behavior ở tầng thích hợp.
- [ ] Các kịch bản test chạy độc lập, không chia sẻ static mutable state.
- [ ] Các resource/context được đóng sau mỗi test.
- [ ] Không kết luận external system success từ fake adapter output.

## 20. Tài liệu tham khảo chính thức

- Spring Framework 6.2 Reference — The IoC Container: https://docs.spring.io/spring-framework/reference/6.2/core/beans.html
- Spring Framework 6.2 Reference — Dependency Injection: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html
- Spring Framework 6.2 Reference — Autowired Qualifiers: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html
- Spring Framework 6.2 Reference — @Primary and @Fallback: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html
- Spring Framework 6.2 Reference — @Value and Placeholder Configurer: https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/value-annotations.html
- Spring Framework 6.2 Reference — Lazy-initialized Beans: https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-lazy-init.html
- Spring Framework 6.2 Reference — Bean Lifecycle: https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html
- Spring Framework 6.2 Javadoc — BeanCreationException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCreationException.html
- Spring Framework 6.2 Javadoc — NoUniqueBeanDefinitionException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html
- Spring Framework 6.2 Javadoc — BeanCurrentlyInCreationException: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/BeanCurrentlyInCreationException.html
- Spring Framework 6.2 Javadoc — PropertySourcesPlaceholderConfigurer: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/support/PropertySourcesPlaceholderConfigurer.html
- Spring Framework 6.2 Javadoc — InitializingBean: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/InitializingBean.html
- JUnit 5 User Guide: https://junit.org/junit5/docs/current/user-guide/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comprehensive-practice-009">Đánh giá thiết kế theo khả năng kiểm thử và mức độ ghép nối</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá thiết kế theo khả năng kiểm thử và mức độ ghép nối trong Spring Core Container

## 1. Vấn đề cần đánh giá

Một ứng dụng Spring có thể khởi động thành công mà vẫn khó kiểm thử: service tự tạo HTTP client hoặc repository bằng `new`, đọc `System.getenv` trong business method, lấy bean qua `ApplicationContext.getBean`, dùng đồng hồ hệ thống và lưu shared state ở biến static. Khi đó, mỗi test nhỏ đều cần hạ tầng thật hoặc những bước setup phức tạp; thay một adapter có thể kéo theo sửa nhiều modules.

**Khả năng kiểm thử (testability)** là mức độ dễ kiểm soát đầu vào và dependencies, quan sát kết quả/side effects, mô phỏng lỗi, cô lập test và tái hiện kết quả. **Mức độ ghép nối (coupling)** là mức độ một thành phần biết/phụ thuộc vào type, hành vi, timing, trạng thái hoặc sự thay đổi của thành phần khác.

Hai thuộc tính có liên hệ nhưng không đồng nhất. Một constructor có bốn interface parameters vẫn biểu thị bốn dependencies; nó chỉ làm việc thay thế implementations và nhìn rõ hợp đồng dễ hơn. Một class chỉ có một field `ApplicationContext` có thể giấu rất nhiều dependencies bên trong các `getBean(...)` calls.

Bài này sử dụng **Spring Framework 6.2.19, Java 17, Maven và JUnit Jupiter**, đánh giá hai cách thiết kế cùng một use case checkout:

- **LegacyCheckout** tự tạo toàn bộ collaborators và khóa chặt implementation choices.
- **CheckoutService** nhận các ports qua constructor; `@Configuration` là nơi Spring lắp ráp production graph, còn JUnit có thể tự tạo object với fake dependencies mà không cần Spring context.

Có ví dụ Maven hoàn chỉnh, unit tests và Spring TestContext tests; không cần database, Spring Boot hoặc Mockito.

## 2. Những câu hỏi phải trả lời trong review

1. **Dependencies được tạo ở đâu?** Trong business method, constructor, static locator hay composition root?
2. **Có thể thay collaborator không?** Nếu muốn Catalog trả SKU vắng, hoặc ReceiptSink ném lỗi, có thể đưa fake vào test mà không sửa source service?
3. **Side effects có quan sát được không?** Test kiểm tra receipt thực sự được gửi qua sink hay chỉ xem log?
4. **Thay backend lan bao xa?** Đổi Catalog từ in-memory sang REST có khiến CheckoutService và các tests về thuế phải sửa?
5. **Vòng dependency có xuất hiện không?** Đồ thị có A→B→A ở compile-time, bean construction hoặc runtime callbacks?
6. **Chi phí chạy test là bao nhiêu?** Một phép tính thuần có buộc boot cả ApplicationContext và DB không?
7. **Tests có đúng tầng không?** Unit test kiểm tra phép tính, wiring test kiểm tra chọn đúng bean, adapter integration test kiểm tra transport/transaction.
8. **Có overengineering không?** Interface/factory/provider thêm vào có tạo seam có giá trị hay chỉ làm code nhiều lớp hơn?

Đánh giá phải dựa vào đường code và tests kiểm chứng được, không chỉ vào số lượng annotations.

## 3. Coupling: phân loại theo nguyên nhân

| Dạng | Dấu hiệu | Hậu quả thường gặp |
| --- | --- | --- |
| Concrete implementation | Service tự `new SqlCatalog(...)` | Khó đổi adapter, unit test phụ thuộc I/O |
| Contract coupling | Service nhận `Catalog` interface | Vẫn có dependency, nhưng consumer không chọn implementation |
| Spring framework coupling | Domain dùng `BeanFactory`/`ApplicationContextAware` | Unit test phải giả container hoặc sử dụng Spring |
| Temporal coupling | Phải gọi `setClient`, `initialize` đúng thứ tự | Object tồn tại trong trạng thái chưa sẵn sàng |
| Global-state coupling | Static mutable registry, system properties | Tests ảnh hưởng nhau, khó chạy song song |
| Operational coupling | DB, network, broker hoặc secret service bắt buộc | CI chậm, flaky và khó cô lập |
| Cyclic coupling | A→B→C→A | Khó tạo object, khó phân tách trách nhiệm |
| Data coupling | Một service nhận giant mutable context object | Phụ thuộc quá nhiều fields không cần |
| Change coupling | Đổi adapter phải sửa nhiều modules | Boundary chưa phản ánh đúng trục biến đổi |

**Đổi concrete class thành interface không tự xóa coupling.** Interface nhỏ, ổn định và diễn đạt đúng nghiệp vụ mới có thể giúp hạn chế mức thay đổi lan truyền.

### Ba loại đồ thị phải phân biệt

~~~text
Static type graph:
  CheckoutService -> Catalog interface

Spring bean creation graph:
  checkoutService -> catalog bean

Runtime interaction graph:
  CheckoutService.checkout -> Catalog.priceCents
~~~

`@Lazy` hoặc `ObjectProvider` có thể hoãn một cạnh trong bean creation graph, nhưng không xóa logical dependency hay runtime call edge. `BeanDefinition.getDependsOn()` cũng không phải danh sách đầy đủ mọi constructor dependencies; nó chủ yếu lưu explicit init ordering.

## 4. Cohesion: một lớp có mấy lý do độc lập để sửa?

Cohesion là mức độ các nhiệm vụ trong một module phục vụ một trách nhiệm chung. Một `CheckoutManager` vừa tính thuế, tạo SQL connection, gửi email, render PDF và thiết lập retry HTTP có nhiều lý do không liên quan để thay đổi.

Refactor hợp lý:

~~~text
CheckoutService
   ├── Catalog         — tra giá theo SKU
   ├── TaxPolicy       — tính thuế theo subtotal
   ├── ReceiptSink     — lưu/phát hành receipt
   └── IdSource        — cung cấp định danh

Spring Wiring
   ├── chọn implementations
   ├── quản lý bean lifecycle
   └── cung cấp các giá trị cấu hình
~~~

CheckoutService vẫn điều phối use case nên có bốn direct dependencies, không phải fan-out bằng không. Nếu coordination tiến hóa thành nhiều steps/compensations, có thể cần một orchestrator riêng; không thêm lớp chỉ để đạt một ngưỡng số tham số giả định.

## 5. Testability: những thành tố có thể quan sát

| Tiêu chí | Câu hỏi | Bằng chứng tốt |
| --- | --- | --- |
| Controllability | Test quyết định dữ liệu từ collaborator được không? | Fake Catalog/TaxPolicy |
| Observability | Test xem được receipt/side effect không? | Return value + RecordingSink |
| Determinism | Test lặp lại có cùng kết quả không? | Fixed ID, không clock/network |
| Isolation | Test business có chạy không cần Spring? | JUnit với `new CheckoutService` |
| Setup cost | Phải tạo bao nhiêu resources? | Vài objects/fakes |
| Failure simulation | Có dễ thử missing SKU, timeout, invalid value? | Fake ném exception rõ |
| Cleanup | Có tài nguyên/shared state để lại không? | Local fixtures, scoped contexts |
| Diagnostic quality | Failure có chỉ ra đúng tầng sai? | Separate unit/wiring/adapter tests |
| Contract robustness | Refactor internal làm tests fail vô cớ không? | Assert behavior/effects thay vì private calls |
| Operational coverage | Infrastructure thật có được test riêng không? | Adapter integration/contract tests |

Một lớp có thể mock được nhưng vẫn khó test vì nhiều preconditions hoặc non-deterministic side effects. Testability không chỉ là có interface.

## 6. Chọn abstractions ở những boundary có ích

Tạo port interface khi implementation có thể thay theo môi trường hoặc cần fake để mô phỏng failure. Ví dụ `Catalog.priceCents(sku)` là capability vừa đủ để checkout không biết DB, HTTP, cache hoặc file.

Không nhất thiết thêm interface cho mọi Java record/domain value. `Receipt` bất biến không cần `IReceipt` chỉ vì team có convention “mọi class phải có interface”. `Clock` sẵn có trong JDK thường đủ để điều khiển thời gian thay vì tự chế thêm một wrapper nếu không cần.

**Hướng dependency hợp lý:**

~~~text
example.domain       Catalog, TaxPolicy, ReceiptSink, Receipt, CheckoutService
       ▲
       │ implements or assembles
example.infrastructure InMemoryCatalog / JdbcCatalog / BrokerSink
       ▲
       │ composition
example.bootstrap     Spring @Configuration
~~~

Business/domain module không nên import REST client SDK, persistence adapter hoặc Spring web controller classes nếu có thể giữ boundary thuần Java.

## 7. Constructor injection làm required dependencies minh bạch

~~~java
public CheckoutService(
        Catalog catalog, TaxPolicy tax,
        ReceiptSink sink, IdSource ids) {

    this.catalog = Objects.requireNonNull(catalog);
    this.tax = Objects.requireNonNull(tax);
    this.sink = Objects.requireNonNull(sink);
    this.ids = Objects.requireNonNull(ids);
}
~~~

Đây là constructor trích từ mã đầy đủ. Các fields `final` và null checks làm object không thể được tạo mà thiếu required collaborators.

Đổi lại, test có thể tạo fake dependencies bằng lambda, không cần SpringExtension hay mock framework. Spring `@Bean` method vẫn có thể gọi constructor ấy bằng dependencies được container resolve.

### Khi nào field injection bất tiện?

~~~java
@Service
class BillingService {
    @Autowired private PaymentGateway gateway;
}
~~~

Một unit test tự `new BillingService()` có field gateway chưa được inject. Người viết test có thể phải dùng reflection hoặc Spring context để setup. Field injection được framework hỗ trợ nhưng ít thích hợp khi muốn immutable required dependencies và test không cần container.

### Service locator che dependency

~~~java
class BillingService {
    private final ApplicationContext context;

    void pay() {
        PaymentGateway gateway =
                context.getBean(PaymentGateway.class);
    }
}
~~~

Đây là snippet khái niệm. Constructor chỉ nhìn thấy ApplicationContext, còn business dependency bị đẩy thành lookup runtime. Infrastructure factory hoặc plugin mechanism đôi lúc cần BeanFactory; business logic thông thường nên nhận port trực tiếp.

## 8. Test doubles và cách dùng hợp lý

| Loại | Dùng để | Ví dụ |
| --- | --- | --- |
| Stub | Cung cấp response cố định | `sku -> 700` |
| Fake | Có implementation đơn giản trong memory | `InMemoryCatalog` |
| Spy | Ghi lại một side effect | `RecordingSink.saved()` |
| Mock | Xác nhận interactions mong đợi | Xác nhận một gateway được gọi theo contract |
| Dummy | Đáp ứng chữ ký nhưng không được dùng | IdSource ở test invalid quantity |

Không cần thư viện Mockito cho ví dụ này: bốn ports đều là functional interfaces và lambdas đủ thể hiện behavior. Tuy nhiên, **đừng dùng fake làm bằng chứng adapter thật hoạt động**. JDBC catalog/remote gateway cần contract và integration tests riêng.

Ưu tiên assert kết quả nghiệp vụ và effects quan trọng. Tránh assert số lời gọi vào helper nội bộ không thuộc contract, vì refactor hợp lệ có thể làm tests mong manh.

## 9. Bài toán thực hành checkout

Hợp đồng:

- Catalog trả `1.000` cents cho SKU `BOOK`.
- Checkout quantity `2`.
- TaxPolicy 10% trên subtotal.
- IdSource trả `demo-001` trong fixture.
- ReceiptSink lưu receipt.

Kết quả:

~~~text
subtotal = 1000 * 2 = 2000
tax      = 2000 * 10 / 100 = 200
total    = 2200 cents
id       = demo-001
~~~

`ConstantIds("demo-001")` chỉ dùng để demo/test deterministic; không sử dụng trong production vì nhiều receipts sẽ có trùng ID. Production cần ID strategy đáp ứng uniqueness/idempotency.

### Thiết kế legacy

`LegacyCheckout` tự dựng InMemoryCatalog, TenPercentTax, RecordingSink và ConstantIds trong constructor. Khi cần test giá 700 hoặc catalog bị lỗi, caller không thể đưa fake vào mà không sửa code của LegacyCheckout.

### Thiết kế DI

`CheckoutService` nhận `Catalog`, `TaxPolicy`, `ReceiptSink` và `IdSource` qua constructor. Test có thể truyền scenario khác. Spring configuration quyết định bản implementation production.

Hai thiết kế có thể cùng output ở một kịch bản, nhưng **không tương đương về test seam và mức coupling tới implementation**.

## 10. Dự án runnable: cấu trúc và dependencies

Dự án Java 17/Spring Framework 6.2.19/JUnit Jupiter:

~~~text
spring-testable-design/
├── pom.xml
└── src/
    ├── main/java/example/DesignDemo.java
    └── test/java/example/
        ├── CheckoutUnitTests.java
        └── CheckoutSpringTests.java
~~~

Có một ứng dụng CLI `DesignDemo.main` và **năm JUnit tests**: ba unit tests thuần Java, hai Spring wiring tests.

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-testable-design</artifactId>
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
            <artifactId>spring-test</artifactId>
            <version>6.2.19</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
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
                <version>3.5.3</version>
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

`spring-test` chứa SpringExtension và `@SpringJUnitConfig`. `spring-context` chứa BeanFactory/JavaConfig. JUnit Jupiter/Surefire chạy tests, không cần Mockito hay Spring Boot.

## 11. Toàn bộ src/main/java/example/DesignDemo.java

~~~java
package example;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class DesignDemo {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(Wiring.class)) {

            CheckoutService checkout =
                    context.getBean(CheckoutService.class);
            RecordingSink sink =
                    context.getBean(RecordingSink.class);

            Receipt receipt = checkout.checkout("BOOK", 2);
            Receipt legacy =
                    new LegacyCheckout().checkout("BOOK", 2);

            require(receipt.equals(legacy),
                    "Legacy and DI results differ");
            require(sink.saved().size() == 1,
                    "Exactly one receipt expected");
            require(sink.saved().get(0) == receipt,
                    "Sink must hold same emitted object");
            require(context.isSingleton("checkoutService"),
                    "Service must be singleton");
            require(checkout == context.getBean(CheckoutService.class),
                    "Singleton reference identity");

            System.out.println("Receipt total: "
                    + receipt.totalCents());
            System.out.println("Legacy agrees: "
                    + receipt.equals(legacy));
            System.out.println("Sink records: "
                    + sink.saved().size());
            System.out.println("Spring singleton: "
                    + context.isSingleton("checkoutService"));
            System.out.println("Verification: PASS");
        }
    }

    private static void require(boolean ok, String reason) {
        if (!ok) {
            throw new IllegalStateException(reason);
        }
    }

    public interface Catalog {
        int priceCents(String sku);
    }

    public interface TaxPolicy {
        int taxCents(int subtotalCents);
    }

    public interface ReceiptSink {
        void save(Receipt receipt);
    }

    public interface IdSource {
        String next();
    }

    public record Receipt(
            String id,
            String sku,
            int quantity,
            int subtotalCents,
            int taxCents,
            int totalCents) {
    }

    public static final class InMemoryCatalog implements Catalog {
        private final Map<String, Integer> prices;

        public InMemoryCatalog(Map<String, Integer> prices) {
            this.prices = Map.copyOf(
                    Objects.requireNonNull(prices));
        }

        @Override
        public int priceCents(String sku) {
            Integer amount = prices.get(sku);
            if (amount == null) {
                throw new IllegalArgumentException(
                        "Unknown SKU: " + sku);
            }
            return amount;
        }
    }

    public static final class TenPercentTax implements TaxPolicy {
        @Override
        public int taxCents(int subtotalCents) {
            return Math.toIntExact(
                    ((long) subtotalCents * 10) / 100);
        }
    }

    public static final class RecordingSink implements ReceiptSink {
        private final CopyOnWriteArrayList<Receipt> values =
                new CopyOnWriteArrayList<>();

        @Override
        public void save(Receipt receipt) {
            values.add(Objects.requireNonNull(receipt));
        }

        public List<Receipt> saved() {
            return List.copyOf(values);
        }
    }

    public static final class ConstantIds implements IdSource {
        private final String id;

        public ConstantIds(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public String next() {
            return id;
        }
    }

    public static final class CheckoutService {
        private final Catalog catalog;
        private final TaxPolicy tax;
        private final ReceiptSink sink;
        private final IdSource ids;

        public CheckoutService(
                Catalog catalog,
                TaxPolicy tax,
                ReceiptSink sink,
                IdSource ids) {
            this.catalog = Objects.requireNonNull(catalog);
            this.tax = Objects.requireNonNull(tax);
            this.sink = Objects.requireNonNull(sink);
            this.ids = Objects.requireNonNull(ids);
        }

        public Receipt checkout(String sku, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Quantity must be positive");
            }
            int unit = catalog.priceCents(sku);
            int subtotal = Math.multiplyExact(unit, quantity);
            int computedTax = tax.taxCents(subtotal);
            int total = Math.addExact(subtotal, computedTax);

            Receipt receipt = new Receipt(
                    ids.next(), sku, quantity,
                    subtotal, computedTax, total);
            sink.save(receipt);
            return receipt;
        }
    }

    // Anti-pattern để đối chiếu: khóa collaborators trong constructor.
    public static final class LegacyCheckout {
        private final CheckoutService delegate;

        public LegacyCheckout() {
            delegate = new CheckoutService(
                    new InMemoryCatalog(Map.of("BOOK", 1000)),
                    new TenPercentTax(),
                    new RecordingSink(),
                    new ConstantIds("demo-001"));
        }

        public Receipt checkout(String sku, int quantity) {
            return delegate.checkout(sku, quantity);
        }
    }

    // Composition root: chọn implementations, không nằm trong domain.
    @Configuration(proxyBeanMethods = false)
    public static class Wiring {
        @Bean
        public Catalog catalog() {
            return new InMemoryCatalog(Map.of("BOOK", 1000));
        }

        @Bean
        public TaxPolicy taxPolicy() {
            return new TenPercentTax();
        }

        @Bean
        public RecordingSink recordingSink() {
            return new RecordingSink();
        }

        @Bean
        public IdSource idSource() {
            return new ConstantIds("demo-001");
        }

        @Bean
        public CheckoutService checkoutService(
                Catalog catalog,
                TaxPolicy taxPolicy,
                ReceiptSink receiptSink,
                IdSource idSource) {
            return new CheckoutService(
                    catalog, taxPolicy, receiptSink, idSource);
        }
    }
}
~~~

### Giải thích wiring

`@Configuration(proxyBeanMethods = false)` là lite inter-bean method call behavior: `@Bean` factory methods không được proxy để chặn Java direct calls. Ví dụ **không gọi trực tiếp** `catalog()` hay `recordingSink()` bên trong `checkoutService` method; Spring resolve bốn factory method parameters bằng BeanFactory, nên tất cả trỏ tới beans được quản lý.

Vì `recordingSink()` khai báo concrete return type `RecordingSink`, test có thể lấy bean này và quan sát receipt. Consumer chỉ cần `ReceiptSink` interface. Nếu sau này có hai ReceiptSink candidates, phải chọn bằng `@Qualifier`/`@Primary` thay vì phụ thuộc registration order.

## 12. src/test/java/example/CheckoutUnitTests.java

~~~java
package example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import example.DesignDemo.Catalog;
import example.DesignDemo.CheckoutService;
import example.DesignDemo.IdSource;
import example.DesignDemo.Receipt;
import example.DesignDemo.ReceiptSink;
import example.DesignDemo.RecordingSink;
import example.DesignDemo.TaxPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutUnitTests {

    @Test
    void calculatesUsingReplaceablePorts() {
        Catalog catalog = sku -> 700;
        TaxPolicy tax = subtotal -> 35;
        List<Receipt> observed = new ArrayList<>();
        ReceiptSink sink = observed::add;
        IdSource ids = () -> "T-1";

        CheckoutService service = new CheckoutService(
                catalog, tax, sink, ids);
        Receipt actual = service.checkout("NOTE", 3);

        Receipt expected = new Receipt(
                "T-1", "NOTE", 3, 2100, 35, 2135);
        assertEquals(expected, actual);
        assertEquals(List.of(expected), observed);
    }

    @Test
    void invalidQuantityDoesNotTouchCollaborators() {
        AtomicInteger calls = new AtomicInteger();

        Catalog catalog = sku -> {
            calls.incrementAndGet();
            return 100;
        };
        TaxPolicy tax = subtotal -> {
            calls.incrementAndGet();
            return 10;
        };
        ReceiptSink sink = receipt -> {
            calls.incrementAndGet();
        };
        IdSource ids = () -> {
            calls.incrementAndGet();
            return "unused";
        };

        CheckoutService service = new CheckoutService(
                catalog, tax, sink, ids);
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout("BOOK", 0));
        assertEquals(0, calls.get());
    }

    @Test
    void unknownSkuDoesNotSaveReceipt() {
        Catalog catalog = sku -> {
            throw new IllegalArgumentException("Unknown SKU");
        };
        TaxPolicy tax = subtotal -> 0;
        RecordingSink sink = new RecordingSink();
        IdSource ids = () -> "unused";

        CheckoutService service = new CheckoutService(
                catalog, tax, sink, ids);
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout("MISSING", 1));
        assertTrue(sink.saved().isEmpty());
    }
}
~~~

**Ba unit tests không sử dụng Spring context**. Test 1 cung cấp Catalog giá 700 và TaxPolicy 35 để tạo một scenario không phụ thuộc production implementations. Test 2 chứng minh invalid quantity không gây side effect. Test 3 tạo lỗi Catalog rõ ràng rồi xác nhận sink không lưu receipt.

Đây là minh họa tính thay thế dependency, không phải bằng chứng Catalog adapter thật hoặc transaction thật đúng. Những adapter đó cần tests riêng.

## 13. src/test/java/example/CheckoutSpringTests.java

~~~java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import example.DesignDemo.Catalog;
import example.DesignDemo.CheckoutService;
import example.DesignDemo.Receipt;
import example.DesignDemo.ReceiptSink;
import example.DesignDemo.RecordingSink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(DesignDemo.Wiring.class)
class CheckoutSpringTests {

    @Autowired
    CheckoutService checkout;

    @Autowired
    RecordingSink sink;

    @Autowired
    ApplicationContext context;

    @Test
    void selectsBeansAndSingletonScope() {
        assertSame(checkout,
                context.getBean("checkoutService",
                        CheckoutService.class));
        assertSame(sink,
                context.getBean(ReceiptSink.class));
        assertTrue(context.isSingleton("checkoutService"));
        assertEquals(1,
                context.getBeansOfType(Catalog.class).size());
    }

    @Test
    void emitsReceiptThroughWiredSink() {
        int before = sink.saved().size();
        Receipt receipt = checkout.checkout("BOOK", 2);

        Receipt expected = new Receipt(
                "demo-001", "BOOK", 2, 2000, 200, 2200);
        assertEquals(expected, receipt);
        assertEquals(before + 1, sink.saved().size());
        assertSame(receipt, sink.saved().get(before));
    }
}
~~~

`@SpringJUnitConfig` tích hợp `SpringExtension` của JUnit Jupiter và `@ContextConfiguration`. Hai tests này kiểm chứng **thứ unit test thuần không kiểm chứng được**: Spring có đăng ký factory methods, chọn đúng ReceiptSink candidate, tạo singleton CheckoutService và nối đúng sink hay không.

`RecordingSink` là mutable singleton có thể được chia sẻ trong cached TestContext. Test tính `before` rồi assert tăng một thay vì giả định sink lúc bắt đầu rỗng. Với JUnit parallel execution tùy chỉnh và cùng shared context, cần thêm isolation/synchronization; demo giả định chạy tests tuần tự mặc định.

## 14. Lệnh chạy và đầu ra kỳ vọng

Chạy toàn bộ kiểm thử:

~~~bash
mvn test
~~~

Chạy riêng unit hoặc wiring tests:

~~~bash
mvn -Dtest=CheckoutUnitTests test
mvn -Dtest=CheckoutSpringTests test
~~~

Nếu dependencies/files đúng như trên, kỳ vọng:

~~~text
CheckoutUnitTests: 3 tests passed
CheckoutSpringTests: 2 tests passed
Total: 5 tests, 0 failures, 0 errors, 0 skipped
~~~

Đây là **tóm tắt logic**, không phải exact Maven Surefire console log hoặc bằng chứng đã thực thi. Để xác nhận kết quả thực, đọc `target/surefire-reports/` sau khi chạy trên JDK 17.

Chạy chương trình CLI:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.DesignDemo
~~~

Output do `System.out.println` dự kiến:

~~~text
Receipt total: 2200
Legacy agrees: true
Sink records: 1
Spring singleton: true
Verification: PASS
~~~

Mã kiểm tra legacy và DI tạo hai Receipt records bằng nhau ở scenario chuẩn; `RecordingSink` trong Spring context giữ đúng object receipt trả về; bean tên `checkoutService` có singleton scope.

**Hai thiết kế có output giống nhau trong kịch bản này không có nghĩa chúng có cùng testability.** Để thử Catalog NOTE giá 700, DI code chỉ cần stub Catalog và không sửa CheckoutService, còn LegacyCheckout hardcode BOOK trong constructor.

## 15. Scorecard trước/sau thiết kế

| Tiêu chí | LegacyCheckout | CheckoutService + Wiring |
| --- | --- | --- |
| Required deps | Ẩn implementation choices trong constructor | Bốn ports explicit |
| Fake Catalog | Không inject được từ API | Lambda Catalog |
| Fake TaxPolicy | Không inject được từ API | Lambda TaxPolicy |
| Quan sát receipt save | Sink nội bộ bị khóa | Inject RecordingSink/spy |
| Thay ID source | Sửa class | Inject IdSource |
| Unit test không Spring | Có thể gọi nhưng khó tạo scenario mới | Tạo POJO với fakes |
| Container integration | LegacyCheckout không cần Spring | Wiring được test bằng Spring TestContext |
| Đổi backend Catalog | Sửa LegacyCheckout | Đổi adapter/Bean configuration |
| Runtime fan-out | Bốn collaborators | Vẫn bốn collaborators |
| Abstraction cost | Ít files/interfaces | Thêm bốn ports và composition |
| Rủi ro | Hardcoded dependencies | Over-abstraction nếu use case quá nhỏ |

Thiết kế DI tốt hơn trong bài toán cần đa dạng implementation/test scenarios, **không phải vì số import ít hơn**. Với một pure function không external side effects, việc bọc mọi phép tính trong interface có thể không có lợi.

## 16. Metrics và rubric: dùng làm tín hiệu, không thành mục tiêu

Các chỉ số bổ trợ:

- **Fan-out/efferent coupling**: số outgoing dependencies từ module. CheckoutService có bốn domain ports; đây là con số mô tả, không là lỗi tự thân.
- **Afferent coupling**: số modules phụ thuộc vào một package. Domain port ổn định có thể có nhiều inbound dependencies hợp lệ.
- **Change amplification**: thay một adapter khiến bao nhiêu modules phải sửa theo. Đo qua tình huống thay đổi có thật hoặc PR history.
- **Cycles/SCC**: phát hiện vòng giữa packages hoặc beans; phân biệt constructor cycle với runtime callback cycle.
- **Setup cost**: thời gian/nguồn lực bắt buộc để chạy một test. Đo từ CI thật.
- **Flake rate**: tần suất test pass/fail không do source change; không đoán từ cảm giác.
- **Test robustness**: sau một refactor nội bộ không đổi contract, bao nhiêu test phải sửa.

Một metric package thường gặp là \(\displaystyle I=C_e/(C_a+C_e)\), với `C_e` outgoing dependencies và `C_a` incoming. Mẫu số bằng 0 thì undefined. Giá trị này **không đo trực tiếp testability**.

### Rubric 0–2 để thảo luận

| Tiêu chí | 0 | 1 | 2 |
| --- | --- | --- | --- |
| Dependency visibility | Hidden | Một số hidden | Explicit required dependencies |
| Substitutability | Hardcoded | Một vài seams | Có seams ở boundaries quan trọng |
| Determinism | External uncontrolled | Có fixtures một phần | Tests giữ input/output ổn định |
| Isolation | Domain test đòi full stack | Một số slices | Plain Java unit tests |
| Observability | Không đo được effect | Phải qua logs | Return values + sink observable |
| Architecture direction | Domain import adapters | Có vi phạm nhỏ | Infrastructure phụ thuộc domain ports |
| Cycles | Nhiều vòng khó hiểu | Có vòng deferred | Graph rõ, không cần workaround |
| Change amplification | Chạm nhiều modules | Chạm một vài | Thay adapter thường không sửa domain |

Nếu cộng tổng, đó chỉ là **rubric nội bộ cho buổi review**, không phải tiêu chuẩn Spring hay phép đo khách quan tuyệt đối. Một rủi ro security/lifecycle nghiêm trọng không được phép bị “bù điểm” bởi bảy tiêu chí khác.

## 17. Những anti-pattern thường phát sinh trong Spring

### @Lazy để giấu vòng

A→B→A còn là logical cycle ngay cả khi `@Lazy` hoãn resolution đủ để refresh thành công. Nếu A và B cần gọi lẫn nhau thường xuyên, cân nhắc coordinator hoặc truyền dữ liệu qua method arguments.

### ApplicationContextAware ở business layer

Business service nhận container làm service locator khiến dependencies khó thấy và unit tests phụ thuộc Spring. Chỉ dùng container-aware APIs ở infrastructure nơi thực sự cần dynamic bean lookup.

### @Autowired concrete class khi bean bị JDK proxy

Một bean AOP có thể expose JDK interface proxy không assignable sang class implementation. Inject contract interface ở đúng boundary, hoặc kiểm tra class proxy requirements nếu có lý do thật.

### @DirtiesContext ở mọi test

Spring TestContext cache có thể reuse ApplicationContext. `@DirtiesContext` cho mỗi test làm chậm suite và che design shared-state. Chỉ dùng khi context bị thay đổi không thể reset hợp lệ.

### @SpringBootTest cho pure logic

Full app bootstrap làm tests chậm/fragile khi chỉ cần tính toán. JUnit thuần với constructors thường đủ. Ngược lại, unit tests không kiểm chứng actual transaction/AOP/bean wiring.

### Quá nhiều mocks

Nếu test một phép tính cần 20 mocks và nhiều stubbing statements, hãy xem cohesion, interface surface và unit boundary trước khi thêm mocks nữa.

### @Value trong domain class

Đọc raw property ở mọi service gây coupling tới môi trường/config precedence và khó test. Một typed configuration bean/record được validate ở composition layer thường rõ hơn.

## 18. Transaction, async, profiles và scope cần test ở đúng tầng

**Transaction:** `@Transactional` trong proxy-based Spring AOP thường chỉ có tác dụng khi call đi qua proxy; `new CheckoutService` không chạy transaction interceptor. Transaction boundary quan trọng cần Spring integration test với PlatformTransactionManager/adapter phù hợp.

**Async:** `@Async` có scheduler/thread semantics; không dùng `Thread.sleep` không ổn định để “đợi” test. Kiểm soát executor/future hoặc test event processing có timeout hợp lý.

**Profiles:** `@Profile("test")` điều kiện đăng ký bean; `@ActiveProfiles("test")` chọn profile trong TestContext. `@TestPropertySource(properties="app.mode=test")` không tự bật active profile.

**Scopes:** Singleton và prototype có instance lifecycles khác. Prototype injected một lần vào singleton không biến thành per-method prototype. Cần ObjectProvider/scoped proxy khi business thật cần lookup per-use.

**BeanPostProcessor:** Có thể wrap bean thành proxy, đổi runtime exposed type và advice. Test fake không thay integration test proxy behavior.

**Context caching:** Các test classes có cùng Spring configuration có thể dùng chung singleton; giữ fake state reset hoặc isolate test contexts. Không cần `@DirtiesContext` chỉ vì muốn chạy một unit test mới.

## 19. Testability không có nghĩa không cần integration tests

Một Catalogue stub có thể luôn trả 700, nhưng adapter REST thật có thể parse JSON sai, timeout hoặc mapping 404 thành null. Một unit test không bắt được các lỗi ấy.

Một chiến lược cân bằng:

1. **Nhiều unit tests** cho domain calculations, validations và failure policies.
2. **Một số Spring wiring tests** xác nhận composition, bean candidates, scopes, profiles.
3. **Adapter contract tests** cho SQL/HTTP/message encoding/error handling.
4. **Cross-system integration/end-to-end tests** cho vài luồng quan trọng và transaction semantics.

Tỷ lệ test cụ thể phụ thuộc chi phí/đặc tính ứng dụng; không áp một hình tháp cứng cho mọi hệ thống. Không dùng 100% line coverage làm bằng chứng requirements đúng.

## 20. Quy trình refactor giảm coupling mà vẫn giữ behavior

1. **Ghi baseline:** liệt kê dependencies, external effects, profile/scope và tests đang có.
2. **Chọn một boundary có chi phí cao:** DB/HTTP client/clock được khởi tạo trong business class.
3. **Viết characterization tests:** xác nhận behavior hiện tại, bao gồm negative paths.
4. **Trích port nhỏ:** chỉ những operations business consumer thật cần.
5. **Chuyển sang constructor injection:** required dependencies xuất hiện trong signature.
6. **Đưa implementation choices vào composition root:** Spring `@Bean` hoặc component configuration, không lẫn business calculations.
7. **Viết unit tests với fakes:** kiểm tra một scenario khác implementation mặc định và failure path.
8. **Viết targeted Spring wiring test:** xác nhận BeanFactory chọn đúng candidates/proxies/scopes.
9. **Test adapter thật:** giữ coverage cho I/O/transaction/protocol ngoài unit.
10. **Đo lại:** mức lan truyền code changes, CI flake, setup time và maintainability.
11. **Loại abstractions không mang giá trị:** tránh tạo một tầng wrapper chỉ vì pattern.
12. **Review regressions:** behavior, lifecycle và security không được đổi âm thầm trong quá trình giảm coupling.

Đừng bật `@Lazy`, `@Primary` hay một class proxy toàn cục chỉ để loại exception trước mắt nếu dependency graph/contract vẫn sai.

## 21. Các câu hỏi tự kiểm tra

**1. Vì sao constructor injection giúp unit test?** Vì test truyền được fake dependencies trực tiếp, không cần Spring container để lắp business object.

**2. Interface có làm coupling bằng 0 không?** Không. Consumer vẫn phụ thuộc contract, nhưng ít phụ thuộc concrete implementation.

**3. Dùng Spring DI thì mọi class tự động testable không?** Không. Classes vẫn có thể phụ thuộc nhiều external services, global state và hidden lookup.

**4. Vì sao không tạo interface cho mọi record?** Immutable data values không có behavior variation cần seam trong nhiều trường hợp; abstraction dư có thể tăng indirection.

**5. Một unit test không dùng Spring xác minh được @Bean wiring không?** Không. Cần Spring context test.

**6. Context load thành công có chứng minh checkout total đúng không?** Không. Test behavior, đặc biệt negative cases.

**7. Tại sao kiểm tra sink effect quan trọng?** Vì return Receipt đúng nhưng không lưu/publish receipt có thể vi phạm use-case contract.

**8. @Lazy có loại bỏ logical circular dependency không?** Không, chỉ trì hoãn resolve bean.

**9. Fan-out cao luôn tệ không?** Không. Orchestrator có thể cần nhiều ports; đánh giá cohesion và responsibilities.

**10. Afferent coupling lớn luôn tệ không?** Không. Một API ổn định có thể là ranh giới dùng chung hợp lệ.

**11. Vì sao cần test real Catalog adapter khi đã có fake?** Fake không chứng minh SQL/HTTP serialization, lỗi mạng và transaction thật đúng.

**12. Vì sao test không nên đọc System.currentTimeMillis trực tiếp nếu cần deterministic clock?** Vì thời gian là input không kiểm soát; inject `Clock` để cố định/giả lập.

**13. Mockito mocks nhiều có luôn tốt hơn lambda stub?** Không. Dùng test double đơn giản nhất diễn đạt đúng contract; tránh over-specifying interactions.

**14. LegacyCheckout và CheckoutService cùng output có đồng nghĩa testability ngang nhau?** Không. LegacyCheckout khóa collaborators, khó thay conditions/failure scenarios.

**15. @SpringJUnitConfig thuộc Spring hay JUnit?** Nó là composed annotation của Spring TestContext tích hợp SpringExtension với JUnit Jupiter.

**16. Tại sao test negative quantity assert zero calls?** Nó kiểm tra yêu cầu không gây side effects khi request invalid, không chỉ exception type.

## 22. Checklist review trước khi merge

1. Có graph dependencies với chiều cạnh nhất quán?
2. Có tách static, bean-creation và runtime interaction graphs?
3. Required dependencies có trong constructor/factory signature?
4. Business code có tự tạo adapter/resource bên ngoài?
5. Có hidden dependency qua static/service locator?
6. Có time/random/global state không kiểm soát?
7. Interface có tạo seam thực tế?
8. Có interface quá rộng hoặc không cần thiết?
9. Cohesion có phù hợp một responsibility/use case?
10. Có cycles, đặc biệt constructor cycles?
11. Có refactor thay vì giấu vòng bằng @Lazy?
12. Pure logic unit tests có chạy không cần Spring?
13. Happy/negative/boundary paths có assertions đúng?
14. External effects có được quan sát qua fake/sink?
15. Có tránh timing-based flaky tests?
16. Spring wiring tests có xác minh bean names/types/candidates?
17. Tests có tránh assert mọi internal call vô nghĩa?
18. Context cache/singleton state có được cô lập?
19. Adapter SQL/HTTP/broker có contract/integration coverage?
20. AOP/transaction/async tests nằm ở integration layer khi cần?
21. Profiles/properties/scopes được kiểm tra đúng không?
22. Resource lifecycle/cleanup có chủ sở hữu?
23. Có chống injection ambiguity với @Qualifier/@Primary đúng nơi?
24. Có đo change amplification từ tình huống thực?
25. Có phân biệt metrics với kết luận chất lượng?
26. Có tránh overengineering interfaces/factories?
27. Security, validation, idempotency được xét độc lập?
28. Có kiểm tra PR thay adapter không kéo theo sửa domain?
29. Có ghi rõ tests đã chạy thật hay chỉ expected?
30. Có regression suite giữ business behavior sau refactor?

## 23. Tài liệu chính thức và đọc thêm

- [Spring Framework 6.2 — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework 6.2 — Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/6.2/core/beans/java.html)
- [Spring Framework 6.2 — Unit Testing](https://docs.spring.io/spring-framework/reference/6.2/testing/unit.html)
- [Spring Framework 6.2 — TestContext Framework](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework.html)
- [Spring Framework 6.2 — SpringExtension and Dependency Injection in Tests](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/support-classes.html)
- [Spring Framework 6.2 — Context Caching](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/caching.html)
- [Spring Framework 6.2 — Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html)
- [Spring Framework 6.2 — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 Javadoc — @SpringJUnitConfig](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/junit/jupiter/SpringJUnitConfig.html)
- [Spring Framework 6.2 Javadoc — @Configuration](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Configuration.html)
- [Spring Framework 6.2 Javadoc — @Bean](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/annotation/Bean.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)


</details>

- [Quay lại đầu trang](#back-to-top)
