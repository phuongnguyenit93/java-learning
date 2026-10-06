---
video:
  url: ""
---

# Spring Boot DevTools

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

## Vì sao Spring Boot DevTools tồn tại?

<!-- VIDEO_SECTION -->

### Scene 1 — Rút ngắn vòng lặp sửa code và quan sát

**Time:** `00:00–00:55`

**Visual:**

Hiện timeline phát triển `edit → build → restart manually → wait → observe`. Sau đó thêm `spring-boot-devtools` và rút gọn thành `edit → classpath update → automatic restart / LiveReload → observe`. Ở góc màn hình có nhãn `development-time only`.

**Script:**

DevTools không thêm khả năng nghiệp vụ cho ứng dụng. Nó tồn tại để rút ngắn vòng lặp sửa code, build lại và quan sát kết quả khi phát triển. Ba nhóm tiện ích chính là automatic restart, LiveReload và các property mặc định phù hợp cho quá trình phát triển. Điểm quan trọng nhất là ranh giới sử dụng: DevTools phục vụ quá trình phát triển. Bỏ DevTools ra không nên làm mất hành vi nghiệp vụ của ứng dụng, và trong luồng đóng gói thông thường nó không được xem là dependency cho môi trường vận hành thực tế.

**Purpose:**

Đặt DevTools đúng vai trò developer feedback tool và tách nó khỏi business/runtime requirement.

## Automatic Restart dùng hai ClassLoader như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Timeline restart thu nhỏ thành hai khối ClassLoader chồng lên nhau: `base` và `restart`.

**Script:**

Automatic restart nhanh hơn việc khởi động lại toàn bộ JVM vì DevTools không nạp lại mọi thứ từ đầu. Cơ chế chính nằm ở cách tách class bằng hai ClassLoader.

**Purpose:**

Chuyển từ mục tiêu feedback nhanh sang cơ chế hai ClassLoader của restart.

### Scene 2 — Giữ dependency ổn định, thay phần đang phát triển

**Time:** `01:05–02:05`

**Visual:**

Hiện `base ClassLoader → stable dependency JARs` và `restart ClassLoader → application classes`. Animate một class application được compile mới, restart loader cũ bị bỏ, restart loader mới được tạo; base loader giữ nguyên. Sau đó `ApplicationContext` được restart. Cuối cảnh hiện cảnh báo `classloader identity issue` cho multi-module project.

**Script:**

Các dependency ít thay đổi, thường là third-party JAR, nằm ở base ClassLoader. Class của ứng dụng đang phát triển nằm ở restart ClassLoader. Khi classpath có thay đổi phù hợp, DevTools bỏ restart loader cũ, tạo loader mới rồi restart `ApplicationContext`, trong khi base loader đã được nạp vẫn giữ lại. Cơ chế này nhanh, nhưng cũng có hệ quả: thư viện giả định classloader identity cố định hoặc dự án nhiều module có class nằm sai loader có thể hành xử khác. Nếu tắt restart làm lỗi biến mất, classloader placement là một hướng chẩn đoán đáng kiểm tra.

**Purpose:**

Giải thích vì sao restart nhanh và chỉ ra failure mode thực tế do classloader identity.

## LiveReload và các mặc định dành cho phát triển hỗ trợ ra sao?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:15`

**Visual:**

Tách màn hình thành hai luồng: `Java class change → restart` và `static/template resource change → browser refresh`.

**Script:**

Không phải thay đổi nào cũng cần restart context. Với tài nguyên phía giao diện, vấn đề thường là làm trình duyệt thấy thay đổi nhanh hơn.

**Purpose:**

Phân biệt automatic restart với LiveReload và development property defaults.

### Scene 3 — Restart, LiveReload và cache phục vụ ba điểm nghẽn khác nhau

**Time:** `02:15–03:10`

**Visual:**

Nhánh một: classpath class change → restart. Nhánh hai: static/template resource change → embedded LiveReload server → browser extension refresh. Nhánh ba: callout `spring.thymeleaf.cache=false` dưới tiêu đề `development property defaults`. Hiện toggle `spring.devtools.add-properties=false` và `spring.devtools.livereload.enabled=false`.

**Script:**

LiveReload giải bài toán khác restart. DevTools có thể chạy một LiveReload server để báo cho browser extension tương thích khi resource thay đổi, từ đó trình duyệt refresh mà không cần khởi động lại toàn bộ ứng dụng. Đồng thời DevTools áp dụng một số property thuận tiện cho quá trình phát triển, chẳng hạn tắt cache của template engine được hỗ trợ để thay đổi dễ thấy hơn. Các mặc định này vẫn có thể tắt bằng `spring.devtools.add-properties=false`, và LiveReload có thể tắt riêng bằng `spring.devtools.livereload.enabled=false`.

**Purpose:**

Tách ba cơ chế feedback và cho thấy development defaults vẫn là lựa chọn có thể kiểm soát.

## Thêm DevTools và điều chỉnh phạm vi restart như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Mở `build.gradle`, sau đó chuyển sang sơ đồ IDE build → runtime classpath directory.

**Script:**

