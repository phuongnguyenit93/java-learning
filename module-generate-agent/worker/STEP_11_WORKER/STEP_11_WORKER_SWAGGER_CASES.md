# STEP 11 — WORKER ONLY: SWAGGER_CASES

## Boundary — one complete TEMP_FILE per worker
Prime supplies MODULE, MODULE_GRADLE_PATH, RUN_ID, JOB_ID, TARGET_LANGUAGE, exact absolute TEMP_FILE, MODULE_FILE (destination only), EXPECTED_TEMP_SHA256, WORKER_ID and this typed worker-rule path. Read only THIS rule and the assigned TEMP_FILE. Do not discover or read siblings, source VI, module destination, manifest, baseline, journal or other rules. This job owns EXACTLY ONE file with no parallel writer. Do not edit module target directly. Path, ownership, language or checksum mismatch => BLOCKER: ASSIGNMENT_MISMATCH; write nothing.

**Translate or retranslate only human-facing text on the whitelist below** into accurate, natural TARGET_LANGUAGE (en, ja, fr, etc.). The file may contain Vietnamese source text OR a translation already written in the target language. If Prime assigns retranslation, review and improve the existing text within the whitelist rather than skipping it because it is already translated. Preserve meaning, technical details, examples, ordering and code; do not invent or omit content. TEMP_FILE is the exact full Markdown/YAML file copied from the selected target-language module path, regardless of its current language or quality. Edit only TEMP_FILE, never segmented JSON or module destination.

**Universal immutable blacklist:** YAML keys, root shape, counts and item order; ID/groupID, anchors/anchorList, readmeRelated/apiRelated, technical paths and URLs, Java class/method/signature/keyword, source code/fences and inline code, JSON/XML inputs and literal outputs, HTTP method/path/status, booleans/numbers, metadata and schema comments. Never update VI, Sheet, Java, LIST.md, knowledge-metadata.yml, Video, Gradle, master.json or Git. For unclear localization of code comments/test strings, mark REVIEW and preserve source literally.

## Scope and whitelist

Own exactly `src/main/resources/swagger/<TARGET_LANGUAGE>/api-cases.yml`. Translate all Vietnamese **human-readable prose** in each Controller/method's:

- `cases[].title`, `cases[].description`, `cases[].detailDescription` (including prose inside HTML);
- `cases[].observations[].source` and `cases[].observations[].program`;
- `cases[].observations[].expect.results` and `cases[].observations[].expect.conclusion` (including prose inside HTML).

Process **every observation of every case**, not just the first item or first `expect` block. Preserve `observations` list shape, count/order, YAML keys, `observations[].type` (LOG/JFR/DEBUGGER etc.), and `observations[].expect.keyword` **byte-for-byte as parsed values**: these are technical enums and exact log/event/exception matchers, NOT translation targets. Keep Java class/method names, JFR event IDs, exception names, command strings, literal outputs, HTML tags/attributes, and the complete contents of `<code>...</code>` and backtick code spans unchanged. Preserve case IDs, input, status, response, request/response bodies, and every other technical field. Do not invent expected behavior, change observed results, or alter sample data to sound English. If an unlisted nested observation key contains human-facing Vietnamese text, report REVIEW to Prime for explicit whitelist approval rather than modifying it. Report every method, case and observation translated; validate HTML, YAML, protected values, and full readback.

## Delivery and stopping
Check the original TEMP_FILE SHA-256 equals EXPECTED_TEMP_SHA256 before editing. Translate all whitelisted content in this SINGLE file, one section/group at a time, then read back the full UTF-8 file and check Markdown headings/list/table/link/anchor/HTML/code, or YAML keys/schema/types/order and every protected value. To deliver, run publishModuleTranslationFile with translationRunId, translationLanguage, translationFile (module-relative MODULE_FILE), translationWorkerId and translationMode=DRY_RUN, then APPLY. This task verifies worker ASSIGNED ownership, baseline and source/target SHA-256 and protected structure, then copies the entire file atomically to MODULE_FILE and deletes only that TEMP_FILE. No manual copy-back on a failed validation; keep temp and report REVIEW/BLOCKER. Report before/after SHA256, TRANSLATABLE_ITEMS, TRANSLATED_ITEMS, PENDING, STRUCTURE_QA and PROTECTED_QA.

**Worker publish command (from repository root, first DRY_RUN then APPLY):**

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleTranslationFile -PtranslationRunId=<RUN_ID> -PtranslationLanguage=<TARGET_LANGUAGE> -PtranslationFile="<MODULE_FILE>" -PtranslationWorkerId=<WORKER_ID> -PtranslationMode=DRY_RUN --console=plain
.\gradlew.bat :<MODULE_GRADLE_PATH>:publishModuleTranslationFile -PtranslationRunId=<RUN_ID> -PtranslationLanguage=<TARGET_LANGUAGE> -PtranslationFile="<MODULE_FILE>" -PtranslationWorkerId=<WORKER_ID> -PtranslationMode=APPLY --console=plain
```

**Report to Prime, END this single-file job turn, then become SLEEPING/Idle for reuse.** Worker must not edit the recovery journal, assign new jobs or update CURRENT_STEP. Prime independently performs REPORT and QA_PASS after reviewing published file semantics. On reassignment, accept only fresh JOB_ID/TEMP_FILE/MODULE_FILE/SHA and typed rule; the former scope is revoked.

