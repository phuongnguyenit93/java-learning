---
video:
  url: ""
---

# Chuyển đổi kiểu trong Spring

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

## ConversionService là điểm vào khi chạy Conversion

<!-- VIDEO_SECTION -->

### Scene 1 — Yêu cầu một conversion thay vì tự chọn converter

**Time:** `00:00–01:00`

**Visual:**

Hiển thị caller gọi `canConvert(String.class, Integer.class)` và `convert("42", Integer.class)`. Sau hộp `ConversionService`, animate nhiều converter có thể được chọn trong khi caller không đổi.

**Script:**

`ConversionService` là điểm vào dành cho bên sử dụng hệ thống conversion. Caller hỏi có đường chuyển đổi hay không rồi yêu cầu giá trị đích; caller không cần biết converter cụ thể nào thực thi. Nhờ vậy code binding có thể phụ thuộc vào một service ổn định trong khi registry phía sau vẫn thay đổi. Cũng cần nhớ `canConvert` chỉ cho biết có conversion path về mặt cấu trúc, không đảm bảo mọi giá trị runtime hay mọi phần tử collection đều chuyển thành công.

**Purpose:**

Đặt ConversionService làm abstraction runtime ổn định và phân biệt capability query với thành công trên giá trị cụ thể.

## ConverterRegistry và đăng ký Converter

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:10`

**Visual:**

Lật hộp ConversionService từ mặt “consume” sang mặt “configure” có nhãn `ConverterRegistry`.

**Script:**

Nếu `ConversionService` là nơi dùng conversion, ta cần một nơi riêng để cấu hình những strategy nào được phép tồn tại.

**Purpose:**

Nối runtime consumption sang configuration của conversion system.

### Scene 1 — Cấu hình một lần, nhiều nơi cùng dùng

**Time:** `01:10–02:05`

**Visual:**

Hiển thị `DefaultConversionService` nhận `addConverter`, `addConverterFactory` và conditional generic converter. Đặt warning quanh một registration source/target quá rộng.

**Script:**

`ConverterRegistry` sở hữu việc cấu hình. Nó nhận converter đơn, converter factory và generic converter. Central registration giúp toàn bộ consumer của cùng service nhìn thấy một policy thống nhất, nhưng registration càng rộng thì ảnh hưởng càng rộng. Nên ưu tiên cặp source/target hẹp hoặc conditional matching rõ ràng, thay vì vô tình biến thứ tự đăng ký thành business rule.

**Purpose:**

Làm rõ separation giữa cấu hình và sử dụng, đồng thời cho thấy hệ quả của converter registration toàn cục.

## Converter cho một cặp kiểu nguồn và đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:05–02:15`

**Visual:**

Thu các lựa chọn registry về contract nhỏ nhất: `Converter<S,T>`.

**Script:**

Với trường hợp phổ biến nhất, ta không cần SPI linh hoạt nhất. Một transformation ổn định giữa hai kiểu có contract đơn giản hơn.

**Purpose:**

Chuyển từ các loại registration sang conversion SPI hẹp nhất.

### Scene 1 — Một cặp kiểu, một rule xác định

**Time:** `02:15–03:10`

**Visual:**

Reveal `StringToOrderIdConverter` trim text, parse số dương rồi tạo `OrderId`. Highlight “source non-null”, “thread-safe/shareable” và “IllegalArgumentException khi input non-null không hợp lệ”.

**Script:**

Dùng `Converter<S,T>` khi một source type có một cách chuyển rõ ràng sang một target type. Spring truyền source non-null vào contract này, và converter đã đăng ký nên được thiết kế để share và thread-safe. Đừng lưu state theo từng lần convert trong field của instance. Khi giá trị non-null không đáp ứng contract, hãy fail rõ ràng thay vì tự bịa giá trị mặc định. Nếu rule cần annotation, generic element hay target subtype thì nên dùng SPI khác phù hợp hơn.

**Purpose:**

Giải thích contract và các ràng buộc thiết kế của Converter đơn giản.

## ConverterFactory cho một họ kiểu đích

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:20`

**Visual:**

Cho một source `String` tỏa ra `Priority`, `Status` và nhiều enum khác dưới cùng một ngoặc `Enum`.

**Script:**

Có lúc thuật toán không đổi nhưng target subtype thay đổi. Khi đó ta đang xử lý một họ kiểu, không phải nhiều converter rời rạc.

**Purpose:**

Nối từ một type pair sang target family có chung policy.

### Scene 1 — Một source format cho cả target family

**Time:** `03:20–04:15`

**Visual:**

Hiển thị `StringToEnumFactory` nhận `Priority.class` rồi tạo converter `String -> Priority`, sau đó lặp lại với `Status.class`. Giữ `Enum` làm upper bound.

**Script:**

`ConverterFactory<S,R>` phù hợp khi cùng một source representation có thể chuyển sang nhiều subtype cùng họ. Factory nhận target class được yêu cầu rồi trả về converter tương ứng. Enum là ví dụ dễ thấy vì thuật toán thường giống nhau. Nếu quyết định còn phụ thuộc annotation, generic hay metadata khác của field, `GenericConverter` có điều kiện sẽ diễn tả đúng hơn.

**Purpose:**

Cho tiêu chí chọn ConverterFactory thay vì tạo hàng loạt Converter hoặc dùng GenericConverter quá rộng.

## GenericConverter và Conditional Conversion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:25`

**Visual:**

Thay raw class bằng hai thẻ source/target descriptor có annotation và generic metadata.

**Script:**

