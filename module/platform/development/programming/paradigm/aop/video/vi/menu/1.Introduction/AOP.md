---
video:
  url: ""
---

# Lập trình hướng khía cạnh (AOP): Vấn đề và mô hình tư duy

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


## AOP: Khái niệm, phạm vi và mối quan tâm xuyên suốt

<!-- VIDEO_SECTION -->

### Scene 1 — AOP: Khái niệm, phạm vi và mối quan tâm xuyên suốt

**Time:** `00:00–01:07`

**Visual:**

Chia màn hình: bên trái TransferService.transferFunds(100), bên phải ba ô dịch vụ khác nhau. Lần lượt hiện biểu tượng đồng hồ ở phía trên các ô, ghi chú "đo thời gian ≠ nghiệp vụ chuyển tiền".

**Script:**

Nhìn vào hàm chuyển tiền này nhé. Nhiệm vụ chính của nó là xử lý giao dịch, chứ không phải chọn định dạng báo cáo thời gian. Nhưng dịch vụ hóa đơn hay kho cũng cần cùng một quy tắc đo. Lập trình hướng khía cạnh giúp ta diễn đạt mối quan tâm xuất hiện ở nhiều thành phần và những điểm thực thi mà nó sẽ tham gia. Phần nghiệp vụ vẫn là trung tâm. AOP là cách tổ chức hành vi, không phải cách thay thế đối tượng.

**Purpose:**

Giúp người chưa biết AOP phân biệt logic nghiệp vụ và chính sách xuyên suốt trước khi gặp thuật ngữ.


## Sự lặp lại của hành vi dùng chung trong nhiều thành phần

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

Từ ba thẻ dịch vụ của cảnh mở, zoom vào đồng hồ gắn trên từng thẻ; nhân ba đoạn startTimer/finally logElapsed và tô cùng một màu để thấy chúng bị sao chép.

**Script:**

Đã tách phần chuyển tiền khỏi việc đo thời gian, giờ hãy xem điều gì xảy ra khi chính sách ấy bị sao chép qua nhiều dịch vụ.

**Purpose:**

Biến nhận xét nhiều dịch vụ cần đo thời gian thành bằng chứng về ba bản triển khai dễ lệch nhau.

### Scene 1 — Sự lặp lại của hành vi dùng chung trong nhiều thành phần

**Time:** `01:19–02:26`

**Visual:**

Nhân một cặp dòng startTimer()/finally logElapsed() sang ba cột chuyển tiền, tạo hóa đơn, giữ hàng; tô vàng những dòng trùng nhau.

**Script:**

Thử nhìn ba đoạn code này. Chúng đều mở đồng hồ, chạy nghiệp vụ rồi ghi thời gian. Hôm nay đổi định dạng log, ta phải sửa cả ba. Ngày mai thêm dịch vụ mới mà quên đoạn finally, những lần thất bại lại biến mất khỏi báo cáo. Vấn đề không phải chỉ là gõ nhiều chữ, mà là một chính sách chung bị rải ra khắp nơi và rất dễ thiếu nhất quán.

**Purpose:**

Biểu diễn được hệ quả của mã lặp và đường lỗi bị bỏ sót.


## Giải pháp tường minh bằng helper, wrapper và decorator

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Giữ ba cột code đo thời gian vừa bị tô vàng; gom các dòng giống nhau vào ba mô hình helper, wrapper và decorator, vẽ lại đường caller gọi target.

**Script:**

Đã thấy sự trùng lặp, chúng ta nên thử những cách viết thông thường trước khi đưa thêm khái niệm mới.

**Purpose:**

Nối bằng chứng trùng lặp với ba lựa chọn tường minh trước khi giới thiệu cơ chế AOP.

### Scene 1 — Giải pháp tường minh bằng helper, wrapper và decorator

**Time:** `02:38–03:45`

**Visual:**

Ba hình nối tiếp: gọi helper đo thời gian ngay tại caller; wrapper có mũi tên delegate; decorator giữ nguyên interface của target. Tô đậm đường gọi tường minh.

**Script:**

