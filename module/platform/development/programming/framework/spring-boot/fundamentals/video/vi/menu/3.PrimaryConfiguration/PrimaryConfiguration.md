---
video:
  url: ""
---

# Cấu hình chính và @SpringBootApplication

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

## Lớp cấu hình chính là gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Điểm định hướng của cấu hình ứng dụng

**Time:** `00:00–00:55`

**Visual:**

Mở `OrdersApplication.java` trong package `com.example.orders`. Highlight `@SpringBootApplication`, class `OrdersApplication` và lời gọi `SpringApplication.run(OrdersApplication.class, args)`. Sau đó zoom ra cây package có `web`, `service`, `persistence`.

**Script:**

Phần lớn ứng dụng Boot chọn một class làm primary configuration và cũng dùng class đó làm bootstrap source. Từ đây, `SpringApplication` có điểm bắt đầu rõ ràng để nạp cấu hình, còn Spring có một vị trí hợp lý để khám phá component và cấu hình bổ sung. Gọi là primary không có nghĩa mọi bean phải viết trong file này. Nó là điểm định hướng của toàn ứng dụng, không phải nơi chứa toàn bộ cấu hình.

**Purpose:**

Giải thích vai trò của primary configuration class như điểm định hướng thay vì một file cấu hình nguyên khối.

## @SpringBootApplication tổng hợp những gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Tách annotation `@SpringBootApplication` thành ba mảnh màu riêng trên màn hình.

**Script:**

Class chính trông rất ngắn vì một annotation đang gom nhiều trách nhiệm. Muốn dùng đúng, ta cần mở annotation đó ra thành từng phần.

**Purpose:**

Chuyển từ vai trò của primary class sang cấu tạo của `@SpringBootApplication`.

### Scene 2 — Ba trách nhiệm trong một composed annotation

**Time:** `01:05–02:00`

**Visual:**

Hiện bảng ba dòng: `@SpringBootConfiguration → primary Spring configuration`, `@EnableAutoConfiguration → bật Boot auto-configuration`, `@ComponentScan → khám phá component từ package boundary`. Dùng mũi tên để cho thấy component scanning thuộc Spring Framework, auto-configuration là phần Boot.

**Script:**

`@SpringBootApplication` là composed annotation. Ở mức nhập môn, nó gom ba vai trò: `@SpringBootConfiguration` đánh dấu cấu hình chính của ứng dụng Boot; `@EnableAutoConfiguration` bật cơ chế auto-configuration; và `@ComponentScan` yêu cầu Spring khám phá component theo ranh giới package. Ba cơ chế này phối hợp, nhưng chúng không phải một khối không thể tách. Chính sự tổng hợp đó làm một ứng dụng nhỏ có thể bắt đầu với rất ít annotation.

**Purpose:**

Làm rõ ba vai trò cấu thành và ranh giới Spring Framework/Boot bên trong annotation tiện ích.

## Vì sao root package quan trọng?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Giữ mảnh `@ComponentScan`, đặt nó lên cây package `com.example.orders` và highlight các subpackage.

**Script:**

Trong ba phần vừa tách ra, component scanning dẫn tới một câu hỏi rất thực tế: đặt primary class ở package nào để phạm vi ứng dụng được nhìn thấy đúng?

**Purpose:**

Nối composition của annotation với ảnh hưởng thực tế của package placement.

### Scene 3 — Package placement tạo phạm vi scan mặc định

**Time:** `02:10–03:05`

**Visual:**

Hiện cây `com.example.orders` với primary class ở root và các subpackage `web/service/persistence` được tô sáng. Sau đó di chuyển primary class xuống `com.example.orders.web` để các sibling package mờ đi. Cuối cùng hiện cảnh báo `default package` với vùng scan quá rộng.

**Script:**

