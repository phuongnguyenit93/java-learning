# 🐳 Hướng dẫn: Kiểm tra Spring Profile trong Docker Container

Tài liệu này tổng hợp các phương pháp từ cơ bản đến chuyên sâu để xác định chính xác Profile nào đang hoạt động bên trong một Container.

---

## 1. Kiểm tra Biến môi trường (Ngoài Container)
Đây là cách nhanh nhất để xem bạn đã truyền thông số gì vào Container lúc khởi tạo mà không cần truy cập vào bên trong.

    ```bash
    docker inspect <tên_container_hoặc_id> | grep -i "SPRING_PROFILES_ACTIVE"
    ```

* **Ưu điểm:** Thực hiện ngay trên máy Host.
* **Hạn chế:** Chỉ cho biết giá trị được truyền vào, không khẳng định ứng dụng Java bên trong đã nhận diện thành công hay chưa.

---

## 2. Kiểm tra Log ứng dụng (Xác thực nhất)
Spring Boot luôn in Profile đang hoạt động ngay ở những dòng đầu tiên khi khởi động. Đây là bằng chứng thực tế nhất về trạng thái Runtime.



    ```bash
    # Tìm dòng chứa thông tin profile
    docker logs <tên_container_hoặc_id> | grep "The following 1 profile is active"

    # Hoặc xem 100 dòng đầu để quan sát chi tiết
    docker logs --tail 100 <tên_container_hoặc_id>
    ```

**Kết quả mong đợi:**
`INFO 1 --- [main] c.e.demo.DemoApplication : The following 1 profile is active: "prod"`

---

## 3. Kiểm tra trực tiếp bên trong Container
Nếu bạn muốn chắc chắn tiến trình Java đang "nhìn" thấy những gì trong môi trường của nó, hãy truy cập trực tiếp vào Shell của Container.

    ```bash
    # Truy cập vào shell (sh hoặc bash tùy image)
    docker exec -it <tên_container_hoặc_id> sh

    # Liệt kê các biến môi trường mà tiến trình đang sở hữu
    env | grep SPRING
    ```

---

## 4. Sử dụng Spring Actuator (Chuyên nghiệp)
Nếu dự án có thư viện `spring-boot-starter-actuator` và mở các endpoint `env` hoặc `info`, bạn có thể kiểm tra qua trình duyệt hoặc `curl`.

    ```bash
    # Kiểm tra qua info
    curl http://localhost:<PORT>/actuator/info

    # Kiểm tra chi tiết qua env
    curl http://localhost:<PORT>/actuator/env
    ```

---

## 🛠️ Mẹo xử lý khi "Sai Profile"

Nếu bạn thấy `SPRING_PROFILES_ACTIVE` trả về đúng giá trị mong muốn nhưng App vẫn chạy cấu hình mặc định, hãy kiểm tra:

1.  **Lỗi chính tả:** Đảm bảo từ khóa là `SPRING_PROFILES_ACTIVE` (có chữ **S** ở cuối PROFILES). Nhiều người thường viết nhầm thành `SPRING_PROFILE_ACTIVE`.
2.  **Hardcode trong code:** Kiểm tra file `application.yml` nội bộ xem có dòng `spring.profiles.active` nào cố định giá trị không (Giá trị ghi cứng trong code thường đè biến môi trường nếu không cấu hình đúng).
3.  **Docker Compose Override:** Nếu dùng nhiều file compose (chồng đè), hãy dùng lệnh sau để xem cấu hình tổng hợp cuối cùng trước khi chạy:

    ```bash
    docker compose config
    ```