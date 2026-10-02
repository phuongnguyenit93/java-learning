<a id="back-to-top"></a>

# ServiceLoader và Provider Discovery

## Menu
- [ServiceLoader Discovery Model](#serviceloader-discovery-model)
- [Lazy Discovery, Provider Cache và Instantiation](#lazy-discovery-and-cache)
- [Iterator so với ServiceLoader.Provider Stream](#iterator-vs-provider-stream)
- [ClassLoader Context của Provider Discovery](#discovery-classloader-context)
- [reload(), Cache Invalidation và Concurrency Boundary](#reload-and-concurrency)
- [ServiceConfigurationError và Failure Surface](#serviceconfigurationerror)

## <a id="serviceloader-discovery-model">ServiceLoader Discovery Model</a>

<details>
<summary>Click for details</summary>

`ServiceLoader<S>` là cơ chế chuẩn của Java để tìm cách triển khai của một **service type** `S` trong môi trường runtime.

Flow cơ bản:

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

for (ReportExporter exporter : loader) {
    System.out.println(exporter.format());
}
```

`ServiceLoader` không phải dependency-injection container. Nó không tự resolve constructor graph, scope hay lifecycle. Nó chỉ biết contract service/provider mà Java platform định nghĩa.

Một service có thể có:

```text
0 provider
1 provider
nhiều provider
```

Do đó host phải thiết kế cả trường hợp “không tìm thấy provider” và “có nhiều provider”.

Nguồn provider phụ thuộc overload/context được dùng:

- class loader based discovery có thể tìm provider trong named module và unnamed module/class path tương ứng;
- layer based discovery dùng `ServiceLoader.load(layer, service)` và tìm trong layer cùng ancestor layers.

Điểm cần nhớ: `ServiceLoader` **tìm + nạp** provider; ứng dụng vẫn sở hữu chính sách lựa chọn và hành vi domain sau đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lazy-discovery-and-cache">Lazy Discovery, Provider Cache và Instantiation</a>

<details>
<summary>Click for details</summary>

Provider được tìm và khởi tạo theo kiểu **lazy**, không phải toàn bộ được instantiate ngay khi gọi `ServiceLoader.load(...)`.

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

// Chưa có nghĩa tất cả provider đã được instantiate.
```

Khi iterate, loader sẽ:

```text
provider đã nằm trong cache
→ yield trước theo load/instantiation order

provider chưa được xử lý
→ locate lazily
→ instantiate khi cần
→ thêm vào cache
```

Cache thuộc **ServiceLoader instance**. Vì vậy tạo hai `ServiceLoader` riêng nghĩa là có hai discovery/cache context riêng.

Laziness hữu ích khi có nhiều provider nhưng request chỉ cần provider đầu tiên phù hợp. Tuy nhiên nó cũng có nghĩa lỗi cấu hình có thể xuất hiện trong lúc iterate/stream, không nhất thiết ở thời điểm `load(...)`.

```java
for (ReportExporter exporter : loader) {
    if (exporter.supports(report)) {
        exporter.export(report);
        break;
    }
}
```

Đừng dựa vào cache như một singleton registry ở cấp ứng dụng. Cache là hành vi của loader phục vụ discovery, không phải contract vòng đời của kiến trúc plugin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iterator-vs-provider-stream">Iterator so với ServiceLoader.Provider Stream</a>

<details>
<summary>Click for details</summary>

Hai API traversal chính có semantic khác nhau:

```text
iterator()/enhanced for
→ yield instance của service/provider
→ provider cần được instantiate để yield

stream()
→ yield ServiceLoader.Provider<S>
→ có thể inspect provider type trước khi Provider.get()
```

Ví dụ iterator:

```java
for (ReportExporter exporter : ServiceLoader.load(ReportExporter.class)) {
    if ("pdf".equals(exporter.format())) {
        exporter.export(report);
    }
}
```

Ví dụ stream khi muốn inspect class metadata trước:

```java
List<ReportExporter> exporters =
        ServiceLoader.load(ReportExporter.class)
                .stream()
                .filter(p -> p.type().isAnnotationPresent(StableProvider.class))
                .map(ServiceLoader.Provider::get)
                .toList();
```

`Provider.type()` cho biết type mà `ServiceLoader.Provider` expose; `Provider.get()` mới lấy instance. Với provider dùng constructor, đây là provider class. Với explicit-module provider dùng static `provider()` method, `type()` trả **return type của `provider()`**, không nhất thiết là class được ghi trong `provides ... with ...`.

Stream không làm discovery trở thành eager mặc định. Provider vẫn được locate lazily khi stream pipeline được consume.

Chọn iterator khi cần instance trực tiếp; chọn `Provider` stream khi việc lựa chọn có thể dựa trên metadata của type mà `Provider.type()` expose và muốn tránh khởi tạo provider quá sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="discovery-classloader-context">ClassLoader Context của Provider Discovery</a>

<details>
<summary>Click for details</summary>

Overload phổ biến nhất:

```java
ServiceLoader.load(ReportExporter.class)
```

tương đương việc dùng **thread context class loader** hiện tại làm điểm bắt đầu discovery.

Điều này quan trọng trong container/plugin environment, nơi code host có thể được load bởi một loader nhưng extension lại chỉ visible từ context loader khác.

Khi cần kiểm soát rõ context, dùng overload:

```java
ClassLoader pluginLoader = ...;

ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class, pluginLoader);
```

Class-loader based discovery không chỉ là “scan một folder”. Java phải tôn trọng visibility, parent delegation, named modules gắn với loader/layer và provider-configuration resources của unnamed modules.

Một hệ quả thực tế: không nên cache kết quả của `ServiceLoader.load(service)` ở phạm vi toàn JVM nếu application có nhiều context class loader khác nhau. Provider visible với application A có thể không phù hợp hoặc không visible với application B.

Nếu architecture cần plugin class-loader isolation, hãy coi loader context là một phần explicit của plugin boundary thay vì phụ thuộc ngầm vào thread context loader ở mọi nơi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reload-and-concurrency">reload(), Cache Invalidation và Concurrency Boundary</a>

<details>
<summary>Click for details</summary>

`reload()` làm đúng một việc cốt lõi:

```text
clear provider cache của ServiceLoader instance
```

Sau đó iteration/stream tiếp theo sẽ discovery lại từ đầu theo context của loader.

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

// ... provider được sử dụng
loader.reload();
```

`reload()` **không**:

- gọi lifecycle `stop()` của provider cũ;
- đóng tài nguyên provider đang giữ;
- unload class;
- unload module;
- bảo đảm JAR cũ có thể xóa;
- biến plugin architecture thành hot-reload system.

Iterator/stream cũ cũng không nên tiếp tục dùng sau khi cache bị reload; chúng có hành vi fail-fast khi cache bị thay đổi.

Ngoài ra, một `ServiceLoader` instance **không thread-safe**. Nếu nhiều thread cần discovery, host phải tự đồng bộ hoặc thiết kế loader ownership phù hợp.

Plugin reload thực sự cần vòng đời + dọn dẹp tài nguyên + thay loader/layer. `ServiceLoader.reload()` chỉ là một primitive nhỏ của discovery cache trong luồng đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serviceconfigurationerror">ServiceConfigurationError và Failure Surface</a>

<details>
<summary>Click for details</summary>

`ServiceConfigurationError` báo rằng service-provider environment/configuration không hợp lệ hoặc provider không thể load/instantiate theo contract.

Các nguyên nhân điển hình:

- provider class không load được;
- provider không assignable cho service khi constructor model yêu cầu;
- thiếu public no-arg provider constructor;
- `provider()` method có signature/return type không hợp lệ;
- `provider()` trả `null` hoặc ném exception;
- file `META-INF/services/...` sai format;
- I/O error khi đọc provider configuration.

Lỗi có thể xuất hiện trong `hasNext()`, `next()` hoặc khi consume stream vì discovery là lazy.

```java
try {
    for (ReportExporter exporter : loader) {
        register(exporter);
    }
} catch (ServiceConfigurationError error) {
    log.error("Broken exporter provider", error);
}
```

Đừng mặc định swallow mọi `ServiceConfigurationError`. Provider configuration bị hỏng thường là deployment/configuration defect cần quan sát rõ.

Nếu business cho phép bỏ qua một provider lỗi và tiếp tục provider khác, policy đó phải explicit và được test. `ServiceLoader` có thể cố tiếp tục trong một số iteration path, nhưng recovery toàn diện không phải guarantee để architecture phụ thuộc mù quáng.

Sau khi hiểu **discovery có thể trả 0, 1, nhiều hoặc provider lỗi**, bước tiếp theo là tách discovery khỏi quyết định nghiệp vụ: provider nào thực sự nên được chọn cho request hiện tại và ambiguity phải được xử lý ra sao.

</details>

- [Quay lại đầu trang](#back-to-top)
