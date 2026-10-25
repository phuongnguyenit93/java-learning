<a id="back-to-top"></a>

# Linker, SymbolLookup và function descriptor

## Menu
- [Linker nối Java với native function như thế nào?](#linker-model)
- [SymbolLookup và tìm native symbol](#symbol-lookup)
- [Library-specific symbol lookup](#library-symbol-lookup)
- [FunctionDescriptor và native function signature](#function-descriptor)
- [Linker và ABI mapping](#linker-abi-mapping)
- [MethodHandle boundary với Dynamic Runtime](#method-handle-boundary)
- [jextract và generated bindings](#jextract-generated-bindings)

## <a id="linker-model">Linker nối Java với native function như thế nào?</a>

<details>
<summary>Click for details</summary>

Linker là thành phần FFM biết cách nối Java với foreign function theo ABI của native platform hiện tại. Trong Java 21, Linker.nativeLinker() trả về linker phù hợp với tổ hợp OS/CPU mà JVM đang chạy.

Để gọi một native function, Java cần ba mảnh thông tin:

1. **address** của function;
2. **FunctionDescriptor** mô tả input/output ở mức layout;
3. **Linker** biết cách chuyển contract đó thành lời gọi theo ABI.

~~~text
symbol address
      +
FunctionDescriptor
      +
native Linker
      ↓
downcall MethodHandle
~~~

Linker xử lý cơ chế calling convention của platform, nhưng không xác minh được rằng descriptor Java thật sự khớp function C. Contract vẫn phải do lập trình viên/library binding xác định chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symbol-lookup">SymbolLookup và tìm native symbol</a>

<details>
<summary>Click for details</summary>

SymbolLookup biến một **symbol name** thành address mà FFM có thể dùng. Kết quả lookup được biểu diễn bằng MemorySegment có địa chỉ của symbol.

~~~java
Linker linker = Linker.nativeLinker();
SymbolLookup lookup = linker.defaultLookup();

MemorySegment strlen = lookup.find("strlen")
        .orElseThrow();
~~~

Các nguồn lookup có phạm vi khác nhau:

- `linker.defaultLookup()` tìm trong tập library thường dùng do linker hiện tại xác định;
- `SymbolLookup.loaderLookup()` tìm symbol trong các native library gắn với class loader của bên gọi;
- `SymbolLookup.libraryLookup(...)` tạo lookup cho một library cụ thể.

Không phải mọi platform đều export cùng symbol trong default lookup. Vì vậy lỗi lookup phải được xem là vấn đề về khả năng/compatibility, không nên fallback sang một address đoán mò.

Sau khi có address, Java vẫn chưa thể call an toàn cho đến khi FunctionDescriptor mô tả đúng signature.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="library-symbol-lookup">Library-specific symbol lookup</a>

<details>
<summary>Click for details</summary>

Khi function nằm trong một library cụ thể, SymbolLookup.libraryLookup giúp load library và tạo lookup chỉ cho library đó.

~~~java
try (Arena arena = Arena.ofConfined()) {
    SymbolLookup lookup =
            SymbolLookup.libraryLookup("mylib", arena);

    MemorySegment function = lookup.find("compute")
            .orElseThrow();
}
~~~

Library lookup gắn lifetime với Arena được truyền vào. Với closeable arena, đóng arena sẽ kết thúc lifetime của lookup/library association theo contract của API.

Trong Java 21, libraryLookup là restricted operation vì việc load native library có thể chạy native code ngay trong quá trình load. Vì vậy nó thuộc native-access boundary, tách biệt với việc chỉ bật preview API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="function-descriptor">FunctionDescriptor và native function signature</a>

<details>
<summary>Click for details</summary>

FunctionDescriptor là bản mô tả **native function signature ở mức MemoryLayout**. Nó cho Linker biết return value và argument được biểu diễn thế nào tại ABI boundary.

Ví dụ function C:

~~~c
int add(int a, int b);
~~~

có thể được mô tả:

~~~java
FunctionDescriptor addDescriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT
);
~~~

FunctionDescriptor.ofVoid được dùng cho function không trả giá trị.

Descriptor không đọc header C để tự kiểm chứng. Nếu native signature thực tế khác — ví dụ pointer bị mô tả thành integer hoặc struct layout sai — Linker có thể tạo boundary dựa trên thông tin sai. Vì vậy descriptor là một phần của contract về tính đúng đắn, không chỉ là metadata.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linker-abi-mapping">Linker và ABI mapping</a>

<details>
<summary>Click for details</summary>

ABI mapping là bước Linker chuyển MemoryLayout/FunctionDescriptor sang quy tắc gọi thật của platform: argument nằm ở register hay stack, return value được trả như thế nào và native type/layout được truyền theo convention nào.

Linker.nativeLinker() chỉ nhắm tới ABI của platform mà JVM hiện tại đang chạy. Nó không phải cross-platform linker để Java trên Windows tạo call theo Linux ABI.

Điều này dẫn tới hai trách nhiệm tách biệt:

- **Linker**: biết cơ chế ABI của platform;
- **binding author**: cung cấp descriptor/layout đúng với native function.

Nếu library có nhiều ABI variant, ứng dụng phải chọn đúng binary trước. Linker không thể làm một Linux binary trở nên hợp lệ trên Windows hoặc sửa mismatch giữa header và library version.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handle-boundary">MethodHandle boundary với Dynamic Runtime</a>

<details>
<summary>Click for details</summary>

Kết quả phổ biến của Linker.downcallHandle là MethodHandle. Trong FFM, MethodHandle là **cầu invocation**: Java có thể gọi handle như một typed callable object và FFM chuyển invocation đó qua native boundary.

~~~java
MethodHandle add = linker.downcallHandle(
        addAddress,
        addDescriptor);
~~~

Module này chỉ cần hiểu ba điều:

1. type của MethodHandle được suy ra từ FunctionDescriptor và carrier mapping;
2. invoke với type/argument sai sẽ không trở thành native call hợp lệ;
3. MethodHandle ở đây là phương tiện được FFM tái sử dụng, không phải nội dung chính của native interoperability.

Lookup rules, adaptation/combinator và mechanics sâu của MethodHandle thuộc **Dynamic Runtime**. Không nên học lại toàn bộ java.lang.invoke chỉ để hiểu downcall.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jextract-generated-bindings">jextract và generated bindings</a>

<details>
<summary>Click for details</summary>

jextract là tooling hỗ trợ tạo Java bindings từ C header để giảm lượng boilerplate khi dùng FFM. Nó có thể sinh code đại diện cho function, constant và layout thay vì buộc developer viết mọi SymbolLookup/FunctionDescriptor bằng tay.

Vai trò của jextract trong module này là **supporting tooling**, không phải một abstraction nền tảng của FFM và cũng không thay thế việc hiểu ABI/lifetime.

Generated binding vẫn cần được review theo các câu hỏi:

- header có đúng version với native binary được deploy không?
- generated layout có đúng platform target không?
- lifetime của MemorySegment/Arena có phù hợp use case không?
- function nào là restricted/native-access boundary?
- callback/error ownership được quản lý ở đâu?

jextract hữu ích nhất sau khi người học đã hiểu luồng thủ công symbol → descriptor → linker. Khi đó generated code trở thành cách tự động hóa contract đã hiểu, thay vì “magic wrapper” khó gỡ lỗi.

Khi symbol resolution và function signature đã rõ, chương tiếp theo thực thi boundary theo cả hai chiều: downcall từ Java sang native code và upcall khi native code gọi callback Java.

</details>

- [Quay lại đầu trang](#back-to-top)