Không phải cứ thấy trùng là phải dùng AOP. Ta có thể gọi helper trực tiếp. Ta có thể bọc một lời gọi trong wrapper. Hoặc tạo decorator cùng hợp đồng với đối tượng gốc rồi ủy quyền công việc. Cả ba cách đều có điểm mạnh: mở code ra là thấy hành vi thêm vào nằm ở đâu. Nếu bài toán nhỏ, sự tường minh này thường đáng giá hơn một cơ chế chọn điểm tự động.

**Purpose:**

Dạy giải pháp tường minh một cách công bằng, không tô xấu OOP hay wrapper.


## Giới hạn của lời gọi thủ công và chính sách phân tán

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Giữ bảng ba đường gọi từ cảnh helper; làm mờ call thứ ba và đánh dấu MISSING TIMER, rồi kéo một nhánh exception khỏi khối không có finally.

**Script:**

Nhưng nếu chính sách phải áp dụng ở rất nhiều nơi, sự tường minh ở từng nơi lại có một điểm yếu.

**Purpose:**

Chuyển từ lợi ích dễ thấy call path sang ca phủ thiếu và bỏ sót đường lỗi.

### Scene 1 — Giới hạn của lời gọi thủ công và chính sách phân tán

**Time:** `03:57–05:04`

**Visual:**

Cố ý xóa helper khỏi lời gọi thứ ba, rồi chạy bảng trace: hai bản ghi có giờ, một bản ghi trống. Đánh dấu nhánh exception thiếu finally.

**Script:**

Ví dụ có một phương thức mới nhưng người viết quên gọi helper. Nghiệp vụ vẫn chạy, còn chính sách audit thì âm thầm không chạy. Nếu chỉ ghi thời gian sau khi gọi thành công, những lời gọi ném lỗi cũng sẽ bị bỏ quên. Wrapper hạn chế được phần nào, nhưng ta vẫn phải nối đúng từng đường gọi. Điều ta cần là quy tắc chọn phạm vi có thể kiểm tra rõ ràng.

**Purpose:**

Cho thấy lỗi thiếu coverage và sai nhánh lỗi thay vì chỉ phàn nàn về code lặp.


## Mô hình ghép logic nghiệp vụ với hành vi xuyên suốt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Trên bảng trace có một dòng không được đo, đặt một khung selection rule phủ đúng các operation cần theo dõi; thêm lane renderPage nằm ngoài khung.

**Script:**

Từ nhu cầu chọn phạm vi đó, hãy dựng một mô hình ghép hành vi dùng chung với công việc gốc.

**Purpose:**

Chuyển nhu cầu kiểm tra coverage thành mô hình chính sách chọn điểm.

### Scene 1 — Mô hình ghép logic nghiệp vụ với hành vi xuyên suốt

**Time:** `05:16–06:23`

**Visual:**

Hoạt họa caller → ranh giới được chọn → bật đồng hồ → transferFunds → ghi thời gian → caller. Dòng renderPage chạy thẳng không có timer.

**Script:**

Giả sử chính sách của chúng ta chỉ áp dụng cho những thao tác dịch vụ thanh toán. Khi transferFunds đi qua ranh giới phù hợp, phần đo thời gian xuất hiện quanh nghiệp vụ. Còn renderPage nằm ngoài phạm vi thì chạy như cũ. Như vậy ta không phải đưa quy tắc định dạng log vào hàm chuyển tiền. Nhưng vẫn phải biết tiêu chí chọn điểm, đường đi của lời gọi và kết quả thực tế sau khi ghép.

**Purpose:**

Trực quan hóa việc chọn điểm và ghép hành vi trước khi đi vào từ vựng AOP.


