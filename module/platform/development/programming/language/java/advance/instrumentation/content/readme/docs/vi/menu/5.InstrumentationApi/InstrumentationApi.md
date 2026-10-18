<a id="back-to-top"></a>

# Instrumentation API

## Menu
- [Vai trò của đối tượng Instrumentation](#instrumentation-service)
- [Đăng ký và quản lý transformer](#transformer-registration)
- [Khảo sát các class đã được nạp](#loaded-class-discovery)
- [Mô hình khả năng của Instrumentation](#capability-model)
- [Khả năng sửa đổi của từng class](#class-modifiability)
- [Đường tìm kiếm ClassLoader và redefineModule](#classloader-module-hooks)

## <a id="instrumentation-service">Vai trò của đối tượng Instrumentation</a>

<details>
<summary>Xem chi tiết</summary>

Instrumentation là lớp giao diện do JVM cung cấp cho agent để làm việc với các định nghĩa class. Thay vì agent gọi trực tiếp hook native/JVMTI, Java API gom các khả năng chính vào một đối tượng:

- đăng ký/gỡ ClassFileTransformer;
- xem class đã được nạp;
- kiểm tra class/module có thể sửa hay không;
- kiểm tra hỗ trợ redefine/retransform/native-prefix;
- yêu cầu retransform/redefine;
- mở rộng đường tìm kiếm của class loader;
- mở rộng quan hệ giữa các module;
- lấy ước lượng kích thước object.

Điểm quan trọng: Instrumentation không phải một “singleton tiện ích toàn cục”. Agent nhận đối tượng này từ premain/agentmain và nên coi nó là đại diện khả năng của JVM hiện tại.

Ví dụ:

~~~java
public static void premain(String args, Instrumentation inst) {
    System.out.println("Loaded classes: " + inst.getAllLoadedClasses().length);
}
~~~

API này là bề mặt điều khiển; kỹ nghệ bytecode vẫn nằm ở transformer và lớp công cụ.

Một API phụ dễ bị hiểu quá mức là getObjectSize(object): kết quả chỉ là **ước lượng phụ thuộc hiện thực** về bộ nhớ mà object tiêu thụ. Nó không phải kích thước sâu của toàn đồ thị đối tượng, không phù hợp để so sánh tuyệt đối giữa các hiện thực JVM và thậm chí ước lượng có thể thay đổi trong cùng một lần chạy JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transformer-registration">Đăng ký và quản lý transformer</a>

<details>
<summary>Xem chi tiết</summary>

addTransformer() đăng ký transformer cho các lần định nghĩa class trong tương lai. Overload hai tham số cho phép khai báo transformer có tham gia retransformation hay không:

~~~java
inst.addTransformer(transformer, false);
inst.addTransformer(retransformableTransformer, true);
~~~

Nếu canRetransform=true nhưng JVM không hỗ trợ khả năng này, thao tác đăng ký có thể thất bại.

Về mặt API, cùng một đối tượng transformer có thể được đăng ký nhiều lần, nhưng tài liệu Java khuyến cáo mạnh không làm vậy vì thứ tự thực thi và việc gỡ đăng ký trở nên khó suy luận. Thực tế nên tạo đối tượng riêng khi cần các lượt đăng ký độc lập.

removeTransformer() chỉ gỡ lượt đăng ký gần nhất khớp đối tượng. Nó không rollback những class đã biến đổi, và do quá trình nạp class đa luồng, lời gọi lại vẫn có thể tới transformer sau khi remove đã trả về.

Vì vậy việc quản lý transformer nên có vòng đời rõ:

~~~text
tạo transformer
→ đăng ký
→ sử dụng
→ vô hiệu hóa hành vi nếu cần
→ gỡ đăng ký
→ nếu cần, retransform/redefine các class bị ảnh hưởng để khôi phục
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loaded-class-discovery">Khảo sát các class đã được nạp</a>

<details>
<summary>Xem chi tiết</summary>

Instrumentation có hai API khám phá runtime dễ nhầm:

- getAllLoadedClasses(): tất cả class hiện đã được JVM nạp;
- getInitiatedClasses(loader): các class mà loader đó đã khởi tạo liên kết hoặc có thể tìm theo tên theo ngữ nghĩa API.

getAllLoadedClasses() bao gồm cả hidden class/interface và array class. Ngược lại, getInitiatedClasses(loader) không thể trả hidden class/interface (hoặc array có phần tử là hidden class) vì chúng không thể được tìm theo tên bằng ClassLoader::loadClass/Class::forName.

Agent nạp động thường dùng getAllLoadedClasses() để tìm class mục tiêu đã được nạp:

~~~java
if (!inst.isRetransformClassesSupported()) {
    return;
}

for (Class<?> type : inst.getAllLoadedClasses()) {
    if (type.getName().equals("com.example.OrderService")
            && inst.isModifiableClass(type)) {
        inst.retransformClasses(type);
    }
}
~~~

Không nên quét rồi retransform mọi class. Việc chọn mục tiêu cần lọc theo package/class/loader/module để tránh:

- instrument chính agent/helper của mình;
- sửa class JDK không mong muốn;
- kích hoạt vòng phụ thuộc;
- tăng chi phí lúc khởi động và khi chạy.

Các API khám phá cho phép agent nhìn danh sách class runtime, nhưng không thay thế mô hình tư duy về ClassLoader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="capability-model">Mô hình khả năng của Instrumentation</a>

<details>
<summary>Xem chi tiết</summary>

Một JVM không bắt buộc hỗ trợ mọi khả năng của Instrumentation. Các phép kiểm tra quan trọng:

~~~java
inst.isRedefineClassesSupported();
inst.isRetransformClassesSupported();
inst.isNativeMethodPrefixSupported();
~~~

Khả năng redefine/retransform cần đồng thời:

~~~text
manifest của agent yêu cầu khả năng
        +
cấu hình JVM hỗ trợ
        ↓
API báo true
~~~

Kết quả kiểm tra khả năng ổn định trong suốt một lần chạy JVM, nhưng code vẫn nên hỏi API thay vì mã hóa cứng theo vendor/phiên bản.

Can-Set-Native-Method-Prefix và setNativeMethodPrefix() phục vụ một trường hợp sử dụng chuyên biệt: hỗ trợ bọc native method bằng cách thử phân giải lại với prefix. Đây là khả năng nâng cao; người học chỉ cần nhận diện nó thuộc bề mặt API của Instrumentation, không cần biến module thành curriculum JNI.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-modifiability">Khả năng sửa đổi của từng class</a>

<details>
<summary>Xem chi tiết</summary>

JVM hỗ trợ retransform/redefine **không có nghĩa mọi Class đều có thể sửa đổi**. isModifiableClass(type) trả lời câu hỏi theo từng class.

Ví dụ:

~~~java
if (!inst.isRetransformClassesSupported()) {
    return;
}
if (!inst.isModifiableClass(OrderService.class)) {
    return;
}
inst.retransformClasses(OrderService.class);
~~~

Primitive class và array class không thể sửa đổi. Một hiện thực JVM cũng có thể có thêm class không thể sửa đổi.

Do đó cần tách hai tầng:

~~~text
khả năng ở cấp JVM
→ "runtime này hỗ trợ retransform/redefine không?"

khả năng sửa đổi ở cấp class
→ "class cụ thể này có được phép thay đổi không?"
~~~

Bỏ qua tầng thứ hai thường dẫn tới UnmodifiableClassException khi chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classloader-module-hooks">Đường tìm kiếm ClassLoader và redefineModule</a>

<details>
<summary>Xem chi tiết</summary>

Instrumentation cung cấp hai nhóm hook liên quan tới khả năng nhìn thấy:

**Đường tìm kiếm của class loader**

- appendToBootstrapClassLoaderSearch(JarFile)
- appendToSystemClassLoaderSearch(JarFile)

Chúng giúp agent đặt helper class vào đường tìm kiếm phù hợp. Nhưng bootstrap helper có khả năng nhìn thấy rất hạn chế: helper do bootstrap loader định nghĩa phải liên kết được tới những class mà loader đó nhìn thấy.

**Điều chỉnh module**

redefineModule() có thể **mở rộng** một module đang tồn tại bằng cách thêm reads, exports, opens, uses và provides. Nó không phải API thiết kế lại module graph tùy ý hoặc thu hồi quan hệ đã có.

Trước khi gọi, agent có thể dùng isModifiableModule(module). Với unnamed module, API báo modifiable nhưng redefineModule thực tế là no-op; named module mới là nơi việc mở rộng reads/exports/opens thường có ý nghĩa.

Quy ước của package Java agent còn hỗ trợ trường hợp phổ biến khi code đã biến đổi cần gọi helper: JVM bố trí để module của class đã biến đổi đọc unnamed module của bootstrap loader và system class loader. Cơ chế này không tự giải quyết mọi vấn đề exports/opens hoặc dependency của helper, nên agent vẫn phải suy luận theo loader và module cụ thể.

Mô hình tư duy:

~~~text
class đã biến đổi cần gọi helper
        ↓
helper phải có thể được nhìn thấy qua ranh giới loader/module
        ↓
hook đường tìm kiếm và/hoặc redefineModule
        ↓
chỉ mở rộng đủ cho nhu cầu instrumentation
~~~

Phần này cần nền tảng JPMS; ngữ nghĩa sâu của readability/exports/opens thuộc Java 9 Module System.

</details>

- [Quay lại đầu trang](#back-to-top)
