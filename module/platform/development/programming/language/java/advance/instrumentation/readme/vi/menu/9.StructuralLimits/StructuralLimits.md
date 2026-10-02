<a id="back-to-top"></a>

# Giới hạn cấu trúc khi thay đổi class

## Menu
- [Ranh giới thay đổi class đã được nạp](#modification-boundary)
- [Giới hạn thay đổi field, method, signature và hierarchy](#schema-change-limits)
- [Các structural class-file attributes bị giới hạn](#structural-attribute-limits)
- [Các class không thể sửa đổi](#unmodifiable-classes)
- [Verifier, liên kết và ràng buộc định dạng](#verification-linkage-constraints)
- [Tương thích JDK và phiên bản class file](#version-compatibility)

## <a id="modification-boundary">Ranh giới thay đổi class đã được nạp</a>

<details>
<summary>Xem chi tiết</summary>

Class đã được JVM nạp không còn là “một file byte tùy ý có thể thay thế bằng bất kỳ schema nào”. JVM đang giữ:

- identity của Class;
- metadata constant-pool/runtime đã được phân giải;
- bố trí field;
- descriptor của method;
- các stack frame đang hoạt động;
- instance và trạng thái static;
- quan hệ kế thừa/module/class loader hiện hữu.

Vì vậy redefine/retransform chỉ cho phép một tập thay đổi tương thích với trạng thái runtime hiện tại.

Mô hình tư duy:

~~~text
phần hiện thực method
→ có thể thay đổi trong giới hạn

hình dạng class / bố trí object / identity kế thừa
→ phần lớn phải giữ ổn định
~~~

Instrumentation mạnh ở việc **thay phần hiện thực/chèn probe**, không phải di chuyển nóng toàn bộ schema kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="schema-change-limits">Giới hạn thay đổi field, method, signature và hierarchy</a>

<details>
<summary>Xem chi tiết</summary>

Các thay đổi cấu trúc lớn bị giới hạn, điển hình:

- thêm/xóa field;
- thêm/xóa method;
- đổi method signature;
- đổi modifiers của class, field hoặc method;
- đổi superclass hoặc các interface đã implement;
- đổi tên class.

Ví dụ muốn biến:

~~~java
class OrderService {
    void placeOrder() { ... }
}
~~~

thành:

~~~java
class OrderService extends NewBase {
    long agentState;              // field mới
    void placeOrder(String id) {} // signature mới
}
~~~

không phải loại thay đổi mà redefine/retransform nên kỳ vọng hỗ trợ.

Agent thường chèn logic vào **thân method hiện có** hoặc gọi helper bên ngoài để tránh thay đổi bố trí object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="structural-attribute-limits">Các structural class-file attributes bị giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

Ngoài field/method/hierarchy, một số attribute của class file mang ý nghĩa cấu trúc cũng không thể tùy ý thay đổi khi class đã được nạp. Trong quy tắc redefine/retransform của JVM TI trên JDK hiện đại, các nhóm quan trọng gồm:

- NestHost / NestMembers;
- Record;
- PermittedSubclasses.

Chúng không chỉ là metadata trang trí. Chúng ảnh hưởng quyền truy cập nestmate, identity/component của record và hệ phân cấp sealed class.

Vì vậy công cụ không nên nghĩ:

~~~text
"attribute chỉ là metadata"
→ "có thể viết lại thoải mái"
~~~

Thư viện bytecode có thể cho phép tạo bytes chứa attribute khác, nhưng JVM vẫn là nơi quyết định định nghĩa có hợp lệ cho redefine/retransform hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-classes">Các class không thể sửa đổi</a>

<details>
<summary>Xem chi tiết</summary>

Không phải mọi Class đều có thể sửa đổi.

Theo Instrumentation API:

- primitive class không thể sửa đổi;
- array class không thể sửa đổi;
- hiện thực JVM có thể có thêm class không thể sửa đổi.

Kiểm tra trước:

~~~java
if (!inst.isModifiableClass(target)) {
    // bỏ qua hoặc báo mục tiêu không được hỗ trợ
    return;
}
~~~

Hidden class/interface **không thể được sửa đổi bởi Java agent hoặc JVMTI agent**. getAllLoadedClasses() vẫn có thể trả về chúng, vì vậy “nhìn thấy Class object” không đồng nghĩa với khả năng redefine/retransform. isModifiableClass() phải phản ánh giới hạn này bằng false.

Một công cụ tốt nên báo rõ “class mục tiêu tồn tại nhưng không thể sửa đổi” thay vì để người dùng chỉ thấy UnmodifiableClassException mà không có ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verification-linkage-constraints">Verifier, liên kết và ràng buộc định dạng</a>

<details>
<summary>Xem chi tiết</summary>

Đầu ra của transformer phải vượt qua nhiều lớp kiểm tra:

~~~text
định dạng class file hợp lệ
        ↓
phiên bản class file được hỗ trợ
        ↓
verification
        ↓
tên / hierarchy / ràng buộc cấu trúc
        ↓
linkage
        ↓
định nghĩa được cài
~~~

Các lỗi có thể xuất hiện dưới dạng:

- ClassFormatError;
- UnsupportedClassVersionError;
- NoClassDefFoundError nếu tên class không khớp;
- ClassCircularityError;
- LinkageError;
- UnsupportedOperationException cho thay đổi cấu trúc không được hỗ trợ.

Điểm quan trọng là ClassFileTransformer chạy **trước** verification. Thư viện bytecode tạo ra byte[] thành công không có nghĩa JVM sẽ chấp nhận định nghĩa.

Kiểm thử transformer phải gồm cả trường hợp thất bại, không chỉ kiểm tra “ASM/Byte Buddy không ném lỗi”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="version-compatibility">Tương thích JDK và phiên bản class file</a>

<details>
<summary>Xem chi tiết</summary>

Định dạng class file tiến hóa theo JDK. Agent được build bằng một thư viện bytecode cũ có thể không hiểu phiên bản class file hoặc attribute mới của ứng dụng chạy trên JDK mới.

Các chiến lược:

- cố định phiên bản và chạy ma trận kiểm thử theo các JDK được hỗ trợ;
- cập nhật ASM/Byte Buddy khi cần hỗ trợ class file mới;
- tránh loại bỏ attribute mà công cụ không hiểu;
- bỏ qua an toàn bằng return null cho class/phiên bản chưa hỗ trợ nếu agent chỉ phục vụ observability;
- dừng sớm khi lỗi nếu instrumentation là yêu cầu bắt buộc về tính đúng đắn và phiên bản không được hỗ trợ là không thể chấp nhận.

Ví dụ:

~~~text
agent được biên dịch trên JDK 17
        ≠
không tự động an toàn cho mọi class file JDK 21/24
~~~

Tính tương thích gồm cả JVM API, phiên bản class file và công cụ bytecode — không chỉ mức source/target khi build agent JAR.

</details>

- [Quay lại đầu trang](#back-to-top)
