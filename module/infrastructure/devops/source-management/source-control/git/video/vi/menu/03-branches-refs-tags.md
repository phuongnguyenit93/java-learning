---
video:
  url: ""
---

# Nhánh, HEAD, tham chiếu và tag

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

## Đồ thị commit và khái niệm tham chiếu Git

<!-- VIDEO_SECTION -->

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `00:00–01:33`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git log --oneline --graph --all --decorate`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Mở đồ thị lịch sử nhỏ: các điểm là commit, cạnh chỉ quan hệ parent, còn tên main và feature là nhãn tham chiếu tới điểm cụ thể. Nếu ta thêm commit ở một nhánh, đồ thị tăng thêm nút và nhãn nhánh có thể dịch chuyển. Đừng tưởng branch là thư mục sao chép hoàn chỉnh của dự án; Git chỉ cần tham chiếu tới chuỗi snapshot có chung phần lịch sử.

**Purpose:**

Liên hệ nhánh với ref trỏ commit chứ không nhân bản thư mục.

## Nhánh cục bộ và HEAD trong mô hình tham chiếu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:33–01:47`

**Visual:**

Giữ frame kết quả lệnh [git log --oneline --graph --all --decorate] với nhãn 'Đồ thị commit và khái niệm tham chiếu Git'; khoanh chứng cứ đã xác nhận: Liên hệ nhánh với ref trỏ commit chứ không nhân bản thư mục.. Mở cửa sổ terminal/đồ thị kế tại [git symbolic-ref --short HEAD] dưới nhãn 'Nhánh cục bộ và HEAD trong mô hình tham chiếu'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh HEAD và tên nhánh có vai trò khác nhau.

**Script:**

Nhánh là tham chiếu, nhưng làm thế nào chúng ta tạo và kiểm tra một đường phát triển mới?

**Purpose:**

Nối phép kiểm chứng 'Đồ thị commit và khái niệm tham chiếu Git' sang 'Nhánh cục bộ và HEAD trong mô hình tham chiếu', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh HEAD và tên nhánh có vai trò khác nhau.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `01:47–03:20`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git symbolic-ref --short HEAD; git branch -vv; git rev-parse HEAD`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Trên sơ đồ, HEAD đang tham chiếu nhánh được checkout, còn nhánh trỏ tới commit. Sau một commit mới, nhánh hiện tại dịch chuyển; những nhánh khác chưa tự thay đổi. Dùng symbolic-ref để nhìn nhánh khi HEAD đang attached, và rev-parse để kiểm tra ID commit đích. Nếu symbolic-ref báo lỗi khi detached, đó là bằng chứng trạng thái chứ không phải lệnh bị hỏng ngẫu nhiên.

**Purpose:**

Chứng minh HEAD và tên nhánh có vai trò khác nhau.

## Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:34`

**Visual:**

Giữ frame kết quả lệnh [git symbolic-ref --short HEAD] với nhãn 'Nhánh cục bộ và HEAD trong mô hình tham chiếu'; khoanh chứng cứ đã xác nhận: Chứng minh HEAD và tên nhánh có vai trò khác nhau.. Mở cửa sổ terminal/đồ thị kế tại [main] dưới nhãn 'Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Cho người học thấy vòng đời tên nhánh và điều kiện xóa an toàn.

**Script:**

Khi có nhiều nhánh, thao tác chuyển nhánh phải tôn trọng những chỉnh sửa chưa ghi.

**Purpose:**

Nối phép kiểm chứng 'Nhánh cục bộ và HEAD trong mô hình tham chiếu' sang 'Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Cho người học thấy vòng đời tên nhánh và điều kiện xóa an toàn.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `03:34–05:07`

**Visual:**

