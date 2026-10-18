---
video:
  url: ""
---

# Chẩn đoán và tổng hợp luồng cấu hình

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

## Luồng phân giải cấu hình từ đầu đến cuối

<!-- VIDEO_SECTION -->

### Scene 1 — Ghép toàn bộ module thành một luồng thống nhất

**Time:** `00:00–01:00`

**Visual:** Hiện lần lượt sơ đồ: nguồn + vị trí/import Config Data → kích hoạt tài liệu → thứ tự ưu tiên `PropertySource` → `Environment` đã phân giải → nhánh `Environment/@Value` và `@ConfigurationProperties` → chuyển đổi kiểu + validation.

**Script:** “Toàn bộ module có thể thu về một luồng duy nhất. Spring Boot khám phá nguồn và Config Data, profile quyết định tài liệu nào tham gia, thứ tự ưu tiên xác định giá trị có hiệu lực, rồi ứng dụng đọc trực tiếp hoặc bind sang đối tượng có kiểu. Chuyển đổi kiểu và validation chỉ xuất hiện sau đó. Khi chẩn đoán, hãy xác định giai đoạn trước khi sửa cấu hình; một khóa chưa từng được nạp thì không thể được chữa bằng validation hay binding.”

**Purpose:** Tạo bản đồ tổng hợp để người học định vị mọi cơ chế và lỗi cấu hình trong cùng một luồng.

## Chẩn đoán lỗi nạp cấu hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:** Sơ đồ chỉ giữ lại giai đoạn đầu “nguồn / vị trí / import” và tô đỏ một vị trí bị thiếu.

**Script:** “Hãy bắt đầu từ đầu luồng: nếu tài nguyên chưa vào được Config Data, mọi bước phía sau đều chưa có dữ liệu để xử lý.”

**Purpose:** Chuyển từ bức tranh tổng thể sang loại lỗi đầu tiên theo đúng thứ tự xử lý.

### Scene 2 — Xác minh vị trí và tính bắt buộc của tài nguyên

**Time:** `01:10–01:35`

**Visual:** Danh sách kiểm tra trên terminal: vị trí có nằm trong tập tìm kiếm/import → cố định hay tương đối → bắt buộc hay `optional:`. Hiện lỗi khởi động khi thiếu Config Data bắt buộc và so sánh với trường hợp dùng `optional:`.

**Script:** “Khi Config Data không vào được ứng dụng, hãy bắt đầu từ địa chỉ và hợp đồng tồn tại của tài nguyên. Kiểm tra vị trí có thật sự nằm trong tập tìm kiếm hoặc import không, đường dẫn được phân giải theo kiểu cố định hay tương đối, rồi xác định tài nguyên là bắt buộc hay được phép thiếu bằng `optional:`. Nếu một tài nguyên bắt buộc không tồn tại, lỗi cần được xử lý ngay ở đây.”

**Purpose:** Tách riêng lỗi địa chỉ và tính bắt buộc của tài nguyên trước khi đi sâu vào khả năng đọc nội dung.

### Scene 3 — Xác nhận Boot đọc và phân tích được tài nguyên

**Time:** `01:35–02:00`

**Visual:** Giữ nguyên tài nguyên đã tìm thấy, rồi lần lượt kiểm tra quyền đọc, phần mở rộng/extension hint và bộ phân tích định dạng. Cuối cảnh đặt mốc “nạp thành công” trước bước xét thứ tự ưu tiên.

**Script:** “Tài nguyên tồn tại vẫn chưa đủ. Boot còn phải đọc được nội dung và chọn đúng bộ phân tích cho định dạng đó. Với tệp không có phần mở rộng, kiểm tra extension hint; với nội dung lỗi hoặc định dạng không hỗ trợ, xử lý ngay ở bước nạp. Chỉ sau khi tài nguyên được nạp thành công mới có ý nghĩa để hỏi property nào thắng theo thứ tự ưu tiên.”

**Purpose:** Phân biệt rõ ‘đã tìm thấy tài nguyên’ với ‘đã nạp được Config Data’ để tránh chuyển sang precedence quá sớm.

