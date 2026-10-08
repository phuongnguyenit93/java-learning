---
video:
  url: ""
---

# Runtime Hints cho truy cập động

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

## Vì sao biên dịch native cần Runtime Hints?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao biên dịch native cần Runtime Hints?

**Time:** `00:00–00:31`

**Visual:**

Hiện direct call edge được static analysis theo dõi, rồi ba lookup vô hình—reflection, resource path, proxy interface—chỉ trở thành reachable khi hint metadata thêm edge.

**Script:**

Static analysis theo dõi direct code reference rất tốt, nhưng framework thường truy cập thứ gì đó gián tiếp. Private member được chọn bằng reflection, resource mở bằng string path hoặc tổ hợp interface dùng cho JDK proxy có thể không trông reachable trong bytecode. Runtime Hints biến những giả định đó thành build metadata để native compiler biết type, member, resource hay proxy capability nào phải tồn tại lúc runtime.

**Purpose:**

Giải thích Runtime Hints như reachability contract tường minh cho truy cập động gián tiếp.

## Mô hình `RuntimeHints` của Spring mô tả điều gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:31–00:41`

**Visual:**

Gom các dynamic edge vào một RuntimeHints object của Spring rồi tách ra các registry riêng.

**Script:**

Spring gói các native requirement khác nhau trong một model chung để application và library không phải tự thao tác GraalVM JSON ở mức thấp.

**Purpose:**

Giới thiệu RuntimeHints như abstraction Spring-level cho nhiều loại reachability requirement.

### Scene 1 — Mô hình `RuntimeHints` của Spring mô tả điều gì?

**Time:** `00:41–01:09`

**Visual:**

RuntimeHints với năm registry: reflection, resources, serialization, JDK proxies và JNI; AOT chuyển chúng thành native-image metadata.

**Script:**

RuntimeHints là model lập trình của Spring để mô tả native runtime requirement. Nó gom reflection, resource inclusion, Java serialization, JDK proxy và JNI dưới API của Spring. Trong AOT, framework có thể merge contribution từ Spring, thư viện và application rồi chuyển thành metadata cho native-image tooling. Cách này giữ semantics ở mức Spring thay vì bắt mỗi application tự viết low-level configuration file.

**Purpose:**

Định nghĩa RuntimeHints model và các registry chính trước khi so sánh semantics của chúng.

## Hint cho reflection, resource, serialization và JDK proxy khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:09–01:18`

**Visual:**

Mở từng registry thành capability card với một ví dụ runtime cụ thể.

**Script:**

Các category không thay thế lẫn nhau; mỗi loại giữ một capability khác và chỉ nên dùng khi đúng với hành vi thực tế.

**Purpose:**

Ngăn đăng ký sai hoặc quá rộng, đặc biệt nhầm Java serialization với JSON binding.

### Scene 1 — Hint cho reflection, resource, serialization và JDK proxy khác nhau thế nào?

**Time:** `01:18–01:54`

**Visual:**

Năm card: reflective constructor/member, classpath resource pattern, Java-serialization type, proxy interface tuple và JNI member access; JSON binding được đặt cạnh reflection chứ không cạnh serialization.

**Script:**

Reflection hint giữ type hoặc member metadata và thao tác reflection được phép. Resource hint đưa classpath resource cần mở sau này vào image. SerializationHints dành riêng cho Java serialization. JDK proxy hint giữ tổ hợp interface, còn JNI có channel riêng cho native access. JSON hay object binding không đồng nghĩa Java serialization; Spring thường đóng góp reflection hints cho type mà binder cần quan sát hoặc khởi tạo. Hãy đăng ký capability hẹp nhất khớp với dynamic contract thực tế.

**Purpose:**

Phân biệt đúng từng hint category và sửa rõ nhầm lẫn JSON với Java serialization.

## Spring có thể tự suy ra hint nào và hint nào cần code ứng dụng đóng góp?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:54–02:05`

**Visual:**

Đánh dấu common Spring contracts là auto-detected; custom reflection utility và computed resource name được highlight là gap cần contribution tường minh.

**Script:**

Trước khi thêm hint ở application, hãy xác định Spring hoặc library đã hiểu pattern đó chưa; explicit metadata chỉ dành cho phần không thể infer an toàn.

**Purpose:**

Dạy ownership order để tránh catch-all hint không cần thiết.

### Scene 1 — Spring có thể tự suy ra hint nào và hint nào cần code ứng dụng đóng góp?

**Time:** `02:05–02:43`

**Visual:**

Controller binding, configuration properties và framework proxies nhận inferred hints; custom reflection và computed resource đi vào hộp application-owned hints.

**Script:**

Spring AOT hiểu nhiều contract của Spring và integration đã hỗ trợ, nên controller binding, configuration properties, framework proxy và các pattern phổ biến có thể nhận hints tự động. Explicit application hint cần khi code của bạn hoặc library dùng dynamic access mà Spring không nhận biết, ví dụ custom reflection helper, resource name tính gián tiếp hoặc integration chưa có native support. Workflow an toàn là build và test trước, xác định đúng missing contract, rồi chỉ thêm hint nhỏ nhất mô tả nhu cầu đó.

