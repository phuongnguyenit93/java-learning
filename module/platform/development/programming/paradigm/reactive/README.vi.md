# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [ReactiveProgramming](readme/vi/menu/1.Introduction/ReactiveProgramming.md)
* **2.DataFlow**
    * [DataFlow](readme/vi/menu/2.DataFlow/DataFlow.md)
* **3.Backpressure**
    * [Backpressure](readme/vi/menu/3.Backpressure/Backpressure.md)
* **4.ReactiveStreams**
    * [ReactiveStreams](readme/vi/menu/4.ReactiveStreams/ReactiveStreams.md)
* **5.ErrorCancellation**
    * [ErrorCancellation](readme/vi/menu/5.ErrorCancellation/ErrorCancellation.md)
* **6.Tradeoffs**
    * [Tradeoffs](readme/vi/menu/6.Tradeoffs/Tradeoffs.md)

# Lập trình phản ứng (Reactive Programming)

Reactive Programming tập trung vào cách **mô tả và lan truyền các giá trị hoặc sự kiện đến theo thời gian** qua một dòng xử lý. Thay vì nối nhiều callback rời rạc, người học nhìn luồng từ nguồn qua các bước biến đổi đến người nhận, đồng thời suy luận về thời điểm phát, lỗi và việc dừng nhận.

**Điểm xuất phát:** cần hiểu giá trị, hàm biến đổi và sự khác biệt giữa mô tả ý định với thao tác tuần tự. Tư duy functional/declarative là nền tảng gợi ý, không phải giả định đã biết *stream*, *subscription*, *demand* hay *backpressure*. Bất đồng bộ có nghĩa các bước không nhất thiết hoàn tất ngay trong cùng dòng gọi; reactive không tự bảo đảm chạy đa luồng, song song hoặc không chặn.

**Cách học:** mở đầu bằng mô hình dữ liệu/sự kiện thay đổi theo thời gian; sau đó đi qua source–producer–consumer, push/pull, composition và subscription. Tiếp theo học vấn đề chênh lệch tốc độ, cách phản hồi bằng demand và backpressure, rồi tìm hiểu contract Reactive Streams với các vai trò và tín hiệu cơ bản. Cuối cùng phân biệt hoàn tất, lỗi, hủy, thu hồi tài nguyên và chọn khi nào reactive thực sự giúp ích.

**Ranh giới:** Reactive Streams là đặc tả tương tác bất đồng bộ có backpressure không chặn, không phải định nghĩa của mọi API reactive. Project Reactor và RxJava là thư viện, Spring WebFlux là framework; Java Flow là API ngôn ngữ/thư viện tiêu chuẩn. Cơ chế thread, scheduling và API cụ thể thuộc các module triển khai hoặc concurrency tương ứng.
