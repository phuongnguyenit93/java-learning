---
video:
  url: ""
---

# Git Flow và phối hợp phát hành theo phiên bản

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

## Bối cảnh ra đời và tiêu chí lựa chọn Git Flow

<!-- VIDEO_SECTION -->

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `00:00–01:52`

**Visual:**

Trên màn hình Vincent Driessen 2010/2020, timeline ứng dụng phát hành theo đợt và web app triển khai liên tục, kèm hai commit graphs khác nhau.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Git Flow được đề xuất trong bối cảnh cần chuẩn bị phiên bản phát hành theo đợt, khi phát triển tính năng mới vẫn tiếp tục. Hãy nhìn sơ đồ gốc năm 2010 và ghi chú 2020 của tác giả: ông không khuyên lấy mô hình ấy làm chuẩn cho mọi phần mềm. Web app triển khai liên tục thường có thể dùng workflow đơn giản hơn; sản phẩm phát hành theo phiên bản hoặc hỗ trợ nhiều bản có lý do cân nhắc phân vai nhánh. Chiến lược xuất phát từ yêu cầu phát hành, không từ lệnh plugin git flow.

**Purpose:**

Đặt Git Flow vào ngữ cảnh phiên bản, trích nhận xét tác giả 2020.

## Vai trò của nhánh production (master gốc, có thể đặt main) và develop trong Git Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:05`

**Visual:**

Giữ thẻ bài gốc Driessen 2010 và ghi chú 2020, phóng hai dòng `master` production và `develop` đang nhận feature, chú thích `main` là tên mới tùy dự án.

**Script:**

Git Flow gốc phục vụ phát hành theo phiên bản. Trước tiên xem hai đường dài hạn production và develop.

**Purpose:**

Đặt đúng bối cảnh tên branch lịch sử trước khi mô tả hai vai trò mainline.

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `02:05–03:57`

**Visual:**

Hai đường dài `master` (gốc) và `develop`, bên cạnh alias minh họa `main` ngày nay; tag release nằm trên production branch.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Trên sơ đồ Driessen, `master` là đường mã đã phát hành và `develop` là đường nhận các feature mới cho bản sắp tới. Nhiều repository hiện đại chọn tên `main` thay cho `master` ở vai trò production; đổi tên không đổi cấu trúc nguyên lý. Vì vậy khi đọc lệnh trong bài gốc phải hiểu master chính là production line của mô hình cũ, không mặc định copy lệnh sang repo mà chỉ có main. Điểm nổi bật ở đây là hai dòng chính tồn tại dài hạn, khác mô hình chỉ tập trung một trunk.

**Purpose:**

Giải thích master/main chỉ là tên, develop là vai trò riêng.

## Vai trò nhánh feature, release và hotfix trong Git Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:10`

**Visual:**

Từ hai đường master/develop tạo feature/cart tách develop, release/1.2 tách develop và hotfix/1.1.1 tách production; tô ba hướng trở về.

**Script:**

Khi đã phân biệt production với develop, hãy theo nơi tách và nhập lại feature, release và hotfix.

**Purpose:**

Thể hiện bằng đồ thị vì sao ba vai trò của Git Flow cần nguồn tách và điểm hợp nhất khác nhau.

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `04:10–06:02`

**Visual:**

Đồ thị 5 đường: `feature/cart` tách develop; `release/1.2` từ develop; `hotfix/1.1.1` từ production, mũi tên merge đích đúng.

Thêm hai trạng thái thay thế của đường hotfix: không có release đang mở → về production và develop; đã mở `release/1.2` → về production và release đó, sau khi kết thúc release thì fix đến develop. Đánh dấu đây là ngoại lệ có thật của sơ đồ Driessen, không phải quy tắc Git tự động.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Hãy theo từng mũi tên: feature/cart xuất phát từ develop và trở về develop khi xong; release/1.2 cắt từ develop để chuẩn bị bản sắp phát hành; hotfix/1.1.1 tách từ production line hoặc tag của bản đang chạy để xử lý lỗi gấp. Với mô hình Git Flow cổ điển, bản sửa trong release/hotfix phải được đưa về các dòng chính phù hợp, tránh bản kế tiếp tái phát lỗi. Đây là vai trò do quy trình quy định, không phải ba kiểu đối tượng Git khác nhau.

Lưu ý tình huống `release/1.2` đã mở: theo bài gốc, hotfix đi vào release branch đó thay vì nhất thiết nhập ngay develop; khi release được hoàn tất, bản sửa sẽ được đưa về develop. Nếu develop cần bản vá trước đó, đội vẫn có thể nhập sớm và kiểm tra riêng.

**Purpose:**

Trình bày hướng nhánh feature/release/hotfix trong Git Flow gốc.

## Ổn định bản sắp phát hành mà vẫn phát triển tính năng tiếp theo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:02–06:15`

**Visual:**

