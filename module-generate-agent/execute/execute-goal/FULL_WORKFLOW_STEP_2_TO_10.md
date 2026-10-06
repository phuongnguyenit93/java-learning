# GOAL — FULL MODULE WORKFLOW STEP 2 → STEP 10

This file is a **shared execution Goal template**.

It intentionally contains **no hard-coded target module name, module path, technology-specific module path, or module-specific Curriculum path**.

The target module must be supplied by the invoking request/session context.

Current repository architecture/governance files and canonical STEP files always override remembered assumptions or stale copies of this template.

---

## PRIMARY OBJECTIVE

Execute the complete canonical module workflow:

```text
STEP 2
→ STEP 3
→ STEP 4
→ STEP 5
→ STEP 6
→ STEP 7
→ STEP 8
→ STEP 9
→ STEP 10
```

for the target module supplied by the caller.

Continue autonomously until the entire target module is completed and CLEAN.

Do NOT stop after each STEP.

Do NOT ask for another prompt before continuing to the next STEP.

Only stop early when:

1. a genuine ownership / architecture / Curriculum blocker cannot be resolved safely, or
2. STEP 10 is blocked by an external CLI/auth/check/permission/conflict condition.

---

## BASELINE

Before work begins, determine the **CURRENT repository baseline** from the live repository rather than hard-coding it in this Goal.

At minimum inspect the current versions / technology baseline relevant to the target module from repository source-of-truth such as:

```text
AGENTS.md
ARCHITECTURE.md
root build/configuration
module metadata
dependency catalogs / platform configuration when relevant
```

If the target technology has a version-sensitive baseline, use that exact repository baseline throughout review and authoring.

When a relevant Curriculum Map exists under:

```text
module-generate-agent/temp/*_CURRICULUM_MAP.md
```

resolve the correct one from the actual target area/module and use it according to the current canonical rules.

For this full STEP 2 → STEP 10 Goal, do **not** abuse isolated-step Curriculum fallback rules to silently skip a required Step 1 area decision. If current canonical governance says the full chain requires Step 1 context and that context is genuinely absent/ambiguous, route safely to the correct prerequisite instead of inventing ownership.

---

## ABSOLUTE NO-SKIP RULE

STEP 2 through STEP 9 MUST ALL BE EXECUTED.

Absolutely DO NOT skip a STEP because:

- Roadmap already exists
- Menu already exists
- Knowledge already exists
- API files already exist
- Video already exists
- Quiz already exists
- Interview already exists
- previous review says CLEAN
- another session already edited the files
- generator output already exists
- module currently builds
- current artifact looks complete

Existing authored content is:

```text
MIGRATION EVIDENCE
```

not:

```text
APPROVED CURRENT DESIGN
```

For STEP 2–9, behave as though the current authored learning content has never been approved under the current Goal.

Every STEP must be freshly re-evaluated from first principles.

---

## MANDATORY SOURCE OF TRUTH

Before EACH STEP:

1. Read the CURRENT canonical STEP_X file.
2. Ensure `GENERAL_AGENT_RULES.md` is understood.
3. Read the relevant Curriculum Map when applicable and present.
4. Inspect current repository architecture and target-module state.
5. Inspect upstream artifacts produced/approved by previous STEP.
6. Consult authoritative official documentation for the target technology.
7. Validate against the actual repository technology/version baseline.

Canonical STEP files:

```text
STEP 2
→ module-generate-agent/STEP_2_ROADMAP_REFERENCE.md

STEP 3
→ module-generate-agent/STEP_3_MENU.md

STEP 4
→ module-generate-agent/STEP_4_KNOWLEDGE.md

STEP 5
→ module-generate-agent/STEP_5_API.md

STEP 6
→ module-generate-agent/STEP_6_VIDEO.md

STEP 7
→ module-generate-agent/STEP_7_QUIZ.md

STEP 8
→ module-generate-agent/STEP_8_INTERVIEW.md

STEP 9
→ module-generate-agent/STEP_9_VALIDATION.md

STEP 10
→ module-generate-agent/STEP_10_COMMIT.md
```

If canonical rules change during execution:

```text
CURRENT canonical file wins.
```

