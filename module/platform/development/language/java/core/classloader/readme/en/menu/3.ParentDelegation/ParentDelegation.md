# Parent Delegation Model

Once several loaders exist, a new problem appears: when a child loader is asked for `demo.api.Plugin`, should it define its own copy or reuse the one its parent already knows? Java's default answer is **parent-first delegation**. That policy keeps shared types consistent while still allowing a child loader to define classes that its parent cannot find.

## <a id="parent-delegation">Parent-first Delegation</a>

The default `ClassLoader.loadClass` workflow is conceptually:

```text
loadClass(name)
    ↓
already loaded by this loader?
    ├─ yes → return it
    └─ no
        ↓
ask parent (or bootstrap when parent is null)
        ├─ found → return parent's Class
        └─ not found
            ↓
call this loader's findClass(name)
            ↓
optionally resolve before returning
```

The real `ClassLoader` implementation also coordinates concurrent loading and resolution, but this order is the core mental model.

The two-argument `loadClass(name, resolve)` overload connects directly to the Lifecycle chapter. When `resolve` is `true`, the loader invokes `resolveClass(...)` before returning the class:

```text
loadClass(name, false)
→ load/find the class
→ no request to resolve it immediately through this call

loadClass(name, true)
→ load/find the class
→ resolveClass(class)

resolveClass
≠ initialize the class
```

Application code rarely needs to call `resolveClass` directly. The important semantic point is that **resolve belongs to linking/resolution, not static initialization**.

At this chapter, keep the implementation at the policy level:

```text
loadClass(name)
→ check whether the class is already loaded
→ ask the parent first
→ only if the parent cannot provide it, try the current loader's own source
```

The next `CustomClassLoader` chapter owns the concrete `findClass`, `defineClass`, and subclass code. This keeps the learning order clean: first understand **why delegation exists**, then learn **how to implement a loader**.

Suppose the application loader already defines the shared plugin API:

```text
Application loader
  └─ demo.api.Plugin

PluginClassLoader
  └─ demo.plugins.HelloPlugin
```

When `PluginClassLoader` needs `demo.api.Plugin`, default delegation asks the application parent first. The child therefore reuses the host's `Plugin` class instead of defining a private copy. When the child requests `demo.plugins.HelloPlugin`, the parent cannot find it, so the child gets a chance to define it through `findClass`.

For most custom loaders, this is why overriding `findClass` is safer than replacing `loadClass`: the inherited `loadClass` keeps the established delegation protocol and invokes your byte-source logic only after parent lookup fails.

## <a id="delegation-purpose">Why Delegate to the Parent?</a>

Parent-first lookup solves a consistency problem. If every loader freely redefined any class name it received, even widely shared API types could fragment into incompatible runtime identities.

Consider the plugin interface again. The host wants this cast to work:

```java
Object instance = pluginType.getDeclaredConstructor().newInstance();
Plugin plugin = (Plugin) instance;
```

That succeeds only if the plugin implementation implements the **same runtime `Plugin` type** that the host uses. Delegating the shared API to a common parent is a natural way to guarantee that identity.

Parent-first loading also protects consistency for Java platform classes. Application code should not be able to place its own `java.lang.String` earlier in a plugin directory and silently replace the runtime's foundational `String` class.

The benefits are therefore connected:

- **type consistency**: shared APIs resolve to one parent-owned runtime class;
- **predictable dependency sharing**: children naturally reuse dependencies visible to the parent;
- **platform integrity**: core Java types remain owned by the runtime's trusted loading boundary;
- **less duplicate metadata**: a child does not define another copy of every class its parent already provides.

Delegation is a lookup policy. It is not a guarantee that all class paths are globally unique, and it does not prevent a different, unrelated loader from defining the same binary name in another isolated namespace. That possibility is exactly why class identity includes the defining loader.

## <a id="child-first-boundary">Child-first Strategies and Their Boundary</a>

Parent-first is a strong default, but some containers, plugin systems, and tooling need the child to prefer its own dependency version. For example, plugin A may require `lib-x` version 1 while the host uses version 2.

A child-first loader can conceptually try:

```text
already loaded?
    ↓
try child source first
    ↓ if absent
delegate to parent
```

This can provide dependency isolation, but it changes the failure modes. If the child also loads its own copy of a shared API, the host and plugin may agree on the binary name while disagreeing on runtime type identity.

That means child-first behavior should usually be selective rather than global. A practical design often separates packages into categories:

```text
parent-first
  → Java platform packages
  → host/plugin API packages

child-first when intentionally isolated
  → plugin implementation packages
  → plugin-private dependency packages
```

Overriding `loadClass` also takes responsibility for the concurrency and delegation rules that the base implementation normally supplies. A custom strategy must still check already-loaded classes and must avoid defining the same class twice under racing requests.

The trade-off is therefore clear: child-first loading can isolate conflicting implementation dependencies, but the more names it isolates, the more carefully the application must design the shared type boundary.

## <a id="protected-packages-boundary">Protected Core-package Boundary</a>

Custom loading is powerful, but it does not mean application code may redefine every name. In particular, `ClassLoader.defineClass` rejects attempts by ordinary loaders to define classes whose names begin with `java.`.

This prevents code such as a custom loader trying to define:

```text
java.lang.String
java.util.List
java.time.Instant
```

as application-owned replacements for core Java classes.

The boundary matters for both correctness and security. Many JVM and library assumptions depend on platform types having well-known ownership and semantics. Allowing arbitrary child loaders to impersonate those classes would break far more than ordinary dependency isolation.

Application packages do not receive that same blanket protection merely because they are important. If `demo.api.Plugin` must remain shared, the loader architecture must enforce that through parent delegation or an equivalent explicit rule.

This leads directly to custom loader design: the next chapter shows where custom bytes enter the process, why `findClass` is the usual extension point, and what responsibilities come with calling `defineClass`.
