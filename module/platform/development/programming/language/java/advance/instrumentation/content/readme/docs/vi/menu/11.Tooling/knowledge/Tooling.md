<a id="back-to-top"></a>

# Công cụ biến đổi bytecode

## Menu
- [Nguyên tắc chọn công cụ bytecode](#tool-selection-principles)
- [ASM ở vai trò công cụ hỗ trợ triển khai](#asm-supporting-role)
- [Byte Buddy ở vai trò công cụ hỗ trợ triển khai](#byte-buddy-supporting-role)
- [Điều khiển mức thấp và mức trừu tượng cao](#low-level-vs-abstraction)
- [Khả năng nhìn thấy helper class qua ranh giới class loader](#helper-class-visibility)
- [Tương thích công cụ, JDK và phiên bản class file](#tooling-version-compatibility)

## <a id="tool-selection-principles">Nguyên tắc chọn công cụ bytecode</a>

<details>
<summary>Xem chi tiết</summary>

Không có một công cụ bytecode “tốt nhất cho mọi agent”. Chọn công cụ theo câu hỏi:

~~~text
cần kiểm soát instruction/class-file ở mức thấp?
→ ưu tiên API mức thấp như ASM

cần mô tả phép biến đổi theo type/method matcher và advice?
→ mức trừu tượng cao như Byte Buddy thường dễ hơn

JDK mục tiêu đủ mới để dùng Class-File API chuẩn?
→ cân nhắc java.lang.classfile ở JDK phù hợp
~~~

Các tiêu chí quan trọng:

- phiên bản class file được hỗ trợ;
- khả năng giữ lại attributes;
- xử lý stack-map/frame;
- độ ổn định API;
- kích thước dependency;
- chiến lược helper/auxiliary class;
- tích hợp với redefine/retransform;
- mức độ dễ kiểm thử/gỡ lỗi.

Công cụ chỉ là lựa chọn triển khai. Vòng đời agent, việc chọn mục tiêu, các khả năng và giới hạn cấu trúc vẫn do Instrumentation/JVM quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asm-supporting-role">ASM ở vai trò công cụ hỗ trợ triển khai</a>

<details>
<summary>Xem chi tiết</summary>

ASM là thư viện bytecode mức thấp, phổ biến để đọc/ghi class file qua API dạng visitor/tree. Trong Instrumentation, ASM phù hợp khi:

- cần kiểm soát chính xác method/instruction;
- phép biến đổi nhỏ nhưng nhạy về hiệu năng;
- đội ngũ đã có chuyên môn bytecode/JVM;
- cần tránh mức trừu tượng tạo cơ chế phụ trợ ngoài ý muốn.

Mô hình tư duy:

~~~text
classfileBuffer
→ ClassReader
→ biến đổi bằng visitor/tree
→ ClassWriter
→ byte[]
~~~

Đổi lại, lập trình viên phải chịu nhiều trách nhiệm hơn:

- chọn API/class-file version đúng;
- xử lý frame/max stack đúng;
- giữ luồng điều khiển hợp lệ;
- không làm mất attribute quan trọng;
- kiểm thử trên đầu ra của nhiều JDK/compiler.

Module này chỉ cần học ASM đủ để hiểu vai trò triển khai; API chi tiết của toàn thư viện không phải nội dung chính.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-buddy-supporting-role">Byte Buddy ở vai trò công cụ hỗ trợ triển khai</a>

<details>
<summary>Xem chi tiết</summary>

Byte Buddy cung cấp mức trừu tượng cao hơn quanh việc sinh/biến đổi bytecode. Thay vì thao tác instruction trực tiếp, code thường diễn đạt:

~~~text
match type
→ match method
→ apply advice/delegation
→ install through instrumentation
~~~

Điều này phù hợp với agent cần:

- ghép khớp method phức tạp;
- mẫu advice/interceptor;
- auxiliary types;
- tích hợp sẵn với Java Agent/Instrumentation;
- khả năng bảo trì tốt hơn cho đội ngũ không chuyên opcode.

Đánh đổi là mức trừu tượng che một phần chi tiết. Khi gặp VerifyError, vấn đề khả năng nhìn thấy qua class loader hoặc xung đột multi-agent, lập trình viên vẫn phải quay lại mô hình tư duy class file/JVM.

Byte Buddy không làm biến mất các ràng buộc của Instrumentation. Nó không thể biến thay đổi cấu trúc bị JVM cấm thành hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="low-level-vs-abstraction">Điều khiển mức thấp và mức trừu tượng cao</a>

<details>
<summary>Xem chi tiết</summary>

So sánh ở mức tư duy:

| Mức | Ưu điểm | Chi phí |
| --- | --- | --- |
| Kiểu ASM mức thấp | kiểm soát bytecode chi tiết, ít mức trừu tượng | cần chuyên môn JVM/class file cao |
| Kiểu Byte Buddy mức cao | matcher/advice dễ diễn đạt, code dễ bảo trì | cần hiểu mức trừu tượng và helper được sinh ra |
| Class-File API chuẩn | API nền tảng chuẩn ở JDK mới | phụ thuộc mốc cơ sở JDK và ranh giới phiên bản |

Không nên chọn chỉ dựa trên số dòng code.

Ví dụ agent đơn giản “đo mọi method có annotation @Timed” có thể dễ với matcher mức cao. Nhưng công cụ kiểu compiler cần viết lại instruction đặc biệt có thể phù hợp với API mức thấp hơn.

Nguyên tắc: chọn **mức trừu tượng cao nhất vẫn cho phép kiểm soát chính xác ràng buộc của trường hợp sử dụng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="helper-class-visibility">Khả năng nhìn thấy helper class qua ranh giới class loader</a>

<details>
<summary>Xem chi tiết</summary>

Một phép biến đổi thường chèn lời gọi tới helper:

~~~text
OrderService.placeOrder()
→ AgentRuntime.onEnter()
→ AgentRuntime.onExit()
~~~

Nhưng class mục tiêu chỉ gọi được helper nếu helper có thể được nhìn thấy và liên kết từ defining loader/module của class đó.

Các chiến lược thường gặp:

- bootstrap helper JAR qua appendToBootstrapClassLoaderSearch;
- helper trên system loader hoặc application loader khi phù hợp;
- auxiliary class được chèn theo loader;
- redefineModule để mở reads/opens/exports cần thiết.

Bẫy điển hình là cùng tên Helper nhưng được hai ClassLoader định nghĩa → identity kiểu khác nhau.

Vì vậy “bytecode đã chèn INVOKESTATIC đúng” chưa đủ; phải hỏi:

~~~text
class mục tiêu sẽ phân giải helper bằng loader nào?
module có read/open/export phù hợp không?
dependency của helper có thể được nhìn thấy không?
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-version-compatibility">Tương thích công cụ, JDK và phiên bản class file</a>

<details>
<summary>Xem chi tiết</summary>

Phiên bản công cụ phải theo kịp phiên bản class file của ứng dụng mục tiêu.

Ma trận kiểm thử nên bao gồm:

~~~text
JDK dùng để build agent
× JDK runtime
× compiler của ứng dụng / phiên bản class file
× phiên bản công cụ bytecode
× các phiên bản framework quan trọng
~~~

Ví dụ một phiên bản ASM cũ có thể từ chối phiên bản class file mới; phiên bản Byte Buddy cũ có thể chưa hỗ trợ tính năng/attribute mới. Ngược lại, agent được build bằng API JDK 24 sẽ không chạy trên mốc cơ sở JDK 21 nếu dependency bytecode/API yêu cầu Java 24.

Thực hành tốt:

- công bố dải phiên bản JDK được hỗ trợ;
- CI chạy các ứng dụng đại diện trên từng dải;
- cố định phiên bản công cụ thay vì để thả nổi;
- nâng cấp công cụ cùng quá trình áp dụng JDK mới;
- có hành vi rõ với phiên bản class file chưa biết: bỏ qua hay báo lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