---

## WORKSPACE / WORKTREE SAFETY — CRITICAL

Before editing anything:

- inspect actual current repository root;
- inspect current branch;
- inspect `git status`;
- inspect existing worktrees;
- inspect whether another session/worktree is already working on the target module.

Do NOT arbitrarily choose an existing worktree only because its name matches the module.

Follow the CURRENT canonical STEP 2 worktree/branch rules exactly.

If a dedicated worktree must be created:

```text
→ create/use the one belonging to THIS execution according to the canonical rule.
```

If another session is already modifying a worktree:

```text
→ do not overwrite, reset, clean, restore, or reuse its uncommitted work unless
  the canonical workflow explicitly establishes that it is the current task worktree.
```

Never:

- reset unrelated user work;
- delete another session's uncommitted changes;
- restore files merely because they look generated without first proving they were produced by the current execution;
- overwrite concurrent work.

If a generator causes repository-wide side effects:

```text
→ identify them precisely
→ preserve target changes
→ revert only side effects that can be proven to belong to the current execution
```

---

## CURRICULUM OWNERSHIP

When a relevant locked Curriculum Map exists, it is authoritative for area-level:

```text
scope
module inventory
primary ownership
neighboring-module boundaries
inter-module dependency
recommended learning order
cross-module terminology ownership
known duplication/gap risks
```

Do not hard-code ownership assumptions in this shared Goal.

For the concrete target module, derive its responsibility and exclusions from the current Curriculum Map and repository architecture.

If the target area has no Curriculum Map and current canonical rules allow an isolated-step fallback, use current content + authoritative technical knowledge only as allowed by those rules. For this full STEP 2 → STEP 10 Goal, do not silently manufacture missing area ownership.

Respect neighboring owners. Related concepts may be referenced for learner context, but their internals remain with their primary owners.

---

## EXISTING CONTENT / MIGRATION RULE

Existing content is evidence, not authority.

Use:

```text
fresh design
        ↓
compare with existing content
        ↓
preserve valid semantic value
        ↓
migrate / reorganize / rewrite
        ↓
remove stale or incorrect material with evidence
```

For meaningful existing content, classify as appropriate:

```text
KEEP
MOVE
RENAME
MERGE
SPLIT
IMPROVE
REMOVE-WITH-EVIDENCE
```

NO-LOSS applies to valid semantic content.

NO-LOSS does NOT mean preserving:

- outdated behavior;
- technically incorrect claims;
- wrong ownership;
- duplicated concepts;
- bad pedagogical order;
- historical paths;
- historical chapter counts;
- obsolete APIs;
- behavior belonging to an older technology/version baseline.

---

## MANDATORY REVIEW LIFECYCLE — STEP 2 THROUGH STEP 9

Every STEP from STEP 2 through STEP 9 MUST follow:

```text
IMPLEMENT / DECIDE
        ↓
BUILD / GENERATOR / SCHEMA / RUNTIME VALIDATION
        ↓
FRESH INDEPENDENT REVIEW #1
        ↓
FIX ALL MUST FIX
+
FIX ALL VALID SHOULD IMPROVE
        ↓
REVALIDATE
        ↓
FRESH INDEPENDENT REVIEW #2
        ↓
FIX ALL MUST FIX
+
FIX ALL VALID SHOULD IMPROVE
        ↓
REVALIDATE
        ↓
CLEAN?
        ├─ NO
        │   → REVIEW #3 / #4 / ...
        │   → fix
        │   → revalidate
        │
        └─ YES
            → proceed to next STEP
```

### REVIEWS MUST BE SEQUENTIAL — NEVER PARALLEL ON THE SAME SOURCE SNAPSHOT

The required independent reviews are **not parallel reviews of the same source state**.

Required model:

```text
SOURCE / ARTIFACT STATE V1
        ↓
FRESH INDEPENDENT REVIEW #1
        ↓
APPLY REVIEW #1 VALID FINDINGS
        ↓
REVALIDATE
        ↓
SOURCE / ARTIFACT STATE V2
        ↓
FRESH INDEPENDENT REVIEW #2 OF V2
        ↓
APPLY REVIEW #2 VALID FINDINGS
        ↓
REVALIDATE
        ↓
SOURCE / ARTIFACT STATE V3
```

