# STEP 12 — PRIME ONLY: canonical module → offline XLSX (VI / EN)

> STEP 12 is the **last required numbered step**. Historical STEP 13/14/15
> (Sheet snapshot, translation QA and Google Sheets publishing) are retired.
> This STEP performs **offline XLSX export only**. No Google Sheets API,
> online tab deletion, online publish or Excel→module importer belongs to
> STEP 12. STEP_FINAL owns online tab cleanup after its own completion gates.

## 1. Source of truth and contract

- Run exactly **one module-local Gradle task**: `generateModuleExcel`.
- All content comes from the selected module's authored YAML/Markdown,
  `LIST.md`, Knowledge metadata and `master.json`. Google Sheets and an
  existing XLSX are **not** sources, even for row positions or formatting.
- `generateModuleExcel { languageList = ['vi', 'en'] }` is the optional
  module-local extension. Empty `languageList` means all values declared by
  `master.json.MODULE_LANGUAGE.VALUE`; a non-empty list selects only those
  declared languages, without duplicates. **One workbook per language**.
- `DRY_RUN` validates and renders in memory, leaving all outputs unchanged.
  `APPLY` preflights **all selected languages**, stages files, reopens through
  Apache POI, validates content and atomically replaces their destinations
  with best-effort transactional rollback. Existing outputs are not used as
  input. If one language fails, do not publish a partial collection.
- Output: `<module>/content/sheet/<lang>/module.xlsx`, exactly one worksheet
  per workbook; the worksheet name comes from SERVICE_NAME (Excel-safe).
  Excel files are ignored by Git by the existing `.gitignore` rule.
- `B1 = master.json.SERVICE_NAME.VALUE`; `I3` is the canonical root-relative
  module path. `J3` is **never** a progress marker; only Prime can checkpoint
  `master.json.CURRENT_STEP.VALUE`. `AB` is reserved and empty; do not revive
  `isImportant`. `AC` belongs to Summary.
- All worksheet rows are fixed at **45 pt (about 60 px)** and all columns
  `A:AC` at **32 Excel character units (about 230 px)**. Both header/body
  styles disable `wrapText`: long text is **visually clipped/overflow-limited**
  instead of increasing row height. Never truncate the actual cell string;
  clicking/selecting a cell must still expose its complete Markdown/YAML.
- Do **not** freeze or split any row/column pane; the entire worksheet must
  scroll normally, including the header and menu columns.
- Never change VI/EN YAML/Markdown, Java/test, Swagger, Portal, Google Sheets,
  other modules or credentials. No workers or AI generation in export.

## 2. One task, twelve internal services

| Phase | Service | Worksheet/data |
| --- | --- | --- |
| 1 | `ExcelWorkbookService` | New empty Apache POI `XSSFWorkbook` |
| 2 | `ExcelHeaderService` | A1/B1, ROAD 1/2, module I3, static labels, Difficulty validation, AB reserved |
| 3 | `ExcelRoadmapService` | `content/roadmap/<lang>/roadmap.yml` → F3:H |
| 4 | `ExcelReferenceService` | `content/reference/<lang>/references.yml` → L3 |
| 5 | `ExcelIntroductionService` | `content/readme/docs/<lang>/BASE.md` → B17 |
| 6 | `ExcelKnowledgeService` | Chapter/lesson order from `LIST.md`; A/B/C-parent; A/B/C:N child; O child Difficulty |
| 7 | `ExcelSummaryService` | Full authored `section.yml` at AC-parent; `summaries.yml` Markdown at AC-child |
| 8 | `ExcelInterviewService` | Canonical module-wide Interview → P20:R24 and answers P27:R by group |
| 9 | `ExcelCaseStudyService` | Canonical module-wide Case Study → S20:U24 and codes S27:U by group |
| 10 | `ExcelQuizService` | Canonical module-wide Quiz → V20:W24 |
| 11 | `ExcelVideoService` | X:AA headers remain; data intentionally blank until a separate implementation is approved |
| 12 | `ExcelValidationService` | Excel/schema/identity/round-trip/readback/atomic save |

The task coordinates the services in this order, once per selected language.
They are not registered as separate Gradle tasks.

### Knowledge/assessment ownership

- The menu starts on row 20. Every chapter has one A-parent title,
  B-parent chapter file name and C-parent base ID, followed by its ordered
  Knowledge A-child stable ID / B-child heading / C:N content / O difficulty.
  Do not assign new IDs or infer links by keyword.
