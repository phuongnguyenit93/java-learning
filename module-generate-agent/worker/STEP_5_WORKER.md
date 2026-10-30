# STEP 5 — WORKER ONLY: Chapter Review, Difficulty và Summary

**STEP status:** This worker must never read or update progress in Google Sheets/Excel or `master.json`. Prime alone records `CURRENT_STEP.VALUE` after whole-STEP QA.


> **CURRENT Summary contract:** Worker fills B filename and C base ID at parent, AC Markdown at children and authored-only YAML at AC parent. Prime fills A child IDs after QA. Summary importer joins AC entries to child A/B by index, materializes full section.yml with derived id/order/title/to. AC parent never contains those technical fields.

> Worker phải đọc toàn văn file này. Prime đã setup SERVLET, bật BUILD_SWAGGER, sync Gradle và provision AC18/19. Handoff bốn dòng gồm tab URL, READ A/B/C cha + B/C:N/O/AC con + AC cha/header/Special Note, WRITE B/C cha + C:N/O/AC con + AC cha, và file worker-only. Không dùng lịch sử chat hoặc tài liệu STEP khác thay contract.

> **STEP 5 ID and AC contract (current):** Worker writes the chapter base ID to C parent (derived from B parent filename: OOPBasic.md -> oop-basic), on top of all existing chapter work. Prime fills A child IDs by code after all workers and filename QA. AC parent must contain authored-only YAML: each roadmap item has only description and transition (description or final null); no id/order/title/to. The Summary importer materializes the full YAML using the A/B child rows. No AI in importers.

## W1. Một chương, một lượt, một owner

1. Một lượt xử lý toàn bộ Menu Cấp 1: đọc trọn chương → lập bản đồ → review/refactor Knowledge → QA nội dung → gán Difficulty → ghi filename B cha/base ID C cha → tạo Summary AC con và authored-only YAML AC cha → snapshot/preflight → ghi đúng ownership → exact readback → chapter QA → báo cáo → SLEEPING. Lượt sửa trùng filename chỉ ghi B/C cha trước Prime fill A-child IDs.
2. Chỉ đọc A1, B1, A/B/C cha, B con, C:N/O/AC hàng con, AC cha, AC18/19 và Special Note đúng chương Prime giao.
3. Worker writes B/C parent, C:N/O/AC child and authored-only AC-parent YAML; Prime alone fills A-child IDs. Preserve formats, limits, other modules and progress.
4. Kiểm tra module setup và quyền sở hữu Sheet; thiếu Knowledge, dữ liệu sai hay ownership xung đột → BLOCKER. Không spawn, chạy STEP khác hoặc commit/push.


## W2. Đọc trọn chương, đánh giá và refactor

### W2.1. Preflight: đọc đầy đủ, không vội viết

1. Đọc toàn văn file worker; xác minh URL/gid và READ/WRITE, `O19=Difficulty`, `AC18=Summary`, `AC19=Summary YAML` hoặc cặp legacy đã được Prime xác minh. Schema khác → BLOCKER: STEP_5_AC_SCHEMA_CONFLICT.
2. **Đọc TOÀN BỘ bài học của TẤT CẢ mục cấp 2** trong chương được giao, không review chỉ từ tiêu đề, độ dài, vài đoạn đầu/cuối. Nếu nhiều nội dung, đọc theo các lượt/chunk liên tiếp; nối chính xác C:N của từng bài. Có thể đọc lại để giữ context khi chương dài. Không tuyên bố review đầy đủ nếu chưa đọc đủ từng bài.
3. **Lập bản đồ nội dung nội bộ trước khi sửa:** mục cấp 2 nào là nơi dạy chính của từng khái niệm, ví dụ, thao tác, giới hạn và bài tập; mục nào chỉ cần nhắc lại vắn tắt để dẫn sang kiến thức khác; đâu là nội dung được yêu cầu riêng bởi từng tiêu đề. Không đảo thứ tự Menu, không chuyển toàn bộ trách nhiệm một bài sang bài khác.
4. Nếu thiếu câu trả lời `C<R>`, có dữ liệu bị đứt đoạn/đè lên ngoài C:N, sai chương, nội dung có dấu hiệu chưa tải hết, hoặc không đọc đủ, **báo blocker** và không tự tạo bài mới để lấp chỗ trống. Không sửa chương một phần rồi báo `COMPLETED` toàn chương.

### W2.2. Đánh giá chất lượng và trùng lặp theo nội dung

