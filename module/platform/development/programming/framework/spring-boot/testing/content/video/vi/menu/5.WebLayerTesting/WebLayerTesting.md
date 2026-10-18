---
video:
  url: ""
---

# Kiểm thử web tập trung với `@WebMvcTest` và `@WebFluxTest`

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## `@WebMvcTest` chọn những thành phần nào cho kiểm thử MVC?

<!-- VIDEO_SECTION -->

### Scene 1 — `@WebMvcTest` chọn những thành phần nào cho kiểm thử MVC?

**Time:** `00:00–00:48`

**Visual:**

Vẽ MVC slice graph: controller/advice/filter/converter và MVC infrastructure được chọn nằm sáng bên trong; service và repository layer bị làm mờ ngoài slice.

**Script:**

`@WebMvcTest` tạo một Spring MVC test slice tập trung. Nó chọn controller cùng web hạ tầng liên quan và loại phần lớn service, repository cùng auto-configuration không liên quan. Slice này đúng để kiểm tra request mapping, validation integration, serialization, controller advice, filter nằm trong phạm vi web slice và MVC configuration ở tầng Boot integration mà không cần khởi động toàn bộ application.

**Purpose:**

Cho thấy chính xác `@WebMvcTest` giữ lại gì trong MVC slice để kiểm tra mapping, validation, serialization, advice, filter và MVC configuration mà không kéo theo layer không liên quan.

## Cung cấp đối tượng cộng tác của controller cho `@WebMvcTest` như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:48–01:03`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: put the controller in the center, then replace an excluded service collaborator with `@MockBean` entering through normal dependency injection while the slice boundary stays fixed.

**Script:**

`@WebMvcTest` thu hẹp MVC layer, nên câu hỏi ngay sau đó là collaborator mà controller vẫn cần được cung cấp thế nào trong context hạn chế này.

**Purpose:**

Cho thấy collaborator bị loại khỏi web slice được đưa trở lại tường minh thay vì vô tình mở rộng component scanning.

### Scene 1 — Cung cấp đối tượng cộng tác của controller cho `@WebMvcTest` như thế nào?

**Time:** `01:03–02:03`

**Visual:**

Put the controller in the center, then replace an excluded service collaborator with `@MockBean` entering through normal dependency injection while the slice boundary stays fixed.

**Script:**

Vì service và repository thường nằm ngoài slice, đối tượng cộng tác của controller phải được cung cấp rõ ràng. Một lựa chọn riêng của Boot thường dùng là `@MockBean`, dùng để thêm hoặc thay bean trong test context để controller vẫn nhận dependency qua dependency injection thường. Nếu phải import ngày càng nhiều đối tượng cộng tác production chỉ để web slice chạy được, hãy xem lại ranh giới. Hành vi đó có thể cần integration context lớn hơn thay vì một web slice được “xây lại” quá nhiều.

**Purpose:**

Giải thích cách controller collaborator được đưa vào `@WebMvcTest`, phân biệt test double/import có chủ đích với component mà slice cố ý loại bỏ.

## `@WebFluxTest` chọn những thành phần nào cho kiểm thử web reactive?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:03–02:18`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: draw the reactive web slice: selected WebFlux controller/router infrastructure and codecs inside; unrelated application layers outside.

**Script:**

Khi collaborator phía MVC đã rõ, hãy so reactive equivalent để cùng ý tưởng focused testing không bị hiểu là cùng một hạ tầng.

**Purpose:**

Đối chiếu MVC slice với reactive slice nhưng giữ nguyên nguyên tắc focused testing.

### Scene 1 — `@WebFluxTest` chọn những thành phần nào cho kiểm thử web reactive?

**Time:** `02:18–03:15`

**Visual:**

Vẽ reactive web slice: WebFlux controller/router infrastructure và codec được chọn nằm bên trong; application layer không liên quan nằm ngoài.

**Script:**

`@WebFluxTest` là phiên bản tập trung tương ứng cho reactive web. Nó nạp hạ tầng hướng tới WebFlux và các component web reactive được chọn nhưng loại các tầng ứng dụng không liên quan. Chọn khi mục tiêu là hành vi routing/controller, codec, validation integration, xử lý exception hoặc reactive web configuration ở ranh giới framework. Ngữ nghĩa của reactive pipeline và hành vi Reactor tự thân vẫn nằm ngoài phạm vi trách nhiệm của Boot testing.

