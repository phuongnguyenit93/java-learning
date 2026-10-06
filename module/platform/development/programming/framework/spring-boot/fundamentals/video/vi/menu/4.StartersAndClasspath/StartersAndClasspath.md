---
video:
  url: ""
---

# Starter, dependency được quản lý và classpath

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

## Spring Boot Starter là gì và vì sao nên dùng?

<!-- VIDEO_SECTION -->

### Scene 1 — Starter là dependency intent, không phải code tự chạy

**Time:** `00:00–00:55`

**Visual:**

Mở `build.gradle` với `implementation 'org.springframework.boot:spring-boot-starter-web'`. Từ dependency này, animate ra các ô Spring web stack và supporting libraries đi vào classpath. Đặt dấu gạch đỏ lên câu giả định "starter chạy web app".

**Script:**

Starter trước hết là một dependency descriptor được tuyển chọn cho một nhu cầu phổ biến. Thay vì tự tìm từng thư viện Spring và thư viện bên thứ ba, ta khai báo một starter để có một classpath khởi đầu theo quy ước. `spring-boot-starter-web`, chẳng hạn, đưa web stack và các thư viện hỗ trợ vào classpath. Bản thân starter không chạy server hay đăng ký bean; vai trò chính của nó là thể hiện ý định dependency và giảm công việc chọn thư viện.

**Purpose:**

Đặt starter đúng ở lớp build/classpath và loại bỏ hiểu lầm rằng starter là cơ chế runtime tự thực thi.

## Phiên bản dependency được quản lý giải quyết vấn đề gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Từ nhiều dependency do starter kéo vào, hiện các badge version khác nhau và một biểu tượng cảnh báo compatibility.

**Script:**

Starter giảm số dependency phải chọn thủ công, nhưng một ứng dụng còn phải giải bài toán khó hơn: rất nhiều thư viện cần dùng các phiên bản tương thích với nhau.

**Purpose:**

Chuyển từ dependency convenience sang giá trị của managed dependency versions.

### Scene 2 — Một baseline phiên bản được phối hợp

**Time:** `01:05–02:00`

**Visual:**

Hiện cây dependency với các version xung đột rồi chuyển sang sơ đồ `Boot version → curated dependency set → Spring libraries + third-party libraries`. Sau đó nâng nhãn Boot version và animate nhiều version con cùng thay đổi.

**Script:**

Nếu mỗi dependency được chọn version độc lập, ta dễ tạo một tổ hợp mà thư viện này kỳ vọng API khác với thư viện kia. Mỗi phiên bản Spring Boot công bố một tập phiên bản dependency được tuyển chọn. Khi build dùng dependency management của Boot, nhiều thư viện phổ biến có thể được khai báo mà không phải lặp version, và khi nâng Boot, cả nhóm version liên quan được dịch chuyển có phối hợp. Ta vẫn có thể ghi đè, nhưng lúc đó một phần trách nhiệm tương thích quay lại phía ứng dụng.

**Purpose:**

Giải thích managed versions như cơ chế giảm rủi ro tổ hợp dependency không tương thích, không đi sâu BOM/plugin mechanics.

## Classpath có thể thay đổi hành vi của Boot như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Từ curated dependency set, zoom vào một nhãn `classpath` và đặt hai snapshot "before web starter" / "after web starter".

**Script:**

Dependency không chỉ ảnh hưởng việc code có compile được hay không. Với Boot, classpath còn là bằng chứng về những công nghệ đang sẵn có.

**Purpose:**

Nối quyết định dependency ở build time với khả năng Boot suy ra và cấu hình ở runtime.

### Scene 3 — Classpath là tín hiệu cho application shape và auto-configuration

**Time:** `02:10–03:10`

**Visual:**

Nhánh A: classpath không có web stack → `WebApplicationType.NONE` / non-web context. Nhánh B: thêm Servlet web stack → `SpringApplication` suy ra Servlet type và tạo Servlet-capable context. Sau đó mới animate `auto-configuration → embedded server infrastructure when conditions match`. Highlight hai bước bằng màu khác nhau.

**Script:**

