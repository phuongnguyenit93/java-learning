# Classpath Resource Loading

Class loaders are also used to locate **resources** such as configuration files, templates, service descriptors, and bundled data. Resource lookup is related to class lookup because it often uses the same loader hierarchy, but a resource is not a Java type: finding `plugin.properties` does not define a `Class<?>`, participate in type identity, or trigger class initialization.

This chapter uses `URL`, `InputStream`, `Path`, and try-with-resources as observation tools. If those I/O APIs are unfamiliar, keep only the model that a resource can be located and read as a stream; detailed stream/file semantics belong to the `io` module.

The main source of mistakes is path semantics. `Class.getResource` can be package-relative, while `ClassLoader.getResource` treats names from the loader's resource root and normally expects no leading `/`.

## <a id="class-resource">`Class.getResource` Path Semantics</a>

`Class.getResource(String)` uses the class as the lookup context.

For a class in package `demo.plugins`:

```java
package demo.plugins;

final class HelloPlugin {
    void inspectResources() {
        System.out.println(HelloPlugin.class.getResource("plugin.properties"));
        System.out.println(HelloPlugin.class.getResource("/shared/banner.txt"));
    }
}
```

the two names mean different things:

```text
"plugin.properties"
  → relative to the class package
  → demo/plugins/plugin.properties

"/shared/banner.txt"
  → absolute from the class/module resource root
  → shared/banner.txt
```

That package-relative behavior is useful when a class owns a nearby template or metadata file. Moving the package moves the conceptual resource namespace with the class.

> **Advanced — JPMS boundary:** the lookup still depends on the class's runtime loading/module context. In named modules, resource encapsulation rules can restrict access to non-class resources. A path that would have worked on a flat class path should not be assumed to bypass JPMS module boundaries merely because it is requested through `Class.getResource`.

The result is a `URL` or `null`:

```java
URL resource = HelloPlugin.class.getResource("plugin.properties");

if (resource == null) {
    throw new IllegalStateException("plugin.properties is missing");
}
```

Treat `null` as a normal "not found / not accessible through this lookup" result rather than assuming every build layout places the requested file in the runtime resource path.

## <a id="loader-resource">`ClassLoader.getResource` Path Semantics</a>

`ClassLoader.getResource(String)` uses a loader-root name. The conventional form does **not** begin with `/`:

```java
ClassLoader loader = HelloPlugin.class.getClassLoader();

URL config = loader.getResource("demo/plugins/plugin.properties");
URL banner = loader.getResource("shared/banner.txt");
```

Compare the equivalent intent:

```java
HelloPlugin.class.getResource("plugin.properties");
loader.getResource("demo/plugins/plugin.properties");
```

Both may find the same physical resource, but they express the path from different starting points.

A common bug is carrying the leading `/` rule from `Class.getResource` into `ClassLoader.getResource`:

```java
// Usually wrong for ClassLoader lookup:
loader.getResource("/shared/banner.txt");

// Root-style ClassLoader name:
loader.getResource("shared/banner.txt");
```

Default ClassLoader resource lookup also follows loader delegation behavior: parent-visible resources may be found before resources owned by the child. A custom ClassLoader can alter resource lookup just as it can alter class lookup, so plugin isolation should define both class and resource policy deliberately.

> **Advanced — JPMS boundary:** named modules add resource-encapsulation rules on top of these path rules. The ClassLoader API is therefore not a universal escape hatch around JPMS visibility.

## <a id="resource-enumeration">Finding Multiple Resources</a>

Sometimes the correct operation is not "give me the first matching resource" but "show me every provider/configuration fragment with this name."

`ClassLoader.getResources` returns an `Enumeration<URL>`:

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Enumeration<URL> resources = loader.getResources("META-INF/services/demo.api.Plugin");

while (resources.hasMoreElements()) {
    System.out.println(resources.nextElement());
}
```

Java also exposes a stream form:

```java
try (Stream<URL> resources = loader.resources("META-INF/services/demo.api.Plugin")) {
    resources.forEach(System.out::println);
}
```

Enumeration is valuable in plugin/framework scenarios because several JARs can contribute resources with the same logical name. `ServiceLoader`, for example, is built around the idea that provider metadata may come from multiple locations visible to a loader.

With the default ClassLoader implementation, parent resources participate in the search before resources found by the current loader. Custom loaders can define different lookup behavior, so application semantics should not depend on an undocumented global ordering between unrelated deployment artifacts.

When duplicates matter, design an explicit merge rule rather than silently accepting whichever resource happens to appear first.

## <a id="resource-stream-lifecycle">Resource Streams Have a Lifecycle</a>

For content, `getResourceAsStream` is often simpler than converting a URL yourself:

```java
ClassLoader loader = HelloPlugin.class.getClassLoader();

try (InputStream in = loader.getResourceAsStream("demo/plugins/plugin.properties")) {
    if (in == null) {
        throw new IllegalStateException("plugin.properties is missing");
    }

    Properties properties = new Properties();
    properties.load(in);
}
```

The returned stream may be backed by a file, a JAR entry, a runtime image, a network-capable custom loader, or another source. The caller should close it with try-with-resources.

For text resources, also choose the character encoding explicitly when the file format does not define one through its own API:

```java
try (InputStream in = loader.getResourceAsStream("shared/banner.txt")) {
    if (in == null) {
        throw new IllegalStateException("banner missing");
    }

    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    System.out.println(text);
}
```

Do not use `InputStream.available()` as the resource length. It describes bytes readable without blocking, not the total content size.

This lifecycle is separate from ClassLoader unloading. Closing a resource stream releases that stream's underlying resource; it does not unload classes. Conversely, keeping archive/resource handles open unnecessarily can make plugin replacement operationally difficult even when type reachability is otherwise correct.

## <a id="classpath-vs-filesystem">A Classpath Resource Is Not Necessarily a File</a>

During development, a resource may happen to live in a directory such as:

```text
build/resources/main/shared/banner.txt
```

That can make code like this appear reasonable:

```java
URL url = loader.getResource("shared/banner.txt");
Path path = Path.of(url.toURI());
```

But after packaging, the URL might identify a JAR entry rather than a normal filesystem file. JDK runtime resources may also use the `jrt:` scheme. A custom loader can expose still other URL protocols.

```text
development directory → file:...
packaged dependency    → jar:file:...!/shared/banner.txt
runtime image          → jrt:...
```

Therefore, if the operation only needs to **read resource content**, prefer `getResourceAsStream` and stay independent of the physical storage mechanism.

Use filesystem APIs only when the application contract truly requires a filesystem path. If an external library requires a `Path`, the application may need to copy the classpath resource to a temporary file or explicitly mount/open the relevant archive filesystem; that is a deployment decision, not a generic property of classpath resources.

## <a id="resource-vs-class-loading">Resource Lookup Is Different from Class Loading and Definition</a>

For the plugin system, keep the distinction clear:

```text
ClassLoader.loadClass("demo.plugins.HelloPlugin")
  → defines or returns a runtime type

ClassLoader.getResource("demo/plugins/plugin.properties")
  → locates non-class data
```

Both use a loader namespace, but only the first creates/returns class identity. The next chapter returns to class lifecycle and examines what happens when initialization finally runs.
