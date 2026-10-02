<a id="back-to-top"></a>

# ModuleLayer cho Runtime Plugin

## Menu
- [Khi nào Plugin Architecture cần ModuleLayer?](#modulelayer-purpose)
- [ModuleFinder, Configuration và Resolution](#configuration-and-resolution)
- [resolve() so với resolveAndBind()](#resolve-vs-resolveandbind)
- [One Loader, Many Loaders và Custom Loader Mapping](#layer-loader-strategies)
- [ServiceLoader.load(layer, service)](#serviceloader-layer-discovery)
- [Tạo Layer mới khi thay đổi Plugin Set](#layer-replacement-model)

## <a id="modulelayer-purpose">Khi nào Plugin Architecture cần ModuleLayer?</a>

<details>
<summary>Click for details</summary>

`ModuleLayer` hữu ích khi plugin là **explicit named modules** và host cần tạo thêm module graph trong JVM sau boot layer.

Đừng dùng `ModuleLayer` chỉ vì provider nằm trong JAR. Nếu tập plugin cố định khi process khởi động, discovery bằng `ServiceLoader` trên class path/module path thường đơn giản hơn.

`ModuleLayer` phù hợp hơn khi cần:

- resolve module set từ directory/plugin repository riêng;
- giữ module graph tách khỏi boot layer;
- chọn one-loader/many-loader mapping;
- khám phá provider trong layer mới;
- replace plugin set bằng layer mới thay vì mutate boot graph.

Mental model:

```text
module artifacts
→ ModuleFinder
→ Configuration
→ ModuleLayer
→ ServiceLoader.load(layer, service)
```

Layer là runtime representation của một resolved module graph, không phải mutable list plugin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-and-resolution">ModuleFinder, Configuration và Resolution</a>

<details>
<summary>Click for details</summary>

`ModuleFinder` tìm module artifacts; `Configuration` biểu diễn **resolved readability graph**.

Ví dụ:

```java
Path plugins = Path.of("plugins");
ModuleFinder finder = ModuleFinder.of(plugins);

ModuleLayer parent = ModuleLayer.boot();
Configuration parentConfig = parent.configuration();

Configuration config = parentConfig.resolve(
        finder,
        ModuleFinder.of(),
        Set.of("com.example.report.plugins"));
```

Resolution bắt đầu từ root modules và tính graph các dependency/readability cần thiết.

Sau đó config mới được define thành runtime layer:

```java
ModuleLayer layer = parent.defineModulesWithOneLoader(
        config,
        ClassLoader.getSystemClassLoader());
```

Tách `Configuration` và `ModuleLayer` là điểm quan trọng:

```text
Configuration
→ graph đã resolve

ModuleLayer
→ runtime modules + mapping sang ClassLoader
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resolve-vs-resolveandbind">resolve() so với resolveAndBind()</a>

<details>
<summary>Click for details</summary>

`resolve()` resolve root modules và dependency graph thông thường.

`resolveAndBind()` thực hiện resolution **kèm service binding**: với service được modules trong graph `uses`, resolution có thể kéo module provider phù hợp vào configuration.

Ví dụ:

```java
Configuration config = parent.configuration()
        .resolveAndBind(
                finder,
                ModuleFinder.of(),
                Set.of("com.example.report.host"));
```

Nếu host module `uses com.example.export.ReportExporter`, và plugin modules trong finder `provides` service đó, service binding cho phép provider modules tham gia graph mà host không `requires` trực tiếp từng provider.

Đây là điểm kết nối JPMS service model với runtime extensibility.

Không dùng `resolveAndBind()` như “scan mọi module và load hết”. Nó vẫn chịu module observability/resolution rules và bắt đầu từ roots/parents/finder đã chỉ định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layer-loader-strategies">One Loader, Many Loaders và Custom Loader Mapping</a>

<details>
<summary>Click for details</summary>

Khi define layer, module phải được map sang class loader.

Java cung cấp ba hướng chính:

```text
defineModulesWithOneLoader
→ tất cả module trong configuration dùng một loader mới

defineModulesWithManyLoaders
→ mỗi module có loader riêng

defineModules
→ ứng dụng cung cấp function module-name → ClassLoader
```

One-loader đơn giản và phù hợp khi module set tin cậy nhau và không cần dependency isolation mạnh.

Many-loaders tăng mức cô lập nhưng tạo nhiều ranh giới class loader hơn; object/type đi qua ranh giới module vẫn phải tuân readability và shared type identity tương ứng.

Custom mapping dành cho kiến trúc đặc biệt và kéo theo trách nhiệm lớn hơn về trạng thái sẵn sàng và delegation của loader.

Với `defineModules(Configuration, Function<String, ClassLoader>)`, ứng dụng phải bảo đảm các loader:

- tôn trọng module readability khi delegation;
- nên là parallel-capable để giảm nguy cơ deadlock trong class loading;
- đã sẵn sàng load class/resource của module trước khi layer được sử dụng.

API cũng không bảo đảm việc tạo layer bằng custom mapping là atomic trong mọi cách triển khai: thao tác có thể thất bại sau khi một phần module đã được define vào JVM. Vì vậy đây là công cụ dành cho trường hợp thật sự cần custom loader topology, không phải lựa chọn mặc định cho plugin system.

Không chọn many-loaders chỉ vì “nhiều plugin”. Yêu cầu cô lập, dependency conflict và chiến lược unload/replacement mới là đầu vào chính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serviceloader-layer-discovery">ServiceLoader.load(layer, service)</a>

<details>
<summary>Click for details</summary>

Sau khi layer được define, provider có thể được khám phá trực tiếp theo layer:

```java
ServiceLoader<ReportExporter> exporters =
        ServiceLoader.load(layer, ReportExporter.class);
```

Overload này tìm provider trong:

```text
current layer
→ ancestor layers theo traversal rules
```

Nó **không** tìm provider từ unnamed modules/class path trong phạm vi khám phá của overload theo layer này.

Named-module caller vẫn phải thỏa service usage contract (`uses`).

Điểm mạnh của layer-based discovery là ngữ cảnh khám phá được chỉ rõ: host biết chính xác module-layer graph nào đang đại diện thế hệ plugin hiện tại.

Việc lựa chọn và quản lý vòng đời vẫn là trách nhiệm của ứng dụng. `ServiceLoader.load(layer, ...)` không tự activate plugin hay tự giải quyết trường hợp nhiều provider cùng phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layer-replacement-model">Tạo Layer mới khi thay đổi Plugin Set</a>

<details>
<summary>Click for details</summary>

`ModuleLayer` được tạo từ một `Configuration` đã resolve. Nó không phải mutable registry để add/remove module tùy ý sau đó.

Khi tập plugin thay đổi, mô hình dễ suy luận hơn là:

```text
old artifacts
→ old Configuration
→ old Layer

new artifacts
→ new Configuration
→ new Layer
```

Host có thể:

1. discover/validate new layer;
2. activate provider mới;
3. chuyển registry/context sang thế hệ mới theo cách nguyên tử;
4. drain/cleanup old plugin generation;
5. release reference tới old layer/loaders nếu muốn GC.

`ModuleLayer.Controller` có một số khả năng điều chỉnh reads/exports/opens, nhưng nó không biến layer thành general module add/remove container.

Nếu reference, thread hoặc tài nguyên cũ vẫn giữ class loader, class của layer/plugin cũ vẫn chưa thể unload dù ứng dụng đã chuyển sang layer mới.

</details>

- [Quay lại đầu trang](#back-to-top)
