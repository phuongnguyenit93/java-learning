# Setup Arthas bằng docker

## <a id="docker-arthas">Quản lý tập trung: Arthas Tunnel Server & Docker Agent </a>

Khi vận hành một hệ thống gồm nhiều Service, việc sử dụng **Arthas Tunnel** giúp bạn điều khiển và chẩn đoán mọi container từ một giao diện Web duy nhất mà không cần can thiệp trực tiếp vào Terminal của từng máy.

## Bước 1: Dựng "Trạm điều khiển" (Arthas Tunnel Server)
Bạn cần một container trung tâm để tiếp nhận các kết nối từ các Service khác đổ về.

Chạy lệnh Docker sau:
```bash
   docker run -d \
     --name arthas-tunnel-server \
     -p 8080:8080 \
     -p 7777:7777 \
     hengyunabc/arthas-tunnel-server
```
* **Port 8080:** Cổng truy cập giao diện Web Console.
* **Port 7777:** Cổng tiếp nhận kết nối WebSocket từ các Agent (Service).

## Bước 2: "Cấy" Arthas vào Service (Docker Agent)
Giả sử bạn có `inventory-service` đang chạy. Bạn cần đưa Arthas vào bên trong Image của Service đó.

**1. Cập nhật Dockerfile của Service:**
Sử dụng kỹ thuật Multi-stage build để lấy Arthas từ Image chính thức:
```dockerfile
   FROM openjdk:17-jdk-slim
   # ... các lệnh build app của bạn ...
   
       # Cấy Arthas vào image từ source chính thức
       COPY --from=hengyunabc/arthas:latest /opt/arthas /opt/arthas
       
       ENTRYPOINT ["java", "-jar", "app.jar"]
       ```
   
   **2. Kích hoạt Agent kết nối về Tunnel:**
   Sau khi Service khởi chạy, dùng `docker exec` để ra lệnh cho Agent "gọi điện" về trạm trung tâm:
   ```bash
   docker exec -it <inventory_container_id> \
     java -jar /opt/arthas/arthas-boot.jar \
     --attach-only \
     --tunnel-server 'ws://host.docker.internal:7777/ws' \
     --agent-id inventory-service
```
* **Lưu ý:** Trên Windows/Mac, dùng `host.docker.internal` để Container tìm thấy Tunnel Server đang chạy ở máy host.

## Bước 3: Điều khiển từ xa qua Web Console
Đây là lúc bạn gặt hái thành quả. Bạn không cần mở Terminal nữa:

1. Mở trình duyệt, truy cập: `http://localhost:8080`
2. **Thông số nhập:**
    * **IP:** `localhost`
    * **Port:** `8080`
    * **Agent ID:** `inventory-service` (đúng tên bạn đặt ở Bước 2).
3. Nhấn **Connect**.

---

## 📊 Tại sao nên dùng Tunnel thay vì `docker exec` thông thường?

| Lợi ích | Giải thích |
| :--- | :--- |
| **Quản lý tập trung** | Chỉ cần 1 tab trình duyệt để quản lý 10-20 service cùng lúc thay vì mở hàng chục Terminal. |
| **Vượt rào cản mạng** | Tunnel cho phép bạn đứng từ máy cá nhân soi code trên Server (Staging) qua WebSocket an toàn. |
| **Giao diện trực quan** | Web Console hỗ trợ copy-paste log, xem Dashboard và biểu đồ mượt mà hơn Terminal. |

---

### 💡 Mẹo nhỏ cho hệ thống Microservices:
Để chuyên nghiệp hơn, thay vì chạy lệnh `docker exec` thủ công ở Bước 2, bạn có thể cấu hình **Arthas Spring Boot Starter** trong file `pom.xml`. Khi đó, mỗi khi Service khởi động, nó sẽ tự động đăng ký với Tunnel Server mà bạn không cần gõ thêm bất kỳ lệnh Docker nào.

```xml
	<dependency>
	    <groupId>com.taobao.arthas</groupId>
	    <artifactId>arthas-spring-boot-starter</artifactId>
	    <version>3.7.1</version>
	</dependency>
```

## <a id="arthas-spring-boot-starter">Arthas Spring Boot Starter: Nhúng "Trạm chẩn đoán" vào mã nguồn</a>

Đây là cách tiếp cận chuyên nghiệp nhất dành cho dân làm Spring Boot. Thay vì dùng các lệnh Docker bên ngoài, bạn sẽ tích hợp Arthas trực tiếp vào vòng đời của ứng dụng.

## 1. Cơ chế hoạt động của "Starter"
Khi bạn thêm dependency vào `pom.xml`, Spring Boot sẽ tự động kích hoạt một con Agent ngầm khi ứng dụng khởi chạy.

* **Không cần file JAR rời:** Bạn không cần tải hay lưu trữ `arthas-boot.jar` bên ngoài nữa.
* **Tự động báo danh:** App sẽ chủ động kết nối về Tunnel Server dựa trên cấu hình có sẵn trong file YAML.

## 2. Cách cấu hình (Cực kỳ đơn giản)

**Bước 1: Thêm Dependency vào `pom.xml`**
```xml
   <dependency>
       <groupId>com.taobao.arthas</groupId>
       <artifactId>arthas-spring-boot-starter</artifactId>
       <version>3.7.1</version>
   </dependency>
```

**Bước 2: Cấu hình trong `application.yml`**
```yaml
   arthas:
     # Địa chỉ Tunnel Server (Trạm trung tâm)
     tunnel-server: ws://host.docker.internal:7777/ws
     # Tên định danh duy nhất cho Service này trên giao diện Web
     agent-id: inventory-service
     # Các cổng giao tiếp mặc định (nếu muốn đổi)
     telnet-port: 3658
     http-port: 8563
```

## 3. Tại sao cách này lại "xịn" hơn?

* **Tự động hóa 100%:** Chỉ cần `docker-compose up`, tất cả Service sẽ tự động xuất hiện trên Web Console của Arthas Tunnel.
* **Phù hợp với Cloud/K8s:** Bạn không cần biết Container ID hay IP cụ thể. Dù có 100 bản sao (Replicas) đang chạy, chúng sẽ tự động đổ về bảng danh sách tập trung.
* **Quản lý tập trung:** Dễ dàng kết hợp với **Spring Cloud Config** để bật/tắt Arthas trên toàn hệ thống chỉ bằng một thay đổi nhỏ ở file cấu hình trung tâm.



## 4. Một vài lưu ý "sống còn" về bảo mật

Vì việc nhúng trực tiếp giúp truy cập vào "nội tạng" của App rất dễ dàng, bạn cần đặc biệt chú ý:

1. **Môi trường Production:** Tuyệt đối không bật starter này trên Production nếu không có tường lửa (Firewall) bảo vệ nghiêm ngặt các cổng `7777` và `8080`.
2. **Tài nguyên:** Nó sẽ chiếm thêm khoảng **20MB - 50MB RAM** để duy trì Agent ngầm. Hãy đảm bảo giới hạn RAM (Memory Limit) của Container đủ cho phần dôi dư này.
3. **Môi trường Local:** Cấu hình `enabled: true` nên được đặt trong file `application-dev.yml` để tránh việc vô tình kích hoạt ở các môi trường không mong muốn.

---

### 💡 Mẹo nhỏ cho dự án java-learning:
Nếu bạn muốn tạm thời tắt Arthas mà không muốn xóa code/dependency, chỉ cần thêm dòng này vào config:
```yaml
   arthas:
     enabled: false
```