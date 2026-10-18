# Observability

## <a id="3-core-of-observability"> Ba trụ cột của khả năng quan sát (Observability)</a>

Để giám sát toàn diện một hệ thống Microservices, bạn cần sự phối hợp của 3 yếu tố cốt lõi. Mỗi yếu tố trả lời cho một câu hỏi khác nhau về tình trạng hệ thống.

---

### 1. Chi tiết 3 yếu tố cốt lõi

| Trụ cột | Công cụ phổ biến | Mục đích (Câu hỏi trả lời) |
| :--- | :--- | :--- |
| **Logging** | Loki, ELK Stack | **CÁI GÌ** đã xảy ra? (Xem chi tiết nội dung lỗi, stack trace). |
| **Tracing** | Zipkin, Jaeger | Lỗi xảy ra **Ở ĐÂU**? (Request đã đi qua những service nào, nghẽn ở chặng nào). |
| **Metrics** | Prometheus, Grafana | Hệ thống đang **KHỎE HAY YẾU**? (Chỉ số CPU, RAM, số lượng lỗi 500). |

---

### 2. "Bức tranh toàn cảnh" trong dự án

Hãy tưởng tượng hệ thống giám sát của bạn giống như một trung tâm điều hành hiện đại:

* **Prometheus:** Đóng vai trò đo "nhịp tim" và "huyết áp" của toàn hệ thống (**Metrics**).
* **Loki:** Đóng vai trò đọc và lưu trữ "nhật ký" hoạt động hằng ngày (**Logs**).
* **Zipkin:** Đóng vai trò vẽ "bản đồ" di chuyển của từng request giữa các service (**Tracing**).
* **Grafana:** Đóng vai trò là cái **màn hình TV trung tâm**, nơi tổng hợp và hiển thị cả 3 nguồn dữ liệu trên một cách trực quan.



---

### 3. Tại sao cần cả ba?

Nếu chỉ có một trong ba, bạn sẽ gặp khó khăn khi xử lý sự cố:
* Nếu chỉ có **Metrics**: Bạn biết hệ thống đang sập (CPU 100%) nhưng không biết tại sao.
* Nếu chỉ có **Logging**: Bạn thấy log lỗi nhưng không biết lỗi này xuất phát từ request nào ở service phía trước gửi sang.
* Nếu chỉ có **Tracing**: Bạn biết request bị chậm ở Service A, nhưng không biết lúc đó Service A đang bị đầy RAM hay do code xử lý logic bị lỗi.

**Sự kết hợp hoàn hảo:** 1. Nhận cảnh báo từ **Grafana** (Metrics báo lỗi 500 tăng cao).
2. Vào **Zipkin** xem Trace ID của những request bị lỗi đó.
3. Dùng Trace ID đó tìm trong **Loki** để xem Log chi tiết để sửa code.