# Lộ trình Microservice

## <a id="microservice-roadmap"> Lộ trình: Chuyển đổi từ Monolith sang Microservices</a>

Việc chuyển từ phát triển ứng dụng **Monolith** (đơn khối) sang **Microservices** là một bước tiến lớn về tư duy hệ thống. Lộ trình này tập trung vào việc tận dụng thế mạnh Java/Spring Boot để xây dựng hệ thống phân tán.

## Giai đoạn 1: Tư duy thiết kế (Design Principles)
Trước khi viết code, bạn cần hiểu "tại sao" và "chia như thế nào".
* **Monolith vs Microservices:** Hiểu ưu và nhược điểm (độ phức tạp, chi phí vận hành, scalability).
* **Domain-Driven Design (DDD):** Cách xác định *Bounded Context* để chia nhỏ các service hợp lý, tránh việc các service quá phụ thuộc vào nhau (Tight Coupling).
* **Database per Service:** Tại sao mỗi service nên có DB riêng và cách quản lý dữ liệu phân tán để đảm bảo tính độc lập.

## Giai đoạn 2: Giao tiếp giữa các Service (Communication)
Trong Microservices, các thành phần phải "nói chuyện" với nhau hiệu quả và tin cậy.

* **Synchronous (Đồng bộ):** Sử dụng **REST** (OpenFeign) hoặc **gRPC** cho các lời gọi cần phản hồi ngay lập tức.
* **Asynchronous (Bất đồng bộ):** Sử dụng Message Broker như **RabbitMQ** hoặc **Kafka** để giao tiếp qua kiến trúc hướng sự kiện (*Event-driven architecture*).
* **API Gateway:** Tìm hiểu về **Spring Cloud Gateway**. Đây là cửa ngõ duy nhất cho client, xử lý Routing, Authentication và Rate Limiting.

## Giai đoạn 3: Quản trị và Điều phối (Service Governance)
Khi có hàng chục service, việc quản trị thủ công là bất khả thi.
* **Service Discovery:** Sử dụng **Netflix Eureka** để các service tự tìm thấy nhau mà không cần hard-code địa chỉ IP.
* **Externalized Configuration:** Dùng **Spring Cloud Config** để quản lý tập trung file cấu hình (`application.yml`) cho tất cả service.
* **Resilience (Khả năng chịu lỗi):** Triển khai **Circuit Breaker** với **Resilience4j**. Tránh việc một service sập kéo theo cả hệ thống sập (Cascading failure).


## Giai đoạn 4: Dữ liệu và Giao dịch phân tán (Distributed Data)
Đây là phần thử thách nhất trong kiến trúc Microservices.
* **Saga Pattern:** Cách quản lý giao dịch (*Transaction*) đi qua nhiều service (ví dụ: Đặt hàng -> Thanh toán -> Trừ kho).
* **CQRS & Event Sourcing:** Tách biệt giữa luồng đọc và luồng ghi dữ liệu để tối ưu hiệu năng và khả năng mở rộng.

## Giai đoạn 5: Observability & DevOps (Giám sát & Triển khai)
Hệ thống phân tán cần các công cụ giám sát đặc thù để theo dõi luồng dữ liệu.
* **Distributed Tracing:** Sử dụng **Micrometer Tracing** (thay thế Zipkin/Sleuth) để theo dõi một request đi qua những service nào.
* **Log Management:** Tập trung log với **ELK Stack** (Elasticsearch, Logstash, Kibana).
* **Containerization:** Đóng gói các service với **Docker** (tận dụng kỹ năng *multi-stage build* bạn đã có).
* **Orchestration:** Sử dụng **Kubernetes (K8s)** để điều phối, tự động scale và quản lý container ở quy mô lớn.

  ```yaml
  # Ví dụ cấu hình Docker Compose cơ bản cho Microservices
  services:
    eureka-server:
      image: java-learning/eureka-server
      ports:
        - "8761:8761"
    
    api-gateway:
      image: java-learning/api-gateway
      environment:
        - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      ports:
        - "8080:8080"
  ```

---

### 💡 Lời khuyên cho dự án java-learning:
Khi thực hiện giai đoạn 2 (Communication), bạn hãy kết hợp với kiến thức về **Virtual Threads** đã học. Việc sử dụng Virtual Threads để xử lý các lời gọi I/O giữa các service qua OpenFeign sẽ giúp hệ thống Microservices của bạn chịu tải cực tốt mà không tốn nhiều tài nguyên RAM.