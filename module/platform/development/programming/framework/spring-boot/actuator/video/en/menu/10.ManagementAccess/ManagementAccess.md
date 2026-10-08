---
video:
  url: ""
---

# Management Surface Access and Security Boundaries

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

## Why Should the Remotely Exposed Endpoint Set Stay Minimal?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Should the Remotely Exposed Endpoint Set Stay Minimal?

**Time:** `00:00–00:34`

**Visual:**

Start with an exposure allow-list containing only `health`. Add `metrics`, `loggers`, `env`, and `heapdump` one by one; for each, reveal its consumer and risk before deciding whether it stays.

**Script:**

The safest remote management capability is one you do not expose without a concrete operational reason. Boot 3.3 starts from a minimal posture with only health exposed over web and JMX by default. Every additional endpoint should have a known consumer or incident workflow. Revisit wildcard exposure after upgrades because a new dependency or endpoint can silently make additional diagnostics reachable.

**Purpose:**

Turn least exposure into a reviewable operational policy rather than a one-time configuration choice.

## How Can the Management Web Base Path Be Separated from the Application Surface?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:34–00:48`

**Visual:**

Keep the network path from operator to endpoint visible and move one gate for `How Can the Management Web Base Path Be Separated from the Application Surface?`—URL routing, listener/address, exposure, authorization, or Spring Security ownership—so access remains a layered model.

**Script:**

Minimal exposure decides which capabilities are reachable. The next decision is where their HTTP routes live—but changing a path must not be mistaken for access control.

**Purpose:**

Move from the minimal endpoint set to URL placement while making clear that path organization is not a security control.

### Scene 1 — How Can the Management Web Base Path Be Separated from the Application Surface?

**Time:** `00:48–01:18`

**Visual:**

Route `/actuator/health` through `management.endpoints.web.base-path`. Change it to `/manage/health`, then show an individual path mapping. Add a note: “path change ≠ security boundary.”

**Script:**

`management.endpoints.web.base-path` moves the common HTTP prefix, so `/actuator` can become `/manage`, and individual endpoint paths can also be remapped. That is useful for routing and conventions, but it does not protect the endpoint. The endpoint ID remains `health`, and callers who can reach the new path still need the same network and authorization controls.

**Purpose:**

Separate routing organization from security and preserve endpoint identity when URLs move.

## How Do a Separate Management Port and Address Change Network Placement?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:18–01:31`

**Visual:**

Keep the network path from operator to endpoint visible and move one gate for `How Do a Separate Management Port and Address Change Network Placement?`—URL routing, listener/address, exposure, authorization, or Spring Security ownership—so access remains a layered model.

**Script:**

Moving the path only reorganizes one listener. A separate port changes network placement itself, and only then can the management server bind to a different address.

**Purpose:**

Escalate from path routing to a separate listener/address because that change actually alters network placement.

### Scene 1 — How Do a Separate Management Port and Address Change Network Placement?

**Time:** `01:31–02:08`

**Visual:**

Draw two listeners: application `:8080` and management `:9090`. Show `management.server.port=9090`, `management.server.address=127.0.0.1`, `management.server.base-path=/ops`, then resolve `management.endpoints.web.base-path=/actuator` beneath it. Mark that a different management address is supported only with a different management port.

**Script:**

`management.server.port` can create a separate management web server so infrastructure can route or firewall operational traffic independently. When that port differs from the main server, `management.server.address` can bind it to a specific interface, such as loopback or an internal operations network. `management.server.base-path` defines the server base and the Actuator web base path is resolved relative to it. A different management address is only supported when the management port is different.

**Purpose:**

Restore both port and address semantics and show exactly how server base path and endpoint base path compose.

## Why Are Endpoint Exposure and Authorization Different Decisions?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:08–02:21`

**Visual:**

Keep the network path from operator to endpoint visible and move one gate for `Why Are Endpoint Exposure and Authorization Different Decisions?`—URL routing, listener/address, exposure, authorization, or Spring Security ownership—so access remains a layered model.

**Script:**

