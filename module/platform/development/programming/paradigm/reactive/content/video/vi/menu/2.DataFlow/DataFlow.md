---
video:
  url: ""
---

# Dòng dữ liệu phản ứng: Nguồn, tín hiệu và đăng ký theo dõi

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

## Nguồn dữ liệu, producer và consumer trong một luồng

<!-- VIDEO_SECTION -->

### Scene 1 — Nguồn dữ liệu, producer và consumer trong một luồng

**Time:** `00:00–00:58`

**Visual:**

Tách cảm biến, adapter, bộ kiểm tra và bảng theo dõi thành bốn làn. Cho token 33°C đi xuyên qua và gắn nhãn source, producer, hai vai, consumer.

**Script:**

Cảm biến vật lý tạo ra số đo. Adapter đưa số đo vào luồng để các thành phần khác nhận; bảng theo dõi sử dụng nó. Bộ kiểm tra ở giữa vừa nhận dữ liệu từ phía trước, vừa gửi dữ liệu xuống phía sau. Gọi đúng vai trò sẽ giúp xác định ai giữ hàng đợi và ai mở kết nối. Bốn hộp này không có nghĩa bốn thread.

**Purpose:**

Phân biệt nguồn dữ liệu với producer và thể hiện vai kép của bước trung gian.

## Các bước biến đổi, lọc và kết hợp tín hiệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:12`

**Visual:**

Giữ nguyên hai làn adapter và bảng, đặt cổng kiểm tra ở giữa rồi đưa số đo -300°C tới đó.

**Script:**

Đã biết số đo xuất phát ở đâu và đi về đâu. Vậy bước trung gian sẽ cho những giá trị nào đi tiếp?

**Purpose:**

Nối trách nhiệm nguồn/đích với bước lọc và biến đổi đầu tiên.

### Scene 2 — Các bước biến đổi, lọc và kết hợp tín hiệu

**Time:** `01:12–02:10`

**Visual:**

Cho 27, -300, 33 đi qua cổng hợp lệ; đẩy -300 vào ô loại bỏ, đổi 27°C thành 80,6°F, gắn cờ cảnh báo cho 33.

**Script:**

Một đầu vào không nhất thiết tạo một đầu ra. Bộ lọc bỏ hẳn âm ba trăm; hai mươi bảy đi tiếp; ba mươi ba được gắn cảnh báo. Phép đổi đơn vị còn có thể cho ra tám mươi phẩy sáu độ F từ hai mươi bảy độ C. Ta có thể ghép thêm trạng thái thiết bị. Mỗi bước có hợp đồng riêng; đừng vội coi số item phía trước và phía sau luôn bằng nhau.

**Purpose:**

Thể hiện trực quan lọc mất phần tử, phép biến đổi và phép kết hợp dữ liệu.

## Push, pull và sự phối hợp hai cách truyền dữ liệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:24`

**Visual:**

Giữ ô -300 bị loại trống, nới hai làn producer và consumer rồi cho đồng hồ chạy tốc độ khác nhau.

**Script:**

Qua các bước xử lý, số lượng và hình dạng dữ liệu có thể đổi. Vậy ai quyết định khi nào dữ liệu được gửi?

**Purpose:**

Nối bài toán số lượng phần tử với quyền chủ động truyền và nhận.

### Scene 3 — Push, pull và sự phối hợp hai cách truyền dữ liệu

**Time:** `02:24–03:22`

**Visual:**

Ba timeline: PUSH phát mỗi 100ms, PULL chờ nơi nhận hỏi, DEMAND có các ô được cấp trước khi phát; bảng theo dõi xử lý mỗi 500ms.

**Script:**

Với push, adapter cứ có số đo là gửi dù màn hình xử lý mỗi nửa giây. Với pull, nơi nhận chủ động hỏi dữ liệu. Cơ chế có demand kết hợp cả hai: người nhận cấp một số lượt, producer phát khi có dữ liệu trong hạn mức. Điều đó chỉ hữu ích nếu ranh giới liên quan tôn trọng demand; gọi tên reactive không làm cảm biến chậm đi.

**Purpose:**

So sánh quyền chủ động truyền theo ba mô hình mà không áp đặc tả lên mọi API.

## Kết hợp các bước xử lý theo mô hình dữ liệu lan truyền

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Vẽ bốn bước nối nhau; tạm dừng đồng hồ downstream trong lúc thay một quy tắc phân loại bằng quy tắc khác.

**Script:**

Đã rõ bên nào khởi phát việc truyền, ta ghép các phép xử lý thế nào để vẫn theo được vòng đời?

**Purpose:**

Chuyển từ cơ chế truyền sang cách tổ chức các bước biến đổi dễ kiểm chứng.

### Scene 4 — Kết hợp các bước xử lý theo mô hình dữ liệu lan truyền

**Time:** `03:36–04:34`

**Visual:**

Lần lượt mở kiểm tra → chuẩn hóa → phân loại → hiển thị. Tô bước phân loại là phép tính thuần, bước hiển thị là tác động ngoài; thêm làn lỗi và hủy.

**Script:**

