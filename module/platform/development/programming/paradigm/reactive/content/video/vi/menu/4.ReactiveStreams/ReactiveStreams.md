---
video:
  url: ""
---

# Reactive Streams: Hợp đồng tín hiệu và nhu cầu nhận

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

## Đặc tả Reactive Streams và phạm vi so với tư duy reactive

<!-- VIDEO_SECTION -->

### Scene 1 — Đặc tả Reactive Streams và phạm vi so với tư duy reactive

**Time:** `00:00–00:58`

**Visual:**

Đặt “tư duy reactive” với đồng hồ ngoài khung “đặc tả Reactive Streams JVM”; bên trong lần lượt mở MUST về giới hạn onNext, tín hiệu tuần tự và backpressure không chặn.

**Script:**

Đây là khu vực của đặc tả Reactive Streams cụ thể, không phải mọi luồng sự kiện trên đời. Nó nhắm tới trao đổi bất đồng bộ giữa các thành phần tương thích với backpressure không chặn. Những từ must, should và may là mức yêu cầu của chuẩn. Nhờ thế ta kiểm tra được số onNext đã xin và thứ tự kết thúc, nhưng đặc tả không quyết định quy tắc cảnh báo nhiệt độ, endpoint HTTP hay scheduler của ứng dụng.

**Purpose:**

Giới hạn rõ lời hứa giao thức bắt buộc so với quyết định của nghiệp vụ.

## Publisher, Subscriber, Subscription và Processor: Các vai trò phối hợp

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Phóng vào khung đặc tả Reactive Streams, chia giao diện thành bốn vị trí vai trò.

**Script:**

Chuẩn đặt ra quy tắc cho các thành phần tương thích. Hãy gọi đúng bốn vai trò trước khi lần theo tín hiệu.

**Purpose:**

Nối phạm vi đặc tả với các thành phần trong hợp đồng.

### Scene 2 — Publisher, Subscriber, Subscription và Processor: Các vai trò phối hợp

**Time:** `01:12–02:10`

**Visual:**

Vẽ Publisher(cảm biến) → Processor(kiểm tra) → Subscriber(màn hình). Mỗi mũi tên có một Subscription riêng; Processor nhận như Subscriber ở trên, phát như Publisher ở dưới.

**Script:**

Publisher cung cấp giá trị, Subscriber nhận dữ liệu và tín hiệu kết thúc. Subscription là quan hệ điều khiển riêng giữa hai bên, để xin thêm hoặc hủy. Processor vừa làm Subscriber của upstream, vừa làm Publisher của downstream. Vì bước lọc có thể bỏ dữ liệu, dashboard xin ba mẫu không bắt buộc Processor chuyển nguyên request ba cho adapter. Mỗi ranh giới có nghĩa vụ của nó.

**Purpose:**

Phân biệt bốn vai trò, Processor tùy chọn và các hợp đồng demand riêng.

## Thứ tự onSubscribe, onNext, onError và onComplete

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Thay nhãn vai trò bằng biểu đồ thông điệp; giữ mũi tên đầu từ Publisher cho đến khi xuất hiện onSubscribe(subscription).

**Script:**

Đã rõ ai là ai. Vậy callback tới một Subscriber phải xuất hiện theo thứ tự nào?

**Purpose:**

Mở câu hỏi về thứ tự tín hiệu và phân biệt lời gọi điều khiển.

### Scene 3 — Thứ tự onSubscribe, onNext, onError và onComplete

**Time:** `02:24–03:22`

**Visual:**

Cho chạy các làn: Publisher→Subscriber onSubscribe đầu tiên; Subscriber→Subscription request(2); Publisher→Subscriber onNext(A), onNext(B); sau đó chia MỘT nhánh onComplete HOẶC onError. Đặt cancel trên mũi tên điều khiển ngược.

**Script:**

Với một subscriber, onSubscribe phải đến trước. Sau đó có thể có không, một hoặc nhiều onNext, nhưng mỗi phần tử cần demand đã xin. Kết thúc nếu có thì là onComplete hoặc onError, không phải cả hai. Các callback tới cùng subscriber phải tuần tự, kể cả khi implementation có nhiều thread. Request và cancel do người nhận gọi trên Subscription; đừng vẽ cancel như một onX do Publisher phát.

**Purpose:**

Minh họa đúng hướng điều khiển, sự tuần tự và hai kiểu terminal loại trừ nhau.