**Mục tiêu trung tâm: giảm trùng lặp không cần thiết, giữ chiều sâu và tính tự học của từng bài**. Trùng vài thuật ngữ, định nghĩa ngắn hoặc ví dụ xuyên suốt **không tự động là lỗi**. Xác định bằng chứng theo từng cặp mục, mức độ lặp, và ảnh hưởng đến người đọc:

- **Trùng lặp cần refactor:** nhiều bài đều mở đầu bằng **cùng một tình huống giải thích dài**; lặp nguyên một chuỗi khái niệm, bảng, code, sơ đồ, ví dụ thành công/thất bại, hoặc cùng một kết luận mà không có góc nhìn/năng lực mới; lặp lời giới thiệu AOP/Aspect/cross-cutting quá dài ở các bài sau; đoạn chuyển ý/boilerplate “Hãy tưởng tượng...” lặp liên tục.
- **Lặp có ích nên giữ:** định nghĩa/cảnh báo ngắn để bài đọc độc lập không khó hiểu; cùng một case study được **tiếp nối** nhưng mỗi bài khai thác câu hỏi, tình huống, góc nhìn, kết quả, phép thử khác nhau; mã nguồn nhỏ bắt buộc để ví dụ của bài chạy được và tự kiểm chứng.
- **Chất lượng bắt buộc song song:** mỗi bài trả lời đúng và đủ tiêu đề Menu Cấp 2; dễ tiếp cận từ người mới, giải thích *vì sao/khi nào*, ranh giới với khái niệm kế bên, ví dụ có diễn biến rõ từ đầu vào → xử lý → kết quả; không mâu thuẫn kỹ thuật giữa các bài, không quá nhiều thuật ngữ chưa định nghĩa; không bỏ các điều kiện, ngoại lệ, hạn chế đáng học chỉ để rút ngắn.
- **Độ chính xác:** soát đặc biệt hành vi Java/Spring/Spring AOP/AspectJ, phạm vi proxy và invocation, thứ tự advice, exception, transaction, bảo mật, output code/ví dụ. Không phát minh hành vi API, không đưa khẳng định tuyệt đối khi thực tế có điều kiện.
- **Hình thức README:** Markdown/heading/code fence cân đối, code block đúng ngôn ngữ, bảng không gãy, Mermaid hợp lệ, ví dụ Java hợp lý, kết quả khớp code; **trình bày đa dạng khi hữu ích**, không biến cả chương thành text thuần hoặc rập khuôn mọi mục đều có cùng số heading/bảng/sơ đồ.
- **Tính nhất quán cả chương:** thuật ngữ, ví dụ, ký hiệu, dữ liệu số và cách gọi tên không xung đột; câu chuyển mạch từ bài trước sang bài sau tự nhiên, tránh mỗi bài phải “dạy lại từ đầu”; phần tổng kết phải đóng góp điều mới hoặc củng cố trọng tâm riêng.

**Không đặt mục tiêu phần trăm/tổng số ký tự bắt buộc phải giảm**. Không rút gọn hàng loạt theo số từ, không xóa bảng/code/sơ đồ chỉ vì bài dài. Đánh giá **giá trị học tập**, không chỉ tỷ lệ trùng văn bản. Ghi nhận rõ nơi lặp được giữ vì có chức năng và nơi lặp được sửa vì gây nhiễu hoặc mất nhịp học.

### W2.3. Refactor có chủ đích

1. Sau khi đã đọc **toàn bộ chương**, tự đề xuất những sửa đổi có căn cứ, giữ một **nơi giải thích chính (primary teaching home)** cho mỗi ý quan trọng nhưng các bài khác được phép nhắc lại ngắn hoặc áp dụng ở ngữ cảnh mới. Không chỉ xóa câu chữ: nếu chuyển/bỏ nội dung phải bảo toàn khái niệm và khả năng hiểu bài.
2. **Mỗi bài sau refactor phải đứng được một mình ở phạm vi hợp lý** và đồng thời đóng góp cho chương; không yêu cầu người đọc phải mò sang bài khác mới hiểu câu hỏi chính. Với phần lặp, có thể rút về 1–2 câu định vị rồi đi sâu vào nội dung riêng, dùng ví dụ mới, bảng phân biệt, thao tác, code trace hoặc tình huống kiểm tra phù hợp.
3. Tránh refactor biến mọi bài thành cùng một template WHAT/WHY/HOW; độ dài tùy trọng tâm. Giữ hoặc cải thiện ví dụ chạy được, giải thích causal flow, tính chính xác và Markdown nguyên vẹn. Nếu bài hiện tốt, **giữ nguyên**, không đổi chỉ để có thay đổi.
4. Giữ nguyên hàng, A cha, B con; chỉ refactor C:N, gán Difficulty O, soạn AC cha/con và điền B/C cha trong ownership. Không sửa Java source/config, module khác, không ghi file tạm hoặc commit/push.

