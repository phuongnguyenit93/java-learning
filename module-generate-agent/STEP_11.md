# STEP 11 — PRIME ONLY: Enrich Related metadata (Swagger / Quiz / Interview / Roadmap)



## 0. Mục đích và ranh giới

STEP 11 chạy **sau STEP 10**, khi Knowledge, API/Swagger, Quiz và Interview của đúng module đã được tạo/QA. Đây là bước **liên kết nội dung đã tồn tại**, không phải bước tạo, viết lại hay cải thiện bài học. **Video đã chuyển thành STEP_OPTIONAL_VIDEO và STEP_OPTIONAL_VIDEO_TRANSLATE, chỉ chạy sau STEP_FINAL khi được yêu cầu; không phải prerequisite của STEP 11.**

- **Một worker = một Menu Cấp 1, một lượt xử lý cả Swagger + Quiz + Interview** thuộc chương. Không chia ba worker theo ba loại tài liệu.
- **Worker chỉ đề xuất** metadata liên kết trong staging riêng chương; **không sửa Google Sheets, YAML chính, README hay Java**. Prime lập danh mục, xác định ownership, kiểm tra độc lập, và **duy nhất Prime** áp dụng thay đổi sau khi mọi chương QA PASS.
- Prime xử lý riêng Roadmap `relatedKnowledge`, `relatedModules` ở **mức toàn module/cross-module**. Không giao Roadmap cho worker từng chương.
- Đích là **100% item/field liên quan được kiểm tra**; không ép 100% field phải có giá trị. Quan hệ không có bằng chứng thì để trống, có lý do `NO_MATCH`; nghi vấn thì `REVIEW`/BLOCKER. Không liên kết theo độ giống tên đơn thuần.
- Scope mặc định là **đúng module tại I3** và **VI nguồn từ Sheet**. Ngôn ngữ khác (EN, ...) chỉ xử lý khi file Knowledge/Swagger/question tương ứng hiện hữu và xác định ownership rõ; không dịch relation từ VI sang EN theo suy đoán. Không đổi video/script, code, câu hỏi/đáp án, metadata review, số lượng/thứ tự.
- Không tự động commit/push, không chạy STEP khác. **STEP 11 này là hợp đồng vận hành; không khẳng định đã tồn tại task enrichment/merge tự động.** Nếu thiếu cơ chế APPLY an toàn, báo blocker; không giả định hàm đã có.

## 1. Prime preflight và danh mục dùng lại code hiện có

