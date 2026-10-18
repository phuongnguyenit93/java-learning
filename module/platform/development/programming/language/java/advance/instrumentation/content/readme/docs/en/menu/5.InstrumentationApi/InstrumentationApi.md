<a id="back-to-top"></a>

# Instrumentation API

## Menu
- [Role of the Instrumentation Service Object](#instrumentation-service)
- [Registering and Managing Transformers](#transformer-registration)
- [Inspecting Loaded Classes](#loaded-class-discovery)
- [Instrumentation Capability Model](#capability-model)
- [Per-Class Modifiability](#class-modifiability)
- [Class-Loader Search Paths and redefineModule](#classloader-module-hooks)

## <a id="instrumentation-service">Role of the Instrumentation Service Object</a>

<details>
<summary>Click for details</summary>

Instrumentation is the JVM-provided facade through which an agent works with class definitions. Rather than calling low-level native tooling hooks directly, an agent gets a Java control surface for:

- registering/removing ClassFileTransformer instances;
- inspecting loaded classes;
- checking class/module modifiability;
- querying redefine/retransform/native-prefix support;
- requesting retransform/redefine;
- extending class-loader search;
- expanding module relationships;
- obtaining an implementation-specific object-size estimate.

Instrumentation is not a global utility singleton. The agent receives an instance through premain/agentmain and should treat it as the capability handle for the current JVM.

~~~java
public static void premain(String args, Instrumentation inst) {
    System.out.println("Loaded classes: " + inst.getAllLoadedClasses().length);
}
~~~

This API is the control surface; bytecode engineering remains the responsibility of the transformer/tooling layer.

One supporting method is easy to overinterpret: getObjectSize(object) returns an **implementation-specific approximation** of storage consumed by that object. It is not a deep size for the entire object graph, should not be used as an absolute cross-JVM comparison, and the estimate may even change during one JVM invocation.

</details>

- [Back to top](#back-to-top)

---

## <a id="transformer-registration">Registering and Managing Transformers</a>

<details>
<summary>Click for details</summary>

addTransformer() registers a transformer for future class definitions. The two-argument overload declares whether that transformer participates in retransformation:

~~~java
inst.addTransformer(transformer, false);
inst.addTransformer(retransformableTransformer, true);
~~~

Registration with canRetransform=true can fail when retransformation is unsupported.

The same transformer instance can technically be added more than once, but the API strongly discourages it because ordering and removal become harder to reason about. Prefer distinct instances when distinct registrations are required.

removeTransformer() removes the most recently added matching instance. It does not restore classes already transformed, and because class loading is multi-threaded, callbacks can still arrive after removal returns.

Use an explicit lifecycle:

~~~text
create
→ register
→ use
→ disable behavior if needed
→ remove registration
→ optionally retransform/redefine affected classes for restoration
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="loaded-class-discovery">Inspecting Loaded Classes</a>

<details>
<summary>Click for details</summary>

Two discovery APIs are easy to confuse:

- getAllLoadedClasses(): all classes currently loaded by the JVM;
- getInitiatedClasses(loader): classes for which that loader is an initiating loader according to the API's lookup/linkage semantics.

getAllLoadedClasses() includes hidden classes/interfaces and array classes. In contrast, getInitiatedClasses(loader) excludes hidden classes/interfaces (and arrays whose element type is hidden) because those types are not discoverable through ClassLoader::loadClass/Class::forName.

A dynamic agent often scans getAllLoadedClasses() to find targets that are already present:

~~~java
for (Class<?> type : inst.getAllLoadedClasses()) {
    if (type.getName().equals("com.example.OrderService")
            && inst.isModifiableClass(type)) {
        inst.retransformClasses(type);
    }
}
~~~

Do not scan and retransform everything. Targeting should filter by package/class/loader/module to avoid instrumenting agent helpers, touching unintended JDK classes, creating dependency cycles, or adding unnecessary runtime cost.

Discovery exposes runtime inventory; it does not replace a ClassLoader mental model.

</details>

- [Back to top](#back-to-top)

---

## <a id="capability-model">Instrumentation Capability Model</a>

<details>
<summary>Click for details</summary>

A JVM is not required to support every Instrumentation capability. Important queries include:

~~~java
inst.isRedefineClassesSupported();
inst.isRetransformClassesSupported();
inst.isNativeMethodPrefixSupported();
~~~

Redefine/retransform support depends on both:

~~~text
agent manifest requests the capability
        +
JVM configuration supports it
        ↓
API reports true
~~~

These capability answers are stable for a JVM instance, but code should still query the API instead of hard-coding assumptions from one vendor/version.

Can-Set-Native-Method-Prefix and setNativeMethodPrefix() address a specialized use case: wrapping native methods by retrying native resolution with a prefix. It belongs to the Instrumentation surface, but deep native interop remains outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="class-modifiability">Per-Class Modifiability</a>

<details>
<summary>Click for details</summary>

JVM support for retransform/redefine **does not make every Class modifiable**. isModifiableClass(type) answers the per-class question.

~~~java
if (!inst.isRetransformClassesSupported()) {
    return;
}
if (!inst.isModifiableClass(OrderService.class)) {
    return;
}
inst.retransformClasses(OrderService.class);
~~~

Primitive classes and array classes are never modifiable. A JVM implementation may also make additional classes unmodifiable.

Keep the two levels separate:

~~~text
JVM-level capability
→ "does this runtime support retransform/redefine?"

class-level modifiability
→ "may this specific class be changed?"
~~~

Skipping the second check commonly ends in UnmodifiableClassException.

</details>

- [Back to top](#back-to-top)

---

## <a id="classloader-module-hooks">Class-Loader Search Paths and redefineModule</a>

<details>
<summary>Click for details</summary>

Instrumentation exposes two families of visibility hooks.

**Class-loader search**

- appendToBootstrapClassLoaderSearch(JarFile)
- appendToSystemClassLoaderSearch(JarFile)

These can make agent helpers available through the appropriate loader search path. Bootstrap helpers have especially strict visibility constraints: code defined by the bootstrap loader must link only to classes visible to that loader.

**Module adjustment**

redefineModule() can **expand** an existing module by adding reads, exports, opens, uses, and provides. It is not a general-purpose API for arbitrarily rebuilding or shrinking the module graph.

An agent can check isModifiableModule(module) first. Unnamed modules report as modifiable, but redefining an unnamed module is a no-op; named modules are where expanding reads/exports/opens is typically meaningful.

The Java agent package contract also supports a common helper-call case: the JVM arranges for the module of a transformed class to read the unnamed modules of the bootstrap and system class loaders. That support does not automatically solve every exports/opens or helper-dependency problem, so the agent must still reason about the concrete loader and module boundary.

Mental model:

~~~text
transformed class needs a helper
        ↓
helper must be visible across loader/module boundaries
        ↓
search-path hook and/or redefineModule
        ↓
expand only what the instrumentation requires
~~~

This section assumes basic JPMS knowledge; deep readability/exports/opens semantics belong to the Java 9 Module System curriculum.

</details>

- [Back to top](#back-to-top)
