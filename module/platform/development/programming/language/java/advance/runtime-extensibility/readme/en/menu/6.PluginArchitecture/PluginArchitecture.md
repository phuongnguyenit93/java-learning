<a id="back-to-top"></a>

# Plugin Architecture

## Menu
- [From SPI to Plugin Architecture](#plugin-architecture-purpose)
- [Designing Extension Points](#extension-point-design)
- [Host and Plugin Responsibility Boundaries](#host-plugin-boundary)
- [Plugin Manager Responsibilities](#plugin-manager-responsibilities)
- [Capability Models and Plugin Selection Contracts](#plugin-capability-model)
- [Dependency Policy across Host, Contract, and Plugins](#plugin-dependency-policy)

## <a id="plugin-architecture-purpose">From SPI to Plugin Architecture</a>

<details>
<summary>Click for details</summary>

An SPI plus `ServiceLoader` solves **contract + discovery**. A complete plugin architecture must also answer questions such as:

```text
which providers may activate?
→ who owns lifecycle?
→ how are plugin failures contained?
→ which dependencies are shared?
→ where is compatibility validated?
```

Once these questions matter, a provider is more than “an implementation that was found.” It becomes a **plugin** with a host-managed boundary and lifecycle.

A typical flow is:

```text
discover
→ validate
→ select
→ initialize
→ activate
→ execute
→ deactivate
→ clean up
```

Java does not provide a universal plugin framework through `ServiceLoader`. The application builds the remaining orchestration on top of Java's discovery and module primitives.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="extension-point-design">Designing Extension Points</a>

<details>
<summary>Click for details</summary>

An extension point is an intentional location where the host allows externally supplied behavior to participate.

`ReportExporter` is an extension point if the host allows new export formats to be added without changing host code.

A useful extension point defines:

- the capability being extended;
- when the host invokes the extension;
- input/output contracts;
- error semantics;
- concurrency and lifecycle expectations;
- resource ownership;
- compatibility expectations.

Not every interface should become an extension point. An internal interface used for code organization is different from a public plugin boundary.

If the contract is too broad, providers learn too much about host internals. If it is too narrow, the host starts using casts, reflection, or side channels to reach the real capability.

Extension-point design balances **boundary stability** with enough expressiveness for real providers.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-plugin-boundary">Host and Plugin Responsibility Boundaries</a>

<details>
<summary>Click for details</summary>

Host and plugin responsibilities should remain distinct.

The host commonly owns:

- discovery context;
- selection policy;
- lifecycle orchestration;
- logging and metrics boundaries;
- timeout or cancellation policy;
- failure containment;
- the shared service contract.

The plugin commonly owns:

- implementation details;
- private dependencies;
- capability-specific resources;
- cleanup for resources it creates;
- extension-domain behavior.

Avoid host code that reaches through the contract:

```java
PdfExporter pdf = (PdfExporter) plugin;
pdf.getInternalEngine().reset();
```

That destroys the plugin boundary.

Likewise, a plugin should not silently control global host lifecycle or retain global mutable state unless the contract explicitly permits it.

A clear responsibility boundary lets providers change without leaking their implementation changes into the host.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-manager-responsibilities">Plugin Manager Responsibilities</a>

<details>
<summary>Click for details</summary>

When the model grows beyond simple discovery, a `PluginManager` or equivalent component often centralizes orchestration.

Typical responsibilities include:

```text
discover providers
→ validate metadata/compatibility
→ build a registry
→ activate selected plugins
→ expose a stable/read-only view to business code
→ stop and clean up on shutdown or replacement
```

For example:

```java
final class PluginManager {
    private final Map<String, ReportExporter> exporters;

    void start() { ... }
    ReportExporter exporterFor(String format) { ... }
    void stop() { ... }
}
```

The manager does not need to be a large framework. Its value is giving lifecycle and selection policy one clear home instead of letting business code call `ServiceLoader` everywhere.

Avoid turning it into a god object that knows provider internals. It should orchestrate through stable contracts and metadata.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-capability-model">Capability Models and Plugin Selection Contracts</a>

<details>
<summary>Click for details</summary>

A capability model answers “what can this plugin do?” in a form the host can use without knowing implementation details.

The `ReportExporter` variant below intentionally uses a **capability-oriented contract** to demonstrate selection across multiple formats and options; it does not replace the baseline interface used by the API experiments:

```java
public interface ReportExporter {
    Set<String> formats();
    boolean supports(ExportOptions options);
    void export(Report report, ExportOptions options);
}
```

The host may build a registry such as:

```text
pdf  → PdfExporter
csv  → CsvExporter
json → JsonExporter
```

If multiple providers advertise the same capability, the contract needs an application policy for priority, version, environment, quality tier, or explicit configuration.

Capability metadata should describe what the host needs to decide, not every provider detail. Do not expose arbitrary dependency versions when a simple supported-feature flag would answer the real selection question.

The more explicit the selection contract, the less the system depends on incidental discovery ordering.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-dependency-policy">Dependency Policy across Host, Contract, and Plugins</a>

<details>
<summary>Click for details</summary>

A plugin system needs a deliberate dependency policy.

A common safe shape is:

```text
host
  ↓
contract/api module
  ↑
plugin A     plugin B
```

Keep the contract module small and stable, containing only types that truly need to be shared.

Plugin-specific libraries should stay private when the host does not need them. Sharing too many libraries increases version conflicts and class-identity coupling.

Avoid cycles such as:

```text
host → plugin
plugin → host implementation
```

If a plugin needs a host capability, expose it through a stable host-facing contract or context rather than importing implementation packages.

For example:

```java
interface PluginContext {
    Logger logger();
    Path dataDirectory();
}
```

This dependency policy becomes the foundation for isolation and compatibility later in the module.

</details>

- [Quay lại đầu trang](#back-to-top)
