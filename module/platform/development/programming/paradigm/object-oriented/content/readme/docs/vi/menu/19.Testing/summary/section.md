# KIỂM THỬ — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Kiểm thử hành vi công khai và hợp đồng đối tượng

Kiểm thử như khách hàng của SeatInventory: xuất phát từ contract và các kết quả quan sát được, bao gồm cả lỗi không mutate và ngữ nghĩa snapshot.

↓ **Từ hợp đồng public → kiểm tra thời điểm trạng thái hợp lệ được thiết lập**

### Bài 2 — Kiểm thử hàm khởi tạo và trạng thái không hợp lệ

Đi sâu vào thời điểm object được tạo: constructor, record hoặc builder không được công bố state sai; soát giá trị biên, liên trường, overflow và aliasing.

↓ **Từ constructor hợp lệ → bảo vệ invariant trong các lần thay đổi về sau**

### Bài 3 — Kiểm thử điều kiện bất biến và chuyển trạng thái

Từ object mới hợp lệ sang toàn bộ vòng đời Wallet: thử cạnh chuyển trạng thái ACTIVE/FROZEN/CLOSED, trạng thái sau lỗi và chuỗi thao tác.

↓ **Từ hành vi một đối tượng có state → áp cùng contract qua các implementation khác nhau**

### Bài 4 — Kiểm thử các cách thực hiện cùng một giao diện

Chuyển sang tình huống nhiều implementation IntSet: đóng gói một contract suite parameterized dùng Supplier/factory, kiểm tra các class khác nhau bằng cùng chuẩn.

↓ **Từ dùng chung contract test → đánh giá điều kiện thay thế của subtype theo LSP**

### Bài 5 — Kiểm thử tính thay thế của kiểu con

Từ việc chạy chung test sang nguyên lý LSP: chứng minh subtype không thắt chặt input hợp lệ hoặc làm yếu postcondition; DiscountPolicy được test qua Checkout.

↓ **Từ đúng hợp đồng kiểu con → chủ động cô lập cộng tác viên ngoài SUT**

### Bài 6 — Fake, stub, mock và đối tượng cộng tác

Mở rộng phạm vi SUT sang các collaborator: phân biệt fake lưu state, stub cung cấp đầu vào gián tiếp và mock/spy kiểm chứng hiệu ứng bên ngoài.

↓ **Từ cách dùng test double → thiết kế dependency để có thể thay thế an toàn**

### Bài 7 — Thiết kế để thay thế phụ thuộc khi kiểm thử

Thiết kế class để có thể thay collaborator: dùng port, constructor DI, composition root và Clock.fixed nhằm tạo test seam không phụ thuộc mạng/thời gian thực.

↓ **Từ test seam có sẵn → chọn assertion không trói thuật toán nội bộ**

### Bài 8 — Tránh kiểm thử phụ thuộc quá chặt vào chi tiết bên trong

Kiểm tra lại chính chất lượng assertion: giữ test bám kết quả Quote và tác dụng phụ có nghĩa, tránh chốt số lần gọi helper/PriceCatalog khi không được hứa.

↓ **Từ test bền với refactor → xây regression suite khi thiết kế thay đổi**

### Bài 9 — Kiểm thử hồi quy khi thay đổi thiết kế

Khi thay đổi thiết kế, dùng hợp đồng và test hồi quy làm mốc, chạy hai phiên bản và kiểm tra ngưỡng miễn phí 100.000 cent bằng oracle độc lập.

↓ **Từ giữ hợp đồng của một API → kiểm chứng cả chuỗi phối hợp nhiều object**

### Bài 10 — Kiểm thử tương tác giữa nhiều đối tượng

Tổng hợp ở workflow nhiều đối tượng: xác minh reserve/charge/release/send đúng nhánh và thứ tự; kết hợp interaction test với fake có trạng thái.

**Mối liên hệ:** Học phần đi từ cam kết hành vi công khai đến tính hợp lệ ngay khi constructor hoàn tất, rồi bảo vệ invariant xuyên các trạng thái và kiểm chứng hợp đồng qua nhiều implementation/subtype. Sau đó người học xây test doubles và điểm thay thế phụ thuộc để cô lập trách nhiệm, lựa chọn assertion bền với refactor, chạy regression suite và cuối cùng kiểm tra sự phối hợp nhiều đối tượng. Các bài contract-test và test-double có những điểm giao nhau có chủ đích nhưng đóng vai trò riêng: định nghĩa điều cần đúng, tổ chức cách kiểm, thiết kế điểm thay thế và xác nhận workflow thực tế.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 | Bài 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Kiểm thử hành vi công khai và hợp đồng đối tượng | Kiểm thử hàm khởi tạo và trạng thái không hợp lệ | Kiểm thử điều kiện bất biến và chuyển trạng thái | Kiểm thử các cách thực hiện cùng một giao diện | Kiểm thử tính thay thế của kiểu con | Fake, stub, mock và đối tượng cộng tác | Thiết kế để thay thế phụ thuộc khi kiểm thử | Tránh kiểm thử phụ thuộc quá chặt vào chi tiết bên trong | Kiểm thử hồi quy khi thay đổi thiết kế | Kiểm thử tương tác giữa nhiều đối tượng |
| Vấn đề cần hiểu | Đối tượng hứa gì với client và khi thất bại? | Object được khởi tạo hợp lệ theo những điều kiện nào? | State/invariant và transition được giữ qua các lệnh thế nào? | Những implementation của interface có cùng hành vi không? | Subtype có thực sự thay kiểu cha được không? | Thay collaborator bằng loại test double nào? | Làm sao làm chủ I/O, thời gian và dependency trong test? | Assert nào thuộc contract, assert nào chỉ bám nội bộ? | Refactor có giữ nguyên hành vi quan sát được không? | Nhiều object gọi nhau đúng điều kiện và thứ tự chưa? |
| Giá trị đối với OOP/module | Test public behavior không khóa implementation | Không công bố object sai hoặc aliasing | Bảo toàn invariant của entity mutable | Thử nhiều adapter/implementation bằng một suite | Phát hiện vi phạm LSP ngoài khả năng compiler | Test logic mà không gọi hạ tầng thật | Cô lập service, tăng tính tái lập | Refactor hợp lệ không làm test đỏ giả | Ngăn hồi quy tại boundary và wiring | Ngăn thu tiền/gửi mail/hoàn tồn kho sai |
| Tư duy hình thành | Viết cam kết trước test | Kiểm tra biên tạo object | Nhìn theo state machine và đường lỗi | Thiết kế test có thể tái sử dụng | So pre/postcondition của abstraction | Chọn fake/stub/mock theo mục tiêu | Thiết kế từ khả năng thay thế phụ thuộc | Phân biệt hiệu ứng thật với detail | Dùng oracle độc lập, baseline và CI | Phân tích workflow theo nhánh và hậu quả |