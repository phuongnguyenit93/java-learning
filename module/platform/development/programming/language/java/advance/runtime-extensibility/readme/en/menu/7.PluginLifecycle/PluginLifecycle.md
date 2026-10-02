<a id="back-to-top"></a>

# Plugin Lifecycle

## Menu
- [The Plugin Lifecycle Model](#plugin-lifecycle-model)
- [From Discovery to Activation](#discovery-to-activation)
- [Initialization and Startup Contracts](#initialization-and-startup)
- [Deactivation, Cleanup, and Resource Ownership](#deactivation-and-cleanup)
- [Rediscovery, ServiceLoader.reload(), and Plugin Reloading](#rediscovery-vs-reload)
- [Replacement, Upgrade, and Rollback Boundaries](#replacement-and-upgrade)

## <a id="plugin-lifecycle-model">The Plugin Lifecycle Model</a>

<details>
<summary>Click for details</summary>

`ServiceLoader` does not define plugin lifecycle. If plugins own resources or need startup and shutdown behavior, the host must define a lifecycle model.

A simple model might be:

```text
DISCOVERED
→ VALIDATED
→ INITIALIZED
→ ACTIVE
→ STOPPING
→ STOPPED
```

Not every plugin needs every state. A stateless provider may only need discovery and use.

A state model becomes valuable when:

- initialization can fail;
- activation acquires resources;
- plugins serve concurrent requests;
- replacement needs traffic draining;
- cleanup must be guaranteed.

Lifecycle orchestration normally belongs to the host, while the plugin exposes the hooks and capabilities required by the contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="discovery-to-activation">From Discovery to Activation</a>

<details>
<summary>Click for details</summary>

Discovery only proves that a provider **can be located**. It does not prove that the provider is ready to serve.

Keep activation steps separate:

```text
discover
→ validate class/metadata
→ validate compatibility
→ create provider
→ initialize resources
→ mark ACTIVE
```

A `PdfExporter` might be discoverable but fail activation because a font engine, license, or configuration is unavailable.

Do not publish the plugin to the request-serving registry before activation succeeds.

When several plugins start together, define partial-failure policy explicitly:

- fail host startup for a required plugin;
- skip an optional plugin;
- run in degraded mode;
- retry according to a separate policy.

Selection should only see providers in a state that is valid for receiving work.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="initialization-and-startup">Initialization and Startup Contracts</a>

<details>
<summary>Click for details</summary>

Initialization is where a plugin receives context/configuration and allocates the resources it needs.

For example:

```java
public interface Plugin {
    void initialize(PluginContext context) throws PluginException;
    void start() throws PluginException;
}
```

Separating `initialize` and `start` can let the host construct and validate all plugins before publishing any of them.

The contract must say whether initialization is one-shot or idempotent. Do not assume repeated initialization is safe.

If a provider constructor performs heavy work such as network calls, thread creation, or large file access, discovery may accidentally trigger those side effects. Complex plugins are often easier to control when constructors stay light and lifecycle hooks perform resource work.

Startup failures should retain the plugin identity and original cause so diagnosis remains straightforward.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deactivation-and-cleanup">Deactivation, Cleanup, and Resource Ownership</a>

<details>
<summary>Click for details</summary>

Deactivation answers when a plugin stops accepting work and who releases each resource.

A common flow is:

```text
mark unavailable
→ stop new requests
→ wait for or cancel in-flight work
→ invoke stop/close
→ release references and resources
```

`AutoCloseable` can be a suitable contract for a simple lifecycle:

```java
public interface ManagedExporter extends ReportExporter, AutoCloseable {
    @Override
    void close();
}
```

More complex plugins may need multiple phases instead of one `close()` method.

Resource ownership must be explicit. If the host supplies an executor, the host typically owns it. If the plugin creates a scheduler, the plugin should stop it. Ambiguous ownership is a common source of leaks.

Cleanup also needs to work after partial initialization, so lifecycle code should be able to roll back partially acquired resources.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rediscovery-vs-reload">Rediscovery, ServiceLoader.reload(), and Plugin Reloading</a>

<details>
<summary>Click for details</summary>

Three concepts are easy to confuse:

```text
rediscovery
→ search the runtime environment again

ServiceLoader.reload()
→ clear one ServiceLoader's cache and rediscover

plugin reload
→ deactivate old plugin + release resources + load/activate replacement
```

`ServiceLoader.reload()` only covers a small part of the middle step. It does not stop old provider instances and does not unload their classes.

If the plugin set changes through another JAR or class loader, the host may need a new discovery context. Modular plugins commonly require a new `Configuration`/`ModuleLayer`.

Hot reload is a separate architecture capability with races to handle:

- requests still using the old plugin;
- replacement activation failures;
- slow cleanup;
- stale references retaining the old plugin.

Without a real hot-reload requirement, process restart is often simpler and safer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="replacement-and-upgrade">Replacement, Upgrade, and Rollback Boundaries</a>

<details>
<summary>Click for details</summary>

Safe replacement usually follows **prepare → switch → retire** rather than stopping the old plugin before the new one proves usable.

```text
discover candidate
→ validate compatibility
→ initialize new plugin
→ health check
→ atomically switch registry/reference
→ drain old plugin
→ stop old plugin
```

If the new plugin fails before the switch, the host can keep the old provider.

Rollback after the switch can be harder if requests already produced side effects with the new version. Runtime lifecycle therefore does not automatically solve data or domain migration.

A versioned registry or immutable snapshot can help in-flight requests retain a consistent provider reference while replacement occurs.

If the architecture cannot provide rollback, document that limitation instead of implying that every “reload” is reversible.

</details>

- [Quay lại đầu trang](#back-to-top)
