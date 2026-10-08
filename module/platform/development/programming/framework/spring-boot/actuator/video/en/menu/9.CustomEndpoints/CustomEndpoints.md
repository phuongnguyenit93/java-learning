---
video:
  url: ""
---

# Authoring Custom Actuator Endpoints

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

## When Does an Application Need a Custom Management Endpoint?

<!-- VIDEO_SECTION -->

### Scene 1 — When Does an Application Need a Custom Management Endpoint?

**Time:** `00:00–00:34`

**Visual:**

Show a decision fork: built-in endpoint/health/metric/info contribution on one side, custom `@Endpoint` on the other. Put bounded maintenance state and operator-only metadata on the custom side; put customer CRUD on the business API side.

**Script:**

A custom Actuator endpoint is for an operational management need that the built-in catalog does not already model. Bounded internal state, a carefully controlled maintenance action, or deployment metadata can fit. Normal customer workflows do not become management operations just because Actuator can expose JSON. Before creating a new endpoint, check whether health, metrics, info, or an existing endpoint already expresses the need.

**Purpose:**

Give custom endpoints a narrow operational purpose and prevent unnecessary or business-facing capabilities from entering the management surface.

## How Do @ReadOperation, @WriteOperation, and @DeleteOperation Define Operations?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:46`

**Visual:**

Keep the `learning` endpoint implementation on screen and change only the surrounding contract for `How Do @ReadOperation, @WriteOperation, and @DeleteOperation Define Operations?`—operation inputs, real Actuator mapping, transport specialization, extension, or business/API boundary.

**Script:**

Once a custom capability is justified, its public contract is defined by endpoint ID, operation type, and explicit method inputs—not by controller conventions.

**Purpose:**

Move from deciding that a custom management capability is justified to defining its transport-neutral operation contract.

### Scene 1 — How Do @ReadOperation, @WriteOperation, and @DeleteOperation Define Operations?

**Time:** `00:46–01:21`

**Visual:**

Open `LearningOperationsEndpoint`: highlight `@Endpoint(id="learning")`, `@ReadOperation`, and `@WriteOperation`. Add callouts showing root JSON property `enabled` mapping to the write parameter, optional `@Nullable`, selector path segments, and the `-parameters` requirement for retaining names.

**Script:**

`@Endpoint` declares the management identity; `@ReadOperation`, `@WriteOperation`, and `@DeleteOperation` declare intent. Operation parameters become management inputs: web writes map root JSON properties to individual parameters, `@Selector` can move an input into the path, and optional inputs use `@Nullable`. Parameter names must be retained with `-parameters` so Actuator can bind them. Conversion then runs through Boot’s application conversion infrastructure before your method is invoked.

**Purpose:**

Narrate every operation-input callout and show how a Java method signature becomes a transport-neutral management contract.

## Why Prefer a Technology-Agnostic @Endpoint When Possible?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:21–01:34`

**Visual:**

Keep the `learning` endpoint implementation on screen and change only the surrounding contract for `Why Prefer a Technology-Agnostic @Endpoint When Possible?`—operation inputs, real Actuator mapping, transport specialization, extension, or business/API boundary.

**Script:**

The operation annotations define intent; now the live endpoint shows the payoff—one management implementation remains independent of the web controller used only for teaching.

**Purpose:**

Use the operation contract in the real `learning` endpoint so transport independence is demonstrated rather than asserted.

### Scene 1 — Why Prefer a Technology-Agnostic @Endpoint When Possible?

**Time:** `01:34–02:09`

**Visual:**

Run `GET /api/actuator-learning/custom-endpoint` to show the shared learning state, then call the real `GET /actuator/learning`. Trigger the bounded write with the learning bridge and show the same state change through the management read. Keep `LearningOperationsEndpoint` in the center, with MVC bridge labeled “demo only.” Add a side card: generic `@Endpoint` → `Resource` → `application/octet-stream` + MVC/WebFlux range support.

**Script:**

Prefer a generic `@Endpoint` when the capability itself does not depend on HTTP or JMX. `LearningOperationsEndpoint` proves that separation: the Swagger bridge and real `/actuator/learning` mapping use the same management implementation. Binary output alone is not a reason to switch to `@WebEndpoint`: a generic endpoint may return a `Resource`, which Actuator serves as `application/octet-stream`, with range-request support in MVC and WebFlux. Use a transport-specific endpoint only when the contract itself truly depends on that transport.

**Purpose:**

