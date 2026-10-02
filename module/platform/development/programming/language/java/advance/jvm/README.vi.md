# 📂 README MODULE STRUCTURE (VI)

* **1.JvmPurposeContract**
    * [JvmPurposeContract](readme/vi/menu/1.JvmPurposeContract/JvmPurposeContract.md)
* **2.ClassFileBytecode**
    * [ClassFileBytecode](readme/vi/menu/2.ClassFileBytecode/ClassFileBytecode.md)
* **3.RuntimeDataAreasFrames**
    * [RuntimeDataAreasFrames](readme/vi/menu/3.RuntimeDataAreasFrames/RuntimeDataAreasFrames.md)
* **4.ManagedHeapGC**
    * [ManagedHeapGC](readme/vi/menu/4.ManagedHeapGC/ManagedHeapGC.md)
* **5.AdaptiveExecutionJIT**
    * [AdaptiveExecutionJIT](readme/vi/menu/5.AdaptiveExecutionJIT/AdaptiveExecutionJIT.md)
* **6.ProcessMemory**
    * [ProcessMemory](readme/vi/menu/6.ProcessMemory/ProcessMemory.md)
* **7.HotSpotErgonomics**
    * [HotSpotErgonomics](readme/vi/menu/7.HotSpotErgonomics/HotSpotErgonomics.md)
* **8.EndToEndModel**
    * [EndToEndModel](readme/vi/menu/8.EndToEndModel/EndToEndModel.md)

# Java Virtual Machine (JVM)

Module JVM xây dựng mental model nền tảng cho Java Advanced: JVM nhận chương trình ở dạng class file như thế nào, tạo trạng thái runtime ra sao, thực thi method bằng frame/operand stack thế nào, quản lý heap và garbage collection ra sao, tối ưu hot code bằng interpreter/JIT như thế nào, và vì sao memory của cả JVM process lớn hơn Java heap.

Điểm xuyên suốt của module là luôn tách **hợp đồng trong JVM Specification** khỏi **chi tiết của một JVM implementation như HotSpot**. Nhờ đó learner có thể reasoning về runtime behavior mà không biến một GC algorithm, memory layout, JIT strategy hay VM flag cụ thể thành quy luật chung của Java.

## Vì sao nên học module này?

Java source code che giấu phần lớn execution machinery phía dưới. Khi cần hiểu vì sao một class chưa thể chạy, một method tiêu thụ stack thế nào, object được reclaim ra sao, vì sao code nóng chạy khác code mới khởi động, hoặc vì sao process memory tăng khi heap chưa đầy, mental model chỉ ở tầng Java language là chưa đủ.

Module này cung cấp lớp nền để các module Dynamic Runtime, Instrumentation, Native Interoperability và Runtime Diagnostics có thể đi sâu hơn mà không phải dạy lại execution model của JVM.

## Prerequisite

Learner nên nắm trước:

- Java Core: class, object, method, exception và type system cơ bản;
- ClassLoader ở mức biết class cần được load trước khi JVM có thể sử dụng nó;
- concurrency fundamentals để phân biệt thread-local runtime state với state dùng chung.

Java Memory Model/happens-before chuyên sâu vẫn thuộc Java Concurrency; ClassLoader API/lifecycle chuyên sâu vẫn thuộc Java Core ClassLoader.

## Learning flow

Học theo thứ tự:

1. hiểu JVM tồn tại để làm gì và class file đóng vai trò execution contract như thế nào;
2. theo dõi class file, bytecode, constant pool và ranh giới loading/linking/initialization trước execution;
3. xây mô hình runtime data areas, JVM stack và stack frame;
4. hiểu managed heap, object allocation, reachability và garbage collection;
5. hiểu interpreter, profiling, JIT và adaptive optimization;
6. mở rộng từ Java heap sang memory/resource của toàn JVM process;
7. đặt HotSpot ergonomics và VM options vào đúng ranh giới specification-vs-implementation;
8. tổng hợp toàn bộ thành một mental model end-to-end từ class file đến runtime behavior.

## Ranh giới module

JVM sở hữu execution/runtime mental model, class-file/bytecode runtime view, runtime data areas, stack frame, object allocation/GC concepts, interpreter/JIT/optimization model, native-memory awareness và VM ergonomics ở mức giải thích behavior.

Các phần sau được handoff:

- ClassLoader API, delegation và class-loading lifecycle chuyên sâu → Java Core ClassLoader;
- Java Memory Model, happens-before và concurrency correctness → Java Concurrency;
- `MethodHandle`, `CallSite`, `VarHandle` và runtime linkage programming model → Dynamic Runtime;
- Java agents và class transformation → Instrumentation;
- JNI, Foreign Function & Memory API và native-call programming → Native Interoperability;
- heap dump, GC log, JFR, NMT và troubleshooting workflow → Runtime Diagnostics.

Mục tiêu cuối cùng là learner có thể nhìn một Java program như một JVM process hoàn chỉnh: hiểu code đi từ class file tới execution state, object đi từ allocation tới reclamation, hot code đi từ interpretation tới optimized machine code, và biết khi nào vấn đề đã vượt khỏi scope JVM để chuyển sang module owner phù hợp.
