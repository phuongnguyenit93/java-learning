<a id="back-to-top"></a>

# Vì sao cần quản lý transaction

## Menu
- [Spring Transaction Management tồn tại để làm gì?](#transaction-purpose)
- [Transaction như một đơn vị công việc của ứng dụng](#transaction-unit-of-work)
- [Ranh giới service và từng lời gọi repository riêng lẻ](#transaction-service-boundary)
- [Spring bổ sung gì phía trên transaction API gốc?](#transaction-framework-value)
- [Bảo đảm của local transaction dừng ở đâu?](#transaction-local-boundary)

## <a id="transaction-purpose">Spring Transaction Management tồn tại để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Transaction management tồn tại vì một thao tác nghiệp vụ thường thay đổi nhiều phần trạng thái, nhưng kết quả nghiệp vụ vẫn phải hành xử như một đơn vị. Nếu order đã được ghi nhưng việc giữ chỗ tồn kho thất bại, ứng dụng thường không được để database ở trạng thái dở dang.

Spring transaction management cung cấp một lớp trừu tượng thống nhất để ứng dụng mô tả **unit of work bắt đầu/kết thúc ở đâu, transaction policy nào áp dụng và transaction manager nào thực thi trên resource cụ thể**. Spring không tự tạo ra tính nguyên tử của database; nó điều phối khả năng transaction gốc của resource qua một mô hình lập trình nhất quán.

Module này xoay quanh bốn câu hỏi:

```text
Unit of work nằm ở đâu?
→ Transaction manager nào sở hữu nó?
→ Lời gọi nào tham gia?
→ Điều gì làm commit hoặc rollback?
```

Nên nắm các câu hỏi đó trước khi học thuộc attribute của `@Transactional`. Annotation chỉ là một cách biểu diễn mô hình này.

Learning path của module đi theo chính các câu hỏi đó: trước hết xây dựng mô hình về transaction manager và cách resource tham gia, sau đó học declarative policy, rollback và propagation, tiếp theo so sánh cách quản lý transaction bằng code với mô hình reactive, rồi cuối cùng nối transaction event với các quyết định boundary trong hệ thống thực tế. Mỗi chapter sau đều thêm một lớp vào cùng mental model về unit of work thay vì mở ra một feature rời rạc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-unit-of-work">Transaction như một đơn vị công việc của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

Transaction hữu ích nhất khi boundary khớp với **business unit of work**: tập thay đổi trạng thái nhỏ nhất phải cùng thành công hoặc cùng thất bại để giữ một invariant.

Ví dụ, đặt hàng có thể cần lưu order và giữ chỗ tồn kho trong cùng một kết quả nhất quán. Unit of work không phải “một repository method”; nó là thao tác nghiệp vụ sở hữu invariant đó.

```text
placeOrder()
  ├─ lưu order
  ├─ reserve stock
  └─ ghi trạng thái payment

tất cả cần cho một kết quả nhất quán
```

Mô hình tư duy này quyết định cách dùng propagation và rollback về sau. Boundary quá nhỏ có thể commit trạng thái dở dang; boundary quá lớn có thể giữ lock/connection trong lúc chạy việc không liên quan. Hãy bắt đầu từ invariant rồi mới chọn transaction scope.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-service-boundary">Ranh giới service và từng lời gọi repository riêng lẻ</a>

<details>
<summary>Xem chi tiết</summary>

Repository call biểu diễn thao tác data-access; service method thường biểu diễn ý định nghiệp vụ. Vì vậy tầng ứng dụng/service thường là nơi phù hợp hơn để định nghĩa transaction gom nhiều repository call.

```java
@Transactional
public void placeOrder(Command command) {
    orders.insert(command.order());
    inventory.reserve(command.sku());
}
```

Nếu mỗi repository method mở và commit transaction độc lập, `orders.insert()` có thể commit trước khi `inventory.reserve()` thất bại. Service-level boundary cho phép cả hai thao tác cùng tham gia một transaction khi resource manager hỗ trợ.

Đây là nguyên tắc thiết kế, không phải quy tắc mọi service method đều phải transactional. Luồng chỉ đọc, quy trình chỉ gọi từ xa hoặc thao tác có resource boundary khác có thể cần scope khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-framework-value">Spring bổ sung gì phía trên transaction API gốc?</a>

<details>
<summary>Xem chi tiết</summary>

Không có Spring, ứng dụng vẫn có thể quản lý transaction trực tiếp qua JDBC, JPA, R2DBC, JTA hoặc API của nhà cung cấp. Vấn đề không phải các API đó không làm transaction được; vấn đề là mỗi công nghệ có vòng đời và chi tiết tích hợp khác nhau.

Spring bổ sung mô hình policy chung và tách nó khỏi chiến lược resource:

```text
transaction policy
  propagation / isolation / timeout / rollback
            ↓
Spring TransactionManager abstraction
            ↓
JDBC / JPA / R2DBC / JTA / chiến lược resource khác
```

Sự tách biệt này cho phép code declarative và programmatic cùng dùng một bộ khái niệm transaction, còn manager cụ thể ánh xạ chúng xuống khả năng của backend. Hành vi riêng của resource vẫn quan trọng—lớp trừu tượng của Spring không thể tự tạo isolation level, nested transaction hay distributed transaction mà backend không hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-local-boundary">Bảo đảm của local transaction dừng ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Local transaction chỉ bảo đảm những gì transaction manager và các resource tham gia thực sự điều phối được. Nó không tự đi qua ranh giới process, thread khác, resource manager không liên quan hoặc dịch vụ bên ngoài.

```text
local DB transaction
  ✓ row do transaction/resource đó quản lý
  ✗ HTTP service ở process khác
  ✗ async task tùy ý trên thread khác
  ✗ broker không hỗ trợ coordinated transaction
```

Boundary này quan trọng vì từ “transactional” dễ bị hiểu sai thành “toàn bộ workflow nghiệp vụ là nguyên tử”. Trong hệ thống phân tán, giả định đó thường sai.

Các chapter sau sẽ phân biệt transaction-bound event, `REQUIRES_NEW`, coordinated transaction manager, outbox-style design và asynchronous boundary. Không cơ chế nào nên bị hiểu như cách mở rộng phổ quát cho một local transaction duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)