Port and address control reachability, but reachability is still not permission. The next model separates publishing an endpoint from authorizing a caller to use it.

**Purpose:**

Use network placement to separate reachability from exposure and caller authorization as independent gates.

### Scene 1 — Why Are Endpoint Exposure and Authorization Different Decisions?

**Time:** `02:21–03:02`

**Visual:**

Show three independent gates in series: endpoint exposure → network reachability → Spring Security authorization. Demonstrate: enabled but not exposed; exposed but firewalled; reachable but 403; exposed and authorized.

**Script:**

Exposure and authorization answer different questions. Exposure decides whether a management transport publishes the endpoint at all. Network placement decides which clients can reach that transport. Authorization decides whether a caller that reaches it may invoke the operation. You can therefore have an enabled endpoint with no route, an exposed route blocked by the network, or a reachable route denied by Spring Security. Hiding a path is not authorization, and authentication is not a reason to publish every endpoint.

**Purpose:**

Restore the full exposure-versus-authorization distinction and place network policy as a third independent defense layer.

## What Security Behavior Does Boot Provide When Spring Security Is Present?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:02–03:14`

**Visual:**

Keep the network path from operator to endpoint visible and move one gate for `What Security Behavior Does Boot Provide When Spring Security Is Present?`—URL routing, listener/address, exposure, authorization, or Spring Security ownership—so access remains a layered model.

**Script:**

After separating exposure from authorization, Boot’s default integration explains who supplies the authorization rules—and exactly when application configuration takes control.

**Purpose:**

Apply those gates to Boot’s Spring Security auto-configuration and its backoff when the application supplies a filter chain.

### Scene 1 — What Security Behavior Does Boot Provide When Spring Security Is Present?

**Time:** `03:14–03:54`

**Visual:**

Show Boot’s default security path: Spring Security present + no custom `SecurityFilterChain` → Boot management security. Highlight health as the endpoint commonly permitted for basic checks. Then add a custom `SecurityFilterChain` and show Boot backing off; reveal `EndpointRequest` as an Actuator-aware matcher.

**Script:**

When Spring Security is on the classpath and the application has not supplied its own `SecurityFilterChain`, Boot provides default security behavior for the management surface, with health remaining suitable for basic operational checks while other Actuator endpoints are secured. Once the application defines its own filter chain, Boot backs off and the team owns the policy. `EndpointRequest` can help target Actuator endpoints, but matcher ordering, identities, roles, CSRF, sessions, and OAuth mechanics belong to Spring Security.

**Purpose:**

Show the auto-configuration backoff point so custom security does not accidentally discard the expected management policy.

## Where Does Actuator Security Integration Hand Off to Spring Security?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:54–04:07`

**Visual:**

Keep the network path from operator to endpoint visible and move one gate for `Where Does Actuator Security Integration Hand Off to Spring Security?`—URL routing, listener/address, exposure, authorization, or Spring Security ownership—so access remains a layered model.

**Script:**

Boot’s default security is only an integration layer. The final boundary is ownership: Actuator identifies the management surface; Spring Security decides who may cross it.

**Purpose:**

Close the access model by handing authentication and authorization mechanics to Spring Security once Actuator routing is confirmed.

### Scene 1 — Where Does Actuator Security Integration Hand Off to Spring Security?

**Time:** `04:07–04:41`

**Visual:**

Draw a handoff at the filter chain: Actuator supplies endpoint identity/exposure plus request matchers; Spring Security supplies authentication and authorization. Route a 401/403 investigation to the active `SecurityFilterChain` rather than changing exposure.

**Script:**

Actuator owns management capabilities, transports, and integration points that let security recognize management requests. Spring Security owns how a caller is authenticated and how authorization is enforced. If the endpoint is enabled, exposed, and reachable but the response is 401 or 403, the investigation has crossed that boundary. Follow the active security filter chain instead of widening Actuator exposure until the request succeeds.

**Purpose:**

Give access failures a precise troubleshooting handoff and prevent security policy from being “fixed” by making endpoints more public.
