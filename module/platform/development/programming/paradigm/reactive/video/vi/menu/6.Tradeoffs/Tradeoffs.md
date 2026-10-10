---
video:
  url: ""
---

# Lựa chọn mô hình reactive: Tình huống phù hợp và giới hạn

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**
Describe what changes visually between the previous concept and the next.
**Script:**
Write a short spoken bridge into the following section.
**Purpose:**
Explain why this transition is pedagogically needed.

SCENE FORMAT

### Scene N — optional presentation title

**Time:** MM:SS–MM:SS
**Visual:**
Describe the exact diagram, timeline, source, or observation visible.
**Script:**
Write natural narration for the filmed demonstration.
**Purpose:**
State the specific concept or evidence this scene demonstrates.
-->

## Thiết kế một luồng sự kiện từ nguồn đến consumer và điểm kết thúc

<!-- VIDEO_SECTION -->

### Scene 1 — Thiết kế một luồng sự kiện từ nguồn đến consumer và điểm kết thúc

**Time:** `00:00–00:58`

**Visual:**

Vẽ DỮ LIỆU cảm biến→lọc→phân loại→màn hình; ĐIỀU KHIỂN màn hình→Subscription→upstream, queue ở mỗi ranh giới và hai bộ đếm 100/40 mẫu giây. Thêm ba nhánh dừng.

**Script:**

Hãy thiết kế cả bảng theo dõi. Dữ liệu đi xuống qua kiểm tra và phân loại. Khi từng bước có demand, request và cancel bắt đầu ở downstream rồi có thể được điều phối qua từng Subscription ngược lên. Processor có thể đổi số lượt vì lọc sẽ bỏ bớt phần tử. Cảm biến vật lý vẫn có thể tạo một trăm mẫu mỗi giây còn màn hình xử lý bốn mươi; mỗi ranh giới phải có chính sách dư tải. Ba kết cục cũng cần được thiết kế.

**Purpose:**

Ghép hai hướng truyền, tốc độ, tài nguyên và vòng đời thành một bản thiết kế thật.

## So sánh reactive với xử lý tuần tự và callback tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Giữ sơ đồ cảm biến 100 mẫu giây một bên, bên kia đưa danh sách 20 số có sẵn và một vòng lặp tính trung bình.

**Script:**

Ta đã thiết kế cả luồng, nhưng có bài toán nào cần phức tạp đến thế không? Hãy so với dữ liệu đã có sẵn trong bộ nhớ.

**Purpose:**

Đặt thiết kế reactive cạnh giải pháp nhỏ gọn phù hợp hơn cho dữ liệu cố định.

### Scene 2 — So sánh reactive với xử lý tuần tự và callback tường minh

**Time:** `01:12–02:10`

**Visual:**

Ba cột: list cố định [27,29,33] với vòng lặp; vài callback rời; luồng liên tục có filter/lỗi/hủy/demand. Ghi việc phải làm về vòng đời dưới từng cột.

**Script:**

Nếu hai mươi số đã có sẵn và chỉ tính trung bình, vòng lặp hay hàm thuần rõ hơn. Vài callback dễ dọn dẹp cũng hoàn toàn phù hợp. Reactive hấp dẫn hơn khi dữ liệu đến liên tục, phải ghép nhiều bước, điều tiết tải và hủy theo dõi rõ ràng. Hãy đếm những tình huống vòng đời thực, đừng đếm số dòng operator.

**Purpose:**

Đặt lựa chọn thay thế đúng bối cảnh trước khi nói chi phí reactive.

## Bất đồng bộ, non-blocking, song song và scheduling: Các khái niệm không đồng nhất

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Đặt pipeline trên timeline một thread; bên cạnh vẽ I/O chờ và hai tác vụ CPU cùng chạy làm ví dụ riêng.

**Script:**

Luồng sống lâu không tự chạy song song. Ta cần tách thời điểm nhận, việc chặn và số thread thực sự.

**Purpose:**

Mở ranh giới runtime mà không gán đặc tính tự động cho paradigm.

### Scene 3 — Bất đồng bộ, non-blocking, song song và scheduling: Các khái niệm không đồng nhất

**Time:** `02:24–03:22`

**Visual:**

Bốn nhãn riêng: ASYNC kết quả sau lời gọi, NONBLOCKING không giữ thread chờ, PARALLEL việc cùng tiến triển, SCHEDULER chọn nơi chạy. Hiện reactive đồng bộ một thread và lời gọi blocking chen vào.

**Script:**

Bất đồng bộ là kết quả không nhất thiết đến trong lời gọi khởi đầu. Không chặn nghĩa là không giữ một thread chỉ để đợi. Song song là công việc thực sự cùng tiến triển. Scheduler quyết định chạy ở đâu và lúc nào. Pipeline reactive vẫn có thể đồng bộ trên một thread, và lời gọi blocking sai chỗ vẫn làm nghẽn. Reactive Streams quy định backpressure không chặn và tính đáp ứng, không cấp sẵn một thread cho mỗi operator.

