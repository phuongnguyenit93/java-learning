---
video:
  url: ""
---

# Nguồn cấu hình và thứ tự ghi đè

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

## Spring Boot sắp xếp các nguồn thuộc tính như thế nào?

<!-- VIDEO_SECTION -->

### Scene 1 — Thứ tự ưu tiên quyết định nguồn thắng

**Time:** `00:00–00:55`

**Visual:** Vẽ một chồng `PropertySource` từ thấp lên cao: giá trị mặc định bằng mã → Config Data → biến môi trường hệ điều hành → JVM system properties → `SPRING_APPLICATION_JSON` → tham số dòng lệnh. Cho cùng khóa `app.mode` xuất hiện ở nhiều tầng rồi làm nổi bật tầng thắng.

**Script:** “Spring Boot không chọn giá trị ngẫu nhiên khi nhiều nguồn cùng khai báo một khóa. Các `PropertySource` có thứ tự ưu tiên; nguồn ở mức cao hơn có thể ghi đè nguồn thấp hơn. Ta chỉ cần nhớ các tầng lớn trước: giá trị mặc định ở thấp, Config Data cao hơn, rồi các đầu vào lúc chạy như biến môi trường, system property, JSON nội tuyến và tham số dòng lệnh. Khi chẩn đoán, hãy hỏi đúng loại nguồn và đúng thứ tự của Boot.”

**Purpose:** Thiết lập mô hình tư duy về thứ tự ưu tiên theo tầng thay vì các mẹo nhớ mơ hồ như “tệp ngoài luôn thắng”.

## Giá trị mặc định, Config Data và các nguồn ghi đè phía sau

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:** Giữ lại ba tầng: tệp trong jar, tệp ngoài jar và tham số dòng lệnh; bỏ các tầng còn lại khỏi màn hình.

**Script:** “Giờ ta thu nhỏ sơ đồ xuống một ví dụ triển khai quen thuộc để thấy cách các lớp ghi đè chồng lên nhau.”

**Purpose:** Chuyển từ mô hình tổng quát sang ví dụ cụ thể về lớp mặc định và lớp ghi đè.

### Scene 1 — Mặc định trong jar, ghi đè ngoài jar và lúc chạy

**Time:** `01:05–01:55`

**Visual:** Hiện ba dòng: `app.region=us-east` trong jar, `app.region=eu-west` ở tệp ngoài, `--app.region=ap-south` trên terminal. Xóa lần lượt tham số dòng lệnh rồi tệp ngoài để lộ chuỗi giá trị dự phòng.

**Script:** “Nếu jar mang `us-east`, tệp ngoài đặt `eu-west`, và lúc chạy có `--app.region=ap-south`, giá trị hiệu lực là `ap-south`. Bỏ tham số dòng lệnh thì tệp ngoài lộ ra; bỏ tiếp tệp ngoài thì giá trị đóng gói trong jar trở thành mặc định. Đây là mô hình rất hữu ích: một cấu hình nền hợp lý đi cùng gói ứng dụng, còn môi trường triển khai chỉ phủ phần cần thay đổi.”

**Purpose:** Chứng minh thứ tự ưu tiên bằng chuỗi giá trị dự phòng có thể quan sát và tái hiện.

## Biến môi trường, system properties, JSON nội tuyến và tham số dòng lệnh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:** Terminal phóng lớn, lần lượt hiện `APP_MODE`, `-Dapp.mode`, `SPRING_APPLICATION_JSON`, `--app.mode`.

**Script:** “Ngoài tệp, môi trường chạy có nhiều kênh ghi đè khác nhau, và thứ tự giữa chúng cũng có ý nghĩa.”

**Purpose:** Dẫn từ Config Data sang các nguồn lúc chạy có thứ tự ưu tiên cao hơn.

### Scene 1 — Các nguồn ghi đè lúc chạy không cùng một mức

**Time:** `02:05–02:55`

**Visual:** Dùng một khóa `app.mode` với bốn đầu vào lúc chạy; mũi tên thứ tự ưu tiên lần lượt đi từ biến môi trường hệ điều hành tới JVM system property, JSON rồi tham số dòng lệnh. Làm nổi bật tham số dòng lệnh là lớp cuối cùng trong ví dụ.

**Script:** “Trong nền tảng Boot 3.3 của module này, biến môi trường hệ điều hành đứng trước JVM system property, sau đó là `SPRING_APPLICATION_JSON`, rồi tham số dòng lệnh có thứ tự ưu tiên cao hơn nữa. Vì thế `--server.port=9090` có thể ghi đè cùng khóa đến từ tệp, biến môi trường hoặc `-D`. Nguồn ưu tiên cao rất tiện cho điều chỉnh lúc triển khai, nhưng nếu lạm dụng các ghi đè tạm thời thì hành vi ở môi trường sản xuất sẽ khó tái hiện.”

**Purpose:** Làm rõ thứ tự tương đối của các nguồn lúc chạy chính và hệ quả vận hành của ghi đè ngắn hạn.

## Vì sao @PropertySource có thể quá muộn với thuộc tính Boot được đọc sớm?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:05`

