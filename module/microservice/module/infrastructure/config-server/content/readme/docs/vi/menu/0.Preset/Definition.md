# Spring Cloud Config và các khái niệm liên quan

## <a id="cloud-config-trade-off"> Sự đánh đổi (Trade-off) của Config Server trong Microservices</a>

### 1. Những khuyết điểm thực tế của Config Server
* **Single Point of Failure (Điểm yếu chí tử):** Nếu Config Server sập khi các service khác đang khởi động, toàn bộ hệ thống sẽ không lấy được cấu hình và không thể chạy.
* **Độ trễ khi khởi động:** Các service con phải đợi "hỏi" Config Server xong mới làm việc tiếp được.
* **Tốn tài nguyên:** Mất thêm ít nhất 200MB - 500MB RAM chỉ để chạy một ông "phục vụ cấu hình".
* **Quản lý vòng đời:** Bạn phải đảm bảo Config Server luôn sống trước khi các service khác ngoi lên (vấn đề `depends_on` trong Docker).

### 2. Vậy tại sao người ta vẫn dùng?
Hãy tưởng tượng khi hệ thống của bạn mở rộng lên đến 50 services:

* **Cách dùng Gradle/File tĩnh:** Bạn phải vào 50 chỗ để sửa, build 50 lần, deploy 50 lần. Rủi ro sai sót (human error) là cực cao.
* **Cách dùng Config Server:** Bạn sửa 1 chỗ trên Git. Toàn bộ 50 service nhận diện thay đổi.

> **Sự thật là:** Khi hệ thống chỉ có 2-3 service (như dự án `java-learning`), Config Server có vẻ hơi "overkill". Nhưng từ 5 service trở lên, nó trở thành cứu cánh.

### 3. Các phương án thay thế (Lightweight)
Nếu bạn thấy việc tạo thêm 1 Java Service chỉ để giữ cấu hình là quá lãng phí, giới công nghệ có các lựa chọn khác:

* **Environment Variables (Docker/K8s):** Truyền cấu hình qua biến môi trường. Đơn giản, không cần server riêng, nhưng khó quản lý khi số lượng biến quá lớn.
* **Consul hoặc Etcd:** Công cụ chuyên biệt để quản lý cấu hình và Service Discovery. Nhẹ hơn Spring Boot vì viết bằng ngôn ngữ Go.
* **Cloud Native (AWS AppConfig, Azure App Configuration):** Sử dụng dịch vụ có sẵn của nhà cung cấp Cloud, không cần tự code server.

### 4. Giải pháp thực tế cho dự án của bạn
Vì bạn đang học để nắm vững Spring Cloud, tôi khuyên bạn vẫn nên làm Config Server để hiểu cơ chế. Sau đó có thể tối ưu bằng cách:

1.  **Gộp chung:** Trong dự án nhỏ, có thể tích hợp Config Server vào chung với Eureka Server để tiết kiệm tài nguyên (dù không khuyến khích trong thực tế).
2.  **Dùng Profile:** Khi code ở Local, dùng file `application.yml` nội bộ. Chỉ khi chạy Docker mới kích hoạt lấy cấu hình từ Config Server.

---
**Một mẹo nhỏ:** Trong Microservices, đừng sợ tạo thêm service. Hãy sợ việc các service bị "dính chặt" (tightly coupled) vào nhau.

## <a id="gradle-task-and-spring-cloud-config"> So sánh Gradle Task (Static) vs. Spring Cloud Config (Dynamic)</a>

Đây là một câu hỏi rất hay và chạm đúng vào sự khác biệt giữa Static Configuration (Cấu hình tĩnh - Gradle) và Dynamic Configuration (Cấu hình động - Spring Cloud Config).

Để trả lời ngắn gọn: **Spring Cloud Config hữu hiệu hơn rất nhiều trong môi trường Microservices thực tế.**

### So sánh Gradle Task vs. Spring Cloud Config

| Đặc điểm | Dùng Gradle Task (Build-time) | Dùng Spring Cloud Config (Run-time) |
| :--- | :--- | :--- |
| **Thời điểm áp dụng** | Khi bạn đang Build dự án (tạo file .jar). | Khi ứng dụng đang chạy (Startup hoặc Refresh). |
| **Tính linh hoạt** | **Thấp.** Muốn đổi mật khẩu DB, bạn phải chạy lại Task, Build lại code và Deploy lại. | **Cao.** Sửa file trên Git/Folder, Service nhận cấu hình mới mà không cần build lại. |
| **Quản lý tập trung** | **Kém.** Cấu hình nằm rải rác trong từng Service hoặc script của Gradle. | **Tuyệt vời.** Tất cả cấu hình của 10-20 services nằm gọn trong một Repo duy nhất. |
| **Bảo mật** | **Rủi ro.** Mật khẩu thường bị "hard-code" trong script hoặc lưu trong file jar. | **An toàn.** Hỗ trợ mã hóa (Encryption) và phân quyền truy cập. |
| **Môi trường (Env)** | Phải tạo nhiều file `.yml` (dev, prod) từ trước. | Tự động khớp cấu hình theo profile thông minh. |

