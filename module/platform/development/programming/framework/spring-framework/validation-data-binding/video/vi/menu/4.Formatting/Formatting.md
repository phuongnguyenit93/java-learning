---
video:
  url: ""
---

# Định dạng trường dữ liệu

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

## Vì sao Formatting tách khỏi Conversion tổng quát?

<!-- VIDEO_SECTION -->

### Scene 1 — Khi cách biểu diễn text là một phần của yêu cầu

**Time:** `00:00–01:05`

**Visual:**

Hiển thị typed value `1234.5`, rồi tách thành `1,234.5` cho US và `1.234,5` cho Germany. Bên cạnh, đặt một conversion `String -> OrderId` không có input locale.

**Script:**

Conversion và Formatting đều có thể thay đổi representation, nhưng chúng tối ưu cho hai loại vấn đề khác nhau. Conversion tổng quát hỏi một Java value biến thành Java type khác như thế nào. Formatting hỏi value đó nên xuất hiện dưới dạng text ra sao cho field và locale cụ thể, và text đó được parse ngược thế nào. Khi presentation là một phần của requirement, locale và field metadata trở thành context quan trọng.

**Purpose:**

Đặt Formatting là transformation có ngữ cảnh trình bày và phân biệt nó với type conversion không phụ thuộc presentation.

## Hợp đồng Printer và Parser

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:05–01:15`

**Visual:**

Tách mũi tên hai chiều thành một nhánh `Printer<T>` đi ra text và một nhánh `Parser<T>` đi từ text vào typed value.

**Script:**

Khi text là boundary, Spring tách hai chiều để mỗi contract nói rõ đúng một việc.

**Purpose:**

Nối bài toán Formatting sang hai contract theo từng hướng.

### Scene 1 — Hai chiều cùng nhận Locale

**Time:** `01:15–02:15`

**Visual:**

Hiển thị `Printer<BigDecimal>.print(value, locale)` tạo display text và `Parser<BigDecimal>.parse(text, locale)` tạo typed value. Chạy cùng một percentage với hai locale và cho thấy text khác nhau.

**Script:**

`Printer<T>` biến object đã có kiểu thành text để hiển thị. `Parser<T>` làm chiều ngược lại. Cả hai đều nhận `Locale`, và đó là khác biệt quan trọng so với plain converter. Parser nên báo lỗi khi text malformed để downstream binding còn giữ được evidence thật. Printer nên xác định theo value và locale được truyền vào, tránh phụ thuộc mutable request state ẩn bên trong formatter.

**Purpose:**

Giải thích contract hai chiều và lý do Locale là input chính thức của cả Printer lẫn Parser.

## Formatter kết hợp Parse và Print

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:15–02:25`

**Visual:**

Gộp hai thẻ Printer và Parser thành một thẻ `Formatter<T>`.

**Script:**

Phần lớn editable field cần cả hai chiều. Spring gộp chúng thành một textual contract thống nhất.

**Purpose:**

Chuyển từ hai contract riêng sang abstraction Formatter thường dùng.

### Scene 1 — Một policy text thống nhất

**Time:** `02:25–03:20`

**Visual:**

Hiển thị `LocalDateFormatter` có `parse` và `print` dùng cùng `DateTimeFormatter` pattern và locale. Animate text đi qua parse, thay đổi typed date, rồi print trở lại.

**Script:**

`Formatter<T>` kết hợp Printer và Parser cho cùng một object type. Nó phù hợp khi một presentation policy phải vừa hiển thị vừa nhận input text. Hai chiều nên tạo thành một contract dễ hiểu. Không phải mọi format đều round-trip tuyệt đối nếu display cố ý làm tròn hay rút gọn, nhưng bất đối xứng đó phải là quyết định rõ ràng chứ không phải lỗi vô tình.

**Purpose:**

Cho thấy Formatter là cặp parse/print cùng policy và làm rõ ý nghĩa của round-trip hợp lý.

## FormatterRegistry và đăng ký tập trung

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:30`

**Visual:**

Đưa nhiều formatter vào một registry trung tâm rồi nối nhiều consumer vào cùng registry đó.

**Script:**

Formatter chỉ trở thành application policy khi các consumer có thể tìm thấy nó qua một điểm đăng ký chung.

**Purpose:**

Nối implementation của formatter sang cấu hình tập trung.

### Scene 1 — Policy tập trung cần scope rõ ràng

**Time:** `03:30–04:25`

**Visual:**

Hiển thị `addFormatter`, `addFormatterForFieldType`, đăng ký Printer/Parser riêng và annotation formatter. Highlight một formatter `BigDecimal` quá rộng có thể tác động mọi field cùng type.

**Script:**

`FormatterRegistry` là mặt cấu hình của formatting system và đồng thời mở rộng `ConverterRegistry`. Ta có thể đăng ký formatter theo generic type, theo field type cụ thể, đăng ký Printer/Parser riêng, hoặc dùng annotation factory. Central registration giúp policy nhất quán, nhưng global rule phải thật sự là default toàn ứng dụng. Trường hợp ngoại lệ theo field nên được mô tả bằng metadata cụ thể hơn.

**Purpose:**

Cho người học hiểu các scope đăng ký và hệ quả khi biến một formatter thành policy toàn cục.

## Formatting theo Annotation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:25–04:35`

