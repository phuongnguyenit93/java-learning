---
video:
  url: ""
---

# Nền tảng quản lý mã nguồn

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

## Quản lý mã nguồn: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Quản lý mã nguồn: Khái niệm và phạm vi

**Time:** `00:00–01:15`

**Visual:**

Mở hai khung: thư mục `Invoice.java` có tên giống nhau bên trái; bên phải là đường thời gian `Bản đang sửa → Bản được ghi nhận → Bản nhóm chấp nhận`. Chỉ tô sáng ô đang nói tới.

**Script:**

Nếu sáng nay An sửa số thuế trong `Invoice.java`, còn Bình cũng sửa cùng tệp ấy, câu hỏi không chỉ là tệp nào mới hơn. Nhóm cần biết phiên bản nào đã được ghi nhận, thay đổi đến từ đâu, và ai quyết định đưa nó vào nguồn chung. Đó là phạm vi của quản lý mã nguồn: tổ chức phiên bản, lịch sử và phối hợp thay đổi. Nó chưa đồng nghĩa review đã được duyệt, cũng chưa có nghĩa ứng dụng đã triển khai. Hãy giữ ba mốc trên màn hình làm bản đồ cho toàn bộ chương.

Lộ trình tiếp theo là hiểu vì sao cần lịch sử, so sánh kiểm soát tập trung và phân tán, phân biệt trạng thái trên máy với kho chia sẻ, rồi tìm hiểu công cụ, nền tảng, chính sách và cách nhận thay đổi vào nguồn chung. Chúng ta sẽ quay lại ví dụ thuế và làm tròn ở mỗi bước.

**Purpose:**

Đặt ranh giới source management bằng ba trạng thái mà người xem sẽ kiểm tra được.



## Mục đích quản lý mã nguồn trong cộng tác nhóm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ thẻ `Recorded / Shared / Accepted` từ cảnh đầu; tách biểu tượng An và Bình thành hai đường chỉnh `Invoice.java` song song; hiện câu hỏi tại giao điểm khi cả hai gửi bản sửa.

**Script:**

Đã có các trạng thái, nhưng vì sao một nhóm hai người lại phải phân biệt chúng?

**Purpose:**

Chuyển từ định nghĩa ba trạng thái sang lý do hai người làm việc đồng thời cần quy tắc chọn nguồn chung.

### Scene 2 — Mục đích quản lý mã nguồn trong cộng tác nhóm

**Time:** `01:27–02:42`

**Visual:**

Đặt An và Bình vào hai nhánh công việc; đồng hồ cho thấy cả hai gửi `Invoice.java` trước 17 giờ. Chiếu bảng `Ai? Sửa gì? Bản nào được nhận?` với ô trống.

**Script:**

Khi chỉ có một người, ta thường nghĩ lưu tệp là đủ. Nhưng An sửa thuế suất và Bình sửa quy tắc làm tròn thì cả hai đều có lý do chính đáng để thay đổi cùng một tệp. Nếu không có lịch sử và điểm tiếp nhận rõ ràng, người tích hợp chỉ thấy hai bản khác nhau và phải đoán. Quản lý nguồn chung giúp nhóm giữ công việc độc lập mà vẫn có căn cứ để đánh giá và phối hợp. Mục tiêu không phải ép mọi người làm trên một máy, mà là tránh quyết định thiếu thông tin.

**Purpose:**

Cho thấy nhu cầu phối hợp đến từ thay đổi đồng thời chứ không từ tên công cụ.



## Rủi ro mất thay đổi và nhầm phiên bản khi trao đổi tệp thủ công

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Từ hai nhánh An/Bình, thu nhỏ bảng `Ai? / Sửa gì? / Bản nào?`; mở ba thẻ ZIP `final`, `final-v2`, `fixed` và khoanh các phần chép đè.

**Script:**

Khi thiếu một lịch sử chung, một tình huống tưởng nhỏ có thể hỏng theo cách nào?

**Purpose:**

Đối chiếu mục tiêu phối hợp với lỗi mất sửa đổi có thể xuất hiện khi chỉ đổi thư mục thủ công.

### Scene 3 — Rủi ro mất thay đổi và nhầm phiên bản khi trao đổi tệp thủ công

**Time:** `02:54–04:09`

**Visual:**

Hiện `project-final.zip`, `project-final-v2.zip`, `project-fixed.zip`; kéo thả tệp lên nhau và tô đỏ phần sửa của Bình biến mất.

**Script:**

Ba thư mục đều mang tên gần giống bản cuối, nhưng không tên nào trả lời được đã bao gồm sửa đổi nào. Nếu An gửi toàn bộ thư mục sau khi Bình đã chỉnh công thức làm tròn, lần chép đè có thể khiến sửa đổi của Bình biến mất mà không báo lỗi. Sao lưu thư mục có thể cứu một bản cũ, nhưng không tự giải thích ai đã thay dòng nào và tại sao. Trên màn hình, ta thấy tệp vẫn mở được, còn hành vi cần thiết đã mất. Đó là kiểu lỗi khó nhận ra nếu nhóm chỉ kiểm tra ngày sửa tệp.

**Purpose:**

Trực quan hóa rủi ro silent overwrite và phân biệt bản sao thư mục với lịch sử có nguồn gốc.



