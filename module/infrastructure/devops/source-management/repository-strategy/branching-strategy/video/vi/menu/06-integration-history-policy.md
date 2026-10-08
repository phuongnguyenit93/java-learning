---
video:
  url: ""
---

# Chính sách hợp nhất và lịch sử thay đổi

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

## Chính sách lịch sử sau tích hợp và nhu cầu thống nhất ở cấp nhóm

<!-- VIDEO_SECTION -->

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `00:00–01:52`

**Visual:**

Một commit graph gốc có feature ba commit, bên phải ba lựa chọn merged target history, team decision card ghi audit/revert/review.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Chúng ta có một PR chứa ba commit: chuẩn bị schema, đổi xử lý và thêm kiểm thử. Câu hỏi không chỉ là Git merge được hay không mà là sau tích hợp, lịch sử trên main nên kể câu chuyện gì. Giữ cả ba commit giúp lần lỗi chi tiết; gộp thành một commit thể hiện một ý định thống nhất; lịch sử tuyến tính có thể dễ đọc hơn nhưng thay ID. Hãy ghi mục tiêu của nhóm trước khi so ba hình để tránh chọn chính sách chỉ vì nhìn graph đẹp.

**Purpose:**

Đặt mục đích truy vết lên trước lựa chọn kiểu merge.

## Merge commit: giữ ngữ cảnh nhánh và các commit trung gian

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:05`

**Visual:**

Giữ PR ba commit W1/W2/W3; bật target merge commit M với hai parent và các commit cũ vẫn nhìn thấy trên commit graph.

**Script:**

Ta có ba commit ở nhánh feature. Trước tiên xem merge commit giữ lịch sử và hai nhánh cha như thế nào.

**Purpose:**

Chuyển lựa chọn lịch sử thành bằng chứng merge giữ ranh giới nhánh và parent relation.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `02:05–03:57`

**Visual:**

Terminal `git log --graph --oneline --all` sau demo merge non-fast-forward: nút M có hai parent A và B, ba commit feature vẫn hiện.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Trong repo thử nghiệm, main và feature cùng có commit riêng rồi được tích hợp bằng merge commit. Trên log graph, nút M có hai parent và các commit feature vẫn hiện với danh tính cũ. Điều này giúp xác định ranh giới một đợt thay đổi và các bước sửa bên trong. Nhưng khi có nhiều PR nhỏ, đồ thị sẽ có nhiều nút merge; cần thỏa thuận cách viết commit message và review. Lưu ý fast-forward có thể không tạo merge commit nếu không yêu cầu rõ; đừng mô tả mọi lệnh merge đều sinh nút hai cha.

**Purpose:**

Chỉ ra parent và intermediate commits của merge graph.

## Squash merge: gộp một thay đổi logic vào lịch sử nhánh đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:10`

**Visual:**

Giữ merge node M, đưa graph thứ hai có W1/W2/W3 được squash thành S trên target; giữ discussion của PR ở bảng riêng.

**Script:**

Merge giữ các commit trung gian. Giờ gom ba bản vá thành một squash commit đại diện một thay đổi logic.

**Purpose:**

So sánh đơn vị lịch sử trên target với hồ sơ trao đổi trên host không bị gộp trong một commit.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `04:10–06:02`

**Visual:**

Graph trước và sau squash: ba commit W1/W2/W3 thành một S trên main, PR giữ link review riêng.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Hãy đặt ba commit 'WIP', 'fix test', 'rename variable' trên feature. Sau squash, main có commit S chứa kết quả tổng hợp và tên mô tả thay đổi nghiệp vụ. Các commit trung gian không xuất hiện riêng trên đường main theo cùng danh tính; PR trên hosting vẫn có thể giữ lịch sử thảo luận của chúng. Đây là lý do squash hữu ích khi muốn main đọc theo đơn vị thay đổi logic, nhưng khó bisect từng bước ở main hơn. Nếu feature chứa hai ý định độc lập, gộp hết chưa chắc tốt.

**Purpose:**

Hiểu gộp commit đích và giới hạn truy vết trung gian.

## Rebase-and-merge: lịch sử tuyến tính, từng commit riêng và commit ID mới

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:02–06:15`

**Visual:**

Từ target squash S, mở đồ thị rebase-and-merge với W1'/W2'/W3' nằm tuyến tính; gắn nhãn SHA cũ và SHA sau replay.

**Script:**

Squash tạo một commit đích. Rebase-and-merge giữ từng bản vá trên dòng thẳng nhưng dùng commit ID mới.

**Purpose:**

Phân biệt một commit tổng hợp với chuỗi commit giữ ranh giới từng patch nhưng có ID mới.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `06:15–08:07`

**Visual:**

So graph rebase-and-merge: các patch tương ứng W1/W2/W3 thành W1'/W2'/W3' theo đường thẳng trên main; commit IDs cũ/mới khác.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Rebase-and-merge đặt các thay đổi của nhánh lên đầu main thành một chuỗi tuyến tính, thường mỗi commit nguồn tương ứng commit mới trên target. Vì parent mới và metadata của commit có thể đổi, ID không cần trùng với branch cũ; đặc biệt GitHub rebase-and-merge tạo SHA mới. Kết quả giữ được các bước logic nếu commit nguồn được tổ chức sạch, nhưng cũng làm lịch sử nguồn và đích khác danh tính. Đọc parent chain và commit message thay vì chỉ nhìn đường thẳng đẹp.

**Purpose:**

Chỉ ra lịch sử tuyến tính không tương đương giữ nguyên commit IDs.

## Kết quả rebase-and-merge trên nhánh đích so với rebase nhánh công việc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:07–08:20`

