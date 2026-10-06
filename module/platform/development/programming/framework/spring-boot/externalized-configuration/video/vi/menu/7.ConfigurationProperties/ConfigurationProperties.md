---
video:
  url: ""
---

# Cấu hình có kiểu với @ConfigurationProperties

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

## Vì sao nên nhóm cấu hình vào đối tượng có kiểu?

<!-- VIDEO_SECTION -->

### Scene 1 — Gom các khóa liên quan thành một hợp đồng có kiểu

**Time:** `00:00–00:55`

**Visual:** Hiện ba khóa `mail.host`, `mail.port`, `mail.retry-count`, rồi gom chúng vào một đối tượng `MailProperties`. Bên cạnh lần lượt hiện các nhãn kiểm tra kiểu, validation, metadata và refactor.

**Script:** “Một nhóm cấu hình thường đại diện cho một khái niệm thật của ứng dụng. Nếu ta rải `mail.host`, `mail.port`, `mail.retry-count` qua nhiều `@Value`, hợp đồng cấu hình sẽ bị phân tán. `@ConfigurationProperties` gom cả không gian tên vào một đối tượng có kiểu. `Environment` vẫn phân giải giá trị có hiệu lực trước; binder chỉ chuyển các giá trị đó thành mô hình mà mã ứng dụng sử dụng.”

**Purpose:** Giải thích lợi ích của cấu hình có kiểu, không chỉ dừng ở việc giảm số annotation.

## Tiền tố và không gian tên cấu hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:** Phóng to annotation `@ConfigurationProperties("mail")` và làm nổi bật phần `mail`.

**Script:** “Đối tượng có kiểu mô tả cấu trúc; prefix xác định chính xác phần nào của không gian cấu hình thuộc về đối tượng đó.”

**Purpose:** Nối đối tượng cấu hình có kiểu với không gian tên mà binder sẽ đọc.

### Scene 2 — Prefix xác định ranh giới của không gian tên

**Time:** `01:05–01:50`

**Visual:** Hiện cây khóa `mail.host`, `mail.retry-count`, `client.timeout`; vùng `mail.*` được khoanh vào `MailProperties`. Thêm ghi chú “prefix dùng kebab-case”.

**Script:** “Với `@ConfigurationProperties("mail")`, binder coi `mail.*` là không gian tên của đối tượng. Prefix nên dùng kebab-case, ổn định và phản ánh đúng miền cấu hình, thay vì bám theo tên lớp hay môi trường triển khai. Một prefix quá rộng như `app` dễ gom nhiều trách nhiệm không liên quan và khiến validation, metadata hay deprecation khó quản lý.”

**Purpose:** Đưa ra tiêu chí đặt prefix để thể hiện rõ phạm vi của hợp đồng cấu hình.

## Đăng ký @ConfigurationProperties bằng quét hoặc bật tường minh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:** Annotation xuất hiện trên lớp nhưng đối tượng chưa có trong bean registry; sau đó hai lựa chọn `@ConfigurationPropertiesScan` và `@EnableConfigurationProperties` được làm nổi bật.

**Script:** “Khai báo cách binding chưa đồng nghĩa Spring Boot đã đăng ký kiểu đó thành bean.”

**Purpose:** Chuyển từ khai báo binding sang bước đăng ký bean.

### Scene 3 — Quét hoặc đăng ký tường minh

**Time:** `02:00–02:50`

**Visual:** Mở cấu hình có `@ConfigurationPropertiesScan`, sau đó chuyển sang `@EnableConfigurationProperties(MyProperties.class)`. Sơ đồ bean registry cho thấy kiểu được đăng ký trước khi binder điền dữ liệu.

**Script:** “Spring Boot cần đăng ký kiểu `@ConfigurationProperties` trước khi binding. `@ConfigurationPropertiesScan` phù hợp khi ứng dụng sở hữu nhiều kiểu trong một phạm vi package rõ ràng. `@EnableConfigurationProperties` phù hợp khi muốn chỉ định tường minh từng kiểu, đặc biệt trong auto-configuration của thư viện. Cả hai chỉ quyết định đối tượng đích nào được tạo và bind; chúng không thay đổi thứ tự ưu tiên của nguồn cấu hình.”

**Purpose:** Phân biệt việc đăng ký bean với việc phân giải giá trị, đồng thời chỉ ra khi nên quét hoặc đăng ký tường minh.

