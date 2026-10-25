# Resilience4j và các khái niệm

## <a id="resilience-4j"> Resilience4j: "Cầu chì" bảo vệ hệ thống Microservices</a>

Resilience4j chính là **Circuit Breaker** giúp hệ thống của bạn không bị sụp đổ dây chuyền. Nếu một service con bị chậm hoặc lỗi, Resilience4j sẽ đứng ra bảo vệ các service còn lại, giống như cầu chì tự ngắt để bảo vệ cả ngôi nhà khi có thiết bị bị chập điện.

### 1. 3 Trạng thái cốt lõi của Circuit Breaker
Resilience4j quản lý các lời gọi service qua 3 trạng thái:

* **CLOSED (Đóng):** Trạng thái bình thường. Mọi request đều được cho qua. Resilience4j theo dõi tỉ lệ lỗi trong giai đoạn này.
* **OPEN (Mở):** Khi tỉ lệ lỗi vượt ngưỡng (ví dụ > 50%), mạch sẽ "ngắt". Mọi request sẽ bị chặn lại ngay lập tức và trả về thông báo lỗi hoặc chạy vào hàm dự phòng (**Fallback**). Điều này giúp service đang lỗi có thời gian để hồi phục.
* **HALF-OPEN (Mở một nửa):** Sau một khoảng thời gian chờ, mạch cho phép một vài request đi qua để "thăm dò". Nếu thành công, mạch đóng lại (**CLOSED**). Nếu vẫn lỗi, mạch quay lại trạng thái **OPEN**.

### 2. Các "vũ khí" hỗ trợ khác
Ngoài Circuit Breaker, thư viện này còn cung cấp:

* **Retry (Thử lại):** Tự động gọi lại request nếu thất bại (hữu ích khi mạng chập chờn).
* **Rate Limiter (Chặn dòng):** Giới hạn số lượng request trong một khoảng thời gian để tránh bị spam.
* **Time Limiter (Giới hạn thời gian):** Tự ngắt nếu service phản hồi quá lâu, giúp giải phóng Thread (quan trọng khi dùng Virtual Threads).
* **Bulkhead (Ngăn khoang):** Chia Thread pool riêng cho từng service. Nếu Service A bị treo, nó chỉ chiếm dụng tài nguyên của "khoang" đó, không ảnh hưởng đến Service B.

### 3. Cách triển khai trong Spring Boot
Giả sử từ `order-service` gọi sang `inventory-service`. Nếu kho hàng sập, chúng ta sẽ trả về thông báo mặc định.

**Cấu hình trong `application.yml`:**

```yaml
	resilience4j:
	  circuitbreaker:
	    instances:
	      inventoryService:
	        failure-rate-threshold: 50 # Ngắt mạch nếu > 50% lỗi
	        wait-duration-in-open-state: 10s # Đợi 10 giây trước khi thử lại
```

**Sử dụng trong Code Java:**

```java
	@CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackInventory")
	public String callInventory() {
	    // Logic gọi sang service khác qua RestTemplate hoặc Feign
	    return restTemplate.getForObject("http://inventory-service/check", String.class);
	}

	// Hàm dự phòng khi mạch ngắt hoặc service kia sập
	public String fallbackInventory(Exception e) {
	    return "Kho hàng hiện tại không phản hồi, vui lòng thử lại sau!";
	}
```

### 4. Giải quyết nỗi lo "Sập hệ thống"
Quay lại vấn đề Config Server hoặc các service con bị sập:

1.  **Kết hợp Retry:** Khi khởi động, service con không "chết" ngay mà sẽ thử gọi lại Config Server nhiều lần theo cấu hình.
2.  **Trải nghiệm người dùng:** Khi một service sập

## <a id="resilience-4j-config"> Kết hợp Resilience4j: Bulkhead và Retry trong Microservices</a>

Để minh họa sức mạnh của Resilience4j trong việc bảo vệ hệ thống (đặc biệt khi bạn đang làm việc với Virtual Threads và Database trong dự án `java-learning`), chúng ta sẽ thiết lập một bộ "phanh" cho hệ thống.

Dưới đây là cách kết hợp **Bulkhead** (Ngăn khoang) và **Retry** (Thử lại) để tránh làm "cháy" Database khi có quá nhiều request cùng lúc.

