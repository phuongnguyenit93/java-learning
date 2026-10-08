---
video:
  url: ""
---

# Nền tảng Git: repository, trạng thái và snapshot

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

## Git: khái niệm và vai trò kiểm soát phiên bản phân tán

<!-- VIDEO_SECTION -->

### Scene 1 — Quan sát trực tiếp: Git: khái niệm và vai trò kiểm soát phiên bản phân tán

**Time:** `00:00–01:36`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git init; git status -sb`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Hãy nhìn hai thư mục chứa cùng một tệp. Thư mục đầu chỉ có bản hiện tại; thư mục thứ hai được khởi tạo bằng Git và có thể ghi lại các mốc thay đổi. Git chạy ngay trên máy của chúng ta, không cần đợi GitHub cấp một tài khoản. Vì thế 'phân tán' trước hết có nghĩa mỗi bản sao hợp lệ có khả năng giữ lịch sử và tạo commit, không chỉ là chia sẻ file.

Trong lộ trình, ta sẽ quan sát working tree/index/repository và commit trước, sau đó nhánh, merge, remote, rebase, hoàn tác và cuối cùng nối các thao tác thành một vòng thực hành. Việc phê duyệt PR sẽ thuộc bài về nền tảng cộng tác.

**Purpose:**

Phân biệt Git phân tán với nơi lưu file hay nền tảng hosting.

## Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:36–01:49`

**Visual:**

Giữ frame kết quả lệnh [git init] với nhãn 'Git: khái niệm và vai trò kiểm soát phiên bản phân tán'; khoanh chứng cứ đã xác nhận: Phân biệt Git phân tán với nơi lưu file hay nền tảng hosting.. Mở cửa sổ terminal/đồ thị kế tại [git log --oneline] dưới nhãn 'Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chỉ ra bằng chứng commit lưu trạng thái dự án theo mốc.

**Script:**

Ảnh chụp cần một nơi cất và một chỗ chúng ta đang sửa; hãy tách hai thứ đó.

**Purpose:**

Nối phép kiểm chứng 'Git: khái niệm và vai trò kiểm soát phiên bản phân tán' sang 'Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chỉ ra bằng chứng commit lưu trạng thái dự án theo mốc.

### Scene 1 — Quan sát trực tiếp: Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản

**Time:** `01:49–03:25`

**Visual:**

Trên repository thử nghiệm riêng sau `git init`, tạo `app.txt` cùng tệp `stable.txt`, cấu hình danh tính Git chỉ trong kho thử và commit cả hai tệp để có snapshot đầu. Sau đó chỉ sửa `app.txt`, stage rồi tạo commit thứ hai, giữ `stable.txt` nguyên trạng. Chỉ khi đã có HEAD hợp lệ mới chạy `git log --oneline` và `git show --stat HEAD`; đối chiếu hai snapshot và phần nội dung không đổi được tái sử dụng. Không thao tác trong repository làm việc thực.

**Script:**

Thử tạo hai commit khi một tệp không đổi và tệp còn lại được sửa. Git trình bày mỗi commit như một ảnh chụp trạng thái có tham chiếu đến phiên bản của cây tệp; các đối tượng không đổi có thể được tái sử dụng. Hãy đọc kết quả show để nhận ra commit mô tả phiên bản và lịch sử, không phải bản ghi của từng phím đã gõ.

**Purpose:**

Chỉ ra bằng chứng commit lưu trạng thái dự án theo mốc.

## Git repository và working tree: khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:25–03:38`

**Visual:**

Giữ frame kết quả lệnh [git log --oneline] với nhãn 'Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản'; khoanh chứng cứ đã xác nhận: Chỉ ra bằng chứng commit lưu trạng thái dự án theo mốc.. Mở cửa sổ terminal/đồ thị kế tại [git rev-parse --show-toplevel] dưới nhãn 'Git repository và working tree: khái niệm và phạm vi'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Nhận ra working tree không đồng nhất với cơ sở dữ liệu Git.

**Script:**

Sửa tệp không tự thành commit. Vậy Git đặt phiên bản định ghi tiếp theo ở đâu?

**Purpose:**