**Visual:** Chuyển từ chồng thứ tự ưu tiên sang trục thời gian khởi động; `Config Data` nằm sớm, bước làm mới application context nằm sau.

**Script:** “Thứ tự ưu tiên trả lời nguồn nào mạnh hơn, nhưng còn một trục khác cũng quan trọng: nguồn đó xuất hiện vào lúc nào.”

**Purpose:** Mở thêm chiều thời gian để giải thích giới hạn của `@PropertySource`.

### Scene 1 — Đúng giá trị nhưng xuất hiện quá muộn

**Time:** `03:05–03:55`

**Visual:** Trên trục thời gian khởi động của Boot, đặt `logging.*` và `spring.main.*` ở pha sớm, sau đó mới thêm `@PropertySource` khi application context được làm mới. Hiện chú thích “thuộc tính xuất hiện trong Environment về sau ≠ đã ảnh hưởng quyết định sớm”.

**Script:** “`@PropertySource` được thêm khi application context đang được làm mới, trong khi một số thiết lập Boot như `logging.*` hay `spring.main.*` có thể đã được đọc trước đó. Vì vậy thuộc tính có thể xuất hiện trong `Environment` về sau nhưng vẫn không thay đổi quyết định khởi động đã xảy ra. Với cấu hình cần tác động sớm, hãy dùng nguồn có mặt sớm như Config Data, biến môi trường, system property hoặc tham số dòng lệnh.”

**Purpose:** Phân biệt thứ tự ưu tiên với thời điểm trong vòng đời và giải thích trường hợp “có thuộc tính nhưng Boot vẫn dùng giá trị khác”.

## Cách suy luận nguồn nào thắng khi nhiều giá trị cạnh tranh

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:** Trục thời gian thu nhỏ thành danh sách kiểm tra sáu bước; khóa `app.mode` được ghim ở đầu danh sách.

**Script:** “Từ thứ tự ưu tiên và thời điểm xuất hiện, ta có thể biến việc chẩn đoán thành một quy trình thay vì thử sửa tệp ngẫu nhiên.”

**Purpose:** Chuyển kiến thức về thứ tự nguồn thành phương pháp chẩn đoán lặp lại được.

### Scene 1 — Chẩn đoán theo ứng viên và chuỗi dự phòng

**Time:** `04:05–05:00`

**Visual:** Danh sách kiểm tra: khóa chuẩn → mọi nguồn khai báo → nguồn có thực sự được nạp → thứ tự ưu tiên → profile/import → bên tiêu thụ. Chạy ví dụ `standard` trong tệp, `safe` từ `APP_MODE`, `fast` từ tham số dòng lệnh và xóa từng nguồn để thấy chuỗi dự phòng.

**Script:** “Khi giá trị bất ngờ, hãy bắt đầu từ khóa chuẩn, liệt kê mọi nguồn đang định nghĩa nó, xác nhận nguồn thật sự được nạp rồi mới so thứ tự ưu tiên. Sau đó kiểm tra profile và import vì chúng thay đổi tập ứng viên từ tệp. Với `standard`, `safe`, `fast`, tham số dòng lệnh thắng; bỏ nó thì biến môi trường thắng; bỏ tiếp biến môi trường thì tệp thắng. Nếu chuỗi này đúng, lúc đó mới nhìn sang binding hay mã tiêu thụ.”

**Purpose:** Cung cấp quy trình chẩn đoán có thể giải thích cả giá trị hiện tại lẫn giá trị dự phòng.

## Các nguồn thuộc tính dành riêng cho kiểm thử thuộc phạm vi nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:** Chồng nguồn theo thứ tự ưu tiên xuất hiện lại và thêm một vùng phía trên ghi “nguồn chỉ dành cho kiểm thử”.

**Script:** “Có một trường hợp dễ làm kết quả khác hẳn môi trường sản xuất: khi ứng dụng đang chạy trong ngữ cảnh kiểm thử.”

**Purpose:** Nối mô hình thứ tự ưu tiên thông thường với ngoại lệ do môi trường kiểm thử bổ sung nguồn riêng.

### Scene 1 — Nhận diện ghi đè của kiểm thử, rồi bàn giao sang Testing

**Time:** `05:10–05:55`

**Visual:** Hiện thuộc tính `properties` trên annotation kiểm thử của Boot, `@DynamicPropertySource`, `@TestPropertySource`; mũi tên cho thấy chúng chỉ xuất hiện trong ngữ cảnh kiểm thử. Cuối cảnh đặt biển chỉ hướng sang module “Spring Boot Testing”.

**Script:** “Kiểm thử Boot có thể thêm các nguồn có thứ tự ưu tiên cao như `properties` trên annotation kiểm thử, `@DynamicPropertySource` và `@TestPropertySource`. Module này cần nhắc chúng vì chúng ảnh hưởng mô hình thứ tự ưu tiên, nhưng vòng đời chi tiết thuộc Spring Boot Testing. Nếu một giá trị chỉ sai trong kiểm thử, hãy kiểm tra các nguồn này trước khi kết luận thứ tự ưu tiên của ứng dụng bình thường có vấn đề.”

**Purpose:** Giữ được bức tranh thứ tự ưu tiên đầy đủ nhưng không lấn sang phạm vi sở hữu của module Testing.