### 1. Cấu hình trong application.yml
Giả sử bạn có một logic truy vấn Database nặng. Bạn không muốn quá 5 Virtual Threads cùng đâm vào DB này một lúc để tránh treo kết nối.

	```yaml
	resilience4j:
	  bulkhead:
	    instances:
	      dbOperation:
	        max-concurrent-calls: 5 # Tối đa 5 luồng chạy cùng lúc
	        max-wait-duration: 500ms # Nếu luồng thứ 6 đến, bắt nó đợi 0.5s, quá 0.5s thì báo lỗi ngay
	  
	  retry:
	    instances:
	      dbOperation:
	        max-attempts: 3 # Nếu lỗi (do mạng hoặc DB bận), thử lại tối đa 3 lần
	        wait-duration: 2s # Mỗi lần thử lại cách nhau 2 giây
	```

### 2. Triển khai trong Code (Service Layer)
Bạn chỉ cần bọc các Annotation này đè lên phương thức xử lý logic:

	```java
	@Service
	public class OrderService {

	    @Bulkhead(name = "dbOperation", fallbackMethod = "handleBulkheadFull")
	    @Retry(name = "dbOperation")
	    public String processLargeOrder(Long orderId) {
	        // Giả sử đây là logic gọi Database hoặc Thread nặng
	        System.out.println("Đang xử lý đơn hàng: " + orderId + " bằng " + Thread.currentThread());
	        return "Đơn hàng " + orderId + " đã xử lý thành công!";
	    }

	    // Hàm này chạy khi có quá nhiều người cùng vào (Bulkhead đầy)
	    public String handleBulkheadFull(Long orderId, Exception e) {
	        return "Hệ thống đang quá tải, đơn hàng " + orderId + " sẽ được xử lý sau ít phút.";
	    }
	}
	```

### 3. Tại sao cái này "hỗ trợ" cực tốt cho bạn?

* **Chống tràn Thread:** Vì bạn đang học về **Java 21 Virtual Threads**, đặc điểm của chúng là tạo ra hàng triệu luồng rất dễ dàng. Nếu không dùng Bulkhead, hàng triệu luồng đó sẽ cùng lúc "tấn công" Database, khiến hệ thống sập ngay lập tức.
* **Tăng độ tin cậy:** Nếu Config Server hoặc một Service khác bị khởi động chậm, cơ chế **Retry** sẽ giúp ứng dụng tự "kiên nhẫn" thử lại thay vì trả về lỗi 500 ngay lập tức.
* **Quan sát (Observability):** Resilience4j tích hợp rất tốt với **Micrometer và Grafana**. Bạn có thể vẽ biểu đồ xem mạch đang Đóng hay Mở, tỉ lệ lỗi là bao nhiêu ngay trên Dashboard.

## <a id="semaphore-and-resilience-4j"> Phân biệt Java Semaphore vs. Resilience4j Bulkhead </a>

Câu hỏi của bạn cực kỳ sắc sảo! Khi làm việc với Java Concurrency (đặc biệt là Thread và Virtual Thread), việc nhầm lẫn giữa Resilience4j Bulkhead và Java Semaphore là rất bình thường vì cả hai đều dùng để "giới hạn số lượng luồng truy cập đồng thời".

Tuy nhiên, chúng khác nhau về mục đích và tầng áp dụng. Một cái là "công cụ cơ bản" (**Low-level**), một cái là "giải pháp hệ thống" (**High-level**).

### 1. So sánh chi tiết: Semaphore vs. Bulkhead

| Đặc điểm | Java Semaphore (JUC) | Resilience4j Bulkhead |
| :--- | :--- | :--- |
| **Bản chất** | Cấu trúc dữ liệu điều khiển luồng trong bộ nhớ (JVM). | Một Design Pattern cho hệ thống phân tán (Microservices). |
| **Cơ chế** | Dựa trên việc "cấp phép" (Permits). Hết slot thì luồng phải đợi (Block). | Giới hạn slot, đi kèm cơ chế hủy bỏ (rejection) và xử lý dự phòng. |
| **Phục hồi** | Không có sẵn cơ chế Fallback (phải tự viết try-catch). | Có sẵn `fallbackMethod` để trả về kết quả mặc định ngay. |
| **Giám sát** | Khó theo dõi chỉ số (metrics) từ bên ngoài. | Tích hợp sẵn Micrometer, đẩy data lên Grafana cực dễ. |
| **Linh hoạt** | Cấu hình cứng trong code (Hard-coded). | Có thể thay đổi cấu hình từ xa qua Spring Cloud Config. |