Giả sử ứng dụng ban đầu là non-web. Khi thêm Servlet web stack, classpath xuất hiện Spring MVC và server implementation. Theo mặc định, `SpringApplication` dùng bằng chứng từ classpath để xác định `WebApplicationType` và tạo context tương ứng. Trong context đó, auto-configuration mới tiếp tục đóng góp hạ tầng khi các condition phù hợp, ví dụ embedded server. Hai trách nhiệm này cần tách rõ: `SpringApplication` xác định shape/context, còn auto-configuration đóng góp cấu hình bên trong context.

**Purpose:**

Chứng minh dependency choice có thể đổi runtime possibilities đồng thời giữ đúng ranh giới giữa application-type detection và auto-configuration.

## Vì sao starter không phải là auto-configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Giữ nhánh web vừa rồi, tách thành hai hộp nối tiếp theo quan hệ nhân quả: `starter/dependency metadata` và `auto-configuration classes/conditions`.

**Script:**

Ví dụ web cho thấy starter và auto-configuration thường xuất hiện gần nhau. Chính vì thế chúng rất dễ bị gọi nhầm thành một cơ chế duy nhất.

**Purpose:**

Chuẩn bị so sánh hai cơ chế thường bị đánh đồng.

### Scene 4 — Build-time convenience và runtime configuration

**Time:** `03:20–04:10`

**Visual:**

Hiện bảng hai cột. Starter: `dependency metadata`, `shape classpath`, `build-time convenience`. Auto-configuration: `configuration classes + conditions`, `contribute beans/configuration`, `ApplicationContext/runtime`. Bên dưới là hai câu hỏi debug: `Library có trên classpath?` và `Vì sao configuration match/back off?`.

**Script:**

Starter giải bài toán dependency: nó đưa một tập thư viện được tuyển chọn vào classpath. Auto-configuration giải bài toán cấu hình: nó đóng góp Spring configuration khi condition thỏa mãn. Vì vậy, khi chẩn đoán, câu hỏi "thư viện có ở classpath không?" thuộc dependency; còn "vì sao Boot tạo hoặc bỏ qua cấu hình này?" thuộc auto-configuration. Starter có thể tạo điều kiện để auto-configuration hoạt động, nhưng starter không tự đăng ký các bean đó.

**Purpose:**

Tách hai cơ chế bằng vai trò, thời điểm và loại bằng chứng cần kiểm tra.

## Khi nào nên dùng starter, dependency riêng lẻ hoặc ghi đè phiên bản?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:10–04:20`

**Visual:**

Thu gọn bảng so sánh thành ba lựa chọn trên một decision tree: `starter`, `individual dependency`, `version override`.

**Script:**

Khi đã phân biệt đúng cơ chế, quyết định còn lại là chọn mức dependency nào phù hợp với nhu cầu thật của ứng dụng.

**Purpose:**

Chuyển từ mental model sang quy tắc lựa chọn dependency thực tế.

### Scene 5 — Decision tree cho dependency

**Time:** `04:20–05:10`

**Visual:**

Decision tree: `Nhu cầu Boot phổ biến? → starter`; `Chỉ cần một library hẹp? → individual dependency`; `Cần compatibility/security/feature cụ thể khác baseline? → explicit version override + verify`. Đặt biển chỉ dẫn `BOM/plugin mechanics → build-tooling-packaging`.

**Script:**

Nếu starter mô tả đúng khả năng cần dùng và bạn muốn tập dependency theo quy ước, starter là lựa chọn tự nhiên. Nếu chỉ cần một thư viện hẹp, dependency riêng lẻ giúp tránh kéo cả tập starter. Chỉ nên ghi đè phiên bản được quản lý khi có yêu cầu cụ thể về tương thích, bảo mật hoặc tính năng và đã kiểm chứng tổ hợp mới. Cách BOM được import hay plugin Gradle/Maven quản lý build thuộc `build-tooling-packaging`; Fundamentals chỉ giữ mô hình lựa chọn.

**Purpose:**

Khép video bằng một quy tắc ra quyết định thực dụng và handoff đúng sang module build-tooling-packaging.
