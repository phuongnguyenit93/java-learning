# TÁI CẤU TRÚC — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Nhận diện dấu hiệu cần cải thiện thiết kế

Xác lập khi nào mã gây chi phí sửa đổi: code smell là dấu hiệu để điều tra, cần bằng chứng từ thay đổi thực và kiểm thử trước khi chọn kỹ thuật.

↓ **Từ chẩn đoán vấn đề → chọn phép biến đổi nhỏ nhất: đổi tên đúng ý định**

### Bài 2 — Đổi tên để làm rõ ý định

Tên tốt biểu đạt hành động và đơn vị ở nơi sử dụng. Rename cần giữ nguyên số tiền, ngoại lệ và rà soát tên được framework hoặc API bên ngoài sử dụng.

↓ **Từ tên rõ ràng → tách các bước đang chen chúc trong một method/class**

### Bài 3 — Tách phương thức và tách lớp

Extract Method làm rõ bước xử lý; Extract Class tách các lý do thay đổi. Bài dùng checkout để chứng minh định giá và định dạng có thể thay đổi độc lập.

↓ **Từ tách trách nhiệm → đặt phép xử lý ở chính object sở hữu dữ liệu**

### Bài 4 — Di chuyển hành vi về đối tượng chịu trách nhiệm

Move Method đưa phép tính thành tiền vào OrderLine, tổng đơn/ưu đãi vào Order và giữ payment gateway ở service. Trách nhiệm đi theo thông tin và quy tắc sở hữu.

↓ **Từ chuyển trách nhiệm → bảo vệ dữ liệu bên trong đối tượng đã nhận trách nhiệm**

### Bài 5 — Đóng gói trường dữ liệu và cấu trúc nội bộ

Encapsulate Field và Collection chặn client sửa cấu trúc lưu trữ, chuyển mọi mutation sang API có kiểm tra và bảo vệ snapshot đọc.

↓ **Từ API đóng gói → gom các nhánh theo loại vào hành vi đa hình khi thật sự cần**

### Bài 6 — Thay điều kiện theo loại bằng đa hình

Khi nhiều nhánh theo loại lặp, DeliveryPolicy phân phối hành vi giao hàng; service chỉ chọn policy rồi gọi cùng hợp đồng, không cần giữ công thức mỗi loại.

↓ **Từ lựa chọn hành vi đa hình → sử dụng collaborator qua composition thay vì extends để tái dùng**

### Bài 7 — Thay kế thừa bằng kết hợp

Composition biến quan hệ mượn chức năng qua extends thành hợp tác MessageChannel, có thể thay Email/SMS và bọc retry mà không làm AlertService kế thừa adapter.

↓ **Từ thêm trừu tượng hợp lý → cân nhắc loại bỏ tầng chuyển tiếp thiếu giá trị**

### Bài 8 — Gộp hoặc loại bỏ trừu tượng không cần thiết

Gộp tầng middle man hoặc hierarchy thừa khi không thêm trách nhiệm; giữ interface ở ranh giới DB, thời gian, thanh toán hoặc plugin có hợp đồng thật.

↓ **Từ chọn cấu trúc phù hợp → bảo đảm mọi refactor bằng vòng lặp kiểm thử bảo vệ**

### Bài 9 — Cải thiện thiết kế từng bước với kiểm thử bảo vệ

Bài kết thúc chương bằng vòng lặp thay đổi nhỏ có safety-net tests, bảo toàn public behavior, exception và side effect; dùng ngưỡng pricing để bắt regression cụ thể.

**Mối liên hệ:** Tái cấu trúc bắt đầu bằng nhận diện vấn đề dựa trên code smell và chi phí thay đổi thực tế. Từ đó người học đổi tên, tách phương thức/lớp và chuyển hành vi về đối tượng sở hữu, rồi thu hẹp API dữ liệu để giữ invariant. Hai hướng tiếp theo là đa hình để loại nhánh theo loại lặp lại và composition để thay quan hệ kế thừa không đúng; bước gộp abstraction là góc nhìn bổ sung tránh thiết kế quá mức. Mọi phép biến đổi phải được dẫn dắt và kiểm chứng bằng kiểm thử bảo vệ, giữ hợp đồng hành vi đã công bố.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Nhận diện dấu hiệu cần cải thiện thiết kế | Đổi tên để làm rõ ý định | Tách phương thức và tách lớp | Di chuyển hành vi về đối tượng chịu trách nhiệm | Đóng gói trường dữ liệu và cấu trúc nội bộ | Thay điều kiện theo loại bằng đa hình | Thay kế thừa bằng kết hợp | Gộp hoặc loại bỏ trừu tượng không cần thiết | Cải thiện thiết kế từng bước với kiểm thử bảo vệ |
| Vấn đề cần hiểu | Những dấu hiệu nào thực sự gây chi phí bảo trì? | Làm sao đặt tên diễn tả đúng ý định/hợp đồng? | Khi nào tách method hay class để tăng cohesion? | Hành vi thuộc về object nào? | Các đường nào làm lộ và phá trạng thái nội bộ? | Các nhánh theo loại nào nên thay bằng polymorphism? | Quan hệ nào nên chuyển từ extends sang collaborator? | Những abstraction nào là tầng chuyển tiếp thừa? | Refactor từng bước thế nào để hành vi không đổi? |
| Giá trị đối với OOP/module | Phân loại smell từ bằng chứng thay đổi | Đổi tên đúng ý định và kiểm tra compatibility | Trích các bước pricing và format với contract giữ nguyên | Phân bố OrderLine/Order/CheckoutService đúng trách nhiệm | Bảo vệ invariant qua encapsulation và snapshot | Chiến lược DeliveryPolicy và dynamic dispatch | Kết hợp AlertService/MessageChannel và Decorator | Loại middle man nhưng giữ IO boundaries | Safety-net tests cho ngưỡng, lỗi và hiệu ứng |
| Tư duy hình thành | Đo lường chi phí trước khi thay thiết kế | Diễn đạt mục đích tại nơi sử dụng | Tách theo lý do thay đổi thực tế | Đưa quy tắc về nơi sở hữu thông tin | Giới hạn quyền sửa state | Thay điều kiện lặp bằng hợp đồng | Chọn has-a khi is-a không đúng | Xét giá trị abstraction, tránh đếm lớp | Mỗi vòng đổi nhỏ đều có bằng chứng |