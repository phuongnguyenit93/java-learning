# Config Gateway

## <a id="api-gateway-config"> Hướng dẫn cấu hình API Gateway chuyên nghiệp </a>

Để cấu hình API Gateway trong hệ thống Microservices, chúng ta không chỉ dừng lại ở việc khai báo Route mà còn phải tích hợp nó với **Eureka** (để tìm service) và **Config Server** (để quản lý cấu hình tập trung).

---

### Bước 1: Khai báo file build.gradle
Bạn cần các thư viện sau để Gateway có thể "nói chuyện" được với các thành phần khác trong hệ thống.

```gradle
	dependencies {
	    // 1. Cốt lõi của Gateway (Sử dụng WebFlux/Netty)
	    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
	    
	    // 2. Client để lấy cấu hình từ Config Server
	    implementation 'org.springframework.cloud:spring-cloud-starter-config'
	    
	    // 3. Client để đăng ký và tìm service trên Eureka
	    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
	    
	    // 4. Load Balancer để chia tải giữa các instance
	    implementation 'org.springframework.cloud:spring-cloud-starter-loadbalancer'
	    
	    // 5. Actuator để Admin Server có thể giám sát
	    implementation 'org.springframework.boot:spring-boot-starter-actuator'
	}
```

---

### Bước 2: Thiết lập kết nối tới Config Server
Tại thư mục `src/main/resources/` của module `api-gateway`, tạo file `application.yml` để trỏ về "nguồn tri thức":

```yaml
	spring:
	  application:
	    name: api-gateway
	  config:
	    # Trỏ tới Config Server để lấy các route và cấu hình chi tiết
	    import: "optional:configserver:http://localhost:8888/"

	eureka:
	  client:
	    service-url:
	      defaultZone: http://localhost:8761/eureka/
```

---

### Bước 3: Tạo file cấu hình Gateway trong config-repo
Bây giờ, bạn sang thư mục `config-repo` và tạo file `api-gateway.yml`. Đây mới là nơi chứa "linh hồn" của Gateway.

```yaml
	server:
	  port: 8080

	spring:
	  cloud:
	    gateway:
	      # Tự động tạo route dựa trên tên Service Id trên Eureka
	      discovery:
	        locator:
	          enabled: true
	          lower-case-service-id: true
	      
	      # Định nghĩa Route thủ công cho chuyên nghiệp
	      routes:
	        - id: order-service-route
	          uri: lb://order-service # lb:// là Load Balancer, hỏi Eureka để lấy IP
	          predicates:
	            - Path=/api/v1/orders/**
	          filters:
	            - StripPrefix=0

	management:
	  endpoints:
	    web:
	      exposure:
	        include: "*"
	  endpoint:
	    health:
	      show-details: always
```

---

### Bước 4: Viết Code cho Main Class
Đảm bảo bạn có các Annotation cần thiết để kích hoạt khả năng tìm kiếm service.

```java
	package com.java.learning.gateway;

	import org.springframework.boot.SpringApplication;
	import org.springframework.boot.autoconfigure.SpringBootApplication;
	import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

	@SpringBootApplication
	@EnableDiscoveryClient // Để đăng ký và tìm kiếm service trên Eureka
	public class ApiGatewayApplication {
	    public static void main(String[] args) {
	        SpringApplication.run(ApiGatewayApplication.class, args);
	    }
	}
```

---

### Bước 5: Thứ tự khởi động và Kiểm tra
Để hệ thống chạy trơn tru, hãy bật các module theo trình tự:
1.  **Discovery Server** (8761) - Đợi trạng thái ổn định.
2.  **Config Server** (8888) - Để cung cấp cấu hình cho Gateway.
3.  **API Gateway** (8080).

**Cách kiểm tra:**
* Truy cập `http://localhost:8761`: Phải thấy **API-GATEWAY** ở trạng thái **UP**.
* Truy cập `http://localhost:8888/api-gateway/default`: Phải thấy nội dung file `api-gateway.yml`.

