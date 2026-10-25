# STEP 11 — WORKER ONLY: Related metadata cho một Menu Cấp 1

**CURRENT_STEP policy (2026-10-25):** Progress is owned ONLY by `CURRENT_STEP.VALUE` (string) in the verified module `master.json`, never by any Google Sheet/Excel cell. Prime updates after full QA using `module-generate-agent/CURRENT_STEP.md`; Worker does not write it. Any old cell, label, formula, or Sheet-readback instructions about progress below are superseded. The STEP-specific completion gates still apply.


**BẮT BUỘC đọc toàn văn file này trước khi làm việc và sau compaction.** Prime giao đúng bốn dòng: link tab (định danh, **không cho ghi**), tọa độ chương/hàng cha Interview+Quiz, đường dẫn `READ` input snapshot và `WRITE` proposal riêng chương, đường dẫn file worker-only này. Không đọc STEP Prime, GENERAL, rules/TXT, source repo hoặc thư mục ngoài scope đã giao; không yêu cầu Prime paste content.

## W1 — Nguồn và ownership

1. **Một worker = một Menu Cấp 1 = cả ba nhóm Swagger / Quiz / Interview**. Chỉ đọc `<ABSOLUTE_STEP11_SEED_CHAPTER_DIR>/input.yml` và dữ liệu **được nhúng trong snapshot chương đó**. Prime đã lấy Knowledge/anchor từ README và source canonical, câu hỏi từ YAML Google Sheets/Module, API từ Swagger, kèm lookup ID/method hợp lệ của toàn module. Không đi dò tài liệu/chương khác. Nếu seed thiếu thân Knowledge, text câu hỏi, metadata hoặc mô tả API cần thiết → `REVIEW: INSUFFICIENT_CONTEXT`; không bịa relation hay đọc vượt quyền.
2. Xác nhận `moduleId`, language, parent row/chapter filename, danh sách Quiz ID/Interview identity, Controller/method ownership và `sourceSha256` trong seed. **Chỉ dùng identity được cấp**, không chuyển câu hỏi hay Controller sang chương khác. Lookup module-level **chỉ là danh sách ứng viên/đích chính xác**, không trao quyền ghi file chung.
3. File **duy nhất** được tạo/ghi là `<ABSOLUTE_STEP11_CHAPTER_PROPOSAL_FILE>` trong `build/step11/<RUN_ID>/chapters/<CHAPTER_KEY>/`. Không sửa `input.yml`, `_prime-manifest.yml`, Sheet P:S/T:W, Swagger, `content`, Java/test, `rules`, CURRENT_STEP.VALUE hoặc bất cứ shared file nào. Không chạy importer, Gradle, git, commit/push.
4. Seed thiếu chapter, hash không khớp, duplicate identity, hai worker tranh cùng Controller hoặc nguồn không parse được → `BLOCKER`, không ghi proposal phỏng đoán.

## W2 — Đánh giá relation theo ý nghĩa thực

Đọc nội dung bài Knowledge của chương cùng câu hỏi Quiz/Interview và mô tả API thực tế trong seed. Với mỗi item **và từng field related** xác định:

- `SET` — field hiện trống/partial hợp lệ, có **target tồn tại và thật sự giải thích, bổ trợ hoặc minh họa** cho nội dung, không chỉ cùng từ khóa.
- `KEEP` — relation hiện có **hợp lệ và đúng ý nghĩa**, không sửa.
- `NO_MATCH` — không có đích phù hợp, để trống có chủ đích; không cố tạo liên kết cho đủ.
- `REVIEW` — target không rõ, source thiếu, relation cũ đáng nghi, cần Knowledge/API ngoài context, nhu cầu vượt ownership hoặc conflict với schema. Không tự sửa relation cũ.

**Mục tiêu là kiểm tra 100% item/field, không phải điền 100% related.** Không ép số liên kết tối thiểu, không sửa nội dung để hợp thức hóa target.

| Loại item | Field đánh giá | Quy tắc |
| --- | --- | --- |
| Swagger Controller | `readmeRelated` | `file` README chapter đúng theo language, không cần anchor |
| Swagger method | `readmeRelated` | `file` (hoặc kế thừa Controller) + `anchor` section có thật, giải thích method |
| Quiz mỗi `id` | `readmeRelated` và `apiRelated` riêng | **Chỉ `anchor`** cùng chương và `controller/methodSignature` cho API |
| Interview mỗi identity | Hai field trên riêng | Lấy identity `parentRow:ordinal:fingerprint` từ seed, không tạo ID mới |

