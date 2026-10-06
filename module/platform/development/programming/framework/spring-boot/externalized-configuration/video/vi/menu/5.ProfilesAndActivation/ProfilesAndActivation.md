---
video:
  url: ""
---

# Profiles và cấu hình theo profile

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

## Profile giải quyết vấn đề gì và không nên dùng cho điều gì?

<!-- VIDEO_SECTION -->

### Scene 1 — Profile là biến thể cấu hình có tên

**Time:** `00:00–00:50`

**Visual:** Một `app.jar` nối tới ba thẻ `dev`, `staging`, `prod`; mỗi thẻ bật một nhóm giá trị liên quan. Bên cạnh là ví dụ một thời gian chờ đơn lẻ được đổi trực tiếp và không tạo profile mới.

**Script:** “Profile hữu ích khi ta có một biến thể cấu hình có tên như `dev` hay `prod`, nơi nhiều thiết lập liên quan cùng thay đổi. Nó không cần thiết cho mọi tên máy chủ, thời gian chờ hoặc bí mật đơn lẻ; các trường hợp đó thường chỉ cần ghi đè thuộc tính. Trong module này, ta tập trung vào profile như cơ chế kích hoạt Config Data, không đi sâu vào `@Profile` cho bean.”

**Purpose:** Xác định đúng bài toán profile giải quyết và tránh biến profile thành lớp cấu hình cho mọi thay đổi nhỏ.

## Profile đang hoạt động và profile mặc định

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Ba thẻ profile mờ đi; một công tắc chọn `prod` và một nhãn `default` xuất hiện.

**Script:** “Để một biến thể tham gia, trước hết Boot phải biết profile nào đang hoạt động.”

**Purpose:** Chuyển từ mục đích của profile sang cơ chế chọn profile.

### Scene 1 — Profile đang hoạt động và mặc định vẫn chịu thứ tự ưu tiên

**Time:** `01:00–01:50`

**Visual:** Hiện `spring.profiles.active=dev,local`, sau đó terminal `--spring.profiles.active=prod` ghi đè. Tiếp theo hiện `spring.profiles.default` với `default` và `none`.

**Script:** “`spring.profiles.active` là một thuộc tính trong `Environment`, nên nguồn có thứ tự ưu tiên cao hơn vẫn có thể thay giá trị từ nguồn thấp hơn. Nếu không profile nào được chọn tường minh, Spring dùng profile `default`; Boot cho phép đổi bằng `spring.profiles.default`, kể cả `none`. Profile quyết định tài liệu nào đủ điều kiện tham gia, nhưng không bỏ qua thứ tự ưu tiên của các nguồn.”

**Purpose:** Giải thích profile đang hoạt động và profile mặc định trong cùng mô hình `Environment` đã học.

## Tệp Config Data dành riêng cho profile

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:** Từ profile `prod`, mũi tên trỏ tới `application-prod.properties`.

**Script:** “Khi profile đã được chọn, cách trực tiếp nhất để cung cấp biến thể là tệp mang tên profile đó.”

**Purpose:** Nối lựa chọn profile với tệp Config Data theo profile.

### Scene 1 — application-{profile} phủ lên cấu hình nền

**Time:** `02:00–02:45`

**Visual:** Hai tệp `application.properties` và `application-prod.properties`; cấu hình nền chứa nhiều khóa, tệp prod chỉ chứa vài khác biệt, các khóa prod được làm nổi bật là phần ghi đè.

**Script:** “Boot tự tìm biến thể `application-{profile}`. Khi `prod` hoạt động, tệp chung và tệp `application-prod` cùng có thể tham gia, và phần profile ghi đè cấu hình nền tương ứng. Thực hành tốt là giữ giá trị chung trong tệp không theo profile và chỉ đặt khác biệt thật sự vào tệp profile, thay vì sao chép toàn bộ cấu hình.”

**Purpose:** Cho thấy cấu hình profile là lớp khác biệt trên cấu hình nền, không phải bản sao đầy đủ.

## Tài liệu cấu hình dành riêng cho profile

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:45–02:55`

**Visual:** Hai tệp vật lý gộp thành một tệp duy nhất, được chia bằng `#---`.

