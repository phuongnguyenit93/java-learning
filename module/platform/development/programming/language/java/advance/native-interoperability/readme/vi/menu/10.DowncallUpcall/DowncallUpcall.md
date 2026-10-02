<a id="back-to-top"></a>

# Downcall, upcall và callback boundary

## Menu
- [Downcall: Java gọi foreign function](#downcall-model)
- [Tạo và sử dụng downcall MethodHandle](#downcall-handle)
- [Argument, return value và carrier mapping](#native-argument-return)
- [Upcall: native code gọi lại Java](#upcall-model)
- [Upcall stub và executable native address](#upcall-stub)
- [Callback lifetime và resource ownership](#callback-lifetime)
- [Thread boundary khi native code callback vào Java](#callback-thread-boundary)

## <a id="downcall-model">Downcall: Java gọi foreign function</a>

<details>
<summary>Click for details</summary>

**Downcall** là hướng Java gọi một foreign function. Đây là luồng phổ biến nhất khi ứng dụng Java muốn dùng C API có sẵn.

FFM cần biết:

1. địa chỉ function;
2. FunctionDescriptor của function;
3. Linker cho ABI hiện tại.

~~~text
Java
  ↓ invoke MethodHandle
Linker
  ↓ ABI adaptation
foreign function
~~~

Downcall không tự động hiểu C header. Nếu descriptor hoặc symbol sai, Linker vẫn đang làm việc trên contract do Java cung cấp. Vì vậy tính đúng đắn của binding vẫn là trách nhiệm của code tích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="downcall-handle">Tạo và sử dụng downcall MethodHandle</a>

<details>
<summary>Click for details</summary>

Sau khi có symbol và descriptor, Linker tạo MethodHandle đại diện cho downcall:

~~~java
Linker linker = Linker.nativeLinker();
MemorySegment addAddress = lookup.find("add").orElseThrow();

FunctionDescriptor descriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT
);

MethodHandle add = linker.downcallHandle(
        addAddress,
        descriptor);

int result = (int) add.invokeExact(10, 20);
~~~

MethodHandle ở đây là typed invocation boundary do FFM tạo ra. Nếu Java carrier type không khớp method type, invocation không hợp lệ.

Linker cũng có dạng **unbound downcall** chỉ nhận `FunctionDescriptor` khi tạo handle. Với dạng này, MethodHandle kết quả nhận địa chỉ target dưới dạng `MemorySegment` ở argument đầu tiên, phù hợp khi function pointer thay đổi theo từng lần invoke.

Không cần đi sâu vào MethodHandle adaptation/combinator trong chương này; phần đó thuộc Dynamic Runtime. Mục tiêu ở đây là hiểu handle là cầu nối từ Java call site sang foreign function.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-argument-return">Argument, return value và carrier mapping</a>

<details>
<summary>Click for details</summary>

FunctionDescriptor dùng MemoryLayout, còn MethodHandle dùng Java **carrier type** tương ứng. Ví dụ ValueLayout.JAVA_INT có carrier là int; AddressLayout dùng MemorySegment để đại diện native address.

Khi thiết kế binding, cần phân biệt:

~~~text
native representation
    ↓ MemoryLayout
FFM carrier
    ↓
Java value truyền vào MethodHandle
~~~

Pointer thường đi qua MemorySegment, không nên tự động map sang long chỉ vì địa chỉ máy có dạng số. Trong Java 21, nếu return layout của downcall là `AddressLayout` không có target layout, `MemorySegment` trả về có size `0` và một scope mới luôn ở trạng thái alive. Nếu return `AddressLayout` có target layout `T`, size của segment trả về là `T.byteSize()`. Các bounds/scope phía Java này vẫn **không chứng minh** vùng nhớ native thật sự còn sống, do Java sở hữu hay an toàn trong khoảng đó. Vì vậy binding vẫn cần contract thật về size/lifetime; khi cần gắn bounds hoặc lifetime khác, có thể phải dùng restricted `reinterpret(...)`. Struct-by-value hoặc return value dạng group layout có thể cần allocator để Linker cấp vùng nhớ chứa kết quả.

Carrier mapping đúng không bảo đảm native semantic đúng. Ví dụ int có đúng carrier nhưng C function thực tế mong enum/range khác; kiểm tra nghiệp vụ vẫn thuộc ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upcall-model">Upcall: native code gọi lại Java</a>

<details>
<summary>Click for details</summary>

**Upcall** là chiều ngược: native code gọi lại Java qua một function pointer. FFM tạo một upcall stub từ Java MethodHandle để native library có thể nhận pointer đó như callback.

Use case điển hình:

- comparator truyền vào qsort;
- callback báo tiến độ;
- native library phát event về Java;
- library yêu cầu user-provided handler.

Mental model:

~~~text
Java method
   ↓ MethodHandle
upcall stub
   ↓ function pointer
native library
   ↓ callback
Java method được thực thi
~~~

Upcall làm callback tiện hơn việc viết JNI glue riêng, nhưng callback lifetime và thread context trở thành phần quan trọng của contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upcall-stub">Upcall stub và executable native address</a>

<details>
<summary>Click for details</summary>

Linker.upcallStub tạo một executable native address trỏ tới Java MethodHandle:

~~~java
MethodHandle comparator = lookupComparatorHandle();

try (Arena arena = Arena.ofConfined()) {
    MemorySegment stub = linker.upcallStub(
            comparator,
            comparatorDescriptor,
            arena);

    // truyền stub cho foreign function như function pointer
}
~~~

MemorySegment trả về đại diện cho địa chỉ của stub. Lifetime của stub gắn với Arena được truyền vào; đóng arena sẽ làm stub không còn hợp lệ.

Trong Java 21, upcallStub là restricted operation. Ngoài ra target MethodHandle không được để exception thoát ra qua native boundary; contract của Linker quy định rằng nếu target MethodHandle ném exception trong upcall thì JVM **sẽ terminate đột ngột**. Callback nên catch và chuyển lỗi thành contract mà native side hiểu được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="callback-lifetime">Callback lifetime và resource ownership</a>

<details>
<summary>Click for details</summary>

Callback pointer có thể được native library lưu lại để gọi **sau khi downcall ban đầu đã return**. Vì thế vòng đời của upcall stub phải dài ít nhất bằng khoảng thời gian phía native còn có thể gọi pointer đó.

Mẫu sai:

~~~text
tạo Arena trong method
→ tạo upcall stub
→ native library lưu callback
→ method return, Arena close
→ native library gọi lại address đã hết lifetime
~~~

Thiết kế đúng thường có quyền sở hữu rõ ràng: component đăng ký callback sở hữu Arena, giữ Arena sống cho tới khi unregister callback thành công, rồi mới close.

Nếu native API không có contract về unregister/vòng đời rõ ràng, callback binding cần đặc biệt thận trọng vì rất khó chứng minh lúc nào address có thể được giải phóng an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="callback-thread-boundary">Thread boundary khi native code callback vào Java</a>

<details>
<summary>Click for details</summary>

Native library có thể thực hiện callback từ luồng khác với luồng đã tạo binding. Vì vậy callback code không được dựa vào giả định rằng nó luôn chạy trên cùng luồng với downcall.

Các câu hỏi cần trả lời:

- trạng thái Java mà callback dùng có thread-safe không?
- MemorySegment được callback truy cập có cho phép luồng hiện tại access không?
- Arena đang dùng là confined hay shared?
- native library có serialize callback hay có thể gọi đồng thời?

Nếu callback cần truy cập segment từ nhiều luồng, confined arena có thể không phù hợp. Shared arena mở quyền truy cập nhiều luồng nhưng ứng dụng vẫn phải đồng bộ hóa dữ liệu.

Boundary ở đây là tính an toàn của FFM callback/thread; cơ chế sâu của Java Memory Model hoặc MethodHandle không thuộc chương này.

Call và callback chạy được vẫn chưa đủ để integration an toàn. Chương tiếp theo gom các failure boundary lại thành một model thống nhất: ownership leak, loading/linkage error, lifetime sai, descriptor mismatch, native error channel và nguy cơ crash/corruption.

</details>

- [Quay lại đầu trang](#back-to-top)
