<a id="back-to-top"></a>

# Thiết kế cô lập Plugin

## Menu
- [Vì sao Plugin cần Isolation Boundary?](#plugin-isolation-purpose)
- [Class Identity và Shared Contract Boundary](#class-identity-boundaries)
- [Shared Dependency so với Private Plugin Dependency](#shared-vs-private-dependencies)
- [Các chiến lược ClassLoader Isolation ở mức kiến trúc](#classloader-isolation-strategies)
- [Resource, Thread và Context Boundary của Plugin](#isolation-resource-boundaries)
- [Failure Boundary và cô lập lỗi giữa Host với Plugin](#plugin-failure-boundaries)
- [Unloadability và Memory/Resource Leak Risk](#unloadability-and-leak-risks)

## <a id="plugin-isolation-purpose">Vì sao Plugin cần Isolation Boundary?</a>

<details>
<summary>Click for details</summary>

Plugin isolation tồn tại để một extension không vô tình chia sẻ toàn bộ không gian class/dependency/tài nguyên với host và plugin khác.

Không isolation, hai plugin có thể:

- yêu cầu version khác nhau của cùng library;
- nhìn thấy chi tiết triển khai không nên thấy;
- giữ thread/tài nguyên khiến host không cleanup được;
- làm crash/failure lan rộng qua shared global state.

Isolation không nhất thiết luôn cần custom class loader. Mức isolation phụ thuộc requirement:

```text
logical isolation
→ contract + lifecycle + không dùng shared mutable state

class-loader isolation
→ dependency/type namespace boundary

module-layer isolation
→ named-module graph + loader mapping

process isolation
→ ranh giới failure/security/tài nguyên mạnh hơn
```

Module này tập trung isolation trong cùng JVM; process/container isolation thuộc architecture khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-identity-boundaries">Class Identity và Shared Contract Boundary</a>

<details>
<summary>Click for details</summary>

Trong JVM, type identity không chỉ là fully-qualified class name. Một mental model quan trọng là:

```text
type identity
≈ class name + defining ClassLoader
```

Hai loader có thể load hai bản `com.example.api.ReportExporter`; JVM có thể xem chúng là hai type khác nhau.

Đây là lý do **shared contract** thường phải được load từ common parent/shared loader mà cả host và plugin cùng nhìn thấy.

```text
shared parent loader
→ ReportExporter contract

plugin loader A
→ PdfExporter

plugin loader B
→ CsvExporter
```

Nếu mỗi plugin bundle một copy riêng của contract class và load child-first, cast về host `ReportExporter` có thể fail dù class name giống hệt.

Chi tiết delegation mechanics thuộc ClassLoader module; điều cần giữ ở đây là contract type phải có một identity chung qua isolation boundary.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-vs-private-dependencies">Shared Dependency so với Private Plugin Dependency</a>

<details>
<summary>Click for details</summary>

Dependency nên được chia thành hai nhóm:

```text
shared
→ contract/API + library cần interoperability thật sự

private
→ thư viện triển khai chỉ plugin đó cần
```

Share quá nhiều dependency làm plugin mất isolation. Share quá ít có thể tạo duplicate type identity cho object phải truyền qua boundary.

Ví dụ không nên để DTO qua contract thuộc library mà mỗi plugin load version riêng nếu object cần cast/serialize bằng type JVM cụ thể.

Một policy thường gặp:

- JDK types và contract API: shared;
- logging façade/context do host cung cấp: shared khi contract yêu cầu;
- parser/rendering/native client riêng: private;
- transitive implementation dependencies: private mặc định.

Boundary càng nhỏ, compatibility surface càng dễ kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classloader-isolation-strategies">Các chiến lược ClassLoader Isolation ở mức kiến trúc</a>

<details>
<summary>Click for details</summary>

Ở mức architecture có vài chiến lược loader phổ biến:

```text
single loader
→ đơn giản, ít isolation

parent-first plugin loader
→ ưu tiên shared/host classes từ parent

child-first plugin loader
→ plugin ưu tiên private version, cần careful package exclusions

one loader per plugin
→ isolation mạnh hơn, lifecycle/unload tracking rõ hơn
```

Không có chiến lược “tốt nhất” cho mọi plugin system.

Parent-first giúp contract identity ổn định nhưng plugin có thể bị ép dùng dependency version của host. Child-first cho phép private dependency version nhưng dễ duplicate contract/JDK-adjacent type nếu policy package không rõ.

ModuleLayer cung cấp one-loader/many-loader mapping cho named modules; custom mapping còn linh hoạt hơn nhưng tăng trách nhiệm về readability và class loading.

Thiết kế loader phải bắt đầu từ isolation requirement, không bắt đầu từ “hãy dùng child-first vì plugin framework thường làm vậy”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="isolation-resource-boundaries">Resource, Thread và Context Boundary của Plugin</a>

<details>
<summary>Click for details</summary>

Class isolation chưa đủ nếu plugin vẫn làm rò rỉ tài nguyên ra ngoài boundary.

Các tài nguyên thường giữ plugin sống:

- non-daemon thread;
- executor/scheduler;
- `ThreadLocal` trên host thread;
- static registry/listener;
- shutdown hook;
- cache ở host giữ instance/class;
- file/socket/native resource còn mở;
- thread context class loader trỏ vào plugin loader.

Ví dụ plugin tạo executor thì phải có cleanup:

```java
final class PdfPlugin implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void close() {
        executor.shutdown();
    }
}
```

Host cũng phải ngừng giữ reference tới provider, class, loader và metadata object nếu muốn loader trở nên collectible.

Isolation boundary vì vậy là **class + tài nguyên + thread + quyền sở hữu reference**, không chỉ là tách folder/JAR.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-failure-boundaries">Failure Boundary và cô lập lỗi giữa Host với Plugin</a>

<details>
<summary>Click for details</summary>

Một plugin lỗi không nên mặc định làm toàn host chuyển sang trạng thái không xác định.

Failure boundary nên phân loại ít nhất:

```text
discovery/config failure
→ provider không load được

activation failure
→ provider load được nhưng không start được

request failure
→ một thao tác của plugin thất bại

fatal integrity failure
→ contract/invariant host bị phá, có thể cần fail host
```

Host có thể wrap invocation:

```java
try {
    exporter.export(report);
} catch (PluginException ex) {
    metrics.pluginFailure(exporter.getClass().getName());
    throw ex;
}
```

Nhưng catch exception không tạo isolation kỳ diệu. Plugin vẫn có thể làm deadlock thread, dùng cạn memory hoặc làm hỏng shared mutable state.

Khi failure containment cần mạnh hơn mức JVM boundary cung cấp, process isolation có thể là lựa chọn đúng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unloadability-and-leak-risks">Unloadability và Memory/Resource Leak Risk</a>

<details>
<summary>Click for details</summary>

“Unload plugin” trong JVM thực chất phụ thuộc vào việc **defining class loader trở nên unreachable** cùng toàn bộ class/object do nó giữ.

Không có API kiểu:

```java
Class.unload(); // không tồn tại
```

Một custom/plugin class loader có thể được GC khi không còn strong reference reachable tới loader, class hoặc object gắn với nó.

Các leak phổ biến:

- static field trong host giữ plugin instance;
- thread còn chạy với context class loader là plugin loader;
- global cache giữ `Class<?>` của plugin;
- listener chưa unregister;
- executor/thread chưa stop;
- JNI/native callback giữ reference.

Do đó unloadability phải được thiết kế từ đầu bằng lifecycle + ownership + reference cleanup.

Đừng dùng `ServiceLoader.reload()` như bằng chứng plugin đã unload; nó chỉ clear provider cache của loader đó.

</details>

- [Quay lại đầu trang](#back-to-top)
