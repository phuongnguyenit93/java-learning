# STEP 5 — AOP learning API applicability (initial author)

**Decision: NOT REQUIRED — pending one independent FULL STEP 5 applicability review.** This decision comes from the approved curriculum and concrete learning evidence, not from existing module flags or source layout. It is not yet STEP 5 CLEAN.

## Curriculum and candidate experiments

The locked Curriculum assigns this module **paradigm-level** understanding of cross-cutting concerns, aspects, join points, pointcuts, advice, weaving versus interception, effective execution, and selection/design trade-offs. Seven approved Roadmap milestones are explained by 31 anchored H2 per locale, in four chapters. Detailed Spring proxy configuration, bean lifecycles, @AspectJ integration, AspectJ compiler/weaver mechanics and transaction internals belong to their dedicated implementation modules.

| Knowledge identity | Useful real observation | Why an HTTP learning endpoint does not materially improve it |
| --- | --- | --- |
| `2.Terminology/Terminology.md`: `aop-selection-example`, `aop-effective-execution` | Trace matched and unmatched calls, around advice with 0/1/multiple continuations, and target side effects/errors | A direct invocation harness/test with a real aspect or an explicit wrapper records the same genuine call trace; HTTP is an unrelated triggering/serialization layer, not the point being taught |
| `3.ImplementationModels/ImplementationModels.md`: `aop-interception-limits` | Compare external invocation through a Spring proxy with a target `this.verify()` call bypassing the proxy | A call-level Spring test can vary **proxy versus target** precisely, without requiring web requests; implementing this here as HTTP would introduce Spring-specific prerequisites owned by `programming/framework/spring-framework/aspect` |
| `3.ImplementationModels/ImplementationModels.md`: `aop-aspectj-spring-boundary` | AspectJ weaving observes some join points not exposed by Spring proxy-based method interception | Faithful evidence needs separate weaving and interception setups. A single Spring HTTP controller cannot demonstrate AspectJ field-access/constructor join points; conflating the two would misteach the model |
| `4.Tradeoffs/Tradeoffs.md`: `aop-design-walkthrough` | Verify timing advice on success and failure, selected/unselected operations, and no sensitive values in logs | Call-level integration tests and a controlled trace sink provide more discriminating evidence; returning a precomputed log as JSON adds no new proof |

This module teaches a learner to **identify, predict and evaluate** effective AOP execution before learning a particular framework. A runnable test harness in an implementation-specific module is useful; it does **not** make an HTTP controller necessary in this language-neutral paradigm module. A web endpoint would require proxy setup plus web runtime/serialization and would obscure whether the observed difference came from AOP interception or routing. We should not fabricate runtime, Swagger, or claims about weaving simply to create API coverage.

## Implementation audit — performed only after the conceptual decision

- Isolated worktree `.wt/paradigm/aop`, branch `module/aop`, original HEAD `9d3433b8`; valid STEP 2–4 changes remain uncommitted.
- Current canonical `master.json` values: `MODULE_TYPE=LIBRARY`, `MODULE_LANGUAGE=[vi,en]`, `BUILD_README=TRUE`, `BUILD_SWAGGER=FALSE`, `BUILD_EXECUTION_CONTEXT=FALSE`. These values are **consistent with**, not reasons for, the independent applicability conclusion.
- Physical child source has **no** existing Java learning controller/service, @Controller/@RestController, runtime application or `src/main/resources/swagger/{vi,en}` files. The existing `knowledge-metadata.yml` records are README governance, not Swagger/API. There are no legacy API documents or stale controller relations to remove.
- Consequently no runtime dependency/config migration, Swagger setup, Gradle generator or projection mutation is indicated. Preserve all approved Roadmap, Knowledge, menu anchors, metadata and generated README output.

## Independent review gate

One **different independent reviewer** must read the CURRENT complete Curriculum, Roadmaps and Knowledge, examine these counterfactual experiment claims, and audit config/source anew. If confirmed NOT REQUIRED, the module can pass STEP 5 after any valid reconciliation. If overturned to REQUIRED, build a real observable API and run two **new sequential full independent post-build reviews**; this no-API decision does not count as either review. No automatic STEP 6 advancement from this initial decision.
