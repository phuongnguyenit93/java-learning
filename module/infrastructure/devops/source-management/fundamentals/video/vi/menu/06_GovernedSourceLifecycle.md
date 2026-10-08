---
video:
  url: ""
---

# Vòng đời thay đổi có kiểm soát

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

## Hành trình từ thay đổi cá nhân đến nguồn chung

<!-- VIDEO_SECTION -->

### Scene 1 — Hành trình từ thay đổi cá nhân đến nguồn chung

**Time:** `00:00–01:15`

**Visual:**

Hiện sơ đồ 6 mốc liên tục với `An sửa Invoice.java → Ghi phiên bản → Chia sẻ → Review → Chấp nhận → Build`; dùng hai màu để phân biệt trạng thái nguồn và delivery.

**Script:**

Đến đây, ta đã có các mảnh của bức tranh quản lý mã nguồn. An bắt đầu bằng một sửa đổi trên máy, ghi nhận phiên bản, chia sẻ để Bình có thể xem, rồi nhóm quyết định có tiếp nhận thay đổi vào nguồn chung hay không. Cuối cùng, bộ phận delivery mới dùng nguồn đã tiếp nhận để build và kiểm tra phát hành. Đừng rút gọn sáu mốc thành một nút Save. Mỗi ranh giới có loại bằng chứng khác nhau. Trong chương cuối, chúng ta sẽ dùng chính thay đổi thuế và làm tròn để phân loại từng mốc, kể cả trường hợp chưa được chấp nhận.

**Purpose:**

Ghép xuyên suốt vòng đời từ local edit đến accepted source và điểm bàn giao sang delivery.



## Đã ghi nhận, đã chia sẻ, đã xem xét và đã chấp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Giữ pipeline `Edit→Record→Share→Review→Accept→Build`; làm nổi bốn thẻ Recorded, Shared, Reviewed, Accepted, gắn mỗi thẻ một dấu vết khác.

**Script:**

Hãy tách những trạng thái dễ bị gọi chung là 'đã xong'.

**Purpose:**

Ngăn gộp các trạng thái lịch sử/cộng tác thành một nhãn Done bằng bốn dạng bằng chứng phân biệt.

### Scene 2 — Đã ghi nhận, đã chia sẻ, đã xem xét và đã chấp nhận

**Time:** `01:27–02:42`

**Visual:**

Bốn thẻ `Recorded`, `Shared`, `Reviewed`, `Accepted` xếp thành hàng, bên dưới đặt bằng chứng `revision ID / remote ref / reviewer outcome / target integration`.

**Script:**

Một thay đổi đã ghi nhận nghĩa là có phiên bản trong lịch sử. Đã chia sẻ nghĩa phiên bản ấy có mặt ở nơi nhóm trao đổi; điều đó không bảo đảm mọi người đã đọc. Đã xem xét nghĩa có hoạt động đánh giá với ý kiến hoặc phiếu thích hợp. Đã chấp nhận nghĩa thay đổi đã đi vào nguồn tích hợp theo quy trình của nhóm. Trình tự thường đi theo hướng này nhưng các trạng thái review có thể cần lặp lại sau khi tác giả sửa tiếp. Cứ nhìn loại bằng chứng dưới mỗi thẻ: mã commit, ref được chia sẻ, kết quả review và mốc nguồn đích. Không lấy bằng chứng ở cột này thay cho cột khác.

**Purpose:**

Phân định bốn trạng thái bằng loại bằng chứng độc lập, tránh suy luận 'push = merge'.



## Tình huống: thay đổi đã chia sẻ nhưng chưa được nhóm chấp nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Giữ bốn thẻ và chỉ bật Recorded/Shared; mở PR 42 `Open`, bình luận của Bình `Needs rounding test`, trong khi main vẫn thuế 8%.

**Script:**

Đã push lên server có phải đã chấp nhận? Thử xem một tình huống có chủ ý chưa hoàn tất.

**Purpose:**

Dùng phản ví dụ shared-but-unaccepted để làm rõ status proposal khác status nguồn đã tích hợp.

### Scene 3 — Tình huống: thay đổi đã chia sẻ nhưng chưa được nhóm chấp nhận

**Time:** `02:54–04:09`

**Visual:**

Mô phỏng `PR 42: tax-rate 10%`, status `Open`, review của Bình `Need rounding test`, target `main` còn ở 8%. Mũi tên chia sẻ sáng, mũi tên accepted xám.

**Script:**

An có commit C đổi thuế sang mười phần trăm và đã đẩy nhánh topic lên nơi dùng chung. Điều đó khiến người khác có thể xem thay đổi, nhưng nhánh main vẫn có thể đang giữ bản cũ. Bình đọc đề xuất và phát hiện quy tắc làm tròn chưa có kiểm thử, nên yêu cầu sửa trước khi nhận. Nếu lúc này ai đó báo 'mã đã lên rồi', câu nói ấy thiếu chính xác: mã được chia sẻ, chưa được nhóm chấp nhận. Hãy nhìn hai bằng chứng tách biệt: commit C trên nhánh đề xuất và target main chưa nhận C.

**Purpose:**

Dùng ví dụ shared-not-accepted để khẳng định khác biệt giữa publication và integration.



