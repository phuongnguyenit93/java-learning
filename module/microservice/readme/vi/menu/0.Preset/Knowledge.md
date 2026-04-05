# Kiến thức cơ bản của microservice

## <a id="monilith-vs-microservice">So sánh Kiến trúc: Monolith (Đơn khối) vs. Microservices (Vi-dịch vụ)</a>

Việc lựa chọn giữa **Monolith** và **Microservices** giống như việc chọn giữa một chiếc xe bus đường dài và một đội xe taxi. Cả hai đều có mục đích di chuyển, nhưng cách vận hành và chi phí lại hoàn toàn khác biệt.

[Image of Monolithic vs Microservices architecture diagram]

## 1. Kiến trúc Monolith (Đơn khối)
Toàn bộ mã nguồn (UI, Business Logic, Data Access) được đóng gói và triển khai thành một đơn vị duy nhất.

### ✅ Ưu điểm
* **Đơn giản giai đoạn đầu:** Dễ phát triển, kiểm thử và triển khai (chỉ cần 1 file `.war` hoặc `.jar`).
* **Hiệu suất cao:** Các lời gọi hàm diễn ra trong cùng một bộ nhớ (in-memory), không có độ trễ mạng.
* **Chi phí thấp:** Chỉ cần quản lý một hạ tầng, một pipeline CI/CD và một database duy nhất.

### ❌ Nhược điểm
* **Khó mở rộng (Scalability):** Muốn scale một module bị quá tải, bạn phải nhân bản toàn bộ ứng dụng, gây lãng phí tài nguyên.
* **Độ phức tạp tăng dần:** Codebase lớn khiến việc bảo trì khó khăn. Một lỗi nhỏ có thể làm sập toàn bộ hệ thống.
* **Rào cản công nghệ:** Rất khó để thay đổi stack công nghệ vì mọi thứ đã gắn chặt với nhau.

---

## 2. Kiến trúc Microservices (Vi-dịch vụ)
Ứng dụng được chia thành các dịch vụ nhỏ, độc lập, liên lạc với nhau qua mạng (REST, gRPC, Message Broker).



### ✅ Ưu điểm
* **Mở rộng linh hoạt:** Scale riêng lẻ dịch vụ đang chịu tải cao mà không ảnh hưởng phần còn lại.
* **Độc lập triển khai:** Các team có thể deploy dịch vụ của mình mà không cần chờ đợi lẫn nhau.
* **Khả năng chịu lỗi (Fault Tolerance):** Nếu dịch vụ "Gợi ý" bị lỗi, người dùng vẫn có thể "Mua hàng" bình thường.

### ❌ Nhược điểm
* **Độ phức tạp cực cao:** Thử thách lớn trong việc quản lý giao tiếp, bảo mật và đồng nhất dữ liệu (**Distributed Transactions**).
* **Chi phí vận hành:** Đòi hỏi đầu tư mạnh vào DevOps, Monitoring (Prometheus, Grafana) và Kubernetes.
* **Độ trễ mạng:** Việc gọi qua lại giữa các service tạo ra độ trễ (latency), cần thiết kế tối ưu.

---

## 📊 Bảng so sánh trực quan

| Tiêu chí | Monolith | Microservices |
| :--- | :--- | :--- |
| **Độ phức tạp** | Thấp (Lúc đầu) | Cao (Ngay từ đầu) |
| **Triển khai** | Dễ (Một khối duy nhất) | Khó (Cần CI/CD tự động hóa cao) |
| **Scalability** | Theo chiều dọc (Vertical) | Theo chiều ngang (Horizontal) |
| **Chi phí ban đầu** | Thấp | Cao (Infrastructure & Nhân sự) |
| **Công nghệ** | Bị giới hạn trong một stack | Tự do chọn ngôn ngữ/DB cho từng service |

---

## 🚀 Khi nào nên chọn cái nào?

### Chọn Monolith khi:
* Dự án đang ở giai đoạn khởi nghiệp (**MVP**) cần tốc độ ra mắt nhanh.
* Đội ngũ phát triển nhỏ (dưới 10 người).
* Ứng dụng không có yêu cầu quá cao về tải hoặc tính sẵn sàng cực đoan.

### Chọn Microservices khi:
* Hệ thống có quy mô rất lớn và độ phức tạp nghiệp vụ cao.
* Có nhiều đội ngũ phát triển làm việc song song (ví dụ: Team Order, Team Payment).
* Cần tối ưu chi phí hạ tầng về lâu dài thông qua việc mở rộng từng phần.