Ta đổi ngưỡng cảnh báo mà không phải sửa adapter cảm biến. Đó là lợi ích khi mỗi bước có hợp đồng rõ. Nhưng cập nhật giao diện vẫn là tác động ra bên ngoài, dù phép phân loại là phép tính thuần. Một luồng thực còn phải biết lỗi đi đâu và dừng thế nào khi chẳng ai xem nữa. Ghép vài hàm với nhau chưa đủ mô tả vòng đời.

**Purpose:**

Tách phép tính thuần khỏi thao tác hiển thị có tác động và đường kết thúc.

## Subscription: Bắt đầu quan hệ theo dõi và nhận tín hiệu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:34–04:48`

**Visual:**

Làm mờ các bước và tô một bắt tay giữa adapter với dashboard; biểu tượng người theo dõi xuất hiện nhưng chưa có giá trị.

**Script:**

Pipeline đã vẽ xong. Có phải cứ khai báo là dữ liệu lập tức chạy? Hãy nhìn subscription.

**Purpose:**

Đặt câu hỏi về lúc thiết lập quan hệ và lúc phát dữ liệu.

### Scene 5 — Subscription: Bắt đầu quan hệ theo dõi và nhận tín hiệu

**Time:** `04:48–05:46`

**Visual:**

Vẽ Publisher.subscribe(subscriber), tiếp theo là mũi tên onSubscribe(subscription) từ Publisher sang Subscriber trước; vẽ request(n), cancel() ngược về Subscription.

**Script:**

Subscription là quan hệ theo dõi nguồn. Với hợp đồng Reactive Streams, Publisher báo onSubscribe đầu tiên, trao cho Subscriber một đối tượng Subscription. Người nhận dùng đối tượng đó để xin thêm dữ liệu hoặc hủy. Chúng ta chưa phát một onNext nào. Hãy nhìn hướng mũi tên: onSubscribe trao quyền điều khiển xuống dưới, còn request và cancel bắt đầu từ phía người nhận.

**Purpose:**

Giải thích bắt tay và hai hướng điều khiển theo hợp đồng RS, không giả lập phát khi đăng ký.

## Thời điểm bắt đầu phát và sự khác nhau giữa mô tả luồng với thực thi

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:46–06:00`

**Visual:**

Giữ onSubscribe ở đầu làn; gắn hai mốc người xem vào 09:00 và 09:02.

**Script:**

Khi hai người đăng ký ở hai thời điểm khác nhau, liệu cả hai đều xem lại dữ liệu cũ không?

**Purpose:**

Nối bước bắt tay với sự khác nhau của nguồn cold và hot.

### Scene 6 — Thời điểm bắt đầu phát và sự khác nhau giữa mô tả luồng với thực thi

**Time:** `06:00–06:58`

**Visual:**

Nguồn cold bên trái tạo nhánh 27→29 riêng cho mỗi subscriber; cảm biến hot bên phải tiếp tục chạy 27→29→33 khi người B vào lúc 09:02.

**Script:**

Nguồn cold thường bắt đầu công việc khi có người đăng ký, nên hai người có thể có hai chuỗi riêng. Nguồn hot có thể phát liên tục dù màn hình chưa mở; người B vào lúc chín giờ hai phút có thể bỏ lỡ số hai mươi bảy trước đó. Đây là cách hoạt động tùy nguồn và thư viện, không phải mọi luồng đều phát lại lịch sử. Khai báo pipeline cũng không chứng minh nó đã xử lý số đo.

**Purpose:**

Cho thấy hai lịch sử quan sát khác nhau và ranh giới giữa mô tả với thực thi.

## Lần theo một dòng sự kiện từ nguồn tới người nhận

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Gộp hai bảng về một timeline; đặt -300 lúc 09:01 và dấu hủy lúc 09:01:30.

**Script:**

Đã biết lúc nào bắt đầu nghe. Giờ lần theo từng số đo, cả phần tử bị lọc lẫn số đo đến sau khi người xem rời đi.

**Purpose:**

Chuẩn bị lần vết với thời gian, lọc và dừng theo dõi.

### Scene 7 — Lần theo một dòng sự kiện từ nguồn tới người nhận

**Time:** `07:12–08:10`

**Visual:**

Dựng bảng giờ/nguồn/sau kiểm tra/bảng theo dõi. Mở từng dòng: 09:00 27→27→bình thường, 09:01 -300→trống, 09:02 33→33→cảnh báo; thêm hủy lúc 09:01:30.

**Script:**

Lúc chín giờ, cảm biến gửi hai mươi bảy, bảng báo bình thường. Một phút sau, âm ba trăm bị loại ngay tại bước kiểm tra, không có sự kiện ở màn hình. Lúc chín giờ hai phút, ba mươi ba sẽ thành cảnh báo nếu còn người theo dõi. Nhưng nếu đã hủy lúc chín giờ một phút ba mươi, người đó có thể không nhận cảnh báo nữa. Tín hiệu đang nằm trong hàng đợi bất đồng bộ vẫn có thể đến trễ. Vết phải có thời gian và vòng đời.

**Purpose:**

Kiểm tra nơi lọc/đổi từng giá trị cùng hậu quả của thời điểm hủy.
