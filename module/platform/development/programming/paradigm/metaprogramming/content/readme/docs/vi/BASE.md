# Siêu lập trình

Nghiên cứu chương trình có thể phân tích, tạo hoặc biến đổi biểu diễn chương trình khác (hoặc chính nó) ở giai đoạn biên dịch hay thời gian chạy.

## Mục đích học

Hiểu đúng mô hình tư duy, lý do sử dụng, cơ chế vận hành, tình huống phù hợp và đánh đổi. Module dạy phạm vi **độc lập ngôn ngữ**; công cụ và framework chỉ dùng làm ví dụ đối chiếu.

## Điểm xuất phát

Hiểu source code, hàm, kiểu dữ liệu và quá trình compile/runtime cơ bản; Java Reflection không phải điều kiện bắt buộc.

## Ranh giới kiến thức

Module này dạy mô hình code-as-data, AST, macro, code generation, staging và reflection ở mức khái niệm. Java annotation processor, Lombok, bytecode instrumentation, dynamic proxy và Spring internals thuộc module Java/Framework tương ứng.

## Lộ trình

1. **Chương trình như dữ liệu:** Hiểu biểu diễn code và sự khác biệt giữa dữ liệu ứng dụng với cú pháp chương trình.
2. **AST, cú pháp và ngữ nghĩa:** Phân biệt token/source/AST/bytecode, cú pháp bề mặt và semantic model.
3. **Các giai đoạn compile-time và runtime:** Xác định khi nào code được tạo, kiểm tra kiểu, biên dịch hoặc thực thi.
4. **Macro, hygiene và biến đổi mã:** Hiểu macro expansion, capture sai tên và giới hạn an toàn của phép biến đổi.
5. **Tạo source và xử lý metadata:** Định vị template, annotation processing và generated source; phân biệt tạo code với chỉ đọc metadata.
6. **Reflection và hành vi tạo động:** So sánh introspection, dynamic proxies và runtime code/bytecode generation.
7. **An toàn, kiểm thử và khả năng bảo trì:** Đánh giá nguồn gốc generated code, debugging, performance, trust boundaries và sử dụng thích hợp.

> Roadmap là nguồn nội dung cấp cao. Knowledge/Menu, Quiz, Interview và thực nghiệm sẽ được tạo ở các STEP sau; chưa coi các phần đó là hoàn thành.
