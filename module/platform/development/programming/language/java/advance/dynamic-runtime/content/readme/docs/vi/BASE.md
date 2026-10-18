# Dynamic Runtime

Dynamic Runtime là phần Java Advanced tập trung vào các primitive mức thấp của `java.lang.invoke` dùng cho lời gọi có kiểu, truy cập biến có ngữ nghĩa và runtime linkage. Module này giúp nối khoảng cách giữa Java code thông thường với các cơ chế mà JVM, language runtime, framework và infrastructure code dùng khi cần biểu diễn hoặc liên kết hành vi linh hoạt hơn lời gọi trực tiếp.

## Vì sao nên học module này?

Reflection có thể khám phá metadata và gọi member động, nhưng không phải là mô hình duy nhất cho runtime behavior. Java còn cung cấp `MethodType` + `MethodHandle` cho lời gọi có kiểu, `MethodHandles.Lookup` cho capability-based lookup/access, `CallSite` + `invokedynamic` cho runtime linkage và `VarHandle` cho truy cập biến strongly typed.

Module này xây mental model để hiểu các primitive đó phối hợp với nhau như thế nào, khi nào chúng phù hợp và khi nào Java code trực tiếp hoặc Reflection đơn giản hơn.

## Prerequisite

Nên nắm trước:

- Java Core: class, method, field, exception và type system cơ bản;
- Reflection ở mức metadata/member access;
- JVM ở mức bytecode execution và linkage mental model;
- Concurrency fundamentals khi đi sâu vào visibility và memory-ordering semantics của `VarHandle` hoặc mutable call site.

## Learning flow

Học theo thứ tự:

1. mental model và ranh giới của Dynamic Runtime;
2. `MethodType` + `MethodHandle` và signature polymorphism;
3. `MethodHandles.Lookup` và access capability;
4. adaptation/composition của MethodHandle;
5. `CallSite` và target có thể thay đổi;
6. `invokedynamic` + bootstrap linkage;
7. `VarHandle` như nhánh variable-access song song;
8. tổng hợp decision model, failure model và module handoff.

## Ranh giới module

Dynamic Runtime sở hữu typed invocation, typed variable handles, lookup/access model, MethodHandle adaptation/composition, CallSite và invokedynamic mental model.

Các phần sau được handoff:

- Reflection metadata/member inspection → Java Core Reflection;
- constant pool, bytecode execution và JVM linkage foundation → JVM;
- Java Memory Model/happens-before chuyên sâu → Java Concurrency;
- lịch sử Java 7 của Method Handles/invokedynamic → Java Version;
- native memory và foreign-function boundary → Native Interoperability.

Mục tiêu cuối cùng là có thể lần theo một runtime invocation/linkage flow end-to-end và chọn đúng primitive mà không biến `java.lang.invoke` thành lựa chọn mặc định cho code ứng dụng thông thường.