## Chẩn đoán lỗi profile và kích hoạt

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:** Tài nguyên đã được nạp thành công nhưng tài liệu theo profile vẫn mờ, kèm nhãn “không được kích hoạt”.

**Script:** “Một tệp có thể được tìm thấy và phân tích đúng nhưng tài liệu bên trong vẫn không tham gia vì điều kiện kích hoạt không khớp.”

**Purpose:** Tách việc nạp tệp thành công khỏi việc tài liệu theo profile có thực sự tham gia hay không.

### Scene 4 — Tệp tồn tại không có nghĩa tài liệu đang được kích hoạt

**Time:** `02:10–03:00`

**Visual:** Hiện các profile đang hoạt động, `application-prod.properties`, `spring.config.activate.on-profile`, include và group; sau đó minh họa `prod,live` và quy tắc giá trị sau cùng thắng. Khoanh đỏ một cấu hình kích hoạt sai.

**Script:** “Khi giá trị mong đợi bị thiếu dù tệp vẫn tồn tại, hãy kiểm tra profile nào đang hoạt động, tệp theo profile có khớp không, điều kiện `on-profile` có đúng không và các khai báo `spring.profiles.active`, `spring.profiles.include` hay nhóm profile có nằm ở vị trí Spring Boot cho phép hay không. Với nhiều profile, nhớ quy tắc giá trị sau cùng thắng trong phạm vi nhóm vị trí. Đừng chữa lỗi kích hoạt bằng cách chép cùng một khóa sang nhiều tệp; cách đó chỉ che đi tài liệu đang tham gia sai.”

**Purpose:** Cung cấp danh sách kiểm tra kích hoạt tách biệt với việc nạp tệp và thứ tự ưu tiên.

## Chẩn đoán lỗi binding và chuyển đổi kiểu

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:** Khóa đã xuất hiện trong `Environment`, nhưng mũi tên sang đối tượng có kiểu bị ngắt.

**Script:** “Nếu khóa đã có giá trị có hiệu lực trong `Environment` mà đối tượng vẫn sai, ta đã đi qua bước nạp và kích hoạt; lúc này cần kiểm tra phía binding.”

**Purpose:** Chuyển từ lỗi ở phía nguồn sang lỗi binding và chuyển đổi kiểu.

### Scene 5 — Trước hết, xác nhận khóa đã có mặt trong Environment

**Time:** `03:10–03:35`

**Visual:** Hiện `client.timeout=fast` trong `Environment`. Cây chẩn đoán tách thành hai nhánh: “không có khóa” quay về nạp/kích hoạt/tên khóa; “có khóa” mới đi tiếp vào binder.

**Script:** “Trước khi kết luận binder có lỗi, hãy xác nhận khóa thực sự có mặt trong `Environment`. Nếu khóa không tồn tại, nguyên nhân vẫn nằm ở việc nạp cấu hình, điều kiện kích hoạt hoặc tên khóa. Chỉ khi khóa đã có giá trị có hiệu lực thì mới nên chuyển sang kiểm tra mô hình Java đích.”

**Purpose:** Tách lỗi phân giải nguồn khỏi lỗi binding trước khi kiểm tra kiểu đích.

### Scene 6 — Sau đó tách lỗi cấu trúc khỏi lỗi chuyển đổi kiểu

**Time:** `03:35–04:05`

**Visual:** Giữ khóa đã có mặt và tách thành hai nhánh: prefix/đường dẫn collection không khớp nên không tới đúng trường; `client.timeout=fast` tới đúng trường `Duration` nhưng thất bại khi chuyển đổi. Bên cạnh nhánh chuyển đổi hiện tên property, giá trị bị từ chối, nguồn gốc và kiểu đích.

