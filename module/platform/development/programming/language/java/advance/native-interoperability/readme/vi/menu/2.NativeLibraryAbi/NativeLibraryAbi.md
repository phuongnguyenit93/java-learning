<a id="back-to-top"></a>

# Native library, ABI và linkage

## Menu
- [Mô hình native library trong Java](#native-library-model)
- [Locate và load native library](#native-library-loading)
- [System.load và System.loadLibrary](#system-load-vs-loadlibrary)
- [Symbol và native linkage](#native-symbol-resolution)
- [ABI và calling convention](#abi-calling-convention)
- [OS, CPU architecture và binary compatibility](#platform-binary-compatibility)

## <a id="native-library-model">Mô hình native library trong Java</a>

<details>
<summary>Click for details</summary>

Native library là binary đã được compiler/linker tạo cho một platform cụ thể và có thể được nạp vào process Java. Trên thực tế nó thường xuất hiện dưới dạng DLL trên Windows, shared object trên Linux hoặc dynamic library trên macOS.

Một library không chỉ là “file chứa code”. Nó còn mang **symbol** mà linker/runtime có thể tìm, và những symbol đó tuân theo một **ABI (Application Binary Interface)** về cách truyền argument, nhận return value, alignment và calling convention.

Trong Java có hai góc nhìn chính:

- với **JNI**, library chứa phần triển khai của native method hoặc đăng ký chúng qua RegisterNatives;
- với **FFM**, Java tìm symbol trong library bằng SymbolLookup rồi tạo foreign call thông qua Linker.

Vì vậy luồng luôn bắt đầu bằng cùng một câu hỏi: binary nào được load, trên nền tảng nào, và Java/native side đang đồng ý với binary contract nào?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-library-loading">Locate và load native library</a>

<details>
<summary>Click for details</summary>

Trước khi gọi native function, process phải tìm và load đúng library. “Đúng” ở đây bao gồm cả tên/path lẫn OS, CPU architecture và các dependency native mà library cần.

Với JNI, ứng dụng thường load library trước khi native method được dùng. Với FFM, SymbolLookup.libraryLookup có thể load một library và tạo lookup cho các symbol của library đó. Hai cơ chế khác API nhưng cùng phụ thuộc vào native loader của nền tảng.

Khi chẩn đoán lỗi loading, nên tách luồng thành các bước:

~~~text
Java biết library nào cần dùng?
        ↓
OS có tìm thấy file đó?
        ↓
binary có đúng architecture/format?
        ↓
các dependency native của library có resolve được?
        ↓
symbol cần gọi có tồn tại và đúng contract?
~~~

Nếu dồn tất cả thành “JNI lỗi” hoặc “FFM lỗi”, việc gỡ lỗi sẽ khó hơn nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="system-load-vs-loadlibrary">System.load và System.loadLibrary</a>

<details>
<summary>Click for details</summary>

System.load và System.loadLibrary đều yêu cầu JVM load native library, nhưng input của chúng khác nhau.

- **System.load(path)** nhận một filename tuyệt đối. Ứng dụng tự quyết định chính xác file nào được load.
- **System.loadLibrary(name)** nhận library name, không phải đường dẫn đầy đủ. JVM/OS mapping tên đó sang tên file native phù hợp và tìm theo cơ chế library search của runtime/platform.

Ví dụ:

~~~java
System.loadLibrary("imagecodec");

Path lib = Path.of("native", "imagecodec.dll")
        .toAbsolutePath();
System.load(lib.toString());
~~~

System.loadLibrary phù hợp khi cách đóng gói và đường dẫn tìm kiếm đã được kiểm soát. System.load hữu ích khi ứng dụng chủ động xác định artifact theo nền tảng hoặc vị trí cụ thể.

Cả hai đều có thể ném UnsatisfiedLinkError khi library không thể load hoặc liên kết. Lỗi này không chỉ có nghĩa “file không tồn tại”; sai kiến trúc CPU hoặc dependency native thiếu cũng có thể là nguyên nhân.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-symbol-resolution">Symbol và native linkage</a>

<details>
<summary>Click for details</summary>

Sau khi library đã load, runtime cần tìm **symbol** đại diện cho function hoặc dữ liệu native.

Với JNI, native method có thể được resolve theo naming convention của JNI hoặc được liên kết chủ động bằng RegisterNatives. Với FFM, SymbolLookup.find nhận symbol name và trả về address nếu tìm thấy; address đó sau đó được Linker dùng để tạo downcall.

Điều này tạo ra hai loại lỗi khác nhau:

- **lỗi phân giải library**: binary chưa load được;
- **lỗi phân giải symbol**: library đã có nhưng symbol mong đợi không tồn tại hoặc không được export dưới tên đó.

Symbol name là contract ở binary level. C++ name mangling, build flags, visibility/export rule hoặc version khác của native library có thể làm symbol name thay đổi. Vì thế header/source-level name không đảm bảo rằng binary export đúng symbol mà Java đang tìm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abi-calling-convention">ABI và calling convention</a>

<details>
<summary>Click for details</summary>

ABI là contract nhị phân giữa bên gọi và function native. Nó quy định những điều mà Java type system không thể tự kiểm tra: argument đi qua register hay stack, return value được đặt ở đâu, dữ liệu có size/alignment nào và calling convention hoạt động ra sao.

Ví dụ, một function C có vẻ đơn giản:

~~~c
long sum(long a, long b);
~~~

nhưng không nên suy luận rằng C long luôn tương đương Java long. Kích thước native type phụ thuộc ABI/platform. Khi dùng FFM, FunctionDescriptor và MemoryLayout phải mô tả đúng contract thực tế; khi dùng JNI, signature native phải đúng với JNI type contract.

Linker của FFM biết ABI của native platform mà JVM đang chạy và thực hiện phần adaptation cần thiết. Tuy nhiên Linker không thể sửa một FunctionDescriptor sai. Nếu Java mô tả sai signature, kết quả có thể từ IllegalArgumentException cho tới undefined native behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-binary-compatibility">OS, CPU architecture và binary compatibility</a>

<details>
<summary>Click for details</summary>

Native binary thường không portable theo cách bytecode Java portable. Một artifact build cho Linux x86-64 không thể mặc nhiên chạy trên Windows x86-64, và binary ARM64 cũng không thể được load như binary x86-64.

Compatibility cần được nhìn theo một ma trận:

~~~text
OS
× CPU architecture
× ABI / compiler-runtime expectation
× native dependency versions
× Java/JNI/FFM contract
~~~

Vì vậy triển khai thường cần nhiều native artifact, ví dụ một file cho Windows x64, một file cho Linux x64 và một file cho macOS ARM64. Ứng dụng nên chọn artifact theo nền tảng một cách rõ ràng và báo lỗi sớm nếu nền tảng không được hỗ trợ.

Không nên “sửa” incompatibility bằng cách đổi extension file. Binary format, instruction set và ABI mới là contract thật. Đây cũng là lý do native interoperability cần được cô lập sau một Java-facing API: phần còn lại của ứng dụng không nên biết chi tiết package theo nền tảng.

Khi library, symbol và ABI contract đã rõ, chương tiếp theo chuyển sang JNI: bridge truyền thống của JVM dùng các contract native đó để nối Java method với reference, exception và thread boundary.

</details>

- [Quay lại đầu trang](#back-to-top)
