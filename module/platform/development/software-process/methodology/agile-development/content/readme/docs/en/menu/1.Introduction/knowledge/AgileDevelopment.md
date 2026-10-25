# Agile Software Development

## <a id="agile-what">1. What is Agile?</a>

Agile is a **software-development approach based on values and principles** that emphasizes early feedback, collaboration, adaptive planning, and incremental delivery of value.

Agile is not one framework, and it is not synonymous with Scrum.

```text
Agile
→ umbrella of values / principles

Scrum
→ framework

Kanban
→ method for managing flow

XP
→ software development method with strong engineering practices
```

## <a id="agile-why">2. Why does Agile exist?</a>

Software projects often contain uncertainty: requirements change, user feedback arrives late, and technical risks become visible only after implementation starts.

If plans, designs, and scope are locked too early, the cost of correcting a wrong direction can become high.

Agile reduces feedback delay by putting planning, implementation, verification, and learning into shorter loops.

## <a id="agile-without">3. Where can a simpler sequential approach become insufficient?</a>

Sequential lifecycles can work well when requirements are stable and change cost is low.

Under high uncertainty:

```text
plan very early
→ build for a long time
→ receive feedback very late
→ discover wrong direction when correction is expensive
```

Agile does not remove planning; it makes planning **continuous and adaptive**.

## <a id="agile-model">4. Mental model</a>

```text
small plan
→ build
→ verify
→ deliver / inspect
→ feedback
→ adapt
→ repeat
```

Central ideas include iterative delivery, incremental value, short feedback loops, transparency, and adaptation.

## <a id="agile-methods">5. Where do Scrum, Kanban, XP, and Lean fit?</a>

**Scrum** provides a framework with accountabilities, events, and artifacts for iterative work.

**Kanban** focuses on visualization, flow, work-in-progress, and continuous improvement.

**Extreme Programming (XP)** emphasizes engineering practices such as fast feedback, testing, refactoring, and continuous integration.

**Lean Software Development** focuses on value, waste reduction, flow, and learning.

They are related to Agile but are not equivalent to Agile itself.

## <a id="agile-boundary">6. Boundary</a>

This module owns the Agile family at the methodology level.

TDD/BDD/ATDD are studied more deeply in `methodology/driven-development`. CI/CD and deployment automation belong to delivery/tooling concerns rather than being canonically owned by the Agile module.