**Script:** “Khi khóa đã có, hãy hỏi hai câu riêng. Thứ nhất, tên và cấu trúc có khớp với mô hình Java, bao gồm prefix, đối tượng lồng và đường dẫn collection hay không? Thứ hai, nếu đã tới đúng trường, giá trị văn bản có chuyển được sang kiểu đích hay không? Đọc cùng tên property, giá trị bị từ chối, nguồn gốc và kiểu đích sẽ cho biết nhánh nào đang lỗi.”

**Purpose:** Tách bài toán binding thành hai quyết định có thể quan sát: khớp cấu trúc trước, chuyển đổi kiểu sau.

## Chẩn đoán lỗi xác thực

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:** Đối tượng có kiểu được tạo thành công rồi dừng tại lớp ràng buộc.

**Script:** “Khi binding đã tạo được mô hình có kiểu, vẫn còn một lớp cuối có thể chủ động từ chối cấu hình không hợp lệ.”

**Purpose:** Nối việc chuyển đổi kiểu thành công với khả năng thất bại ở validation.

### Scene 7 — Lỗi validation cho biết dữ liệu đã đi tới bước kiểm tra hợp đồng

**Time:** `04:15–05:00`

**Visual:** Hiện `@NotBlank`, `@Positive`, `@Valid` cho đối tượng lồng; một báo cáo lỗi nối property → giá trị bị từ chối → ràng buộc. Gạch bỏ hành động “xóa ràng buộc để ứng dụng khởi động được”.

**Script:** “Lỗi validation cho biết Spring Boot đã bind được dữ liệu nhưng đối tượng vi phạm ràng buộc. Hãy lần theo báo cáo tới property, giá trị bị từ chối và ràng buộc, rồi sửa cấu hình hoặc hợp đồng theo đúng ý định miền. Với đối tượng lồng, hãy kiểm tra `@Valid` nếu giá trị sai mà không bị phát hiện. Xóa `@NotBlank` hay `@Positive` chỉ để ứng dụng chạy sẽ làm mất chính hàng rào phát hiện lỗi sớm.”

**Purpose:** Hướng người học sửa đúng lớp lỗi và giữ hợp đồng validation có ý nghĩa.

## Lần theo một khóa đến giá trị có hiệu lực

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:** Tất cả giai đoạn thu nhỏ, chỉ giữ một khóa ở giữa với nhiều mũi tên từ các nguồn cạnh tranh.

**Script:** “Khi ứng dụng vẫn khởi động nhưng một khóa có giá trị bất ngờ, hãy lần ngược từ giá trị có hiệu lực về các nguồn đã tham gia.”

**Purpose:** Chuyển từ chẩn đoán theo giai đoạn sang lần theo một khóa cụ thể.

### Scene 8 — Lần theo khóa từ kết quả về nguồn

**Time:** `05:10–06:05`

**Visual:** Checklist sáu bước: khóa chuẩn → giá trị quan sát được → các nguồn ứng viên → tài liệu đang hoạt động → thứ tự ưu tiên → placeholder/chuyển đổi kiểu. Thêm ghi chú “Actuator `env`/`configprops`: bằng chứng khi được bật” và biểu tượng che dữ liệu nhạy cảm.

**Script:** “Hãy ghi khóa chuẩn, xác nhận giá trị ứng dụng thực sự nhìn thấy, liệt kê mọi nguồn có thể định nghĩa khóa đó, xác nhận tài liệu theo profile nào đang tham gia, rồi áp dụng thứ tự ưu tiên. Chỉ sau khi biết nguồn nào thắng mới kiểm tra placeholder hoặc chuyển đổi kiểu. Actuator `env` và `configprops` có thể cung cấp bằng chứng khi được bật có chủ ý, nhưng các endpoint này thuộc module Actuator và dữ liệu nhạy cảm vẫn phải được bảo vệ.”

**Purpose:** Cung cấp quy trình lần theo giá trị có hiệu lực, dù có hay không sử dụng Actuator.

