# STEP 5 — Data-Oriented Programming executable learning API

**INITIAL DECISION: REQUIRED, implemented and locally validated; pending TWO fresh sequential independent full post-build reviews.** This is not a self-review and does not mark STEP 5 CLEAN.

## Curriculum-first applicability

The locked Paradigm Curriculum establishes Sharvit-style DOP as the primary model, with four principles: code/data separation; generic inspectable maps/lists; immutable values; independent schemas. Evaluated eight approved bilingual Roadmap milestones, all eight Step 3 chapter identities and **40 fully authored H2 lessons in both EN and VI** before consulting the existing runtime. The most useful experiment is the *real trust boundary* in DataSchema/DataFlow, not a Java record tutorial or a database/payment simulator.

| Exact Knowledge focus | Required learner observation | Why interactive HTTP exceeds a static or fixed CLI trace |
| --- | --- | --- |
| 3.GenericData / generic-domain-example, generic-failure-modes | Change the shape and nested values of the order map; a typo/missing field must not produce a plausible subtotal | JSON is an actual external representation chosen by the learner, not a preselected in-process fixture. Different submissions reveal the schema contract and rejected field paths. |
| 5.DataSchema / dop-schema-concept, dop-validation-boundaries, dop-validation-failure | The *same* incoming representation can be accepted or rejected by an independent schema. Rejection names the path (such as lines[1].qty), expected rule, and observed numeric value | Live untrusted HTTP payloads exercise the boundary; unlike a static success-only demo, users can remove fields, send strings instead of integers, add unexpected fields or negative quantities and inspect distinct HTTP 422 outcomes. |
| 4.ImmutableData / dop-state-update and 6.DataFlow / dop-flow-transform | For valid data, see the original order nested map and a separately created discounted result without mutating the input or persisting state | A response exposes both snapshots from the *same request* plus subtotal and derived total. Learners can compare different request bodies; HTTP alone does not prove deep immutability, so independent copy code and repeated tests remain part of the evidence. |
| 6.DataFlow / dop-flow-scenario, dop-flow-validate, dop-flow-observe | Observe one receive → validate → calculate → produce-new-value path, and separate domain data from validation and operations | One real request crosses a trust boundary; invalid input stops *before* calculation instead of being paper-only. This is distinct from simulating payment/transaction side effects. |

**Bounded experiment:** POST /paradigm/data-oriented/orders/preview?discountPercent=10, with a generic JSON order object. Example: {"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30},{"sku":"Q","qty":1,"price":40}]}. On acceptance, return HTTP 200, before snapshot and after snapshot; subtotal 100, discountedTotal 90.00. On a negative second quantity or missing price, return HTTP 422 with precise nested path and reason. No data is persisted, no external payment is invoked, no shared state or background resources exist. Both the number of lines (1..20) and field quantities/prices/discount are bounded.

The API is **not** intended to prove that all maps are superior to typed models, that immutability solves version conflicts, or that every HTTP operation is intrinsically DOP. Java/Project Amber typed models and performance-oriented DOD remain explanatory Knowledge, not fabricated runtime demonstrations.

## Implementation reconciliation — performed AFTER choosing REQUIRED

- Originally the current module was LIBRARY with BUILD_SWAGGER=FALSE and no Java controller. Those are *implementation gaps*, not reasons to omit a high-value trust-boundary experiment.
- Canonical master.json now sets MODULE_TYPE=SERVLET and BUILD_SWAGGER=TRUE. Repository Gradle setup generated the Boot application, appropriate Spring MVC starter and Swagger Servlet adapter dependencies. No ad hoc dependency injection or generated build.gradle edits.
- One OrderPreviewController POST maps to readme **6.DataFlow/DataFlow.md** / anchored **dop-flow-scenario**, with exact generated method signature preview(Map<String,Object>,int). Controller has only HTTP binding and status selection; OrderShapeSchema separately defines/validates the external map shape; OrderPreviewService separately calculates from accepted generic data and derives a new result. Code remains within this child worktree only.
- Both localized Swagger sets include controller-description.yml, api-descriptions.yml, api-execution.yml and api-params.yml with substantive rationale, varied input experiments, exact Knowledge mapping, and clear observation/conclusion.

## Initial author validation and independent gate

JDK 21 canonical setup, compileJava, generateApiSwaggerDescription and bootJar were executed successfully. A live local HTTP smoke run returned **200** for the valid two-line order (subtotal **100**, discounted total **90.00**, original qty **2**) and **422** for negative second quantity at path **lines[1].qty** and unexpected fields; the launched application was shut down afterward. Additional post-edit build and metadata/source-integrity checks are recorded in the Prime handoff.

The **initial author must not review their own work**. Two fresh, sequential, independent full reviews of the *completed built API* must inspect learning value, source, runtime evidence, Swagger bilingual documentation, Knowledge relation and original uncommitted steps. Fix and revalidate after review findings; only then can the parent mark this child STEP 5 CLEAN and schedule its STEP 6. No Step 6/commit/push/merge or main worktree changes here.
