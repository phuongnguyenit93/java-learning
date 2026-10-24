# Lập trình đồng thời

Tổ chức nhiều công việc có thể tiến triển xen kẽ, chia sẻ dữ liệu hoặc giao tiếp qua thông điệp, trong khi vẫn đảm bảo an toàn và tiến triển.

## Mục đích học

Hiểu đúng mô hình tư duy, lý do sử dụng, cơ chế vận hành, tình huống phù hợp và đánh đổi. Module dạy phạm vi **độc lập ngôn ngữ**; công cụ và framework chỉ dùng làm ví dụ đối chiếu.

## Điểm xuất phát

Trạng thái, hàm, thao tác bất đồng bộ và khái niệm luồng thực thi cơ bản.

## Ranh giới kiến thức

Giải thích mô hình độc lập ngôn ngữ: shared memory, actors/message passing và CSP/channels. Thread, synchronized, virtual thread và Java Memory Model thuộc java/advance/concurrency; Reactive/Backpressure chuyên biệt thuộc reactive.

## Lộ trình

1. **Đồng thời, song song và bất đồng bộ:** Phân biệt concurrency, parallelism, asynchrony và interleaving.
2. **Trạng thái dùng chung và đồng bộ:** Nhận diện race condition, critical section và ranh giới quyền sở hữu dữ liệu.
3. **Visibility, atomicity và thứ tự:** Giải thích vì sao thứ tự quan sát và memory visibility quyết định kết quả.
4. **Gửi thông điệp và Actor Model:** Mô hình hóa trao đổi thông điệp, mailbox, isolation và supervision ở mức khái niệm.
5. **CSP, kênh và phối hợp:** Phân biệt giao tiếp qua channel với shared memory; suy luận rendezvous và buffering.
6. **Deadlock, livelock và starvation:** Nhận diện điều kiện gây tắc nghẽn và vấn đề công bằng/tiến triển.
7. **Hủy, lỗi và kiểm thử đồng thời:** Lập kế hoạch cancellation, structured lifetime, deterministic tests và trade-offs.

> Roadmap là nguồn nội dung cấp cao. Knowledge/Menu, Quiz, Interview và thực nghiệm sẽ được tạo ở các STEP sau; chưa coi các phần đó là hoàn thành.
