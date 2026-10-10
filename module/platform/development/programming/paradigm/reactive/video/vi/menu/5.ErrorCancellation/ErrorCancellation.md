---
video:
  url: ""
---

# Vòng đời luồng reactive: Hoàn tất, lỗi và hủy đăng ký

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

## Kết thúc thành công và giới hạn phát tín hiệu sau onComplete

<!-- VIDEO_SECTION -->

### Scene 1 — Kết thúc thành công và giới hạn phát tín hiệu sau onComplete

**Time:** `00:00–00:58`

**Visual:**

Trên biểu đồ tín hiệu vẽ nguồn rỗng onSubscribe→onComplete khi demand=0. Dòng dưới vẽ request(2)→onNext(A)→onComplete; gạch chéo onNext(B) sau hoàn tất.

**Script:**

Hoàn tất nghĩa là subscription không còn dữ liệu nữa. Ví dụ đầu tiên hoàn toàn hợp lệ: onSubscribe rồi onComplete ngay, dù chưa xin phần tử nào. Nguồn thứ hai nhận request hai, phát A rồi kết thúc mà chưa dùng hết lượt còn lại. Demand giới hạn dữ liệu onNext, không chặn thông báo terminal. Sau onComplete không được còn onNext hay onError cho subscriber ấy.

**Purpose:**

Cho thấy hoàn tất không chờ demand và cấm phát sau terminal.

## Kết thúc do lỗi và đường lan truyền onError

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Giữ làn xanh onComplete rồi thay bằng lỗi cảm biến mất kết nối màu đỏ, không thêm callback cuối nào khác.

**Script:**

Nguồn vừa hết dữ liệu bình thường. Nếu cảm biến gặp lỗi, subscriber cần nhận tín hiệu nào?

**Purpose:**

Đối chiếu terminal bình thường với terminal lỗi loại trừ nhau.

### Scene 2 — Kết thúc do lỗi và đường lan truyền onError

**Time:** `01:12–02:10`

**Visual:**

Thay cuối chuỗi thành onError(disconnected); thêm nhánh onSubscribe→onError(cannotStart) không hề request và gạch chéo tất cả slot sau lỗi.

**Script:**

Cảm biến mất kết nối là thất bại chứ không phải hoàn tất bình thường. Publisher báo onError kèm nguyên nhân rồi subscription kết thúc. Nó còn có thể lỗi ngay sau onSubscribe khi chưa có demand. Nếu retry thì đó là chính sách với vòng đời mới và có thể có tác động ngoài; không được coi subscription đã terminal tự sống lại và tiếp tục phát onNext như cũ.

**Purpose:**

Tách tín hiệu lỗi terminal khỏi giá trị nghiệp vụ và mọi tiếp diễn giả sau lỗi.

## Cancellation: Consumer chấm dứt nhu cầu theo dõi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Bỏ biểu tượng nguồn lỗi, cho người xem bấm Đóng và vẽ cancel() ngược tới Subscription thay vì onComplete xuống dưới.

**Script:**

Nguồn có thể tự hết hay báo lỗi. Nhưng nếu nguồn vẫn khỏe còn người xem không muốn nghe nữa thì sao?

**Purpose:**

Chuyển nguyên nhân dừng từ Publisher sang Subscriber.

### Scene 3 — Cancellation: Consumer chấm dứt nhu cầu theo dõi

**Time:** `02:24–03:22`

**Visual:**

Vẽ người xem đóng bảng→Subscriber→Subscription.cancel(); Publisher vẫn có làn tới người xem khác, biểu tượng onComplete xám và không xuất hiện.

**Script:**

Cancellation bắt đầu từ người nhận. Người xem đóng một bảng, subscriber gọi cancel qua Subscription và Publisher phải dần ngừng phát cho người ấy. Đây không phải onComplete; lệnh hủy không bắt buộc sinh callback thành công. Nguồn hot vật lý vẫn có thể phục vụ bảng khác. Một subscription dừng không đồng nghĩa mọi nguồn trong hệ thống đều tắt.

**Purpose:**

Cho thấy hướng hủy và phạm vi theo từng subscriber, không bịa thêm terminal.

## Tín hiệu đang truyền khi hủy và tính bất đồng bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Đặt dấu cancel lúc 09:02:00; giữ token 33°C đang đi giữa hàng đợi và subscriber vừa hủy.