Therefore:

```text
DO NOT spawn Review #1 and Review #2 together.
DO NOT let Review #2 audit the same pre-fix snapshot used by Review #1.
DO NOT start Review #2 before Review #1 findings have been adjudicated,
valid findings fixed, and the resulting source revalidated.
```

Review #2 must inspect the **current post-Review-#1-fix source/artifact state**.

The same sequencing rule applies to Review #3 / #4 / later reviews:

```text
review N
→ fix valid findings
→ revalidate
→ only then review N+1 on the new current source
```

This sequencing remains mandatory even when multiple workers are available.

IMPORTANT:

Build success is NOT a review.

Generator success is NOT a review.

Schema validation is NOT a review.

Parity script is NOT a review.

The implementation agent checking its own diff is NOT one of the required independent reviews.

Minimum per STEP 2–9:

```text
1 implementation/decision pass
+
2 fresh independent full reviews
```

---

## INDEPENDENT REVIEW DEFINITION

Each reviewer must independently re-read:

- current canonical STEP file;
- CURRENT target artifacts;
- relevant upstream artifacts;
- Curriculum Map when applicable;
- authoritative official documentation for the current technology/version baseline.

Reviewer #2 must NOT simply verify reviewer #1's findings.

It must freshly audit the whole STEP artifact **after Review #1 fixes have been applied and revalidated**.

For Review #2 and every later review, the previous review result is a required input/context, but it is not a substitute for inspecting the current source.

Mandatory review-chain semantics:

```text
Review #1 input
→ current source V1

Review #2 input
→ Review #1 result
+ current source V2 after Review #1 fixes

Review #3 input, when required
→ Review #2 result
+ current source V3 after Review #2 fixes
```

The later reviewer must independently validate the whole current artifact and may confirm, reject, refine or extend previous findings, including detecting incomplete fixes, regressions, severity mistakes or newly visible issues.

Each review should classify:

```text
MUST FIX
SHOULD IMPROVE
OPTIONAL
```

A STEP cannot be declared CLEAN while a valid MUST FIX remains.

Valid SHOULD IMPROVE should normally be fixed before CLEAN.

If Review #2 causes a substantial change:

```text
→ apply the change
→ revalidate
→ run another independent review on the resulting new source state.
```

---

## STEP 2 — ROADMAP + REFERENCE

Design the Roadmap from first principles.

Do NOT derive the Roadmap mechanically from the old Menu.

Do NOT preserve old milestones simply because they exist.

The Roadmap must explain the learner journey for the target module at **milestone level**.

Freshly evaluate:

```text
purpose / why the module exists
foundation / prerequisite mental model
major conceptual groups
important relationships
mechanics at the correct abstraction level
practical usage
decision / trade-off
failure / debugging model
integration / ownership handoff
synthesis
```

Do NOT force one Roadmap milestone per concept/API/property/class.

Roadmap remains milestone-level.

It must not become:

- API inventory;
- property-key inventory;
- class list;
- Menu list;
- documentation table of contents.

Curate:

```text
reference/references.yml
```

Use authoritative documentation relevant to the exact repository technology/version baseline.

Reference catalog should be intentionally useful, not a dump of every URL visited during research.

STEP 2 gate:

```text
IMPLEMENT
→ validate
→ REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 3 — MENU

Re-design the learner-facing structure from first principles against the approved STEP 2 Roadmap.

### CRITICAL EXISTING-MENU RULE

Only **REAL BODY CONTENT** creates migration/no-loss obligation.

REAL BODY CONTENT includes:

- explanatory prose;
- meaningful examples;
- code;
- meaningful tables/lists;
- failure discussion;
- trade-offs;
- learner-facing explanation inside a section.

These do **NOT** count as body content:

- H1/H2/H3 title;
- anchor;
- path;
- folder name;
- generated internal menu;
- navigation boilerplate;
- empty section;
- placeholder;
- TODO;
- title-only scaffold.

If old Menu is title/anchor/scaffold only:

```text
→ treat it as reference only
→ redesign from zero
→ do NOT preserve chapter count
→ do NOT preserve ordering
→ do NOT preserve paths
→ do NOT preserve anchors
→ do NOT preserve title wording
```

Freely:

```text
ADD
REMOVE
RENAME
REORDER
SPLIT
MERGE
REPLACE
```

when pedagogically better.

If body content exists:

```text
→ preserve valid meaning
→ but structure is still refactorable
```

The final Menu must be **clear for a beginner** appropriate to the module prerequisites.

It should naturally support:

```text
WHAT
→ what is this?

