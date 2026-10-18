# Cách add Bean custom và JMX

## <a id="add-business-bean">Cách hiển thị Business Bean trên Hawtio/JMX</a>

Câu hỏi của bạn rất thực tế. Có một sự hiểu lầm phổ biến: Không phải cứ là Bean của Spring thì mặc định sẽ hiện ra trong cây JMX của Hawtio.

Mặc định, Spring Boot chỉ đăng ký các Bean thuộc về hệ thống quản trị (Actuator Endpoints) lên JMX. Các Bean nghiệp vụ của bạn (như `ProductService`, `OrderRepository`...) sẽ không tự động xuất hiện trừ khi bạn "mời" chúng lên.

---

### 1. Sử dụng Annotation @ManagedResource (Cách chuẩn JMX)
Đây là cách để biến một Bean bình thường thành một MBean (Managed Bean) thực thụ. Lúc này, Hawtio sẽ nhận diện được nó và cho phép bạn xem các Attributes (biến) hoặc chạy các Operations (hàm).

```java
	import org.springframework.jmx.export.annotation.ManagedAttribute;
	import org.springframework.jmx.export.annotation.ManagedOperation;
	import org.springframework.jmx.export.annotation.ManagedResource;
	import org.springframework.stereotype.Service;

	@Service
	@ManagedResource(objectName = "com.inventory:type=Service,name=InventoryService")
	public class InventoryService {

	    private int stockCount = 100;

	    @ManagedAttribute // Cho phép Hawtio xem và sửa giá trị này
	    public int getStockCount() {
	        return stockCount;
	    }

	    @ManagedAttribute
	    public void setStockCount(int stockCount) {
	        this.stockCount = stockCount;
	    }

	    @ManagedOperation // Cho phép Hawtio nhấn nút "Execute" để chạy hàm này
	    public void resetInventory() {
	        this.stockCount = 0;
	    }
	}
```
**Kết quả:** Bạn vào tab JMX trong Hawtio, sẽ thấy một thư mục mới tên là `com.inventory`. Trong đó có `InventoryService` với đầy đủ nút bấm và ô nhập liệu.

---

### 2. Bật tính năng Export tất cả Bean (Chỉ nên dùng để Debug)
Nếu bạn muốn Spring "đẩy" toàn bộ Bean hiện có lên JMX, bạn có thể cấu hình `MBeanExporter`. Tuy nhiên, JMX sẽ trở nên rất hỗn loạn vì có hàng trăm Bean hệ thống.

```java
	@Bean
	public MBeanExporter exporter(Map<String, Object> beansToExport) {
	    MBeanExporter exporter = new MBeanExporter();
	    exporter.setBeans(beansToExport); // Export toàn bộ map các beans
	    return exporter;
	}
```

---

### 3. Kiểm tra lại cấu hình spring.jmx
Hãy đảm bảo bạn không vô tình tắt tính năng JMX của Spring Boot trong file `application.properties`:

```properties
	# Phải là true (mặc định là true)
	spring.jmx.enabled=true

	# Nếu bạn muốn các endpoint của Actuator cũng xuất hiện trong JMX
	management.endpoints.jmx.exposure.include=*
```

---

### Tóm lại:
* **Hawtio/JMX** không phải là nơi để "liệt kê danh sách Bean" thuần túy (việc đó Spring Boot Admin làm tốt hơn qua tab Beans).
* **JMX là nơi để quản trị.** Vì vậy, Spring chỉ hiện những Bean nào được đánh dấu là "Cần được quản trị" thông qua `@ManagedResource`.

## <a id="managed-bean-by-spring-and-jmx">Phân biệt Cấp độ Quản lý Bean: Spring vs JMX</a>

Việc một Bean được "Managed" (Quản lý) có hai cấp độ ý nghĩa khác nhau hoàn toàn: một là đối với Spring Framework (nội tại hệ thống) và hai là đối với JMX/Hawtio (quản trị bên ngoài).

### 1. Spring Managed (Ý nghĩa sống còn của ứng dụng)
Khi bạn đánh dấu `@Service`, `@Component`, Spring coi đó là một Bean được nó quản lý. Ý nghĩa của việc này là:

* **Dependency Injection (DI):** Spring tự động "ship" các Bean khác vào cho nó. Bạn không cần `new`.
* **Lifecycle:** Spring lo từ lúc sinh ra (`@PostConstruct`) đến lúc chết đi (`@PreDestroy`).
* **AOP (Aspect Oriented Programming):** Spring có thể bọc Bean của bạn lại để làm những việc như: tự động mở Transaction (`@Transactional`), bắt lỗi, hay Log.

> **Lưu ý:** Nếu không được Spring Managed, bạn phải tự `new` thủ công, và các tính năng như `@Autowired` hay `@Transactional` sẽ vô tác dụng.

### 2. JMX Managed (Ý nghĩa về Quản trị & Giám sát)
Đây là cấp độ mà bạn đang thắc mắc khi dùng Hawtio. Khi bạn thêm `@ManagedResource`, bạn đang khai báo: *"Tôi muốn phơi bày Bean này ra ngoài cửa sổ để người quản trị có thể sờ vào"*.

* **A. Khả năng "Nội soi" dữ liệu (Visibility):** Bạn có thể xem giá trị thực tế của các biến (fields) ngay trên Hawtio mà không cần in Log hay Debug.
* **B. Khả năng "Điều khiển nóng" (Hot Control):** Giúp bạn thay đổi các cấu hình (ví dụ: tắt/mở một tính năng) ngay lập tức trên Hawtio mà không cần Build lại code hay Restart server.
* **C. Khả năng "Kích hoạt thủ công" (Manual Trigger):** Cho phép bạn chủ động nhấn nút **Execute** trên Hawtio để chạy một hàm (ví dụ: Batch Job) bất cứ lúc nào.

---

### Bảng so sánh nhanh

| Đặc điểm | Chỉ Spring Managed | Thêm JMX Managed (Hawtio thấy) |
| :--- | :--- | :--- |
| **App chạy được không?** | Có | Có |
| **DI / AOP hoạt động?** | Có | Có |
| **Xem được biến từ ngoài?** | Không | Có |
| **Gọi hàm từ trình duyệt?** | Không | Có |
| **Phù hợp cho ai?** | Lập trình viên viết code | Quản trị viên / Dev đang fix lỗi |

---

### Tóm lại:
* **Spring Managed** là để code của bạn chạy đúng.
* **JMX Managed** là để bạn có **quyền lực điều khiển** code đó từ bên ngoài mà không cần chạm vào mã nguồn hay khởi động lại máy chủ.