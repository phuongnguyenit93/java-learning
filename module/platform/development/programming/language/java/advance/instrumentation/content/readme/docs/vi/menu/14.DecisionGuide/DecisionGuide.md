<a id="back-to-top"></a>

# Hướng dẫn lựa chọn và tổng hợp

## Menu
- [Khi nào nên dùng Instrumentation?](#when-to-use-instrumentation)
- [Khi nào không nên dùng Instrumentation?](#when-not-to-use-instrumentation)
- [Chọn startup agent hay dynamic attach?](#startup-vs-dynamic-agent-choice)
- [Chọn redefine hay retransform?](#redefine-vs-retransform-choice)
- [Instrumentation so với Reflection và Proxy](#instrumentation-vs-reflection-proxy)
- [Instrumentation so với Runtime Diagnostics](#instrumentation-vs-runtime-diagnostics)
- [Khi nào phải chuyển sang JVMTI/native agent?](#jvmti-native-agent-handoff)
- [Mô hình end-to-end của một Java Agent](#end-to-end-agent-flow)

## <a id="when-to-use-instrumentation">Khi nào nên dùng Instrumentation?</a>

<details>
<summary>Xem chi tiết</summary>

Instrumentation phù hợp khi yêu cầu nằm ở **ranh giới định nghĩa class/runtime** thay vì API nghiệp vụ thông thường.

Các dấu hiệu:

- cần chèn probe dùng chung theo chiều ngang vào nhiều class mà không sửa mã nguồn;
- cần instrument code của thư viện phụ thuộc/framework;
- cần áp dụng logic ngay lúc class được nạp;
- cần thay đổi phần hiện thực method của class đã được nạp trong giới hạn JVM;
- cần agent hoạt động xuyên nhiều ứng dụng/module theo chính sách tập trung.

Chuỗi quyết định:

~~~text
sửa mã nguồn/proxy có giải quyết được không?
        ↓ không
có cần can thiệp lúc nạp class hoặc ở mức bytecode không?
        ↓ có
có chấp nhận agent đặc quyền chạy trong cùng tiến trình không?
        ↓ có
có thể kiểm thử tương thích JDK/class loader/module không?
        ↓ có
Instrumentation là một lựa chọn hợp lý
~~~

Đừng chọn chỉ vì “agent mạnh”. Chọn vì vấn đề thật sự nằm ở ranh giới mà Instrumentation sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="when-not-to-use-instrumentation">Khi nào không nên dùng Instrumentation?</a>

<details>
<summary>Xem chi tiết</summary>

Không nên dùng Instrumentation khi có cơ chế đơn giản và rõ hơn.

Ví dụ:

- ứng dụng sở hữu mã nguồn và chỉ cần số liệu đo cho vài method → viết decorator/interceptor rõ ràng có thể dễ hơn;
- lời gọi Spring bean cần AOP → proxy/AOP của framework thường phù hợp hơn;
- chỉ cần đọc annotation/metadata → Reflection;
- cần chẩn đoán GC/thread/heap → JFR/jcmd/Runtime Diagnostics;
- cần thay đổi cấu trúc object/quan hệ kế thừa tùy ý → redefine/retransform không đủ;
- cần sự kiện VM native ở mức sâu → chuyển sang ranh giới JVMTI/native agent.

Phản mẫu:

~~~text
"Không muốn sửa code"
→ dùng bytecode agent cho mọi thứ
~~~

Độ phức tạp ẩn gồm vấn đề ClassLoader, khả năng tương thích JDK, overhead khi khởi động và khó gỡ lỗi. Instrumentation nên là **lựa chọn hạ tầng có chủ đích**, không phải lối tắt mặc định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-vs-dynamic-agent-choice">Chọn startup agent hay dynamic attach?</a>

<details>
<summary>Xem chi tiết</summary>

Chọn startup agent khi nhu cầu đã biết trước triển khai:

- agent observability luôn bật;
- coverage agent trong kiểm thử;
- chính sách bảo mật/runtime bắt buộc;
- muốn thấy class được nạp từ sớm;
- muốn người triển khai opt-in rõ ràng.

Chọn dynamic attach khi:

- điều tra theo nhu cầu;
- công cụ cần attach vào tiến trình đã chạy;
- không thể khởi động lại ngay;
- instrumentation chỉ bật tạm thời.

So sánh:

| | Startup | Dynamic |
| --- | --- | --- |
| chính sách | khai báo rõ lúc JVM khởi động | thay đổi runtime sau khi khởi động |
| class được nạp sớm | thấy từ đầu | có thể phải retransform |
| lỗi | có thể làm quá trình khởi động bị hủy | JVM mục tiêu thường tiếp tục |
| xu hướng JDK | mô hình triển khai ổn định | chính sách ngày càng yêu cầu rõ ràng |

Nếu agent là dependency thường trực trong production, nạp lúc khởi động thường dễ suy luận và kiểm toán hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="redefine-vs-retransform-choice">Chọn redefine hay retransform?</a>

<details>
<summary>Xem chi tiết</summary>

Chọn theo nguồn của bytes mong muốn:

~~~text
có class bytes thay thế cụ thể?
→ redefineClasses

muốn chuỗi transformer tính lại instrumentation?
→ retransformClasses
~~~

Retransform phù hợp với:

- bật/tắt probe;
- cấu hình động;
- agent attach sau khi class đã được nạp;
- áp dụng cùng logic transformer cho class đã tồn tại.

Redefine phù hợp với:

- thay thế kiểu debugger/fix-and-continue;
- công cụ đã biên dịch/sinh đầy đủ định nghĩa thay thế.

Cả hai:

- cần khả năng tương ứng của JVM;
- cần class có thể sửa đổi;
- chịu giới hạn cấu trúc;
- không đặt lại trạng thái object/static;
- stack frame đang hoạt động có thể tiếp tục chạy code cũ.

Quyết định phải dựa trên ngữ nghĩa, không chỉ việc API có tồn tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-vs-reflection-proxy">Instrumentation so với Reflection và Proxy</a>

<details>
<summary>Xem chi tiết</summary>

Ba cơ chế giải quyết ba lớp khác nhau:

| Cơ chế | Điểm can thiệp | Phù hợp |
| --- | --- | --- |
| Reflection | truy cập metadata/member ở runtime | inspect/invoke khi type/member được quyết định động |
| Proxy/AOP | lời gọi đi qua ranh giới wrapper/interceptor | hành vi cross-cutting ở lời gọi interface/bean/framework |
| Instrumentation | định nghĩa class/bytecode | chèn/sửa logic ở mức class bất kể lời gọi có đi qua proxy hay không |

Ví dụ OrderService:

~~~text
đọc @Timed annotation
→ Reflection

chặn lời gọi qua Spring proxy
→ Proxy/AOP

instrument method kể cả object được tạo trực tiếp bằng new
→ Instrumentation
~~~

Các cơ chế có thể kết hợp: agent có thể dùng Reflection để khám phá metadata rồi dùng Instrumentation để thay bytecode.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-vs-runtime-diagnostics">Instrumentation so với Runtime Diagnostics</a>

<details>
<summary>Xem chi tiết</summary>

Hai module trả lời câu hỏi khác nhau:

~~~text
Instrumentation
→ code/probe được chèn vào runtime như thế nào?

Runtime Diagnostics
→ bằng chứng thu được nói gì và tìm nguyên nhân gốc thế nào?
~~~

Nếu câu hỏi là:

- “làm sao thêm timing probe vào class đã được nạp?” → Instrumentation;
- “vì sao service latency cao?” → Runtime Diagnostics;
- “agent có làm latency tăng không?” → cần cả hai: Instrumentation để hiểu overhead của probe, Diagnostics để đo/đánh giá bằng chứng runtime.

Ranh giới này cũng giúp thiết kế công cụ: lớp thu thập dữ liệu/probe nên tách khỏi lớp phân tích/trình bày khi có thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvmti-native-agent-handoff">Khi nào phải chuyển sang JVMTI/native agent?</a>

<details>
<summary>Xem chi tiết</summary>

Java Instrumentation không phơi bày mọi khả năng công cụ của JVM.

Khi yêu cầu cần:

- event VM mức thấp không có trong java.lang.instrument;
- hook native method/runtime chuyên sâu;
- event heap/thread/GC ở tầng JVMTI;
- native agent chạy trước/ngoài mô hình Java agent;
- quyền điều khiển vượt khỏi quy ước redefine/retransform;

thì cần nghiên cứu JVMTI/native agent.

Mô hình tư duy khi chuyển giao:

~~~text
Java Instrumentation
→ Java agent mức cao + biến đổi class file

JVMTI
→ giao diện công cụ native của VM với tập event/khả năng rộng hơn
~~~

Đừng “lách” các ràng buộc của Java Instrumentation bằng thủ thuật bytecode không được hỗ trợ. Nếu quy ước API không đủ, hãy chuyển sang đúng lớp công cụ mức thấp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="end-to-end-agent-flow">Mô hình end-to-end của một Java Agent</a>

<details>
<summary>Xem chi tiết</summary>

Toàn module có thể ghép thành một luồng:

~~~text
1. đóng gói agent JAR
        ↓
2. chọn đường khởi động
   -javaagent / Launcher-Agent-Class / dynamic attach
        ↓
3. JVM gọi premain hoặc agentmain
        ↓
4. nhận Instrumentation
        ↓
5. kiểm tra khả năng + cài transformer
        ↓
6. class mục tiêu được nạp/redefine/retransform
        ↓
7. transformer nhận class-file bytes
        ↓
8. công cụ bytecode tạo bytes đã biến đổi hợp lệ
        ↓
9. JVM verify + link + cài định nghĩa
        ↓
10. probe/helper runtime chạy
        ↓
11. vận hành an toàn: overhead, khả năng nhìn thấy qua loader/module, chính sách
        ↓
12. remove/rollback/retransform khi cần
~~~

Nếu người học có thể giải thích từng mũi tên, biết lỗi nào xảy ra ở đâu và biết ranh giới nào phải chuyển giao, thì đã có mô hình tư duy đủ chắc để bước sang API experiment, Quiz và Interview sau này.

</details>

- [Quay lại đầu trang](#back-to-top)
