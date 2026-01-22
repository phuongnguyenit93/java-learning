## Apache Kafka

**Apache Kafka** là một nền tảng **event streaming phân tán** được thiết kế để xử lý các luồng dữ liệu thời gian thực với **hiệu năng cao**, **độ trễ thấp** và **khả năng mở rộng lớn**. Kafka thường được sử dụng trong các hệ thống **microservices**, **event-driven architecture** và **real-time data pipeline**.

Kafka hoạt động theo mô hình **publish / subscribe**, trong đó dữ liệu được gửi và nhận thông qua các **topic**. Mỗi topic có thể được chia thành nhiều **partition**, cho phép xử lý song song và mở rộng theo chiều ngang.

### Các khái niệm chính

- **Producer**: Thành phần gửi message vào Kafka topic
- **Consumer**: Thành phần đọc message từ Kafka topic
- **Broker**: Kafka server chịu trách nhiệm lưu trữ và phân phối message
- **Topic**: Kênh логical dùng để phân loại message
- **Partition**: Phân vùng của topic, giúp tăng khả năng song song và mở rộng
- **Consumer Group**: Nhóm consumer cùng đọc một topic, mỗi partition chỉ được xử lý bởi một consumer trong group

### Ưu điểm của Kafka

- High throughput, xử lý lượng lớn message
- Fault-tolerant với cơ chế replication
- Lưu trữ dữ liệu bền vững trên đĩa
- Dễ dàng mở rộng mà không gián đoạn hệ thống
- Hỗ trợ tốt cho kiến trúc bất đồng bộ và event-driven

## Một số thuật ngữ trong Kafka

### Broker
**Broker** là một server Kafka, chịu trách nhiệm:
- Nhận message từ Producer
- Lưu trữ message vào disk
- Phân phối message cho Consumer

Một Kafka cluster bao gồm nhiều broker để đảm bảo **high availability** và **scalability**.

---

### Producer
**Producer** là thành phần gửi dữ liệu (message/event) vào Kafka.
- Producer gửi message đến một **topic**
- Message sẽ được ghi vào một **partition** cụ thể
- Có thể cấu hình cơ chế **acks**, **retry**, **batching** để đảm bảo độ tin cậy

---

### Consumer
**Consumer** là thành phần đọc dữ liệu từ Kafka.
- Consumer subscribe vào một hoặc nhiều topic
- Dữ liệu được đọc theo thứ tự trong từng partition
- Consumer quản lý offset để xác định message đã xử lý
- 4 trạng thái để điều khiển 1 consumer : start, stop, pause, resume.
#### Trạng thái Consumer

- **Start**  
  Khởi động consumer để bắt đầu lắng nghe và xử lý message từ Kafka.  
  Khi start, consumer đọc dữ liệu từ offset đã commit gần nhất.
  Trường hợp chưa có offset hoặc offset đã bị mất do retention, Kafka sẽ sử dụng auto.offset.reset (earliest / latest) để xác định vị trí bắt đầu đọc.


- **Stop**  
  Dừng hoàn toàn consumer và giải phóng tài nguyên.  
  Consumer sẽ ngắt kết nối với Kafka broker và không tiếp tục xử lý message.


- **Pause**  
  Tạm thời dừng việc consume message nhưng **vẫn giữ kết nối** với Kafka.  
  Offset không thay đổi trong thời gian pause.


- **Resume**  
  Tiếp tục consume message từ vị trí offset đã dừng trước đó.  
  Consumer không bị rebalance lại khi resume.

> Việc sử dụng **pause / resume** giúp kiểm soát luồng dữ liệu tốt hơn trong các trường hợp bảo trì, quá tải hoặc xử lý sự cố tạm thời.

---

### Topic
**Topic** là một kênh логical dùng để phân loại message.
- Topic không tự giới hạn số lượng message
- Message trong topic được lưu trữ theo thứ tự
- Một topic có thể có nhiều partition

---

### Partition
**Partition** là đơn vị lưu trữ nhỏ nhất của topic.
- Cho phép xử lý song song dữ liệu
- Mỗi partition chỉ được consume bởi **một consumer trong cùng consumer group**
- Đảm bảo thứ tự message **trong phạm vi partition**

---

### Consumer Group
**Consumer Group** là tập hợp các consumer cùng đọc dữ liệu từ một topic.
- Kafka phân phối mỗi partition cho một consumer trong group
- Giúp mở rộng khả năng xử lý (scale out)
- Nếu một consumer bị down, partition sẽ được gán lại cho consumer khác

### Compact và Delete

Kafka hỗ trợ hai cơ chế dọn dẹp dữ liệu (log cleanup policy):

