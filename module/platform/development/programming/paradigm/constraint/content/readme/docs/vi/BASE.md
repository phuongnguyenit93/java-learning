# Lập trình ràng buộc

Mô tả biến, miền giá trị và điều kiện hợp lệ để solver tìm nghiệm, thay vì viết tay chiến lược duyệt tất cả phương án.

## Mục đích học

Hiểu đúng mô hình tư duy, lý do sử dụng, cơ chế vận hành, tình huống phù hợp và đánh đổi. Module dạy phạm vi **độc lập ngôn ngữ**; công cụ và framework chỉ dùng làm ví dụ đối chiếu.

## Điểm xuất phát

Biết tập hợp, biểu thức logic, biến và quy tắc điều kiện; không yêu cầu kiến thức tối ưu toán học nâng cao.

## Ranh giới kiến thức

Phân biệt constraint satisfaction với logic programming và imperative search. Module sở hữu propagation, search, optimization, soft/hard constraints; API OR-Tools/Timefold và triển khai sản xuất ở module công cụ chuyên biệt.

## Lộ trình

1. **Constraint Satisfaction Problem (CSP):** Mô tả biến, miền giá trị và tập ràng buộc tạo nên bài toán thỏa mãn.
2. **Mô hình hóa ràng buộc:** Phân biệt mô hình với thuật toán giải, ràng buộc toàn cục và điều kiện dư thừa.
3. **Lan truyền và thu hẹp miền:** Hiểu constraint propagation, consistency và pruning mà không liệt kê mọi nghiệm.
4. **Backtracking và heuristic tìm kiếm:** Đánh giá thứ tự chọn biến, giá trị và các trường hợp bùng nổ tổ hợp.
5. **Tối ưu hóa, ràng buộc cứng và mềm:** Phân biệt feasibility với optimum; mô hình hóa objective và ưu tiên đánh đổi.
6. **Xếp lịch và phân bổ tài nguyên:** Xây mô hình ca làm hoặc phân bổ tài nguyên, diễn giải trường hợp không có nghiệm.
7. **Độ khó tính toán và phạm vi sử dụng:** So sánh constraint solving với rule engines, search và thuật toán chuyên dụng.

> Roadmap là nguồn nội dung cấp cao. Knowledge/Menu, Quiz, Interview và thực nghiệm sẽ được tạo ở các STEP sau; chưa coi các phần đó là hoàn thành.
