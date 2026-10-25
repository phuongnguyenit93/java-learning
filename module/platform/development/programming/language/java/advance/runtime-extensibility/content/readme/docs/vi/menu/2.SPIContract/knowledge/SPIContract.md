<a id="back-to-top"></a>

# Hợp đồng SPI

## Menu
- [SPI là gì và vì sao cần một Extension Contract ổn định?](#spi-contract-purpose)
- [Thiết kế Service Type](#service-type-design)
- [Thiết kế Provider Type](#provider-type-design)
- [Provider như Implementation trực tiếp hay Factory/Indirection](#provider-as-factory)
- [Ổn định Contract và giảm Coupling giữa Host với Provider](#contract-stability)

## <a id="spi-contract-purpose">SPI là gì và vì sao cần một Extension Contract ổn định?</a>

<details>
<summary>Click for details</summary>

SPI (Service Provider Interface) là contract mà host công bố để các provider triển khai capability có thể cắm vào hệ thống.

Điểm quan trọng không nằm ở tên “SPI”, mà ở dependency direction:

```text
host ────────┐
             ↓
        service contract
             ↑
provider ────┘
```

Host và provider cùng phụ thuộc vào contract; host không phụ thuộc trực tiếp vào implementation.

Không có contract ổn định, “plugin” chỉ là một class được load động nhưng vẫn coupling mạnh vào chi tiết implementation. Contract tạo ra vocabulary chung để hai phía phát triển độc lập.

Một SPI tốt nên nói rõ:

- capability nào provider cung cấp;
- input/output nào là ổn định;
- error semantics nào host phải xử lý;
- provider có state/lifecycle hay không;
- host được phép giả định điều gì về thread safety, resource ownership và compatibility.

Do đó SPI là **architecture boundary**, không chỉ là một interface để `ServiceLoader` có thứ để load.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-type-design">Thiết kế Service Type</a>

<details>
<summary>Click for details</summary>

Service type nên mô tả **capability mà host cần**, không mô tả cấu trúc nội bộ của provider.

Để minh họa một contract giàu semantics hơn baseline ở chapter trước, biến thể sau cố ý thêm capability check, domain type và error contract:

```java
public interface ReportExporter {
    String format();
    boolean supports(Report report);
    void export(Report report) throws ExportException;
}
```

Ở đây host có đủ thông tin để:

1. xác định format/capability;
2. hỏi provider có xử lý request hay không;
3. gọi operation chính;
4. xử lý lỗi ở mức contract.

Một service type nên nhỏ nhưng không đến mức host phải inspect implementation bằng Reflection để biết provider làm được gì. Nếu selection cần metadata quan trọng, hãy cân nhắc biểu diễn metadata đó qua method ổn định của service hoặc annotation rõ ràng.

Tránh để SPI phụ thuộc vào type nội bộ của host:

```java
// Coupling mạnh: provider phải biết internal class của host
void export(InternalReportEntity entity);
```

Ưu tiên DTO/value type nằm trong contract module và có semantics ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-type-design">Thiết kế Provider Type</a>

<details>
<summary>Click for details</summary>

Provider type là type mà cơ chế khám phá ở runtime có thể tìm thấy và dùng để cung cấp service. Theo contract của `ServiceLoader`, provider type phải là `public` và không được là inner class. Với explicit module, provider type thậm chí có thể là interface hoặc abstract class nếu nó cung cấp static `provider()` method hợp lệ.

Trong trường hợp đơn giản:

```java
public final class PdfExporter implements ReportExporter {
    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public boolean supports(Report report) {
        return true;
    }

    @Override
    public void export(Report report) {
        // PDF implementation
    }
}
```

Provider không nên yêu cầu host biết helper class, type đặc thù của thư viện hay chi tiết khởi tạo nội bộ của nó.

Thiết kế provider cũng cần trả lời:

- một instance có thể tái sử dụng hay không;
- có an toàn khi nhiều thread dùng đồng thời hay không;
- provider giữ tài nguyên dài hạn hay chỉ tạo tài nguyên khi được gọi;
- lỗi khởi tạo được báo bằng cách nào;
- trách nhiệm dọn dẹp thuộc provider hay host.

`ServiceLoader` biết cách tạo provider theo contract của platform, nhưng nó không tự định nghĩa semantics vòng đời cho ứng dụng. Vì vậy thiết kế provider phải phù hợp với vòng đời plugin mà host sẽ quản lý ở các chapter sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-as-factory">Provider như Implementation trực tiếp hay Factory/Indirection</a>

<details>
<summary>Click for details</summary>

Không phải service nào cũng nên để provider chính là object thực thi operation cuối cùng.

Hai mô hình thường gặp:

```text
Direct provider
→ provider chính là service implementation

Factory/indirection provider
→ provider tạo object thực thi thực sự khi cần
```

Direct provider phù hợp khi object rẻ, stateless hoặc reusable:

```java
public final class PdfExporter implements ReportExporter { ... }
```

Factory phù hợp khi object đắt hoặc phụ thuộc request-specific configuration:

```java
public interface ExporterFactory {
    String format();
    ReportExporter create(ExportOptions options);
}
```

Host discovery factory một lần, sau đó factory tạo exporter theo từng configuration.

Java module provider còn hỗ trợ static `provider()` method, cho phép class được khai báo trong `provides ... with ...` đóng vai trò factory mà bản thân class đó không nhất thiết implement service type. Đây là platform mechanism hỗ trợ indirection; design decision vẫn phải xuất phát từ lifecycle/cost của domain object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-stability">Ổn định Contract và giảm Coupling giữa Host với Provider</a>

<details>
<summary>Click for details</summary>

SPI là boundary mà nhiều artifact có thể compile độc lập, nên thay đổi contract phải thận trọng hơn internal API.

Một thay đổi tưởng nhỏ như thêm abstract method mới có thể làm provider cũ không còn tương thích binary/source theo cách mong muốn.

Một số nguyên tắc thiết kế:

- giữ contract module nhỏ và ít dependency;
- tránh expose implementation-specific type;
- dùng default method khi semantics thực sự có default hợp lý;
- ưu tiên thêm capability theo cách provider cũ có thể tiếp tục hoạt động;
- document rõ behavior của `null`, exception, concurrency và resource ownership;
- không để host cast provider sang implementation class.

Ví dụ host không nên làm:

```java
if (exporter instanceof PdfExporter pdf) {
    pdf.enableInternalFeature();
}
```

Nếu capability đó là một phần của contract, hãy model nó trong SPI. Nếu không, host đang phá abstraction boundary.

Version compatibility sẽ được đào sâu ở chapter riêng; ở đây điều cần giữ lại là: **SPI là public compatibility surface giữa host và extension**.

Khi contract đã ổn định, câu hỏi tiếp theo không còn là “provider implement gì?” mà là **host tìm provider đó ở runtime như thế nào**. Vì vậy chapter tiếp theo chuyển từ thiết kế SPI sang `ServiceLoader` và discovery semantics.

</details>

- [Quay lại đầu trang](#back-to-top)
