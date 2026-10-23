# Java Advance — Compilation

Biên dịch Java là bước chuyển mã nguồn thành các tệp `.class` để JVM có thể thực thi. Module này giúp bạn hiểu trình biên dịch làm gì, vì sao một đoạn mã biên dịch thành công hoặc thất bại, và cách kiểm soát đầu ra thay vì chỉ chạy lệnh `javac`.

Nội dung dành cho người đã nắm Java cơ bản và muốn đào sâu quá trình build, phân tích lỗi biên dịch hoặc phát triển công cụ hỗ trợ lập trình. Lộ trình bắt đầu từ các giai đoạn biên dịch, tùy chọn `javac`, classpath/module-path và bytecode; sau đó đi vào annotation processing, Java Compiler API và nguyên lý incremental compilation.

## Kết quả mong đợi

- Đọc và xử lý lỗi biên dịch, cấu hình phiên bản Java và phụ thuộc chính xác.
- Kiểm tra bytecode, hiểu cơ chế sinh mã từ annotation và biên dịch bằng API Java.
- Giải thích khi nào dự án cần biên dịch lại và những yếu tố ảnh hưởng tới thời gian build.