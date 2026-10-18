<a id="back-to-top"></a>

# Propagation và cách lời gọi tham gia transaction

## Menu
- [Physical transaction và logical transaction scope](#transaction-physical-logical)
- [PROPAGATION_REQUIRED và cùng tham gia transaction](#transaction-propagation-required)
- [PROPAGATION_REQUIRES_NEW và áp lực tài nguyên](#transaction-propagation-requires-new)
- [PROPAGATION_NESTED và ngữ nghĩa savepoint](#transaction-propagation-nested)
- [SUPPORTS, MANDATORY, NOT_SUPPORTED và NEVER](#transaction-propagation-supports)
- [Tạm ngưng và khôi phục transaction với REQUIRES_NEW và NOT_SUPPORTED](#transaction-propagation-suspension)
- [Rollback-only khi dùng chung transaction](#transaction-propagation-rollback-only)

## <a id="transaction-physical-logical">Physical transaction và logical transaction scope</a>

<details>
<summary>Xem chi tiết</summary>

Propagation mô tả một **logical transactional scope** sẽ xử lý thế nào khi được đi vào trong lúc có thể đã tồn tại scope khác. Điểm quan trọng là phải tách logical scope khỏi physical resource transaction.

Hai method cùng dùng `REQUIRED` có thể tạo hai logical scope riêng nhưng cùng tham gia một physical database transaction. Ngược lại, `REQUIRES_NEW` tạo physical transaction độc lập cho inner scope.

```text
serviceA() @Transactional(REQUIRED)  ← logical scope A
    └─ serviceB() @Transactional(REQUIRED) ← logical scope B
          cả hai có thể dùng chung physical transaction T1
```

Rollback-only marker xuất phát từ các quyết định ở logical scope nhưng cuối cùng ảnh hưởng tới kết quả physical transaction. Khi phân biệt được hai lớp này, `UnexpectedRollbackException`, `REQUIRES_NEW` và `NESTED` sẽ dễ lý giải hơn nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-required">PROPAGATION_REQUIRED và cùng tham gia transaction</a>

<details>
<summary>Xem chi tiết</summary>

`PROPAGATION_REQUIRED` là mặc định của Spring. Nếu chưa có transaction, scope hiện tại sẽ tạo một transaction mới. Nếu đã có transaction, scope sẽ tham gia transaction đó thay vì tạo thêm physical transaction.

Vì vậy `REQUIRED` phù hợp với luồng service-facade trong đó nhiều thao tác repository phải cùng thành công hoặc cùng thất bại. Scope tham gia thường dùng đặc tính của transaction đang tồn tại; isolation, timeout hoặc read-only khai báo cục bộ không tự tạo physical transaction thứ hai.

Hệ quả quan trọng là rollback-only có thể lan tới shared transaction. Nếu inner `REQUIRED` scope quyết định transaction phải rollback, outer scope không thể biến physical transaction đó trở lại trạng thái có thể commit.

Với transaction manager hỗ trợ, có thể bật `validateExistingTransaction` nếu muốn các khai báo isolation/read-only không tương thích bị từ chối thay vì âm thầm tham gia đặc tính của transaction hiện có.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-requires-new">PROPAGATION_REQUIRES_NEW và áp lực tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

`PROPAGATION_REQUIRES_NEW` luôn chạy scope hiện tại trong một physical transaction độc lập. Nếu có outer transaction, Spring sẽ tạm ngưng resource/synchronization của transaction đó trong khả năng của transaction manager, chạy inner transaction rồi khôi phục outer context sau đó.

Inner transaction có quyết định commit/rollback, isolation, timeout và read-only riêng. Lock của inner transaction có thể được giải phóng ngay khi inner transaction kết thúc, không cần chờ outer transaction.

Đánh đổi là áp lực tài nguyên. Với local transaction kiểu JDBC, outer transaction có thể vẫn giữ một connection trong khi inner transaction phải lấy thêm connection khác. Khi concurrency cao, điều này có thể làm cạn connection pool hoặc góp phần gây deadlock. Spring Reference cảnh báo pool phải được sizing phù hợp; khi dùng pattern này, pool nên lớn hơn số thread đồng thời ít nhất một connection.

Chỉ nên dùng `REQUIRES_NEW` cho công việc thực sự cần độc lập, không nên dùng như cách chung chung để "sửa" hành vi rollback.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-nested">PROPAGATION_NESTED và ngữ nghĩa savepoint</a>

<details>
<summary>Xem chi tiết</summary>

`PROPAGATION_NESTED` khác `REQUIRES_NEW`. Ngữ nghĩa điển hình của Spring dùng **một physical transaction với các savepoint**. Inner nested scope có thể rollback về savepoint của nó trong khi outer physical transaction vẫn tiếp tục.

```text
physical transaction T1
  savepoint S1
    nested work
    ↓ lỗi
  rollback về S1
  outer work có thể tiếp tục
```

Khả năng này phụ thuộc transaction manager và resource bên dưới. Spring Reference mô tả nó chủ yếu cho JDBC resource transaction có savepoint, ví dụ `DataSourceTransactionManager`. Không được mặc định rằng mọi JPA, JTA hoặc reactive manager đều hỗ trợ cùng ngữ nghĩa nested.

Chỉ chọn `NESTED` khi partial rollback bên trong cùng một physical transaction đúng là mô hình mong muốn; nếu không, flow sẽ khó lý giải và khó vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-supports">SUPPORTS, MANDATORY, NOT_SUPPORTED và NEVER</a>

<details>
<summary>Xem chi tiết</summary>

Các propagation mode còn lại mô tả điều kiện đối với transaction context hiện tại:

- `SUPPORTS`: tham gia nếu có transaction, nếu không thì chạy mà không có transaction thực tế. Khi synchronization được bật, nó vẫn có thể tạo synchronization scope nên không phải lúc nào cũng giống hoàn toàn với "không có hạ tầng transaction".
- `MANDATORY`: bắt buộc phải có transaction đang tồn tại, nếu không sẽ fail.
- `NOT_SUPPORTED`: chạy ngoài transaction và tạm ngưng transaction hiện có nếu manager hỗ trợ.
- `NEVER`: bắt buộc không được có transaction; nếu đang có transaction thì fail.

Các mode này hữu ích khi hợp đồng của method thực sự yêu cầu hoặc cấm transaction. Không nên dùng chỉ vì tên của chúng có vẻ "rõ ràng hơn"; propagation phải thể hiện một boundary invariant có thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-suspension">Tạm ngưng và khôi phục transaction với REQUIRES_NEW và NOT_SUPPORTED</a>

<details>
<summary>Xem chi tiết</summary>

Suspension là một khả năng của transaction manager, không phải đặc tính phổ quát của mọi backend. `REQUIRES_NEW` và `NOT_SUPPORTED` cần tạm ngưng transaction context bên ngoài để inner scope có thể chạy với trạng thái transaction khác.

Về mặt mô hình tư duy, Spring giữ lại resource và synchronization đã bind của outer transaction, tạm thời gỡ chúng khỏi execution context trong lúc inner scope chạy rồi khôi phục lại sau đó. **Tạm ngưng không có nghĩa outer transaction đã commit.**

Một số manager không thể thực hiện suspension thật sự nếu thiếu tích hợp nền tảng. Ví dụ JTA suspension phụ thuộc vào việc có thể truy cập Jakarta `TransactionManager` phù hợp. Vì vậy cần kiểm tra hợp đồng của manager cụ thể thay vì giả định mọi propagation constant có hành vi backend giống nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-propagation-rollback-only">Rollback-only khi dùng chung transaction</a>

<details>
<summary>Xem chi tiết</summary>

Khi các `REQUIRED` scope lồng nhau cùng dùng một physical transaction, mỗi logical scope vẫn có thể quyết định shared transaction phải rollback. Ví dụ, exception có thể thoát qua inner transactional boundary khiến scope đó đánh dấu shared transaction rollback-only, dù bên gọi phía ngoài sau đó bắt hoặc chuyển đổi exception. Nếu exception được bắt hoàn toàn bên trong inner method rồi method trả về bình thường, bản thân việc đó không làm Spring interceptor tự chọn rollback, trừ khi code đánh dấu rollback-only tường minh hoặc một rollback rule khác được kích hoạt.

Outer scope có thể không biết quyết định đó và cuối cùng vẫn gọi commit. Spring sẽ ném `UnexpectedRollbackException`, vì trả về success trong tình huống transaction thực tế rollback sẽ gây hiểu nhầm.

Đây không phải lỗi của `REQUIRED`; hành vi này bảo vệ tính minh bạch của kết quả transaction. Nếu công việc bên trong phải được phép thất bại mà không làm outer physical transaction thất bại, cần xem lại unit-of-work boundary. Tùy yêu cầu, giải pháp có thể là `REQUIRES_NEW`, `NESTED` dựa trên savepoint, cơ chế bù trừ hoặc tách service boundary khác.

</details>

- [Quay lại đầu trang](#back-to-top)
