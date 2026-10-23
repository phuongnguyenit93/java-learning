# STEP 8 — PRIME ONLY: Generate Interview Q&A YAML theo từng Menu Cấp 1

## 0. Mục tiêu

STEP 8 hiện là bước **sinh nội dung phỏng vấn / hệ thống hóa kiến thức** trên Google Sheets, **KHÔNG phải** bước đặt filename (đã gộp vào STEP 5). **Video hiện thuộc STEP 10** trong workflow `temp/`.

- **Một worker = một Menu Cấp 1** (một chương); worker đọc toàn văn Knowledge của các Menu Cấp 2 trong chương, tự thiết kế **câu hỏi thực tế cùng câu trả lời tham khảo** rồi ghi **MỘT YAML hoàn chỉnh** vào tối đa **4 ô P:S trên CHÍNH hàng Menu Cấp 1**.
- YAML chỉ chứa `questions:` với các phần tử `question:` và `answer:` (câu trả lời là YAML string, có thể chứa README Markdown trong block `|-`); **không có** `aiGenerated`, `reviewed`, `readmeRelated`, `apiRelated` hoặc các field khác ở STEP 8.
- **Worker STEP 8 tạo mới chỉ YAML nguồn, KHÔNG tự bổ sung defaults/related, chỉnh module hoặc gọi importer.** Ngoại lệ: nếu YAML **đã tồn tại** nhưng lỗi cú pháp, worker được `FORMAT_FIX` mà **giữ nguyên tất cả fields kể cả metadata đã Normalize**, không tạo lại từ đầu. Sau khi **TOÀN BỘ worker STEP 8 của tab hoàn tất và Prime QA PASS**, Prime **phải chạy Gradle root task** `normalizeInterviewQuestionsFromSheet` để code thuần parse YAML P:S, bổ sung default fields/`readmeRelated.file` và **ghi lại YAML chuẩn hóa cùng P:S**. **Sau Normalize, chính Prime chạy tiếp task module-local `generateModuleInterviewFromSheetByLocation`** để Groovy thuần xuất `question.yml` cho đúng module. **Cả hai thao tác là nhiệm vụ Prime, không giao cho worker.** AI enrichment cho anchor/API có thể thực hiện sau này.
- Mỗi worker làm đủ **một lượt**: đọc → soạn YAML → QA → chia 4 ô → preflight → ghi → exact readback → báo Prime → SLEEPING. Không có PHA A/B hoặc hàng Interview riêng.
- Pool theo **Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; tính cả ACTIVE + SLEEPING)**; phải tái sử dụng worker SLEEPING trước và spawn từng worker một.

### Prime/Worker và nguồn quy định

