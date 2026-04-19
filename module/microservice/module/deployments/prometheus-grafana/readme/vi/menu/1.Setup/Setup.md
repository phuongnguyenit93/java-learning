# Setup Prometheus

## <a id="setup-prometheus"> Hướng dẫn Setup Prometheus & Grafana cho Microservices</a>

Để thiết lập hệ thống giám sát vào cấu trúc dự án hiện tại, chúng ta sẽ thực hiện qua 3 giai đoạn: Cấu hình Java (xuất dữ liệu), Cấu hình Prometheus (thu thập) và triển khai Docker.

---

### Bước 1: Cấu hình trong các module Java (Service)
Bạn cần thêm thư viện để Spring Boot có thể "phơi" dữ liệu (metrics) ra cho Prometheus đọc. Thực hiện trên các service như `order-service`, `payment-service`...

**1.1. Thêm Dependency (Gradle):**
	```gradle
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'io.micrometer:micrometer-registry-prometheus'
	```

**1.2. Cấu hình `application.yml`:**
Bật endpoint prometheus để dữ liệu có thể truy cập được từ bên ngoài:
	```yaml
	management:
	  endpoints:
	    web:
	      exposure:
	        include: prometheus, health, info
	  metrics:
	    tags:
	      application: ${spring.application.name} # Gắn tag để lọc trên Grafana
	```

---

### Bước 2: Cấu hình Prometheus (Thủ kho)
Trong thư mục `deployments/docker/prometheus`, tạo file `prometheus.yml` để định nghĩa các mục tiêu cần quét dữ liệu:

```yaml
	global:
	  scrape_interval: 15s # Chu kỳ quét dữ liệu (15 giây/lần)

	scrape_configs:
	  - job_name: 'spring-boot-services'
	    metrics_path: '/actuator/prometheus'
	    static_configs:
	      - targets: 
	          - 'order-service:8081'
	          - 'payment-service:8082'
	          - 'inventory-service:8083'
```

---

### Bước 3: Cấu hình Docker Compose
Cập nhật file `docker-compose.yml` tại folder `deployments/docker` để chạy cụm Monitoring:

```yaml
	services:
	  prometheus:
	    image: prom/prometheus:latest
	    container_name: prometheus
	    volumes:
	      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
	    ports:
	      - "9090:9090"
	    networks:
	      - spring-network

	  grafana:
	    image: grafana/grafana:latest
	    container_name: grafana
	    ports:
	      - "3000:3000"
	    environment:
	      - GF_SECURITY_ADMIN_PASSWORD=admin
	    networks:
	      - spring-network
	    depends_on:
	      - prometheus

	networks:
	  spring-network:
	    driver: bridge
```

---

### Bước 4: Kết nối và Hiển thị trên Grafana

1.  **Truy cập Grafana:** Mở `localhost:3000` (User/Pass: `admin`/`admin`).
2.  **Add Data Source:**
    * Chọn **Connections** -> **Data Sources** -> **Prometheus**.
    * Tại ô URL, nhập: `http://prometheus:9090`.
    * Nhấn **Save & Test**.
3.  **Import Dashboard (Khuyên dùng):**
    * Chọn **Dashboards** -> **New** -> **Import**.
    * Nhập ID: `11378` (Mẫu JVM Micrometer chuẩn cho Spring Boot).
    * Chọn nguồn dữ liệu Prometheus vừa tạo và nhấn **Import**.

---

### Kết quả đạt được
Sau khi cấu hình, bạn sẽ quan sát được các chỉ số thời gian thực:
* **Hiệu năng:** Biểu đồ CPU/RAM của từng service.
* **Độ tin cậy:** Tỉ lệ request thành công/thất bại (HTTP 200 vs 500).
* **JVM Health:** Trạng thái bộ nhớ Heap, giúp phát hiện sớm các lỗi Memory Leak.

## <a id="config-prometheus-advance"> Các phương pháp cấu hình Prometheus nâng cao</a>

Nếu bạn muốn tránh việc mount file `.yml` từ máy thật vào Docker (thường gặp lỗi đường dẫn trên Windows), dưới đây là 3 cách tiếp cận chuyên nghiệp hơn.

---

### 1. Dùng "Service Discovery" qua Eureka (Hiện đại)
Thay vì ghi cứng địa chỉ, Prometheus sẽ tự động hỏi **Eureka Server** để biết danh sách các service đang chạy.

* **Cơ chế:** Prometheus gọi sang Eureka -> Eureka trả về danh sách IP/Port -> Prometheus tự động quét (scrape).
* **Lợi ích:** Bạn không bao giờ phải sửa file `.yml` khi thêm service mới. Hệ thống sẽ tự động nhận diện instance mới khi nó được đăng ký với Eureka.

### 2. Dùng Grafana Cloud (Giải pháp tiết kiệm tài nguyên)
Nếu máy bạn đang quá tải, bạn có thể đẩy dữ liệu lên **Grafana Cloud** (miễn phí cho cá nhân).

