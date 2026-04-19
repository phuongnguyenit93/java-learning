# Distributed Tracing

## <a id="distributed-tracing"> Distributed Tracing: "Camera giám sát" hành trình request</a>

Khi hệ thống chia nhỏ thành nhiều Microservices, một request từ client có thể đi qua hàng chục dịch vụ khác nhau. **Distributed Tracing** chính là công cụ giúp bạn biết chính xác request bị nghẽn hoặc lỗi ở đâu trong hành trình đó.

### 1. Các khái niệm cốt lõi (Core Concepts)
Để hiểu Distributed Tracing, bạn cần nhớ 3 thuật ngữ quan trọng nhất:

* **Trace:** Toàn bộ hành trình của một request từ đầu đến cuối. Một Trace bao gồm nhiều Span.
* **Span:** Đơn vị công việc nhỏ nhất (ví dụ: một lần gọi API, một lần truy vấn Database). Mỗi Span có ID, tên và thời gian thực hiện.
* **Trace ID & Span ID:** Trace ID dùng chung cho cả hành trình để liên kết các dịch vụ, còn Span ID để định danh từng bước nhỏ.



### 2. Cách hoạt động: Cơ chế "Propagating"
Làm sao Service B biết nó là con của Service A?

1.  **Service A** nhận request và tạo ra một **Trace ID**.
2.  Khi Service A gọi Service B, nó đính kèm Trace ID này vào **HTTP Header** (thường là header `b3` hoặc `traceparent`).
3.  **Service B** đọc header này, giữ nguyên Trace ID và tiếp tục tạo Span mới cho công việc của nó.
4.  Tất cả dịch vụ gửi dữ liệu này về một "nhà kho" tập trung để hiển thị.

### 3. Công cụ trong hệ sinh thái Spring Boot

* **Micrometer Tracing:** (Thay thế Spring Cloud Sleuth từ Spring Boot 3) Thư viện chuẩn để tạo và quản lý Trace/Span.
* **Tracing Backends (Nơi hiển thị):**
    * **Zipkin:** Phổ biến, nhẹ, giao diện đơn giản (biểu đồ Gantt).
    * **Jaeger:** Mạnh mẽ, thường dùng cho hệ thống lớn chạy Kubernetes.
    * **Tempo (Grafana):** Tích hợp mượt mà với Logs và Metrics trên cùng dashboard.

### 4. Cấu hình nhanh với Spring Boot 3 & Zipkin

**Bước 1: Thêm dependency (Maven)**
Bạn cần Micrometer để tạo trace và một "bridge" để gửi dữ liệu tới Zipkin.

```xml
	<dependency>
	    <groupId>io.micrometer</groupId>
	    <artifactId>micrometer-tracing-bridge-brave</artifactId>
	</dependency>
	<dependency>
	    <groupId>io.zipkin.reporter2</groupId>
	    <artifactId>zipkin-reporter-brave</artifactId>
	</dependency>
```

**Bước 2: Cấu hình `application.yml`**

```yaml
	management:
	  tracing:
	    sampling:
	      probability: 1.0 # 1.0 là lấy 100% request (môi trường dev)
	  zipkin:
	    tracing:
	      endpoint: "http://localhost:9411/api/v2/spans"
```

### 5. Tại sao nó quan trọng?

* **Phát hiện nút thắt cổ chai (Bottleneck):** Nhìn thấy ngay Service nào mất 2s trong khi các cái khác chỉ mất 10ms.
* **Dependency Mapping:** Tự động vẽ ra sơ đồ các service đang gọi nhau.
* **Troubleshooting:** Khi có lỗi, bạn chỉ cần lấy **Trace ID** từ log và dán vào Zipkin để xem chính xác nó "chết" ở bước nào.

---
**Kinh nghiệm:** Trong cấu hình log (`logback.xml`), hãy luôn in `traceId` và `spanId`. Khi có sự cố, ID này chính là "chìa khóa" để bạn tái dựng lại toàn bộ hiện trường vụ án trên dashboard tracing.

## <a id="datadog-tracing"> Datadog APM: "Ông trùm" Distributed Tracing & Giám sát hiệu năng</a>

Datadog thực tế là một trong những giải pháp hàng đầu trong mảng Distributed Tracing (thuộc bộ giải pháp **APM - Application Performance Monitoring**). So với Zipkin hay Jaeger, Datadog là một nền tảng trả phí (SaaS) mạnh mẽ và toàn diện hơn rất nhiều.

### 1. Datadog Tracing khác gì với Zipkin/Jaeger?

* **Tính toàn diện:** Zipkin chỉ tập trung vào Trace. Datadog kết nối **Trace + Log + Metrics + Hạ tầng** trên cùng một màn hình.
* **Auto-Instrumentation:** Datadog Agent tự động "tiêm" mã vào ứng dụng Java để thu thập dữ liệu mà không cần viết nhiều code cấu hình.
* **Chi phí:** Zipkin miễn phí (tự vận hành), Datadog tính phí dựa trên số lượng host hoặc lượng dữ liệu.