---

### Tại sao Spring Cloud Config lại thắng thế?

Trong dự án `java-learning` của bạn, nếu bạn dùng Gradle Task, mỗi lần bạn thay đổi địa chỉ RabbitMQ hoặc Eureka, bạn sẽ phải trải qua quy trình:
1.  Chỉnh sửa script Gradle.
2.  Chạy `gradle build`.
3.  Build lại Docker Image.
4.  Chạy lại Docker Compose.
    **=> Quá mất thời gian.**

Với **Spring Cloud Config**, quy trình rút gọn chỉ còn:
1.  Bạn sửa file `application.yml` trong thư mục cấu hình.
2.  Restart Service (hoặc dùng `@RefreshScope` để cập nhật ngay lập tức).
    **=> Xong.**

---

### Khi nào thì nên dùng Gradle Task?

Gradle Task chỉ hữu hiệu cho các tác vụ mang tính chất **Scaffolding** (như cái Task vẽ sơ đồ cấu trúc dự án bạn đã làm) hoặc tạo ra các file cấu hình mặc định ban đầu. Nó không phù hợp để quản lý các thông số biến động theo môi trường.

## <a id="spof-config-server">Đối mặt với rủi ro Single Point of Failure (SPOF) của Config Server</a>

Đúng vậy, bạn đã chỉ ra "tử huyệt" lớn nhất của mô hình cấu hình tập trung. Trong kỹ thuật, lỗi này được gọi là **Single Point of Failure (SPOF)** — một mắt xích duy nhất bị hỏng kéo sập cả tòa nhà.

Tuy nhiên, trong thực tế triển khai Microservices, người ta không bao giờ để nó "sụp là chết" một cách dễ dàng như vậy. Có **3 "tấm khiên"** bảo vệ để hệ thống vẫn sống sót:

### 1. Cơ chế Cấu hình Ngoại tuyến (Fail-fast & Local Overrides)
Khi một service con (Client) khởi động, nó sẽ thử gọi lên Config Server. Nếu không gọi được:
* **Mặc định (Fail-fast):** Service sẽ sụp ngay lập tức. Điều này giúp bạn phát hiện lỗi hệ thống sớm khi mới deploy.
* **Cơ chế dự phòng:** Bạn có thể để sẵn một file `application.yml` cơ bản bên trong chính service đó. Nếu Config Server không phản hồi, nó sẽ dùng cấu hình "sơ cua" này để chạy ở mức tối thiểu.

### 2. Bộ nhớ đệm (Caching/Local Copy)
Đây là cách phổ biến nhất để duy trì sự ổn định:
* Các service con thường có cơ chế Cache. Một khi đã lấy được cấu hình lần đầu, nó sẽ lưu bản sao đó vào bộ nhớ.
* Nếu Config Server sập sau khi các service khác đã chạy, hệ thống vẫn hoạt động bình thường vì chúng đã có cấu hình trong tay. Nó chỉ trở thành vấn đề khi bạn muốn Restart service hoặc Scale thêm instance mới.

### 3. Tăng cường khả năng chịu tải (High Availability - HA)
Trong môi trường Production, người ta không bao giờ chạy một Config Server đơn lẻ:
* **Chạy cụm (Cluster):** Chạy 2-3 instance Config Server cùng lúc.
* **Load Balancer:** Đặt một bộ cân bằng tải đứng trước. Nếu Server 1 sập, Load Balancer tự động chuyển request sang Server 2.
* **Git Backend:** Vì Config Server lấy dữ liệu từ Git, nên bản thân Git (GitHub/GitLab) cũng là một hệ thống cực kỳ khó sập.

---

### Phân tích tình huống thực tế

| Tình huống | Kết quả | Giải pháp |
| :--- | :--- | :--- |
| **Sập khi đang chạy** | Không ảnh hưởng ngay lập tức. | Không cần quá lo, chỉ lo khi cần update cấu hình gấp. |
| **Sập khi khởi động** | Service con không lên được. | Dùng `fail-fast=true` kết hợp với cơ chế **Retry** (thử lại sau mỗi 5s). |

---

### Lời khuyên chân thành
Với dự án `java-learning` mang tính chất học tập hoặc chạy 1-2 module, việc bảo trì thêm "ông nội" Config Server này thực sự là một gánh nặng. Nếu bạn cảm thấy nó quá rủi ro hoặc phiền phức, hãy cân nhắc:

