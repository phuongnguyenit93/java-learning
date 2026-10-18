---
video:
  url: ""
---

# Hợp nhất nhánh và giải quyết xung đột

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

## Phân kỳ lịch sử khi các nhánh cùng phát triển

<!-- VIDEO_SECTION -->

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `00:00–01:42`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git log --graph --oneline --all --decorate`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Hai người tạo commit mới từ một tổ tiên chung; đồ thị bắt đầu phân nhánh. Không có gì 'hỏng' khi hai đường lịch sử cùng tiến, nhưng Git cần quyết định đưa thay đổi nào vào nhánh đích. Hãy đánh dấu commit tổ tiên chung và hai đầu nhánh, rồi dự đoán đâu là phần riêng của từng người. Đây là lý do merge không chỉ là dán hai nội dung tệp với nhau.

**Purpose:**

Xác định chính xác phân kỳ trước khi chọn cơ chế merge.

## Fast-forward merge và chuyển dịch tham chiếu nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:42–01:55`

**Visual:**

Giữ frame kết quả lệnh [git log --graph --oneline --all --decorate] với nhãn 'Phân kỳ lịch sử khi các nhánh cùng phát triển'; khoanh chứng cứ đã xác nhận: Xác định chính xác phân kỳ trước khi chọn cơ chế merge.. Mở cửa sổ terminal/đồ thị kế tại [git switch main] dưới nhãn 'Fast-forward merge và chuyển dịch tham chiếu nhánh'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Chứng minh fast-forward không cần commit hợp nhất mới.

**Script:**

Nếu một nhánh vẫn là tổ tiên trực tiếp, Git có thể tiến tham chiếu; còn khi cả hai đã thêm commit thì sao?

**Purpose:**

Nối phép kiểm chứng 'Phân kỳ lịch sử khi các nhánh cùng phát triển' sang 'Fast-forward merge và chuyển dịch tham chiếu nhánh', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Chứng minh fast-forward không cần commit hợp nhất mới.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `01:55–03:37`

**Visual:**

Chuyển sang ví dụ thử nghiệm độc lập đã chuẩn bị sẵn (không dùng lịch sử phân kỳ ở Scene 1): `feature/rounding` có một commit mới, còn `main` vẫn là tổ tiên của feature. Đặt terminal cạnh đồ thị trước/sau lệnh `git switch main; git merge feature/rounding; git log --graph --oneline --all`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Khác với đồ thị phân kỳ vừa xem, tình huống độc lập này giữ `main` làm tổ tiên của đầu nhánh feature. Vì main chưa có commit riêng kể từ lúc feature tách ra, merge có thể đơn giản là đưa con trỏ main tiến tới commit ở feature. Hãy dừng đồ thị ngay trước lệnh và dự đoán có thêm merge commit hay không. Sau merge, dùng log để thấy không có nút hợp nhất mới trong trường hợp fast-forward; cơ chế này phụ thuộc quan hệ tổ tiên, không phải tên nhánh.

**Purpose:**

Chứng minh fast-forward không cần commit hợp nhất mới.

## Merge base và cơ chế hợp nhất ba chiều

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:37–03:50`

**Visual:**

Giữ frame kết quả lệnh [git switch main] với nhãn 'Fast-forward merge và chuyển dịch tham chiếu nhánh'; khoanh chứng cứ đã xác nhận: Chứng minh fast-forward không cần commit hợp nhất mới.. Mở cửa sổ terminal/đồ thị kế tại [git merge-base main feature/rounding] dưới nhãn 'Merge base và cơ chế hợp nhất ba chiều'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Nối logic ba chiều với tổ tiên chung và hai đường thay đổi.

**Script:**

Fast-forward là trường hợp đơn giản; khi cả hai nhánh đã đi tiếp, cần snapshot tổ tiên chung.

**Purpose:**

Nối phép kiểm chứng 'Fast-forward merge và chuyển dịch tham chiếu nhánh' sang 'Merge base và cơ chế hợp nhất ba chiều', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Nối logic ba chiều với tổ tiên chung và hai đường thay đổi.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `03:50–05:32`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git merge-base main feature/rounding; git log --graph --oneline --all`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Tạo tình huống main và feature đều có commit riêng rồi tìm merge-base. Git dùng snapshot ở tổ tiên chung làm điểm so sánh với mỗi đầu nhánh; từ đó có thể kết hợp những sửa đổi độc lập. Nếu cả hai cùng sửa một đoạn không tương thích, Git cần sự quyết định của chúng ta. Mũi tên từ merge-base sang từng tip cho thấy tại sao chỉ nhìn diff giữa hai tip có thể bỏ sót bối cảnh.

**Purpose:**

