# STEP 4A — WORKER ONLY: Định hướng học tập cho TOÀN BỘ một Menu Cấp 1

> **Chỉ dành cho Worker.** Tự đọc **TOÀN VĂN** file `/java-learning/module-generate-agent/worker/STEP_4A_WORKER.md` trước khi thực hiện và khi mất context. Không đọc STEP Prime, General, rules, TXT hay STEP khác. Đọc nội dung thực tế **trực tiếp từ đúng các ô Google Sheets được giao**; không dùng tóm tắt từ Prime hay lịch sử chat làm nguồn Knowledge.

## W1. Ownership: một chương, tất cả bài, chỉ ghi cột AC

Tin giao từ Prime gồm đúng **bốn dòng**: (1) link tab/gid; (2) tọa độ nguồn `A1/B1`, `A<PARENT>`, `B<CHILD_ROWS>`, `C:N` của các hàng con, `AC18/AC19`, `AC<PARENT>` và `AC<CHILD_ROWS>`, hai ô Special Note; (3) **chỉ `AC<CHILD_ROWS>` được ghi**; (4) đường dẫn tuyệt đối đến file này.

1. **Một lần giao = một Menu Cấp 1 hoàn chỉnh.** Phải xử lý mọi hàng Menu Cấp 2 do Prime liệt kê, không dừng ở bài đầu, không tự bỏ bài cuối hoặc tự nhận chương khác. Một chương có **một owner duy nhất**; giữ ownership đến khi đã QA/readback tất cả hàng con, báo Prime rồi mới SLEEPING.
2. Xác minh tên tab, `A1/B1`, đúng `A<PARENT>`, các tiêu đề bài `B<R>`, `AC18 = Learning Objectives`, `AC19 = Learning Objectives Markdown`. Đọc hai ô Special Note (nhãn đúng `Special Note`, giá trị ngay dưới cùng cột) và tuân thủ phần liên quan. Nếu sai vị trí/schema/gid, thiếu row hoặc chủ sở hữu khác: `BLOCKER: INVALID_SOURCE_OR_SCOPE`, không quét chọn bài khác.
3. Chỉ **đọc** `C<R>:N<R>` của từng bài để lập mục tiêu. Chỉ **ghi giá trị Markdown vào đúng `AC<R>`** từng hàng con thuộc chương. **`AC<PARENT>` là vị trí dành cho STEP 5 tạo liên kết toàn chương, chỉ đọc/bảo toàn; tuyệt đối không thêm nội dung STEP 4A vào đó.** Không sửa `C:N`, `A:B`, `O:AB`, header, `AD:...`, hàng/chương khác, tab khác, format/notes/validation, source/repo hay `J3`. Không chạy importer/Gradle, không commit/push, không spawn worker.

## W2. Đọc đầy đủ cả chương, không viết dựa trên tiêu đề

1. Với **từng bài**, đọc đủ 12 ô `C:N`, ghép theo thứ tự **C → N không separator**, đọc từ đầu tới cuối (định nghĩa, cơ chế, ví dụ Java, code/output, bảng, ngoại lệ, giới hạn, bài tập và tổng kết). Nếu Knowledge thiếu, đứt fragment hoặc không thể đọc đủ → `BLOCKER: INCOMPLETE_KNOWLEDGE`, không sửa Knowledge hộ STEP 4/5.
2. Đọc trọn **các bài trong cùng chương** trước khi chốt định hướng cho từng bài. Xác định *mỗi bài dạy chính điều gì, tại sao nó có ích cho OOP/module và khác bài sát bên ở năng lực nào*. Mỗi `AC<R>` vẫn là định hướng **riêng của bài R**, không ghi phần liên kết nhiều bài vào AC, không sao chép cùng một barem cho mọi hàng.
3. Đối chiếu Special Note. Nếu chỉ dẫn yêu cầu điều không được dạy trong body, hoặc chương có sai/thiếu nghiêm trọng đến mức không thể đặt mục tiêu trung thực, báo `CONTENT_SCOPE_LIMITATION` hoặc `BLOCKER: UNSUPPORTED_LEARNING_OBJECTIVES` với row tương ứng. Không thêm ví dụ hoặc sửa câu chữ Knowledge; **STEP 5 mới review/refactor C:N**.

## W3. Barem Markdown BẮT BUỘC cho MỖI bài

Tạo **một tài liệu Markdown hoàn chỉnh duy nhất trong một ô `AC<R>`**. **Chỉ cố định bốn mục 0 → 1 → 2 → 3, tên bài lấy nguyên văn từ `B<R>`, và bảng hai cột ở mục 3.** Phần giải thích mục đích, tên/số lượng nhóm câu hỏi, số câu hỏi và số năng lực **phải do nội dung thực tế quyết định**. Không biến ví dụ bài “Hàm khởi tạo và thiết lập trạng thái hợp lệ” thành khuôn cứng cho mọi bài.