- Interview, Case Study and Quiz are **module-wide** files at
  `content/{interview,casestudy,quiz}/<lang>/*.yml`, **not** the old
  `menu/<chapter>/questions/` / `menu/<chapter>/quiz/` formats.
- Main blocks preserve `groupID`, `groupName`, item order, `knowledge`,
  `resolve`, options/explanations, correctAnswerId and
  `readmeRelated.anchorList`. Interview `answer` and Case Study `code`
  are represented separately in Phase 4 blocks:
  `P/S(27 + 3*(groupID-1))` for three rows and three columns per group.
  Quiz has no Phase 4.
- `P25:R26`, `S25:U26` and `V25:W26` were authoring-only Phase 1
  staging; they **remain blank** because canonical module data does not
  include those intermediate drafts.
- Every multi-cell payload uses **MAX-FILL**, row-first, left-to-right;
  each cell contains the longest prefix obeying both <=30,000 Unicode
  code points and <=32,767 UTF-16 code units. Concatenation uses **no**
  separators. Overflow is a BLOCKER: never truncate, split a surrogate pair
  or silently skip a field. The assessment reader must reconstruct all I/C/Q
  canonical groups exactly, including linked answers/code.
- Roadmap relations not representable in F:H and Swagger metadata remain
  canonical YAML; Excel is a *projection*, not a reverse-import source.

## 3. QA gates and execution

Prime first verifies STEP 11 translation is fully reviewed, the VI/EN file
inventory and semantic QA pass, existing Knowledge IDs and technical
relations resolve, no writer is active, the selected module is valid and
`CURRENT_STEP.VALUE = "11"` (or an explicitly verified higher historical
checkpoint for guarded rerun). **Never** re-clone VI→EN after translation.
Existing valid relations are KEEP; ambiguous links must be reviewed in
their owning stage, not guessed during export.

The module's Gradle build may configure:

```groovy
generateModuleExcel {
    languageList = ['vi', 'en']
}
```

An auto-generated `task.gradle` is a *reference only* and is not executed.
The extension can be left empty to export all declared module languages.

Run from the repository root against the exact module Gradle path:

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:generateModuleExcel -PexcelMode=DRY_RUN --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:generateModuleExcel -PexcelMode=APPLY --console=plain
```

Use JDK 21 for the current Gradle 8.5 build. The XLSX exporter does not
require a Sheet URL, gid, credentials or Google API calls. Never run the historical
`exportModuleSheetToExcel`, `publishModuleEnSheet`,
`syncModuleContentToExcel`, or `importExcelFromModuleSheet` as STEP 12.

### Required validation

1. Exactly one worksheet per language, valid `B1`, `I3`, fixed schema,
   `AB` blank, `J3` blank, `X:AA` Video data blank.
2. Knowledge chapter count/order, lesson IDs, titles, C:N content,
   metadata difficulty and Summary parent/child mapping agree with module.
3. Roadmap, Reference and Introduction preserve authored content and order.
4. Parse/recombine all five-row main I/C/Q blocks and all three-row Phase 4
   answer/code blocks. Reconstruct canonical grouping by groupID and item
   index and compare the recovered YAML model to each module source.
5. Inspect text encodings, workbook validity, character limits and basic
   layout/dropdown. Verify the reopened staged workbook before atomic replace.
6. Verify output hashes and no changes to any source, previous translation,
   Google Sheet, master.json or unrelated files **during the XLSX export**.
   No unsupported SKIP counts.

Only after all selected-language outputs and **independent Prime VI/EN semantic
QA** pass may Prime change **only**
`<module>/master.json.CURRENT_STEP.VALUE` to JSON string `"12"`, exact
readback and hand over to `STEP_FINAL.md`. A generator exit code alone
does not prove English translation quality or completion of the module.
No automatic commit/push/deploy, online publish or Google Sheets deletion.

Report: `MODULE,LANGUAGES,CHAPTERS,KNOWLEDGE,INTERVIEW,CASE_STUDY,QUIZ,
VIDEO_RESERVED,HEADER_QA,ROUNDTRIP_QA,XLSX_PATHS,SHA256,DRY_RUN,APPLY,
TRANSLATION_QA,RELATED_QA,CURRENT_STEP_READBACK,BLOCKERS`.

**Historical compatibility:** prior `CURRENT_STEP=13/14/15/12A` values may
exist on previously generated modules. They are not new checkpoints; do not
blindly rewrite those values or treat them as proof of this STEP 12. Resolve
their provenance before changing status.