Nối logic ba chiều với tổ tiên chung và hai đường thay đổi.

## Merge commit và nhiều quan hệ commit cha

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:32–05:45`

**Visual:**

Giữ frame kết quả lệnh [git merge-base main feature/rounding] với nhãn 'Merge base và cơ chế hợp nhất ba chiều'; khoanh chứng cứ đã xác nhận: Nối logic ba chiều với tổ tiên chung và hai đường thay đổi.. Mở cửa sổ terminal/đồ thị kế tại [git merge --no-ff feature/rounding] dưới nhãn 'Merge commit và nhiều quan hệ commit cha'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Xác minh merge commit bằng số parent chứ không dựa tên lệnh.

**Script:**

Ba chiều cho phép kết hợp; khi thành công nó có thể lưu dấu tích hợp trong commit mới.

**Purpose:**

Nối phép kiểm chứng 'Merge base và cơ chế hợp nhất ba chiều' sang 'Merge commit và nhiều quan hệ commit cha', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Xác minh merge commit bằng số parent chứ không dựa tên lệnh.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `05:45–07:27`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git merge --no-ff feature/rounding; git show --pretty=raw HEAD`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Trong kho thử nghiệm đã có hai đường commit, merge thành công có thể tạo một nút mới với hai parent. Dùng show --pretty=raw và đánh dấu cả hai ID cha trên đồ thị. Merge commit ghi lại điểm tích hợp, còn nội dung tệp phụ thuộc kết quả hợp nhất. Không phải mỗi merge đều tạo nút hai cha vì fast-forward có thể xảy ra; ta luôn kiểm tra đồ thị sau thao tác.

**Purpose:**

Xác minh merge commit bằng số parent chứ không dựa tên lệnh.

## Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:27–07:40`

**Visual:**

Giữ frame kết quả lệnh [git merge --no-ff feature/rounding] với nhãn 'Merge commit và nhiều quan hệ commit cha'; khoanh chứng cứ đã xác nhận: Xác minh merge commit bằng số parent chứ không dựa tên lệnh.. Mở cửa sổ terminal/đồ thị kế tại [git status] dưới nhãn 'Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Dùng status, index và conflict markers xác định chính xác trạng thái.

**Script:**

Nội dung không phải lúc nào cũng hòa hợp. Hãy xem Git báo conflict thay vì tự chọn bên thắng.

**Purpose:**

Nối phép kiểm chứng 'Merge commit và nhiều quan hệ commit cha' sang 'Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Dùng status, index và conflict markers xác định chính xác trạng thái.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `07:40–09:22`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git status; git ls-files -u; git diff -- README.md`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Bây giờ hai nhánh sửa cùng dòng theo hai cách khác nhau, Git dừng lại thay vì giả vờ chọn đúng. Trên terminal, status cho biết đang merge và tệp chưa được xử lý; ls-files -u có thể lộ các stage của index; file có dấu <<<<<<<, ======= và >>>>>>> để người dùng xem hai phía. Dấu này là gợi ý xử lý, không phải nội dung cuối được phép giữ lại trong chương trình.

**Purpose:**

Dùng status, index và conflict markers xác định chính xác trạng thái.

## Quy trình giải quyết, hoàn tất hoặc hủy một merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:22–09:35`

**Visual:**

Giữ frame kết quả lệnh [git status] với nhãn 'Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra'; khoanh chứng cứ đã xác nhận: Dùng status, index và conflict markers xác định chính xác trạng thái.. Mở cửa sổ terminal/đồ thị kế tại [README.md] dưới nhãn 'Quy trình giải quyết, hoàn tất hoặc hủy một merge'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Dạy hai đường hoàn tất/hủy merge cùng cảnh báo mất công việc dở dang.

**Script:**

Một conflict đã được phát hiện, nhưng cách kết thúc phải dựa vào nội dung và kiểm chứng.

**Purpose:**

Nối phép kiểm chứng 'Dấu hiệu xung đột tệp và trạng thái merge đang diễn ra' sang 'Quy trình giải quyết, hoàn tất hoặc hủy một merge', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Dạy hai đường hoàn tất/hủy merge cùng cảnh báo mất công việc dở dang.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `09:35–11:17`

**Visual:**

Chia màn hình thành HAI bản sao demo độc lập có cùng xung đột: bên trái sửa đúng `README.md`, chạy `git diff --check; git add README.md; git status`, kiểm tra nội dung và tiếp tục bằng `git merge --continue` nếu trạng thái cho phép. Bên phải KHÔNG stage hay tiếp tục, xem `git status` rồi thử riêng `git merge --abort`; đối chiếu graph trước/sau và nêu điều kiện không bảo toàn công việc chưa commit. Không chạy hai phương án nối tiếp trên một repository.

