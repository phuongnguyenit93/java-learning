---
video:
  url: ""
---

# SSL bundles and runtime TLS configuration

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

## Why Does Boot Provide Named SSL Bundles?

<!-- VIDEO_SECTION -->

### Scene 1 — Why Does Boot Provide Named SSL Bundles?

**Time:** `00:00–00:49`

**Visual:**

Progressive reveal on the chapter visual: replace duplicated key/trust settings across consumers with one reusable named bundle.

**Script:**

At the TLS integration boundary, without a reusable abstraction, every supported client or server integration can end up with a separate set of keystore paths, passwords, certificates, and protocol options. Applications commonly reuse the same trust or key material in more than one secure connection. Spring Boot's SSL bundle model gives that material a name under `spring.ssl.bundle`. A runtime consumer can then refer to the named bundle instead of repeating the source material. The bundle becomes the Boot-managed boundary between configuration and the component that needs SSL objects. This chapter focuses on that reusable runtime abstraction.

**Purpose:**

Show how a named bundle prevents repeated key/trust configuration across supported secure runtime consumers.


## How Are JKS/PKCS12 and PEM Bundles Configured?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:49–01:02`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

A named abstraction is useful only if the material can be configured consistently, so next compare the JKS/PKCS12 and PEM namespaces that create bundle definitions.

**Purpose:**

Move from the reason for naming SSL material to the two supported configuration families that create those names.

### Scene 2 — How Are JKS/PKCS12 and PEM Bundles Configured?

**Time:** `01:02–01:43`

**Visual:**

Progressive reveal on the chapter visual: split configuration into `spring.ssl.bundle.jks.<name>` and `.pem.<name>` trees with secret-free example material.

**Script:**

Boot 3.3 can create named bundles from Java keystores or PEM material. Put JKS/PKCS12 definitions under `spring.ssl.bundle.jks.<name>` and PEM definitions under `spring.ssl.bundle.pem.<name>`. A PEM trust bundle can point `truststore.certificate` at a classpath CA certificate; key material can also provide a certificate and private key. JKS/PKCS12 uses the corresponding key/trust-store locations and passwords. The stable contract is the bundle name; real passwords and private material still belong in normal externalized secret/configuration practices.

**Purpose:**

Distinguish JKS/PKCS12 and PEM configuration namespaces while keeping real credentials in externalized secret/configuration practices.


## What Does the Auto-Configured `SslBundles` Catalog Provide?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:43–01:55`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

Configured names become useful at runtime through the auto-configured `SslBundles` catalog, which lets consumers ask for a bundle without knowing its source format.

**Purpose:**

Turn static bundle definitions into a runtime lookup model through the `SslBundles` catalog.

### Scene 3 — What Does the Auto-Configured `SslBundles` Catalog Provide?

**Time:** `01:55–02:33`

**Visual:**

Progressive reveal on the chapter visual: show `SslBundles` catalog lookup `getBundle("partner-api")` followed by `createSslContext()`.

**Script:**

After bundle names are configured, Boot exposes them through the auto-configured `SslBundles` catalog. A consumer can call `getBundle("partner-api")` and then `createSslContext()` when it needs a high-level TLS object. The consumer therefore depends on the bundle abstraction rather than PEM/JKS file layout. The catalog is not a certificate authority or secret store; it is a runtime view of SSL configuration already present in the application environment.

**Purpose:**

Use `SslBundles` as the runtime catalog boundary and show bundle lookup plus `SSLContext` creation without exposing source-format details to consumers.


## What Runtime Material Can an `SslBundle` Expose?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:33–02:45`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

After retrieving a bundle, the consumer can choose the abstraction level it needs—stores, managers, or an `SSLContext`—without rebuilding everything from paths.

**Purpose:**

Expand one retrieved bundle into the concrete object layers a consumer can request.

### Scene 4 — What Runtime Material Can an `SslBundle` Expose?

**Time:** `02:45–03:35`

**Visual:**

Progressive reveal on the chapter visual: expand one `SslBundle` as stores → managers → `SSLContext`, highlighting the highest layer the consumer needs.

**Script:**