* **Cách làm:** Thêm thư viện `micrometer-registry-otlp` vào dự án Java.
* **Cơ chế:** Các service chủ động "đẩy" (Push) dữ liệu lên mây qua Internet.
* **Ưu điểm:** * Xóa bỏ Container Prometheus và Grafana cục bộ để nhẹ máy.
    * Không cần cấu hình mạng Docker phức tạp.
    * Xem biểu đồ hệ thống mọi lúc mọi nơi qua điện thoại.

---

### 3. Giải pháp "Tự chế" (Custom Docker Image)
Nếu bạn ngại việc dùng `-v` (mount volume) mỗi lần chạy lệnh CLI, hãy đóng gói file cấu hình trực tiếp vào Image.

**Bước A: Tạo `Dockerfile` trong folder prometheus:**
```dockerfile
FROM prom/prometheus
# Copy file cấu hình vào bên trong image khi build
COPY prometheus.yml /etc/prometheus/prometheus.yml
```

**Bước B: Build image của riêng bạn:**
```bash
    docker build -t my-prometheus .
```

**Bước C: Khởi chạy cực kỳ đơn giản:**
```bash
    docker run -d --name prometheus \
      --network spring-network \
      -p 9090:9090 \
      my-prometheus
```

---

### 4. Bảng so sánh các phương pháp

| Phương pháp | Độ khó | Ưu điểm | Phù hợp cho |
| :--- | :--- | :--- | :--- |
| **Static Config** | Dễ | Đơn giản, dễ hiểu. | Dự án cực nhỏ (1-2 service). |
| **Service Discovery** | Trung bình | Tự động hóa hoàn toàn, đúng chuẩn Microservices. | **Khuyên dùng cho dự án của bạn.** |
| **Custom Image** | Trung bình | Gọn gàng, không cần mount file. | Môi trường CI/CD, Deployment. |
| **Grafana Cloud** | Dễ | Tiết kiệm RAM máy cá nhân. | Máy yếu, cần giám sát từ xa. |

---
**Lời khuyên:** Với cấu trúc dự án đã có sẵn **Eureka Server**, bạn nên đi theo hướng **Service Discovery**. Nó thể hiện đúng tinh thần của Microservices: *"Các thành phần tự tìm thấy nhau, không cần can thiệp thủ công"*.


## <a id="config-prometheus-with-eureka-server"> Triển khai Prometheus với Eureka Service Discovery</a>

Đây là lựa chọn chuyên nghiệp và đúng chất Microservices nhất. Với cách này, bạn chỉ cần cấu hình Prometheus một lần duy nhất, sau đó bất kỳ Service nào đăng ký vào Eureka sẽ tự động được Prometheus tìm thấy và thu thập dữ liệu.

---

### Bước 1: Chuẩn bị Prometheus Configuration (prometheus.yml)
Thay vì ghi cứng IP/Port, bạn sẽ bảo Prometheus "hỏi" Eureka. Hãy tạo file này tại đường dẫn dự án của bạn:

	```yaml
	global:
	  scrape_interval: 15s

	scrape_configs:
	  - job_name: 'eureka-discovery'
	    metrics_path: '/actuator/prometheus'
	    eureka_sd_configs:
	      - server: http://eureka-server:8761/eureka  # Tên container của Eureka Server
	    relabel_configs:
	      # Lọc và đổi tên hiển thị dựa trên tên ứng dụng trong Eureka
	      - source_labels: [__meta_eureka_app_name]
	        target_label: application
	```

---

### Bước 2: Build Custom Image (Né việc mount file)
Để đảm bảo tính ổn định khi chạy CLI, chúng ta đóng gói file config vào một Image riêng. Tại thư mục chứa file `.yml` trên, tạo file `Dockerfile`:

	```dockerfile
	FROM prom/prometheus:latest
	COPY prometheus.yml /etc/prometheus/prometheus.yml
	```

**Mở Terminal tại thư mục đó và chạy lệnh build:**
```bash
docker build -t my-prometheus-eureka .
```

---

### Bước 3: Khởi chạy bằng Docker CLI
Sử dụng các lệnh sau để "hợp nhất" hệ thống giám sát vào mạng chung của dự án:

**1. Chạy Prometheus (Image vừa build):**
```bash
docker run -d \
  --name prometheus \
  --network java-network \
  -p 9090:9090 \
  my-prometheus-eureka
```

**2. Chạy Grafana:**
```bash
docker run -d \
  --name grafana \
  --network java-network \
  -p 3000:3000 \
  -e "GF_SECURITY_ADMIN_PASSWORD=admin" \
  grafana/grafana
```

---

### Bước 4: Kiểm tra thành quả

* **Kiểm tra Prometheus:** Truy cập `localhost:9090`. Vào mục **Status -> Service Discovery**. Bạn sẽ thấy các service như `ORDER-SERVICE`, `PAYMENT-SERVICE` xuất hiện tự động.
* **Kiểm tra Grafana:** Truy cập `localhost:3000`, kết nối Data Source tới `http://prometheus:9090` và Import Dashboard **ID: 11378**.

---
**Ưu điểm vượt trội:** Với cấu hình này, khi bạn scale thêm instance hoặc thêm một Microservice hoàn toàn mới, Prometheus sẽ tự động "nhận diện" ngay lập tức mà không cần bạn phải khởi động lại hay chỉnh sửa bất kỳ file cấu hình nào.