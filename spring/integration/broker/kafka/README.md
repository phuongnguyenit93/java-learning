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

#### Quản lý Offset

- **Auto Commit**
    - Kafka tự động commit offset theo chu kỳ
    - Đơn giản nhưng có thể gây mất message nếu xử lý chưa xong
  ```properties
  enable.auto.commit=true