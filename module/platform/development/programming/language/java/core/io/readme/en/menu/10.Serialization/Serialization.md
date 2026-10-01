# Java Object Serialization Boundary

So far we have mostly moved bytes or text. Java also has a mechanism for turning the state of an **object graph** into a byte stream and reconstructing that graph later. An object graph is a root object plus the objects reachable through its fields/references; it can contain shared references and cycles. Turning that graph into bytes and reconstructing it later is commonly called **Java native serialization**.

The name can tempt a beginner to treat it as the default way to store objects in a database/cache or send them over a network. That is a poor default. Native serialization is tightly coupled to Java classes, version compatibility, the **classpath** — the set of classes/resources the JVM can find and load at runtime — and **deserialization**, meaning reading serialized bytes to reconstruct the object graph. It is therefore a boundary-heavy mechanism rather than a general persistence or network-format recommendation.

## <a id="java-serialization-model">Java Serialization Model</a>

A class participating in native serialization commonly implements the marker interface `Serializable`:

~~~java
final class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String title;
    private final String body;

    Note(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
~~~

`Serializable` declares no methods. It is a **marker interface**: implementing it tells the serialization mechanism that instances of the class may participate.

Writing an object:

~~~java
Path file = Files.createTempFile("note-", ".ser");

try (
        OutputStream out = Files.newOutputStream(file);
        ObjectOutputStream objectOut = new ObjectOutputStream(out)
) {
    objectOut.writeObject(
            new Note("I/O", "Java serialization demo")
    );
}
~~~

Reading it back:

~~~java
try (
        InputStream in = Files.newInputStream(file);
        ObjectInputStream objectIn = new ObjectInputStream(in)
) {
    Note note = (Note) objectIn.readObject();
}
~~~

The mental model is not:

~~~text
object
→ JSON-like field map
~~~

It is closer to:

~~~text
Java object graph
→ ObjectOutputStream writes Java serialization protocol
→ byte stream contains class/type descriptors + state + graph references
→ ObjectInputStream reads the protocol
→ JVM reconstructs Java objects
~~~

The resulting bytes should not be treated as a human-readable, language-independent, or automatically long-term-stable contract.

## <a id="serializable-graph">The Serializable Object Graph</a>

Serialization does not stop at the first object. It traverses objects reachable through fields that are part of the serialized form.

For example:

~~~java
final class Author implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
}

final class Article implements Serializable {
    private static final long serialVersionUID = 1L;
    String title;
    Author author;
}
~~~

Serializing an `Article` also includes the `Author` referenced by its `author` field. If a reachable object is not serializable and that field is not excluded from serialization, runtime can throw `NotSerializableException`.

The object stream also preserves identity relationships in the graph. If two fields refer to the same object, the protocol can record a reference to an object already written instead of reconstructing two independent objects. Cycles can therefore be represented as well.

~~~text
Article A ─┐
           ├─→ the same Author instance
Article B ─┘
~~~

After deserialization, shared-reference relationships are restored according to the serialization protocol.

For an **ordinary serializable class**, deserialization does not invoke that class's ordinary constructor as if application code had called `new`. The nearest non-serializable superclass participates through its accessible no-argument constructor according to serialization rules. This is another sign that deserialization is more than “parse fields and call a constructor.”

Serializable **record classes are a defined exception to that ordinary-class rule**: during deserialization Java reconstructs the record component values and invokes the record's canonical constructor. Record serialization has additional special rules, so do not automatically apply every ordinary-`Serializable` constructor rule to records.

The ability to reconstruct a whole graph through this special lifecycle is one reason native serialization must be treated as a strong boundary.

## <a id="serialversionuid">serialVersionUID and Version Compatibility</a>

Serialized bytes can outlive the exact class version that created them. Java therefore needs to decide whether the class available while reading is compatible with the class represented in the stream. `serialVersionUID` is part of that compatibility check:

~~~java
final class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    String title;
    String body;
}
~~~

For an **ordinary serializable class**, if `serialVersionUID` is omitted, the runtime can compute a UID from class details. Class changes can then change the computed value in ways that make older data unreadable.

Classes that intentionally use native serialization therefore commonly declare the value explicitly:

~~~java
private static final long serialVersionUID = 1L;
~~~