```bash
  # Ví dụ tư duy triển khai Monolith (Đơn giản)
  java -jar monolith-app.jar
  
  # Ví dụ tư duy triển khai Microservices (Phức tạp hơn)
  docker-compose up -d discovery-server gateway-service order-service payment-service
```

## <a id="domain-drive-design">Domain-Driven Design (DDD): Nghệ thuật phân xác định Bounded Context</a>

Để chia nhỏ một hệ thống Monolith mà không biến nó thành một "mớ hỗn độn phân tán" (Distributed Monolith), bạn cần nắm vững khái niệm **Bounded Context**.

## 1. Bounded Context là gì?

Trong một hệ thống lớn, một từ ngữ có thể mang ý nghĩa khác nhau tùy vào ngữ cảnh.
* **Ví dụ:** Thực thể `Product` (Sản phẩm).
    * Trong ngữ cảnh **Bán hàng (Sales)**: Nó quan tâm đến giá cả, khuyến mãi, mô tả sản phẩm.
    * Trong ngữ cảnh **Kho vận (Inventory)**: Nó quan tâm đến số lượng tồn kho, kích thước, khối lượng để đóng gói.
    * Trong ngữ cảnh **Giao hàng (Shipping)**: Nó quan tâm đến địa chỉ người nhận và đơn vị vận chuyển.

**Bounded Context** là một ranh giới logic mà bên trong đó, một mô hình dữ liệu (Domain Model) có ý nghĩa thống nhất và đặc thù nhất.

## 2. Cách xác định Bounded Context để chia Service

Để xác định ranh giới này, bạn có thể thực hiện theo các bước sau:

### Bước 1: Phân tích Ngôn ngữ chung (Ubiquitous Language)
Hãy nói chuyện với các chuyên gia nghiệp vụ (Product Owner, Stakeholders). Nếu bạn thấy khi nói về một đối tượng mà định nghĩa bắt đầu thay đổi, đó là dấu hiệu của một Bounded Context mới.

### Bước 2: Dựa trên quy trình nghiệp vụ (Business Capability)
Mỗi dịch vụ nên đại diện cho một khả năng kinh doanh cụ thể.
* Context Đặt hàng (Ordering)
* Context Thanh toán (Payment)
* Context Chăm sóc khách hàng (Support)

### Bước 3: Phân tích sự thay đổi (Pivot Point)
Nếu bạn thay đổi logic của module A mà module B cũng phải thay đổi theo và deploy cùng lúc, có nghĩa là ranh giới của bạn đang bị sai (Tight Coupling). Một Bounded Context chuẩn phải có tính **Tự trị (Autonomy)** cao.



## 3. Context Mapping: Cách các Service "nói chuyện"

Sau khi chia nhỏ, các Context cần tương tác với nhau. DDD cung cấp các kiểu quan hệ:

* **Shared Kernel:** Hai service dùng chung một phần nhỏ thư viện hoặc database (Hạn chế dùng trong Microservices).
* **Customer-Supplier:** Một bên cung cấp dữ liệu (Supplier), một bên tiêu thụ (Customer).
* **Anti-Corruption Layer (ACL):** Service A tạo ra một lớp đệm để chuyển đổi dữ liệu từ Service B sang định dạng mình mong muốn, tránh bị ảnh hưởng bởi sự thay đổi của Service B.

## 4. Tại sao DDD giúp tránh phụ thuộc lẫn nhau?

* **Dữ liệu độc lập:** Mỗi Bounded Context sở hữu Database riêng. Không có chuyện Service này "chọc" thẳng vào bảng của Service kia.
* **Giao tiếp qua Event:** Thay vì gọi trực tiếp (Request-Response), các Service giao tiếp qua các sự kiện (Domain Events) như `OrderCreated`, `PaymentConfirmed`. Điều này giúp hệ thống cực kỳ linh hoạt.

---

### 💡 Áp dụng vào dự án java-learning:

Khi bạn thiết kế module Microservices đầu tiên, hãy vẽ sơ đồ các thực thể. Nếu bạn thấy `User` xuất hiện ở khắp nơi với quá nhiều thuộc tính (vừa có password, vừa có địa chỉ ship, vừa có lịch sử mua hàng), hãy tách nó ra:
1. `UserAccount` (trong Identity Context)
2. `CustomerProfile` (trong Order Context)
3. `Recipient` (trong Shipping Context)

