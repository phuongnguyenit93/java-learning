<a id="back-to-top"></a>

# Choosing AOP: Benefits, Risks, and Use Cases

## Menu
- [Reduced Duplication and Centralized Cross-Cutting Policies](#aop-benefits)
- [Implicit Behavior, Coupling, and Tracing Costs](#aop-cost)
- [Cross-Cutting Concerns Suitable for Aspects](#aop-good-fit)
- [Business Flows That Should Remain Explicit](#aop-bad-fit)
- [Paradigm Boundaries with AOP Frameworks and Technologies](#aop-boundary)
- [Broad Pointcuts, Overlapping Advice, and Unclear Ordering](#aop-pointcut-pitfalls)
- [From Repeated Logging or Auditing to an Aspect Design Decision](#aop-design-walkthrough)
- [Final Choice between Aspects, Helpers, Wrappers, and Decorators](#aop-explicit-alternative-decision)

## <a id="aop-benefits">Reduced Duplication and Centralized Cross-Cutting Policies</a>

<details>
<summary>Click for details</summary>

AOP can reduce duplicated code, centralize cross-cutting policies, and keep business code focused on its primary responsibility.

Reducing duplication is valuable when **one consistent policy** spans independent call paths. A single timing aspect can change the format of measurement across payment and invoice operations without editing their business algorithms. Testing the selected join points can also make the intended coverage explicit.

This is conditional: the pointcut must be stable, and maintainers must know that some effective behavior lives beyond the target source. A wrapper may be easier to understand overall when only two operations share a concern.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-cost">Implicit Behavior, Coupling, and Tracing Costs</a>

<details>
<summary>Click for details</summary>

Behavior may no longer be visible directly in the target source code.

This can make debugging, control-flow tracing, and execution-order reasoning harder when too many aspects exist or selection rules become too broad.

Someone reading `transferFunds` might not see the timer or auditing behavior that actually executes. A failure stack or observed output can then surprise anyone reasoning only from the method's source. The maintenance cost lives in the **effective execution model**, not just the number of aspect classes.

Debugging should start by asking which proxy/weaver the call crosses, which pointcut matches, and whether advice changes results or hides failures. Tests calling the target directly may miss the aspect; integration tests traversing the actual boundary will expose it.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-good-fit">Cross-Cutting Concerns Suitable for Aspects</a>

<details>
<summary>Click for details</summary>

Concerns are usually a good fit when they:

- repeat across many execution boundaries;
- depend little on the unique business meaning of each use case;
- have a clear application rule;
- can be tested independently.

Good candidates are relatively **independent of each operation's domain-specific meaning**, with a selection rule that can be verified. Measuring service-level latency or attaching correlation identifiers across calls can fit well. Security policy may also be cross-cutting but demands rigorous coverage and safe failure behavior.

Demonstrate at least one **matched** and one **unmatched** operation and their observable outcomes. If deciding to apply the concern requires extensive case-specific business data, it may be domain logic rather than suitable aspect advice.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-bad-fit">Business Flows That Should Remain Explicit</a>

<details>
<summary>Click for details</summary>

Important business decisions, primary workflows, or behavior that readers need to see directly should usually not be hidden behind aspects.

Practical rule:

```text
cross-cutting policy  → AOP may fit
core business flow    → prefer explicit code
```

A transfer workflow may require `validate account → reserve limit → post ledger entry → respond`. This is **the primary business story**, including possible compensation decisions; spreading its steps across advice can conceal order and make correctness difficult to establish.

An aspect can measure the whole transfer, but a critical approval decision should not become an invisible side effect of a broad pointcut. Explicit steps make reviews, tests, and failure handling agree on the same control flow.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-boundary">Paradigm Boundaries with AOP Frameworks and Technologies</a>

<details>
<summary>Click for details</summary>

This module owns AOP only at the paradigm level.

Concrete implementations such as framework proxies/interceptors or weaving engines should be learned in their technology-specific modules rather than moving all implementation detail into this module.

At the paradigm level, understand the concern, join point, pointcut, advice, target, and composition time. Spring proxy configuration, interceptor ordering, pointcut expression syntax, the AspectJ compiler, and transaction annotations are **technology-specific mechanisms**. Observing `@Transactional` is not evidence that all transaction behavior follows from AOP alone.

In the later Spring aspect/transaction modules, carry questions about calls through proxies, supported join points, errors, and transaction boundaries. This module supplies the reasoning framework rather than duplicating a framework tutorial.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-pointcut-pitfalls">Broad Pointcuts, Overlapping Advice, and Unclear Ordering</a>

<details>
<summary>Click for details</summary>

A pointcut selecting every `save*` method might include an unrelated save or miss `persistOrder`. It can be **overly broad or overly narrow**, not merely syntactically wrong. If two around advices overlap, measurement may become nested and security/logging order can depend on explicitly configured precedence.

Test a matrix: `transferFunds` should match, `formatMoney` should not, and normal completion, exceptions, and internal calls should behave as intended. Do not assume aspect order without defining and verifying the implementation's ordering rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-design-walkthrough">From Repeated Logging or Auditing to an Aspect Design Decision</a>

<details>
<summary>Click for details</summary>

Requirement: three services must record **elapsed time even on failure**, without logging account numbers. (1) Separate measurement from domain logic. (2) Select the intended service executions with a narrow pointcut. (3) Ensure advice records duration on both exit paths. (4) Verify the actual interception boundary. (5) Assert an unrelated operation remains unmatched.

```text
caller → [timing boundary] → transferFunds → success/error
       ← [elapsed + safe operation id] ←
```

Force `transferFunds` to throw: if no duration is recorded, the policy has failed. If a sensitive identifier leaks, the design is unsafe despite removing duplicated code. Explicitly specify what may be logged.

</details>

- [Back to top](#back-to-top)

---

## <a id="aop-explicit-alternative-decision">Final Choice between Aspects, Helpers, Wrappers, and Decorators</a>

<details>
<summary>Click for details</summary>

Choose a **helper** when callers must decide how to supply data, a **wrapper/decorator** when a visible boundary with the same contract is useful, and an **aspect** when many eligible execution points share a stable selection rule. None is automatically better simply because it uses fewer lines.

For three timed operations, explicit wrappers may be easier to inspect; for hundreds of truly uniform service operations, an aspect may be worthwhile. Make the final decision using evidence about matching coverage, failure semantics, observability, and the cost of future changes—not fashion or annotation count.

</details>

- [Back to top](#back-to-top)