* **Sử dụng Environment Variables (Biến môi trường) trong Docker:** Nó vừa hiện đại, vừa không lo sập server cấu hình, lại rất phù hợp với tư duy Cloud Native.
* **Cấu hình Retry:**
```yaml
  spring:
    cloud:
      config:
        fail-fast: true
        retry:
          max-attempts: 6
          initial-interval: 1000
```

## <a id="spring-cloud-server-config"> Hướng dẫn cấu hình Spring Cloud Config (Server & Client)</a>

Tài liệu này hướng dẫn cách thiết lập hệ thống quản lý cấu hình tập trung cho dự án Microservices, giúp tách rời cấu hình khỏi mã nguồn và quản lý chúng dễ dàng hơn.

---

### 1. Tại sao cần Spring Cloud Config?

Trong môi trường Microservices, việc thay đổi cấu hình (như địa chỉ Database, RabbitMQ) cho hàng chục service là một cực hình. Spring Cloud Config giúp:
* **Quản lý tập trung:** Tất cả cấu hình nằm tại một nơi (Git hoặc Local Folder).
* **Thay đổi nóng:** Cập nhật cấu hình mà không cần build lại mã nguồn.
* **Bảo mật:** Hỗ trợ mã hóa các thông tin nhạy cảm.

---

### 2. Thiết lập Config Server (Ông trùm cấu hình)

Module này sẽ đóng vai trò phục vụ file cấu hình cho các service khác.

**A. Khai báo `build.gradle`:**
```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

**B. Main Class:**
```java
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

**C. Cấu hình `application.yml` (Sử dụng Local File System):**
```yaml
server:
  port: 8888
spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:/path/to/your/config-repo
```

---

### 3. Thiết lập Config Client (Service con)

Các service con sẽ "nhận cha" và tải cấu hình khi khởi động.

**A. Khai báo `build.gradle`:**
```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

**B. Cấu hình `application.yml` (Trỏ về Server):**
```yaml
spring:
  application:
    name: order-service
  config:
    import: "optional:configserver:http://localhost:8888/"
```

**C. Sử dụng giá trị trong Code:**
```java
    @RestController
    @RefreshScope // Giúp cập nhật giá trị mà không cần restart service
    public class ConfigController {
    
            @Value("${message:Default Message}")
            private String message;
    
            @GetMapping("/msg")
            public String getMessage() {
                return this.message;
            }
        }
```

---

### 4. Lưu ý quan trọng về tính an toàn

1.  **Tiền tố `optional:`:** Luôn sử dụng `optional:configserver:` để đảm bảo service con vẫn khởi động được bằng cấu hình nội bộ nếu Config Server tạm thời không liên lạc được.
2.  **Thứ tự khởi động:**
    * Chạy **Discovery Server** (Eureka) trước.
    * Chạy **Config Server** tiếp theo.
    * Cuối cùng mới chạy các **Service con**.
3.  **Cấu hình Retry:** Nên bổ sung thư viện `spring-retry` để service con tự động thử lại nếu Config Server khởi động chậm hơn dự kiến.

---

## <a id="hot-reload-spring-cloud-config"> Quy trình cập nhật cấu hình (Hot Reload) trong Spring Cloud </a>
Đây chính là "phép thuật" thực sự của Spring Cloud Config. Việc điều chỉnh cấu hình diễn ra theo một quy trình 3 bước: **Sửa file -> Config Server nhận diện -> Client làm mới (Refresh)**.

### 1. Cách thủ công: Sửa file trong config-repo
Vì bạn đang dùng chế độ `native` (thư mục cục bộ), cách đơn giản nhất là:

1.  Mở thư mục `config-repo` trên máy tính.
2.  Mở file `order-service.yml`.
3.  Thay đổi giá trị (ví dụ: đổi `timeout: 1000` thành `timeout: 5000`) và Lưu file.

> **Lưu ý:** Lúc này Config Server đã có giá trị mới, nhưng `order-service` (Client) vẫn đang giữ giá trị cũ trong RAM. Bạn cần thực hiện bước "Refresh".

---

### 2. Cách "Phù thủy": Refresh không cần khởi động lại
Để ứng dụng cập nhật giá trị mới mà không phải tắt đi bật lại, bạn cần sử dụng Spring Boot Actuator.

**Bước A: Thêm `@RefreshScope` vào Code**
Trong các Class Java dùng `@Value` hoặc `@ConfigurationProperties`, bạn phải thêm annotation này:

```java
	@RestController
	@RefreshScope // <--- Quan trọng nhất: Đánh dấu Bean này có thể nạp lại dữ liệu
	public class OrderController {
	    @Value("${message}")
	    private String message;
	}
```

**Bước B: Gọi Endpoint `/refresh`**
Bạn phải "nhắc" service con bằng cách gửi một request POST (dùng Postman hoặc cURL):

* **URL:** `http://localhost:8081/actuator/refresh` (Port của service con)
* **Method:** `POST`
* **Body:** Trống.

