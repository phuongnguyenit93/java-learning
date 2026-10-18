<a id="back-to-top"></a>

# Java Agent

## Menu
- [Mô hình Java Agent](#java-agent-model)
- [Agent class và thỏa thuận với JVM](#agent-class-contract)
- [JVM trao đối tượng Instrumentation cho agent như thế nào?](#instrumentation-service-handoff)
- [Các hình thức khởi động agent](#agent-startup-shapes)
- [Agent là mã tin cậy và ranh giới trách nhiệm](#agent-trust-boundary)

## <a id="java-agent-model">Mô hình Java Agent</a>

<details>
<summary>Xem chi tiết</summary>

Java Agent là một thành phần Java được JVM khởi động theo quy ước đặc biệt để thực hiện công việc trước hoặc trong khi ứng dụng đang chạy. Điểm khác với thư viện thông thường là ứng dụng không nhất thiết phải gọi agent. Chính JVM gọi điểm vào của agent và có thể truyền vào một đối tượng Instrumentation.

Mô hình tư duy:

~~~text
agent JAR
  ↓ manifest chọn agent class
agent class
  ↓ premain hoặc agentmain
Instrumentation instance
  ↓
đăng ký transformer / xem class đã nạp / yêu cầu retransform-redefine
~~~

Agent thường sống cùng tiến trình với ứng dụng. Vì vậy lỗi, deadlock, rò rỉ bộ nhớ hoặc xung đột phụ thuộc trong agent có thể ảnh hưởng trực tiếp tới ứng dụng. Ta nên xem agent như hạ tầng runtime chạy **trong cùng tiến trình (in-process)**, không phải một dịch vụ độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-class-contract">Agent class và thỏa thuận với JVM</a>

<details>
<summary>Xem chi tiết</summary>

Agent class không bắt buộc phải hiện thực một interface. JVM tìm các static method có signature do đặc tả quy định:

~~~java
public static void premain(String agentArgs, Instrumentation inst)

public static void agentmain(String agentArgs, Instrumentation inst)
~~~

Các overload chỉ nhận String cũng hợp lệ, nhưng nếu overload có Instrumentation tồn tại thì JVM ưu tiên nó. Điều này cho phép agent nhận đối tượng đại diện khả năng ngay lúc khởi động.

Các quy ước quan trọng:

- method phải là public static;
- tên method phụ thuộc đường khởi động;
- agent nên khởi tạo nhanh rồi trả quyền điều khiển;
- công việc chạy dài nên được quản lý rõ thay vì chặn premain/agentmain;
- cách xử lý exception khác nhau giữa agent khởi động cùng JVM và agent được nạp vào JVM đang chạy.

Không có một “vòng đời đối tượng Agent” do JVM quản lý như Spring bean. Vòng đời thực tế được quyết định bởi điểm vào static và trạng thái mà agent tự tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-service-handoff">JVM trao đối tượng Instrumentation cho agent như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Đối tượng Instrumentation là đại diện khả năng do JVM cung cấp. Agent không tự tạo bằng new hoặc tự hiện thực Instrumentation; Java API ghi rõ interface này không dành cho hiện thực bên ngoài module java.instrument.

Một mẫu tối thiểu:

~~~java
public final class TimingAgent {
    private static volatile Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        inst.addTransformer(new TimingTransformer());
    }
}
~~~

Sau khi nhận đối tượng này, agent có thể giữ tham chiếu và dùng về sau để:

- thêm/gỡ transformer;
- xem các class đã được nạp;
- kiểm tra khả năng;
- yêu cầu retransform/redefine;
- thêm JAR vào đường tìm kiếm của bootstrap/system class loader;
- mở rộng quan hệ module qua redefineModule.

Đây là điểm nối giữa “agent được JVM khởi động” và “agent có quyền thao tác lên runtime”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-startup-shapes">Các hình thức khởi động agent</a>

<details>
<summary>Xem chi tiết</summary>

Có ba hình thức cần phân biệt:

| Hình thức | Manifest | Entry point | Thời điểm |
| --- | --- | --- | --- |
| command-line agent | Premain-Class | premain | trước main của ứng dụng |
| executable-JAR agent | Launcher-Agent-Class | agentmain | trước main của ứng dụng |
| dynamic agent | Agent-Class | agentmain | sau khi JVM đã chạy |

Hai hình thức đầu là **startup-time**: triển khai chủ động khai báo agent trước khi ứng dụng chạy. Dynamic agent cần JVM/hiện thực hỗ trợ cơ chế attach/nạp động và chịu chính sách ngày càng chặt của JDK.

Việc cả Launcher-Agent-Class và dynamic agent đều gọi agentmain không có nghĩa chúng giống nhau về cách xử lý lỗi hoặc agentArgs. Luôn suy luận từ **đường khởi động**, không chỉ từ tên method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-trust-boundary">Agent là mã tin cậy và ranh giới trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

Agent có quyền chạy code trong cùng JVM và có thể biến đổi bytecode của class ứng dụng. Vì vậy đặc tả package Java đặt trách nhiệm xác minh độ tin cậy lên người triển khai agent hoặc công cụ.

Các rủi ro điển hình:

- agent JAR bị thay thế hoặc tải từ nguồn không đáng tin;
- transformer sửa nhầm class JDK/framework;
- helper class bị nạp bởi loader không mong muốn;
- agent đọc dữ liệu nhạy cảm trong tiến trình;
- bytecode không hợp lệ làm ứng dụng lỗi;
- dynamic attach tạo một kênh thay đổi runtime ngoài kế hoạch triển khai.

Mô hình bảo mật nên là: **agent có quyền rất cao trong tiến trình**. Do đó nguồn gốc artifact, cố định phiên bản, kiểm soát quyền attach, ghi log và kế hoạch rollback quan trọng không kém code của transformer.

</details>

- [Quay lại đầu trang](#back-to-top)