## Chọn tệp, biến môi trường, tham số dòng lệnh, profile, @Value hay @ConfigurationProperties

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:05–06:15`

**Visual:** Sau khi lần xong một khóa, màn hình chuyển thành bảng quyết định với hai cột “nguồn giá trị” và “cách mã ứng dụng sử dụng”.

**Script:** “Khi đã hiểu toàn bộ luồng, việc chọn cơ chế cấu hình sẽ rõ hơn nếu tách hai câu hỏi độc lập.”

**Purpose:** Chuyển từ chẩn đoán sang cách chọn cơ chế dựa trên mô hình tư duy đã hoàn chỉnh.

### Scene 9 — Chọn nguồn giá trị và cách sử dụng riêng nhau

**Time:** `06:15–07:10`

**Visual:** Bảng: giá trị mặc định được quản lý cùng mã nguồn → tệp cấu hình đóng gói; giá trị theo môi trường triển khai → tệp ngoài/biến môi trường; ghi đè cho một lần chạy → CLI; biến thể có tên → profile; giá trị đơn lẻ → `@Value`; không gian tên có cấu trúc → `@ConfigurationProperties`; thư mục một tệp cho mỗi khóa → `configtree:`.

**Script:** “Hãy tách câu hỏi ‘giá trị đến từ đâu’ khỏi câu hỏi ‘mã ứng dụng sử dụng giá trị thế nào’. Giá trị mặc định được quản lý cùng mã nguồn phù hợp với tệp đóng gói; giá trị theo môi trường triển khai có thể nằm ở tệp ngoài hoặc biến môi trường; ghi đè cho một lần chạy phù hợp với CLI; biến thể có tên phù hợp với profile. Ở phía sử dụng, giá trị đơn lẻ có thể dùng `@Value`, còn một không gian tên có cấu trúc nên dùng `@ConfigurationProperties`. Đối tượng có kiểu hoàn toàn có thể nhận giá trị có hiệu lực mà nguồn thắng là biến môi trường.”

**Purpose:** Tổng hợp các cơ chế thành hướng dẫn lựa chọn mà không trộn việc chọn nguồn với cách mã ứng dụng tiêu thụ giá trị.

## Bàn giao phạm vi trách nhiệm sang Testing, Application Runtime, Auto-Configuration, Cloud Config và quản lý secret

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:10–07:20`

**Visual:** Bảng quyết định thu lại thành vùng Externalized Configuration; các mũi tên đi ra các module lân cận.

**Script:** “Hiểu bài toán cấu hình cũng đồng nghĩa với việc nhận ra khi nào câu hỏi đã đi ra ngoài phạm vi của module này.”

**Purpose:** Kết thúc module bằng ranh giới trách nhiệm rõ ràng thay vì mở rộng thêm nội dung ngoài phạm vi.

### Scene 10 — Giữ đúng điểm bàn giao sang module khác

**Time:** `07:20–08:15`

**Visual:** Sơ đồ bàn giao: ghi đè chỉ dùng trong kiểm thử → Testing; điều kiện dựa trên property → Auto-Configuration; logging/task/availability lúc chạy → Application Runtime; endpoint `env/configprops` → Actuator; cấu hình tập trung từ xa → Spring Cloud Config; vòng đời, xoay vòng và quyền truy cập secret → bảo mật hoặc hạ tầng.

**Script:** “Externalized Configuration chịu trách nhiệm cho nguồn cấu hình, Config Data, thứ tự ưu tiên, profile, cách đọc giá trị đã phân giải, binding, tích hợp validation và metadata. Ghi đè chỉ dành cho kiểm thử thuộc Testing; quyết định bean theo property thuộc Auto-Configuration; thiết lập vận hành lúc chạy thuộc Application Runtime; endpoint `env/configprops` thuộc Actuator; cấu hình tập trung từ xa thuộc Spring Cloud Config; vòng đời và xoay vòng secret thuộc bảo mật hoặc hạ tầng. Khi rời module này, bạn cần vừa giải thích được Spring Boot phân giải cấu hình thế nào, vừa nhận ra lúc nào vấn đề đã thuộc trách nhiệm của module khác.”

**Purpose:** Chốt ranh giới học tập và tạo điểm bàn giao rõ sang các module liên quan mà không tự mở rộng phạm vi.