WHY
→ why does it exist?

RELATION
→ how does it connect to surrounding concepts?

HOW
→ how does the technology actually behave/work?

EVIDENCE
→ how can the learner observe or prove the model?
```

Do not mechanically name every H2 `WHAT`, `WHY`, `HOW`, etc.

Use natural technical titles and a learner progression appropriate to the module.

Only H2 identities approved by the **CURRENT STEP 3** become stable downstream Knowledge identities.

Run all generators and projection checks required by the current canonical STEP 3 rule, including when applicable:

- `generateInternalReadmeMenuForFile`;
- `generateInternalReadmeMenuForModule`;
- `BASE.md` updates;
- LIST generation;
- `generateFinalReadme`;
- metadata/path checks required by current canonical rules.

Then:

```text
REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 4 — KNOWLEDGE

Author/refactor the complete Knowledge surface from the CURRENT approved STEP 3 structure.

Do not trust historical prose automatically.

Reverify every important technical claim against the repository's current technology/version baseline and authoritative documentation.

For important concepts include, where useful:

```text
WHAT
WHY
RELATION
HOW
EXAMPLE
FAILURE MODEL
TRADE-OFF
BOUNDARY
EVIDENCE
```

Examples should establish a useful mental model rather than merely show syntax.

Be precise around version-sensitive behavior, edge cases, ordering, lifecycle, override/precedence rules, failure semantics, and ownership boundaries when they are relevant to the module.

Localized languages:

- same concept coverage;
- same stable anchors when required by repository contract;
- same intended semantics;
- natural localization;
- no unnecessary English-body leakage where localized prose is expected, except genuine technical terminology/code.

If Knowledge reveals a structural Menu problem:

```text
→ MENU GAP
→ return to STEP 3
→ fix STEP 3
→ re-run STEP 3 review gate
→ revalidate STEP 4
```

Then:

```text
IMPLEMENT
→ generators/build/parity/metadata validation
→ REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 5 — API / EXECUTABLE EXPERIMENT APPLICABILITY

STEP 5 MUST ALWAYS BE EXECUTED.

It may conclude:

```text
REQUIRED
```

or:

```text
NOT REQUIRED
```

but it must never be skipped.

ABSOLUTE DECISION RULE:

Do NOT decide applicability from:

- `MODULE_TYPE`;
- `BUILD_SWAGGER`;
- current controller count;
- current Java source layout;
- current Swagger files;
- current dependencies;
- historical absence of API;
- historical existence of API.

Correct decision direction:

```text
Curriculum / reconstructed approved scope
        ↓
approved Roadmap
        ↓
approved Menu
        ↓
approved Knowledge
        ↓
learning-value analysis
        ↓
REQUIRED / NOT REQUIRED
        ↓
