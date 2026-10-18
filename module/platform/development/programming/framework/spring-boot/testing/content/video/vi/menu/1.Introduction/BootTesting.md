---
video:
  url: ""
---

# Bootstrap test và phát hiện cấu hình trong Spring Boot

<!--
VIDEO SCRIPT FORMAT
- Each H2 maps 1:1 to the current Knowledge H2 order.
- The first section contains a Scene; later sections contain a conceptual Transition and a Scene.
- Timing is estimated from spoken density and must remain sequential.
-->

## Spring Boot bổ sung gì cho kiểm thử có application context?

<!-- VIDEO_SECTION -->

### Scene 1 — Spring Boot bổ sung gì cho kiểm thử có application context?

**Time:** `00:00–01:10`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Spring Boot bổ sung gì cho kiểm thử có application context?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Spring Framework đã cho ta TestContext Framework để nạp và tái sử dụng application context trong test. Boot bổ sung thêm một tầng nhận biết Boot phía trên nền tảng đó: test có thể khởi động context qua `SpringApplication`, tìm cấu hình Boot chính, áp dụng external configuration và auto-configuration của Boot, đồng thời dùng các annotation tập trung cho từng phần của application. Câu hỏi trọng tâm của module này vì thế là “khi test cần hạ tầng application thì Boot nên tham gia ở mức nào?”. Cơ chế thực thi của JUnit, thư viện assertion, ngữ nghĩa stubbing của Mockito và vòng đời chung của TestContext vẫn thuộc các module chuyên trách.

**Purpose:**

Phân biệt nền tảng Spring TestContext với phần ghép nối Boot-aware để người học hiểu vì sao auto-configuration, environment và cấu hình gần production có thể tham gia vào test.

## Vì sao cần bootstrap test theo mô hình của Boot?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:10–01:25`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao cần bootstrap test theo mô hình của Boot?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Spring TestContext cung cấp lifecycle nhưng chưa tự tái hiện environment processing và auto-configuration của Boot; khoảng trống đó chính là lý do cần Boot-aware test bootstrap.

**Purpose:**

Làm lộ phần hành vi khởi động của Boot mà Spring TestContext không tự tái hiện, để người học hiểu vì sao phần kế tiếp cần một bootstrap model nhận biết Boot.

### Scene 1 — Vì sao cần bootstrap test theo mô hình của Boot?

**Time:** `01:25–02:30`

**Visual:**

Chỉ hiện đoạn code/cấu hình nhỏ nhất cần cho “Vì sao cần bootstrap test theo mô hình của Boot?”, rồi nối input đó với test-context hoặc runtime boundary kết quả.

**Script:**

Một Boot application production thường phụ thuộc nhiều hơn việc đăng ký bean thường. Nó có thể dựa vào externalized configuration, auto-configuration, environment detection, configuration-properties binding và conditional hạ tầng. Nếu chỉ nạp một Spring context tổng quát, test có thể bỏ qua chính các hành vi Boot mà application production sử dụng. Bootstrap theo mô hình Boot giúp test tái hiện phần luồng khởi động cần thiết mà không phải tự lắp ráp lại bằng tay. Mức độ sát thực tế vẫn phải theo mục tiêu: có test cần full Boot context, có test nên chủ động dùng slice nhỏ hơn.

**Purpose:**

Chỉ ra vì sao một Spring context thông thường có thể bỏ sót hành vi do Boot tạo ra, từ đó nhận diện đúng lỗi nằm ở bootstrap fidelity chứ không phải assertion.

## `spring-boot-test`, `spring-boot-test-autoconfigure` và `spring-boot-starter-test` liên hệ với nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:30–02:45`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: vẽ sơ đồ phụ thuộc ba tầng: spring-boot-test và spring-boot-test-autoconfigure ở dưới, spring-boot-starter-test ở trên; tách nhánh JUnit Jupiter, AssertJ, Hamcrest và Mockito thành các thư viện độc lập.

**Script:**

Khi đã cần bootstrap fidelity, câu hỏi thực tế tiếp theo là module Boot nào cung cấp core test support, focused test auto-configuration và testing stack thường dùng.

**Purpose:**

Chuyển từ lý do cần Boot-aware bootstrap sang các test module cung cấp khả năng đó, đồng thời tách Boot module khỏi những testing library độc lập mà starter gom lại.

### Scene 1 — `spring-boot-test`, `spring-boot-test-autoconfigure` và `spring-boot-starter-test` liên hệ với nhau thế nào?

**Time:** `02:45–03:45`

**Visual:**

