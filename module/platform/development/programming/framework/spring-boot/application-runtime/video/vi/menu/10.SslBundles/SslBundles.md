---
video:
  url: ""
---

# SSL bundles và cấu hình TLS runtime

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

## Vì sao Boot cung cấp SSL bundle có tên?

<!-- VIDEO_SECTION -->

### Scene 1 — Vì sao Boot cung cấp SSL bundle có tên?

**Time:** `00:00–00:47`

**Visual:**

Progressive reveal trên visual của chương: thay duplicated key/trust setting ở nhiều consumer bằng một named bundle dùng lại.

**Script:**

Tại TLS integration boundary, nếu không có abstraction dùng lại được, mỗi tích hợp client/server có thể tự khai báo đường dẫn keystore, mật khẩu, certificate và tùy chọn protocol riêng. Nhiều ứng dụng cần dùng cùng trust/key material cho nhiều kết nối bảo mật. SSL bundle của Spring Boot đặt material đó dưới một tên trong `spring.ssl.bundle`. Thành phần runtime được hỗ trợ có thể tham chiếu bundle theo tên thay vì lặp lại source material. Bundle trở thành ranh giới do Boot quản lý giữa cấu hình và thành phần cần các đối tượng SSL. Chương này tập trung vào abstraction runtime đó.

**Purpose:**

Cho thấy named bundle loại bỏ việc lặp key/trust configuration giữa các secure runtime consumer được hỗ trợ.


## Cấu hình bundle JKS/PKCS12 và PEM như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:47–00:59`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Named abstraction chỉ hữu ích khi material được cấu hình nhất quán, vì vậy tiếp theo so sánh namespace JKS/PKCS12 và PEM tạo ra bundle definition.

**Purpose:**

Đi từ lý do cần đặt tên SSL material sang hai nhóm cấu hình được hỗ trợ dùng để tạo bundle name.

### Scene 2 — Cấu hình bundle JKS/PKCS12 và PEM như thế nào?

**Time:** `00:59–01:37`

**Visual:**

Progressive reveal trên visual của chương: tách tree `spring.ssl.bundle.jks.<name>` và `.pem.<name>` với material ví dụ không chứa secret.

**Script:**

Boot 3.3 tạo named bundle từ Java keystore hoặc PEM material. JKS/PKCS12 nằm dưới `spring.ssl.bundle.jks.<name>`, PEM nằm dưới `spring.ssl.bundle.pem.<name>`. PEM trust bundle có thể trỏ `truststore.certificate` tới CA certificate trên classpath; key material cũng có thể cung cấp certificate/private key. JKS/PKCS12 dùng location/password tương ứng cho keystore/truststore. Contract ổn định là bundle name; password/private material thật vẫn phải qua externalized secret/configuration thông thường.

**Purpose:**

Phân biệt namespace JKS/PKCS12 và PEM nhưng giữ credential thật trong cơ chế externalized secret/configuration.


## Danh mục `SslBundles` được auto-configure cung cấp điều gì?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:37–01:47`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Các name đã cấu hình trở thành runtime catalog qua `SslBundles`, cho phép consumer lấy bundle mà không cần biết format nguồn.

**Purpose:**

Biến static bundle definition thành runtime lookup model qua catalog `SslBundles`.

### Scene 3 — Danh mục `SslBundles` được auto-configure cung cấp điều gì?

**Time:** `01:47–02:25`

**Visual:**

Progressive reveal trên visual của chương: hiện catalog `SslBundles`, lookup `getBundle("partner-api")` rồi `createSslContext()`.

**Script:**

Khi bundle name đã cấu hình, Boot expose chúng qua catalog `SslBundles` được auto-configure. Consumer có thể gọi `getBundle("partner-api")`, rồi `createSslContext()` nếu cần TLS object cấp cao. Nhờ vậy consumer phụ thuộc bundle abstraction thay vì layout PEM/JKS. Catalog không phải certificate authority hay secret store; nó là runtime view của SSL configuration đã có trong application environment.

**Purpose:**

Dùng `SslBundles` làm runtime catalog boundary và minh họa lookup bundle + tạo `SSLContext` mà consumer không cần biết source format.


## Một `SslBundle` có thể cung cấp những thành phần runtime nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:35`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Sau khi lấy bundle, consumer có thể chọn level cần dùng—stores, managers hoặc `SSLContext`—thay vì tự dựng lại từ path.

**Purpose:**

Mở một bundle đã lookup thành các object layer cụ thể mà consumer có thể yêu cầu.

### Scene 4 — Một `SslBundle` có thể cung cấp những thành phần runtime nào?

**Time:** `02:35–03:33`

**Visual:**

Progressive reveal trên visual của chương: mở một `SslBundle` theo stores → managers → `SSLContext` và highlight layer cao nhất consumer cần.

**Script:**

Bên trong một `SslBundle`, `getStores()` cho truy cập key/trust stores, `getManagers()` cho key/trust manager factories và managers, còn `createSslContext()` tạo `SSLContext`. `SslBundle` cung cấp SSL material theo nhiều lớp để thành phần sử dụng lấy đúng mức abstraction cần thiết. Bundle cũng mang protocol/options và chi tiết key liên quan. API theo lớp giúp thành phần sử dụng không phải tự dựng đối tượng cấp thấp hơn từ đường dẫn file. Thư viện chỉ cần `SSLContext` nên dùng method cấp cao; tích hợp cần manager factory mới đi xuống lớp đó. Không nên hạ abstraction thấp hơn nếu thành phần sử dụng không yêu cầu, vì mỗi bước làm ứng dụng sở hữu thêm chi tiết TLS và tiến gần trách nhiệm JSSE/TLS tổng quát ngoài module này.