- **`STEP_8.md` này chỉ dành cho Prime**. Prime đọc `/java-learning/module-generate-agent/temp/GENERAL_AGENT_RULES.md`, **liệt kê và đọc tất cả file `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn bộ `STEP_8.md` và `worker/STEP_8_WORKER.md` trước khi giao; đọc lại sau mất context.
- **Worker bắt buộc TỰ đọc TOÀN VĂN duy nhất `/java-learning/module-generate-agent/temp/worker/STEP_8_WORKER.md`** (kể cả sau compaction). Worker không đọc tài liệu Prime, STEP khác, rule/TXT, repo hay lịch sử chat để thay cho nhiệm vụ.
- Prime **chỉ gửi đúng 4 dòng**: URL tab, tọa độ nguồn, tọa độ 4 ô đích, đường dẫn file worker-only. **Không gửi nội dung Knowledge, mẫu câu hỏi, tiêu chí chất lượng, tóm tắt worker file hoặc filename đề xuất**.

## 1. Mapping Sheet

| Thành phần | Vùng | Quyền |
| --- | --- | --- |
| Định danh module | `A1`, `B1` | Chỉ đọc |
| Tiêu đề Menu Cấp 1 | `A<PARENT_ROW>` | Chỉ đọc; chính là chủ đề Interview |
| Filename của chương | `B<PARENT_ROW>` | Chỉ đọc; đã được STEP 5 điền |
| Danh sách Menu Cấp 2 | `B<CHILD_ROWS>` | Chỉ đọc; Prime xác định đúng các hàng con |
| Nội dung từng bài Knowledge | `C<CHILD_ROWS>:N<CHILD_ROWS>` | Chỉ đọc; ghép C→N trên **từng hàng con**, không có ký tự nối |
| Difficulty từng bài (nếu cần ngữ cảnh) | `O<CHILD_ROWS>` | Chỉ đọc, không ảnh hưởng ràng buộc phân bố câu hỏi |
| **Interview YAML** | **`P<PARENT_ROW>:S<PARENT_ROW>`** | **Chỉ worker sở hữu chương được ghi; tối đa 4 ô, 30.000 Unicode/ô** |

Schema Template mới: `A19 = Menu Cấp 1`, `B19 = Menu Cấp 2`, `C19:N19 = Câu trả lời`, `O19 = Difficulty`, **`P18 = Interview`, `P19:S19 = Interview YAML`**, `T18 = Quiz`, `T19:W19 = Quiz YAML`, `X18 = Video`, `X19:AA19 = Video Script`. Template phải có tối thiểu cột AA. **Header legacy `Interview JSON` trên cả bốn ô được importer/Normalize chấp nhận để không phá dữ liệu cũ**, nhưng Prime phải báo `LEGACY_INTERVIEW_HEADER`; không đổi header hoặc dữ liệu của tab trong STEP 8. Thiếu P:S hoặc header không hợp lệ/mixed → `BLOCKER: INTERVIEW SCHEMA`. Khi cập nhật Template dùng nhãn `Interview YAML`.

**Ví dụ mapping:** A20 (chương thứ nhất) → P20:S20, các bài B21:B28 + C21:N28; A29 (chương tiếp theo) → P29:S29. **Không dùng số thứ tự chương để suy ra hàng khác, không dùng hàng con để ghi Interview**, không đụng Quiz T:W hay Video X:AA.

## 2. Prime điều phối

1. Đọc mapping tên module → gid từ `module-generate-info.txt`, xác nhận URL tab và `A1/B1`; không nhầm spreadsheet ID với gid. Đọc Menu A/B để nhận diện **mọi hàng cha** (A có tiêu đề, B cùng hàng là filename .md hoặc trống bất thường) cùng tất cả hàng con B; bảo toàn đúng ownership và thứ tự. Filename B cha phải hợp lệ theo STEP 5. Nếu tên/menu cấu trúc sai → `BLOCKER`.
2. Preflight Template/tab schema và **C:N trên toàn bộ hàng con** của chương sắp giao: dữ liệu không thiếu hoặc không liên tục, các bài có nội dung thực, không tạo câu hỏi chỉ từ tiêu đề. Nếu thiếu nguồn → `BLOCKER: INCOMPLETE KNOWLEDGE`.
3. Preflight **cả 4 ô P:S trên hàng cha** gồm `userEnteredValue`/formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns` và quyền sở hữu. Trống hết → giao tạo mới (`NEW_FILL`). Có YAML gốc/đã Normalize từ trước và hợp lệ → `UNCHANGED`, không ghi. Có **lỗi cú pháp/định dạng YAML hoặc Markdown nhúng thật sự** → có thể giao worker `FORMAT_FIX` tối thiểu trong đúng P:S, với snapshot/diff chứng minh **câu hỏi, đáp án, metadata và thứ tự không đổi**. Không giao sửa nội dung hoặc tạo lại YAML đã Normalize; không chứng minh bảo toàn → BLOCKER. Không coi 4 ô Interview trên hàng con là hợp lệ.
4. Quản lý pool **theo Mode A/B của session** (`PRIME_RULES.md §0`); **reuse sleeping** trước, spawn tuần tự khi cần, giao mỗi worker đúng một chương, không giao trùng `P:S` cùng hàng; giữ ownership đến khi worker báo cáo/readback xong. Cho phép các chương độc lập chạy song song.
5. Prime **chỉ định tọa độ thực, không soạn nội dung**. Sau worker, đọc lại đủ 4 ô P:S, ghép **P→S không separator**, YAML parse được, root đúng `questions`, danh sách không trống; `NEW_FILL` có mỗi item đúng 2 trường string `question` và `answer` chưa metadata/related; **`FORMAT_FIX` với YAML đã Normalize được giữ nguyên các field/giá trị metadata hiện hữu**, không ép về 2 field. Đối chiếu diff trước–sau, bảo toàn mọi nội dung và exact readback bản sửa. `UNCHANGED` chỉ khi QA PASS mà không write. QA tính phù hợp với Knowledge/chương và tình huống phỏng vấn/ôn tập.
6. Theo dõi `PARENT_CELL → CHILD_ROWS → OUTPUT_RANGE_P_S → OWNER_WORKER → STATUS → READBACK → QA`. Tiếp tục tất cả chương trong scope đã giao, xử lý blocker theo rule ownership, không dừng sau batch đầu. **Khi toàn tab đạt PASS ở phase worker, chạy NGAY task code Normalize bên dưới; sau Normalize APPLY/readback PASS, BẮT BUỘC tiếp tục task Interview ByLocation của đúng module (DRY_RUN → APPLY)** theo `GENERAL_AGENT_RULES.md §6`. **Chỉ sau khi CẢ HAI task PASS** và Prime kiểm tra `J2 = Current STEP`, preflight/readback theo PRIME_RULES mới cập nhật `J3 = STEP 8`. Nếu chỉ thử nghiệm vài chương, không chạy APPLY normalize/import toàn tab và **không đổi J3**. Không tự chạy STEP 10 hoặc task ByFiles.

