# Export one module tab to Excel offline

The module-local Gradle task **exportModuleSheetToExcel** reads exactly one
Google Sheets tab (identified by gid) and writes:

    <module>/content/sheet/<language>/module.xlsx

It uses Apache POI and the existing Google Sheets API / ADC credentials.

## Required extension fields

    exportModuleSheetToExcel {
        fileURL = 'https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit'
        sheetURL = 'https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit#gid=<tab-gid>'
        language = 'vi'
    }

- **fileURL** identifies the spreadsheet.
- **sheetURL** identifies the tab in that same spreadsheet and must contain
  an explicit numeric gid.
- **language** specifies the output directory (for example vi or en).

The generated module task.gradle shows this extension as a template. It is not
executed by Gradle automatically. Configure the extension in executable Gradle
configuration, or use the equivalent command-line properties.

## Run on a module (example OOP)

    .\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:exportModuleSheetToExcel -PfileURL=https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit "-PsheetURL=https://docs.google.com/spreadsheets/d/<spreadsheet-id>/edit#gid=<tab-gid>" -Planguage=vi -PsheetMode=DRY_RUN

DRY_RUN (default) validates and renders the workbook without writing a file.
To save it, rerun with -PsheetMode=APPLY.

## Safety, fidelity, and limits

- The tab's cell I3 **must** exactly match the invoking module's root-relative
  location. Mismatches stop the task before output.
- The fileURL and sheetURL spreadsheet IDs must match. Only the specified tab
  is downloaded; the generated workbook has exactly one worksheet.
- The Sheets client uses the read-only scope. Neither the Google document nor
  any other module content is changed.
- APPLY writes a temporary file, opens it again with Apache POI to validate
  worksheet and populated-cell counts, then atomically replaces the latest file.
- Formula cells are snapshotted as their cached/effective values, not live
  formulas. Unresolved formula errors and values longer than 32,767 characters
  cause a failure instead of silent data truncation.
- Common cell styles, column widths, row heights, merged regions, notes, frozen
  panes and simple list dropdowns are exported. Unsupported dropdowns are
  counted in the task log; Google-only formatting and special chip features
  cannot always be reproduced in Excel.
- Offline Excel files are ignored by Git; a future GitHub Release
  upload/download workflow is independent of this export.