Hiểu cơ chế rồi mới thấy vì sao chỉ nhấn Save chưa chắc đã đủ. DevTools phản ứng với cập nhật classpath, nên build tool và IDE phải thực sự tạo ra class hoặc resource mới trên runtime classpath.

**Purpose:**

Chuyển từ cơ chế restart sang setup và evidence cần kiểm tra khi restart không xảy ra.

### Scene 4 — Classpath phải thật sự thay đổi

**Time:** `03:20–04:25`

**Visual:**

Highlight Gradle snippet `developmentOnly 'org.springframework.boot:spring-boot-devtools'`. Tiếp theo minh họa `save source → compile/copy → classpath directory changes → DevTools detects → restart`. Hiện properties `spring.devtools.restart.additional-paths`, `restart.exclude`, `additional-exclude`, và file `META-INF/spring-devtools.properties` với `restart.include.*` / `restart.exclude.*`.

**Script:**

Với Gradle, DevTools nên nằm trong configuration `developmentOnly`. DevTools theo dõi những classpath entry là thư mục, nên source file được lưu nhưng chưa compile thì runtime classpath chưa đổi và restart chưa có lý do để xảy ra. Trong IntelliJ, build project tạo cập nhật đó; auto-build có thể tự động hóa khi được cấu hình phù hợp. Với cấu trúc nhiều module, `additional-paths` mở rộng vùng theo dõi; exclude điều chỉnh resource nào không gây restart; còn `spring-devtools.properties` giúp tinh chỉnh class nào vào base hay restart loader. Khi chẩn đoán, hãy kiểm chứng cập nhật classpath trước khi đổi hàng loạt DevTools property.

**Purpose:**

Cho learner một flow setup/debug có bằng chứng rõ và tránh hiểu nhầm Save file tự động đồng nghĩa classpath đã đổi.

## Remote DevTools là gì và vì sao nhạy cảm về bảo mật?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Sơ đồ local development được kéo thành hai máy: `developer machine` và `remote application`, nối bằng đường network có biểu tượng khóa.

**Script:**

DevTools còn có một trường hợp đặc biệt: hỗ trợ vòng phản hồi với ứng dụng chạy từ xa. Chính việc mở đường giao tiếp qua network khiến ranh giới bảo mật trở nên quan trọng hơn sự tiện lợi.

**Purpose:**

Chuyển từ local DevTools sang remote support với security-first framing.

### Scene 5 — Remote support là opt-in và không dành cho production

**Time:** `04:35–05:25`

**Visual:**

Hiện remote side cần `DevTools included intentionally` và `spring.devtools.remote.secret`. Trên đường network đặt `trusted network / SSL`. Sau đó hiện cảnh báo lớn `Never enable on production deployment` và callout `Spring WebFlux: remote DevTools unsupported in Boot 3.3`.

**Script:**

Remote DevTools là tính năng phải được chủ động bật. Ứng dụng phía remote phải cố ý chứa DevTools và cấu hình `spring.devtools.remote.secret`. Spring Boot cảnh báo tính năng này có rủi ro bảo mật, chỉ nên dùng trên mạng tin cậy hoặc được bảo vệ bằng SSL, và không bao giờ nên bật trên bản triển khai dùng để vận hành thực tế. Trong Spring Boot 3.3, remote DevTools cũng không hỗ trợ Spring WebFlux. Đây là quy trình phát triển đặc biệt, không phải một cơ chế triển khai chung.

**Purpose:**

Giữ đúng security boundary và version-specific limitation của Remote DevTools trong Boot 3.3.

## DevTools khác công cụ vận hành thực tế như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:**

Hai máy ở scene trước thu lại thành bảng `DevTools` và `Actuator` đặt cạnh nhau.

**Script:**

Từ hỗ trợ từ xa rất dễ trượt sang một hiểu lầm khác: vì DevTools có thứ để quan sát, nó có phải công cụ vận hành không? Câu trả lời nằm ở giai đoạn sử dụng và mục tiêu.

**Purpose:**

Nối security boundary của remote support với phân biệt DevTools và Actuator.

### Scene 6 — Feedback khi phát triển khác operational visibility

**Time:** `05:35–06:25`

**Visual:**

Bảng so sánh: DevTools → `development feedback`, `restart`, `LiveReload`, `dev property defaults`, `normally excluded/disabled in packaged production use`; Actuator → `running application operations`, `health`, `metrics`, `loggers`, `management endpoints`, `exposure/security decisions`. Kết bằng hai câu hỏi: `Thấy kết quả edit sớm hơn? → DevTools`; `Service đang ở trạng thái gì? → Actuator`.

**Script:**

DevTools trả lời câu hỏi của lập trình viên trong lúc phát triển: làm sao thấy thay đổi nhanh hơn? Actuator trả lời câu hỏi vận hành của một ứng dụng đang chạy: health ra sao, metrics thế nào, log level hay management endpoint nào đang cần quan sát? Restart nhanh không phải giám sát health, và LiveReload server không phải management endpoint. Giữ ranh giới này sẽ giúp bạn không mang công cụ phát triển sang môi trường vận hành thực tế như một cơ chế vận hành.

**Purpose:**

Khép video bằng boundary rõ giữa developer experience và production-ready operational surface, đồng thời handoff sang Actuator.
