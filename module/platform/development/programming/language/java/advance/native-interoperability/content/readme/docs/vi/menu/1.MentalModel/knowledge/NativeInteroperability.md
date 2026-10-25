<a id="back-to-top"></a>

# Native Interoperability và biên Java ↔ native

## Menu
- [Native Interoperability là gì?](#native-interoperability-definition)
- [Vì sao Java cần Native Interoperability?](#native-interoperability-purpose)
- [Managed runtime và native boundary](#managed-native-boundary)
- [Khi nào Java cần đi ra native?](#native-interoperability-use-cases)
- [Chi phí và rủi ro khi vượt qua native boundary](#native-boundary-costs)

## <a id="native-interoperability-definition">Native Interoperability là gì?</a>

<details>
<summary>Click for details</summary>

Native interoperability là khả năng để code Java trao đổi dữ liệu hoặc gọi hành vi nằm ngoài Java runtime được quản lý. Phía bên kia biên có thể là một thư viện C/C++, một API của hệ điều hành, một vùng nhớ native/off-heap hoặc một ứng dụng native đang nhúng JVM.

Trong module này có hai cơ chế chính:

- **JNI (Java Native Interface)**: bridge truyền thống. Java khai báo native method, còn code native dùng JNI function interface để đọc dữ liệu, gọi method và làm việc với object của Java.
- **Foreign Function & Memory API (FFM)**: API Java hiện đại để mô tả native memory, native data layout và foreign function trực tiếp từ Java. Baseline của module là Java 21, nơi FFM vẫn là preview API.

Điểm quan trọng không phải chỉ là “Java gọi được C”, mà là hiểu **biên quản lý**. Khi execution hoặc memory đi qua biên này, các bảo đảm quen thuộc như type safety, automatic memory management hay exception propagation không còn bao phủ toàn bộ hệ thống.

~~~text
Java code
   ↓
JNI hoặc FFM
   ↓
ABI / symbol / native memory
   ↓
native library / OS / hardware
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-purpose">Vì sao Java cần Native Interoperability?</a>

<details>
<summary>Click for details</summary>

Java ưu tiên khả năng chạy đa nền tảng và an toàn, nhưng không phải mọi khả năng đều được viết bằng Java. Nhiều hệ thống đã có thư viện native lâu đời, driver hoặc SDK chỉ cung cấp C API, và một số chức năng cấp hệ điều hành không có abstraction Java tương đương.

Native interoperability tồn tại để Java **tái sử dụng khả năng bên ngoài runtime**, thay vì buộc phải viết lại toàn bộ hệ thống. Các lý do thường gặp gồm:

- tích hợp thư viện C/C++ có sẵn;
- gọi OS API hoặc SDK phần cứng;
- trao đổi với dữ liệu native có binary layout cố định;
- dùng vùng nhớ ngoài heap khi native library cần trực tiếp đọc/ghi;
- nhúng JVM vào một ứng dụng native hiện hữu.

Native code không nên được xem là “đường tắt để nhanh hơn”. Việc vượt qua boundary có chi phí về marshalling, triển khai, ABI compatibility, gỡ lỗi và safety. Nếu một thư viện Java thuần đáp ứng được yêu cầu với độ tin cậy tương đương, giữ phần triển khai trong managed Java thường đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-native-boundary">Managed runtime và native boundary</a>

<details>
<summary>Click for details</summary>

Trong managed runtime, JVM quản lý object Java, garbage collection, type checking và nhiều rule về thread/exceptions. Native code thì chạy theo contract của platform và ABI; nó có thể thao tác địa chỉ bộ nhớ trực tiếp, gọi code không do JVM quản lý và làm hỏng process nếu contract bị vi phạm.

~~~text
Managed Java side                 Native side
-----------------                 -------------------------
Java object                       pointer / native buffer
GC-managed lifetime      ↔        explicit/native lifetime
Java type system          ↔        ABI data representation
Java exception            ↔        error code / lỗi native
Java thread model         ↔        OS/native thread
~~~

JNI và FFM không loại bỏ boundary này. Chúng chỉ cung cấp contract để vượt qua boundary có cấu trúc hơn. Vì vậy khi thiết kế tích hợp, luôn phải trả lời bốn câu hỏi: dữ liệu có layout gì, ai sở hữu tài nguyên, lifetime kết thúc khi nào và lỗi được truyền qua boundary ra sao.

JVM internals sâu hơn không thuộc module này; ở đây chỉ dùng mental model managed-vs-native đủ để lập luận về interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-use-cases">Khi nào Java cần đi ra native?</a>

<details>
<summary>Click for details</summary>

Java nên đi ra native khi khả năng cần thiết thực sự nằm ở native side hoặc chi phí thay thế nó bằng Java là không hợp lý. Một số tình huống điển hình:

1. **Thư viện native có sẵn** — ví dụ engine xử lý media, numerical library hoặc SDK vendor chỉ cung cấp C API.
2. **OS/hardware integration** — gọi API đặc thù của platform, driver hoặc accelerator.
3. **Native data exchange** — làm việc với binary structure, pointer, shared/mapped memory mà native component hiểu trực tiếp.
4. **Embedding** — một ứng dụng viết bằng C/C++ cần khởi tạo JVM và gọi Java code thông qua JNI Invocation API.

~~~text
Có API Java chuẩn không?
    ↓ không
Có thư viện Java/JNI đã được đóng gói và bảo trì tốt không?
    ↓ không
Có bắt buộc dùng native API/binary contract hiện hữu không?
    ↓ có
Thiết kế boundary và vòng đời rõ ràng
~~~

Đây là quyết định kiến trúc, không chỉ là quyết định syntax. Native interoperability có giá trị nhất khi boundary nhỏ, rõ và được kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-boundary-costs">Chi phí và rủi ro khi vượt qua native boundary</a>

<details>
<summary>Click for details</summary>

Mỗi lần vượt qua native boundary, ứng dụng nhận thêm một tập rủi ro mà code Java thuần thường tránh được.

- **Khả năng chạy đa nền tảng**: binary native thường gắn với OS, CPU architecture và ABI cụ thể.
- **Triển khai**: phải đóng gói đúng DLL/shared library và dependency native liên quan.
- **Lifetime/ownership**: native memory, callback stub, library handle hoặc JNI global reference phải được giải phóng đúng thời điểm.
- **An toàn**: sai pointer, layout hoặc function descriptor có thể gây JVM crash hoặc silent memory corruption.
- **Gỡ lỗi**: stack trace Java không đủ để chẩn đoán lỗi native; có thể cần debugger và symbol native.
- **Error boundary**: Java exception và native error code không tự động chuyển đổi cho nhau.

Một nguyên tắc thực tế là **thu hẹp bề mặt native**: giữ phần đặc thù native sau một Java API nhỏ, kiểm thử contract ở boundary và tránh để chi tiết pointer/lifetime lan vào mã nghiệp vụ. Khi boundary càng rõ, khả năng thay đổi library, nâng JDK hoặc hỗ trợ thêm nền tảng càng tốt.

Các chương sau sẽ lần lượt giải quyết các nguồn rủi ro này: library/ABI trước, rồi JNI/FFM, memory lifetime, function linkage và cuối cùng là lỗi/tổng hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
