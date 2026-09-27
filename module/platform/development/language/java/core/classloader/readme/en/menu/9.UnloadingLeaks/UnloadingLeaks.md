# Class Unloading and ClassLoader Leaks

Custom loaders make runtime namespaces disposable in principle. A plugin can be loaded, used, stopped, and replaced by a new loader with new class definitions. The hard part is reachability: the JVM cannot reclaim classes while their defining loader is still reachable from live application state.

This is why ClassLoader leaks are especially expensive. Retaining one old loader can indirectly retain the metadata, static state, resources, and objects associated with many classes from an old plugin or application deployment.

## <a id="class-unloading">Class Unloading Follows Defining-loader Reachability</a>

The JVM specification ties class unloading to the lifetime of the **defining ClassLoader**. A class defined by a user-created loader can become unloadable only when that loader itself can be reclaimed by garbage collection.

Think of a plugin generation as a graph:

```text
PluginClassLoader
    ├─ Class<HelloPlugin>
    ├─ Class<PluginConfig>
    ├─ static state associated with those classes
    └─ loader/resource bookkeeping
```

When the plugin is stopped, dropping the manager's direct `PluginClassLoader` variable is necessary but not sufficient. **Every path from GC roots to that old loader graph must disappear.**

Conversely, cycles entirely inside the plugin graph are not automatically leaks. Modern tracing garbage collectors can reclaim unreachable cycles:

```text
old loader ↔ old classes ↔ old plugin objects

no path from a GC root
→ whole graph can be collectible
```

Classes defined by the bootstrap loading mechanism do not have a collectible user ClassLoader and therefore do not follow the disposable plugin-loader lifecycle.

This also explains why class unloading is much coarser than object collection. The JVM does not normally unload one arbitrary class from a still-live custom loader while keeping that loader's other definitions as if each class had an independent lifecycle.

### Why ordinary application classes usually live for the process lifetime

In a typical Java application, the Application/System ClassLoader is long-lived—usually for roughly the lifetime of the JVM process. While that loader remains reachable, classes it defines do **not** have the disposable lifecycle of plugin classes.

```text
JVM process is alive
→ Application/System ClassLoader remains alive
→ application classes in that loader namespace usually remain loaded
→ dropping the last instance of one application class does not imply that class unloads
```

Class unloading becomes operationally important when the architecture deliberately creates a **disposable loader boundary**, such as:

```text
plugin loader
application-server deployment loader
hot-reload/devtools loader
isolated scripting/tooling loader
```

In those systems, the goal is to make an entire old loader generation unreachable so the namespace it owns can become reclaimable. That is why this chapter focuses on plugin/redeploy lifecycles rather than presenting unloading as the normal lifecycle of every application class.

## <a id="loader-retention">What Keeps a ClassLoader Reachable?</a>

The useful debugging question is: **what longer-lived object still has a path to the old loader, one of its classes, or one of its instances?**

A **GC root** is a starting point that the garbage collector treats as live when tracing reachability, such as a live thread or other long-lived runtime structures that lead to static state. If an object still has a reference path from a GC root, it is not collectible.

Common paths include:

```text
live host thread
  → contextClassLoader
  → old plugin loader

live pooled thread
  → ThreadLocal value
  → old plugin object
  → its Class
  → old plugin loader

host/global cache
  → Class<?> or ClassLoader key/value
  → old plugin loader

host registry/listener list
  → old plugin listener object
  → old plugin Class
  → old plugin loader
```

The reference does not have to point to the loader directly. Retaining an object whose class was defined by the old loader can keep that class and loader graph relevant as well.

This is where heap-dump analysis is more useful than guessing from source syntax. Search from the stale `ClassLoader` instance back to GC roots and identify the ownership boundary that should have been cleared during shutdown.

The TCCL chapter showed one such root. The same principle applies to callbacks, registries, executors, management hooks, caches, and other infrastructure whose lifetime exceeds the plugin deployment.

## <a id="static-threadlocal-leaks">Static, `ThreadLocal`, Listener, and Cache Patterns</a>

The phrase "static causes ClassLoader leaks" is too broad. A static field owned by a class inside the old plugin loader does not by itself make the graph immortal. If nothing outside the old loader graph is reachable from a GC root, the loader, its classes, and their static state can be reclaimed together.

The dangerous pattern crosses a lifetime boundary.

### Host static cache retains plugin types

```java
// Defined by a long-lived host/shared loader:
final class MetadataCache {
    static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();
}

// Later:
MetadataCache.CACHE.put(pluginType, pluginMetadata);
```

