# Spring Cloud Config Server

## <a id="static-config-and-dynamic-config"> So sánh: Static Configuration (Gradle) vs. Dynamic Configuration (Spring Cloud Config)</a>

Đây là một vấn đề chạm đúng vào sự khác biệt giữa cấu hình lúc đóng gói (**Build-time**) và cấu hình lúc vận hành (**Run-time**). Trong môi trường Microservices, Spring Cloud Config tỏ ra hữu hiệu hơn rất nhiều.

## 1. Bảng so sánh chi tiết

| Đặc điểm | Dùng Gradle Task (Build-time) | Dùng Spring Cloud Config (Run-time) |
| :--- | :--- | :--- |
| **Thời điểm áp dụng** | Khi bạn đang Build dự án (tạo file `.jar`). | Khi ứng dụng đang chạy (Startup hoặc Refresh). |
| **Tính linh hoạt** | **Thấp.** Muốn đổi mật khẩu DB, bạn phải chạy lại Task, Build lại code và Deploy lại. | **Cao.** Chỉ cần sửa file trên Git/Folder, Service nhận cấu hình mới mà không cần build lại code. |
| **Quản lý tập trung** | **Kém.** Cấu hình nằm rải rác trong từng Service hoặc script của Gradle. | **Tuyệt vời.** Tất cả cấu hình của 10-20 services nằm gọn trong một Repo duy nhất. |
| **Bảo mật** | **Rủi ro.** Mật khẩu thường bị "hard-code" trong script hoặc lưu trong file jar. | **An toàn.** Hỗ trợ mã hóa (Encryption) và phân quyền truy cập vào Config Server. |
| **Môi trường (Env)** | Phải tạo nhiều file `.yml` khác nhau (`dev`, `prod`) từ trước. | Tự động khớp cấu hình theo profile (`dev`/`test`/`prod`) thông minh. |

## 2. Tại sao Spring Cloud Config lại thắng thế?

Trong dự án `java-learning` của bạn, nếu dùng Gradle Task, mỗi lần thay đổi địa chỉ RabbitMQ hoặc Eureka, bạn sẽ phải trải qua quy trình:
`Chỉnh sửa script Gradle` $\rightarrow$ `Chạy gradle build` $\rightarrow$ `Build lại Docker Image` $\rightarrow$ `Chạy lại Docker Compose`.
**=> Quá mất thời gian.**

Với **Spring Cloud Config**:
1. Bạn sửa file `application.yml` trong thư mục cấu hình (Config Repo).
2. Restart Service (hoặc dùng `@RefreshScope` để cập nhật ngay lập tức).
   **=> Xong.**



## 3. Khi nào thì nên dùng Gradle Task?

Gradle Task chỉ hữu hiệu cho các tác vụ mang tính chất **Scaffolding** (như Task vẽ sơ đồ cấu trúc dự án bạn đã làm) hoặc tạo ra các file cấu hình mặc định ban đầu. Nó không phù hợp để quản lý các thông số biến động theo môi trường.

---

### 💡 Lời khuyên cho lộ trình Microservices:

Hãy tập thói quen tách biệt hoàn toàn giữa **Code** và **Cấu hình** theo nguyên lý *12-Factor App*:

* **Code:** Chỉ build 1 lần duy nhất thành file `.jar` hoặc Docker Image (**Build once, run everywhere**).
* **Cấu hình:** Được "bơm" vào ứng dụng từ bên ngoài (**Spring Cloud Config**) tùy theo môi trường nó đang chạy.

```yaml
  # Ví dụ cấu hình bootstrap.yml để trỏ tới Config Server
  spring:
    application:
      name: order-service
    cloud:
      config:
        uri: http://config-server:8888
        fail-fast: true
        retry:
          max-attempts: 5
```

## <a id="spring-cloud-server-shutdown"> Giải quyết bài toán: Config Server sập thì cả hệ thống "chết chùm"?</a>

Bạn đã chỉ ra "tử huyệt" lớn nhất của mô hình cấu hình tập trung. Trong kỹ thuật, lỗi này được gọi là **Single Point of Failure (SPOF)** — một mắt xích duy nhất bị hỏng có thể kéo sập cả tòa nhà.

Tuy nhiên, trong thực tế triển khai Microservices, người ta sử dụng 3 "tấm khiên" bảo vệ sau để đảm bảo hệ thống vẫn sống sót:



## 1. Cơ chế Cấu hình Ngoại tuyến (Fail-fast & Local Overrides)
Khi một service con (Client) khởi động, nó sẽ thử gọi lên Config Server. Nếu không gọi được:
* **Fail-fast:** Service sẽ sập ngay lập tức. Điều này giúp phát hiện lỗi hệ thống sớm ngay khi deploy thay vì chạy với cấu hình sai.
* **Cơ chế dự phòng:** Bạn có thể để sẵn một file `application.yml` cơ bản bên trong chính service đó. Nếu Config Server không phản hồi, nó sẽ dùng cấu hình "sơ cua" này để duy trì hoạt động tối thiểu.

## 2. Bộ nhớ đệm (Caching / Local Copy)
Đây là cách bảo vệ phổ biến nhất:
* **Bản sao nội bộ:** Một khi service đã lấy được cấu hình lần đầu, nó sẽ lưu bản sao đó vào bộ nhớ.
* **Trạng thái chạy:** Nếu Config Server sập sau khi các service khác đã khởi động xong, hệ thống vẫn hoạt động bình thường vì chúng đã "cầm sẵn" cấu hình trong tay. Nó chỉ trở thành vấn đề khi bạn cần **Restart** service hoặc **Scale** thêm instance mới.

## 3. Khả năng sẵn sàng cao (High Availability - HA)
Trong môi trường Production, người ta không bao giờ chạy một Config Server duy nhất:



* **Chạy cụm (Cluster):** Chạy 2-3 instance Config Server cùng lúc.
* **Load Balancer:** Đặt một bộ cân bằng tải đứng trước. Nếu Server 1 sập, Load Balancer tự động chuyển request sang Server 2.
* **Git Backend:** Vì Config Server lấy dữ liệu từ Git, nên bản thân nền tảng Git (như GitHub/GitLab) cũng là một hệ thống cực kỳ khó sập.

---

## 📊 Tổng kết tình huống cho dự án java-learning

| Tình huống | Kết quả | Giải pháp kỹ thuật |
| :--- | :--- | :--- |
| **Sập khi đang chạy** | Không ảnh hưởng ngay lập tức. | Không cần quá lo, chỉ lo khi cần update cấu hình gấp. |
| **Sập khi khởi động** | Service con không lên được. | Dùng `spring.cloud.config.fail-fast=true` kết hợp với cơ chế **Retry** (thử lại sau mỗi 5s). |

```yaml
	# Cấu hình Retry và Fail-fast trong client (bootstrap.yml)
	spring:
	  cloud:
	    config:
	      fail-fast: true # Sập luôn nếu không thấy Config Server
	      retry:
	        initial-interval: 2000 # Thử lại sau 2s
	        max-attempts: 6 # Thử tối đa 6 lần
```

---

### 💡 Lời khuyên:
Trong dự án `java-learning`, bạn hãy cài đặt thêm thư viện `spring-retry` và `spring-boot-starter-aop`. Khi đó, service con của bạn sẽ trở nên "