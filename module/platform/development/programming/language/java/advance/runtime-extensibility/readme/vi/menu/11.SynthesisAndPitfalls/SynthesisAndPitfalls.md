<a id="back-to-top"></a>

# Tổng hợp Runtime Extensibility và các bẫy thiết kế

## Menu
- [Flow Runtime Extensibility End-to-End](#extensibility-end-to-end-flow)
- [Chọn Static Wiring, ServiceLoader hay ModuleLayer](#mechanism-selection)
- [Các bẫy thiết kế thường gặp](#common-design-pitfalls)
- [Checklist Failure Containment và Resource Cleanup](#failure-containment-checklist)
- [Khi nào phải handoff sang ClassLoader, JPMS, Framework Plugin hoặc Instrumentation?](#boundary-handoffs)
- [Mental Model tổng hợp cần giữ lại](#runtime-extensibility-synthesis)

## <a id="extensibility-end-to-end-flow">Flow Runtime Extensibility End-to-End</a>

<details>
<summary>Click for details</summary>

Một luồng Runtime Extensibility hoàn chỉnh có thể tóm tắt như sau:

```text
1. Define stable service contract
        ↓
2. Package provider independently
        ↓
3. Deploy/register provider
        ↓
4. Discover provider
        ↓
5. Validate compatibility/capability
        ↓
6. Select provider
        ↓
7. Initialize/activate
        ↓
8. Execute behind failure boundary
        ↓
9. Deactivate/cleanup/replacement
```

`ServiceLoader` chỉ trực tiếp hỗ trợ một phần của bước 4 và việc khởi tạo provider. Thiết kế SPI, lựa chọn provider, vòng đời, cô lập và compatibility vẫn thuộc kiến trúc của ứng dụng.

Nếu dùng JPMS dynamic plugins, bước 3–4 có thêm:

```text
ModuleFinder → Configuration/resolveAndBind → ModuleLayer → ServiceLoader
```

Giữ luồng này giúp tránh kỳ vọng một API đơn lẻ giải quyết toàn bộ plugin system.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mechanism-selection">Chọn Static Wiring, ServiceLoader hay ModuleLayer</a>

<details>
<summary>Click for details</summary>

Chọn cơ chế theo yêu cầu, không theo độ “advanced”.

| Nhu cầu | Cơ chế thường đủ |
|---|---|
| Implementation biết trước lúc build | Static wiring / DI |
| Provider JAR được discover ở startup | `ServiceLoader` class path |
| Named-module service providers | `uses` / `provides` + `ServiceLoader` |
| Runtime module graph riêng | `Configuration` + `ModuleLayer` |
| Dependency isolation mạnh trong JVM | dedicated loaders/layers |
| Cô lập lỗi mạnh | process boundary có thể phù hợp hơn |

Đừng dùng `ModuleLayer` nếu static startup module path đã đủ.

Đừng viết custom plugin framework nếu `ServiceLoader` + một registry nhỏ đã đáp ứng yêu cầu.

Ngược lại, đừng coi `ServiceLoader.reload()` là hot-reload architecture nếu plugin có trạng thái, tài nguyên và vòng đời class loader thực sự.

Độ phức tạp chỉ đáng đánh đổi khi nó giải quyết một yêu cầu cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-design-pitfalls">Các bẫy thiết kế thường gặp</a>

<details>
<summary>Click for details</summary>

Các bẫy thường gặp:

1. **Host require/import provider trực tiếp** → mất decoupling.
2. **Dùng thứ tự discovery làm priority** → hành vi phụ thuộc topology triển khai.
3. **Provider constructor làm công việc nặng** → discovery gây side effect khó kiểm soát.
4. **Không định nghĩa chính sách khi thiếu provider** → feature fail muộn.
5. **Share quá nhiều dependency** → version conflict.
6. **Load riêng contract ở từng plugin loader** → type identity mismatch.
7. **Gọi `reload()` rồi nghĩ class đã unload** → rò rỉ tài nguyên/class loader.
8. **Không cleanup thread/ThreadLocal/listener** → old plugin generation không collectible.
9. **SPI evolve không contract-test provider cũ** → incompatibility chỉ lộ ở deploy.
10. **ModuleLayer nhưng vẫn hard-code provider modules trong host** → architecture phức tạp nhưng không đạt extensibility.

Một lượt review thiết kế tốt nên đặt các câu hỏi trên trước khi phần triển khai trở nên lớn và khó đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="failure-containment-checklist">Checklist Failure Containment và Resource Cleanup</a>

<details>
<summary>Click for details</summary>

Trước khi activate hoặc replace plugin, kiểm tra ít nhất:

```text
[ ] provider discovery context explicit?
[ ] service/capability compatibility đã validate?
[ ] ambiguity/priority policy rõ?
[ ] required provider thiếu thì fail thế nào?
[ ] initialization failure rollback resource nào?
[ ] plugin tạo thread/executor/socket/file nào?
[ ] ai sở hữu cleanup?
[ ] in-flight request được drain/cancel thế nào?
[ ] host/global cache có giữ plugin class/instance không?
[ ] context ClassLoader/ThreadLocal có reset không?
[ ] observability ghi plugin identity/version/cause chưa?
```

Checklist này không thay thế design, nhưng giúp biến “reload plugin” từ một từ mơ hồ thành một lifecycle có ownership cụ thể.

Failure containment nên được test bằng provider cố ý fail ở discovery, initialization, request và cleanup — không chỉ test happy path.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boundary-handoffs">Khi nào phải handoff sang ClassLoader, JPMS, Framework Plugin hoặc Instrumentation?</a>

<details>
<summary>Click for details</summary>

Biết lúc nào **không tiếp tục đào sâu trong module này** cũng quan trọng.

Handoff khi câu hỏi chính trở thành:

```text
ClassLoader delegation/class identity mechanics chi tiết?
→ Java Core ClassLoader

module readability/exports/opens/resolution đầy đủ?
→ JPMS / Module System

framework extension points, DI scopes, bean lifecycle?
→ framework plugin/module owner

redefine/retransform bytecode hoặc Java Agent?
→ Instrumentation

process sandbox/security/OS isolation?
→ system/infrastructure architecture
```

Runtime Extensibility chỉ giữ đủ boundary concept để thiết kế host/plugin đúng; nó không duplicate full curriculum của các owner trên.

Boundary rõ giúp learner biết concept nào là “tool dùng để implement plugin system” và concept nào chính là core responsibility của module này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-extensibility-synthesis">Mental Model tổng hợp cần giữ lại</a>

<details>
<summary>Click for details</summary>

Mental model cuối cùng cần giữ:

```text
Runtime Extensibility
=
stable contract
+ independently deployable provider
+ explicit discovery context
+ application-owned selection policy
+ lifecycle
+ compatibility
+ isolation/failure boundary
```

Trong Java:

```text
SPI
→ định nghĩa capability

ServiceLoader
→ locate/load provider

Class Path / Module Path
→ deployment models

ClassLoader
→ visibility + type identity boundary

Configuration / ModuleLayer
→ dynamic named-module graph
```

Nếu chỉ nhớ một nguyên tắc: **discovery không phải toàn bộ plugin architecture**.

Hãy bắt đầu bằng contract và requirement. Chỉ thêm `ServiceLoader`, custom loader hoặc `ModuleLayer` khi mỗi cơ chế giải quyết một vấn đề rõ ràng mà level đơn giản hơn không giải quyết được.

</details>

- [Quay lại đầu trang](#back-to-top)