Its limit is crucial: **keeping the same UID does not make every class change safe or semantically compatible**. It is only one piece of the serialization runtime's compatibility rules. Changes to fields, hierarchy, invariants, or custom serialization logic can still make old data inappropriate even when runtime accepts it.

Conversely, deliberately changing the UID can declare old serialized forms incompatible; reading them can result in `InvalidClassException`.

Record classes again have a special boundary: a serializable record has a default `serialVersionUID` of `0L` unless it declares one explicitly, and the requirement for matching stream/local `serialVersionUID` values is waived for records. That special rule is another reason to treat Java Object Serialization as a mechanism with type-specific contracts rather than one universal recipe.

Because serialized form is tied to class evolution, using native serialization for data expected to live for years often creates more migration and compatibility burden than a format designed around an explicit schema or contract.

## <a id="transient-field">transient Fields</a>

Not every instance field should become part of the serialized form. The `transient` keyword excludes a field from default serialization:

~~~java
final class SessionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String temporaryToken;
}
~~~

After default deserialization, `temporaryToken` has the default value for its type, which is `null` here.

`transient` is useful for data that:

- can be recomputed from other state;
- only makes sense in the current runtime;
- refers to an object that should not/cannot be serialized;
- should not be part of the serialized form.

`static` fields are class state rather than per-instance state, so they are also not part of the default serialized object state.

Do not treat `transient` as a complete security feature. It only controls participation in default serialized form. If sensitive data is copied elsewhere, explicitly written by custom serialization, or exposed through another artifact, the keyword does not solve that design problem.

Java also supports custom `writeObject/readObject` and related hooks. They provide more control but enlarge the compatibility and security surface. Add them only when there is a deliberate serialized-form contract that requires them.

## <a id="serialization-security-risk">Security Risk and Usage Boundary</a>

Deserializing native Java data is not the same as parsing a passive data structure. While reconstructing the graph, the serialization runtime can activate class-specific behavior such as custom `readObject`, `readResolve`, and validation hooks for classes present on the classpath.

Therefore, **do not deserialize native Java serialization from untrusted input**. Arbitrary client bytes, untrusted uploads, or messages crossing an external trust boundary should not be passed directly to `ObjectInputStream.readObject()`.

In deserialization attacks, a **gadget** is a class/method already available on the classpath whose special behavior can be abused while objects are being reconstructed. A **gadget chain** links several such gadgets so crafted serialized data can trigger a dangerous sequence of behavior. This is what the phrase “gadget/deserialization attack” refers to in discussions of native serialization risk.

~~~text
untrusted bytes
    ↓
ObjectInputStream.readObject()
    ↓
object graph reconstruction + class-specific hooks
    ↓
risk
~~~

`ObjectInputFilter` can constrain classes, graph depth, reference counts, or size in systems that must support serialization:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "com.example.Note;java.base/*;!*"
        );

objectIn.setObjectInputFilter(filter);
~~~

The filter string above allows `com.example.Note`, allows classes from the `java.base` module, and then uses `!*` to reject other classes. Install the filter on the `ObjectInputStream` **before the first object read**, such as `readObject()` or `readUnshared()`, so the restriction applies from the moment graph reconstruction begins.

Filtering is **defense in depth**. It does not turn untrusted native deserialization into the recommended default.

Native serialization also carries non-security boundaries:

| Boundary | Consequence |
| --- | --- |
| Coupled to Java classes/classpath | Poor fit for cross-language contracts |
| Coupled to class evolution | Requires compatibility/version management |
| Binary protocol is opaque | Harder to inspect/debug than text/schema formats |
| Reconstructs an object graph | Larger runtime behavior surface than simple DTO parsing |
| History of gadget-chain/deserialization attacks | Untrusted input is a serious risk |

For network APIs, message contracts, or long-lived persistence, formats with an explicit contract such as JSON, Protocol Buffers, or another schema/format suited to the system are usually better defaults, combined with deliberate DTO/versioning design. The exact format depends on the problem, but **Java native serialization is not a general recommendation for persistence or interchange**.

When a legacy codebase already uses serialization, understanding the mechanism is still necessary to maintain it and narrow its boundary. The final chapter places serialization beside streams, readers/writers, `Files`, and channels so we can choose the simplest correct I/O abstraction for each problem.