**Script:** “Nếu khác biệt nhỏ, ta không nhất thiết phải tạo một tệp vật lý riêng cho từng profile.”

**Purpose:** Chuyển từ tệp theo profile sang tài liệu theo profile trong cùng một tệp.

### Scene 1 — Nhiều tài liệu trong một tệp

**Time:** `02:55–03:45`

**Visual:** `app.mode=standard`, dấu phân cách `#---`, `spring.config.activate.on-profile=prod`, `app.mode=hardened`. Bật/tắt profile prod để thấy tài liệu thứ hai tham gia hoặc biến mất.

**Script:** “Một tệp properties hoặc YAML có thể chứa nhiều tài liệu logic. Tài liệu đầu luôn tham gia, tài liệu sau có thể chỉ bật khi profile phù hợp. Với `prod`, `hardened` có thể ghi đè `standard`; khi `prod` tắt, tài liệu đó không đóng góp giá trị. Cách này phù hợp khi phần khác biệt nhỏ và nên nằm gần cấu hình nền.”

**Purpose:** Minh họa kích hoạt ở mức tài liệu và tác động trực tiếp lên tập ứng viên Config Data.

## Kích hoạt tài liệu bằng spring.config.activate.on-profile

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:55`

**Visual:** Phóng lớn dòng `spring.config.activate.on-profile` trong tài liệu thứ hai.

**Script:** “Điểm quyết định tài liệu vừa rồi có tham gia chính là điều kiện `on-profile`.”

**Purpose:** Tách rõ cơ chế chọn profile khỏi cơ chế tài liệu kiểm tra profile đã chọn.

### Scene 1 — Chọn profile và kiểm tra profile là hai chiều khác nhau

**Time:** `03:55–04:45`

**Visual:** YAML có `on-profile: "prod | staging"`; bên trái là `spring.profiles.active` chọn tập profile, bên phải tài liệu kiểm tra tập đó có khớp hay không.

**Script:** “`spring.profiles.active` chọn profile cho ứng dụng. `spring.config.activate.on-profile` không chọn profile; nó chỉ hỏi tài liệu hiện tại có được phép tham gia dưới tập profile đang hoạt động hay không. Tách hai chiều này tránh cấu hình vòng tròn, nơi một tài liệu cần `prod` để tồn tại nhưng lại cố tự kích hoạt `prod`.”

**Purpose:** Ngăn nhầm lẫn giữa việc chọn profile và việc kích hoạt tài liệu.

## Profile bổ sung và nhóm profile

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:45–04:55`

**Visual:** Profile `production` mở rộng thành hai nhánh `proddb` và `prodmq`; cạnh đó `include` thêm `common`.

**Script:** “Một profile được chọn đôi khi cần kéo theo các profile liên quan mà bên gọi không muốn liệt kê thủ công.”

**Purpose:** Dẫn sang `include` và `group` như cơ chế tổ chức profile.

### Scene 1 — Include và nhóm profile giữ tên gọi có ý nghĩa

**Time:** `04:55–05:45`

**Visual:** Hiện `spring.profiles.include[0]=common`, `[1]=observability`; sau đó `spring.profiles.group.production[0]=proddb`, `[1]=prodmq`. Khi bật `production`, hai thành viên group sáng lên.

**Script:** “`spring.profiles.include` bổ sung thêm profile ngoài tập đang hoạt động. Nhóm profile cho nhiều profile chi tiết một tên logic, ví dụ `production` mở rộng thành `proddb` và `prodmq`. Cả hai hữu ích nếu quan hệ vẫn nhỏ và dễ nhìn; nếu biến thành đồ thị phụ thuộc dài, việc suy luận cấu hình sẽ nhanh chóng khó hơn.”

**Purpose:** Giải thích cách tổ chức profile mà vẫn giữ khả năng truy vết.

## Các thuộc tính kích hoạt profile được phép khai báo ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:45–05:55`

**Visual:** Tài liệu có điều kiện cố ghi `spring.profiles.active`; một biểu tượng cảnh báo đỏ xuất hiện.