### W2.4. Phân loại Difficulty cho TỪNG bài sau review

1. **Bắt buộc chọn đúng một trong ba chuỗi nguyên văn**: `BASIC`, `INTERMEDIATE`, `ADVANCED`. `BASIC`: kiến thức nền để bắt đầu, thuật ngữ/cơ chế cơ bản. `INTERMEDIATE`: ứng dụng, kết hợp nhiều khái niệm, so sánh và đánh đổi thực tế. `ADVANCED`: cơ chế sâu, ngoại lệ/edge cases, kiến trúc hay phân tích nâng cao. **Đánh giá toàn bộ nội dung bài và năng lực cần đạt**, không suy đoán bằng số ký tự, thứ tự chương hoặc tiêu đề đơn lẻ.
2. Mỗi hàng Menu Cấp 2 nhận một giá trị `O<R>`; **không có Difficulty ở hàng Menu Cấp 1**. Nếu giá trị O hiện tại trống thì thêm; nếu đã là một trong ba nhãn thì giữ khi đánh giá vẫn đúng hoặc cập nhật nếu có căn cứ rõ ràng. Giá trị khác danh sách hoặc formula/note lạ: `BLOCKER: INVALID DIFFICULTY CELL`, không tự chữa.
3. **Giữ nguyên Data Validation dropdown và Conditional Formatting** của Google Sheets; chỉ cập nhật `userEnteredValue`, tuyệt đối không thay màu/validation/notes bằng API. Nếu dropdown không được thiết lập trên tab cũ, vẫn chỉ dùng ba nhãn hợp lệ; nêu cảnh báo cấu hình trong báo cáo để Prime xử lý, **không tự chỉnh Template hoặc validation**.



### W2.5. Chính worker tự chọn và GHI filename vào B hàng cha

1. Sau khi đọc đầy đủ chương và biết trọng tâm Knowledge, tự chọn một tên `.md` tiếng Anh ngắn, **có nghĩa với toàn chương** (không chỉ dịch một bài con). Tên phải theo regex chính xác `^[A-Za-z][A-Za-z0-9]*\.md$`, ưu tiên **PascalCase** hoặc acronym chuẩn. Ví dụ `OOPBasic.md`, `Class.md`, `Encapsulation.md`. Không dùng tên chung chung như `Chapter1.md`/`Topic.md`, tên chứa dấu/space/gạch/underscore/slash/chấm thừa hoặc basename Windows dành riêng `CON/PRN/AUX/NUL/COM1–COM9/LPT1–LPT9`.
2. **B hàng cha trống → chính worker của chương phải tự ghi filename vào B này trong phiên STEP 5.** Nếu B đã chứa một filename hợp lệ, giữ nguyên; nếu B có tên sai định dạng, formula, note hoặc metadata ngoài quyền thì `BLOCKER: INVALID PARENT FILENAME`, không tự ghi đè. **Không bao giờ chạm B của Menu Cấp 2**.
3. Worker kiểm tra độ hợp lý với A cha và tất cả B con **trong chương được giao**. Không được gọi Prime đặt tên hộ hoặc gửi `SUGGESTED_FILENAME` rồi kết thúc mà **không ghi B cha**. Mỗi worker chịu trách nhiệm filename của chính chương. **Prime thực hiện kiểm tra trùng tên case-insensitive và trùng kebab-case prefix trên toàn tab SAU KHI tất cả worker hoàn thành**; vì vậy việc một worker chưa biết tên của chương khác không phải lý do trì hoãn ghi B cha.
4. Filename collision fix chỉ ghi B/C cha trước khi có A-child IDs, sau snapshot và exact readback; không sửa Knowledge, AC hoặc các chương khác.

### W2.5.5. Chapter base ID in C parent

For the normal STEP 5 review, also write C<PARENT_ROW> = ReadmeSheetImportService.slugFromStem(B-parent-filename-without-md). Use lowercase kebab-case: OOPBasic.md -> oop-basic, Encapsulation.md -> encapsulation. Do not write C on children (their C:N is Knowledge) or A on children. C parent empty -> fill; exact matching value -> unchanged; mismatching value, formula, notes, chips or unclear owner -> BLOCKER. Snapshot and exact readback C parent. Collision correction of a B parent filename must also update C parent before A child IDs exist; Prime must grant BOTH B and C for correction. Once A IDs exist, refuse automatic B/C renaming to protect references.

