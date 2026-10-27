# STEP 14 — WORKER ONLY: Translate EXACTLY ONE EN file

**Đọc toàn văn file này trước khi làm và sau khi mất context.** Prime cấp một file EN duy nhất trong mỗi lượt, kèm `FILE_TYPE`, `EN_TARGET`, SHA-256 và nguồn tab để định danh (KHÔNG cho quyền ghi Google Sheets). Bạn **chỉ được đọc/ghi chính EN_TARGET** và đọc file worker-only này. Không đọc file STEP Prime/General/rules, không duyệt repo, VI, file EN khác, source/test, Swagger khác, module-info, manifest hay backup. Không chạy task Gradle, Google Sheets importer, Git, commit/push; không tạo file proposal, không xóa/tạo thư mục/file khác.

## W1. Nguyên tắc dịch — không được thêm/bớt nội dung

1. **MỘT worker = MỘT file HOÀN CHỈNH cho một lượt**. Nếu file có 100 câu hỏi/67 API methods hoặc 20 milestone, phải duyệt hết tất cả trong chính file ấy. Không chia nhỏ cho worker khác, không tự nhận file tiếp theo. Không sửa file khi SHA-256 đã khác hash Prime cấp hoặc ownership chưa chắc chắn.
2. Dịch **duy nhất chữ tiếng Việt thuộc whitelist** sang tiếng Anh tự nhiên, chính xác thuật ngữ; **bảo toàn từng mệnh đề, điều kiện, phủ định, cảnh báo, số liệu, ví dụ và mức độ chắc chắn**. Không tự ý thêm bớt câu/ý, sửa kiến thức, rút gọn, thêm ví dụ, "cải thiện" giải thích, đổi câu đúng/sai, sáng tác case/scene/milestone hoặc chỉnh số lượng.
3. Giữ đúng thứ tự tất cả list/paragraph/section; không gộp hai item thành một hoặc tách thành item mới; không bỏ đoạn kể cả đoạn trùng lặp. Dịch sát nghĩa thay vì rewrite. VI có lỗi, thiếu logic hoặc mâu thuẫn → **REVIEW/BLOCKER**, không tự sửa dữ kiện.
4. **BẤT BIẾN đối với mọi file:** key/structure YAML, tên file, path, mọi ID/anchor, Java class/method/package, HTTP verb/path/status, JSON/XML property, placeholder/interpolation, các đoạn code/code fence, URLs, Markdown link destination, hình ảnh/media path, table/list count/order, boolean/null/number, schema comment, `isImportant`, `difficulty`, `readmeRelated`, `apiRelated`, tất cả relation và technical metadata. Không thay định danh ngay cả khi nó là chữ tiếng Việt.
5. **Không dịch** tên sản phẩm/thư viện/annotation/keyword Java, text output thật của chương trình/API, chuỗi dữ liệu kiểm thử, URL, HTML tag/attribute. Chỉ dịch lời diễn giải xung quanh. Ví dụ code chứa comment tiếng Việt → giữ nguyên đoạn code, báo REVIEW nếu cần localization riêng.

## W2. Whitelist theo FILE_TYPE — MỌI field khác đều KHÓA

| FILE_TYPE | Chỉ được dịch |
| --- | --- |
| `README` | Văn bản hiển thị: H1/H2/H3… tiêu đề; nhãn menu, text trong câu, đoạn Markdown, text link (không đổi href), text trong table/list, blockquote, phần giảng giải; chuỗi `<summary>Xem chi tiết</summary>` có thể dịch text bên trong |
| `INTRODUCTION` | Văn bản Markdown trong `BASE.md` theo cùng quy tắc README |
| `SUMMARY_SECTION` | YAML `summary/section.yml`: chỉ dịch `title`, `roadmapHeading`, `roadmap[].title`, `roadmap[].description`, `roadmap[].transition.description` và `comparisonMarkdown` (**một bảng GFM nhúng trong YAML block scalar, giữ nguyên số cột/dòng**). Giữ nguyên `version`, `type`, `roadmap[].id`, `roadmap[].order`, `roadmap[].transition.to`, final `transition: null`, cấu trúc danh sách và số bài. Bản EN sẽ được STEP 15 copy toàn văn vào AC hàng cha. |
| `SUMMARY_LESSONS` | **Chỉ** `summaries[].title` và `summaries[].summary` trong `summary/summaries.yml`; `summaries[].id`, root, thứ tự/đếm bài và schema khóa bất biến |
| `QUIZ` | `questions[].question`, `questions[].answers[].answer`, `questions[].answers[].explanation` |
| `INTERVIEW` | `questions[].question`, `questions[].answer` |
| `SUMMARY_QUESTIONS` | `questions[].question`, `questions[].answer` trong `questions/summary.yml`; không có relation |
| `PRACTICE` | `scenarios[].title/context/problem/analysis.{possibleCauses,application,tradeoffs}/exercise.{requirements[],deliverables[],acceptanceCriteria[],hints[],referenceSolution}`; dịch **toàn bộ một `practice/scenarios.yml`**, không tách file theo scenario |
| `ROADMAP` | `roadmap[].title`, `roadmap[].purpose`, từng phần tử `roadmap[].objectives[]`, `roadmap[].relatedModules[].label` và `note` |
| `SWAGGER_CONTROLLER_DESCRIPTION` | `<Controller>.description` (chỉ text trong HTML, không thay tag/attrs) |
| `SWAGGER_API_DESCRIPTIONS` | `<Controller>.<method>.summary`, `description` |
| `SWAGGER_API_EXECUTION` | `<Controller>.<method>.execution` (HTML text, giữ cấu trúc tag) |
| `SWAGGER_API_CASES` | `<Controller>.<method>.cases[].title`, `description`, `detailDescription` (HTML text, giữ tags) |
| `SWAGGER_API_PARAMS` | `<paramName>.summary`, `description` |

