# Các tính năng của Hawtio

## <a id="hawtio-feature">Hawtio: "Phòng phẫu thuật" của ứng dụng Java </a>


### 1. Quản lý Thread (Luồng) - "Nội soi" thời gian thực
Trong khi SBA chỉ cung cấp một bản Thread Dump (ảnh chụp tĩnh), Hawtio cho phép bạn theo dõi sự biến thiên:

* **Thread Chart:** Vẽ biểu đồ trực quan về số lượng thread (Peak, Live, Daemon). Bạn sẽ thấy ngay nếu có hiện tượng "Thread Leak".
* **Phân tích Blocked:** Làm nổi bật các thread đang ở trạng thái `BLOCKED` hoặc `WAITING`. Bạn có thể nhìn thấy chính xác thread A đang chờ lock từ thread B nào.
* **Chi tiết Call Stack:** Nhấp vào bất kỳ thread nào để xem toàn bộ **Stack Trace** hiện tại. Cực kỳ hữu ích để tìm ra "ai" đang làm app chạy chậm mà không cần log file rườm rà.

### 2. Can thiệp hàm qua JMX - "Điều khiển từ xa"
Đây là tính năng "quyền lực" nhất trong tab `JMX > Operations`. Bạn có thể thực hiện những việc mà bình thường phải viết code hoặc restart app mới làm được:

* **Gọi hàm bất kỳ:** Nếu bạn có một `EmailService` với hàm `sendTestEmail(String email)`, bạn có thể nhập email vào ô input và nhấn **Execute**. App sẽ thực thi ngay lập tức.
* **Thay đổi cấu hình "nóng":** Thay đổi các cờ (flags) boolean, đổi thời gian timeout của connection pool (như HikariCP) mà không cần sửa file `.properties`.
* **Kích hoạt Logic:** Chủ động kích hoạt một tiến trình quét dữ liệu (Batch Job) hoặc xóa Cache (`clearCache()`) thủ công.

---

### Bảng so sánh "Kỹ năng đặc biệt"

| Tính năng | Spring Boot Admin (SBA) | Hawtio |
| :--- | :--- | :--- |
| **Theo dõi Thread** | Chụp ảnh tĩnh (JSON/Text) | Biểu đồ động + Tương tác trực tiếp |
| **Tương tác Bean** | Chỉ xem (Read-only) | Thực thi hàm (Invoke Operations) |
| **Thay đổi cấu hình** | Chỉ xem Property | Sửa đổi giá trị MBean ngay lập tức |
| **Giao diện** | Thân thiện, hiện đại, dễ hiểu | Kỹ thuật, chi tiết, dạng cây (Tree) |

---

### Kết luận:
* **Dùng SBA khi bạn muốn biết:** "Hệ thống của tôi hôm nay khỏe không?"
* **Dùng Hawtio khi bạn muốn hỏi:** "Cụ thể cái Thread này đang làm gì? Tôi muốn ép cái Bean này chạy hàm này ngay bây giờ!"

Sự kết hợp giữa SBA và Hawtio sẽ biến bạn thành một "siêu nhân" quản trị hệ thống Java, vì không có ngóc ngách nào của JVM mà bạn không soi tới được!