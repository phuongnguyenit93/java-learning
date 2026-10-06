---
video:
  url: ""
---

# Mô hình tư duy về Externalized Configuration

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

## Externalized Configuration là gì và vì sao cần?

<!-- VIDEO_SECTION -->

### Scene 1 — Một gói ứng dụng, nhiều môi trường

**Time:** `00:00–00:55`

**Visual:** Mở sơ đồ một `app.jar` ở giữa, lần lượt nối tới ba hộp `dev`, `staging`, `prod`; bên cạnh mỗi hộp hiện các giá trị khác nhau cho URL cơ sở dữ liệu, thời gian chờ và `server.port`. Cuối cảnh gom các đầu vào vào một hộp “cấu hình có hiệu lực”.

**Script:** “Externalized Configuration giải quyết một nhu cầu rất thực tế: cùng một bản ứng dụng đã đóng gói phải chạy được ở nhiều môi trường mà không sửa lại logic Java. Thứ thay đổi là các giá trị phụ thuộc môi trường như URL, thời gian chờ hay cổng. Spring Boot không coi cấu hình là một tệp duy nhất; nhiều nguồn đầu vào cùng đóng góp vào một góc nhìn cấu hình đã được phân giải, rồi góc nhìn đó mới ảnh hưởng hành vi lúc chạy.”

**Purpose:** Đặt mô hình nền tảng: tách giá trị theo môi trường khỏi mã ứng dụng và nhìn cấu hình như tập nhiều nguồn cùng tạo nên giá trị có hiệu lực.

## Giá trị cấu hình và mã ứng dụng khác nhau thế nào?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:** Thu nhỏ sơ đồ môi trường và đặt cạnh một khối mã Java; giữa hai khối xuất hiện câu hỏi “Giá trị nào nên đổi bằng cấu hình?”.

**Script:** “Tách được cấu hình ra ngoài chưa đủ; bước tiếp theo là xác định thứ gì thực sự nên cấu hình và thứ gì vẫn phải thuộc về mã ứng dụng.”

**Purpose:** Nối nhu cầu triển khai đa môi trường với ranh giới trách nhiệm giữa dữ liệu cấu hình và logic ứng dụng.

### Scene 1 — Cấu hình không thay thế logic nghiệp vụ

**Time:** `01:05–01:55`

**Visual:** Hiện `payment.retry.max-attempts=4` bên trái và mã giả cho cơ chế thử lại bên phải. Làm nổi bật số `4`, rồi làm nổi bật phần quyết định ngoại lệ nào được thử lại và cách xử lý sau lần cuối.

**Script:** “`payment.retry.max-attempts=4` là cấu hình vì đội vận hành có thể đổi con số này mà năng lực cốt lõi của ứng dụng vẫn giữ nguyên. Nhưng thuật toán thử lại, loại lỗi nào được thử lại và hành vi sau lần thất bại cuối cùng vẫn là logic chương trình. Một câu hỏi hữu ích là: nếu thay giá trị giữa các môi trường mà không đổi quy tắc nghiệp vụ, nó thường là ứng viên tốt cho cấu hình.”

**Purpose:** Làm rõ tiêu chí thực hành để phân biệt giá trị triển khai với hành vi mà mã ứng dụng phải sở hữu.

## Environment như góc nhìn đã phân giải của cấu hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:** Các nguồn cấu hình khác nhau cùng đổ vào một biểu tượng `Environment`; phần mã Java chỉ còn một mũi tên đọc từ `Environment`.

**Script:** “Khi nhiều nguồn cùng tồn tại, mã ứng dụng không nên tự đi đọc từng nơi. Spring gom chúng lại và cho ứng dụng một điểm nhìn chung.”

**Purpose:** Chuyển từ ranh giới cấu hình và mã ứng dụng sang cơ chế ứng dụng quan sát giá trị đã được Spring phân giải.

### Scene 1 — Đọc giá trị có hiệu lực

**Time:** `02:05–02:55`

**Visual:** Mở đoạn Java `environment.getProperty("app.region")`. Bên dưới hiện ba ứng viên `us-east`, `eu-west`, `ap-south`; sau đó chỉ giữ lại giá trị thắng và gắn nhãn “giá trị có hiệu lực”.

**Script:** “`Environment` là góc nhìn đã phân giải sau khi các nguồn thuộc tính được tập hợp và sắp thứ tự. Ứng dụng hỏi một khóa như `app.region` và nhận giá trị có hiệu lực. Điểm quan trọng là giá trị đó vẫn có thể bắt nguồn từ tệp, biến môi trường, JVM system property hay dòng lệnh. Đọc giá trị thì đơn giản; giải thích vì sao nó thắng mới cần hiểu nguồn và thứ tự ưu tiên.”

**Purpose:** Giải thích vai trò của `Environment` mà không đồng nhất nó với một nguồn cấu hình cụ thể.