## Binding theo JavaBean và setter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:** Bean registry chuyển thành một đối tượng rỗng, rồi các setter lần lượt được gọi.

**Script:** “Sau khi kiểu được đăng ký, binder cần một cách cụ thể để tạo và điền dữ liệu vào đối tượng.”

**Purpose:** Dẫn từ bước đăng ký sang mô hình binding theo JavaBean.

### Scene 4 — Tạo JavaBean rồi gọi setter

**Time:** `03:00–03:50`

**Visual:** Mở `MailProperties` có `host`, `port=25`, getter và setter. Minh họa lần lượt: tạo đối tượng → chuyển đổi `mail.port` → gọi `setPort`. Làm nổi bật `25` là giá trị mặc định của chính đối tượng.

**Script:** “JavaBean binding tạo đối tượng qua constructor không tham số rồi ghi thuộc tính bằng setter sau khi chuyển đổi kiểu. Nếu `port` được khởi tạo sẵn là 25 và không có giá trị cấu hình nào bind vào, đối tượng giữ nguyên 25. Đây là giá trị mặc định của đối tượng đích, không phải một `PropertySource` có độ ưu tiên thấp trong `Environment`.”

**Purpose:** Làm rõ cách setter binding hoạt động và phân biệt giá trị mặc định của trường với thứ tự ưu tiên của nguồn cấu hình bên ngoài.

## Binding qua constructor trong Spring Boot 3.x

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:** Đối tượng thay đổi được qua setter chuyển thành đối tượng có các trường `final`, được tạo một lần qua constructor.

**Script:** “Nếu hợp đồng cấu hình nên bất biến, Spring Boot 3.x có thể tạo đối tượng trực tiếp từ constructor.”

**Purpose:** Chuyển từ JavaBean có thể thay đổi sang constructor binding cho cấu hình bất biến.

### Scene 5 — Spring Boot 3.x chọn constructor nào?

**Time:** `04:00–04:30`

**Visual:** Mở lớp bất biến `MailProperties(String host, int port)` với một constructor có tham số và ghi chú “không cần `@ConstructorBinding`”. Sau đó chuyển sang lớp có nhiều constructor và làm nổi bật constructor được gắn `@ConstructorBinding`; đặt dấu loại trừ bên cạnh constructor `@Autowired`.

**Script:** “Với Spring Boot 3.x, nếu kiểu `@ConfigurationProperties` chỉ có một constructor có tham số, Boot có thể dùng constructor đó mà không cần `@ConstructorBinding`. Nếu có nhiều constructor, `@ConstructorBinding` có thể chỉ rõ constructor dành cho binding. Constructor được đánh dấu `@Autowired` thuộc cơ chế dependency injection thông thường, không phải đường constructor binding này.”

**Purpose:** Tách riêng quy tắc chọn constructor để người học hiểu chính xác hành vi của Spring Boot 3.x.

### Scene 6 — Đăng ký kiểu và giữ lại tên tham số

**Time:** `04:30–05:00`

**Visual:** Giữ lớp bất biến trên màn hình. Bên trái hiện `@ConfigurationPropertiesScan` và `@EnableConfigurationProperties`; bên phải hiện tùy chọn compiler `-parameters`. Phía dưới, so sánh Gradle có Spring Boot plugin với Maven dùng `spring-boot-starter-parent`; thêm ghi chú rằng chỉ khai báo `spring-boot-maven-plugin` không tự thay thế cấu hình compiler này.

**Script:** “Kiểu dùng constructor binding vẫn phải được đăng ký qua `@ConfigurationPropertiesScan` hoặc `@EnableConfigurationProperties`, thay vì coi như một component thông thường. Tên tham số constructor cũng phải còn được giữ lại khi chạy, vì vậy lúc biên dịch cần `-parameters`. Spring Boot Gradle plugin cấu hình tùy chọn này; với Maven, dự án dùng `spring-boot-starter-parent` sẽ kế thừa cấu hình phù hợp, còn một cấu hình Maven tùy biến cần tự bảo đảm `-parameters` thay vì chỉ dựa vào `spring-boot-maven-plugin`.”

**Purpose:** Bổ sung hai điều kiện thực tế của constructor binding: đăng ký đúng kiểu bean và giữ lại tên tham số khi biên dịch.

## record làm kiểu @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:** Lớp bất biến được thu gọn thành một Java record.

