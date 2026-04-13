# Path trong hawtio

## <a id="path-hawtio">Giải mã: Tại sao Hawtio lại "núp" dưới đường dẫn /actuator?</a>

Trong kiến trúc Spring Boot hiện đại, Hawtio không còn chạy như một ứng dụng web riêng lẻ mà được tích hợp như một **Management Endpoint**. Điều này mang lại sự đồng nhất về bảo mật và quản lý.



## 1. Tại sao lại là /actuator/hawtio?
Spring Boot 3 ưu tiên gom tất cả các công cụ quản trị (Health, Metrics, Beans, Hawtio) vào tiền tố `/actuator`.
* **Lợi ích:** Bạn chỉ cần cấu hình bảo mật (Spring Security) cho một vùng duy nhất là `/actuator/**` thay vì phải cấu hình cho từng công cụ rời rạc.

Nếu bạn muốn đổi tên đường dẫn trong Actuator cho dễ nhớ hơn:
```properties
# Đổi /actuator/hawtio thành /actuator/console
management.endpoints.web.path-mapping.hawtio=console
```

## 2. Cách đưa Hawtio ra ngoài Actuator (Link trực tiếp)
Nếu bạn vẫn muốn truy cập theo kiểu truyền thống (ví dụ: `/inventory-service/hawtio`), bạn cần tách Hawtio ra khỏi Management Context của Actuator:

	```properties
	# Đưa Hawtio ra khỏi tiền tố /actuator
	hawtio.managementContextPath=/
	```

> **⚠️ Cảnh báo:** Khi đưa ra ngoài như thế này, đường dẫn Hawtio sẽ không còn được bảo vệ bởi các cấu hình bảo mật mặc định của Actuator. Bạn sẽ phải tự viết thêm Rule trong `SecurityFilterChain` để chặn người lạ truy cập.

## 3. Mẹo kiểm tra "Bản đồ" Endpoints
Nếu bạn bối rối không biết các công cụ của mình đang nằm ở đâu, hãy truy cập vào **Link gốc của Actuator**:
`http://localhost:55644/inventory-service/actuator`

Server sẽ trả về một tệp JSON liệt kê tất cả các "cửa" đang mở. Bạn chỉ cần tìm từ khóa `"hawtio"` để thấy URL chính xác:

	```json
	{
	  "_links": {
	    "self": { "href": ".../actuator", "templated": false },
	    "hawtio": { "href": ".../actuator/hawtio", "templated": false },
	    "health": { "href": ".../actuator/health", "templated": false }
	  }
	}
	```



---

## 📌 Các lưu ý quan trọng cho dự án của bạn

1.  **Context Path:** Vì bạn đang dùng `/inventory-service/`, hãy đảm bảo `server.servlet.context-path` được giữ nguyên để các link nội bộ của Hawtio không bị vỡ.
2.  **Management Port:** Nếu bạn cấu hình `management.server.port=9090` (khác với port 55644 của App), bạn phải truy cập Hawtio qua port 9090.
3.  **Cấu hình CORS:** Nếu bạn truy cập Hawtio từ một domain khác, hãy nhớ cấu hình CORS cho Actuator để tránh lỗi bị trình duyệt chặn.

### 💡 Lời khuyên:
Việc Hawtio chạy tại `/actuator/hawtio` là **"chuẩn bài" (Standard Practice)**. Bạn nên giữ nguyên cấu hình này để tận dụng hệ thống phân quyền (Role-based Access Control) mà Spring Security cung cấp cho Actuator, giúp bảo vệ các MBeans nhạy cảm khỏi những truy cập trái phép.