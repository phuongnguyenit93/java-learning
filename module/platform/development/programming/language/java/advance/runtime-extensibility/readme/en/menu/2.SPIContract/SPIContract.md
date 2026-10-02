<a id="back-to-top"></a>

# SPI Contract

## Menu
- [What an SPI Is and Why a Stable Extension Contract Matters](#spi-contract-purpose)
- [Designing the Service Type](#service-type-design)
- [Designing the Provider Type](#provider-type-design)
- [Direct Providers vs Factory or Indirection Providers](#provider-as-factory)
- [Contract Stability and Host-Provider Decoupling](#contract-stability)

## <a id="spi-contract-purpose">What an SPI Is and Why a Stable Extension Contract Matters</a>

<details>
<summary>Click for details</summary>

An SPI (Service Provider Interface) is the contract a host publishes so independently developed providers can supply a capability to the system.

The important property is the dependency direction:

```text
host ────────┐
             ↓
        service contract
             ↑
provider ────┘
```

The host and providers depend on the contract; the host does not depend directly on provider implementations.

Without a stable contract, a “plugin” may merely be a dynamically loaded class that still exposes implementation details. The contract gives both sides a shared vocabulary that can evolve independently within known compatibility rules.

A useful SPI should establish:

- what capability a provider supplies;
- stable inputs and outputs;
- error semantics the host must handle;
- whether providers are stateful or have lifecycle requirements;
- expectations around thread safety, resources, and compatibility.

An SPI is therefore an **architecture boundary**, not just an interface for `ServiceLoader` to locate.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-type-design">Designing the Service Type</a>

<details>
<summary>Click for details</summary>

The service type should describe **the capability the host needs**, not the provider's internal architecture.

To illustrate a contract with richer semantics than the baseline from the previous chapter, the following variant intentionally adds a capability check, domain type, and error contract:

```java
public interface ReportExporter {
    String format();
    boolean supports(Report report);
    void export(Report report) throws ExportException;
}
```

The host now has enough information to:

1. identify a provider's format or capability;
2. ask whether it supports the request;
3. invoke the operation;
4. handle errors at the contract boundary.

A service type should remain small, but not so small that the host must use reflection or implementation casts to discover provider capabilities. If selection requires important metadata, represent it through stable service methods or clearly defined metadata.

Avoid leaking host-internal types into the SPI:

```java
// Strong coupling: providers now depend on a host-internal class
void export(InternalReportEntity entity);
```

Prefer DTOs or value types in the contract module with intentionally stable semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-type-design">Designing the Provider Type</a>

<details>
<summary>Click for details</summary>

A provider type is the type that runtime discovery can locate and use to supply a service. Under the `ServiceLoader` contract, the provider type must be `public` and must not be an inner class. For an explicit module, the provider type may even be an interface or abstract class when it declares a valid static `provider()` method.

In the direct case:

```java
public final class PdfExporter implements ReportExporter {
    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public boolean supports(Report report) {
        return true;
    }

    @Override
    public void export(Report report) {
        // PDF implementation
    }
}
```

The provider should not require the host to know helper classes, library-specific types, or construction details.

Provider design also needs clear answers to lifecycle questions:

- Is one instance reusable?
- Is it thread-safe?
- Does it retain long-lived resources?
- How are initialization failures reported?
- Who owns cleanup?

`ServiceLoader` defines platform-level construction rules, but it does not define the application's plugin lifecycle. Provider design must therefore fit the lifecycle policy the host establishes later.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-as-factory">Direct Providers vs Factory or Indirection Providers</a>

<details>
<summary>Click for details</summary>

The provider does not always need to be the final object that performs the domain operation.

Two common models are:

```text
Direct provider
→ the provider is the service implementation

Factory/indirection provider
→ the provider creates the real implementation on demand
```

A direct provider works well for cheap, stateless, or reusable objects:

```java
public final class PdfExporter implements ReportExporter { ... }
```

A factory is useful when the final object is expensive or request-specific:

```java
public interface ExporterFactory {
    String format();
    ReportExporter create(ExportOptions options);
}
```

The host discovers factories once and lets them create exporters for particular configurations.

For providers deployed as explicit modules, Java also supports a public static `provider()` method. That mechanism allows the class named in `provides ... with ...` to act as an indirection point without itself being assignable to the service. The domain design should still decide whether indirection is useful; the platform mechanism merely enables it.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contract-stability">Contract Stability and Host-Provider Decoupling</a>

<details>
<summary>Click for details</summary>

An SPI is a boundary across independently compiled artifacts, so contract changes deserve more care than changes to an internal API.

Even a small-looking change, such as adding another abstract method, may make existing providers incompatible with the new host expectations.

Useful design rules include:

- keep the contract module small and dependency-light;
- avoid implementation-specific types;
- use default methods only when a real backward-compatible default exists;
- add capabilities in ways that allow older providers to remain useful when possible;
- document `null`, exception, concurrency, and resource-ownership behavior;
- never require the host to cast a provider to its concrete class.

For example, avoid this:

```java
if (exporter instanceof PdfExporter pdf) {
    pdf.enableInternalFeature();
}
```

If the capability belongs to the contract, model it in the SPI. Otherwise the host is breaking the abstraction boundary.

Version compatibility is covered in depth later. The key idea here is that the **SPI is the public compatibility surface between a host and its extensions**.

Once the contract is stable, the next question is no longer “what does a provider implement?” but **how the host finds providers at runtime**. The next chapter therefore moves from SPI design into `ServiceLoader` and discovery semantics.

</details>

- [Quay lại đầu trang](#back-to-top)