1. Đọc **toàn văn** `module-generate-agent/GENERAL_AGENT_RULES.md`, liệt kê/đọc **mọi file** `module-generate-agent/rules/`, file Prime này, `worker/STEP_11_WORKER.md`, và `module-generate-info.txt`. Pool Mode A = tối đa 3 (ACTIVE+SLEEPING) mặc định; Mode B = N nếu user chỉ định trong session; reuse SLEEPING trước, spawn từng worker một theo `rules/PRIME_RULES.md`.
2. Từ TXT xác minh URL/gid/tên tab, `A1`, `B1`, `I3`, `CURRENT_STEP:CURRENT_STEP.VALUE`. I3 phải nằm trong `java-learning/module/`, trùng module Gradle thật; không đoán theo tên sheet. Kiểm tra STEP trước hoàn tất bằng **dữ liệu thực** (không chỉ tin CURRENT_STEP.VALUE lịch sử), không có chỉnh sửa đang diễn ra ở nguồn.
3. Lập ledger **toàn bộ** Menu Cấp 1 từ `A19:B<last>`. `A<PARENT>` là chương; `B<PARENT>` là filename; child Knowledge từ `B<CHILD>` và `C:N`. Xác nhận README tại `content/readme/docs/<language>/menu/<chapter>/knowledge/<filename>`; Quiz/Interview từng chương có thể nằm tại `<chapter>/{quiz,interview}/question.yml`. Chương chưa có file câu hỏi là `NOT_APPLICABLE`, không tự tạo câu hỏi.
4. **Tái sử dụng parser/projection sẵn có**, không viết lại parser anchor: `ReadmeKnowledgeParser`, `PortalKnowledgeProjectionService` (category `id`, `sourcePath`, section `id`/title), `PortalApiProjectionService` (Swagger), `PortalQuizProjectionService` và `PortalInterviewProjectionService`. Có thể đọc đầu ra `generatePortalData` nếu đã được tạo **và đối chiếu freshness với source**; task này là generator toàn repo, không phải một hàm danh mục riêng cho STEP 11, **không tự chạy** khi chỉ cần kiểm kê. Nếu projection không có hoặc cũ, parse nguồn canonical bằng service hiện hữu. Không dùng projection làm nơi ghi.
5. Tạo lookup chỉ đọc **theo language**: `Knowledge (sourcePath, categoryId, sectionId/anchor, title)`; `API (ControllerName, methodSignature, usage, description)`; `Quiz (id, parent chapter, text)`; `Interview (parent chapter, ordinal, fingerprint của question+answer)`; và catalog `module routeId` cho `relatedModules`. `ReadmeKnowledgeParser` loại anchor giả bên trong code fence; không lấy anchor từ tiêu đề trống, không tự bịa id. Kiểm tra trùng/missing key và namespace module/language trước khi giao.
6. **Nguồn Quiz/Interview VI là YAML chuẩn hóa trên Google Sheets**: Quiz `T<PARENT>:W<PARENT>`, Interview `P<PARENT>:S<PARENT>` (ghép liên tiếp **không separator**, mỗi ô tối đa 30.000 Unicode code points). Đối chiếu câu hỏi/đáp án/ID với `content/readme/docs/vi/menu/<chapter>/{quiz,interview}/question.yml` của **đúng chương**. Có drift hoặc Sheet chưa normalize → `BLOCKER: SHEET MODULE DRIFT`; Quiz dùng `id`, Interview dùng `parentRow + ordinal trong chương + SHA-256(question, answer)`.
7. Mapping Swagger: lấy **ControllerName + exact methodSignature + `path`** từ `swagger/{vi,en}/api-descriptions.yml`, xác minh source path/chapter package như STEP 7 và `usage` của method. Một Controller chỉ được gán một owner; shared/cross-chapter Controller chưa được giải quyết → `BLOCKER: CROSS_CHAPTER_OWNERSHIP`. Worker không sửa shared YAML chính.
8. Kiểm tra dữ liệu đang có và SHA-256 của các nguồn Sheet vùng đọc, Markdown/section IDs, Swagger vi/en, module Quiz/Interview vi/en, Roadmap vi/en. **Không làm mất relation được người viết điền sẵn**; relation hợp lệ đã có là `KEEP`, relation sai/xung đột là `REVIEW`, không tự ghi đè.

## 2. Phạm vi relation chính xác

| Nguồn | Field có thể enrichment | Đích có nghĩa |
| --- | --- | --- |
| `swagger/<lang>/controller-description.yml` | `<Controller>.readmeRelated.file` | Đường dẫn README chương trong **cùng language** |
| `swagger/<lang>/api-descriptions.yml` | `<Controller>.<method>.readmeRelated.file/anchor` | `file` chương (có thể kế thừa controller); anchor **section** chính xác của API method |
| `content/readme/docs/<lang>/menu/<chapter>/quiz/question.yml` | `questions[].readmeRelated.anchor`, `apiRelated{controller,methodSignature}` | Section Knowledge trong chính chương / method API thực sự giải thích câu hỏi |
| `content/readme/docs/<lang>/menu/<chapter>/interview/question.yml` | Hai relation như Quiz | Bài Knowledge / API giúp trả lời câu hỏi |
| `content/roadmap/<lang>/roadmap.yml` | `roadmap[].relatedKnowledge[]`, `relatedModules[]` | Category ID **cùng module** / `routeId` của module đích |