- **Delete**
    - Message sẽ bị xóa sau một khoảng thời gian hoặc khi vượt quá dung lượng cấu hình
    - Phù hợp với các use case xử lý event thông thường
    - Cấu hình bằng:
      ```properties
      log.cleanup.policy=delete
      ```

- **Compact**
    - Kafka chỉ giữ lại **message mới nhất cho mỗi key**
    - Các message cũ có cùng key sẽ bị loại bỏ
    - Phù hợp với các use case lưu trạng thái (state), ví dụ: user profile, configuration
    - Cấu hình bằng:
      ```properties
      log.cleanup.policy=compact
      ```

> Có thể kết hợp cả hai: `cleanup.policy=compact,delete`

---

### Retention Time và Delete Retention Time

#### Retention là cơ chế xóa message, không xóa offset → offset trở nên invalid

- **Retention Time (`retention.ms`)**
    - Thời gian Kafka giữ message trước khi xóa
    - Áp dụng cho topic dùng chính sách **delete**
    - Ví dụ:
      ```properties
      retention.ms=604800000 # 7 ngày
      ```

- **Delete Retention Time (`delete.retention.ms`)**
    - Thời gian Kafka giữ **tombstone message** (message có value = null)
    - Áp dụng cho topic dùng **log compaction**
    - Sau thời gian này, key tương ứng có thể bị xóa hoàn toàn
    - Ví dụ:
      ```properties
      delete.retention.ms=86400000 # 1 ngày
      ```

---

### StreamBridge

**StreamBridge** là một thành phần trong **Spring Cloud Stream**, cho phép:
- Gửi message vào Kafka **mà không cần binding sẵn**
- Gửi message **động** tại runtime
- Phù hợp với các use case publish event linh hoạt

Ví dụ sử dụng:
```
    streamBridge.send("output-topic", message);
```

**Ưu điểm của StreamBridge:**

- Không phụ thuộc chặt chẽ vào cấu hình binding
- Dễ sử dụng trong kiến trúc event-driven
- Phù hợp cho microservices Spring Boot

### Offset

**Offset** là một số nguyên dùng để xác định **vị trí của message** trong một partition của Kafka.

- Mỗi message trong một partition có **offset duy nhất và tăng dần**
- Offset giúp Kafka xác định message nào đã được consumer xử lý
- Offset chỉ có ý nghĩa **trong phạm vi một partition**

Consumer sử dụng offset để:
- Đọc tiếp message từ đúng vị trí đã xử lý trước đó
- Tránh xử lý trùng lặp message
- Hỗ trợ cơ chế retry và fault tolerance

Ghi chú thêm: 
- Thuộc tính auto.offset.reset không ảnh hưởng nếu offset đã được commit và còn hợp lệ
- Offset được quản lý theo consumer group

### Commit

**Commit** là cơ chế dùng để **lưu offset** mà consumer đã xử lý thành công, giúp Kafka xác định vị trí đọc tiếp theo khi consumer restart hoặc xảy ra sự cố.

Commit offset đảm bảo:
- Tránh xử lý trùng lặp message
- Hỗ trợ khôi phục (recovery) khi consumer bị restart
- Kiểm soát độ tin cậy của quá trình consume

#### Commit được lưu ở đâu?

➡️ Kafka lưu commit offset vào internal topic:
```
    __consumer_offsets
```

Key gồm:
* group.id
* topic
* partition

📌 Vì vậy:
- Cùng group.id → dùng chung offset
- Khác group.id → đọc lại từ đầu

#### Các hình thức Commit