## request(n), nhu cầu tích lũy và giới hạn onNext

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Xóa nhánh kết thúc, đặt hai mũi tên request(2) và request(3) lên cùng một bộ đếm Subscription.

**Script:**

Đã thấy thứ tự tín hiệu hợp lệ. Trước khi phát A, hãy đếm người nhận đã cấp bao nhiêu lượt dữ liệu.

**Purpose:**

Nối thứ tự callback với giới hạn số onNext được xin.

### Scene 4 — request(n), nhu cầu tích lũy và giới hạn onNext

**Time:** `03:36–04:34`

**Visual:**

Bộ đếm bắt đầu 0; request(2)→2, request(3)→5; onNext(A/B/C) còn 2. Mở onComplete hợp lệ dù dư hai lượt; gạch chéo onNext thứ sáu nếu chưa xin thêm.

**Script:**

Trên cùng Subscription, xin hai rồi ba là tổng hạn mức năm. A, B, C tiêu hết ba, còn hai. Publisher có thể phát ít hơn năm, rồi onComplete nếu nguồn đã cạn; không phải bịa thêm hai phần tử để đủ số. Nhưng nó không được phát onNext thứ sáu khi chưa được cấp thêm lượt. Khi cộng demand rất lớn, implementation còn cần xử lý tràn bộ đếm an toàn.

**Purpose:**

Dùng bộ đếm thật để phân biệt data demand với terminal và tràn số.

## Nhu cầu không hợp lệ, kết thúc luồng và lỗi giao thức

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Đổi request(3) thành request(0), tô đỏ đường request, giữ hai nút pause và cancel ở nơi khác.

**Script:**

Nếu subscriber xin số không với ý định tạm dừng, có phải nó đang hủy đúng cách không?

**Purpose:**

Chuẩn bị đối chiếu request không hợp lệ với cancel hợp lệ.

### Scene 5 — Nhu cầu không hợp lệ, kết thúc luồng và lỗi giao thức

**Time:** `04:48–05:46`

**Visual:**

Đặt hai lời gọi request(0), request(-1) đóng dấu SAI; vẽ Publisher→Subscriber onError(IllegalArgumentException), gạch chéo mọi onNext/onComplete sau đó. Bảng khác hiển thị nguồn rỗng onSubscribe→onComplete dù không request.

**Script:**

Xin số không hoặc số âm không phải lệnh tạm dừng. Theo quy tắc Subscription ba chấm chín, nó vi phạm hợp đồng, Publisher phải báo onError với IllegalArgumentException. Muốn ngừng nhận thì dùng cancel. Một điểm dễ nhầm nữa: nguồn rỗng có thể onComplete ngay sau onSubscribe dù chưa ai xin item, lỗi cũng có thể kết thúc không cần demand. Terminal không phải thêm một đơn vị dữ liệu.

**Purpose:**

Chứng minh lỗi request không dương và terminal không phụ thuộc demand.

## Khả năng tương tác nhờ contract và vai trò của thư viện triển khai

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Xóa lớp màu đỏ request lỗi, nối Publisher thư viện A với Subscriber thư viện B; thêm dấu kiểm tương thích ở Processor giữa.

**Script:**

Các quy tắc đủ cụ thể để kiểm chứng. Hai thư viện khác nhau có trao đổi được dù không cùng tên operator không?

**Purpose:**

Nối kiểm tra hợp đồng với mục đích tương tác của Reactive Streams.

### Scene 6 — Khả năng tương tác nhờ contract và vai trò của thư viện triển khai

**Time:** `06:00–06:58`

**Visual:**

Vẽ Publisher thư viện A → điểm kiểm demand/thứ tự/terminal/cancel → Subscriber thư viện B; thêm chốt kiểm Processor và biểu tượng kiểm thử TCK, đặt thẻ WebFlux bên ngoài.

**Script:**

Khi hai bên và các Processor trung gian đều tuân hợp đồng Publisher–Subscriber, họ có thể trao đổi dữ liệu mà không dùng trùng tên operator. Đó là khả năng tương tác. Implementation vẫn phải giữ demand, thứ tự và cách kết thúc; kiểm thử giao thức giúp xác nhận. Reactor là một thư viện, RxJava có nhiều loại observable, còn WebFlux là framework ứng dụng. Chuẩn chung không làm mọi API trở thành một.

**Purpose:**

Thể hiện điều kiện tương thích và giữ ranh giới protocol/thư viện/framework.
