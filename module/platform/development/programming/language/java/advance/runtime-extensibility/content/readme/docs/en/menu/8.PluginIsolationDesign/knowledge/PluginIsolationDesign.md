<a id="back-to-top"></a>

# Plugin Isolation Design

## Menu
- [Why Plugins Need Isolation Boundaries](#plugin-isolation-purpose)
- [Class Identity and Shared Contract Boundaries](#class-identity-boundaries)
- [Shared vs Private Plugin Dependencies](#shared-vs-private-dependencies)
- [ClassLoader Isolation Strategies at the Architecture Level](#classloader-isolation-strategies)
- [Plugin Resource, Thread, and Context Boundaries](#isolation-resource-boundaries)
- [Failure Boundaries and Host-Plugin Failure Containment](#plugin-failure-boundaries)
- [Unloadability and Memory or Resource Leak Risks](#unloadability-and-leak-risks)

## <a id="plugin-isolation-purpose">Why Plugins Need Isolation Boundaries</a>

<details>
<summary>Click for details</summary>

Plugin isolation prevents an extension from implicitly sharing the host's entire class, dependency, and resource space with every other extension.

Without isolation, plugins can:

- require conflicting versions of one library;
- see implementation details they should not depend on;
- keep threads or resources that the host cannot clean up;
- spread failures through shared global state.

Isolation does not always mean a custom class loader. Requirements may call for different levels:

```text
logical isolation
→ contract + lifecycle + no shared mutable state

class-loader isolation
→ dependency/type namespace boundary

module-layer isolation
→ named-module graph + loader mapping

process isolation
→ stronger failure/security/resource boundary
```

This module focuses on in-JVM isolation. Process or container isolation belongs to a broader system architecture decision.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-identity-boundaries">Class Identity and Shared Contract Boundaries</a>

<details>
<summary>Click for details</summary>

In the JVM, type identity is not just the fully qualified class name. A useful mental model is:

```text
type identity
≈ class name + defining ClassLoader
```

Two loaders can define two copies of `com.example.api.ReportExporter`, and the JVM may treat them as different types.

That is why the **shared contract** is commonly defined by a shared or parent loader visible to both host and plugins.

```text
shared parent loader
→ ReportExporter contract

plugin loader A
→ PdfExporter

plugin loader B
→ CsvExporter
```

If each plugin loads its own copy of the contract with incompatible loader policy, a cast to the host's `ReportExporter` may fail even when class names are identical.

Detailed delegation mechanics belong to the ClassLoader module. The key architectural rule here is that types crossing the plugin boundary need a compatible shared identity.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shared-vs-private-dependencies">Shared vs Private Plugin Dependencies</a>

<details>
<summary>Click for details</summary>

Dependencies usually fall into two groups:

```text
shared
→ contract/API and libraries that truly require interoperability

private
→ implementation libraries needed only by one plugin
```

Sharing too much destroys isolation. Sharing too little can duplicate type identities for objects that cross the boundary.

For example, avoid making a contract DTO depend on a library that each plugin loads privately if the JVM must exchange that concrete type across loaders.

A practical policy might be:

- JDK types and contract API: shared;
- logging façade/context supplied by the host: shared when required by the contract;
- rendering/parser/native client libraries: private;
- transitive implementation dependencies: private by default.

The smaller the shared boundary, the easier compatibility becomes to reason about.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classloader-isolation-strategies">ClassLoader Isolation Strategies at the Architecture Level</a>

<details>
<summary>Click for details</summary>

Several class-loader strategies are common at the architecture level:

```text
single loader
→ simplest, little isolation

parent-first plugin loader
→ shared/host classes win

child-first plugin loader
→ plugin-private versions win; package exclusions become important

one loader per plugin
→ stronger isolation and clearer lifecycle tracking
```

There is no universally correct choice.

Parent-first helps preserve shared contract identity but may force plugins to use host dependency versions. Child-first enables private versions but can accidentally duplicate contract types unless package rules are explicit.

`ModuleLayer` can map named modules using one loader, many loaders, or a custom mapping. More flexibility also creates more responsibility around readability and loading behavior.

Start from the isolation requirement rather than selecting a loader strategy because it is fashionable in plugin frameworks.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="isolation-resource-boundaries">Plugin Resource, Thread, and Context Boundaries</a>

<details>
<summary>Click for details</summary>

Class isolation is not enough if a plugin leaks resources beyond its boundary.

Common resources that retain plugins include:

- non-daemon threads;
- executors and schedulers;
- `ThreadLocal` values on host threads;
- global registries or listeners;
- shutdown hooks;
- host caches retaining plugin instances/classes;
- open files, sockets, or native resources;
- thread context class loaders pointing to plugin loaders.

For example, a plugin-created executor needs explicit cleanup:

```java
final class PdfPlugin implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void close() {
        executor.shutdown();
    }
}
```

The host must also stop retaining references to provider instances, classes, loaders, and plugin metadata if loader collection is expected.

The isolation boundary is therefore about **classes, resources, threads, and reference ownership**, not merely separate JAR files.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="plugin-failure-boundaries">Failure Boundaries and Host-Plugin Failure Containment</a>

<details>
<summary>Click for details</summary>

A broken plugin should not automatically leave the whole host in an undefined state.

At minimum distinguish:

```text
discovery/configuration failure
→ provider cannot be loaded

activation failure
→ provider loads but cannot start

request failure
→ one plugin operation fails

fatal integrity failure
→ host invariant is compromised and host shutdown may be appropriate
```

Invocation can be wrapped for observability:

```java
try {
    exporter.export(report);
} catch (PluginException ex) {
    metrics.pluginFailure(exporter.getClass().getName());
    throw ex;
}
```

But catching exceptions does not create perfect isolation. A plugin can still deadlock threads, exhaust memory, or corrupt shared mutable state.

When failure containment must be stronger than an in-JVM boundary can provide, process isolation may be the correct architecture.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unloadability-and-leak-risks">Unloadability and Memory or Resource Leak Risks</a>

<details>
<summary>Click for details</summary>

“Unloading a plugin” in the JVM ultimately depends on making its **defining class loader unreachable** together with classes and objects that retain it.

There is no API such as:

```java
Class.unload(); // does not exist
```

A custom/plugin class loader can become collectible when no reachable strong references remain to the loader, its classes, or objects that retain them.

Common leak sources include:

- host static fields retaining plugin instances;
- live threads whose context class loader is the plugin loader;
- global caches retaining plugin `Class<?>` objects;
- listeners that were not unregistered;
- executors that were not stopped;
- native/JNI callbacks retaining references.

Unloadability therefore has to be designed through lifecycle, ownership, and reference cleanup.

`ServiceLoader.reload()` is not evidence that a plugin was unloaded; it only clears that loader's provider cache.

</details>

- [Quay lại đầu trang](#back-to-top)