## Trường hợp phù hợp và giới hạn ban đầu của AOP

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:23–06:35`

**Visual:**

Giữ hai lane transferFunds MATCH và renderPage NO MATCH; kéo vào bảng phân loại gồm cross-cutting timing và core debit/credit.

**Script:**

Nhìn sơ đồ thì có vẻ gọn, nhưng ranh giới này có phù hợp với mọi yêu cầu hay không?

**Purpose:**

Buộc người xem phân biệt nơi AOP áp dụng với việc có nên đưa quy tắc nghiệp vụ vào advice.

### Scene 1 — Trường hợp phù hợp và giới hạn ban đầu của AOP

**Time:** `06:35–07:42`

**Visual:**

Bảng hai bên: đồng hồ, mã tương quan, audit an toàn ở bên trái; kiểm tra số dư, trừ và cộng tiền, xử lý bù trừ ở bên phải.

**Script:**

Đo thời gian hoặc gắn mã tương quan thường lặp lại ở nhiều dịch vụ và có thể dùng chung một quy tắc. Ngược lại, quyết định khi nào được trừ tiền, cộng tiền hay bù trừ chính là luồng nghiệp vụ. Nếu giấu các bước ấy vào aspect, người đọc khó biết tiền thực sự di chuyển lúc nào. AOP hữu ích khi ta chọn được concern xuyên suốt và kiểm chứng chính xác nơi nó áp dụng.

**Purpose:**

Đặt giới hạn cho AOP dựa trên ý nghĩa nghiệp vụ, không phải sở thích cú pháp.


## AOP trong quan hệ với trách nhiệm đối tượng và nguyên lý phân tách mối quan tâm

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:54`

**Visual:**

Từ bảng timing/transfer, giữ riêng hộp TransferService và InvoiceService; vẽ dải đo thời gian đi ngang các cạnh gọi mà không xâm nhập hộp nghiệp vụ.

**Script:**

Để phân biệt ranh giới ấy, ta cần liên hệ lại cách các đối tượng đang chia trách nhiệm.

**Purpose:**

Kết nối quyết định dùng aspect với trách nhiệm OOP và hai trục phân chia khác nhau.

### Scene 1 — AOP trong quan hệ với trách nhiệm đối tượng và nguyên lý phân tách mối quan tâm

**Time:** `07:54–09:01`

**Visual:**

Ba ô Order, Payment, Inventory giữ thuật toán riêng; vẽ một dải mờ đo thời gian đi ngang các mũi tên gọi, không nằm bên trong ô nghiệp vụ.

**Script:**

Hướng đối tượng giúp ta chọn nơi giữ trạng thái và hành vi chính. Phân tách mối quan tâm nhắc rằng không nên trộn những trách nhiệm ít liên quan. AOP xử lý một tình huống đặc biệt: cùng một chính sách cần tham gia tại nhiều điểm thực thi. Nó bổ sung cho cách chia trách nhiệm của đối tượng, chứ không trả lời thay câu hỏi ai sở hữu quy tắc chuyển tiền.

**Purpose:**

Nối với OOP và separation of concerns ở mức người mới tiếp cận được.


## Từ nhu cầu xuyên suốt đến thuật ngữ, cơ chế ghép và quyết định thiết kế

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:01–09:13`

**Visual:**

Thu dải đo thời gian về một thẻ concern; nối tiếp bằng các thẻ aspect, join point, advice, proxy/weaver rồi đặt câu hỏi cuối wrapper hay aspect.

**Script:**

Khi đã hiểu AOP bổ trợ cho điều gì, ta mới cần gọi tên chính xác các bộ phận trong mô hình.

**Purpose:**

Biến câu chuyện hiện tại thành lộ trình bốn video, gắn thuật ngữ sắp học vào ví dụ đã nhìn thấy.

### Scene 1 — Từ nhu cầu xuyên suốt đến thuật ngữ, cơ chế ghép và quyết định thiết kế

**Time:** `09:13–10:20`

**Visual:**

Tuyến đường bốn điểm hiện dần: concern lặp lại → aspect/join point/pointcut/advice → trace kết quả và lỗi → proxy/weaver → lựa chọn thiết kế.

**Script:**

Chúng ta sẽ dùng một ví dụ xuyên suốt: chuyển tiền và đo thời gian. Đầu tiên xác định chính sách dùng chung. Sau đó học tên của các điểm có thể chọn và phần hành vi thêm vào. Tiếp theo lần theo lời gọi khi thành công, thất bại, hoặc khi around advice bỏ qua hay chạy lặp target. Cuối cùng so sánh weaving với proxy để chọn giải pháp phù hợp, không biến bài này thành hướng dẫn cấu hình Spring.

**Purpose:**

Cho người học lộ trình có quan hệ nhân quả và giới hạn của chuỗi video.