### 3. Prime chạy root Gradle Normalize — BẮT BUỘC khi kết thúc toàn STEP 8

Task đã đăng ký tại root repository `java-learning`: **`normalizeInterviewQuestionsFromSheet`**.

- **Hai input**: `-PfileUrl=<URL spreadsheet>`, `-PsheetID=<gid tab>` (khác spreadsheet ID). Task cũng hỗ trợ alias `-PsheetFileUrl` và `-PsheetId`. Prime lấy hai giá trị từ mapping đã xác minh, không hardcode ví dụ.
- **Credentials**: `DRY_RUN` chỉ cần Google Sheets **scope `spreadsheets.readonly`**; `APPLY` dùng ADC/Service Account Impersonation với **scope `spreadsheets`** để ghi dữ liệu và file Sheet phải share Service Account **quyền Editor**. Những task import cũ tiếp tục chỉ đọc. Nếu chưa có quyền hoặc credentials không đạt, `BLOCKER: INTERVIEW NORMALIZE AUTH`, không báo hoàn thành.
- Lần 1 (chỉ kiểm tra và báo số chương/câu hỏi sẽ đổi, không ghi):

```powershell
.\gradlew.bat normalizeInterviewQuestionsFromSheet -PfileUrl="<URL_GOOGLE_SHEETS>" -PsheetID=<GID_TAB> -PinterviewMode=DRY_RUN
```

- **Sau khi DRY_RUN PASS**, lần 2 thực sự ghi **ngược về chính Sheet/tab đó**:

```powershell
.\gradlew.bat normalizeInterviewQuestionsFromSheet -PfileUrl="<URL_GOOGLE_SHEETS>" -PsheetID=<GID_TAB> -PinterviewMode=APPLY
```

- Task Normalize tìm **các hàng Menu Cấp 1 có YAML ở P:S**, nối P→S không separator, parse `questions`; với từng question/answer thêm `aiGenerated:true`, `reviewed:false`, `readmeRelated:{file:"<index>.<stem>/<filename>",anchor:""}`, `apiRelated:{controller:"",methodSignature:""}`. **Nếu metadata hợp lệ đã tồn tại, giữ nguyên** (`reviewed`, anchor, API); nếu `readmeRelated.file` xung đột với đường dẫn chapter từ A/B, **BLOCKER chứ không ghi đè**.
- **Riêng task Normalize** không gọi AI và không ghi file module; nó đọc YAML nguồn (hoặc JSON legacy), **xuất YAML chuẩn hóa** về đúng bốn ô P:S hàng cha, tự chia 30.000 Unicode/ô. **Task ByLocation ngay sau đó** mới sinh `content/interview/vi/question.yml` chỉ từ Sheet đã normalize.
- Toàn tab được preflight đầy đủ trước **một batch ghi duy nhất**, đọc lại sau ghi và so khớp từng mảnh YAML; nếu có lỗi parse, vượt 4 ô, menu không hợp lệ hoặc nguồn bị chỉnh cùng lúc, **không dispatch batch**. Chạy lại với cùng trạng thái là **idempotent** (`UNCHANGED`), không nhân đôi câu hỏi.
- Prime ghi nhận **`NORMALIZE_STATUS`, `NORMALIZED_ROWS`, `QUESTION_COUNT`, `READBACK`**; **Normalize không tự tạo file module và chưa đủ điều kiện hoàn tất STEP 8**. Nếu chưa có YAML hoặc bất kỳ chương nào chưa được worker QA, chặn `BLOCKER: INCOMPLETE STEP 8`; không chạy APPLY để tránh chuẩn hóa một phần rồi báo hoàn tất.

