# Java Stream API

Java Stream API giúp mô tả việc xử lý dữ liệu bằng pipeline gồm nguồn, các thao tác trung gian và thao tác kết thúc. Module này dành cho người đã biết Java cơ bản, collection, lambda và method reference, muốn viết các phép lọc, biến đổi và tổng hợp dữ liệu rõ ràng hơn.

Lộ trình bắt đầu với tư duy pipeline, tính lazy và vòng đời Stream; tiếp đó là tạo nguồn, lọc, biến đổi, sắp xếp, giới hạn và lấy kết quả với Optional. Bạn sẽ học rút gọn dữ liệu, sử dụng Stream cho kiểu số và thu thập kết quả bằng Collector, bao gồm nhóm dữ liệu và xử lý khóa trùng.

Tư duy nền tảng là chọn thao tác theo kết quả cần đạt, giữ hàm xử lý không can thiệp vào nguồn và hạn chế trạng thái dùng chung. Phần cuối giúp bạn quản lý Stream gắn với tài nguyên I/O, đánh giá việc chạy tuần tự hoặc song song và kiểm chứng hiệu năng bằng đo đạc.