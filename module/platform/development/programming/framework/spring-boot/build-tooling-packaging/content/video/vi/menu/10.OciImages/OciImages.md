---
video:
  url: ""
---

# Tạo OCI image bằng Spring Boot

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

## `bootBuildImage` hoặc Maven `build-image` tạo ra điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Từ Boot build input đến OCI application image

**Time:** `00:00–00:24`

**Visual:**

Một lane `bootJar` kết thúc ở executable JAR; lane khác `bootBuildImage / build-image` kết thúc ở OCI image reference. Gắn nhãn “Cloud Native Buildpacks” cho lane image.

**Script:**

`bootBuildImage` và Maven `build-image` dùng Cloud Native Buildpacks để tạo OCI-compatible application image. Image chứa application, runtime layer và launch metadata được builder/buildpacks chuẩn bị. Đây là delivery unit khác executable JAR: điểm bàn giao cuối là image reference để container platform pull và run, không chỉ là đường dẫn tới file JAR.

**Purpose:**

Phân biệt OCI image output với executable archive output của Boot.

## Ứng dụng Boot trở thành OCI image như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:24–00:30`

**Visual:**

Mở rộng nhãn “Buildpacks” thành pipeline nằm giữa application input và final OCI image.

**Script:**

Image task không dịch một Dockerfile ẩn. Nó điều phối Buildpacks lifecycle.

**Purpose:**

Chuyển từ output artifact sang cơ chế tạo image.

### Scene 2 — Buildpacks ghép runtime và application layer

**Time:** `00:30–00:54`

**Visual:**

Animate `Boot application input → builder + buildpacks → detect → reusable layers → run image → OCI image`. Cuối cùng reveal launch process metadata và mũi tên container start.

**Script:**

Builder nhận application input, detect buildpack cần dùng, tạo hoặc reuse application/runtime layer, kết hợp với run image rồi export OCI image. Buildpacks còn đóng góp process metadata dùng để launch application. Nhờ đó team chọn Buildpacks không phải tự lặp lại toàn bộ classpath launch command của Boot trong Dockerfile.

**Purpose:**

Cho thấy đường application-to-image và nguồn gốc của launch process metadata.

## Vì sao quá trình tạo image cần truy cập Docker daemon hoặc container engine tương thích được hỗ trợ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:02`

**Visual:**

Đặt Gradle/Maven process một bên và Docker daemon/container engine một bên, nối bằng socket hoặc remote endpoint.

**Script:**

Plugin điều phối build, nhưng builder vẫn cần một container engine để chạy và để image layer được lưu hoặc publish.

**Purpose:**

Giới thiệu container-engine connection như build prerequisite riêng với application runtime.

### Scene 3 — Tách ba external connection khi chẩn đoán

**Time:** `01:02–01:33`

**Visual:**

Ba arrow: build tool → Docker daemon; builder container → external dependency sources; image task → registry. Thêm label `DOCKER_HOST`, Docker context/TLS và registry credentials.

**Script:**

Trong Spring Boot 3.3, Buildpacks image task cần truy cập Docker daemon qua connection model được hỗ trợ. Ở CI, connection đó phải được cấu hình rõ; Docker Desktop trên máy developer không tự xuất hiện ở remote runner. Khi lỗi xảy ra, hãy tách ba đường: build tool có tới được daemon không, builder container có tới external dependency source không, và image task có tới registry để pull hoặc publish không.

**Purpose:**

Cung cấp connection model cụ thể để phân loại image-build infrastructure failure.

## Tên image, tag và việc publish được cấu hình thế nào ở ranh giới tích hợp của Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:33–01:41`

**Visual:**

Hiển thị local image đã build thành công, sau đó overlay full image name gồm registry host, repository, tag và digest.

**Script:**

Khi image đã build xong, danh tính của nó trở thành contract giữa build và các delivery system phía sau.

**Purpose:**

Chuyển từ image construction sang naming, publication và traceability.

### Scene 4 — Bàn giao image bằng danh tính immutable

**Time:** `01:41–02:17`

**Visual:**

Hiển thị Boot config thiết lập image name, additional tags, publish flag và registry credentials. Thêm default-name callout: Gradle `docker.io/library/${project.name}:${project.version}`, Maven dùng dạng artifactId/version tương ứng. Đánh dấu publish là opt-in và chuyển việc lưu/rotate credential sang CI secret box. Sau đó so mutable tag với immutable digest.

**Script:**

Boot có default image name dùng được ngay—Gradle suy ra từ project name/version, Maven từ artifactId/version—và build có thể override tên hoặc thêm tag. Publish là opt-in; khi bật, plugin có thể truyền registry credentials nhưng CI/build environment vẫn nên là nơi lưu và rotate secret. Retention, signing, promotion policy và tag governance vẫn thuộc registry hoặc DevOps. Dù dùng naming rule nào, delivery system nên giữ immutable digest association để biết image được promote chính là image build đã tạo.

**Purpose:**

Kết nối default naming, opt-in publication và credential handoff của Boot với image delivery có traceability mà không kéo registry governance vào module.
