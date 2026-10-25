<a id="back-to-top"></a>

# Quản lý transaction bằng code

## Menu
- [Declarative và programmatic demarcation](#transaction-declarative-vs-programmatic)
- [TransactionTemplate và điều khiển bằng callback](#transaction-template)
- [Điều khiển trực tiếp với PlatformTransactionManager](#transaction-direct-manager)
- [TransactionalOperator và các cách quản lý reactive bằng code](#transaction-transactional-operator)
- [Đánh đổi khi điều khiển transaction bằng code](#transaction-programmatic-tradeoffs)

## <a id="transaction-declarative-vs-programmatic">Declarative và programmatic demarcation</a>

<details>
<summary>Xem chi tiết</summary>

Declarative và programmatic transaction management cùng dùng các transaction abstraction của Spring; khác nhau ở nơi boundary được biểu diễn. `@Transactional` đặt policy bên ngoài method body và thường dễ đọc hơn cho service boundary ổn định. Programmatic API làm việc điều khiển transaction hiện rõ trong code.

Programmatic control hữu ích khi boundary thay đổi động, chỉ một phần nhỏ của method cần transaction, hoặc code phải phản ứng trực tiếp với transaction status. Đánh đổi là coupling: business code bắt đầu import và phải hiểu Spring transaction API.

Nên ưu tiên declarative demarcation cho unit of work thông thường ở application service. Chỉ dùng programmatic API khi nó thực sự giúp boundary chính xác hơn, không phải chỉ vì cảm giác "tường minh" hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-template">TransactionTemplate và điều khiển bằng callback</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionTemplate` là convenience API Spring khuyến nghị cho imperative programmatic transaction. Nó bọc một `PlatformTransactionManager` và chạy callback trong transaction do Spring quản lý, tự xử lý phần boilerplate begin/commit/rollback quanh callback.

```java
Order result = transactionTemplate.execute(status -> {
    Order order = repository.save(new Order());
    auditRepository.record(order.id());
    return order;
});
```

Template có thể cấu hình propagation, isolation, timeout, read-only và name. Sau khi cấu hình ổn định, instance có thể được dùng chung; tuy nhiên thay đổi cấu hình trên shared template sẽ thay đổi policy của các lần execute sau.

Hãy dùng callback để biểu diễn unit of work. Không nên nhét một workflow lớn, không liên quan vào cùng template chỉ vì API cho phép làm như vậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-direct-manager">Điều khiển trực tiếp với PlatformTransactionManager</a>

<details>
<summary>Xem chi tiết</summary>

API imperative chung ở mức thấp nhất là `PlatformTransactionManager`: lấy `TransactionStatus` bằng `getTransaction(definition)`, sau đó gọi `commit(status)` hoặc `rollback(status)`.

```java
TransactionStatus status = txManager.getTransaction(definition);
try {
    doWork();
} catch (RuntimeException | Error ex) {
    txManager.rollback(status);
    throw ex;
}
txManager.commit(status);
```

Cách này cho quyền điều khiển đầy đủ nhưng cũng khiến code ứng dụng chịu trách nhiệm xử lý exception, cleanup và nested participation đúng cách. Ví dụ bắt cả `RuntimeException` lẫn `Error`, tương ứng với kỳ vọng rollback thông thường của Spring cho unchecked failure; code production vẫn phải quyết định rõ checked failure sẽ được biểu diễn thế nào. Lưu ý `commit(status)` nằm ngoài `try` chứa công việc nghiệp vụ: nếu chính commit thất bại thì transaction đã đi vào completion, không nên mù quáng gọi thêm rollback. `TransactionStatus` cho biết transaction có mới hay không, có savepoint hay không, có bị đánh dấu rollback-only hay chưa; nó cũng hỗ trợ đánh dấu rollback-only tường minh.

Nên ưu tiên `TransactionTemplate` trừ khi thực sự cần điều khiển manager trực tiếp. Tự viết lại workflow transaction thủ công làm tăng nguy cơ sai commit/rollback.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-transactional-operator">TransactionalOperator và các cách quản lý reactive bằng code</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionalOperator` là counterpart reactive của `TransactionTemplate`. Nó làm việc với `ReactiveTransactionManager` và tạo scope cho reactive sequence để công việc chạy khi subscribe có thể tham gia transaction context.

```java
TransactionalOperator operator = TransactionalOperator.create(txManager);

Mono<Order> result = repository.save(order)
    .flatMap(saved -> audit.save(saved.id()).thenReturn(saved))
    .as(operator::transactional);
```

Operator style áp transaction semantics cho publisher chain upstream đi qua operator. Callback style có thể biểu diễn scope rõ hơn khi có nhiều publisher nhưng chỉ một số thao tác cần tham gia transaction.

Không nên gọi `block()` chỉ để áp cách suy nghĩ imperative vào reactive flow. Reactive transaction context đi qua Reactor context, không dựa vào giả định cùng một thread luôn được giữ nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-programmatic-tradeoffs">Đánh đổi khi điều khiển transaction bằng code</a>

<details>
<summary>Xem chi tiết</summary>

Programmatic control đánh đổi sự tách biệt của declarative style để lấy độ chính xác cục bộ. Nó phù hợp khi transaction scope phụ thuộc runtime branch, khi cần giữ transaction ngắn và tách khỏi slow work ngoài transaction, hoặc khi rollback-only decision phải được đưa ra tường minh.

Các nhược điểm cũng cần nhìn rõ:

- Spring transaction API xuất hiện trong code ứng dụng;
- callback lồng nhau có thể làm business flow khó đọc;
- dùng manager trực tiếp dễ lặp logic hạ tầng;
- trộn declarative và programmatic boundary mà không có mô hình tư duy rõ ràng có thể tạo cách tham gia transaction bất ngờ.

Một câu hỏi review hữu ích là: **code có thực sự cần điều khiển transaction động, hay một hợp đồng service-level ổn định đã đủ?** Nếu boundary ổn định, declarative transaction management thường đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
