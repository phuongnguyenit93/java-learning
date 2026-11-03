# STEP 12 — Related metadata enrichment / QA (Prime owns APPLY)

> Renumbered from the former STEP 11. **STEP 11 translation already happened.** This STEP may ONLY inspect or enrich technical relation fields; it must not overwrite translated EN prose or re-clone VI to EN. The legacy per-chapter Quiz/Interview import instructions are obsolete for the canonical STEP 6/7 module-wide assessment files.

## 1. Source and scope

Prime reads General/rules, validates I3/gid and module identity, verifies STEP 11 translation QA/readback and `CURRENT_STEP.VALUE="11"` before any mutation. Check source VI Knowledge anchors, canonical Swagger `vi/en`, roadmap, and module-wide `content/{interview,casestudy,quiz}/{vi,en}/*.yml`. This step is for **relations only**, not content creation, translation or rewriting.

Eligible relation fields:

- Swagger `controller-description.yml`: `readmeRelated.file`. `api-descriptions.yml`: method `readmeRelated.file/anchor`. Keep exact controller/method signatures and HTTP metadata.
- Canonical module-wide Interview/Case Study/Quiz: `readmeRelated.anchorList[]` only where a provable existing Knowledge anchor is relevant; do not force a link when no evidence exists. STEP 7 already populated many such links; valid data is `KEEP`, not rewritten.
- Roadmap `relatedKnowledge[]`, `relatedModules[].routeId` only with exact existing target and safe persistence. Keep milestone IDs/order, transitions and translated text.

Cross-chapter links are allowed only when canonical module-wide relationship semantics support them. Avoid legacy per-chapter `questions/quiz`, `questions/interview` and the removed old Sheet import tasks. Never generate new knowledge or question IDs. No `apiRelated` guessed. VI and EN should retain identical technical links unless a proven language-specific link demands review.

## 2. Workers and Prime

For any missing relation requiring a semantic proposal, worker ownership is **one chapter or one validated independent batch**, using isolated staging `build/step12/<RUN_ID>/`; workers propose targets with evidence and do not write canonical files or Sheets. Prime alone reviews all proposals, validates target existence and uniqueness, stages an exact before/after diff of **ONLY relation fields**, and performs an atomic guarded APPLY when safe.

Worker pool follows `rules/PRIME_RULES.md`: NORMAL max 3; SPECIAL requires explicit N from the user, otherwise `BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`. For STEP 12, Prime can also do relation verification without spawning workers when links are already complete. Do not dispatch a legacy `STEP_11_WORKER.md` as the translation worker rule.

## 3. Safety gates

1. Capture SHA-256 and protected values for the entire VI/EN file before edits, including text from STEP 11. Verify no active translation writer.
2. Every proposed relation must resolve to an existing, unique Knowledge anchor, Controller/method or route. Uncertain → REVIEW/NO_MATCH (no forced links).
3. Do not run `cloneModuleViToEn`, the legacy chapter Quiz/Interview importers, or a serializer that rewrites translated EN values. If a module-wide Sheet importer is required, first prove it preserves all fields and translated EN independently; otherwise report BLOCKER.
4. Apply changes only with rollback and exact readback of source/target relation fields; byte/semantic-check **all non-relation fields** against snapshot. Preserve every translated English sentence in EN.
5. If safe source of truth or Roadmap persistence is unavailable, no partial blind APPLY: report `BLOCKER: RELATION_PERSISTENCE` rather than claiming completed.

When all metadata relations have been audited (valid existing fields can remain unchanged), no dangling links or blockers remain and preservation QA passes, Prime updates ONLY `master.json.CURRENT_STEP.VALUE = "12"` then exact readback. Never update the Sheet progress cell, commit/push/deploy or run STEP 13 automatically.

Report: `I3,GID,RUN_ID,RELATIONS_AUDITED,KEEP,SET,NO_MATCH,REVIEW,ROADMAP,SWAGGER,ASSESSMENTS,VI_EN_TEXT_PRESERVED,CURRENT_STEP_READBACK,BLOCKERS`.
