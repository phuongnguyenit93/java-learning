---
video:
  url: ""
---

# Rebase và tác động khi viết lại lịch sử

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

## Lịch sử tạo bởi merge so với rebase

<!-- VIDEO_SECTION -->

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `00:00–01:50`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git log --graph --oneline --all; git merge feature/rounding`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Cùng một điểm xuất phát, hãy dựng hai cách tích hợp: merge giữ quan hệ hai nhánh bằng merge commit khi lịch sử đã phân kỳ, còn rebase phát lại commit của nhánh feature trên nền khác để có đường lịch sử tuyến tính hơn. Đặt hai đồ thị trước/sau cạnh nhau: không có lựa chọn nào làm chất lượng code tự tốt lên. Sự khác biệt chính là cấu trúc lịch sử và cách đồng đội hiểu những commit đã chia sẻ.

**Purpose:**

So sánh cấu trúc lịch sử mà không biến merge/rebase thành chính sách đội nhóm.

## Rebase: phát lại commit và thay đổi commit ID

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:03`

**Visual:**

Giữ frame kết quả lệnh [git log --graph --oneline --all] với nhãn 'Lịch sử tạo bởi merge so với rebase'; khoanh chứng cứ đã xác nhận: So sánh cấu trúc lịch sử mà không biến merge/rebase thành chính sách đội nhóm.. Mở cửa sổ terminal/đồ thị kế tại [git log --format='%h %p %s' -5] dưới nhãn 'Rebase: phát lại commit và thay đổi commit ID'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chỉ ra rebase replay tạo commit mới dựa trên parent mới.

**Script:**

Hai đồ thị có thể nhìn tương tự về nội dung nhưng commit ID khác nhau vì sao?

**Purpose:**

Nối phép kiểm chứng 'Lịch sử tạo bởi merge so với rebase' sang 'Rebase: phát lại commit và thay đổi commit ID', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chỉ ra rebase replay tạo commit mới dựa trên parent mới.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `02:03–03:53`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git log --format='%h %p %s' -5; git rebase main`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Hãy đánh dấu commit feature trước khi rebase rồi đặt chúng lên nền main mới trong kho sao chép thử nghiệm. Rebase phát lại thay đổi thành commit mới, vì parent, tree hoặc metadata có thể khác nên ID thường thay đổi. Đừng diễn giải đó là việc 'di chuyển nguyên commit cũ' một cách nguyên vẹn. Kiểm tra log trước/sau và chú ý rằng commit đã public nếu bị thay ID sẽ làm đồng đội khó đồng bộ.

**Purpose:**

Chỉ ra rebase replay tạo commit mới dựa trên parent mới.

## Rebase chuỗi commit cục bộ lên base mới

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:53–04:06`

**Visual:**

Giữ frame kết quả lệnh [git log --format='%h %p %s' -5] với nhãn 'Rebase: phát lại commit và thay đổi commit ID'; khoanh chứng cứ đã xác nhận: Chỉ ra rebase replay tạo commit mới dựa trên parent mới.. Mở cửa sổ terminal/đồ thị kế tại [git switch feature/rounding] dưới nhãn 'Rebase chuỗi commit cục bộ lên base mới'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Trình diễn rebase an toàn trên nhánh local với state trước/sau.

**Script:**

Biết commit được phát lại rồi, chúng ta cần thấy thứ tự thao tác trên nhánh riêng.

**Purpose:**

Nối phép kiểm chứng 'Rebase: phát lại commit và thay đổi commit ID' sang 'Rebase chuỗi commit cục bộ lên base mới', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Trình diễn rebase an toàn trên nhánh local với state trước/sau.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `04:06–05:56`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git switch feature/rounding; git status -sb; git rebase main`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Ở demo, bảo đảm working tree sạch rồi checkout feature, không rebase main hoặc nhánh dùng chung. Sau đó rebase feature lên main mới; quan sát Git lần lượt áp dụng từng commit và ref feature cuối cùng chuyển đến tip mới. Nếu có commit rỗng hoặc nội dung đã có, Git có thể thông báo skip; hãy đọc log và nội dung để hiểu chứ đừng áp lệnh máy móc. Chỉ demo trên nhánh chưa publish.

**Purpose:**

Trình diễn rebase an toàn trên nhánh local với state trước/sau.

## Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:56–06:09`