### W2.6. BẮT BUỘC tạo Summary cho TỪNG bài trong AC hàng con (sau review/refactor)

**Chỉ thực hiện sau khi bản Knowledge cuối trong C:N của TOÀN BỘ chương đã review/refactor và qua chapter QA về độ chính xác, trùng lặp, thuật ngữ, code và Markdown.** Với từng Menu Cấp 2 tại `B<R>`, worker tạo **một tài liệu Markdown độc lập duy nhất tại `AC<R>`**; không sao chép cùng một bản cho cả chương, không chèn vào C:N hay AC cha. Phải đọc đủ body sau refactor, Special Note và bản đồ trách nhiệm các bài; không suy nội dung từ mỗi tiêu đề, không đặt mục tiêu mà Knowledge chưa dạy.

**Barem BẮT BUỘC: bốn mục 0 → 1 → 2 → 3, tên bài B<R> chính xác, phần 3 có bảng hai cột.** Số đoạn, số nhóm/câu hỏi, số năng lực đều theo nội dung thực tế, **không áp quota**. Ví dụ bên dưới chỉ minh họa định dạng, không được sao chép placeholder hoặc các mục minh họa làm nội dung thật:

```markdown
# 0. Định hướng học tập

## 1. Mục đích của bài học

**<Tên chính xác Menu Cấp 2 trong B<R>>**

<Lý do học, vấn đề cần giải quyết và giá trị khi vận dụng; chia thành số đoạn phù hợp bài.>

## 2. Những câu hỏi người học phải tự trả lời được

### <Nhóm kiến thức về bản chất/cơ chế, khi có trong bài>

1. <Câu hỏi mở yêu cầu giải thích bản chất được dạy?>
2. <Câu hỏi yêu cầu suy luận nguyên nhân, cơ chế hoặc hệ quả?>

### <Nhóm ứng dụng/thiết kế/giới hạn, nếu nội dung bài có>

1. <Câu hỏi tình huống áp dụng hoặc lựa chọn phương án?>
2. <Câu hỏi về lỗi, giới hạn hoặc kiểm chứng?>

## 3. Sau khi học xong, người học phải đạt được điều gì?

| Năng lực | Kết quả mong đợi |
| --- | --- |
| <Năng lực cụ thể từ nội dung bài> | <Hành động kiểm chứng được qua ví dụ/tình huống> |
```

**Tiêu chí tự QA từng AC con:**

1. Giữ **đúng bốn heading** theo thứ tự trên; Mục 0 chỉ là heading, không có prose chung chung. Mục 1 có **tên bài in đậm khớp 100% B<R>**; giải thích giá trị, vì sao cần học và vấn đề mà bài giúp giải quyết, không ép số đoạn/văn mẫu.
2. Mục 2 có một vài **nhóm câu hỏi mở có tên thực bằng heading `###`**, bên dưới dùng numbered list Markdown, câu hỏi có dấu hỏi và có thể trả lời/suy luận từ chính bài; không ghi sẵn đáp án, không ép A/B/C hay chín câu, không tạo nhóm rỗng hoặc cùng câu hỏi cho nhiều bài.
3. Mục 3 có **một bảng Markdown đúng hai cột `Năng lực` và `Kết quả mong đợi`**, mỗi hàng mô tả năng lực cụ thể → hành động quan sát/đánh giá được (phân tích, thiết kế, dự đoán, giải thích với lý do, mô phỏng, sửa lỗi...), không ghi chung chung chỉ “hiểu/biết”.
4. Nội dung phải đúng phạm vi Knowledge SAU refactor và vai trò riêng của bài so với các bài bên cạnh. Nếu Special Note đòi mục tiêu chưa được dạy hoặc không đủ căn cứ, ghi `CONTENT_SCOPE_LIMITATION` hoặc `BLOCKER: UNSUPPORTED_LEARNING_OBJECTIVES`; **không tự bịa kiến thức/đáp án, không sửa Knowledge sau khi đã soạn AC mà không rà soát AC lại**.
5. Markdown chuẩn (heading, **bold**, dòng trống, numbered list và bảng cân bằng dấu `|`; escape `|` trong cell). **Không có placeholder `<...>` trong output, HTML, JSON/YAML, fence bao trọn AC, TODO, thông báo nội bộ**, không thêm mạch liên kết nhiều bài vào AC con. Một ô AC con tối đa **30.000 ký tự Unicode**, không đẩy sang AD, không cắt bỏ mục tiêu quan trọng chỉ để vừa.