- Quiz/Interview không chứa `readmeRelated.file` trong nguồn; **chỉ đề xuất anchor cùng chương**. Nếu target phù hợp nằm ở chương khác → `REVIEW: CROSS_CHAPTER_QUESTION`, không đưa target trái schema vào `SET`. Swagger vẫn giữ đầy đủ `file` riêng.
- API phải dùng tên Controller, `methodSignature` nguyên văn trong danh mục và method `usage:true` nếu muốn điều hướng trên Portal. Không liên kết `usage:false` như API đang hoạt động. API không phù hợp là `NO_MATCH`, không suy từ tên class.
- Swagger VI/EN độc lập: không copy file/anchor VI vào EN nếu file EN/anchor EN không có thật. Các method có `usage:false` vẫn phải được audit và bảo toàn metadata hiện hữu, nhưng không tạo liên kết Portal mới chỉ để đủ coverage.
- `KEEP` chỉ khi relation gốc đúng cả tồn tại lẫn ngữ nghĩa. Relation gốc không hợp lệ → `REVIEW`, **không overwrite**. `NO_MATCH` cũng không xóa existing.
- Trong `evidence` ghi ngắn **tại sao quan hệ đúng, hoặc vì sao không chọn**; không sinh thêm nội dung Knowledge, sửa câu hỏi/đáp án hay viết Swagger mới.

## W3 — Format proposal duy nhất

File `proposal.yml` là **đề xuất thay đổi metadata**, không phải bản sao/override nguyên `question.yml` hay Swagger. Ghi YAML parseable:

```yaml
moduleId: "example-module"
chapterKey: "1.Introduction/knowledge/Concept.md"
sourceSha256: "<exact seed sourceSha256>"
relations:
  - kind: "swagger-controller"
    language: "vi"
    key: "ConceptController"
    field: "readmeRelated"
    decision: "SET"
    value:
      file: "1.Introduction/knowledge/Concept.md"
    evidence: "Controller minh họa trực tiếp chương Concept."
  - kind: "swagger-method"
    language: "vi"
    key: "ConceptController#inspect()"
    field: "readmeRelated"
    decision: "SET"
    value:
      file: "1.Introduction/knowledge/Concept.md"
      anchor: "concept-inspect"
    evidence: "Section này giải thích đúng hành vi inspect()."
  - kind: "quiz"
    language: "vi"
    key: "concept-001"
    field: "apiRelated"
    decision: "NO_MATCH"
    evidence: "Đây là câu hỏi khái niệm, không có API minh họa trực tiếp."
  - kind: "interview"
    language: "vi"
    key: "20:1:<sha256-of-question-and-answer>"
    field: "readmeRelated"
    decision: "KEEP"
    evidence: "Liên kết hiện có đúng file và section Knowledge."
```

Ví dụ chỉ thể hiện cấu trúc, **không dùng các ID/path ví dụ làm target thật**. `moduleId/chapterKey/sourceSha256` phải bằng seed. `relations[]` phải có **đúng một record cho từng cặp (`kind`, `language`, `key`, `field`)** trong danh sách targets Prime cấp, không thiếu/duplicate; mỗi Quiz/Interview cần **hai record** (`readmeRelated` và `apiRelated`).

- `kind`: `swagger-controller` / `swagger-method` / `quiz` / `interview`.
- `key`: Controller name; Swagger method `ControllerName#exactMethodSignature`; Quiz `id`; Interview **đúng identity trong seed**.
- `decision`: `SET` / `KEEP` / `NO_MATCH` / `REVIEW` (case-sensitive).
- `value`: **chỉ có khi `SET`**, gồm `file` (Swagger Controller), `file+anchor` (Swagger method), **`anchor` duy nhất (Quiz/Interview)**, hoặc `controller+methodSignature` (API); đích lấy từ lookup chính xác. Không đề xuất `value` khi `KEEP/NO_MATCH/REVIEW`.
- `evidence`: một giải thích ngắn, có căn cứ trong content/behavior. Không trích dài, không nhồi điểm số hay confidence giả tạo.
- Field hiện có đúng hoặc không có match thì không cần ghi giá trị: Prime sẽ giữ nguyên bytes/semantic payload. **Không đề xuất xóa, đảo thứ tự, reset metadata hoặc đổi chapter owner.**

## W4 — QA và bàn giao

1. Kiểm tra mọi mục/field trong seed đã audit; không bỏ Controller/method/question chỉ vì `usage:false` hay target trống. Không tạo record cho module/chapter không được cấp.
2. Đảm bảo `SET` có cả target chính xác và evidence, phù hợp language, target không self-contradict; `KEEP` được kiểm tra; `NO_MATCH` có lý do; `REVIEW` ghi rõ điểm cần Prime xử lý. Không đổi content.
3. Trước ghi, kiểm tra proposal path chỉ thuộc chương này và không có dữ liệu session khác cần giữ. Ghi **một file proposal**; YAML parse và exact readback; source fingerprint so với seed. Nếu ghi một phần/mismatch → `BLOCKER`, không tuyên bố completed.
4. Báo: `STATUS=COMPLETED/BLOCKER/FAILED`, `MODULE_ID`, `CHAPTER_KEY`, `LANGUAGES`, `TARGET_FIELDS`, `AUDITED`, `SET`, `KEEP`, `NO_MATCH`, `REVIEW`, `SEED_SHA256`, `PROPOSAL_PATH`, `YAML_PARSE`, `READBACK`, `BLOCKERS`. `COMPLETED` ở worker nghĩa là proposal/readback xong, **chưa phải Prime QA hoặc module APPLY**.

Sau khi báo kết quả, trở về `SLEEPING`. Không tự nhận chương khác, không spawn worker và không thao tác tiếp trên module.
