<a id="back-to-top"></a>

# Interpreter, JIT và Adaptive Optimization

## Menu
- [Mô hình execution engine](#execution-engine-model)
- [Vai trò của interpreter](#interpreter-role)
- [Profiling và hot code](#profiling-and-hot-code)
- [Mô hình JIT compilation](#jit-compilation-model)
- [Tiered và adaptive compilation](#tiered-and-adaptive-compilation)
- [Compiled code và code cache](#compiled-code-and-code-cache)
- [Inlining và runtime optimization](#inlining-and-runtime-optimization)
- [Escape Analysis và Scalar Replacement](#escape-analysis-and-scalar-replacement)
- [Speculation và deoptimization](#speculation-and-deoptimization)
- [Ranh giới giữa optimization và Java semantics](#optimization-semantics-boundary)

## <a id="execution-engine-model">Mô hình execution engine</a>

<details>
<summary>Click for details</summary>

JVM không bắt buộc bytecode luôn được thực thi theo đúng một kỹ thuật. Một implementation có thể dùng interpreter, compiler hoặc kết hợp nhiều tầng miễn Java-level semantics vẫn được giữ.

HotSpot điển hình dùng mô hình **adaptive execution**:

```text
bytecode
   ↓
interpreter chạy trước
   ↓
runtime profiling thu thập hành vi
   ↓
hot code được compile
   ↓
optimized native code
```

Lợi ích là runtime không phải compile toàn bộ ứng dụng trước khi chạy, đồng thời có thể dùng thông tin thực tế của workload để tối ưu code quan trọng.

Execution engine vì thế nên được hiểu là subsystem biến bytecode semantics thành machine execution, không phải một class/API mà ứng dụng gọi trực tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpreter-role">Vai trò của interpreter</a>

<details>
<summary>Click for details</summary>

Interpreter có thể bắt đầu thực thi bytecode ngay mà không cần chờ compile toàn bộ method sang machine code. Điều này giúp startup nhanh hơn và tránh tốn compiler effort cho code chỉ chạy một vài lần.

Trade-off:

```text
interpreter
→ startup tốt
→ dễ quan sát hành vi runtime sớm
→ nhưng peak execution thường chậm hơn optimized native code
```

HotSpot dùng interpreter như một phần của adaptive strategy, không phải dấu hiệu rằng “Java luôn interpreted”.

Một ứng dụng chạy lâu có thể dành phần lớn execution time trong compiled code, trong khi cold path vẫn interpreted hoặc ít tối ưu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="profiling-and-hot-code">Profiling và hot code</a>

<details>
<summary>Click for details</summary>

Runtime profiling thu thập thông tin về code thực sự chạy: method nào được gọi nhiều, branch nào thường được chọn, type nào thường xuất hiện ở call site và nhiều signal khác tùy implementation.

Từ đó runtime có thể xác định **hot code** — phần code đáng để dành compiler effort.

Điểm mạnh của runtime optimization là compiler có bằng chứng mà static compiler trước runtime chưa chắc có:

```text
source-level possibility
≠ hành vi quan sát được ở runtime
```

Nhưng profiling data có thể thay đổi theo workload. Optimization dựa trên assumption runtime vì vậy phải có cơ chế deoptimization khi assumption không còn đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jit-compilation-model">Mô hình JIT compilation</a>

<details>
<summary>Click for details</summary>

JIT (Just-In-Time) compilation chuyển bytecode của code đủ quan trọng thành native machine code trong lúc ứng dụng đang chạy.

Conceptual flow:

```text
method bytecode
→ collect profile
→ compilation request
→ optimizer
→ machine code
→ future calls có thể chạy compiled version
```

JIT có thể thực hiện optimization mà source compiler khó làm an toàn trước runtime, ví dụ aggressive inlining dựa trên observed receiver types.

Tuy nhiên compilation cũng tốn CPU/memory. Vì vậy runtime cần policy để chọn **code nào**, **khi nào**, và **mức tối ưu nào** đáng compile.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tiered-and-adaptive-compilation">Tiered và adaptive compilation</a>

<details>
<summary>Click for details</summary>

HotSpot có thể dùng nhiều compilation level để cân bằng startup và peak performance. Ý tưởng tiered compilation là không bắt method phải đi thẳng từ interpreted sang “tối ưu tối đa”.

```text
interpreter
→ lightweight/fast compilation
→ richer profiling
→ highly optimized compilation
```

Policy cụ thể là implementation/version detail. Learner nên giữ mental model:

- cold code không đáng tốn nhiều compiler effort;
- warm/hot code có thể được nâng level;
- runtime profile feedback ảnh hưởng quyết định;
- code có thể được recompiled hoặc deoptimized.

Do đó hiệu năng của ứng dụng Java thường có **giai đoạn warm-up** thay vì ổn định ngay từ request đầu tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compiled-code-and-code-cache">Compiled code và code cache</a>

<details>
<summary>Click for details</summary>

Native code do JIT sinh ra cần được lưu ở vùng memory executable của JVM process. Trong HotSpot, người học thường gặp khái niệm **code cache**.

Code cache là implementation-level resource, không phải runtime data area được JVMS định nghĩa như heap hay JVM stack.

Điều này tạo một practical distinction:

```text
Java heap
→ Java objects

code cache / compiled-code memory
→ native machine code do runtime sinh
```

Vì vậy bộ nhớ tiến trình có thể tăng do compilation ngay cả khi heap usage gần như không đổi.

Chi tiết quan sát code cache hoặc compiler events thuộc tooling/diagnostics; module này chỉ đặt compiled-code memory vào đúng execution model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="inlining-and-runtime-optimization">Inlining và runtime optimization</a>

<details>
<summary>Click for details</summary>

Inlining thay một call boundary bằng body của callee trong biểu diễn đã compile khi optimizer đánh giá có lợi và an toàn.

```text
before
caller → call smallMethod()

after conceptual inlining
caller → [body of smallMethod]
```

Inlining quan trọng không chỉ vì loại call overhead. Nó mở ra optimization mới xuyên qua boundary: constant propagation, dead-code elimination, escape analysis và nhiều transform khác.

Nhưng inlining không phải Java semantics. Source vẫn có method boundary về mặt language/debug meaning; runtime có thể chọn inline hoặc không tùy profile, size và policy.

Vì vậy benchmark “method nhỏ chắc chắn inline” mà không đo bằng chứng runtime là suy đoán về implementation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="escape-analysis-and-scalar-replacement">Escape Analysis và Scalar Replacement</a>

<details>
<summary>Click for details</summary>

Escape analysis cố gắng xác định object allocation có “escape” khỏi phạm vi mà compiler có thể suy luận hay không.

Nếu optimizer chứng minh một object không cần tồn tại như một heap object có thể quan sát được, HotSpot server compiler có thể áp dụng **scalar replacement**: loại allocation khỏi generated code và thay object aggregate bằng các scalar values riêng lẻ.

Ví dụ source:

```java
Point p = new Point(x, y);
return p.x() + p.y();
```

Không nên kết luận `new` trong source luôn đồng nghĩa một heap allocation vật lý tồn tại đến GC. Runtime optimizer có thể thay đổi cách triển khai miễn semantics của chương trình không đổi.

Một hiểu nhầm phổ biến là “escape analysis sẽ chuyển object lên stack”. HotSpot documentation cho Java 21 mô tả **scalar replacement/allocation elimination**, và nêu rõ server compiler không thay heap allocation bằng stack allocation cho object chỉ vì nó không escape.

Đây là lý do “đếm từ khóa `new`” không phải cách đáng tin để suy ra chi phí allocation thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="speculation-and-deoptimization">Speculation và deoptimization</a>

<details>
<summary>Click for details</summary>

Runtime optimizer có thể tối ưu dựa trên assumption từ profiling, ví dụ một call site trong thực tế chỉ thấy một receiver type trong thời gian dài.

Nếu assumption đủ mạnh, compiled code có thể được specialize. Nhưng workload có thể thay đổi. Khi assumption bị phá vỡ, JVM phải quay lại execution state an toàn hơn — quá trình này thường gọi là **deoptimization**.

```text
profile assumption
→ optimized compiled code
→ new runtime behavior invalidates assumption
→ deopt
→ interpreter/less-optimized state
→ có thể recompile
```

Speculation làm runtime optimization mạnh hơn nhưng cũng giải thích vì sao latency/hiệu năng có thể thay đổi theo từng phase của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="optimization-semantics-boundary">Ranh giới giữa optimization và Java semantics</a>

<details>
<summary>Click for details</summary>

Optimizer được phép thay đổi **cách** chương trình được thực thi, nhưng không được thay đổi hành vi mà Java/JVM contract yêu cầu phải observable.

Do đó:

- object có thể không tồn tại vật lý như source gợi ý;
- method có thể được inline;
- instruction order machine-level có thể khác mental model source trong giới hạn semantics;
- compiled code có thể bị discard/recompile.

Nhưng ứng dụng không nên phụ thuộc vào một chi tiết triển khai tình cờ như “method này chắc chắn inline” hoặc “object này chắc chắn ở stack”.

Rule thực tế:

> viết code theo language/JMM semantics; dùng profiler/benchmark khi cần biết optimization thật của runtime.

</details>

- [Quay lại đầu trang](#back-to-top)
