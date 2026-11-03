# STEP 14 — Read-only VI/EN final QA (Prime ONLY)

> STEP 13 legacy clone instructions are **retired**. STEP 10 was the only clone; STEP 11 translated EN; STEP 12 handled relations; STEP 13 exported VI Excel. **STEP 14 must NOT clone, translate, replace, or rewrite any VI/EN file.**

## 1. Preflight

Prime reads General, all rules, STEP 14 and module-info. Verify exact I3 path and VI gid, source module, STEP 11 translation manifest `build/step11/<RUN_ID>/manifest.json`, STEP 12 relation audit and STEP 13 VI Excel export QA. `master.json.CURRENT_STEP.VALUE` normally must be `"13"`; if different due historical migration or rerun, require direct evidence and don't guess.

**Do not compare translated EN against VI byte-by-byte.** Compare technical fields, structural shapes and approved translation whitelist, using baseline/hashes. Never run `cloneModuleViToEn` after translation; it would destroy EN text. No workers and no Sheet writes here.

## 2. Gates — all read-only

1. **Inventory:** all required VI/EN pairs exist for Knowledge Markdown, Summary `section.yml + summaries.yml` by chapter, `BASE.md`, Reference, Roadmap, canonical module-wide Quiz/Interview/Case Study, four Swagger YAML. Protected `LIST.md` points at EN. No stale EN-only files inside STEP 10-owned scope. Video belongs to optional post-FINAL workflow, not this QA gate.
2. **Knowledge:** same Knowledge anchor IDs and heading counts/order, Markdown link destinations, code fences/bodies, inline code, list/table structures; EN prose translated accurately and completely, not abridged.
3. **Assessments:** Quiz `quiz[].groupID/groupName/questions` group/question counts, answer IDs A/B/C/D and correctAnswerId unchanged; Interview `interviewQuestion[].groupID/questions` and Case Study `caseStudy[].groupID/cases` preserved, readmeRelated.anchorList stable. English question/answer/knowledge/situation/resolve prose reviewed. Case Study Java code and outputs remain exact.
4. **Summary and Common:** summaries/section pair in each chapter complete; title/description/table translated; summary IDs/order/roadmap transitions stable. Reference URLs/order intact; Roadmap milestones/dependencies/relatedKnowledge/routeId preserved, Introduction translated.
5. **Swagger:** controller names, method signatures, params, request/response/status, case IDs/inputs/outputs and all relations preserved. Only allowed visible descriptions translated, HTML structure intact.
6. **YAML and cross-links:** parse every canonical EN YAML with schema/contract validators; check Knowledge anchors, Swagger relations, route IDs and module-wide assessment links resolve. No pending/review/blocker in STEP 11 manifest/ledger or STEP 12 related QA.
7. **Preservation:** VI, Java/test, Sheet, `master.json` and out-of-scope paths unchanged during QA. Validate STEP 13 `content/sheet/vi/module.xlsx` if applicable; no current writer or incomplete rollback.

For structural validation reuse `validateModuleTranslation -PtranslationRunId=<RUN_ID>`, which is **read-only** and reports `STRUCTURE_PASS_NEEDS_SEMANTIC_QA` rather than claiming semantic PASS. Prime independently checks translation completeness/accuracy; no false PASS from an exit code alone.

## 3. Completion

All gates PASS, no unexplained differences or untranslated required field, every job QA_PASS (or reviewed SKIP_NO_TRANSLATABLE_TEXT). Prime writes **only** `<I3>/master.json.CURRENT_STEP.VALUE = "14"`, exact readback. Do not execute STEP 15 automatically, do not commit/push/deploy. If any gate fails, leave checkpoint untouched and report exact file/path, field and remediation owner; fixes happen in the owning STEP with guarded resumption, not by writing in STEP 14.

Report: `I3,GID,TRANSLATION_RUN,INVENTORY,KNOWLEDGE,ASSESSMENTS,SUMMARY,COMMON,SWAGGER,RELATIONS,SCHEMA,SEMANTIC_QA,OUT_OF_SCOPE,CURRENT_STEP_READBACK,BLOCKERS`.
