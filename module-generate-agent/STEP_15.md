# STEP 15 — PRIME ONLY: Publish module EN to Google Sheets + Excel




## 0. Hard rule: code-only, no AI, no workers

STEP 15 is **entirely deterministic**: Gradle/Groovy, Google Sheets API and
Apache POI. No worker, AI translation, paraphrasing, relation inference,
content editing or manual copy-paste is permitted. If mapping or validation is
ambiguous, stop as BLOCKER; do not invent data.

**One module per Gradle task invocation.** After STEP 14 passes QA, use:

- The existing `Template` Google Sheet tab for native formatting, dropdowns,
  row sizing and conditional colors.
- STEP 12 `content/sheet/vi/module.xlsx` for the exact chapter and lesson rows.
- Authored module EN YAML/Markdown from STEP 14 for actual English content.
- The same spreadsheet as VI; EN gets a separate tab and a stable gid.

Do not touch Java code/tests, module EN content, VI content, the Template tab,
other modules, Portal generated data or other Google spreadsheets. Swagger
and non-column Roadmap relations remain canonical in module EN YAML rather
than being pushed into Sheet cells.

## 1. Prime preflight

1. Read `GENERAL_AGENT_RULES.md`, all `rules/`, STEP 15 and current module-info
   mapping. Confirm STEP 14 fully completed and readback on the VI gid.
2. Verify VI tab `I3` exact module root-relative path, `CURRENT_STEP.VALUE = "14"` in module `master.json`; verify STEP 12 offline VI Excel has exactly one worksheet,
   correct `I3` and final VI menu row mapping.
3. Validate the Google-native `Template`: 700+ rows, **at least A:AC (29 columns)** (extra legacy columns are preserved), Difficulty
   dropdown BASIC/INTERMEDIATE/ADVANCED at O20/O21/O700, isImportant
   dropdown TRUE/FALSE at AB20/AB700, and three conditional coloring rules
   for BASIC/INTERMEDIATE/ADVANCED on O20:O700.
4. Ensure no concurrent writer. Read any existing persistent EN gid mapping;
   check conflicts with actual Google tabs. Check service account API scopes.

## 2. Tasks — no content generation

