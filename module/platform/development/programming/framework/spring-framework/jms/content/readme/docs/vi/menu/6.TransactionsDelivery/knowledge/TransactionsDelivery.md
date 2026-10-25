<a id="back-to-top"></a>

# Transaction và ngữ nghĩa giao nhận

## Menu
- [Acknowledgement Mode tại ranh giới Spring](#jms-acknowledgement-model)
- [Transacted Session và Listener Rollback](#jms-session-transacted)
- [JmsTransactionManager và Local Transaction](#jms-local-transaction-manager)
- [JMS Resource gắn theo Thread và sự tham gia của JmsTemplate](#jms-thread-bound-resources)
- [JTA/XA và phối hợp transaction bên ngoài](#jms-jta-xa-boundary)
- [Redelivery, xử lý trùng lặp và Idempotency](#jms-redelivery-idempotency)

## <a id="jms-acknowledgement-model">Acknowledgement Mode tại ranh giới Spring</a>

<details>
<summary>Xem chi tiết</summary>

Acknowledgement trả lời một câu hỏi: khi nào provider có thể xem một message đã được consumer xác nhận? Nó liên quan tới transaction và redelivery, nhưng không đồng nghĩa với business transaction.

Spring cho phép cấu hình JMS session acknowledgement mode qua listener container. Hành vi quan trọng phụ thuộc cách triển khai container. Với `DefaultMessageListenerContainer` (DMLC), mode mặc định `AUTO_ACKNOWLEDGE` acknowledge trước khi listener chạy. Vì vậy một user exception không khiến message được redeliver chỉ vì listener thất bại. `SimpleMessageListenerContainer` có thời điểm automatic acknowledgement khác, nên quyết định về reliability phải dựa trên container thực tế chứ không chỉ dựa vào tên mode.

Spring mô tả các lựa chọn chính như sau:

- `AUTO_ACKNOWLEDGE`: tiện dụng, nhưng với DMLC không cung cấp exception-driven redelivery.
- `DUPS_OK_ACKNOWLEDGE`: acknowledge theo kiểu lazy; JMS cho phép duplicate delivery và listener exception vẫn không có rollback semantics như transaction.
- `CLIENT_ACKNOWLEDGE`: Spring acknowledge sau khi listener xử lý thành công và cung cấp best-effort redelivery khi listener thất bại hoặc bị gián đoạn.
- `sessionTransacted=true`: commit sau khi xử lý thành công và rollback khi listener thất bại, cho local JMS redelivery semantics rõ ràng nhất.

```java
@Bean
DefaultJmsListenerContainerFactory reliableFactory(ConnectionFactory cf) {
    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cf);
    factory.setSessionTransacted(true);
    return factory;
}
```

Với DMLC listener cần reliability, Spring khuyến nghị transacted session hoặc external transaction manager thay vì dựa vào default auto-acknowledge.

**Ranh giới:** acknowledgement và redelivery cuối cùng vẫn dựa trên Jakarta Messaging và provider. Spring điều khiển cách container sử dụng JMS `Session`; nó không định nghĩa lại persistence, dead-letter policy hay delivery guarantee của broker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-session-transacted">Transacted Session và Listener Rollback</a>

<details>
<summary>Xem chi tiết</summary>

Một JMS `Session` được transaction hóa làm cho việc receive message và JMS work thực hiện trong session đó nằm trong cùng local JMS transaction. Với listener container, `sessionTransacted=true` tạo mô hình lỗi đơn giản:

```text
receive message
    ↓
gọi listener
    ├─ thành công → commit JMS Session
    └─ ném exception → rollback JMS Session → message có thể được redeliver
```

Local transaction này đặc biệt hữu ích khi listener nhận message rồi gửi JMS reply hoặc một JMS message khác trong cùng transactional context. Nó không tự động bao gồm một database transaction độc lập.

Với listener container dùng local transaction, Spring còn có một cầu nối quan trọng sang thao tác gửi bằng template. `AbstractMessageListenerContainer` mặc định cho phép các lời gọi `JmsTemplate` dùng JMS `Session` của listener (`exposeListenerSession=true`). Vì vậy `JmsTemplate` dùng cùng `ConnectionFactory` với listener có thể tái sử dụng session đang hoạt động, để thao tác gửi bên trong quá trình xử lý listener tham gia cùng local JMS transaction thay vì vô tình tạo một JMS transaction độc lập. Nếu tắt `exposeListenerSession`, template sẽ dùng một session mới lấy từ cùng connection bên dưới; còn session do transaction manager bên ngoài quản lý luôn được cung cấp cho `JmsTemplate` bất kể cờ này. Đây là cơ chế của listener container, khác với mô hình tài nguyên gắn theo thread của `JmsTransactionManager` được trình bày ở phần sau.

```java
@JmsListener(destination = "orders.in", containerFactory = "reliableFactory")
void handle(OrderPlaced event) {
    validate(event);
    // Nếu đoạn xử lý này ném exception, container rollback JMS Session.
    orderService.apply(event);
}
```

Transaction boundary là listener invocation do container quản lý. Nếu method kết thúc bình thường, Spring có thể commit local JMS transaction. Nếu xử lý ném exception, container rollback. Sau đó provider redelivery policy quyết định message quay lại khi nào, theo cách nào, và điều gì xảy ra sau nhiều lần thất bại.

Không nên catch mọi exception rồi return bình thường trừ khi đó thật sự là kết quả mong muốn. Nuốt lỗi khiến container hiểu rằng processing đã thành công, vì vậy transacted session không có lý do rollback.

Local JMS transaction hẹp hơn khái niệm “toàn bộ business work atomic”. Nếu listener đồng thời ghi database bằng một local transaction khác, JVM vẫn có thể crash sau khi một resource commit nhưng trước khi resource còn lại commit. Vì vậy duplicate handling và idempotency vẫn quan trọng dù đã dùng `sessionTransacted=true`.

**Lựa chọn thực tế thường gặp:** với DMLC chạy độc lập cần JMS redelivery nhưng không cần distributed atomicity, locally transacted JMS session thường là cơ chế nên cân nhắc đầu tiên.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `AbstractMessageListenerContainer#setExposeListenerSession`
- Spring Framework Reference — Processing Messages Within Transactions

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-local-transaction-manager">JmsTransactionManager và Local Transaction</a>

<details>
<summary>Xem chi tiết</summary>

`JmsTransactionManager` là `PlatformTransactionManager` của Spring dành cho **một JMS `ConnectionFactory`**. Nó quản lý local JMS resource transaction; nó không phải distributed transaction manager.

Khi transaction bắt đầu, manager lấy một cặp JMS `Connection`/`Session` từ `ConnectionFactory` đã cấu hình và bind resource holder đó với thread hiện tại. Commit sẽ commit JMS session; rollback sẽ rollback session. Các Spring JMS component sau đó có thể tham gia transaction mà application code không phải chuyền `Session` thủ công.

```java
@Bean
JmsTransactionManager jmsTransactionManager(ConnectionFactory connectionFactory) {
    return new JmsTransactionManager(connectionFactory);
}

@Transactional("jmsTransactionManager")
public void publish(OrderPlaced event) {
    jmsTemplate.convertAndSend("orders.events", event);
    jmsTemplate.convertAndSend("audit.events", event);
}
```

Hai lệnh send có thể dùng cùng transactional JMS resource gắn với thread khi template và transaction manager tham chiếu đúng cùng chuỗi `ConnectionFactory` mà cơ chế resource lookup của Spring mong đợi.

Đối với listener container, hướng dẫn của Spring thường ưu tiên `sessionTransacted=true` khi transaction không cần được quản lý từ bên ngoài. Chỉ cấu hình `transactionManager` cho container khi thực sự cần external coordination; trường hợp điển hình là JTA/XA hơn là `JmsTransactionManager`.

`JmsTransactionManager` không tự đưa database resource vào cùng atomic transaction. Nếu JDBC work và JMS work dùng hai local transaction manager riêng thì vẫn tồn tại khoảng lỗi giữa hai resource.

Spring khuyến nghị resource reuse phù hợp quanh manager này. `CachingConnectionFactory` giúp tránh tạo lại JMS resource đắt đỏ trong khi mỗi transaction vẫn có transactional `Session` riêng.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `JmsTransactionManager`
- Spring Framework Reference — JMS Transaction Management

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-thread-bound-resources">JMS Resource gắn theo Thread và sự tham gia của JmsTemplate</a>

<details>
<summary>Xem chi tiết</summary>

Điểm tích hợp quan trọng của `JmsTransactionManager` không nằm ở annotation mà nằm ở mô hình thread-bound resource của Spring. Transaction manager bind JMS resource theo `ConnectionFactory`; khi `JmsTemplate` hoạt động, nó hỏi JMS resource utilities của Spring để lấy transactional `Session` trước khi tự tạo session mới.

```text
transaction bắt đầu
    ↓
Connection/Session được bind với thread hiện tại
    ↓
JmsTemplate được gọi
    ↓
phát hiện/tái sử dụng transactional Session
    ↓
transaction manager commit hoặc rollback một lần
```

Nhờ vậy code dùng template không cần tự gọi `Session.commit()` hoặc `Session.rollback()`.

```java
@Transactional("jmsTransactionManager")
public void sendBatch(List<OrderPlaced> events) {
    for (OrderPlaced event : events) {
        jmsTemplate.convertAndSend("orders.events", event);
    }
    // Exception trước khi method hoàn tất sẽ đi qua cơ chế rollback
    // của Spring transaction infrastructure.
}
```

Việc match resource rất quan trọng. Nếu transaction manager và template được wire tới hai `ConnectionFactory` không liên quan hoặc hai lớp proxy có identity không phù hợp, template có thể không tìm thấy resource đã bind. Nên duy trì một factory chain rõ ràng và inject nhất quán.

Native JMS code không tự tham gia transaction chỉ vì nó chạy trên cùng thread. `JmsTemplate` và `ConnectionFactoryUtils` biết cách dùng Spring-managed transaction. Legacy/native code cần tham gia trong suốt có thể cần `TransactionAwareConnectionFactoryProxy`; theo contract của Spring, proxy này nên nằm ngoài cùng của connection-factory chain.

Thread binding cũng chỉ ra một giới hạn: transactional context không tự truyền sang thread con hoặc executor task tùy ý. Không nên offload transactional JMS work sang thread khác rồi giả định nó vẫn dùng cùng session.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-jta-xa-boundary">JTA/XA và phối hợp transaction bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Local JMS transaction phối hợp một JMS resource. Khi một business operation bắt buộc atomic giữa JMS và một XA-capable resource khác, ví dụ database cùng tham gia global transaction, mô hình liên quan là JTA/XA.

DMLC có thể bọc cả message reception và listener execution trong một external `PlatformTransactionManager`. Với distributed JMS coordination thực sự, đây thường là `JtaTransactionManager` cùng XA-aware JMS `ConnectionFactory` và cấu hình XA tương ứng.

```text
JTA transaction bắt đầu
    ↓
XA JMS receive được enlist
    +
XA database work được enlist
    ↓
prepare / commit coordination
```

Thuộc tính `transactionManager` của JMS listener container vì vậy khác với `sessionTransacted=true`. Local transacted session yêu cầu JMS provider commit/rollback một JMS session. JTA/XA giao quyền điều phối cho external coordinator quản lý tất cả XA resource đã enlist trong distributed transaction.

API của Spring JMS cũng có một chi tiết quan trọng: khi đang ở trong managed JTA transaction, các cờ `transacted` và acknowledgement truyền khi tạo JMS session do managed environment/provider wrapper quyết định, không còn là local-session policy độc lập.

Chỉ chọn XA khi yêu cầu atomicity đủ mạnh để biện minh cho chi phí vận hành. XA đòi hỏi coordinator, XA-capable driver/provider, recovery và thêm runtime overhead. Nhiều hệ thống thay vào đó dùng local transaction kết hợp idempotent consumer, outbox/inbox hoặc consistency pattern khác ở tầng kiến trúc ứng dụng.

**Không nên diễn giải XA thành “mọi thứ exactly-once”.** XA chỉ có thể phối hợp atomic các resource thực sự được enlist. HTTP call, email, filesystem effect hoặc service không hỗ trợ XA không trở nên atomic chỉ vì listener đang chạy trong JTA transaction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-redelivery-idempotency">Redelivery, xử lý trùng lặp và Idempotency</a>

<details>
<summary>Xem chi tiết</summary>

Redelivery là cơ chế phục hồi sau lỗi, không phải bằng chứng rằng ứng dụng sẽ quan sát mỗi business event đúng một lần. Message có thể được giao lại sau rollback, connection loss, broker recovery hoặc crash đúng vào khoảng nhạy cảm quanh commit. Ứng dụng phải quyết định việc lặp tác động nghiệp vụ có an toàn hay không.

Với locally transacted listener, một đường lỗi điển hình là:

```text
message được giao
    ↓
business work chạy
    ↓
JVM/provider/network fail trước JMS commit
    ↓
broker không thấy receipt đã commit
    ↓
message có thể được redeliver
```

Tác động nghiệp vụ có thể đã xảy ra trước crash. Vì vậy `JMSRedelivered` là thông tin chẩn đoán hữu ích nhưng không chứng minh lần xử lý trước đã hoặc chưa thay đổi hệ thống bên ngoài.

Idempotency nghĩa là chạy lại operation không tạo thêm tác động sai. Một số cách thường dùng:

- lưu business event/message id ổn định và bỏ qua id đã xử lý;
- biểu diễn database change bằng upsert hoặc chuyển trạng thái (state transition) có guard;
- giữ inbox/deduplication table trong cùng database transaction với business update;
- truyền idempotency key xuống downstream command nếu downstream system hỗ trợ.

```java
@Transactional
public void apply(OrderPlaced event) {
    if (!processedEventRepository.tryInsert(event.eventId())) {
        return; // event đã được áp dụng trước đó
    }
    orderRepository.markPlaced(event.orderId());
}
```

Không nên deduplicate chỉ bằng một delivery-attempt id không ổn định nếu producer đã có business event id ổn định. Ngược lại, một `Set` trong memory không phải durable deduplication: restart sẽ mất dữ liệu và tập này còn có thể tăng vô hạn.

Poison message thất bại lặp lại cần chính sách vận hành có giới hạn, thường gồm provider redelivery limit và dead-letter/error destination. Spring JMS cung cấp hook cho lỗi listener và phục hồi hạ tầng; lịch redelivery/dead-letter cụ thể vẫn thuộc provider.

</details>

- [Quay lại đầu trang](#back-to-top)
