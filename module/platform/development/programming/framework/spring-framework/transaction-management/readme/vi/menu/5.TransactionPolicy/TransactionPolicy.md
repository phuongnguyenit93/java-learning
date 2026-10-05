<a id="back-to-top"></a>

# Thuộc tính transaction và thiết kế ranh giới

## Menu
- [Chính sách transaction như một hợp đồng tại ranh giới](#transaction-policy-model)
- [Giá trị mặc định của @Transactional trong Spring 6.1](#transaction-defaults)
- [Isolation và kiểm tra transaction hiện có](#transaction-isolation)
- [Timeout, read-only và transaction labels](#transaction-policy-hints)
- [Chọn transaction boundary có phạm vi phù hợp](#transaction-boundary-placement)

## <a id="transaction-policy-model">Chính sách transaction như một hợp đồng tại ranh giới</a>

<details>
<summary>Xem chi tiết</summary>

Transaction attribute tạo thành một **hợp đồng tại ranh giới**: chúng mô tả method sẽ hành xử thế nào khi đi vào hạ tầng transaction và quan hệ của scope đó với transaction đang tồn tại.

Hợp đồng trả lời các câu hỏi:

- tham gia transaction hiện có hay tạo/tạm ngưng transaction?
- yêu cầu isolation level và timeout nào?
- công việc có read-only không?
- manager nào thực thi?
- failure nào gây rollback?

Đây là policy của ứng dụng. Transaction manager sau đó dịch policy xuống resource bên dưới, nơi một số tùy chọn có thể được hỗ trợ khác nhau. Nên xem attribute như một policy thống nhất, không phải các công tắc annotation độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-defaults">Giá trị mặc định của @Transactional trong Spring 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 cung cấp cho `@Transactional` một policy mặc định thực dụng:

```text
propagation = REQUIRED
isolation   = DEFAULT
readOnly    = false
timeout     = mặc định của transaction system bên dưới
rollback    = RuntimeException và Error theo mặc định
```

`DEFAULT` isolation giao quyết định cho transaction system bên dưới. Timeout mặc định cũng lấy từ transaction system (hoặc không có timeout tường minh nếu backend không hỗ trợ).

Các giá trị mặc định này phù hợp với nhiều service method nhưng không bảo đảm mọi backend có hành vi giống nhau. Chỉ ghi đè khi có yêu cầu rõ ràng. Cấu hình quá nhiều ở từng method làm policy khó hiểu và có thể không có tác dụng nếu method chỉ tham gia physical transaction đã tồn tại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-isolation">Isolation và kiểm tra transaction hiện có</a>

<details>
<summary>Xem chi tiết</summary>

Isolation là đặc tính của **physical transaction**. Khai báo isolation level chỉ có tác dụng khi scope thực sự tạo transaction mới; `REQUIRED` scope đang tham gia không thể đổi isolation của transaction đang chạy mà nó join.

Vì vậy Spring mô tả isolation annotation như policy dành cho newly started transaction, thường là `REQUIRED` khi chưa có transaction hoặc `REQUIRES_NEW`.

Theo mặc định, scope đang tham gia khá linh hoạt: isolation/read-only khai báo cục bộ có thể bị bỏ qua để dùng đặc tính của outer transaction. Với manager hỗ trợ, có thể bật kiểm tra nghiêm ngặt (`validateExistingTransaction`) để từ chối isolation không tương thích và các read-only mismatch nằm trong cơ chế validation của manager.

Isolation phải được chọn từ yêu cầu nhất quán dữ liệu và hành vi của database; module này không thay thế nội dung chuyên sâu về isolation/anomaly của database.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-policy-hints">Timeout, read-only và transaction labels</a>

<details>
<summary>Xem chi tiết</summary>

Timeout, read-only và label tinh chỉnh transaction policy nhưng mức độ ràng buộc khác nhau.

**Timeout** áp cho transaction mới được tạo và yêu cầu manager/backend kết thúc hoặc báo lỗi khi công việc vượt thời gian cấu hình. Cách thực thi cụ thể phụ thuộc resource.

**Read-only** là một gợi ý cho transaction subsystem. Nó có thể giúp tối ưu nhưng hợp đồng của Spring không bảo đảm mọi lần ghi đều bị từ chối. Manager không hiểu gợi ý này có thể bỏ qua nó.

**Label** là chuỗi mô tả gắn với Spring transaction attribute. Transaction manager có thể diễn giải label thành manager-specific option hoặc chỉ dùng cho mô tả.

Không nên dùng các attribute này thay business authorization hay data validation. Chúng mô tả execution policy của transaction, không phải quy tắc đúng/sai của nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-boundary-placement">Chọn transaction boundary có phạm vi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

Transaction có phạm vi phù hợp phải chứa mọi thay đổi trạng thái cần cho một invariant và loại ra công việc không cần giữ cùng resource.

Quá hẹp:

```text
lưu order    → commit
reserve stock → fail
```

Hệ thống có thể để lộ trạng thái nghiệp vụ dở dang.

Quá rộng:

```text
mở DB transaction
→ remote HTTP call
→ xử lý file lớn
→ chờ phụ thuộc user
→ commit
```

Connection và lock bị giữ lâu không cần thiết, trong khi remote failure vẫn không trở thành atomic với local database.

Nên đặt boundary quanh chuyển đổi trạng thái nghiệp vụ, thường ở method thuộc tầng service/ứng dụng. Đưa công việc chậm hoặc việc không liên quan ra ngoài khi có thể, rồi phối hợp side effect sau commit một cách tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)