Nối phép kiểm chứng 'Bản chụp trạng thái (snapshot) và lý do Git lưu lịch sử theo phiên bản' sang 'Git repository và working tree: khái niệm và phạm vi', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Nhận ra working tree không đồng nhất với cơ sở dữ liệu Git.

### Scene 1 — Quan sát trực tiếp: Git repository và working tree: khái niệm và phạm vi

**Time:** `03:38–05:14`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git rev-parse --show-toplevel; git status --short`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Màn hình bên trái là tệp đang được sửa trong working tree, còn vùng .git là nơi repository ghi đối tượng, refs và lịch sử. Thử sửa README rồi chạy status: tệp đổi ngay nhưng lịch sử commit chưa tự tăng. Nhận diện đường dẫn gốc repository bằng rev-parse; chính sự tách biệt này giúp Git biết nội dung đang làm khác với trạng thái đã ghi nhận.

**Purpose:**

Nhận ra working tree không đồng nhất với cơ sở dữ liệu Git.

## Index (staging area) trong mô hình ba vùng của Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:27`

**Visual:**

Giữ frame kết quả lệnh [git rev-parse --show-toplevel] với nhãn 'Git repository và working tree: khái niệm và phạm vi'; khoanh chứng cứ đã xác nhận: Nhận ra working tree không đồng nhất với cơ sở dữ liệu Git.. Mở cửa sổ terminal/đồ thị kế tại [git add README.md] dưới nhãn 'Index (staging area) trong mô hình ba vùng của Git'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Đối chiếu rõ ba vùng bằng hai bản diff khác nhau.

**Script:**

Khi index quyết định ảnh chụp kế tiếp, commit liên kết ảnh chụp ấy với lịch sử bằng cách nào?

**Purpose:**

Nối phép kiểm chứng 'Git repository và working tree: khái niệm và phạm vi' sang 'Index (staging area) trong mô hình ba vùng của Git', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Đối chiếu rõ ba vùng bằng hai bản diff khác nhau.

### Scene 1 — Quan sát trực tiếp: Index (staging area) trong mô hình ba vùng của Git

**Time:** `05:27–07:03`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git add README.md; git diff; git diff --cached`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Giữa working tree và HEAD có một nơi rất quan trọng: index, còn gọi là staging area. Hãy sửa README, add, rồi sửa tiếp một dòng nữa. Khi chạy diff thông thường ta thấy phần sửa sau, còn diff --cached hiển thị bản đã chọn để commit. Ba vùng vì vậy có thể chứa ba trạng thái khác nhau của cùng một tệp; commit sẽ lấy nội dung từ index.

**Purpose:**

Đối chiếu rõ ba vùng bằng hai bản diff khác nhau.

## Git objects, snapshots và quan hệ giữa các commit

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:16`

**Visual:**

Giữ frame kết quả lệnh [git add README.md] với nhãn 'Index (staging area) trong mô hình ba vùng của Git'; khoanh chứng cứ đã xác nhận: Đối chiếu rõ ba vùng bằng hai bản diff khác nhau.. Mở cửa sổ terminal/đồ thị kế tại [git cat-file -p HEAD] dưới nhãn 'Git objects, snapshots và quan hệ giữa các commit'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Liên hệ tree và parent với lịch sử commit thực.

**Script:**

Các đối tượng cho ta cấu trúc lịch sử; còn trạng thái một tệp chưa ghi nhận sẽ hiện ra thế nào?

**Purpose:**

Nối phép kiểm chứng 'Index (staging area) trong mô hình ba vùng của Git' sang 'Git objects, snapshots và quan hệ giữa các commit', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Liên hệ tree và parent với lịch sử commit thực.

### Scene 1 — Quan sát trực tiếp: Git objects, snapshots và quan hệ giữa các commit

**Time:** `07:16–08:52`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git cat-file -p HEAD; git ls-tree HEAD`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Phóng to output cat-file của một commit mẫu: ta thấy tree, parent khi có và thông tin người ghi nhận. Tiếp đến dùng ls-tree để thấy snapshot được chỉ đến, chứ không phải một danh sách lệnh sửa file. Đường parent kết nối các mốc thành đồ thị. Đọc object chỉ để hiểu bằng chứng; chúng ta không cần can thiệp thủ công vào thư mục .git.

**Purpose:**

Liên hệ tree và parent với lịch sử commit thực.

## Trạng thái tracked, untracked, modified, staged và committed

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:52–09:05`