Vẽ sơ đồ phụ thuộc ba tầng: spring-boot-test và spring-boot-test-autoconfigure ở dưới, spring-boot-starter-test ở trên; tách nhánh JUnit Jupiter, AssertJ, Hamcrest và Mockito thành các thư viện độc lập.

**Script:**

`spring-boot-test` chứa hỗ trợ kiểm thử cốt lõi của Boot như `@SpringBootTest` và các tiện ích test riêng của Boot. `spring-boot-test-autoconfigure` chứa slice annotation cùng test auto-configuration cho context tập trung. Phần lớn dự án dùng `spring-boot-starter-test` thay vì chọn từng module. Starter kéo vào cả hai module test của Boot cùng các thư viện thường dùng như JUnit Jupiter, AssertJ, Hamcrest và Mockito. Các thư viện này vẫn có phạm vi trách nhiệm riêng dù starter giúp dùng chúng cùng nhau thuận tiện.

**Purpose:**

Làm rõ vai trò của `spring-boot-test`, `spring-boot-test-autoconfigure`, `spring-boot-starter-test` và các thư viện test bên ngoài để không nhầm dependency composition với ownership.

## `@SpringBootTest` bootstrap qua `SpringApplication` như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:59`

**Visual:**

Nối dài lifecycle/timeline hiện tại sang phase kế tiếp thay vì dựng lại từ đầu: dùng timeline bootstrap: test annotation → Boot test bootstrapper → `SpringApplication` → Environment → configuration sources → auto-configuration → `ApplicationContext` sẵn sàng.

**Script:**

Biết module mới chỉ trả lời câu hỏi build-time; bây giờ cần theo `@SpringBootTest` ở runtime để thấy chúng dựng context như thế nào.

**Purpose:**

Chuyển từ dependency composition sang runtime bootstrap chain để thấy Boot behavior tham gia trước assertion ở đâu.

### Scene 1 — `@SpringBootTest` bootstrap qua `SpringApplication` như thế nào?

**Time:** `03:59–05:04`

**Visual:**

Dùng timeline bootstrap: test annotation → Boot test bootstrapper → `SpringApplication` → Environment → configuration sources → auto-configuration → `ApplicationContext` sẵn sàng.

**Script:**

`@SpringBootTest` tạo test `ApplicationContext` thông qua `SpringApplication` thay vì coi nó như Spring context thuần. Nhờ đó các tính năng Boot như external properties, logging configuration, environment processing và auto-configuration có thể tham gia quá trình khởi động test. Điều này làm full-context test có độ sát thực tế cao ở tầng tích hợp Boot. Nó cũng có nghĩa lỗi trong quá trình khởi động test nên được phân tích như lỗi khởi động Boot: phát hiện cấu hình, condition, property và hạ tầng đều có thể gặp vấn đề trước khi test method đầu tiên chạy.

**Purpose:**

Theo dõi `@SpringBootTest` đi qua `SpringApplication` để lỗi startup trong test được debug theo cùng các phase Boot dùng khi dựng application context thật.

## Boot tìm `@SpringBootConfiguration` chính của ứng dụng như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:19`

**Visual:**

Giữ configuration graph hiện tại và chỉ thay input/import kế tiếp: show a package tree and move upward from the test package until the first `@SpringBootApplication` / `@SpringBootConfiguration` is found; add an explicit configuration class as the escape hatch.

**Script:**

`SpringApplication` chỉ dựng đúng application model khi có primary configuration source, vì vậy configuration discovery là mắt xích kế tiếp của bootstrap chain.

**Purpose:**

Nối SpringApplication bootstrap với bước tìm configuration source quyết định application model mà test thực sự khởi động.

### Scene 1 — Boot tìm `@SpringBootConfiguration` chính của ứng dụng như thế nào?

**Time:** `05:19–06:18`

**Visual:**

Hiện package tree và đi ngược từ package của test lên tới `@SpringBootApplication` / `@SpringBootConfiguration` đầu tiên; thêm explicit configuration class làm escape hatch.

**Script:**

Khi Boot test annotation không chỉ định configuration class rõ ràng, Boot tìm ngược lên từ package chứa test cho tới khi thấy class có `@SpringBootApplication` hoặc `@SpringBootConfiguration`. Với cấu trúc package theo quy ước, cấu hình chính của application thường được tìm thấy tự động. Vì vậy vị trí package của test ảnh hưởng bootstrap. Test nằm ngoài hệ phân cấp package của application có thể không tìm thấy cấu hình chính nếu không truyền configuration class tường minh.

