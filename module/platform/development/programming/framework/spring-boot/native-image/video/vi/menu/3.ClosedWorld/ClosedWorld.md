---
video:
  url: ""
---

# Hệ quả của closed-world đối với ứng dụng Boot

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

## Closed-world assumption có ý nghĩa gì đối với ứng dụng Boot?

<!-- VIDEO_SECTION -->

### Scene 1 — Closed-world assumption có ý nghĩa gì đối với ứng dụng Boot?

**Time:** `00:00–00:36`

**Visual:**

Vẽ reachability graph đóng ở build time: direct code edge được giữ, node không reachable mờ đi, dynamic edge chỉ xuất hiện khi metadata mô tả nó.

**Script:**

Closed world nghĩa là native compiler suy luận từ code và metadata nó nhìn thấy trong build. Direct call edge tương đối dễ theo dõi; code trông như không reachable có thể bị loại, còn reflection, resource lookup, proxy hay truy cập gián tiếp cần reachability evidence. Spring AOT hỗ trợ bằng generated code và hints. Tuy vậy closed world không có nghĩa business runtime đứng yên; nó nghĩa cấu trúc code và framework phải được mô tả đủ sớm trước khi chạy.

**Purpose:**

Xây mental model closed-world đúng mà không tạo hiểu lầm rằng mọi runtime behavior đều bị đóng băng.

## Vì sao build-time classpath trở thành một phần của mô hình executable?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:36–00:46`

**Visual:**

Bao reachability graph bằng build-time classpath và cho thấy presence của dependency bật hoặc tắt conditional nodes.

**Script:**

Nếu compiler suy luận từ một thế giới đóng ở build time, chính classpath dùng để dựng thế giới đó trở thành một phần của executable model.

**Purpose:**

Nối closed-world analysis với dependency reproducibility và Boot conditions.

### Scene 1 — Vì sao build-time classpath trở thành một phần của mô hình executable?

**Time:** `00:46–01:17`

**Visual:**

Một dependency JAR có mặt lúc AOT bật auto-configuration branch; sau build, copy thêm JAR cạnh binary không thể tự thêm branch đó vào executable.

**Script:**

Classpath presence là input quan trọng của Boot. Trong AOT, classpath build time có thể quyết định auto-configuration, bean definition, generated proxy và reachability. Thêm một JAR sau khi binary đã build không làm class của nó tự chui vào executable; đổi dependency version trước rebuild cũng có thể đổi prepared model dù source không đổi. Vì vậy dependency versions và classpath composition phải được quản lý tái lập như source code.

**Purpose:**

Cho thấy classpath là input của native executable model chứ không phải extension point thêm muộn.

## Vì sao bean graph không thể tự do thay đổi sau xử lý AOT?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:17–01:29`

**Visual:**

Zoom từ dependency-selected configuration vào generated BeanFactory; khóa node cấu trúc nhưng để runtime values còn editable.

**Script:**

Classpath decisions đi vào bean model, và điều đó dẫn tới ràng buộc tiếp theo: AOT chuẩn bị một cấu trúc cụ thể chứ không để registry mở vô hạn lúc runtime.

**Purpose:**

Nối condition do dependency quyết định với structural bean model đã chuẩn bị.

### Scene 1 — Vì sao bean graph không thể tự do thay đổi sau xử lý AOT?

**Time:** `01:29–02:08`

**Visual:**

So sánh AOT-known BeanFactory với một runtime scanner cố đăng ký infrastructure bean chưa từng xuất hiện; đường đúng là AOT-aware integration đóng góp bean ngay trong processing.

**Script:**

Spring AOT chuẩn bị một BeanFactory cụ thể và sinh initialization cho model đó. Những pattern đăng ký bean muộn tùy ý, không xuất hiện trong processing, không thể được giả định là sẽ hoạt động sau native compilation. Điều này đặc biệt quan trọng với thư viện scan lúc runtime, tạo infrastructure động hoặc đăng ký singleton bằng custom bootstrap. Chúng cần đường tích hợp AOT-compatible để Spring nhìn thấy cấu trúc trong processing. Runtime value vẫn có thể đổi; ràng buộc nằm ở structural change của prepared model.

**Purpose:**

Tách late structural mutation khỏi runtime configuration value bình thường.

## Profile và property có thể ảnh hưởng quyết định bean ở build time như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:08–02:17`

**Visual:**

Chia configuration input thành hai lane: value-only settings đi tới runtime; profile/property dùng cho condition đi vào AOT và chọn bean branch.

**Script:**