**Script:**

Cancel đã trả về nhưng có phần tử đang trên đường đi. Liệu nó biến mất ngay lập tức?

**Purpose:**

Gợi tình huống phần tử đang bay và bảo đảm dừng dần thay vì đồng bộ.

### Scene 4 — Tín hiệu đang truyền khi hủy và tính bất đồng bộ

**Time:** `03:36–04:34`

**Visual:**

Cho onNext(33) rời upstream lúc 09:01:59, cancel() gọi lúc 09:02:00 rồi token mới tới Subscriber; tô phần thời gian về sau không còn phát.

**Script:**

Số ba mươi ba đã vào hàng đợi trước khi hủy. Nó vẫn có thể tới sau khi cancel trả về; Reactive Streams yêu cầu Publisher cuối cùng ngừng báo, không thể làm mọi tín hiệu đang truyền biến mất tức thì. Nếu bảng đã đóng, handler giao diện có thể kiểm trạng thái người xem trước khi hiển thị cảnh báo muộn. Đó là quyết định ứng dụng bổ sung bên cạnh vòng đời giao thức.

**Purpose:**

Minh họa race tín hiệu đang truyền mà không cho phép phát vô hạn sau hủy.

## Thu hồi tài nguyên và xử lý kết thúc ở từng giai đoạn

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Chuyển số đo tới muộn sang bảng kiểm kết nối, hàng đợi, subscription với ba ô thành công/lỗi/hủy.

**Script:**

Phần tử tới muộn nhắc ta: dừng phát tín hiệu và giải phóng tài nguyên có liên quan nhưng không cùng một thời điểm.

**Purpose:**

Chuyển từ race tín hiệu sang trách nhiệm thu hồi tài nguyên.

### Scene 5 — Thu hồi tài nguyên và xử lý kết thúc ở từng giai đoạn

**Time:** `04:48–05:46`

**Visual:**

Dựng ma trận ba kết cục COMPLETE, ERROR, CANCEL và ba tài nguyên kết nối, queue, subscription; tô phần chẩn đoán riêng của ERROR, nguồn hot còn phục vụ người khác.

**Script:**

Nguồn hữu hạn hoàn thành thì cần trả những tài nguyên nó sở hữu. Nhánh lỗi còn cần thông tin chẩn đoán. Hủy nên dừng phần việc không cần nữa, nhưng không nhất thiết giải phóng mọi kết nối đồng bộ. Và đóng một bảng nguồn hot không được phá người xem khác. Với mỗi nhánh, hãy kiểm tài nguyên thuộc subscription nào, số kết nối và độ sâu hàng đợi cuối cùng có giảm đúng không.

**Purpose:**

Kiểm thu hồi theo quyền sở hữu và từng đường thoát, tránh giả định cancel xóa mọi tài nguyên ngay.

## Đối chiếu ba kết cục trong một luồng tín hiệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Thay bảng kiểm bằng ba dòng tín hiệu khởi đầu cùng onSubscribe, tô màu terminal khác với lời gọi điều khiển.

**Script:**

Ta đã xét từng cách dừng. Bây giờ đặt cả ba cạnh nhau để chắc chắn không thêm callback không hề tồn tại.

**Purpose:**

Tổng hợp khác biệt terminal và cancel trong ba chuỗi giao thức.

### Scene 6 — Đối chiếu ba kết cục trong một luồng tín hiệu

**Time:** `06:00–06:58`

**Visual:**

Vẽ ba làn cảm biến: SUCCESS request(2)→27→33→onComplete; FAILURE request(2)→27→onError(disconnected); CANCEL request(2)→27→cancel()→dừng dần, có token nét đứt đang bay.

**Script:**

Ba câu chuyện này kết thúc khác nhau. Hoàn thành là nguồn hết dữ liệu, thất bại có nguyên nhân terminal; sau hai tín hiệu ấy không được phát tiếp. Hủy là người nhận không còn muốn xem; không bắt buộc onComplete và số đo đang đi vẫn có thể tới trong chốc lát. Nếu kiểm thử cả ba chỉ bằng một callback done giống nhau, ta đã hiểu sai giao thức.

**Purpose:**

Đối chiếu ba kết cục mà không nhầm cancel thành terminal thứ ba.