**Purpose:**

Giải thích API theo tầng stores → managers → `SSLContext` để consumer chỉ đi xuống level thấp nhất thật sự cần.


## Các thành phần runtime được hỗ trợ dùng lại bundle có tên như thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:33–03:44`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Bundle chỉ giúp integration hỗ trợ nó hoặc chấp nhận object SSL mà bundle tạo ra, nên boundary kế tiếp là explicit consumer support.

**Purpose:**

Chuyển từ bundle expose được gì sang support contract tường minh mà từng runtime consumer phải có.

### Scene 5 — Các thành phần runtime được hỗ trợ dùng lại bundle có tên như thế nào?

**Time:** `03:44–04:37`

**Visual:**

Progressive reveal trên visual của chương: chỉ nối bundle tới integration hỗ trợ rõ hoặc chấp nhận SSL object; để arbitrary client chưa nối.

**Script:**

Với consumer được hỗ trợ, nhờ đó một định nghĩa có thể phục vụ nhiều thành phần runtime mà không lặp trust/key properties. Bundle có tên có giá trị khi tích hợp Boot được hỗ trợ cho phép tham chiếu theo tên hoặc code ứng dụng lấy bundle từ `SslBundles`. Hỗ trợ phía thành phần sử dụng vẫn phải tường minh. Thành phần phải hiểu tích hợp bundle của Boot hoặc chấp nhận đối tượng SSL có thể tạo từ bundle. Bundle tồn tại không đồng nghĩa mọi client bên thứ ba trên classpath tự động được cấu hình. Khi tích hợp thư viện, trước tiên kiểm tra Boot auto-configuration của công nghệ đó có property nhận tên bundle hay không.

**Purpose:**

Làm consumer support tường minh: có bundle không có nghĩa mọi third-party client tự động được cấu hình lại.


## Trách nhiệm SSL bundle tổng quát bàn giao sang TLS của web server ở đâu?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:37–04:46`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Khi consumer là embedded web server, ownership của generic bundle kết thúc và server TLS chuyển sang `web-runtime`.

**Purpose:**

Vẽ handoff chính xác từ reusable bundle material sang embedded-server TLS configuration.

### Scene 6 — Trách nhiệm SSL bundle tổng quát bàn giao sang TLS của web server ở đâu?

**Time:** `04:46–05:24`

**Visual:**

Progressive reveal trên visual của chương: vẽ `spring.ssl.bundle.* → SslBundles [application-runtime] → server.ssl.bundle=<name> [web-runtime]`.

**Script:**

Giữ handoff rõ ràng. `spring.ssl.bundle.*` tạo named `SslBundle`/catalog `SslBundles` trong application-runtime. Áp bundle cho embedded server—ví dụ `server.ssl.bundle=<name>`—cùng HTTPS port, connector và server-specific TLS behavior thuộc `web-runtime`. Cùng generic bundle còn có thể phục vụ consumer không phải server, nên server option không phải semantics chung của bundle.

**Purpose:**

Tách generic SSL material có thể tái sử dụng khỏi port/connector/server TLS thuộc `web-runtime`.


## Phần nào vẫn thuộc hạ tầng TLS và PKI thay vì Boot runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:24–05:35`

**Visual:**

Giữ named-bundle catalog và mở layer kế tiếp: source format, catalog lookup, stores/managers/`SSLContext`, consumer hoặc ownership handoff.

**Script:**

Ngay cả khi Boot load được key/trust material, certificate policy, trust design, renewal, hostname verification và threat decision vẫn thuộc TLS/PKI.

**Purpose:**

Kết thúc bằng cách tách material-loading integration của Boot khỏi security policy và PKI ownership.

### Scene 7 — Phần nào vẫn thuộc hạ tầng TLS và PKI thay vì Boot runtime?

**Time:** `05:35–06:26`

**Visual:**

Progressive reveal trên visual của chương: tách lỗi Boot binding/resource khỏi TLS chain/hostname/cipher/PKI policy.

**Script:**

Tại PKI boundary, việc cấp/gia hạn certificate, thiết kế trust chain, chính sách kiểm tra hostname, lựa chọn protocol/cipher, xoay vòng key, HSM và threat modeling thuộc security/network. Spring Boot có thể nạp và cung cấp key/trust material đã cấu hình, nhưng Boot không quyết định PKI của tổ chức. Tương tự, biết bundle có truststore khác hoàn toàn với quyết định CA nào nên được tin cậy. Boot cung cấp cấu trúc cấu hình; miền security cung cấp chính sách. Khi chẩn đoán SSL, hãy tách lỗi binding/tích hợp của Boot khỏi lỗi TLS handshake/trust. Tên bundle không tồn tại hoặc resource không đọc được là vấn đề Boot/cấu hình.

**Purpose:**

Giữ Boot ở loading/integration và chuyển certificate policy, trust design, rotation, hostname verification, threat modeling sang TLS/PKI owner.
