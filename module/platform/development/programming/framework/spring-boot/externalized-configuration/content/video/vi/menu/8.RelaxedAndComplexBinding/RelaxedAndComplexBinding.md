---
video:
  url: ""
---

# Relaxed Binding, kiểu dữ liệu phức hợp và chuyển đổi kiểu

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

## Relaxed Binding chuẩn hóa tên thuộc tính như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Một tên logic, nhiều cách biểu diễn theo nguồn

**Time:** `00:00–00:50`

**Visual:** Đặt `customer.first-name` ở giữa; xung quanh lần lượt hiện `customer.firstName`, `customer.first_name` và biến môi trường `CUSTOMER_FIRSTNAME`, tất cả cùng trỏ tới trường Java `firstName`.

**Script:** “Relaxed binding cho phép một thuộc tính logic được biểu diễn theo quy ước của từng nguồn. Với `@ConfigurationProperties`, Spring Boot có thể ánh xạ kebab-case, camelCase, dạng có underscore và biến môi trường về cùng một thuộc tính đích. Tuy vậy, không phải mọi nơi đọc cấu hình đều có mức linh hoạt giống nhau. Kebab-case chữ thường vẫn nên là dạng chuẩn dùng trong tài liệu, prefix, metadata và placeholder.”

**Purpose:** Giải thích relaxed binding là bước chuẩn hóa tên tại binder, đồng thời giữ một tên chuẩn rõ ràng cho hợp đồng cấu hình.

## Chuyển tên thuộc tính chuẩn sang biến môi trường

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Khóa chuẩn `spring.main.log-startup-info` được đưa qua ba bước biến đổi.

**Script:** “Biến môi trường có quy ước tên riêng, nên Spring Boot định nghĩa cách chuyển đổi nhất quán từ tên thuộc tính chuẩn.”

**Purpose:** Chuyển từ relaxed binding nói chung sang quy tắc ánh xạ biến môi trường cụ thể.

### Scene 2 — Đổi dấu chấm, bỏ gạch ngang và viết hoa

**Time:** `01:00–01:45`

**Visual:** Hiện lần lượt các bước: `spring.main.log-startup-info` → thay dấu chấm bằng `_` → bỏ `-` → viết hoa → `SPRING_MAIN_LOGSTARTUPINFO`. Sau đó hiện `my.service[0].other → MY_SERVICE_0_OTHER`.

**Script:** “Để suy ra tên biến môi trường, bắt đầu từ khóa chuẩn: đổi dấu chấm thành underscore, bỏ dấu gạch ngang rồi chuyển toàn bộ sang chữ hoa. Với chỉ số của danh sách, số được đặt giữa hai underscore, ví dụ `my.service[0].other` thành `MY_SERVICE_0_OTHER`. Hãy suy ra tên biến từ khóa chuẩn thay vì tự đặt một biến thể riêng.”

**Purpose:** Cung cấp quy tắc ánh xạ có thể áp dụng và kiểm tra một cách nhất quán.

## Binding đối tượng cấu hình lồng nhau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–01:55`

**Visual:** Khóa phẳng được mở thành cây `mail → security → enabled/protocol`.

**Script:** “Relaxed binding giải quyết cách viết tên; bước tiếp theo là để cấu trúc đối tượng phản ánh đúng cấu trúc phân cấp của cấu hình.”

**Purpose:** Nối bước chuẩn hóa tên với binding theo cấu trúc đối tượng.

### Scene 3 — Đối tượng lồng nhau phản ánh cấu trúc thuộc tính

**Time:** `01:55–02:50`

**Visual:** Hiện `mail.host`, `mail.security.enabled`, `mail.security.protocol`, đồng thời mở `MailProperties` có đối tượng `Security` lồng bên trong. Các khóa được nối tới trường tương ứng.

**Script:** “Một không gian tên có thể chứa nhóm con mang ý nghĩa riêng. `mail.security.*` có thể bind tự nhiên vào đối tượng `Security` nằm trong `MailProperties`. Cấu trúc này giữ các giá trị liên quan gần nhau và tạo ranh giới rõ cho validation lồng nhau. Hãy mô hình hóa theo miền cấu hình, không cần sao chép nguyên cấu trúc package hay cây lớp của mã nguồn.”

