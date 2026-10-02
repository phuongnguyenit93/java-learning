<a id="back-to-top"></a>

# Providers on the Class Path and Module Path

## Menu
- [META-INF/services on the Class Path](#classpath-provider-configuration)
- [Consumer Modules: requires and uses](#module-consumer-requires-uses)
- [Provider Modules and the provides Directive](#module-provider-provides)
- [Provider Constructors and provider() Methods](#provider-instantiation-rules)
- [Automatic Modules and provider() Method Limitations](#automatic-module-limitations)
- [Keeping the Host Decoupled from Provider Modules](#host-provider-decoupling)

## <a id="classpath-provider-configuration">META-INF/services on the Class Path</a>

<details>
<summary>Click for details</summary>

On the class path, a provider is registered with a **provider-configuration file** under:

```text
META-INF/services/<fully-qualified-service-name>
```

For a service named:

```text
com.example.export.ReportExporter
```

the provider JAR contains:

```text
META-INF/services/com.example.export.ReportExporter
```

with entries such as:

```text
com.example.export.pdf.PdfExporter
com.example.export.csv.CsvExporter
```

Each line names a provider class. The file is UTF-8 and supports blank lines and `#` comments.

The provider class must be visible from the discovery class loader and satisfy the constructor-based provider contract used for class-path providers.

This resource-based registration keeps the host free from compile-time provider dependencies, but it makes packaging correctness important. Shading or assembly can accidentally drop or overwrite service files, so test the actual built artifact.

One migration detail matters when moving to JPMS: when a provider class is deployed in a **named module**, a matching entry in `META-INF/services` is not used as an unnamed-module provider registration. Named-module providers are declared through `provides` in the module descriptor. This avoids discovering the same provider through both the module descriptor and a service-configuration file.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-consumer-requires-uses">Consumer Modules: requires and uses</a>

<details>
<summary>Click for details</summary>

For a named-module consumer, using a service is declared in `module-info.java`.

If the service type is exported by `com.example.export.api`:

```java
module com.example.report.host {
    requires com.example.export.api;
    uses com.example.export.ReportExporter;
}
```

The directives serve different purposes:

```text
requires
→ lets the host read the module that exports the service type

uses
→ declares that the host consumes this service
```

When the service type is declared in the consumer module itself, it does not require itself, but `uses` still expresses service consumption.

For named-module callers, `uses` is not merely documentation. It is part of the service-loading/module contract used by resolution and service binding.

This module uses the JPMS rules needed for extensibility without duplicating the entire module-system curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-provider-provides">Provider Modules and the provides Directive</a>

<details>
<summary>Click for details</summary>

A provider in an explicit named module is registered with `provides ... with ...`:

```java
module com.example.export.pdf {
    requires com.example.export.api;

    provides com.example.export.ReportExporter
        with com.example.export.pdf.PdfExporter;
}
```

Important consequences are:

- the provider is declared by the module that contains it;
- a `provides` directive cannot name a provider class in another module;
- the implementation package usually does not need to be exported to the host;
- the host discovers the provider through the service contract instead of importing the implementation.

Avoiding an export for provider implementation packages preserves stronger encapsulation:

```text
host
→ sees service API
→ does not need provider implementation packages
```

A module may declare multiple providers for one service; their declaration order within that module contributes to the order used by `ServiceLoader` for those providers.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-instantiation-rules">Provider Constructors and provider() Methods</a>

<details>
<summary>Click for details</summary>

An explicit module provider supports two construction forms.

**1. Provider constructor**

```java
public final class PdfExporter implements ReportExporter {
    public PdfExporter() {}
}
```

The provider has a public no-argument constructor and the resulting instance is assignable to the service.

**2. Public static `provider()` method**

```java
public final class PdfExporterProvider {
    public static ReportExporter provider() {
        return new PdfExporter(loadNativeEngine());
    }
}
```

In this case `PdfExporterProvider` itself does not have to implement `ReportExporter`; the return type of `provider()` must be assignable to the service.

The provider method is useful when construction needs indirection or the class named in `provides` is not the final service object.

This is a precise Java service-provider convention: a public static no-argument method named `provider`, not an arbitrary application factory method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="automatic-module-limitations">Automatic Modules and provider() Method Limitations</a>

<details>
<summary>Click for details</summary>

An automatic module is a JAR without an explicit `module-info.class` that is placed on the module path and treated as a named module by JPMS.

For service providers it has an important limitation:

```text
provider()
→ not supported as the provider construction mechanism

provider constructor
→ required
```

The provider class must therefore satisfy the service relationship and expose a public no-argument constructor.

This matters during migration. A provider JAR that used `META-INF/services` on the class path may operate as an automatic module on the module path, but it does not gain the full expressiveness of an explicit module descriptor.

If the architecture needs explicit `provides`, strong implementation encapsulation, or the static `provider()` factory mechanism, migrate the provider to an explicit named module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-provider-decoupling">Keeping the Host Decoupled from Provider Modules</a>

<details>
<summary>Click for details</summary>

A central goal of service-provider architecture is for the host to depend on the **service contract**, not provider modules.

Prefer a host descriptor like:

```java
module com.example.report.host {
    requires com.example.export.api;
    uses com.example.export.ReportExporter;
}
```

instead of:

```java
module com.example.report.host {
    requires com.example.export.api;
    requires com.example.export.pdf;
    requires com.example.export.csv;
}
```

If the host requires every provider module, the extension set becomes a compile-time dependency list again and much of the decoupling benefit disappears.

Provider modules belong in the runtime image/module path or in a dynamically resolved configuration according to the deployment architecture. The host knows the contract and selection policy, not each implementation.

Tests should preserve this boundary too: provider contract tests validate each provider independently, and integration tests verify discovery from real deployment artifacts without importing concrete providers into host code.

At this point Java has supplied the **contract, registration, and discovery boundary**, but it still has not decided who owns validation, the selection registry, lifecycle, failure containment, or cleanup. Those responsibilities motivate the next chapter's move from ServiceLoader providers to a **plugin architecture**.

</details>

- [Quay lại đầu trang](#back-to-top)