```java
   // Ví dụ về một Domain Event đơn giản trong Bounded Context Ordering
   public record OrderCreatedEvent(
       String orderId, 
       LocalDateTime createdAt, 
       BigDecimal totalAmount
   ) {}
```


## <a id="database-per-service"> Chiến lược Database per Service: Nguyên lý và Quản lý dữ liệu phân tán</a>

Trong kiến trúc Microservices, **Database per Service** là nguyên lý tối thượng. Thay vì một "Shared Database" khổng lồ, mỗi dịch vụ sẽ toàn quyền sở hữu và quản lý dữ liệu riêng của nó.



## 1. Tại sao mỗi Service nên có Database riêng?

Việc tách biệt cơ sở dữ liệu giúp hệ thống đạt được các mục tiêu chiến lược:

* **Tính độc lập (Loose Coupling):** Thay đổi cấu trúc bảng (schema) của Service A không bao giờ làm hỏng code của Service B. Các team tự do nâng cấp DB mà không cần "xin phép" các team khác.
* **Khả năng mở rộng (Scalability):** Bạn có thể chọn loại DB phù hợp nhất cho từng bài toán.
  * *Ví dụ:* Service "Đơn hàng" dùng **NoSQL (MongoDB)** để ghi nhanh; Service "Tài chính" dùng **RDBMS (PostgreSQL)** để đảm bảo tính toàn vẹn (ACID).
* **Cô lập lỗi (Fault Isolation):** Nếu DB của Service A bị treo, Service B và C vẫn hoạt động bình thường với dữ liệu của riêng chúng.

## 2. Các kỹ thuật quản lý dữ liệu phân tán

Thách thức lớn nhất khi tách DB là làm sao để dữ liệu vẫn "khớp" với nhau. Dưới đây là 4 kỹ thuật cốt lõi:

### A. Giao tiếp qua API (Quy tắc vàng)
**Tuyệt đối không** cho phép Service A truy cập trực tiếp vào DB của Service B. Mọi yêu cầu lấy dữ liệu phải đi qua:
* **REST API / gRPC:** Gọi trực tiếp để lấy dữ liệu thời gian thực.
* **Message Broker (Kafka/RabbitMQ):** Lấy dữ liệu qua các sự kiện được phát ra.

### B. Saga Pattern (Quản lý giao dịch phân tán)
Thay thế cho cơ chế `BEGIN TRANSACTION` truyền thống bằng một chuỗi các giao dịch địa phương (Local Transactions).



* **Choreography:** Các service tự quan sát Event của nhau để thực hiện bước tiếp theo.
* **Orchestration:** Có một bộ điều phối trung tâm chỉ định luồng công việc. Nếu một bước lỗi, nó sẽ ra lệnh thực hiện **Giao dịch bù (Compensating Transaction)** để hoàn tác dữ liệu ở các bước trước.

### C. CQRS (Command Query Responsibility Segregation)
Tách biệt luồng **Ghi** (Command) và luồng **Đọc** (Query) để tối ưu hiệu năng.
* Dữ liệu từ nhiều service được đồng bộ về một **View Database** chung (Elasticsearch hoặc Redis) để phục vụ các truy vấn phức tạp mà không cần join nhiều API.

### D. API Composition
Cách đơn giản nhất: API Gateway hoặc một service trung gian sẽ gọi đến các service thành phần, lấy dữ liệu về, thực hiện "Join" trong bộ nhớ và trả kết quả cho người dùng.



---

## 📊 Tóm tắt quy tắc thiết kế "Sống còn"

1.  **Dữ liệu là riêng tư:** Service chỉ lộ dữ liệu qua API công khai, giấu kín cấu trúc bảng bên trong.
2.  **Chấp nhận tính nhất quán sau (Eventual Consistency):** Dữ liệu có thể không giống hệt nhau ở mọi DB ngay lập tức, nhưng sẽ đồng bộ sau vài giây thông qua các Event.
3.  **Hạn chế Join phân tán:** Thiết kế ranh giới Service (Bounded Context) tốt để giảm thiểu việc một request phải gọi quá nhiều service khác nhau.

```java
    // Ví dụ tư duy Event-driven để đồng bộ dữ liệu
    @Service
    public class OrderService {
        @Transactional
        public void createOrder(Order order) {
            orderRepository.save(order);
            // Phát ra event để các service khác (Kho, Thanh toán) cập nhật
            eventPublisher.publish(new OrderCreatedEvent(order.getId()));
        }
    }
```

