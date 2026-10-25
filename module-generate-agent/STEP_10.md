# STEP 10 — PRIME ONLY: Generate Quiz YAML theo Menu Cấp 1

**CURRENT_STEP policy (2026-10-25):** Progress is owned ONLY by `CURRENT_STEP.VALUE` (string) in the verified module `master.json`, never by any Google Sheet/Excel cell. Prime updates after full QA using `module-generate-agent/CURRENT_STEP.md`; Worker does not write it. Any old cell, label, formula, or Sheet-readback instructions about progress below are superseded. The STEP-specific completion gates still apply.


## 0. Mục tiêu và quyền hạn

- **STEP 10 = Quiz**; **STEP 9 = Interview YAML**; Video thuộc các optional STEP sau STEP_FINAL.
- Một worker phụ trách **một Menu Cấp 1**. Đọc toàn bộ Knowledge của chương, tạo một tài liệu Quiz YAML trong **T:W cùng hàng cha**, tối đa 4 ô × 30.000 Unicode code points/ô. Không viết Quiz ở hàng con.
- Worker chỉ viết `questions[]` với **`question`, `answers`** (chính xác A/B/C/D, mỗi đáp án có `answer` và `explanation`) và **`correctAnswerId`**. Worker **không viết** question `id` `readmeRelated`, `apiRelated` hoặc tự chỉnh module.
- Prime phải đọc `GENERAL_AGENT_RULES.md`, tất cả `module-generate-agent/rules/`, `module-generate-info.txt`, toàn bộ file này và `worker/STEP_10_WORKER.md`. **Worker chỉ tự đọc toàn bộ `worker/STEP_10_WORKER.md`**, không đọc Prime/rules/source repo. Prime chỉ gửi 4 dòng: URL tab, vùng nguồn chính xác, vùng đích T:W, đường dẫn worker-only.
- Pool theo Mode A/B **chỉ trong session** (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; ACTIVE+SLEEPING); **reuse SLEEPING** trước, spawn tuần tự mỗi lệnh một worker, không tạo dư và không trùng ownership.

## 1. Mapping và preflight

| Thành phần | Vùng | Quyền |
| --- | --- | --- |
| Module/chủ đề | `A1`, `B1`; I3 Module Location | Chỉ đọc |
| Chương | `A<PARENT_ROW>` và filename `B<PARENT_ROW>` | Chỉ đọc |
| Menu Cấp 2/Knowledge | `B<CHILD_ROWS>` và `C<CHILD_ROWS>:N<CHILD_ROWS>` | Chỉ đọc, ghép C→N không separator |
| Difficulty | `O<CHILD_ROWS>` | Chỉ đọc nếu cần |
| Quiz YAML | **`T<PARENT_ROW>:W<PARENT_ROW>`** | Chỉ worker sở hữu chương được ghi |