**Script:** “Java record giúp mô hình cấu hình bất biến ngắn gọn hơn vì constructor và tên các component đã có sẵn.”

**Purpose:** Nối constructor binding với một kiểu Java phù hợp tự nhiên cho dữ liệu bất biến.

### Scene 7 — Record gọn nhưng không tự tạo validation

**Time:** `05:10–05:55`

**Visual:** Mở `record ClientProperties(URI baseUrl, Duration timeout)`; bên trái, `client.base-url` và `client.timeout` được bind vào các component. Hiện cảnh báo “record không tự làm mọi giá trị trở thành bắt buộc”.

**Script:** “Record là lựa chọn gọn cho cấu hình bất biến. Boot vẫn bind `client.base-url` và `client.timeout` bằng relaxed binding và chuyển đổi kiểu như bình thường. Nhưng record không tự biến mọi giá trị thành bắt buộc; nullability, giá trị mặc định của kiểu primitive, giá trị mặc định khai báo tường minh và validation mới quyết định điều gì xảy ra khi property thiếu hoặc sai.”

**Purpose:** Khuyến khích dùng record đúng trường hợp mà không gán cho nó hành vi validation vốn không tồn tại.

## Binding cấu hình vào @Bean của thư viện bên thứ ba

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:05`

**Visual:** Một lớp từ thư viện bên ngoài xuất hiện với biểu tượng không thể sửa mã nguồn; bên cạnh là một phương thức `@Bean`.

**Script:** “Đôi khi đối tượng cần bind đến từ một thư viện bên ngoài, nên ứng dụng không thể gắn annotation trực tiếp lên kiểu đó.”

**Purpose:** Dẫn sang cách binding một đối tượng của thư viện bên thứ ba.

### Scene 8 — Đặt @ConfigurationProperties trên phương thức @Bean

**Time:** `06:05–06:55`

**Visual:** Mở phương thức `@Bean @ConfigurationProperties("client.pool") ConnectionPoolSettings ...`; minh họa đối tượng được tạo trước, sau đó binder điền các thuộc tính JavaBean.

**Script:** “Ta có thể đặt `@ConfigurationProperties` trên phương thức `@Bean` để Spring Boot bind các giá trị bên ngoài vào đối tượng mà phương thức tạo ra. Cách này hữu ích với JavaBean của thư viện bên thứ ba và tránh phải viết một lớp bọc chỉ để chép cấu hình. Vì ứng dụng không sở hữu kiểu đích, metadata và validation thường kém linh hoạt hơn so với một kiểu cấu hình do chính ứng dụng thiết kế.”

**Purpose:** Giải thích cách binding bean bên thứ ba và những giới hạn đi kèm khi ứng dụng không sở hữu kiểu đích.

## Vòng đời @ConfigurationProperties và ranh giới bean

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:55–07:05`

**Visual:** Đối tượng cấu hình ở bên trái, dịch vụ hoặc client sử dụng lúc chạy ở bên phải; giữa chúng là một phương thức `@Bean`.

**Script:** “Đối tượng cấu hình có kiểu nên chỉ giữ dữ liệu cấu hình; nó không nên dần biến thành một dịch vụ hoạt động lúc chạy.”

**Purpose:** Tổng hợp binding có kiểu thành ranh giới bean và vòng đời rõ ràng.

### Scene 9 — Binding dữ liệu trước, tạo thành phần lúc chạy sau

**Time:** `07:05–08:00`

**Visual:** Sơ đồ `Environment → @ConfigurationProperties binding → đối tượng cấu hình đã validation → @Bean/component`. Gạch bỏ các mũi tên từ đối tượng cấu hình sang nhiều service phụ thuộc.

**Script:** “Bean `@ConfigurationProperties` nên tập trung vào dữ liệu và validation nhẹ. Đặc biệt với constructor binding, hãy để cơ chế configuration-properties tạo đối tượng thay vì biến nó thành component phụ thuộc vào các bean nghiệp vụ khác. Nếu dữ liệu cấu hình quyết định cách tạo client hay dịch vụ, hãy bind và xác thực trước, rồi để một `@Configuration` hoặc `@Bean` riêng dùng đối tượng đó để tạo thành phần hoạt động lúc chạy.”

**Purpose:** Giữ quá trình cấu hình dễ dự đoán và tách mô hình dữ liệu cấu hình khỏi vòng đời của dịch vụ nghiệp vụ hoặc thành phần lúc chạy.
