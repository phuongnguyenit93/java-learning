<a id="back-to-top"></a>

# Chiến lược khám phá và lựa chọn Provider

## Menu
- [Phân biệt Provider Discovery và Provider Selection](#discovery-vs-selection)
- [Thiết kế Policy lựa chọn Provider](#provider-selection-policy)
- [Lựa chọn theo Capability và Metadata](#capability-based-selection)
- [Giới hạn của Provider Ordering và findFirst()](#provider-ordering-caveats)
- [Không có Provider và Fallback Strategy](#no-provider-and-fallback)
- [Nhiều Provider, Provider trùng lặp và Provider lỗi](#duplicate-and-broken-providers)

## <a id="discovery-vs-selection">Phân biệt Provider Discovery và Provider Selection</a>

<details>
<summary>Click for details</summary>

Discovery và selection là hai bước khác nhau:

```text
Discovery
→ runtime có provider nào?

Selection
→ provider nào phù hợp với request/policy hiện tại?
```

`ServiceLoader` giải quyết bước đầu. Bước sau là trách nhiệm application.

Ví dụ host cần exporter cho `pdf`:

```java
ReportExporter exporter = ServiceLoader.load(ReportExporter.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.format().equalsIgnoreCase("pdf"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No PDF exporter"));
```

Ở đây `findFirst()` chỉ là terminal operation sau khi ứng dụng đã lọc provider theo chính sách của nó.

Đừng biến “provider được discovery trước” thành business rule nếu không có contract rõ. Thứ tự provider ở runtime có thể phụ thuộc module graph, thứ tự class loader/resource và topology triển khai.

Một architecture tốt xem discovery như input cho selection policy, không xem discovery order như policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-selection-policy">Thiết kế Policy lựa chọn Provider</a>

<details>
<summary>Click for details</summary>

Selection policy nên được viết thành rule explicit thay vì rải nhiều `if/else` khắp host.

Ví dụ:

```java
public final class ExporterSelector {
    public ReportExporter select(
            Collection<ReportExporter> providers,
            String format,
            Report report) {

        return providers.stream()
                .filter(p -> p.format().equalsIgnoreCase(format))
                .filter(p -> p.supports(report))
                .findFirst()
                .orElseThrow(() -> new NoExporterException(format));
    }
}
```

Policy có thể dựa trên:

- format/protocol/capability;
- feature set;
- version compatibility;
- configuration của environment;
- explicit priority do application định nghĩa;
- health/availability nếu architecture cho phép.

Điểm quan trọng là priority phải đến từ **application contract**, không từ incidental discovery order.

Khi selection phức tạp, hãy tách provider registry/selector khỏi business service để dễ test. `ServiceLoader` nên nằm ở infrastructure boundary; domain code không cần biết provider được tìm bằng class path hay module layer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="capability-based-selection">Lựa chọn theo Capability và Metadata</a>

<details>
<summary>Click for details</summary>

Một cách chọn provider là dựa trên capability mà không instantiate tất cả provider quá sớm.

Nếu capability nằm ở type-level metadata:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExportFormat {
    String value();
}
```

host có thể dùng `Provider.type()`:

```java
Optional<ReportExporter> pdf =
        ServiceLoader.load(ReportExporter.class)
                .stream()
                .filter(p -> {
                    ExportFormat meta = p.type().getAnnotation(ExportFormat.class);
                    return meta != null && meta.value().equals("pdf");
                })
                .map(ServiceLoader.Provider::get)
                .findFirst();
```

Ví dụ này phù hợp khi annotation nằm trên type mà `Provider.type()` trả về. Nếu explicit module dùng static `provider()` method thì `Provider.type()` là return type của method đó, không nhất thiết là factory class được ghi trong `provides`. Vì vậy đừng thiết kế selection metadata dựa trên giả định rằng `type()` luôn trả class khai báo provider.

Nếu capability phụ thuộc trạng thái runtime/request, service method thường rõ hơn:

```java
boolean supports(Report report);
```

Metadata tốt phải phục vụ một câu hỏi lựa chọn cụ thể. Tránh tạo annotation/descriptor lớn chỉ để mô tả mọi chi tiết provider.

Nếu ứng dụng cần ordering/priority, hãy biểu diễn nó rõ ràng (`priority()`, configuration, registry rule), rồi document hành vi phân xử khi bằng điểm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-ordering-caveats">Giới hạn của Provider Ordering và findFirst()</a>

<details>
<summary>Click for details</summary>

`findFirst()` trả provider đầu tiên mà loader traversal cung cấp. Nó không biến thứ tự discovery thành một global priority contract.

Java có một số ordering rule cục bộ, ví dụ provider trong cùng module descriptor giữ declaration order hoặc class-path service file giữ thứ tự resource/class-name tương ứng. Nhưng thứ tự giữa các module trong cùng layer/class loader không phải business order tổng quát mà ứng dụng nên dựa vào.

Do đó code kiểu này có thể quá yếu:

```java
ReportExporter exporter =
        ServiceLoader.load(ReportExporter.class)
                .findFirst()
                .orElseThrow();
```

nếu có nhiều provider cùng service nhưng khác quality/capability.

Tốt hơn:

```text
discover candidates
→ validate compatibility
→ filter capability
→ apply explicit priority/tie-breaker
→ select
```

`findFirst()` phù hợp khi contract thực sự nói “bất kỳ provider hợp lệ nào cũng được”, hoặc sau khi pipeline đã sắp xếp/filter theo policy của host.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="no-provider-and-fallback">Không có Provider và Fallback Strategy</a>

<details>
<summary>Click for details</summary>

Không có provider là một trạng thái hợp lệ của `ServiceLoader`; service có thể có zero provider.

Host phải quyết định semantic:

```text
required extension
→ fail fast lúc startup/activation

optional extension
→ disable feature

default implementation
→ fallback có chủ đích
```

Ví dụ fallback:

```java
ReportExporter exporter = loader.stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.format().equals("txt"))
        .findFirst()
        .orElse(DEFAULT_TEXT_EXPORTER);
```

Không nên fallback âm thầm nếu provider thiếu là lỗi deployment nghiêm trọng. Fallback âm thầm có thể che configuration defect và tạo hành vi khác kỳ vọng ở production.

Một startup validation tốt có thể kiểm tra required capabilities trước khi nhận traffic:

```text
required capabilities
→ discover
→ validate exactly/at least one usable provider
→ publish readiness
```

Selection policy và observability nên cùng phản ánh việc provider vắng mặt là optional hay fatal.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="duplicate-and-broken-providers">Nhiều Provider, Provider trùng lặp và Provider lỗi</a>

<details>
<summary>Click for details</summary>

Có “nhiều provider” không đồng nghĩa bị duplicate. Nhiều cách triển khai là use case bình thường của SPI.

Duplicate có thể xuất hiện ở hai mức:

```text
cùng provider class được liệt kê lặp
→ Java có rule loại duplicate trong service configuration

hai provider class khác nhau cung cấp cùng business capability
→ application phải giải quyết ambiguity
```

Ví dụ hai exporter đều khai báo `format() == "pdf"`. Host cần policy: ưu tiên provider nào, reject ambiguity, hay chọn theo capability/version.

Provider “broken” là trường hợp khác: class/configuration tồn tại nhưng load/instantiate lỗi. Đừng trộn nó với “unsupported capability”.

Một registry trung gian có thể chuẩn hóa discovery result:

```text
ServiceLoader
→ validate provider
→ normalize metadata
→ detect capability collision
→ build immutable registry
```

Sau startup, business path có thể dùng registry đã validate thay vì thực hiện raw discovery lặp lại trên mỗi request.

Selection policy chỉ có ý nghĩa nếu provider thực sự được **đăng ký và triển khai đúng trong runtime topology**. Chapter tiếp theo vì vậy chuyển từ “chọn provider nào” sang cách class path và module path biểu diễn provider registration/discovery boundary.

</details>

- [Quay lại đầu trang](#back-to-top)