Target subtype vẫn chưa đủ trong mọi trường hợp. Có conversion phải nhìn cả metadata của vị trí source và target.

**Purpose:**

Chuyển từ class-only matching sang descriptor-aware conversion.

### Scene 1 — Chỉ dùng descriptor context khi rule thật sự cần

**Time:** `04:25–05:25`

**Visual:**

Hiển thị `ConditionalGenericConverter`: trước tiên khai báo candidate pair, sau đó `matches(sourceType, targetType)` kiểm tra annotation `@EntityRef`, cuối cùng mới chạy `convert`. Highlight rằng generic converter có thể nhận source null.

**Script:**

`GenericConverter` nhận `TypeDescriptor` của cả source và target nên có thể xử lý nhiều pair và dùng metadata phong phú hơn raw class. `ConditionalGenericConverter` thêm bước `matches` để quyết định converter có thực sự phù hợp với descriptor hiện tại hay không. Cơ chế này rất hữu ích cho annotation-aware hoặc generic-aware conversion, nhưng cũng khó review hơn nếu scope quá rộng. Chỉ dùng nó khi context thực sự là một phần của semantics.

**Purpose:**

Giải thích lý do tồn tại của Generic/Conditional Converter và tránh dùng API mạnh hơn mức cần thiết.

## TypeDescriptor và ngữ cảnh Conversion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:35`

**Visual:**

Phóng to thẻ `TypeDescriptor` rồi mở các lớp metadata: annotation, collection element, map key/value và property location.

**Script:**

Conditional converter vừa rồi làm được việc đó vì Spring giữ nhiều thông tin hơn một `Class<?> ` đơn thuần.

**Purpose:**

Giới thiệu object metadata giúp conversion hiểu context.

### Scene 1 — Raw type đôi khi chưa đủ

**Time:** `05:35–06:35`

**Visual:**

So sánh hai field `BigDecimal` mang annotation tiền tệ khác nhau, rồi so sánh `List<Integer>` và `List<UUID>`. Highlight rằng raw class giống nhau nhưng descriptor khác.

**Script:**

Raw class chỉ cho ta kiểu runtime cơ bản. Nó không nói hai field `BigDecimal` có annotation khác nhau, cũng không phân biệt element type của hai `List` sau type erasure. `TypeDescriptor` giữ location, annotation và nested type như element, map key hoặc map value. Với scalar conversion đơn giản, class-based API là đủ. Khi annotation, generic hay property location thay đổi semantics, descriptor mới là context cần thiết.

**Purpose:**

Cho thấy TypeDescriptor bổ sung thông tin gì và khi nào thông tin đó ảnh hưởng conversion.

## Chọn Converter và mô hình lỗi Conversion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:35–06:45`

**Visual:**

Biến descriptor flow thành hai bước: “tìm conversion path phù hợp” rồi “convert giá trị thực tế”.

**Script:**

Sau khi có đủ metadata để chọn strategy, thành công vẫn cần hai điều: phải tìm được converter và converter đó phải xử lý được giá trị cụ thể.

**Purpose:**

Nối converter selection sang failure model của conversion runtime.

### Scene 1 — Có capability chưa chắc mọi value đều thành công

**Time:** `06:45–07:45`

**Visual:**

Nhánh A không có converter và dừng ở “no strategy”. Nhánh B chọn được UUID converter nhưng text malformed gây `ConversionException`. Thêm một collection có một phần tử hỏng dù `canConvert` trả true.

**Script:**

Conversion có thể fail vì hệ thống không có strategy cho source/target, hoặc vì strategy đã chọn không xử lý được giá trị thực tế. Vì vậy `canConvert` chỉ nên được hiểu là capability query, không phải lời hứa rằng mọi value sau đó chắc chắn chạy qua. Converter cũng nên giữ rõ sự khác nhau giữa không có input, input sai và một domain value hợp lệ, thay vì nuốt lỗi rồi trả default tùy ý.

**Purpose:**

Phân biệt failure khi tìm converter với failure khi convert value và giải thích giới hạn thực tế của canConvert.

## Ranh giới với PropertyEditor legacy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:45–07:55`

**Visual:**

Fade sơ đồ conversion hiện đại sang thẻ JavaBeans `PropertyEditor`, đồng thời giữ `DataBinder` nối được với cả hai.

**Script:**

ConversionService là hướng hiện đại, nhưng Spring vẫn giữ `PropertyEditor` vì lịch sử và compatibility của binding API.

**Purpose:**

Đặt PropertyEditor vào đúng bối cảnh legacy mà không trộn nó với Converter hiện đại.

### Scene 1 — State của editor cần scope phù hợp

**Time:** `07:55–08:55`

**Visual:**

So sánh `Converter` stateless dùng chung với `PropertyEditor` có mutable current value. Hiển thị `DataBinder.registerCustomEditor(...)` rồi hướng mũi tên khuyến nghị sang `ConversionService` cho policy mới.

**Script:**

`PropertyEditor` xuất hiện trước `ConversionService` và vẫn được DataBinder hỗ trợ. Điểm khác quan trọng là state: editor truyền thống giữ mutable state, còn Spring Converter được thiết kế như strategy có thể chia sẻ. Với conversion policy mới của ứng dụng, ưu tiên ConversionService cùng converter và formatter. Dùng custom editor khi cần tích hợp code legacy thật sự phụ thuộc vào model đó, và tránh định nghĩa hai policy mâu thuẫn cho cùng một concept.

**Purpose:**

Giải thích boundary legacy và tránh giả định PropertyEditor mutable có thể được dùng như Converter stateless toàn cục.