**Visual:**

Hiển thị hai field `LocalDate` cùng type nhưng mang hai annotation format khác nhau.

**Script:**

Đôi khi Java type giống nhau nhưng cách biểu diễn text khác nhau. Khi đó context cần nằm ngay trên field.

**Purpose:**

Nối type-wide registration sang formatting chọn theo metadata.

### Scene 1 — Để annotation chọn presentation policy

**Time:** `04:35–05:35`

**Visual:**

Reveal `@YearMonthText(pattern="MM/uuuu")` và `AnnotationFormatterFactory` khai báo field type hỗ trợ rồi trả về Printer/Parser dựa trên annotation instance.

**Script:**

`AnnotationFormatterFactory` cho phép annotation trên field quyết định cách format. Factory khai báo type nào được annotation hỗ trợ rồi tạo Printer và Parser từ giá trị annotation cụ thể. Cách này giải quyết trường hợp nhiều field cùng Java type nhưng cần text contract khác nhau. Annotation formatting nên mô tả presentation; không nên giấu business validation hoặc transport behavior vào trong đó.

**Purpose:**

Minh họa annotation-driven formatting và giữ ranh giới với validation/domain behavior.

## FormattingConversionService

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:35–05:45`

**Visual:**

Đưa converter và formatter vào cùng hộp `FormattingConversionService`, phía consumer chỉ còn một API `convert(...)`.

**Script:**

Ta đang có hai họ policy transformation. Spring kết hợp chúng để caller không phải dùng hai runtime API rời rạc.

**Purpose:**

Nối formatting registry trở lại runtime conversion model chung.

### Scene 1 — Một service runtime, hai họ policy

**Time:** `05:45–06:45`

**Visual:**

Hiển thị `FormattingConversionService` kế thừa generic conversion và implement `FormatterRegistry`. Animate formatter được adapt vào conversion system trong khi caller vẫn gọi `convert`.

**Script:**

`FormattingConversionService` là điểm Spring nối conversion và field formatting lại với nhau. Nó đăng ký được cả converter lẫn formatter nhưng vẫn phục vụ request qua API quen thuộc của `ConversionService`. `DefaultFormattingConversionService` cung cấp nhiều converter và formatter chuẩn cho number hay Java time, nhưng không thể tự biết format đặc thù của domain. Những policy đó vẫn cần application đăng ký rõ ràng.

**Purpose:**

Giải thích cách Spring dùng một runtime service thống nhất mà vẫn giữ semantics riêng của Conversion và Formatting.

## Ranh giới Formatting phụ thuộc Locale

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:45–06:55`

**Visual:**

Phóng to tham số `Locale`, rồi nối cả standalone caller và web framework vào cùng một formatter.

**Script:**

Formatter nhận locale nhưng không quyết định locale đến từ đâu. Boundary đó giúp ta tách formatting khỏi locale resolution và i18n rộng hơn.

**Purpose:**

Phân biệt locale-aware formatting với cơ chế resolve locale và message localization.

### Scene 1 — Locale làm thay đổi text, không làm thay đổi ownership

**Time:** `06:55–08:00`

**Visual:**

Hiển thị `NumberStyleFormatter` print cùng một giá trị với `Locale.US` và `Locale.GERMANY`. Bên cạnh, vẽ pipeline riêng nơi validation error code đi vào `MessageSource`. Thêm cảnh báo không dùng locale-formatted text làm machine serialization ổn định.

**Script:**

Formatting dùng `Locale` để parse và render text cho con người, nhưng formatter không sở hữu việc resolve locale. Caller độc lập có thể truyền locale trực tiếp; MVC hay WebFlux lấy locale từ lifecycle của chúng. Formatting cũng khác `MessageSource`: formatter biến typed value thành hoặc từ text, còn MessageSource biến message code thành thông điệp đã localize. Với dữ liệu machine-to-machine, nên dùng representation cố định và không phụ thuộc locale.

**Purpose:**

Chốt boundary giữa field formatting, locale resolution, message localization và serialization cho máy.
