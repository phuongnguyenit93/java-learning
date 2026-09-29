# AOP Trade-offs

## <a id="aop-benefits">1. Benefits</a>

AOP can reduce duplicated code, centralize cross-cutting policies, and keep business code focused on its primary responsibility.

## <a id="aop-cost">2. Costs</a>

Behavior may no longer be visible directly in the target source code.

This can make debugging, control-flow tracing, and execution-order reasoning harder when too many aspects exist or selection rules become too broad.

## <a id="aop-good-fit">3. Good-fit concerns</a>

Concerns are usually a good fit when they:

- repeat across many execution boundaries;
- depend little on the unique business meaning of each use case;
- have a clear application rule;
- can be tested independently.

## <a id="aop-bad-fit">4. Poor-fit concerns</a>

Important business decisions, primary workflows, or behavior that readers need to see directly should usually not be hidden behind aspects.

Practical rule:

```text
cross-cutting policy  → AOP may fit
core business flow    → prefer explicit code
```

## <a id="aop-boundary">5. Boundary with concrete implementations</a>

This module owns AOP only at the paradigm level.

Concrete implementations such as framework proxies/interceptors or weaving engines should be learned in their technology-specific modules rather than moving all implementation detail into this module.