**Visual:**

Giữ frame kết quả lệnh [git cat-file -p HEAD] với nhãn 'Git objects, snapshots và quan hệ giữa các commit'; khoanh chứng cứ đã xác nhận: Liên hệ tree và parent với lịch sử commit thực.. Mở cửa sổ terminal/đồ thị kế tại [git status --short] dưới nhãn 'Trạng thái tracked, untracked, modified, staged và committed'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Đọc trạng thái tracked/untracked/staged bằng output thực.

**Script:**

Chúng ta đã biết trạng thái tệp; giờ cần phân biệt việc mở kho mới và lấy kho có sẵn.

**Purpose:**

Nối phép kiểm chứng 'Git objects, snapshots và quan hệ giữa các commit' sang 'Trạng thái tracked, untracked, modified, staged và committed', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Đọc trạng thái tracked/untracked/staged bằng output thực.

### Scene 1 — Quan sát trực tiếp: Trạng thái tracked, untracked, modified, staged và committed

**Time:** `09:05–10:41`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git status --short; git add notes.txt; git status -sb`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Tạo notes.txt lần đầu, status hiển thị dấu hỏi vì đây là untracked. Sau khi add, nó được đưa vào index; sửa lần nữa thì status có thể cho thấy cả staged lẫn unstaged. Một tệp đã commit vẫn có thể bị sửa ở working tree. Đừng gọi tất cả những trạng thái ấy là 'đã lưu': hãy nhìn đúng cột trạng thái và vị trí của nội dung.

**Purpose:**

Đọc trạng thái tracked/untracked/staged bằng output thực.

## Khởi tạo repository và sao chép lịch sử bằng clone

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:41–10:54`

**Visual:**

Giữ frame kết quả lệnh [git status --short] với nhãn 'Trạng thái tracked, untracked, modified, staged và committed'; khoanh chứng cứ đã xác nhận: Đọc trạng thái tracked/untracked/staged bằng output thực.. Mở cửa sổ terminal/đồ thị kế tại [git init scratch-demo] dưới nhãn 'Khởi tạo repository và sao chép lịch sử bằng clone'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Phân biệt kho mới với bản sao có lịch sử khi thao tác an toàn.

**Script:**

Có đủ các mảnh rồi, hãy kiểm tra một tệp đi từ lần sửa đầu tiên đến commit.

**Purpose:**

Nối phép kiểm chứng 'Trạng thái tracked, untracked, modified, staged và committed' sang 'Khởi tạo repository và sao chép lịch sử bằng clone', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Phân biệt kho mới với bản sao có lịch sử khi thao tác an toàn.

### Scene 1 — Quan sát trực tiếp: Khởi tạo repository và sao chép lịch sử bằng clone

**Time:** `10:54–12:30`

**Visual:**

Trong thư mục thực hành mới, chạy `git init scratch-demo`; trước khi clone, tạo lịch sử thật bằng `git -C scratch-demo -c user.name=Demo -c user.email=demo@example.invalid commit --allow-empty -m "Seed history"`; sau đó chạy `git clone ./scratch-demo scratch-copy` và `git -C scratch-copy log --oneline`. Chia đôi hai terminal, tô đúng cùng commit ID ở source/copy; không thao tác repository người dùng.

**Script:**

Trên màn hình chia đôi, init tạo repository mới với lịch sử chưa có commit, còn clone lấy repository và refs có sẵn từ một nguồn phù hợp. Với demo này dùng đường dẫn cục bộ để không phụ thuộc mạng; clone chỉ có ý nghĩa về lịch sử sau khi source đã có commit. Clone không phải lệnh 'tạo branch mới'. Sau mỗi thao tác hãy kiểm tra git status và git log trong đúng thư mục.

**Purpose:**

Phân biệt kho mới với bản sao có lịch sử khi thao tác an toàn.

## Tình huống theo dõi trạng thái tệp qua ba vùng của Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:30–12:43`

**Visual:**

