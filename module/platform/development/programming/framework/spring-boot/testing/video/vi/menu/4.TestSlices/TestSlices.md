---
video:
  url: ""
---

# Chọn Boot test slice nhỏ nhất nhưng đủ dùng

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Boot test slice giải quyết vấn đề gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Boot test slice giải quyết vấn đề gì?

**Time:** `00:00–01:00`

**Visual:**

Bắt đầu từ full application graph, làm mờ layer không liên quan và chỉ giữ focused framework boundary cùng test infrastructure cần để chạy nó.

**Script:**

Boot test slice nạp một application context được giới hạn có chủ đích cho một loại hành vi. Thay vì khởi động mọi application bean và mọi auto-configuration đúng, slice chọn component cùng test hạ tầng liên quan tới layer tập trung như MVC, WebFlux, JPA hoặc JDBC. Lợi ích không chỉ là tốc độ. Context nhỏ làm ranh giới cần kiểm thử rõ hơn và giảm lỗi không liên quan. Đánh đổi là hành vi phụ thuộc layer bị loại bỏ sẽ không thể được slice đó chứng minh.

**Purpose:**

Giải thích bài toán mà test slice giải quyết: giữ lại framework behavior cần kiểm thử nhưng loại các application layer và infrastructure không liên quan.

## Slice giới hạn component scanning và phạm vi context như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:15`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: show component scanning entering a filter gate: allowed slice component types pass; unrelated services/repositories/configuration stay outside the context.

**Script:**

Slice hữu ích vì nó thu hẹp scope, nên trước hết cần xem scanning và filtering rule xác định thứ gì ở lại trong boundary đó.

**Purpose:**

Biến slice boundary trừu tượng thành component filter quyết định application bean nào được giữ lại.

### Scene 1 — Slice giới hạn component scanning và phạm vi context như thế nào?

**Time:** `01:15–02:07`

**Visual:**

Hiện component scanning đi qua filter gate: component type mà slice cho phép được đi vào; service/repository/configuration không liên quan ở ngoài context.

**Script:**

Slice annotation dùng type-exclusion filter và quy tắc scanning tập trung để chỉ phát hiện application component đúng. Web slice chẳng hạn chọn hạ tầng hướng tới controller thay vì mọi service/repository trong application. Giới hạn này là chủ đích. Khi thiếu đối tượng cộng tác, hãy hỏi trước liệu slice có được thiết kế để loại component đó không, thay vì lập tức coi bean bị thiếu là lỗi của application.

**Purpose:**

Cho thấy scanning và type-exclusion rule của slice chủ động giới hạn application component nào được đưa vào context.

## Slice chọn test auto-configuration theo mục tiêu như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:22`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: under the filtered component set, reveal the curated test auto-configuration imported by the slice annotation.

**Script:**

Lọc application component mới chỉ là một nửa; Boot còn import purpose-specific test auto-configuration để phần còn lại của slice hoạt động đúng.

**Purpose:**

Nối application component đã lọc với curated test auto-configuration làm slice hoạt động.

### Scene 1 — Slice chọn test auto-configuration theo mục tiêu như thế nào?

**Time:** `02:22–03:14`

**Visual:**

Under the filtered component set, reveal the curated test auto-configuration imported by the slice annotation.

**Script:**

Mỗi slice import một tập test/application auto-configuration được chọn lọc cho mục tiêu của nó. Danh sách chính xác khác nhau theo annotation và được Boot ghi rõ trong phụ lục test auto-configuration. Vì vậy slice vẫn nhận biết Boot dù không phải full `@SpringBootTest` context: phần Boot configuration đúng vẫn được kích hoạt, còn auto-configuration không liên quan bị loại có chủ đích.

**Purpose:**

Làm rõ slice auto-configuration để phân biệt component filtering với hạ tầng mà Boot cố ý import cho slice đó.

## Vì sao một test nên bắt đầu từ một annotation `@...Test` duy nhất?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:14–03:29`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: show one `@...Test` annotation as the root boundary, then add optional `@AutoConfigure...`, `@ImportAutoConfiguration`, `@Import`, or test beans around it instead of stacking another slice.

**Script:**

Mỗi slice đã định nghĩa một boundary và infrastructure set nhất quán, nên bước kế tiếp là bắt đầu từ một slice thay vì chồng nhiều slice lên nhau.

**Purpose:**

Biến curated slice model thành quy tắc composition: giữ một slice làm boundary chính rồi bổ sung hỗ trợ có mục tiêu, thay vì chồng nhiều slice definition cạnh tranh nhau.

