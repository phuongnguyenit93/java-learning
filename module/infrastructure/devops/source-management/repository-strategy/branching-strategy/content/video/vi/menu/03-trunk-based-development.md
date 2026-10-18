---
video:
  url: ""
---

# Trunk-Based Development và tích hợp liên tục

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

## Trunk-Based Development: tích hợp thường xuyên để tránh nhánh tồn đọng

<!-- VIDEO_SECTION -->

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `00:00–01:54`

**Visual:**

Đồ thị main có commit mỗi ngày; một nhánh `feature/tax` xuất hiện rồi biến mất trong 1 ngày, đặt cạnh sơ đồ nhánh phát triển kéo dài 3 tuần.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Trunk-Based Development bắt đầu từ một điểm chung: thay đổi được đưa về trunk thường xuyên thay vì duy trì các dòng phát triển cô lập. Tên trunk có thể là main; đổi tên branch không tạo ra quy trình mới. Hãy theo dõi `git log --graph` trong một tuần demo: các nhánh review nhỏ quay về main nhanh, và không còn hàng loạt nhánh feature treo nhiều tuần. Đây là nguyên tắc cộng tác, không phải một lệnh Git đặc biệt hay lựa chọn chỉ có ở monorepo.

**Purpose:**

Giải thích TBD bằng nhịp tích hợp thật thay vì tên branch.

## Trunk duy nhất và yêu cầu giữ nhánh chính đáng tin cậy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:54–02:07`

**Visual:**

Giữ graph một trunk có feature một ngày; tô một commit gây test đỏ rồi kéo cảnh cảnh báo tới người sở hữu khôi phục main trước PR tiếp theo.

**Script:**

Các commit thường xuyên hội tụ vào một trunk. Hãy xem kiểm chứng và xử lý lỗi giúp giữ dòng chung đáng tin thế nào.

**Purpose:**

Chuyển từ tần suất hợp nhất sang trách nhiệm giữ main đáng tin trong khi nhiều người dùng cùng một nền.

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `02:07–04:01`

**Visual:**

Graph một trunk được kiểm thử thường xuyên; bảng đỏ/vàng/xanh minh họa kiểm thử và trách nhiệm khắc phục khi build đỏ.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Nhánh chính được nhiều người dùng làm nền, nên đưa thay đổi vào thường xuyên chỉ an toàn nếu phản hồi nhanh. Trên sơ đồ, một commit làm bài test đỏ; người vừa tích hợp hoặc nhóm chịu trách nhiệm khôi phục trunk ngay, không chờ đến ngày release. Branch protection hoặc checks của hosting là công cụ hỗ trợ, còn cam kết giữ main đáng tin là quy định của nhóm. Một build xanh không chứng minh logic nghiệp vụ tuyệt đối đúng, nhưng giúp phát hiện lỗi sớm.

**Purpose:**

Nối độ tin cậy nhánh chính với trách nhiệm xử lý test fail.

## Tích hợp trực tiếp so với nhánh review rất ngắn hạn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:01–04:14`

**Visual:**

Từ bảng phục hồi trunk, mở hai lối thay đổi được kiểm thử: direct commit và PR một ngày; đặt PR kéo dài hai tuần ở làn cảnh báo.

**Script:**

Khi đã biết yêu cầu về trunk ổn định, so commit trực tiếp với nhánh review ngắn rồi quay về cùng trunk.

**Purpose:**

Cho thấy review branch cực ngắn và direct-to-trunk có thể phục vụ cùng một kỷ luật hội tụ.

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `04:14–06:08`

**Visual:**

Hai timeline cùng trunk: nhóm nhỏ commit trực tiếp sau validation; nhóm khác qua PR 1 ngày rồi merge; một nhánh PR 2 tuần được tô cảnh báo.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Một đội nhỏ có thể cùng commit trực tiếp nếu kỷ luật và kiểm tra cho phép; đội khác cần review qua nhánh rất ngắn hạn. Cả hai có thể vận hành TBD vì thay đổi trở về main nhanh và nhánh review không thành nơi phát triển dài hạn của nhiều người. Hãy gắn thời gian mở và merge ở PR, không suy từ việc có PR rằng đó chính là GitHub Flow hay mặc định không phải TBD. Kiểm chứng bằng tuổi nhánh và khoảng cách khỏi trunk.

**Purpose:**

Tránh nhầm TBD với 'không bao giờ PR' hoặc 'có PR là GitHub Flow'.

