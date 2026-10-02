<a id="back-to-top"></a>

# Provider trên Class Path và Module Path

## Menu
- [META-INF/services trên Class Path](#classpath-provider-configuration)
- [Consumer Module: requires và uses](#module-consumer-requires-uses)
- [Provider Module và provides Directive](#module-provider-provides)
- [Provider Constructor và provider() Method](#provider-instantiation-rules)
- [Automatic Module và giới hạn provider() Method](#automatic-module-limitations)
- [Giữ Host độc lập với Provider Module](#host-provider-decoupling)

## <a id="classpath-provider-configuration">META-INF/services trên Class Path</a>

<details>
<summary>Click for details</summary>

Trên class path, provider được đăng ký bằng **provider-configuration file** dưới:

```text
META-INF/services/<fully-qualified-service-name>
```

Ví dụ service:

```text
com.example.export.ReportExporter
```

thì provider JAR có file:

```text
META-INF/services/com.example.export.ReportExporter
```

với nội dung:

```text
com.example.export.pdf.PdfExporter
com.example.export.csv.CsvExporter
```

Mỗi dòng là binary name của provider class. File dùng UTF-8; blank line và comment `#` được hỗ trợ.

Provider class trên class path phải visible từ discovery class loader và phải thỏa constructor-based provider contract.

Đăng ký bằng file resource có lợi là host không cần compile dependency tới provider. Nhưng tính đúng đắn khi deployment giờ phụ thuộc cách đóng gói resource; shading/assembly sai có thể làm mất hoặc ghi đè service file, nên cần integration test artifact thực tế.

Một chi tiết quan trọng khi migrate sang JPMS: nếu provider class đã nằm trong **named module**, entry tương ứng trong `META-INF/services` không còn được dùng như registration cho unnamed-module discovery. Provider của named module phải được công bố bằng `provides` trong module descriptor. Điều này tránh một provider bị nhìn thấy hai lần qua cả module descriptor lẫn service file.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-consumer-requires-uses">Consumer Module: requires và uses</a>

<details>
<summary>Click for details</summary>

Với named module consumer, việc gọi `ServiceLoader` là một dependency được khai báo trong `module-info.java`.

Ví dụ service type nằm trong module `com.example.export.api`:

```java
module com.example.report.host {
    requires com.example.export.api;
    uses com.example.export.ReportExporter;
}
```

Hai directive giải quyết hai việc khác nhau:

```text
requires
→ host đọc module export service type

uses
→ host tuyên bố nó là consumer của service đó
```

Nếu service type nằm ngay trong consumer module thì không cần `requires` chính module đó; nhưng `uses` vẫn là declaration của service consumption.

Named-module caller không nên coi `uses` chỉ là documentation. `ServiceLoader` kiểm tra caller/module contract và service binding/resolution dựa trên thông tin này.

Đây là điểm Runtime Extensibility sử dụng JPMS; full readability/exports/resolution semantics vẫn thuộc Module System curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-provider-provides">Provider Module và provides Directive</a>

<details>
<summary>Click for details</summary>

Provider trong explicit named module được đăng ký bằng `provides ... with ...`:

```java
module com.example.export.pdf {
    requires com.example.export.api;

    provides com.example.export.ReportExporter
        with com.example.export.pdf.PdfExporter;
}
```

Điểm đáng chú ý:

- provider được khai báo trong chính module chứa provider;
- một `provides` directive không trỏ provider class nằm ở module khác;
- package chứa phần triển khai thường không cần export cho host;
- host khám phá provider qua service contract, không cần import class triển khai của provider.

Việc không export provider package giúp giữ encapsulation tốt hơn:

```text
host
→ nhìn thấy service API
→ không cần nhìn thấy package triển khai
```

Một module có thể khai báo nhiều provider cho cùng service; declaration order của provider trong module descriptor là một phần ordering mà `ServiceLoader` sử dụng trong module đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-instantiation-rules">Provider Constructor và provider() Method</a>

<details>
<summary>Click for details</summary>

Explicit module provider có hai cách construction.

**1. Provider constructor**

```java
public final class PdfExporter implements ReportExporter {
    public PdfExporter() {}
}
```

Provider phải có public no-arg constructor và instance phải assignable cho service.

**2. Public static `provider()` method**

```java
public final class PdfExporterProvider {
    public static ReportExporter provider() {
        return new PdfExporter(loadNativeEngine());
    }
}
```

Class `PdfExporterProvider` ở trường hợp này không bắt buộc implement `ReportExporter`; return type của `provider()` phải assignable cho service.

Provider method hữu ích khi construction cần indirection hoặc class khai báo trong `provides` không phải final service object.

Đừng nhầm với arbitrary factory API của application: Java nhận diện **đúng** public static no-arg method tên `provider` theo service-provider contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="automatic-module-limitations">Automatic Module và giới hạn provider() Method</a>

<details>
<summary>Click for details</summary>

Automatic module là JAR không có explicit `module-info.class` nhưng được đặt trên module path và được JPMS coi như named module tự động.

Với service provider, automatic module có giới hạn quan trọng:

```text
provider()
→ không được hỗ trợ như explicit module provider

provider constructor
→ bắt buộc
```

Do đó class provider phải trực tiếp phù hợp với service type và có public no-arg constructor.

Điều này ảnh hưởng migration strategy: một provider JAR đang dùng `META-INF/services` trên class path có thể chạy như automatic module trên module path, nhưng không có toàn bộ expressiveness của explicit module descriptor.

Nếu architecture cần `provides` rõ ràng, strong encapsulation hoặc static `provider()` factory method, hãy chuyển provider thành explicit named module thay vì dừng ở automatic module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-provider-decoupling">Giữ Host độc lập với Provider Module</a>

<details>
<summary>Click for details</summary>

Một mục tiêu cốt lõi của service-provider architecture là host phụ thuộc vào **service contract**, không phụ thuộc provider modules.

Host module nên có dạng:

```java
module com.example.report.host {
    requires com.example.export.api;
    uses com.example.export.ReportExporter;
}
```

không phải:

```java
module com.example.report.host {
    requires com.example.export.api;
    requires com.example.export.pdf;
    requires com.example.export.csv;
}
```

Nếu host `requires` mọi provider, extension set lại trở thành compile-time dependency list và mất phần lớn lợi ích decoupling.

Provider modules nên được đưa vào runtime image/module path hoặc dynamic configuration theo deployment architecture. Host chỉ cần biết contract và selection policy.

Test cũng nên phản ánh boundary này: contract test verify mỗi provider độc lập; integration test verify host discover provider từ artifact/deployment thực tế mà không import concrete class.

Đến đây Java đã giải quyết được **contract + registration + discovery boundary**, nhưng vẫn chưa trả lời ai sở hữu validation, selection registry, lifecycle, failure containment và cleanup. Những trách nhiệm đó là lý do chapter tiếp theo nâng mô hình từ ServiceLoader provider thành **plugin architecture**.

</details>

- [Quay lại đầu trang](#back-to-top)
