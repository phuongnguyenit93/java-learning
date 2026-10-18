# Cấu hình Eureka Server

## <a id="context-path-mistake"> Giải đáp: Tại sao vào http://localhost:8761/eureka lại báo lỗi 404? </a>

Đây là một hiểu lầm rất phổ biến. Để truy cập vào giao diện quản lý, bạn hãy thử lại với địa chỉ: **[http://localhost:8761/](http://localhost:8761/)** (bỏ chữ `/eureka` ở cuối).


## 1. Tại sao lại có sự khác biệt này?

Eureka Server phân tách rõ ràng giữa hai đối tượng tương tác:

* **Giao diện Dashboard (Dành cho con người):** Nằm tại đường dẫn gốc `/`. Đây là nơi bạn xem trực quan các Service đang chạy (Instances currently registered), trạng thái RAM, CPU và chế độ Self-Preservation của Eureka.
* **API Endpoint (Dành cho máy móc):** Các đường dẫn như `/eureka` hoặc `/eureka/apps` là các API trả về dữ liệu dạng XML/JSON. Đây là nơi các Service con (như `order-service`, `payment-service`) gửi "nhịp tim" (**heartbeat**) và đăng ký danh tính.
  > **Lưu ý:** Nếu bạn vào bằng trình duyệt thông thường vào các đường dẫn API này mà không có tham số cụ thể, server thường trả về 404 hoặc lỗi định dạng vì nó không được thiết kế để hiển thị giao diện web tại đó.

## 2. Cách kiểm tra Eureka Server đã chạy thực sự chưa

Trước khi kết luận server lỗi, hãy kiểm tra 3 dấu hiệu "sống còn" sau:

1.  **Kiểm tra Console Log:** Trong IntelliJ/Eclipse, bạn phải tìm thấy dòng chữ:
    `Started DiscoveryServerApplication in ... seconds`
2.  **Kiểm tra Port:** Tìm dòng log báo Tomcat đã sẵn sàng:
    `Tomcat started on port 8761 (http)`
3.  **Truy cập đúng Dashboard:** Nhấn trực tiếp vào link: [http://localhost:8761/](http://localhost:8761/)

## 3. Cấu hình "chuẩn" cho Eureka Server (application.yml)

Để Eureka Server hoạt động ổn định và không tự đăng ký chính nó (gây rối Dashboard), bạn nên dùng cấu hình sau:

```yaml
	server:
	  port: 8761
	
	eureka:
	  client:
	    register-with-eureka: false # Server không cần tự đăng ký chính nó
	    fetch-registry: false       # Server không cần lấy danh sách từ server khác
	  instance:
	    hostname: localhost
```

---

### 💡 Mẹo nhỏ cho dự án java-learning:
Khi bạn bắt đầu chạy các Microservices con, hãy chú ý cột **Status** trên Dashboard. Nếu service hiện dòng chữ đỏ **EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP**, đó là chế độ *Self-Preservation* (Tự bảo tồn) — Eureka đang giữ lại danh sách service cũ vì nghi ngờ mạng chập chờn thay vì xóa chúng đi.

## <a id="eureka-config-list"> Bí kíp tra cứu: Tất cả cấu hình (Properties) của Eureka</a>

Để xem đầy đủ danh sách các thuộc tính cấu hình của Eureka hay bất kỳ dự án Spring Cloud nào, bạn có 3 "nguồn tài nguyên" từ mức độ tóm tắt đến chi tiết tận chân tơ kẽ tóc:

## 1. Tài liệu chính thức (Official Documentation) - Nguồn chuẩn nhất
Spring Cloud có một trang chuyên biệt liệt kê tất cả các thuộc tính cấu hình. Đối với Eureka, bạn có thể tra cứu tại:
* https://docs.spring.io/spring-cloud-netflix/reference/configprops.html
* **Mẹo:** Nhấn `Ctrl + F` và tìm kiếm tiền tố `eureka.server`, `eureka.client`, hoặc `eureka.instance`. Tài liệu sẽ giải thích rõ: *Default value* là gì và mục đích sử dụng.

## 2. Sử dụng IDE (IntelliJ IDEA) - Nguồn nhanh nhất
Vì bạn đang phát triển trên IntelliJ, bạn có một trợ thủ đắc lực ngay trong trình soạn thảo:

* **Gợi ý tự động (Autocomplete):** Trong file `application.yml`, bạn chỉ cần gõ `eureka.` rồi nhấn `Ctrl + Space`. IDE sẽ liệt kê tất cả các thuộc tính khả dụng kèm mô tả ngắn.
* **Truy cập Source Code:** Giữ phím `Ctrl` và click chuột trái vào một từ khóa cấu hình (ví dụ: `enable-self-preservation`). IntelliJ sẽ đưa bạn thẳng đến class Java định nghĩa thuộc tính đó (thường là `EurekaServerConfigBean.java` hoặc `EurekaClientConfigBean.java`).

## 3. Xem trực tiếp trên App đang chạy (Spring Boot Actuator)
Đây là cách "thực tế" nhất để xem cấu hình đang có hiệu lực (Real-time):

1. Thêm dependency `spring-boot-starter-actuator` vào module.
2. Cấu hình mở endpoint trong `application.yml`:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: "configprops,env"
   ```
3. Truy cập: `http://localhost:8761/actuator/configprops`.
4. Kết quả trả về là một file JSON chứa tất cả giá trị cấu hình thực tế, bao gồm cả những giá trị mặc định mà bạn không khai báo.

---

## 📊 Tóm tắt các nhóm cấu hình Eureka quan trọng

| Nhóm Config | Ý nghĩa | Ví dụ tiêu biểu |
| :--- | :--- | :--- |
| **eureka.server.\*** | Cấu hình cho "đầu não" Eureka Server. | `enable-self-preservation`, `eviction-interval-timer-in-ms` |
| **eureka.client.\*** | Cách service tương tác với Server. | `service-url`, `register-with-eureka`, `fetch-registry` |
| **eureka.instance.\*** | Định danh của chính service đó. | `instance-id`, `prefer-ip-address`, `lease-renewal-interval-in-seconds` |

---

### 💡 Lưu ý cho dự án java-learning:
Khi bạn chạy Eureka trong môi trường **Docker**, hãy đặc biệt chú ý đến nhóm `eureka.instance.*`. Bạn thường phải cấu hình `prefer-ip-address: true` để các service có thể tìm thấy nhau qua địa chỉ IP nội bộ của Docker thay vì dùng hostname của container.

```yaml
	eureka:
	  instance:
	    prefer-ip-address: true
	    instance-id: ${spring.application.name}:${random.value}
```