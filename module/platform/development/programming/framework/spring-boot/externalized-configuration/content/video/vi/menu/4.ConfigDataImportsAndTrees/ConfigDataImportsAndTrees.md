---
video:
  url: ""
---

# Nhập Config Data và cây cấu hình

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

## Nhập Config Data bổ sung bằng spring.config.import

<!-- VIDEO_SECTION -->

### Scene 1 — Import là một phần của Config Data

**Time:** `00:00–00:50`

**Visual:** Mở `application.properties` với `spring.config.import=optional:file:./ops.properties`; hiệu ứng kéo `ops.properties` vào cùng luồng Config Data rồi đổ vào `Environment`.

**Script:** “`spring.config.import` cho phép một tài liệu Config Data kéo thêm cấu hình từ nơi khác. Đây không phải thao tác đọc tệp tùy ý sau khi ứng dụng chạy; tài nguyên được import tham gia trực tiếp vào quá trình Config Data, profile và thứ tự ưu tiên. Một tài nguyên cụ thể chỉ được nạp một lần, nên import nên dùng để làm cấu trúc rõ hơn chứ không tạo một chuỗi phụ thuộc dài khó lần theo.”

**Purpose:** Định vị import trong luồng Config Data và nhấn mạnh đây là nguồn cấu hình chính thức.

## Giá trị được nhập liên hệ thế nào với tài liệu khai báo import?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:** Hai tệp `application.properties` và `extra.properties` xếp chồng, cùng chứa `app.name`.

**Script:** “Khi tệp import và tệp khai báo cùng có một khóa, ta cần biết quan hệ thứ tự ưu tiên cục bộ giữa hai tài liệu.”

**Purpose:** Chuyển từ việc nạp import sang cách giá trị import cạnh tranh với tài liệu gọi nó.

### Scene 1 — Tài liệu được import có thể ghi đè tài liệu gọi

**Time:** `01:00–01:50`

**Visual:** `application.properties`: `app.name=base`, import `extra.properties`; `extra.properties`: `app.name=imported`. Kết quả hiện `app.name=imported`; sau đó thêm hai import theo thứ tự để minh họa import sau thắng import trước.

**Script:** “Boot xem tài liệu import như được chèn ngay bên dưới tài liệu khai báo và giá trị import có thứ tự ưu tiên cao hơn tài liệu gọi nó. Vì vậy `base` bị `imported` ghi đè. Nếu một import liệt kê nhiều vị trí, chúng được xử lý theo thứ tự khai báo và vị trí sau có thể ghi đè vị trí trước. Tuy vậy, biến môi trường hay tham số dòng lệnh ở tầng cao hơn vẫn có thể thắng cả hai.”

**Purpose:** Làm rõ thứ tự ưu tiên cục bộ của import mà vẫn đặt nó trong thứ tự ưu tiên toàn cục.

## Vị trí cố định và vị trí tương đối theo tài nguyên import

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:** Cùng một chuỗi đường dẫn được đặt cạnh hai thư mục khác nhau; một mũi tên ghi “tương đối”, một mũi tên ghi “cố định”.

**Script:** “Sau thứ tự ưu tiên, lỗi import thường gặp nhất là hiểu sai một đường dẫn sẽ được tính từ đâu.”

**Purpose:** Dẫn từ quan hệ giá trị sang cách Boot phân giải địa chỉ import.

### Scene 1 — Import tương đối đi theo tài liệu gọi

**Time:** `02:00–02:50`

**Visual:** Cây `/demo/application.properties` import `core/core.properties`; làm nổi bật kết quả `/demo/core/core.properties`. Tệp đó import tiếp `extra/extra.properties`, làm nổi bật `/demo/core/extra/extra.properties`. Bên cạnh hiển thị ví dụ `file:` và `classpath:` là vị trí cố định.

**Script:** “Import bắt đầu bằng `file:`, `classpath:` hoặc đường dẫn tuyệt đối là vị trí cố định. Import không có các dấu hiệu đó được tính tương đối từ tài liệu khai báo. Vì vậy `core/core.properties` trong `/demo/application.properties` sẽ đi tới `/demo/core/`; import tiếp bên trong tệp đó lại lấy chính thư mục mới làm mốc. `optional:` chỉ đổi việc thiếu tài nguyên có được chấp nhận hay không, không đổi vị trí cố định thành tương đối.”

**Purpose:** Cung cấp quy tắc xác định đường dẫn import theo từng bước trong chuỗi tài liệu.

## Tài nguyên import tùy chọn và hành vi khi tài nguyên không tồn tại

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:** Một tệp import biến mất khỏi cây thư mục; màn hình chia hai nhánh “bắt buộc” và “tùy chọn”.

**Script:** “Biết Boot tìm ở đâu rồi, câu hỏi kế tiếp là điều gì xảy ra nếu tài nguyên đó không tồn tại.”

**Purpose:** Chuyển từ phân giải đường dẫn sang hợp đồng khi import thất bại.

### Scene 1 — optional: thay đổi hợp đồng khi tài nguyên vắng mặt

**Time:** `03:00–03:50`

**Visual:** Nhánh bắt buộc dừng khởi động với `ConfigDataLocationNotFoundException`; nhánh `optional:file:./local-overrides.properties` tiếp tục khởi động. Thêm chú thích “chỉ dùng khi vắng mặt là hợp lệ”.