---

### 3. Cách tự động hóa: Dùng Spring Cloud Bus (Nâng cao)
Nếu bạn có hàng trăm instance đang chạy, việc gọi lệnh POST thủ công là bất khả thi. Giải pháp là dùng **Spring Cloud Bus** kết hợp với **RabbitMQ** hoặc **Kafka**.

1.  Bạn sửa file cấu hình.
2.  Gọi duy nhất 1 lần tới Config Server: `POST http://localhost:8888/actuator/bus-refresh`.
3.  Config Server gửi một "thông điệp" qua Message Broker tới tất cả các service con.
4.  Toàn bộ các service sẽ tự động đồng loạt làm mới cấu hình.

---

### 4. Quản lý qua Giao diện (Spring Boot Admin)
Vì bạn đã dựng **Admin Server**, bạn có thể điều chỉnh trực tiếp tại đó:

1.  Vào Dashboard `http://localhost:9090`.
2.  Chọn service cần cập nhật (ví dụ: `order-service`).
3.  Tìm mục **Configuration** hoặc **Environment**.
4.  Bạn có thể xem các thuộc tính đang chạy và kích hoạt lệnh **Refresh** ngay trên giao diện web.

---
**Tóm lại:** * **Sửa trên RAM:** Dùng JMX/Hawtio (Mất khi restart).
* **Sửa vĩnh viễn:** Sửa file ở Config Repo + Gọi `/refresh` (Bền vững).

## <a id="spring-cloud-server-precedence">Thứ tự ưu tiên cấu hình (Precedence) trong Spring Cloud</a>

Đây là một vấn đề cực kỳ quan trọng khi làm việc với Spring Cloud Config. Khi có sự xung đột giữa cấu hình tại Config Server và cấu hình nội bộ (file `application.yml` nằm trong chính service đó), Spring áp dụng các quy tắc rất chặt chẽ.

### 1. Quy tắc mặc định: Config Server là "Vua"
Mặc định, các cấu hình lấy từ Remote Config Server sẽ có độ ưu tiên **CAO HƠN** các file cấu hình nội bộ.

**Thứ tự ưu tiên từ cao xuống thấp:**
1.  **Config Server:** File cấu hình theo profile (ví dụ: `order-service-dev.yml`).
2.  **Config Server:** File cấu hình theo tên service (`order-service.yml`).
3.  **Config Server:** File `application.yml` (nằm trong config-repo, dùng chung cho tất cả).
4.  **Nội bộ Service:** File `application.yml` (nằm trong thư mục `src/main/resources` của project).

> **Ví dụ thực tế:**
> * Trong `config-repo/order-service.yml` bạn để: `server.port: 8881`
> * Trong `src/main/resources/application.yml` (nội bộ) bạn để: `server.port: 8080`
> * **Kết quả:** Khi chạy, service sẽ nhận port **8881**.

### 2. Tại sao lại ưu tiên Config Server?
Triết lý của Microservices là **"Externalize Configuration"** (Đưa cấu hình ra ngoài). Ý tưởng là bạn có thể thay đổi hành vi của ứng dụng (như đổi DB URL, tắt/mở tính năng) mà không cần phải build lại file JAR. Nếu cấu hình nội bộ thắng, thì Config Server sẽ mất đi ý nghĩa "điều khiển tập trung".

### 3. Cách "lật ngược thế cờ" (Override)
Nếu bạn muốn cấu hình nội bộ của service có quyền ghi đè những gì Config Server gửi xuống, bạn phải cấu hình tại **Config Server** (file trong repo) để cho phép điều này:

```yaml
	spring:
	  cloud:
	    config:
	      # Cho phép client ghi đè cấu hình từ server
	      allow-override: true 
	      # Cấu hình nội bộ sẽ thắng cấu hình từ server
	      override-none: true 
	      # Chỉ ghi đè nếu cấu hình đó không tồn tại ở hệ thống/biến môi trường
	      override-system-properties: false 
```

### 4. Tổng kết bảng thứ tự ưu tiên thực tế (Full Stack)

| Độ ưu tiên | Nguồn cấu hình |
| :--- | :--- |
| **1 (Cao nhất)** | Đối số dòng lệnh (Command line arguments: `--server.port=9000`) |
| **2** | Thuộc tính từ **Config Server** |
| **3** | Biến môi trường (OS Environment Variables) |
| **4** | File cấu hình nội bộ (`application.yml` trong JAR) |
| **5 (Thấp nhất)** | Giá trị mặc định trong code Java |

---
**Mẹo:** Trong môi trường Production, người ta thường dùng **Biến môi trường** hoặc **Config Server** để đảm bảo tính linh hoạt mà không cần can thiệp vào file JAR đã đóng gói.