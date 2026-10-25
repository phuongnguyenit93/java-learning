# Reference / Summary — Sheet is the authored source of truth (2026-10-25)

This document supersedes outdated STEP statements claiming Reference has no authoring checkpoint, AC is not imported, AC18/19 must be named `Learning Objectives`, or STEP 15 ends at AB.

## Reference — STEP 2

- Source: `L2 = Reference`, `L3 = references: [...]` YAML on the selected module tab. `K2/K3` remain Special Note; `L3` must never be used for other purposes.
- Worker authors vetted Reference YAML **in L3**, using sources appropriate to C2/Special Note. Preserve valid existing content, no automatic rewrite. Use only real URLs; require `title`, `url`, optional `description`. Hard limit: 30,000 Unicode code points **and** 32,767 UTF-16 units; do not spill into neighboring cells.
- Prime validates L2/L3 and full YAML, then runs module-local `generateModuleReferenceFromSheetByLocation` `-PsheetMode=DRY_RUN` → `APPLY`; exact readback `content/reference/references.yml`. The importer is deterministic, does not call AI, cleans **only** `content/reference/`, and writes the L3 bytes. The file is language-neutral. A blank L3 means `SKIPPED_EMPTY_REFERENCE` (no delete); an explicit `references: []` means intentionally empty Reference. Missing L2 is `SKIPPED_NO_REFERENCE_SCHEMA` if L3 is also empty.
- **Backfill:** If Roadmap/B17 are already complete, Prime may assign only L2/L3 to worker, without reauthoring Roadmap/B17 or touching F:H. Validate and sync Reference separately. For a newly authored STEP 2, require Reference PASS along with Roadmap and Introduction before advancing J3.

## Summary — STEP 5

- The former Learning Objectives capability is called **Summary**. Keep the Markdown rubric 0/1/2/3 and chapter learning-flow requirements; rename the new column labels to `AC18=Summary`, `AC19=Summary Markdown`. The importer also accepts a consistent legacy `Learning Objectives`/`Learning Objectives Markdown` pair, preserving older tabs. Prime must not overwrite valid existing AC data when provisioning headers.
- Worker still writes plain Markdown ONLY to `AC<PARENT>` and `AC<CHILD>` after reviewing Knowledge C:N and verifying every related lesson. Worker must NOT generate or edit YAML/module Summary files.
- After successful STEP 5 README import, Prime runs module-local `generateModuleSummaryFromSheetByLocation` DRY_RUN → APPLY. Deterministic importer validates AC and menu ownership, cleans only VI `<chapter>/summary/` directories, including removed chapters, and fills:

  ```text
  content/readme/docs/vi/menu/<chapter>/summary/
    section.md       # exact AC parent Markdown
    summaries.yml    # all AC child Markdown in ONE YAML
  ```

  ```yaml
  summaries:
    - id: oop-basic-001
      title: "What is OOP?"
      summary: |-
        ## 0. Định hướng học tập
        ...
  ```

- IDs and order are deterministic from Knowledge chapter filename/anchor and child B rows; Markdown is not summarized or rewritten by code. Missing/incomplete AC entries block before cleanup. A fully empty, valid AC header is an authoritative empty Summary snapshot. Reference, Introduction, Knowledge, Metadata, Interview, Quiz, Video, EN, and other modules are never deleted by Summary import.

## Excel / EN / Portal contracts

- STEP 12 exports exactly one VI tab to Excel, including Reference `L2/L3` and Summary `AC18:AC<last row>`; each cell must fit Excel's 32,767 UTF-16 unit limit. The Google `Template` must offer at least 29 columns.
- STEP 12A clones VI Summary files to the EN tree as preliminary translation inputs when VI Summary exists. STEP 14 translates **only** `section.md`, `summaries[].title` and `summaries[].summary` into EN, preserving `id`, order, number of lessons, Markdown/code/links/anchors; Reference remains shared and is never translated.
- STEP 15 reads the EN Summary files, maps them onto the original VI rows in Excel AC parent/children, rejects missing EN content when VI Summary was authored, and writes EN `L2/L3` and `AC18/19` + all AC bodies using code only; the shared Reference is copied verbatim into EN L3. Template clone includes 29 columns. Exact readback is mandatory.
- Publishing Summary source files is not equivalent to displaying Summary on the Portal. Static projection and frontend consumers are separate from the importer. No Gradle configuration/IDE sync may silently import a Sheet or synthesize Summary.