If the entry is never removed, a parent-owned static field keeps `pluginType` reachable, which can keep its defining loader reachable.

For metadata naturally associated with a `Class<?>`, `ClassValue` can sometimes provide a better lifecycle-aware design than a permanent global `Map<Class<?>, ...>`, though the correct choice depends on the cache contract.

### Long-lived thread retains plugin state through `ThreadLocal`

```java
ThreadLocal<Object> local = hostThreadLocal;
local.set(pluginObject);
```

If a pooled host thread survives plugin shutdown and the value is not removed, the thread can retain an object from the old plugin loader.

Use a bounded lifecycle:

```java
try {
    local.set(pluginObject);
    runWork();
} finally {
    local.remove();
}
```

### Host registry retains plugin listeners

```java
eventBus.addListener(pluginListener);
```

Registration establishes a reference from a longer-lived owner to the plugin. Plugin shutdown must remove that listener.

The common rule is ownership: if an object with a longer lifecycle stores plugin-defined state, there must be a matching release/unregister path.

## <a id="redeploy-leak">Redeploy and Plugin Lifecycle Leaks</a>

Redeploy works by creating a new namespace; it does not mutate old `Class<?>` objects into new definitions.

```text
generation 1
  → PluginClassLoader A
  → HelloPlugin A

redeploy
  → PluginClassLoader B
  → HelloPlugin B
```

If loader A is still reachable, generation 1 remains alive even though generation 2 works correctly. Repeating that pattern creates the classic redeploy leak: every deployment adds another loader graph.

A disciplined plugin shutdown sequence typically includes the responsibilities that the plugin actually acquired:

- stop plugin-owned threads, scheduled tasks, and executors;
- restore or clear TCCL values on surviving host threads;
- remove `ThreadLocal` values from long-lived threads where the plugin placed them;
- unregister listeners, callbacks, drivers, or management objects from longer-lived registries;
- evict host caches containing plugin classes, loaders, method handles, reflection metadata, or plugin objects;
- close closeable loader/resource containers such as `URLClassLoader` when used, so JAR/file handles are released;
- remove the host's final strong references to plugin instances and the old loader.

Closing a `URLClassLoader` does **not** unload its already-defined classes. It releases resources used for future loading. Unloading still depends on garbage-collection reachability.

The shared API should remain parent-owned while plugin-private implementations remain child-owned. That boundary makes lifecycle ownership easier to reason about: the durable parent keeps contracts; a disposable child owns one plugin generation.

## <a id="unloading-observation">Unloading Is GC-dependent, Not Deterministic</a>

There is no ordinary Java call that means "unload this Class now." Application code can remove strong references and make the loader **eligible** for collection, but the garbage collector decides when collection and class unloading actually happen.

A bounded experiment can observe reachability with a `WeakReference`. A weak reference **does not by itself keep its referent alive**, so it can observe whether a loader remains reachable without becoming the strong reference that prevents collection:

```java
static WeakReference<ClassLoader> loadThenRelease(Path pluginRoot) throws Exception {
    ClassLoader loader = new PluginClassLoader(
            Plugin.class.getClassLoader(),
            pluginRoot
    );

    Class<?> type = loader.loadClass("demo.plugins.HelloPlugin");
    Object plugin = type.getDeclaredConstructor().newInstance();
    System.out.println(plugin);

    return new WeakReference<>(loader);
}
```

After the method returns, the local strong references are gone, but a real experiment must also ensure no TCCL, cache, listener, thread, or other external structure retained the plugin graph.

Calling `System.gc()` can request garbage collection for an experiment, but it is only a hint and cannot prove that unloading must occur at that exact point. Tests should not make correctness depend on one forced-GC call.

> **Advanced runtime diagnostics:** on HotSpot, unified JVM logging can provide direct evidence during controlled runs, for example:

```text
-Xlog:class+unload=info
```

Heap dumps and JDK diagnostic tools can then help answer a different question when unloading does **not** happen: which GC-root path still retains the stale loader?

That is the final mental model for this module:

```text
class bytes
  → loader chooses/defines runtime identity
  → linking prepares the type
  → initialization runs on active use
  → objects/classes live while reachable
  → custom-loader classes become unloadable only when the defining loader graph is no longer rooted
```

ClassLoader behavior is therefore not only about finding files. It defines namespaces, type compatibility, discovery boundaries, initialization lifecycles, and the unit of disposal for dynamic Java systems.
