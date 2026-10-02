<a id="back-to-top"></a>

# Ownership, safety và failure boundary

## Menu
- [Ownership và cleanup của native resource](#native-resource-ownership)
- [Native library loading và linkage failures](#native-loading-failures)
- [JNI reference lifetime failures](#jni-reference-failures)
- [Preview enablement, restricted operations và native access](#native-access-control)
- [Layout, descriptor và ABI mismatch](#layout-descriptor-mismatch)
- [Use-after-close và temporal-safety failures](#lifetime-failures)
- [Java exception, native error code và error propagation](#native-error-boundary)
- [Thread safety và shared native state](#native-thread-safety)
- [JVM crash và silent memory corruption](#native-crash-corruption)

## <a id="native-resource-ownership">Ownership và cleanup của native resource</a>

<details>
<summary>Click for details</summary>

Ở native boundary, mỗi tài nguyên cần một **chủ sở hữu** và một thời điểm giải phóng rõ ràng. Tài nguyên không chỉ là native memory; nó có thể là JNI global reference, callback stub, native library association, file mapping hoặc handle do thư viện C trả về.

Một bảng quyền sở hữu đơn giản thường giúp phát hiện lỗi sớm:

| Tài nguyên | Ai tạo? | Ai giải phóng? | Vòng đời |
| --- | --- | --- | --- |
| Allocation từ closeable Arena (`ofConfined` / `ofShared`) | Java binding | Arena.close | theo tác vụ/component |
| Allocation từ `Arena.ofAuto()` / `Arena.global()` | Java binding/runtime | không close thủ công | theo GC-managed lifetime / process lifetime |
| JNI global reference | native code | DeleteGlobalRef | tới khi không cần giữ object |
| upcall stub | Java binding | theo Arena liên kết; `Arena.close()` nếu Arena closeable | tới khi callback đã unregister và Arena kết thúc vòng đời |
| native handle | native library | API close/free tương ứng | theo contract library |

Không phải mọi Arena đều close được: `ofConfined` và `ofShared` hỗ trợ explicit close, còn `ofAuto` và `global` không được giải phóng bằng `Arena.close()`. Nếu không trả lời được một trong ba cột cuối, thiết kế chưa đủ rõ để production hóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-loading-failures">Native library loading và linkage failures</a>

<details>
<summary>Click for details</summary>

Native library có thể thất bại ở nhiều tầng khác nhau. Phân loại đúng tầng giúp tránh gỡ lỗi sai chỗ.

~~~text
không tìm thấy file
→ lỗi loading

file có nhưng sai architecture / thiếu dependency
→ lỗi loader/linker

library load được nhưng không có symbol
→ lỗi symbol resolution

symbol có nhưng descriptor/ABI sai
→ lỗi tại call boundary, có thể crash
~~~

JNI thường biểu hiện một số lỗi loading/binding bằng UnsatisfiedLinkError. FFM SymbolLookup.find có thể không trả symbol mong đợi. OS loader còn có thông báo riêng về dependency hoặc binary format.

Khi điều tra, hãy ghi rõ library path/name, OS/architecture, dependency native và symbol/version đang kỳ vọng thay vì chỉ log “native call failed”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jni-reference-failures">JNI reference lifetime failures</a>

<details>
<summary>Click for details</summary>

JNI reference lifetime sai có hai dạng chính: **dùng reference sau khi hết hạn** và **giữ reference quá lâu**.

Local reference luôn gắn với thread và local frame JNI. Với local reference được tạo trong Java → native method call, reference đó hết hiệu lực khi native method trả về; không được cất vào native global state để dùng ở một call sau. Native thread được attach qua Invocation API là trường hợp khác: local reference của nó có thể tồn tại qua nhiều JNI call cho tới khi bị xóa/pop frame hoặc thread detach, nhưng vẫn không phải reference dùng chung qua thread. Nếu cần giữ Java object độc lập với local frame/thread lifetime, phải tạo global reference.

Ngược lại, global reference giữ Java object reachable cho tới khi DeleteGlobalRef. Quên xóa reference tạo leak dù heap profiler có thể khiến object trông như “vẫn còn được dùng”.

Trong loop native dài, quá nhiều local reference cũng có thể làm đầy local-reference table. DeleteLocalRef hoặc local frame giúp giữ phạm vi reference nhỏ.

Quy tắc thực tế: lifetime của reference phải phản ánh đúng vòng đời sử dụng của object, không dài hơn chỉ vì “đỡ phải tạo lại”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-access-control">Preview enablement, restricted operations và native access</a>

<details>
<summary>Click for details</summary>

Java 21 có **hai cổng độc lập** cần phân biệt.

Thứ nhất, FFM là preview API nên compile/run cần bật preview:

~~~bash
javac --release 21 --enable-preview NativeDemo.java
java --enable-preview NativeDemo
~~~

Thứ hai, một số FFM method là **restricted operation** vì có thể thực thi native code hoặc làm thay đổi giả định an toàn. Trong Java 21, nếu hoàn toàn không truyền `--enable-native-access`, runtime vẫn cho phép restricted call nhưng phát warning. Khi option này đã được truyền, chỉ các module được liệt kê mới có native access; restricted call từ bên gọi không được cấp quyền bị từ chối bằng `IllegalCallerException`. Có thể khai báo native access cho code trên class path bằng `ALL-UNNAMED`:

~~~bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     NativeDemo
~~~

Không nên diễn giải `--enable-native-access` là “cờ bật FFM”. Nhiều API FFM an toàn không đi qua restricted boundary; còn việc khai báo native access nên được xem như một ranh giới tin cậy có chủ đích cho code gọi restricted operation.

Khi ứng dụng dùng named module, nên cấp native access cho đúng module cần nó thay vì mở rộng không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-descriptor-mismatch">Layout, descriptor và ABI mismatch</a>

<details>
<summary>Click for details</summary>

Ba contract phải đồng ý với nhau: **MemoryLayout của data**, **FunctionDescriptor của call** và **ABI của native binary**.

Ví dụ các mismatch nguy hiểm:

- struct thiếu padding nên field offset bị lệch;
- pointer parameter bị mô tả thành integer;
- native function trả struct nhưng descriptor mô tả scalar;
- binary dùng calling convention khác;
- code build từ header version A nhưng runtime load library version B.

Một số mismatch bị Java/Linker phát hiện sớm, nhưng không phải tất cả. Native function pointer không mang đủ type information để runtime xác minh toàn bộ signature.

Vì vậy binding test nên có giá trị biên và structure known-value: ghi dữ liệu từ Java, đọc bên native và ngược lại; kiểm tra size/offset trước khi chạy workload thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifetime-failures">Use-after-close và temporal-safety failures</a>

<details>
<summary>Click for details</summary>

Use-after-close xảy ra khi code giữ MemorySegment hoặc callback address lâu hơn tài nguyên thật sự backing nó.

FFM giảm rủi ro bằng temporal safety: segment thuộc closeable Arena sẽ trở nên không hợp lệ sau khi Arena đóng và Java access tiếp theo thất bại.

~~~java
Arena arena = Arena.ofConfined();
MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
arena.close();

segment.get(ValueLayout.JAVA_LONG, 0); // lifetime đã kết thúc
~~~

Nhưng safety check của Java không bảo vệ native code đã lưu raw address trước đó. Nếu library giữ pointer rồi dùng sau khi Arena đóng, lỗi xảy ra ngoài vùng kiểm soát của segment.

Vì thế bất kỳ API “register pointer/callback for later” nào cũng cần protocol vòng đời tương ứng: giữ Arena sống → unregister thành công → mới close.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-error-boundary">Java exception, native error code và error propagation</a>

<details>
<summary>Click for details</summary>

Java và native library thường có hai hệ thống báo lỗi khác nhau. Java dùng exception; C API có thể trả số âm, NULL, errno hoặc error object riêng.

Boundary layer nên chuyển đổi lỗi một cách có chủ đích:

~~~text
native return/status
        ↓ kiểm tra kết quả theo contract của library
Java exception/result có ngữ cảnh
        ↓
ứng dụng xử lý theo Java contract
~~~

Với JNI, một Java exception phát sinh trong native call trở thành pending exception và phải được kiểm tra/propagate hoặc clear có chủ ý. Với FFM downcall, native error code không tự biến thành exception; binding phải đọc kết quả theo contract của library.

Call state như `errno` cần cách xử lý chặt hơn. Trong Java 21, khi linker của platform hỗ trợ state đó, hãy dùng `Linker.Option.captureCallState("errno")` (hoặc tên captured state khác được hỗ trợ). Downcall khi đó capture state ngay sau foreign call vào một segment do bên gọi cung cấp, trước khi hoạt động Java/runtime tiếp theo có thể ghi đè native error state theo cơ chế thông thường. Danh sách state được hỗ trợ và layout của vùng capture phụ thuộc platform và có thể được kiểm tra qua `Linker.Option.captureStateLayout()`.

Không nên vừa trả error code vừa ném exception cho cùng một contract nếu bên gọi không biết nguồn sự thật nào cần ưu tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-thread-safety">Thread safety và shared native state</a>

<details>
<summary>Click for details</summary>

Cho phép nhiều luồng nhìn thấy cùng native memory không đồng nghĩa dữ liệu đã thread-safe. Shared Arena chỉ cho phép truy cập từ nhiều luồng; nó không tạo lock hay atomic protocol cho native structure.

Các nguồn race phổ biến:

- Java và native thread cùng ghi một buffer;
- callback đến đồng thời trong khi Java đang close tài nguyên;
- native library giữ trạng thái toàn cục nhưng tài liệu của library yêu cầu serialize call;
- JNI global reference trỏ tới object có trạng thái có thể thay đổi nhưng không thread-safe.

Trước khi chia sẻ, cần xác định rõ ai được đọc/ghi, điều kiện nào cho phép close và library có đảm bảo thread safety hay không.

Nếu dữ liệu chỉ dùng trong một tác vụ một luồng, confined ownership thường dễ lập luận và an toàn hơn trạng thái dùng chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-crash-corruption">JVM crash và silent memory corruption</a>

<details>
<summary>Click for details</summary>

Điểm khác biệt lớn nhất giữa lỗi Java thuần và lỗi native là **mức độ nghiêm trọng của lỗi**. NullPointerException thường giữ JVM sống; native memory corruption có thể phá hỏng process trước khi Java có cơ hội ném exception.

Nguyên nhân thường gặp:

- gọi function pointer với sai descriptor;
- native code ghi vượt buffer;
- dereference pointer đã hết lifetime;
- callback stub đã được giải phóng;
- ABI/layout mismatch;
- bug trong chính native library.

Silent memory corruption còn nguy hiểm hơn crash vì triệu chứng có thể xuất hiện rất lâu sau thao tác gây lỗi.

Phòng ngừa tốt hơn “catch exception”: thu hẹp native boundary, dùng spatial/temporal checks của FFM, validate descriptor/layout, giữ ownership rõ và test bằng native sanitizer/debugger khi cần. Không có try/catch Java nào có thể biến mọi undefined native behavior thành recoverable error.

Khi các failure boundary này đã rõ, chương cuối biến chúng thành tiêu chí thiết kế: khi nào chọn JNI, FFM, wrapper có sẵn hoặc không dùng native boundary, và cách review toàn bộ integration end-to-end.

</details>

- [Quay lại đầu trang](#back-to-top)
