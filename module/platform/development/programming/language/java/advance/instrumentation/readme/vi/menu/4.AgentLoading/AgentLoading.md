<a id="back-to-top"></a>

# Nạp và khởi động Agent

## Menu
- [Khởi động agent bằng -javaagent và premain](#javaagent-premain-flow)
- [Khởi động Launcher-Agent-Class trước main của ứng dụng](#launcher-agent-flow)
- [Nạp agent vào JVM đang chạy và agentmain](#runtime-agentmain-flow)
- [Attach API và luồng nạp agent động](#attach-api-flow)
- [Khác biệt cách xử lý lỗi giữa các đường khởi động](#launch-failure-semantics)
- [Chính sách dynamic agent loading trên JDK 21](#jdk21-dynamic-agent-loading)

## <a id="javaagent-premain-flow">Khởi động agent bằng -javaagent và premain</a>

<details>
<summary>Xem chi tiết</summary>

Với agent khởi động cùng JVM được truyền qua dòng lệnh:

~~~text
java -javaagent:timing-agent.jar=config=prod app.Main
~~~

JVM đọc Premain-Class từ manifest, nạp agent class và gọi premain **trước main của ứng dụng**.

~~~java
public static void premain(String agentArgs, Instrumentation inst) {
    inst.addTransformer(new TimingTransformer());
}
~~~

Nếu có nhiều option -javaagent, premain được gọi theo thứ tự xuất hiện trên dòng lệnh. Điều này có thể ảnh hưởng thứ tự đăng ký transformer và dependency giữa các agent.

Ưu điểm của startup agent là triển khai có tính xác định: agent có mặt từ đầu và có thể thấy những class ứng dụng được nạp sau đó. Nhược điểm là cấu hình triển khai phải thay đổi và lỗi khởi động agent có thể làm JVM dừng trước khi main của ứng dụng chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="launcher-agent-flow">Khởi động Launcher-Agent-Class trước main của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

Launcher-Agent-Class phục vụ executable JAR:

~~~text
java -jar application.jar
~~~

Nếu manifest của JAR có:

~~~text
Main-Class: com.example.Main
Launcher-Agent-Class: com.example.agent.BootAgent
~~~

JVM nạp BootAgent và gọi agentmain trước Main.main().

Điểm khác biệt với -javaagent:

- dùng agentmain thay vì premain;
- agentArgs luôn là chuỗi rỗng;
- agent nằm cùng executable JAR;
- lỗi khi agent không khởi động được có thể làm JVM dừng trước main của ứng dụng.

Đây vẫn là **startup agent** dù method tên agentmain. Vì vậy không nên dùng “premain = startup, agentmain = dynamic” như một quy tắc tuyệt đối.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-agentmain-flow">Nạp agent vào JVM đang chạy và agentmain</a>

<details>
<summary>Xem chi tiết</summary>

Nạp động xảy ra sau khi JVM/ứng dụng đã chạy. Agent JAR phải có Agent-Class và agent class phải khai báo agentmain:

~~~java
public static void agentmain(String agentArgs, Instrumentation inst) {
    if (!inst.isRetransformClassesSupported()) {
        throw new IllegalStateException("Retransformation is required");
    }
    inst.addTransformer(new TimingTransformer(), true);
}
~~~

Khác startup agent, dynamic agent đến **sau** khi nhiều class đã được nạp. Nếu muốn áp dụng phép biến đổi cho chúng, agent thường phải:

1. đăng ký transformer có canRetransform phù hợp;
2. tìm class mục tiêu trong getAllLoadedClasses();
3. kiểm tra isModifiableClass();
4. gọi retransformClasses() hoặc dùng redefineClasses() theo trường hợp sử dụng.

Nạp động hữu ích cho công cụ chạy theo nhu cầu, nhưng làm tăng yêu cầu về kiểm tra khả năng, rollback và chính sách kiểm soát attach.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="attach-api-flow">Attach API và luồng nạp agent động</a>

<details>
<summary>Xem chi tiết</summary>

Module jdk.attach cung cấp API để công cụ kết nối tới JVM khác và yêu cầu thao tác như nạp agent. Mô hình tư duy:

~~~text
tiến trình công cụ chẩn đoán
        ↓
VirtualMachine.attach(target)
        ↓
yêu cầu nạp agent
        ↓
JVM mục tiêu nạp agent JAR
        ↓
Agent-Class.agentmain(...)
~~~

Attach API là **ranh giới công cụ**, không phải một phần của package java.lang.instrument. java.lang.instrument định nghĩa vòng đời agent; jdk.attach là một cơ chế JDK có thể được dùng để khởi động vòng đời đó trong JVM đang chạy.

Attach có thể bị giới hạn bởi quyền của hệ điều hành/người dùng, option JVM, môi trường container/tiến trình hoặc chính sách của hiện thực JVM. Vì vậy code không nên giả định “biết PID là attach được”.

Khi xây công cụ cho môi trường production, hãy tách hai miền lỗi: lỗi attach ở tiến trình công cụ và lỗi khởi tạo agent bên JVM mục tiêu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="launch-failure-semantics">Khác biệt cách xử lý lỗi giữa các đường khởi động</a>

<details>
<summary>Xem chi tiết</summary>

Ngữ nghĩa khi xảy ra lỗi khác nhau theo đường khởi động:

| Đường khởi động | Lỗi khởi tạo agent |
| --- | --- |
| -javaagent / premain | JVM dừng trước main của ứng dụng |
| Launcher-Agent-Class | JVM dừng trước main của ứng dụng |
| JVM đang chạy / agentmain | JVM mục tiêu không bị dừng; lỗi có thể bị bỏ qua/ghi log |

Điều này ảnh hưởng cách thiết kế:

- startup agent có thể dừng sớm khi lỗi nếu instrumentation bắt buộc;
- dynamic agent phải báo trạng thái về công cụ/người vận hành vì ứng dụng mục tiêu vẫn tiếp tục;
- quá trình khởi tạo nên idempotent hoặc có guard nếu công cụ có thể attach nhiều lần;
- agent cần dọn phần trạng thái đã tạo nếu khởi tạo thất bại giữa chừng.

Đừng dùng cùng một chiến lược xử lý lỗi cho cả premain và agentmain mà không xét đường khởi động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk21-dynamic-agent-loading">Chính sách dynamic agent loading trên JDK 21</a>

<details>
<summary>Xem chi tiết</summary>

JDK 21 triển khai hướng của JEP 451: khi agent được nạp động vào JVM đang chạy, JVM phát cảnh báo để chuẩn bị cho tương lai khi nạp động có thể bị tắt mặc định.

Trên HotSpot JDK 21:

- nạp agent động vẫn được phép mặc định;
- JVM phát cảnh báo khi agent được nạp động;
- -XX:+EnableDynamicAgentLoading thể hiện opt-in rõ ràng và loại bỏ cảnh báo;
- -XX:-EnableDynamicAgentLoading có thể tắt việc nạp động.

Thông điệp thiết kế quan trọng không phải thuộc lòng flag, mà là:

~~~text
startup instrumentation
→ người sở hữu triển khai cho phép rõ ngay từ lúc JVM khởi động

dynamic instrumentation
→ thay đổi runtime sau startup
→ phải chịu chính sách/quyền cho phép chặt hơn
~~~

Agent/công cụ nên ưu tiên nạp lúc khởi động khi biết trước nhu cầu instrumentation; dynamic attach phù hợp với chẩn đoán theo nhu cầu nhưng không nên được coi là quyền mặc định vĩnh viễn.

</details>

- [Quay lại đầu trang](#back-to-top)
