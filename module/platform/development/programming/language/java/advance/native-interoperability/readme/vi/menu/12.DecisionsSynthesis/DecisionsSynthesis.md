<a id="back-to-top"></a>

# Lựa chọn JNI, FFM và thiết kế end-to-end

## Menu
- [JNI và FFM khác nhau ở đâu?](#jni-vs-ffm)
- [Tiêu chí chọn cơ chế interoperability](#interop-selection-criteria)
- [Deployment, portability và version constraints](#deployment-portability)
- [Thiết kế một native integration end-to-end](#end-to-end-native-integration)
- [Khi nào không nên dùng native interoperability?](#when-not-to-use-native)
- [Ranh giới với JVM, Dynamic Runtime và Java Version](#native-interoperability-handoffs)

## <a id="jni-vs-ffm">JNI và FFM khác nhau ở đâu?</a>

<details>
<summary>Click for details</summary>

JNI và FFM cùng giải quyết Java ↔ native interoperability nhưng tối ưu cho cách tích hợp khác nhau.

| Khía cạnh | JNI | FFM |
| --- | --- | --- |
| Mô hình chính | native method + JNI function interface | foreign memory + foreign function |
| Glue code | thường cần C/C++ JNI glue | nhiều binding có thể viết trực tiếp bằng Java |
| Java object interaction | mạnh, trực tiếp qua JNIEnv | không phải mục tiêu chính |
| C-style function API | làm được nhưng nhiều boilerplate | phù hợp tự nhiên với SymbolLookup/Linker |
| Vòng đời memory/tài nguyên | JNI có Java-object/reference semantics nhưng native allocation/tài nguyên vẫn cần quản lý riêng; không có scoped memory model tương đương Arena | MemorySegment/Arena biểu diễn scoped native-memory lifetime trực tiếp |
| Baseline Java 21 | ổn định lâu năm | preview API theo JEP 442 |

Không có lựa chọn “luôn tốt hơn”. Một thư viện JNI hiện hữu và ổn định không cần được viết lại chỉ vì FFM mới hơn; ngược lại một C API mới, đơn giản theo function/pointer/layout có thể dùng FFM để giảm glue code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interop-selection-criteria">Tiêu chí chọn cơ chế interoperability</a>

<details>
<summary>Click for details</summary>

Khi chọn cơ chế, nên bắt đầu từ contract thật của native side thay vì sở thích API.

Các câu hỏi hữu ích:

1. Native surface là C function đơn giản hay tích hợp sâu với Java object/class?
2. Đã có JNI wrapper được kiểm thử và bảo trì chưa?
3. Baseline JDK có chấp nhận FFM preview của Java 21 không?
4. Callback, pointer và vòng đời tài nguyên phức tạp đến mức nào?
5. Cần hỗ trợ những OS/architecture nào?
6. Đội ngũ có khả năng gỡ lỗi native crash, ABI mismatch và xử lý đóng gói không?

Một binding tốt cũng nên có Java-facing API nhỏ, không để consumer phải biết JNIEnv, raw symbol name hay lifetime detail nếu không cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deployment-portability">Deployment, portability và version constraints</a>

<details>
<summary>Click for details</summary>

Native interoperability khiến triển khai trở thành một phần của tính đúng đắn.

Ứng dụng cần quản lý:

- native artifact theo OS/architecture;
- dependency native đi kèm;
- search/load path;
- symbol/version compatibility;
- JDK/runtime option như --enable-preview và native access khi dùng FFM Java 21.

Khả năng chạy đa nền tảng không còn chỉ là “JAR chạy ở đâu cũng được”. Java layer có thể portable nhưng native binary thường phải được build riêng cho từng target.

Ràng buộc phiên bản cũng cần tách rõ: version của JDK, version JNI/FFM API, version native header và version binary library. Ma trận kiểm thử nên phản ánh các tổ hợp thực sự được hỗ trợ thay vì chỉ chạy trên máy developer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="end-to-end-native-integration">Thiết kế một native integration end-to-end</a>

<details>
<summary>Click for details</summary>

Một native integration end-to-end nên được thiết kế theo chuỗi contract thay vì bắt đầu ngay từ một call API.

~~~text
1. Xác định khả năng native cần dùng
        ↓
2. Chọn JNI hoặc FFM
        ↓
3. Chốt library + platform + ABI
        ↓
4. Chốt data layout / signature
        ↓
5. Thiết kế ownership + lifetime
        ↓
6. Thiết kế call/callback + thread boundary
        ↓
7. Chuyển đổi lỗi
        ↓
8. Đóng gói, kiểm thử và giải phóng tài nguyên
~~~

Mỗi bước nên có bằng chứng kiểm thử. Ví dụ: xác minh symbol tồn tại, assert layout size/offset, test callback sau nhiều lần đăng ký/hủy, test shutdown và test trên từng architecture được hỗ trợ.

Khi một integration khó gỡ lỗi, quay lại chuỗi trên giúp xác định lỗi thuộc loading, ABI, data, lifetime hay concurrency thay vì xem toàn bộ native side như một “hộp đen”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-not-to-use-native">Khi nào không nên dùng native interoperability?</a>

<details>
<summary>Click for details</summary>

Không nên dùng native interoperability chỉ vì API trông “low-level” hoặc có kỳ vọng mơ hồ rằng native sẽ nhanh hơn.

Nên ưu tiên giải pháp managed Java khi:

- JDK hoặc thư viện Java đã cung cấp khả năng tương đương;
- lợi ích native nhỏ nhưng ma trận triển khai lớn;
- đội ngũ không kiểm soát được native binary/version;
- khả năng cô lập lỗi quan trọng hơn vài phần trăm hiệu năng;
- workload chủ yếu bị giới hạn bởi I/O/network thay vì đoạn xử lý native cụ thể.

Việc vượt qua boundary còn có overhead riêng và làm profiling/debugging khó hơn. Chỉ nên nhận chi phí đó khi phía native đem lại khả năng hoặc giá trị đo được.

Quyết định “không dùng native” cũng là một kết quả thiết kế hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-interoperability-handoffs">Ranh giới với JVM, Dynamic Runtime và Java Version</a>

<details>
<summary>Click for details</summary>

Module này dừng ở **Java ↔ native boundary** và chủ động handoff các chủ đề sâu hơn cho module sở hữu phù hợp.

- **JVM**: runtime internals, native-memory internals của JVM, execution engine.
- **Dynamic Runtime**: MethodHandle/VarHandle mechanics, adaptation và typed invocation chi tiết.
- **Java Version / Java 22 FFM**: lịch sử thay đổi và finalization của FFM theo từng release.
- **Ngoài Java curriculum**: C/C++ language, compiler/linker toolchain và platform-specific ABI engineering chuyên sâu.
- **Infrastructure/runtime**: quản trị process/OS nói chung.

Ở đây người học chỉ cần đủ kiến thức các boundary trên để thiết kế native integration đúng. Ví dụ ta dùng MethodHandle như kết quả của Linker nhưng không học lại toàn bộ combinator; ta biết Java 22 final hóa FFM nhưng baseline thực hành vẫn là Java 21 preview.

Checkpoint cuối module: có thể giải thích từ lúc Java chọn/load native library, mô tả data/function contract, quản lý memory/callback lifetime, gọi qua JNI hoặc FFM, cho tới khi lỗi và tài nguyên được xử lý an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)
