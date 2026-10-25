<a id="back-to-top"></a>

# Đồng bộ tài nguyên và tham gia transaction

## Menu
- [Tài nguyên gắn với transaction và cơ chế tái sử dụng](#transaction-bound-resources)
- [Vòng đời transaction synchronization và cleanup](#transaction-synchronization)
- [TransactionSynchronizationManager trong transaction imperative](#transaction-synchronization-manager)
- [Hạ tầng data-access có nhận biết transaction](#transaction-aware-infrastructure)
- [Điều phối transaction và cơ chế data-access cụ thể](#transaction-resource-boundary)

## <a id="transaction-bound-resources">Tài nguyên gắn với transaction và cơ chế tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều thao tác data-access của cùng một resource factory tham gia một imperative transaction, Spring-aware access thông thường sẽ dùng lại resource đã gắn với transaction thay vì mở resource độc lập ngoài transaction manager. Spring biểu diễn điều này bằng **transaction-bound resource**, được định danh theo resource như `DataSource` hoặc session factory.

Ví dụ, JDBC transaction manager có thể bind connection holder của một `DataSource` vào transaction context hiện tại. Spring-aware data-access code sẽ lookup resource đã bind và tái sử dụng nó trong unit of work.

```text
transaction bắt đầu
  ↓
resource được bind vào context
  ↓
repository call A ─┐
repository call B ─┼→ dùng chung transactional resource
repository call C ─┘
  ↓
completion → unbind/release resource
```

Resource object cụ thể khác nhau giữa JDBC, JPA, Hibernate và integration khác. Coordinated transaction có thể enlist nhiều resource khác nhau; mỗi resource có integration/binding model riêng. Phần transaction-management cần hiểu là cách participating resource được bind/reuse, còn API resource chi tiết thuộc data-access module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-synchronization">Vòng đời transaction synchronization và cleanup</a>

<details>
<summary>Xem chi tiết</summary>

Chỉ bind resource là chưa đủ. Component còn cần lifecycle callback để flush, release, suspend, resume hoặc chạy công việc quanh transaction completion. Spring biểu diễn các callback này bằng `TransactionSynchronization`.

Các callback imperative thường gặp gồm `beforeCommit`, `beforeCompletion`, `afterCommit` và `afterCompletion`; synchronization object cũng có thể tham gia suspend/resume và flush. Transaction manager kích hoạt synchronization, gọi callback đúng phase rồi xóa synchronization khi completion kết thúc.

Không nên dùng synchronization callback thay cho transaction manager. Callback chỉ quan sát hoặc phối hợp công việc quanh một transaction đã tồn tại; nó không tự tạo atomicity.

Cleanup phải an toàn sau completion. Không được để resource vẫn bind sau commit/rollback vì request sau trên cùng thread có thể vô tình nhìn thấy transaction state cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-synchronization-manager">TransactionSynchronizationManager trong transaction imperative</a>

<details>
<summary>Xem chi tiết</summary>

`org.springframework.transaction.support.TransactionSynchronizationManager` trong imperative stack là delegate trung tâm quản lý resource và synchronization **theo thread**. Đây là API ở tầng hạ tầng; business code thông thường không nên tự bind resource.

Nó có thể cho biết:

- synchronization có active không;
- có transaction thực tế đang active hay không;
- transaction name/read-only/isolation metadata hiện tại;
- resource theo một key đã được bind chưa.

Resource-management code có thể gọi `getResource(key)` để dùng lại connection/session đã bind. Transaction manager chịu trách nhiệm activate synchronization và bind/unbind resource của nó.

Class này thuộc mô hình tư duy imperative. Reactive transaction management có `TransactionSynchronizationManager` khác trong reactive package, làm việc với subscriber context thay vì trạng thái thread-local.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-aware-infrastructure">Hạ tầng data-access có nhận biết transaction</a>

<details>
<summary>Xem chi tiết</summary>

Hạ tầng data-access của Spring tham gia transaction bằng cách đọc transaction context thay vì bỏ qua nó. Helper như `DataSourceUtils` có thể lấy connection nhận biết Spring-managed JDBC transaction; các công nghệ resource được hỗ trợ khác cũng có integration tương tự.

Nhờ vậy repository/data-access code thông thường không cần tự gọi transaction API:

```text
service transaction boundary
        ↓
transaction manager bind resource
        ↓
Spring-aware data-access helper tìm bound resource
        ↓
repository work tự tham gia transaction
```

Điều kiện quan trọng là đường đi data-access phải dùng hạ tầng có khả năng tham gia Spring transaction đó. Tự mở resource thô, độc lập ngoài cơ chế Spring có thể bỏ qua transaction mà ứng dụng mong đợi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-resource-boundary">Điều phối transaction và cơ chế data-access cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

Transaction management sở hữu **policy điều phối**: resource tham gia khi nào, completion diễn ra lúc nào và callback nào được gọi. Nó không sở hữu mọi hành vi data-access cụ thể.

Ví dụ:

```text
module này
→ resource binding, synchronization, participation, lifecycle

data-access module
→ JdbcTemplate/JdbcClient/DatabaseClient, SQL execution, mapping

persistence/ORM module
→ trạng thái entity, flush mode, mapping, lazy loading
```

Đôi khi cần vượt boundary một chút để giải thích ảnh hưởng, ví dụ “JPA flush có thể xảy ra trước commit”, nhưng API chi tiết thuộc module khác. Giữ boundary rõ giúp nội dung transaction không biến thành tutorial JDBC/JPA/R2DBC.

</details>

- [Quay lại đầu trang](#back-to-top)
