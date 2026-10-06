---
video:
  url: ""
---

# Kiểm thử quyết định Auto-configuration

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

## Vì sao Auto-configuration cần kiểm thử Context tập trung?

<!-- VIDEO_SECTION -->

### Scene 1 — Auto-configuration là ma trận quyết định, không phải một đường chạy thuận lợi duy nhất

**Time:** `00:00–00:50`

**Visual:**

Bảng ma trận có các trục bean người dùng, property, classpath và loại context. Các ô hiển thị bean có mặt hoặc vắng mặt. Một SpringBootTest duy nhất chỉ được tô vào một ô.

**Script:**

Nếu chỉ chạy một ứng dụng hoàn chỉnh rồi thấy khởi động thành công, ta mới chứng minh được một điểm trong ma trận. Auto-configuration có thể đổi kết quả theo bean người dùng, property, classpath và loại context. Vì thế ta cần kiểm thử tập trung, nơi mỗi test dựng đúng một trạng thái và quan sát bean nào được đóng góp. Mục tiêu là chứng minh quy tắc quyết định, không phải chỉ chứng minh ứng dụng có thể khởi động.

**Purpose:**

Giải thích vì sao kiểm thử context tập trung phù hợp hơn một SpringBootTest chỉ kiểm tra đường chạy thuận lợi cho auto-configuration.

## ApplicationContextRunner

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`

**Visual:**

Từ ma trận chọn một ô rồi thu nhỏ thành đoạn ApplicationContextRunner với AutoConfigurations.of.

**Script:**

Muốn kiểm tra từng ô độc lập, ta cần một context nhỏ, cấu hình được và tạo mới cho mỗi lần chạy.

**Purpose:**

Chuyển từ chiến lược kiểm thử sang công cụ cốt lõi của Boot cho auto-configuration.

### Scene 2 — ApplicationContextRunner tạo ca tái hiện nhỏ và sạch

**Time:** `01:00–01:50`

**Visual:**

Hiển thị ApplicationContextRunner được cấu hình bằng AutoConfigurations.of(AcmeClientAutoConfiguration.class). Sau khi chạy, làm nổi bật phép kiểm tra bean có mặt/vắng mặt và biểu tượng context được đóng sau test.

**Script:**

ApplicationContextRunner cho phép đăng ký đúng auto-configuration cần kiểm tra mà không khởi động toàn bộ ứng dụng thực tế. Mỗi lần chạy tạo một context mới, ta có thể thêm cấu hình người dùng, property hoặc classloader rồi kiểm tra kết quả. Cấu trúc test vì thế rất gần với câu hỏi học tập: đây là trạng thái đầu vào, đây là bean Boot đóng góp, và context được dọn sạch sau khi phần kiểm tra kết thúc.

**Purpose:**

Cho người học mô hình tư duy thiết lập → chạy → quan sát của ApplicationContextRunner.

## Kiểm thử Back-off và các biến thể Property

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`

**Visual:**

Nhân context runner thành bốn trường hợp: mặc định, bean tùy chỉnh, enabled=true và enabled=false.

**Script:**

Một annotation có vẻ đúng vẫn có thể nhắm sai type hoặc dùng sai havingValue. Ta cần kiểm tra cả hai phía của quyết định.

**Purpose:**

Biến back-off và property condition thành các biến thể kiểm thử đối xứng.

### Scene 3 — Kiểm thử hai phía của back-off và property

**Time:** `02:00–02:50`

**Visual:**

Trường hợp bean tùy chỉnh dùng withUserConfiguration; các trường hợp property dùng withPropertyValues. Hiển thị hasSingleBean cho nhánh tùy chỉnh và doesNotHaveBean khi bị tắt.

**Script:**

Với back-off, đừng chỉ kiểm tra bean mặc định xuất hiện. Thêm cấu hình của người dùng và xác nhận bean mặc định biến mất, đồng thời phân biệt rõ bean tùy chỉnh với bean mặc định. Với property, chạy cả enabled và disabled để bắt logic havingValue bị đảo hoặc matchIfMissing bị hiểu sai. Hai phía của condition tạo bằng chứng mạnh hơn nhiều so với chỉ nhìn annotation trong mã nguồn.

**Purpose:**

Chứng minh quy tắc condition bằng các biến thể đối xứng và bắt lỗi type/giá trị mục tiêu thường gặp.