**Purpose:**

Biến primary-configuration discovery thành quy tắc chẩn đoán cụ thể: vị trí package và cấu hình tường minh quyết định full-context test khởi động từ application model nào.

## Phạm vi kiểm thử của Boot bàn giao sang Spring TestContext và các thư viện test ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:18–06:32`

**Visual:**

Giữ các ownership lane và chuyển trách nhiệm kế tiếp qua đúng boundary: use ownership swimlanes for Boot testing, Spring TestContext, JUnit/Mockito, web/data frameworks, and Testcontainers; move each responsibility into the lane that actually implements it.

**Script:**

Sau khi context đã được tạo, không phải mọi hành vi test đều còn thuộc Boot; cần tách trách nhiệm của framework và thư viện trước khi debug sâu hơn.

**Purpose:**

Khép cơ chế hiện tại bằng cách bàn giao trách nhiệm kế tiếp cho đúng framework hoặc library sở hữu nó.

### Scene 1 — Phạm vi kiểm thử của Boot bàn giao sang Spring TestContext và các thư viện test ở đâu?

**Time:** `06:32–07:42`

**Visual:**

Dùng ownership swimlane cho Boot testing, Spring TestContext, JUnit/Mockito, web/data framework và Testcontainers; chuyển từng responsibility vào đúng lane thực sự triển khai nó.

**Script:**

Ở phía Boot, trách nhiệm chính là bootstrap context riêng của Boot, lựa chọn slice, test auto-configuration, các annotation test của Boot, tích hợp thay bean bằng mock/spy và hỗ trợ service connection. Bên dưới, Spring TestContext chịu trách nhiệm về vòng đời context, cache, test-managed transaction, test property source và cơ chế tích hợp thực thi test chung. JUnit vẫn chịu trách nhiệm về cơ chế phát hiện và thực thi test. Mockito vẫn chịu trách nhiệm về hành vi mock và việc xác minh lời gọi. AssertJ/Hamcrest sở hữu API assertion. Testcontainers vẫn chịu trách nhiệm về định nghĩa container và vòng đời của container do JUnit/Testcontainers quản lý, cùng tương tác với Docker, image, network, wait strategy và các cơ chế container tổng quát. Khi container được khai báo như một Spring bean, vòng đời của nó đi theo Spring application context. Giữ ranh giới này rõ giúp không nhầm annotation tiện ích của Boot với phần triển khai của công cụ bên dưới.

**Purpose:**

Cung cấp bản đồ ownership để chuyển lỗi đúng cho Boot testing, Spring TestContext, JUnit, Mockito, assertion library hoặc Testcontainers.

## Bước lựa chọn tiếp theo khi kiểm thử với Boot là gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:42–07:57`

**Visual:**

Giữ kết quả quan sát được từ cảnh trước và đưa nó vào cơ chế kế tiếp: build a decision tree from the assertion boundary: full context → `WebEnvironment`; focused web/data behavior → slice; scenario customization → properties/config/mock; real dependency → service connection.

**Script:**

Khi ownership đã rõ, bước cuối của phần mở đầu là biến các trách nhiệm đó thành lựa chọn test scope và customization có chủ đích.

**Purpose:**

Biến ownership map thành quyết định lựa chọn tiếp theo, ưu tiên chốt scope trước khi thêm customization.

### Scene 1 — Bước lựa chọn tiếp theo khi kiểm thử với Boot là gì?

**Time:** `07:57–09:05`

**Visual:**

Build a decision tree from the assertion boundary: full context → `WebEnvironment`; focused web/data behavior → slice; scenario customization → properties/config/mock; real dependency → service connection.

**Script:**

Hãy bắt đầu bằng việc xác định ranh giới mà test phải chứng minh. Dùng full `@SpringBootTest` khi tích hợp trên toàn application Boot là điều cần kiểm tra. Chọn `WebEnvironment` đúng khi cần độ sát thực tế của HTTP server. Dùng slice tập trung khi chỉ web hoặc data layer cần được kiểm thử. Sau khi chốt phạm vi context, chỉ thêm tùy biến mà kịch bản thực sự cần: test auto-configuration, property cục bộ, cấu hình chỉ dành cho test, thay thế mock/spy hoặc service connection tới dependency thật. Chương cuối sẽ kết nối các lựa chọn này thành một chiến lược nhất quán.

**Purpose:**

Gom phần mở đầu thành quyết định scope-first: chỉ chọn full context, server mode, slice, local customization, replacement hay service connection khi ranh giới assertion thực sự cần.