Inside one `SslBundle`, `getStores()` provides key/trust store access, `getManagers()` provides key/trust manager factories and managers, and `createSslContext()` creates an `SSLContext`. An `SslBundle` exposes SSL material in layers so a consumer can choose the level it needs. The bundle also carries protocol/options and key details used by supported integrations. The layered API avoids forcing every consumer to reconstruct lower-level objects from file paths. A library that accepts an `SSLContext` can use the high-level method; an integration that needs manager factories can use the manager layer. Do not descend to a lower layer without a consumer requirement.

**Purpose:**

Explain the layered stores → managers → `SSLContext` API so consumers use only the lowest level they actually require.


## How Do Supported Runtime Consumers Reuse a Named Bundle?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:35–03:47`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

The bundle only helps integrations that explicitly support it or can accept the SSL object it produces, so consumer support is the next boundary.

**Purpose:**

Move from what a bundle can expose to the explicit support contract required by each runtime consumer.

### Scene 5 — How Do Supported Runtime Consumers Reuse a Named Bundle?

**Time:** `03:47–04:37`

**Visual:**

Progressive reveal on the chapter visual: connect a bundle only to integrations that explicitly support it or accept an SSL object; leave arbitrary clients disconnected.

**Script:**

For a supported consumer, this lets one configuration definition serve multiple runtime consumers without duplicating trust/key properties. A named bundle becomes useful when a supported Boot integration can refer to it by name, or when application code retrieves it from `SslBundles`. Consumer support is still explicit. A component must understand Boot's bundle integration or accept an SSL object that can be created from the bundle. The existence of a bundle does not automatically rewrite every third-party client on the classpath. When integrating a library, first check whether Spring Boot's auto-configuration exposes a bundle property for that technology.

**Purpose:**

Make consumer support explicit: a named bundle does not automatically reconfigure arbitrary third-party clients.


## Where Does Generic SSL Bundle Ownership Hand Off to Web-Server TLS?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:37–04:47`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

When the consumer is an embedded web server, generic bundle ownership ends and server TLS configuration begins in `web-runtime`.

**Purpose:**

Draw the exact handoff from reusable bundle material to embedded-server TLS configuration.

### Scene 6 — Where Does Generic SSL Bundle Ownership Hand Off to Web-Server TLS?

**Time:** `04:47–05:25`

**Visual:**

Progressive reveal on the chapter visual: draw `spring.ssl.bundle.* → SslBundles [application-runtime] → server.ssl.bundle=<name> [web-runtime]`.

**Script:**

Keep the handoff explicit. `spring.ssl.bundle.*` creates the named `SslBundle`/`SslBundles` catalog in application-runtime. Applying a bundle to the embedded server—for example `server.ssl.bundle=<name>`—plus HTTPS ports, connectors, and server-specific TLS behavior belongs to `web-runtime`. The same generic bundle may serve supported non-server consumers, which is why server options are not universal bundle semantics.

**Purpose:**

Separate reusable generic SSL material from embedded server ports/connectors/TLS behavior owned by `web-runtime`.


## What Remains TLS and PKI Infrastructure Rather Than Boot Runtime?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:25–05:37`

**Visual:**

Keep the named-bundle catalog visible and expand the next layer: source format, catalog lookup, stores/managers/`SSLContext`, consumer, or ownership handoff.

**Script:**

Even after Boot can load the key and trust material, certificate policy, trust design, renewal, hostname verification, and threat decisions remain TLS/PKI responsibilities.

**Purpose:**

Finish by separating Boot’s material-loading integration from security policy and PKI ownership.

### Scene 7 — What Remains TLS and PKI Infrastructure Rather Than Boot Runtime?

**Time:** `05:37–06:28`

**Visual:**

Progressive reveal on the chapter visual: split troubleshooting into Boot binding/resource errors versus TLS chain/hostname/cipher/PKI policy errors.

**Script:**

At the PKI boundary, certificate issuance and renewal, trust-chain design, hostname verification policy, protocol/cipher choices, key rotation, hardware security modules, and threat modeling remain security/network responsibilities. Boot can load and expose configured key/trust material, but it does not decide the organization's PKI. Likewise, knowing that a bundle contains a truststore is different from knowing which certificate authorities should be trusted. Boot provides configuration structure; the security domain provides the policy. When troubleshooting SSL, first separate a Boot binding/integration error from a TLS handshake or trust-policy error. A missing bundle name or unreadable resource is a Boot/configuration concern.

**Purpose:**

Keep Boot to loading/integration and hand certificate policy, trust design, rotation, hostname verification, and threat modeling to TLS/PKI owners.