### 2. Khi nào dùng cái nào?

* **Dùng Semaphore khi:**
    * Bạn đang xử lý logic bên trong một ứng dụng đơn lẻ.
    * Cần kiểm soát việc truy cập vào tài nguyên nội bộ rất nhanh (biến dùng chung, mảng).
    * Muốn các luồng chờ đợi mãi mãi cho đến khi có tài nguyên.

* **Dùng Resilience4j Bulkhead khi:**
    * Bạn đang gọi một Service bên ngoài hoặc một Database.
    * Muốn hệ thống có tính kháng cự: Nếu quá tải, thay vì treo luồng, bạn muốn từ chối bớt request (**Fail-fast**).
    * Cần thống kê báo cáo về tình trạng quá tải của hệ thống.

### 3. Mối liên hệ thú vị (Sự thật ngầm hiểu)
Thực tế, Resilience4j Bulkhead (loại `SemaphoreBulkhead`) bên dưới lớp vỏ bọc chính là sử dụng **Java Semaphore** để quản lý các slot!

> **Ví dụ so sánh:**
> * **Semaphore** là cái "đinh vít".
> * **Resilience4j Bulkhead** là cái "máy khoan" hoàn chỉnh đã lắp sẵn đinh vít, có thêm đèn báo hiệu, chế độ tự ngắt khi nóng máy và tay cầm chống rung.

### 4. Áp dụng vào dự án java-learning của bạn
Vì bạn đang học về **Virtual Threads**, việc dùng Semaphore truyền thống đôi khi khiến hàng triệu Virtual Threads bị chặn (blocked) và nằm chờ trong bộ nhớ. Dù không tốn tài nguyên CPU nhưng chúng vẫn chiếm RAM.

Nếu dùng **Bulkhead**, bạn có thể cấu hình `max-wait-duration: 0`. Nghĩa là nếu không còn slot, Virtual Thread đó sẽ thực thi hàm fallback ngay lập tức và kết thúc vòng đời, giúp giải phóng bộ nhớ cực nhanh. Đây chính là tư duy **Cloud-Native**.

## <a id="ignore-noretry-and-fallback"> Phân biệt Ignore/NoRetry vs. Fallback Mechanism</a>

Bạn đã nắm bắt rất chính xác bản chất của vấn đề! Đây là "điểm mù" mà nhiều lập trình viên Java hay nhầm lẫn. Hãy tưởng tượng `ignoreException` và `noRetryFor` giống như một cái bộ lọc thông tin, chứ không phải là bộ ngắt dòng điện.

### 1. Bản chất của "Sự từ chối" (Ignore/NoRetry)

* **Với Retry (noRetryFor):** Khi gặp lỗi này, nó bảo: "Đừng thử lại làm gì cho mệt, ném lỗi ra ngoài ngay lập tức!".
* **Với Circuit Breaker (ignoreExceptions):** Khi gặp lỗi này, nó bảo: "Đừng đếm lỗi này vào tỉ lệ thất bại, mạch vẫn cứ coi như đang **Khỏe Mạnh (CLOSED)** nhé!".

> **Mấu chốt:** Dù bị "từ chối" hay "bỏ qua", bản chất dòng code đó vẫn ném ra một **Exception**. Và khi có Exception văng ra khỏi method chính, các cơ chế Fallback sẽ ngay lập tức "nhảy vào ứng cứu" theo mặc định.

### 2. Luồng đi thực tế (Workflow) khi dùng Ignore
Giả sử bạn gặp lỗi **422 (Unprocessable Entity - Hết hàng)** và đã cấu hình ignore:

1.  `placeOrder()` ném ra `FeignClientException(422)`.
2.  **Retry** thấy lỗi 422 nằm trong `noRetryFor` -> Dừng ngay, không thử lại lần 2.
3.  **Circuit Breaker** thấy lỗi 422 nằm trong `ignoreExceptions` -> Không tăng biến đếm lỗi (Mạch vẫn xanh).
4.  **Tuy nhiên**, vì vẫn có Exception đang "bay", Spring AOP sẽ bắt lấy nó và chuyển hướng vào hàm **Fallback** hoặc `@Recover`.

---