**Visual:**

Giữ chuỗi W1'–W3' trên target, chia hai hàng local feature rebase F→F' và host PR rebase-and-merge lên main, tách source ref.

**Script:**

Đồ thị thẳng có thể đến từ hai thao tác. Hãy phân biệt rebase-and-merge ở host và rebase nhánh làm việc.

**Purpose:**

Không nhập nhằng tác giả rewrite work branch với host tạo commit mới ở target history.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `08:20–10:12`

**Visual:**

Đồ thị hai hàng: local feature rebase trên main tạo F' rồi PR cập nhật; hosted rebase-and-merge tạo M' trên target mà không sửa nhánh gốc.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Hãy tách hai hành động. Nếu tác giả rebase branch feature local và push các commit thay ID, nhánh công việc được viết lại; đồng đội đã lấy branch cũ có thể phải hòa giải. Nếu nền tảng hoàn tất PR bằng rebase-and-merge, thay đổi được phát lại trên target trong lúc nguồn có thể giữ lịch sử cũ. Hai kết quả trên main có thể cùng tuyến tính nhưng thao tác và rủi ro chia sẻ khác nhau. Không lấy menu của hosting làm bằng chứng rằng tác giả đã chạy `git rebase` trên máy.

**Purpose:**

Phân biệt rebase ở nhánh công việc và chính sách merge trên server.

## Rủi ro thay đổi commit đã chia sẻ và tác động đến người cộng tác

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:12–10:25`

**Visual:**

Từ hai cơ chế rebase cho clone đồng nghiệp B giữ H trong khi tác giả A ở H'; khoanh force-with-lease như điều kiện không phải quyền cho phép.

**Script:**

Hai thao tác ảnh hưởng cộng tác khác nhau. Xem điều gì xảy ra khi commit đã chia sẻ bị viết lại.

**Purpose:**

Nêu tác động rewrite lịch sử đã publish đối với những người dựa trên commit IDs cũ.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `10:25–12:17`

**Visual:**

Clone A và clone B cùng trỏ commit H; sau khi A rebase feature thì A có H' còn B vẫn ở H; cảnh báo force-push.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Trong clone thử A và B, cả hai đã lấy commit H của feature. A rebase lịch sử rồi đổi ID thành H'; B vẫn dựa trên H. Nếu A ép push, lần đồng bộ sau B có thể thấy nhánh diverged hoặc commit tương đương bị lặp trong graph. Đó là lý do tránh rewrite lịch sử đã chia sẻ nếu không phối hợp rõ ràng. `--force-with-lease` có cơ chế kiểm tra điều kiện nhưng không thay review hoặc biến thao tác trở nên vô hại. Video chỉ minh họa trên clone bỏ đi, không force nhánh thật.

**Purpose:**

Giải thích rủi ro rewrite shared history qua hai clone thực.

## Đánh đổi giữa truy vết, hoàn tác, điều tra sự cố và lịch sử dễ đọc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:17–12:30`

**Visual:**

Từ hai clone H/H' phân kỳ, mở bảng merge/squash/rebase với tiêu chí bisect, revert, audit và commit context, giữ diff nghiệp vụ để đối chiếu.

**Script:**

Sau khi thấy rủi ro đổi commit ID, hãy đánh giá merge, squash và rebase theo audit, revert và chẩn đoán.

**Purpose:**

Chuyển rủi ro lịch sử thành lựa chọn policy theo khả năng chẩn đoán và kiểm toán.

### Scene 1 — Đối chiếu lịch sử sau hợp nhất

**Time:** `12:30–14:22`

**Visual:**

Bảng quyết định ba cột merge/squash/rebase, hàng 'trace feature', 'bisect', 'revert', 'PR audit', bên cạnh graph mỗi loại.

Graph tạo bằng repository thử nghiệm; nếu hiển thị nút hosting, ảnh là minh họa từ docs có dẫn nguồn, không gán live PR cho người dùng.

**Script:**

Chốt bằng ma trận, không chọn người thắng tuyệt đối. Merge commit giữ ranh giới nhánh và bước trung gian, đổi lại graph phức tạp. Squash khiến mỗi PR có thể thành một commit logic thuận lợi đọc và đảo một cụm, nhưng mất chi tiết bước trong target. Rebase-and-merge giữ chuỗi tuyến tính và có thể giữ từng bước, đổi lại ID được phát lại. Với mọi lựa chọn, reviewer vẫn cần đọc diff và test; thông điệp commit, PR link và cách backport mới quyết định mức dễ truy vết.

**Purpose:**

Tổng hợp lựa chọn theo mục đích điều tra và revert.
