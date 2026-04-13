# Hawtio

## <a id="hawtio-define">Hawtio: Bảng điều khiển "Cắm nóng" cho ứng dụng Java</a>

**Hawtio** (phát âm là "hot-io") là một Web Console mã nguồn mở, nhẹ và linh hoạt. Nếu Spring Boot Admin là hệ thống quản lý tập trung, thì Hawtio là một "bảng điều khiển gắn thêm" nhúng trực tiếp vào từng ứng dụng.

## 1. Bản chất của Hawtio
Hawtio hoạt động dựa trên **JMX (Java Management Extensions)**.
* Thay vì chỉ lấy dữ liệu qua HTTP JSON (như Actuator), Hawtio tương tác trực tiếp với các **MBeans (Managed Beans)** bên trong máy ảo Java, cho phép can thiệp sâu vào trạng thái của ứng dụng.

## 2. Các tính năng chính

* **JMX Explorer (Quản lý Bean):** Không chỉ xem danh sách, bạn còn có thể thay đổi giá trị thuộc tính hoặc thực thi các hàm (`operations`) của Bean ngay trên giao diện Web.
* **Log Viewer:** Xem log trực tiếp trên trình duyệt, không cần dùng lệnh `tail -f` qua SSH.
* **Tích hợp Spring Boot:** Hiển thị mượt mà các thông tin từ Actuator như `Health`, `Metrics`, và `Context`.
* **Quản lý Thread:** Theo dõi trạng thái thực thi và phát hiện các Thread bị Block.
* **Middleware Support:** Cực kỳ mạnh mẽ nếu bạn sử dụng **Apache Camel** hoặc **ActiveMQ** với khả năng vẽ sơ đồ luồng dữ liệu (Route) trực quan.

## 3. So sánh nhanh: Spring Boot Admin (SBA) vs. Hawtio

| Đặc điểm | Spring Boot Admin | Hawtio |
| :--- | :--- | :--- |
| **Kiến trúc** | Client - Server (Cần Server riêng) | Có thể nhúng trực tiếp (**Embedded**) |
| **Nguồn dữ liệu** | Actuator Endpoints (JSON) | **JMX (MBeans)** |
| **Khả năng tác động** | Chủ yếu là Read-only | **Read & Write** (Sửa thông số) |
| **Phạm vi** | Chuyên cho Spring Boot | Mọi ứng dụng Java |

## 4. Cách sử dụng với Spring Boot

Để tích hợp Hawtio vào dự án `java-learning`, bạn chỉ cần thêm dependency vào file build:

```gradle
	// Thêm Hawtio cho Spring Boot
	implementation 'io.hawt:hawtio-springboot:4.0.0'
```

Sau đó, truy cập vào đường dẫn: `/hawtio/index.html`. Hệ thống sẽ tự động nhận diện các Endpoint Actuator và hiển thị giao diện quản trị.

---

## 🎯 Khi nào nên chọn Hawtio?

1.  **Quản trị nhanh:** Khi bạn muốn có Dashboard ngay lập tức mà không muốn dựng thêm một Server Admin riêng biệt.
2.  **Thao tác sâu:** Khi cần kích hoạt lại một tiến trình ngầm hoặc thay đổi cấu hình nóng mà không muốn Restart App.
3.  **Hệ thống Messaging:** Khi dự án sử dụng nhiều Apache Camel hoặc các hệ thống tin nhắn phức tạp.


### 💡 Lời khuyên cho Backend Developer:
Hawtio là giải pháp **"Tất cả trong một"** cho từng Instance. Tuy nhiên, cũng giống như Arthas, hãy cẩn trọng khi bật Hawtio ở môi trường Production. Hãy đảm bảo đường dẫn `/hawtio` đã được bảo vệ bởi **Spring Security** (Basic Auth hoặc Role-based) để tránh lộ thông tin nhạy cảm của hệ thống.


## <a id="hawtio-and-sba">So sánh Hawtio và Spring Boot Admin (SBA)</a>

Sự khác biệt mà bạn cảm nhận được là hoàn toàn chính xác. Dù cả hai đều soi vào Actuator, nhưng triết lý hiển thị của chúng khác nhau hoàn toàn.

Có **3 lý do chính** khiến bạn thấy Hawtio có vẻ "nghèo nàn" hơn Spring Boot Admin (SBA) khi mới cài đặt:

### 1. Hawtio là "Kính hiển vi", SBA là "Bảng điều khiển (Dashboard)"
* **SBA:** Được thiết kế để làm Dashboard. Nó tự động tổng hợp dữ liệu từ nhiều endpoint Actuator để vẽ biểu đồ (CPU, Memory), thống kê số lượng Bean, hiển thị thông tin Git/Build một cách trực quan. Nó "dọn sẵn cỗ" cho bạn xem.
* **Hawtio:** Là một JMX Explorer. Nó thiên về việc liệt kê mọi thứ có trong JVM dưới dạng cây thư mục (JMX Tree). Bạn phải tự tìm đến đúng "ngõ ngách" để xem. Thông tin thực tế rất nhiều, nhưng nó nằm ẩn trong các folder MBean chứ không trình bày ra trang chủ.

### 2. Sự khác biệt về việc "Aggregating Data" (Gộp dữ liệu)
* **Spring Boot Admin:** Nó thực hiện các logic gộp. Ví dụ: Nó gọi `/mappings`, `/beans`, `/health` cùng lúc và trình bày chúng vào các tab riêng biệt (Insights, Loggers, JVM).
* **Hawtio:** Nếu bạn không cài thêm các Plugin, nó chỉ hiện thị những gì JMX cung cấp. Để thấy được thông tin Spring Boot rõ ràng hơn trong Hawtio, bạn cần tìm đến tab **"Spring Boot"** trên thanh menu của nó (nếu có) hoặc kiểm tra trong mục "Quartz", "Camel" nếu dự án có sử dụng.

### 3. Khả năng giám sát nhiều Instance
* **SBA:** Có khả năng quản lý nhiều Microservices cùng lúc. Bạn có thể thấy danh sách 10 service đang chạy, cái nào sống, cái nào chết.
* **Hawtio:** Thường chỉ tập trung vào chính nó (Local Instance). Nó không có giao diện "tổng quan toàn bộ hệ thống" mạnh mẽ như SBA.

---

### Để Hawtio hiển thị "nhiều đồ" hơn, bạn hãy kiểm tra:

* **Tab JMX:** Bạn vào mục JMX trên menu. Tìm folder `org.springframework.boot`. Tại đây bạn sẽ thấy gần như toàn bộ Actuator nhưng dưới dạng các thuộc tính (Attributes) của MBean. Bạn có thể thay đổi cấu hình `logging.level` trực tiếp tại đây.
* **Expose Endpoint:** Đảm bảo bạn đã expose hết các endpoint trong `application.properties`:

```properties
  management.endpoints.web.exposure.include=*
  management.endpoint.health.show-details=always
```

* **Hawtio Plugins:** Hawtio 4.x có các plugin tự động nhận diện Spring Boot. Nếu thanh menu phía trên của bạn chỉ có vài mục, có thể nó chưa nhận diện hết hoặc bạn đang dùng bản Hawtio cũ.

### Kết luận:

1. **Nếu bạn muốn đẹp, trực quan, quản lý nhiều app:** Dùng **Spring Boot Admin** (và cái Proxy Inspector chúng ta vừa build là một sự bổ sung tuyệt vời).
2. **Nếu bạn muốn soi sâu, sửa thông số Bean ngay khi đang chạy (Runtime Manipulation):** Dùng **Hawtio**.

## <a id="jokolia">Tìm hiểu về Jolokia và Hawtio </a>

Jolokia đóng vai trò là một "cây cầu" (bridge) công nghệ giúp Hawtio có thể giao tiếp và điều khiển các ứng dụng Java của bạn. Dưới đây là cách hoạt động và lý do tại sao nó lại quan trọng:

### 1. Jolokia là gì?
Về bản chất, Jolokia là một **JMX-over-HTTP bridge**.

* **JMX (Java Management Extensions):** Là tiêu chuẩn trong Java để quản lý và giám sát ứng dụng (ví dụ: kiểm tra bộ nhớ heap, số lượng thread, cấu hình log). Tuy nhiên, JMX gốc sử dụng giao thức RMI, vốn khá "khó chịu" khi đi qua firewall hoặc làm việc với các ứng dụng web hiện đại.
* **Giao thức của Jolokia:** Nó chuyển đổi các yêu cầu JMX phức tạp thành các chuẩn quen thuộc là **HTTP** và **JSON**.

### 2. Mối quan hệ với Hawtio
Hawtio là một giao diện web (Frontend). Nó không thể tự kết nối trực tiếp vào bên trong máy ảo Java (JVM) để lấy dữ liệu.

1.  Hawtio sẽ gửi các request dưới dạng JSON qua HTTP đến Jolokia.
2.  Jolokia (thường chạy như một Agent bên trong app Java hoặc một servlet) sẽ nhận request đó, đọc dữ liệu từ JMX, rồi trả kết quả về cho Hawtio hiển thị lên biểu đồ và bảng biểu.