> **Lưu ý quan trọng:** Gateway sử dụng **WebFlux (Netty)**. Nếu bạn thêm `spring-boot-starter-web` (Tomcat) vào module này, ứng dụng sẽ báo lỗi xung đột thư viện ngay lập tức.

## <a id="route-config-gateway"> Cấu hình Route bằng Code Java trong Spring Cloud Gateway</a>

Trong Spring Cloud Gateway, ngoài cách cấu hình bằng file `.yml` (Declarative), bạn có thể định nghĩa các Route bằng **Code Java (Programmatic)**. Đây là cách tiếp cận linh hoạt khi bạn cần logic kiểm tra điều kiện phức tạp trước khi điều hướng.

### 1. RouterFunction (Spring WebFlux)
Đây là một phần của Spring WebFlux cơ bản, dùng để định nghĩa các API Endpoints theo phong cách Functional Programming thay vì dùng `@RestController`.

* **Mục đích:** Xử lý request tại chỗ (giống Controller) thay vì điều hướng (proxy) sang service khác.
* **Ví dụ:** Viết một API ngay tại Gateway để trả về lời chào hoặc kiểm tra nhanh trạng thái hệ thống.

```java
  @Configuration
  public class LocalRouteConfig {
      @Bean
      public RouterFunction<ServerResponse> helloRoute() {
          return RouterFunctions.route()
              .GET("/hello-gateway", request -> 
                  ServerResponse.ok().bodyValue("Hello from Gateway Local!"))
              .build();
      }
  }
```

### 2. GatewayRouterFunction (Spring Cloud Gateway)
Đây là "vũ khí" chính của Gateway từ phiên bản **Spring Cloud Gateway 4.x**. Nó kết hợp sức mạnh của WebFlux RouterFunction nhưng bổ sung các tính năng đặc thù như Filters và Load Balancer.

* **Mục đích:** Định nghĩa logic điều hướng (Routing) sang Microservices khác bằng Code.
* **Ưu điểm:** Có thể dùng Java Stream, `if-else`, hoặc truy vấn Database/Redis để quyết định Route.

```java
  import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
  import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
  import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

  @Configuration
  public class GatewayRoutes {
      @Bean
      public RouterFunction<ServerResponse> orderServiceRoute() {
          return route("order-service")
              .route(path("/api/v1/orders/**"), http("lb://order-service"))
              .before(request -> {
                  // Thêm logic xử lý trước khi forward (ví dụ: Logging, Auth)
                  return request;
              })
              .build();
      }
  }
```

### 3. So sánh: Cấu hình .yml vs. Code Java

| Đặc điểm | Cấu hình .yml (Declarative) | Cấu hình Java Code (Programmatic) |
| :--- | :--- | :--- |
| **Độ khó** | Dễ, trực quan, dễ đọc. | Đòi hỏi kỹ năng Functional Programming. |
| **Linh hoạt** | Thấp (chỉ cấu hình tĩnh). | Rất cao (xử lý logic động). |
| **Hot Reload** | Tuyệt vời (qua Config Server). | Khó (thường phải build và deploy lại). |
| **Phù hợp** | Route cơ bản, cố định. | Route có logic phức tạp, bảo mật đặc thù. |

### 4. Lời khuyên cho dự án java-learning
Vì bạn đang sử dụng **Config Server**, hãy ưu tiên dùng file **.yml**.

* **Tại sao?** Khi dùng Java Code, mỗi lần thêm Route bạn phải thực hiện quy trình: Sửa Code -> Commit -> Build -> Deploy. Với `.yml`, bạn chỉ cần sửa file text trên repo và gọi `/refresh`, giúp đạt trạng thái **Zero Downtime**.
* **Khi nào dùng Code?** Chỉ sử dụng khi bạn có những logic "quái dị" mà YAML không diễn tả được (ví dụ: A/B Testing chia tải dựa trên ID người dùng thực tế trong Database).