**Purpose:**

Phản chiếu mô hình MVC sang reactive stack bằng cách chỉ ra `@WebFluxTest` giữ gì và vì sao boundary của nó khác full reactive application context.

## Web slice auto-configure những test client nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:15–03:30`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: compare `@WebMvcTest` → `MockMvc` with `@WebFluxTest` → `WebTestClient`; keep both paths in-process with no live server socket.

**Script:**

Mỗi web slice đi kèm execution model riêng, vì vậy bước kế tiếp là nhận diện client Boot auto-configure cho model đó.

**Purpose:**

Ghép mỗi web slice với client Boot auto-configure cho đúng request-processing model.

### Scene 1 — Web slice auto-configure những test client nào?

**Time:** `03:30–04:19`

**Visual:**

Đối chiếu `@WebMvcTest` → `MockMvc` với `@WebFluxTest` → `WebTestClient`; giữ cả hai đường trong cùng process và không có live server socket.

**Script:**

`@WebMvcTest` auto-configure `MockMvc`, cho phép chạy MVC request processing mà không khởi động HTTP server thật. `@WebFluxTest` có thể auto-configure `WebTestClient` cho reactive web slice. Boot chịu trách nhiệm làm các client này có mặt trong test context được chọn. Request builder, exchange, expectation và assertion API chi tiết thuộc phần hỗ trợ kiểm thử web tương ứng của Spring.

**Purpose:**

Ghép test client được web slice auto-configure với execution model tương ứng của MVC hoặc WebFlux.

## Khi nào web slice là đủ và khi nào cần server thật?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:19–04:34`

**Visual:**

Giữ context boundary hiện tại, rồi co/giãn nó để làm lộ quyết định scope kế tiếp: place mock web processing and live-server HTTP on parallel lanes; highlight socket, connector, and server-configuration concerns as the reasons to widen the test.

**Script:**

Slice client vẫn nằm trong framework boundary; nếu assertion phụ thuộc socket hay connector thật thì test phải mở rộng sang real-server fidelity.

**Purpose:**

Dùng mô hình client in-process để xác định chính xác rủi ro nào buộc test phải mở rộng sang server thật.

### Scene 1 — Khi nào web slice là đủ và khi nào cần server thật?

**Time:** `04:34–05:29`

**Visual:**

Đặt mock web processing và live-server HTTP trên hai lane song song; tô sáng socket, connector và server-configuration concern là lý do phải mở rộng test.

**Script:**

Web slice là đủ khi hành vi có thể được chứng minh hoàn toàn trong ranh giới request processing của framework: mapping, validation, serialization, advice, security integration đã cấu hình cho slice hoặc sự cộng tác của controller. Chọn `@SpringBootTest` chạy server thật khi assertion phụ thuộc hành vi của embedded server, ranh giới network thật, server configuration, tương tác HTTP client/server hoặc wiring liên tầng mà slice chủ động loại bỏ.

**Purpose:**

Đưa ra rule về fidelity để biết khi nào web slice đã đủ và khi nào behavior cần real embedded server.

## Cấu hình web slice của Boot bàn giao sang cơ chế test MVC hoặc WebFlux ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:29–05:43`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Quyết định scope này cũng đánh dấu ownership handoff: Boot dựng slice, còn MVC/WebFlux testing sở hữu request-processing mechanics bên trong.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Cấu hình web slice của Boot bàn giao sang cơ chế test MVC hoặc WebFlux ở đâu?

**Time:** `05:43–06:30`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Ở phía Boot, phần chịu trách nhiệm là việc chọn component/auto-configuration cho `@WebMvcTest` hoặc `@WebFluxTest` và auto-configure test client tương ứng. Spring Framework sở hữu cách `MockMvc` và `WebTestClient` thực thi request và assertion. Production MVC/WebFlux request pipeline thuộc các Spring Framework web module. Boot testing chỉ tạo môi trường tập trung để pipeline đó được kiểm thử.

**Purpose:**

Vẽ ranh giới ownership giữa Boot web-slice configuration với mechanics sâu hơn của MVC/WebFlux testing khi request thực sự được xử lý.