Phân biệt structure và value trở nên rất cụ thể khi profile hoặc property quyết định một bean có tồn tại hay không.

**Purpose:**

Khôi phục nuance build-time profile/property và hậu quả lên native bean structure.

### Scene 1 — Profile và property có thể ảnh hưởng quyết định bean ở build time như thế nào?

**Time:** `02:17–03:05`

**Visual:**

Đặt cạnh nhau ba input cấu trúc: `@Profile("prod")`, profile-specific configuration và `@ConditionalOnProperty(name = "feature.enabled")`; AOT đánh giá chúng từ build-time environment rồi freeze bean branch. Bên cạnh, `db.url` chỉ đổi value của bean đã tồn tại ở runtime.

**Script:**

Trong AOT processing, Spring chuẩn bị đầy đủ `BeanFactory` và đánh giá condition theo **build-time environment**. Nếu `@Profile` hoặc profile-specific configuration quyết định bean hay configuration nào tồn tại, profile đó phải có trong môi trường AOT/build; chỉ đổi active profile lúc runtime không thể dựng lại một bean graph khác. Ranh giới tương tự áp dụng cho `@ConditionalOnProperty` hoặc switch quy ước `*.enabled`: khi chúng quyết định bean creation, giá trị chọn cấu trúc phải có lúc AOT và không thể được coi là runtime structural toggle. Điều này không đóng băng mọi property; URL, timeout hay credential của bean đã biết vẫn có thể đổi lúc chạy. Câu hỏi chẩn đoán là input đang đổi **value** hay đang cố đổi **bean structure**.

**Purpose:**

Phản chiếu đầy đủ giới hạn Boot 3.3 cho `@Profile`/profile-specific configuration và `@ConditionalOnProperty`/`*.enabled`, đồng thời giữ rõ runtime value không đổi bean graph vẫn có thể biến thiên.

## Những hành vi động nào cần được kiểm tra về mức độ sẵn sàng cho native?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:14`

**Visual:**

Chồng các biểu tượng reflection, resource path, proxy interfaces, serialization, generated bytecode và JNI lên closed graph.

**Script:**

Khi structural decision đã rõ, nhóm cần review tiếp là hành vi động có target không xuất hiện dưới dạng direct code edge.

**Purpose:**

Chuyển từ bean structure sang reachability risk mà hints hoặc library metadata phải mô tả.

### Scene 1 — Những hành vi động nào cần được kiểm tra về mức độ sẵn sàng cho native?

**Time:** `03:14–03:47`

**Visual:**

Native-readiness checklist với ví dụ lỗi cạnh mỗi dynamic category và badge cho biết Spring/library đã có hint hay chưa.

**Script:**

Hãy review reflection trên application hoặc library type, classpath resource mở bằng tên, JDK proxy, serialization cần reflective construction, runtime class loading hoặc bytecode generation, JNI và configuration gián tiếp chỉ tên class hay resource. Đây không phải danh sách 'cấm'. Spring và nhiều thư viện đã đóng góp hints tự động. Câu hỏi thật là native build có đủ bằng chứng để giữ đúng target và operation mà code sẽ cần lúc runtime hay chưa.

**Purpose:**

Đưa ra checklist hành vi động chính xác mà không biến mọi dynamic feature thành unsupported.

## Các giới hạn closed-world khác cấu hình runtime thông thường như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:47–03:57`

**Visual:**

Bỏ checklist và hiện native service đang chạy, vẫn thay request data và database URL trong khi code/bean structure giữ nguyên.

**Script:**

Ràng buộc dynamic access không nên bị nhầm với runtime behavior thông thường; application native vẫn có đời sống vận hành bình thường sau startup.

**Purpose:**

Chốt chapter bằng ranh giới giữa structural reachability và normal runtime state.

### Scene 1 — Các giới hạn closed-world khác cấu hình runtime thông thường như thế nào?

**Time:** `03:57–04:28`

**Visual:**

Hai case chẩn đoán: đổi database URL trong bean đã biết hoạt động bình thường; property runtime cố bật bean đã bị loại ở AOT được đánh dấu structural mismatch.

**Script:**

Native Boot application vẫn nhận request, mở connection, schedule task, đọc external value và thay business state. Closed world nói về reachable code và prepared framework structure, không nói mọi runtime value bị đóng băng. Khi chẩn đoán, hãy tách rõ: database URL sai là runtime configuration; property cố kích hoạt bean bị AOT loại là structural mismatch; reflective call chỉ lỗi ở native lại là tín hiệu cần xem reachability metadata.

**Purpose:**

Cho learner một boundary thực tế giữa runtime configuration và closed-world structure.
