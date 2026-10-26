# CƠ CHẾ NGÔN NGỮ — LIÊN KẾT BÀI HỌC

## 1. Mạch liên kết bài học

### Bài 1 — Kiểu tổng quát và lớp có tham số kiểu

Xây lớp/phương thức tổng quát (`Box`, `Pair`, `Repository`) và hiểu cách type argument, inference, raw type và erasure quyết định mức an toàn kiểu.

↓ **Từ biết generic type → thêm upper bound khi thuật toán cần khả năng cụ thể từ T**

### Bài 2 — Ràng buộc tham số kiểu

Giới hạn type parameter bằng class/interface bounds, `Comparable<? super T>` và intersection; chọn điều kiện mà thuật toán thật sự cần.

↓ **Từ giới hạn năng lực T → kiểm tra subtype, wildcard và dữ liệu có thể đọc hoặc ghi qua generic API**

### Bài 3 — Quan hệ kiểu của các cấu trúc tổng quát

Đọc quan hệ subtype giữa parameterized types; chọn `?`, `? extends`, `? super` và helper capture theo vai trò producer/consumer.

↓ **Từ quan hệ kiểu trong API → phân biệt lúc compiler kiểm tra và khi JVM dispatch hay cast**

### Bài 4 — Kiểu tĩnh, kiểu động và kiểm tra kiểu

Phân biệt kiểu tĩnh với runtime class, overload với override; dùng `instanceof`/validation khi dữ liệu đến từ boundary không chắc kiểu.

↓ **Từ kiểu và lời gọi hợp lệ → đánh giá biểu thức, field, accessor và quy ước Java tác động lên trạng thái**

### Bài 5 — Toán tử, thuộc tính và quy ước của ngôn ngữ

Vận dụng toán tử, numeric promotion, short-circuit và JavaBeans/record conventions để tránh sai số và bảo vệ field.

↓ **Từ so sánh biểu thức bằng `==`/`equals` → xây hợp đồng equality/hash đầy đủ để dùng object làm key**

### Bài 6 — Hợp đồng so sánh bằng và mã băm

Thiết kế equality/hash nhất quán, giữ key ổn định và áp dụng đúng cho HashMap, HashSet, record, array và entity.

↓ **Từ danh tính và equality → quyết định cách hiển thị object cho debug mà không rò rỉ dữ liệu**

### Bài 7 — Biểu diễn chuỗi và thông tin chẩn đoán của đối tượng

Thiết kế `toString` và logging gọn, không lộ secret hoặc tạo side effect, với DTO/structured log riêng khi cần.

↓ **Từ chuỗi chẩn đoán và exception message → xác định hợp đồng lỗi có cấu trúc và trạng thái sau thất bại**

### Bài 8 — Ngoại lệ, trạng thái lỗi và hợp đồng phương thức

Quy định tiền/hậu điều kiện, cách trả Result/Optional hoặc ném exception và trạng thái sau lỗi, cùng nguyên tắc atomicity.

↓ **Từ các cơ chế ngôn ngữ Java → phân biệt phần thiết kế OOP phải giữ với cú pháp/runtime có thể thay đổi khi port**

### Bài 9 — Khác biệt ngôn ngữ khi hiện thực cùng một thiết kế OOP

Đối chiếu cùng thiết kế ShippingPolicy trên năm ngôn ngữ và xác nhận parity bằng ràng buộc input, đơn vị tiền và test biên.

**Mối liên hệ:** Chương xây nền tảng từ generics và quan hệ kiểu tới cách Java phân giải lời gọi, tính biểu thức và công bố trạng thái; sau đó định nghĩa những hợp đồng quan trọng của `Object` (`equals`, `hashCode`, `toString`) và cách một method báo thành công hoặc thất bại. Bài cuối đối chiếu thiết kế trên nhiều ngôn ngữ để nhấn mạnh rằng trách nhiệm, invariant và kết quả kiểm thử có thể giữ nguyên dù cú pháp thay đổi. Các bài về kiểu, biểu diễn và lỗi cung cấp nhiều góc nhìn bổ trợ; mũi tên biểu thị hướng học thuận tiện, không khẳng định mọi bài chỉ học được sau bài đứng trước.

[↓ Xem bảng đối chiếu các bài học](#2-bảng-đối-chiếu-các-bài-học)

## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 | Bài 2 | Bài 3 | Bài 4 | Bài 5 | Bài 6 | Bài 7 | Bài 8 | Bài 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Tên | Kiểu tổng quát và lớp có tham số kiểu | Ràng buộc tham số kiểu | Quan hệ kiểu của các cấu trúc tổng quát | Kiểu tĩnh, kiểu động và kiểm tra kiểu | Toán tử, thuộc tính và quy ước của ngôn ngữ | Hợp đồng so sánh bằng và mã băm | Biểu diễn chuỗi và thông tin chẩn đoán của đối tượng | Ngoại lệ, trạng thái lỗi và hợp đồng phương thức | Khác biệt ngôn ngữ khi hiện thực cùng một thiết kế OOP |
| Vấn đề cần hiểu | Generic class/method giúp an toàn kiểu ra sao? | T cần những năng lực nào? | List<T> gán và đọc/ghi thế nào? | Compiler và runtime quyết định điều gì? | Biểu thức, field/property hoạt động ra sao? | Thế nào là cùng giá trị và hash phù hợp? | Object nên được ghi log bằng dữ liệu nào? | Failure có ý nghĩa gì và state ra sao? | Thiết kế nào phải giữ giữa các ngôn ngữ? |
| Giá trị đối với OOP/module | Tái sử dụng typed API, giảm cast | Chữ ký thuật toán có bound chính xác | Chọn wildcard/PECS đúng dữ liệu | Dispatch/cast/validation chính xác | Tính toán và đóng gói đúng quy tắc Java | Hash collection nhận diện key hợp lệ | Chẩn đoán an toàn, có trace và không lộ secret | Method contract, failure atomicity và retry đúng | Chuyển hành vi OOP với test parity |
| Tư duy hình thành | Tách thuật toán khỏi kiểu cụ thể nhưng giữ type safety | Chỉ đòi năng lực thực sự cần cho generic T | Suy luận từ nguồn/đích và subtype | Tách kiểu compile time khỏi object runtime | Phân tích kiểu kết quả và quyền cập nhật trạng thái | Định nghĩa domain identity/value equality ổn định | Tách diagnostic text với public API/schema | Thiết kế đường lỗi ngay cùng đường thành công | Ưu tiên trách nhiệm và invariant trước cú pháp |