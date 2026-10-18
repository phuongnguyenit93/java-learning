<a id="back-to-top"></a>

# Vòng đời Plugin

## Menu
- [Mô hình vòng đời Plugin](#plugin-lifecycle-model)
- [Từ Discovery tới Activation](#discovery-to-activation)
- [Initialization và Startup Contract](#initialization-and-startup)
- [Deactivation, Cleanup và Resource Ownership](#deactivation-and-cleanup)
- [Rediscovery, ServiceLoader.reload() và Plugin Reload](#rediscovery-vs-reload)
- [Replacement, Upgrade và Rollback Boundary](#replacement-and-upgrade)

## <a id="plugin-lifecycle-model">Mô hình vòng đời Plugin</a>

<details>
<summary>Click for details</summary>

`ServiceLoader` không định nghĩa vòng đời plugin. Nếu plugin giữ tài nguyên hoặc có hành vi startup/shutdown, host phải tự thiết kế lifecycle model.

Một model đơn giản:

```text
DISCOVERED
→ VALIDATED
→ INITIALIZED
→ ACTIVE
→ STOPPING
→ STOPPED
```

Không phải plugin nào cũng cần đủ các trạng thái này. Stateless provider có thể chỉ cần discovery → use.

Mô hình trạng thái hữu ích khi:

- initialization có thể fail;
- activation cần tài nguyên;
- plugin nhận request concurrent;
- replacement/upgrade cần drain traffic;
- cleanup phải bảo đảm chạy.

Lifecycle nên thuộc host orchestration, còn plugin cung cấp hook/capability cần thiết qua contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="discovery-to-activation">Từ Discovery tới Activation</a>

<details>
<summary>Click for details</summary>

Discovery chỉ chứng minh rằng provider **có thể được tìm thấy**; nó chưa chứng minh provider sẵn sàng phục vụ.

Flow activation nên tách các bước:

```text
discover
→ validate class/metadata
→ validate compatibility
→ create provider
→ initialize tài nguyên
→ mark ACTIVE
```

Ví dụ host có thể discovery `PdfExporter`, nhưng provider chỉ activate thành công sau khi native font engine hoặc license configuration sẵn sàng.

Đừng publish plugin vào registry phục vụ request trước khi activation hoàn tất.

Nếu nhiều plugin start cùng lúc, cần policy rõ về partial failure:

- fail toàn host nếu required plugin fail;
- bỏ qua optional plugin;
- startup degraded mode;
- retry theo policy riêng.

Selection chỉ nên thấy provider ở trạng thái phù hợp để nhận request.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="initialization-and-startup">Initialization và Startup Contract</a>

<details>
<summary>Click for details</summary>

Initialization là nơi plugin nhận context/config và tạo tài nguyên cần thiết.

Ví dụ contract lifecycle riêng:

```java
public interface Plugin {
    void initialize(PluginContext context) throws PluginException;
    void start() throws PluginException;
}
```

Tách `initialize` và `start` hữu ích khi host muốn:

1. construct + validate tất cả plugin;
2. sau đó mới publish/activate đồng loạt.

Initialization nên idempotent hay one-shot phải được contract quy định rõ. Đừng mặc định gọi lặp an toàn.

Nếu provider constructor làm quá nhiều việc (network call, thread creation, mở file lớn), discovery có thể vô tình gây side effect. Với plugin phức tạp, giữ constructor nhẹ và chuyển work sang lifecycle hook thường dễ kiểm soát hơn.

Startup failure nên giữ original cause và plugin identity để observability/debugging rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deactivation-and-cleanup">Deactivation, Cleanup và Resource Ownership</a>

<details>
<summary>Click for details</summary>

Deactivation cần trả lời: khi nào plugin ngừng nhận work mới và ai đóng tài nguyên?

Flow thường là:

```text
mark unavailable
→ stop new requests
→ wait/cancel in-flight work
→ invoke plugin stop/close
→ release references/resources
```

`AutoCloseable` có thể là contract phù hợp nếu semantics đơn giản:

```java
public interface ManagedExporter extends ReportExporter, AutoCloseable {
    @Override
    void close();
}
```

Nhưng lifecycle không nhất thiết phải map vào `close()` nếu plugin có nhiều phase.

Quyền sở hữu tài nguyên phải rõ ràng. Nếu host cấp executor cho plugin, host thường đóng executor. Nếu plugin tự tạo scheduler, plugin phải dừng nó. Không rõ ownership là nguồn leak rất phổ biến.

Cleanup nên chạy cả khi initialization chỉ thành công một phần; vì vậy code lifecycle cần xử lý rollback/trạng thái chưa hoàn chỉnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rediscovery-vs-reload">Rediscovery, ServiceLoader.reload() và Plugin Reload</a>

<details>
<summary>Click for details</summary>

Ba khái niệm dễ bị trộn:

```text
rediscovery
→ tìm lại provider từ runtime environment

ServiceLoader.reload()
→ clear cache của một ServiceLoader rồi discovery lại

plugin reload
→ deactivate old plugin + release resources + load/activate replacement
```

`ServiceLoader.reload()` chỉ thực hiện phần giữa rất nhỏ. Nó không shutdown provider cũ và không làm JVM unload class.

Nếu plugin set thay đổi bằng JAR/class-loader mới, host có thể cần tạo discovery context mới. Với modular plugin, thường cần `Configuration`/`ModuleLayer` mới.

Hot reload là capability architecture riêng và phải test các race:

- request đang dùng old plugin;
- new plugin activation fail;
- old plugin cleanup chậm;
- reference cũ còn bị giữ.

Nếu không có requirement rõ cho hot reload, restart process thường đơn giản và an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="replacement-and-upgrade">Replacement, Upgrade và Rollback Boundary</a>

<details>
<summary>Click for details</summary>

Replacement/upgrade an toàn thường cần **prepare → switch → retire** thay vì stop cũ trước rồi hy vọng mới start được.

```text
discover candidate
→ validate compatibility
→ initialize new plugin
→ health check
→ atomically switch registry/reference
→ drain old plugin
→ stop old plugin
```

Nếu new plugin fail trước switch, host vẫn giữ old provider.

Rollback sau khi đã switch khó hơn nếu request đã tạo side effect với version mới. Vì vậy plugin lifecycle chỉ giải quyết runtime object transition; domain/data migration có thể cần strategy riêng.

Versioned registry hoặc immutable snapshot giúp request đang chạy giữ consistent provider reference trong lúc replacement xảy ra.

Một architecture không hỗ trợ rollback nên nói rõ thay vì giả vờ “reload” luôn reversible.

</details>

- [Quay lại đầu trang](#back-to-top)
