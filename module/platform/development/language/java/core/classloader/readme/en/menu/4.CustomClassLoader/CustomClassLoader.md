# Custom Class Loaders

Built-in loaders are enough when every class comes from the normal runtime, module path, or application class path. A custom ClassLoader becomes useful when the application needs a different **source of class bytes** or a deliberate **namespace boundary**: plugin directories, generated classes, container isolation, scripting engines, instrumentation tools, or similar runtime systems.

The important design question is not "how do I read a `.class` file?" It is "which classes should this loader define, which classes should it share with its parent, and how will that ownership affect type identity?"

## <a id="classloader-contract">The `loadClass` / `findClass` Contract</a>

`ClassLoader` separates the public loading protocol from the usual subclass extension point:

```text
loadClass(name)
  → checks whether the class was already loaded
  → delegates according to the loader policy
  → calls findClass(name) when this loader should define the class

findClass(name)
  → subclass-specific byte lookup
  → defineClass(...)
  → return the resulting Class<?>
```

For a normal parent-first custom loader, overriding `findClass` is usually enough. The inherited `loadClass` preserves duplicate-load checks, parent delegation, and synchronization around loading.

```java
final class PluginClassLoader extends ClassLoader {
    private final Path root;

    PluginClassLoader(ClassLoader parent, Path root) {
        super(parent);
        this.root = root;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Custom byte lookup belongs here.
        return definePluginClass(name);
    }

    private Class<?> definePluginClass(String name) throws ClassNotFoundException {
        // implemented in the next section
        throw new ClassNotFoundException(name);
    }
}
```

This relationship matters because `loadClass` answers the full lookup question, while `findClass` answers only the narrower question: **"If this loader is responsible for defining this name, where are its bytes?"**

Override `loadClass` only when the lookup policy itself must change, such as a carefully scoped child-first strategy. Once you replace it, you also take responsibility for preserving already-loaded checks, thread-safety, protected package boundaries, and the intended parent/child sharing rules.

## <a id="define-class">Defining Runtime Classes from Bytes</a>

Reading bytes does not create a Java runtime type. The transition happens through `defineClass`.

Conceptually:

```java
byte[] bytes = ...;
Class<?> type = defineClass(binaryName, bytes, 0, bytes.length);
```

`defineClass` asks the JVM to turn a valid class-file representation into a `Class<?>` owned by **this defining loader**. The JVM verifies structural constraints and associates the resulting class with that loader's namespace.

Several consequences follow immediately:

- the binary name in the request must agree with the class-file identity;
- one loader cannot successfully define the same binary name twice;
- defining a class does not by itself mean its static initialization has run;
- the defining loader becomes part of the type's runtime identity;
- ordinary custom loaders cannot define classes in protected `java.*` packages.

A second attempt to define a name already defined by the same loader results in a linkage failure. This is why a custom loader should participate in the normal `loadClass` protocol instead of calling `defineClass` blindly on every request.

> **Advanced — safe to skip on the first pass:** `defineClass` also has overloads that accept a `ProtectionDomain`. Signed JARs and package certificate/sealing rules introduce additional production concerns. This module only needs the boundary to be visible; it does not teach the full protection-domain or signing model.

## <a id="custom-source">Loading from a Custom Source</a>

The running plugin system can load implementation classes from a directory that is not part of the application's normal class path:

```java
final class PluginClassLoader extends ClassLoader {
    private final Path root;

    PluginClassLoader(ClassLoader parent, Path root) {
        super(parent);
        this.root = root;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Path classFile = root.resolve(name.replace('.', '/') + ".class");

        try {
            byte[] bytes = Files.readAllBytes(classFile);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException ex) {
            throw new ClassNotFoundException(name, ex);
        }
    }
}
```

The host can then create a loader whose parent owns the shared plugin API:

```java
Path pluginRoot = Path.of("plugins/hello/classes");

ClassLoader loader = new PluginClassLoader(
        Plugin.class.getClassLoader(),
        pluginRoot
);

Class<?> type = loader.loadClass("demo.plugins.HelloPlugin");
Object instance = type.getDeclaredConstructor().newInstance();
Plugin plugin = (Plugin) instance;

System.out.println(plugin.name());
```

The example focuses on the loading boundary, so a real implementation would add source validation, lifecycle management, and perhaps JAR support. If the requirement is simply "load classes and resources from these JAR/URL locations," `URLClassLoader` may already provide the needed mechanism; a new ClassLoader is justified when the source, isolation rule, or lifecycle really differs.

The key flow is:

```text
binary name
  → custom source path / archive / generated bytes
  → byte[]
  → defineClass
  → Class<?> owned by this loader
```

The source can change; the identity rule does not.

## <a id="custom-loader-safety">Safety, Concurrency, and Package Boundaries</a>

A ClassLoader is infrastructure shared by potentially many class-loading requests, so correctness includes more than returning the right bytes.

The base `loadClass` implementation coordinates loading through a class-loading lock. This prevents racing requests from defining the same name twice. Loaders that replace `loadClass` must preserve equivalent synchronization.

> **Advanced — safe to skip on the first pass:** loaders can become **parallel-capable**, but that requires deliberately following the `ClassLoader.registerAsParallelCapable()` contract through the loader hierarchy. It is not a default requirement for a basic custom loader.

Package boundaries also follow loader identity. At runtime, package membership used for access control is effectively tied to both the package name and the defining loader. Two classes named:

```text
demo.internal.First   — defined by loader A
demo.internal.Second  — defined by loader B
```

do not gain package-private access to each other merely because both names begin with `demo.internal`. They belong to different runtime package identities.

That can surprise plugin systems that split cooperating implementation classes across different loaders. If two classes need package-private cooperation, they should normally be defined within the same intended loader/package boundary.

Other practical rules follow from the same ownership model:

- keep shared API types in a parent visible to all participating plugins;
- isolate implementation dependencies only when there is a concrete reason;
- do not let arbitrary child-first rules shadow host contracts accidentally;
- preserve package/signing constraints when loading signed artifacts;
- release the custom loader and everything externally retaining it when the plugin lifecycle ends.

The last point becomes critical later: a custom loader creates a namespace, and retaining that loader can retain the entire namespace. Before discussing leaks, the next chapter first makes the identity consequences precise.
