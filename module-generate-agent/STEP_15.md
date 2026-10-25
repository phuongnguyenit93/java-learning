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
2. Verify VI tab `I3` exact module root-relative path, `J2 = Current STEP` and
   `J3 = STEP 14`; verify STEP 12 offline VI Excel has exactly one worksheet,
   correct `I3` and final VI menu row mapping.
3. Validate the Google-native `Template`: 700+ rows, A:AB, Difficulty
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
| `publishModuleEnSheet` | Clone → sync → verify → J3 EN → export → J3 VI |

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
   `menu/<chapter>/{interview,quiz}/question.yml` của ngôn ngữ EN.
   Mỗi câu hỏi chỉ lưu `readmeRelated.anchor`; chapter được xác định bằng
   vị trí file, không dựa vào `readmeRelated.file`.
3. Translate **fixed template labels only** using literal code mappings and
   clear VI-only authoring prompts. No freeform AI translation.
4. Upload only `userEnteredValue` with the Google Sheets API, preserving
   formatting, metadata, notes, validation, merged cells and conditional
   coloring inherited from Template. Không ghi/đè **nội dung Video X20:AA các hàng chương**; header Video Script tại X19:AA19 vẫn là nhãn schema. Leave J3 untouched until validation.

## 5. Verify, mark status, export

1. Compare exact uploaded cell values (including blanks, booleans, numeric
   values) via Google API readback; incomplete batches or unexpected cells
   are blockers. Recheck source SHA-256 and EN I3/title.
2. After all data and native formatting PASS, set/readback
   `EN J3 = STEP 15` while ensuring `J2 = Current STEP`.
3. Export **the official EN gid** using `ModuleSheetExcelExportService` into
   `<MODULE>/content/sheet/en/module.xlsx`. It must have one worksheet and
   `J3 = STEP 15`. Do not use an intermediary workbook as final output.
4. Persist phase `EXPORTED`, then set/readback `VI J3 = STEP 15` as the final
   update. If that update fails, keep the completed EN tab/Excel and report a
   resumable blocker; never duplicate again.

## 6. Recovery, completion gate and handoff

- Persistent state phases: `CLONED`, `SYNCED`, `VERIFIED`, `EXPORTED`.
- Same source and already published? Return `ALREADY_PUBLISHED` without clone.
- Source content hash changed? BLOCKER, no silent refresh of the EN tab.
- Network/upload crash? Inspect live gid and cells; retry same gid and
  overwrite **only the approved values**, never clone a replacement.
- Missing metadata / unavailable template format / schema or mapping error?
  BLOCKER, no AI-generated workaround.

**COMPLETED only if** EN gid and persistent mapping agree; Template is intact;
all mapped values and native dropdowns/color rules pass; `J3 VI` and `J3 EN`
read back STEP 15; final exported EN Excel is the snapshot of official EN gid,
one sheet with J3 STEP 15. All changes remain module-local except the two
authorized J3 Sheet cells. Do not commit/push; hand off to STEP_FINAL.

**Video optional sau FINAL:** `STEP_OPTIONAL_VIDEO` tạo Video VI; `STEP_OPTIONAL_VIDEO_TRANSLATE` dịch EN và gọi `publishOptionalVideoEn` để **chỉ** đồng bộ X:AA của tab EN hiện hữu rồi export Excel EN. Không chạy lại `publishModuleEnSheet` hoặc sửa J3 để cập nhật Video.

Final report:
`MODULE_I3, SPREADSHEET_ID, VI_GID, EN_GID, EN_TAB, TEMPLATE_QA,
SOURCE_SHA256, CLONE, SYNC, CELL_READBACK, FORMAT_QA, J3_VI,
J3_EN, XLSX_OUTPUT, XLSX_J3, RESUME_STATUS, BLOCKER`.