**Script:** “Để tránh vòng lặp kích hoạt, Boot giới hạn nơi các thuộc tính chọn profile được phép xuất hiện.”

**Purpose:** Nối nguy cơ tự tham chiếu với quy tắc khai báo của Boot.

### Scene 1 — Thuộc tính chọn profile phải ở cấu hình vô điều kiện

**Time:** `05:55–06:45`

**Visual:** Ví dụ không hợp lệ: `spring.config.activate.on-profile=prod` cùng `spring.profiles.active=metrics`. Bên cạnh là ví dụ hợp lệ đặt các thuộc tính `active`, `include`, `group` trong tài liệu không điều kiện hoặc truyền từ tham số dòng lệnh.

**Script:** “Trong Boot 3.3, `spring.profiles.active`, `default`, `include` và `group` không được khai báo trong tệp dành riêng cho profile hoặc tài liệu được kích hoạt bằng `on-profile`. Lý do rất rõ: tài liệu có điều kiện không được quay lại thay đổi chính tập profile quyết định nó có tồn tại. Hãy đặt lựa chọn profile ở cấu hình vô điều kiện hoặc nguồn `Environment` cấp cao hơn.”

**Purpose:** Chứng minh quy tắc khai báo bằng mô hình lỗi tự tham chiếu.

## Nhiều profile đang hoạt động và quy tắc giá trị sau cùng thắng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:45–06:55`

**Visual:** Hai profile `prod` và `live` cùng sáng, cả hai đưa giá trị vào một key.

**Script:** “Khi nhiều profile cùng hoạt động, thứ tự của chúng không còn là chi tiết trang trí.”

**Purpose:** Chuyển từ tính hợp lệ của việc kích hoạt sang thứ tự ưu tiên giữa nhiều profile.

### Scene 1 — Giá trị sau cùng thắng trong nhóm vị trí

**Time:** `06:55–07:45`

**Visual:** `spring.profiles.active=prod,live`; `application-prod.properties` cho một giá trị, `application-live.properties` cho giá trị khác; làm nổi bật giá trị từ `live`. Sau đó phủ thêm dấu `,` và `;` để nhắc lại nhóm vị trí.

**Script:** “Với `prod,live`, Config Data theo profile dùng quy tắc giá trị sau cùng thắng, nên giá trị cạnh tranh từ `application-live` có thể ghi đè `application-prod`. Ở cấu hình vị trí phức tạp, quy tắc này áp dụng trong bối cảnh nhóm vị trí, vì vậy dấu phẩy và chấm phẩy đã học ở chương trước có thể làm thứ tự xử lý khác đi.”

**Purpose:** Liên kết thứ tự ưu tiên theo profile với thứ tự các profile đang hoạt động và nhóm vị trí.

## Profile và ghi đè thuộc tính thông thường

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:45–07:55`

**Visual:** Một cán cân: bên trái “biến thể cấu hình có tên”, bên phải “ghi đè một thuộc tính”.

**Script:** “Cuối cùng, profile chỉ hữu ích khi nó thực sự là cơ chế nhỏ nhất diễn đạt đúng nhu cầu.”

**Purpose:** Tổng hợp chương bằng quyết định chọn profile hay ghi đè trực tiếp.

### Scene 1 — Chọn cơ chế nhỏ nhất đủ dùng

**Time:** `07:55–08:45`

**Visual:** Nhánh profile: `prod → cơ sở dữ liệu + nhắn tin + tập tính năng`; nhánh ghi đè trực tiếp: `orders.timeout=5s`, `server.port=9090`, `partner.base-url=...`.

**Script:** “Dùng profile cho một biến thể có tên kéo theo nhóm khác biệt liên quan. Dùng ghi đè thuộc tính cho một giá trị cụ thể. Nếu mỗi cụm triển khai, vùng, khách hàng hay secret đều trở thành một profile, ta đã tạo thêm một hệ thứ tự ưu tiên khó nhìn nằm trên hệ thuộc tính vốn có. Hãy chọn cơ chế nhỏ nhất vẫn diễn đạt đúng ý định triển khai.”

**Purpose:** Cho người học tiêu chí thực hành để tránh lạm dụng profile.
