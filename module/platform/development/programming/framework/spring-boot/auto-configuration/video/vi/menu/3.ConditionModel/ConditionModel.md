---
video:
  url: ""
---

# Mô hình lựa chọn bằng Condition

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

## Vì sao Auto-configuration cần Condition?

<!-- VIDEO_SECTION -->

### Scene 1 — Một phần tích hợp không thể giả định mọi ứng dụng giống nhau

**Time:** `00:00–00:50`


**Visual:**

Sơ đồ bốn ứng dụng có classpath, property, bean và loại context khác nhau cùng trỏ vào AcmeClientAutoConfiguration. Chỉ hai ứng dụng nhận bean mặc định.

**Script:**

Một thư viện có thể được dùng trong rất nhiều ứng dụng khác nhau. Có nơi đủ dependency, có nơi thiếu; có nơi bật tính năng, có nơi tắt; có ứng dụng web và cũng có ứng dụng không phải web. Nếu auto-configuration bỏ qua những khác biệt đó thì chỉ cần thêm JAR cũng có thể tạo bean không phù hợp. Condition biến dữ kiện của context thành quy tắc áp dụng rõ ràng.

**Purpose:**

Giải thích condition như hợp đồng về tính phù hợp, không phải chỉ là tối ưu khởi động.

## Condition theo Classpath

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:00`


**Visual:**

Tô sáng riêng cột classpath trong sơ đồ đầu.

**Script:**

Đầu vào dễ thấy nhất là classpath: nếu công nghệ cần tích hợp còn không tồn tại, cấu hình tương ứng không nên chạy.

**Purpose:**

Chuyển từ mô hình condition tổng quát sang nhóm condition dựa trên classpath.

### Scene 2 — Chỉ cấu hình khi thư viện có mặt

**Time:** `01:00–01:50`


**Visual:**

Hiển thị đoạn mã nhỏ ConditionalOnClass(AcmeClient.class) trên AcmeClientAutoConfiguration. Mô phỏng class có mặt và vắng mặt; cạnh đó là hai kết quả khớp và không khớp trong Condition Evaluation Report.

**Script:**

ConditionalOnClass đưa trạng thái classpath vào quyết định. Nếu AcmeClient có mặt, candidate có thể tiếp tục được xét; nếu class vắng mặt, nhánh cấu hình dừng lại. Ở mức configuration, Boot có thể xem annotation metadata trước khi phải nạp sớm mọi type tùy chọn. Ta sẽ quay lại ranh giới class loading ở cuối video.

**Purpose:**

Cho thấy classpath condition vừa bảo vệ điều kiện áp dụng vừa liên quan trực tiếp đến thiết kế dependency tùy chọn.

## Bean Condition và thời điểm đánh giá

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:00`


**Visual:**

Từ classpath chuyển sang bảng bean definition đã có trong ApplicationContext.

**Script:**

Có dependency thôi chưa đủ. Nhiều quyết định còn phụ thuộc vào những bean mà context đã biết tại thời điểm condition chạy.

**Purpose:**

Nối trạng thái classpath với trạng thái bean definition và mở ra vấn đề thời điểm đánh giá.

### Scene 3 — Condition chỉ nhìn thấy những definition đã có

**Time:** `02:00–02:50`


**Visual:**

Hiển thị ConditionalOnMissingBean trên phương thức @Bean. Chạy hai trường hợp ApplicationContextRunner cạnh nhau: không có bean người dùng thì mặc định xuất hiện; thêm cấu hình người dùng thì mặc định biến mất. Thêm dòng thời gian cho thấy bean definition của người dùng xuất hiện trước auto-configuration.

**Script:**

Bean condition lý giải dựa trên bean definition đã được xử lý. Với auto-configuration, definition phía ứng dụng có cơ hội xuất hiện trước, nên ConditionalOnMissingBean rất phù hợp cho back-off. Bằng chứng tốt nhất là hai kiểm thử context đối xứng: context không có bean người dùng phải nhận mặc định; context có bean người dùng phải làm mặc định biến mất. Thời điểm đánh giá và type mà condition kiểm tra đều là một phần của kết quả.

**Purpose:**

Biến vấn đề thời điểm đánh giá của bean condition thành bằng chứng thực thi cụ thể bằng kiểm thử context tập trung.

### Scene 4 — @ConditionalOnExpression có thể khởi tạo Bean quá sớm

**Time:** `02:50–03:35`

**Visual:**

Hiển thị một @ConditionalOnExpression có SpEL tham chiếu trực tiếp tới `acmeProperties`. Dòng thời gian cho thấy bean này bị khởi tạo sớm trong quá trình refresh, trước lớp hậu xử lý thông thường; cạnh đó đánh dấu `@ConfigurationProperties binding` không được áp dụng đầy đủ và trạng thái bean có thể chưa hoàn chỉnh.

**Script:**