only now inspect/reconcile config and implementation
```

Ask:

> Would bounded executable experiments materially improve understanding of this module compared with prose/examples alone?

Potential evidence mechanisms include, depending on the topic:

```text
HTTP/API response
startup/runtime logs
CLI output
tests
timing
state transitions
exception/failure output
configuration result
generated artifact inspection
runtime observation
```

Do NOT invent HTTP endpoints merely because a behavior can technically be exposed through HTTP.

Choose the best evidence mechanism.

HTTP API is justified only when it genuinely improves learner observability.

If REQUIRED:

```text
→ design bounded experiments from Knowledge
→ implement only what materially proves the mental model
→ adjust config after decision
→ Swagger/API docs only when HTTP is actually used
→ Local Run / execution instructions when applicable
→ validate expected behavior
```

If NOT REQUIRED:

```text
→ document/reconcile that decision
→ audit for stale controller/Swagger/config contradiction
→ remove obsolete learning API artifacts if necessary and allowed by canonical rules
```

Regardless of decision:

```text
DECISION / IMPLEMENTATION
→ REVIEW #1
→ fix/revalidate
→ REVIEW #2
→ fix/revalidate
→ CLEAN
```

---

## STEP 6 — VIDEO

STEP 6 must always be executed.

Treat existing Video as unapproved presentation content.

Knowledge is the technical source-of-truth.

Default mapping follows the CURRENT canonical rule, typically one Video source per Knowledge Menu when that remains the repository contract.

Video paths/H1/H2 must map correctly to Knowledge according to current canonical rules.

Video should turn concepts into useful visible evidence where possible.

Useful visuals may include:

- architecture/relationship diagrams;
- state/lifecycle diagrams;
- source/config snippets;
- code snippets;
- runtime logs;
- CLI output;
- generated artifacts;
- failure output;
- API experiment output when STEP 5 actually created one.

Do NOT copy README prose verbatim.

Use natural spoken narration.

Maintain conceptual parity across supported languages.

Validate:

- paths;
- H1/H2 identity/order;
- Scene structure;
- Transition structure;
- visual/narration completeness;
- timestamps when required;
- source → Portal projection;
- no placeholders.

Then:

```text
IMPLEMENT
→ generate/project/validate
→ REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 7 — QUIZ

STEP 7 must always be freshly implemented/re-evaluated.

Quiz should test **real reasoning about the module**, not superficial trivia.

Question domains should be derived from the approved Knowledge and may include:

- why a concept/mechanism exists;
- behavior prediction;
- ordering/precedence/lifecycle reasoning;
- comparison and distinction;
- failure analysis;
- misconception detection;
- trade-offs;
- ownership boundaries;
- practical evidence interpretation.

Do not produce trivia such as memorizing class names or property names without conceptual value.

Use plausible distractors.

Follow the current Quiz schema exactly.

Relations:

```text
exact Knowledge relation
or
blank
```

Do not invent approximate anchors.

Perform blind-answer leakage review:

Correct choice must not be obvious from:

- length;
- wording;
- specificity;
- grammar;
- being the only nuanced answer.

Localized Quiz versions must test the same concepts with semantically equivalent answers.

Then:

```text
IMPLEMENT
→ schema/projection/parity validation
→ REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 8 — INTERVIEW

STEP 8 must always be freshly implemented/re-evaluated.

Interview is explanation/reasoning practice, not Quiz-without-options.

Questions should encourage:

```text
WHY
WHEN
COMPARISON
TRADE-OFF
FAILURE ANALYSIS
DEBUGGING
MENTAL MODEL
DESIGN REASONING
PRODUCTION IMPLICATION
```

Choose interview topics from the approved Knowledge and module ownership rather than from an arbitrary quota.

Use exact-or-blank relations.

Language parity is required for concept, intent, answer semantics and relation identity.

Then:

```text
IMPLEMENT
→ schema/projection/parity validation
→ REVIEW #1
→ fix
→ revalidate
→ REVIEW #2
→ fix
→ revalidate
→ CLEAN
```

---

## STEP 9 — FULL INTEGRATED VALIDATION

STEP 9 must always run from scratch.

Do not rely on previous STEP-specific validation.

Validate the target module as **ONE curriculum**.

Structural validation should include every applicable check:

- Roadmap YAML;
- Reference structure;
- Menu structure;
- exact anchors;
- duplicate anchors;
- Knowledge metadata;
- localized file parity;
- localized anchor parity where required;
- Roadmap relations;
- Knowledge relations;
- API relations;
- Video mapping;
- Quiz schema;
- Interview schema;
- exact-or-blank relation validity;
- source ↔ generated consistency;
- Portal projections;
- placeholder scan;
- generated drift;
- module build;
- tests;
- runtime/API experiments when applicable;
- only intended git changes.

Learning audit should verify:

- beginner orientation appropriate to prerequisites;
- WHY before deep mechanics where pedagogically needed;
- clear mental model;
- technically correct behavior for the repository version baseline;
- useful failure/debugging/evidence coverage;
- correct cross-module ownership boundaries;
- no accidental duplication of neighboring curricula;
- Video usefulness;
- Quiz quality;
- Interview depth;
- localization semantic parity;
- no semantic loss from historical valid content.

Perform Coverage Review.

Do NOT optimize for arbitrary counts or percentages.

STEP 9 itself still requires:

```text
VALIDATION IMPLEMENTATION
→ independent REVIEW #1
→ fix
→ rerun relevant/full validation
→ independent REVIEW #2
→ fix
→ rerun validation
→ CLEAN
```

---

## UPSTREAM DEFECT ROUTING

If a downstream STEP finds a defect belonging upstream:

route it to the correct owner.

Examples:

```text
Knowledge reveals bad Menu structure
→ STEP 3