## <a id="3-gold-rule-microservice"> Bộ ba nguyên lý vàng: SRP, Loose Coupling và High Cohesion</a>

Trong thiết kế phần mềm bền vững, đặc biệt là khi dịch chuyển từ Monolith sang Microservices, đây là ba khái niệm cốt lõi. Nếu vi phạm, hệ thống của bạn sẽ trở thành một "hố đen" bảo trì.


## 1. Single Responsibility Principle (SRP - Đơn nhiệm)
> "Một class/module/service chỉ nên có duy nhất một lý do để thay đổi."

Trong Microservices, SRP áp dụng cho cả phạm vi dịch vụ (Service Boundary).
* **Ví dụ sai:** Một `OrderService` vừa lưu đơn hàng, vừa tính thuế, vừa gửi email, vừa quản lý kho. Khi logic thuế thay đổi, bạn phải deploy lại cả dịch vụ đặt hàng.
* **Ví dụ đúng:** Tách thành `OrderService` (quản lý trạng thái đơn) và `TaxService` (chỉ tính toán thuế).
* **Lợi ích:** Dễ hiểu, dễ kiểm thử và cô lập tác động khi thay đổi code.

## 2. Loose Coupling (Phụ thuộc lỏng lẻo)
> "Sự thay đổi bên trong một component không nên buộc các component khác phải thay đổi theo."

Đây là mục tiêu tối thượng của kiến trúc phân tán. Các thành phần kết nối nhưng không "dính chặt" vào nhau.



* **Cách đạt được:**
  * **Dùng Interface/Contract:** Service A gọi Service B qua API chuẩn (REST/gRPC) thay vì can thiệp vào DB của nhau.
  * **Dùng Message Broker:** Thay vì đợi Service B trả lời (Synchronous), A chỉ cần đẩy tin nhắn vào Kafka (Asynchronous).
* **Lợi ích:** Bạn có thể thay thế công nghệ của Service B (ví dụ từ Java sang Go) mà Service A không hề hay biết.

## 3. High Cohesion (Độ gắn kết cao)
> "Những thứ liên quan mật thiết với nhau thì nên được đặt cạnh nhau."

Cohesion tập trung vào việc tổ chức logic bên trong một đơn vị (class hoặc service).
* **Đặc điểm:** Một service có High Cohesion sẽ chứa các chức năng phục vụ cho cùng một mục đích nghiệp vụ duy nhất.
* **Mối quan hệ với SRP:** Nếu một service thực hiện quá nhiều việc khác nhau (**Low Cohesion**), nó đang vi phạm SRP.
* **Ví dụ:** Trong `PaymentService`, các hàm `validateCard()`, `capturePayment()`, `refund()` có độ gắn kết cao. Nếu thêm hàm `generateShippingLabel()` vào đây, độ gắn kết sẽ giảm xuống.

---

## 📊 Mối liên quan giữa 3 nguyên lý

| Cặp tương tác | Ý nghĩa |
| :--- | :--- |
| **High Cohesion + Loose Coupling** | **Trạng thái lý tưởng.** Logic liên quan ở cùng một chỗ, nhưng các nhóm logic khác nhau thì không phụ thuộc quá mức vào nhau. |
| **SRP + High Cohesion** | Khi tuân thủ SRP, bạn mặc nhiên tạo ra các module có độ gắn kết cao vì chúng chỉ tập trung vào một nhiệm vụ. |
| **Low Cohesion -> Tight Coupling** | Nếu một service làm quá nhiều việc, nó phải kết nối với quá nhiều DB và service khác, dẫn đến bị "trói chặt" vào hệ thống. |

---

## 🛠️ Áp dụng vào thực tế (Java/Spring Boot)

Nếu bạn đang phát triển repository `java-learning`, hãy thực hành như sau:

1.  **SRP:** Tách logic xử lý file và logic gửi mail ra khỏi `Controller` (đưa vào các `@Service` riêng biệt).
2.  **Loose Coupling:** Sử dụng `@Autowired` với **Interface** thay vì Implementation class cụ thể.
3.  **High Cohesion:** Đóng gói các class liên quan đến Security (Filter, Provider, DetailService) vào cùng một package `config.security`.

```java
    // Ví dụ về Loose Coupling qua Interface
    @Service
    public class OrderService {
        // Phụ thuộc vào Interface, không quan tâm nó gửi qua Mail hay SMS
        private final NotificationClient notificationClient; 
    
        public OrderService(NotificationClient notificationClient) {
            this.notificationClient = notificationClient;
        }
    }
```