## Thay đổi nhỏ, kiểm tra sớm và tích hợp thường xuyên

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:08–06:21`

**Visual:**

Giữ hai lối direct/PR; đưa user story refund qua ba mốc contract tương thích, hành vi nhỏ và cleanup, mỗi mốc có test trước khi vào trunk.

**Script:**

Hai cách đều tích hợp nhanh. Bây giờ chia tính năng hoàn tiền thành các phần độc lập, kiểm chứng được.

**Purpose:**

Nêu cách chia feature lớn thành các phần hoàn chỉnh có thể tích hợp thường xuyên dù cùng một ticket.

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `06:21–08:15`

**Visual:**

Một user story được chia thành ba commit có test; log main cho thấy mỗi lát đổi contract nhỏ và PR được merge sớm.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Hãy chia yêu cầu hoàn tiền thành ba lát: đầu tiên thêm khả năng đọc trạng thái mới theo cách tương thích, tiếp theo xử lý nghiệp vụ, cuối cùng dọn lối cũ sau khi xác minh. Mỗi lát đi qua kiểm thử riêng rồi được tích hợp vào trunk; chúng ta không cần chờ mọi màn hình của tính năng hoàn thiện. Nhịp tích hợp tốt dựa trên sửa đổi đủ nhỏ để có thể review và hoàn tác, chứ không chỉ đặt cron chạy pipeline thường xuyên. Một thay đổi chưa độc lập an toàn phải được thiết kế lại trước khi merge.

**Purpose:**

Chỉ ra small verified increments hỗ trợ frequent integration.

## Feature flags và nhánh phát hành tùy nhu cầu trong mô hình trunk

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:15–08:28`

**Visual:**

Từ ba mốc refund đã vào trunk, đặt cờ OFF/ON cho hành vi mới và hai lối phát hành: tag từ trunk hoặc cắt release branch muộn.

**Script:**

Có phần mã an toàn nhưng chưa thể mở cho người dùng. Hãy so feature flag với cách chuẩn bị release tùy chọn.

**Purpose:**

Làm rõ ẩn chức năng, tích hợp và chuẩn bị release là các quyền quyết định khác nhau.

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `08:28–10:22`

**Visual:**

Bảng code-path flag OFF/ON, tag release từ trunk, tùy chọn release branch tách sát ngày phát hành; highlight cherry-pick trunk → release.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Feature flag che hành vi chưa sẵn sàng, không che việc code đã vào trunk. Với đội phát hành liên tục có thể dùng tag từ main và sửa tiến khi lỗi xuất hiện. Nếu phải giữ bản ổn định một thời gian, release branch được cắt gần thời điểm phát hành và chỉ nhận bản vá chọn lọc. Theo thực hành trunk-first, hãy tái hiện lỗi, sửa và kiểm tra ở trunk trước rồi cherry-pick commit phù hợp sang release; không phát triển tính năng dài hạn trên release. Nhớ kiểm tra lại cả nhánh bản vá.

**Purpose:**

Phân biệt feature flag, tag và release branch tùy hoàn cảnh.

## Dấu hiệu vận hành trunk thực chất và những cách làm trái nguyên tắc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:22–10:35`

**Visual:**

Giữ cờ feature và hai đường release; tô mũi tên fix bắt đầu từ trunk rồi cherry-pick sang release, mở checklist tuổi nhánh và merge frequency.

**Script:**

Khi đã thấy cờ và đường release, hãy dùng tuổi nhánh, nhịp merge và hướng hotfix để kiểm tra TBD thực chất.

**Purpose:**

Tổng kết tiêu chí phân biệt TBD thật với việc chỉ đặt tên nhánh là main hoặc trunk.

### Scene 1 — Quan sát một trunk đáng tin

**Time:** `10:35–12:29`

**Visual:**

Sơ đồ chẩn đoán: branch age histogram, main merge frequency, active feature branches, release branches và direction hotfix arrows.

Dùng Git repository thử nghiệm và ảnh PR/check có ghi nhãn minh họa; không giả kết quả CI trực tiếp.

**Script:**

Chốt bằng sáu dấu vết: có một trunk thật sự không, nhánh feature tồn tại bao lâu, tích hợp diễn ra mấy lần mỗi tuần, checks có phản hồi nhanh không, bản đang dở có được ẩn an toàn không, và fix production đi từ đâu. Chỉ đổi tên develop thành main trong khi tích hợp theo đợt là chống lại tinh thần TBD. Nếu bug chỉ sửa trên release rồi quên đưa về trunk, phiên bản kế tiếp có thể tái phát. Ta sửa quy trình từ bằng chứng, không áp một khẩu hiệu.

**Purpose:**

Đưa tiêu chí vận hành và anti-patterns có thể đo được.
