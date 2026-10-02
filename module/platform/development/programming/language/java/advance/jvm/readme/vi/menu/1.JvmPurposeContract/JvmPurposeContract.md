<a id="back-to-top"></a>

# JVM - Mục đích và Hợp đồng Thực thi

## Menu
- [JVM là gì và vì sao tồn tại?](#jvm-purpose)
- [Class file là hợp đồng thực thi của JVM](#classfile-execution-contract)
- [JVM và tính độc lập với ngôn ngữ nguồn](#jvm-language-independence)
- [Mô hình portability qua lớp máy ảo](#jvm-portability-model)
- [JVM Specification và JVM Implementation](#jvm-specification-vs-implementation)
- [Ranh giới trách nhiệm của JVM runtime](#jvm-runtime-boundary)

## <a id="jvm-purpose">JVM là gì và vì sao tồn tại?</a>

<details>
<summary>Click for details</summary>

JVM (Java Virtual Machine) là máy trừu tượng thực thi chương trình ở dạng **class file**, thay vì chạy trực tiếp mã nguồn Java trên CPU. Nhờ lớp trung gian này, compiler có thể tạo ra một biểu diễn chung, còn cách triển khai JVM chịu trách nhiệm ánh xạ biểu diễn đó xuống hệ điều hành, kiến trúc CPU, tổ chức bộ nhớ nội bộ và các cơ chế tối ưu cụ thể.

Vấn đề mà JVM giải quyết không chỉ là “chạy Java ở nhiều hệ điều hành”. Nếu không có một hợp đồng thực thi chung, mỗi ngôn ngữ/compiler sẽ phải gắn chặt hơn với từng nền tảng máy, tự quản lý nhiều chi tiết runtime và khó chia sẻ cùng một hệ sinh thái thư viện/tooling. JVM tạo ra một ranh giới ổn định giữa **biểu diễn chương trình** và **cách máy thật thực thi**.

Một cách đơn giản hơn là compile thẳng source thành native machine code cho từng target. Cách đó vẫn chạy được chương trình, nhưng gánh nặng portability/distribution chuyển sang compiler và bộ artifact theo từng kiến trúc/OS; cũng không có một **JVM contract chung** bắt buộc mọi target cùng chia sẻ. Một native runtime khác vẫn có thể cung cấp GC hoặc tooling riêng, nhưng đó là runtime contract khác. JVM thêm lớp trung gian để đổi sự phụ thuộc “ứng dụng ↔ máy cụ thể” thành “class file ↔ JVM contract”.

Trong module này, hãy luôn giữ mô hình tư duy:

```text
source language
    ↓ compiler
class-file representation
    ↓ JVM implementation
runtime state + native machine execution
```

JVM vì vậy vừa là một abstraction được đặc tả, vừa có nhiều cách triển khai cụ thể. HotSpot là một cách triển khai phổ biến, nhưng không phải bản thân “định nghĩa JVM”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-execution-contract">Class file là hợp đồng thực thi của JVM</a>

<details>
<summary>Click for details</summary>

JVM Specification định nghĩa **class file format** như một hợp đồng nhị phân mà JVM phải hiểu. Một class file chứa thông tin như version, constant pool, access flags, field, method và attribute. Method có code thực thi sẽ mang `Code` attribute chứa bytecode và metadata cần thiết cho việc thực thi.

Điểm quan trọng là JVM không cần biết mã nguồn ban đầu có phải Java hay không. Miễn toolchain sinh ra biểu diễn class file hợp lệ, JVM có thể xử lý nó theo cùng hợp đồng.

Class file cũng không mô tả trực tiếp “object nằm ở offset nào trong RAM” hoặc “GC dùng thuật toán nào”. Nó mô tả **cấu trúc logic của chương trình và symbolic references**. Cách triển khai runtime mới quyết định biểu diễn vật lý và tối ưu.

Vì vậy, khi debug một vấn đề JVM, hãy hỏi trước:

```text
đây là hợp đồng của class file/JVMS?
hay là hành vi riêng của HotSpot?
```

Phân biệt này giúp tránh biến một chi tiết triển khai thành quy luật chung của Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-language-independence">JVM và tính độc lập với ngôn ngữ nguồn</a>

<details>
<summary>Click for details</summary>

JVM không phải “máy chỉ hiểu mã nguồn Java”. Nó hiểu các cấu trúc được biểu diễn trong class-file format và instruction set của JVM. Java compiler là một nguồn tạo class file; các ngôn ngữ JVM khác cũng có thể compile xuống cùng target.

Điều này giải thích vì sao nhiều ngôn ngữ có thể:

- dùng chung Java class library;
- gọi code của nhau khi quy ước kiểu và interop ở JVM level tương thích;
- dùng cùng GC, JIT, profiler và diagnostic tooling;
- chạy trong cùng tiến trình/runtime.

Tuy nhiên, “cùng JVM” không có nghĩa mọi tính năng ngôn ngữ đều map một-một sang tính năng Java. Một compiler có thể biểu diễn semantics riêng bằng synthetic methods, `invokedynamic`, metadata hoặc runtime library của chính nó.

Trong module JVM, ta học **nền thực thi chung**, không học semantics của từng ngôn ngữ JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-portability-model">Mô hình portability qua lớp máy ảo</a>

<details>
<summary>Click for details</summary>

Tính portable của Java dựa trên ý tưởng: ứng dụng không phát hành trực tiếp machine code cho từng CPU/OS, mà phát hành biểu diễn class file theo một hợp đồng chung; JVM trên từng nền tảng chịu trách nhiệm thực thi hợp đồng đó.

```text
same class file
   ├─→ JVM on Windows/x64
   ├─→ JVM on Linux/x64
   └─→ JVM on Linux/ARM64
```

Mô hình này giảm coupling giữa artifact của ứng dụng và machine instruction set. Tuy vậy, tính portable không tuyệt đối. Code vẫn có thể phụ thuộc filesystem semantics, native library, default charset, đặc điểm hiệu năng theo kiến trúc, OS scheduling hoặc VM option riêng của implementation.

Do đó “write once, run anywhere” nên hiểu là **hợp đồng thực thi có tính portable**, không phải lời bảo đảm rằng mọi hành vi của môi trường đều giống hệt nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-specification-vs-implementation">JVM Specification và JVM Implementation</a>

<details>
<summary>Click for details</summary>

JVM Specification mô tả những gì một JVM phải đảm bảo ở mức hợp đồng: class-file format, runtime data areas theo mô hình logic, instruction semantics, quy tắc loading/linking/initialization, frames, exceptions và nhiều quy tắc khác.

Một cách triển khai JVM như HotSpot phải hiện thực hợp đồng đó, nhưng có quyền lựa chọn nhiều chi tiết:

- object layout thực tế;
- garbage collector;
- JIT strategy;
- internal memory layout;
- code cache organization;
- thresholds và heuristics;
- VM flags riêng của implementation.

Ví dụ, specification nói JVM có heap dùng cho object/array và heap được quản lý tự động; specification **không bắt buộc** heap phải chia young/old generation hay phải dùng G1.

Tương tự, JVM không “vốn là interpreted”. Specification cho phép implementation interpret bytecode, compile bytecode thành machine code hoặc kết hợp nhiều chiến lược miễn semantics được giữ.

Quy tắc học quan trọng:

> Cái gì được đảm bảo bởi specification thì có tính portable cao hơn; cái gì đến từ tài liệu HotSpot phải được xem là hành vi của một implementation cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-boundary">Ranh giới trách nhiệm của JVM runtime</a>

<details>
<summary>Click for details</summary>

Module JVM sở hữu mô hình tư duy về cách class-file code trở thành runtime state và được thực thi: runtime data areas, frame, managed heap, các khái niệm GC, interpreter/JIT, nhận thức về native memory và ergonomics.

Nhưng một số chủ đề chỉ chạm vào JVM rồi chuyển sang module chịu trách nhiệm khác:

- **ClassLoader**: JVM cần loading/linking, nhưng API/delegation/class-loader identity chuyên sâu thuộc Java Core ClassLoader.
- **Concurrency**: JVM có thread-private/shared runtime areas, nhưng happens-before và Java Memory Model thuộc Concurrency.
- **Dynamic Runtime**: constant pool/linkage là nền tảng, còn MethodHandle/CallSite/VarHandle programming model thuộc Dynamic Runtime.
- **Instrumentation**: JVM có biểu diễn class ở runtime, nhưng Java Agent/ClassFileTransformer thuộc Instrumentation.
- **Native Interoperability**: JVM dùng native resources, còn JNI/FFM programming thuộc Native Interoperability.
- **Runtime Diagnostics**: JVM tạo ra hành vi runtime, còn thu thập heap dump, JFR, NMT hay quy trình troubleshooting thuộc Runtime Diagnostics.

Giữ ranh giới này giúp module giải thích **cơ chế**, còn các module phía sau giải thích cách điều khiển, mở rộng hoặc chẩn đoán cơ chế đó.

Toàn bộ module đi theo một chuỗi học thống nhất:

```text
JVM + class-file contract
→ class file / bytecode / pre-execution lifecycle
→ runtime data areas + frames
→ managed heap + GC
→ interpreter + JIT + adaptive optimization
→ whole-process memory
→ HotSpot ergonomics / VM controls
→ end-to-end JVM mental model
```

Chuỗi này là bản đồ cho các chapter tiếp theo: mỗi chapter mở rộng một phần của cùng execution model, thay vì là một tập thuật ngữ độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)
