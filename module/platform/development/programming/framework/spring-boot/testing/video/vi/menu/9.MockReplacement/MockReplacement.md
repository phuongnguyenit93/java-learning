---
video:
  url: ""
---

# Thay thế bean bằng mock và bọc bean bằng spy trong kiểm thử Spring Boot

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Khi nào nên dùng `@MockBean` để thêm hoặc thay thế bean trong test context?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi nào nên dùng `@MockBean` để thêm hoặc thay thế bean trong test context?

**Time:** `00:00–00:59`

**Visual:**

Vẽ bean graph trước và sau `@MockBean`: collaborator mục tiêu được thay hoặc thêm, còn consumer hiện có được nối lại tới Mockito mock qua dependency injection thông thường.

**Script:**

`@MockBean` tích hợp Mockito mock vào Spring Boot test `ApplicationContext`. Nó có thể thêm bean mới khi chưa có bean đúng hoặc thay một bean hiện có để Boot-managed component nhận mock qua dependency injection thường. Chọn khi ranh giới test có Spring context nhưng một đối tượng cộng tác cần được kiểm soát thay vì khởi động thật. Web slice thường thay đối tượng cộng tác service theo cách này. Nếu test chỉ là unit test thuần và không cần Spring context, hãy dùng Mockito trực tiếp.

**Purpose:**

Cho thấy lúc `@MockBean` phù hợp để thay hoặc thêm collaborator bên trong Spring test context thay vì chỉ mock object nằm ngoài Spring.

## Khi nào nên dùng `@SpyBean` để bọc bean hiện có?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:59–01:14`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: wrap the existing bean with a Mockito spy while leaving its real dependencies and behavior in place; mark only the interactions being stubbed or verified.

**Script:**

Full mock thay collaborator hoàn toàn; đối chiếu tiếp với `@SpyBean`, nơi real bean vẫn tồn tại và chỉ một phần behavior bị can thiệp.

**Purpose:**

Đối chiếu replacement hoàn toàn với việc bọc real bean để chỉ dùng spy khi real behavior phải được giữ lại.

### Scene 1 — Khi nào nên dùng `@SpyBean` để bọc bean hiện có?

**Time:** `01:14–02:15`

**Visual:**

Bọc bean hiện có bằng Mockito spy nhưng giữ real dependency và behavior; chỉ đánh dấu interaction được stub hoặc verify.

**Script:**

`@SpyBean` giữ bean thật trong Boot context nhưng bọc nó bằng Mockito spy. Cách này hữu ích khi phần lớn hành vi production vẫn nên chạy bình thường nhưng test cần quan sát tương tác hoặc ghi đè một phần nhỏ hành vi. Vì bean thật và dependency của nó vẫn tồn tại, spy không phải một mock rẻ hơn. Đây là lựa chọn thiên về integration và có thể tương tác với Spring proxy hoặc caching hạ tầng; chi tiết về Mockito stubbing và proxy unwrapping thuộc các module chuyên trách tương ứng.

**Purpose:**

Phân biệt `@SpyBean` với full replacement bằng cách giữ real bean và chỉ can thiệp một phần behavior.

## Việc thay thế bean làm Boot test context thay đổi như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:30`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: compare two cached-context identities: same application configuration, different mock/spy definitions, therefore different effective bean graphs.

**Script:**

Dù dùng mock hay spy, hệ quả quan trọng là replacement thay Spring bean graph chứ không chỉ thay code cục bộ trong test.

**Purpose:**

Chuyển từ lựa chọn mock hay spy sang hệ quả chung: cả hai đều thay effective Spring bean graph.

### Scene 1 — Việc thay thế bean làm Boot test context thay đổi như thế nào?

**Time:** `02:30–03:30`

**Visual:**

Đối chiếu hai cached-context identity: cùng application configuration nhưng mock/spy definition khác nhau, vì vậy effective bean graph cũng khác.

**Script:**

Các định nghĩa mock và spy là context customizer. Chúng thay đổi đồ thị bean thực tế mà Spring tạo cho test và vì vậy ảnh hưởng đến định danh của cached test context. Cách này hữu ích vì bean thay thế tham gia autowiring thường, nhưng cũng có nghĩa hai test gần như giống nhau mà có bộ mock/spy khác nhau có thể không tái sử dụng cùng context. Hãy xem việc thay dependency như một phần của thiết kế test context.

**Purpose:**

Làm rõ bean replacement là context-level operation: injection target, bean graph và context identity đều thay đổi khi thêm mock hoặc spy.

