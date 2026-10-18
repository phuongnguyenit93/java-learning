---
video:
  url: ""
---

# Remote, nhánh theo dõi và đồng bộ lịch sử

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

## Remote repository: vai trò chia sẻ và trao đổi lịch sử Git

<!-- VIDEO_SECTION -->

### Scene 1 — Đối chiếu hai kho thật

**Time:** `00:00–01:41`

**Visual:**

Trong thư mục cha thử nghiệm mới, khởi tạo `local-demo` với nhánh `main` và ghi ít nhất một seed commit bằng danh tính demo chỉ thuộc kho này. Từ `local-demo`, tạo bare remote rỗng ở thư mục ngang cấp bằng `git init --bare ../remote-demo.git`; kiểm tra `git remote -v` còn trống. Chưa vẽ commit của server hoặc clone thứ hai vì chưa có bước publish. Không thao tác trên repository thật.

**Script:**

Để nhìn remote mà không cần tài khoản hosted, hãy dùng một bare repository đặt ở thư mục thử nghiệm. Local Git có lịch sử riêng, remote là một repository khác để trao đổi các đối tượng commit và ref. Remote không có nghĩa tự động có PR, reviewer hay quyền chính sách của GitHub. Hãy vẽ hai ô local/remote và theo dõi commit ID đi qua chúng, thay vì gọi mọi thứ trên Internet là 'đám mây'.

**Purpose:**

Chứng minh remote là repository Git, không phải một API PR.

## Cấu hình và kiểm tra remote như origin

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:41–01:53`

**Visual:**

Giữ frame kết quả lệnh [git init --bare ../remote-demo.git] với nhãn 'Remote repository: vai trò chia sẻ và trao đổi lịch sử Git'; khoanh chứng cứ đã xác nhận: Chứng minh remote là repository Git, không phải một API PR.. Mở cửa sổ terminal/đồ thị kế tại [git remote add origin ../remote-demo.git] dưới nhãn 'Cấu hình và kiểm tra remote như origin'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Đọc cấu hình remote mà không nhầm với đồng bộ dữ liệu.

**Script:**

Ta đã có repository từ xa, nhưng phải định vị được nó bằng tên và URL trước khi trao đổi.

**Purpose:**

Nối phép kiểm chứng 'Remote repository: vai trò chia sẻ và trao đổi lịch sử Git' sang 'Cấu hình và kiểm tra remote như origin', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Đọc cấu hình remote mà không nhầm với đồng bộ dữ liệu.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `01:53–03:34`

**Visual:**

Trong `local-demo` đã có commit, chạy `git remote add origin ../remote-demo.git; git remote -v; git remote get-url origin`; đóng băng màn hình: remote vẫn chưa có nhánh `main`. SAU ĐÓ publish rõ ràng bằng `git push -u origin main`, tiếp theo `git fetch origin`; đối chiếu `git ls-remote origin main` và `git show-ref refs/remotes/origin/main` để chứng minh nhánh thật và tracking ref đã tồn tại. Chỉ push/fetch mới trao đổi lịch sử, không phải remote add.

**Script:**

Origin chỉ là tên remote thường dùng, không phải máy chủ bắt buộc. Thêm remote path cục bộ và đọc lại URL bằng remote -v/get-url; bước ấy không sửa commit local hay tự mang lịch sử đến server. Ta sẽ thấy bare remote ban đầu trống, rồi chỉ có lệnh push riêng mới tạo `main` phía server. Fetch sau đó bảo đảm `origin/main` sẵn sàng cho những cảnh tiếp theo. Phải tách hành vi cấu hình remote và hành vi chuyển refs.

**Purpose:**

Đọc cấu hình remote mà không nhầm với đồng bộ dữ liệu.

## Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:34–03:46`

**Visual:**

Giữ frame kết quả lệnh [git remote add origin ../remote-demo.git] với nhãn 'Cấu hình và kiểm tra remote như origin'; khoanh chứng cứ đã xác nhận: Đọc cấu hình remote mà không nhầm với đồng bộ dữ liệu.. Mở cửa sổ terminal/đồ thị kế tại [git branch -avv] dưới nhãn 'Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Tách server branch, local branch và remote-tracking ref.

**Script:**

Đã biết địa chỉ remote rồi, giờ cần ba loại tham chiếu khác nhau để tránh đọc nhầm trạng thái.

**Purpose:**

Nối phép kiểm chứng 'Cấu hình và kiểm tra remote như origin' sang 'Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Tách server branch, local branch và remote-tracking ref.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `03:46–05:27`

**Visual:**

Màn hình chia đôi hai bản Git cục bộ độc lập cùng bare remote thử nghiệm; gõ/đọc `git branch -avv; git show-ref; git ls-remote origin`. So sánh commit IDs/refs, cảnh báo không force lên server thật.

**Script:**

