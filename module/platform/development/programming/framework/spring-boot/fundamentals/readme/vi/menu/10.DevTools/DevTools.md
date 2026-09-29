# Spring boot Devtools

## <a id="spring-boot-devtools"> Spring Boot DevTools: Tăng tốc quy trình phát triển</a>

Spring Boot DevTools là một bộ công cụ "tiện ích mở rộng" được thiết kế riêng để cải thiện trải nghiệm của lập trình viên. Mục tiêu lớn nhất là giảm bớt thời gian chờ đợi bằng cách loại bỏ việc phải restart ứng dụng thủ công mỗi khi thay đổi code.

---

### 1. Các tính năng cốt lõi

* **A. Tự động Restart (Automatic Restart):**
    * Mỗi khi bạn lưu code (`Ctrl + S`), DevTools sẽ tự động nạp lại ứng dụng.
    * **Cơ chế:** Sử dụng 2 ClassLoaders. **Base ClassLoader** (chứa các thư viện bên thứ ba - không đổi) và **Restart ClassLoader** (chứa code của bạn). Khi có thay đổi, nó chỉ nạp lại Restart ClassLoader nên tốc độ cực nhanh.

* **B. LiveReload (Tự động tải lại trình duyệt):**
    * Khi bạn thay đổi HTML, CSS, JS trong `resources/static` hoặc `templates`, trình duyệt sẽ tự động Refresh (F5) mà không cần restart ứng dụng.

* **C. Tự động tắt Caching (Property Defaults):**
    * DevTools tự động tắt tính năng cache của các template engine như Thymeleaf khi đang ở môi trường phát triển, giúp các thay đổi giao diện hiển thị ngay lập tức.

* **D. Hỗ trợ Remote (Remote Applications):**
    * Cho phép đẩy thay đổi code từ máy cá nhân lên một server từ xa đang chạy mà không cần quy trình build/deploy phức tạp.

---

### 2. Cách tích hợp vào dự án

Thêm dependency sau vào file `build.gradle`:

```groovy
	dependencies {
	    // developmentOnly giúp DevTools không bị đóng gói khi build Production
	    developmentOnly 'org.springframework.boot:spring-boot-devtools'
	}
```

---

### 3. Cấu hình tối ưu trên IntelliJ IDEA (Windows)

Để DevTools hoạt động "mượt" nhất, bạn nên kích hoạt 2 chế độ sau trong IntelliJ:

1.  **Settings > Build, Execution, Deployment > Compiler:** Tích chọn **Build project automatically**.
2.  **Settings > Advanced Settings:** Tích chọn **Allow auto-make to start even if developed application is currently running**.

---

### 4. So sánh nhanh: DevTools vs. Actuator

| Đặc điểm | Spring Boot DevTools | Spring Boot Actuator |
| :--- | :--- | :--- |
| **Mục đích** | Tăng tốc độ viết code. | Giám sát và quản lý ứng dụng. |
| **Môi trường** | **Development** (Chỉ dùng khi code). | **Production** (Dùng khi vận hành). |
| **Tính năng chính** | Auto-restart, LiveReload. | Health check, Metrics, Loggers. |

---

### 5. Mẹo cho dự án Multi-module
Nếu DevTools không nhận diện được thay đổi ở các module con (như module `infrastructure`), hãy khai báo thêm đường dẫn quét trong `application.properties`:

```properties
	spring.devtools.restart.additional-paths=../infrastructure/src/main/java
```