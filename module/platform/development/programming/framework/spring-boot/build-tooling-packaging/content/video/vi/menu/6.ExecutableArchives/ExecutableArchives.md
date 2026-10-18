---
video:
  url: ""
---

# Executable archive và Spring Boot Loader

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

## Vì sao Spring Boot dùng nested archive?

<!-- VIDEO_SECTION -->

### Scene 1 — Giữ dependency riêng biệt nhưng vẫn bàn giao một file

**Time:** `00:00–00:31`

**Visual:**

Hiển thị ba dependency JAR và application classes. Đầu tiên flatten tất cả vào một archive với warning về resource/metadata collision; sau đó reset và đặt các dependency JAR nguyên vẹn bên trong một Boot executable archive.

**Script:**

Một Boot application có thể phụ thuộc vào hàng chục JAR. Nếu flatten tất cả file của chúng vào một archive lớn, boundary giữa artifact bị mất và resource hoặc metadata có thể va chạm. Spring Boot giữ dependency dưới dạng nested JAR để vẫn phân phối được một file nhưng dependency còn là những unit dễ nhận diện. Chính cấu trúc này tạo nhu cầu cho Boot Loader ở bước launch.

**Purpose:**

Giải thích động cơ của nested archive trước khi xem cấu trúc vật lý của nó.

## `BOOT-INF` và `WEB-INF` được tổ chức như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:31–00:40`

**Visual:**

Zoom vào executable archive và chuyển nó thành file-tree view.

**Script:**

Ý tưởng nested archive trở nên rất cụ thể khi ta mở artifact và nhìn vào các directory Boot thực sự tạo.

**Purpose:**

Chuyển từ packaging concept sang evidence quan sát được trong archive.

### Scene 2 — Đọc archive tree để chẩn đoán packaging

**Time:** `00:40–01:08`

**Visual:**

JAR tree có `BOOT-INF/classes` và `BOOT-INF/lib/*.jar`; WAR tree có `WEB-INF/classes`, `WEB-INF/lib`, `WEB-INF/lib-provided`. Highlight một class và một dependency được kỳ vọng.

**Script:**

Trong executable JAR, application classes và resources nằm dưới `BOOT-INF/classes`, còn runtime dependency JAR nằm ở `BOOT-INF/lib`. WAR dùng các vị trí `WEB-INF` theo servlet model và có thể tách dependency do external container cung cấp. Ta không tạo các folder này bằng tay, nhưng mở archive để kiểm tra là evidence rất tốt khi class hoặc dependency bị đóng gói sai chỗ hay biến mất.

**Purpose:**

Dạy cách dùng BOOT-INF và WEB-INF như diagnostic evidence thay vì cấu trúc cần hand-author.

## Vì sao cần Spring Boot Loader?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:08–01:18`

**Visual:**

Giữ nested JAR trên màn hình rồi cho thấy JVM `java -jar` thông thường không tự xem các nested JAR đó như classpath entry bình thường.

**Script:**

Giữ dependency nested giải quyết bài toán phân phối, nhưng đồng thời tạo một bài toán launch mà JVM thông thường không tự xử lý.

**Purpose:**

Nối trực tiếp archive layout với nhu cầu có Spring Boot Loader.

### Scene 3 — Loader dựng classpath trước khi Spring runtime bắt đầu

**Time:** `01:18–02:01`

**Visual:**

Animate `java -jar` → manifest → Boot loader → nested libraries/classpath → `Start-Class` → `SpringApplication`. Vẽ checkpoint đậm trước `SpringApplication`. Thêm một dependency ngoại lệ gắn `requiresUnpack` được Loader đưa ra temporary filesystem location trước khi load.

**Script:**

Spring Boot Loader hiểu executable archive layout, dựng effective classpath từ các vị trí đã package và gọi application main class. Toàn bộ việc này xảy ra trước khi Spring container tồn tại. Phần lớn dependency chạy trực tiếp khi vẫn nested, nhưng một số library cần filesystem access thật; Boot packaging có thể đánh dấu chúng bằng `requiresUnpack` để Loader giải nén ra vị trí tạm lúc runtime. Đây là compatibility mechanism ngoại lệ, không phải optimization chung. Nếu Loader không tìm được dependency hoặc chưa gọi được `Start-Class`, hãy kiểm manifest và archive layout trước Spring bean creation.

**Purpose:**

Định vị Loader như pre-runtime launch boundary và khôi phục `requiresUnpack` cho trường hợp nested-library compatibility đặc biệt.

## Classpath index và layer index mô tả điều gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:10`

**Visual:**

Reveal `classpath.idx` và `layers.idx` bên cạnh archive tree mà không thay đổi file đã package.

**Script:**

Ngoài classes và libraries, archive có thể chứa metadata mô tả cách những nội dung đã đóng gói được sắp xếp hoặc phân nhóm.

**Purpose:**

Giới thiệu index file như metadata phủ lên artifact đã build.

### Scene 4 — Một index cho thứ tự classpath, một index cho layer

**Time:** `02:10–02:38`

**Visual:**

`classpath.idx` nối tới thứ tự nested classpath; `layers.idx` nối tới logical layer membership. Giữ Gradle/Maven dependency declarations nằm ngoài hai sơ đồ.

**Script:**

Classpath index có thể ghi thứ tự rõ ràng cho các nested classpath entry. Layer index ghi file đã package thuộc logical layer nào để phục vụ extraction hoặc image construction. Cả hai không thay dependency graph của Gradle hay Maven; chúng mô tả artifact đã được tạo. Vì vậy khi index sai, hãy truy về packaging input thay vì sửa trực tiếp generated index.

**Purpose:**

Phân biệt launch-order metadata, layer-grouping metadata và source dependency declarations.

## Khi nào việc giải nén executable archive trở nên quan trọng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:38–02:45`

**Visual:**

Cho single executable JAR bung ra thành application JAR và thư mục `lib` riêng.

**Script:**

Single-file nested layout rất tiện, nhưng một số production environment phù hợp hơn với dạng đã extract.

**Purpose:**

Nối executable archive structure với extraction workflow được Boot hỗ trợ.

### Scene 5 — Extraction đổi delivery layout, không đổi Spring semantics

**Time:** `02:45–03:10`

**Visual:**

Hiển thị `java -Djarmode=tools -jar app.jar extract`, sau đó layout đã extract với external libraries và application JAR. Cuối cùng launch application rồi nối lại cùng Spring runtime diagram.

**Script:**

Spring Boot 3.3 hỗ trợ extract executable application bằng tools jar mode. Layout hiệu quả có thể tách libraries khỏi application JAR, phù hợp với platform muốn cache file riêng hoặc giảm một phần overhead đọc nested archive. Điều thay đổi là delivery layout. Khi process đã đi vào application main class, Spring runtime model vẫn như cũ.

**Purpose:**

Cho thấy extraction là lựa chọn packaging được hỗ trợ nhưng không tạo ra một application runtime khác.
