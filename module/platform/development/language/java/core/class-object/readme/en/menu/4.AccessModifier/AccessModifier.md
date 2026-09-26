# Access Modifiers

Access modifiers define **visibility boundaries**. They support encapsulation by limiting which code may depend directly on a type or member.

## <a id="access-levels">Access Levels</a>

For members, Java provides:

```text
private
→ declaring class only

package-private
→ same package

protected
→ package + specific subclass access rules

public
→ broadly visible subject to package/module visibility
```

There is no `package-private` keyword; omitting an access modifier creates that level.

## <a id="protected-cross-package">protected Across Packages</a>

`protected` is more subtle than “subclasses can access it”. Across packages, access also depends on subclass context and the qualifying reference.

A subclass in another package cannot arbitrarily use a protected member through every superclass instance.

For example, assume `Parent` is declared in package `a` with a protected field:

```java
package a;

public class Parent {
    protected int value;
}
```

Then a subclass in package `b` may use the inherited member through its own subclass context, but not through an arbitrary `Parent` reference:

```java
package b;

import a.Parent;

class Child extends Parent {
    void allowed(Child other) {
        this.value = 1;
        other.value = 2;
    }

    void rejected(Parent other) {
        // other.value = 3; // compile-time error
    }
}
```

This is why `protected` should be learned as a precise language rule, not as the shortcut “subclasses can always access it”.

## <a id="encapsulation-boundary">Access Control as a Boundary</a>

`private` does not automatically create good design, but access control is a core tool for reducing coupling.

A useful heuristic:

```text
member does not need to be public
→ do not make it public merely for convenience

state has invariants
→ avoid exposing unrestricted mutation
```

A smaller API surface gives fewer callers direct dependence on implementation details.

## <a id="java-modifier-map">Java Modifier Map</a>

Access modifiers are only **one group** among the modifiers and keywords that can appear around classes, fields, and methods. They should not be learned as one flat list because each group solves a different problem.

```text
Access control
→ public, protected, private

Class/object structure
→ static, final

Abstraction / inheritance
→ abstract

Concurrency
→ synchronized, volatile

Serialization
→ transient

Native interoperability
→ native

Floating-point semantics / language history
→ strictfp
```

At an orientation level:

| Keyword | What question does it answer? | Where to learn it deeply |
| --- | --- | --- |
| `public`, `protected`, `private` | Which code may access a type or member? | **Class Object → Access Modifiers** |
| `static` | Does the member belong to the class or to each object? | **Class Object → Static / Final** |
| `final` | Can the declaration be reassigned, overridden, or extended further, depending on where it is used? | **Class Object → Static / Final** |
| `abstract` | Which class or method defines an incomplete contract that a subtype must complete? | **Abstract Interface** |
| `synchronized` | How do threads coordinate access to a critical section or monitor? | **Concurrency → Thread → Synchronization** |
| `volatile` | How are field visibility and ordering defined across threads by the Java Memory Model? | **Concurrency → Thread → Java Memory Model** |
| `transient` | Which field is excluded from Java native serialization? | **IO → Serialization** |
| `native` | Which method is implemented outside Java code? | **JNI / native-interoperability boundary** |
| `strictfp` | How was strict floating-point behavior expressed historically in Java? | **Numbers / language-history boundary** |

### REMEMBER — similar syntax does not imply the same concept

These keywords may appear next to the same declaration, but that does **not** mean they solve the same problem.

For example:

```java
private volatile boolean running;
```

Here:

```text
private
→ controls access

volatile
→ controls cross-thread visibility/ordering semantics
```

`private` does not make the field thread-safe, and `volatile` does not create encapsulation.

Likewise:

```java
public synchronized void update() { ... }
```

`public` answers **who may call the method**; `synchronized` answers **how threads coordinate while executing it**.

Class Object only needs to establish this modifier map. Concurrency-, serialization-, and native-related modifiers should be learned deeply in the modules that explain the problems they exist to solve.

Next we separate two different concerns: class-level vs instance-level members, and whether a variable/reference may be reassigned.
