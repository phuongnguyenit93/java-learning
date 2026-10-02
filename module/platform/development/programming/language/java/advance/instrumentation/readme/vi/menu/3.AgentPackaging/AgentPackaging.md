<a id="back-to-top"></a>

# Đóng gói Agent

## Menu
- [Agent JAR và cấu trúc đóng gói](#agent-jar)
- [Manifest của Java Agent](#agent-manifest)
- [Premain-Class, Agent-Class và Launcher-Agent-Class](#agent-entry-points)
- [Tham số truyền vào agent](#agent-arguments)
- [Khai báo khả năng redefine, retransform và native-method prefix](#agent-capability-declarations)
- [Agent được đóng gói cùng executable JAR](#executable-jar-agent)

## <a id="agent-jar">Agent JAR và cấu trúc đóng gói</a>

<details>
<summary>Xem chi tiết</summary>

Một Java agent thường được đóng gói thành JAR riêng. JAR chứa:

- agent class có premain/agentmain;
- transformer và helper classes;
- manifest khai báo cách JVM tìm agent class;
- các dependency cần thiết, tùy chiến lược đóng gói.

Ví dụ cấu trúc:

~~~text
timing-agent.jar
├── META-INF/MANIFEST.MF
└── com/example/agent/
    ├── TimingAgent.class
    └── TimingTransformer.class
~~~

Agent JAR không tự động “được ứng dụng import”. JVM đọc manifest và nạp agent class theo vòng đời của java.lang.instrument. Vì agent nằm trong cùng tiến trình với ứng dụng, cách đóng gói cần đặc biệt chú ý xung đột phụ thuộc và khả năng nhìn thấy qua class loader.

Theo quy ước của package, các class trong agent JAR được system class loader nạp và thuộc unnamed module của loader đó. Nếu agent đặt helper qua Boot-Class-Path hoặc appendToBootstrapClassLoaderSearch(), helper lại chịu khả năng nhìn thấy của bootstrap class loader. Sự khác biệt này giải thích nhiều lỗi kiểu “agent class nhìn thấy helper nhưng class đã biến đổi không phân giải được helper”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-manifest">Manifest của Java Agent</a>

<details>
<summary>Xem chi tiết</summary>

Manifest là nơi nối JAR với vòng đời agent. Các attribute chính:

~~~text
Premain-Class
Agent-Class
Launcher-Agent-Class
Can-Redefine-Classes
Can-Retransform-Classes
Can-Set-Native-Method-Prefix
Boot-Class-Path
~~~

Không phải attribute nào cũng cần cho mọi agent. Ví dụ startup agent tối thiểu dùng -javaagent thường chỉ cần Premain-Class. Nếu agent gọi retransformClasses, manifest phải yêu cầu Can-Retransform-Classes: true và JVM cũng phải hỗ trợ khả năng đó.

Manifest là **khai báo nhu cầu**, không phải đảm bảo tuyệt đối. Code runtime vẫn phải kiểm tra khả năng bằng Instrumentation trước khi dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-entry-points">Premain-Class, Agent-Class và Launcher-Agent-Class</a>

<details>
<summary>Xem chi tiết</summary>

Ba attribute chọn agent class cho ba đường khởi động:

- **Premain-Class**: dùng với -javaagent, JVM gọi premain trước main của ứng dụng.
- **Agent-Class**: dùng khi agent được nạp vào JVM đang chạy, JVM gọi agentmain.
- **Launcher-Agent-Class**: agent đóng gói trong executable JAR, JVM gọi agentmain trước main của ứng dụng.

Một agent JAR có thể chứa cả Premain-Class và Agent-Class để hỗ trợ cả khởi động cùng JVM lẫn nạp động.

Ví dụ:

~~~text
Manifest-Version: 1.0
Premain-Class: com.example.agent.TimingAgent
Agent-Class: com.example.agent.TimingAgent
Can-Retransform-Classes: true
~~~

Điểm dễ nhầm: Launcher-Agent-Class cũng dùng agentmain, nhưng không phải dynamic attach. Nó chạy trong luồng khởi động của executable JAR.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-arguments">Tham số truyền vào agent</a>

<details>
<summary>Xem chi tiết</summary>

Với -javaagent, phần sau dấu bằng được chuyển cho premain dưới dạng **một String duy nhất**:

~~~text
java -javaagent:timing-agent.jar=include=com.example,debug=true app.jar
~~~

Agent tự quyết định định dạng và cách phân tích:

~~~java
public static void premain(String agentArgs, Instrumentation inst) {
    AgentConfig config = AgentConfig.parse(agentArgs);
}
~~~

Không nên coi agentArgs là một định dạng cấu hình chuẩn của Java. Nó chỉ là chuỗi truyền tải. Trong môi trường production, bộ phân tích nên:

- kiểm tra hợp lệ key/value rõ ràng;
- có mặc định an toàn;
- không ghi log bí mật;
- báo lỗi với thông báo hữu ích khi cấu hình sai;
- tránh phụ thuộc vào thư mục làm việc nếu nhận đường dẫn.

Với Launcher-Agent-Class, đặc tả package quy định agentArgs là chuỗi rỗng. Đây là khác biệt cần nhớ khi tái sử dụng cùng một agentmain.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="agent-capability-declarations">Khai báo khả năng redefine, retransform và native-method prefix</a>

<details>
<summary>Xem chi tiết</summary>

Ba khả năng manifest không “bật một API toàn cục” theo nghĩa tuyệt đối. Chúng khai báo agent **cần** khả năng:

~~~text
Can-Redefine-Classes
Can-Retransform-Classes
Can-Set-Native-Method-Prefix
~~~

Sau đó JVM quyết định khả năng thực tế. Vì vậy mẫu an toàn là:

~~~java
if (inst.isRetransformClassesSupported()) {
    inst.addTransformer(transformer, true);
}
~~~

Nếu đăng ký transformer với canRetransform=true khi JVM không hỗ trợ retransformation, addTransformer có thể ném UnsupportedOperationException.

Quy tắc quan trọng:

~~~text
khai báo trong manifest
        +
JVM hỗ trợ
        ↓
khả năng runtime
~~~

Không mã hóa cứng giả định “HotSpot hiện tại hỗ trợ nên mọi JVM đều hỗ trợ”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-jar-agent">Agent được đóng gói cùng executable JAR</a>

<details>
<summary>Xem chi tiết</summary>

Executable JAR có thể vừa chứa class main của ứng dụng vừa chứa Java agent. Launcher-Agent-Class cho JVM biết agent nào cần được gọi **trước main của ứng dụng**.

Ví dụ manifest:

~~~text
Main-Class: com.example.app.Main
Launcher-Agent-Class: com.example.agent.BootAgent
~~~

JVM gọi:

~~~java
public static void agentmain(String agentArgs, Instrumentation inst)
~~~

trước khi gọi main của ứng dụng. Với đường khởi động này, agentArgs là chuỗi rỗng.

Trường hợp sử dụng phù hợp là ứng dụng muốn tự đóng gói agent bootstrap mà không yêu cầu người vận hành thêm -javaagent. Tuy nhiên mức gắn kết triển khai tăng lên: artifact ứng dụng giờ mang luôn một thành phần runtime có đặc quyền. Cần rà soát độ tin cậy, quản lý phiên bản và cách xử lý lỗi như một phần của đường khởi động.

</details>

- [Quay lại đầu trang](#back-to-top)
