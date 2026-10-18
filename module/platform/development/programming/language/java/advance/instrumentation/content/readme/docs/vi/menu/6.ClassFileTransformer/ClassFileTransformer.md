<a id="back-to-top"></a>

# ClassFileTransformer

## Menu
- [Quy ước của ClassFileTransformer](#transformer-contract)
- [Ngữ cảnh đầu vào của một lần transform](#transform-input-context)
- [Kết quả transform: bytecode mới, không thay đổi hoặc lỗi](#transform-result-contract)
- [Exception và cách xử lý lỗi trong transformer](#transform-failure-contract)
- [Chọn class mục tiêu theo class, loader và module](#target-selection)
- [Gỡ transformer và giới hạn của removeTransformer](#transformer-removal)

## <a id="transformer-contract">Quy ước của ClassFileTransformer</a>

<details>
<summary>Xem chi tiết</summary>

ClassFileTransformer là quy ước callback giữa chuỗi Instrumentation của JVM và code biến đổi bytecode của agent. Sau khi transformer được đăng ký bằng Instrumentation.addTransformer(), JVM có thể gọi transform khi:

- class được định nghĩa lần đầu;
- class được redefine;
- class được retransform, nếu transformer được đăng ký với canRetransform=true.

Điểm cần nhớ: transformer chạy **trước khi định nghĩa class mới được JVM kiểm tra và áp dụng**. Vì vậy đầu ra của transformer phải là class-file bytes hợp lệ và phải phù hợp với loại thao tác đang diễn ra.

Một transformer tối thiểu:

~~~java
final class TimingTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {

        if (!"com/example/OrderService".equals(className)) {
            return null;
        }
        return transformOrderService(classfileBuffer);
    }
}
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transform-input-context">Ngữ cảnh đầu vào của một lần transform</a>

<details>
<summary>Xem chi tiết</summary>

Callback transform cung cấp đủ ngữ cảnh để agent quyết định có nên xử lý class hay không:

- module: module chứa class;
- loader: defining loader, có thể null cho bootstrap loader;
- className: binary name ở dạng nội bộ, ví dụ java/util/List;
- classBeingRedefined: null ở lần nạp đầu, khác null khi redefine/retransform;
- protectionDomain: ngữ cảnh bảo vệ của class;
- classfileBuffer: bytes đầu vào theo định dạng class file.

className dùng dấu slash thay vì dấu chấm:

~~~java
if (className != null
        && className.startsWith("com/example/")) {
    // candidate
}
~~~

Không nên chọn mục tiêu chỉ theo className nếu ứng dụng có nhiều ClassLoader. Cùng một binary name do hai loader khác nhau định nghĩa là hai Class khác nhau. Với agent/framework phức tạp, module + loader + className thường là bộ lọc an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transform-result-contract">Kết quả transform: bytecode mới, không thay đổi hoặc lỗi</a>

<details>
<summary>Xem chi tiết</summary>

Transformer có ba kết quả thực tế:

**Không thay đổi**

~~~java
return null;
~~~

JVM tiếp tục dùng đầu vào hiện tại.

**Thay đổi**

~~~java
return transformedBytes;
~~~

Theo quy ước, transformer **nên** tạo một mảng byte mới chứa class file hợp lệ khi có thay đổi; điều bắt buộc là không được sửa trực tiếp classfileBuffer đầu vào.

**Không thể xử lý định dạng**

~~~java
throw new IllegalClassFormatException("...");
~~~

Điểm quan trọng là đầu ra không được “gần đúng”. JVM còn phải verify/link class sau chuỗi biến đổi; lỗi định dạng/phiên bản/liên kết có thể làm thao tác thất bại.

Công cụ bytecode nên nhận đầu vào, tạo model/visitor riêng rồi sinh mảng byte mới. Điều này cũng giúp transformer dễ thử lại/retransform hơn so với sửa tại chỗ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transform-failure-contract">Exception và cách xử lý lỗi trong transformer</a>

<details>
<summary>Xem chi tiết</summary>

Nếu transform() ném exception không được bắt, **các transformer tiếp theo vẫn được gọi** và JVM vẫn cố tiếp tục nạp/redefine/retransform. Theo quy ước, hiệu ứng gần tương đương return null đối với transformer đó.

Vì vậy nếu muốn agent dừng phép instrumentation cho một class mục tiêu khi gặp lỗi, chỉ ném RuntimeException không đủ để “dừng chuỗi biến đổi”.

Một mẫu an toàn:

~~~java
try {
    return transformerEngine.transform(classfileBuffer);
} catch (Throwable ex) {
    agentLogger.error("Transform failed for " + className, ex);
    return null;
}
~~~

Bắt Throwable không phải quy tắc mặc định cho mã ứng dụng, nhưng tài liệu ClassFileTransformer nêu đây là lựa chọn để tránh lỗi unchecked bất ngờ trong transformer.

IllegalClassFormatException hữu ích khi đầu vào thực sự sai định dạng vì nó giúp công cụ/ghi log nhận biết đây là lỗi định dạng, dù ngữ nghĩa của chuỗi biến đổi vẫn cho phép phần còn lại tiếp tục.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-selection">Chọn class mục tiêu theo class, loader và module</a>

<details>
<summary>Xem chi tiết</summary>

Transformer tốt nên **lọc sớm**. Nếu mọi class đều đi qua bộ phân tích bytecode trước khi biết có phải mục tiêu hay không, overhead lúc khởi động và rủi ro sẽ tăng mạnh.

Ví dụ:

~~~java
if (className == null) {
    return null;
}
if (!className.startsWith("com/example/order/")) {
    return null;
}
if (loader == null) {
    return null; // skip bootstrap classes for this agent
}
if (module != null && module.isNamed()
        && !"com.example.app".equals(module.getName())) {
    return null;
}
~~~

Các bộ lọc thực tế thường gồm:

- danh sách package/class được phép;
- danh sách loại trừ cho class của agent/helper/công cụ;
- identity/loại class loader;
- module name;
- annotation/interface đánh dấu nếu công cụ có metadata trước;
- trạng thái agent đang bật/tắt.

Việc chọn mục tiêu là một phần của tính đúng đắn, không chỉ là tối ưu hiệu năng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transformer-removal">Gỡ transformer và giới hạn của removeTransformer</a>

<details>
<summary>Xem chi tiết</summary>

removeTransformer(transformer) gỡ lượt đăng ký gần nhất khớp đối tượng và trả về boolean cho biết có tìm thấy hay không.

Nhưng hai điều thường bị hiểu sai:

1. class đã được biến đổi **không tự trở lại bytecode cũ**;
2. lời gọi lại vẫn có thể tới transformer sau khi remove do quá trình nạp class đa luồng.

Vì vậy transformer nên kiểm tra trạng thái theo hướng phòng thủ:

~~~java
if (!enabled.get()) {
    return null;
}
~~~

Quy trình dừng thường là:

~~~text
không nhận công việc mới
→ gỡ transformer
→ chờ/điều phối trạng thái agent nếu cần
→ nếu cần, khôi phục các class bị ảnh hưởng bằng retransform/redefine
→ giải phóng tài nguyên helper
~~~

Khôi phục là một thao tác riêng; removeTransformer chỉ dừng đăng ký cho các lần định nghĩa/lời gọi tương lai trong phạm vi bảo đảm của API.

</details>

- [Quay lại đầu trang](#back-to-top)