**Phân loại AC hiện có trước khi ghi:** `AC<R>` sạch/trống → `NEW_FILL`; đã đúng barem và vẫn khớp bản Knowledge cuối → `UNCHANGED/WRITE=NOT_RUN` (không rewrite cho hay hơn); có dữ liệu khác, guide thiếu mục, mâu thuẫn/stale sau refactor, công thức/chip, hoặc nghi do người sửa → `BLOCKER: EXISTING_TARGET_CONTENT/STALE_OBJECTIVES`. **Không tự xóa/ghi đè**, ngay cả khi guide trước đó được tạo bởi một workflow cũ. Muốn thay thế phải có ủy quyền riêng kèm xác minh provenance và preflight; không mặc nhiên suy quyền overwrite từ quyền refactor C:N. Trong cùng chương, xử lý các ô khác sạch/trống chỉ khi preflight cho phép và không tạo snapshot nửa chừng; chương có bất kỳ blocker không thể báo `COMPLETED`.

### W2.7. Authored-only AC parent YAML, matched by child row index

Worker writes one YAML document to AC parent after full Knowledge review and AC child Summaries. Root has exactly:
version: 1, type: LEARNING_ROADMAP, title, roadmapHeading, roadmap, comparisonMarkdown.

For N lessons, roadmap contains N maps in the exact B-child row order. Each map has exactly TWO keys:
description (specific to that lesson) and transition. For items 1..N-1, transition has just
description (the reason to continue); the last item's transition must be null. Do NOT author
id, order, title or transition.to. The Summary importer produces those technical fields
from A child ID, B child title, and list index. A child IDs are filled by Prime after worker QA.

Example of AC parent (two lessons):

    version: 1
    type: LEARNING_ROADMAP
    title: Chapter learning flow
    roadmapHeading: Learning sequence
    roadmap:
      - description: Understand foundational principles.
        transition:
          description: Apply those principles to concrete objects.
      - description: Model object state and behavior.
        transition: null
    comparisonMarkdown: |-
      ## 2. Lesson comparison

      | Criteria | Lesson 1 | Lesson 2 |
      | --- | --- | --- |
      | Ten | First | Second |
      | Van de can hieu | Basics | Behavior |
      | Gia tri doi voi module | Principles | Model |
      | Tu duy hinh thanh | Abstract reasoning | Object reasoning |

comparisonMarkdown continues to be a single GFM table with N+1 columns, proper heading,
and at least four existing pedagogical comparison rows. Preserve Vietnamese labels and
substance for real chapters; the example above is illustrative only. Never insert
fake arrows, redundant links or chapter conclusions in description.

QA: exactly N roadmap items; authored-only keys; N-1 meaningful transition descriptions,
last transition null; one valid N+1-column table; semantic match to final Knowledge and
AC-child Summaries; size at most 30,000 characters and exact readback.

Existing full-schema AC parent YAML (with id/order/title/to) is NOT authored-only: stop with
BLOCKER: LEGACY_AC_SCHEMA rather than overwrite/migrate it without explicit authorization.
The module section.yml output retains full schema, generated deterministically by code.

## W3. Ghi an toàn Knowledge C:N, Difficulty O, filename B/C cha và Summary AC

**W3/W4 correction:** Fix trùng filename chỉ sửa B/C cha trước khi A-child IDs được gán, snapshot và readback đầy đủ; không sửa hàng con.

