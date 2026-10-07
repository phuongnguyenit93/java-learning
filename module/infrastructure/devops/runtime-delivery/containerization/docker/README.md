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

Đây là bản đóng gói toàn bộ hướng dẫn về Docker BuildKit vào định dạng README.md hoàn chỉnh. Tôi đã định dạng các khối mã nguồn và bảng biểu để bạn có thể sử dụng ngay làm tài liệu kỹ thuật cho dự án.

Markdown
# 🚀 Tối ưu hóa Docker Build với BuildKit cho Spring Boot

Tài liệu này hướng dẫn cách sử dụng BuildKit để tăng tốc độ đóng gói ứng dụng Java, đặc biệt là tối ưu hóa việc quản lý thư viện (dependencies) thông qua cơ chế Mount Cache.

---

## 1. Docker BuildKit là gì?

**BuildKit** là công cụ thực thi (build engine) thế hệ mới của Docker, thay thế cho trình builder truyền thống. Nó được thiết kế để tăng tốc độ build, bảo mật hơn và linh hoạt hơn.



### Tại sao nên dùng BuildKit?
* **Parallelism**: Tự động phát hiện và chạy các công đoạn build không phụ thuộc nhau cùng một lúc.
* **Cache thông minh**: Cơ chế cache hiệu quả hơn, đặc biệt là với các folder chứa thư viện (như `.m2` của Maven hay `.gradle`).
* **Secrets handling**: Truyền các thông tin nhạy cảm (API key, mật khẩu) mà không để lại vết trong image cuối cùng.
* **SSH Forwarding**: Giúp bạn truy cập các private repo mà không cần copy SSH key vào image.

---

## 2. Cách sử dụng BuildKit cho công đoạn `bootJar`

Với ứng dụng Spring Boot, phần nặng nhất thường là tải và đóng gói các thư viện. Chúng ta sẽ sử dụng tính năng **Mount Cache** để giữ lại các thư viện này giữa các lần build.

### Bước 1: Kích hoạt BuildKit
Trước khi chạy lệnh build, hãy đảm bảo BuildKit đã được bật bằng cách thiết lập biến môi trường:

    ```bash
    export DOCKER_BUILDKIT=1
    docker build -t my-spring-app .
    ```

### Bước 2: Viết Dockerfile tối ưu (Multi-stage build)
Dưới đây là ví dụ sử dụng BuildKit để cache thư mục Gradle khi chạy `bootJar`:

    ```dockerfile
    # syntax=docker/dockerfile:1
    FROM eclipse-temurin:17-jdk-alpine AS build
    WORKDIR /app
    COPY . .

    # Sử dụng tính năng --mount của BuildKit để cache thư mục .gradle
    RUN --mount=type=cache,target=/root/.gradle \
        ./gradlew bootJar --no-daemon

    # Stage cuối cùng để tạo image chạy app (nhẹ và bảo mật)
    FROM eclipse-temurin:17-jre-alpine
    WORKDIR /app
    COPY --from=build /app/build/libs/*.jar app.jar
    EXPOSE 8080
    ENTRYPOINT ["java", "-jar", "app.jar"]
    ```

**Giải thích dòng "ma thuật":**
`RUN --mount=type=cache,target=/root/.gradle ...`
* **`--mount=type=cache`**: Tính năng đặc quyền của BuildKit, tạo không gian lưu trữ tạm thời tồn tại qua các lần build.
* **`target=/root/.gradle`**: Nơi Gradle lưu trữ bản tải xuống. Lần build sau, Docker sẽ nhận diện thư viện đã có sẵn và không tải lại.

---

## 3. Lợi ích thực tế

| Đặc điểm | Docker truyền thống | Docker với BuildKit |
| :--- | :--- | :--- |
| **Tốc độ build lại** | Chậm (thường tải lại lib nếu đổi code) | **Rất nhanh** (chỉ biên dịch code mới) |
| **Kích thước Image** | Có thể lớn nếu không tối ưu | **Tối ưu** nhờ Multi-stage |
| **Bảo mật** | Khó quản lý secret | **An toàn** với `--mount=type=secret` |

---

Đây là bản đóng gói hoàn chỉnh nội dung phân tích chuyên sâu về BuildKit Cache Mount vào định dạng README.md. Tôi đã sắp xếp lại các đề mục và tối ưu hóa các khối mã nguồn để bạn có thể lưu trữ làm tài liệu kỹ thuật.

Markdown
# 💎 Chuyên sâu về BuildKit: Cơ chế Cache Mount trong Docker

Tài liệu này giải thích sự khác biệt cốt lõi giữa cơ chế Layer Cache truyền thống và BuildKit Cache Mount, giúp bạn hiểu rõ tại sao tốc độ build ứng dụng Java được cải thiện vượt trội.

---

## 1. Sự khác biệt cốt lõi

Việc phân biệt hai cơ chế này là chìa khóa để tối ưu hóa Dockerfile:

* **Layer Cache truyền thống:** Hoạt động theo chuỗi. Nếu bạn thay đổi một file code, tất cả các bước (instructions) từ dòng `COPY` đó trở đi sẽ bị "vỡ cache" (**cache bust**). Hệ quả là Gradle phải tải lại toàn bộ dependencies từ đầu.
* **BuildKit Cache Mount (`--mount=type=cache`):** Hoạt động như một **"ổ cứng ngoài"** được gắn vào container trong quá trình build. Dù dòng lệnh `RUN` buộc phải thực thi lại do code thay đổi, thì nội dung trong thư mục cache vẫn được bảo toàn.



---

## 2. Kịch bản vận hành thực tế