Use Step 5 evidence to prove technology-agnostic endpoint behavior and clearly distinguish the demo bridge from the actual management surface.

## When Are Web- or JMX-specific Endpoints Appropriate?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:09–02:21`

**Visual:**

Keep the `learning` endpoint implementation on screen and change only the surrounding contract for `When Are Web- or JMX-specific Endpoints Appropriate?`—operation inputs, real Actuator mapping, transport specialization, extension, or business/API boundary.

**Script:**

The generic endpoint works when transport is incidental. The next branch covers the opposite case: when transport semantics are genuinely part of the requirement.

**Purpose:**

Contrast the generic endpoint with cases where HTTP or JMX semantics are genuinely part of the requirement.

### Scene 1 — When Are Web- or JMX-specific Endpoints Appropriate?

**Time:** `02:21–02:55`

**Visual:**

Place `@WebEndpoint` and `@JmxEndpoint` on separate branches. Under Web, show transport-specific status/content-type needs; under JMX, show a representation that only makes sense as an MBean. Keep a generic `@Endpoint` path as the default choice.

**Script:**

Technology-specific endpoints are appropriate when the capability truly belongs to one transport. `@WebEndpoint` can model a management feature whose contract is meaningful only over HTTP; `@JmxEndpoint` can depend on JMX representation. That specialization trades portability for a narrower contract. If you need full MVC or WebFlux request/response behavior, a normal controller may be more honest than hiding framework-specific assumptions inside a generic endpoint.

**Purpose:**

Show the decision rule for transport-specific endpoint types and avoid using them merely because HTTP APIs feel familiar.

## When Should an Existing Endpoint Be Extended Instead?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:55–03:07`

**Visual:**

Keep the `learning` endpoint implementation on screen and change only the surrounding contract for `When Should an Existing Endpoint Be Extended Instead?`—operation inputs, real Actuator mapping, transport specialization, extension, or business/API boundary.

**Script:**

Transport-specific behavior does not always justify a brand-new management identity. If the underlying concept already exists, an extension can preserve that identity.

**Purpose:**

Show that transport-specific behavior can extend an existing management identity instead of forcing a duplicate endpoint.

### Scene 1 — When Should an Existing Endpoint Be Extended Instead?

**Time:** `03:07–03:41`

**Visual:**

Show a built-in endpoint card gaining a small Web extension via `@EndpointWebExtension`, and a JMX extension via `@EndpointJmxExtension`. Keep the original endpoint identity unchanged and mark a completely new responsibility as “new endpoint instead.”

**Script:**

When the operational concept already belongs to a built-in endpoint but one transport needs an additional operation or representation, extend that endpoint rather than inventing a duplicate ID. Web and JMX extensions preserve the underlying endpoint meaning while adding transport-specific behavior. Keep extensions small and test them against the Boot version you target, because coupling deeply to built-in implementation details increases upgrade cost.

**Purpose:**

Distinguish endpoint extension from new endpoint creation and make upgrade/coupling cost visible.

## How Do Custom Actuator Endpoints Stay Separate from Business APIs?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:41–03:53`

**Visual:**

Keep the `learning` endpoint implementation on screen and change only the surrounding contract for `How Do Custom Actuator Endpoints Stay Separate from Business APIs?`—operation inputs, real Actuator mapping, transport specialization, extension, or business/API boundary.

**Script:**

After extensions, the last design check is ownership: sharing HTTP does not make a management contract and a product contract the same thing.

**Purpose:**

Finish endpoint design by separating operational contracts from business/product API contracts even when both use HTTP.

### Scene 1 — How Do Custom Actuator Endpoints Stay Separate from Business APIs?

**Time:** `03:53–04:30`

**Visual:**

Split the screen between `/api/orders` and `/actuator/learning`. Label callers, contract purpose, versioning/security expectations, and failure impact. Put maintenance state on management; put customer order creation on business.

**Script:**

Business APIs and management endpoints may both use HTTP, but they serve different contracts. A business API models product/domain behavior for normal clients. A custom Actuator endpoint models operation, diagnosis, or maintenance for an operational audience. Putting customer CRUD behind `@Endpoint` confuses discovery, authorization, and versioning; hiding a privileged maintenance action among public domain controllers obscures its risk. Ask who calls it, why, and what accidental exposure would mean.

**Purpose:**

Restore both sides of the business-versus-management comparison so endpoint placement follows contract ownership, not transport similarity.
