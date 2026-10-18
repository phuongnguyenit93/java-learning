---
video:
  url: ""
---

# Layered JAR và WAR

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

## Layered archive giải quyết vấn đề gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Tách content theo tần suất thay đổi

**Time:** `00:00–00:27`

**Visual:**

Hiển thị một application archive lớn nơi một class nhỏ thay đổi làm cả khối bị invalidate. Sau đó tách cùng nội dung thành stable dependencies, loader, snapshot dependencies và application content.

**Script:**

Application classes thường thay đổi nhiều hơn released third-party dependencies. Layered Boot archive ghi lại các logical group để công cụ extraction hoặc image phía sau có thể tách phần ổn định khỏi phần thay đổi thường xuyên. Dependency semantics của Java không đổi; layering bổ sung packaging metadata nhằm tăng khả năng reuse cache và giảm dữ liệu cần xử lý lại.

**Purpose:**

Giải thích layering dựa trên change frequency và cache reuse, không dựa trên business architecture.

## Các layer mặc định của Spring Boot archive là gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:27–00:35`

**Visual:**

Biến các group khái quát thành bốn block có tên theo default của Boot.

**Script:**

Boot cung cấp một default grouping hữu ích để mỗi project không phải tự phát minh taxonomy layer từ đầu.

**Purpose:**

Chuyển từ mục tiêu của layering sang default layer model của Spring Boot.

### Scene 2 — Đọc bốn default layer

**Time:** `00:35–01:03`

**Visual:**

Xếp `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`. Thêm ví dụ: released external JAR, loader classes, SNAPSHOT JAR, project classes/local module dependency.

**Script:**

Default model tách released dependencies, Spring Boot Loader, snapshot dependencies và application content. Cách chia này phản ánh tần suất thay đổi dự kiến chứ không phải cấu trúc business. Project dependency thường nằm cùng application content, trong khi released external library có thể vào layer dependencies. `layers.idx` được generate mới là evidence chính xác về vị trí của từng file trong build hiện tại.

**Purpose:**

Dạy tên layer mặc định và lý do nội dung được phân nhóm như vậy.

## Vì sao thứ tự layer ảnh hưởng khả năng tái sử dụng cache?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:03–01:10`

**Visual:**

Animate application class thay đổi trong khi ba layer ổn định phía trước vẫn giữ nguyên.

**Script:**

Phân nhóm chỉ có giá trị khi downstream tooling giữ được các boundary đó theo cách hỗ trợ cache.

**Purpose:**

Kết nối logical layer membership với cache behavior thực tế.

### Scene 3 — Stable layer sống sót qua các lần đổi source

**Time:** `01:10–01:36`

**Visual:**

Timeline cache: dependencies và loader reused, application layer rebuilt. Sau đó đưa ra phản ví dụ Dockerfile copy toàn bộ archive thành một opaque image layer khiến mọi thứ invalidate.

**Script:**

Container cache có thể reuse layer ổn định khi dependency được tách khỏi application code thay đổi thường xuyên. Nhưng layered archive không tự bảo đảm lợi ích này. Nếu Dockerfile chỉ copy toàn bộ JAR như một file vào một image layer thì inner grouping không được reuse độc lập. Packaging metadata và image-building strategy phải cùng tôn trọng boundary.

**Purpose:**

Giải thích vì sao layer order và cách downstream consume layer cùng quyết định cache reuse.

## Khi nào nên tùy chỉnh layering?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:36–01:44`

**Visual:**

Giữ default four layers rồi overlay một cache profile cho thấy internal library lớn thay đổi ít hơn application.

**Script:**

Default là điểm bắt đầu. Custom layer chỉ đáng làm khi behavior đo được của project không khớp assumption mặc định.

**Purpose:**

Định vị custom layering như optimization dựa trên evidence.

### Scene 4 — Tùy chỉnh theo change pattern thật

**Time:** `01:44–02:24`

**Visual:**

Tạo layer “stable internal libraries” và layer “application”. Hiển thị ba rule: claim content vào named layer; rule match sớm hơn thắng khi pattern overlap; mọi destination layer phải có mặt trong complete layer order. Thêm warning cho unclaimed content và quá nhiều package-named layer.

**Script:**

Hãy customize layering khi có pattern ổn định, ví dụ internal library lớn thay đổi ít hơn application code. Custom rule phải phân content vào layer có ý nghĩa và khai báo thứ tự đầy đủ. Nếu nhiều pattern cùng có thể claim một content thì rule order quyết định layer nhận nó, và content vẫn cần destination hợp lệ có mặt trong final order. Không nên biến từng package thành một layer; cách đó gắn cache policy quá chặt với source organization và tạo maintenance cost mà chưa chắc cải thiện cache.

**Purpose:**

Đưa ra nguyên tắc rõ khi nào custom layer có ích và constraint về matching/order để custom scheme hoàn chỉnh.

## Archive layering không định nghĩa điều gì về container runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:24–02:32`

**Visual:**

Đặt `layers.idx` ở build side và một running container với network, volume, CPU và rollout control ở runtime side.

**Script:**

Từ “layer” cũng xuất hiện trong container domain, nên cần chốt lại chính xác archive layer không điều khiển những gì.

**Purpose:**

Ngăn packaging-layer terminology bị trộn với container-runtime behavior.

### Scene 5 — Packaging metadata dừng trước container operations

**Time:** `02:32–02:59`

**Visual:**

Bên trái: archive grouping, extraction và cache. Bên phải: container networking, mounts, limits, orchestration, registry, rollout. Có arrow cho thấy archive layer có thể hỗ trợ image construction nhưng không cấu hình runtime.

**Script:**

Boot archive layer chỉ mô tả cách file ứng dụng được phân nhóm để packaging và extraction. Nó không định nghĩa container networking, volume, process isolation, CPU hay memory limit, registry behavior hoặc rollout strategy. Dependency vào sai Boot layer thì kiểm packaging rule. Image đã đúng nhưng mount hay limit lúc chạy sai thì artifact đã sang owner của container platform.

**Purpose:**

Khép lại bằng troubleshooting boundary rõ giữa archive layering và container runtime concerns.