**Script:**

Trên bản demo có conflict, đọc cả yêu cầu nghiệp vụ rồi sửa tệp để giữ hành vi đúng, không chỉ xoá ba dòng marker. Chạy diff --check, add để đánh dấu tệp đã giải quyết, và dùng status để biết merge còn cần commit hay không; chỉ commit sau khi kiểm tra kết quả và test phù hợp. Ở bản sao demo thứ hai, merge --abort giúp quay lại trước merge nếu điều kiện cho phép; nếu có việc chưa ghi trước đó, đừng xem abort là bản sao lưu tuyệt đối.

**Purpose:**

Dạy hai đường hoàn tất/hủy merge cùng cảnh báo mất công việc dở dang.

## Cherry-pick từng commit so với merge cả nhánh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:17–11:30`

**Visual:**

Giữ frame kết quả lệnh [README.md] với nhãn 'Quy trình giải quyết, hoàn tất hoặc hủy một merge'; khoanh chứng cứ đã xác nhận: Dạy hai đường hoàn tất/hủy merge cùng cảnh báo mất công việc dở dang.. Mở cửa sổ terminal/đồ thị kế tại [git log --oneline feature/rounding] dưới nhãn 'Cherry-pick từng commit so với merge cả nhánh'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Phân biệt cherry-pick chọn lọc với merge tích hợp nhánh.

**Script:**

Không phải lúc nào ta cũng muốn toàn bộ nhánh; đôi khi cần đúng một commit để sửa phiên bản khác.

**Purpose:**

Nối phép kiểm chứng 'Quy trình giải quyết, hoàn tất hoặc hủy một merge' sang 'Cherry-pick từng commit so với merge cả nhánh', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Phân biệt cherry-pick chọn lọc với merge tích hợp nhánh.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `11:30–13:12`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git log --oneline feature/rounding; git cherry-pick <safe-commit-id>`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Đặt hai sơ đồ cạnh nhau: merge thường tích hợp lịch sử của cả nhánh, cherry-pick chọn một commit cụ thể và áp dụng thay đổi của nó lên nhánh hiện tại. Một commit được cherry-pick thường có ID mới vì parent mới, và cũng có thể tạo conflict. Chỉ dùng commit ID thật từ log của kho thử nghiệm, kiểm tra status và nội dung sau thao tác; đây không phải cách tự động chuyển mọi dependency đi kèm.

**Purpose:**

Phân biệt cherry-pick chọn lọc với merge tích hợp nhánh.

## Bằng chứng về nội dung và đồ thị commit sau merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `13:12–13:25`

**Visual:**

Giữ frame kết quả lệnh [git log --oneline feature/rounding] với nhãn 'Cherry-pick từng commit so với merge cả nhánh'; khoanh chứng cứ đã xác nhận: Phân biệt cherry-pick chọn lọc với merge tích hợp nhánh.. Mở cửa sổ terminal/đồ thị kế tại [git status -sb] dưới nhãn 'Bằng chứng về nội dung và đồ thị commit sau merge'; tạm che kết quả để người xem dự đoán, rồi làm sáng bằng chứng: Kết hợp bằng chứng đồ thị, nội dung và hành vi sau tích hợp.

**Script:**

Cherry-pick hay merge đều phải có bằng chứng cuối; trạng thái sạch chưa đủ thay cho hành vi đúng.

**Purpose:**

Nối phép kiểm chứng 'Cherry-pick từng commit so với merge cả nhánh' sang 'Bằng chứng về nội dung và đồ thị commit sau merge', từ kết quả đã có tới trạng thái Git phải chứng minh tiếp: Kết hợp bằng chứng đồ thị, nội dung và hành vi sau tích hợp.

### Scene 1 — Thao tác và kiểm chứng merge

**Time:** `13:25–15:07`

**Visual:**

Hai nhánh trong repository thử nghiệm, terminal bên đồ thị, trước/sau lệnh `git status -sb; git log --graph --oneline --decorate -8; git show --stat HEAD`. Đóng băng phần kết quả liên quan, không áp dụng lên nhánh production.

**Script:**

Dùng ba bằng chứng độc lập sau khi hợp nhất: status cho biết còn dở dang không, log graph cho biết quan hệ commit có đúng như dự định, và show cho biết nội dung/tệp được ghi. Thêm kiểm thử trên ví dụ rounding để xác minh cả trường hợp biên; merge thành công không chứng minh kết quả nghiệp vụ đúng. Nếu status còn conflict hay diff chưa commit, không được báo thay đổi đã tích hợp xong.

**Purpose:**

Kết hợp bằng chứng đồ thị, nội dung và hành vi sau tích hợp.