Trên màn hình ba cột: nhánh main local, nhánh main thực sự trong remote và origin/main là remote-tracking ref trong clone. Sau fetch, origin/main phản ánh thông tin remote tại lần cập nhật gần nhất; nó không phải 'con trỏ sống' luôn đổi tức thì cùng server. So sánh ID của ls-remote origin với show-ref local để nhận ra lúc nào dữ liệu theo dõi đã cũ.

**Purpose:**

Tách server branch, local branch và remote-tracking ref.

## Quan hệ upstream của nhánh cục bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:27–05:39`

**Visual:**

Giữ frame kết quả lệnh [git branch -avv] với nhãn 'Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references'; khoanh chứng cứ đã xác nhận: Tách server branch, local branch và remote-tracking ref.. Mở cửa sổ terminal/đồ thị kế tại [git branch --set-upstream-to=origin/main main] dưới nhãn 'Quan hệ upstream của nhánh cục bộ'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Giải thích upstream là quan hệ theo dõi không phải quyền remote.

**Script:**

Ba loại ref đã rõ; hãy gắn quan hệ upstream để hiểu các thông báo ahead/behind.

**Purpose:**

Nối phép kiểm chứng 'Nhánh cục bộ, nhánh trên máy chủ và remote-tracking references' sang 'Quan hệ upstream của nhánh cục bộ', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Giải thích upstream là quan hệ theo dõi không phải quyền remote.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `05:39–07:20`

**Visual:**

Màn hình chia đôi hai bản Git cục bộ độc lập cùng bare remote thử nghiệm; gõ/đọc `git branch --set-upstream-to=origin/main main; git branch -vv`. So sánh commit IDs/refs, cảnh báo không force lên server thật.

**Script:**

Cùng một nhánh local có thể được cấu hình upstream để Git biết nơi so sánh ahead/behind và nơi pull thường lấy thông tin. Hãy chỉ lệnh set-upstream sau khi origin/main đã tồn tại từ fetch hoặc push phù hợp; nếu chưa tồn tại, dừng và kiểm tra refs thay vì cố đoán. Upstream là quan hệ cấu hình, không đồng nghĩa hai nhánh đang có cùng nội dung hay ta được quyền push.

**Purpose:**

Giải thích upstream là quan hệ theo dõi không phải quyền remote.

## Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:20–07:32`

**Visual:**

Giữ frame kết quả lệnh [git branch --set-upstream-to=origin/main main] với nhãn 'Quan hệ upstream của nhánh cục bộ'; khoanh chứng cứ đã xác nhận: Giải thích upstream là quan hệ theo dõi không phải quyền remote.. Mở cửa sổ terminal/đồ thị kế tại [git fetch origin] dưới nhãn 'Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Quan sát fetch không nhập local branch, pull có bước tích hợp.

**Script:**

Upstream cho ta mốc so sánh; ta cần hiểu fetch và pull khác nhau ở phần lịch sử local.

**Purpose:**

Nối phép kiểm chứng 'Quan hệ upstream của nhánh cục bộ' sang 'Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Quan sát fetch không nhập local branch, pull có bước tích hợp.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `07:32–09:13`

**Visual:**

Màn hình chia đôi hai bản Git cục bộ độc lập cùng bare remote thử nghiệm; gõ/đọc `git fetch origin; git log --oneline --graph --all; git pull --ff-only`. So sánh commit IDs/refs, cảnh báo không force lên server thật.

**Script:**

Trước hết hãy fetch rồi đọc origin/main và main có khác nhau không. Fetch lấy đối tượng và cập nhật remote-tracking refs, nhưng không tự sửa working tree hay nhập commit vào nhánh đang làm. Pull thường gồm fetch rồi tích hợp theo cấu hình merge/rebase; ở demo an toàn, dùng --ff-only để từ chối nếu không thể tiến thẳng. Đừng dùng pull như một phép màu 'luôn cập nhật sạch' khi đã phân kỳ.

**Purpose:**

Quan sát fetch không nhập local branch, pull có bước tích hợp.

## git push, cập nhật nhánh từ xa và trường hợp bị từ chối

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:13–09:25`

**Visual:**

Giữ frame kết quả lệnh [git fetch origin] với nhãn 'Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử'; khoanh chứng cứ đã xác nhận: Quan sát fetch không nhập local branch, pull có bước tích hợp.. Mở cửa sổ terminal/đồ thị kế tại [git push -u origin feature/rounding] dưới nhãn 'git push, cập nhật nhánh từ xa và trường hợp bị từ chối'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Nhận diện push thành công so với non-fast-forward rejection.

**Script:**

Đã fetch thì biết những gì đang ở remote; bước push phải tôn trọng lịch sử nơi nhận.

**Purpose:**

Nối phép kiểm chứng 'Khác biệt giữa git fetch và git pull trong việc đồng bộ lịch sử' sang 'git push, cập nhật nhánh từ xa và trường hợp bị từ chối', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Nhận diện push thành công so với non-fast-forward rejection.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `09:25–11:06`

**Visual:**

Từ `local-demo/main` đã được seed, tạo nhánh `feature/rounding` và commit một sửa đổi thật trên tệp thực hành; chạy `git push -u origin feature/rounding; git status -sb; git ls-remote origin feature/rounding`. So commit ID của nhánh local và server. Trường hợp non-fast-forward là ví dụ KHÁC, khi clone khác đã cập nhật cùng nhánh; không giả vờ lần push đầu tiên bị từ chối. Không dùng force-push tới kho thật.

**Script:**

Đẩy một nhánh thử nghiệm lên bare remote rồi xác minh cả local feature và nhánh thật ở remote. Push đề xuất cập nhật ref phía remote; nếu remote đã có commit không nằm trong nhánh đang đẩy, server thường từ chối non-fast-forward. Không dùng --force để 'sửa' sự từ chối đó: cần xem lịch sử của người khác trước. Cấu hình upstream bằng -u chỉ giúp lần sau thuận tiện, không bỏ qua kiểm soát máy chủ.

**Purpose:**

Nhận diện push thành công so với non-fast-forward rejection.

## Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:06–11:18`