Giữ frame kết quả lệnh [git init scratch-demo] với nhãn 'Khởi tạo repository và sao chép lịch sử bằng clone'; khoanh chứng cứ đã xác nhận: Phân biệt kho mới với bản sao có lịch sử khi thao tác an toàn.. Mở cửa sổ terminal/đồ thị kế tại [git status -sb] dưới nhãn 'Tình huống theo dõi trạng thái tệp qua ba vùng của Git'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh bằng trạng thái trước/sau thay vì mô tả thuộc lòng.

**Script:**

Một chuỗi câu lệnh ngắn đã kể được cả quá trình; giờ cần biết tra cứu lệnh và cảnh báo ở đâu.

**Purpose:**

Nối phép kiểm chứng 'Khởi tạo repository và sao chép lịch sử bằng clone' sang 'Tình huống theo dõi trạng thái tệp qua ba vùng của Git', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh bằng trạng thái trước/sau thay vì mô tả thuộc lòng.

### Scene 1 — Quan sát trực tiếp: Tình huống theo dõi trạng thái tệp qua ba vùng của Git

**Time:** `12:43–14:19`

**Visual:**

Trên repository thử nghiệm riêng, hiện lần lượt terminal và sơ đồ ba vùng/đồ thị phù hợp. Gõ/đọc `git status -sb; git diff; git add README.md; git diff --cached; git commit -m "Record baseline"`; dừng hình ở dòng đầu ra thay đổi và đối chiếu trạng thái trước/sau.

**Script:**

Giữ nguyên một tệp xuyên suốt: lúc vừa sửa, diff cho biết working tree khác index; lúc add, diff --cached cho biết index khác HEAD. Commit xong, chạy status trên repository thử nghiệm để xem trạng thái sạch nếu không còn thay đổi khác. Quan trọng là ta không phải đoán: mỗi lệnh trả lời một câu hỏi riêng và nếu sửa tiếp sau add, commit chưa bao gồm phần sửa thêm đó.

**Purpose:**

Chứng minh bằng trạng thái trước/sau thay vì mô tả thuộc lòng.

## Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức

<!-- VIDEO_SECTION -->

### Transition

**Time:** `14:19–14:32`

**Visual:**

Giữ frame kết quả lệnh [git status -sb] với nhãn 'Tình huống theo dõi trạng thái tệp qua ba vùng của Git'; khoanh chứng cứ đã xác nhận: Chứng minh bằng trạng thái trước/sau thay vì mô tả thuộc lòng.. Mở cửa sổ terminal/đồ thị kế tại [git help status] dưới nhãn 'Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Để lại quy trình tra cứu lệnh chính thức và nguyên tắc an toàn trước khi học staging sâu.

**Script:**
Chúng ta vừa chứng minh được tệp đi qua working tree, index và commit bằng output thật. Để tự kiểm chứng không phụ thuộc video này, cần biết chọn lệnh tra cứu và đọc cảnh báo trước khi thử.



**Purpose:**

Nối phép kiểm chứng 'Tình huống theo dõi trạng thái tệp qua ba vùng của Git' sang 'Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Để lại quy trình tra cứu lệnh chính thức và nguyên tắc an toàn trước khi học staging sâu.

### Scene 1 — Quan sát trực tiếp: Bảng tra cứu lệnh Git cơ bản và tài liệu tham khảo chính thức

**Time:** `14:32–16:08`

**Visual:**

Trên repository demo, chạy riêng `git help status`, `git status -h`, `git help restore`; mở https://git-scm.com/docs trong trình duyệt bên cạnh terminal. Khoanh vùng Usage, Options và cảnh báo thao tác hủy dữ liệu.

**Script:**

Chốt chương bằng một bảng ba cột: lệnh, câu hỏi cần trả lời và dấu vết quan sát. status hỏi 'điều gì thay đổi', diff hỏi 'khác ở đâu', add chọn nội dung, commit ghi snapshot, log đọc lịch sử. Mở git help status hoặc tài liệu git-scm.com/docs để tra tùy chọn; tuyệt đối không chạy reset --hard hay clean -fd như bước khám phá vô hại. Hãy đọc help trước, thử trên kho sao chép riêng và kiểm tra status sau mỗi thay đổi.

**Purpose:**

Để lại quy trình tra cứu lệnh chính thức và nguyên tắc an toàn trước khi học staging sâu.
