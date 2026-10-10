---
video:
  url: ""
---

# Behavior Composition and AOP Implementation Boundaries

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


## Compile-Time Aspect Weaving

<!-- VIDEO_SECTION -->

### Scene 1 — Compile-Time Aspect Weaving

**Time:** `00:00–01:07`

**Visual:**

Show an unmodified TransferService.java pane and a conceptual compiler input [target + aspect] → woven class bytecode output; emphasize build-time.

**Script:**

In compile-time weaving, the bytecode is produced with the aspect behavior already connected before the application starts. AspectJ offers this model. That is why reading only the original target source does not reveal all effective behavior. For this paradigm lesson, what matters is when the composition occurs and what join points the weaver can represent, not specific compiler flags.

**Purpose:**

Teach compile-time composition without claiming a proxy or a fabricated compiled trace.


## Load-Time Aspect Weaving

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:07–01:19`

**Visual:**

Keep the compile-time target/aspect-to-bytecode chart and move its weaving marker from BUILD to CLASS LOAD before first use.

**Script:**

A woven result exists by the time the build finishes, but what if the bytecode is transformed later during loading?

**Purpose:**

Clarify that the transformation time changes while the mechanism remains bytecode weaving.

### Scene 1 — Load-Time Aspect Weaving

**Time:** `01:19–02:26`

**Visual:**

Move the same target/aspect cards to a class-loading bar: original bytes → loader transformation → running class, with a clear barrier before first use.

**Script:**

Load-time weaving changes class bytes while a class is being loaded. The original file need not have been woven at compile time, but the transformation must happen before that class starts executing in its ordinary form. A debugger or deployment check should confirm the actual loader configuration. This is different from adding a proxy object around an already existing target.

**Purpose:**

Distinguish load-time bytecode transformation from compile-time and proxy interception.


## Runtime Interception through an Intermediate Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:26–02:38`

**Visual:**

Replace the class-loader timeline with separate caller and target objects; insert timingProxy between them and show a red bypass arrow.

**Script:**

Now compare the third model, where the caller crosses a separate runtime object boundary.

**Purpose:**

Contrast transformed bytecode with a runtime intermediary that only sees calls crossing it.

### Scene 1 — Runtime Interception through an Intermediate Boundary

**Time:** `02:38–03:45`

**Visual:**

Animate externalCaller → timingProxy → transferService; then draw a red shortcut externalCaller → target to show missed interception.

**Script:**

A proxy-based interceptor stands on the call path. When a caller invokes the proxy, it can run timing logic and delegate to transferFunds. If the call does not pass through that proxy, this particular interception does not happen. No target bytecode needs to be woven just to explain this mechanism; the object reference and actual path are the important evidence.

**Purpose:**

Establish the causal call path for runtime method interception.


## Observation Scope and Limitations of Composition Models

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:57`

**Visual:**

Keep the green proxied path and red bypass path, then unfold a join-point grid of method execution, method call, constructor and field set.

**Script:**

Those mechanisms seem similar from the caller view, but their coverage is not identical.

**Purpose:**

Generalize one intercepted call into the capabilities and limitations of each model.

### Scene 1 — Observation Scope and Limitations of Composition Models

**Time:** `03:57–05:04`

**Visual:**

Make a four-column matrix: execution join point, method call, constructor, field set. Mark model-dependent cells; highlight proxy-only eligible method execution.

**Script:**

Before claiming an aspect covers an event, identify what event the technology can actually see. A method-execution proxy cannot select arbitrary field assignments simply by writing a clever pointcut. A woven AspectJ model can represent more kinds of join points, depending on its configuration. Use this matrix to ask about mechanism, eligible objects and bypass paths rather than judging from notation alone.

**Purpose:**

Make capabilities and limitations explicit before introducing framework names.


## AspectJ Weaving versus Spring AOP Proxies: Different Join Point Models

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:16`

**Visual:**

Split the uncovered FIELD SET grid cell into AspectJ-woven and Spring-proxy columns; distribute constructor and field markers only to eligible categories.

**Script:**

A concrete contrast clarifies why two tools using similar AOP words are not identical.

**Purpose:**

Use real join-point types to show why Spring proxying and AspectJ weaving differ.

### Scene 1 — AspectJ Weaving versus Spring AOP Proxies: Different Join Point Models

**Time:** `05:16–06:23`

**Visual:**

Side-by-side diagrams: AspectJ woven target with arrows at field/constructor/method join points; Spring proxy wrapping method execution on managed bean. Add annotation @Aspect ≠ woven bytecode.

**Script:**

AspectJ can weave the class model itself and expose method calls, constructor events or field access join points. Spring AOP normally advises method execution reached through a proxy around Spring-managed beans. Using an AspectJ-style annotation inside Spring does not, by itself, turn on the AspectJ weaver. To demonstrate field writes faithfully, we would need a genuine weaving setup, not an HTTP request routed through a Spring controller.

**Purpose:**

Correctly separate AspectJ join point breadth from Spring AOP interception boundary and prevent annotation conflation.

### Scene 2 — Stress-test the model

**Time:** `06:23–07:16`

**Visual:**

Zoom into a single field assignment balance = balance - 100; mark AspectJ woven field-set join point possible and Spring method-execution proxy NO FIELD JOIN POINT.

**Script:**

Consider this field assignment inside transferFunds. It is not a separate Spring AOP method-execution join point just because it mutates a field. With a correctly configured AspectJ weaving model, field-set selection may be possible. That distinction is about the mechanism and join point model, not which annotation looks more expressive.

**Purpose:**

Test the boundary with one concrete field event rather than conflating method interception with weaving.


## Interception Boundaries and Internal Calls in Proxy Models

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:16–07:28`

**Visual:**

Keep balance = balance - 100 marked as a possible AspectJ field-set join point, then reveal proxy.execute leading to a red this.verify internal path bypassing proxy advice.

**Script:**

The proxy diagram also explains a very common surprise inside an object.

**Purpose:**

Move from join-point capabilities to the distinct self-invocation limit in Spring proxy call graphs.

### Scene 1 — Interception Boundaries and Internal Calls in Proxy Models

**Time:** `07:28–08:35`

**Visual:**

Create two paths: external → proxy.verify() → advice ✓; external → proxy.execute() → target.this.verify() → advice ✕. Add JDK interface and class-proxy labels as a note.

**Script:**

Watch the inner call. An external caller enters the proxy for execute, but inside the target the expression this.verify points at the target itself. It does not come back out to the proxy, so advice bound to that inner verify execution is bypassed. A separate external call to proxy.verify may be intercepted if it is eligible. Interface and class proxies also have different constraints; woven bytecode has a different interception model.

**Purpose:**

Demonstrate self-invocation path causally without pretending it is a general AspectJ limitation.