- Quiz/Interview chỉ lưu `readmeRelated.anchor` trong YAML nguồn; `readmeRelated.file` được Portal tự tính từ chapter + anchor, **không đề xuất hay ghi field này về Sheet**. `apiRelated` vẫn có `controller` và `methodSignature` đồng thời hoặc cùng trống. Nếu cần Knowledge chương khác → `REVIEW: CROSS_CHAPTER_QUESTION`, không lách ownership.
- Swagger Controller relation chỉ có `file`; Swagger method relation có `anchor` và có thể kế thừa `file` từ Controller. Khi file method trống phải kiểm tra được inherited file; nếu chưa rõ, đề xuất cả file+anchor. Bảo toàn VI/EN độc lập, kể cả method `usage: false` (không tự bật usage hay tạo link Portal tới inactive API).
- `relatedKnowledge` của Roadmap trỏ **category ID**, **không phải section anchor**; `relatedModules` trỏ **routeId** có thực, không tạo module ID từ display name. Chúng không thay đổi thứ tự học hoặc `dependsOn`.
- Với Quiz/Interview, Normalize mặc định `readmeRelated.anchor: ''` (chưa liên kết). Khi không có anchor phù hợp, `NO_MATCH` **không được bịa anchor**; không cần tự tạo filename. Đã có anchor phải tồn tại duy nhất trong `knowledge/` của chương khi Projector chạy.
- Relation đã có và đúng → `KEEP`. Không có đích thật sự phù hợp → `NO_MATCH` (không ghi). Không chắc chắn/sai/ngoài chapter ownership → `REVIEW` và Prime xử lý; **chỉ `SET` vào field trống hoặc cặp còn thiếu hợp lệ** sau QA. Không ép quota, không dùng matching tên một mình.

## 3. Staging không tranh chấp / không tạo metadata ảo

Prime chọn `<RUN_ID>` duy nhất, tạo:

`<MODULE_ROOT>/build/step11/<RUN_ID>/_prime-manifest.yml` (chỉ Prime)

`<MODULE_ROOT>/build/step11/<RUN_ID>/_seed/<CHAPTER_KEY>/input.yml` (Prime ghi, Worker chỉ đọc)

`<MODULE_ROOT>/build/step11/<RUN_ID>/chapters/<CHAPTER_KEY>/proposal.yml` (**chỉ Worker owner ghi**)

`build/` đã được Git ignore; không nằm trong `src/main/resources` nên không đóng gói Swagger. Không xóa staging của session trước nếu chưa kiểm tra manifest, hash và trạng thái. `CHAPTER_KEY` do Prime chuẩn hóa từ chapter định danh đã xác minh, không cho path traversal.

`input.yml` chỉ chứa **dữ liệu chương được giao**: chapter/language IDs, filepath/source hashes, nội dung Knowledge (đúng chương), các question Quiz/Interview thuộc chương kèm identity/fingerprint, Controller/method thuộc chương và những relation hiện có; kèm **lookup chỉ-định-danh toàn module** cho Knowledge/anchor/API và module routeId để cân nhắc liên kết ngoài chương. **Không cấp toàn bộ nội dung chương khác, YAML file dùng chung, dữ liệu module khác, credentials, manifest hoặc chỉnh sửa của worker khác**. Nếu context thêm là cần thiết mà vượt ownership → Worker `REVIEW`, Prime thẩm định; không tự mở ngoài scope.

### Hợp đồng giao worker — đúng bốn dòng định vị

```text
<URL_TAB_DUNG_GID>
A1, B1, I3, A<PARENT>, B<PARENT>, P<PARENT>:S<PARENT>, T<PARENT>:W<PARENT>
READ: <ABSOLUTE_STEP11_SEED_CHAPTER_DIR> | WRITE: <ABSOLUTE_STEP11_CHAPTER_PROPOSAL_FILE>
/java-learning/module-generate-agent/worker/STEP_11_WORKER.md
```

