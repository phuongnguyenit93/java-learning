# 📂 README MODULE STRUCTURE (VI)

* **1.MentalModel**
    * [NativeInteroperability](content/readme/docs/vi/menu/1.MentalModel/knowledge/NativeInteroperability.md)
* **2.NativeLibraryAbi**
    * [NativeLibraryAbi](content/readme/docs/vi/menu/2.NativeLibraryAbi/knowledge/NativeLibraryAbi.md)
* **3.JNI**
    * [JNI](content/readme/docs/vi/menu/3.JNI/knowledge/JNI.md)
* **4.JNIInvocation**
    * [JNIInvocation](content/readme/docs/vi/menu/4.JNIInvocation/knowledge/JNIInvocation.md)
* **5.FFM**
    * [FFM](content/readme/docs/vi/menu/5.FFM/knowledge/FFM.md)
* **6.MemorySegment**
    * [MemorySegment](content/readme/docs/vi/menu/6.MemorySegment/knowledge/MemorySegment.md)
* **7.ArenaLifetime**
    * [ArenaLifetime](content/readme/docs/vi/menu/7.ArenaLifetime/knowledge/ArenaLifetime.md)
* **8.MemoryLayout**
    * [MemoryLayout](content/readme/docs/vi/menu/8.MemoryLayout/knowledge/MemoryLayout.md)
* **9.LinkerSymbolLookup**
    * [LinkerSymbolLookup](content/readme/docs/vi/menu/9.LinkerSymbolLookup/knowledge/LinkerSymbolLookup.md)
* **10.DowncallUpcall**
    * [DowncallUpcall](content/readme/docs/vi/menu/10.DowncallUpcall/knowledge/DowncallUpcall.md)
* **11.SafetyPitfalls**
    * [SafetyPitfalls](content/readme/docs/vi/menu/11.SafetyPitfalls/knowledge/SafetyPitfalls.md)
* **12.DecisionsSynthesis**
    * [DecisionsSynthesis](content/readme/docs/vi/menu/12.DecisionsSynthesis/knowledge/DecisionsSynthesis.md)

# Native Interoperability

Native Interoperability là phần Java Advanced tập trung vào biên giữa Java runtime được quản lý với native code, native memory và thư viện của hệ điều hành. Module này giúp learner hiểu cách Java vượt ra ngoài JVM-managed world bằng JNI và Foreign Function & Memory API (FFM), đồng thời giữ được mental model rõ về ABI, ownership, lifetime, linkage và failure boundary.

## Vì sao nên học module này?

Java thường che giấu chi tiết memory và platform để code portable và an toàn hơn. Nhưng một số bài toán vẫn cần gọi thư viện C/C++, dùng native API của hệ điều hành, truy cập vùng nhớ ngoài heap hoặc tích hợp với hệ thống đã có binary interface. Khi đó developer phải hiểu không chỉ cách gọi API mà cả những guarantee nào của JVM không còn tự động bảo vệ mình.

Module này xây learning path từ native boundary và binary contract, đi qua JNI, rồi tới FFM memory/function model, sau đó kết thúc bằng safety và decision model.

## Prerequisite

Nên nắm trước:

- Java Core: class, method, exception, resource lifecycle và I/O cơ bản;
- JVM ở mức managed runtime, memory và execution mental model;
- Dynamic Runtime ở mức MethodHandle/VarHandle concept khi đi sâu vào FFM linkage;
- concurrency fundamentals khi native state hoặc callback được chia sẻ qua nhiều thread.

## Learning flow

Học theo thứ tự:

1. Native Interoperability là gì và vì sao Java cần vượt qua managed boundary;
2. native library, symbol, ABI và platform compatibility;
3. JNI bridge model và JNI Invocation API;
4. FFM mental model, MemorySegment và Arena;
5. MemoryLayout để mô tả native data;
6. Linker, SymbolLookup, downcall và upcall để gọi foreign function;
7. ownership, safety, failure và native-access boundary;
8. tổng hợp tiêu chí chọn JNI/FFM và thiết kế integration end-to-end.

## Ranh giới module

Native Interoperability sở hữu JNI, FFM, native-library loading ở góc Java/native integration, native/off-heap lifetime, memory layout, symbol/linker model, downcall/upcall và các safety concern tại native boundary.

Các phần sau được handoff:

- JVM native-memory internals và execution internals → JVM;
- MethodHandle/VarHandle mechanics chuyên sâu → Dynamic Runtime;
- lịch sử preview/finalization của FFM theo từng release → Java Version, đặc biệt Java 22 FFM;
- C/C++ language, compiler toolchain và ABI engineering chuyên sâu theo từng platform → ngoài Java curriculum;
- generic OS/process administration → infrastructure/runtime owner tương ứng.

Mục tiêu cuối cùng là có thể lần theo một native integration từ library/ABI tới memory, call/callback, error propagation và cleanup, đồng thời biết khi nào JNI, FFM hoặc thuần Java là lựa chọn phù hợp.