**Purpose:**

Minh họa bốn tính chất khác nhau và giới hạn lời hứa của đặc tả.

## Chi phí về truy vết, lỗi, tài nguyên và độ phức tạp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Thay timeline thread bằng cảnh báo 33°C bị mất và năm chốt kiểm: filter, drop, demand, cancel, error.

**Script:**

Khi cảnh báo biến mất, ta cần đo bằng chứng để phân biệt nguyên nhân thay vì đoán lỗi scheduler.

**Purpose:**

Chuyển kiến thức runtime sang phép đo khi gỡ lỗi luồng.

### Scene 4 — Chi phí về truy vết, lỗi, tài nguyên và độ phức tạp

**Time:** `03:36–04:34`

**Visual:**

Dựng bảng cảnh báo 33°C bị mất với số đếm trước/sau filter, độ sâu queue, demand còn lại, dấu giờ terminal/cancel; vẽ năm nhánh nguyên nhân.

**Script:**

Số ba mươi ba có thể bị lọc sai, rơi khi queue đầy, chưa được xin, đến sau cancel hoặc bị chặn bởi onError. Hãy đặt bộ đếm và timestamp đúng tại các ranh giới. Stack trace qua nhiều bước bất đồng bộ có thể chỉ kể một đoạn câu chuyện. Ghi log thật nhiều operator mà không biết ai giữ tài nguyên hay chính sách lỗi vẫn không đủ bằng chứng.

**Purpose:**

Cho trình tự phân biệt năm nguyên nhân gây mất một cảnh báo.

## Ranh giới với Project Reactor, Spring WebFlux, RxJava và Java Flow

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Biến năm điểm đo thành bốn kệ học Reactor, RxJava, Java Flow, WebFlux, không đổi những câu hỏi về hợp đồng.

**Script:**

Những câu hỏi về luồng vẫn còn khi ta đổi thư viện. Vậy cơ chế operator và runtime cụ thể nên học ở đâu?

**Purpose:**

Nối kiến thức gỡ lỗi nền tảng với module công nghệ chuyên biệt.

### Scene 5 — Ranh giới với Project Reactor, Spring WebFlux, RxJava và Java Flow

**Time:** `04:48–05:46`

**Visual:**

Bốn kệ: Reactor Flux/Mono, RxJava Observable/Flowable, giao diện Java Flow và HTTP WebFlux. Dưới cùng đánh dấu backpressure khác nhau và cảnh báo API reactive không tự nonblocking.

**Script:**

Reactor cung cấp kiểu ghép luồng, RxJava có nhiều loại observable không cùng nghĩa demand, Java Flow có giao diện chuẩn, WebFlux đưa reactive vào web. Operator, scheduler và HTTP runtime cụ thể phải học ở module chuyên trách. Chỉ gọi một API chưa đủ chứng minh ứng dụng không chặn, cũng không có nghĩa mọi kiểu đều tuân chính xác cùng hợp đồng Reactive Streams.

**Purpose:**

Bàn giao cho module công nghệ mà vẫn giữ đúng phạm vi đặc tả.

## Tiêu chí chọn reactive hoặc giải pháp đơn giản hơn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Cất bốn thẻ công nghệ, đặt bài toán trung bình hai mươi số cạnh dashboard cảm biến liên tục kèm checklist buffer/lỗi/hủy.

**Script:**

Trước khi chọn thư viện, hãy quyết định chính bài toán có đáng dùng reactive không.

**Purpose:**

Chuyển từ tên công cụ trở lại bằng chứng tải và vòng đời.

### Scene 6 — Tiêu chí chọn reactive hoặc giải pháp đơn giản hơn

**Time:** `06:00–06:58`

**Visual:**

Hai thẻ cuối: hai mươi số có sẵn→một vòng lặp và cảm biến liên tục→kiểm tra→cảnh báo→hiển thị. Đánh dấu data, demand, dư tải, terminal và dọn tài nguyên.

**Script:**

Hai mươi số có sẵn trong bộ nhớ thường chỉ cần một hàm đơn giản. Cảm biến liên tục, nhiều bước biến đổi, tốc độ chênh nhau và người xem có thể thoát hoặc gặp lỗi thì reactive đáng cân nhắc. Nhưng đừng triển khai chỉ vì tên gọi. Phải biết upstream có hỗ trợ demand không, ai giữ buffer, dữ liệu nào được bỏ và mọi nhánh kết thúc được dọn thế nào. Paradigm giúp đặt câu hỏi; implementation phải chứng minh câu trả lời.

**Purpose:**

Khép video bằng tiêu chí dựa trên bằng chứng và ranh giới kiểm thử công nghệ.