### 3. Tại sao lại dùng bộ đôi này?
Việc sử dụng Jolokia thay vì JMX thuần túy mang lại nhiều lợi ích:

* **Thân thiện với Firewall:** Chỉ cần mở cổng HTTP (thường là 8080 hoặc 8778) là có thể giám sát từ xa.
* **Hiệu năng cao:** Jolokia hỗ trợ "bulk requests", cho phép lấy nhiều thông số chỉ trong một lần gọi mạng, giúp Hawtio load dữ liệu nhanh hơn.
* **Bảo mật:** Bạn có thể dễ dàng cấu hình xác thực (Authentication) thông qua các tiêu chuẩn web thông thường.

### 4. Cách Jolokia xuất hiện trong project
Tùy vào cách bạn cấu hình, Jolokia thường xuất hiện dưới 2 dạng:

* **JVM Agent:** Chạy song song khi khởi động app:
  ```bash
  java -javaagent:jolokia-agent.jar -jar your-app.jar
  ```

* **Spring Boot Starter:** Nếu bạn dùng Spring Boot, chỉ cần thêm dependency `jolokia-core`, nó sẽ tự động tạo một endpoint để Hawtio kết nối vào:
  ```xml
  <dependency>
      <groupId>org.jolokia</groupId>
      <artifactId>jolokia-core</artifactId>
  </dependency>
  ```
  *(Endpoint thường là `/actuator/jolokia`)*

---
**Tóm lại:** Nếu không có Jolokia, Hawtio sẽ "mù tịt" và không thể lấy được bất kỳ thông số nào từ ứng dụng Java của bạn.


## <a id="jmx-and-mbean">Tổng quan về JMX (Java Management Extensions)</a>

JMX là một công nghệ tiêu chuẩn của Java dùng để quản lý và giám sát các ứng dụng, thiết bị hoặc bất kỳ thực thể nào chạy trên nền tảng Java (JVM).

Nếu coi ứng dụng của bạn là một con tàu đang chạy, thì **JMX chính là hệ thống bảng điều khiển** và các cảm biến được gắn vào động cơ, giúp bạn biết tàu đang chạy nhanh bao nhiêu, nhiệt độ thế nào và cho phép bạn gạt cần điều khiển từ xa.

### 1. Kiến trúc của JMX
Kiến trúc JMX được chia thành 3 tầng chính:

* **Tầng Thiết bị (Instrumentation Level):** Nơi các tài nguyên được bao bọc trong các đối tượng Java đặc biệt gọi là **MBeans** (Managed Beans).
* **Tầng Đại lý (Agent Level):** Thành phần chính là **MBean Server**, đóng vai trò như một "nhà kho" trung tâm. Tất cả các MBeans phải được đăng ký vào đây thì thế giới bên ngoài mới thấy được.
* **Tầng Quản trị (Remote Management Level):** Nơi các công cụ bên ngoài (Hawtio, JConsole, VisualVM) kết nối vào MBean Server để đọc dữ liệu hoặc ra lệnh.

### 2. MBean là gì? (Trái tim của JMX)
MBean là một Java Object tuân thủ các quy tắc thiết kế của JMX để phơi bày (expose) các thông tin ra ngoài. Có 2 thành phần quan trọng:

* **Attributes (Thuộc tính):** Các biến mà bạn có thể xem hoặc sửa (ví dụ: số lượng kết nối, cấu hình timeout).
* **Operations (Thao tác):** Các hàm mà bạn có thể thực thi bằng cách nhấn nút (ví dụ: xóa cache, dừng dịch vụ, gửi mail test).

### 3. Tại sao JMX lại quan trọng?
* **Giám sát sức khỏe (Monitoring):** Theo dõi RAM, Thread, CPU trực quan mà không cần nhìn vào Log.
* **Cấu hình "Nóng" (Hot Reconfiguration):** Thay đổi tham số ứng dụng (đổi mật khẩu DB, thay đổi mức Log từ INFO sang DEBUG) ngay lập tức mà không cần Restart server.
* **Tích hợp dễ dàng:** Các thư viện lớn (Spring Boot, Hibernate, Tomcat, Kafka, HikariCP) đều đã tích hợp sẵn JMX.

### 4. Cách kết nối và xem JMX
Có nhiều cách để bạn "nhìn" thấy các thông số JMX:

* **JConsole / VisualVM:** Công cụ miễn phí đi kèm JDK. Chạy lệnh:
  ```bash
  jconsole
  ```