Trên kho demo đã có nhánh `main` và commit baseline, tạo `feature/rounding` bằng `git switch -c feature/rounding`, ghi một commit thử với danh tính chỉ trong repo rồi xem `git branch -vv`. Để minh họa xóa an toàn, TRƯỚC TIÊN chạy `git switch main; git merge --ff-only feature/rounding`, xác nhận main chứa commit, SAU ĐÓ `git branch -d feature/rounding`. Giữ graph trước/sau và không dùng `-D` để vượt bảo vệ.

**Script:**

Tạo nhánh feature trên kho thử nghiệm, thêm một commit nhỏ và quan sát main vẫn trỏ commit cũ. Để xóa nhánh, hãy quay về main trước và chỉ dùng branch -d sau khi đã hợp nhất công việc cần giữ; Git có thể từ chối xóa nhánh chưa merge. Không dùng -D như phản xạ để vượt cảnh báo. Đây là thao tác với tên tham chiếu, không phải xoá ngay các snapshot đã được tham chiếu hợp lệ.

**Purpose:**

Cho người học thấy vòng đời tên nhánh và điều kiện xóa an toàn.

## Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:07–05:21`

**Visual:**

Giữ frame kết quả lệnh [main] với nhãn 'Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ'; khoanh chứng cứ đã xác nhận: Cho người học thấy vòng đời tên nhánh và điều kiện xóa an toàn.. Mở cửa sổ terminal/đồ thị kế tại [git status -sb] dưới nhãn 'Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Quan sát từ chối chuyển nhánh và bảo vệ working tree.

**Script:**

Chuyển nhánh an toàn quan trọng; còn nếu ta trỏ HEAD trực tiếp vào commit thì điều gì xảy ra?

**Purpose:**

Nối phép kiểm chứng 'Vòng thao tác tạo, kiểm tra và xóa nhánh cục bộ' sang 'Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Quan sát từ chối chuyển nhánh và bảo vệ working tree.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `05:21–06:54`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git status -sb; git switch main; git diff`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Hãy sửa README nhưng chưa commit, rồi thử chuyển sang nhánh có nội dung tệp khác. Git có thể từ chối nếu chuyển sẽ ghi đè công việc, nhưng đôi khi chuyển được và mang theo chỉnh sửa. Vì thế không được dạy rằng switch luôn lưu hay luôn xoá thay đổi. Chúng ta xem status trước, sau đó chọn commit, stash hoặc xử lý phần chưa ghi, thay vì ép switch bằng tùy chọn huỷ dữ liệu.

**Purpose:**

Quan sát từ chối chuyển nhánh và bảo vệ working tree.

## Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:54–07:08`

**Visual:**

Giữ frame kết quả lệnh [git status -sb] với nhãn 'Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn'; khoanh chứng cứ đã xác nhận: Quan sát từ chối chuyển nhánh và bảo vệ working tree.. Mở cửa sổ terminal/đồ thị kế tại [git switch --detach HEAD~1] dưới nhãn 'Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Nhận diện detached HEAD và cách giữ công việc muốn bảo toàn.

**Script:**

Khi HEAD không gắn nhánh, ta càng cần phân biệt tham chiếu có thể chuyển và điểm đánh dấu.

**Purpose:**

Nối phép kiểm chứng 'Chuyển nhánh với thay đổi chưa commit và các giới hạn an toàn' sang 'Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Nhận diện detached HEAD và cách giữ công việc muốn bảo toàn.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `07:08–08:41`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git switch --detach HEAD~1; git status -sb; git switch main`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Chuyển tới commit cũ bằng detached HEAD trong kho demo. Ta có thể đọc, thử nghiệm và thậm chí commit, nhưng commit mới không được một nhánh đặt tên tự động bảo vệ. Trước khi rời nếu muốn giữ kết quả, hãy tạo nhánh tại commit đó. Dòng trạng thái HEAD detached giúp nhắc rằng đây là vị trí lịch sử, không phải lỗi repository hay một nhánh tên detached.

**Purpose:**

Nhận diện detached HEAD và cách giữ công việc muốn bảo toàn.