**Visual:**

Giữ frame kết quả lệnh [git switch feature/rounding] với nhãn 'Rebase chuỗi commit cục bộ lên base mới'; khoanh chứng cứ đã xác nhận: Trình diễn rebase an toàn trên nhánh local với state trước/sau.. Mở cửa sổ terminal/đồ thị kế tại [git status] dưới nhãn 'Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Dạy continue/abort với kiểm tra sequencer, tránh phá dữ liệu.

**Script:**

Lệnh rebase chạy trên chuỗi commit; một conflict phải giải từng lượt, không được bỏ qua trạng thái.

**Purpose:**

Nối phép kiểm chứng 'Rebase chuỗi commit cục bộ lên base mới' sang 'Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Dạy continue/abort với kiểm tra sequencer, tránh phá dữ liệu.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `06:09–07:59`

**Visual:**

Hai clone hoặc bản sao repo demo ĐỘC LẬP cùng gặp rebase conflict: nhánh A sửa tệp, stage, xác minh `git status`, rồi chạy `git rebase --continue`; nhánh B giữ xung đột và riêng rẽ chạy `git rebase --abort`. So sánh commit ID trước/sau trên từng đồ thị; KHÔNG gọi continue và abort nối tiếp trong cùng một phiên rebase, và không rewrite nhánh đã chia sẻ.

**Script:**

Hai commit được phát lại có thể va vào sửa đổi ở main, khiến rebase dừng với conflict. Trước khi chọn continue, đọc status, mở file, giải quyết theo đúng hành vi, stage kết quả, rồi mới rebase --continue. Trong bản thử riêng, dùng rebase --abort để trở về trạng thái trước khi bắt đầu nếu có thể; đừng tùy tiện kết hợp reset --hard trong lúc không hiểu sequencer. Rebase có nhiều bước, khác một merge commit đơn lẻ.

**Purpose:**

Dạy continue/abort với kiểm tra sequencer, tránh phá dữ liệu.

## Amend commit cục bộ và hệ quả tới lịch sử

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:59–08:12`

**Visual:**

Giữ frame kết quả lệnh [git status] với nhãn 'Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase'; khoanh chứng cứ đã xác nhận: Dạy continue/abort với kiểm tra sequencer, tránh phá dữ liệu.. Mở cửa sổ terminal/đồ thị kế tại [git commit --amend -m "Document corrected rounding"] dưới nhãn 'Amend commit cục bộ và hệ quả tới lịch sử'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Thấy amend thay danh tính commit và ranh giới commit chưa chia sẻ.

**Script:**

Rebase có thể viết lại nhiều commit; amend là tình huống tương tự ở commit cuối.

**Purpose:**

Nối phép kiểm chứng 'Xử lý xung đột và trạng thái tiếp tục hoặc hủy rebase' sang 'Amend commit cục bộ và hệ quả tới lịch sử', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Thấy amend thay danh tính commit và ranh giới commit chưa chia sẻ.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `08:12–10:02`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git commit --amend -m "Document corrected rounding"; git log -2 --format='%h %s'`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Một commit local vừa tạo có thể sai message hoặc thiếu một tệp cần thiết. Sau khi kiểm tra staged diff, amend thay commit HEAD bằng commit mới; log hiển thị ID khác dù ý định sửa đổi chỉ một phần nhỏ. Vì commit là đối tượng bất biến, đây là việc tạo phiên bản lịch sử mới, không phải sửa tại chỗ ID cũ. Không amend commit đã chia sẻ nếu chưa có thỏa thuận rõ với người khác.

**Purpose:**

