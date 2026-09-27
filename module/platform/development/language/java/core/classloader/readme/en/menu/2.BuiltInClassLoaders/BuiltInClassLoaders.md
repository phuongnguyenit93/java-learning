# Bootstrap, Platform and Application Class Loaders

A normal Java 21 application already has a loader hierarchy before application code creates any custom loader. Understanding these built-in loaders gives a concrete answer to "who loaded this class?" and prepares the parent-delegation model used in the next chapter.

Why not use one global loader for everything? Separate ownership boundaries keep **core JDK classes**, **platform classes**, and **application classes** in predictable layers. That gives foundational types stable ownership and lets later custom loaders choose what to share through a parent and what to isolate in a child namespace.

## <a id="bootstrap-loader">Bootstrap Loader</a>

The JVM must be able to load fundamental runtime classes before ordinary Java application code is available. That bootstrap problem is handled by the **bootstrap class loader**, the root loading mechanism provided by the JVM implementation.

It loads core platform classes, including fundamental classes from modules such as `java.base`. Unlike ordinary application loaders, it is not exposed as a normal `ClassLoader` object through `Class.getClassLoader()`.

That is why this prints `null`:

```java
System.out.println(String.class.getClassLoader());
System.out.println(Object.class.getClassLoader());
```

`null` here does **not** mean "nobody loaded this class." In the `Class` API it represents that the class was defined by the bootstrap loading mechanism.

This special representation matters when inspecting loader chains. Code such as this must treat `null` as the bootstrap boundary instead of dereferencing it:

```java
ClassLoader loader = String.class.getClassLoader();

if (loader == null) {
    System.out.println("bootstrap-defined class");
}
```

For the plugin running example, shared application interfaces should never attempt to redefine core JDK types. The bootstrap loader sits above every ordinary loader boundary and establishes one consistent runtime identity for those foundational classes.

## <a id="platform-loader">Platform ClassLoader</a>

Java also exposes a **platform ClassLoader**:

```java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
System.out.println(platform);
System.out.println(platform.getParent()); // typically null: bootstrap boundary
```

Its role is to load platform classes that are not defined by the bootstrap loader. Since Java 9, this model is tied to the modular runtime rather than the older Java 8 "extension class loader" model.

The platform loader is useful as a boundary between core/runtime classes and ordinary application classes. It also matters when creating a custom loader: choosing a parent determines which types the custom loader naturally shares with the rest of the process.

For example, an isolated tool might deliberately use the platform loader as its parent when it should see Java platform APIs but should not automatically inherit all classes visible to the main application loader:

```java
ClassLoader parent = ClassLoader.getPlatformClassLoader();
ClassLoader isolated = new PluginClassLoader(parent, pluginDirectory);
```

That is an architectural decision, not merely a constructor parameter. A narrower parent can increase isolation, but it also means application-owned API classes are no longer automatically shared unless the loader design provides another bridge.

## <a id="application-loader">Application/System ClassLoader</a>

The loader most application code encounters is the **application ClassLoader**, which is normally also returned as the system ClassLoader:

```java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader current = BuiltInClassLoaders.class.getClassLoader();

System.out.println(system);
System.out.println(current);
System.out.println(system == current);
```

In a typical command-line application, this loader defines classes and dependencies made visible to the application through the configured class path and the runtime's module-loading arrangements.

The phrase **system ClassLoader** comes from the API (`ClassLoader.getSystemClassLoader()`). In common JDK setups it is the application loader, but code should avoid assuming a specific implementation class name because the JVM can use a custom system loader configuration.

In the plugin example, the host application's `demo.api.Plugin` interface would normally be defined by this application loader:

```java
System.out.println(Plugin.class.getClassLoader());
```

If plugin implementations are defined by child loaders but delegate `demo.api.Plugin` to this parent, both host and plugin agree on the same interface identity. That shared API boundary becomes essential when the Class Identity chapter explains cross-loader casts.

## <a id="loader-chain">Built-in Parent Chain</a>

The ordinary parent relationship can be pictured as:

```text
Application/System ClassLoader
            ↓ parent
Platform ClassLoader
            ↓ parent
Bootstrap loading mechanism
            ↓ represented by
           null
```

You can inspect the object-visible part of that chain:

```java
static void printChain(ClassLoader loader) {
    for (ClassLoader current = loader;
         current != null;
         current = current.getParent()) {
        System.out.println(current);
    }
    System.out.println("<bootstrap>");
}

printChain(ClassLoader.getSystemClassLoader());
```

And you can compare ownership of representative classes:

```java
System.out.println("project  : " + BuiltInClassLoaders.class.getClassLoader());
System.out.println("platform : " + java.sql.Driver.class.getClassLoader());
System.out.println("bootstrap: " + String.class.getClassLoader());
```

Exact ownership of individual JDK classes can vary with the modular runtime, so the useful mental model is the **responsibility boundary**, not memorizing one example class for each loader.

The parent chain is also not the same thing as Java inheritance. A child ClassLoader does not inherit classes as Java subclasses inherit members. Instead, the chain provides a lookup relationship. The default lookup strategy asks the parent first, which is the subject of the next chapter.
