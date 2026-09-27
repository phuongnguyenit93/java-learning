# Class Identity

Java source code encourages us to identify a type by its fully qualified name. At runtime that is not enough. Class loaders create separate namespaces, so two classes can have the same binary name and still be different, incompatible runtime types.

This rule is the reason plugin APIs must be shared deliberately and the reason many confusing `ClassCastException` messages are really loader-boundary problems.

## <a id="class-identity-rule">Binary Name + Defining Loader</a>

For runtime class identity, think in terms of this pair:

```text
(binary class name, defining ClassLoader)
```

If both components are the same, the JVM is referring to the same runtime class identity. If the binary name is the same but the defining loaders differ, the runtime types differ.

```text
(demo.plugins.HelloPlugin, loader A)
≠
(demo.plugins.HelloPlugin, loader B)
```

This is why the phrase **defining loader** matters more than merely asking which loader received the original `loadClass` call. A child may receive the request and delegate to its parent. If the parent returns the class, the parent is still the defining loader.

An **initiating loader** is a loader that causes a class to be created through loading, either by defining it directly or by delegation. The JVM can record multiple initiating loaders for the same class, while that class still has exactly one defining loader. For runtime type identity, the defining loader is the crucial part.

You can inspect the relationship directly:

```java
Class<?> type = ...;

System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
```

The Java Platform Module System adds another visibility and encapsulation layer, but it does not remove ClassLoader identity. Modules and loaders answer related, different questions: a module expresses module membership/readability/export rules; the defining loader remains part of the JVM's runtime type identity.

The same loader-sensitive idea also affects runtime packages. Package-private access is not granted merely by matching package text; classes must belong to the same runtime package boundary, which includes defining-loader identity.

### Boundary of this identity rule

The `binary name + defining ClassLoader` model above is the main rule for the **ordinary named classes/interfaces** taught in this module. Some special runtime types are not created through the same direct `ClassLoader#defineClass` path:

```text
array class
→ created automatically by the JVM when needed
→ for a reference component, getClassLoader() reflects the component type's loader
→ for a primitive component, getClassLoader() returns null via the bootstrap representation

primitive type / void
→ still have Class objects such as int.class / void.class
→ do not have an ordinary ClassLoader
```

No deeper array/primitive loading model is needed here; this note simply prevents the overgeneralization that every `Class<?>` is directly defined by a custom ClassLoader.

## <a id="same-name-different-type">Same Name, Different Runtime Type</a>

The plugin example makes the rule observable. Assume `demo.plugins.HelloPlugin` exists only in the plugin directory, so the application parent cannot load it itself. Create two independent custom loaders over the same bytes:

```java
ClassLoader parent = Plugin.class.getClassLoader();
Path source = Path.of("plugins/hello/classes");

ClassLoader loaderA = new PluginClassLoader(parent, source);
ClassLoader loaderB = new PluginClassLoader(parent, source);

Class<?> typeA = loaderA.loadClass("demo.plugins.HelloPlugin");
Class<?> typeB = loaderB.loadClass("demo.plugins.HelloPlugin");

System.out.println(typeA.getName().equals(typeB.getName())); // true
System.out.println(typeA == typeB);                         // false
System.out.println(typeA.getClassLoader() == loaderA);      // true
System.out.println(typeB.getClassLoader() == loaderB);      // true
```

The class bytes may be byte-for-byte identical. That still does not merge their identities because each loader defined its own runtime class.

This behavior is useful, not accidental. It allows two plugins, containers, or test environments to host isolated copies of implementation classes without forcing one global definition for the whole JVM.

Isolation has a cost: objects of one definition are not instances of the other definition.

```java
Object a = typeA.getDeclaredConstructor().newInstance();

System.out.println(typeA.isInstance(a)); // true
System.out.println(typeB.isInstance(a)); // false
```

When debugging a same-name type problem, print both `Class#getName()` and `Class#getClassLoader()`. The name alone can hide the cause.

## <a id="class-cast-loader-failure">Cross-loader `ClassCastException`</a>

Suppose an object was created from `typeA`:

```java
Object value = typeA.getDeclaredConstructor().newInstance();
```

This cast-like check succeeds:

```java
Object sameType = typeA.cast(value);
```

But this fails:

```java
Object otherType = typeB.cast(value); // ClassCastException
```

The JVM is not confused by equal names. It sees two different runtime types. A message that appears to say a class "cannot be cast to itself" often becomes understandable once the defining loaders are included in the diagnosis.

The useful debugging sequence is:

```java
static void describe(Class<?> type) {
    System.out.println("name   = " + type.getName());
    System.out.println("loader = " + type.getClassLoader());
    System.out.println("module = " + type.getModule());
}
```

Then inspect both the source and target types.

This issue commonly appears in plugin frameworks, servlet containers, hot-reload environments, IDE/test runners, and applications that accidentally bundle a shared API in both parent and child class paths.

The fix is usually architectural: restore one agreed ownership boundary for the shared type. Repeated casts or reflection tricks do not make distinct JVM type identities become the same type.

## <a id="loader-boundary-api">Design Shared APIs Across Loader Boundaries</a>

The clean plugin design keeps the contract in a loader visible to both sides:

```text
Application loader
  └─ demo.api.Plugin
       ↑ shared identity
       │
       ├─ PluginClassLoader A
       │    └─ HelloPlugin implements parent-owned Plugin
       │
       └─ PluginClassLoader B
            └─ MetricsPlugin implements parent-owned Plugin
```

Then each plugin implementation can be isolated while both still satisfy the host's `Plugin` interface:

```java
Object instance = pluginType.getDeclaredConstructor().newInstance();
Plugin plugin = (Plugin) instance; // works if Plugin was delegated to shared parent
```

Types that cross the boundary deserve the same care. Shared DTOs, exceptions, annotations, callback interfaces, and service contracts should come from a common loader or use a neutral transport representation that both sides understand.

Avoid making the host API depend on plugin-private concrete classes:

```java
// Fragile across isolated loader boundaries
PluginImplementationDetails details();
```

Prefer a stable shared contract:

```java
PluginDescriptor descriptor();
```

where `PluginDescriptor` is defined with the shared API.

Reflection can inspect an object across a loader boundary, but it does not erase the boundary. `Class`, method parameter types, annotations, and reflective invocation all still operate on real runtime type identities. This is where ClassLoader knowledge connects to the Reflection/Class-object topics without replacing them.

Once the shared boundary is correct, one discovery problem remains: framework code loaded by a parent may need to find providers that exist only in a child/application loader. The thread context ClassLoader is one standard bridge for that direction.