### 2. Cách triển khai Datadog Tracing trong Spring Boot

Trong Microservices, bạn thường chạy một con **Datadog Agent** (Docker/OS). Ứng dụng sẽ gửi dữ liệu qua Agent này.

**Sử dụng Java Agent (Zero-code instrumentation)**
Bạn không cần thêm dependency vào `pom.xml`. Chỉ cần tải file `dd-java-agent.jar` và chạy ứng dụng với tham số:

	```bash
	java -javaagent:path/to/dd-java-agent.jar \
	     -Ddd.service=order-service \
	     -Ddd.env=dev \
	     -Ddd.trace.agent.url=http://localhost:8126 \
	     -jar my-app.jar
	```

* **Cơ chế:** Java Agent tự động "bọc" các thư viện như Spring Web, Hibernate, Kafka... để tạo Trace/Span và gửi về Agent local (cổng 8126).

### 3. Những tính năng "đáng tiền" của Datadog

* **Service Map:** Tự động vẽ sơ đồ toàn bộ hệ thống. Thấy rõ Service A gọi B qua Kafka, B gọi Database... theo thời gian thực.
* **Error Tracking:** Gom nhóm các lỗi giống nhau, cung cấp Stack Trace chi tiết và tần suất xuất hiện lỗi.
* **Continuous Profiler:** Cho biết chính xác **dòng code nào** đang ngốn nhiều CPU hoặc RAM nhất ngay khi request đang thực thi.



### 4. Bảng so sánh lựa chọn

| Tiêu chí | Zipkin / Jaeger / Micrometer | Datadog APM |
| :--- | :--- | :--- |
| **Giá cả** | Miễn phí (tốn phí vận hành) | Khá đắt |
| **Cài đặt** | Cấu hình thủ công trong code | Tự động hoàn toàn (Zero-code) |
| **Lưu trữ** | Tự quản lý (Elasticsearch/S3) | Datadog quản lý toàn bộ |
| **Phù hợp** | Học tập, dự án nhỏ, thích tự chủ | Doanh nghiệp lớn, hệ thống phức tạp |

---
**Lời khuyên:** Nếu bạn đang làm dự án `java-learning` để hiểu bản chất, hãy bắt đầu với **Zipkin**. Khi làm việc trong các hệ thống doanh nghiệp yêu cầu độ ổn định cực cao và khả năng quan sát (observability) sâu, **Datadog** sẽ là người bạn đồng hành không thể thiếu.

## <a id="trace-id-and-span-id"> Phân biệt Trace ID và Span ID trong Distributed Tracing</a>

Trong Distributed Tracing, Trace ID và Span ID là hai chiếc "chìa khóa" quan trọng nhất để xâu chuỗi hàng nghìn dòng log rời rạc thành một câu chuyện hoàn chỉnh về hành trình của một request.

### 1. Trace ID (Định danh toàn hành trình)
Hãy tưởng tượng một **Trace ID** giống như một **Mã vận đơn (Tracking Number)** của một kiện hàng.

* **Đặc điểm:** Khi khách hàng nhấn nút "Đặt hàng", một Trace ID duy nhất được sinh ra (ví dụ: `abc-123`).
* **Phạm vi:** Nó được giữ nguyên suốt cả hành trình. Dù request đi qua Gateway, sang Order Service, nhảy qua Payment Service, tất cả đều dùng chung một mã `abc-123`.
* **Mục đích:** Giúp bạn tìm được tất cả các log liên quan đến một request cụ thể giữa hàng triệu request khác.

### 2. Span ID (Định danh từng chặng dừng)
Nếu Trace ID là mã vận đơn cho cả quãng đường, thì **Span ID** giống như mã định danh cho từng chặng (ví dụ: nhập kho, vận chuyển, giao hàng).

* **Đặc điểm:** Mỗi khi request thực hiện một công việc cụ thể (gọi API, truy vấn DB, đọc file), một Span ID mới sẽ được sinh ra.
* **Cấu trúc cha-con:** Span ID có tính phân cấp. Một chặng lớn (**Parent Span**) có thể chứa nhiều chặng nhỏ (**Child Spans**).
* **Mục đích:** Giúp bạn biết chính xác bước nào trong hệ thống đang chạy chậm hoặc bị lỗi.

---

### 3. Mối quan hệ giữa Trace ID và Span ID

Dưới đây là sơ đồ logic về cách chúng phối hợp:

* **Trace (Hành trình tổng):** `Trace ID = 69dd1766...`
  * **Chặng 1 (Gửi Order):** `Span ID = A`
  * **Chặng 2 (Kiểm tra kho):** `Span ID = B` (là con của A)
  * **Chặng 3 (Thanh toán):** `Span ID = C` (là con của A)