### 4. Prime đồng bộ Interview YAML đã Normalize về ĐÚNG một module

**Ngay sau khi Normalize APPLY/readback PASS**, Prime **BẮT BUỘC** chạy task **module-local** `generateModuleInterviewFromSheetByLocation` (đã đăng ký cho module có Sheets plugin), **không dùng `generateModuleContentFromSheetByFiles` hoặc task tổng hợp**. Prime xác minh `I3` bằng module path Gradle đã đăng ký và extension `fileUrl/sheetId` đúng tab. Từ root `java-learning`:

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleInterviewFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleInterviewFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
```

- Chỉ chạy lệnh thứ hai khi DRY_RUN có dữ liệu hợp lệ, exit code PASS, không có blocker; `SKIPPED_NO_INTERVIEW_SCHEMA`, `SKIPPED_EMPTY_INTERVIEW` hoặc status `SKIPPED_*` **không phải sync thành công**.
- Kiểm tra sau APPLY: status thực, số câu hỏi/chương, chính xác file `<MODULE_ROOT>/content/interview/vi/question.yml` đã sinh/giữ nguyên hợp lệ, đúng module/tab, có schema hợp lệ và related file đúng filename của từng chương. Worker không chạy Gradle, không chỉnh module.
- Nếu thiếu Gradle module/task/config/ADC, `I3` mismatch, lỗi schema hoặc file chưa cập nhật: **`BLOCKER: MODULE INTERVIEW SYNC`**, báo trạng thái Normalize đã làm (không thử lại ghi Sheet mù) và **không cập nhật J3**. Chỉ sau **Normalize + module-local Interview import PASS/UNCHANGED được xác minh**, Prime cập nhật/readback `J3 = STEP 8`. Báo cả `NORMALIZE_STATUS` và `MODULE_SYNC_STATUS` riêng.

### Mẫu tin nhắn Prime giao worker — CHỈ bốn dòng

```text
<URL_TAB_ĐÚNG_GID>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>, <CHILD_KNOWLEDGE_C_TO_N_RANGE>, <CHILD_DIFFICULTY_O_RANGE>
<OUTPUT_INTERVIEW_P_TO_S_CÙNG_HÀNG_PARENT>
/java-learning/module-generate-agent/temp/worker/STEP_8_WORKER.md
```

Thay placeholder bằng địa chỉ thực, không gửi dấu `...` hay ranges có thể bao gồm chương ngoài nhiệm vụ. Nếu các hàng con không liền nhau, liệt kê chính xác các ô trong dòng nguồn. **Prime không chép nội dung từ worker-only vào tin giao; worker phải tự đọc file này**.

## 3. Điều kiện hoàn thành

- Mỗi chương đã được worker độc lập đọc **đủ Knowledge**, tạo một **YAML list câu hỏi–trả lời có giá trị thực tế**, không cố nhồi câu hỏi vô dụng hoặc ép số lượng theo bài.
- **Chỉ đúng `P:S` của hàng cha**, mỗi ô `<= 30.000` Unicode code points; cả YAML tối đa 120.000; không truncate, không ghi một phần nếu vượt 4 ô (`BLOCKER: INTERVIEW CAPACITY`).
- **Worker** viết YAML gốc chỉ có `question` và `answer`; sau QA toàn tab, **Prime chạy task code** bổ sung defaults/related file **và exact readback chính YAML đầy đủ trên Sheet**, không phải worker làm.
- A/B, Knowledge C:N, Difficulty O, Video X:AA, Template, tab khác, source/repo và J3 của worker đều giữ nguyên.
- **STEP 8 hoàn thành = mọi worker YAML PASS + root Normalize DRY_RUN/APPLY/readback PASS + `generateModuleInterviewFromSheetByLocation` DRY_RUN/APPLY/QA trên ĐÚNG module PASS + J3 exact readback PASS**. Filename vẫn thuộc STEP 5, STEP 10 vẫn là Video. Không chạy ByFiles, task tổng hợp, STEP 10 hoặc commit/push.