1. Snapshot B/C cha, AC cha, các ô C:N/O/AC con gồm formula/note/validation; preflight ownership, không ghi đè AC human-owned/stale.
2. Review/refactor C:N, gán O, B/C cha, AC Markdown con và YAML cha; A-child IDs thuộc Prime.
3. Soạn bản README refactor hoàn chỉnh trước khi ghi. Nếu quá **360.000 ký tự**, báo `BLOCKER: CONTENT EXCEEDS C:N CAPACITY`, không cắt bỏ nội dung. Nếu vừa giới hạn, chia tuần tự **C, D, E, ... , N**, mỗi ô tối đa **30.000 ký tự**; bảo toàn chính xác mọi ký tự Unicode/newline/fence khi nối.
4. **Khi bản mới ngắn hơn bản cũ**, bắt buộc **xóa chỉ những ô đuôi cũ trong C:N của hàng đó** không còn dùng đến; nếu giữ đoạn D/E cũ thì người đọc sẽ ghép nhầm bài mới + bài cũ. Phần ô thay đổi và ô đuôi cần dọn **phải được cập nhật cùng một giao dịch/batch khi công cụ hỗ trợ**. Không ghi nguyên văn một phần khi còn lỗi dữ liệu/công cụ.
5. Ghi đúng B/C cha, C:N/O/AC con và AC cha; exact-readback toàn bộ, kiểm tra N roadmap item, final transition null, bảng GFM và C base ID từ B filename.
6. Nếu phát sinh lỗi khi ghi/read-back hoặc đã lỡ ghi một phần, **không báo PASS**. Nêu hàng bị ảnh hưởng, snapshot gốc và trạng thái cuối cùng; chỉ phục hồi từ snapshot nếu quyền sở hữu còn hợp lệ và chắc chắn không chạm thay đổi của người khác.

## W4. Kiểm tra sau refactor theo cả chương

After refactor, QA Knowledge/Difficulty/AC child Markdown, B/C identity, AC YAML, N roadmap entries, N-1 transitions, final null và bảng GFM N+1 cột.

**Không đánh dấu chương CLEAN nếu mới chỉ đọc từng bài độc lập nhưng chưa kiểm tra lại toàn chương.**

## W5. Báo cáo bắt buộc và SLEEPING

Worker trả kết quả đủ để Prime kiểm chứng, không thay thế bằng câu “đã refactor xong”:

- `STATUS`: `COMPLETED` / `BLOCKER` / `FAILED`; `SHEET`: tên tab, gid; `CHAPTER`: tọa độ Menu Cấp 1; `CHILD_ROWS`: các hàng Menu Cấp 2 thực sự review.
- `READ_COVERAGE`: tổng số bài phải đọc, số bài đã đọc **đầy đủ từ C:N**, và tổng số bài đã review lại sau refactor. Không báo 100% khi chỉ sample.
- `FINDINGS`: chỗ trùng lặp có ý nghĩa (mục/hàng nào với mục/hàng nào), điểm lặp cố ý giữ, vấn đề về độ chính xác/thiếu ý/Markdown và lý do.
- `CHANGES`: mỗi hàng sửa ghi tóm tắt **đã đổi gì, vì sao**, số ký tự trước/sau, các ô C:N bị thay đổi/xóa đuôi; danh sách hàng giữ nguyên và lý do ngắn.
- `DIFFICULTY`: bảng từng `B<R> → O<R> → BASIC/INTERMEDIATE/ADVANCED` kèm lý do dựa trên nội dung đã đọc, các ô O đã điền/cập nhật/giữ nguyên; cảnh báo dropdown nếu có.
- `FILENAME`: `A<PARENT> → B<PARENT>` với tên `.md` cuối cùng, `NEW/UNCHANGED/COLLISION_FIX`, lý do đặt tên, regex/Windows-name PASS và xác nhận **chính worker đã ghi B cha** nếu trước đó trống.
- `LEARNING_OBJECTIVES`: **từng hàng con** `B<R> → AC<R> → NEW_FILL/UNCHANGED/BLOCKER`, số nhóm/câu hỏi mở/số năng lực thực, độ dài ≤30.000, đủ bốn mục 0/1/2/3, tên bài khớp B, câu hỏi/năng lực đúng Knowledge sau refactor, Markdown/bảng hai cột `PASS`, exact readback `PASS`; nêu blocker và owner của mọi AC cũ stale/human-edited. Lượt chỉ sửa filename báo `NOT_TOUCHED`.
- CHAPTER_LINK: AC parent authored-only YAML parse/schema PASS, exactly N description/transition entries, last null, full comparison table, CONTENT_GROUNDED_QA and exact readback. Do not require derived IDs/order/titles/to in AC.
- READBACK: PASS/FAIL B/C parent, AC parent, từng C:N/O/AC child, không sửa ngoài ownership; kiểm tra giới hạn ô và Markdown sau ghép.
- `CHAPTER_QA`: PASS/FAIL kiểm tra lại toàn chương sau refactor, số lỗi trùng lặp/độ chính xác/format còn lại và `BLOCKERS` theo tọa độ (nếu có).
**STEP 5 chapter completion:** Knowledge QA, B/C identity, Difficulty O, AC child/parent authored content và exact readback PASS; Prime xử lý global collisions và A-child ID fill.
