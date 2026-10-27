# Legacy reverse-export tasks (STEP 15 code building blocks)

**Canonical STEP 15 now clones the native Google Sheet Template before
writing EN values.** Run `publishModuleEnSheet` per module and follow
`module-generate-agent/STEP_15.md`. This file describes the still-supported
standalone offline workbook converters and the older, independent importer.
Do **not** use `importExcelFromModuleSheet` in canonical STEP 15: it creates
a separate tab from Excel, bypassing the native Template clone and stable gid.

The existing import tasks are **Google Sheets -> module/vi**. The reverse
tasks read already-authored **module/en** documents and update only their
matching cells in the offline workbook
`<module>/content/sheet/en/module.xlsx`.

These tasks do **not** translate text, change any module YAML/Markdown,
modify VI Excel, or write to Google Sheets.

## Prerequisites

1. Run STEP 12 first. `content/sheet/vi/module.xlsx` must contain exactly one
   worksheet with the correct module location in `I3` and the final VI menu
   row layout. Its values and styles are the workbook template.
2. Complete STEP 13's structural clone VI -> EN and STEP 14 translation of all
   requested
   module EN files. **VI/EN must use identical chapter filenames and README
   anchor identities**, with valid `readmeRelated.file` associations.
3. Do not use the older independent EN chapter layout as the source of truth:
   mismatched menus, anchors, question ownership, or Roadmap milestone counts
   cause a task failure before any Excel file is replaced.

## Reverse tasks (module-local)

| Task | Source | Target worksheet range |
| --- | --- | --- |
| `syncModuleIntroductionToExcel` | `content/readme/docs/en/BASE.md` | `B17` |
| `syncModuleReadmeToExcel` | `content/readme/docs/en/menu/` and `readme/metadata/en/knowledge-metadata.yml` | `A/B` chapter/lesson titles, `C:N` Knowledge, `O` difficulty, `AB` isImportant |
| `syncModuleRoadmapToExcel` | `content/roadmap/en/roadmap.yml` | `F3:H` milestone title, purpose, objectives |
| `syncModuleInterviewToExcel` | `content/interview/en/question.yml` | `P:S` at its original chapter parent row |
| `syncModuleQuizToExcel` | `content/quiz/en/question.yml` | `T:W` at its original chapter parent row |
| `syncModuleVideoToExcel` | `content/readme/docs/en/menu/<chapter>/video/video-script.yml` | `X:AA` at its original chapter parent row; optional after FINAL |
| `syncModuleContentToExcel` | All applicable sources above | All above ranges, one atomic write |

`DRY_RUN` is the default. Each task uses the **existing** EN workbook if it
exists, or starts from a **copy in memory** of the VI snapshot if not. It
preserves other cell ranges, comments, number formats, row and column sizes,
merged cells, freeze panes and dropdowns. A first export names the worksheet
`<old title> EN`. An APPLY writes to a temporary XLSX, verifies I3/worksheet
count, then atomically replaces **only EN module.xlsx**.

From repository root (PowerShell example, substitute the real module path):

```powershell
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:syncModuleContentToExcel -PsheetMode=DRY_RUN
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:syncModuleContentToExcel -PsheetMode=APPLY
```

To export one completed component, substitute any individual task name in
the table. Fragmented Knowledge is split across C:N (12 chunks, max 30,000
Unicode code points each); Interview/Quiz/Video YAML is split across four
adjacent cells in the same manner. Overflow **fails**, never truncates data.

## Legacy standalone Excel → new tab (NOT canonical STEP 15)

The existing `importExcelFromModuleSheet` task reads EN `module.xlsx` and
creates a **new** tab with a new `gid`. It does NOT update or overwrite an
existing tab.

```powershell
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:importExcelFromModuleSheet "-PfileUrl=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -Planguage=en -PsheetMode=DRY_RUN
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:importExcelFromModuleSheet "-PfileUrl=https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit" -Planguage=en -PsheetMode=APPLY
```

After APPLY, record its **new gid/tab URL** in your chosen module mapping.
The static English workbook remains at `content/sheet/en/module.xlsx`
and is already covered by `.gitignore`.

## Known limits

- The reverse exporter **does not edit Swagger**: Swagger is authored in
  its own `src/main/resources/swagger/en/` YAML files, outside the Sheet
  content importer contract.
- `readmeRelated`/`apiRelated` are preserved in question YAML. Questions are
  grouped only by verified `readmeRelated.file`. Missing or ambiguous chapter
  links stop the export instead of silently assigning questions.
- Roadmap `dependsOn`, `relatedKnowledge` and `relatedModules` **have no
  columns in F:H**. They remain canonical in `content/roadmap/en/roadmap.yml`;
  **do not re-import the Excel Roadmap into EN YAML**, since the existing
  Sheet->Roadmap importer is VI-only and derives IDs from localized titles.
- The separate Sheet->module importers currently target **VI** and are not
  reversible losslessly in all fields; EN module YAML/Markdown remain
  canonical after translation. Uploading Excel creates a useful offline
  publishing/editing tab, not an EN content regeneration pipeline.
- The original Excel exporter stores Google formulas as static values,
  subject to Excel's 32,767-character cell limit.
- This does **not** perform STEP 13's structural clone, translation, relation
  repair or workflow completion/J3 update. Those remain separate gates.
