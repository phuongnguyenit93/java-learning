<a id="back-to-top"></a>

# ServiceLoader and Provider Discovery

## Menu
- [The ServiceLoader Discovery Model](#serviceloader-discovery-model)
- [Lazy Discovery, Provider Caching, and Instantiation](#lazy-discovery-and-cache)
- [Iterator vs ServiceLoader.Provider Stream](#iterator-vs-provider-stream)
- [ClassLoader Context for Provider Discovery](#discovery-classloader-context)
- [reload(), Cache Invalidation, and Concurrency Boundaries](#reload-and-concurrency)
- [ServiceConfigurationError and Discovery Failure Surfaces](#serviceconfigurationerror)

## <a id="serviceloader-discovery-model">The ServiceLoader Discovery Model</a>

<details>
<summary>Click for details</summary>

`ServiceLoader<S>` is Java's standard facility for locating implementations of a **service type** `S` in the runtime environment.

The basic flow is:

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

for (ReportExporter exporter : loader) {
    System.out.println(exporter.format());
}
```

`ServiceLoader` is not a dependency-injection container. It does not resolve arbitrary constructor graphs, scopes, or application lifecycle. It follows the service/provider contracts defined by the Java platform.

A service may have:

```text
zero providers
one provider
many providers
```

The host therefore has to design both the “no provider” and “multiple providers” cases.

Provider sources depend on the overload and context being used:

- class-loader-based discovery can locate providers in relevant named and unnamed modules/class-path resources;
- layer-based discovery via `ServiceLoader.load(layer, service)` searches the supplied layer and its ancestors.

The key point is that `ServiceLoader` **locates and loads** providers. The application still owns provider selection and domain behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lazy-discovery-and-cache">Lazy Discovery, Provider Caching, and Instantiation</a>

<details>
<summary>Click for details</summary>

Providers are discovered and instantiated **lazily** rather than all being created when `ServiceLoader.load(...)` returns.

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

// This does not mean every provider has already been instantiated.
```

During iteration the loader behaves conceptually like this:

```text
cached providers
→ yielded first in load/instantiation order

unprocessed providers
→ located lazily
→ instantiated when required
→ added to the loader cache
```

The cache belongs to a particular **ServiceLoader instance**. Two loader objects have separate discovery/cache state.

Laziness is useful when many providers exist but a request only needs one. It also means provider errors may surface while iterating or consuming a stream rather than at the initial `load(...)` call.

```java
for (ReportExporter exporter : loader) {
    if (exporter.supports(report)) {
        exporter.export(report);
        break;
    }
}
```

Do not confuse the provider cache with an application-level singleton registry. It is discovery behavior, not a plugin lifecycle contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iterator-vs-provider-stream">Iterator vs ServiceLoader.Provider Stream</a>

<details>
<summary>Click for details</summary>

The two main traversal APIs expose different things:

```text
iterator()/enhanced for
→ yields service/provider instances
→ providers are instantiated as needed

stream()
→ yields ServiceLoader.Provider<S>
→ provider type can be inspected before Provider.get()
```

Iterator example:

```java
for (ReportExporter exporter : ServiceLoader.load(ReportExporter.class)) {
    if ("pdf".equals(exporter.format())) {
        exporter.export(report);
    }
}
```

Stream example when type metadata is useful:

```java
List<ReportExporter> exporters =
        ServiceLoader.load(ReportExporter.class)
                .stream()
                .filter(p -> p.type().isAnnotationPresent(StableProvider.class))
                .map(ServiceLoader.Provider::get)
                .toList();
```

`Provider.type()` exposes the type represented by the `ServiceLoader.Provider`, while `Provider.get()` obtains an instance. For constructor-based providers this is the provider class. For an explicit-module provider that uses a static `provider()` method, `type()` is the **return type of `provider()`**, not necessarily the class named in `provides ... with ...`.

The stream does not make discovery eager by default. Providers are still located lazily as the stream pipeline is consumed.

Use iteration when the instance is what matters immediately. Use the `Provider` stream when selection can inspect metadata on the type exposed by `Provider.type()` before instantiation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="discovery-classloader-context">ClassLoader Context for Provider Discovery</a>

<details>
<summary>Click for details</summary>

The common convenience call:

```java
ServiceLoader.load(ReportExporter.class)
```

uses the current thread's **context class loader** as the starting discovery context.

That matters in containers and plugin systems where host code may be defined by one loader while extensions are visible only from another context loader.

When the discovery context should be explicit, use:

```java
ClassLoader pluginLoader = ...;

ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class, pluginLoader);
```

Class-loader-based discovery is more than “scan this directory.” Visibility, parent delegation, named modules associated with loaders/layers, and service-configuration resources in unnamed modules all influence what can be found.

One practical consequence is that a `ServiceLoader` created with the context-loader convenience method should not be cached VM-wide when multiple applications use different context class loaders. A provider appropriate for one application may be invisible or inappropriate for another.

When plugin isolation uses dedicated class loaders, make loader context an explicit part of the plugin boundary rather than relying everywhere on ambient thread state.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reload-and-concurrency">reload(), Cache Invalidation, and Concurrency Boundaries</a>

<details>
<summary>Click for details</summary>

`reload()` has one central job:

```text
clear the provider cache of this ServiceLoader instance
```

Later iteration or stream processing then performs discovery again from scratch for that loader context.

```java
ServiceLoader<ReportExporter> loader =
        ServiceLoader.load(ReportExporter.class);

// ... providers were used
loader.reload();
```

`reload()` does **not**:

- invoke an application `stop()` lifecycle hook;
- close provider-owned resources;
- unload classes;
- unload modules;
- guarantee that an old JAR can be deleted;
- turn the architecture into a hot-reload plugin system.

Existing iterators and streams should be discarded after a reload; their sources are fail-fast with respect to the cleared provider cache.

A `ServiceLoader` instance is also **not safe for concurrent use by multiple threads**. The host must synchronize access or design loader ownership accordingly.

Real plugin replacement requires lifecycle, resource cleanup, and often loader/layer replacement. `ServiceLoader.reload()` is only a discovery-cache primitive inside that broader flow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serviceconfigurationerror">ServiceConfigurationError and Discovery Failure Surfaces</a>

<details>
<summary>Click for details</summary>

`ServiceConfigurationError` indicates that the service-provider environment is invalid or a provider cannot be loaded or instantiated according to the platform contract.

Typical causes include:

- a provider class cannot be loaded;
- the provider is not assignable to the service when constructor-based instantiation requires it;
- a required public no-argument provider constructor is missing;
- a `provider()` method has an invalid signature or return type;
- `provider()` returns `null` or throws;
- a `META-INF/services/...` file is malformed;
- an I/O error occurs while reading provider configuration.

Because discovery is lazy, the error may surface from `hasNext()`, `next()`, or during stream processing.

```java
try {
    for (ReportExporter exporter : loader) {
        register(exporter);
    }
} catch (ServiceConfigurationError error) {
    log.error("Broken exporter provider", error);
}
```

Do not silently swallow every `ServiceConfigurationError`. Broken provider configuration is usually a deployment/configuration defect that deserves clear observability.

If the product requirement allows skipping a bad provider and continuing, make that recovery policy explicit and test it. Do not build the architecture on an assumption that every failure is recoverable.

Once discovery can produce zero, one, many, or broken providers, the next problem is deliberately separate from discovery itself: **which usable provider should the application select for the current request, and how should ambiguity be handled?**

</details>

- [Quay lại đầu trang](#back-to-top)