**Script:** “Mặc định, vị trí Config Data bắt buộc bị thiếu làm khởi động thất bại, thường với `ConfigDataLocationNotFoundException`. Thêm `optional:` nghĩa là việc không có tài nguyên là trạng thái hợp lệ. Đừng dùng nó để che một tệp bắt buộc ở môi trường sản xuất; như vậy lỗi chỉ bị đẩy sang binding hoặc lúc chạy và nguyên nhân gốc khó nhìn hơn.”

**Purpose:** Phân biệt tài nguyên tùy chọn thật sự với việc né cơ chế thất bại sớm của cấu hình bắt buộc.

## Nhập tệp cấu hình không có phần mở rộng bằng gợi ý định dạng

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:** Một tệp `/etc/config/myconfig` không có phần mở rộng xuất hiện với dấu hỏi về bộ phân tích định dạng.

**Script:** “Tài nguyên có thể tồn tại đúng chỗ nhưng Boot vẫn chưa biết phải dùng bộ phân tích nào nếu tệp không có phần mở rộng.”

**Purpose:** Nối việc tài nguyên có tồn tại với vấn đề định dạng của tài nguyên import.

### Scene 1 — Gợi ý phần mở rộng cho Boot biết cách đọc tệp

**Time:** `04:00–04:45`

**Visual:** Hiện `spring.config.import=file:/etc/config/myconfig[.yaml]`; phần `[.yaml]` được làm nổi bật, sau đó nội dung YAML được phân tích.

**Script:** “Với tệp do nền tảng gắn vào mà không có phần mở rộng, Boot hỗ trợ gợi ý phần mở rộng. Dạng `myconfig[.yaml]` nói rằng tài nguyên thật không có đuôi nhưng nội dung cần được đọc như YAML. Gợi ý này chỉ giải quyết việc chọn bộ nạp; nó không thay đổi thứ tự ưu tiên và cũng không nên dùng để che giấu định dạng sai.”

**Purpose:** Giải thích chính xác vai trò của gợi ý phần mở rộng trong Boot 3.3.

## Cây cấu hình và đầu vào kiểu một tệp cho mỗi thuộc tính

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:45–04:55`

**Visual:** Tệp đơn lẻ chuyển thành một thư mục gồm nhiều tệp `username`, `password`.

**Script:** “Một số nền tảng không gắn một tệp cấu hình lớn mà gắn mỗi giá trị thành một tệp riêng.”

**Purpose:** Dẫn từ import tệp thông thường sang mô hình cây cấu hình.

### Scene 1 — configtree: biến cây tệp thành thuộc tính

**Time:** `04:55–05:50`

**Visual:** Cây `/etc/config/myapp/username` và `password`; lệnh import `optional:configtree:/etc/config/`; bên phải hiện `myapp.username` và `myapp.password` trong `Environment`.

**Script:** “`configtree:` là cầu nối từ kiểu gắn ‘một tệp cho mỗi giá trị’ sang thuộc tính của Boot. Với thư mục `myapp` chứa `username` và `password`, Boot có thể tạo các khóa `myapp.username` và `myapp.password`. Giá trị có thể được bind thành `String` hoặc `byte[]`. Việc cấp phát secret thuộc nền tảng hoặc hệ quản lý secret; module này chỉ chịu trách nhiệm đưa các tệp đã gắn vào `Environment`.”

**Purpose:** Minh họa cây cấu hình bằng cấu trúc tệp cụ thể và giữ đúng ranh giới trách nhiệm.

## Chẩn đoán lỗi khi nhập Config Data

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:00`

**Visual:** Các ví dụ import trước đó thu lại thành bốn thẻ: địa chỉ, khả dụng, định dạng, thứ tự.

**Script:** “Các lỗi import vừa thấy có thể gom thành bốn câu hỏi để chẩn đoán nhanh hơn.”

**Purpose:** Tổng hợp cơ chế import thành danh sách kiểm tra chẩn đoán.

### Scene 1 — Bốn lớp lỗi import

**Time:** `06:00–06:55`

**Visual:** Danh sách kiểm tra: địa chỉ → khả dụng → định dạng → thứ tự. Bảng terminal/log làm nổi bật logger `org.springframework.boot.context.config`; cuối cảnh tách hai nhánh “chưa nạp” và “đã nạp nhưng bị ghi đè”.

**Script:** “Khi import lỗi, kiểm tra lần lượt: địa chỉ có đúng không, tài nguyên có tồn tại hay được phép thiếu không, Boot có đọc đúng định dạng không, và cuối cùng giá trị đã nạp có thắng thứ tự ưu tiên không. Với import tương đối, lần đường dẫn từ chính tài liệu khai báo. Với tệp không có phần mở rộng, kiểm tra gợi ý định dạng. Khi cần bằng chứng, log của `org.springframework.boot.context.config` giúp thấy quá trình nạp. Điều quan trọng là tách ‘chưa được nạp’ khỏi ‘đã nạp nhưng bị ghi đè’.”

**Purpose:** Cung cấp quy trình chẩn đoán import dựa trên bằng chứng thay vì thử chỉnh cấu hình ngẫu nhiên.
