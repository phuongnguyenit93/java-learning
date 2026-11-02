# Mẫu kiến trúc Transactional Outbox

Transactional Outbox là **mẫu thiết kế tích hợp và truyền thông điệp trong hệ thống phân tán**. Mẫu này giải quyết bài toán **ghi vào hai hệ thống (dual-write)**: giao dịch lưu dữ liệu thành công nhưng việc phát sự kiện sang Kafka, RabbitMQ hoặc một hệ thống khác lại thất bại.

## Ý tưởng chính

1. Trong **cùng một giao dịch cơ sở dữ liệu**, lưu thay đổi nghiệp vụ (ví dụ: đơn hàng) và bản ghi outbox mô tả sự kiện `OrderCreated`.
2. Xác nhận (commit) cả hai bản ghi cùng nhau. Nếu giao dịch bị hủy (rollback), cả hai thay đổi đều không được lưu.
3. Một tiến trình phát sự kiện (outbox relay) đọc các bản ghi đã commit và chuyển tới hệ thống nhận. Có thể triển khai bằng cách quét bảng định kỳ (polling) hoặc thu thập thay đổi dữ liệu (CDC).
4. Bên nhận xử lý an toàn khi một sự kiện được gửi nhiều lần, dựa vào mã sự kiện hoặc khóa chống xử lý trùng.

Outbox giúp **không đánh mất ý định phát sự kiện** sau khi dữ liệu nghiệp vụ đã commit. Mẫu này **không tự bảo đảm exactly-once** trên toàn hệ thống: tiến trình phát có thể gặp sự cố sau khi gửi nhưng trước khi ghi nhận đã gửi. Cần thiết kế thêm thứ tự sự kiện, thử lại, giới hạn số lần thử, xử lý sự kiện lỗi và giám sát tồn đọng.

## Phạm vi học

- Bài toán dual-write và giao dịch nguyên tử trong một cơ sở dữ liệu.
- Cấu trúc bản ghi outbox: mã sự kiện, mã đối tượng nghiệp vụ, loại sự kiện, dữ liệu, thời điểm tạo và trạng thái phát.
- So sánh polling publisher với CDC (ví dụ: Debezium).
- Gửi ít nhất một lần (at-least-once), xử lý trùng, thứ tự sự kiện và khôi phục khi tiến trình bị dừng.
- Kiểm thử rollback, lỗi phát sự kiện, gửi trùng và khả năng gửi thành công sau khi khôi phục.

## Liên quan

- [Saga](../saga/README.md): điều phối quy trình nghiệp vụ qua nhiều dịch vụ; có thể dùng outbox để gửi thông điệp một cách đáng tin cậy.
- [Distributed System](../README.md): nhóm kiến thức kiến trúc hệ thống phân tán.
- Nội dung Kafka nâng cao tại `module/integration/messaging/event-streaming/kafka` để nghiên cứu việc phát và nhận thông điệp.

README này là phần giới thiệu ban đầu. Roadmap, bài Knowledge và ví dụ thực thi đầy đủ sẽ được xây dựng ở những bước biên soạn tiếp theo.