Giữ ba nhánh có mũi tên, khóa scope release/1.2 chỉ còn fix/metadata; cho develop tiếp tục 1.3, tô hai bước merge production và back-merge.

**Script:**

Ba vai trò nhánh tạo điều kiện ổn định bản phát hành. Đóng phạm vi release trong khi develop vẫn nhận việc mới.

**Purpose:**

Chứng minh sửa lỗi phát hành phải trở lại develop để tránh tái phát ở phiên bản kế tiếp.

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `06:15–08:07`

**Visual:**

Trong lịch hai nhóm: release/1.2 chỉ nhận version metadata và bugfix; develop tiếp tục feature/1.3; cuối kỳ release merge production rồi back-merge develop.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Ngày cắt release/1.2, đội cố định phạm vi cho phiên bản sắp ra. Nhánh release nhận sửa lỗi và chỉnh metadata cần thiết, trong khi tính năng tương lai tiếp tục ở develop. Khi kiểm tra xong, Git Flow cổ điển merge release vào production, gắn tag và merge ngược những sửa ổn định về develop. Hãy nhìn nếu quên bước sau: một bug đã sửa cho 1.2 có thể lại xuất hiện khi phát hành 1.3 từ develop. Chi phí đổi lại là phải duy trì và đồng bộ hai dòng.

**Purpose:**

Cho thấy release stabilization và trách nhiệm merge back.

## Nhiều phiên bản đang được bảo trì cần chính sách bổ sung ngoài Git Flow cơ bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:07–08:20`

**Visual:**

Từ release/1.2 đã hoàn tất, thêm maintenance/1.1 còn khách dùng và bảng release→fix để lộ một ô hotfix chưa được backport.

**Script:**

Bản mới sắp ra, còn phiên bản cũ vẫn có khách sử dụng thì sao? Cần phân công bảo trì và backport rõ ràng.

**Purpose:**

Nêu lý do lịch sử Git Flow cơ bản không tự giải quyết nghĩa vụ hỗ trợ nhiều bản cũ.

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `08:20–10:12`

**Visual:**

Timeline hai bản đã phát hành `1.1.x` và `1.2.x` với hai maintenance lines, hotfix A áp ở bản cũ nhưng thiếu ở bản mới.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Giả sử khách hàng A vẫn dùng 1.1 còn B dùng 1.2. Một nhánh release chuẩn bị 1.3 không tự giải quyết việc sửa bảo mật cho cả hai phiên bản cũ. Nhóm cần thêm chính sách nhánh bảo trì, chỉ định ai chọn bản vá, kiểm tra tương thích và ghi dấu bản nào đã nhận fix. Hãy đặt bảng 'phiên bản → commit vá → kiểm thử' bên đồ thị. Không phát biểu rằng cứ bật Git Flow là mọi phiên bản cũ tự được hỗ trợ.

**Purpose:**

Giới hạn đúng phạm vi Git Flow gốc với maintenance đa phiên bản.

## Chi phí đồng bộ và vì sao tác giả khuyên quy trình đơn giản hơn cho continuous delivery

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:12–10:25`

**Visual:**

Giữ bảng maintenance versions, mở hai lịch web deploy hằng ngày và desktop phát hành mỗi quý; highlight chú thích của Driessen 2020.

**Script:**

Nhiều bản đang hỗ trợ đòi hỏi công đồng bộ. Hãy đối chiếu chi phí này với lưu ý của tác giả về continuous delivery.

**Purpose:**

Đánh giá chi phí hai dòng dài hạn dựa trên nhịp giao hàng thay vì coi Git Flow là mặc định.

### Scene 1 — Theo dõi một phiên bản trên Git Flow

**Time:** `10:25–12:17`

**Visual:**

Biểu đồ so sánh: web deploy liên tục cần một đường main gọn, sản phẩm versioned cần nhánh ổn định; highlight note Driessen 2020.

Đồ thị chỉ là repository demo; sơ đồ gốc dẫn nguồn Vincent Driessen và giữ chú thích master là tên lịch sử, main là biến thể hiện đại.

**Script:**

Đặt hai đội cạnh nhau. Dịch vụ web triển khai nhiều lần mỗi ngày sẽ phải liên tục đưa sửa đổi từ develop qua release sang production nếu dùng đủ Git Flow; chuyển nhánh và back-merge có thể thành thủ tục tốn công. Với phần mềm phát hành theo gói, khoảng ổn định phiên bản lại có ý nghĩa. Chính Driessen năm 2020 gợi ý workflow đơn giản như GitHub Flow cho continuous delivery. Câu hỏi đúng không phải 'Git Flow có lỗi thời không?' mà là 'lịch phát hành của đội có đủ khác biệt để trả chi phí hai dòng và nhiều nhánh không?'.

**Purpose:**

Đưa trade-off và lời khuyên của tác giả vào quyết định thực tế.
