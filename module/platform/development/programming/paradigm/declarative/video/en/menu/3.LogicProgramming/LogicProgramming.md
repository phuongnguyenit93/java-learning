---
video:
  url: ""
---

# Logical Facts, Relations, and Inference

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Logic Programming as a Way to Describe Relations

<!-- VIDEO_SECTION -->

### Scene 1 — Logic Programming as a Way to Describe Relations

**Time:** `00:00–01:18`

**Visual:**

Display An→Binh→Chi and mark two accepted parent facts in a knowledge base.

**Script:**

Logic programming starts from accepted facts about relationships and rules that support further conclusions. If An is a parent of Binh and Binh is a parent of Chi, we can ask how these facts justify a grandparent relationship. We have not specified an algorithm for visiting people. Different logical systems still define how such rules are interpreted and how evidence is searched, so the relational model is the starting point, not a claim of universal execution behavior.

**Purpose:**

Ground logic programming in a beginner-readable relationship.

## Logical Facts and Known Relations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:30`

**Visual:**

Contrast parent(An,Binh) on a relationship graph with "user clicked at 10:20" on a time line.

**Script:**

We keep saying fact. Is a logical fact the same thing as an event arriving over time?

**Purpose:**

Distinguish a knowledge assertion from a timestamped event.

### Scene 1 — Logical Facts and Known Relations

**Time:** `01:30–02:48`

**Visual:**

Contrast an enduring parent assertion with a timestamped click event, labeling FACT and EVENT separately.

**Script:**

A logical fact is an assertion accepted in a knowledge base, such as the parent relationship between An and Binh. It is not an event notification that happened at a particular instant. Adding parent(Binh,Chi) gives another relation to reason from. An event could later be recorded as a fact in some application, but their meanings remain different. Do not confuse a logic query with the stream of signals studied in reactive programming.

**Purpose:**

Prevent confusing fact databases with event streams.

## Inference Rules and Logical Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:48–03:00`

**Visual:**

Highlight the two parent edges, then reveal a new dashed grandparent edge from An to Chi only after both premises light up.

**Script:**

Two facts are visible. How can we obtain a new conclusion that was not listed as a fact?

**Purpose:**

Introduce inference and a witness variable.

### Scene 1 — Inference Rules and Logical Conditions

**Time:** `03:00–04:18`

**Visual:**

Highlight both parent edges and reveal grandparent An→Chi only after choosing witness y=Binh.

**Script:**

A grandparent rule says that x is a grandparent of z when some y makes both parent(x,y) and parent(y,z) true. Here Binh is that middle person, so An is a grandparent of Chi under the stated facts. The rule expresses the necessary relationships; it does not prescribe whether the solver should inspect An or Chi first. If either premise lacks support, the rule does not establish the conclusion.

**Purpose:**

Show premises, shared variable and derived relationship.

## Query Goals and Satisfying Conclusions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:18–04:30`

**Visual:**

Keep An–Binh–Chi on the left; show the goal grandparent(Who,Chi) on the right, and reveal Who=An after highlighting both edges.

**Script:**

The rule now supports a relationship. How does a learner ask for the person who satisfies it?

**Purpose:**

Move from a rule to a goal and solution.

### Scene 1 — Query Goals and Satisfying Conclusions

**Time:** `04:30–05:48`

**Visual:**

Show grandparent(Who,Chi), trace through Binh, then reveal the substitution Who=An.

**Script:**

A goal is the proposition or relationship we want to check. Asking whether An is a grandparent of Chi is one possible goal. Asking who is a grandparent of Chi asks a solver that supports variables to find matching values; on this small graph, An qualifies. A larger knowledge base can provide several answers, so do not assume a relational goal automatically has a unique solution.

**Purpose:**

Explain queries that answer yes/no or bind variables.

## Relationship Specifications versus Search Algorithms

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:48–06:00`

**Visual:**

Keep the grandparent rule fixed while showing alternative search paths starting at An or at Chi, with a recursion warning on a third path.

**Script:**

We can state the proof rule without specifying a walk. Does that mean searching is unnecessary?

**Purpose:**

Introduce the search/termination boundary.

### Scene 1 — Relationship Specifications versus Search Algorithms

**Time:** `06:00–07:18`

**Visual:**

Illustrate two search orders on the same graph and a recursive branch with a termination warning.

**Script:**

A relation specifies which premises justify a conclusion. A solver still chooses a search or inference strategy. Different approaches can consume different resources, emit answers in different orders and behave differently on recursive rules. Some badly constrained searches may not terminate. Not having found a conclusion should not always be described as having proved it false; the system's logical assumptions and evaluation method matter.

**Purpose:**

Keep search strategy separate from relational meaning.

## Boundary between Logic Models and Prolog Implementations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:30`

**Visual:**

Move the fact–rule–goal board next to a Prolog label; put unification, clause ordering and interpreter details outside the conceptual boundary.

**Script:**

These diagrams resemble Prolog notation. Have we actually executed a Prolog program?

**Purpose:**

Mark the handoff to the technology-specific language.

### Scene 1 — Boundary between Logic Models and Prolog Implementations

**Time:** `07:30–08:48`

**Visual:**

Place conceptual fact/rule/goal beside a separate Prolog syntax panel showing the technology ownership boundary.

**Script:**

Prolog is one language that expresses facts, rules and query goals. But understanding its variable unification, clause selection and search behavior requires learning how that actual interpreter works, including cases that do not terminate. The parent relation written on this slide is conceptual notation, not a runnable transcript. A real Prolog lesson should test a real engine; we should not substitute an invented HTTP response for its semantics.

**Purpose:**

Stop at the paradigm boundary with honest evidence.