**Visual:**

Giữ frame kết quả lệnh [git push -u origin feature/rounding] với nhãn 'git push, cập nhật nhánh từ xa và trường hợp bị từ chối'; khoanh chứng cứ đã xác nhận: Nhận diện push thành công so với non-fast-forward rejection.. Mở cửa sổ terminal/đồ thị kế tại [git fetch origin] dưới nhãn 'Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Dạy chẩn đoán divergence trước khi retry push.

**Script:**

Push có thể bị từ chối vì lịch sử phân kỳ; điều đúng cần làm là quan sát rồi hòa giải.

**Purpose:**

Nối phép kiểm chứng 'git push, cập nhật nhánh từ xa và trường hợp bị từ chối' sang 'Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Dạy chẩn đoán divergence trước khi retry push.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `11:18–12:59`

**Visual:**

Tạo `clone-B` từ bare remote đã có nhánh main, checkout `main`, commit một thay đổi ở dòng KHÁC bằng danh tính demo và push lên `origin/main`. Trong lúc ấy `local-demo` ở nhánh `feature/rounding` đã có commit riêng. Trở lại `local-demo`, chạy `git fetch origin; git log --graph --oneline --decorate --all; git merge origin/main`; đóng băng hai tip đã phân kỳ rồi mới merge. Nếu chủ động làm conflict cùng dòng thì phải xử lý trước khi báo merge xong. Tất cả trong vùng demo riêng.

**Script:**

Giả sử hai clone độc lập đều thêm commit rồi clone A push trước. Ở clone B, fetch trước và đọc hai đầu nhánh; nếu phân kỳ, lựa chọn merge hay rebase phụ thuộc chính sách và việc commit nào đã chia sẻ. Trong demo này dùng merge để giữ lịch sử rõ ràng, xử lý conflict nếu có, rồi mới push. Tuyệt đối không xóa commit từ xa bằng force push để nhanh chóng hết lỗi.

**Purpose:**

Dạy chẩn đoán divergence trước khi retry push.

## Bằng chứng tham chiếu trước và sau đồng bộ lịch sử

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:59–13:11`

**Visual:**

Giữ frame kết quả lệnh [git fetch origin] với nhãn 'Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi'; khoanh chứng cứ đã xác nhận: Dạy chẩn đoán divergence trước khi retry push.. Mở cửa sổ terminal/đồ thị kế tại [git branch -avv] dưới nhãn 'Bằng chứng tham chiếu trước và sau đồng bộ lịch sử'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Khép chương bằng đối chiếu server refs và remote-tracking refs.

**Script:**

Sau khi hòa giải, cần xác nhận cả hai repository thay vì chỉ xem status của một máy.

**Purpose:**

Nối phép kiểm chứng 'Hòa giải lịch sử phân kỳ trước khi chia sẻ thay đổi' sang 'Bằng chứng tham chiếu trước và sau đồng bộ lịch sử', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Khép chương bằng đối chiếu server refs và remote-tracking refs.

### Scene 1 — Đối chiếu hai kho thật

**Time:** `13:11–14:52`

**Visual:**

Màn hình chia đôi hai bản Git cục bộ độc lập cùng bare remote thử nghiệm; gõ/đọc `git branch -avv; git ls-remote origin; git log --graph --oneline --all`. So sánh commit IDs/refs, cảnh báo không force lên server thật.

**Script:**

Kết thúc bằng hai màn hình đặt cạnh nhau: local refs sau fetch và refs đang có thật trên bare remote. Dùng ls-remote chứng minh server đang trỏ ở đâu, branch -avv chứng minh tracking local sau cập nhật, log graph giải thích đường commit. Nếu ID khác, hãy hỏi fetch đã chạy chưa, push thành công chưa và đang nhìn đúng remote hay không. Một thông báo thành công đơn lẻ không thay cho bằng chứng nhiều phía.

**Purpose:**

Khép chương bằng đối chiếu server refs và remote-tracking refs.
