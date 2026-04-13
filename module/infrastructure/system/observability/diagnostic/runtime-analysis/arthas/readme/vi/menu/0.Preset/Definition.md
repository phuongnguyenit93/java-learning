# Arthas và các khái niệm liên quan

## <a id="define-arthas"> Arthas: "Con mắt thần" chẩn đoán ứng dụng Java Runtime </a>

**Arthas** là công cụ chẩn đoán mã nguồn mở từ Alibaba, cho phép bạn soi thấu bên trong máy ảo JVM mà không cần khởi động lại server hay thay đổi mã nguồn.



## 1. Những khả năng "thần thánh" của Arthas

* **Dashboard trực quan:** Hiển thị thời gian thực CPU, Memory, Thread.
* **Kiểm tra Thread:** Phát hiện nhanh các Thread chiếm CPU cao hoặc tình trạng **Deadlock**.
* **Dịch ngược (Decompile):** Sử dụng lệnh `jad` để xem code thực tế đang chạy trên RAM, đảm bảo phiên bản deploy là chính xác.
* **Giám sát phương thức (Watch/Trace):** * Xem giá trị tham số (`params`) và kết quả (`returnObj`) mà không cần đặt log.
   * Tìm "nút thắt cổ chai" bằng cách đo thời gian thực thi từng dòng code.
* **Thay đổi code nóng (Hot Swap):** Sửa lỗi nhỏ và đẩy trực tiếp vào App đang chạy (`retransform`) mà không cần Restart.



## 2. Khi nào nên "triệu hồi" Arthas?

Bạn sẽ cần đến Arthas trong các tình huống "ngặt nghèo":
1.  **Lỗi chỉ có trên Production:** Không thể tái lập lỗi ở Local.
2.  **Hệ thống bị treo/chậm:** Cần tìm Thread đang "ăn" tài nguyên.
3.  **Vấn đề ClassLoader:** Xử lý lỗi `NoClassDefFoundError` hoặc xung đột thư viện.
4.  **Kiểm tra tham số ngầm:** Nghi ngờ dữ liệu sai nhưng hệ thống không in log.

## 3. Cơ chế hoạt động: Java Agent & Instrumentation

Arthas hoạt động dựa trên cơ chế **Java Agent**. Khi bạn "attach" vào một Process ID (PID), nó sử dụng Instrumentation API để can thiệp vào **Bytecode** của ứng dụng, thu thập thông tin hoặc thay đổi hành vi code theo lệnh.



## 🛠️ Các lệnh phổ biến cần nhớ

| Lệnh | Tác dụng |
| :--- | :--- |
| **`dashboard`** | Xem tổng quan hệ thống (CPU, RAM, GC). |
| **`thread -n 3`** | Hiển thị 3 thread "ngốn" CPU nhất. |
| **`jad <Class>`** | Dịch ngược class để xem mã nguồn thực tế. |
| **`watch <Class> <Method> "{params,returnObj}"`** | Theo dõi dữ liệu vào/ra của hàm. |
| **`trace <Class> <Method>`** | Đo thời gian chạy chi tiết từng bước trong hàm. |

---

### 💡 Lưu ý cho dự án java-learning:
Khi làm việc với các hệ thống Microservices trong Docker, Arthas sẽ giúp bạn kiểm tra các Proxy được tạo ra bởi Spring (như `@Transactional`) một cách cực kỳ trực quan thông qua lệnh `stack` hoặc `sc`.