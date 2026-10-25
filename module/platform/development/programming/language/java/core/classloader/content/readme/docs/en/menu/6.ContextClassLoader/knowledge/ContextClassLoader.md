# Thread Context ClassLoader (TCCL)

Parent delegation works naturally when a child asks a parent for shared types. Framework discovery sometimes needs the opposite direction: code owned by a parent or shared library needs to discover application/provider classes that only a child loader can see.

Read the discovery vocabulary in the simplest possible way:

```text
SPI (Service Provider Interface)
→ an interface/contract published for others to implement

provider
→ an implementation of that SPI

ServiceLoader
→ a JDK API that discovers declared providers
```

In the running example, `Plugin` is the SPI and `HelloPlugin` is a provider.

The **Thread Context ClassLoader (TCCL)** gives code a thread-associated loader to consult for that situation. It is a discovery bridge, not a replacement for the defining-loader identity rules learned earlier.

## <a id="tccl-purpose">Why the Thread Context ClassLoader Exists</a>

Imagine a reusable framework class loaded by a parent loader:

```text
parent/shared loader
  └─ framework discovery code

child/application loader
  ├─ application classes
  └─ service provider implementation
```

Normal delegation points upward: the child can ask the parent. The parent cannot automatically search every child that might exist beneath it, and there may be many unrelated children.

The TCCL solves this by associating a loader with the **current thread**:

```java
ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
```

Framework/library code can use that loader as a caller-provided discovery context. The thread therefore carries information about "which application/provider namespace should this work use?"

That is why TCCL belongs in the ClassLoader module: it is a controlled way to cross a loader visibility direction that parent delegation alone does not solve.

It does **not** change who defines already-loaded classes. If the context loader finds and defines `demo.plugins.HelloPlugin`, that loader still participates in the class identity exactly as before.

### Why can the framework's own loader fail while TCCL succeeds?

Suppose framework code is parent-defined while the provider is visible only to a child loader:

```text
framework defining loader
→ sees parent/shared namespace
→ cannot see HelloPlugin in the child

thread context ClassLoader = PluginClassLoader
→ can see HelloPlugin and provider metadata
→ discovery can succeed
```

That is the core reason TCCL exists: code can use a **lookup context different from the framework class's own defining loader** when the architecture needs child/application visibility.

## <a id="tccl-discovery">Provider and Framework Discovery</a>

`ServiceLoader` is the classic example. The convenience form:

```java
ServiceLoader<Plugin> plugins = ServiceLoader.load(Plugin.class);
```

uses the current thread's context ClassLoader to locate provider configuration and provider classes.

For a plugin system, the host can expose the shared `Plugin` interface from a parent while a plugin loader provides an implementation and its service descriptor:

```text
META-INF/services/demo.api.Plugin
```

containing, for example:

```text
demo.plugins.HelloPlugin
```

Then discovery can run under the plugin loader as the TCCL:

```java
ClassLoader pluginLoader = ...;
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);

    for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
        System.out.println(plugin.name());
    }
} finally {
    thread.setContextClassLoader(previous);
}
```

The shared service type still has to be loader-compatible. Setting the TCCL does not repair a design where the provider implements a private child copy of `Plugin` while the host expects the parent copy.

Frameworks may use TCCL for other discovery tasks such as resource lookup or reflective loading. The principle is the same: parent-owned code obtains a loader that represents the current application's visibility context.

When the caller already knows the exact loader that should be used, an explicit API such as `ServiceLoader.load(service, loader)` is usually easier to reason about than ambient thread context. TCCL is most valuable when the framework contract intentionally relies on a loader context associated with the current thread so parent-owned code can act on behalf of child/application code.

## <a id="tccl-lifecycle">Save, Set, and Restore</a>

TCCL is mutable thread state. That means temporary changes must have a clear lifecycle.

If concurrency is not yet familiar, the minimum model is enough: a **Thread is one execution flow**, while a **thread pool reuses a set of Threads for many tasks**. Because the same Thread may later run unrelated work, state attached to it must be restored.

Use the save/set/restore pattern:

```java
Thread thread = Thread.currentThread();
ClassLoader oldLoader = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    runPluginDiscovery();
} finally {
    thread.setContextClassLoader(oldLoader);
}
```

The `finally` block matters even when discovery throws. Request threads and executor workers are commonly reused. Leaving a plugin loader installed on a pooled thread means unrelated later work inherits the wrong discovery context.

For the same reason, a method that changes the TCCL should usually treat that change like acquiring a resource or changing transaction/security context: establish the smallest useful scope and restore the previous value before returning.

Avoid assuming the previous loader is always the system ClassLoader. Containers, test tools, IDEs, application servers, and other frameworks may already have installed a meaningful context loader. Restoring the exact previous value preserves the caller's environment.

Newly created threads commonly inherit their initial context ClassLoader from the creating thread. That inheritance is another reason plugin code must manage long-lived child threads carefully and stop them when the plugin lifecycle ends.

## <a id="tccl-leak-risk">Long-lived Thread Leak Risk</a>

The TCCL is a strong reference from a `Thread` to a `ClassLoader`. If the thread outlives a plugin or application deployment, that one reference can keep the loader reachable and prevent its classes from becoming unloadable.

The dangerous shape is:

```text
long-lived thread / executor worker
        ↓ contextClassLoader
old PluginClassLoader
        ↓
plugin classes and associated loader state
```

This is especially relevant for:

- shared thread pools that survive application redeploy;
- plugin-created background threads that are never stopped;
- host threads whose TCCL was changed and never restored;
- thread factories that intentionally install a plugin loader but are not shut down with the plugin.

The immediate prevention rule is to restore temporary TCCL changes and stop plugin-owned threads/executors as part of plugin shutdown.

The leak mechanism should be stated precisely: the TCCL is harmful here because a **longer-lived root** retains the loader. Merely having references among plugin classes and their own static fields does not automatically prevent collection if the entire loader graph becomes unreachable from outside.

The final chapter returns to that reachability model in detail. Before that, the next chapter separates class loading from another operation that often uses the same loader hierarchy: loading non-class resources.
