---
video:
  url: ""
---

# TLS, SSL bundles và chứng chỉ web server

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** MM:SS–MM:SS

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** MM:SS–MM:SS

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Các điểm cấu hình của Boot cho TLS phía web server

<!-- VIDEO_SECTION -->

### Scene 1 — Các điểm cấu hình của Boot cho TLS phía web server

**Time:** `00:00–00:46`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Các điểm cấu hình của Boot cho TLS phía web server", đặt `server.ssl.*`, `spring.ssl.bundle.*`, `server.ssl.bundle` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot có hai hướng khai báo chính để bảo vệ embedded server. Hướng thứ nhất cấu hình dữ liệu chứng chỉ/khóa trực tiếp dưới `server.ssl.*`. Hướng thứ hai định nghĩa dữ liệu có tên có thể tái sử dụng dưới `spring.ssl.bundle.*` rồi trỏ web server tới một bundle bằng `server.ssl.bundle`. Cả hai đều cấu hình server do Boot quản lý và không thay route của ứng dụng. Khác biệt chủ yếu nằm ở cách sở hữu/tái sử dụng cấu hình. Bundle có tên phù hợp khi cùng dữ liệu TLS hoặc tùy chọn cần được dùng nhất quán bởi nhiều loại kết nối mà Boot hỗ trợ.

**Purpose:**

Giải thích "Các điểm cấu hình của Boot cho TLS phía web server" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Cấu hình keystore và PEM bằng server.ssl.*

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:46–01:01`

**Visual:**

Giữ sơ đồ certificate/bundle → HTTPS connector trên màn hình. Làm mờ annotation của "Các điểm cấu hình của Boot cho TLS phía web server" rồi animate focus sang "Cấu hình keystore và PEM bằng server.ssl.*"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Các điểm cấu hình của Boot cho TLS phía web server" đã rõ. Dependency kế tiếp là "Cấu hình keystore và PEM bằng server.ssl.*"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Cấu hình keystore và PEM bằng server.ssl.*" là dependency kế tiếp sau "Các điểm cấu hình của Boot cho TLS phía web server", đồng thời giữ ownership và runtime state liên tục.

### Scene 2 — Cấu hình keystore và PEM bằng server.ssl.*

**Time:** `01:01–01:39`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Cấu hình keystore và PEM bằng server.ssl.*", đặt `server.ssl.*` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Với cấu hình trực tiếp cho server, `server.ssl.*` hỗ trợ dữ liệu Java KeyStore và dữ liệu chứng chỉ/khóa mã hóa PEM. Thiết lập keystore thường khai báo vị trí/mật khẩu keystore; thiết lập PEM khai báo vị trí chứng chỉ và private key. Khi bật SSL cho server, connector chính chuyển sang HTTPS. Boot không cung cấp một cặp property thông thường để đồng thời cấu hình một HTTP connector và một HTTPS connector; nếu cần mô hình kết nối đó thì thêm connector còn lại bằng mã.

**Purpose:**

Nối input trong "Cấu hình keystore và PEM bằng server.ssl.*" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.

## Áp dụng SSL bundle có tên cho web server

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:39–01:54`

**Visual:**

Giữ sơ đồ certificate/bundle → HTTPS connector trên màn hình. Làm mờ annotation của "Cấu hình keystore và PEM bằng server.ssl.*" rồi animate focus sang "Áp dụng SSL bundle có tên cho web server"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Khi "Cấu hình keystore và PEM bằng server.ssl.*" đã rõ, hãy kiểm boundary kế tiếp: "Áp dụng SSL bundle có tên cho web server". Giữ kết quả trước để cơ chế mới đi ra trực tiếp từ nó.

**Purpose:**

Chuyển từ "Cấu hình keystore và PEM bằng server.ssl.*" sang "Áp dụng SSL bundle có tên cho web server" mà không reset mô hình, đồng thời làm rõ input, owner hoặc output nào thay đổi.

### Scene 3 — Áp dụng SSL bundle có tên cho web server

**Time:** `01:54–02:29`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Áp dụng SSL bundle có tên cho web server", đặt `spring.ssl.bundle.jks.`, `spring.ssl.bundle.pem.`, `SslBundle` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Khai báo một SSL bundle có tên dưới `spring.ssl.bundle.jks.<name>` hoặc `spring.ssl.bundle.pem.<name>`, sau đó trỏ embedded server tới tên đó bằng `server.ssl.bundle`. Ví dụ, PEM bundle có thể tham chiếu certificate và private-key resource một lần để web server tiêu thụ bundle theo tên thay vì lặp key material trong `server.ssl.*`. Hai lớp `SslBundle` và `SslBundles` dùng chung thuộc application runtime; chapter này chỉ sở hữu điểm tiêu thụ phía web, nơi Boot gắn bundle đã chọn vào HTTPS server.

**Purpose:**

Biến "Áp dụng SSL bundle có tên cho web server" thành lựa chọn cụ thể bằng cách cho thấy runtime thay đổi gì và tầng cấu hình hoặc customization nào nên dùng tiếp.

