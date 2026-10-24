# Lập trình theo luồng dữ liệu

Biểu diễn chương trình thành các nút tính toán và cạnh phụ thuộc dữ liệu, trong đó tác vụ được kích hoạt khi đầu vào cần thiết sẵn sàng.

## Mục đích học

Hiểu đúng mô hình tư duy, lý do sử dụng, cơ chế vận hành, tình huống phù hợp và đánh đổi. Module dạy phạm vi **độc lập ngôn ngữ**; công cụ và framework chỉ dùng làm ví dụ đối chiếu.

## Điểm xuất phát

Hàm, cấu trúc dữ liệu, đồ thị phụ thuộc đơn giản; biết Functional và Imperative giúp đối chiếu.

## Ranh giới kiến thức

Khác Data-Oriented Programming (tổ chức dữ liệu), Reactive Programming (tín hiệu theo thời gian/backpressure) và workflow orchestration. Module dạy dataflow graph, scheduling, readiness, determinism; không phải hướng dẫn một framework pipeline cụ thể.

## Lộ trình

1. **Mô hình Dataflow và đồ thị phụ thuộc:** Phân biệt phụ thuộc dữ liệu với thứ tự câu lệnh, xác định node/edge/input/output.
2. **Readiness và kích hoạt tác vụ:** Hiểu điều kiện đủ đầu vào, propagation và vòng đời một node tính toán.
3. **DAG, chu kỳ và phản hồi:** Phân biệt luồng có hướng không chu kỳ với vòng phản hồi và cơ chế lặp.
4. **Song song hóa theo phụ thuộc:** Nhận diện các nhánh độc lập, scheduling và giới hạn do critical path.
5. **Tính quyết định và trạng thái:** Giải thích ảnh hưởng của mutable state, side effects, retries và nondeterministic ordering.
6. **Dữ liệu hữu hạn và luồng dữ liệu liên tục:** So sánh batch graph, streaming dataflow, buffering và ranh giới với Reactive.
7. **Ứng dụng và đánh đổi trong hệ thống thực:** Phân loại build graph, ETL, tensor graph và giới hạn của trừu tượng dataflow.

> Roadmap là nguồn nội dung cấp cao. Knowledge/Menu, Quiz, Interview và thực nghiệm sẽ được tạo ở các STEP sau; chưa coi các phần đó là hoàn thành.