**Purpose:**

Tách inferred framework metadata khỏi explicit hint do application sở hữu.

## Khi nào `RuntimeHintsRegistrar` và các hint annotation được dùng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:43–02:52`

**Visual:**

Mở code panel ba cột: RuntimeHintsRegistrar, @RegisterReflectionForBinding và assertion bằng RuntimeHintsPredicates.

**Script:**

Khi application thực sự sở hữu missing contract, Spring cung cấp cả cách programmatic, declarative và một cách test nhanh trước full native build.

**Purpose:**

Khôi phục đầy đủ registrar, annotation và predicate bằng evidence cụ thể.

### Scene 1 — Khi nào `RuntimeHintsRegistrar` và các hint annotation được dùng?

**Time:** `02:52–03:22`

**Visual:**

Một RuntimeHintsRegistrar được import bằng @ImportRuntimeHints; DTO dùng @RegisterReflectionForBinding; unit test dùng RuntimeHintsPredicates để xác nhận member hoặc resource registration.

**Script:**

Dùng RuntimeHintsRegistrar khi cần đăng ký bằng code nhiều native requirement; registrar nhận RuntimeHints và có thể thêm reflection, resource, proxy, Java serialization hoặc JNI, rồi được import bằng @ImportRuntimeHints. Với binding phổ biến, @RegisterReflectionForBinding diễn đạt intent hẹp hơn theo kiểu declarative. Không cần đợi full native build mới biết hint có tồn tại: RuntimeHintsPredicates có thể assert reflection hoặc resource registration mong đợi trong test nhanh.

**Purpose:**

Dạy đủ ba công cụ application-level: registrar, binding annotation và predicate-based hint test.

## Reachability metadata của thư viện bên thứ ba nằm ở đâu trong mô hình?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:31`

**Visual:**

Đưa hộp application hints vào stack ba tầng: framework inference, library metadata, application override.

**Script:**

Application hint chỉ là một tầng; dynamic behavior thuộc thư viện nên được mô tả gần chính thư viện đó nhất khi có thể.

**Purpose:**

Đặt third-party reachability metadata vào đúng ownership hierarchy.

### Scene 1 — Reachability metadata của thư viện bên thứ ba nằm ở đâu trong mô hình?

**Time:** `03:31–04:06`

**Visual:**

Ownership stack: Spring/framework inferred hints ở trên; metadata do library ship hoặc GraalVM reachability ecosystem ở giữa; application RuntimeHints ở dưới cho custom behavior hoặc workaround cần thiết.

**Script:**

Dependency bên thứ ba có thể ship native-image configuration hoặc được bao phủ bởi hệ sinh thái GraalVM reachability metadata. Thứ tự ownership rất quan trọng: để Spring hoặc framework infer phần nó hiểu; ưu tiên metadata do library hoặc ecosystem cung cấp cho dynamic behavior nằm trong dependency; dùng application RuntimeHints cho hành vi do chính ứng dụng tạo ra hoặc workaround có kiểm soát khi upstream thiếu hỗ trợ. Metadata càng gần owner càng dễ bảo trì khi upgrade.

**Purpose:**

Khôi phục ownership order framework → library/reachability metadata → application RuntimeHints.

## Khi reflection, resource hoặc proxy không hoạt động, điều đó gợi ý vấn đề về hint như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:06–04:15`

**Visual:**

Biến ownership stack thành failure trace từ native exception quay ngược về exact dynamic operation và owner của metadata.

**Script:**

Ownership model này cho ta đường chẩn đoán có kỷ luật khi một dynamic feature chạy trên JVM nhưng chỉ hỏng trong native executable.

**Purpose:**

Kết thúc Runtime Hints bằng troubleshooting hẹp thay vì thêm reflection config hàng loạt.

### Scene 1 — Khi reflection, resource hoặc proxy không hoạt động, điều đó gợi ý vấn đề về hint như thế nào?

**Time:** `04:15–04:53`

**Visual:**

Reflective constructor fail, missing resource và proxy error đi vào flow: xác định operation → check framework support → inspect metadata → thêm/test hint đúng owner → rebuild. Tracing agent xuất hiện như evidence tùy chọn cần review.

**Script:**

Tín hiệu hint mạnh là cùng code path chạy tốt trên JVM nhưng lỗi native đúng lúc chạm reflection, resource lookup hoặc proxy creation. Xác định operation bị lỗi trước, kiểm tra Spring hay dependency đã có supported hint path chưa, inspect generated metadata, rồi chỉ thêm và test explicit hint nếu application sở hữu gap. GraalVM tracing agent có thể hỗ trợ phát hiện access, nhưng output của nó vẫn phải review vì test infrastructure rộng có thể ghi lại metadata không liên quan production path.

**Purpose:**

Đưa ra vòng chẩn đoán hint tập trung và dùng tracing agent như bằng chứng cần review.
