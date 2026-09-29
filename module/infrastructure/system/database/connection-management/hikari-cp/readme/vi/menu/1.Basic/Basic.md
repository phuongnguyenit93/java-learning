# HikariCP và các khái niệm liên quan

## <a id="hikari-basic">HikariCP: "Tia sáng" tối ưu hóa kết nối Database</a>

Nếu bạn đang làm việc với các ứng dụng Java (như Spring Boot) và muốn tối ưu hóa hiệu suất, thì **HikariCP** chính là "ngôi sao sáng" bạn cần biết. Đây là thư viện Connection Pool mặc định của Spring Boot nhờ tốc độ cực nhanh và sự gọn nhẹ.

## 1. Vai trò của HikariCP trong hệ thống

Hãy tưởng tượng việc mở một kết nối (Connection) tới DB giống như việc xây một cây cầu mới mỗi khi có người muốn qua sông. Việc này cực kỳ tốn thời gian và tài nguyên. HikariCP đóng vai trò là một **"Bến xe buýt" (Connection Pool)**:

* **Duy trì sẵn kết nối:** Thay vì tạo mới và đóng liên tục, HikariCP tạo ra một nhóm các kết nối sẵn có và giữ chúng ở trạng thái chờ.
* **Tái sử dụng (Reuse):** Khi ứng dụng cần truy vấn, nó "mượn" một kết nối từ pool. Sau khi dùng xong, kết nối được trả lại pool thay vì bị hủy bỏ.
* **Quản lý vòng đời:** Tự động kiểm tra các kết nối "chết", quản lý timeout và đảm bảo số lượng kết nối luôn nằm trong giới hạn để không làm quá tải Database.

## 2. Tại sao HikariCP lại được ưa chuộng đến thế?

Hikari (tiếng Nhật nghĩa là "Ánh sáng") được thiết kế với triết lý tối giản và hiệu quả cực cao:

| Đặc điểm | Mô tả |
| :--- | :--- |
| **Tốc độ (Performance)** | Sử dụng kỹ thuật tối ưu hóa bytecode và cấu trúc dữ liệu vi mô để giảm thiểu độ trễ (latency). |
| **Gọn nhẹ (Lightweight)** | File JAR cực kỳ nhỏ (~130KB), giảm gánh nặng cho bộ nhớ ứng dụng. |
| **Độ tin cậy (Reliability)** | Xử lý cực tốt các tình huống rớt mạng hoặc DB khởi động lại mà không làm treo ứng dụng. |
| **Cấu hình thông minh** | Các tham số mặc định đã được tối ưu hóa rất tốt, thường không cần chỉnh sửa nhiều. |



## 3. Cấu hình thực tế trong Spring Boot

Trong Spring Boot, bạn chỉ cần khai báo thông tin DB trong file `application.properties`, hệ thống sẽ tự động kích hoạt HikariCP:

```properties
	# Thông tin kết nối cơ bản
	spring.datasource.url=jdbc:mysql://localhost:3306/mydb
	spring.datasource.username=root
	spring.datasource.password=123456
	
	# Cấu hình tối ưu cho Hikari Pool (Tùy chọn)
	# Số lượng kết nối tối đa trong pool
	spring.datasource.hikari.maximum-pool-size=10
	# Thời gian tối đa một kết nối được phép rảnh trước khi bị giải phóng (ms)
	spring.datasource.hikari.idle-timeout=30000
	# Thời gian tối đa chờ đợi để mượn một kết nối từ pool (ms)
	spring.datasource.hikari.connection-timeout=20000
```

---

## <a id="hikari-config">Tinh chỉnh HikariCP: 4 thông số "vàng" cho hiệu năng tối đa</a>

Cấu hình đúng các thông số của HikariCP là yếu tố then chốt để hệ thống chạy mượt mà, tránh lỗi `Connection is not available` hoặc làm treo Database.



## 1. maximumPoolSize (Số lượng kết nối tối đa)
Đây là giới hạn cao nhất số lượng kết nối mà Pool có thể tạo ra.
* **Sai lầm:** Cho rằng số này càng lớn càng tốt. Thực tế, quá nhiều kết nối gây nghẽn cổ chai tại CPU và Disk I/O của Database.
* **Công thức gợi ý (từ tác giả HikariCP):**
  $$connections = ((core\_count \times 2) + effective\_spindle\_count)$$
  *(Trong đó `core_count` là số CPU và `spindle_count` là số ổ đĩa cứng).*
* **Lời khuyên:** Bắt đầu với con số nhỏ (ví dụ: 10) và tăng dần nếu hệ thống bị nghẽn.

## 2. minimumIdle (Số lượng kết nối rảnh tối thiểu)
Số lượng kết nối mà HikariCP duy trì ở trạng thái "ngủ" để sẵn sàng phục vụ ngay.
* **Khuyến nghị:** HikariCP khuyên để giá trị này **bằng** với `maximumPoolSize`.
* **Lợi ích:** Giúp Pool ổn định, tránh việc liên tục tạo mới và hủy kết nối (vốn rất tốn tài nguyên).

## 3. connectionTimeout (Thời gian chờ kết nối)
Thời gian tối đa (ms) ứng dụng sẽ đợi để mượn được một kết nối từ Pool.
* **Giá trị mặc định:** 30000 (30 giây).
* **Lời khuyên:** Nếu sau 30 giây vẫn không lấy được kết nối, chứng tỏ Pool quá tải hoặc DB có vấn đề. Không nên tăng số này quá cao vì sẽ khiến người dùng phải chờ đợi "vô tận".

## 4. maxLifetime (Thời gian sống tối đa)
Thời gian tối đa một kết nối tồn tại trong Pool trước khi bị thay thế.
* **Lưu ý:** Giá trị này **bắt buộc** phải ngắn hơn giới hạn `wait_timeout` của Database (MySQL mặc định là 8 tiếng).
* **Khuyến nghị:** Khoảng 30 phút (1,800,000 ms) là con số an toàn.



---

## 🛠️ Cấu hình mẫu trong application.yml (Spring Boot)

```yaml
	spring:
	  datasource:
	    hikari:
	      maximum-pool-size: 20
	      minimum-idle: 20
	      connection-timeout: 30000
	      max-lifetime: 1800000
	      idle-timeout: 600000
	      pool-name: MyHikariCP-Pool
```

## 📈 Cách kiểm tra (Monitoring)

Để biết các thông số trên đã tối ưu chưa, bạn nên bật **Metrics** (qua Actuator & Micrometer):
* **Active Connections chạm ngưỡng max:** Cần tăng size hoặc tối ưu lại code (đóng kết nối sớm hơn).
* **Wait Time tăng cao:** Hệ thống đang bị nghẽn ở tầng Database (Disk hoặc CPU của DB server).

---