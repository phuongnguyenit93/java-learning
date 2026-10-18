---
video:
  url: ""
---

# Lập trình phản ứng (Reactive Programming): Dòng dữ liệu theo thời gian

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

## Reactive Programming: Khái niệm và phạm vi

<!-- VIDEO_SECTION -->

### Scene 1 — Reactive Programming: Khái niệm và phạm vi

**Time:** `00:00–00:58`

**Visual:**

Vẽ cảm biến bên trái, đồng hồ lần lượt hiện 27°C, 29°C, 33°C và bảng cảnh báo bên phải; chỉ mở mũi tên tại thời điểm số đo đến.

**Script:**

Hãy tưởng tượng mở bảng nhiệt độ lúc chín giờ. Bây giờ chỉ có số hai mươi bảy, nhưng cảm biến chưa dừng đo. Một phút sau là hai mươi chín, rồi ba mươi ba làm cảnh báo sáng lên. Lập trình reactive giúp mô tả việc phải làm mỗi khi giá trị mới tới. Đây là tư duy xử lý dữ liệu thay đổi theo thời gian, không phải tên của một framework web.

**Purpose:**

Đặt nền cho dòng dữ liệu theo thời gian mà không ngầm hứa đa luồng hoặc non-blocking.

## Động cơ: Dữ liệu đến theo thời gian và giới hạn của callback rời rạc

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Cho timeline cảm biến tiếp tục chạy nhưng tách ba mũi tên cảnh báo, lỗi và mất kết nối về các callback riêng.

**Script:**

Số đo đã thay đổi theo thời gian. Nhưng nếu lọc, lỗi và mất kết nối nằm rải rác ở những callback khác nhau thì sao?

**Purpose:**

Chuyển từ giá trị theo thời gian sang vấn đề phối hợp nhiều callback.

### Scene 2 — Động cơ: Dữ liệu đến theo thời gian và giới hạn của callback rời rạc

**Time:** `01:12–02:10`

**Visual:**

Tách cùng cảm biến thành ba hộp callback onTemperature, onSensorError, onDisconnect; nối các mũi tên rời tới lọc, cửa sổ mười giây và giải phóng tài nguyên.

**Script:**

Nếu bảng theo dõi còn phải loại số đo bất khả thi, gom dữ liệu mười giây và đóng kết nối lúc mất tín hiệu thì sao? Ba callback vẫn làm được, nhưng việc chúng phối hợp với nhau khó lần hơn. Động cơ dùng luồng không phải vì callback luôn sai, mà vì ta muốn nhìn thấy các bước biến đổi, nhánh lỗi và điều kiện dừng trong cùng một đường xử lý.

**Purpose:**

Cho thấy bài toán điều phối vòng đời lớn dần thay vì phủ nhận callback đơn giản.

## Giá trị có sẵn và các sự kiện đến ở những thời điểm khác nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Thu ba mũi tên callback về một trục thời gian, để dấu hỏi tại nhánh lỗi chưa xử lý.

**Script:**

Ta vừa thấy callback bị phân tán. Bây giờ hãy so một con số đã có sẵn với những số đo chưa biết lúc nào mới tới.

**Purpose:**

Chuyển từ vấn đề callback sang chiều thời gian của dữ liệu.

### Scene 3 — Giá trị có sẵn và các sự kiện đến ở những thời điểm khác nhau

**Time:** `02:24–03:22`

**Visual:**

Đặt bên trái temperature=27 cố định, bên phải timeline 09:00→27, 09:01→29, 09:02→33; kéo dài nét chấm hỏi về tương lai.

**Script:**

Bên trái ta có thể tính ngay với hai mươi bảy. Bên phải, hai mươi bảy mới là sự kiện đầu tiên; chưa ai biết khi nào có số tiếp theo, thậm chí có hay không. Hai bên vẫn có thể dùng cùng phép lọc, nhưng chỉ dòng bên phải phải trả lời ai bắt đầu theo dõi, lỗi được báo ra sao và khi nào người nhận rời đi.

**Purpose:**

Phân biệt độ dài và thời điểm bất định với một giá trị đã sẵn có.

## Điểm xuất phát: Hàm biến đổi, cách mô tả ý định và bất đồng bộ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Kéo điều kiện lọc từ thẻ giá trị cố định sang timeline, để trống mũi tên đầu vào cho đến nhịp tiếp theo.

**Script:**

Phép lọc không đổi. Điều khác biệt nằm ở thời điểm dữ liệu đến và cách nối các bước xử lý.

**Purpose:**

Dùng cùng một phép lọc để nối tính toán cố định với xử lý theo thời gian.

### Scene 4 — Điểm xuất phát: Hàm biến đổi, cách mô tả ý định và bất đồng bộ

**Time:** `03:36–04:34`

**Visual:**

Hiện ba hộp lọc → biến đổi → nhận; phía trên tách NHẬN GÌ, KHI NÀO và BAO NHIÊU (chỉ khi có contract demand).

**Script:**

Hàm biến đổi vẫn là một hàm: nhận số đo rồi có thể trả loại cảnh báo. Ta mô tả lọc, đổi và sử dụng dữ liệu trước khi thấy bất kỳ số đo nào. Nhưng khai báo pipeline không chứng minh nó đã chạy. Bất đồng bộ chỉ nói kết quả có thể đến sau lời gọi hiện tại, chứ không tự có thêm lõi CPU hay thread chạy song song.

**Purpose:**

Nối phép ghép hàm với chiều thời gian, không đồng nhất bất đồng bộ và song song.

## Mô hình nguồn, luồng tín hiệu, phép biến đổi và nơi nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Giữ ba hộp xử lý, tách đồng hồ ra khỏi hộp và đặt biểu tượng cảm biến vào đầu vào.