---

### 4. Bảng so sánh nhanh

| Đặc điểm | Trace ID | Span ID |
| :--- | :--- | :--- |
| **Số lượng** | Chỉ có 1 duy nhất cho mỗi request. | Có nhiều cái, sinh ra theo từng đơn vị công việc. |
| **Giá trị** | Dùng để nhóm (Group) dữ liệu lại. | Dùng để đo thời gian (Duration) của từng bước. |
| **Câu hỏi trả lời** | "Chuyện gì đã xảy ra với request của User X?" | "Tại sao bước gọi Database lại mất tới 2 giây?" |

---

### 5. Ví dụ thực tế từ Zipkin
Trong giao diện Zipkin khi bạn thực hiện truy vết:

1.  Dòng trên cùng có mã dài (ví dụ: `69dd1766361cc99fba9ccc12a9e1976b`) chính là **Trace ID**.
2.  Mỗi thanh ngang màu xanh bên dưới (như `authenticate`, `http get`) tương ứng với một **Span ID** riêng biệt.
3.  Khi bạn click vào một thanh xanh, bạn sẽ thấy thông tin chi tiết của chặng đó (Span) nhưng vẫn thấy nó thuộc về hành trình chung (Trace).

## <a id="custom-span"> Hướng dẫn tạo Custom Span trong Spring Boot 3 (Micrometer Tracing)</a>

Mặc dù các thư viện (như Spring Web, Feign, Hibernate) đã tự động tạo Span cho các sự kiện mặc định, nhưng đôi khi bạn muốn "soi" kỹ hơn vào một đoạn code xử lý phức tạp (vòng lặp nặng, thuật toán quan trọng). Lúc đó, bạn cần sử dụng **Custom Span**.

---

### Cách 1: Sử dụng `@Observed` (Khai báo - Hiện đại)
Đây là cách sạch nhất, sử dụng Annotation để bọc một phương thức lại thành một Span với tên tùy chỉnh.

**1. Cấu hình Bean (Nếu chưa có):**
```java
  @Bean
  ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
      return new ObservedAspect(observationRegistry);
  }
```

**2. Sử dụng trong Service:**
```java
  @Service
  public class OrderService {
  
          @Observed(name = "order.processing.logic", contextualName = "calculating-discount")
          public void processOrder() {
              // Logic tính toán giảm giá sẽ hiện lên Zipkin với tên "calculating-discount"
          }
      }
```

---

### Cách 2: Sử dụng `Tracer` (Thủ công - Linh hoạt)
Dùng khi bạn muốn tạo Span cho một vài dòng code nhất định thay vì cả một phương thức.

```java
	import io.micrometer.tracing.Span;
	import io.micrometer.tracing.Tracer;
	import org.springframework.beans.factory.annotation.Autowired;

	@Service
	public class PaymentService {

	    @Autowired
	    private Tracer tracer; 

	    public void handlePayment() {
	        // 1. Tạo và bắt đầu một Span mới
	        Span newSpan = this.tracer.nextSpan().name("call-external-bank-api");
	        
	        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
	            // 2. Thêm các thông tin tùy chỉnh (Tags)
	            newSpan.tag("bank.name", "Vietcombank");
	            
	            // Logic xử lý của bạn
	            System.out.println("Đang kết nối tới ngân hàng...");
	            Thread.sleep(500); 

	        } catch (Exception e) {
	            newSpan.error(e); // Ghi nhận lỗi nếu có
	        } finally {
	            newSpan.end(); // QUAN TRỌNG: Luôn gọi end() để gửi dữ liệu đi
	        }
	    }
	}
```

---

### Tại sao bạn nên dùng Custom Span?

* **Phân tích hiệu năng chi tiết:** Thay vì chỉ biết tổng thời gian 1s, bạn sẽ thấy rõ: `validate-user` (10ms), `calculate-tax` (800ms), `save-db` (190ms).
* **Gắn thêm dữ liệu (Tagging):** Đính kèm thông tin như `order.id`, `customer.type`. Bạn có thể tìm kiếm tất cả các Trace liên quan đến ID đó trên Zipkin.
* **Dễ đọc biểu đồ:** Các Custom Name giúp bạn hiểu ngay đoạn code đang làm gì khi nhìn vào biểu đồ "thác nước" (waterfall) mà không cần lục lại mã nguồn.

---
**Lưu ý cực kỳ quan trọng:** Khi dùng `Tracer` thủ công, hãy luôn sử dụng khối **try-finally** và gọi `.end()`. Nếu quên gọi `.end()`, dữ liệu sẽ bị "rò rỉ" và không bao giờ xuất hiện trên hệ thống giám sát.
