<a id="back-to-top"></a>

# Mô hình Runtime Extensibility

## Menu
- [Runtime Extensibility là gì và vì sao cần?](#runtime-extensibility-purpose)
- [Host, Service Contract, Provider và Discovery](#host-extension-model)
- [Giới hạn của Static Wiring và Hard-coded Implementation](#static-wiring-limitations)
- [Các mức Runtime Extensibility trong Java](#extensibility-mechanism-spectrum)
- [Ranh giới với Class Loading, JPMS, Framework Plugin và Instrumentation](#runtime-extensibility-boundaries)

## <a id="runtime-extensibility-purpose">Runtime Extensibility là gì và vì sao cần?</a>

<details>
<summary>Click for details</summary>

Runtime Extensibility là khả năng để một ứng dụng Java **mở rộng hành vi sau khi host đã được thiết kế**, bằng cách nạp implementation thông qua một contract đã biết trước thay vì hard-code mọi implementation vào host.

Hãy hình dung một ứng dụng cần xuất báo cáo. Cách đơn giản nhất là viết trực tiếp:

```java
PdfExporter exporter = new PdfExporter();
exporter.export(report);
```

Cách này ổn khi chỉ có một implementation. Nhưng nếu sau này cần thêm `CsvExporter`, `JsonExporter`, hoặc provider do team khác đóng gói thành JAR riêng, host bắt đầu phải biết quá nhiều implementation cụ thể.

Runtime extensibility giải quyết bài toán đó bằng một boundary:

```text
Host
  ↓ depends on
Service Contract
  ↑ implemented/provided by
Provider

Provider Discovery
→ tìm implementation phù hợp ở runtime
```

Điểm quan trọng: extensibility không có nghĩa “mọi thứ đều động”. Host vẫn phải biết **service contract** nào nó muốn dùng. Phần động là provider nào thực hiện contract đó và provider được phát hiện như thế nào.

Module này tồn tại vì Java có một service-provider model chuẩn (`ServiceLoader`) và các cơ chế module/runtime như `ModuleLayer` để tổ chức extension theo cách ít coupling hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-extension-model">Host, Service Contract, Provider và Discovery</a>

<details>
<summary>Click for details</summary>

Bốn vai trò chính tạo thành mental model của module:

```text
Host
→ code sử dụng capability

Service Contract
→ interface/abstract class mô tả capability

Provider
→ implementation hoặc factory cung cấp capability

Discovery
→ cơ chế tìm provider khi application đang chạy
```

Ví dụ:

```java
public interface ReportExporter {
    String format();
    String export(String report);
}
```

`ReportExporter` là service contract. `PdfExporter` và `CsvExporter` có thể là provider. Host chỉ cần phụ thuộc vào `ReportExporter`; nó không cần `new PdfExporter()` ở mọi nơi.

Đây là **baseline running example** cũng được dùng bởi các API experiment của module. Ở các chapter sau, một số snippet sẽ cố ý mở rộng hoặc rút gọn `ReportExporter` để minh họa riêng capability modeling, error contract hoặc SPI evolution; các snippet đó là **biến thể minh họa**, không phải cùng một source interface âm thầm thay đổi.

```java
ServiceLoader<ReportExporter> exporters =
        ServiceLoader.load(ReportExporter.class);
```

`ServiceLoader` ở đây là discovery mechanism. Sau discovery, **host vẫn phải quyết định** provider nào phù hợp. Vì vậy cần giữ rõ hai khái niệm:

```text
discovery
→ tìm provider nào đang tồn tại

selection
→ chọn provider nào phù hợp với yêu cầu hiện tại
```

Sự phân tách này là nền tảng cho toàn bộ module: SPI định nghĩa contract, `ServiceLoader` làm discovery, plugin architecture quản lý lifecycle/isolation, và `ModuleLayer` hỗ trợ composition ở module level.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-wiring-limitations">Giới hạn của Static Wiring và Hard-coded Implementation</a>

<details>
<summary>Click for details</summary>

Static wiring không sai. Nó thường là lựa chọn đơn giản và tốt nhất khi tập implementation được biết trước khi build/deploy.

Vấn đề xuất hiện khi host phải thay đổi mỗi lần có extension mới:

```java
switch (format) {
    case "pdf" -> new PdfExporter();
    case "csv" -> new CsvExporter();
    case "json" -> new JsonExporter();
    default -> throw new IllegalArgumentException();
}
```

Ở đây, thêm một provider mới đồng nghĩa sửa host, build lại host và redeploy host. Điều đó làm mất lợi ích của extension độc lập.

Một registry hard-coded cũng chỉ chuyển coupling sang vị trí khác:

```java
registry.put("pdf", new PdfExporter());
registry.put("csv", new CsvExporter());
```

Runtime extensibility đáng cân nhắc khi có ít nhất một nhu cầu như:

- provider được phát triển/đóng gói độc lập;
- host không nên biết trước mọi provider;
- capability có thể được bổ sung bằng JAR/module mới;
- application cần chọn provider theo capability hoặc metadata;
- plugin cần isolation hoặc lifecycle riêng.

Nếu các nhu cầu trên không tồn tại, dependency injection hoặc static wiring thường đơn giản hơn và dễ vận hành hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="extensibility-mechanism-spectrum">Các mức Runtime Extensibility trong Java</a>

<details>
<summary>Click for details</summary>

Java có nhiều mức extensibility. Không phải hệ thống nào cũng cần `ModuleLayer`.

```text
1. Static wiring
   Host biết implementation cụ thể.

2. Interface + configurable wiring
   Host phụ thuộc contract, configuration chọn implementation.

3. ServiceLoader trên class path/module path
   Provider được phát hiện từ runtime environment.

4. Plugin architecture
   Host thêm selection, lifecycle, failure boundary, compatibility policy.

5. ModuleLayer-based composition
   Host resolve và define một graph named module mới trong JVM.
```

Mỗi mức thêm flexibility nhưng cũng tăng complexity.

Ví dụ, `ServiceLoader` chỉ giải quyết **discovery/loading**. Nó không tự cung cấp plugin lifecycle, dependency conflict resolution, hot-unload, version negotiation hay retry policy. Những capability đó phải do architecture của application thiết kế.

Nguyên tắc xuyên suốt module:

> Chọn cơ chế đơn giản nhất vẫn đáp ứng đúng boundary và lifecycle requirement của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-extensibility-boundaries">Ranh giới với Class Loading, JPMS, Framework Plugin và Instrumentation</a>

<details>
<summary>Click for details</summary>

Runtime Extensibility đứng cạnh nhiều topic Java khác nhưng không sở hữu toàn bộ chúng.

```text
ClassLoader
→ class identity, delegation, visibility, loader lifecycle

JPMS
→ module descriptor, readability, exports/opens, resolution nói chung

Runtime Extensibility
→ service/provider contract, discovery, plugin architecture,
   service binding và ModuleLayer integration

Instrumentation
→ sửa/quan sát bytecode hoặc class đã được JVM load
```

Ví dụ: plugin isolation có thể cần custom `ClassLoader`, nhưng module này chỉ học **vì sao isolation cần loader boundary** và cách nó ảnh hưởng plugin design. Chi tiết delegation model thuộc ClassLoader curriculum.

Tương tự, module này dùng `requires`, `uses`, `provides`, `Configuration` và `ModuleLayer` vì chúng phục vụ extensibility. Nó không dạy lại toàn bộ JPMS.

Một boundary đặc biệt quan trọng:

```text
load một provider mới
≠
redefine class đang chạy
```

Trường hợp thứ nhất là extensibility. Trường hợp thứ hai thuộc Instrumentation/agent model.

</details>

- [Quay lại đầu trang](#back-to-top)
