# STEP 11 — WORKER ONLY: KNOWLEDGE

## Boundary — read before touching EN
Prime supplies JOB_ID, exact absolute EN_FILES, EXPECTED_SHA256 for **each** file and THIS worker-rule path. Read only this rule and assigned EN file(s). Do not discover siblings, read VI, read Prime/General rules, edit manifest/baseline or any other EN file. One worker exclusively owns this job; no parallel writers. If paths, checksums, count, or types disagree: BLOCKER: ASSIGNMENT_MISMATCH, write nothing.

**Translate only text on the whitelist below** from Vietnamese into precise, natural technical English. Preserve the meaning and all details; never paraphrase away conditions, invent examples, correct source semantics, reorder entries, remove repetition or rewrite code. UTF-8. Source EN files already contain VI text cloned by STEP 10.

**Universal immutable blacklist:** YAML keys, root shape, counts and item order; ID/groupID, anchors/anchorList, readmeRelated/apiRelated, technical paths and URLs, Java class/method/signature/keyword, source code/fences and inline code, JSON/XML inputs and literal outputs, HTTP method/path/status, booleans/numbers, metadata and schema comments. Never update VI, Sheet, Java, LIST.md, knowledge-metadata.yml, Video, Gradle, master.json or Git. For unclear localization of code comments/test strings, mark REVIEW and preserve source literally.

## Scope and whitelist

Own exactly **one** assigned Knowledge Markdown file, never a whole folder. Translate visible headings, chapter/menu labels, prose, explanations, visible link labels, paragraphs, bullets, tables, blockquotes and HTML visible text, including `<summary>View details</summary>` equivalents. **Keep** every `<a id="...">`, `(#anchor)`, URL, code fence language/body (Java/Mermaid/Bash/text), inline code, HTML tag/attribute, code output, sample values, section ordering and number of Knowledge anchors. A large file contains multiple lessons; translate each anchor section in sequence. Never split ownership of the file with another worker.

## Delivery and stopping
Check every assigned file's original SHA-256 before write. Translate **all whitelist content**, even for long files: process sections/groups sequentially while the same worker owns the file, preserving untouched content. For Markdown validate heading hierarchy, link destinations, anchor IDs, number/order of list and table entries, HTML tags, inline code and exact fenced code. For YAML parse the whole file, preserve keys/order/types/count and all protected values exactly. Read back **each** EN file; report each file's before/after SHA-256 and counts TRANSLATABLE_ITEMS, TRANSLATED_ITEMS, PENDING, plus STRUCTURE_QA, PROTECTED_QA and any REVIEW/BLOCKER. A grouped job completes only after all files pass. If validation fails, do not claim COMPLETED.

**Report to Prime then END the worker task.** Do not stay waiting for, or self-assign, the next job. Prime independently performs semantic QA and controls the queue and CURRENT_STEP; worker reports do not complete STEP 11.

