---
video:
  url: ""
---

# Back-off và quyền kiểm soát của ứng dụng

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

## Back-off như một cam kết thiết kế

<!-- VIDEO_SECTION -->

### Scene 1 — Mặc định chỉ tồn tại khi ứng dụng chưa quyết định

**Time:** `00:00–00:50`

**Visual:**

Sơ đồ quyết định bắt đầu bằng câu hỏi Ứng dụng đã có lựa chọn cho vai trò AcmeClient chưa? Nhánh có đi tới back off; nhánh chưa đi tới DefaultAcmeClient.

**Script:**

Back-off không chỉ là một annotation. Nó là cam kết thiết kế rằng mặc định dùng chung phải biến mất khi ứng dụng đã đưa ra lựa chọn tường minh cho cùng vai trò. Nhờ vậy auto-configuration có thể tiện dụng mà không khóa người dùng vào một cách triển khai. Khi xem xét một phần tích hợp, câu hỏi nên là ứng dụng đã sở hữu quyết định này chưa, chứ không chỉ là Boot có thể tạo bean hay không.

**Purpose:**

Định nghĩa back-off bằng quyền sở hữu quyết định, không bằng cú pháp annotation.

## Bean do ứng dụng định nghĩa giành quyền kiểm soát thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ sơ đồ quyết định chuyển sang hai trường hợp ApplicationContextRunner đặt cạnh nhau.

**Script:**

Một cam kết chỉ đáng tin khi ta thấy nó đổi kết quả thật của context.

**Purpose:**

Chuyển từ nguyên tắc thiết kế sang bằng chứng thực thi của back-off.

### Scene 2 — Chứng minh Bean của người dùng thắng giá trị mặc định

**Time:** `01:00–01:50`

**Visual:**

Trường hợp A không có cấu hình người dùng: DefaultAcmeClient xuất hiện. Trường hợp B thêm CustomAcmeClient: mặc định biến mất. Làm nổi bật ConditionalOnMissingBean trên phương thức @Bean.

**Script:**

EnableAutoConfiguration được xử lý sau các bean definition phía ứng dụng, nên missing-bean condition có cơ hội nhìn thấy lựa chọn của người dùng. Ta không chỉ đọc annotation rồi tin. Hãy chạy hai context nhỏ: một context không có AcmeClient phải nhận mặc định; context có CustomAcmeClient phải chỉ còn bean của người dùng. Sự thay đổi đó là bằng chứng trực tiếp cho quy ước back-off.

**Purpose:**

Biến việc người dùng ghi đè thành quan sát có thể lặp lại bằng kiểm thử context tập trung.

## Độ cụ thể của Bean Type và phạm vi Condition nhìn thấy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Phóng to chữ ký phương thức @Bean và tách hai nhãn: type mà condition kiểm tra và return type được khai báo.

**Script:**

Nhưng back-off có thể sai dù annotation nhìn có vẻ hợp lý nếu ta nhắm sai type.

**Purpose:**

Mở vấn đề khả năng nhìn thấy type mà không làm mất mạch quyền kiểm soát của ứng dụng.

### Scene 3 — Type của Condition và type của Bean là hai quyết định khác nhau

**Time:** `02:00–02:50`

**Visual:**

Hiển thị phương thức @Bean trả về DefaultAcmeClient với ConditionalOnMissingBean(AcmeClient.class). Bên cạnh là sơ đồ: AcmeClient dùng cho back-off; DefaultAcmeClient vẫn hiện trong metadata type của bean definition.

**Script:**

Ở đây cần tách hai việc. Condition phải kiểm tra đúng vai trò mà người dùng có thể thay thế, nên mục tiêu là AcmeClient. Trong khi đó, return type cụ thể DefaultAcmeClient giúp những condition khác cần quan sát cách triển khai có thông tin chính xác hơn. Nếu gộp hai khái niệm này, ta rất dễ viết condition chỉ back off cho đúng DefaultAcmeClient và bỏ sót một cách triển khai AcmeClient khác của người dùng.

**Purpose:**

Làm rõ sự khác biệt giữa type mà condition kiểm tra và độ cụ thể của type trong bean definition.

## Mặc định có thể tùy biến và chính sách bị áp đặt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Đặt hai cột Mặc định tốt và Chính sách áp đặt, dùng cùng ví dụ AcmeClient.

**Script:**

Khi target đã đúng, ta vẫn cần hỏi thiết kế có thực sự để lại đường tùy biến cho ứng dụng hay không.

**Purpose:**

Chuyển từ cơ chế back-off sang chất lượng của điểm mở rộng.

### Scene 4 — Mặc định tốt khác chính sách áp đặt

**Time:** `03:00–03:50`

**Visual:**

Cột trái: không có bean thì tạo DefaultAcmeClient, có bean tùy chỉnh thì dùng bean tùy chỉnh. Cột phải: luôn tạo DefaultAcmeClient, xuất hiện cảnh báo mơ hồ và phải loại trừ cấu hình.

**Script:**

Mặc định tốt là lựa chọn tiện lợi nhưng có đường thay thế được hỗ trợ. Chính sách áp đặt thì ngược lại: bean mặc định luôn xuất hiện và buộc ứng dụng phải đấu với phần tích hợp hoặc loại trừ cả khối cấu hình. Không phải bean nào cũng cần cho phép thay thế theo mọi cách, nhưng điểm mở rộng phải có chủ đích và dễ hiểu.

**Purpose:**

Giúp người học đánh giá thiết kế back-off ở bề mặt cấu hình của phần tích hợp, không chỉ ở mức annotation.

## Các mẫu lỗi thường gặp khi thiết kế Back-off

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Chuyển từ bảng thiết kế sang Condition Evaluation Report với một missing-bean condition bị đánh dấu nhắm sai type.

**Script:**

Nếu mặc định không chịu nhường chỗ, đừng đoán từ context cuối cùng. Hãy quay lại dữ kiện mà condition thật sự nhìn thấy.

**Purpose:**

Nối quy ước thiết kế với quy trình chẩn đoán có bằng chứng.

### Scene 5 — Đọc lỗi từ góc nhìn của condition

**Time:** `04:00–04:50`

**Visual:**

Danh sách kiểm tra: type/tên mục tiêu, bean definition đã có, thời điểm đánh giá, các condition chồng lấn. Làm nổi bật một dòng report giải thích trường hợp không khớp hoặc khớp ngoài dự kiến.

**Script:**

Lỗi back-off thường rơi vào vài nhóm quen thuộc: condition nhắm sai type hoặc tên, definition cần thiết chưa có ở thời điểm đánh giá, nhiều bean mặc định dùng missing-bean condition chồng lấn, hoặc phần tích hợp đăng ký bean vô điều kiện. Cách chẩn đoán chắc nhất là đọc Condition Evaluation Report cùng tập bean definition tại thời điểm condition chạy. Sau đó tái hiện cách ghi đè được hỗ trợ bằng kiểm thử context để chứng minh bean mặc định thật sự biến mất.

**Purpose:**

Cung cấp quy trình ngắn để tìm nguyên nhân thay vì sửa annotation theo cảm tính.