Run from `java-learning` root; supply the exact module Gradle project path
and the spreadsheet URL / VI gid from current module-info.

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleEnSheet "-PfileUrl=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -PviGid=<VI_GID> -PsheetMode=DRY_RUN --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleEnSheet "-PfileUrl=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -PviGid=<VI_GID> -PsheetMode=APPLY --console=plain
```

`publishModuleEnSheet` orchestrates five independently callable tasks:

| Task | Responsibility |
| --- | --- |
| `cloneModuleTemplateToEnTab` | Duplicate Template to `<VI TITLE> EN`; bind B1/I3; persist EN gid |
| `syncModuleEnToSheet` | Render module EN via VI Excel row layout and upload **values only** |
| `validateModuleEnSheet` | Exact typed cell readback vs prepared upload workbook |
| `exportPublishedEnSheet` | Export verified EN gid to Excel EN |
| `publishModuleEnSheet` | Clone → sync → verify → export EN; Prime cập nhật tiến độ trong `master.json` sau QA |

All default to `DRY_RUN`; only `APPLY` mutates Sheets or local files.

## 3. Clone first, retain EN_GID

1. Compute a deterministic positive 31-bit EN gid from spreadsheet ID and
   exact module location. Use `DuplicateSheetRequest` on the **Template gid**,
   with explicit `newSheetId` and `newSheetName` = `<VI TITLE> EN`.
2. Save the mapping immediately to
   `<MODULE>/content/sheet/en/publish-state.json` (not Git-ignored). It stores
   spreadsheet ID, VI_GID, EN_GID, exact module, EN tab title, source SHA-256
   and phase; **no credentials**.
3. On resume, check the live gid/title/I3. If duplicate succeeded but the
   local checkpoint failed, recover via deterministic gid and exact title,
   not another duplicate. Collision/foreign tab is BLOCKER.
4. Write only B1/I3 on the new tab, verify exact readback; check cloned native
   dropdowns/colors. Do not modify Template or VI here.

## 4. Populate code-only

1. Build an intermediary `<MODULE>/build/step15/en-upload.xlsx` using
   `ModuleContentExcelSyncService` **forced to use VI snapshot as baseline**,
   never stale EN Excel.
2. Map introduction B17, Roadmap F:H, Menu A/B, Knowledge C:N, difficulty O,
   Interview P:S, Quiz T:W, Importance AB. **Bỏ qua Video X:AA trong publish bắt buộc.** Use source VI row
   identities; fail on lost chapter ownership, mismatched section count, long
   text or invalid YAML.
   Đọc `menu/<chapter>/knowledge/<file>.md`, metadata tại
   `menu/<chapter>/metadata/knowledge-metadata.yml`, và Interview/Quiz tại
   `menu/<chapter>/questions/{interview,summary}.yml` và `menu/<chapter>/quiz/question.yml` của ngôn ngữ EN.
   Riêng Interview P:S, code ghép `questions/interview.yml.questions[]` thành root
   `questions[]` và `questions/summary.yml.questions[]` thành root
   `summaryQuestions[]` trong **một YAML** trước khi chia bốn ô.
   Không AI, không tạo Summary nếu nguồn không có; không làm mất hai nhóm khi round-trip.
   Mỗi câu hỏi chỉ lưu `readmeRelated.anchor`; chapter được xác định bằng
   vị trí file, không dựa vào `readmeRelated.file`.
   **Summary:** đọc đầy đủ `menu/<chapter>/summary/section.yml` EN, validate
   root `version/type/title/roadmapHeading/roadmap/comparisonMarkdown`, lesson
   ID/order/transition và bảng GFM; **ghi toàn bộ chuỗi YAML không chuyển đổi vào
   đúng `AC<PARENT>`**. Đọc `summaries.yml`, kiểm tra mỗi `summaries[].id`
   khớp bài/ID theo thứ tự, rồi ghi `summaries[].summary` Markdown vào đúng
   `AC<CHILD>`. Header `AC19` phải là `Summary YAML`. Không tái dựng
   Markdown chương từ YAML, không re-serialize `section.yml` và không dùng AI.
3. Translate **fixed template labels only** using literal code mappings and
   clear VI-only authoring prompts. No freeform AI translation.
4. Upload only `userEnteredValue` with the Google Sheets API, preserving
   formatting, metadata, notes, validation, merged cells and conditional
   coloring inherited from Template. Không ghi/đè **nội dung Video X20:AA các hàng chương**; header Video Script tại X19:AA19 vẫn là nhãn schema. Leave CURRENT_STEP.VALUE untouched until validation.

## 5. Verify, export, and update master.json

1. Verify the uploaded EN content and all native Sheet formatting. Confirm the exact VI gid/I3, no drift in source hashes, and no conflicts.
   Exact readback phải chứng minh mỗi `AC<PARENT>` **bằng đúng chuỗi UTF-8 của
   `section.yml` EN** (bao gồm indent, `comparisonMarkdown` và line breaks),
   mỗi `AC<CHILD>` bằng Summary Markdown trong `summaries.yml`, và `AC19=Summary YAML`.
2. Export the verified official EN gid to `<MODULE>/content/sheet/en/module.xlsx`. Verify one worksheet and exact content readback. Do not require any STEP value in the workbook or Google Sheets.
3. Persist phase `EXPORTED` in `content/sheet/en/publish-state.json`. The Gradle publisher **never reads or writes J2/J3**. It checks the module `master.json` has `CURRENT_STEP.VALUE = "14"` (or 15/FINAL for safe published replay).
4. After **all** STEP 15 gates PASS, Prime alone updates `CURRENT_STEP.VALUE = "15"` in the verified module `master.json` and exact readback. If this update fails, the EN Sheet/Excel remains valid and can be resumed using the recorded state; do not duplicate the tab.

## 6. Recovery, completion gate and handoff

- Persistent state phases: `CLONED`, `SYNCED`, `VERIFIED`, `EXPORTED`.
- Same source and already published? Return `ALREADY_PUBLISHED` without clone.
- Source content hash changed? BLOCKER, no silent refresh of the EN tab.
- Network/upload crash? Inspect live gid and cells; retry same gid and
  overwrite **only the approved values**, never clone a replacement.
- Missing metadata / unavailable template format / schema or mapping error?
  BLOCKER, no AI-generated workaround.

**COMPLETED only if** the EN gid/persistent mapping agree, Template is unchanged, all translated values and native formatting pass exact readback, the official EN Excel has one valid worksheet, and Prime verifies `CURRENT_STEP.VALUE = "15"` in the *module master.json*. No Sheet cell is a completion marker. The only Sheet writes are the authorized EN content and tab initialization. Do not commit/push; hand off to STEP_FINAL.

**Video optional sau FINAL:** `STEP_OPTIONAL_VIDEO` tạo Video VI; `STEP_OPTIONAL_VIDEO_TRANSLATE` dịch EN và gọi `publishOptionalVideoEn` để **chỉ** đồng bộ X:AA của tab EN hiện hữu rồi export Excel EN. Không chạy lại `publishModuleEnSheet` hoặc sửa CURRENT_STEP.VALUE để cập nhật Video.

Final report:
`MODULE_I3, SPREADSHEET_ID, VI_GID, EN_GID, EN_TAB, TEMPLATE_QA,
SOURCE_SHA256, CLONE, SYNC, CELL_READBACK, FORMAT_QA, CURRENT_STEP_MASTER_BEFORE, CURRENT_STEP_MASTER_AFTER, XLSX_OUTPUT, RESUME_STATUS, BLOCKER`.
