# Lập trình hướng sự kiện

Tổ chức xử lý quanh sự kiện xảy ra và các handler phản ứng, thay vì giả định mọi thao tác theo một chuỗi gọi trực tiếp.

## Mục đích học

Hiểu đúng mô hình tư duy, lý do sử dụng, cơ chế vận hành, tình huống phù hợp và đánh đổi. Module dạy phạm vi **độc lập ngôn ngữ**; công cụ và framework chỉ dùng làm ví dụ đối chiếu.

## Điểm xuất phát

Điều khiển luồng, callback và trạng thái ứng dụng căn bản.

## Ranh giới kiến thức

Chủ sở hữu event loop, dispatcher, handler, event ordering, reentrancy và lifecycle; không đồng nhất với Event-Driven Architecture (tích hợp giữa dịch vụ) hoặc Reactive Streams (subscription/backpressure).

## Lộ trình

1. **Sự kiện, lệnh và trạng thái:** Phân biệt event, command, notification và state transition.
2. **Publisher, dispatcher và handler:** Hiểu đăng ký handler, dispatch, callback và vấn đề ownership.
3. **Event loop và hàng đợi:** Mô hình hóa queue, scheduling và trách nhiệm không chặn quá lâu trong handler.
4. **Thứ tự, reentrancy và nhất quán:** Phân tích ordering, nested dispatch, reentrancy và race ở stateful handlers.
5. **Lỗi, retry và vòng đời handler:** Chọn cách xử lý lỗi, timeout, unsubscribe và cleanup an toàn.
6. **Định ranh Event-Driven Programming:** Phân biệt callback/event loop với Reactive và kiến trúc truyền sự kiện giữa service.
7. **Thiết kế, kiểm thử và đánh đổi:** Xây một dispatcher đơn giản, kiểm thử ordering và đánh giá độ phức tạp ẩn.

> Roadmap là nguồn nội dung cấp cao. Knowledge/Menu, Quiz, Interview và thực nghiệm sẽ được tạo ở các STEP sau; chưa coi các phần đó là hoàn thành.