**Mẫu minh họa về cách trình bày, không phải quota hay outline cố định:**

```markdown
# 0. Định hướng học tập

## 1. Mục đích của bài học

**<Tên chính xác của bài trong B<R>>**

<Diễn giải mục đích bằng số đoạn phù hợp: người học cần kiến thức để làm gì, giải quyết vấn đề nào trong OOP/module, và giá trị khi vận dụng. Những gợi ý này có thể được kết hợp, thay đổi thứ tự hoặc phát triển thêm.>

## 2. Những câu hỏi người học phải tự trả lời được

### <Nhóm về bản chất hoặc vấn đề cốt lõi phù hợp bài>

1. <Câu hỏi về lý do hoặc bản chất được dạy trong bài?>
2. <Câu hỏi về cơ chế, nguyên tắc hoặc hệ quả?>

### <Nhóm về ứng dụng, thiết kế hay kiểm chứng nếu phù hợp bài>

1. <Câu hỏi về cách sử dụng hoặc lựa chọn giải pháp?>
2. <Câu hỏi về lỗi, giới hạn hoặc tình huống thực tế?>

## 3. Sau khi học xong, người học phải đạt được điều gì?

| Năng lực | Kết quả mong đợi |
| --- | --- |
| <Năng lực thực sự được bài dạy> | <Hành động có thể đánh giá trong tình huống cụ thể> |
| <Năng lực khác nếu có> | <Kết quả mong đợi đúng nội dung bài> |
```

### W3.1. Tiêu chí chất lượng từng phần

- **Mục 0:** đúng tiêu đề `# 0. Định hướng học tập`; không thêm prose chung chung ở mục 0. Markdown trong **ô AC độc lập**, không chèn vào body `C:N`.
- **Mục 1:** đúng heading `## 1. Mục đích của bài học` và ngay sau là **tên bài in đậm, khớp chính xác `B<R>`**. Phần còn lại **không cố định số đoạn hoặc mô hình lập luận**; viết ngắn gọn nhưng đủ chiều sâu về lý do học, vấn đề giải quyết và giá trị thực tế trong OOP/module. Các hướng “vấn đề → cơ chế → lợi ích” chỉ là ví dụ; có thể diễn đạt theo cách khác phù hợp bài.
- **Mục 2:** đúng heading `## 2. Những câu hỏi người học phải tự trả lời được`. **Phải gom câu hỏi theo một vài nhóm kiến thức có ý nghĩa**, nhưng **không bắt buộc ba nhóm A/B/C, không yêu cầu số câu/nhóm hoặc tổng chín câu**. Tự chọn số nhóm và tên nhóm dựa trên mạch bài: bản chất, cơ chế, ứng dụng, quyết định thiết kế, giới hạn, lỗi thường gặp... chỉ khi có nội dung tương ứng. Tránh nhóm rỗng, nhóm lặp hoặc chia nhóm chỉ để giống ví dụ. Dùng heading `###` có tên mô tả thực; bên trong là danh sách đánh số Markdown (có thể bắt đầu lại từ 1 ở mỗi nhóm), câu hỏi mở có dấu hỏi, đòi giải thích/vận dụng/dự đoán và **có thể trả lời từ chính bài**. **Không ghi đáp án sẵn.**
- **Mục 3:** đúng heading `## 3. Sau khi học xong, người học phải đạt được điều gì?`; **bắt buộc bảng Markdown** với header chính xác `| Năng lực | Kết quả mong đợi |` và hai cột. **Số hàng tùy năng lực thực được giảng dạy**, không ép 5–7 hàng. Mỗi hàng là *năng lực cụ thể → hành động kiểm chứng được* (xác định, giải thích có căn cứ, mô phỏng, phân tích, lựa chọn, thiết kế, thực hiện, phát hiện/sửa lỗi...) trong tình huống có thực.
- **Không bịa để đủ số:** không áp quota số đoạn, nhóm, câu hỏi hoặc số hàng bảng. Chỉ cố định mục 0/1/2/3, tên bài, cách trình bày nhóm ở mục 2 và bảng mục 3. Nội dung nguồn quá hạn chế → `CONTENT_SCOPE_LIMITATION`; không đủ căn cứ để định hướng trung thực → blocker.
- **Markdown chuẩn:** heading, **bold**, đoạn văn cách dòng rỗng, numbered list rõ trong mỗi nhóm và **bảng có `|` cân đối** (escape `|` trong ô). Không để lại placeholder `<...>`, HTML, YAML, JSON, fence bao trọn AC, TODO, mô tả công việc của worker hoặc thông báo nội bộ. Giữ ngôn ngữ bài và thuật ngữ Java/OOP chính xác.

### W3.2. Không nhân đôi và không ghi đè dữ liệu cũ

