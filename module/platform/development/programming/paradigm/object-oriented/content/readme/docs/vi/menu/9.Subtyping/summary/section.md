# KIỂU CON — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Phân biệt kế thừa mã với quan hệ kiểu con

Phân biệt tái sử dụng implementation với quan hệ subtype; dùng NotificationSender, helper và AbstractReport để chọn extends/implements/composition có cơ sở.

↓ **Từ việc phân biệt dùng lại code và khả năng thay thế → kiểm tra ý nghĩa thực tế của quan hệ is-a.**

### Bài 2 — Quan hệ “là một” và tính thay thế

Phân tích is-a và khả năng thay thế theo hành vi qua ShippingPolicy; kết quả phí có thể khác nhau nhưng mọi implementation phải giữ hợp đồng.

↓ **Từ hợp đồng thay thế → xem Java quyết định quan hệ subtype qua tên và khai báo kiểu như thế nào.**

### Bài 3 — Tương thích kiểu theo tên và theo cấu trúc

Giải thích Java dùng nominal typing còn kiểu theo cấu trúc có quy tắc khác; adapter cho phép SDK cũ tham gia hợp đồng tường minh.

↓ **Từ quan hệ kiểu tĩnh → phân biệt loại tham chiếu với lớp runtime quyết định cách thực thi.**

### Bài 4 — Kiểu khai báo và kiểu thực tế của đối tượng

Đọc declared type và runtime class của đối tượng để hiểu quyền gọi method, override, overload, field và static hiding.

↓ **Từ declared/runtime type → sử dụng subtype qua supertype mà vẫn giữ chính đối tượng gốc.**

### Bài 5 — Chuyển từ kiểu con sang kiểu cha

Theo dõi upcasting ở Customer/PremiumCustomer và interface: một object, nhiều góc nhìn type và cách dùng đa hình.

↓ **Từ upcast an toàn → xét điều kiện phải kiểm tra khi muốn truy cập khả năng riêng của subtype.**

### Bài 6 — Ép về kiểu con và kiểm tra kiểu

Kiểm tra downcasting với instanceof, pattern matching, getClass(), null và giới hạn type erasure để tránh lỗi runtime.

↓ **Từ các phép chuyển kiểu → đánh giá điều kiện đúng/sai của một implementation được mở rộng.**

### Bài 7 — Giữ đúng hợp đồng khi mở rộng kiểu

Đưa LSP vào thực hành bằng contract của DiscountPolicy; kiểm tra miền đầu vào, kết quả, ngoại lệ và hành vi mở rộng.

↓ **Từ quy tắc hợp đồng chung → kiểm chứng bằng phản ví dụ hình chữ nhật và hình vuông.**

### Bài 8 — Phản ví dụ hình chữ nhật và hình vuông

Kiểm chứng hành vi sai của Square extends mutable Rectangle và thiết kế lại hợp đồng Shape/resize để bảo vệ invariant.

**Mối liên hệ:** Chương làm rõ cách một kiểu con có thể xuất hiện trong hệ kiểu Java và được dùng qua hợp đồng kiểu cha. Sau khi phân biệt kế thừa mã, subtyping danh nghĩa và runtime dispatch, người học có thể upcast để khai thác đa hình, downcast có kiểm tra khi thực sự cần và kiểm thử LSP qua kết quả, trạng thái, ngoại lệ. Rectangle–Square đóng vai trò phản chứng giúp đánh giá tính thay thế bằng cam kết cụ thể thay vì tên lớp.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Phân biệt kế thừa mã với quan hệ kiểu con | Quan hệ “là một” và tính thay thế | Tương thích kiểu theo tên và theo cấu trúc | Kiểu khai báo và kiểu thực tế của đối tượng | Chuyển từ kiểu con sang kiểu cha | Ép về kiểu con và kiểm tra kiểu | Giữ đúng hợp đồng khi mở rộng kiểu | Phản ví dụ hình chữ nhật và hình vuông |
| Vấn đề cần hiểu | Code reuse khác subtype ở điểm nào? | Kiểu con có giữ vai trò kiểu cha không? | Java có cần khai báo implements không? | Kiểu biến khác kiểu object ra sao? | Sang supertype mất gì ở API nhìn thấy? | Cast xuống khi nào hợp lệ? | Subtype giữ lời hứa nào của contract? | Vì sao Square không thay được mutable Rectangle? |
| Giá trị đối với OOP/module | Chọn kế thừa hay composition đúng nghĩa | Đặt nền tảng LSP và behavioral substitutability | Bảo đảm quan hệ kiểu rõ ràng, tích hợp adapter | Hiểu compile-time vs runtime dispatch | Dùng đa hình qua API supertype | Tránh ClassCastException, dùng type check đúng | Mở rộng implementation mà vẫn đúng nghiệp vụ | Nhận diện hậu điều kiện bị phá qua ví dụ thực |
| Tư duy hình thành | Từ trùng code sang quan hệ vai trò | Từ is-a bằng tên sang is-a bằng hợp đồng | Từ trùng chữ ký sang danh tính kiểu | Từ một biến sang hai góc nhìn type/runtime | Từ lớp cụ thể sang hợp đồng chung | Từ ép kiểu tùy tiện sang kiểm tra khả năng | Từ @Override hợp lệ sang kiểm chứng correctness | Từ trực giác hình học sang contract tests |