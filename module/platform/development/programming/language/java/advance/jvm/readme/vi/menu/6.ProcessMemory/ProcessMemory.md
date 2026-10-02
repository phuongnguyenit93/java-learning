<a id="back-to-top"></a>

# Bộ nhớ Tiến trình JVM Ngoài Java Heap

## Menu
- [Vì sao Java heap không phải toàn bộ memory của JVM process?](#process-memory-purpose)
- [Runtime data areas và process-level memory](#runtime-areas-vs-process-memory)
- [Thread stack và native stack memory](#thread-and-native-stack-memory)
- [Class metadata và memory ngoài heap](#class-metadata-memory)
- [Memory dành cho compiled code](#compiled-code-memory)
- [Direct/off-heap memory ở mức nhận thức JVM](#direct-and-offheap-memory-awareness)
- [Vì sao process memory có thể tăng khi heap chưa đầy?](#process-memory-growth)
- [Cạn kiệt memory/resource ngoài heap](#nonheap-resource-exhaustion)
- [Native memory awareness và Native Interoperability khác nhau thế nào?](#native-memory-vs-native-interoperability)

## <a id="process-memory-purpose">Vì sao Java heap không phải toàn bộ memory của JVM process?</a>

<details>
<summary>Click for details</summary>

Một JVM process cần nhiều memory hơn nơi chứa Java objects. Ngoài heap, runtime còn cần thread stacks, class metadata, compiled code, internal VM structures, native libraries, direct buffers và memory của subsystem khác.

Vì vậy:

```text
process RSS / committed memory
≠ Java heap usage
```

Nếu dashboard chỉ nhìn `-Xmx` hoặc heap-used metric, người học có thể bỏ sót nguyên nhân tiến trình bị container/OS kill dù heap còn dư.

Chapter này xây **mô hình bộ nhớ của toàn tiến trình**. Công cụ NMT/JFR/OS metrics để đo cụ thể thuộc Runtime Diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-areas-vs-process-memory">Runtime data areas và process-level memory</a>

<details>
<summary>Click for details</summary>

JVMS runtime data areas là abstraction mô tả execution semantics. Process-level memory là cách implementation thực sự lấy virtual/native memory từ operating system để hiện thực các abstraction và subsystem của nó.

Không có mapping 1:1 bắt buộc:

```text
spec concept       implementation examples
-----------        -----------------------
heap               heap reservation/commit
method area        class metadata structures
JVM stacks         native thread stack mappings
execution engine   code cache/compiler data
```

Một implementation có thể thay đổi cấu trúc giữa các version mà vẫn conform specification.

Do đó khi đọc OS memory, hãy tránh cố ép mọi byte vào một JVMS area duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-and-native-stack-memory">Thread stack và native stack memory</a>

<details>
<summary>Click for details</summary>

Mỗi platform thread cần stack memory ở cấp tiến trình. Nhiều thread vì vậy có thể tạo mức chiếm dụng bộ nhớ đáng kể ngay cả khi mỗi thread gần như không allocate object trên heap.

Conceptual:

```text
N platform threads
× per-thread stack reservation
→ process virtual/native memory pressure
```

`-Xss` là HotSpot control liên quan thread stack sizing, không phải universal Java-language setting.

Virtual threads có runtime architecture khác về scheduling/stack representation, nhưng concurrency model chi tiết thuộc module Concurrency/Virtual Threads. Ở đây chỉ cần hiểu “số lượng thread có thể ảnh hưởng bộ nhớ tiến trình”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-metadata-memory">Class metadata và memory ngoài heap</a>

<details>
<summary>Click for details</summary>

Runtime phải giữ metadata cho loaded classes: type information, method/field structures và dữ liệu liên quan tới class lifecycle.

HotSpot hiện đại thường dùng **Metaspace** cho phần lớn class metadata và lấy memory từ native memory, không phải Java heap.

Hệ quả practical:

- load rất nhiều distinct classes/class loaders có thể tăng native memory;
- unloading phụ thuộc class-loader reachability/lifecycle;
- heap bình thường không loại trừ class-metadata pressure.

Chi tiết ClassLoader leak thuộc ClassLoader/Diagnostics. Module JVM chỉ giúp người học biết class metadata là một nguồn tiêu thụ bộ nhớ ngoài heap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compiled-code-memory">Memory dành cho compiled code</a>

<details>
<summary>Click for details</summary>

JIT-generated machine code phải nằm trong executable memory của JVM process. HotSpot quản lý vùng này bằng code-cache-related structures.

Compiled-code memory tăng theo amount/profile của code được compile, compiler policy và runtime phase. Nó không được tính là Java object heap.

```text
warm-up
→ more compilation
→ more compiled native code
→ bộ nhớ tiến trình có thể tăng
```

Điều này giúp giải thích tại sao service sau vài phút có RSS lớn hơn lúc startup dù live heap gần tương đương.

Đo/diagnose code cache cụ thể thuộc diagnostics tooling.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-and-offheap-memory-awareness">Direct/off-heap memory ở mức nhận thức JVM</a>

<details>
<summary>Click for details</summary>

`ByteBuffer.allocateDirect` tạo một **direct byte buffer**. API contract của Java không định nghĩa đơn giản “direct = off-heap”; điều nó đảm bảo là JVM sẽ cố gắng thực hiện native I/O trực tiếp trên buffer để tránh copy qua buffer trung gian khi có thể. Trong HotSpot, nội dung của direct buffer thường được backing bằng native memory, vì vậy mức chiếm dụng này có thể không xuất hiện như ordinary Java-heap usage.

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
```

“Off-heap” là thuật ngữ rộng; không phải mọi native memory đều là direct buffer và không phải mọi cơ chế ngoài heap có cùng lifecycle.

Quy tắc:

```text
heap monitoring
→ không đủ để quan sát toàn bộ bộ nhớ của ứng dụng
```

FFM/JNI native-memory programming chuyên sâu thuộc Native Interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="process-memory-growth">Vì sao process memory có thể tăng khi heap chưa đầy?</a>

<details>
<summary>Click for details</summary>

Process memory có thể tăng vì nhiều nguồn độc lập với live Java heap:

- thêm platform threads;
- thêm loaded classes/class loaders;
- JIT compilation/code cache;
- direct buffers;
- JNI/native libraries;
- runtime internal structures;
- allocator fragmentation hoặc committed-but-not-currently-used memory.

Vì vậy biểu đồ:

```text
heap used: stable
RSS: rising
```

không mâu thuẫn.

Đây là lúc mô hình tư duy phải chuyển từ “heap leak?” sang “nhóm bộ nhớ nào đang tăng?”. Việc trả lời bằng bằng chứng NMT/OS thuộc Runtime Diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nonheap-resource-exhaustion">Cạn kiệt memory/resource ngoài heap</a>

<details>
<summary>Click for details</summary>

Application có thể fail vì process/native resource dù Java heap chưa chạm `-Xmx`.

Ví dụ class metadata pressure, không tạo thêm native thread được, direct-buffer/native allocation fail hoặc container memory limit bị vượt.

Failure cuối cùng có thể vẫn xuất hiện dưới dạng `OutOfMemoryError` với message khác nhau hoặc thậm chí process bị OS/container terminate.

Do đó:

```text
OutOfMemoryError
→ category, không phải root cause duy nhất
```

Root cause cần bằng chứng từ error message, giới hạn của tiến trình và diagnostic tools.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-memory-vs-native-interoperability">Native memory awareness và Native Interoperability khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

Module JVM học **native-memory awareness** để hiểu tổng mức chiếm dụng bộ nhớ của tiến trình và các resource của implementation.

Native Interoperability học cách Java code **chủ động tương tác với native world**:

- JNI;
- Foreign Function & Memory API;
- native library loading;
- symbol lookup/linker;
- explicit native-memory lifetime.

```text
JVM Process Memory
→ “runtime đang dùng memory ở đâu?”

Native Interoperability
→ “Java code gọi/đọc/ghi native resource như thế nào?”
```

Hai chủ đề gặp nhau ở memory safety/lifetime nhưng có learning objective khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