### 3. Cách xử lý: "Lỗi này thì đừng chạy Fallback"

Nếu bạn muốn lỗi 422 thì trả về lỗi 422 luôn cho khách hàng (để họ biết là hết hàng), còn lỗi 500 mới chạy Fallback (trả về giá trị mặc định), bạn có thể áp dụng:

**Kiểm tra loại lỗi trong hàm Fallback (Khuyên dùng)**
Đây là cách minh bạch và dễ kiểm soát nhất:

	```java
	public boolean handleInStockFallback(String skuCode, Integer quantity, Throwable t) {
	    // Nếu là lỗi nghiệp vụ (đã ignore), hãy ném nó đi tiếp để GlobalExceptionHandler xử lý
	    if (t instanceof FeignClientException || t instanceof FeignException.BadRequest) {
	        throw (RuntimeException) t; 
	    }
	    
	    // Chỉ những lỗi hệ thống thực sự (Timeout, 500) mới trả về giá trị fallback mặc định
	    log.error("Hệ thống gặp sự cố kỹ thuật, tạm thời coi như không có hàng để an toàn");
	    return false;
	}
	```

---

### 4. Tóm lại sự khác biệt

| Tính năng | Mục tiêu bảo vệ | Ý nghĩa của "Ignore" |
| :--- | :--- | :--- |
| **Ignore/NoRetry** | **Hệ thống (Infrastructure)** | Không làm treo thread, không làm hỏng mạch, không phí tài nguyên thử lại. |
| **Fallback/Recover** | **Người dùng (Experience)** | Quyết định xem khách hàng sẽ thấy cái gì khi có lỗi (Giá trị giả hay thông báo lỗi). |

---
**Lời khuyên:** Luôn để các lỗi **Business Exception** (như Sai mật khẩu, Hết hàng, Không đủ quyền) vào danh sách `ignore`, vì chúng là lỗi của người dùng hoặc logic nghiệp vụ, không phải lỗi của hệ thống hạ tầng.


## <a id="3-state-of-circuit-breaker"> 3 Trạng thái cốt lõi của Circuit Breaker (Bộ ngắt mạch)</a>

Ba trạng thái này chính là "linh hồn" của mô hình Circuit Breaker. Nó hoạt động y hệt như cái cầu dao điện trong nhà bạn: Khi có chập điện (lỗi hệ thống), cầu dao sẽ tự ngắt để bảo vệ các thiết bị khác không bị cháy theo.

### 1. Trạng thái CLOSED (Mạch đóng - Bình thường)
Đây là trạng thái lý tưởng nhất khi hệ thống đang hoạt động ổn định.

* **Cơ chế:** Mọi request đều được phép đi xuyên qua mạch để gọi đến Service đích.
* **Theo dõi:** Resilience4j sẽ âm thầm đếm số lượng cuộc gọi thành công và thất bại.
* **Chuyển trạng thái:** Nếu tỉ lệ lỗi (ví dụ: > 50%) vượt ngưỡng cấu hình trong một khoảng thời gian, mạch sẽ tự động "nhảy" sang trạng thái **OPEN**.

### 2. Trạng thái OPEN (Mạch ngắt - Đang có sự cố)
Lúc này, hệ thống coi như Service đích "đã chết" hoặc đang cực kỳ quá tải.

* **Cơ chế:** Mạch ngắt hoàn toàn. Mọi request gửi đến sẽ bị chặn đứng ngay lập tức (**Fast-fail**) và ném ra lỗi `CallNotPermittedException`.
* **Lợi ích:** Tránh việc người dùng phải chờ Timeout quá lâu và giúp Service đích có thời gian để "hồi sức".
* **Chuyển trạng thái:** Mạch giữ trạng thái này trong một khoảng thời gian chờ (ví dụ: 30 giây), sau đó tự chuyển sang **HALF-OPEN**.

### 3. Trạng thái HALF-OPEN (Mạch mở một nửa - Thăm dò)
Đây là trạng thái "thử nghiệm" để xem Service đích đã thực sự khỏe lại chưa.

* **Cơ chế:** Mạch cho phép một lượng nhỏ request (ví dụ: 10 cuộc gọi) đi qua.
* **Kết quả:**
  * **Nếu đa số thành công:** Hệ thống đã ổn, mạch quay về **CLOSED**.
  * **Nếu vẫn thất bại:** Lập tức quay lại **OPEN** và tiếp tục đợi thêm một chu kỳ nữa.