## Ví dụ: hai người cùng sửa dự án bằng cách gửi tệp và thư mục phiên bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Giữ thẻ ZIP đang khiến thay đổi làm tròn biến mất; kéo thanh thời gian về 09:00, đặt mốc 10:00 thuế 10% và 10:30 làm tròn bên cạnh hai actor.

**Script:**

Hãy theo dõi chính xác hai lần chỉnh sửa thay vì chỉ nhìn tên thư mục.

**Purpose:**

Biến triệu chứng mất sửa đổi thành timeline có thể suy ra chính xác lần chép nào làm thất lạc bản sửa của Bình.

### Scene 4 — Ví dụ: hai người cùng sửa dự án bằng cách gửi tệp và thư mục phiên bản

**Time:** `04:21–05:36`

**Visual:**

Dựng timeline 09:00 bản thuế 8%; 10:00 An sửa 10%; 10:30 Bình thêm làm tròn; 11:00 An gửi cả thư mục. So hai ô `10%` và `Làm tròn` trước/sau lần chép.

**Script:**

Bắt đầu từ bản có thuế tám phần trăm. An đổi thành mười phần trăm, còn Bình thêm bước làm tròn. Họ trao đổi các thư mục bằng tin nhắn. Tại thời điểm An gửi bản của mình, thư mục ấy chưa có sửa đổi của Bình. Nếu cả nhóm lấy nguyên thư mục An làm bản cuối, thuế mới vẫn có, nhưng xử lý làm tròn bị bỏ sót. Ta không thể chữa bằng cách chọn file có timestamp muộn nhất. Cần đối chiếu cả hai thay đổi, xác định phần nào phải kết hợp, rồi ghi lại bản được chấp nhận.

**Purpose:**

Lấy running scenario cụ thể làm minh chứng cho mất thay đổi khi sao chép thủ công.



## Mã nguồn, phiên bản, kho lưu trữ và lịch sử thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Thu gọn timeline 09:00–11:00; gắn nhãn trực tiếp vào `Invoice.java`, snapshot, repository và từng nút revision history.

**Script:**

Để gọi đúng từng mốc trên timeline, cần thống nhất một số thuật ngữ nền tảng.

**Purpose:**

Gắn bốn thuật ngữ source/version/repository/history vào các vật thể đã xuất hiện để tránh học thuộc định nghĩa rời rạc.

### Scene 5 — Mã nguồn, phiên bản, kho lưu trữ và lịch sử thay đổi

**Time:** `05:48–07:03`

**Visual:**

Bốn thẻ cùng một hình: `Source = Invoice.java`, `Version = snapshot`, `Repository = nơi lưu lịch sử`, `History = chuỗi mốc`; nối thẻ bằng mũi tên.

**Script:**

Mã nguồn là nội dung chúng ta xây dựng, chẳng hạn `Invoice.java`. Một phiên bản là trạng thái mã tại một mốc xác định, không nhất thiết là thư mục mang tên số phiên bản. Repository là kho quản lý dữ liệu phiên bản và tham chiếu; còn lịch sử cho phép đi từ bản hiện tại về những thay đổi đã ghi. Bốn khái niệm này liên hệ với nhau nhưng không thay thế nhau. Khi nói ‘đã lưu’ cần hỏi: mới lưu vào editor, đã ghi vào lịch sử, hay đã chia sẻ đến nơi nhóm sử dụng?

**Purpose:**

Cố định từ vựng để người xem không nhầm source file với history hoặc shared acceptance.



## Nguồn chung, truy vết và trách nhiệm thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:15`

**Visual:**

Giữ đường revision A→B cùng nhãn Version/History; mở bảng `Author / Changed lines / Reason / Accepted?` và để dấu hỏi ở Approved.

**Script:**

Biết phiên bản thôi chưa đủ: khi tranh luận về một sửa đổi, nhóm cần căn cứ gì?

**Purpose:**

Chuyển từ tồn tại lịch sử sang trách nhiệm chứng minh ai thay đổi điều gì và nhóm đã nhận hay chưa.

### Scene 6 — Nguồn chung, truy vết và trách nhiệm thay đổi

**Time:** `07:15–08:30`

**Visual:**

Trên bảng review minh họa có cột `Tác giả / Nội dung / Lý do / Trạng thái tiếp nhận`, điền một dòng của An và một dòng của Bình; dòng của Bình đang chờ xét.

**Script:**

Giả sử An hỏi vì sao mã có mức thuế mười phần trăm. Lịch sử đáng tin cậy giúp truy từ trạng thái hiện tại về nội dung thay đổi, tác giả và lý do được ghi. Nhưng nguồn chung được nhóm chấp nhận còn cần một quyết định cộng tác: ai xem xét, đã đáp ứng điều kiện nào, và thay đổi đang ở trạng thái nào. Chúng ta chỉ phác bức tranh đó ở đây; cơ chế commit thuộc Git, còn phê duyệt và quyền truy cập thuộc nền tảng cộng tác. Sang chương sau, ta sẽ tìm hiểu phần lịch sử phiên bản cung cấp được bằng chứng gì.

**Purpose:**

Kết thúc bằng traceability/accountability và chuyển sang vai trò thực tế của hệ thống kiểm soát phiên bản.
