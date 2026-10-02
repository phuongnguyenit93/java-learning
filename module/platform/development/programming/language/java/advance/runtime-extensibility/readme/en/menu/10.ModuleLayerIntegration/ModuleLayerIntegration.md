<a id="back-to-top"></a>

# ModuleLayer for Runtime Plugins

## Menu
- [When a Plugin Architecture Needs ModuleLayer](#modulelayer-purpose)
- [ModuleFinder, Configuration, and Resolution](#configuration-and-resolution)
- [resolve() vs resolveAndBind()](#resolve-vs-resolveandbind)
- [One-loader, Many-loader, and Custom-loader Mapping](#layer-loader-strategies)
- [ServiceLoader.load(layer, service)](#serviceloader-layer-discovery)
- [Creating New Layers when the Plugin Set Changes](#layer-replacement-model)

## <a id="modulelayer-purpose">When a Plugin Architecture Needs ModuleLayer</a>

<details>
<summary>Click for details</summary>

`ModuleLayer` is useful when plugins are **explicit named modules** and the host needs to create an additional module graph in the JVM after the boot layer already exists.

Do not introduce a `ModuleLayer` merely because providers are packaged as JARs. Startup discovery on the class path or module path is simpler when the provider set is fixed when the process starts.

`ModuleLayer` becomes useful when the host needs to:

- resolve modules from a separate plugin directory or repository;
- keep a module graph distinct from the boot layer;
- choose one-loader or many-loader mapping;
- discover services in the resulting layer;
- replace a plugin generation by creating another layer rather than mutating the boot graph.

The mental model is:

```text
module artifacts
→ ModuleFinder
→ Configuration
→ ModuleLayer
→ ServiceLoader.load(layer, service)
```

A layer represents a runtime module graph; it is not a mutable list of plugins.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-and-resolution">ModuleFinder, Configuration, and Resolution</a>

<details>
<summary>Click for details</summary>

`ModuleFinder` locates module artifacts, while `Configuration` represents a **resolved readability graph**.

For example:

```java
Path plugins = Path.of("plugins");
ModuleFinder finder = ModuleFinder.of(plugins);

ModuleLayer parent = ModuleLayer.boot();
Configuration parentConfig = parent.configuration();

Configuration config = parentConfig.resolve(
        finder,
        ModuleFinder.of(),
        Set.of("com.example.report.plugins"));
```

Resolution starts from root modules and computes the required dependency/readability graph.

The configuration can then be defined as a runtime layer:

```java
ModuleLayer layer = parent.defineModulesWithOneLoader(
        config,
        ClassLoader.getSystemClassLoader());
```

Keep the distinction clear:

```text
Configuration
→ resolved graph

ModuleLayer
→ runtime modules + ClassLoader mapping
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resolve-vs-resolveandbind">resolve() vs resolveAndBind()</a>

<details>
<summary>Click for details</summary>

`resolve()` performs normal root-module and dependency resolution.

`resolveAndBind()` performs resolution **with service binding**. When modules in the graph `uses` a service, service binding can bring matching provider modules into the resulting configuration.

For example:

```java
Configuration config = parent.configuration()
        .resolveAndBind(
                finder,
                ModuleFinder.of(),
                Set.of("com.example.report.host"));
```

If the host module `uses com.example.export.ReportExporter` and modules observable through the finder `provides` that service, service binding can include those providers without the host directly requiring each provider module.

This is where JPMS service binding becomes useful for runtime extensibility.

`resolveAndBind()` is not “scan every module and load everything.” Normal observability, parent, root, and resolution rules still apply.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layer-loader-strategies">One-loader, Many-loader, and Custom-loader Mapping</a>

<details>
<summary>Click for details</summary>

When defining a layer, resolved modules must be mapped to class loaders.

Java provides three main choices:

```text
defineModulesWithOneLoader
→ all modules in the configuration use one newly created loader

defineModulesWithManyLoaders
→ each module is mapped to its own loader

defineModules
→ the application supplies a module-name → ClassLoader mapping function
```

One-loader is simpler when modules trust each other and strong dependency isolation is unnecessary.

Many-loaders increases isolation but also introduces more loader boundaries. Types crossing boundaries must still respect module readability and compatible shared identity.

Custom mappings serve specialized architectures and carry more responsibility for loader readiness and delegation behavior.

With `defineModules(Configuration, Function<String, ClassLoader>)`, the application must ensure that custom loaders:

- respect module readability in their delegation behavior;
- are preferably parallel-capable to reduce class-loading deadlock risk;
- are ready to load classes and resources from their mapped modules before the layer is used.

The API also does not guarantee that custom layer creation is atomic in every implementation: an operation can fail after some modules have already been defined to the JVM. That makes custom mapping an advanced tool for a real loader-topology requirement rather than a default plugin choice.

Do not choose many-loaders merely because there are many plugins. Start with actual isolation, dependency-conflict, and replacement requirements.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serviceloader-layer-discovery">ServiceLoader.load(layer, service)</a>

<details>
<summary>Click for details</summary>

Once a layer exists, providers can be discovered explicitly in that layer context:

```java
ServiceLoader<ReportExporter> exporters =
        ServiceLoader.load(layer, ReportExporter.class);
```

This overload searches:

```text
the current layer
→ ancestor layers according to layer traversal rules
```

It does **not** include providers in unnamed modules/class-path configuration as part of this layer-based discovery scope.

A named-module caller still has to satisfy the service-usage contract (`uses`).

The main advantage is an explicit discovery context: the host knows which module-layer graph represents the current plugin generation.

Selection and lifecycle remain application responsibilities. `ServiceLoader.load(layer, ...)` does not activate plugins or resolve provider ambiguity for the host.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layer-replacement-model">Creating New Layers when the Plugin Set Changes</a>

<details>
<summary>Click for details</summary>

A `ModuleLayer` is created from an already-resolved `Configuration`. It is not a mutable registry where modules are freely added and removed afterward.

When the plugin set changes, a clearer model is:

```text
old artifacts
→ old Configuration
→ old Layer

new artifacts
→ new Configuration
→ new Layer
```

The host can then:

1. discover and validate the new layer;
2. activate new providers;
3. atomically switch registry/context;
4. drain and clean up the old generation;
5. release references to old layers/loaders when collection is desired.

`ModuleLayer.Controller` can adjust some reads/exports/opens relationships, but that does not turn a layer into a general module add/remove container.

If old threads, resources, or references retain plugin class loaders, old plugin classes will remain reachable even after the host switches layers.

</details>

- [Quay lại đầu trang](#back-to-top)
