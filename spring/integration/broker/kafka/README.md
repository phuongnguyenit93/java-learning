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
- Lưu trữ message vào disk : Message -> Topic → Partition → Broker lưu trên disk
- Phân phối message cho Consumer

Một Kafka cluster bao gồm nhiều broker để đảm bảo **high availability** và **scalability**.

Ngoài ra các broker còn phân công nhiệm vụ Leader & Follower
#### Mỗi partition có:

* 1 Leader (xử lý read/write)
* N Follower (replica)

#### Nguyên tắc: 
* Producer & Consumer chỉ nói chuyện với Leader
* Khi 1 broker Leader sập , thì broker follower sẽ lên làm leader => Data không bị mất
* Chia partition quản lý => Giảm tải kafka và phân tán Disk/CPU/Network


Ví dụ (replicas=3):
```text
Partition 0:
Leader: Broker 1
Follower: Broker 2, Broker 3
```

---

### Cluster

**Kafka Cluster** là tập hợp nhiều **broker** hoạt động cùng nhau để cung cấp một hệ thống **phân tán**, đảm bảo **khả năng mở rộng**, **tính sẵn sàng cao (high availability)** và **chịu lỗi**.

Một cluster Kafka chịu trách nhiệm:
- Lưu trữ và phân phối message
- Quản lý topic, partition và replica
- Đảm bảo dữ liệu không bị mất khi broker gặp sự cố

---

#### Thành phần của Kafka Cluster

- **Broker**: Các node chính trong cluster, lưu trữ dữ liệu và xử lý request
- **Controller**: Điều phối cluster, quản lý metadata và leader election
- **Topic / Partition / Replica**: Cấu trúc dữ liệu phân tán trong cluster

---

#### Cluster và Khả năng Mở rộng

- Có thể thêm broker mới vào cluster mà không cần downtime
- Partition được phân bố trên nhiều broker
- Consumer group cho phép scale out việc xử lý dữ liệu

---

#### Cluster và Khả năng Chịu lỗi

- Dữ liệu được replicate trên nhiều broker
- Khi một broker down, leader sẽ được bầu lại từ ISR
- Consumer và producer tự động reconnect

---

#### Best Practices

- Số lượng broker ≥ replication factor
- Không sử dụng single broker cho môi trường production
- Giám sát cluster bằng các công cụ như Kafdrop, AKHQ, Prometheus

> Kafka Cluster là nền tảng cốt lõi để xây dựng các hệ thống **event-driven** và **microservices** có độ tin cậy cao.

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

### Replica

**Replica** là các bản sao của **partition** trong Kafka, được sử dụng để đảm bảo **tính sẵn sàng (high availability)** và **khả năng chịu lỗi (fault tolerance)**.

- Mỗi partition có thể có nhiều replica
- Một replica được bầu làm **Leader**
- Các replica còn lại là **Follower**
- Producer và Consumer **chỉ làm việc với Leader**

#### Leader và Follower

- **Leader Replica**
    - Nhận message từ Producer
    - Phân phối message cho Consumer
    - Chịu trách nhiệm ghi dữ liệu chính

- **Follower Replica**
    - Đồng bộ dữ liệu từ Leader
    - Không xử lý trực tiếp request từ Producer / Consumer
    - Sẵn sàng thay thế Leader khi xảy ra sự cố


#### In-Sync Replicas (ISR)

**ISR (In-Sync Replicas)** là tập hợp các replica:
- Được đồng bộ đầy đủ với Leader
- Đáp ứng yêu cầu về độ trễ cho phép
- Có khả năng được bầu làm Leader

Nếu Leader gặp sự cố, Kafka sẽ:
- Chọn một replica trong ISR làm Leader mới
- Đảm bảo dữ liệu không bị mất hoặc mất ở mức tối thiểu


#### Replica và Độ Tin Cậy Dữ Liệu

- Số lượng replica được cấu hình thông qua `replication.factor`
- Replica càng nhiều → độ an toàn dữ liệu càng cao
- Replica nhiều cũng làm tăng chi phí tài nguyên

```properties
replication.factor=3
```

---
### Consumer Group
**Consumer Group** là tập hợp các consumer cùng đọc dữ liệu từ một topic.
- Kafka phân phối mỗi partition cho một consumer trong group
- Giúp mở rộng khả năng xử lý (scale out)
- Nếu một consumer bị down, partition sẽ được gán lại cho consumer khác

---
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

---
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

---

### Commit

**Commit** là cơ chế dùng để **lưu offset** mà consumer đã xử lý thành công, giúp Kafka xác định vị trí đọc tiếp theo khi consumer restart hoặc xảy ra sự cố.

Commit của consumer = việc ghi lại “tôi đã xử lý xong tới message nào rồi”

Cụ thể hơn:

* Kafka không tự biết consumer đã xử lý xong message hay chưa
* Consumer phải nói cho Kafka biết bằng cách commit offset


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

---

### Controller

**Controller** là một broker đặc biệt trong Kafka cluster, chịu trách nhiệm **điều phối và quản lý trạng thái của cluster**.

