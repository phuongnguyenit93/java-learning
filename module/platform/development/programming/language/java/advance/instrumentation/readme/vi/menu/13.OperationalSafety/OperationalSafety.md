<a id="back-to-top"></a>

# An toàn vận hành và lỗi thường gặp

## Menu
- [Ranh giới tin cậy và rủi ro bảo mật của agent](#agent-trust-security)
- [Chi phí khởi động và overhead khi chạy](#startup-runtime-overhead)
- [ClassLoader, khả năng nhìn thấy module và helper class](#classloader-module-visibility)
- [Chính sách dynamic attach và quyền kiểm soát triển khai](#dynamic-attach-policy)
- [removeTransformer và race với class loading](#remove-transformer-race)
- [Rollback, redefine và retransform có điều kiện](#rollback-strategy)
- [Kiểm thử tương thích qua JDK và phiên bản class file](#compatibility-testing)

## <a id="agent-trust-security">Ranh giới tin cậy và rủi ro bảo mật của agent</a>

<details>
<summary>Xem chi tiết</summary>

Java Agent là mã đặc quyền trong tiến trình. Nó có thể thấy các class runtime, thay bytecode và chạy trong cùng miền bảo mật/lỗi với ứng dụng.

Vì vậy cần kiểm soát:

- artifact agent đến từ đâu;
- checksum/chữ ký/cố định phiên bản;
- ai được phép thêm -javaagent hoặc attach vào tiến trình;
- cấu hình/bí mật agent có thể đọc;
- telemetry agent gửi ra ngoài;
- dấu vết kiểm toán khi agent được bật/dynamic attach.

Đặc tả package java.lang.instrument đặt trách nhiệm xác minh độ tin cậy của agent lên lập trình viên hoặc quản trị viên triển khai agent.

Mô hình đe dọa thực tế:

~~~text
agent độc hại hoặc bị xâm phạm
→ quan sát/chỉnh sửa tùy ý trong cùng tiến trình
→ tác động tương đương một thành phần có đặc quyền cao
~~~

Đừng rà soát agent như một thư viện ghi log vô hại chỉ vì trường hợp sử dụng ban đầu là monitoring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-runtime-overhead">Chi phí khởi động và overhead khi chạy</a>

<details>
<summary>Xem chi tiết</summary>

Overhead xuất hiện ở hai giai đoạn:

**Khởi động / nạp class**

- matcher/bộ lọc cho mỗi class;
- phân tích/biến đổi/sinh bytecode;
- verification/JIT warm-up có thể thay đổi;
- chuỗi nhiều agent làm tăng chi phí.

**Runtime**

- instruction của probe;
- lời gọi helper;
- cấp phát;
- timestamp/counter/locking;
- đệm/xuất telemetry.

Một agent tốt đo overhead của chính mình:

~~~text
ứng dụng cơ sở
vs
agent đã nạp nhưng chưa có mục tiêu
vs
agent có instrumentation đại diện
~~~

Lọc mục tiêu sớm, tránh công việc nặng trong callback transform và dùng batching/sampling ở runtime giúp giảm chi phí.

Agent observability làm ứng dụng chậm đáng kể có thể làm sai chính dữ liệu nó đo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classloader-module-visibility">ClassLoader, khả năng nhìn thấy module và helper class</a>

<details>
<summary>Xem chi tiết</summary>

ClassLoader/khả năng nhìn thấy qua module là nguồn lỗi rất phổ biến trong môi trường production.

Ví dụ class mục tiêu:

~~~text
class plugin
được định nghĩa bởi PluginClassLoader
trong named module plugin.foo
~~~

bytecode đã biến đổi gọi:

~~~text
AgentRuntime.record()
~~~

Nếu AgentRuntime chỉ nhìn thấy từ system loader, class do PluginClassLoader định nghĩa có thể không phân giải được helper.

Giải pháp tùy kiến trúc:

- bootstrap helper tối thiểu;
- chèn helper vào loader của class mục tiêu;
- thêm JAR vào đường tìm kiếm của loader phù hợp;
- dùng redefineModule để thêm read/open/export cần thiết.

Không nên “sửa” bằng cách thêm toàn bộ agent JAR vào đường tìm kiếm bootstrap. Tài liệu API cảnh báo JAR trên đường tìm kiếm bootstrap chỉ nên chứa class/tài nguyên cần được bootstrap loader định nghĩa; xung đột package có thể gây IllegalAccessError và lỗi khó chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-attach-policy">Chính sách dynamic attach và quyền kiểm soát triển khai</a>

<details>
<summary>Xem chi tiết</summary>

Dynamic attach thay đổi một JVM **sau khi triển khai đã khởi động**, nên chính sách phải rõ hơn startup agent.

JDK 21 phát cảnh báo cho dynamic agent loading theo hướng của JEP 451. Điều này phản ánh nguyên tắc bảo toàn tính toàn vẹn:

~~~text
startup -javaagent
→ người triển khai đã opt-in trước khi ứng dụng chạy

dynamic attach
→ thay đổi runtime theo nhu cầu
→ cần quyền cho phép rõ ràng
~~~

Chính sách production nên xác định:

- ai/công cụ nào được attach;
- môi trường nào cho phép;
- cảnh báo/kiểm toán khi attach;
- flag JVM được cấu hình ra sao;
- phương án dự phòng khi JDK tương lai siết mặc định.

Không xây tự động hóa phụ thuộc vào việc dynamic attach “chắc chắn luôn bật” ở mọi JDK tương lai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="remove-transformer-race">removeTransformer và race với class loading</a>

<details>
<summary>Xem chi tiết</summary>

removeTransformer có race được API ghi rõ: do quá trình nạp class đa luồng, transformer **có thể vẫn nhận callback sau khi đã được gỡ**.

Do đó:

~~~java
enabled.set(false);
inst.removeTransformer(transformer);
~~~

transformer vẫn cần kiểm tra trạng thái:

~~~java
if (!enabled.get()) {
    return null;
}
~~~

Nếu giải phóng tài nguyên ngay sau remove nhưng callback trễ vẫn dùng tài nguyên đó, agent có thể xảy ra race.

Quy trình dừng an toàn cần nghĩ tới:

- trạng thái enabled dạng atomic;
- bộ đếm callback đang chạy nếu cần;
- vòng đời tài nguyên;
- callback không chặn vô thời hạn;
- dọn dẹp theo kiểu idempotent.

Đây là ví dụ rõ rằng code Instrumentation chạy trên đường thực thi runtime đồng thời, không phải một vòng đời tuần tự đơn giản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rollback-strategy">Rollback, redefine và retransform có điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

Rollback không đồng nghĩa removeTransformer.

Muốn bỏ phần instrumentation khỏi class đã biến đổi, agent cần một chiến lược:

~~~text
vô hiệu hóa hành vi transformer
→ gỡ đăng ký nếu phù hợp
→ xác định các class bị ảnh hưởng
→ retransform/redefine về dạng không còn instrumentation mong muốn
→ xác minh kết quả
~~~

Với transformer hỗ trợ retransformation, một chiến lược phổ biến là đổi cấu hình thành “không chèn instrumentation” rồi retransform class mục tiêu. Nhưng phải hiểu các phép biến đổi từ transformer không hỗ trợ retransformation trước đó vẫn được JVM tự áp dụng lại.

Với redefine, agent có thể giữ/khôi phục bộ bytes mốc cơ sở đã biết, nhưng việc quản lý mốc cơ sở phức tạp khi có nhiều agent hoặc class đã redefine trước đó.

Rollback phải được kiểm thử như một tính năng riêng:

- stack frame đang hoạt động chuyển tiếp thế nào;
- tính nguyên tử của từng lời gọi theo nhóm: nếu một lời gọi redefineClasses/retransformClasses ném exception thì không class nào trong chính tập truyền vào lời gọi đó được thay đổi;
- tiến độ một phần ở quy trình nhiều nhóm/lời gọi: các nhóm trước có thể đã thành công trước khi nhóm sau thất bại;
- helper còn có thể được nhìn thấy sau rollback;
- dọn trạng thái telemetry;
- nhiều agent cùng sửa class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compatibility-testing">Kiểm thử tương thích qua JDK và phiên bản class file</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm thử tương thích cần nhiều chiều:

~~~text
JDK runtime
× phiên bản ứng dụng/framework
× phiên bản class file
× phiên bản công cụ bytecode
× cấu hình agent
× khả năng cùng tồn tại với agent khác
~~~

Kiểm thử tối thiểu nên có:

- startup agent;
- dynamic attach nếu hỗ trợ;
- class mục tiêu được nạp trước/sau agent;
- retransform nhiều lần;
- bật/tắt/rollback;
- nhiều class loader;
- named module;
- class không được hỗ trợ hoặc không thể sửa đổi;
- đường thoát bằng exception trong method đã biến đổi;
- thứ tự nhiều agent.

Agent production nên công bố thông tin tự chẩn đoán:

- phiên bản agent;
- các instrumentation đang bật;
- số lần transform thành công/thất bại;
- số class không được hỗ trợ đã bỏ qua;
- phiên bản JDK/công cụ hiện tại.

Agent càng “vô hình” với ứng dụng thì khả năng tự chẩn đoán càng quan trọng khi có lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
