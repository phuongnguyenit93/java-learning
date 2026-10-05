<a id="back-to-top"></a>

# Rollback và ngữ nghĩa lỗi

## Menu
- [Quy tắc rollback mặc định cho lỗi checked và unchecked](#transaction-rollback-defaults)
- [Rollback rule và no-rollback rule](#transaction-rollback-rules)
- [Trạng thái rollback-only và UnexpectedRollbackException](#transaction-rollback-only)
- [Xử lý exception và rủi ro khi nuốt lỗi](#transaction-exception-handling)
- [Rollback từ kết quả Vavr Try và Future](#transaction-return-value-rollback)

## <a id="transaction-rollback-defaults">Quy tắc rollback mặc định cho lỗi checked và unchecked</a>

<details>
<summary>Xem chi tiết</summary>

Spring cần một quy tắc nhất quán để biến exception thành kết quả của transaction. Với declarative transaction, quy tắc mặc định trong Spring Framework 6.1 là: `RuntimeException` hoặc `Error` thoát ra khỏi method sẽ gây rollback, còn checked `Exception` mặc định không gây rollback.

Quy tắc này được đánh giá khi exception đi ra khỏi transactional method và quay về transaction interceptor. Chính interceptor mới quyết định commit hay rollback; việc một exception được tạo hoặc bị bắt ở bên trong method chưa đủ để quyết định kết quả cuối cùng.

```java
@Transactional
public void placeOrder() {
    inventory.reserve();
    throw new IllegalStateException("payment failed");
}
```

`IllegalStateException` là unchecked exception nên transaction sẽ rollback, trừ khi có no-rollback rule cụ thể hơn ghi đè hành vi đó. Không nên suy ra rằng mọi checked business exception cũng rollback; nếu một checked exception phải rollback, hãy mô tả chính sách đó tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-rollback-rules">Rollback rule và no-rollback rule</a>

<details>
<summary>Xem chi tiết</summary>

Rollback policy là một phần của hợp đồng transaction, không chỉ là hệ quả của exception hierarchy. `@Transactional` cho phép khai báo `rollbackFor` / `noRollbackFor` theo exception type, hoặc các biến thể dùng tên class khi không thuận tiện tham chiếu trực tiếp type.

Nên ưu tiên rule theo type vì ít mơ hồ hơn. Rule theo chuỗi tên dùng cơ chế substring matching nên có thể match rộng hơn dự kiến; một pattern như `"Exception"` thường quá rộng và dễ làm thay đổi chính sách ngoài ý muốn.

```java
@Transactional(
    rollbackFor = BusinessCheckedException.class,
    noRollbackFor = OptimisticWarning.class
)
public void process() throws BusinessCheckedException { ... }
```

Khi nhiều rule cùng match, Spring dùng rule mạnh nhất. Hãy xem override này là business policy: cần giải thích vì sao một checked exception làm toàn bộ unit of work không còn hợp lệ, hoặc vì sao một unchecked exception vẫn được phép commit.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-rollback-only">Trạng thái rollback-only và UnexpectedRollbackException</a>

<details>
<summary>Xem chi tiết</summary>

Một transaction có thể đã mất khả năng commit từ trước khi outer method đi tới commit point. Spring biểu diễn trạng thái này bằng rollback-only marker. Một inner scope cùng tham gia physical transaction có thể đánh dấu transaction là rollback-only; outer scope vẫn có thể tiếp tục chạy Java code nhưng transaction đó không còn commit thành công được nữa.

Đây là mô hình tư duy của `UnexpectedRollbackException`: bên gọi phía ngoài yêu cầu commit nhưng physical transaction đã bị đánh dấu rollback-only. Spring ném exception để bên gọi không hiểu nhầm rằng commit đã thành công.

```text
outer REQUIRED scope
    ↓ cùng tham gia một physical transaction
inner REQUIRED scope
    ↓ đánh dấu rollback-only
outer tiếp tục chạy
    ↓ yêu cầu commit
UnexpectedRollbackException
```

Không nên coi `UnexpectedRollbackException` là success path bình thường rồi chỉ catch bỏ qua. Cần tìm scope nào đã đánh dấu rollback-only và xem lại transaction boundary hoặc propagation policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-exception-handling">Xử lý exception và rủi ro khi nuốt lỗi</a>

<details>
<summary>Xem chi tiết</summary>

Xử lý exception bên trong transactional method có thể vô tình che failure khỏi Spring. Nếu code catch exception, chuyển nó thành một return value bình thường và không đánh dấu transaction rollback-only, interceptor sẽ thấy method kết thúc thành công và có thể commit.

```java
@Transactional
public boolean reserve() {
    try {
        repository.update();
        riskyOperation();
        return true;
    } catch (RuntimeException ex) {
        return false; // interceptor thấy một lần return bình thường
    }
}
```

Cách xử lý phụ thuộc ý nghĩa nghiệp vụ: rethrow failure, chuyển nó thành exception khác nằm trong rollback rule, hoặc gọi `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` nếu method bắt buộc phải return bình thường nhưng unit of work đã không còn hợp lệ.

Đánh dấu rollback bằng code làm business code phụ thuộc trực tiếp vào hạ tầng transaction của Spring, vì vậy chỉ nên dùng khi thực sự cần. Nếu luồng exception biểu đạt lỗi rõ ràng hơn thì nên ưu tiên cách đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-return-value-rollback">Rollback từ kết quả Vavr Try và Future</a>

<details>
<summary>Xem chi tiết</summary>

Spring 6.1 nhận biết một số dạng failure nằm trong return value ngoài exception bị ném ra. Nếu có Vavr, một `Try` trả về ở trạng thái failure có thể đưa exception bên dưới tới transaction interceptor. Spring 6.1 cũng kiểm tra `Future` / `CompletableFuture`: nếu future **đã hoàn tất exceptional ngay tại thời điểm transactional method trả về**, failure đó có thể được đánh giá ngay tại boundary này.

Các tích hợp dựa trên return value không bỏ qua rollback rule của transaction attribute. Spring vẫn đánh giá failure bên dưới bằng cùng quyết định rollback (`rollbackOn`) như với exception bị ném ra: checked cause vẫn mặc định không rollback, còn `noRollbackFor` tường minh có thể chặn rollback cho unchecked failure match rule.

Điều này không có nghĩa imperative transaction sẽ đi theo asynchronous work sang thread khác. Quyết định vẫn được đưa ra tại method-return boundary. Nếu future còn chưa hoàn tất khi method trả về rồi thất bại sau đó, transaction đã hoàn tất trước sẽ không bị rollback ngược thời gian.

```text
method trả về CompletableFuture đã failed
→ interceptor đã nhìn thấy failure
→ đánh giá rollback rule

method trả về CompletableFuture chưa hoàn tất
→ transaction boundary kết thúc theo kết quả đang nhìn thấy
→ async failure xảy ra sau đó nằm ngoài transaction đã hoàn tất
```

Khi kết hợp `@Transactional` và `@Async`, phải giữ rõ distinction này; truyền transaction context qua thread là một vấn đề khác.

</details>

- [Quay lại đầu trang](#back-to-top)