## Các nhóm nguồn cấu hình chính

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:05`

**Visual:** Từ `Environment`, bung ra các thẻ đại diện cho tệp, biến môi trường hệ điều hành, JVM, JSON và dòng lệnh.

**Script:** “Muốn giải thích giá trị thắng từ đâu, trước tiên ta phải nhận diện các nhóm nguồn có thể tham gia.”

**Purpose:** Dẫn từ góc nhìn đã phân giải về các loại đầu vào tạo nên góc nhìn đó.

### Scene 1 — Nhận diện nguồn trước khi nói về thứ tự ưu tiên

**Time:** `03:05–03:55`

**Visual:** Dựng bảng năm hàng: giá trị mặc định bằng mã; Config Data; biến môi trường hệ điều hành/JVM system property; `SPRING_APPLICATION_JSON`/dòng lệnh; framework/container. Góc cuối bảng có thẻ “ghi đè khi kiểm thử → Testing”.

**Script:** “Spring Boot có nhiều nhóm nguồn: giá trị mặc định bằng mã, Config Data như `application.properties` hay YAML, đầu vào lúc chạy như biến môi trường và JVM system property, rồi JSON nội tuyến hoặc tham số dòng lệnh. Một số nguồn từ container và nguồn dành riêng cho kiểm thử cũng tồn tại. Hãy tách hai câu hỏi: giá trị có thể đến từ đâu, và khi nhiều nguồn cùng có một khóa thì nguồn nào thắng.”

**Purpose:** Cung cấp bản đồ các nhóm nguồn và tách rõ câu hỏi “nguồn nào tham gia” khỏi câu hỏi “nguồn nào có thứ tự ưu tiên cao hơn”.

## Từ đầu vào cấu hình đến giá trị có hiệu lực khi chạy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:** Các thẻ nguồn xếp lại thành một luồng dọc, từ “phát hiện nguồn” xuống “tiêu thụ giá trị”.

**Script:** “Các nguồn không được đọc như những mảnh rời rạc; lúc khởi động chúng đi qua một chuỗi bước có thứ tự.”

**Purpose:** Chuyển từ danh mục nguồn sang luồng phân giải cấu hình của Boot.

### Scene 1 — Luồng phân giải

**Time:** `04:05–05:05`

**Visual:** Hiện dần sơ đồ: phát hiện nguồn → nạp Config Data → kích hoạt tài liệu theo profile → áp dụng thứ tự ưu tiên → phân giải khóa → `Environment/@Value` hoặc binding. Sau đó chèn ví dụ `server.port`: 8080 trong jar, 8081 trong tệp ngoài, 9090 từ dòng lệnh; làm nổi bật 9090 ở cuối.

**Script:** “Có thể hình dung Boot đi qua sáu bước: tìm các nguồn có thể dùng, nạp Config Data, quyết định tài liệu profile nào tham gia, áp dụng thứ tự ưu tiên, chọn giá trị có hiệu lực rồi mới để mã ứng dụng đọc trực tiếp hoặc binding vào đối tượng có kiểu. Ví dụ `server.port` có ba ứng viên 8080, 8081 và 9090 từ dòng lệnh; ứng dụng chỉ quan sát giá trị cuối cùng sau khi luồng này hoàn tất.”

**Purpose:** Tạo mô hình tư duy theo giai đoạn và dùng một khóa cụ thể để chứng minh nhiều nguồn hội tụ thành một giá trị có hiệu lực.

## Phạm vi trách nhiệm và điểm bàn giao của module

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:05–05:15`

**Visual:** Luồng xử lý thu nhỏ vào giữa; xung quanh xuất hiện các nhãn Spring Framework, Testing, Auto-Configuration, Cloud Config, vận hành ứng dụng và Actuator.

**Script:** “Luồng này chạm vào nhiều phần của hệ sinh thái Spring, nên cần chốt rõ phần nào module này chịu trách nhiệm.”

**Purpose:** Đặt ranh giới chương trình học trước khi các chương sau đi sâu vào từng cơ chế.

### Scene 1 — Biết điểm dừng của Externalized Configuration

**Time:** `05:15–06:05`

**Visual:** Làm nổi bật vùng “Externalized Configuration” gồm các nguồn, thứ tự ưu tiên, vị trí/import, profile, `Environment`, `@ConfigurationProperties`, binding, tích hợp xác thực và metadata. Các chủ đề lân cận được nối ra ngoài bằng mũi tên bàn giao.

**Script:** “Module này sở hữu cách Spring Boot nạp và phân giải cấu hình: nguồn, thứ tự ưu tiên, Config Data, profile, đọc giá trị, `@ConfigurationProperties`, binding, tích hợp xác thực và metadata. Chi tiết sâu của Spring Framework, ghi đè dành riêng cho kiểm thử, quyết định auto-configuration, cấu hình từ xa hay quản lý secret được bàn giao sang module tương ứng. Câu hỏi xuyên suốt cần giữ là: Boot biến các đầu vào cấu hình thành giá trị đáng tin cậy cho ứng dụng dùng như thế nào?”

**Purpose:** Khóa ranh giới sở hữu để người học biết nội dung nào thuộc module và nội dung nào chỉ được nhắc tới như điểm bàn giao.
