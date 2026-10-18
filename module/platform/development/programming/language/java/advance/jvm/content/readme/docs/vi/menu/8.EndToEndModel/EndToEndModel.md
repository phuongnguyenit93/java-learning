<a id="back-to-top"></a>

# Tổng hợp Mental Model JVM End-to-End

## Menu
- [Từ JVM startup đến method execution](#startup-to-execution-flow)
- [Từ class file đến runtime state](#classfile-to-runtime-state-flow)
- [Method execution qua frame, operand stack và execution engine](#method-execution-flow)
- [Từ object allocation đến memory reclamation](#allocation-to-reclamation-flow)
- [Từ interpreted execution đến optimized code](#interpreted-to-optimized-flow)
- [Nhìn JVM như một process hoàn chỉnh](#whole-process-memory-view)
- [Phân loại vấn đề theo đúng module owner](#jvm-problem-boundary-map)
- [Checkpoint mental model JVM](#jvm-mental-model-checkpoint)

## <a id="startup-to-execution-flow">Từ JVM startup đến method execution</a>

<details>
<summary>Click for details</summary>

Khi JVM process khởi động, runtime thiết lập VM-wide structures, threads và chọn runtime policies/ergonomic defaults cần thiết. Initial class/entry point của ứng dụng sau đó được load, linked và initialized theo lifecycle rules trước khi method execution tiến triển.

Mental flow:

```text
process startup
→ JVM runtime initialization
→ initial class loading/linking/initialization
→ main/entry method frame
→ bytecode execution
→ adaptive optimization theo thời gian
```

Flow này không có nghĩa JVM eagerly load mọi class ngay startup. Class loading/linking có thể diễn ra động khi execution cần thêm types.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-to-runtime-state-flow">Từ class file đến runtime state</a>

<details>
<summary>Click for details</summary>

Class file bắt đầu là một biểu diễn nhị phân: version, constant pool, fields, methods và attributes.

Khi runtime cần type:

```text
class-file representation
→ load/create runtime class
→ verify + prepare (+ resolve khi cần)
→ runtime constant pool / class metadata
→ initialize static state khi trigger
```

Sau đó instruction execution có thể sử dụng resolved runtime entities thay vì chỉ symbolic entries.

Đây là bridge giữa chapter 2 và chapter 3: dữ liệu “trong file” trở thành runtime structures phục vụ execution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-execution-flow">Method execution qua frame, operand stack và execution engine</a>

<details>
<summary>Click for details</summary>

Khi method được invoke, thread nhận frame mới chứa local variables, operand stack và runtime-constant-pool reference.

```text
invoke
→ push/create frame
→ bytecode consumes locals/operand stack
→ nested invoke tạo frame mới
→ return hoặc exception
→ frame bị hủy
```

Execution engine có thể interpret bytecode hoặc chạy compiled native version của method. Dù implementation path khác nhau, observable Java semantics phải tương thích contract.

Vì vậy stack-frame model là “logical execution state”, còn CPU register/machine stack cụ thể là chi tiết triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="allocation-to-reclamation-flow">Từ object allocation đến memory reclamation</a>

<details>
<summary>Click for details</summary>

Một object bắt đầu từ allocation request và trở thành node trong object graph.

```text
new
→ object storage
→ constructor <init> initialization
→ references giữ object reachable
→ ứng dụng thay đổi reference graph
→ object không còn reachable
→ GC có thể reclaim storage
```

“Có thể reclaim” không có nghĩa reclaim ngay lập tức. Timing phụ thuộc collector và runtime state.

Nếu ứng dụng vô tình giữ reference, object vẫn live đối với GC dù nghiệp vụ đã “không cần nữa”. Đây là nguồn gốc phổ biến của managed-memory leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpreted-to-optimized-flow">Từ interpreted execution đến optimized code</a>

<details>
<summary>Click for details</summary>

Trong HotSpot, method có thể bắt đầu ở interpreter, thu thập profile rồi được JIT compile khi đủ hot.

```text
cold bytecode
→ interpreted
→ profile
→ compiled
→ optimized
→ assumption invalid?
   └─→ deopt / recompile
```

Đây là lifecycle động, không phải một transition chỉ xảy ra đúng một lần.

Hệ quả: benchmark JVM cần warm-up và measurement discipline; latency của startup/cold path có thể khác steady-state.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="whole-process-memory-view">Nhìn JVM như một process hoàn chỉnh</a>

<details>
<summary>Click for details</summary>

Một JVM production process nên được nhìn như tổng của nhiều memory/resource category:

```text
managed heap
+ thread/native stacks
+ class metadata
+ compiled code/code cache
+ direct/off-heap buffers
+ native libraries/runtime internals
= process footprint
```

Heap tuning chỉ tác động một phần tổng footprint. Thread explosion, classloader leak hoặc native buffer growth có thể gây memory pressure độc lập.

Whole-process view giúp tránh anti-pattern “heap graph ổn nên chắc chắn không có memory problem”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-problem-boundary-map">Phân loại vấn đề theo đúng module owner</a>

<details>
<summary>Click for details</summary>

Khi gặp vấn đề runtime, xác định đúng owner trước:

```text
class được tìm/load bởi ai, identity/delegation?
→ ClassLoader

MethodHandle/CallSite/VarHandle/linkage API?
→ Dynamic Runtime

agent/transform/redefine class?
→ Instrumentation

JNI/FFM/native call/memory lifetime?
→ Native Interoperability

thread-safety/happens-before?
→ Concurrency

heap dump/JFR/NMT/bằng chứng root-cause?
→ Runtime Diagnostics
```

JVM module là bản đồ nền: nó giúp biết cơ chế nào đang tham gia để chuyển sang tool/module chuyên sâu đúng lúc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-mental-model-checkpoint">Checkpoint mental model JVM</a>

<details>
<summary>Click for details</summary>

Sau module này, người học nên tự giải thích được các câu sau mà không cần học thuộc flag:

1. JVM thực thi contract gì và class file đóng vai trò nào?
2. Loading/linking/initialization khác execution ra sao?
3. Heap, method area, JVM stack, frame và runtime constant pool liên hệ thế nào?
4. Vì sao `new` không nhất thiết đồng nghĩa một heap allocation vật lý tồn tại đến GC?
5. Vì sao GC không có một algorithm “chuẩn duy nhất”?
6. Interpreter/JIT/profiling/deoptimization tạo adaptive execution thế nào?
7. Vì sao RSS có thể tăng khi heap usage ổn định?
8. VM option nào là HotSpot detail thay vì Java/JVM contract?
9. Khi nào phải dừng suy đoán và chuyển sang diagnostics?

Nếu trả lời được theo flow và boundary thay vì định nghĩa rời rạc, mental model JVM đã đủ vững để học các module Java Advanced phía sau.

</details>

- [Quay lại đầu trang](#back-to-top)