Khi primary class nằm ở `com.example.orders`, component scanning mặc định tự nhiên bao phủ các package con như `web`, `service` và `persistence`. Nếu đặt class quá sâu, các package ngang cấp có thể nằm ngoài phạm vi scan. Còn default package thì nên tránh vì phạm vi tìm kiếm trở nên quá rộng và có thể gây vấn đề cho những cơ chế dựa trên base package. Vì thế root package hợp lý vừa giảm cấu hình thủ công, vừa làm ranh giới của ứng dụng dễ nhìn.

**Purpose:**

Biến quy ước root package thành bằng chứng cấu trúc cụ thể và chỉ ra hai failure mode phổ biến.

## Có thể tùy biến các mặc định được tổng hợp như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:15`

**Visual:**

Từ cây package chuẩn, thêm một package `com.example.shared` nằm ngoài root hiện tại và đặt dấu hỏi cạnh nó.

**Script:**

Quy ước root package giải quyết trường hợp phổ biến. Nhưng khi cấu trúc thật của ứng dụng vượt ra ngoài ranh giới đó, Boot vẫn cho phép điều chỉnh rõ ràng.

**Purpose:**

Chuyển từ default package convention sang các điểm tùy biến có chủ đích.

### Scene 4 — Tùy biến khi cấu trúc thật sự yêu cầu

**Time:** `03:15–04:05`

**Visual:**

Hiện snippet `@SpringBootApplication(scanBasePackages = {"com.example.orders", "com.example.shared"})`. Sau đó hiện callout `exclude selected auto-configurations` và một sơ đồ tách `@SpringBootConfiguration`, `@EnableAutoConfiguration`, `@ComponentScan` thành annotation riêng.

**Script:**

Nếu cấu trúc thật sự cần nhiều package root, `scanBasePackages` có thể mở rộng phạm vi component scan. Ứng dụng cũng có thể loại trừ một số auto-configuration hoặc, trong kiến trúc đặc biệt, thay composed annotation bằng các annotation cấu thành để điều khiển riêng từng phần. Những tùy chọn điều chỉnh này nên phản ánh cấu trúc có thật, chứ không phải vá cho một package layout khó hiểu.

**Purpose:**

Cho thấy convention là mặc định có thể tùy biến và nhấn mạnh việc tùy biến phải phục vụ cấu trúc ứng dụng thực tế.

## Chi tiết auto-configuration bắt đầu từ đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Zoom vào `@EnableAutoConfiguration`, nhưng đặt một biển chỉ dẫn `Fundamentals stops here → auto-configuration module` trước vùng condition graph.

**Script:**

Ta đã thấy `@EnableAutoConfiguration` bật cơ chế của Boot. Bước tiếp theo không còn là cấu trúc primary class nữa, mà là vì sao một cấu hình cụ thể được chọn hay bị bỏ qua.

**Purpose:**

Đặt boundary rõ giữa phần giới thiệu auto-configuration trong Fundamentals và mechanics thuộc module chuyên trách.

### Scene 5 — Bật cơ chế không đồng nghĩa mọi bean đều được tạo

**Time:** `04:15–05:00`

**Visual:**

Hiện `@EnableAutoConfiguration` mở một cánh cửa. Phía sau là các thẻ `classpath`, `configuration inputs`, `existing beans`, `conditions`, nhưng phần `condition evaluation / ordering / back-off / exclusions / report` được gom vào khung `auto-configuration module`.

**Script:**

Trong Fundamentals, chỉ cần nhớ rằng `@EnableAutoConfiguration` cho phép Boot đóng góp cấu hình dựa trên context, classpath, đầu vào cấu hình và bean đã có. Bản thân annotation không phải bộ máy quyết định và cũng không có nghĩa mọi bean khả dĩ đều được đăng ký. Khi câu hỏi chuyển thành condition nào match, vì sao Boot back off, thứ tự ra sao hay Condition Evaluation Report nói gì, hãy chuyển sang module `auto-configuration`.

**Purpose:**

Khóa đúng mental model của `@EnableAutoConfiguration` và handoff phần quyết định chi tiết sang owner phù hợp.
