<a id="back-to-top"></a>

# Runtime Data Areas và Stack Frame

## Menu
- [Runtime data areas tồn tại để làm gì?](#runtime-data-areas-purpose)
- [Vùng dùng chung toàn JVM và vùng riêng theo thread](#vm-wide-vs-thread-private-areas)
- [PC register](#pc-register)
- [JVM stacks](#jvm-stacks)
- [Heap](#heap)
- [Method area](#method-area)
- [Runtime constant pool trong runtime data model](#runtime-constant-pool-area)
- [Native method stacks](#native-method-stacks)
- [Mô hình stack frame](#stack-frame-model)
- [Local variables và operand stack](#local-variables-and-operand-stack)
- [Vòng đời frame, method return và abrupt completion](#frame-lifecycle-and-completion)
- [StackOverflowError, OutOfMemoryError và ranh giới tài nguyên](#runtime-area-failure-boundaries)

## <a id="runtime-data-areas-purpose">Runtime data areas tồn tại để làm gì?</a>

<details>
<summary>Click for details</summary>

Khi chương trình chạy, JVM cần nơi lưu trạng thái thực thi, object, call stack và dữ liệu trung gian. JVM Specification mô tả các **runtime data areas** ở mức hợp đồng logic để mọi implementation có cùng execution semantics, nhưng không bắt buộc một physical layout duy nhất trong bộ nhớ tiến trình.

Hai câu hỏi nền tảng:

```text
dữ liệu này thuộc toàn JVM?
hay thuộc riêng một thread?
```

và:

```text
vùng này thuộc specification-level model?
hay là implementation-specific memory structure?
```

Nếu không tách hai lớp này, người học rất dễ đồng nhất “method area = metaspace” hoặc “JVM stack = một vùng có layout cố định”, trong khi specification không nói như vậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="vm-wide-vs-thread-private-areas">Vùng dùng chung toàn JVM và vùng riêng theo thread</a>

<details>
<summary>Click for details</summary>

Một số runtime areas được tạo khi JVM khởi động và được chia sẻ giữa các thread; một số khác được tạo theo từng thread.

```text
VM-wide/shared
├─ Heap
├─ Method Area
└─ Runtime Constant Pools (gắn với class/interface)

Per-thread
├─ pc Register
├─ JVM Stack
└─ Native Method Stack (nếu implementation sử dụng)
```

Phân loại này giúp suy luận ownership và lifetime. Một local variable nằm trong frame trên JVM stack của thread hiện tại; object mà local variable trỏ tới thường nằm trên heap dùng chung.

Đây không phải Java Memory Model/happens-before. Shared-vs-thread-private ở đây là **runtime storage model**, còn visibility/order giữa thread thuộc Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pc-register">PC register</a>

<details>
<summary>Click for details</summary>

Mỗi JVM thread có một `pc` (program counter) register riêng. Khi thread đang thực thi non-native method, `pc` biểu diễn địa chỉ/instruction position của JVM instruction hiện tại theo model của implementation.

Nó tồn tại vì JVM cần biết thread đang ở đâu trong instruction stream để tiếp tục execution sau branching, method dispatch hoặc scheduling.

Đối với native method, giá trị `pc` không được specification định nghĩa theo cùng cách.

Learner không cần thao tác trực tiếp với `pc`; mục đích của section là hoàn thiện mental model rằng execution state không chỉ gồm stack và heap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-stacks">JVM stacks</a>

<details>
<summary>Click for details</summary>

Mỗi JVM thread có một JVM stack riêng. Stack này chủ yếu chứa **frames** tương ứng với active method invocations của thread.

```text
Thread A JVM Stack
┌─────────────────┐
│ methodC frame   │ ← current
├─────────────────┤
│ methodB frame   │
├─────────────────┤
│ methodA frame   │
└─────────────────┘
```

Khi method được gọi, frame mới được tạo; khi invocation hoàn tất, frame bị hủy. Vì stack là thread-private, local variable trong frame không tự động trở thành shared state chỉ vì object referent nằm trên heap.

Stack size có thể fixed hoặc dynamically expanded tùy implementation. Specification tập trung vào semantics và hành vi khi thất bại, không áp một physical layout duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap">Heap</a>

<details>
<summary>Click for details</summary>

Heap là runtime data area dùng chung, nơi JVM cấp phát object và array. Heap được tạo khi JVM khởi động và storage cho object được quản lý bởi automatic storage-management system — thường gọi là garbage collector.

Điểm rất quan trọng:

> JVM Specification không quy định object phải được đặt liên tục, heap phải chia generation, hay collector phải dùng algorithm cụ thể.

Heap có thể fixed/dynamic theo implementation; failure vì không còn đủ storage có thể dẫn tới `OutOfMemoryError`.

Chapter tiếp theo sẽ đi sâu vào allocation, reachability và GC. Ở đây chỉ cần đặt heap đúng vị trí trong runtime-area model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-area">Method area</a>

<details>
<summary>Click for details</summary>

Method area là runtime data area dùng chung, lưu các cấu trúc liên quan tới class/interface như runtime constant pool, field/method data và biểu diễn code cần cho runtime.

Specification mô tả **logical responsibility**, không bắt implementation phải có một physical region tên “method area”.

Trong HotSpot hiện đại, người học thường gặp **Metaspace** khi nói về class metadata. Không nên viết:

```text
method area = metaspace
```

như một identity tuyệt đối. Chính xác hơn:

```text
method area
→ specification-level abstraction

Metaspace
→ một phần HotSpot implementation strategy cho class metadata
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-constant-pool-area">Runtime constant pool trong runtime data model</a>

<details>
<summary>Click for details</summary>

Mỗi class/interface có một runtime constant pool được xây từ constant pool trong biểu diễn class file. Nó thuộc method-area-level runtime state theo JVMS.

Runtime constant pool chứa static constants và symbolic references phục vụ resolution/execution. Vì vậy nó nối hai chapter:

```text
class file constant_pool
        ↓ class creation
runtime constant pool
        ↓ resolution/execution
hành vi runtime
```

Điểm này giải thích vì sao constant pool vừa xuất hiện trong chapter class file vừa xuất hiện trong chapter runtime data: cùng concept nhưng ở **hai biểu diễn khác nhau theo lifecycle**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-method-stacks">Native method stacks</a>

<details>
<summary>Click for details</summary>

JVM implementation có thể sử dụng native method stack để hỗ trợ method được hiện thực bằng ngôn ngữ native hoặc runtime native mechanism.

Specification cho phép implementation tự quyết cách tổ chức vùng này; thậm chí một implementation không hỗ trợ native method theo cách đó có thể không cần native method stack riêng.

Trong module JVM, mục đích là hiểu rằng việc thực thi ứng dụng Java có thể cần stack/resource ngoài JVM frame model. Việc gọi native function qua JNI hoặc FFM thuộc **Native Interoperability**, không mở rộng ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frame-model">Mô hình stack frame</a>

<details>
<summary>Click for details</summary>

Frame là execution context cho **một method invocation**. JVMS mô tả frame chứa ba nhóm chính:

```text
Frame
├─ local variables
├─ operand stack
└─ reference tới runtime constant pool của current class
```

Frame còn phục vụ dynamic linking, method return và exception dispatch.

Size cần cho local-variable array và operand stack được biết từ class-file method metadata, cho phép runtime chuẩn bị frame phù hợp khi method được invoke.

Mental model này quan trọng vì bytecode instruction phần lớn không thao tác trực tiếp với “biến Java source”; chúng thao tác với local slots và operand stack trong frame.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-variables-and-operand-stack">Local variables và operand stack</a>

<details>
<summary>Click for details</summary>

Local-variable array là indexed slots trong frame dùng cho parameters và local state của method. Operand stack là working stack để instruction lấy operand và đặt result.

Ví dụ:

```text
iload_1   local[1] → operand stack
iload_2   local[2] → operand stack
iadd      pop 2 int, push result
ireturn   pop result và return
```

Một value Java source có thể không map đơn giản 1:1 tới một “biến vật lý” sau optimization, nhưng ở class-file/JVMS model, local variables + operand stack là nền tảng để hiểu bytecode execution.

Đây cũng là lý do đọc `javap -c` có ý nghĩa: ta có thể theo flow value qua instruction mà không cần biết machine register thật của CPU.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="frame-lifecycle-and-completion">Vòng đời frame, method return và abrupt completion</a>

<details>
<summary>Click for details</summary>

Frame được tạo mỗi khi method được invoke và bị hủy khi invocation hoàn tất.

Hai kiểu completion:

- **normal completion**: method return bình thường, có thể mang return value cho caller;
- **abrupt completion**: method throw exception không được handle trong chính invocation đó.

Khi exception propagates, current frame có thể bị discard và JVM tiếp tục tìm handler ở caller frames theo exception-dispatch rules.

```text
caller frame
   ↓ invokes
callee frame
   ├─ normal return → result về caller
   └─ uncaught exception → callee frame bỏ, exception propagate
```

Frame lifecycle vì vậy gắn chặt với call stack và exception propagation, không chỉ với “method bắt đầu/kết thúc”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-area-failure-boundaries">StackOverflowError, OutOfMemoryError và ranh giới tài nguyên</a>

<details>
<summary>Click for details</summary>

Runtime area có giới hạn tài nguyên, nên JVM specification định nghĩa các ranh giới lỗi quan trọng.

**StackOverflowError** thường gắn với việc thread cần thêm stack/frame nhưng vượt giới hạn stack có thể cấp.

```java
static void recurse() {
    recurse();
}
```

điển hình sẽ tăng depth cho tới khi stack không còn đáp ứng được.

**OutOfMemoryError** rộng hơn “Java heap full”. Nó có thể xuất hiện khi JVM không thể cấp đủ memory cho heap hoặc một số runtime structures/resources cần thiết.

Rule thực tế:

```text
error name
≠ đủ để xác định nguyên nhân production
```

Phân loại cơ chế thuộc JVM; thu thập heap dump/NMT/JFR và root-cause troubleshooting thuộc Runtime Diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)
