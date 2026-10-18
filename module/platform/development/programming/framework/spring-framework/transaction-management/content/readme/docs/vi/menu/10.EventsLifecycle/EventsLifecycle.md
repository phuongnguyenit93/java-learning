<a id="back-to-top"></a>

# Sự kiện theo transaction và lifecycle hook

## Menu
- [TransactionalEventListener và các giai đoạn transaction](#transaction-event-listener)
- [Thực thi dự phòng khi không có transaction](#transaction-event-fallback)
- [Reactive transaction event trong Spring 6.1](#transaction-reactive-events)
- [Truy cập tài nguyên sau khi transaction hoàn tất](#transaction-post-completion-resources)
- [TransactionExecutionListener so với transaction synchronization](#transaction-execution-listener)

## <a id="transaction-event-listener">TransactionalEventListener và các giai đoạn transaction</a>

<details>
<summary>Xem chi tiết</summary>

`@TransactionalEventListener` gắn application-event listener vào một transaction phase. Phase mặc định là `AFTER_COMMIT`, phù hợp khi side effect chỉ nên xảy ra sau khi transaction phát event đã commit thành công.

Các phase gồm `BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK` và `AFTER_COMPLETION`. `AFTER_COMPLETION` chạy sau cả commit lẫn rollback, vì vậy không nên dùng khi listener cần khẳng định riêng rằng commit đã thành công nếu không kiểm tra kết quả theo cách khác.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreated event) {
    // side effect chỉ có ý nghĩa sau commit
}
```

Cơ chế này đồng bộ thời điểm callback với transaction; nó không biến mọi side effect bên ngoài thành một phần nguyên tử của cùng transaction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-event-fallback">Thực thi dự phòng khi không có transaction</a>

<details>
<summary>Xem chi tiết</summary>

Mặc định, `@TransactionalEventListener` **không được gọi** nếu event được publish ngoài active transaction, vì không có transaction phase để listener bám vào. Đặt `fallbackExecution = true` cho phép listener vẫn chạy.

Fallback làm thay đổi hợp đồng hành vi: cùng một listener có thể chạy trong hai tình huống khác nhau—một lần được phối hợp với transaction phase và một lần không có transaction. Điều này có thể phù hợp với thông báo có tính idempotent, nhưng nguy hiểm nếu listener giả định trạng thái database đã commit.

Chỉ bật fallback khi hành vi ngoài transaction đã được định nghĩa và kiểm thử rõ ràng. Không nên bật chỉ để listener "luôn chạy".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-events">Reactive transaction event trong Spring 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Từ Spring Framework 6.1, transactional event listener hỗ trợ cả thread-bound transaction do `PlatformTransactionManager` quản lý và reactive transaction do `ReactiveTransactionManager` quản lý.

Reactive transaction không thể lấy trạng thái transaction từ biến thread-local. Vì vậy transaction context phải được mang trong **event source**. `TransactionalEventPublisher` là helper dành cho việc này: nó phát event với source là `TransactionContext` hiện tại do Reactor quản lý.

Điểm quan trọng không chỉ là annotation mà là cách transaction context đến được listener. Với imperative transaction, thread-bound context hiện tại được nhìn thấy trực tiếp. Với reactive transaction, quá trình publish phải bảo toàn Reactor transaction context một cách tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-post-completion-resources">Truy cập tài nguyên sau khi transaction hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

`AFTER_COMMIT`, `AFTER_ROLLBACK` và `AFTER_COMPLETION` chạy **sau khi** kết quả transaction đã được quyết định. Spring cảnh báo rằng transactional resource vẫn có thể còn active và truy cập được ở thời điểm đó dù transaction đã hoàn tất.

Đây là pitfall dễ nhầm: data-access code chạy trong listener có thể vẫn nhìn thấy original resource và trông như đang "tham gia", nhưng thay đổi mới sẽ không được commit vào transaction đã kết thúc.

Nếu công việc sau completion cần ghi dữ liệu bền vững riêng, hãy chạy nó trong **transaction thực sự mới**, ví dụ qua một proxied service method riêng dùng `PROPAGATION_REQUIRES_NEW` khi transaction manager hỗ trợ. Boundary mặc định `REQUIRED` vẫn có thể nhìn thấy/tham gia resource đã bind của transaction vừa hoàn tất và không biến write mới thành một transaction mới có thể commit. Cần xử lý failure/retry riêng; việc resource còn truy cập được không có nghĩa original transaction vẫn có thể commit thêm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-execution-listener">TransactionExecutionListener so với transaction synchronization</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionExecutionListener`, được bổ sung trong Spring Framework 6.1, là callback quan sát vòng đời không giữ trạng thái và có thể được đăng ký với transaction manager hỗ trợ cấu hình listener. Nó quan sát các bước tạo/hoàn tất transaction, chủ yếu phù hợp cho quan sát và thống kê ở cấp manager.

Vai trò này khác `TransactionSynchronization`. Synchronization gắn với một transaction context cụ thể và điều phối callback/resource như `beforeCommit`, `afterCommit`, `afterCompletion`. Execution listener quan sát execution lifecycle của transaction manager chứ không phải một resource-bound participant.

Mô hình tư duy:

```text
TransactionExecutionListener
→ quan sát lifecycle ở transaction-manager level

TransactionSynchronization
→ điều phối callback/resource bên trong transaction context
```

</details>

- [Quay lại đầu trang](#back-to-top)