---

### Bảng tóm tắt nhanh

| Trạng thái | Request được đi qua? | Mục đích chính |
| :--- | :--- | :--- |
| **CLOSED** | Có (100%) | Hoạt động bình thường và theo dõi tỉ lệ lỗi. |
| **OPEN** | Không (0%) | Ngắt kết nối để bảo vệ hệ thống và báo lỗi nhanh. |
| **HALF-OPEN** | Có (Giới hạn) | Kiểm tra thực tế xem hệ thống đã hồi phục chưa. |

---

### Ví dụ cấu hình thực tế trong YAML

```yaml
	resilience4j:
	  circuitbreaker:
	    instances:
	      inventoryCB:
	        failureRateThreshold: 50       # Nếu lỗi quá 50%
	        waitDurationInOpenState: 10000 # Đợi 10 giây ở trạng thái OPEN
	        slidingWindowSize: 10          # Tính tỉ lệ dựa trên 10 cuộc gọi gần nhất
```

> **Hình tượng hóa:** Hãy tưởng tượng Circuit Breaker như một bác sĩ: **CLOSED** là đang khám bệnh bình thường; **OPEN** là đưa bệnh nhân vào phòng cấp cứu và treo biển "Miễn tiếp khách"; còn **HALF-OPEN** là hé cửa cho vài người vào xem bệnh nhân đã tỉnh hẳn chưa trước khi mở cửa đón khách lại.
## <a id="time-limiter-resilience-4j"> Cấu hình TimeLimiter trong Resilience4j</a>

Trong Resilience4j, việc thiết lập giới hạn thời gian chờ (timeout) thường được xử lý thông qua module **TimeLimiter**. Đây là cơ chế giúp ngăn chặn việc một luồng (thread) bị treo quá lâu khi gọi một service bên ngoài không phản hồi.

### 1. Cấu hình qua file application.yml
Đây là cách phổ biến và dễ quản lý nhất. Bạn có thể định nghĩa các giá trị mặc định hoặc cấu hình riêng cho từng instance.

```yaml
	resilience4j:
	  timelimiter:
	    instances:
	      backendService:
		timeout-duration: 2s # Giới hạn thời gian chờ là 2 giây
		cancel-running-future: true # Hủy thực thi nếu quá thời gian
```

* **timeout-duration:** Khoảng thời gian tối đa để hoàn thành request.
* **cancel-running-future:** Nếu đặt là `true`, khi hết thời gian, Resilience4j sẽ cố gắng ngắt quãng luồng đang chạy.

### 2. Cấu hình bằng Code (Java Config)
Nếu bạn muốn tùy chỉnh linh hoạt hơn, bạn có thể tạo một `TimeLimiterConfig`:

	```java
	TimeLimiterConfig config = TimeLimiterConfig.custom()
	    .timeoutDuration(Duration.ofSeconds(2))
	    .cancelRunningFuture(true)
	    .build();

	TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
	TimeLimiter timeLimiter = registry.timeLimiter("backendService");
	```

### 3. Cách sử dụng trong Service
Sử dụng annotation `@TimeLimiter` để áp dụng cấu hình.

> **Lưu ý:** `@TimeLimiter` hoạt động tốt nhất với các phương thức trả về kiểu không đồng bộ như `CompletableFuture` hoặc các kiểu Reactive (`Mono`, `Flux`).

```java
	@Service
	public class MyService {

	    @TimeLimiter(name = "backendService", fallbackMethod = "fallback")
	    public CompletableFuture<String> doSomething() {
		return CompletableFuture.supplyAsync(() -> {
		    // Giả lập xử lý nặng
		    try { Thread.sleep(3000); } catch (InterruptedException e) {}
		    return "Thành công!";
		});
	    }

	    public CompletableFuture<String> fallback(Throwable t) {
		return CompletableFuture.completedFuture("Hệ thống phản hồi chậm, vui lòng thử lại sau!");
	    }
	}
```

---

### Một số lưu ý quan trọng

