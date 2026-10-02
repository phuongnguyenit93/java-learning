<a id="back-to-top"></a>

# Kiến trúc Plugin

## Menu
- [Từ SPI tới Plugin Architecture](#plugin-architecture-purpose)
- [Thiết kế Extension Point](#extension-point-design)
- [Ranh giới trách nhiệm giữa Host và Plugin](#host-plugin-boundary)
- [Vai trò của Plugin Manager](#plugin-manager-responsibilities)
- [Capability Model và Plugin Selection Contract](#plugin-capability-model)
- [Chính sách Dependency giữa Host, Contract và Plugin](#plugin-dependency-policy)

## <a id="plugin-architecture-purpose">Từ SPI tới Plugin Architecture</a>

<details>
<summary>Click for details</summary>

SPI + `ServiceLoader` mới giải quyết **contract + discovery**. Một plugin architecture hoàn chỉnh còn phải trả lời:

```text
provider nào được phép activate?
→ ai quản lý lifecycle?
→ failure của plugin bị cô lập thế nào?
→ dependency nào được share?
→ compatibility được kiểm tra ở đâu?
```

Khi những câu hỏi này xuất hiện, provider không còn chỉ là “một implementation được tìm thấy”, mà trở thành một **plugin** có boundary và lifecycle do host quản lý.

Flow điển hình:

```text
discover
→ validate
→ select
→ initialize
→ activate
→ execute
→ deactivate
→ cleanup
```

Java không cung cấp một universal plugin framework trong `ServiceLoader`. Architecture của application phải xây phần orchestration còn lại trên primitive discovery/module mechanisms.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="extension-point-design">Thiết kế Extension Point</a>

<details>
<summary>Click for details</summary>

Extension point là vị trí có chủ đích nơi host cho phép behavior bên ngoài tham gia.

Ví dụ `ReportExporter` là extension point nếu host cho phép provider mới bổ sung format export mà không sửa host.

Một extension point tốt cần rõ:

- capability nào được mở rộng;
- khi nào host gọi plugin;
- input/output contract;
- error semantics;
- concurrency/lifecycle expectation;
- resource ownership;
- compatibility/version expectation.

Không nên biến mọi interface thành extension point. Interface nội bộ phục vụ implementation decomposition khác với public plugin boundary.

Nếu contract quá rộng, provider phải hiểu quá nhiều host internals. Nếu quá nhỏ, host phải dùng cast/reflection hoặc side channel để lấy capability thật.

Thiết kế extension point là cân bằng giữa **ổn định boundary** và **đủ expressiveness** cho provider thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-plugin-boundary">Ranh giới trách nhiệm giữa Host và Plugin</a>

<details>
<summary>Click for details</summary>

Host và plugin nên có trách nhiệm tách biệt.

Host thường sở hữu:

- discovery context;
- selection policy;
- lifecycle orchestration;
- logging/metrics boundary;
- timeout/cancellation policy;
- failure containment;
- shared service contract.

Plugin thường sở hữu:

- implementation detail;
- private dependencies;
- capability-specific resource;
- cleanup cho resource nó tạo ra;
- domain behavior của extension.

Một anti-pattern là host can thiệp sâu vào implementation:

```java
PdfExporter pdf = (PdfExporter) plugin;
pdf.getInternalEngine().reset();
```

Khi đó plugin boundary không còn thật sự tồn tại.

Ngược lại, plugin cũng không nên tự ý điều khiển toàn host lifecycle hoặc giữ global mutable state nếu contract không cho phép.

Boundary tốt giúp thay provider mà không kéo thay đổi implementation detail sang host.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-manager-responsibilities">Vai trò của Plugin Manager</a>

<details>
<summary>Click for details</summary>

Khi plugin model vượt qua discovery đơn giản, một `PluginManager` hoặc component tương đương thường xuất hiện để tập trung orchestration.

Ví dụ responsibilities:

```text
discover providers
→ validate metadata/compatibility
→ build registry
→ activate selected plugins
→ expose immutable/read-only view cho business code
→ stop/cleanup khi shutdown hoặc replacement
```

Pseudo-code:

```java
final class PluginManager {
    private final Map<String, ReportExporter> exporters;

    void start() { ... }
    ReportExporter exporterFor(String format) { ... }
    void stop() { ... }
}
```

`PluginManager` không nhất thiết là framework lớn. Nó chỉ cần tạo một nơi rõ ràng chứa lifecycle và selection policy thay vì để business code gọi `ServiceLoader` ở nhiều chỗ.

Tránh biến manager thành “god object” biết mọi plugin-specific detail. Nó nên orchestration qua contract/metadata ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-capability-model">Capability Model và Plugin Selection Contract</a>

<details>
<summary>Click for details</summary>

Capability model trả lời “plugin này làm được gì?” theo cách host có thể dùng để selection mà không biết implementation.

Biến thể `ReportExporter` dưới đây cố ý đổi sang **capability-oriented contract** để minh họa selection theo nhiều format/options; nó không thay thế baseline interface dùng bởi API experiment:

```java
public interface ReportExporter {
    Set<String> formats();
    boolean supports(ExportOptions options);
    void export(Report report, ExportOptions options);
}
```

Host có thể xây registry:

```text
pdf  → PdfExporter
csv  → CsvExporter
json → JsonExporter
```

Nếu có nhiều provider cùng capability, contract cần thêm policy: priority, version, tenant/environment, quality tier hoặc explicit configuration.

Capability nên mô tả điều host cần quyết định, không phải mọi implementation detail. Đừng expose raw dependency version nếu host chỉ cần biết một boolean capability.

Selection contract càng explicit, architecture càng ít phụ thuộc incidental discovery order.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-dependency-policy">Chính sách Dependency giữa Host, Contract và Plugin</a>

<details>
<summary>Click for details</summary>

Plugin system cần policy rõ cho dependency direction.

Một layout thường an toàn:

```text
host
  ↓
contract/api module
  ↑
plugin A     plugin B
```

Contract module nên nhỏ, ổn định và chứa type thật sự cần share.

Plugin-specific library nên ở phía plugin nếu host không cần biết. Chia sẻ quá nhiều dependency làm tăng nguy cơ version conflict và class-identity coupling.

Tránh dependency vòng:

```text
host → plugin
plugin → host implementation
```

Nếu plugin cần host service, hãy expose capability đó qua một **host-facing contract/context** ổn định thay vì để plugin import implementation package.

Ví dụ:

```java
interface PluginContext {
    Logger logger();
    Path dataDirectory();
}
```

Policy dependency là nền tảng cho isolation và compatibility ở các chapter tiếp theo.

</details>

- [Quay lại đầu trang](#back-to-top)
