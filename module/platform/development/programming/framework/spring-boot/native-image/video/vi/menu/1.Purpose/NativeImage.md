---
video:
  url: ""
---

# Vì sao Spring Boot Native Image cần AOT

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

## Spring Boot Native Image là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Spring Boot Native Image là gì?

**Time:** `00:00–00:38`

**Visual:**

Mở bằng màn hình chia đôi: bên trái Boot JAR đi vào JVM; bên phải Spring AOT đi qua GraalVM rồi kết thúc bằng một executable chạy trực tiếp trên OS. Đánh dấu phần việc được chuyển sang build time.

**Script:**

Hãy bắt đầu từ thứ được bàn giao. Spring Boot Native Image không phải một JAR được bọc bằng JVM ẩn; nó là executable theo nền tảng, được tạo sau khi Spring AOT làm rõ mô hình ứng dụng và GraalVM Native Image biên dịch phần chương trình có thể reach được. Flow cần nhớ là ứng dụng Boot, rồi source, bytecode và metadata do AOT sinh, tiếp theo là phân tích và biên dịch native, cuối cùng là binary mà hệ điều hành khởi chạy trực tiếp.

**Purpose:**

Đặt đúng artifact và pipeline tổng thể trước khi giải thích vì sao native cần AOT.

## Vì sao cách khởi động Boot động thông thường chưa đủ cho biên dịch native?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:38–00:50`

**Visual:**

Cho các biểu tượng runtime discovery rời khỏi lane JVM và dịch sang trái vào một hộp build-time evidence.

**Script:**

Khi đã biết artifact cuối là gì, câu hỏi kế tiếp là vì sao cách Boot khám phá mọi thứ lúc runtime không thể giữ nguyên khi tạo binary native.

**Purpose:**

Nối deployment form với bài toán biến runtime discovery thành bằng chứng ở build time.

### Scene 1 — Vì sao cách khởi động Boot động thông thường chưa đủ cho biên dịch native?

**Time:** `00:50–01:27`

**Visual:**

So sánh hai timeline: JVM có thể xét condition, reflection, resource và proxy lúc startup; lane native yêu cầu những nhu cầu động đó phải được biểu diễn trước khi compiler phân tích.

**Script:**

Trên JVM, Spring có thể khám phá class, đánh giá condition, dùng reflection, tìm resource và tạo proxy khi process đang khởi động. Native Image lại phân tích theo closed world ngay trong build. Thứ gì cần tồn tại lúc chạy phải đủ reachable hoặc được mô tả sớm cho compiler. Spring AOT lấp khoảng trống này bằng generated initialization và reachability metadata. Vấn đề không phải Spring 'quá động để compile', mà là các quyết định muộn phải được chuyển thành bằng chứng sớm hơn.

**Purpose:**

Giải thích đúng mâu thuẫn giữa startup động của Boot và closed-world native compilation.

## Native executable khác triển khai JVM thông thường như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:27–01:38`

**Visual:**

Khóa hộp build-time evidence rồi mở ra hai dạng runtime cạnh nhau.

**Script:**

Khi discovery được đẩy về build time, thay đổi không chỉ nằm ở command build mà còn ở artifact được giao và runtime mà máy đích cần cung cấp.

**Purpose:**

Dùng dịch chuyển AOT để dẫn sang so sánh chính xác JVM và native runtime.

### Scene 1 — Native executable khác triển khai JVM thông thường như thế nào?

**Time:** `01:38–02:15`

**Visual:**

Hai cột runtime contract. JVM: bytecode portable + JVM, class loading và JIT lúc chạy. Native: executable cho OS/architecture cụ thể chứa code đã biên dịch và phần runtime support được chọn.

**Script:**

Dạng JVM bàn giao bytecode và cần JVM trên máy đích, nên class loading, JIT optimization và nhiều quyết định reflection vẫn có thể xảy ra lúc runtime. Dạng native đã thực hiện reachability analysis và ahead-of-time compilation cho một OS và architecture cụ thể. Binary chứa machine code cùng runtime support và resource mà native-image quyết định giữ lại. Cùng một business application có thể hỗ trợ cả hai dạng, nhưng dependency chạy tốt trên JVM vẫn có thể thiếu reachability metadata khi đi native.

**Purpose:**

Làm rõ đầy đủ nửa native của phép so sánh, gồm platform specificity và runtime support được đóng vào artifact.

## Native Image có thể mang lại lợi ích runtime nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:26`

**Visual:**

Thay sơ đồ artifact bằng dashboard đo cold start, memory, throughput và hành vi khi JVM đã warm.

