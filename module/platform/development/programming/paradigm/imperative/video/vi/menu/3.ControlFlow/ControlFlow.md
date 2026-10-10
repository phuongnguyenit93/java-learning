---
video:
  url: ""
---

# Điều khiển luồng bằng rẽ nhánh và vòng lặp

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

## Vai trò của điều khiển luồng trong chương trình mệnh lệnh

<!-- VIDEO_SECTION -->

### Scene 1 — Vai trò của điều khiển luồng trong chương trình mệnh lệnh

**Time:** 00:00–01:20

**Visual:** Biến chuỗi thẳng thành cây có nút CHECK dẫn tới ACCEPT hoặc REJECT; bên cạnh vẽ vòng quay qua ba yêu cầu giao dịch.

**Script:** Điều khiển luồng trả lời câu hỏi lệnh nào chạy tiếp. Không phải dòng nào cũng được thực hiện đúng một lần. Khi tài khoản thiếu tiền, nhánh từ chối cần bỏ qua phép trừ. Khi có danh sách yêu cầu, một nhóm lệnh được lặp qua từng phần tử. Vẫn là mô hình trước và sau trạng thái, chỉ bổ sung đường đi thực thi. Từ đây, ta không chỉ xem kết quả của phép tính mà phải xem nhánh nào thực sự được chọn.

**Purpose:** Đưa nhánh và vòng lặp vào mô hình trạng thái đã học.

## Lựa chọn nhánh dựa trên điều kiện

<!-- VIDEO_SECTION -->

### Transition

**Time:** 01:20–01:33

**Visual:** Giữ cây CHECK→ACCEPT/REJECT, thêm cổng `amount>0` trước nhánh rút và khoanh yêu cầu âm chưa được xử lý.

**Script:** Điều kiện chọn nhánh phải đọc số dư lúc nào, và thiếu điều kiện nào sẽ nguy hiểm?

**Purpose:** Từ ý tưởng điều khiển luồng dẫn sang tiền điều kiện chọn nhánh rút tiền.

### Scene 2 — Lựa chọn nhánh dựa trên điều kiện

**Time:** 01:33–02:48

**Visual:** Hai hình thoi nối nhau: amount>0 rồi balance>=amount. Thử rút -10 và rút 90 từ 70, tô hai kết quả từ chối khác nhau.

**Script:** Chỉ kiểm tra đủ số dư chưa đủ, vì số tiền âm có thể lọt qua và trừ một số âm lại làm tài khoản tăng. Ta cần xác nhận số tiền dương trước, sau đó so với số dư tại thời điểm kiểm tra. Đặt yêu cầu âm vào nhánh từ chối sớm; với yêu cầu 90 khi chỉ có 70 cũng từ chối. Cả hai không được chạm lệnh cập nhật. Đó là cách điều kiện bảo vệ bất biến của trạng thái.

**Purpose:** Dùng hai loại đầu vào sai để giải thích nhánh bảo vệ thao tác ghi.

## Lặp lại thao tác theo điều kiện hoặc tập phần tử

<!-- VIDEO_SECTION -->

### Transition

**Time:** 02:48–03:01

**Visual:** Giữ hai cổng xác thực trên sơ đồ, xếp ba phiếu thu thành hàng và tô mũi tên quay lại cổng cho lần tiếp theo.

**Script:** Một giao dịch đã xử lý; làm sao áp dụng cùng chuỗi kiểm tra cho nhiều phần tử?

**Purpose:** Từ một nhánh hợp lệ tới sự lặp lại cùng công việc trên nhiều dữ liệu.

### Scene 3 — Lặp lại thao tác theo điều kiện hoặc tập phần tử

**Time:** 03:01–04:16

**Visual:** Cho con trỏ duyệt [10,20,5] và ô sum từ 0 lên 10 rồi 30 rồi 35 sau mỗi bước.

**Script:** Vòng lặp là cách thực hiện lại một công việc trên những phần tử khác nhau. Tôi chọn khoản thu 10 và cộng vào tổng 0 để được 10; sang 20 thì tổng thành 30; sang 5 thì tổng là 35. Những con số trung gian giúp chúng ta hiểu phép cộng dồn, thay vì chỉ tin đáp số. Có vòng lặp biết số lần trước, cũng có vòng lặp dựa vào điều kiện. Cần xác định chỉ số hoặc trạng thái nào tiến triển.

**Purpose:** Làm phép cộng dồn nhìn thấy được qua từng lần lặp.

