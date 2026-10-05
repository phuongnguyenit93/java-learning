<a id="back-to-top"></a>

# Lớp trừu tượng transaction và chiến lược TransactionManager

## Menu
- [TransactionManager như một chiến lược trừu tượng](#transaction-manager-strategy)
- [PlatformTransactionManager và transaction imperative](#transaction-platform-manager)
- [ReactiveTransactionManager và transaction reactive](#transaction-reactive-manager)
- [TransactionDefinition và chính sách transaction](#transaction-definition)
- [TransactionStatus và trạng thái thực thi transaction](#transaction-status)
- [Begin, commit, rollback và completion](#transaction-lifecycle)
- [Chọn và định danh transaction manager](#transaction-manager-selection)
- [Local resource transaction và transaction phối hợp](#transaction-local-vs-global)

## <a id="transaction-manager-strategy">TransactionManager như một chiến lược trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionManager` là marker chung cho các implementation transaction manager của Spring. Nhờ đó hạ tầng framework có thể nói về “một transaction manager” mà chưa cần giả định cách thực thi là imperative hay reactive.

Hai chiến lược chính:

- `PlatformTransactionManager` cho workflow imperative, thường có transaction context gắn theo thread;
- `ReactiveTransactionManager` cho workflow reactive, nơi context được truyền qua Reactor.

Transaction policy của ứng dụng nên dựa trên ranh giới trừu tượng này thay vì API begin/commit riêng của nhà cung cấp. Tuy nhiên manager cụ thể vẫn quan trọng vì nó quyết định resource nào được điều phối và khả năng nào—savepoint, suspension, isolation—thực sự tồn tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-platform-manager">PlatformTransactionManager và transaction imperative</a>

<details>
<summary>Xem chi tiết</summary>

`PlatformTransactionManager` là interface trung tâm cho imperative transaction trong Spring. Hợp đồng của nó cố ý nhỏ:

```text
getTransaction(TransactionDefinition) → TransactionStatus
commit(TransactionStatus)
rollback(TransactionStatus)
```

`getTransaction` không phải lúc nào cũng có nghĩa “tạo physical transaction mới”. Manager sẽ đọc propagation: có thể tạo mới, tham gia transaction đang có, trả về non-transactional status hoặc từ chối lời gọi.

`commit` cũng tôn trọng trạng thái rollback-only. Gọi `commit(status)` là yêu cầu hoàn tất theo trạng thái hiện tại; nếu transaction đã rollback-only thì completion có thể là rollback và bên gọi phía ngoài có thể nhận `UnexpectedRollbackException`.

Manager cụ thể ánh xạ hợp đồng này xuống JDBC, JPA, JTA hoặc imperative resource khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-reactive-manager">ReactiveTransactionManager và transaction reactive</a>

<details>
<summary>Xem chi tiết</summary>

`ReactiveTransactionManager` giữ cùng vai trò policy nhưng cho thực thi reactive không chặn. Các thao tác vòng đời trả về reactive publisher vì cả việc lấy resource lẫn completion đều có thể bất đồng bộ.

```text
getReactiveTransaction(definition) → Mono<ReactiveTransaction>
commit(transaction)                → Mono<Void>
rollback(transaction)              → Mono<Void>
```

Khác biệt khái niệm quan trọng là cách truyền context. Imperative manager thường bind resource theo thread; reactive manager dùng subscriber/Reactor context. Bộ thuật ngữ policy—propagation, isolation, read-only, timeout—vẫn quen thuộc, nhưng mức hỗ trợ của backend và cơ chế context khác nhau.

Không nên trộn hai mô hình chỉ vì cả hai cùng thuộc `TransactionManager`. Code phải dùng execution model khớp với manager và resource tham gia.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-definition">TransactionDefinition và chính sách transaction</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionDefinition` mô tả **đặc tính transaction được yêu cầu**. Nó là đầu vào policy cho transaction manager, không phải bằng chứng backend sẽ hỗ trợ mọi tùy chọn giống nhau.

Các thuộc tính chung gồm transaction name, propagation, isolation level, timeout và cờ read-only. Metadata declarative từ `@Transactional` được chuyển thành `TransactionAttribute`, mở rộng mô hình policy này bằng rollback rule và qualifier/label phục vụ hạ tầng transaction.

```text
policy request
  REQUIRED
  DEFAULT isolation
  timeout = 30s
  readOnly = true
        ↓
TransactionManager diễn giải theo context hiện tại + khả năng backend
```

Một số attribute chỉ có ý nghĩa khi physical transaction mới được tạo. Scope đang tham gia không thể đổi isolation level của physical transaction đã chạy từ trước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-status">TransactionStatus và trạng thái thực thi transaction</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionStatus` biểu diễn trạng thái của một lần thực thi imperative transaction theo góc nhìn `PlatformTransactionManager`. Hạ tầng hoặc code ứng dụng có thể biết transaction có mới không, đã hoàn tất chưa, có rollback-only không, có savepoint không và có thể đánh dấu rollback-only tường minh.

Không được nhầm “logical scope mới” với `isNewTransaction()`. Một `REQUIRED` method có thể tạo logical boundary riêng nhưng vẫn tham gia outer physical transaction; status mô tả execution do manager quản lý chứ không chỉ phản ánh annotation nesting.

`TransactionStatus` còn cung cấp khả năng savepoint/flush khi manager/resource hỗ trợ. Đây là các hook tổng quát; mức hỗ trợ thực tế vẫn phụ thuộc transaction system cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-lifecycle">Begin, commit, rollback và completion</a>

<details>
<summary>Xem chi tiết</summary>

Ở mức khái quát, transaction workflow đi qua cùng một máy trạng thái:

```text
resolve transaction definition
→ tạo mới hoặc tham gia theo propagation
→ chạy công việc của ứng dụng
→ chọn commit hoặc rollback
→ chạy completion/synchronization callback
→ release hoặc resume resource
```

“Commit” không chỉ là một method được gọi sau khi Java code chạy thành công. Trước completion, trạng thái rollback-only, lỗi callback, timeout hoặc lỗi transaction system vẫn có thể làm kết quả thay đổi.

Sau completion, transaction không còn dùng để ghi thay đổi mới. Việc cleanup/resume resource thuộc hạ tầng manager/synchronization để code ứng dụng thông thường không phải tự bind/unbind transaction resource.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-manager-selection">Chọn và định danh transaction manager</a>

<details>
<summary>Xem chi tiết</summary>

Khi chỉ có một manager phù hợp, hạ tầng transaction của Spring có thể dùng nó làm mặc định. Nếu ứng dụng có nhiều manager, transaction boundary phải xác định manager nào sở hữu công việc.

`@Transactional` cung cấp `transactionManager` (alias `value`) như một qualifier. Khi cần, cấu hình framework cũng có thể chọn manager mặc định bằng `TransactionManagementConfigurer`.

```java
@Transactional(transactionManager = "ordersTxManager")
public void updateOrder() { ... }
```

Việc chọn manager mang ý nghĩa hành vi, không chỉ là cách đặt tên. Chọn sai manager có thể khiến thao tác trên resource thực tế không tham gia transaction mà code tưởng đã mở. Nên đặt tên/qualify manager theo resource ownership và execution model, đặc biệt khi imperative và reactive manager cùng tồn tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-local-vs-global">Local resource transaction và transaction phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

Local transaction manager thường điều phối một resource family, ví dụ một JDBC `DataSource` hoặc một JPA `EntityManagerFactory`. Cách này đơn giản và hiệu quả khi business invariant nằm trong resource boundary đó.

Coordinated/global transaction giải quyết bài toán khác: nhiều transactional resource phải cùng chia sẻ một kết quả nguyên tử. Trong imperative stack của Spring, JTA integration có thể cung cấp khả năng này khi môi trường triển khai và resource tham gia thực sự hỗ trợ.

Không được suy ra tính nguyên tử toàn cục chỉ từ interface `PlatformTransactionManager`. Cùng interface có thể đại diện local manager đơn giản hoặc global coordinator. Phải kiểm tra khả năng của manager/resource cụ thể và cân nhắc chi phí điều phối phân tán trước khi chọn thay cho pattern ở cấp ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