Prime **không** gửi nội dung nguồn, gợi ý relation, nhận xét QA hay tóm tắt quy tắc. Worker tự đọc toàn văn file riêng và `input.yml` đã được Prime tách theo chương; tọa độ Sheet là định danh nguồn, **không trao quyền ghi Sheet**. Worker chỉ ghi file proposal của mình. Không cho worker sửa `_seed`, `_prime-manifest`, shared YAML, Google Sheets, Gradle hoặc source.

## 4. Worker làm một lượt, Prime nghiệm thu độc lập

1. Worker kiểm tra snapshot chapter/identity/hash; đọc Knowledge và nội dung API/Quiz/Interview **từ seed được cấp**, xem xét toàn bộ trường related hiện có. Mỗi item/field có đúng một quyết định `SET / KEEP / NO_MATCH / REVIEW` và lý do ngắn; `SET` có target cụ thể và bằng chứng liên quan. Không soạn lại content.
2. Worker ghi **duy nhất** `proposal.yml` theo format của `worker/STEP_11_WORKER.md`, parse/readback nguyên bản, báo số audited/SET/KEEP/NO_MATCH/REVIEW rồi `SLEEPING`. Prime không coi report là QA PASS.
3. Prime kiểm tra **tất cả** target trong seed có decision; không có item lạ/duplicate/outside ownership; giải quyết conflict của nhiều worker (ví dụ cùng Controller) và kiểm tra độc lập bằng source thật: file/anchor tồn tại, exact signature, `usage`, path/module/language, ý nghĩa mapping. Không ghi nếu thiếu source hoặc proposal chưa rõ.
4. Chỉ khi **toàn tab** xong, writer giải phóng, QA PASS và không còn blocker, Prime chuẩn bị **semantic diff chỉ các field related**, checksum trước/sau và rollback checkpoint. `NO_MATCH` là kết quả hợp lệ, `REVIEW` chưa giải quyết là blocker cho các field thuộc scope. Nếu chỉ thử một vài chương → báo `TRIAL_ONLY`, **không áp dụng snapshot toàn module/không cập nhật CURRENT_STEP.VALUE**.

## 5. Áp dụng — Prime ONLY, theo nguồn sở hữu

**Gate trước mọi APPLY:** Prime kiểm tra toàn bộ khả năng ghi nguồn (Sheet/Swagger/EN) **và khả năng bảo toàn Roadmap relation** đối với phạm vi STEP này. Nếu `BLOCKER: ROADMAP_RELATION_PERSISTENCE` hay thiếu cách ghi/rollback an toàn, **chỉ giữ proposal/read-only QA trong staging, không bắt đầu ghi từng phần vào Sheet hoặc module**. Không cố chạy task chưa tồn tại.

**VI Quiz / Interview: Google Sheets là source of truth.** Prime ghép proposal vào **đúng item** của YAML trong `T:W` / `P:S` hàng cha tương ứng; sửa *chỉ* `readmeRelated.anchor`/`apiRelated`, giữ nguyên từng scalar khác (`id/question/answer/answers/correctAnswerId`), thứ tự, count, nguồn người viết và metadata ô. Preflight entered value/formula, effective/formatted, note/validation/chips và fingerprint trước ghi. Ghi batch đúng ô thuộc ownership (chia lại ≤30.000 Unicode mỗi ô, ghép không separator), exact readback YAML đã sửa. Nếu thay đổi đồng thời/không bảo toàn → BLOCKER, không ghi mù. **Không sửa trực tiếp file `<chapter>/{quiz,interview}/question.yml`** làm nguồn chính.