**Purpose:** Cho thấy binding lồng nhau giúp mô hình cấu hình rõ cấu trúc và chuẩn bị cho validation theo tầng.

## Binding List và Set

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:** Các trường đơn lẻ được mở rộng thành một collection `servers[0..n]`.

**Script:** “Không phải nhóm cấu hình nào cũng có số phần tử cố định; binder cũng có thể xử lý collection.”

**Purpose:** Chuyển từ đối tượng lồng nhau cố định sang binding collection.

### Scene 4 — Collection từ YAML, properties và biến môi trường

**Time:** `03:00–03:50`

**Visual:** YAML `app.servers` với hai phần tử; song song hiện properties có chỉ số và `APP_SERVERS_0`, `APP_SERVERS_1`. Cuối cảnh đặt ghi chú “List không được trộn theo từng chỉ số giữa các nguồn”.

**Script:** “Spring Boot có thể bind List hoặc Set từ YAML, từ properties có chỉ số và từ dạng biến môi trường tương ứng. Với List, cần nhớ rằng thứ tự ưu tiên được xét ở cấp collection; đừng giả định các phần tử từ nhiều nguồn sẽ tự ghép lại theo từng chỉ số.”

**Purpose:** Cho thấy nhiều cách biểu diễn collection và chuẩn bị cho quy tắc thay thế toàn bộ List ở phần sau.

## Binding Map và giữ nguyên khóa có ký tự đặc biệt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:** Danh sách theo chỉ số chuyển thành Map với các khóa động `region`, `tier`, `a.b`.

**Script:** “Khi chính tên khóa là dữ liệu và không được cố định trong kiểu Java, Map thường phù hợp hơn một collection dựa trên vị trí.”

**Purpose:** Dẫn từ collection theo chỉ số sang cấu trúc Map theo khóa.

### Scene 5 — Dùng ngoặc vuông để giữ nguyên khóa đặc biệt

**Time:** `04:00–04:50`

**Visual:** Hiện `labels.region=eu`, `labels.tier=gold`; sau đó so sánh `[a.b]` và `a.b` trong `Map<String,Object>`: dạng có ngoặc giữ `a.b` thành một khóa, dạng không có ngoặc được hiểu như đường dẫn lồng nhau.

**Script:** “Map phù hợp khi tên khóa là một phần của dữ liệu. Với khóa chứa ký tự đặc biệt, cú pháp ngoặc vuông giúp giữ nguyên những ký tự mà binder có thể hiểu như dấu phân tách đường dẫn. Ví dụ `[a.b]` giữ `a.b` thành một khóa Map, còn `a.b` không có ngoặc sẽ biểu diễn cấu trúc lồng nhau. Đây là quy tắc binding, không phải lý do để biến tên property thành một kho dữ liệu tùy ý.”

**Purpose:** Làm rõ cách Map xử lý khóa đặc biệt bằng một ví dụ đối chiếu trực tiếp.

## Vì sao List ở nguồn ưu tiên cao thay thế List ở nguồn ưu tiên thấp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:** Hai List `users` từ hai nguồn có độ ưu tiên khác nhau được đặt chồng lên nhau.

**Script:** “List dễ gây nhầm nhất khi cùng một thuộc tính xuất hiện ở nhiều nguồn cấu hình.”

**Purpose:** Chuyển từ hình dạng binding sang quy tắc thay thế khi nhiều nguồn cùng định nghĩa một List.

### Scene 6 — Nguồn ưu tiên cao thay toàn bộ List thông thường

**Time:** `05:00–05:27`

**Visual:** Nguồn ưu tiên thấp có `alice`, `bob`; nguồn ưu tiên cao chỉ có `carol`. Gạch bỏ các mũi tên ghép theo chỉ số và hiển thị List có hiệu lực chỉ còn `carol`.

**Script:** “Với binding List phức hợp thông thường, Spring Boot không ghép từng phần tử từ nhiều nguồn. List ở nguồn có độ ưu tiên cao hơn sẽ thay toàn bộ List ở nguồn thấp hơn. Vì vậy nếu phần ghi đè chỉ chứa `carol`, List có hiệu lực cũng chỉ còn `carol`, chứ không phải `alice`, `bob`, rồi thêm `carol`.”

**Purpose:** Làm rõ quy tắc thay thế toàn bộ List và loại bỏ giả định rằng List được vá theo từng chỉ số.

