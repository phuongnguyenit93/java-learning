## <a id="prometheus-and-grafana"> Prometheus & Grafana: "Bác sĩ" giám sát sức khỏe hệ thống</a>

Trong dự án của bạn, nếu Tracing giúp truy vết hành trình request, thì bộ đôi này giúp bạn nắm bắt **"sức khỏe" tổng thể** của hệ thống (CPU, RAM, số lượng request/giây).

### 1. Prometheus là gì? (Người đi thu thập dữ liệu)
Hãy coi Prometheus là một **"Anh thủ kho cần cù"**. Nhiệm vụ của anh ta là cứ sau một khoảng thời gian cố định (ví dụ 15 giây), anh ta lại chạy đến từng Service để thu thập thông tin.

* **Cơ chế Pull (Kéo):** Thay vì Service tự gửi dữ liệu đi, Prometheus chủ động gọi vào endpoint `/actuator/prometheus` của Spring Boot để lấy dữ liệu.
* **Time-series Database:** Lưu dữ liệu dưới dạng chuỗi thời gian. Ví dụ: *Lúc 10:00 CPU là 20%, lúc 10:15 là 25%*.
* **Chuyên trị số liệu (Metrics):** Chỉ quan tâm đến con số (số request, latency, memory). Nó không lưu log văn bản.

### 2. Grafana là gì? (Người họa sĩ trình bày)
Nếu Prometheus là anh thủ kho với các bảng số liệu khô khan, thì Grafana là một **"Họa sĩ tài ba"**.

* **Giao diện trực quan:** Kết nối vào Prometheus và vẽ thành các biểu đồ, đồ thị, đồng hồ đo (gauge) đẹp mắt.
* **Dashboard tập trung:** Tạo một màn hình duy nhất để giám sát toàn bộ các service: `order`, `payment`, `inventory`.
* **Cảnh báo (Alerting):** Tự động gửi tin nhắn Telegram/Email nếu CPU vọt lên quá 90%.



### 3. Sự khác biệt dễ hiểu nhất

| Đặc điểm | Prometheus | Grafana |
| :--- | :--- | :--- |
| **Vai trò** | Thu thập và lưu trữ dữ liệu (Backend). | Hiển thị và phân tích dữ liệu (Frontend). |
| **Giao diện** | Đơn giản, dùng để test query (PromQL). | Cực đẹp, chuyên nghiệp, tùy biến cao. |
| **Thế mạnh** | Xử lý các phép toán trên số liệu (Metric). | Gom dữ liệu từ nhiều nguồn (SQL, Prometheus...). |

### 4. Áp dụng vào dự án java-learning
Dựa trên cấu trúc dự án của bạn, bộ đôi này sẽ hoạt động như sau:

1.  **Trong mỗi Service (Java):** Thêm thư viện `micrometer-registry-prometheus` để mở cổng dữ liệu.
2.  **Trong `deployments/prometheus`:** Cấu hình file `prometheus.yml` để liệt kê các mục tiêu cần "quét":
    ```yaml
    scrape_configs:
      - job_name: 'spring-boot-apps'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['order-service:8081', 'payment-service:8082']
    ```
3.  **Trong `deployments/grafana`:** Mở giao diện Grafana, thêm **Data Source** là Prometheus và bắt đầu tạo Dashboard.

---
**Lời khuyên:** Đừng cố gắng tự vẽ mọi biểu đồ từ đầu. Trên trang chủ của Grafana có rất nhiều mẫu **Dashboard ID** (ví dụ: ID `11378` cho Spring Boot) mà bạn chỉ cần nhập vào là có ngay một bộ dashboard chuyên nghiệp.