- **Auto Commit**
  - Kafka tự động commit offset theo chu kỳ cấu hình
  - Không quan tâm bạn xử lý xong hay chưa
  - Dễ cấu hình nhưng có thể gây mất message nếu xử lý chưa hoàn tất
  ```properties
  enable.auto.commit=true
  auto.commit.interval.ms=5000
- **Manual Commit**
  - Consumer chủ động commit offset sau khi xử lý message thành công
  - Kiểm soát tốt hơn, phù hợp với hệ thống yêu cầu độ tin cậy cao
  ```properties
  enable.auto.commit=false
  ```
  #### Code commit manual
    ```
    @KafkaListener
    public void listen(String msg, Acknowledgment ack) {
    // xử lý xong
    ack.acknowledge();
    }
    ```
    or 
    ```  
    consumer.commitSync();
    ``` 

#### Commit Sync và Commit Async
 - Commit Sync

    - Chờ Kafka broker xác nhận commit thành công
    - Đảm bảo offset được lưu chính xác
    - Có thể làm giảm throughput

 - Commit Async

    - Không chờ phản hồi từ broker
    - Tăng hiệu năng
    - Có rủi ro commit thất bại mà không được phát hiện

#### Commit và Consumer Group
  - Offset được commit theo Consumer Group
  - Mỗi consumer group có offset riêng cho từng partition
  - Khi xảy ra rebalance, Kafka sẽ sử dụng offset đã commit gần nhất

### KRaft và ZooKeeper

Kafka hỗ trợ hai cơ chế quản lý metadata và điều phối cluster: **ZooKeeper** (truyền thống) và **KRaft** (Kafka Raft – kiến trúc mới).

#### ZooKeeper

**ZooKeeper** từng là thành phần bắt buộc trong Kafka để:
- Quản lý metadata của cluster (broker, topic, partition)
- Thực hiện leader election cho partition
- Theo dõi trạng thái broker

Nhược điểm:
- Phụ thuộc thêm một hệ thống bên ngoài
- Tăng độ phức tạp trong vận hành
- Khó mở rộng và bảo trì ở quy mô lớn

---

#### KRaft (Kafka Raft)

**KRaft** là kiến trúc mới của Kafka, sử dụng thuật toán **Raft** để quản lý metadata **nội bộ Kafka**, không cần ZooKeeper.

Ưu điểm của KRaft:
- Loại bỏ sự phụ thuộc vào ZooKeeper
- Đơn giản hóa kiến trúc hệ thống
- Tăng hiệu năng và độ ổn định
- Thời gian khởi động và recovery nhanh hơn

Trong chế độ KRaft:
- Kafka controller được tích hợp trực tiếp trong broker
- Metadata được lưu trữ trong **metadata log**
- Leader election được thực hiện thông qua Raft consensus

---

#### So sánh nhanh

| Tiêu chí        | ZooKeeper           | KRaft                |
|-----------------|---------------------|----------------------|
| Phụ thuộc ngoài | Có                  | Không                |
| Quản lý metadata| ZooKeeper           | Kafka nội bộ         |
| Độ phức tạp     | Cao                 | Thấp hơn             |
| Tương lai Kafka | Đã deprecated       | Kiến trúc mặc định   |

> **KRaft** là hướng phát triển chính và sẽ thay thế hoàn toàn ZooKeeper trong các phiên bản Kafka mới.

#### Quản lý Offset

- **Auto Commit**
    - Kafka tự động commit offset theo chu kỳ
    - Đơn giản nhưng có thể gây mất message nếu xử lý chưa xong
  ```properties
  enable.auto.commit=true
  
## Hướng dẫn setup và build 1 project Kafka

### 1. Setup Kafka với Docker Compose

Sử dụng `docker-compose` là cách nhanh nhất để khởi tạo Kafka phục vụ cho môi trường **local development**.

#### Yêu cầu
- Docker
- Docker Compose

#### Cấu trúc thư mục
```text
project-root
├── docker-compose.yml
└── README.md
```
### Docker Compose Configuration

Sử dụng Docker Compose để khởi tạo Kafka chạy ở chế độ **KRaft (không cần ZooKeeper)**.

#### File `docker-compose.yml`

```yaml
version: '3.8'

services:
  kafka1:
    image: apache/kafka:latest
    container_name: kafka1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:29093,2@kafka2:29093,3@kafka3:29093'
      KAFKA_LISTENERS: 'PLAINTEXT://:29092,CONTROLLER://:29093,PLAINTEXT_HOST://:9092'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka1:29092,PLAINTEXT_HOST://localhost:9092'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3 # Tăng lên 3 để an toàn dữ liệu
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'

  kafka2:
    image: apache/kafka:latest
    container_name: kafka2
    ports:
      - "9093:9093"
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:29093,2@kafka2:29093,3@kafka3:29093'
      KAFKA_LISTENERS: 'PLAINTEXT://:29092,CONTROLLER://:29093,PLAINTEXT_HOST://:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka2:29092,PLAINTEXT_HOST://localhost:9093'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'

  kafka3:
    image: apache/kafka:latest
    container_name: kafka3
    ports:
      - "9094:9094"
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:29093,2@kafka2:29093,3@kafka3:29093'
      KAFKA_LISTENERS: 'PLAINTEXT://:29092,CONTROLLER://:29093,PLAINTEXT_HOST://:9094'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka3:29092,PLAINTEXT_HOST://localhost:9094'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    ports:
      - "8080:8080"
    depends_on:
      - kafka1
      - kafka2
      - kafka3
    environment:
      # Kết nối Kafka UI với service 'kafka' bên trên
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka1:29092,kafka2:29092,kafka3:29092
      DYNAMIC_CONFIG_ENABLED: 'true'

```
Khởi động Kafka
```bash
docker-compose up -d
```

## Một số câu hỏi về Kafka