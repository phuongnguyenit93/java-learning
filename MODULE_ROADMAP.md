# Module Roadmap Architecture

## 1. Purpose

This file defines the canonical **roadmap-first learning architecture** for every learning module in `java-learning`.

The central dependency is:

```text
Module / Topic
    ↓
Roadmap Skeleton
    ↓
Module Roadmap
    ↓
README / Knowledge Menu structure
    ↓
Knowledge / Lessons
    ↓
API Docs / executable evidence when applicable
    ↓
Quiz
    ↓
Interview
    ↓
Integrated Coverage Review
```

The roadmap is an **upstream curriculum artifact**, not a visualization generated after Knowledge already exists.

If the roadmap is wrong, downstream content must not compensate by silently inventing a different curriculum.

---

## 2. Roadmap plugin contract

The roadmap capability is implemented as a dedicated module setup plugin/workflow with two phases:

```text
Phase A — Roadmap Skeleton
→ define the reusable planning questions and quality gates

Phase B — Module Roadmap
→ instantiate an ordered learning journey for one concrete module/topic
```

The plugin must not start by reading generated Knowledge and reverse-engineering a roadmap from it. For an existing module, the preferred migration flow is:

```text
module identity + curriculum boundary + neighboring modules
        ↓
design roadmap independently
        ↓
review roadmap
        ↓
compare existing Knowledge against the approved roadmap
        ↓
classify: aligned / missing / misplaced / too deep / duplicate / out of scope
```

This prevents historical content order from becoming the new roadmap by accident.

### On-disk contract

Roadmap files are localized and live inside each module:

```text
<module>/
└── roadmap/
    ├── vi/
    │   └── roadmap.yml
    └── en/
        └── roadmap.yml
```

`MODULE_LANGUAGE` decides which language skeletons exist. `BUILD_ROADMAP` enables/disables the capability and defaults to `TRUE` in the canonical `automation/master.json`.

Each language owns its own roadmap. The build system does **not** require different languages to have the same node ids, node count, ordering or dependencies.

The Gradle generator owns only the generated schema/comment block. The actual `roadmap:` content is human/AI-owned and must not be overwritten during normal synchronization.

---

## 3. Roadmap Skeleton

The skeleton is a planning framework, not a fixed list of chapters. A module does **not** need one node for every skeleton question.

The skeleton should force the designer to consider at least these dimensions:

```text
PURPOSE
→ What is this module/topic?
→ Why does it exist?
→ What problem exists without it?

FOUNDATION
→ What prerequisite mental model must exist first?

CORE MODEL
→ What are the major abstractions/building blocks?

MECHANICS
→ How does the language/runtime/API make the idea work?

USAGE
→ How is it used in real code?

DECISION / TRADE-OFF
→ When should one mechanism be chosen over another?

FAILURE / PITFALL
→ What mistakes, compile-time failures or runtime failures matter?

INTEGRATION
→ How does the topic connect to neighboring Java concepts/modules?

SYNTHESIS
→ What end-to-end mental model should the learner retain?
```

These are **design questions**, not mandatory visible headings.

---

## 4. Module Roadmap rules

A roadmap contains **learning milestones**, not individual facts, methods, keywords or isolated syntax items.

Good roadmap granularity:

```text
Purpose
→ Type Model
→ Control Flow
→ Methods & Data Flow
→ Scope & Lifetime
→ Arrays
→ Synthesis
```

Too granular for roadmap level:

```text
add()
remove()
get()
size()
for
while
byte
short
```

Those belong in the Knowledge Menu or lesson content.

Each roadmap node should establish:

```text
stable identity
title
learning purpose
why this node exists here
dependency on earlier nodes when relevant
what the learner should understand before moving on
which conceptual area downstream Knowledge may expand
```

One roadmap milestone may own several Knowledge chapters/sections.

### Related Knowledge — marker navigation

A roadmap milestone may optionally declare `relatedKnowledge`. Values are Knowledge category ids from the **same module**:

```yaml
roadmap:
  - id: type-system-basics
    title: Type System Basics
    relatedKnowledge:
      - PrimitiveReference
      - WrapperBoxing
      - Casting
      - Null
      - TypeSystemMentalModel
```

Portal behavior:

```text
milestone number marker
→ subtle pulse when Related Knowledge exists
→ Related Knowledge is always visible on the opposite side of the milestone
→ choose Knowledge category
→ switch to Knowledge tab
→ activate that category in the Knowledge topic strip
```

`relatedKnowledge` is supporting Knowledge navigation only. It does not alter roadmap node order and does not mean that every category is exclusively owned by one milestone.

Lifecycle/ownership rule:

```text
design + approve milestone/order first
        ↓
derive/refine Knowledge structure
        ↓
add/refine relatedKnowledge navigation mapping
```

For a legacy module, an existing Knowledge category may be mapped provisionally even when the semantic fit is imperfect. That mapping is migration/navigation metadata, not evidence that the historical Knowledge order should define the roadmap.

### Related modules — Portal level 2

A roadmap milestone may optionally declare `relatedModules`. These are **secondary navigation cards**, not child roadmap milestones and not a second learning-order graph.

```yaml
roadmap:
  - id: type-system-basics
    title: Type System Basics
    purpose: ...
    relatedModules:
      - routeId: JAVA_OOP
        label: Object-Oriented Programming
        note: Type hierarchy, inheritance and polymorphism extend this model.
      - routeId: JAVA_GENERICS
        label: Generics
        note: Compile-time type safety and parameterized types.
```

Contract:

```text
Level 1
→ current module milestone
→ owns learning order

Level 2
→ related module card
→ optional
→ clickable Portal navigation
→ does not change milestone order
→ does not imply that the related module is a child curriculum node
```

`routeId` is the stable Portal/module identity used for navigation. `label` and `note` are localized presentation text owned by the current language roadmap.

`relatedModules` expresses adjacency/continuation across module boundaries. It does **not** express containment, prerequisite ownership or a child roadmap hierarchy unless a separate curriculum rule explicitly says so.

---

## 5. Roadmap approval gate

No Knowledge Menu or new lesson content should be generated until the roadmap has passed a roadmap review.

The review checks:

```text
missing prerequisite?
wrong learning order?
scope leakage into another module?
missing practical concern?
node too implementation-specific?
node too granular?
duplicate responsibility?
important concept isolated from its motivation?
learner can explain the module end-to-end after following the path?
```

If a downstream generator finds a curriculum gap, it must report a **ROADMAP GAP**. It must not silently alter the learning journey.

---

## 6. README / Knowledge generation from roadmap

After roadmap approval:

```text
Roadmap node
    ↓
Knowledge Menu sections needed to satisfy that milestone
    ↓
lesson content for those sections
```

The README/Knowledge generator asks:

> What Knowledge sections are required for the learner to complete this roadmap node?

It must not independently ask:

> What facts about this Java topic can I list?

The existing chapter-level authoring rule still applies inside each section:

```text
WHAT
→ WHY
→ problem without it / limitation before it
→ RELATION
→ HOW
→ EVIDENCE / example
→ PITFALL / TRADE-OFF when relevant
→ PRACTICAL USE
```

---

## 7. Downstream artifact rules

All later artifacts inherit scope from the approved roadmap and Knowledge structure:

```text
Roadmap
    ↓
Knowledge
    ├── API Docs / experiments prove observable behavior
    ├── Quiz tests learning objectives and misconceptions
    └── Interview tests explanation, trade-offs and practical reasoning
```

API Docs, Quiz and Interview must never become alternate curriculum designers.

Raw count is not a success metric. Traceability and coverage of the roadmap are more important than volume.

---

## 8. Portal presentation

The Learning Portal should present **Roadmap next to Menu** so the learner can distinguish the two levels:

```text
Roadmap
→ where am I?
→ what comes next?
→ how do the major concepts connect?
→ which Knowledge categories support this milestone?
→ which neighboring modules can I continue into?

Menu
→ what exact Knowledge sections can I open?
```

Roadmap interaction should navigate/highlight **roadmap milestones or chapter/section headings**, not individual sentences or tiny knowledge facts.

Current Portal visual contract:

```text
milestone card       numbered marker       Related Knowledge
     left/right      on center spine        opposite side
                          ↓
                    subtle pulse when
                    relatedKnowledge exists

Related Knowledge
→ always visible when configured
→ same-module Knowledge category navigation
→ item shows the current category section count when resolvable

Related Modules
→ secondary/satellite cards around the milestone card
→ cross-module navigation
→ not a nested roadmap graph
```

On narrower screens the layout may collapse to a single-column timeline, but the semantic distinction between milestone, Related Knowledge and Related Modules must remain.

The Portal is a projection/consumer only. It must not become the source of truth for roadmap design.

---

## 9. Source-of-truth invariant

For learning architecture, the precedence is:

```text
Curriculum boundary / module scope
        ↓
approved Module Roadmap
        ↓
README / Knowledge Menu
        ↓
Knowledge lessons
        ↓
API Docs / Quiz / Interview
        ↓
Portal projection
```

When an older module disagrees with a newly approved roadmap, treat that as migration/audit work. Do not redefine the roadmap merely to preserve accidental historical ordering.