Có một ngoại lệ rất đáng nhớ về thời điểm. Nếu @ConditionalOnExpression tham chiếu trực tiếp tới một bean trong SpEL, bean đó bị khởi tạo rất sớm khi context đang refresh. Khi ấy bean không còn đủ điều kiện nhận các bước hậu xử lý thông thường, trong đó có binding @ConfigurationProperties, nên trạng thái quan sát được có thể chưa hoàn chỉnh. Vì vậy đừng dùng tham chiếu bean trong biểu thức như một đường tắt; khi có thể, hãy ưu tiên condition có quy ước rõ theo classpath, property hoặc bean.

**Purpose:**

Làm rõ bẫy khởi tạo sớm của @ConditionalOnExpression và vì sao nó có thể làm sai giả định về trạng thái bean đã được hậu xử lý.

## Property Condition và đầu vào cấu hình

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:45`


**Visual:**

Từ bảng bean definition chuyển sang công tắc acme.client.enabled.

**Script:**

Ngoài cấu trúc của context, ứng dụng còn cần một cách tường minh để bật hoặc tắt hành vi bằng cấu hình.

**Purpose:**

Chuyển từ trạng thái bean sang trạng thái property mà không lấn sang nội dung binding của module khác.

### Scene 5 — Property thay đổi quyết định, không đổi trách nhiệm module

**Time:** `03:45–04:35`


**Visual:**

Hiển thị ConditionalOnProperty với prefix acme.client, name enabled, havingValue true, matchIfMissing true. Bên cạnh là hai trường hợp true và false với kết quả bean có hoặc không.

**Script:**

ConditionalOnProperty cho phép giá trị trong Environment tham gia quyết định. Ví dụ này chọn mặc định bật và cho phép người dùng tắt bằng acme.client.enabled=false. Ở đây ta chỉ quan tâm property làm condition đổi kết quả thế nào. Cách property được nạp, ưu tiên, bind hay validate vẫn thuộc Externalized Configuration.

**Purpose:**

Giải thích đúng ranh giới: module sở hữu ảnh hưởng của property lên quyết định lựa chọn, không sở hữu toàn bộ externalized configuration.

## Condition theo Resource và loại ứng dụng Web

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:35–04:45`


**Visual:**

Mở rộng bảng đầu vào condition thêm resource và loại ứng dụng.

**Script:**

Condition không dừng ở class, bean và property. Có những nhánh chỉ hợp lệ khi một resource hoặc đúng loại ứng dụng tồn tại.

**Purpose:**

Mở rộng mô hình tư duy condition sang resource và các nhánh chuyên biệt theo web context.

### Scene 6 — Cùng phần tích hợp, khác nhánh theo context

**Time:** `04:45–05:35`


**Visual:**

Cây cấu hình có core client ở giữa; nhánh Servlet chỉ sáng trong WebApplicationContextRunner, nhánh Reactive chỉ sáng trong ReactiveWebApplicationContextRunner. Thêm biểu tượng file cho ConditionalOnResource.

**Script:**

ConditionalOnResource có thể yêu cầu một resource cụ thể. ConditionalOnWebApplication và ConditionalOnNotWebApplication giúp tách nhánh theo loại context. Với phần tích hợp web, bằng chứng phù hợp không phải dựng HTTP API giả, mà là tạo đúng loại context bằng WebApplicationContextRunner hoặc ReactiveWebApplicationContextRunner rồi quan sát bean nào được đóng góp.

**Purpose:**

Cho thấy loại ứng dụng là đầu vào của quyết định lựa chọn và nên được chứng minh trực tiếp bằng loại ApplicationContext thay vì thêm một HTTP API không cung cấp bằng chứng tốt hơn.

## Class tùy chọn, Annotation Metadata và ranh giới liên kết JVM

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:45`


**Visual:**

Quay lại ConditionalOnClass rồi phóng to chữ ký phương thức @Bean dùng OptionalLibraryAdapter.

**Script:**

Còn một bẫy quan trọng: condition có thể đúng về ý tưởng nhưng JVM vẫn đụng vào type tùy chọn quá sớm.

**Purpose:**

Nối classpath condition với giới hạn class loading mà annotation metadata không tự bảo vệ được.

### Scene 7 — Cô lập type tùy chọn và chứng minh bằng FilteredClassLoader

**Time:** `05:45–06:35`


**Visual:**

So sánh hai cấu trúc: một phương thức @Bean dùng type tùy chọn ngay trong cấu hình chính và một cấu hình lồng có class-level ConditionalOnClass. Sau đó chạy ApplicationContextRunner với FilteredClassLoader và hiển thị context khởi động sạch, chỉ nhánh tùy chọn biến mất.

**Script:**

Đọc annotation metadata an toàn không có nghĩa mọi tham chiếu bên trong class cấu hình đều an toàn. Nếu chữ ký method buộc JVM phân giải một type đang thiếu, lỗi có thể xảy ra trước khi condition ở mức method kịp giúp. Cách chắc chắn hơn là cô lập type tùy chọn vào cấu hình riêng được bảo vệ ở mức class. FilteredClassLoader cho ta bằng chứng trực tiếp: giả lập thiếu dependency và xác nhận context vẫn khởi động, chỉ nhánh tùy chọn không còn.

**Purpose:**

Chứng minh ranh giới giữa condition metadata và JVM linkage bằng một thí nghiệm classpath có thể lặp lại.