Thấy amend thay danh tính commit và ranh giới commit chưa chia sẻ.

## Rủi ro viết lại commit đã được chia sẻ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:02–10:15`

**Visual:**

Giữ frame kết quả lệnh [git commit --amend -m "Document corrected rounding"] với nhãn 'Amend commit cục bộ và hệ quả tới lịch sử'; khoanh chứng cứ đã xác nhận: Thấy amend thay danh tính commit và ranh giới commit chưa chia sẻ.. Mở cửa sổ terminal/đồ thị kế tại [git branch -vv] dưới nhãn 'Rủi ro viết lại commit đã được chia sẻ'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Giải thích rủi ro ID khác làm lệch cơ sở làm việc của đồng đội.

**Script:**

Amend dễ dùng trên commit cá nhân, nhưng hậu quả khác hẳn khi người khác đã lấy lịch sử.

**Purpose:**

Nối phép kiểm chứng 'Amend commit cục bộ và hệ quả tới lịch sử' sang 'Rủi ro viết lại commit đã được chia sẻ', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Giải thích rủi ro ID khác làm lệch cơ sở làm việc của đồng đội.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `10:15–12:05`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git branch -vv; git log --graph --oneline --all; git status -sb`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Hãy dựng tình huống hai người đã fetch commit feature cũ. Nếu tác giả rebase rồi force-push, ID lịch sử được người khác dùng làm base không còn trùng, dẫn đến diverged hoặc commit trùng nội dung. Đó là lý do nguyên tắc mặc định: tránh rewrite lịch sử đã chia sẻ. Nếu một đội thật sự có quy trình chỉnh lịch sử đã publish, phải phối hợp và đánh giá --force-with-lease, chứ không coi đây là nút sửa lỗi đơn giản.

**Purpose:**

Giải thích rủi ro ID khác làm lệch cơ sở làm việc của đồng đội.

## Bằng chứng thay đổi commit graph và nội dung sau rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:05–12:18`

**Visual:**

Giữ frame kết quả lệnh [git branch -vv] với nhãn 'Rủi ro viết lại commit đã được chia sẻ'; khoanh chứng cứ đã xác nhận: Giải thích rủi ro ID khác làm lệch cơ sở làm việc của đồng đội.. Mở cửa sổ terminal/đồ thị kế tại [git log --graph --oneline --decorate --all] dưới nhãn 'Bằng chứng thay đổi commit graph và nội dung sau rebase'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Kết thúc với so sánh cấu trúc và kiểm chứng thay đổi thực.

**Script:**

Sau khi thay ID, phải xác minh cả tính tương đương của patch lẫn nội dung cuối.

**Purpose:**

Nối phép kiểm chứng 'Rủi ro viết lại commit đã được chia sẻ' sang 'Bằng chứng thay đổi commit graph và nội dung sau rebase', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Kết thúc với so sánh cấu trúc và kiểm chứng thay đổi thực.

### Scene 1 — Đồ thị, patch và điểm dừng an toàn

**Time:** `12:18–14:08`

**Visual:**

Chỉ dùng nhánh feature trong clone thử nghiệm; hiển thị terminal và các commit cũ mới, thao tác `git log --graph --oneline --decorate --all; git range-diff main old-feature feature/rounding`. Gắn cảnh báo nếu ID đã publish, dừng khi status chưa rõ.

**Script:**

Ở khung trái là graph trước rebase, bên phải là graph sau; ta đánh dấu cặp commit đại diện cùng ý định thay đổi nhưng ID khác. Dùng range-diff khi có các mốc ref phù hợp để so sánh chuỗi patch trước/sau, rồi kiểm tra diff cuối của nội dung trên feature. Đừng chỉ thấy graph thẳng đẹp mà kết luận rebase không làm sai logic; cần kiểm tra patch và tests. Chương sau sẽ dùng reflog để nhìn những vị trí ref trước đó.

**Purpose:**

Kết thúc với so sánh cấu trúc và kiểm chứng thay đổi thực.