## Tùy chọn trong SSL bundle và các server.ssl property rời

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:29–02:45`

**Visual:**

Giữ sơ đồ certificate/bundle → HTTPS connector trên màn hình. Làm mờ annotation của "Áp dụng SSL bundle có tên cho web server" rồi animate focus sang "Tùy chọn trong SSL bundle và các server.ssl property rời"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ta đã chốt "Áp dụng SSL bundle có tên cho web server". Giữ kết quả đó trên màn hình; bây giờ chuyển sang "Tùy chọn trong SSL bundle và các server.ssl property rời" và chỉ thay quyết định runtime kế tiếp.

**Purpose:**

Nối "Áp dụng SSL bundle có tên cho web server" với "Tùy chọn trong SSL bundle và các server.ssl property rời" để quyết định web-runtime kế tiếp đi ra từ kết quả đã chứng minh.

### Scene 4 — Tùy chọn trong SSL bundle và các server.ssl property rời

**Time:** `02:45–03:12`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Tùy chọn trong SSL bundle và các server.ssl property rời", đặt `server.ssl.bundle`, `server.ssl`, `server.ssl.ciphers` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

`server.ssl.bundle` không được kết hợp với các tùy chọn dữ liệu Java KeyStore/PEM rời dưới`server.ssl`. Khi dùng bundle, các property như`server.ssl.ciphers`,`server.ssl.enabled-protocols` và`server.ssl.protocol` bị bỏ qua. Hãy đưa tùy chọn giao thức/bộ mã vào`spring.ssl.bundle. . .options` của bundle có tên. Cách này giữ bundle tự chứa và tránh hai mô hình cấu hình cạnh tranh cho cùng trạng thái TLS.

**Purpose:**

Cho thấy hệ quả runtime cụ thể của "Tùy chọn trong SSL bundle và các server.ssl property rời" và tạo checkpoint quan sát được cho quyết định Boot sở hữu.

## Server Name Indication và lựa chọn chứng chỉ

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:28`

**Visual:**

Giữ sơ đồ certificate/bundle → HTTPS connector trên màn hình. Làm mờ annotation của "Tùy chọn trong SSL bundle và các server.ssl property rời" rồi animate focus sang "Server Name Indication và lựa chọn chứng chỉ"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Section trước đã cho evidence cho "Tùy chọn trong SSL bundle và các server.ssl property rời". Đi tiếp trên cùng runtime path tới "Server Name Indication và lựa chọn chứng chỉ" và quan sát input, owner hoặc output nào thay đổi.

**Purpose:**

Giữ evidence từ "Tùy chọn trong SSL bundle và các server.ssl property rời" và chỉ đưa thêm cơ chế mới cần cho "Server Name Indication và lựa chọn chứng chỉ".

### Scene 5 — Server Name Indication và lựa chọn chứng chỉ

**Time:** `03:28–04:11`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Server Name Indication và lựa chọn chứng chỉ", đặt `server.ssl.server-name-bundles`, `server.ssl.bundle` tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Boot 3.3 có thể ánh xạ hostname tới các SSL bundle bổ sung qua `server.ssl.server-name-bundles`. Bundle ở`server.ssl.bundle` vẫn là dữ liệu chứng chỉ mặc định, còn các mục hostname có tên chọn bundle thay thế cho máy khách hỗ trợ SNI. Tomcat, Netty và Undertow có cấu hình SNI do Boot quản lý. Jetty khác ở chỗ ánh xạ SNI tường minh của Boot không được hỗ trợ, dù Jetty có thể tự thiết lập SNI khi được cung cấp nhiều chứng chỉ. Đây là khác biệt về khả năng server chứ không phải quy tắc TLS tổng quát.

**Purpose:**

Giải thích "Server Name Indication và lựa chọn chứng chỉ" ở đúng layer Boot sở hữu, đồng thời làm rõ điểm bàn giao sang framework, phần nội bộ server hoặc hạ tầng.

## Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:11–04:27`

**Visual:**

Giữ sơ đồ certificate/bundle → HTTPS connector trên màn hình. Làm mờ annotation của "Server Name Indication và lựa chọn chứng chỉ" rồi animate focus sang "Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát"; chỉ reveal property, class, thành phần server hoặc trust boundary mới mà scene kế tiếp cần.

**Script:**

Ở layer này, "Server Name Indication và lựa chọn chứng chỉ" đã rõ. Dependency kế tiếp là "Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát"; chuyển highlight sang đó nhưng giữ trạng thái server/context đã chứng minh.

**Purpose:**

Cho thấy vì sao "Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát" là dependency kế tiếp sau "Server Name Indication và lựa chọn chứng chỉ", đồng thời giữ ownership và runtime state liên tục.

### Scene 6 — Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát

**Time:** `04:27–05:13`

**Visual:**

Dùng TLS handshake diagram với certificate material ở trái, server.ssl hoặc named SslBundle ở giữa và HTTPS connector ở phải; với SNI cho hostname chọn certificate. Với "Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát", đặt input của section và runtime result quan sát được tại đúng node hoặc kết nối mà nó tác động và làm mờ các nhánh không liên quan.

**Script:**

Module này cần giải thích Boot nhận dữ liệu chứng chỉ ở đâu, gắn dữ liệu đó vào embedded server như thế nào, bundle có tên được chọn ra sao và hạn chế riêng theo server xuất hiện ở đâu. Nó cũng cần nối sang trường hợp triển khai như TLS termination tại reverse proxy. Việc cấp chứng chỉ, mô hình tin cậy CA, mật mã bắt tay TLS, thiết kế bộ mã, xác thực chuỗi chứng chỉ, giao thức ACME và vận hành PKI thuộc phần bảo mật/mạng chịu trách nhiệm. Boot chỉ tiêu thụ các khái niệm đó qua cấu hình, không định nghĩa lại chúng.

**Purpose:**

Nối input trong "Tích hợp TLS của web server và ranh giới với TLS/PKI tổng quát" với factory, server, context hoặc request state mà nó thay đổi để chẩn đoán bắt đầu đúng layer.
