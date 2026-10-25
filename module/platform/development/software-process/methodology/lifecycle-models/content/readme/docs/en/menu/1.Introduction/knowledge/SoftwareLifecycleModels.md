# Software Development Lifecycle Models

## <a id="lifecycle-what">1. What is a Lifecycle Model?</a>

A Software Development Lifecycle Model organizes **development stages and their relationships over time**.

It answers questions such as:

```text
When are requirements handled?
Does design happen before or alongside implementation?
When does feedback return?
Are releases one large batch or many smaller increments?
```

A lifecycle model does not describe syntax or code organization. It describes the **flow of the development process**.

## <a id="lifecycle-why">2. Why do Lifecycle Models exist?</a>

Software development includes discovery, requirements, design, implementation, testing, release, and maintenance.

Without a shared model for ordering and feedback between those activities, teams can suffer unclear hand-offs, late feedback, stale plans, and testing or integration deferred until the end.

A lifecycle model gives the team a shared mental model of **how work progresses**.

## <a id="lifecycle-before">3. Does every project need a rigid named model?</a>

No.

Even when a team does not name a formal model, its real workflow still creates a lifecycle:

```text
plan
→ build
→ verify
→ release
→ learn
```

The important part is understanding feedback loops, sequencing, and the ability to revisit earlier stages.

## <a id="lifecycle-model">4. Important axes</a>

```text
Sequential        ↔ Iterative
Big-batch         ↔ Incremental
Late feedback     ↔ Continuous feedback
Fixed planning    ↔ Adaptive planning
```

Waterfall, Iterative, Incremental, Spiral, and V-Model differ mainly in how they organize these axes.

## <a id="lifecycle-relations">5. Relationship with Agile</a>

Lifecycle Models and Agile are not strictly peer concepts.

Agile emphasizes values, principles, feedback, and adaptive delivery; an Agile team commonly uses iterative and incremental lifecycles.

This module owns **lifecycle structure**. Agile principles and Scrum/Kanban/XP belong to the sibling `methodology/agile-development` module.