* **Kết hợp với Circuit Breaker:** Thông thường, `@TimeLimiter` hay được dùng cùng `@CircuitBreaker`. Nếu timeout xảy ra liên tục, Circuit Breaker sẽ chuyển sang trạng thái **OPEN** để bảo vệ hệ thống.
* **Cấu hình HTTP Client:** Các thư viện như `RestTemplate` hay `WebClient` cũng có cấu hình timeout riêng. Bạn nên để timeout của Client lớn hơn hoặc bằng với `TimeLimiter` để tránh xung đột.
* **Quản lý Thread Pool:** Khi dùng `CompletableFuture`, hãy đảm bảo quản lý Thread Pool hợp lý để tránh tình trạng cạn kiệt tài nguyên (Thread exhaustion) khi có quá nhiều request bị timeout đồng thời.

## <a id="time-limiter-with-async"> Tại sao TimeLimiter yêu cầu phương thức Bất đồng bộ (Async)?</a>

Việc **TimeLimiter** yêu cầu (hoặc khuyến khích mạnh mẽ) sử dụng với `CompletableFuture`, `Mono`, hoặc `Flux` xuất phát từ cách quản lý luồng (**Thread**) trong môi trường Servlet truyền thống.

### 1. Cơ chế "Ngắt" (Interruption) của Java
Trong Java, bạn không thể ép một Thread đang chạy phải dừng lại ngay lập tức một cách an toàn (phương thức `thread.stop()` đã bị khai tử). Bạn chỉ có thể gửi một tín hiệu **Interrupt**.

* **Nếu chạy đồng bộ (Synchronous):** Thread hiện tại sẽ bị "khóa" (block) cho đến khi hàm trả về. Nếu hàm đó đang đợi một I/O (gọi API khác) không hỗ trợ interrupt, TimeLimiter không có cách nào giành lại quyền kiểm soát để báo lỗi timeout.
* **Nếu chạy bất đồng bộ (Asynchronous):** Công việc được đẩy sang một Thread khác. TimeLimiter đứng ở Thread chính để theo dõi thời gian. Khi hết giờ, nó báo lỗi ngay cho người dùng và gửi tín hiệu hủy (`cancel`) tới Thread đang chạy ngầm.

### 2. Tránh hiện tượng "Thread Starvation" (Cạn kiệt luồng)
Hãy tưởng tượng hệ thống có 200 Threads trong Thread Pool:

* **Với đồng bộ:** Nếu timeout là 10s và service bên kia chậm, cả 200 Threads sẽ bị treo cứng để đợi. Người dùng thứ 201 sẽ bị từ chối dù hệ thống không bận xử lý logic nặng.
* **Với Async:** TimeLimiter cho phép phản hồi lỗi về phía Client ngay khi hết timeout, giúp giải phóng tài nguyên và tránh "nghẽn cổ chai" tại các luồng xử lý chính.

---

### 3. So sánh TimeLimiter và Circuit Breaker

| Thành phần | Cơ chế | Đối tượng quản lý |
| :--- | :--- | :--- |
| **Circuit Breaker** | Theo dõi tỷ lệ lỗi/timeout để ngắt mạch. | Bọc quanh lệnh gọi đồng bộ hoặc bất đồng bộ. |
| **TimeLimiter** | Đảm bảo một công việc không chạy quá $X$ giây. | Cần cơ chế để tách việc theo dõi và thực thi (Async). |

---

### 4. Cách hoạt động thực tế trong Resilience4j
Khi bạn dùng `@TimeLimiter`, Resilience4j sẽ tạo ra một "bộ hẹn giờ":
1.  Nếu công việc (`Future`/`Mono`) hoàn thành trước: Mọi thứ bình thường.
2.  Nếu bộ hẹn giờ kêu trước: Resilience4j ném ra `TimeoutException`.

> **Mấu chốt:** Nếu không có Async, Thread hiện tại đang bận xử lý logic, nó không thể tự "phân thân" để vừa chạy vừa tự đếm giờ cho chính mình.

---

### Ví dụ về sự khác biệt

* **SAI (Đồng bộ - Không hiệu quả):**
```java
  @TimeLimiter(name = "backend")
  public String doSync() {
      // Luồng này bị block, TimeLimiter không thể can thiệp hiệu quả
      return restTemplate.getForObject(...); 
  }
```

* **ĐÚNG (Bất đồng bộ):**
```java
  @TimeLimiter(name = "backend")
  public CompletableFuture<String> doAsync() {
      // Tách biệt luồng theo dõi và luồng thực thi
      return CompletableFuture.supplyAsync(() -> remoteCall());
  }
```
