# Create a new Google Sheets tab from a module's offline Excel

The module-local task importExcelFromModuleSheet reads a single-worksheet
Excel file from:

    <module>/content/sheet/<language>/module.xlsx

and creates a NEW tab inside the existing Google Sheets spreadsheet.
It never overwrites or deletes a pre-existing tab. Google automatically
assigns the new gid; the user does not supply a sheet ID.

## Extension

    importExcelFromModuleSheet {
        fileUrl = 'https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit'
        language = 'vi'
    }

Use executable module Gradle configuration, or pass -PfileUrl and -Planguage.
The generated task.gradle files are illustrative DSL templates rather than
automatically executed Gradle configuration.

## Example

    .\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:importExcelFromModuleSheet "-PfileUrl=https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit" -Planguage=vi -PsheetMode=DRY_RUN

Default mode is DRY_RUN: validate the file, build the Sheets requests, read
the destination spreadsheet using readonly credentials, report a proposed
tab title, and leave Google Sheets unchanged. Then change -PsheetMode to
APPLY to write.

APPLY logs the newly generated gid and direct tab URL. If the original
title is taken, a distinct suffix is used (for example OOP (import 1)).

## Security and fidelity

- Requires one Excel worksheet with cell I3 exactly equal to the invoking
  module's path, and an unlinked Excel file at the expected location.
- Preflights all cells, formatting, merges and dropdowns before any write.
- Uses write-scoped Sheets ADC in APPLY. When impersonating a service account,
  share the target spreadsheet as Editor with that service account.
- Transfers static values, basic styles, notes, frozen panes, merges,
  row/column sizes and supported explicit-list dropdowns.
- Live Excel formulas and Excel error cells fail fast; the corresponding
  exporter already converts Google formulas to static cached values.
- Large files may need several bounded batchUpdate requests. If an upload
  batch fails, the task attempts to DELETE ONLY the newly created tab; if
  rollback also fails, the error includes its gid.
- No existing tab is overwritten, and no local Excel file is changed.
- Google-only features lost during original export cannot be recreated.

GitHub Releases upload/download is a separate future workflow.
