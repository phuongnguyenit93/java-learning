# Spring Boot Admin

## <a id="spring-boot-admin">Spring Boot Admin (SBA): "Phòng giám sát camera" của Microservices</a>

Nếu Eureka là "danh bạ điện thoại" thì Spring Boot Admin (SBA) chính là "phòng giám sát camera" cho toàn bộ hệ thống Microservices của bạn.

Trong khi Eureka chỉ cho bạn biết Service A có đang sống hay không, thì Spring Boot Admin cho bạn xem "nội tạng" của Service A đang hoạt động thế nào thông qua một giao diện Web (UI) cực kỳ chuyên nghiệp.

### 1. Spring Boot Admin giúp bạn xem được những gì?
Nó thu thập dữ liệu từ các Actuator Endpoints của từng service và hiển thị lên dashboard:

* **Thông số phần cứng:** Theo dõi RAM, CPU, Heap Memory theo thời gian thực (rất quan trọng khi bạn test Virtual Threads).
* **Quản lý Log:** Có thể đổi Level Log (từ `INFO` sang `DEBUG`) của một service đang chạy ngay trên giao diện mà không cần restart.
* **Chi tiết Luồng (Thread Dump):** Xem danh sách tất cả các Thread đang chạy, cái nào đang bị Block, cái nào đang hoạt động.
* **HTTP Tracing:** Xem danh sách các request gần nhất vừa đi qua service đó (thời gian xử lý, status code).
* **Environment:** Xem toàn bộ biến môi trường và file cấu hình mà service đang sử dụng.

### 2. Tại sao bạn nên dùng nó trong dự án java-learning?
Thay vì phải gõ những đường dẫn loằng ngoằng và đọc file JSON thô của Spring Actuator như:  
`http://localhost:8081/actuator/metrics/jvm.memory.used`

Bạn chỉ cần vào Spring Boot Admin UI, mọi thứ đều có biểu đồ, màu sắc và nút bấm trực quan.

### 3. Cách thiết lập trong cấu trúc Multi-module
SBA hoạt động theo mô hình **Server - Client**.

* **Bước 1: Tạo module `infrastructure/admin-server`**
    * Dependency: `codecentric:spring-boot-admin-starter-server`
    * Main class: Thêm `@EnableAdminServer`.
    * **Kết nối với Eureka:** Cấu hình để Admin Server cũng là một Eureka Client. Khi đó, hễ có service nào mới đăng ký vào Eureka, nó sẽ tự động xuất hiện trên giao diện Admin.

* **Bước 2: Cấu hình các Service con (Clients)**
  Mọi service nghiệp vụ (`order-service`, `inventory-service`) chỉ cần thêm:
    * Dependency: `spring-boot-starter-actuator`
    * Cấu hình: Cho phép Actuator show hết thông tin ra:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: "*" # Mở hết tất cả "cửa sổ" để Admin Server nhìn vào
    endpoint:
      health:
        show-details: always
  ```

### 4. So sánh nhanh: Spring Boot Admin vs. Grafana

| Đặc điểm | Spring Boot Admin | Grafana + Prometheus |
| :--- | :--- | :--- |
| **Mục đích** | Quản lý, điều khiển và xem trạng thái tức thời. | Lưu trữ dữ liệu lịch sử và vẽ biểu đồ chuyên sâu. |
| **Thao tác** | Có thể tương tác (Đổi Log level, ép Restart). | Chỉ để xem (Read-only). |
| **Cài đặt** | Rất nhanh, thuần Java/Spring. | Phức tạp hơn, cần cài thêm DB (Prometheus). |
| **Phạm vi** | Chỉ dành cho Spring Boot. | Dùng được cho mọi thứ (Nodejs, MySQL...). |


## <a id="admin-server-config">Hướng dẫn dựng Admin Server (Phòng giám sát)</a>

Tiếp nối `discovery-server`, chúng ta sẽ dựng **Admin Server**. Vì bạn đã cấu hình BOM (Spring Cloud Dependencies) ở file root, việc tạo module này sẽ rất nhẹ nhàng.

**Lưu ý:** Admin Server sẽ đóng vai trò là một "khách hàng" của Eureka để tự động lấy danh sách các service về.

### Bước 1: Tạo module infrastructure/admin-server

1. **Khai báo build.gradle con:**

```gradle
   plugins {
       id 'org.springframework.boot'
   }

   dependencies {
       // Thư viện Spring Boot Admin Server
       implementation 'de.codecentric:spring-boot-admin-starter-server'
       
       // Eureka Client để Admin tự tìm các service khác
       implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
       
       // Thêm Security để bảo vệ Dashboard (khuyên dùng)
       implementation 'org.springframework.boot:spring-boot-starter-security'
   }
```

2. **Tạo Main Class:**
   Đừng quên annotation `@EnableAdminServer`.

```java
   package com.java.learning.admin;

   import de.codecentric.boot.admin.server.config.EnableAdminServer;
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

   @SpringBootApplication
   @EnableAdminServer
   @EnableDiscoveryClient // Để Admin Server tìm đến Eureka lấy danh sách service
   public class AdminServerApplication {
       public static void main(String[] args) {
           SpringApplication.run(AdminServerApplication.class, args);
       }
   }
```

### Bước 2: Cấu hình application.yml
File này sẽ định nghĩa Port và cách nó kết nối với "tổng đài" Eureka.

```yaml
	server:
	  port: 9090

	spring:
	  application:
	    name: admin-server
	  security:
	    user:
	      name: admin
	      password: admin_password

	eureka:
	  client:
	    service-url:
	      defaultZone: http://localhost:8761/eureka/
	    # Admin Server cũng cần đăng ký mình lên Eureka để các service khác biết
	    register-with-eureka: true
	    fetch-registry: true
```

### Bước 3: Cấu hình Security (Quan trọng)
Vì Spring Boot Admin có giao diện web, bạn cần cấu hình để nó cho phép các service con gửi dữ liệu vào và người dùng xem được giao diện.

```java
	@Configuration
	public class SecurityConfig {
	    @Bean
	    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	        http.authorizeHttpRequests(authorize -> authorize
	                .requestMatchers("/assets/**").permitAll() // Cho phép load CSS/JS
	                .requestMatchers("/login").permitAll()
	                .anyRequest().authenticated()
	            )
	            .formLogin(form -> form.loginPage("/login").permitAll())
	            .logout(logout -> logout.logoutUrl("/logout"))
	            .httpBasic(Customizer.withDefaults()) // Cho phép các service con login qua HTTP Basic
	            .csrf(csrf -> csrf.disable()); // Tắt CSRF để các service con gửi data dễ dàng
	        
	        return http.build();
	    }
	}
```

### Bước 4: Đăng ký module vào Root
Trong file `settings.gradle` ở thư mục gốc:

	```gradle
	include 'infrastructure:admin-server'
	```

### Bước 5: Kiểm tra
1.  Chạy **Discovery Server** (Port 8761).
2.  Chạy **Admin Server** (Port 9090).
3.  Truy cập `http://localhost:9090`, đăng nhập với `admin` / `admin_password`.

**Bạn sẽ thấy gì?**
Lúc này trên Dashboard sẽ xuất hiện 2 "ông lớn" đang ở trạng thái **UP**:
* **DISCOVERY-SERVER**
* **ADMIN-SERVER**