- Template: **`T18 = Quiz`, `T19:W19 = Quiz YAML`** (bốn header thống nhất); tối thiểu **27 cột**. Thiếu/sai: `BLOCKER: QUIZ SCHEMA`; không tự dời Video X:AA hoặc Interview P:S.
- Prime xác định hàng cha/con bằng A:B thật, không suy từ số thứ tự. Filename B cha phải là `.md` hợp lệ sau STEP 5. Mỗi chương có Knowledge C:N đầy đủ; thiếu là `BLOCKER: INCOMPLETE KNOWLEDGE`.
- Preflight tất cả T:W trên hàng cha: `userEnteredValue`, formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`, owner. Trống hết → `NEW_FILL`. YAML nguồn/đã Normalize hợp lệ → `UNCHANGED` không ghi. **Chỉ khi có lỗi cú pháp/định dạng YAML hoặc Markdown nhúng**, Prime mới được giao worker `FORMAT_FIX` tối thiểu trong T:W với diff chứng minh **câu hỏi, bốn đáp án/giải thích, correctAnswerId, metadata và thứ tự giữ nguyên**. Không sửa nội dung hoặc ép YAML Normalize về bản YAML nguồn; không chứng minh được → BLOCKER. Không coi dữ liệu ở hàng con là Quiz hợp lệ.
- `question`/`answers` phải đúng Java, có một đáp án đúng, ba distractors hợp lý, giải thích cho **từng** lựa chọn; kiểm tra đánh đố/answer-leakage, kiến thức hiện tại, code và stdout. Không ép quota câu hỏi. A/B/C/D là ID nội bộ, không phải thứ tự frontend shuffle.

## 2. Worker → Prime QA

1. Giao một worker mỗi chương với phạm vi đọc/ghi chính xác; worker tự đọc worker-only file, chỉ soạn YAML mới ở `NEW_FILL`; `FORMAT_FIX` thì sửa cú pháp tối thiểu trên bản cũ, `UNCHANGED` thì không ghi. Khi có write, chia ≤4 ô T→U→V→W, preflight, exact readback Unicode/whitespace 100% và so diff bảo toàn nội dung. Worker không chạy Gradle, không cập nhật CURRENT_STEP.VALUE, không commit/push.
2. Prime đọc lại **toàn bộ** T:W trên các hàng cha và kiểm tra mọi hàng con không có Quiz orphan. YAML nguồn `NEW_FILL` có root chỉ `questions`; mỗi question **chỉ có** `question`, `answers`, `correctAnswerId`, mỗi answer chỉ `id`, `answer`, `explanation` và đúng A/B/C/D. YAML `FORMAT_FIX`/đã Normalize trước đó có thêm canonical fields thì phải giữ nguyên các field/giá trị; Prime đối chiếu snapshot trước–sau, `FORMAT_DIFF`, `CONTENT_PRESERVED` và exact readback.
3. Đánh giá toàn tab Knowledge→Quiz, không trùng lặp vô ích, kiểm tra blind-answer, distractors, explanation và đúng-sai. Theo dõi `PARENT_ROW → CHILD_ROWS → T:W → OWNER → YAML_QA → READBACK`.
4. **Nếu chỉ thử vài chương, không chạy Normalize APPLY/module import toàn tab hoặc đổi CURRENT_STEP.VALUE.** Khi toàn bộ chương trong tab đã đầy đủ và QA PASS mới đi tiếp.

## 3. Normalize Quiz trên chính Google Sheets — Prime, root task

Task **`normalizeQuizQuestionsFromSheet`**; mặc định DRY_RUN; yêu cầu URL spreadsheet và **gid tab** (khác spreadsheet ID). `DRY_RUN` chỉ đọc, `APPLY` cần Sheets editor/ADC như STEP 9.

```powershell
.\gradlew.bat normalizeQuizQuestionsFromSheet -PfileUrl="<URL>" -PsheetID=<GID> -PquizMode=DRY_RUN
.\gradlew.bat normalizeQuizQuestionsFromSheet -PfileUrl="<URL>" -PsheetID=<GID> -PquizMode=APPLY
```

- Code parse YAML, không gọi AI. Với câu hỏi **chưa có** `id`, tạo ID **`<lowercase filename stem>-001`, `-002`, ...**, tránh mọi ID đang có; ID cũ phải giữ nguyên, trùng ID → BLOCKER, không bao giờ tự đánh lại ID khi đổi thứ tự. Filename đã đổi nhưng ID tồn tại vẫn giữ; nếu cần migrate ID, xử lý chủ động sau.
- Điền `readmeRelated`/`apiRelated` mặc định; **không reset human-edited**. Chỉ điền `readmeRelated.anchor: ''` (không còn `file`); `apiRelated.controller: ''`, `apiRelated.methodSignature: ''`. Metadata và related hợp lệ đã có được giữ; `file` từ dữ liệu legacy chỉ được chấp nhận khi khớp chương, sau đó bỏ khỏi YAML Normalize.
- `readmeRelated.anchor: ''` nghĩa là chưa có liên kết Knowledge; không tự đoán anchor/API. Khi có anchor, Portal chỉ tìm Knowledge **trong cùng chương** và tự tạo `file + anchor` ở output.
- Preflight mọi row, ngăn orphan/missing Quiz và fragment gap/oversize, snapshot fingerprint trước batch, một batch ghi T:W, exact readback và idempotent. Không ghi module.

## 4. Quiz ByLocation — Prime, một module

Sau Normalize APPLY và readback PASS, chạy đúng module có `I3` khớp (không dùng `generateModuleContentFromSheetByFiles` hoặc task tổng hợp):

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleQuizFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleQuizFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
```

- Task ghi snapshot **`content/readme/docs/vi/menu/<chapter>/quiz/question.yml`** cho từng chương. Trước APPLY phải preflight toàn bộ YAML/schema/ID, stage mọi chương rồi **CLEAN toàn bộ thư mục `quiz/` VI cũ + legacy Quiz VI của module trước FILL**; nếu lỗi ghi, rollback. Không động Knowledge/Metadata/Interview/Video hoặc Quiz EN. **Completion gate STEP 10 vẫn không APPLY khi còn chương trống**, schema không hợp lệ, hoặc `SKIPPED_*`; importer thuần code hiểu chapter trống trong snapshot hợp lệ là xóa Quiz cũ ở chương đó.
- Validate `quiz.schema.json` qua `QuizQuestionSchemaService`, kiểm tra question IDs unique toàn module, đúng 4 đáp án và correctAnswerId. Readback file theo từng chương, counts, flags/câu hỏi. `generatePortalData` xuất một Quiz YAML mỗi chương, `index.json` theo ngôn ngữ và resolve anchor trong Knowledge cùng chương.
- Chỉ sau **toàn tab Worker+QA PASS, Normalize DRY_RUN/APPLY/readback PASS, ByLocation DRY_RUN/APPLY/output QA PASS** Prime mới update/readback `CURRENT_STEP.VALUE = "10" với preflight `CURRENT_STEP` theo PRIME_RULES. Báo `NORMALIZE_STATUS`, `MODULE_SYNC_STATUS`, `DESTINATION`, `QUESTION_COUNT`, `READBACK`, blockers; không commit/push.

### Prime giao worker — đúng bốn dòng

```text
<URL_TAB_ĐÚNG_GID>
A1, B1, <PARENT_A>, <FILENAME_B_PARENT>, <CHILD_B_ROWS>, <KNOWLEDGE_C_TO_N_CHILD_ROWS>, <DIFFICULTY_O_CHILD_ROWS>
<T_TO_W_TRÊN_HÀNG_PARENT>
/java-learning/module-generate-agent/worker/STEP_10_WORKER.md
```