- Chạy **module-local** `generateModuleInterviewFromSheetByLocation` và `generateModuleQuizFromSheetByLocation` từng task `-PsheetMode=DRY_RUN` → QA snapshot diff → `APPLY` khi an toàn; verify output/schema/count/relations exact. Không dùng `ByFiles` hay importer tổng hợp. Không giả định có `en` Sheet: các file EN do người biên soạn quản lý phải được thay **chỉ relation** với snapshot/ownership riêng, hoặc `NOT_APPLICABLE/REVIEW` khi không thể gán chapter an toàn.
- **Swagger VI/EN**: Prime cập nhật duy nhất `readmeRelated` ở `controller-description.yml` và `api-descriptions.yml` (theo language tương ứng); bảo toàn các key khác và tất cả 10 Swagger YAML ngoài phạm vi. Lập bản trước/sau, YAML parse/validate, QA target, kiểm tra hash nguồn vẫn khớp, backup file gốc, APPLY có rollback khi lỗi và exact readback. Không chạy lại `generateApiSwaggerDescription` sau merge; không chạm `api-execution`, `api-cases`, `api-params`.
- Sau Sheet+module+Swagger, validate projection/Portal relation bằng các validator hiện có; có thể **chạy task projection phù hợp để kiểm tra** nếu không động vào dữ liệu nguồn ngoài phạm vi. Warning unresolved link hoặc `usage:false` phải được phân loại chính xác, không coi warning là proof của link đúng.

### Roadmap là phase riêng và có blocker kỹ thuật hiện tại

Prime đọc `roadmap/vi|en/roadmap.yml`, Knowledge index và module catalog, kiểm tra/enrich `relatedKnowledge` và `relatedModules` ở mức milestone. **Không để worker chương sửa Roadmap**.

**Quan trọng:** `RoadmapSheetImportService.syncRoadmap` hiện **thay thế cả thư mục `content/roadmap/vi/`**, còn `convertRoadmap` chỉ dựng `id/title/purpose/objectives` từ F:H; không bảo toàn `relatedKnowledge/relatedModules` ở module YAML. Vì vậy **không APPLY Roadmap VI enrichment rồi tuyên bố bền vững**. Cần có cơ chế source/persistence được xác minh và kiểm thử (ví dụ bổ sung nguồn relation riêng hoặc importer hỗ trợ bảo toàn theo ID, với quyết định sở hữu rõ ràng). **STEP 11 chưa tự triển khai cơ chế đó**; nếu chưa có → `BLOCKER: ROADMAP_RELATION_PERSISTENCE`, không chạy Roadmap importer và **không nâng CURRENT_STEP.VALUE** khi Roadmap nằm trong phạm vi. Roadmap EN chỉ thay relation khi file có thật và ranh giới sở hữu/persistence đã được xác minh.

## 6. Resume, completion và báo cáo

- Sau gián đoạn, Prime đối chiếu manifest `RUN_ID`, source hash Sheet/repo, seed/proposal, trạng thái từng phase và snapshot đích **trước khi resume**. Không làm lại batch đã APPLY hay xóa staging khi chưa readback. Nếu đã cập nhật Sheet mà import module lỗi, giữ checkpoint/backup, khôi phục hoặc tiếp tục có đối chiếu, **không overwrite chỉnh sửa mới của người khác**.
- `COMPLETED` chỉ khi: toàn bộ Menu Cấp 1 thuộc scope đã audited, không `REVIEW` chưa giải quyết, relation `SET` đều exact target/correct semantics, source of truth không bị phá, VI và EN (nơi có khả năng áp dụng) QA, Roadmap phase persistence PASS hoặc `NOT_APPLICABLE` có căn cứ, importer module & Swagger readback PASS, các metadata/content khác nguyên vẹn, không còn writer/blocker. Sau đó Prime mới xác minh `CURRENT_STEP=Current STEP`, cập nhật `CURRENT_STEP.VALUE = "11" và đọc lại như `rules/PRIME_RULES.md`.
- Báo cáo: `MODULE_I3`, `TAB_GID`, `SESSION_WORKER_MODE`, `MAX_WORKERS`, `CHAPTERS_TOTAL/AUDITED`, `RELATIONS_AUDITED/SET/KEEP/NO_MATCH/REVIEW`, `LANGUAGES`, `SHEET_QA/SYNC`, `SWAGGER_MERGE`, `ROADMAP_PERSISTENCE`, `DIFF/PRESERVATION`, `READBACK`, `CURRENT_STEP.VALUE`, `BLOCKERS`, `GIT_STATUS`. Không tự commit/push.