* **Hawtio:** Cung cấp giao diện Web hiện đại để quản lý JMX qua HTTP (thường qua cầu nối Jolokia).
* **Spring Boot Admin:** Đọc dữ liệu JMX và trình bày theo phong cách chuyên biệt cho Spring.

### 5. JMX trong Microservices
Trong kỷ nguyên Microservices và Docker, việc kết nối JMX trực tiếp qua cổng RMI truyền thống khá phức tạp do vấn đề Firewall. Đó là lý do tại sao người ta dùng **Jolokia**.

Jolokia đóng vai trò là một "người phiên dịch", chuyển đổi các yêu cầu từ JMX sang HTTP JSON. Hawtio chính là "người dùng" tiêu thụ các JSON đó để vẽ lên giao diện cho bạn.

---
**Tóm lại:** JMX là một "cánh cửa hậu" tiêu chuẩn và bảo mật, cho phép bạn soi vào bên trong bộ não của ứng dụng Java để theo dõi và điều khiển nó khi nó đang vận hành.

## <a id="jmx-feature">JMX: "Admin Dashboard Ngầm" và Cần Gạt Khẩn Cấp</a>

### 1. Tại sao nó "nhanh" và "đặc biệt" hơn các cách khác?

| Phương pháp | Cách hoạt động | Tốc độ phản hồi | Độ phức tạp |
| :--- | :--- | :--- | :--- |
| **Config Server** | Sửa file trên Git -> Refresh Endpoint -> App kéo logic mới. | Chậm (vài giây/phút). | Cao (Git, Config Server, Bus). |
| **Docker Env** | Sửa biến môi trường -> Restart Container. | Rất chậm (mất thời gian khởi động). | Trung bình. |
| **JMX (Hawtio)** | Sửa trực tiếp vào bộ nhớ của Bean đang chạy. | **Tức thì (Real-time).** | Thấp (Chỉ cần 1 Annotation). |

### 2. Sự "đặc biệt" của chiếc RestController này
Nếu một `@RestController` thông thường dành cho người dùng (User), thì JMX là RestController dành cho **Kỹ sư vận hành (Operator)**:

* **Không cần định nghĩa Route:** Bạn không cần viết `@GetMapping`. Chỉ cần đánh dấu `@ManagedAttribute`, JMX sẽ tự tạo "đường truyền".
* **Giao tiếp trực tiếp với Object:** Tác động thẳng vào Instance của Bean trong RAM, không qua các tầng lọc (Filter) hay Interceptor phức tạp.
* **Hỗ trợ mọi kiểu dữ liệu:** Hiển thị các cấu trúc dữ liệu phức tạp của Java mà không cần lo lắng việc convert JSON (Hawtio/Jolokia đã lo việc đó).

### 3. Những kịch bản "Cứu nguy" chỉ JMX làm tốt nhất
Hãy tưởng tượng các tình huống mà Config Server hay Docker không giúp ích được nhiều:

* **Kịch bản "Tháo ngòi nổ":** Hệ thống bị nghẽn ở vòng lặp vô tận. Bạn dùng Hawtio vào tab Thread, tìm đúng Thread đó và **Stop/Interrupt** nó ngay lập tức.
* **Kịch bản "Xả e":** Cache bị đầy hoặc sai dữ liệu. Thay vì restart hệ thống, bạn vào JMX của Bean Cache đó và nhấn nút **clear()**.
* **Kịch bản "Bật chế độ soi":** Nghi ngờ một hàm chạy sai nhưng không muốn bật Debug toàn server. Bạn vào JMX, đổi cờ `debugMode` của đúng Bean đó lên `true`.

### 4. Tuy nhiên, có một "Cái giá" phải trả
Vì JMX cho phép can thiệp trực tiếp vào RAM, nó có nhược điểm:

* **Tính tạm thời:** Những thay đổi qua Hawtio thường sẽ mất đi khi bạn restart app (vì chỉ sửa trên RAM). Config Server bền vững hơn vì lưu trên Git.
* **Bảo mật:** Nếu ai đó vào được Hawtio, họ có quyền "hủy diệt" hệ thống chỉ bằng một nút bấm.

---
**Tóm lại:** Hãy coi JMX là **"Cần gạt khẩn cấp"** và **"Bảng thông số kỹ thuật"** trực tiếp trên máy móc. Nó là công cụ mạnh nhất để điều chỉnh hệ thống tại chỗ (Ad-hoc) mà không phương pháp nào khác nhanh bằng.