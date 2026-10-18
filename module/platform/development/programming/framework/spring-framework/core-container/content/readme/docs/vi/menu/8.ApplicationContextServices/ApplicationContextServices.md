<a id="back-to-top"></a>

# Các dịch vụ nền của ApplicationContext

## Menu
- [Resource abstraction](#resource-abstraction)
- [Nạp resource và phân giải pattern](#resource-loading-and-patterns)
- [Application events trong context](#application-events)
- [ApplicationListener và @EventListener](#event-listeners)
- [MessageSource và quốc tế hóa (i18n)](#message-source-i18n)
- [Parent-child ApplicationContext hierarchy](#context-hierarchy)
- [Ranh giới với Spring Messaging và web stacks](#context-service-boundaries)

## <a id="resource-abstraction">Resource abstraction</a>

<details>
<summary>Xem chi tiết</summary>

`Resource` của Spring cho phép code mô tả và truy cập resource mà không giả định resource đó luôn là file của hệ điều hành. Cùng một đoạn code sử dụng có thể làm việc với classpath, filesystem, URL hoặc cách triển khai khác thông qua một API chung.

```java
final class TemplateReader {
    String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

Mô hình tư duy quan trọng:

```text
Resource
→ đối tượng mô tả vị trí và cách truy cập

không phải

Resource
→ chắc chắn là java.io.File
```

Điểm này rất quan trọng sau khi đóng gói ứng dụng. Một classpath resource có thể nằm bên trong JAR và vẫn cung cấp `InputStream`, nhưng `getFile()` không thể biến entry trong JAR thành một file thông thường trên filesystem. Nếu code chỉ cần đọc dữ liệu byte, hãy ưu tiên stream thay vì ép mọi resource đi qua `File`.

Các lớp triển khai như `ClassPathResource`, `FileSystemResource` và `UrlResource` cung cấp cách diễn giải vị trí khác nhau phía sau cùng interface. Khi vị trí resource là cấu hình, mã ứng dụng thường nên nhận `Resource` hoặc `ResourceLoader` thay vì tự tạo lớp triển khai cụ thể ở nhiều nơi.

`Resource` chỉ chịu trách nhiệm định vị và truy cập dữ liệu. Ý nghĩa cao hơn của dữ liệu, chẳng hạn static resource của MVC, message bundle hoặc việc nạp cấu hình, thuộc component sở hữu định dạng/trường hợp sử dụng đó.

### Tài liệu tham khảo

- Spring Framework Reference — Resources

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-loading-and-patterns">Nạp resource và phân giải pattern</a>

<details>
<summary>Xem chi tiết</summary>

`ResourceLoader` biến một chuỗi vị trí thành một `Resource`. Prefix giúp làm rõ chiến lược lookup:

```java
Resource classpath = resourceLoader.getResource("classpath:catalog/default.json");
Resource file = resourceLoader.getResource("file:/opt/app/catalog.json");
Resource remote = resourceLoader.getResource("https://example.test/catalog.json");
```

Nếu không có prefix, cách triển khai `ResourceLoader` cụ thể quyết định cách diễn giải path. Vì vậy chỉ nên dùng vị trí không có qualifier khi hành vi phụ thuộc context là chủ đích.

`ResourcePatternResolver` mở rộng từ một vị trí sang tập resource khớp pattern. Một `ApplicationContext` tiêu chuẩn triển khai API này:

```java
Resource[] mappings =
        context.getResources("classpath*:/META-INF/acme/*.json");
```

`classpath:` thường nhắm tới một vị trí resource trên classpath. `classpath*:` tìm tất cả resource phù hợp trên classpath và đặc biệt hữu ích khi nhiều JAR cùng đóng góp file dưới một path. Việc phân giải pattern còn hỗ trợ các Ant-style path pattern phù hợp.

Không nên thiết kế code dựa vào bố cục filesystem vật lý chỉ vì môi trường development chạy từ thư mục class chưa đóng gói. Khi đóng gói JAR, một resource có thể không còn biểu diễn được bằng `File`, trong khi `Resource` và truy cập qua stream vẫn hoạt động.

Cũng cần tách việc tìm resource với việc parse nội dung. `ResourceLoader` trả lời "dữ liệu ở đâu và truy cập thế nào?"; JSON, XML, properties, template hay định dạng khác phải được component sở hữu định dạng đó diễn giải.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-events">Application events trong context</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationContext` đồng thời là một `ApplicationEventPublisher`. Application event cung cấp một cơ chế observer nhẹ để các component do Spring quản lý giao tiếp qua hạ tầng event của context.

Event có thể là một object ứng dụng bình thường:

```java
record CatalogRefreshed(String catalogId) {}

@Component
final class CatalogService {
    private final ApplicationEventPublisher events;

    CatalogService(ApplicationEventPublisher events) {
        this.events = events;
    }

    void refresh(String id) {
        // update the catalog...
        events.publishEvent(new CatalogRefreshed(id));
    }
}
```

Publish event nghĩa là trao event cho event multicaster của Spring. API publisher không bảo đảm lưu bền hay giao event qua process khác. Với `SimpleApplicationEventMulticaster` mặc định, listener phù hợp chạy đồng bộ trên chính thread phát event, nên `publishEvent(...)` chỉ trả về sau khi các listener đó xử lý xong.

Mặc định đồng bộ hữu ích vì listener chạy trong execution context của publisher, kể cả transaction context nếu lúc đó transaction đang active. Đổi lại, độ trễ và lỗi của listener cũng có thể ảnh hưởng trực tiếp lời gọi publish trừ khi multicaster/error strategy được cấu hình khác đi.

Application event phù hợp để tách publisher khỏi các phản ứng trong cùng process mà publisher không cần biết cụ thể từng listener. Nó không phù hợp khi yêu cầu là durable messaging, broker delivery, retry sau khi process lỗi hoặc giao tiếp sang service khác.

Spring còn phát các context lifecycle event như `ContextRefreshedEvent` và `ContextClosedEvent`. Chúng mô tả trạng thái context và khác với bean initialization callback ở chương lifecycle.

### Tài liệu tham khảo

- Spring Framework Reference — Standard and Custom Events

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="event-listeners">ApplicationListener và @EventListener</a>

<details>
<summary>Xem chi tiết</summary>

Có hai phong cách listener phổ biến. Dùng interface thì kiểu event trở thành một phần của Java type:

```java
@Component
final class ContextMetrics
        implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // record that this context completed a refresh
    }
}
```

Dùng annotation thì listener chỉ là một method bình thường:

```java
@Component
final class SearchIndexListener {

    @EventListener
    void onCatalogRefreshed(CatalogRefreshed event) {
        // rebuild the relevant index
    }
}
```

`ApplicationListener<E>` là giao diện có kiểu cổ điển cho các `ApplicationEvent`. `@EventListener` thường suy ra event type từ method parameter và cũng làm việc tự nhiên với object bất kỳ được publish dưới dạng payload event, chẳng hạn record `CatalogRefreshed` ở trên. Nó hỗ trợ filtering và ordering mà không bắt class phải triển khai listener interface.

Nếu thứ tự listener thực sự quan trọng, `@Order` có thể ảnh hưởng thứ tự gọi các listener phù hợp trong multicaster thông thường. Tuy nhiên không nên dùng ordering để che dependency bị thiếu. Nếu B chỉ có thể đúng sau khi A hoàn tất, một service điều phối tường minh thường diễn tả yêu cầu tốt hơn hai observer lỏng lẻo.

Cần nhớ hành vi đồng bộ mặc định. Listener chậm sẽ làm publisher chậm. Chuyển xử lý event sang executor khác sẽ thay đổi thread-local context, transaction context, cách exception lan truyền và thời điểm hoàn tất; phần concurrency chi tiết thuộc module Spring concurrency.

Event nên mô tả một sự kiện đã xảy ra như `CatalogRefreshed`. Với event mang tính command, nếu publisher thực chất phụ thuộc đúng một listener phải chạy để thao tác nghiệp vụ hoàn tất, dependency trực tiếp thường rõ nghĩa hơn event bus.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-source-i18n">MessageSource và quốc tế hóa (i18n)</a>

<details>
<summary>Xem chi tiết</summary>

`MessageSource` là abstraction của Spring Framework để phân giải message theo code, arguments và `Locale`. `ApplicationContext` extends `MessageSource`, vì vậy việc phân giải message là dịch vụ nền của context, không phải tính năng chỉ dành cho web.

```java
String text = messageSource.getMessage(
        "order.not-found",
        new Object[] { orderId },
        Locale.ENGLISH);
```

Bundle có thể chứa:

```properties
order.not-found=Order {0} was not found
```

Message code là metadata ổn định của ứng dụng; nội dung text có thể khác theo locale. Arguments được định dạng vào message đã phân giải theo các quy tắc format tương ứng.

Khi `ApplicationContext` khởi động, nó tìm bean có đúng tên `messageSource`. Nếu có, các lời gọi message của context được chuyển tới bean đó. Nếu context hiện tại không có source, parent context có thể cung cấp source; nếu toàn hierarchy không có, context vẫn đáp ứng giao diện `MessageSource` bằng một `DelegatingMessageSource` rỗng.

Hai cách triển khai phổ biến là `ResourceBundleMessageSource`, dựa trên mô hình resource bundle của JDK, và `ReloadableResourceBundleMessageSource`, hỗ trợ vị trí resource của Spring cùng cache/reload có thể cấu hình.

`MessageSource` phù hợp cho message hiển thị cho người dùng hoặc message nghiệp vụ cần phân giải theo locale. Cách chọn locale cho HTTP request, cách truyền validation error hoặc render view thuộc module web/validation; section này chỉ sở hữu dịch vụ phân giải nền tảng.

### Tài liệu tham khảo

- Spring Framework Reference — Internationalization using MessageSource

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy">Parent-child ApplicationContext hierarchy</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationContext` có thể có parent. Child context phân giải bean cục bộ trước và có thể fallback lên parent khi cần. Parent không nhìn thấy bean chỉ được định nghĩa trong child.

```text
parent context
  sharedRepository
  auditService
        ↑ child có thể nhìn thấy
        │
child context
  featureController
  localFormatter
```

Nếu child định nghĩa bean trùng tên với bean ở parent, definition trong child có độ ưu tiên cao hơn khi lookup từ child. Nhờ vậy parent có thể cung cấp hạ tầng dùng chung hoặc giá trị mặc định, trong khi child giữ một đồ thị object chuyên biệt hơn.

Context hierarchy khác bean-definition inheritance. Parent `ApplicationContext` là container đang hoạt động với bean và lifecycle riêng; nó không chỉ là metadata mẫu để child sao chép bean definition.

Một số dịch vụ context cũng có hành vi theo hierarchy. `MessageSource`, chẳng hạn, có thể chuyển message code không phân giải được lên parent message source. Event có quy tắc riêng: với hành vi chuẩn của `AbstractApplicationContext`, event được publish ở child sẽ được truyền tiếp lên parent, còn event publish ở parent không tự động được phát xuống các child. Bean lookup vẫn có hướng nhìn thấy riêng: child có thể lookup lên parent; parent không lookup xuống child.

Chỉ nên dùng hierarchy khi thực sự có ranh giới container với hạ tầng dùng chung ở parent và mối quan tâm riêng ở child. Không nên tạo thêm context chỉ để tổ chức package. Nhiều context đồng nghĩa nhiều bean factory, quyền sở hữu lifecycle, event multicasting và hạ tầng cục bộ, làm chẩn đoán phức tạp hơn.

Web application là nơi hierarchy thường xuất hiện, nhưng mô hình xử lý request của Servlet/MVC và cách web context được khởi động thuộc module web.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-service-boundaries">Ranh giới với Spring Messaging và web stacks</a>

<details>
<summary>Xem chi tiết</summary>

Những nội dung trong chương này là **hạ tầng ApplicationContext**:

```text
Resource / ResourceLoader
ApplicationEventPublisher + context listeners
MessageSource
parent-child context relationships
```

Các tầng Spring cao hơn tái sử dụng chúng, nhưng việc tái sử dụng không chuyển quyền sở hữu ngữ nghĩa chuyên sâu sang core-container.

Application event là event trong cùng process của context. `Message`, `MessageChannel`, broker-oriented flow, WebSocket/STOMP và RSocket programming model của Spring Messaging giải quyết nhóm vấn đề khác và thuộc module messaging. Không nên hiểu `publishEvent(...)` như cơ chế có persistence, acknowledgement, ngữ nghĩa broker hay remote delivery.

Tương tự, web application dùng resource, message, event và đôi khi context hierarchy, nhưng MVC/WebFlux sở hữu việc dispatch request, gọi controller, web binding, hành vi request-scoped, phân giải HTTP locale, phục vụ static resource và lifecycle riêng của web.

Có thể dùng câu hỏi xác định phạm vi sở hữu sau:

```text
Khái niệm này có thể giải thích chỉ với ApplicationContext thuần,
không cần HTTP request hay broker không?
    → core-container sở hữu nền tảng

Hành vi có phụ thuộc MVC/WebFlux dispatch
hoặc Spring Messaging transport/channel ngữ nghĩa không?
    → chuyển sang module tương ứng
```

Giữ ranh giới này giúp `ApplicationContext` không trở thành nơi gom mọi tính năng framework chỉ vì tính năng đó tình cờ sử dụng context.

</details>

- [Quay lại đầu trang](#back-to-top)