## Sai lầm khi đồng bộ, đánh giá và thống nhất nguồn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Giữ PR Open và main chưa đổi; hiện bốn thẻ lỗi Overwrite, Stale sync, Outdated screenshot, Push mistaken for merge cạnh đúng dấu vết cần kiểm.

**Script:**

Nhóm thường mắc lỗi nào khi bỏ qua những ranh giới ấy?

**Purpose:**

Liên hệ một đề xuất chưa nhận với các failure modes gây báo cáo sai hoặc thất lạc bản sửa.

### Scene 4 — Sai lầm khi đồng bộ, đánh giá và thống nhất nguồn

**Time:** `04:21–05:36`

**Visual:**

Bốn ô cảnh báo xuất hiện tuần tự: `Ghi đè thư mục`, `Quên đồng bộ`, `Duyệt theo ảnh chụp cũ`, `Nhầm shared với accepted`; mỗi ô có một quan sát kiểm tra tương ứng.

**Script:**

Nếu chép thư mục lên nhau, nhóm có thể âm thầm mất sửa của Bình. Nếu quên đồng bộ, An đánh giá trên lịch sử cũ và không thấy commit mới. Nếu reviewer chỉ xem diff từ hôm qua, một lần push mới có thể đổi nội dung đang chờ duyệt. Và nếu coi push thành công là đã nhận vào main, báo cáo tiến độ sẽ sai. Mỗi lỗi có một điểm kiểm tra: xem lịch sử thực, so nhánh nguồn và nhánh đích, xem diff đang được đề xuất và kiểm tra trạng thái review hiện tại. Không cần học tất cả lệnh hay giao diện ở đây, chỉ cần chọn đúng bằng chứng cho đúng câu hỏi.

**Purpose:**

Liên kết bốn failure mode với kiểm tra an toàn, không quy mọi lỗi về Git hoặc hosting.



## Dấu vết cần có của một thay đổi được kiểm soát

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Thu gọn bốn thẻ lỗi; dựng evidence sheet `Change / Revision / Proposal / Reviewer decision / Accepted target`, để trạng thái pending không bị đánh dấu hoàn tất.

**Script:**

Có thể trình bày một thay đổi đã được kiểm soát bằng một bộ bằng chứng ngắn gọn không?

**Purpose:**

Chuyển từ cách nhận diện lỗi sang checklist nguồn dữ liệu có thể truy vết trước khi bàn giao.

### Scene 5 — Dấu vết cần có của một thay đổi được kiểm soát

**Time:** `05:48–07:03`

**Visual:**

Hiện bảng 5 hàng cho thay đổi An: `Change`, `Recorded revision`, `Shared proposal`, `Review decision`, `Accepted target`; điền mỗi hàng một giá trị giả định có nhãn demo.

**Script:**

Khi bàn giao cho đồng đội, hãy có một bản tóm tắt kiểm chứng được. Nội dung nào đã đổi? Revision nào ghi nhận sửa đổi đó? Đề xuất được chia sẻ ở đâu? Ai xem xét và yêu cầu điều gì? Nhánh hoặc mốc tích hợp nào cuối cùng đã nhận thay đổi? Những thông tin này giúp người nhận tái dựng quyết định mà không cần lục mọi tin nhắn. Nếu có một cột chưa đạt, hãy để rõ trạng thái đang chờ thay vì tự đánh dấu hoàn tất. Một thay đổi được kiểm soát không có nghĩa không thể sai; nó nghĩa nhóm có dấu vết để đánh giá, sửa và giải thích quyết định.

**Purpose:**

Cung cấp checklist bằng chứng thực tế và trung thực về trạng thái, không giả định approval.



## Bàn giao nguồn đã chấp nhận sang quy trình phân phối

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:15`

**Visual:**

Giữ evidence sheet đã chốt accepted revision M; vẽ ranh giới đậm sang `Build→Test→Release→Deploy` và để các trạng thái delivery là `unknown`.

**Script:**

Sau khi nhóm đã chấp nhận mã, chúng ta còn phải kiểm chứng điều gì trước khi có phần mềm chạy?

**Purpose:**

Phân định bằng chứng source được nhận với kết quả build/release/deployment mà video không chạy thực tế.

### Scene 6 — Bàn giao nguồn đã chấp nhận sang quy trình phân phối

**Time:** `07:15–08:30`

**Visual:**

Mũi tên từ `Accepted source M` sang `Build → Test → Release → Deploy` đặt dưới đường ranh giới đậm. Ghi chú `Nguồn đã nhận ≠ ứng dụng đã chạy`.

**Script:**

Ngay cả khi bản M đã chứa cả thuế mười phần trăm và xử lý làm tròn, chúng ta mới biết nguồn đã được nhóm chấp nhận. Chưa có bằng chứng rằng bản ấy đã build thành công, vượt qua test, được phát hành hay chạy trên môi trường production. Phần đó thuộc pipeline và quy trình phân phối phần mềm. Nếu nghe câu 'bug đã lên production', hãy yêu cầu bằng chứng deployment riêng, không chỉ một PR Completed. Từ sáu chương, hãy nhớ chuỗi kiểm chứng: biết phiên bản nào, đã ghi nhận ở đâu, ai đã xem, thay đổi có được nhận không, rồi mới hỏi nó được phân phối thế nào.

**Purpose:**

Kết chương bằng ranh giới source acceptance vs delivery và câu hỏi bằng chứng cho từng bước.
