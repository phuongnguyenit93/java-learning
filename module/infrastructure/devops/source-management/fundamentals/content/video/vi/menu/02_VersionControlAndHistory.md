---
video:
  url: ""
---

# Kiểm soát phiên bản và lịch sử

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Hệ thống kiểm soát phiên bản: Khái niệm và nhiệm vụ

<!-- VIDEO_SECTION -->

### Scene 1 — Hệ thống kiểm soát phiên bản: Khái niệm và nhiệm vụ

**Time:** `00:00–01:15`

**Visual:**

Hiện hai ảnh `Invoice.java` ngày thứ hai và thứ tư. Cạnh đó là timeline ba nút `commit A → B → C`, với đường nối từng trạng thái.

**Script:**

Sau chương một, chúng ta biết gửi thư mục qua lại không đủ để truy vết. Một hệ thống kiểm soát phiên bản, hay VCS, ghi lại các trạng thái nguồn cùng quan hệ lịch sử, để ta đọc được phiên bản nào xuất hiện sau phiên bản nào. Hãy nhìn đường thời gian: thay vì ba thư mục có chữ final, từng mốc có định danh và có thể được kiểm tra. Nhưng VCS không tự phán xét phần tính thuế hay quyết định duyệt thay đổi. Nó cung cấp thông tin đáng tin cậy để con người và quy trình cộng tác ra quyết định.

**Purpose:**

Phân biệt nhiệm vụ ghi lịch sử của VCS với quyền phê duyệt và trách nhiệm kiểm thử.



## Lưu tệp và sao lưu thư mục so với lịch sử phiên bản có truy vết

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ timeline commit A→B→C; đặt ba thao tác riêng `Save in editor`, `Folder backup`, `Record VCS revision` cùng vị trí đầu ra của chúng.

**Script:**

Có lịch sử có định danh rồi, vậy nó khác gì nút Save và một thư mục backup?

**Purpose:**

Làm rõ cùng một tệp có thể được lưu, sao lưu hoặc ghi lịch sử nhưng bằng chứng thu được khác nhau.

### Scene 2 — Lưu tệp và sao lưu thư mục so với lịch sử phiên bản có truy vết

**Time:** `01:27–02:42`

**Visual:**

Ba cột `Save`, `Backup`, `Version history`; bên dưới tô sáng: bản hiện tại, bản sao tại thời điểm, và chuỗi thay đổi có liên kết tác giả/nguyên nhân.

**Script:**

Nhấn Save thì editor ghi nội dung hiện tại lên đĩa. Chép thư mục backup có thể giữ lại một trạng thái cũ và vẫn rất quan trọng để dự phòng. Nhưng cả hai không tự tạo một chuỗi thay đổi có thể đối chiếu từng mốc. VCS bổ sung lịch sử phiên bản, quan hệ giữa các bản và công cụ so sánh, cho phép ta trả lời thuế suất đổi ở mốc nào. Nhớ rằng history không thay thế backup độc lập: nếu cả máy và kho đều mất, bản sao lưu ở nơi khác vẫn có vai trò riêng.

**Purpose:**

Chứng minh backup và VCS bổ sung cho nhau, không đánh đồng hành động lưu và ghi nhận lịch sử.



## Nội dung, người thực hiện, thời điểm và lý do thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Từ ba cột Save/Backup/History, phóng lớn revision B; hiện `Invoice.java: 8%→10%`, `Author An`, thời điểm và phần Reason do An khai báo.

**Script:**

Nếu không chỉ biết 'bản nào', ta cần thêm bằng chứng gì để tin một thay đổi?

**Purpose:**

Chuyển từ chức năng giữ lịch sử sang cách kiểm tra provenance và giới hạn metadata do tác giả cung cấp.

### Scene 3 — Nội dung, người thực hiện, thời điểm và lý do thay đổi

**Time:** `02:54–04:09`

**Visual:**

Hiện bản ghi giả định: `Invoice.java: 8% → 10%`, `Author: An`, `When: 10:00`, `Reason: tax rule updated`; khoanh tròn lý do để nhắc rằng đó là thông tin được khai báo.

**Script:**

Một bản ghi hữu ích cho biết nội dung nào khác trước, ai ghi nhận, thời gian và thông điệp nêu mục đích. Khi Bình phát hiện hóa đơn sai, nhóm có thể lần lại thời điểm thay thuế, so chính các dòng đã đổi và đọc lý do tác giả nêu. Đừng nhầm metadata với bằng chứng ai đó đã duyệt: tên tác giả hay thông điệp có thể do người thực hiện khai báo, không tự xác thực nghiệp vụ. Vì vậy thay đổi đáng tin cần cả lịch sử có thể kiểm tra và việc đánh giá độc lập ở lớp cộng tác.

**Purpose:**

Dạy kiểm tra provenance với giới hạn của author metadata, không nâng thông điệp thành approval.



## So sánh, xem lại và khôi phục phiên bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Giữ diff thuế 8%→10% cùng thông điệp thay đổi; tách hai snapshot A và B, thêm lựa chọn `Inspect`, `Compare`, `Recover` nhưng khoá hành động phá huỷ.

**Script:**

Đã tìm được thay đổi gây lỗi; cách an toàn để xem và phục hồi là gì?

**Purpose:**

Liên hệ dấu vết phát hiện sai với quy trình xem và khôi phục an toàn, bảo vệ sửa đổi chưa ghi nhận.

### Scene 4 — So sánh, xem lại và khôi phục phiên bản

**Time:** `04:21–05:36`

**Visual:**

Tách màn hình so sánh hai snapshot `A`/`B`; đánh dấu đúng dòng thuế và làm tròn. Mở nút `Inspect → Compare → Recover` nhưng không biểu diễn click destructive trên dự án thật.

**Script:**

Đầu tiên chọn hai mốc muốn đối chiếu: trước và sau khi An đổi thuế. Bản diff cho biết dòng nào thêm hoặc bỏ; xem bản gốc giúp xác định hành vi đã lưu. Nếu bản được chấp nhận gặp lỗi, có thể phục hồi nội dung phiên bản cũ hoặc tạo thay đổi đảo ngược tùy tình huống. Không nên ghi đè ngay thư mục đang làm việc, vì nó có thể chứa sửa đổi chưa được ghi nhận. Ở chương Git sau này, chúng ta sẽ thực hành các lệnh cụ thể. Kết luận của chương này là hãy đọc lịch sử và giữ an toàn dữ liệu trước khi quyết định hoàn tác.

**Purpose:**

Đặt thứ tự quan sát–so sánh–phục hồi và cảnh báo không phá hủy thay đổi chưa ghi.
