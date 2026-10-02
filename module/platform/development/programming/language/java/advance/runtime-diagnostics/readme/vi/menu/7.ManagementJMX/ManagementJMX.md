<a id="back-to-top"></a>

# Theo dõi và quản lý runtime với MXBeans và JMX

## Menu
- [Java management API tồn tại để giải quyết vấn đề gì?](#java-management-purpose)
- [ManagementFactory và platform MXBeans](#management-factory-and-platform-mxbeans)
- [Thông tin thread, memory, GC, class loading, compilation, runtime và OS](#platform-management-surfaces)
- [Mô hình MBean, MXBean và ObjectName](#mbean-and-object-name-model)
- [Platform MBeanServer và cách truy cập quản lý](#platform-mbean-server)
- [JMX local và remote](#local-vs-remote-jmx)
- [JConsole và các client quản lý](#jconsole-and-management-clients)
- [Ranh giới bảo mật của remote JMX](#remote-jmx-security)

## <a id="java-management-purpose">Java management API tồn tại để giải quyết vấn đề gì?</a>

<details>
<summary>Click for details</summary>

Không phải mọi câu hỏi runtime đều cần quyền truy cập shell hoặc dump file. Java cung cấp một **management API** chuẩn để ứng dụng hoặc client quản lý đọc trạng thái JVM qua các interface ổn định.

Mục tiêu của lớp này là:

- cung cấp trạng thái runtime dưới dạng managed object;
- cho phép đọc metric/trạng thái ngay trong tiến trình hoặc từ xa;
- hỗ trợ một số thao tác quản lý;
- tách client quản lý khỏi class triển khai cụ thể của JVM.

Mental model:

~~~text
JVM/runtime component
        ↓
platform MXBean / MBean
        ↓
MBeanServer / proxy / direct access
        ↓
mã ứng dụng, JConsole, JMC hoặc client quản lý
~~~

Trong Runtime Diagnostics, JMX/MXBeans là **nguồn bằng chứng runtime có cấu trúc**. Chúng không thay thế thread dump, JFR hay heap dump; chúng cung cấp một kênh quan sát liên tục và có thể truy cập bằng mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="management-factory-and-platform-mxbeans">ManagementFactory và platform MXBeans</a>

<details>
<summary>Click for details</summary>

Lớp `java.lang.management.ManagementFactory` là điểm vào chính để lấy **platform MXBeans** của JVM.

Ví dụ:

~~~java
MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
ThreadMXBean threads = ManagementFactory.getThreadMXBean();
RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
~~~

Platform MXBean đại diện cho một phần runtime, ví dụ:

- ClassLoadingMXBean;
- CompilationMXBean;
- MemoryMXBean;
- MemoryPoolMXBean;
- GarbageCollectorMXBean;
- ThreadMXBean;
- RuntimeMXBean;
- OperatingSystemMXBean.

Một số loại có đúng một instance, một số trả về list vì JVM có nhiều pool/collector.

Điểm quan trọng: API này cho phép code trong chính JVM đọc trạng thái mà không cần parse command output.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-management-surfaces">Thông tin thread, memory, GC, class loading, compilation, runtime và OS</a>

<details>
<summary>Click for details</summary>

Các giao diện quản lý của platform trả lời những câu hỏi khác nhau:

| Giao diện | Ví dụ câu hỏi |
| --- | --- |
| ThreadMXBean | số platform thread, deadlock, thread info |
| MemoryMXBean | heap/non-heap usage tổng quát |
| MemoryPoolMXBean | usage theo memory pool |
| GarbageCollectorMXBean | collection count/time |
| ClassLoadingMXBean | số class loaded/unloaded |
| CompilationMXBean | compilation time nếu VM hỗ trợ |
| RuntimeMXBean | uptime, định danh VM, input args |
| OperatingSystemMXBean | thông tin OS chuẩn; JDK extension bổ sung số liệu CPU/bộ nhớ của tiến trình và hệ thống |

Ví dụ:

~~~java
MemoryUsage heap = ManagementFactory
        .getMemoryMXBean()
        .getHeapMemoryUsage();

System.out.println(heap.getUsed());
~~~

Có hai interface cùng tên cần phân biệt:

- `java.lang.management.OperatingSystemMXBean` là API chuẩn, cung cấp tên/phiên bản/kiến trúc OS, số processor khả dụng và system load average;
- `com.sun.management.OperatingSystemMXBean` là JDK extension, bổ sung các số liệu như CPU time/load của tiến trình, committed virtual memory và physical/swap memory.

`ManagementFactory.getOperatingSystemMXBean()` trả về kiểu chuẩn ở compile time; trên JDK triển khai extension, object thực tế có thể implement `com.sun.management.OperatingSystemMXBean`. Chỉ dùng các method extension sau khi kiểm tra/cast phù hợp, và không trình bày chúng như API chuẩn của Java SE.
Đây là giao diện theo dõi, không phải công cụ tự tìm nguyên nhân gốc. Heap used tăng chỉ là tín hiệu; vẫn cần bằng chứng GC/heap/JFR để giải thích.

Trong Java 21, `ThreadMXBean` quản lý **platform thread**, không phải virtual thread. Đây là ranh giới quan trọng khi dùng management API cho ứng dụng dùng nhiều virtual thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mbean-and-object-name-model">Mô hình MBean, MXBean và ObjectName</a>

<details>
<summary>Click for details</summary>

**MBean** là object cung cấp một interface quản lý qua JMX. **MXBean** là một dạng MBean có quy tắc ánh xạ type sang các “open types” chuẩn, giúp client từ xa không cần các class riêng của ứng dụng.

Mỗi managed bean đăng ký trong MBeanServer được nhận diện bằng **ObjectName**, ví dụ conceptual:

~~~text
java.lang:type=Memory
java.lang:type=Threading
java.lang:type=GarbageCollector,name=<collector>
~~~

`ObjectName` có domain + key properties. Nó là định danh quản lý, không phải Java object reference.

Tại sao điều này quan trọng cho diagnostics?

- client có thể query bean theo tên/pattern;
- attribute/thao tác quản lý được cung cấp có cấu trúc;
- client local và remote dùng cùng mô hình quản lý;
- platform MXBeans đã có ObjectName chuẩn.

Không cần học toàn bộ mô hình instrumentation của JMX để dùng platform MXBeans, nhưng hiểu MBean/ObjectName giúp đọc JConsole và remote JMX đúng cách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="platform-mbean-server">Platform MBeanServer và cách truy cập quản lý</a>

<details>
<summary>Click for details</summary>

Platform MBeanServer là registry MBean tích hợp trong JVM. Lấy bằng:

~~~java
MBeanServer server = ManagementFactory.getPlatformMBeanServer();
~~~

Ba cách truy cập platform MXBeans phổ biến:

1. trực tiếp qua ManagementFactory trong cùng JVM;
2. qua MXBean proxy trên MBeanServerConnection;
3. gián tiếp qua MBeanServer/MBeanServerConnection và ObjectName.

Ví dụ direct:

~~~java
RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
System.out.println(runtime.getUptime());
~~~

Proxy hữu ích khi mã muốn giữ interface có type rõ ràng nhưng JVM đích có thể ở xa.

MBeanServer cũng cho ứng dụng đăng ký MBean riêng. Phần custom instrumentation đó thuộc thiết kế JMX rộng hơn; module này chỉ đi đủ sâu để hiểu đường truy cập phục vụ chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-vs-remote-jmx">JMX local và remote</a>

<details>
<summary>Click for details</summary>

**Local JMX** quan sát JVM trên cùng host/ranh giới tiến trình. **Remote JMX** cung cấp management agent qua connector để client từ tiến trình/host khác truy cập.

Truy cập local thường đơn giản hơn và giảm bề mặt tấn công. Truy cập remote hữu ích khi:

- ứng dụng chạy trên host không tiện truy cập shell;
- hệ thống monitoring cần đọc MXBeans;
- người vận hành cần JConsole/JMC từ máy riêng.

Remote JMX thường được cấu hình qua system properties/management agent của JDK, ví dụ port, authentication và các tùy chọn SSL/TLS.

Đừng đồng nhất “remote JMX đã bật” với “ứng dụng đã quan sát được đầy đủ”. JMX cung cấp trạng thái quản lý; logs, JFR, traces hoặc business metrics vẫn có vai trò khác.

Trong môi trường container/orchestrated, networking/NAT/firewall có thể làm cấu hình RMI/JMX từ xa phức tạp hơn; cần kiểm tra topology triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jconsole-and-management-clients">JConsole và các client quản lý</a>

<details>
<summary>Click for details</summary>

**JConsole** là công cụ GUI theo dõi/quản lý đi kèm JDK. Nó kết nối qua JMX tới JVM local hoặc remote và hiển thị:

- memory;
- threads;
- classes;
- VM summary;
- MBeans.

JConsole hữu ích để khám phá giao diện quản lý và điều tra nhanh. Oracle lưu ý JConsole chạy local cũng tiêu tốn tài nguyên trên cùng host, nên môi trường production thường ưu tiên theo dõi từ xa để tách chi phí của UI khỏi JVM đích.

Ngoài JConsole, các client khác có thể dùng:

- JMC qua JMX cho dữ liệu quản lý;
- client Java tùy chỉnh với `JMXConnector`/`MBeanServerConnection`;
- tích hợp monitoring đọc các MXBean attribute.

UI của công cụ không thay đổi ngữ nghĩa dữ liệu. Cần hiểu MXBean/ObjectName phía dưới để tránh diễn giải metric sai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="remote-jmx-security">Ranh giới bảo mật của remote JMX</a>

<details>
<summary>Click for details</summary>

Remote JMX là interface quản lý có quyền quan sát — và đôi khi gọi thao tác — trên JVM. Vì vậy không được mở tùy tiện ra mạng.

Các nguyên tắc:

- bật authentication thay vì truy cập ẩn danh;
- dùng TLS/SSL khi dữ liệu đi qua network không tin cậy;
- firewall/network policy giới hạn nguồn kết nối;
- không công khai management port ra Internet;
- quản lý credentials/keystore/truststore như secret;
- chỉ bật thao tác/quyền truy cập thực sự cần thiết.

Cấu hình “không auth / không SSL” có thể tiện cho lab local nhưng không phải baseline cho production.

Ngoài bảo mật, truy cập từ xa còn có rủi ro vận hành: client polling quá dày hoặc thao tác nặng vẫn tạo tải. Management API không đồng nghĩa với chi phí bằng không.

Nếu bài toán chuyển sang IAM, vòng đời certificate hoặc kiến trúc bảo mật hạ tầng, hãy chuyển sang nhóm phụ trách security/infrastructure.

</details>

- [Quay lại đầu trang](#back-to-top)