Menu reveals missing learning milestone
→ STEP 2

Video reveals missing Knowledge explanation
→ STEP 4

Quiz reveals ambiguous Knowledge semantics
→ STEP 4

API requires a genuinely missing curriculum concept
→ identify correct owner
→ route upstream
```

After upstream correction:

```text
rerun the affected STEP review gate
and all materially affected downstream validations/reviews
```

---

## INTEGRATED FINAL AUDIT BEFORE STEP 10

After STEP 9 is CLEAN:

perform one final whole-module audit.

Check:

- cross-step regression;
- semantic loss;
- Roadmap → Menu → Knowledge coherence;
- Step 5 decision/implementation consistency;
- Video drift;
- Quiz stale relations;
- Interview stale relations;
- localization mismatch;
- generated drift;
- placeholders;
- source ↔ generated mismatch;
- build/test/runtime failure;
- unrelated working-tree changes.

Fix every valid finding.

If a fix materially changes STEP 2–9:

```text
re-open that STEP's review gate
and revalidate affected downstream artifacts
```

Only proceed to STEP 10 when fully CLEAN.

---

## STEP 10 — COMMIT / PUSH / PR / MERGE / CLEANUP

STEP 10 does NOT require two learning reviews.

Follow CURRENT:

```text
module-generate-agent/STEP_10_COMMIT.md
```

exactly.

This Goal authorizes completion of the canonical STEP 10 lifecycle when allowed.

Expected sequence is whatever the CURRENT STEP 10 contract requires, typically including:

```text
final git isolation check
        ↓
commit only target-task changes
        ↓
push target module branch
        ↓
create PR/MR using canonical CLI flow
        ↓
inspect checks / mergeability
        ↓
merge when permitted
        ↓
verify remote commit
        ↓
verify remote main contains result
        ↓
cleanup remote source branch when safe
        ↓
cleanup disposable worktree
        ↓
delete local module branch safely
        ↓
prune/fetch
        ↓
fast-forward local main
        ↓
verify local main == origin/main
```

Do not use browser fallback unless current canonical rules explicitly allow it.

If external CLI/auth/check/permission/conflict blocks completion:

```text
STOP SAFELY
```

Preserve branch/worktree and report:

- exact blocker;
- exact branch;
- exact commit state;
- exact completed STEP status;
- exact next action required.

---

## DEFINITION OF DONE

The Goal is complete only when:

```text
STEP 2
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 3
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 4
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 5
→ freshly decided/implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 6
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 7
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 8
→ freshly implemented
→ >= 2 independent reviews executed sequentially on successive post-fix source states
→ CLEAN

STEP 9
→ freshly executed
→ >= 2 independent integrated reviews executed sequentially on successive post-fix source states
→ CLEAN

Integrated final audit
→ CLEAN

STEP 10
→ canonical commit/push/PR/merge/cleanup lifecycle completed,
  unless blocked by a genuine external CLI condition
```

NO STEP 2–9 may be skipped because artifacts already exist.

NO previous CLEAN result may substitute for the fresh execution required by this Goal.

NO build/generator/schema validation counts toward the two required reviews.

NO two mandatory reviews for the same STEP may be performed in parallel against the same source snapshot.

Proceed autonomously through STEP 2 → STEP 10 for the target module supplied by the invoking request.
