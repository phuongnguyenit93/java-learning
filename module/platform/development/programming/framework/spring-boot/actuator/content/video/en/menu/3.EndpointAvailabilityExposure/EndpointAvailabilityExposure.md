---
video:
  url: ""
---

# Endpoint Enablement, Availability, and Exposure

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

## What Does Enabling or Disabling an Endpoint Change?

<!-- VIDEO_SECTION -->

### Scene 1 — What Does Enabling or Disabling an Endpoint Change?

**Time:** `00:00–00:32`

**Visual:**

Use a two-state endpoint card. In “enabled” state the endpoint bean/capability exists; in “disabled” state remove it from the application context. Show `management.endpoint.<id>.enabled`, `management.endpoints.enabled-by-default`, and mark `shutdown` as disabled by default.

**Script:**

Enablement answers whether the management capability exists in the application at all. Most built-in endpoints are enabled by default, while higher-impact functionality such as shutdown is disabled by default. Disabling an endpoint is stronger than hiding a URL: Boot removes that endpoint from the application context, so code and auto-configuration cannot rely on its bean being there.

**Purpose:**

Make enablement a capability-existence decision, not a synonym for HTTP visibility.

## How Is Exposure Different from Enablement?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:32–00:44`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `How Is Exposure Different from Enablement?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

An enabled capability still has no remote route. The next decision is which management transport, if any, is allowed to publish it.

**Purpose:**

Show that an existing capability still needs a separate transport-exposure decision before any remote client can reach it.

### Scene 1 — How Is Exposure Different from Enablement?

**Time:** `00:44–01:16`

**Visual:**

Keep the enabled endpoint card, then add separate Web and JMX gates controlled by include/exclude lists. Show only `health` passing the default gates; put an exclude rule in front of an include rule to show precedence.

**Script:**

Exposure answers a different question: through which management technology can an enabled endpoint be reached? Web and JMX have their own include and exclude lists, and exclude wins. In Boot 3.3 only `health` is exposed by default over those remote management technologies. You can therefore have an enabled endpoint that is intentionally unreachable from HTTP or JMX.

**Purpose:**

Visually separate capability existence from remote reachability and capture the default least-exposure posture.

## When Is an Endpoint Actually Available Through a Management Technology?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:16–01:26`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `When Is an Endpoint Actually Available Through a Management Technology?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

Enablement and exposure are separate gates; combining them explains when a management capability becomes genuinely available through a transport.

**Purpose:**

Combine enablement and exposure into the availability conditions that explain why an endpoint may still be absent.

### Scene 1 — When Is an Endpoint Actually Available Through a Management Technology?

**Time:** `01:26–02:00`

**Visual:**

Build an AND-gate diagram: endpoint enabled + selected for exposure + transport infrastructure available → endpoint available on that technology. Remove one input at a time and show the route/MBean disappear.

**Script:**

Availability through a management technology is the result of several conditions, not one property. The endpoint must be enabled, selected for that exposure technology, and supported by the relevant infrastructure. Boot’s endpoint auto-configuration uses this availability model, so a disabled or non-exposed endpoint may not create the management component you expected. When an endpoint is “missing,” inspect those gates before debugging routing.

**Purpose:**

Turn endpoint availability into a concrete diagnostic sequence instead of treating all missing endpoints as path problems.

## How Are Endpoints Exposed over HTTP?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:11`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `How Are Endpoints Exposed over HTTP?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

The availability model is abstract. HTTP is the first concrete adapter: it turns the same endpoint operations into web routes.

**Purpose:**

Apply the abstract availability gates to HTTP so the learner can see where web routing actually enters the model.

### Scene 1 — How Are Endpoints Exposed over HTTP?

**Time:** `02:11–02:43`

**Visual:**

Show an enabled endpoint passing through WebEndpoint infrastructure into three possible stacks: MVC, WebFlux, Jersey. Add the rule “MVC wins when MVC + Jersey are both present,” then show `management.endpoints.web.exposure.include` selecting the endpoint.

**Script:**

