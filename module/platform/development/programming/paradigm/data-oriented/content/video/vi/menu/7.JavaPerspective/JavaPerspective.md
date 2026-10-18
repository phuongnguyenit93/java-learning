---
video:
  url: ""
---

# Góc nhìn lập trình hướng dữ liệu trong Java

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

## DOP trong Java: Hướng tiếp cận liên quan nhưng khác biệt

<!-- VIDEO_SECTION -->
### Scene 1 — Java cũng nói DOP nhưng đang hỏi câu khác

**Time:** `00:00–01:29`

**Visual:**

Đặt bảng Sharvit map/list bên trái và cột typed Java ở phải, giữa là dữ liệu A.

**Script:**

Java cũng dùng cụm Data-Oriented Programming, nhưng không hoàn toàn theo bộ bốn nguyên tắc của Sharvit. Sharvit thích biểu diễn chung bằng map và list, biến đổi không sửa gốc và schema đặt riêng. Project Amber trong Java lại chú ý đến dữ liệu có kiểu, minh bạch, bất biến và những biến thể hợp lệ mà trình biên dịch có thể hỗ trợ kiểm tra. Hai hướng cùng xem dữ liệu là trung tâm, nhưng phương pháp biểu diễn không giống nhau. Ta sẽ so sánh ở mức tư duy, chưa học cú pháp record, sealed hay pattern matching chi tiết.

**Purpose:**

Đặt cạnh nhau hai mô hình DOP có quan hệ nhưng không đồng nhất, tránh bịa code Java runtime.

## Mô hình dữ liệu minh bạch và bất biến

<!-- VIDEO_SECTION -->
### Transition

**Time:** `01:29–01:42`

**Visual:**

Từ map kéo các trường sang hình record OrderData, không viết code biên dịch theo phiên bản.

**Script:**

Map A đã nhìn thấy; nếu dùng kiểu Java thì tính minh bạch đến từ đâu?

**Purpose:**

Đưa khái niệm transparency/immutability vào kiểu dữ liệu.

### Scene 2 — Một record mô tả dữ liệu rõ ràng

**Time:** `01:42–03:11`

**Visual:**

So sánh map `id/status/lines` với hình record có ba trường; đặt icon kính lúp bên các trường và icon ổ khóa bên các giá trị bất biến sâu cần được đảm bảo.

**Script:**

Một record Java có thể làm các thành phần dữ liệu hiển thị rõ trong kiểu, giúp công cụ kiểm tra và người đọc biết điều gì được truyền. Nhưng record không tự bảo đảm mọi đối tượng bên trong là bất biến sâu, nếu một danh sách có thể bị sửa. Vì vậy nên mô tả chính xác bản ghi chứa những gì và liệu các giá trị lồng nhau có ổn định hay không. Điểm mạnh ở đây là thông tin về hình dạng xuất hiện trong kiểu dữ liệu, thay vì chỉ trong schema chạy.

**Purpose:**

Giải thích lợi ích Java typed transparency và lưu ý nested mutability.

## Mô hình dữ liệu đầy đủ và loại trừ trạng thái bất hợp lệ

<!-- VIDEO_SECTION -->
### Transition

**Time:** `03:11–03:24`

**Visual:**

Đưa thẻ trạng thái pending và paid vào hai nhánh biến thể tách nhau.

**Script:**

Một kiểu rõ trường còn có thể mô hình những trường hợp hợp lệ như thế nào?

**Purpose:**

Mô tả complete/no-more/no-less và loại bỏ trạng thái bất hợp lệ.

### Scene 3 — Chỉ biểu diễn lựa chọn hợp lệ

**Time:** `03:24–04:53`

**Visual:**

Sơ đồ `PaymentState = Pending | Paid(receipt)`; thẻ `Paid` không thể thiếu receipt trong mô hình ý tưởng. Đặt cạnh map `{status:paid,receipt:null}` gạch đỏ.

**Script:**