Hãy xem cách BuildKit xử lý thông minh khi bạn thực hiện thay đổi mã nguồn:

1.  **Lần build đầu tiên:** BuildKit thấy cache trống. Nó chạy `bootJar`, tải 500MB thư viện về thư mục target, sau đó lưu trữ lượng dữ liệu này vào vùng nhớ riêng của Docker Engine.
2.  **Sửa đổi code Java:** Lệnh `COPY . .` làm thay đổi layer, khiến lệnh `RUN --mount=type=cache...` bị buộc phải chạy lại.
3.  **Phép màu xảy ra:** Ngay khi lệnh `RUN` bắt đầu, BuildKit ngay lập tức **"gắn ngược"** 500MB thư viện cũ vào thư mục target.
4.  **Kết quả:** Gradle nhận diện mọi dependencies đã có sẵn, nó chỉ biên dịch duy nhất file code bạn vừa sửa. Thời gian build giảm từ vài phút xuống còn vài giây.

---

## 3. Quản lý vòng đời của Cache

Dữ liệu trong cache mount được lưu trữ bền vững và không bị mất đi trừ khi:

* **Chủ động xóa:** Sử dụng lệnh `docker builder prune --filter type=exec.cachemount`.
* **Dọn dẹp hệ thống:** Sử dụng lệnh `docker system prune -a` (tùy thuộc vào cấu hình hệ thống).
* **Garbage Collection (GC):** Dung lượng cache vượt quá giới hạn thiết lập trong cấu hình chuyên sâu của Docker.

---

## ⚠️ Lưu ý quan trọng về Đường dẫn (Path)

Để Cache Mount hoạt động chính xác, tham số `target` phải khớp với thư mục Home của người dùng đang thực thi trong Dockerfile:

| User thực thi | Đường dẫn Target tương ứng |
| :--- | :--- |
| **User root** | `/root/.gradle` |
| **User gradle** | `/home/gradle/.gradle` |

> **Mẹo:** Nếu bạn thấy Gradle vẫn tải lại thư viện, hãy kiểm tra lại lệnh `whoami` trong Dockerfile để xác định chính xác đường dẫn target cần mount.

---

# 💾 Bản chất của BuildKit Cache: Bền bỉ hay Vĩnh viễn?

Cách hiểu về "Vĩnh viễn" rất gần với thực tế, nhưng chính xác hơn về mặt kỹ thuật, hãy coi đây là một **"Cache bền bỉ" (Persistent Cache)**.

---

## 1. Tại sao nó giống "Vĩnh viễn"?

BuildKit Cache có khả năng "sinh tồn" vượt xa các loại cache thông thường:

* **Vượt qua vòng đời Container:** Khi quá trình `docker build` kết thúc, container trung gian bị xóa sạch, nhưng dữ liệu trong mount cache vẫn được bảo toàn.
* **Vượt qua mọi lần Build:** Dù bạn build lần thứ 2 hay thứ 100, thậm chí xóa Image cũ để build lại từ đầu, "kho báu" này vẫn nằm im một chỗ chờ phục vụ.
* **Không phụ thuộc Layer:** Ngay cả khi bạn sửa code làm hỏng Layer Cache truyền thống, Mount Cache vẫn không hề hấn gì.

---

## 2. Tại sao nó KHÔNG thực sự là "Vĩnh viễn"?

Có 3 tình huống khiến cái "kho" này bị dọn dẹp (Reset):

1.  **Lệnh dọn dẹp hệ thống:** Khi chạy `docker system prune -a`, Docker có thể dọn dẹp các cache không còn liên kết với lần build hiện tại.
2.  **Lệnh dọn dẹp chuyên biệt:** `docker builder prune --filter type=exec.cachemount` là cách duy nhất để "reset" hoàn toàn bộ nhớ đệm của BuildKit.
3.  **Cơ chế Garbage Collection (GC):** Nếu ổ cứng bị đầy, BuildKit sẽ tự động xóa những phần cache lâu ngày không dùng đến để nhường chỗ cho dữ liệu mới.

---

## 3. So sánh các loại Cache trong Docker



| Đặc điểm | Layer Cache (Truyền thống) | Mount Cache (BuildKit) | Volume (Runtime) |
| :--- | :--- | :--- | :--- |
| **Thời điểm sử dụng** | Trong lúc Build | Trong lúc Build | Khi App đang chạy |
| **Tính kế thừa** | Bị mất nếu bước trước thay đổi | **Vẫn giữ lại** dù bước trước thay đổi | Không liên quan đến build |
| **Độ bền** | Lưu cùng với Image | Lưu tại Docker Engine | Lưu tại ổ cứng máy Host |

---

## 4. Cách hiểu chuẩn nhất: "Tủ đồ cá nhân"

Hãy tưởng tượng Mount Cache giống như một cái **"tủ đồ cá nhân"** tại nơi làm việc:

* Mỗi ngày bạn đến làm việc (**Build**), bạn mở tủ lấy đồ ra dùng.
* Hết giờ bạn về (**Build xong**), bạn cất đồ vào tủ và khóa lại.
* Ngày mai bạn mặc áo khác (**Sửa code**) hay đi xe khác đến (**Đổi Base Image**) thì đồ trong tủ vẫn còn nguyên.
* Đồ chỉ mất khi bạn **tự tay dọn tủ** hoặc công ty giải thể (**Xóa Docker Engine**).

> **Lời khuyên:** Trong 99% trường hợp sử dụng hằng ngày, nó sẽ hoạt động như một ổ cứng vĩnh viễn, giúp bạn tiết kiệm hàng giờ chờ đợi tải Gradle và thư viện.

---