**Phần khóa riêng cần nhớ:**

- **README/INTRODUCTION:** Giữ nguyên số/tầng heading, các anchor `<a id="...">`, `id` trong HTML, code fences (ngôn ngữ và thân), inline code, tag/attribute HTML, địa chỉ hyperlink và file ảnh; không thêm bớt section, list item, table row/column. Menu item label có thể dịch nhưng anchor liên kết bất biến.
- **QUIZ:** `id`, thứ tự `questions` và thứ tự đáp án `A/B/C/D`, `correctAnswerId`, `readmeRelated`, `apiRelated`, metadata đều khóa. `explanation` của **mọi** lựa chọn đúng/sai đều phải dịch, không chỉ giải thích đáp án đúng. Không đảo vị trí đáp án.
- **INTERVIEW:** Không tự tạo question ID; giữ **chính xác số lượng và thứ tự** câu, cặp `question/answer` tương ứng, metadata và relation. Không thêm phỏng vấn mới hoặc rút ngắn câu trả lời.
- **SUMMARY_QUESTIONS:** Chỉ dịch question/answer; không thêm câu, không trộn với Interview hoặc Summary STEP 5, không thêm metadata/related.
- **ROADMAP:** `id`, `dependsOn`, `relatedKnowledge`, `relatedModules[].routeId`, số lượng/thứ tự milestone là khóa. Không dịch route/category ID.
- **VIDEO không thuộc STEP 14:** file Video phải được chuyển sang `STEP_OPTIONAL_VIDEO_TRANSLATE` sau `STEP_FINAL`; nếu nhận file Video trong bước này, worker báo `BLOCKER: WRONG STEP`.
- **SWAGGER:** Controller name, exact method signature, `usage`, `methodName`, `params`, `path`, `mapping`, `httpMethods`, `mappingPaths`, `consumes/produces`, `enableVideoYoutube`, `videoYoutubeId/title`, case `id`, `input`, `status`, `response`, `readmeRelated`, metadata đều khóa. Không "dịch" literal JSON body, lỗi kỹ thuật, Java code hoặc response gốc. `api-params.yml`: param keys và `usage` khóa.
- **YAML comments** (bao gồm khối `# <quiz-schema>`, `# <roadmap-schema>`) là schema contract cố định, **không sửa/xóa/reflow**. Chỉ sửa đúng các scalar strings được whitelist; giữ YAML parser compatibility và Unicode.

## W3. Quy trình thực thi

1. Kiểm tra đúng `EN_TARGET` và `FILE_TYPE` do Prime cấp, đủ quyền chỉ một file, nội dung EN ban đầu còn tiếng Việt từ STEP 13. Kiểm tra hash; khác → `BLOCKER: SOURCE_CHANGED`. Không tìm phần "thiếu" từ VI hoặc file khác.
2. Đọc **toàn bộ** file, xác định các trường/text span cần dịch. Tự theo dõi số mục cần hoàn thành (section/question/answer/API/milestone/scene). Với file dài, làm tuần tự **trong một lượt, không bỏ sót**.
3. Dịch chỉ whitelist, bảo toàn source markup và khóa fields; thực hiện thay đổi **tại chính file EN được giao**. Không dùng YAML serializer nếu làm mất comments, thứ tự hay formatting cần giữ.
4. QA ngay tại file: Markdown anchor/link/fences còn nguyên; YAML parse hợp lệ, keys/list structure nguyên; số mục giữ nguyên; không còn tiếng Việt trong **các trường cần dịch** (ngoại trừ chuỗi kỹ thuật được phép giữ), không thêm câu, không đảo ý. Đọc lại file đã ghi. Nếu không kiểm chứng được → báo REVIEW, không claim PASS.
5. Báo `STATUS=COMPLETED/REVIEW/BLOCKER`, `FILE_TYPE`, `EN_TARGET`, `SOURCE_SHA256`, `AFTER_SHA256`, `TRANSLATABLE_ITEMS`, `TRANSLATED_ITEMS`, `PROTECTED_FIELDS_CHECK`, `PARSE`, `READBACK`, `NOTES`. `COMPLETED` là worker hoàn tất **một file**, **không phải Prime QA hoặc STEP 14 hoàn tất**.

Trả về SLEEPING và giải phóng ownership, không tự sửa thêm file nào.
