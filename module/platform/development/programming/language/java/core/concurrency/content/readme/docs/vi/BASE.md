# Java Advance - Concurrency

Concurrency trong Java giúp ứng dụng xử lý nhiều công việc có tiến độ chồng lấp, từ tác vụ tính toán đến thao tác I/O. Module này dành cho người đã nắm Java cơ bản và muốn hiểu cách tạo, quản lý, phối hợp nhiều luồng, đồng thời bảo vệ dữ liệu dùng chung. Nội dung tập trung vào Java và các API của JDK; những cơ chế liên quan riêng đến Spring được học ở chương khác.

Bạn sẽ bắt đầu từ mô hình concurrency, vòng đời thread và Java Memory Model để giải thích các lỗi khó thấy như race condition, dữ liệu không được cập nhật kịp thời hay deadlock. Từ nền tảng đó, bạn học các kỹ thuật đồng bộ, lock, phối hợp luồng, hủy tác vụ và quản lý tài nguyên an toàn.

Lộ trình tiếp tục với ExecutorService, Fork/Join, concurrent collections và các tiện ích đồng bộ cấp cao; sau đó mở rộng sang CompletableFuture và virtual threads. Phần cuối hướng dẫn kiểm thử, đọc thread dump và đo hiệu năng, giúp bạn lựa chọn giải pháp phù hợp với tải công việc và chẩn đoán vấn đề trong ứng dụng Java thực tế.