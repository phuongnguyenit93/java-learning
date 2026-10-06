<a id="back-to-top"></a>

# Đọc quá trình khởi động Spring Boot như bằng chứng

## Menu
- [Vì sao nên xem đầu ra khởi động là bằng chứng?](#startup-as-evidence)
- [Có thể học được gì từ đầu ra lúc khởi động?](#reading-startup-output)
- [Các mặc định của Boot vẫn quan sát và ghi đè được như thế nào?](#defaults-observable-overridable)
- [Những nhóm lỗi khởi động nào quan trọng với người mới?](#startup-failure-classes)
- [Khi nào nên chuyển việc chẩn đoán sang Auto-Configuration hoặc Actuator?](#diagnostics-handoff)

## <a id="startup-as-evidence">Vì sao nên xem đầu ra khởi động là bằng chứng?</a>

<details>
<summary>Xem chi tiết</summary>

Boot dễ học hơn khi xem startup là hành vi có thể quan sát. Một lần `main` chạy thành công không phải bằng chứng rằng "Boot tự làm mọi thứ"; startup log cho biết ứng dụng nào đang bắt đầu, profile nào đang được kích hoạt, thành phần runtime nào đang được khởi tạo và context có đạt trạng thái chạy hay không.

Đọc đầu ra tạo vòng phản hồi giữa mô hình tư duy và tiến trình thật:

```text
thay đổi dependency / configuration / code
        ↓
khởi động ứng dụng
        ↓
đọc bằng chứng lúc khởi động
        ↓
so sánh ứng dụng quan sát được với ứng dụng mong muốn
```

Thói quen này có giá trị ngay trước khi học chẩn đoán sâu hơn. Nó khiến bạn hỏi Boot thực sự đã tạo và chọn gì, thay vì giả định mọi mặc định đều cố định hoặc bị che giấu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reading-startup-output">Có thể học được gì từ đầu ra lúc khởi động?</a>

<details>
<summary>Xem chi tiết</summary>

Đầu ra startup điển hình cho thấy nhiều dấu hiệu hữu ích. Boot banner cho biết phiên bản Boot đang chạy. Thông báo "Starting ..." nhận diện ứng dụng và thường cho biết ngữ cảnh tiến trình. Thông báo profile cho biết profile nào đang được kích hoạt. Thông báo về context hoặc server cho thấy dạng runtime lớn nào đang được khởi tạo. Dòng "Started ... in ... seconds" là bằng chứng startup đã hoàn tất.

Với web application, bạn còn có thể thấy server implementation và port đang được khởi tạo. Với non-web application, các thông báo server đó nên vắng mặt. Đây là cách đơn giản để kiểm chứng mô hình dạng ứng dụng bằng bằng chứng runtime.

Không nên học thuộc câu chữ chính xác của log như một API contract. Logging configuration và chi tiết triển khai có thể thay đổi. Nên học các nhóm bằng chứng: định danh ứng dụng, configuration/profile, context/runtime được chọn, thời gian startup và lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="defaults-observable-overridable">Các mặc định của Boot vẫn quan sát và ghi đè được như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Mặc định của Boot hữu ích vì ứng dụng phổ biến có thể chạy với ít thiết lập tường minh, nhưng mặc định vẫn là một quyết định có thể quan sát và thay đổi. Dạng server, tên ứng dụng, banner mode, giá trị cấu hình và nhiều lựa chọn tích hợp đều có điểm điều khiển bằng cấu hình hoặc bằng mã được tài liệu hóa.

Đây là một cách diễn đạt thực tế của convention over configuration:

```text
không có lựa chọn tường minh
→ Boot áp dụng mặc định được tài liệu hóa khi phù hợp

có lựa chọn được hỗ trợ
→ cấu hình ứng dụng thay đổi hoặc điều chỉnh mặc định đó
```

Thứ tự ưu tiên chính xác giữa externalized property thuộc `externalized-configuration`. Bài học Fundamentals là: khi thấy một mặc định trong log hoặc hành vi runtime, hãy tìm cấu hình/tài liệu của module chuyên trách thay vì xem nó như hành vi được viết cố định trong mã và không thể thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-failure-classes">Những nhóm lỗi khởi động nào quan trọng với người mới?</a>

<details>
<summary>Xem chi tiết</summary>

Với người mới, lỗi startup dễ chẩn đoán hơn nếu trước hết xác định lớp trách nhiệm bị lỗi.

1. **Lỗi Java/process:** JVM không khởi động được main class, thiếu class bắt buộc hoặc argument của tiến trình sai trước khi Boot thực sự bắt đầu.
2. **Lỗi Spring container:** context không refresh được vì tạo bean, dependency injection, phân tích cấu hình hoặc một thao tác container cốt lõi khác thất bại.
3. **Lỗi tích hợp/cấu hình Boot:** Boot bắt đầu nhưng một tích hợp được chọn không cấu hình được, thiếu cấu hình bắt buộc, hoặc lựa chọn application/environment xung đột với thiết lập hiện có.

Các nhóm có thể chồng lên nhau vì Boot xây trên Spring. Đây là công cụ phân loại ban đầu chứ không phải taxonomy exception. Nên bắt đầu từ nguyên nhân gốc và thông báo lỗi có ý nghĩa xuất hiện sớm nhất, thay vì sửa stack frame có tên quen mắt nhất.

Khi vấn đề đã cụ thể thành **vì sao điều kiện của auto-configuration được thỏa mãn**, chuyển sang diagnostics của auto-configuration. Khi ứng dụng đã chạy và cần bằng chứng runtime cho môi trường vận hành thực tế, chuyển sang Actuator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostics-handoff">Khi nào nên chuyển việc chẩn đoán sang Auto-Configuration hoặc Actuator?</a>

<details>
<summary>Xem chi tiết</summary>

Fundamentals sở hữu việc đọc startup cơ bản. Module `auto-configuration` sở hữu Condition Evaluation Report và phần giải thích chi tiết về vì sao một automatic configuration thỏa điều kiện, không thỏa điều kiện hoặc back off. Đó là đích đúng khi câu hỏi xoay quanh quyết định cấu hình có điều kiện.

Actuator sở hữu các bề mặt runtime phục vụ vận hành thực tế như health, metrics, loggers và operational endpoints. Đó là đích đúng khi startup đã thành công và câu hỏi chuyển sang trạng thái của ứng dụng đang vận hành.

```text
startup không tạo cấu hình như mong đợi
→ auto-configuration diagnostics

ứng dụng đang chạy; cần trạng thái vận hành
→ Actuator
```

Tách hai đích này giúp tránh xem startup log như một hệ thống giám sát vận hành hoàn chỉnh.

</details>

- [Quay lại đầu trang](#back-to-top)
