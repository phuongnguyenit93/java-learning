<a id="back-to-top"></a>

# Provider Discovery and Selection Strategy

## Menu
- [Provider Discovery vs Provider Selection](#discovery-vs-selection)
- [Designing an Explicit Provider Selection Policy](#provider-selection-policy)
- [Capability- and Metadata-based Selection](#capability-based-selection)
- [Provider Ordering and findFirst() Caveats](#provider-ordering-caveats)
- [No-provider Cases and Fallback Strategies](#no-provider-and-fallback)
- [Multiple, Duplicate, and Broken Providers](#duplicate-and-broken-providers)

## <a id="discovery-vs-selection">Provider Discovery vs Provider Selection</a>

<details>
<summary>Click for details</summary>

Discovery and selection are separate steps:

```text
Discovery
→ which providers exist in this runtime context?

Selection
→ which provider satisfies the current request and policy?
```

`ServiceLoader` handles the first step. The application owns the second.

For example, a host that needs a PDF exporter might do:

```java
ReportExporter exporter = ServiceLoader.load(ReportExporter.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.format().equalsIgnoreCase("pdf"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No PDF exporter"));
```

Here `findFirst()` is only a terminal operation after the application has applied part of its policy.

Do not turn “the provider discovered first” into a business rule without an explicit contract. Discovery order can depend on module topology, class-loader/resource ordering, and deployment structure.

A sound architecture treats discovery as input to selection rather than treating discovery order as the selection policy itself.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-selection-policy">Designing an Explicit Provider Selection Policy</a>

<details>
<summary>Click for details</summary>

A selection policy should be explicit instead of scattered through host code as unrelated `if/else` statements.

For example:

```java
public final class ExporterSelector {
    public ReportExporter select(
            Collection<ReportExporter> providers,
            String format,
            Report report) {

        return providers.stream()
                .filter(p -> p.format().equalsIgnoreCase(format))
                .filter(p -> p.supports(report))
                .findFirst()
                .orElseThrow(() -> new NoExporterException(format));
    }
}
```

Selection can use:

- format, protocol, or capability;
- supported feature set;
- version compatibility;
- environment configuration;
- application-defined priority;
- health or availability when the architecture models it.

Priority should come from an **application contract**, not incidental discovery order.

When selection becomes non-trivial, isolate a provider registry/selector from business services. Domain code should not need to know whether providers came from the class path, module path, or a module layer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="capability-based-selection">Capability- and Metadata-based Selection</a>

<details>
<summary>Click for details</summary>

Providers can sometimes be selected using capabilities without instantiating every provider immediately.

If capability is type-level metadata:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExportFormat {
    String value();
}
```

the host can inspect `Provider.type()`:

```java
Optional<ReportExporter> pdf =
        ServiceLoader.load(ReportExporter.class)
                .stream()
                .filter(p -> {
                    ExportFormat meta = p.type().getAnnotation(ExportFormat.class);
                    return meta != null && meta.value().equals("pdf");
                })
                .map(ServiceLoader.Provider::get)
                .findFirst();
```

This example is appropriate when the annotation is present on the type returned by `Provider.type()`. If an explicit module uses a static `provider()` method, `Provider.type()` is the method's return type rather than necessarily the factory class named in `provides`. Do not design selection metadata around the assumption that `type()` always returns the declared provider class.

When capability depends on the request or runtime state, a service method is usually clearer:

```java
boolean supports(Report report);
```

Metadata should answer a concrete selection question. Avoid designing a large annotation or descriptor that attempts to describe every provider detail.

If priority matters, model it explicitly and define tie-breaking behavior rather than inheriting ordering accidentally from discovery.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-ordering-caveats">Provider Ordering and findFirst() Caveats</a>

<details>
<summary>Click for details</summary>

`findFirst()` returns the first provider yielded by the loader traversal. It does not make discovery order a universal priority contract.

Java defines some local ordering behavior, such as the order of multiple providers declared by one module and the order derived from class-path configuration resources. But ordering between modules in the same layer is not a total business order an application should rely on.

This can therefore be too weak:

```java
ReportExporter exporter =
        ServiceLoader.load(ReportExporter.class)
                .findFirst()
                .orElseThrow();
```

when multiple providers expose different quality or capabilities.

A stronger flow is:

```text
discover candidates
→ validate compatibility
→ filter by capability
→ apply explicit priority/tie-breaker
→ select
```

`findFirst()` is appropriate when any valid provider is genuinely acceptable or when the pipeline has already imposed the host's selection policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="no-provider-and-fallback">No-provider Cases and Fallback Strategies</a>

<details>
<summary>Click for details</summary>

Having no provider is a valid `ServiceLoader` state; a service may have zero providers.

The host has to define the meaning:

```text
required extension
→ fail fast during startup/activation

optional extension
→ disable the feature

default implementation
→ use an intentional fallback
```

Example fallback:

```java
ReportExporter exporter = loader.stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.format().equals("txt"))
        .findFirst()
        .orElse(DEFAULT_TEXT_EXPORTER);
```

Do not silently fall back when a missing provider indicates a deployment error. Silent fallback can hide broken configuration and create surprising production behavior.

A startup validation phase can verify required capabilities before accepting traffic:

```text
required capabilities
→ discover
→ validate at least/exactly one usable provider as required
→ publish readiness
```

Observability should make it clear whether a missing provider is optional or fatal.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="duplicate-and-broken-providers">Multiple, Duplicate, and Broken Providers</a>

<details>
<summary>Click for details</summary>

“Multiple providers” does not automatically mean duplication. Multiple implementations are a normal SPI use case.

Duplication can occur at different levels:

```text
the same provider class is listed repeatedly
→ Java has duplicate-elimination rules for service configuration

different provider classes claim the same business capability
→ the application must resolve the ambiguity
```

For example, two exporters may both report `format() == "pdf"`. The host needs a policy: prefer one, reject the ambiguity, or choose by capability/version.

A broken provider is different from an unsupported provider. A broken provider is present in configuration but fails to load or instantiate; an unsupported provider is healthy but does not match the current request.

A useful architecture often normalizes discovery once:

```text
ServiceLoader
→ validate provider
→ normalize metadata
→ detect capability collisions
→ build an immutable registry
```

The request path can then use a validated registry instead of performing raw discovery repeatedly.

A selection policy only helps when providers are actually **registered and deployed correctly in the runtime topology**. The next chapter therefore moves from “which provider should be selected?” to how the class path and module path encode the provider registration and discovery boundary.

</details>

- [Quay lại đầu trang](#back-to-top)