For web exposure, Actuator adapts endpoint operations to the active web stack. Spring MVC, WebFlux, or Jersey can provide the management HTTP surface; when Jersey and MVC are both available, MVC is used. The endpoint still comes from the same core model—the web stack is the adapter that turns selectors and operations into routes and HTTP methods.

**Purpose:**

Show where HTTP routing is introduced without collapsing the endpoint abstraction into a controller.

## How Are Endpoints Exposed over JMX?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:43–02:54`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `How Are Endpoints Exposed over JMX?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

Once HTTP has shown one adapter, JMX makes the transport-neutral design visible: the endpoint identity survives while the representation changes.

**Purpose:**

Contrast HTTP with JMX to prove that transport representation can change while endpoint identity stays fixed.

### Scene 1 — How Are Endpoints Exposed over JMX?

**Time:** `02:54–03:24`

**Visual:**

Show `spring.jmx.enabled=false` switching to `true`, then render the same endpoint ID under the `org.springframework.boot` JMX domain as an MBean operation view. Keep the web route beside it for comparison.

**Script:**

JMX is another representation of the same management capability. Spring JMX support is disabled by default and can be enabled with `spring.jmx.enabled=true`; exposed Actuator endpoints are then represented through the JMX infrastructure rather than HTTP routing. The important comparison is that transport changes, while the endpoint ID and management intent remain the same.

**Purpose:**

Contrast JMX with HTTP so the learner sees two transports over one endpoint model rather than two unrelated feature sets.

## How Does the Default /actuator Web Base Path Fit the Endpoint Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:24–03:38`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `How Does the Default /actuator Web Base Path Fit the Endpoint Model?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

HTTP and JMX share endpoint identity, but only HTTP needs URL composition. That makes the `/actuator` base path the next piece to place correctly in the model.

**Purpose:**

Place URL composition after transport selection so `/actuator` is understood as routing, not endpoint identity.

### Scene 1 — How Does the Default /actuator Web Base Path Fit the Endpoint Model?

**Time:** `03:38–04:07`

**Visual:**

Draw endpoint ID `health` flowing through `management.endpoints.web.base-path=/actuator` to `/actuator/health`. Then change the base path to `/manage` while leaving the ID untouched; add `management.server.base-path` only when a separate management server is shown.

**Script:**

The default `/actuator` prefix belongs to web routing, not to endpoint identity. With the default base path, endpoint ID `health` normally appears at `/actuator/health`; changing `management.endpoints.web.base-path` moves that route without renaming the endpoint. On a separate management server, that endpoint base path is resolved relative to the management-server base path.

**Purpose:**

Prevent URL conventions from being confused with endpoint identity and prepare for management-server placement later.

## Why Should Remote Endpoint Exposure Be Deliberate?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:07–04:19`

**Visual:**

Keep the endpoint capability card fixed and animate the gate relevant to `Why Should Remote Endpoint Exposure Be Deliberate?`—enablement, transport exposure, availability, HTTP/JMX adapter, base-path routing, or risk controls—so each transition adds one condition to the same model.

**Script:**

Now that routing and transport are clear, the final question is not “can we expose more?” but “which capabilities actually justify remote reachability?”

**Purpose:**

Turn the mechanics of exposure into a risk decision about which management capabilities deserve remote reachability.

### Scene 1 — Why Should Remote Endpoint Exposure Be Deliberate?

**Time:** `04:19–04:55`

**Visual:**

Create a risk matrix: `health` low disclosure, `metrics` moderate, `env`/`configprops` sensitive, `loggers` state-changing, `heapdump` highly sensitive/expensive. Put exposure, network placement, and authorization controls around the matrix.

**Script:**

Exposure should be deliberate because management endpoints can reveal internals or change runtime behavior. Configuration views disclose structure, metrics expose runtime signals, logger writes change behavior, and heap dumps can contain production data. A wildcard include may also expose newly added endpoints after an upgrade. Use the smallest remote endpoint set that supports a real operational workflow, then protect that set with network and authorization controls.

**Purpose:**

Close the chapter with the operational reason behind the default minimal exposure and connect exposure decisions to security risk.
