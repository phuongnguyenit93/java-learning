# STEP 5 — Learning API applicability: Functional Programming

**INITIAL DECISION: NOT REQUIRED.** This is an author's applicability result, **pending one independent full review**, not a completed STEP 5 gate.

## Curriculum / learning value — evaluated before reading implementation config

Evaluated the locked Paradigm Curriculum, seven approved Roadmap milestones in both languages, six stable Menu chapters, and all **33 substantive Knowledge H2 sections in each locale**. The primary objective is language-independent reasoning about pure computation, explicit inputs, immutable values, function composition, controlled effects, and trade-offs.

| Existing Knowledge / actual focus | What a learner should observe | HTTP experiment value |
| --- | --- | --- |
| PureFunctions: purity-concept, referential-transparency, hidden inputs | Repeated calculation with fixed arguments gives the same value; changing explicit discount changes the result; compare hidden live rates | **Not distinct.** Repeated identical HTTP JSON results cannot prove absence of hidden state, I/O, or time dependence. Small direct function tests and inspection of the calculation prove more. |
| Immutability: immutable-updates and immutability-limits | Previous snapshot [A,B] remains while next snapshot [A,B,C] is derived; two concurrent candidates based on version 3 need coordination | **Not distinct.** One API returning old/new JSON could use mutable implementation. Exposing conflict-aware persistence would shift to database/concurrency, not demonstrate the paradigm-level immutability claim. |
| FunctionsComposition: higher-order behavior, map/filter/reduce, walkthrough | [20,40,50] becomes [40,50], then [36,45], then 81; 100 discounted 10% then taxed 5% becomes 94.5 | **Not distinct.** Knowledge already shows every intermediate value. Direct CLI or unit assertions are smaller and avoid introducing an unrelated HTTP layer. |
| SideEffects: pure-core-effects, effect-coordination, effect-testing | Separate read from pure calculate and charge/save; test rejected payment, duplicate requests and failed persistence independently | **Low in this scope.** A fake HTTP gateway returning predetermined outcomes would merely simulate the effect, while a meaningful transaction/retry demo would need technology-specific persistence, idempotency and coordination owned by other modules. |
| Tradeoffs: functional-end-to-end and functional-costs | Compare loop versus composition, named stages, allocation and state ownership | **Knowledge/source-first.** This comparison is not a remote-action learning experiment. |

**Explicit candidate rejected:** A bounded POST pricing/transform endpoint with input cart, policy, intermediate discount/tax stages and JSON result could be implemented. However, its HTTP request/response adds no observation beyond the existing deterministic calculation trace; it cannot prove purity or deep immutability. A POST orders/payment endpoint would instead teach external system effects and concurrency/version checks. **No HTTP API is necessary for a learner to understand the approved H2 outcomes.** A minimal unit test or language-neutral CLI trace would be the more fitting executable proof, if desired in another surface.

## Existing implementation audit — performed AFTER applicability decision

The current master.json uses MODULE_TYPE=LIBRARY, BUILD_SWAGGER=FALSE, BUILD_TESTER=FALSE and BUILD_EXECUTION_CONTEXT=FALSE. These values **were not used to choose NOT REQUIRED**; they merely agree with the learning-value determination.

Inspected current Java source tree, controller/mapping patterns and Swagger source/metadata paths. No executable learning controller, Swagger learning descriptions, or stale runtime/API configuration contradicted the decision. No legacy human-authored learning API required cleanup. No web capability, controller, Swagger metadata, generator invocation, or unrelated module modification is justified. Existing approved Roadmaps, Menu, Knowledge, reference and metadata are preserved.

## Independent review gate

A different worker must independently read the current learning design and audit runtime/config before accepting this exception. If confirmed, a single full applicability review is sufficient. If overturned to REQUIRED, build the justified API and complete TWO new sequential independent full post-build reviews. No self-review, STEP 6, commit, push, merge or main worktree edits here.