Các nhiệm vụ chính của Controller:
- Quản lý metadata của cluster (topic, partition, replica)
- Thực hiện **leader election (bầu cử leader)** cho partition
- Giám sát trạng thái của broker (broker join / leave) hoặc (broker dead/alive)
- Xử lý sự kiện **failover** khi broker hoặc replica gặp sự cố

#### Lưu ý
- Broker không nhận message
- Broker không lưu data
- Broker không đọc/ghi topic


#### Controller trong ZooKeeper Mode

- Controller được **bầu chọn thông qua ZooKeeper**
- Tại một thời điểm trong 1 cluster chỉ có **một Controller duy nhất**
- Mọi thay đổi metadata đều được ghi nhận và đồng bộ qua ZooKeeper

---

#### Controller trong KRaft Mode

- Controller được quản lý thông qua **Raft consensus**
- Không phụ thuộc vào ZooKeeper
- Metadata được lưu trữ trong **metadata log** nội bộ Kafka
- Có thể có nhiều controller node nhưng chỉ **một controller leader** hoạt động tại một thời điểm

---

#### Vai trò của Controller đối với Cluster

- Đảm bảo cluster hoạt động ổn định và nhất quán
- Điều phối cluster
- Tự động khôi phục khi có sự cố
- Là thành phần cốt lõi để Kafka đảm bảo **high availability**

> Trong các phiên bản Kafka mới, **KRaft Controller** là kiến trúc mặc định và là hướng phát triển lâu dài của Kafka.

#### Lưu ý quan trọng

* Nếu Controller chết → Kafka bầu Controller mới
* Message KHÔNG bị mất
* Có thể chậm vài giây trong thời gian re-election

---

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

Giải thích một số properties trong docker compose :
### 1. Định danh và Vai trò (Identification & Roles)

#### KAFKA_NODE_ID: 1
* ID duy nhất của broker trong cụm. 
* Các node khác phải là 2, 3... 
* Nó thay thế cho broker.id trước đây.

#### KAFKA_PROCESS_ROLES: 'broker,controller' 
* Xác định node này làm nhiệm vụ gì.
* broker: Lưu trữ dữ liệu và xử lý yêu cầu từ client.
* controller: Quản lý cụm (thay thế vai trò của Zookeeper). Một node có thể làm cả hai.

📌 Có 3 kiểu:
* broker
* controller
* broker,controller (phổ biến cho dev)

#### CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
* ID của cả cụm. 
* Tất cả các broker trong cùng một cụm phải dùng chung ID này để chúng nhận diện được nhau.
---
### 2. Cơ chế Bầu chọn (Quorum Configuration)

#### KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka1:29093,2@kafka2:29093,3@kafka3:29093'
* Danh sách các node có quyền biểu quyết để bầu ra "Leader" quản lý cụm. 
* Định dạng là node_id@host_name:port_controller. 
* Đây là cách các controller tìm thấy nhau để duy trì sự ổn định của cụm.

### 3. Mạng và Kết nối (Listeners) - Đây là phần dễ gây nhầm lẫn nhất:

#### KAFKA_LISTENERS: 'PLAINTEXT://:29092,CONTROLLER://:29093,PLAINTEXT_HOST://:9092'

 - Khai báo các "cổng" mà Kafka sẽ mở ra để lắng nghe.

   * PLAINTEXT://:29092: Cho các broker khác hoặc app trong Docker (Broker nội bộ (container ↔ container))
   * CONTROLLER://:29093: Chỉ dành cho các controller trao đổi thông tin bầu chọn.
   * PLAINTEXT_HOST://:9092: Cho các ứng dụng chạy bên ngoài Docker (localhost).
 - Listener chỉ là cổng mở, chưa phải địa chỉ client thấy

#### KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka3:29092,PLAINTEXT_HOST://localhost:9092'

  * Địa chỉ mà Kafka "quảng bá" ra ngoài. 
  * Khi client kết nối tới Kafka, Kafka sẽ gửi lại địa chỉ này để bảo client hãy liên lạc qua đó.
  * Client ngoài Docker sẽ dùng localhost:9092.
  * Client trong Docker (như Kafka UI) sẽ dùng kafka1:29092.

#### KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
  * Định nghĩa giao thức bảo mật cho từng tên listener. 
  * Ở đây tất cả đều là PLAINTEXT (không mã hóa).
  * Nếu dùng SSL/SASL thì config tại đây

### 4. Giao tiếp nội bộ (Internal Communication)

#### KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER': 
* Chỉ định listener nào được dùng cho mục đích quản lý cụm (controller).
* Bắt buộc trong KRaft mode

#### KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT': 
* Chỉ định listener nào được các broker dùng để sao chép dữ liệu qua lại với nhau.
* Listener dùng cho:
  * Broker ↔ Broker
  * Replication
  * Metadata sync
* Không phải client listener

### 5. Cấu hình hệ thống và Dữ liệu

#### KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3: 
* Kafka lưu vị trí (offset) đã đọc của các Consumer trong một topic nội bộ. Khi có 3 broker, ta đặt là 3 để nếu 2 broker chết, ta vẫn không mất dấu vết đang đọc đến đâu.

#### KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false': 
* Tắt tính năng tự tạo topic để kiểm soát chặt chẽ hệ thống.

#### KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1 & REPLICATION_FACTOR: 1: 
* Cấu hình cho các bản ghi giao dịch (transactions). 
* Trong môi trường 3 broker, bạn nên nâng REPLICATION_FACTOR lên 3 để đồng bộ với số lượng broker.

--- 
Tóm tắt luồng đi của dữ liệu:

* Spring Boot (ngoài Docker) nhìn thấy localhost:9092 (PLAINTEXT_HOST).
* Kafka UI (trong Docker) nhìn thấy kafka1:29092 (PLAINTEXT).
* Các Broker bầu chọn lẫn nhau qua cổng 29093 (CONTROLLER).

## Khởi động Kafka
```bash
docker-compose up -d
```

## Một số câu hỏi về Kafka

### 1. Chuyện gì sẽ xảy ra khi consumer bị ngắt kết nối giữa chừng ?
- Kafka không push message trực tiếp vào consumer, mà:

  * Message được lưu bền vững (persist) trong topic partition
  * Consumer chủ động pull message
  * Kafka chỉ coi message là đã đọc xong khi offset được commit

👉 Vì vậy:
  * Khi consumer bị tắt / crash / pause → message vẫn nằm nguyên trong Kafka

👉 Khi consumer start lại:
  * Kafka sẽ đọc từ offset đã commit gần nhất
  * Các message chưa được commit → được đọc lại

✅ Message trong thời gian consumer bị tắt → đọc lại được

👉 Tuy nhiên, đối với message đang xử lý mà consumer bị tắt, nếu đã commit offset thì coi như message đó đã mất

### 2. Message tồn tại trong bao lâu ? Làm sao để điều chỉnh thời gian tồn tại ? Chuyện gì xảy ra nếu message đã xóa nhưng consumer chưa commit offset ?

- Thời gian tổn tại của message dựa theo retention 
- Retention dựa thao thời gian hoạc dung lượng 
- Cách thức xóa cũng sẽ dựa theo cleanup policy (Compact hoặc Delete)
  - Delete : Xóa theo thời gian / size : Và xóa cái xa nhất
  - Compact : Giữ message cuối cùng theo key

#### Nếu message đã xóa nhưng chưa commit offset
  - Mất message đó 
  - Ngoài ra có 3 case sau

1. Case 1: auto.offset.reset=latest
* ➡️ Kafka nhảy thẳng tới offset mới nhất
* ➡️ ❌ MẤT TOÀN BỘ MESSAGE CŨ

2. Case 2: auto.offset.reset=earliest
* ➡️ Kafka đọc từ message còn tồn tại sớm nhất
* ➡️ ❌ MẤT MESSAGE ĐÃ BỊ XÓA

3. Case 3: auto.offset.reset=none
* ➡️ ❌ Consumer CRASH
* ➡️ OffsetOutOfRangeException

#### Kafka có 3 cấp cấu hình retention (theo thứ tự ưu tiên):
* Topic config  >  Broker config  >  Default

### 3. Làm sao để các massage khi gửi cần theo thứ tự nó vào cùng partition trong 1 topics ? Vì các thứ tư giữa các partition không đảm bảo

#### 👉 Kafka chỉ đảm bảo thứ tự trong 1 partition

❌ Không bao giờ đảm bảo thứ tự:

* Giữa các partition
* Sau khi tăng partition cho cùng 1 key (nếu dùng sai cách)

#### Vậy làm sao đảm bảo message của 1 consumer luôn ở cùng 1 partition?

* CÁCH DUY NHẤT: DÙNG MESSAGE KEY
  * Producer phải gửi message có key
```text
kafkaTemplate.send("deposit-money-event", userId, message);
```
Kafka sẽ 

```text
partition = hash(userId) % partition_count
```

📌 Kết quả:
* Cùng userId → luôn vào cùng partition
* Thứ tự của user đó được đảm bảo

#### Vậy nếu khi tăng partition thì sao?
⚠️ Đây là điểm nhiều người dính lỗi

Giả sử ban đầu:

```text
partition_count = 3
partition = hash(userId) % 3
```

Sau đó bạn tăng lên:
```text
partition_count = 6
partition = hash(userId) % 6
```

➡️ KẾT QUẢ:
1. [x] Cùng userId → vào partition KHÁC
2. [x] Thứ tự bị phá

#### Cách KHẮC PHỤC khi cần tăng partition

Cách 1 (Chuẩn nhất): Chấp nhận MẤT ORDER khi scale

  ✔️ Phổ biến
  
  ✔️ Kafka design chấp nhận

➡️ Với hệ thống không yêu cầu strict ordering toàn cục

* Cách 2: Dùng Custom Partitioner (nâng cao)
```text
public class FixedPartitioner implements Partitioner {
@Override
public int partition(String topic, Object key, byte[] keyBytes,Object value, byte[] valueBytes, Cluster cluster) {
        return Math.abs(key.hashCode()) % 3; // CỐ ĐỊNH
    }
}
```

➡️ Dù topic tăng partition:

* Key vẫn map vào 3 partition đầu
* Partition mới chỉ dùng cho key mới
