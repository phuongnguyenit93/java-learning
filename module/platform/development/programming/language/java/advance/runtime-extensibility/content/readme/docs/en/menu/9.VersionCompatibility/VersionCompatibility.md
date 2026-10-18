<a id="back-to-top"></a>

# Host-Plugin Version Compatibility

## Menu
- [The Host-Plugin Compatibility Contract](#compatibility-contract)
- [Evolving an SPI without Breaking Existing Providers](#spi-evolution-rules)
- [Host-Provider Version Negotiation](#host-provider-version-negotiation)
- [Dependency Version Conflicts](#dependency-version-conflicts)
- [Capability Versioning beyond Version-number Checks](#capability-versioning)
- [Testing a Compatibility Matrix](#compatibility-testing)

## <a id="compatibility-contract">The Host-Plugin Compatibility Contract</a>

<details>
<summary>Click for details</summary>

A compatibility contract is the set of assumptions that lets a host and plugin continue to work together across versions.

It is broader than “the code still compiles”:

```text
binary compatibility
→ existing classes/method references still link

source compatibility
→ old provider source can still compile

semantic compatibility
→ API meaning has not changed incompatibly

configuration/data compatibility
→ required configuration and data contracts still work
```

Adding an SPI method may affect source/binary compatibility. Keeping the same signature while changing the meaning of `supports()` can be a semantic break.

A host-plugin contract should make important assumptions explicit, including:

- API or capability version;
- lifecycle expectations;
- threading and resource rules;
- error behavior;
- required and optional features.

`ServiceLoader` does not validate this application compatibility for you. It only enforces the Java service-provider loading rules.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spi-evolution-rules">Evolving an SPI without Breaking Existing Providers</a>

<details>
<summary>Click for details</summary>

SPI evolution should prefer changes that allow existing providers to remain useful when that behavior is genuinely valid.

To isolate the evolution problem, the following example uses a **reduced `ReportExporter` variant** and adds an optional capability with a sensible default. It is an evolution sketch, not the current signature of the running example:

```java
public interface ReportExporter {
    void export(Report report);

    default boolean supportsStreaming() {
        return false;
    }
}
```

Default methods can reduce disruption, but use them only when the default semantics are truly correct. A fake default can hide incompatibility.

Other strategies include:

- add a separate capability interface such as `StreamingExporter`;
- support two SPI generations during migration;
- keep shared DTOs backward compatible;
- deprecate before removal;
- avoid silently changing exception or `null` semantics.

Do not use implementation casts as a hidden extension mechanism. If the host needs a capability, model it in a contract.

Validate evolution rules with contract tests against older providers rather than relying only on code review.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="host-provider-version-negotiation">Host-Provider Version Negotiation</a>

<details>
<summary>Click for details</summary>

Version negotiation lets host and plugin decide whether they can collaborate and at what capability level.

A simple descriptor might expose:

```java
interface PluginDescriptor {
    int apiMajor();
    int apiMinor();
}
```

The host can apply policy such as:

```text
different major
→ reject

same major, older minor
→ allow when required capabilities are still present
```

A version number alone is rarely sufficient. Two plugins both labeled `2.1` may expose different optional capabilities or environment constraints.

Negotiation is usually stronger when it combines:

```text
contract version
+ capability set
+ environment constraints
+ host policy
```

Perform validation before activation so incompatibility fails predictably instead of on the first business request.

Semantic Versioning or another version scheme is an application decision; Java does not define a universal host-plugin negotiation policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-version-conflicts">Dependency Version Conflicts</a>

<details>
<summary>Click for details</summary>

Dependency conflict occurs when the host or different plugins require incompatible versions of one library.

For example:

```text
host → library X 1.x
plugin A → library X 1.x
plugin B → library X 2.x
```

With one shared class loader, only one definition is ultimately resolved according to loading rules. A plugin can then fail with `NoSuchMethodError`, `NoClassDefFoundError`, or subtler behavioral incompatibility.

Possible strategies include:

- align versions across the system;
- shade or relocate a private dependency;
- isolate plugins with separate loaders;
- keep the shared contract module minimal;
- avoid passing private-library types across the plugin boundary.

Isolation does not solve every conflict automatically. Types exchanged across the boundary still need compatible identity.

Dependency conflicts are a reason to design shared/private dependency policy early rather than introducing custom loaders only after production failures.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="capability-versioning">Capability Versioning beyond Version-number Checks</a>

<details>
<summary>Click for details</summary>

Capability versioning focuses on **what the provider can do** rather than only on “which release is this provider?”

For example:

```java
enum ExportCapability {
    PDF_A,
    STREAMING,
    ENCRYPTION
}

Set<ExportCapability> capabilities();
```

The host can express a requirement as:

```text
request needs PDF_A + ENCRYPTION
→ filter by capabilities
→ then apply version/priority policy
```

This is useful when feature rollout does not align perfectly with release numbers or providers implement different subsets.

Do not create a version dimension for every method. Model compatibility dimensions that the host actually needs to decide.

Combining capabilities with contract versions can enable gradual migration: older providers continue serving basic requests while new capabilities route to newer providers.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compatibility-testing">Testing a Compatibility Matrix</a>

<details>
<summary>Click for details</summary>

Compatibility should be tested as a **matrix** because failures often exist only in particular host/provider combinations.

For example:

```text
Host 2.0 × Plugin 1.8 → supported
Host 2.0 × Plugin 2.0 → supported
Host 2.0 × Plugin 3.0 → rejected
```

Tests should cover:

- discovery from real artifacts;
- contract method invocation;
- capability negotiation;
- lifecycle start/stop;
- old provider with a new host;
- new provider with the minimum supported host;
- dependency/class-loader isolation when used;
- clear rejection of incompatible providers.

One reusable contract-test suite can be applied to every provider implementation.

Include integration tests with actual JAR/module packaging because `META-INF/services`, module descriptors, and class-loader visibility problems do not appear when tests simply call `new Provider()`.

</details>

- [Quay lại đầu trang](#back-to-top)