## Kiểm thử khi Classpath thiếu Dependency bằng FilteredClassLoader

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:50–03:00`

**Visual:**

Từ đầu vào property chuyển sang đầu vào classpath; kéo AcmeClient.class ra khỏi một classloader mô phỏng.

**Script:**

Có một trạng thái khó tái hiện chỉ bằng property: dependency tồn tại trong test build nhưng ta muốn giả lập lúc nó vắng mặt.

**Purpose:**

Chuyển sang bằng chứng khi class vắng mặt trên classpath mà runner tập trung hỗ trợ trực tiếp.

### Scene 4 — FilteredClassLoader chứng minh dependency tùy chọn thật sự tùy chọn

**Time:** `03:00–03:50`

**Visual:**

Hiển thị withClassLoader(new FilteredClassLoader(AcmeClient.class)). Kết quả: bean liên quan vắng mặt, context vẫn khởi động và không có NoClassDefFoundError.

**Script:**

FilteredClassLoader cho phép ẩn một class hoặc package dù dependency vẫn có trong môi trường chạy của test. Đây là cách rất mạnh để kiểm tra thiết kế dependency tùy chọn. Nếu sự cô lập đúng, nhánh auto-configuration liên quan chỉ đơn giản không áp dụng. Nếu mã nguồn liên kết quá sớm tới type đã bị ẩn, test sẽ phơi ra lỗi liên kết ngay. Một HTTP endpoint không thể tạo loại trạng thái classpath này hiệu quả bằng kiểm thử context.

**Purpose:**

Dùng classloader như đầu vào kiểm thử để chứng minh cả condition và cấu trúc cô lập.

## Context Runner cho Servlet và Reactive

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:00`

**Visual:**

Tách một nhánh thành không phải web, Servlet và Reactive; đặt ba runner tương ứng bên dưới.

**Script:**

Một số condition phụ thuộc loại ứng dụng. Khi đó chính loại context là đầu vào của thí nghiệm.

**Purpose:**

Nối ma trận kiểm thử với web-application condition mà không tạo API giả.

### Scene 5 — Chọn runner theo loại context cần chứng minh

**Time:** `04:00–04:50`

**Visual:**

ApplicationContextRunner dưới context không phải web; WebApplicationContextRunner dưới Servlet; ReactiveWebApplicationContextRunner dưới Reactive. Mỗi runner làm nổi bật bean dành riêng cho context tương ứng.

**Script:**

ApplicationContextRunner mặc định tạo context không phải web. Nếu auto-configuration chỉ áp dụng cho Servlet hoặc Reactive, hãy dùng đúng runner chuyên biệt. Điều ta cần chứng minh là quyết định lựa chọn theo loại context, không phải hành vi của một HTTP endpoint. Với auto-configuration, context runner cho bằng chứng trực tiếp hơn về loại ApplicationContext và tránh kéo thêm nội dung MVC hoặc WebFlux không liên quan tới quyết định condition đang học.

**Purpose:**

Chứng minh web condition bằng đúng loại context và giữ ranh giới với Web Runtime.

## Bằng chứng từ Condition Evaluation trong Test

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:50–05:00`

**Visual:**

Phép kiểm tra doesNotHaveBean thất bại, sau đó mở Condition Evaluation Report của chính context test.

**Script:**

Bean có mặt hay vắng mặt là quy tắc chính. Khi kết quả bất ngờ, report giúp ta đi từ dấu hiệu lỗi đến condition cụ thể.

**Purpose:**

Kết thúc phần kiểm thử bằng lớp bằng chứng chẩn đoán hỗ trợ phép kiểm tra.

### Scene 6 — Report giải thích vì sao phép kiểm tra thất bại

**Time:** `05:00–05:50`

**Visual:**

Hiển thị bean được kỳ vọng vắng mặt nhưng thực tế lại có mặt. Mở report, làm nổi bật condition khớp gây ra kết quả, rồi sửa đầu vào kiểm thử hoặc quy tắc áp dụng. Không biến toàn bộ report thành một bản chụp cố định.

**Script:**

Đôi khi phép kiểm tra chỉ cho ta biết kết quả sai, chưa cho biết vì sao. Khi đó hãy bật listener hoặc đọc Condition Evaluation Report trong kiểm thử context tập trung và tìm đúng auto-configuration đang kiểm tra. Report chỉ ra condition nào khớp hoặc không khớp để ta sửa đúng quy tắc áp dụng hay đầu vào kiểm thử. Đừng biến mọi test thành bản chụp của cả report; sự có mặt/vắng mặt của bean và hành vi vẫn là quy tắc chính.

**Purpose:**

Đặt Condition Evaluation Report đúng vai trò: bằng chứng chẩn đoán hỗ trợ, không thay thế việc kiểm tra hành vi.
