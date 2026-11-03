# STEP 12 — WORKER ONLY: Relation audit proposal, no translation

Prime may assign a **single, explicitly identified chapter or batch** of existing Knowledge/Swagger/Quiz/Interview/Case Study relations. The handoff must give one module, a run ID, exact read-only snapshot/staging seed, the required proposal target file and worker ownership. Only the proposal target may be written; do not inspect unrelated modules or write canonical VI/EN/Sheet.

## What this worker may do

- Confirm each existing `readmeRelated`, `anchorList`, `relatedKnowledge`, `relatedModules.routeId` and approved Swagger relation against actual unique targets in the assigned seed. Categorize each as `KEEP` (already correct), `SET` (missing, supported by proof), `NO_MATCH` (no support), or `REVIEW` (uncertain or invalid).
- Respect canonical module-wide `content/{quiz,interview,casestudy}/vi/*.yml` and STEP 7's cross-chapter `readmeRelated.anchorList[]`. Never use obsolete per-chapter Quiz/Interview import shapes, and never force a link merely because titles resemble one another.
- For a proposed `SET`, include exact source identity, existing value, target anchor/method/route, evidence and owner. Do not modify any code, prose, translated EN sentence, Markdown, YAML source, Java or Google Sheet.

## Forbidden

No rewriting questions, summaries, case descriptions, translated content, group IDs, order, answers, correctAnswerId, Swagger method signatures, URLs, input/response/status, or any technical field other than proposing supported relations. No running clone/importers/Gradle/Git. No changing `master.json`. Unknown or drifted seed checksum is a BLOCKER, not permission to repair source.

## Completion

Write only the assigned proposal in the format Prime requested; parse/read back and report `AUDITED, KEEP, SET, NO_MATCH, REVIEW, EVIDENCE, BLOCKERS`. **Report to Prime and end the worker job.** Prime alone reviews and applies approved relation deltas with rollback and protected EN text preservation.
