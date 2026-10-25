<a id="back-to-top"></a>

# Mô hình transaction reactive

## Menu
- [Reactive transaction context: ThreadLocal và Reactor Context](#transaction-reactive-context-model)
- [Quy ước của ReactiveTransactionManager](#transaction-reactive-manager-contract)
- [Quy ước của method @Transactional reactive](#transaction-reactive-method-contract)
- [Cùng tham gia transaction context và phạm vi của TransactionalOperator](#transaction-reactive-participation)
- [Ngữ nghĩa của lỗi và hủy bỏ](#transaction-reactive-error-cancel)
- [Ranh giới giữa reactive transaction và R2DBC/data-access](#transaction-reactive-resource-boundary)

## <a id="transaction-reactive-context-model">Reactive transaction context: ThreadLocal và Reactor Context</a>

<details>
<summary>Xem chi tiết</summary>

Imperative transaction của Spring truyền trạng thái transaction và resource theo thread hiện tại. Reactive pipeline không bảo đảm một thao tác logic luôn chạy trên cùng một thread, vì vậy reactive transaction management dùng **Reactor Context** thay cho `ThreadLocal` để mang trạng thái transaction.

Mô hình tư duy là context theo subscriber:

```text
subscription
  ↓
Reactor Context chứa trạng thái transaction
  ↓
operator tham gia khi context còn được nhìn thấy
  ↓
thread có thể đổi nhưng transaction context vẫn được giữ
```

Điểm này giải thích vì sao chỉ gọi reactive repository từ một imperative `@Transactional` method không tự biến flow thành reactive transaction, và vì sao thread switch tự nó không phá transaction nếu reactive chain được compose đúng.

Cần tách rõ hai mô hình: `org.springframework.transaction.support.TransactionSynchronizationManager` quản lý trạng thái imperative gắn theo thread, còn package reactive có hạ tầng transaction synchronization dựa trên Reactor Context riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-manager-contract">Quy ước của ReactiveTransactionManager</a>

<details>
<summary>Xem chi tiết</summary>

`ReactiveTransactionManager` là chiến lược trung tâm của reactive transaction. Thay vì trả về `TransactionStatus` theo kiểu đồng bộ, API trả về publisher: `getReactiveTransaction(...)` tạo `Mono<ReactiveTransaction>`, còn commit/rollback trả về `Mono<Void>`.

Hợp đồng này quan trọng vì cả begin, commit và rollback đều có thể cần công việc bất đồng bộ không chặn. Manager cụ thể quyết định cách lấy resource bên dưới, cách gắn resource vào reactive transaction context và backend hỗ trợ được propagation/isolation nào.

Code ứng dụng nên suy nghĩ theo hợp đồng tổng quát nhưng không được giả định mọi reactive manager có khả năng backend giống nhau. Nested transaction, suspension, isolation level, timeout và read-only vẫn phụ thuộc manager/resource cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-method-contract">Quy ước của method @Transactional reactive</a>

<details>
<summary>Xem chi tiết</summary>

Để `@Transactional` dùng ngữ nghĩa reactive transaction, transaction manager phải là reactive và method phải trả về reactive type tham gia vào pipeline. Transaction tồn tại quanh **thời điểm subscription thực thi**, không phải quanh lúc Java code tạo object `Mono` hoặc `Flux`.

```java
@Transactional
public Mono<Order> createOrder(Order order) {
    return orders.save(order)
        .flatMap(saved -> audit.save(saved.id()).thenReturn(saved));
}
```

Nếu method gắn với `ReactiveTransactionManager` trả về giá trị thông thường, đây không chỉ là trường hợp “không có reactive transaction”. Trong Spring Framework 6.1, cặp manager/method này không hợp lệ và transaction interception thất bại với `IllegalStateException`; method trả về kiểu thông thường hoặc `void` cần một imperative `PlatformTransactionManager`. Tương tự, tự `subscribe()` bên trong method tạo một lần thực thi riêng không còn được biểu diễn bởi chain trả về.

Quy tắc thực tế: hãy compose rồi trả về pipeline; để bên gọi/framework thực hiện subscription.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-participation">Cùng tham gia transaction context và phạm vi của TransactionalOperator</a>

<details>
<summary>Xem chi tiết</summary>

Công việc reactive tham gia transaction khi nó thực thi bên trong Reactor Context do hạ tầng transaction của Spring thiết lập. `TransactionalOperator` có thể bọc toàn bộ upstream chain theo operator style, hoặc dùng callback style khi chỉ một số publisher cần nằm trong transaction.

```java
Mono<Void> flow = operator.execute(status ->
    account.debit(from, amount)
        .then(account.credit(to, amount))
).then();
```

Context có thể bị mất khi code thoát khỏi composed chain: tự subscribe, callback API không được bridge vào Reactor, hoặc chuyển công việc sang execution mechanism khác có thể rời transaction scope.

Hãy suy nghĩ theo **việc nằm trong pipeline**, không theo thread identity. Nếu một thao tác không nằm trong publisher chain được compose/trả về và mang transaction context, không nên giả định nó tham gia transaction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-error-cancel">Ngữ nghĩa của lỗi và hủy bỏ</a>

<details>
<summary>Xem chi tiết</summary>

Kết quả reactive transaction đi theo terminal signal của reactive flow. Error thường dẫn tới rollback theo transaction policy; completion thành công dẫn tới commit. Cancellation phức tạp hơn vì publisher bị cancel chưa tạo một successful completion bình thường.

Tài liệu `TransactionalOperator` của Spring nhấn mạnh cancel signal vì downstream operator như `take`, `next`, hoặc client ngắt kết nối có thể cancel công việc upstream. Từ Spring Framework 5.3, cancel signal làm transactional publisher rollback. Vì vậy công việc transactional có thể kết thúc trước khi ứng dụng consume hết các giá trị được phát ra.

Với multi-value publisher, bên tiêu thụ thông thường nên để transactional publisher chạy tới completion thay vì cancel sau khi chỉ đọc một phần sequence. Không nên thiết kế transaction mà tính đúng đắn phụ thuộc vào việc phải đọc hết một `Flux` dài hoặc không giới hạn; với công việc cần tính nguyên tử, nên dùng pipeline hữu hạn có kết quả kết thúc rõ ràng và kiểm thử hành vi cancellation khi operator có thể kết thúc source sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-resource-boundary">Ranh giới giữa reactive transaction và R2DBC/data-access</a>

<details>
<summary>Xem chi tiết</summary>

Reactive transaction management điều phối transaction policy; nó không dạy giao thức data-access. `DatabaseClient`, connection factory, SQL mapping và hành vi driver của Spring R2DBC thuộc tầng data-access. Module này chỉ cần nhắc đủ để giải thích cách tham gia transaction.

Boundary là:

```text
transaction-management
→ reactive transaction bắt đầu, tham gia, commit hoặc rollback khi nào

data-access / R2DBC
→ SQL được gửi thế nào, row được map ra sao, connection hoạt động thế nào
```

Guarantee của reactive transaction cũng bị giới hạn bởi resource manager. Transaction trên một R2DBC connection không tự làm remote HTTP call, Kafka publish hoặc resource không liên quan trở thành atomic. Những trường hợp đó cần coordination pattern tường minh hoặc transaction technology hỗ trợ đúng tập resource.

</details>

- [Quay lại đầu trang](#back-to-top)