**Script:**

Khi hai runtime form đã tách bạch, ta mới nên hỏi native mang lại giá trị vận hành gì thay vì coi bản thân binary là lợi ích.

**Purpose:**

Chuyển từ cơ chế sang lợi ích phải đo được.

### Scene 1 — Native Image có thể mang lại lợi ích runtime nào?

**Time:** `02:26–03:06`

**Visual:**

Dashboard workload: cold-start latency và resident memory nổi bật ở native; throughput, tail latency, CPU và warm JVM/JIT nằm ở các đồng hồ riêng để so sánh công bằng.

**Script:**

Native đáng cân nhắc nhất khi cold-start latency hoặc memory footprint là constraint thật. Đưa nhiều bước phân tích framework và compilation về build time thường giúp startup nhanh hơn đáng kể và có thể giảm memory cho workload tương đương. Điều này hữu ích với scale-to-zero, worker ngắn hạn, command application hoặc deployment mật độ cao. Nhưng phải đo trên workload của chính ứng dụng: native không mặc định thắng mọi chỉ số throughput hay latency, còn JVM chạy lâu có thể hưởng lợi từ JIT và tooling trưởng thành.

**Purpose:**

Trình bày lợi ích native như kết quả đo theo workload, không phải lời hứa hiệu năng tuyệt đối.

## Native Image làm tăng chi phí build và khả năng tương thích nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:06–03:15`

**Visual:**

Giữ dashboard lợi ích và trượt vào bên cạnh một bảng cost ledger của build pipeline.

**Script:**

Lợi ích runtime chỉ là một nửa quyết định, vì native chuyển khá nhiều chi phí và rủi ro tương thích về pipeline build.

**Purpose:**

Cân bằng lợi ích runtime với chi phí kỹ thuật cần để đạt được nó.

### Scene 1 — Native Image làm tăng chi phí build và khả năng tương thích nào?

**Time:** `03:15–03:56`

**Visual:**

Cost ledger gồm native compile time, CPU/RAM, build theo platform, hint maintenance, dependency compatibility và native validation; tất cả được trừ khỏi runtime gain.

**Script:**

Native compilation chuyển chi phí sang bên trái. Build lâu hơn, ăn CPU và memory hơn, tạo output theo platform và mở thêm bài toán tương thích quanh reflection, resource, proxy, serialization, JNI, dynamic loading cùng thư viện bên thứ ba. CI có thể cần toolchain riêng và native-specific tests. Câu hỏi nên là kinh tế kỹ thuật: startup, memory hoặc density gain có đủ trả cho feedback chậm hơn, metadata maintenance, dependency readiness và validation bổ sung hay không? Native là một lựa chọn, không phải cột mốc hiện đại hóa bắt buộc.

**Purpose:**

Đóng khung native adoption như một trade-off kỹ thuật có chi phí build và compatibility rõ ràng.

## Module này sở hữu phần nào, và phần nào thuộc GraalVM hoặc công cụ build?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:07`

**Visual:**

Biến cost ledger thành ownership map với native-image ở giữa và các module lân cận xung quanh.

**Script:**

Vì các chi phí này chạm nhiều domain, nền tảng cuối cùng là biết câu hỏi nào thuộc native-image và câu hỏi nào phải bàn giao cho owner khác.

**Purpose:**

Ngăn module native-image nuốt luôn compiler internals, packaging, testing tổng quát và semantics của JVM.

### Scene 1 — Module này sở hữu phần nào, và phần nào thuộc GraalVM hoặc công cụ build?

**Time:** `04:07–04:45`

**Visual:**

Ownership boundary: native-image sở hữu Boot AOT integration, closed-world consequences, hints và native readiness; mũi tên bàn giao GraalVM internals, build tooling, testing tổng quát, lifecycle và Java reflection semantics sang module tương ứng.

**Script:**

Module này sở hữu cách Spring Boot tích hợp với AOT và native execution: chuẩn bị application model, hệ quả closed world, Runtime Hints, đường build và test native được hỗ trợ, cùng cách chẩn đoán readiness. Nó không trở thành owner của internals của GraalVM compiler, Maven/Gradle/Buildpacks tổng quát, chiến lược Boot testing chung, lifecycle ứng dụng thông thường hay semantics của reflection và class loading trong Java. Giữ ranh giới này giúp câu chuyện native liền mạch mà không lặp curriculum của module khác.

**Purpose:**

Chốt phần mở đầu bằng ownership contract rõ ràng cho các section sau và module lân cận.