Trong một thiết kế Java có kiểu, ta có thể tạo các biến thể khác nhau cho trạng thái chờ và trạng thái đã thanh toán kèm biên nhận. Khi kiểu được thiết kế hợp lý, trường hợp đã trả tiền nhưng không có bằng chứng có thể bị loại khỏi tập giá trị biểu diễn được. Đây là ý nghĩa make illegal states unrepresentable. Nhưng phải mô hình đúng nghiệp vụ: chỉ khai báo sealed không tự bảo vệ mọi quy tắc hay hiệu ứng ngoài đời. Bài này nói về ý tưởng xây kiểu, không yêu cầu cú pháp cụ thể.

**Purpose:**

Gắn sealed variants với invariant nghiệp vụ thay vì giới thiệu keyword rời rạc.

## Tách thao tác khỏi dữ liệu và xử lý các biến thể theo mẫu

<!-- VIDEO_SECTION -->
### Transition

**Time:** `04:53–05:06`

**Visual:**

Đặt hai nhánh Pending/Paid vào khối xử lý bên ngoài kiểu, đánh dấu pattern cases.

**Script:**

Khi đã có các biến thể, phép xử lý cần nhìn chúng bằng cách nào?

**Purpose:**

Giữ code-data separation dù dữ liệu được typed.

### Scene 4 — Một phép xử lý cho từng biến thể

**Time:** `05:06–06:35`

**Visual:**

Sơ đồ PaymentState→`describe(state)`; hai nhánh `Pending → chờ`, `Paid(receipt) → có biên nhận`; đặt dấu cảnh báo nếu thiếu nhánh.

**Script:**

Một phép xử lý độc lập có thể nhận giá trị thuộc một trong các biến thể và chọn cách xử lý theo cấu trúc thực sự của nó. Khi các nhánh dữ liệu được mô hình đầy đủ, ta có thể xem đã xét hết trường hợp hay chưa. Đây là mối liên hệ giữa pattern matching và dữ liệu minh bạch. Nó vẫn không có nghĩa phải gom mọi xử lý vào một switch khổng lồ; phải chia theo trách nhiệm rõ. Luồng tính giá Sharvit vừa qua chỉ cần map và schema, không cần giả triển khai mã Java.

**Purpose:**

Minh họa case handling đúng ý tưởng với một tập biến thể đóng.

## So sánh mô hình dữ liệu có kiểu Java với dữ liệu phổ dụng theo Sharvit

<!-- VIDEO_SECTION -->
### Transition

**Time:** `06:35–06:48`

**Visual:**

Tách schema runtime của HTTP và typed Java model thành hai cột với dữ liệu A ở giữa.

**Script:**

Hai mô hình đều có giá trị; quyết định nên dựa trên điều gì?

**Purpose:**

Chốt đánh đổi generic flexibility và compile-time guarantees.

### Scene 5 — Schema runtime đối diện kiểm tra kiểu

**Time:** `06:48–08:17`

**Visual:**

So sánh POST JSON từ bên ngoài qua runtime schema trả 200/422 với mô hình Java typed báo sai cấu trúc khi build; nối giữa chúng bằng bước deserialize/validate.

**Script:**

Dữ liệu đi qua HTTP vẫn có thể sai dù phía server dùng kiểu Java, vì client bên ngoài không phải trình biên dịch của ta. Mô hình typed hữu ích để kiểm tra nhiều tình huống trước khi chạy; map linh hoạt và schema riêng dễ diễn đạt những shape chưa cố định và lỗi theo đường dẫn. Ta có thể phối hợp hai cách ở các ranh giới khác nhau. Không nên kết luận Java DOP là bản dịch cú pháp của Sharvit hay map luôn tốt hơn record. Hãy đánh giá dữ liệu, mức thay đổi và trách nhiệm của mỗi lớp.

**Purpose:**

Phân biệt đúng runtime validation của API và typed modeling mà không nhập nhằng hai học thuyết.
