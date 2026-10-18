# Centralized Logging

## <a id="centralized-logging"> Centralized Logging: Quản lý Log tập trung với Loki hoặc ELK</a>

Loki và ELK là các hệ thống giúp bạn "soi" Log (những dòng chữ báo lỗi trong console) một cách hiệu quả. Thay vì kiểm tra từng service, chúng gom tất cả về một nơi duy nhất.

### 1. Tại sao bạn cần Centralized Logging?
Trong hệ thống Microservices (`order-service`, `payment-service`,...), khi có lỗi xảy ra, bạn không thể dùng `docker logs -f` cho từng container để tìm dấu vết.

* **Lợi ích:** Gom toàn bộ log về một "kho" tập trung, cho phép tìm kiếm, lọc và phân tích lỗi từ một giao diện duy nhất.

---

### 2. ELK Stack (Ông vua truyền thống)
ELK là bộ ba công cụ mạnh mẽ:
* **E (Elasticsearch):** Cỗ máy tìm kiếm cực mạnh, lưu trữ và đánh chỉ mục (index) toàn bộ nội dung log.
* **L (Logstash):** Bộ lọc dữ liệu, xẻ nhỏ log (tách ngày tháng, level INFO/ERROR) trước khi lưu trữ.
* **K (Kibana):** Giao diện hiển thị chuyên sâu cho Log.

> **Nhược điểm:** Cực kỳ "ngốn" RAM. Chạy ELK đôi khi tốn tài nguyên hơn cả các service Java cộng lại.

---

### 3. Grafana Loki (Chàng lính trẻ tài năng)
Loki được thiết kế bởi đội ngũ Grafana với triết lý: "Đơn giản và hiệu quả".

* **Giao diện:** Dùng chung với **Grafana**, không cần cài thêm Kibana.
* **Cơ chế:** Chỉ đánh chỉ mục các **Label** (ví dụ: `app=order-service`) thay vì toàn bộ nội dung, nên cực kỳ nhẹ và tiết kiệm RAM.
* **Tích hợp:** Cho phép chuyển đổi mượt mà giữa Metrics (biểu đồ) và Logs (văn bản) ngay trên một màn hình.



---

### 4. So sánh nhanh: ELK Stack vs. Grafana Loki

| Đặc điểm | ELK Stack | Grafana Loki |
| :--- | :--- | :--- |
| **Tài nguyên** | Rất nặng (Tốn RAM/CPU). | Rất nhẹ (Phù hợp máy cá nhân). |
| **Tìm kiếm** | Full-text search cực nhanh. | Tìm kiếm theo nhãn & filter. |
| **Giao diện** | Kibana (Riêng biệt). | Tích hợp sẵn trong Grafana. |
| **Độ khó setup** | Phức tạp. | Đơn giản. |

---

### 5. Lời khuyên cho dự án java-learning
Dựa trên cấu trúc dự án hiện tại của bạn (đã có Grafana và Prometheus), **Loki** là sự lựa chọn tối ưu nhất.

* **Tiết kiệm RAM:** Giúp máy cá nhân của bạn vẫn đủ tài nguyên chạy Docker, Database và Keycloak.
* **Hợp nhất:** Bạn chỉ cần cài thêm **Promtail** (để đi nhặt log từ container gửi cho Loki). Toàn bộ Metrics và Logs sẽ hội quân tại một Dashboard Grafana duy nhất.

---
**Mẹo:** Để Loki hoạt động tốt, hãy đảm bảo các service Java của bạn in log ra định dạng **JSON** hoặc có cấu trúc rõ ràng, giúp việc lọc dữ liệu trên Grafana trở nên dễ dàng hơn.