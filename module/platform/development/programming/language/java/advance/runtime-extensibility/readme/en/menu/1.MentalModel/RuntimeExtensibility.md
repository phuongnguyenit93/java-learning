<a id="back-to-top"></a>

# Runtime Extensibility Mental Model

## Menu
- [What Runtime Extensibility Is and Why It Exists](#runtime-extensibility-purpose)
- [Host, Service Contract, Provider, and Discovery](#host-extension-model)
- [Limits of Static Wiring and Hard-coded Implementations](#static-wiring-limitations)
- [The Java Runtime Extensibility Spectrum](#extensibility-mechanism-spectrum)
- [Boundaries with Class Loading, JPMS, Framework Plugins, and Instrumentation](#runtime-extensibility-boundaries)

## <a id="runtime-extensibility-purpose">What Runtime Extensibility Is and Why It Exists</a>

<details>
<summary>Click for details</summary>

Runtime extensibility is the ability of a Java application to **extend behavior after the host has been designed** by loading implementations through a known contract instead of hard-coding every implementation into the host.

Consider an application that exports reports. The simplest design may be:

```java
PdfExporter exporter = new PdfExporter();
exporter.export(report);
```

That is perfectly reasonable when one implementation is enough. It becomes limiting when the application later needs `CsvExporter`, `JsonExporter`, or providers packaged independently by another team.

Runtime extensibility introduces a boundary:

```text
Host
  ↓ depends on
Service Contract
  ↑ implemented/provided by
Provider

Provider Discovery
→ locates implementations at runtime
```

“Extensible” does not mean that everything becomes dynamic. The host still knows which **service contract** it needs. What becomes dynamic is which provider satisfies that contract and how that provider is discovered.

This module exists because Java provides a standard service-provider model (`ServiceLoader`) and module/runtime mechanisms such as `ModuleLayer` for composing extensions with less direct coupling.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-extension-model">Host, Service Contract, Provider, and Discovery</a>

<details>
<summary>Click for details</summary>

Four roles form the core mental model:

```text
Host
→ code that consumes a capability

Service Contract
→ interface or abstract class that describes the capability

Provider
→ implementation or factory that supplies the capability

Discovery
→ mechanism that locates providers while the application runs
```

For example:

```java
public interface ReportExporter {
    String format();
    String export(String report);
}
```

`ReportExporter` is the service contract. `PdfExporter` and `CsvExporter` can be providers. The host depends on `ReportExporter` instead of constructing every provider directly.

This is the **baseline running example** also used by the module's API experiments. Later chapters intentionally enrich or reduce `ReportExporter` in a few snippets to isolate capability modeling, error-contract, or SPI-evolution ideas; those snippets are **illustrative variants**, not silent changes to one source interface.

```java
ServiceLoader<ReportExporter> exporters =
        ServiceLoader.load(ReportExporter.class);
```

`ServiceLoader` is the discovery mechanism. Discovery does not make the business decision for the host, so keep these concepts separate:

```text
discovery
→ which providers exist?

selection
→ which provider satisfies this request?
```

That distinction drives the rest of the module: SPIs define contracts, `ServiceLoader` discovers providers, plugin architecture manages lifecycle and isolation, and `ModuleLayer` supports modular runtime composition.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-wiring-limitations">Limits of Static Wiring and Hard-coded Implementations</a>

<details>
<summary>Click for details</summary>

Static wiring is not a problem by itself. It is often the simplest and best design when the set of implementations is known at build and deployment time.

The limitation appears when adding an extension requires changing the host:

```java
switch (format) {
    case "pdf" -> new PdfExporter();
    case "csv" -> new CsvExporter();
    case "json" -> new JsonExporter();
    default -> throw new IllegalArgumentException();
}
```

Adding a provider now means editing, rebuilding, and redeploying the host. A hard-coded registry merely moves the coupling:

```java
registry.put("pdf", new PdfExporter());
registry.put("csv", new CsvExporter());
```

Runtime extensibility becomes useful when one or more of these requirements exist:

- providers are developed or packaged independently;
- the host should not know every provider in advance;
- capabilities can be added by deploying another JAR or module;
- the application must select providers by capability or metadata;
- plugins need explicit lifecycle or isolation boundaries.

Without such requirements, dependency injection or static wiring is usually simpler to reason about and operate.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="extensibility-mechanism-spectrum">The Java Runtime Extensibility Spectrum</a>

<details>
<summary>Click for details</summary>

Java supports several levels of extensibility. Not every system needs a `ModuleLayer`.

```text
1. Static wiring
   The host knows concrete implementations.

2. Interface + configurable wiring
   The host depends on a contract; configuration chooses an implementation.

3. ServiceLoader on the class path/module path
   Providers are discovered from the runtime environment.

4. Plugin architecture
   The host adds selection, lifecycle, failure, and compatibility policies.

5. ModuleLayer-based composition
   The host resolves and defines another graph of named modules in the JVM.
```

Each level adds flexibility and operational complexity.

For example, `ServiceLoader` solves **discovery and loading**. It does not automatically provide plugin lifecycle, dependency-conflict resolution, hot unload, version negotiation, or retries. Those are application architecture concerns.

A useful rule throughout this module is:

> Choose the simplest mechanism that still satisfies the required extension boundary and lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-extensibility-boundaries">Boundaries with Class Loading, JPMS, Framework Plugins, and Instrumentation</a>

<details>
<summary>Click for details</summary>

Runtime Extensibility touches several neighboring Java topics without owning them completely.

```text
ClassLoader
→ class identity, delegation, visibility, loader lifecycle

JPMS
→ module descriptors, readability, exports/opens, general resolution

Runtime Extensibility
→ service/provider contracts, discovery, plugin architecture,
   service binding, and ModuleLayer integration

Instrumentation
→ changing or observing bytecode/classes in a running JVM
```

A plugin-isolation design may use custom class loaders, but this module focuses on **why the isolation boundary is needed** and how it affects plugin architecture. Detailed delegation mechanics belong to the ClassLoader curriculum.

Likewise, this module uses `requires`, `uses`, `provides`, `Configuration`, and `ModuleLayer` where they support extensibility; it does not duplicate the complete JPMS curriculum.

One boundary is especially important:

```text
loading another provider
≠
redefining an already-loaded class
```

The first is extensibility. The second belongs to instrumentation and agent mechanisms.

</details>

- [Quay lại đầu trang](#back-to-top)