**Script:**

Ta đã biết mỗi bước cần làm gì. Giờ hãy gọi đúng tên thành phần tạo số đo và nơi nhận cuối cùng.

**Purpose:**

Chuyển từ chức năng trừu tượng sang nguồn và người nhận cụ thể.

### Scene 5 — Mô hình nguồn, luồng tín hiệu, phép biến đổi và nơi nhận

**Time:** `04:48–05:46`

**Visual:**

Lần lượt chạy cảm biến [27,-300,33] → kiểm tra [27,33] → phân loại [bình thường,cảnh báo] → bảng theo dõi; tô xám nhánh -300 bị loại.

**Script:**

Nguồn là cảm biến, adapter có thể đóng vai producer. Bước kiểm tra bỏ âm ba trăm, bước phân loại biến ba mươi ba thành cảnh báo, consumer cập nhật bảng. Dữ liệu đi xuôi qua từng bước. Chỉ nhìn sơ đồ này chưa thể kết luận luồng đã phát, có demand hay dùng nhiều thread. Các câu hỏi đó thuộc vòng đời và hợp đồng.

**Purpose:**

Thể hiện rõ trách nhiệm từng bước và trường hợp filter không phát phần tử nào.

## Reactive Streams: Hợp đồng luồng, không phải toàn bộ paradigm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Đóng băng cảnh báo trên bảng, vẽ đường nét đứt ranh giới giao thức sau mũi tên upstream, không đặt logo framework.

**Script:**

Sơ đồ vừa rồi là tư duy reactive. Muốn đếm chính xác số phần tử người nhận được phép nhận, ta còn cần lời hứa nào?

**Purpose:**

Gợi nhu cầu về hợp đồng đếm phần tử mà không gán cho toàn bộ paradigm.

### Scene 6 — Reactive Streams: Hợp đồng luồng, không phải toàn bộ paradigm

**Time:** `06:00–06:58`

**Visual:**

Đặt hai thẻ: luồng sự kiện tự đẩy khi có dữ liệu và kết nối Reactive Streams Publisher↔Subscriber kèm request(n), cancel(); không gắn chữ sai cho thẻ đầu.

**Script:**

Một nguồn cứ có sự kiện là đẩy vẫn có thể mang phong cách reactive dù không có request. Reactive Streams là hợp đồng cụ thể hơn: trao đổi bất đồng bộ, tín hiệu theo thứ tự và backpressure không chặn. Nó đặt tên Publisher, Subscriber, Subscription và Processor khi cần. Không nên bê quy tắc demand này áp cho mọi thư viện có chữ reactive.

**Purpose:**

Tách ý tưởng reactive chung khỏi giao thức Reactive Streams có quy tắc bắt buộc.

## Ranh giới giữa paradigm, thư viện và framework reactive

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Đưa bốn vai trò RS vào lớp giữa, xếp ba thẻ Reactor, RxJava, WebFlux ở lớp phía trên.

**Script:**

Khi đã tách hợp đồng khỏi khái niệm luồng, ta mới đặt thư viện và framework vào đúng tầng.

**Purpose:**

Dẫn từ giao thức sang lớp công cụ hiện thực mà không trộn các tầng.

### Scene 7 — Ranh giới giữa paradigm, thư viện và framework reactive

**Time:** `07:12–08:10`

**Visual:**

Dựng ba tầng: PARADIGM (phản ứng số đo), LIBRARY (Reactor Flux/Mono, RxJava), FRAMEWORK (WebFlux HTTP); đặt Reactive Streams ở ranh giới phù hợp.

**Script:**

Reactor có những kiểu và phép biến đổi như Flux, Mono. RxJava có nhiều kiểu observable với cách xử lý backpressure khác nhau. Spring WebFlux áp dụng reactive cho ứng dụng web. Không tên nào trong đó là định nghĩa của paradigm. Không cần tạo endpoint WebFlux để chứng minh luồng cảm biến, và gọi API reactive cũng chưa đủ chứng minh code ứng dụng không chặn.

**Purpose:**

Ngăn đồng nhất tên framework với mô hình tư duy hoặc lời hứa non-blocking.

## Từ lan truyền dữ liệu đến subscription, backpressure và kết thúc luồng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:10–08:24`

**Visual:**

Thay tầng framework bằng lộ trình: dòng dữ liệu → subscription → backpressure → kết thúc, tô sáng dấu hỏi ở bước tiếp.

**Script:**

Ta đã phân loại công cụ. Bây giờ hãy đi theo một số đo từ đầu tới cuối, rồi xem ai yêu cầu nhận và chuyện gì xảy ra khi cảm biến chạy nhanh hơn màn hình.

**Purpose:**

Tạo lộ trình từ mô tả dòng sang vòng đời, tải và kết thúc.

### Scene 8 — Từ lan truyền dữ liệu đến subscription, backpressure và kết thúc luồng

**Time:** `08:24–09:22`

**Visual:**

Kéo dài pipeline cảm biến thành bốn trạm: lần dấu dữ liệu, subscription, đồng hồ demand, rồi ba biểu tượng kết thúc; tô sáng chương Data Flow.

**Script:**

Đây là lộ trình học. Đầu tiên ta tìm đúng chỗ mỗi số đo được giữ, đổi hoặc bỏ. Sau đó mới mở subscription để hiểu thời điểm dữ liệu chạy. Tiếp theo là bài toán cảm biến phát một trăm mẫu mỗi giây còn màn hình chỉ xử lý bốn mươi. Từ đó ta học quy tắc demand của Reactive Streams, rồi so hoàn tất, lỗi và hủy trước khi quyết định có nên dùng mô hình này.

**Purpose:**

Cho người học hình dung các điều kiện tiên quyết bằng một ví dụ cảm biến nhất quán.
