<a id="back-to-top"></a>

# Loại trừ và chẩn đoán

## Menu
- [Khi nào nên loại trừ Auto-configuration?](#when-to-exclude-auto-configuration)
- [Loại trừ theo Class, Name và Property](#exclusion-mechanisms)
- [Condition Evaluation Report](#condition-evaluation-report)
- [Thông tin chẩn đoán từ Boot Debug](#debug-diagnostics)
- [Chẩn đoán Candidate khớp ngoài dự kiến và Bean bị thiếu](#condition-diagnosis-playbook)

## <a id="when-to-exclude-auto-configuration">Khi nào nên loại trừ Auto-configuration?</a>

<details>
<summary>Xem chi tiết</summary>

Back-off phù hợp khi ứng dụng vẫn muốn phần tích hợp nhưng cần thay một lựa chọn. Loại trừ phù hợp khi ứng dụng không muốn một auto-configuration cụ thể tham gia hoàn toàn.

Các lý do thường gặp:

- ứng dụng cố ý cấu hình công nghệ đó bằng một con đường hoàn toàn khác;
- candidate hợp lệ nói chung nhưng không phù hợp môi trường triển khai này;
- quá trình chuyển đổi tạm thời cần tắt phần tích hợp do Boot cung cấp;
- thông tin chẩn đoán cho thấy auto-configuration đang tạo hạ tầng mà ứng dụng chủ động không muốn.

Không dùng loại trừ như phản ứng đầu tiên với mọi nhu cầu tùy biến. Nếu cách ghi đè bean hoặc công tắc cấu hình được hỗ trợ diễn đạt ý định hẹp hơn thì nên ưu tiên chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exclusion-mechanisms">Loại trừ theo Class, Name và Property</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot hỗ trợ nhiều dạng loại trừ. EnableAutoConfiguration có cách loại trừ theo class và theo tên; property spring.autoconfigure.exclude cho phép đưa quyết định loại trừ ra cấu hình bên ngoài.

~~~text
loại trừ theo class
→ mã lúc biên dịch nhìn thấy type auto-configuration

loại trừ theo tên
→ không cần tham chiếu class trực tiếp

spring.autoconfigure.exclude
→ quyết định từ externalized configuration
~~~

Loại trừ loại candidate khỏi quá trình tham gia; nó khác với candidate được khám phá rồi condition tự đánh giá false. Sự khác biệt này xuất hiện trong thông tin chẩn đoán và quan trọng khi giải thích vì sao candidate không áp dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="condition-evaluation-report">Condition Evaluation Report</a>

<details>
<summary>Xem chi tiết</summary>

Condition Evaluation Report ghi lại lý do các condition của auto-configuration khớp hoặc không khớp. Đây là bằng chứng quan trọng nhất để hiểu quyết định của Boot.

Khi AcmeClient bị thiếu, lần theo:

~~~text
AcmeClientAutoConfiguration có là candidate?
        ↓
class condition khớp?
        ↓
property condition khớp?
        ↓
missing-bean condition khớp?
        ↓
candidate có bị exclude?
~~~

Báo cáo biến câu hỏi mơ hồ “vì sao Boot không tạo bean?” thành tập điều kiện cụ thể. Nó đặc biệt hữu ích khi nhiều condition phối hợp trên cùng một configuration.

Báo cáo là bằng chứng của quá trình lựa chọn, không thay thế việc hiểu ý nghĩa thật của từng condition.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="debug-diagnostics">Thông tin chẩn đoán từ Boot Debug</a>

<details>
<summary>Xem chi tiết</summary>

Boot có thể ghi Condition Evaluation Report vào log khi bật chế độ debug. Điều này hữu ích khi điều tra quá trình khởi động vì các condition khớp hoặc không khớp trở nên nhìn thấy được mà không phải thêm log thủ công vào từng configuration.

Thông tin debug nên giúp trả lời câu hỏi có mục tiêu:

- Candidate có được khám phá?
- Condition nào chặn nó?
- Bean do ứng dụng cung cấp có làm giá trị mặc định back off?
- Giá trị property có khác dự kiến?
- Candidate có bị loại trừ?

Đừng đọc toàn bộ báo cáo như một khối nhiễu. Bắt đầu từ auto-configuration class mong đợi rồi lần ra condition giải thích trạng thái của nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="condition-diagnosis-playbook">Chẩn đoán Candidate khớp ngoài dự kiến và Bean bị thiếu</a>

<details>
<summary>Xem chi tiết</summary>

Khi bean mong đợi bị thiếu hoặc bean ngoài dự kiến xuất hiện, chẩn đoán từ ngoài vào:

~~~text
1. xác nhận dependency/candidate tồn tại
2. kiểm tra trạng thái loại trừ
3. kiểm tra configuration-level condition
4. kiểm tra bean-level condition và definition đã có
5. kiểm tra property và loại ứng dụng
6. xác minh giả định về thứ tự
7. tái hiện trạng thái bằng kiểm thử context tập trung
~~~

Luồng này tách bước khám phá, lựa chọn và đăng ký bean thay vì đoán từ context cuối cùng.

Với auto-configuration tùy chỉnh, kiểm thử bằng ApplicationContextRunner thường là cách tái hiện thực thi nhỏ nhất. Nếu test khác ứng dụng thật, so sánh classpath, property, cấu hình do ứng dụng cung cấp và loại context.

Chẩn đoán giúp giải thích các quyết định đã xảy ra. Chương tiếp theo dùng chính mô hình đó theo hướng chủ động để xây dựng custom auto-configuration với condition hẹp, đăng ký tường minh và điểm mở rộng ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)
