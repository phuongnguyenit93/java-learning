# ĐỐI TƯỢNG — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Danh tính, trạng thái và hành vi của đối tượng

Nhận diện instance qua identity, state và behavior; dùng Order minh họa biến đổi mà giữ danh tính.

↓ **Từ ba mặt của object → đi sâu vào giá trị của biến và đối số**

### Bài 2 — Tham chiếu, giá trị và cách truyền đối tượng

Hiểu phép gán tham chiếu và Java pass-by-value qua sửa state, gán lại local và swap.

↓ **Từ tham số nhận bản sao reference → nhận diện aliasing**

### Bài 3 — Nhiều tham chiếu cùng trỏ đến một đối tượng

Truy vết nhiều alias qua biến, field, collection và phương thức trên cùng instance mutable.

↓ **Từ cùng instance → xét hai instance khác nhau vẫn có thể bằng về giá trị**

### Bài 4 — Phân biệt cùng đối tượng với bằng nhau về giá trị

So sánh identity với value equality, cài equals/hashCode đúng và dùng hash collections an toàn.

↓ **Từ hợp đồng equality → xét state của object có thay đổi được không**

### Bài 5 — Đối tượng có thể thay đổi và đối tượng bất biến

Phân biệt mutation và tạo giá trị mới, thiết kế immutable cả ở thành phần lồng nhau.

↓ **Từ khả năng mutable → quyết định sao chép nông hay sâu**

### Bài 6 — Sao chép nông và sao chép sâu

Chọn copy đúng cấp với Customer, collection, mảng, graph có chu trình và dữ liệu định danh.

↓ **Từ copy tách biệt → phân tích chia sẻ state có chủ đích**

### Bài 7 — Chia sẻ đối tượng và tác động của thay đổi trạng thái

Theo dõi state lan qua các thành phần, snapshot và stale reference, làm rõ refresh và đồng bộ.

↓ **Từ tham chiếu cùng chia sẻ → xử lý tham chiếu đang không có object**

### Bài 8 — Giá trị rỗng và đối tượng chưa tồn tại

Phân biệt local chưa gán, null, empty object và kết quả không tìm thấy; dùng Optional đúng ngữ nghĩa.

↓ **Từ object, alias và vắng mặt → ghép vào luồng tương tác nhiều đối tượng**

### Bài 9 — Theo dõi tương tác của các đối tượng trong một tình huống

Tổng hợp identity, tham chiếu và state vào checkout cùng sequence diagram và nhánh lỗi.

**Mối liên hệ:** Từ identity, state, behavior của một instance, chương làm rõ việc truyền giá trị tham chiếu, aliasing và equality giữa các instance. Sau đó người học xét tính mutable, phạm vi sao chép và tác động của shared state; hiểu sự vắng mặt qua null/Optional; cuối cùng áp dụng vào luồng checkout nhiều object. Các bài về hash, copy và null bổ sung những chiều an toàn riêng, không mặc định là điều kiện tiên quyết của bài liền kề.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Danh tính, trạng thái và hành vi của đối tượng | Tham chiếu, giá trị và cách truyền đối tượng | Nhiều tham chiếu cùng trỏ đến một đối tượng | Phân biệt cùng đối tượng với bằng nhau về giá trị | Đối tượng có thể thay đổi và đối tượng bất biến | Sao chép nông và sao chép sâu | Chia sẻ đối tượng và tác động của thay đổi trạng thái | Giá trị rỗng và đối tượng chưa tồn tại | Theo dõi tương tác của các đối tượng trong một tình huống |
| Vấn đề cần hiểu | Đối tượng nào tồn tại và nó có thể thay đổi thế nào? | Java sao chép gì khi gán hoặc truyền object? | Ai đang giữ cùng một tham chiếu object? | Cùng đối tượng hay bằng nhau theo hợp đồng? | State của instance có thể thay đổi sau khởi tạo không? | Bản sao còn chia sẻ đối tượng con nào? | Thay đổi ở object chung xuất hiện ở đâu, khi nào? | Không có reference, object rỗng hay lỗi tìm dữ liệu? | Ai gọi ai và trạng thái đổi như thế nào trong checkout? |
| Giá trị đối với OOP/module | Mô hình identity, trạng thái và hành vi có điều kiện. | Dự đoán chính xác mutation và reassignment. | Nhận diện các mutation lan qua alias. | Chọn phép so sánh và hash thích hợp. | Thiết kế mutable có kiểm soát hoặc immutable an toàn. | Áp dụng shallow/deep copy theo ownership. | Phân biệt live state, snapshot và owner cập nhật. | Tránh NPE và thiết kế absence rõ ràng. | Truy vết Cart, Inventory, PaymentGateway, Order và Notifier. |
| Tư duy hình thành | Nhìn object như một instance có vòng đời. | Tách giá trị biến khỏi đối tượng được tham chiếu. | Vẽ đồ thị tham chiếu tới từng đối tượng. | Định nghĩa ý nghĩa equality của entity/value object. | Xét toàn bộ đường làm thay đổi object graph. | Sao chép đúng phần mutable của đồ thị. | Lần theo đối tượng và thời điểm đọc lại. | Chọn kiểu biểu diễn đúng lý do vắng mặt. | Kết hợp call graph, log, state và ngoại lệ. |