## Vì sao `@MockBean` không thể cấu hình lại hành vi cần dùng ngay trong lúc context refresh?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:30–03:45`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: dùng timeline refresh: đăng ký mock bean → dependent bean khởi tạo trong context refresh → test method mới stub sau đó; đánh dấu vì sao stubbing ở test method không thể thay behavior lúc refresh.

**Script:**

Vì replacement tham gia vào context construction, timing rất quan trọng: một số behavior cần trong refresh không thể chờ mock của context đã dựng xong.

**Purpose:**

Chuyển từ replacement semantics sang lifecycle timing để không nhầm behavior lúc refresh với stubbing trong test method.

### Scene 1 — Vì sao `@MockBean` không thể cấu hình lại hành vi cần dùng ngay trong lúc context refresh?

**Time:** `03:45–04:43`

**Visual:**

Dùng timeline refresh: đăng ký mock bean → dependent bean khởi tạo trong context refresh → test method mới stub sau đó; đánh dấu vì sao stubbing ở test method không thể thay behavior lúc refresh.

**Script:**

`@MockBean` tạo mock trước context refresh, nhưng stubbing trong test method chỉ diễn ra sau khi context đã refresh xong. Nếu một bean khác cần một giá trị trả về cụ thể từ mock ngay trong lúc chính nó khởi tạo, cấu hình hành vi ở test method là quá muộn. Boot khuyến nghị tạo và cấu hình mock đó bằng `@Bean` trong test configuration. Khi đó hành vi cần thiết đã tồn tại trước khi bean phụ thuộc khởi tạo.

**Purpose:**

Giải thích giới hạn refresh-time của `@MockBean` để behavior cần trước khi context dựng xong không bị giao nhầm cho mock xuất hiện quá muộn.

## Định nghĩa mock và spy ảnh hưởng cache key của test context như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:43–04:58`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: show the context-cache key gaining a mock/spy customizer component; change the replacement set and animate a cache miss/new context startup.

**Script:**

Khi replacement được xem là context configuration, ảnh hưởng của nó tới cache identity trở thành hệ quả trực tiếp.

**Purpose:**

Nối replacement như context configuration với cache-key consequence có thể làm tăng số lần context startup.

### Scene 1 — Định nghĩa mock và spy ảnh hưởng cache key của test context như thế nào?

**Time:** `04:58–06:04`

**Visual:**

Hiện context-cache key có thêm mock/spy customizer; đổi replacement set rồi animate cache miss và một context startup mới.

**Script:**

Spring context cache tính cả các context customizer thực tế, trong đó có định nghĩa mock/spy do Boot quản lý. Vì vậy các bộ thay thế khác nhau có thể tạo cache key khác nhau và làm phát sinh thêm lần khởi động context. Một bộ test có nhiều test, mỗi test khai báo mock hơi khác nhau, có thể chậm đáng kể dù từng test nhỏ. Hãy tái sử dụng cấu trúc context ổn định khi hợp lý và dùng unit test thuần nếu tích hợp Spring không phải hành vi cần chứng minh.

**Purpose:**

Nối mock/spy definition với test-context cache key để bộ replacement khác nhau được nhận diện là nguyên nhân tạo thêm context startup.

## Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:04–06:19`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Cache behavior vẫn không biến Boot thành owner của mock semantics, nên cuối cùng phải tách Boot bean integration khỏi stubbing/verification của Mockito.

**Purpose:**

Dùng hệ quả ở cache key để khép phần tích hợp của Boot, rồi bàn giao stubbing, verification và spy semantics về đúng owner là Mockito.

### Scene 1 — Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?

**Time:** `06:19–07:16`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Phạm vi thay thế bean của Boot kết thúc ở đâu và hành vi Mockito bắt đầu ở đâu?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Ở phía Boot, phần chịu trách nhiệm là phần tích hợp dùng để đăng ký Mockito mock/spy thành bean, inject chúng vào test và reset mock theo hỗ trợ test của Boot. Mockito vẫn chịu trách nhiệm về cách mock được tạo, stub, verify, match argument và spy. Các câu hỏi như `when` so với `doReturn`, argument matcher, strictness, verification mode hay ngữ nghĩa spy thuộc module Mockito. Boot testing chỉ giải thích cách test double đi vào Spring application context.

**Purpose:**

Vẽ ownership boundary: Boot tích hợp mock/spy bean vào context, còn Mockito sở hữu stubbing, verification và behavior của test double.