### Scene 1 — Vì sao một test nên bắt đầu từ một annotation `@...Test` duy nhất?

**Time:** `03:29–04:27`

**Visual:**

Hiện một annotation `@...Test` làm root boundary; sau đó bổ sung `@AutoConfigure...`, `@ImportAutoConfiguration`, `@Import` hoặc test bean khi cần thay vì chồng thêm slice khác.

**Script:**

Boot không hỗ trợ dùng nhiều annotation slice `@...Test` trên cùng một test. Hãy chọn một slice làm ranh giới kiểm thử chính thay vì chồng nhiều slice với nhau. Hãy chọn slice đúng nhất với hành vi cần kiểm tra, rồi bổ sung hỗ trợ từ slice khác thông qua annotation `@AutoConfigure...` tương ứng khi Boot cung cấp. Chỉ dùng `@ImportAutoConfiguration`, `@Import` cho user configuration hoặc mock/test bean khi test thực sự cần phần hỗ trợ bổ sung đó.

**Purpose:**

Củng cố quy tắc bắt đầu từ một `@...Test` slice để focused test không vô tình biến thành full context chắp vá do stack nhiều slice annotation.

## Chọn slice nhỏ nhất nhưng vẫn chứng minh được hành vi như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:27–04:42`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: use an assertion-driven decision tree: controller mapping → web slice; JPA query → data slice; cross-layer startup/wiring → full context.

**Script:**

Khi đã chọn một slice, hãy tinh chỉnh quyết định bằng cách tìm boundary nhỏ nhất vẫn chứng minh đúng collaboration cần thiết.

**Purpose:**

Chuyển từ “một slice” sang “slice vừa đủ” bằng cách kiểm tra mọi collaboration mà assertion cần có còn nằm trọn trong boundary đã chọn hay không.

### Scene 1 — Chọn slice nhỏ nhất nhưng vẫn chứng minh được hành vi như thế nào?

**Time:** `04:42–05:43`

**Visual:**

Dùng decision tree theo assertion: controller mapping → web slice; JPA query → data slice; startup/wiring liên tầng → full context.

**Script:**

Hãy bắt đầu từ hành vi quan sát được thay vì cấu trúc package production. Nếu cần chứng minh controller mapping và serialization, web slice thường đủ. Nếu cần JPA mapping/query, data slice đúng ranh giới hơn. Nếu cần khởi động hoặc wiring liên tầng, dùng full context. Slice nhỏ nhất hữu ích là context nhỏ nhất vẫn chứa mọi ranh giới mà assertion phụ thuộc. Thu nhỏ hơn mức đó chỉ tạo cảm giác an toàn sai hoặc buộc phải mock quá nhiều.

**Purpose:**

Đưa ra decision rule để chọn slice nhỏ nhất nhưng vẫn chứa đủ collaboration mà assertion phải chứng minh.

## Khi nào slice cần bàn giao sang full context?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:43–05:57`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: animate a slice growing as production imports accumulate; stop before it becomes a reconstructed application and replace it with one `@SpringBootTest` boundary.

**Script:**

Boundary hẹp cuối cùng cũng có giới hạn; nếu behavior phụ thuộc wiring bị loại bỏ thì phải bàn giao sang full Boot context.

**Purpose:**

Làm rõ điểm slice bị thu quá hẹp: khi wiring liên tầng bắt buộc nằm ngoài boundary, hãy đổi sang full Boot context thay vì tiếp tục import production component trở lại từng phần.

### Scene 1 — Khi nào slice cần bàn giao sang full context?

**Time:** `05:57–06:50`

**Visual:**

Cho slice mở rộng dần khi production import tăng lên; dừng trước khi nó biến thành một application được dựng lại bằng tay, rồi chuyển sang một boundary `@SpringBootTest` rõ ràng.

**Script:**

Chuyển sang `@SpringBootTest` khi hành vi vốn đã vượt qua ranh giới của slice, phụ thuộc auto-configuration rộng hơn, cần quá trình khởi động gần production hoặc cần web server thật. Tránh liên tục import production configuration vào slice cho tới khi nó âm thầm giống full application. Đây là quyết định thiết kế: slice chứng minh tích hợp tập trung; full context chứng minh sự phối hợp trên ranh giới Boot application lớn hơn.

**Purpose:**

Xác định điểm slice trở nên không trung thực vì hành vi liên tầng cần thiết nằm ngoài boundary và full context mới là mô hình phù hợp.
