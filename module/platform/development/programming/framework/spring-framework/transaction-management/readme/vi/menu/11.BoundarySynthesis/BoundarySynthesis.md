<a id="back-to-top"></a>

# Tổng hợp ranh giới transaction trong hệ thống thực tế

## Menu
- [Transaction boundary ở tầng service](#transaction-service-layer-boundary)
- [Năng lực của transaction manager và nhiều tài nguyên](#transaction-manager-capabilities)
- [Ranh giới thread và async](#transaction-thread-boundary)
- [Ranh giới lời gọi từ xa](#transaction-remote-boundary)
- [Ranh giới messaging và phối hợp side effect](#transaction-messaging-boundary)
- [Tác vụ kéo dài và transaction scope](#transaction-long-running-boundary)
- [Liên kết sang kiểm thử và chẩn đoán vận hành](#transaction-testing-handoff)
- [Danh sách kiểm tra khi thiết kế transaction boundary](#transaction-design-checklist)

## <a id="transaction-service-layer-boundary">Transaction boundary ở tầng service</a>

<details>
<summary>Xem chi tiết</summary>

Transaction boundary tốt thường trùng với một business unit of work, thường ở tầng ứng dụng/service. Boundary phải đủ cao để bao các thay đổi dữ liệu cần cùng thành công, nhưng không nên rộng tới mức giữ lock/resource trong lúc chạy công việc chậm không liên quan.

```text
request/controller
    ↓
tầng application service ← transaction boundary thường phù hợp ở đây
    ↓
nhiều thao tác repository/data-access
```

Đặt `@Transactional` trên mọi repository helper làm policy bị chia vụn và che mất business unit of work thật. Ngược lại, bọc cả workflow từ xa trong transaction có thể giữ local resource quá lâu. Hãy bắt đầu từ business invariant rồi chọn boundary nhỏ nhất vẫn bảo toàn invariant đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-manager-capabilities">Năng lực của transaction manager và nhiều tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng có thể có nhiều `TransactionManager`, ví dụ các local resource khác nhau hoặc một imperative manager và một reactive manager. Trong Spring Framework 6.1, `@Transactional(transactionManager = "...")` (alias là `value`) cung cấp bean name hoặc qualifier value của manager cần chọn; manager mặc định khi không ghi qualifier được cấu hình riêng.

Việc chọn manager không tạo thêm khả năng mà manager không có. Local manager cho một `DataSource` điều phối resource đó; nó không tự điều phối database thứ hai hoặc message broker. XA/JTA có thể điều phối **transactional resource có khả năng XA**—ví dụ database và JMS provider phù hợp—khi môi trường cùng driver/provider hỗ trợ enlistment. HTTP/RPC service là một ranh giới remote/distributed khác và không trở thành XA-transactional chỉ vì chọn JTA manager; trường hợp đó cần điều phối ở cấp ứng dụng như idempotency, retry, compensation, saga hoặc durable messaging pattern.

Luôn khớp transaction boundary với khả năng của manager: loại resource, suspension, savepoint, isolation, cách thực thi imperative/reactive và điều phối nhiều resource đều phải được xét.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-thread-boundary">Ranh giới thread và async</a>

<details>
<summary>Xem chi tiết</summary>

Imperative transaction của Spring gắn với thread. Chuyển công việc sang thread khác không tự mang theo resource hay trạng thái synchronization của transaction hiện tại. Vì vậy khi kết hợp `@Transactional` với executor hoặc `@Async`, phải xác định rõ transaction thật sự bắt đầu ở đường thực thi nào.

```text
thread A: transactional method ──> submit task
                                  ↓
thread B: task chạy với context riêng
```

Nếu task cần transaction, hãy tạo transaction boundary riêng cho execution path của task thay vì giả định transaction ở thread A đi theo nó. Reactive transaction dùng Reactor Context, nhưng nếu thoát sang asynchronous mechanism ngoài reactive chain thì context đó cũng có thể bị mất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-remote-boundary">Ranh giới lời gọi từ xa</a>

<details>
<summary>Xem chi tiết</summary>

Local Spring transaction không truyền qua HTTP/RPC boundary chỉ vì bên gọi đang transactional. Remote service có process, resource và transaction manager riêng.

Giữ database transaction mở trong lúc chờ remote call làm lock tồn tại lâu hơn và gắn local rollback với network latency nhưng vẫn không tạo distributed atomicity. Nếu remote call thành công rồi local transaction rollback, remote side không tự được hoàn tác.

Workflow từ xa cần pattern điều phối tường minh như idempotency, retry, compensation, saga hoặc durable message tùy bài toán. Module này chỉ sở hữu việc nhận diện transaction boundary; chi tiết pattern phân tán thuộc module architecture/integration phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-messaging-boundary">Ranh giới messaging và phối hợp side effect</a>

<details>
<summary>Xem chi tiết</summary>

Phát message và cập nhật database là hai side effect riêng trừ khi công nghệ transaction thực sự điều phối cả hai resource. Local database transaction một mình không bảo đảm "row đã commit" và "message đã phát" xảy ra nguyên tử.

Vì vậy ứng dụng thường phải phối hợp boundary tường minh. Ví dụ: phát transaction-bound event sau commit, ghi outbox record trong cùng database transaction rồi phát sau, hoặc dùng transaction manager thật sự điều phối được các resource tham gia.

Mỗi cách có mô hình lỗi khác nhau. `AFTER_COMMIT` listener tránh phát event cho database work bị rollback, nhưng crash sau commit và trước khi phát ra bên ngoài vẫn có thể làm mất side effect nếu nó chưa được lưu bền vững. Không được nhầm thời điểm callback với tính nguyên tử phân tán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-long-running-boundary">Tác vụ kéo dài và transaction scope</a>

<details>
<summary>Xem chi tiết</summary>

Transaction giữ các resource hữu hạn như database connection, lock, snapshot và trạng thái transaction manager. Boundary kéo dài qua thời gian chờ người dùng, công việc CPU lớn, truyền file, remote API hoặc thời gian chờ dài làm tăng tranh chấp và phạm vi chịu lỗi.

Một kỹ thuật thiết kế hữu ích là tách phase:

```text
chuẩn bị chậm ngoài transaction
→ thay đổi trạng thái ngắn trong transaction
→ side effect sau commit / tiếp tục bất đồng bộ
```

Không được tách một cách máy móc vì business invariant nguyên tử vẫn phải được bảo toàn. Mục tiêu là giảm thời gian transaction mở nhưng giữ mọi thay đổi trạng thái cần nguyên tử trong cùng unit of work.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-testing-handoff">Liên kết sang kiểm thử và chẩn đoán vận hành</a>

<details>
<summary>Xem chi tiết</summary>

Transaction design phải test và quan sát được, nhưng module này không sở hữu lifecycle của Spring TestContext transaction hay toàn bộ observability stack.

Ở mức ứng dụng, cần kiểm thử kết quả nghiệp vụ khi commit/rollback, tương tác propagation và cách chọn manager. Test-managed transaction, `TransactionalTestExecutionListener` và ngữ nghĩa rollback trong test thuộc Spring Testing module vì vòng đời của chúng khác production service transaction.

Khi chẩn đoán, bằng chứng hữu ích gồm debug log của transaction manager, transaction name, trạng thái active/read-only, rollback-only flag và quan sát vòng đời bằng `TransactionExecutionListener` khi phù hợp. Metrics/tracing chỉ nên quan sát transaction, không được làm thay đổi ngữ nghĩa nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-design-checklist">Danh sách kiểm tra khi thiết kế transaction boundary</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi chốt transaction boundary, hãy trả lời một số câu hỏi:

1. **Business invariant nào phải atomic?** Đây là unit of work thật.
2. **Resource nào tham gia?** Một database, nhiều database, broker hoặc remote service có nhu cầu coordination khác nhau.
3. **Transaction manager nào sở hữu các resource đó?** Kiểm tra mô hình imperative/reactive và khả năng backend.
4. **Cần propagation nào?** Ưu tiên `REQUIRED` trừ khi ngữ nghĩa independent/nested là chủ ý.
5. **Lỗi nào gây rollback?** Làm rõ checked exception và hành vi rollback-only.
6. **Resource bị giữ bao lâu?** Đưa slow work không liên quan ra ngoài khi có thể.
7. **Guarantee dừng ở đâu?** Thread, remote call, message và post-commit work cần boundary tường minh.
8. **Failure sẽ được quan sát và test thế nào?** Transaction policy không chẩn đoán được sẽ rất khó vận hành.

Mục tiêu của Spring transaction management không phải che giấu các quyết định này. Nó cung cấp lớp trừu tượng thống nhất để ứng dụng mô tả chúng một cách chủ động.

</details>

- [Quay lại đầu trang](#back-to-top)
