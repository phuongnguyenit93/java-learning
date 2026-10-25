<a id="back-to-top"></a>

# Ranh giới kỹ nghệ bytecode

## Menu
- [Mô hình biến đổi bytecode trong agent](#bytecode-transformation-model)
- [Sinh class mới và biến đổi class hiện có](#generation-vs-transformation)
- [Tính hợp lệ của class file sau transformation](#classfile-validity)
- [Instrumentation cần biết kỹ nghệ bytecode tới mức nào?](#bytecode-engineering-scope)
- [Class-File API được chuẩn hóa trong Java 24 và ranh giới với Java Version](#standard-classfile-api-version-boundary)

## <a id="bytecode-transformation-model">Mô hình biến đổi bytecode trong agent</a>

<details>
<summary>Xem chi tiết</summary>

Trong ngữ cảnh của agent, phép biến đổi bytecode có thể được mô hình hóa như một hàm:

~~~text
class-file bytes hiện tại
        +
chính sách biến đổi
        ↓
class-file bytes mới
~~~

Instrumentation không yêu cầu agent phải tự phân tích constant pool hay opcode bằng tay. Nó chỉ yêu cầu transformer trả về bytes hợp lệ. Lớp kỹ nghệ bytecode có nhiệm vụ:

- phân tích đầu vào;
- tìm method/instruction cần chỉnh;
- thêm/xóa/thay code theo chính sách;
- cập nhật metadata/stack maps khi cần;
- tạo class-file bytes mới.

Ví dụ timing probe:

~~~text
điểm vào method
→ ghi thời điểm bắt đầu

mọi đường thoát bình thường hoặc bằng exception
→ ghi thời lượng
~~~

Điểm khó không nằm ở vài opcode riêng lẻ mà ở việc giữ luồng điều khiển, trạng thái stack/local và các bất biến của class file hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generation-vs-transformation">Sinh class mới và biến đổi class hiện có</a>

<details>
<summary>Xem chi tiết</summary>

**Sinh class** và **biến đổi class** có quan hệ nhưng không giống nhau.

Sinh class:

~~~text
không có định nghĩa class cũ
→ tạo class mới từ mô hình/builder
→ tạo byte[]
~~~

Biến đổi:

~~~text
đã có class-file bytes
→ giữ phần lớn cấu trúc
→ thay một phần cần thiết
→ tạo byte[] mới
~~~

Java Agent thường cần phép biến đổi vì class mục tiêu đã tồn tại trong ứng dụng. Tuy nhiên công cụ bytecode vẫn có thể sinh helper/proxy/auxiliary class.

Ranh giới của module:

- hiểu phép biến đổi đủ để instrument method đúng;
- hiểu việc sinh class đủ để phân biệt trường hợp sử dụng;
- không biến module thành khóa học compiler/code generator tổng quát.

Khi class đã được nạp, việc sinh class mới cũng **không né được** các giới hạn cấu trúc của redefine/retransform đối với class cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-validity">Tính hợp lệ của class file sau transformation</a>

<details>
<summary>Xem chi tiết</summary>

Class-file bytes kết quả phải thỏa định dạng class file của JVM và các ràng buộc của thao tác hiện tại.

Một công cụ bytecode thường giúp tính:

- entry của constant pool;
- cách mã hóa instruction;
- offset của nhánh;
- bảng exception;
- stack-map frame;
- attribute.

Nhưng agent vẫn chịu trách nhiệm về ngữ nghĩa. Ví dụ công cụ có thể tạo method gọi Helper.record(), nhưng nếu Helper không thể được nhìn thấy từ defining loader/module của class mục tiêu, verification/linkage có thể thất bại dù bytes “đúng định dạng”.

Việc xác minh nên có nhiều tầng:

~~~text
công cụ phân tích/tạo bytes
→ kiểm thử verification/nạp class độc lập
→ kiểm thử tích hợp agent
→ kiểm thử nhanh trên ứng dụng mục tiêu
→ kiểm thử tương thích multi-agent / JDK mục tiêu
~~~

“Thư viện bytecode không ném lỗi” chỉ là tầng đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-engineering-scope">Instrumentation cần biết kỹ nghệ bytecode tới mức nào?</a>

<details>
<summary>Xem chi tiết</summary>

Người học Instrumentation không cần trở thành chuyên gia kỹ nghệ bytecode trước khi viết agent. Mức kiến thức phù hợp là:

**Cần biết**

- class file chứa method/code/attribute;
- transformer nhận và trả byte[];
- thân method có luồng điều khiển + operand stack/local variables;
- verification/linkage xảy ra sau phép biến đổi;
- phiên bản class file/attribute thay đổi theo JDK;
- mức trừu tượng của công cụ luôn có đánh đổi.

**Chuyển phần chuyên sâu sang JVM / miền bytecode**

- bố trí nhị phân constant pool chi tiết;
- thuật toán verifier chuyên sâu;
- mọi opcode ngữ nghĩa;
- bộ tối ưu bytecode/compiler backend tùy biến.

Mục tiêu là đủ để rà soát rủi ro của phép biến đổi và gỡ lỗi agent, không phải viết assembler từ đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="standard-classfile-api-version-boundary">Class-File API được chuẩn hóa trong Java 24 và ranh giới với Java Version</a>

<details>
<summary>Xem chi tiết</summary>

Java platform hiện có Class-File API chuẩn trong java.lang.classfile. Lịch sử:

~~~text
Java 22 → Preview
Java 23 → Second Preview
Java 24 → Standard API
~~~

API này cung cấp mô hình để phân tích, sinh và biến đổi class file. Nó có thể trở thành một lựa chọn công cụ quan trọng cho agent trên JDK phù hợp.

Nhưng module Instrumentation không sở hữu curriculum đầy đủ của java.lang.classfile. Lý do:

- Instrumentation là quy ước về agent runtime và vòng đời đã tồn tại từ lâu;
- Class-File API là API nền tảng riêng cho việc xử lý class file;
- người học dùng JDK 21 vẫn cần ASM/Byte Buddy hoặc công cụ tương thích khác;
- quá trình tiến hóa API theo phiên bản thuộc Java Version.

Ở đây chỉ cần hiểu điểm nối:

~~~text
ClassFileTransformer
→ cần byte[] mới
→ Class-File API / ASM / Byte Buddy có thể giúp tạo bytes
~~~

</details>

- [Quay lại đầu trang](#back-to-top)