## Tag cố định và nhánh di chuyển theo commit

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:41–08:55`

**Visual:**

Giữ frame kết quả lệnh [git switch --detach HEAD~1] với nhãn 'Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh'; khoanh chứng cứ đã xác nhận: Nhận diện detached HEAD và cách giữ công việc muốn bảo toàn.. Mở cửa sổ terminal/đồ thị kế tại [git tag -a v1.0 -m "First baseline"] dưới nhãn 'Tag cố định và nhánh di chuyển theo commit'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Phân biệt tag mốc cố định với nhánh phát triển có thể tiến.

**Script:**

Đã có branch và tag; hãy đặt chúng cùng HEAD lên một đồ thị để kiểm chứng.

**Purpose:**

Nối phép kiểm chứng 'Detached HEAD: trạng thái và rủi ro tham chiếu không gắn nhánh' sang 'Tag cố định và nhánh di chuyển theo commit', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Phân biệt tag mốc cố định với nhánh phát triển có thể tiến.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `08:55–10:28`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git tag -a v1.0 -m "First baseline"; git show v1.0 --no-patch; git branch -vv`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Một nhánh di chuyển khi có commit mới, còn tag thường dùng để đánh dấu mốc đã phát hành. Tạo annotated tag trong demo, ghi chú và đọc nó bằng show. Không hiểu tag là nhánh cho tác giả tiếp tục phát triển, và cũng đừng nhầm tag với GitHub Release có mô tả/tệp riêng. Nếu chuyển tag trên nơi đã chia sẻ, người khác có thể giữ tham chiếu cũ; vì vậy cần quy trình phát hành rõ.

**Purpose:**

Phân biệt tag mốc cố định với nhánh phát triển có thể tiến.

## Bằng chứng vị trí HEAD, nhánh và tag trong lịch sử

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:28–10:42`

**Visual:**

Giữ frame kết quả lệnh [git tag -a v1.0 -m "First baseline"] với nhãn 'Tag cố định và nhánh di chuyển theo commit'; khoanh chứng cứ đã xác nhận: Phân biệt tag mốc cố định với nhánh phát triển có thể tiến.. Mở cửa sổ terminal/đồ thị kế tại [git log --graph --oneline --decorate --all] dưới nhãn 'Bằng chứng vị trí HEAD, nhánh và tag trong lịch sử'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Khép lại bằng kiểm chứng nhiều ref trên cùng commit graph.

**Script:**

Trước khi bước vào merge, chúng ta cần chứng minh được mỗi nhánh đang thực sự trỏ ở đâu.

**Purpose:**

Nối phép kiểm chứng 'Tag cố định và nhánh di chuyển theo commit' sang 'Bằng chứng vị trí HEAD, nhánh và tag trong lịch sử', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Khép lại bằng kiểm chứng nhiều ref trên cùng commit graph.

### Scene 1 — Đọc đồ thị và thao tác thử

**Time:** `10:42–12:15`

**Visual:**

Trên kho demo tách biệt, hiển thị terminal bên đồ thị có mũi tên main/feature/HEAD/tag; thực thi có điều kiện `git log --graph --oneline --decorate --all; git show-ref --heads --tags`. Khoanh dòng ref và commit ID trước/sau, dừng nếu worktree không sạch.

**Script:**

Kết thúc bằng đồ thị có hai nhánh và một tag. Đọc show-ref để lấy ID của ref, rồi đối chiếu log --decorate để biết ref nào nằm ở nút nào. HEAD attached hiện trên nhánh đang chọn; tag vẫn ở mốc của nó khi feature tiến thêm commit. Hãy tự dự đoán ID trước khi chạy lệnh: nếu đoán sai, quay về mô hình 'tên tham chiếu trỏ commit' chứ đừng học thuộc đầu ra.

**Purpose:**

Khép lại bằng kiểm chứng nhiều ref trên cùng commit graph.
