---
video:
  url: ""
---

# Nền tảng Auto-configuration

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

## Auto-configuration là gì và vì sao Spring Boot cần nó?

<!-- VIDEO_SECTION -->

### Scene 1 — Từ cấu hình lặp lại đến mặc định có điều kiện

**Time:** `00:00–00:50`


**Visual:**

Chia màn hình: bên trái là ba ứng dụng đều tự tạo AcmeClient; bên phải gom phần lặp thành một khối Auto-configuration. Lần lượt bật các nhãn classpath, property, bean, resource và loại ứng dụng trước khi khối mặc định đi vào ApplicationContext.

**Script:**

Hãy bắt đầu từ một việc rất thực tế: nhiều ứng dụng dùng cùng một thư viện thường lặp lại cùng phần cấu hình hạ tầng. Spring Boot gom phần lặp đó thành auto-configuration, nhưng không có nghĩa cứ thấy dependency là Boot tạo bean. Boot nhìn vào trạng thái thật của ứng dụng rồi mới quyết định cấu hình mặc định có phù hợp hay không. Vì thế auto-configuration là cấu hình có điều kiện, chứ không phải cơ chế đoán tùy ý.

**Purpose:**

Đặt đúng mô hình nền: auto-configuration giải quyết phần nối hạ tầng lặp lại bằng các mặc định chỉ tham gia khi trạng thái ứng dụng cho phép.

## Spring Framework Configuration và Spring Boot Auto-configuration khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`


**Visual:**

Thu nhỏ khối Auto-configuration và đặt nó lên trên một sơ đồ Spring Framework container.

**Script:**

Vậy Boot có tạo ra một container khác không? Không. Phần quyết định của Boot vẫn chạy trên cơ chế cấu hình và bean của Spring Framework.

**Purpose:**

Nối auto-configuration với nền tảng Spring Framework mà không trộn lẫn trách nhiệm hai lớp.

### Scene 2 — Boot chọn, Spring container thực thi

**Time:** `01:00–01:50`


**Visual:**

Hiển thị hai tầng. Tầng dưới gồm ApplicationContext, class cấu hình, phương thức @Bean và bean definition. Tầng trên gồm AutoConfiguration.imports, condition, back-off và ordering. Một mũi tên từ tầng Boot đi xuống tập bean definition.

**Script:**

Spring Framework chịu trách nhiệm đọc cấu hình, đăng ký bean definition và quản lý vòng đời bean trong ApplicationContext. Spring Boot xây trên nền đó. Phần Boot là tìm candidate, sắp xếp, đánh giá condition và quyết định bean definition mặc định nào được phép đóng góp. Nói ngắn gọn: Spring quản lý bean; Boot quyết định khi nào một gói cấu hình mặc định nên tham gia.

**Purpose:**

Phân biệt cơ chế container của Spring với lớp lựa chọn theo quy ước của Spring Boot.

## Giá trị mặc định, Back-off và quyền kiểm soát của ứng dụng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`


**Visual:**

Phóng to một ô DefaultAcmeClient và đặt cạnh CustomAcmeClient do ứng dụng cung cấp.

**Script:**

Nếu Boot đã biết cách cung cấp một mặc định, câu hỏi tiếp theo là: khi ứng dụng tự quyết định rồi thì ai được ưu tiên?

**Purpose:**

Chuyển từ cơ chế lựa chọn sang nguyên tắc giữ quyền kiểm soát cho ứng dụng.

### Scene 3 — Mặc định phải biết nhường chỗ

**Time:** `02:00–02:50`


**Visual:**

Sơ đồ hai trạng thái: không có AcmeClient thì DefaultAcmeClient xuất hiện; có CustomAcmeClient thì bean mặc định bị gạch bỏ. Đặt nhãn back-off giữa hai trạng thái.

**Script:**

Auto-configuration tốt không ép ứng dụng dùng giá trị mặc định. Nếu ứng dụng chưa chọn gì, Boot có thể cung cấp DefaultAcmeClient. Nhưng khi người dùng đã đăng ký một AcmeClient phù hợp, mặc định nên biến mất. Đó là back-off. Quyền kiểm soát còn có thể thể hiện qua property, loại trừ tường minh hoặc condition khác. Điều cần nhớ là được auto-configure không bao giờ đồng nghĩa với luôn được tạo.

**Purpose:**

Làm rõ back-off như nguyên tắc thiết kế giúp mặc định tiện dụng nhưng không xâm lấn lựa chọn của ứng dụng.

## Luồng Auto-configuration từ đầu đến cuối

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`


**Visual:**

Ghép ba mảnh vừa học — khám phá candidate, condition và back-off — thành một dòng thời gian khởi động.

**Script:**

Bây giờ ta đã có các mảnh chính. Hãy xếp chúng đúng thứ tự để biết một bean mặc định đi từ dependency đến ApplicationContext như thế nào.

**Purpose:**

Tổng hợp các khái niệm nền thành một luồng duy nhất trước khi sang các chương chuyên sâu.

### Scene 4 — Bảy bước của quyết định

**Time:** `03:00–03:50`


**Visual:**

Dòng thời gian bảy bước: SpringBootApplication bật auto-configuration, khám phá candidate, sắp xếp, đánh giá condition, đóng góp bean definition, back-off, tạo bean. Tô riêng bước khám phá và bước khớp condition; thêm chú thích thứ tự xử lý cấu hình không phải thứ tự tạo bean.

**Script:**

Luồng có thể nhớ theo bảy bước. Ứng dụng bật auto-configuration. Boot tìm candidate, sắp xếp chúng rồi đánh giá condition. Cấu hình khớp đóng góp bean definition, quy tắc back-off cho lựa chọn của ứng dụng quyền ưu tiên, và sau cùng Spring container mới tạo bean. Hai ranh giới rất dễ nhầm: tìm thấy candidate chưa có nghĩa candidate sẽ khớp, và thứ tự xử lý auto-configuration không phải thứ tự khởi tạo bean.

**Purpose:**

Tạo mô hình tư duy từ đầu đến cuối làm bản đồ cho toàn module và nhấn mạnh hai ranh giới dễ nhầm nhất.