## Theo dõi trạng thái và số lần lặp

<!-- VIDEO_SECTION -->

### Transition

**Time:** 04:16–04:29

**Visual:** Gộp các phiếu `[10,20,5]` thành ba dòng, giữ tổng 35 cuối bảng và mở thêm cột index trống.

**Script:** Tổng 35 vẫn có thể tình cờ đúng dù con trỏ đọc sai; ta cần kiểm tra từng lần lặp.

**Purpose:** Đưa phép cộng dồn sang câu hỏi số lần lặp và phần tử đã được duyệt.

### Scene 4 — Theo dõi trạng thái và số lần lặp

**Time:** 04:29–05:44

**Visual:** Vẽ bảng iteration 1/2/3, index 0/1/2, item 10/20/5 và sum 10/30/35; diễn lại lỗi tăng index trước khi đọc.

**Script:** Hãy tạm dừng ở cuối vòng thứ hai. Ta đã đọc 10 và 20 nên tổng là 30; khoản 5 còn ở phía trước. Nếu tăng chỉ số quá sớm, ta có thể bỏ qua phần tử 10. Nếu tăng quá muộn, lại có thể cộng một phần tử hai lần. Bảng ghi vòng, chỉ số, phần tử và tổng giúp bắt các lỗi lệch một bước tốt hơn việc chỉ nhìn hàng cuối.

**Purpose:** Cho thấy bằng chứng tại thời điểm lỗi off-by-one xảy ra.

## Điều kiện dừng và vòng lặp không kết thúc

<!-- VIDEO_SECTION -->

### Transition

**Time:** 05:44–05:57

**Visual:** Dừng con trỏ ở index 2; chuyển tiêu điểm sang biến đếm `remaining=3` và dấu hỏi tại điều kiện `>0`.

**Script:** Vòng lặp phải tiến triển, nhưng điều kiện dừng nào cho biết nó sẽ kết thúc?

**Purpose:** Nối vết lặp có index với điều kiện tiến triển cần thiết để dừng.

### Scene 5 — Điều kiện dừng và vòng lặp không kết thúc

**Time:** 05:57–07:12

**Visual:** Chiếu while remaining>0 và ba bước 3→2→1→0; bên cạnh giữ remaining=3 không đổi, con trỏ vòng mãi.

**Script:** Để tin một vòng lặp kết thúc, ta cần biết điều kiện tiếp tục và cách điều kiện trở nên sai. Với remaining bằng 3, giảm dần tới 0 khiến vòng dừng. Nếu quên giảm, chương trình có nguy cơ chạy mãi. Giảm quá tay còn có thể phá điều kiện như remaining không được âm. Dừng đúng và bảo vệ dữ liệu là hai yêu cầu phải cùng được xem xét khi viết vòng lặp.

**Purpose:** So sánh vòng hữu hạn, vòng không tiến triển và biến bị cập nhật sai.

## Theo dõi một luồng có rẽ nhánh và lặp

<!-- VIDEO_SECTION -->

### Transition

**Time:** 07:12–07:25

**Visual:** Giữ trạng thái `remaining=0` kèm STOP, thay danh sách thu bằng ba thẻ giao dịch `[+20,-30,-200]` bên cạnh số dư 100.

**Script:** Hãy ghép điều kiện và vòng lặp để xử lý chuỗi giao dịch gồm cả lần từ chối.

**Purpose:** Chuyển từ điều kiện dừng sang kết hợp vòng lặp với các nhánh chấp nhận và từ chối.

### Scene 6 — Theo dõi một luồng có rẽ nhánh và lặp

**Time:** 07:25–08:40

**Visual:** Cho ba thẻ +20,-30,-200 đi qua CHECK. Bảng lần lượt 100→120, 120→90, 90→90; đánh dấu lần ba REJECT.

**Script:** Bây giờ chúng ta chạy trọn ba giao dịch từ số dư 100. Cộng 20 được 120, rút 30 được 90, còn rút 200 bị từ chối nên vẫn 90. Con trỏ đã xét cả ba mục, nhưng số dư chỉ đổi ở hai mục đầu. Đó là điều bảng vết phải diễn đạt: số lần thực hiện không bằng số lần ghi thành công. Đổi thứ tự yêu cầu đôi khi sẽ đổi cả nhánh được phép chạy.

**Purpose:** Tổng hợp sequencing, branching, repetition và tác động lên trạng thái.
