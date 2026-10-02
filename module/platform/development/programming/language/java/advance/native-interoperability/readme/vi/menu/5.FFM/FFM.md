<a id="back-to-top"></a>

# Foreign Function & Memory API

## Menu
- [FFM là gì và vì sao tồn tại?](#ffm-definition-purpose)
- [FFM và JNI giải quyết các lớp bài toán nào?](#ffm-jni-relationship)
- [Java 21 preview và Java 22 finalization](#ffm-java21-java22-boundary)
- [MemorySegment, Arena, MemoryLayout, Linker và SymbolLookup](#ffm-core-abstractions)
- [Safety model của FFM](#ffm-safety-model)
- [Restricted operations và native access](#ffm-restricted-operations)

## <a id="ffm-definition-purpose">FFM là gì và vì sao tồn tại?</a>

<details>
<summary>Click for details</summary>

Foreign Function & Memory API (FFM) cung cấp Java API để làm việc với code và data nằm ngoài Java runtime. Hai bài toán được gom vào cùng một model:

- **foreign memory**: biểu diễn và truy cập vùng nhớ trong/ngoài heap bằng MemorySegment;
- **foreign function**: tìm native symbol và tạo call boundary bằng Linker.

FFM giúp phần lớn binding logic được mô tả bằng Java thay vì phải viết một lớp JNI glue bằng C chỉ để gọi function native. Tuy nhiên FFM không biến native code thành managed Java: ABI, pointer, lifetime và crash risk vẫn tồn tại.

Baseline của module là **Java 21**, nơi package java.lang.foreign là preview API. Vì vậy mọi ví dụ FFM phải được hiểu trong preview context của JDK 21.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-jni-relationship">FFM và JNI giải quyết các lớp bài toán nào?</a>

<details>
<summary>Click for details</summary>

JNI và FFM cùng giải quyết Java ↔ native interoperability nhưng tiếp cận khác nhau.

**JNI** tập trung vào bridge giữa JVM và phần triển khai native method. Native code nhận JNIEnv và có API phong phú để thao tác object/class/method Java. Đây là lựa chọn tự nhiên khi đã có JNI code hoặc native side cần tích hợp sâu với Java object model.

**FFM** tập trung vào foreign function và foreign memory. Java mô tả address, signature và data layout bằng MemorySegment, FunctionDescriptor, MemoryLayout và Linker. Với C-style library API, điều này thường giảm số lượng handwritten JNI glue.

Không nên xem FFM là “JNI nhưng nhanh hơn” hay JNI là “FFM cũ”. Chúng có mô hình lập trình khác nhau. Tiêu chí chọn cần dựa trên bề mặt native hiện hữu, callback/data model, khả năng bảo trì, JDK baseline và ràng buộc triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-java21-java22-boundary">Java 21 preview và Java 22 finalization</a>

<details>
<summary>Click for details</summary>

Trong **Java 21**, FFM là preview API theo JEP 442. Source dùng java.lang.foreign chỉ compile và run khi preview features được bật.

Ví dụ command line tối thiểu:

~~~bash
javac --release 21 --enable-preview NativeDemo.java
java --enable-preview NativeDemo
~~~

Java 22 là mốc FFM được final hóa, nhưng module này không đi sâu vào lịch sử thay đổi từng release. Điều cần nhớ ở baseline Java 21 là source/API contract vẫn mang trạng thái preview và mã production phải chủ động chấp nhận ràng buộc đó.

**Preview enablement không đồng nghĩa với native-access enablement.** --enable-preview cho phép dùng preview API; restricted operation thuộc một security/safety boundary khác và được trình bày riêng ở phần native access.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-core-abstractions">MemorySegment, Arena, MemoryLayout, Linker và SymbolLookup</a>

<details>
<summary>Click for details</summary>

FFM gồm một nhóm abstraction phối hợp với nhau:

- **MemorySegment**: một vùng nhớ liên tục có size, lifetime và access bounds.
- **Arena**: quản lý vòng đời của native segment được allocate qua arena.
- **MemoryLayout**: mô tả hình dạng dữ liệu trong memory: primitive value, struct, sequence, union, address.
- **SymbolLookup**: tìm address của named symbol trong library hoặc tập symbol đã biết.
- **FunctionDescriptor**: mô tả input/output layout của foreign function.
- **Linker**: chuyển ABI-level description thành downcall/upcall boundary mà Java có thể invoke.

Luồng điển hình:

~~~text
load/find library
    ↓
SymbolLookup.find("function")
    ↓
FunctionDescriptor mô tả signature
    ↓
Linker tạo downcall MethodHandle
    ↓
MemorySegment/Arena giữ native data và lifetime
~~~

Các chương tiếp theo tách từng abstraction để người học không phải hiểu toàn bộ FFM cùng một lúc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-safety-model">Safety model của FFM</a>

<details>
<summary>Click for details</summary>

FFM đưa một số safety property của native memory vào Java API thay vì để mọi thứ là raw pointer.

MemorySegment có **spatial bounds**: access phải nằm trong phạm vi segment. Segment cũng có **temporal bounds** thông qua scope/lifetime; access sau khi arena phù hợp đã đóng sẽ thất bại thay vì tiếp tục dereference vùng nhớ đã được giải phóng.

Arena còn biểu diễn chính sách truy cập theo luồng. Segment từ confined arena chỉ được truy cập bởi owner thread; shared arena cho phép nhiều thread truy cập nhưng không tự giải quyết data race.

Các bảo đảm này giúp bắt được nhiều lỗi ở Java boundary, nhưng không thể chứng minh native function an toàn. Nếu FunctionDescriptor sai, native code ghi ngoài vùng thực hoặc callback dùng address đã hết lifetime, JVM vẫn có thể crash hoặc memory bị corrupt.

Vì vậy “FFM safer than raw pointer” không có nghĩa “FFM eliminates native risk”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ffm-restricted-operations">Restricted operations và native access</a>

<details>
<summary>Click for details</summary>

Java 21 đánh dấu một số FFM operation là **restricted** vì chúng có thể làm JVM crash hoặc gây silent memory corruption khi contract bị mô tả sai. Ví dụ gồm tạo foreign call/upcall boundary, load library qua libraryLookup và reinterpret segment trong các trường hợp cho phép thay đổi giả định về spatial/temporal bounds.

Đây là vấn đề tách biệt với preview:

~~~text
--enable-preview
→ cho phép dùng preview API của Java 21

--enable-native-access=<module>
→ khai báo module được cấp native access để restricted operation chạy không phát warning
~~~

Trong Java 21, nếu không truyền `--enable-native-access`, restricted operation vẫn được phép chạy nhưng runtime phát warning. Nếu đã truyền option này, native access chỉ được cấp cho các module được liệt kê; restricted call từ bên gọi không có native access bị từ chối bằng `IllegalCallerException`. Code chạy trên class path có thể dùng `ALL-UNNAMED` để khai báo native access và tránh warning:

~~~bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     NativeDemo
~~~

Không nên bật native access rộng chỉ để “hết cảnh báo”. Tùy chọn này nên phản ánh một ranh giới tin cậy có chủ đích: module nào thật sự cần thực hiện restricted operation thì module đó mới nên được khai báo native access.

Từ overview này, các abstraction FFM sẽ được tách ra theo trách nhiệm. Chương tiếp theo bắt đầu với MemorySegment, vì Java cần mô hình một vùng memory có bounds trước khi có thể lập luận chính xác về lifetime, layout và foreign call.

</details>

- [Quay lại đầu trang](#back-to-top)