- Nếu `AC<R>` **trống sạch**: viết mới `MODE=NEW_FILL`.
- Nếu đã có một tài liệu **đúng bốn mục 0/1/2/3, tên bài, nhóm câu hỏi hợp lý và bảng hai cột**, đồng thời vẫn được Knowledge C:N hiện tại hỗ trợ: `MODE=UNCHANGED`, `WRITE=NOT_RUN`. **Không đánh giá guide sai chỉ vì khác số đoạn, nhóm, câu hỏi hoặc năng lực của mẫu minh họa.** Không rewrite vì muốn câu chữ đẹp hơn.
- Nếu AC đã có bất kỳ giá trị khác (kể cả fragment, marker cũ, thiếu mục/nhóm/bảng, formula/chip, dữ liệu một mục đích khác, guide lỗi thời): `BLOCKER: EXISTING_TARGET_CONTENT` hoặc `GUIDE_STALE_OR_UNSUPPORTED`; không sửa hay xóa mà chưa được cấp quyền riêng. Một số bài có thể `UNCHANGED` trong khi bài còn lại trong chương vẫn `NEW_FILL`; worker phải kiểm kê đầy đủ.

## W4. Snapshot, ghi đúng một ô/bài và exact readback

1. Snapshot tất cả `A1/B1`, `A<PARENT>`, `B<CHILD_ROWS>`, Special Note, `C:N` và `AC` của mỗi row (giá trị entered/effective/formatted, formula/chipRuns, notes/validation), cùng trạng thái ownership. Nếu header AC18/AC19 không đúng chuẩn hoặc ô AC có dữ liệu không hợp lệ → báo blocker, không tự sửa schema.
2. **Soạn từng Markdown đầy đủ trước khi ghi;** kiểm tra mục 0/1/2/3, tên bài, **các nhóm do nội dung thực tế hình thành**, danh sách câu hỏi Markdown và bảng đúng hai cột. Đối chiếu mỗi câu hỏi và mỗi năng lực với phần bài thực sự giảng dạy. Một ô `AC<R>` **tối đa 30.000 ký tự**; không chia sang `AD`, không cắt ngắn tùy tiện hoặc bỏ một ý cần thiết để vừa.
3. **Preflight lại ngay trước WRITE** trên mọi ô nguồn/đích dự kiến: AC vẫn trống; tên hàng con, C:N, Special Note, ownership không bị thay đổi bởi phiên khác. Nếu thay đổi → `BLOCKER: CONCURRENT_CHANGE`, không ghi đè.
4. Chỉ ghi **`userEnteredValue` của ô AC chính xác trên những hàng con còn trống**, tránh batch theo cả row hoặc phạm vi liên tục chứa hàng không được cấp. Giữ nguyên style, notes, validations, formula và các ô khác. Không tự thay đổi chiều cao/định dạng của Sheet.
5. **Đọc lại tất cả AC của mọi hàng con**, gồm cả `NEW_FILL` và `UNCHANGED`; so với bản Markdown gốc mới soạn theo từng hàng 100% Unicode/newline, xác minh barem và dữ liệu của đúng bài, C:N/tiêu đề/ô cha/ngoài scope không bị sửa. Nếu công cụ lỗi hoặc ghi một phần, nêu chính xác rows còn thiếu/sai; **không báo COMPLETED chương**, không retry ghi đè mù.
6. Chapter QA: **100% bài đã đọc đầy đủ, 100% AC hợp lệ, không bỏ sót bài phía dưới**, các mục đích khác biệt theo từng bài, năng lực khớp nội dung. Không “review lại bài cũ” bằng cách sửa C:N; STEP 4A chỉ đọc body để viết mục tiêu.

## W5. Báo cáo chương và trở về SLEEPING

Báo Prime **một báo cáo cho toàn bộ Menu Cấp 1**:

- `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET=<tab,gid>`, `CHAPTER=A<PARENT>`, `CHILD_ROWS=<row list>`, `TOTAL_LESSONS`, `READ_COVERAGE=<full/total>`.
- `RESULTS` theo **từng row**: `B<R>`, `AC<R>`, `MODE=NEW_FILL/UNCHANGED/BLOCKER`, `QUESTION_GROUPS=<tên các nhóm thực tế>`, `QUESTION_COUNT`, `OUTCOMES_COUNT`, `MARKDOWN_QA`, `READBACK`; `CONTENT_SCOPE_LIMITATION` nếu có.
- `PREFLIGHT` (header AC, source/target ownership), `PRESERVED_C:N=PASS/FAIL`, `PRESERVED_OTHER_CELLS`, `CHAPTER_QA`, `BLOCKERS` theo tọa độ cụ thể. Nếu bất kỳ bài chưa hoàn thành hoặc chưa đọc hết, `STATUS` **không thể là `COMPLETED`**.

Giải phóng toàn bộ chapter AC ownership và chuyển `SLEEPING`. Không tự nhận chương khác, không cập nhật J3, không chạy STEP 5 hoặc viết khung liên kết kiến thức.