### Scene 7 — Không áp quy tắc List cho mọi cơ chế nhiều giá trị

**Time:** `05:27–05:55`

**Visual:** Giữ ví dụ List bị thay thế ở bên trái. Bên phải, hiển thị một Map có các khóa có thể kết hợp giữa các nguồn và một thẻ riêng cho `spring.profiles.include` với nhãn “cơ chế đặc biệt của Spring Boot”.

**Script:** “Quy tắc vừa rồi áp dụng cho binding List phức hợp thông thường, không phải cho mọi cấu hình có nhiều giá trị. Map có thể kết hợp các khóa theo cách khác, còn `spring.profiles.include` được Spring Boot xử lý theo hợp đồng riêng. Khi chẩn đoán, hãy xác định đúng cơ chế trước khi kết luận rằng dữ liệu sẽ được ghép hay thay thế.”

**Purpose:** Ngăn việc khái quát sai quy tắc thay thế List sang Map hoặc các thuộc tính đặc biệt như `spring.profiles.include`.

## Chuyển đổi kiểu trong quá trình binding

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:05`

**Visual:** Chuỗi `"2s"` và `"10MB"` đi vào binder rồi được chuyển thành các kiểu Java tương ứng.

**Script:** “Sau khi binder tìm đúng khóa và đúng cấu trúc, nó còn phải chuyển dữ liệu dạng văn bản thành kiểu Java đích.”

**Purpose:** Nối bước khớp tên và cấu trúc với giai đoạn chuyển đổi kiểu.

### Scene 8 — Có khóa nhưng vẫn có thể lỗi chuyển đổi kiểu

**Time:** `06:05–07:00`

**Visual:** Mở `client.timeout=2s`, `client.max-payload=10MB` cùng record có `Duration` và `DataSize`. Sau đó đổi thành `client.timeout=fast` và dừng ở lỗi chuyển đổi kiểu.

**Script:** “Nguồn cấu hình phần lớn cung cấp dữ liệu dạng văn bản, còn kiểu đích có thể là số, boolean, enum, URI, `Duration`, `DataSize` và nhiều kiểu khác. Nếu khóa đã tồn tại nhưng `timeout=fast` không thể chuyển thành `Duration`, đây là lỗi chuyển đổi kiểu chứ không phải lỗi nạp cấu hình hay thứ tự ưu tiên. Có thể dùng converter tùy biến, nhưng vì binding diễn ra sớm nên các phụ thuộc của converter cần được giữ tối giản.”

**Purpose:** Phân biệt lỗi chuyển đổi kiểu với lỗi nạp nguồn hoặc lỗi suy luận thứ tự ưu tiên.

## Duration, DataSize và các kiểu đích phổ biến

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:10`

**Visual:** Hai giá trị số `30` và `25` xuất hiện với dấu hỏi về đơn vị, rồi được đổi thành `30s` và `25MB`.

**Script:** “Binding có kiểu cũng cho phép hợp đồng cấu hình biểu đạt đơn vị trực tiếp, thay vì giấu ý nghĩa trong một con số trần.”

**Purpose:** Chuyển từ chuyển đổi kiểu nói chung sang thiết kế property với kiểu đích thể hiện rõ ý nghĩa.

### Scene 9 — Đơn vị là một phần của hợp đồng

**Time:** `07:10–07:55`

**Visual:** Hiện `cache.ttl=30s`, `upload.max-size=25MB`; bên cạnh là `@DurationUnit`, `@DataSizeUnit` cho các giá trị số cũ chưa ghi đơn vị. Cuối cảnh so sánh `Duration` với `long timeout`.

**Script:** “Với `Duration` và `DataSize`, nên ghi đơn vị tường minh như `250ms`, `2s`, `10KB` hay `25MB`. Annotation về đơn vị có thể hỗ trợ property cũ chỉ chứa số, nhưng cấu hình mới nên để người đọc thấy đơn vị ngay trong giá trị. Một kiểu đúng miền như `Duration` an toàn hơn `long` khi đơn vị của nó chỉ tồn tại trong tài liệu.”

**Purpose:** Kết thúc phần binding bằng nguyên tắc thiết kế property có kiểu rõ ràng, dễ đọc và ít mơ hồ.
